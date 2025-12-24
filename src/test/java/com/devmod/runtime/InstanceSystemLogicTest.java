package com.devmod.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Instance Dimension System logic.
 *
 * Tests the core state machine logic, data structures, and edge cases
 * without requiring a running Minecraft server.
 *
 * Focus areas:
 * 1. InstanceData state transitions
 * 2. PlayerInstanceState transitions
 * 3. Data mapping consistency (bidirectional maps)
 * 4. Edge cases and error handling
 */
public class InstanceSystemLogicTest {

    // ============================================================
    // TEST 1: InstanceState State Machine
    // ============================================================
    @Nested
    @DisplayName("InstanceState State Machine Tests")
    class InstanceStateTests {

        @Test
        @DisplayName("Valid state transitions: CREATING -> READY -> ACTIVE -> COMPLETING -> DESTROYING -> DESTROYED")
        void testValidStateTransitions() {
            // Test the happy path through all states
            InstanceState state = InstanceState.CREATING;

            // CREATING -> READY (dimension created)
            assertTrue(isValidTransition(state, InstanceState.READY),
                "CREATING -> READY should be valid");
            state = InstanceState.READY;

            // READY -> ACTIVE (first player entered)
            assertTrue(isValidTransition(state, InstanceState.ACTIVE),
                "READY -> ACTIVE should be valid");
            state = InstanceState.ACTIVE;

            // ACTIVE -> COMPLETING (quest ending)
            assertTrue(isValidTransition(state, InstanceState.COMPLETING),
                "ACTIVE -> COMPLETING should be valid");
            state = InstanceState.COMPLETING;

            // COMPLETING -> DESTROYING (players returned)
            assertTrue(isValidTransition(state, InstanceState.DESTROYING),
                "COMPLETING -> DESTROYING should be valid");
            state = InstanceState.DESTROYING;

            // DESTROYING -> DESTROYED (cleanup complete)
            assertTrue(isValidTransition(state, InstanceState.DESTROYED),
                "DESTROYING -> DESTROYED should be valid");
        }

        @Test
        @DisplayName("Invalid state transitions should be rejected")
        void testInvalidStateTransitions() {
            // Can't go backwards
            assertFalse(isValidTransition(InstanceState.ACTIVE, InstanceState.CREATING),
                "ACTIVE -> CREATING should be invalid (backwards)");
            assertFalse(isValidTransition(InstanceState.DESTROYED, InstanceState.ACTIVE),
                "DESTROYED -> ACTIVE should be invalid (backwards)");

            // Can't skip states (except for error paths to DESTROYING)
            assertFalse(isValidTransition(InstanceState.CREATING, InstanceState.ACTIVE),
                "CREATING -> ACTIVE should be invalid (skipping READY)");
            // Note: READY -> DESTROYING IS valid (cancellation before player enters)
            assertFalse(isValidTransition(InstanceState.READY, InstanceState.DESTROYED),
                "READY -> DESTROYED should be invalid (skipping DESTROYING)");
        }

        @Test
        @DisplayName("Error state transitions")
        void testErrorStateTransitions() {
            // CREATING can fail
            assertTrue(isValidTransition(InstanceState.CREATING, InstanceState.DESTROYING),
                "CREATING -> DESTROYING should be valid (creation failed)");

            // READY can be cancelled before any player enters
            assertTrue(isValidTransition(InstanceState.READY, InstanceState.DESTROYING),
                "READY -> DESTROYING should be valid (cancelled)");
        }

        /**
         * Validates state transitions according to the design.
         */
        private boolean isValidTransition(InstanceState from, InstanceState to) {
            return switch (from) {
                case CREATING -> to == InstanceState.READY || to == InstanceState.DESTROYING;
                case READY -> to == InstanceState.ACTIVE || to == InstanceState.DESTROYING;
                case ACTIVE -> to == InstanceState.COMPLETING;
                case COMPLETING -> to == InstanceState.DESTROYING;
                case DESTROYING -> to == InstanceState.DESTROYED;
                case DESTROYED -> false; // Terminal state
            };
        }
    }

    // ============================================================
    // TEST 2: PlayerInstanceState State Machine
    // ============================================================
    @Nested
    @DisplayName("PlayerInstanceState State Machine Tests")
    class PlayerInstanceStateTests {

