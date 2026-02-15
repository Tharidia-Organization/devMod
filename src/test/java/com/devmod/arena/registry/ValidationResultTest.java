package com.devmod.arena.registry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class ValidationResultTest {

    @Test
    void success() {
        ValidationResult result = ValidationResult.success();
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
        assertFalse(result.hasWarnings());
    }

    @Test
    void failureWithList() {
        ValidationResult result = ValidationResult.failure(List.of("err1", "err2"));
        assertFalse(result.valid());
        assertEquals(2, result.errors().size());
        assertEquals("err1; err2", result.errorsAsString());
    }

    @Test
    void failureWithSingleError() {
        ValidationResult result = ValidationResult.failure("single error");
        assertFalse(result.valid());
        assertEquals(1, result.errors().size());
        assertEquals("single error", result.errorsAsString());
    }

    @Test
    void warningsAsString() {
        ValidationResult result = new ValidationResult(true, List.of(), List.of("w1", "w2"));
        assertTrue(result.hasWarnings());
        assertEquals("w1; w2", result.warningsAsString());
    }

    @Test
    void backwardCompatibleConstructor() {
        ValidationResult result = new ValidationResult(true, List.of());
        assertTrue(result.valid());
        assertTrue(result.warnings().isEmpty());
    }
}
