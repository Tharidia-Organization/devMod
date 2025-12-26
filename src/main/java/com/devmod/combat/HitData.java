package com.devmod.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.Entity;
public class HitData {
    private static final Map<UUID, HitInfo> CONTEXT = new ConcurrentHashMap<>();
    private static final long EXPIRATION_MS = 100; // 100ms max lifespan per context entry

    /**
     * Store hit information for a target entity.
     * Called by DamageHandler before damage is applied.
     *
     * Thread-safe: synchronized to prevent race conditions with cleanup()
     * when store()/retrieve() called during cleanup (async mods).
     *
     * @param target Any entity (LivingEntity, Mob, custom modded entities)
     */
    public static synchronized void store(Entity target, HitHelper.BodyPart bodyPart, boolean isRanged) {
        store(target, bodyPart, isRanged, 0f);
    }

    /**
     * Store hit information with armor penetration bonus for telemetry.
     * Called by DamageHandler after armor pen calculation.
     *
     * @param target Any entity
     * @param bodyPart The body part hit
     * @param isRanged Whether this was a ranged attack
     * @param armorPenBonus The calculated armor penetration bonus damage
     */
    public static synchronized void store(Entity target, HitHelper.BodyPart bodyPart, boolean isRanged, float armorPenBonus) {
        CONTEXT.put(target.getUUID(), new HitInfo(bodyPart, isRanged, armorPenBonus, 0f, System.currentTimeMillis()));
    }

    /**
     * Store armor reduction separately (called after store() in DamageHandler).
     * Updates existing HitInfo with armor reduction value.
     *
     * @param target The victim entity
     * @param armorReduction The custom armor reduction percentage (0.0 - 0.8)
     */
    public static synchronized void storeArmorReduction(Entity target, float armorReduction) {
        HitInfo existing = CONTEXT.get(target.getUUID());
        if (existing != null) {
            // Update with armor reduction
            CONTEXT.put(target.getUUID(), new HitInfo(
                existing.bodyPart(),
                existing.isRanged(),
                existing.armorPenBonus(),
                armorReduction,
                existing.timestamp()
            ));
        }
    }

    /**
     * Retrieve and remove hit information for a target entity.
     * Called by TelemetryEvents to get the body part that was already calculated.
     *
     * Thread-safe: synchronized to prevent race conditions with cleanup()
     * when store()/retrieve() called during cleanup (async mods).
     *
     * @param target Any entity (supports modded entities)
     * @return HitInfo if found and not expired, null otherwise
     */
    public static synchronized HitInfo retrieve(Entity target) {
        HitInfo info = CONTEXT.remove(target.getUUID());
        if (info != null && System.currentTimeMillis() - info.timestamp > EXPIRATION_MS) {
            // Expired context, discard
            return null;
        }
        return info;
    }

    /**
     * Cleanup expired entries to prevent memory leaks.
     * Should be called periodically (e.g., every tick).
     *
     * Thread-safe: synchronized to prevent ConcurrentModificationException
     * when store()/retrieve() called during cleanup (async mods).
     */
    public static synchronized void cleanup() {
        long now = System.currentTimeMillis();
        CONTEXT.entrySet().removeIf(entry -> now - entry.getValue().timestamp > EXPIRATION_MS);
    }

    /**
     * Record containing hit information for telemetry.
     * @param bodyPart The body part that was hit
     * @param isRanged Whether this was a ranged attack
     * @param armorPenBonus The armor penetration bonus damage (for telemetry)
     * @param armorReduction The custom armor reduction percentage (0.0 - 0.8)
     * @param timestamp When this hit was recorded
     */
    public record HitInfo(HitHelper.BodyPart bodyPart, boolean isRanged, float armorPenBonus, float armorReduction, long timestamp) {}
}
