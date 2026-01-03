package com.devmod.client.ui.radial;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.radial.model.MacroCategory;

/**
 * Fluent builder API for programmatic radial menu registration.
 * Allows mods/plugins to add custom categories and items at runtime.
 *
 * <p>Usage example:
 * <pre>
 * RadialMenuBuilder.forMacro(MacroCategory.TOOLS)
 *     .category("my_tools")
 *         .name("My Tools")
 *         .color(DesignTokens.Radial.ANALYZE_DEBUG)
 *         .iconItem(Items.DIAMOND_PICKAXE)
 *         .item("mymod.action.do_thing")
 *         .item("mymod.action.other_thing", () -&gt; isConditionMet())
 *         .subcategory("advanced")
 *             .name("Advanced")
 *             .item("mymod.action.advanced_thing")
 *             .endSubcategory()
 *         .endCategory()
 *     .register();
 * </pre>
 * </p>
 *
 * <p>For mods, call this during FMLClientSetupEvent after RadialMenuRuntimeRegistry is initialized.</p>
 */
public final class RadialMenuBuilder {

    private final MacroCategory macro;
    private final List<CategoryBuilder> categories = new ArrayList<>();

    private RadialMenuBuilder(MacroCategory macro) {
        this.macro = Objects.requireNonNull(macro, "Macro category cannot be null");
    }

    /**
     * Start building for a specific macro category.
     *
     * @param macro The macro category to add categories to
     * @return A new builder instance
     */
    public static RadialMenuBuilder forMacro(MacroCategory macro) {
        return new RadialMenuBuilder(macro);
    }

    /**
     * Start a new category definition.
     *
     * @param id Unique category ID
     * @return The category builder
     */
    public CategoryBuilder category(String id) {
        CategoryBuilder cat = new CategoryBuilder(this, id);
        categories.add(cat);
        return cat;
    }

    /**
     * Register all built categories with the runtime registry.
     * Call this after defining all categories.
     */
    public void register() {
        for (CategoryBuilder cat : categories) {
            RadialCategory built = cat.build();
            RadialMenuRuntimeRegistry.addCategory(macro, built);
        }
    }

    /**
     * Get the target macro category.
     *
     * @return The macro category
     */
    public MacroCategory getMacro() {
        return macro;
    }

    // ========================================================================
    // Category Builder
    // ========================================================================

    /**
     * Builder for a single category.
     */
    public static final class CategoryBuilder {
        private final RadialMenuBuilder parent;
        private final String id;
        private String name;
        private int color = DesignTokens.Text.WHITE;
        private String icon = "";
        @Nullable private ItemStack iconStack;
        private final List<ItemDef> items = new ArrayList<>();
        private final List<SubcategoryBuilder> subcategories = new ArrayList<>();

        CategoryBuilder(RadialMenuBuilder parent, String id) {
            this.parent = parent;
            this.id = Objects.requireNonNull(id, "Category ID cannot be null");
            this.name = id; // Default to ID
        }

        /**
         * Set the display name.
         *
         * @param name Display name
         * @return this builder
         */
        public CategoryBuilder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the color (ARGB format).
         *
         * @param color ARGB color value
         * @return this builder
         */
        public CategoryBuilder color(int color) {
            this.color = color;
            return this;
        }

        /**
         * Set the emoji icon.
         *
         * @param icon Emoji icon
         * @return this builder
         */
        public CategoryBuilder icon(String icon) {
            this.icon = icon;
            return this;
        }

        /**
         * Set the icon from a Minecraft item.
         *
         * @param item The item to use as icon (must not be null)
         * @return this builder
         * @throws NullPointerException if item is null
         */
        public CategoryBuilder iconItem(Item item) {
            this.iconStack = new ItemStack(Objects.requireNonNull(item, "item cannot be null"));
            return this;
        }

        /**
         * Set the icon from an ItemStack.
         *
         * @param stack The item stack to use as icon
         * @return this builder
         */
        public CategoryBuilder iconStack(ItemStack stack) {
            this.iconStack = stack;
            return this;
        }

        /**
         * Add an item by action ID.
         *
         * @param actionId The action ID
         * @return this builder
         */
        public CategoryBuilder item(String actionId) {
            items.add(new ItemDef(actionId, null, -1));
            return this;
        }

        /**
         * Add an item with visibility supplier.
         *
         * @param actionId The action ID
         * @param visibility Supplier that returns true when item should be visible
         * @return this builder
         */
        public CategoryBuilder item(String actionId, BooleanSupplier visibility) {
            items.add(new ItemDef(actionId, visibility, -1));
            return this;
        }

        /**
         * Add an item with visibility and custom color.
         *
         * @param actionId The action ID
         * @param visibility Supplier that returns true when item should be visible
         * @param customColor Custom ARGB color
         * @return this builder
         */
        public CategoryBuilder item(String actionId, BooleanSupplier visibility, int customColor) {
            items.add(new ItemDef(actionId, visibility, customColor));
            return this;
        }

        /**
         * Start a subcategory definition.
         *
         * @param id Unique subcategory ID
         * @return The subcategory builder
         */
        public SubcategoryBuilder subcategory(String id) {
            SubcategoryBuilder sub = new SubcategoryBuilder(this, null, id);
            subcategories.add(sub);
            return sub;
        }

