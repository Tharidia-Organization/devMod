package com.devmod.runtime;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NexusBuildStepTest {

    @Test
    @DisplayName("NexusBuildStep stores name and action")
    void storesNameAndAction() {
        Runnable action = () -> {};
        NexusBuildStep step = new NexusBuildStep("test_step", action);

        assertEquals("test_step", step.name());
        assertSame(action, step.action());
    }

    @Test
    @DisplayName("run() executes the action")
    void runExecutesAction() {
        AtomicBoolean executed = new AtomicBoolean(false);
        NexusBuildStep step = new NexusBuildStep("build", () -> executed.set(true));

        step.run();
        assertTrue(executed.get());
    }

    @Test
    @DisplayName("null name throws NullPointerException")
    void nullNameThrows() {
        assertThrows(NullPointerException.class, () -> new NexusBuildStep(null, () -> {}));
    }

    @Test
    @DisplayName("null action throws NullPointerException")
    void nullActionThrows() {
        assertThrows(NullPointerException.class, () -> new NexusBuildStep("step", null));
    }
}
