package com.devmod.combat.tracking;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.neoforged.fml.loading.FMLEnvironment;

public final class EvasionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvasionHandler.class);

    private static final ScheduledExecutorService SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DevMod-EvasionHandler");
            t.setDaemon(true);
            return t;
        });

    private record PendingAttack(long timestamp, Vec3 targetPosition, Vec3 playerEye, Vec3 lookDir) {}

    private static final Map<Integer, PendingAttack> pendingAttacks = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> confirmedHits = new ConcurrentHashMap<>();
    private static final Object LOCK = new Object();

    private static long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL_MS = 30_000;
    private static final long STALE_ENTRY_AGE_MS = 10_000;

    private EvasionHandler() {}

    /**
     * Records an attack attempt. Call this from AttackEntityEvent.
     */
    public static void recordAttackAttempt(Player attacker, LivingEntity target) {
        long now = System.currentTimeMillis();
        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 playerEye = attacker.getEyePosition();
        Vec3 lookDir = attacker.getLookAngle();

        pendingAttacks.put(target.getId(), new PendingAttack(now, targetPos, playerEye, lookDir));

        LOGGER.debug("Attack recorded: player={}, target={}, pos={}",
            attacker.getName().getString(), target.getName().getString(), targetPos);

        if (target instanceof EnderMan) {
            scheduleEvasionCheck(attacker, target, now);
        }
    }

    /**
     * Confirms that damage was actually dealt. Call this from LivingIncomingDamageEvent.
     */
    public static void confirmHit(LivingEntity target) {
        synchronized (LOCK) {
            confirmedHits.put(target.getId(), System.currentTimeMillis());
        }
    }

    /**
     * Cleanup stale tracking entries to prevent memory leaks.
     */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanupTime = now;

        int pendingRemoved = 0;
        var pendingIter = pendingAttacks.entrySet().iterator();
        while (pendingIter.hasNext()) {
            var entry = pendingIter.next();
            if (now - entry.getValue().timestamp > STALE_ENTRY_AGE_MS) {
                pendingIter.remove();
                pendingRemoved++;
            }
        }

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
            LOGGER.debug("Cleanup: removed {} pending, {} confirmed", pendingRemoved, hitsRemoved);
        }
    }

    /**
     * Shutdown the tracker. Call on server stop.
     */
    public static void shutdown() {
        SCHEDULER.shutdown();
        try {
            if (!SCHEDULER.awaitTermination(2, TimeUnit.SECONDS)) {
                SCHEDULER.shutdownNow();
            }
        } catch (InterruptedException e) {
            SCHEDULER.shutdownNow();
            Thread.currentThread().interrupt();
        }
        pendingAttacks.clear();
        confirmedHits.clear();
        LOGGER.info("EvasionHandler shutdown complete");
    }

    private static void scheduleEvasionCheck(Player player, LivingEntity target, long attackTime) {
        final int targetId = target.getId();

        var scheduled = SCHEDULER.schedule(() -> {
            synchronized (LOCK) {
                PendingAttack attackData = pendingAttacks.remove(targetId);
                if (attackData == null) return;

                Long hitTime = confirmedHits.remove(targetId);
                if (hitTime == null || hitTime < attackTime) {
                    LOGGER.debug("EVASION DETECTED at {}", attackData.targetPosition);
                    spawnMeleeEvasionPanelClientSafe(player, target, attackData.targetPosition, attackData.lookDir);
                } else {
                    LOGGER.debug("Attack confirmed, no evasion");
                }
            }
        }, 150, TimeUnit.MILLISECONDS);
        if (scheduled.isCancelled()) {
            LOGGER.debug("Evasion check canceled before execution");
        }
    }

    // ========== Client-safe VFX helpers ==========

    private static Method spawnMeleeEvasionMethod;
    private static boolean vfxMethodInitialized = false;

    /**
     * Spawns evasion VFX panel (client-only, server-safe).
     * Uses reflection to avoid loading client classes on dedicated server.
     */
    private static void spawnMeleeEvasionPanelClientSafe(Player player, LivingEntity target, Vec3 position, Vec3 lookDir) {
        if (!FMLEnvironment.dist.isClient()) {
            return; // No-op on server
        }

        try {
            if (!vfxMethodInitialized) {
                vfxMethodInitialized = true;
                Class<?> proxyClass = Class.forName("com.devmod.client.ClientVFXProxy");
                spawnMeleeEvasionMethod = proxyClass.getMethod("spawnMeleeEvasionPanel",
                    Player.class, LivingEntity.class, Vec3.class, Vec3.class);
            }
            if (spawnMeleeEvasionMethod != null) {
                spawnMeleeEvasionMethod.invoke(null, player, target, position, lookDir);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not spawn evasion VFX: {}", e.getMessage());
        }
    }
}
