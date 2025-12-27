package com.devmod.mailbox.news;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NewsArticle record.
 */
class NewsArticleTest {

    private static final UUID TEST_ID = UUID.randomUUID();

    @Nested
    @DisplayName("Article Creation")
    class ArticleCreation {

        @Test
        @DisplayName("Builder creates valid article with required fields")
        void builderCreatesValidArticle() {
            NewsArticle article = NewsArticle.builder()
                .id(TEST_ID)
                .title("Test Title")
                .content("Test content here")
                .category(NewsCategory.ANNOUNCEMENT)
                .authorName("Admin")
                .publishedAt(Instant.now())
                .priority(1)
                .active(true)
                .build();

            assertNotNull(article);
            assertEquals(TEST_ID, article.id());
            assertEquals("Test Title", article.title());
            assertEquals("Test content here", article.content());
            assertEquals(NewsCategory.ANNOUNCEMENT, article.category());
            assertEquals("Admin", article.authorName());
            assertEquals(1, article.priority());
            assertTrue(article.active());
        }

        @Test
        @DisplayName("Article defaults to active")
        void articleDefaultsToActive() {
            NewsArticle article = NewsArticle.builder()
                .id(TEST_ID)
                .title("Title")
                .content("Content")
                .category(NewsCategory.PATCH)
                .authorName("System")
                .build();

            assertTrue(article.active());
        }
    }

    @Nested
    @DisplayName("Categories")
    class Categories {

        @Test
        @DisplayName("All news categories are valid")
        void allCategoriesValid() {
            for (NewsCategory category : NewsCategory.values()) {
                assertNotNull(category.name());
            }
        }

        @Test
        @DisplayName("Categories include expected values")
        void categoriesIncludeExpected() {
            assertNotNull(NewsCategory.PATCH);
            assertNotNull(NewsCategory.EVENT);
            assertNotNull(NewsCategory.ANNOUNCEMENT);
            assertNotNull(NewsCategory.MAINTENANCE);
        }

        @Test
        @DisplayName("Category ordinals are stable for network serialization")
        void categoryOrdinalsStable() {
            assertEquals(0, NewsCategory.PATCH.ordinal());
            assertEquals(1, NewsCategory.EVENT.ordinal());
            assertEquals(2, NewsCategory.ANNOUNCEMENT.ordinal());
            assertEquals(3, NewsCategory.MAINTENANCE.ordinal());
        }
    }

    @Nested
    @DisplayName("Priority")
    class Priority {

        @Test
        @DisplayName("Priority 0 is lowest")
        void priority0IsLowest() {
            NewsArticle low = createArticleWithPriority(0);
            NewsArticle high = createArticleWithPriority(10);

            assertTrue(high.priority() > low.priority());
        }

        @Test
        @DisplayName("Higher priority articles should display first")
        void higherPriorityFirst() {
            NewsArticle normal = createArticleWithPriority(1);
            NewsArticle urgent = createArticleWithPriority(5);
            NewsArticle critical = createArticleWithPriority(10);

            assertTrue(critical.priority() > urgent.priority());
            assertTrue(urgent.priority() > normal.priority());
        }
    }

    @Nested
    @DisplayName("Expiration")
    class Expiration {

        @Test
        @DisplayName("Article without expiration is not expired")
        void articleWithoutExpirationNotExpired() {
            NewsArticle article = createTestArticle();
            assertNull(article.expiresAt());
            assertFalse(article.isExpired());
        }

        @Test
        @DisplayName("Article with future expiration is not expired")
        void articleWithFutureExpirationNotExpired() {
            NewsArticle article = NewsArticle.builder()
                .id(TEST_ID)
                .title("Event")
                .content("Limited time event")
                .category(NewsCategory.EVENT)
                .authorName("System")
                .publishedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .active(true)
                .build();

            assertFalse(article.isExpired());
        }

        @Test
        @DisplayName("Article with past expiration is expired")
        void articleWithPastExpirationIsExpired() {
            NewsArticle article = NewsArticle.builder()
                .id(TEST_ID)
                .title("Old Event")
                .content("Event has ended")
                .category(NewsCategory.EVENT)
                .authorName("System")
                .publishedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600))
                .active(true)
                .build();

            assertTrue(article.isExpired());
        }
    }

    @Nested
    @DisplayName("Active Status")
    class ActiveStatus {

        @Test
        @DisplayName("Inactive article can be created")
        void inactiveArticleCanBeCreated() {
            NewsArticle inactive = NewsArticle.builder()
                .id(TEST_ID)
                .title("Draft")
                .content("Work in progress")
                .category(NewsCategory.ANNOUNCEMENT)
                .authorName("Admin")
                .active(false)
                .build();

            assertFalse(inactive.active());
        }

        @Test
        @DisplayName("Active article can be deactivated")
        void activeCanBeDeactivated() {
            NewsArticle active = createTestArticle();
            assertTrue(active.active());

            NewsArticle deactivated = active.withDeactivated();
            assertFalse(deactivated.active());
            assertEquals(active.id(), deactivated.id());
        }
    }

    private NewsArticle createTestArticle() {
        return NewsArticle.builder()
            .id(TEST_ID)
            .title("Test Article")
            .content("Test content")
            .category(NewsCategory.ANNOUNCEMENT)
            .authorName("Admin")
            .publishedAt(Instant.now())
            .priority(1)
            .active(true)
            .build();
    }

    private NewsArticle createArticleWithPriority(int priority) {
        return NewsArticle.builder()
            .id(UUID.randomUUID())
            .title("Priority " + priority)
            .content("Content")
            .category(NewsCategory.ANNOUNCEMENT)
            .authorName("Admin")
            .priority(priority)
            .active(true)
            .build();
    }
}
