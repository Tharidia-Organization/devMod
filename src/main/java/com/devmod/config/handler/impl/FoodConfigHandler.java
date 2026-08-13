package com.devmod.config.handler.impl;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.reflect.TypeToken;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.devmod.components.FoodComponents;
import com.devmod.config.component.BooleanComponent;
import com.devmod.config.component.FloatComponent;
import com.devmod.config.component.IntComponent;
import com.devmod.config.handler.AbstractConfigHandler;
import com.devmod.config.handler.DecomposedConfig;
import com.devmod.config.handler.IConfigComponent;
import com.devmod.config.handler.IDecomposedConfig;
import com.devmod.stats.FoodStats;
import com.devmod.util.ItemStackResetUtils;

/**
 * Config handler for food stats.
 * Provides decompose/recompose functionality with typed components.
 */
public class FoodConfigHandler extends AbstractConfigHandler<FoodStats> {

    /** Singleton instance */
    public static final FoodConfigHandler INSTANCE = new FoodConfigHandler();

    // ═══════════════════════════════════════════════════════════════
    // COMPONENT DEFINITIONS
    // ═══════════════════════════════════════════════════════════════

    // Nutrition properties
    public static final IntComponent NUTRITION = new IntComponent(
        "nutrition", "Nutrition", "Nutrition", 0, 0, 20);
    public static final FloatComponent SATURATION = new FloatComponent(
        "saturation", "Saturation", "Nutrition", 0.0f, 0.0f, 2.0f);
    public static final IntComponent CONSUMPTION_TIME = new IntComponent(
        "consumptionTime", "Consumption Time", "Nutrition", 32, 1, 200);
    public static final BooleanComponent CAN_ALWAYS_EAT = new BooleanComponent(
        "canAlwaysEat", "Can Always Eat", "Nutrition", false);

    // Food properties
    public static final BooleanComponent IS_MEAT = new BooleanComponent(
        "isMeat", "Is Meat", "Properties", false);
    public static final BooleanComponent IS_FAST_FOOD = new BooleanComponent(
        "isFastFood", "Is Fast Food", "Properties", false);

    private static final Set<IConfigComponent<?>> ALL_COMPONENTS;

    static {
        ALL_COMPONENTS = new LinkedHashSet<>();
        ALL_COMPONENTS.add(NUTRITION);
        ALL_COMPONENTS.add(SATURATION);
        ALL_COMPONENTS.add(CONSUMPTION_TIME);
        ALL_COMPONENTS.add(CAN_ALWAYS_EAT);
        ALL_COMPONENTS.add(IS_MEAT);
        ALL_COMPONENTS.add(IS_FAST_FOOD);
    }

    public FoodConfigHandler() {
        super("food_configs.json", "FoodModStats");
    }

    // ═══════════════════════════════════════════════════════════════
    // ABSTRACT METHOD IMPLEMENTATIONS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Class<FoodStats> getStatsClass() {
        return FoodStats.class;
    }

    @Override
    @Nullable
    protected DataComponentType<CompoundTag> getComponentType() {
        return FoodComponents.foodStatsComponent();
    }

    @Override
    protected FoodStats fromTag(CompoundTag tag) {
        FoodStats stats = new FoodStats();
        stats.load(tag);
        return stats;
    }

    @Override
    protected Type getMapType() {
        return new TypeToken<Map<String, FoodStats>>(){}.getType();
    }

    @Override
    public FoodStats createDefault() {
        return new FoodStats();
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // Items with food component are food items
        return stack.has(DataComponents.FOOD);
    }

    @Override
    public Set<IConfigComponent<?>> getComponents() {
        return ALL_COMPONENTS;
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public FoodStats validateAndClamp(FoodStats input) {
        if (input == null) return createDefault();

        FoodStats stats = input.copy();
        // Use component clamp for all values
        stats.setNutrition(NUTRITION.clamp(stats.getNutrition()));
        stats.setSaturation(SATURATION.clamp(stats.getSaturation()));
        stats.setConsumptionTime(CONSUMPTION_TIME.clamp(stats.getConsumptionTime()));

        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // DECOMPOSE/RECOMPOSE
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void populateDecomposed(DecomposedConfig config, FoodStats stats) {
        config.set(NUTRITION, stats.getNutrition());
        config.set(SATURATION, stats.getSaturation());
        config.set(CONSUMPTION_TIME, stats.getConsumptionTime());
        config.set(CAN_ALWAYS_EAT, stats.isCanAlwaysEat());
        config.set(IS_MEAT, stats.isMeat());
        config.set(IS_FAST_FOOD, stats.isFastFood());
    }

    @Override
    protected void applyFromDecomposed(IDecomposedConfig config, FoodStats stats) {
        config.get(NUTRITION).ifPresent(stats::setNutrition);
        config.get(SATURATION).ifPresent(stats::setSaturation);
        config.get(CONSUMPTION_TIME).ifPresent(stats::setConsumptionTime);
        config.get(CAN_ALWAYS_EAT).ifPresent(stats::setCanAlwaysEat);
        config.get(IS_MEAT).ifPresent(stats::setMeat);
        config.get(IS_FAST_FOOD).ifPresent(stats::setFastFood);
    }

    // ═══════════════════════════════════════════════════════════════
    // VANILLA DEFAULTS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public FoodStats getStats(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return createDefault();
        }

        // Try parent implementation first (component, customdata, global)
        FoodStats stats = super.getStats(stack);

        // If still default, try vanilla food properties
        if (stats.isDefault() && stack.has(DataComponents.FOOD)) {
            return getVanillaDefaults(stack);
        }

        return stats;
    }

    /**
     * Extract vanilla food properties as FoodStats.
     */
    private FoodStats getVanillaDefaults(ItemStack stack) {
        FoodStats stats = new FoodStats();

        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            int nutrition = food.nutrition();
            stats.setNutrition(nutrition);
            float saturationModifier = 0.0f;
            if (nutrition > 0) {
                saturationModifier = food.saturation() / (nutrition * 2.0f);
            }
            stats.setSaturation(saturationModifier);
            stats.setCanAlwaysEat(food.canAlwaysEat());
            stats.setConsumptionTime(food.eatDurationTicks());
        } else {
            stats.setConsumptionTime(32); // Default for food
        }

        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // STATIC COMPATIBILITY API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if an item has food properties.
     */
    public static boolean isFood(ItemStack stack) {
        return INSTANCE.appliesTo(stack);
    }

    /**
     * Set global stats using item ID string.
     */
    public static void setGlobalStats(String itemId, FoodStats stats) {
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
        ItemStackResetUtils.clearNonEditorComponents(stack);
    }
}