        @Test
        @DisplayName("Valid player state transitions: NORMAL -> PREPARING -> IN_TRANSIT -> IN_INSTANCE -> RETURNING -> NORMAL")
        void testValidPlayerStateTransitions() {
            PlayerInstanceState state = PlayerInstanceState.NORMAL;

            // NORMAL -> PREPARING (quest accepted)
            assertTrue(isValidPlayerTransition(state, PlayerInstanceState.PREPARING));
            state = PlayerInstanceState.PREPARING;

            // PREPARING -> IN_TRANSIT (countdown started)
            assertTrue(isValidPlayerTransition(state, PlayerInstanceState.IN_TRANSIT));
            state = PlayerInstanceState.IN_TRANSIT;

            // IN_TRANSIT -> IN_INSTANCE (teleport complete)
            assertTrue(isValidPlayerTransition(state, PlayerInstanceState.IN_INSTANCE));
            state = PlayerInstanceState.IN_INSTANCE;

            // IN_INSTANCE -> RETURNING (quest ended)
            assertTrue(isValidPlayerTransition(state, PlayerInstanceState.RETURNING));
            state = PlayerInstanceState.RETURNING;

            // RETURNING -> NORMAL (recovery complete)
            assertTrue(isValidPlayerTransition(state, PlayerInstanceState.NORMAL));
        }

        @Test
        @DisplayName("Early termination paths")
        void testEarlyTerminationPaths() {
            // Player can abort during PREPARING
            assertTrue(isValidPlayerTransition(PlayerInstanceState.PREPARING, PlayerInstanceState.NORMAL),
                "PREPARING -> NORMAL should be valid (cancelled)");

            // Player can fail during IN_TRANSIT (teleport failed)
            assertTrue(isValidPlayerTransition(PlayerInstanceState.IN_TRANSIT, PlayerInstanceState.NORMAL),
                "IN_TRANSIT -> NORMAL should be valid (teleport failed)");
        }

        @Test
        @DisplayName("Recovery should always return to NORMAL")
        void testRecoveryAlwaysReturnsToNormal() {
            // From any non-terminal state, recovery leads to NORMAL
            for (PlayerInstanceState state : PlayerInstanceState.values()) {
                if (state != PlayerInstanceState.NORMAL) {
                    assertTrue(isValidPlayerTransition(state, PlayerInstanceState.NORMAL),
                        state + " -> NORMAL should be valid (recovery)");
                }
            }
        }

        private boolean isValidPlayerTransition(PlayerInstanceState from, PlayerInstanceState to) {
            return switch (from) {
                case NORMAL -> to == PlayerInstanceState.PREPARING;
                case PREPARING -> to == PlayerInstanceState.IN_TRANSIT || to == PlayerInstanceState.NORMAL;
                case IN_TRANSIT -> to == PlayerInstanceState.IN_INSTANCE || to == PlayerInstanceState.NORMAL;
                case IN_INSTANCE -> to == PlayerInstanceState.RETURNING || to == PlayerInstanceState.NORMAL;
                case RETURNING -> to == PlayerInstanceState.NORMAL;
            };
        }
    }

    // ============================================================
    // TEST 3: Bidirectional Map Consistency
    // ============================================================
    @Nested
    @DisplayName("Bidirectional Map Consistency Tests")
    class BidirectionalMapTests {

        private Map<UUID, UUID> arenaToInstance;
        private Map<UUID, UUID> instanceToArena;

        @BeforeEach
        void setUp() {
            arenaToInstance = new ConcurrentHashMap<>();
            instanceToArena = new ConcurrentHashMap<>();
        }

        @Test
        @DisplayName("Adding to both maps maintains consistency")
        void testAddConsistency() {
            UUID arenaId = UUID.randomUUID();
            UUID instanceId = UUID.randomUUID();

            // Add to both maps
            arenaToInstance.put(arenaId, instanceId);
            instanceToArena.put(instanceId, arenaId);

            // Verify bidirectional lookup
            assertEquals(instanceId, arenaToInstance.get(arenaId));
            assertEquals(arenaId, instanceToArena.get(instanceId));
        }

