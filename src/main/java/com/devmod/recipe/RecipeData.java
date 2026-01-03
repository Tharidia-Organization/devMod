package com.devmod.recipe;

import java.util.Objects;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public sealed interface RecipeData permits
    CraftingRecipeData,
    SmeltingRecipeData,
    SmithingRecipeData,
    StonecuttingRecipeData {

    // ═══════════════════════════════════════════════════════════════
    // IDENTIFICATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Unique identifier for this recipe.
     */
    ResourceLocation id();

    /**
     * Recipe category for recipe book.
     */
    RecipeCategory category();

    /**
     * Optional group for recipe book grouping.
     */
    @Nullable
    String group();

    // ═══════════════════════════════════════════════════════════════
    // MODIFICATION TRACKING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Whether this recipe has been modified from its original.
     */
    boolean isModified();

    /**
     * Original recipe ID if this is a modification.
     */
    @Nullable
    ResourceLocation originalId();

    // ═══════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Serialize to Minecraft JSON format.
     */
    JsonObject toJson();

    /**
     * Get the Minecraft recipe type string (e.g., "minecraft:crafting_shaped").
     */
    String getMinecraftType();

    // ═══════════════════════════════════════════════════════════════
    // FACTORY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Deserialize from Minecraft JSON format.
     *
     * @param id The recipe ID
     * @param json The recipe JSON
     * @return The appropriate RecipeData subtype
     * @throws IllegalArgumentException if the type is unknown
     */
    static RecipeData fromJson(ResourceLocation id, JsonObject json) {
        JsonObject root = Objects.requireNonNull(json, "json");
        String type = Objects.requireNonNull(GsonHelper.getAsString(root, "type", ""));

        return switch (type) {
            case "minecraft:crafting_shaped",
                 "minecraft:crafting_shapeless" -> CraftingRecipeData.fromJson(id, root);

            case "minecraft:smelting",
                 "minecraft:blasting",
                 "minecraft:smoking",
                 "minecraft:campfire_cooking" -> SmeltingRecipeData.fromJson(id, root);

            case "minecraft:smithing_transform",
                 "minecraft:smithing_trim" -> SmithingRecipeData.fromJson(id, root);

            case "minecraft:stonecutting" -> StonecuttingRecipeData.fromJson(id, root);

            default -> throw new IllegalArgumentException("Unknown recipe type: " + type);
        };
    }

    /**
     * Get display-friendly recipe type name.
     */
    default String getTypeName() {
        if (this instanceof CraftingRecipeData c) {
            return c.craftingType() == CraftingType.SHAPED ? "Shaped" : "Shapeless";
        }
        if (this instanceof SmeltingRecipeData s) {
            return switch (s.smeltingType()) {
                case SMELTING -> "Smelting";
                case BLASTING -> "Blasting";
                case SMOKING -> "Smoking";
                case CAMPFIRE -> "Campfire";
            };
        }
        if (this instanceof SmithingRecipeData sm) {
            return sm.smithingType() == SmithingType.TRANSFORM ? "Smithing" : "Trim";
        }
        if (this instanceof StonecuttingRecipeData) {
            return "Stonecutting";
        }
        return "Unknown";
    }

    /**
     * Get the result of this recipe (if applicable).
     */
    @Nullable
    default ResultData getResult() {
        return switch (this) {
            case CraftingRecipeData c -> c.result();
            case SmeltingRecipeData s -> s.result();
            case SmithingRecipeData sm -> sm.result();
            case StonecuttingRecipeData sc -> sc.result();
        };
    }
}
