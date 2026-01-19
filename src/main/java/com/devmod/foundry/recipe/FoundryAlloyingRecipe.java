package com.devmod.foundry.recipe;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.FoundryRecipeTypes;
import com.devmod.foundry.fluid.FoundryFluidTank;

/**
 * Alloying recipe: fluid inputs -> fluid output.
 */
public class FoundryAlloyingRecipe implements Recipe<FoundryAlloyingRecipe.AlloyingInput> {
    private final List<FluidStack> inputs;
    private final FluidStack output;
    private final int temperature;

    public FoundryAlloyingRecipe(List<FluidStack> inputs, FluidStack output, int temperature) {
        this.inputs = inputs;
        this.output = output;
        this.temperature = temperature;
    }

    @Override
    public boolean matches(@Nonnull AlloyingInput input, @Nonnull Level level) {
        return false;
    }

    @Override
    @Nonnull
    public ItemStack assemble(@Nonnull AlloyingInput input, @Nonnull HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    @Nonnull
    public ItemStack getResultItem(@Nonnull HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<net.minecraft.world.item.crafting.Ingredient> getIngredients() {
        return NonNullList.create();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FoundryRecipeTypes.ALLOYING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return FoundryRecipeTypes.ALLOYING.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public List<FluidStack> getInputs() {
        return inputs;
    }

    public FluidStack getOutput() {
        return output.copy();
    }

    public int getTemperature() {
        return temperature;
    }

    public int getInputAmount() {
        int total = 0;
        for (FluidStack stack : inputs) {
            total += stack.getAmount();
        }
        return total;
    }

    public boolean canApply(FoundryFluidTank tank) {
        for (FluidStack stack : inputs) {
            if (!tank.contains(stack)) {
                return false;
            }
        }
        return true;
    }

    public void consumeInputs(FoundryFluidTank tank) {
        for (FluidStack stack : inputs) {
            tank.drain(stack, false);
        }
    }

    public record AlloyingInput(List<FluidStack> fluids) implements net.minecraft.world.item.crafting.RecipeInput {
        @Override
        public int size() {
            return fluids.size();
        }

        @Override
        public ItemStack getItem(int index) {
            return ItemStack.EMPTY;
        }
    }

    public static class Serializer implements RecipeSerializer<FoundryAlloyingRecipe> {
        public static final MapCodec<FoundryAlloyingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                FoundryCodecs.FLUID_STACK_CODEC.listOf().fieldOf("inputs").forGetter(FoundryAlloyingRecipe::getInputs),
                FoundryCodecs.FLUID_STACK_CODEC.fieldOf("result").forGetter(FoundryAlloyingRecipe::getOutput),
                com.mojang.serialization.Codec.INT.optionalFieldOf("temperature", 0).forGetter(FoundryAlloyingRecipe::getTemperature)
            ).apply(instance, FoundryAlloyingRecipe::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, FoundryAlloyingRecipe> STREAM_CODEC =
            StreamCodec.composite(
                FoundryCodecs.FLUID_STACK_STREAM_CODEC.apply(ByteBufCodecs.list()), FoundryAlloyingRecipe::getInputs,
                FoundryCodecs.FLUID_STACK_STREAM_CODEC, FoundryAlloyingRecipe::getOutput,
                ByteBufCodecs.INT, FoundryAlloyingRecipe::getTemperature,
                FoundryAlloyingRecipe::new
            );

        @Override
        public MapCodec<FoundryAlloyingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FoundryAlloyingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
