package com.devmod.arena.failure;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class ArenaFailureHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaFailureHandler.class);

    /**
     * Player-facing messages for each failure type.
     * DD46: User-friendly, no tech details exposed.
     */
    private static final Map<FailureType, String> PLAYER_MESSAGES;

    static {
        Map<FailureType, String> messages = new EnumMap<>(FailureType.class);

        messages.put(FailureType.TEMPLATE_NOT_FOUND,
            "The selected arena is currently unavailable. Please try another arena.");
        messages.put(FailureType.TEMPLATE_INVALID,
            "This arena configuration has an issue. Please contact an administrator.");
        messages.put(FailureType.BUILD_FAILED,
            "Could not prepare the arena. Please try again in a few moments.");
        messages.put(FailureType.SPAWN_FAILED,
            "Could not find suitable spawn locations. Please try again.");
        messages.put(FailureType.TIMEOUT,
            "Arena setup is taking too long. Please try again.");
        messages.put(FailureType.CIRCUIT_OPEN,
            "Arena system is temporarily unavailable. Please wait a moment and try again.");
        messages.put(FailureType.RESOURCE_EXHAUSTED,
            "Server resources are limited. Please try again later.");
        messages.put(FailureType.NETWORK_ERROR,
            "Connection issue detected. Please check your connection and try again.");
        messages.put(FailureType.CONFIG_ERROR,
            "Arena configuration issue. Please contact an administrator.");
        messages.put(FailureType.INTERNAL_ERROR,
            "An unexpected error occurred. Please try again later.");
        messages.put(FailureType.PLAYER_COUNT_MISMATCH,
            "Player count does not match arena requirements.");
        messages.put(FailureType.MAP_UNAVAILABLE,
            "The selected map is not available. Please choose another.");
        messages.put(FailureType.PERMISSION_DENIED,
            "You do not have permission to access this arena.");
        messages.put(FailureType.ARENA_IN_USE,
            "This arena is currently in use. Please wait or choose another.");
        messages.put(FailureType.UNKNOWN,
            "Something went wrong. Please try again later.");

        PLAYER_MESSAGES = Collections.unmodifiableMap(messages);
    }

    private final CopyOnWriteArrayList<Consumer<FailureEvent>> alertHandlers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<FailureEvent>> failureListeners = new CopyOnWriteArrayList<>();

    /**
     * Get the player-facing message for a failure type.
     *
     * @param type The failure type
     * @return User-friendly message (never contains technical details)
     */
    public String getPlayerMessage(FailureType type) {
        return PLAYER_MESSAGES.getOrDefault(type, PLAYER_MESSAGES.get(FailureType.UNKNOWN));
    }

    /**
     * Get all player messages.
     */
    public Map<FailureType, String> getAllPlayerMessages() {
        return PLAYER_MESSAGES;
    }

    /**
     * Handle a failure with full logging and optional alerting.
     *
     * @param type The failure type
     * @param exception The exception that caused the failure (may be null)
     * @param context Additional context for logging
     * @return The player-facing message
     */
    public String handleFailure(FailureType type, Throwable exception, String context) {
        return handleFailure(type, exception, context, null);
    }

    /**
     * Handle a failure with full logging, optional alerting, and player notification.
     *
     * @param type The failure type
     * @param exception The exception that caused the failure (may be null)
     * @param context Additional context for logging
     * @param playerId The player ID for context (may be null)
     * @return The player-facing message
     */
    public String handleFailure(FailureType type, Throwable exception, String context, String playerId) {
        // Create failure event
        FailureEvent event = new FailureEvent(type, exception, context, playerId);

        // Log full details (with stack trace) for debugging
        logFailure(event);

        // Check if this is a critical failure that needs alerting
        if (isCriticalFailure(type)) {
            triggerAlert(event);
        }

        // Notify all failure listeners
        notifyListeners(event);

        // Return player-friendly message (no tech details)
        return getPlayerMessage(type);
    }

    /**
     * Handle a failure, returning a FailureResult with all information.
     */
    public FailureResult handleFailureWithResult(FailureType type, Throwable exception, String context) {
        String playerMessage = handleFailure(type, exception, context);
        return new FailureResult(type, playerMessage, context);
    }

    private void logFailure(FailureEvent event) {
        String logMessage = buildLogMessage(event);

        if (event.exception() != null) {
            // Log with full stack trace for debugging
            if (isCriticalFailure(event.type())) {
                LOGGER.error(logMessage, event.exception());
            } else {
                LOGGER.warn(logMessage, event.exception());
            }
        } else {
            if (isCriticalFailure(event.type())) {
                LOGGER.error(logMessage);
            } else {
                LOGGER.warn(logMessage);
            }
        }
    }

    private String buildLogMessage(FailureEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Arena failure: type=").append(event.type());

        if (event.playerId() != null) {
            sb.append(", player=").append(event.playerId());
        }

        if (event.context() != null) {
            sb.append(", context=").append(event.context());
        }

        if (event.exception() != null) {
            sb.append(", error=").append(event.exception().getMessage());
        }

        return sb.toString();
    }

    /**
     * Determine if a failure type is critical and requires alerting.
     */
    public boolean isCriticalFailure(FailureType type) {
        return switch (type) {
            case INTERNAL_ERROR, RESOURCE_EXHAUSTED, CONFIG_ERROR, CIRCUIT_OPEN -> true;
            default -> false;
        };
    }

    private void triggerAlert(FailureEvent event) {
        LOGGER.error("CRITICAL ALERT: {} failure detected. Context: {}",
            event.type(), event.context());

        for (Consumer<FailureEvent> handler : alertHandlers) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                LOGGER.error("Alert handler failed", e);
            }
        }
    }

    private void notifyListeners(FailureEvent event) {
        for (Consumer<FailureEvent> listener : failureListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOGGER.error("Failure listener threw exception", e);
            }
        }
    }

    /**
     * Register an alert handler for critical failures.
     */
    public void registerAlertHandler(Consumer<FailureEvent> handler) {
        if (handler != null) {
            alertHandlers.add(handler);
        }
    }

    /**
     * Remove an alert handler.
     */
    public void removeAlertHandler(Consumer<FailureEvent> handler) {
        alertHandlers.remove(handler);
    }

    /**
     * Register a listener for all failures.
     */
    public void registerFailureListener(Consumer<FailureEvent> listener) {
        if (listener != null) {
            failureListeners.add(listener);
        }
    }

    /**
     * Remove a failure listener.
     */
    public void removeFailureListener(Consumer<FailureEvent> listener) {
        failureListeners.remove(listener);
    }

    /**
     * Clear all handlers and listeners.
     */
    public void clearAll() {
        alertHandlers.clear();
        failureListeners.clear();
    }

    /**
     * Event record containing failure details.
     */
    public record FailureEvent(
        FailureType type,
        Throwable exception,
        String context,
        String playerId
    ) {
        /**
         * Get the exception if present.
         */
        public Optional<Throwable> getException() {
            return Optional.ofNullable(exception);
        }

        /**
         * Get stack trace as string, or empty string if no exception.
         */
        public String getStackTraceString() {
            if (exception == null) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(exception.getClass().getName()).append(": ").append(exception.getMessage()).append("\n");
            for (StackTraceElement element : exception.getStackTrace()) {
                sb.append("\tat ").append(element).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * Result record for failure handling.
     */
    public record FailureResult(
        FailureType type,
        String playerMessage,
        String context
    ) {}
}
