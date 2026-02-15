package com.devmod.mob;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SpawnSourceDetector static mapping data and utility methods.
 * Does NOT test detect() which requires EntityType (Minecraft registry).
 */
class SpawnSourceDetectorTest {

    // === Static Mapping Data ===

    @Test
    @DisplayName("Structure-only mobs include vanilla bosses")
    void structureOnlyIncludesVanillaBosses() {
        Set<String> structureOnly = SpawnSourceDetector.getStructureOnlyMobs();
        assertTrue(structureOnly.contains("minecraft:elder_guardian"));
        assertTrue(structureOnly.contains("minecraft:shulker"));
        assertTrue(structureOnly.contains("minecraft:evoker"));
        assertTrue(structureOnly.contains("minecraft:vindicator"));
    }

    @Test
    @DisplayName("Structure-only mobs include modded bosses")
    void structureOnlyIncludesModdedBosses() {
        Set<String> structureOnly = SpawnSourceDetector.getStructureOnlyMobs();
        assertTrue(structureOnly.contains("twilightforest:lich"));
        assertTrue(structureOnly.contains("twilightforest:hydra"));
        assertTrue(structureOnly.contains("iceandfire:fire_dragon"));
        assertTrue(structureOnly.contains("cataclysm:ignis"));
    }

    @Test
    @DisplayName("getStructuresForMob returns structures for known structure-only mobs")
    void getStructuresForStructureOnlyMobs() {
        List<String> structures = SpawnSourceDetector.getStructuresForMob("minecraft:elder_guardian");
        assertFalse(structures.isEmpty());
        assertTrue(structures.contains("minecraft:ocean_monument"));
    }

    @Test
    @DisplayName("getStructuresForMob returns structures for known structure-also mobs")
    void getStructuresForStructureAlsoMobs() {
        List<String> structures = SpawnSourceDetector.getStructuresForMob("minecraft:blaze");
        assertFalse(structures.isEmpty());
        assertTrue(structures.contains("minecraft:fortress"));
    }

    @Test
    @DisplayName("getStructuresForMob returns empty for unknown mobs")
    void getStructuresForUnknownMobs() {
        List<String> structures = SpawnSourceDetector.getStructuresForMob("unknown:mob");
        assertTrue(structures.isEmpty());
    }

    // === Config Overrides ===

    @Test
    @DisplayName("registerOverride and clearOverrides work correctly")
    void overrideLifecycle() {
        String mobId = "test:override_mob";
        SpawnSourceDetector.SpawnSourceOverride override = new SpawnSourceDetector.SpawnSourceOverride(
            SpawnSource.STRUCTURE_ONLY, List.of("test:structure"), 1.0f
        );

        SpawnSourceDetector.registerOverride(mobId, override);
        // Can't easily verify without detect(), but ensure no exception
        SpawnSourceDetector.clearOverrides();
        // Should be able to register again after clear
        SpawnSourceDetector.registerOverride(mobId, override);
        SpawnSourceDetector.clearOverrides();
    }

    // === DetectionResult ===

    @Nested
    class DetectionResultTests {

        @Test
        @DisplayName("isConfident returns true for confidence >= 0.7")
        void isConfidentAboveThreshold() {
            SpawnSourceDetector.DetectionResult confident = new SpawnSourceDetector.DetectionResult(
                SpawnSource.NATURAL_ONLY, List.of(), null, 0.7f, SpawnSourceDetector.DetectionMethod.HARDCODED_MAPPING
            );
            assertTrue(confident.isConfident());

            SpawnSourceDetector.DetectionResult notConfident = new SpawnSourceDetector.DetectionResult(
                SpawnSource.UNKNOWN, List.of(), null, 0.69f, SpawnSourceDetector.DetectionMethod.FALLBACK
            );
            assertFalse(notConfident.isConfident());
        }

        @Test
        @DisplayName("describe contains spawn source and confidence")
        void describeContainsDetails() {
            SpawnSourceDetector.DetectionResult result = new SpawnSourceDetector.DetectionResult(
                SpawnSource.STRUCTURE_ONLY, List.of("minecraft:fortress"), null, 0.95f,
                SpawnSourceDetector.DetectionMethod.HARDCODED_MAPPING
            );

            String desc = result.describe();
            assertTrue(desc.contains("STRUCTURE_ONLY"));
            assertTrue(desc.contains("95%"));
            assertTrue(desc.contains("hardcoded_mapping"));
            assertTrue(desc.contains("minecraft:fortress"));
        }
    }

    // === DetectionMethod ===

    @Test
    @DisplayName("DetectionMethod has 5 values")
    void detectionMethodHasFiveValues() {
        assertEquals(5, SpawnSourceDetector.DetectionMethod.values().length);
    }

    // === SpawnSourceOverride record ===

    @Test
    @DisplayName("SpawnSourceOverride record fields accessible")
    void overrideRecordFieldsAccessible() {
        SpawnSourceDetector.SpawnSourceOverride override = new SpawnSourceDetector.SpawnSourceOverride(
            SpawnSource.NATURAL_AND_STRUCTURE, List.of("test:a", "test:b"), 0.8f
        );

        assertEquals(SpawnSource.NATURAL_AND_STRUCTURE, override.source());
        assertEquals(2, override.structures().size());
        assertEquals(0.8f, override.confidence());
    }

    // === Graveyard Mod Mappings ===

    @Test
    @DisplayName("Graveyard mobs have correct structure mappings")
    void graveyardMobMappings() {
        Set<String> structureOnly = SpawnSourceDetector.getStructureOnlyMobs();
        assertTrue(structureOnly.contains("graveyard:acolyte"));
        assertTrue(structureOnly.contains("graveyard:corrupted_pillager"));

        List<String> ghoulStructures = SpawnSourceDetector.getStructuresForMob("graveyard:ghoul");
        assertFalse(ghoulStructures.isEmpty());
        assertTrue(ghoulStructures.contains("graveyard:large_graveyard"));
    }

    // === Twilight Forest Mappings ===

    @Test
    @DisplayName("Twilight Forest bosses are all structure-only")
    void twilightForestBossesStructureOnly() {
        Set<String> structureOnly = SpawnSourceDetector.getStructureOnlyMobs();
        assertTrue(structureOnly.contains("twilightforest:lich"));
        assertTrue(structureOnly.contains("twilightforest:naga"));
        assertTrue(structureOnly.contains("twilightforest:hydra"));
        assertTrue(structureOnly.contains("twilightforest:ur_ghast"));
        assertTrue(structureOnly.contains("twilightforest:knight_phantom"));
        assertTrue(structureOnly.contains("twilightforest:minoshroom"));
        assertTrue(structureOnly.contains("twilightforest:alpha_yeti"));
        assertTrue(structureOnly.contains("twilightforest:snow_queen"));
    }
}
