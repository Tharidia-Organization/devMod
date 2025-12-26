package com.devmod.client.compat.mods.yacl;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class YaclCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(YaclCompat.class);
    public static final String MOD_ID = "yet_another_config_lib_v3";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    @Nullable
    private static Class<?> yaclClass;
    @Nullable
    private static Class<?> configCategoryClass;
    @Nullable
    private static Class<?> optionClass;
    @Nullable
    private static Class<?> optionDescriptionClass;
    @Nullable
    private static Method createBuilderMethod;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Yet Another Config Lib";
    }

    @Override
    public int priority() {
        // Same as Cloth Config - UI framework
        return 15;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        // YACL uses different mod ID formats
        if (!Compat.isLoaded(MOD_ID) && !Compat.isLoaded("yacl")) {
            LOGGER.debug("[Compat:yacl] YACL not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:yacl] YACL detected");

        loadApi();
    }

    /**
     * Load YACL API classes via reflection.
     */
    private void loadApi() {
        try {
            // YACL v3 package structure
            String[] packages = {
                "dev.isxander.yacl3",
                "dev.isxander.yacl"
            };

            for (String pkg : packages) {
                try {
                    yaclClass = Class.forName(pkg + ".api.YetAnotherConfigLib");
                    LOGGER.debug("[Compat:yacl] Found YACL at {}", pkg);

                    configCategoryClass = Class.forName(pkg + ".api.ConfigCategory");
                    optionClass = Class.forName(pkg + ".api.Option");
                    optionDescriptionClass = Class.forName(pkg + ".api.OptionDescription");

                    createBuilderMethod = yaclClass.getMethod("createBuilder");
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            if (yaclClass != null) {
                apiAvailable = true;
                LOGGER.info("[Compat:yacl] YACL API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:yacl] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:yacl] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Config screen framework, category-based options, search support";
    }

    /**
     * Check if YACL is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the YACL API is accessible.
     */
    public static boolean isApiAvailable() {
        return apiAvailable;
    }

    /**
     * Create a YACL config screen builder.
     *
     * @return Builder object, or null if YACL not available
     */
    @Nullable
    public static Object createBuilder() {
        if (!apiAvailable || createBuilderMethod == null) {
            return null;
        }

        try {
            return createBuilderMethod.invoke(null);
        } catch (Exception e) {
            LOGGER.debug("[Compat:yacl] Failed to create builder: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build a simple YACL config screen.
     *
     * @param parent Parent screen
     * @param title Screen title
     * @param configurer Consumer to configure the builder
     * @return Built screen, or null if failed
     */
    @Nullable
    public static Screen buildConfigScreen(Screen parent, Component title,
                                           Consumer<Object> configurer) {
        if (!apiAvailable) {
            return null;
        }

        try {
            Object builder = createBuilder();
            if (builder == null) return null;

            // Set title
            Method titleMethod = builder.getClass().getMethod("title", Component.class);
            builder = titleMethod.invoke(builder, title);

            // Let caller configure
            configurer.accept(builder);

            // Build the screen
            Method buildMethod = builder.getClass().getMethod("build");
            Object yacl = buildMethod.invoke(builder);

            // Generate screen
            Method generateScreenMethod = yacl.getClass().getMethod("generateScreen", Screen.class);
            return (Screen) generateScreenMethod.invoke(yacl, parent);

        } catch (Exception e) {
            LOGGER.debug("[Compat:yacl] Failed to build config screen: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Create a category builder.
     *
     * @return Category builder, or null if not available
     */
    @Nullable
    public static Object createCategoryBuilder() {
        if (!apiAvailable || configCategoryClass == null) {
            return null;
        }

        try {
            Method createBuilderMethod = configCategoryClass.getMethod("createBuilder");
            return createBuilderMethod.invoke(null);
        } catch (Exception e) {
            LOGGER.debug("[Compat:yacl] Failed to create category builder: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Create an option description.
     *
     * @param text Description text
     * @return Description object, or null if not available
     */
    @Nullable
    public static Object createDescription(Component text) {
        if (!apiAvailable || optionDescriptionClass == null) {
            return null;
        }

        try {
            Method ofMethod = optionDescriptionClass.getMethod("of", Component.class);
            return ofMethod.invoke(null, text);
        } catch (Exception e) {
            LOGGER.debug("[Compat:yacl] Failed to create description: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if YACL should be preferred over Cloth Config.
     * Returns true if YACL is available and Cloth Config is not.
     */
    public static boolean shouldPreferYacl() {
        if (!available) return false;

        // Check if Cloth Config is also available
        boolean clothConfigAvailable = Compat.isLoaded("cloth-config") ||
                                       Compat.isLoaded("cloth_config");

        // If both available, prefer Cloth Config (more established)
        // If only YACL, use YACL
        return !clothConfigAvailable;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "YACL: not available";
        }
        if (!apiAvailable) {
            return "YACL: detected (API not loaded)";
        }
        return "YACL: API available" + (shouldPreferYacl() ? " [preferred]" : "");
    }
}
