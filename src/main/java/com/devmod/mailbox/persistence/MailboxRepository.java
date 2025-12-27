package com.devmod.mailbox.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.devmod.mailbox.MailboxMessage;
import com.devmod.mailbox.moderation.AdminAuditLog;
import com.devmod.mailbox.news.NewsArticle;
import com.devmod.mailbox.task.TaskAuditEntry;
import com.devmod.mailbox.task.TestTask;

/**
 * Repository interface for mailbox persistence operations.
 *
 * All operations are asynchronous to avoid blocking the main game thread.
 */
public interface MailboxRepository {

    // ============================================================================
    // MESSAGE OPERATIONS
    // ============================================================================

    /**
     * Save a new message to the database.
     *
     * @param message the message to save
     * @return future completing with the saved message
     */
    CompletableFuture<MailboxMessage> saveMessage(MailboxMessage message);

    /**
     * Get a message by its ID.
     *
     * @param messageId the message UUID
     * @return future completing with the message if found
     */
    CompletableFuture<Optional<MailboxMessage>> getMessage(UUID messageId);

    /**
     * Get all messages for a player.
     *
     * @param playerUuid the player's UUID
     * @param includeDeleted whether to include soft-deleted messages
     * @return future completing with the list of messages
     */
    CompletableFuture<List<MailboxMessage>> getMessagesForPlayer(UUID playerUuid, boolean includeDeleted);

    /**
     * Get unread message count for a player.
     *
     * @param playerUuid the player's UUID
     * @return future completing with the unread count
     */
    CompletableFuture<Integer> getUnreadCount(UUID playerUuid);

    /**
     * Mark a message as read.
     *
     * @param messageId the message UUID
     * @param readAt the timestamp when read
     * @return future completing with success status
     */
    CompletableFuture<Boolean> markAsRead(UUID messageId, Instant readAt);

    /**
     * Mark a message's attachment as claimed.
     *
     * @param messageId the message UUID
     * @return future completing with success status
     */
    CompletableFuture<Boolean> markAttachmentClaimed(UUID messageId);

    /**
     * Attempt to start an attachment claim (guards against concurrent claims).
     *
     * @param messageId the message UUID
     * @return future completing with true if claim lock acquired
     */
    CompletableFuture<Boolean> startAttachmentClaim(UUID messageId);

    /**
     * Clear an attachment claim lock (for failed claim attempts).
     *
     * @param messageId the message UUID
     * @return future completing with success status
     */
    CompletableFuture<Boolean> clearAttachmentClaim(UUID messageId);

    /**
     * Soft-delete a message (optionally setting a retention expiry).
     *
     * @param messageId the message UUID
     * @param retainUntil optional timestamp to retain before purge
     * @return future completing with success status
     */
    CompletableFuture<Boolean> softDeleteMessage(UUID messageId, @Nullable Instant retainUntil);

    /**
     * Hard-delete a message immediately.
     *
     * @param messageId the message UUID
     * @return future completing with success status
     */
    CompletableFuture<Boolean> hardDeleteMessage(UUID messageId);

    /**
     * Permanently delete expired messages.
     *
     * @param before delete messages that expired before this timestamp
     * @return future completing with the number of deleted messages
     */
    CompletableFuture<Integer> purgeExpiredMessages(Instant before);

    /**
     * Get total message count for a player.
     *
     * @param playerUuid the player's UUID
     * @return future completing with the message count
     */
    CompletableFuture<Integer> getMessageCount(UUID playerUuid);

    /**
     * Get all messages across all users (admin).
     *
     * @param limit maximum number to return
     * @param offset starting offset
     * @return future completing with message list
     */
    CompletableFuture<List<MailboxMessage>> getAllMessages(int limit, int offset);

    /**
     * Get all known user UUIDs that have received messages.
     *
     * @return future completing with list of UUIDs
     */
    CompletableFuture<List<UUID>> getKnownUsers();

    /**
     * Get total message count across all users.
     *
     * @return future completing with count
     */
    CompletableFuture<Integer> getTotalMessageCount();

    /**
     * Get total unread message count across all users.
     *
     * @return future completing with count
     */
    CompletableFuture<Integer> getTotalUnreadCount();

    // ============================================================================
    // NEWS OPERATIONS
    // ============================================================================

    /**
     * Save a news article.
     *
     * @param article the article to save
     * @return future completing with the saved article
     */
    CompletableFuture<NewsArticle> saveNewsArticle(NewsArticle article);

    /**
     * Get a news article by ID.
     *
     * @param articleId the article UUID
     * @return future completing with the article if found
     */
    CompletableFuture<Optional<NewsArticle>> getNewsArticle(UUID articleId);

    /**
     * Get all active and visible news articles.
     *
     * @param limit maximum number of articles to return
     * @return future completing with the list of articles
     */
    CompletableFuture<List<NewsArticle>> getActiveNews(int limit);

    /**
     * Get all news articles (including inactive) for admin.
     *
     * @param limit maximum number of articles to return
     * @return future completing with the list of articles
     */
    CompletableFuture<List<NewsArticle>> getAllNews(int limit);

