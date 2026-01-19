package com.devmod.foundry.tool;

import java.util.Optional;
import java.util.Random;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import com.devmod.DevMod;

/**
 * Event hooks for foundry tool leveling.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public final class FoundryToolingEvents {
    private FoundryToolingEvents() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide || player.isCreative()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return;
        }
        FoundryToolData data = FoundryToolData.fromStack(stack).orElse(null);
        if (data == null) {
            return;
        }
        FoundryToolDefinition definition = FoundryToolDefinitionRegistry.get(data.toolId());
        if (definition == null) {
            return;
        }
        FoundryToolLeveling.addXp(stack, definition, data, 1);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide || player.isCreative()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return;
        }
        FoundryToolData data = FoundryToolData.fromStack(stack).orElse(null);
        if (data == null) {
            return;
        }
        FoundryToolDefinition definition = FoundryToolDefinitionRegistry.get(data.toolId());
        if (definition == null) {
            return;
        }
        FoundryToolLeveling.addXp(stack, definition, data, 2);
    }

    /**
     * Slime armor set bonus: bounce on landing and negate fall damage.
     */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (!hasFullSlimeArmor(entity)) {
            return;
        }
        // Cancel fall damage
        event.setCanceled(true);
        // Bounce: reverse and reduce vertical velocity
        Vec3 motion = entity.getDeltaMovement();
        if (motion.y < -0.1) {
            double bounceY = -motion.y * 0.8; // 80% bounce efficiency
            entity.setDeltaMovement(motion.x, bounceY, motion.z);
            entity.hurtMarked = true; // Sync to client
        }
    }

    private static boolean hasFullSlimeArmor(LivingEntity entity) {
        Item helmet = entity.getItemBySlot(EquipmentSlot.HEAD).getItem();
        Item chest = entity.getItemBySlot(EquipmentSlot.CHEST).getItem();
        Item legs = entity.getItemBySlot(EquipmentSlot.LEGS).getItem();
        Item boots = entity.getItemBySlot(EquipmentSlot.FEET).getItem();
        return helmet == FoundryToolItems.SLIME_HELMET.get()
            && chest == FoundryToolItems.SLIME_CHESTPLATE.get()
            && legs == FoundryToolItems.SLIME_LEGGINGS.get()
            && boots == FoundryToolItems.SLIME_BOOTS.get();
    }

    private static final Random CRIT_RANDOM = new Random();

    /**
     * Applies foundry tool critical hit damage bonus.
     * This runs early to multiply base damage before other reductions.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        // Only apply to direct entity damage (melee attacks)
        if (event.getSource().getEntity() == null) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (attacker.level().isClientSide) {
            return;
        }

        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty()) {
            return;
        }

        Optional<FoundryToolStats> statsOpt = getToolStats(weapon);
        if (statsOpt.isEmpty()) {
            return;
        }

        FoundryToolStats stats = statsOpt.get();
        float critChance = stats.critChance();
        float critDamage = stats.critDamage();

        // No crit stats configured
        if (critChance <= 0.0f || critDamage <= 1.0f) {
            return;
        }

        // Roll for crit
        if (CRIT_RANDOM.nextFloat() < critChance) {
            float originalDamage = event.getOriginalDamage();
            float newDamage = originalDamage * critDamage;
            event.setNewDamage(newDamage);

            // Visual/audio feedback for critical hit
            LivingEntity target = event.getEntity();
            if (target.level() instanceof ServerLevel serverLevel) {
                // Spawn crit particles
                serverLevel.sendParticles(
                    ParticleTypes.CRIT,
                    target.getX(),
                    target.getY() + target.getBbHeight() * 0.5,
                    target.getZ(),
                    10, // count
                    0.3, 0.3, 0.3, // spread
                    0.1 // speed
                );
                // Play crit sound
                serverLevel.playSound(
                    null,
                    target.getX(), target.getY(), target.getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS,
                    1.0f, 1.0f
                );
            }
        }
    }

    /**
     * Computes the current stats of a foundry tool from its stack data.
     */
    public static Optional<FoundryToolStats> getToolStats(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        FoundryToolData data = FoundryToolData.fromStack(stack).orElse(null);
        if (data == null) {
            return Optional.empty();
        }
        FoundryToolDefinition definition = FoundryToolDefinitionRegistry.get(data.toolId());
        if (definition == null) {
            return Optional.empty();
        }

        FoundryToolStats stats = FoundryToolBuilder.computeStats(
            definition,
            definition.parts(),
            data.materials(),
            data.modifiersWithEmbossment(),
            data.quality()
        );

        // Apply specialization if present
        if (data.specialization() != null) {
            stats = FoundryToolBuilder.applySpecialization(stats, definition.kind(), data.specialization());
        }

        return Optional.of(stats);
    }

    /**
     * Modifies crossbow projectile velocity when spawned.
     * This handles the projectile speed stat for crossbows since we can't easily
     * override the internal shooting velocity in CrossbowItem.
     */
    @SubscribeEvent
    public static void onProjectileSpawn(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Projectile projectile)) {
            return;
        }
        if (event.getLevel().isClientSide) {
            return;
        }

        Entity owner = projectile.getOwner();
        if (!(owner instanceof LivingEntity shooter)) {
            return;
        }

        ItemStack weapon = shooter.getMainHandItem();
        if (weapon.isEmpty()) {
            return;
        }

        // Only apply to foundry crossbow projectiles
        if (!(weapon.getItem() instanceof FoundryCrossbowItem crossbow)) {
            return;
        }

        // Get projectile speed multiplier and apply it
        float speedMult = crossbow.getProjectileSpeedMultiplier(weapon);
        if (speedMult != 1.0f && projectile instanceof AbstractArrow arrow) {
            // Scale the arrow's velocity
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(speedMult));
        }
    }
}
