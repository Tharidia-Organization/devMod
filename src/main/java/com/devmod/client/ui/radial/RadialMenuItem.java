package com.devmod.client.ui.radial;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;

/**
 * Represents a single item in a radial menu category.
 * Wraps a RadialAction with display properties.
 */
public class RadialMenuItem {
    private final String name;
    private final RadialAction action;
    private final String iconEmoji;
    @Nullable
    private final ItemStack iconStack;

    // Custom properties for advanced items
    private boolean visible = true;
    @Nullable
    private BooleanSupplier visibilitySupplier = null;  // Dynamic visibility
    private int customColor = -1;  // -1 means use category color

    public RadialMenuItem(String name, RadialAction action, String iconEmoji, @Nullable ItemStack iconStack) {
        this.name = name;
        this.action = action;
        this.iconEmoji = iconEmoji;
        this.iconStack = iconStack;
    }

    /**
     * Execute the action associated with this item
     */
    public void execute() {
        if (action.isAvailable()) {
            action.execute();
        }
    }

    /**
     * Check if item is a toggle type
     */
    public boolean isToggle() {
        return action.isToggle();
    }

    /**
     * Check if item is currently active (for toggles)
     */
    public boolean isActive() {
        return action.isActive();
    }

    /**
     * Check if item action is available
     */
    public boolean isAvailable() {
        return action.isAvailable();
    }

    /**
     * Check if this item navigates to a subcategory
     */
    public boolean isSubcategoryLink() {
        return action.isSubcategoryAction();
    }

    /**
     * Get the subcategory this item links to (if applicable)
     */
    @Nullable
    public RadialCategory getLinkedSubcategory() {
        return action.getSubcategory();
    }

    // === Getters and Setters ===

    public String getName() {
        return name;
    }

    public RadialAction getAction() {
        return action;
    }

    public String getDescription() {
        return action.getDescription();
    }

    public String getIconEmoji() {
        // Prefer action's emoji if set, otherwise use item's
        String actionEmoji = action.getIconEmoji();
        return actionEmoji != null && !actionEmoji.equals("*") ? actionEmoji : iconEmoji;
    }

    @Nullable
    public ItemStack getIconStack() {
        // Prefer action's icon stack if set, otherwise use item's
        ItemStack actionStack = action.getIconStack();
        return actionStack != null ? actionStack : iconStack;
    }

    /**
     * Check if item is visible. Uses dynamic supplier if set, otherwise static value.
     * Uses action.isVisible() for visibility gating (separate from execution precondition).
     */
    public boolean isVisible() {
        boolean baseVisible = visibilitySupplier != null ? visibilitySupplier.getAsBoolean() : visible;
        return baseVisible && action.isVisible();
    }

    /**
     * Check if item can be executed (precondition would pass).
     * Used for grayed-out rendering - item is visible but not clickable.
     */
    public boolean canExecute() {
        return action.canExecute();
    }

    /**
     * Set static visibility (evaluated once).
     */
    public RadialMenuItem setVisible(boolean visible) {
        this.visible = visible;
        this.visibilitySupplier = null;  // Clear dynamic supplier
        return this;
    }

    /**
     * Set dynamic visibility supplier (evaluated on each access).
     * Use this for items whose visibility depends on runtime state (e.g., held item).
     */
    public RadialMenuItem setVisibilitySupplier(BooleanSupplier supplier) {
        this.visibilitySupplier = supplier;
        return this;
    }

    public int getCustomColor() {
        return customColor;
    }

    public RadialMenuItem setCustomColor(int color) {
        this.customColor = color;
        return this;
    }

    public boolean hasCustomColor() {
        return customColor != -1;
    }

    // === Static factory methods ===

    /**
     * Create a toggle item
     */
    public static RadialMenuItem toggle(String name, String emoji,
                                         java.util.function.BooleanSupplier getter,
                                         java.util.function.Consumer<Boolean> setter,
                                         String description) {
        return new RadialMenuItem(name,
            RadialAction.toggle(name, description, emoji, getter, setter),
            emoji, null);
    }

    /**
     * Create a toggle item with ItemStack icon
     */
    public static RadialMenuItem toggle(String name, String emoji, ItemStack icon,
                                         java.util.function.BooleanSupplier getter,
                                         java.util.function.Consumer<Boolean> setter,
                                         String description) {
        return new RadialMenuItem(name,
            RadialAction.toggle(name, description, emoji, icon, getter, setter),
            emoji, icon);
    }

    /**
     * Create an action item (one-shot, not toggle)
     * @deprecated Use {@link #registry(String)} with ActionRegistry instead.
     *             Package-private to prevent new external usages.
     */
    @Deprecated
    static RadialMenuItem action(String name, String emoji, Runnable action, String description) {
        return new RadialMenuItem(name,
            RadialAction.custom(name, description, emoji, action),
            emoji, null);
    }

    /**
     * Create an action item with ItemStack icon
     * @deprecated Use {@link #registry(String)} with ActionRegistry instead.
     *             Package-private to prevent new external usages.
     */
    @Deprecated
    static RadialMenuItem action(String name, String emoji, ItemStack icon,
                                         Runnable action, String description) {
        return new RadialMenuItem(name,
            RadialAction.custom(name, description, emoji, icon, action),
            emoji, icon);
    }

    /**
     * Create a command item
     * @deprecated Use {@link #registry(String)} with ActionRegistry instead.
     *             Package-private to prevent new external usages.
     */
    @Deprecated
    static RadialMenuItem command(String name, String emoji, String command, String description) {
        return new RadialMenuItem(name,
            RadialAction.command(name, description, emoji, command),
            emoji, null);
    }

    /**
     * Create a command item with ItemStack icon
     * @deprecated Use {@link #registry(String)} with ActionRegistry instead.
     *             Package-private to prevent new external usages.
     */
    @Deprecated
    static RadialMenuItem command(String name, String emoji, ItemStack icon, String command, String description) {
        return new RadialMenuItem(name,
            RadialAction.command(name, description, emoji, icon, command),
            emoji, icon);
    }

    /**
     * Create a screen-opening item
     */
    public static RadialMenuItem screen(String name, String emoji,
                                         java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screenFactory,
                                         String description) {
        return new RadialMenuItem(name,
            RadialAction.screen(name, description, emoji, screenFactory),
            emoji, null);
    }

    /**
     * Create a screen-opening item with ItemStack icon
     */
    public static RadialMenuItem screen(String name, String emoji, ItemStack icon,
                                         java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screenFactory,
                                         String description) {
        return new RadialMenuItem(name,
            RadialAction.screen(name, description, emoji, icon, screenFactory),
            emoji, icon);
    }

    /**
     * Create an item backed by the ActionRegistry.
     */
    public static RadialMenuItem registry(String actionId) {
        RadialAction action = RadialAction.registry(actionId);
        String name = action.getLabel().getString();
        ItemStack icon = action.getIconStack();
        return new RadialMenuItem(name, action, action.getIconEmoji(), icon);
    }
}
