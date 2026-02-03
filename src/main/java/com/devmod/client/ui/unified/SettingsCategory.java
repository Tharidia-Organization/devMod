package com.devmod.client.ui.unified;

import java.util.List;

import com.devmod.client.ui.editor.core.DesignTokens;

public enum SettingsCategory {
    GENERAL("General", "G", DesignTokens.Semantic.INFO, "General mod settings"),
    RADIAL("Radial", "R", DesignTokens.Radial.MACRO_TOOLS, "Radial menu behavior and UX"),
    EDITOR("Editor", "E", DesignTokens.Accent.PRIMARY, "Item Editor UI settings"),
    DEBUG("Debug", "D", DesignTokens.Semantic.WARNING, "Debug overlays and tools"),
    VISUALIZERS("Visualizers", "V", DesignTokens.Accent.PURPLE, "Heatmaps and visualizations"),
    COMBAT("Combat", "C", DesignTokens.Semantic.ERROR, "Weapon and damage settings"),
    MOBS("Mobs", "M", DesignTokens.Accent.ORANGE, "Mob configuration"),
    TELEMETRY("Telemetry", "T", DesignTokens.Semantic.SUCCESS, "Analytics and export"),
    KEYBINDS("Keybinds", "K", DesignTokens.Text.MUTED, "Keyboard shortcuts");

    private final String label;
    private final String icon;
    private final int accentColor;
    private final String description;

    private static final List<SettingsCategory> ORDER = List.of(values());

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

    /**
     * Returns the stable index for this category.
     */
    public int getIndex() {
        return ORDER.indexOf(this);
    }
}
