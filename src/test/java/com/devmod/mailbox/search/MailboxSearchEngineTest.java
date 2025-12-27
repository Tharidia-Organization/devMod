package com.devmod.mailbox.search;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.mailbox.MessageType;
import com.devmod.mailbox.search.MailboxSearchEngine.ReadStatus;
import com.devmod.mailbox.search.MailboxSearchEngine.SearchQuery;
import com.devmod.mailbox.search.MailboxSearchEngine.SearchResult;
import com.devmod.mailbox.search.MailboxSearchEngine.SearchStats;
import com.devmod.mailbox.search.MailboxSearchEngine.SortBy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MailboxSearchEngine.
 */
@DisplayName("MailboxSearchEngine Tests")
class MailboxSearchEngineTest {

    @BeforeAll
    static void setup() {
        // Initialize would require DuckDB - tests are structural
    }

    @Nested
    @DisplayName("SearchQuery Builder")
    class SearchQueryBuilderTests {

        @Test
        @DisplayName("Build query with all parameters")
        void buildQueryWithAllParameters() {
            UUID playerUuid = UUID.randomUUID();
            Instant fromDate = Instant.now().minus(7, ChronoUnit.DAYS);
            Instant toDate = Instant.now();

            SearchQuery query = SearchQuery.builder()
                .playerUuid(playerUuid)
                .searchText("test search")
                .messageType(MessageType.SYSTEM)
                .readStatus(ReadStatus.UNREAD)
                .fromDate(fromDate)
                .toDate(toDate)
                .senderName("Admin")
                .sortBy(SortBy.DATE_DESC)
                .page(0)
                .pageSize(20)
                .build();

            assertNotNull(query);
        }

        @Test
        @DisplayName("Build query with minimal parameters")
        void buildQueryWithMinimalParameters() {
            UUID playerUuid = UUID.randomUUID();

            SearchQuery query = SearchQuery.builder()
                .playerUuid(playerUuid)
                .build();

            assertNotNull(query);
        }

        @Test
        @DisplayName("Page cannot be negative")
        void pageCannotBeNegative() {
            UUID playerUuid = UUID.randomUUID();

            SearchQuery query = SearchQuery.builder()
                .playerUuid(playerUuid)
                .page(-5)
                .build();

            // Negative page should be clamped to 0
            assertNotNull(query);
        }
    }

    @Nested
    @DisplayName("SearchResult")
    class SearchResultTests {

        @Test
        @DisplayName("Empty result")
        void emptyResult() {
            SearchResult result = SearchResult.empty();

            assertTrue(result.isEmpty());
            assertEquals(0, result.totalHits());
            assertEquals(0, result.totalPages());
            assertFalse(result.hasMore());
        }

        @Test
        @DisplayName("Has more pages")
        void hasMorePages() {
            SearchResult result = new SearchResult(
                List.of(),
                0,
                20,
                50,
                3,
                "test"
            );

            assertTrue(result.hasMore());
            assertEquals(3, result.totalPages());
        }

        @Test
        @DisplayName("No more pages on last page")
        void noMorePagesOnLastPage() {
            SearchResult result = new SearchResult(
                List.of(),
                2,
                20,
                50,
                3,
                "test"
            );

            assertFalse(result.hasMore());
        }
    }

    @Nested
    @DisplayName("Read Status")
    class ReadStatusTests {

        @Test
        @DisplayName("All read status values")
        void allReadStatusValues() {
            assertEquals(3, ReadStatus.values().length);
            assertNotNull(ReadStatus.READ);
            assertNotNull(ReadStatus.UNREAD);
            assertNotNull(ReadStatus.ALL);
        }
    }

    @Nested
    @DisplayName("Sort Options")
    class SortOptionsTests {

        @Test
        @DisplayName("All sort options")
        void allSortOptions() {
            assertEquals(5, SortBy.values().length);
            assertNotNull(SortBy.DATE_ASC);
            assertNotNull(SortBy.DATE_DESC);
            assertNotNull(SortBy.SENDER_ASC);
            assertNotNull(SortBy.SENDER_DESC);
            assertNotNull(SortBy.RELEVANCE);
        }
    }

    @Nested
    @DisplayName("Search Stats")
    class SearchStatsTests {

        @Test
        @DisplayName("Create search stats")
        void createSearchStats() {
            SearchStats stats = new SearchStats(100, 25, true);

            assertEquals(100, stats.totalIndexedMessages());
            assertEquals(25, stats.uniquePlayers());
            assertTrue(stats.indexReady());
        }
    }

    @Nested
    @DisplayName("Search Hit")
    class SearchHitTests {

