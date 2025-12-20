package com.devmod.arena.alert;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable error context record for structured error logging (DD18).
 * Captures stacktrace as JSON array with max 20 frames.
 */
public record ErrorContext(
    UUID errorId,
    Instant timestamp,
    String errorType,
    String message,
    List<StackFrame> stackFrames,
    Severity severity,
    String component,
    Map<String, Object> metadata
) {

    /**
     * Maximum number of stack frames to retain (DD18).
     */
    public static final int MAX_STACK_FRAMES = 20;

    /**
     * Error severity levels for alert routing.
     */
    public enum Severity {
        DEBUG(0),
        INFO(1),
        WARNING(2),
        ERROR(3),
        CRITICAL(4);

        private final int level;

        Severity(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }

        public boolean isAtLeast(Severity other) {
            return this.level >= other.level;
        }
    }

    /**
     * Immutable representation of a single stack frame.
     */
    public record StackFrame(
        String className,
        String methodName,
        String fileName,
        int lineNumber,
        boolean isNativeMethod
    ) {
        /**
         * Creates a StackFrame from a Java StackTraceElement.
         */
        public static StackFrame from(StackTraceElement element) {
            return new StackFrame(
                element.getClassName(),
                element.getMethodName(),
                element.getFileName(),
                element.getLineNumber(),
                element.isNativeMethod()
            );
        }

        /**
         * Converts to JSON-compatible map.
         */
        public Map<String, Object> toMap() {
            return Map.of(
                "className", className != null ? className : "unknown",
                "methodName", methodName != null ? methodName : "unknown",
                "fileName", fileName != null ? fileName : "unknown",
                "lineNumber", lineNumber,
                "isNativeMethod", isNativeMethod
            );
        }
    }

    /**
     * Compact constructor with validation and frame limiting.
     */
    public ErrorContext {
        Objects.requireNonNull(errorId, "errorId must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(errorType, "errorType must not be null");
        Objects.requireNonNull(severity, "severity must not be null");

        // Limit stack frames to MAX_STACK_FRAMES (DD18)
        if (stackFrames != null && stackFrames.size() > MAX_STACK_FRAMES) {
            stackFrames = Collections.unmodifiableList(
                stackFrames.subList(0, MAX_STACK_FRAMES)
            );
        } else if (stackFrames != null) {
            stackFrames = Collections.unmodifiableList(List.copyOf(stackFrames));
        } else {
            stackFrames = Collections.emptyList();
        }

        // Defensive copy of metadata
        metadata = metadata != null
            ? Collections.unmodifiableMap(Map.copyOf(metadata))
            : Collections.emptyMap();
    }

    /**
     * Creates an ErrorContext from a Throwable.
     */
    public static ErrorContext fromThrowable(
            Throwable throwable,
            Severity severity,
            String component,
            Map<String, Object> metadata) {

        List<StackFrame> frames = Arrays.stream(throwable.getStackTrace())
            .limit(MAX_STACK_FRAMES)
            .map(StackFrame::from)
            .toList();

        return new ErrorContext(
            UUID.randomUUID(),
            Instant.now(),
            throwable.getClass().getName(),
            throwable.getMessage(),
            frames,
            severity,
            component,
            metadata
        );
    }

    /**
     * Creates an ErrorContext from a Throwable with default metadata.
     */
    public static ErrorContext fromThrowable(
            Throwable throwable,
            Severity severity,
            String component) {
        return fromThrowable(throwable, severity, component, Collections.emptyMap());
    }

    /**
     * Creates a builder for flexible ErrorContext creation.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Checks if this error is critical (requires retry on failure).
     */
    public boolean isCritical() {
        return severity == Severity.CRITICAL || severity == Severity.ERROR;
    }

    /**
     * Converts stack frames to JSON-compatible list.
     */
    public List<Map<String, Object>> stackFramesToJson() {
        return stackFrames.stream()
            .map(StackFrame::toMap)
            .toList();
    }

    /**
     * Converts the entire context to a JSON-compatible map.
     */
    public Map<String, Object> toMap() {
        return Map.of(
            "errorId", errorId.toString(),
            "timestamp", timestamp.toString(),
            "errorType", errorType,
            "message", message != null ? message : "",
            "stackFrames", stackFramesToJson(),
            "severity", severity.name(),
            "component", component != null ? component : "unknown",
            "metadata", metadata
        );
    }

    /**
     * Builder for ErrorContext.
     */
    public static class Builder {
        private UUID errorId;
        private Instant timestamp;
        private String errorType;
        private String message;
        private List<StackFrame> stackFrames;
        private Severity severity = Severity.ERROR;
        private String component;
        private Map<String, Object> metadata;

        public Builder errorId(UUID errorId) {
            this.errorId = errorId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder stackFrames(List<StackFrame> stackFrames) {
            this.stackFrames = stackFrames;
            return this;
        }

        public Builder fromThrowable(Throwable throwable) {
            this.errorType = throwable.getClass().getName();
            this.message = throwable.getMessage();
            this.stackFrames = Arrays.stream(throwable.getStackTrace())
                .limit(MAX_STACK_FRAMES)
                .map(StackFrame::from)
                .toList();
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder component(String component) {
            this.component = component;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = new java.util.HashMap<>();
            } else if (!(this.metadata instanceof java.util.HashMap)) {
                this.metadata = new java.util.HashMap<>(this.metadata);
            }
            this.metadata.put(key, value);
            return this;
        }

        public ErrorContext build() {
            if (errorId == null) {
                errorId = UUID.randomUUID();
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
            return new ErrorContext(
                errorId,
                timestamp,
                errorType,
                message,
                stackFrames,
                severity,
                component,
                metadata
            );
        }
    }
}
