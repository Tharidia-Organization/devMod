package com.devmod.arena.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

class TemplateSuggestionTest {

    @Test
    void compatibleSuggestion() {
        TemplateSuggestion suggestion = TemplateSuggestion.compatible(
            "t1", "Arena One", 64, "minecraft:plains", "RECTANGULAR",
            true, false, 4, 85.5, Map.of("size", 40.0, "biome", 45.5), true);

        assertEquals("t1", suggestion.templateId());
        assertEquals("Arena One", suggestion.templateName());
        assertEquals(64, suggestion.size());
        assertTrue(suggestion.isCompatible());
        assertTrue(suggestion.isSelected());
        assertNull(suggestion.incompatibilityReason());
        assertEquals(85.5, suggestion.compatibilityScore());
    }

    @Test
    void incompatibleSuggestion() {
        TemplateSuggestion suggestion = TemplateSuggestion.incompatible(
            "t2", "Arena Two", 32, "minecraft:desert", "CIRCULAR",
            false, true, 2, "Too small for boss");

        assertFalse(suggestion.isCompatible());
        assertFalse(suggestion.isSelected());
        assertEquals("Too small for boss", suggestion.incompatibilityReason());
        assertEquals(0.0, suggestion.compatibilityScore());
    }

    @Test
    void displayStringCompatible() {
        TemplateSuggestion s = TemplateSuggestion.compatible(
            "t1", "Arena", 64, "plains", "RECT", true, false, 4, 90.0, Map.of(), false);
        String display = s.displayString();
        assertTrue(display.contains("Arena"));
        assertTrue(display.contains("64"));
        assertTrue(display.contains("Score:"));
    }

    @Test
    void displayStringIncompatible() {
        TemplateSuggestion s = TemplateSuggestion.incompatible(
            "t1", "Arena", 64, "plains", "RECT", true, false, 4, "Reason");
        String display = s.displayString();
        assertTrue(display.contains("Reason"));
    }
}
