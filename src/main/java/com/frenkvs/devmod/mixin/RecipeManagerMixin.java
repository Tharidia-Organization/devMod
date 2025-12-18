package com.frenkvs.devmod.mixin;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.recipe.RecipeInjector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Mixin to inject custom recipes into RecipeManager lookups.
 * This is necessary because RecipeManager is mostly immutable after datapack loading.
 * We intercept recipe queries and supplement them with our custom recipes.
 */
@Mixin(RecipeManager.class)
public class RecipeManagerMixin {

    /**
     * Inject custom recipes into getAllRecipesFor query.
     * This is called when the game looks up all recipes of a specific type
     * (e.g., all crafting recipes for the recipe book).
     */
    @Inject(method = "getAllRecipesFor", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void devmod$injectAllRecipes(
            RecipeType<T> type,
            CallbackInfoReturnable<Collection<RecipeHolder<T>>> cir) {
        RecipeType<T> safeType = Objects.requireNonNull(type, "recipe type");
        List<RecipeHolder<T>> injected = RecipeInjector.getInjectedByType(safeType);
        if (!injected.isEmpty()) {
            // Create new list with both vanilla and custom recipes
            List<RecipeHolder<T>> combined = new ArrayList<>(cir.getReturnValue());
            combined.addAll(injected);
            cir.setReturnValue(combined);
        }
    }

    /**
     * Inject custom recipes into getRecipeFor query.
     * This is the main method called when checking if an input matches any recipe.
     */
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void devmod$injectRecipeFor(
            RecipeType<T> type,
            I input,
            Level level,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        RecipeType<T> safeType = Objects.requireNonNull(type, "recipe type");
        I safeInput = Objects.requireNonNull(input, "recipe input");
        Level safeLevel = Objects.requireNonNull(level, "level");
        // Check injected recipes first
        List<RecipeHolder<T>> injected = RecipeInjector.getInjectedByType(safeType);

        // Debug: log when crafting type is queried
        if (safeType == RecipeType.CRAFTING && !injected.isEmpty()) {
            DevMod.LOGGER.debug("[RecipeManagerMixin] Checking {} injected crafting recipes", injected.size());
        }

        for (RecipeHolder<T> holder : injected) {
            try {
                boolean matches = holder.value().matches(safeInput, safeLevel);
                if (safeType == RecipeType.CRAFTING) {
                    DevMod.LOGGER.debug("[RecipeManagerMixin] Recipe {} matches: {}", holder.id(), matches);
                }
                if (matches) {
                    DevMod.LOGGER.info("[RecipeManagerMixin] Found matching recipe: {}", holder.id());
                    cir.setReturnValue(Optional.of(holder));
                    return;
                }
            } catch (Exception e) {
                DevMod.LOGGER.error("[RecipeManagerMixin] Error checking recipe {}: {}", holder.id(), e.getMessage());
            }
        }
        // If no match in injected, let vanilla handle it
    }

    /**
     * Inject custom recipes into getRecipeFor with ResourceLocation hint.
     * This is called when the game has a specific recipe ID hint (e.g., from recipe book).
     */
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void devmod$injectRecipeForWithHint(
            RecipeType<T> type,
            I input,
            Level level,
            ResourceLocation lastRecipe,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        RecipeType<T> safeType = Objects.requireNonNull(type, "recipe type");
        I safeInput = Objects.requireNonNull(input, "recipe input");
        Level safeLevel = Objects.requireNonNull(level, "level");

        // First check if the hint points to an injected recipe
        if (lastRecipe != null && RecipeInjector.hasInjectedRecipe(lastRecipe)) {
            Optional<RecipeHolder<?>> injected = RecipeInjector.getInjectedRecipe(lastRecipe);
            if (injected.isPresent()) {
                RecipeHolder<T> holder = castRecipeHolder(injected.get());
                if (holder.value().matches(safeInput, safeLevel)) {
                    cir.setReturnValue(Optional.ofNullable(holder));
                    return;
                }
            }
        }

        // Check all injected recipes of this type
        List<RecipeHolder<T>> injectedList = RecipeInjector.getInjectedByType(safeType);
        for (RecipeHolder<T> holder : injectedList) {
            if (holder.value().matches(safeInput, safeLevel)) {
                cir.setReturnValue(Optional.ofNullable(holder));
                return;
            }
        }
        // If no match in injected, let vanilla handle it
    }

    /**
     * Type-cast helper for RecipeHolder. Safe when type has been validated.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> RecipeHolder<T> castRecipeHolder(RecipeHolder<?> holder) {
        return (RecipeHolder<T>) holder;
    }

    /**
     * Inject custom recipes into getRecipeFor with RecipeHolder hint.
     * This variant is used when there's a cached recipe holder from previous lookups.
     */
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void devmod$injectRecipeForWithHolder(
            RecipeType<T> type,
            I input,
            Level level,
            RecipeHolder<T> lastRecipeHolder,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        RecipeType<T> safeType = Objects.requireNonNull(type, "recipe type");
        I safeInput = Objects.requireNonNull(input, "recipe input");
        Level safeLevel = Objects.requireNonNull(level, "level");

        // First check if the hint is an injected recipe
        if (lastRecipeHolder != null && RecipeInjector.hasInjectedRecipe(lastRecipeHolder.id())) {
            try {
                if (lastRecipeHolder.value().matches(safeInput, safeLevel)) {
                    DevMod.LOGGER.debug("[RecipeManagerMixin] Hint matched injected recipe: {}", lastRecipeHolder.id());
                    cir.setReturnValue(Optional.of(lastRecipeHolder));
                    return;
                }
            } catch (Exception e) {
                DevMod.LOGGER.error("[RecipeManagerMixin] Error checking hint recipe: {}", e.getMessage());
            }
        }

        // Check all injected recipes of this type
        List<RecipeHolder<T>> injectedList = RecipeInjector.getInjectedByType(safeType);

        if (safeType == RecipeType.CRAFTING && !injectedList.isEmpty()) {
            DevMod.LOGGER.debug("[RecipeManagerMixin] (WithHolder) Checking {} injected crafting recipes", injectedList.size());
        }

        for (RecipeHolder<T> holder : injectedList) {
            try {
                boolean matches = holder.value().matches(safeInput, safeLevel);
                if (safeType == RecipeType.CRAFTING) {
                    DevMod.LOGGER.debug("[RecipeManagerMixin] (WithHolder) Recipe {} matches: {}", holder.id(), matches);
                }
                if (matches) {
                    DevMod.LOGGER.info("[RecipeManagerMixin] (WithHolder) Found matching recipe: {}", holder.id());
                    cir.setReturnValue(Optional.of(holder));
                    return;
                }
            } catch (Exception e) {
                DevMod.LOGGER.error("[RecipeManagerMixin] Error checking recipe {}: {}", holder.id(), e.getMessage());
            }
        }
        // If no match in injected, let vanilla handle it
    }

    /**
     * Intercept byKey lookup to include injected recipes.
     */
    @Inject(method = "byKey", at = @At("HEAD"), cancellable = true)
    private void devmod$injectByKey(
            ResourceLocation id,
            CallbackInfoReturnable<Optional<RecipeHolder<?>>> cir) {

        // Check if this ID is in our injected recipes
        Optional<RecipeHolder<?>> injected = RecipeInjector.getInjectedRecipe(id);
        if (injected.isPresent()) {
            cir.setReturnValue(injected);
        }
        // Otherwise let vanilla handle it
    }
}
