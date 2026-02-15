package com.devmod.hologram.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.Display;

import com.devmod.TestBootstrap;

@DisplayName("HologramBillboard")
class HologramBillboardTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Test
    @DisplayName("getSerializedName matches expected values")
    void serializedNames() {
        assertEquals("center", HologramBillboard.CENTER.getSerializedName());
        assertEquals("fixed", HologramBillboard.FIXED.getSerializedName());
        assertEquals("vertical", HologramBillboard.VERTICAL.getSerializedName());
        assertEquals("horizontal", HologramBillboard.HORIZONTAL.getSerializedName());
    }

    @Test
    @DisplayName("getNbtValue returns same as serialized name")
    void nbtValueMatchesSerialized() {
        for (HologramBillboard bb : HologramBillboard.values()) {
            assertEquals(bb.getSerializedName(), bb.getNbtValue());
        }
    }

    @Test
    @DisplayName("getConstraint maps to correct MC enum")
    void constraintMapping() {
        assertEquals(Display.BillboardConstraints.CENTER, HologramBillboard.CENTER.getConstraint());
        assertEquals(Display.BillboardConstraints.FIXED, HologramBillboard.FIXED.getConstraint());
        assertEquals(Display.BillboardConstraints.VERTICAL, HologramBillboard.VERTICAL.getConstraint());
        assertEquals(Display.BillboardConstraints.HORIZONTAL, HologramBillboard.HORIZONTAL.getConstraint());
    }

    @Test
    @DisplayName("has exactly 4 values")
    void valueCount() {
        assertEquals(4, HologramBillboard.values().length);
    }
}
