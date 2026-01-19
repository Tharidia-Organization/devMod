package com.devmod.foundry.tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import com.devmod.foundry.tool.modifier.FoundryModifierDefinition;
import com.devmod.foundry.tool.modifier.FoundryModifierRegistry;

/**
 * Foundry shield with dynamic stats based on materials.
 * Shield abilities are determined by modifiers:
 * - Thorned: Damages attackers when blocking
 * - Reflective: Reflects projectiles
 * - Fortified: Increased knockback resistance
 * - Aegis: Grants resistance effect when blocking
 * - Reactive: Bonus counter-attack damage after blocking
 * - Phalanx: Nearby allies gain protection
 * - Default: Standard blocking
 */
public class FoundryShieldItem extends Item {
    private final FoundryToolKind kind;
    private static final int BASH_COOLDOWN_TICKS = 40;
    private static final float BASH_DAMAGE = 3.0f;
    private static final double BASH_KNOCKBACK = 1.5;

    public enum ShieldAbility {
        STANDARD("standard"),
        THORNED("thorned"),
        REFLECTIVE("reflective"),
        FORTIFIED("fortified"),
        AEGIS("aegis"),
        REACTIVE("reactive"),
        PHALANX("phalanx");

        private final String id;

        ShieldAbility(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public Component getDisplayName() {
            return Component.translatable("foundry.shield.ability." + id);
        }
    }

    public FoundryShieldItem(FoundryToolKind kind) {
        super(new Item.Properties().stacksTo(1).durability(336));
        this.kind = kind;
    }

    public FoundryToolKind getKind() {
        return kind;
    }

    /**
     * Determines the shield's primary ability based on modifiers.
     */
    public ShieldAbility getPrimaryAbility(ItemStack stack) {
        Optional<FoundryToolData> dataOpt = FoundryToolData.fromStack(stack);
        if (dataOpt.isEmpty()) return ShieldAbility.STANDARD;

        FoundryToolData data = dataOpt.get();
        for (ResourceLocation modId : data.modifiers().keySet()) {
            String path = modId.getPath();
            if (path.contains("thorned") || path.contains("spiky") || path.contains("barbed")) {
                return ShieldAbility.THORNED;
            }
            if (path.contains("reflective") || path.contains("mirror") || path.contains("deflecting")) {
                return ShieldAbility.REFLECTIVE;
            }
            if (path.contains("fortified") || path.contains("sturdy") || path.contains("immovable")) {
                return ShieldAbility.FORTIFIED;
            }
            if (path.contains("aegis") || path.contains("warding") || path.contains("blessed")) {
                return ShieldAbility.AEGIS;
            }
            if (path.contains("reactive") || path.contains("counter") || path.contains("retaliating")) {
                return ShieldAbility.REACTIVE;
            }
            if (path.contains("phalanx") || path.contains("guardian") || path.contains("protector")) {
                return ShieldAbility.PHALANX;
            }
        }
        return ShieldAbility.STANDARD;
    }

    /**
     * Calculates protection bonus based on material hardness and quality.
     * Returns percentage of damage reduction (0.0 - 0.5 = 0-50%).
     */
    public float getProtectionBonus(ItemStack stack) {
        Optional<FoundryToolData> dataOpt = FoundryToolData.fromStack(stack);
        if (dataOpt.isEmpty()) return 0f;

        FoundryToolData data = dataOpt.get();
        float bonus = 0f;

        // Quality bonus: +5% per quality tier above COMMON
        bonus += data.quality().ordinal() * 0.05f;

        // Level bonus: +1% per 2 levels
        bonus += (data.level() / 2f) * 0.01f;

        // Modifier bonuses
        for (Map.Entry<ResourceLocation, Integer> mod : data.modifiers().entrySet()) {
            FoundryModifierDefinition def = FoundryModifierRegistry.get(mod.getKey());
            if (def != null) {
                String path = def.id().getPath();
                if (path.contains("protection") || path.contains("hardened") || path.contains("reinforced")) {
                    bonus += 0.05f * mod.getValue();
                }
            }
        }

        return Math.min(bonus, 0.5f); // Cap at 50% reduction
    }

