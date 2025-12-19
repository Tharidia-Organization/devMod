package com.frenkvs.devmod.panels.context;

import com.frenkvs.devmod.ui.UIConstants;

/**
 * Context modes that determine which panels to show automatically.
 *
 * The system automatically detects context based on:
 * - Recent combat events
 * - Active testing session
 * - Entity interactions
 * - Explicit user input
 */
public enum ContextMode {

    /**
     * Combat mode: activated when the player is in combat.
     * Shows: CombatPanel, EntityInfoPanel for target
     */
    COMBAT("Combat", UIConstants.Status.ERROR(), true, false, true),

    /**
     * Explore mode: default when not in combat or testing.
     * Shows: EntityInfoPanel on hover, ToolStatusPanel
     */
    EXPLORE("Explore", UIConstants.Status.INFO(), true, true, false),

    /**
     * Testing mode: activated during a test session.
     * Shows: TestProgressPanel, ToolStatusPanel, reduces other panels
     */
    TEST("Test", UIConstants.Status.SUCCESS(), true, true, true),

    /**
     * Custom mode: user manually controls all panels.
     * No automatic panels.
     */
    CUSTOM("Custom", UIConstants.Text.MUTED(), false, false, false);

    private final String displayName;
    private final int color;
    private final boolean autoEntityInfo;
    private final boolean showToolStatus;
    private final boolean showProgressPanel;

    ContextMode(String displayName, int color, boolean autoEntityInfo,
                boolean showToolStatus, boolean showProgressPanel) {
        this.displayName = displayName;
        this.color = color;
        this.autoEntityInfo = autoEntityInfo;
        this.showToolStatus = showToolStatus;
        this.showProgressPanel = showProgressPanel;
    }

    /**
     * Name displayed in UI.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Color associated with this mode.
     */
    public int getColor() {
        return color;
    }

    /**
     * Whether to automatically show EntityInfoPanel for target entities.
     */
    public boolean showsAutoEntityInfo() {
        return autoEntityInfo;
    }

    /**
     * Whether to show the tool status panel.
     */
    public boolean showsToolStatus() {
        return showToolStatus;
    }

    /**
     * Whether to show the progress panel (test/combat).
     */
    public boolean showsProgressPanel() {
        return showProgressPanel;
    }

    /**
     * Whether this mode automatically manages panels.
     */
    public boolean isAutomatic() {
        return this != CUSTOM;
    }

    /**
     * Gets the panel priority in this mode.
     * Higher priority = panels persist longer.
     */
    public int getPanelPriority() {
        return switch (this) {
            case COMBAT -> 3;   // High priority - combat panels persist
            case TEST -> 2;     // Medium-high - test info is important
            case EXPLORE -> 1;  // Normal
            case CUSTOM -> 0;   // Low - user manages everything
        };
    }

    /**
     * Auto-hide time for EntityInfoPanel in this mode (ms).
     * 0 = don't auto-hide.
     */
    public long getEntityInfoTimeout() {
        return switch (this) {
            case COMBAT -> 0;       // Don't hide in combat
            case TEST -> 10000;     // 10 seconds
            case EXPLORE -> 5000;   // 5 seconds
            case CUSTOM -> 0;       // Manual
        };
    }
}
