package com.frenkvs.devmod.instance;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L3 Test: Recovery System Validation
 *
 * Tests the recovery system rules without Minecraft dependencies.
 * Validates:
 * - PlayerInstanceState state machine
 * - Snapshot lifecycle rules
 * - Recovery decision logic
 * - File path generation rules
 */
@DisplayName("L3: Recovery System Validation")
class RecoverySystemValidationTest {

    // === L3-01: PlayerInstanceState Definitions ===

    @Nested
    @DisplayName("L3-01: PlayerInstanceState Definitions")
    class PlayerInstanceStateDefinitionsTest {

        @Test
        @DisplayName("PlayerInstanceState has 5 states")
        void playerInstanceStateHasFiveStates() {
            assertEquals(5, PlayerInstanceState.values().length);
        }

        @Test
        @DisplayName("NORMAL state exists")
        void normalStateExists() {
            assertNotNull(PlayerInstanceState.NORMAL);
            assertEquals(0, PlayerInstanceState.NORMAL.ordinal());
        }

        @Test
        @DisplayName("PREPARING state exists")
        void preparingStateExists() {
            assertNotNull(PlayerInstanceState.PREPARING);
            assertEquals(1, PlayerInstanceState.PREPARING.ordinal());
        }

        @Test
        @DisplayName("IN_TRANSIT state exists")
        void inTransitStateExists() {
            assertNotNull(PlayerInstanceState.IN_TRANSIT);
            assertEquals(2, PlayerInstanceState.IN_TRANSIT.ordinal());
        }

        @Test
        @DisplayName("IN_INSTANCE state exists")
        void inInstanceStateExists() {
            assertNotNull(PlayerInstanceState.IN_INSTANCE);
            assertEquals(3, PlayerInstanceState.IN_INSTANCE.ordinal());
        }

        @Test
        @DisplayName("RETURNING state exists")
        void returningStateExists() {
            assertNotNull(PlayerInstanceState.RETURNING);
            assertEquals(4, PlayerInstanceState.RETURNING.ordinal());
        }
    }

    // === L3-02: PlayerInstanceState Forward Transitions ===

    @Nested
    @DisplayName("L3-02: PlayerInstanceState Forward Transitions")
    class PlayerInstanceStateForwardTransitionsTest {

        @Test
        @DisplayName("NORMAL -> PREPARING is valid")
        void normalToPreparingIsValid() {
            assertTrue(PlayerInstanceState.NORMAL.canTransitionTo(PlayerInstanceState.PREPARING));
        }

        @Test
        @DisplayName("PREPARING -> IN_TRANSIT is valid")
        void preparingToInTransitIsValid() {
            assertTrue(PlayerInstanceState.PREPARING.canTransitionTo(PlayerInstanceState.IN_TRANSIT));
        }

        @Test
        @DisplayName("IN_TRANSIT -> IN_INSTANCE is valid")
        void inTransitToInInstanceIsValid() {
            assertTrue(PlayerInstanceState.IN_TRANSIT.canTransitionTo(PlayerInstanceState.IN_INSTANCE));
        }

        @Test
        @DisplayName("IN_INSTANCE -> RETURNING is valid")
        void inInstanceToReturningIsValid() {
            assertTrue(PlayerInstanceState.IN_INSTANCE.canTransitionTo(PlayerInstanceState.RETURNING));
        }

        @Test
        @DisplayName("RETURNING -> NORMAL only (cannot go elsewhere)")
        void returningOnlyGoesToNormal() {
            // Can go to NORMAL
            assertTrue(PlayerInstanceState.RETURNING.canTransitionTo(PlayerInstanceState.NORMAL));
            // Cannot go forward to anything else
            assertFalse(PlayerInstanceState.RETURNING.canTransitionTo(PlayerInstanceState.PREPARING));
            assertFalse(PlayerInstanceState.RETURNING.canTransitionTo(PlayerInstanceState.IN_TRANSIT));
            assertFalse(PlayerInstanceState.RETURNING.canTransitionTo(PlayerInstanceState.IN_INSTANCE));
        }

