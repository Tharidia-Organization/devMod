package com.devmod.arena.registry;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.devmod.arena.zone.ZoneLayout;

public record ArenaTemplate(
    String id,
    @Nullable String extendsTemplate,
    int version,
    int schemaVersion,  // DD25: Changed from String to int
    boolean breakingChange,
    boolean deprecated,
    @Nullable String replacementVersion,
    @Nullable Integer minParentVersion,
    TemplateType templateType,  // DD1-DD2: Sealed interface with variants
    Origin origin,
    int size,
    @Nullable Integer sizeX,
    @Nullable Integer sizeZ,
    ArenaShape arenaShape,
    @Nullable Integer ringInnerRadius,
    Floor floor,
    Walls walls,
    Ceiling ceiling,
    Underfloor underfloor,
    Palette palette,
    Biome biome,
    Lighting lighting,
    List<SpawnSlot> spawnSlots,
    ArenaTemplate.Offset playerSpawnOffset,
    MobSpawnStrategy mobSpawnStrategy,
    List<ForbiddenZone> forbiddenZones,
    List<Hazard> hazards,
    Environment environment,
    Compat compat,
    InstanceSettings instanceSettings,
    @Nullable StructureNbt structureNbt,
    Limits limits,
    BuildSettings buildSettings,
    @Nullable ZoneSettings zoneSettings,
    @Nullable TerrainSettings terrainSettings,
    List<String> tags
) {
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Template di fallback (default_flat_64) con valori deterministici.
     */
    public static ArenaTemplate defaultTemplate() {
        return builder("default_flat_64")
            .version(1)
            .schemaVersion(1)  // DD25: int
            .breakingChange(false)
            .deprecated(false)
            .replacementVersion(null)
            .minParentVersion(null)
            .templateType(TemplateType.FlatTemplate.defaults())  // DD1-DD2
            .origin(new Origin(OriginMode.CENTER, 0, 64, 0))
            .size(64)
            .floor(new Floor(64, 1, "minecraft:stone_bricks", "solid", "minecraft:polished_andesite", 0))
            .walls(new Walls(true, "minecraft:barrier", 11, 1, 64, "solid"))
            .ceiling(new Ceiling(true, "minecraft:barrier", 74, 1))
            .underfloor(new Underfloor("minecraft:bedrock", 3, false))
            .palette(new Palette("minecraft:polished_andesite", "minecraft:glowstone", "minecraft:magma_block"))
            .biome(new Biome("minecraft:plains", Biome.ApplyTo.BOUNDS))
            .lighting(new Lighting(15, 10, true, List.of()))
            .spawnSlots(List.of(
                new SpawnSlot(new int[]{0, 1, 0}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("center", "player"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{10, 1, 0}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("melee", "mob"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{-10, 1, 0}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("ranged", "mob"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{0, 1, 10}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("mid", "mob"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{0, 1, -10}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("mid", "mob"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{20, 1, 20}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("corner", "mob"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{-20, 1, 20}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("corner", "mob"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{20, 1, -20}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("corner", "mob"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{-20, 1, -20}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("corner", "mob"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{25, 1, 0}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("flank", "mob"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{-25, 1, 0}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("flank", "mob"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{0, 1, 25}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("rear", "mob"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{0, 1, -25}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("rear", "mob"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{14, 1, -8}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("skirmish", "mob"),
                    new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{-14, 1, 8}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("skirmish", "mob"),
                    new SpawnSlot.Validation(true, 2, 1))
            ))
            .playerSpawnOffset(new ArenaTemplate.Offset(0, 0, 0))
            .mobSpawnStrategy(MobSpawnStrategy.DISTRIBUTED)
            .forbiddenZones(List.of())
            .hazards(List.of())
            .environment(new Environment(List.of(), null, new Environment.Fog(false, 0.0f)))
            .compat(new Compat(1, 4))
            .instanceSettings(new InstanceSettings(5, 4, true))
            .structureNbt(null)
            .limits(new Limits(5000, 50000, 100))
            .buildSettings(new BuildSettings(BuildSettings.Priority.SYNC, BuildSettings.Order.FLOOR_FIRST))
            .zoneSettings(null)
            .tags(List.of("default", "flat"))
            .build();
    }

    /**
     * Smoke variant of default_flat_64 for autosmoke coverage.
     */
    public static ArenaTemplate smokeFlat64Template() {
        ArenaTemplate base = defaultTemplate();
        return new ArenaTemplate(
            "default_flat_64_smoke",
            base.extendsTemplate(),
            base.version(),
            base.schemaVersion(),
            base.breakingChange(),
            base.deprecated(),
            base.replacementVersion(),
            base.minParentVersion(),
            base.templateType(),
            base.origin(),
            base.size(),
            base.sizeX(),
            base.sizeZ(),
            base.arenaShape(),
            base.ringInnerRadius(),
            base.floor(),
            base.walls(),
            base.ceiling(),
            base.underfloor(),
            base.palette(),
            base.biome(),
            base.lighting(),
            base.spawnSlots(),
            base.playerSpawnOffset(),
            base.mobSpawnStrategy(),
            base.forbiddenZones(),
            base.hazards(),
            base.environment(),
            base.compat(),
            base.instanceSettings(),
            base.structureNbt(),
            base.limits(),
            base.buildSettings(),
            base.zoneSettings(),
            base.terrainSettings(),
            List.of("default", "flat", "smoke")
        );
    }

    /**
     * Boss ring template 80x80 with a lava ring hazard (golden reference).
     */
    public static ArenaTemplate bossRing80Template() {
        return builder("boss_ring_80")
            .version(1)
            .schemaVersion(1)
            .breakingChange(false)
            .deprecated(false)
            .replacementVersion(null)
            .minParentVersion(null)
            .templateType(TemplateType.FlatTemplate.defaults())
            .origin(new Origin(OriginMode.CENTER, 0, 64, 0))
            .size(80)
            .arenaShape(ArenaShape.CIRCULAR)
            .floor(new Floor(64, 1, "minecraft:stone_bricks", "solid", "minecraft:polished_andesite", 0))
            .walls(new Walls(true, "minecraft:barrier", 14, 1, 64, "solid"))
            .ceiling(new Ceiling(true, "minecraft:barrier", 76, 1))
            .underfloor(new Underfloor("minecraft:bedrock", 3, false))
            .palette(new Palette("minecraft:polished_andesite", "minecraft:glowstone", "minecraft:magma_block"))
            .biome(new Biome("minecraft:plains", Biome.ApplyTo.BOUNDS))
            .lighting(new Lighting(15, 12, true, List.of(
                new Lighting.LightSource(new int[]{0, 70, 0}, "minecraft:glowstone")
            )))
            .spawnSlots(List.of(
                new SpawnSlot(new int[]{0, 1, 0}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("center", "player"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{-25, 1, 0}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("melee", "mob"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{25, 1, 0}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("ranged", "mob"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{0, 1, -25}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("melee", "mob"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{20, 1, 20}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("corner", "mob"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{-20, 1, 20}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("corner", "mob"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{20, 1, -20}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("corner", "mob"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{-20, 1, -20}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("corner", "mob"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{22, 1, 12}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("melee", "mob"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{-22, 1, 12}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("melee", "mob"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{22, 1, -12}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("ranged", "mob"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{-22, 1, -12}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("ranged", "mob"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{12, 1, 22}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("ranged", "mob"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{-12, 1, 22}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("ranged", "mob"), new SpawnSlot.Validation(true, 2, 1)),
                new SpawnSlot(new int[]{0, 1, 25}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("boss", "mob"), new SpawnSlot.Validation(true, 2, 2))
            ))
            .playerSpawnOffset(new ArenaTemplate.Offset(0, 0, 0))
            .mobSpawnStrategy(MobSpawnStrategy.RING)
            .forbiddenZones(List.of())
            .hazards(List.of(
                new Hazard("lava_ring", Map.of(
                    "innerRadius", 30,
                    "outerRadius", 32,
                    "material", "minecraft:lava"
                ), 64, SpawnSlot.YMode.ABSOLUTE)
            ))
            .environment(new Environment(List.of(), null, new Environment.Fog(false, 0.0f)))
            .compat(new Compat(1, 4))
            .instanceSettings(new InstanceSettings(6, 5, true))
            .structureNbt(null)
            .limits(new Limits(8000, 75000, 150))
            .buildSettings(new BuildSettings(BuildSettings.Priority.SYNC, BuildSettings.Order.FLOOR_FIRST))
            .zoneSettings(null)
            .tags(List.of("boss", "ring", "hazard"))
            .build();
    }

    // ==== Nested Records ====

    public record Origin(OriginMode mode, int x, int y, int z) {}
    public enum OriginMode { CENTER, CORNER_NW, CORNER_SW }

    public record Floor(int y, int thickness, String material, String pattern,
                        @Nullable String borderMaterial, int borderWidth) {}

    public record Walls(boolean enabled, String material, int height, int thickness, int startY, String style) {}

    public record Ceiling(boolean enabled, String material, int y, int thickness) {}

    public record Underfloor(String material, int depth, boolean sameAsFloor) {}

    public record Palette(String accent, String highlight, String hazardBorder) {}

    public record Biome(String id, ApplyTo applyTo) {
        public enum ApplyTo { BOUNDS, CHUNKS }
    }

    /**
     * Lighting configuration for an arena template.
     *
     * @param skyLight     Target sky light level (0-15)
     * @param blockLight   Target block light level (0-15), used for ambient grid lighting spacing
     * @param ambientLight Whether to place ambient grid lighting automatically
     * @param lightSources Explicit light source positions to place
     */
    public record Lighting(int skyLight, int blockLight, boolean ambientLight, List<LightSource> lightSources) {
        /**
         * Defines an explicit light source position and block type.
         *
         * <p>Position coordinates (pos) are interpreted as follows:
         * <ul>
         *   <li>pos[0] (X): Relative to arena center (origin)</li>
         *   <li>pos[1] (Y): <b>Floor-relative</b> when template has a floor defined,
         *       otherwise absolute. For example, pos[1]=2 means 2 blocks above floor.</li>
         *   <li>pos[2] (Z): Relative to arena center (origin)</li>
         * </ul>
         *
         * @param pos   3-element array [x, y, z] for position
         * @param block Block ID for the light source (e.g., "minecraft:lantern")
         */
        public record LightSource(int[] pos, String block) {}
    }

    public record SpawnSlot(int[] pos, YMode yMode, List<String> tags, Validation validation) {
        public enum YMode { RELATIVE_TO_FLOOR, ABSOLUTE }
        public record Validation(boolean requireSolidBelow, int requireAirAbove, int requireClearRadius) {}
    }

    public record ForbiddenZone(int[] min, int[] max, SpawnSlot.YMode yMode, String reason) {}

    public record Hazard(String type, Map<String, Object> params, @Nullable Integer y, @Nullable SpawnSlot.YMode yMode) {}

    public record Environment(List<Particle> particles, @Nullable String ambientSound, Fog fog) {
        public record Particle(String type, double rate, String area) {}
        public record Fog(boolean enabled, double density) {}
    }

    public record Compat(int minPlayers, int maxPlayers) {}

    public record InstanceSettings(int chunkRadius, int tickDistance, boolean keepLoaded) {}

    public record StructureNbt(String path, StructureNbt.Offset offset, String rotation, String mirror,
                               String seedPolicy, boolean ignoreAir) {
        public record Offset(int x, int y, int z) {}
    }

    public record Limits(int maxBuildTimeMs, int maxBlocks, int maxEntities) {}

    public record BuildSettings(Priority buildPriority, Order buildOrder) {
        public enum Priority { SYNC, ASYNC }
        public enum Order { FLOOR_FIRST, WALLS_FIRST, STRUCTURE_FIRST }
    }

    /**
     * Zone configuration for multi-environment arenas.
     * Allows defining multiple zones with different biomes, floors, and lighting.
     */
    public record ZoneSettings(
        boolean enabled,
        boolean autoGenerate,
        ZoneLayout.LayoutStrategy preferredStrategy,
        List<ZoneDefinition> zones
    ) {
        public static final ZoneSettings DISABLED = new ZoneSettings(false, false, ZoneLayout.LayoutStrategy.SINGLE, List.of());
        public static final ZoneSettings AUTO = new ZoneSettings(true, true, ZoneLayout.LayoutStrategy.SINGLE, List.of());

        public record ZoneDefinition(
            String name,
            @Nullable String biome,
            @Nullable String floorMaterial,
            @Nullable Integer skyLight,
            @Nullable Integer blockLight,
            @Nullable String time,
            List<String> mobTags
        ) {}
    }

    public record Offset(int x, int y, int z) {}

    /**
     * Terrain generation configuration for dynamic arenas.
     * Controls how the chunk generator creates terrain outside the arena structure.
     */
    public record TerrainSettings(
        TerrainType type,
        @Nullable DynamicSettings dynamic
    ) {
        public enum TerrainType {
            /** Flat terrain - simple void with bedrock layer (fast, default) */
            FLAT,
            /** Dynamic terrain - natural worldgen with biomes, caves, features */
            DYNAMIC
        }

        public record DynamicSettings(
            Mode mode,
            @Nullable String sourceDimension,
            BiomePolicy biomePolicy,
            @Nullable String fixedBiome,
            @Nullable String biomeTag,
            boolean allowCaves,
            int blendRadius,
            int combatRingRadius
        ) {
            public enum Mode {
                /** Proxy to source dimension's worldgen (full mod support) */
                PROXY_WORLDGEN,
                /** Controlled natural generation by DevMod (lightweight fallback) */
                CONTROLLED_NATURAL
            }

            public enum BiomePolicy {
                /** Match biome to mob's preferred environment */
                MATCH_MOB,
                /** Use fixed biome from configuration */
                FIXED,
                /** Random biome from a biome tag */
                RANDOM_FROM_TAG
            }

            public static DynamicSettings proxyOverworld() {
                return new DynamicSettings(
                    Mode.PROXY_WORLDGEN,
                    "minecraft:overworld",
                    BiomePolicy.MATCH_MOB,
                    null,
                    null,
                    true,
                    3,
                    32
                );
            }

            public static DynamicSettings controlledNatural() {
                return new DynamicSettings(
                    Mode.CONTROLLED_NATURAL,
                    null,
                    BiomePolicy.MATCH_MOB,
                    null,
                    null,
                    false,
                    3,
                    32
                );
            }
        }

        public static final TerrainSettings FLAT = new TerrainSettings(TerrainType.FLAT, null);
        public static final TerrainSettings DYNAMIC_PROXY = new TerrainSettings(
            TerrainType.DYNAMIC,
            DynamicSettings.proxyOverworld()
        );
        public static final TerrainSettings DYNAMIC_CONTROLLED = new TerrainSettings(
            TerrainType.DYNAMIC,
            DynamicSettings.controlledNatural()
        );
    }

    public enum MobSpawnStrategy {
        DISTRIBUTED,
        CLUSTERED,
        CORNERS,
        RING
    }

    /**
     * Physical shape of the arena playable area.
     * Controls floor generation, boundary checks, and spawn slot placement.
     */
    public enum ArenaShape {
        /** Standard rectangular/square arena (default). */
        RECTANGULAR,
        /** Circular arena using radius = max(sizeX, sizeZ) / 2. */
        CIRCULAR,
        /** Ring/donut shaped arena with inner and outer radius. */
        RING
    }

    /**
     * Builder per creare template in modo fluido.
     */
    public static class Builder {
        private final String id;
        @Nullable
        private String extendsTemplate;
        private int version = 1;
        private int schemaVersion = 1;  // DD25: int instead of String
        private boolean breakingChange = false;
        private boolean deprecated = false;
        @Nullable
        private String replacementVersion;
        @Nullable
        private Integer minParentVersion;
        private TemplateType templateType = TemplateType.FlatTemplate.defaults();  // DD1-DD2
        private Origin origin = new Origin(OriginMode.CENTER, 0, 64, 0);
        private int size = 64;
        @Nullable
        private Integer sizeX;
        @Nullable
        private Integer sizeZ;
        private ArenaShape arenaShape = ArenaShape.RECTANGULAR;
        @Nullable
        private Integer ringInnerRadius;
        private Floor floor = new Floor(64, 1, "minecraft:stone_bricks", "solid", "minecraft:polished_andesite", 0);
        private Walls walls = new Walls(true, "minecraft:barrier", 10, 1, 64, "solid");
        private Ceiling ceiling = new Ceiling(true, "minecraft:barrier", 74, 1);
        private Underfloor underfloor = new Underfloor("minecraft:bedrock", 1, false);
        private Palette palette = new Palette("minecraft:polished_andesite", "minecraft:glowstone", "minecraft:magma_block");
        private Biome biome = new Biome("minecraft:plains", Biome.ApplyTo.BOUNDS);
        private Lighting lighting = new Lighting(15, 10, true, List.of());
        private List<SpawnSlot> spawnSlots = List.of();
        private ArenaTemplate.Offset playerSpawnOffset = new ArenaTemplate.Offset(0, 0, 0);
        private MobSpawnStrategy mobSpawnStrategy = MobSpawnStrategy.DISTRIBUTED;
        private List<ForbiddenZone> forbiddenZones = List.of();
        private List<Hazard> hazards = List.of();
        private Environment environment = new Environment(List.of(), null, new Environment.Fog(false, 0.0f));
        private Compat compat = new Compat(1, 4);
        private InstanceSettings instanceSettings = new InstanceSettings(5, 4, true);
        @Nullable
        private StructureNbt structureNbt;
        private Limits limits = new Limits(5000, 50000, 100);
        private BuildSettings buildSettings = new BuildSettings(BuildSettings.Priority.SYNC, BuildSettings.Order.FLOOR_FIRST);
        @Nullable
        private ZoneSettings zoneSettings;
        @Nullable
        private TerrainSettings terrainSettings;
        private List<String> tags = List.of();

        public Builder(String id) {
            this.id = id;
        }

        public Builder extendsTemplate(@Nullable String extendsTemplate) { this.extendsTemplate = extendsTemplate; return this; }
        public Builder version(int version) { this.version = version; return this; }
        public Builder schemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; return this; }  // DD25: int
        public Builder breakingChange(boolean breakingChange) { this.breakingChange = breakingChange; return this; }
        public Builder deprecated(boolean deprecated) { this.deprecated = deprecated; return this; }
        public Builder replacementVersion(@Nullable String replacementVersion) { this.replacementVersion = replacementVersion; return this; }
        public Builder minParentVersion(@Nullable Integer minParentVersion) { this.minParentVersion = minParentVersion; return this; }
        public Builder templateType(TemplateType templateType) { this.templateType = templateType; return this; }  // DD1-DD2
        public Builder origin(Origin origin) { this.origin = origin; return this; }
        public Builder size(int size) { this.size = size; return this; }
        public Builder sizeX(@Nullable Integer sizeX) { this.sizeX = sizeX; return this; }
        public Builder sizeZ(@Nullable Integer sizeZ) { this.sizeZ = sizeZ; return this; }
        public Builder arenaShape(ArenaShape arenaShape) { this.arenaShape = arenaShape; return this; }
        public Builder ringInnerRadius(@Nullable Integer ringInnerRadius) { this.ringInnerRadius = ringInnerRadius; return this; }
        public Builder floor(Floor floor) { this.floor = floor; return this; }
        public Builder walls(Walls walls) { this.walls = walls; return this; }
        public Builder ceiling(Ceiling ceiling) { this.ceiling = ceiling; return this; }
        public Builder underfloor(Underfloor underfloor) { this.underfloor = underfloor; return this; }
        public Builder palette(Palette palette) { this.palette = palette; return this; }
        public Builder biome(Biome biome) { this.biome = biome; return this; }
        public Builder lighting(Lighting lighting) { this.lighting = lighting; return this; }
        public Builder spawnSlots(List<SpawnSlot> spawnSlots) { this.spawnSlots = spawnSlots; return this; }
        public Builder playerSpawnOffset(ArenaTemplate.Offset offset) { this.playerSpawnOffset = offset; return this; }
        public Builder mobSpawnStrategy(MobSpawnStrategy strategy) { this.mobSpawnStrategy = strategy; return this; }
        public Builder forbiddenZones(List<ForbiddenZone> forbiddenZones) { this.forbiddenZones = forbiddenZones; return this; }
        public Builder hazards(List<Hazard> hazards) { this.hazards = hazards; return this; }
        public Builder environment(Environment environment) { this.environment = environment; return this; }
        public Builder compat(Compat compat) { this.compat = compat; return this; }
        public Builder instanceSettings(InstanceSettings instanceSettings) { this.instanceSettings = instanceSettings; return this; }
        public Builder structureNbt(@Nullable StructureNbt structureNbt) { this.structureNbt = structureNbt; return this; }
        public Builder limits(Limits limits) { this.limits = limits; return this; }
        public Builder buildSettings(BuildSettings buildSettings) { this.buildSettings = buildSettings; return this; }
        public Builder zoneSettings(@Nullable ZoneSettings zoneSettings) { this.zoneSettings = zoneSettings; return this; }
        public Builder terrainSettings(@Nullable TerrainSettings terrainSettings) { this.terrainSettings = terrainSettings; return this; }
        public Builder tags(List<String> tags) { this.tags = tags; return this; }

        public ArenaTemplate build() {
            return new ArenaTemplate(
                id,
                extendsTemplate,
                version,
                schemaVersion,
                breakingChange,
                deprecated,
                replacementVersion,
                minParentVersion,
                Objects.requireNonNull(templateType, "templateType"),
                Objects.requireNonNull(origin, "origin"),
                size,
                sizeX,
                sizeZ,
                Objects.requireNonNull(arenaShape, "arenaShape"),
                ringInnerRadius,
                Objects.requireNonNull(floor, "floor"),
                Objects.requireNonNull(walls, "walls"),
                Objects.requireNonNull(ceiling, "ceiling"),
                Objects.requireNonNull(underfloor, "underfloor"),
                Objects.requireNonNull(palette, "palette"),
                Objects.requireNonNull(biome, "biome"),
                Objects.requireNonNull(lighting, "lighting"),
                List.copyOf(Objects.requireNonNull(spawnSlots, "spawnSlots")),
                Objects.requireNonNull(playerSpawnOffset, "playerSpawnOffset"),
                Objects.requireNonNull(mobSpawnStrategy, "mobSpawnStrategy"),
                List.copyOf(Objects.requireNonNull(forbiddenZones, "forbiddenZones")),
                List.copyOf(Objects.requireNonNull(hazards, "hazards")),
                Objects.requireNonNull(environment, "environment"),
                Objects.requireNonNull(compat, "compat"),
                Objects.requireNonNull(instanceSettings, "instanceSettings"),
                structureNbt,
                Objects.requireNonNull(limits, "limits"),
                Objects.requireNonNull(buildSettings, "buildSettings"),
                zoneSettings,
                terrainSettings,
                List.copyOf(Objects.requireNonNull(tags, "tags"))
            );
        }
    }
}
