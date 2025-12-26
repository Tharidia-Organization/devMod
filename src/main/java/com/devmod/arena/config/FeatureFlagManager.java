package com.devmod.arena.config;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatureFlagManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureFlagManager.class);

    private final FeatureFlagRegistry registry;
    private final Set<FeatureFlagListener> listeners = new CopyOnWriteArraySet<>();

    public FeatureFlagManager(FeatureFlagRegistry registry) {
        this.registry = registry;
    }

    /**
     * Apply a new config snapshot to the registry and notify listeners.
     */
    public void applyConfig(ArenaTemplateConfig config) {
        Map<String, Boolean> before = registry.snapshot();
        registry.applyConfig(config);
        Map<String, Boolean> after = registry.snapshot();

        listeners.forEach(l -> {
            try {
                l.onFlagsUpdated(before, after);
            } catch (Exception e) {
                LOGGER.warn("FeatureFlagListener threw exception: {}", e.getMessage());
            }
        });
    }

    public void addListener(FeatureFlagListener listener) {
        listeners.add(listener);
    }

    public void removeListener(FeatureFlagListener listener) {
        listeners.remove(listener);
    }

    public FeatureFlagRegistry registry() {
        return registry;
    }

    public interface FeatureFlagListener {
        void onFlagsUpdated(Map<String, Boolean> previous, Map<String, Boolean> current);
    }
}