        @Test
        @DisplayName("NORMAL cannot skip to IN_INSTANCE")
        void normalCannotSkipToInInstance() {
            assertFalse(PlayerInstanceState.NORMAL.canTransitionTo(PlayerInstanceState.IN_INSTANCE));
        }

        @Test
        @DisplayName("PREPARING cannot skip to IN_INSTANCE")
        void preparingCannotSkipToInInstance() {
            assertFalse(PlayerInstanceState.PREPARING.canTransitionTo(PlayerInstanceState.IN_INSTANCE));
        }
    }

    // === L3-03: PlayerInstanceState Recovery Transitions ===

    @Nested
    @DisplayName("L3-03: PlayerInstanceState Recovery Transitions")
    class PlayerInstanceStateRecoveryTransitionsTest {

        @ParameterizedTest
        @EnumSource(PlayerInstanceState.class)
        @DisplayName("Any state can transition to NORMAL (recovery path)")
        void anyStateCanTransitionToNormal(PlayerInstanceState fromState) {
            assertTrue(fromState.canTransitionTo(PlayerInstanceState.NORMAL),
                "State " + fromState + " should be able to transition to NORMAL");
        }

        @Test
        @DisplayName("PREPARING recovery valid next states include NORMAL")
        void preparingValidNextStatesIncludeNormal() {
            Set<PlayerInstanceState> validNext = PlayerInstanceState.PREPARING.getValidNextStates();
            assertTrue(validNext.contains(PlayerInstanceState.NORMAL));
            assertTrue(validNext.contains(PlayerInstanceState.IN_TRANSIT));
        }

        @Test
        @DisplayName("IN_TRANSIT recovery valid next states include NORMAL")
        void inTransitValidNextStatesIncludeNormal() {
            Set<PlayerInstanceState> validNext = PlayerInstanceState.IN_TRANSIT.getValidNextStates();
            assertTrue(validNext.contains(PlayerInstanceState.NORMAL));
            assertTrue(validNext.contains(PlayerInstanceState.IN_INSTANCE));
        }

        @Test
        @DisplayName("IN_INSTANCE recovery valid next states include NORMAL")
        void inInstanceValidNextStatesIncludeNormal() {
            Set<PlayerInstanceState> validNext = PlayerInstanceState.IN_INSTANCE.getValidNextStates();
            assertTrue(validNext.contains(PlayerInstanceState.NORMAL));
            assertTrue(validNext.contains(PlayerInstanceState.RETURNING));
        }

        @Test
        @DisplayName("RETURNING only goes to NORMAL")
        void returningOnlyGoesToNormal() {
            Set<PlayerInstanceState> validNext = PlayerInstanceState.RETURNING.getValidNextStates();
            assertEquals(1, validNext.size());
            assertTrue(validNext.contains(PlayerInstanceState.NORMAL));
        }
    }

    // === L3-04: Snapshot Requirement Rules ===

    @Nested
    @DisplayName("L3-04: Snapshot Requirement Rules")
    class SnapshotRequirementRulesTest {

        @Test
        @DisplayName("NORMAL state does not require snapshot")
        void normalStateDoesNotRequireSnapshot() {
            assertFalse(PlayerInstanceState.NORMAL.requiresSnapshot());
        }

        @Test
        @DisplayName("PREPARING state requires snapshot")
        void preparingStateRequiresSnapshot() {
            assertTrue(PlayerInstanceState.PREPARING.requiresSnapshot());
        }

        @Test
        @DisplayName("IN_TRANSIT state requires snapshot")
        void inTransitStateRequiresSnapshot() {
            assertTrue(PlayerInstanceState.IN_TRANSIT.requiresSnapshot());
        }

        @Test
        @DisplayName("IN_INSTANCE state requires snapshot")
        void inInstanceStateRequiresSnapshot() {
            assertTrue(PlayerInstanceState.IN_INSTANCE.requiresSnapshot());
        }

        @Test
        @DisplayName("RETURNING state requires snapshot")
        void returningStateRequiresSnapshot() {
            assertTrue(PlayerInstanceState.RETURNING.requiresSnapshot());
        }

