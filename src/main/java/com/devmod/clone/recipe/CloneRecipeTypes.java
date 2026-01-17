package com.devmod.clone.recipe;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;

/**
 * Registry for Clone module recipe types and serializers.
 */
public final class CloneRecipeTypes {
    private CloneRecipeTypes() {}

    // Recipe Types Registry
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Objects.requireNonNull(Registries.RECIPE_TYPE, "RECIPE_TYPE registry key"), DevMod.MODID);

    // Recipe Serializers Registry
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Objects.requireNonNull(Registries.RECIPE_SERIALIZER, "RECIPE_SERIALIZER registry key"), DevMod.MODID);

    // Pulverizing Recipe Type
    public static final DeferredHolder<RecipeType<?>, RecipeType<PulverizingRecipe>> PULVERIZING =
            RECIPE_TYPES.register("pulverizing", () -> new RecipeType<PulverizingRecipe>() {
                @Override
                public String toString() {
                    return "devmod:pulverizing";
                }
            });

    // Pulverizing Recipe Serializer
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PulverizingRecipe>> PULVERIZING_SERIALIZER =
            RECIPE_SERIALIZERS.register("pulverizing", PulverizingRecipe.Serializer::new);

    // Centrifuging Recipe Type
    public static final DeferredHolder<RecipeType<?>, RecipeType<CentrifugingRecipe>> CENTRIFUGING =
            RECIPE_TYPES.register("centrifuging", () -> new RecipeType<CentrifugingRecipe>() {
                @Override
                public String toString() {
                    return "devmod:centrifuging";
                }
            });

    // Centrifuging Recipe Serializer
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CentrifugingRecipe>> CENTRIFUGING_SERIALIZER =
            RECIPE_SERIALIZERS.register("centrifuging", CentrifugingRecipe.Serializer::new);

    /**
     * Register all recipe types and serializers.
     * Call from CloneModule.init().
     */
    public static void register(@Nonnull IEventBus modEventBus) {
        Objects.requireNonNull(modEventBus, "modEventBus");
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        DevMod.LOGGER.info("[Clone] Recipe types registered");
    }
}
