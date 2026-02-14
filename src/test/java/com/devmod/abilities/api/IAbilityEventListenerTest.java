package com.devmod.abilities.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.devmod.abilities.DodgeAbilitySystem.DodgeDirection;

/**
 * Tests for IAbilityEventListener default method behavior.
 */
class IAbilityEventListenerTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final long TICK = 99L;

    // ------------------------------------------------------------------
    // Default methods return false / no-op
    // ------------------------------------------------------------------

    @Test
    void defaultOnDodgePreReturnsFalse() {
        IAbilityEventListener listener = new IAbilityEventListener() {};
        assertFalse(listener.onDodgePre(new AbilityEvent.DodgePre(PLAYER, TICK, DodgeDirection.LEFT, 2)));
    }

    @Test
    void defaultOnDashPreReturnsFalse() {
        IAbilityEventListener listener = new IAbilityEventListener() {};
        assertFalse(listener.onDashPre(new AbilityEvent.DashPre(PLAYER, TICK, 1)));
    }

    @Test
    void defaultOnDodgePostDoesNotThrow() {
        IAbilityEventListener listener = new IAbilityEventListener() {};
        assertDoesNotThrow(() ->
                listener.onDodgePost(new AbilityEvent.DodgePost(PLAYER, TICK, null, 1, 1)));
    }

    @Test
    void defaultOnDashPostDoesNotThrow() {
        IAbilityEventListener listener = new IAbilityEventListener() {};
        assertDoesNotThrow(() ->
                listener.onDashPost(new AbilityEvent.DashPost(PLAYER, TICK, 0, 1)));
    }

    @Test
    void defaultOnPerfectDodgeDoesNotThrow() {
        IAbilityEventListener listener = new IAbilityEventListener() {};
        assertDoesNotThrow(() ->
                listener.onPerfectDodge(new AbilityEvent.PerfectDodge(PLAYER, TICK, 1f, "src", 1)));
    }

    // ------------------------------------------------------------------
    // onEvent dispatches to specific handlers
    // ------------------------------------------------------------------

    @Test
    void onEventDispatchesDodgePre() {
        List<String> calls = new ArrayList<>();
        IAbilityEventListener listener = new IAbilityEventListener() {
            @Override
            public boolean onDodgePre(AbilityEvent.DodgePre event) {
                calls.add("dodgePre");
                return false;
            }
        };

        listener.onEvent(new AbilityEvent.DodgePre(PLAYER, TICK, DodgeDirection.RIGHT, 2));
        assertEquals(List.of("dodgePre"), calls);
    }

    @Test
    void onEventDispatchesDodgePost() {
        List<String> calls = new ArrayList<>();
        IAbilityEventListener listener = new IAbilityEventListener() {
            @Override
            public void onDodgePost(AbilityEvent.DodgePost event) {
                calls.add("dodgePost");
            }
        };

        listener.onEvent(new AbilityEvent.DodgePost(PLAYER, TICK, null, 1, 1));
        assertEquals(List.of("dodgePost"), calls);
    }

    @Test
    void onEventDispatchesDashPre() {
        List<String> calls = new ArrayList<>();
        IAbilityEventListener listener = new IAbilityEventListener() {
            @Override
            public boolean onDashPre(AbilityEvent.DashPre event) {
                calls.add("dashPre");
                return false;
            }
        };

        listener.onEvent(new AbilityEvent.DashPre(PLAYER, TICK, 2));
        assertEquals(List.of("dashPre"), calls);
    }

    @Test
    void onEventDispatchesDashPost() {
        List<String> calls = new ArrayList<>();
        IAbilityEventListener listener = new IAbilityEventListener() {
            @Override
            public void onDashPost(AbilityEvent.DashPost event) {
                calls.add("dashPost");
            }
        };

        listener.onEvent(new AbilityEvent.DashPost(PLAYER, TICK, 1, 3));
        assertEquals(List.of("dashPost"), calls);
    }

    @Test
    void onEventDispatchesPerfectDodge() {
        List<String> calls = new ArrayList<>();
        IAbilityEventListener listener = new IAbilityEventListener() {
            @Override
            public void onPerfectDodge(AbilityEvent.PerfectDodge event) {
                calls.add("perfectDodge");
            }
        };

        listener.onEvent(new AbilityEvent.PerfectDodge(PLAYER, TICK, 4f, "test", 2));
        assertEquals(List.of("perfectDodge"), calls);
    }

    // ------------------------------------------------------------------
    // onEvent default dispatches all 5 types without error
    // ------------------------------------------------------------------

    @Test
    void defaultOnEventHandlesAllEventTypes() {
        IAbilityEventListener listener = new IAbilityEventListener() {};

        assertDoesNotThrow(() -> {
            listener.onEvent(new AbilityEvent.DodgePre(PLAYER, TICK, null, 2));
            listener.onEvent(new AbilityEvent.DodgePost(PLAYER, TICK, null, 1, 1));
            listener.onEvent(new AbilityEvent.DashPre(PLAYER, TICK, 2));
            listener.onEvent(new AbilityEvent.DashPost(PLAYER, TICK, 1, 1));
            listener.onEvent(new AbilityEvent.PerfectDodge(PLAYER, TICK, 1f, "x", 0));
        });
    }

    // ------------------------------------------------------------------
    // Selective override: only overridden methods are called
    // ------------------------------------------------------------------

    @Test
    void selectiveOverrideOnlyCallsRelevantHandler() {
        List<String> calls = new ArrayList<>();
        IAbilityEventListener listener = new IAbilityEventListener() {
            @Override
            public void onPerfectDodge(AbilityEvent.PerfectDodge event) {
                calls.add("perfectDodge");
            }
        };

        // These should not trigger "perfectDodge"
        listener.onEvent(new AbilityEvent.DodgePre(PLAYER, TICK, null, 2));
        listener.onEvent(new AbilityEvent.DashPost(PLAYER, TICK, 0, 1));
        assertTrue(calls.isEmpty());

        // This one should
        listener.onEvent(new AbilityEvent.PerfectDodge(PLAYER, TICK, 1f, "src", 1));
        assertEquals(List.of("perfectDodge"), calls);
    }
}
