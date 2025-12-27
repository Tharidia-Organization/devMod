package com.devmod.arena.error;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record UserFriendlyError(
    /** Unique error ID for correlation in logs */
    UUID errorId,
    /** Error type for categorization */
    ErrorType type,
    /** User-friendly message (NO technical details) */
    String userMessage,
    /** Technical message for logs only (NOT shown to user) */
    String technicalMessage,
    /** Original exception if any */
    Optional<Throwable> cause,
    /** Additional context for debugging */
    Map<String, Object> context
) {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserFriendlyError.class);

    /**
     * Error types with default user messages.
     * DD46: User-friendly, no tech details.
     */
    public enum ErrorType {
        ARENA_BUILD_FAILED(
            "L'arena non è disponibile. Riprova più tardi.",
            true
        ),
        ARENA_TIMEOUT(
            "La preparazione dell'arena ha richiesto troppo tempo. Riprova.",
            false
        ),
        TEMPLATE_NOT_FOUND(
            "Il template richiesto non è disponibile.",
            false
        ),
        ARENA_FULL(
            "L'arena è piena. Attendi che si liberi un posto.",
            false
        ),
        PERMISSION_DENIED(
            "Non hai i permessi per questa azione.",
            false
        ),
        SERVICE_UNAVAILABLE(
            "Il servizio arena è temporaneamente non disponibile.",
            true
        ),
        INVALID_REQUEST(
            "La richiesta non è valida. Verifica i parametri.",
            false
        ),
        CONCURRENT_REQUEST(
            "Un'altra richiesta è in corso. Attendi un momento.",
            false
        ),
        RESOURCE_EXHAUSTED(
            "Risorse del server esaurite. Riprova più tardi.",
            true
        ),
        INTERNAL_ERROR(
            "Si è verificato un errore. Il team è stato notificato.",
            true
        );

        private final String defaultMessage;
        private final boolean critical;

        ErrorType(String defaultMessage, boolean critical) {
            this.defaultMessage = defaultMessage;
            this.critical = critical;
        }

        public String getDefaultMessage() {
            return defaultMessage;
        }

        public boolean isCritical() {
            return critical;
        }
    }

    /**
     * Creates a UserFriendlyError with auto-generated error ID.
     */
    public UserFriendlyError {
        if (errorId == null) {
            errorId = UUID.randomUUID();
        }
        if (userMessage == null) {
            userMessage = type.getDefaultMessage();
        }
        if (context == null) {
            context = Map.of();
        }
        if (cause == null) {
            cause = Optional.empty();
        }
    }

    /**
     * Builder for UserFriendlyError.
     */
    public static class Builder {
        private UUID errorId = UUID.randomUUID();
        private ErrorType type = ErrorType.INTERNAL_ERROR;
        private String userMessage;
        private String technicalMessage = "No technical details provided";
        private Throwable cause;
        private Map<String, Object> context = Map.of();

        public Builder type(ErrorType type) {
            this.type = type;
            this.userMessage = type.getDefaultMessage();
            return this;
        }

        public Builder userMessage(String message) {
            this.userMessage = message;
            return this;
        }

        public Builder technicalMessage(String message) {
            this.technicalMessage = message;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            if (this.technicalMessage.equals("No technical details provided") && cause != null) {
                this.technicalMessage = cause.getClass().getSimpleName() + ": " + cause.getMessage();
            }
            return this;
        }

        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public Builder errorId(UUID errorId) {
            this.errorId = errorId;
            return this;
        }

        public UserFriendlyError build() {
            return new UserFriendlyError(
                errorId,
                type,
                userMessage,
                technicalMessage,
                Optional.ofNullable(cause),
                context
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates from exception with automatic type detection.
     *
     * @param cause the exception
     * @return UserFriendlyError with appropriate type
     */
    public static UserFriendlyError fromException(Throwable cause) {
        ErrorType type = detectErrorType(cause);
        return builder()
            .type(type)
            .cause(cause)
            .build();
    }

    /**
     * Creates from exception with specified type.
     *
     * @param type  the error type
     * @param cause the exception
     * @return UserFriendlyError
     */
    public static UserFriendlyError fromException(ErrorType type, Throwable cause) {
        return builder()
            .type(type)
            .cause(cause)
            .build();
    }

    /**
     * Creates a simple error by type.
     *
     * @param type the error type
     * @return UserFriendlyError with default message
     */
    public static UserFriendlyError of(ErrorType type) {
        return builder()
            .type(type)
            .build();
    }

    /**
     * Creates a simple error with custom user message.
     *
     * @param type        the error type
     * @param userMessage custom user-friendly message
     * @return UserFriendlyError
     */
    public static UserFriendlyError of(ErrorType type, String userMessage) {
        return builder()
            .type(type)
            .userMessage(userMessage)
            .build();
    }

    /**
     * Detects error type from exception.
     * DD46: Map technical exceptions to user-friendly types.
     */
    private static ErrorType detectErrorType(Throwable cause) {
        String className = cause.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        String message = cause.getMessage() != null ? cause.getMessage().toLowerCase(Locale.ROOT) : "";

        if (className.contains("timeout") || message.contains("timeout")) {
            return ErrorType.ARENA_TIMEOUT;
        }
        if (className.contains("notfound") || message.contains("not found")) {
            return ErrorType.TEMPLATE_NOT_FOUND;
        }
        if (className.contains("permission") || message.contains("permission")) {
            return ErrorType.PERMISSION_DENIED;
        }
        if (className.contains("resource") || message.contains("exhausted")) {
            return ErrorType.RESOURCE_EXHAUSTED;
        }
        if (message.contains("concurrent") || message.contains("lock")) {
            return ErrorType.CONCURRENT_REQUEST;
        }
        if (cause instanceof IllegalArgumentException) {
            return ErrorType.INVALID_REQUEST;
        }

        return ErrorType.INTERNAL_ERROR;
    }

    /**
     * Logs the full error details including stack trace.
     * DD46: Stack trace only in logs, never to user.
     */
    public void logFull() {
        if (cause.isPresent()) {
            LOGGER.error("[{}] {} - Technical: {} - Context: {}",
                errorId, type, technicalMessage, context, cause.get());
        } else {
            LOGGER.error("[{}] {} - Technical: {} - Context: {}",
                errorId, type, technicalMessage, context);
        }
    }

    /**
     * Logs the error at appropriate level based on criticality.
     */
    public void log() {
        if (type.isCritical()) {
            logFull();
        } else {
            LOGGER.warn("[{}] {} - Technical: {}", errorId, type, technicalMessage);
        }
    }

    /**
     * Gets the message to show to users.
     * DD46: NEVER contains technical details or stack traces.
     *
     * @return user-friendly message
     */
    public String getPlayerMessage() {
        return userMessage;
    }

    /**
     * Gets the message with error ID for support reference.
     *
     * @return message with error ID
     */
    public String getPlayerMessageWithRef() {
        return userMessage + " (Ref: " + errorId.toString().substring(0, 8) + ")";
    }

    /**
     * Checks if this is a critical error requiring alert.
     *
     * @return true if critical
     */
    public boolean isCritical() {
        return type.isCritical();
    }

    /**
     * Gets a short reference ID for support.
     *
     * @return first 8 chars of error ID
     */
    public String getShortRef() {
        return errorId.toString().substring(0, 8);
    }

    /**
     * Converts to telemetry-safe map (no stack traces).
     *
     * @return map for telemetry
     */
    public Map<String, Object> toTelemetryMap() {
        return Map.of(
            "errorId", errorId.toString(),
            "type", type.name(),
            "critical", type.isCritical(),
            "context", context
        );
    }

    /**
     * Converts to log-safe map (includes technical details).
     *
     * @return map for logging
     */
    public Map<String, Object> toLogMap() {
        return Map.of(
            "errorId", errorId.toString(),
            "type", type.name(),
            "userMessage", userMessage,
            "technicalMessage", technicalMessage,
            "critical", type.isCritical(),
            "context", context,
            "hasCause", cause.isPresent()
        );
    }
}
