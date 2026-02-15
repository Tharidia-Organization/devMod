package com.devmod.config.component;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BooleanComponent")
class BooleanComponentTest {

    private final BooleanComponent component = new BooleanComponent(
        "test.enabled", "Enabled", "test", true);

    @Test
    @DisplayName("id returns the component id")
    void idReturnsComponentId() {
        assertEquals("test.enabled", component.id());
    }

    @Test
    @DisplayName("displayName returns the display name")
    void displayNameReturnsName() {
        assertEquals("Enabled", component.displayName());
    }

    @Test
    @DisplayName("category returns the category")
    void categoryReturnsCategory() {
        assertEquals("test", component.category());
    }

    @Test
    @DisplayName("defaultValue returns configured default")
    void defaultValueReturnsDefault() {
        assertTrue(component.defaultValue());

        var falseDefault = new BooleanComponent("x", "X", "cat", false);
        assertFalse(falseDefault.defaultValue());
    }

    @Test
    @DisplayName("valueType returns Boolean.class")
    void valueTypeIsBoolean() {
        assertEquals(Boolean.class, component.valueType());
    }

    @Test
    @DisplayName("validate returns null for non-null values")
    void validateNonNullReturnsNull() {
        assertNull(component.validate(true));
        assertNull(component.validate(false));
    }

    @Test
    @DisplayName("validate returns error for null value")
    void validateNullReturnsError() {
        String error = component.validate(null);
        assertNotNull(error);
        assertTrue(error.contains("null"));
    }

    @Test
    @DisplayName("clamp returns value when non-null")
    void clampReturnsValueWhenNonNull() {
        assertTrue(component.clamp(true));
        assertFalse(component.clamp(false));
    }

    @Test
    @DisplayName("clamp returns default when null")
    void clampReturnsDefaultWhenNull() {
        assertTrue(component.clamp(null));

        var falseDefault = new BooleanComponent("x", "X", "cat", false);
        assertFalse(falseDefault.clamp(null));
    }

    @Test
    @DisplayName("toCommandString formats boolean correctly")
    void toCommandStringFormatsBoolean() {
        assertEquals("true", component.toCommandString(true));
        assertEquals("false", component.toCommandString(false));
    }
}
