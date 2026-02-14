package com.devmod.abilities.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event dispatcher for ability system events.
 *
 * <p>Singleton dispatcher that allows addons to register listeners for dodge/dash events.
 * Pre-events support cancellation: if any listener returns {@code true} for a Pre-event,
 * the ability is cancelled.</p>
 *
 * <p>Thread-safe: Uses CopyOnWriteArrayList for listener management and
 * catches exceptions from individual listeners to prevent cascade failures.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Register listener (e.g., during mod init)
 * AbilityEventDispatcher.INSTANCE.addListener(new MyAbilityListener());
 *
 * // Events are dispatched automatically by DodgeAbilitySystem and DashAbilitySystem
 * }</pre>
 */
public final class AbilityEventDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbilityEventDispatcher.class);

    public static final AbilityEventDispatcher INSTANCE = new AbilityEventDispatcher();

    private final List<IAbilityEventListener> listeners = new CopyOnWriteArrayList<>();

    private AbilityEventDispatcher() {}

    /**
     * Add a listener to receive ability events.
     *
     * @param listener The listener to add
     * @throws IllegalArgumentException if listener is null
     */
    public void addListener(IAbilityEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        listeners.add(listener);
        LOGGER.debug("[AbilityEvents] Added listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * Remove a listener.
     *
     * @param listener The listener to remove
     * @return true if the listener was found and removed
     */
    public boolean removeListener(IAbilityEventListener listener) {
        boolean removed = listeners.remove(listener);
        if (removed) {
            LOGGER.debug("[AbilityEvents] Removed listener: {}", listener.getClass().getSimpleName());
        }
        return removed;
    }

    /**
     * Dispatch a dodge pre-event. Returns true if any listener cancelled the dodge.
     */
    public boolean fireDodgePre(AbilityEvent.DodgePre event) {
        for (IAbilityEventListener listener : listeners) {
            try {
                if (listener.onDodgePre(event)) {
                    LOGGER.debug("[AbilityEvents] Dodge cancelled by {}", listener.getClass().getSimpleName());
                    return true;
                }
            } catch (Exception e) {
                LOGGER.error("[AbilityEvents] Listener {} threw exception on DodgePre: {}",
                    listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        return false;
    }

    /**
     * Dispatch a dodge post-event (informational, not cancellable).
     */
    public void fireDodgePost(AbilityEvent.DodgePost event) {
        dispatch(event);
    }

    /**
     * Dispatch a dash pre-event. Returns true if any listener cancelled the dash.
     */
    public boolean fireDashPre(AbilityEvent.DashPre event) {
        for (IAbilityEventListener listener : listeners) {
            try {
                if (listener.onDashPre(event)) {
                    LOGGER.debug("[AbilityEvents] Dash cancelled by {}", listener.getClass().getSimpleName());
                    return true;
                }
            } catch (Exception e) {
                LOGGER.error("[AbilityEvents] Listener {} threw exception on DashPre: {}",
                    listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        return false;
    }

    /**
     * Dispatch a dash post-event (informational, not cancellable).
     */
    public void fireDashPost(AbilityEvent.DashPost event) {
        dispatch(event);
    }

    /**
     * Dispatch a perfect dodge event (informational, not cancellable).
     */
    public void firePerfectDodge(AbilityEvent.PerfectDodge event) {
        dispatch(event);
    }

    /**
     * Generic dispatch for non-cancellable events.
     */
    private void dispatch(AbilityEvent event) {
        for (IAbilityEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOGGER.error("[AbilityEvents] Listener {} threw exception handling {}: {}",
                    listener.getClass().getSimpleName(),
                    event.getClass().getSimpleName(),
                    e.getMessage(), e);
            }
        }
    }

    /** Get current listener count. */
    public int getListenerCount() {
        return listeners.size();
    }

    /** Clear all listeners. */
    public void clearListeners() {
        listeners.clear();
        LOGGER.debug("[AbilityEvents] All listeners cleared");
    }
}
