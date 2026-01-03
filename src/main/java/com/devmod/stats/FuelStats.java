package com.devmod.stats;

import java.util.Objects;

import net.minecraft.nbt.CompoundTag;

public class FuelStats {

    // ═══════════════════════════════════════════════════════════════
    // BURN TIME PROPERTIES
    // ═══════════════════════════════════════════════════════════════

    /** Burn time in ticks (how long fuel burns). 200 = coal, 1600 = coal block */
    private int burnTime = 0;

    /** Whether to override vanilla burn time */
    private boolean overrideDefault = false;

    /** Efficiency multiplier for burn time (0.1-3.0) */
    private float efficiencyMultiplier = 1.0f;

    // ═══════════════════════════════════════════════════════════════
    // COOK TIME PROPERTIES (per furnace type)
    // ═══════════════════════════════════════════════════════════════

    /** Cook time in standard furnace (default 200 ticks = 10 seconds) */
    private int furnaceCookTime = 200;

    /** Cook time in blast furnace (default 100 ticks = 5 seconds) */
    private int blastFurnaceCookTime = 100;

    /** Cook time in smoker (default 100 ticks = 5 seconds) */
    private int smokerCookTime = 100;

    /** Cook time on campfire (default 600 ticks = 30 seconds) */
    private int campfireCookTime = 600;

    /** Whether custom cook times are enabled */
    private boolean customCookTimesEnabled = false;

    public int getBurnTime() {
        return burnTime;
    }

    public void setBurnTime(int value) {
        burnTime = value;
    }

    public boolean isOverrideDefault() {
        return overrideDefault;
    }

    public void setOverrideDefault(boolean value) {
        overrideDefault = value;
    }

    public float getEfficiencyMultiplier() {
        return efficiencyMultiplier;
    }

    public void setEfficiencyMultiplier(float value) {
        efficiencyMultiplier = value;
    }

    public int getFurnaceCookTime() {
        return furnaceCookTime;
    }

    public void setFurnaceCookTime(int value) {
        furnaceCookTime = value;
    }

    public int getBlastFurnaceCookTime() {
        return blastFurnaceCookTime;
    }

    public void setBlastFurnaceCookTime(int value) {
        blastFurnaceCookTime = value;
    }

    public int getSmokerCookTime() {
        return smokerCookTime;
    }

    public void setSmokerCookTime(int value) {
        smokerCookTime = value;
    }

    public int getCampfireCookTime() {
        return campfireCookTime;
    }

    public void setCampfireCookTime(int value) {
        campfireCookTime = value;
    }

    public boolean isCustomCookTimesEnabled() {
        return customCookTimesEnabled;
    }

    public void setCustomCookTimesEnabled(boolean value) {
        customCookTimesEnabled = value;
    }

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ═══════════════════════════════════════════════════════════════

    public FuelStats() {}

    public FuelStats copy() {
        FuelStats copy = new FuelStats();
        copy.setBurnTime(this.getBurnTime());
        copy.setOverrideDefault(this.isOverrideDefault());
        copy.setEfficiencyMultiplier(this.getEfficiencyMultiplier());
        copy.setFurnaceCookTime(this.getFurnaceCookTime());
        copy.setBlastFurnaceCookTime(this.getBlastFurnaceCookTime());
        copy.setSmokerCookTime(this.getSmokerCookTime());
        copy.setCampfireCookTime(this.getCampfireCookTime());
        copy.setCustomCookTimesEnabled(this.isCustomCookTimesEnabled());
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
        if (!(o instanceof FuelStats that)) return false;
        return burnTime == that.getBurnTime() &&
               overrideDefault == that.isOverrideDefault() &&
               Float.compare(that.getEfficiencyMultiplier(), efficiencyMultiplier) == 0 &&
               furnaceCookTime == that.getFurnaceCookTime() &&
               blastFurnaceCookTime == that.getBlastFurnaceCookTime() &&
               smokerCookTime == that.getSmokerCookTime() &&
               campfireCookTime == that.getCampfireCookTime() &&
               customCookTimesEnabled == that.isCustomCookTimesEnabled();
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
