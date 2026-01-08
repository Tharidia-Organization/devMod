package com.devmod.config.handler.impl;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.reflect.TypeToken;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import com.devmod.components.FuelComponents;
import com.devmod.config.component.BooleanComponent;
import com.devmod.config.component.FloatComponent;
import com.devmod.config.component.IntComponent;
import com.devmod.config.handler.AbstractConfigHandler;
import com.devmod.config.handler.DecomposedConfig;
import com.devmod.config.handler.IConfigComponent;
import com.devmod.config.handler.IDecomposedConfig;
import com.devmod.stats.FuelStats;

/**
 * Config handler for fuel stats.
 * Provides decompose/recompose functionality with typed components.
 */
public class FuelConfigHandler extends AbstractConfigHandler<FuelStats> {

    /** Singleton instance */
    public static final FuelConfigHandler INSTANCE = new FuelConfigHandler();

    // ═══════════════════════════════════════════════════════════════
    // COMPONENT DEFINITIONS
    // ═══════════════════════════════════════════════════════════════

    // Burn time properties
    public static final IntComponent BURN_TIME = new IntComponent(
        "burnTime", "Burn Time", "Burn", 0, 0, 100000);
    public static final BooleanComponent OVERRIDE_DEFAULT = new BooleanComponent(
        "overrideDefault", "Override Default", "Burn", false);
    public static final FloatComponent EFFICIENCY_MULTIPLIER = new FloatComponent(
        "efficiencyMultiplier", "Efficiency Multiplier", "Burn", 1.0f, 0.1f, 3.0f);

    // Cook time properties
    public static final IntComponent FURNACE_COOK_TIME = new IntComponent(
        "furnaceCookTime", "Furnace Cook Time", "Cook Time", 200, 1, 10000);
    public static final IntComponent BLAST_FURNACE_COOK_TIME = new IntComponent(
        "blastFurnaceCookTime", "Blast Furnace Cook Time", "Cook Time", 100, 1, 10000);
    public static final IntComponent SMOKER_COOK_TIME = new IntComponent(
        "smokerCookTime", "Smoker Cook Time", "Cook Time", 100, 1, 10000);
    public static final IntComponent CAMPFIRE_COOK_TIME = new IntComponent(
        "campfireCookTime", "Campfire Cook Time", "Cook Time", 600, 1, 10000);
    public static final BooleanComponent CUSTOM_COOK_TIMES_ENABLED = new BooleanComponent(
        "customCookTimesEnabled", "Custom Cook Times", "Cook Time", false);

    private static final Set<IConfigComponent<?>> ALL_COMPONENTS;

    static {
        ALL_COMPONENTS = new LinkedHashSet<>();
        // Burn
        ALL_COMPONENTS.add(BURN_TIME);
        ALL_COMPONENTS.add(OVERRIDE_DEFAULT);
        ALL_COMPONENTS.add(EFFICIENCY_MULTIPLIER);
        // Cook Time
        ALL_COMPONENTS.add(FURNACE_COOK_TIME);
        ALL_COMPONENTS.add(BLAST_FURNACE_COOK_TIME);
        ALL_COMPONENTS.add(SMOKER_COOK_TIME);
        ALL_COMPONENTS.add(CAMPFIRE_COOK_TIME);
        ALL_COMPONENTS.add(CUSTOM_COOK_TIMES_ENABLED);
    }

    public FuelConfigHandler() {
        super("fuel_configs.json", "FuelModStats");
    }

