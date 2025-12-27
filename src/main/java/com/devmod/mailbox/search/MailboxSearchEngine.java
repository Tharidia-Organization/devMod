package com.devmod.mailbox.search;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.fml.loading.FMLPaths;

import com.devmod.mailbox.MessageType;
import com.devmod.telemetry.duckdb.DuckDBConnectionManager;

/**
 * Full-text search engine for mailbox messages.
 *
 * Features:
 * - Full-text search on subject, body, and sender
 * - Filtering by message type, read status, date range
 * - Pagination and sorting
 * - Relevance scoring
 * - Search highlighting
 * - Search suggestions/autocomplete
 */
public final class MailboxSearchEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailboxSearchEngine.class);

    public static final MailboxSearchEngine INSTANCE = new MailboxSearchEngine();

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SUGGESTIONS = 10;

    private final DuckDBConnectionManager connectionManager;
    private final ExecutorService executor;
    private volatile boolean initialized = false;

    private MailboxSearchEngine() {
        connectionManager = new DuckDBConnectionManager(resolveDbPath());
        executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Mailbox-Search");
            t.setDaemon(true);
            return t;
        });
    }

    private static Path resolveDbPath() {
        return FMLPaths.GAMEDIR.get().resolve("devmod").resolve("mailbox.duckdb");
    }

    // ============================================================================
    // INITIALIZATION
    // ============================================================================

    /**
     * Initialize the search engine and create FTS indexes.
     */
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Connection conn = connectionManager.getConnection();

                // Create FTS index table for messages
                // DuckDB uses a separate table approach for FTS
                try (PreparedStatement stmt = conn.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS mailbox_messages_fts (
                        message_id VARCHAR PRIMARY KEY,
                        recipient_uuid VARCHAR NOT NULL,
                        sender_name VARCHAR,
                        subject VARCHAR,
                        body VARCHAR,
                        message_type VARCHAR,
                        sent_at TIMESTAMP,
                        read_at TIMESTAMP,
                        search_text VARCHAR GENERATED ALWAYS AS (
                            COALESCE(subject, '') || ' ' ||
                            COALESCE(body, '') || ' ' ||
                            COALESCE(sender_name, '')
                        ) STORED
                    )
                    """)) {
                    stmt.execute();
                }

                // Create indexes for common queries
                try (PreparedStatement stmt = conn.prepareStatement("""
                    CREATE INDEX IF NOT EXISTS idx_fts_recipient
                    ON mailbox_messages_fts(recipient_uuid)
                    """)) {
                    stmt.execute();
                }

                try (PreparedStatement stmt = conn.prepareStatement("""
                    CREATE INDEX IF NOT EXISTS idx_fts_sent_at
                    ON mailbox_messages_fts(sent_at DESC)
                    """)) {
                    stmt.execute();
                }

                try (PreparedStatement stmt = conn.prepareStatement("""
                    CREATE INDEX IF NOT EXISTS idx_fts_type
                    ON mailbox_messages_fts(message_type)
                    """)) {
                    stmt.execute();
                }

                initialized = true;
                LOGGER.info("[Search] Full-text search engine initialized");
                return true;
            } catch (SQLException e) {
                LOGGER.error("[Search] Failed to initialize FTS", e);
                return false;
            }
        }, executor);
    }

    /**
     * Index a message for full-text search.
     */
    public CompletableFuture<Void> indexMessage(UUID messageId, UUID recipientUuid,
                                                 @Nullable String senderName,
                                                 String subject, String body,
                                                 MessageType type, Instant sentAt,
                                                 @Nullable Instant readAt) {
        return CompletableFuture.runAsync(() -> {
            if (!initialized) {
                LOGGER.warn("[Search] Search engine not initialized, skipping index");
                return;
            }

            try {
                Connection conn = connectionManager.getConnection();
                try (PreparedStatement stmt = conn.prepareStatement("""
                    INSERT INTO mailbox_messages_fts
                    (message_id, recipient_uuid, sender_name, subject, body, message_type, sent_at, read_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (message_id) DO UPDATE SET
                        sender_name = EXCLUDED.sender_name,
                        subject = EXCLUDED.subject,
                        body = EXCLUDED.body,
                        message_type = EXCLUDED.message_type,
                        sent_at = EXCLUDED.sent_at,
                        read_at = EXCLUDED.read_at
                    """)) {
                    stmt.setString(1, messageId.toString());
                    stmt.setString(2, recipientUuid.toString());
                    stmt.setString(3, senderName);
                    stmt.setString(4, subject);
                    stmt.setString(5, body);
                    stmt.setString(6, type.name());
                    stmt.setTimestamp(7, java.sql.Timestamp.from(sentAt));
                    stmt.setTimestamp(8, readAt != null ? java.sql.Timestamp.from(readAt) : null);
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                LOGGER.error("[Search] Failed to index message {}", messageId, e);
            }
        }, executor);
    }

    /**
     * Remove a message from the search index.
     */
    public CompletableFuture<Void> removeFromIndex(UUID messageId) {
        return CompletableFuture.runAsync(() -> {
            if (!initialized) return;

            try {
                Connection conn = connectionManager.getConnection();
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM mailbox_messages_fts WHERE message_id = ?")) {
                    stmt.setString(1, messageId.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                LOGGER.error("[Search] Failed to remove message {} from index", messageId, e);
            }
        }, executor);
    }

    /**
     * Update read status in search index.
     */
    public CompletableFuture<Void> updateReadStatus(UUID messageId, Instant readAt) {
        return CompletableFuture.runAsync(() -> {
            if (!initialized) return;

            try {
                Connection conn = connectionManager.getConnection();
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE mailbox_messages_fts SET read_at = ? WHERE message_id = ?")) {
                    stmt.setTimestamp(1, java.sql.Timestamp.from(readAt));
                    stmt.setString(2, messageId.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                LOGGER.error("[Search] Failed to update read status for {}", messageId, e);
            }
        }, executor);
    }

    // ============================================================================
    // SEARCH
    // ============================================================================

    /**
     * Search messages with full-text query.
     *
     * @param query the search parameters
     * @return search results with pagination info
     */
    public CompletableFuture<SearchResult> search(SearchQuery query) {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                LOGGER.warn("[Search] Search engine not initialized");
                return SearchResult.empty();
            }

            Objects.requireNonNull(query.playerUuid, "Player UUID is required");

            try {
                Connection conn = connectionManager.getConnection();

                // Build the search query
                StringBuilder sql = new StringBuilder();
                List<Object> params = new ArrayList<>();

                sql.append("SELECT message_id, sender_name, subject, body, message_type, sent_at, read_at ");

                // Extract nullable fields to local variables for null-safety
                String searchText = query.searchText;
                MessageType msgType = query.messageType;
                String senderName = query.senderName;

                // Add relevance score if searching text
                if (searchText != null && !searchText.isBlank()) {
                    sql.append(", (");
                    sql.append("CASE WHEN LOWER(subject) LIKE ? THEN 3 ELSE 0 END + ");
                    sql.append("CASE WHEN LOWER(body) LIKE ? THEN 2 ELSE 0 END + ");
                    sql.append("CASE WHEN LOWER(sender_name) LIKE ? THEN 1 ELSE 0 END");
                    sql.append(") AS relevance ");
                    String searchPattern = "%" + searchText.toLowerCase(Locale.ROOT) + "%";
                    params.add(searchPattern);
                    params.add(searchPattern);
                    params.add(searchPattern);
                } else {
                    sql.append(", 0 AS relevance ");
                }

                sql.append("FROM mailbox_messages_fts WHERE recipient_uuid = ? ");
                params.add(query.playerUuid.toString());

                // Full-text search
                if (searchText != null && !searchText.isBlank()) {
                    sql.append("AND (");
                    sql.append("LOWER(subject) LIKE ? OR ");
                    sql.append("LOWER(body) LIKE ? OR ");
                    sql.append("LOWER(sender_name) LIKE ?");
                    sql.append(") ");
                    String searchPattern = "%" + searchText.toLowerCase(Locale.ROOT) + "%";
                    params.add(searchPattern);
                    params.add(searchPattern);
                    params.add(searchPattern);
                }

                // Message type filter
                if (msgType != null) {
                    sql.append("AND message_type = ? ");
                    params.add(msgType.name());
                }

                // Read status filter
                if (query.readStatus != null) {
                    switch (query.readStatus) {
                        case READ -> sql.append("AND read_at IS NOT NULL ");
                        case UNREAD -> sql.append("AND read_at IS NULL ");
                        case ALL -> {} // No filter
                    }
                }

                // Date range filters
                if (query.fromDate != null) {
                    sql.append("AND sent_at >= ? ");
                    params.add(java.sql.Timestamp.from(query.fromDate));
                }
                if (query.toDate != null) {
                    sql.append("AND sent_at <= ? ");
                    params.add(java.sql.Timestamp.from(query.toDate));
                }

                // Sender filter
                if (senderName != null && !senderName.isBlank()) {
                    sql.append("AND LOWER(sender_name) LIKE ? ");
                    params.add("%" + senderName.toLowerCase(Locale.ROOT) + "%");
                }

                // Sorting
                if (searchText != null && !searchText.isBlank()) {
                    sql.append("ORDER BY relevance DESC, sent_at DESC ");
                } else {
                    sql.append("ORDER BY ");
                    sql.append(switch (query.sortBy) {
                        case DATE_ASC -> "sent_at ASC ";
                        case DATE_DESC -> "sent_at DESC ";
                        case SENDER_ASC -> "sender_name ASC, sent_at DESC ";
                        case SENDER_DESC -> "sender_name DESC, sent_at DESC ";
                        case RELEVANCE -> "relevance DESC, sent_at DESC ";
                    });
                }

                // Pagination
                int pageSize = Math.min(query.pageSize > 0 ? query.pageSize : DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
                int offset = query.page * pageSize;
                sql.append("LIMIT ? OFFSET ?");
                params.add(pageSize);
                params.add(offset);

                // Execute search
                List<SearchHit> hits = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        stmt.setObject(i + 1, params.get(i));
                    }

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String highlight = null;
                            String body = rs.getString("body");
                            if (query.searchText != null && body != null) {
                                highlight = createHighlight(body, query.searchText);
                            }

                            hits.add(new SearchHit(
                                UUID.fromString(rs.getString("message_id")),
                                rs.getString("sender_name"),
                                rs.getString("subject"),
                                body,
                                MessageType.valueOf(rs.getString("message_type")),
                                rs.getTimestamp("sent_at").toInstant(),
                                rs.getTimestamp("read_at") != null
                                    ? rs.getTimestamp("read_at").toInstant() : null,
                                rs.getDouble("relevance"),
                                highlight
                            ));
                        }
                    }
                }

                // Get total count for pagination
                int totalCount = getTotalCount(conn, query);
                int totalPages = (int) Math.ceil((double) totalCount / pageSize);

                return new SearchResult(
                    hits,
                    query.page,
                    pageSize,
                    totalCount,
                    totalPages,
                    query.searchText
                );

            } catch (SQLException e) {
                LOGGER.error("[Search] Search failed", e);
                return SearchResult.empty();
            }
        }, executor);
    }

    /**
     * Search with simple string query (convenience method).
     */
    public CompletableFuture<SearchResult> search(UUID playerUuid, String searchText) {
        return search(SearchQuery.builder()
            .playerUuid(playerUuid)
            .searchText(searchText)
            .build());
    }

    /**
     * Get search suggestions/autocomplete based on previous messages.
     */
    public CompletableFuture<List<String>> getSuggestions(UUID playerUuid, String prefix) {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized || prefix == null || prefix.length() < 2) {
                return Collections.emptyList();
            }

            try {
                Connection conn = connectionManager.getConnection();

                // Get suggestions from subjects and sender names
                List<String> suggestions = new ArrayList<>();
                String pattern = prefix.toLowerCase(Locale.ROOT) + "%";

                try (PreparedStatement stmt = conn.prepareStatement("""
                    SELECT DISTINCT subject FROM mailbox_messages_fts
                    WHERE recipient_uuid = ? AND LOWER(subject) LIKE ?
                    LIMIT ?
                    """)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, pattern);
                    stmt.setInt(3, MAX_SUGGESTIONS / 2);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String subject = rs.getString("subject");
                            if (subject != null) {
                                suggestions.add(subject);
                            }
                        }
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement("""
                    SELECT DISTINCT sender_name FROM mailbox_messages_fts
                    WHERE recipient_uuid = ? AND LOWER(sender_name) LIKE ?
                    LIMIT ?
                    """)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, pattern);
                    stmt.setInt(3, MAX_SUGGESTIONS / 2);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String sender = rs.getString("sender_name");
                            if (sender != null && !suggestions.contains(sender)) {
                                suggestions.add(sender);
                            }
                        }
                    }
                }

                return suggestions.stream().limit(MAX_SUGGESTIONS).toList();

            } catch (SQLException e) {
                LOGGER.error("[Search] Failed to get suggestions", e);
                return Collections.emptyList();
            }
        }, executor);
    }

    /**
     * Get recent searches for a player (if tracked).
     */
    public CompletableFuture<List<String>> getRecentSearches(UUID playerUuid, int limit) {
        // This could be expanded to track search history
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    // ============================================================================
    // BATCH OPERATIONS
    // ============================================================================

    /**
     * Rebuild the entire search index from messages table.
     */
    public CompletableFuture<Integer> rebuildIndex() {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                LOGGER.warn("[Search] Cannot rebuild - engine not initialized");
                return 0;
            }

            try {
                Connection conn = connectionManager.getConnection();

                // Clear existing index
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM mailbox_messages_fts")) {
                    stmt.executeUpdate();
                }

                // Rebuild from main messages table
                try (PreparedStatement stmt = conn.prepareStatement("""
                    INSERT INTO mailbox_messages_fts
                    (message_id, recipient_uuid, sender_name, subject, body, message_type, sent_at, read_at)
                    SELECT
                        id::VARCHAR,
                        recipient_uuid::VARCHAR,
                        sender_name,
                        subject,
                        body,
                        message_type,
                        sent_at,
                        read_at
                    FROM mailbox_messages
                    WHERE deleted_at IS NULL
                    """)) {
                    int count = stmt.executeUpdate();
                    LOGGER.info("[Search] Rebuilt index with {} messages", count);
                    return count;
                }

            } catch (SQLException e) {
                LOGGER.error("[Search] Failed to rebuild index", e);
                return 0;
            }
        }, executor);
    }

    /**
     * Get search index statistics.
     */
    public CompletableFuture<SearchStats> getStats() {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                return new SearchStats(0, 0, false);
            }

            try {
                Connection conn = connectionManager.getConnection();

                int totalIndexed = 0;
                int uniquePlayers = 0;

                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM mailbox_messages_fts")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            totalIndexed = rs.getInt(1);
                        }
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(DISTINCT recipient_uuid) FROM mailbox_messages_fts")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            uniquePlayers = rs.getInt(1);
                        }
                    }
                }

                return new SearchStats(totalIndexed, uniquePlayers, true);

            } catch (SQLException e) {
                LOGGER.error("[Search] Failed to get stats", e);
                return new SearchStats(0, 0, false);
            }
        }, executor);
    }

    // ============================================================================
    // SHUTDOWN
    // ============================================================================

    /**
     * Shutdown the search engine.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("[Search] Search engine shut down");
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    private int getTotalCount(Connection conn, SearchQuery query) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM mailbox_messages_fts WHERE recipient_uuid = ? ");

        // Extract nullable fields to local variables for null-safety
        String searchText = query.searchText;
        MessageType msgType = query.messageType;
        String senderName = query.senderName;

        List<Object> countParams = new ArrayList<>();
        countParams.add(query.playerUuid.toString());

        if (searchText != null && !searchText.isBlank()) {
            // Skip the relevance params (first 3) and use the search params
            sql.append("AND (LOWER(subject) LIKE ? OR LOWER(body) LIKE ? OR LOWER(sender_name) LIKE ?) ");
            String searchPattern = "%" + searchText.toLowerCase(Locale.ROOT) + "%";
            countParams.add(searchPattern);
            countParams.add(searchPattern);
            countParams.add(searchPattern);
        }

        if (msgType != null) {
            sql.append("AND message_type = ? ");
            countParams.add(msgType.name());
        }

        if (query.readStatus != null) {
            switch (query.readStatus) {
                case READ -> sql.append("AND read_at IS NOT NULL ");
                case UNREAD -> sql.append("AND read_at IS NULL ");
                case ALL -> {}
            }
        }

        if (query.fromDate != null) {
            sql.append("AND sent_at >= ? ");
            countParams.add(java.sql.Timestamp.from(query.fromDate));
        }
        if (query.toDate != null) {
            sql.append("AND sent_at <= ? ");
            countParams.add(java.sql.Timestamp.from(query.toDate));
        }

        if (senderName != null && !senderName.isBlank()) {
            sql.append("AND LOWER(sender_name) LIKE ? ");
            countParams.add("%" + senderName.toLowerCase(Locale.ROOT) + "%");
        }

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < countParams.size(); i++) {
                stmt.setObject(i + 1, countParams.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Nullable
    private String createHighlight(@Nullable String text, @Nullable String searchText) {
        if (text == null || searchText == null) return null;

        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerSearch = searchText.toLowerCase(Locale.ROOT);
        int index = lowerText.indexOf(lowerSearch);

        if (index == -1) return null;

        // Create a snippet around the match
        int snippetStart = Math.max(0, index - 50);
        int snippetEnd = Math.min(text.length(), index + searchText.length() + 50);

        StringBuilder sb = new StringBuilder();
        if (snippetStart > 0) sb.append("...");
        sb.append(text, snippetStart, index);
        sb.append("**").append(text, index, index + searchText.length()).append("**");
        sb.append(text, index + searchText.length(), snippetEnd);
        if (snippetEnd < text.length()) sb.append("...");

        return sb.toString();
    }

    // ============================================================================
    // TYPES
    // ============================================================================

    /**
     * Search query parameters.
     */
    public static final class SearchQuery {
        final UUID playerUuid;
        @Nullable final String searchText;
        @Nullable final MessageType messageType;
        @Nullable final ReadStatus readStatus;
        @Nullable final Instant fromDate;
        @Nullable final Instant toDate;
        @Nullable final String senderName;
        final SortBy sortBy;
        final int page;
        final int pageSize;

        private SearchQuery(Builder builder) {
            this.playerUuid = Objects.requireNonNull(builder.playerUuid, "playerUuid");
            this.searchText = builder.searchText;
            this.messageType = builder.messageType;
            this.readStatus = builder.readStatus;
            this.fromDate = builder.fromDate;
            this.toDate = builder.toDate;
            this.senderName = builder.senderName;
            this.sortBy = builder.sortBy;
            this.page = builder.page;
            this.pageSize = builder.pageSize;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            @Nullable private UUID playerUuid;
            @Nullable private String searchText;
            @Nullable private MessageType messageType;
            @Nullable private ReadStatus readStatus;
            @Nullable private Instant fromDate;
            @Nullable private Instant toDate;
            @Nullable private String senderName;
            private SortBy sortBy = SortBy.DATE_DESC;
            private int page = 0;
            private int pageSize = DEFAULT_PAGE_SIZE;

            public Builder playerUuid(UUID playerUuid) {
                this.playerUuid = playerUuid;
                return this;
            }

            public Builder searchText(@Nullable String searchText) {
                this.searchText = searchText;
                return this;
            }

            public Builder messageType(@Nullable MessageType messageType) {
                this.messageType = messageType;
                return this;
            }

            public Builder readStatus(@Nullable ReadStatus readStatus) {
                this.readStatus = readStatus;
                return this;
            }

            public Builder fromDate(@Nullable Instant fromDate) {
                this.fromDate = fromDate;
                return this;
            }

            public Builder toDate(@Nullable Instant toDate) {
                this.toDate = toDate;
                return this;
            }

            public Builder senderName(@Nullable String senderName) {
                this.senderName = senderName;
                return this;
            }

            public Builder sortBy(SortBy sortBy) {
                this.sortBy = sortBy;
                return this;
            }

            public Builder page(int page) {
                this.page = Math.max(0, page);
                return this;
            }

            public Builder pageSize(int pageSize) {
                if (pageSize <= 0) {
                    this.pageSize = DEFAULT_PAGE_SIZE;
                } else {
                    this.pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
                }
                return this;
            }

            public SearchQuery build() {
                Objects.requireNonNull(playerUuid, "playerUuid");
                return new SearchQuery(this);
            }
        }
    }

    /**
     * Search result with pagination.
     */
    public record SearchResult(
        List<SearchHit> hits,
        int page,
        int pageSize,
        int totalHits,
        int totalPages,
        @Nullable String query
    ) {
        public static SearchResult empty() {
            return new SearchResult(Collections.emptyList(), 0, DEFAULT_PAGE_SIZE, 0, 0, null);
        }

        public boolean hasMore() {
            return page < totalPages - 1;
        }

        public boolean isEmpty() {
            return hits.isEmpty();
        }
    }

    /**
     * Single search hit.
     */
    public record SearchHit(
        UUID messageId,
        @Nullable String senderName,
        String subject,
        String body,
        MessageType type,
        Instant sentAt,
        @Nullable Instant readAt,
        double relevance,
        @Nullable String highlight
    ) {
        public boolean isRead() {
            return readAt != null;
        }
    }

    /**
     * Search statistics.
     */
    public record SearchStats(
        int totalIndexedMessages,
        int uniquePlayers,
        boolean indexReady
    ) {}

    /**
     * Read status filter.
     */
    public enum ReadStatus {
        READ,
        UNREAD,
        ALL
    }

    /**
     * Sort options.
     */
    public enum SortBy {
        DATE_ASC,
        DATE_DESC,
        SENDER_ASC,
        SENDER_DESC,
        RELEVANCE
    }
}
