package com.devmod.mailbox.client;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.mailbox.network.payload.NewsSyncPayload;
import com.devmod.mailbox.network.payload.NewsSyncPayload.NewsArticleData;

/**
 * Client-side cache for news articles.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientNewsCache {

    @Nullable
    private static volatile NewsSyncPayload cachedSync = null;

    private ClientNewsCache() {}

    /**
     * Update the cache with a full sync from server.
     */
    public static void update(NewsSyncPayload payload) {
        cachedSync = payload;
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
    }

    // ========== GETTERS ==========

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
