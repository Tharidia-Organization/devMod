package com.devmod.mailbox.api.controllers;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.mailbox.MessageType;
import com.devmod.mailbox.search.MailboxSearchEngine;
import com.devmod.mailbox.search.MailboxSearchEngine.ReadStatus;
import com.devmod.mailbox.search.MailboxSearchEngine.SearchHit;
import com.devmod.mailbox.search.MailboxSearchEngine.SearchQuery;
import com.devmod.mailbox.search.MailboxSearchEngine.SearchResult;
import com.devmod.mailbox.search.MailboxSearchEngine.SearchStats;
import com.devmod.mailbox.search.MailboxSearchEngine.SortBy;

import io.javalin.http.Context;

/**
 * REST API controller for mailbox search functionality.
 */
public final class SearchController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchController.class);

    private SearchController() {}

    // ============================================================================
    // SEARCH ENDPOINTS
    // ============================================================================

    /**
     * POST /api/search
     * Search messages with full-text query and filters.
     *
     * Request body:
     * {
     *   "playerUuid": "uuid",
     *   "query": "search text",
     *   "messageType": "SYSTEM|ADMIN|PLAYER|REWARD",
     *   "readStatus": "READ|UNREAD|ALL",
     *   "fromDate": "2024-01-01T00:00:00Z",
     *   "toDate": "2024-12-31T23:59:59Z",
     *   "senderName": "partial sender name",
     *   "sortBy": "DATE_DESC|DATE_ASC|SENDER_ASC|SENDER_DESC|RELEVANCE",
     *   "page": 0,
     *   "pageSize": 20
     * }
     */
    public static void search(Context ctx) {
        SearchRequestDto request = ctx.bodyAsClass(SearchRequestDto.class);

        if (request.playerUuid() == null) {
            ctx.status(400).json(new ErrorResponse("error", "playerUuid is required"));
            return;
        }

        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(request.playerUuid());
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(new ErrorResponse("error", "Invalid playerUuid format"));
            return;
        }

        SearchQuery.Builder queryBuilder = SearchQuery.builder()
            .playerUuid(playerUuid)
            .searchText(request.query())
            .page(request.page() != null ? request.page() : 0)
            .pageSize(request.pageSize() != null ? request.pageSize() : 20);

        MessageType messageType = parseEnum(request.messageType(), MessageType.class, "messageType");
        if (messageType != null) {
            queryBuilder.messageType(messageType);
        }

        ReadStatus readStatus = parseEnum(request.readStatus(), ReadStatus.class, "readStatus");
        if (readStatus != null) {
            queryBuilder.readStatus(readStatus);
        }

        Instant fromDate = parseInstant(request.fromDate(), "fromDate");
        if (fromDate != null) {
            queryBuilder.fromDate(fromDate);
        }

        Instant toDate = parseInstant(request.toDate(), "toDate");
        if (toDate != null) {
            queryBuilder.toDate(toDate);
        }

        if (request.senderName() != null) {
            queryBuilder.senderName(request.senderName());
        }

        SortBy sortBy = parseEnum(request.sortBy(), SortBy.class, "sortBy");
        if (sortBy != null) {
            queryBuilder.sortBy(sortBy);
        }

        SearchResult result = MailboxSearchEngine.INSTANCE.search(queryBuilder.build()).join();
        ctx.json(toSearchResultDto(result));
    }

    /**
     * GET /api/search/quick
     * Quick search with just query string and player UUID.
     *
     * Query params:
     * - playerUuid (required)
     * - q (required): search query
     * - page (optional, default 0)
     * - pageSize (optional, default 20)
     */
    public static void quickSearch(Context ctx) {
        String playerUuidParam = ctx.queryParam("playerUuid");
        String query = ctx.queryParam("q");

        if (playerUuidParam == null || playerUuidParam.isBlank()) {
            ctx.status(400).json(new ErrorResponse("error", "playerUuid is required"));
            return;
        }

        if (query == null || query.isBlank()) {
            ctx.status(400).json(new ErrorResponse("error", "q (query) is required"));
            return;
        }

        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(playerUuidParam);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(new ErrorResponse("error", "Invalid playerUuid format"));
            return;
        }

        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int pageSize = ctx.queryParamAsClass("pageSize", Integer.class).getOrDefault(20);

        SearchResult result = MailboxSearchEngine.INSTANCE.search(
            SearchQuery.builder()
                .playerUuid(playerUuid)
                .searchText(query)
                .page(page)
                .pageSize(pageSize)
                .build()
        ).join();

        ctx.json(toSearchResultDto(result));
    }

    /**
     * GET /api/search/suggestions
     * Get search suggestions/autocomplete.
     *
     * Query params:
     * - playerUuid (required)
     * - prefix (required): partial search text
     */
    public static void getSuggestions(Context ctx) {
        String playerUuidParam = ctx.queryParam("playerUuid");
        String prefix = ctx.queryParam("prefix");

        if (playerUuidParam == null || playerUuidParam.isBlank()) {
            ctx.status(400).json(new ErrorResponse("error", "playerUuid is required"));
            return;
        }

        if (prefix == null || prefix.length() < 2) {
            ctx.status(400).json(new ErrorResponse("error", "prefix must be at least 2 characters"));
            return;
        }

        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(playerUuidParam);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(new ErrorResponse("error", "Invalid playerUuid format"));
            return;
        }

        List<String> suggestions = MailboxSearchEngine.INSTANCE.getSuggestions(playerUuid, prefix).join();
        ctx.json(new SuggestionsResponse(suggestions, suggestions.size()));
    }

    /**
     * GET /api/search/stats
     * Get search index statistics.
     */
    public static void getStats(Context ctx) {
        SearchStats stats = MailboxSearchEngine.INSTANCE.getStats().join();
        ctx.json(new SearchStatsDto(
            stats.totalIndexedMessages(),
            stats.uniquePlayers(),
            stats.indexReady()
        ));
    }

    /**
     * POST /api/search/rebuild-index
     * Rebuild the search index (admin only).
     */
    public static void rebuildIndex(Context ctx) {
        int count = MailboxSearchEngine.INSTANCE.rebuildIndex().join();
        ctx.json(new RebuildResponse("success", count));
    }

    // ============================================================================
    // DTOs
    // ============================================================================

    @Nullable
    private static Instant parseInstant(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            LOGGER.warn("[MailboxSearch] Ignoring invalid {} value: {}", field, value);
            return null;
        }
    }

    @Nullable
    private static <T extends Enum<T>> T parseEnum(@Nullable String value, Class<T> enumType, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[MailboxSearch] Ignoring invalid {} value: {}", field, value);
            return null;
        }
    }

    private static SearchResultDto toSearchResultDto(SearchResult result) {
        List<SearchHitDto> hits = result.hits().stream()
            .map(SearchController::toSearchHitDto)
            .toList();

        return new SearchResultDto(
            hits,
            result.page(),
            result.pageSize(),
            result.totalHits(),
            result.totalPages(),
            result.hasMore(),
            result.query()
        );
    }

    private static SearchHitDto toSearchHitDto(SearchHit hit) {
        return new SearchHitDto(
            hit.messageId().toString(),
            hit.senderName(),
            hit.subject(),
            hit.body(),
            hit.type().name(),
            hit.sentAt().toEpochMilli(),
            hit.readAt() != null ? hit.readAt().toEpochMilli() : null,
            hit.isRead(),
            hit.relevance(),
            hit.highlight()
        );
    }

    public record ErrorResponse(String status, String message) {}

    public record SearchRequestDto(
        @Nullable String playerUuid,
        @Nullable String query,
        @Nullable String messageType,
        @Nullable String readStatus,
        @Nullable String fromDate,
        @Nullable String toDate,
        @Nullable String senderName,
        @Nullable String sortBy,
        @Nullable Integer page,
        @Nullable Integer pageSize
    ) {}

    public record SearchHitDto(
        String messageId,
        @Nullable String senderName,
        String subject,
        String body,
        String messageType,
        long sentAtMillis,
        @Nullable Long readAtMillis,
        boolean isRead,
        double relevance,
        @Nullable String highlight
    ) {}

    public record SearchResultDto(
        List<SearchHitDto> hits,
        int page,
        int pageSize,
        int totalHits,
        int totalPages,
        boolean hasMore,
        @Nullable String query
    ) {}

    public record SuggestionsResponse(List<String> suggestions, int count) {}

    public record SearchStatsDto(
        int totalIndexedMessages,
        int uniquePlayers,
        boolean indexReady
    ) {}

    public record RebuildResponse(String status, int messagesIndexed) {}
}
