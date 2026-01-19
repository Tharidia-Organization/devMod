package com.devmod.foundry.compat.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.foundry.FoundryItems;
import com.devmod.foundry.recipe.FoundryFuelRecipe;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;

/**
 * JEI category for Foundry fuel recipes.
 */
public class FoundryFuelCategory implements IRecipeCategory<FoundryFuelRecipe> {
    private static final int WIDTH = 120;
    private static final int HEIGHT = 50;

    private final IDrawable icon;

    public FoundryFuelCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(new ItemStack(FoundryItems.FOUNDRY_CONTROLLER_ITEM.get()));
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<FoundryFuelRecipe> getRecipeType() {
        return FoundryJeiTypes.FUEL;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.devmod.foundry.fuel");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FoundryFuelRecipe recipe, IFocusGroup focuses) {
        FluidStack fluid = recipe.getFluid();
        builder.addSlot(RecipeIngredientRole.INPUT, 6, 10)
            .addIngredient(NeoForgeTypes.FLUID_STACK, fluid)
            .setFluidRenderer(Math.max(fluid.getAmount(), 1000), true, 16, 32);
    }

    @Override
    public void draw(FoundryFuelRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font,
            Component.translatable("jei.devmod.foundry.temperature", recipe.getTemperature()),
            28, 8, DesignTokens.Foundry.Jei.TEXT, false);
        graphics.drawString(font,
            Component.translatable("jei.devmod.foundry.burn_time", recipe.getBurnTime()),
            28, 24, DesignTokens.Foundry.Jei.TEXT, false);
    }
}
