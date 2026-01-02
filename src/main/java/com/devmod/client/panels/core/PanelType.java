package com.devmod.client.panels.core;

import com.devmod.client.ui.editor.core.DesignTokens;

public enum PanelType {
    /** Entity info panel - shows target mob/player stats */
    ENTITY_INFO("Entity Info", 180, 120, 8000, true, DesignTokens.Semantic.INFO),

    /** Combat panel - shows damage dealt/received */
    COMBAT("Combat", 160, 100, 4000, false, DesignTokens.Semantic.ERROR),

    /** Tool status panel - shows active overlays */
    TOOL_STATUS("Tools", 140, 80, 0, true, DesignTokens.Accent.PRIMARY),

    /** Test progress panel - shows current test */
    TEST_PROGRESS("Test", 200, 90, 0, true, DesignTokens.Semantic.SUCCESS),

    /** Generic custom panel */
    CUSTOM("Custom", 150, 100, 5000, true, DesignTokens.Text.PRIMARY);

    private final String displayName;
    private final int defaultWidth;
    private final int defaultHeight;
    private final long autoExpireMs;
    private final boolean canPin;
    private final int accentColor;

    PanelType(String displayName, int defaultWidth, int defaultHeight,
              long autoExpireMs, boolean canPin, int accentColor) {
        this.displayName = displayName;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
        this.autoExpireMs = autoExpireMs;
        this.canPin = canPin;
        this.accentColor = accentColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultWidth() {
        return defaultWidth;
    }

    public int getDefaultHeight() {
        return defaultHeight;
    }

    /**
     * Time after which the panel closes automatically.
     * 0 = never expires (requires manual close or pin).
     */
    public long getAutoExpireMs() {
        return autoExpireMs;
    }

    /**
     * Whether this panel type can be pinned.
     */
    public boolean canPin() {
        return canPin;
    }

    /**
     * Accent color for borders and highlights.
     */
    public int getAccentColor() {
        return accentColor;
    }

    /**
     * Whether the panel expires automatically.
     */
    public boolean hasAutoExpire() {
        return autoExpireMs > 0;
    }
}
