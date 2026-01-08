package com.devmod.recipe.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import com.devmod.config.handler.export.CommandStringUtil;
import com.devmod.recipe.CraftingRecipeData;
import com.devmod.recipe.CraftingType;
import com.devmod.recipe.IngredientData;
import com.devmod.recipe.RecipeData;
import com.devmod.recipe.ResultData;
import com.devmod.recipe.SmeltingRecipeData;
import com.devmod.recipe.SmithingRecipeData;
import com.devmod.recipe.StonecuttingRecipeData;

/**
 * Helper class for exporting recipes to command string format.
 * Follows the same pattern as ConfigExportHelper for item stats.
 */
public final class RecipeExportHelper {

    private RecipeExportHelper() {}

    // ═══════════════════════════════════════════════════════════════
    // MAIN EXPORT METHOD
    // ═══════════════════════════════════════════════════════════════

    /**
     * Export a recipe to command string format.
     *
     * @param recipe the recipe to export
     * @param prettyPrint whether to format with headers and newlines
     * @return the command string
     */
    public static String toCommandString(RecipeData recipe, boolean prettyPrint) {
        Objects.requireNonNull(recipe, "recipe cannot be null");

        return switch (recipe) {
            case CraftingRecipeData c -> exportCrafting(c, prettyPrint);
            case SmeltingRecipeData s -> exportSmelting(s, prettyPrint);
            case SmithingRecipeData sm -> exportSmithing(sm, prettyPrint);
            case StonecuttingRecipeData sc -> exportStonecutting(sc, prettyPrint);
        };
    }

