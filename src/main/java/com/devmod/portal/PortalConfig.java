package com.devmod.portal;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration settings for the Custom Portals module.
 * Provides configurable range, behavior, and redstone settings.
 *
 * <p>Configuration is loaded from devmod-portals.toml via NeoForge config system.
 * Default values match the original CustomPortals mod behavior.
 */
public final class PortalConfig {
    private PortalConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ========================================================================
    // Enums
    // ========================================================================

    /**
     * Haste mode for portals.
     * Controls when instant teleportation is applied.
     */
    public enum HasteMode {
        /** Always apply instant teleportation */
        TRUE,
        /** Never apply instant teleportation (use delay) */
        FALSE,
        /** Apply instant teleportation only for creative mode players */
        CREATIVE_ONLY
    }

    /**
     * Redstone interaction mode for portals.
     * Controls how portals respond to redstone signals.
     */
    public enum RedstoneMode {
        /** Portal is active when NO redstone signal is present */
        OFF,
        /** Portal is active when redstone signal IS present */
        ON,
        /** Portal ignores redstone signals (always active when linked) */
        IGNORE
    }

    // ========================================================================
    // Range Settings
    // ========================================================================

    private static final int DEFAULT_RANGE_VALUE = 100;
    private static final int RANGE_WITH_ENHANCER_VALUE = 1000;
    private static final int RANGE_WITH_STRONG_ENHANCER_VALUE = 10000;

    public static final ModConfigSpec.IntValue DEFAULT_RANGE_SPEC;
    public static final ModConfigSpec.IntValue RANGE_WITH_ENHANCER_SPEC;
    public static final ModConfigSpec.IntValue RANGE_WITH_STRONG_ENHANCER_SPEC;

    // ========================================================================
    // Behavior Settings
    // ========================================================================

    public static final ModConfigSpec.BooleanValue UNLIMITED_RANGE;
    public static final ModConfigSpec.BooleanValue ALWAYS_INTERDIMENSIONAL;
    public static final ModConfigSpec.EnumValue<HasteMode> ALWAYS_HASTE;
    public static final ModConfigSpec.BooleanValue PRIVATE_PORTALS;

    // ========================================================================
    // Redstone Settings
    // ========================================================================

    public static final ModConfigSpec.EnumValue<RedstoneMode> REDSTONE_MODE;

    static {
        BUILDER.push("range");

        DEFAULT_RANGE_SPEC = BUILDER
            .comment("Default portal linking range in blocks (no rune enhancers)")
            .defineInRange("defaultRange", DEFAULT_RANGE_VALUE, 1, 100000);

        RANGE_WITH_ENHANCER_SPEC = BUILDER
            .comment("Portal linking range when ENHANCER rune is present")
            .defineInRange("rangeWithEnhancer", RANGE_WITH_ENHANCER_VALUE, 1, 100000);

        RANGE_WITH_STRONG_ENHANCER_SPEC = BUILDER
            .comment("Portal linking range when STRONG_ENHANCER rune is present")
            .defineInRange("rangeWithStrongEnhancer", RANGE_WITH_STRONG_ENHANCER_VALUE, 1, 1000000);

        BUILDER.pop();

        BUILDER.push("behavior");

        UNLIMITED_RANGE = BUILDER
            .comment("If true, portals can link at any distance (ignores range settings)")
            .define("unlimitedRange", false);

        ALWAYS_INTERDIMENSIONAL = BUILDER
            .comment("If true, portals can always link across dimensions (no GATE rune required)")
            .define("alwaysInterdimensional", false);

        ALWAYS_HASTE = BUILDER
            .comment("Controls instant teleportation behavior",
                "TRUE = Always instant (no HASTE rune required)",
                "FALSE = Requires HASTE rune for instant teleport",
                "CREATIVE_ONLY = Instant for creative mode players only")
            .defineEnum("alwaysHaste", HasteMode.FALSE);

        PRIVATE_PORTALS = BUILDER
            .comment("If true, portals can only link to other portals created by the same player")
            .define("privatePortals", false);

        BUILDER.pop();

        BUILDER.push("redstone");

        REDSTONE_MODE = BUILDER
            .comment("Controls how portals respond to redstone signals",
                "OFF = Portal active when no redstone signal",
                "ON = Portal active when redstone signal present",
                "IGNORE = Portal ignores redstone (always active when linked)")
            .defineEnum("mode", RedstoneMode.IGNORE);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ========================================================================
    // Getter Methods (with fallback for unit tests)
    // ========================================================================

    /**
     * Returns the default portal linking range in blocks.
     */
    public static int getDefaultRange() {
        try {
            return DEFAULT_RANGE_SPEC.get();
        } catch (IllegalStateException e) {
            // Config not loaded yet (e.g., in unit tests)
            return DEFAULT_RANGE_VALUE;
        }
    }

    /**
     * Returns the portal linking range when ENHANCER rune is present.
     */
    public static int getRangeWithEnhancer() {
        try {
            return RANGE_WITH_ENHANCER_SPEC.get();
        } catch (IllegalStateException e) {
            return RANGE_WITH_ENHANCER_VALUE;
        }
    }

    /**
     * Returns the portal linking range when STRONG_ENHANCER rune is present.
     */
    public static int getRangeWithStrongEnhancer() {
        try {
            return RANGE_WITH_STRONG_ENHANCER_SPEC.get();
        } catch (IllegalStateException e) {
            return RANGE_WITH_STRONG_ENHANCER_VALUE;
        }
    }

    /**
     * Returns true if unlimited range is enabled.
     */
    public static boolean isUnlimitedRange() {
        try {
            return UNLIMITED_RANGE.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * Returns true if portals can always link across dimensions.
     */
    public static boolean isAlwaysInterdimensional() {
        try {
            return ALWAYS_INTERDIMENSIONAL.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * Returns the always haste mode setting.
     */
    public static HasteMode getAlwaysHasteMode() {
        try {
            return ALWAYS_HASTE.get();
        } catch (IllegalStateException e) {
            return HasteMode.FALSE;
        }
    }

    /**
     * Returns true if private portals mode is enabled.
     */
    public static boolean isPrivatePortals() {
        try {
            return PRIVATE_PORTALS.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * Returns the redstone interaction mode.
     */
    public static RedstoneMode getRedstoneMode() {
        try {
            return REDSTONE_MODE.get();
        } catch (IllegalStateException e) {
            return RedstoneMode.IGNORE;
        }
    }

    // ========================================================================
    // Utility Methods
    // ========================================================================

    /**
     * Calculates the effective portal linking range based on rune presence.
     * Priority: INFINITY > STRONG_ENHANCER > ENHANCER > default
     *
     * @param hasEnhancer true if ENHANCER rune is present
     * @param hasStrongEnhancer true if STRONG_ENHANCER rune is present
     * @param hasInfinity true if INFINITY rune is present
     * @return the effective range in blocks
     */
    public static int calculateEffectiveRange(boolean hasEnhancer, boolean hasStrongEnhancer, boolean hasInfinity) {
        // Check unlimited range config first
        if (isUnlimitedRange()) {
            return Integer.MAX_VALUE;
        }

        // INFINITY takes precedence
        if (hasInfinity) {
            return Integer.MAX_VALUE;
        }

        // STRONG_ENHANCER takes precedence over ENHANCER
        if (hasStrongEnhancer) {
            return getRangeWithStrongEnhancer();
        }

        // ENHANCER
        if (hasEnhancer) {
            return getRangeWithEnhancer();
        }

        // Default range
        return getDefaultRange();
    }
}
