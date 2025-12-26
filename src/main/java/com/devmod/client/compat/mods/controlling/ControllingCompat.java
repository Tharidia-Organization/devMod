package com.devmod.client.compat.mods.controlling;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.KeyMapping;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class ControllingCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ControllingCompat.class);
    public static final String MOD_ID = "controlling";

    private static boolean available = false;
    private static boolean initialized = false;

    // Cached reflection references
    private static Class<?> controllingClass;
    private static Class<?> controllingApiClass;
    private static Method getConflictsMethod;
    private static Method hasConflictMethod;

    // Cache of known conflicts (keybind name -> list of conflicting keybind names)
    private static final Map<String, List<String>> conflictCache = new HashMap<>();
    private static long lastCacheUpdate = 0;
    private static final long CACHE_TTL_MS = 5000; // 5 second cache

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Controlling";
    }

    @Override
    public int priority() {
        // Medium-high priority - UI/Input mod
        return 15;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:controlling] Controlling not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:controlling] Controlling detected");
        LOGGER.debug("[Compat:controlling] Version: {}", Compat.getVersion(MOD_ID));

        // Try to load API classes for advanced features
        tryLoadApi();
    }

    /**
     * Attempt to load Controlling's API classes via reflection.
     * This is optional - basic detection works without API access.
     */
    private void tryLoadApi() {
        try {
            // Try common package locations for Controlling
            String[] possiblePackages = {
                "com.blamejared.controlling",
                "com.blamejared.controlling.api",
                "blusunrize.lib.controlling"
            };

            for (String pkg : possiblePackages) {
                try {
                    controllingClass = Class.forName(pkg + ".Controlling");
                    LOGGER.debug("[Compat:controlling] Found Controlling class at {}", pkg);
                    break;
                } catch (ClassNotFoundException ignored) {
                    // Try next package
                }
            }

            if (controllingClass != null) {
                // Try to find conflict detection methods
                for (Method m : controllingClass.getDeclaredMethods()) {
                    if (m.getName().toLowerCase().contains("conflict")) {
                        LOGGER.debug("[Compat:controlling] Found method: {}", m.getName());
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:controlling] Could not load advanced API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:controlling] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Keybind conflict detection and management integration";
    }

    /**
     * Check if Controlling is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if a specific KeyMapping has conflicts.
     * Uses Minecraft's built-in conflict detection when Controlling is present.
     *
     * @param keyMapping The KeyMapping to check
     * @return true if there are conflicts
     */
    public static boolean hasConflict(KeyMapping keyMapping) {
        if (!available || keyMapping == null) {
            return false;
        }

        try {
            // Use Minecraft's KeyMapping conflict detection
            // When Controlling is loaded, it enhances this detection
            return !keyMapping.isUnbound() && hasKeyConflict(keyMapping);
        } catch (Exception e) {
            LOGGER.debug("[Compat:controlling] Error checking conflict: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if a KeyMapping conflicts with any other bound keys.
     */
    private static boolean hasKeyConflict(KeyMapping keyMapping) {
        try {
            // Access Minecraft's key mappings
            Class<?> keyMappingClass = KeyMapping.class;
            Field allField = keyMappingClass.getDeclaredField("ALL");
            allField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, KeyMapping> allMappings = (Map<String, KeyMapping>) allField.get(null);

            if (allMappings == null) return false;

            com.mojang.blaze3d.platform.InputConstants.Key key = keyMapping.getKey();
            if (key == null || key.getValue() == -1) return false;

            for (KeyMapping other : allMappings.values()) {
                if (other == keyMapping) continue;
                if (other.isUnbound()) continue;

                if (other.getKey().equals(key)) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:controlling] Error accessing key mappings: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Get all keybinds that conflict with a specific KeyMapping.
     *
     * @param keyMapping The KeyMapping to check
     * @return List of conflicting KeyMapping names
     */
    public static List<String> getConflicts(KeyMapping keyMapping) {
        List<String> conflicts = new ArrayList<>();

        if (!available || keyMapping == null || keyMapping.isUnbound()) {
            return conflicts;
        }

        try {
            Class<?> keyMappingClass = KeyMapping.class;
            Field allField = keyMappingClass.getDeclaredField("ALL");
            allField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, KeyMapping> allMappings = (Map<String, KeyMapping>) allField.get(null);

            if (allMappings == null) return conflicts;

            com.mojang.blaze3d.platform.InputConstants.Key key = keyMapping.getKey();
            if (key == null || key.getValue() == -1) return conflicts;

            for (KeyMapping other : allMappings.values()) {
                if (other == keyMapping) continue;
                if (other.isUnbound()) continue;

                if (other.getKey().equals(key)) {
                    conflicts.add(other.getName());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:controlling] Error getting conflicts: {}", e.getMessage());
        }

        return conflicts;
    }

    /**
     * Get a map of all current keybind conflicts.
     * Results are cached for performance.
     *
     * @return Map of keybind name to list of conflicting names
     */
    public static Map<String, List<String>> getAllConflicts() {
        if (!available) {
            return Collections.emptyMap();
        }

        // Check cache
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate < CACHE_TTL_MS && !conflictCache.isEmpty()) {
            return new HashMap<>(conflictCache);
        }

        // Rebuild cache
        conflictCache.clear();

        try {
            Class<?> keyMappingClass = KeyMapping.class;
            Field allField = keyMappingClass.getDeclaredField("ALL");
            allField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, KeyMapping> allMappings = (Map<String, KeyMapping>) allField.get(null);

            if (allMappings == null) return conflictCache;

            // Group by key
            Map<com.mojang.blaze3d.platform.InputConstants.Key, List<String>> keyGroups = new HashMap<>();
            for (KeyMapping mapping : allMappings.values()) {
                if (mapping.isUnbound()) continue;
                com.mojang.blaze3d.platform.InputConstants.Key key = mapping.getKey();
                if (key == null || key.getValue() == -1) continue;

                keyGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(mapping.getName());
            }

            // Find conflicts (groups with >1 keybind)
            for (List<String> group : keyGroups.values()) {
                if (group.size() > 1) {
                    for (String name : group) {
                        List<String> others = new ArrayList<>(group);
                        others.remove(name);
                        conflictCache.put(name, others);
                    }
                }
            }

            lastCacheUpdate = now;

        } catch (Exception e) {
            LOGGER.debug("[Compat:controlling] Error building conflict cache: {}", e.getMessage());
        }

        return new HashMap<>(conflictCache);
    }

    /**
     * Get a formatted conflict warning message for a keybind.
     *
     * @param keyMapping The KeyMapping to check
     * @return Warning message, or null if no conflicts
     */
    @Nullable
    public static String getConflictWarning(KeyMapping keyMapping) {
        if (!available || keyMapping == null) {
            return null;
        }

        List<String> conflicts = getConflicts(keyMapping);
        if (conflicts.isEmpty()) {
            return null;
        }

        if (conflicts.size() == 1) {
            return String.format("Conflicts with: %s", conflicts.get(0));
        } else {
            return String.format("Conflicts with %d keybinds: %s",
                conflicts.size(),
                String.join(", ", conflicts));
        }
    }

    /**
     * Get the total number of keybind conflicts in the system.
     */
    public static int getConflictCount() {
        return getAllConflicts().size() / 2; // Each conflict is counted twice
    }

    /**
     * Check if any DevMod keybinds have conflicts.
     *
     * @param devModKeyMappings List of DevMod's KeyMappings
     * @return true if any have conflicts
     */
    public static boolean hasDevModConflicts(List<KeyMapping> devModKeyMappings) {
        if (!available || devModKeyMappings == null) {
            return false;
        }

        for (KeyMapping mapping : devModKeyMappings) {
            if (hasConflict(mapping)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all DevMod keybinds that have conflicts.
     *
     * @param devModKeyMappings List of DevMod's KeyMappings
     * @return Map of conflicting keybind name to its conflicts
     */
    public static Map<String, List<String>> getDevModConflicts(List<KeyMapping> devModKeyMappings) {
        Map<String, List<String>> result = new HashMap<>();

        if (!available || devModKeyMappings == null) {
            return result;
        }

        for (KeyMapping mapping : devModKeyMappings) {
            List<String> conflicts = getConflicts(mapping);
            if (!conflicts.isEmpty()) {
                result.put(mapping.getName(), conflicts);
            }
        }

        return result;
    }

    /**
     * Clear the conflict cache.
     * Call this when keybinds are changed.
     */
    public static void invalidateCache() {
        conflictCache.clear();
        lastCacheUpdate = 0;
    }

    /**
     * Get a status summary of keybind conflicts.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Controlling: not available";
        }

        int conflicts = getConflictCount();
        if (conflicts == 0) {
            return "Controlling: no keybind conflicts";
        } else {
            return String.format("Controlling: %d keybind conflict%s",
                conflicts, conflicts == 1 ? "" : "s");
        }
    }
}
