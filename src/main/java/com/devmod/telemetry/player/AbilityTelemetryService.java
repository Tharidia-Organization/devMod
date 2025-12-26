package com.devmod.telemetry.player;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.devmod.abilities.DodgeAbilitySystem;
import com.devmod.telemetry.TelemetryService;
import com.devmod.telemetry.duckdb.DuckDBConfig;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;
import com.devmod.telemetry.util.BitPackedFlags;

public class AbilityTelemetryService {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final AbilityTelemetryService INSTANCE = new AbilityTelemetryService();

    // Per-player session stats
    private final Map<UUID, AbilitySessionStats> sessionStats = new ConcurrentHashMap<>();

    private AbilityTelemetryService() {}

    /**
     * Get or create session stats for a player.
     */
    private AbilitySessionStats getStats(UUID playerId) {
        return sessionStats.computeIfAbsent(playerId, id -> new AbilitySessionStats());
    }

    /**
     * Record a dash attempt.
     * PERFORMANCE: Uses StringBuilder instead of String.format (~10x faster).
     */
    public void recordDashAttempt(UUID playerId, boolean success, float staminaBefore, float staminaAfter) {
        AbilitySessionStats stats = getStats(playerId);
        stats.dashAttempts++;
        if (success) {
            stats.dashSuccesses++;
            stats.totalStaminaUsedDash += (staminaBefore - staminaAfter);
        }

        float staminaCost = staminaBefore - staminaAfter;
        float successRate = stats.dashAttempts > 0 ? (float) stats.dashSuccesses / stats.dashAttempts : 0;

        StringBuilder json = new StringBuilder(180);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"playerId\":\"").append(playerId).append("\",");
        json.append("\"type\":\"dash\",");
        json.append("\"success\":").append(success).append(",");
        json.append("\"staminaBefore\":"); appendFloat1(json, staminaBefore); json.append(",");
        json.append("\"staminaAfter\":"); appendFloat1(json, staminaAfter); json.append(",");
        json.append("\"staminaCost\":"); appendFloat1(json, staminaCost); json.append(",");
        json.append("\"totalDashes\":").append(stats.dashSuccesses).append(",");
        json.append("\"successRate\":"); appendFloat2(json, successRate);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logAbility(playerId, "dash", success,
            null, (double) staminaBefore, (double) staminaAfter, (double) staminaCost,
            null, null, null, null);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendAbilityUsageLine(json.toString());
        }

        LOGGER.debug("[AbilityTelemetry] Dash: {} - success={}", playerId, success);
    }

    /**
     * Record a dodge attempt.
     * PERFORMANCE: Uses bit-packed result (direction + success in 3 bits) and StringBuilder.
     */
    public void recordDodgeAttempt(UUID playerId, boolean success, DodgeAbilitySystem.DodgeDirection direction,
                                    float staminaBefore, float staminaAfter) {
        AbilitySessionStats stats = getStats(playerId);
        stats.dodgeAttempts++;
        if (success) {
            stats.dodgeSuccesses++;
            stats.totalStaminaUsedDodge += (staminaBefore - staminaAfter);

            // Track direction distribution
            switch (direction) {
                case LEFT -> stats.dodgeDirectionLeft++;
                case RIGHT -> stats.dodgeDirectionRight++;
                case BACK -> stats.dodgeDirectionBack++;
                case FORWARD -> stats.dodgeDirectionForward++;
            }
        }

        // PERFORMANCE: Bit-packed result: [0-1]=direction (0-3), [2]=success
        // Decoding: direction = result & 0x03, success = (result & 0x04) != 0
        int dirOrdinal = direction != null ? direction.ordinal() : 2; // BACK=2 default
        int packedResult = BitPackedFlags.packDodgeResult(dirOrdinal, success);

        float staminaCost = staminaBefore - staminaAfter;
        float successRate = stats.dodgeAttempts > 0 ? (float) stats.dodgeSuccesses / stats.dodgeAttempts : 0;

        // PERFORMANCE: StringBuilder instead of String.format (~10x faster)
        StringBuilder json = new StringBuilder(200);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"playerId\":\"").append(playerId).append("\",");
        json.append("\"type\":\"dodge\",");
        json.append("\"result\":").append(packedResult).append(","); // Bit-packed: dir + success
        json.append("\"staminaBefore\":"); appendFloat1(json, staminaBefore); json.append(",");
        json.append("\"staminaAfter\":"); appendFloat1(json, staminaAfter); json.append(",");
        json.append("\"staminaCost\":"); appendFloat1(json, staminaCost); json.append(",");
        json.append("\"totalDodges\":").append(stats.dodgeSuccesses).append(",");
        json.append("\"successRate\":"); appendFloat2(json, successRate);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logAbility(playerId, "dodge", success,
            packedResult, (double) staminaBefore, (double) staminaAfter, (double) staminaCost,
            null, null, null, null);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendAbilityUsageLine(json.toString());
        }

        LOGGER.debug("[AbilityTelemetry] Dodge: {} - result={} (dir={}, success={})", playerId, packedResult, dirOrdinal, success);
    }

    /**
     * Record a perfect dodge (damage negated by i-frames).
     * PERFORMANCE: Uses StringBuilder instead of String.format.
     */
    public void recordPerfectDodge(UUID playerId, float damageNegated, String damageSource) {
        AbilitySessionStats stats = getStats(playerId);
        stats.perfectDodges++;
        stats.totalDamageNegated += damageNegated;

        StringBuilder json = new StringBuilder(180);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"playerId\":\"").append(playerId).append("\",");
        json.append("\"type\":\"perfect_dodge\",");
        json.append("\"damageNegated\":"); appendFloat1(json, damageNegated); json.append(",");
        json.append("\"source\":\"").append(escape(damageSource)).append("\",");
        json.append("\"totalPerfectDodges\":").append(stats.perfectDodges).append(",");
        json.append("\"totalDamageNegated\":"); appendFloat1(json, stats.totalDamageNegated);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logAbility(playerId, "perfect_dodge", true,
            null, null, null, null,
            (double) damageNegated, damageSource, null, null);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendAbilityUsageLine(json.toString());
        }

        LOGGER.debug("[AbilityTelemetry] Perfect Dodge: {} - negated {} damage from {}",
            playerId, damageNegated, damageSource);
    }

    /**
     * Record stamina exhaustion event.
     */
    public void recordExhaustion(UUID playerId, String context) {
        AbilitySessionStats stats = getStats(playerId);
        stats.exhaustionCount++;

        String json = String.format(
            "{\"ts\":\"%s\",\"playerId\":\"%s\",\"type\":\"exhaustion\"," +
            "\"context\":\"%s\",\"totalExhaustions\":%d}",
            Instant.now(), playerId, escape(context), stats.exhaustionCount
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logAbility(playerId, "exhaustion", null,
            null, null, null, null,
            null, null, context, null);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendAbilityUsageLine(json);
        }

        LOGGER.debug("[AbilityTelemetry] Exhaustion: {} - context={}", playerId, context);
    }

    /**
     * Record stamina full regeneration.
     */
    public void recordStaminaFull(UUID playerId, long regenTimeMs) {
        AbilitySessionStats stats = getStats(playerId);
        stats.fullRegenCount++;
        stats.totalRegenTimeMs += regenTimeMs;

        String json = String.format(
            "{\"ts\":\"%s\",\"playerId\":\"%s\",\"type\":\"stamina_full\"," +
            "\"regenTimeMs\":%d,\"avgRegenTimeMs\":%d}",
            Instant.now(), playerId, regenTimeMs,
            stats.fullRegenCount > 0 ? stats.totalRegenTimeMs / stats.fullRegenCount : 0
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logAbility(playerId, "stamina_full", null,
            null, null, null, null,
            null, null, null, regenTimeMs);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendAbilityUsageLine(json);
        }
    }

    /**
     * Export session summary for a player.
     */
    public void exportSessionSummary(UUID playerId) {
        AbilitySessionStats stats = sessionStats.get(playerId);
        if (stats == null) return;

        String json = String.format(
            "{\"ts\":\"%s\",\"playerId\":\"%s\",\"type\":\"session_summary\"," +
            "\"dashAttempts\":%d,\"dashSuccesses\":%d,\"dashSuccessRate\":%.2f," +
            "\"dodgeAttempts\":%d,\"dodgeSuccesses\":%d,\"dodgeSuccessRate\":%.2f," +
            "\"perfectDodges\":%d,\"totalDamageNegated\":%.1f," +
            "\"exhaustionCount\":%d," +
            "\"dodgeDirections\":{\"left\":%d,\"right\":%d,\"back\":%d,\"forward\":%d}," +
            "\"avgStaminaPerDash\":%.1f,\"avgStaminaPerDodge\":%.1f}",
            Instant.now(), playerId,
            stats.dashAttempts, stats.dashSuccesses,
            stats.dashAttempts > 0 ? (float) stats.dashSuccesses / stats.dashAttempts : 0,
            stats.dodgeAttempts, stats.dodgeSuccesses,
            stats.dodgeAttempts > 0 ? (float) stats.dodgeSuccesses / stats.dodgeAttempts : 0,
            stats.perfectDodges, stats.totalDamageNegated, stats.exhaustionCount,
            stats.dodgeDirectionLeft, stats.dodgeDirectionRight,
            stats.dodgeDirectionBack, stats.dodgeDirectionForward,
            stats.dashSuccesses > 0 ? stats.totalStaminaUsedDash / stats.dashSuccesses : 0,
            stats.dodgeSuccesses > 0 ? stats.totalStaminaUsedDodge / stats.dodgeSuccesses : 0
        );

        // NDJSON: Fallback only (no DuckDB mapping for session_summary yet)
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendAbilityUsageLine(json);
        }
        LOGGER.info("[AbilityTelemetry] Session summary exported for {}", playerId);
    }

    /**
     * Clean up player data on disconnect.
     */
    public void cleanupPlayer(UUID playerId) {
        // Export summary before cleanup
        exportSessionSummary(playerId);
        sessionStats.remove(playerId);
    }

    /**
     * Get session stats for analytics.
     */
    public @Nullable AbilitySessionStats getSessionStats(UUID playerId) {
        return sessionStats.get(playerId);
    }

    // ============================================
    // PERFORMANCE: Fast number formatting helpers
    // ============================================

    /** Append float with 1 decimal place without String.format() */
    private static void appendFloat1(StringBuilder sb, float value) {
        long scaled = Math.round(value * 10.0);
        long intPart = scaled / 10;
        long decPart = Math.abs(scaled % 10);
        sb.append(intPart).append('.').append(decPart);
    }

    /** Append float with 2 decimal places without String.format() */
    private static void appendFloat2(StringBuilder sb, float value) {
        long scaled = Math.round(value * 100.0);
        long intPart = scaled / 100;
        long decPart = Math.abs(scaled % 100);
        sb.append(intPart).append('.');
        if (decPart < 10) sb.append('0');
        sb.append(decPart);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Session statistics for a player.
     */
    public static class AbilitySessionStats {
        // Dash stats
        public int dashAttempts = 0;
        public int dashSuccesses = 0;
        public float totalStaminaUsedDash = 0;

        // Dodge stats
        public int dodgeAttempts = 0;
        public int dodgeSuccesses = 0;
        public float totalStaminaUsedDodge = 0;
        public int perfectDodges = 0;
        public float totalDamageNegated = 0;

        // Dodge direction distribution
        public int dodgeDirectionLeft = 0;
        public int dodgeDirectionRight = 0;
        public int dodgeDirectionBack = 0;
        public int dodgeDirectionForward = 0;

        // Stamina stats
        public int exhaustionCount = 0;
        public int fullRegenCount = 0;
        public long totalRegenTimeMs = 0;
    }
}
