package com.devmod.mailbox.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.devmod.mailbox.MailboxMessage;
import com.devmod.mailbox.news.NewsArticle;

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
     * Soft-delete a message.
     *
     * @param messageId the message UUID
     * @return future completing with success status
     */
    CompletableFuture<Boolean> deleteMessage(UUID messageId);

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
}
