package com.devmod.actions.domains;

import java.util.Objects;

import javax.annotation.Nullable;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.ActionResult;

/**
 * A handler that performs an action's side effects and reports whether they succeeded.
 *
 * <p>Contrast with the {@link java.util.function.Consumer} form accepted by
 * {@link HandlerRegistry#register}: a Consumer has no way to say "I did not do it",
 * so a delegating handler whose underlying call returns a failure signal must be
 * registered via {@link HandlerRegistry#registerResult} instead.
 */
@FunctionalInterface
public interface ActionHandler {

    /**
     * Runs the action.
     *
     * @param context the invocation context
     * @return the outcome; never null
     */
    Outcome handle(ActionContext context);

    /**
     * A handler that forwards to the V1 registry and reports what V1 decided.
     *
     * <p>Uses {@code invokeWithResult} rather than {@code invoke} so V1's own error
     * code and message survive: a discarded boolean is what let V2 log success for
     * a click V1 had refused.
     */
    static ActionHandler delegatingToV1(String actionId) {
        Objects.requireNonNull(actionId, "actionId");
        return context -> Outcome.of(ActionRegistry.invokeWithResult(actionId, context));
    }

    /**
     * A handler that runs a server command, reporting failure when the command is
     * rejected by {@code CommandSanitizer} or there is no command source to run it.
     */
    static ActionHandler runningCommand(String command) {
        Objects.requireNonNull(command, "command");
        return context -> context.executeCommand(command)
            ? Outcome.ok()
            // BLOCKED, not FAILED: the command was refused before running, so nothing
            // was applied.
            : Outcome.blocked(ActionResult.ERROR_COMMAND_REJECTED,
                "Command was not executed: " + command);
    }

    /**
     * What a handler reports back to the engine.
     *
     * <p>Deliberately carries no duration: only the executor knows how long the whole
     * invocation took, so it stamps the elapsed time when converting to an
     * {@link ActionResult}.
     *
     * @param status    OK, BLOCKED (refused before doing anything) or FAILED (tried
     *                  and did not complete). The distinction survives into telemetry,
     *                  so a refusal is not counted as a crash.
     * @param errorCode machine-readable failure code; null when successful
     * @param message   player-facing message, or null for no feedback
     */
    record Outcome(ActionResult.Status status, @Nullable String errorCode,
                   @Nullable String message) {

        private static final Outcome OK = new Outcome(ActionResult.Status.OK, null, null);

        public Outcome {
            Objects.requireNonNull(status, "status");
            if (status == ActionResult.Status.OK && errorCode != null) {
                throw new IllegalArgumentException("successful outcome cannot carry an errorCode");
            }
            if (status != ActionResult.Status.OK && errorCode == null) {
                throw new IllegalArgumentException("unsuccessful outcome requires an errorCode");
            }
        }

        /** Succeeded, with no message to show the player. */
        public static Outcome ok() {
            return OK;
        }

        /** Succeeded, showing the player the given message. */
        public static Outcome ok(String message) {
            return new Outcome(ActionResult.Status.OK, null,
                Objects.requireNonNull(message, "message"));
        }

        /** Refused before applying anything, showing the player the given message. */
        public static Outcome blocked(String errorCode, String message) {
            return new Outcome(ActionResult.Status.BLOCKED,
                Objects.requireNonNull(errorCode, "errorCode"),
                Objects.requireNonNull(message, "message"));
        }

        /** Attempted but did not complete, showing the player the given message. */
        public static Outcome failed(String errorCode, String message) {
            return new Outcome(ActionResult.Status.FAILED,
                Objects.requireNonNull(errorCode, "errorCode"),
                Objects.requireNonNull(message, "message"));
        }

        /**
         * Converts a delegated {@link ActionResult} into an outcome, preserving its
         * status, error code and message so a V1 refusal is not reported as a V2 crash.
         */
        public static Outcome of(ActionResult result) {
            Objects.requireNonNull(result, "result");
            if (result.isSuccess()) {
                return result.message() == null ? ok() : ok(result.message());
            }
            return new Outcome(result.status(),
                Objects.requireNonNullElse(result.errorCode(), ActionResult.ERROR_EXCEPTION),
                Objects.requireNonNullElse(result.message(), "Action failed"));
        }

        /** Returns true if the action's side effects were applied. */
        public boolean success() {
            return status == ActionResult.Status.OK;
        }

        /** Builds the engine-facing result, stamping the invocation's elapsed time. */
        public ActionResult toActionResult(long durationMs) {
            return new ActionResult(status, errorCode, message, durationMs);
        }
    }
}
