package com.devmod.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerInstanceStateDirectTest {

    @Test
    @DisplayName("Player state transitions follow allowed flow and recovery")
    void transitionsFollowAllowedFlow() {
        assertTrue(PlayerInstanceState.NORMAL.canTransitionTo(PlayerInstanceState.PREPARING));
        assertTrue(PlayerInstanceState.PREPARING.canTransitionTo(PlayerInstanceState.IN_TRANSIT));
        assertTrue(PlayerInstanceState.IN_TRANSIT.canTransitionTo(PlayerInstanceState.IN_INSTANCE));
        assertTrue(PlayerInstanceState.IN_INSTANCE.canTransitionTo(PlayerInstanceState.RETURNING));

        for (PlayerInstanceState state : PlayerInstanceState.values()) {
            assertTrue(state.canTransitionTo(PlayerInstanceState.NORMAL));
        }
    }

    @Test
    @DisplayName("Invalid player state transitions are rejected")
    void invalidTransitionsAreRejected() {
        assertFalse(PlayerInstanceState.PREPARING.canTransitionTo(PlayerInstanceState.IN_INSTANCE));
        assertFalse(PlayerInstanceState.RETURNING.canTransitionTo(PlayerInstanceState.IN_INSTANCE));
    }

    @Test
    @DisplayName("Player state flags reflect recovery and instance flow")
    void playerStateFlagsReflectLifecycle() {
        assertFalse(PlayerInstanceState.NORMAL.requiresSnapshot());
        assertTrue(PlayerInstanceState.IN_TRANSIT.requiresSnapshot());

        assertFalse(PlayerInstanceState.NORMAL.isInInstanceFlow());
        assertFalse(PlayerInstanceState.PREPARING.isInInstanceFlow());
        assertTrue(PlayerInstanceState.IN_TRANSIT.isInInstanceFlow());
        assertTrue(PlayerInstanceState.IN_INSTANCE.isInInstanceFlow());
        assertTrue(PlayerInstanceState.RETURNING.isInInstanceFlow());
    }
}