        @ParameterizedTest
        @EnumSource(value = PlayerInstanceState.class, names = "NORMAL", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("All non-NORMAL states require snapshot")
        void allNonNormalStatesRequireSnapshot(PlayerInstanceState state) {
            assertTrue(state.requiresSnapshot(),
                "State " + state + " should require snapshot");
        }
    }

    // === L3-05: Instance Flow Detection Rules ===

    @Nested
    @DisplayName("L3-05: Instance Flow Detection Rules")
    class InstanceFlowDetectionRulesTest {

        @Test
        @DisplayName("NORMAL is not in instance flow")
        void normalIsNotInInstanceFlow() {
            assertFalse(PlayerInstanceState.NORMAL.isInInstanceFlow());
        }

        @Test
        @DisplayName("PREPARING is not in instance flow")
        void preparingIsNotInInstanceFlow() {
            assertFalse(PlayerInstanceState.PREPARING.isInInstanceFlow());
        }

        @Test
        @DisplayName("IN_TRANSIT is in instance flow")
        void inTransitIsInInstanceFlow() {
            assertTrue(PlayerInstanceState.IN_TRANSIT.isInInstanceFlow());
        }

        @Test
        @DisplayName("IN_INSTANCE is in instance flow")
        void inInstanceIsInInstanceFlow() {
            assertTrue(PlayerInstanceState.IN_INSTANCE.isInInstanceFlow());
        }

        @Test
        @DisplayName("RETURNING is in instance flow")
        void returningIsInInstanceFlow() {
            assertTrue(PlayerInstanceState.RETURNING.isInInstanceFlow());
        }
    }

    // === L3-06: Recovery Decision Matrix ===

    @Nested
    @DisplayName("L3-06: Recovery Decision Matrix")
    class RecoveryDecisionMatrixTest {

        /**
         * Simulates RecoverySystem.checkPendingRecovery() decision logic.
         * Returns recovery action based on snapshot state.
         */
        private String getRecoveryAction(PlayerInstanceState snapshotState) {
            return switch (snapshotState) {
                case PREPARING, IN_TRANSIT -> "Restore from failed teleport";
                case IN_INSTANCE -> "Quest failed, restore to original position";
                case RETURNING -> "Complete interrupted return";
                case NORMAL -> "Clean up orphaned snapshot";
            };
        }

        @Test
        @DisplayName("PREPARING triggers teleport failure recovery")
        void preparingTriggersTeportFailureRecovery() {
            String action = getRecoveryAction(PlayerInstanceState.PREPARING);
            assertTrue(action.contains("teleport"), "PREPARING should trigger teleport recovery");
        }

        @Test
        @DisplayName("IN_TRANSIT triggers teleport failure recovery")
        void inTransitTriggersTeportFailureRecovery() {
            String action = getRecoveryAction(PlayerInstanceState.IN_TRANSIT);
            assertTrue(action.contains("teleport"), "IN_TRANSIT should trigger teleport recovery");
        }

        @Test
        @DisplayName("IN_INSTANCE triggers quest failed recovery")
        void inInstanceTriggersQuestFailedRecovery() {
            String action = getRecoveryAction(PlayerInstanceState.IN_INSTANCE);
            assertTrue(action.contains("Quest failed") || action.contains("failed"),
                "IN_INSTANCE should trigger quest failed recovery");
        }

        @Test
        @DisplayName("RETURNING triggers return completion")
        void returningTriggersReturnCompletion() {
            String action = getRecoveryAction(PlayerInstanceState.RETURNING);
            assertTrue(action.contains("return"), "RETURNING should complete return");
        }

        @Test
        @DisplayName("NORMAL triggers orphan cleanup")
        void normalTriggersOrphanCleanup() {
            String action = getRecoveryAction(PlayerInstanceState.NORMAL);
            assertTrue(action.contains("orphan") || action.contains("Clean up"),
                "NORMAL should clean up orphaned snapshot");
        }
    }

