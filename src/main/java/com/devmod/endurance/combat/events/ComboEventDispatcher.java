package com.devmod.endurance.combat.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.endurance.combat.api.ComboEvent;
import com.devmod.endurance.combat.api.IComboEventListener;

/**
 * Event dispatcher for combo system events.
 *
 * <p>Implements the Observer pattern to decouple combo state changes from
 * dependent systems like telemetry, challenges, and notifications.</p>
 *
 * <p>Thread-safe: Uses CopyOnWriteArrayList for listener management and
 * catches exceptions from individual listeners to prevent cascade failures.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Register listener
 * dispatcher.addListener(new TelemetryComboListener());
 *
 * // Dispatch event
 * dispatcher.dispatch(new ComboEvent.RankChanged(...));
 * }</pre>
 */
public final class ComboEventDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComboEventDispatcher.class);

    private final List<IComboEventListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Add a listener to receive combo events.
     *
     * @param listener The listener to add
     * @throws IllegalArgumentException if listener is null
     */
    public void addListener(IComboEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        listeners.add(listener);
        LOGGER.debug("[ComboEvents] Added listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * Remove a listener.
     *
     * @param listener The listener to remove
     * @return true if the listener was found and removed
     */
    public boolean removeListener(IComboEventListener listener) {
        boolean removed = listeners.remove(listener);
        if (removed) {
            LOGGER.debug("[ComboEvents] Removed listener: {}", listener.getClass().getSimpleName());
        }
        return removed;
    }

    /**
     * Clear all listeners.
     */
    public void clearListeners() {
        listeners.clear();
        LOGGER.debug("[ComboEvents] All listeners cleared");
    }

    /**
     * Get current listener count.
     */
    public int getListenerCount() {
        return listeners.size();
    }

    /**
     * Dispatch an event to all registered listeners.
     *
     * <p>Exceptions from individual listeners are caught and logged,
     * preventing cascade failures.</p>
     *
     * @param event The event to dispatch
     */
    public void dispatch(ComboEvent event) {
        if (event == null) {
            LOGGER.warn("[ComboEvents] Attempted to dispatch null event");
            return;
        }

        LOGGER.trace("[ComboEvents] Dispatching {} to {} listeners",
            event.getClass().getSimpleName(), listeners.size());

        for (IComboEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOGGER.error("[ComboEvents] Listener {} threw exception handling {}: {}",
                    listener.getClass().getSimpleName(),
                    event.getClass().getSimpleName(),
                    e.getMessage(), e);
            }
        }
    }

    /**
     * Dispatch multiple events in order.
     *
     * @param events Events to dispatch
     */
    public void dispatchAll(Iterable<ComboEvent> events) {
        for (ComboEvent event : events) {
            dispatch(event);
        }
    }

    /**
     * Check if a specific listener is registered.
     *
     * @param listener The listener to check
     * @return true if registered
     */
    public boolean hasListener(IComboEventListener listener) {
        return listeners.contains(listener);
    }

    /**
     * Check if any listeners of a specific type are registered.
     *
     * @param listenerClass The listener class to check for
     * @return true if at least one listener of this type is registered
     */
    public boolean hasListenerOfType(Class<? extends IComboEventListener> listenerClass) {
        return listeners.stream().anyMatch(listenerClass::isInstance);
    }
}
