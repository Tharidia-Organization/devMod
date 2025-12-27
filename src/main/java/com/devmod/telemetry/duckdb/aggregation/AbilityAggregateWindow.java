package com.devmod.telemetry.duckdb.aggregation;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Aggregation window for ability events (dash, dodge, perfect dodge, etc.).
 *
 * <p>Accumulates ability usage over a configurable time window (default 60s),
 * then flushes as aggregated records to DuckDB.
 *
 * <p>Memory: ~500B (6 ability types x 32B + metadata)
 */
public class AbilityAggregateWindow {

    private static final Locale NORMALIZE_LOCALE = Locale.ROOT;

    /**
     * Supported ability types for aggregation.
     */
    public enum AbilityType {
        DASH,
        DODGE,
        PERFECT_DODGE,
        BLOCK,
        PARRY,
        EXHAUSTION,
        OTHER
    }

    private final long windowDurationMs;
    private Instant windowStart;

    // ============================================
    // PER-ABILITY STATS
    // ============================================

    private final EnumMap<AbilityType, AbilityStats> abilityStats;

    // For custom ability types not in the enum
    private final Map<String, AbilityStats> customAbilityStats;

    // ============================================
    // CONTEXT
    // ============================================

    private @Nullable UUID sessionId;

    /**
     * Create an ability aggregation window with default settings.
     */
    public AbilityAggregateWindow() {
        this(AggregationConfig.ABILITY_WINDOW_MS);
    }

    /**
     * Create an ability aggregation window with custom duration.
     */
    public AbilityAggregateWindow(long windowDurationMs) {
        this.windowDurationMs = windowDurationMs;
        this.windowStart = Instant.now();
        this.abilityStats = new EnumMap<>(AbilityType.class);
        this.customAbilityStats = new HashMap<>();

        // Initialize all ability types
        for (AbilityType type : AbilityType.values()) {
            abilityStats.put(type, new AbilityStats());
        }
    }

    // ============================================
    // RECORD EVENTS
    // ============================================

    /**
     * Record an ability usage event.
     *
     * @param abilityType type of ability (string, will be normalized)
     * @param success whether the ability succeeded
     * @param staminaCost stamina spent
     * @param damageNegated damage negated (for defensive abilities)
     */
    public void recordAbility(String abilityType, boolean success,
                              double staminaCost, double damageNegated) {
        if (abilityType == null || abilityType.isEmpty()) return;

        AbilityType type = parseAbilityType(abilityType);
        AbilityStats stats;

        if (type != AbilityType.OTHER) {
            stats = Objects.requireNonNull(abilityStats.get(type),
                "Ability stats should be initialized");
        } else {
            // Custom ability type
            stats = customAbilityStats.computeIfAbsent(
                abilityType.toLowerCase(NORMALIZE_LOCALE),
                k -> new AbilityStats()
            );
        }

        stats.record(success, staminaCost, damageNegated);
    }

    /**
     * Parse ability type string to enum.
     */
    private AbilityType parseAbilityType(String type) {
        String normalized = type.toUpperCase(NORMALIZE_LOCALE).replace(" ", "_").replace("-", "_");
        try {
            return AbilityType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // Check common variations
            if (normalized.contains("DASH")) return AbilityType.DASH;
            if (normalized.contains("DODGE") && normalized.contains("PERFECT")) return AbilityType.PERFECT_DODGE;
            if (normalized.contains("DODGE")) return AbilityType.DODGE;
            if (normalized.contains("BLOCK")) return AbilityType.BLOCK;
            if (normalized.contains("PARRY")) return AbilityType.PARRY;
            if (normalized.contains("EXHAUST")) return AbilityType.EXHAUSTION;
            return AbilityType.OTHER;
        }
    }

    // ============================================
    // WINDOW MANAGEMENT
    // ============================================

    /**
     * Check if window should be flushed.
     */
    public boolean shouldFlush() {
        if (isEmpty()) return false;

        long elapsed = Instant.now().toEpochMilli() - windowStart.toEpochMilli();
        return elapsed >= windowDurationMs;
    }

    /**
     * Check if window has any data.
     */
    public boolean isEmpty() {
        for (AbilityStats stats : abilityStats.values()) {
            if (stats.attempts > 0) return false;
        }
        for (AbilityStats stats : customAbilityStats.values()) {
            if (stats.attempts > 0) return false;
        }
        return true;
    }

    /**
     * Flush window and return aggregate data.
     */
    public AbilityAggregate flush() {
        Instant windowEnd = Instant.now();

        // Copy stats
        Map<String, AbilityStats> allStats = new HashMap<>();
        for (Map.Entry<AbilityType, AbilityStats> e : abilityStats.entrySet()) {
            if (e.getValue().attempts > 0) {
                allStats.put(e.getKey().name().toLowerCase(NORMALIZE_LOCALE), e.getValue().copy());
            }
        }
        allStats.putAll(customAbilityStats);

        AbilityAggregate aggregate = new AbilityAggregate(
            windowStart,
            windowEnd,
            allStats,
            sessionId
        );

        // Reset
        reset();

        return aggregate;
    }

    /**
     * Reset window.
     */
    public void reset() {
        windowStart = Instant.now();
        for (AbilityStats stats : abilityStats.values()) {
            stats.reset();
        }
        customAbilityStats.clear();
    }

    // ============================================
    // CONTEXT SETTERS
    // ============================================

    public void setSessionId(@Nullable UUID sessionId) {
        this.sessionId = sessionId;
    }

    // ============================================
    // GETTERS
    // ============================================

    public int getTotalAttempts() {
        int total = 0;
        for (AbilityStats stats : abilityStats.values()) {
            total += stats.attempts;
        }
        for (AbilityStats stats : customAbilityStats.values()) {
            total += stats.attempts;
        }
        return total;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    // ============================================
    // INNER CLASSES
    // ============================================

    /**
     * Statistics for a single ability type.
     */
    public static class AbilityStats {
        private int attempts;
        private int successes;
        private double totalStaminaCost;
        private double totalDamageNegated;

        void record(boolean success, double staminaCost, double damageNegated) {
            attempts++;
            if (success) successes++;
            totalStaminaCost += staminaCost;
            totalDamageNegated += damageNegated;
        }

        void reset() {
            attempts = 0;
            successes = 0;
            totalStaminaCost = 0;
            totalDamageNegated = 0;
        }

        AbilityStats copy() {
            AbilityStats c = new AbilityStats();
            c.attempts = this.attempts;
            c.successes = this.successes;
            c.totalStaminaCost = this.totalStaminaCost;
            c.totalDamageNegated = this.totalDamageNegated;
            return c;
        }

        public int getAttempts() { return attempts; }
        public int getSuccesses() { return successes; }
        public double getTotalStaminaCost() { return totalStaminaCost; }
        public double getTotalDamageNegated() { return totalDamageNegated; }

        public double getSuccessRate() {
            return attempts > 0 ? (double) successes / attempts : 0.0;
        }
    }

    /**
     * Aggregated ability data for a single window.
     */
    public record AbilityAggregate(
        Instant windowStart,
        Instant windowEnd,
        Map<String, AbilityStats> abilityStats,
        @Nullable UUID sessionId
    ) {
        /**
         * Get total attempts across all abilities.
         */
        public int getTotalAttempts() {
            return abilityStats.values().stream()
                .mapToInt(AbilityStats::getAttempts)
                .sum();
        }

        /**
         * Get total stamina spent.
         */
        public double getTotalStaminaSpent() {
            return abilityStats.values().stream()
                .mapToDouble(AbilityStats::getTotalStaminaCost)
                .sum();
        }
    }
}