    // ═══════════════════════════════════════════════════════════════
    // ABSTRACT METHOD IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Class<FuelStats> getStatsClass() {
        return FuelStats.class;
    }

    @Override
    @Nullable
    protected DataComponentType<CompoundTag> getComponentType() {
        return FuelComponents.fuelStatsComponent();
    }

    @Override
    protected FuelStats fromTag(CompoundTag tag) {
        return FuelStats.fromTag(tag);
    }

    @Override
    protected Type getMapType() {
        return new TypeToken<Map<String, FuelStats>>(){}.getType();
    }

    @Override
    public FuelStats createDefault() {
        return new FuelStats();
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        // Fuel items can be detected by burn time, but this is typically any item
        // We'll allow all items since fuel config is generic
        return stack != null && !stack.isEmpty();
    }

    @Override
    public Set<IConfigComponent<?>> getComponents() {
        return ALL_COMPONENTS;
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public FuelStats validateAndClamp(FuelStats stats) {
        if (stats == null) return createDefault();

        // Use component clamp for all values
        stats.setBurnTime(BURN_TIME.clamp(stats.getBurnTime()));
        stats.setEfficiencyMultiplier(EFFICIENCY_MULTIPLIER.clamp(stats.getEfficiencyMultiplier()));
        stats.setFurnaceCookTime(FURNACE_COOK_TIME.clamp(stats.getFurnaceCookTime()));
        stats.setBlastFurnaceCookTime(BLAST_FURNACE_COOK_TIME.clamp(stats.getBlastFurnaceCookTime()));
        stats.setSmokerCookTime(SMOKER_COOK_TIME.clamp(stats.getSmokerCookTime()));
        stats.setCampfireCookTime(CAMPFIRE_COOK_TIME.clamp(stats.getCampfireCookTime()));

        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // DECOMPOSE/RECOMPOSE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void populateDecomposed(DecomposedConfig config, FuelStats stats) {
        // Burn
        config.set(BURN_TIME, stats.getBurnTime());
        config.set(OVERRIDE_DEFAULT, stats.isOverrideDefault());
        config.set(EFFICIENCY_MULTIPLIER, stats.getEfficiencyMultiplier());
        // Cook Time
        config.set(FURNACE_COOK_TIME, stats.getFurnaceCookTime());
        config.set(BLAST_FURNACE_COOK_TIME, stats.getBlastFurnaceCookTime());
        config.set(SMOKER_COOK_TIME, stats.getSmokerCookTime());
        config.set(CAMPFIRE_COOK_TIME, stats.getCampfireCookTime());
        config.set(CUSTOM_COOK_TIMES_ENABLED, stats.isCustomCookTimesEnabled());
    }

    @Override
    protected void applyFromDecomposed(IDecomposedConfig config, FuelStats stats) {
        // Burn
        config.get(BURN_TIME).ifPresent(stats::setBurnTime);
        config.get(OVERRIDE_DEFAULT).ifPresent(stats::setOverrideDefault);
        config.get(EFFICIENCY_MULTIPLIER).ifPresent(stats::setEfficiencyMultiplier);
        // Cook Time
        config.get(FURNACE_COOK_TIME).ifPresent(stats::setFurnaceCookTime);
        config.get(BLAST_FURNACE_COOK_TIME).ifPresent(stats::setBlastFurnaceCookTime);
        config.get(SMOKER_COOK_TIME).ifPresent(stats::setSmokerCookTime);
        config.get(CAMPFIRE_COOK_TIME).ifPresent(stats::setCampfireCookTime);
        config.get(CUSTOM_COOK_TIMES_ENABLED).ifPresent(stats::setCustomCookTimesEnabled);
    }

    // ═══════════════════════════════════════════════════════════════
    // VANILLA DEFAULTS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public FuelStats getStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return createDefault();
        }

        // Try parent implementation first (component, customdata, global)
        FuelStats stats = super.getStats(stack);

        // If still default, try vanilla fuel properties
        if (stats.isDefault()) {
            return getVanillaDefaults(stack);
        }

        return stats;
    }

    /**
     * Extract vanilla fuel properties as FuelStats.
     */
    private FuelStats getVanillaDefaults(ItemStack stack) {
        FuelStats stats = new FuelStats();
        stats.setBurnTime(getVanillaBurnTime(stack));
        // Default cook times (vanilla values)
        stats.setFurnaceCookTime(200);
        stats.setBlastFurnaceCookTime(100);
        stats.setSmokerCookTime(100);
        stats.setCampfireCookTime(600);
        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // STATIC COMPATIBILITY API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if an item is a fuel using vanilla fuel registry.
     * IMPORTANT: This method must NOT call stack.getBurnTime() to avoid infinite recursion.
     */
    public static boolean isFuel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            // Check if item has custom fuel stats first
            if (INSTANCE.globalStats.containsKey(stack.getItem())) return true;

            // Check vanilla fuel map directly - DO NOT use stack.getBurnTime()!
            @SuppressWarnings("deprecation")
            boolean vanillaFuel = AbstractFurnaceBlockEntity.getFuel().containsKey(stack.getItem());
            return vanillaFuel;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get burn time for an item from vanilla fuel registry.
     * IMPORTANT: This method must NOT call stack.getBurnTime() to avoid infinite recursion.
     */
    public static int getVanillaBurnTime(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        try {
            @SuppressWarnings("deprecation")
            Integer vanillaBurnTime = AbstractFurnaceBlockEntity.getFuel().get(stack.getItem());
            return vanillaBurnTime != null ? vanillaBurnTime : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Set global stats using item ID string.
     */
    public static void setGlobalStats(String itemId, FuelStats stats) {
        if (itemId == null || stats == null) return;

        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc != null && BuiltInRegistries.ITEM.containsKey(loc)) {
            Item item = BuiltInRegistries.ITEM.get(loc);
            INSTANCE.setGlobalStats(item, stats);
        }
    }

    /**
     * Clear global stats using item ID string.
     */
    public static void clearGlobalStats(String itemId) {
        if (itemId == null) return;

        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc != null && BuiltInRegistries.ITEM.containsKey(loc)) {
            Item item = BuiltInRegistries.ITEM.get(loc);
            INSTANCE.clearGlobalStats(item);
        }
    }

    /**
     * Clear specific stats from an item stack (static version).
     */
    public static void clearItemSpecificStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        INSTANCE.clearSpecificStats(stack);
    }
}
