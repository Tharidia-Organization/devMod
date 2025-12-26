package com.devmod.client.ui.editor.modules;

import java.util.Objects;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.components.SourceBadge;
import com.devmod.config.FoodConfigManager;
import com.devmod.stats.FoodStats;

/**
 * Core logic for FoodModule: stats management, loading, saving.
 */
public class FoodModuleCore {

    @SuppressWarnings("unused")
    private final FoodModule module;

    private FoodStats stats = new FoodStats();
    private FoodStats originalStats = new FoodStats();
    private SourceBadge.Source dataSource = SourceBadge.Source.VANILLA;
    private String sourcePrefix = "VAN";

    public FoodModuleCore(FoodModule module) {
        this.module = module;
    }

    // ═══════════════════════════════════════════════════════════════
    // STATS ACCESS
    // ═══════════════════════════════════════════════════════════════

    public FoodStats getStats() {
        return stats;
    }

    public void setStats(FoodStats newStats) {
        this.stats = newStats;
    }

    public FoodStats getOriginalStats() {
        return originalStats;
    }

    public void setOriginalStats(FoodStats newOriginal) {
        this.originalStats = newOriginal;
    }

    public SourceBadge.Source getDataSource() {
        return dataSource;
    }

    public String getSourcePrefix() {
        return sourcePrefix;
    }

    public boolean hasModifications() {
        return !Objects.equals(stats, originalStats);
    }

    // ═══════════════════════════════════════════════════════════════
    // LOADING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Load food stats from item, using priority: Component > CustomData > Global > Vanilla
     */
    public void loadStatsFromItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            stats = new FoodStats();
            originalStats = new FoodStats();
            dataSource = SourceBadge.Source.VANILLA;
            sourcePrefix = "VAN";
            return;
        }

        // Use FoodConfigManager which handles priority
        stats = FoodConfigManager.getStats(item);
        originalStats = stats.copy();

        // Determine source for badge display
        determineDataSource(item);

        DevMod.LOGGER.info("[Editor][Food] Loaded stats from {}: nutrition={} saturation={} effects={}",
            sourcePrefix, stats.nutrition, stats.saturation, stats.effects.size());
    }

    private void determineDataSource(ItemStack item) {
        // Check if item has our custom component
        var componentType = com.devmod.components.FoodComponents.foodStatsComponent();
        if (componentType != null && item.has(componentType)) {
            dataSource = SourceBadge.Source.DEV;
            sourcePrefix = "DEV";
            return;
        }

        // Check for CustomData
        var customData = item.get(Objects.requireNonNull(DataComponents.CUSTOM_DATA, "custom data component"));
        if (customData != null && customData.copyTag().contains("FoodModStats")) {
            dataSource = SourceBadge.Source.NBT;
            sourcePrefix = "NBT";
            return;
        }

        // Default to vanilla
        dataSource = SourceBadge.Source.VANILLA;
        sourcePrefix = "VAN";
    }

    /**
     * Apply vanilla food properties as defaults to the target stats.
     */
    public void applyVanillaDefaults(ItemStack item, FoodStats target) {
        if (item == null || item.isEmpty()) return;

        try {
            FoodProperties food = item.get(Objects.requireNonNull(DataComponents.FOOD, "food component"));
            if (food != null) {
                target.nutrition = food.nutrition();
                target.saturation = food.saturation();
                target.canAlwaysEat = food.canAlwaysEat();
            }

            // Default consumption time
            target.consumptionTime = 32;

            DevMod.LOGGER.info("[Editor][Food] Vanilla defaults -> nutrition={} saturation={} canAlwaysEat={}",
                target.nutrition, target.saturation, target.canAlwaysEat);
        } catch (Exception e) {
            DevMod.LOGGER.warn("[Editor][Food] Failed to apply vanilla defaults", e);
        }
    }
}
