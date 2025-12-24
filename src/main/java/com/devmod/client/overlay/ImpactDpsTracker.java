package com.devmod.client.overlay;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks rolling DPS per player for the Impact HUD.
 */
public final class ImpactDpsTracker {
    private static final long WINDOW_MS = 5000;
    private static final int MAX_RECORDS = 100;

    private static final Map<UUID, Deque<DamageRecord>> DAMAGE_BY_PLAYER = new ConcurrentHashMap<>();

    private record DamageRecord(long timestamp, float damage) {}

    private ImpactDpsTracker() {}

    public static void recordDamage(UUID attackerId, float damage) {
        if (attackerId == null || damage <= 0f) {
            return;
        }

        long now = System.currentTimeMillis();
        Deque<DamageRecord> records = DAMAGE_BY_PLAYER.computeIfAbsent(attackerId, id -> new ArrayDeque<>());
        synchronized (records) {
            records.addLast(new DamageRecord(now, damage));
            prune(records, now);
            while (records.size() > MAX_RECORDS) {
                records.removeFirst();
            }
        }
    }

    public static float getCurrentDps(UUID attackerId) {
        if (attackerId == null) {
            return 0f;
        }

        Deque<DamageRecord> records = DAMAGE_BY_PLAYER.get(attackerId);
        if (records == null) {
            return 0f;
        }

        long now = System.currentTimeMillis();
        synchronized (records) {
            prune(records, now);
            if (records.isEmpty()) {
                return 0f;
            }

            float total = 0f;
            long oldest = now;
            for (DamageRecord record : records) {
                total += record.damage;
                if (record.timestamp < oldest) {
                    oldest = record.timestamp;
                }
            }

            long windowMs = Math.max(1, now - oldest);
            return total / (windowMs / 1000f);
        }
    }

    public static void clearForPlayer(UUID attackerId) {
        if (attackerId == null) {
            return;
        }
        DAMAGE_BY_PLAYER.remove(attackerId);
    }

    public static void clearAll() {
        DAMAGE_BY_PLAYER.clear();
    }

    private static void prune(Deque<DamageRecord> records, long now) {
        while (!records.isEmpty()) {
            DamageRecord oldest = records.peekFirst();
            if (oldest == null) {
                break;
            }
            if (now - oldest.timestamp > WINDOW_MS) {
                records.removeFirst();
            } else {
                break;
            }
        }
    }
}
