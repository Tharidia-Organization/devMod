package com.devmod.client.ui.editor.core;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple runtime grid validation helper (4px grid).
 * Logs when bounds are off-grid to aid debugging (guarded by EditorSpacing.ENABLE_GRID_VALIDATION).
 */
public final class GridValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(GridValidator.class);

    private GridValidator() {}

    public static void validate(String label, Bounds... bounds) {
        if (!EditorSpacing.ENABLE_GRID_VALIDATION) return;
        if (bounds == null) return;
        for (Bounds b : bounds) {
            if (b == null) continue;
            boolean ok = EditorSpacing.isOnGrid(b.x()) && EditorSpacing.isOnGrid(b.y())
                && EditorSpacing.isOnGrid(b.width()) && EditorSpacing.isOnGrid(b.height());
            if (!ok) {
                LOGGER.debug("[GridValidator] Off-grid bounds for {}: {},{} {}x{}",
                    label, b.x(), b.y(), b.width(), b.height());
            }
        }
    }

    public static void validate(String label, int... values) {
        if (!EditorSpacing.ENABLE_GRID_VALIDATION) return;
        if (values == null) return;
        boolean ok = Arrays.stream(values).allMatch(EditorSpacing::isOnGrid);
        if (!ok) {
            LOGGER.debug("[GridValidator] Off-grid values for {}: {}", label, Arrays.toString(values));
        }
    }
}
