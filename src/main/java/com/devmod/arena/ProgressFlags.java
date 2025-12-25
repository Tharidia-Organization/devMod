package com.devmod.arena;

/**
 * Flag constants for arena build progress updates.
 *
 * <p>This is in the common package so it can be used by both
 * server-side code and client-side overlays.</p>
 */
public final class ProgressFlags {
    public static final int NONE = 0;
    public static final int PHASE_CHANGED = 1;
    public static final int COMPLETE = 2;
    public static final int FAILED = 4;
    public static final int PAUSED = 8;

    private ProgressFlags() {}
}
