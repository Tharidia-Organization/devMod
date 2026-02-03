package com.devmod.client.ui.radial.input;

import com.devmod.client.ui.radial.config.RadialMenuConstants;

/**
 * Pure math helpers for radial menu selection.
 * Extracted to keep selection logic testable and isolated.
 */
public final class RadialSelectionMath {
    private RadialSelectionMath() {}

    /**
     * Returns an angle normalized to [0, 2π).
     */
    public static double normalizedAngle(double dx, double dy) {
        double angle = Math.atan2(dy, dx);
        if (angle < 0) {
            angle += RadialMenuConstants.TWO_PI;
        }
        return angle;
    }

    /**
     * Computes a segment index for a given angle and start offset.
     *
     * @param angle normalized angle in [0, 2π)
     * @param startOffset segment start offset
     * @param segments number of segments
     * @return index in [0, segments-1], or NO_SELECTION if segments <= 0
     */
    public static int segmentIndex(double angle, double startOffset, int segments) {
        if (segments <= 0) {
            return RadialMenuConstants.NO_SELECTION;
        }
        double adjusted = angle - startOffset;
        if (adjusted < 0) {
            adjusted += RadialMenuConstants.TWO_PI;
        }
        return (int) (adjusted / (RadialMenuConstants.TWO_PI / segments)) % segments;
    }
}
