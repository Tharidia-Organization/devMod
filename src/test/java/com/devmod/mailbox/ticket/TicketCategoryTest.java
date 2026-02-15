package com.devmod.mailbox.ticket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketCategoryTest {

    @Test
    @DisplayName("getDefaultPriority returns expected priority per category")
    void getDefaultPriority() {
        assertEquals(TicketPriority.HIGH, TicketCategory.ABUSE.getDefaultPriority());
        assertEquals(TicketPriority.NORMAL, TicketCategory.BUG.getDefaultPriority());
        assertEquals(TicketPriority.LOW, TicketCategory.SUGGESTION.getDefaultPriority());
        assertEquals(TicketPriority.LOW, TicketCategory.QUESTION.getDefaultPriority());
        assertEquals(TicketPriority.NORMAL, TicketCategory.OTHER.getDefaultPriority());
    }

    @Test
    @DisplayName("displayName and description are populated")
    void displayNameAndDescription() {
        for (TicketCategory cat : TicketCategory.values()) {
            assertNotNull(cat.getDisplayName());
            assertFalse(cat.getDisplayName().isEmpty());
            assertNotNull(cat.getDescription());
            assertFalse(cat.getDescription().isEmpty());
        }
    }
}