    /**
     * Calculates knockback resistance bonus based on material weight.
     * Returns resistance value (0.0 - 1.0).
     */
    public float getKnockbackResistance(ItemStack stack) {
        Optional<FoundryToolData> dataOpt = FoundryToolData.fromStack(stack);
        if (dataOpt.isEmpty()) return 0f;

        FoundryToolData data = dataOpt.get();
        float resistance = 0f;

        // Quality bonus
        resistance += data.quality().ordinal() * 0.1f;

        // Fortified ability gives extra resistance
        if (getPrimaryAbility(stack) == ShieldAbility.FORTIFIED) {
            resistance += 0.3f;
        }

        // Modifier bonuses
        for (Map.Entry<ResourceLocation, Integer> mod : data.modifiers().entrySet()) {
            FoundryModifierDefinition def = FoundryModifierRegistry.get(mod.getKey());
            if (def != null) {
                String path = def.id().getPath();
                if (path.contains("heavy") || path.contains("anchor") || path.contains("sturdy")) {
                    resistance += 0.15f * mod.getValue();
                }
            }
        }

        return Math.min(resistance, 1.0f); // Cap at 100% resistance
    }

    /**
     * Calculates thorns damage when blocking.
     */
    public float getThornsDamage(ItemStack stack) {
        Optional<FoundryToolData> dataOpt = FoundryToolData.fromStack(stack);
        if (dataOpt.isEmpty()) return 0f;

        FoundryToolData data = dataOpt.get();
        float damage = 0f;

        // Thorned ability base damage
        if (getPrimaryAbility(stack) == ShieldAbility.THORNED) {
            damage += 2.0f;
        }

        // Quality bonus
        damage += data.quality().ordinal() * 0.5f;

        // Level bonus
        damage += data.level() * 0.1f;

        return damage;
    }

    /**
     * Executes shield bash when player sprints and attacks while blocking.
     */
    public void executeShieldBash(Level level, Player player, ItemStack stack, Entity target) {
        if (level.isClientSide) return;

        Optional<FoundryToolData> dataOpt = FoundryToolData.fromStack(stack);
        float power = 1.0f;
        if (dataOpt.isPresent()) {
            FoundryToolData data = dataOpt.get();
            power += data.quality().ordinal() * 0.2f;
            power += data.level() * 0.05f;
        }

        // Apply damage
        float damage = BASH_DAMAGE * power;
        if (target instanceof LivingEntity living) {
            living.hurt(level.damageSources().playerAttack(player), damage);

            // Apply knockback
            Vec3 pushDir = target.position().subtract(player.position()).normalize();
            living.setDeltaMovement(living.getDeltaMovement().add(
                pushDir.x * BASH_KNOCKBACK * power,
                0.2 * power,
                pushDir.z * BASH_KNOCKBACK * power
            ));
            living.hurtMarked = true;

            // Stun effect (brief slowness)
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (20 * power), 1));
        }

