package com.devmod.npc.dialog.condition;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

/**
 * Result of evaluating a dialog condition in debug mode.
 * Contains the evaluation result and a human-readable reason explaining why.
 *
 * <p>For composite conditions (And, Or, Not), the children list contains
 * the debug results of the nested conditions.
 */
public record ConditionDebugResult(
    @Nonnull DialogCondition condition,
    boolean passed,
    @Nonnull String reason,
    @Nonnull List<ConditionDebugResult> children
) {
    public ConditionDebugResult {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(children, "children");
    }

    /**
     * Creates a simple debug result with no children.
     */
    @Nonnull
    public static ConditionDebugResult simple(
        @Nonnull DialogCondition condition,
        boolean passed,
        @Nonnull String reason
    ) {
        return new ConditionDebugResult(condition, passed, reason, List.of());
    }

    /**
     * Creates a debug result with children (for composite conditions).
     */
    @Nonnull
    public static ConditionDebugResult composite(
        @Nonnull DialogCondition condition,
        boolean passed,
        @Nonnull String reason,
        @Nonnull List<ConditionDebugResult> children
    ) {
        return new ConditionDebugResult(condition, passed, reason, List.copyOf(children));
    }

    /**
     * Gets a display string for the condition type.
     */
    @Nonnull
    public String getConditionType() {
        return condition.getType();
    }

    /**
     * Returns true if this is a composite condition with children.
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }

    /**
     * Gets the status icon for display.
     */
    @Nonnull
    public String getStatusIcon() {
        return passed ? "✅" : "❌";
    }

    /**
     * Gets a formatted summary for display.
     */
    @Nonnull
    public String getSummary() {
        return getStatusIcon() + " " + getConditionType() + ": " + reason;
    }
}
