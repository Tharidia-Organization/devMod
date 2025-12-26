package com.devmod.telemetry.duckdb;

import java.sql.SQLException;

/**
 * Classifies SQL errors for intelligent error handling.
 *
 * Error types determine retry behavior:
 * - TRANSIENT: Temporary issues, retry with exponential backoff (up to 10 times)
 * - PERMANENT: Unrecoverable issues, circuit break immediately (after 2 errors)
 * - DEGRADATION: Load-related issues, reduce batch size and continue
 */
public final class DuckDBErrorClassifier {

    private DuckDBErrorClassifier() {} // Utility class

    /**
     * Error classification for different handling strategies.
     */
    public enum ErrorType {
        /**
         * Transient errors that may resolve with retry.
         * Examples: timeout, lock contention, temporary I/O errors, disk full
         */
        TRANSIENT,

        /**
         * Permanent errors that won't resolve with retry.
         * Examples: permission denied, schema mismatch, constraint violation
         */
        PERMANENT,

        /**
         * Degradation errors suggesting the system is under stress.
         * Examples: memory pressure, query too complex, unknown errors
         */
        DEGRADATION
    }

    /**
     * Classify a SQLException to determine appropriate handling strategy.
     *
     * @param e the SQLException to classify
     * @return the error type for handling decisions
     */
    public static ErrorType classify(SQLException e) {
        if (e == null) {
            return ErrorType.DEGRADATION;
        }

        String message = e.getMessage();
        if (message == null) {
            return ErrorType.DEGRADATION;
        }

        String msg = message.toLowerCase();
        String sqlState = e.getSQLState();

        // TRANSIENT - retry with exponential backoff
        if (isTransientError(msg, sqlState)) {
            return ErrorType.TRANSIENT;
        }

        // PERMANENT - circuit break quickly
        if (isPermanentError(msg, sqlState)) {
            return ErrorType.PERMANENT;
        }

        // Default to DEGRADATION - reduce load
        return ErrorType.DEGRADATION;
    }

    /**
     * Check if error is transient (may resolve with retry).
     */
    private static boolean isTransientError(String msg, String sqlState) {
        // Timeout errors
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return true;
        }

        // Lock contention
        if (msg.contains("lock") || msg.contains("busy") || msg.contains("blocked")) {
            return true;
        }

        // I/O errors (often transient)
        if (msg.contains("i/o error") || msg.contains("io error") || msg.contains("read error")) {
            return true;
        }

        // Disk space (might free up)
        if (msg.contains("disk full") || msg.contains("no space left")) {
            return true;
        }

        // Connection issues
        if (msg.contains("connection reset") || msg.contains("broken pipe") ||
            msg.contains("connection refused")) {
            return true;
        }

        // SQL states indicating transient issues
        if (sqlState != null) {
            // 08xxx = Connection exception
            if (sqlState.startsWith("08")) {
                return true;
            }
            // 40xxx = Transaction rollback (deadlock, etc)
            if (sqlState.startsWith("40")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if error is permanent (won't resolve with retry).
     */
    private static boolean isPermanentError(String msg, String sqlState) {
        // Permission errors
        if (msg.contains("permission denied") || msg.contains("access denied") ||
            msg.contains("not authorized")) {
            return true;
        }

        // Schema/structure errors
        if (msg.contains("no such table") || msg.contains("table not found") ||
            msg.contains("column not found") || msg.contains("no such column")) {
            return true;
        }

        // Schema mismatch
        if (msg.contains("schema") && (msg.contains("mismatch") || msg.contains("incompatible"))) {
            return true;
        }

        // Integrity/constraint violations
        if (msg.contains("integrity") || msg.contains("constraint violation") ||
            msg.contains("unique constraint") || msg.contains("foreign key")) {
            return true;
        }

        // Syntax errors (code bug, won't fix itself)
        if (msg.contains("syntax error") || msg.contains("parse error")) {
            return true;
        }

        // Corruption
        if (msg.contains("corrupt") || msg.contains("invalid database")) {
            return true;
        }

        // SQL states indicating permanent issues
        if (sqlState != null) {
            // 42xxx = Syntax error or access rule violation
            if (sqlState.startsWith("42")) {
                return true;
            }
            // 23xxx = Integrity constraint violation
            if (sqlState.startsWith("23")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if error indicates a lock contention issue.
     *
     * @param e the SQLException to check
     * @return true if the error is lock-related
     */
    public static boolean isLockError(SQLException e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }
        String msg = e.getMessage().toLowerCase();
        return msg.contains("lock") || msg.contains("busy") || msg.contains("blocked");
    }

    /**
     * Check if error indicates a timeout.
     *
     * @param e the SQLException to check
     * @return true if the error is timeout-related
     */
    public static boolean isTimeoutError(SQLException e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }
        String msg = e.getMessage().toLowerCase();
        return msg.contains("timeout") || msg.contains("timed out");
    }

    /**
     * Check if error indicates disk space issues.
     *
     * @param e the SQLException to check
     * @return true if the error is disk-space-related
     */
    public static boolean isDiskSpaceError(SQLException e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }
        String msg = e.getMessage().toLowerCase();
        return msg.contains("disk full") || msg.contains("no space left") || msg.contains("out of disk");
    }

    /**
     * Get a human-readable description of the error type.
     *
     * @param type the error type
     * @return description of handling strategy
     */
    public static String getHandlingDescription(ErrorType type) {
        return switch (type) {
            case TRANSIENT -> "Retry with exponential backoff (max 10 attempts)";
            case PERMANENT -> "Circuit break after 2 consecutive errors";
            case DEGRADATION -> "Reduce batch size and continue";
        };
    }
}
