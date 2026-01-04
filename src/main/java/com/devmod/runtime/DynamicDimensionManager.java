package com.devmod.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import com.devmod.DevMod;
import com.devmod.arena.config.InstanceLimitConfig;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.registry.InstanceSettingsValidator;
import com.devmod.arena.zone.ArenaZone;
import com.devmod.arena.zone.ZoneLayout;
import com.devmod.mixin.MinecraftServerAccessor;
import com.devmod.runtime.biome.ZoneBiomeSource;
import com.devmod.runtime.environment.DimensionEnvironmentManager;
import com.devmod.runtime.generator.ArenaChunkGenerator;
import com.devmod.runtime.generator.ArenaFlatChunkGenerator;
import com.devmod.runtime.generator.BiomePolicyResolver;
import com.devmod.runtime.generator.DynamicArenaChunkGenerator;

public class DynamicDimensionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicDimensionManager.class);
    public static final DynamicDimensionManager INSTANCE = new DynamicDimensionManager();
    private static final int DEFAULT_PLATFORM_RADIUS = 25;
    private static final int DEFAULT_FORCE_CHUNK_RADIUS = (DEFAULT_PLATFORM_RADIUS / 16) + 2;
    private static final int DEFAULT_TICK_DISTANCE = 3;

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

        // Clear DH registrations on shutdown (wrapped to handle classloader issues during shutdown)
        try {
            com.devmod.integration.DistantHorizonsIntegration.clearAllRegistrations();
        } catch (LinkageError e) {
            LOGGER.debug("[DynamicDim] Could not clear DH registrations during shutdown: {}", e.getMessage());
        }

        LOGGER.info("[DynamicDim] Shutdown complete");
    }

    /**
     * Called when a dimension is unloaded. Invalidates proxy generators that
     * were using this dimension as their source.
     *
     * @param unloadedDimension The dimension key that was unloaded
     */
    public void onDimensionUnload(ResourceKey<Level> unloadedDimension) {
        if (!initialized || server == null || unloadedDimension == null) {
            return;
        }

        LOGGER.debug("[DynamicDim] onDimensionUnload called for {}", unloadedDimension.location());

        // ConcurrentHashMap.keySet() is weakly consistent - safe to iterate while
        // other threads modify the map. Destroyed dimensions return null from getLevel().
        for (ResourceKey<Level> dimKey : dimensionToInstance.keySet()) {
            if (dimKey == null) continue;
            ServerLevel level = server.getLevel(dimKey);
            if (level == null) {
                continue;
            }

            ChunkGenerator gen = level.getChunkSource().getGenerator();
            if (gen instanceof DynamicArenaChunkGenerator dynamicGen) {
                var config = dynamicGen.getConfig();
                // Objects.equals handles null sourceDimension (CONTROLLED_NATURAL mode)
                if (config.mode() == DynamicArenaChunkGenerator.Mode.PROXY_WORLDGEN
                        && Objects.equals(unloadedDimension, config.sourceDimension())) {
                    dynamicGen.invalidateProxy();
                    LOGGER.info("[DynamicDim] Invalidated proxy for {} because source {} was unloaded",
                            dimKey.location(), unloadedDimension.location());
                }
            }
        }
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

        // Resolve biome from template/mob requirements via DimensionEnvironmentManager
        ArenaTemplateRegistry registry = DevMod.getArenaTemplateRegistry();
        ArenaTemplate template = (registry != null && arenaId != null)
            ? registry.get(arenaId).orElse(null)
            : null;

        // Get optimal biome from environment manager
        Holder<net.minecraft.world.level.biome.Biome> biomeHolder = nn(
            DimensionEnvironmentManager.INSTANCE.resolveBiome(
                template,
                null, // mobIds will be set later when quest starts
                server
            ),
            "biome holder from environment manager"
        );
        LOGGER.debug("[DynamicDim] Using biome {} for dimension {}", biomeHolder, dimensionKey.location());

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

        // Create chunk generator - use ArenaFlatChunkGenerator for multi-biome support
        ChunkGenerator chunkGenerator = createChunkGenerator(template, flatSettings, biomeHolder);

        // Create the level stem
        LevelStem levelStem = new LevelStem(dimensionType, nn(chunkGenerator, "chunk generator"));

        // Use Mixin accessor to add dimension to server
        // This is the critical part that requires the Mixin
        ServerLevel newLevel = injectDimension(dimensionKey, levelStem);

        if (newLevel != null) {
            // Initialize proxy generator if using dynamic terrain with proxy mode
            if (chunkGenerator instanceof DynamicArenaChunkGenerator dynamicGen) {
                boolean proxyInitialized = dynamicGen.initializeProxy(server);
                if (!proxyInitialized && dynamicGen.getConfig().mode() == DynamicArenaChunkGenerator.Mode.PROXY_WORLDGEN) {
                    LOGGER.warn("[DynamicDim] Proxy initialization failed, falling back to controlled natural generation");
                }
            }

            // Generate the arena platform
            generateArenaPlatform(newLevel, arenaId);

            // Configure environment settings (time, lighting) via manager
            DimensionEnvironmentManager.INSTANCE.configureEnvironment(
                dimensionKey,
                template,
                null, // mobIds will be set when quest starts
                newLevel
            );
        }

        return newLevel;
    }

    /**
     * Creates the appropriate chunk generator based on template settings.
     * Uses DynamicArenaChunkGenerator for dynamic terrain, ArenaFlatChunkGenerator for flat.
     *
     * @param template The arena template (may be null)
     * @param flatSettings The flat level settings
     * @param defaultBiome The default biome holder
     * @return The configured chunk generator
     */
    private ChunkGenerator createChunkGenerator(
            @Nullable ArenaTemplate template,
            FlatLevelGeneratorSettings flatSettings,
            Holder<net.minecraft.world.level.biome.Biome> defaultBiome
    ) {
        // Check if template has terrain settings with DYNAMIC type
        if (template != null) {
            var terrainSettings = template.terrainSettings();
            if (terrainSettings != null && terrainSettings.type() == ArenaTemplate.TerrainSettings.TerrainType.DYNAMIC) {
                return createDynamicGenerator(template, terrainSettings, defaultBiome);
            }
        }

        // Check if template has zone settings with multiple zones
        var zoneSettings = template != null ? template.zoneSettings() : null;
        if (zoneSettings != null && zoneSettings.enabled()) {
            // Build zone layout if we have zone definitions
            if (!zoneSettings.zones().isEmpty()) {
                ZoneLayout zoneLayout = buildZoneLayoutFromTemplate(template);

                if (zoneLayout != null && !zoneLayout.zones().isEmpty()) {
                    // Create zone-aware biome source
                    var biomeRegistry = server.registryAccess().registryOrThrow(nn(Registries.BIOME, "biome registry key"));
                    ZoneBiomeSource zoneBiomeSource = ZoneBiomeSource.fromZoneLayout(
                            zoneLayout, biomeRegistry, defaultBiome
                    );

                    // Calculate arena bounds
                    ArenaChunkGenerator.ArenaBounds bounds = calculateArenaBounds(template);
                    ArenaZone.ZoneShape shape = determineArenaShape(template);

                    LOGGER.info("[DynamicDim] Using ArenaFlatChunkGenerator with {} zones",
                            zoneLayout.zones().size());

                    return new ArenaFlatChunkGenerator(
                            zoneBiomeSource,
                            bounds,
                            shape,
                            flatSettings
                    );
                }
            }
        }

        // Fallback: Use ArenaFlatChunkGenerator with single default biome
        // We always use our custom generator to ensure structure generation is disabled
        // FlatLevelSource would still do expensive structure checks even with empty structure sets
        ZoneBiomeSource simpleBiomeSource = ZoneBiomeSource.singleBiome(defaultBiome);
        ArenaChunkGenerator.ArenaBounds bounds = calculateArenaBounds(template);
        ArenaZone.ZoneShape shape = determineArenaShape(template);

        LOGGER.debug("[DynamicDim] Using ArenaFlatChunkGenerator (no zones, single biome)");
        return new ArenaFlatChunkGenerator(
                simpleBiomeSource,
                bounds,
                shape,
                flatSettings
        );
    }

    /**
     * Creates a DynamicArenaChunkGenerator based on template terrain settings.
     * Supports PROXY_WORLDGEN (delegates to source dimension) and CONTROLLED_NATURAL (fallback).
     *
     * @param template The arena template
     * @param terrainSettings The terrain settings with dynamic config
     * @param defaultBiome The default biome holder
     * @return The configured dynamic chunk generator
     */
    private ChunkGenerator createDynamicGenerator(
            @Nonnull ArenaTemplate template,
            @Nonnull ArenaTemplate.TerrainSettings terrainSettings,
            Holder<net.minecraft.world.level.biome.Biome> defaultBiome
    ) {
        var dynamicSettings = terrainSettings.dynamic();
        if (dynamicSettings == null) {
            dynamicSettings = ArenaTemplate.TerrainSettings.DynamicSettings.proxyOverworld();
        }

        // Build DynamicConfig from template settings
        DynamicArenaChunkGenerator.Mode mode = dynamicSettings.mode() == ArenaTemplate.TerrainSettings.DynamicSettings.Mode.CONTROLLED_NATURAL
                ? DynamicArenaChunkGenerator.Mode.CONTROLLED_NATURAL
                : DynamicArenaChunkGenerator.Mode.PROXY_WORLDGEN;

        ResourceKey<Level> sourceDimension = null;
        String sourceDimStr = dynamicSettings.sourceDimension();
        if (sourceDimStr != null && !sourceDimStr.isBlank()) {
            ResourceLocation dimLoc = ResourceLocation.tryParse(sourceDimStr);
            if (dimLoc != null) {
                sourceDimension = ResourceKey.create(nn(Registries.DIMENSION, "dimension registry"), dimLoc);
            }
        }
        if (sourceDimension == null && mode == DynamicArenaChunkGenerator.Mode.PROXY_WORLDGEN) {
            sourceDimension = Level.OVERWORLD;
        }

        DynamicArenaChunkGenerator.BiomePolicy biomePolicy = switch (dynamicSettings.biomePolicy()) {
            case FIXED -> DynamicArenaChunkGenerator.BiomePolicy.FIXED;
            case RANDOM_FROM_TAG -> DynamicArenaChunkGenerator.BiomePolicy.RANDOM_FROM_TAG;
            default -> DynamicArenaChunkGenerator.BiomePolicy.MATCH_MOB;
        };

        String fixedBiomeStr = dynamicSettings.fixedBiome();
        ResourceLocation fixedBiome = fixedBiomeStr != null
                ? ResourceLocation.tryParse(fixedBiomeStr)
                : null;

        String biomeTagStr = dynamicSettings.biomeTag();
        ResourceLocation biomeTag = biomeTagStr != null
                ? ResourceLocation.tryParse(biomeTagStr)
                : null;

        DynamicArenaChunkGenerator.DynamicConfig config = new DynamicArenaChunkGenerator.DynamicConfig(
                mode,
                sourceDimension,
                biomePolicy,
                fixedBiome,
                biomeTag,
                dynamicSettings.allowCaves(),
                dynamicSettings.blendRadius(),
                dynamicSettings.combatRingRadius()
        );

        // Validate biome configuration before proceeding
        if (server != null) {
            List<String> issues = BiomePolicyResolver.validateConfig(config, server);
            if (!issues.isEmpty()) {
                LOGGER.warn("[DynamicDim] Biome config validation issues for template '{}': {}",
                        template.id(), issues);
            }
        }

        // Generate deterministic seed from template + world seed (needed for biome resolution)
        long seed = template.id().hashCode() ^ (server != null ? server.overworld().getSeed() : 0L);

        // Resolve biome using BiomePolicyResolver
        // Note: mobId is null at dimension creation time - MATCH_MOB will use default
        Holder<net.minecraft.world.level.biome.Biome> resolvedBiome = defaultBiome;
        if (server != null) {
            resolvedBiome = BiomePolicyResolver.resolve(config, server, null, seed)
                    .orElse(defaultBiome);
            LOGGER.debug("[DynamicDim] BiomePolicy {} resolved to: {}",
                    biomePolicy,
                    resolvedBiome.unwrapKey().map(k -> k.location()).orElse(null));
        }

        // Create zone-aware biome source with resolved biome
        ZoneBiomeSource biomeSource = ZoneBiomeSource.singleBiome(resolvedBiome);

        // Calculate arena bounds and shape
        ArenaChunkGenerator.ArenaBounds bounds = calculateArenaBounds(template);
        ArenaZone.ZoneShape shape = determineArenaShape(template);

        LOGGER.info("[DynamicDim] Using DynamicArenaChunkGenerator mode={}, source={}, caves={}, combatRadius={}, blendRadius={}",
                mode, sourceDimension != null ? sourceDimension.location() : "none",
                dynamicSettings.allowCaves(), config.combatRingRadius(), config.blendRadius());

        return new DynamicArenaChunkGenerator(biomeSource, bounds, shape, config, seed);
    }

    /**
     * Builds a ZoneLayout from template zone settings.
     * Uses striped layout to divide arena into horizontal strips for each zone.
     */
    @Nullable
    private ZoneLayout buildZoneLayoutFromTemplate(@Nullable ArenaTemplate template) {
        if (template == null || template.zoneSettings() == null) {
            return null;
        }

        var zoneSettings = template.zoneSettings();
        if (zoneSettings == null || zoneSettings.zones().isEmpty()) {
            return null;
        }

        // Get arena dimensions
        Integer sizeXValue = template.sizeX();
        Integer sizeZValue = template.sizeZ();
        int sizeX = sizeXValue != null ? sizeXValue.intValue() : template.size();
        int sizeZ = sizeZValue != null ? sizeZValue.intValue() : template.size();
        int halfSize = Math.max(sizeX, sizeZ) / 2;

        // Build zones using striped layout strategy
        ZoneLayout.Builder builder = ZoneLayout.builder(halfSize);

        int totalZones = zoneSettings.zones().size();
        for (int i = 0; i < totalZones; i++) {
            var zoneDef = zoneSettings.zones().get(i);
            ArenaZone zone = createZoneFromDefinition(zoneDef, i, totalZones, halfSize);
            if (zone != null) {
                builder.addZone(zone);
            }
        }

        return builder.build();
    }

    /**
     * Creates an ArenaZone from a zone definition.
     * Calculates bounds using striped layout (horizontal strips).
     */
    @Nullable
    private ArenaZone createZoneFromDefinition(
            ArenaTemplate.ZoneSettings.ZoneDefinition zoneDef,
            int zoneIndex,
            int totalZones,
            int halfSize
    ) {
        try {
            // Calculate bounds using striped layout (divide arena into horizontal strips)
            int stripeHeight = (halfSize * 2) / Math.max(1, totalZones);
            int z1 = -halfSize + (zoneIndex * stripeHeight);
            int z2 = (zoneIndex == totalZones - 1) ? halfSize : z1 + stripeHeight;

            ArenaZone.ZoneBounds bounds = ArenaZone.ZoneBounds.rect(-halfSize, z1, halfSize, z2);

            // Create base zone
            ArenaZone zone = new ArenaZone(
                    zoneDef.name(),
                    ArenaZone.ZoneShape.RECTANGULAR,
                    bounds,
                    com.devmod.arena.zone.ZoneEnvironment.DEFAULT,
                    List.of(),
                    zoneIndex // Priority based on index
            );

            // Apply environment if any zone-specific settings are specified
            boolean hasEnvironmentSettings = zoneDef.biome() != null ||
                    zoneDef.floorMaterial() != null ||
                    zoneDef.skyLight() != null ||
                    zoneDef.blockLight() != null ||
                    zoneDef.time() != null;

            if (hasEnvironmentSettings) {
                String biomeId = zoneDef.biome();
                String floorId = zoneDef.floorMaterial();
                Integer skyLightVal = zoneDef.skyLight();
                Integer blockLightVal = zoneDef.blockLight();
                var env = new com.devmod.arena.zone.ZoneEnvironment(
                        biomeId != null
                                ? Optional.of(ResourceLocation.parse(biomeId))
                                : Optional.empty(),
                        floorId != null
                                ? Optional.of(ResourceLocation.parse(floorId))
                                : Optional.empty(),
                        new com.devmod.arena.zone.ZoneEnvironment.LightingConfig(
                                skyLightVal != null ? skyLightVal.intValue() : 15,
                                blockLightVal != null ? blockLightVal.intValue() : 0,
                                false
                        ),
                        parseTimeConfig(zoneDef.time())
                );
                zone = zone.withEnvironment(env);
            }

            return zone;
        } catch (Exception e) {
            LOGGER.warn("[DynamicDim] Failed to create zone from definition '{}': {}",
                    zoneDef.name(), e.getMessage());
            return null;
        }
    }

    /**
     * Parses a time config string to TimeConfig enum.
     */
    private com.devmod.arena.zone.ZoneEnvironment.TimeConfig parseTimeConfig(@Nullable String time) {
        if (time == null) {
            return com.devmod.arena.zone.ZoneEnvironment.TimeConfig.ANY;
        }
        return switch (time.toLowerCase(java.util.Locale.ROOT)) {
            case "day" -> com.devmod.arena.zone.ZoneEnvironment.TimeConfig.DAY;
            case "night" -> com.devmod.arena.zone.ZoneEnvironment.TimeConfig.NIGHT;
            case "dusk", "sunset" -> com.devmod.arena.zone.ZoneEnvironment.TimeConfig.DUSK;
            case "dawn", "sunrise" -> com.devmod.arena.zone.ZoneEnvironment.TimeConfig.DAWN;
            default -> com.devmod.arena.zone.ZoneEnvironment.TimeConfig.ANY;
        };
    }

    /**
     * Calculates arena bounds from template.
     * Returns shape-appropriate bounds (rectangular, circular, or ring).
     */
    private ArenaChunkGenerator.ArenaBounds calculateArenaBounds(@Nullable ArenaTemplate template) {
        if (template == null) {
            return ArenaChunkGenerator.ArenaBounds.rectangular(-32, -32, 32, 32);
        }

        Integer sizeXVal = template.sizeX();
        Integer sizeZVal = template.sizeZ();
        int sizeX = sizeXVal != null ? sizeXVal.intValue() : template.size();
        int sizeZ = sizeZVal != null ? sizeZVal.intValue() : template.size();
        int halfX = sizeX / 2;
        int halfZ = sizeZ / 2;

        // Origin determines center
        int centerX = template.origin() != null ? template.origin().x() : 0;
        int centerZ = template.origin() != null ? template.origin().z() : 0;

        // Use radius as max of halfX/halfZ for circular shapes
        int radius = Math.max(halfX, halfZ);

        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        return switch (shape) {
            case CIRCULAR -> ArenaChunkGenerator.ArenaBounds.circular(centerX, centerZ, radius);
            case RING -> {
                Integer innerRadiusVal = template.ringInnerRadius();
                int innerRadius = innerRadiusVal != null ? innerRadiusVal.intValue() : radius / 2;
                yield ArenaChunkGenerator.ArenaBounds.ring(centerX, centerZ, radius, innerRadius);
            }
            case RECTANGULAR -> ArenaChunkGenerator.ArenaBounds.rectangular(
                    centerX - halfX, centerZ - halfZ,
                    centerX + halfX, centerZ + halfZ
            );
        };
    }

    /**
     * Determines the arena shape from template.
     * Reads the arenaShape field from the template and maps to ZoneShape.
     */
    private ArenaZone.ZoneShape determineArenaShape(@Nullable ArenaTemplate template) {
        if (template == null || template.arenaShape() == null) {
            return ArenaZone.ZoneShape.RECTANGULAR;
        }
        return switch (template.arenaShape()) {
            case RECTANGULAR -> ArenaZone.ZoneShape.RECTANGULAR;
            case CIRCULAR -> ArenaZone.ZoneShape.CIRCULAR;
            case RING -> ArenaZone.ZoneShape.RING;
        };
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

            // Create custom level data for the instance dimension
            // Using InstanceLevelData to force NORMAL difficulty regardless of server settings
            // This ensures mobs can attack players in endurance quests even if server is PEACEFUL
            InstanceLevelData derivedLevelData = new InstanceLevelData(
                nn(server.getWorldData(), "world data"),
                nn(server.getWorldData().overworldData(), "overworld data")
            );
            LOGGER.debug("[DynamicDim] Using InstanceLevelData with forced NORMAL difficulty for {}", dimensionKey.location());

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
            try {
                com.devmod.integration.DistantHorizonsIntegration.registerDynamicDimension(newLevel);
            } catch (LinkageError e) {
                LOGGER.debug("[DynamicDim] Could not register with DH: {}", e.getMessage());
            }

            // Post LevelEvent.Load to notify other mods about the new dimension
            try {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new net.neoforged.neoforge.event.level.LevelEvent.Load(newLevel)
                );
                LOGGER.debug("[DynamicDim] Posted LevelEvent.Load for {}", dimensionKey.location());
            } catch (Exception e) {
                LOGGER.warn("[DynamicDim] Failed to post LevelEvent.Load: {}", e.getMessage());
            }

            // Register with LittleTiles for animation handler compatibility
            // This forces lazy initialization of the per-level handler
            try {
                com.devmod.integration.LittleTilesIntegration.registerDynamicDimension(newLevel);
            } catch (LinkageError e) {
                LOGGER.debug("[DynamicDim] Could not register with LittleTiles: {}", e.getMessage());
            }

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
        InstanceLoadSettings settings = resolveInstanceSettings(arenaId);

        // Default spawn position
        BlockPos center = new BlockPos(0, 64, 0);
        int radius = DEFAULT_PLATFORM_RADIUS;  // 50x50 platform

        // Preload the center chunk to avoid void fall during early teleports
        level.getChunkAt(nn(center, "center chunk"));

        // Force-load chunks to ensure entities tick properly
        // This is critical for mobs AI to work in the instance dimension
        // We use multiple approaches to guarantee entity ticking:
        // 1. setChunkForced - marks chunk as force-loaded
        // 2. PLAYER ticket - ensures entity ticking level
        int chunkRadius = settings.chunkRadius > 0 ? settings.chunkRadius : DEFAULT_FORCE_CHUNK_RADIUS;
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;

        ChunkPos centerChunkPos = new ChunkPos(centerChunkX, centerChunkZ);

        int chunksForced = 0;
        if (settings.keepLoaded) {
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
            int ticketRadius = Math.max(1, settings.tickDistance);
            level.getChunkSource().addRegionTicket(
                nn(net.minecraft.server.level.TicketType.PLAYER, "player ticket type"),
                centerChunkPos,
                ticketRadius,
                centerChunkPos
            );

            LOGGER.info("[DynamicDim] Force-loaded {} chunks around arena center with PLAYER ticket (radius={})",
                chunksForced, settings.tickDistance);
        } else {
            LOGGER.info("[DynamicDim] keepLoaded=false; skipping forced chunks for template {}", settings.templateId);
        }

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

        LOGGER.info("[DynamicDim] Arena platform generated for arena type: {}", arenaId);
    }

    private InstanceLoadSettings resolveInstanceSettings(@Nullable String arenaId) {
        String templateId = arenaId != null ? arenaId : "unknown";
        ArenaTemplateRegistry registry = DevMod.getArenaTemplateRegistry();
        if (registry == null || arenaId == null || arenaId.isBlank()) {
            return InstanceLoadSettings.defaults(templateId);
        }
        ArenaTemplate template = registry.get(arenaId).orElse(null);
        if (template == null) {
            LOGGER.warn("[DynamicDim] Template '{}' not found; using default instance settings", arenaId);
            return InstanceLoadSettings.defaults(templateId);
        }

        InstanceLimitConfig limits = InstanceLimitConfig.load();
        InstanceSettingsValidator validator = new InstanceSettingsValidator();
        InstanceSettingsValidator.Result result = validator.validate(template, limits.toLimits());
        if (!result.errors().isEmpty()) {
            LOGGER.warn("[DynamicDim] Instance settings invalid for template {}: {}",
                template.id(), String.join("; ", result.errors()));
        }
        if (!result.warnings().isEmpty()) {
            LOGGER.info("[DynamicDim] Instance settings warnings for template {}: {}",
                template.id(), String.join("; ", result.warnings()));
        }

        ArenaTemplate.InstanceSettings settings = template.instanceSettings();
        boolean keepLoaded = settings == null || settings.keepLoaded();
        return new InstanceLoadSettings(
            template.id(),
            Math.max(1, result.effectiveChunkRadius()),
            Math.max(1, result.effectiveTickDistance()),
            keepLoaded
        );
    }

    private record InstanceLoadSettings(String templateId, int chunkRadius, int tickDistance, boolean keepLoaded) {
        private static InstanceLoadSettings defaults(String templateId) {
            return new InstanceLoadSettings(templateId, DEFAULT_FORCE_CHUNK_RADIUS, DEFAULT_TICK_DISTANCE, true);
        }
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
            @Nonnull ResourceKey<Level> fallbackKey = nn(resolveDimensionKey(instanceId), "fallback dimension key");
            Path dimensionPath = resolveDimensionPath(instanceId);
            boolean hasFiles = dimensionPath != null && Files.exists(dimensionPath);
            ServerLevel level = server != null ? server.getLevel(nn(fallbackKey, "fallback dimension key")) : null;
            if (level == null && !hasFiles) {
                LOGGER.debug("[DynamicDim] No tracked dimension for instance {}, treating as already destroyed", instanceId);
                return true;
            }
            dimensionKey = fallbackKey;
        }

        LOGGER.info("[DynamicDim] Destroying dimension for instance {}", instanceId);

        boolean unloadSucceeded = false;
        @Nonnull ResourceKey<Level> resolvedKey = nn(dimensionKey, "dimension key");
        ServerLevel level = server.getLevel(resolvedKey);
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
            unloadSucceeded = unloadDimension(resolvedKey, level);
        } else {
            // Level doesn't exist in memory - safe to proceed with cleanup
            unloadSucceeded = true;
        }

        // 3. Unregister from Distant Horizons BEFORE cleanup
        try {
            com.devmod.integration.DistantHorizonsIntegration.unregisterDynamicDimension(resolvedKey);
        } catch (LinkageError e) {
            LOGGER.debug("[DynamicDim] Could not unregister from DH: {}", e.getMessage());
        }

        // 3b. Clean up environment settings
        DimensionEnvironmentManager.INSTANCE.cleanupDimension(resolvedKey);

        // 4. Clean up tracking maps BEFORE file deletion
        // This prevents other code from trying to use this dimension
        dimensionToInstance.remove(resolvedKey);
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
            // 1. Post LevelEvent.Unload to notify other mods before unloading
            try {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new net.neoforged.neoforge.event.level.LevelEvent.Unload(
                        java.util.Objects.requireNonNull(level, "level")
                    )
                );
                LOGGER.debug("[DynamicDim] Posted LevelEvent.Unload for {}", dimensionKey.location());
            } catch (Exception e) {
                LOGGER.warn("[DynamicDim] Failed to post LevelEvent.Unload: {}", e.getMessage());
            }

            // 2. Save dimension data before unloading
            level.save(null, true, false);

            // 3. Get the levels map via Mixin accessor
            Map<ResourceKey<Level>, ServerLevel> levels = ((MinecraftServerAccessor) server).getLevels();

            // 4. Remove from the levels map
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
        Path dimensionPath = resolveDimensionPath(instanceId);
        if (dimensionPath == null) {
            return;
        }

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

        // Teleport to arena center (if known)
        BlockPos spawnPos = new BlockPos(0, 65, 0);  // default fallback
        var instanceOpt = InstanceRegistry.INSTANCE.getInstance(instanceId);
        if (instanceOpt.isPresent()) {
            BlockPos center = instanceOpt.get().getArenaCenter();
            if (center != null) {
                spawnPos = center;
            }
        }

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

        // Sync environment settings (frozen time) to the client
        var envSettings = DimensionEnvironmentManager.INSTANCE.getSettings(dimensionKey);
        String biomeId = envSettings.map(s -> s.biomeId()).orElse("");
        DimensionEnvironmentManager.INSTANCE.getTimeController().syncToPlayer(player, dimensionKey, biomeId);

        LOGGER.info("[DynamicDim] Teleported {} to instance {}", player.getName().getString(), instanceId);
        return true;
    }

    /**
     * Teleport a player back to the overworld spawn.
     */
    public boolean teleportToOverworld(ServerPlayer player) {
        if (server == null) return false;

        // Capture previous dimension before teleport to clear environment sync
        ResourceKey<Level> previousDim = player.level().dimension();

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

        // Clear environment sync (frozen time, biome) for the arena dimension
        // This prevents client from keeping stale frozen time after leaving arena
        if (isInstanceDimension(previousDim)) {
            DimensionEnvironmentManager.INSTANCE.getTimeController().clearSyncForPlayer(player, previousDim);
        }

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

    private ResourceKey<Level> resolveDimensionKey(UUID instanceId) {
        String dimensionName = "instance_" + instanceId.toString().replace("-", "");
        ResourceLocation dimensionLocation = nn(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, dimensionName), "dimension location");
        ResourceKey<? extends net.minecraft.core.Registry<Level>> dimensionRegistryKey =
            nn(Registries.DIMENSION, "dimension registry key");
        return ResourceKey.create(dimensionRegistryKey, dimensionLocation);
    }

    private Path resolveDimensionPath(UUID instanceId) {
        if (server == null) {
            return null;
        }
        String dimensionName = "instance_" + instanceId.toString().replace("-", "");
        var rootResource = nn(net.minecraft.world.level.storage.LevelResource.ROOT, "level resource root");
        return nn(server.getWorldPath(rootResource), "world path")
            .resolve("dimensions")
            .resolve(DevMod.MODID)
            .resolve(dimensionName);
    }

    /**
     * Utility to document non-null expectations for APIs that aren't annotated.
     */
    @Nonnull
    private static <T> T nn(T value, String context) {
        return Objects.requireNonNull(value, context);
    }
}
