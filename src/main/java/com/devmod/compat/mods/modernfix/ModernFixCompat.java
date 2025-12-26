package com.devmod.compat.mods.modernfix;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

/**
 * Compatibility module for ModernFix.
 *
 * ModernFix provides:
 * - Memory usage optimizations
 * - Startup time improvements
 * - Dynamic resource management
 * - Mod compatibility fixes
 *
 * This integration allows DevMod to:
 * - Detect ModernFix presence and config
 * - Track enabled optimizations
 * - Monitor memory improvements
 * - Include ModernFix info in telemetry
 *
 * @see <a href="https://github.com/embeddedt/ModernFix">ModernFix GitHub</a>
 */
public class ModernFixCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModernFixCompat.class);
    public static final String MOD_ID = "modernfix";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> modernFixClass;
    private static Class<?> configClass;
    private static Object configInstance;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "ModernFix";
    }

    @Override
    public int priority() {
        // High priority - core optimization mod
        return 40;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:modernfix] ModernFix not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:modernfix] ModernFix detected");
        LOGGER.debug("[Compat:modernfix] Version: {}", Compat.getVersion(MOD_ID));

        loadApi();
    }

    /**
     * Load ModernFix API/config classes via reflection.
     */
    private void loadApi() {
        try {
            // ModernFix package structures
            String[] mainClasses = {
                "org.embeddedt.modernfix.ModernFix",
                "org.embeddedt.modernfix.forge.ModernFixMod",
                "org.embeddedt.modernfix.neoforge.ModernFixMod"
            };

            for (String className : mainClasses) {
                try {
                    modernFixClass = Class.forName(className);
                    LOGGER.debug("[Compat:modernfix] Found main class at {}", className);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            // Try to find config class
            String[] configClasses = {
                "org.embeddedt.modernfix.core.config.ModernFixConfig",
                "org.embeddedt.modernfix.ModernFixConfig",
                "org.embeddedt.modernfix.core.ModernFixConfig"
            };

            for (String className : configClasses) {
                try {
                    configClass = Class.forName(className);
                    LOGGER.debug("[Compat:modernfix] Found config at {}", className);

                    // Try to get config instance
                    try {
                        Field instanceField = configClass.getField("INSTANCE");
                        configInstance = instanceField.get(null);
                    } catch (NoSuchFieldException ignored) {
                        try {
                            Method getMethod = configClass.getMethod("get");
                            configInstance = getMethod.invoke(null);
                        } catch (NoSuchMethodException ignored2) {}
                    }

                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            if (modernFixClass != null || configClass != null) {
                apiAvailable = true;
                LOGGER.info("[Compat:modernfix] ModernFix API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:modernfix] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:modernfix] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Memory optimization detection, startup improvements, config tracking";
    }

    /**
     * Check if ModernFix is available.
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
     * Get enabled ModernFix features.
     *
     * @return Set of enabled feature names
     */
    public static Set<String> getEnabledFeatures() {
        Set<String> features = new LinkedHashSet<>();

        if (!apiAvailable || configInstance == null) {
            return features;
        }

        try {
            Class<?> configCls = configInstance.getClass();

            // Common ModernFix features to check
            String[] featureFields = {
                "dynamicResources", "removeBlockingLevelEvent",
                "fixBlockEntityRendering", "removeSearchTrees",
                "optimizedModelLoading", "cacheDFU"
            };

            for (String fieldName : featureFields) {
                try {
                    Field field = configCls.getField(fieldName);
                    Object value = field.get(configInstance);
                    if (value instanceof Boolean && (Boolean) value) {
                        features.add(fieldName);
                    }
                } catch (NoSuchFieldException | IllegalAccessException ignored) {}
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:modernfix] Error reading features: {}", e.getMessage());
        }

        return features;
    }

    /**
     * Check if dynamic resources feature is enabled.
     *
     * @return true if enabled
     */
    public static boolean isDynamicResourcesEnabled() {
        return getEnabledFeatures().contains("dynamicResources");
    }

    /**
     * Get ModernFix performance info for telemetry.
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

        Set<String> features = getEnabledFeatures();
        if (!features.isEmpty()) {
            info.put("enabledFeatures", new ArrayList<>(features));
            info.put("featureCount", features.size());
        }

        return info;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "ModernFix: not available";
        }
        if (!apiAvailable) {
            return "ModernFix: detected (API not loaded)";
        }

        Set<String> features = getEnabledFeatures();
        if (!features.isEmpty()) {
            return "ModernFix: " + features.size() + " optimizations active";
        }
        return "ModernFix: API available";
    }
}
