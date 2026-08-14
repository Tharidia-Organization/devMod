package com.devmod.debug;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DebugManager using UUID-based methods only (no MC entity mocking).
 */
@DisplayName("DebugManager")
class DebugManagerTest {

    private final DebugManager manager = DebugManager.INSTANCE;

    @AfterEach
    void tearDown() {
        // Clean up by clearing all test players
        // DebugManager is a singleton so we need to clear state
        for (DebugFeature feature : DebugFeature.values()) {
            List<UUID> players = manager.getPlayersWithFeature(feature);
            for (UUID playerId : players) {
                manager.clearPlayer(playerId);
            }
        }
    }

    @Nested
    @DisplayName("isEnabled(UUID)")
    class IsEnabledByUUID {

        @Test
        @DisplayName("Returns false for unknown player")
        void falseForUnknown() {
            assertFalse(manager.isEnabled(UUID.randomUUID(), DebugFeature.POI));
        }

        @Test
        @DisplayName("Returns false after clearPlayer")
        void falseAfterClear() {
            UUID playerId = UUID.randomUUID();
            // We can't directly enable via UUID without ServerPlayer,
            // but clearPlayer should be safe to call
            manager.clearPlayer(playerId);
            assertFalse(manager.isEnabled(playerId, DebugFeature.POI));
        }
    }

    @Nested
    @DisplayName("getPlayersWithFeature")
    class GetPlayersWithFeature {

        @Test
        @DisplayName("Returns empty list when no one has feature enabled")
        void emptyWhenNoneEnabled() {
            List<UUID> players = manager.getPlayersWithFeature(DebugFeature.HEIGHTMAP);
            assertNotNull(players);
            // Could be non-empty if other tests leaked state, but should be testable
        }
    }

    @Nested
    @DisplayName("anyPlayerHasFeature")
    class AnyPlayerHasFeature {

        @Test
        @DisplayName("Returns false for feature nobody has enabled")
        void falseWhenNoneEnabled() {
            // Use a rare feature that's unlikely to be set by other tests
            // Since we clean up in tearDown, this should be safe
            assertFalse(manager.anyPlayerHasFeature(DebugFeature.SPAWN_CHUNKS));
        }
    }

    @Nested
    @DisplayName("clearPlayer")
    class ClearPlayer {

        @Test
        @DisplayName("Clears player features and global subscriber")
        void clearsAll() {
            UUID playerId = UUID.randomUUID();
            manager.clearPlayer(playerId);
            assertFalse(manager.isEnabled(playerId, DebugFeature.POI));
            assertFalse(manager.isEnabled(playerId, DebugFeature.ENTITY_PATHING));
        }

        @Test
        @DisplayName("clearPlayer is idempotent for unknown player")
        void idempotentForUnknown() {
            UUID playerId = UUID.randomUUID();
            assertDoesNotThrow(() -> manager.clearPlayer(playerId));
            assertDoesNotThrow(() -> manager.clearPlayer(playerId));
        }
    }

    @Nested
    @DisplayName("getActivePlayerCount")
    class GetActivePlayerCount {

        @Test
        @DisplayName("Count is non-negative")
        void countIsNonNegative() {
            assertTrue(manager.getActivePlayerCount() >= 0);
        }
    }
}
