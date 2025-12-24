package com.devmod.compat;

import com.devmod.actions.ActionRegistry;

import javax.annotation.Nullable;

/**
 * Interface for mod compatibility modules.
 * Each module handles integration with a single external mod.
 *
 * Lifecycle:
 * 1. Module is registered with CompatRegistry
 * 2. CompatRegistry checks if modId() is loaded
 * 3. If loaded, initCommon() is called during FMLCommonSetupEvent
 * 4. If client, initClient() is called during FMLClientSetupEvent
 * 5. registerActions() is called to add radial menu actions
 *
 * All methods must be safe to call even if the mod is not present.
 * Use reflection or Optional patterns to avoid hard class dependencies.
 */
public interface CompatModule {

    /**
     * Get the mod ID this module provides compatibility for.
     * Must match the modId in the target mod's mods.toml.
     *
     * @return The mod ID (e.g., "irons_spellbooks")
     */
    String modId();

    /**
     * Get a human-readable name for this integration.
     *
     * @return Display name (e.g., "Iron's Spellbooks")
     */
    default String displayName() {
        return modId();
    }

    /**
     * Initialize common (server + client) functionality.
     * Called during FMLCommonSetupEvent if the mod is present.
     *
     * This method should:
     * - Set up event listeners
     * - Register capabilities
     * - Initialize reflection caches
     *
     * Must not access client-only classes!
     */
    void initCommon();

    /**
     * Initialize client-side functionality.
     * Called during FMLClientSetupEvent if the mod is present.
     *
     * This method should:
     * - Register renderers
     * - Set up HUD overlays
     * - Initialize keybinds
     *
     * Only called on physical client.
     */
    default void initClient() {
        // Default: no client initialization
    }

    /**
     * Register actions with the action registry.
     * Called after initCommon() if the mod is present.
     *
     * @param registry The action registry to add actions to
     */
    default void registerActions(@Nullable ActionRegistry registry) {
        // Default: no actions
    }

    /**
     * Called when the module is being unloaded (e.g., server shutdown).
     * Clean up any resources.
     */
    default void shutdown() {
        // Default: no cleanup needed
    }

    /**
     * Get the priority for initialization order.
     * Lower values initialize first.
     * Default is 100.
     *
     * @return Priority value (lower = earlier)
     */
    default int priority() {
        return 100;
    }

    /**
     * Check if this module is currently active and functional.
     * Can return false if initialization failed.
     *
     * @return true if the module is working correctly
     */
    default boolean isActive() {
        return Compat.isLoaded(modId());
    }

    /**
     * Get a description of what features this module provides.
     *
     * @return Feature description for logging/docs
     */
    default String getFeatureDescription() {
        return "Integration with " + displayName();
    }

    /**
     * Get the minimum required version of the target mod.
     * Return null if any version is acceptable.
     *
     * @return Minimum version string, or null
     */
    @Nullable
    default String getMinVersion() {
        return null;
    }

    /**
     * Check if the loaded mod version is compatible.
     * Default implementation accepts any version.
     *
     * @return true if the mod version is compatible
     */
    default boolean isVersionCompatible() {
        String minVersion = getMinVersion();
        if (minVersion == null) {
            return true;
        }

        String loadedVersion = Compat.getVersion(modId());
        if (loadedVersion == null) {
            return false;
        }

        // Simple version comparison - assumes semantic versioning
        // Override for more complex version requirements
        return compareVersions(loadedVersion, minVersion) >= 0;
    }

    /**
     * Simple semantic version comparison.
     * Returns negative if v1 < v2, zero if equal, positive if v1 > v2.
     */
    private static int compareVersions(String v1, String v2) {
        // Strip any suffix after + or -
        v1 = v1.split("[+-]")[0];
        v2 = v2.split("[+-]")[0];

        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (p1 != p2) {
                return p1 - p2;
            }
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            // Extract leading digits
            StringBuilder digits = new StringBuilder();
            for (char c : part.toCharArray()) {
                if (Character.isDigit(c)) {
                    digits.append(c);
                } else {
                    break;
                }
            }
            return digits.length() > 0 ? Integer.parseInt(digits.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
