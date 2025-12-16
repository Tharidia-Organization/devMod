package com.frenkvs.devmod.ui.editor.core;

/**
 * Minimal editor config bridge for UI scale and related toggles.
 * In absence of a full config system, reads from system properties / env.
 */
public final class EditorConfig {

    private EditorConfig() {}

    private static final String UI_SCALE_PROP = "devmod.editor.uiScale";
    private static final String UI_SCALE_ENV = "DEVMOD_EDITOR_UISCALE";

    /**
     * Get UI scale setting.
     * Accepted values: "auto", "1.0", "1.25", "1.5", "2.0".
     */
    public static String getUiScaleSetting() {
        String sys = System.getProperty(UI_SCALE_PROP);
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }
        String env = System.getenv(UI_SCALE_ENV);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return "auto";
    }
}
