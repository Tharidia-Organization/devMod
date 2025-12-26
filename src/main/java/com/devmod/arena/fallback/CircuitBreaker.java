package com.devmod.arena.fallback;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
public class CircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long windowMillis;
    private final long cooldownMillis;

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<Instant> openedAt = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<Instant> windowStart = new AtomicReference<>(Instant.now());

    /**
     * Creates a CircuitBreaker with DD45 defaults:
     * - threshold: 3 failures
     * - window: 5 minutes
     * - cooldown: 30 seconds
     */
    public CircuitBreaker() {
        this(3, 5 * 60 * 1000L, 30 * 1000L);
    }

    /**
     * Creates a CircuitBreaker with custom parameters.
     *
     * @param failureThreshold Number of failures before opening circuit
     * @param windowMillis Time window for counting failures (milliseconds)
     * @param cooldownMillis Time to wait before attempting recovery (milliseconds)
     */
    public CircuitBreaker(int failureThreshold, long windowMillis, long cooldownMillis) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("Failure threshold must be positive");
        }
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("Window must be positive");
        }
        if (cooldownMillis <= 0) {
            throw new IllegalArgumentException("Cooldown must be positive");
        }

        this.failureThreshold = failureThreshold;
        this.windowMillis = windowMillis;
        this.cooldownMillis = cooldownMillis;
    }

    /**
     * Check if a request is allowed to proceed.
     *
     * @return true if the request should be allowed, false if it should fail fast
     */
    public boolean allowRequest() {
        State currentState = state.get();

        switch (currentState) {
            case CLOSED:
                return true;

            case OPEN:
                // Check if cooldown has elapsed
                Instant opened = openedAt.get();
                if (Instant.now().isAfter(opened.plusMillis(cooldownMillis))) {
                    // Transition to HALF_OPEN
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        return true;
                    }
                }
                return false;

            case HALF_OPEN:
                // Only allow one request through during half-open
                return true;

            default:
                return false;
        }
    }

    /**
     * Record a successful operation. Resets failure count and closes circuit.
     */
    public void recordSuccess() {
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            // Recovery successful, close the circuit
            reset();
        } else if (currentState == State.CLOSED) {
            // Reset window if outside current window
            Instant now = Instant.now();
            Instant ws = windowStart.get();
            if (now.isAfter(ws.plusMillis(windowMillis))) {
                windowStart.set(now);
                failureCount.set(0);
            }
        }
    }

    /**
     * Record a failed operation. May trigger circuit to open.
     */
    public void recordFailure() {
        Instant now = Instant.now();
        lastFailureTime.set(now);

        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            // Recovery failed, reopen circuit
            openCircuit(now);
            return;
        }

        if (currentState == State.OPEN) {
            return; // Already open
        }

        // CLOSED state - check window and count failures
        Instant ws = windowStart.get();
        if (now.isAfter(ws.plusMillis(windowMillis))) {
            // Reset window
            windowStart.set(now);
            failureCount.set(1);
        } else {
            int count = failureCount.incrementAndGet();
            if (count >= failureThreshold) {
                openCircuit(now);
            }
        }
    }

    private void openCircuit(Instant now) {
        state.set(State.OPEN);
        openedAt.set(now);
    }

    /**
     * Reset the circuit breaker to closed state.
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        windowStart.set(Instant.now());
    }

    /**
     * Force the circuit to open.
     */
    public void forceOpen() {
        openCircuit(Instant.now());
    }

    /**
     * Get the current state of the circuit breaker.
     */
    public State getState() {
        // Check for state transitions
        if (state.get() == State.OPEN) {
            Instant opened = openedAt.get();
            if (Instant.now().isAfter(opened.plusMillis(cooldownMillis))) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
            }
        }
        return state.get();
    }

    /**
     * Get current failure count within window.
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * Get the failure threshold.
     */
    public int getFailureThreshold() {
        return failureThreshold;
    }

    /**
     * Get the window duration in milliseconds.
     */
    public long getWindowMillis() {
        return windowMillis;
    }

    /**
     * Get the cooldown duration in milliseconds.
     */
    public long getCooldownMillis() {
        return cooldownMillis;
    }

    /**
     * Check if circuit is open (not allowing requests).
     */
    public boolean isOpen() {
        return getState() == State.OPEN;
    }

    /**
     * Check if circuit is closed (allowing requests).
     */
    public boolean isClosed() {
        return getState() == State.CLOSED;
    }

    /**
     * Get time until circuit breaker allows next request attempt.
     * Returns 0 if already allowing requests.
     */
    public long getTimeUntilNextAttempt() {
        if (state.get() != State.OPEN) {
            return 0;
        }

        Instant opened = openedAt.get();
        Instant allowedAt = opened.plusMillis(cooldownMillis);
        long remaining = allowedAt.toEpochMilli() - Instant.now().toEpochMilli();
        return Math.max(0, remaining);
    }
}
