package com.devmod.telemetry.duckdb.aggregation;

/**
 * Configuration for telemetry aggregation and LVC (Last Value Cache).
 * Supports JVM property and environment variable overrides.
 *
 * This system provides 121x write reduction by aggregating high-volume events
 * into time windows while maintaining instant real-time queries via LVC.
 */
public final class AggregationConfig {

    private AggregationConfig() {} // Utility class

    // ============================================
    // AGGREGATION WINDOWS
    // ============================================

    /** Combat aggregation window duration (30 seconds default) */
    public static final long COMBAT_WINDOW_MS = getLong(
        "devmod.aggregation.combat_window_ms",
        "DEVMOD_AGGREGATION_COMBAT_WINDOW_MS",
        30_000
    );

    /** Ability aggregation window duration (60 seconds default) */
    public static final long ABILITY_WINDOW_MS = getLong(
        "devmod.aggregation.ability_window_ms",
        "DEVMOD_AGGREGATION_ABILITY_WINDOW_MS",
        60_000
    );

    /** Heatmap aggregation window duration (60 seconds default) */
    public static final long HEATMAP_WINDOW_MS = getLong(
        "devmod.aggregation.heatmap_window_ms",
        "DEVMOD_AGGREGATION_HEATMAP_WINDOW_MS",
        60_000
    );

    /** Player snapshot sample interval (10 seconds default - 1 snapshot per 10s) */
    public static final long SNAPSHOT_SAMPLE_INTERVAL_MS = getLong(
        "devmod.aggregation.snapshot_interval_ms",
        "DEVMOD_AGGREGATION_SNAPSHOT_INTERVAL_MS",
        10_000
    );

    // ============================================
    // FLUSH SETTINGS
    // ============================================

    /** Periodic flush interval for all aggregators (60 seconds default) */
    public static final long PERIODIC_FLUSH_INTERVAL_MS = getLong(
        "devmod.aggregation.flush_interval_ms",
        "DEVMOD_AGGREGATION_FLUSH_INTERVAL_MS",
        60_000
    );

    /** Force flush if window has this many events (prevents memory bloat) */
    public static final int FORCE_FLUSH_EVENT_THRESHOLD = getInt(
        "devmod.aggregation.force_flush_threshold",
        "DEVMOD_AGGREGATION_FORCE_FLUSH_THRESHOLD",
        1000
    );

    // ============================================
    // MEMORY LIMITS
    // ============================================

    /** Maximum weapons tracked per player (LRU eviction beyond this) */
    public static final int MAX_WEAPONS_PER_PLAYER = getInt(
        "devmod.aggregation.max_weapons",
        "DEVMOD_AGGREGATION_MAX_WEAPONS",
        8
    );

    /** Maximum target types tracked per player */
    public static final int MAX_TARGET_TYPES_PER_PLAYER = getInt(
        "devmod.aggregation.max_target_types",
        "DEVMOD_AGGREGATION_MAX_TARGET_TYPES",
        10
    );

    /** Maximum heatmap grid cells (sparse storage) */
    public static final int MAX_HEATMAP_CELLS = getInt(
        "devmod.aggregation.max_heatmap_cells",
        "DEVMOD_AGGREGATION_MAX_HEATMAP_CELLS",
        64
    );

    /** Heatmap grid size (16x16 default) */
    public static final int HEATMAP_GRID_SIZE = getInt(
        "devmod.aggregation.heatmap_grid_size",
        "DEVMOD_AGGREGATION_HEATMAP_GRID_SIZE",
        16
    );

    // ============================================
    // LVC (LAST VALUE CACHE) SETTINGS
    // ============================================

    /** Rolling DPS window size in seconds */
    public static final int LVC_DPS_WINDOW_SECONDS = getInt(
        "devmod.lvc.dps_window_seconds",
        "DEVMOD_LVC_DPS_WINDOW_SECONDS",
        60
    );

    /** Maximum players in top-N leaderboard queries */
    public static final int LVC_TOP_N_LIMIT = getInt(
        "devmod.lvc.top_n_limit",
        "DEVMOD_LVC_TOP_N_LIMIT",
        20
    );

    /** LVC entry stale threshold (remove entries older than this on query) */
    public static final long LVC_STALE_THRESHOLD_MS = getLong(
        "devmod.lvc.stale_threshold_ms",
        "DEVMOD_LVC_STALE_THRESHOLD_MS",
        300_000  // 5 minutes
    );

    // ============================================
    // FEATURE TOGGLES
    // ============================================

    /** Enable aggregation layer (can be disabled for debugging) */
    public static final boolean AGGREGATION_ENABLED = getBoolean(
        "devmod.aggregation.enabled",
        "DEVMOD_AGGREGATION_ENABLED",
        true
    );

    /** Enable LVC (can be disabled for debugging) */
    public static final boolean LVC_ENABLED = getBoolean(
        "devmod.lvc.enabled",
        "DEVMOD_LVC_ENABLED",
        true
    );

    // ============================================
    // DEBUG
    // ============================================

    /** Log aggregation statistics periodically */
    public static final boolean LOG_AGGREGATION_STATS = getBoolean(
        "devmod.aggregation.log_stats",
        "DEVMOD_AGGREGATION_LOG_STATS",
        false
    );

    /** Log individual flush operations */
    public static final boolean LOG_FLUSH_OPERATIONS = getBoolean(
        "devmod.aggregation.log_flush",
        "DEVMOD_AGGREGATION_LOG_FLUSH",
        false
    );

    // ============================================
    // CONFIG RESOLUTION HELPERS
    // ============================================

    private static boolean getBoolean(String jvmProp, String envVar, boolean defaultValue) {
        String value = System.getProperty(jvmProp);
        if (value == null) {
            value = System.getenv(envVar);
        }
        if (value != null) {
            return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
        }
        return defaultValue;
    }

    private static int getInt(String jvmProp, String envVar, int defaultValue) {
        String value = System.getProperty(jvmProp);
        if (value == null) {
            value = System.getenv(envVar);
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private static long getLong(String jvmProp, String envVar, long defaultValue) {
        String value = System.getProperty(jvmProp);
        if (value == null) {
            value = System.getenv(envVar);
        }
        if (value != null) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
