package com.devmod.client.ui.editor.modules;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.components.RecipeGridComponent;
import com.devmod.recipe.CraftingRecipeData;
import com.devmod.recipe.CraftingType;
import com.devmod.recipe.IngredientData;
import com.devmod.recipe.RecipeCategory;
import com.devmod.recipe.RecipeValidator;
import com.devmod.recipe.ResultData;

/**
 * Core data management for RecipeModule.
 * Handles recipe state, validation, and recipe building.
 */
public class RecipeModuleCore {

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private CraftingType craftingType = CraftingType.SHAPED;
    private String recipeId = "";
    private String recipeGroup = "";
    private RecipeCategory category = RecipeCategory.MISC;
    private int resultQuantity = 1;
    private boolean replaceVanillaRecipe = false;
    private @Nullable ResourceLocation vanillaRecipeToReplace = null;

    // Validation cache
    private @Nullable RecipeValidator.ValidationResult lastValidation;
    private boolean validationDirty = true;

    public RecipeModuleCore() {
        // Default constructor
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Initialize state from an item.
     */
    public void initFromItem(ItemStack item, RecipeGridComponent grid) {
        Objects.requireNonNull(item, "item cannot be null");
        Objects.requireNonNull(grid, "grid cannot be null");

        CraftingRecipeData recipe = CraftingRecipeData.empty(item);
        craftingType = recipe.craftingType();
        recipeId = recipe.id().toString();
        recipeGroup = recipe.group() != null ? recipe.group() : "";
        category = recipe.category();
        resultQuantity = recipe.result().count();

        grid.loadFromRecipe(recipe);
        validationDirty = true;
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILD RECIPE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Build a CraftingRecipeData from current state.
     */
    public CraftingRecipeData buildCurrentRecipe(ItemStack item, RecipeGridComponent grid) {
        Objects.requireNonNull(item, "item cannot be null");
        Objects.requireNonNull(grid, "grid cannot be null");

        List<IngredientData> gridData = grid.getIngredientList();

        ResourceLocation id = ResourceLocation.tryParse(Objects.requireNonNull(recipeId, "recipeId"));
        if (id == null) {
            id = ResourceLocation.fromNamespaceAndPath("devmod", "invalid_" + System.currentTimeMillis());
        }

        ResultData result = ResultData.of(item).withCount(resultQuantity);

        CraftingRecipeData recipe;
        if (craftingType == CraftingType.SHAPED) {
            recipe = CraftingRecipeData.empty(item)
                .withId(id)
                .withGrid(gridData)
                .withResult(result)
                .withCategory(category);
        } else {
            List<IngredientData> nonEmpty = gridData.stream()
                .filter(i -> !i.isEmpty())
                .toList();
            recipe = CraftingRecipeData.shapeless(id, nonEmpty, result)
                .withCategory(category);
        }

        ResourceLocation replaceTarget = vanillaRecipeToReplace;
        if (replaceVanillaRecipe && replaceTarget != null) {
            recipe = recipe.withOriginalId(replaceTarget);
        }

        return recipe;
    }

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get validation result, computing if dirty.
     */
    public RecipeValidator.ValidationResult getValidation(ItemStack item, RecipeGridComponent grid) {
        if (validationDirty || lastValidation == null) {
            CraftingRecipeData recipe = buildCurrentRecipe(item, grid);
            lastValidation = RecipeValidator.validateCrafting(recipe);
            validationDirty = false;
        }
        return Objects.requireNonNull(lastValidation, "lastValidation");
    }

    /**
     * Mark validation as dirty (needs recomputation).
     */
    public void invalidateValidation() {
        validationDirty = true;
    }

    // ═══════════════════════════════════════════════════════════════
    // VANILLA RECIPE LOOKUP
    // ═══════════════════════════════════════════════════════════════

    /**
     * Find a vanilla or modded recipe that produces this item.
     * Searches all recipe types: crafting, smelting, blasting, smoking, campfire, smithing, stonecutting.
     */
    public @Nullable ResourceLocation findVanillaRecipeForItem(ItemStack item) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return null;

        @Nonnull var safeLevel = Objects.requireNonNull(level, "level");
        var recipeManager = safeLevel.getRecipeManager();
        @Nonnull var baseItem = Objects.requireNonNull(item.getItem(), "item");
        var itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(baseItem);
        @Nonnull var registryAccess = Objects.requireNonNull(safeLevel.registryAccess(), "registry access");

        // Capture recipe types with null safety
        var craftingType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.CRAFTING, "CRAFTING type");
        var smeltingType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.SMELTING, "SMELTING type");
        var blastingType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.BLASTING, "BLASTING type");
        var smokingType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.SMOKING, "SMOKING type");
        var campfireType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING, "CAMPFIRE_COOKING type");
        var smithingType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.SMITHING, "SMITHING type");
        var stonecuttingType = Objects.requireNonNull(
            net.minecraft.world.item.crafting.RecipeType.STONECUTTING, "STONECUTTING type");

        // Search crafting recipes
        for (var holder : recipeManager.getAllRecipesFor(craftingType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        // Search smelting recipes
        for (var holder : recipeManager.getAllRecipesFor(smeltingType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        // Search blasting recipes
        for (var holder : recipeManager.getAllRecipesFor(blastingType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        // Search smoking recipes
        for (var holder : recipeManager.getAllRecipesFor(smokingType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        // Search campfire cooking recipes
        for (var holder : recipeManager.getAllRecipesFor(campfireType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        // Search smithing recipes
        for (var holder : recipeManager.getAllRecipesFor(smithingType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        // Search stonecutting recipes
        for (var holder : recipeManager.getAllRecipesFor(stonecuttingType)) {
            var result = holder.value().getResultItem(registryAccess);
            if (!result.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                    Objects.requireNonNull(result.getItem(), "result item")).equals(itemId)) {
                return holder.id();
            }
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & SETTERS
    // ═══════════════════════════════════════════════════════════════

    public CraftingType getCraftingType() {
        return craftingType;
    }

    public void setCraftingType(@Nullable CraftingType type) {
        this.craftingType = Objects.requireNonNull(type, "type cannot be null");
        validationDirty = true;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(@Nullable String id) {
        this.recipeId = id == null ? "" : id;
        validationDirty = true;
    }

    public String getRecipeGroup() {
        return recipeGroup;
    }

    public void setRecipeGroup(@Nullable String group) {
        this.recipeGroup = group == null ? "" : group;
    }

    public RecipeCategory getCategory() {
        return category;
    }

    public void setCategory(@Nullable RecipeCategory cat) {
        this.category = cat == null ? RecipeCategory.MISC : cat;
    }

    public int getResultQuantity() {
        return resultQuantity;
    }

    public void setResultQuantity(int quantity) {
        this.resultQuantity = Math.max(1, Math.min(64, quantity));
    }

    public boolean isReplaceVanillaRecipe() {
        return replaceVanillaRecipe;
    }

    public void setReplaceVanillaRecipe(boolean replace) {
        this.replaceVanillaRecipe = replace;
    }

    @Nullable
    public ResourceLocation getVanillaRecipeToReplace() {
        return vanillaRecipeToReplace;
    }

    public void setVanillaRecipeToReplace(@Nullable ResourceLocation id) {
        this.vanillaRecipeToReplace = id;
    }
}
