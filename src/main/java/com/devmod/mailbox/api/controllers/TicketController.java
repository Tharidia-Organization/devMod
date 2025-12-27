package com.devmod.mailbox.api.controllers;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

import com.devmod.mailbox.ticket.Ticket;
import com.devmod.mailbox.ticket.TicketCategory;
import com.devmod.mailbox.ticket.TicketComment;
import com.devmod.mailbox.ticket.TicketManager;
import com.devmod.mailbox.ticket.TicketPriority;
import com.devmod.mailbox.ticket.TicketRepository;
import com.devmod.mailbox.ticket.TicketStatus;

import io.javalin.http.Context;

/**
 * REST API controller for support ticket management.
 */
public final class TicketController {

    private TicketController() {}

    // ============================================================================
    // TICKET ENDPOINTS
    // ============================================================================

    /**
     * GET /api/tickets
     * List tickets with optional filters.
     *
     * Query params:
     * - status: OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED
     * - category: BUG, ABUSE, SUGGESTION, QUESTION, OTHER
     * - priority: LOW, NORMAL, HIGH, URGENT
     * - assignedTo: UUID
     * - unassigned: true/false
     * - page: int (default 0)
     * - pageSize: int (default 20)
     */
    public static void listTickets(Context ctx) {
        TicketStatus status = parseEnum(ctx.queryParam("status"), TicketStatus.class);
        TicketCategory category = parseEnum(ctx.queryParam("category"), TicketCategory.class);
        TicketPriority priority = parseEnum(ctx.queryParam("priority"), TicketPriority.class);
        UUID assignedTo = parseUuid(ctx.queryParam("assignedTo"));
        boolean unassigned = "true".equalsIgnoreCase(ctx.queryParam("unassigned"));
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int pageSize = ctx.queryParamAsClass("pageSize", Integer.class).getOrDefault(20);

        TicketRepository.TicketQuery query = new TicketRepository.TicketQuery(
            null, // reporterUuid
            status,
            category,
            priority,
            assignedTo,
            unassigned,
            pageSize,
            page * pageSize
        );

        List<Ticket> tickets = TicketManager.INSTANCE.findTickets(query).join();
        int total = TicketRepository.INSTANCE.countTickets(query).join();

        ctx.json(new TicketListResponse(
            tickets.stream().map(TicketController::toTicketDto).toList(),
            page,
            pageSize,
            total,
            (total + pageSize - 1) / pageSize
        ));
    }

    /**
     * GET /api/tickets/{id}
     * Get a ticket by ID.
     */
    public static void getTicket(Context ctx) {
        UUID ticketId = parseUuidOrThrow(ctx.pathParam("id"));

        Ticket ticket = TicketManager.INSTANCE.getTicket(ticketId).join()
            .orElse(null);

        if (ticket == null) {
            ctx.status(404).json(new ErrorResponse("error", "Ticket not found"));
            return;
        }

        // Include comments
        List<TicketComment> comments = TicketManager.INSTANCE.getComments(ticketId, true).join();

        ctx.json(new TicketDetailResponse(
            toTicketDto(ticket),
            comments.stream().map(TicketController::toCommentDto).toList()
        ));
    }

    /**
     * POST /api/tickets
     * Create a new ticket.
     *
     * Request body:
     * {
     *   "reporterUuid": "uuid",
     *   "reporterName": "name",
     *   "category": "BUG|ABUSE|SUGGESTION|QUESTION|OTHER",
     *   "subject": "Short subject",
     *   "description": "Detailed description",
     *   "reportedUuid": "uuid (optional, for ABUSE)",
     *   "reportedName": "name (optional, for ABUSE)"
     * }
     */
    public static void createTicket(Context ctx) {
        CreateTicketRequest request = ctx.bodyAsClass(CreateTicketRequest.class);

        if (request.reporterUuid() == null || request.category() == null || request.subject() == null) {
            ctx.status(400).json(new ErrorResponse("error", "reporterUuid, category, and subject are required"));
            return;
        }

        UUID reporterUuid = parseUuidOrThrow(request.reporterUuid());
        TicketCategory category = parseEnumOrThrow(request.category(), TicketCategory.class);
        String reporterName = nonBlankOrDefault(request.reporterName(), "Unknown Reporter");

        Ticket ticket;
        if (category == TicketCategory.ABUSE && request.reportedUuid() != null) {
            UUID reportedUuid = parseUuidOrThrow(request.reportedUuid());
            String reportedName = nonBlankOrDefault(request.reportedName(), "Unknown Player");
            ticket = TicketManager.INSTANCE.createPlayerReport(
                reporterUuid,
                reporterName,
                reportedUuid,
                reportedName,
                request.subject(),
                request.description()
            ).join();
        } else {
            ticket = TicketManager.INSTANCE.createTicket(
                reporterUuid,
                reporterName,
                category,
                request.subject(),
                request.description()
            ).join();
        }

        ctx.status(201).json(toTicketDto(ticket));
    }

