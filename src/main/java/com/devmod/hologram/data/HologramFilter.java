package com.devmod.hologram.data;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Filters for highlighting specific block types in the hologram.
 * Allows players to easily spot valuable resources.
 */
@SuppressWarnings("ImmutableEnumChecker")
public enum HologramFilter {
    /** Highlight diamond ore and deepslate diamond ore */
    DIAMONDS("Diamonds", 0x4FF0FF, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DIAMOND_BLOCK),

    /** Highlight gold ore and deepslate gold ore */
    GOLD("Gold", 0xFFD700, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.GOLD_BLOCK, Blocks.RAW_GOLD_BLOCK),

    /** Highlight iron ore and deepslate iron ore */
    IRON("Iron", 0xD4A574, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.IRON_BLOCK, Blocks.RAW_IRON_BLOCK),

    /** Highlight emerald ore and deepslate emerald ore */
    EMERALDS("Emeralds", 0x50C878, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.EMERALD_BLOCK),

    /** Highlight lapis lazuli ore */
    LAPIS("Lapis", 0x1E90FF, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.LAPIS_BLOCK),

    /** Highlight redstone ore */
    REDSTONE("Redstone", 0xFF0000, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.REDSTONE_BLOCK),

    /** Highlight copper ore */
    COPPER("Copper", 0xB87333, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.COPPER_BLOCK, Blocks.RAW_COPPER_BLOCK),

    /** Highlight ancient debris (netherite) */
    NETHERITE("Netherite", 0x4A3728, Blocks.ANCIENT_DEBRIS, Blocks.NETHERITE_BLOCK),

    /** Highlight coal ore */
    COAL("Coal", 0x2F2F2F, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.COAL_BLOCK),

    /** Highlight chests and containers */
    CHESTS("Chests", 0xFFAA00, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.BARREL, Blocks.SHULKER_BOX),

    /** Highlight spawners */
    SPAWNERS("Spawners", 0xFF00FF, Blocks.SPAWNER, Blocks.TRIAL_SPAWNER);

    private final String displayName;
    private final int highlightColor;
    private final ImmutableSet<Block> blocks;

    HologramFilter(String displayName, int highlightColor, Block... blocks) {
        this.displayName = displayName;
        this.highlightColor = highlightColor;
        this.blocks = ImmutableSet.copyOf(blocks);
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the highlight color for this filter (RGB format).
     */
    public int getHighlightColor() {
        return highlightColor;
    }

    /**
     * Check if a block state matches this filter.
     */
    public boolean matches(BlockState state) {
        return blocks.contains(state.getBlock());
    }

    /**
     * Check if a block state matches any of the given filters.
     * Returns the matching filter or null if none match.
     */
    @Nullable
    public static HologramFilter getMatchingFilter(BlockState state, EnumSet<HologramFilter> activeFilters) {
        for (HologramFilter filter : activeFilters) {
            if (filter.matches(state)) {
                return filter;
            }
        }
        return null;
    }

    /**
     * Get a default set of valuable filters (diamonds, gold, emeralds, chests, spawners).
     */
    public static EnumSet<HologramFilter> getDefaultFilters() {
        return EnumSet.of(DIAMONDS, GOLD, EMERALDS, CHESTS, SPAWNERS);
    }

    /**
     * Get all ore filters.
     */
    public static EnumSet<HologramFilter> getAllOres() {
        return EnumSet.of(DIAMONDS, GOLD, IRON, EMERALDS, LAPIS, REDSTONE, COPPER, NETHERITE, COAL);
    }

    /**
     * Get an empty filter set (no highlighting).
     */
    public static EnumSet<HologramFilter> none() {
        return EnumSet.noneOf(HologramFilter.class);
    }

    /**
     * Get all filters.
     */
    public static EnumSet<HologramFilter> all() {
        return EnumSet.allOf(HologramFilter.class);
    }
}
