package com.devmod.mailbox.ticket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketPriorityTest {

    @Test
    @DisplayName("isHigherThan compares levels correctly")
    void isHigherThan() {
        assertTrue(TicketPriority.URGENT.isHigherThan(TicketPriority.HIGH));
        assertTrue(TicketPriority.HIGH.isHigherThan(TicketPriority.NORMAL));
        assertTrue(TicketPriority.NORMAL.isHigherThan(TicketPriority.LOW));
        assertFalse(TicketPriority.LOW.isHigherThan(TicketPriority.NORMAL));
        assertFalse(TicketPriority.HIGH.isHigherThan(TicketPriority.HIGH));
    }

    @Test
    @DisplayName("level values are ordered")
    void levelOrdering() {
        assertTrue(TicketPriority.LOW.getLevel() < TicketPriority.NORMAL.getLevel());
        assertTrue(TicketPriority.NORMAL.getLevel() < TicketPriority.HIGH.getLevel());
        assertTrue(TicketPriority.HIGH.getLevel() < TicketPriority.URGENT.getLevel());
    }

    @Test
    @DisplayName("getColorHex returns valid hex string")
    void colorHex() {
        for (TicketPriority p : TicketPriority.values()) {
            String hex = p.getColorHex();
            assertNotNull(hex);
            assertTrue(hex.startsWith("#"));
            assertEquals(7, hex.length());
        }
    }

    @Test
    @DisplayName("displayName is set for all values")
    void displayName() {
        for (TicketPriority p : TicketPriority.values()) {
            assertNotNull(p.getDisplayName());
            assertFalse(p.getDisplayName().isEmpty());
        }
    }
}
