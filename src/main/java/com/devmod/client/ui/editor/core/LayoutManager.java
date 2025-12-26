package com.devmod.client.ui.editor.core;

import java.util.ArrayList;
import java.util.List;

public final class LayoutManager {

    private LayoutManager() {}

    /**
     * Arranges a list of components into a vertical column within a given area.
     *
     * @param area The bounding box to place the section in.
     * @param componentHeights A list of heights for each component.
     * @param gap The vertical space between each component.
     * @return A list of Bounds for each component.
     */
    public static List<Bounds> createSectionLayout(Bounds area, List<Integer> componentHeights, int gap) {
        List<Bounds> bounds = new ArrayList<>();
        int currentY = area.y();

        for (int height : componentHeights) {
            bounds.add(new Bounds(area.x(), currentY, area.width(), height));
            currentY += height + gap;
        }

        return bounds;
    }

    /**
     * Arranges a list of components into a horizontal row within a given area.
     *
     * @param area The bounding box to place the row in.
     * @param componentWidths A list of widths for each component.
     * @param height The uniform height for all components in the row.
     * @param gap The horizontal space between each component.
     * @return A list of Bounds for each component.
     */
    public static List<Bounds> createRowLayout(Bounds area, List<Integer> componentWidths, int height, int gap) {
        List<Bounds> bounds = new ArrayList<>();
        int currentX = area.x();

        for (int width : componentWidths) {
            bounds.add(new Bounds(currentX, area.y(), width, height));
            currentX += width + gap;
        }

        return bounds;
    }
}
