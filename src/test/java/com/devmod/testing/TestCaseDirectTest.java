package com.devmod.testing;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.testing.TestCase.TestPriority;
import com.devmod.testing.TestCase.TestStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCaseDirectTest {

    @Test
    @DisplayName("Progress-based tests auto-start and auto-complete")
    void progressAutoStartsAndCompletes() {
        AtomicReference<Float> progressValue = new AtomicReference<>(0f);
        TestCase testCase = TestCase.withProgress(
            "progress_test",
            "Category",
            "Progress Test",
            "Desc",
            "Steps",
            TestPriority.MEDIUM,
            progress -> progressValue.get()
        );

        assertEquals(TestStatus.PENDING, testCase.getStatus());
        assertFalse(testCase.checkAutoComplete(null));
        assertEquals(TestStatus.PENDING, testCase.getStatus());

        progressValue.set(0.25f);
        assertFalse(testCase.checkAutoComplete(null));
        assertEquals(TestStatus.IN_PROGRESS, testCase.getStatus());

        progressValue.set(1.0f);
        assertTrue(testCase.checkAutoComplete(null));
        assertEquals(TestStatus.PASSED, testCase.getStatus());
        assertTrue(testCase.wasAutoValidated());
    }

    @Test
    @DisplayName("Auto-validation failures capture error logs and update status")
    void autoValidationFailureCapturesErrorLog() {
        TestCase testCase = new TestCase(
            "auto_fail",
            "Category",
            "Auto Fail",
            "Desc",
            "Steps",
            TestPriority.HIGH,
            () -> { throw new IllegalStateException("boom"); }
        );

        assertFalse(testCase.runAutoValidation());
        assertEquals(TestStatus.FAILED, testCase.getStatus());
        assertTrue(testCase.hasError());
        assertNotNull(testCase.getErrorLog());
        assertTrue(testCase.getErrorLog().contains("Auto-validation error: boom"));

        String report = testCase.toReportString();
        assertTrue(report.contains("Auto-validation failed"));
        assertTrue(report.contains("Auto-validation error: boom"));
    }

    @Test
    @DisplayName("checkAutoValidator marks auto-validated without changing status")
    void checkAutoValidatorDoesNotChangeStatus() {
        TestCase testCase = new TestCase(
            "auto_check",
            "Category",
            "Auto Check",
            "Desc",
            "Steps",
            TestPriority.LOW,
            () -> true
        );

        assertEquals(TestStatus.PENDING, testCase.getStatus());
        assertTrue(testCase.checkAutoValidator());
        assertEquals(TestStatus.PENDING, testCase.getStatus());
        assertTrue(testCase.wasAutoValidated());
    }
}
