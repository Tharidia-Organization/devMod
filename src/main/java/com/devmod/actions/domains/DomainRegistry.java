package com.devmod.actions.domains;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.catalog.ActionSpec;

/**
 * Manages all domain registrars. Validates that no action ID conflicts exist
 * between domains and ensures domains are initialized in dependency order.
 */
public final class DomainRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DomainRegistry.class);

    private final Map<String, DomainRegistrar> registrars = new LinkedHashMap<>();
    private final HandlerRegistry handlerRegistry = new HandlerRegistry();
    private final Map<String, ActionSpec> catalog = new HashMap<>();
    private boolean initialized = false;

    /**
     * Adds a domain registrar. Must be called before {@link #initialize()}.
     *
     * @throws IllegalStateException if already initialized or domain name conflicts
     */
    public void addRegistrar(DomainRegistrar registrar) {
        Objects.requireNonNull(registrar, "registrar");
        if (initialized) {
            throw new IllegalStateException("Cannot add registrars after initialization");
        }
        String name = registrar.domainName();
        if (registrars.containsKey(name)) {
            throw new IllegalStateException("Duplicate domain name: " + name);
        }
        registrars.put(name, registrar);
    }

    /**
     * Initializes all registrars in dependency order, validates no ID conflicts,
     * and registers all handlers.
     *
     * @throws IllegalStateException if dependency cycle or ID conflict detected
     */
    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("Already initialized");
        }

        List<DomainRegistrar> ordered = topologicalSort();

        for (DomainRegistrar registrar : ordered) {
            LOGGER.info("Initializing domain: {}", registrar.domainName());

            List<ActionSpec> specs = registrar.getActionSpecs();
            for (ActionSpec spec : specs) {
                ActionSpec existing = catalog.putIfAbsent(spec.id(), spec);
                if (existing != null) {
                    throw new IllegalStateException(
                        "Action ID conflict: '" + spec.id() + "' registered by both '"
                            + registrar.domainName() + "' and another domain");
                }
            }

            registrar.registerHandlers(handlerRegistry);
            LOGGER.info("Domain '{}' initialized: {} actions", registrar.domainName(), specs.size());
        }

        initialized = true;
        LOGGER.info("DomainRegistry initialized: {} domains, {} actions",
            registrars.size(), catalog.size());
    }

    /**
     * Returns the action catalog (action ID to ActionSpec).
     */
    public Map<String, ActionSpec> getCatalog() {
        return Map.copyOf(catalog);
    }

    /**
     * Returns the handler registry.
     */
    public HandlerRegistry getHandlerRegistry() {
        return handlerRegistry;
    }

    /**
     * Returns the number of registered domains.
     */
    public int domainCount() {
        return registrars.size();
    }

    /**
     * Returns the total number of action specs across all domains.
     */
    public int actionCount() {
        return catalog.size();
    }

    /**
     * Topological sort of registrars by dependency order.
     */
    private List<DomainRegistrar> topologicalSort() {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        List<DomainRegistrar> result = new ArrayList<>();

        for (String name : registrars.keySet()) {
            if (!visited.contains(name)) {
                visit(name, visited, visiting, result);
            }
        }

        return result;
    }

    private void visit(String name, Set<String> visited, Set<String> visiting,
                       List<DomainRegistrar> result) {
        if (visiting.contains(name)) {
            throw new IllegalStateException("Dependency cycle detected at domain: " + name);
        }
        if (visited.contains(name)) {
            return;
        }

        visiting.add(name);

        DomainRegistrar registrar = registrars.get(name);
        if (registrar == null) {
            throw new IllegalStateException("Unknown domain dependency: " + name);
        }

        for (String dep : registrar.getDependencies()) {
            visit(dep, visited, visiting, result);
        }

        visiting.remove(name);
        visited.add(name);
        result.add(registrar);
    }
}
