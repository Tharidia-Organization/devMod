package com.devmod.mailbox.ticket;

import java.time.Instant;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Represents a comment on a support ticket.
 */
public record TicketComment(
    UUID id,
    UUID ticketId,
    @Nullable UUID authorUuid,
    @Nullable String authorName,
    String content,
    boolean isInternal,
    Instant createdAt
) {
    /**
     * Create a new player comment.
     */
    public static TicketComment playerComment(UUID ticketId, UUID authorUuid, String authorName, String content) {
        return new TicketComment(
            UUID.randomUUID(),
            ticketId,
            authorUuid,
            authorName,
            content,
            false,
            Instant.now()
        );
    }

    /**
     * Create a new staff comment (visible to players).
     */
    public static TicketComment staffComment(UUID ticketId, @Nullable UUID authorUuid, String authorName, String content) {
        return new TicketComment(
            UUID.randomUUID(),
            ticketId,
            authorUuid,
            authorName,
            content,
            false,
            Instant.now()
        );
    }

    /**
     * Create an internal staff note (not visible to players).
     */
    public static TicketComment internalNote(UUID ticketId, @Nullable UUID authorUuid, String authorName, String content) {
        return new TicketComment(
            UUID.randomUUID(),
            ticketId,
            authorUuid,
            authorName,
            content,
            true,
            Instant.now()
        );
    }

    /**
     * Create a system-generated comment.
     */
    public static TicketComment systemComment(UUID ticketId, String content) {
        return new TicketComment(
            UUID.randomUUID(),
            ticketId,
            null,
            "System",
            content,
            false,
            Instant.now()
        );
    }
}
