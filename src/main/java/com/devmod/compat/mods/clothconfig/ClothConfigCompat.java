package com.devmod.compat.mods.clothconfig;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Compatibility module for Cloth Config API.
 *
 * Cloth Config provides a powerful config screen API with:
 * - Sliders, toggles, dropdowns, color pickers
 * - Categories and sub-categories
 * - Validation and save callbacks
 * - Auto-config annotation system
 *
 * This integration allows DevMod to:
 * - Detect Cloth Config presence
 * - Provide enhanced config screens when available
 * - Use reflection to avoid hard compile-time dependency
 *
 * @see <a href="https://shedaniel.gitbook.io/cloth-config/">Cloth Config Documentation</a>
 */
public class ClothConfigCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClothConfigCompat.class);
    public static final String MOD_ID = "cloth_config";

    private static boolean available = false;
    private static boolean initialized = false;

    // Cached reflection references
    private static Class<?> configBuilderClass;
    private static Class<?> configCategoryClass;
    private static Class<?> configEntryBuilderClass;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Cloth Config API";
    }

    @Override
    public int priority() {
        // High priority - config API should initialize early
        return 10;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        try {
            // Try to load Cloth Config classes via reflection
            configBuilderClass = Class.forName("me.shedaniel.clothconfig2.api.ConfigBuilder");
            configCategoryClass = Class.forName("me.shedaniel.clothconfig2.api.ConfigCategory");
            configEntryBuilderClass = Class.forName("me.shedaniel.clothconfig2.api.ConfigEntryBuilder");

            available = true;
            LOGGER.info("[Compat:cloth_config] Cloth Config API detected and available");
            LOGGER.debug("[Compat:cloth_config] Version: {}", Compat.getVersion(MOD_ID));
        } catch (ClassNotFoundException e) {
            available = false;
            LOGGER.debug("[Compat:cloth_config] Cloth Config classes not found - using fallback");
        } catch (Exception e) {
            available = false;
            LOGGER.warn("[Compat:cloth_config] Error initializing: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;

        // Client-side initialization
        // Cloth Config is primarily a client-side library
        LOGGER.debug("[Compat:cloth_config] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Enhanced config screen support with sliders, toggles, and categories";
    }

    /**
     * Check if Cloth Config is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the ConfigBuilder class is loaded.
     */
    public static boolean hasConfigBuilder() {
        return configBuilderClass != null;
    }

    /**
     * Create a new ConfigBuilder instance via reflection.
     * Returns null if Cloth Config is not available.
     *
     * @return ConfigBuilder instance, or null
     */
    @Nullable
    public static Object createConfigBuilder() {
        if (!available || configBuilderClass == null) {
            return null;
        }

        try {
            Method createMethod = configBuilderClass.getMethod("create");
            return createMethod.invoke(null);
        } catch (Exception e) {
            LOGGER.debug("[Compat:cloth_config] Failed to create ConfigBuilder: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the ConfigEntryBuilder from a ConfigBuilder instance.
     *
     * @param configBuilder The ConfigBuilder instance
     * @return ConfigEntryBuilder, or null
     */
    @Nullable
    public static Object getEntryBuilder(Object configBuilder) {
        if (configBuilder == null || configBuilderClass == null) {
            return null;
        }

        try {
            Method method = configBuilderClass.getMethod("entryBuilder");
            return method.invoke(configBuilder);
        } catch (Exception e) {
            LOGGER.debug("[Compat:cloth_config] Failed to get entry builder: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Set the title of a ConfigBuilder.
     *
     * @param configBuilder The ConfigBuilder instance
     * @param titleKey Translation key for the title
     */
    public static void setTitle(Object configBuilder, String titleKey) {
        if (configBuilder == null) return;

        try {
            // Use reflection to call setTitle with a Component
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Method translatableMethod = componentClass.getMethod("translatable", String.class);
            Object titleComponent = translatableMethod.invoke(null, titleKey);

            Method setTitleMethod = configBuilderClass.getMethod("setTitle", componentClass);
            setTitleMethod.invoke(configBuilder, titleComponent);
        } catch (Exception e) {
            LOGGER.debug("[Compat:cloth_config] Failed to set title: {}", e.getMessage());
        }
    }

    /**
     * Get or create a category in the config builder.
     *
     * @param configBuilder The ConfigBuilder instance
     * @param categoryKey Translation key for the category name
     * @return The ConfigCategory, or null
     */
    @Nullable
    public static Object getOrCreateCategory(Object configBuilder, String categoryKey) {
        if (configBuilder == null) return null;

        try {
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Method translatableMethod = componentClass.getMethod("translatable", String.class);
            Object categoryName = translatableMethod.invoke(null, categoryKey);

            Method getCategoryMethod = configBuilderClass.getMethod("getOrCreateCategory", componentClass);
            return getCategoryMethod.invoke(configBuilder, categoryName);
        } catch (Exception e) {
            LOGGER.debug("[Compat:cloth_config] Failed to get/create category: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Add a boolean toggle entry to a category.
     *
     * @param entryBuilder The ConfigEntryBuilder
     * @param category The ConfigCategory to add to
     * @param key Translation key for the entry label
     * @param value Current value
     * @param defaultValue Default value
     * @param saveConsumer Consumer called when value is saved
     */
    public static void addBooleanToggle(
            Object entryBuilder,
            Object category,
            String key,
            boolean value,
            boolean defaultValue,
            Consumer<Boolean> saveConsumer
    ) {
        if (entryBuilder == null || category == null) return;

        try {
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Method translatableMethod = componentClass.getMethod("translatable", String.class);
            Object fieldName = translatableMethod.invoke(null, key);

            // Create the toggle entry
            Method startBooleanToggleMethod = configEntryBuilderClass.getMethod(
                "startBooleanToggle",
                componentClass,
                boolean.class
            );
            Object toggleBuilder = startBooleanToggleMethod.invoke(entryBuilder, fieldName, value);

            // Set default value
            Class<?> toggleBuilderClass = toggleBuilder.getClass();
            Method setDefaultMethod = toggleBuilderClass.getMethod("setDefaultValue", boolean.class);
            setDefaultMethod.invoke(toggleBuilder, defaultValue);

            // Set save consumer
            Method setSaveConsumerMethod = toggleBuilderClass.getMethod("setSaveConsumer", Consumer.class);
            setSaveConsumerMethod.invoke(toggleBuilder, saveConsumer);

            // Build the entry
            Method buildMethod = toggleBuilderClass.getMethod("build");
            Object entry = buildMethod.invoke(toggleBuilder);

            // Add to category
            Method addEntryMethod = configCategoryClass.getMethod("addEntry",
                Class.forName("me.shedaniel.clothconfig2.api.AbstractConfigListEntry"));
            addEntryMethod.invoke(category, entry);

        } catch (Exception e) {
            LOGGER.debug("[Compat:cloth_config] Failed to add boolean toggle: {}", e.getMessage());
        }
    }

    /**
     * Add an integer slider entry to a category.
     *
     * @param entryBuilder The ConfigEntryBuilder
     * @param category The ConfigCategory
     * @param key Translation key
     * @param value Current value
     * @param min Minimum value
     * @param max Maximum value
     * @param defaultValue Default value
     * @param saveConsumer Save callback
     */
    public static void addIntSlider(
            Object entryBuilder,
            Object category,
            String key,
            int value,
            int min,
            int max,
            int defaultValue,
            Consumer<Integer> saveConsumer
    ) {
        if (entryBuilder == null || category == null) return;

        try {
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Method translatableMethod = componentClass.getMethod("translatable", String.class);
            Object fieldName = translatableMethod.invoke(null, key);

            // Create the slider entry
            Method startIntSliderMethod = configEntryBuilderClass.getMethod(
                "startIntSlider",
                componentClass,
                int.class,
                int.class,
                int.class
            );
            Object sliderBuilder = startIntSliderMethod.invoke(entryBuilder, fieldName, value, min, max);

            // Set default value
            Class<?> sliderBuilderClass = sliderBuilder.getClass();
            Method setDefaultMethod = sliderBuilderClass.getMethod("setDefaultValue", int.class);
            setDefaultMethod.invoke(sliderBuilder, defaultValue);

            // Set save consumer
            Method setSaveConsumerMethod = sliderBuilderClass.getMethod("setSaveConsumer", Consumer.class);
            setSaveConsumerMethod.invoke(sliderBuilder, saveConsumer);

            // Build the entry
            Method buildMethod = sliderBuilderClass.getMethod("build");
            Object entry = buildMethod.invoke(sliderBuilder);

            // Add to category
            Method addEntryMethod = configCategoryClass.getMethod("addEntry",
                Class.forName("me.shedaniel.clothconfig2.api.AbstractConfigListEntry"));
            addEntryMethod.invoke(category, entry);

        } catch (Exception e) {
            LOGGER.debug("[Compat:cloth_config] Failed to add int slider: {}", e.getMessage());
        }
    }

    /**
     * Build the config screen from the ConfigBuilder.
     *
     * @param configBuilder The ConfigBuilder instance
     * @return The built Screen, or null
     */
    @Nullable
    public static Object buildScreen(Object configBuilder) {
        if (configBuilder == null) return null;

        try {
            Method buildMethod = configBuilderClass.getMethod("build");
            return buildMethod.invoke(configBuilder);
        } catch (Exception e) {
            LOGGER.debug("[Compat:cloth_config] Failed to build screen: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Set the parent screen for the config builder.
     *
     * @param configBuilder The ConfigBuilder instance
     * @param parentScreen The parent screen (or null)
     */
    public static void setParentScreen(Object configBuilder, Object parentScreen) {
        if (configBuilder == null) return;

        try {
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.Screen");
            Method setParentMethod = configBuilderClass.getMethod("setParentScreen", screenClass);
            setParentMethod.invoke(configBuilder, parentScreen);
        } catch (Exception e) {
            LOGGER.debug("[Compat:cloth_config] Failed to set parent screen: {}", e.getMessage());
        }
    }

    /**
     * Set the save callback for the config builder.
     *
     * @param configBuilder The ConfigBuilder instance
     * @param saveRunnable Runnable to execute when config is saved
     */
    public static void setSavingRunnable(Object configBuilder, Runnable saveRunnable) {
        if (configBuilder == null) return;

        try {
            Method setSavingMethod = configBuilderClass.getMethod("setSavingRunnable", Runnable.class);
            setSavingMethod.invoke(configBuilder, saveRunnable);
        } catch (Exception e) {
            LOGGER.debug("[Compat:cloth_config] Failed to set saving runnable: {}", e.getMessage());
        }
    }
}
