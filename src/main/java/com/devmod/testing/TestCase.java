package com.devmod.testing;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import com.devmod.ui.hub.ToolType;

/**
 * Represents a single test case in the QA Testing Framework.
 * Similar to Minecraft achievements but for mod functionality testing.
 * Supports serialization for session persistence.
 *
 * Supports two types of auto-completion:
 * 1. Boolean autoValidator (legacy) - simple pass/fail check
 * 2. Progress-based progressChecker - returns 0.0 to 1.0 for incremental progress
 */
public class TestCase {

    private final String id;
    private final String category;
    private final String name;
    private final String description;
    private final String instructions;
    private final TestPriority priority;
    private final Supplier<Boolean> autoValidator;

    // NEW: Progress-based checker for incremental test completion
    // Returns a float from 0.0 (not started) to 1.0 (complete)
    private final Function<TesterProgress, Float> progressChecker;

    private TestStatus status = TestStatus.PENDING;
    private String testerComment = "";
    private Instant completedAt;
    private String errorLog;
    private boolean hasError = false;

    // Tracks if auto-validator ever passed (for persistent detection)
    private boolean autoValidatedOnce = false;

    // Cached progress value for UI display
    private float cachedProgress = 0f;

    public enum TestStatus {
        PENDING("Pending", 0xFF888888),
        IN_PROGRESS("In Progress", 0xFFFFAA00),
        PASSED("Passed", 0xFF55FF55),
        FAILED("Failed", 0xFFFF5555),
        SKIPPED("Skipped", 0xFF888888);

        private final String displayName;
        private final int color;

        TestStatus(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public int getColor() { return color; }
    }

    public enum TestPriority {
        CRITICAL("Critical", 0xFFFF0000, 1),
        HIGH("High", 0xFFFF8800, 2),
        MEDIUM("Medium", 0xFFFFFF00, 3),
        LOW("Low", 0xFF88FF88, 4);

        private final String displayName;
        private final int color;
        private final int order;

        TestPriority(String displayName, int color, int order) {
            this.displayName = displayName;
            this.color = color;
            this.order = order;
        }

        public String getDisplayName() { return displayName; }
        public int getColor() { return color; }
        public int getOrder() { return order; }
    }

    public TestCase(String id, String category, String name, String description,
                    String instructions, TestPriority priority) {
        this(id, category, name, description, instructions, priority, null, null);
    }

    public TestCase(String id, String category, String name, String description,
                    String instructions, TestPriority priority, Supplier<Boolean> autoValidator) {
        this(id, category, name, description, instructions, priority, autoValidator, null);
    }

    /**
     * Full constructor with progress checker support.
     */
    public TestCase(String id, String category, String name, String description,
                    String instructions, TestPriority priority,
                    Supplier<Boolean> autoValidator,
                    Function<TesterProgress, Float> progressChecker) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.description = description;
        this.instructions = instructions;
        this.priority = priority;
        this.autoValidator = autoValidator;
        this.progressChecker = progressChecker;
    }

    /**
     * Constructor with only progress checker (no legacy validator).
     */
    public static TestCase withProgress(String id, String category, String name, String description,
                                         String instructions, TestPriority priority,
                                         Function<TesterProgress, Float> progressChecker) {
        return new TestCase(id, category, name, description, instructions, priority, null, progressChecker);
    }

    // === Status Management ===

    public void startTest() {
        if (status == TestStatus.PENDING) {
            status = TestStatus.IN_PROGRESS;
        }
    }

    public void markPassed(String comment) {
        status = TestStatus.PASSED;
        testerComment = comment;
        completedAt = Instant.now();
        hasError = false;
    }

    public void markFailed(String comment, String errorLog) {
        status = TestStatus.FAILED;
        testerComment = comment;
        this.errorLog = errorLog;
        completedAt = Instant.now();
        hasError = true;
    }

    public void markSkipped(String reason) {
        status = TestStatus.SKIPPED;
        testerComment = reason;
        completedAt = Instant.now();
    }

    public void reset() {
        status = TestStatus.PENDING;
        testerComment = "";
        completedAt = null;
        errorLog = null;
        hasError = false;
        autoValidatedOnce = false;
    }

    /**
     * Restore state from saved session data.
     */
    public void restoreState(TestStatus savedStatus, String comment, Instant completed, String error, boolean validated) {
        this.status = savedStatus;
        this.testerComment = comment != null ? comment : "";
        this.completedAt = completed;
        this.errorLog = error;
        this.hasError = savedStatus == TestStatus.FAILED;
        this.autoValidatedOnce = validated;
    }

