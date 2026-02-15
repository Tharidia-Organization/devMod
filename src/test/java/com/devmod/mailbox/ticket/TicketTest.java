package com.devmod.mailbox.ticket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TicketTest {

    private final UUID reporterUuid = UUID.randomUUID();
    private final UUID reportedUuid = UUID.randomUUID();

    @Test
    @DisplayName("builder creates ticket with defaults")
    void builderDefaults() {
        Ticket ticket = Ticket.builder(reporterUuid, TicketCategory.BUG, "Test Bug")
            .build();

        assertNotNull(ticket.id());
        assertEquals(reporterUuid, ticket.reporterUuid());
        assertEquals(TicketCategory.BUG, ticket.category());
        assertEquals("Test Bug", ticket.subject());
        assertEquals(TicketStatus.OPEN, ticket.status());
        assertEquals(TicketPriority.NORMAL, ticket.priority()); // BUG default
        assertNull(ticket.reportedUuid());
        assertFalse(ticket.isPlayerReport());
        assertFalse(ticket.isAssigned());
        assertFalse(ticket.isResolved());
    }

    @Test
    @DisplayName("builder uses category default priority")
    void categoryDefaultPriority() {
        Ticket abuseTicket = Ticket.builder(reporterUuid, TicketCategory.ABUSE, "Bad player")
            .build();
        assertEquals(TicketPriority.HIGH, abuseTicket.priority());

        Ticket sugTicket = Ticket.builder(reporterUuid, TicketCategory.SUGGESTION, "Idea")
            .build();
        assertEquals(TicketPriority.LOW, sugTicket.priority());
    }

    @Test
    @DisplayName("isPlayerReport returns true when reportedUuid is set")
    void isPlayerReport() {
        Ticket ticket = Ticket.builder(reporterUuid, TicketCategory.ABUSE, "Report")
            .reportedPlayer(reportedUuid, "BadPlayer")
            .build();

        assertTrue(ticket.isPlayerReport());
    }

    @Test
    @DisplayName("withStatus creates copy with new status")
    void withStatus() {
        Ticket original = Ticket.builder(reporterUuid, TicketCategory.BUG, "Bug")
            .build();

        Ticket resolved = original.withStatus(TicketStatus.RESOLVED);
        assertEquals(TicketStatus.RESOLVED, resolved.status());
        assertEquals(TicketStatus.OPEN, original.status());
        assertNotNull(resolved.updatedAt());
        assertNotNull(resolved.resolvedAt());
    }

    @Test
    @DisplayName("withAssignment sets status to ASSIGNED")
    void withAssignment() {
        UUID assigneeUuid = UUID.randomUUID();
        Ticket ticket = Ticket.builder(reporterUuid, TicketCategory.BUG, "Bug")
            .build();

        Ticket assigned = ticket.withAssignment(assigneeUuid, "Mod1");
        assertEquals(TicketStatus.ASSIGNED, assigned.status());
        assertEquals(assigneeUuid, assigned.assignedTo());
        assertEquals("Mod1", assigned.assignedToName());
        assertTrue(assigned.isAssigned());
    }

    @Test
    @DisplayName("withPriority changes priority")
    void withPriority() {
        Ticket ticket = Ticket.builder(reporterUuid, TicketCategory.BUG, "Bug")
            .build();

        Ticket urgent = ticket.withPriority(TicketPriority.URGENT);
        assertEquals(TicketPriority.URGENT, urgent.priority());
        assertEquals(TicketPriority.NORMAL, ticket.priority());
    }

    @Test
    @DisplayName("withResolution marks as RESOLVED with notes")
    void withResolution() {
        Ticket ticket = Ticket.builder(reporterUuid, TicketCategory.BUG, "Bug")
            .build();

        Ticket resolved = ticket.withResolution("Fixed in v1.2");
        assertEquals(TicketStatus.RESOLVED, resolved.status());
        assertEquals("Fixed in v1.2", resolved.resolutionNotes());
        assertNotNull(resolved.resolvedAt());
    }

    @Test
    @DisplayName("isResolved returns true for RESOLVED and CLOSED")
    void isResolved() {
        Ticket resolved = Ticket.builder(reporterUuid, TicketCategory.BUG, "Bug")
            .status(TicketStatus.RESOLVED).build();
        assertTrue(resolved.isResolved());

        Ticket closed = Ticket.builder(reporterUuid, TicketCategory.BUG, "Bug")
            .status(TicketStatus.CLOSED).build();
        assertTrue(closed.isResolved());

        Ticket open = Ticket.builder(reporterUuid, TicketCategory.BUG, "Bug")
            .status(TicketStatus.OPEN).build();
        assertFalse(open.isResolved());
    }

    @Test
    @DisplayName("getAge returns positive value")
    void getAge() {
        Ticket ticket = Ticket.builder(reporterUuid, TicketCategory.BUG, "Bug")
            .createdAt(Instant.now().minusSeconds(60))
            .build();

        assertTrue(ticket.getAge() >= 50_000); // at least ~50 seconds in ms
    }
}
