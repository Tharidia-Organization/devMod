package com.devmod.endurance.nutrition;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NutritionCategoryTest {

    @Test
    @DisplayName("Six categories are defined")
    void sixCategoriesDefined() {
        assertEquals(6, NutritionCategory.COUNT);
        assertEquals(6, NutritionCategory.values().length);
    }

    @Test
    @DisplayName("ALL list contains all values in order")
    void allListContainsAll() {
        List<NutritionCategory> all = NutritionCategory.ALL;
        assertEquals(6, all.size());
        assertEquals(NutritionCategory.GRAIN, all.get(0));
        assertEquals(NutritionCategory.WATER, all.get(5));
    }

    @Test
    @DisplayName("byIndex returns correct category")
    void byIndexReturnsCorrect() {
        assertEquals(NutritionCategory.GRAIN, NutritionCategory.byIndex(0));
        assertEquals(NutritionCategory.PROTEIN, NutritionCategory.byIndex(1));
        assertEquals(NutritionCategory.VEGETABLE, NutritionCategory.byIndex(2));
        assertEquals(NutritionCategory.FRUIT, NutritionCategory.byIndex(3));
        assertEquals(NutritionCategory.SUGAR, NutritionCategory.byIndex(4));
        assertEquals(NutritionCategory.WATER, NutritionCategory.byIndex(5));
    }

    @Test
    @DisplayName("byIndex returns null for out-of-range")
    void byIndexOutOfRange() {
        assertNull(NutritionCategory.byIndex(-1));
        assertNull(NutritionCategory.byIndex(6));
        assertNull(NutritionCategory.byIndex(100));
    }

    @Test
    @DisplayName("byKey returns correct category")
    void byKeyReturnsCorrect() {
        assertEquals(NutritionCategory.GRAIN, NutritionCategory.byKey("grain"));
        assertEquals(NutritionCategory.PROTEIN, NutritionCategory.byKey("protein"));
        assertEquals(NutritionCategory.VEGETABLE, NutritionCategory.byKey("vegetable"));
        assertEquals(NutritionCategory.FRUIT, NutritionCategory.byKey("fruit"));
        assertEquals(NutritionCategory.SUGAR, NutritionCategory.byKey("sugar"));
        assertEquals(NutritionCategory.WATER, NutritionCategory.byKey("water"));
    }

    @Test
    @DisplayName("byKey returns null for unknown key")
    void byKeyUnknown() {
        assertNull(NutritionCategory.byKey("unknown"));
        assertNull(NutritionCategory.byKey(""));
        assertNull(NutritionCategory.byKey(null));
    }

    @Test
    @DisplayName("Each category has unique index matching ordinal")
    void indicesMatchOrdinals() {
        for (NutritionCategory cat : NutritionCategory.values()) {
            assertEquals(cat.ordinal(), cat.getIndex());
        }
    }

    @Test
    @DisplayName("getRgbComponents returns 3-element normalized array")
    void rgbComponentsNormalized() {
        for (NutritionCategory cat : NutritionCategory.values()) {
            float[] rgb = cat.getRgbComponents();
            assertEquals(3, rgb.length);
            for (float component : rgb) {
                assertTrue(component >= 0f && component <= 1f,
                    cat + " RGB component out of range: " + component);
            }
        }
    }

    @Test
    @DisplayName("GRAIN color RGB extraction is correct")
    void grainColorRgb() {
        // 0xFFD4AF37 -> R=0xD4=212, G=0xAF=175, B=0x37=55
        float[] rgb = NutritionCategory.GRAIN.getRgbComponents();
        assertEquals(212f / 255f, rgb[0], 0.002f);
        assertEquals(175f / 255f, rgb[1], 0.002f);
        assertEquals(55f / 255f, rgb[2], 0.002f);
    }

    @Test
    @DisplayName("getAlpha returns alpha byte from ARGB color")
    void alphaExtraction() {
        // All categories have 0xFF alpha
        for (NutritionCategory cat : NutritionCategory.values()) {
            assertEquals(0xFF, cat.getAlpha(),
                cat + " should have full alpha");
        }
    }

    @Test
    @DisplayName("All categories have non-empty display names and keys")
    void displayNamesAndKeys() {
        for (NutritionCategory cat : NutritionCategory.values()) {
            assertNotNull(cat.getDisplayName());
            assertFalse(cat.getDisplayName().isEmpty());
            assertNotNull(cat.getKey());
            assertFalse(cat.getKey().isEmpty());
        }
    }
}
