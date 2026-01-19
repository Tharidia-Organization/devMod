package com.devmod.foundry.tool;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.ItemAbility;

/**
 * Foundry staff - a utility tool for magical/special effects.
 * Staff abilities are determined by modifiers:
 * - Fiery: Shoots fire projectiles
 * - Glacial: Slows nearby enemies
 * - Telekinetic: Pulls items to player
 * - Mystic: Reveals ores (glowing effect)
 * - Charged: Lightning strike
 * - Verdant: Heals player over time
 * - Default: Knockback wave
 */
public class FoundryStaffItem extends Item {
    private final FoundryToolKind kind;
    private static final int MIN_CHARGE_TICKS = 10;
    private static final int FULL_CHARGE_TICKS = 40;

    public enum StaffAbility {
        KNOCKBACK("knockback"),
        FIERY("fiery"),
        GLACIAL("glacial"),
        TELEKINETIC("telekinetic"),
        MYSTIC("mystic"),
        CHARGED("charged"),
        VERDANT("verdant");

        private final String id;

        StaffAbility(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public Component getDisplayName() {
            return Component.translatable("foundry.staff.ability." + id);
        }
    }

    public FoundryStaffItem(FoundryToolKind kind) {
        super(new Item.Properties().stacksTo(1).durability(250));
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
     * Determines the staff's primary ability based on modifiers.
     */
    public StaffAbility getPrimaryAbility(ItemStack stack) {
        Optional<FoundryToolData> dataOpt = FoundryToolData.fromStack(stack);
        if (dataOpt.isEmpty()) return StaffAbility.KNOCKBACK;

        FoundryToolData data = dataOpt.get();
        for (ResourceLocation modId : data.modifiers().keySet()) {
            String path = modId.getPath();
            if (path.contains("fiery") || path.contains("blazing") || path.contains("scorching")) {
                return StaffAbility.FIERY;
            }
            if (path.contains("glacial") || path.contains("frosty") || path.contains("icy")) {
                return StaffAbility.GLACIAL;
            }
            if (path.contains("telekinetic") || path.contains("magnetic")) {
                return StaffAbility.TELEKINETIC;
            }
            if (path.contains("mystic") || path.contains("prospecting")) {
                return StaffAbility.MYSTIC;
            }
            if (path.contains("charged") || path.contains("conducting") || path.contains("stormborn")) {
                return StaffAbility.CHARGED;
            }
            if (path.contains("verdant") || path.contains("nourishing") || path.contains("sanguine")) {
                return StaffAbility.VERDANT;
            }
        }
        return StaffAbility.KNOCKBACK;
    }

    /**
     * Calculates power based on charge time and quality.
     */
    public float getPower(ItemStack stack, int chargeTime) {
        float basePower = Math.min((float) chargeTime / FULL_CHARGE_TICKS, 1.0f);

        Optional<FoundryToolData> dataOpt = FoundryToolData.fromStack(stack);
        if (dataOpt.isPresent()) {
            FoundryToolData data = dataOpt.get();
            // Quality bonus: +10% per tier above COMMON
            basePower *= 1.0f + (data.quality().ordinal() * 0.1f);
            // Level bonus: +2% per level
            basePower *= 1.0f + (data.level() * 0.02f);
        }

        return Math.min(basePower, 2.0f); // Cap at 2x power
    }

    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        int chargeTime = getUseDuration(stack, entity) - timeLeft;
        if (chargeTime < MIN_CHARGE_TICKS) return;

        float power = getPower(stack, chargeTime);
        StaffAbility ability = getPrimaryAbility(stack);

        if (!level.isClientSide) {
            executeAbility(level, player, stack, ability, power);

            // Damage and XP
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
            FoundryToolData.fromStack(stack).ifPresent(data -> {
                FoundryToolDefinition definition = FoundryToolDefinitionRegistry.get(data.toolId());
                if (definition != null) {
                    int xpGain = (int) (5 * power); // Base 5 XP, scaled by power
                    FoundryToolLeveling.addXp(stack, definition, data, xpGain);
                }
            });
        }

        // Play sound
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.0f + (level.getRandom().nextFloat() * 0.2f));
    }

