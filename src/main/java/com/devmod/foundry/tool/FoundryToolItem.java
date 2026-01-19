package com.devmod.foundry.tool;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.tooltip.TooltipContext;

import net.neoforged.neoforge.common.ItemAbility;

/**
 * Base item for foundry tools.
 */
public class FoundryToolItem extends Item {
    private final FoundryToolKind kind;

    public FoundryToolItem(FoundryToolKind kind) {
        super(new Item.Properties().stacksTo(1));
        this.kind = kind;
    }

    public FoundryToolKind getKind() {
        return kind;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        return kind.getAbilities().contains(itemAbility);
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("tooltip.devmod.foundry.durability", stack.getMaxDamage()));
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool != null) {
            tooltip.add(Component.translatable("tooltip.devmod.foundry.mining_speed", String.format(java.util.Locale.ROOT, "%.2f", tool.defaultMiningSpeed())));
        }
        ItemAttributeModifiers attributes = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (attributes != null) {
            double attack = attributes.compute(0.0, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            tooltip.add(Component.translatable("tooltip.devmod.foundry.attack_damage", String.format(java.util.Locale.ROOT, "%.2f", attack)));
        }
        FoundryToolData.fromStack(stack).ifPresent(data -> {
            tooltip.add(Component.translatable("tooltip.devmod.foundry.materials", data.materials().size()));
            if (!data.modifiers().isEmpty()) {
                tooltip.add(Component.translatable("tooltip.devmod.foundry.modifiers", data.modifiers().size()));
            }
        });
    }
}
