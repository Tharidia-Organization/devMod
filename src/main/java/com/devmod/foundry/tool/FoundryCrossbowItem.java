package com.devmod.foundry.tool;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import net.neoforged.neoforge.common.ItemAbility;

/**
 * Foundry crossbow with dynamic stats based on materials.
 * Supports draw_speed and projectile_speed from tool stats.
 */
public class FoundryCrossbowItem extends CrossbowItem {
    private final FoundryToolKind kind;

    /** Base charge time in ticks (vanilla = 25 ticks) */
    private static final int BASE_CHARGE_TIME = 25;

    public FoundryCrossbowItem(FoundryToolKind kind) {
        super(new Item.Properties().stacksTo(1).durability(465));
        this.kind = kind;
    }

    public FoundryToolKind getKind() {
        return kind;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        return kind.getAbilities().contains(itemAbility);
    }

    /**
     * Gets the draw speed multiplier from tool stats.
     * Higher draw_speed = faster charging.
     */
    public float getDrawSpeedMultiplier(ItemStack stack) {
        Optional<FoundryToolStats> stats = FoundryToolingEvents.getToolStats(stack);
        if (stats.isEmpty()) {
            return 1.0f;
        }
        // drawSpeed is a bonus, so 0.1 = 10% faster charge
        return 1.0f + stats.get().drawSpeed();
    }

    /**
     * Gets the projectile speed multiplier from tool stats.
     */
    public float getProjectileSpeedMultiplier(ItemStack stack) {
        Optional<FoundryToolStats> stats = FoundryToolingEvents.getToolStats(stack);
        if (stats.isEmpty()) {
            return 1.0f;
        }
        return stats.get().projectileSpeed();
    }

    /**
     * Override to apply draw speed bonus to charge duration.
     */
    @Override
    public int getUseDuration(@Nonnull ItemStack stack, @Nonnull LivingEntity entity) {
        // Return a large value; the actual charge progress is calculated separately
        return getChargeDuration(stack) + 3;
    }

    /**
     * Calculate the effective charge duration with draw speed bonus.
     */
    public int getChargeDuration(ItemStack stack) {
        float drawSpeed = getDrawSpeedMultiplier(stack);
        return Math.max(1, Math.round(BASE_CHARGE_TIME / drawSpeed));
    }

    /**
     * Get projectile velocity multiplier for shooting.
     * This is used by event handlers to modify projectile speed.
     */
    public float getShootingVelocity(ItemStack stack) {
        // Base crossbow velocity is 3.15, multiply by our stat
        return 3.15f * getProjectileSpeedMultiplier(stack);
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.devmod.foundry.durability", stack.getMaxDamage()));

        // Show draw speed and projectile speed bonuses
        float drawSpeed = getDrawSpeedMultiplier(stack);
        float projSpeed = getProjectileSpeedMultiplier(stack);
        if (drawSpeed != 1.0f) {
            tooltip.add(Component.translatable("tooltip.devmod.foundry.draw_speed",
                String.format(java.util.Locale.ROOT, "%.0f%%", drawSpeed * 100)));
        }
        if (projSpeed != 1.0f) {
            tooltip.add(Component.translatable("tooltip.devmod.foundry.projectile_speed",
                String.format(java.util.Locale.ROOT, "%.0f%%", projSpeed * 100)));
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
        });
    }
}
