package com.devmod.arena.registry;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arena Template (L1 - layout fisico) allineato alla spec v2.23.
 *
 * <p>Campi principali:
 * <ul>
 *   <li>id/version/schemaVersion/breakingChange</li>
 *   <li>origin/size/sizeX/sizeZ</li>
 *   <li>floor/walls/ceiling/underfloor/palette</li>
 *   <li>biome/lighting/environment</li>
 *   <li>spawnSlots/forbiddenZones/hazards</li>
 *   <li>compat/instanceSettings/structureNbt/limits/buildSettings/tags</li>
 * </ul>
 * I record nidificati sono minimali ma mappano 1:1 i campi previsti dalla
 * TODO_ARENA_TEMPLATE.md; la validazione applica vincoli di base.
 */
public record ArenaTemplate(
    String id,
    @Nullable String extendsTemplate,
    int version,
    String schemaVersion,
    boolean breakingChange,
    Origin origin,
    int size,
    @Nullable Integer sizeX,
    @Nullable Integer sizeZ,
    Floor floor,
    Walls walls,
    Ceiling ceiling,
    Underfloor underfloor,
    Palette palette,
    Biome biome,
    Lighting lighting,
    List<SpawnSlot> spawnSlots,
    List<ForbiddenZone> forbiddenZones,
    List<Hazard> hazards,
    Environment environment,
    Compat compat,
    InstanceSettings instanceSettings,
    @Nullable StructureNbt structureNbt,
    Limits limits,
    BuildSettings buildSettings,
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
            .schemaVersion("1.0.0")
            .breakingChange(false)
            .origin(new Origin(OriginMode.CENTER, 0, 64, 0))
            .size(64)
            .floor(new Floor(64, 1, "minecraft:stone_bricks", "solid", "minecraft:polished_andesite", 0))
            .walls(new Walls(true, "minecraft:barrier", 10, 1, 64, "solid"))
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
                new SpawnSlot(new int[]{20, 1, 20}, SpawnSlot.YMode.RELATIVE_TO_FLOOR, List.of("corner", "mob"),
                    new SpawnSlot.Validation(true, 2, 1))
            ))
            .forbiddenZones(List.of())
            .hazards(List.of())
            .environment(new Environment(List.of(), null, new Environment.Fog(false, 0.0f)))
            .compat(new Compat(1, 4))
            .instanceSettings(new InstanceSettings(2, 4, true))
            .structureNbt(null)
            .limits(new Limits(5000, 50000, 100))
            .buildSettings(new BuildSettings(BuildSettings.Priority.SYNC, BuildSettings.Order.FLOOR_FIRST))
            .tags(List.of("default", "flat"))
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

    public record Lighting(int skyLight, int blockLight, boolean ambientLight, List<LightSource> lightSources) {
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

    public record StructureNbt(String path, Offset offset, String rotation, String mirror,
                               String seedPolicy, boolean ignoreAir) {
        public record Offset(int x, int y, int z) {}
    }

    public record Limits(int maxBuildTimeMs, int maxBlocks, int maxEntities) {}

    public record BuildSettings(Priority buildPriority, Order buildOrder) {
        public enum Priority { SYNC, ASYNC }
        public enum Order { FLOOR_FIRST, WALLS_FIRST, STRUCTURE_FIRST }
    }

    /**
     * Builder per creare template in modo fluido.
     */
    public static class Builder {
        private final String id;
        private String extendsTemplate;
        private int version = 1;
        private String schemaVersion = "1.0.0";
        private boolean breakingChange = false;
        private Origin origin = new Origin(OriginMode.CENTER, 0, 64, 0);
        private int size = 64;
        private Integer sizeX;
        private Integer sizeZ;
        private Floor floor;
        private Walls walls;
        private Ceiling ceiling;
        private Underfloor underfloor;
        private Palette palette = new Palette("minecraft:polished_andesite", "minecraft:glowstone", "minecraft:magma_block");
        private Biome biome = new Biome("minecraft:plains", Biome.ApplyTo.BOUNDS);
        private Lighting lighting = new Lighting(15, 10, true, List.of());
        private List<SpawnSlot> spawnSlots = List.of();
        private List<ForbiddenZone> forbiddenZones = List.of();
        private List<Hazard> hazards = List.of();
        private Environment environment = new Environment(List.of(), null, new Environment.Fog(false, 0.0f));
        private Compat compat = new Compat(1, 4);
        private InstanceSettings instanceSettings = new InstanceSettings(2, 4, true);
        private StructureNbt structureNbt;
        private Limits limits = new Limits(5000, 50000, 100);
        private BuildSettings buildSettings = new BuildSettings(BuildSettings.Priority.SYNC, BuildSettings.Order.FLOOR_FIRST);
        private List<String> tags = List.of();

        public Builder(String id) {
            this.id = id;
        }

        public Builder extendsTemplate(@Nullable String extendsTemplate) { this.extendsTemplate = extendsTemplate; return this; }
        public Builder version(int version) { this.version = version; return this; }
        public Builder schemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; return this; }
        public Builder breakingChange(boolean breakingChange) { this.breakingChange = breakingChange; return this; }
        public Builder origin(Origin origin) { this.origin = origin; return this; }
        public Builder size(int size) { this.size = size; return this; }
        public Builder sizeX(@Nullable Integer sizeX) { this.sizeX = sizeX; return this; }
        public Builder sizeZ(@Nullable Integer sizeZ) { this.sizeZ = sizeZ; return this; }
        public Builder floor(Floor floor) { this.floor = floor; return this; }
        public Builder walls(Walls walls) { this.walls = walls; return this; }
        public Builder ceiling(Ceiling ceiling) { this.ceiling = ceiling; return this; }
        public Builder underfloor(Underfloor underfloor) { this.underfloor = underfloor; return this; }
        public Builder palette(Palette palette) { this.palette = palette; return this; }
        public Builder biome(Biome biome) { this.biome = biome; return this; }
        public Builder lighting(Lighting lighting) { this.lighting = lighting; return this; }
        public Builder spawnSlots(List<SpawnSlot> spawnSlots) { this.spawnSlots = spawnSlots; return this; }
        public Builder forbiddenZones(List<ForbiddenZone> forbiddenZones) { this.forbiddenZones = forbiddenZones; return this; }
        public Builder hazards(List<Hazard> hazards) { this.hazards = hazards; return this; }
        public Builder environment(Environment environment) { this.environment = environment; return this; }
        public Builder compat(Compat compat) { this.compat = compat; return this; }
        public Builder instanceSettings(InstanceSettings instanceSettings) { this.instanceSettings = instanceSettings; return this; }
        public Builder structureNbt(@Nullable StructureNbt structureNbt) { this.structureNbt = structureNbt; return this; }
        public Builder limits(Limits limits) { this.limits = limits; return this; }
        public Builder buildSettings(BuildSettings buildSettings) { this.buildSettings = buildSettings; return this; }
        public Builder tags(List<String> tags) { this.tags = tags; return this; }

        public ArenaTemplate build() {
            return new ArenaTemplate(
                id,
                extendsTemplate,
                version,
                schemaVersion,
                breakingChange,
                origin,
                size,
                sizeX,
                sizeZ,
                floor,
                walls,
                ceiling,
                underfloor,
                palette,
                biome,
                lighting,
                List.copyOf(spawnSlots),
                List.copyOf(forbiddenZones),
                List.copyOf(hazards),
                environment,
                compat,
                instanceSettings,
                structureNbt,
                limits,
                buildSettings,
                List.copyOf(tags)
            );
        }
    }
}
