package com.devmod.compat.mods.iris;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Compatibility module for Iris Shaders.
 *
 * Iris provides:
 * - Shader support for Fabric/NeoForge (Sodium-based)
 * - OptiFine shader pack compatibility
 * - Shadow and lighting effects
 * - Performance optimizations
 *
 * This integration allows DevMod to:
 * - Detect if shaders are active
 * - Get current shader pack name
 * - Check for shader features that affect rendering
 * - Adjust DevMod overlays for shader compatibility
 *
 * @see <a href="https://github.com/IrisShaders/Iris">Iris GitHub</a>
 */
public class IrisCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(IrisCompat.class);
    public static final String MOD_ID = "iris";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> irisApiClass;
    private static Method isShadersEnabledMethod;
    private static Method getCurrentPackNameMethod;
    private static Method isRenderingShadowPassMethod;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Iris Shaders";
    }

    @Override
    public int priority() {
        // Medium priority - rendering
        return 25;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        // Iris can have different mod IDs
        if (!Compat.isLoaded(MOD_ID) && !Compat.isLoaded("oculus")) {
            LOGGER.debug("[Compat:iris] Iris/Oculus not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:iris] Iris/Oculus detected");

        loadApi();
    }

    /**
     * Load Iris API classes via reflection.
     */
    private void loadApi() {
        try {
            // Try different API locations
            String[] apiClasses = {
                "net.irisshaders.iris.api.v0.IrisApi",
                "net.coderbot.iris.apiimpl.IrisApiV0Impl",
                "net.coderbot.iris.Iris"
            };

            for (String className : apiClasses) {
                try {
                    irisApiClass = Class.forName(className);
                    LOGGER.debug("[Compat:iris] Found Iris API at {}", className);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            if (irisApiClass != null) {
                // Get instance method first if needed
                Object apiInstance = getApiInstance();

                // Find shaders enabled method
                try {
                    isShadersEnabledMethod = irisApiClass.getMethod("isShaderPackInUse");
                } catch (NoSuchMethodException e) {
                    try {
                        isShadersEnabledMethod = irisApiClass.getMethod("isShadersEnabled");
                    } catch (NoSuchMethodException ignored) {}
                }

                // Find current pack name method
                try {
                    getCurrentPackNameMethod = irisApiClass.getMethod("getCurrentPackName");
                } catch (NoSuchMethodException e) {
                    try {
                        getCurrentPackNameMethod = irisApiClass.getMethod("getConfiguredPackName");
                    } catch (NoSuchMethodException ignored) {}
                }

                // Find shadow pass method
                try {
                    isRenderingShadowPassMethod = irisApiClass.getMethod("isRenderingShadowPass");
                } catch (NoSuchMethodException ignored) {}

                apiAvailable = true;
                LOGGER.info("[Compat:iris] Iris API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:iris] Error loading API: {}", e.getMessage());
        }
    }

    @Nullable
    private static Object getApiInstance() {
        if (irisApiClass == null) return null;

        try {
            // Try getInstance() or INSTANCE field
            try {
                Method getInstanceMethod = irisApiClass.getMethod("getInstance");
                return getInstanceMethod.invoke(null);
            } catch (NoSuchMethodException e) {
                try {
                    var field = irisApiClass.getField("INSTANCE");
                    return field.get(null);
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:iris] Could not get API instance: {}", e.getMessage());
        }

        return null;
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:iris] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Shader detection, pack info, rendering state";
    }

    /**
     * Check if Iris is available.
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
     * Check if shaders are currently enabled/active.
     *
     * @return true if shader pack is in use
     */
    public static boolean areShadersEnabled() {
        if (!apiAvailable || isShadersEnabledMethod == null) {
            return false;
        }

        try {
            Object api = getApiInstance();
            if (api != null) {
                return (boolean) isShadersEnabledMethod.invoke(api);
            } else {
                // Try static call
                return (boolean) isShadersEnabledMethod.invoke(null);
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:iris] Error checking shader state: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the current shader pack name.
     *
     * @return Shader pack name, or null if none/disabled
     */
    @Nullable
    public static String getCurrentShaderPack() {
        if (!apiAvailable || getCurrentPackNameMethod == null) {
            return null;
        }

        try {
            Object api = getApiInstance();
            Object result;
            if (api != null) {
                result = getCurrentPackNameMethod.invoke(api);
            } else {
                result = getCurrentPackNameMethod.invoke(null);
            }

            if (result instanceof Optional<?> opt) {
                return opt.map(Object::toString).orElse(null);
            } else if (result != null) {
                return result.toString();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:iris] Error getting shader pack name: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Check if currently rendering shadow pass.
     * Useful for skipping certain rendering in shadow pass.
     *
     * @return true if in shadow pass
     */
    public static boolean isRenderingShadowPass() {
        if (!apiAvailable || isRenderingShadowPassMethod == null) {
            return false;
        }

        try {
            Object api = getApiInstance();
            if (api != null) {
                return (boolean) isRenderingShadowPassMethod.invoke(api);
            } else {
                return (boolean) isRenderingShadowPassMethod.invoke(null);
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:iris] Error checking shadow pass: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get shader status info for telemetry/debug.
     *
     * @return Shader status map
     */
    public static Map<String, Object> getShaderStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        if (!available) {
            status.put("available", false);
            return status;
        }

        status.put("available", true);
        status.put("apiLoaded", apiAvailable);

        if (apiAvailable) {
            boolean enabled = areShadersEnabled();
            status.put("shadersEnabled", enabled);

            if (enabled) {
                String packName = getCurrentShaderPack();
                if (packName != null) {
                    status.put("shaderPack", packName);
                }
            }
        }

        return status;
    }

    /**
     * Check if DevMod should adjust rendering for shaders.
     * Some overlays may need adjustment when shaders are active.
     *
     * @return true if shader adjustments should be applied
     */
    public static boolean shouldAdjustForShaders() {
        return areShadersEnabled();
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Iris: not available";
        }
        if (!apiAvailable) {
            return "Iris: detected (API not loaded)";
        }

        boolean enabled = areShadersEnabled();
        if (enabled) {
            String pack = getCurrentShaderPack();
            return "Iris: " + (pack != null ? pack : "shaders enabled");
        }

        return "Iris: shaders disabled";
    }
}
