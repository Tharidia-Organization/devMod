package com.devmod.arena.monitor;

import java.time.Instant;

/**
 * A single MSPT (milliseconds per tick) sample with metadata.
 * DD38: Used in sliding window for baseline comparison.
 */
public record MsptSample(
    double mspt,
    Instant timestamp,
    double confidenceScore
) {
    private static final double DEFAULT_THRESHOLD = 40.0;
    private static final double MIN_CONFIDENCE_FOR_BACKPRESSURE = 0.7;

    /**
     * Creates a sample with current timestamp.
     */
    public static MsptSample now(double mspt, double confidenceScore) {
        return new MsptSample(mspt, Instant.now(), confidenceScore);
    }

    /**
     * Creates a sample with default confidence.
     */
    public static MsptSample now(double mspt) {
        return new MsptSample(mspt, Instant.now(), 1.0);
    }

    /**
     * Returns true if this sample indicates backpressure should be applied.
     * DD38: Combines MSPT threshold with confidence score.
     *
     * @param threshold The MSPT threshold (default 40ms)
     */
    public boolean shouldBackpressure(double threshold) {
        // Only trigger backpressure if:
        // 1. MSPT exceeds threshold
        // 2. Confidence score is high enough
        return mspt > threshold && confidenceScore >= MIN_CONFIDENCE_FOR_BACKPRESSURE;
    }

    /**
     * Returns true if this sample indicates backpressure should be applied
     * using the default threshold.
     */
    public boolean shouldBackpressure() {
        return shouldBackpressure(DEFAULT_THRESHOLD);
    }

    /**
     * Returns the age of this sample in milliseconds.
     */
    public long getAgeMs() {
        return Instant.now().toEpochMilli() - timestamp.toEpochMilli();
    }

    /**
     * Returns true if this sample is still fresh (less than maxAgeMs old).
     */
    public boolean isFresh(long maxAgeMs) {
        return getAgeMs() < maxAgeMs;
    }
}
