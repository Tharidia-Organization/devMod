package com.devmod.area.aesthetic;

import java.util.Locale;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * BIBBIA ESTETICA - REGOLA 7: ICONE TABS E AZIONI
 *
 * Set di icone standard basate su Items Minecraft.
 * Queste icone definiscono l'identità visiva delle GUI del sistema.
 */
public final class AreaBuilderIcons {

    private AreaBuilderIcons() {}

    // ============================================================================
    // TAB ICONS
    // ============================================================================

    /** Icon for Shape tab */
    public static final Item TAB_SHAPE = Items.COMPASS;

    /** Icon for Dimensions tab */
    public static final Item TAB_DIMENSIONS = Items.SCAFFOLDING;

    /** Icon for Materials tab */
    public static final Item TAB_MATERIALS = Items.BRUSH;

    /** Icon for Biome tab */
    public static final Item TAB_BIOME = Items.AZALEA;

    /** Icon for Options tab */
    public static final Item TAB_OPTIONS = Items.COMPARATOR;

    // ============================================================================
    // SHAPE ICONS
    // ============================================================================

    /** Icon for rectangular shape */
    public static final Item SHAPE_RECT = Items.BRICK;

    /** Icon for circular shape */
    public static final Item SHAPE_CIRCLE = Items.ENDER_PEARL;

    /** Icon for hexagonal shape */
    public static final Item SHAPE_HEX = Items.HONEYCOMB;

    /** Icon for octagonal shape */
    public static final Item SHAPE_OCT = Items.AMETHYST_SHARD;

    /** Icon for custom NBT shape */
    public static final Item SHAPE_NBT = Items.STRUCTURE_BLOCK;

    // ============================================================================
    // ACTION ICONS
    // ============================================================================

    /** Icon for preview action */
    public static final Item ACTION_PREVIEW = Items.SPYGLASS;

    /** Icon for build action */
    public static final Item ACTION_BUILD = Items.DIAMOND_PICKAXE;

    /** Icon for save action */
    public static final Item ACTION_SAVE = Items.BOOK;

    /** Icon for cancel action */
    public static final Item ACTION_CANCEL = Items.BARRIER;

    /** Icon for reset action */
    public static final Item ACTION_RESET = Items.WATER_BUCKET;

    /** Icon for delete action */
    public static final Item ACTION_DELETE = Items.LAVA_BUCKET;

    /** Icon for edit action */
    public static final Item ACTION_EDIT = Items.WRITABLE_BOOK;

    /** Icon for copy action */
    public static final Item ACTION_COPY = Items.PAPER;

    // ============================================================================
    // STATE ICONS
    // ============================================================================

    /** Icon for locked state */
    public static final Item STATE_LOCKED = Items.IRON_BARS;

    /** Icon for unlocked state */
    public static final Item STATE_UNLOCKED = Items.IRON_DOOR;

    /** Icon for error state */
    public static final Item STATE_ERROR = Items.FIRE_CHARGE;

    /** Icon for success state */
    public static final Item STATE_SUCCESS = Items.EMERALD;

    /** Icon for warning state */
    public static final Item STATE_WARNING = Items.GLOWSTONE_DUST;

    /** Icon for info state */
    public static final Item STATE_INFO = Items.LIGHT_BLUE_DYE;

    // ============================================================================
    // BIOME ICONS
    // ============================================================================

    /** Icon for plains biome */
    public static final Item BIOME_PLAINS = Items.GRASS_BLOCK;

    /** Icon for forest biome */
    public static final Item BIOME_FOREST = Items.OAK_SAPLING;

    /** Icon for desert biome */
    public static final Item BIOME_DESERT = Items.SAND;

    /** Icon for mountain biome */
    public static final Item BIOME_MOUNTAIN = Items.STONE;

    /** Icon for ocean biome */
    public static final Item BIOME_OCEAN = Items.WATER_BUCKET;

    /** Icon for swamp biome */
    public static final Item BIOME_SWAMP = Items.LILY_PAD;

    /** Icon for jungle biome */
    public static final Item BIOME_JUNGLE = Items.JUNGLE_SAPLING;

    /** Icon for taiga biome */
    public static final Item BIOME_TAIGA = Items.SPRUCE_SAPLING;

