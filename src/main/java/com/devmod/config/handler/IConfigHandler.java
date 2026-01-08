package com.devmod.config.handler;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.devmod.config.stats.IItemStats;

/**
 * Handler for a specific type of item stats configuration.
 * Inspired by CraftTweaker's IRecipeHandler pattern.
 *
 * <p>Provides unified operations for:
 * <ul>
 *   <li>Decomposing stats into typed components</li>
 *   <li>Recomposing components back into stats</li>
 *   <li>Getting/setting stats on items</li>
 *   <li>Validation and clamping</li>
 *   <li>Persistence (load/save)</li>
 * </ul>
 *
 * @param <S> The stats type this handler manages
 */
public interface IConfigHandler<S extends IItemStats> {

    /**
     * Get the stats class this handler manages.
     */
    Class<S> getStatsClass();

    /**
     * Get all components supported by this handler.
     */
    Set<IConfigComponent<?>> getComponents();

    // ═══════════════════════════════════════════════════════════════
    // DECOMPOSE/RECOMPOSE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Decompose stats into a component map.
     *
     * @param stats the stats to decompose
     * @return decomposed config with component values
     */
    IDecomposedConfig decompose(S stats);

    /**
     * Recompose a component map back into stats.
     *
     * @param decomposed the decomposed config
     * @return the recomposed stats, or empty if invalid
     */
    Optional<S> recompose(IDecomposedConfig decomposed);

    // ═══════════════════════════════════════════════════════════════
    // ITEM OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if this handler applies to the given item.
     *
     * @param stack the item to check
     * @return true if this handler can manage the item
     */
    boolean appliesTo(ItemStack stack);

    /**
     * Get stats for an item stack.
     * Priority: Component > CustomData > Global > Default
     *
     * @param stack the item stack
     * @return the stats for this item
     */
    S getStats(ItemStack stack);

    /**
     * Set specific stats on an item stack.
     *
     * @param stack the item stack to modify
     * @param stats the stats to apply
     */
    void setSpecificStats(ItemStack stack, S stats);

    /**
     * Clear specific stats from an item stack.
     *
     * @param stack the item stack to clear
     */
    void clearSpecificStats(ItemStack stack);

    /**
     * Get global stats for an item type.
     *
     * @param item the item type
     * @return the global stats, or null if not set
     */
    S getGlobalStats(Item item);

    /**
     * Set global stats for an item type.
     *
     * @param item the item type
     * @param stats the stats to set
     */
    void setGlobalStats(Item item, S stats);

    /**
     * Clear global stats for an item type.
     *
     * @param item the item type
     */
    void clearGlobalStats(Item item);

    /**
     * Get all global stats.
     *
     * @return immutable map of item to stats
     */
    Map<Item, S> getAllGlobalStats();

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Validate and clamp stats to safe values.
     *
     * @param stats the stats to validate
     * @return the clamped stats
     */
    S validateAndClamp(S stats);

    /**
     * Create a new default stats instance.
     *
     * @return new default stats
     */
    S createDefault();

    // ═══════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load global stats from persistent storage.
     */
    void load();

    /**
     * Save global stats to persistent storage.
     */
    void save();
}
