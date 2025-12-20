package com.devmod.arena.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for arena system feature flags with runtime toggle support.
 *
 * <p>Provides centralized management of feature flags with:
 * <ul>
 *   <li>Static registration of known flags</li>
 *   <li>Runtime override capability</li>
 *   <li>Dependency chain validation (e.g., routing requires arenaTemplate)</li>
 *   <li>Telemetry on flag state changes</li>
 * </ul>
 *
 * <p>Standard arena flags:
 * <ul>
 *   <li>{@code arena.template.enabled} - Master toggle for arena template system</li>
 *   <li>{@code arena.instance.only} - Restrict to instance dimensions</li>
 *   <li>{@code arena.routing.enabled} - Policy-based template routing</li>
 *   <li>{@code arena.gamification.enabled} - Gamification features</li>
 * </ul>
 *
 * <p>Flag dependencies (validated on enable):
 * <ul>
 *   <li>routing.enabled → requires template.enabled</li>
 *   <li>gamification.enabled → requires template.enabled</li>
 * </ul>
 *
 * @see FeatureFlag
 * @see ArenaTemplateConfig
 */
public class FeatureFlagRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureFlagRegistry.class);

    // Standard flag IDs
    public static final String ARENA_TEMPLATE_ENABLED = "arena.template.enabled";
    public static final String ARENA_INSTANCE_ONLY = "arena.instance.only";
    public static final String ARENA_ROUTING_ENABLED = "arena.routing.enabled";
    public static final String ARENA_GAMIFICATION_ENABLED = "arena.gamification.enabled";

    private final Map<String, MutableFeatureFlag> flags;
    private final Telemetry telemetry;

    /**
     * Creates a registry with optional telemetry.
     */
    public FeatureFlagRegistry(Telemetry telemetry) {
        this.telemetry = telemetry;
        this.flags = createDefaultFlags();
    }

    /**
     * Creates a registry without telemetry.
     */
    public FeatureFlagRegistry() {
        this(null);
    }

    /**
     * Creates a registry initialized from ArenaTemplateConfig.
     */
    public static FeatureFlagRegistry fromConfig(ArenaTemplateConfig config, Telemetry telemetry) {
        FeatureFlagRegistry registry = new FeatureFlagRegistry(telemetry);
        registry.setEnabled(ARENA_TEMPLATE_ENABLED, config.arenaTemplateEnabled());
        registry.setEnabled(ARENA_INSTANCE_ONLY, config.instanceOnly());
        registry.setEnabled(ARENA_ROUTING_ENABLED, config.routingEnabled());
        registry.setEnabled(ARENA_GAMIFICATION_ENABLED, config.gamificationEnabled());
        return registry;
    }

    private Map<String, MutableFeatureFlag> createDefaultFlags() {
        Map<String, MutableFeatureFlag> map = new ConcurrentHashMap<>();
        map.put(ARENA_TEMPLATE_ENABLED, new MutableFeatureFlag(
            ARENA_TEMPLATE_ENABLED,
            true,
            "Master toggle for the arena template system"
        ));
        map.put(ARENA_INSTANCE_ONLY, new MutableFeatureFlag(
            ARENA_INSTANCE_ONLY,
            true,
            "Restrict arena builds to instance dimensions only"
        ));
        map.put(ARENA_ROUTING_ENABLED, new MutableFeatureFlag(
            ARENA_ROUTING_ENABLED,
            true,
            "Enable policy-based template routing"
        ));
        map.put(ARENA_GAMIFICATION_ENABLED, new MutableFeatureFlag(
            ARENA_GAMIFICATION_ENABLED,
            false,
            "Enable gamification features (achievements, leaderboards)"
        ));
        return map;
    }

    /**
     * Registers a custom feature flag.
     */
    public void register(MutableFeatureFlag flag) {
        flags.put(flag.id(), flag);
        LOGGER.debug("Registered feature flag: {} (default={})", flag.id(), flag.defaultValue());
    }

    /**
     * Gets a feature flag by ID.
     */
    public Optional<FeatureFlag> get(String id) {
        return Optional.ofNullable(flags.get(id));
    }

    /**
     * Checks if a feature is enabled.
     */
    public boolean isEnabled(String id) {
        MutableFeatureFlag flag = flags.get(id);
        return flag != null && flag.isEnabled();
    }

    /**
     * Sets the enabled state of a flag with dependency validation.
     *
     * @return true if the flag was set, false if dependency validation failed
     */
    public boolean setEnabled(String id, boolean enabled) {
        MutableFeatureFlag flag = flags.get(id);
        if (flag == null) {
            LOGGER.warn("Attempted to set unknown flag: {}", id);
            return false;
        }

        // Validate dependencies when enabling
        if (enabled && !validateDependencies(id)) {
            LOGGER.warn("Cannot enable '{}': dependency validation failed", id);
            return false;
        }

        boolean oldValue = flag.isEnabled();
        flag.setEnabled(enabled);

        if (oldValue != enabled) {
            LOGGER.info("Feature flag '{}' changed: {} -> {}", id, oldValue, enabled);
            if (telemetry != null) {
                telemetry.onFlagChanged(id, oldValue, enabled);
            }

            // Cascade disable dependents if disabling a parent
            if (!enabled) {
                cascadeDisable(id);
            }
        }

        return true;
    }

    /**
     * Validates that dependencies are satisfied before enabling a flag.
     */
    private boolean validateDependencies(String id) {
        return switch (id) {
            case ARENA_ROUTING_ENABLED, ARENA_GAMIFICATION_ENABLED -> isEnabled(ARENA_TEMPLATE_ENABLED);
            case ARENA_TEMPLATE_ENABLED -> isEnabled(ARENA_INSTANCE_ONLY);
            default -> true;
        };
    }

    /**
     * Cascades disable to dependent flags when a parent is disabled.
     */
    private void cascadeDisable(String disabledId) {
        if (ARENA_TEMPLATE_ENABLED.equals(disabledId)) {
            // Disable dependents
            if (isEnabled(ARENA_ROUTING_ENABLED)) {
                setEnabled(ARENA_ROUTING_ENABLED, false);
                LOGGER.info("Cascade disabled '{}' due to '{}' being disabled",
                    ARENA_ROUTING_ENABLED, disabledId);
            }
            if (isEnabled(ARENA_GAMIFICATION_ENABLED)) {
                setEnabled(ARENA_GAMIFICATION_ENABLED, false);
                LOGGER.info("Cascade disabled '{}' due to '{}' being disabled",
                    ARENA_GAMIFICATION_ENABLED, disabledId);
            }
        }
        if (ARENA_INSTANCE_ONLY.equals(disabledId)) {
            if (isEnabled(ARENA_TEMPLATE_ENABLED)) {
                setEnabled(ARENA_TEMPLATE_ENABLED, false);
                LOGGER.info("Cascade disabled '{}' due to '{}' being disabled",
                    ARENA_TEMPLATE_ENABLED, disabledId);
            }
        }
    }

    /**
     * Returns all registered flags.
     */
    public Collection<FeatureFlag> all() {
        return flags.values().stream().map(f -> (FeatureFlag) f).toList();
    }

    /**
     * Returns a snapshot of current flag states.
     */
    public Map<String, Boolean> snapshot() {
        Map<String, Boolean> result = new ConcurrentHashMap<>();
        for (var entry : flags.entrySet()) {
            result.put(entry.getKey(), entry.getValue().isEnabled());
        }
        return result;
    }

    /**
     * Telemetry interface for flag state changes.
     */
    public interface Telemetry {
        void onFlagChanged(String flagId, boolean oldValue, boolean newValue);
    }

    /**
     * Applies a config snapshot to refresh standard flags (instanceOnly, template, routing, gamification),
     * honoring dependency validation and cascade.
     */
    public void applyConfig(ArenaTemplateConfig config) {
        setEnabled(ARENA_INSTANCE_ONLY, config.instanceOnly());
        setEnabled(ARENA_TEMPLATE_ENABLED, config.arenaTemplateEnabled());
        setEnabled(ARENA_ROUTING_ENABLED, config.routingEnabled());
        setEnabled(ARENA_GAMIFICATION_ENABLED, config.gamificationEnabled());
    }

    /**
     * Mutable feature flag implementation.
     */
    public static class MutableFeatureFlag implements FeatureFlag {
        private final String id;
        private final boolean defaultValue;
        private final String description;
        private volatile boolean enabled;

        public MutableFeatureFlag(String id, boolean defaultValue, String description) {
            this.id = id;
            this.defaultValue = defaultValue;
            this.description = description;
            this.enabled = defaultValue;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public boolean defaultValue() {
            return defaultValue;
        }

        @Override
        public String description() {
            return description;
        }

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
