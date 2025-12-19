package com.frenkvs.devmod;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

/**
 * Data class holding all modifiable fuel item statistics.
 * Supports NBT serialization for persistence and network sync.
 */
public class FuelStats {

    // ═══════════════════════════════════════════════════════════════
    // BURN TIME PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    /** Burn time in ticks (how long fuel burns). 200 = coal, 1600 = coal block */
    public int burnTime = 0;

    /** Whether to override vanilla burn time */
    public boolean overrideDefault = false;

    /** Efficiency multiplier for burn time (0.1-3.0) */
    public float efficiencyMultiplier = 1.0f;

    // ═══════════════════════════════════════════════════════════════
    // COOK TIME PROPERTIES (per furnace type)
    // ═══════════════════════════════════════════════════════════════

    /** Cook time in standard furnace (default 200 ticks = 10 seconds) */
    public int furnaceCookTime = 200;

    /** Cook time in blast furnace (default 100 ticks = 5 seconds) */
    public int blastFurnaceCookTime = 100;

    /** Cook time in smoker (default 100 ticks = 5 seconds) */
    public int smokerCookTime = 100;

    /** Cook time on campfire (default 600 ticks = 30 seconds) */
    public int campfireCookTime = 600;

    /** Whether custom cook times are enabled */
    public boolean customCookTimesEnabled = false;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════

    public FuelStats() {}

    public FuelStats copy() {
        FuelStats copy = new FuelStats();
        copy.burnTime = this.burnTime;
        copy.overrideDefault = this.overrideDefault;
        copy.efficiencyMultiplier = this.efficiencyMultiplier;
        copy.furnaceCookTime = this.furnaceCookTime;
        copy.blastFurnaceCookTime = this.blastFurnaceCookTime;
        copy.smokerCookTime = this.smokerCookTime;
        copy.campfireCookTime = this.campfireCookTime;
        copy.customCookTimesEnabled = this.customCookTimesEnabled;
        return copy;
    }

    // ═══════════════════════════════════════════════════════════════
    // NBT SERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    public void save(CompoundTag tag) {
        tag.putInt("burnTime", burnTime);
        tag.putBoolean("overrideDefault", overrideDefault);
        tag.putFloat("efficiencyMultiplier", efficiencyMultiplier);
        tag.putInt("furnaceCookTime", furnaceCookTime);
        tag.putInt("blastFurnaceCookTime", blastFurnaceCookTime);
        tag.putInt("smokerCookTime", smokerCookTime);
        tag.putInt("campfireCookTime", campfireCookTime);
        tag.putBoolean("customCookTimesEnabled", customCookTimesEnabled);
    }

    public void loadFrom(CompoundTag tag) {
        burnTime = tag.getInt("burnTime");
        overrideDefault = tag.getBoolean("overrideDefault");
        efficiencyMultiplier = tag.contains("efficiencyMultiplier") ? tag.getFloat("efficiencyMultiplier") : 1.0f;
        furnaceCookTime = tag.contains("furnaceCookTime") ? tag.getInt("furnaceCookTime") : 200;
        blastFurnaceCookTime = tag.contains("blastFurnaceCookTime") ? tag.getInt("blastFurnaceCookTime") : 100;
        smokerCookTime = tag.contains("smokerCookTime") ? tag.getInt("smokerCookTime") : 100;
        campfireCookTime = tag.contains("campfireCookTime") ? tag.getInt("campfireCookTime") : 600;
        customCookTimesEnabled = tag.getBoolean("customCookTimesEnabled");
    }

    public static FuelStats load(CompoundTag tag) {
        FuelStats stats = new FuelStats();
        stats.loadFrom(tag);
        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get effective burn time with efficiency multiplier applied.
     */
    public int getEffectiveBurnTime() {
        return Math.round(burnTime * efficiencyMultiplier);
    }

    /**
     * Convert burn time to number of items that can be smelted.
     * Standard smelting takes 200 ticks per item.
     */
    public float getItemsSmeltable() {
        return getEffectiveBurnTime() / 200.0f;
    }

    /**
     * Check if these stats represent default/unmodified values.
     */
    public boolean isDefault() {
        return burnTime == 0 &&
               !overrideDefault &&
               Math.abs(efficiencyMultiplier - 1.0f) < 0.001f &&
               furnaceCookTime == 200 &&
               blastFurnaceCookTime == 100 &&
               smokerCookTime == 100 &&
               campfireCookTime == 600 &&
               !customCookTimesEnabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FuelStats that = (FuelStats) o;
        return burnTime == that.burnTime &&
               overrideDefault == that.overrideDefault &&
               Float.compare(that.efficiencyMultiplier, efficiencyMultiplier) == 0 &&
               furnaceCookTime == that.furnaceCookTime &&
               blastFurnaceCookTime == that.blastFurnaceCookTime &&
               smokerCookTime == that.smokerCookTime &&
               campfireCookTime == that.campfireCookTime &&
               customCookTimesEnabled == that.customCookTimesEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(burnTime, overrideDefault, efficiencyMultiplier,
            furnaceCookTime, blastFurnaceCookTime, smokerCookTime, campfireCookTime, customCookTimesEnabled);
    }

    @Override
    public String toString() {
        return "FuelStats{" +
               "burnTime=" + burnTime +
               ", overrideDefault=" + overrideDefault +
               ", efficiency=" + efficiencyMultiplier +
               ", customCookTimes=" + customCookTimesEnabled +
               '}';
    }
}
