package com.devmod.foundry.tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import net.neoforged.neoforge.common.ItemAbility;

import com.devmod.foundry.tool.modifier.FoundryModifierDefinition;
import com.devmod.foundry.tool.modifier.FoundryModifierRegistry;

/**
 * Foundry fishing rod with dynamic stats based on materials.
 * Extends vanilla FishingRodItem to inherit all fishing mechanics.
 *
 * Features:
 * - Material quality affects luck bonus
 * - Flexibility stat affects lure speed
 * - Modifiers can add special fishing abilities
 */
public class FoundryFishingRodItem extends FishingRodItem {
    private final FoundryToolKind kind;

    public FoundryFishingRodItem(FoundryToolKind kind) {
        super(new Item.Properties().stacksTo(1).durability(64));
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
     * Calculates luck bonus based on material quality and modifiers.
     */
    public int getLuckBonus(ItemStack stack) {
        Optional<FoundryToolData> dataOpt = FoundryToolData.fromStack(stack);
        if (dataOpt.isEmpty()) return 0;

        FoundryToolData data = dataOpt.get();
        int luck = 0;

        // Quality bonus: +1 luck per quality tier above COMMON
        luck += data.quality().ordinal();

        // Level bonus: +1 luck every 5 levels
        luck += data.level() / 5;

        // Modifier bonuses
        for (Map.Entry<ResourceLocation, Integer> mod : data.modifiers().entrySet()) {
            FoundryModifierDefinition def = FoundryModifierRegistry.get(mod.getKey());
            if (def != null && def.bonuses() != null) {
                // Check for luck-related bonuses
                if (def.id().getPath().contains("lucky") || def.id().getPath().contains("fortune")) {
                    luck += mod.getValue();
                }
            }
        }

        return Math.min(luck, 5); // Cap at +5 luck
    }

    /**
     * Calculates lure speed bonus based on material flexibility and modifiers.
     */
    public int getLureSpeedBonus(ItemStack stack) {
        Optional<FoundryToolData> dataOpt = FoundryToolData.fromStack(stack);
        if (dataOpt.isEmpty()) return 0;

        FoundryToolData data = dataOpt.get();
        int lureSpeed = 0;

        // Quality bonus: +1 lure speed per quality tier above STANDARD
        if (data.quality().ordinal() > 1) {
            lureSpeed += data.quality().ordinal() - 1;
        }

        // Level bonus: +1 lure speed every 10 levels
        lureSpeed += data.level() / 10;

        // Modifier bonuses
        for (Map.Entry<ResourceLocation, Integer> mod : data.modifiers().entrySet()) {
            FoundryModifierDefinition def = FoundryModifierRegistry.get(mod.getKey());
            if (def != null) {
                // Check for speed-related bonuses
                if (def.id().getPath().contains("quick") || def.id().getPath().contains("swift") || def.id().getPath().contains("lure")) {
                    lureSpeed += mod.getValue();
                }
            }
        }

        return Math.min(lureSpeed, 5); // Cap at +5 lure speed
    }

    /**
     * Checks if the rod has aquadynamic trait for water bonuses.
     */
    public boolean hasAquadynamic(ItemStack stack) {
        Optional<FoundryToolData> dataOpt = FoundryToolData.fromStack(stack);
        if (dataOpt.isEmpty()) return false;

        FoundryToolData data = dataOpt.get();
        for (ResourceLocation modId : data.modifiers().keySet()) {
            if (modId.getPath().contains("aquadynamic") ||
                modId.getPath().contains("aquatic") ||
                modId.getPath().contains("tidal")) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.fishing != null) {
            if (!level.isClientSide) {
                int retrieve = player.fishing.retrieve(stack);
                stack.hurtAndBreak(retrieve, player, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));

                // Award XP for successful catches
                if (retrieve > 0) {
                    FoundryToolData.fromStack(stack).ifPresent(data -> {
                        FoundryToolDefinition definition = FoundryToolDefinitionRegistry.get(data.toolId());
                        if (definition != null) {
                            int xpGain = retrieve * 5; // 5 XP per item caught
                            FoundryToolLeveling.addXp(stack, definition, data, xpGain);
                        }
                    });
                }
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
            player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));

            if (!level.isClientSide) {
                // Calculate bonuses from foundry data
                int luck = getLuckBonus(stack);
                int lureSpeed = getLureSpeedBonus(stack);

                // Add enchantment bonuses from the stack
                if (level instanceof ServerLevel serverLevel) {
                    luck += EnchantmentHelper.getFishingLuckBonus(serverLevel, stack, player);
                    lureSpeed += (int) EnchantmentHelper.getFishingTimeReduction(serverLevel, stack, player);
                }

                // Create fishing hook with bonuses
                FishingHook hook = new FishingHook(player, level, luck, lureSpeed);
                level.addFreshEntity(hook);
            }

            player.awardStat(Stats.ITEM_USED.get(this));
            player.gameEvent(GameEvent.ITEM_INTERACT_START);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("tooltip.devmod.foundry.durability", stack.getMaxDamage()));

        // Show fishing stats
        int luck = getLuckBonus(stack);
        int lure = getLureSpeedBonus(stack);
        if (luck > 0) {
            tooltip.add(Component.translatable("tooltip.devmod.foundry.fishing_luck", luck));
        }
        if (lure > 0) {
            tooltip.add(Component.translatable("tooltip.devmod.foundry.fishing_lure", lure));
        }
        if (hasAquadynamic(stack)) {
            tooltip.add(Component.translatable("tooltip.devmod.foundry.fishing_aquadynamic"));
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
