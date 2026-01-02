package com.devmod.exception;

import java.io.Serial;

import javax.annotation.Nullable;

/**
 * P1-012: Base exception hierarchy for DevMod.
 *
 * Provides structured exception types for:
 * <ul>
 *   <li>Better error categorization and handling</li>
 *   <li>Consistent error messages</li>
 *   <li>Error codes for logging and debugging</li>
 *   <li>Context preservation for troubleshooting</li>
 * </ul>
 *
 * <p>Exception Hierarchy:
 * <pre>
 * DevModException (base)
 * ├── ArenaException
 * │   ├── TemplateException
 * │   ├── BuildException
 * │   └── InstanceException
 * ├── QuestException
 * │   ├── WaveException
 * │   └── RewardException
 * ├── NetworkException
 * │   ├── PayloadException
 * │   └── RateLimitException
 * ├── ConfigException
 * │   ├── ValidationException
 * │   └── PersistenceException
 * └── SecurityException
 *     ├── PermissionException
 *     └── AuthenticationException
 * </pre>
 */
public class DevModException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    @Nullable
    private final String context;

    public DevModException(ErrorCode errorCode, String message) {
        super(formatMessage(errorCode, message));
        this.errorCode = errorCode;
        this.context = null;
    }

    public DevModException(ErrorCode errorCode, String message, @Nullable String context) {
        super(formatMessage(errorCode, message));
        this.errorCode = errorCode;
        this.context = context;
    }

    public DevModException(ErrorCode errorCode, String message, Throwable cause) {
        super(formatMessage(errorCode, message), cause);
        this.errorCode = errorCode;
        this.context = null;
    }

    public DevModException(ErrorCode errorCode, String message, @Nullable String context, Throwable cause) {
        super(formatMessage(errorCode, message), cause);
        this.errorCode = errorCode;
        this.context = context;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    @Nullable
    public String getContext() {
        return context;
    }

    public boolean isRecoverable() {
        return errorCode.isRecoverable();
    }

    private static String formatMessage(ErrorCode code, String message) {
        return String.format("[%s] %s", code.getCode(), message);
    }

    // ============================================================================
    // ERROR CODES
    // ============================================================================

    /**
     * Error codes for categorization and logging.
     */
    public enum ErrorCode {
        // General (1xxx)
        UNKNOWN("DM-1000", "Unknown error", true),
        INTERNAL("DM-1001", "Internal error", false),
        NOT_IMPLEMENTED("DM-1002", "Feature not implemented", false),

        // Arena (2xxx)
        ARENA_NOT_FOUND("DM-2000", "Arena not found", true),
        TEMPLATE_NOT_FOUND("DM-2001", "Template not found", true),
        TEMPLATE_INVALID("DM-2002", "Template validation failed", true),
        TEMPLATE_LOAD_FAILED("DM-2003", "Template loading failed", true),
        BUILD_FAILED("DM-2100", "Arena build failed", true),
        BUILD_TIMEOUT("DM-2101", "Arena build timed out", true),
        BUILD_CANCELLED("DM-2102", "Arena build cancelled", true),
        INSTANCE_NOT_FOUND("DM-2200", "Instance not found", true),
        INSTANCE_FULL("DM-2201", "Instance at capacity", true),
        INSTANCE_LOCKED("DM-2202", "Instance is locked", true),

        // Quest (3xxx)
        QUEST_NOT_FOUND("DM-3000", "Quest not found", true),
        QUEST_NOT_ACTIVE("DM-3001", "No active quest", true),
        QUEST_ALREADY_ACTIVE("DM-3002", "Quest already active", true),
        WAVE_SPAWN_FAILED("DM-3100", "Wave spawn failed", true),
        WAVE_INVALID_STATE("DM-3101", "Invalid wave state", false),
        REWARD_CALCULATION_FAILED("DM-3200", "Reward calculation failed", true),
        REWARD_GRANT_FAILED("DM-3201", "Reward grant failed", true),

        // Network (4xxx)
        PAYLOAD_INVALID("DM-4000", "Invalid payload", true),
        PAYLOAD_TOO_LARGE("DM-4001", "Payload too large", true),
        RATE_LIMITED("DM-4100", "Rate limit exceeded", true),
        CONNECTION_FAILED("DM-4200", "Connection failed", true),

        // Config (5xxx)
        CONFIG_NOT_FOUND("DM-5000", "Configuration not found", true),
        CONFIG_INVALID("DM-5001", "Configuration invalid", true),
        CONFIG_SAVE_FAILED("DM-5100", "Configuration save failed", true),
        CONFIG_LOAD_FAILED("DM-5101", "Configuration load failed", true),

        // Security (6xxx)
        PERMISSION_DENIED("DM-6000", "Permission denied", false),
        AUTH_FAILED("DM-6001", "Authentication failed", false),
        TOKEN_INVALID("DM-6002", "Invalid token", false),
        TOKEN_EXPIRED("DM-6003", "Token expired", true),
        ESCALATION_BLOCKED("DM-6100", "Permission escalation blocked", false),

        // Database (7xxx)
        DB_CONNECTION_FAILED("DM-7000", "Database connection failed", true),
        DB_QUERY_FAILED("DM-7001", "Database query failed", true),
        DB_TIMEOUT("DM-7002", "Database timeout", true),
        DB_CONSTRAINT_VIOLATION("DM-7003", "Database constraint violation", false);

        private final String code;
        private final String description;
        private final boolean recoverable;

        ErrorCode(String code, String description, boolean recoverable) {
            this.code = code;
            this.description = description;
            this.recoverable = recoverable;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public boolean isRecoverable() {
            return recoverable;
        }
    }

    // ============================================================================
    // ARENA EXCEPTIONS
    // ============================================================================

    /**
     * Base exception for arena-related errors.
     */
    public static class ArenaException extends DevModException {
        @Serial private static final long serialVersionUID = 1L;

        public ArenaException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }

        public ArenaException(ErrorCode errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }

        public ArenaException(ErrorCode errorCode, String message, String context) {
            super(errorCode, message, context);
        }
    }

    /**
     * Exception for template-related errors.
     */
    public static class TemplateException extends ArenaException {
        @Serial private static final long serialVersionUID = 1L;
        @Nullable private final String templateId;

        public TemplateException(ErrorCode errorCode, String templateId, String message) {
            super(errorCode, message, "template=" + templateId);
            this.templateId = templateId;
        }

        public TemplateException(ErrorCode errorCode, String templateId, String message, Throwable cause) {
            super(errorCode, message, cause);
            this.templateId = templateId;
        }

        @Nullable
        public String getTemplateId() {
            return templateId;
        }
    }

    /**
     * Exception for build-related errors.
     */
    public static class BuildException extends ArenaException {
        @Serial private static final long serialVersionUID = 1L;

        public BuildException(String message) {
            super(ErrorCode.BUILD_FAILED, message);
        }

        public BuildException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }

        public BuildException(String message, Throwable cause) {
            super(ErrorCode.BUILD_FAILED, message, cause);
        }
    }

    /**
     * Exception for instance-related errors.
     */
    public static class InstanceException extends ArenaException {
        @Serial private static final long serialVersionUID = 1L;

        public InstanceException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }
    }

    // ============================================================================
    // QUEST EXCEPTIONS
    // ============================================================================

    /**
     * Base exception for quest-related errors.
     */
    public static class QuestException extends DevModException {
        @Serial private static final long serialVersionUID = 1L;

        public QuestException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }

        public QuestException(ErrorCode errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }
    }

    /**
     * Exception for wave-related errors.
     */
    public static class WaveException extends QuestException {
        @Serial private static final long serialVersionUID = 1L;
        private final int waveNumber;

        public WaveException(int waveNumber, String message) {
            super(ErrorCode.WAVE_SPAWN_FAILED, message + " (wave=" + waveNumber + ")");
            this.waveNumber = waveNumber;
        }

        public int getWaveNumber() {
            return waveNumber;
        }
    }

    /**
     * Exception for reward-related errors.
     */
    public static class RewardException extends QuestException {
        @Serial private static final long serialVersionUID = 1L;

        public RewardException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }
    }

    // ============================================================================
    // NETWORK EXCEPTIONS
    // ============================================================================

    /**
     * Base exception for network-related errors.
     */
    public static class NetworkException extends DevModException {
        @Serial private static final long serialVersionUID = 1L;

        public NetworkException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }
    }

    /**
     * Exception for payload validation errors.
     */
    public static class PayloadException extends NetworkException {
        @Serial private static final long serialVersionUID = 1L;

        public PayloadException(String message) {
            super(ErrorCode.PAYLOAD_INVALID, message);
        }

        public PayloadException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }
    }

    /**
     * Exception for rate limiting.
     */
    public static class RateLimitException extends NetworkException {
        @Serial private static final long serialVersionUID = 1L;
        private final long retryAfterMs;

        public RateLimitException(long retryAfterMs) {
            super(ErrorCode.RATE_LIMITED, "Rate limit exceeded, retry after " + retryAfterMs + "ms");
            this.retryAfterMs = retryAfterMs;
        }

        public long getRetryAfterMs() {
            return retryAfterMs;
        }
    }

    // ============================================================================
    // CONFIG EXCEPTIONS
    // ============================================================================

    /**
     * Base exception for configuration errors.
     */
    public static class ConfigException extends DevModException {
        @Serial private static final long serialVersionUID = 1L;

        public ConfigException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }

        public ConfigException(ErrorCode errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }
    }

    /**
     * Exception for validation errors.
     */
    public static class ValidationException extends ConfigException {
        @Serial private static final long serialVersionUID = 1L;
        @Nullable private final String field;

        public ValidationException(String field, String message) {
            super(ErrorCode.CONFIG_INVALID, message + " (field=" + field + ")");
            this.field = field;
        }

        @Nullable
        public String getField() {
            return field;
        }
    }

    /**
     * Exception for persistence errors.
     */
    public static class PersistenceException extends ConfigException {
        @Serial private static final long serialVersionUID = 1L;

        public PersistenceException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }

        public PersistenceException(ErrorCode errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }
    }

    // ============================================================================
    // SECURITY EXCEPTIONS
    // ============================================================================

    /**
     * Base exception for security-related errors.
     */
    public static class DevModSecurityException extends DevModException {
        @Serial private static final long serialVersionUID = 1L;

        public DevModSecurityException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }
    }

    /**
     * Exception for permission errors.
     */
    public static class PermissionException extends DevModSecurityException {
        @Serial private static final long serialVersionUID = 1L;
        @Nullable private final String permission;

        public PermissionException(String permission) {
            super(ErrorCode.PERMISSION_DENIED, "Permission denied: " + permission);
            this.permission = permission;
        }

        @Nullable
        public String getPermission() {
            return permission;
        }
    }

    /**
     * Exception for authentication errors.
     */
    public static class AuthenticationException extends DevModSecurityException {
        @Serial private static final long serialVersionUID = 1L;

        public AuthenticationException(String message) {
            super(ErrorCode.AUTH_FAILED, message);
        }

        public AuthenticationException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }
    }

    // ============================================================================
    // DATABASE EXCEPTIONS
    // ============================================================================

    /**
     * Exception for database errors.
     */
    public static class DatabaseException extends DevModException {
        @Serial private static final long serialVersionUID = 1L;

        public DatabaseException(ErrorCode errorCode, String message) {
            super(errorCode, message);
        }

        public DatabaseException(ErrorCode errorCode, String message, Throwable cause) {
            super(errorCode, message, cause);
        }
    }
}
