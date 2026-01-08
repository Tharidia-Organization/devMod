package com.devmod.portal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for portal frame detection constants and validation.
 * Full frame detection testing requires Minecraft environment (gametest).
 */
class PortalFrameDetectorTest {

    @Test
    @DisplayName("MIN_SIZE constant is 3")
    void minSizeIs3() {
        assertEquals(3, PortalFrameDetector.MIN_SIZE);
    }

    @Test
    @DisplayName("MAX_SIZE constant is 23")
    void maxSizeIs23() {
        assertEquals(23, PortalFrameDetector.MAX_SIZE);
    }

    @Test
    @DisplayName("Size range allows standard portal sizes")
    void sizeRangeAllowsStandardPortals() {
        // Standard nether portal is 4x5
        assertTrue(4 >= PortalFrameDetector.MIN_SIZE);
        assertTrue(4 <= PortalFrameDetector.MAX_SIZE);
        assertTrue(5 >= PortalFrameDetector.MIN_SIZE);
        assertTrue(5 <= PortalFrameDetector.MAX_SIZE);
    }

    @Test
    @DisplayName("Size range allows large custom portals")
    void sizeRangeAllowsLargePortals() {
        // 21x21 should be valid
        assertTrue(21 >= PortalFrameDetector.MIN_SIZE);
        assertTrue(21 <= PortalFrameDetector.MAX_SIZE);
    }
}
