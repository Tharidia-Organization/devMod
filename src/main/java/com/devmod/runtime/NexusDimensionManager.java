package com.devmod.runtime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.storage.DerivedLevelData;

import com.devmod.DevMod;
import com.devmod.arena.zone.ArenaZone;
import com.devmod.config.Config;
import com.devmod.mixin.MinecraftServerAccessor;
import com.devmod.runtime.biome.ZoneBiomeSource;
import com.devmod.runtime.generator.ArenaChunkGenerator;
import com.devmod.runtime.generator.ArenaFlatChunkGenerator;

public class NexusDimensionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusDimensionManager.class);
    public static final NexusDimensionManager INSTANCE = new NexusDimensionManager();

    public static final ResourceKey<Level> NEXUS_DIMENSION = ResourceKey.create(
        Objects.requireNonNull(Registries.DIMENSION, "Registries.DIMENSION"),
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "nexus"), "ResourceLocation")
    );

    private static final BlockPos HUB_ORIGIN = new BlockPos(0, 64, 0);
    private static final int HUB_HALF_SIZE = 96;

    // Thread-safe: accessed from server thread and potentially config reload callbacks
    private final Set<ChunkPos> forcedChunks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile ChunkPos forcedCenter;
    private volatile int forcedRadius = -1;
    private volatile int forcedTickDistance = -1;
    private volatile boolean forcedEnabled = false;
    private volatile NexusBuildTask buildTask;
    private volatile int pendingBuildVersion = NexusHubSavedData.CURRENT_VERSION;
    private volatile long tickCounter;

    private NexusDimensionManager() {}

    public static BlockPos getHubOrigin() {
        return HUB_ORIGIN;
    }

    public void ensureNexusDimension(MinecraftServer server) {
        if (server == null) {
            return;
        }

        if (!server.isSameThread()) {
            server.execute(() -> ensureNexusDimension(server));
            return;
        }

        if (!Config.NEXUS_ENABLED.get()) {
            return;
        }

        ServerLevel level = server.getLevel(nn(NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        if (level == null) {
            level = createNexusDimension(server);
        }
        if (level != null) {
            applyRuntimeConfig(level);
            ensureHubBuilt(level);
        }
    }

    public void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (!Config.NEXUS_ENABLED.get()) {
            return;
        }
        ServerLevel level = server.getLevel(nn(NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        if (level == null) {
            return;
        }

        tickCounter++;

        if (buildTask != null && buildTask.tick()) {
            finalizeBuild(level, NexusHubSavedData.get(server), pendingBuildVersion);
            buildTask = null;
        }

        boolean hasPlayers = !level.players().isEmpty();
        boolean shouldTick = switch (Config.NEXUS_TICK_MODE.get()) {
            case ALWAYS -> true;
            case PLAYERS_ONLY -> hasPlayers;
            case THROTTLED -> hasPlayers || (tickCounter % Config.NEXUS_IDLE_TICK_INTERVAL.get() == 0);
        };
        if (!shouldTick) {
            return;
        }

        try {
            level.tick(() -> true);
        } catch (Exception e) {
            LOGGER.warn("[Nexus] Failed to tick dimension: {}", e.getMessage());
        }

        if (hasPlayers && Config.NEXUS_AMBIENT_EFFECTS.get()) {
            int interval = Config.NEXUS_AMBIENT_PARTICLE_INTERVAL.get();
            if (interval > 0 && tickCounter % interval == 0) {
                spawnAmbientParticles(level);
            }
        }

        if (hasPlayers) {
            NexusZoneTracker.tick(level);
        }

        // Animate the Nexus avatar (floating, particles)
        NexusAvatarManager.tick(level, nn(HUB_ORIGIN, "HUB_ORIGIN"));

        // Tick portal particles
        NexusPortalManager.INSTANCE.tick(level, nn(HUB_ORIGIN, "HUB_ORIGIN"));

        // Tick hologram updates
        NexusHologramManager.INSTANCE.tick(level, nn(HUB_ORIGIN, "HUB_ORIGIN"));

        // Tick performance optimizations
        NexusPerformanceManager.INSTANCE.tick(level, nn(HUB_ORIGIN, "HUB_ORIGIN"));
    }

    @Nullable
    private ServerLevel createNexusDimension(MinecraftServer server) {
        try {
            LevelStem levelStem = createLevelStem(server);
            return injectDimension(server, levelStem);
        } catch (Exception e) {
            LOGGER.error("[Nexus] Failed to create Nexus dimension", e);
            return null;
        }
    }

    private LevelStem createLevelStem(MinecraftServer server) {
        ResourceKey<net.minecraft.world.level.dimension.DimensionType> overworldType =
            nn(BuiltinDimensionTypes.OVERWORLD, "overworld dimension type");
        var dimensionTypeRegistry = nn(
            server.registryAccess().registryOrThrow(nn(Registries.DIMENSION_TYPE, "Registries.DIMENSION_TYPE")),
            "dimension type registry"
        );
        var dimensionType = nn(dimensionTypeRegistry.getHolderOrThrow(overworldType), "dimension type");

        Holder<net.minecraft.world.level.biome.Biome> biomeHolder = nn(
            server.registryAccess().registryOrThrow(nn(Registries.BIOME, "Registries.BIOME")).getHolderOrThrow(nn(net.minecraft.world.level.biome.Biomes.THE_VOID, "Biomes.THE_VOID")),
            "biome holder"
        );

        FlatLevelGeneratorSettings flatSettings = new FlatLevelGeneratorSettings(
            nn(Optional.<HolderSet<StructureSet>>empty(), "structures"),
            biomeHolder,
            nn(List.<Holder<PlacedFeature>>of(), "features")
        );
        List<FlatLayerInfo> layerInfo = flatSettings.getLayersInfo();
        layerInfo.clear();
        layerInfo.add(new FlatLayerInfo(1, nn(Blocks.BEDROCK, "bedrock block")));
        flatSettings.updateLayers();

        ZoneBiomeSource biomeSource = ZoneBiomeSource.singleBiome(biomeHolder);
        ArenaChunkGenerator.ArenaBounds bounds = ArenaChunkGenerator.ArenaBounds.rectangular(
            -HUB_HALF_SIZE, -HUB_HALF_SIZE, HUB_HALF_SIZE - 1, HUB_HALF_SIZE - 1
        );
        ChunkGenerator generator = new ArenaFlatChunkGenerator(
            biomeSource,
            bounds,
            ArenaZone.ZoneShape.RECTANGULAR,
            flatSettings
        );

        return new LevelStem(dimensionType, nn(generator, "chunk generator"));
    }

    @Nullable
    private ServerLevel injectDimension(MinecraftServer server, LevelStem levelStem) {
        Map<ResourceKey<Level>, ServerLevel> levels = ((MinecraftServerAccessor) server).getLevels();
        if (levels.containsKey(NEXUS_DIMENSION)) {
            return levels.get(NEXUS_DIMENSION);
        }

        ServerLevel overworld = server.overworld();
        DerivedLevelData levelData = new DerivedLevelData(
            nn(server.getWorldData(), "world data"),
            nn(server.getWorldData().overworldData(), "overworld data")
        );

        MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;
        Executor executor = nn(accessor.getExecutor(), "executor");
        var storage = nn(accessor.getStorageSource(), "storage");
        List<net.minecraft.world.level.CustomSpawner> customSpawners = nn(List.of(), "custom spawners");

        ServerLevel newLevel = new ServerLevel(
            nn(server, "server"),
            executor,
            storage,
            levelData,
            nn(NEXUS_DIMENSION, "dimension key"),
            nn(levelStem, "level stem"),
            new NoOpChunkProgressListener(),
            false,
            overworld.getSeed(),
            customSpawners,
            true,
            null
        );

        levels.put(NEXUS_DIMENSION, newLevel);

        try {
            com.devmod.integration.DistantHorizonsIntegration.registerDynamicDimension(newLevel);
        } catch (LinkageError e) {
            LOGGER.debug("[Nexus] Could not register with DH: {}", e.getMessage());
        }

        try {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.neoforged.neoforge.event.level.LevelEvent.Load(newLevel)
            );
        } catch (Exception e) {
            LOGGER.warn("[Nexus] Failed to post LevelEvent.Load: {}", e.getMessage());
        }

        try {
            com.devmod.integration.LittleTilesIntegration.registerDynamicDimension(newLevel);
        } catch (LinkageError e) {
            LOGGER.debug("[Nexus] Could not register with LittleTiles: {}", e.getMessage());
        }

        LOGGER.info("[Nexus] Dimension initialized: {}", NEXUS_DIMENSION.location());
        return newLevel;
    }

    public void applyRuntimeConfig(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (!server.isSameThread()) {
            server.execute(() -> applyRuntimeConfig(server));
            return;
        }
        if (!Config.NEXUS_ENABLED.get()) {
            ServerLevel level = server.getLevel(nn(NEXUS_DIMENSION, "NEXUS_DIMENSION"));
            if (level != null) {
                clearForcedChunks(level);
            }
            return;
        }
        ServerLevel level = server.getLevel(nn(NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        if (level != null) {
            applyRuntimeConfig(level);
        }
    }

    private void applyRuntimeConfig(ServerLevel level) {
        applyWorldBorder(level);
        applyChunkForcing(level);
    }

    private void applyWorldBorder(ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        border.setCenter(HUB_ORIGIN.getX() + 0.5, HUB_ORIGIN.getZ() + 0.5);
        border.setSize(Config.NEXUS_WORLD_BORDER_SIZE.get());
        border.setWarningBlocks(2);
        border.setWarningTime(5);
        border.setDamageSafeZone(1.0);
        border.setDamagePerBlock(0.2);
    }

    private void applyChunkForcing(ServerLevel level) {
        boolean keepLoaded = Config.NEXUS_KEEP_LOADED.get();
        int radius = Config.NEXUS_FORCE_CHUNK_RADIUS.get();
        int tickDistance = Config.NEXUS_TICK_DISTANCE.get();

        if (!keepLoaded) {
            clearForcedChunks(level);
            return;
        }

        ChunkPos center = new ChunkPos(nn(HUB_ORIGIN, "HUB_ORIGIN"));
        boolean needsRefresh = !forcedEnabled || forcedRadius != radius || forcedTickDistance != tickDistance
            || (forcedCenter != null && !forcedCenter.equals(center));

        if (needsRefresh) {
            clearForcedChunks(level);
        }

        if (!needsRefresh && forcedEnabled) {
            return;
        }

        int centerChunkX = center.x;
        int centerChunkZ = center.z;
        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                int chunkX = centerChunkX + cx;
                int chunkZ = centerChunkZ + cz;
                level.setChunkForced(chunkX, chunkZ, true);
                forcedChunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }

        level.getChunkSource().addRegionTicket(
            nn(TicketType.PLAYER, "TicketType.PLAYER"),
            center,
            Math.max(1, tickDistance),
            center
        );

        forcedCenter = center;
        forcedRadius = radius;
        forcedTickDistance = tickDistance;
        forcedEnabled = true;
    }

    private void clearForcedChunks(ServerLevel level) {
        if (!forcedChunks.isEmpty()) {
            for (ChunkPos pos : forcedChunks) {
                level.setChunkForced(pos.x, pos.z, false);
            }
            forcedChunks.clear();
        }

        if (forcedCenter != null && forcedTickDistance > 0) {
            level.getChunkSource().removeRegionTicket(
                nn(TicketType.PLAYER, "TicketType.PLAYER"),
                nn(forcedCenter, "forcedCenter"),
                Math.max(1, forcedTickDistance),
                nn(forcedCenter, "forcedCenter")
            );
        }

        forcedCenter = null;
        forcedRadius = -1;
        forcedTickDistance = -1;
        forcedEnabled = false;
    }

    private void ensureHubBuilt(ServerLevel level) {
        NexusHubSavedData data = NexusHubSavedData.get(level.getServer());
        if (data.isLocked()) {
            return;
        }

        if (buildTask != null) {
            return;
        }

        Config.NexusRebuildPolicy policy = Config.NEXUS_REBUILD_POLICY.get();
        boolean shouldBuild = switch (policy) {
            case ALWAYS -> true;
            case ON_VERSION_MISMATCH -> !data.isBuilt() || data.getVersion() < NexusHubSavedData.CURRENT_VERSION;
            case ONCE -> !data.isBuilt();
            case NEVER -> false;
        };

        if (!shouldBuild) {
            // Hub already exists - initialize portals and holograms for existing hub
            initializeHubEntities(level);
            return;
        }

        startBuild(level, data);
    }

    /**
     * Initialize portal and hologram entities for an existing hub.
     * Called when hub already exists and doesn't need to be rebuilt.
     */
    private void initializeHubEntities(ServerLevel level) {
        LOGGER.debug("[Nexus] Initializing hub entities for existing hub");

        // Initialize portal pedestals (entity markers for teleportation)
        NexusPortalManager.INSTANCE.initialize(nn(level, "level"), nn(HUB_ORIGIN, "HUB_ORIGIN"));

        // Initialize holographic displays
        NexusHologramManager.INSTANCE.initialize(nn(level, "level"), nn(HUB_ORIGIN, "HUB_ORIGIN"));

        // Initialize performance manager
        NexusPerformanceManager.INSTANCE.initialize(nn(HUB_ORIGIN, "HUB_ORIGIN"));

        // Spawn avatar and other entities
        NexusHubBuilder.postBuildEntities(level, HUB_ORIGIN);
    }

    private void startBuild(ServerLevel level, NexusHubSavedData data) {
        pendingBuildVersion = NexusHubSavedData.CURRENT_VERSION;

        if (Config.NEXUS_BUILD_MODE.get() == Config.NexusBuildMode.STAGGERED) {
            LOGGER.info("[Nexus] Scheduling staged hub build (version={})", pendingBuildVersion);
            buildTask = new NexusBuildTask(
                NexusHubBuilder.buildSteps(level, HUB_ORIGIN),
                Config.NEXUS_BUILD_STEP_INTERVAL.get()
            );
            return;
        }

        LOGGER.info("[Nexus] Building hub layout (version={})", pendingBuildVersion);
        long start = System.nanoTime();
        NexusHubBuilder.build(level, HUB_ORIGIN);
        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        LOGGER.info("[Nexus] Hub build completed in {} ms", durationMs);
        finalizeBuild(level, data, pendingBuildVersion);
    }

    private void finalizeBuild(ServerLevel level, NexusHubSavedData data, int version) {
        level.setDefaultSpawnPos(nn(NexusSpawnManager.getDefaultSpawn(nn(HUB_ORIGIN, "HUB_ORIGIN")), "defaultSpawn"), 180.0f);
        data.markBuilt(version);
        level.save(null, true, false);
        applyRuntimeConfig(level);
        NexusHubBuilder.applyOptionalOverlay(level, HUB_ORIGIN);
        NexusHubBuilder.postBuildEntities(level, HUB_ORIGIN);

        // Initialize portal pedestals
        NexusPortalManager.INSTANCE.initialize(level, nn(HUB_ORIGIN, "HUB_ORIGIN"));

        // Initialize holographic displays
        NexusHologramManager.INSTANCE.initialize(level, nn(HUB_ORIGIN, "HUB_ORIGIN"));

        // Initialize performance manager
        NexusPerformanceManager.INSTANCE.initialize(nn(HUB_ORIGIN, "HUB_ORIGIN"));
    }

    public boolean requestRebuild(MinecraftServer server, boolean ignoreLock) {
        if (server == null) {
            return false;
        }
        if (!server.isSameThread()) {
            server.execute(() -> requestRebuild(server, ignoreLock));
            return true;
        }
        if (!Config.NEXUS_ENABLED.get()) {
            return false;
        }
        ServerLevel level = server.getLevel(nn(NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        if (level == null) {
            ensureNexusDimension(server);
            level = server.getLevel(nn(NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        }
        if (level == null) {
            return false;
        }
        if (buildTask != null) {
            return false;
        }

        NexusHubSavedData data = NexusHubSavedData.get(server);
        if (data.isLocked() && !ignoreLock) {
            return false;
        }
        if (ignoreLock && data.isLocked()) {
            data.setLocked(false);
        }
        data.markUnbuilt();
        startBuild(level, data);
        return true;
    }

    public void setHubLocked(MinecraftServer server, boolean locked) {
        if (server == null) {
            return;
        }
        if (!server.isSameThread()) {
            server.execute(() -> setHubLocked(server, locked));
            return;
        }
        NexusHubSavedData data = NexusHubSavedData.get(server);
        data.setLocked(locked);
    }

    public void teleportPlayerToSpawn(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        if (!server.isSameThread()) {
            server.execute(() -> teleportPlayerToSpawn(player));
            return;
        }

        ServerLevel level = server.getLevel(nn(NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        if (level == null) {
            ensureNexusDimension(server);
            level = server.getLevel(nn(NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        }
        if (level == null) {
            return;
        }

        BlockPos spawn = NexusSpawnManager.getSpawnForPlayer(player, HUB_ORIGIN);
        teleportPlayerTo(level, player, spawn);
    }

    public void teleportPlayerToZone(net.minecraft.server.level.ServerPlayer player, NexusSpawnManager.Zone zone) {
        if (player == null || zone == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        if (!server.isSameThread()) {
            server.execute(() -> teleportPlayerToZone(player, zone));
            return;
        }

        if (!player.level().dimension().equals(NEXUS_DIMENSION)) {
            NexusReturnSavedData.get(server).recordReturn(player);
        }

        ServerLevel level = server.getLevel(nn(NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        if (level == null) {
            ensureNexusDimension(server);
            level = server.getLevel(nn(NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        }
        if (level == null) {
            return;
        }

        BlockPos spawn = NexusSpawnManager.getSpawnForZone(HUB_ORIGIN, zone);
        teleportPlayerTo(level, player, spawn);
    }

    public boolean teleportPlayerToReturn(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        if (!server.isSameThread()) {
            server.execute(() -> teleportPlayerToReturn(player));
            return false;
        }

        NexusReturnSavedData data = NexusReturnSavedData.get(server);
        NexusReturnSavedData.ReturnPoint point = data.getReturn(player.getUUID());
        if (point == null) {
            return false;
        }
        ServerLevel target = NexusReturnSavedData.resolveReturnLevel(server, point);
        if (target == null) {
            return false;
        }
        player.teleportTo(target,
            point.pos().getX() + 0.5,
            point.pos().getY() + 1.0,
            point.pos().getZ() + 0.5,
            point.yaw(),
            point.pitch()
        );
        data.clearReturn(player.getUUID());
        return true;
    }

    private void teleportPlayerTo(ServerLevel level, net.minecraft.server.level.ServerPlayer player, BlockPos spawn) {
        player.teleportTo(nn(level, "level"),
            spawn.getX() + 0.5,
            spawn.getY() + 1.0,
            spawn.getZ() + 0.5,
            player.getYRot(),
            player.getXRot()
        );
    }

    public void shutdown(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (!server.isSameThread()) {
            server.execute(() -> shutdown(server));
            return;
        }
        ServerLevel level = server.getLevel(nn(NEXUS_DIMENSION, "NEXUS_DIMENSION"));
        if (level != null) {
            clearForcedChunks(level);
            NexusPortalManager.INSTANCE.cleanup(level);
            NexusHologramManager.INSTANCE.cleanup(level);
            NexusAvatarManager.cleanup(level, nn(HUB_ORIGIN, "HUB_ORIGIN"));
        }
        NexusPerformanceManager.INSTANCE.cleanup();
        buildTask = null;
    }

    private void spawnAmbientParticles(ServerLevel level) {
        double x = HUB_ORIGIN.getX() + 0.5;
        double y = HUB_ORIGIN.getY() + 4.2;
        double z = HUB_ORIGIN.getZ() + 0.5;
        level.sendParticles(nn(ParticleTypes.ELECTRIC_SPARK, "ParticleTypes.ELECTRIC_SPARK"), x, y, z, 6, 0.8, 0.6, 0.8, 0.0);
        level.sendParticles(nn(ParticleTypes.END_ROD, "ParticleTypes.END_ROD"), x, y + 2.0, z, 4, 0.6, 0.8, 0.6, 0.0);
    }

    private static class NoOpChunkProgressListener implements ChunkProgressListener {
        @Override
        public void updateSpawnPos(@Nonnull net.minecraft.world.level.ChunkPos pos) {}

        @Override
        public void onStatusChange(@Nonnull net.minecraft.world.level.ChunkPos pos, @Nullable net.minecraft.world.level.chunk.status.ChunkStatus status) {}

        @Override
        public void start() {}

        @Override
        public void stop() {}
    }

    @Nonnull
    private static <T> T nn(T value, String context) {
        return Objects.requireNonNull(value, context);
    }
}
