package com.devmod.client.overlay;

/**
 * Display modes for the Impact HUD system.
 * Controls what level of visual feedback is shown during combat.
 */
public enum ImpactDisplayMode {
    /**
     * No impact feedback shown.
     * Used during exploration or when user disables the system.
     */
    OFF("Off", false, false, false),

    /**
     * Minimal feedback: flash, shake, sound only.
     * No 3D panels or detailed breakdown.
     * Good for performance or when Epic Fight provides its own feedback.
     */
    MINIMAL("Minimal", true, false, false),

    /**
     * Full 3D panels with damage breakdown.
     * Standard combat mode.
     */
    DETAILED("Detailed", true, true, true),

    /**
     * Everything enabled plus DPS meter and history.
     * Used for testing and training sessions.
     */
    ANALYSIS("Analysis", true, true, true);

    private final String displayName;
    private final boolean showFlash;
    private final boolean show3DPanels;
    private final boolean showBreakdown;

    ImpactDisplayMode(String displayName, boolean showFlash, boolean show3DPanels, boolean showBreakdown) {
        this.displayName = displayName;
        this.showFlash = showFlash;
        this.show3DPanels = show3DPanels;
        this.showBreakdown = showBreakdown;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean shouldShowFlash() {
        return showFlash;
    }

    public boolean shouldShow3DPanels() {
        return show3DPanels;
    }

    public boolean shouldShowBreakdown() {
        return showBreakdown;
    }

    public boolean shouldShowDpsMeter() {
        return this == ANALYSIS;
    }

    public boolean shouldShowHistory() {
        return this == ANALYSIS;
    }

    /**
     * Cycles to the next user-selectable mode.
     * OFF -> MINIMAL -> DETAILED -> OFF
     * (ANALYSIS is only set automatically in TEST context)
     */
    public ImpactDisplayMode cycleNext() {
        return switch (this) {
            case OFF -> MINIMAL;
            case MINIMAL -> DETAILED;
            case DETAILED, ANALYSIS -> OFF;
        };
    }

    /**
     * Gets mode by name, case-insensitive.
     */
    public static ImpactDisplayMode fromName(String name) {
        for (ImpactDisplayMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return DETAILED; // Default
    }
}
