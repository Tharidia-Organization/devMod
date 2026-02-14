package com.devmod.actions.bindings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central registry for all action bindings. Maps action IDs to their bindings
 * and provides lookup by binding type. Thread-safe for concurrent access.
 */
public final class BindingRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(BindingRegistry.class);

    private final Map<String, List<ActionBinding>> bindingsByAction = new ConcurrentHashMap<>();
    private final List<ActionBinding> allBindings = new CopyOnWriteArrayList<>();

    /**
     * Registers a binding. An action can have multiple bindings (e.g., radial + keybind).
     */
    public void register(ActionBinding binding) {
        Objects.requireNonNull(binding, "binding");
        allBindings.add(binding);
        bindingsByAction.computeIfAbsent(binding.actionId(), k -> new CopyOnWriteArrayList<>()).add(binding);
        LOGGER.debug("Registered binding: {}", binding.describe());
    }

    /**
     * Returns all bindings for the given action ID, or an empty list.
     */
    public List<ActionBinding> getBindings(String actionId) {
        return List.copyOf(bindingsByAction.getOrDefault(actionId, List.of()));
    }

    /**
     * Returns all bindings of a specific type.
     */
    public <T extends ActionBinding> List<T> getBindingsOfType(Class<T> type) {
        List<T> result = new ArrayList<>();
        for (ActionBinding binding : allBindings) {
            if (type.isInstance(binding)) {
                result.add(type.cast(binding));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Returns the total number of registered bindings.
     */
    public int size() {
        return allBindings.size();
    }
}
