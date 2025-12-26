package com.devmod.compat.mods.lithium;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class LithiumCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(LithiumCompat.class);
    public static final String MOD_ID = "lithium";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> lithiumConfigClass;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Lithium";
    }

    @Override
    public int priority() {
        // High priority - core logic optimization
        return 42;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:lithium] Lithium not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:lithium] Lithium detected");
        LOGGER.debug("[Compat:lithium] Version: {}", Compat.getVersion(MOD_ID));

        loadApi();
    }

    /**
     * Load Lithium config classes via reflection.
     */
    private void loadApi() {
        try {
            // Lithium config locations
            String[] configClasses = {
                "me.jellysquid.mods.lithium.common.config.LithiumConfig",
                "net.caffeinemc.mods.lithium.common.config.LithiumConfig",
                "me.jellysquid.mods.lithium.LithiumConfig"
            };

            for (String className : configClasses) {
                try {
                    lithiumConfigClass = Class.forName(className);
                    LOGGER.debug("[Compat:lithium] Found config at {}", className);

                    apiAvailable = true;
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:lithium] Config class not found: {}", className);
                }
            }

            if (apiAvailable) {
                LOGGER.info("[Compat:lithium] Lithium API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:lithium] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:lithium] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Game logic optimization detection, AI improvements, tick optimization";
    }

    /**
     * Check if Lithium is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the API is accessible.
     */
    public static boolean isApiAvailable() {
        return apiAvailable;
    }

    /**
     * Get known Lithium optimization categories.
     * Lithium organizes its optimizations into categories.
     *
     * @return List of optimization categories
     */
    public static List<String> getOptimizationCategories() {
        // Known Lithium optimization categories
        return Arrays.asList(
            "ai",           // Entity AI improvements
            "alloc",        // Memory allocation optimizations
            "block",        // Block-related optimizations
            "cached_hashcode", // Hashcode caching
            "chunk",        // Chunk operations
            "collections",  // Collection optimizations
            "entity",       // Entity processing
            "gen",          // World generation
            "math",         // Math optimizations
            "shapes",       // VoxelShape optimizations
            "world"         // World tick optimizations
        );
    }

    /**
     * Get Lithium performance info for telemetry.
     *
     * @return Map of performance info
     */
    public static Map<String, Object> getPerformanceInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        if (!available) {
            return info;
        }

        info.put("available", true);
        info.put("version", Compat.getVersion(MOD_ID));
        info.put("apiAvailable", apiAvailable);
        info.put("categories", getOptimizationCategories());

        return info;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Lithium: not available";
        }

        List<String> categories = getOptimizationCategories();
        return "Lithium: " + categories.size() + " optimization categories";
    }
}
