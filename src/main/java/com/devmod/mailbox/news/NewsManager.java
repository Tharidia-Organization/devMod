package com.devmod.mailbox.news;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.MailboxConfig;
import com.devmod.mailbox.persistence.MailboxRepository;
import com.devmod.mailbox.webhook.WebhookManager;

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

    private NewsManager() {}

    public static NewsManager getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final NewsManager INSTANCE = new NewsManager();
    }

    // ============================================================================
    // STATE
    // ============================================================================

    @Nullable
    private MailboxRepository repository;

    /** Callback for notifying clients of new news */
    @Nullable
    private NewNewsCallback newNewsCallback;

    // ============================================================================
    // GLOBAL NEWS CACHE
    // ============================================================================

    /** Default cache TTL (5 minutes). */
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);

    /** Cached active news list. */
    private final AtomicReference<CachedNews> cachedActiveNews = new AtomicReference<>();

    /** Lock for cache operations. */
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();

    /** Whether caching is enabled. */
    private volatile boolean cacheEnabled = true;

    /** Custom cache TTL. */
    private volatile Duration cacheTtl = DEFAULT_CACHE_TTL;

    /** Cache holder record. */
    private record CachedNews(List<NewsArticle> articles, Instant cachedAt) {
        boolean isExpired(Duration ttl) {
            if (ttl == null) {
                return true; // Treat as expired if no TTL
            }
            return Instant.now().isAfter(cachedAt.plus(ttl));
        }
    }

    // ============================================================================
    // INITIALIZATION
    // ============================================================================

    /**
     * Initialize the news manager with a repository.
     * Called by MailboxManager during initialization.
     */
    public void initialize(MailboxRepository repository) {
        this.repository = repository;
        invalidateCache();
        LOGGER.info("[News] News manager initialized");
    }

    /**
     * Shutdown the news manager.
     */
    public void shutdown() {
        this.repository = null;
        invalidateCache();
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
        MailboxRepository repo = repository;
        if (repo == null) {
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

        return repo.saveNewsArticle(article).thenApply(saved -> {
            LOGGER.info("[News] Published article: {} (category: {})", title, category.getId());

            // Invalidate cache since news list changed
            invalidateCache();

            // Notify if published immediately
            if (!Instant.now().isBefore(publishTime)) {
                notifyNewNews(saved);

                // Dispatch webhook
                WebhookManager.INSTANCE.dispatchNewsPublished(
                    saved.id(),
                    title,
                    authorName != null ? authorName : "System"
                );
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
        MailboxRepository repo = repository;
        if (repo == null) {
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

        return repo.saveNewsArticle(article).thenApply(saved -> {
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
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return repo.getNewsArticle(articleId);
    }

    /**
     * Get all active news articles visible to players.
     * Uses caching to reduce database queries.
     *
     * @return future completing with the list of articles
     */
    public CompletableFuture<List<NewsArticle>> getActiveNews() {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        // Check cache first (if enabled)
        if (cacheEnabled) {
            cacheLock.readLock().lock();
            try {
                CachedNews cached = cachedActiveNews.get();
                if (cached != null && !cached.isExpired(cacheTtl)) {
                    LOGGER.debug("[News] Returning {} cached articles", cached.articles().size());
                    return CompletableFuture.completedFuture(cached.articles());
                }
            } finally {
                cacheLock.readLock().unlock();
            }
        }

        // Fetch from database and update cache
        return repo.getActiveNews(MailboxConfig.INSTANCE.getMaxNewsArticles())
            .thenApply(articles -> {
                if (cacheEnabled) {
                    cacheLock.writeLock().lock();
                    try {
                        cachedActiveNews.set(new CachedNews(List.copyOf(articles), Instant.now()));
                        LOGGER.debug("[News] Cached {} active articles", articles.size());
                    } finally {
                        cacheLock.writeLock().unlock();
                    }
                }
                return articles;
            });
    }

    /**
     * Invalidate the news cache.
     * Called when news are published, updated, or deleted.
     */
    public void invalidateCache() {
        cacheLock.writeLock().lock();
        try {
            cachedActiveNews.set(null);
            LOGGER.debug("[News] Cache invalidated");
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Enable or disable news caching.
     */
    public void setCacheEnabled(boolean enabled) {
        this.cacheEnabled = enabled;
        if (!enabled) {
            invalidateCache();
        }
        LOGGER.info("[News] Cache {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Check if caching is enabled.
     */
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    /**
     * Set the cache TTL.
     */
    public void setCacheTtl(Duration ttl) {
        this.cacheTtl = ttl != null ? ttl : DEFAULT_CACHE_TTL;
        LOGGER.info("[News] Cache TTL set to {} seconds", this.cacheTtl.toSeconds());
    }

    /**
     * Get cache statistics.
     */
    public CacheStats getCacheStats() {
        cacheLock.readLock().lock();
        try {
            CachedNews cached = cachedActiveNews.get();
            if (cached == null) {
                return new CacheStats(false, 0, null, cacheTtl);
            }
            if (cached.isExpired(cacheTtl)) {
                return new CacheStats(false, 0, cached.cachedAt(), cacheTtl);
            }
            return new CacheStats(true, cached.articles().size(), cached.cachedAt(), cacheTtl);
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Cache statistics record.
     */
    public record CacheStats(
        boolean hasCachedData,
        int articleCount,
        @Nullable Instant cachedAt,
        Duration ttl
    ) {}

    /**
     * Get all news articles (including inactive) for admin.
     *
     * @return future completing with the list of articles
     */
    public CompletableFuture<List<NewsArticle>> getAllNews() {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return repo.getAllNews(200); // Higher limit for admin view
    }

    // ============================================================================
    // API SUPPORT METHODS
    // ============================================================================

    /**
     * Get active articles with pagination (for API).
     */
    public CompletableFuture<List<NewsArticle>> getActiveArticles(int limit, int offset) {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        // Get all active and apply pagination in memory
        return repo.getActiveNews(limit + offset).thenApply(articles ->
            articles.stream().skip(offset).limit(limit).toList()
        );
    }

    /**
     * Get all articles with pagination (for API).
     */
    public CompletableFuture<List<NewsArticle>> getAllArticles(int limit, int offset) {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        // Get all and apply pagination in memory
        return repo.getAllNews(limit + offset).thenApply(articles ->
            articles.stream().skip(offset).limit(limit).toList()
        );
    }

    /**
     * Get articles by category with pagination (for API).
     */
    public CompletableFuture<List<NewsArticle>> getArticlesByCategory(NewsCategory category, int limit, int offset) {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return repo.getActiveNews(200).thenApply(articles ->
            articles.stream()
                .filter(a -> a.category() == category)
                .skip(offset)
                .limit(limit)
                .toList()
        );
    }

    /**
     * Create an article (alias for saveNewsArticle).
     */
    public CompletableFuture<Void> createArticle(NewsArticle article) {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("News manager not initialized"));
        }
        return repo.saveNewsArticle(article).thenAccept(saved -> {
            invalidateCache();
            LOGGER.info("[News] Created article: {}", saved.id());
        });
    }

    /**
     * Get total article count.
     */
    public CompletableFuture<Integer> getTotalArticleCount() {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(0);
        }
        return repo.getAllNews(Integer.MAX_VALUE).thenApply(List::size);
    }

    /**
     * Get active article count.
     */
    public CompletableFuture<Integer> getActiveArticleCount() {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(0);
        }
        return repo.getActiveNews(Integer.MAX_VALUE).thenApply(List::size);
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
                invalidateCache();
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
                invalidateCache();
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
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(false);
        }
        return repo.hasReadNews(playerUuid, articleId);
    }

    /**
     * Mark a news article as read by a player.
     *
     * @param playerUuid the player's UUID
     * @param articleId the article UUID
     * @return future completing with success status
     */
    public CompletableFuture<Boolean> markAsRead(UUID playerUuid, UUID articleId) {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(false);
        }
        return repo.markNewsAsRead(playerUuid, articleId);
    }

    /**
     * Get unread news count for a player.
     *
     * @param playerUuid the player's UUID
     * @return future completing with the unread count
     */
    public CompletableFuture<Integer> getUnreadCount(UUID playerUuid) {
        MailboxRepository repo = repository;
        if (repo == null) {
            return CompletableFuture.completedFuture(0);
        }
        return repo.getUnreadNewsCount(playerUuid);
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
