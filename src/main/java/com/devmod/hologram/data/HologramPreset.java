package com.devmod.hologram.data;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Predefined hologram presets for common use cases.
 * These correspond to the legacy NexusHologramManager types.
 */
@SuppressWarnings("ImmutableEnumChecker")
public enum HologramPreset {
    /**
     * Welcome sign at spawn area.
     */
    WELCOME(
        "preset_welcome",
        "Welcome",
        List.of(
            "\u00A7b\u00A7lWelcome to the Nexus",
            "\u00A77\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550",
            "\u00A7fPlayers online: \u00A7a{online}",
            "\u00A7fServer TPS: \u00A7a{tps}"
        ),
        HologramType.DYNAMIC,
        new Offset(0, 3, 30)
    ),

    /**
     * Top players leaderboard.
     */
    LEADERBOARD(
        "preset_leaderboard",
        "Leaderboard",
        List.of(
            "\u00A76\u00A7lTop Players",
            "\u00A77\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550",
            "\u00A7e1. \u00A7fLoading...",
            "\u00A7e2. \u00A7fLoading...",
            "\u00A7e3. \u00A7fLoading...",
            "\u00A7e4. \u00A7fLoading...",
            "\u00A7e5. \u00A7fLoading..."
        ),
        HologramType.LEADERBOARD,
        new Offset(0, 4, 20)
    ),

    /**
     * Zone status information.
     */
    ZONE_INFO(
        "preset_zone_info",
        "Zone Info",
        List.of(
            "\u00A7a\u00A7l{zone}",
            "\u00A77\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550",
            "\u00A7fCurrent zone information"
        ),
        HologramType.DYNAMIC,
        new Offset(-15, 3, 0)
    ),

    /**
     * Server announcements.
     */
    ANNOUNCEMENT(
        "preset_announcement",
        "Announcement",
        List.of(
            "\u00A7c\u00A7lAnnouncement",
            "\u00A77\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550",
            "\u00A7fEdit this message"
        ),
        HologramType.ANNOUNCEMENT,
        new Offset(0, 5, -15)
    );

    private final String id;
    private final String label;
    private final List<String> defaultLines;
    private final HologramType type;
    private final Offset defaultOffset;

    HologramPreset(String id, String label, List<String> defaultLines, HologramType type, Offset offset) {
        this.id = id;
        this.label = label;
        this.defaultLines = defaultLines;
        this.type = type;
        this.defaultOffset = offset;
    }

    /**
     * Unique identifier for this preset.
     */
    public String getId() {
        return id;
    }

    /**
     * Display label for UI.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Default content lines for this preset.
     */
    public List<String> getDefaultLines() {
        return defaultLines;
    }

    /**
     * Hologram type for this preset.
     */
    public HologramType getType() {
        return type;
    }

    /**
     * Default offset from hub origin for legacy compatibility.
     */
    public Offset getDefaultOffset() {
        return defaultOffset;
    }

    /**
     * Offset record for preset positioning.
     */
    public record Offset(int x, int y, int z) {
        public net.minecraft.core.BlockPos applyTo(net.minecraft.core.BlockPos origin) {
            return origin.offset(x, y, z);
        }
    }

    /**
     * Finds a preset by its ID.
     */
    @Nullable
    public static HologramPreset byId(String id) {
        for (HologramPreset preset : values()) {
            if (preset.id.equals(id)) {
                return preset;
            }
        }
        return null;
    }

    /**
     * Checks if the given ID is a reserved preset ID.
     */
    public static boolean isReservedId(String id) {
        return id != null && id.startsWith("preset_");
    }
}
