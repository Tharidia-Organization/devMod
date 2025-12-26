package com.devmod.arena.budget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.builder.BuildLimitExceededException;
public class BuildBudget {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildBudget.class);

    // DD11: Threshold percentages
    private static final double WARN_THRESHOLD = 0.80;
    private static final double ERROR_THRESHOLD = 1.00;

    private final int maxBlocks;
    private final long maxTimeMs;
    private final int maxEntities;

    private int currentBlocks = 0;
    private long startTimeMs = 0;
    private int currentEntities = 0;
    private boolean started = false;
    private boolean warnedBlocks = false;
    private boolean warnedTime = false;
    private boolean warnedEntities = false;

    public BuildBudget(int maxBlocks, long maxTimeMs, int maxEntities) {
        this.maxBlocks = maxBlocks;
        this.maxTimeMs = maxTimeMs;
        this.maxEntities = maxEntities;
    }

    /**
     * Creates a budget with default limits.
     */
    public static BuildBudget defaults() {
        return new BuildBudget(50_000, 30_000, 100);
    }

    /**
     * Creates a budget for boss arenas (higher limits).
     */
    public static BuildBudget forBoss() {
        return new BuildBudget(100_000, 60_000, 200);
    }

    /**
     * Starts the budget timer.
     */
    public void start() {
        this.startTimeMs = System.currentTimeMillis();
        this.started = true;
    }

    /**
     * Tracks a block placement.
     *
     * @throws BuildLimitExceededException if hard limit exceeded
     */
    public void trackBlock() {
        currentBlocks++;
        checkBlockLimit();
    }

    /**
     * Tracks multiple block placements.
     *
     * @throws BuildLimitExceededException if hard limit exceeded
     */
    public void trackBlocks(int count) {
        currentBlocks += count;
        checkBlockLimit();
    }

    /**
     * Tracks an entity spawn.
     *
     * @throws BuildLimitExceededException if hard limit exceeded
     */
    public void trackEntity() {
        currentEntities++;
        checkEntityLimit();
    }

    /**
     * Checks time limit.
     *
     * @throws BuildTimeoutException if hard limit exceeded
     */
    public void checkTime() {
        if (!started) return;

        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        double ratio = (double) elapsedMs / maxTimeMs;

        if (ratio >= ERROR_THRESHOLD) {
            throw new BuildTimeoutException(elapsedMs, maxTimeMs);
        }

        if (ratio >= WARN_THRESHOLD && !warnedTime) {
            warnedTime = true;
            LOGGER.warn("Build time at {}% of budget ({}ms / {}ms)",
                (int)(ratio * 100), elapsedMs, maxTimeMs);
        }
    }

    /**
     * DD11: Checks all budget limits and returns result.
     * Use this for pre-flight checks before operations.
     *
     * @return BudgetCheckResult with status and telemetry event name
     */
    public BudgetCheckResult checkBudget() {
        BudgetState state = getState();
        BudgetStatus status = getStatus();

        // DD11: telemetry event name is "budget_warn" not "budget_warning"
        String telemetryEvent = switch (state) {
            case OK -> null;
            case WARNING -> "budget_warn";
            case EXCEEDED -> "budget_exceeded";
        };

        return new BudgetCheckResult(
            state != BudgetState.EXCEEDED,
            state,
            status,
            telemetryEvent
        );
    }

    /**
     * DD11: Result of budget check with telemetry event.
     */
    public record BudgetCheckResult(
        boolean canProceed,
        BudgetState state,
        BudgetStatus status,
        String telemetryEvent // null if OK, "budget_warn" or "budget_exceeded"
    ) {
        public boolean shouldEmitTelemetry() {
            return telemetryEvent != null;
        }
    }

    /**
     * Returns current budget status.
     */
    public BudgetStatus getStatus() {
        long elapsedMs = started ? System.currentTimeMillis() - startTimeMs : 0;

        return new BudgetStatus(
            currentBlocks,
            maxBlocks,
            (double) currentBlocks / maxBlocks,
            elapsedMs,
            maxTimeMs,
            (double) elapsedMs / maxTimeMs,
            currentEntities,
            maxEntities,
            (double) currentEntities / maxEntities,
            getState()
        );
    }

    /**
     * Returns current state based on highest ratio.
     */
    public BudgetState getState() {
        double blockRatio = (double) currentBlocks / maxBlocks;
        double timeRatio = started ? (double) (System.currentTimeMillis() - startTimeMs) / maxTimeMs : 0;
        double entityRatio = (double) currentEntities / maxEntities;

        double maxRatio = Math.max(blockRatio, Math.max(timeRatio, entityRatio));

        if (maxRatio >= ERROR_THRESHOLD) {
            return BudgetState.EXCEEDED;
        } else if (maxRatio >= WARN_THRESHOLD) {
            return BudgetState.WARNING;
        } else {
            return BudgetState.OK;
        }
    }

    private void checkBlockLimit() {
        double ratio = (double) currentBlocks / maxBlocks;

        if (ratio >= ERROR_THRESHOLD) {
            throw new BuildLimitExceededException(
                BuildLimitExceededException.LimitType.BLOCKS,
                currentBlocks,
                maxBlocks
            );
        }

        if (ratio >= WARN_THRESHOLD && !warnedBlocks) {
            warnedBlocks = true;
            LOGGER.warn("Block count at {}% of budget ({} / {})",
                (int)(ratio * 100), currentBlocks, maxBlocks);
        }
    }

    private void checkEntityLimit() {
        double ratio = (double) currentEntities / maxEntities;

        if (ratio >= ERROR_THRESHOLD) {
            throw new BuildLimitExceededException(
                BuildLimitExceededException.LimitType.ENTITIES,
                currentEntities,
                maxEntities
            );
        }

        if (ratio >= WARN_THRESHOLD && !warnedEntities) {
            warnedEntities = true;
            LOGGER.warn("Entity count at {}% of budget ({} / {})",
                (int)(ratio * 100), currentEntities, maxEntities);
        }
    }

    // === Getters ===

    public int getCurrentBlocks() {
        return currentBlocks;
    }

    public int getMaxBlocks() {
        return maxBlocks;
    }

    public long getElapsedMs() {
        return started ? System.currentTimeMillis() - startTimeMs : 0;
    }

    public long getMaxTimeMs() {
        return maxTimeMs;
    }

    public int getCurrentEntities() {
        return currentEntities;
    }

    public int getMaxEntities() {
        return maxEntities;
    }

    public boolean isStarted() {
        return started;
    }

    // === Supporting Types ===

    public enum BudgetState {
        OK,         // Under 80%
        WARNING,    // 80-99%
        EXCEEDED    // 100%+
    }

    public record BudgetStatus(
        int currentBlocks,
        int maxBlocks,
        double blockRatio,
        long elapsedMs,
        long maxTimeMs,
        double timeRatio,
        int currentEntities,
        int maxEntities,
        double entityRatio,
        BudgetState state
    ) {
        public boolean isOk() {
            return state == BudgetState.OK;
        }

        public boolean isWarning() {
            return state == BudgetState.WARNING;
        }

        public boolean isExceeded() {
            return state == BudgetState.EXCEEDED;
        }
    }
}
