package com.devmod.mailbox.moderation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.persistence.MailboxRepository;

/**
 * Audit log for admin actions on the mailbox system.
 * Tracks critical operations like broadcasts, deletions, and config changes.
 */
public final class AdminAuditLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminAuditLog.class);

    public static final AdminAuditLog INSTANCE = new AdminAuditLog();

    /** Maximum entries to keep in memory. */
    private static final int MAX_IN_MEMORY_ENTRIES = 1000;

    /** In-memory audit log (newest first). */
    private final ConcurrentLinkedDeque<AuditEntry> entries = new ConcurrentLinkedDeque<>();

    @Nullable
    private volatile MailboxRepository repository;

    private AdminAuditLog() {}

    /**
     * Initialize with repository for persistence.
     */
    public void initialize(@Nullable MailboxRepository repo) {
        this.repository = repo;
        LOGGER.info("[AdminAudit] Admin audit log initialized");
    }

    /**
     * Shutdown the audit log.
     */
    public void shutdown() {
        this.repository = null;
        LOGGER.info("[AdminAudit] Admin audit log shutdown");
    }

    // ============================================================================
    // LOGGING OPERATIONS
    // ============================================================================

    /**
     * Log an admin action.
     */
    public CompletableFuture<Void> log(AuditEntry entry) {
        // Add to in-memory log
        entries.addFirst(entry);

        // Trim if too large
        while (entries.size() > MAX_IN_MEMORY_ENTRIES) {
            entries.removeLast();
        }

        LOGGER.info("[AdminAudit] {} by {} on {}: {}",
            entry.action(), entry.actorName(), entry.targetType(), entry.targetId());

        // Persist if repository available
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(null);
        }
        return repo.saveAdminAudit(entry).thenAccept(saved -> {});
    }

    /**
     * Log a message broadcast action.
     */
    public CompletableFuture<Void> logBroadcast(
            @Nullable UUID actorUuid, String actorName, int recipientCount, String subject) {
        return log(AuditEntry.builder()
            .action(Action.BROADCAST)
            .actorUuid(actorUuid)
            .actorName(actorName)
            .targetType("broadcast")
            .details("Sent to " + recipientCount + " recipients: " + truncate(subject, 50))
            .build());
    }

    /**
     * Log a message deletion action.
     */
    public CompletableFuture<Void> logMessageDelete(
            @Nullable UUID actorUuid, String actorName, UUID messageId, UUID recipientUuid) {
        return log(AuditEntry.builder()
            .action(Action.MESSAGE_DELETE)
            .actorUuid(actorUuid)
            .actorName(actorName)
            .targetType("message")
            .targetId(messageId.toString())
            .details("Deleted message for user " + recipientUuid)
            .build());
    }

    /**
     * Log a news article creation.
     */
    public CompletableFuture<Void> logNewsCreate(
            @Nullable UUID actorUuid, String actorName, UUID articleId, String title) {
        return log(AuditEntry.builder()
            .action(Action.NEWS_CREATE)
            .actorUuid(actorUuid)
            .actorName(actorName)
            .targetType("news")
            .targetId(articleId.toString())
            .details("Created: " + truncate(title, 50))
            .build());
    }

    /**
     * Log a news article deletion.
     */
    public CompletableFuture<Void> logNewsDelete(
            @Nullable UUID actorUuid, String actorName, UUID articleId) {
        return log(AuditEntry.builder()
            .action(Action.NEWS_DELETE)
            .actorUuid(actorUuid)
            .actorName(actorName)
            .targetType("news")
            .targetId(articleId.toString())
            .build());
    }

    /**
     * Log a configuration change.
     */
    public CompletableFuture<Void> logConfigChange(
            @Nullable UUID actorUuid, String actorName, String setting, String oldValue, String newValue) {
        return log(AuditEntry.builder()
            .action(Action.CONFIG_CHANGE)
            .actorUuid(actorUuid)
            .actorName(actorName)
            .targetType("config")
            .targetId(setting)
            .details(oldValue + " -> " + newValue)
            .build());
    }

    /**
     * Log a user block action.
     */
    public CompletableFuture<Void> logUserBlock(
            @Nullable UUID actorUuid, String actorName, UUID targetUserUuid, String reason) {
        return log(AuditEntry.builder()
            .action(Action.USER_BLOCK)
            .actorUuid(actorUuid)
            .actorName(actorName)
            .targetType("user")
            .targetId(targetUserUuid.toString())
            .details(reason)
            .build());
    }

    /**
     * Log a task assignment.
     */
    public CompletableFuture<Void> logTaskAssign(
            @Nullable UUID actorUuid, String actorName, UUID taskId, UUID assigneeUuid) {
        return log(AuditEntry.builder()
            .action(Action.TASK_ASSIGN)
            .actorUuid(actorUuid)
            .actorName(actorName)
            .targetType("task")
            .targetId(taskId.toString())
            .details("Assigned to " + assigneeUuid)
            .build());
    }

    // ============================================================================
    // QUERY OPERATIONS
    // ============================================================================

    /**
     * Get recent audit entries (from memory).
     */
    public List<AuditEntry> getRecentEntries(int limit) {
        return entries.stream()
            .limit(Math.min(limit, MAX_IN_MEMORY_ENTRIES))
            .toList();
    }

    /**
     * Fetch recent audit entries, preferring persisted data when available.
     */
    public CompletableFuture<List<AuditEntry>> fetchRecentEntries(int limit) {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(getRecentEntries(limit));
        }
        return repo.getRecentAdminAudits(limit);
    }

    /**
     * Get entries by action type.
     */
    public List<AuditEntry> getEntriesByAction(Action action, int limit) {
        return entries.stream()
            .filter(e -> e.action() == action)
            .limit(limit)
            .toList();
    }

    /**
     * Get entries by actor.
     */
    public List<AuditEntry> getEntriesByActor(UUID actorUuid, int limit) {
        return entries.stream()
            .filter(e -> actorUuid.equals(e.actorUuid()))
            .limit(limit)
            .toList();
    }

    /**
     * Get total entry count (in memory).
     */
    public int getEntryCount() {
        return entries.size();
    }

    /**
     * Clear all in-memory entries.
     */
    public void clear() {
        entries.clear();
        LOGGER.info("[AdminAudit] Audit log cleared");
    }

    // ============================================================================
    // HELPER
    // ============================================================================

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    // ============================================================================
    // TYPES
    // ============================================================================

    /**
     * Admin action types.
     */
    public enum Action {
        BROADCAST,
        MESSAGE_SEND,
        MESSAGE_FLAG,
        MESSAGE_DELETE,
        MESSAGE_FORCE_DELETE,
        NEWS_CREATE,
        NEWS_UPDATE,
        NEWS_DELETE,
        TASK_CREATE,
        TASK_ASSIGN,
        TASK_UPDATE,
        TASK_DELETE,
        CONFIG_CHANGE,
        USER_BLOCK,
        USER_UNBLOCK,
        USER_ACCESS_UPDATE,
        LOGIN,
        LOGOUT
    }

    /**
     * Audit entry record.
     */
    public record AuditEntry(
        UUID id,
        Action action,
        @Nullable UUID actorUuid,
        String actorName,
        String targetType,
        @Nullable String targetId,
        @Nullable String details,
        long timestamp
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private @Nullable UUID id;
            private @Nullable Action action;
            private @Nullable UUID actorUuid;
            private @Nullable String actorName;
            private @Nullable String targetType;
            private @Nullable String targetId;
            private @Nullable String details;
            private long timestamp;

            public Builder id(UUID id) {
                this.id = id;
                return this;
            }

            public Builder action(Action action) {
                this.action = action;
                return this;
            }

            public Builder actorUuid(@Nullable UUID actorUuid) {
                this.actorUuid = actorUuid;
                return this;
            }

            public Builder actorName(String actorName) {
                this.actorName = actorName;
                return this;
            }

            public Builder targetType(String targetType) {
                this.targetType = targetType;
                return this;
            }

            public Builder targetId(@Nullable String targetId) {
                this.targetId = targetId;
                return this;
            }

            public Builder details(@Nullable String details) {
                this.details = details;
                return this;
            }

            public Builder timestamp(long timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public AuditEntry build() {
                return new AuditEntry(
                    id != null ? id : UUID.randomUUID(),
                    action != null ? action : Action.CONFIG_CHANGE,
                    actorUuid,
                    actorName != null ? actorName : "Unknown",
                    targetType != null ? targetType : "unknown",
                    targetId,
                    details,
                    timestamp > 0 ? timestamp : System.currentTimeMillis()
                );
            }
        }
    }
}
