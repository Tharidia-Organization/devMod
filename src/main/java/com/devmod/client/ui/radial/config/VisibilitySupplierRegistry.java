package com.devmod.client.ui.radial.config;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.crafting.RecipeType;

import com.devmod.client.ui.editor.WeaponTypeDetector;
import com.devmod.config.ArmorConfigManager;

/**
 * Registry for visibility suppliers that can be referenced by ID in JSON config.
 * These suppliers determine whether a menu item should be visible based on runtime state.
 *
 * <p>Visibility suppliers are evaluated each frame to provide dynamic menu item visibility
 * based on context like held item type, player state, etc.</p>
 *
 * <p>Usage in JSON config:
 * <pre>
 * { "actionId": "devmod.ui.item_editor.open_weapon", "visibilitySupplier": "isWeaponItem" }
 * </pre>
 * </p>
 *
 * <p>Mods can register custom suppliers:
 * <pre>
 * VisibilitySupplierRegistry.register("hasMyModItem", () -> hasMySpecialItem());
 * </pre>
 * </p>
 */
public final class VisibilitySupplierRegistry {

    private static final Map<String, BooleanSupplier> SUPPLIERS = new ConcurrentHashMap<>();
    private static volatile boolean defaultsRegistered = false;

    private VisibilitySupplierRegistry() {} // Utility class

    /**
     * Register a visibility supplier with a unique ID.
     * Call during mod initialization (e.g., in FMLClientSetupEvent).
     *
     * @param id Unique identifier for this supplier (used in JSON config)
     * @param supplier The supplier that returns true when the menu item should be visible
     */
    public static void register(String id, BooleanSupplier supplier) {
        Objects.requireNonNull(id, "Visibility supplier ID cannot be null");
        Objects.requireNonNull(supplier, "Visibility supplier cannot be null");
        SUPPLIERS.put(id, supplier);
    }

    /**
     * Get a registered visibility supplier by ID.
     *
     * @param id The supplier ID
     * @return The supplier, or null if not found
     */
    @Nullable
    public static BooleanSupplier get(String id) {
        if (id == null) return null;
        return SUPPLIERS.get(id);
    }

    /**
     * Check if a supplier with the given ID exists.
     *
     * @param id The supplier ID to check
     * @return true if a supplier with this ID is registered
     */
    public static boolean exists(String id) {
        return id != null && SUPPLIERS.containsKey(id);
    }

    /**
     * Get all registered supplier IDs.
     *
     * @return Set of all registered IDs (unmodifiable)
     */
    public static Set<String> getRegisteredIds() {
        return Set.copyOf(SUPPLIERS.keySet());
    }

    /**
     * Register all built-in visibility suppliers.
     * Called during client mod initialization.
     * Safe to call multiple times (idempotent).
     */
    public static void registerDefaults() {
        if (defaultsRegistered) return;
        synchronized (VisibilitySupplierRegistry.class) {
            if (defaultsRegistered) return;

            // Item type detection suppliers
            register("isWeaponItem", () -> isWeaponItem(getHeldItem()));
            register("isArmorItem", () -> isArmorItem(getHeldItem()));
            register("isShieldItem", () -> isShieldItem(getHeldItem()));
            register("isGeneralItem", () -> isGeneralItem(getHeldItem()));
            register("isFoodItem", () -> isFoodItem(getHeldItem()));
            register("isFuelItem", () -> isFuelItem(getHeldItem()));
            register("isUsableItem", () -> isUsableItem(getHeldItem()));

            // Combination suppliers
            register("hasHeldItem", () -> !getHeldItem().isEmpty());
            register("isRangedWeapon", () -> isRangedWeapon(getHeldItem()));
            register("isMeleeWeapon", () -> isMeleeWeapon(getHeldItem()));

            // Always visible/hidden (useful for testing or conditional items)
            register("always", () -> true);
            register("never", () -> false);

            defaultsRegistered = true;
        }
    }

    // ========================================================================
    // Helper methods - Item detection logic (migrated from RadialMenuRegistry)
    // ========================================================================

    /**
     * Get the item currently held in the player's main hand.
     * Returns EMPTY if no player or empty hand.
     */
    @Nonnull
    private static ItemStack getHeldItem() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return ItemStack.EMPTY;
        ItemStack held = player.getMainHandItem();
        return held.isEmpty() ? ItemStack.EMPTY : held.copy();
    }

    /**
     * Check if the item is a melee or ranged weapon.
     */
    private static boolean isWeaponItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var detection = WeaponTypeDetector.detectDetailed(stack);
        return WeaponTypeDetector.isMelee(detection.type())
            || WeaponTypeDetector.isRanged(detection.type());
    }

    /**
     * Check if the item is armor.
     */
    private static boolean isArmorItem(ItemStack stack) {
        return stack != null && ArmorConfigManager.isArmor(stack);
    }

    /**
     * Check if the item is a shield.
     */
    private static boolean isShieldItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var detection = WeaponTypeDetector.detectDetailed(stack);
        return detection.type() == WeaponTypeDetector.WeaponType.SHIELD;
    }

    /**
     * Check if the item is a "general" item (not weapon, armor, or shield).
     */
    private static boolean isGeneralItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var detection = WeaponTypeDetector.detectDetailed(stack);
        return !isWeaponItem(stack)
            && !isArmorItem(stack)
            && detection.type() != WeaponTypeDetector.WeaponType.SHIELD;
    }

    /**
     * Check if the item is food.
     */
    private static boolean isFoodItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        @Nonnull DataComponentType<?> foodComponent = Objects.requireNonNull(
            DataComponents.FOOD, "food component");
        return stack.getItem().components().has(foodComponent);
    }

    /**
     * Check if the item is fuel (has burn time).
     */
    private static boolean isFuelItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    /**
     * Check if the item is "usable" (throwable or has use duration).
     */
    private static boolean isUsableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var item = stack.getItem();

        // Throwable items
        if (item instanceof SnowballItem ||
            item instanceof EggItem ||
            item instanceof EnderpearlItem ||
            item instanceof ThrowablePotionItem ||
            item instanceof InstrumentItem) {
            return true;
        }

        // Items with use duration (potions, etc. - food handled separately)
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        return item.getUseDuration(stack, player) > 0 && !isFoodItem(stack);
    }

    /**
     * Check if the item is a ranged weapon specifically.
     */
    private static boolean isRangedWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var detection = WeaponTypeDetector.detectDetailed(stack);
        return WeaponTypeDetector.isRanged(detection.type());
    }

    /**
     * Check if the item is a melee weapon specifically.
     */
    private static boolean isMeleeWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var detection = WeaponTypeDetector.detectDetailed(stack);
        return WeaponTypeDetector.isMelee(detection.type());
    }

    /**
     * Reset the registry (for testing purposes only).
     */
    static void reset() {
        SUPPLIERS.clear();
        defaultsRegistered = false;
    }
}
