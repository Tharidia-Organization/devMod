package com.devmod.compat.mods.c2me;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class C2MECompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(C2MECompat.class);
    public static final String MOD_ID = "c2me";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    @Nullable
    private static Class<?> configClass;
    @Nullable
    private static Class<?> c2meModClass;
    @Nullable
    private static Object configInstance;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "C2ME";
    }

    @Override
    public int priority() {
        // High priority - core performance mod
        return 22;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:c2me] C2ME not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:c2me] C2ME detected");
        LOGGER.debug("[Compat:c2me] Version: {}", Compat.getVersion(MOD_ID));

        loadApi();
    }

    /**
     * Load C2ME API/config classes via reflection.
     */
    private void loadApi() {
        try {
            // C2ME package structures (differ between versions)
            String[][] classPatterns = {
                {"com.ishland.c2me", "C2MEMod"},
                {"com.ishland.c2me.base", "C2MEConfig"},
                {"com.ishland.c2me.common", "Config"}
            };

            for (String[] pattern : classPatterns) {
                try {
                    c2meModClass = Class.forName(pattern[0] + "." + pattern[1]);
                    LOGGER.debug("[Compat:c2me] Found {} at {}", pattern[1], pattern[0]);
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:c2me] Class {} not found in {}", pattern[1], pattern[0]);
                }
            }

            // Try to find config class
            String[] configClasses = {
                "com.ishland.c2me.base.common.config.C2MEConfig",
                "com.ishland.c2me.common.config.C2MEConfig",
                "com.ishland.c2me.C2MEConfig"
            };

            for (String className : configClasses) {
                try {
                    configClass = Class.forName(className);
                    LOGGER.debug("[Compat:c2me] Found config at {}", className);

                    // Try to get config instance
                    try {
                        Field instanceField = configClass.getField("INSTANCE");
                        configInstance = instanceField.get(null);
                    } catch (NoSuchFieldException e) {
                        LOGGER.trace("[Compat:c2me] INSTANCE field missing on {}", className, e);
                        try {
                            Method getInstanceMethod = configClass.getMethod("getInstance");
                            configInstance = getInstanceMethod.invoke(null);
                        } catch (NoSuchMethodException e2) {
                            LOGGER.trace("[Compat:c2me] getInstance method missing on {}", className, e2);
                        }
                    }

                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:c2me] Config class not found: {}", className);
                }
            }

            if (c2meModClass != null || configClass != null) {
                apiAvailable = true;
                LOGGER.info("[Compat:c2me] C2ME API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:c2me] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:c2me] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Chunk threading detection, performance config, generation metrics";
    }

    /**
     * Check if C2ME is available.
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
     * Get C2ME configuration settings.
     *
     * @return Map of config values
     */
    public static Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();

        if (!apiAvailable) {
            return config;
        }

        try {
            if (configInstance != null) {
                // Try to extract common config values
                Class<?> configCls = configInstance.getClass();

                // Thread count settings
                tryGetConfigValue(configCls, configInstance, "globalExecutorParallelism", config, "globalThreads");
                tryGetConfigValue(configCls, configInstance, "ioParallelism", config, "ioThreads");

                // Feature toggles
                tryGetConfigValue(configCls, configInstance, "asyncSerialization", config, "asyncSerialization");
                tryGetConfigValue(configCls, configInstance, "threadedWorldGen", config, "threadedWorldGen");
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:c2me] Error reading config: {}", e.getMessage());
        }

        return config;
    }

    private static void tryGetConfigValue(Class<?> cls, Object instance, String fieldName,
                                          Map<String, Object> target, String key) {
        try {
            Field field = cls.getField(fieldName);
            Object value = field.get(instance);
            if (value != null) {
                target.put(key, value);
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // Try getter method
            try {
                String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                Method getter = cls.getMethod(getterName);
                Object value = getter.invoke(instance);
                if (value != null) {
                    target.put(key, value);
                }
            } catch (Exception e) {
                LOGGER.trace("[Compat:c2me] Getter invocation failed for {}", fieldName, e);
            }
        }
    }

    /**
     * Check if C2ME's async serialization is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public static boolean isAsyncSerializationEnabled() {
        Map<String, Object> config = getConfig();
        Object value = config.get("asyncSerialization");
        return value instanceof Boolean && (Boolean) value;
    }

    /**
     * Check if C2ME's threaded worldgen is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public static boolean isThreadedWorldGenEnabled() {
        Map<String, Object> config = getConfig();
        Object value = config.get("threadedWorldGen");
        return value instanceof Boolean && (Boolean) value;
    }

    /**
     * Get C2ME performance info for telemetry.
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

        Map<String, Object> config = getConfig();
        if (!config.isEmpty()) {
            info.put("config", config);
        }

        return info;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "C2ME: not available";
        }
        if (!apiAvailable) {
            return "C2ME: detected (API not loaded)";
        }

        Map<String, Object> config = getConfig();
        if (!config.isEmpty()) {
            Object threads = config.get("globalThreads");
            if (threads != null) {
                return "C2ME: " + threads + " threads";
            }
        }
        return "C2ME: API available";
    }
}
