package com.devmod.combat.shield;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.Vec3;

import com.devmod.combat.ShieldDeflector;
import com.devmod.combat.bridge.CombatEnduranceBridge;
import com.devmod.config.handler.impl.ArmorConfigHandler;
import com.devmod.integration.ModIntegrationManager;
import com.devmod.network.ShieldImpactPayload;
import com.devmod.network.ShieldShatterPayload;
import com.devmod.stats.ArmorStats;
public final class ShieldBlockHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShieldBlockHandler.class);
    private static final float DEFAULT_SHIELD_RADIUS = 1.2f;
    private static final double BROADCAST_RANGE_SQ = 32 * 32;

    private ShieldBlockHandler() {}

    /**
     * Result of applying shield block.
     */
    public record BlockResult(float damageAfterBlock, boolean wasShattered, boolean wasDeflection) {}

    private enum ParryTier {
        NONE,
        NORMAL,
        PERFECT
    }

    /**
     * Applies shield blocking to incoming damage.
     *
     * @param player The player blocking with a shield
     * @param source The damage source
     * @param incomingDamage The incoming damage amount
     * @return The damage after shield block is applied
     */
    public static float applyBlock(Player player, DamageSource source, float incomingDamage) {
        LOGGER.debug("applyBlock: player={}, damage={}", player.getName().getString(), incomingDamage);

        ItemStack shield = player.getUseItem();
        if (shield.isEmpty()) {
            LOGGER.debug("No shield in use");
            return incomingDamage;
        }

        // Handle vanilla shields
        if (!(shield.getItem() instanceof ShieldItem)) {
            LOGGER.debug("Item is not a shield");
            return incomingDamage;
        }

        ArmorStats stats = ArmorConfigHandler.INSTANCE.getStats(shield);
        BlockResult result = calculateBlock(player, source, incomingDamage, stats);

        applyParryEffects(player, result, incomingDamage);
        applyVisualEffects(player, source, result);
        applyCooldown(player, shield, stats);

        return result.damageAfterBlock();
    }

    private static BlockResult calculateBlock(Player player, DamageSource source,
                                               float incomingDamage, ArmorStats stats) {
        float blocked = Math.min(1f, Math.max(0f, stats.getShieldBlockStrength()));
        float damageAfterBlock = incomingDamage * (1f - blocked);

        boolean wasShattered = incomingDamage >= stats.getShieldShatterThreshold();
        boolean wasDeflection = false;

        if (stats.isShieldReflectProjectiles() && source.getDirectEntity() instanceof Projectile projectile) {
            wasDeflection = ShieldDeflector.deflectProjectile(
                projectile,
                player,
                stats.getShieldDeflectionSpread(),
                stats.getShieldDeflectSpeedMult(),
                stats.isShieldDeflectToOwner(),
                DEFAULT_SHIELD_RADIUS
            );
        }

        if (wasShattered) {
            damageAfterBlock = incomingDamage;
        }

        return new BlockResult(damageAfterBlock, wasShattered, wasDeflection);
    }

    private static void applyVisualEffects(Player player, DamageSource source,
                                            BlockResult result) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        Vec3 impactPos = calculateImpactPosition(player, source);

        if (result.wasShattered()) {
            broadcastShatterEffect(serverPlayer, impactPos, result.damageAfterBlock());
        } else {
            broadcastImpactEffect(serverPlayer, impactPos, result.damageAfterBlock(), result.wasDeflection());
        }
    }

    private static Vec3 calculateImpactPosition(Player player, DamageSource source) {
        Vec3 basePos = Objects.requireNonNull(player.position());
        Vec3 centerPos = Objects.requireNonNull(basePos.add(0, player.getBbHeight() * 0.5, 0));
        Vec3 impactPos = centerPos;

        var directEntity = source.getDirectEntity();
        if (directEntity != null) {
            Vec3 attackerPos = Objects.requireNonNull(directEntity.position());
            Vec3 toAttacker = Objects.requireNonNull(
                Objects.requireNonNull(attackerPos.subtract(impactPos)).normalize()
            );
            Vec3 surfaceOffset = Objects.requireNonNull(toAttacker.scale(1.0));
            impactPos = Objects.requireNonNull(impactPos.add(surfaceOffset));
        }

        return impactPos;
    }

    private static void broadcastImpactEffect(ServerPlayer player, Vec3 pos, float damage, boolean wasDeflection) {
        ShieldImpactPayload payload = wasDeflection
            ? ShieldImpactPayload.projectileDeflected(player.getId(), pos.x, pos.y, pos.z, damage)
            : ShieldImpactPayload.damageBlocked(player.getId(), pos.x, pos.y, pos.z, damage);

        broadcastToNearby(player, payload);
    }

    private static void broadcastShatterEffect(ServerPlayer player, Vec3 pos, float damage) {
        ShieldShatterPayload payload = ShieldShatterPayload.at(player.getId(), pos.x, pos.y, pos.z, damage);
        broadcastToNearby(player, payload);
    }

    private static <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void broadcastToNearby(
            ServerPlayer player, T payload) {
        var level = player.serverLevel();
        for (ServerPlayer nearby : level.players()) {
            if (nearby.distanceToSqr(player) <= BROADCAST_RANGE_SQ) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(nearby, Objects.requireNonNull(payload));
            }
        }
    }

    private static void applyParryEffects(Player player, BlockResult result, float incomingDamage) {
        if (result.wasShattered() || incomingDamage <= 0f) {
            return;
        }
        if (result.damageAfterBlock() >= incomingDamage) {
            return;
        }

        ParryTier parryTier = detectParryTier(player);
        if (parryTier == ParryTier.NONE) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // Register parry with combo system via bridge (no direct endurance imports)
        CombatEnduranceBridge.get().onParry(serverPlayer);

        playParrySound(serverPlayer, parryTier);

        if (LOGGER.isDebugEnabled()) {
            String skillName = ModIntegrationManager.getEpicFightHoldingSkillName(serverPlayer);
            LOGGER.debug("[Shield] EpicFight parry: player={}, tier={}, skill={}",
                serverPlayer.getName().getString(),
                parryTier,
                skillName != null ? skillName : "unknown");
        }
    }

    private static ParryTier detectParryTier(Player player) {
        if (!ModIntegrationManager.isEpicFightLoaded()) {
            return ParryTier.NONE;
        }

        boolean guarding = ModIntegrationManager.isEpicFightGuarding(player);
        boolean parrying = ModIntegrationManager.isEpicFightParrying(player);
        if (!guarding && !parrying) {
            return ParryTier.NONE;
        }
        if (!ModIntegrationManager.isEpicFightInParryWindow(player)) {
            return ParryTier.NONE;
        }
        if (ModIntegrationManager.isEpicFightPerfectParry(player)) {
            return ParryTier.PERFECT;
        }
        return ParryTier.NORMAL;
    }

    private static void playParrySound(ServerPlayer player, ParryTier parryTier) {
        SoundEvent sound = parryTier == ParryTier.PERFECT
            ? ModIntegrationManager.getEpicFightParrySoundEvent()
            : ModIntegrationManager.getEpicFightGuardSoundEvent();
        if (sound == null) {
            return;
        }

        float pitch = parryTier == ParryTier.PERFECT ? 1.2f : 1.0f;
        player.serverLevel().playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            sound,
            SoundSource.PLAYERS,
            0.9f,
            pitch
        );
    }

    private static void applyCooldown(Player player, ItemStack shield, ArmorStats stats) {
        int baseCooldown = 5;
        int cooldown = Math.max(1, Math.round(baseCooldown / Math.max(0.1f, stats.getShieldRecoverySpeed())));
        player.getCooldowns().addCooldown(Objects.requireNonNull(shield.getItem()), cooldown);
    }
}
