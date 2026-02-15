package com.devmod.hologram.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HologramType")
class HologramTypeTest {

    @Test
    @DisplayName("STATIC does not support dynamic content")
    void staticNotDynamic() {
        assertFalse(HologramType.STATIC.supportsDynamicContent());
    }

    @Test
    @DisplayName("DYNAMIC supports dynamic content")
    void dynamicSupportsDynamic() {
        assertTrue(HologramType.DYNAMIC.supportsDynamicContent());
    }

    @Test
    @DisplayName("LEADERBOARD supports dynamic content")
    void leaderboardSupportsDynamic() {
        assertTrue(HologramType.LEADERBOARD.supportsDynamicContent());
    }

    @Test
    @DisplayName("ANNOUNCEMENT supports dynamic content")
    void announcementSupportsDynamic() {
        assertTrue(HologramType.ANNOUNCEMENT.supportsDynamicContent());
    }

    @Test
    @DisplayName("getSerializedName matches expected values")
    void serializedNames() {
        assertEquals("static", HologramType.STATIC.getSerializedName());
        assertEquals("dynamic", HologramType.DYNAMIC.getSerializedName());
        assertEquals("leaderboard", HologramType.LEADERBOARD.getSerializedName());
        assertEquals("announcement", HologramType.ANNOUNCEMENT.getSerializedName());
    }

    @Test
    @DisplayName("has exactly 4 values")
    void valueCount() {
        assertEquals(4, HologramType.values().length);
    }
}
