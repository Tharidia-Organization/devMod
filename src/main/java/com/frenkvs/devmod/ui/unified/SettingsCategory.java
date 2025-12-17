package com.frenkvs.devmod.ui.unified;

import com.frenkvs.devmod.ui.UIConstants;

/**
 * Available categories in the unified settings panel.
 * Each category has an icon, label, and accent color.
 */
public enum SettingsCategory {
    GENERAL("General", "G", UIConstants.Status.INFO(), "General mod settings"),
    EDITOR("Editor", "E", UIConstants.Accent.CYAN(), "Item Editor UI settings"),
    DEBUG("Debug", "D", UIConstants.Status.WARNING(), "Debug overlays and tools"),
    VISUALIZERS("Visualizers", "V", UIConstants.Accent.PURPLE(), "Heatmaps and visualizations"),
    COMBAT("Combat", "C", UIConstants.Status.ERROR(), "Weapon and damage settings"),
    MOBS("Mobs", "M", UIConstants.Accent.ORANGE(), "Mob configuration"),
    TELEMETRY("Telemetry", "T", UIConstants.Status.SUCCESS(), "Analytics and export"),
    KEYBINDS("Keybinds", "K", UIConstants.Text.MUTED(), "Keyboard shortcuts");

    private final String label;
    private final String icon;
    private final int accentColor;
    private final String description;

    SettingsCategory(String label, String icon, int accentColor, String description) {
        this.label = label;
        this.icon = icon;
        this.accentColor = accentColor;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    public int getAccentColor() {
        return accentColor;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Finds a category by label (case insensitive).
     */
    public static SettingsCategory fromLabel(String label) {
        for (SettingsCategory cat : values()) {
            if (cat.label.equalsIgnoreCase(label)) {
                return cat;
            }
        }
        return GENERAL;
    }
}
