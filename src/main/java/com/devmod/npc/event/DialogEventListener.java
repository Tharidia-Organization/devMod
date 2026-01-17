package com.devmod.npc.event;

import javax.annotation.Nonnull;

/**
 * Functional interface for dialog event listeners.
 * Provides a type-safe way to listen to dialog events.
 *
 * <p>Usage:
 * <pre>
 * DialogEventListener listener = event -> {
 *     switch (event) {
 *         case DialogEvent.Opened o -> handleOpened(o);
 *         case DialogEvent.Closed c -> handleClosed(c);
 *         default -> {}
 *     }
 * };
 * DialogEventBus.register(listener::onEvent);
 * </pre>
 */
@FunctionalInterface
public interface DialogEventListener {

    /**
     * Called when a dialog event occurs.
     *
     * @param event The dialog event
     */
    void onEvent(@Nonnull DialogEvent event);
}
