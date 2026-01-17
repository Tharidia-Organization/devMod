package com.devmod.transport.manager;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportData;
import com.devmod.transport.TransportRegistry;
import com.devmod.transport.executor.RecoveryManager;

/**
 * Manages temporary Rift Gates - portal-like structures that can be spawned
 * at player locations for group transport or emergency evacuation.
 *
 * <p>Features:
 * <ul>
 *   <li>Temporary portal structures with configurable duration</li>
 *   <li>BossBar countdown display</li>
 *   <li>Area protection during gate lifetime</li>
 *   <li>Automatic cleanup and restoration</li>
 * </ul>
 *
 * <p>Pattern based on RiftStampManager's portal spawning system.
 */
public final class RiftGateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RiftGateManager.class);

    public static final RiftGateManager INSTANCE = new RiftGateManager();

    /** Default gate duration (60 seconds). */
    private static final int DEFAULT_DURATION_TICKS = 20 * 60;

    /** Gate footprint size. */
    private static final int GATE_SIZE = 5;
    private static final int GATE_HEIGHT = 6;

    /** BossBar visibility range squared. */
    private static final double BOSSBAR_RANGE_SQ = 48.0 * 48.0;

    /** BossBar update interval. */
    private static final int BOSSBAR_UPDATE_INTERVAL = 10;

    /** Active rift gates. */
    private final Map<UUID, RiftGate> activeGates = new ConcurrentHashMap<>();

    private RiftGateManager() {}

    /**
     * Spawns a rift gate at a player's location.
     *
     * @param player The player requesting the gate
     * @param destination The transport destination (optional)
     * @param durationTicks Gate duration in ticks
     * @param color Gate color
     * @return The gate UUID if successful, empty if failed
     */
    @Nonnull
    public Optional<UUID> spawnGateAtPlayer(
            @Nonnull ServerPlayer player,
            @Nullable TransportData destination,
            int durationTicks,
            @Nonnull TransportColor color) {

        if (!(player.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }

        MinecraftServer server = level.getServer();
        if (!server.isSameThread()) {
            server.execute(() -> spawnGateAtPlayer(player, destination, durationTicks, color));
            return Optional.empty();
        }

        BlockPos playerPos = player.blockPosition();
        BlockPos gateBase = findSuitablePosition(level, playerPos);
        if (gateBase == null) {
            LOGGER.warn("[RiftGate] Could not find suitable position near {}", playerPos);
            return Optional.empty();
        }

        // Check for existing gate at this location
        for (RiftGate existing : activeGates.values()) {
            if (existing.level.dimension().equals(level.dimension()) &&
                existing.basePos.closerThan(gateBase, GATE_SIZE * 2)) {
                LOGGER.warn("[RiftGate] Gate already exists near {}", gateBase);
                return Optional.empty();
            }
        }

        // Take snapshot of area
        UUID gateId = UUID.randomUUID();
        boolean snapshotOk = RecoveryManager.INSTANCE.takeSnapshot(
            gateId, level, gateBase,
            new Vec3i(GATE_SIZE, GATE_HEIGHT, GATE_SIZE)
        );

        if (!snapshotOk) {
            LOGGER.warn("[RiftGate] Failed to take snapshot at {}", gateBase);
            return Optional.empty();
        }

        // Build the gate structure
        buildGateStructure(level, gateBase);

        // Create BossBar
        long startTick = level.getGameTime();
        long endTick = startTick + durationTicks;
        int initialSeconds = durationTicks / 20;

        ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("devmod.transport.rift_gate", initialSeconds),
            toBossBarColor(color),
            BossEvent.BossBarOverlay.PROGRESS
        );
        bossBar.setDarkenScreen(false);
        bossBar.setCreateWorldFog(false);
        bossBar.setPlayBossMusic(false);
        bossBar.setProgress(1.0f);

        // Calculate teleport area (center of gate)
        BlockPos center = gateBase.offset(GATE_SIZE / 2, 1, GATE_SIZE / 2);
        AABB teleportBox = new AABB(
            center.getX() - 1.0, center.getY(), center.getZ() - 1.0,
            center.getX() + 2.0, center.getY() + 3, center.getZ() + 2.0
        );

        // Create gate instance
        RiftGate gate = new RiftGate(
            gateId,
            level,
            gateBase,
            center,
            teleportBox,
            destination,
            color,
            bossBar,
            startTick,
            endTick,
            player.getUUID()
        );

        activeGates.put(gateId, gate);

        // Play spawn sound
        level.playSound(null, center, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8f, 1.2f);

        // Register as transport node
        registerAsTransportNode(level, gate);

        LOGGER.info("[RiftGate] Spawned gate {} at {} for {} seconds",
            gateId, gateBase, initialSeconds);

        return Optional.of(gateId);
    }

    /**
     * Spawns a gate with default duration.
     */
    @Nonnull
    public Optional<UUID> spawnGateAtPlayer(
            @Nonnull ServerPlayer player,
            @Nullable TransportData destination,
            @Nonnull TransportColor color) {
        return spawnGateAtPlayer(player, destination, DEFAULT_DURATION_TICKS, color);
    }

    /**
     * Closes a rift gate early.
     */
    public boolean closeGate(@Nonnull UUID gateId) {
        RiftGate gate = activeGates.remove(gateId);
        if (gate == null) {
            return false;
        }

        restore(gate);
        LOGGER.info("[RiftGate] Closed gate {} early", gateId);
        return true;
    }

    /**
     * Checks if a position is protected by a rift gate.
     */
    public boolean isProtected(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
        for (RiftGate gate : activeGates.values()) {
            if (!gate.level.dimension().equals(level.dimension())) {
                continue;
            }
            if (isWithinGateBounds(gate, pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called every server tick to update all active gates.
     */
    public void tick(@Nonnull MinecraftServer server) {
        if (activeGates.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, RiftGate>> iterator = activeGates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RiftGate> entry = iterator.next();
            RiftGate gate = entry.getValue();

            ServerLevel level = gate.level;
            if (level == null || level.getServer() == null) {
                iterator.remove();
                cleanup(gate);
                continue;
            }

            long now = level.getGameTime();

            // Check expiration
            if (now >= gate.endTick) {
                iterator.remove();
                restore(gate);
                LOGGER.debug("[RiftGate] Gate {} expired", gate.gateId);
                continue;
            }

            // Update effects and handle teleportation
            emitEffects(gate, now);
            handleTeleportation(gate, server);
            updateBossBar(gate, now);
        }
    }

    /**
     * Finds a suitable position for the gate.
     */
    @Nullable
    private BlockPos findSuitablePosition(ServerLevel level, BlockPos playerPos) {
        // Start at player position and find solid ground
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(
            playerPos.getX() - GATE_SIZE / 2,
            playerPos.getY(),
            playerPos.getZ() - GATE_SIZE / 2
        );

        // Check if current position is valid
        for (int y = playerPos.getY(); y > level.getMinBuildHeight(); y--) {
            mutable.setY(y);
            if (level.getBlockState(mutable).isSolidRender(level, mutable)) {
                mutable.setY(y + 1);
                return mutable.immutable();
            }
        }

        // Fallback to player position
        return playerPos.below();
    }

    /**
     * Builds the gate structure.
     */
    private void buildGateStructure(ServerLevel level, BlockPos basePos) {
        BlockState frame = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        BlockState portal = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(); // Placeholder
        BlockState base = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        int flags = 2 | 16; // UPDATE | NO_OBSERVER

        int centerX = basePos.getX() + GATE_SIZE / 2;
        int centerZ = basePos.getZ() + GATE_SIZE / 2;

        // Build platform base
        for (int x = 0; x < GATE_SIZE; x++) {
            for (int z = 0; z < GATE_SIZE; z++) {
                level.setBlock(basePos.offset(x, 0, z), base, flags);
            }
        }

        // Build frame pillars at corners
        for (int y = 1; y <= GATE_HEIGHT - 1; y++) {
            level.setBlock(new BlockPos(basePos.getX(), basePos.getY() + y, basePos.getZ()), frame, flags);
            level.setBlock(new BlockPos(basePos.getX() + GATE_SIZE - 1, basePos.getY() + y, basePos.getZ()), frame, flags);
            level.setBlock(new BlockPos(basePos.getX(), basePos.getY() + y, basePos.getZ() + GATE_SIZE - 1), frame, flags);
            level.setBlock(new BlockPos(basePos.getX() + GATE_SIZE - 1, basePos.getY() + y, basePos.getZ() + GATE_SIZE - 1), frame, flags);
        }

        // Top frame
        for (int x = 0; x < GATE_SIZE; x++) {
            level.setBlock(basePos.offset(x, GATE_HEIGHT - 1, 0), frame, flags);
            level.setBlock(basePos.offset(x, GATE_HEIGHT - 1, GATE_SIZE - 1), frame, flags);
        }
        for (int z = 1; z < GATE_SIZE - 1; z++) {
            level.setBlock(basePos.offset(0, GATE_HEIGHT - 1, z), frame, flags);
            level.setBlock(basePos.offset(GATE_SIZE - 1, GATE_HEIGHT - 1, z), frame, flags);
        }

        // Center portal area (glass)
        for (int y = 1; y < GATE_HEIGHT - 1; y++) {
            level.setBlock(new BlockPos(centerX, basePos.getY() + y, centerZ), portal, flags);
        }
    }

    /**
     * Emits visual and audio effects.
     */
    private void emitEffects(RiftGate gate, long now) {
        ServerLevel level = gate.level;
        BlockPos center = gate.center;
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 1.5;
        double cz = center.getZ() + 0.5;

        // Ambient particles
        if (now % 10 == 0) {
            level.sendParticles(ParticleTypes.PORTAL, cx, cy, cz, 8, 0.5, 1.0, 0.5, 0.1);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, cy + 1.5, cz, 4, 0.3, 0.5, 0.3, 0.05);
        }

        // Ambient sound
        if (now % 60 == 0) {
            level.playSound(null, center, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.3f, 1.0f);
        }
    }

    /**
     * Handles player teleportation through the gate.
     */
    private void handleTeleportation(RiftGate gate, MinecraftServer server) {
        if (gate.destination == null) {
            return; // No destination set
        }

        ServerLevel level = gate.level;
        for (ServerPlayer player : level.players()) {
            if (!player.getBoundingBox().intersects(gate.teleportBox)) {
                continue;
            }

            // Save recovery point
            RecoveryManager.INSTANCE.saveRecoveryPoint(player);

            // Get destination
            Optional<BlockPos> destPosOpt = gate.destination.getPosition();
            if (destPosOpt.isEmpty()) {
                continue;
            }

            ResourceLocation destDim = gate.destination.dimension();
            BlockPos destPos = destPosOpt.get();

            ServerLevel destLevel = server.getLevel(
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, destDim)
            );
            if (destLevel == null) {
                continue;
            }

            // Teleport
            destLevel.getChunk(destPos);
            player.teleportTo(destLevel,
                destPos.getX() + 0.5,
                destPos.getY() + 0.5,
                destPos.getZ() + 0.5,
                player.getYRot(),
                player.getXRot()
            );

            // Play sound
            level.playSound(null, gate.center, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.0f);

            LOGGER.debug("[RiftGate] Teleported {} through gate {}", player.getName().getString(), gate.gateId);
        }
    }

    /**
     * Updates the BossBar.
     */
    private void updateBossBar(RiftGate gate, long now) {
        ServerBossEvent bossBar = gate.bossBar;
        ServerLevel level = gate.level;

        long totalTicks = Math.max(1L, gate.endTick - gate.startTick);
        long remaining = Math.max(0L, gate.endTick - now);
        float progress = (float) remaining / (float) totalTicks;
        bossBar.setProgress(Math.max(0.0f, Math.min(1.0f, progress)));

        // Phase-based color
        BossEvent.BossBarColor bossColor;
        if (progress > 0.66f) {
            bossColor = toBossBarColor(gate.color);
        } else if (progress > 0.33f) {
            bossColor = BossEvent.BossBarColor.PURPLE;
        } else {
            bossColor = BossEvent.BossBarColor.RED;
        }
        bossBar.setColor(bossColor);

        // Update name periodically
        if (now % BOSSBAR_UPDATE_INTERVAL == 0) {
            int seconds = (int) Math.ceil(remaining / 20.0);
            bossBar.setName(Component.translatable("devmod.transport.rift_gate", seconds));
        }

        // Update player visibility
        double cx = gate.center.getX() + 0.5;
        double cy = gate.center.getY() + 1.0;
        double cz = gate.center.getZ() + 0.5;

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(cx, cy, cz) <= BOSSBAR_RANGE_SQ) {
                bossBar.addPlayer(player);
            } else {
                bossBar.removePlayer(player);
            }
        }
    }

    /**
     * Restores the area after gate closes.
     */
    private void restore(RiftGate gate) {
        cleanup(gate);

        // Restore snapshot
        MinecraftServer server = gate.level.getServer();
        if (server != null) {
            RecoveryManager.INSTANCE.restoreSnapshot(gate.gateId, server);
        }

        // Unregister transport node
        unregisterTransportNode(gate.level, gate);

        // Play closing sound
        gate.level.playSound(null, gate.center, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.6f, 0.8f);
    }

    /**
     * Cleans up gate resources.
     */
    private void cleanup(RiftGate gate) {
        if (gate.bossBar != null) {
            gate.bossBar.removeAllPlayers();
        }
    }

    /**
     * Registers the gate as a transport node.
     */
    private void registerAsTransportNode(ServerLevel level, RiftGate gate) {
        TransportRegistry registry = TransportRegistry.get(level);
        ResourceLocation dimension = level.dimension().location();

        // Get destination info
        ResourceLocation destDim = dimension;
        BlockPos destPos = gate.center;
        if (gate.destination != null) {
            ResourceLocation candidateDim = gate.destination.dimension();
            BlockPos candidatePos = gate.destination.getPosition().orElse(null);
            if (candidateDim != null && candidatePos != null) {
                destDim = candidateDim;
                destPos = candidatePos;
            }
        }

        TransportData nodeData = TransportData.createRiftGate(
            gate.gateId,
            dimension, gate.center,
            destDim, destPos,
            gate.startTick, gate.endTick - gate.startTick
        );

        registry.register(nodeData);
    }

    /**
     * Unregisters the gate transport node.
     */
    private void unregisterTransportNode(ServerLevel level, RiftGate gate) {
        TransportRegistry registry = TransportRegistry.get(level);
        registry.unregister(gate.gateId);
    }

    /**
     * Checks if position is within gate bounds.
     */
    private boolean isWithinGateBounds(RiftGate gate, BlockPos pos) {
        return pos.getX() >= gate.basePos.getX() &&
               pos.getX() < gate.basePos.getX() + GATE_SIZE &&
               pos.getY() >= gate.basePos.getY() &&
               pos.getY() < gate.basePos.getY() + GATE_HEIGHT &&
               pos.getZ() >= gate.basePos.getZ() &&
               pos.getZ() < gate.basePos.getZ() + GATE_SIZE;
    }

    /**
     * Converts TransportColor to BossBarColor.
     */
    private BossEvent.BossBarColor toBossBarColor(TransportColor color) {
        return switch (color) {
            case WHITE -> BossEvent.BossBarColor.WHITE;
            case PINK -> BossEvent.BossBarColor.PINK;
            case RED -> BossEvent.BossBarColor.RED;
            case YELLOW -> BossEvent.BossBarColor.YELLOW;
            case GREEN, LIME -> BossEvent.BossBarColor.GREEN;
            case BLUE, LIGHT_BLUE, CYAN -> BossEvent.BossBarColor.BLUE;
            case PURPLE, MAGENTA -> BossEvent.BossBarColor.PURPLE;
            default -> BossEvent.BossBarColor.WHITE;
        };
    }

    /**
     * Clears all active gates (for server shutdown).
     */
    public void clearAll(MinecraftServer server) {
        for (RiftGate gate : activeGates.values()) {
            restore(gate);
        }
        activeGates.clear();
        LOGGER.info("[RiftGate] Cleared all rift gates");
    }

    /**
     * Gets the number of active gates.
     */
    public int getActiveCount() {
        return activeGates.size();
    }

    // =========================================================================
    // Inner classes
    // =========================================================================

    /**
     * Represents an active rift gate.
     */
    private record RiftGate(
        UUID gateId,
        ServerLevel level,
        BlockPos basePos,
        BlockPos center,
        AABB teleportBox,
        @Nullable TransportData destination,
        TransportColor color,
        ServerBossEvent bossBar,
        long startTick,
        long endTick,
        UUID ownerId
    ) {}
}
