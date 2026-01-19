package com.devmod.foundry.recipe;

import java.util.List;
import java.util.Objects;

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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.FoundryRecipeTypes;
import com.devmod.foundry.fluid.FoundryFluidTank;

/**
 * Melting recipe: item -> molten fluid.
 */
public class FoundryMeltingRecipe implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_TIME = 200;

    private final Ingredient ingredient;
    private final FluidStack output;
    private final int time;
    private final int temperature;
    private final List<FluidStack> byproducts;

    public FoundryMeltingRecipe(Ingredient ingredient, FluidStack output, int time, int temperature, List<FluidStack> byproducts) {
        this.ingredient = ingredient;
        this.output = output;
        this.time = time;
        this.temperature = temperature;
        this.byproducts = byproducts;
    }

    @Override
    public boolean matches(@Nonnull SingleRecipeInput input, @Nonnull Level level) {
        return ingredient.test(input.getItem(0));
    }

    @Override
    @Nonnull
    public ItemStack assemble(@Nonnull SingleRecipeInput input, @Nonnull HolderLookup.Provider registries) {
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
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(ingredient);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FoundryRecipeTypes.MELTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return FoundryRecipeTypes.MELTING.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public FluidStack getOutput() {
        return output.copy();
    }

    public int getTime() {
        return time;
    }

    public int getTemperature() {
        return temperature;
    }

    public List<FluidStack> getByproducts() {
        return byproducts;
    }

    public int getByproductsTotal() {
        int total = 0;
        for (FluidStack stack : byproducts) {
            total += stack.getAmount();
        }
        return total;
    }

    public void fillByproducts(FoundryFluidTank tank) {
        for (FluidStack stack : byproducts) {
            tank.fill(stack, false);
        }
    }

    public static class Serializer implements RecipeSerializer<FoundryMeltingRecipe> {
        public static final MapCodec<FoundryMeltingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Objects.requireNonNull(Ingredient.CODEC_NONEMPTY).fieldOf("ingredient").forGetter(FoundryMeltingRecipe::getIngredient),
                Objects.requireNonNull(FoundryCodecs.FLUID_STACK_CODEC).fieldOf("result").forGetter(FoundryMeltingRecipe::getOutput),
                Codec.INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(FoundryMeltingRecipe::getTime),
                Codec.INT.optionalFieldOf("temperature", 0).forGetter(FoundryMeltingRecipe::getTemperature),
                FoundryCodecs.FLUID_STACK_CODEC.listOf().optionalFieldOf("byproducts", List.of()).forGetter(FoundryMeltingRecipe::getByproducts)
            ).apply(instance, (ingredient, result, time, temperature, byproducts) ->
                new FoundryMeltingRecipe(ingredient, result, time, temperature, byproducts))
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, FoundryMeltingRecipe> STREAM_CODEC =
            StreamCodec.composite(
                Objects.requireNonNull(Ingredient.CONTENTS_STREAM_CODEC), FoundryMeltingRecipe::getIngredient,
                Objects.requireNonNull(FoundryCodecs.FLUID_STACK_STREAM_CODEC), FoundryMeltingRecipe::getOutput,
                ByteBufCodecs.INT, FoundryMeltingRecipe::getTime,
                ByteBufCodecs.INT, FoundryMeltingRecipe::getTemperature,
                FoundryCodecs.FLUID_STACK_STREAM_CODEC.apply(ByteBufCodecs.list()), FoundryMeltingRecipe::getByproducts,
                FoundryMeltingRecipe::new
            );

        @Override
        public MapCodec<FoundryMeltingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FoundryMeltingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
