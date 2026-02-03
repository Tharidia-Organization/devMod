package com.devmod.client.ui.radial.input;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.client.ui.radial.config.RadialMenuConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Radial Selection Math")
class RadialSelectionMathTest {

    private static final double EPS = 1e-6;

    @Test
    @DisplayName("normalizedAngle maps to [0, 2π)")
    void normalizedAngleMapsToPositiveRange() {
        assertEquals(0.0, RadialSelectionMath.normalizedAngle(1, 0), EPS);
        assertEquals(Math.PI / 2, RadialSelectionMath.normalizedAngle(0, 1), EPS);
        assertEquals(RadialMenuConstants.TWO_PI - Math.PI / 2,
            RadialSelectionMath.normalizedAngle(0, -1), EPS);
    }

    @Test
    @DisplayName("segmentIndex uses start offset")
    void segmentIndexUsesStartOffset() {
        int segments = 4;
        double step = RadialMenuConstants.TWO_PI / segments;
        assertEquals(0, RadialSelectionMath.segmentIndex(0, 0, segments));
        assertEquals(1, RadialSelectionMath.segmentIndex(step + 1e-4, 0, segments));
        assertEquals(3, RadialSelectionMath.segmentIndex(RadialMenuConstants.TWO_PI - 1e-4, 0, segments));
    }

    @Test
    @DisplayName("segmentIndex returns NO_SELECTION for invalid segments")
    void segmentIndexHandlesInvalidSegments() {
        assertEquals(RadialMenuConstants.NO_SELECTION, RadialSelectionMath.segmentIndex(0, 0, 0));
        assertEquals(RadialMenuConstants.NO_SELECTION, RadialSelectionMath.segmentIndex(0, 0, -3));
    }
}
