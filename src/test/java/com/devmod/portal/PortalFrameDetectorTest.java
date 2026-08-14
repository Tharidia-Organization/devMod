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

    @Test
    @DisplayName("Interior bounds follow MAX_SIZE")
    void interiorBoundsFollowMaxSize() {
        // The frame ring costs one block on each side, so a 23x23 frame holds a 21x21 interior.
        assertEquals(PortalFrameDetector.MAX_SIZE - 2, PortalFrameDetector.MAX_INTERIOR_SIZE);
        assertEquals(21, PortalFrameDetector.MAX_INTERIOR_SIZE);
        assertEquals(PortalFrameDetector.MAX_INTERIOR_SIZE * PortalFrameDetector.MAX_INTERIOR_SIZE,
            PortalFrameDetector.MAX_INTERIOR_BLOCKS);
        assertEquals(441, PortalFrameDetector.MAX_INTERIOR_BLOCKS);
    }
}