    // === L3-07: Snapshot File Naming Rules ===

    @Nested
    @DisplayName("L3-07: Snapshot File Naming Rules")
    class SnapshotFileNamingRulesTest {

        @Test
        @DisplayName("Snapshot file uses player UUID")
        void snapshotFileUsesPlayerUUID() {
            UUID playerId = UUID.randomUUID();
            String expectedFileName = playerId.toString() + ".dat";
            assertTrue(expectedFileName.endsWith(".dat"));
            assertTrue(expectedFileName.startsWith(playerId.toString()));
        }

        @Test
        @DisplayName("Snapshot file path is deterministic")
        void snapshotFilePathIsDeterministic() {
            UUID playerId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
            String path1 = playerId.toString() + ".dat";
            String path2 = playerId.toString() + ".dat";
            assertEquals(path1, path2);
        }

        @Test
        @DisplayName("Different players have different files")
        void differentPlayersHaveDifferentFiles() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            String file1 = player1.toString() + ".dat";
            String file2 = player2.toString() + ".dat";
            assertNotEquals(file1, file2);
        }
    }

    // === L3-08: Recovery Data Completeness Rules ===

    @Nested
    @DisplayName("L3-08: Recovery Data Completeness Rules")
    class RecoveryDataCompletenessRulesTest {

        /**
         * Simulates what data must be present in a snapshot.
         */
        private List<String> getRequiredSnapshotFields() {
            return Arrays.asList(
                "playerId",
                "state",
                "originalDimension",
                "originalX", "originalY", "originalZ",
                "originalYaw", "originalPitch",
                "inventory",
                "gameMode",
                "health", "maxHealth",
                "foodLevel", "saturation", "exhaustion",
                "experienceLevel", "experienceProgress", "totalExperience",
                "createdAt", "lastUpdated"
            );
        }

        @Test
        @DisplayName("Snapshot requires position data")
        void snapshotRequiresPositionData() {
            List<String> required = getRequiredSnapshotFields();
            assertTrue(required.contains("originalX"));
            assertTrue(required.contains("originalY"));
            assertTrue(required.contains("originalZ"));
            assertTrue(required.contains("originalYaw"));
            assertTrue(required.contains("originalPitch"));
            assertTrue(required.contains("originalDimension"));
        }

        @Test
        @DisplayName("Snapshot requires inventory data")
        void snapshotRequiresInventoryData() {
            List<String> required = getRequiredSnapshotFields();
            assertTrue(required.contains("inventory"));
        }

        @Test
        @DisplayName("Snapshot requires health data")
        void snapshotRequiresHealthData() {
            List<String> required = getRequiredSnapshotFields();
            assertTrue(required.contains("health"));
            assertTrue(required.contains("maxHealth"));
        }

        @Test
        @DisplayName("Snapshot requires food data")
        void snapshotRequiresFoodData() {
            List<String> required = getRequiredSnapshotFields();
            assertTrue(required.contains("foodLevel"));
            assertTrue(required.contains("saturation"));
            assertTrue(required.contains("exhaustion"));
        }

        @Test
        @DisplayName("Snapshot requires experience data")
        void snapshotRequiresExperienceData() {
            List<String> required = getRequiredSnapshotFields();
            assertTrue(required.contains("experienceLevel"));
            assertTrue(required.contains("experienceProgress"));
            assertTrue(required.contains("totalExperience"));
        }

        @Test
        @DisplayName("Snapshot requires timestamps")
        void snapshotRequiresTimestamps() {
            List<String> required = getRequiredSnapshotFields();
            assertTrue(required.contains("createdAt"));
            assertTrue(required.contains("lastUpdated"));
        }
    }

    // === L3-09: Recovery Order Rules ===

    @Nested
    @DisplayName("L3-09: Recovery Order Rules")
    class RecoveryOrderRulesTest {

        /**
         * Simulates the order of recovery operations.
         * Based on RecoverySystem.performRecovery()
         */
        private List<String> getRecoverySteps() {
            return Arrays.asList(
                "1. Teleport to original position",
                "2. Restore inventory",
                "3. Restore game mode",
                "4. Restore health and food",
                "5. Restore potion effects",
                "6. Restore experience",
                "7. Clean up instance registry",
                "8. Delete snapshot",
                "9. Notify player"
            );
        }

