package com.devmod.foundry.tool;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Foundry bow with dynamic stats based on materials.
 * Supports draw_speed and projectile_speed from tool stats.
 */
public class FoundryBowItem extends BowItem {
    private final FoundryToolKind kind;

    /** Base draw time in ticks (vanilla = 20 ticks for full power) */
    private static final float BASE_DRAW_TIME = 20.0f;
    /** Base arrow velocity (vanilla = 3.0) */
    private static final float BASE_VELOCITY = 3.0f;

    public FoundryBowItem(FoundryToolKind kind) {
        super(new Item.Properties().stacksTo(1).durability(384));
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
     * Higher draw_speed = faster drawing.
     */
    public float getDrawSpeedMultiplier(ItemStack stack) {
        Optional<FoundryToolStats> stats = FoundryToolingEvents.getToolStats(stack);
        if (stats.isEmpty()) {
            return 1.0f;
        }
        // drawSpeed is a bonus, so 0.1 = 10% faster draw
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
     * Calculate the effective draw progress considering draw speed bonus.
     */
    public float getDrawProgress(ItemStack stack, int useTicks) {
        float drawSpeed = getDrawSpeedMultiplier(stack);
        float effectiveDrawTime = BASE_DRAW_TIME / drawSpeed;
        return Math.min(1.0f, useTicks / effectiveDrawTime);
    }

    @Override
    public void releaseUsing(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }

        ItemStack ammo = player.getProjectile(stack);
        if (ammo.isEmpty()) {
            return;
        }

        int useDuration = this.getUseDuration(stack, entity) - timeLeft;
        useDuration = EventHooks.onArrowLoose(stack, level, player, useDuration, !ammo.isEmpty());
        if (useDuration < 0) {
            return;
        }

        // Calculate power with draw speed bonus
        float drawProgress = getDrawProgress(stack, useDuration);
        if (drawProgress < 0.1f) {
            return;
        }

        // Calculate velocity with projectile speed multiplier
        float velocity = BASE_VELOCITY * drawProgress * getProjectileSpeedMultiplier(stack);

        boolean isCreative = player.getAbilities().instabuild;
        boolean isInfinite = isCreative || (ammo.getItem() instanceof ArrowItem &&
            ((ArrowItem) ammo.getItem()).isInfinite(ammo, stack, player));

        if (!level.isClientSide) {
            ArrowItem arrowItem = ammo.getItem() instanceof ArrowItem ? (ArrowItem) ammo.getItem() : (ArrowItem) Items.ARROW.asItem();
            AbstractArrow arrow = arrowItem.createArrow(level, ammo, player, stack);

            arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, velocity, 1.0f);

            if (drawProgress >= 1.0f) {
                arrow.setCritArrow(true);
            }

            // Apply enchantments
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(entity.getUsedItemHand()));

            if (isInfinite) {
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }

            level.addFreshEntity(arrow);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
            1.0f, 1.0f / (level.getRandom().nextFloat() * 0.4f + 1.2f) + drawProgress * 0.5f);

        if (!isInfinite && !isCreative) {
            ammo.shrink(1);
            if (ammo.isEmpty()) {
                player.getInventory().removeItem(ammo);
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag
    ) {
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