    /**
     * PUT /api/tickets/{id}
     * Update a ticket.
     *
     * Request body:
     * {
     *   "status": "OPEN|ASSIGNED|IN_PROGRESS|RESOLVED|CLOSED",
     *   "priority": "LOW|NORMAL|HIGH|URGENT",
     *   "resolutionNotes": "notes (for RESOLVED status)"
     * }
     */
    public static void updateTicket(Context ctx) {
        UUID ticketId = parseUuidOrThrow(ctx.pathParam("id"));
        UpdateTicketRequest request = ctx.bodyAsClass(UpdateTicketRequest.class);

        // Get admin name from auth context (simplified)
        String actorName = ctx.attribute("adminName");
        if (actorName == null) actorName = "Admin";

        Ticket updated = null;

        if (request.status() != null) {
            TicketStatus newStatus = parseEnumOrThrow(request.status(), TicketStatus.class);

            if (newStatus == TicketStatus.RESOLVED && request.resolutionNotes() != null) {
                updated = TicketManager.INSTANCE.resolveTicket(
                    ticketId, request.resolutionNotes(), actorName
                ).join().orElse(null);
            } else {
                updated = TicketManager.INSTANCE.updateStatus(
                    ticketId, newStatus, actorName
                ).join().orElse(null);
            }
        }

        if (request.priority() != null) {
            TicketPriority newPriority = parseEnumOrThrow(request.priority(), TicketPriority.class);
            updated = TicketManager.INSTANCE.updatePriority(
                ticketId, newPriority, actorName
            ).join().orElse(null);
        }

        if (updated == null) {
            ctx.status(404).json(new ErrorResponse("error", "Ticket not found or invalid update"));
            return;
        }

        ctx.json(toTicketDto(updated));
    }

    /**
     * PUT /api/tickets/{id}/assign
     * Assign a ticket to a moderator.
     *
     * Request body:
     * {
     *   "assigneeUuid": "uuid",
     *   "assigneeName": "name"
     * }
     */
    public static void assignTicket(Context ctx) {
        UUID ticketId = parseUuidOrThrow(ctx.pathParam("id"));
        AssignTicketRequest request = ctx.bodyAsClass(AssignTicketRequest.class);

        if (request.assigneeUuid() == null || request.assigneeName() == null) {
            ctx.status(400).json(new ErrorResponse("error", "assigneeUuid and assigneeName are required"));
            return;
        }

        UUID assigneeUuid = parseUuidOrThrow(request.assigneeUuid());

        Ticket updated = TicketManager.INSTANCE.assignTicket(
            ticketId, assigneeUuid, request.assigneeName()
        ).join().orElse(null);

        if (updated == null) {
            ctx.status(404).json(new ErrorResponse("error", "Ticket not found"));
            return;
        }

        ctx.json(toTicketDto(updated));
    }

    /**
     * POST /api/tickets/{id}/comments
     * Add a comment to a ticket.
     *
     * Request body:
     * {
     *   "authorUuid": "uuid",
     *   "authorName": "name",
     *   "content": "Comment text",
     *   "isInternal": false
     * }
     */
    public static void addComment(Context ctx) {
        UUID ticketId = parseUuidOrThrow(ctx.pathParam("id"));
        AddCommentRequest request = ctx.bodyAsClass(AddCommentRequest.class);

        String content = request.content();
        if (content == null || content.isBlank()) {
            ctx.status(400).json(new ErrorResponse("error", "content is required"));
            return;
        }
        String authorUuidStr = request.authorUuid();
        if (authorUuidStr == null || authorUuidStr.isBlank()) {
            ctx.status(400).json(new ErrorResponse("error", "authorUuid is required"));
            return;
        }

        UUID authorUuid = parseUuidOrThrow(authorUuidStr);
        String authorName = nonBlankOrDefault(request.authorName(), "Admin");
        boolean isInternal = Boolean.TRUE.equals(request.isInternal());

        TicketComment comment = TicketManager.INSTANCE.addComment(
            ticketId,
            authorUuid,
            authorName,
            request.content(),
            isInternal
        ).join();

        ctx.status(201).json(toCommentDto(comment));
    }