        // Sound and particles
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.8f);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(),
                10, 0.3, 0.3, 0.3, 0.1);
        }

        // Damage shield and grant XP
        stack.hurtAndBreak(2, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
        dataOpt.ifPresent(data -> {
            FoundryToolDefinition definition = FoundryToolDefinitionRegistry.get(data.toolId());
            if (definition != null) {
                FoundryToolLeveling.addXp(stack, definition, data, 3);
            }
        });

        // Cooldown
        player.getCooldowns().addCooldown(this, BASH_COOLDOWN_TICKS);
    }

    /**
     * Called when the shield successfully blocks damage.
     * Used by event handler to apply ability effects.
     */
    public void onBlockDamage(Level level, LivingEntity blocker, ItemStack stack, DamageSource source, float amount) {
        if (level.isClientSide) return;

        ShieldAbility ability = getPrimaryAbility(stack);
        Optional<FoundryToolData> dataOpt = FoundryToolData.fromStack(stack);
        float power = 1.0f;
        if (dataOpt.isPresent()) {
            FoundryToolData data = dataOpt.get();
            power += data.quality().ordinal() * 0.15f;
        }

        switch (ability) {
            case THORNED -> {
                // Damage attacker
                Entity attacker = source.getEntity();
                if (attacker instanceof LivingEntity living) {
                    float thornsDamage = getThornsDamage(stack);
                    living.hurt(level.damageSources().thorns(blocker), thornsDamage);
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.CRIT, attacker.getX(), attacker.getY() + 1, attacker.getZ(),
                            8, 0.2, 0.2, 0.2, 0.05);
                    }
                }
            }
            case REFLECTIVE -> {
                // Reflect projectile damage back (indirect = source entity differs from direct entity)
                Entity attacker = source.getEntity();
                Entity direct = source.getDirectEntity();
                boolean isIndirect = attacker != null && direct != null && !attacker.equals(direct);
                if (attacker instanceof LivingEntity living && isIndirect) {
                    living.hurt(level.damageSources().magic(), amount * 0.5f * power);
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.ENCHANT, blocker.getX(), blocker.getY() + 1, blocker.getZ(),
                            15, 0.3, 0.3, 0.3, 0.2);
                    }
                }
            }
            case AEGIS -> {
                // Grant resistance effect
                blocker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, (int) (40 * power), 0));
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.END_ROD, blocker.getX(), blocker.getY() + 1, blocker.getZ(),
                        10, 0.3, 0.5, 0.3, 0.05);
                }
            }
            case REACTIVE -> {
                // Grant attack bonus for next hit
                blocker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, (int) (60 * power), (int) power));
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, blocker.getX(), blocker.getY() + 1.5, blocker.getZ(),
                        5, 0.2, 0.2, 0.2, 0.02);
                }
            }
            case PHALANX -> {
                // Grant nearby allies resistance
                AABB area = blocker.getBoundingBox().inflate(5.0);
                for (Entity entity : level.getEntities(blocker, area)) {
                    if (entity instanceof Player ally && !ally.equals(blocker)) {
                        ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, (int) (30 * power), 0));
                    }
                }
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, blocker.getX(), blocker.getY() + 1, blocker.getZ(),
                        20, 2.0, 1.0, 2.0, 0.1);
                }
            }
            case FORTIFIED -> {
                // Already handled by knockback resistance
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.DUST_PLUME, blocker.getX(), blocker.getY(), blocker.getZ(),
                        8, 0.3, 0.1, 0.3, 0.02);
                }
            }
            default -> {
                // Standard blocking - no special effect
            }
        }

        // Award XP for successful block
        if (blocker instanceof Player player) {
            dataOpt.ifPresent(data -> {
                FoundryToolDefinition definition = FoundryToolDefinitionRegistry.get(data.toolId());
                if (definition != null) {
                    int xpGain = Math.max(1, (int) (amount / 2));
                    FoundryToolLeveling.addXp(stack, definition, data, xpGain);
                }
            });
        }
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        if (itemAbility == ItemAbilities.SHIELD_BLOCK) {
            return true;
        }
        return kind.getAbilities().contains(itemAbility);
    }

    @Override
    @Nonnull
    public UseAnim getUseAnimation(@Nonnull ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(@Nonnull ItemStack stack, @Nonnull LivingEntity entity) {
        return 72000;
    }

    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isValidRepairItem(@Nonnull ItemStack stack, @Nonnull ItemStack repairCandidate) {
        // Can be repaired with foundry materials based on tool data
        return false; // Handled by foundry repair system
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("tooltip.devmod.foundry.durability", stack.getMaxDamage()));

        // Show shield ability
        ShieldAbility ability = getPrimaryAbility(stack);
        tooltip.add(Component.translatable("tooltip.devmod.foundry.shield_ability", ability.getDisplayName()));

        // Show shield stats
        float protection = getProtectionBonus(stack);
        if (protection > 0) {
            int protectionPercent = (int) (protection * 100);
            tooltip.add(Component.translatable("tooltip.devmod.foundry.shield_protection", protectionPercent));
        }

        float knockbackRes = getKnockbackResistance(stack);
        if (knockbackRes > 0) {
            int knockbackPercent = (int) (knockbackRes * 100);
            tooltip.add(Component.translatable("tooltip.devmod.foundry.shield_knockback_res", knockbackPercent));
        }

        float thorns = getThornsDamage(stack);
        if (thorns > 0) {
            tooltip.add(Component.translatable("tooltip.devmod.foundry.shield_thorns", String.format("%.1f", thorns)));
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
        });
    }
}
