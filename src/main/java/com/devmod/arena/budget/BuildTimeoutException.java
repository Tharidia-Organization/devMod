package com.devmod.arena.budget;

/**
 * Exception thrown when a build exceeds the time budget (DD11).
 */
public class BuildTimeoutException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final long elapsedMs;
    private final long limitMs;

    public BuildTimeoutException(long elapsedMs, long limitMs) {
        super("Build timeout: %dms exceeded limit of %dms".formatted(elapsedMs, limitMs));
        this.elapsedMs = elapsedMs;
        this.limitMs = limitMs;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public long getLimitMs() {
        return limitMs;
    }

    public double getOverageRatio() {
        return (double) elapsedMs / limitMs;
    }
}
