package com.devmod.hologram.data;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Types of holograms supported by the Hologram Builder system.
 */
public enum HologramType implements StringRepresentable {
    /**
     * Static content - no placeholder resolution.
     */
    STATIC("static"),

    /**
     * Dynamic content - supports placeholders like {player}, {online}, {tps}.
     */
    DYNAMIC("dynamic"),

    /**
     * Leaderboard format - special formatting for ranked entries.
     */
    LEADERBOARD("leaderboard"),

    /**
     * Announcement format - styled for server announcements.
     */
    ANNOUNCEMENT("announcement");

    public static final Codec<HologramType> CODEC = StringRepresentable.fromEnum(HologramType::values);

    private final String name;

    HologramType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /**
     * Whether this type supports placeholder resolution.
     */
    public boolean supportsDynamicContent() {
        return this == DYNAMIC || this == LEADERBOARD || this == ANNOUNCEMENT;
    }
}
