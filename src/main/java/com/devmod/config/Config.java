package com.devmod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

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

    /**
     * Nexus hub tick behavior.
     */
    public enum NexusTickMode {
        /** Always tick the Nexus dimension. */
        ALWAYS,
        /** Tick only when players are present (current behavior). */
        PLAYERS_ONLY,
        /** Tick when players are present, otherwise at a slower interval. */
        THROTTLED
    }

    /**
     * Nexus hub rebuild policy.
     */
    public enum NexusRebuildPolicy {
        /** Always rebuild on server start (unless locked). */
        ALWAYS,
        /** Rebuild only when the hub version changes. */
        ON_VERSION_MISMATCH,
        /** Build once if missing, never rebuild automatically. */
        ONCE,
        /** Never rebuild automatically. */
        NEVER
    }

    /**
     * Nexus hub build scheduling.
     */
    public enum NexusBuildMode {
        /** Build the hub in a single pass (fast, can spike). */
        SYNC,
        /** Build the hub in staged steps across ticks. */
        STAGGERED
    }

    /**
     * Nexus hub spawn routing.
     */
    public enum NexusSpawnMode {
        /** Use the default shared spawn position. */
        DEFAULT,
        /** Round-robin spawn pads per player entry. */
        ROUND_ROBIN,
        /** Route to pads based on team/tag keywords. */
        BY_TEAM
    }

    /**
     * Nexus hub palette profile.
     */
    public enum NexusPaletteProfile {
        /** Full fidelity palette (glass, emissive). */
        DEFAULT,
        /** Lower GPU cost palette (more opaque blocks). */
        PERFORMANCE,
        /** Custom palette loaded from config/devmod/nexus_palette.json. */
        CUSTOM
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
    public static final ModConfigSpec.ConfigValue<String> IMPACT_DISPLAY_MODE_DEFAULT;

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

    // ============================================
    // NEXUS HUB SETTINGS
    // ============================================

    public static final ModConfigSpec.BooleanValue NEXUS_ENABLED;
    public static final ModConfigSpec.EnumValue<NexusRebuildPolicy> NEXUS_REBUILD_POLICY;
    public static final ModConfigSpec.EnumValue<NexusBuildMode> NEXUS_BUILD_MODE;
    public static final ModConfigSpec.IntValue NEXUS_BUILD_STEP_INTERVAL;
    public static final ModConfigSpec.EnumValue<NexusTickMode> NEXUS_TICK_MODE;
    public static final ModConfigSpec.IntValue NEXUS_IDLE_TICK_INTERVAL;
    public static final ModConfigSpec.BooleanValue NEXUS_KEEP_LOADED;
    public static final ModConfigSpec.IntValue NEXUS_FORCE_CHUNK_RADIUS;
    public static final ModConfigSpec.IntValue NEXUS_TICK_DISTANCE;
    public static final ModConfigSpec.IntValue NEXUS_WORLD_BORDER_SIZE;
    public static final ModConfigSpec.EnumValue<NexusSpawnMode> NEXUS_SPAWN_MODE;
    public static final ModConfigSpec.EnumValue<NexusPaletteProfile> NEXUS_PALETTE_PROFILE;
    public static final ModConfigSpec.BooleanValue NEXUS_ENTRY_MESSAGE;
    public static final ModConfigSpec.BooleanValue NEXUS_ENTRY_SOUND;
    public static final ModConfigSpec.BooleanValue NEXUS_ZONE_CUES;
    public static final ModConfigSpec.BooleanValue NEXUS_ZONE_HINTS;
    public static final ModConfigSpec.IntValue NEXUS_ZONE_CUE_COOLDOWN;
    public static final ModConfigSpec.BooleanValue NEXUS_DUMMY_FEEDBACK;
    public static final ModConfigSpec.IntValue NEXUS_DUMMY_FEEDBACK_COOLDOWN;
    public static final ModConfigSpec.BooleanValue NEXUS_ALLOW_BUILD_ANYWHERE;
    public static final ModConfigSpec.BooleanValue NEXUS_RESTRICT_BUILDING;
    public static final ModConfigSpec.BooleanValue NEXUS_HIDE_MISSING_MOD_PODS;
    public static final ModConfigSpec.BooleanValue NEXUS_DYNAMIC_MOD_PODS;
    public static final ModConfigSpec.ConfigValue<String> NEXUS_LAYOUT_OVERLAY_TEMPLATE;
    public static final ModConfigSpec.BooleanValue NEXUS_AMBIENT_EFFECTS;
    public static final ModConfigSpec.IntValue NEXUS_AMBIENT_PARTICLE_INTERVAL;
    public static final ModConfigSpec.BooleanValue NEXUS_AVATAR_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> NEXUS_AVATAR_NAME;
    public static final ModConfigSpec.ConfigValue<String> NEXUS_AVATAR_SKIN;
    public static final ModConfigSpec.BooleanValue NEXUS_AVATAR_GLOW;
    public static final ModConfigSpec.IntValue NEXUS_AVATAR_FLOAT_OFFSET;
    public static final ModConfigSpec.BooleanValue NEXUS_AVATAR_FLOAT_ENABLED;
    public static final ModConfigSpec.DoubleValue NEXUS_AVATAR_FLOAT_AMPLITUDE;
    public static final ModConfigSpec.DoubleValue NEXUS_AVATAR_FLOAT_SPEED;
    public static final ModConfigSpec.BooleanValue NEXUS_AVATAR_PARTICLES_ENABLED;
    public static final ModConfigSpec.IntValue NEXUS_AVATAR_PARTICLE_INTERVAL;
    public static final ModConfigSpec.BooleanValue NEXUS_AVATAR_ROTATE;
    public static final ModConfigSpec.DoubleValue NEXUS_AVATAR_ROTATE_SPEED;

    // Portal Network settings
    public static final ModConfigSpec.BooleanValue NEXUS_PORTALS_ENABLED;
    public static final ModConfigSpec.IntValue NEXUS_PORTAL_PARTICLE_INTERVAL;

    // Hologram Display settings
    public static final ModConfigSpec.BooleanValue NEXUS_HOLOGRAMS_ENABLED;
    public static final ModConfigSpec.IntValue NEXUS_HOLOGRAM_UPDATE_INTERVAL;

    // Performance settings
    public static final ModConfigSpec.BooleanValue NEXUS_PERFORMANCE_ENABLED;
    public static final ModConfigSpec.BooleanValue NEXUS_LAZY_CHUNKS_ENABLED;
    public static final ModConfigSpec.BooleanValue NEXUS_ENTITY_CULLING_ENABLED;
    public static final ModConfigSpec.IntValue NEXUS_CULL_DISTANCE;

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

        IMPACT_DISPLAY_MODE_DEFAULT = BUILDER
                .comment("Default display mode for Impact HUD (OFF, MINIMAL, DETAILED, ANALYSIS)")
                .define("impactDisplayModeDefault", "DETAILED");

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

        // ============================================
        // NEXUS HUB SETTINGS
        // ============================================

        BUILDER.push("nexus");

        NEXUS_ENABLED = BUILDER
                .comment("Enable the Nexus control dimension")
                .define("enabled", true);

        NEXUS_REBUILD_POLICY = BUILDER
                .comment("Automatic hub rebuild policy",
                        "ALWAYS = rebuild every server start (unless locked)",
                        "ON_VERSION_MISMATCH = rebuild only when hub version changes",
                        "ONCE = build once if missing, never rebuild automatically",
                        "NEVER = never rebuild automatically")
                .defineEnum("rebuildPolicy", NexusRebuildPolicy.ON_VERSION_MISMATCH);

        NEXUS_BUILD_MODE = BUILDER
                .comment("Hub build mode: SYNC (single pass) or STAGGERED (multi-step across ticks)")
                .defineEnum("buildMode", NexusBuildMode.STAGGERED);

        NEXUS_BUILD_STEP_INTERVAL = BUILDER
                .comment("Ticks to wait between staged build steps (STAGGERED mode)")
                .defineInRange("buildStepInterval", 2, 1, 100);

        NEXUS_TICK_MODE = BUILDER
                .comment("Nexus tick behavior: ALWAYS, PLAYERS_ONLY, THROTTLED")
                .defineEnum("tickMode", NexusTickMode.THROTTLED);

        NEXUS_IDLE_TICK_INTERVAL = BUILDER
                .comment("Ticks between Nexus ticks when empty (THROTTLED mode)")
                .defineInRange("idleTickInterval", 40, 1, 400);

        NEXUS_KEEP_LOADED = BUILDER
                .comment("Force-load Nexus chunks to keep the hub active")
                .define("keepLoaded", true);

        NEXUS_FORCE_CHUNK_RADIUS = BUILDER
                .comment("Chunk radius to force-load around Nexus center")
                .defineInRange("forceChunkRadius", 6, 1, 16);

        NEXUS_TICK_DISTANCE = BUILDER
                .comment("Ticket radius for entity ticking when force-loading")
                .defineInRange("tickDistance", 3, 1, 10);

        NEXUS_WORLD_BORDER_SIZE = BUILDER
                .comment("World border diameter for the Nexus dimension")
                .defineInRange("worldBorderSize", 256, 64, 512);

        NEXUS_SPAWN_MODE = BUILDER
                .comment("Spawn routing for Nexus: DEFAULT, ROUND_ROBIN, BY_TEAM")
                .defineEnum("spawnMode", NexusSpawnMode.ROUND_ROBIN);

        NEXUS_PALETTE_PROFILE = BUILDER
                .comment("Palette profile for hub materials: DEFAULT, PERFORMANCE, CUSTOM")
                .defineEnum("paletteProfile", NexusPaletteProfile.DEFAULT);

        NEXUS_ENTRY_MESSAGE = BUILDER
                .comment("Show a short onboarding message when entering the Nexus")
                .define("entryMessage", true);

        NEXUS_ENTRY_SOUND = BUILDER
                .comment("Play a short sound cue when entering the Nexus")
                .define("entrySound", true);

        NEXUS_ZONE_CUES = BUILDER
                .comment("Play zone-specific sound cues when entering Nexus areas")
                .define("zoneCues", true);

        NEXUS_ZONE_HINTS = BUILDER
                .comment("Show short zone hints in the action bar when entering Nexus areas")
                .define("zoneHints", true);

        NEXUS_ZONE_CUE_COOLDOWN = BUILDER
                .comment("Ticks between zone cue repeats (prevents rapid flicker)")
                .defineInRange("zoneCueCooldown", 20, 0, 200);

        NEXUS_DUMMY_FEEDBACK = BUILDER
                .comment("Show combat feedback when hitting Nexus training dummies")
                .define("dummyFeedback", true);

        NEXUS_DUMMY_FEEDBACK_COOLDOWN = BUILDER
                .comment("Ticks between dummy feedback updates per player")
                .defineInRange("dummyFeedbackCooldown", 4, 0, 40);

        NEXUS_ALLOW_BUILD_ANYWHERE = BUILDER
                .comment("Allow block break/place anywhere in the Nexus (recommended for testing)")
                .define("allowBuildAnywhere", true);

        NEXUS_RESTRICT_BUILDING = BUILDER
                .comment("Restrict block break/place to the Sandbox test zone in Nexus when allowBuildAnywhere is false")
                .define("restrictBuilding", true);

        NEXUS_HIDE_MISSING_MOD_PODS = BUILDER
                .comment("Hide integration pods for missing mods instead of showing offline placeholders")
                .define("hideMissingModPods", false);

        NEXUS_DYNAMIC_MOD_PODS = BUILDER
                .comment("Append generic pods for active mods beyond the curated list")
                .define("dynamicModPods", true);

        NEXUS_LAYOUT_OVERLAY_TEMPLATE = BUILDER
                .comment("Optional structure overlay for Nexus layout (data pack structure id)")
                .define("layoutOverlayTemplate", "");

        NEXUS_AMBIENT_EFFECTS = BUILDER
                .comment("Enable ambient particles in the Nexus hub")
                .define("ambientEffects", true);

        NEXUS_AMBIENT_PARTICLE_INTERVAL = BUILDER
                .comment("Ticks between ambient particle bursts in the Nexus")
                .defineInRange("ambientParticleInterval", 12, 2, 100);

        NEXUS_AVATAR_ENABLED = BUILDER
                .comment("Spawn the Nexus AI avatar in the hub")
                .define("avatarEnabled", true);

        NEXUS_AVATAR_NAME = BUILDER
                .comment("Display name for the Nexus AI avatar")
                .define("avatarName", "NEXA");

        NEXUS_AVATAR_SKIN = BUILDER
                .comment("Avatar skin reference: player:<name> or texture:<base64>")
                .define("avatarSkin", "texture:e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2ZkZWUyMDE1MGM3YmY2NDdmYjA3ZmM4MjRiNzc5ZGRhNDA4YTEyYmE2NGQ2ZTA3NDhjNzA3ZjMyMjMxY2FhZSJ9fX0=");

        NEXUS_AVATAR_GLOW = BUILDER
                .comment("Enable glow effect on the Nexus AI avatar")
                .define("avatarGlow", true);

        NEXUS_AVATAR_FLOAT_OFFSET = BUILDER
                .comment("Vertical offset above the Nexus avatar projector")
                .defineInRange("avatarFloatOffset", 2, 0, 12);

        NEXUS_AVATAR_FLOAT_ENABLED = BUILDER
                .comment("Enable floating animation for the avatar")
                .define("avatarFloatEnabled", true);

        NEXUS_AVATAR_FLOAT_AMPLITUDE = BUILDER
                .comment("Amplitude of floating animation in blocks")
                .defineInRange("avatarFloatAmplitude", 0.3, 0.0, 2.0);

        NEXUS_AVATAR_FLOAT_SPEED = BUILDER
                .comment("Speed of floating animation (radians per tick)")
                .defineInRange("avatarFloatSpeed", 0.05, 0.01, 0.5);

        NEXUS_AVATAR_PARTICLES_ENABLED = BUILDER
                .comment("Enable ambient particles around the avatar")
                .define("avatarParticlesEnabled", true);

        NEXUS_AVATAR_PARTICLE_INTERVAL = BUILDER
                .comment("Ticks between particle bursts")
                .defineInRange("avatarParticleInterval", 10, 1, 100);

        NEXUS_AVATAR_ROTATE = BUILDER
                .comment("Enable slow rotation of the avatar")
                .define("avatarRotate", true);

        NEXUS_AVATAR_ROTATE_SPEED = BUILDER
                .comment("Rotation speed in degrees per tick")
                .defineInRange("avatarRotateSpeed", 0.5, 0.0, 5.0);

        // Portal Network
        NEXUS_PORTALS_ENABLED = BUILDER
                .comment("Enable portal pedestals for zone navigation")
                .define("portalsEnabled", true);

        NEXUS_PORTAL_PARTICLE_INTERVAL = BUILDER
                .comment("Ticks between portal particle effects")
                .defineInRange("portalParticleInterval", 5, 1, 60);

        // Hologram Displays
        NEXUS_HOLOGRAMS_ENABLED = BUILDER
                .comment("Enable holographic displays (leaderboards, announcements)")
                .define("hologramsEnabled", true);

        NEXUS_HOLOGRAM_UPDATE_INTERVAL = BUILDER
                .comment("Ticks between hologram content updates")
                .defineInRange("hologramUpdateInterval", 600, 20, 6000);

        // Performance Optimizations
        NEXUS_PERFORMANCE_ENABLED = BUILDER
                .comment("Enable Nexus performance optimizations")
                .define("performanceEnabled", true);

        NEXUS_LAZY_CHUNKS_ENABLED = BUILDER
                .comment("Enable lazy chunk loading based on player zones")
                .define("lazyChunksEnabled", false);

        NEXUS_ENTITY_CULLING_ENABLED = BUILDER
                .comment("Enable entity culling for distant entities")
                .define("entityCullingEnabled", true);

        NEXUS_CULL_DISTANCE = BUILDER
                .comment("Distance in blocks beyond which entities are culled")
                .defineInRange("cullDistance", 64, 16, 256);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
