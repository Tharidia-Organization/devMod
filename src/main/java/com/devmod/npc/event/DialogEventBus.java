package com.devmod.npc.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import com.devmod.DevMod;

/**
 * Event bus for dialog events.
 * Allows other systems to subscribe to and react to dialog state changes.
 *
 * <p>Thread-safe implementation using CopyOnWriteArrayList.
 *
 * <p>Usage:
 * <pre>
 * // Register a listener
 * DialogEventBus.register(event -> {
 *     if (event instanceof DialogEvent.Opened opened) {
 *         // Handle dialog opened
 *     }
 * });
 *
 * // Or use the typed registration
 * DialogEventBus.onOpened(opened -> handleOpened(opened));
 * DialogEventBus.onOptionSelected(selected -> handleSelected(selected));
 * </pre>
 */
public final class DialogEventBus {

    private static final List<Consumer<DialogEvent>> listeners = new CopyOnWriteArrayList<>();

    private DialogEventBus() {}

    // ========================================================================
    // Registration
    // ========================================================================

    /**
     * Registers a listener for all dialog events.
     *
     * @param listener The event listener
     */
    public static void register(@Nonnull Consumer<DialogEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        DevMod.LOGGER.debug("Registered dialog event listener: {}", listener.getClass().getSimpleName());
    }

    /**
     * Unregisters a previously registered listener.
     *
     * @param listener The listener to remove
     * @return true if the listener was found and removed
     */
    public static boolean unregister(@Nonnull Consumer<DialogEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        boolean removed = listeners.remove(listener);
        if (removed) {
            DevMod.LOGGER.debug("Unregistered dialog event listener: {}", listener.getClass().getSimpleName());
        }
        return removed;
    }

    /**
     * Clears all registered listeners.
     * Typically called during server shutdown.
     */
    public static void clearAll() {
        int count = listeners.size();
        listeners.clear();
        DevMod.LOGGER.debug("Cleared {} dialog event listeners", count);
    }

    // ========================================================================
    // Typed Registration (Convenience Methods)
    // ========================================================================

    /**
     * Registers a listener specifically for Opened events.
     */
    public static void onOpened(@Nonnull Consumer<DialogEvent.Opened> listener) {
        Objects.requireNonNull(listener, "listener");
        register(event -> {
            if (event instanceof DialogEvent.Opened opened) {
                listener.accept(opened);
            }
        });
    }

    /**
     * Registers a listener specifically for OptionSelected events.
     */
    public static void onOptionSelected(@Nonnull Consumer<DialogEvent.OptionSelected> listener) {
        Objects.requireNonNull(listener, "listener");
        register(event -> {
            if (event instanceof DialogEvent.OptionSelected selected) {
                listener.accept(selected);
            }
        });
    }

    /**
     * Registers a listener specifically for ActionExecuted events.
     */
    public static void onActionExecuted(@Nonnull Consumer<DialogEvent.ActionExecuted> listener) {
        Objects.requireNonNull(listener, "listener");
        register(event -> {
            if (event instanceof DialogEvent.ActionExecuted executed) {
                listener.accept(executed);
            }
        });
    }

    /**
     * Registers a listener specifically for Closed events.
     */
    public static void onClosed(@Nonnull Consumer<DialogEvent.Closed> listener) {
        Objects.requireNonNull(listener, "listener");
        register(event -> {
            if (event instanceof DialogEvent.Closed closed) {
                listener.accept(closed);
            }
        });
    }

    // ========================================================================
    // Event Posting
    // ========================================================================

    /**
     * Posts an event to all registered listeners.
     * Exceptions in listeners are caught and logged to prevent breaking the event chain.
     *
     * @param event The event to post
     */
    public static void post(@Nonnull DialogEvent event) {
        Objects.requireNonNull(event, "event");

        if (listeners.isEmpty()) {
            return;
        }

        DevMod.LOGGER.trace("Posting dialog event: {} for player {} with NPC {}",
            event.getClass().getSimpleName(), event.playerId(), event.npcId());

        for (Consumer<DialogEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                DevMod.LOGGER.error("Error in dialog event listener: {}", e.getMessage(), e);
            }
        }
    }

    // ========================================================================
    // Info
    // ========================================================================

    /**
     * Returns the number of registered listeners.
     */
    public static int getListenerCount() {
        return listeners.size();
    }
}
