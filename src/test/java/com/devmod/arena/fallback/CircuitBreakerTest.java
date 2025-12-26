package com.devmod.arena.fallback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CircuitBreaker Tests")
class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // Use short times for testing
        circuitBreaker = new CircuitBreaker(3, 1000, 50);
    }

    @Test
    @DisplayName("Starts in CLOSED state")
    void startsInClosedState() {
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertTrue(circuitBreaker.isClosed());
        assertTrue(circuitBreaker.allowRequest());
    }

    @Test
    @DisplayName("Opens after threshold failures")
    void opensAfterThresholdFailures() {
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure();
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertTrue(circuitBreaker.isOpen());
        assertFalse(circuitBreaker.allowRequest());
    }

    @Test
    @DisplayName("Stays closed below threshold")
    void staysClosedBelowThreshold() {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();

        assertTrue(circuitBreaker.isClosed());
        assertEquals(2, circuitBreaker.getFailureCount());
    }

    @Test
    @DisplayName("Success resets failure count")
    void successResetsFailureCount() {
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordSuccess();

        assertTrue(circuitBreaker.isClosed());
        // Window resets on success, so count is 0
    }

    @Test
    @DisplayName("Transitions to HALF_OPEN after cooldown")
    void transitionsToHalfOpenAfterCooldown() throws InterruptedException {
        // Trip the circuit
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure();
        }

        assertTrue(circuitBreaker.isOpen());

        // Wait for cooldown (50ms)
        Thread.sleep(100);

        // Should transition to HALF_OPEN
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        assertTrue(circuitBreaker.allowRequest());
    }

    @Test
    @DisplayName("Closes after success in HALF_OPEN state")
    void closesAfterSuccessInHalfOpen() throws InterruptedException {
        // Trip and wait for cooldown
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure();
        }
        Thread.sleep(100);

        // Now in HALF_OPEN
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());

        // Record success
        circuitBreaker.recordSuccess();

        // Should be CLOSED now
        assertTrue(circuitBreaker.isClosed());
    }

    @Test
    @DisplayName("Reopens after failure in HALF_OPEN state")
    void reopensAfterFailureInHalfOpen() throws InterruptedException {
        // Trip and wait for cooldown
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure();
        }
        Thread.sleep(100);

        // Now in HALF_OPEN
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());

        // Record failure
        circuitBreaker.recordFailure();

        // Should be OPEN again
        assertTrue(circuitBreaker.isOpen());
    }

    @Test
    @DisplayName("DD45: Default threshold is 3")
    void defaultThresholdIsThree() {
        CircuitBreaker defaultBreaker = new CircuitBreaker();
        assertEquals(3, defaultBreaker.getFailureThreshold());
    }

    @Test
    @DisplayName("DD45: Default window is 5 minutes")
    void defaultWindowIsFiveMinutes() {
        CircuitBreaker defaultBreaker = new CircuitBreaker();
        assertEquals(5 * 60 * 1000L, defaultBreaker.getWindowMillis());
    }

    @Test
    @DisplayName("DD45: Default cooldown is 30 seconds")
    void defaultCooldownIsThirtySeconds() {
        CircuitBreaker defaultBreaker = new CircuitBreaker();
        assertEquals(30 * 1000L, defaultBreaker.getCooldownMillis());
    }

    @Test
    @DisplayName("Force open works")
    void forceOpenWorks() {
        assertTrue(circuitBreaker.isClosed());
        circuitBreaker.forceOpen();
        assertTrue(circuitBreaker.isOpen());
    }

    @Test
    @DisplayName("Reset works")
    void resetWorks() {
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure();
        }
        assertTrue(circuitBreaker.isOpen());

        circuitBreaker.reset();

        assertTrue(circuitBreaker.isClosed());
        assertEquals(0, circuitBreaker.getFailureCount());
    }

    @Test
    @DisplayName("Invalid constructor parameters throw exceptions")
    void invalidConstructorParametersThrow() {
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker(0, 1000, 100));
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker(3, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker(3, 1000, 0));
    }

    @Test
    @DisplayName("Time until next attempt is correct")
    void timeUntilNextAttemptCorrect() throws InterruptedException {
        // When closed, should be 0
        assertEquals(0, circuitBreaker.getTimeUntilNextAttempt());

        // Trip circuit
        for (int i = 0; i < 3; i++) {
            circuitBreaker.recordFailure();
        }

        // Should have remaining time
        long remaining = circuitBreaker.getTimeUntilNextAttempt();
        assertTrue(remaining > 0 && remaining <= 50);

        // Wait for cooldown
        Thread.sleep(100);

        // Should be 0 again
        assertEquals(0, circuitBreaker.getTimeUntilNextAttempt());
    }
}
