package com.devmod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientVisualConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue TELEPAD_PORTAL_COMPAT_MODE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("telepad");

        TELEPAD_PORTAL_COMPAT_MODE = BUILDER
                .comment(
                        "Enable compatibility mode for the Telepad portal renderer.",
                        "Uses a textureless render path to avoid GPU driver artifacts (notably on some NVIDIA/Windows setups)."
                )
                .define("portalCompatMode", false);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private ClientVisualConfig() {}
}
