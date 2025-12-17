package com.frenkvs.devmod.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates recipe data for correctness.
 */
public final class RecipeValidator {

    private static final Pattern RESOURCE_LOCATION_PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_/.-]+$");

    private RecipeValidator() {}

    // ═══════════════════════════════════════════════════════════════
    // RESULT RECORD
    // ═══════════════════════════════════════════════════════════════

    /**
     * Result of validation.
     */
    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of(), List.of());
        }

        public static ValidationResult error(String... errors) {
            return new ValidationResult(false, List.of(errors), List.of());
        }

        public static ValidationResult withWarnings(List<String> warnings) {
            return new ValidationResult(true, List.of(), warnings);
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public String getFirstError() {
            return errors.isEmpty() ? null : errors.getFirst();
        }

        public String getSummary() {
            if (valid && warnings.isEmpty()) return "Valid";
            if (valid) return "Valid with " + warnings.size() + " warning(s)";
            return errors.size() + " error(s)";
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MAIN VALIDATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Validate any recipe data.
     */
    public static ValidationResult validate(RecipeData recipe) {
        if (recipe == null) {
            return ValidationResult.error("Recipe cannot be null");
        }

        return switch (recipe) {
            case CraftingRecipeData c -> validateCrafting(c);
            case SmeltingRecipeData s -> validateSmelting(s);
            case SmithingRecipeData sm -> validateSmithing(sm);
            case StonecuttingRecipeData sc -> validateStonecutting(sc);
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // CRAFTING VALIDATION
    // ═══════════════════════════════════════════════════════════════

    public static ValidationResult validateCrafting(CraftingRecipeData recipe) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ID validation
        validateId(recipe.id(), errors);

        // Must have at least one ingredient
        if (!recipe.hasIngredients()) {
            errors.add("Recipe must have at least one ingredient");
        }

        // Result validation
        validateResult(recipe.result(), errors);

        // Shaped-specific validation
        if (recipe.craftingType() == CraftingType.SHAPED) {
            validateShapedPattern(recipe, errors, warnings);
        }

        // Shapeless-specific validation
        if (recipe.craftingType() == CraftingType.SHAPELESS) {
            if (recipe.ingredients().size() > 9) {
                errors.add("Shapeless recipe can have at most 9 ingredients");
            }
        }

        // Validate individual ingredients
        validateIngredients(recipe.getGridIngredients(), errors, warnings);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private static void validateShapedPattern(CraftingRecipeData recipe, List<String> errors, List<String> warnings) {
        String[] pattern = recipe.pattern();
        if (pattern == null || pattern.length == 0) {
            errors.add("Shaped recipe must have a pattern");
            return;
        }

        if (pattern.length > 3) {
            errors.add("Pattern can have at most 3 rows");
        }

        int width = 0;
        for (String row : pattern) {
            if (row == null) continue;
            if (row.length() > 3) {
                errors.add("Pattern row can have at most 3 columns");
            }
            width = Math.max(width, row.length());
        }

        // Check for unused keys
        var keyMap = recipe.keyMap();
        if (keyMap != null) {
            for (var entry : keyMap.entrySet()) {
                char key = entry.getKey();
                boolean used = false;
                for (String row : pattern) {
                    if (row != null && row.indexOf(key) >= 0) {
                        used = true;
                        break;
                    }
                }
                if (!used) {
                    warnings.add("Key '" + key + "' is defined but not used in pattern");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SMELTING VALIDATION
    // ═══════════════════════════════════════════════════════════════

    public static ValidationResult validateSmelting(SmeltingRecipeData recipe) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validateId(recipe.id(), errors);

        if (recipe.ingredient().isEmpty()) {
            errors.add("Recipe must have an ingredient");
        } else {
            validateIngredient(recipe.ingredient(), errors, warnings);
        }

        validateResult(recipe.result(), errors);

        if (recipe.cookingTime() <= 0) {
            errors.add("Cooking time must be positive");
        } else if (recipe.cookingTime() > 72000) {
            warnings.add("Cooking time is very long (over 1 hour)");
        }

        if (recipe.experience() < 0) {
            errors.add("Experience cannot be negative");
        } else if (recipe.experience() > 10) {
            warnings.add("Experience is unusually high");
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    // ═══════════════════════════════════════════════════════════════
    // SMITHING VALIDATION
    // ═══════════════════════════════════════════════════════════════

    public static ValidationResult validateSmithing(SmithingRecipeData recipe) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validateId(recipe.id(), errors);

        if (recipe.base().isEmpty()) {
            errors.add("Smithing recipe must have a base item");
        } else {
            validateIngredient(recipe.base(), errors, warnings);
        }

        if (recipe.smithingType() == SmithingType.TRANSFORM) {
            if (recipe.result() == null || recipe.result().isEmpty()) {
                errors.add("Smithing transform recipe must have a result");
            } else {
                validateResult(recipe.result(), errors);
            }
        }

        if (recipe.template() != null && !recipe.template().isEmpty()) {
            validateIngredient(recipe.template(), errors, warnings);
        }

        if (recipe.addition() != null && !recipe.addition().isEmpty()) {
            validateIngredient(recipe.addition(), errors, warnings);
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    // ═══════════════════════════════════════════════════════════════
    // STONECUTTING VALIDATION
    // ═══════════════════════════════════════════════════════════════

    public static ValidationResult validateStonecutting(StonecuttingRecipeData recipe) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validateId(recipe.id(), errors);

        if (recipe.ingredient().isEmpty()) {
            errors.add("Recipe must have an ingredient");
        } else {
            validateIngredient(recipe.ingredient(), errors, warnings);
        }

        validateResult(recipe.result(), errors);

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════

    private static void validateId(ResourceLocation id, List<String> errors) {
        if (id == null) {
            errors.add("Recipe ID cannot be null");
            return;
        }

        String idStr = id.toString();
        if (!RESOURCE_LOCATION_PATTERN.matcher(idStr).matches()) {
            errors.add("Invalid recipe ID format: " + idStr);
        }
    }

    private static void validateResult(ResultData result, List<String> errors) {
        if (result == null || result.isEmpty()) {
            errors.add("Recipe must have a result");
            return;
        }

        if (result.itemId() == null) {
            errors.add("Result item ID cannot be null");
            return;
        }

        // Check item exists
        Item item = BuiltInRegistries.ITEM.get(result.itemId());
        if (item == null || item == Items.AIR) {
            errors.add("Result item does not exist: " + result.itemId());
        }

        if (result.count() <= 0) {
            errors.add("Result count must be at least 1");
        } else if (result.count() > 64) {
            errors.add("Result count cannot exceed 64");
        }
    }

    private static void validateIngredients(List<IngredientData> ingredients, List<String> errors, List<String> warnings) {
        for (int i = 0; i < ingredients.size(); i++) {
            IngredientData ing = ingredients.get(i);
            if (ing != null && !ing.isEmpty()) {
                validateIngredient(ing, errors, warnings);
            }
        }
    }

    private static void validateIngredient(IngredientData ingredient, List<String> errors, List<String> warnings) {
        if (ingredient.isItem()) {
            Item item = BuiltInRegistries.ITEM.get(ingredient.item());
            if (item == null || item == Items.AIR) {
                errors.add("Ingredient item does not exist: " + ingredient.item());
            }
        } else if (ingredient.isTag()) {
            // Check if tag has any items
            var tagItems = ingredient.getTagItems();
            if (tagItems.isEmpty()) {
                warnings.add("Tag has no items: " + ingredient.tag().location());
            }
        } else if (ingredient.isAlternatives()) {
            for (ResourceLocation alt : ingredient.alternatives()) {
                Item item = BuiltInRegistries.ITEM.get(alt);
                if (item == null || item == Items.AIR) {
                    errors.add("Alternative item does not exist: " + alt);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // QUICK CHECKS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Quick check if recipe ID is valid format.
     */
    public static boolean isValidId(String id) {
        return id != null && RESOURCE_LOCATION_PATTERN.matcher(id).matches();
    }

    /**
     * Quick check if item exists in registry.
     */
    public static boolean itemExists(ResourceLocation itemId) {
        if (itemId == null) return false;
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item != null && item != Items.AIR;
    }
}