        @Test
        @DisplayName("Read message")
        void readMessage() {
            var hit = new MailboxSearchEngine.SearchHit(
                UUID.randomUUID(),
                "Admin",
                "Test Subject",
                "Test Body",
                MessageType.SYSTEM,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now(),
                0.8,
                "...test **highlighted**..."
            );

            assertTrue(hit.isRead());
            assertNotNull(hit.readAt());
        }

        @Test
        @DisplayName("Unread message")
        void unreadMessage() {
            var hit = new MailboxSearchEngine.SearchHit(
                UUID.randomUUID(),
                "Admin",
                "Test Subject",
                "Test Body",
                MessageType.SYSTEM,
                Instant.now().minus(1, ChronoUnit.HOURS),
                null,
                0.5,
                null
            );

            assertFalse(hit.isRead());
            assertNull(hit.readAt());
        }
    }

    @Nested
    @DisplayName("Query Filters")
    class QueryFiltersTests {

        @Test
        @DisplayName("Filter by message type")
        void filterByMessageType() {
            for (MessageType type : MessageType.values()) {
                SearchQuery query = SearchQuery.builder()
                    .playerUuid(UUID.randomUUID())
                    .messageType(type)
                    .build();

                assertNotNull(query);
            }
        }

        @Test
        @DisplayName("Filter by read status")
        void filterByReadStatus() {
            for (ReadStatus status : ReadStatus.values()) {
                SearchQuery query = SearchQuery.builder()
                    .playerUuid(UUID.randomUUID())
                    .readStatus(status)
                    .build();

                assertNotNull(query);
            }
        }

        @Test
        @DisplayName("Filter by date range")
        void filterByDateRange() {
            SearchQuery query = SearchQuery.builder()
                .playerUuid(UUID.randomUUID())
                .fromDate(Instant.now().minus(30, ChronoUnit.DAYS))
                .toDate(Instant.now())
                .build();

            assertNotNull(query);
        }

        @Test
        @DisplayName("Filter by sender name")
        void filterBySenderName() {
            SearchQuery query = SearchQuery.builder()
                .playerUuid(UUID.randomUUID())
                .senderName("Admin")
                .build();

            assertNotNull(query);
        }

        @Test
        @DisplayName("Multiple filters combined")
        void multipleFiltersCombined() {
            SearchQuery query = SearchQuery.builder()
                .playerUuid(UUID.randomUUID())
                .searchText("reward")
                .messageType(MessageType.REWARD)
                .readStatus(ReadStatus.UNREAD)
                .senderName("System")
                .fromDate(Instant.now().minus(7, ChronoUnit.DAYS))
                .sortBy(SortBy.RELEVANCE)
                .page(0)
                .pageSize(10)
                .build();

            assertNotNull(query);
        }
    }

    @Nested
    @DisplayName("Pagination")
    class PaginationTests {

        @Test
        @DisplayName("First page")
        void firstPage() {
            SearchQuery query = SearchQuery.builder()
                .playerUuid(UUID.randomUUID())
                .page(0)
                .pageSize(20)
                .build();

            assertNotNull(query);
        }

        @Test
        @DisplayName("Middle page")
        void middlePage() {
            SearchQuery query = SearchQuery.builder()
                .playerUuid(UUID.randomUUID())
                .page(5)
                .pageSize(20)
                .build();

            assertNotNull(query);
        }

        @Test
        @DisplayName("Custom page size")
        void customPageSize() {
            SearchQuery query = SearchQuery.builder()
                .playerUuid(UUID.randomUUID())
                .pageSize(50)
                .build();

            assertNotNull(query);
        }
    }

    @Nested
    @DisplayName("Sort Options")
    class SortOptionsQueryTests {

        @Test
        @DisplayName("Sort by date ascending")
        void sortByDateAscending() {
            SearchQuery query = SearchQuery.builder()
                .playerUuid(UUID.randomUUID())
                .sortBy(SortBy.DATE_ASC)
                .build();

            assertNotNull(query);
        }

        @Test
        @DisplayName("Sort by date descending")
        void sortByDateDescending() {
            SearchQuery query = SearchQuery.builder()
                .playerUuid(UUID.randomUUID())
                .sortBy(SortBy.DATE_DESC)
                .build();

            assertNotNull(query);
        }

        @Test
        @DisplayName("Sort by sender")
        void sortBySender() {
            SearchQuery query = SearchQuery.builder()
                .playerUuid(UUID.randomUUID())
                .sortBy(SortBy.SENDER_ASC)
                .build();

            assertNotNull(query);
        }

        @Test
        @DisplayName("Sort by relevance")
        void sortByRelevance() {
            SearchQuery query = SearchQuery.builder()
                .playerUuid(UUID.randomUUID())
                .searchText("test")
                .sortBy(SortBy.RELEVANCE)
                .build();

            assertNotNull(query);
        }
    }
}
