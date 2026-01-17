package com.devmod.area.aesthetic;

/**
 * BIBBIA ESTETICA - REGOLA 5: TIMING COSTRUZIONE
 *
 * Costanti temporali non negoziabili per tutte le operazioni.
 * Questi valori definiscono il ritmo e la fluidità dell'esperienza utente.
 */
public final class AreaBuilderTiming {

    private AreaBuilderTiming() {}

    // ============================================================================
    // PREVIEW
    // ============================================================================

    /** Ticks for preview fade-in animation (0.5 sec) */
    public static final int PREVIEW_FADE_IN_TICKS = 10;

    /** Ticks between preview updates (0.25 sec) */
    public static final int PREVIEW_UPDATE_INTERVAL = 5;

    /** Ticks between preview particle spawns (0.5 sec) */
    public static final int PREVIEW_PARTICLE_INTERVAL = 10;

    /** Maximum preview duration in ticks (10 minutes) */
    public static final int PREVIEW_MAX_DURATION = 12000;

    // ============================================================================
    // BUILDING PHASES
    // ============================================================================

    /** Ticks between build steps (0.1 sec per step) */
    public static final int BUILD_STEP_INTERVAL = 2;

    /** Ticks delay between layers (0.05 sec) */
    public static final int BUILD_LAYER_DELAY = 1;

    /** Number of blocks placed per step */
    public static final int BUILD_CHUNK_SIZE = 256;

    /** Maximum blocks per tick for multi-tick building */
    public static final int BUILD_MAX_BLOCKS_PER_TICK = 512;

    // ============================================================================
    // PROGRESS UPDATES
    // ============================================================================

    /** Ticks between progress UI updates (1 sec) */
    public static final int PROGRESS_UPDATE_INTERVAL = 20;

    /** Progress percentage for sound thresholds (25%, 50%, 75%, 100%) */
    public static final int PROGRESS_SOUND_THRESHOLD = 25;

    /** Milestones for progress sounds */
    private static final int[] PROGRESS_MILESTONES = {25, 50, 75, 100};

    // ============================================================================
    // COOLDOWNS
    // ============================================================================

    /** Ticks cooldown between editor interactions (0.5 sec) */
    public static final int EDITOR_INTERACT_COOLDOWN = 10;

    /** Ticks cooldown before build confirm (2 sec anti-misclick) */
    public static final int BUILD_CONFIRM_COOLDOWN = 40;

    /** Ticks cooldown between preview requests (1 sec) */
    public static final int PREVIEW_REQUEST_COOLDOWN = 20;

    /** Ticks cooldown for GUI button presses (0.25 sec) */
    public static final int GUI_BUTTON_COOLDOWN = 5;

    // ============================================================================
    // BIOME GENERATION
    // ============================================================================

    /** Ticks between biome generation steps (0.2 sec per chunk) */
    public static final int BIOME_GEN_STEP_INTERVAL = 4;

    /** Number of smoothing passes for terrain */
    public static final int TERRAIN_SMOOTHING_PASSES = 3;

    /** Ticks per smoothing pass */
    public static final int TERRAIN_SMOOTH_INTERVAL = 2;

    /** Max chunks to process per tick for biome generation */
    public static final int BIOME_GEN_MAX_CHUNKS_PER_TICK = 4;

    // ============================================================================
    // ANIMATIONS
    // ============================================================================

    /** Ticks for button hover animation */
    public static final int BUTTON_HOVER_TICKS = 3;

    /** Ticks for tab switch animation */
    public static final int TAB_SWITCH_TICKS = 5;

    /** Ticks for slider value change animation */
    public static final int SLIDER_CHANGE_TICKS = 2;

    /** Ticks for error shake animation */
    public static final int ERROR_SHAKE_TICKS = 10;

    // ============================================================================
    // NETWORK
    // ============================================================================

    /** Ticks timeout for server response */
    public static final int NETWORK_TIMEOUT_TICKS = 100;

    /** Ticks between sync requests */
    public static final int SYNC_INTERVAL_TICKS = 60;

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Converts ticks to milliseconds.
     */
    public static long ticksToMs(int ticks) {
        return ticks * 50L;
    }

    /**
     * Converts milliseconds to ticks.
     */
    public static int msToTicks(long ms) {
        return (int) (ms / 50L);
    }

    /**
     * Converts ticks to seconds (float).
     */
    public static float ticksToSeconds(int ticks) {
        return ticks / 20.0f;
    }

    /**
     * Checks if a progress milestone has been reached.
     */
    public static boolean isProgressMilestone(int previousPercent, int currentPercent) {
        for (int milestone : PROGRESS_MILESTONES) {
            if (previousPercent < milestone && currentPercent >= milestone) {
                return true;
            }
        }
        return false;
    }

    /**
     * L-07 fix: Gets all milestones crossed between previous and current progress.
     * Useful when progress may jump over multiple milestones at once.
     *
     * @param previousPercent Previous progress percentage
     * @param currentPercent  Current progress percentage
     * @return Array of crossed milestone values (may be empty)
     */
    public static int[] getCrossedMilestones(int previousPercent, int currentPercent) {
        int count = 0;
        for (int milestone : PROGRESS_MILESTONES) {
            if (previousPercent < milestone && currentPercent >= milestone) {
                count++;
            }
        }
        if (count == 0) {
            return new int[0];
        }
        int[] result = new int[count];
        int idx = 0;
        for (int milestone : PROGRESS_MILESTONES) {
            if (previousPercent < milestone && currentPercent >= milestone) {
                result[idx++] = milestone;
            }
        }
        return result;
    }

    /**
     * Gets the current progress milestone.
     */
    public static int getCurrentMilestone(int percent) {
        for (int i = PROGRESS_MILESTONES.length - 1; i >= 0; i--) {
            if (percent >= PROGRESS_MILESTONES[i]) {
                return PROGRESS_MILESTONES[i];
            }
        }
        return 0;
    }
}
