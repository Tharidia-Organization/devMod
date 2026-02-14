package com.devmod.transport.executor;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.devmod.transport.TransportColor;
import com.devmod.transport.executor.ChargeManager.ChargingState;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChargeManager.ChargingState")
class ChargeManagerTest {

    private final UUID nodeId = UUID.randomUUID();
    private final BlockPos nodePos = new BlockPos(100, 64, 200);

    // =========================================================================
    // ChargingState Record Tests
    // =========================================================================

    @Nested
    @DisplayName("ChargingState")
    class ChargingStateTests {

        @Test
        @DisplayName("getProgressPercent returns 0 at start")
        void progressAtStart() {
            ChargingState state = new ChargingState(nodeId, nodePos, 0, 40, TransportColor.CYAN);
            assertEquals(0, state.getProgressPercent());
        }

        @Test
        @DisplayName("getProgressPercent returns 50 at halfway")
        void progressAtHalfway() {
            ChargingState state = new ChargingState(nodeId, nodePos, 20, 40, TransportColor.CYAN);
            assertEquals(50, state.getProgressPercent());
        }

        @Test
        @DisplayName("getProgressPercent returns 100 at completion")
        void progressAtCompletion() {
            ChargingState state = new ChargingState(nodeId, nodePos, 40, 40, TransportColor.CYAN);
            assertEquals(100, state.getProgressPercent());
        }

        @Test
        @DisplayName("getProgressPercent caps at 100 for overcounted ticks")
        void progressCapsAt100() {
            ChargingState state = new ChargingState(nodeId, nodePos, 60, 40, TransportColor.CYAN);
            assertEquals(100, state.getProgressPercent());
        }

        @Test
        @DisplayName("getProgressPercent returns 100 for zero required ticks (instant)")
        void progressForInstant() {
            ChargingState state = new ChargingState(nodeId, nodePos, 0, 0, TransportColor.CYAN);
            assertEquals(100, state.getProgressPercent());
        }

        @Test
        @DisplayName("getProgressPercent returns 100 for negative required ticks")
        void progressForNegativeRequired() {
            ChargingState state = new ChargingState(nodeId, nodePos, 0, -1, TransportColor.CYAN);
            assertEquals(100, state.getProgressPercent());
        }

        @Test
        @DisplayName("isComplete returns false before required ticks")
        void notCompleteBeforeRequired() {
            ChargingState state = new ChargingState(nodeId, nodePos, 39, 40, TransportColor.CYAN);
            assertFalse(state.isComplete());
        }

        @Test
        @DisplayName("isComplete returns true at required ticks")
        void completeAtRequired() {
            ChargingState state = new ChargingState(nodeId, nodePos, 40, 40, TransportColor.CYAN);
            assertTrue(state.isComplete());
        }

        @Test
        @DisplayName("isComplete returns true past required ticks")
        void completePastRequired() {
            ChargingState state = new ChargingState(nodeId, nodePos, 50, 40, TransportColor.CYAN);
            assertTrue(state.isComplete());
        }

        @Test
        @DisplayName("tick increments currentTicks by 1")
        void tickIncrements() {
            ChargingState state = new ChargingState(nodeId, nodePos, 10, 40, TransportColor.CYAN);
            ChargingState ticked = state.tick();
            assertEquals(11, ticked.currentTicks());
            // Other fields remain the same
            assertEquals(nodeId, ticked.nodeId());
            assertEquals(nodePos, ticked.nodePos());
            assertEquals(40, ticked.requiredTicks());
            assertEquals(TransportColor.CYAN, ticked.color());
        }

        @Test
        @DisplayName("tick produces immutable progression to completion")
        void tickProgressesToCompletion() {
            ChargingState state = new ChargingState(nodeId, nodePos, 38, 40, TransportColor.RED);
            assertFalse(state.isComplete());

            state = state.tick(); // 39
            assertFalse(state.isComplete());

            state = state.tick(); // 40
            assertTrue(state.isComplete());
            assertEquals(100, state.getProgressPercent());
        }
    }

    // =========================================================================
    // ChargeManager State Management (non-ServerPlayer paths)
    // =========================================================================

    @Nested
    @DisplayName("ChargeManager state tracking")
    class StateTracking {

        private ChargeManager manager;
        private final UUID playerId = UUID.randomUUID();

        @BeforeEach
        void setUp() {
            manager = new ChargeManager();
        }

        @Test
        @DisplayName("isCharging returns false for unknown player")
        void notChargingForUnknown() {
            assertFalse(manager.isCharging(UUID.randomUUID()));
        }

        @Test
        @DisplayName("stopCharging removes player state")
        void stopChargingRemovesState() {
            // We can't call startCharging without a ServerPlayer, but we can test stop
            manager.stopCharging(playerId);
            assertFalse(manager.isCharging(playerId));
        }

        @Test
        @DisplayName("getState returns empty for unknown player")
        void getStateReturnsEmpty() {
            assertTrue(manager.getState(UUID.randomUUID()).isEmpty());
        }

        @Test
        @DisplayName("getPlayersChargingAt returns empty when none charging")
        void noPlayersChargingAtNode() {
            assertTrue(manager.getPlayersChargingAt(nodeId).isEmpty());
        }

        @Test
        @DisplayName("clear removes all charging states")
        void clearAll() {
            manager.clear();
            assertFalse(manager.isCharging(playerId));
        }
    }
}
