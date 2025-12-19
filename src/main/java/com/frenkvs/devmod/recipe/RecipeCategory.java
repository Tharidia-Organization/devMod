package com.frenkvs.devmod.recipe;

/**
 * Recipe categories for the recipe book.
 * Aligns with Minecraft's CraftingBookCategory and CookingBookCategory.
 */
public enum RecipeCategory {
    EQUIPMENT("equipment"),
    BUILDING("building"),
    MISC("misc"),
    REDSTONE("redstone"),
    FOOD("food"),
    BLOCKS("blocks");

    private final String id;

    RecipeCategory(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static RecipeCategory fromString(String s) {
        if (s == null || s.isEmpty()) return MISC;
        for (RecipeCategory cat : values()) {
            if (cat.id.equalsIgnoreCase(s)) return cat;
        }
        return MISC;
    }

    /**
     * Get categories valid for crafting recipes.
     */
    public static RecipeCategory[] craftingCategories() {
        return new RecipeCategory[]{EQUIPMENT, BUILDING, MISC, REDSTONE};
    }

    /**
     * Get categories valid for cooking recipes.
     */
    public static RecipeCategory[] cookingCategories() {
        return new RecipeCategory[]{FOOD, BLOCKS, MISC};
    }
}
