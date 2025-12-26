package com.devmod.telemetry.duckdb.aggregation;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Aggregation window for combat events (hits, kills, damage).
 *
 * <p>Accumulates combat data over a configurable time window (default 30s),
 * then flushes as a single aggregated record to DuckDB.
 *
 * <p>Memory: ~2KB (counters + 8 weapons x 24B + 10 targets x 16B)
 */
public class CombatAggregateWindow {

    private final long windowDurationMs;
    private Instant windowStart;

    // ============================================
    // AGGREGATED COUNTERS
    // ============================================

    private int hitCount;
    private double totalDamage;
    private int killCount;
    private int criticalHits;
    private int missCount;

    // ============================================
    // PER-WEAPON STATS (LRU-limited)
    // ============================================

    private final Map<String, WeaponStats> weaponStats;
    private final int maxWeapons;

    // ============================================
    // PER-TARGET-TYPE STATS (LRU-limited)
    // ============================================

    private final Map<String, TargetStats> targetStats;
    private final int maxTargets;

    // ============================================
    // CONTEXT
    // ============================================

    private @Nullable UUID sessionId;
    private @Nullable UUID questId;
    private @Nullable String templateId;
    private @Nullable Integer templateVersion;

    /**
     * Create a combat aggregation window with default settings.
     */
    public CombatAggregateWindow() {
        this(AggregationConfig.COMBAT_WINDOW_MS,
             AggregationConfig.MAX_WEAPONS_PER_PLAYER,
             AggregationConfig.MAX_TARGET_TYPES_PER_PLAYER);
    }

    /**
     * Create a combat aggregation window with custom settings.
     */
    public CombatAggregateWindow(long windowDurationMs, int maxWeapons, int maxTargets) {
        this.windowDurationMs = windowDurationMs;
        this.maxWeapons = maxWeapons;
        this.maxTargets = maxTargets;
        this.windowStart = Instant.now();

        // LRU map for weapon stats
        this.weaponStats = Collections.synchronizedMap(
            new LinkedHashMap<String, WeaponStats>(maxWeapons + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, WeaponStats> eldest) {
                    return size() > CombatAggregateWindow.this.maxWeapons;
                }
            }
        );

