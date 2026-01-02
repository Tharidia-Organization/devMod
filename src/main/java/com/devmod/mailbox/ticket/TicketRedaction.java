package com.devmod.mailbox.ticket;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * P1-006: Field-level redaction for ticket views.
 *
 * Applies redaction rules based on viewer permissions to ensure:
 * - Players only see public information about their own tickets
 * - Moderators see full ticket details but not other staff internal notes
 * - Admins see everything
 * - Reported players never see reporter identity
 *
 * <p>Redaction Rules:
 * <ul>
 *   <li>REPORTER viewing own ticket: Full access except staff internal notes</li>
 *   <li>REPORTED_PLAYER viewing ticket about them: Reporter identity hidden</li>
 *   <li>MODERATOR: Full access to assigned tickets, limited for others</li>
 *   <li>ADMIN: Full unrestricted access</li>
 * </ul>
 */
public final class TicketRedaction {

    private static final String REDACTED = "[REDACTED]";

    // ============================================================================
    // VIEWER ROLE
    // ============================================================================

    /**
     * Role of the person viewing the ticket.
     */
    public enum ViewerRole {
        /** The ticket reporter viewing their own ticket */
        REPORTER,
        /** The reported player (in abuse cases) */
        REPORTED_PLAYER,
        /** A moderator/staff member */
        MODERATOR,
        /** An admin with full access */
        ADMIN,
        /** Anonymous/unauthenticated viewer (minimal access) */
        ANONYMOUS
    }

    /**
     * Context for redaction decisions.
     */
    public record ViewerContext(
        UUID viewerUuid,
        String viewerName,
        ViewerRole role,
        Set<UUID> assignedTickets
    ) {
        public ViewerContext {
            Objects.requireNonNull(viewerUuid, "viewerUuid");
            Objects.requireNonNull(role, "role");
            assignedTickets = assignedTickets != null ? Set.copyOf(assignedTickets) : Set.of();
        }

        public static ViewerContext reporter(UUID uuid, String name) {
            return new ViewerContext(uuid, name, ViewerRole.REPORTER, Set.of());
        }

        public static ViewerContext moderator(UUID uuid, String name, Set<UUID> assignedTickets) {
            return new ViewerContext(uuid, name, ViewerRole.MODERATOR, assignedTickets);
        }

        public static ViewerContext admin(UUID uuid, String name) {
            return new ViewerContext(uuid, name, ViewerRole.ADMIN, Set.of());
        }

        public boolean isAssignedTo(UUID ticketId) {
            return assignedTickets.contains(ticketId);
        }
    }

    // ============================================================================
    // REDACTED VIEWS
    // ============================================================================

    /**
     * A redacted view of a ticket with sensitive fields masked.
     */
    public record RedactedTicket(
        UUID id,
        @Nullable String reporterName,
        boolean reporterVisible,
        @Nullable String reportedName,
        boolean reportedVisible,
        TicketCategory category,
        TicketPriority priority,
        TicketStatus status,
        String subject,
        @Nullable String description,
        @Nullable String assignedToName,
        boolean assigneeVisible,
        Instant createdAt,
        @Nullable Instant resolvedAt,
        @Nullable String resolutionNotes,
        boolean resolutionVisible
    ) {
        public static RedactedTicket fromTicket(Ticket ticket, ViewerContext ctx) {
            RedactionDecision decision = decide(ticket, ctx);

            return new RedactedTicket(
                ticket.id(),
                decision.showReporter() ? ticket.reporterName() : REDACTED,
                decision.showReporter(),
                decision.showReported() ? ticket.reportedName() : REDACTED,
                decision.showReported(),
                ticket.category(),
                ticket.priority(),
                ticket.status(),
                ticket.subject(),
                decision.showDescription() ? ticket.description() : REDACTED,
                decision.showAssignee() ? ticket.assignedToName() : REDACTED,
                decision.showAssignee(),
                ticket.createdAt(),
                ticket.resolvedAt(),
                decision.showResolution() ? ticket.resolutionNotes() : REDACTED,
                decision.showResolution()
            );
        }
    }

    /**
     * A redacted view of a ticket comment.
     */
    public record RedactedComment(
        UUID id,
        UUID ticketId,
        @Nullable String authorName,
        boolean authorVisible,
        String content,
        boolean contentVisible,
        boolean isInternal,
        Instant createdAt
    ) {
        public static RedactedComment fromComment(TicketComment comment, ViewerContext ctx, Ticket ticket) {
            CommentRedactionDecision decision = decideComment(comment, ctx, ticket);

            return new RedactedComment(
                comment.id(),
                comment.ticketId(),
                decision.showAuthor() ? comment.authorName() : REDACTED,
                decision.showAuthor(),
                decision.showContent() ? comment.content() : REDACTED,
                decision.showContent(),
                comment.isInternal(),
                comment.createdAt()
            );
        }
    }

    // ============================================================================
    // REDACTION DECISIONS
    // ============================================================================

    /**
     * Decision record for ticket field visibility.
     */
    public record RedactionDecision(
        boolean showReporter,
        boolean showReported,
        boolean showDescription,
        boolean showAssignee,
        boolean showResolution,
        boolean showInternalNotes
    ) {
        public static final RedactionDecision FULL_ACCESS = new RedactionDecision(
            true, true, true, true, true, true
        );

        public static final RedactionDecision MINIMAL = new RedactionDecision(
            false, false, false, false, false, false
        );
    }

    /**
     * Decision record for comment field visibility.
     */
    public record CommentRedactionDecision(
        boolean showAuthor,
        boolean showContent,
        boolean visible
    ) {}

