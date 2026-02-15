package com.devmod.telemetry.duckdb;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.telemetry.duckdb.DuckDBErrorClassifier.ErrorType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DuckDBErrorClassifier")
class DuckDBErrorClassifierTest {

    // ============================================
    // classify() - TRANSIENT errors
    // ============================================

    @Test
    @DisplayName("classify: timeout error is TRANSIENT")
    void classifyTimeoutAsTransient() {
        assertEquals(ErrorType.TRANSIENT,
            DuckDBErrorClassifier.classify(new SQLException("Query timed out after 30s")));
    }

    @Test
    @DisplayName("classify: lock contention error is TRANSIENT")
    void classifyLockAsTransient() {
        assertEquals(ErrorType.TRANSIENT,
            DuckDBErrorClassifier.classify(new SQLException("Database file is locked")));
    }

    @Test
    @DisplayName("classify: busy error is TRANSIENT")
    void classifyBusyAsTransient() {
        assertEquals(ErrorType.TRANSIENT,
            DuckDBErrorClassifier.classify(new SQLException("Database is busy")));
    }

    @Test
    @DisplayName("classify: I/O error is TRANSIENT")
    void classifyIOErrorAsTransient() {
        assertEquals(ErrorType.TRANSIENT,
            DuckDBErrorClassifier.classify(new SQLException("I/O error reading file")));
    }

    @Test
    @DisplayName("classify: disk full is TRANSIENT")
    void classifyDiskFullAsTransient() {
        assertEquals(ErrorType.TRANSIENT,
            DuckDBErrorClassifier.classify(new SQLException("No space left on device")));
    }

    @Test
    @DisplayName("classify: connection reset is TRANSIENT")
    void classifyConnectionResetAsTransient() {
        assertEquals(ErrorType.TRANSIENT,
            DuckDBErrorClassifier.classify(new SQLException("Connection reset by peer")));
    }

    @Test
    @DisplayName("classify: SQL state 08xxx (connection) is TRANSIENT")
    void classifySqlState08AsTransient() {
        assertEquals(ErrorType.TRANSIENT,
            DuckDBErrorClassifier.classify(new SQLException("connection error", "08001")));
    }

    @Test
    @DisplayName("classify: SQL state 40xxx (transaction rollback) is TRANSIENT")
    void classifySqlState40AsTransient() {
        assertEquals(ErrorType.TRANSIENT,
            DuckDBErrorClassifier.classify(new SQLException("deadlock", "40001")));
    }

    // ============================================
    // classify() - PERMANENT errors
    // ============================================

    @Test
    @DisplayName("classify: permission denied is PERMANENT")
    void classifyPermissionDeniedAsPermanent() {
        assertEquals(ErrorType.PERMANENT,
            DuckDBErrorClassifier.classify(new SQLException("Permission denied")));
    }

    @Test
    @DisplayName("classify: table not found is PERMANENT")
    void classifyTableNotFoundAsPermanent() {
        assertEquals(ErrorType.PERMANENT,
            DuckDBErrorClassifier.classify(new SQLException("Table not found: combat_hits")));
    }

    @Test
    @DisplayName("classify: syntax error is PERMANENT")
    void classifySyntaxErrorAsPermanent() {
        assertEquals(ErrorType.PERMANENT,
            DuckDBErrorClassifier.classify(new SQLException("Syntax error at line 1")));
    }

    @Test
    @DisplayName("classify: constraint violation is PERMANENT")
    void classifyConstraintViolationAsPermanent() {
        assertEquals(ErrorType.PERMANENT,
            DuckDBErrorClassifier.classify(new SQLException("Unique constraint violation on id")));
    }

    @Test
    @DisplayName("classify: corruption is PERMANENT")
    void classifyCorruptionAsPermanent() {
        assertEquals(ErrorType.PERMANENT,
            DuckDBErrorClassifier.classify(new SQLException("Database file is corrupt")));
    }

    @Test
    @DisplayName("classify: SQL state 42xxx is PERMANENT")
    void classifySqlState42AsPermanent() {
        assertEquals(ErrorType.PERMANENT,
            DuckDBErrorClassifier.classify(new SQLException("access rule violation", "42000")));
    }

    @Test
    @DisplayName("classify: SQL state 23xxx is PERMANENT")
    void classifySqlState23AsPermanent() {
        assertEquals(ErrorType.PERMANENT,
            DuckDBErrorClassifier.classify(new SQLException("integrity constraint", "23000")));
    }

    // ============================================
    // classify() - DEGRADATION (default)
    // ============================================

    @Test
    @DisplayName("classify: null exception is DEGRADATION")
    void classifyNullAsDegradation() {
        assertEquals(ErrorType.DEGRADATION,
            DuckDBErrorClassifier.classify(null));
    }

    @Test
    @DisplayName("classify: null message is DEGRADATION")
    void classifyNullMessageAsDegradation() {
        assertEquals(ErrorType.DEGRADATION,
            DuckDBErrorClassifier.classify(new SQLException((String) null)));
    }

    @Test
    @DisplayName("classify: unknown error is DEGRADATION")
    void classifyUnknownAsDegradation() {
        assertEquals(ErrorType.DEGRADATION,
            DuckDBErrorClassifier.classify(new SQLException("Something unexpected happened")));
    }

    // ============================================
    // Helper methods
    // ============================================

    @Test
    @DisplayName("isLockError detects lock-related messages")
    void isLockErrorDetectsLock() {
        assertTrue(DuckDBErrorClassifier.isLockError(new SQLException("Database file is locked")));
        assertTrue(DuckDBErrorClassifier.isLockError(new SQLException("Database is busy")));
        assertTrue(DuckDBErrorClassifier.isLockError(new SQLException("Thread blocked")));
        assertFalse(DuckDBErrorClassifier.isLockError(new SQLException("Timeout")));
        assertFalse(DuckDBErrorClassifier.isLockError(null));
    }

    @Test
    @DisplayName("isTimeoutError detects timeout messages")
    void isTimeoutErrorDetectsTimeout() {
        assertTrue(DuckDBErrorClassifier.isTimeoutError(new SQLException("Query timeout")));
        assertTrue(DuckDBErrorClassifier.isTimeoutError(new SQLException("Connection timed out")));
        assertFalse(DuckDBErrorClassifier.isTimeoutError(new SQLException("Database locked")));
        assertFalse(DuckDBErrorClassifier.isTimeoutError(null));
    }

    @Test
    @DisplayName("isDiskSpaceError detects disk space messages")
    void isDiskSpaceErrorDetectsDiskSpace() {
        assertTrue(DuckDBErrorClassifier.isDiskSpaceError(new SQLException("Disk full")));
        assertTrue(DuckDBErrorClassifier.isDiskSpaceError(new SQLException("No space left on device")));
        assertFalse(DuckDBErrorClassifier.isDiskSpaceError(new SQLException("Timeout")));
        assertFalse(DuckDBErrorClassifier.isDiskSpaceError(null));
    }

    @Test
    @DisplayName("getHandlingDescription returns non-null for all types")
    void getHandlingDescriptionReturnsForAllTypes() {
        for (ErrorType type : ErrorType.values()) {
            assertNotNull(DuckDBErrorClassifier.getHandlingDescription(type));
        }
    }
}
