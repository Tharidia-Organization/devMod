package com.devmod.client.ui.radial.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.devmod.actions.ActionRegistry;
import com.devmod.client.ui.radial.model.MacroCategory;
import com.devmod.client.ui.radial.config.RadialMenuDefinitionConfig.*;

/**
 * Validates radial menu configuration at load time.
 * Checks for schema version, valid references, and structural issues.
 */
public final class RadialMenuConfigValidator {

    private RadialMenuConfigValidator() {} // Utility class

    /**
     * Validate a loaded configuration.
     *
     * @param config The configuration to validate
     * @return Validation result with errors and warnings
     */
    public static ValidationResult validate(RootConfig config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (config == null) {
            return ValidationResult.failure("Configuration is null");
        }

        // Schema version check
        if (config.schemaVersion() != RadialMenuDefinitionConfig.CURRENT_SCHEMA_VERSION) {
            errors.add("Unsupported schema version: " + config.schemaVersion()
                + " (expected: " + RadialMenuDefinitionConfig.CURRENT_SCHEMA_VERSION + ")");
        }

        // Track unique IDs
        Set<String> categoryIds = new HashSet<>();
        Set<String> subcategoryIds = new HashSet<>();

        // Validate each macro category
        if (config.macroCategories() != null) {
            for (MacroCategoryConfig macroCfg : config.macroCategories()) {
                validateMacroCategory(macroCfg, categoryIds, subcategoryIds, errors, warnings);
            }
        } else {
            warnings.add("No macro categories defined");
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private static void validateMacroCategory(
            MacroCategoryConfig macro,
            Set<String> categoryIds,
            Set<String> subcategoryIds,
            List<String> errors,
            List<String> warnings) {

        // Validate macro ID
        if (macro.id() == null || macro.id().isEmpty()) {
            errors.add("Macro category has no ID");
            return;
        }

        try {
            MacroCategory.valueOf(macro.id());
        } catch (IllegalArgumentException e) {
            errors.add("Unknown macro category: " + macro.id()
                + " (valid: ANALYZE, TELEMETRY, COMBAT, ARENA, PLAY, TOOLS)");
        }

        // Validate categories
        if (macro.categories() != null) {
            for (CategoryConfig cat : macro.categories()) {
                validateCategory(cat, macro.id(), categoryIds, subcategoryIds, errors, warnings);
            }
        }
    }

    private static void validateCategory(
            CategoryConfig cat,
            String macroId,
            Set<String> categoryIds,
            Set<String> subcategoryIds,
            List<String> errors,
            List<String> warnings) {

        String context = "Category in " + macroId;

        // Validate ID
        if (cat.id() == null || cat.id().isEmpty()) {
            errors.add(context + " has no ID");
            return;
        }

        // Check for duplicates
        if (!categoryIds.add(cat.id())) {
            errors.add("Duplicate category ID: " + cat.id());
        }

        // Validate name
        if (cat.name() == null || cat.name().isEmpty()) {
            warnings.add("Category " + cat.id() + " has no name (will use ID)");
        }

        // Validate color
        if (cat.color() == null && cat.colorToken() == null) {
            warnings.add("Category " + cat.id() + " has no color (will use default)");
        } else if (cat.colorToken() != null && !ColorTokenResolver.isValidToken(cat.colorToken())) {
            warnings.add("Category " + cat.id() + " has unknown color token: " + cat.colorToken());
        }

        // Validate items
        if (cat.items() != null) {
            for (MenuItemConfig item : cat.items()) {
                validateMenuItem(item, "Category " + cat.id(), errors, warnings);
            }
        }

        // Validate subcategories
        if (cat.subcategories() != null) {
            for (SubcategoryConfig sub : cat.subcategories()) {
                validateSubcategory(sub, cat.id(), subcategoryIds, errors, warnings, 1);
            }
        }
    }

    private static void validateSubcategory(
            SubcategoryConfig sub,
            String parentId,
            Set<String> subcategoryIds,
            List<String> errors,
            List<String> warnings,
            int depth) {

        String context = "Subcategory of " + parentId;

        // Check max depth
        if (depth > 3) {
            warnings.add(context + ": Subcategory " + sub.id() + " exceeds max depth (3)");
        }

        // Validate ID
        if (sub.id() == null || sub.id().isEmpty()) {
            errors.add(context + " has no ID");
            return;
        }

        // Check for duplicates
        String fullId = parentId + "/" + sub.id();
        if (!subcategoryIds.add(fullId)) {
            errors.add("Duplicate subcategory ID: " + fullId);
        }

        // Validate items
        if (sub.items() != null) {
            for (MenuItemConfig item : sub.items()) {
                validateMenuItem(item, "Subcategory " + fullId, errors, warnings);
            }
        }

        // Recursive validation
        if (sub.subcategories() != null) {
            for (SubcategoryConfig nested : sub.subcategories()) {
                validateSubcategory(nested, fullId, subcategoryIds, errors, warnings, depth + 1);
            }
        }
    }

    private static void validateMenuItem(
            MenuItemConfig item,
            String context,
            List<String> errors,
            List<String> warnings) {

        // Validate action ID
        if (item.actionId() == null || item.actionId().isEmpty()) {
            errors.add(context + " has item with no action ID");
            return;
        }

        // Special case for supplier-based items
        if (item.actionId().startsWith("MOB_EDITOR_") || item.actionId().endsWith("_SUPPLIER")) {
            return; // These are handled specially
        }

        // Check if action exists in registry
        if (ActionRegistry.getAction(item.actionId()) == null) {
            warnings.add(context + " references unknown action: " + item.actionId());
        }

        // Validate visibility supplier reference
        if (item.visibilitySupplier() != null && !item.visibilitySupplier().isEmpty()) {
            if (!VisibilitySupplierRegistry.exists(item.visibilitySupplier())) {
                warnings.add(context + " references unknown visibility supplier: " + item.visibilitySupplier());
            }
        }

        // Validate custom color format
        if (item.customColor() != null && !item.customColor().isEmpty()) {
            if (!isValidColorFormat(item.customColor())) {
                warnings.add(context + " has invalid color format: " + item.customColor());
            }
        }
    }

    private static boolean isValidColorFormat(String color) {
        // Check hex format
        if (color.startsWith("0x") || color.startsWith("0X") || color.startsWith("#")) {
            String hex = color.startsWith("#") ? color.substring(1) : color.substring(2);
            if (hex.length() != 6 && hex.length() != 8) {
                return false;
            }
            try {
                Long.parseLong(hex, 16);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // Check if it's a valid token name
        return ColorTokenResolver.isValidToken(color);
    }
}
