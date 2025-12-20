package com.devmod.arena.config;

/**
 * Feature flag interface for dynamic runtime toggle support.
 *
 * <p>Provides a unified abstraction for feature toggles in the arena system.
 * Implementations can be backed by config, environment variables, or remote
 * feature flag services.
 *
 * <p>Usage:
 * <pre>{@code
 * FeatureFlag arenaEnabled = registry.get("arena.template.enabled");
 * if (arenaEnabled.isEnabled()) {
 *     // feature logic
 * }
 * }</pre>
 *
 * @see FeatureFlagRegistry
 */
public interface FeatureFlag {

    /**
     * Returns the unique identifier for this flag.
     */
    String id();

    /**
     * Returns whether this feature is currently enabled.
     */
    boolean isEnabled();

    /**
     * Returns the default value for this flag.
     */
    boolean defaultValue();

    /**
     * Returns a human-readable description of this flag.
     */
    String description();
}
