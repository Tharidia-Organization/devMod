package com.devmod.endurance.nutrition;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * Nutritional categories tracked by the Easy-Diet integration.
 *
 * <p>These mirror the categories from Easy-Diet for consistency,
 * but are defined here to avoid requiring Easy-Diet at runtime
 * for code that only needs the category definitions.
 *
 * <p><b>Note:</b> Colors are defined inline to avoid importing client code
 * (DesignTokens) into a common/server-side class.
 *
 * @author DevMod Team
 */
public enum NutritionCategory {

    // Color constants (ARGB) - mirrored from DesignTokens.Nutrition for server-side access
    // Keep in sync with: com.devmod.client.ui.editor.core.DesignTokens.Nutrition

    /** Cereals, bread, pasta, grains. */
    GRAIN(0, "Grain", "grain", 0xFFD4AF37),

    /** Meat, fish, eggs, legumes. */
    PROTEIN(1, "Protein", "protein", 0xFFFF6B6B),

    /** Vegetables, greens, roots. */
    VEGETABLE(2, "Vegetable", "vegetable", 0xFF51CF66),

    /** Fruits, berries. */
    FRUIT(3, "Fruit", "fruit", 0xFFFFA500),

    /** Sweets, honey, sugar. */
    SUGAR(4, "Sugar", "sugar", 0xFFFFB6C1),

    /** Water, drinks, soups. */
    WATER(5, "Water", "water", 0xFF4A90E2);

    /** Pre-computed array of all values for fast iteration. */
    private static final NutritionCategory[] VALUES = values();

    /** Immutable list view for safe iteration. */
    public static final List<NutritionCategory> ALL = List.of(VALUES);

    /** Number of categories. */
    public static final int COUNT = VALUES.length;

    /** Ordinal index (0-5). */
    public final int index;

    /** Display name for UI. */
    @Nonnull
    public final String displayName;

    /** Lowercase key for serialization. */
    @Nonnull
    public final String key;

    /** ARGB color for UI rendering. */
    public final int color;

    NutritionCategory(int index, @Nonnull String displayName, @Nonnull String key, int color) {
        this.index = index;
        this.displayName = displayName;
        this.key = key;
        this.color = color;
    }

    /**
     * Get category by index.
     *
     * @param index ordinal (0-5)
     * @return the category, or null if out of range
     */
    public static NutritionCategory byIndex(int index) {
        if (index < 0 || index >= COUNT) {
            return null;
        }
        return VALUES[index];
    }

    /**
     * Get category by key.
     *
     * @param key lowercase key
     * @return the category, or null if not found
     */
    public static NutritionCategory byKey(String key) {
        if (key == null) {
            return null;
        }
        for (NutritionCategory cat : VALUES) {
            if (cat.key.equals(key)) {
                return cat;
            }
        }
        return null;
    }

    /**
     * Extract RGB components from color.
     *
     * @return array of [r, g, b] normalized to 0.0-1.0
     */
    public float[] getRgbComponents() {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return new float[] { r / 255f, g / 255f, b / 255f };
    }

    /**
     * Get alpha component from color.
     *
     * @return alpha value 0-255
     */
    public int getAlpha() {
        return (color >> 24) & 0xFF;
    }
}
