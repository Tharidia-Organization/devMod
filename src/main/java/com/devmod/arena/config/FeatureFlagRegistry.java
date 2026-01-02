package com.devmod.arena.config;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatureFlagRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureFlagRegistry.class);

    /**
     * Global singleton instance for static access.
     * Initialized lazily on first access.
     */
    public static final FeatureFlagRegistry INSTANCE = new FeatureFlagRegistry();

    // ============================================================================
    // ARENA FLAGS
    // ============================================================================
    public static final String ARENA_TEMPLATE_ENABLED = "arena.template.enabled";
    public static final String ARENA_INSTANCE_ONLY = "arena.instance.only";
    public static final String ARENA_ROUTING_ENABLED = "arena.routing.enabled";
    public static final String ARENA_GAMIFICATION_ENABLED = "arena.gamification.enabled";

    // ============================================================================
    // GLOBAL SUBSYSTEM FLAGS
    // ============================================================================
    /** Master toggle for the mailbox/communication system */
    public static final String MAILBOX_ENABLED = "global.mailbox.enabled";
    /** Master toggle for telemetry collection and dashboard */
    public static final String TELEMETRY_ENABLED = "global.telemetry.enabled";
    /** Master toggle for party system */
    public static final String PARTY_SYSTEM_ENABLED = "global.party.enabled";
    /** Master toggle for notification system */
    public static final String NOTIFICATION_ENABLED = "global.notification.enabled";
    /** Master toggle for endurance quest system */
    public static final String ENDURANCE_ENABLED = "global.endurance.enabled";
    /** Toggle for season pass features (depends on endurance) */
    public static final String SEASON_PASS_ENABLED = "global.seasonpass.enabled";
    /** Toggle for debug overlays and visualization */
    public static final String DEBUG_OVERLAY_ENABLED = "global.debug.overlay.enabled";
    /** Toggle for item editor system */
    public static final String ITEM_EDITOR_ENABLED = "global.editor.enabled";
    /** Toggle for mob config system */
    public static final String MOB_CONFIG_ENABLED = "global.mobconfig.enabled";
    /** Toggle for compat layer auto-detection */
    public static final String COMPAT_AUTODETECT_ENABLED = "global.compat.autodetect.enabled";

    private final Map<String, MutableFeatureFlag> flags;
    @Nullable
    private final Telemetry telemetry;

    /**
     * Creates a registry with optional telemetry.
     */
    public FeatureFlagRegistry(@Nullable Telemetry telemetry) {
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
    public static FeatureFlagRegistry fromConfig(ArenaTemplateConfig config, @Nullable Telemetry telemetry) {
        FeatureFlagRegistry registry = new FeatureFlagRegistry(telemetry);
        registry.setEnabled(ARENA_TEMPLATE_ENABLED, config.arenaTemplateEnabled());
        registry.setEnabled(ARENA_INSTANCE_ONLY, config.instanceOnly());
        registry.setEnabled(ARENA_ROUTING_ENABLED, config.routingEnabled());
        registry.setEnabled(ARENA_GAMIFICATION_ENABLED, config.gamificationEnabled());
        return registry;
    }

    private Map<String, MutableFeatureFlag> createDefaultFlags() {
        Map<String, MutableFeatureFlag> map = new ConcurrentHashMap<>();

        // Arena flags
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

        // Global subsystem flags
        map.put(MAILBOX_ENABLED, new MutableFeatureFlag(
            MAILBOX_ENABLED,
            true,
            "Master toggle for mailbox and communication system"
        ));
        map.put(TELEMETRY_ENABLED, new MutableFeatureFlag(
            TELEMETRY_ENABLED,
            true,
            "Master toggle for telemetry collection and dashboard"
        ));
        map.put(PARTY_SYSTEM_ENABLED, new MutableFeatureFlag(
            PARTY_SYSTEM_ENABLED,
            true,
            "Master toggle for party/group system"
        ));
        map.put(NOTIFICATION_ENABLED, new MutableFeatureFlag(
            NOTIFICATION_ENABLED,
            true,
            "Master toggle for notification system"
        ));
        map.put(ENDURANCE_ENABLED, new MutableFeatureFlag(
            ENDURANCE_ENABLED,
            true,
            "Master toggle for endurance quest system"
        ));
        map.put(SEASON_PASS_ENABLED, new MutableFeatureFlag(
            SEASON_PASS_ENABLED,
            false,
            "Toggle for season pass features (requires endurance)"
        ));
        map.put(DEBUG_OVERLAY_ENABLED, new MutableFeatureFlag(
            DEBUG_OVERLAY_ENABLED,
            false,
            "Toggle for debug overlays and visualization tools"
        ));
        map.put(ITEM_EDITOR_ENABLED, new MutableFeatureFlag(
            ITEM_EDITOR_ENABLED,
            true,
            "Toggle for item editor system"
        ));
        map.put(MOB_CONFIG_ENABLED, new MutableFeatureFlag(
            MOB_CONFIG_ENABLED,
            true,
            "Toggle for mob configuration system"
        ));
        map.put(COMPAT_AUTODETECT_ENABLED, new MutableFeatureFlag(
            COMPAT_AUTODETECT_ENABLED,
            true,
            "Toggle for automatic mod compatibility detection"
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
     *
     * <p>Dependency graph:
     * <pre>
     * ARENA_INSTANCE_ONLY
     *   └─ ARENA_TEMPLATE_ENABLED
     *        ├─ ARENA_ROUTING_ENABLED
     *        └─ ARENA_GAMIFICATION_ENABLED
     *
     * ENDURANCE_ENABLED
     *   ├─ SEASON_PASS_ENABLED
     *   └─ ARENA_GAMIFICATION_ENABLED (also depends on ARENA_TEMPLATE)
     *
     * PARTY_SYSTEM_ENABLED
     *   └─ (standalone, no dependencies)
     *
     * MAILBOX_ENABLED
     *   └─ (standalone, no dependencies)
     *
     * NOTIFICATION_ENABLED
     *   └─ (standalone, no dependencies)
     * </pre>
     */
    private boolean validateDependencies(String id) {
        return switch (id) {
            // Arena dependencies
            case ARENA_ROUTING_ENABLED -> isEnabled(ARENA_TEMPLATE_ENABLED);
            case ARENA_GAMIFICATION_ENABLED -> isEnabled(ARENA_TEMPLATE_ENABLED) && isEnabled(ENDURANCE_ENABLED);
            case ARENA_TEMPLATE_ENABLED -> isEnabled(ARENA_INSTANCE_ONLY);

            // Global dependencies
            case SEASON_PASS_ENABLED -> isEnabled(ENDURANCE_ENABLED);

            // No dependencies
            default -> true;
        };
    }

    /**
     * Cascades disable to dependent flags when a parent is disabled.
     */
    private void cascadeDisable(String disabledId) {
        switch (disabledId) {
            case ARENA_TEMPLATE_ENABLED -> {
                cascadeDisableFlag(ARENA_ROUTING_ENABLED, disabledId);
                cascadeDisableFlag(ARENA_GAMIFICATION_ENABLED, disabledId);
            }
            case ARENA_INSTANCE_ONLY -> {
                cascadeDisableFlag(ARENA_TEMPLATE_ENABLED, disabledId);
            }
            case ENDURANCE_ENABLED -> {
                cascadeDisableFlag(SEASON_PASS_ENABLED, disabledId);
                cascadeDisableFlag(ARENA_GAMIFICATION_ENABLED, disabledId);
            }
            default -> {
                // No cascade needed
            }
        }
    }

    /**
     * Helper to cascade disable a single flag.
     */
    private void cascadeDisableFlag(String flagId, String parentId) {
        if (isEnabled(flagId)) {
            setEnabled(flagId, false);
            LOGGER.info("Cascade disabled '{}' due to '{}' being disabled", flagId, parentId);
        }
    }

    /**
     * Get the dependencies for a flag.
     *
     * @param flagId The flag to check
     * @return List of flag IDs this flag depends on (empty if no dependencies)
     */
    public java.util.List<String> getDependencies(String flagId) {
        return switch (flagId) {
            case ARENA_ROUTING_ENABLED -> java.util.List.of(ARENA_TEMPLATE_ENABLED);
            case ARENA_GAMIFICATION_ENABLED -> java.util.List.of(ARENA_TEMPLATE_ENABLED, ENDURANCE_ENABLED);
            case ARENA_TEMPLATE_ENABLED -> java.util.List.of(ARENA_INSTANCE_ONLY);
            case SEASON_PASS_ENABLED -> java.util.List.of(ENDURANCE_ENABLED);
            default -> java.util.List.of();
        };
    }

    /**
     * Get flags that depend on a given flag.
     *
     * @param flagId The parent flag
     * @return List of flag IDs that depend on this flag
     */
    public java.util.List<String> getDependents(String flagId) {
        return switch (flagId) {
            case ARENA_INSTANCE_ONLY -> java.util.List.of(ARENA_TEMPLATE_ENABLED);
            case ARENA_TEMPLATE_ENABLED -> java.util.List.of(ARENA_ROUTING_ENABLED, ARENA_GAMIFICATION_ENABLED);
            case ENDURANCE_ENABLED -> java.util.List.of(SEASON_PASS_ENABLED, ARENA_GAMIFICATION_ENABLED);
            default -> java.util.List.of();
        };
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
