package com.devmod.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceStateDirectTest {

    @Test
    @DisplayName("Instance state transitions follow allowed graph")
    void transitionsFollowAllowedGraph() {
        assertTrue(InstanceState.CREATING.canTransitionTo(InstanceState.READY));
        assertTrue(InstanceState.CREATING.canTransitionTo(InstanceState.DESTROYING));

        assertTrue(InstanceState.READY.canTransitionTo(InstanceState.ACTIVE));
        assertTrue(InstanceState.READY.canTransitionTo(InstanceState.DESTROYING));

        assertTrue(InstanceState.ACTIVE.canTransitionTo(InstanceState.COMPLETING));
        assertTrue(InstanceState.COMPLETING.canTransitionTo(InstanceState.DESTROYING));
        assertTrue(InstanceState.DESTROYING.canTransitionTo(InstanceState.DESTROYED));
    }

    @Test
    @DisplayName("Invalid instance state transitions are rejected")
    void invalidTransitionsAreRejected() {
        assertFalse(InstanceState.ACTIVE.canTransitionTo(InstanceState.READY));
        assertFalse(InstanceState.DESTROYED.canTransitionTo(InstanceState.ACTIVE));
    }

    @Test
    @DisplayName("Instance state flags reflect lifecycle")
    void stateFlagsReflectLifecycle() {
        assertTrue(InstanceState.ACTIVE.isAlive());
        assertFalse(InstanceState.DESTROYING.isAlive());
        assertTrue(InstanceState.DESTROYED.isTerminal());
    }
}
