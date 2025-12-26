package com.devmod.mailbox.news;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Immutable record representing a news article.
 *
 * News articles are global announcements visible to all players.
 * They can be categorized, scheduled for future publication, and set to expire.
 */
public record NewsArticle(
    UUID id,
    String title,
    String content,
    NewsCategory category,
    @Nullable String authorName,
    Instant createdAt,
    @Nullable Instant publishedAt,
    @Nullable Instant expiresAt,
    int priority,
    boolean active
) {

    /**
     * Compact constructor with validation.
     */
    public NewsArticle {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        // Validate title length
        if (title.length() > 256) {
            title = title.substring(0, 256);
        }

        // Validate author name length
        if (authorName != null && authorName.length() > 64) {
            authorName = authorName.substring(0, 64);
        }

        // Validate priority range
        priority = Math.max(0, Math.min(100, priority));
    }

    /**
     * Check if this article is currently published.
     */
    public boolean isPublished() {
        if (!active) {
            return false;
        }
        if (publishedAt == null) {
            return false;
        }
        Instant now = Instant.now();
        return !now.isBefore(publishedAt);
    }

    /**
     * Check if this article is expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if this article is visible to players.
     */
    public boolean isVisible() {
        return active && isPublished() && !isExpired();
    }

    /**
     * Create a copy with updated content.
     */
    public NewsArticle withContent(String newTitle, String newContent) {
        return new NewsArticle(
            id, newTitle, newContent, category, authorName,
            createdAt, publishedAt, expiresAt, priority, active
        );
    }

    /**
     * Create a copy that is published now.
     */
    public NewsArticle withPublishedNow() {
        return new NewsArticle(
            id, title, content, category, authorName,
            createdAt, Instant.now(), expiresAt, priority, active
        );
    }

    /**
     * Create a copy with scheduled publication.
     */
    public NewsArticle withPublishedAt(Instant publishAt) {
        return new NewsArticle(
            id, title, content, category, authorName,
            createdAt, publishAt, expiresAt, priority, active
        );
    }

    /**
     * Create a copy that is deactivated.
     */
    public NewsArticle withDeactivated() {
        return new NewsArticle(
            id, title, content, category, authorName,
            createdAt, publishedAt, expiresAt, priority, false
        );
    }

    /**
     * Builder for creating NewsArticle instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private @Nullable UUID id;
        private @Nullable String title;
        private @Nullable String content;
        private NewsCategory category = NewsCategory.ANNOUNCEMENT;
        private @Nullable String authorName;
        private @Nullable Instant createdAt;
        private @Nullable Instant publishedAt;
        private @Nullable Instant expiresAt;
        private int priority = 0;
        private boolean active = true;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder category(NewsCategory category) {
            if (category != null) {
                this.category = category;
            }
            return this;
        }

        public Builder authorName(@Nullable String authorName) {
            this.authorName = authorName != null && !authorName.isBlank() ? authorName : null;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder publishedAt(@Nullable Instant publishedAt) {
            this.publishedAt = publishedAt;
            return this;
        }

        public Builder publishNow() {
            this.publishedAt = Instant.now();
            return this;
        }

        public Builder expiresAt(@Nullable Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public NewsArticle build() {
            UUID resolvedId = id != null ? id : UUID.randomUUID();
            Instant resolvedCreatedAt = createdAt != null ? createdAt : Instant.now();
            String resolvedTitle = Objects.requireNonNull(title, "title");
            String resolvedContent = Objects.requireNonNull(content, "content");
            NewsCategory resolvedCategory = category != null ? category : NewsCategory.ANNOUNCEMENT;
            return new NewsArticle(
                resolvedId, resolvedTitle, resolvedContent, resolvedCategory, authorName,
                resolvedCreatedAt, publishedAt, expiresAt, priority, active
            );
        }
    }
}
