package com.devmod.config.component;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FloatComponent")
class FloatComponentTest {

    private final FloatComponent component = new FloatComponent(
        "weapon.damage", "Attack Damage", "weapon", 5.0f, 0.0f, 100.0f);

    @Test
    @DisplayName("id returns the component id")
    void idReturnsComponentId() {
        assertEquals("weapon.damage", component.id());
    }

    @Test
    @DisplayName("displayName returns the display name")
    void displayNameReturnsName() {
        assertEquals("Attack Damage", component.displayName());
    }

    @Test
    @DisplayName("category returns the category")
    void categoryReturnsCategory() {
        assertEquals("weapon", component.category());
    }

    @Test
    @DisplayName("defaultValue returns configured default")
    void defaultValueReturnsDefault() {
        assertEquals(5.0f, component.defaultValue());
    }

    @Test
    @DisplayName("valueType returns Float.class")
    void valueTypeIsFloat() {
        assertEquals(Float.class, component.valueType());
    }

    @Test
    @DisplayName("minValue and maxValue are accessible")
    void minMaxAreAccessible() {
        assertEquals(0.0f, component.getMinValue());
        assertEquals(100.0f, component.getMaxValue());
    }

    // --- validate ---

    @Test
    @DisplayName("validate returns null for valid value in range")
    void validateInRangeReturnsNull() {
        assertNull(component.validate(5.0f));
        assertNull(component.validate(0.0f));
        assertNull(component.validate(100.0f));
        assertNull(component.validate(50.5f));
    }

    @Test
    @DisplayName("validate returns error for null value")
    void validateNullReturnsError() {
        assertNotNull(component.validate(null));
    }

    @Test
    @DisplayName("validate returns error for NaN")
    void validateNaNReturnsError() {
        String error = component.validate(Float.NaN);
        assertNotNull(error);
        assertTrue(error.contains("valid number"));
    }

    @Test
    @DisplayName("validate returns error for Infinity")
    void validateInfinityReturnsError() {
        assertNotNull(component.validate(Float.POSITIVE_INFINITY));
        assertNotNull(component.validate(Float.NEGATIVE_INFINITY));
    }

    @Test
    @DisplayName("validate returns error for value below min")
    void validateBelowMinReturnsError() {
        String error = component.validate(-0.1f);
        assertNotNull(error);
        assertTrue(error.contains("between"));
    }

    @Test
    @DisplayName("validate returns error for value above max")
    void validateAboveMaxReturnsError() {
        String error = component.validate(100.1f);
        assertNotNull(error);
        assertTrue(error.contains("between"));
    }

    // --- clamp ---

    @Test
    @DisplayName("clamp returns value when in range")
    void clampReturnsValueInRange() {
        assertEquals(50.0f, component.clamp(50.0f));
    }

    @Test
    @DisplayName("clamp clamps to min when below")
    void clampClampsToMin() {
        assertEquals(0.0f, component.clamp(-10.0f));
    }

    @Test
    @DisplayName("clamp clamps to max when above")
    void clampClampsToMax() {
        assertEquals(100.0f, component.clamp(200.0f));
    }

    @Test
    @DisplayName("clamp returns default for null")
    void clampReturnsDefaultForNull() {
        assertEquals(5.0f, component.clamp(null));
    }

    @Test
    @DisplayName("clamp returns default for NaN")
    void clampReturnsDefaultForNaN() {
        assertEquals(5.0f, component.clamp(Float.NaN));
    }

    @Test
    @DisplayName("clamp returns default for Infinity")
    void clampReturnsDefaultForInfinity() {
        assertEquals(5.0f, component.clamp(Float.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("toCommandString formats float value")
    void toCommandStringFormatsFloat() {
        String result = component.toCommandString(5.0f);
        assertNotNull(result);
        assertTrue(result.contains("5"));
        assertTrue(result.endsWith("f"));
    }
}