    /**
     * GET /api/tickets/stats
     * Get ticket statistics.
     */
    public static void getStats(Context ctx) {
        TicketRepository.TicketStats stats = TicketManager.INSTANCE.getStats().join();

        ctx.json(new TicketStatsResponse(
            stats.total(),
            stats.open(),
            stats.assigned(),
            stats.inProgress(),
            stats.resolved(),
            stats.closed(),
            stats.avgResolutionTimeMs()
        ));
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    @Nullable
    private static UUID parseUuid(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID parseUuidOrThrow(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID: " + value);
        }
    }

    private static String nonBlankOrDefault(@Nullable String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    @Nullable
    private static <T extends Enum<T>> T parseEnum(@Nullable String value, Class<T> enumType) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <T extends Enum<T>> T parseEnumOrThrow(String value, Class<T> enumType) {
        try {
            return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + enumType.getSimpleName() + ": " + value);
        }
    }

    private static TicketDto toTicketDto(Ticket ticket) {
        UUID reportedUuid = ticket.reportedUuid();
        UUID assignedTo = ticket.assignedTo();
        java.time.Instant updatedAt = ticket.updatedAt();
        java.time.Instant resolvedAt = ticket.resolvedAt();
        return new TicketDto(
            ticket.id().toString(),
            ticket.reporterUuid().toString(),
            ticket.reporterName(),
            reportedUuid != null ? reportedUuid.toString() : null,
            ticket.reportedName(),
            ticket.category().name(),
            ticket.category().getDisplayName(),
            ticket.priority().name(),
            ticket.priority().getDisplayName(),
            ticket.priority().getColorHex(),
            ticket.status().name(),
            ticket.status().getDisplayName(),
            ticket.subject(),
            ticket.description(),
            assignedTo != null ? assignedTo.toString() : null,
            ticket.assignedToName(),
            ticket.createdAt().toEpochMilli(),
            updatedAt != null ? updatedAt.toEpochMilli() : null,
            resolvedAt != null ? resolvedAt.toEpochMilli() : null,
            ticket.resolutionNotes(),
            ticket.getAge()
        );
    }

    private static CommentDto toCommentDto(TicketComment comment) {
        UUID authorUuid = comment.authorUuid();
        return new CommentDto(
            comment.id().toString(),
            comment.ticketId().toString(),
            authorUuid != null ? authorUuid.toString() : null,
            comment.authorName(),
            comment.content(),
            comment.isInternal(),
            comment.createdAt().toEpochMilli()
        );
    }

    // ============================================================================
    // DTOs
    // ============================================================================

    public record ErrorResponse(String status, String message) {}

    public record CreateTicketRequest(
        @Nullable String reporterUuid,
        @Nullable String reporterName,
        @Nullable String category,
        @Nullable String subject,
        @Nullable String description,
        @Nullable String reportedUuid,
        @Nullable String reportedName
    ) {}

    public record UpdateTicketRequest(
        @Nullable String status,
        @Nullable String priority,
        @Nullable String resolutionNotes
    ) {}

    public record AssignTicketRequest(
        @Nullable String assigneeUuid,
        @Nullable String assigneeName
    ) {}

    public record AddCommentRequest(
        @Nullable String authorUuid,
        @Nullable String authorName,
        @Nullable String content,
        @Nullable Boolean isInternal
    ) {}

    public record TicketDto(
        String id,
        String reporterUuid,
        @Nullable String reporterName,
        @Nullable String reportedUuid,
        @Nullable String reportedName,
        String category,
        String categoryDisplay,
        String priority,
        String priorityDisplay,
        String priorityColor,
        String status,
        String statusDisplay,
        String subject,
        @Nullable String description,
        @Nullable String assignedTo,
        @Nullable String assignedToName,
        long createdAtMillis,
        @Nullable Long updatedAtMillis,
        @Nullable Long resolvedAtMillis,
        @Nullable String resolutionNotes,
        long ageMillis
    ) {}

    public record CommentDto(
        String id,
        String ticketId,
        @Nullable String authorUuid,
        @Nullable String authorName,
        String content,
        boolean isInternal,
        long createdAtMillis
    ) {}

    public record TicketListResponse(
        List<TicketDto> tickets,
        int page,
        int pageSize,
        int totalItems,
        int totalPages
    ) {}

    public record TicketDetailResponse(
        TicketDto ticket,
        List<CommentDto> comments
    ) {}

    public record TicketStatsResponse(
        int total,
        int open,
        int assigned,
        int inProgress,
        int resolved,
        int closed,
        long avgResolutionTimeMs
    ) {}
}
