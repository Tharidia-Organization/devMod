package com.devmod.runtime.environment;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.zone.ZoneEnvironment;
import com.devmod.mob.MobRequirements;
import com.devmod.mob.MobRequirementsRegistry;

/**
 * Coordinates environment configuration for arena dimensions.
 * Manages biome selection, time control, and lighting based on mob requirements.
 */
public class DimensionEnvironmentManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DimensionEnvironmentManager.class);

    public static final DimensionEnvironmentManager INSTANCE = new DimensionEnvironmentManager();

    /** Cached environment settings per dimension */
    private final Map<ResourceKey<Level>, EnvironmentSettings> dimensionSettings = new ConcurrentHashMap<>();

    /** Time controller for per-dimension time management */
    private final TimeController timeController = new TimeController();

    private DimensionEnvironmentManager() {}

    /**
     * Resolves the optimal biome for an arena based on template and mob requirements.
     *
     * @param template The arena template (may have explicit biome setting)
     * @param mobIds List of mob IDs that will spawn in the arena
     * @param server The Minecraft server for registry access
     * @return The biome holder to use for dimension generation
     */
    public Holder<Biome> resolveBiome(
        @Nullable ArenaTemplate template,
        @Nullable List<ResourceLocation> mobIds,
        MinecraftServer server
    ) {
        var biomeRegistry = server.registryAccess().registryOrThrow(
            Objects.requireNonNull(Registries.BIOME));

        // 1. Check if template specifies explicit biome
        if (template != null && template.biome() != null) {
            String biomeId = template.biome().id();
            if (biomeId != null && !biomeId.equals("minecraft:the_void")) {
                try {
                    ResourceLocation biomeLoc = Objects.requireNonNull(ResourceLocation.parse(biomeId));
                    ResourceKey<Biome> biomeKey = Objects.requireNonNull(
                        ResourceKey.create(Objects.requireNonNull(Registries.BIOME), biomeLoc));
                    Holder<Biome> holder = biomeRegistry.getHolder(biomeKey).orElse(null);
                    if (holder != null) {
                        LOGGER.debug("Using template biome: {}", biomeId);
                        return holder;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Invalid biome in template: {}", biomeId);
                }
            }
        }

        // 2. Check zone settings for preferred biome
        var zoneSettings = template != null ? template.zoneSettings() : null;
        if (zoneSettings != null && zoneSettings.enabled()) {
            var zones = zoneSettings.zones();
            if (!zones.isEmpty()) {
                // Use first zone's biome as primary
                String zoneBiome = zones.get(0).biome();
                if (zoneBiome != null) {
                    try {
                        ResourceLocation biomeLoc = Objects.requireNonNull(ResourceLocation.parse(zoneBiome));
                        ResourceKey<Biome> biomeKey = Objects.requireNonNull(
                            ResourceKey.create(Objects.requireNonNull(Registries.BIOME), biomeLoc));
                        Holder<Biome> holder = biomeRegistry.getHolder(biomeKey).orElse(null);
                        if (holder != null) {
                            LOGGER.debug("Using zone biome: {}", zoneBiome);
                            return holder;
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Invalid zone biome: {}", zoneBiome);
                    }
                }
            }
        }

        // 3. Analyze mob requirements for optimal biome
        if (mobIds != null && !mobIds.isEmpty()) {
            ResourceLocation optimalBiome = findOptimalBiomeForMobs(mobIds);
            if (optimalBiome != null) {
                try {
                    ResourceKey<Biome> biomeKey = Objects.requireNonNull(
                        ResourceKey.create(Objects.requireNonNull(Registries.BIOME), optimalBiome));
                    Holder<Biome> holder = biomeRegistry.getHolder(biomeKey).orElse(null);
                    if (holder != null) {
                        LOGGER.debug("Using optimal biome for mobs: {}", optimalBiome);
                        return holder;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to resolve optimal biome: {}", optimalBiome);
                }
            }
        }

        // 4. Default to THE_VOID for minimal interference
        LOGGER.debug("Using default THE_VOID biome");
        return biomeRegistry.getHolderOrThrow(Objects.requireNonNull(Biomes.THE_VOID));
    }

    /**
     * Finds the optimal biome based on mob requirements.
     */
    @Nullable
    private ResourceLocation findOptimalBiomeForMobs(List<ResourceLocation> mobIds) {
        Map<ResourceLocation, Integer> biomeVotes = new HashMap<>();

        for (ResourceLocation mobId : mobIds) {
            MobRequirements reqs = MobRequirementsRegistry.INSTANCE.getOrDefault(mobId);
            if (reqs.biome().preferredBiome().isPresent()) {
                ResourceLocation preferred = reqs.biome().preferredBiome().get();
                biomeVotes.merge(preferred, 1, (a, b) -> a + b);
            }
        }

        if (biomeVotes.isEmpty()) {
            return null;
        }

        // Return most voted biome
        return biomeVotes.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    /**
     * Configures environment settings for a dimension based on template and mob requirements.
     *
     * @param dimensionKey The dimension to configure
     * @param template The arena template
     * @param mobIds List of mob IDs that will spawn
     * @param level The ServerLevel
     */
    public void configureEnvironment(
        ResourceKey<Level> dimensionKey,
        @Nullable ArenaTemplate template,
        @Nullable List<ResourceLocation> mobIds,
        ServerLevel level
    ) {
        EnvironmentSettings settings = calculateSettings(template, mobIds);
        dimensionSettings.put(dimensionKey, settings);

        // Apply time settings
        if (settings.timeConfig() != ZoneEnvironment.TimeConfig.ANY) {
            timeController.setTime(dimensionKey, settings.timeConfig());
            LOGGER.debug("Set dimension {} time to {}", dimensionKey.location(), settings.timeConfig());
        }

        // Apply lighting if needed
        if (settings.targetLightLevel() >= 0) {
            applyLighting(level, settings);
        }

        LOGGER.info("Configured environment for dimension {}: biome={}, time={}, light={}",
            dimensionKey.location(),
            settings.biomeId(),
            settings.timeConfig(),
            settings.targetLightLevel());
    }

    /**
     * Calculates environment settings from template and mob requirements.
     */
    private EnvironmentSettings calculateSettings(
        @Nullable ArenaTemplate template,
        @Nullable List<ResourceLocation> mobIds
    ) {
        String biomeId = "minecraft:the_void";
        ZoneEnvironment.TimeConfig timeConfig = ZoneEnvironment.TimeConfig.ANY;
        int targetLight = -1;
        boolean placeLightSources = false;

        // Get template settings
        if (template != null) {
            if (template.biome() != null) {
                biomeId = template.biome().id();
            }
            if (template.lighting() != null) {
                targetLight = template.lighting().blockLight();
                placeLightSources = template.lighting().ambientLight();
            }
        }

        // Override with zone settings if present
        var zs = template != null ? template.zoneSettings() : null;
        if (zs != null && zs.enabled()) {
            var zones = zs.zones();
            if (!zones.isEmpty()) {
                var firstZone = zones.get(0);
                if (firstZone.biome() != null) {
                    biomeId = firstZone.biome();
                }
                Integer zoneLight = firstZone.blockLight();
                if (zoneLight != null) {
                    targetLight = zoneLight;
                }
                String zoneTime = firstZone.time();
                if (zoneTime != null) {
                    try {
                        timeConfig = ZoneEnvironment.TimeConfig.valueOf(zoneTime.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        LOGGER.trace("Invalid time config '{}', using default", zoneTime);
                    }
                }
            }
        }

        // Analyze mob requirements for time preference
        if (mobIds != null && !mobIds.isEmpty()) {
            timeConfig = determineOptimalTime(mobIds);
        }

        return new EnvironmentSettings(biomeId, timeConfig, targetLight, placeLightSources);
    }

    /**
     * Determines optimal time of day based on mob requirements.
     */
    private ZoneEnvironment.TimeConfig determineOptimalTime(List<ResourceLocation> mobIds) {
        int dayVotes = 0;
        int nightVotes = 0;

        for (ResourceLocation mobId : mobIds) {
            MobRequirements reqs = MobRequirementsRegistry.INSTANCE.getOrDefault(mobId);
            switch (reqs.time()) {
                case DAY -> dayVotes++;
                case NIGHT -> nightVotes++;
                default -> {} // ANY doesn't vote
            }
        }

        if (nightVotes > dayVotes) {
            return ZoneEnvironment.TimeConfig.NIGHT;
        } else if (dayVotes > nightVotes) {
            return ZoneEnvironment.TimeConfig.DAY;
        }

        // Default to ANY if tied or no preference
        return ZoneEnvironment.TimeConfig.ANY;
    }

    /**
     * Applies lighting configuration to a level.
     */
    private void applyLighting(ServerLevel level, EnvironmentSettings settings) {
        Objects.requireNonNull(level, "level"); // Reserved for future runtime lighting
        if (!settings.placeLightSources() || settings.targetLightLevel() < 0) {
            return;
        }

        // Light source placement is handled by ArenaBuilder
        // This method is for runtime lighting adjustments if needed
        LOGGER.debug("Lighting configured: target level={}, placeSources={}",
            settings.targetLightLevel(), settings.placeLightSources());
    }

    /**
     * Gets the environment settings for a dimension.
     */
    public Optional<EnvironmentSettings> getSettings(ResourceKey<Level> dimensionKey) {
        return Optional.ofNullable(dimensionSettings.get(dimensionKey));
    }

    /**
     * Gets the time controller for per-dimension time management.
     */
    public TimeController getTimeController() {
        return timeController;
    }

    /**
     * Cleans up settings for a destroyed dimension.
     */
    public void cleanupDimension(ResourceKey<Level> dimensionKey) {
        dimensionSettings.remove(dimensionKey);
        timeController.removeDimension(dimensionKey);
    }

    /**
     * Syncs environment settings (time override, biome) to a player entering/re-entering a dimension.
     * Call this when player teleports to, respawns in, or re-joins an arena dimension.
     *
     * @param player The player to sync
     * @param dimensionKey The dimension key
     */
    public void syncEnvironmentToPlayer(net.minecraft.server.level.ServerPlayer player, ResourceKey<Level> dimensionKey) {
        EnvironmentSettings settings = dimensionSettings.get(dimensionKey);
        String biomeId = settings != null ? settings.biomeId() : "";
        timeController.syncToPlayer(player, dimensionKey, biomeId);
    }

    /**
     * Ticks time control for all managed dimensions.
     * Should be called each server tick.
     */
    public void tick(MinecraftServer server) {
        timeController.tick(server);
    }

    /**
     * Environment settings for a dimension.
     */
    public record EnvironmentSettings(
        String biomeId,
        ZoneEnvironment.TimeConfig timeConfig,
        int targetLightLevel,
        boolean placeLightSources
    ) {}
}
