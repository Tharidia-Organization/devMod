package com.devmod.foundry.tool;

import javax.annotation.Nonnull;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.tooltip.TooltipContext;

/**
 * Pattern item used by the Part Builder.
 */
public class FoundryPatternItem extends Item {
    private final FoundryPartType partType;

    public FoundryPatternItem(FoundryPartType partType) {
        super(new Item.Properties().stacksTo(16));
        this.partType = partType;
    }

    public FoundryPartType getPartType() {
        return partType;
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull java.util.List<Component> tooltip,
        @Nonnull TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("tooltip.devmod.foundry.pattern", partType.id().getPath()));
    }
}
