package com.devmod.client.ui.editor.components;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Centralized Minecraft item icons for editor buttons.
 * Each editor type has an associated item icon for visual identification.
 */
public final class EditorIcons {

    private EditorIcons() {} // Utility class

    // ============================================
    // ITEM EDITORS
    // ============================================

    /** Auto-detect item editor (smart selection) */
    public static final ItemStack ITEM_AUTO = new ItemStack(Items.NETHER_STAR);

    /** Weapon editor - swords, axes, etc. */
    public static final ItemStack WEAPON = new ItemStack(Items.IRON_SWORD);

    /** Armor editor - chestplates, helmets, etc. */
    public static final ItemStack ARMOR = new ItemStack(Items.IRON_CHESTPLATE);

    /** Shield editor */
    public static final ItemStack SHIELD = new ItemStack(Items.SHIELD);

    /** General item editor */
    public static final ItemStack ITEM_GENERAL = new ItemStack(Items.CHEST);

    /** Recipe editor - crafting recipes */
    public static final ItemStack RECIPE = new ItemStack(Items.CRAFTING_TABLE);

    /** Food editor - consumables */
    public static final ItemStack FOOD = new ItemStack(Items.GOLDEN_APPLE);

    /** Fuel editor - furnace fuels */
    public static final ItemStack FUEL = new ItemStack(Items.COAL);

    /** Usable item editor - items with use actions */
    public static final ItemStack USABLE = new ItemStack(Items.ENDER_PEARL);

    // ============================================
    // MOB EDITORS
    // ============================================

    /** Mob configuration editor */
    public static final ItemStack MOB = new ItemStack(Items.ZOMBIE_SPAWN_EGG);

    /** Mob equipment editor */
    public static final ItemStack MOB_EQUIPMENT = new ItemStack(Items.LEATHER_CHESTPLATE);

    // ============================================
    // QUEST & PROGRESSION
    // ============================================

    /** Quest editor */
    public static final ItemStack QUEST = new ItemStack(Items.BOOK);

    /** Endurance mode editor */
    public static final ItemStack ENDURANCE = new ItemStack(Items.CLOCK);

    /** Stamina system editor */
    public static final ItemStack STAMINA = new ItemStack(Items.FEATHER);

    // ============================================
    // WORLD TOOLS
    // ============================================

    /** Room bounds editor */
    public static final ItemStack ROOM_BOUNDS = new ItemStack(Items.STRUCTURE_BLOCK);

    // ============================================
    // NAVIGATION
    // ============================================

    /** Back/return navigation */
    public static final ItemStack BACK = new ItemStack(Items.OAK_DOOR);

    /** Arrow for navigation indicators */
    public static final ItemStack ARROW = new ItemStack(Items.ARROW);

    /** Settings/configuration */
    public static final ItemStack SETTINGS = new ItemStack(Items.REPEATER);

    /** Help/info */
    public static final ItemStack HELP = new ItemStack(Items.KNOWLEDGE_BOOK);
}
