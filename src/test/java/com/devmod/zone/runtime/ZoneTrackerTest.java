package com.devmod.zone.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.TestBootstrap;

/**
 * Unit tests for ZoneTracker: player state tracking, zone queries, and cleanup.
 *
 * Tests the state management aspects that do not require a MinecraftServer/ServerLevel.
 */
@DisplayName("ZoneTracker")
class ZoneTrackerTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    private static final ZoneTracker tracker = ZoneTracker.INSTANCE;

    @BeforeEach
    void reset() {
        tracker.clearAll();
    }

    // ========================================================================
    // State Management
    // ========================================================================

    @Nested
    @DisplayName("State Management")
    class StateTests {

        @Test
        @DisplayName("getCurrentZone returns null for untracked player")
        void untrackedPlayerReturnsNull() {
            UUID playerId = UUID.randomUUID();
            assertNull(tracker.getCurrentZone(playerId));
        }

        @Test
        @DisplayName("getCurrentZoneId returns null for untracked player")
        void untrackedPlayerIdReturnsNull() {
            UUID playerId = UUID.randomUUID();
            assertNull(tracker.getCurrentZoneId(playerId));
        }

        @Test
        @DisplayName("isInZone returns false for untracked player")
        void untrackedPlayerNotInZone() {
            UUID playerId = UUID.randomUUID();
            assertFalse(tracker.isInZone(playerId, "hub"));
        }
    }

    // ========================================================================
    // Cleanup
    // ========================================================================

    @Nested
    @DisplayName("Cleanup")
    class CleanupTests {

        @Test
        @DisplayName("clearAll resets tracked player count to zero")
        void clearAllResetsCount() {
            tracker.clearAll();
            assertEquals(0, tracker.getTrackedPlayerCount());
        }

        @Test
        @DisplayName("clear removes specific player state")
        void clearRemovesPlayer() {
            UUID playerId = UUID.randomUUID();
            // clear should not throw even for untracked player
            assertDoesNotThrow(() -> tracker.clear(playerId));
        }

        @Test
        @DisplayName("getTrackedPlayerCount returns zero initially")
        void initialCountZero() {
            assertEquals(0, tracker.getTrackedPlayerCount());
        }
    }

    // ========================================================================
    // Null Safety
    // ========================================================================

    @Nested
    @DisplayName("Null Safety")
    class NullSafetyTests {

        @Test
        @DisplayName("getCurrentZone rejects null playerId")
        void nullPlayerIdGetZone() {
            assertThrows(NullPointerException.class, () -> tracker.getCurrentZone(null));
        }

        @Test
        @DisplayName("getCurrentZoneId rejects null playerId")
        void nullPlayerIdGetZoneId() {
            assertThrows(NullPointerException.class, () -> tracker.getCurrentZoneId(null));
        }

        @Test
        @DisplayName("isInZone rejects null playerId")
        void nullPlayerIdIsInZone() {
            assertThrows(NullPointerException.class, () -> tracker.isInZone(null, "hub"));
        }

        @Test
        @DisplayName("clear rejects null playerId")
        void nullPlayerIdClear() {
            assertThrows(NullPointerException.class, () -> tracker.clear(null));
        }
    }
}
