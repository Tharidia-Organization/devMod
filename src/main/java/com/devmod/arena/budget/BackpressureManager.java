package com.devmod.arena.budget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages backpressure for async builds based on server MSPT (DD12).
 *
 * <p>When MSPT exceeds threshold:
 * <ul>
 *   <li>Reduces blocks-per-tick</li>
 *   <li>Gradually recovers when MSPT normalizes</li>
 * </ul>
 */
public class BackpressureManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackpressureManager.class);

    // DD12: MSPT threshold for backpressure
    private static final double DEFAULT_MSPT_THRESHOLD = 40.0;

    // DD12: Default blocks per tick
    private static final int DEFAULT_BLOCKS_PER_TICK = 500;
    private static final int MIN_BLOCKS_PER_TICK = 50;
    private static final int MAX_BLOCKS_PER_TICK = 1000;

    // Recovery settings
    private static final double REDUCE_FACTOR = 0.5;      // Halve on pressure
    private static final double RECOVER_FACTOR = 1.1;     // 10% increase on recovery
    private static final int RECOVERY_TICKS = 20;         // Wait 20 ticks before recovery

    private final double msptThreshold;
    private final int baseBlocksPerTick;

    private int currentBlocksPerTick;
    private int ticksSinceLastPressure = 0;
    private boolean underPressure = false;
    private long totalBlocksThrottled = 0;

    public BackpressureManager() {
        this(DEFAULT_MSPT_THRESHOLD, DEFAULT_BLOCKS_PER_TICK);
    }

    public BackpressureManager(double msptThreshold, int baseBlocksPerTick) {
        this.msptThreshold = msptThreshold;
        this.baseBlocksPerTick = baseBlocksPerTick;
        this.currentBlocksPerTick = baseBlocksPerTick;
    }

    /**
     * Called each tick to update backpressure state.
     *
     * @param currentMspt Current server MSPT
     * @return Number of blocks allowed this tick
     */
    public int update(double currentMspt) {
        if (currentMspt > msptThreshold) {
            // Apply backpressure
            if (!underPressure) {
                LOGGER.warn("Backpressure triggered: MSPT {} > threshold {}",
                    String.format("%.1f", currentMspt), msptThreshold);
            }

            underPressure = true;
            ticksSinceLastPressure = 0;

            // Reduce blocks per tick
            int previousBlocks = currentBlocksPerTick;
            currentBlocksPerTick = Math.max(MIN_BLOCKS_PER_TICK,
                (int)(currentBlocksPerTick * REDUCE_FACTOR));

            if (currentBlocksPerTick != previousBlocks) {
                totalBlocksThrottled += previousBlocks - currentBlocksPerTick;
                LOGGER.debug("Reduced blocks/tick: {} -> {}", previousBlocks, currentBlocksPerTick);
            }

        } else {
            // Normal operation or recovery
            ticksSinceLastPressure++;

            if (underPressure && ticksSinceLastPressure > RECOVERY_TICKS) {
                // Start recovery
                underPressure = false;
                LOGGER.info("Backpressure released after {} ticks", ticksSinceLastPressure);
            }

            // Gradual recovery
            if (!underPressure && currentBlocksPerTick < baseBlocksPerTick) {
                int previousBlocks = currentBlocksPerTick;
                currentBlocksPerTick = Math.min(baseBlocksPerTick,
                    (int)(currentBlocksPerTick * RECOVER_FACTOR));

                if (currentBlocksPerTick != previousBlocks) {
                    LOGGER.debug("Recovering blocks/tick: {} -> {}", previousBlocks, currentBlocksPerTick);
                }
            }
        }

        return currentBlocksPerTick;
    }

    /**
     * Forces a specific blocks-per-tick value (for testing).
     */
    public void setBlocksPerTick(int blocksPerTick) {
        this.currentBlocksPerTick = Math.max(MIN_BLOCKS_PER_TICK,
            Math.min(MAX_BLOCKS_PER_TICK, blocksPerTick));
    }

    /**
     * Resets to default state.
     */
    public void reset() {
        this.currentBlocksPerTick = baseBlocksPerTick;
        this.ticksSinceLastPressure = 0;
        this.underPressure = false;
    }

    // === Getters ===

    public int getCurrentBlocksPerTick() {
        return currentBlocksPerTick;
    }

    public int getBaseBlocksPerTick() {
        return baseBlocksPerTick;
    }

    public double getMsptThreshold() {
        return msptThreshold;
    }

    public boolean isUnderPressure() {
        return underPressure;
    }

    public int getTicksSinceLastPressure() {
        return ticksSinceLastPressure;
    }

    public long getTotalBlocksThrottled() {
        return totalBlocksThrottled;
    }

    public double getThrottleRatio() {
        return (double) currentBlocksPerTick / baseBlocksPerTick;
    }

    /**
     * Returns current backpressure status.
     */
    public BackpressureStatus getStatus() {
        return new BackpressureStatus(
            underPressure,
            currentBlocksPerTick,
            baseBlocksPerTick,
            getThrottleRatio(),
            ticksSinceLastPressure,
            totalBlocksThrottled
        );
    }

    /**
     * Backpressure status snapshot.
     */
    public record BackpressureStatus(
        boolean underPressure,
        int currentBlocksPerTick,
        int baseBlocksPerTick,
        double throttleRatio,
        int ticksSinceLastPressure,
        long totalBlocksThrottled
    ) {}
}
