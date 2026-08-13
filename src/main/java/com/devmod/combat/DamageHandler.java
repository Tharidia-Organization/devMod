package com.devmod.combat;

import java.util.Objects;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import com.devmod.DevMod;
import com.devmod.collision.integration.OBBHitHelper;
import com.devmod.combat.bridge.CombatVisualsBridge;
import com.devmod.combat.filter.AmmoFilter;
import com.devmod.combat.shield.ShieldBlockHandler;
import com.devmod.combat.tracking.EvasionHandler;
import com.devmod.config.handler.impl.WeaponConfigHandler;
import com.devmod.damage.DamageBreakdown;
import com.devmod.damage.DamageCalculator;
import com.devmod.shared.BodyPart;
import com.devmod.stats.WeaponStats;
import com.devmod.util.DamageTypeConfig;
import com.devmod.util.I18n;

@EventBusSubscriber(modid = DevMod.MODID)
public class DamageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DamageHandler.class);

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onDamage(LivingIncomingDamageEvent event) {
        EvasionHandler.cleanup();
        HitData.cleanup();

        LivingEntity victim = event.getEntity();
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {

            ItemStack weapon = ItemStack.EMPTY;
            HitHelper.BodyPart part = HitHelper.BodyPart.BODY;
            Vec3 hitPoint = null;
            Vec3 slashDirection = null;
            boolean isRanged = false;
            float rangedBaseOverride = -1f;
            float rangedSpeedOverride = -1f;
            float rangedCritChance = 0f;
            float rangedCritDamage = 1f;

            // 1. Identify the weapon and body part hit
            if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow) {
                // RANGED: Use arrow Y coordinate (100% PRECISION)
                weapon = attacker.getMainHandItem();
                part = HitHelper.getBodyPart(victim, arrow.getY());
                hitPoint = arrow.position();
                // SAFETY: getDeltaMovement() can return zero vector for stationary arrows
                Vec3 delta = arrow.getDeltaMovement();
                slashDirection = (delta.lengthSqr() > 0.0001) ? delta.normalize() : arrow.getViewVector(1.0f);
                isRanged = true;

                // Enforce ammo filter defined by ranged stats, if any
                if (!AmmoFilter.matches(weapon, arrow)) {
                    if (attacker instanceof ServerPlayer sp) {
                        sp.displayClientMessage(Objects.requireNonNull(
                            Component.literal("Ammo not allowed for this preset")
                                .withStyle(s -> s.withColor(CombatColors.Text.WARNING))),
                            true);
                    }
                    return; // Skip DevMod scaling; fall back to vanilla handling
                }

                // Apply ranged-specific overrides (base damage, speed, pierce, crit)
                RangedOverrides ranged = RangedOverridesResolver.resolve(weapon);
                if (ranged.baseDamage > 0) {
                    rangedBaseOverride = ranged.baseDamage;
                }
                if (ranged.projectileSpeed > 0 && delta.lengthSqr() > 0.0001) {
                    Vec3 normalized = Objects.requireNonNullElseGet(delta.normalize(), () -> delta);
                    arrow.setDeltaMovement(Objects.requireNonNull(normalized.scale(ranged.projectileSpeed)));
                    rangedSpeedOverride = ranged.projectileSpeed;
                }
                if (ranged.getCritChance() > 0) {
                    rangedCritChance = ranged.getCritChance();
                    rangedCritDamage = ranged.getCritDamage();
                }
            } else {
                // MELEE: Use OBB raycast when enabled, fallback to AABB subdivision
                weapon = attacker.getMainHandItem();
                LivingEntity target = Objects.requireNonNull(victim);
                if (OBBHitHelper.useOBBSystem()) {
                    com.devmod.shared.HitResult obbResult = OBBHitHelper.rayTraceBodyPart(attacker, target);
                    part = HitHelper.BodyPart.fromShared(obbResult.part());
                    hitPoint = obbResult.hitPoint();
                } else {
                    HitHelper.HitResult aabbResult = HitHelper.rayTraceBodyPartWithHitPoint(attacker, target);
                    part = aabbResult.part();
                    hitPoint = aabbResult.hitPoint();
                }
                slashDirection = attacker.getViewVector(1.0F);
                isRanged = false;
            }

            // Confirm that damage was dealt (for Enderman evasion tracking)
            EvasionHandler.confirmHit(victim);

            // 2. Retrieve Statistics (Global or Specific) - component/modifier source of truth
            WeaponStats stats = WeaponConfigHandler.INSTANCE.getStats(weapon);

            // 3. Resolve part labels/colors for UI
            PartPresentation partPresentation = getPartPresentation(part);
            String partKey = partPresentation.partKey();
            int color = partPresentation.color();

            // 4. Calculate Final Damage using DamageCalculator
            float originalDamage = event.getAmount();
            if (rangedBaseOverride > 0) {
                originalDamage = rangedBaseOverride;
                event.setAmount(originalDamage);
            }

            // Use centralized damage calculation with HUD breakdown
            DamageCalculator.CalculationDetails calcDetails = DamageCalculator.calculateWithBreakdown(
                weapon, attacker, victim, part, originalDamage, stats, event.getSource());
            DamageCalculator.CalculationResult calcResult = calcDetails.result();

            float newDamage = calcResult.finalDamage();
            float armorPenBonus = calcResult.armorPenBonus();
            float armorReduction = calcResult.armorReduction();
            float multiplier = calcResult.bodyPartMultiplier();

            // Apply ranged modifiers (speed scaling, crit)
            if (isRanged) {
                newDamage = DamageCalculator.applyRangedModifiers(
                    calcResult, rangedSpeedOverride, rangedCritChance, rangedCritDamage, victim.getRandom());
            }

            // Store body part, armor pen bonus, AND armor reduction in context for telemetry
            recordHitData(victim, part, isRanged, armorPenBonus, armorReduction, weapon);

            LOGGER.debug("Damage calc: base={}, final={}, armorPen={}, armorReduction={}",
                originalDamage, newDamage, armorPenBonus, armorReduction);

            // 5. Apply shield block
            if (victim instanceof Player playerVictim && playerVictim.isBlocking()) {
                newDamage = ShieldBlockHandler.applyBlock(playerVictim, event.getSource(), newDamage);
            }

            event.setAmount(newDamage);

            // 7. Feedback Visivo (actionbar) - Uses translatable component for i18n
            sendDamageFeedback(attacker, partKey, color, newDamage, stats);

            // 8. HUD Impact Analysis - Create and save data for the overlay
            // Note: armorPenBonus already calculated above for damage and telemetry

            DamageBreakdown breakdown = calcDetails.breakdown();

            String attackSource = getAttackSource(isRanged);

            // Create and store ImpactData for HUD
            if (FMLEnvironment.dist.isClient()) {
                // Single-player or integrated server: create locally
                triggerImpactHudClientSafe(attacker, victim, part, multiplier, breakdown,
                    attackSource, isRanged, hitPoint, slashDirection, newDamage);
            } else if (attacker instanceof ServerPlayer serverPlayer) {
                // Dedicated server: send network packet to client
                sendImpactSyncToClient(serverPlayer, victim, part, multiplier, breakdown,
                    attackSource, isRanged, hitPoint, slashDirection, newDamage);
            }

            applyPostHitEffects(attacker, stats, newDamage);
        }
    }

    private record PartPresentation(String partKey, int color) {}

    private static PartPresentation getPartPresentation(HitHelper.BodyPart part) {
        if (part == null) {
            return new PartPresentation("devmod.bodypart.body", CombatColors.BodyPart.DEFAULT);
        }

        return switch (part) {
            case HEAD -> new PartPresentation("devmod.bodypart.head", CombatColors.BodyPart.HEAD);
            case BODY -> new PartPresentation("devmod.bodypart.body", CombatColors.BodyPart.BODY);
            case ARMS -> new PartPresentation("devmod.bodypart.arms", CombatColors.BodyPart.ARMS);
            case LEGS -> new PartPresentation("devmod.bodypart.legs", CombatColors.BodyPart.LEGS);
        };
    }

    private static void recordHitData(LivingEntity victim, HitHelper.BodyPart part, boolean isRanged,
                                         float armorPenBonus, float armorReduction, ItemStack weapon) {
        HitData.store(victim, part, isRanged, armorPenBonus,
            weapon != null && !weapon.isEmpty() ? weapon.getItem() : null);
        HitData.storeArmorReduction(victim, armorReduction);
    }

    private static void sendDamageFeedback(LivingEntity attacker, String partKey, int color,
                                           float newDamage, WeaponStats stats) {
        if (!(attacker instanceof ServerPlayer player)) {
            return;
        }

        String dmgText = String.format("%.1f", newDamage);
        String penText = stats.getArmorPenetration() > 0 ? " [Pen]" : "";

        MutableComponent hitLabel = Objects.requireNonNull(I18n.translate("devmod.message.hit")
            .withStyle(s -> s.withColor(CombatColors.Text.MUTED)));
        MutableComponent hitSeparator = Objects.requireNonNull(Component.literal(": ")
            .withStyle(s -> s.withColor(CombatColors.Text.MUTED)));
        MutableComponent partComponent = Objects.requireNonNull(I18n.translate(partKey)
            .withStyle(s -> s.withColor(color)));
        MutableComponent damageLabel = Objects.requireNonNull(I18n.translate("devmod.hud.damage")
            .withStyle(s -> s.withColor(CombatColors.Text.PRIMARY)));
        MutableComponent damageSeparator = Objects.requireNonNull(Component.literal(": ")
            .withStyle(s -> s.withColor(CombatColors.Text.PRIMARY)));
        MutableComponent damageValue = Objects.requireNonNull(Component.literal(dmgText + penText)
            .withStyle(s -> s.withColor(CombatColors.Text.PRIMARY)));
        MutableComponent feedback = Objects.requireNonNull(Component.empty())
            .append(hitLabel)
            .append(hitSeparator)
            .append(partComponent)
            .append(Objects.requireNonNull(Component.literal(" ")
                .withStyle(s -> s.withColor(CombatColors.Text.PRIMARY))))
            .append(damageLabel)
            .append(damageSeparator)
            .append(damageValue);

        player.displayClientMessage(Objects.requireNonNull(feedback, "feedback"), true);
    }

    private static String getAttackSource(boolean isRanged) {
        return isRanged ? "devmod.hud.attack_source.ranged" : "devmod.hud.attack_source.melee";
    }

    private static void applyPostHitEffects(LivingEntity attacker, WeaponStats stats, float damageDealt) {
        if (stats.getLifesteal() > 0 && attacker.isAlive()) {
            float heal = damageDealt * stats.getLifesteal();
            attacker.heal(heal);
        }
    }

    /**
     * Shutdown delegated handlers. Call on server stop.
     */
    public static void shutdown() {
        EvasionHandler.shutdown();
        LOGGER.info("[DamageHandler] Shutdown complete");
    }

    /**
     * Captures ALL attack attempts (before damage is calculated).
     * This event fires even when the Enderman evades!
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        EvasionHandler.recordAttackAttempt(event.getEntity(), target);
    }

    // === Handler for environmental damage (fire, lava, fall, etc.) ===

    /**
     * Tracks environmental damage that does NOT have a LivingEntity attacker.
     * Includes: fire, lava, drowning, fall, cactus, suffocation, etc.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.NORMAL)
    public static void onEnvironmentalDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();

        // Skip if there's already a LivingEntity attacker (handled by onDamage)
        if (event.getSource().getEntity() instanceof LivingEntity) {
            return;
        }

        // Only for players (show HUD)
        if (!(victim instanceof Player)) {
            return;
        }

        // Determine the environmental damage type
        String damageSourceName = getEnvironmentalDamageSource(event);
        if (damageSourceName == null) {
            return; // Damage type not tracked
        }

        float damage = event.getAmount();

        // Hit position = center of victim
        Vec3 hitPoint = victim.position().add(0, victim.getBbHeight() * 0.5, 0);

        DamageBreakdown breakdown = DamageCalculator
            .calculateEnvironmentalWithBreakdown(victim, damage)
            .breakdown();

        LOGGER.debug("Environmental damage: {}, amount={}, victim={}",
            damageSourceName, damage, victim.getName().getString());

        // Create ImpactData and trigger VFX (client-only)
        if (FMLEnvironment.dist.isClient()) {
            triggerEnvironmentalImpactClientSafe(victim, breakdown, damageSourceName, hitPoint);
        }
    }

    /**
     * Identifies the environmental damage type.
     * Uses config-based damage type mappings loaded from damage_types.json.
     * Custom damage types can be added to the config without recompiling.
     *
     * <p>Now always returns a label (never null) for damage types without
     * an attacker - unknown types get a formatted fallback label so HUD
     * panels always show something.</p>
     *
     * @return Readable name for the damage type, or null if player attack without mace smash
     */
    @Nullable
    private static String getEnvironmentalDamageSource(LivingIncomingDamageEvent event) {
        var source = event.getSource();

        // Special case: Mace smash detection (requires runtime check, not just type matching)
        if (source.is(Objects.requireNonNull(DamageTypes.PLAYER_ATTACK))) {
            var attacker = source.getEntity();
            if (attacker instanceof Player player) {
                ItemStack mainHand = player.getMainHandItem();
                if (mainHand.getItem() instanceof MaceItem && player.fallDistance > 1.5f) {
                    // Use config label for mace smash with fallback
                    return DamageTypeConfig.INSTANCE.getLabelWithFallback("devmod:mace_smash");
                }
            }
            // Player attack without mace smash - don't track as environmental
            return null;
        }

        // Get the damage type's ResourceLocation and look it up in config
        var typeHolder = source.typeHolder();
        if (typeHolder.unwrapKey().isPresent()) {
            // Use getLabelWithFallback to always return a readable label
            // Unknown types will be formatted as "\u00A77Some Damage Type" instead of being skipped
            return DamageTypeConfig.INSTANCE.getLabelWithFallback(typeHolder.unwrapKey().get());
        }

        // Fallback for damage sources without a type key (very rare)
        return "\u00A77Environmental Damage";
    }

    // ========== Ranged overrides via CombatVisualsBridge ==========

    private static final class RangedOverridesResolver {
        private static RangedOverrides resolve(ItemStack weapon) {
            CombatVisualsBridge.RangedStatsSnapshot snapshot =
                    CombatVisualsBridge.get().resolveRangedStats(weapon);
            return new RangedOverrides(
                snapshot.baseDamage(),
                snapshot.projectileSpeed(),
                snapshot.critChance(),
                snapshot.critDamage()
            );
        }
    }

    private static final class RangedOverrides {
        private final float baseDamage;
        private final float projectileSpeed;
        private final float critChance;
        private final float critDamage;

        private RangedOverrides(float baseDamage, float projectileSpeed, float critChance, float critDamage) {
            this.baseDamage = baseDamage;
            this.projectileSpeed = projectileSpeed;
            this.critChance = critChance;
            this.critDamage = critDamage;
        }

        private float getCritChance() {
            return critChance;
        }

        private float getCritDamage() {
            return critDamage;
        }
    }

    // ========== Client-safe HUD helpers via CombatVisualsBridge ==========

    /**
     * Triggers impact HUD on client side via the CombatVisualsBridge.
     * On dedicated server the bridge is a no-op; on client the real impl renders the HUD.
     */
    private static void triggerImpactHudClientSafe(LivingEntity attacker, LivingEntity victim,
            HitHelper.BodyPart part, float multiplier, DamageBreakdown breakdown,
            String attackSource, boolean isRanged, Vec3 hitPoint, Vec3 slashDirection, float damage) {
        CombatVisualsBridge bridge = CombatVisualsBridge.get();
        Object impactData = bridge.createAndStoreImpactData(attacker, victim, part, multiplier,
                breakdown, attackSource, isRanged, hitPoint, slashDirection);
        bridge.triggerImpactVfx(impactData, hitPoint, slashDirection, victim);
        bridge.triggerDamageShakeIfApplicable(victim, part, multiplier, damage, hitPoint);
    }

    /**
     * Triggers environmental impact HUD on client side via the CombatVisualsBridge.
     */
    private static void triggerEnvironmentalImpactClientSafe(LivingEntity victim,
            DamageBreakdown breakdown, String damageSourceName, Vec3 hitPoint) {
        CombatVisualsBridge bridge = CombatVisualsBridge.get();
        Object impactData = bridge.createAndStoreImpactData(victim.getUUID(), victim,
                HitHelper.BodyPart.BODY, 1.0f, breakdown, damageSourceName, false, hitPoint, new Vec3(0, -1, 0));
        if (hitPoint != null) {
            bridge.triggerImpactVfx(impactData, hitPoint, new Vec3(0, 1, 0), victim);
        }
    }

    /**
     * Sends impact data to client via network packet (dedicated server only).
     */
    private static void sendImpactSyncToClient(ServerPlayer attacker, LivingEntity victim,
            HitHelper.BodyPart part, float multiplier, DamageBreakdown breakdown,
            String attackSource, boolean isRanged, Vec3 hitPoint, Vec3 slashDirection, float damage) {
        com.devmod.network.ImpactSyncPayload payload = com.devmod.network.ImpactSyncPayload.create(
            victim.getId(),
            part.ordinal(),
            multiplier,
            breakdown.getBaseWeaponDamage(),
            breakdown.getTotalEnchantBonus(),
            breakdown.getPehkuiSizeBonus(),
            breakdown.getBodyPartMultiplier(),
            breakdown.getArmorPenetrationBonus(),
            breakdown.getFinalDamage(),
            attackSource,
            isRanged,
            hitPoint,
            slashDirection,
            damage
        );
        com.devmod.network.NetworkHandler.sendImpactSync(attacker, payload);
        LOGGER.debug("Sent ImpactSyncPayload to {}: victim={}, part={}, damage={}",
            attacker.getName().getString(), victim.getName().getString(), part, damage);
    }
}
