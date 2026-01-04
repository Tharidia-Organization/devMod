package com.devmod.mob;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * Defines the environmental characteristics of a structure for arena generation.
 * Used to create appropriately-themed arenas when a mob naturally spawns in structures.
 *
 * This is a mapping system: structure ID -> arena hints.
 * NOT a block analysis system - we use predefined characteristics.
 */
public record StructureCharacteristics(
    /** Structure ID (e.g., "minecraft:stronghold", "graveyard:crypt") */
    String structureId,

    /** Display name for the structure */
    String displayName,

    /** Whether the structure is underground */
    boolean underground,

    /** Whether the structure is typically dark (low light) */
    boolean dark,

    /** Whether the structure is enclosed (has walls/ceiling) */
    boolean enclosed,

    /** Typical Y level range (min, max) - null means any */
    @Nullable YRange yRange,

    /** Preferred floor material for arenas mimicking this structure */
    String floorMaterial,

    /** Secondary floor material for variation */
    @Nullable String secondaryFloorMaterial,

    /** Wall material hint */
    @Nullable String wallMaterial,

    /** Optimal light level (0-15) */
    int lightLevel,

    /** Biome hint for arena - null means use default */
    @Nullable String biomeHint,

    /** Tags for matching (e.g., "dungeon", "mansion", "temple") */
    Set<String> tags
) {
    // ============== Y Range ==============

    public record YRange(int min, int max) {
        public static final YRange SURFACE = new YRange(60, 100);
        public static final YRange UNDERGROUND = new YRange(0, 60);
        public static final YRange DEEP_UNDERGROUND = new YRange(-64, 0);
        public static final YRange ANY = new YRange(-64, 320);

        public boolean contains(int y) {
            return y >= min && y <= max;
        }
    }

    // ============== Registry ==============

    private static final Map<String, StructureCharacteristics> REGISTRY = new LinkedHashMap<>();

    static {
        // === VANILLA STRUCTURES ===

        // Overworld - Underground
        register(new StructureCharacteristics(
            "minecraft:stronghold", "Stronghold",
            true, true, true, YRange.UNDERGROUND,
            "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:stone_bricks",
            3, null, Set.of("dungeon", "end_portal", "underground")
        ));

        register(new StructureCharacteristics(
            "minecraft:mineshaft", "Mineshaft",
            true, true, false, YRange.UNDERGROUND,
            "minecraft:oak_planks", "minecraft:cobblestone", "minecraft:oak_planks",
            0, null, Set.of("dungeon", "underground", "cave")
        ));

        register(new StructureCharacteristics(
            "minecraft:ancient_city", "Ancient City",
            true, true, false, YRange.DEEP_UNDERGROUND,
            "minecraft:deepslate_tiles", "minecraft:deepslate_bricks", "minecraft:deepslate_bricks",
            0, "minecraft:deep_dark", Set.of("dungeon", "underground", "sculk", "warden")
        ));

        register(new StructureCharacteristics(
            "minecraft:trial_chambers", "Trial Chambers",
            true, true, true, YRange.UNDERGROUND,
            "minecraft:tuff_bricks", "minecraft:copper_block", "minecraft:tuff_bricks",
            7, null, Set.of("dungeon", "underground", "trial")
        ));

        // Overworld - Surface
        register(new StructureCharacteristics(
            "minecraft:mansion", "Woodland Mansion",
            false, true, true, YRange.SURFACE,
            "minecraft:dark_oak_planks", "minecraft:birch_planks", "minecraft:dark_oak_planks",
            5, "minecraft:dark_forest", Set.of("mansion", "illager", "surface")
        ));

        register(new StructureCharacteristics(
            "minecraft:pillager_outpost", "Pillager Outpost",
            false, false, false, YRange.SURFACE,
            "minecraft:dark_oak_planks", "minecraft:cobblestone", "minecraft:dark_oak_planks",
            15, null, Set.of("illager", "surface", "tower")
        ));

        register(new StructureCharacteristics(
            "minecraft:desert_pyramid", "Desert Pyramid",
            false, true, true, YRange.SURFACE,
            "minecraft:sandstone", "minecraft:cut_sandstone", "minecraft:sandstone",
            7, "minecraft:desert", Set.of("temple", "surface", "desert")
        ));

        register(new StructureCharacteristics(
            "minecraft:jungle_pyramid", "Jungle Temple",
            false, true, true, YRange.SURFACE,
            "minecraft:mossy_cobblestone", "minecraft:cobblestone", "minecraft:mossy_cobblestone",
            5, "minecraft:jungle", Set.of("temple", "surface", "jungle")
        ));

        register(new StructureCharacteristics(
            "minecraft:witch_hut", "Witch Hut",
            false, false, true, YRange.SURFACE,
            "minecraft:spruce_planks", "minecraft:oak_planks", "minecraft:spruce_planks",
            10, "minecraft:swamp", Set.of("hut", "surface", "swamp")
        ));

        register(new StructureCharacteristics(
            "minecraft:ocean_monument", "Ocean Monument",
            true, true, true, new YRange(30, 62),
            "minecraft:prismarine_bricks", "minecraft:dark_prismarine", "minecraft:prismarine",
            5, "minecraft:deep_ocean", Set.of("monument", "underwater", "guardian")
        ));

        // Nether
        register(new StructureCharacteristics(
            "minecraft:fortress", "Nether Fortress",
            false, true, false, YRange.ANY,
            "minecraft:nether_bricks", "minecraft:cracked_nether_bricks", "minecraft:nether_bricks",
            7, "minecraft:nether_wastes", Set.of("fortress", "nether", "blaze")
        ));

        register(new StructureCharacteristics(
            "minecraft:bastion_remnant", "Bastion Remnant",
            false, true, true, YRange.ANY,
            "minecraft:blackstone", "minecraft:polished_blackstone_bricks", "minecraft:blackstone_bricks",
            5, "minecraft:nether_wastes", Set.of("bastion", "nether", "piglin")
        ));

        // End
        register(new StructureCharacteristics(
            "minecraft:end_city", "End City",
            false, true, true, YRange.ANY,
            "minecraft:end_stone_bricks", "minecraft:purpur_block", "minecraft:purpur_pillar",
            7, "minecraft:end_highlands", Set.of("end_city", "end", "shulker")
        ));

        // === THE GRAVEYARD MOD ===

        register(new StructureCharacteristics(
            "graveyard:crypt", "Crypt",
            true, true, true, YRange.DEEP_UNDERGROUND,
            "minecraft:deepslate_tiles", "minecraft:deepslate_bricks", "minecraft:deepslate_bricks",
            2, null, Set.of("dungeon", "underground", "undead", "graveyard")
        ));

        register(new StructureCharacteristics(
            "graveyard:haunted_house", "Haunted House",
            false, true, true, YRange.SURFACE,
            "minecraft:dark_oak_planks", "minecraft:spruce_planks", "minecraft:dark_oak_planks",
            3, "minecraft:dark_forest", Set.of("mansion", "undead", "graveyard", "surface")
        ));

        register(new StructureCharacteristics(
            "graveyard:large_graveyard", "Large Graveyard",
            false, true, false, YRange.SURFACE,
            "minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone_bricks",
            5, "minecraft:forest", Set.of("graveyard", "undead", "surface")
        ));

        register(new StructureCharacteristics(
            "graveyard:small_graveyard", "Small Graveyard",
            false, true, false, YRange.SURFACE,
            "minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone",
            7, "minecraft:plains", Set.of("graveyard", "undead", "surface")
        ));

        register(new StructureCharacteristics(
            "graveyard:mushroom_grave", "Mushroom Grave",
            false, true, false, YRange.SURFACE,
            "minecraft:mycelium", "minecraft:podzol", "minecraft:mushroom_stem",
            7, "minecraft:mushroom_fields", Set.of("graveyard", "mushroom", "surface")
        ));

        register(new StructureCharacteristics(
            "graveyard:ruins", "Ruins",
            false, true, false, YRange.SURFACE,
            "minecraft:mossy_cobblestone", "minecraft:cobblestone", "minecraft:mossy_stone_bricks",
            10, null, Set.of("ruins", "undead", "surface")
        ));

        // === TWILIGHT FOREST ===

        register(new StructureCharacteristics(
            "twilightforest:hollow_hill_small", "Small Hollow Hill",
            true, true, true, YRange.UNDERGROUND,
            "minecraft:stone", "minecraft:cobblestone", "minecraft:stone",
            3, null, Set.of("dungeon", "underground", "twilight")
        ));

        register(new StructureCharacteristics(
            "twilightforest:hollow_hill_medium", "Medium Hollow Hill",
            true, true, true, YRange.UNDERGROUND,
            "minecraft:stone", "minecraft:mossy_cobblestone", "minecraft:stone",
            3, null, Set.of("dungeon", "underground", "twilight")
        ));

        register(new StructureCharacteristics(
            "twilightforest:hollow_hill_large", "Large Hollow Hill",
            true, true, true, YRange.UNDERGROUND,
            "minecraft:stone", "minecraft:stone_bricks", "minecraft:stone",
            3, null, Set.of("dungeon", "underground", "twilight", "boss")
        ));

        register(new StructureCharacteristics(
            "twilightforest:lich_tower", "Lich Tower",
            false, true, true, YRange.SURFACE,
            "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:stone_bricks",
            5, null, Set.of("tower", "boss", "twilight", "surface")
        ));

        register(new StructureCharacteristics(
            "twilightforest:labyrinth", "Labyrinth",
            true, true, true, YRange.UNDERGROUND,
            "minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:stone_bricks",
            3, null, Set.of("dungeon", "underground", "twilight", "boss")
        ));

        register(new StructureCharacteristics(
            "twilightforest:dark_tower", "Dark Tower",
            false, true, true, YRange.SURFACE,
            "minecraft:nether_bricks", "minecraft:cracked_nether_bricks", "minecraft:nether_bricks",
            5, null, Set.of("tower", "boss", "twilight", "surface")
        ));

        register(new StructureCharacteristics(
            "twilightforest:final_castle", "Final Castle",
            false, true, true, YRange.SURFACE,
            "minecraft:stone_bricks", "minecraft:chiseled_stone_bricks", "minecraft:stone_bricks",
            7, null, Set.of("castle", "boss", "twilight", "surface")
        ));

        // === ICE AND FIRE ===

        register(new StructureCharacteristics(
            "iceandfire:fire_dragon_cave", "Fire Dragon Cave",
            true, true, true, YRange.UNDERGROUND,
            "minecraft:charred_log", "minecraft:magma_block", "minecraft:stone",
            7, null, Set.of("dragon", "underground", "fire")
        ));

        register(new StructureCharacteristics(
            "iceandfire:ice_dragon_cave", "Ice Dragon Cave",
            true, true, true, YRange.UNDERGROUND,
            "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:stone",
            5, "minecraft:snowy_plains", Set.of("dragon", "underground", "ice")
        ));

        register(new StructureCharacteristics(
            "iceandfire:lightning_dragon_cave", "Lightning Dragon Cave",
            true, true, true, YRange.UNDERGROUND,
            "minecraft:copper_block", "minecraft:oxidized_copper", "minecraft:stone",
            7, null, Set.of("dragon", "underground", "lightning")
        ));

        register(new StructureCharacteristics(
            "iceandfire:cyclops_cave", "Cyclops Cave",
            true, true, true, YRange.UNDERGROUND,
            "minecraft:stone", "minecraft:cobblestone", "minecraft:stone",
            5, null, Set.of("giant", "underground")
        ));

        // === BLUE SKIES ===

        register(new StructureCharacteristics(
            "blue_skies:blinding_dungeon", "Blinding Dungeon",
            true, true, true, YRange.UNDERGROUND,
            "minecraft:stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:stone_bricks",
            3, null, Set.of("dungeon", "underground", "blue_skies")
        ));

        // === CATACLYSM ===

        register(new StructureCharacteristics(
            "cataclysm:burning_arena", "Burning Arena",
            false, true, true, YRange.ANY,
            "minecraft:blackstone", "minecraft:gilded_blackstone", "minecraft:blackstone_bricks",
            10, "minecraft:nether_wastes", Set.of("arena", "nether", "boss", "cataclysm")
        ));

        register(new StructureCharacteristics(
            "cataclysm:cursed_pyramid", "Cursed Pyramid",
            false, true, true, YRange.SURFACE,
            "minecraft:sandstone", "minecraft:cut_sandstone", "minecraft:sandstone",
            5, "minecraft:desert", Set.of("temple", "boss", "cataclysm", "desert")
        ));

        register(new StructureCharacteristics(
            "cataclysm:soul_black_smith", "Soul Blacksmith",
            false, true, true, YRange.ANY,
            "minecraft:soul_sand", "minecraft:blackstone", "minecraft:nether_bricks",
            7, "minecraft:soul_sand_valley", Set.of("nether", "boss", "cataclysm")
        ));
    }

    // ============== API ==============

    /**
     * Register a structure's characteristics.
     */
    public static void register(StructureCharacteristics characteristics) {
        REGISTRY.put(characteristics.structureId(), characteristics);
    }

    /**
     * Get characteristics for a structure.
     */
    public static Optional<StructureCharacteristics> get(String structureId) {
        return Optional.ofNullable(REGISTRY.get(structureId));
    }

    /**
     * Get characteristics for a structure by ResourceLocation.
     */
    public static Optional<StructureCharacteristics> get(ResourceLocation structureId) {
        return get(structureId.toString());
    }

    /**
     * Find structures matching a tag.
     */
    public static Map<String, StructureCharacteristics> findByTag(String tag) {
        Map<String, StructureCharacteristics> result = new LinkedHashMap<>();
        for (var entry : REGISTRY.entrySet()) {
            if (entry.getValue().tags().contains(tag)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * Get all registered structures.
     */
    public static Map<String, StructureCharacteristics> getAll() {
        return Map.copyOf(REGISTRY);
    }

    /**
     * Check if a structure is registered.
     */
    public static boolean isRegistered(String structureId) {
        return REGISTRY.containsKey(structureId);
    }

    // ============== Derived Methods ==============

    /**
     * Returns preferred floor as ResourceLocation.
     * Falls back to stone_bricks if floorMaterial is somehow null.
     */
    public ResourceLocation floorBlock() {
        String material = floorMaterial != null ? floorMaterial : "minecraft:stone_bricks";
        return ResourceLocation.parse(material);
    }

    /**
     * Check if this structure is suitable for a "dungeon run" quest type.
     */
    public boolean isDungeon() {
        return tags.contains("dungeon") || underground || enclosed;
    }

    /**
     * Check if this structure typically houses boss mobs.
     */
    public boolean isBossStructure() {
        return tags.contains("boss");
    }

    /**
     * Get a description of the environment for logging.
     */
    public String describeEnvironment() {
        StringBuilder sb = new StringBuilder();
        if (underground) sb.append("underground, ");
        if (dark) sb.append("dark, ");
        if (enclosed) sb.append("enclosed, ");
        sb.append("floor=").append(floorMaterial);
        sb.append(", light=").append(lightLevel);
        return sb.toString();
    }
}
