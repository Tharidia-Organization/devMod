package com.devmod.mailbox.ticket;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

import net.neoforged.fml.loading.FMLPaths;

import com.devmod.telemetry.duckdb.DuckDBConnectionManager;

/**
 * Repository for ticket persistence using DuckDB.
 */
public final class TicketRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketRepository.class);

    public static final TicketRepository INSTANCE = new TicketRepository();

    @Nullable
    private DuckDBConnectionManager connectionManager;
    @Nullable
    private ExecutorService executor;
    private volatile boolean initialized = false;

    private TicketRepository() {}

    // ============================================================================
    // LIFECYCLE
    // ============================================================================

    public CompletableFuture<Void> initialize() {
        if (initialized) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                Path dbPath = FMLPaths.GAMEDIR.get()
                    .resolve("devmod")
                    .resolve("tickets.duckdb");

                connectionManager = new DuckDBConnectionManager(dbPath);
                executor = Executors.newFixedThreadPool(2, r -> {
                    Thread t = new Thread(r, "TicketRepo");
                    t.setDaemon(true);
                    return t;
                });

                initializeSchema();
                initialized = true;
                LOGGER.info("[TicketRepo] Initialized with database: {}", dbPath);
            } catch (Exception e) {
                LOGGER.error("[TicketRepo] Failed to initialize", e);
                throw new RuntimeException("Failed to initialize ticket repository", e);
            }
        });
    }

    private void initializeSchema() throws SQLException {
        DuckDBConnectionManager connMgr = connectionManager;
        if (connMgr == null) {
            throw new SQLException("ConnectionManager not initialized");
        }
        Connection conn = connMgr.getConnectionUnchecked();

        // Create tickets table
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tickets (
                    id VARCHAR PRIMARY KEY,
                    reporter_uuid VARCHAR NOT NULL,
                    reporter_name VARCHAR,
                    reported_uuid VARCHAR,
                    reported_name VARCHAR,
                    category VARCHAR NOT NULL,
                    priority VARCHAR DEFAULT 'NORMAL',
                    status VARCHAR DEFAULT 'OPEN',
                    subject VARCHAR NOT NULL,
                    description VARCHAR,
                    assigned_to VARCHAR,
                    assigned_to_name VARCHAR,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP,
                    resolved_at TIMESTAMP,
                    resolution_notes VARCHAR
                )
                """);

            // Create ticket_comments table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ticket_comments (
                    id VARCHAR PRIMARY KEY,
                    ticket_id VARCHAR NOT NULL,
                    author_uuid VARCHAR,
                    author_name VARCHAR,
                    content VARCHAR NOT NULL,
                    is_internal BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP NOT NULL
                )
                """);

            // Create indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_reporter ON tickets(reporter_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_assigned ON tickets(assigned_to)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_comments_ticket ON ticket_comments(ticket_id)");
        }

        LOGGER.info("[TicketRepo] Schema initialized");
    }

    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            initialized = false;
            ExecutorService exec = executor;
            if (exec != null) {
                exec.shutdown();
                executor = null;
            }
            DuckDBConnectionManager connMgr = connectionManager;
            if (connMgr != null) {
                connMgr.shutdown();
                connectionManager = null;
            }
            LOGGER.info("[TicketRepo] Shutdown complete");
        });
    }

    // ============================================================================
    // TICKET OPERATIONS
    // ============================================================================

    public CompletableFuture<Ticket> saveTicket(Ticket ticket) {
        ExecutorService exec = executor;
        if (exec == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Repository not initialized"));
        }
        return CompletableFuture.supplyAsync(() -> {
            DuckDBConnectionManager connMgr = connectionManager;
            if (connMgr == null) {
                throw new IllegalStateException("ConnectionManager not initialized");
            }
            try { Connection conn = connMgr.getConnectionUnchecked();
                // Delete existing if exists (upsert)
                try (PreparedStatement delStmt = conn.prepareStatement("DELETE FROM tickets WHERE id = ?")) {
                    delStmt.setString(1, ticket.id().toString());
                    delStmt.executeUpdate();
                }

                String sql = """
                    INSERT INTO tickets (
                        id, reporter_uuid, reporter_name, reported_uuid, reported_name,
                        category, priority, status, subject, description,
                        assigned_to, assigned_to_name, created_at, updated_at, resolved_at, resolution_notes
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, ticket.id().toString());
                    stmt.setString(2, ticket.reporterUuid().toString());
                    stmt.setString(3, ticket.reporterName());
                    UUID reportedUuid = ticket.reportedUuid();
                    setNullableString(stmt, 4, reportedUuid != null ? reportedUuid.toString() : null);
                    stmt.setString(5, ticket.reportedName());
                    stmt.setString(6, ticket.category().name());
                    stmt.setString(7, ticket.priority().name());
                    stmt.setString(8, ticket.status().name());
                    stmt.setString(9, ticket.subject());
                    stmt.setString(10, ticket.description());
                    UUID assignedTo = ticket.assignedTo();
                    setNullableString(stmt, 11, assignedTo != null ? assignedTo.toString() : null);
                    stmt.setString(12, ticket.assignedToName());
                    stmt.setTimestamp(13, Timestamp.from(ticket.createdAt()));
                    setNullableTimestamp(stmt, 14, ticket.updatedAt());
                    setNullableTimestamp(stmt, 15, ticket.resolvedAt());
                    stmt.setString(16, ticket.resolutionNotes());

                    stmt.executeUpdate();
                }

                LOGGER.debug("[TicketRepo] Saved ticket {}", ticket.id());
                return ticket;
            } catch (SQLException e) {
                LOGGER.error("[TicketRepo] Failed to save ticket", e);
                throw new RuntimeException("Failed to save ticket", e);
            }
        }, exec);
    }

    public CompletableFuture<Optional<Ticket>> getTicket(UUID ticketId) {
        ExecutorService exec = executor;
        if (exec == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Repository not initialized"));
        }
        return CompletableFuture.supplyAsync(() -> {
            DuckDBConnectionManager connMgr = connectionManager;
            if (connMgr == null) {
                throw new IllegalStateException("ConnectionManager not initialized");
            }
            try { Connection conn = connMgr.getConnectionUnchecked();
                String sql = "SELECT * FROM tickets WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, ticketId.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return Optional.of(mapTicket(rs));
                        }
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                LOGGER.error("[TicketRepo] Failed to get ticket", e);
                throw new RuntimeException("Failed to get ticket", e);
            }
        }, exec);
    }

    public CompletableFuture<List<Ticket>> findTickets(TicketQuery query) {
        ExecutorService exec = executor;
        if (exec == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Repository not initialized"));
        }
        return CompletableFuture.supplyAsync(() -> {
            DuckDBConnectionManager connMgr = connectionManager;
            if (connMgr == null) {
                throw new IllegalStateException("ConnectionManager not initialized");
            }
            try { Connection conn = connMgr.getConnectionUnchecked();
                StringBuilder sql = new StringBuilder("SELECT * FROM tickets WHERE 1=1");
                List<Object> params = new ArrayList<>();

                UUID reporterUuid = query.reporterUuid();
                if (reporterUuid != null) {
                    sql.append(" AND reporter_uuid = ?");
                    params.add(reporterUuid.toString());
                }
                TicketStatus status = query.status();
                if (status != null) {
                    sql.append(" AND status = ?");
                    params.add(status.name());
                }
                TicketCategory category = query.category();
                if (category != null) {
                    sql.append(" AND category = ?");
                    params.add(category.name());
                }
                TicketPriority priority = query.priority();
                if (priority != null) {
                    sql.append(" AND priority = ?");
                    params.add(priority.name());
                }
                UUID assignedToUuid = query.assignedTo();
                if (assignedToUuid != null) {
                    sql.append(" AND assigned_to = ?");
                    params.add(assignedToUuid.toString());
                }
                if (query.unassignedOnly()) {
                    sql.append(" AND assigned_to IS NULL");
                }

                sql.append(" ORDER BY created_at DESC");
                sql.append(" LIMIT ? OFFSET ?");
                params.add(query.limit());
                params.add(query.offset());

                try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        stmt.setObject(i + 1, params.get(i));
                    }

                    List<Ticket> tickets = new ArrayList<>();
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            tickets.add(mapTicket(rs));
                        }
                    }
                    return tickets;
                }
            } catch (SQLException e) {
                LOGGER.error("[TicketRepo] Failed to find tickets", e);
                throw new RuntimeException("Failed to find tickets", e);
            }
        }, exec);
    }

    public CompletableFuture<Integer> countTickets(TicketQuery query) {
        ExecutorService exec = executor;
        if (exec == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Repository not initialized"));
        }
        return CompletableFuture.supplyAsync(() -> {
            DuckDBConnectionManager connMgr = connectionManager;
            if (connMgr == null) {
                throw new IllegalStateException("ConnectionManager not initialized");
            }
            try { Connection conn = connMgr.getConnectionUnchecked();
                StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM tickets WHERE 1=1");
                List<Object> params = new ArrayList<>();

                TicketStatus status = query.status();
                if (status != null) {
                    sql.append(" AND status = ?");
                    params.add(status.name());
                }
                TicketCategory category = query.category();
                if (category != null) {
                    sql.append(" AND category = ?");
                    params.add(category.name());
                }

                try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        stmt.setObject(i + 1, params.get(i));
                    }
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                    }
                }
                return 0;
            } catch (SQLException e) {
                LOGGER.error("[TicketRepo] Failed to count tickets", e);
                throw new RuntimeException("Failed to count tickets", e);
            }
        }, exec);
    }

    // ============================================================================
    // COMMENT OPERATIONS
    // ============================================================================

    public CompletableFuture<TicketComment> saveComment(TicketComment comment) {
        ExecutorService exec = executor;
        if (exec == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Repository not initialized"));
        }
        return CompletableFuture.supplyAsync(() -> {
            DuckDBConnectionManager connMgr = connectionManager;
            if (connMgr == null) {
                throw new IllegalStateException("ConnectionManager not initialized");
            }
            try { Connection conn = connMgr.getConnectionUnchecked();
                String sql = """
                    INSERT INTO ticket_comments (id, ticket_id, author_uuid, author_name, content, is_internal, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, comment.id().toString());
                    stmt.setString(2, comment.ticketId().toString());
                    UUID authorUuid = comment.authorUuid();
                    setNullableString(stmt, 3, authorUuid != null ? authorUuid.toString() : null);
                    stmt.setString(4, comment.authorName());
                    stmt.setString(5, comment.content());
                    stmt.setBoolean(6, comment.isInternal());
                    stmt.setTimestamp(7, Timestamp.from(comment.createdAt()));

                    stmt.executeUpdate();
                }

                LOGGER.debug("[TicketRepo] Saved comment {} for ticket {}", comment.id(), comment.ticketId());
                return comment;
            } catch (SQLException e) {
                LOGGER.error("[TicketRepo] Failed to save comment", e);
                throw new RuntimeException("Failed to save comment", e);
            }
        }, exec);
    }

    public CompletableFuture<List<TicketComment>> getComments(UUID ticketId, boolean includeInternal) {
        ExecutorService exec = executor;
        if (exec == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Repository not initialized"));
        }
        return CompletableFuture.supplyAsync(() -> {
            DuckDBConnectionManager connMgr = connectionManager;
            if (connMgr == null) {
                throw new IllegalStateException("ConnectionManager not initialized");
            }
            try { Connection conn = connMgr.getConnectionUnchecked();
                String sql = includeInternal
                    ? "SELECT * FROM ticket_comments WHERE ticket_id = ? ORDER BY created_at ASC"
                    : "SELECT * FROM ticket_comments WHERE ticket_id = ? AND is_internal = FALSE ORDER BY created_at ASC";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, ticketId.toString());

                    List<TicketComment> comments = new ArrayList<>();
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            comments.add(mapComment(rs));
                        }
                    }
                    return comments;
                }
            } catch (SQLException e) {
                LOGGER.error("[TicketRepo] Failed to get comments", e);
                throw new RuntimeException("Failed to get comments", e);
            }
        }, exec);
    }

    // ============================================================================
    // STATISTICS
    // ============================================================================

    public CompletableFuture<TicketStats> getStats() {
        ExecutorService exec = executor;
        if (exec == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Repository not initialized"));
        }
        return CompletableFuture.supplyAsync(() -> {
            DuckDBConnectionManager connMgr = connectionManager;
            if (connMgr == null) {
                throw new IllegalStateException("ConnectionManager not initialized");
            }
            try { Connection conn = connMgr.getConnectionUnchecked();
                int total = 0, open = 0, assigned = 0, inProgress = 0, resolved = 0, closed = 0;

                long avgResolutionMs = 0;
                try (Statement stmt = conn.createStatement()) {
                    try (ResultSet rs = stmt.executeQuery(
                            "SELECT status, COUNT(*) as cnt FROM tickets GROUP BY status")) {
                        while (rs.next()) {
                            String status = rs.getString("status");
                            int cnt = rs.getInt("cnt");
                            total += cnt;
                            switch (TicketStatus.valueOf(status)) {
                                case OPEN -> open = cnt;
                                case ASSIGNED -> assigned = cnt;
                                case IN_PROGRESS -> inProgress = cnt;
                                case RESOLVED -> resolved = cnt;
                                case CLOSED -> closed = cnt;
                            }
                        }
                    }

                    // Get average resolution time (for resolved tickets)
                    try (ResultSet rs = stmt.executeQuery("""
                            SELECT AVG(EPOCH(resolved_at) - EPOCH(created_at)) * 1000 as avg_time
                            FROM tickets WHERE resolved_at IS NOT NULL
                            """)) {
                        if (rs.next()) {
                            double avgTime = rs.getDouble("avg_time");
                            if (!rs.wasNull()) {
                                avgResolutionMs = (long) avgTime;
                            }
                        }
                    }
                }

                return new TicketStats(total, open, assigned, inProgress, resolved, closed, avgResolutionMs);
            } catch (SQLException e) {
                LOGGER.error("[TicketRepo] Failed to get stats", e);
                throw new RuntimeException("Failed to get stats", e);
            }
        }, exec);
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private Ticket mapTicket(ResultSet rs) throws SQLException {
        UUID id = getRequiredUuid(rs, "id");
        UUID reporterUuid = getRequiredUuid(rs, "reporter_uuid");
        String reporterName = rs.getString("reporter_name");
        UUID reportedUuid = getNullableUuid(rs, "reported_uuid");
        String reportedName = rs.getString("reported_name");
        TicketCategory category = TicketCategory.valueOf(getRequiredString(rs, "category"));
        TicketPriority priority = TicketPriority.valueOf(getRequiredString(rs, "priority"));
        TicketStatus status = TicketStatus.valueOf(getRequiredString(rs, "status"));
        String subject = getRequiredString(rs, "subject");
        String description = rs.getString("description");
        UUID assignedTo = getNullableUuid(rs, "assigned_to");
        String assignedToName = rs.getString("assigned_to_name");
        Instant createdAt = getRequiredInstant(rs, "created_at");
        @Nullable Instant updatedAt = getNullableInstant(rs, "updated_at");
        @Nullable Instant resolvedAt = getNullableInstant(rs, "resolved_at");
        String resolutionNotes = rs.getString("resolution_notes");

        return new Ticket(
            id,
            reporterUuid,
            reporterName,
            reportedUuid,
            reportedName,
            category,
            priority,
            status,
            subject,
            description,
            assignedTo,
            assignedToName,
            createdAt,
            updatedAt,
            resolvedAt,
            resolutionNotes
        );
    }

    private TicketComment mapComment(ResultSet rs) throws SQLException {
        UUID id = getRequiredUuid(rs, "id");
        UUID ticketId = getRequiredUuid(rs, "ticket_id");
        UUID authorUuid = getNullableUuid(rs, "author_uuid");
        String authorName = rs.getString("author_name");
        String content = getRequiredString(rs, "content");
        Instant createdAt = getRequiredInstant(rs, "created_at");
        return new TicketComment(
            id,
            ticketId,
            authorUuid,
            authorName,
            content,
            rs.getBoolean("is_internal"),
            createdAt
        );
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

    private String getRequiredString(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null) {
            throw new SQLException("Required column '" + column + "' was null");
        }
        return value;
    }

    private UUID getRequiredUuid(ResultSet rs, String column) throws SQLException {
        String value = getRequiredString(rs, column);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid UUID in column '" + column + "'", e);
        }
    }

    @Nullable
    private UUID getNullableUuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid UUID in column '" + column + "'", e);
        }
    }

    private Instant getRequiredInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        if (ts == null) {
            throw new SQLException("Required timestamp '" + column + "' was null");
        }
        return ts.toInstant();
    }

    @Nullable
    private Instant getNullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }

    // ============================================================================
    // QUERY TYPES
    // ============================================================================

    public record TicketQuery(
        @Nullable UUID reporterUuid,
        @Nullable TicketStatus status,
        @Nullable TicketCategory category,
        @Nullable TicketPriority priority,
        @Nullable UUID assignedTo,
        boolean unassignedOnly,
        int limit,
        int offset
    ) {
        public static TicketQuery all() {
            return new TicketQuery(null, null, null, null, null, false, 100, 0);
        }

        public static TicketQuery open() {
            return new TicketQuery(null, TicketStatus.OPEN, null, null, null, false, 100, 0);
        }

        public static TicketQuery forReporter(UUID reporterUuid) {
            return new TicketQuery(reporterUuid, null, null, null, null, false, 100, 0);
        }

        public static TicketQuery forAssignee(UUID assigneeUuid) {
            return new TicketQuery(null, null, null, null, assigneeUuid, false, 100, 0);
        }
    }

    public record TicketStats(
        int total,
        int open,
        int assigned,
        int inProgress,
        int resolved,
        int closed,
        long avgResolutionTimeMs
    ) {}
}
