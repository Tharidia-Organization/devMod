package com.devmod.compat.mods.sodium;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class SodiumCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(SodiumCompat.class);
    public static final String MOD_ID = "sodium";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> sodiumClientModClass;
    private static Class<?> sodiumOptionsClass;
    private static Object optionsInstance;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Sodium";
    }

    @Override
    public int priority() {
        // Very high priority - rendering core
        return 43;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:sodium] Sodium not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:sodium] Sodium detected");
        LOGGER.debug("[Compat:sodium] Version: {}", Compat.getVersion(MOD_ID));

        // API loading deferred to client init (client-only mod)
    }

    /**
     * Load Sodium API classes via reflection.
     */
    private void loadApi() {
        try {
            // Sodium package structures
            String[] mainClasses = {
                "me.jellysquid.mods.sodium.client.SodiumClientMod",
                "net.caffeinemc.mods.sodium.client.SodiumClientMod"
            };

            for (String className : mainClasses) {
                try {
                    sodiumClientModClass = Class.forName(className);
                    LOGGER.debug("[Compat:sodium] Found main class at {}", className);
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:sodium] Main class not found: {}", className);
                }
            }

            // Try to find options class
            String[] optionsClasses = {
                "me.jellysquid.mods.sodium.client.gui.SodiumGameOptions",
                "net.caffeinemc.mods.sodium.client.gui.SodiumGameOptions",
                "me.jellysquid.mods.sodium.client.SodiumClientMod$Options"
            };

            for (String className : optionsClasses) {
                try {
                    sodiumOptionsClass = Class.forName(className);
                    LOGGER.debug("[Compat:sodium] Found options at {}", className);

                    // Try to get options instance
                    if (sodiumClientModClass != null) {
                        try {
                            Method getOptionsMethod = sodiumClientModClass.getMethod("options");
                            optionsInstance = getOptionsMethod.invoke(null);
                        } catch (NoSuchMethodException e) {
                            LOGGER.trace("[Compat:sodium] options() not available", e);
                            try {
                                Field optionsField = sodiumClientModClass.getField("options");
                                optionsInstance = optionsField.get(null);
                            } catch (NoSuchFieldException e2) {
                                LOGGER.trace("[Compat:sodium] options field not available", e2);
                            }
                        }
                    }

                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:sodium] Options class not found: {}", className);
                }
            }

            if (sodiumClientModClass != null || sodiumOptionsClass != null) {
                apiAvailable = true;
                LOGGER.info("[Compat:sodium] Sodium API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:sodium] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;

        loadApi();
        LOGGER.debug("[Compat:sodium] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Rendering optimization detection, graphics settings, performance metrics";
    }

    /**
     * Check if Sodium is available.
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
     * Get Sodium graphics quality setting.
     *
     * @return Quality level name, or null if unavailable
     */
    public static String getQualityLevel() {
        if (!apiAvailable || optionsInstance == null) {
            return null;
        }

        try {
            // Try to find quality field
            Field qualityField = optionsInstance.getClass().getField("quality");
            Object quality = qualityField.get(optionsInstance);
            if (quality != null) {
                return quality.toString();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:sodium] Error getting quality: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get Sodium performance info for telemetry.
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

        String quality = getQualityLevel();
        if (quality != null) {
            info.put("qualityLevel", quality);
        }

        // Known Sodium features
        List<String> features = Arrays.asList(
            "modern_renderer",
            "chunk_optimization",
            "gpu_acceleration",
            "translucent_sorting"
        );
        info.put("knownFeatures", features);

        return info;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Sodium: not available";
        }
        if (!apiAvailable) {
            return "Sodium: detected (API not loaded)";
        }

        String quality = getQualityLevel();
        if (quality != null) {
            return "Sodium: quality=" + quality;
        }
        return "Sodium: rendering active";
    }
}
