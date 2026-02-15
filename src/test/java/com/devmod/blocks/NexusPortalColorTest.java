package com.devmod.blocks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NexusPortalColor")
class NexusPortalColorTest {

    @Test
    @DisplayName("all values have non-empty serialized name")
    void allValuesHaveSerializedName() {
        for (NexusPortalColor color : NexusPortalColor.values()) {
            assertNotNull(color.getSerializedName(),
                color.name() + " should have a serialized name");
            assertFalse(color.getSerializedName().isEmpty(),
                color.name() + " serialized name should not be empty");
        }
    }

    @Test
    @DisplayName("serialized names are lowercase")
    void serializedNamesAreLowercase() {
        for (NexusPortalColor color : NexusPortalColor.values()) {
            assertEquals(color.getSerializedName(), color.getSerializedName().toLowerCase(),
                color.name() + " serialized name should be lowercase");
        }
    }

    @Test
    @DisplayName("expected number of portal colors")
    void expectedCount() {
        assertEquals(8, NexusPortalColor.values().length);
    }

    @Test
    @DisplayName("all serialized names are unique")
    void uniqueSerializedNames() {
        NexusPortalColor[] values = NexusPortalColor.values();
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                assertNotEquals(values[i].getSerializedName(), values[j].getSerializedName(),
                    values[i].name() + " and " + values[j].name() + " should have different serialized names");
            }
        }
    }

    @Test
    @DisplayName("specific colors exist with expected serialized names")
    void specificColors() {
        assertEquals("combat", NexusPortalColor.COMBAT.getSerializedName());
        assertEquals("arena", NexusPortalColor.ARENA.getSerializedName());
        assertEquals("ui", NexusPortalColor.UI.getSerializedName());
        assertEquals("telemetry", NexusPortalColor.TELEMETRY.getSerializedName());
        assertEquals("showcase", NexusPortalColor.SHOWCASE.getSerializedName());
        assertEquals("integration", NexusPortalColor.INTEGRATION.getSerializedName());
        assertEquals("sandbox", NexusPortalColor.SANDBOX.getSerializedName());
        assertEquals("mechanics", NexusPortalColor.MECHANICS.getSerializedName());
    }

    @Test
    @DisplayName("valueOf roundtrip works for all colors")
    void valueOfRoundtrip() {
        for (NexusPortalColor color : NexusPortalColor.values()) {
            assertEquals(color, NexusPortalColor.valueOf(color.name()));
        }
    }
}
