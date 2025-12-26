package com.devmod.arena.monitor;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors MSPT (milliseconds per tick) using a sliding window.
 * DD38: Baseline capture with median, sliding window of 100 samples over 5 seconds.
 *
 * <p>Key specifications (DD38):
 * <ul>
 *   <li>BASELINE_SAMPLES = 20 (1 second of samples at 20 TPS)</li>
 *   <li>WINDOW_SIZE = 100 (5 seconds sliding window)</li>
 *   <li>Confidence formula: Math.max(0, 1.0 - (stdDev / 10.0))</li>
 *   <li>Backpressure: confidence > 0.7 AND buildImpact > 20.0ms</li>
 * </ul>
 */
public class MsptMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MsptMonitor.class);

    // DD38: Baseline samples (1 second = 20 ticks at 20 TPS)
    private static final int BASELINE_SAMPLES = 20;

    // DD38: Sliding window (5 seconds = 100 ticks at 20 TPS)
    private static final int WINDOW_SIZE = 100;
    private static final Duration WINDOW_DURATION = Duration.ofSeconds(5);

    // DD38: Backpressure thresholds
    private static final double BUILD_IMPACT_THRESHOLD_MS = 20.0;
    private static final double MIN_CONFIDENCE_FOR_BACKPRESSURE = 0.7;

    private final double[] samples = new double[WINDOW_SIZE];
    private final long[] timestamps = new long[WINDOW_SIZE];
    private final AtomicInteger sampleIndex = new AtomicInteger(0);
    private final AtomicInteger sampleCount = new AtomicInteger(0);

    private volatile double baseline = -1.0;
    private volatile Instant baselineCapturedAt = null;

    /**
     * Records a new MSPT sample.
     */
    public void recordSample(double mspt) {
        int index = sampleIndex.getAndUpdate(i -> (i + 1) % WINDOW_SIZE);
        samples[index] = mspt;
        timestamps[index] = System.currentTimeMillis();

        if (sampleCount.get() < WINDOW_SIZE) {
            sampleCount.incrementAndGet();
        }
    }

    /**
     * Captures the current baseline using median of recent samples.
     * DD38: Uses median for outlier resistance.
     */
    public double captureBaseline() {
        int count = sampleCount.get();
        if (count == 0) {
            LOGGER.warn("Cannot capture baseline: no samples recorded");
            return -1.0;
        }

        // Get valid samples within window duration
        long cutoff = System.currentTimeMillis() - WINDOW_DURATION.toMillis();
        double[] validSamples = new double[count];
        int validCount = 0;

        for (int i = 0; i < count; i++) {
            if (timestamps[i] >= cutoff) {
                validSamples[validCount++] = samples[i];
            }
        }

        if (validCount == 0) {
            LOGGER.warn("Cannot capture baseline: no recent samples");
            return -1.0;
        }

        // Calculate median
        double[] sorted = Arrays.copyOf(validSamples, validCount);
        Arrays.sort(sorted);
        double median;
        if (validCount % 2 == 0) {
            median = (sorted[validCount / 2 - 1] + sorted[validCount / 2]) / 2.0;
        } else {
            median = sorted[validCount / 2];
        }

        this.baseline = median;
        this.baselineCapturedAt = Instant.now();

        LOGGER.info("Baseline captured: {}ms (from {} samples)",
            String.format("%.2f", median), validCount);

        return median;
    }

    /**
     * Returns the current baseline, or -1 if not captured.
     */
    public double getBaseline() {
        return baseline;
    }

    /**
     * Returns when the baseline was captured.
     */
    public Instant getBaselineCapturedAt() {
        return baselineCapturedAt;
    }

    /**
     * Returns the current MSPT sample with confidence score.
     */
    public MsptSample getCurrentSample() {
        int count = sampleCount.get();
        if (count == 0) {
            return MsptSample.now(0, 0);
        }

        int latestIndex = (sampleIndex.get() - 1 + WINDOW_SIZE) % WINDOW_SIZE;
        double mspt = samples[latestIndex];
        double confidence = calculateConfidence();

        return MsptSample.now(mspt, confidence);
    }

    /**
     * Calculates confidence score based on sample count and variance.
     * DD38: Confidence formula = Math.max(0, 1.0 - (stdDev / 10.0))
     */
    public double calculateConfidence() {
        int count = sampleCount.get();

        if (count < BASELINE_SAMPLES) {
            return 0.1 * count / BASELINE_SAMPLES; // Low confidence with few samples
        }

        // Calculate variance of recent samples
        long cutoff = System.currentTimeMillis() - WINDOW_DURATION.toMillis();
        double sum = 0;
        double sumSq = 0;
        int validCount = 0;

        for (int i = 0; i < count && i < WINDOW_SIZE; i++) {
            if (timestamps[i] >= cutoff) {
                double val = samples[i];
                sum += val;
                sumSq += val * val;
                validCount++;
            }
        }

        if (validCount < 5) {
            return 0.5; // Moderate confidence
        }

        double mean = sum / validCount;
        double variance = (sumSq / validCount) - (mean * mean);
        double stdDev = Math.sqrt(Math.max(0, variance));

        // DD38: Confidence formula as specified
        // High stdDev = low confidence, stdDev=0 -> confidence=1.0, stdDev=10 -> confidence=0
        double confidence = Math.max(0, 1.0 - (stdDev / 10.0));

        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * Returns true if backpressure should be applied based on current samples.
     * DD38: Backpressure when confidence > 0.7 AND buildImpact > 20.0ms
     */
    public boolean shouldBackpressure() {
        if (baseline < 0) {
            // No baseline captured yet, cannot determine build impact
            return false;
        }

        MsptSample current = getCurrentSample();
        double buildImpact = current.mspt() - baseline;
        double confidence = current.confidenceScore();

        // DD38: Both conditions must be met
        return confidence > MIN_CONFIDENCE_FOR_BACKPRESSURE
            && buildImpact > BUILD_IMPACT_THRESHOLD_MS;
    }

    /**
     * Returns the current build impact (current MSPT - baseline).
     *
     * @return build impact in ms, or 0 if no baseline
     */
    public double getBuildImpact() {
        if (baseline < 0) {
            return 0;
        }
        MsptSample current = getCurrentSample();
        return current.mspt() - baseline;
    }

    /**
     * Returns statistics about the current monitoring state.
     */
    public MonitorStats getStats() {
        int count = sampleCount.get();
        if (count == 0) {
            return new MonitorStats(0, 0, 0, 0, 0, baseline);
        }

        long cutoff = System.currentTimeMillis() - WINDOW_DURATION.toMillis();
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double sum = 0;
        int validCount = 0;

        for (int i = 0; i < count && i < WINDOW_SIZE; i++) {
            if (timestamps[i] >= cutoff) {
                double val = samples[i];
                min = Math.min(min, val);
                max = Math.max(max, val);
                sum += val;
                validCount++;
            }
        }

        if (validCount == 0) {
            return new MonitorStats(0, 0, 0, 0, 0, baseline);
        }

        double mean = sum / validCount;

        return new MonitorStats(validCount, min, max, mean, calculateConfidence(), baseline);
    }

    /**
     * Resets all samples and baseline.
     */
    public void reset() {
        sampleIndex.set(0);
        sampleCount.set(0);
        baseline = -1.0;
        baselineCapturedAt = null;
        Arrays.fill(samples, 0);
        Arrays.fill(timestamps, 0);
    }

    /**
     * Statistics record for monitoring state.
     */
    public record MonitorStats(
        int sampleCount,
        double minMspt,
        double maxMspt,
        double avgMspt,
        double confidence,
        double baseline
    ) {
        public boolean hasBaseline() {
            return baseline >= 0;
        }
    }
}
