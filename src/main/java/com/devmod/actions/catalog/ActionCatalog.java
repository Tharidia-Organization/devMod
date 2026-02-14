package com.devmod.actions.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;

/**
 * Central catalog that stores ActionSpec instances. Thread-safe via ConcurrentHashMap.
 * This is the single source of truth for all action metadata in the V2 system.
 */
public final class ActionCatalog {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActionCatalog.class);

    private final Map<String, ActionSpec> specs = new ConcurrentHashMap<>();

    /**
     * Registers an ActionSpec. Throws if an action with the same ID already exists.
     *
     * @param spec the action spec to register
     * @throws IllegalStateException if a spec with the same ID is already registered
     */
    public void register(ActionSpec spec) {
        Objects.requireNonNull(spec, "spec");
        ActionSpec existing = specs.putIfAbsent(spec.id(), spec);
        if (existing != null) {
            throw new IllegalStateException(
                "[ActionCatalog] Duplicate action id: " + spec.id());
        }
        LOGGER.debug("Registered action spec: {}", spec.id());
    }

    /**
     * Returns the ActionSpec for the given ID, or null if not found.
     */
    @Nullable
    public ActionSpec get(String id) {
        return specs.get(id);
    }

    /**
     * Returns all registered ActionSpecs.
     */
    public Collection<ActionSpec> getAll() {
        return List.copyOf(specs.values());
    }

    /**
     * Returns all ActionSpecs in the given category.
     */
    public List<ActionSpec> getByCategory(ActionCategory category) {
        Objects.requireNonNull(category, "category");
        List<ActionSpec> result = new ArrayList<>();
        for (ActionSpec spec : specs.values()) {
            if (spec.category() == category) {
                result.add(spec);
            }
        }
        return result;
    }

    /**
     * Returns all ActionSpecs with the given channel.
     */
    public List<ActionSpec> getByChannel(ActionChannel channel) {
        Objects.requireNonNull(channel, "channel");
        List<ActionSpec> result = new ArrayList<>();
        for (ActionSpec spec : specs.values()) {
            if (spec.channel() == channel) {
                result.add(spec);
            }
        }
        return result;
    }

    /**
     * Returns true if the catalog contains a spec with the given ID.
     */
    public boolean contains(String id) {
        return specs.containsKey(id);
    }

    /**
     * Returns the number of registered action specs.
     */
    public int size() {
        return specs.size();
    }
}
