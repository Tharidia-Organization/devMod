package com.devmod.client.ui.radial.input;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;

import com.devmod.client.ui.radial.RadialAction;
import com.devmod.client.ui.radial.RadialCategory;
import com.devmod.client.ui.radial.RadialMenuItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadialSearchHandlerDirectTest {

    @Test
    @DisplayName("Search ranks prefix matches higher than fuzzy matches")
    void searchRanksPrefixMatches() {
        RadialCategory root = new RadialCategory("root", "Root", 0xFFFFFFFF, "*");
        RadialMenuItem dashboard = new RadialMenuItem("Dashboard", new TestAction("Dashboard", "open dashboard"), "*", null);
        RadialMenuItem debug = new RadialMenuItem("Debug", new TestAction("Debug", "debug tools"), "*", null);
        root.addItems(dashboard, debug);

        List<RadialSearchHandler.SearchResult> results = RadialSearchHandler.search("da", List.of(root));
        assertEquals("Dashboard", results.get(0).getItem().getName());
    }

    @Test
    @DisplayName("Fuzzy matching finds ordered character sequences")
    void fuzzyMatchingFindsSequences() {
        int score = RadialSearchHandler.calculateFuzzyScore("dbg", "debug");
        assertTrue(score > 0);
    }

    @Test
    @DisplayName("Best match returns null when no results")
    void bestMatchReturnsNullWhenEmpty() {
        RadialCategory root = new RadialCategory("root", "Root", 0xFFFFFFFF, "*");
        RadialSearchHandler.SearchResult result = RadialSearchHandler.findBest("zzz", List.of(root));
        assertEquals(null, result);
    }

    private static class TestAction extends RadialAction {
        private final String name;
        private final String description;

        private TestAction(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public void execute() {
            // no-op
        }

        @Override
        public Component getLabel() {
            return Component.literal(name);
        }

        @Override
        public String getDescription() {
            return description;
        }
    }
}
