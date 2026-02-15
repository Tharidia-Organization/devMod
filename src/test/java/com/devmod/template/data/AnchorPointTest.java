package com.devmod.template.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class AnchorPointTest {

    @Test
    void allValuesHaveNonNullSerializedName() {
        for (AnchorPoint anchor : AnchorPoint.values()) {
            assertNotNull(anchor.getSerializedName(), "Serialized name should not be null for " + anchor);
            assertFalse(anchor.getSerializedName().isEmpty(), "Serialized name should not be empty for " + anchor);
        }
    }

    @Test
    void fromStringReturnsMatchingEnum() {
        assertEquals(AnchorPoint.CENTER, AnchorPoint.fromString("center"));
        assertEquals(AnchorPoint.NORTH_WALL, AnchorPoint.fromString("north_wall"));
        assertEquals(AnchorPoint.SOUTH_WALL, AnchorPoint.fromString("south_wall"));
        assertEquals(AnchorPoint.EAST_WALL, AnchorPoint.fromString("east_wall"));
        assertEquals(AnchorPoint.WEST_WALL, AnchorPoint.fromString("west_wall"));
        assertEquals(AnchorPoint.NW_CORNER, AnchorPoint.fromString("nw_corner"));
        assertEquals(AnchorPoint.NE_CORNER, AnchorPoint.fromString("ne_corner"));
        assertEquals(AnchorPoint.SW_CORNER, AnchorPoint.fromString("sw_corner"));
        assertEquals(AnchorPoint.SE_CORNER, AnchorPoint.fromString("se_corner"));
    }

    @Test
    void fromStringDefaultsToCenterForUnknown() {
        assertEquals(AnchorPoint.CENTER, AnchorPoint.fromString("nonexistent"));
        assertEquals(AnchorPoint.CENTER, AnchorPoint.fromString(""));
    }

    @ParameterizedTest
    @EnumSource(AnchorPoint.class)
    void roundTripThroughSerializedName(AnchorPoint anchor) {
        assertEquals(anchor, AnchorPoint.fromString(anchor.getSerializedName()));
    }

    @Test
    void hasExpectedValueCount() {
        assertEquals(13, AnchorPoint.values().length);
    }
}
