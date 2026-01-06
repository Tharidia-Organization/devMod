package com.devmod.runtime;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes Nexus hub build steps over multiple ticks.
 */
public final class NexusBuildTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusBuildTask.class);

    private final Iterator<NexusBuildStep> steps;
    private final int stepInterval;
    private final long startNanos;
    private int cooldown;
    private int stepIndex;
    private boolean completed;

    public NexusBuildTask(List<NexusBuildStep> steps, int stepInterval) {
        Objects.requireNonNull(steps, "steps");
        this.steps = steps.iterator();
        this.stepInterval = Math.max(1, stepInterval);
        this.startNanos = System.nanoTime();
        this.cooldown = 0;
        this.stepIndex = 0;
        this.completed = false;
    }

    public boolean tick() {
        if (completed) {
            return true;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!steps.hasNext()) {
            complete();
            return true;
        }

        NexusBuildStep step = steps.next();
        long stepStart = System.nanoTime();
        try {
            step.run();
        } catch (Exception e) {
            LOGGER.warn("[Nexus] Build step '{}' failed: {}", step.name(), e.getMessage());
        }
        long stepDurationMs = (System.nanoTime() - stepStart) / 1_000_000L;
        stepIndex++;
        LOGGER.info("[Nexus] Build step {} '{}' completed in {} ms",
            stepIndex, step.name(), stepDurationMs);

        cooldown = stepInterval;
        if (!steps.hasNext()) {
            complete();
            return true;
        }
        return false;
    }

    public boolean isCompleted() {
        return completed;
    }

    private void complete() {
        completed = true;
        long totalMs = (System.nanoTime() - startNanos) / 1_000_000L;
        LOGGER.info("[Nexus] Build completed in {} ms", totalMs);
    }
}
