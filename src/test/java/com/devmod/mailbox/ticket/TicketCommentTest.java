package com.devmod.mailbox.ticket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TicketCommentTest {

    private final UUID ticketId = UUID.randomUUID();
    private final UUID authorUuid = UUID.randomUUID();

    @Test
    @DisplayName("playerComment creates non-internal comment")
    void playerComment() {
        TicketComment comment = TicketComment.playerComment(ticketId, authorUuid, "Player1", "Hello");

        assertEquals(ticketId, comment.ticketId());
        assertEquals(authorUuid, comment.authorUuid());
        assertEquals("Player1", comment.authorName());
        assertEquals("Hello", comment.content());
        assertFalse(comment.isInternal());
        assertNotNull(comment.id());
        assertNotNull(comment.createdAt());
    }

    @Test
    @DisplayName("staffComment creates non-internal comment")
    void staffComment() {
        TicketComment comment = TicketComment.staffComment(ticketId, authorUuid, "Staff1", "We're looking into it");

        assertFalse(comment.isInternal());
        assertEquals("Staff1", comment.authorName());
    }

    @Test
    @DisplayName("internalNote creates internal comment")
    void internalNote() {
        TicketComment note = TicketComment.internalNote(ticketId, authorUuid, "Mod1", "Player has history");

        assertTrue(note.isInternal());
        assertEquals("Player has history", note.content());
    }

    @Test
    @DisplayName("systemComment has null author uuid and System name")
    void systemComment() {
        TicketComment comment = TicketComment.systemComment(ticketId, "Ticket auto-escalated");

        assertNull(comment.authorUuid());
        assertEquals("System", comment.authorName());
        assertEquals("Ticket auto-escalated", comment.content());
        assertFalse(comment.isInternal());
    }
}
