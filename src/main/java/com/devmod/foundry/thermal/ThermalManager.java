package com.devmod.foundry.thermal;

import net.minecraft.nbt.CompoundTag;

/**
 * Manages thermal state for a foundry structure.
 * Tracks heat accumulation, thermal stress, and heat loss.
 */
public class ThermalManager {
    private static final String TAG_STRUCTURE_HEAT = "StructureHeat";
    private static final String TAG_THERMAL_STRESS = "ThermalStress";
    private static final String TAG_CYCLE_COUNT = "CycleCount";
    private static final String TAG_LAST_TEMP = "LastTemp";

    // Thermal properties
    private float structureHeat = 0f;
    private int thermalStress = 0;
    private int cycleCount = 0;
    private float lastTargetTemp = 0f;

    // Configuration (can be modified by structure type)
    private float heatCapacity = 1000f;
    private float heatLossRate = 0.5f;
    private float heatTransferRate = 0.1f;
    private int maxThermalStress = 1000;

    // Thresholds
    private static final float STRESS_DELTA_THRESHOLD = 200f;
    private static final float RAPID_HEAT_THRESHOLD = 50f;
    private static final float CACHE_EPSILON = 0.0001f;

    // Efficiency cache for performance optimization
    private float cachedEfficiencyTargetTemp = -1f;
    private float cachedEfficiencyMultiplier = 1.0f;
    private float lastStructureHeatForCache = -1f;

    public ThermalManager() {
    }

    public ThermalManager(float heatCapacity) {
        this.heatCapacity = heatCapacity;
    }

    /**
     * Called every tick to update thermal state.
     *
     * @param currentFuelTemp The temperature provided by fuel
     * @param isActive Whether the foundry is actively processing
     */
    public void tick(float currentFuelTemp, boolean isActive) {
        float targetTemp = isActive ? currentFuelTemp : 0f;

        // Calculate temperature delta for stress
        float tempDelta = Math.abs(targetTemp - structureHeat);

        // Accumulate thermal stress from rapid temperature changes
        if (tempDelta > STRESS_DELTA_THRESHOLD) {
            int stressGain = (int) ((tempDelta - STRESS_DELTA_THRESHOLD) / 100f);
            thermalStress = Math.min(maxThermalStress, thermalStress + stressGain);
        }

        // Detect heating cycles (crossing threshold)
        if ((lastTargetTemp < RAPID_HEAT_THRESHOLD && targetTemp >= RAPID_HEAT_THRESHOLD) ||
            (lastTargetTemp >= RAPID_HEAT_THRESHOLD && targetTemp < RAPID_HEAT_THRESHOLD)) {
            cycleCount++;
            // Rapid cycling causes additional stress
            if (cycleCount % 10 == 0) {
                thermalStress = Math.min(maxThermalStress, thermalStress + 50);
            }
        }
        lastTargetTemp = targetTemp;

        // Gradual heat transfer toward target
        if (Math.abs(targetTemp - structureHeat) > 1f) {
            float direction = Math.signum(targetTemp - structureHeat);
            float transfer = Math.min(
                Math.abs(targetTemp - structureHeat) * heatTransferRate,
                10f // Max transfer per tick
            );
            structureHeat += direction * transfer;
        }

        // Ambient heat loss
        if (structureHeat > 0) {
            structureHeat = Math.max(0, structureHeat - heatLossRate);
        }

        // Natural stress recovery (very slow)
        if (thermalStress > 0 && tempDelta < 50) {
            thermalStress = Math.max(0, thermalStress - 1);
        }
    }

    /**
     * Gets the effective temperature for processing.
     * Takes into account structure heat vs fuel temp.
     */
    public float getEffectiveTemperature(float fuelTemp) {
        // Structure needs to be heated up before full efficiency
        if (structureHeat < fuelTemp * 0.5f) {
            // Still heating up - reduced efficiency
            return structureHeat;
        }
        // At operating temperature
        return Math.min(structureHeat, fuelTemp);
    }

    /**
     * Checks if the structure is thermally stable.
     */
    public boolean isStable() {
        return thermalStress < maxThermalStress * 0.5f;
    }

    /**
     * Checks if thermal damage should occur.
     */
    public boolean shouldApplyDamage() {
        return thermalStress >= maxThermalStress;
    }

    /**
     * Gets efficiency multiplier based on thermal state.
     * Cold structure = slower processing.
     * Results are cached for performance when called multiple times per tick.
     */
    public float getEfficiencyMultiplier(float targetTemp) {
        if (targetTemp <= 0) return 1.0f;

        // Check if cached value is still valid
        if (Math.abs(targetTemp - cachedEfficiencyTargetTemp) < CACHE_EPSILON
            && Math.abs(structureHeat - lastStructureHeatForCache) < CACHE_EPSILON) {
            return cachedEfficiencyMultiplier;
        }

        // Calculate and cache
        float heatRatio = structureHeat / targetTemp;
        float efficiency;
        if (heatRatio >= 0.9f) {
            efficiency = 1.0f; // Full efficiency
        } else if (heatRatio >= 0.5f) {
            // Linear scaling between 50% and 100% heat ratio
            efficiency = 0.5f + (heatRatio - 0.5f) * 1.25f;
        } else {
            // Below 50% heat ratio - significantly reduced
            efficiency = Math.max(0.1f, heatRatio);
        }

        // Update cache
        cachedEfficiencyTargetTemp = targetTemp;
        lastStructureHeatForCache = structureHeat;
        cachedEfficiencyMultiplier = efficiency;

        return efficiency;
    }

    /**
     * Called when structure damage is applied.
     * Resets some stress but records the event.
     */
    public void onDamageApplied() {
        thermalStress = (int) (thermalStress * 0.7f);
    }

    /**
     * Resets thermal stress (e.g., after repairs).
     */
    public void resetStress() {
        thermalStress = 0;
    }

    // Getters

    public float getStructureHeat() {
        return structureHeat;
    }

    public int getThermalStress() {
        return thermalStress;
    }

    public int getCycleCount() {
        return cycleCount;
    }

    public float getHeatCapacity() {
        return heatCapacity;
    }

    public int getMaxThermalStress() {
        return maxThermalStress;
    }

    public float getStressPercent() {
        return (float) thermalStress / maxThermalStress;
    }

    // Setters for configuration

    public void setHeatCapacity(float heatCapacity) {
        this.heatCapacity = heatCapacity;
    }

    public void setHeatLossRate(float heatLossRate) {
        this.heatLossRate = heatLossRate;
    }

    public void setHeatTransferRate(float heatTransferRate) {
        this.heatTransferRate = heatTransferRate;
    }

    public void setMaxThermalStress(int maxThermalStress) {
        this.maxThermalStress = maxThermalStress;
    }

    // For testing/debug
    public void setThermalStress(int stress) {
        this.thermalStress = Math.max(0, Math.min(maxThermalStress, stress));
    }

    // Serialization

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat(TAG_STRUCTURE_HEAT, structureHeat);
        tag.putInt(TAG_THERMAL_STRESS, thermalStress);
        tag.putInt(TAG_CYCLE_COUNT, cycleCount);
        tag.putFloat(TAG_LAST_TEMP, lastTargetTemp);
        return tag;
    }

    public void load(CompoundTag tag) {
        structureHeat = tag.getFloat(TAG_STRUCTURE_HEAT);
        thermalStress = tag.getInt(TAG_THERMAL_STRESS);
        cycleCount = tag.getInt(TAG_CYCLE_COUNT);
        lastTargetTemp = tag.getFloat(TAG_LAST_TEMP);
    }
}
