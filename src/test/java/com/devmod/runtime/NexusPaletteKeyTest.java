package com.devmod.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class NexusPaletteKeyTest {

    @Test
    @DisplayName("Key.fromId resolves known keys")
    void fromIdResolvesKnownKeys() {
        assertEquals(NexusPalette.Key.FLOOR, NexusPalette.Key.fromId("floor"));
        assertEquals(NexusPalette.Key.WALL, NexusPalette.Key.fromId("wall"));
        assertEquals(NexusPalette.Key.AIR, NexusPalette.Key.fromId("air"));
        assertEquals(NexusPalette.Key.COMBAT_RING, NexusPalette.Key.fromId("combat_ring"));
        assertEquals(NexusPalette.Key.PAD_BLUE, NexusPalette.Key.fromId("pad_blue"));
    }

    @Test
    @DisplayName("Key.fromId is case-insensitive")
    void fromIdIsCaseInsensitive() {
        assertEquals(NexusPalette.Key.FLOOR, NexusPalette.Key.fromId("FLOOR"));
        assertEquals(NexusPalette.Key.WALL, NexusPalette.Key.fromId("Wall"));
        assertEquals(NexusPalette.Key.CORE, NexusPalette.Key.fromId("CORE"));
    }

    @Test
    @DisplayName("Key.fromId trims whitespace")
    void fromIdTrimsWhitespace() {
        assertEquals(NexusPalette.Key.FLOOR, NexusPalette.Key.fromId("  floor  "));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"nonexistent", "invalid_key", "floor_extra"})
    @DisplayName("Key.fromId returns null for unknown/null/empty ids")
    void fromIdReturnsNullForUnknown(String id) {
        assertNull(NexusPalette.Key.fromId(id));
    }

    @Test
    @DisplayName("Key.id() returns correct string id")
    void keyIdReturnsCorrectString() {
        assertEquals("floor", NexusPalette.Key.FLOOR.id());
        assertEquals("floor_alt", NexusPalette.Key.FLOOR_ALT.id());
        assertEquals("combat_ring", NexusPalette.Key.COMBAT_RING.id());
        assertEquals("pad_blue", NexusPalette.Key.PAD_BLUE.id());
    }

    @Test
    @DisplayName("All Key values have unique ids")
    void allKeysHaveUniqueIds() {
        NexusPalette.Key[] keys = NexusPalette.Key.values();
        long uniqueIds = java.util.Arrays.stream(keys)
            .map(NexusPalette.Key::id)
            .distinct()
            .count();
        assertEquals(keys.length, uniqueIds);
    }

    @Test
    @DisplayName("All Key ids are roundtrippable via fromId")
    void allKeysRoundTrip() {
        for (NexusPalette.Key key : NexusPalette.Key.values()) {
            assertEquals(key, NexusPalette.Key.fromId(key.id()),
                "Key " + key + " should roundtrip via id '" + key.id() + "'");
        }
    }

    @Test
    @DisplayName("Key enum has expected number of entries")
    void keyEnumSize() {
        // Verify the Key enum has all the palette entries
        assertTrue(NexusPalette.Key.values().length >= 30,
            "NexusPalette.Key should have at least 30 entries");
    }
}
