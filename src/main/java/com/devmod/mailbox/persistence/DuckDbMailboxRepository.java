package com.devmod.mailbox.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.MailboxMessage;
import com.devmod.mailbox.MessageType;
import com.devmod.mailbox.moderation.AdminAuditLog;
import com.devmod.mailbox.news.NewsArticle;
import com.devmod.mailbox.news.NewsCategory;
import com.devmod.mailbox.task.TaskAuditEntry;
import com.devmod.mailbox.task.TestTask;
import com.devmod.telemetry.duckdb.DuckDBConnectionManager;

/**
 * DuckDB implementation of MailboxRepository.
 *
 * Uses the existing DuckDB infrastructure for persistence.
 */
public class DuckDbMailboxRepository implements MailboxRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DuckDbMailboxRepository.class);
    private static final int CURRENT_SCHEMA_VERSION = 6;

    private final DuckDBConnectionManager connectionManager;
    private final ExecutorService executor;
    private final Path dbPath;

    public DuckDbMailboxRepository(Path dbPath) {
        this.dbPath = dbPath;
        this.connectionManager = new DuckDBConnectionManager(dbPath);
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "MailboxDB-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Get the database path.
     */
    public Path getDbPath() {
        return dbPath;
    }

    // ============================================================================
    // MESSAGE OPERATIONS
    // ============================================================================

    @Override
    public CompletableFuture<MailboxMessage> saveMessage(MailboxMessage message) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO mailbox_messages
                (id, sender_uuid, sender_name, recipient_uuid, subject, body,
                 message_type, created_at, read_at, expires_at,
                 has_attachment, attachment_claimed, attachment_data, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            DbPerformanceMonitor.QueryTimer timer = DbPerformanceMonitor.INSTANCE.startQuery("saveMessage");
            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, message.id().toString());
                setNullableUuid(stmt, 2, message.senderUuid());
                setNullableString(stmt, 3, message.senderName());
                stmt.setString(4, message.recipientUuid().toString());
                stmt.setString(5, message.subject());
                setNullableString(stmt, 6, message.body());
                stmt.setString(7, message.messageType().getId());
                stmt.setTimestamp(8, Timestamp.from(message.createdAt()));
                setNullableTimestamp(stmt, 9, message.readAt());
                setNullableTimestamp(stmt, 10, message.expiresAt());
                stmt.setBoolean(11, message.hasAttachment());
                stmt.setBoolean(12, message.attachmentClaimed());
                setNullableString(stmt, 13, message.attachmentData());
                stmt.setBoolean(14, message.deleted());

                stmt.executeUpdate();
                timer.stop();
                LOGGER.debug("[Mailbox] Saved message {} to {}", message.id(), message.recipientUuid());
                return message;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to save message", e);
                throw new RuntimeException("Failed to save message", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<MailboxMessage>> getMessage(UUID messageId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM mailbox_messages WHERE id = ?";

            DbPerformanceMonitor.QueryTimer timer = DbPerformanceMonitor.INSTANCE.startQuery("getMessage");
            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, messageId.toString());

                Optional<MailboxMessage> result;
                try (ResultSet rs = stmt.executeQuery()) {
                    result = rs.next()
                        ? Optional.of(mapResultSetToMessage(rs))
                        : Optional.empty();
                }
                timer.stop();
                return result;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to get message {}", messageId, e);
                throw new RuntimeException("Failed to get message", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<MailboxMessage>> getMessagesForPlayer(UUID playerUuid, boolean includeDeleted) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = includeDeleted
                ? "SELECT * FROM mailbox_messages WHERE recipient_uuid = ? ORDER BY created_at DESC"
                : "SELECT * FROM mailbox_messages WHERE recipient_uuid = ? AND deleted = FALSE ORDER BY created_at DESC";

            DbPerformanceMonitor.QueryTimer timer = DbPerformanceMonitor.INSTANCE.startQuery("getMessagesForPlayer");
            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                List<MailboxMessage> messages = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        messages.add(mapResultSetToMessage(rs));
                    }
                }
                timer.stop();
                return messages;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to get messages for player {}", playerUuid, e);
                throw new RuntimeException("Failed to get messages", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> getUnreadCount(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT COUNT(*) FROM mailbox_messages
                WHERE recipient_uuid = ? AND read_at IS NULL AND deleted = FALSE
                """;

            DbPerformanceMonitor.QueryTimer timer = DbPerformanceMonitor.INSTANCE.startQuery("getUnreadCount");
            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                int result;
                try (ResultSet rs = stmt.executeQuery()) {
                    result = rs.next() ? rs.getInt(1) : 0;
                }
                timer.stop();
                return result;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to get unread count for {}", playerUuid, e);
                throw new RuntimeException("Failed to get unread count", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> markAsRead(UUID messageId, Instant readAt) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE mailbox_messages SET read_at = ? WHERE id = ? AND read_at IS NULL";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setTimestamp(1, Timestamp.from(readAt));
                stmt.setString(2, messageId.toString());

                int updated = stmt.executeUpdate();
                return updated > 0;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to mark message {} as read", messageId, e);
                throw new RuntimeException("Failed to mark as read", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> markAttachmentClaimed(UUID messageId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                UPDATE mailbox_messages
                SET attachment_claimed = TRUE,
                    attachment_claiming = FALSE
                WHERE id = ? AND attachment_claimed = FALSE
                """;

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, messageId.toString());

                int updated = stmt.executeUpdate();
                return updated > 0;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to mark attachment claimed for {}", messageId, e);
                throw new RuntimeException("Failed to mark attachment claimed", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> startAttachmentClaim(UUID messageId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                UPDATE mailbox_messages
                SET attachment_claiming = TRUE
                WHERE id = ?
                  AND attachment_claimed = FALSE
                  AND attachment_claiming = FALSE
                """;

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, messageId.toString());

                int updated = stmt.executeUpdate();
                return updated > 0;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to start attachment claim for {}", messageId, e);
                throw new RuntimeException("Failed to start attachment claim", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> clearAttachmentClaim(UUID messageId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE mailbox_messages SET attachment_claiming = FALSE WHERE id = ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, messageId.toString());

                int updated = stmt.executeUpdate();
                return updated > 0;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to clear attachment claim for {}", messageId, e);
                throw new RuntimeException("Failed to clear attachment claim", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> softDeleteMessage(UUID messageId, @Nullable Instant retainUntil) {
        return CompletableFuture.supplyAsync(() -> {
            boolean withRetention = retainUntil != null;
            String sql = withRetention
                ? """
                    UPDATE mailbox_messages
                    SET deleted = TRUE,
                        expires_at = CASE
                            WHEN expires_at IS NULL OR expires_at > ? THEN ?
                            ELSE expires_at
                        END
                    WHERE id = ?
                    """
                : "UPDATE mailbox_messages SET deleted = TRUE WHERE id = ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                if (withRetention) {
                    Timestamp retention = Timestamp.from(retainUntil);
                    stmt.setTimestamp(1, retention);
                    stmt.setTimestamp(2, retention);
                    stmt.setString(3, messageId.toString());
                } else {
                    stmt.setString(1, messageId.toString());
                }

                int updated = stmt.executeUpdate();
                return updated > 0;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to soft-delete message {}", messageId, e);
                throw new RuntimeException("Failed to soft-delete message", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> hardDeleteMessage(UUID messageId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM mailbox_messages WHERE id = ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, messageId.toString());

                int deleted = stmt.executeUpdate();
                return deleted > 0;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to hard-delete message {}", messageId, e);
                throw new RuntimeException("Failed to hard-delete message", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> purgeExpiredMessages(Instant before) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM mailbox_messages WHERE expires_at IS NOT NULL AND expires_at < ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setTimestamp(1, Timestamp.from(before));

                int deleted = stmt.executeUpdate();
                if (deleted > 0) {
                    LOGGER.info("[Mailbox] Purged {} expired messages", deleted);
                }
                return deleted;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to purge expired messages", e);
                throw new RuntimeException("Failed to purge messages", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> getMessageCount(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM mailbox_messages WHERE recipient_uuid = ? AND deleted = FALSE";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                }

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to get message count for {}", playerUuid, e);
                throw new RuntimeException("Failed to get message count", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<MailboxMessage>> getAllMessages(int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT * FROM mailbox_messages
                WHERE deleted = FALSE
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;

            DbPerformanceMonitor.QueryTimer timer = DbPerformanceMonitor.INSTANCE.startQuery("getAllMessages");
            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, limit);
                stmt.setInt(2, offset);

                List<MailboxMessage> messages = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        messages.add(mapResultSetToMessage(rs));
                    }
                }
                timer.stop();
                return messages;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to get all messages", e);
                throw new RuntimeException("Failed to get all messages", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<UUID>> getKnownUsers() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT DISTINCT recipient_uuid FROM mailbox_messages";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                List<UUID> users = new ArrayList<>();
                while (rs.next()) {
                    users.add(UUID.fromString(rs.getString("recipient_uuid")));
                }
                return users;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to get known users", e);
                throw new RuntimeException("Failed to get known users", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> getTotalMessageCount() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM mailbox_messages WHERE deleted = FALSE";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to get total message count", e);
                throw new RuntimeException("Failed to get total message count", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> getTotalUnreadCount() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM mailbox_messages WHERE deleted = FALSE AND read_at IS NULL";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to get total unread count", e);
                throw new RuntimeException("Failed to get total unread count", e);
            }
        }, executor);
    }

    // ============================================================================
    // NEWS OPERATIONS
    // ============================================================================

    @Override
    public CompletableFuture<NewsArticle> saveNewsArticle(NewsArticle article) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO news_articles
                (id, title, content, category, author_name, created_at,
                 published_at, expires_at, priority, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, article.id().toString());
                stmt.setString(2, article.title());
                stmt.setString(3, article.content());
                stmt.setString(4, article.category().getId());
                setNullableString(stmt, 5, article.authorName());
                stmt.setTimestamp(6, Timestamp.from(article.createdAt()));
                setNullableTimestamp(stmt, 7, article.publishedAt());
                setNullableTimestamp(stmt, 8, article.expiresAt());
                stmt.setInt(9, article.priority());
                stmt.setBoolean(10, article.active());

                stmt.executeUpdate();
                LOGGER.debug("[Mailbox] Saved news article {}", article.id());
                return article;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to save news article", e);
                throw new RuntimeException("Failed to save news article", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<NewsArticle>> getNewsArticle(UUID articleId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM news_articles WHERE id = ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, articleId.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToNewsArticle(rs));
                    }
                    return Optional.empty();
                }

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to get news article {}", articleId, e);
                throw new RuntimeException("Failed to get news article", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<NewsArticle>> getActiveNews(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT * FROM news_articles
                WHERE active = TRUE
                  AND (published_at IS NULL OR published_at <= CURRENT_TIMESTAMP)
                  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                ORDER BY priority DESC, published_at DESC
                LIMIT ?
                """;

            DbPerformanceMonitor.QueryTimer timer = DbPerformanceMonitor.INSTANCE.startQuery("getActiveNews");
            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, limit);

                List<NewsArticle> articles = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        articles.add(mapResultSetToNewsArticle(rs));
                    }
                }
                timer.stop();
                return articles;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to get active news", e);
                throw new RuntimeException("Failed to get active news", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<NewsArticle>> getAllNews(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM news_articles ORDER BY created_at DESC LIMIT ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, limit);

                List<NewsArticle> articles = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        articles.add(mapResultSetToNewsArticle(rs));
                    }
                }
                return articles;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to get all news", e);
                throw new RuntimeException("Failed to get all news", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> updateNewsArticle(NewsArticle article) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                UPDATE news_articles SET
                title = ?, content = ?, category = ?, author_name = ?,
                published_at = ?, expires_at = ?, priority = ?, active = ?
                WHERE id = ?
                """;

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, article.title());
                stmt.setString(2, article.content());
                stmt.setString(3, article.category().getId());
                setNullableString(stmt, 4, article.authorName());
                setNullableTimestamp(stmt, 5, article.publishedAt());
                setNullableTimestamp(stmt, 6, article.expiresAt());
                stmt.setInt(7, article.priority());
                stmt.setBoolean(8, article.active());
                stmt.setString(9, article.id().toString());

                int updated = stmt.executeUpdate();
                return updated > 0;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to update news article {}", article.id(), e);
                throw new RuntimeException("Failed to update news article", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> deleteNewsArticle(UUID articleId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM news_articles WHERE id = ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, articleId.toString());

                int deleted = stmt.executeUpdate();
                return deleted > 0;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to delete news article {}", articleId, e);
                throw new RuntimeException("Failed to delete news article", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> hasReadNews(UUID playerUuid, UUID articleId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM news_read_status WHERE player_uuid = ? AND news_id = ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, articleId.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to check news read status", e);
                throw new RuntimeException("Failed to check news read status", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> markNewsAsRead(UUID playerUuid, UUID articleId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO news_read_status (player_uuid, news_id, read_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (player_uuid, news_id) DO NOTHING
                """;

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, articleId.toString());

                stmt.executeUpdate();
                return true;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to mark news as read", e);
                throw new RuntimeException("Failed to mark news as read", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> getUnreadNewsCount(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT COUNT(*) FROM news_articles n
                WHERE n.active = TRUE
                  AND (n.published_at IS NULL OR n.published_at <= CURRENT_TIMESTAMP)
                  AND (n.expires_at IS NULL OR n.expires_at > CURRENT_TIMESTAMP)
                  AND NOT EXISTS (
                    SELECT 1 FROM news_read_status r
                    WHERE r.player_uuid = ? AND r.news_id = n.id
                  )
                """;

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                }

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to get unread news count for {}", playerUuid, e);
                throw new RuntimeException("Failed to get unread news count", e);
            }
        }, executor);
    }

    // ============================================================================
    // TASK OPERATIONS
    // ============================================================================

    @Override
    public CompletableFuture<TestTask> saveTask(TestTask task) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO test_tasks
                (id, title, description, assigned_to, assigned_by_name,
                 priority, status, created_at, due_at, completed_at, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, task.id().toString());
                stmt.setString(2, task.title());
                setNullableString(stmt, 3, task.description());
                stmt.setString(4, task.assignedTo().toString());
                setNullableString(stmt, 5, task.assignedByName());
                stmt.setInt(6, task.priority());
                stmt.setString(7, task.status().getId());
                stmt.setLong(8, task.createdAt());
                setNullableLong(stmt, 9, task.dueAt());
                setNullableLong(stmt, 10, task.completedAt());
                setNullableString(stmt, 11, task.notes());

                stmt.executeUpdate();
                LOGGER.debug("[Tasks] Saved task {} for {}", task.id(), task.assignedTo());
                return task;

            } catch (SQLException e) {
                LOGGER.error("[Tasks] Failed to save task {}", task.id(), e);
                throw new RuntimeException("Failed to save task", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<TestTask>> getTask(UUID taskId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM test_tasks WHERE id = ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, taskId.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToTask(rs));
                    }
                    return Optional.empty();
                }

            } catch (SQLException e) {
                LOGGER.error("[Tasks] Failed to get task {}", taskId, e);
                throw new RuntimeException("Failed to get task", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<TestTask>> getTasksForUser(UUID userId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM test_tasks WHERE assigned_to = ? ORDER BY created_at DESC";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, userId.toString());

                List<TestTask> tasks = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        tasks.add(mapResultSetToTask(rs));
                    }
                }
                return tasks;

            } catch (SQLException e) {
                LOGGER.error("[Tasks] Failed to get tasks for user {}", userId, e);
                throw new RuntimeException("Failed to get tasks", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<TestTask>> getAllTasks(int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM test_tasks ORDER BY created_at DESC LIMIT ? OFFSET ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, limit);
                stmt.setInt(2, offset);

                List<TestTask> tasks = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        tasks.add(mapResultSetToTask(rs));
                    }
                }
                return tasks;

            } catch (SQLException e) {
                LOGGER.error("[Tasks] Failed to get tasks", e);
                throw new RuntimeException("Failed to get tasks", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> updateTask(TestTask task) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                UPDATE test_tasks SET
                title = ?, description = ?, assigned_to = ?, assigned_by_name = ?,
                priority = ?, status = ?, created_at = ?, due_at = ?, completed_at = ?, notes = ?
                WHERE id = ?
                """;

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, task.title());
                setNullableString(stmt, 2, task.description());
                stmt.setString(3, task.assignedTo().toString());
                setNullableString(stmt, 4, task.assignedByName());
                stmt.setInt(5, task.priority());
                stmt.setString(6, task.status().getId());
                stmt.setLong(7, task.createdAt());
                setNullableLong(stmt, 8, task.dueAt());
                setNullableLong(stmt, 9, task.completedAt());
                setNullableString(stmt, 10, task.notes());
                stmt.setString(11, task.id().toString());

                int updated = stmt.executeUpdate();
                return updated > 0;

            } catch (SQLException e) {
                LOGGER.error("[Tasks] Failed to update task {}", task.id(), e);
                throw new RuntimeException("Failed to update task", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> deleteTask(UUID taskId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM test_tasks WHERE id = ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, taskId.toString());

                int deleted = stmt.executeUpdate();
                return deleted > 0;

            } catch (SQLException e) {
                LOGGER.error("[Tasks] Failed to delete task {}", taskId, e);
                throw new RuntimeException("Failed to delete task", e);
            }
        }, executor);
    }

    // ============================================================================
    // TASK AUDIT OPERATIONS
    // ============================================================================

    @Override
    public CompletableFuture<TaskAuditEntry> saveTaskAudit(TaskAuditEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO task_audit (id, task_id, action, actor_uuid, actor_name, old_value, new_value, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                UUID actorUuid = entry.actorUuid();
                stmt.setString(1, entry.id().toString());
                stmt.setString(2, entry.taskId().toString());
                stmt.setString(3, entry.action());
                stmt.setString(4, actorUuid != null ? actorUuid.toString() : null);
                stmt.setString(5, entry.actorName());
                stmt.setString(6, entry.oldValue());
                stmt.setString(7, entry.newValue());
                stmt.setLong(8, entry.timestamp());

                stmt.executeUpdate();
                return entry;

            } catch (SQLException e) {
                LOGGER.error("[Tasks] Failed to save audit entry for task {}", entry.taskId(), e);
                throw new RuntimeException("Failed to save task audit entry", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<TaskAuditEntry>> getTaskAuditHistory(UUID taskId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM task_audit WHERE task_id = ? ORDER BY timestamp DESC";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, taskId.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    List<TaskAuditEntry> entries = new java.util.ArrayList<>();
                    while (rs.next()) {
                        entries.add(mapResultSetToAuditEntry(rs));
                    }
                    return entries;
                }

            } catch (SQLException e) {
                LOGGER.error("[Tasks] Failed to get audit history for task {}", taskId, e);
                throw new RuntimeException("Failed to get task audit history", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<TaskAuditEntry>> getRecentTaskAudits(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM task_audit ORDER BY timestamp DESC LIMIT ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    List<TaskAuditEntry> entries = new java.util.ArrayList<>();
                    while (rs.next()) {
                        entries.add(mapResultSetToAuditEntry(rs));
                    }
                    return entries;
                }

            } catch (SQLException e) {
                LOGGER.error("[Tasks] Failed to get recent audit entries", e);
                throw new RuntimeException("Failed to get recent task audits", e);
            }
        }, executor);
    }

    private TaskAuditEntry mapResultSetToAuditEntry(ResultSet rs) throws SQLException {
        return TaskAuditEntry.builder()
            .id(UUID.fromString(rs.getString("id")))
            .taskId(UUID.fromString(rs.getString("task_id")))
            .action(rs.getString("action"))
            .actorUuid(getNullableUuid(rs, "actor_uuid"))
            .actorName(rs.getString("actor_name"))
            .oldValue(rs.getString("old_value"))
            .newValue(rs.getString("new_value"))
            .timestamp(rs.getLong("timestamp"))
            .build();
    }

    // ============================================================================
    // ADMIN AUDIT OPERATIONS
    // ============================================================================

    @Override
    public CompletableFuture<AdminAuditLog.AuditEntry> saveAdminAudit(AdminAuditLog.AuditEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO admin_audit_log
                (id, action, actor_uuid, actor_name, target_type, target_id, details, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                UUID actorUuid = entry.actorUuid();
                stmt.setString(1, entry.id().toString());
                stmt.setString(2, entry.action().name());
                stmt.setString(3, actorUuid != null ? actorUuid.toString() : null);
                stmt.setString(4, entry.actorName());
                stmt.setString(5, entry.targetType());
                stmt.setString(6, entry.targetId());
                stmt.setString(7, entry.details());
                stmt.setLong(8, entry.timestamp());

                stmt.executeUpdate();
                return entry;
            } catch (SQLException e) {
                LOGGER.error("[AdminAudit] Failed to save audit entry {}", entry.id(), e);
                throw new RuntimeException("Failed to save admin audit entry", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<AdminAuditLog.AuditEntry>> getRecentAdminAudits(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM admin_audit_log ORDER BY timestamp DESC LIMIT ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    List<AdminAuditLog.AuditEntry> entries = new ArrayList<>();
                    while (rs.next()) {
                        entries.add(mapResultSetToAdminAudit(rs));
                    }
                    return entries;
                }
            } catch (SQLException e) {
                LOGGER.error("[AdminAudit] Failed to get recent audit entries", e);
                throw new RuntimeException("Failed to get recent admin audit entries", e);
            }
        }, executor);
    }

    private AdminAuditLog.AuditEntry mapResultSetToAdminAudit(ResultSet rs) throws SQLException {
        return AdminAuditLog.AuditEntry.builder()
            .id(UUID.fromString(rs.getString("id")))
            .action(AdminAuditLog.Action.valueOf(rs.getString("action")))
            .actorUuid(getNullableUuid(rs, "actor_uuid"))
            .actorName(rs.getString("actor_name"))
            .targetType(rs.getString("target_type"))
            .targetId(rs.getString("target_id"))
            .details(rs.getString("details"))
            .timestamp(rs.getLong("timestamp"))
            .build();
    }

    // ============================================================================
    // LIFECYCLE
    // ============================================================================

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            LOGGER.info("[Mailbox] Initializing DuckDB repository...");

            try (Connection conn = connectionManager.getConnection()) {
                initializeSchema(conn);
                LOGGER.info("[Mailbox] DuckDB repository initialized successfully");
            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to initialize repository", e);
                throw new RuntimeException("Failed to initialize mailbox repository", e);
            }
        }, executor);
    }

    private void initializeSchema(Connection conn) throws SQLException {
        createSchemaVersionTable(conn);
        createTables(conn);

        int currentVersion = getSchemaVersion(conn);
        if (currentVersion == 0) {
            setSchemaVersion(conn, CURRENT_SCHEMA_VERSION);
            LOGGER.info("[Mailbox] Schema version initialized to {}", CURRENT_SCHEMA_VERSION);
            return;
        }

        if (currentVersion < CURRENT_SCHEMA_VERSION) {
            applyMigrations(conn, currentVersion, CURRENT_SCHEMA_VERSION);
            setSchemaVersion(conn, CURRENT_SCHEMA_VERSION);
            LOGGER.info("[Mailbox] Schema migrated {} -> {}", currentVersion, CURRENT_SCHEMA_VERSION);
        }
    }

    private void createSchemaVersionTable(Connection conn) throws SQLException {
        String versionTable = """
            CREATE TABLE IF NOT EXISTS mailbox_schema_version (
                version INTEGER NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        try (var stmt = conn.createStatement()) {
            stmt.execute(versionTable);
        }
    }

    private int getSchemaVersion(Connection conn) throws SQLException {
        String sql = "SELECT version FROM mailbox_schema_version ORDER BY updated_at DESC LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private void setSchemaVersion(Connection conn, int version) throws SQLException {
        String sql = "INSERT INTO mailbox_schema_version (version, updated_at) VALUES (?, CURRENT_TIMESTAMP)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, version);
            stmt.executeUpdate();
        }
    }

    private void applyMigrations(Connection conn, int fromVersion, int toVersion) throws SQLException {
        int current = fromVersion;
        while (current < toVersion) {
            int next = current + 1;
            switch (next) {
                case 1 -> {
                    // Initial version - nothing to migrate.
                }
                case 2 -> {
                    createTasksTable(conn);
                }
                case 3 -> {
                    createTaskAuditTable(conn);
                }
                case 4 -> {
                    addAttachmentClaimingColumn(conn);
                }
                case 5 -> {
                    widenSubjectColumn(conn);
                }
                case 6 -> {
                    createAdminAuditTable(conn);
                }
                default -> throw new SQLException("Unknown mailbox schema version: " + next);
            }
            current = next;
        }
    }

    private void createTables(Connection conn) throws SQLException {
        // Create messages table
        String messagesTable = """
            CREATE TABLE IF NOT EXISTS mailbox_messages (
                id VARCHAR PRIMARY KEY,
                sender_uuid VARCHAR,
                sender_name VARCHAR(64),
                recipient_uuid VARCHAR NOT NULL,
                subject VARCHAR(256) NOT NULL,
                body TEXT,
                message_type VARCHAR(32) NOT NULL,
                created_at TIMESTAMP NOT NULL,
                read_at TIMESTAMP,
                expires_at TIMESTAMP,
                has_attachment BOOLEAN DEFAULT FALSE,
                attachment_claiming BOOLEAN DEFAULT FALSE,
                attachment_claimed BOOLEAN DEFAULT FALSE,
                attachment_data TEXT,
                deleted BOOLEAN DEFAULT FALSE
            )
            """;

        // Create news table
        String newsTable = """
            CREATE TABLE IF NOT EXISTS news_articles (
                id VARCHAR PRIMARY KEY,
                title VARCHAR(256) NOT NULL,
                content TEXT NOT NULL,
                category VARCHAR(32) NOT NULL,
                author_name VARCHAR(64),
                created_at TIMESTAMP NOT NULL,
                published_at TIMESTAMP,
                expires_at TIMESTAMP,
                priority INTEGER DEFAULT 0,
                active BOOLEAN DEFAULT TRUE
            )
            """;

        // Create news read status table
        String newsReadTable = """
            CREATE TABLE IF NOT EXISTS news_read_status (
                player_uuid VARCHAR NOT NULL,
                news_id VARCHAR NOT NULL,
                read_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (player_uuid, news_id)
            )
            """;

        try (var stmt = conn.createStatement()) {
            stmt.execute(messagesTable);
            stmt.execute(newsTable);
            stmt.execute(newsReadTable);
            createTasksTable(conn);
            createTaskAuditTable(conn);
            createAdminAuditTable(conn);

            // Create indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_recipient ON mailbox_messages(recipient_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_expires ON mailbox_messages(expires_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_news_active ON news_articles(active, published_at)");
        }

        LOGGER.debug("[Mailbox] Database tables created");
    }

    private void createTasksTable(Connection conn) throws SQLException {
        String tasksTable = """
            CREATE TABLE IF NOT EXISTS test_tasks (
                id VARCHAR PRIMARY KEY,
                title VARCHAR(256) NOT NULL,
                description TEXT,
                assigned_to VARCHAR NOT NULL,
                assigned_by_name VARCHAR(64),
                priority INTEGER DEFAULT 1,
                status VARCHAR(32) NOT NULL,
                created_at BIGINT NOT NULL,
                due_at BIGINT,
                completed_at BIGINT,
                notes TEXT
            )
            """;

        try (var stmt = conn.createStatement()) {
            stmt.execute(tasksTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tasks_assigned ON test_tasks(assigned_to)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tasks_status ON test_tasks(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tasks_created ON test_tasks(created_at)");
        }
    }

    private void createTaskAuditTable(Connection conn) throws SQLException {
        String auditTable = """
            CREATE TABLE IF NOT EXISTS task_audit (
                id VARCHAR PRIMARY KEY,
                task_id VARCHAR NOT NULL,
                action VARCHAR(32) NOT NULL,
                actor_uuid VARCHAR,
                actor_name VARCHAR(64),
                old_value TEXT,
                new_value TEXT,
                timestamp BIGINT NOT NULL
            )
            """;

        try (var stmt = conn.createStatement()) {
            stmt.execute(auditTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_task_audit_task ON task_audit(task_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_task_audit_timestamp ON task_audit(timestamp)");
        }
    }

    private void createAdminAuditTable(Connection conn) throws SQLException {
        String auditTable = """
            CREATE TABLE IF NOT EXISTS admin_audit_log (
                id VARCHAR PRIMARY KEY,
                action VARCHAR(32) NOT NULL,
                actor_uuid VARCHAR,
                actor_name VARCHAR(64) NOT NULL,
                target_type VARCHAR(64) NOT NULL,
                target_id TEXT,
                details TEXT,
                timestamp BIGINT NOT NULL
            )
            """;

        try (var stmt = conn.createStatement()) {
            stmt.execute(auditTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_action ON admin_audit_log(action)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_actor ON admin_audit_log(actor_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_admin_audit_timestamp ON admin_audit_log(timestamp)");
        }
    }

    private void addAttachmentClaimingColumn(Connection conn) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE mailbox_messages ADD COLUMN attachment_claiming BOOLEAN DEFAULT FALSE");
        }
    }

    private void widenSubjectColumn(Connection conn) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE mailbox_messages ALTER COLUMN subject SET DATA TYPE VARCHAR(256)");
        }
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            LOGGER.info("[Mailbox] Shutting down DuckDB repository...");
            connectionManager.shutdown();
            executor.shutdown();
            LOGGER.info("[Mailbox] DuckDB repository shutdown complete");
        });
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private MailboxMessage mapResultSetToMessage(ResultSet rs) throws SQLException {
        return MailboxMessage.builder()
            .id(UUID.fromString(rs.getString("id")))
            .sender(
                getNullableUuid(rs, "sender_uuid"),
                rs.getString("sender_name")
            )
            .recipient(UUID.fromString(rs.getString("recipient_uuid")))
            .subject(rs.getString("subject"))
            .body(rs.getString("body"))
            .messageType(MessageType.fromId(rs.getString("message_type")))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .readAt(getNullableInstant(rs, "read_at"))
            .expiresAt(getNullableInstant(rs, "expires_at"))
            .attachment(rs.getString("attachment_data"))
            .attachmentClaimed(rs.getBoolean("attachment_claimed"))
            .deleted(rs.getBoolean("deleted"))
            .build();
    }

    private TestTask mapResultSetToTask(ResultSet rs) throws SQLException {
        return TestTask.builder()
            .id(UUID.fromString(rs.getString("id")))
            .title(rs.getString("title"))
            .description(rs.getString("description"))
            .assignedTo(UUID.fromString(rs.getString("assigned_to")))
            .assignedByName(rs.getString("assigned_by_name"))
            .priority(rs.getInt("priority"))
            .status(TestTask.TaskStatus.fromId(rs.getString("status")))
            .createdAt(rs.getLong("created_at"))
            .dueAt(getNullableLong(rs, "due_at"))
            .completedAt(getNullableLong(rs, "completed_at"))
            .notes(rs.getString("notes"))
            .build();
    }

    private NewsArticle mapResultSetToNewsArticle(ResultSet rs) throws SQLException {
        return NewsArticle.builder()
            .id(UUID.fromString(rs.getString("id")))
            .title(rs.getString("title"))
            .content(rs.getString("content"))
            .category(NewsCategory.fromId(rs.getString("category")))
            .authorName(rs.getString("author_name"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .publishedAt(getNullableInstant(rs, "published_at"))
            .expiresAt(getNullableInstant(rs, "expires_at"))
            .priority(rs.getInt("priority"))
            .active(rs.getBoolean("active"))
            .build();
    }

    private void setNullableUuid(PreparedStatement stmt, int index, @Nullable UUID uuid) throws SQLException {
        if (uuid != null) {
            stmt.setString(index, uuid.toString());
        } else {
            stmt.setNull(index, Types.VARCHAR);
        }
    }

    private void setNullableString(PreparedStatement stmt, int index, @Nullable String value) throws SQLException {
        if (value != null) {
            stmt.setString(index, value);
        } else {
            stmt.setNull(index, Types.VARCHAR);
        }
    }

    private void setNullableTimestamp(PreparedStatement stmt, int index, @Nullable Instant instant) throws SQLException {
        if (instant != null) {
            stmt.setTimestamp(index, Timestamp.from(instant));
        } else {
            stmt.setNull(index, Types.TIMESTAMP);
        }
    }

    private void setNullableLong(PreparedStatement stmt, int index, @Nullable Long value) throws SQLException {
        if (value != null) {
            stmt.setLong(index, value);
        } else {
            stmt.setNull(index, Types.BIGINT);
        }
    }

    @Nullable
    private UUID getNullableUuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value != null ? UUID.fromString(value) : null;
    }

    @Nullable
    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    @Nullable
    private Instant getNullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }

    // ============================================================================
    // BACKUP/RESTORE
    // ============================================================================

    private static final String BACKUP_SUFFIX = ".backup";
    private static final java.time.format.DateTimeFormatter BACKUP_DATE_FORMAT =
        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private Path getBackupDir(Path dbPath) {
        Path parent = dbPath.getParent();
        if (parent == null) {
            throw new IllegalStateException("Database path has no parent: " + dbPath);
        }
        return parent.resolve("backups");
    }

    @Override
    public CompletableFuture<String> createBackup(@Nullable String backupName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Generate backup name if not provided
                String name = backupName;
                if (name == null || name.isBlank()) {
                    name = "mailbox_" + LocalDateTime.now(ZoneId.systemDefault())
                        .format(BACKUP_DATE_FORMAT);
                }

                Path dbPath = connectionManager.getDbPath();
                Path backupDir = getBackupDir(dbPath);

                // Create backup directory if it doesn't exist
                if (!java.nio.file.Files.exists(backupDir)) {
                    java.nio.file.Files.createDirectories(backupDir);
                }

                // Checkpoint WAL to ensure all data is in main file
                try (Connection conn = connectionManager.getConnection();
                     var stmt = conn.createStatement()) {
                    stmt.execute("CHECKPOINT");
                    LOGGER.debug("[Mailbox] WAL checkpoint completed before backup");
                }

                // Copy database file to backup
                Path backupPath = backupDir.resolve(name + BACKUP_SUFFIX);
                java.nio.file.Files.copy(dbPath, backupPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                LOGGER.info("[Mailbox] Backup created: {}", backupPath);
                return backupPath.toString();

            } catch (Exception e) {
                LOGGER.error("[Mailbox] Failed to create backup", e);
                throw new RuntimeException("Failed to create backup", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> restoreBackup(String backupName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path dbPath = connectionManager.getDbPath();
                Path backupDir = getBackupDir(dbPath);
                Path backupPath = backupDir.resolve(backupName + BACKUP_SUFFIX);

                if (!java.nio.file.Files.exists(backupPath)) {
                    LOGGER.warn("[Mailbox] Backup not found: {}", backupPath);
                    return false;
                }

                // Shutdown connection manager to release file lock
                connectionManager.shutdown();

                // Create a safety backup of current database before restore
                Path safetyBackup = dbPath.resolveSibling(dbPath.getFileName() + ".pre_restore");
                if (java.nio.file.Files.exists(dbPath)) {
                    java.nio.file.Files.copy(dbPath, safetyBackup,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }

                // Restore from backup
                java.nio.file.Files.copy(backupPath, dbPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                LOGGER.info("[Mailbox] Restored from backup: {}", backupPath);
                LOGGER.info("[Mailbox] Note: Server restart required to use restored database");
                return true;

            } catch (Exception e) {
                LOGGER.error("[Mailbox] Failed to restore backup", e);
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<String>> listBackups() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path dbPath = connectionManager.getDbPath();
                Path backupDir = getBackupDir(dbPath);

                if (!java.nio.file.Files.exists(backupDir)) {
                    return List.<String>of();
                }

                try (var stream = java.nio.file.Files.list(backupDir)) {
                    return stream
                        .filter(p -> p.toString().endsWith(BACKUP_SUFFIX))
                        .map(p -> {
                            String fileName = p.getFileName().toString();
                            return fileName.substring(0, fileName.length() - BACKUP_SUFFIX.length());
                        })
                        .sorted(java.util.Comparator.reverseOrder())
                        .toList();
                }

            } catch (Exception e) {
                LOGGER.error("[Mailbox] Failed to list backups", e);
                return List.of();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> deleteBackup(String backupName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path dbPath = connectionManager.getDbPath();
                Path backupDir = getBackupDir(dbPath);
                Path backupPath = backupDir.resolve(backupName + BACKUP_SUFFIX);

                if (!java.nio.file.Files.exists(backupPath)) {
                    LOGGER.warn("[Mailbox] Backup not found: {}", backupPath);
                    return false;
                }

                java.nio.file.Files.delete(backupPath);
                LOGGER.info("[Mailbox] Deleted backup: {}", backupPath);
                return true;

            } catch (Exception e) {
                LOGGER.error("[Mailbox] Failed to delete backup", e);
                return false;
            }
        }, executor);
    }

    /**
     * Get backup file size in bytes.
     */
    public CompletableFuture<Long> getBackupSize(String backupName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path dbPath = connectionManager.getDbPath();
                Path backupDir = getBackupDir(dbPath);
                Path backupPath = backupDir.resolve(backupName + BACKUP_SUFFIX);

                if (!java.nio.file.Files.exists(backupPath)) {
                    return -1L;
                }

                return java.nio.file.Files.size(backupPath);

            } catch (Exception e) {
                LOGGER.error("[Mailbox] Failed to get backup size", e);
                return -1L;
            }
        }, executor);
    }
}
