package com.devmod.template.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class ComponentCategoryTest {

    @Test
    void allValuesHaveNonNullSerializedName() {
        for (ComponentCategory cat : ComponentCategory.values()) {
            assertNotNull(cat.getSerializedName(), "Serialized name should not be null for " + cat);
            assertFalse(cat.getSerializedName().isEmpty());
        }
    }

    @Test
    void fromStringReturnsMatchingEnum() {
        assertEquals(ComponentCategory.DISPLAY, ComponentCategory.fromString("display"));
        assertEquals(ComponentCategory.CONSOLE, ComponentCategory.fromString("console"));
        assertEquals(ComponentCategory.COMBAT, ComponentCategory.fromString("combat"));
        assertEquals(ComponentCategory.STORAGE, ComponentCategory.fromString("storage"));
        assertEquals(ComponentCategory.UTILITY, ComponentCategory.fromString("utility"));
        assertEquals(ComponentCategory.DECORATIVE, ComponentCategory.fromString("decorative"));
        assertEquals(ComponentCategory.FUNCTIONAL, ComponentCategory.fromString("functional"));
        assertEquals(ComponentCategory.QUEST, ComponentCategory.fromString("quest"));
        assertEquals(ComponentCategory.SHOP, ComponentCategory.fromString("shop"));
        assertEquals(ComponentCategory.TRANSPORT, ComponentCategory.fromString("transport"));
    }

    @Test
    void fromStringDefaultsToDecorativeForUnknown() {
        assertEquals(ComponentCategory.DECORATIVE, ComponentCategory.fromString("nonexistent"));
        assertEquals(ComponentCategory.DECORATIVE, ComponentCategory.fromString(""));
    }

    @ParameterizedTest
    @EnumSource(ComponentCategory.class)
    void roundTripThroughSerializedName(ComponentCategory category) {
        assertEquals(category, ComponentCategory.fromString(category.getSerializedName()));
    }

    @Test
    void hasExpectedValueCount() {
        assertEquals(10, ComponentCategory.values().length);
    }
}
