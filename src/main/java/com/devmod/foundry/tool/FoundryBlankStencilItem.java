package com.devmod.foundry.tool;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Blank stencil item that can be carved into specific patterns
 * at the Stencil Table. This is the base material for all patterns.
 */
public class FoundryBlankStencilItem extends Item {

    public FoundryBlankStencilItem() {
        super(new Properties().stacksTo(64));
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag
    ) {
        tooltip.add(Objects.requireNonNull(
            Component.translatable("item.devmod.foundry_blank_stencil.tooltip")
                .withStyle(ChatFormatting.GRAY)
        ));
    }
}
