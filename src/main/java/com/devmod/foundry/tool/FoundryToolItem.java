package com.devmod.foundry.tool;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;

import net.neoforged.neoforge.common.ItemAbility;
import com.devmod.foundry.tool.item.IModifiable;

/**
 * Base item for foundry tools.
 */
public class FoundryToolItem extends Item implements IModifiable {
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

        // Display extended stats (crit, reach)
        Optional<FoundryToolStats> statsOpt = FoundryToolingEvents.getToolStats(stack);
        if (statsOpt.isPresent()) {
            FoundryToolStats stats = statsOpt.get();
            if (stats.reach() != 0.0f) {
                tooltip.add(Component.translatable("tooltip.devmod.foundry.reach",
                    String.format(java.util.Locale.ROOT, "%.1f", stats.reach())));
            }
            if (stats.critChance() > 0.0f) {
                tooltip.add(Component.translatable("tooltip.devmod.foundry.crit_chance",
                    String.format(java.util.Locale.ROOT, "%.0f", stats.critChance() * 100)));
            }
            if (stats.critDamage() > 1.5f) {
                tooltip.add(Component.translatable("tooltip.devmod.foundry.crit_damage",
                    String.format(java.util.Locale.ROOT, "%.2f", stats.critDamage())));
            }
        }

        FoundryToolData.fromStack(stack).ifPresent(data -> {
            tooltip.add(data.quality().getColoredDisplayName());
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
}
