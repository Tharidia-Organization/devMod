package com.devmod.recipe;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Data for smithing recipes (transform and trim).
 */
public record SmithingRecipeData(
    ResourceLocation id,
    SmithingType smithingType,
    @Nullable IngredientData template,
    IngredientData base,
    @Nullable IngredientData addition,
    @Nullable ResultData result,
    boolean isModified,
    @Nullable ResourceLocation originalId
) implements RecipeData {

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════

    public SmithingRecipeData {
        Objects.requireNonNull(id, "Recipe ID cannot be null");
        Objects.requireNonNull(smithingType, "Smithing type cannot be null");
        Objects.requireNonNull(base, "Base ingredient cannot be null");

        // Transform requires a result, Trim does not
        if (smithingType == SmithingType.TRANSFORM && result == null) {
            result = ResultData.empty();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RecipeData IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public RecipeCategory category() {
        return RecipeCategory.EQUIPMENT;
    }

    @Override
    public @Nullable String group() {
        return null; // Smithing recipes don't use groups
    }

    // ═══════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create a smithing transform recipe.
     */
    public static SmithingRecipeData transform(
        ResourceLocation id,
        IngredientData template,
        IngredientData base,
        IngredientData addition,
        ResultData result
    ) {
        return new SmithingRecipeData(
            id, SmithingType.TRANSFORM,
            template, base, addition, result,
            false, null
        );
    }

    /**
     * Create a smithing trim recipe.
     */
    public static SmithingRecipeData trim(
        ResourceLocation id,
        IngredientData template,
        IngredientData base,
        IngredientData addition
    ) {
        return new SmithingRecipeData(
            id, SmithingType.TRIM,
            template, base, addition, null,
            false, null
        );
    }

    /**
     * Create an empty smithing recipe.
     */
    public static SmithingRecipeData empty(ResourceLocation id) {
        return new SmithingRecipeData(
            id, SmithingType.TRANSFORM,
            IngredientData.empty(),
            IngredientData.empty(),
            IngredientData.empty(),
            ResultData.empty(),
            false, null
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // DESERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    public static SmithingRecipeData fromJson(ResourceLocation id, JsonObject json) {
        ResourceLocation safeId = Objects.requireNonNull(id, "id");
        JsonObject root = Objects.requireNonNull(json, "json");
        String type = GsonHelper.getAsString(root, "type");
        SmithingType smithingType = SmithingType.fromMinecraftType(type);

        // Template (optional)
        IngredientData template = root.has("template")
            ? IngredientData.fromJson(root.get("template"))
            : IngredientData.empty();

        // Base
        IngredientData base = root.has("base")
            ? IngredientData.fromJson(root.get("base"))
            : IngredientData.empty();

        // Addition (optional)
        IngredientData addition = root.has("addition")
            ? IngredientData.fromJson(root.get("addition"))
            : IngredientData.empty();

        // Result (only for transform)
        ResultData result = null;
        if (smithingType == SmithingType.TRANSFORM && root.has("result")) {
            result = ResultData.fromJson(
                Objects.requireNonNull(GsonHelper.getAsJsonObject(root, "result"), "result")
            );
        }

        return new SmithingRecipeData(
            safeId, smithingType,
            template, base, addition, result,
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

        if (template != null && !template.isEmpty()) {
            json.add("template", template.toJson());
        }

        json.add("base", base.toJson());

        if (addition != null && !addition.isEmpty()) {
            json.add("addition", addition.toJson());
        }

        if (smithingType == SmithingType.TRANSFORM && result != null && !result.isEmpty()) {
            json.add("result", result.toJson());
        }

        return json;
    }

    @Override
    public String getMinecraftType() {
        return smithingType.getMinecraftType();
    }

    // ═══════════════════════════════════════════════════════════════
    // MODIFICATION
    // ═══════════════════════════════════════════════════════════════

    public SmithingRecipeData withSmithingType(SmithingType newType) {
        ResultData newResult = newType == SmithingType.TRIM ? null : result;
        return new SmithingRecipeData(
            id, newType, template, base, addition, newResult,
            true, originalId != null ? originalId : id
        );
    }

    public SmithingRecipeData withTemplate(IngredientData newTemplate) {
        return new SmithingRecipeData(
            id, smithingType, newTemplate, base, addition, result,
            true, originalId != null ? originalId : id
        );
    }

    public SmithingRecipeData withBase(IngredientData newBase) {
        return new SmithingRecipeData(
            id, smithingType, template, newBase, addition, result,
            true, originalId != null ? originalId : id
        );
    }

    public SmithingRecipeData withAddition(IngredientData newAddition) {
        return new SmithingRecipeData(
            id, smithingType, template, base, newAddition, result,
            true, originalId != null ? originalId : id
        );
    }

    public SmithingRecipeData withResult(ResultData newResult) {
        return new SmithingRecipeData(
            id, smithingType, template, base, addition, newResult,
            true, originalId != null ? originalId : id
        );
    }

    @Override
    public String toString() {
        return "SmithingRecipeData{" +
            "id=" + id +
            ", type=" + smithingType +
            ", template=" + template +
            ", base=" + base +
            ", addition=" + addition +
            ", result=" + result +
            '}';
    }
}
