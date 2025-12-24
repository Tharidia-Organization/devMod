package com.devmod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only configuration for Item Editor preferences.
 */
public final class EditorClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue EDITOR_SOUNDS_ENABLED;
    public static final ModConfigSpec.EnumValue<EditorDefaultMode> EDITOR_DEFAULT_MODE;
    public static final ModConfigSpec.EnumValue<EditorUiScale> EDITOR_UI_SCALE;
    public static final ModConfigSpec.BooleanValue EDITOR_WEAPON_HEURISTIC_ENABLED;
    public static final ModConfigSpec.DoubleValue EDITOR_WEAPON_MIN_CONFIDENCE;
    public static final ModConfigSpec.BooleanValue EDITOR_WEAPON_LOG_DETECTION;
    public static final ModConfigSpec.BooleanValue EDITOR_WEAPON_TREAT_PICKAXE;
    public static final ModConfigSpec.BooleanValue EDITOR_GRID_VALIDATION;

    public static final ModConfigSpec SPEC;

    /**
     * Default editor mode preference.
     */
    public enum EditorDefaultMode {
        PREVIEW,
        APPLY
    }

    /**
     * Editor UI scale options.
     * AUTO selects the largest scale that fits the screen with margin.
     */
    public enum EditorUiScale {
        AUTO("auto"),
        SCALE_1_0("1.0"),
        SCALE_1_25("1.25"),
        SCALE_1_5("1.5"),
        SCALE_2_0("2.0");

        private final String value;

        EditorUiScale(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public float getScaleFloat() {
            return switch (this) {
                case AUTO -> -1f; // Signal for auto-calculation
                case SCALE_1_0 -> 1.0f;
                case SCALE_1_25 -> 1.25f;
                case SCALE_1_5 -> 1.5f;
                case SCALE_2_0 -> 2.0f;
            };
        }
    }

    static {
        BUILDER.push("editor");

        EDITOR_SOUNDS_ENABLED = BUILDER
                .comment("Enable sound effects for editor interactions (button clicks, slider ticks, toggles)")
                .define("soundsEnabled", true);

        EDITOR_DEFAULT_MODE = BUILDER
                .comment("Default editor mode when opening the Item Editor",
                        "PREVIEW = safe client-only mode (recommended)",
                        "APPLY = persistent mode (requires confirmation if dirty)")
                .defineEnum("defaultMode", EditorDefaultMode.PREVIEW);

        EDITOR_UI_SCALE = BUILDER
                .comment("UI scale for the Item Editor",
                        "AUTO = automatically select largest scale that fits screen (recommended)",
                        "SCALE_1_0 = 1.0x (550x420px, best for 1080p)",
                        "SCALE_1_25 = 1.25x (688x525px)",
                        "SCALE_1_5 = 1.5x (825x630px, best for 1440p)",
                        "SCALE_2_0 = 2.0x (1100x840px, best for 4K)")
                .defineEnum("uiScale", EditorUiScale.AUTO);

        EDITOR_WEAPON_HEURISTIC_ENABLED = BUILDER
                .comment("Enable attribute/name heuristic detection for weapons when class/tag/whitelist are missing")
                .define("weaponDetectionHeuristic", true);

        EDITOR_WEAPON_MIN_CONFIDENCE = BUILDER
                .comment("Minimum confidence (0.0-1.0) to auto-open editor without low-confidence warning")
                .defineInRange("weaponDetectionMinConfidence", 0.8, 0.0, 1.0);

        EDITOR_WEAPON_LOG_DETECTION = BUILDER
                .comment("Log weapon detection results for debugging")
                .define("weaponDetectionLog", false);

        EDITOR_WEAPON_TREAT_PICKAXE = BUILDER
                .comment("Treat pickaxes as combat weapons in the editor (false = ignore pickaxes unless explicitly whitelisted)")
                .define("treatPickaxeAsWeapon", false);

        EDITOR_GRID_VALIDATION = BUILDER
                .comment("Enable 4px grid validation logs for editor layouts (dev/debug only)")
                .define("gridValidation", false);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private EditorClientConfig() {}
}
