package com.devmod.mailbox.ticket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketStatusTest {

    @Test
    @DisplayName("canTransitionTo from OPEN allows all expected targets")
    void canTransitionFromOpen() {
        assertTrue(TicketStatus.OPEN.canTransitionTo(TicketStatus.ASSIGNED));
        assertTrue(TicketStatus.OPEN.canTransitionTo(TicketStatus.IN_PROGRESS));
        assertTrue(TicketStatus.OPEN.canTransitionTo(TicketStatus.RESOLVED));
        assertTrue(TicketStatus.OPEN.canTransitionTo(TicketStatus.CLOSED));
        assertFalse(TicketStatus.OPEN.canTransitionTo(TicketStatus.OPEN));
    }

    @Test
    @DisplayName("canTransitionTo from CLOSED only allows OPEN")
    void canTransitionFromClosed() {
        assertTrue(TicketStatus.CLOSED.canTransitionTo(TicketStatus.OPEN));
        assertFalse(TicketStatus.CLOSED.canTransitionTo(TicketStatus.ASSIGNED));
        assertFalse(TicketStatus.CLOSED.canTransitionTo(TicketStatus.RESOLVED));
    }

    @Test
    @DisplayName("isTerminal returns true only for CLOSED")
    void isTerminal() {
        assertTrue(TicketStatus.CLOSED.isTerminal());
        assertFalse(TicketStatus.OPEN.isTerminal());
        assertFalse(TicketStatus.RESOLVED.isTerminal());
    }

    @Test
    @DisplayName("isActive returns true for OPEN, ASSIGNED, IN_PROGRESS")
    void isActive() {
        assertTrue(TicketStatus.OPEN.isActive());
        assertTrue(TicketStatus.ASSIGNED.isActive());
        assertTrue(TicketStatus.IN_PROGRESS.isActive());
        assertFalse(TicketStatus.RESOLVED.isActive());
        assertFalse(TicketStatus.CLOSED.isActive());
    }

    @Test
    @DisplayName("displayName and description are set")
    void displayNameAndDescription() {
        assertEquals("Open", TicketStatus.OPEN.getDisplayName());
        assertFalse(TicketStatus.OPEN.getDescription().isEmpty());
    }
}
