package com.devmod.clone.recipe;

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

/**
 * Recipe for the Clone Pulverizer machine.
 * Converts items into crushed/pulverized versions.
 */
public class PulverizingRecipe implements Recipe<SingleRecipeInput> {

    public static final int DEFAULT_PROCESSING_TIME = 100; // 5 seconds at 20 tps

    private final Ingredient ingredient;
    private final ItemStack result;
    private final int processingTime;

    public PulverizingRecipe(Ingredient ingredient, ItemStack result, int processingTime) {
        this.ingredient = ingredient;
        this.result = result;
        this.processingTime = processingTime;
    }

    public PulverizingRecipe(Ingredient ingredient, ItemStack result) {
        this(ingredient, result, DEFAULT_PROCESSING_TIME);
    }

    @Override
    public boolean matches(@Nonnull SingleRecipeInput input, @Nonnull Level level) {
        return ingredient.test(input.getItem(0));
    }

    @Override
    @Nonnull
    public ItemStack assemble(@Nonnull SingleRecipeInput input, @Nonnull HolderLookup.Provider registries) {
        return Objects.requireNonNull(result.copy());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    @Nonnull
    public ItemStack getResultItem(@Nonnull HolderLookup.Provider registries) {
        return Objects.requireNonNull(result.copy());
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(ingredient);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return CloneRecipeTypes.PULVERIZING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return CloneRecipeTypes.PULVERIZING.get();
    }

    // Getters for the block entity
    public Ingredient getIngredient() {
        return ingredient;
    }

    public ItemStack getResult() {
        return result.copy();
    }

    public int getProcessingTime() {
        return processingTime;
    }

    /**
     * Serializer for pulverizing recipes.
     * Handles JSON parsing and network sync.
     */
    public static class Serializer implements RecipeSerializer<PulverizingRecipe> {

        // Codec for JSON deserialization
        public static final MapCodec<PulverizingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Objects.requireNonNull(Ingredient.CODEC_NONEMPTY).fieldOf("ingredient").forGetter(PulverizingRecipe::getIngredient),
                        Objects.requireNonNull(ItemStack.STRICT_CODEC).fieldOf("result").forGetter(r -> r.result),
                        Objects.requireNonNull(Codec.INT).optionalFieldOf("processing_time", DEFAULT_PROCESSING_TIME)
                                .forGetter(PulverizingRecipe::getProcessingTime)
                ).apply(instance, (ing, res, time) -> new PulverizingRecipe(ing, res, time.intValue()))
        );

        // Stream codec for network sync
        public static final StreamCodec<RegistryFriendlyByteBuf, PulverizingRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Objects.requireNonNull(Ingredient.CONTENTS_STREAM_CODEC), PulverizingRecipe::getIngredient,
                        Objects.requireNonNull(ItemStack.STREAM_CODEC), r -> r.result,
                        Objects.requireNonNull(ByteBufCodecs.INT), PulverizingRecipe::getProcessingTime,
                        (ing, res, time) -> new PulverizingRecipe(ing, res, time.intValue())
                );

        @Override
        public MapCodec<PulverizingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PulverizingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
