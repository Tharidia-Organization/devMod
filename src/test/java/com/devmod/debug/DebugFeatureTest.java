package com.devmod.debug;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DebugFeature")
class DebugFeatureTest {

    @ParameterizedTest
    @EnumSource(DebugFeature.class)
    @DisplayName("All features have non-empty display name and description")
    void allFeaturesHaveMetadata(DebugFeature feature) {
        assertNotNull(feature.getDisplayName());
        assertFalse(feature.getDisplayName().isEmpty(), "Display name should not be empty for " + feature);
        assertNotNull(feature.getDescription());
        assertFalse(feature.getDescription().isEmpty(), "Description should not be empty for " + feature);
    }

    @ParameterizedTest
    @EnumSource(DebugFeature.class)
    @DisplayName("getId() returns lowercase name")
    void getIdIsLowercase(DebugFeature feature) {
        assertEquals(feature.name().toLowerCase(Locale.ROOT), feature.getId());
    }

    @Test
    @DisplayName("All feature IDs are unique")
    void allIdsUnique() {
        Set<String> ids = new HashSet<>();
        for (DebugFeature feature : DebugFeature.values()) {
            assertTrue(ids.add(feature.getId()), "Duplicate ID: " + feature.getId());
        }
    }

    @Test
    @DisplayName("Diagnostic features start with DIAG_ prefix")
    void diagnosticFeaturesHaveDiagPrefix() {
        int diagCount = 0;
        for (DebugFeature feature : DebugFeature.values()) {
            if (feature.name().startsWith("DIAG_")) {
                diagCount++;
                assertTrue(feature.getDisplayName().contains("Diagnostics"),
                    feature.name() + " should have 'Diagnostics' in display name");
            }
        }
        assertTrue(diagCount >= 6, "Expected at least 6 DIAG_ features");
    }

    @Test
    @DisplayName("Non-diagnostic features cover entity, world, and technical categories")
    void nonDiagnosticFeatureCategories() {
        // Check some known features exist
        assertNotNull(DebugFeature.ENTITY_PATHING);
        assertNotNull(DebugFeature.ENTITY_GOALS);
        assertNotNull(DebugFeature.POI);
        assertNotNull(DebugFeature.RAIDS);
        assertNotNull(DebugFeature.LIGHT);
        assertNotNull(DebugFeature.COLLISION);
        assertNotNull(DebugFeature.BLOCK_UPDATES);
    }

    @Test
    @DisplayName("Features removed for having no data source stay removed")
    void removedFeaturesAbsent() {
        // BEE_HIVES duplicated what BEES already draws and BREEZE never had a source; both would
        // be a toggle that renders nothing. Reintroducing either needs a payload chain first.
        Set<String> names = new HashSet<>();
        for (DebugFeature feature : DebugFeature.values()) {
            names.add(feature.name());
        }
        assertFalse(names.contains("BEE_HIVES"), "BEE_HIVES is covered by BEES");
        assertFalse(names.contains("BREEZE"), "BREEZE has no data source");
    }
}
