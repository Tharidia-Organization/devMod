package com.devmod.client.ui.testing;

/**
 * Enum representing the available tabs in the VoxelLab screen.
 * Each tab corresponds to a category of configurable services.
 */
public enum VoxelLabTab {
    OVERVIEW("Overview", "\u2302", "System dashboard and quick toggles"),
    DEBUG_OVERLAYS("Debug", "\u2699", "Debug visualizers and overlays"),
    HUD_SYSTEMS("HUD", "\u25A1", "HUD overlays configuration"),
    TELEMETRY("Telemetry", "\u2261", "Data collection and analytics"),
    EFFECTS("Effects", "\u2728", "VFX and visual effects"),
    COMBAT("Combat", "\u2694", "Combat mechanics settings"),
    SHOWCASE("Showcase", "\u25B6", "UI component showcase");

    private final String displayName;
    private final String icon;
    private final String description;

    VoxelLabTab(String displayName, String icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public String getLabel() {
        return icon + " " + displayName;
    }
}
