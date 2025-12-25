package com.devmod.arena;

/**
 * Build phases for arena construction progress.
 *
 * <p>This is in the common package so it can be used by both
 * server-side code and client-side overlays.</p>
 */
public enum BuildPhase {
    INITIALIZING,
    LOADING_CHUNKS,
    PLACING_BLOCKS,
    SPAWNING_ENTITIES,
    FINALIZING,
    COMPLETE,
    FAILED
}
