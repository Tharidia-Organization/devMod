package com.devmod.actions.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.config.FeatureFlagRegistry;
import com.devmod.arena.config.FeatureFlagRegistry.MutableFeatureFlag;

/**
 * Feature flags for the Actions V2 engine. Registers engine-specific flags
 * in the global {@link FeatureFlagRegistry} and provides static accessors
 * for runtime toggling.
 *
 * <p>Flag hierarchy:
 * <pre>
 * V2_ENGINE_ENABLED (master toggle)
 *   +-- V2_SHADOW_MODE (run both V1 and V2, compare results)
 *   +-- V2_ACTIVE_MODE (V2 is the primary path, V1 is fallback)
 * </pre>
 *
 * <p>Modes are mutually exclusive in practice:
 * <ul>
 *   <li>Engine disabled: all invocations go through V1</li>
 *   <li>Shadow mode: V1 is primary, V2 runs read-only alongside for comparison</li>
 *   <li>Active mode: V2 is primary, V1 is fallback on V2 failure</li>
 *   <li>Both disabled (engine enabled): V2 is primary with no fallback</li>
 * </ul>
 *
 * <p>Call {@link #register()} once during mod initialization to register all flags.
 */
public final class EngineFeatureFlags {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngineFeatureFlags.class);

    // =========================================================================
    // Flag IDs
    // =========================================================================

    /** Master toggle for the V2 action engine. When disabled, all actions go through V1. */
    public static final String V2_ENGINE_ENABLED = "actions.v2.engine.enabled";

    /** Shadow mode: run both V1 (primary) and V2 (read-only), compare results. */
    public static final String V2_SHADOW_MODE = "actions.v2.shadow.enabled";

    /** Active mode: V2 is primary, V1 is fallback on V2 errors. */
    public static final String V2_ACTIVE_MODE = "actions.v2.active.enabled";

    /** Enable detailed shadow mode comparison logging. */
    public static final String V2_SHADOW_VERBOSE = "actions.v2.shadow.verbose";

    /** Enable V2 telemetry collection for pipeline step durations. */
    public static final String V2_TELEMETRY_ENABLED = "actions.v2.telemetry.enabled";

    private static volatile boolean registered;

    private EngineFeatureFlags() {}

    /**
     * Registers all V2 engine feature flags in the global registry.
     * Safe to call multiple times; only the first call has effect.
     */
    public static void register() {
        register(FeatureFlagRegistry.INSTANCE);
    }

    /**
     * Registers all V2 engine feature flags in the given registry.
     * Safe to call multiple times; only the first call has effect.
     *
     * @param registry the feature flag registry to register into
     */
    public static void register(FeatureFlagRegistry registry) {
        if (registered) {
            return;
        }
        synchronized (EngineFeatureFlags.class) {
            if (registered) {
                return;
            }

            registry.register(new MutableFeatureFlag(
                V2_ENGINE_ENABLED,
                false,
                "Master toggle for V2 action engine"
            ));
            registry.register(new MutableFeatureFlag(
                V2_SHADOW_MODE,
                false,
                "Shadow mode: run V1 + V2 in parallel, compare results"
            ));
            registry.register(new MutableFeatureFlag(
                V2_ACTIVE_MODE,
                false,
                "Active mode: V2 primary, V1 fallback"
            ));
            registry.register(new MutableFeatureFlag(
                V2_SHADOW_VERBOSE,
                false,
                "Verbose logging for shadow mode comparisons"
            ));
            registry.register(new MutableFeatureFlag(
                V2_TELEMETRY_ENABLED,
                true,
                "V2 pipeline step duration telemetry"
            ));

            registered = true;
            LOGGER.info("[EngineFeatureFlags] Registered V2 engine feature flags");
        }
    }

    // =========================================================================
    // Static accessors
    // =========================================================================

    /**
     * Returns true if the V2 engine is enabled (master toggle).
     */
    public static boolean isEngineEnabled() {
        return FeatureFlagRegistry.INSTANCE.isEnabled(V2_ENGINE_ENABLED);
    }

    /**
     * Returns true if shadow mode is active (V1 primary, V2 read-only comparison).
     * Shadow mode requires the engine to be enabled.
     */
    public static boolean isShadowMode() {
        return isEngineEnabled() && FeatureFlagRegistry.INSTANCE.isEnabled(V2_SHADOW_MODE);
    }

    /**
     * Returns true if active mode is enabled (V2 primary, V1 fallback).
     * Active mode requires the engine to be enabled.
     */
    public static boolean isActiveMode() {
        return isEngineEnabled() && FeatureFlagRegistry.INSTANCE.isEnabled(V2_ACTIVE_MODE);
    }

    /**
     * Returns true if verbose shadow comparison logging is enabled.
     */
    public static boolean isShadowVerbose() {
        return FeatureFlagRegistry.INSTANCE.isEnabled(V2_SHADOW_VERBOSE);
    }

    /**
     * Returns true if V2 pipeline telemetry is enabled.
     */
    public static boolean isTelemetryEnabled() {
        return FeatureFlagRegistry.INSTANCE.isEnabled(V2_TELEMETRY_ENABLED);
    }

    /**
     * Determines the current routing mode based on flag state.
     *
     * @return the active routing mode
     */
    public static RoutingMode currentMode() {
        if (!isEngineEnabled()) {
            return RoutingMode.V1_ONLY;
        }
        if (isShadowMode()) {
            return RoutingMode.SHADOW;
        }
        if (isActiveMode()) {
            return RoutingMode.V2_ACTIVE;
        }
        // Engine enabled but neither shadow nor active -> V2 only (no fallback)
        return RoutingMode.V2_ONLY;
    }

    /**
     * Routing modes for the compatibility bridge.
     */
    public enum RoutingMode {
        /** All invocations go through V1. V2 engine is disabled. */
        V1_ONLY,
        /** V1 is primary. V2 runs read-only alongside for comparison. */
        SHADOW,
        /** V2 is primary. Falls back to V1 on V2 errors. */
        V2_ACTIVE,
        /** V2 is the sole path. No V1 fallback. */
        V2_ONLY
    }

    /**
     * Resets the registration state. Only for testing.
     */
    public static void resetForTesting() {
        registered = false;
    }
}
