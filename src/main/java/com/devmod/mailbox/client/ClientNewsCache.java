package com.devmod.mailbox.client;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.mailbox.network.payload.NewsSyncPayload;
import com.devmod.mailbox.network.payload.NewsSyncPayload.NewsArticleData;
import com.devmod.mailbox.news.NewsArticle;
import com.devmod.mailbox.news.NewsCategory;

/**
 * Client-side cache for news articles.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientNewsCache {

    @Nullable
    private static volatile NewsSyncPayload cachedSync = null;
    private static volatile long lastSyncTime = 0;

    /** News articles that have been displayed in the overlay (not persisted across sessions). */
    private static final Set<UUID> viewedNewsIds = new HashSet<>();

    public static final long CACHE_STALE_MS = 300000; // 5 minutes

    private ClientNewsCache() {}

    /**
     * Update the cache with a full sync from server.
     */
    public static void update(NewsSyncPayload payload) {
        cachedSync = payload;
        lastSyncTime = System.currentTimeMillis();
    }

    /**
     * Mark an article as read locally (optimistic update).
     */
    public static void markAsRead(UUID articleId) {
        NewsSyncPayload sync = cachedSync;
        if (sync == null) return;

        List<NewsArticleData> updated = sync.articles().stream()
            .map(article -> {
                if (article.id().equals(articleId) && !article.isRead()) {
                    return new NewsArticleData(
                        article.id(),
                        article.title(),
                        article.content(),
                        article.categoryOrdinal(),
                        article.authorName(),
                        article.publishedAtMillis(),
                        article.priority(),
                        true // now read
                    );
                }
                return article;
            })
            .toList();

        int newUnread = (int) updated.stream().filter(a -> !a.isRead()).count();
        cachedSync = new NewsSyncPayload(updated, newUnread);
    }

    /**
     * Clear all cached data.
     */
    public static void clear() {
        cachedSync = null;
        lastSyncTime = 0;
        viewedNewsIds.clear();
    }

    /**
     * Mark a news article as viewed in the overlay (local tracking only).
     */
    public static void markAsViewed(UUID articleId) {
        viewedNewsIds.add(articleId);
    }

    /**
     * Get a news article that should be displayed in the overlay.
     * Returns the highest priority unread article that hasn't been viewed yet.
     */
    @Nullable
    public static NewsArticle getDisplayableNews() {
        NewsSyncPayload sync = cachedSync;
        if (sync == null) return null;

        return sync.articles().stream()
            .filter(a -> !a.isRead())
            .filter(a -> !viewedNewsIds.contains(a.id()))
            .max((a, b) -> Integer.compare(a.priority(), b.priority()))
            .map(ClientNewsCache::toNewsArticle)
            .orElse(null);
    }

    /**
     * Convert NewsArticleData to NewsArticle.
     */
    private static NewsArticle toNewsArticle(NewsArticleData data) {
        NewsCategory[] categories = NewsCategory.values();
        int safeIndex = Math.max(0, Math.min(data.categoryOrdinal(), categories.length - 1));
        NewsCategory category = categories[safeIndex];
        return NewsArticle.builder()
            .id(data.id())
            .title(data.title())
            .content(data.content())
            .category(category)
            .authorName(data.authorName())
            .createdAt(Instant.now()) // Not synced, use current time
            .publishedAt(data.publishedAtMillis() > 0 ? Instant.ofEpochMilli(data.publishedAtMillis()) : null)
            .priority(data.priority())
            .active(true)
            .build();
    }

    // ========== GETTERS ==========

    /**
     * Check if we have any cached data.
     */
    public static boolean hasCachedData() {
        return cachedSync != null;
    }

    /**
     * Check if cache is stale.
     */
    public static boolean isStale() {
        return System.currentTimeMillis() - lastSyncTime > CACHE_STALE_MS;
    }

    /**
     * Get the cached sync payload.
     */
    @Nullable
    public static NewsSyncPayload getSync() {
        return cachedSync;
    }

    /**
     * Get all articles.
     */
    public static List<NewsArticleData> getArticles() {
        NewsSyncPayload sync = cachedSync;
        return sync != null ? sync.articles() : List.of();
    }

    /**
     * Get a specific article by ID.
     */
    @Nullable
    public static NewsArticleData getArticle(UUID articleId) {
        NewsSyncPayload sync = cachedSync;
        if (sync == null) return null;

        return sync.articles().stream()
            .filter(a -> a.id().equals(articleId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get unread count.
     */
    public static int getUnreadCount() {
        NewsSyncPayload sync = cachedSync;
        return sync != null ? sync.unreadCount() : 0;
    }

    /**
     * Get total article count.
     */
    public static int getArticleCount() {
        NewsSyncPayload sync = cachedSync;
        return sync != null ? sync.articles().size() : 0;
    }

    /**
     * Check if there are unread articles.
     */
    public static boolean hasUnread() {
        return getUnreadCount() > 0;
    }

    /**
     * Get articles by category.
     */
    public static List<NewsArticleData> getArticlesByCategory(int categoryOrdinal) {
        NewsSyncPayload sync = cachedSync;
        if (sync == null) return List.of();

        return sync.articles().stream()
            .filter(a -> a.categoryOrdinal() == categoryOrdinal)
            .toList();
    }
}
