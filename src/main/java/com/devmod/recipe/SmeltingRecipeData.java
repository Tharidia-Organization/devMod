package com.devmod.recipe;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Data for smelting recipes (furnace, blast furnace, smoker, campfire).
 */
public record SmeltingRecipeData(
    ResourceLocation id,
    SmeltingType smeltingType,
    RecipeCategory category,
    @Nullable String group,
    IngredientData ingredient,
    ResultData result,
    float experience,
    int cookingTime,
    boolean isModified,
    @Nullable ResourceLocation originalId
) implements RecipeData {

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════

    public SmeltingRecipeData {
        Objects.requireNonNull(id, "Recipe ID cannot be null");
        Objects.requireNonNull(smeltingType, "Smelting type cannot be null");
        Objects.requireNonNull(ingredient, "Ingredient cannot be null");
        Objects.requireNonNull(result, "Result cannot be null");
        if (category == null) category = RecipeCategory.MISC;
        if (experience < 0) experience = 0;
        if (cookingTime <= 0) cookingTime = smeltingType.getDefaultCookingTime();
    }

    // ═══════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create with default cooking time for the smelting type.
     */
    public static SmeltingRecipeData create(
        ResourceLocation id,
        SmeltingType type,
        IngredientData ingredient,
        ResultData result,
        float experience
    ) {
        return new SmeltingRecipeData(
            id, type, RecipeCategory.MISC, null,
            ingredient, result, experience,
            type.getDefaultCookingTime(),
            false, null
        );
    }

    /**
     * Create an empty smelting recipe.
     */
    public static SmeltingRecipeData empty(ResourceLocation id) {
        return new SmeltingRecipeData(
            id, SmeltingType.SMELTING, RecipeCategory.MISC, null,
            IngredientData.empty(), ResultData.empty(), 0f,
            SmeltingType.SMELTING.getDefaultCookingTime(),
            false, null
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // DESERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    public static SmeltingRecipeData fromJson(ResourceLocation id, JsonObject json) {
        ResourceLocation safeId = Objects.requireNonNull(id, "id");
        JsonObject root = Objects.requireNonNull(json, "json");
        String type = GsonHelper.getAsString(root, "type");
        SmeltingType smeltingType = SmeltingType.fromMinecraftType(type);

        // Category
        String catStr = GsonHelper.getAsString(root, "category", "misc");
        RecipeCategory category = RecipeCategory.fromString(catStr);

        // Group
        String group = root.has("group") ? GsonHelper.getAsString(root, "group") : null;

        // Ingredient - can be string or object
        IngredientData ingredient;
        if (root.has("ingredient")) {
            ingredient = IngredientData.fromJson(root.get("ingredient"));
        } else {
            ingredient = IngredientData.empty();
        }

        // Result
        ResultData result = ResultData.fromJson(
            Objects.requireNonNull(GsonHelper.getAsJsonObject(root, "result"), "result")
        );

        // Experience
        float experience = GsonHelper.getAsFloat(root, "experience", 0f);

        // Cooking time
        int cookingTime = GsonHelper.getAsInt(root, "cookingtime", smeltingType.getDefaultCookingTime());

        return new SmeltingRecipeData(
            safeId, smeltingType, category, group,
            ingredient, result, experience, cookingTime,
            false, null
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        json.addProperty("type", getMinecraftType());

        if (category != RecipeCategory.MISC) {
            json.addProperty("category", category.getId());
        }

        if (group != null && !group.isEmpty()) {
            json.addProperty("group", group);
        }

        json.add("ingredient", ingredient.toJson());
        json.add("result", result.toJson());

        if (experience > 0) {
            json.addProperty("experience", experience);
        }

        json.addProperty("cookingtime", cookingTime);

        return json;
    }

    @Override
    public String getMinecraftType() {
        return smeltingType.getMinecraftType();
    }

    // ═══════════════════════════════════════════════════════════════
    // MODIFICATION
    // ═══════════════════════════════════════════════════════════════

    public SmeltingRecipeData withSmeltingType(SmeltingType newType) {
        return new SmeltingRecipeData(
            id, newType, category, group,
            ingredient, result, experience,
            newType.getDefaultCookingTime(),
            true, originalId != null ? originalId : id
        );
    }

    public SmeltingRecipeData withIngredient(IngredientData newIngredient) {
        return new SmeltingRecipeData(
            id, smeltingType, category, group,
            newIngredient, result, experience, cookingTime,
            true, originalId != null ? originalId : id
        );
    }

    public SmeltingRecipeData withResult(ResultData newResult) {
        return new SmeltingRecipeData(
            id, smeltingType, category, group,
            ingredient, newResult, experience, cookingTime,
            true, originalId != null ? originalId : id
        );
    }

    public SmeltingRecipeData withExperience(float newExperience) {
        return new SmeltingRecipeData(
            id, smeltingType, category, group,
            ingredient, result, newExperience, cookingTime,
            true, originalId != null ? originalId : id
        );
    }

    public SmeltingRecipeData withCookingTime(int newCookingTime) {
        return new SmeltingRecipeData(
            id, smeltingType, category, group,
            ingredient, result, experience, newCookingTime,
            true, originalId != null ? originalId : id
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get cooking time in seconds.
     */
    public float getCookingTimeSeconds() {
        return cookingTime / 20.0f;
    }

    @Override
    public String toString() {
        return "SmeltingRecipeData{" +
            "id=" + id +
            ", type=" + smeltingType +
            ", ingredient=" + ingredient +
            ", result=" + result +
            ", xp=" + experience +
            ", time=" + cookingTime +
            '}';
    }
}
