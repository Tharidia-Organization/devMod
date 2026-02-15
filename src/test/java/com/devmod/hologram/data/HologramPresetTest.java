package com.devmod.hologram.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.devmod.TestBootstrap;

@DisplayName("HologramPreset")
class HologramPresetTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Nested
    @DisplayName("byId")
    class ById {
        @Test
        @DisplayName("finds WELCOME by id")
        void findsWelcome() {
            assertEquals(HologramPreset.WELCOME, HologramPreset.byId("preset_welcome"));
        }

        @Test
        @DisplayName("finds LEADERBOARD by id")
        void findsLeaderboard() {
            assertEquals(HologramPreset.LEADERBOARD, HologramPreset.byId("preset_leaderboard"));
        }

        @Test
        @DisplayName("finds ZONE_INFO by id")
        void findsZoneInfo() {
            assertEquals(HologramPreset.ZONE_INFO, HologramPreset.byId("preset_zone_info"));
        }

        @Test
        @DisplayName("finds ANNOUNCEMENT by id")
        void findsAnnouncement() {
            assertEquals(HologramPreset.ANNOUNCEMENT, HologramPreset.byId("preset_announcement"));
        }

        @Test
        @DisplayName("returns null for unknown id")
        void returnsNullForUnknown() {
            assertNull(HologramPreset.byId("nonexistent"));
        }
    }

    @Nested
    @DisplayName("isReservedId")
    class IsReservedId {
        @Test
        @DisplayName("preset_ prefix is reserved")
        void presetReserved() {
            assertTrue(HologramPreset.isReservedId("preset_anything"));
        }

        @Test
        @DisplayName("non-preset prefix is not reserved")
        void nonPresetNotReserved() {
            assertFalse(HologramPreset.isReservedId("my_hologram"));
        }

        @Test
        @DisplayName("null is not reserved")
        void nullNotReserved() {
            assertFalse(HologramPreset.isReservedId(null));
        }
    }

    @Nested
    @DisplayName("preset properties")
    class Properties {
        @Test
        @DisplayName("all presets have unique ids")
        void uniqueIds() {
            var ids = new java.util.HashSet<String>();
            for (HologramPreset preset : HologramPreset.values()) {
                assertTrue(ids.add(preset.getId()), "Duplicate id: " + preset.getId());
            }
        }

        @Test
        @DisplayName("all presets have non-empty labels")
        void nonEmptyLabels() {
            for (HologramPreset preset : HologramPreset.values()) {
                assertNotNull(preset.getLabel());
                assertFalse(preset.getLabel().isEmpty());
            }
        }

        @Test
        @DisplayName("all presets have non-empty default lines")
        void nonEmptyLines() {
            for (HologramPreset preset : HologramPreset.values()) {
                assertNotNull(preset.getDefaultLines());
                assertFalse(preset.getDefaultLines().isEmpty());
            }
        }

        @Test
        @DisplayName("all presets have a type")
        void hasType() {
            for (HologramPreset preset : HologramPreset.values()) {
                assertNotNull(preset.getType());
            }
        }

        @Test
        @DisplayName("all preset ids start with preset_")
        void idsStartWithPreset() {
            for (HologramPreset preset : HologramPreset.values()) {
                assertTrue(preset.getId().startsWith("preset_"),
                    "Preset " + preset.name() + " id should start with preset_");
            }
        }
    }

    @Nested
    @DisplayName("Offset")
    class OffsetTest {
        @Test
        @DisplayName("applyTo adds offset to origin")
        void applyToAddsOffset() {
            var offset = new HologramPreset.Offset(10, 5, -3);
            BlockPos origin = new BlockPos(100, 64, 200);
            BlockPos result = offset.applyTo(origin);
            assertEquals(new BlockPos(110, 69, 197), result);
        }

        @Test
        @DisplayName("zero offset returns same position")
        void zeroOffset() {
            var offset = new HologramPreset.Offset(0, 0, 0);
            BlockPos origin = new BlockPos(100, 64, 200);
            assertEquals(origin, offset.applyTo(origin));
        }
    }

    @Test
    @DisplayName("WELCOME uses DYNAMIC type")
    void welcomeIsDynamic() {
        assertEquals(HologramType.DYNAMIC, HologramPreset.WELCOME.getType());
    }

    @Test
    @DisplayName("LEADERBOARD uses LEADERBOARD type")
    void leaderboardType() {
        assertEquals(HologramType.LEADERBOARD, HologramPreset.LEADERBOARD.getType());
    }

    @Test
    @DisplayName("has exactly 4 presets")
    void presetCount() {
        assertEquals(4, HologramPreset.values().length);
    }
}
