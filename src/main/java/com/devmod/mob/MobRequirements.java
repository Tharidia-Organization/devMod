package com.devmod.mob;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Comprehensive requirements for a mob to function optimally in an arena environment.
 * Used by the arena system to create ideal conditions for testing each mob type.
 *
 * This is the core data model for the Mob Requirements Registry system.
 */
public record MobRequirements(
    ResourceLocation mobId,
    BiomeRequirement biome,
    LightRequirement light,
    FloorRequirement floor,
    SpaceRequirement space,
    TimeRequirement time,
    BossRequirement boss,
    RequirementSource source,
    float confidence
) {
    /**
     * Creates default requirements for a mob with minimal constraints.
     */
    public static MobRequirements defaultFor(ResourceLocation mobId) {
        return new MobRequirements(
            mobId,
            BiomeRequirement.ANY,
            LightRequirement.ANY,
            FloorRequirement.SOLID_GROUND,
            SpaceRequirement.DEFAULT,
            TimeRequirement.ANY,
            BossRequirement.NOT_BOSS,
            RequirementSource.DEFAULT,
            0.0f
        );
    }

    /**
     * Merges this requirement with another, preferring non-default values.
     * Used when combining auto-detected requirements with JSON overrides.
     */
    public MobRequirements merge(MobRequirements override) {
        if (override == null) {
            return this;
        }
        return new MobRequirements(
            this.mobId,
            override.biome().isDefault() ? this.biome : override.biome(),
            override.light().isDefault() ? this.light : override.light(),
            override.floor().isDefault() ? this.floor : override.floor(),
            override.space().isDefault() ? this.space : override.space(),
            override.time() == TimeRequirement.ANY ? this.time : override.time(),
            override.boss().isBoss() ? override.boss() : this.boss(),
            RequirementSource.MERGED,
            Math.max(this.confidence, override.confidence)
        );
    }

    // ============== Nested Types ==============

    /**
     * Biome requirements for mob spawning and behavior.
     */
    public record BiomeRequirement(
        Optional<ResourceLocation> preferredBiome,
        Optional<TagKey<Biome>> biomeTag,
        List<ResourceLocation> validBiomes,
        boolean required
    ) {
        public static final BiomeRequirement ANY = new BiomeRequirement(
            Optional.empty(), Optional.empty(), List.of(), false
        );

        public boolean isDefault() {
            return preferredBiome.isEmpty() && biomeTag.isEmpty() && validBiomes.isEmpty();
        }

        public static BiomeRequirement preferred(ResourceLocation biome) {
            return new BiomeRequirement(Optional.of(biome), Optional.empty(), List.of(biome), false);
        }

        public static BiomeRequirement required(ResourceLocation biome) {
            return new BiomeRequirement(Optional.of(biome), Optional.empty(), List.of(biome), true);
        }

        public static BiomeRequirement fromTag(TagKey<Biome> tag) {
            return new BiomeRequirement(Optional.empty(), Optional.of(tag), List.of(), false);
        }

        public static BiomeRequirement anyOf(List<ResourceLocation> biomes) {
            return new BiomeRequirement(
                biomes.isEmpty() ? Optional.empty() : Optional.of(biomes.get(0)),
                Optional.empty(),
                biomes,
                false
            );
        }
    }

    /**
     * Light level requirements for mob spawning.
     */
    public record LightRequirement(
        int minLight,
        int maxLight,
        boolean prefersDark,
        boolean prefersLight
    ) {
        public static final LightRequirement ANY = new LightRequirement(0, 15, false, false);
        public static final LightRequirement DARK = new LightRequirement(0, 7, true, false);
        public static final LightRequirement BRIGHT = new LightRequirement(8, 15, false, true);
        public static final LightRequirement PITCH_BLACK = new LightRequirement(0, 0, true, false);

        public boolean isDefault() {
            return minLight == 0 && maxLight == 15 && !prefersDark && !prefersLight;
        }

        public static LightRequirement range(int min, int max) {
            boolean dark = max <= 7;
            boolean light = min >= 8;
            return new LightRequirement(min, max, dark, light);
        }

        /**
         * Returns the optimal light level for this requirement.
         */
        public int optimalLight() {
            if (prefersDark) {
                return minLight;
            } else if (prefersLight) {
                return maxLight;
            }
            return (minLight + maxLight) / 2;
        }
    }

    /**
     * Floor/surface requirements for mob movement and spawning.
     */
    public record FloorRequirement(
        List<ResourceLocation> validBlocks,
        boolean requiresSolid,
        boolean canSpawnOnLiquid,
        boolean canSpawnInAir,
        Optional<ResourceLocation> preferredBlock
    ) {
        public static final FloorRequirement SOLID_GROUND = new FloorRequirement(
            List.of(), true, false, false, Optional.empty()
        );
        public static final FloorRequirement WATER = new FloorRequirement(
            List.of(ResourceLocation.withDefaultNamespace("water")), false, true, false,
            Optional.of(ResourceLocation.withDefaultNamespace("water"))
        );
        public static final FloorRequirement LAVA = new FloorRequirement(
            List.of(ResourceLocation.withDefaultNamespace("lava")), false, true, false,
            Optional.of(ResourceLocation.withDefaultNamespace("lava"))
        );
        public static final FloorRequirement FLYING = new FloorRequirement(
            List.of(), false, false, true, Optional.empty()
        );

        public boolean isDefault() {
            return validBlocks.isEmpty() && requiresSolid && !canSpawnOnLiquid && !canSpawnInAir && preferredBlock.isEmpty();
        }

        public static FloorRequirement specificBlocks(List<ResourceLocation> blocks) {
            return new FloorRequirement(
                blocks,
                true,
                false,
                false,
                blocks.isEmpty() ? Optional.empty() : Optional.of(blocks.get(0))
            );
        }

        public static FloorRequirement preferredBlock(ResourceLocation block) {
            return new FloorRequirement(
                List.of(block),
                true,
                false,
                false,
                Optional.of(block)
            );
        }
    }

    /**
     * Space requirements based on mob dimensions.
     */
    public record SpaceRequirement(
        float width,
        float height,
        float clearanceAbove,
        float clearanceAround
    ) {
        public static final SpaceRequirement DEFAULT = new SpaceRequirement(1.0f, 2.0f, 1.0f, 1.0f);
        public static final SpaceRequirement SMALL = new SpaceRequirement(0.5f, 1.0f, 0.5f, 0.5f);
        public static final SpaceRequirement LARGE = new SpaceRequirement(2.0f, 3.0f, 2.0f, 2.0f);
        public static final SpaceRequirement BOSS = new SpaceRequirement(3.0f, 4.0f, 4.0f, 4.0f);

        public boolean isDefault() {
            return width == 1.0f && height == 2.0f && clearanceAbove == 1.0f && clearanceAround == 1.0f;
        }

        public static SpaceRequirement fromDimensions(float width, float height) {
            return new SpaceRequirement(
                width,
                height,
                Math.max(1.0f, height * 0.5f),
                Math.max(1.0f, width * 0.5f)
            );
        }

        /**
         * Returns the minimum arena radius needed for this mob.
         */
        public int minArenaRadius() {
            return (int) Math.ceil(Math.max(width, clearanceAround) * 4);
        }
    }

    /**
     * Time of day requirements.
     */
    public enum TimeRequirement {
        ANY(0, 24000),
        DAY(0, 12000),
        NIGHT(12000, 24000),
        DUSK(11000, 13000),
        /** Dawn wraps around midnight: 23000-24000 and 0-1000 (approximately 5-7 AM) */
        DAWN(23000, 1000);

        private final long startTick;
        private final long endTick;

        TimeRequirement(long start, long end) {
            this.startTick = start;
            this.endTick = end;
        }

        public long startTick() { return startTick; }
        public long endTick() { return endTick; }

        /**
         * Returns the optimal time tick for this requirement.
         */
        public long optimalTick() {
            return switch (this) {
                case DAY -> 6000; // Noon
                case NIGHT -> 18000; // Midnight
                case DUSK -> 12000;
                case DAWN -> 23000;
                case ANY -> 6000; // Default to day
            };
        }

        public boolean isValidAt(long worldTime) {
            long dayTime = worldTime % 24000;
            if (this == ANY) return true;
            if (startTick <= endTick) {
                return dayTime >= startTick && dayTime < endTick;
            } else {
                // Wraps around midnight
                return dayTime >= startTick || dayTime < endTick;
            }
        }
    }

    /**
     * Boss-specific requirements.
     */
    public record BossRequirement(
        boolean isBoss,
        int minPlayers,
        boolean requiresActivation,
        Optional<ResourceLocation> activationItem,
        Optional<String> activationStructure
    ) {
        public static final BossRequirement NOT_BOSS = new BossRequirement(
            false, 1, false, Optional.empty(), Optional.empty()
        );

        public static BossRequirement simpleBoss() {
            return new BossRequirement(true, 1, false, Optional.empty(), Optional.empty());
        }

        /**
         * Creates a mini-boss requirement (powerful mob but not a true boss).
         * Mini-bosses are treated as bosses for spawn handling but don't require
         * special arena configurations like true bosses do.
         */
        public static BossRequirement miniBoss() {
            // Mini-bosses are flagged as bosses for spawn limiting but with no special requirements
            return new BossRequirement(true, 1, false, Optional.empty(), Optional.empty());
        }

        public static BossRequirement withMinPlayers(int minPlayers) {
            return new BossRequirement(true, minPlayers, false, Optional.empty(), Optional.empty());
        }

        public static BossRequirement withActivation(ResourceLocation item) {
            return new BossRequirement(true, 1, true, Optional.of(item), Optional.empty());
        }
    }

    /**
     * Source of the requirements data.
     */
    public enum RequirementSource {
        /** Default fallback values */
        DEFAULT,
        /** Auto-detected from vanilla/mod APIs */
        AUTO_DETECTED,
        /** Loaded from JSON override file */
        JSON_OVERRIDE,
        /** Merged from auto-detected and JSON */
        MERGED
    }
}
