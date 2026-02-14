package com.devmod.combat;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExecutionSystem")
public class ExecutionSystemTest {

    private ExecutionSystem system;

    @BeforeEach
    void setUp() {
        system = ExecutionSystem.INSTANCE;
        system.reset();
        system.clearOverrides();
    }

    // ===========================================================
    // ExecutionState tests (pure POJO, no Minecraft dependencies)
    // ===========================================================

    @Nested
    @DisplayName("ExecutionState")
    class ExecutionStateTests {

        @Test
        @DisplayName("Constructor sets all fields correctly")
        void constructorSetsFields() {
            UUID playerId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            var state = new ExecutionSystem.ExecutionState(playerId, targetId, 40);

            assertEquals(playerId, state.getPlayerId());
            assertEquals(targetId, state.getTargetId());
            assertTrue(state.getStartTime() > 0);
            assertEquals(40, state.getDurationTicks());
            assertEquals(40, state.getTicksRemaining());
        }

        @Test
        @DisplayName("decrementTicksRemaining reduces count by 1")
        void decrementReducesByOne() {
            var state = new ExecutionSystem.ExecutionState(
                    UUID.randomUUID(), UUID.randomUUID(), 40);
            assertEquals(40, state.getTicksRemaining());

            state.decrementTicksRemaining();
            assertEquals(39, state.getTicksRemaining());

            state.decrementTicksRemaining();
            assertEquals(38, state.getTicksRemaining());
        }

        @Test
        @DisplayName("getProgress starts at 0 and reaches 1.0 when done")
        void progressCalculation() {
            var state = new ExecutionSystem.ExecutionState(
                    UUID.randomUUID(), UUID.randomUUID(), 20);

            assertEquals(0.0f, state.getProgress(), 0.001f);

            // Tick halfway
            for (int i = 0; i < 10; i++) {
                state.decrementTicksRemaining();
            }
            assertEquals(0.5f, state.getProgress(), 0.001f);

            // Tick to completion
            for (int i = 0; i < 10; i++) {
                state.decrementTicksRemaining();
            }
            assertEquals(1.0f, state.getProgress(), 0.001f);
        }

        @Test
        @DisplayName("Progress is linear")
        void progressIsLinear() {
            int duration = 100;
            var state = new ExecutionSystem.ExecutionState(
                    UUID.randomUUID(), UUID.randomUUID(), duration);

            for (int i = 1; i <= duration; i++) {
                state.decrementTicksRemaining();
                float expected = (float) i / duration;
                assertEquals(expected, state.getProgress(), 0.001f,
                        "Progress at tick " + i + " should be " + expected);
            }
        }

        @Test
        @DisplayName("Null playerId throws")
        void nullPlayerIdThrows() {
            assertThrows(Exception.class, () ->
                    new ExecutionSystem.ExecutionState(null, UUID.randomUUID(), 40));
        }

        @Test
        @DisplayName("Null targetId throws")
        void nullTargetIdThrows() {
            assertThrows(Exception.class, () ->
                    new ExecutionSystem.ExecutionState(UUID.randomUUID(), null, 40));
        }
    }

    // ===========================================================
    // ExecutionResult tests (pure record, no Minecraft)
    // ===========================================================

    @Nested
    @DisplayName("ExecutionResult")
    class ExecutionResultTests {

        @Test
        @DisplayName("fail() creates unsuccessful result")
        void failResult() {
            var result = ExecutionSystem.ExecutionResult.fail("Not ready");
            assertFalse(result.success());
            assertEquals("Not ready", result.message());
            assertEquals(0, result.styleGained());
            assertEquals(0f, result.hpRestored(), 0.001f);
            assertFalse(result.wasInterrupted());
        }

        @Test
        @DisplayName("success() creates successful result")
        void successResult() {
            var result = ExecutionSystem.ExecutionResult.success(200, 1.5f);
            assertTrue(result.success());
            assertEquals("EXECUTED!", result.message());
            assertEquals(200, result.styleGained());
            assertEquals(1.5f, result.hpRestored(), 0.001f);
            assertFalse(result.wasInterrupted());
        }

        @Test
        @DisplayName("interrupted() creates interrupted result")
        void interruptedResult() {
            var result = ExecutionSystem.ExecutionResult.interrupted();
            assertFalse(result.success());
            assertEquals("INTERRUPTED!", result.message());
            assertTrue(result.wasInterrupted());
            assertEquals(0, result.styleGained());
        }
    }

    // ===========================================================
    // Cooldown tracking tests (via direct map access)
    // ===========================================================

    @Nested
    @DisplayName("getRemainingCooldown")
    class CooldownTests {

        @Test
        @DisplayName("No cooldown returns 0")
        void noCooldownReturnsZero() {
            UUID playerId = UUID.randomUUID();
            assertEquals(0, system.getRemainingCooldown(playerId));
        }

