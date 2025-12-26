package com.devmod.client.ui.radial.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroCategoryDirectTest {

    @Test
    @DisplayName("Macro categories count and indexing")
    void macroCategoryCountAndIndexing() {
        assertEquals(6, MacroCategory.count());
        assertEquals(MacroCategory.ANALYZE, MacroCategory.fromIndex(0));
        assertThrows(IndexOutOfBoundsException.class, () -> MacroCategory.fromIndex(6));
    }

    @Test
    @DisplayName("Macro categories handle adjacency with wrap")
    void macroCategoryAdjacencyWraps() {
        assertTrue(MacroCategory.ANALYZE.isAdjacentTo(MacroCategory.TELEMETRY));
        assertTrue(MacroCategory.ANALYZE.isAdjacentTo(MacroCategory.TOOLS));
    }
}
