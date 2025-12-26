package com.devmod.mailbox.news;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.MailboxConfig;
import com.devmod.mailbox.persistence.MailboxRepository;

/**
 * Manager for the news/announcements system.
 *
 * Handles creating, publishing, and retrieving news articles for players.
 */
public class NewsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NewsManager.class);

    // ============================================================================
    // SINGLETON
    // ============================================================================

    public static final NewsManager INSTANCE = new NewsManager();

    private NewsManager() {}

    // ============================================================================
    // STATE
    // ============================================================================

    @Nullable
    private MailboxRepository repository;

    /** Callback for notifying clients of new news */
    @Nullable
    private NewNewsCallback newNewsCallback;

    // ============================================================================
    // INITIALIZATION
    // ============================================================================

    /**
     * Initialize the news manager with a repository.
     * Called by MailboxManager during initialization.
     */
    public void initialize(MailboxRepository repository) {
        this.repository = repository;
        LOGGER.info("[News] News manager initialized");
    }

    /**
     * Shutdown the news manager.
     */
    public void shutdown() {
        this.repository = null;
        LOGGER.info("[News] News manager shutdown");
    }

    // ============================================================================
    // NEWS OPERATIONS
    // ============================================================================

    /**
     * Create and publish a news article immediately.
     *
     * @param title the article title
     * @param content the article content
     * @param category the article category
     * @param authorName the author's name
     * @return future completing with the created article
     */
    public CompletableFuture<NewsArticle> publishNews(
            String title,
            String content,
            NewsCategory category,
            @Nullable String authorName
    ) {
        return publishNews(title, content, category, authorName, null, null, 0);
    }

    /**
     * Create a news article with scheduling options.
     *
     * @param title the article title
     * @param content the article content
     * @param category the article category
     * @param authorName the author's name
     * @param publishAt when to publish (null for immediately)
     * @param expiresIn how long until expiration (null for default)
     * @param priority priority level (higher = more prominent)
     * @return future completing with the created article
     */
    public CompletableFuture<NewsArticle> publishNews(
            String title,
            String content,
            NewsCategory category,
            @Nullable String authorName,
            @Nullable Instant publishAt,
            @Nullable Duration expiresIn,
            int priority
    ) {
        if (repository == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("News manager not initialized"));
        }

        Instant publishTime = publishAt != null ? publishAt : Instant.now();
        Instant expiresAt = expiresIn != null
            ? Instant.now().plus(expiresIn)
            : Instant.now().plus(MailboxConfig.INSTANCE.getDefaultNewsTtl());

        NewsArticle article = NewsArticle.builder()
            .title(title)
            .content(content)
            .category(category)
            .authorName(authorName)
            .publishedAt(publishTime)
            .expiresAt(expiresAt)
            .priority(priority)
            .active(true)
            .build();

        return repository.saveNewsArticle(article).thenApply(saved -> {
            LOGGER.info("[News] Published article: {} (category: {})", title, category.getId());

            // Notify if published immediately
            if (!Instant.now().isBefore(publishTime)) {
                notifyNewNews(saved);
            }

            return saved;
        });
    }

    /**
     * Create a draft news article (not published).
     *
     * @param title the article title
     * @param content the article content
     * @param category the article category
     * @param authorName the author's name
     * @return future completing with the created draft
     */
    public CompletableFuture<NewsArticle> createDraft(
            String title,
            String content,
            NewsCategory category,
            @Nullable String authorName
    ) {
        if (repository == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("News manager not initialized"));
        }

        NewsArticle article = NewsArticle.builder()
            .title(title)
            .content(content)
            .category(category)
            .authorName(authorName)
            .publishedAt(null) // Not published
            .expiresAt(null)
            .priority(0)
            .active(false)
            .build();

        return repository.saveNewsArticle(article).thenApply(saved -> {
            LOGGER.info("[News] Created draft article: {}", title);
            return saved;
        });
    }

    /**
     * Get a news article by ID.
     *
     * @param articleId the article UUID
     * @return future completing with the article if found
     */
    public CompletableFuture<Optional<NewsArticle>> getArticle(UUID articleId) {
        if (repository == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return repository.getNewsArticle(articleId);
    }

    /**
     * Get all active news articles visible to players.
     *
     * @return future completing with the list of articles
     */
    public CompletableFuture<List<NewsArticle>> getActiveNews() {
        if (repository == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return repository.getActiveNews(MailboxConfig.INSTANCE.getMaxNewsArticles());
    }

    /**
     * Get all news articles (including inactive) for admin.
     *
     * @return future completing with the list of articles
     */
    public CompletableFuture<List<NewsArticle>> getAllNews() {
        if (repository == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return repository.getAllNews(200); // Higher limit for admin view
    }

    /**
     * Update a news article.
     *
     * @param article the updated article
     * @return future completing with success status
     */
    public CompletableFuture<Boolean> updateArticle(NewsArticle article) {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(false);
        }
        return repo.updateNewsArticle(article).thenApply(success -> {
            if (success) {
                LOGGER.info("[News] Updated article: {}", article.id());
            }
            return success;
        });
    }

    /**
     * Delete a news article.
     *
     * @param articleId the article UUID
     * @return future completing with success status
     */
    public CompletableFuture<Boolean> deleteArticle(UUID articleId) {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(false);
        }
        return repo.deleteNewsArticle(articleId).thenApply(success -> {
            if (success) {
                LOGGER.info("[News] Deleted article: {}", articleId);
            }
            return success;
        });
    }

    /**
     * Deactivate a news article (soft delete).
     *
     * @param articleId the article UUID
     * @return future completing with success status
     */
    public CompletableFuture<Boolean> deactivateArticle(UUID articleId) {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(false);
        }

        return repo.getNewsArticle(articleId).thenCompose(opt -> {
            if (opt.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            return repo.updateNewsArticle(opt.get().withDeactivated());
        });
    }

    // ============================================================================
    // PLAYER READ STATUS
    // ============================================================================

    /**
     * Check if a player has read a news article.
     *
     * @param playerUuid the player's UUID
     * @param articleId the article UUID
     * @return future completing with read status
     */
    public CompletableFuture<Boolean> hasPlayerReadNews(UUID playerUuid, UUID articleId) {
        if (repository == null) {
            return CompletableFuture.completedFuture(false);
        }
        return repository.hasReadNews(playerUuid, articleId);
    }

    /**
     * Mark a news article as read by a player.
     *
     * @param playerUuid the player's UUID
     * @param articleId the article UUID
     * @return future completing with success status
     */
    public CompletableFuture<Boolean> markAsRead(UUID playerUuid, UUID articleId) {
        if (repository == null) {
            return CompletableFuture.completedFuture(false);
        }
        return repository.markNewsAsRead(playerUuid, articleId);
    }

    /**
     * Get unread news count for a player.
     *
     * @param playerUuid the player's UUID
     * @return future completing with the unread count
     */
    public CompletableFuture<Integer> getUnreadCount(UUID playerUuid) {
        if (repository == null) {
            return CompletableFuture.completedFuture(0);
        }
        return repository.getUnreadNewsCount(playerUuid);
    }

    // ============================================================================
    // CALLBACKS
    // ============================================================================

    /**
     * Set the callback for new news notifications.
     */
    public void setNewNewsCallback(@Nullable NewNewsCallback callback) {
        this.newNewsCallback = callback;
    }

    private void notifyNewNews(NewsArticle article) {
        NewNewsCallback callback = this.newNewsCallback;
        if (callback != null) {
            try {
                callback.onNewNews(article);
            } catch (Exception e) {
                LOGGER.error("[News] Error in new news callback", e);
            }
        }
    }

    // ============================================================================
    // HELPER TYPES
    // ============================================================================

    /**
     * Callback interface for new news notifications.
     */
    @FunctionalInterface
    public interface NewNewsCallback {
        void onNewNews(NewsArticle article);
    }
}
