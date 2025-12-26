package com.devmod.client.ui.editor.modules;

import java.util.Objects;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.components.SourceBadge;
import com.devmod.config.FuelConfigManager;
import com.devmod.stats.FuelStats;

/**
 * Core stats management for FuelModule.
 * Handles loading, saving, and state management.
 */
public class FuelModuleCore {

    static final String NBT_KEY = "FuelModStats";
    static final double EPSILON = 1e-4;

    // Stats state
    FuelStats stats = new FuelStats();
    FuelStats originalStats = new FuelStats();
    String sourcePrefix = "";
    SourceBadge.Source dataSource = SourceBadge.Source.VANILLA;

    public FuelModuleCore() {
    }

    // ═══════════════════════════════════════════════════════════════
    // LOADING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load stats from item NBT/components.
     */
    public void loadStatsFromItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            stats = new FuelStats();
            originalStats = new FuelStats();
            dataSource = SourceBadge.Source.VANILLA;
            sourcePrefix = "[VANILLA] ";
            return;
        }

        CompoundTag statsTag = null;
        try {
            var fuelComponent = com.devmod.components.FuelComponents.fuelStatsComponent();
            if (fuelComponent != null) {
                statsTag = item.get(fuelComponent);
            }
        } catch (Exception ignored) { }

        CompoundTag customTag;
        try {
            customTag = item.getOrDefault(
                Objects.requireNonNull(DataComponents.CUSTOM_DATA),
                Objects.requireNonNull(CustomData.EMPTY)
            ).copyTag();
        } catch (Exception ignored) {
            customTag = new CompoundTag();
        }
        if (customTag == null) {
            customTag = new CompoundTag();
        }

        DevMod.LOGGER.info("[Editor][Fuel] Item={} | hasComponent={} | hasCustomData={}",
            item.getItem().toString(),
            statsTag != null && !statsTag.isEmpty(),
            customTag != null && !customTag.isEmpty());

        if (statsTag == null || statsTag.isEmpty()) {
            try {
                if (customTag.contains(NBT_KEY)) {
                    statsTag = customTag.getCompound(NBT_KEY);
                }
            } catch (Exception ignored) {
                statsTag = new CompoundTag();
            }
        }

        if (statsTag != null && !statsTag.isEmpty()) {
            sourcePrefix = "[DEV] ";
            dataSource = SourceBadge.Source.DEV;
            DevMod.LOGGER.info("[Editor][Fuel] Loaded stats from component tag (size={})", statsTag.size());
            stats = FuelStats.load(statsTag);
        } else {
            boolean hasCustomData = customTag != null && !customTag.isEmpty();
            sourcePrefix = hasCustomData ? "[NBT] " : "[VANILLA] ";
            dataSource = hasCustomData ? SourceBadge.Source.NBT : SourceBadge.Source.VANILLA;
            DevMod.LOGGER.info("[Editor][Fuel] No custom stats found; applying vanilla defaults.");
            stats = new FuelStats();
            applyVanillaDefaults(item, stats);
        }

        originalStats = stats.copy();
    }

    /**
     * Populate stats from vanilla item properties.
     */
    void applyVanillaDefaults(ItemStack item, FuelStats target) {
        if (target == null || item == null) return;

        try {
            int vanillaBurnTime = FuelConfigManager.getVanillaBurnTime(item);
            if (vanillaBurnTime > 0) {
                target.burnTime = vanillaBurnTime;
            }

            // Default cook times (vanilla values)
            target.furnaceCookTime = 200;
            target.blastFurnaceCookTime = 100;
            target.smokerCookTime = 100;
            target.campfireCookTime = 600;

            DevMod.LOGGER.info("[Editor][Fuel] Vanilla defaults -> burnTime={} furnace={} blast={} smoker={} campfire={}",
                target.burnTime, target.furnaceCookTime, target.blastFurnaceCookTime,
                target.smokerCookTime, target.campfireCookTime);
        } catch (Exception e) {
            DevMod.LOGGER.warn("[Editor][Fuel] Failed to apply vanilla defaults", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    public FuelStats getStats() {
        return stats;
    }

    public void setStats(FuelStats newStats) {
        this.stats = newStats != null ? newStats : new FuelStats();
    }

    public FuelStats getOriginalStats() {
        return originalStats;
    }

    public void setOriginalStats(FuelStats newOriginal) {
        this.originalStats = newOriginal != null ? newOriginal : new FuelStats();
    }

    public SourceBadge.Source getDataSource() {
        return dataSource;
    }

    public String getSourcePrefix() {
        return sourcePrefix;
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPARISON
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if current stats differ from original.
     */
    public boolean hasModifications() {
        if (stats.burnTime != originalStats.burnTime) return true;
        if (stats.overrideDefault != originalStats.overrideDefault) return true;
        if (Math.abs(stats.efficiencyMultiplier - originalStats.efficiencyMultiplier) > EPSILON) return true;
        if (stats.furnaceCookTime != originalStats.furnaceCookTime) return true;
        if (stats.blastFurnaceCookTime != originalStats.blastFurnaceCookTime) return true;
        if (stats.smokerCookTime != originalStats.smokerCookTime) return true;
        if (stats.campfireCookTime != originalStats.campfireCookTime) return true;
        if (stats.customCookTimesEnabled != originalStats.customCookTimesEnabled) return true;
        return false;
    }

    /**
     * Get CustomData tag from item (utility method for module).
     */
    public CompoundTag getCustomDataTag(ItemStack item) {
        try {
            return item.getOrDefault(
                Objects.requireNonNull(DataComponents.CUSTOM_DATA),
                Objects.requireNonNull(CustomData.EMPTY)
            ).copyTag();
        } catch (Exception e) {
            return new CompoundTag();
        }
    }
}
