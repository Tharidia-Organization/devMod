package com.devmod.abilities.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.devmod.abilities.DodgeAbilitySystem.DodgeDirection;

/**
 * Tests for AbilityEventDispatcher singleton.
 */
class AbilityEventDispatcherTest {

    private final AbilityEventDispatcher dispatcher = AbilityEventDispatcher.INSTANCE;
    private static final UUID PLAYER = UUID.randomUUID();
    private static final long TICK = 100L;

    @BeforeEach
    void setUp() {
        dispatcher.clearListeners();
    }

    @AfterEach
    void tearDown() {
        dispatcher.clearListeners();
    }

    // ------------------------------------------------------------------
    // addListener / removeListener / getListenerCount / clearListeners
    // ------------------------------------------------------------------

    @Test
    void addListenerIncrementsCount() {
        assertEquals(0, dispatcher.getListenerCount());
        dispatcher.addListener(new NoOpListener());
        assertEquals(1, dispatcher.getListenerCount());
    }

    @Test
    void addNullListenerThrows() {
        assertThrows(IllegalArgumentException.class, () -> dispatcher.addListener(null));
    }

    @Test
    void removeListenerDecrementsCount() {
        IAbilityEventListener listener = new NoOpListener();
        dispatcher.addListener(listener);
        assertTrue(dispatcher.removeListener(listener));
        assertEquals(0, dispatcher.getListenerCount());
    }

    @Test
    void removeUnknownListenerReturnsFalse() {
        assertFalse(dispatcher.removeListener(new NoOpListener()));
    }

    @Test
    void clearListenersResetsCount() {
        dispatcher.addListener(new NoOpListener());
        dispatcher.addListener(new NoOpListener());
        dispatcher.clearListeners();
        assertEquals(0, dispatcher.getListenerCount());
    }

    // ------------------------------------------------------------------
    // fireDodgePre — cancellation
    // ------------------------------------------------------------------

    @Test
    void fireDodgePreNotCancelledByDefault() {
        dispatcher.addListener(new NoOpListener());
        var event = new AbilityEvent.DodgePre(PLAYER, TICK, DodgeDirection.LEFT, 2);
        assertFalse(dispatcher.fireDodgePre(event));
    }

