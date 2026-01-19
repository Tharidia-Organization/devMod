package com.devmod.foundry.compat.jei;

import com.devmod.DevMod;
import com.devmod.foundry.recipe.FoundryAlloyingRecipe;
import com.devmod.foundry.recipe.FoundryCastingRecipe;
import com.devmod.foundry.recipe.FoundryFuelRecipe;
import com.devmod.foundry.recipe.FoundryMeltingRecipe;

import mezz.jei.api.recipe.RecipeType;

/**
 * JEI recipe type constants for Foundry recipes.
 */
public final class FoundryJeiTypes {
    private FoundryJeiTypes() {}

    public static final RecipeType<FoundryMeltingRecipe> MELTING =
        RecipeType.create(DevMod.MODID, "foundry_melting", FoundryMeltingRecipe.class);
    public static final RecipeType<FoundryAlloyingRecipe> ALLOYING =
        RecipeType.create(DevMod.MODID, "foundry_alloying", FoundryAlloyingRecipe.class);
    public static final RecipeType<FoundryCastingRecipe> CASTING_TABLE =
        RecipeType.create(DevMod.MODID, "foundry_casting_table", FoundryCastingRecipe.class);
    public static final RecipeType<FoundryCastingRecipe> CASTING_BASIN =
        RecipeType.create(DevMod.MODID, "foundry_casting_basin", FoundryCastingRecipe.class);
    public static final RecipeType<FoundryFuelRecipe> FUEL =
        RecipeType.create(DevMod.MODID, "foundry_fuel", FoundryFuelRecipe.class);
}
