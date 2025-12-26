package com.devmod.recipe;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import com.google.gson.JsonObject;

/**
 * Data for stonecutting recipes.
 */
public record StonecuttingRecipeData(
    ResourceLocation id,
    IngredientData ingredient,
    ResultData result,
    boolean isModified,
    @Nullable ResourceLocation originalId
) implements RecipeData {

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════

    public StonecuttingRecipeData {
        Objects.requireNonNull(id, "Recipe ID cannot be null");
        Objects.requireNonNull(ingredient, "Ingredient cannot be null");
        Objects.requireNonNull(result, "Result cannot be null");
    }

    // ═══════════════════════════════════════════════════════════════
    // RecipeData IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public RecipeCategory category() {
        return RecipeCategory.BUILDING;
    }

    @Override
    public @Nullable String group() {
        return null; // Stonecutting recipes don't use groups
    }

    // ═══════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create a stonecutting recipe.
     */
    public static StonecuttingRecipeData create(
        ResourceLocation id,
        IngredientData ingredient,
        ResultData result
    ) {
        return new StonecuttingRecipeData(id, ingredient, result, false, null);
    }

    /**
     * Create an empty stonecutting recipe.
     */
    public static StonecuttingRecipeData empty(ResourceLocation id) {
        return new StonecuttingRecipeData(
            id,
            IngredientData.empty(),
            ResultData.empty(),
            false, null
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // DESERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    public static StonecuttingRecipeData fromJson(ResourceLocation id, JsonObject json) {
        // Ingredient
        IngredientData ingredient = json.has("ingredient")
            ? IngredientData.fromJson(json.get("ingredient"))
            : IngredientData.empty();

        // Result
        ResultData result = ResultData.fromJson(GsonHelper.getAsJsonObject(json, "result"));

        return new StonecuttingRecipeData(id, ingredient, result, false, null);
    }

    // ═══════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        json.addProperty("type", getMinecraftType());
        json.add("ingredient", ingredient.toJson());
        json.add("result", result.toJson());

        return json;
    }

    @Override
    public String getMinecraftType() {
        return "minecraft:stonecutting";
    }

    // ═══════════════════════════════════════════════════════════════
    // MODIFICATION
    // ═══════════════════════════════════════════════════════════════

    public StonecuttingRecipeData withIngredient(IngredientData newIngredient) {
        return new StonecuttingRecipeData(
            id, newIngredient, result,
            true, originalId != null ? originalId : id
        );
    }

    public StonecuttingRecipeData withResult(ResultData newResult) {
        return new StonecuttingRecipeData(
            id, ingredient, newResult,
            true, originalId != null ? originalId : id
        );
    }

    @Override
    public String toString() {
        return "StonecuttingRecipeData{" +
            "id=" + id +
            ", ingredient=" + ingredient +
            ", result=" + result +
            '}';
    }
}