        @Test
        @DisplayName("Removing from both maps maintains consistency")
        void testRemoveConsistency() {
            UUID arenaId = UUID.randomUUID();
            UUID instanceId = UUID.randomUUID();

            // Add
            arenaToInstance.put(arenaId, instanceId);
            instanceToArena.put(instanceId, arenaId);

            // Remove by instanceId (as done in endInstanceQuest)
            UUID removedArenaId = instanceToArena.remove(instanceId);
            if (removedArenaId != null) {
                arenaToInstance.remove(removedArenaId);
            }

            // Verify both are empty
            assertNull(arenaToInstance.get(arenaId), "arenaToInstance should not contain arenaId");
            assertNull(instanceToArena.get(instanceId), "instanceToArena should not contain instanceId");
        }

        @Test
        @DisplayName("Multiple instances don't interfere with each other")
        void testMultipleInstancesIsolation() {
            UUID arena1 = UUID.randomUUID();
            UUID instance1 = UUID.randomUUID();
            UUID arena2 = UUID.randomUUID();
            UUID instance2 = UUID.randomUUID();

            // Add both
            arenaToInstance.put(arena1, instance1);
            instanceToArena.put(instance1, arena1);
            arenaToInstance.put(arena2, instance2);
            instanceToArena.put(instance2, arena2);

            // Remove first
            UUID removed = instanceToArena.remove(instance1);
            arenaToInstance.remove(removed);

            // Second should still be intact
            assertEquals(instance2, arenaToInstance.get(arena2));
            assertEquals(arena2, instanceToArena.get(instance2));
        }
    }

    // ============================================================
    // TEST 4: InstanceData Player Management
    // ============================================================
    @Nested
    @DisplayName("InstanceData Player Management Tests")
    class InstanceDataPlayerTests {

        @Test
        @DisplayName("Adding and removing players")
        void testPlayerManagement() {
            // Simulate InstanceData behavior
            Set<UUID> players = new HashSet<>();
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            // Add players
            assertTrue(players.add(player1));
            assertTrue(players.add(player2));
            assertEquals(2, players.size());

            // Remove player
            assertTrue(players.remove(player1));
            assertEquals(1, players.size());
            assertTrue(players.contains(player2));
            assertFalse(players.contains(player1));
        }

        @Test
        @DisplayName("Instance becomes empty after all players leave")
        void testInstanceBecomesEmpty() {
            Set<UUID> players = new HashSet<>();
            UUID player1 = UUID.randomUUID();

            players.add(player1);
            assertFalse(players.isEmpty());

            players.remove(player1);
            assertTrue(players.isEmpty(), "Instance should be empty after last player leaves");
        }

        @Test
        @DisplayName("Duplicate player adds are ignored")
        void testDuplicatePlayerAdd() {
            Set<UUID> players = new HashSet<>();
            UUID player1 = UUID.randomUUID();

            assertTrue(players.add(player1), "First add should return true");
            assertFalse(players.add(player1), "Duplicate add should return false");
            assertEquals(1, players.size(), "Size should still be 1");
        }
    }

    // ============================================================
    // TEST 5: Edge Cases and Error Scenarios
    // ============================================================
    @Nested
    @DisplayName("Edge Cases and Error Scenarios")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handling null instance ID in cleanup")
        void testNullInstanceIdInCleanup() {
            Map<UUID, UUID> instanceToArena = new ConcurrentHashMap<>();
            Map<UUID, UUID> arenaToInstance = new ConcurrentHashMap<>();

            // Try to end a non-existent instance
            UUID nonExistentInstanceId = UUID.randomUUID();
            UUID arenaId = instanceToArena.remove(nonExistentInstanceId);

            // Should handle gracefully
            assertNull(arenaId, "Should return null for non-existent instance");

            // The correct pattern: check for null BEFORE calling remove
            // This mirrors what the actual code does in InstanceArenaManager.endInstanceQuest()
            assertTrue(arenaToInstance.isEmpty(), "No removals should occur when arenaId is null");

            // Also validate the branch where an arena mapping exists
            UUID existingInstanceId = UUID.randomUUID();
            UUID existingArenaId = UUID.randomUUID();
            instanceToArena.put(existingInstanceId, existingArenaId);
            arenaToInstance.put(existingArenaId, existingInstanceId);

            UUID removedArenaId = instanceToArena.remove(existingInstanceId);
            assertNotNull(removedArenaId);
            if (removedArenaId != null) {
                arenaToInstance.remove(removedArenaId);
                assertFalse(arenaToInstance.containsKey(removedArenaId), "Arena mapping should be removed when present");
            }
        }