    /**
     * Export all recipes to command strings.
     *
     * @param recipes the recipes to export
     * @param prettyPrint whether to format with headers
     * @return list of command strings
     */
    public static List<String> toCommandStrings(List<RecipeData> recipes, boolean prettyPrint) {
        return recipes.stream()
            .map(recipe -> toCommandString(recipe, prettyPrint))
            .toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // CRAFTING EXPORT
    // ═══════════════════════════════════════════════════════════════

    private static String exportCrafting(CraftingRecipeData recipe, boolean prettyPrint) {
        StringBuilder sb = new StringBuilder();

        if (prettyPrint) {
            appendCraftingHeader(sb, recipe);
        }

        if (recipe.craftingType() == CraftingType.SHAPED) {
            exportShapedCrafting(sb, recipe, prettyPrint);
        } else {
            exportShapelessCrafting(sb, recipe, prettyPrint);
        }

        return sb.toString();
    }

    private static void appendCraftingHeader(StringBuilder sb, CraftingRecipeData recipe) {
        ResultData result = recipe.result();
        String resultName = getDisplayName(result);
        String typeName = recipe.craftingType() == CraftingType.SHAPED ? "Shaped Crafting" : "Shapeless Crafting";

        sb.append(CommandStringUtil.HEADER_LINE).append("\n");
        sb.append("// ").append(resultName).append(" (").append(recipe.id()).append(")\n");
        sb.append("// Type: ").append(typeName);
        sb.append(" | Result: ").append(result.count()).append("x ").append(result.itemId()).append("\n");
        sb.append(CommandStringUtil.HEADER_LINE).append("\n");
    }

    private static void exportShapedCrafting(StringBuilder sb, CraftingRecipeData recipe, boolean prettyPrint) {
        ResultData result = recipe.result();

        sb.append("DevMod.recipes.addShaped(");
        sb.append(resultBracket(result));

        if (result.count() > 1) {
            sb.append(" * ").append(result.count());
        }

        sb.append(", ");

        // Build grid
        List<IngredientData> grid = recipe.getGridIngredients();

        if (prettyPrint) {
            sb.append("[\n");
            for (int row = 0; row < 3; row++) {
                sb.append("    [");
                for (int col = 0; col < 3; col++) {
                    int idx = row * 3 + col;
                    sb.append(ingredientBracket(grid.get(idx)));
                    if (col < 2) sb.append(", ");
                }
                sb.append("]");
                if (row < 2) sb.append(",");
                sb.append("\n");
            }
            sb.append("]");
        } else {
            sb.append("[");
            for (int row = 0; row < 3; row++) {
                sb.append("[");
                for (int col = 0; col < 3; col++) {
                    int idx = row * 3 + col;
                    sb.append(ingredientBracket(grid.get(idx)));
                    if (col < 2) sb.append(", ");
                }
                sb.append("]");
                if (row < 2) sb.append(", ");
            }
            sb.append("]");
        }

        sb.append(");");
    }

    private static void exportShapelessCrafting(StringBuilder sb, CraftingRecipeData recipe, boolean prettyPrint) {
        ResultData result = recipe.result();
        List<IngredientData> ingredients = recipe.ingredients();

        sb.append("DevMod.recipes.addShapeless(");
        sb.append(resultBracket(result));

        if (result.count() > 1) {
            sb.append(" * ").append(result.count());
        }

        sb.append(", ");

        if (prettyPrint && ingredients.size() > 3) {
            sb.append("[\n");
            for (int i = 0; i < ingredients.size(); i++) {
                sb.append("    ").append(ingredientBracket(ingredients.get(i)));
                if (i < ingredients.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]");
        } else {
            sb.append("[");
            for (int i = 0; i < ingredients.size(); i++) {
                sb.append(ingredientBracket(ingredients.get(i)));
                if (i < ingredients.size() - 1) sb.append(", ");
            }
            sb.append("]");
        }

        sb.append(");");
    }

    // ═══════════════════════════════════════════════════════════════
    // SMELTING EXPORT
    // ═══════════════════════════════════════════════════════════════

    private static String exportSmelting(SmeltingRecipeData recipe, boolean prettyPrint) {
        StringBuilder sb = new StringBuilder();

        if (prettyPrint) {
            appendSmeltingHeader(sb, recipe);
        }

        String methodName = switch (recipe.smeltingType()) {
            case SMELTING -> "addSmelting";
            case BLASTING -> "addBlasting";
            case SMOKING -> "addSmoking";
            case CAMPFIRE -> "addCampfireCooking";
        };

        sb.append("DevMod.recipes.").append(methodName).append("(");
        sb.append(resultBracket(recipe.result()));
        sb.append(", ");
        sb.append(ingredientBracket(recipe.ingredient()));

        // Optional parameters
        boolean hasExperience = recipe.experience() > 0;
        boolean hasCustomTime = recipe.cookingTime() != recipe.smeltingType().getDefaultCookingTime();

        if (hasExperience || hasCustomTime) {
            if (prettyPrint) {
                sb.append(", {\n");
                if (hasExperience) {
                    sb.append("    experience: ").append(CommandStringUtil.formatFloat(recipe.experience()));
                    if (hasCustomTime) sb.append(",");
                    sb.append("\n");
                }
                if (hasCustomTime) {
                    sb.append("    cookingTime: ").append(recipe.cookingTime()).append("\n");
                }
                sb.append("}");
            } else {
                sb.append(", {");
                List<String> opts = new ArrayList<>();
                if (hasExperience) opts.add("experience: " + CommandStringUtil.formatFloat(recipe.experience()));
                if (hasCustomTime) opts.add("cookingTime: " + recipe.cookingTime());
                sb.append(String.join(", ", opts));
                sb.append("}");
            }
        }

        sb.append(");");
        return sb.toString();
    }

    private static void appendSmeltingHeader(StringBuilder sb, SmeltingRecipeData recipe) {
        ResultData result = recipe.result();
        String resultName = getDisplayName(result);
        String typeName = recipe.smeltingType().name().charAt(0)
                        + recipe.smeltingType().name().substring(1).toLowerCase(Locale.ROOT);

        sb.append(CommandStringUtil.HEADER_LINE).append("\n");
        sb.append("// ").append(resultName).append(" (").append(recipe.id()).append(")\n");
        sb.append("// Type: ").append(typeName);
        sb.append(" | Input: ").append(recipe.ingredient().getDisplayName()).append("\n");
        sb.append(CommandStringUtil.HEADER_LINE).append("\n");
    }

    // ═══════════════════════════════════════════════════════════════
    // SMITHING EXPORT
    // ═══════════════════════════════════════════════════════════════

    private static String exportSmithing(SmithingRecipeData recipe, boolean prettyPrint) {
        StringBuilder sb = new StringBuilder();

        if (prettyPrint) {
            appendSmithingHeader(sb, recipe);
        }

        String methodName = switch (recipe.smithingType()) {
            case TRANSFORM -> "addSmithingTransform";
            case TRIM -> "addSmithingTrim";
        };

        sb.append("DevMod.recipes.").append(methodName).append("(");

        if (recipe.result() != null && !recipe.result().isEmpty()) {
            sb.append(resultBracket(recipe.result()));
            sb.append(", ");
        }

        sb.append(ingredientBracket(recipe.template()));
        sb.append(", ");
        sb.append(ingredientBracket(recipe.base()));
        sb.append(", ");
        sb.append(ingredientBracket(recipe.addition()));

        sb.append(");");
        return sb.toString();
    }

    private static void appendSmithingHeader(StringBuilder sb, SmithingRecipeData recipe) {
        String typeName = recipe.smithingType().name().charAt(0)
                        + recipe.smithingType().name().substring(1).toLowerCase(Locale.ROOT) + " Smithing";

        sb.append(CommandStringUtil.HEADER_LINE).append("\n");
        sb.append("// Smithing Recipe (").append(recipe.id()).append(")\n");
        sb.append("// Type: ").append(typeName).append("\n");
        sb.append(CommandStringUtil.HEADER_LINE).append("\n");
    }

    // ═══════════════════════════════════════════════════════════════
    // STONECUTTING EXPORT
    // ═══════════════════════════════════════════════════════════════

    private static String exportStonecutting(StonecuttingRecipeData recipe, boolean prettyPrint) {
        StringBuilder sb = new StringBuilder();

        if (prettyPrint) {
            appendStonecuttingHeader(sb, recipe);
        }

        sb.append("DevMod.recipes.addStonecutting(");
        sb.append(resultBracket(recipe.result()));

        if (recipe.result().count() > 1) {
            sb.append(" * ").append(recipe.result().count());
        }

        sb.append(", ");
        sb.append(ingredientBracket(recipe.ingredient()));
        sb.append(");");

        return sb.toString();
    }

    private static void appendStonecuttingHeader(StringBuilder sb, StonecuttingRecipeData recipe) {
        ResultData result = recipe.result();
        String resultName = getDisplayName(result);

        sb.append(CommandStringUtil.HEADER_LINE).append("\n");
        sb.append("// ").append(resultName).append(" (").append(recipe.id()).append(")\n");
        sb.append("// Type: Stonecutting");
        sb.append(" | Input: ").append(recipe.ingredient().getDisplayName()).append("\n");
        sb.append(CommandStringUtil.HEADER_LINE).append("\n");
    }

    // ═══════════════════════════════════════════════════════════════
    // BRACKET SYNTAX HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Convert ingredient to bracket notation.
     * - Item: {@code <item:minecraft:diamond>}
     * - Tag: {@code <tag:minecraft:logs>}
     * - Empty: {@code <item:empty>}
     */
    public static String ingredientBracket(@Nullable IngredientData ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return CommandStringUtil.EMPTY_ITEM;
        }

        // Tag check: isTag() means tag() != null, but NullAway requires explicit check
        if (ingredient.isTag()) {
            var tag = ingredient.tag();
            if (tag != null) {
                return CommandStringUtil.tagBracket(tag.location());
            }
        }

        // Item check: isItem() means item() != null, but NullAway requires explicit check
        if (ingredient.isItem()) {
            var item = ingredient.item();
            if (item != null) {
                return CommandStringUtil.resourceLocationBracket(item);
            }
        }

        if (ingredient.isAlternatives()) {
            List<ResourceLocation> alts = ingredient.alternatives();
            if (alts != null && !alts.isEmpty()) {
                return CommandStringUtil.resourceLocationBracket(alts.getFirst());
            }
        }

        return CommandStringUtil.EMPTY_ITEM;
    }

    /**
     * Convert result to bracket notation.
     */
    public static String resultBracket(ResultData result) {
        if (result == null || result.isEmpty()) {
            return CommandStringUtil.EMPTY_ITEM;
        }
        return CommandStringUtil.resourceLocationBracket(result.itemId());
    }

    /**
     * Get display name for a result item.
     */
    private static String getDisplayName(@Nullable ResultData result) {
        if (result == null || result.isEmpty() || result.itemId() == null) {
            return "Empty";
        }

        ResourceLocation itemId = result.itemId();
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item != null) {
            try {
                return item.getDescription().getString();
            } catch (Exception e) {
                // Fallback
            }
        }

        // Fallback to path
        return itemId.getPath().replace("_", " ");
    }
}
