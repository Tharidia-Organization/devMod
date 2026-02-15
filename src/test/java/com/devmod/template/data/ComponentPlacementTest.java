package com.devmod.template.data;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;

import com.devmod.TestBootstrap;

import static org.junit.jupiter.api.Assertions.*;

class ComponentPlacementTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    void constructorRejectsNullComponentId() {
        assertThrows(NullPointerException.class, () ->
                new ComponentPlacement(null, RelativePosition.center(), Direction.NORTH, Map.of()));
    }

    @Test
    void constructorRejectsNullPosition() {
        assertThrows(NullPointerException.class, () ->
                new ComponentPlacement("test", null, Direction.NORTH, Map.of()));
    }

    @Test
    void constructorRejectsNullFacing() {
        assertThrows(NullPointerException.class, () ->
                new ComponentPlacement("test", RelativePosition.center(), null, Map.of()));
    }

    @Test
    void constructorRejectsNullParams() {
        assertThrows(NullPointerException.class, () ->
                new ComponentPlacement("test", RelativePosition.center(), Direction.NORTH, null));
    }

    @Test
    void factoryOfWithPosition() {
        ComponentPlacement placement = ComponentPlacement.of("comp_id", RelativePosition.center());
        assertEquals("comp_id", placement.componentId());
        assertEquals(AnchorPoint.CENTER, placement.position().anchor());
        assertEquals(Direction.NORTH, placement.facing());
        assertTrue(placement.params().isEmpty());
    }

    @Test
    void factoryOfWithFacing() {
        ComponentPlacement placement = ComponentPlacement.of("comp_id", RelativePosition.center(), Direction.EAST);
        assertEquals("comp_id", placement.componentId());
        assertEquals(Direction.EAST, placement.facing());
        assertTrue(placement.params().isEmpty());
    }

    @Test
    void getParamReturnsValueWhenPresent() {
        ComponentPlacement placement = new ComponentPlacement(
                "test", RelativePosition.center(), Direction.NORTH,
                Map.of("color", "red", "size", "large"));
        assertEquals("red", placement.getParam("color", "default"));
        assertEquals("large", placement.getParam("size", "default"));
    }

    @Test
    void getParamReturnsDefaultWhenMissing() {
        ComponentPlacement placement = ComponentPlacement.of("test", RelativePosition.center());
        assertEquals("default_val", placement.getParam("missing_key", "default_val"));
    }

    @Test
    void getIntParamReturnsValueWhenPresent() {
        ComponentPlacement placement = new ComponentPlacement(
                "test", RelativePosition.center(), Direction.NORTH,
                Map.of("width", "5", "height", "3"));
        assertEquals(5, placement.getIntParam("width", 0));
        assertEquals(3, placement.getIntParam("height", 0));
    }

    @Test
    void getIntParamReturnsDefaultWhenMissing() {
        ComponentPlacement placement = ComponentPlacement.of("test", RelativePosition.center());
        assertEquals(42, placement.getIntParam("missing", 42));
    }

    @Test
    void getIntParamReturnsDefaultForNonNumericValue() {
        ComponentPlacement placement = new ComponentPlacement(
                "test", RelativePosition.center(), Direction.NORTH,
                Map.of("width", "not_a_number"));
        assertEquals(10, placement.getIntParam("width", 10));
    }

    @Test
    void getBoolParamReturnsTrueWhenSet() {
        ComponentPlacement placement = new ComponentPlacement(
                "test", RelativePosition.center(), Direction.NORTH,
                Map.of("enabled", "true"));
        assertTrue(placement.getBoolParam("enabled", false));
    }

    @Test
    void getBoolParamReturnsFalseWhenSetToFalse() {
        ComponentPlacement placement = new ComponentPlacement(
                "test", RelativePosition.center(), Direction.NORTH,
                Map.of("enabled", "false"));
        assertFalse(placement.getBoolParam("enabled", true));
    }

    @Test
    void getBoolParamReturnsDefaultWhenMissing() {
        ComponentPlacement placement = ComponentPlacement.of("test", RelativePosition.center());
        assertTrue(placement.getBoolParam("missing", true));
        assertFalse(placement.getBoolParam("missing", false));
    }

    @Test
    void getBoolParamReturnsFalseForNonBooleanString() {
        ComponentPlacement placement = new ComponentPlacement(
                "test", RelativePosition.center(), Direction.NORTH,
                Map.of("enabled", "yes"));
        // Boolean.parseBoolean("yes") returns false
        assertFalse(placement.getBoolParam("enabled", true));
    }
}
