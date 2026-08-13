package com.devmod.runtime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import com.devmod.DevMod;
import com.devmod.config.Config;

@SuppressWarnings("null")
public final class RiftStampManager {
    public static final RiftStampManager INSTANCE = new RiftStampManager();

    private static final ResourceLocation TEMPLATE_ID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "rift_stamp");
    private static final int FOOTPRINT_SIZE = 16;
    private static final int SNAPSHOT_HEIGHT = 24;
    private static final int PORTAL_DURATION_TICKS = 20 * 60;
    private static final int CALIBRATION_TICKS = 20 * 5;
    private static final int COLLAPSE_START_TICKS = 20 * 50;
    private static final int BOSSBAR_UPDATE_INTERVAL = 10;
    private static final double BOSSBAR_RANGE_SQ = 48.0 * 48.0;
    private static final int PLACEMENT_FLAGS = 2 | 16 | 64;

    private final Map<RiftKey, RiftSession> active = new ConcurrentHashMap<>();

    private RiftStampManager() {}

    public boolean spawnAtPlayer(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (!Config.NEXUS_ENABLED.get()) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (level.dimension().equals(NexusDimensionManager.NEXUS_DIMENSION)) {
            return false;
        }
        MinecraftServer server = level.getServer();
        if (!server.isSameThread()) {
            server.execute(() -> spawnAtPlayer(player));
            return false;
        }

        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        RiftKey key = new RiftKey(level.dimension(), chunkPos);
        if (active.containsKey(key)) {
            return false;
        }
        level.getChunk(chunkPos.x, chunkPos.z);

        StructureTemplate portalTemplate = resolveTemplate(level, TEMPLATE_ID);
        int snapshotHeight = SNAPSHOT_HEIGHT;
        if (portalTemplate != null) {
            Vec3i size = portalTemplate.getSize();
            if (size != null) {
                if (size.getX() > FOOTPRINT_SIZE || size.getZ() > FOOTPRINT_SIZE) {
                    DevMod.LOGGER.warn("[RiftStamp] Template {} exceeds {}x{} footprint ({}x{}). Falling back.",
                        TEMPLATE_ID, FOOTPRINT_SIZE, FOOTPRINT_SIZE, size.getX(), size.getZ());
                    portalTemplate = null;
                } else if (size.getY() > snapshotHeight) {
                    snapshotHeight = size.getY();
                }
            }
        }

        Integer baseY = resolveBaseY(level, chunkPos, snapshotHeight);
        if (baseY == null) {
            return false;
        }
        BlockPos basePos = new BlockPos(chunkPos.getMinBlockX(), baseY, chunkPos.getMinBlockZ());
        if (!isWithinWorldBorder(level, basePos)) {
            return false;
        }
        StructureTemplate snapshot = new StructureTemplate();
        snapshot.fillFromWorld(level, basePos, new Vec3i(FOOTPRINT_SIZE, snapshotHeight, FOOTPRINT_SIZE), false, Blocks.AIR);

        boolean placed = placePortal(level, basePos, portalTemplate);
        if (!placed) {
            return false;
        }

        long startTick = level.getGameTime();
        BlockPos center = new BlockPos(basePos.getX() + 8, baseY + 1, basePos.getZ() + 8);
        BoundingBox bounds = new BoundingBox(basePos.getX(), basePos.getY(), basePos.getZ(),
            basePos.getX() + FOOTPRINT_SIZE - 1, basePos.getY() + snapshotHeight - 1, basePos.getZ() + FOOTPRINT_SIZE - 1);
        AABB teleportBox = new AABB(center.getX() - 1.2, baseY + 1, center.getZ() - 1.2,
            center.getX() + 2.2, baseY + 8, center.getZ() + 2.2);

        ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("devmod.riftstamp.bossbar", PORTAL_DURATION_TICKS / 20),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS
        );
        bossBar.setDarkenScreen(false);
        bossBar.setCreateWorldFog(false);
        bossBar.setPlayBossMusic(false);

        RiftSession session = new RiftSession(level, chunkPos, basePos, center, snapshot, bounds, teleportBox, bossBar,
            startTick, startTick + PORTAL_DURATION_TICKS);
        active.put(key, session);
        level.playSound(null, center, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8f, 1.2f);
        return true;
    }

    public void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (active.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<RiftKey, RiftSession>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<RiftKey, RiftSession> entry = iterator.next();
            RiftSession session = entry.getValue();
            ServerLevel level = session.level();
            if (level == null) {
                iterator.remove();
                continue;
            }
            level.getChunk(session.chunkPos().x, session.chunkPos().z);

            long now = level.getGameTime();
            if (now >= session.endTick()) {
                restore(session);
                iterator.remove();
                continue;
            }

            emitFx(session, now);
            handleTeleport(session);
            updateBossBar(session, now);
        }
    }

    /**
     * Restore every open rift and drop all sessions.
     *
     * <p>Sessions hold a strong ServerLevel, so they must not outlive the server they were
     * created in; without this the terrain snapshot is never placed back and the footprint
     * stays block-protected forever.
     */
    public void shutdown() {
        for (RiftSession session : active.values()) {
            restore(session);
        }
        active.clear();
    }

    public boolean isProtected(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        for (RiftSession session : active.values()) {
            if (!session.level().dimension().equals(level.dimension())) {
                continue;
            }
            if (session.bounds().isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    private static void handleTeleport(RiftSession session) {
        ServerLevel level = session.level();
        if (level == null) {
            return;
        }
        List<ServerPlayer> players = new ArrayList<>(level.players());
        AABB teleportBox = session.teleportBox();
        for (ServerPlayer player : players) {
            if (!player.getBoundingBox().intersects(teleportBox)) {
                continue;
            }
            level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.0f);
            NexusDimensionManager.INSTANCE.teleportPlayerToZone(player, "hub");
        }
    }

    private static void emitFx(RiftSession session, long now) {
        long age = now - session.startTick();
        ServerLevel level = session.level();
        if (level == null) {
            return;
        }
        RandomSource random = level.getRandom();
        BlockPos center = session.center();
        double cx = center.getX() + 0.5;
        double cz = center.getZ() + 0.5;
        double baseY = session.basePos().getY();

        if (age < CALIBRATION_TICKS) {
            if (now % 6 == 0) {
                for (int i = 0; i < 8; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double radius = 5.0 + random.nextDouble() * 1.5;
                    double x = cx + Math.cos(angle) * radius;
                    double z = cz + Math.sin(angle) * radius;
                    double y = baseY + 1.0 + random.nextDouble() * 4.0;
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                        x, y, z, 0,
                        (cx - x) * 0.05, 0.02, (cz - z) * 0.05, 0.15);
                }
            }
            if (now % 30 == 0) {
                level.playSound(null, center, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.4f, 1.4f);
            }
            return;
        }

        if (age < COLLAPSE_START_TICKS) {
            if (now % 10 == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                    cx, baseY + 4.0, cz, 14, 0.6, 1.4, 0.6, 0.2);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    cx, baseY + 5.5, cz, 4, 0.2, 0.8, 0.2, 0.0);
            }
            if (now % 60 == 0) {
                level.playSound(null, center, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.5f, 0.9f);
            }
            return;
        }

        if (now % 4 == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                cx, baseY + 4.0, cz, 12, 0.9, 1.2, 0.9, 0.02);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                cx, baseY + 4.5, cz, 10, 0.7, 1.0, 0.7, 0.12);
        }
        if (now % 20 == 0) {
            level.playSound(null, center, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5f, 0.6f);
        }
    }

    private static void restore(RiftSession session) {
        ServerLevel level = session.level();
        if (level == null) {
            return;
        }
        level.getChunk(session.chunkPos().x, session.chunkPos().z);
        StructurePlaceSettings settings = new StructurePlaceSettings();
        settings.setIgnoreEntities(true);
        try {
            session.snapshot().placeInWorld(level, session.basePos(), session.basePos(),
                settings, level.getRandom(), PLACEMENT_FLAGS);
            level.playSound(null, session.center(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.6f, 0.8f);
        } catch (Exception e) {
            DevMod.LOGGER.warn("[RiftStamp] Failed to restore snapshot in {}: {}", session.chunkPos(), e.getMessage());
        }
        ServerBossEvent bossBar = session.bossBar();
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }
    }

    @Nullable
    private static Integer resolveBaseY(ServerLevel level, ChunkPos chunkPos, int snapshotHeight) {
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int[] offsets = {4, 8, 12};
        int maxHeight = level.getMinBuildHeight();
        for (int dx : offsets) {
            for (int dz : offsets) {
                int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, minX + dx, minZ + dz);
                if (height > maxHeight) {
                    maxHeight = height;
                }
            }
        }
        int baseY = maxHeight + 1;
        int minBase = level.getMinBuildHeight() + 1;
        int maxBase = level.getMaxBuildHeight() - snapshotHeight - 1;
        if (maxBase < minBase) {
            maxBase = minBase;
        }
        if (baseY < minBase || baseY > maxBase) {
            return null;
        }
        return baseY;
    }

    private static boolean placePortal(ServerLevel level, BlockPos basePos, @Nullable StructureTemplate template) {
        if (template != null) {
            StructurePlaceSettings settings = new StructurePlaceSettings();
            settings.setIgnoreEntities(true);
            template.placeInWorld(level, basePos, basePos, settings, level.getRandom(), PLACEMENT_FLAGS);
            return true;
        }

        buildFallbackPortal(level, basePos);
        return true;
    }

    private static void buildFallbackPortal(ServerLevel level, BlockPos basePos) {
        int baseX = basePos.getX();
        int baseY = basePos.getY();
        int baseZ = basePos.getZ();
        int centerX = baseX + 8;
        int centerZ = baseZ + 8;

        BlockState ring = Blocks.REINFORCED_DEEPSLATE.defaultBlockState();
        BlockState rune = Blocks.AMETHYST_BLOCK.defaultBlockState();
        BlockState accent = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        BlockState riftEdge = Blocks.BLACK_STAINED_GLASS.defaultBlockState();
        BlockState riftCore = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
        BlockState stabilizer = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState light = Blocks.SOUL_LANTERN.defaultBlockState();
        BlockState core = Blocks.RESPAWN_ANCHOR.defaultBlockState();

        drawRectOutline(level, centerX - 5, centerZ - 5, centerX + 5, centerZ + 5, baseY, ring);
        drawRectOutline(level, centerX - 3, centerZ - 3, centerX + 3, centerZ + 3, baseY, accent);

        level.setBlock(new BlockPos(centerX - 5, baseY, centerZ), rune, PLACEMENT_FLAGS);
        level.setBlock(new BlockPos(centerX + 5, baseY, centerZ), rune, PLACEMENT_FLAGS);
        level.setBlock(new BlockPos(centerX, baseY, centerZ - 5), rune, PLACEMENT_FLAGS);
        level.setBlock(new BlockPos(centerX, baseY, centerZ + 5), rune, PLACEMENT_FLAGS);

        level.setBlock(new BlockPos(centerX, baseY + 1, centerZ), core, PLACEMENT_FLAGS);

        for (int y = 1; y <= 7; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                BlockState state = dx == 0 ? riftCore : riftEdge;
                level.setBlock(new BlockPos(centerX + dx, baseY + y, centerZ), state, PLACEMENT_FLAGS);
            }
        }

        int[][] corners = {
            {centerX - 5, centerZ - 5},
            {centerX + 4, centerZ - 5},
            {centerX - 5, centerZ + 4},
            {centerX + 4, centerZ + 4}
        };
        for (int[] corner : corners) {
            int cx = corner[0];
            int cz = corner[1];
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    for (int y = 1; y <= 6; y++) {
                        level.setBlock(new BlockPos(cx + dx, baseY + y, cz + dz), stabilizer, PLACEMENT_FLAGS);
                    }
                }
            }
            level.setBlock(new BlockPos(cx, baseY + 7, cz), light, PLACEMENT_FLAGS);
            level.setBlock(new BlockPos(cx + 1, baseY + 7, cz + 1), light, PLACEMENT_FLAGS);
        }
    }

    @Nullable
    private static StructureTemplate resolveTemplate(ServerLevel level, ResourceLocation location) {
        Object manager = level.getStructureManager();
        try {
            Method getOrCreate = manager.getClass().getMethod("getOrCreate", ResourceLocation.class);
            Object result = getOrCreate.invoke(manager, location);
            if (result instanceof StructureTemplate template) {
                return template;
            }
        } catch (ReflectiveOperationException ignored) {
            // Optional API path, fall back below.
        }
        try {
            Method get = manager.getClass().getMethod("get", ResourceLocation.class);
            Object result = get.invoke(manager, location);
            if (result instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof StructureTemplate template) {
                return template;
            }
            if (result instanceof StructureTemplate template) {
                return template;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    private static void drawRectOutline(ServerLevel level, int x1, int z1, int x2, int z2, int y, BlockState state) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            level.setBlock(new BlockPos(x, y, minZ), state, PLACEMENT_FLAGS);
            level.setBlock(new BlockPos(x, y, maxZ), state, PLACEMENT_FLAGS);
        }
        for (int z = minZ; z <= maxZ; z++) {
            level.setBlock(new BlockPos(minX, y, z), state, PLACEMENT_FLAGS);
            level.setBlock(new BlockPos(maxX, y, z), state, PLACEMENT_FLAGS);
        }
    }

    private static void updateBossBar(RiftSession session, long now) {
        ServerBossEvent bossBar = session.bossBar();
        ServerLevel level = session.level();
        if (bossBar == null || level == null) {
            return;
        }
        long totalTicks = Math.max(1L, session.endTick() - session.startTick());
        long remaining = Math.max(0L, session.endTick() - now);
        float progress = Math.max(0.0f, Math.min(1.0f, (float) remaining / (float) totalTicks));
        bossBar.setProgress(progress);

        long age = now - session.startTick();
        BossEvent.BossBarColor color = age < CALIBRATION_TICKS
            ? BossEvent.BossBarColor.BLUE
            : age < COLLAPSE_START_TICKS ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED;
        bossBar.setColor(color);

        if (now % BOSSBAR_UPDATE_INTERVAL == 0) {
            int seconds = (int) Math.ceil(remaining / 20.0);
            bossBar.setName(Component.translatable("devmod.riftstamp.bossbar", seconds));
        }

        double cx = session.center().getX() + 0.5;
        double cy = session.center().getY() + 1.0;
        double cz = session.center().getZ() + 0.5;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(cx, cy, cz) <= BOSSBAR_RANGE_SQ) {
                bossBar.addPlayer(player);
            } else {
                bossBar.removePlayer(player);
            }
        }
    }

    private static boolean isWithinWorldBorder(ServerLevel level, BlockPos basePos) {
        var border = level.getWorldBorder();
        double minX = border.getMinX();
        double maxX = border.getMaxX();
        double minZ = border.getMinZ();
        double maxZ = border.getMaxZ();
        int maxBlockX = basePos.getX() + FOOTPRINT_SIZE - 1;
        int maxBlockZ = basePos.getZ() + FOOTPRINT_SIZE - 1;
        return basePos.getX() >= minX && maxBlockX <= maxX && basePos.getZ() >= minZ && maxBlockZ <= maxZ;
    }

    private record RiftKey(ResourceKey<net.minecraft.world.level.Level> dimension, ChunkPos chunkPos) {}

    private record RiftSession(ServerLevel level, ChunkPos chunkPos, BlockPos basePos, BlockPos center,
                               StructureTemplate snapshot, BoundingBox bounds, AABB teleportBox,
                               ServerBossEvent bossBar, long startTick, long endTick) {}
}
