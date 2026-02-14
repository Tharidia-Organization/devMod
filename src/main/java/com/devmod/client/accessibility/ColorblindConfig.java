package com.devmod.client.accessibility;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side configuration for colorblind accessibility settings.
 *
 * <p>Registered as a CLIENT config so each player has their own preference.
 * The active mode is applied through {@link ColorblindHelper#getColor(int)}.
 *
 * @see ColorblindMode
 */
public final class ColorblindConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** The active colorblind mode. */
    public static final ModConfigSpec.EnumValue<ColorblindMode> COLORBLIND_MODE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("accessibility");

        COLORBLIND_MODE = BUILDER
                .comment(
                        "Colorblind mode for DevMod UI/HUD.",
                        "NONE = default colors (no remapping)",
                        "PROTANOPIA = red-blind (reds -> orange/brown)",
                        "DEUTERANOPIA = green-blind (greens -> yellow/brown, most common)",
                        "TRITANOPIA = blue-blind (blues -> cyan, yellows -> pink)"
                )
                .defineEnum("colorblindMode", ColorblindMode.NONE);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private ColorblindConfig() {}
}
