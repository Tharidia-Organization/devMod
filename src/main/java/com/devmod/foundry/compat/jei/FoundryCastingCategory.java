package com.devmod.foundry.compat.jei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.foundry.recipe.FoundryCastingRecipe;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

/**
 * JEI category for Foundry casting recipes.
 */
public class FoundryCastingCategory implements IRecipeCategory<FoundryCastingRecipe> {
    private static final int WIDTH = 140;
    private static final int HEIGHT = 60;

    private final IDrawable icon;
    private final RecipeType<FoundryCastingRecipe> recipeType;
    private final Component title;

    public FoundryCastingCategory(
        IGuiHelper guiHelper,
        RecipeType<FoundryCastingRecipe> recipeType,
        Component title,
        ItemStack iconStack
    ) {
        this.icon = guiHelper.createDrawableItemStack(iconStack);
        this.recipeType = recipeType;
        this.title = title;
    }

    @Override
    public RecipeType<FoundryCastingRecipe> getRecipeType() {
        return recipeType;
    }

    @Override
    public Component getTitle() {
        return title;
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
    public void setRecipe(IRecipeLayoutBuilder builder, FoundryCastingRecipe recipe, IFocusGroup focuses) {
        if (!recipe.getCast().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 6, 22)
                .addIngredients(recipe.getCast());
        }

        FluidStack fluid = recipe.getFluid();
        builder.addSlot(RecipeIngredientRole.INPUT, 40, 12)
            .addIngredient(NeoForgeTypes.FLUID_STACK, fluid)
            .setFluidRenderer(Math.max(fluid.getAmount(), 1000), true, 16, 32);

        ItemStack result = ItemStack.EMPTY;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            result = recipe.getResultItem(level.registryAccess());
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 100, 22)
            .addItemStack(result);
    }

    @Override
    public void draw(FoundryCastingRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font,
            Component.translatable("jei.devmod.foundry.cooling_time", recipe.getCoolingTime()),
            6, 4, DesignTokens.Foundry.Jei.TEXT, false);
        Component castNote = Component.translatable(recipe.isConsumeCast()
            ? "jei.devmod.foundry.cast_consumed"
            : "jei.devmod.foundry.cast_kept");
        graphics.drawString(font, castNote, 6, 46, DesignTokens.Foundry.Jei.TEXT, false);
    }
}
