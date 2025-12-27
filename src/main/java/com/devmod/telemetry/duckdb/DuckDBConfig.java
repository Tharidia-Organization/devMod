package com.devmod.telemetry.duckdb;

/**
 * Configuration for DuckDB telemetry storage.
 *
 * DuckDB is the PRIMARY storage system for telemetry data.
 * NDJSON is an OPTIONAL FALLBACK for:
 * - External tool compatibility
 * - Debugging/manual inspection
 * - Backup during DuckDB failures
 *
 * Architecture:
 * - DuckDB: Primary, always used when available
 * - NDJSON: Fallback, controlled by NDJSON_FALLBACK flag
 *
 * Configuration Override (JVM args or environment variables):
 * - -Ddevmod.duckdb.enabled=false
 * - -Ddevmod.duckdb.path=/custom/path.duckdb
 * - -Ddevmod.duckdb.ndjson_fallback=true
 * - -Ddevmod.duckdb.fallback_on_error=false
 * - DEVMOD_DUCKDB_ENABLED=false (env var)
 * - DEVMOD_DUCKDB_PATH=/custom/path.duckdb (env var)
 */
public final class DuckDBConfig {

    private DuckDBConfig() {} // Utility class

    // ============================================
    // MAIN TOGGLES
    // ============================================

    /**
     * Enable DuckDB storage (PRIMARY).
     * If false, falls back to NDJSON only (legacy mode).
     * Override: -Ddevmod.duckdb.enabled=false or DEVMOD_DUCKDB_ENABLED=false
     */
    public static boolean ENABLED = getBoolean("devmod.duckdb.enabled", "DEVMOD_DUCKDB_ENABLED", true);

    /**
     * Enable NDJSON fallback/backup alongside DuckDB.
     * When true: writes to both DuckDB (primary) and NDJSON (backup)
     * When false: writes only to DuckDB (recommended for production)
     * Override: -Ddevmod.duckdb.ndjson_fallback=true or DEVMOD_DUCKDB_NDJSON_FALLBACK=true
     *
     * Use cases for enabling:
     * - Migration period (verify DuckDB data integrity)
     * - External tools that read NDJSON
     * - Debugging (human-readable format)
     */
    public static boolean NDJSON_FALLBACK = getBoolean("devmod.duckdb.ndjson_fallback", "DEVMOD_DUCKDB_NDJSON_FALLBACK", false);

    /**
     * Enable automatic NDJSON fallback when DuckDB circuit breaker triggers.
     * Override: -Ddevmod.duckdb.fallback_on_error=false or DEVMOD_DUCKDB_FALLBACK_ON_ERROR=false
     *
     * Policy options:
     * - NDJSON_FALLBACK=true: Always dual-write (dev/debug)
     * - NDJSON_FALLBACK=false + FALLBACK_ON_ERROR=true: Production safe - fallback on errors
     * - NDJSON_FALLBACK=false + FALLBACK_ON_ERROR=false: Production strict - telemetry OFF on errors
     *
     * When false and circuit breaker triggers, telemetry is disabled entirely
     * rather than silently enabling NDJSON (which would violate the "DuckDB only" config).
     */
    public static boolean FALLBACK_ON_ERROR = getBoolean("devmod.duckdb.fallback_on_error", "DEVMOD_DUCKDB_FALLBACK_ON_ERROR", true);

    // ============================================
    // PERFORMANCE TUNING
    // ============================================

    /** Number of events to batch before flushing to database */
    public static int BATCH_SIZE = getInt("devmod.duckdb.batch_size", "DEVMOD_DUCKDB_BATCH_SIZE", 100);

    /** Maximum time between flushes in milliseconds */
    public static long FLUSH_INTERVAL_MS = getLong("devmod.duckdb.flush_interval_ms", "DEVMOD_DUCKDB_FLUSH_INTERVAL_MS", 5000);

    /** Maximum queue capacity per table (prevents memory overflow) */
    public static int QUEUE_CAPACITY = getInt("devmod.duckdb.queue_capacity", "DEVMOD_DUCKDB_QUEUE_CAPACITY", 10000);

    /** Thread pool size for batch writer (1 is optimal for DuckDB single-writer) */
    public static int WRITER_THREADS = 1;

    // ============================================
    // TIMEOUTS & RESILIENCE
    // ============================================

    /** Connection timeout in seconds (prevents infinite hang on lock) */
    public static int CONNECTION_TIMEOUT_SECONDS = getInt("devmod.duckdb.connection_timeout", "DEVMOD_DUCKDB_CONNECTION_TIMEOUT", 30);

    /** Query timeout for write operations in seconds */
    public static int QUERY_TIMEOUT_SECONDS = getInt("devmod.duckdb.query_timeout", "DEVMOD_DUCKDB_QUERY_TIMEOUT", 10);

    /** Query timeout for analytics/read operations in seconds (longer for complex queries) */
    public static int ANALYTICS_QUERY_TIMEOUT_SECONDS = getInt("devmod.duckdb.analytics_timeout", "DEVMOD_DUCKDB_ANALYTICS_TIMEOUT", 60);

    /** Maximum connection retry attempts on lock errors */
    public static int MAX_CONNECTION_RETRIES = getInt("devmod.duckdb.max_retries", "DEVMOD_DUCKDB_MAX_RETRIES", 5);

