package com.frenkvs.devmod;

import com.frenkvs.devmod.client.ClientVFXProxy;
import com.frenkvs.devmod.collision.integration.OBBHitHelper;
import com.frenkvs.devmod.combat.ShieldDeflector;
import com.frenkvs.devmod.hud.ImpactHudService;
import com.frenkvs.devmod.damage.DamageCalculator;
import com.frenkvs.devmod.damage.DamageBreakdown;
import com.frenkvs.devmod.hud.ImpactData;
import com.frenkvs.devmod.network.ShieldImpactPayload;
import com.frenkvs.devmod.network.ShieldShatterPayload;
import com.frenkvs.devmod.util.DamageTypeConfig;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.MaceItem;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Method;

/**
 * Server-side damage handler for combat mechanics.
 * Client VFX are delegated to ClientVFXHelper via safe dist checks.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public class DamageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DamageHandler.class);

    // Scheduled executor for evasion check (replaces Thread.sleep)
    private static final ScheduledExecutorService EVASION_SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DevMod-MeleeEvasion");
            t.setDaemon(true);
            return t;
        });

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onDamage(LivingIncomingDamageEvent event) {
        // Periodically cleanup stale tracking entries to prevent memory leaks
        cleanupStaleEntries();
        HitContext.cleanup(); // Also cleanup HitContext expired entries

        if (event.getEntity() instanceof LivingEntity victim && event.getSource().getEntity() instanceof LivingEntity attacker) {

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
                if (!ammoMatchesFilter(weapon, arrow)) {
                    if (attacker instanceof ServerPlayer sp) {
                        sp.displayClientMessage(Objects.requireNonNull(Component.literal("Ammo not allowed for this preset").withStyle(s -> s.withColor(0xFFAA00))), true);
                    }
                    return; // Skip DevMod scaling; fall back to vanilla handling
                }

                // Apply ranged-specific overrides (base damage, speed, pierce, crit)
                var ranged = com.frenkvs.devmod.ui.editor.RangedWeaponModule.getStats(weapon);
                if (ranged.baseDamage > 0) {
                    rangedBaseOverride = ranged.baseDamage;
                }
                if (ranged.projectileSpeed > 0 && delta.lengthSqr() > 0.0001) {
                    Vec3 normalized = Objects.requireNonNullElseGet(delta.normalize(), () -> delta);
                    arrow.setDeltaMovement(Objects.requireNonNull(normalized.scale(ranged.projectileSpeed)));
                    rangedSpeedOverride = ranged.projectileSpeed;
                }
                if (ranged.critChance > 0) {
                    rangedCritChance = ranged.critChance;
                    rangedCritDamage = ranged.critDamage;
                }
            } else {
                // MELEE: Use OBB raycast when enabled, fallback to AABB subdivision
                weapon = attacker.getMainHandItem();
                HitHelper.HitResult hitResult = OBBHitHelper.useOBBSystem()
                    ? OBBHitHelper.rayTraceBodyPart(attacker, victim)
                    : HitHelper.rayTraceBodyPartWithHitPoint(attacker, victim);
                part = hitResult.part();
                hitPoint = hitResult.hitPoint();
                slashDirection = attacker.getViewVector(1.0F);
                isRanged = false;
            }

            // Confirm that damage was dealt (for Enderman evasion tracking)
            confirmHit(victim);

            // 2. Retrieve Statistics (Global or Specific) - component/modifier source of truth
            WeaponStats stats = WeaponConfigManager.getStats(weapon);

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
                    calcResult, rangedSpeedOverride, rangedCritChance, rangedCritDamage);
            }

            // Store body part, armor pen bonus, AND armor reduction in context for telemetry
            recordHitContext(victim, part, isRanged, armorPenBonus, armorReduction);

            LOGGER.debug("Damage calc: base={}, final={}, armorPen={}, armorReduction={}",
                originalDamage, newDamage, armorPenBonus, armorReduction);

            // 5. Apply shield block
            if (victim instanceof Player playerVictim) {
                LOGGER.info("[Shield Debug] Player victim: isBlocking={}, useItem={}",
                    playerVictim.isBlocking(), playerVictim.getUseItem());
                if (playerVictim.isBlocking()) {
                    newDamage = applyShieldBlock(playerVictim, event.getSource(), newDamage);
                }
            }

            event.setAmount(newDamage);

            // 7. Feedback Visivo (actionbar) - Uses translatable component for i18n
            sendDamageFeedback(attacker, partKey, color, newDamage, stats);

            // 8. HUD Impact Analysis - Create and save data for the overlay
            // Note: armorPenBonus already calculated above for damage and telemetry

            DamageBreakdown breakdown = calcDetails.breakdown();

            String attackSource = getAttackSource(isRanged);

            // Create and store ImpactData for HUD (with hit position)
            ImpactData impactData = ImpactHudService.createAndStoreImpactData(
                attacker, victim, part, multiplier, breakdown, attackSource, isRanged, hitPoint, slashDirection);

            ImpactHudService.triggerImpactVfx(impactData, hitPoint, slashDirection, victim);
            ImpactHudService.triggerDamageShakeIfApplicable(victim, part, multiplier, newDamage, hitPoint);

            applyPostHitEffects(attacker, stats, newDamage);
        }
    }

    private record PartPresentation(String partKey, int color) {}

    private static PartPresentation getPartPresentation(HitHelper.BodyPart part) {
        if (part == null) {
            return new PartPresentation("devmod.bodypart.body", 0xFFFFFF);
        }

        return switch (part) {
            case HEAD -> new PartPresentation("devmod.bodypart.head", 0xFF5555);
            case BODY -> new PartPresentation("devmod.bodypart.body", 0x55FF55);
            case ARMS -> new PartPresentation("devmod.bodypart.arms", 0xFFAA00);
            case LEGS -> new PartPresentation("devmod.bodypart.legs", 0x55FFFF);
        };
    }

    private static void recordHitContext(LivingEntity victim, HitHelper.BodyPart part, boolean isRanged,
                                         float armorPenBonus, float armorReduction) {
        HitContext.store(victim, part, isRanged, armorPenBonus);
        HitContext.storeArmorReduction(victim, armorReduction);
    }

    private static void sendDamageFeedback(LivingEntity attacker, String partKey, int color,
                                           float newDamage, WeaponStats stats) {
        if (!(attacker instanceof ServerPlayer player)) {
            return;
        }

        String dmgText = String.format("%.1f", newDamage);
        String penText = stats.armorPenetration > 0 ? " [Pen]" : "";

        MutableComponent hitLabel = Objects.requireNonNull(I18n.translate("devmod.message.hit")
            .withStyle(s -> s.withColor(0xAAAAAA)));
        MutableComponent hitSeparator = Objects.requireNonNull(Component.literal(": ")
            .withStyle(s -> s.withColor(0xAAAAAA)));
        MutableComponent partComponent = Objects.requireNonNull(I18n.translate(partKey)
            .withStyle(s -> s.withColor(color)));
        MutableComponent damageLabel = Objects.requireNonNull(I18n.translate("devmod.hud.damage")
            .withStyle(s -> s.withColor(0xFFFFFF)));
        MutableComponent damageSeparator = Objects.requireNonNull(Component.literal(": ")
            .withStyle(s -> s.withColor(0xFFFFFF)));
        MutableComponent damageValue = Objects.requireNonNull(Component.literal(dmgText + penText)
            .withStyle(s -> s.withColor(0xFFFFFF)));
        MutableComponent feedback = Objects.requireNonNull(Component.empty())
            .append(hitLabel)
            .append(hitSeparator)
            .append(partComponent)
            .append(Objects.requireNonNull(Component.literal(" ")
                .withStyle(s -> s.withColor(0xFFFFFF))))
            .append(damageLabel)
            .append(damageSeparator)
            .append(damageValue);

        player.displayClientMessage(Objects.requireNonNull(feedback, "feedback"), true);
    }

    private static String getAttackSource(boolean isRanged) {
        return isRanged ? "devmod.hud.attack_source.ranged" : "devmod.hud.attack_source.melee";
    }

    private static void applyPostHitEffects(LivingEntity attacker, WeaponStats stats, float damageDealt) {
        if (stats.lifesteal > 0 && attacker.isAlive()) {
            float heal = damageDealt * stats.lifesteal;
            attacker.heal(heal);
        }
    }

    /**
     * Applies shield-specific tuning: block strength scaling, projectile deflection, shatter mechanics,
     * and visual feedback via network payloads.
     */
    private static float applyShieldBlock(Player player, DamageSource source, float incomingDamage) {
        LOGGER.info("[Shield Debug] applyShieldBlock called! incomingDamage={}", incomingDamage);

        ItemStack shield = player.getUseItem();
        if (shield.isEmpty() || !(shield.getItem() instanceof ShieldItem)) {
            LOGGER.info("[Shield Debug] No shield in use item");
            return incomingDamage;
        }

        ArmorStats stats = ArmorConfigManager.getStats(shield);
        LOGGER.info("[Shield Debug] Shield stats: blockStrength={}, opacity={}, reflectProjectiles={}",
            stats.shieldBlockStrength, stats.shieldOpacity, stats.shieldReflectProjectiles);
        float blocked = Math.min(1f, Math.max(0f, stats.shieldBlockStrength));
        float damageAfterBlock = incomingDamage * (1f - blocked);

        // Calculate impact position for visual effects
        final Vec3 impactBase = Objects.requireNonNull(player.position(), "player position");
        final Vec3 impactPosCenter = Objects.requireNonNull(impactBase.add(0, player.getBbHeight() * 0.5, 0), "impact center");
        Vec3 impactPos = impactPosCenter;
        var directEntity = source.getDirectEntity();
        if (directEntity != null) {
            // Use attacker direction to place impact on shield surface
            final Vec3 attackerPos = Objects.requireNonNull(directEntity.position(), "attacker position");
            final Vec3 toAttacker = Objects.requireNonNull(
                Objects.requireNonNull(attackerPos.subtract(impactPos), "toAttacker vec").normalize(),
                "toAttacker");
            final Vec3 surfaceOffset = Objects.requireNonNull(toAttacker.scale(1.0), "toAttacker scaled");
            impactPos = Objects.requireNonNull(impactPos.add(surfaceOffset), "impactPos surface"); // Shield surface
        }
        final Vec3 finalImpactPos = Objects.requireNonNull(impactPos, "impactPos final");

        // Projectile deflection using ShieldDeflector (physics-based)
        if (stats.shieldReflectProjectiles && source.getDirectEntity() instanceof Projectile projectile) {
            boolean deflected = ShieldDeflector.deflectProjectile(
                projectile,
                player,
                stats.shieldDeflectionSpread,
                stats.shieldDeflectSpeedMult,
                stats.shieldDeflectToOwner,
                1.2f // Default shield radius
            );

            // Send deflection visual effect to nearby clients
            if (deflected && player instanceof ServerPlayer serverPlayer) {
                sendShieldImpactPacket(serverPlayer, finalImpactPos, damageAfterBlock, true);
            }
        } else if (player instanceof ServerPlayer serverPlayer) {
            // Non-projectile damage: send regular impact visual
            sendShieldImpactPacket(serverPlayer, finalImpactPos, damageAfterBlock, false);
        }

        // Check for shield shatter (high damage threshold)
        if (incomingDamage >= stats.shieldShatterThreshold && player instanceof ServerPlayer serverPlayer) {
            sendShieldShatterPacket(serverPlayer, finalImpactPos, incomingDamage);
            // Shield is shattered - apply full damage this hit
            damageAfterBlock = incomingDamage;
        }

        // Cooldown tuning: base 5 ticks scaled by recovery speed (avoid div by zero)
        int baseCooldown = 5;
        int cooldown = Math.max(1, Math.round(baseCooldown / Math.max(0.1f, stats.shieldRecoverySpeed)));
        player.getCooldowns().addCooldown(Objects.requireNonNull(shield.getItem()), cooldown);

        return damageAfterBlock;
    }

    /**
     * Sends shield impact visual effect packet to nearby players.
     */
    private static void sendShieldImpactPacket(ServerPlayer player, Vec3 impactPos, float damage, boolean wasDeflection) {
        ShieldImpactPayload payload = wasDeflection
            ? ShieldImpactPayload.projectileDeflected(player.getId(), impactPos.x, impactPos.y, impactPos.z, damage)
            : ShieldImpactPayload.damageBlocked(player.getId(), impactPos.x, impactPos.y, impactPos.z, damage);

        // Send to all players within 32 blocks
        var level = player.serverLevel();
        for (ServerPlayer nearby : level.players()) {
            if (nearby.distanceToSqr(player) <= 32 * 32) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    nearby, Objects.requireNonNull(payload, "shield impact payload"));
            }
        }
    }

    /**
     * Sends shield shatter effect packet to nearby players.
     */
    private static void sendShieldShatterPacket(ServerPlayer player, Vec3 center, float finalDamage) {
        ShieldShatterPayload payload = ShieldShatterPayload.at(
            player.getId(), center.x, center.y, center.z, finalDamage);

        // Send to all players within 32 blocks
        var level = player.serverLevel();
        for (ServerPlayer nearby : level.players()) {
            if (nearby.distanceToSqr(player) <= 32 * 32) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    nearby, Objects.requireNonNull(payload, "shield shatter payload"));
            }
        }
    }

    private static boolean ammoMatchesFilter(ItemStack weapon, AbstractArrow arrow) {
        try {
            var custom = weapon.getOrDefault(Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA), Objects.requireNonNull(net.minecraft.world.item.component.CustomData.EMPTY));
            var tag = custom.copyTag();
            if (tag == null || !tag.contains("RangedStats")) return true;
            var ranged = tag.getCompound("RangedStats");
            if (!ranged.contains("ammoFilter")) return true;
            String rawFilter = ranged.getString("ammoFilter");
            if (rawFilter == null) return true;
            String nonNullFilter = Objects.requireNonNull(rawFilter).trim();
            if (nonNullFilter.isEmpty()) return true;
            final String filterValue = Objects.requireNonNull(nonNullFilter);
            ItemStack pickup = ItemStack.EMPTY;
            try {
                Method m = AbstractArrow.class.getDeclaredMethod("getPickupItem");
                m.setAccessible(true);
                Object res = m.invoke(arrow);
                if (res instanceof ItemStack stack) {
                    pickup = stack;
                }
            } catch (Exception ignored) { }
            var registry = net.minecraft.core.registries.BuiltInRegistries.ITEM;
            if (filterValue.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(Objects.requireNonNull(filterValue.substring(1)));
                if (tagId == null) return false;
                net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tagKey = net.minecraft.tags.TagKey.create(
                    Objects.requireNonNull(net.minecraft.core.registries.Registries.ITEM),
                    Objects.requireNonNull(tagId)
                );
                return pickup.is(Objects.requireNonNull(tagKey));
            }
            ResourceLocation id = pickup.isEmpty() ? null : registry.getKey(Objects.requireNonNull(pickup.getItem()));
            if (id == null) return false;
            String idStr = id.toString().toLowerCase(Locale.ROOT);
            String normFilter = filterValue.trim().toLowerCase(Locale.ROOT);
            return idStr.equals(normFilter) || idStr.endsWith(normFilter);
        } catch (Exception e) {
            LOGGER.debug("Ammo filter check failed: {}", e.getMessage());
            return true;
        }
    }

    // === Tracking to detect Enderman evasions ===
    // Record to save attack data at the moment it occurs
    private record PendingAttack(long timestamp, Vec3 targetPosition, Vec3 playerEye, Vec3 lookDir) {}

    // Map: targetId -> attack data
    private static final Map<Integer, PendingAttack> pendingAttacks = new ConcurrentHashMap<>();
    // Map: targetId -> timestamp of last LivingIncomingDamageEvent
    private static final Map<Integer, Long> confirmedHits = new ConcurrentHashMap<>();

    // Lock for atomic operations on pending/confirmed maps
    private static final Object EVASION_LOCK = new Object();

    // Cleanup tracking to prevent memory leaks
    private static long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL_MS = 30_000; // Cleanup every 30 seconds
    private static final long STALE_ENTRY_AGE_MS = 10_000;  // Entries older than 10 seconds are stale

    /**
     * Cleanup stale entries from tracking maps to prevent memory leaks.
     * Called periodically during damage events.
     */
    private static void cleanupStaleEntries() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanupTime = now;

        // Remove old pending attacks
        int pendingRemoved = 0;
        var pendingIter = pendingAttacks.entrySet().iterator();
        while (pendingIter.hasNext()) {
            var entry = pendingIter.next();
            if (now - entry.getValue().timestamp > STALE_ENTRY_AGE_MS) {
                pendingIter.remove();
                pendingRemoved++;
            }
        }

        // Remove old confirmed hits
        int hitsRemoved = 0;
        var hitsIter = confirmedHits.entrySet().iterator();
        while (hitsIter.hasNext()) {
            var entry = hitsIter.next();
            if (now - entry.getValue() > STALE_ENTRY_AGE_MS) {
                hitsIter.remove();
                hitsRemoved++;
            }
        }

        if (pendingRemoved > 0 || hitsRemoved > 0) {
            LOGGER.debug("[DamageHandler] Cleanup: removed {} pending attacks, {} confirmed hits",
                    pendingRemoved, hitsRemoved);
        }
    }

    /**
     * Shutdown the evasion scheduler. Call on server stop.
     */
    public static void shutdown() {
        EVASION_SCHEDULER.shutdown();
        try {
            if (!EVASION_SCHEDULER.awaitTermination(2, TimeUnit.SECONDS)) {
                EVASION_SCHEDULER.shutdownNow();
            }
        } catch (InterruptedException e) {
            EVASION_SCHEDULER.shutdownNow();
            Thread.currentThread().interrupt();
        }
        pendingAttacks.clear();
        confirmedHits.clear();
        LOGGER.info("[DamageHandler] Shutdown complete");
    }

    /**
     * Captures ALL attack attempts (before damage is calculated).
     * This event fires even when the Enderman evades!
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof LivingEntity target)) return;

        Player player = event.getEntity();
        long now = System.currentTimeMillis();

        // IMPORTANT: Save the Enderman's position NOW, before it teleports!
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 playerEye = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle();

        // Register the attack attempt with all positions
        pendingAttacks.put(target.getId(), new PendingAttack(now, targetPos, playerEye, lookDir));

        LOGGER.debug("AttackEntityEvent: player={}, target={}, targetPos={}",
            player.getName().getString(), target.getName().getString(), targetPos);

        // After 100ms, check if damage was confirmed
        // If not confirmed, the attack was evaded
        if (target instanceof EnderMan) {
            // Schedule a deferred check to see if damage occurred
            scheduleEvasionCheck(player, target, now);
        }
    }

    /**
     * Schedules a check to detect if the Enderman has evaded.
     * Uses ScheduledExecutorService instead of Thread.sleep to avoid blocking.
     * THREAD SAFETY: Uses synchronized block to prevent race with confirmHit().
     */
    private static void scheduleEvasionCheck(Player player, LivingEntity target, long attackTime) {
        final int targetId = target.getId();

        // Use ScheduledExecutor instead of Thread.sleep
        EVASION_SCHEDULER.schedule(() -> {
            // THREAD SAFETY: Synchronize access to both maps for atomic check-then-act
            synchronized (EVASION_LOCK) {
                // Retrieve data saved at the moment of attack
                PendingAttack attackData = pendingAttacks.remove(targetId);
                if (attackData == null) return;

                // Check if damage was confirmed
                Long hitTime = confirmedHits.remove(targetId); // Also remove to cleanup
                if (hitTime == null || hitTime < attackTime) {
                    // Damage was NOT confirmed -> EVASION!
                    LOGGER.debug("EVASION DETECTED! Enderman evaded attack at {}", attackData.targetPosition);

                    // Spawn an "EVADED" panel (proxy handles dist check)
                    ClientVFXProxy.spawnMeleeEvasionPanel(player, target, attackData.targetPosition, attackData.lookDir);
                } else {
                    LOGGER.debug("Attack confirmed, no evasion");
                }
            }
        }, 150, TimeUnit.MILLISECONDS);
    }

    /**
     * Called when damage is confirmed (from onDamage).
     * THREAD SAFETY: Uses synchronized block to prevent race with scheduleEvasionCheck().
     */
    private static void confirmHit(LivingEntity target) {
        synchronized (EVASION_LOCK) {
            confirmedHits.put(target.getId(), System.currentTimeMillis());
        }
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

        // Create ImpactData
        // MULTIPLAYER-SAFE: for environmental damage, the "receiver" is the player itself
        // Use victim's UUID because we want the player to see their own received damage
        ImpactData impactData = ImpactHudService.createAndStoreImpactData(
            victim.getUUID(),         // UUID of player taking damage
            victim,
            HitHelper.BodyPart.BODY,  // Environmental damage = generic body
            1.0f,
            breakdown,
            damageSourceName,         // E.g.: "Fire Damage", "Fall Damage"
            false,
            hitPoint,
            new Vec3(0, -1, 0)        // Generic direction (downward)
        );

        LOGGER.debug("Environmental damage: {}, amount={}, victim={}",
            damageSourceName, damage, victim.getName().getString());

        // Spawn 3D panel (proxy handles dist check)
        if (hitPoint != null) {
            ImpactHudService.triggerImpactVfx(impactData, hitPoint, new Vec3(0, 1, 0), victim);
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
     * @return Readable name for the damage type (never null for environmental damage)
     */
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
            // Unknown types will be formatted as "§7Some Damage Type" instead of being skipped
            return DamageTypeConfig.INSTANCE.getLabelWithFallback(typeHolder.unwrapKey().get());
        }

        // Fallback for damage sources without a type key (very rare)
        return "§7Environmental Damage";
    }
}
