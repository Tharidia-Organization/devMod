package com.devmod.telemetry.duckdb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuckDBConnectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DuckDBConnectionManager.class);

    private final Path dbPath;
    private final ReentrantLock connectionLock = new ReentrantLock();
    @Nullable
    private Connection connection;
    private volatile boolean shuttingDown = false;

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
     * by the caller - use it and let the manager handle lifecycle.
     *
     * @return Active database connection
     * @throws SQLException if connection cannot be established
     */
    public Connection getConnection() throws SQLException {
        if (shuttingDown) {
            throw new SQLException("DuckDB is shutting down, cannot provide connection");
        }

        connectionLock.lock();
        try {
            if (connection == null || connection.isClosed()) {
                connection = createConnection();
            }
            return connection;
        } finally {
            connectionLock.unlock();
        }
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

            // Load DuckDB driver explicitly
            Class.forName("org.duckdb.DuckDBDriver");

            // Create connection
            String jdbcUrl = "jdbc:duckdb:" + dbPath.toAbsolutePath();
            Connection conn = DriverManager.getConnection(jdbcUrl);

            // Configure connection for optimal performance
            configureConnection(conn);

            LOGGER.info("[DuckDB] Connection established to: {}", dbPath);
            return conn;

        } catch (ClassNotFoundException e) {
            throw new SQLException("DuckDB JDBC driver not found", e);
        } catch (Exception e) {
            throw new SQLException("Failed to create DuckDB connection: " + e.getMessage(), e);
        }
    }

    /**
     * Configure connection settings for optimal telemetry performance.
     * Note: Using DuckDB defaults - custom PRAGMA/SET commands may have compatibility issues across versions.
     */
    private void configureConnection(Connection conn) throws SQLException {
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
            if (connection != null && !connection.isClosed()) {
                try (var stmt = connection.createStatement()) {
                    stmt.execute("CHECKPOINT");
                    LOGGER.debug("[DuckDB] Checkpoint completed");
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("[DuckDB] Checkpoint failed: {}", e.getMessage());
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
            if (connection != null) {
                try {
                    if (!connection.isClosed()) {
                        // Final checkpoint before close
                        try (var stmt = connection.createStatement()) {
                            stmt.execute("CHECKPOINT");
                        }
                        LOGGER.debug("[DuckDB] Final checkpoint completed");

                        connection.close();
                        LOGGER.info("[DuckDB] Connection closed successfully");
                    }
                } catch (SQLException e) {
                    LOGGER.error("[DuckDB] Error during shutdown: {}", e.getMessage());
                }
            }
            connection = null;
        } finally {
            connectionLock.unlock();
        }
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
            if (connection == null || connection.isClosed()) {
                return false;
            }
            try (var stmt = connection.createStatement();
                 var rs = stmt.executeQuery("SELECT 1")) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.warn("[DuckDB] Connection test failed: {}", e.getMessage());
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
}
