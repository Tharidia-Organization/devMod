package com.devmod.compat;

import java.util.StringTokenizer;

import javax.annotation.Nullable;

import com.devmod.actions.ActionRegistry;
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
        v1 = stripSuffix(v1);
        v2 = stripSuffix(v2);

        StringTokenizer parts1 = new StringTokenizer(v1, ".");
        StringTokenizer parts2 = new StringTokenizer(v2, ".");

        while (parts1.hasMoreTokens() || parts2.hasMoreTokens()) {
            int p1 = parts1.hasMoreTokens() ? parseVersionPart(parts1.nextToken()) : 0;
            int p2 = parts2.hasMoreTokens() ? parseVersionPart(parts2.nextToken()) : 0;
            if (p1 != p2) {
                return p1 - p2;
            }
        }
        return 0;
    }

    private static String stripSuffix(String version) {
        int plus = version.indexOf('+');
        int dash = version.indexOf('-');
        int cut = -1;
        if (plus >= 0 && dash >= 0) {
            cut = Math.min(plus, dash);
        } else if (plus >= 0) {
            cut = plus;
        } else if (dash >= 0) {
            cut = dash;
        }
        return cut >= 0 ? version.substring(0, cut) : version;
    }

    private static int parseVersionPart(String part) {
        try {
            // Extract leading digits
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
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
