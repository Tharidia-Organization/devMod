package com.devmod.telemetry.duckdb;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuckDBConnectionManager implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DuckDBConnectionManager.class);

    private final Path dbPath;
    private final ReentrantLock connectionLock = new ReentrantLock(true); // Fair lock
    @Nullable
    private Connection connection;
    private volatile boolean shuttingDown = false;

    // P0-006: Connection acquisition metrics
    private final AtomicLong totalAcquisitions = new AtomicLong(0);
    private final AtomicLong acquisitionTimeouts = new AtomicLong(0);
    private final AtomicLong totalWaitTimeNanos = new AtomicLong(0);
    private final AtomicLong maxWaitTimeNanos = new AtomicLong(0);
    private final AtomicLong contentionEvents = new AtomicLong(0);

    /**
     * Create a connection manager for the specified database path.
     *
     * @param dbPath Path to the DuckDB database file (.duckdb)
     */
    public DuckDBConnectionManager(Path dbPath) {
        this.dbPath = dbPath;
        LOGGER.info("[DuckDB] Connection manager initialized for: {}", dbPath);
    }

    /**
     * Get the database connection, creating it if necessary.
     *
     * This method is thread-safe. The returned connection should NOT be closed
     * by the caller - it is a shared connection managed by this class.
     *
     * The connection is shared, and DuckDB cannot run concurrent statements on
     * one connection: hold a {@link #lockStatements()} lease around every
     * statement executed on it.
     *
     * IMPORTANT: Do NOT use this in try-with-resources that auto-closes Connection!
     * Correct usage:
     *   Connection conn = connectionManager.getConnection();
     *   try (PreparedStatement stmt = conn.prepareStatement(sql)) { ... }
     *
     * Incorrect (will break concurrent operations):
     *   try (Connection conn = connectionManager.getConnection(); ...) { ... }
     *
     * @return Active database connection (DO NOT CLOSE)
     * @throws SQLException if connection cannot be established
     */
    public Connection getConnection() throws SQLException {
        if (shuttingDown) {
            throw new SQLException("DuckDB is shutting down, cannot provide connection");
        }

        // P0-006: Track acquisition metrics
        long startNanos = System.nanoTime();
        boolean wasContended = connectionLock.hasQueuedThreads();
        if (wasContended) {
            contentionEvents.incrementAndGet();
        }

        boolean acquired;
        try {
            // P0-006: Use tryLock with timeout instead of blocking lock()
            acquired = connectionLock.tryLock(
                DuckDBConfig.CONNECTION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Connection acquisition interrupted", e);
        }

        if (!acquired) {
            acquisitionTimeouts.incrementAndGet();
            LOGGER.error("[DuckDB] Connection acquisition timeout after {}s (contention: {}, queue: {})",
                DuckDBConfig.CONNECTION_TIMEOUT_SECONDS, contentionEvents.get(),
                connectionLock.getQueueLength());
            throw new SQLException("Connection acquisition timeout - database may be overloaded");
        }

        try {
            // P0-006: Record wait time metrics
            long waitNanos = System.nanoTime() - startNanos;
            totalWaitTimeNanos.addAndGet(waitNanos);
            totalAcquisitions.incrementAndGet();

            // Update max wait time atomically
            long currentMax;
            do {
                currentMax = maxWaitTimeNanos.get();
                if (waitNanos <= currentMax) break;
            } while (!maxWaitTimeNanos.compareAndSet(currentMax, waitNanos));

            // Warn if wait time is excessive (> 1 second)
            if (waitNanos > 1_000_000_000L) {
                LOGGER.warn("[DuckDB] Slow connection acquisition: {}ms", waitNanos / 1_000_000);
            }

            Connection conn = connection;
            if (conn == null || conn.isClosed()) {
                conn = createConnectionWithRetry();
                connection = conn;
            }
            return conn;
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Reserve the shared connection for the duration of a statement.
     *
     * A DuckDB JDBC Connection does not support concurrent statements: two
     * threads running statements on the same Connection corrupt each other's
     * ResultSets. Every caller that executes SQL on the connection returned by
     * {@link #getConnection()} must hold a lease for as long as it uses the
     * statement AND its ResultSet:
     *
     *   try (var lease = manager.lockStatements();
     *        var stmt = conn.createStatement();
     *        var rs = stmt.executeQuery(sql)) { ... }
     *
     * The lease is reentrant, so {@link #getConnection()} may be called while
     * holding it. Internal helpers that execute SQL (checkpoint, testConnection,
     * integrity/permission checks, shutdown) take the same lock.
     *
     * @return lease to release in a finally/try-with-resources block
     * @throws SQLException if the lease cannot be acquired within
     *         {@link DuckDBConfig#CONNECTION_TIMEOUT_SECONDS}
     */
    public StatementLease lockStatements() throws SQLException {
        boolean acquired;
        try {
            acquired = connectionLock.tryLock(
                DuckDBConfig.CONNECTION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Statement lease acquisition interrupted", e);
        }

        if (!acquired) {
            acquisitionTimeouts.incrementAndGet();
            throw new SQLException("Statement lease timeout - database may be overloaded");
        }

        return connectionLock::unlock;
    }

    /**
     * Exclusive use of the shared connection, released on close.
     * Declared without a checked exception so it can be used as a
     * try-with-resources resource inside SQLException-only handlers.
     */
    public interface StatementLease extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Get the database connection without checked exception.
     *
     * Same as {@link #getConnection()} but wraps SQLException in RuntimeException.
     * Useful when calling from lambda expressions or contexts where checked
     * exceptions are inconvenient.
     *
     * @return Active database connection (DO NOT CLOSE)
     * @throws RuntimeException wrapping SQLException if connection fails
     */
    public Connection getConnectionUnchecked() {
        try {
            return getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get DuckDB connection", e);
        }
    }

    /**
     * Create connection with retry logic for transient errors (locks, timeouts).
     * Uses exponential backoff with max delay of 10 seconds.
     */
    private Connection createConnectionWithRetry() throws SQLException {
        SQLException lastException = null;
        long delay = DuckDBConfig.INITIAL_RETRY_DELAY_MS;
        int maxRetries = DuckDBConfig.MAX_CONNECTION_RETRIES;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return createConnection();
            } catch (SQLException e) {
                lastException = e;

                // Only retry on transient errors (locks, timeouts)
                if (!DuckDBErrorClassifier.isLockError(e) && !DuckDBErrorClassifier.isTimeoutError(e)) {
                    throw e; // Permanent error, don't retry
                }

                if (attempt < maxRetries) {
                    LOGGER.warn("[DuckDB] Connection attempt {}/{} failed ({}), retry in {}ms",
                        attempt, maxRetries, e.getMessage(), delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Connection interrupted", ie);
                    }
                    delay = Math.min(delay * 2, 10_000); // Max 10s delay
                }
            }
        }

        LOGGER.error("[DuckDB] All {} connection attempts failed", maxRetries);
        if (lastException == null) {
            throw new SQLException("Connection failed after " + maxRetries + " attempts");
        }
        throw lastException;
    }

    /**
     * Check if the connection is currently available.
     */
    public boolean isConnected() {
        connectionLock.lock();
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Ensure DuckDB native library path is set before driver initialization.
     * This fixes NPE in NeoForge environment where DuckDB can't determine temp directory.
     *
     * DuckDB's native loader can fail with NPE when:
     * - java.io.tmpdir is null or invalid
     * - The classloader environment doesn't have proper temp directory access
     * - NeoForge's FML classloader interferes with path resolution
     *
     * This method sets multiple fallback paths to maximize compatibility.
     */
    private void ensureNativeLibraryPath(@Nullable Path preferredDir) {
        try {
            // Ensure java.io.tmpdir is set (DuckDB uses this as fallback)
            String tmpDir = System.getProperty("java.io.tmpdir");
            if (tmpDir == null || tmpDir.isEmpty()) {
                // Set a sensible default if missing
                tmpDir = System.getProperty("user.home", ".") + "/.devmod/tmp";
                System.setProperty("java.io.tmpdir", tmpDir);
                LOGGER.warn("[DuckDB] java.io.tmpdir was null/empty, set to: {}", tmpDir);
            }

            // Only set DuckDB library path if not already configured
            if (System.getProperty("org.duckdb.library_path") != null) {
                LOGGER.debug("[DuckDB] Native library path already set: {}",
                    System.getProperty("org.duckdb.library_path"));
                return;
            }

            Path libPath = null;

            // Priority 1: Use the database parent directory (most reliable)
            if (preferredDir != null) {
                try {
                    if (Files.exists(preferredDir) || Files.createDirectories(preferredDir) != null) {
                        libPath = preferredDir.resolve("native");
                    }
                } catch (IOException ignored) {
                    // Fall through to next option
                }
            }

            // Priority 2: Use user home directory (always available)
            if (libPath == null) {
                String userHome = System.getProperty("user.home");
                if (userHome != null && !userHome.isEmpty()) {
                    libPath = Path.of(userHome, ".devmod", "duckdb-native");
                }
            }

            // Priority 3: Use java.io.tmpdir
            if (libPath == null) {
                libPath = Path.of(tmpDir, "duckdb-native");
            }

            // Ensure the directory exists
            if (!Files.exists(libPath)) {
                Files.createDirectories(libPath);
            }

            // Set the system property for DuckDB native library extraction
            String libPathStr = libPath.toAbsolutePath().toString();
            System.setProperty("org.duckdb.library_path", libPathStr);
            LOGGER.info("[DuckDB] Native library path set to: {}", libPathStr);

        } catch (IOException | SecurityException e) {
            LOGGER.warn("[DuckDB] Could not set native library path, DuckDB may fail to initialize", e);
        }
    }

    /**
     * Create a new database connection.
     */
    private Connection createConnection() throws SQLException {
        try {
            // Ensure parent directory exists
            Path parentDir = dbPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                LOGGER.info("[DuckDB] Created directory: {}", parentDir);
            }

            // P0: Set DuckDB native library extraction path BEFORE loading driver
            // This prevents NPE when DuckDB can't determine temp directory in NeoForge environment
            ensureNativeLibraryPath(parentDir);

            // The driver is provisioned by DuckDBBootstrap into config/devmod/libs/ and loaded
            // through its own URLClassLoader. It must therefore be used DIRECTLY:
            // DriverManager only offers drivers visible to the calling class's classloader, so
            // it cannot see this one and would fail with "No suitable driver".
            Driver driver = DuckDBBootstrap.getDriver();
            if (driver == null) {
                throw new SQLException("DuckDB JDBC driver not found");
            }

            String jdbcUrl = "jdbc:duckdb:" + dbPath.toAbsolutePath();

            // The native loader can still blow up here on its first use: DuckDB extracts and
            // links its native library lazily, and in a modded environment it has been seen to
            // fail on a null temp directory. These are Errors, not Exceptions, so the generic
            // handler below would not translate them into something a caller can act on.
            Connection conn;
            try {
                conn = driver.connect(jdbcUrl, new Properties());
            } catch (ExceptionInInitializerError e) {
                LOGGER.error("[DuckDB] Native library initialization failed. This is often caused by "
                    + "DuckDB being unable to determine the temp directory path in modded environments.", e);
                throw new SQLException("DuckDB native library initialization failed: " + e.getMessage(), e);
            } catch (NoClassDefFoundError e) {
                LOGGER.error("[DuckDB] DuckDB native class not found/initialized. "
                    + "The native library may have failed to load.", e);
                throw new SQLException("DuckDB native class initialization failed: " + e.getMessage(), e);
            } catch (UnsatisfiedLinkError e) {
                LOGGER.error("[DuckDB] Failed to load DuckDB native library. "
                    + "Ensure DuckDB JDBC is included with native binaries.", e);
                throw new SQLException("DuckDB native library load failed: " + e.getMessage(), e);
            }

            // Driver.connect returns null when the URL is not one it handles. That would mean the
            // jdbc:duckdb: prefix stopped matching, not a connection failure, so say so plainly.
            if (conn == null) {
                throw new SQLException("DuckDB driver did not accept the URL: " + jdbcUrl);
            }

            // Configure connection for optimal performance
            configureConnection();

            LOGGER.info("[DuckDB] Connection established to: {}", dbPath);
            return conn;

        } catch (SQLException e) {
            // Re-throw SQLException (already wrapped native errors)
            throw e;
        } catch (Exception e) {
            throw new SQLException("Failed to create DuckDB connection: " + e.getMessage(), e);
        }
    }

    /**
     * Configure connection settings for optimal telemetry performance.
     * Note: Using DuckDB defaults - custom PRAGMA/SET commands may have compatibility issues across versions.
     */
    private void configureConnection() throws SQLException {
        // DuckDB defaults are already optimized for most use cases
        LOGGER.debug("[DuckDB] Connection configured with default settings");
    }

    /**
     * Execute a checkpoint to persist WAL changes to main database file.
     * Call this periodically or before shutdown.
     */
    public void checkpoint() {
        if (shuttingDown) return;

        connectionLock.lock();
        try {
            Connection conn = connection;
            if (conn != null && !conn.isClosed()) {
                try (var stmt = conn.createStatement()) {
                    stmt.execute("CHECKPOINT");
                    LOGGER.debug("[DuckDB] Checkpoint completed");
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("[DuckDB] Checkpoint failed", e);
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Get the database file path.
     */
    public Path getDbPath() {
        return dbPath;
    }

    /**
     * Check if the database file exists.
     */
    public boolean databaseExists() {
        return Files.exists(dbPath);
    }

    /**
     * Gracefully shutdown the connection.
     * Performs checkpoint and closes connection.
     */
    public void shutdown() {
        LOGGER.info("[DuckDB] Shutting down connection manager...");
        shuttingDown = true;

        connectionLock.lock();
        try {
            Connection conn = connection;
            if (conn != null) {
                try {
                    if (!conn.isClosed()) {
                        // Final checkpoint before close
                        try (var stmt = conn.createStatement()) {
                            stmt.execute("CHECKPOINT");
                        }
                        LOGGER.debug("[DuckDB] Final checkpoint completed");

                        conn.close();
                        LOGGER.info("[DuckDB] Connection closed successfully");
                    }
                } catch (SQLException e) {
                    LOGGER.error("[DuckDB] Error during shutdown", e);
                }
            }
            connection = null;
        } finally {
            connectionLock.unlock();
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    /**
     * Force reconnection (useful after errors).
     */
    public void reconnect() throws SQLException {
        connectionLock.lock();
        try {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // Ignore close errors during reconnect
                }
            }
            connection = createConnection();
            LOGGER.info("[DuckDB] Reconnection successful");
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Execute a simple query to test connection health.
     *
     * @return true if connection is healthy
     */
    public boolean testConnection() {
        connectionLock.lock();
        try {
            Connection conn = connection;
            if (conn == null || conn.isClosed()) {
                return false;
            }
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 1")) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.warn("[DuckDB] Connection test failed", e);
            return false;
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Check if the manager is in shutdown state.
     */
    public boolean isShuttingDown() {
        return shuttingDown;
    }

    // ============================================
    // STARTUP SAFEGUARDS
    // ============================================

    // ============================================
    // WAL MONITORING
    // ============================================

    /**
     * Get the current WAL (Write-Ahead Log) file size in bytes.
     *
     * @return WAL size in bytes, or 0 if WAL doesn't exist or can't be read
     */
    public long getWalSize() {
        try {
            Path walPath = dbPath.resolveSibling(dbPath.getFileName() + ".wal");
            if (Files.exists(walPath)) {
                return Files.size(walPath);
            }
        } catch (IOException e) {
            LOGGER.debug("[DuckDB] Could not check WAL size", e);
        }
        return 0;
    }

    /**
     * Check if WAL has grown beyond the configured maximum size.
     *
     * @return true if WAL is oversized and needs checkpoint
     */
    public boolean isWalOversized() {
        long walSize = getWalSize();
        return walSize > DuckDBConfig.MAX_WAL_SIZE_BYTES;
    }

    /**
     * Check WAL size and force checkpoint if oversized.
     *
     * @return true if checkpoint was performed
     */
    public boolean checkpointIfWalOversized() {
        if (isWalOversized()) {
            long walSizeMB = getWalSize() / (1024 * 1024);
            LOGGER.info("[DuckDB] WAL oversized ({}MB > {}MB), forcing checkpoint",
                walSizeMB, DuckDBConfig.MAX_WAL_SIZE_BYTES / (1024 * 1024));
            checkpoint();
            return true;
        }
        return false;
    }

    /**
     * Check if there is sufficient disk space for database operations.
     *
     * @return true if disk space is sufficient, false otherwise
     */
    public boolean checkDiskSpace() {
        try {
            Path parentDir = dbPath.getParent();
            if (parentDir == null) {
                parentDir = dbPath.toAbsolutePath().getParent();
            }
            if (parentDir == null || !Files.exists(parentDir)) {
                LOGGER.warn("[DuckDB] Cannot check disk space - directory does not exist: {}", parentDir);
                return true; // Assume OK, will fail on write if not
            }

            FileStore store = Files.getFileStore(parentDir);
            long usableSpace = store.getUsableSpace();

            if (usableSpace < DuckDBConfig.MIN_DISK_SPACE_BYTES) {
                LOGGER.error("[DuckDB] Insufficient disk space: {} MB available, {} MB required",
                    usableSpace / (1024 * 1024), DuckDBConfig.MIN_DISK_SPACE_BYTES / (1024 * 1024));
                return false;
            }

            LOGGER.debug("[DuckDB] Disk space OK: {} MB available", usableSpace / (1024 * 1024));
            return true;

        } catch (IOException e) {
            LOGGER.warn("[DuckDB] Could not check disk space", e);
            return true; // Assume OK on error, will fail on actual write
        }
    }

    /**
     * Get available disk space in bytes.
     *
     * @return available bytes, or -1 if unknown
     */
    public long getAvailableDiskSpace() {
        try {
            Path parentDir = dbPath.getParent();
            if (parentDir == null) {
                parentDir = dbPath.toAbsolutePath().getParent();
            }
            if (parentDir == null || !Files.exists(parentDir)) {
                return -1;
            }
            return Files.getFileStore(parentDir).getUsableSpace();
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * Run database integrity check.
     * Should be called after connection is established.
     *
     * @return true if database is healthy, false if corruption detected
     */
    public boolean runIntegrityCheck() {
        connectionLock.lock();
        try {
            Connection conn = connection;
            if (conn == null || conn.isClosed()) {
                LOGGER.warn("[DuckDB] Cannot run integrity check - no connection");
                return false;
            }

            // DuckDB doesn't support PRAGMA integrity_check like SQLite.
            // Instead, we verify the connection works with a simple query.
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 1")) {
                if (rs.next() && rs.getInt(1) == 1) {
                    LOGGER.debug("[DuckDB] Integrity check passed");
                    return true;
                }
                LOGGER.error("[DuckDB] Integrity check FAILED: unexpected result");
                return false;
            }
        } catch (SQLException e) {
            LOGGER.error("[DuckDB] Integrity check error", e);
            return false;
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Validate write permissions by attempting a test write.
     *
     * @return true if write permissions are valid
     */
    public boolean validateWritePermissions() {
        connectionLock.lock();
        try {
            Connection conn = connection;
            if (conn == null || conn.isClosed()) {
                LOGGER.warn("[DuckDB] Cannot validate permissions - no connection");
                return false;
            }

            // Create and drop a test table to verify write access
            try (var stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS _devmod_write_test (id INTEGER)");
                stmt.execute("DROP TABLE IF EXISTS _devmod_write_test");
                LOGGER.debug("[DuckDB] Write permissions validated");
                return true;
            }
        } catch (SQLException e) {
            LOGGER.error("[DuckDB] Write permission validation failed", e);
            return false;
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Run all startup validation checks.
     *
     * @return true if all checks pass
     */
    public boolean runStartupValidation() {
        LOGGER.info("[DuckDB] Running startup validation checks...");

        // Check disk space
        if (!checkDiskSpace()) {
            LOGGER.error("[DuckDB] Startup validation FAILED: insufficient disk space");
            return false;
        }

        // Ensure connection exists
        try {
            getConnection();
        } catch (SQLException e) {
            LOGGER.error("[DuckDB] Startup validation FAILED: cannot connect", e);
            return false;
        }

        // Check database integrity
        if (!runIntegrityCheck()) {
            LOGGER.error("[DuckDB] Startup validation FAILED: integrity check failed");
            return false;
        }

        // Validate write permissions
        if (!validateWritePermissions()) {
            LOGGER.error("[DuckDB] Startup validation FAILED: no write permissions");
            return false;
        }

        LOGGER.info("[DuckDB] Startup validation PASSED");
        return true;
    }

    // ============================================
    // P0-006: CONNECTION METRICS
    // ============================================

    /**
     * Get connection acquisition statistics for monitoring.
     *
     * @return ConnectionMetrics snapshot
     */
    public ConnectionMetrics getConnectionMetrics() {
        return new ConnectionMetrics(
            totalAcquisitions.get(),
            acquisitionTimeouts.get(),
            contentionEvents.get(),
            totalWaitTimeNanos.get(),
            maxWaitTimeNanos.get(),
            connectionLock.getQueueLength(),
            connectionLock.isLocked()
        );
    }

    /**
     * Reset connection metrics (useful for periodic reporting).
     */
    public void resetConnectionMetrics() {
        totalAcquisitions.set(0);
        acquisitionTimeouts.set(0);
        contentionEvents.set(0);
        totalWaitTimeNanos.set(0);
        maxWaitTimeNanos.set(0);
        LOGGER.debug("[DuckDB] Connection metrics reset");
    }

    /**
     * Log current connection metrics at INFO level.
     */
    public void logConnectionMetrics() {
        ConnectionMetrics m = getConnectionMetrics();
        if (m.totalAcquisitions() == 0) {
            LOGGER.info("[DuckDB] Connection metrics: no acquisitions yet");
            return;
        }

        double avgWaitMs = m.totalAcquisitions() > 0
            ? (double) m.totalWaitTimeNanos() / m.totalAcquisitions() / 1_000_000.0
            : 0;
        double maxWaitMs = m.maxWaitTimeNanos() / 1_000_000.0;
        double timeoutRate = m.totalAcquisitions() > 0
            ? (double) m.acquisitionTimeouts() / m.totalAcquisitions() * 100
            : 0;

        LOGGER.info("[DuckDB] Connection metrics: acquisitions={}, timeouts={} ({}%), " +
            "contention={}, avgWait={}ms, maxWait={}ms, queueLen={}",
            m.totalAcquisitions(), m.acquisitionTimeouts(),
            String.format("%.2f", timeoutRate),
            m.contentionEvents(),
            String.format("%.2f", avgWaitMs),
            String.format("%.2f", maxWaitMs),
            m.currentQueueLength());
    }

    /**
     * Immutable snapshot of connection metrics.
     */
    public record ConnectionMetrics(
        long totalAcquisitions,
        long acquisitionTimeouts,
        long contentionEvents,
        long totalWaitTimeNanos,
        long maxWaitTimeNanos,
        int currentQueueLength,
        boolean isLocked
    ) {
        /**
         * Calculate average wait time in milliseconds.
         */
        public double avgWaitTimeMs() {
            return totalAcquisitions > 0
                ? (double) totalWaitTimeNanos / totalAcquisitions / 1_000_000.0
                : 0;
        }

        /**
         * Calculate timeout rate as percentage.
         */
        public double timeoutRatePercent() {
            return totalAcquisitions > 0
                ? (double) acquisitionTimeouts / totalAcquisitions * 100
                : 0;
        }

        /**
         * Check if contention is high (> 10% of acquisitions).
         */
        public boolean isHighContention() {
            return totalAcquisitions > 0 &&
                (double) contentionEvents / totalAcquisitions > 0.1;
        }
    }
}