    @Test
    void fireDodgePreCancelledWhenListenerReturnsTrue() {
        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public boolean onDodgePre(AbilityEvent.DodgePre event) { return true; }
        });
        var event = new AbilityEvent.DodgePre(PLAYER, TICK, DodgeDirection.LEFT, 2);
        assertTrue(dispatcher.fireDodgePre(event));
    }

    @Test
    void fireDodgePreShortCircuitsOnFirstCancel() {
        AtomicBoolean secondCalled = new AtomicBoolean(false);

        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public boolean onDodgePre(AbilityEvent.DodgePre event) { return true; }
        });
        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public boolean onDodgePre(AbilityEvent.DodgePre event) {
                secondCalled.set(true);
                return false;
            }
        });

        var event = new AbilityEvent.DodgePre(PLAYER, TICK, null, 1);
        assertTrue(dispatcher.fireDodgePre(event));
        assertFalse(secondCalled.get(), "Second listener should not be called after cancellation");
    }

    // ------------------------------------------------------------------
    // fireDashPre — cancellation
    // ------------------------------------------------------------------

    @Test
    void fireDashPreNotCancelledByDefault() {
        dispatcher.addListener(new NoOpListener());
        var event = new AbilityEvent.DashPre(PLAYER, TICK, 2);
        assertFalse(dispatcher.fireDashPre(event));
    }

    @Test
    void fireDashPreCancelledWhenListenerReturnsTrue() {
        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public boolean onDashPre(AbilityEvent.DashPre event) { return true; }
        });
        var event = new AbilityEvent.DashPre(PLAYER, TICK, 1);
        assertTrue(dispatcher.fireDashPre(event));
    }

    // ------------------------------------------------------------------
    // fireDodgePost / fireDashPost / firePerfectDodge — non-cancellable
    // ------------------------------------------------------------------

    @Test
    void fireDodgePostDispatchesToOnEvent() {
        AtomicReference<AbilityEvent> received = new AtomicReference<>();
        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public void onEvent(AbilityEvent event) { received.set(event); }
        });

        var event = new AbilityEvent.DodgePost(PLAYER, TICK, DodgeDirection.RIGHT, 1, 5);
        dispatcher.fireDodgePost(event);
        assertSame(event, received.get());
    }

    @Test
    void fireDashPostDispatchesToOnEvent() {
        AtomicReference<AbilityEvent> received = new AtomicReference<>();
        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public void onEvent(AbilityEvent event) { received.set(event); }
        });

        var event = new AbilityEvent.DashPost(PLAYER, TICK, 0, 3);
        dispatcher.fireDashPost(event);
        assertSame(event, received.get());
    }

    @Test
    void firePerfectDodgeDispatchesToOnEvent() {
        AtomicReference<AbilityEvent> received = new AtomicReference<>();
        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public void onEvent(AbilityEvent event) { received.set(event); }
        });

        var event = new AbilityEvent.PerfectDodge(PLAYER, TICK, 5.0f, "mob", 1);
        dispatcher.firePerfectDodge(event);
        assertSame(event, received.get());
    }

    // ------------------------------------------------------------------
    // Exception isolation
    // ------------------------------------------------------------------

    @Test
    void exceptionInPreListenerDoesNotBlockOthers() {
        AtomicBoolean secondCalled = new AtomicBoolean(false);

        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public boolean onDodgePre(AbilityEvent.DodgePre event) {
                throw new RuntimeException("boom");
            }
        });
        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public boolean onDodgePre(AbilityEvent.DodgePre event) {
                secondCalled.set(true);
                return false;
            }
        });

        var event = new AbilityEvent.DodgePre(PLAYER, TICK, null, 1);
        assertFalse(dispatcher.fireDodgePre(event), "Exception should not count as cancellation");
        assertTrue(secondCalled.get(), "Second listener should still be called");
    }

    @Test
    void exceptionInPostListenerDoesNotBlockOthers() {
        List<AbilityEvent> received = new ArrayList<>();

        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public void onEvent(AbilityEvent event) {
                throw new RuntimeException("boom");
            }
        });
        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public void onEvent(AbilityEvent event) { received.add(event); }
        });

        var event = new AbilityEvent.DodgePost(PLAYER, TICK, null, 1, 1);
        dispatcher.fireDodgePost(event);
        assertEquals(1, received.size());
    }

    @Test
    void exceptionInDashPreListenerDoesNotBlockOthers() {
        AtomicBoolean secondCalled = new AtomicBoolean(false);

        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public boolean onDashPre(AbilityEvent.DashPre event) {
                throw new RuntimeException("fail");
            }
        });
        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public boolean onDashPre(AbilityEvent.DashPre event) {
                secondCalled.set(true);
                return false;
            }
        });

        var event = new AbilityEvent.DashPre(PLAYER, TICK, 2);
        assertFalse(dispatcher.fireDashPre(event));
        assertTrue(secondCalled.get());
    }

    // ------------------------------------------------------------------
    // No listeners
    // ------------------------------------------------------------------

    @Test
    void fireEventsWithNoListenersDoesNotThrow() {
        assertFalse(dispatcher.fireDodgePre(new AbilityEvent.DodgePre(PLAYER, TICK, null, 2)));
        assertFalse(dispatcher.fireDashPre(new AbilityEvent.DashPre(PLAYER, TICK, 2)));
        assertDoesNotThrow(() -> dispatcher.fireDodgePost(
                new AbilityEvent.DodgePost(PLAYER, TICK, null, 1, 1)));
        assertDoesNotThrow(() -> dispatcher.fireDashPost(
                new AbilityEvent.DashPost(PLAYER, TICK, 1, 1)));
        assertDoesNotThrow(() -> dispatcher.firePerfectDodge(
                new AbilityEvent.PerfectDodge(PLAYER, TICK, 1f, "x", 0)));
    }

    // ------------------------------------------------------------------
    // Multiple listeners see the same event
    // ------------------------------------------------------------------

    @Test
    void multipleListenersAllReceivePostEvent() {
        List<AbilityEvent> first = new ArrayList<>();
        List<AbilityEvent> second = new ArrayList<>();

        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public void onEvent(AbilityEvent event) { first.add(event); }
        });
        dispatcher.addListener(new IAbilityEventListener() {
            @Override
            public void onEvent(AbilityEvent event) { second.add(event); }
        });

        var event = new AbilityEvent.PerfectDodge(PLAYER, TICK, 2f, "src", 1);
        dispatcher.firePerfectDodge(event);

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        assertSame(event, first.get(0));
        assertSame(event, second.get(0));
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private static class NoOpListener implements IAbilityEventListener {}
}
