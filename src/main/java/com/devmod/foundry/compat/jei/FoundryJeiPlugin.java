package com.devmod.foundry.compat.jei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.DevMod;
import com.devmod.foundry.FoundryItems;
import com.devmod.foundry.FoundryRecipeTypes;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

/**
 * JEI plugin for Foundry recipes.
 */
@OnlyIn(Dist.CLIENT)
@JeiPlugin
public class FoundryJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        IGuiHelper guiHelper = registry.getJeiHelpers().getGuiHelper();
        registry.addRecipeCategories(
            new FoundryMeltingCategory(guiHelper),
            new FoundryAlloyingCategory(guiHelper),
            new FoundryCastingCategory(
                guiHelper,
                FoundryJeiTypes.CASTING_TABLE,
                net.minecraft.network.chat.Component.translatable("jei.devmod.foundry.casting_table"),
                new net.minecraft.world.item.ItemStack(FoundryItems.FOUNDRY_CASTING_TABLE_ITEM.get())
            ),
            new FoundryCastingCategory(
                guiHelper,
                FoundryJeiTypes.CASTING_BASIN,
                net.minecraft.network.chat.Component.translatable("jei.devmod.foundry.casting_basin"),
                new net.minecraft.world.item.ItemStack(FoundryItems.FOUNDRY_CASTING_BASIN_ITEM.get())
            ),
            new FoundryFuelCategory(guiHelper)
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RecipeManager manager = level.getRecipeManager();
        registration.addRecipes(FoundryJeiTypes.MELTING, getRecipes(manager, FoundryRecipeTypes.MELTING.get()));
        registration.addRecipes(FoundryJeiTypes.ALLOYING, getRecipes(manager, FoundryRecipeTypes.ALLOYING.get()));
        registration.addRecipes(FoundryJeiTypes.CASTING_TABLE, getRecipes(manager, FoundryRecipeTypes.CASTING_TABLE.get()));
        registration.addRecipes(FoundryJeiTypes.CASTING_BASIN, getRecipes(manager, FoundryRecipeTypes.CASTING_BASIN.get()));
        registration.addRecipes(FoundryJeiTypes.FUEL, getRecipes(manager, FoundryRecipeTypes.FUEL.get()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(FoundryItems.FOUNDRY_CONTROLLER_ITEM.get(),
            FoundryJeiTypes.MELTING, FoundryJeiTypes.ALLOYING, FoundryJeiTypes.FUEL);
        registration.addRecipeCatalyst(FoundryItems.FOUNDRY_CASTING_TABLE_ITEM.get(), FoundryJeiTypes.CASTING_TABLE);
        registration.addRecipeCatalyst(FoundryItems.FOUNDRY_CASTING_BASIN_ITEM.get(), FoundryJeiTypes.CASTING_BASIN);
        registration.addRecipeCatalyst(FoundryItems.FOUNDRY_FAUCET_ITEM.get(),
            FoundryJeiTypes.CASTING_TABLE, FoundryJeiTypes.CASTING_BASIN);
    }

    private static <I extends RecipeInput, T extends Recipe<I>> List<T> getRecipes(RecipeManager manager, RecipeType<T> type) {
        return manager.getAllRecipesFor(type).stream()
            .map(RecipeHolder::value)
            .toList();
    }
}
