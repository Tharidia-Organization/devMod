package com.devmod.mailbox.persistence;

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
import com.devmod.mailbox.news.NewsArticle;
import com.devmod.mailbox.news.NewsCategory;
import com.devmod.telemetry.duckdb.DuckDBConnectionManager;

/**
 * DuckDB implementation of MailboxRepository.
 *
 * Uses the existing DuckDB infrastructure for persistence.
 */
public class DuckDbMailboxRepository implements MailboxRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DuckDbMailboxRepository.class);

    private final DuckDBConnectionManager connectionManager;
    private final ExecutorService executor;

    public DuckDbMailboxRepository(Path dbPath) {
        this.connectionManager = new DuckDBConnectionManager(dbPath);
        this.executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "MailboxDB-Worker");
            t.setDaemon(true);
            return t;
        });
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

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, messageId.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSetToMessage(rs));
                    }
                    return Optional.empty();
                }

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

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                List<MailboxMessage> messages = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        messages.add(mapResultSetToMessage(rs));
                    }
                }
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
            String sql = "UPDATE mailbox_messages SET attachment_claimed = TRUE WHERE id = ? AND attachment_claimed = FALSE";

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
    public CompletableFuture<Boolean> deleteMessage(UUID messageId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE mailbox_messages SET deleted = TRUE WHERE id = ?";

            try (Connection conn = connectionManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, messageId.toString());

                int updated = stmt.executeUpdate();
                return updated > 0;

            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to delete message {}", messageId, e);
                throw new RuntimeException("Failed to delete message", e);
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
    // LIFECYCLE
    // ============================================================================

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            LOGGER.info("[Mailbox] Initializing DuckDB repository...");

            try (Connection conn = connectionManager.getConnection()) {
                createTables(conn);
                LOGGER.info("[Mailbox] DuckDB repository initialized successfully");
            } catch (SQLException e) {
                LOGGER.error("[Mailbox] Failed to initialize repository", e);
                throw new RuntimeException("Failed to initialize mailbox repository", e);
            }
        }, executor);
    }

    private void createTables(Connection conn) throws SQLException {
        // Create messages table
        String messagesTable = """
            CREATE TABLE IF NOT EXISTS mailbox_messages (
                id VARCHAR PRIMARY KEY,
                sender_uuid VARCHAR,
                sender_name VARCHAR(64),
                recipient_uuid VARCHAR NOT NULL,
                subject VARCHAR(128) NOT NULL,
                body TEXT,
                message_type VARCHAR(32) NOT NULL,
                created_at TIMESTAMP NOT NULL,
                read_at TIMESTAMP,
                expires_at TIMESTAMP,
                has_attachment BOOLEAN DEFAULT FALSE,
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

            // Create indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_recipient ON mailbox_messages(recipient_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_expires ON mailbox_messages(expires_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_news_active ON news_articles(active, published_at)");
        }

        LOGGER.debug("[Mailbox] Database tables created");
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

    @Nullable
    private UUID getNullableUuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value != null ? UUID.fromString(value) : null;
    }

    @Nullable
    private Instant getNullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }
}
