package com.frenkvs.devmod;

import com.frenkvs.devmod.client.ClientVFXProxy;
import com.frenkvs.devmod.hud.DamageBreakdown;
import com.frenkvs.devmod.hud.ImpactData;
import com.frenkvs.devmod.util.DamageTypeConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.MaceItem;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
            } else {
                // MELEE: Use AABB subdivision raycast (95% PRECISION)
                weapon = attacker.getMainHandItem();
                HitHelper.HitResult hitResult = HitHelper.rayTraceBodyPartWithHitPoint(attacker, victim);
                part = hitResult.part();
                hitPoint = hitResult.hitPoint();
                slashDirection = attacker.getViewVector(1.0F);
                isRanged = false;
            }

            // Confirm that damage was dealt (for Enderman evasion tracking)
            confirmHit(victim);

            // 2. Retrieve Statistics (Global or Specific)
            WeaponStats stats = WeaponConfigManager.getStats(weapon);

            // 3. Calculate Multiplier
            float multiplier = 1.0f;
            String partKey = "devmod.bodypart.body";
            int color = 0xFFFFFF;

            switch (part) {
                case HEAD -> { multiplier = stats.headMult; partKey = "devmod.bodypart.head"; color = 0xFF5555; }
                case BODY -> { multiplier = stats.bodyMult; partKey = "devmod.bodypart.body"; color = 0x55FF55; }
                case ARMS -> { multiplier = stats.armsMult; partKey = "devmod.bodypart.arms"; color = 0xFFAA00; }
                case LEGS -> { multiplier = stats.legsMult; partKey = "devmod.bodypart.legs"; color = 0x55FFFF; }
            }

            // 4. Calculate Final Damage
            float originalDamage = event.getAmount();
            float newDamage = (originalDamage + stats.baseDamageBonus) * multiplier;

            // 5. Armor Penetration (configurable formula via Config)
            // Calculate armor pen bonus ONCE for both damage application AND telemetry
            float armorPenBonus = 0f;
            if (stats.armorPenetration > 0) {
                armorPenBonus = calculateArmorPenBonus(stats.armorPenetration, victim.getArmorValue(), newDamage);
                newDamage += armorPenBonus;
            }

            // Store body part AND armor pen bonus in context for telemetry
            HitContext.store(victim, part, isRanged, armorPenBonus);

            // 6. Apply
            event.setAmount(newDamage);

            // 7. Feedback Visivo (actionbar) - Uses translatable component for i18n
            if (attacker instanceof ServerPlayer player) {
                String dmgText = String.format("%.1f", newDamage);
                String penText = stats.armorPenetration > 0 ? " [Pen]" : "";

                var partComponent = Objects.requireNonNull(Component.translatable(partKey));
                var damageComponent = Objects.requireNonNull(Component.literal(" §fDmg: " + dmgText + penText));
                var feedback = Objects.requireNonNull(
                    Component.literal("§7Hit: §" + getChar(color))
                        .append(partComponent)
                        .append(damageComponent)
                );

                player.displayClientMessage(feedback, true);
            }

            // 8. HUD Impact Analysis - Create and save data for the overlay
            // Note: armorPenBonus already calculated above for damage and telemetry

            // Create detailed breakdown
            DamageBreakdown breakdown = new DamageBreakdown(
                weapon,
                victim,
                originalDamage,
                multiplier,
                armorPenBonus
            );

            // Determine attack source
            String attackSource = isRanged ? "Ranged Attack" : "Melee Attack";

            // Create and store ImpactData for HUD (with hit position)
            // MULTIPLAYER-SAFE: pass attacker UUID for data isolation
            ImpactData impactData = new ImpactData(
                attacker.getUUID(),
                victim,
                part,
                multiplier,
                breakdown,
                attackSource,
                isRanged,
                hitPoint,
                slashDirection
            );
            ImpactData.store(impactData);

            // Add 3D VFX effect (marker + slash animation)
            // In singleplayer, the damage event runs on the integrated server but we can
            // access client classes because we're on the same process
            LOGGER.debug("dist.isClient={}, hitPoint={}, target={}",
                FMLEnvironment.dist.isClient(), hitPoint, victim.getName().getString());

            // Delegate VFX to proxy (safe on both client and server)
            ClientVFXProxy.addImpactVFX(hitPoint, slashDirection, impactData);

            // 9. Screen Shake Effect - adds tactile feedback for combat
            // Only trigger shake when PLAYER takes damage (not when dealing damage)
            if (victim instanceof Player && hitPoint != null) {
                boolean isCritical = multiplier > 1.5f;
                boolean isHeadshot = part == HitHelper.BodyPart.HEAD;
                ClientVFXProxy.addDamageShake(hitPoint, newDamage, isCritical, isHeadshot);
            }
        }
    }

    private static char getChar(int color) {
        if (color == 0xFF5555) return 'c';
        if (color == 0x55FFFF) return 'b';
        return 'a';
    }

    /**
     * Calculates armor penetration bonus based on configured formula.
     * Designers can choose which formula fits their PvP/PvE balance via config.
     *
     * @param armorPen Armor penetration percentage (0.0 - 1.0)
     * @param armorValue Target's armor value
     * @param baseDamage Base damage before armor pen
     * @return Bonus damage to add
     */
    private static float calculateArmorPenBonus(float armorPen, float armorValue, float baseDamage) {
        Config.ArmorPenFormula formula;
        double multiplier;
        double flatBonus;

        // Safe config access with fallbacks
        try {
            formula = Config.ARMOR_PEN_FORMULA.get();
            multiplier = Config.ARMOR_PEN_MULTIPLIER.get();
            flatBonus = Config.ARMOR_PEN_FLAT_BONUS.get();
        } catch (Exception e) {
            // Config not loaded yet, use defaults
            formula = Config.ArmorPenFormula.SIMPLE;
            multiplier = 0.5;
            flatBonus = 2.0;
        }

        return switch (formula) {
            case SIMPLE -> {
                // Original formula: armorPen * armorValue * multiplier
                float ignoredArmor = armorValue * armorPen;
                yield ignoredArmor * (float) multiplier;
            }
            case VANILLA_ACCURATE -> {
                // Uses Minecraft's armor reduction formula for accurate penetration
                // Vanilla formula: damage * (1 - min(20, max(armor/5, armor - damage/(2 + toughness/4)))/25)
                // We calculate how much armor would have blocked and ignore that percentage
                float effectiveArmor = Math.min(20f, Math.max(armorValue / 5f, armorValue - baseDamage / 2f));
                float armorReduction = effectiveArmor / 25f; // 0.0 - 0.8 (max 80% reduction)
                float blockedDamage = baseDamage * armorReduction;
                yield blockedDamage * armorPen * (float) multiplier;
            }
            case PERCENTAGE -> {
                // Directly reduces armor effectiveness by percentage
                // E.g., 50% armor pen means armor only blocks 50% of what it normally would
                float effectiveArmor = Math.min(20f, armorValue);
                float normalReduction = effectiveArmor / 25f;
                float reducedReduction = normalReduction * (1f - armorPen);
                float bonusDamage = baseDamage * (normalReduction - reducedReduction);
                yield bonusDamage * (float) multiplier;
            }
            case FLAT_BONUS -> {
                // Adds flat true damage bonus regardless of armor
                yield armorPen * (float) flatBonus;
            }
        };
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

        // Create breakdown for environmental damage
        DamageBreakdown breakdown = new DamageBreakdown(
            ItemStack.EMPTY,       // No weapon
            victim,
            damage,
            1.0f,                  // No body part multiplier
            0f                     // No armor pen
        );

        // Create ImpactData
        // MULTIPLAYER-SAFE: for environmental damage, the "receiver" is the player itself
        // Use victim's UUID because we want the player to see their own received damage
        ImpactData impactData = new ImpactData(
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

        // Store for HUD overlay
        ImpactData.store(impactData);

        LOGGER.debug("Environmental damage: {}, amount={}, victim={}",
            damageSourceName, damage, victim.getName().getString());

        // Spawn 3D panel (proxy handles dist check)
        if (hitPoint != null) {
            ClientVFXProxy.addImpactVFX(hitPoint, new Vec3(0, 1, 0), impactData);
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
