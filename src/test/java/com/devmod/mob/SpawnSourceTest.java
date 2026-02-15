package com.devmod.mob;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SpawnSourceTest {

    // === shouldUseStructure ===

    @Test
    @DisplayName("STRUCTURE_ONLY always returns true regardless of quest type")
    void structureOnlyAlwaysTrue() {
        assertTrue(SpawnSource.STRUCTURE_ONLY.shouldUseStructure(null));
        assertTrue(SpawnSource.STRUCTURE_ONLY.shouldUseStructure("dungeon"));
        assertTrue(SpawnSource.STRUCTURE_ONLY.shouldUseStructure("random_quest"));
    }

    @Test
    @DisplayName("NATURAL_ONLY always returns false regardless of quest type")
    void naturalOnlyAlwaysFalse() {
        assertFalse(SpawnSource.NATURAL_ONLY.shouldUseStructure(null));
        assertFalse(SpawnSource.NATURAL_ONLY.shouldUseStructure("dungeon"));
        assertFalse(SpawnSource.NATURAL_ONLY.shouldUseStructure("boss"));
    }

    @Test
    @DisplayName("UNKNOWN always returns false regardless of quest type")
    void unknownAlwaysFalse() {
        assertFalse(SpawnSource.UNKNOWN.shouldUseStructure(null));
        assertFalse(SpawnSource.UNKNOWN.shouldUseStructure("boss"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("NATURAL_AND_STRUCTURE returns false for null/empty quest type")
    void naturalAndStructureFalseForNullOrEmpty(String questType) {
        assertFalse(SpawnSource.NATURAL_AND_STRUCTURE.shouldUseStructure(questType));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dungeon", "dungeon_run", "boss", "boss_fight", "lore", "elite", "raid"})
    @DisplayName("NATURAL_AND_STRUCTURE returns true for structure-preferred quest types")
    void naturalAndStructureTrueForStructureQuests(String questType) {
        assertTrue(SpawnSource.NATURAL_AND_STRUCTURE.shouldUseStructure(questType));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DUNGEON", "Boss", "ELITE", "Raid"})
    @DisplayName("NATURAL_AND_STRUCTURE is case-insensitive for quest types")
    void naturalAndStructureCaseInsensitive(String questType) {
        assertTrue(SpawnSource.NATURAL_AND_STRUCTURE.shouldUseStructure(questType));
    }

    @ParameterizedTest
    @ValueSource(strings = {"explore", "collect", "gather", "escort", "unknown"})
    @DisplayName("NATURAL_AND_STRUCTURE returns false for non-structure quest types")
    void naturalAndStructureFalseForNonStructureQuests(String questType) {
        assertFalse(SpawnSource.NATURAL_AND_STRUCTURE.shouldUseStructure(questType));
    }

    // === hasStructureSpawn ===

    @Test
    @DisplayName("hasStructureSpawn returns true for STRUCTURE_ONLY and NATURAL_AND_STRUCTURE")
    void hasStructureSpawn() {
        assertTrue(SpawnSource.STRUCTURE_ONLY.hasStructureSpawn());
        assertTrue(SpawnSource.NATURAL_AND_STRUCTURE.hasStructureSpawn());
        assertFalse(SpawnSource.NATURAL_ONLY.hasStructureSpawn());
        assertFalse(SpawnSource.UNKNOWN.hasStructureSpawn());
    }

    // === hasNaturalSpawn ===

    @Test
    @DisplayName("hasNaturalSpawn returns true for NATURAL_ONLY, NATURAL_AND_STRUCTURE, and UNKNOWN")
    void hasNaturalSpawn() {
        assertTrue(SpawnSource.NATURAL_ONLY.hasNaturalSpawn());
        assertTrue(SpawnSource.NATURAL_AND_STRUCTURE.hasNaturalSpawn());
        assertTrue(SpawnSource.UNKNOWN.hasNaturalSpawn());
        assertFalse(SpawnSource.STRUCTURE_ONLY.hasNaturalSpawn());
    }

    // === STRUCTURE_PREFERRED_QUEST_TYPES ===

    @Test
    @DisplayName("STRUCTURE_PREFERRED_QUEST_TYPES contains expected types")
    void structurePreferredQuestTypesContainsExpected() {
        assertTrue(SpawnSource.STRUCTURE_PREFERRED_QUEST_TYPES.contains("dungeon"));
        assertTrue(SpawnSource.STRUCTURE_PREFERRED_QUEST_TYPES.contains("dungeon_run"));
        assertTrue(SpawnSource.STRUCTURE_PREFERRED_QUEST_TYPES.contains("boss"));
        assertTrue(SpawnSource.STRUCTURE_PREFERRED_QUEST_TYPES.contains("boss_fight"));
        assertTrue(SpawnSource.STRUCTURE_PREFERRED_QUEST_TYPES.contains("lore"));
        assertTrue(SpawnSource.STRUCTURE_PREFERRED_QUEST_TYPES.contains("elite"));
        assertTrue(SpawnSource.STRUCTURE_PREFERRED_QUEST_TYPES.contains("raid"));
        assertEquals(7, SpawnSource.STRUCTURE_PREFERRED_QUEST_TYPES.size());
    }

    @Test
    @DisplayName("STRUCTURE_PREFERRED_QUEST_TYPES is immutable")
    void structurePreferredQuestTypesIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
            () -> SpawnSource.STRUCTURE_PREFERRED_QUEST_TYPES.add("new_type"));
    }

    // === Enum values ===

    @Test
    @DisplayName("SpawnSource has exactly 4 values")
    void enumHasFourValues() {
        assertEquals(4, SpawnSource.values().length);
    }
}
