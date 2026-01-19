package com.devmod.foundry.tool;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Base item for foundry armor pieces.
 */
public class FoundryArmorItem extends ArmorItem {
    private final Holder<ArmorMaterial> materialHolder;

    public FoundryArmorItem(Holder<ArmorMaterial> material, Type type) {
        super(material, type, new Properties().stacksTo(1));
        this.materialHolder = material;
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("tooltip.devmod.foundry.durability", stack.getMaxDamage()));
        ItemAttributeModifiers attributes = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (attributes != null) {
            double armor = sumAttribute(attributes, Attributes.ARMOR.value());
            double toughness = sumAttribute(attributes, Attributes.ARMOR_TOUGHNESS.value());
            double knockback = sumAttribute(attributes, Attributes.KNOCKBACK_RESISTANCE.value());
            tooltip.add(Component.translatable("tooltip.devmod.foundry.armor", String.format(java.util.Locale.ROOT, "%.2f", armor)));
            tooltip.add(Component.translatable("tooltip.devmod.foundry.toughness", String.format(java.util.Locale.ROOT, "%.2f", toughness)));
            tooltip.add(Component.translatable("tooltip.devmod.foundry.knockback_resistance", String.format(java.util.Locale.ROOT, "%.2f", knockback)));
        }
        // Show set bonus for slime armor
        if (materialHolder == ArmorMaterials.CHAIN) {
            tooltip.add(Component.translatable("tooltip.devmod.foundry.set_bonus").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.devmod.foundry.slime_set_bonus").withStyle(ChatFormatting.GRAY));
        }
        FoundryToolData.fromStack(stack).ifPresent(data -> {
            tooltip.add(Component.translatable("tooltip.devmod.foundry.level", data.level()));
            int xpNeeded = FoundryToolLeveling.getXpForNextLevel(data.level());
            tooltip.add(Component.translatable("tooltip.devmod.foundry.xp", data.xp(), xpNeeded));
            tooltip.add(Component.translatable("tooltip.devmod.foundry.materials", data.materials().size()));
            if (!data.modifiers().isEmpty()) {
                tooltip.add(Component.translatable("tooltip.devmod.foundry.modifiers", data.modifiers().size()));
            }
            FoundryToolDefinition definition = FoundryToolDefinitionRegistry.get(data.toolId());
            if (definition != null) {
                FoundryToolSlots.SlotUsage usage = FoundryToolSlots.calculate(definition, data);
                tooltip.add(Component.translatable("tooltip.devmod.foundry.slots_upgrade",
                    usage.usedUpgrades(), usage.totalUpgrades()));
                tooltip.add(Component.translatable("tooltip.devmod.foundry.slots_ability",
                    usage.usedAbilities(), usage.totalAbilities()));
            }
            if (data.embossment() != null) {
                tooltip.add(Component.translatable("tooltip.devmod.foundry.embossment", data.embossment().getPath()));
            }
        });
    }

    private static double sumAttribute(ItemAttributeModifiers modifiers, Attribute attribute) {
        double total = 0.0;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().value() == attribute) {
                total += entry.modifier().amount();
            }
        }
        return total;
    }
}
