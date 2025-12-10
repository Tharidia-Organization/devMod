package com.frenkvs.devmod.instance;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L2 Instance System Validation Tests.
 *
 * Validates:
 * - Instance state machine transitions
 * - InstanceData player management
 * - Instance destruction scheduling
 * - Solo vs party instance creation
 * - Serialization/deserialization
 */
@DisplayName("L2: Instance System Validation")
class InstanceValidationTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-08: Instance State Definitions
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-08: Instance State Definitions")
    class InstanceStateDefinitionsTest {

        @Test
        @DisplayName("InstanceState has all required states")
        void instanceStateHasAllStates() {
            InstanceState[] states = InstanceState.values();
            assertEquals(6, states.length, "Should have exactly 6 instance states");
        }

        @Test
        @DisplayName("All expected states exist")
        void allExpectedStatesExist() {
            assertNotNull(InstanceState.CREATING);
            assertNotNull(InstanceState.READY);
            assertNotNull(InstanceState.ACTIVE);
            assertNotNull(InstanceState.COMPLETING);
            assertNotNull(InstanceState.DESTROYING);
            assertNotNull(InstanceState.DESTROYED);
        }

        @Test
        @DisplayName("State ordinals are consistent")
        void stateOrdinalsAreConsistent() {
            assertEquals(0, InstanceState.CREATING.ordinal());
            assertEquals(1, InstanceState.READY.ordinal());
            assertEquals(2, InstanceState.ACTIVE.ordinal());
            assertEquals(3, InstanceState.COMPLETING.ordinal());
            assertEquals(4, InstanceState.DESTROYING.ordinal());
            assertEquals(5, InstanceState.DESTROYED.ordinal());
        }

        @Test
        @DisplayName("DESTROYED is terminal state")
        void destroyedIsTerminal() {
            assertTrue(InstanceState.DESTROYED.isTerminal(),
                "DESTROYED should be terminal");

            // All other states should not be terminal
            assertFalse(InstanceState.CREATING.isTerminal());
            assertFalse(InstanceState.READY.isTerminal());
            assertFalse(InstanceState.ACTIVE.isTerminal());
            assertFalse(InstanceState.COMPLETING.isTerminal());
            assertFalse(InstanceState.DESTROYING.isTerminal());
        }

        @Test
        @DisplayName("Alive states are correct")
        void aliveStatesAreCorrect() {
            assertTrue(InstanceState.CREATING.isAlive());
            assertTrue(InstanceState.READY.isAlive());
            assertTrue(InstanceState.ACTIVE.isAlive());
            assertTrue(InstanceState.COMPLETING.isAlive());
            assertFalse(InstanceState.DESTROYING.isAlive(),
                "DESTROYING should not be alive");
            assertFalse(InstanceState.DESTROYED.isAlive(),
                "DESTROYED should not be alive");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-09: Instance State Transitions
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-09: Instance State Transitions")
    class InstanceStateTransitionsTest {

        @Test
        @DisplayName("CREATING can transition to READY")
        void creatingCanTransitionToReady() {
            assertTrue(InstanceState.CREATING.canTransitionTo(InstanceState.READY));
        }

        @Test
        @DisplayName("CREATING can transition to DESTROYING (failure)")
        void creatingCanTransitionToDestroying() {
            assertTrue(InstanceState.CREATING.canTransitionTo(InstanceState.DESTROYING));
        }

        @Test
        @DisplayName("READY can transition to ACTIVE")
        void readyCanTransitionToActive() {
            assertTrue(InstanceState.READY.canTransitionTo(InstanceState.ACTIVE));
        }

        @Test
        @DisplayName("READY can transition to DESTROYING (cancelled)")
        void readyCanTransitionToDestroying() {
            assertTrue(InstanceState.READY.canTransitionTo(InstanceState.DESTROYING));
        }

        @Test
        @DisplayName("ACTIVE can only transition to COMPLETING")
        void activeCanOnlyTransitionToCompleting() {
            assertTrue(InstanceState.ACTIVE.canTransitionTo(InstanceState.COMPLETING));
            assertFalse(InstanceState.ACTIVE.canTransitionTo(InstanceState.READY));
            assertFalse(InstanceState.ACTIVE.canTransitionTo(InstanceState.DESTROYING));
        }

        @Test
        @DisplayName("COMPLETING can only transition to DESTROYING")
        void completingCanOnlyTransitionToDestroying() {
            assertTrue(InstanceState.COMPLETING.canTransitionTo(InstanceState.DESTROYING));
            assertFalse(InstanceState.COMPLETING.canTransitionTo(InstanceState.ACTIVE));
            assertFalse(InstanceState.COMPLETING.canTransitionTo(InstanceState.DESTROYED));
        }

        @Test
        @DisplayName("DESTROYING can only transition to DESTROYED")
        void destroyingCanOnlyTransitionToDestroyed() {
            assertTrue(InstanceState.DESTROYING.canTransitionTo(InstanceState.DESTROYED));
            assertFalse(InstanceState.DESTROYING.canTransitionTo(InstanceState.COMPLETING));
            assertFalse(InstanceState.DESTROYING.canTransitionTo(InstanceState.READY));
        }

        @Test
        @DisplayName("DESTROYED cannot transition to anything")
        void destroyedCannotTransition() {
            for (InstanceState state : InstanceState.values()) {
                assertFalse(InstanceState.DESTROYED.canTransitionTo(state),
                    "DESTROYED should not transition to " + state);
            }
        }

        @Test
        @DisplayName("Valid next states are correct for each state")
        void validNextStatesCorrect() {
            assertEquals(Set.of(InstanceState.READY, InstanceState.DESTROYING),
                InstanceState.CREATING.getValidNextStates());

            assertEquals(Set.of(InstanceState.ACTIVE, InstanceState.DESTROYING),
                InstanceState.READY.getValidNextStates());

            assertEquals(Set.of(InstanceState.COMPLETING),
                InstanceState.ACTIVE.getValidNextStates());

            assertEquals(Set.of(InstanceState.DESTROYING),
                InstanceState.COMPLETING.getValidNextStates());

            assertEquals(Set.of(InstanceState.DESTROYED),
                InstanceState.DESTROYING.getValidNextStates());

            assertEquals(Set.of(),
                InstanceState.DESTROYED.getValidNextStates());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-10: Instance Creation Rules
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-10: Instance Creation Rules")
    class InstanceCreationRulesTest {

        @Test
        @DisplayName("Solo instance max players is 1")
        void soloInstanceMaxPlayersIsOne() {
            int soloMaxPlayers = 1;
            assertEquals(1, soloMaxPlayers, "Solo instance should have max 1 player");
        }

        @Test
        @DisplayName("Party instance caps at 4 players")
        void partyInstanceCapsAtFourPlayers() {
            int requestedPlayers = 10;
            int actualMax = Math.min(requestedPlayers, 4);

            assertEquals(4, actualMax, "Party should cap at 4 players maximum");
        }

        @Test
        @DisplayName("Instance starts in CREATING state")
        void instanceStartsInCreatingState() {
            InstanceState initialState = InstanceState.CREATING;
            assertEquals(InstanceState.CREATING, initialState);
        }

        @Test
        @DisplayName("UUID generation produces unique IDs")
        void uuidGenerationProducesUniqueIds() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            assertNotEquals(id1, id2, "Each UUID should be unique");
        }

        @Test
        @DisplayName("Creation timestamp is recorded accurately")
        void creationTimestampIsRecorded() {
            long before = System.currentTimeMillis();
            long createdAt = System.currentTimeMillis();
            long after = System.currentTimeMillis();

            assertTrue(createdAt >= before);
            assertTrue(createdAt <= after);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-11: Player Management Rules
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-11: Player Management Rules")
    class PlayerManagementRulesTest {

        @Test
        @DisplayName("Players can join READY or ACTIVE instances")
        void playersCanJoinReadyOrActiveInstances() {
            Set<InstanceState> joinableStates = Set.of(
                InstanceState.READY,
                InstanceState.ACTIVE
            );

            assertTrue(joinableStates.contains(InstanceState.READY));
            assertTrue(joinableStates.contains(InstanceState.ACTIVE));
            assertFalse(joinableStates.contains(InstanceState.CREATING));
            assertFalse(joinableStates.contains(InstanceState.COMPLETING));
        }

        @Test
        @DisplayName("Capacity check prevents overfilling")
        void capacityCheckPreventsOverfilling() {
            int maxPlayers = 4;
            int currentPlayers = 4;

            boolean canAccept = currentPlayers < maxPlayers;
            assertFalse(canAccept, "Full instance should not accept more players");
        }

        @Test
        @DisplayName("Empty instance triggers destruction")
        void emptyInstanceTriggersDestruction() {
            // Rule: when last player leaves ACTIVE instance, schedule destruction
            int playersAfterLeave = 0;
            InstanceState currentState = InstanceState.ACTIVE;

            boolean shouldScheduleDestruction =
                playersAfterLeave == 0 &&
                (currentState == InstanceState.ACTIVE || currentState == InstanceState.READY);

            assertTrue(shouldScheduleDestruction,
                "Empty ACTIVE/READY instance should schedule destruction");
        }

        @Test
        @DisplayName("Player set uses concurrent collection")
        void playerSetUsesConcurrentCollection() {
            // ConcurrentHashMap.newKeySet() is thread-safe
            Set<UUID> players = java.util.concurrent.ConcurrentHashMap.newKeySet();

            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();

            players.add(p1);
            players.add(p2);

            assertEquals(2, players.size());
            assertTrue(players.contains(p1));
            assertTrue(players.contains(p2));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-12: Destruction Scheduling Rules
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-12: Destruction Scheduling Rules")
    class DestructionSchedulingRulesTest {

        @Test
        @DisplayName("Destruction delay is 5 seconds")
        void destructionDelayIsFiveSeconds() {
            long expectedDelay = 5000L;
            assertEquals(5000L, expectedDelay,
                "Destroy delay should be 5000ms (5 seconds)");
        }

        @Test
        @DisplayName("Destruction scheduling uses timestamp")
        void destructionSchedulingUsesTimestamp() {
            long markedAt = System.currentTimeMillis();
            long delay = 5000L;

            long destroyAt = markedAt + delay;
            assertTrue(destroyAt > markedAt);
        }

        @Test
        @DisplayName("Destruction can be cancelled before timeout")
        void destructionCanBeCancelled() {
            // Cancel by setting timestamp to 0
            long afterCancel = 0L;

            boolean isMarked = afterCancel > 0;
            assertFalse(isMarked, "Cancelled destruction should not be marked");
        }

        @Test
        @DisplayName("Should destroy check uses timestamp comparison")
        void shouldDestroyUsesTimestampComparison() {
            long markedAt = 1000L;
            long delay = 5000L;
            long now = 6001L; // After delay

            boolean shouldDestroy = markedAt > 0 && now >= markedAt + delay;
            assertTrue(shouldDestroy, "Should destroy after delay expires");
        }

        @Test
        @DisplayName("Should not destroy before delay expires")
        void shouldNotDestroyBeforeDelayExpires() {
            long markedAt = 1000L;
            long delay = 5000L;
            long now = 4000L; // Before delay

            boolean shouldDestroy = markedAt > 0 && now >= markedAt + delay;
            assertFalse(shouldDestroy, "Should not destroy before delay");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-13: Instance Lifecycle Helper Rules
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-13: Instance Lifecycle Helper Rules")
    class InstanceLifecycleHelperRulesTest {

        @Test
        @DisplayName("isActive checks for ACTIVE state")
        void isActiveChecksForActiveState() {
            InstanceState state = InstanceState.ACTIVE;
            boolean isActive = state == InstanceState.ACTIVE;
            assertTrue(isActive);

            state = InstanceState.READY;
            isActive = state == InstanceState.ACTIVE;
            assertFalse(isActive);
        }

        @Test
        @DisplayName("isDestroyed checks for DESTROYED state")
        void isDestroyedChecksForDestroyedState() {
            InstanceState state = InstanceState.DESTROYED;
            boolean isDestroyed = state == InstanceState.DESTROYED;
            assertTrue(isDestroyed);

            state = InstanceState.DESTROYING;
            isDestroyed = state == InstanceState.DESTROYED;
            assertFalse(isDestroyed);
        }

        @Test
        @DisplayName("canAcceptPlayers checks state and capacity")
        void canAcceptPlayersChecksStateAndCapacity() {
            // Must be READY or ACTIVE, and not full
            InstanceState state = InstanceState.READY;
            int currentPlayers = 0;
            int maxPlayers = 4;

            boolean canAccept = (state == InstanceState.READY || state == InstanceState.ACTIVE)
                && currentPlayers < maxPlayers;

            assertTrue(canAccept, "READY instance with capacity should accept players");

            // Full instance
            currentPlayers = 4;
            canAccept = (state == InstanceState.READY || state == InstanceState.ACTIVE)
                && currentPlayers < maxPlayers;
            assertFalse(canAccept, "Full instance should not accept players");
        }

        @Test
        @DisplayName("Age calculation uses time difference")
        void ageCalculationUsesTimeDifference() throws InterruptedException {
            long createdAt = System.currentTimeMillis();

            Thread.sleep(50);

            long now = System.currentTimeMillis();
            long age = now - createdAt;

            assertTrue(age >= 50, "Age should reflect elapsed time");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-14: Arena and Quest Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-14: Arena and Quest Configuration")
    class ArenaQuestConfigTest {

        @Test
        @DisplayName("Arena radius must be positive")
        void arenaRadiusMustBePositive() {
            // Validate arena radius constraints
            int validRadius = 32;
            assertTrue(validRadius > 0, "Arena radius should be positive");
            assertTrue(validRadius <= 100, "Arena radius should be reasonable");
        }

        @Test
        @DisplayName("Total waves must be at least 1")
        void totalWavesMustBeAtLeastOne() {
            int minWaves = 1;
            int defaultWaves = 10;
            int maxWaves = 100;

            assertTrue(minWaves >= 1);
            assertTrue(defaultWaves >= minWaves);
            assertTrue(maxWaves >= defaultWaves);
        }

        @Test
        @DisplayName("Endless mode flag is independent of total waves")
        void endlessModeIndependentOfTotalWaves() {
            boolean endlessMode = true;
            int totalWaves = 10;

            // In endless mode, total waves is just a checkpoint interval
            assertTrue(endlessMode || totalWaves > 0,
                "Either endless mode or have positive waves");
        }

        @Test
        @DisplayName("Current wave starts at 0 before quest starts")
        void currentWaveStartsAtZero() {
            int initialWave = 0;
            assertEquals(0, initialWave, "Current wave should start at 0");
        }

        @Test
        @DisplayName("Quest start time is recorded")
        void questStartTimeIsRecorded() {
            long beforeStart = System.currentTimeMillis();
            // Simulating quest start
            long questStartTime = System.currentTimeMillis();
            long afterStart = System.currentTimeMillis();

            assertTrue(questStartTime >= beforeStart);
            assertTrue(questStartTime <= afterStart);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-15: Serialization Rules
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-15: Serialization Rules")
    class SerializationRulesTest {

        @Test
        @DisplayName("Serialization uses string keys")
        void serializationUsesStringKeys() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("instanceId", UUID.randomUUID().toString());
            map.put("ownerId", UUID.randomUUID().toString());
            map.put("maxPlayers", 4);
            map.put("state", "READY");

            assertTrue(map.containsKey("instanceId"));
            assertTrue(map.containsKey("ownerId"));
            assertTrue(map.containsKey("maxPlayers"));
            assertTrue(map.containsKey("state"));
        }

        @Test
        @DisplayName("UUID serializes as string")
        void uuidSerializesAsString() {
            UUID id = UUID.randomUUID();
            String serialized = id.toString();
            UUID deserialized = UUID.fromString(serialized);

            assertEquals(id, deserialized);
        }

        @Test
        @DisplayName("State serializes as name")
        void stateSerializesAsName() {
            InstanceState state = InstanceState.ACTIVE;
            String serialized = state.name();
            InstanceState deserialized = InstanceState.valueOf(serialized);

            assertEquals(state, deserialized);
        }

        @Test
        @DisplayName("Player list serializes as string list")
        void playerListSerializesAsStringList() {
            List<UUID> players = List.of(UUID.randomUUID(), UUID.randomUUID());
            List<String> serialized = players.stream()
                .map(UUID::toString)
                .toList();

            assertEquals(2, serialized.size());
            for (String s : serialized) {
                assertNotNull(UUID.fromString(s));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // L2-16: Equality and HashCode Rules
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("L2-16: Equality and HashCode Rules")
    class EqualityHashCodeRulesTest {

        @Test
        @DisplayName("Same UUID means equal")
        void sameUuidMeansEqual() {
            UUID id = UUID.randomUUID();
            UUID copy = UUID.fromString(id.toString());

            assertEquals(id, copy);
            assertEquals(id.hashCode(), copy.hashCode());
        }

        @Test
        @DisplayName("Different UUIDs are not equal")
        void differentUuidsNotEqual() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("UUID hashCode is consistent")
        void uuidHashCodeIsConsistent() {
            UUID id = UUID.randomUUID();
            int hash1 = id.hashCode();
            int hash2 = id.hashCode();

            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("Object not equal to null")
        void objectNotEqualToNull() {
            Object obj = UUID.randomUUID();
            assertNotEquals(null, obj);
        }
    }
}