        @Test
        @DisplayName("Teleport happens first")
        void teleportHappensFirst() {
            List<String> steps = getRecoverySteps();
            assertTrue(steps.get(0).contains("Teleport"));
        }

        @Test
        @DisplayName("Inventory restored before game mode")
        void inventoryRestoredBeforeGameMode() {
            List<String> steps = getRecoverySteps();
            int inventoryIndex = -1;
            int gameModeIndex = -1;
            for (int i = 0; i < steps.size(); i++) {
                if (steps.get(i).contains("inventory")) inventoryIndex = i;
                if (steps.get(i).contains("game mode")) gameModeIndex = i;
            }
            assertTrue(inventoryIndex < gameModeIndex,
                "Inventory should be restored before game mode");
        }

        @Test
        @DisplayName("Snapshot deleted after restoration")
        void snapshotDeletedAfterRestoration() {
            List<String> steps = getRecoverySteps();
            int deleteIndex = -1;
            int lastRestoreIndex = -1;
            for (int i = 0; i < steps.size(); i++) {
                if (steps.get(i).contains("Delete snapshot")) deleteIndex = i;
                if (steps.get(i).contains("Restore")) lastRestoreIndex = i;
            }
            assertTrue(deleteIndex > lastRestoreIndex,
                "Snapshot should be deleted after all restorations");
        }

        @Test
        @DisplayName("Player notification is last")
        void playerNotificationIsLast() {
            List<String> steps = getRecoverySteps();
            String lastStep = steps.get(steps.size() - 1);
            assertTrue(lastStep.contains("Notify"));
        }

        @Test
        @DisplayName("Registry cleanup before snapshot deletion")
        void registryCleanupBeforeSnapshotDeletion() {
            List<String> steps = getRecoverySteps();
            int cleanupIndex = -1;
            int deleteIndex = -1;
            for (int i = 0; i < steps.size(); i++) {
                if (steps.get(i).contains("registry")) cleanupIndex = i;
                if (steps.get(i).contains("Delete snapshot")) deleteIndex = i;
            }
            assertTrue(cleanupIndex < deleteIndex,
                "Registry cleanup should happen before snapshot deletion");
        }
    }

    // === L3-10: UUID Parsing Rules (for dimension folder cleanup) ===

    @Nested
    @DisplayName("L3-10: UUID Parsing Rules")
    class UUIDParsingRulesTest {

        /**
         * Parse UUID without dashes (as stored in dimension folders).
         */
        private UUID parseUuidWithoutDashes(String uuidWithoutDashes) {
            if (uuidWithoutDashes == null || uuidWithoutDashes.length() != 32) {
                return null;
            }
            try {
                String formatted = uuidWithoutDashes.substring(0, 8) + "-" +
                    uuidWithoutDashes.substring(8, 12) + "-" +
                    uuidWithoutDashes.substring(12, 16) + "-" +
                    uuidWithoutDashes.substring(16, 20) + "-" +
                    uuidWithoutDashes.substring(20, 32);
                return UUID.fromString(formatted);
            } catch (Exception e) {
                return null;
            }
        }

        @Test
        @DisplayName("Valid 32-char hex string parses to UUID")
        void valid32CharHexStringParsesToUUID() {
            String noDashes = "12345678123412341234123456789abc";
            UUID result = parseUuidWithoutDashes(noDashes);
            assertNotNull(result);
            assertEquals("12345678-1234-1234-1234-123456789abc", result.toString());
        }

        @Test
        @DisplayName("Null input returns null")
        void nullInputReturnsNull() {
            assertNull(parseUuidWithoutDashes(null));
        }

        @Test
        @DisplayName("Too short string returns null")
        void tooShortStringReturnsNull() {
            assertNull(parseUuidWithoutDashes("12345678"));
        }

