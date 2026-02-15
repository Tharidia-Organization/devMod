package com.devmod.config.component;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IntComponent")
class IntComponentTest {

    private final IntComponent component = new IntComponent(
        "combo.timeout", "Timeout", "combo", 60, 10, 200);

    @Test
    @DisplayName("id returns the component id")
    void idReturnsComponentId() {
        assertEquals("combo.timeout", component.id());
    }

    @Test
    @DisplayName("displayName returns the display name")
    void displayNameReturnsName() {
        assertEquals("Timeout", component.displayName());
    }

    @Test
    @DisplayName("category returns the category")
    void categoryReturnsCategory() {
        assertEquals("combo", component.category());
    }

    @Test
    @DisplayName("defaultValue returns configured default")
    void defaultValueReturnsDefault() {
        assertEquals(60, component.defaultValue());
    }

    @Test
    @DisplayName("valueType returns Integer.class")
    void valueTypeIsInteger() {
        assertEquals(Integer.class, component.valueType());
    }

    @Test
    @DisplayName("minValue and maxValue are accessible")
    void minMaxAreAccessible() {
        assertEquals(10, component.getMinValue());
        assertEquals(200, component.getMaxValue());
    }

    // --- validate ---

    @Test
    @DisplayName("validate returns null for valid value in range")
    void validateInRangeReturnsNull() {
        assertNull(component.validate(60));
        assertNull(component.validate(10));
        assertNull(component.validate(200));
    }

    @Test
    @DisplayName("validate returns error for null value")
    void validateNullReturnsError() {
        assertNotNull(component.validate(null));
    }

    @Test
    @DisplayName("validate returns error for value below min")
    void validateBelowMinReturnsError() {
        String error = component.validate(9);
        assertNotNull(error);
        assertTrue(error.contains("between"));
    }

    @Test
    @DisplayName("validate returns error for value above max")
    void validateAboveMaxReturnsError() {
        String error = component.validate(201);
        assertNotNull(error);
        assertTrue(error.contains("between"));
    }

    // --- clamp ---

    @Test
    @DisplayName("clamp returns value when in range")
    void clampReturnsValueInRange() {
        assertEquals(100, component.clamp(100));
    }

    @Test
    @DisplayName("clamp clamps to min when below")
    void clampClampsToMin() {
        assertEquals(10, component.clamp(5));
    }

    @Test
    @DisplayName("clamp clamps to max when above")
    void clampClampsToMax() {
        assertEquals(200, component.clamp(300));
    }

    @Test
    @DisplayName("clamp returns default for null")
    void clampReturnsDefaultForNull() {
        assertEquals(60, component.clamp(null));
    }

    @Test
    @DisplayName("toCommandString formats integer value")
    void toCommandStringFormatsInt() {
        assertEquals("42", component.toCommandString(42));
    }
}
