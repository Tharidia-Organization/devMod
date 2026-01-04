package com.devmod.client.endurance.wis;

/**
 * Defines the phases of the Wave Intelligence System.
 * Each phase has different UI behavior and data handling.
 */
public enum WavePhase {
    /**
     * Pre-wave briefing phase (10 seconds).
     * Shows wave composition, elite intel, modifiers, player status.
     * Full opacity UI with detailed information.
     */
    PRE_BRIEF("Pre-Wave Brief", 200), // 10 seconds = 200 ticks

    /**
     * Active combat phase.
     * Minimal transparent HUD, data collected in background.
     * UI at 20% opacity to minimize distraction.
     */
    COMBAT("Combat", -1), // Duration depends on wave completion

    /**
     * Post-wave debrief phase.
     * Full screen analysis with 5 tabs.
     * Player can skip or review detailed analytics.
     */
    DEBRIEF("Debrief", -1), // Duration until player dismisses

    /**
     * Idle state between waves or when no quest is active.
     */
    IDLE("Idle", -1);

    private final String displayName;
    private final int defaultDurationTicks;

    WavePhase(String displayName, int defaultDurationTicks) {
        this.displayName = displayName;
        this.defaultDurationTicks = defaultDurationTicks;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get default duration in ticks (-1 means variable/unlimited).
     */
    public int getDefaultDurationTicks() {
        return defaultDurationTicks;
    }

    /**
     * Check if this phase has a fixed duration.
     */
    public boolean hasFixedDuration() {
        return defaultDurationTicks > 0;
    }
}
