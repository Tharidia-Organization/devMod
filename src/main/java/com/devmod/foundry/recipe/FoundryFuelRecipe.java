package com.devmod.foundry.recipe;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
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

/**
 * Fuel recipe: fluid -> heat + burn time.
 */
public class FoundryFuelRecipe implements Recipe<FoundryFuelRecipe.FuelInput> {
    public static final int DEFAULT_BURN_TIME = 200;
    public static final int DEFAULT_TEMPERATURE = 1000;

    private final FluidStack fluid;
    private final int temperature;
    private final int burnTime;

    public FoundryFuelRecipe(FluidStack fluid, int temperature, int burnTime) {
        this.fluid = fluid;
        this.temperature = temperature;
        this.burnTime = burnTime;
    }

    @Override
    public boolean matches(@Nonnull FuelInput input, @Nonnull Level level) {
        boolean match = input.fluid.getFluid() == fluid.getFluid();
        // Debug logging
        if (!match) {
            com.devmod.DevMod.LOGGER.warn("[Foundry] Fuel recipe match failed: input={} vs recipe={}",
                input.fluid.getFluid(), fluid.getFluid());
        }
        return match;
    }

    @Override
    @Nonnull
    public ItemStack assemble(@Nonnull FuelInput input, @Nonnull HolderLookup.Provider registries) {
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
        return FoundryRecipeTypes.FUEL_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return FoundryRecipeTypes.FUEL.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public FluidStack getFluid() {
        return fluid.copy();
    }

    public int getTemperature() {
        return temperature;
    }

    public int getBurnTime() {
        return burnTime;
    }

    public record FuelInput(FluidStack fluid) implements net.minecraft.world.item.crafting.RecipeInput {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack getItem(int index) {
            return ItemStack.EMPTY;
        }
    }

    public static class Serializer implements RecipeSerializer<FoundryFuelRecipe> {
        public static final MapCodec<FoundryFuelRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                FoundryCodecs.FLUID_STACK_CODEC.fieldOf("fluid").forGetter(FoundryFuelRecipe::getFluid),
                Codec.INT.optionalFieldOf("temperature", DEFAULT_TEMPERATURE).forGetter(FoundryFuelRecipe::getTemperature),
                Codec.INT.optionalFieldOf("burn_time", DEFAULT_BURN_TIME).forGetter(FoundryFuelRecipe::getBurnTime)
            ).apply(instance, FoundryFuelRecipe::new)
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, FoundryFuelRecipe> STREAM_CODEC =
            StreamCodec.composite(
                FoundryCodecs.FLUID_STACK_STREAM_CODEC, FoundryFuelRecipe::getFluid,
                ByteBufCodecs.INT, FoundryFuelRecipe::getTemperature,
                ByteBufCodecs.INT, FoundryFuelRecipe::getBurnTime,
                FoundryFuelRecipe::new
            );

        @Override
        public MapCodec<FoundryFuelRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FoundryFuelRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
