package com.devmod.client.ui.radial.config;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Data records for JSON deserialization of radial menu definitions.
 * Immutable records matching the JSON schema structure.
 *
 * <p>The structure mirrors the three-level hierarchy:
 * <ul>
 *   <li>MacroCategory (6 top-level: ANALYZE, TELEMETRY, COMBAT, ARENA, PLAY, TOOLS)</li>
 *   <li>Category (24 categories, each belonging to a macro)</li>
 *   <li>MenuItem (245+ items, each belonging to a category)</li>
 * </ul>
 * </p>
 *
 * <p>Example JSON:
 * <pre>
 * {
 *   "schemaVersion": 1,
 *   "macroCategories": [
 *     {
 *       "id": "ANALYZE",
 *       "categories": [
 *         {
 *           "id": "debug",
 *           "name": "Debug",
 *           "colorToken": "ANALYZE_DEBUG",
 *           "iconItem": "minecraft:ender_eye",
 *           "items": [
 *             { "actionId": "devmod.debug.overlay.toggle" }
 *           ]
 *         }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 * </p>
 */
public final class RadialMenuDefinitionConfig {

    private RadialMenuDefinitionConfig() {} // Namespace class

    /** Current schema version for compatibility checking. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Root configuration containing all macro categories.
     *
     * @param schemaVersion Schema version for compatibility checking
     * @param macroCategories List of macro category definitions
     */
    public record RootConfig(
        int schemaVersion,
        boolean preserveOrdering,
        List<MacroCategoryConfig> macroCategories
    ) {
        public RootConfig {
            if (macroCategories == null) {
                macroCategories = List.of();
            }
        }
    }

    /**
     * Macro category configuration (one of the 6 top-level categories).
     *
     * @param id Macro category ID matching MacroCategory enum (ANALYZE, TELEMETRY, etc.)
     * @param categories List of categories within this macro
     */
    public record MacroCategoryConfig(
        String id,
        List<CategoryConfig> categories
    ) {
        public MacroCategoryConfig {
            if (categories == null) {
                categories = List.of();
            }
        }
    }

    /**
     * Category configuration within a macro category.
     *
     * @param id Unique category ID (lowercase with underscores)
     * @param name Display name for the category
     * @param color Optional hex color (e.g., "#FF4488FF")
     * @param colorToken Optional reference to DesignTokens.Radial constant
     * @param icon Optional emoji icon
     * @param iconItem Optional Minecraft item ID for icon (e.g., "minecraft:ender_eye")
     * @param items List of menu items in this category
     * @param subcategories Optional nested subcategories
     */
    public record CategoryConfig(
        String id,
        String name,
        @Nullable String color,
        @Nullable String colorToken,
        @Nullable String icon,
        @Nullable String iconItem,
        List<MenuItemConfig> items,
        @Nullable List<SubcategoryConfig> subcategories
    ) {
        public CategoryConfig {
            if (items == null) {
                items = List.of();
            }
        }
    }

    /**
     * Subcategory configuration (nested within a category).
     *
     * @param id Unique subcategory ID
     * @param name Display name
     * @param color Optional hex color
     * @param colorToken Optional reference to DesignTokens.Radial constant
     * @param icon Optional emoji icon
     * @param iconItem Optional Minecraft item ID for icon
     * @param items List of menu items in this subcategory
     * @param subcategories Optional deeply nested subcategories (max 3 levels)
     */
    public record SubcategoryConfig(
        String id,
        String name,
        @Nullable String color,
        @Nullable String colorToken,
        @Nullable String icon,
        @Nullable String iconItem,
        List<MenuItemConfig> items,
        @Nullable List<SubcategoryConfig> subcategories
    ) {
        public SubcategoryConfig {
            if (items == null) {
                items = List.of();
            }
        }
    }

    /**
     * Menu item configuration.
     *
     * @param actionId Action ID from ActionIds class (e.g., "devmod.debug.overlay.toggle")
     * @param visibilitySupplier Optional ID of visibility supplier from VisibilitySupplierRegistry
     * @param customColor Optional custom color override (hex or token)
     */
    public record MenuItemConfig(
        String actionId,
        @Nullable String visibilitySupplier,
        @Nullable String customColor
    ) {}

    /**
     * Validation result for config loading.
     *
     * @param valid Whether the config is valid
     * @param errors List of error messages (if any)
     * @param warnings List of warning messages (if any)
     */
    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), List.of());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors, List.of());
        }

        public static ValidationResult failure(String error) {
            return new ValidationResult(false, List.of(error), List.of());
        }

        public static ValidationResult withWarnings(List<String> warnings) {
            return new ValidationResult(true, List.of(), warnings);
        }
    }
}
