package com.devmod.portal;

import com.devmod.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PortalColor enum.
 */
class PortalColorTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Test
    @DisplayName("All 16 dye colors are present")
    void all16ColorsPresent() {
        assertEquals(16, PortalColor.values().length,
            "Should have exactly 16 colors (Minecraft dye colors)");
    }

    @Test
    @DisplayName("Color indices are sequential 0-15")
    void colorIndicesAreSequential() {
        PortalColor[] colors = PortalColor.values();
        for (int i = 0; i < colors.length; i++) {
            assertEquals(i, colors[i].getIndex(),
                "Color " + colors[i].name() + " should have index " + i);
        }
    }

    @Test
    @DisplayName("byIndex returns correct color")
    void byIndexReturnsCorrectColor() {
        assertEquals(PortalColor.WHITE, PortalColor.byIndex(0));
        assertEquals(PortalColor.BLUE, PortalColor.byIndex(11));
        assertEquals(PortalColor.BLACK, PortalColor.byIndex(15));
    }

    @Test
    @DisplayName("byIndex with invalid index returns BLUE default")
    void byIndexInvalidReturnsDefault() {
        assertEquals(PortalColor.BLUE, PortalColor.byIndex(-1));
        assertEquals(PortalColor.BLUE, PortalColor.byIndex(100));
    }

    @Test
    @DisplayName("getSerializedName matches expected format")
    void serializedNameFormat() {
        assertEquals("blue", PortalColor.BLUE.getSerializedName());
        assertEquals("light_blue", PortalColor.LIGHT_BLUE.getSerializedName());
        assertEquals("light_gray", PortalColor.LIGHT_GRAY.getSerializedName());
    }

    @Test
    @DisplayName("RGB color values are valid")
    void rgbValuesAreValid() {
        for (PortalColor color : PortalColor.values()) {
            int rgb = color.getColor();
            int r = color.getRed();
            int g = color.getGreen();
            int b = color.getBlue();

            // Verify components are in 0-255 range
            assertTrue(r >= 0 && r <= 255, "Red component should be 0-255");
            assertTrue(g >= 0 && g <= 255, "Green component should be 0-255");
            assertTrue(b >= 0 && b <= 255, "Blue component should be 0-255");

            // Verify components match the full color value
            assertEquals((rgb >> 16) & 0xFF, r, "Red extraction mismatch");
            assertEquals((rgb >> 8) & 0xFF, g, "Green extraction mismatch");
            assertEquals(rgb & 0xFF, b, "Blue extraction mismatch");
        }
    }

    @Test
    @DisplayName("BLUE color has correct RGB value")
    void blueColorRgbValue() {
        PortalColor blue = PortalColor.BLUE;
        assertEquals(0x3C44AA, blue.getColor());
        assertEquals(0x3C, blue.getRed());   // 60
        assertEquals(0x44, blue.getGreen()); // 68
        assertEquals(0xAA, blue.getBlue());  // 170
    }
}
