package com.devmod.foundry.compat.jei;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.foundry.FoundryItems;
import com.devmod.foundry.recipe.FoundryMeltingRecipe;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;

/**
 * JEI category for Foundry melting recipes.
 */
public class FoundryMeltingCategory implements IRecipeCategory<FoundryMeltingRecipe> {
    private static final int WIDTH = 140;
    private static final int HEIGHT = 60;

    private final IDrawable icon;

    public FoundryMeltingCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(new ItemStack(FoundryItems.FOUNDRY_CONTROLLER_ITEM.get()));
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<FoundryMeltingRecipe> getRecipeType() {
        return FoundryJeiTypes.MELTING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.devmod.foundry.melting");
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
    public void setRecipe(IRecipeLayoutBuilder builder, FoundryMeltingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 6, 22)
            .addIngredients(recipe.getIngredient());

        FluidStack output = recipe.getOutput();
        builder.addSlot(RecipeIngredientRole.OUTPUT, 60, 12)
            .addIngredient(NeoForgeTypes.FLUID_STACK, output)
            .setFluidRenderer(Math.max(output.getAmount(), 1000), true, 16, 32);

        List<FluidStack> byproducts = recipe.getByproducts();
        int startX = 86;
        int startY = 12;
        int perRow = 2;
        for (int i = 0; i < byproducts.size(); i++) {
            FluidStack byproduct = byproducts.get(i);
            int x = startX + (i % perRow) * 18;
            int y = startY + (i / perRow) * 18;
            builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                .addIngredient(NeoForgeTypes.FLUID_STACK, byproduct)
                .setFluidRenderer(Math.max(byproduct.getAmount(), 1000), true, 16, 16);
        }
    }

    @Override
    public void draw(FoundryMeltingRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font,
            Component.translatable("jei.devmod.foundry.temperature", recipe.getTemperature()),
            6, 4, DesignTokens.Foundry.Jei.TEXT, false);
        int impurityPercent = Math.round(recipe.getImpurity() * 100f);
        if (impurityPercent > 0) {
            graphics.drawString(font,
                Component.translatable("jei.devmod.foundry.impurity", impurityPercent),
                6, 16, DesignTokens.Foundry.Jei.TEXT, false);
        }
        graphics.drawString(font,
            Component.translatable("jei.devmod.foundry.time", recipe.getTime()),
            6, 46, DesignTokens.Foundry.Jei.TEXT, false);
    }
}
