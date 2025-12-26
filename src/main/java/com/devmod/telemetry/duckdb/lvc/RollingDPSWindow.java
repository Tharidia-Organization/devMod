package com.devmod.telemetry.duckdb.lvc;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.devmod.telemetry.duckdb.aggregation.AggregationConfig;

/**
 * Thread-safe rolling window for calculating real-time DPS (Damage Per Second).
 * Uses a circular buffer to track damage per second over a configurable window.
 *
 * Memory: ~500 bytes (60 doubles + atomic counters)
 *
 * <p>Usage:
 * <pre>
 * RollingDPSWindow dps = new RollingDPSWindow();
 * dps.recordDamage(25.5);  // Called on each hit
 * double currentDPS = dps.getCurrentDPS();  // Instant query, <1ms
 * </pre>
 */
public class RollingDPSWindow {

    /** Window size in seconds */
    private final int windowSize;

    /** Circular buffer of damage accumulated each second */
    private final double[] damagePerSecond;

    /** Current write index in circular buffer */
    private final AtomicInteger currentIndex;

    /** Timestamp (seconds since epoch) of last update */
    private final AtomicLong lastUpdateSecond;

    /** Accumulated damage in the current second (not yet committed) */
    private volatile double currentSecondDamage;

    /** Lock for current second accumulation */
    private final Object accumulatorLock = new Object();

    /**
     * Create a rolling DPS window with default size from config.
     */
    public RollingDPSWindow() {
        this(AggregationConfig.LVC_DPS_WINDOW_SECONDS);
    }

    /**
     * Create a rolling DPS window with custom size.
     *
     * @param windowSizeSeconds size of rolling window in seconds
     */
    public RollingDPSWindow(int windowSizeSeconds) {
        this.windowSize = Math.max(1, windowSizeSeconds);
        this.damagePerSecond = new double[this.windowSize];
        this.currentIndex = new AtomicInteger(0);
        this.lastUpdateSecond = new AtomicLong(getCurrentSecond());
        this.currentSecondDamage = 0.0;
    }

    /**
     * Record damage dealt. Thread-safe.
     *
     * @param damage amount of damage dealt
     */
    public void recordDamage(double damage) {
        if (damage <= 0) return;

        long now = getCurrentSecond();
        long lastSecond = lastUpdateSecond.get();

        synchronized (accumulatorLock) {
            // If we're in a new second, commit previous and advance
            if (now != lastSecond) {
                advanceWindow(now, lastSecond);
            }

            // Accumulate damage for current second
            currentSecondDamage += damage;
        }
    }

    /**
     * Advance the window to the current second, zeroing any skipped seconds.
     */
    private void advanceWindow(long now, long lastSecond) {
        // Commit accumulated damage to the buffer
        int idx = currentIndex.get();
        damagePerSecond[idx] = currentSecondDamage;

        // Calculate how many seconds to advance
        long secondsElapsed = Math.min(now - lastSecond, windowSize);

        // Zero out any skipped seconds
        for (int i = 1; i <= secondsElapsed; i++) {
            int nextIdx = (idx + i) % windowSize;
            damagePerSecond[nextIdx] = 0.0;
        }

        // Update current index
        int newIndex = (int) ((idx + secondsElapsed) % windowSize);
        currentIndex.set(newIndex);

        // Reset accumulator
        currentSecondDamage = 0.0;
        lastUpdateSecond.set(now);
    }

    /**
     * Get current DPS (sum of damage over window / window size).
     * Instant query, thread-safe.
     *
     * @return damage per second averaged over the rolling window
     */
    public double getCurrentDPS() {
        // First, ensure we're up to date
        long now = getCurrentSecond();
        long lastSecond = lastUpdateSecond.get();

        double totalDamage;
        int effectiveWindowSize;

        synchronized (accumulatorLock) {
            // If time has passed, we need to account for it
            if (now != lastSecond) {
                long secondsElapsed = Math.min(now - lastSecond, windowSize);

                // Calculate total from buffer (excluding zeroed slots)
                totalDamage = currentSecondDamage;  // Include uncommitted damage
                effectiveWindowSize = windowSize;

                int idx = currentIndex.get();
                for (int i = 0; i < windowSize; i++) {
                    // Skip slots that would be zeroed by the advance
                    if (i < windowSize - secondsElapsed) {
                        totalDamage += damagePerSecond[(idx - i + windowSize) % windowSize];
                    }
                }
            } else {
                // We're current, sum entire buffer plus accumulator
                totalDamage = currentSecondDamage;
                effectiveWindowSize = windowSize;

                for (double d : damagePerSecond) {
                    totalDamage += d;
                }
            }
        }

        return totalDamage / effectiveWindowSize;
    }

    /**
     * Get total damage dealt over the window (not averaged).
     *
     * @return total damage in the rolling window
     */
    public double getTotalDamageInWindow() {
        return getCurrentDPS() * windowSize;
    }

    /**
     * Get peak DPS (highest single second in window).
     *
     * @return maximum damage dealt in any single second
     */
    public double getPeakDPS() {
        double peak = currentSecondDamage;
        for (double d : damagePerSecond) {
            if (d > peak) {
                peak = d;
            }
        }
        return peak;
    }

    /**
     * Reset the window (call on session start).
     */
    public void reset() {
        synchronized (accumulatorLock) {
            for (int i = 0; i < windowSize; i++) {
                damagePerSecond[i] = 0.0;
            }
            currentSecondDamage = 0.0;
            currentIndex.set(0);
            lastUpdateSecond.set(getCurrentSecond());
        }
    }

    /**
     * Get window size in seconds.
     */
    public int getWindowSize() {
        return windowSize;
    }

    /**
     * Check if there has been any activity in the window.
     */
    public boolean hasActivity() {
        if (currentSecondDamage > 0) return true;
        for (double d : damagePerSecond) {
            if (d > 0) return true;
        }
        return false;
    }

    private static long getCurrentSecond() {
        return System.currentTimeMillis() / 1000;
    }
}
