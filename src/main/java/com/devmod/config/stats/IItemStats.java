package com.devmod.config.stats;

import net.minecraft.nbt.CompoundTag;

/**
 * Common interface for all item stats classes.
 * Provides a unified contract for serialization and manipulation.
 *
 * <p>Implementing classes: WeaponStats, ArmorStats, FoodStats, FuelStats, UsableStats
 */
public interface IItemStats {

    /**
     * Create a deep copy of this stats object.
     *
     * @return a new instance with identical values
     */
    IItemStats copy();

    /**
     * Save stats to an NBT compound tag.
     *
     * @param tag the tag to write to
     */
    void save(CompoundTag tag);

    /**
     * Load stats from an NBT compound tag.
     *
     * @param tag the tag to read from
     */
    void load(CompoundTag tag);

    /**
     * Check if all values are at defaults (no modifications).
     *
     * @return true if this represents unmodified/default stats
     */
    boolean isDefault();
}
