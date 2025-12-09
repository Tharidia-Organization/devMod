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

    // Scheduled executor per evasion check (sostituisce Thread.sleep)
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

            // 1. Identifichiamo l'arma e la parte colpita
            if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow) {
                // RANGED: Usa coordinata Y della freccia (PRECISIONE 100%)
                weapon = attacker.getMainHandItem();
                part = HitHelper.getBodyPart(victim, arrow.getY());
                hitPoint = arrow.position();
                // SAFETY: getDeltaMovement() can return zero vector for stationary arrows
                Vec3 delta = arrow.getDeltaMovement();
                slashDirection = (delta.lengthSqr() > 0.0001) ? delta.normalize() : arrow.getViewVector(1.0f);
                isRanged = true;
            } else {
                // MELEE: Usa AABB subdivision raycast (PRECISIONE 95%)
                weapon = attacker.getMainHandItem();
                HitHelper.HitResult hitResult = HitHelper.rayTraceBodyPartWithHitPoint(attacker, victim);
                part = hitResult.part();
                hitPoint = hitResult.hitPoint();
                slashDirection = attacker.getViewVector(1.0F);
                isRanged = false;
            }

            // Conferma che il danno è arrivato (per il tracking evasioni Enderman)
            confirmHit(victim);

            // 2. Recuperiamo le Statistiche (Globali o Specifiche)
            WeaponStats stats = WeaponConfigManager.getStats(weapon);

            // 3. Calcolo Moltiplicatore
            float multiplier = 1.0f;
            String partKey = "devmod.bodypart.body";
            int color = 0xFFFFFF;

            switch (part) {
                case HEAD -> { multiplier = stats.headMult; partKey = "devmod.bodypart.head"; color = 0xFF5555; }
                case BODY -> { multiplier = stats.bodyMult; partKey = "devmod.bodypart.body"; color = 0x55FF55; }
                case ARMS -> { multiplier = stats.armsMult; partKey = "devmod.bodypart.arms"; color = 0xFFAA00; }
                case LEGS -> { multiplier = stats.legsMult; partKey = "devmod.bodypart.legs"; color = 0x55FFFF; }
            }

            // 4. Calcolo Danni Finali
            float originalDamage = event.getAmount();
            float newDamage = (originalDamage + stats.baseDamageBonus) * multiplier;

            // 5. Penetrazione Armatura (formula configurabile via Config)
            // Calculate armor pen bonus ONCE for both damage application AND telemetry
            float armorPenBonus = 0f;
            if (stats.armorPenetration > 0) {
                armorPenBonus = calculateArmorPenBonus(stats.armorPenetration, victim.getArmorValue(), newDamage);
                newDamage += armorPenBonus;
            }

            // Store body part AND armor pen bonus in context for telemetry
            HitContext.store(victim, part, isRanged, armorPenBonus);

            // 6. Applica
            event.setAmount(newDamage);

            // 7. Feedback Visivo (actionbar) - Uses translatable component for i18n
            if (attacker instanceof ServerPlayer player) {
                String dmgText = String.format("%.1f", newDamage);
                String penText = stats.armorPenetration > 0 ? " [Pen]" : "";

                player.displayClientMessage(
                        Component.literal("§7Hit: §" + getChar(color))
                            .append(Component.translatable(partKey))
                            .append(Component.literal(" §fDmg: " + dmgText + penText)),
                        true
                );
            }

            // 8. HUD Impact Analysis - Crea e salva i dati per l'overlay
            // Note: armorPenBonus already calculated above for damage and telemetry

            // Crea breakdown dettagliato
            DamageBreakdown breakdown = new DamageBreakdown(
                weapon,
                victim,
                originalDamage,
                multiplier,
                armorPenBonus
            );

            // Determina fonte attacco
            String attackSource = isRanged ? "Ranged Attack" : "Melee Attack";

            // Crea e salva ImpactData per l'HUD (con posizione impatto)
            // MULTIPLAYER-SAFE: passa UUID dell'attaccante per isolamento dati
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

            // Aggiungi effetto VFX 3D (marker + slash animation)
            // In singleplayer, l'evento damage gira sul server integrato ma possiamo
            // accedere alle classi client perché siamo sullo stesso processo
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

    // === Tracking per rilevare evasioni Enderman ===
    // Record per salvare i dati dell'attacco al momento in cui avviene
    private record PendingAttack(long timestamp, Vec3 targetPosition, Vec3 playerEye, Vec3 lookDir) {}

    // Mappa: targetId -> dati dell'attacco
    private static final Map<Integer, PendingAttack> pendingAttacks = new ConcurrentHashMap<>();
    // Mappa: targetId -> timestamp dell'ultimo LivingIncomingDamageEvent
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
     * Cattura TUTTI i tentativi di attacco (prima che il danno venga calcolato).
     * Questo evento si attiva anche quando l'Enderman evade!
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof LivingEntity target)) return;

        Player player = event.getEntity();
        long now = System.currentTimeMillis();

        // IMPORTANTE: Salva la posizione dell'Enderman ORA, prima che si teletrasporti!
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 playerEye = player.getEyePosition();
        Vec3 lookDir = player.getLookAngle();

        // Registra il tentativo di attacco con tutte le posizioni
        pendingAttacks.put(target.getId(), new PendingAttack(now, targetPos, playerEye, lookDir));

        LOGGER.debug("AttackEntityEvent: player={}, target={}, targetPos={}",
            player.getName().getString(), target.getName().getString(), targetPos);

        // Dopo 100ms, controlla se il danno è stato confermato
        // Se non è stato confermato, l'attacco è stato evaso
        if (target instanceof EnderMan) {
            // Schedula un check differito per vedere se il danno è arrivato
            scheduleEvasionCheck(player, target, now);
        }
    }

    /**
     * Schedula un controllo per rilevare se l'Enderman ha evaso.
     * Usa ScheduledExecutorService invece di Thread.sleep per evitare blocchi.
     * THREAD SAFETY: Uses synchronized block to prevent race with confirmHit().
     */
    private static void scheduleEvasionCheck(Player player, LivingEntity target, long attackTime) {
        final int targetId = target.getId();

        // Usa ScheduledExecutor invece di Thread.sleep
        EVASION_SCHEDULER.schedule(() -> {
            // THREAD SAFETY: Synchronize access to both maps for atomic check-then-act
            synchronized (EVASION_LOCK) {
                // Recupera i dati salvati al momento dell'attacco
                PendingAttack attackData = pendingAttacks.remove(targetId);
                if (attackData == null) return;

                // Controlla se il danno è stato confermato
                Long hitTime = confirmedHits.remove(targetId); // Also remove to cleanup
                if (hitTime == null || hitTime < attackTime) {
                    // Il danno NON è stato confermato -> EVASIONE!
                    LOGGER.debug("EVASION DETECTED! Enderman evaded attack at {}", attackData.targetPosition);

                    // Spawna un pannello "EVADED" (proxy handles dist check)
                    ClientVFXProxy.spawnMeleeEvasionPanel(player, target, attackData.targetPosition, attackData.lookDir);
                } else {
                    LOGGER.debug("Attack confirmed, no evasion");
                }
            }
        }, 150, TimeUnit.MILLISECONDS);
    }

    /**
     * Chiamato quando un danno viene confermato (da onDamage).
     * THREAD SAFETY: Uses synchronized block to prevent race with scheduleEvasionCheck().
     */
    private static void confirmHit(LivingEntity target) {
        synchronized (EVASION_LOCK) {
            confirmedHits.put(target.getId(), System.currentTimeMillis());
        }
    }

    // === Handler per danni ambientali (fuoco, lava, caduta, etc.) ===

    /**
     * Traccia danni ambientali che NON hanno un attacker LivingEntity.
     * Include: fuoco, lava, annegamento, caduta, cactus, soffocamento, etc.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.NORMAL)
    public static void onEnvironmentalDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();

        // Skip se c'è già un attacker LivingEntity (gestito da onDamage)
        if (event.getSource().getEntity() instanceof LivingEntity) {
            return;
        }

        // Solo per il player (mostra HUD)
        if (!(victim instanceof Player)) {
            return;
        }

        // Determina il tipo di danno ambientale
        String damageSourceName = getEnvironmentalDamageSource(event);
        if (damageSourceName == null) {
            return; // Tipo di danno non tracciato
        }

        float damage = event.getAmount();

        // Posizione impatto = centro della vittima
        Vec3 hitPoint = victim.position().add(0, victim.getBbHeight() * 0.5, 0);

        // Crea breakdown per danno ambientale
        DamageBreakdown breakdown = new DamageBreakdown(
            ItemStack.EMPTY,       // Nessuna arma
            victim,
            damage,
            1.0f,                  // Nessun moltiplicatore body part
            0f                     // Nessuna armor pen
        );

        // Crea ImpactData
        // MULTIPLAYER-SAFE: per danno ambientale, il "receiver" è il player stesso
        // Usa l'UUID della vittima perché vogliamo che il player veda i propri danni subiti
        ImpactData impactData = new ImpactData(
            victim.getUUID(),         // UUID del player che subisce il danno
            victim,
            HitHelper.BodyPart.BODY,  // Danno ambientale = body generico
            1.0f,
            breakdown,
            damageSourceName,         // Es: "Fire Damage", "Fall Damage"
            false,
            hitPoint,
            new Vec3(0, -1, 0)        // Direzione generica (verso il basso)
        );

        // Salva per HUD overlay
        ImpactData.store(impactData);

        LOGGER.debug("Environmental damage: {}, amount={}, victim={}",
            damageSourceName, damage, victim.getName().getString());

        // Spawna pannello 3D (proxy handles dist check)
        if (hitPoint != null) {
            ClientVFXProxy.addImpactVFX(hitPoint, new Vec3(0, 1, 0), impactData);
        }
    }

    /**
     * Identifica il tipo di danno ambientale.
     * Uses config-based damage type mappings loaded from damage_types.json.
     * Custom damage types can be added to the config without recompiling.
     *
     * <p>Now always returns a label (never null) for damage types without
     * an attacker - unknown types get a formatted fallback label so HUD
     * panels always show something.</p>
     *
     * @return Nome leggibile del tipo di danno (never null for environmental damage)
     */
    private static String getEnvironmentalDamageSource(LivingIncomingDamageEvent event) {
        var source = event.getSource();

        // Special case: Mace smash detection (requires runtime check, not just type matching)
        if (source.is(DamageTypes.PLAYER_ATTACK)) {
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
