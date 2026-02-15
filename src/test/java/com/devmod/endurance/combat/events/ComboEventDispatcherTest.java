package com.devmod.endurance.combat.events;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.endurance.combat.api.ComboEvent;
import com.devmod.endurance.combat.api.IComboEventListener;

import static org.junit.jupiter.api.Assertions.*;

class ComboEventDispatcherTest {

    private ComboEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ComboEventDispatcher();
    }

    @Test
    @DisplayName("Starts with zero listeners")
    void startsEmpty() {
        assertEquals(0, dispatcher.getListenerCount());
    }

    @Test
    @DisplayName("addListener increments count")
    void addIncrementsCount() {
        dispatcher.addListener(new NoopListener());
        assertEquals(1, dispatcher.getListenerCount());
        dispatcher.addListener(new NoopListener());
        assertEquals(2, dispatcher.getListenerCount());
    }

    @Test
    @DisplayName("addListener throws on null")
    void addNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> dispatcher.addListener(null));
    }

    @Test
    @DisplayName("removeListener decrements count and returns true")
    void removeReturnsTrue() {
        IComboEventListener listener = new NoopListener();
        dispatcher.addListener(listener);
        assertTrue(dispatcher.removeListener(listener));
        assertEquals(0, dispatcher.getListenerCount());
    }

    @Test
    @DisplayName("removeListener returns false for unknown listener")
    void removeReturnsFalse() {
        assertFalse(dispatcher.removeListener(new NoopListener()));
    }

    @Test
    @DisplayName("clearListeners removes all")
    void clearAll() {
        dispatcher.addListener(new NoopListener());
        dispatcher.addListener(new NoopListener());
        dispatcher.clearListeners();
        assertEquals(0, dispatcher.getListenerCount());
    }

    @Test
    @DisplayName("hasListener checks presence")
    void hasListenerCheck() {
        IComboEventListener listener = new NoopListener();
        assertFalse(dispatcher.hasListener(listener));
        dispatcher.addListener(listener);
        assertTrue(dispatcher.hasListener(listener));
    }

    @Test
    @DisplayName("hasListenerOfType checks by class")
    void hasListenerOfTypeCheck() {
        assertFalse(dispatcher.hasListenerOfType(NoopListener.class));
        dispatcher.addListener(new NoopListener());
        assertTrue(dispatcher.hasListenerOfType(NoopListener.class));
    }

    @Test
    @DisplayName("dispatch delivers event to all listeners")
    void dispatchToAll() {
        RecordingListener l1 = new RecordingListener();
        RecordingListener l2 = new RecordingListener();
        dispatcher.addListener(l1);
        dispatcher.addListener(l2);

        ComboEvent event = testEvent();
        dispatcher.dispatch(event);

        assertEquals(1, l1.events.size());
        assertEquals(1, l2.events.size());
        assertSame(event, l1.events.get(0));
    }

    @Test
    @DisplayName("dispatch null event is silently ignored")
    void dispatchNullIgnored() {
        RecordingListener l = new RecordingListener();
        dispatcher.addListener(l);
        dispatcher.dispatch(null);
        assertTrue(l.events.isEmpty());
    }

    @Test
    @DisplayName("Exception in one listener does not prevent others from receiving event")
    void exceptionIsolation() {
        RecordingListener before = new RecordingListener();
        IComboEventListener thrower = new IComboEventListener() {
            @Override
            public void onEvent(ComboEvent event) {
                throw new RuntimeException("Test exception");
            }
        };
        RecordingListener after = new RecordingListener();

        dispatcher.addListener(before);
        dispatcher.addListener(thrower);
        dispatcher.addListener(after);

        dispatcher.dispatch(testEvent());

        assertEquals(1, before.events.size());
        assertEquals(1, after.events.size()); // Should still receive event
    }

    @Test
    @DisplayName("dispatchAll sends multiple events in order")
    void dispatchAllInOrder() {
        RecordingListener l = new RecordingListener();
        dispatcher.addListener(l);

        UUID player = UUID.randomUUID();
        UUID quest = UUID.randomUUID();
        List<ComboEvent> events = List.of(
            new ComboEvent.SessionStarted(player, quest, 1L),
            new ComboEvent.ComboIncreased(player, quest, 2L, 0, 1),
            new ComboEvent.ComboIncreased(player, quest, 3L, 1, 2)
        );

        dispatcher.dispatchAll(events);

        assertEquals(3, l.events.size());
        assertInstanceOf(ComboEvent.SessionStarted.class, l.events.get(0));
        assertInstanceOf(ComboEvent.ComboIncreased.class, l.events.get(1));
    }

    private static ComboEvent testEvent() {
        return new ComboEvent.SessionStarted(UUID.randomUUID(), UUID.randomUUID(), System.currentTimeMillis());
    }

    private static class NoopListener implements IComboEventListener {}

    private static class RecordingListener implements IComboEventListener {
        final List<ComboEvent> events = new ArrayList<>();

        @Override
        public void onEvent(ComboEvent event) {
            events.add(event);
        }
    }
}
