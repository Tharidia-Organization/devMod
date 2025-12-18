package com.frenkvs.devmod;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configurazione centralizzata per DevMod.
 *
 * Categorie:
 * - Telemetry: Controllo logging e tracking
 * - Combat: Body part detection e damage calculation
 * - Debug: Overlay e visualizzazioni
 * - Performance: Cache e ottimizzazioni
 *
 * Nota: le preferenze dell'Item Editor sono in {@link EditorClientConfig}.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * Armor penetration formula types.
     * Designers can choose which formula fits their PvP/PvE balance.
     */
    public enum ArmorPenFormula {
        /** Simple: armorPen * armorValue * multiplier (current behavior) */
        SIMPLE,
        /** Minecraft-accurate: Uses vanilla armor reduction formula */
        VANILLA_ACCURATE,
        /** Percentage: Directly reduces armor value by percentage before damage calc */
        PERCENTAGE,
        /** Flat: Adds flat true damage bonus regardless of armor */
        FLAT_BONUS
    }

    /**
     * Impact HUD position on screen.
     * BUG-008 FIX: Allow users to configure where the HUD appears.
     */
    public enum HudPosition {
        /** Top-right corner (default, classic position) */
        TOP_RIGHT,
        /** Top-left corner */
        TOP_LEFT,
        /** Bottom-right corner */
        BOTTOM_RIGHT,
        /** Bottom-left corner */
        BOTTOM_LEFT,
        /** Center-right (middle of screen, right side) */
        CENTER_RIGHT,
        /** Center-left (middle of screen, left side) */
        CENTER_LEFT
    }

    // ============================================
    // TELEMETRY SETTINGS
    // ============================================

    public static final ModConfigSpec.BooleanValue TELEMETRY_ENABLED;
    public static final ModConfigSpec.BooleanValue TELEMETRY_HITS_ENABLED;
    public static final ModConfigSpec.BooleanValue TELEMETRY_DEATHS_ENABLED;
    public static final ModConfigSpec.BooleanValue TELEMETRY_SPAWNS_ENABLED;
    public static final ModConfigSpec.IntValue TELEMETRY_TICK_INTERVAL;

    // ============================================
    // COMBAT SETTINGS
    // ============================================

    public static final ModConfigSpec.BooleanValue BODY_PART_DETECTION_ENABLED;
    public static final ModConfigSpec.BooleanValue OBB_HITBOX_ENABLED;
    public static final ModConfigSpec.BooleanValue OBB_DEBUG_AXES;
    public static final ModConfigSpec.DoubleValue HEAD_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BODY_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARMS_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LEGS_DAMAGE_MULTIPLIER;

    // Armor Penetration Formula Settings
    public static final ModConfigSpec.EnumValue<ArmorPenFormula> ARMOR_PEN_FORMULA;
    public static final ModConfigSpec.DoubleValue ARMOR_PEN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ARMOR_PEN_FLAT_BONUS;

    // ============================================
    // DEBUG OVERLAY SETTINGS
    // ============================================

    public static final ModConfigSpec.BooleanValue DEBUG_OVERLAY_ENABLED;
    public static final ModConfigSpec.BooleanValue IMPACT_HUD_ENABLED;
    public static final ModConfigSpec.EnumValue<HudPosition> IMPACT_HUD_POSITION;
    public static final ModConfigSpec.IntValue IMPACT_HUD_OFFSET_X;
    public static final ModConfigSpec.IntValue IMPACT_HUD_OFFSET_Y;
    public static final ModConfigSpec.BooleanValue IMPACT_HUD_HISTORY_ENABLED;
    public static final ModConfigSpec.BooleanValue IMPACT_HUD_DPS_ENABLED;
    public static final ModConfigSpec.IntValue IMPACT_HUD_HISTORY_COUNT;
    public static final ModConfigSpec.BooleanValue SHOW_BODY_PART_BOXES;
    public static final ModConfigSpec.IntValue IMPACT_VFX_DURATION_MS;
    public static final ModConfigSpec.BooleanValue IMPACT_VFX_ENABLED;
    public static final ModConfigSpec.BooleanValue IMPACT_VFX_VORTEX_ENABLED;
    public static final ModConfigSpec.BooleanValue IMPACT_VFX_SLASH_ENABLED;
    public static final ModConfigSpec.BooleanValue IMPACT_VFX_LINES_ENABLED;
    public static final ModConfigSpec.DoubleValue IMPACT_VFX_INTENSITY;

    // ============================================
    // PERFORMANCE SETTINGS
    // ============================================

    public static final ModConfigSpec.IntValue BODY_PART_CACHE_TTL_MS;
    public static final ModConfigSpec.IntValue BODY_PART_CACHE_MAX_SIZE;
    public static final ModConfigSpec.IntValue MOB_SEARCH_RADIUS;

    // ============================================
    // VISUAL EFFECTS SETTINGS (Perception-style)
    // ============================================

    public static final ModConfigSpec.BooleanValue SCREEN_SHAKE_ENABLED;
    public static final ModConfigSpec.DoubleValue SCREEN_SHAKE_INTENSITY;
    public static final ModConfigSpec.BooleanValue PROJECTILE_TRAILS_ENABLED;
    public static final ModConfigSpec.DoubleValue PROJECTILE_TRAILS_INTENSITY;

    // ============================================
    // BADGE POPUP SETTINGS
    // ============================================

    public static final ModConfigSpec.BooleanValue BADGE_POPUP_ENABLED;
    public static final ModConfigSpec.IntValue BADGE_POPUP_DURATION_MS;
    public static final ModConfigSpec.IntValue BADGE_POPUP_SLIDE_IN_MS;
    public static final ModConfigSpec.IntValue BADGE_POPUP_FADE_OUT_MS;
    public static final ModConfigSpec.IntValue BADGE_POPUP_Y_POSITION;
    public static final ModConfigSpec.DoubleValue BADGE_POPUP_SOUND_VOLUME;
    public static final ModConfigSpec.BooleanValue BADGE_POPUP_PARTICLES_ENABLED;
    public static final ModConfigSpec.BooleanValue BADGE_POPUP_GLOW_ENABLED;
    public static final ModConfigSpec.BooleanValue BADGE_POPUP_SOUND_ENABLED;

    static {
        BUILDER.push("telemetry");

        TELEMETRY_ENABLED = BUILDER
                .comment("Enable telemetry logging to NDJSON files")
                .define("enabled", true);

        TELEMETRY_HITS_ENABLED = BUILDER
                .comment("Log hit events (damage dealt)")
                .define("logHits", true);

        TELEMETRY_DEATHS_ENABLED = BUILDER
                .comment("Log death events")
                .define("logDeaths", true);

        TELEMETRY_SPAWNS_ENABLED = BUILDER
                .comment("Log entity spawn events")
                .define("logSpawns", true);

        TELEMETRY_TICK_INTERVAL = BUILDER
                .comment("Telemetry tick interval (1 = every tick, 20 = every second)")
                .defineInRange("tickInterval", 20, 1, 100);

        BUILDER.pop();

        BUILDER.push("combat");

        BODY_PART_DETECTION_ENABLED = BUILDER
                .comment("Enable body part detection for damage calculation")
                .define("bodyPartDetection", true);

        OBB_HITBOX_ENABLED = BUILDER
                .comment("Enable OBB (Oriented Bounding Box) hitbox system for rotation-aware body part detection",
                        "When enabled, hitboxes follow model rotations (arms, head, etc.)",
                        "When disabled, uses static AABB subdivision (faster but less accurate)")
                .define("obbHitboxEnabled", true);

        OBB_DEBUG_AXES = BUILDER
                .comment("Show OBB rotation axes in debug overlay (requires obbHitboxEnabled and showBodyPartBoxes)")
                .define("obbDebugAxes", false);

        HEAD_DAMAGE_MULTIPLIER = BUILDER
                .comment("Default head damage multiplier")
                .defineInRange("headMultiplier", 1.5, 0.1, 10.0);

        BODY_DAMAGE_MULTIPLIER = BUILDER
                .comment("Default body/torso damage multiplier")
                .defineInRange("bodyMultiplier", 1.0, 0.1, 10.0);

        ARMS_DAMAGE_MULTIPLIER = BUILDER
                .comment("Default arms damage multiplier")
                .defineInRange("armsMultiplier", 0.8, 0.1, 10.0);

        LEGS_DAMAGE_MULTIPLIER = BUILDER
                .comment("Default legs damage multiplier")
                .defineInRange("legsMultiplier", 0.7, 0.1, 10.0);

        ARMOR_PEN_FORMULA = BUILDER
                .comment("Armor penetration formula type:",
                        "SIMPLE - armorPen * armorValue * multiplier (default, current behavior)",
                        "VANILLA_ACCURATE - Uses Minecraft's armor reduction formula for accurate penetration",
                        "PERCENTAGE - Directly reduces effective armor by percentage before damage calc",
                        "FLAT_BONUS - Adds flat true damage bonus regardless of target armor")
                .defineEnum("armorPenFormula", ArmorPenFormula.SIMPLE);

        ARMOR_PEN_MULTIPLIER = BUILDER
                .comment("Multiplier applied to armor penetration bonus (default 0.5 for SIMPLE formula)",
                        "Higher = more bonus damage from armor pen, Lower = less impactful")
                .defineInRange("armorPenMultiplier", 0.5, 0.0, 5.0);

        ARMOR_PEN_FLAT_BONUS = BUILDER
                .comment("Base flat damage bonus when using FLAT_BONUS formula (added per 100% armor pen)")
                .defineInRange("armorPenFlatBonus", 2.0, 0.0, 20.0);

        BUILDER.pop();

        BUILDER.push("debug");

        DEBUG_OVERLAY_ENABLED = BUILDER
                .comment("Enable debug overlay rendering (press G to toggle)")
                .define("overlayEnabled", false);

        IMPACT_HUD_ENABLED = BUILDER
                .comment("Enable impact analysis HUD")
                .define("impactHudEnabled", true);

        IMPACT_HUD_POSITION = BUILDER
                .comment("Position of the Impact HUD on screen (TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT, CENTER_RIGHT, CENTER_LEFT)")
                .defineEnum("impactHudPosition", HudPosition.TOP_RIGHT);

        IMPACT_HUD_OFFSET_X = BUILDER
                .comment("Horizontal offset from screen edge (pixels)")
                .defineInRange("impactHudOffsetX", 10, 0, 200);

        IMPACT_HUD_OFFSET_Y = BUILDER
                .comment("Vertical offset from screen edge (pixels)")
                .defineInRange("impactHudOffsetY", 10, 0, 200);

        IMPACT_HUD_HISTORY_ENABLED = BUILDER
                .comment("Show recent impacts panel in the Impact HUD")
                .define("impactHudHistoryEnabled", true);

        IMPACT_HUD_DPS_ENABLED = BUILDER
                .comment("Show rolling DPS in the Impact HUD history panel")
                .define("impactHudDpsEnabled", true);

        IMPACT_HUD_HISTORY_COUNT = BUILDER
                .comment("Number of recent impacts to show in the Impact HUD")
                .defineInRange("impactHudHistoryCount", 5, 1, 10);

        SHOW_BODY_PART_BOXES = BUILDER
                .comment("Show body part bounding boxes when debug overlay is enabled (Shift+G to toggle)")
                .define("showBodyPartBoxes", false);

        IMPACT_VFX_DURATION_MS = BUILDER
                .comment("Duration of impact VFX in milliseconds")
                .defineInRange("impactVfxDuration", 500, 100, 5000);

        IMPACT_VFX_ENABLED = BUILDER
                .comment("Enable impact VFX rendering")
                .define("impactVfxEnabled", true);

        IMPACT_VFX_VORTEX_ENABLED = BUILDER
                .comment("Enable impact vortex VFX")
                .define("impactVfxVortexEnabled", true);

        IMPACT_VFX_SLASH_ENABLED = BUILDER
                .comment("Enable impact slash VFX")
                .define("impactVfxSlashEnabled", true);

        IMPACT_VFX_LINES_ENABLED = BUILDER
                .comment("Enable impact connection lines VFX")
                .define("impactVfxLinesEnabled", true);

        IMPACT_VFX_INTENSITY = BUILDER
                .comment("Impact VFX intensity multiplier (0.1 = subtle, 1.0 = normal, 2.0 = intense)")
                .defineInRange("impactVfxIntensity", 1.0, 0.1, 2.0);

        BUILDER.pop();

        BUILDER.push("performance");

        BODY_PART_CACHE_TTL_MS = BUILDER
                .comment("Body part cache TTL in milliseconds (higher = better performance, lower = more accurate)")
                .defineInRange("cacheTtl", 100, 50, 1000);

        BODY_PART_CACHE_MAX_SIZE = BUILDER
                .comment("Maximum number of cached body part calculations")
                .defineInRange("cacheMaxSize", 1000, 100, 10000);

        MOB_SEARCH_RADIUS = BUILDER
                .comment("Radius in blocks to search for mobs when applying global stats")
                .defineInRange("mobSearchRadius", 128, 32, 512);

        BUILDER.pop();

        // ============================================
        // VISUAL EFFECTS SETTINGS (Perception-style)
        // ============================================

        BUILDER.push("effects");

        SCREEN_SHAKE_ENABLED = BUILDER
                .comment("Enable screen shake effects when taking damage")
                .define("screenShakeEnabled", true);

        SCREEN_SHAKE_INTENSITY = BUILDER
                .comment("Screen shake intensity multiplier (0.0 = disabled, 1.0 = normal, 2.0 = intense)")
                .defineInRange("screenShakeIntensity", 1.0, 0.0, 2.0);

        PROJECTILE_TRAILS_ENABLED = BUILDER
                .comment("Enable glowing trails behind projectiles (arrows, fireballs, etc)")
                .define("projectileTrailsEnabled", false);

        PROJECTILE_TRAILS_INTENSITY = BUILDER
                .comment("Projectile trail intensity/opacity (0.0 = invisible, 1.0 = bright)")
                .defineInRange("projectileTrailsIntensity", 1.0, 0.0, 2.0);

        BUILDER.pop();

        // ============================================
        // BADGE POPUP SETTINGS
        // ============================================

        BUILDER.push("badgePopup");

        BADGE_POPUP_ENABLED = BUILDER
                .comment("Enable badge unlock popup notifications")
                .define("enabled", true);

        BADGE_POPUP_DURATION_MS = BUILDER
                .comment("Total popup display duration in milliseconds")
                .defineInRange("durationMs", 5000, 1000, 15000);

        BADGE_POPUP_SLIDE_IN_MS = BUILDER
                .comment("Slide-in animation duration in milliseconds")
                .defineInRange("slideInMs", 400, 100, 1000);

        BADGE_POPUP_FADE_OUT_MS = BUILDER
                .comment("Fade-out animation duration in milliseconds")
                .defineInRange("fadeOutMs", 600, 100, 2000);

        BADGE_POPUP_Y_POSITION = BUILDER
                .comment("Vertical position from top of screen in pixels")
                .defineInRange("yPosition", 12, 0, 200);

        BADGE_POPUP_SOUND_ENABLED = BUILDER
                .comment("Enable sound effects when badge is unlocked")
                .define("soundEnabled", true);

        BADGE_POPUP_SOUND_VOLUME = BUILDER
                .comment("Sound volume multiplier (0.0 = muted, 1.0 = full volume)")
                .defineInRange("soundVolume", 1.0, 0.0, 1.0);

        BADGE_POPUP_PARTICLES_ENABLED = BUILDER
                .comment("Enable particle effects for EPIC and LEGENDARY badges")
                .define("particlesEnabled", true);

        BADGE_POPUP_GLOW_ENABLED = BUILDER
                .comment("Enable glow effect for RARE+ badges")
                .define("glowEnabled", true);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
