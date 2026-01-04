package com.devmod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side configuration for the Wave Intelligence System (WIS).
 * Persists user preferences across game sessions.
 */
public final class WISClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ========== HUD Settings ==========

    public static final ModConfigSpec.EnumValue<HudPosition> HUD_POSITION;
    public static final ModConfigSpec.DoubleValue HUD_OPACITY;
    public static final ModConfigSpec.BooleanValue HUD_EXTENDED_STATS;

    // ========== Briefing Settings ==========

    public static final ModConfigSpec.IntValue BRIEFING_MOB_LIST_LIMIT;
    public static final ModConfigSpec.BooleanValue AUTO_SHOW_DEBRIEF;

    // ========== System Settings ==========

    public static final ModConfigSpec.BooleanValue WIS_ENABLED;

    public static final ModConfigSpec SPEC;

    /**
     * HUD position options for MinimalCombatHUD.
     */
    public enum HudPosition {
        TOP_LEFT("Top Left"),
        TOP_RIGHT("Top Right"),
        BOTTOM_LEFT("Bottom Left"),
        BOTTOM_RIGHT("Bottom Right");

        private final String displayName;

        HudPosition(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    static {
        BUILDER.push("wis");

        BUILDER.comment("HUD Settings").push("hud");

        HUD_POSITION = BUILDER
            .comment("Position of the minimal combat HUD during waves",
                     "TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT")
            .defineEnum("position", HudPosition.TOP_RIGHT);

        HUD_OPACITY = BUILDER
            .comment("Opacity of the minimal combat HUD (0.0 = invisible, 1.0 = fully opaque)")
            .defineInRange("opacity", 0.2, 0.0, 1.0);

        HUD_EXTENDED_STATS = BUILDER
            .comment("Show extended stats panel during combat (DMG, Crits, TTK)")
            .define("extendedStats", false);

        BUILDER.pop();

        BUILDER.comment("Briefing Settings").push("briefing");

        BRIEFING_MOB_LIST_LIMIT = BUILDER
            .comment("Maximum number of mob types to show in pre-wave briefing (1-20)")
            .defineInRange("mobListLimit", 5, 1, 20);

        AUTO_SHOW_DEBRIEF = BUILDER
            .comment("Automatically show debrief screen after each wave")
            .define("autoShowDebrief", true);

        BUILDER.pop();

        BUILDER.comment("System Settings").push("system");

        WIS_ENABLED = BUILDER
            .comment("Enable the Wave Intelligence System (disable to hide all WIS UI)")
            .define("enabled", true);

        BUILDER.pop();

        BUILDER.pop(); // wis

        SPEC = BUILDER.build();
    }

    private WISClientConfig() {}
}
