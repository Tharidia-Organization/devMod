package com.frenkvs.devmod.ui.editor.systems;

import java.util.List;

/**
 * Result of a batch edit operation.
 */
public class BatchEditResult {
    private final List<String> successes;
    private final List<String> failures;

    public BatchEditResult(List<String> successes, List<String> failures) {
        this.successes = successes;
        this.failures = failures;
    }

    public int totalCount() {
        return successes.size() + failures.size();
    }

    public int successCount() {
        return successes.size();
    }

    public int failureCount() {
        return failures.size();
    }

    public boolean allSucceeded() {
        return failures.isEmpty();
    }

    public boolean allFailed() {
        return successes.isEmpty();
    }

    public List<String> successes() { return successes; }
    public List<String> failures() { return failures; }
}
