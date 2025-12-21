package com.frenkvs.devmod.actions;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Result of executing an action through ActionRegistry.
 * Provides structured information about success, failure, or blocking.
 */
public record ActionResult(
    Status status,
    @Nullable String errorCode,
    @Nullable String message,
    long durationMs
) {
    /**
     * Status of action execution.
     */
    public enum Status {
        /** Action executed successfully */
        OK,
        /** Action was blocked by precondition or permission check */
        BLOCKED,
        /** Action failed during execution (exception or error) */
        FAILED
    }

    // Common error codes
    public static final String ERROR_UNKNOWN_ACTION = "UNKNOWN_ACTION";
    public static final String ERROR_PRECONDITION_FAILED = "PRECONDITION_FAILED";
    public static final String ERROR_PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String ERROR_REQUIRES_CONFIRM = "REQUIRES_CONFIRM";
    public static final String ERROR_EXCEPTION = "EXCEPTION";
    public static final String ERROR_RPC_FAILED = "RPC_FAILED";
    public static final String ERROR_DESKTOP_UNSUPPORTED = "DESKTOP_UNSUPPORTED";
    public static final String ERROR_URL_UNKNOWN = "URL_UNKNOWN";

    /**
     * Creates a successful result.
     */
    public static ActionResult ok(long durationMs) {
        return new ActionResult(Status.OK, null, null, durationMs);
    }

    /**
     * Creates a successful result with a message.
     */
    public static ActionResult ok(long durationMs, String message) {
        return new ActionResult(Status.OK, null, message, durationMs);
    }

    /**
     * Creates a blocked result (precondition or permission failure).
     */
    public static ActionResult blocked(String errorCode, String message) {
        return new ActionResult(Status.BLOCKED,
            Objects.requireNonNull(errorCode, "errorCode"),
            message,
            0);
    }

    /**
     * Creates a failed result (execution error).
     */
    public static ActionResult failed(String errorCode, String message, long durationMs) {
        return new ActionResult(Status.FAILED,
            Objects.requireNonNull(errorCode, "errorCode"),
            message,
            durationMs);
    }

    /**
     * Returns true if the action completed successfully.
     */
    public boolean isSuccess() {
        return status == Status.OK;
    }

    /**
     * Returns true if the action was blocked before execution.
     */
    public boolean isBlocked() {
        return status == Status.BLOCKED;
    }

    /**
     * Returns true if the action failed during execution.
     */
    public boolean isFailed() {
        return status == Status.FAILED;
    }
}
