package com.devmod.foundry.block.entity;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.FoundryRecipeTypes;
import com.devmod.foundry.recipe.FoundryCastingRecipe;

public class FoundryCastingBasinBlockEntity extends FoundryCastingBlockEntity {
    public FoundryCastingBasinBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_CASTING_BASIN.get()), pos, state);
    }

    @Override
    public RecipeType<FoundryCastingRecipe> getRecipeType() {
        return FoundryRecipeTypes.CASTING_BASIN.get();
    }
}