    private void executeAbility(Level level, Player player, ItemStack stack, StaffAbility ability, float power) {
        Vec3 look = player.getLookAngle();
        double range = 8.0 * power;

        switch (ability) {
            case FIERY -> {
                // Shoot fire projectile
                if (level instanceof ServerLevel serverLevel) {
                    SmallFireball fireball = new SmallFireball(level, player, look.scale(1.5));
                    fireball.setPos(player.getX() + look.x, player.getEyeY() - 0.1, player.getZ() + look.z);
                    level.addFreshEntity(fireball);
                    spawnParticles(serverLevel, player.position().add(0, 1, 0), ParticleTypes.FLAME, 20);
                }
            }
            case GLACIAL -> {
                // Slow nearby enemies
                AABB area = player.getBoundingBox().inflate(range);
                for (Entity entity : level.getEntities(player, area)) {
                    if (entity instanceof LivingEntity living && living != player) {
                        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (60 * power), (int) power));
                        living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) (40 * power), 0));
                    }
                }
                if (level instanceof ServerLevel serverLevel) {
                    spawnParticles(serverLevel, player.position().add(0, 1, 0), ParticleTypes.SNOWFLAKE, 30);
                }
            }
            case TELEKINETIC -> {
                // Pull nearby items
                AABB area = player.getBoundingBox().inflate(range);
                for (Entity entity : level.getEntities(player, area)) {
                    if (entity instanceof net.minecraft.world.entity.item.ItemEntity item) {
                        Vec3 pull = player.position().subtract(item.position()).normalize().scale(0.5 * power);
                        item.setDeltaMovement(item.getDeltaMovement().add(pull));
                    }
                }
                if (level instanceof ServerLevel serverLevel) {
                    spawnParticles(serverLevel, player.position().add(0, 1, 0), ParticleTypes.PORTAL, 25);
                }
            }
            case MYSTIC -> {
                // Apply glowing to nearby ores (simulated by glowing effect on hidden mobs)
                // For now, give player night vision and glowing detection
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, (int) (200 * power), 0));
                AABB area = player.getBoundingBox().inflate(range);
                for (Entity entity : level.getEntities(player, area)) {
                    if (entity instanceof LivingEntity living) {
                        living.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) (100 * power), 0));
                    }
                }
                if (level instanceof ServerLevel serverLevel) {
                    spawnParticles(serverLevel, player.position().add(0, 1, 0), ParticleTypes.ENCHANT, 30);
                }
            }
            case CHARGED -> {
                // Lightning damage to nearest entity
                AABB area = player.getBoundingBox().inflate(range);
                Entity nearest = null;
                double nearestDist = Double.MAX_VALUE;
                for (Entity entity : level.getEntities(player, area)) {
                    if (entity instanceof LivingEntity living && living != player) {
                        double dist = entity.distanceToSqr(player);
                        if (dist < nearestDist) {
                            nearest = entity;
                            nearestDist = dist;
                        }
                    }
                }
                if (nearest instanceof LivingEntity living) {
                    living.hurt(level.damageSources().lightningBolt(), 6.0f * power);
                    if (level instanceof ServerLevel serverLevel) {
                        spawnParticles(serverLevel, living.position().add(0, 1, 0), ParticleTypes.ELECTRIC_SPARK, 20);
                    }
                }
                if (level instanceof ServerLevel serverLevel) {
                    spawnParticles(serverLevel, player.position().add(0, 1, 0), ParticleTypes.ELECTRIC_SPARK, 15);
                }
            }
            case VERDANT -> {
                // Heal player
                player.heal(4.0f * power);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, (int) (60 * power), 0));
                if (level instanceof ServerLevel serverLevel) {
                    spawnParticles(serverLevel, player.position().add(0, 1, 0), ParticleTypes.HEART, 10);
                }
            }
            default -> {
                // Knockback wave
                AABB area = player.getBoundingBox().inflate(range / 2);
                for (Entity entity : level.getEntities(player, area)) {
                    if (entity instanceof LivingEntity living && living != player) {
                        Vec3 push = entity.position().subtract(player.position()).normalize().scale(power);
                        living.setDeltaMovement(living.getDeltaMovement().add(push.x, 0.3 * power, push.z));
                        living.hurt(level.damageSources().magic(), 2.0f * power);
                    }
                }
                if (level instanceof ServerLevel serverLevel) {
                    spawnParticles(serverLevel, player.position().add(0, 0.5, 0), ParticleTypes.SWEEP_ATTACK, 15);
                }
            }
        }
    }

    private void spawnParticles(ServerLevel level, Vec3 pos, net.minecraft.core.particles.ParticleOptions particle, int count) {
        level.sendParticles(particle, pos.x, pos.y, pos.z, count, 0.5, 0.5, 0.5, 0.1);
    }

    @Override
    public int getUseDuration(@Nonnull ItemStack stack, @Nonnull LivingEntity entity) {
        return 72000;
    }

    @Override
    @Nonnull
    public UseAnim getUseAnimation(@Nonnull ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void appendHoverText(
        @Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("tooltip.devmod.foundry.durability", stack.getMaxDamage()));

        // Show staff ability
        StaffAbility ability = getPrimaryAbility(stack);
        tooltip.add(Component.translatable("tooltip.devmod.foundry.staff_ability", ability.getDisplayName()));

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
