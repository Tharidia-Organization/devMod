package com.devmod.arena.zone;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/**
 * Defines the environmental conditions for a zone within an arena.
 * Includes biome, floor material, lighting, and time settings.
 *
 * @param biome The biome for this zone (affects mob spawning, foliage colors, etc.)
 * @param floorMaterial The floor material for this zone. When set, ArenaBuilder will use this
 *                      material instead of the template's default floor material for blocks
 *                      placed within this zone's bounds. Enables visual distinction between
 *                      zones (e.g., netherrack for nether zone, packed_ice for frozen zone).
 * @param lighting Lighting configuration (sky light, block light, ambient sources)
 * @param time Time configuration (frozen day/night/dusk/dawn or dynamic)
 */
public record ZoneEnvironment(
    Optional<ResourceLocation> biome,
    Optional<ResourceLocation> floorMaterial,
    LightingConfig lighting,
    TimeConfig time
) {
    /** Default environment with no specific requirements */
    public static final ZoneEnvironment DEFAULT = new ZoneEnvironment(
        Optional.empty(),
        Optional.empty(),
        LightingConfig.DEFAULT,
        TimeConfig.ANY
    );

    /**
     * Creates an environment with just a biome.
     */
    public static ZoneEnvironment withBiome(ResourceLocation biome) {
        return new ZoneEnvironment(
            Optional.of(biome),
            Optional.empty(),
            LightingConfig.DEFAULT,
            TimeConfig.ANY
        );
    }

    /**
     * Creates an environment optimized for hostile mobs (dark, night).
     */
    public static ZoneEnvironment hostile() {
        return new ZoneEnvironment(
            Optional.empty(),
            Optional.empty(),
            LightingConfig.DARK,
            TimeConfig.NIGHT
        );
    }

    /**
     * Creates an environment optimized for passive mobs (bright, day).
     */
    public static ZoneEnvironment passive() {
        return new ZoneEnvironment(
            Optional.empty(),
            Optional.empty(),
            LightingConfig.BRIGHT,
            TimeConfig.DAY
        );
    }

    /**
     * Creates a nether-style environment.
     */
    public static ZoneEnvironment nether() {
        return new ZoneEnvironment(
            Optional.of(ResourceLocation.withDefaultNamespace("nether_wastes")),
            Optional.of(ResourceLocation.withDefaultNamespace("netherrack")),
            new LightingConfig(0, 11, false),
            TimeConfig.ANY
        );
    }

    /**
     * Creates an end-style environment.
     */
    public static ZoneEnvironment end() {
        return new ZoneEnvironment(
            Optional.of(ResourceLocation.withDefaultNamespace("the_end")),
            Optional.of(ResourceLocation.withDefaultNamespace("end_stone")),
            LightingConfig.DARK,
            TimeConfig.ANY
        );
    }

    /**
     * Creates an ice/frozen environment.
     */
    public static ZoneEnvironment frozen() {
        return new ZoneEnvironment(
            Optional.of(ResourceLocation.withDefaultNamespace("snowy_plains")),
            Optional.of(ResourceLocation.withDefaultNamespace("packed_ice")),
            LightingConfig.BRIGHT,
            TimeConfig.NIGHT
        );
    }

    /**
     * Checks if this environment has any specific requirements.
     */
    public boolean hasRequirements() {
        return biome.isPresent() || floorMaterial.isPresent() ||
               !lighting.isDefault() || time != TimeConfig.ANY;
    }

    /**
     * Merges this environment with another, preferring non-empty values from the override.
     */
    public ZoneEnvironment merge(ZoneEnvironment override) {
        if (override == null) return this;
        return new ZoneEnvironment(
            override.biome.isPresent() ? override.biome : this.biome,
            override.floorMaterial.isPresent() ? override.floorMaterial : this.floorMaterial,
            override.lighting.isDefault() ? this.lighting : override.lighting,
            override.time == TimeConfig.ANY ? this.time : override.time
        );
    }

    // ============== Nested Types ==============

    /**
     * Lighting configuration for a zone.
     *
     * <p>This class is compatible with {@link com.devmod.arena.registry.ArenaTemplate.Lighting}
     * through the {@link #toArenaTemplateLighting()} conversion method.</p>
     */
    public record LightingConfig(
        int skyLight,
        int blockLight,
        boolean placeLightSources
    ) {
        public static final LightingConfig DEFAULT = new LightingConfig(15, 0, false);
        public static final LightingConfig DARK = new LightingConfig(0, 0, false);
        public static final LightingConfig BRIGHT = new LightingConfig(15, 15, true);
        public static final LightingConfig AMBIENT = new LightingConfig(7, 7, false);

        public boolean isDefault() {
            return skyLight == 15 && blockLight == 0 && !placeLightSources;
        }

        /**
         * Returns the effective light level for mob spawning.
         */
        public int effectiveLight() {
            return Math.max(skyLight, blockLight);
        }

        /**
         * Creates a lighting config that ensures darkness.
         */
        public static LightingConfig dark(int maxLight) {
            return new LightingConfig(0, Math.min(maxLight, 7), false);
        }

        /**
         * Creates a lighting config with light sources.
         */
        public static LightingConfig withSources(int blockLight) {
            return new LightingConfig(0, blockLight, true);
        }

        /**
         * Converts this zone lighting config to an ArenaTemplate.Lighting.
         * Allows zone-level lighting to be used with template-level building APIs.
         *
         * @return Equivalent ArenaTemplate.Lighting with no explicit light sources
         */
        public com.devmod.arena.registry.ArenaTemplate.Lighting toArenaTemplateLighting() {
            return new com.devmod.arena.registry.ArenaTemplate.Lighting(
                skyLight,
                blockLight,
                placeLightSources,
                List.of()  // No explicit sources from zone config
            );
        }

        /**
         * Creates a LightingConfig from an ArenaTemplate.Lighting.
         * Allows template-level lighting to be used with zone-level APIs.
         *
         * @param lighting The ArenaTemplate.Lighting to convert
         * @return Equivalent LightingConfig
         */
        public static LightingConfig fromArenaTemplateLighting(
                com.devmod.arena.registry.ArenaTemplate.Lighting lighting) {
            return new LightingConfig(
                lighting.skyLight(),
                lighting.blockLight(),
                lighting.ambientLight()
            );
        }
    }

    /**
     * Time configuration for a zone.
     */
    public enum TimeConfig {
        /** Any time of day */
        ANY(6000, false),
        /** Fixed daytime (noon) */
        DAY(6000, true),
        /** Fixed nighttime (midnight) */
        NIGHT(18000, true),
        /** Fixed dusk */
        DUSK(12000, true),
        /** Fixed dawn */
        DAWN(23000, true);

        private final long fixedTime;
        private final boolean frozen;

        TimeConfig(long time, boolean frozen) {
            this.fixedTime = time;
            this.frozen = frozen;
        }

        public long getFixedTime() { return fixedTime; }
        public boolean isFrozen() { return frozen; }

        /**
         * Determines if this time setting satisfies a mob's time requirement.
         */
        public boolean satisfies(com.devmod.mob.MobRequirements.TimeRequirement requirement) {
            if (requirement == com.devmod.mob.MobRequirements.TimeRequirement.ANY) {
                return true;
            }
            return requirement.isValidAt(fixedTime);
        }
    }
}
