package com.devmod.clone.block.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.block.TelepadBlock;
import com.devmod.clone.network.TelepadConfigPayload;
import com.devmod.network.NetworkConstants;
import com.devmod.portal.PortalColor;
import com.devmod.portal.PortalData;
import com.devmod.portal.PortalRegistry;

/**
 * Block entity for the telepad.
 * Handles charging mechanic and teleportation using the existing PortalRegistry.
 *
 * <p>Uses "telepad:{name}" as network name in PortalRegistry for cross-dimensional teleportation.
 */
@SuppressWarnings("null") // Minecraft API lacks @Nonnull annotations
public class TelepadBlockEntity extends BlockEntity {

    private static final int CHARGE_TIME = 40; // ticks (2 seconds)
    private static final long ARRIVAL_COOLDOWN = 60; // ticks (3 seconds)
    private static final String TELEPAD_NETWORK_PREFIX = "telepad:";
    private static final float CHARGE_DECAY = 0.05f;
    private static final int CHARGE_SYNC_INTERVAL = 4;
    private static final float CHARGE_SYNC_EPS = 0.02f;
    private static final String TAG_CHARGE_PROGRESS = "ChargeProgress";

    private String telepadName = "";
    private UUID portalId;
    private final Map<UUID, Integer> chargingPlayers = new HashMap<>();
    private final Map<UUID, Long> recentArrivals = new HashMap<>();
    private float chargeProgress = 0.0f;
    private float chargeProgressPrev = 0.0f;
    private float lastSyncedChargeProgress = 0.0f;
    private long lastChargeSyncTick = 0L;

    public TelepadBlockEntity(BlockPos pos, BlockState state) {
        super(CloneBlockEntities.TELEPAD.get(), pos, state);
        this.portalId = UUID.randomUUID();
    }

    public String getTelepadName() {
        return telepadName;
    }

    public void setTelepadName(String name) {
        String sanitized = sanitizeTelepadName(name);
        if (sanitized.equals(this.telepadName)) {
            return;
        }
        this.telepadName = sanitized;
        setChanged();
        if (level != null && !level.isClientSide) {
            updateRegistry();
            syncToClient();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            updateRegistry();
        }
    }

    private void updateRegistry() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        PortalRegistry registry = PortalRegistry.get(serverLevel);

