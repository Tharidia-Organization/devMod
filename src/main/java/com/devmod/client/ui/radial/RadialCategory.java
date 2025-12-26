package com.devmod.client.ui.radial;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
public class RadialCategory {
    private final String id;
    private final String name;
    private final int color;
    private final String icon;
    @Nullable
    private final ItemStack iconStack;
    private final List<RadialMenuItem> items = new ArrayList<>();
    @Nullable
    private RadialCategory parent;

    public RadialCategory(String id, String name, int color, String icon) {
        this(id, name, color, icon, null);
    }

    public RadialCategory(String id, String name, int color, String icon, @Nullable ItemStack iconStack) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.icon = icon;
        this.iconStack = iconStack;
    }

    /**
     * Add an item to this category
     */
    public RadialCategory addItem(RadialMenuItem item) {
        items.add(item);
        return this;
    }

    /**
     * Add multiple items to this category
     */
    public RadialCategory addItems(RadialMenuItem... newItems) {
        for (RadialMenuItem item : newItems) {
            items.add(item);
        }
        return this;
    }

    /**
     * Create and add a subcategory
     */
    public RadialCategory addSubcategory(String subId, String subName, int subColor, String subIcon) {
        return addSubcategory(subId, subName, subColor, subIcon, null);
    }

    /**
     * Create and add a subcategory with an ItemStack icon.
     */
    public RadialCategory addSubcategory(String subId, String subName, int subColor, ItemStack iconStack) {
        return addSubcategory(subId, subName, subColor, "", iconStack);
    }

    private RadialCategory addSubcategory(String subId, String subName, int subColor, String subIcon,
                                          @Nullable ItemStack iconStack) {
        RadialCategory subcategory = new RadialCategory(subId, subName, subColor, subIcon, iconStack);
        subcategory.parent = this;

        // Add a menu item that navigates to the subcategory
        items.add(new RadialMenuItem(
            subName,
            RadialAction.subcategory(subName, "Open " + subName + " submenu", subIcon, subcategory),
            subIcon,
            iconStack
        ));

        return subcategory;
    }

    /**
     * Count how many toggleable items are currently active
     */
    public int countActiveItems() {
        int count = 0;
        for (RadialMenuItem item : items) {
            if (item.getAction().isToggle() && item.getAction().isActive()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get total item count
     */
    public int getItemCount() {
        return items.size();
    }

    /**
     * Get only visible items (filtered dynamically).
     * Use this for rendering and selection logic.
     */
    public List<RadialMenuItem> getVisibleItems() {
        List<RadialMenuItem> visible = new ArrayList<>();
        for (RadialMenuItem item : items) {
            if (item.isVisible()) {
                visible.add(item);
            }
        }
        return visible;
    }

    /**
     * Get visible item count
     */
    public int getVisibleItemCount() {
        int count = 0;
        for (RadialMenuItem item : items) {
            if (item.isVisible()) count++;
        }
        return count;
    }

    /**
     * Check if this category has a parent (is a subcategory)
     */
    public boolean hasParent() {
        return parent != null;
    }

    // === Getters ===

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
    }

    @Nullable
    public ItemStack getIconStack() {
        return iconStack;
    }

    public List<RadialMenuItem> getItems() {
        return items;
    }

    @Nullable
    public RadialCategory getParent() {
        return parent;
    }

    /**
     * Builder for fluent category construction
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String name;
        private int color = 0xFFFFFFFF;
        private String icon = "*";
        private ItemStack iconStack;
        private final List<RadialMenuItem> items = new ArrayList<>();

        public Builder(String id) {
            this.id = id;
            this.name = id;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder color(int color) {
            this.color = color;
            return this;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder iconStack(ItemStack iconStack) {
            this.iconStack = iconStack;
            return this;
        }

        public Builder item(RadialMenuItem item) {
            this.items.add(item);
            return this;
        }

        public Builder item(String name, RadialAction action, String emoji) {
            this.items.add(new RadialMenuItem(name, action, emoji, null));
            return this;
        }

        public Builder item(String name, RadialAction action, String emoji, ItemStack icon) {
            this.items.add(new RadialMenuItem(name, action, emoji, icon));
            return this;
        }

        public RadialCategory build() {
            RadialCategory category = new RadialCategory(id, name, color, icon, iconStack);
            for (RadialMenuItem item : items) {
                category.addItem(item);
            }
            return category;
        }
    }
}
