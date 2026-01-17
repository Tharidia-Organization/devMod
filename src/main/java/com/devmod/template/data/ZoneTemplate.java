package com.devmod.template.data;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Zone types for Nexus areas with associated color palettes.
 * Each zone has a primary pad color and accent block.
 *
 * Based on Nexus structure:
 * 1. Spawn, 2. Tutorial, 3. Hub Quest, 4. Gate Progression,
 * 5. Building, 6. Town Test, 7. Economia, 8. War Hub,
 * 9. Classes System, 10. Eventi, 11. DM Mod
 */
@SuppressWarnings("ImmutableEnumChecker")
public enum ZoneTemplate implements StringRepresentable {
    // Core zones
    SPAWN("spawn", Blocks.WHITE_CONCRETE, Blocks.QUARTZ_BLOCK, 0xFFFFFF),
    TUTORIAL("tutorial", Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_STAINED_GLASS, 0x4488FF),

    // Quest system
    QUEST_HUB("quest_hub", Blocks.YELLOW_CONCRETE, Blocks.GOLD_BLOCK, 0xFFDD00),
    QUEST_BOARD("quest_board", Blocks.ORANGE_CONCRETE, Blocks.HONEY_BLOCK, 0xFF8800),

    // Progression
    GATE_PROGRESSION("gate_progression", Blocks.PURPLE_CONCRETE, Blocks.AMETHYST_BLOCK, 0xAA44FF),
    WORLD_BOSS("world_boss", Blocks.RED_CONCRETE, Blocks.NETHER_BRICKS, 0xFF4444),

    // Building/Claims
    BUILDING("building", Blocks.LIME_CONCRETE, Blocks.MOSS_BLOCK, 0x44FF44),
    CLAIM("claim", Blocks.GREEN_CONCRETE, Blocks.EMERALD_BLOCK, 0x22AA22),

    // Town system
    TOWN_TEST("town_test", Blocks.CYAN_CONCRETE, Blocks.PRISMARINE, 0x44FFFF),
    TOWN_GESTIONALE("town_gestionale", Blocks.BLUE_CONCRETE, Blocks.LAPIS_BLOCK, 0x4444FF),
    TOWN_POLITICA("town_politica", Blocks.MAGENTA_CONCRETE, Blocks.PURPUR_BLOCK, 0xFF44FF),

    // Resources
    RESOURCE_MINING("resource_mining", Blocks.GRAY_CONCRETE, Blocks.IRON_BLOCK, 0x888888),
    RESOURCE_FARMING("resource_farming", Blocks.BROWN_CONCRETE, Blocks.FARMLAND, 0x8B4513),
    DUNGEON_FARM("dungeon_farm", Blocks.BLACK_CONCRETE, Blocks.OBSIDIAN, 0x222222),

    // Economy
    ECONOMY_SHOP("economy_shop", Blocks.YELLOW_CONCRETE, Blocks.GOLD_BLOCK, 0xFFDD00),
    ECONOMY_BANK("economy_bank", Blocks.LIGHT_GRAY_CONCRETE, Blocks.IRON_BLOCK, 0xAAAAAA),
    ECONOMY_AUCTION("economy_auction", Blocks.ORANGE_CONCRETE, Blocks.COPPER_BLOCK, 0xDD8844),
    ECONOMY_CARAVAN("economy_caravan", Blocks.BROWN_CONCRETE, Blocks.BARREL, 0x8B4513),

    // War
    WAR_HUB("war_hub", Blocks.RED_CONCRETE, Blocks.NETHER_BRICKS, 0xFF4444),
    WAR_ARENA("war_arena", Blocks.ORANGE_CONCRETE, Blocks.SMOOTH_STONE, 0xFF8800),
    WAR_SIEGE("war_siege", Blocks.GRAY_CONCRETE, Blocks.STONE_BRICKS, 0x666666),
    WAR_FORTIFICATION("war_fortification", Blocks.BLACK_CONCRETE, Blocks.DEEPSLATE_BRICKS, 0x333333),

    // Classes
    CLASS_MASTER("class_master", Blocks.LIGHT_BLUE_CONCRETE, Blocks.DIAMOND_BLOCK, 0x44DDFF),
    CLASS_CRAFTING("class_crafting", Blocks.ORANGE_CONCRETE, Blocks.SMITHING_TABLE, 0xFF8800),

    // Minigame labs
    LAB_FORGING("lab_forging", Blocks.ORANGE_CONCRETE, Blocks.BLAST_FURNACE, 0xFF6600),
    LAB_ALCHEMY("lab_alchemy", Blocks.PURPLE_CONCRETE, Blocks.BREWING_STAND, 0xAA44FF),
    LAB_COOKING("lab_cooking", Blocks.BROWN_CONCRETE, Blocks.SMOKER, 0x8B4513),
    LAB_FARMING("lab_farming", Blocks.LIME_CONCRETE, Blocks.COMPOSTER, 0x44FF44),
    LAB_MINING("lab_mining", Blocks.GRAY_CONCRETE, Blocks.STONECUTTER, 0x888888),
    LAB_HUSBANDRY("lab_husbandry", Blocks.PINK_CONCRETE, Blocks.HAY_BLOCK, 0xFFAACC),

    // Events
    EVENTI_TORNEO("eventi_torneo", Blocks.GOLD_BLOCK, Blocks.GILDED_BLACKSTONE, 0xFFDD00),

    // DM/Admin
    DM_MOD("dm_mod", Blocks.BLACK_CONCRETE, Blocks.COMMAND_BLOCK, 0x00FFFF);

    private final String name;
    private final Block padBlock;
    private final Block accentBlock;
    private final int color;

    ZoneTemplate(String name, Block padBlock, Block accentBlock, int color) {
        this.name = name;
        this.padBlock = padBlock;
        this.accentBlock = accentBlock;
        this.color = color;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return Objects.requireNonNull(name);
    }

    public Block getPadBlock() {
        return padBlock;
    }

    public Block getAccentBlock() {
        return accentBlock;
    }

    public int getColor() {
        return color;
    }

    public int getColorWithAlpha() {
        return 0xFF000000 | color;
    }

    public static ZoneTemplate fromString(String name) {
        for (ZoneTemplate zone : values()) {
            if (zone.name.equals(name)) {
                return zone;
            }
        }
        return SPAWN;
    }
}
