package com.devmod.actions.domains;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.ActionContext;

/**
 * Maps action IDs to their handler functions. Used by the execution engine
 * to look up the handler for a resolved ActionSpec.
 *
 * <p>Handler functions receive an {@link ActionContext} and perform the
 * action's side effects (open screen, execute command, toggle state, etc.).
 */
public final class HandlerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(HandlerRegistry.class);

    private final Map<String, Consumer<ActionContext>> handlers = new ConcurrentHashMap<>();

    /**
     * Registers a handler for the given action ID.
     *
     * @param actionId the action ID
     * @param handler  the handler function
     * @throws IllegalStateException if a handler is already registered for this ID
     */
    public void register(String actionId, Consumer<ActionContext> handler) {
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(handler, "handler");

        Consumer<ActionContext> existing = handlers.putIfAbsent(actionId, handler);
        if (existing != null) {
            throw new IllegalStateException(
                "Duplicate handler registration for action: " + actionId);
        }
        LOGGER.debug("Registered handler for action: {}", actionId);
    }

    /**
     * Returns the handler for the given action ID, or null if not registered.
     */
    @Nullable
    public Consumer<ActionContext> getHandler(String actionId) {
        return handlers.get(actionId);
    }

    /**
     * Returns true if a handler is registered for the given action ID.
     */
    public boolean hasHandler(String actionId) {
        return handlers.containsKey(actionId);
    }

    /**
     * Returns the number of registered handlers.
     */
    public int size() {
        return handlers.size();
    }
}