    /**
     * Decide what fields should be visible for a ticket.
     */
    public static RedactionDecision decide(Ticket ticket, ViewerContext ctx) {
        return switch (ctx.role()) {
            case ADMIN -> RedactionDecision.FULL_ACCESS;

            case MODERATOR -> {
                // Moderators see everything except other staff's internal context
                // if not assigned to this ticket
                boolean isAssigned = ctx.isAssignedTo(ticket.id()) ||
                    (ticket.assignedTo() != null && ticket.assignedTo().equals(ctx.viewerUuid()));
                yield new RedactionDecision(
                    true, true, true, true, true, isAssigned
                );
            }

            case REPORTER -> {
                // Reporter sees their own ticket details
                boolean isOwner = ticket.reporterUuid().equals(ctx.viewerUuid());
                if (!isOwner) {
                    yield RedactionDecision.MINIMAL;
                }
                yield new RedactionDecision(
                    true,   // showReporter (self)
                    true,   // showReported
                    true,   // showDescription
                    true,   // showAssignee
                    true,   // showResolution
                    false   // showInternalNotes - never for players
                );
            }

            case REPORTED_PLAYER -> {
                // Reported player NEVER sees reporter identity
                boolean isAboutMe = ticket.reportedUuid() != null &&
                    ticket.reportedUuid().equals(ctx.viewerUuid());
                if (!isAboutMe) {
                    yield RedactionDecision.MINIMAL;
                }
                yield new RedactionDecision(
                    false,  // showReporter - NEVER
                    true,   // showReported (self)
                    true,   // showDescription
                    true,   // showAssignee
                    true,   // showResolution
                    false   // showInternalNotes
                );
            }

            case ANONYMOUS -> RedactionDecision.MINIMAL;
        };
    }

    /**
     * Decide what should be visible for a comment.
     */
    public static CommentRedactionDecision decideComment(
            TicketComment comment, ViewerContext ctx, Ticket ticket) {

        // Internal notes only visible to staff
        if (comment.isInternal()) {
            boolean canSeeInternal = ctx.role() == ViewerRole.ADMIN ||
                (ctx.role() == ViewerRole.MODERATOR && ctx.isAssignedTo(ticket.id()));
            return new CommentRedactionDecision(canSeeInternal, canSeeInternal, canSeeInternal);
        }

        // Public comments
        return switch (ctx.role()) {
            case ADMIN, MODERATOR -> new CommentRedactionDecision(true, true, true);

            case REPORTER -> {
                boolean isOwner = ticket.reporterUuid().equals(ctx.viewerUuid());
                yield new CommentRedactionDecision(isOwner, isOwner, isOwner);
            }

            case REPORTED_PLAYER -> {
                // Can see comment content but not who wrote it if it might reveal reporter
                boolean isAboutMe = ticket.reportedUuid() != null &&
                    ticket.reportedUuid().equals(ctx.viewerUuid());
                // Hide author if it could be the reporter
                boolean hideAuthor = comment.authorUuid() != null &&
                    comment.authorUuid().equals(ticket.reporterUuid());
                yield new CommentRedactionDecision(!hideAuthor && isAboutMe, isAboutMe, isAboutMe);
            }

            case ANONYMOUS -> new CommentRedactionDecision(false, false, false);
        };
    }

    // ============================================================================
    // BATCH REDACTION
    // ============================================================================

    /**
     * Redact a list of tickets for a viewer.
     */
    public static List<RedactedTicket> redactTickets(List<Ticket> tickets, ViewerContext ctx) {
        return tickets.stream()
            .map(t -> RedactedTicket.fromTicket(t, ctx))
            .collect(Collectors.toList());
    }

    /**
     * Redact a list of comments for a viewer.
     */
    public static List<RedactedComment> redactComments(
            List<TicketComment> comments, ViewerContext ctx, Ticket ticket) {
        return comments.stream()
            .map(c -> RedactedComment.fromComment(c, ctx, ticket))
            .filter(rc -> rc.contentVisible()) // Filter out completely hidden comments
            .collect(Collectors.toList());
    }

    /**
     * Filter comments to only those visible to the viewer.
     * Unlike redactComments, this returns original TicketComment objects
     * but only includes those the viewer can see.
     */
    public static List<TicketComment> filterComments(
            List<TicketComment> comments, ViewerContext ctx, Ticket ticket) {
        return comments.stream()
            .filter(c -> {
                if (c.isInternal()) {
                    return ctx.role() == ViewerRole.ADMIN ||
                        (ctx.role() == ViewerRole.MODERATOR && ctx.isAssignedTo(ticket.id()));
                }
                return ctx.role() != ViewerRole.ANONYMOUS;
            })
            .collect(Collectors.toList());
    }

    // ============================================================================
    // UTILITY
    // ============================================================================

    /**
     * Determine the viewer role for a given player viewing a ticket.
     */
    public static ViewerRole determineRole(UUID viewerUuid, Ticket ticket, boolean isModerator, boolean isAdmin) {
        if (isAdmin) {
            return ViewerRole.ADMIN;
        }
        if (isModerator) {
            return ViewerRole.MODERATOR;
        }
        if (ticket.reporterUuid().equals(viewerUuid)) {
            return ViewerRole.REPORTER;
        }
        if (ticket.reportedUuid() != null && ticket.reportedUuid().equals(viewerUuid)) {
            return ViewerRole.REPORTED_PLAYER;
        }
        return ViewerRole.ANONYMOUS;
    }

    private TicketRedaction() {} // Static utility class
}
