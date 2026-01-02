package com.devmod.mailbox.api.controllers;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import com.devmod.DevMod;
import com.devmod.mailbox.api.AuthMiddleware;
import com.devmod.mailbox.moderation.AdminAuditLog;
import com.devmod.mailbox.news.NewsArticle;
import com.devmod.mailbox.news.NewsCategory;
import com.devmod.mailbox.news.NewsManager;

/**
 * REST API controller for news articles.
 */
public final class NewsController {

    private NewsController() {}

    /**
     * GET /api/news
     * Query params: activeOnly (default true), category (optional)
     */
    public static void listNews(Context ctx) {
        boolean activeOnly = ctx.queryParamAsClass("activeOnly", Boolean.class).getOrDefault(true);
        String categoryStr = ctx.queryParam("category");

        NewsManager.INSTANCE.getActiveNews().thenAccept(articles -> {
            List<NewsArticleDto> dtos = articles.stream()
                .filter(a -> !activeOnly || a.active())
                .filter(a -> categoryStr == null || a.category().name().equalsIgnoreCase(categoryStr))
                .map(NewsArticleDto::from)
                .toList();
            ctx.json(new ListResponse<>(dtos, dtos.size()));
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * GET /api/news/{id}
     */
    public static void getNews(Context ctx) {
        String idStr = ctx.pathParam("id");
        UUID newsId = parseUuid(idStr);
        if (newsId == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid news ID"));
            return;
        }

        NewsManager.INSTANCE.getArticle(newsId).thenAccept(optArticle -> {
            if (optArticle.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND).json(errorResponse("Article not found"));
            } else {
                ctx.json(NewsArticleDto.from(optArticle.get()));
            }
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * POST /api/news
     * Create a new news article.
     */
    public static void createNews(Context ctx) {
        CreateNewsRequest request = ctx.bodyAsClass(CreateNewsRequest.class);

        if (request.title() == null || request.title().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("title is required"));
            return;
        }
        if (request.content() == null || request.content().isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("content is required"));
            return;
        }

        String categoryStr2 = request.category();
        NewsCategory category;
        try {
            category = categoryStr2 != null
                ? NewsCategory.valueOf(categoryStr2.toUpperCase(Locale.ROOT))
                : NewsCategory.ANNOUNCEMENT;
        } catch (IllegalArgumentException e) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid category"));
            return;
        }

        Long expiresAtMillis = request.expiresAtMillis();
        Integer priority = request.priority();
        Boolean active = request.active();
        Long publishAtMillis = request.publishAtMillis();
        Instant publishAt = null;
        if (publishAtMillis != null) {
            publishAt = Instant.ofEpochMilli(publishAtMillis);
        } else if (Boolean.TRUE.equals(request.publishNow())) {
            publishAt = Instant.now();
        }

        NewsArticle article = NewsArticle.builder()
            .id(UUID.randomUUID())
            .title(request.title())
            .content(request.content())
            .category(category)
            .authorName(request.authorName() != null ? request.authorName() : "Admin")
            .publishedAt(publishAt)
            .expiresAt(expiresAtMillis != null ? Instant.ofEpochMilli(expiresAtMillis) : null)
            .priority(priority != null ? priority : 0)
            .active(active != null ? active : true)
            .build();

        NewsManager.INSTANCE.createArticle(article).thenAccept(v -> {
            ctx.status(HttpStatus.CREATED).json(NewsArticleDto.from(article));
            AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                    .action(AdminAuditLog.Action.NEWS_CREATE)
                    .actorUuid(null)
                    .actorName(resolveActorName(ctx))
                    .targetType("news")
                    .targetId(article.id().toString())
                    .details("Title=" + article.title())
                    .build())
                .exceptionally(error -> {
                    DevMod.LOGGER.warn("[Mailbox API] Failed to persist news create audit log", error);
                    return null;
                });
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * PUT /api/news/{id}
     * Update an existing news article.
     */
    public static void updateNews(Context ctx) {
        String idStr = ctx.pathParam("id");
        UUID newsId = parseUuid(idStr);
        if (newsId == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid news ID"));
            return;
        }

        UpdateNewsRequest request = ctx.bodyAsClass(UpdateNewsRequest.class);

        NewsManager.INSTANCE.getArticle(newsId).<NewsArticle>thenCompose(optArticle -> {
            if (optArticle.isEmpty()) {
                ctx.status(HttpStatus.NOT_FOUND).json(errorResponse("Article not found"));
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            NewsArticle existing = optArticle.get();

            NewsCategory category = existing.category();
            String reqCategory = request.category();
            if (reqCategory != null) {
                try {
                    category = NewsCategory.valueOf(reqCategory.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid category"));
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
            }

            Boolean activeFlag = request.active();
            Long publishAtMillis = request.publishAtMillis();
            Long expiresAtMillis = request.expiresAtMillis();
            Integer priority = request.priority();
            NewsArticle updated = NewsArticle.builder()
                .id(existing.id())
                .title(request.title() != null ? request.title() : existing.title())
                .content(request.content() != null ? request.content() : existing.content())
                .category(category)
                .authorName(request.authorName() != null ? request.authorName() : existing.authorName())
                .publishedAt(publishAtMillis != null
                    ? Instant.ofEpochMilli(publishAtMillis)
                    : existing.publishedAt())
                .expiresAt(expiresAtMillis != null
                    ? Instant.ofEpochMilli(expiresAtMillis)
                    : existing.expiresAt())
                .priority(priority != null ? priority : existing.priority())
                .active(activeFlag != null ? activeFlag : existing.active())
                .build();

            return NewsManager.INSTANCE.updateArticle(updated)
                .thenApply(success -> success ? updated : null);
        }).thenAccept(result -> {
            if (result != null) {
                ctx.json(NewsArticleDto.from(result));
                AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                        .action(AdminAuditLog.Action.NEWS_UPDATE)
                        .actorUuid(null)
                        .actorName(resolveActorName(ctx))
                        .targetType("news")
                        .targetId(result.id().toString())
                        .details("Title=" + result.title())
                        .build())
                    .exceptionally(error -> {
                        DevMod.LOGGER.warn("[Mailbox API] Failed to persist news update audit log", error);
                        return null;
                    });
            }
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    /**
     * DELETE /api/news/{id}
     */
    public static void deleteNews(Context ctx) {
        String idStr = ctx.pathParam("id");
        UUID newsId = parseUuid(idStr);
        if (newsId == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid news ID"));
            return;
        }

        NewsManager.INSTANCE.deleteArticle(newsId).thenAccept(success -> {
            if (success) {
                ctx.status(HttpStatus.NO_CONTENT);
                AdminAuditLog.INSTANCE.log(AdminAuditLog.AuditEntry.builder()
                        .action(AdminAuditLog.Action.NEWS_DELETE)
                        .actorUuid(null)
                        .actorName(resolveActorName(ctx))
                        .targetType("news")
                        .targetId(newsId.toString())
                        .details("Deleted via admin API")
                        .build())
                    .exceptionally(error -> {
                        DevMod.LOGGER.warn("[Mailbox API] Failed to persist news delete audit log", error);
                        return null;
                    });
            } else {
                ctx.status(HttpStatus.NOT_FOUND).json(errorResponse("Article not found"));
            }
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    @Nullable
    private static UUID parseUuid(String str) {
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ErrorResponse errorResponse(@Nullable String message) {
        return new ErrorResponse("error", message != null ? message : "Unknown error");
    }

    private static String resolveActorName(Context ctx) {
        AuthMiddleware.AuthenticatedUser user = AuthMiddleware.getUser(ctx);
        return user != null ? user.username() : "Admin";
    }

    // ========================================================================
    // DTOs
    // ========================================================================

    public record ErrorResponse(String status, String message) {}

    public record ListResponse<T>(List<T> items, int count) {}

    public record NewsArticleDto(
        UUID id,
        String title,
        String content,
        String category,
        String authorName,
        @Nullable Long publishedAtMillis,
        @Nullable Long expiresAtMillis,
        int priority,
        boolean active,
        boolean expired
    ) {
        public static NewsArticleDto from(NewsArticle article) {
            Instant publishedAt = article.publishedAt();
            Instant expiresAt = article.expiresAt();
            return new NewsArticleDto(
                article.id(),
                article.title(),
                article.content(),
                article.category().name(),
                article.authorName() != null ? article.authorName() : "Unknown",
                publishedAt != null ? publishedAt.toEpochMilli() : null,
                expiresAt != null ? expiresAt.toEpochMilli() : null,
                article.priority(),
                article.active(),
                article.isExpired()
            );
        }
    }

    public record CreateNewsRequest(
        String title,
        String content,
        @Nullable String category,
        @Nullable String authorName,
        @Nullable Boolean publishNow,
        @Nullable Long publishAtMillis,
        @Nullable Long expiresAtMillis,
        @Nullable Integer priority,
        @Nullable Boolean active
    ) {}

    public record UpdateNewsRequest(
        @Nullable String title,
        @Nullable String content,
        @Nullable String category,
        @Nullable String authorName,
        @Nullable Long publishAtMillis,
        @Nullable Long expiresAtMillis,
        @Nullable Integer priority,
        @Nullable Boolean active
    ) {}
}
