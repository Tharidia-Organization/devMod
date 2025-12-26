package com.devmod.client.compat.mods.fancymenu;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.gui.screens.Screen;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
public class FancyMenuCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(FancyMenuCompat.class);
    public static final String MOD_ID = "fancymenu";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> layoutHandlerClass;
    private static Class<?> customizationClass;
    private static Method isScreenCustomizedMethod;
    private static Method getLayoutForScreenMethod;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "FancyMenu";
    }

    @Override
    public int priority() {
        // High priority - UI framework
        return 14;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:fancymenu] FancyMenu not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:fancymenu] FancyMenu detected");
        LOGGER.debug("[Compat:fancymenu] Version: {}", Compat.getVersion(MOD_ID));

        loadApi();
    }

    /**
     * Load FancyMenu API classes via reflection.
     */
    private void loadApi() {
        try {
            // FancyMenu package structures (v2 and v3 differ)
            String[][] packagePatterns = {
                {"de.keksuccino.fancymenu.menu.fancy", "FancyMenu"},
                {"de.keksuccino.fancymenu.customization", "LayoutHandler"},
                {"de.keksuccino.fancymenu.api", "FancyMenuAPI"}
            };

            for (String[] pattern : packagePatterns) {
                try {
                    layoutHandlerClass = Class.forName(pattern[0] + "." + pattern[1]);
                    LOGGER.debug("[Compat:fancymenu] Found {} at {}", pattern[1], pattern[0]);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            // Try to find customization detection methods
            if (layoutHandlerClass != null) {
                try {
                    isScreenCustomizedMethod = layoutHandlerClass
                        .getMethod("isScreenCustomized", Screen.class);
                } catch (NoSuchMethodException ignored) {
                    // Try alternative method names
                    try {
                        isScreenCustomizedMethod = layoutHandlerClass
                            .getMethod("hasCustomLayout", String.class);
                    } catch (NoSuchMethodException ignored2) {}
                }

                apiAvailable = true;
                LOGGER.info("[Compat:fancymenu] FancyMenu API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:fancymenu] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:fancymenu] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Custom menu detection, layout awareness, overlay coordination";
    }

    /**
     * Check if FancyMenu is available.
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
     * Check if a screen has FancyMenu customizations.
     *
     * @param screen The screen to check
     * @return true if customized by FancyMenu
     */
    public static boolean isScreenCustomized(Screen screen) {
        if (!apiAvailable || screen == null || isScreenCustomizedMethod == null) {
            return false;
        }

        try {
            Object result = isScreenCustomizedMethod.invoke(null, screen);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            LOGGER.debug("[Compat:fancymenu] Error checking screen: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if a screen class has FancyMenu customizations.
     *
     * @param screenClass The screen class name
     * @return true if customized
     */
    public static boolean isScreenClassCustomized(String screenClass) {
        if (!apiAvailable || screenClass == null) {
            return false;
        }

        try {
            // Try to find method that takes screen class name
            if (layoutHandlerClass != null) {
                Method hasLayoutMethod = layoutHandlerClass
                    .getMethod("hasCustomLayout", String.class);
                Object result = hasLayoutMethod.invoke(null, screenClass);
                return result instanceof Boolean && (Boolean) result;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:fancymenu] Error checking screen class: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Get customization info for a screen.
     *
     * @param screen The screen
     * @return Map of customization info
     */
    public static Map<String, Object> getScreenCustomizationInfo(Screen screen) {
        Map<String, Object> info = new LinkedHashMap<>();

        if (!available || screen == null) {
            return info;
        }

        String screenClass = screen.getClass().getName();
        info.put("screenClass", screenClass);
        info.put("isCustomized", isScreenCustomized(screen));

        return info;
    }

    /**
     * Check if DevMod should adjust its UI for a FancyMenu-customized screen.
     * Useful for avoiding overlay conflicts.
     *
     * @param screen The current screen
     * @return true if DevMod should use minimal UI mode
     */
    public static boolean shouldUseMinimalUI(Screen screen) {
        if (!available) {
            return false;
        }

        // If FancyMenu has customized this screen, DevMod should be careful
        // about adding overlays that might conflict
        return isScreenCustomized(screen);
    }

    /**
     * Get list of known customized screen types.
     * Note: This is a best-effort detection.
     *
     * @return Set of customized screen class names
     */
    public static Set<String> getCustomizedScreenTypes() {
        Set<String> screens = new HashSet<>();

        if (!apiAvailable) {
            return screens;
        }

        try {
            // Try to get all customized screens
            if (layoutHandlerClass != null) {
                Method getCustomizedScreensMethod = layoutHandlerClass
                    .getMethod("getCustomizedScreens");
                Object result = getCustomizedScreensMethod.invoke(null);

                if (result instanceof Collection<?> collection) {
                    for (Object item : collection) {
                        if (item != null) {
                            screens.add(item.toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:fancymenu] Error getting customized screens: {}", e.getMessage());
        }

        return screens;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "FancyMenu: not available";
        }
        if (!apiAvailable) {
            return "FancyMenu: detected (API not loaded)";
        }
        return "FancyMenu: API available";
    }
}
