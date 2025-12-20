package com.devmod.arena.registry;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple registry for custom hazard builders.
 */
public final class CustomHazardRegistry {

    private static final CustomHazardRegistry INSTANCE = new CustomHazardRegistry();
    private final Set<String> registered = Collections.synchronizedSet(new HashSet<>());

    private CustomHazardRegistry() {}

    public static CustomHazardRegistry getInstance() {
        return INSTANCE;
    }

    public void register(String builderId) {
        if (builderId != null && !builderId.isBlank()) {
            registered.add(builderId);
        }
    }

    public void unregister(String builderId) {
        if (builderId != null) {
            registered.remove(builderId);
        }
    }

    /**
     * Replaces current registrations with provided set (used on config reload).
     */
    public void reset(Set<String> newBuilders) {
        registered.clear();
        if (newBuilders != null) {
            newBuilders.stream()
                .filter(id -> id != null && !id.isBlank())
                .forEach(registered::add);
        }
    }

    public Set<String> all() {
        return Set.copyOf(registered);
    }
}