        if (!telepadName.isEmpty()) {
            // Register as a portal with telepad network name
            String networkName = TELEPAD_NETWORK_PREFIX + telepadName;
            PortalData data = new PortalData(
                portalId,
                PortalColor.CYAN, // Default color for telepads
                null,  // linkedId - uses network instead
                serverLevel.dimension().location(),  // dim
                getBlockPos(),  // pos
                null,  // runeType
                0,     // frameBlockCount
                null,  // creatorId
                null,  // fixedDestDim
                null,  // fixedDestPos
                networkName  // networkName = "telepad:{name}"
            );
            registry.register(data);
        } else {
            // Remove from registry if name is cleared
            registry.unregister(portalId);
        }
    }

    public void removeFromRegistry() {
        if (level instanceof ServerLevel serverLevel) {
            PortalRegistry registry = PortalRegistry.get(serverLevel);
            registry.unregister(portalId);
        }
    }

    public boolean needsTicking() {
        // Always tick on client side if the telepad is active (for depth rendering registration)
        if (level != null && level.isClientSide) {
            BlockState state = getBlockState();
            if (state.hasProperty(TelepadBlock.ACTIVE) && state.getValue(TelepadBlock.ACTIVE)) {
                return true;
            }
        }
        return !chargingPlayers.isEmpty() || !recentArrivals.isEmpty() || chargeProgress > 0.001f;
    }

    public void clientTick() {
        chargeProgressPrev = chargeProgress;

        // Register for client-side occlusion checks
        BlockState state = getBlockState();
        boolean active = state.getValue(TelepadBlock.ACTIVE);
        float intensity = active ? 1.0f : (chargeProgress > 0.01f ? 0.75f : 0.0f);

        if (intensity > 0.01f) {
            com.devmod.clone.client.renderer.TelepadDepthRenderer.registerTelepad(worldPosition);
        } else {
            com.devmod.clone.client.renderer.TelepadDepthRenderer.unregisterTelepad(worldPosition);
        }
    }

    public float getChargeProgress(float partialTick) {
        return Mth.clamp(Mth.lerp(partialTick, chargeProgressPrev, chargeProgress), 0.0f, 1.0f);
    }

    public void tick() {
        if (level == null || level.isClientSide) {
            return;
        }

        long currentTime = level.getGameTime();

        // Clean up old arrivals
        if (currentTime % 20L == 0L) {
            recentArrivals.entrySet().removeIf(entry -> currentTime - entry.getValue() > ARRIVAL_COOLDOWN);
        }

        // Process charging players
        var toRemove = new java.util.ArrayList<UUID>();
        for (Map.Entry<UUID, Integer> entry : chargingPlayers.entrySet()) {
            UUID playerId = entry.getKey();
            int chargeTime = entry.getValue();

            Player player = level.getPlayerByUUID(playerId);
            if (player == null || !isPlayerOnTelepad(player)) {
                toRemove.add(playerId);
                continue;
            }

            chargeTime++;
            entry.setValue(chargeTime);

            // Particles every 2 ticks
            if (chargeTime % 2 == 0) {
                spawnChargingParticles(player);
            }

            // Sounds
            if (chargeTime == 1) {
                level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5f, 1.5f);
            } else if (chargeTime % 10 == 0 && chargeTime < CHARGE_TIME) {
                level.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.3f, 1.8f);
            }

            // Execute teleport when charged
            if (chargeTime >= CHARGE_TIME) {
                if (player instanceof ServerPlayer serverPlayer) {
                    executeTeleport(serverPlayer);
                }
                toRemove.add(playerId);
            }
        }

        boolean wasActive = !toRemove.isEmpty() || !chargingPlayers.isEmpty();
        toRemove.forEach(chargingPlayers::remove);
        boolean isActive = !chargingPlayers.isEmpty();

        // Update block state if active state changed
        if (level != null && wasActive != isActive) {
            TelepadBlock.setActive(level, worldPosition, getBlockState(), isActive);
        }

        updateChargeProgress(currentTime);
    }

    private boolean isPlayerOnTelepad(Player player) {
        BlockPos playerPos = player.blockPosition();
        return playerPos.getX() == worldPosition.getX()
            && playerPos.getZ() == worldPosition.getZ()
            && Math.abs(playerPos.getY() - worldPosition.getY()) <= 1;
    }

    public void startCharging(Player player) {
        if (level == null || level.isClientSide || !(player instanceof ServerPlayer)) {
            return;
        }

        UUID playerId = player.getUUID();

        // Check arrival cooldown
        if (recentArrivals.containsKey(playerId)) {
            long arrivedAt = recentArrivals.get(playerId);
            long currentTime = level.getGameTime();
            if (currentTime - arrivedAt < ARRIVAL_COOLDOWN) {
                return;
            }
        }

        // Check if telepad is configured
        if (telepadName.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.devmod.telepad.not_configured"), true);
            return;
        }

        // Don't restart charging if already charging
        if (chargingPlayers.containsKey(playerId)) {
            return;
        }

        chargingPlayers.put(playerId, 0);
    }

    private void executeTeleport(ServerPlayer player) {
        if (level == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        String networkName = TELEPAD_NETWORK_PREFIX + telepadName;
        PortalRegistry registry = PortalRegistry.get(serverLevel);

        // Find random destination with same network name
        Optional<PortalData> destination = registry.getRandomNetworkDestination(networkName, portalId);

        if (destination.isEmpty()) {
            player.displayClientMessage(
                Component.translatable("message.devmod.telepad.no_destination", telepadName), true);
            return;
        }

        PortalData dest = destination.get();
        BlockPos destPos = dest.position().orElse(null);
        if (destPos == null || dest.dimension().isEmpty()) {
            return;
        }

        // Get destination level
        ServerLevel destLevel = serverLevel.getServer().getLevel(
            net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                dest.dimension().get()
            )
        );
        if (destLevel == null) {
            return;
        }

        // Effects at departure
        spawnTeleportParticles(player.position());
        level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 1.4f);

        // Ensure chunk is loaded
        destLevel.getChunk(destPos);

        // Teleport
        player.teleportTo(destLevel,
            destPos.getX() + 0.5,
            destPos.getY() + 0.5,
            destPos.getZ() + 0.5,
            player.getYRot(),
            player.getXRot());

        // Mark arrival at destination to prevent immediate re-teleport
        BlockEntity destBe = destLevel.getBlockEntity(destPos);
        if (destBe instanceof TelepadBlockEntity destTelepad) {
            destTelepad.markPlayerArrival(player.getUUID());
        }

        // Effects at arrival
        destLevel.playSound(null, destPos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 1.6f);
        spawnArrivalParticles(destLevel, destPos);

        player.displayClientMessage(
            Component.translatable("message.devmod.telepad.teleported", telepadName), true);
    }

    public void markPlayerArrival(UUID playerId) {
        if (level != null) {
            recentArrivals.put(playerId, level.getGameTime());
        }
    }

    private void spawnChargingParticles(Player player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        var random = serverLevel.getRandom();
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 0.5 + random.nextDouble() * 0.5;
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                x + offsetX, y + random.nextDouble(), z + offsetZ,
                1, 0, 0, 0, 0.05);
        }
    }

    private void spawnTeleportParticles(Vec3 pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        spawnTeleportParticlesAt(serverLevel, pos.x, pos.y, pos.z);
    }

    private void spawnArrivalParticles(ServerLevel serverLevel, BlockPos pos) {
        spawnTeleportParticlesAt(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private void spawnTeleportParticlesAt(ServerLevel serverLevel, double x, double y, double z) {
        serverLevel.sendParticles(ParticleTypes.PORTAL, x, y + 0.5, z, 50, 0.3, 0.5, 0.3, 0.5);
        serverLevel.sendParticles(ParticleTypes.END_ROD, x, y + 0.5, z, 20, 0.2, 0.3, 0.2, 0.1);
    }

    private void updateChargeProgress(long currentTime) {
        int maxCharge = 0;
        for (int chargeTime : chargingPlayers.values()) {
            if (chargeTime > maxCharge) {
                maxCharge = chargeTime;
            }
        }

        float targetCharge = maxCharge > 0 ? (float) maxCharge / (float) CHARGE_TIME : 0.0f;
        chargeProgressPrev = chargeProgress;

        if (maxCharge > 0) {
            chargeProgress = Mth.clamp(targetCharge, 0.0f, 1.0f);
        } else if (chargeProgress > 0.0f) {
            chargeProgress = Math.max(0.0f, chargeProgress - CHARGE_DECAY);
        }

        maybeSyncChargeProgress(currentTime);
    }

    private void maybeSyncChargeProgress(long currentTime) {
        if (level == null || level.isClientSide) {
            return;
        }

        boolean timeElapsed = currentTime - lastChargeSyncTick >= CHARGE_SYNC_INTERVAL;
        if (Math.abs(chargeProgress - lastSyncedChargeProgress) >= CHARGE_SYNC_EPS || timeElapsed) {
            lastSyncedChargeProgress = chargeProgress;
            lastChargeSyncTick = currentTime;
            markDirtyAndSync();
        }
    }

    private void markDirtyAndSync() {
        setChanged();
        syncToClient();
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            BlockState currentState = getBlockState();
            level.sendBlockUpdated(worldPosition, currentState, currentState, 2);
        }
    }

    // === NBT Persistence ===

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("TelepadName", telepadName);
        tag.putUUID("PortalId", portalId);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        telepadName = sanitizeTelepadName(tag.contains("TelepadName") ? tag.getString("TelepadName") : "");
        portalId = tag.hasUUID("PortalId") ? tag.getUUID("PortalId") : UUID.randomUUID();
        if (tag.contains(TAG_CHARGE_PROGRESS)) {
            chargeProgressPrev = chargeProgress;
            chargeProgress = tag.getFloat(TAG_CHARGE_PROGRESS);
        } else {
            chargeProgress = 0.0f;
            chargeProgressPrev = 0.0f;
        }
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putString("TelepadName", telepadName);
        tag.putFloat(TAG_CHARGE_PROGRESS, chargeProgress);
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static String sanitizeTelepadName(@Nullable String name) {
        String trimmed = name == null ? "" : name.trim();
        return NetworkConstants.truncate(trimmed, TelepadConfigPayload.MAX_TELEPAD_NAME_LENGTH);
    }
}
