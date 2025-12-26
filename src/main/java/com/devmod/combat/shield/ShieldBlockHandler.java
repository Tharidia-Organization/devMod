package com.devmod.combat.shield;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.Vec3;

import com.devmod.combat.ShieldDeflector;
import com.devmod.config.ArmorConfigManager;
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
        if (shield.isEmpty() || !(shield.getItem() instanceof ShieldItem)) {
            LOGGER.debug("No shield in use");
            return incomingDamage;
        }

        ArmorStats stats = ArmorConfigManager.getStats(shield);
        BlockResult result = calculateBlock(player, source, incomingDamage, shield, stats);

        applyVisualEffects(player, source, result, stats);
        applyCooldown(player, shield, stats);

        return result.damageAfterBlock();
    }

    private static BlockResult calculateBlock(Player player, DamageSource source,
                                               float incomingDamage, ItemStack shield, ArmorStats stats) {
        float blocked = Math.min(1f, Math.max(0f, stats.shieldBlockStrength));
        float damageAfterBlock = incomingDamage * (1f - blocked);

        boolean wasShattered = incomingDamage >= stats.shieldShatterThreshold;
        boolean wasDeflection = false;

        if (stats.shieldReflectProjectiles && source.getDirectEntity() instanceof Projectile projectile) {
            wasDeflection = ShieldDeflector.deflectProjectile(
                projectile,
                player,
                stats.shieldDeflectionSpread,
                stats.shieldDeflectSpeedMult,
                stats.shieldDeflectToOwner,
                DEFAULT_SHIELD_RADIUS
            );
        }

        if (wasShattered) {
            damageAfterBlock = incomingDamage;
        }

        return new BlockResult(damageAfterBlock, wasShattered, wasDeflection);
    }

    private static void applyVisualEffects(Player player, DamageSource source,
                                            BlockResult result, ArmorStats stats) {
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
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(nearby, payload);
            }
        }
    }

    private static void applyCooldown(Player player, ItemStack shield, ArmorStats stats) {
        int baseCooldown = 5;
        int cooldown = Math.max(1, Math.round(baseCooldown / Math.max(0.1f, stats.shieldRecoverySpeed)));
        player.getCooldowns().addCooldown(Objects.requireNonNull(shield.getItem()), cooldown);
    }
}
