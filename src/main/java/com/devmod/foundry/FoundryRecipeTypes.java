package com.devmod.foundry;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.foundry.recipe.FoundryAlloyingRecipe;
import com.devmod.foundry.recipe.FoundryCastingRecipe;
import com.devmod.foundry.recipe.FoundryFuelRecipe;
import com.devmod.foundry.recipe.FoundryMeltingRecipe;

/**
 * Registry for Foundry recipe types and serializers.
 */
public final class FoundryRecipeTypes {
    private FoundryRecipeTypes() {}

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
        DeferredRegister.create(Objects.requireNonNull(Registries.RECIPE_TYPE), DevMod.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Objects.requireNonNull(Registries.RECIPE_SERIALIZER), DevMod.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<FoundryMeltingRecipe>> MELTING =
        RECIPE_TYPES.register("foundry_melting", () -> new RecipeType<FoundryMeltingRecipe>() {
            @Override
            public String toString() {
                return "devmod:foundry_melting";
            }
        });

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FoundryMeltingRecipe>> MELTING_SERIALIZER =
        RECIPE_SERIALIZERS.register("foundry_melting", FoundryMeltingRecipe.Serializer::new);

    public static final DeferredHolder<RecipeType<?>, RecipeType<FoundryAlloyingRecipe>> ALLOYING =
        RECIPE_TYPES.register("foundry_alloying", () -> new RecipeType<FoundryAlloyingRecipe>() {
            @Override
            public String toString() {
                return "devmod:foundry_alloying";
            }
        });

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FoundryAlloyingRecipe>> ALLOYING_SERIALIZER =
        RECIPE_SERIALIZERS.register("foundry_alloying", FoundryAlloyingRecipe.Serializer::new);

    public static final DeferredHolder<RecipeType<?>, RecipeType<FoundryFuelRecipe>> FUEL =
        RECIPE_TYPES.register("foundry_fuel", () -> new RecipeType<FoundryFuelRecipe>() {
            @Override
            public String toString() {
                return "devmod:foundry_fuel";
            }
        });

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FoundryFuelRecipe>> FUEL_SERIALIZER =
        RECIPE_SERIALIZERS.register("foundry_fuel", FoundryFuelRecipe.Serializer::new);

    public static final DeferredHolder<RecipeType<?>, RecipeType<FoundryCastingRecipe>> CASTING_TABLE =
        RECIPE_TYPES.register("foundry_casting_table", () -> new RecipeType<FoundryCastingRecipe>() {
            @Override
            public String toString() {
                return "devmod:foundry_casting_table";
            }
        });

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FoundryCastingRecipe>> CASTING_TABLE_SERIALIZER =
        RECIPE_SERIALIZERS.register("foundry_casting_table", () -> new FoundryCastingRecipe.Serializer(() -> CASTING_TABLE.get()));

    public static final DeferredHolder<RecipeType<?>, RecipeType<FoundryCastingRecipe>> CASTING_BASIN =
        RECIPE_TYPES.register("foundry_casting_basin", () -> new RecipeType<FoundryCastingRecipe>() {
            @Override
            public String toString() {
                return "devmod:foundry_casting_basin";
            }
        });

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FoundryCastingRecipe>> CASTING_BASIN_SERIALIZER =
        RECIPE_SERIALIZERS.register("foundry_casting_basin", () -> new FoundryCastingRecipe.Serializer(() -> CASTING_BASIN.get()));

    public static void register(@Nonnull IEventBus modEventBus) {
        RECIPE_TYPES.register(Objects.requireNonNull(modEventBus));
        RECIPE_SERIALIZERS.register(Objects.requireNonNull(modEventBus));
        DevMod.LOGGER.info("[Foundry] Recipe types registered");
    }
}