    /**
     * Check if this test was auto-validated at least once.
     */
    public boolean wasAutoValidated() {
        return autoValidatedOnce;
    }

    /**
     * Attempts auto-validation if validator is available.
     * @return true if passed, false if failed, null if no validator
     */
    public Boolean tryAutoValidate() {
        if (autoValidator == null) return null;
        try {
            return autoValidator.get();
        } catch (Exception e) {
            errorLog = "Auto-validation error: " + e.getMessage();
            return false;
        }
    }

    // === Getters ===

    public String getId() { return id; }
    public String getCategory() { return category; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getInstructions() { return instructions; }
    public TestPriority getPriority() { return priority; }
    public TestStatus getStatus() { return status; }
    public String getTesterComment() { return testerComment; }
    public String getComments() { return testerComment; } // Alias for UI
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorLog() { return errorLog; }
    public boolean hasError() { return hasError; }
    public boolean hasAutoValidator() { return autoValidator != null; }
    public boolean hasProgressChecker() { return progressChecker != null; }
    public float getCachedProgress() { return cachedProgress; }

    /**
     * Get the required tools for this test case.
     * Override in subclasses or use the builder to specify required tools.
     * Default: no tools required.
     */
    public Set<ToolType> getRequiredTools() {
        return EnumSet.noneOf(ToolType.class);
    }

    /**
     * Get current progress from the progress checker (0.0 - 1.0).
     * Updates the cached value for UI display.
     */
    public float getProgress(TesterProgress progress) {
        if (status == TestStatus.PASSED) return 1.0f;
        if (status == TestStatus.FAILED || status == TestStatus.SKIPPED) return cachedProgress;

        if (progressChecker != null) {
            try {
                cachedProgress = Math.min(1.0f, Math.max(0.0f, progressChecker.apply(progress)));
            } catch (Exception e) {
                // Keep previous cached value on error
            }
        } else if (autoValidator != null) {
            // Legacy: boolean validator = 0 or 1
            try {
                cachedProgress = autoValidator.get() ? 1.0f : 0.0f;
            } catch (Exception e) {
                // Keep previous cached value
            }
        }

        return cachedProgress;
    }

    /**
     * Check if test should auto-complete based on progress.
     * Returns true if test was just completed.
     */
    public boolean checkAutoComplete(TesterProgress progress) {
        if (status == TestStatus.PASSED || status == TestStatus.FAILED || status == TestStatus.SKIPPED) {
            return false;
        }

        float currentProgress = getProgress(progress);
        if (currentProgress >= 1.0f) {
            autoValidatedOnce = true;
            markPassed("Auto-completed via gameplay");
            return true;
        }

        // Auto-start test if progress > 0
        if (currentProgress > 0 && status == TestStatus.PENDING) {
            startTest();
        }

        return false;
    }

    /** Alias for markSkipped - more intuitive name for UI */
    public void skip(String reason) {
        markSkipped(reason);
    }

    /**
     * Runs auto-validation and updates status accordingly.
     * @return true if test passed, false otherwise
     */
    public boolean runAutoValidation() {
        Boolean result = tryAutoValidate();
        if (result == null) {
            return false; // No validator
        }
        if (result) {
            autoValidatedOnce = true;
            markPassed("Auto-validated");
            return true;
        } else {
            markFailed("Auto-validation failed", errorLog != null ? errorLog : "Condition not met");
            return false;
        }
    }

    /**
     * Check auto-validator without changing status (for background polling).
     * @return true if validator passes, false otherwise
     */
    public boolean checkAutoValidator() {
        if (autoValidator == null) return false;
        try {
            boolean result = autoValidator.get();
            if (result) {
                autoValidatedOnce = true;
            }
            return result;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generates a report section for this test case.
     */
    public String toReportString() {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(name).append("\n");
        sb.append("- **ID**: ").append(id).append("\n");
        sb.append("- **Category**: ").append(category).append("\n");
        sb.append("- **Priority**: ").append(priority.getDisplayName()).append("\n");
        sb.append("- **Status**: ").append(status.getDisplayName()).append("\n");
        if (completedAt != null) {
            sb.append("- **Completed**: ").append(completedAt).append("\n");
        }
        if (!testerComment.isEmpty()) {
            sb.append("- **Tester Comment**: ").append(testerComment).append("\n");
        }
        if (errorLog != null && !errorLog.isEmpty()) {
            sb.append("- **Error Log**:\n```\n").append(errorLog).append("\n```\n");
        }
        sb.append("\n");
        return sb.toString();
    }
}