        @Test
        @DisplayName("Active cooldown returns positive value")
        void activeCooldownReturnsPositive() throws Exception {
            UUID playerId = UUID.randomUUID();
            // Set cooldown end to 60 seconds in the future
            setCooldown(playerId, System.currentTimeMillis() + 60_000L);

            assertTrue(system.getRemainingCooldown(playerId) > 0);
        }

        @Test
        @DisplayName("Expired cooldown returns 0 (not negative)")
        void expiredCooldownReturnsZero() throws Exception {
            UUID playerId = UUID.randomUUID();
            // Set cooldown end to the past
            setCooldown(playerId, System.currentTimeMillis() - 1000L);

            assertEquals(0, system.getRemainingCooldown(playerId));
        }
    }

    // ===========================================================
    // getExecutionState
    // ===========================================================

    @Nested
    @DisplayName("getExecutionState")
    class GetExecutionStateTests {

        @Test
        @DisplayName("Returns empty for unknown player")
        void unknownPlayerReturnsEmpty() {
            Optional<ExecutionSystem.ExecutionState> state =
                    system.getExecutionState(UUID.randomUUID());
            assertTrue(state.isEmpty());
        }

        @Test
        @DisplayName("Returns state after insertion")
        void returnsStateAfterInsertion() throws Exception {
            UUID playerId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            var execState = new ExecutionSystem.ExecutionState(playerId, targetId, 40);
            putActiveExecution(playerId, execState);

            Optional<ExecutionSystem.ExecutionState> retrieved = system.getExecutionState(playerId);
            assertTrue(retrieved.isPresent());
            assertEquals(targetId, retrieved.get().getTargetId());
        }
    }

    // ===========================================================
    // onPlayerLeave / reset
    // ===========================================================

    @Nested
    @DisplayName("State cleanup")
    class StateCleanupTests {

        @Test
        @DisplayName("onPlayerLeave removes execution and cooldown")
        void onPlayerLeaveClearsState() throws Exception {
            UUID playerId = UUID.randomUUID();
            putActiveExecution(playerId,
                    new ExecutionSystem.ExecutionState(playerId, UUID.randomUUID(), 40));
            setCooldown(playerId, System.currentTimeMillis() + 60_000L);

            system.onPlayerLeave(playerId);

            assertTrue(system.getExecutionState(playerId).isEmpty());
            assertEquals(0, system.getRemainingCooldown(playerId));
        }

        @Test
        @DisplayName("onPlayerLeave for unknown player is safe")
        void onPlayerLeaveUnknownSafe() {
            assertDoesNotThrow(() -> system.onPlayerLeave(UUID.randomUUID()));
        }

        @Test
        @DisplayName("reset clears all state")
        void resetClearsAll() throws Exception {
            UUID p1 = UUID.randomUUID();
            UUID p2 = UUID.randomUUID();
            putActiveExecution(p1, new ExecutionSystem.ExecutionState(p1, UUID.randomUUID(), 40));
            putActiveExecution(p2, new ExecutionSystem.ExecutionState(p2, UUID.randomUUID(), 40));
            setCooldown(p1, System.currentTimeMillis() + 60_000L);
            setCooldown(p2, System.currentTimeMillis() + 60_000L);

            system.reset();

            assertTrue(system.getExecutionState(p1).isEmpty());
            assertTrue(system.getExecutionState(p2).isEmpty());
            assertEquals(0, system.getRemainingCooldown(p1));
            assertEquals(0, system.getRemainingCooldown(p2));
        }

        @Test
        @DisplayName("Double reset is safe")
        void doubleResetSafe() {
            system.reset();
            assertDoesNotThrow(() -> system.reset());
        }
    }

    // ===========================================================
    // Helpers: reflective access to private maps for unit testing
    // ===========================================================

    @SuppressWarnings("unchecked")
    private void setCooldown(UUID playerId, long endTimeMillis) throws Exception {
        Field cooldownsField = ExecutionSystem.class.getDeclaredField("cooldowns");
        cooldownsField.setAccessible(true);
        Map<UUID, Long> cooldowns = (Map<UUID, Long>) cooldownsField.get(system);
        cooldowns.put(playerId, endTimeMillis);
    }

    @SuppressWarnings("unchecked")
    private void putActiveExecution(UUID playerId, ExecutionSystem.ExecutionState state) throws Exception {
        Field activeField = ExecutionSystem.class.getDeclaredField("activeExecutions");
        activeField.setAccessible(true);
        Map<UUID, ExecutionSystem.ExecutionState> active =
                (Map<UUID, ExecutionSystem.ExecutionState>) activeField.get(system);
        active.put(playerId, state);
    }
}