        /**
         * Finish this category and return to parent builder.
         *
         * @return The parent builder
         */
        public RadialMenuBuilder endCategory() {
            return parent;
        }

        /**
         * Build the category.
         */
        RadialCategory build() {
            RadialCategory.Builder builder = RadialCategory.builder(id)
                .name(name)
                .color(color)
                .icon(icon);

            if (iconStack != null) {
                builder.iconStack(iconStack);
            }

            for (ItemDef itemDef : items) {
                RadialMenuItem item = RadialMenuItem.registry(itemDef.actionId);
                if (item != null) {
                    if (itemDef.visibility != null) {
                        item.setVisibilitySupplier(itemDef.visibility);
                    }
                    if (itemDef.customColor >= 0) {
                        item.setCustomColor(itemDef.customColor);
                    }
                    builder.item(item);
                }
            }

            RadialCategory category = builder.build();

            for (SubcategoryBuilder subDef : subcategories) {
                subDef.buildInto(category);
            }

            return category;
        }
    }

    // ========================================================================
    // Subcategory Builder
    // ========================================================================

    /**
     * Builder for a subcategory.
     */
    public static final class SubcategoryBuilder {
        @Nullable private final CategoryBuilder parentCat;
        @Nullable private final SubcategoryBuilder parentSub;
        private final String id;
        private String name;
        private int color = DesignTokens.Text.WHITE;
        @Nullable private ItemStack iconStack;
        private final List<ItemDef> items = new ArrayList<>();
        private final List<SubcategoryBuilder> subcategories = new ArrayList<>();

        SubcategoryBuilder(@Nullable CategoryBuilder parentCat, @Nullable SubcategoryBuilder parentSub, String id) {
            this.parentCat = parentCat;
            this.parentSub = parentSub;
            this.id = Objects.requireNonNull(id, "Subcategory ID cannot be null");
            this.name = id; // Default to ID
        }

        /**
         * Set the display name.
         *
         * @param name Display name
         * @return this builder
         */
        public SubcategoryBuilder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the color (ARGB format).
         *
         * @param color ARGB color value
         * @return this builder
         */
        public SubcategoryBuilder color(int color) {
            this.color = color;
            return this;
        }

        /**
         * Set the icon from a Minecraft item.
         *
         * @param item The item to use as icon (must not be null)
         * @return this builder
         * @throws NullPointerException if item is null
         */
        public SubcategoryBuilder iconItem(Item item) {
            this.iconStack = new ItemStack(Objects.requireNonNull(item, "item cannot be null"));
            return this;
        }

        /**
         * Add an item by action ID.
         *
         * @param actionId The action ID
         * @return this builder
         */
        public SubcategoryBuilder item(String actionId) {
            items.add(new ItemDef(actionId, null, -1));
            return this;
        }

        /**
         * Add an item with visibility supplier.
         *
         * @param actionId The action ID
         * @param visibility Supplier that returns true when item should be visible
         * @return this builder
         */
        public SubcategoryBuilder item(String actionId, BooleanSupplier visibility) {
            items.add(new ItemDef(actionId, visibility, -1));
            return this;
        }

        /**
         * Start a nested subcategory definition.
         *
         * @param id Unique subcategory ID
         * @return The nested subcategory builder
         */
        public SubcategoryBuilder subcategory(String id) {
            SubcategoryBuilder sub = new SubcategoryBuilder(null, this, id);
            subcategories.add(sub);
            return sub;
        }

        /**
         * End subcategory definition and return to parent category.
         *
         * @return The parent category builder
         */
        public CategoryBuilder endSubcategory() {
            if (parentCat != null) {
                return parentCat;
            }
            throw new IllegalStateException("endSubcategory() called on nested subcategory - use endNestedSubcategory()");
        }

        /**
         * End nested subcategory definition and return to parent subcategory.
         *
         * @return The parent subcategory builder
         */
        public SubcategoryBuilder endNestedSubcategory() {
            if (parentSub != null) {
                return parentSub;
            }
            throw new IllegalStateException("endNestedSubcategory() called on top-level subcategory - use endSubcategory()");
        }

        /**
         * Build this subcategory into the parent category.
         */
        void buildInto(RadialCategory parent) {
            RadialCategory sub = iconStack != null
                ? parent.addSubcategory(id, name, color, iconStack)
                : parent.addSubcategory(id, name, color, "");

            for (ItemDef itemDef : items) {
                RadialMenuItem item = RadialMenuItem.registry(itemDef.actionId);
                if (item != null) {
                    if (itemDef.visibility != null) {
                        item.setVisibilitySupplier(itemDef.visibility);
                    }
                    if (itemDef.customColor >= 0) {
                        item.setCustomColor(itemDef.customColor);
                    }
                    sub.addItem(item);
                }
            }

            for (SubcategoryBuilder nested : subcategories) {
                nested.buildInto(sub);
            }
        }
    }

    // ========================================================================
    // Internal types
    // ========================================================================

    /**
     * Internal item definition holder.
     */
    private record ItemDef(
        String actionId,
        @Nullable BooleanSupplier visibility,
        int customColor
    ) {}
}