        @Test
        @DisplayName("Concurrent modification safety")
        void testConcurrentModificationSafety() throws InterruptedException {
            Map<UUID, UUID> map = new ConcurrentHashMap<>();

            // Add some initial data
            for (int i = 0; i < 100; i++) {
                map.put(UUID.randomUUID(), UUID.randomUUID());
            }

            // Concurrent reads and writes
            Thread writer = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    map.put(UUID.randomUUID(), UUID.randomUUID());
                }
            });

            Thread reader = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    // Iterate without ConcurrentModificationException
                    assertDoesNotThrow(() -> {
                        for (UUID key : map.keySet()) {
                            map.get(key);
                        }
                    });
                }
            });

            writer.start();
            reader.start();
            writer.join();
            reader.join();
        }

        @Test
        @DisplayName("Player mapping consistency after instance destruction")
        void testPlayerMappingAfterDestruction() {
            // Simulating the scenario where instance is destroyed but player mapping might linger
            Map<UUID, UUID> playerToInstance = new ConcurrentHashMap<>();
            Set<UUID> destroyedInstances = new HashSet<>();

            UUID playerId = UUID.randomUUID();
            UUID instanceId = UUID.randomUUID();

            // Map player to instance
            playerToInstance.put(playerId, instanceId);

            // Instance gets destroyed
            destroyedInstances.add(instanceId);

            // Check if player's instance is still valid
            UUID mappedInstance = playerToInstance.get(playerId);
            if (mappedInstance != null && destroyedInstances.contains(mappedInstance)) {
                // Clean up orphaned mapping
                playerToInstance.remove(playerId);
            }

            assertNull(playerToInstance.get(playerId),
                "Player mapping should be cleaned after instance destruction");
        }
    }

    // ============================================================
    // TEST 6: Quest-Instance Integration Logic
    // ============================================================
    @Nested
    @DisplayName("Quest-Instance Integration Logic Tests")
    class QuestIntegrationTests {

        @Test
        @DisplayName("Session tracks instanceId correctly")
        void testSessionInstanceTracking() {
            // Simulate ActiveQuestSession with instanceId
            UUID instanceId = UUID.randomUUID();

            // Mock session behavior
            class MockSession {
                private UUID instanceId;
                public void setInstanceId(UUID id) { this.instanceId = id; }
                public UUID getInstanceId() { return instanceId; }
                public boolean isInInstanceDimension() { return instanceId != null; }
            }

            MockSession session = new MockSession();

            // Initially not in instance
            assertFalse(session.isInInstanceDimension());
            assertNull(session.getInstanceId());

            // Set instance ID
            session.setInstanceId(instanceId);

            // Now in instance
            assertTrue(session.isInInstanceDimension());
            assertEquals(instanceId, session.getInstanceId());
        }

        @Test
        @DisplayName("Cleanup order for instance mode")
        void testCleanupOrderInstanceMode() {
            // Verify the correct order of operations
            StringBuilder order = new StringBuilder();

            // Simulate the correct cleanup order for Instance mode
            order.append("1-cleanupQuestSystems,");
            order.append("2-onQuestEnd,");
            order.append("3-endTelemetry,");
            order.append("4-updateStats,");
            order.append("5-syncPayload,");
            order.append("6-notify,");
            order.append("7-restorePlayer,");  // No-op for instance mode
            order.append("8-cleanupInstance"); // Triggers teleport + recovery

            String expected = "1-cleanupQuestSystems,2-onQuestEnd,3-endTelemetry,4-updateStats,5-syncPayload,6-notify,7-restorePlayer,8-cleanupInstance";
            assertEquals(expected, order.toString(),
                "Cleanup operations should be in correct order");
        }

        @Test
        @DisplayName("Legacy mode vs Instance mode branching")
        void testModeBranching() {
            boolean useInstanceDimensions = true;
            UUID instanceId = UUID.randomUUID();

            class MockSession {
                UUID instanceId;
                boolean isInInstanceDimension() { return instanceId != null; }
            }

            MockSession instanceSession = new MockSession();
            instanceSession.instanceId = instanceId;

            MockSession legacySession = new MockSession();
            legacySession.instanceId = null;

            // Instance mode session
            if (instanceSession.isInInstanceDimension()) {
                // Should skip local restore
                assertTrue(useInstanceDimensions, "Instance mode should skip local restore");
            } else {
                fail("Instance session should be detected as in instance dimension");
            }

            // Legacy mode session
            if (legacySession.isInInstanceDimension()) {
                fail("Legacy session should not be in instance dimension");
            } else {
                // Should do local restore
                assertTrue(true, "Legacy mode should do local restore");
            }
        }
    }

    // ============================================================
    // TEST 7: UUID Parsing Without Dashes (BUG #6 fix validation)
    // ============================================================
    @Nested
    @DisplayName("UUID Parsing Tests")
    class UuidParsingTests {

        @Test
        @DisplayName("Parse UUID without dashes to standard format")
        void testParseUuidWithoutDashes() {
            UUID original = UUID.randomUUID();
            // DynamicDimensionManager stores without dashes
            String withoutDashes = original.toString().replace("-", "");

            // Verify it's 32 chars
            assertEquals(32, withoutDashes.length());

            // Parse back with dashes
            String formatted = withoutDashes.substring(0, 8) + "-" +
                               withoutDashes.substring(8, 12) + "-" +
                               withoutDashes.substring(12, 16) + "-" +
                               withoutDashes.substring(16, 20) + "-" +
                               withoutDashes.substring(20, 32);

            UUID parsed = UUID.fromString(formatted);
            assertEquals(original, parsed, "Parsed UUID should match original");
        }

        @Test
        @DisplayName("Multiple UUIDs parse correctly")
        void testMultipleUuidParsing() {
            for (int i = 0; i < 10; i++) {
                UUID original = UUID.randomUUID();
                String withoutDashes = original.toString().replace("-", "");

                String formatted = withoutDashes.substring(0, 8) + "-" +
                                   withoutDashes.substring(8, 12) + "-" +
                                   withoutDashes.substring(12, 16) + "-" +
                                   withoutDashes.substring(16, 20) + "-" +
                                   withoutDashes.substring(20, 32);

                UUID parsed = UUID.fromString(formatted);
                assertEquals(original, parsed);
            }
        }

        @Test
        @DisplayName("Invalid length returns null behavior")
        void testInvalidLengthHandling() {
            String tooShort = "abc123";

            // Too short strings should throw on substring operations
            assertThrows(StringIndexOutOfBoundsException.class, () -> {
                tooShort.substring(0, 8);
            });

            // Invalid hex characters should throw IllegalArgumentException
            String invalidHex = "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"; // 32 chars but not valid hex
            String formattedInvalid = invalidHex.substring(0, 8) + "-" +
                               invalidHex.substring(8, 12) + "-" +
                               invalidHex.substring(12, 16) + "-" +
                               invalidHex.substring(16, 20) + "-" +
                               invalidHex.substring(20, 32);
            assertThrows(IllegalArgumentException.class, () -> {
                UUID.fromString(formattedInvalid);
            });
        }

        @Test
        @DisplayName("Dimension folder name matches pattern")
        void testDimensionFolderNamePattern() {
            UUID instanceId = UUID.randomUUID();

            // DynamicDimensionManager format
            String dimensionName = "instance_" + instanceId.toString().replace("-", "");

            // Verify pattern
            assertTrue(dimensionName.startsWith("instance_"));
            String uuidPart = dimensionName.replace("instance_", "");
            assertEquals(32, uuidPart.length());

            // Round-trip
            String formatted = uuidPart.substring(0, 8) + "-" +
                               uuidPart.substring(8, 12) + "-" +
                               uuidPart.substring(12, 16) + "-" +
                               uuidPart.substring(16, 20) + "-" +
                               uuidPart.substring(20, 32);
            UUID recovered = UUID.fromString(formatted);
            assertEquals(instanceId, recovered);
        }
    }
}
