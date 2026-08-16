package com.devmod.notification.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationParamsCodec;
import com.devmod.notification.NotificationPriority;
import com.devmod.telemetry.duckdb.DuckDBConnectionManager;
/**
 * DuckDB-based persistence for notification history.
 *
 * <p>Stores notifications for:
 * <ul>
 *   <li>Historical viewing in NotificationHistoryScreen</li>
 *   <li>Analytics and reporting</li>
 *   <li>Reconnect sync for offline players</li>
 * </ul>
 */
public class NotificationHistoryRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationHistoryRepository.class);
    /** Schema version for future migrations - reserved for use. */
    public static final NotificationHistoryRepository INSTANCE = new NotificationHistoryRepository();

    @Nullable
    private DuckDBConnectionManager connectionManager;
    @Nullable
    private ExecutorService executor;
    private volatile boolean initialized = false;

    private NotificationHistoryRepository() {}

    // ============================================================================
    // INITIALIZATION
    // ============================================================================

    /**
     * Initialize the repository with the given database path.
     */
    public CompletableFuture<Void> initialize(Path dbPath) {
        if (initialized) {
            return CompletableFuture.completedFuture(null);
        }

        this.connectionManager = DuckDBConnectionManager.forPath(dbPath);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "NotificationDB-Worker");
            t.setDaemon(true);
            return t;
        });

        return CompletableFuture.runAsync(() -> {
            try {
                createSchema();
                initialized = true;
                LOGGER.info("[NotificationHistory] Repository initialized successfully");
            } catch (Exception e) {
                LOGGER.error("[NotificationHistory] Failed to initialize repository", e);
                throw new RuntimeException("Failed to initialize notification history", e);
            }
        }, executor);
    }

    /**
     * Shutdown the repository.
     */
    public void shutdown() {
        if (!initialized) return;

        initialized = false;
        if (executor != null) {
            executor.shutdown();
        }
        if (connectionManager != null) {
            connectionManager.shutdown();
        }
        LOGGER.info("[NotificationHistory] Repository shutdown complete");
    }

    private void createSchema() throws SQLException {
        DuckDBConnectionManager manager = connectionManager;
        if (manager == null) return;

        String createTableSql = """
            CREATE TABLE IF NOT EXISTS notification_history (
                id VARCHAR PRIMARY KEY,
                player_uuid VARCHAR NOT NULL,
                category VARCHAR NOT NULL,
                priority INTEGER NOT NULL,
                title_key VARCHAR NOT NULL,
                message_key VARCHAR,
                params_json VARCHAR,
                icon_id VARCHAR,
                sound_id VARCHAR,
                action_id VARCHAR,
                action_data_json VARCHAR,
                created_at TIMESTAMP NOT NULL,
                read_at TIMESTAMP,
                related_entity_id VARCHAR,
                display_duration_ms INTEGER DEFAULT 2000
            )
            """;

        String createIndexSql = """
            CREATE INDEX IF NOT EXISTS idx_notification_player_created
            ON notification_history (player_uuid, created_at DESC)
            """;

        String createCategoryIndexSql = """
            CREATE INDEX IF NOT EXISTS idx_notification_category
            ON notification_history (player_uuid, category)
            """;

        Connection conn = manager.getConnectionUnchecked();
        try (PreparedStatement stmt = conn.prepareStatement(createTableSql)) {
            stmt.execute();
        }
        try (PreparedStatement stmt = conn.prepareStatement(createIndexSql)) {
            stmt.execute();
        }
        try (PreparedStatement stmt = conn.prepareStatement(createCategoryIndexSql)) {
            stmt.execute();
        }
        LOGGER.debug("[NotificationHistory] Schema created/verified");
    }

    // ============================================================================
    // SAVE OPERATIONS
    // ============================================================================

    /**
     * Save a notification to history.
     */
    public CompletableFuture<Void> save(UUID playerUuid, Notification notification) {
        ExecutorService exec = executor;
        DuckDBConnectionManager manager = connectionManager;
        if (!initialized || exec == null || manager == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO notification_history
                (id, player_uuid, category, priority, title_key, message_key, params_json,
                 icon_id, sound_id, action_id, action_data_json, created_at, related_entity_id,
                 display_duration_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            Connection conn = manager.getConnectionUnchecked();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, notification.id().toString());
                stmt.setString(2, playerUuid.toString());
                stmt.setString(3, notification.category().getId());
                stmt.setInt(4, notification.priority().getLevel());
                stmt.setString(5, notification.titleKey());
                setNullableString(stmt, 6, notification.messageKey());
                setNullableString(stmt, 7, NotificationParamsCodec.toJson(notification.params()));
                setNullableString(stmt, 8, notification.iconId());
                setNullableString(stmt, 9, notification.soundId());
                setNullableString(stmt, 10, notification.actionId());
                setNullableString(stmt, 11, notification.actionDataJson());
                stmt.setTimestamp(12, Timestamp.from(notification.createdAt()));
                setNullableUuid(stmt, 13, notification.relatedEntityId());
                stmt.setInt(14, notification.displayDurationMs());

                stmt.executeUpdate();
                LOGGER.debug("[NotificationHistory] Saved notification {} for player {}",
                        notification.id(), playerUuid);
            } catch (SQLException e) {
                LOGGER.warn("[NotificationHistory] Failed to save notification: {}", e.getMessage());
            }
        }, exec);
    }

    // ============================================================================
    // QUERY OPERATIONS
    // ============================================================================

    /**
     * Get notification history for a player.
     */
    public CompletableFuture<List<Notification>> getHistory(UUID playerUuid, int limit, int offset) {
        ExecutorService exec = executor;
        DuckDBConnectionManager manager = connectionManager;
        if (!initialized || exec == null || manager == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT * FROM notification_history
                WHERE player_uuid = ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;

            List<Notification> results = new ArrayList<>();
            Connection conn = manager.getConnectionUnchecked();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                stmt.setInt(2, limit);
                stmt.setInt(3, offset);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Notification notification = mapFromResultSet(rs);
                        if (notification != null) {
                            results.add(notification);
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.warn("[NotificationHistory] Failed to get history: {}", e.getMessage());
            }
            return results;
        }, exec);
    }

    /**
     * Get notification history filtered by category.
     */
    public CompletableFuture<List<Notification>> getHistoryByCategory(
            UUID playerUuid, NotificationCategory category, int limit) {
        ExecutorService exec = executor;
        DuckDBConnectionManager manager = connectionManager;
        if (!initialized || exec == null || manager == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT * FROM notification_history
                WHERE player_uuid = ? AND category = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;

            List<Notification> results = new ArrayList<>();
            Connection conn = manager.getConnectionUnchecked();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, category.getId());
                stmt.setInt(3, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Notification notification = mapFromResultSet(rs);
                        if (notification != null) {
                            results.add(notification);
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.warn("[NotificationHistory] Failed to get history by category: {}", e.getMessage());
            }
            return results;
        }, exec);
    }

    /**
     * Mark a notification as read.
     */
    public CompletableFuture<Void> markAsRead(UUID notificationId) {
        ExecutorService exec = executor;
        DuckDBConnectionManager manager = connectionManager;
        if (!initialized || exec == null || manager == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE notification_history SET read_at = ? WHERE id = ?";

            Connection conn = manager.getConnectionUnchecked();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setTimestamp(1, Timestamp.from(Instant.now()));
                stmt.setString(2, notificationId.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.warn("[NotificationHistory] Failed to mark as read: {}", e.getMessage());
            }
        }, exec);
    }

    /**
     * Get unread count for a player.
     */
    public CompletableFuture<Integer> getUnreadCount(UUID playerUuid) {
        ExecutorService exec = executor;
        DuckDBConnectionManager manager = connectionManager;
        if (!initialized || exec == null || manager == null) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT COUNT(*) FROM notification_history
                WHERE player_uuid = ? AND read_at IS NULL
                """;

            Connection conn = manager.getConnectionUnchecked();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                LOGGER.warn("[NotificationHistory] Failed to get unread count: {}", e.getMessage());
            }
            return 0;
        }, exec);
    }

    /**
     * Delete old notifications (retention policy).
     */
    public CompletableFuture<Integer> deleteOlderThan(Instant cutoff) {
        ExecutorService exec = executor;
        DuckDBConnectionManager manager = connectionManager;
        if (!initialized || exec == null || manager == null) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM notification_history WHERE created_at < ?";

            Connection conn = manager.getConnectionUnchecked();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setTimestamp(1, Timestamp.from(cutoff));
                int deleted = stmt.executeUpdate();
                if (deleted > 0) {
                    LOGGER.info("[NotificationHistory] Deleted {} old notifications", deleted);
                }
                return deleted;
            } catch (SQLException e) {
                LOGGER.warn("[NotificationHistory] Failed to delete old notifications: {}", e.getMessage());
                return 0;
            }
        }, exec);
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    @Nullable
    private Notification mapFromResultSet(ResultSet rs) throws SQLException {
        try {
            UUID id = UUID.fromString(rs.getString("id"));
            NotificationCategory category = NotificationCategory.fromId(rs.getString("category"));
            NotificationPriority priority = NotificationPriority.fromLevel(rs.getInt("priority"));

            String titleKey = rs.getString("title_key");
            String messageKey = rs.getString("message_key");
            String paramsJson = rs.getString("params_json");
            Map<String, String> params = NotificationParamsCodec.fromJson(paramsJson);

            String iconId = rs.getString("icon_id");
            String soundId = rs.getString("sound_id");
            String actionId = rs.getString("action_id");
            String actionDataJson = rs.getString("action_data_json");

            Timestamp createdTs = rs.getTimestamp("created_at");
            Instant createdAt = createdTs != null ? createdTs.toInstant() : Instant.now();

            String relatedEntityStr = rs.getString("related_entity_id");
            UUID relatedEntityId = relatedEntityStr != null ? UUID.fromString(relatedEntityStr) : null;

            int displayDurationMs = rs.getInt("display_duration_ms");

            return new Notification(
                    id, category, priority, titleKey, messageKey, params,
                    iconId, soundId, actionId, actionDataJson, createdAt,
                    relatedEntityId, false, true, displayDurationMs
            );
        } catch (Exception e) {
            LOGGER.warn("[NotificationHistory] Failed to map notification from result set: {}", e.getMessage());
            return null;
        }
    }

    private void setNullableString(PreparedStatement stmt, int index, @Nullable String value)
            throws SQLException {
        if (value != null) {
            stmt.setString(index, value);
        } else {
            stmt.setNull(index, Types.VARCHAR);
        }
    }

    private void setNullableUuid(PreparedStatement stmt, int index, @Nullable UUID value)
            throws SQLException {
        if (value != null) {
            stmt.setString(index, value.toString());
        } else {
            stmt.setNull(index, Types.VARCHAR);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }
}