    /** Icon for snowy biome */
    public static final Item BIOME_SNOWY = Items.SNOWBALL;

    /** Icon for nether biome */
    public static final Item BIOME_NETHER = Items.NETHERRACK;

    /** Icon for end biome */
    public static final Item BIOME_END = Items.END_STONE;

    /** Icon for mushroom biome */
    public static final Item BIOME_MUSHROOM = Items.RED_MUSHROOM;

    /** Icon for cherry grove biome */
    public static final Item BIOME_CHERRY = Items.CHERRY_SAPLING;

    // ============================================================================
    // DIMENSION ICONS (for sliders)
    // ============================================================================

    /** Icon for width dimension */
    public static final Item DIM_WIDTH = Items.RAIL;

    /** Icon for length dimension */
    public static final Item DIM_LENGTH = Items.LADDER;

    /** Icon for height dimension */
    public static final Item DIM_HEIGHT = Items.SCAFFOLDING;

    // ============================================================================
    // OPTION ICONS
    // ============================================================================

    /** Icon for walls option */
    public static final Item OPT_WALLS = Items.STONE_BRICKS;

    /** Icon for ceiling option */
    public static final Item OPT_CEILING = Items.SMOOTH_STONE_SLAB;

    /** Icon for floor option */
    public static final Item OPT_FLOOR = Items.DEEPSLATE_TILES;

    /** Icon for portals option */
    public static final Item OPT_PORTALS = Items.OBSIDIAN;

    /** Icon for building allowed option */
    public static final Item OPT_ALLOW_BUILD = Items.CRAFTING_TABLE;

    // ============================================================================
    // PRESET ICONS
    // ============================================================================

    /** Icon for default preset */
    public static final Item PRESET_DEFAULT = Items.DEEPSLATE_BRICKS;

    /** Icon for industrial preset */
    public static final Item PRESET_INDUSTRIAL = Items.IRON_BLOCK;

    /** Icon for fantasy preset */
    public static final Item PRESET_FANTASY = Items.AMETHYST_BLOCK;

    /** Icon for minimal preset */
    public static final Item PRESET_MINIMAL = Items.QUARTZ_BLOCK;

    /** Icon for nether preset */
    public static final Item PRESET_NETHER = Items.POLISHED_BLACKSTONE_BRICKS;

    /** Icon for end preset */
    public static final Item PRESET_END = Items.PURPUR_BLOCK;

    /** Icon for nature preset */
    public static final Item PRESET_NATURE = Items.MOSS_BLOCK;

    /** Icon for tech preset */
    public static final Item PRESET_TECH = Items.COPPER_BLOCK;

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Gets the tab icon for a given tab index.
     */
    public static Item getTabIcon(int tabIndex) {
        return switch (tabIndex) {
            case 0 -> TAB_SHAPE;
            case 1 -> TAB_DIMENSIONS;
            case 2 -> TAB_MATERIALS;
            case 3 -> TAB_BIOME;
            case 4 -> TAB_OPTIONS;
            default -> Items.BARRIER;
        };
    }

    /**
     * Gets the shape icon for a given shape name.
     */
    public static Item getShapeIcon(String shapeName) {
        return switch (shapeName.toUpperCase(Locale.ROOT)) {
            case "RECTANGULAR" -> SHAPE_RECT;
            case "CIRCULAR" -> SHAPE_CIRCLE;
            case "HEXAGONAL" -> SHAPE_HEX;
            case "OCTAGONAL" -> SHAPE_OCT;
            case "CUSTOM_NBT" -> SHAPE_NBT;
            default -> Items.BARRIER;
        };
    }

    /**
     * Gets the preset icon for a given preset ID.
     */
    public static Item getPresetIcon(String presetId) {
        return switch (presetId.toLowerCase(Locale.ROOT)) {
            case "default" -> PRESET_DEFAULT;
            case "industrial" -> PRESET_INDUSTRIAL;
            case "fantasy" -> PRESET_FANTASY;
            case "minimal" -> PRESET_MINIMAL;
            case "nether" -> PRESET_NETHER;
            case "end" -> PRESET_END;
            case "nature" -> PRESET_NATURE;
            case "tech" -> PRESET_TECH;
            default -> Items.BARRIER;
        };
    }
}