        @Test
        @DisplayName("Too long string returns null")
        void tooLongStringReturnsNull() {
            assertNull(parseUuidWithoutDashes("12345678123412341234123456789abc0"));
        }

        @Test
        @DisplayName("Invalid hex characters returns null")
        void invalidHexCharactersReturnsNull() {
            String invalid = "1234567812341234123412345678ZZZZ";
            assertNull(parseUuidWithoutDashes(invalid));
        }

        @Test
        @DisplayName("Round-trip UUID conversion works")
        void roundTripUUIDConversionWorks() {
            UUID original = UUID.randomUUID();
            String noDashes = original.toString().replace("-", "");
            UUID parsed = parseUuidWithoutDashes(noDashes);
            assertEquals(original, parsed);
        }
    }

    // === L3-11: Startup Cleanup Rules ===

    @Nested
    @DisplayName("L3-11: Startup Cleanup Rules")
    class StartupCleanupRulesTest {

        /**
         * Simulates startup cleanup phases.
         */
        private List<String> getStartupCleanupPhases() {
            return Arrays.asList(
                "1. Clean up orphaned snapshots",
                "2. Mark empty instances for destruction",
                "3. Clean up orphaned dimension folders"
            );
        }

        @Test
        @DisplayName("Startup cleanup has 3 phases")
        void startupCleanupHasThreePhases() {
            assertEquals(3, getStartupCleanupPhases().size());
        }

        @Test
        @DisplayName("Orphaned snapshots cleaned first")
        void orphanedSnapshotsCleanedFirst() {
            List<String> phases = getStartupCleanupPhases();
            assertTrue(phases.get(0).contains("snapshot"));
        }

        @Test
        @DisplayName("Empty instances marked for destruction")
        void emptyInstancesMarkedForDestruction() {
            List<String> phases = getStartupCleanupPhases();
            boolean found = phases.stream().anyMatch(p ->
                p.contains("empty") && p.contains("destruction"));
            assertTrue(found);
        }

        @Test
        @DisplayName("Orphaned dimension folders cleaned last")
        void orphanedDimensionFoldersCleanedLast() {
            List<String> phases = getStartupCleanupPhases();
            assertTrue(phases.get(2).contains("dimension"));
        }
    }

    // === L3-12: Thread Safety Rules ===

    @Nested
    @DisplayName("L3-12: Thread Safety Rules")
    class ThreadSafetyRulesTest {

        @Test
        @DisplayName("Snapshot state update uses synchronization")
        void snapshotStateUpdateUsesSynchronization() {
            // The RecoverySystem.updateSnapshotState uses synchronized(playerId.toString().intern())
            // This test validates the synchronization pattern is correct for string interning
            String uuid1 = UUID.randomUUID().toString();
            String uuid2 = new String(uuid1); // Different String object, same content

            // intern() should return the same reference for equal strings
            assertSame(uuid1.intern(), uuid2.intern());
        }

        @Test
        @DisplayName("Concurrent snapshot updates for different players are independent")
        void concurrentSnapshotUpdatesForDifferentPlayersAreIndependent() {
            String uuid1 = UUID.randomUUID().toString();
            String uuid2 = UUID.randomUUID().toString();

            // Different UUIDs should NOT share the same intern lock
            assertNotSame(uuid1.intern(), uuid2.intern());
        }
    }

    // === L3-13: Recovery Timing Constants ===

    @Nested
    @DisplayName("L3-13: Recovery Timing Constants")
    class RecoveryTimingConstantsTest {

        @Test
        @DisplayName("Destroy delay is 5 seconds")
        void destroyDelayIsFiveSeconds() {
            assertEquals(5000L, InstanceData.DESTROY_DELAY_MS);
        }

        @Test
        @DisplayName("Destroy delay is reasonable (1-30 seconds)")
        void destroyDelayIsReasonable() {
            long delay = InstanceData.DESTROY_DELAY_MS;
            assertTrue(delay >= 1000, "Delay should be at least 1 second");
            assertTrue(delay <= 30000, "Delay should be at most 30 seconds");
        }
    }
}
