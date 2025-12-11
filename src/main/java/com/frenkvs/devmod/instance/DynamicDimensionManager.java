package com.frenkvs.devmod.instance;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.mixin.MinecraftServerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.storage.DerivedLevelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.RelativeMovement;

/**
 * Manages dynamic creation and destruction of instance dimensions at runtime.
 *
 * This class handles:
 * - Creating new void dimensions for arena instances
 * - Tracking active dimensions
 * - Safely destroying dimensions and cleaning up files
 * - Teleporting players between dimensions
 *
 * Note: This requires Mixin hooks to inject dimensions into MinecraftServer.levels
 */
public class DynamicDimensionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicDimensionManager.class);
    public static final DynamicDimensionManager INSTANCE = new DynamicDimensionManager();

    // Tracks dimensions we've created dynamically
    private final Map<ResourceKey<Level>, UUID> dimensionToInstance = new ConcurrentHashMap<>();
    private final Map<UUID, ResourceKey<Level>> instanceToDimension = new ConcurrentHashMap<>();

    private MinecraftServer server;
    private boolean initialized = false;

    private DynamicDimensionManager() {}

    /**
     * Initialize the manager with server reference.
     * Called during ServerStartedEvent.
     */
    public void initialize(MinecraftServer server) {
        this.server = server;
        this.initialized = true;
        LOGGER.info("[DynamicDim] Initialized with server");
    }

    /**
     * Shutdown the manager.
     * Called during ServerStoppingEvent.
     */
    public void shutdown() {
        // Force destroy all remaining instances
        for (UUID instanceId : new ArrayList<>(instanceToDimension.keySet())) {
            try {
                destroyDimensionSync(instanceId);
            } catch (Exception e) {
                LOGGER.error("[DynamicDim] Failed to destroy dimension for instance {} during shutdown", instanceId, e);
            }
        }

        this.server = null;
        this.initialized = false;
        dimensionToInstance.clear();
        instanceToDimension.clear();

        // Clear DH registrations on shutdown
        com.frenkvs.devmod.integration.DistantHorizonsIntegration.clearAllRegistrations();

        LOGGER.info("[DynamicDim] Shutdown complete");
    }

    // === Dimension Creation ===

    /**
     * Create a new instance dimension asynchronously.
     * Returns a CompletableFuture that resolves when the dimension is ready.
     *
     * IMPORTANT: Dimension creation MUST happen on the server thread because it
     * modifies MinecraftServer.levels which is not thread-safe.
     *
     * @param instanceId The instance this dimension belongs to
     * @param arenaId The arena template to use for generation
     * @return CompletableFuture with the dimension key, or null on failure
     */
    public CompletableFuture<ResourceKey<Level>> createDimensionAsync(UUID instanceId, String arenaId) {
        if (!initialized || server == null) {
            LOGGER.error("[DynamicDim] Not initialized, cannot create dimension");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<ResourceKey<Level>> future = new CompletableFuture<>();

        // Schedule creation on server thread to ensure thread safety
        // MinecraftServer.levels map is NOT thread-safe and must only be modified
        // from the server thread
        server.execute(() -> {
            try {
                ResourceKey<Level> result = createDimensionSync(instanceId, arenaId);
                future.complete(result);
            } catch (Exception e) {
                LOGGER.error("[DynamicDim] Failed to create dimension for instance {}", instanceId, e);
                future.complete(null);
            }
        });

        return future;
    }

    /**
     * Create a new instance dimension synchronously.
     * Must be called from the server thread.
     *
     * @param instanceId The instance this dimension belongs to
     * @param arenaId The arena template to use for generation
     * @return The dimension key, or null on failure
     */
    @Nullable
    public ResourceKey<Level> createDimensionSync(UUID instanceId, String arenaId) {
        if (!initialized || server == null) {
            LOGGER.error("[DynamicDim] Not initialized, cannot create dimension");
            return null;
        }

        // Create unique dimension name
        String dimensionName = "instance_" + instanceId.toString().replace("-", "");
        ResourceLocation dimensionLocation = nn(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, dimensionName), "dimension location");
        ResourceKey<? extends net.minecraft.core.Registry<Level>> dimensionRegistryKey =
            nn(Registries.DIMENSION, "dimension registry key");
        ResourceKey<Level> dimensionKey = ResourceKey.create(dimensionRegistryKey, dimensionLocation);

        LOGGER.info("[DynamicDim] Creating dimension {} for instance {}", dimensionLocation, instanceId);

        try {
            // Create the dimension using the server's dimension registry
            ServerLevel newLevel = createVoidDimension(dimensionKey, arenaId);

            if (newLevel != null) {
                // Track the mapping
                dimensionToInstance.put(dimensionKey, instanceId);
                instanceToDimension.put(instanceId, dimensionKey);

                LOGGER.info("[DynamicDim] Successfully created dimension {} for instance {}",
                    dimensionLocation, instanceId);

                return dimensionKey;
            } else {
                LOGGER.error("[DynamicDim] Failed to create ServerLevel for {}", dimensionLocation);
                return null;
            }

        } catch (Exception e) {
            LOGGER.error("[DynamicDim] Exception creating dimension {}", dimensionLocation, e);
            return null;
        }
    }

    /**
     * Creates a void dimension with a flat bedrock platform for the arena.
     */
    private ServerLevel createVoidDimension(ResourceKey<Level> dimensionKey, String arenaId) {
        // Get dimension type reference
        ResourceKey<? extends net.minecraft.core.Registry<? extends net.minecraft.world.level.dimension.DimensionType>> dimensionTypeRegistryKey =
            nn(Registries.DIMENSION_TYPE, "dimension type registry key");
        ResourceKey<net.minecraft.world.level.dimension.DimensionType> overworldType =
            nn(BuiltinDimensionTypes.OVERWORLD, "overworld dimension type");
        var dimensionTypeRegistry = nn(server.registryAccess().registryOrThrow(dimensionTypeRegistryKey), "dimension type registry");
        var dimensionType = nn(dimensionTypeRegistry.getHolderOrThrow(overworldType), "dimension type");

        // Create flat generator settings for void world
        // This creates a minimal world with just bedrock at y=0
        ResourceKey<? extends net.minecraft.core.Registry<? extends net.minecraft.world.level.biome.Biome>> biomeRegistryKey =
            nn(Registries.BIOME, "biome registry key");
        var biomeRegistry = nn(server.registryAccess().registryOrThrow(biomeRegistryKey), "biome registry");
        ResourceKey<net.minecraft.world.level.biome.Biome> voidBiomeKey =
            nn(Biomes.THE_VOID, "void biome key");

        var biomeHolder = nn(biomeRegistry.getHolderOrThrow(voidBiomeKey), "void biome");

        FlatLevelGeneratorSettings flatSettings = new FlatLevelGeneratorSettings(
            nn(Optional.<HolderSet<StructureSet>>empty(), "structures"),
            biomeHolder,
            nn(List.<Holder<PlacedFeature>>of(), "features")
        );

        // Ensure the void world has exactly one bedrock layer and no fillers
        // LayersInfo is the editable representation converted internally to states
        List<FlatLayerInfo> layerInfo = flatSettings.getLayersInfo();
        layerInfo.clear();
        layerInfo.add(new FlatLayerInfo(1, nn(Blocks.BEDROCK, "bedrock block")));
        flatSettings.updateLayers();

        // Note: We need to set layers via reflection or use a custom generator
        // For now, create a flat source that will generate void
        ChunkGenerator chunkGenerator = new FlatLevelSource(flatSettings);

        // Create the level stem
        LevelStem levelStem = new LevelStem(dimensionType, chunkGenerator);

        // Use Mixin accessor to add dimension to server
        // This is the critical part that requires the Mixin
        ServerLevel newLevel = injectDimension(dimensionKey, levelStem);

        if (newLevel != null) {
            // Generate the arena platform
            generateArenaPlatform(newLevel, arenaId);
        }

        return newLevel;
    }

    /**
     * Injects a new dimension into the running server.
     * Uses Mixin accessor to access MinecraftServer.levels map.
     */
    @Nullable
    private ServerLevel injectDimension(ResourceKey<Level> dimensionKey, LevelStem levelStem) {
        try {
            // Get the levels map via Mixin accessor
            Map<ResourceKey<Level>, ServerLevel> levels = ((MinecraftServerAccessor) server).getLevels();

            // Check if dimension already exists
            if (levels.containsKey(dimensionKey)) {
                LOGGER.warn("[DynamicDim] Dimension {} already exists!", dimensionKey.location());
                return levels.get(dimensionKey);
            }

            // Get overworld as template for world settings
            ServerLevel overworld = server.overworld();

            // Create derived level data for the new dimension
            DerivedLevelData derivedLevelData = new DerivedLevelData(
                nn(server.getWorldData(), "world data"),
                nn(server.getWorldData().overworldData(), "overworld data")
            );

            // Get executor and storage via accessor
            MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;
            var executor = nn(accessor.getExecutor(), "executor");
            var storage = nn(accessor.getStorageSource(), "storage");
            List<net.minecraft.world.level.CustomSpawner> customSpawners = nn(List.of(), "custom spawners");

            // Create the new ServerLevel
            // Note: This is a simplified creation - full implementation may need more setup
            // IMPORTANT: tickTime must be TRUE for entities to have AI and tick properly
        ServerLevel newLevel = new ServerLevel(
            nn(server, "server"),
            executor,
            storage,
            derivedLevelData,
            nn(dimensionKey, "dimension key"),
            nn(levelStem, "level stem"),
            new NoOpChunkProgressListener(),
            false,  // isDebug
            overworld.getSeed(),
            customSpawners,
            true,   // tickTime - MUST be true for entity AI to work!
            null    // randomSequences
        );

            // Add to the levels map
            levels.put(dimensionKey, newLevel);

            // Register with Distant Horizons for LOD compatibility
            com.frenkvs.devmod.integration.DistantHorizonsIntegration.registerDynamicDimension(newLevel);

            LOGGER.info("[DynamicDim] Successfully injected dimension {}", dimensionKey.location());
            return newLevel;

        } catch (Exception e) {
            LOGGER.error("[DynamicDim] Failed to inject dimension {}: {}", dimensionKey.location(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * No-op chunk progress listener for dynamic dimensions.
     */
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

    /**
     * Generates the arena platform in the void dimension.
     * This creates the physical arena structure.
     */
    private void generateArenaPlatform(ServerLevel level, String arenaId) {
        // Default spawn position
        BlockPos center = new BlockPos(0, 64, 0);
        int radius = 25;  // 50x50 platform

        // Preload the center chunk to avoid void fall during early teleports
        level.getChunkAt(nn(center, "center chunk"));

        // Force-load chunks to ensure entities tick properly
        // This is critical for mobs AI to work in the instance dimension
        // We use multiple approaches to guarantee entity ticking:
        // 1. setChunkForced - marks chunk as force-loaded
        // 2. PLAYER ticket - ensures entity ticking level
        int chunkRadius = (radius / 16) + 2;  // Cover arena + margin
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;

        ChunkPos centerChunkPos = new ChunkPos(centerChunkX, centerChunkZ);

        int chunksForced = 0;
        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                int chunkX = centerChunkX + cx;
                int chunkZ = centerChunkZ + cz;
                // setChunkForced ensures chunk stays loaded AND entities tick
                level.setChunkForced(chunkX, chunkZ, true);
                chunksForced++;
            }
        }

        // Add PLAYER ticket to center chunk to guarantee entity ticking
        // PLAYER tickets have level 33 - 31 = 2, which enables entity ticking
        level.getChunkSource().addRegionTicket(
            nn(net.minecraft.server.level.TicketType.PLAYER, "player ticket type"),
            centerChunkPos,
            3,  // radius in chunks around the center
            centerChunkPos
        );

        LOGGER.info("[DynamicDim] Force-loaded {} chunks around arena center with PLAYER ticket", chunksForced);

        LOGGER.debug("[DynamicDim] Generating arena platform at {} with radius {}", center, radius);

        // Generate a circular stone platform
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    BlockPos pos = center.offset(x, 0, z);

                    // Main platform layer
                    level.setBlock(nn(pos, "platform pos"), nn(Blocks.STONE_BRICKS.defaultBlockState(), "stone bricks"), 2);

                    // Add some variation
                    if ((x + z) % 7 == 0) {
                        level.setBlock(nn(pos, "platform pos"), nn(Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), "chiseled bricks"), 2);
                    }
                }
            }
        }

        // Add corner pillars
        int pillarHeight = 4;
        int[][] corners = {{-radius, -radius}, {radius, -radius}, {-radius, radius}, {radius, radius}};
        for (int[] corner : corners) {
            BlockPos pillarBase = center.offset(corner[0], 0, corner[1]);
            for (int y = 0; y <= pillarHeight; y++) {
                level.setBlock(nn(pillarBase.above(y), "pillar pos"), nn(Blocks.STONE_BRICK_WALL.defaultBlockState(), "pillar block"), 2);
            }
            // Torch on top
            level.setBlock(nn(pillarBase.above(pillarHeight + 1), "torch pos"), nn(Blocks.TORCH.defaultBlockState(), "torch block"), 2);
        }

        // Add spawn marker at center
        level.setBlock(nn(center.above(1), "lodestone pos"), nn(Blocks.LODESTONE.defaultBlockState(), "lodestone block"), 2);

        LOGGER.info("[DynamicDim] Arena platform generated for arena type: {}", arenaId);
    }

    // === Dimension Destruction ===

    /**
     * Destroy an instance dimension asynchronously.
     * Ensures all players are removed first.
     *
     * IMPORTANT: Dimension destruction MUST happen on the server thread because it
     * modifies MinecraftServer.levels which is not thread-safe.
     */
    public CompletableFuture<Boolean> destroyDimensionAsync(UUID instanceId) {
        if (!initialized || server == null) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        // Schedule destruction on server thread to ensure thread safety
        server.execute(() -> {
            try {
                boolean result = destroyDimensionSync(instanceId);
                future.complete(result);
            } catch (Exception e) {
                LOGGER.error("[DynamicDim] Failed to destroy dimension for instance {}", instanceId, e);
                future.complete(false);
            }
        });

        return future;
    }

    /**
     * Destroy an instance dimension synchronously.
     * Must be called from the server thread.
     *
     * IMPORTANT: Cleanup order is critical to prevent resource leaks:
     * 1. Eject all players
     * 2. Unload dimension (closes ServerLevel resources)
     * 3. Clean up tracking maps
     * 4. Delete files (only after resources are released)
     */
    public boolean destroyDimensionSync(UUID instanceId) {
        ResourceKey<Level> dimensionKey = instanceToDimension.get(instanceId);
        if (dimensionKey == null) {
            LOGGER.warn("[DynamicDim] No dimension found for instance {}", instanceId);
            return false;
        }

        LOGGER.info("[DynamicDim] Destroying dimension for instance {}", instanceId);

        boolean unloadSucceeded = false;
        ServerLevel level = server.getLevel(dimensionKey);
        if (level != null) {
            // 1. Check for players still in dimension
            List<ServerPlayer> playersInDim = new ArrayList<>(level.players());
            if (!playersInDim.isEmpty()) {
                LOGGER.warn("[DynamicDim] {} players still in dimension, force ejecting", playersInDim.size());
                for (ServerPlayer player : playersInDim) {
                    // Force teleport to overworld spawn
                    ServerLevel overworld = server.overworld();
                    BlockPos spawnPos = overworld.getSharedSpawnPos();
                    player.teleportTo(
                        overworld,
                        spawnPos.getX() + 0.5,
                        spawnPos.getY(),
                        spawnPos.getZ() + 0.5,
                        nn(Set.<RelativeMovement>of(), "movement flags"),
                        player.getYRot(),
                        player.getXRot()
                    );
                }
            }

            // 2. Unload the dimension (closes resources)
            // This requires Mixin to remove from MinecraftServer.levels
            unloadSucceeded = unloadDimension(dimensionKey, level);
        } else {
            // Level doesn't exist in memory - safe to proceed with cleanup
            unloadSucceeded = true;
        }

        // 3. Unregister from Distant Horizons BEFORE cleanup
        com.frenkvs.devmod.integration.DistantHorizonsIntegration.unregisterDynamicDimension(dimensionKey);

        // 4. Clean up tracking maps BEFORE file deletion
        // This prevents other code from trying to use this dimension
        dimensionToInstance.remove(dimensionKey);
        instanceToDimension.remove(instanceId);

        // 5. Delete dimension files ONLY if unload succeeded
        // This prevents file-in-use errors and ensures resources are released
        if (unloadSucceeded) {
            deleteDimensionFiles(instanceId);
        } else {
            LOGGER.warn("[DynamicDim] Skipping file deletion for instance {} - unload failed", instanceId);
        }

        LOGGER.info("[DynamicDim] Dimension destroyed for instance {} (files deleted: {})",
            instanceId, unloadSucceeded);
        return true;
    }

    /**
     * Unloads a dimension from the server.
     * Uses Mixin accessor to remove from levels map.
     *
     * @return true if unload succeeded, false if critical errors occurred
     */
    private boolean unloadDimension(ResourceKey<Level> dimensionKey, ServerLevel level) {
        try {
            // 1. Save dimension data before unloading
            level.save(null, true, false);

            // 2. Get the levels map via Mixin accessor
            Map<ResourceKey<Level>, ServerLevel> levels = ((MinecraftServerAccessor) server).getLevels();

            // 3. Remove from the levels map
            levels.remove(dimensionKey);

            // 4. Close the level's resources
            try {
                level.close();
            } catch (Exception e) {
                LOGGER.warn("[DynamicDim] Error closing level: {}", e.getMessage());
                // Non-fatal - resources may still be released
            }

            LOGGER.info("[DynamicDim] Dimension {} unloaded successfully", dimensionKey.location());
            return true;

        } catch (Exception e) {
            LOGGER.error("[DynamicDim] Error unloading dimension {}: {}", dimensionKey.location(), e.getMessage());
            return false;
        }
    }

    /**
     * Deletes all files associated with an instance dimension.
     */
    private void deleteDimensionFiles(UUID instanceId) {
        if (server == null) return;

        String dimensionName = "instance_" + instanceId.toString().replace("-", "");
        var rootResource = nn(net.minecraft.world.level.storage.LevelResource.ROOT, "level resource root");
        Path dimensionPath = nn(server.getWorldPath(rootResource), "world path")
            .resolve("dimensions")
            .resolve(DevMod.MODID)
            .resolve(dimensionName);

        if (!Files.exists(dimensionPath)) {
            LOGGER.debug("[DynamicDim] No dimension folder to delete: {}", dimensionPath);
            return;
        }

        try {
            // Recursively delete all files
            deleteRecursively(dimensionPath);
            LOGGER.info("[DynamicDim] Deleted dimension folder: {}", dimensionPath);
        } catch (IOException e) {
            LOGGER.error("[DynamicDim] Failed to delete dimension folder: {}", dimensionPath, e);
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    // === Teleportation ===

    /**
     * Teleport a player to an instance dimension.
     *
     * @param player The player to teleport
     * @param instanceId The instance to teleport to
     * @return true if teleport succeeded
     */
    public boolean teleportToInstance(ServerPlayer player, UUID instanceId) {
        ResourceKey<Level> dimensionKey = instanceToDimension.get(instanceId);
        if (dimensionKey == null) {
            LOGGER.error("[DynamicDim] No dimension found for instance {}", instanceId);
            return false;
        }

        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) {
            LOGGER.error("[DynamicDim] Level not found for dimension {}", dimensionKey.location());
            return false;
        }

        // Teleport to arena center
        BlockPos spawnPos = new BlockPos(0, 65, 0);  // One block above the platform

        // Make sure the destination chunk is fully loaded before teleport
        level.getChunkAt(spawnPos);

        player.teleportTo(
            level,
            spawnPos.getX() + 0.5,
            spawnPos.getY(),
            spawnPos.getZ() + 0.5,
            nn(Set.<RelativeMovement>of(), "movement flags"),
            0,  // Face north
            0   // Level pitch
        );

        LOGGER.info("[DynamicDim] Teleported {} to instance {}", player.getName().getString(), instanceId);
        return true;
    }

    /**
     * Teleport a player back to the overworld spawn.
     */
    public boolean teleportToOverworld(ServerPlayer player) {
        if (server == null) return false;

        ServerLevel overworld = server.overworld();
        BlockPos spawnPos = overworld.getSharedSpawnPos();

        player.teleportTo(
            overworld,
            spawnPos.getX() + 0.5,
            spawnPos.getY(),
            spawnPos.getZ() + 0.5,
            nn(Set.<RelativeMovement>of(), "movement flags"),
            player.getYRot(),
            player.getXRot()
        );

        LOGGER.debug("[DynamicDim] Teleported {} to overworld", player.getName().getString());
        return true;
    }

    // === Queries ===

    /**
     * Get the instance ID for a dimension key.
     */
    public Optional<UUID> getInstanceForDimension(ResourceKey<Level> dimensionKey) {
        return Optional.ofNullable(dimensionToInstance.get(dimensionKey));
    }

    /**
     * Get the dimension key for an instance.
     */
    public Optional<ResourceKey<Level>> getDimensionForInstance(UUID instanceId) {
        return Optional.ofNullable(instanceToDimension.get(instanceId));
    }

    /**
     * Check if a dimension is an instance dimension we created.
     */
    public boolean isInstanceDimension(ResourceKey<Level> dimensionKey) {
        return dimensionToInstance.containsKey(dimensionKey);
    }

    /**
     * Get all active instance dimensions.
     */
    public Set<UUID> getActiveInstances() {
        return new HashSet<>(instanceToDimension.keySet());
    }

    /**
     * Check if manager is initialized and ready.
     */
    public boolean isReady() {
        return initialized && server != null;
    }

    /**
     * Utility to document non-null expectations for APIs that aren't annotated.
     */
    @Nonnull
    private static <T> T nn(T value, String context) {
        return Objects.requireNonNull(value, context);
    }
}
