package com.devmod.compat.mods.terrablender;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class TerraBlenderCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(TerraBlenderCompat.class);
    public static final String MOD_ID = "terrablender";

    private static volatile boolean available = false;
    private static volatile boolean initialized = false;
    private static volatile boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> regionsClass;
    private static Method getRegionsMethod;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "TerraBlender";
    }

    @Override
    public int priority() {
        // High priority - world generation foundation
        return 20;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:terrablender] TerraBlender not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:terrablender] TerraBlender detected");
        LOGGER.debug("[Compat:terrablender] Version: {}", Compat.getVersion(MOD_ID));

        loadApi();
    }

    /**
     * Load TerraBlender API classes via reflection.
     */
    private void loadApi() {
        try {
            // TerraBlender package structure
            String[] packages = {
                "terrablender.api",
                "terrablender.core"
            };

            for (String pkg : packages) {
                try {
                    regionsClass = Class.forName(pkg + ".Regions", false, TerraBlenderCompat.class.getClassLoader());
                    LOGGER.debug("[Compat:terrablender] Found Regions at {}", pkg);
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:terrablender] Regions class not found in {}", pkg);
                }
            }

            if (regionsClass != null) {
                try {
                    getRegionsMethod = regionsClass.getMethod("getOverworldRegions");
                } catch (NoSuchMethodException e) {
                    LOGGER.trace("[Compat:terrablender] getOverworldRegions not available", e);
                    try {
                        getRegionsMethod = regionsClass.getMethod("getRegions");
                    } catch (NoSuchMethodException e2) {
                        LOGGER.trace("[Compat:terrablender] getRegions not available", e2);
                    }
                }

                apiAvailable = true;
                LOGGER.info("[Compat:terrablender] TerraBlender API loaded");
            }

        } catch (Throwable t) {
            LOGGER.debug("[Compat:terrablender] Error loading API: {}", t.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:terrablender] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Biome region detection, custom biome tracking, worldgen info";
    }

    /**
     * Check if TerraBlender is available.
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
     * Get count of registered overworld regions.
     *
     * @return Number of regions, or -1 if unavailable
     */
    public static int getOverworldRegionCount() {
        if (!apiAvailable || getRegionsMethod == null) {
            return -1;
        }

        try {
            Object result = getRegionsMethod.invoke(null);
            if (result instanceof Collection<?> collection) {
                return collection.size();
            } else if (result instanceof Map<?, ?> map) {
                return map.size();
            }
        } catch (Throwable t) {
            apiAvailable = false;
            LOGGER.debug("[Compat:terrablender] Error getting region count: {}", t.getMessage());
        }

        return -1;
    }

    /**
     * Get names of registered regions.
     *
     * @return Set of region names
     */
    public static Set<String> getRegionNames() {
        Set<String> names = new HashSet<>();

        if (!apiAvailable || getRegionsMethod == null) {
            return names;
        }

        try {
            Object result = getRegionsMethod.invoke(null);

            if (result instanceof Collection<?> collection) {
                for (Object region : collection) {
                    if (region != null) {
                        // Try to get region name
                        try {
                            Method getNameMethod = region.getClass().getMethod("getName");
                            Object name = getNameMethod.invoke(region);
                            if (name != null) {
                                names.add(name.toString());
                            }
                        } catch (NoSuchMethodException ignored) {
                            names.add(region.getClass().getSimpleName());
                        }
                    }
                }
            } else if (result instanceof Map<?, ?> map) {
                for (Object key : map.keySet()) {
                    if (key != null) {
                        names.add(key.toString());
                    }
                }
            }
        } catch (Throwable t) {
            apiAvailable = false;
            LOGGER.debug("[Compat:terrablender] Error getting region names: {}", t.getMessage());
        }

        return names;
    }

    /**
     * Get TerraBlender worldgen info for telemetry.
     *
     * @return Map of worldgen info
     */
    public static Map<String, Object> getWorldgenInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        if (!available) {
            return info;
        }

        info.put("available", true);
        info.put("version", Compat.getVersion(MOD_ID));

        int regionCount = getOverworldRegionCount();
        if (regionCount >= 0) {
            info.put("overworldRegions", regionCount);
        }

        Set<String> regionNames = getRegionNames();
        if (!regionNames.isEmpty()) {
            info.put("regionNames", new ArrayList<>(regionNames));
        }

        return info;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "TerraBlender: not available";
        }
        if (!apiAvailable) {
            return "TerraBlender: detected (API not loaded)";
        }

        int regions = getOverworldRegionCount();
        if (regions >= 0) {
            return "TerraBlender: " + regions + " overworld regions";
        }
        return "TerraBlender: API available";
    }
}
