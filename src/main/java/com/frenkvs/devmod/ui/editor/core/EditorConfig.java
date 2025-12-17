package com.frenkvs.devmod.ui.editor.core;

import com.frenkvs.devmod.EditorClientConfig;

import java.util.Objects;

/**
 * Editor config bridge for UI scale and related toggles.
 * Uses NeoForge config system with fallback to system properties / env.
 */
public final class EditorConfig {

    private EditorConfig() {}

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