    /**
     * Update a news article.
     *
     * @param article the updated article
     * @return future completing with success status
     */
    CompletableFuture<Boolean> updateNewsArticle(NewsArticle article);

    /**
     * Delete a news article.
     *
     * @param articleId the article UUID
     * @return future completing with success status
     */
    CompletableFuture<Boolean> deleteNewsArticle(UUID articleId);

    /**
     * Check if a player has read a news article.
     *
     * @param playerUuid the player's UUID
     * @param articleId the article UUID
     * @return future completing with read status
     */
    CompletableFuture<Boolean> hasReadNews(UUID playerUuid, UUID articleId);

    /**
     * Mark a news article as read by a player.
     *
     * @param playerUuid the player's UUID
     * @param articleId the article UUID
     * @return future completing with success status
     */
    CompletableFuture<Boolean> markNewsAsRead(UUID playerUuid, UUID articleId);

    /**
     * Get unread news count for a player.
     *
     * @param playerUuid the player's UUID
     * @return future completing with the unread count
     */
    CompletableFuture<Integer> getUnreadNewsCount(UUID playerUuid);

    // ============================================================================
    // TASK OPERATIONS
    // ============================================================================

    /**
     * Save a new test task.
     *
     * @param task the task to save
     * @return future completing with the saved task
     */
    CompletableFuture<TestTask> saveTask(TestTask task);

    /**
     * Get a task by ID.
     *
     * @param taskId the task UUID
     * @return future completing with the task if found
     */
    CompletableFuture<Optional<TestTask>> getTask(UUID taskId);

    /**
     * Get tasks assigned to a specific user.
     *
     * @param userId the assigned user's UUID
     * @return future completing with the list of tasks
     */
    CompletableFuture<List<TestTask>> getTasksForUser(UUID userId);

    /**
     * Get all tasks with pagination.
     *
     * @param limit maximum number of tasks
     * @param offset starting offset
     * @return future completing with the list of tasks
     */
    CompletableFuture<List<TestTask>> getAllTasks(int limit, int offset);

    /**
     * Update an existing task.
     *
     * @param task the updated task
     * @return future completing with success status
     */
    CompletableFuture<Boolean> updateTask(TestTask task);

    /**
     * Delete a task.
     *
     * @param taskId the task UUID
     * @return future completing with success status
     */
    CompletableFuture<Boolean> deleteTask(UUID taskId);

    // ============================================================================
    // TASK AUDIT OPERATIONS
    // ============================================================================

    /**
     * Save a task audit entry.
     *
     * @param entry the audit entry to save
     * @return future completing with the saved entry
     */
    CompletableFuture<TaskAuditEntry> saveTaskAudit(TaskAuditEntry entry);

    /**
     * Get audit history for a task.
     *
     * @param taskId the task UUID
     * @return future completing with the list of audit entries (newest first)
     */
    CompletableFuture<List<TaskAuditEntry>> getTaskAuditHistory(UUID taskId);

    /**
     * Get recent audit entries across all tasks.
     *
     * @param limit maximum number of entries
     * @return future completing with the list of audit entries (newest first)
     */
    CompletableFuture<List<TaskAuditEntry>> getRecentTaskAudits(int limit);

    // ============================================================================
    // ADMIN AUDIT OPERATIONS
    // ============================================================================

    /**
     * Save an admin audit entry.
     *
     * @param entry the audit entry to save
     * @return future completing with the saved entry
     */
    CompletableFuture<AdminAuditLog.AuditEntry> saveAdminAudit(AdminAuditLog.AuditEntry entry);

    /**
     * Get recent admin audit entries.
     *
     * @param limit maximum number of entries
     * @return future completing with list of entries (newest first)
     */
    CompletableFuture<List<AdminAuditLog.AuditEntry>> getRecentAdminAudits(int limit);

    // ============================================================================
    // LIFECYCLE
    // ============================================================================

    /**
     * Initialize the repository (create tables, etc.).
     *
     * @return future completing when initialization is done
     */
    CompletableFuture<Void> initialize();

    /**
     * Shutdown the repository gracefully.
     *
     * @return future completing when shutdown is done
     */
    CompletableFuture<Void> shutdown();

    // ============================================================================
    // BACKUP/RESTORE
    // ============================================================================

    /**
     * Create a backup of the database.
     *
     * @param backupName optional name for the backup (defaults to timestamp)
     * @return future completing with the backup file path
     */
    default CompletableFuture<String> createBackup(@Nullable String backupName) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Restore from a backup.
     *
     * @param backupName the backup name to restore
     * @return future completing with success status
     */
    default CompletableFuture<Boolean> restoreBackup(String backupName) {
        return CompletableFuture.completedFuture(false);
    }

    /**
     * List available backups.
     *
     * @return future completing with list of backup names
     */
    default CompletableFuture<List<String>> listBackups() {
        return CompletableFuture.completedFuture(List.of());
    }

    /**
     * Delete a backup.
     *
     * @param backupName the backup name to delete
     * @return future completing with success status
     */
    default CompletableFuture<Boolean> deleteBackup(String backupName) {
        return CompletableFuture.completedFuture(false);
    }
}
