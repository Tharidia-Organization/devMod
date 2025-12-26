package com.devmod.client.ui.editor.core;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;

import com.devmod.DevMod;
import com.devmod.config.EditorClientConfig;

public final class EditorConfig {

    private EditorConfig() {}

    // ═══════════════════════════════════════════════════════════════
    // CONFIG CHANGE LISTENERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Listener interface for config changes.
     */
    @FunctionalInterface
    public interface ConfigChangeListener {
        /**
         * Called when editor config values change.
         * @param key the config key that changed (e.g., "uiScale", "soundsEnabled")
         */
        void onConfigChanged(String key);
    }

    private static final List<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();

    // Cache previous values to detect actual changes
    @Nullable
    private static EditorClientConfig.EditorUiScale cachedUiScale = null;
    @Nullable
    private static Boolean cachedSoundsEnabled = null;
    @Nullable
    private static EditorClientConfig.EditorDefaultMode cachedDefaultMode = null;

    /**
     * Register a listener for config changes.
     * @param listener the listener to add
     */
    public static void addConfigChangeListener(ConfigChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a previously registered listener.
     * @param listener the listener to remove
     */
    public static void removeConfigChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notify all listeners of a config change.
     * @param key the config key that changed
     */
    private static void notifyListeners(String key) {
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onConfigChanged(key);
            } catch (Exception e) {
                DevMod.LOGGER.warn("[EditorConfig] Listener error for key '{}': {}", key, e.getMessage());
            }
        }
    }

    /**
     * Called when NeoForge config is reloaded.
     * Checks for actual value changes and notifies listeners.
     */
    public static void onConfigReload() {
        DevMod.LOGGER.debug("[EditorConfig] Config reload triggered");

        try {
            // Check UI Scale
            EditorClientConfig.EditorUiScale newUiScale = EditorClientConfig.EDITOR_UI_SCALE.get();
            if (cachedUiScale != newUiScale) {
                cachedUiScale = newUiScale;
                DevMod.LOGGER.info("[EditorConfig] UI Scale changed to: {}", newUiScale);
                notifyListeners("uiScale");
            }

            // Check Sounds Enabled
            Boolean newSoundsEnabled = EditorClientConfig.EDITOR_SOUNDS_ENABLED.get();
            if (!Objects.equals(cachedSoundsEnabled, newSoundsEnabled)) {
                cachedSoundsEnabled = newSoundsEnabled;
                DevMod.LOGGER.info("[EditorConfig] Sounds enabled changed to: {}", newSoundsEnabled);
                notifyListeners("soundsEnabled");
            }

            // Check Default Mode
            EditorClientConfig.EditorDefaultMode newDefaultMode = EditorClientConfig.EDITOR_DEFAULT_MODE.get();
            if (cachedDefaultMode != newDefaultMode) {
                cachedDefaultMode = newDefaultMode;
                DevMod.LOGGER.info("[EditorConfig] Default mode changed to: {}", newDefaultMode);
                notifyListeners("defaultMode");
            }

        } catch (Exception e) {
            DevMod.LOGGER.warn("[EditorConfig] Error during config reload check: {}", e.getMessage());
        }
    }

    /**
     * Initialize cached values from current config.
     * Call this after config is first loaded.
     */
    public static void initCache() {
        try {
            cachedUiScale = EditorClientConfig.EDITOR_UI_SCALE.get();
            cachedSoundsEnabled = EditorClientConfig.EDITOR_SOUNDS_ENABLED.get();
            cachedDefaultMode = EditorClientConfig.EDITOR_DEFAULT_MODE.get();
            DevMod.LOGGER.debug("[EditorConfig] Cache initialized - scale={}, sounds={}, mode={}",
                cachedUiScale, cachedSoundsEnabled, cachedDefaultMode);
        } catch (Exception e) {
            DevMod.LOGGER.warn("[EditorConfig] Could not initialize cache: {}", e.getMessage());
        }
    }

    // Fallback properties (used when config not yet loaded)
    private static final String UI_SCALE_PROP = "devmod.editor.uiScale";
    private static final String UI_SCALE_ENV = "DEVMOD_EDITOR_UISCALE";

    /**
     * Get UI scale setting.
     * Priority: NeoForge config > System property > Environment variable > "auto"
     * @return scale value: "auto", "1.0", "1.25", "1.5", "2.0"
     */
    public static String getUiScaleSetting() {
        // Try NeoForge config first
        try {
            EditorClientConfig.EditorUiScale scale = EditorClientConfig.EDITOR_UI_SCALE.get();
            if (scale != null) {
                return scale.getValue();
            }
        } catch (Exception ignored) {
            // Config may not be loaded yet during early init
        }

        // Fallback to system property
        String sys = System.getProperty(UI_SCALE_PROP);
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }

        // Fallback to environment variable
        String env = System.getenv(UI_SCALE_ENV);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        return "auto";
    }

    /**
     * Get UI scale as float value.
     * @return scale float, or -1 for AUTO
     */
    public static float getUiScaleFloat() {
        try {
            EditorClientConfig.EditorUiScale scale = EditorClientConfig.EDITOR_UI_SCALE.get();
            if (scale != null) {
                return scale.getScaleFloat();
            }
        } catch (Exception ignored) {
            // Config may not be loaded yet
        }

        // Parse from string fallback
        String setting = getUiScaleSetting();
        if ("auto".equalsIgnoreCase(setting)) {
            return -1f;
        }
        try {
            return Float.parseFloat(setting);
        } catch (NumberFormatException e) {
            return -1f; // Default to auto
        }
    }

    /**
     * Set UI scale in config.
     * @param scale the scale enum to set
     */
    public static void setUiScale(EditorClientConfig.EditorUiScale scale) {
        try {
            EditorClientConfig.EDITOR_UI_SCALE.set(Objects.requireNonNull(scale));
        } catch (Exception ignored) {
            // Config may be read-only in some contexts
        }
    }
}