    /** Initial retry delay in milliseconds (doubles each retry, max 10s) */
    public static long INITIAL_RETRY_DELAY_MS = getLong("devmod.duckdb.retry_delay_ms", "DEVMOD_DUCKDB_RETRY_DELAY_MS", 1000);

    /** Checkpoint interval in milliseconds (15 minutes default) */
    public static long CHECKPOINT_INTERVAL_MS = getLong("devmod.duckdb.checkpoint_interval_ms", "DEVMOD_DUCKDB_CHECKPOINT_INTERVAL_MS", 15 * 60 * 1000);

    /** Disk space check interval in milliseconds during runtime */
    public static long DISK_CHECK_INTERVAL_MS = getLong("devmod.duckdb.disk_check_interval_ms", "DEVMOD_DUCKDB_DISK_CHECK_INTERVAL_MS", 60_000);

    /** Maximum WAL size in bytes before forcing checkpoint (300MB) */
    public static long MAX_WAL_SIZE_BYTES = getLong("devmod.duckdb.max_wal_size", "DEVMOD_DUCKDB_MAX_WAL_SIZE", 300 * 1024 * 1024);

    /** Minimum free disk space in bytes (100MB) */
    public static long MIN_DISK_SPACE_BYTES = getLong("devmod.duckdb.min_disk_space", "DEVMOD_DUCKDB_MIN_DISK_SPACE", 100 * 1024 * 1024);

    // ============================================
    // FILE PATHS
    // ============================================

    /**
     * Database filename (stored in server's telemetry directory).
     * Override: -Ddevmod.duckdb.path=/absolute/path.duckdb or DEVMOD_DUCKDB_PATH=/absolute/path.duckdb
     *
     * If the path is absolute, it's used directly.
     * If relative, it's resolved from TELEMETRY_DIR.
     */
    public static String DB_FILENAME = getString("devmod.duckdb.path", "DEVMOD_DUCKDB_PATH", "devmod_telemetry.duckdb");

    /** Subdirectory within server folder for telemetry data */
    public static String TELEMETRY_DIR = getString("devmod.duckdb.dir", "DEVMOD_DUCKDB_DIR", "telemetry");

    // ============================================
    // NETWORK (MULTIPLAYER)
    // ============================================

    /** Maximum events per batch packet from client to server */
    public static int MAX_EVENTS_PER_PACKET = 50;

    /** Client sync interval in ticks (200 = 10 seconds) */
    public static int CLIENT_SYNC_INTERVAL_TICKS = 200;

    /** Maximum events per second allowed from a single client (rate limiting) */
    public static int MAX_EVENTS_PER_SECOND_PER_CLIENT = 100;

    /** Client-side buffer capacity before oldest events are dropped */
    public static int CLIENT_BUFFER_CAPACITY = 500;

    // ============================================
    // SCHEMA VERSION
    // ============================================

    /** Current schema version for migration tracking */
    public static int SCHEMA_VERSION = 10;  // Bumped for aggregation tables (LVC + per-player)

    // ============================================
    // DEBUG
    // ============================================

    /** Log batch insert timings for performance analysis */
    public static boolean LOG_BATCH_TIMING = getBoolean("devmod.duckdb.log_batch_timing", "DEVMOD_DUCKDB_LOG_BATCH_TIMING", false);

    /** Log individual insert operations (very verbose) */
    public static boolean LOG_INSERTS = getBoolean("devmod.duckdb.log_inserts", "DEVMOD_DUCKDB_LOG_INSERTS", false);

    /** Rate limit interval for warning logs in milliseconds (default 60s) */
    public static long LOG_RATE_LIMIT_MS = getLong("devmod.duckdb.log_rate_limit_ms", "DEVMOD_DUCKDB_LOG_RATE_LIMIT_MS", 60_000);

    /** Log every N backpressure drops (sampled logging, default 100) */
    public static int LOG_BACKPRESSURE_SAMPLE_RATE = getInt("devmod.duckdb.log_backpressure_sample", "DEVMOD_DUCKDB_LOG_BACKPRESSURE_SAMPLE", 100);

    // ============================================
    // CONFIG RESOLUTION HELPERS
    // ============================================

    /**
     * Get boolean value from JVM property or environment variable.
     * Priority: JVM property > env var > defaultValue
     */
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

    /**
     * Get string value from JVM property or environment variable.
     * Priority: JVM property > env var > defaultValue
     */
    private static String getString(String jvmProp, String envVar, String defaultValue) {
        String value = System.getProperty(jvmProp);
        if (value == null || value.isBlank()) {
            value = System.getenv(envVar);
        }
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    /**
     * Get int value from JVM property or environment variable.
     * Priority: JVM property > env var > defaultValue
     */
    private static int getInt(String jvmProp, String envVar, int defaultValue) {
        String value = System.getProperty(jvmProp);
        if (value == null) {
            value = System.getenv(envVar);
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Get long value from JVM property or environment variable.
     * Priority: JVM property > env var > defaultValue
     */
    private static long getLong(String jvmProp, String envVar, long defaultValue) {
        String value = System.getProperty(jvmProp);
        if (value == null) {
            value = System.getenv(envVar);
        }
        if (value != null) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
