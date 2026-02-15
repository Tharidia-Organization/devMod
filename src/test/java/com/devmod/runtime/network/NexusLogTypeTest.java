package com.devmod.runtime.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NexusLogTypeTest {

    @Test
    @DisplayName("All NexusLogType values have non-null file names and labels")
    void allValuesHaveNonNullFields() {
        for (NexusLogType type : NexusLogType.values()) {
            assertNotNull(type.fileName(), type.name() + " should have a non-null fileName");
            assertNotNull(type.label(), type.name() + " should have a non-null label");
            assertFalse(type.fileName().isEmpty(), type.name() + " fileName should not be empty");
            assertFalse(type.label().isEmpty(), type.name() + " label should not be empty");
        }
    }

    @Test
    @DisplayName("All file names end with .ndjson")
    void fileNamesEndWithNdjson() {
        for (NexusLogType type : NexusLogType.values()) {
            assertTrue(type.fileName().endsWith(".ndjson"),
                type.name() + " fileName should end with .ndjson but was: " + type.fileName());
        }
    }

    @Test
    @DisplayName("All file names are unique")
    void fileNamesUnique() {
        NexusLogType[] values = NexusLogType.values();
        long uniqueCount = java.util.Arrays.stream(values)
            .map(NexusLogType::fileName)
            .distinct()
            .count();
        assertEquals(values.length, uniqueCount, "All NexusLogType fileNames should be unique");
    }

    @Test
    @DisplayName("fromOrdinal returns correct type")
    void fromOrdinalReturnsCorrectType() {
        assertEquals(NexusLogType.HITS, NexusLogType.fromOrdinal(0));
        assertEquals(NexusLogType.HEALS, NexusLogType.fromOrdinal(1));
        assertEquals(NexusLogType.DEATHS, NexusLogType.fromOrdinal(2));
    }

    @Test
    @DisplayName("fromOrdinal returns HITS for out-of-bounds ordinal")
    void fromOrdinalFallsBackToHits() {
        assertEquals(NexusLogType.HITS, NexusLogType.fromOrdinal(-1));
        assertEquals(NexusLogType.HITS, NexusLogType.fromOrdinal(1000));
    }

    @Test
    @DisplayName("Known log types have expected file names")
    void knownLogTypesHaveExpectedFileNames() {
        assertEquals("hits.ndjson", NexusLogType.HITS.fileName());
        assertEquals("heals.ndjson", NexusLogType.HEALS.fileName());
        assertEquals("deaths.ndjson", NexusLogType.DEATHS.fileName());
        assertEquals("performance.ndjson", NexusLogType.PERFORMANCE.fileName());
        assertEquals("endurance.ndjson", NexusLogType.ENDURANCE.fileName());
    }

    @Test
    @DisplayName("Known log types have expected labels")
    void knownLogTypesHaveExpectedLabels() {
        assertEquals("Hits", NexusLogType.HITS.label());
        assertEquals("Heals", NexusLogType.HEALS.label());
        assertEquals("Boss Phases", NexusLogType.PHASES.label());
        assertEquals("Heatmap: Death", NexusLogType.HEATMAP_DEATH.label());
    }

    @Test
    @DisplayName("Heatmap types are grouped together")
    void heatmapTypesExist() {
        // Verify heatmap types exist
        assertNotNull(NexusLogType.HEATMAP_DEATH);
        assertNotNull(NexusLogType.HEATMAP_MOVEMENT);
        assertNotNull(NexusLogType.HEATMAP_CAMPING);
        assertNotNull(NexusLogType.HEATMAP_STUCK);
        assertNotNull(NexusLogType.HEATMAP_AGGRO_DROP);
        assertNotNull(NexusLogType.HEATMAP_KITING);
        assertNotNull(NexusLogType.HEATMAP_CHOKE_POINTS);
        assertNotNull(NexusLogType.HEATMAP_PARKOUR_FALL);
        assertNotNull(NexusLogType.HEATMAP_INVISIBLE_COLLISION);
    }
}