        // LRU map for target stats
        this.targetStats = Collections.synchronizedMap(
            new LinkedHashMap<String, TargetStats>(maxTargets + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, TargetStats> eldest) {
                    return size() > CombatAggregateWindow.this.maxTargets;
                }
            }
        );
    }

    // ============================================
    // RECORD EVENTS
    // ============================================

    /**
     * Record a hit event.
     *
     * @param damage damage dealt
     * @param isKill whether this hit killed the target
     * @param isCritical whether this was a critical hit
     * @param weapon weapon identifier (nullable)
     * @param targetType target entity type (nullable)
     */
    public void recordHit(double damage, boolean isKill, boolean isCritical,
                          String weapon, String targetType) {
        hitCount++;
        totalDamage += damage;

        if (isKill) {
            killCount++;
        }

        if (isCritical) {
            criticalHits++;
        }

        // Update weapon stats
        if (weapon != null && !weapon.isEmpty()) {
            weaponStats.computeIfAbsent(weapon, k -> new WeaponStats())
                .record(damage, isKill);
        }

        // Update target stats
        if (targetType != null && !targetType.isEmpty()) {
            targetStats.computeIfAbsent(targetType, k -> new TargetStats())
                .record(damage);
        }
    }

    /**
     * Record a miss.
     */
    public void recordMiss() {
        missCount++;
    }

    // ============================================
    // WINDOW MANAGEMENT
    // ============================================

    /**
     * Check if window should be flushed (time elapsed or force threshold).
     */
    public boolean shouldFlush() {
        if (isEmpty()) {
            return false;
        }

        long elapsed = Instant.now().toEpochMilli() - windowStart.toEpochMilli();
        boolean timeExceeded = elapsed >= windowDurationMs;
        boolean forceThreshold = hitCount >= AggregationConfig.FORCE_FLUSH_EVENT_THRESHOLD;

        return timeExceeded || forceThreshold;
    }

    /**
     * Check if window has any data.
     */
    public boolean isEmpty() {
        return hitCount == 0 && missCount == 0;
    }

    /**
     * Get remaining time until window expires (milliseconds).
     */
    public long getRemainingMs() {
        long elapsed = Instant.now().toEpochMilli() - windowStart.toEpochMilli();
        return Math.max(0, windowDurationMs - elapsed);
    }

    /**
     * Flush window and return aggregate data.
     * Resets window for next interval.
     */
    public CombatAggregate flush() {
        Instant windowEnd = Instant.now();

        CombatAggregate aggregate = new CombatAggregate(
            windowStart,
            windowEnd,
            hitCount,
            totalDamage,
            killCount,
            criticalHits,
            missCount,
            new LinkedHashMap<>(weaponStats),
            new LinkedHashMap<>(targetStats),
            sessionId,
            questId,
            templateId,
            templateVersion
        );

        // Reset for next window
        reset();

        return aggregate;
    }

    /**
     * Reset window without returning data.
     */
    public void reset() {
        windowStart = Instant.now();
        hitCount = 0;
        totalDamage = 0;
        killCount = 0;
        criticalHits = 0;
        missCount = 0;
        weaponStats.clear();
        targetStats.clear();
    }

    // ============================================
    // CONTEXT SETTERS
    // ============================================

    public void setSessionId(@Nullable UUID sessionId) {
        this.sessionId = sessionId;
    }

    public void setQuestId(@Nullable UUID questId) {
        this.questId = questId;
    }

    public void setTemplateInfo(@Nullable String templateId, @Nullable Integer templateVersion) {
        this.templateId = templateId;
        this.templateVersion = templateVersion;
    }

    // ============================================
    // GETTERS (for monitoring)
    // ============================================

    public int getHitCount() {
        return hitCount;
    }

    public double getTotalDamage() {
        return totalDamage;
    }

    public int getKillCount() {
        return killCount;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    // ============================================
    // INNER CLASSES
    // ============================================

    /**
     * Per-weapon aggregated statistics.
     */
    public static class WeaponStats {
        private int hits;
        private double damage;
        private int kills;

        void record(double dmg, boolean isKill) {
            hits++;
            damage += dmg;
            if (isKill) kills++;
        }

        public int getHits() { return hits; }
        public double getDamage() { return damage; }
        public int getKills() { return kills; }

        public String toJson() {
            return String.format("{\"hits\":%d,\"damage\":%.2f,\"kills\":%d}", hits, damage, kills);
        }
    }

    /**
     * Per-target-type aggregated statistics.
     */
    public static class TargetStats {
        private int hits;
        private double damage;

        void record(double dmg) {
            hits++;
            damage += dmg;
        }

        public int getHits() { return hits; }
        public double getDamage() { return damage; }

        public String toJson() {
            return String.format("{\"hits\":%d,\"damage\":%.2f}", hits, damage);
        }
    }

    /**
     * Aggregated combat data for a single window.
     */
    public record CombatAggregate(
        Instant windowStart,
        Instant windowEnd,
        int hitCount,
        double totalDamage,
        int killCount,
        int criticalHits,
        int missCount,
        Map<String, WeaponStats> weaponStats,
        Map<String, TargetStats> targetStats,
        @Nullable UUID sessionId,
        @Nullable UUID questId,
        @Nullable String templateId,
        @Nullable Integer templateVersion
    ) {
        /**
         * Convert weapon stats to JSON for storage.
         */
        public @Nullable String weaponStatsJson() {
            if (weaponStats.isEmpty()) return null;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, WeaponStats> e : weaponStats.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":").append(e.getValue().toJson());
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }

        /**
         * Convert target stats to JSON for storage.
         */
        public @Nullable String targetStatsJson() {
            if (targetStats.isEmpty()) return null;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, TargetStats> e : targetStats.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":").append(e.getValue().toJson());
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }

        /**
         * Get accuracy ratio (hits / (hits + misses)).
         */
        public double getAccuracy() {
            int total = hitCount + missCount;
            return total > 0 ? (double) hitCount / total : 0.0;
        }
    }
}
