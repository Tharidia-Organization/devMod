package com.devmod.mailbox.api.controllers;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.devmod.mailbox.moderation.AdminAuditLog;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
/**
 * REST API controller for admin audit log entries.
 */
public final class AdminAuditController {

    private AdminAuditController() {}

    /**
     * GET /api/audit
     * Query params: limit (default 100), action (optional), actor (optional name/uuid)
     */
    public static void listAudits(Context ctx) {
        int limitParam = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(100);
        int limit = Math.min(Math.max(limitParam, 1), 200);
        String actionStr = ctx.queryParam("action");
        String actorStr = ctx.queryParam("actor");

        AdminAuditLog.Action actionFilter = null;
        if (actionStr != null && !actionStr.isBlank()) {
            try {
                actionFilter = AdminAuditLog.Action.valueOf(actionStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                ctx.status(HttpStatus.BAD_REQUEST).json(errorResponse("Invalid action"));
                return;
            }
        }

        UUID actorUuid = parseUuid(actorStr);
        CompletableFuture<List<AdminAuditLog.AuditEntry>> future =
            AdminAuditLog.INSTANCE.fetchRecentEntries(limit);

        final AdminAuditLog.Action finalActionFilter = actionFilter;
        final String finalActorStr = actorStr;
        final UUID finalActorUuid = actorUuid;

        future.thenAccept(entries -> {
            List<AuditDto> dtos = entries.stream()
                .filter(entry -> finalActionFilter == null || entry.action() == finalActionFilter)
                .filter(entry -> {
                    if (finalActorStr == null || finalActorStr.isBlank()) {
                        return true;
                    }
                    if (finalActorUuid != null) {
                        return finalActorUuid.equals(entry.actorUuid());
                    }
                    return entry.actorName() != null
                        && entry.actorName().equalsIgnoreCase(finalActorStr);
                })
                .map(AuditDto::from)
                .toList();
            ctx.json(new ListResponse<>(dtos, dtos.size()));
        }).exceptionally(e -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(errorResponse(e.getMessage()));
            return null;
        });
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    @Nullable
    private static UUID parseUuid(@Nullable String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ErrorResponse errorResponse(@Nullable String message) {
        return new ErrorResponse("error", message != null ? message : "Unknown error");
    }

    // ========================================================================
    // DTOs
    // ========================================================================

    public record ListResponse<T>(List<T> items, int count) {}

    public record ErrorResponse(String status, String message) {}

    public record AuditDto(
        UUID id,
        String action,
        @Nullable UUID actorUuid,
        String actorName,
        String targetType,
        @Nullable String targetId,
        @Nullable String details,
        long timestamp
    ) {
        public static AuditDto from(AdminAuditLog.AuditEntry entry) {
            return new AuditDto(
                entry.id(),
                entry.action().name(),
                entry.actorUuid(),
                entry.actorName(),
                entry.targetType(),
                entry.targetId(),
                entry.details(),
                entry.timestamp()
            );
        }
    }
}
