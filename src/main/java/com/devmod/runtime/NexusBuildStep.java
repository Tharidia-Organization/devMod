package com.devmod.runtime;

import java.util.Objects;

/**
 * Single staged build step for the Nexus hub.
 */
public record NexusBuildStep(String name, Runnable action) {
    public NexusBuildStep {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(action, "action");
    }

    public void run() {
        action.run();
    }
}
