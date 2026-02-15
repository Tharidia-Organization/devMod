package com.devmod.compat.mods.entityculling;

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

public class EntityCullingCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityCullingCompat.class);
    public static final String MOD_ID = "entityculling";

    private static volatile boolean available = false;
    private static volatile boolean initialized = false;
    private static volatile boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> entityCullingModClass;
    private static Class<?> cullingHandlerClass;
    private static Object cullingHandlerInstance;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Entity Culling";
    }

    @Override
    public int priority() {
        // High priority - rendering optimization
        return 44;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:entityculling] Entity Culling not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:entityculling] Entity Culling detected");
        LOGGER.debug("[Compat:entityculling] Version: {}", Compat.getVersion(MOD_ID));

        // API loading deferred to client init (client-only mod)
    }

    /**
     * Load Entity Culling API classes via reflection.
     */
    private void loadApi() {
        try {
            // Entity Culling package structures
            String[] mainClasses = {
                "dev.tr7zw.entityculling.EntityCullingMod",
                "dev.tr7zw.entityculling.EntityCullingModBase"
            };

            for (String className : mainClasses) {
                try {
                    entityCullingModClass = Class.forName(className);
                    LOGGER.debug("[Compat:entityculling] Found main class at {}", className);
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:entityculling] Main class not found: {}", className);
                }
            }

            // Try to find culling handler
            String[] handlerClasses = {
                "dev.tr7zw.entityculling.CullTask",
                "dev.tr7zw.entityculling.EntityCullingHandler"
            };

            for (String className : handlerClasses) {
                try {
                    cullingHandlerClass = Class.forName(className);
                    LOGGER.debug("[Compat:entityculling] Found handler at {}", className);

                    // Try to get handler instance
                    try {
                        Field instanceField = cullingHandlerClass.getField("INSTANCE");
                        cullingHandlerInstance = instanceField.get(null);
                    } catch (NoSuchFieldException e) {
                        LOGGER.trace("[Compat:entityculling] INSTANCE field missing on {}", className, e);
                    }

                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:entityculling] Handler class not found: {}", className);
                }
            }

            if (entityCullingModClass != null) {
                apiAvailable = true;
                LOGGER.info("[Compat:entityculling] Entity Culling API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:entityculling] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;

        loadApi();
        LOGGER.debug("[Compat:entityculling] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Entity occlusion culling, block entity culling, render optimization";
    }

    /**
     * Check if Entity Culling is available.
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
     * Get culling statistics if available.
     *
     * @return Map of culling stats
     */
    public static Map<String, Object> getCullingStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        if (!apiAvailable || cullingHandlerInstance == null) {
            return stats;
        }

        try {
            Class<?> handlerCls = cullingHandlerInstance.getClass();

            // Try to get various stats
            String[] statFields = {
                "culledEntities", "culledBlockEntities",
                "checkedEntities", "skippedEntities"
            };

            for (String fieldName : statFields) {
                try {
                    Field field = handlerCls.getField(fieldName);
                    Object value = field.get(cullingHandlerInstance);
                    if (value instanceof Number) {
                        stats.put(fieldName, ((Number) value).intValue());
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    LOGGER.trace("[Compat:entityculling] Stat field unavailable: {}", fieldName, e);
                }
            }

            // Try getter methods
            try {
                Method getCulledMethod = handlerCls.getMethod("getCulledCount");
                Object culled = getCulledMethod.invoke(cullingHandlerInstance);
                if (culled instanceof Number) {
                    stats.put("culledCount", ((Number) culled).intValue());
                }
            } catch (NoSuchMethodException e) {
                LOGGER.trace("[Compat:entityculling] getCulledCount not available", e);
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:entityculling] Error getting stats: {}", e.getMessage());
        }

        return stats;
    }

    /**
     * Get Entity Culling performance info for telemetry.
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

        Map<String, Object> stats = getCullingStats();
        if (!stats.isEmpty()) {
            info.put("stats", stats);
        }

        // Known Entity Culling features
        List<String> features = Arrays.asList(
            "entity_occlusion",
            "block_entity_culling",
            "frustum_culling",
            "async_culling"
        );
        info.put("knownFeatures", features);

        return info;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Entity Culling: not available";
        }
        if (!apiAvailable) {
            return "Entity Culling: detected (API not loaded)";
        }

        Map<String, Object> stats = getCullingStats();
        Object culled = stats.get("culledCount");
        if (culled != null) {
            return "Entity Culling: " + culled + " entities culled";
        }
        return "Entity Culling: occlusion active";
    }
}
