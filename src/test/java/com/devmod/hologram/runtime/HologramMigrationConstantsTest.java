package com.devmod.hologram.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HologramMigration constants")
class HologramMigrationConstantsTest {

    @Test
    @DisplayName("LEGACY_TAG is non-empty")
    void legacyTag() {
        assertNotNull(HologramMigration.LEGACY_TAG);
        assertFalse(HologramMigration.LEGACY_TAG.isEmpty());
    }

    @Test
    @DisplayName("LEGACY_TYPE_PREFIX is non-empty")
    void legacyTypePrefix() {
        assertNotNull(HologramMigration.LEGACY_TYPE_PREFIX);
        assertFalse(HologramMigration.LEGACY_TYPE_PREFIX.isEmpty());
    }

    @Test
    @DisplayName("MIGRATED_TAG is non-empty")
    void migratedTag() {
        assertNotNull(HologramMigration.MIGRATED_TAG);
        assertFalse(HologramMigration.MIGRATED_TAG.isEmpty());
    }

    @Test
    @DisplayName("LEGACY_TAG and MIGRATED_TAG are different")
    void tagsDiffer() {
        assertNotEquals(HologramMigration.LEGACY_TAG, HologramMigration.MIGRATED_TAG);
    }

    @Test
    @DisplayName("tags contain devmod prefix")
    void tagsContainPrefix() {
        assertTrue(HologramMigration.LEGACY_TAG.contains("devmod"));
        assertTrue(HologramMigration.MIGRATED_TAG.contains("devmod"));
        assertTrue(HologramMigration.LEGACY_TYPE_PREFIX.contains("devmod"));
    }
}
