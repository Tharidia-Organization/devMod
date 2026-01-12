package com.devmod.clone.recipe;

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
            DeferredRegister.create(Registries.RECIPE_TYPE, DevMod.MODID);

    // Recipe Serializers Registry
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, DevMod.MODID);

    // Pulverizing Recipe Type
    public static final DeferredHolder<RecipeType<?>, RecipeType<PulverizingRecipe>> PULVERIZING =
            RECIPE_TYPES.register("pulverizing", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return "devmod:pulverizing";
                }
            });

    // Pulverizing Recipe Serializer
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PulverizingRecipe>> PULVERIZING_SERIALIZER =
            RECIPE_SERIALIZERS.register("pulverizing", PulverizingRecipe.Serializer::new);

    /**
     * Register all recipe types and serializers.
     * Call from CloneModule.init().
     */
    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        DevMod.LOGGER.info("[Clone] Recipe types registered");
    }
}
