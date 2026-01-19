package com.devmod.clone.recipe;

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
 * Recipe for the Centrifuge machine.
 * Separates items into component parts via centrifugal force.
 */
public class CentrifugingRecipe implements Recipe<SingleRecipeInput> {

    public static final int DEFAULT_PROCESSING_TIME = 100; // 5 seconds at 20 tps

    private final Ingredient ingredient;
    private final ItemStack result;
    private final int processingTime;

    public CentrifugingRecipe(Ingredient ingredient, ItemStack result, int processingTime) {
        this.ingredient = ingredient;
        this.result = result;
        this.processingTime = processingTime;
    }

    public CentrifugingRecipe(Ingredient ingredient, ItemStack result) {
        this(ingredient, result, DEFAULT_PROCESSING_TIME);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.getItem(0));
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(ingredient);
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return CloneRecipeTypes.CENTRIFUGING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return CloneRecipeTypes.CENTRIFUGING.get();
    }

    @Override
    public boolean isSpecial() {
        // Machine-only recipe; keep it out of the recipe book.
        return true;
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
     * Serializer for centrifuging recipes.
     * Handles JSON parsing and network sync.
     */
    public static class Serializer implements RecipeSerializer<CentrifugingRecipe> {

        // Codec for JSON deserialization
        public static final MapCodec<CentrifugingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(CentrifugingRecipe::getIngredient),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
                        Codec.INT.optionalFieldOf("processing_time", DEFAULT_PROCESSING_TIME)
                                .forGetter(CentrifugingRecipe::getProcessingTime)
                ).apply(instance, CentrifugingRecipe::new)
        );

        // Stream codec for network sync
        public static final StreamCodec<RegistryFriendlyByteBuf, CentrifugingRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, CentrifugingRecipe::getIngredient,
                        ItemStack.STREAM_CODEC, r -> r.result,
                        ByteBufCodecs.INT, CentrifugingRecipe::getProcessingTime,
                        CentrifugingRecipe::new
                );

        @Override
        public MapCodec<CentrifugingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CentrifugingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
