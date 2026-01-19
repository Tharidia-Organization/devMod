package com.devmod.foundry.recipe;

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
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Casting recipe for table/basin.
 */
public class FoundryCastingRecipe implements Recipe<FoundryCastingRecipe.CastingInput> {
    private final Ingredient cast;
    private final FluidStack fluid;
    private final ItemStack result;
    private final int coolingTime;
    private final boolean consumeCast;
    private final RecipeType<FoundryCastingRecipe> type;

    public FoundryCastingRecipe(
        Ingredient cast,
        FluidStack fluid,
        ItemStack result,
        int coolingTime,
        boolean consumeCast,
        RecipeType<FoundryCastingRecipe> type
    ) {
        this.cast = cast;
        this.fluid = fluid;
        this.result = result;
        this.coolingTime = coolingTime;
        this.consumeCast = consumeCast;
        this.type = type;
    }

    @Override
    public boolean matches(@Nonnull CastingInput input, @Nonnull Level level) {
        if (!cast.isEmpty() && !cast.test(input.cast)) {
            return false;
        }
        return FluidStack.isSameFluidSameComponents(input.fluid, fluid) && input.fluid.getAmount() > 0;
    }

    @Override
    @Nonnull
    public ItemStack assemble(@Nonnull CastingInput input, @Nonnull HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    @Nonnull
    public ItemStack getResultItem(@Nonnull HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        if (!cast.isEmpty()) {
            list.add(cast);
        }
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        if (type == com.devmod.foundry.FoundryRecipeTypes.CASTING_TABLE.get()) {
            return com.devmod.foundry.FoundryRecipeTypes.CASTING_TABLE_SERIALIZER.get();
        }
        return com.devmod.foundry.FoundryRecipeTypes.CASTING_BASIN_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return type;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public Ingredient getCast() {
        return cast;
    }

    public FluidStack getFluid() {
        return fluid.copy();
    }

    public int getCoolingTime() {
        return coolingTime;
    }

    public boolean isConsumeCast() {
        return consumeCast;
    }

    public static final int DEFAULT_COOLING_TIME = 100;

    public static class CastingInput implements net.minecraft.world.item.crafting.RecipeInput {
        private final ItemStack cast;
        private final FluidStack fluid;

        public CastingInput(ItemStack cast, FluidStack fluid) {
            this.cast = cast;
            this.fluid = fluid;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack getItem(int index) {
            return cast;
        }
    }

    public static class Serializer implements RecipeSerializer<FoundryCastingRecipe> {
        private final java.util.function.Supplier<RecipeType<FoundryCastingRecipe>> typeSupplier;

        public Serializer(java.util.function.Supplier<RecipeType<FoundryCastingRecipe>> typeSupplier) {
            this.typeSupplier = typeSupplier;
        }

        @Override
        public MapCodec<FoundryCastingRecipe> codec() {
            return RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                    Ingredient.CODEC.optionalFieldOf("cast", Ingredient.EMPTY).forGetter(FoundryCastingRecipe::getCast),
                    FoundryCodecs.FLUID_STACK_CODEC.fieldOf("fluid").forGetter(FoundryCastingRecipe::getFluid),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
                    Codec.INT.optionalFieldOf("cooling_time", DEFAULT_COOLING_TIME).forGetter(FoundryCastingRecipe::getCoolingTime),
                    Codec.BOOL.optionalFieldOf("consume_cast", false).forGetter(FoundryCastingRecipe::isConsumeCast)
                ).apply(instance, (cast, fluid, result, time, consume) ->
                    new FoundryCastingRecipe(cast, fluid, result, time, consume, Objects.requireNonNull(typeSupplier.get())))
            );
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FoundryCastingRecipe> streamCodec() {
            return StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, FoundryCastingRecipe::getCast,
                FoundryCodecs.FLUID_STACK_STREAM_CODEC, FoundryCastingRecipe::getFluid,
                ItemStack.STREAM_CODEC, r -> r.result,
                ByteBufCodecs.INT, FoundryCastingRecipe::getCoolingTime,
                ByteBufCodecs.BOOL, FoundryCastingRecipe::isConsumeCast,
                (cast, fluid, result, time, consume) -> new FoundryCastingRecipe(cast, fluid, result, time, consume, Objects.requireNonNull(typeSupplier.get()))
            );
        }
    }
}
