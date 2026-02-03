package com.devmod.client.ui.editor.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.client.ui.editor.EditorSection;

public class EditorLayout {

    private static final Logger LOGGER = LoggerFactory.getLogger(EditorLayout.class);

    private Bounds panelBounds = Bounds.EMPTY;
    private Bounds headerBounds = Bounds.EMPTY;
    private Bounds footerBounds = Bounds.EMPTY;
    private Bounds leftColumnBounds = Bounds.EMPTY;
    private Bounds contentBounds = Bounds.EMPTY;
    private static final int HOTBAR_RESERVE = 24;
    private static final int MIN_CONTENT_HEIGHT = 32;

    // Responsive layout constants
    /** Minimum panel width to show left column (scaled base value) */
    private static final int MIN_WIDTH_FOR_LEFT_COLUMN = 400;
    /** Minimum content width before hiding left column */
    private static final int MIN_CONTENT_WIDTH = 200;

    /** Whether left column is visible in current layout */
    private boolean leftColumnVisible = true;
    /** Whether layout is in compact mode (small screen) */
    private boolean compactMode = false;

    private final Map<String, List<SectionBounds>> columnSections = new HashMap<>();

    /**
    * Compute all bounds given the screen size and UI scale.
    */
    public void computePositions(int screenWidth, int screenHeight) {
        computePositions(screenWidth, screenHeight, ScaledCoord.getScale());
    }

    public void computePositions(int screenWidth, int screenHeight, float scale) {
        EditorScaleCalculator.ScreenFitResult fit = EditorScaleCalculator.calculateFit(screenWidth, screenHeight, scale);
        computePositions(fit, scale);
    }

    /**
     * Compute all bounds using a pre-calculated fit result (clamped panel size/position).
     */
    public void computePositions(EditorScaleCalculator.ScreenFitResult fit, float scale) {
        columnSections.clear();

        int panelWidth = fit.panelWidth();
        int panelHeight = fit.panelHeight();
        int hotbarReserve = ScaledCoord.scaleDim(HOTBAR_RESERVE, scale); // leave space for vanilla hotbar
        int headerHeight = ScaledCoord.scaleDim(EditorConstants.HEADER_HEIGHT, scale);
        int footerHeight = ScaledCoord.scaleDim(EditorConstants.FOOTER_HEIGHT, scale);
        int baseLeftWidth = ScaledCoord.scaleDim(EditorConstants.LEFT_COLUMN_WIDTH, scale);
        int minContentWidth = ScaledCoord.scaleDim(MIN_CONTENT_WIDTH, scale);
        int minWidthForLeftColumn = ScaledCoord.scaleDim(MIN_WIDTH_FOR_LEFT_COLUMN, scale);

        int panelX = fit.panelX();
        int panelY = fit.panelY();

        // ensure we don't cover the vanilla hotbar; keep minimum height for header/footer/content
        int minHeight = headerHeight + footerHeight + ScaledCoord.scaleDim(MIN_CONTENT_HEIGHT, scale);
        panelHeight = Math.max(panelHeight - hotbarReserve, minHeight);

        // Responsive left column visibility:
        // Hide left column if panel is too narrow to fit both left column and minimum content
        int availableForContent = panelWidth - baseLeftWidth;
        leftColumnVisible = panelWidth >= minWidthForLeftColumn && availableForContent >= minContentWidth;
        compactMode = !leftColumnVisible || panelWidth < minWidthForLeftColumn;

        int leftWidth = leftColumnVisible ? baseLeftWidth : 0;
        int contentX = panelX + leftWidth;
        int contentWidth = panelWidth - leftWidth;

        panelBounds = new Bounds(panelX, panelY, panelWidth, panelHeight);
        headerBounds = new Bounds(panelX, panelY, panelWidth, headerHeight);
        footerBounds = new Bounds(panelX, panelY + panelHeight - footerHeight, panelWidth, footerHeight);

        if (leftColumnVisible) {
            leftColumnBounds = new Bounds(panelX, panelY + headerHeight, leftWidth,
                panelHeight - headerHeight - footerHeight);
        } else {
            leftColumnBounds = Bounds.EMPTY;
        }

        contentBounds = new Bounds(contentX, panelY + headerHeight,
            contentWidth, panelHeight - headerHeight - footerHeight);

        validateBounds(panelBounds, "panelBounds");
        validateBounds(headerBounds, "headerBounds");
        validateBounds(footerBounds, "footerBounds");
        if (leftColumnVisible) {
            validateBounds(leftColumnBounds, "leftColumnBounds");
        }
        validateBounds(contentBounds, "contentBounds");

        if (compactMode) {
            LOGGER.debug("[EditorLayout] Compact mode enabled - panel width: {}, left column visible: {}",
                panelWidth, leftColumnVisible);
        }
    }

    /**
     * Lays out a list of sections within a column area.
     * @param columnId An identifier for the column (e.g., "left", "content").
     * @param sections The list of sections to layout.
     * @param area The bounds of the column.
     * @param padding The padding to apply inside the column.
     * @param gap The gap between sections.
     */
    public void layoutSectionsInColumn(String columnId, List<EditorSection> sections, Bounds area, int padding, int gap) {
        validateGridAlignment(padding, "padding for " + columnId);
        validateGridAlignment(gap, "gap for " + columnId);

        List<SectionBounds> boundsList = new ArrayList<>();
        int currentY = area.y() + padding;
        int sectionWidth = area.width() - (2 * padding);
        validateGridAlignment(sectionWidth, "sectionWidth for " + columnId);

        for (EditorSection section : sections) {
            int sectionHeight = section.getHeight();
            validateGridAlignment(sectionHeight, "sectionHeight for " + section.getId());
            Bounds sectionArea = new Bounds(area.x() + padding, currentY, sectionWidth, sectionHeight);
            boundsList.add(new SectionBounds(section.getId(), sectionArea));
            currentY += sectionHeight + gap;
        }

        columnSections.put(columnId, boundsList);
    }

    public Bounds getPanelBounds() { return panelBounds; }
    public Bounds getHeaderBounds() { return headerBounds; }
    public Bounds getFooterBounds() { return footerBounds; }
    public Bounds getLeftColumnBounds() { return leftColumnBounds; }
    public Bounds getContentBounds() { return contentBounds; }

    /** Returns true if the left column is visible in the current layout. */
    public boolean isLeftColumnVisible() { return leftColumnVisible; }

    /** Returns true if the layout is in compact mode (small screen). */
    public boolean isCompactMode() { return compactMode; }

    /**
     * Gets the pre-calculated bounds for a specific section within a column.
     * @param columnId The ID of the column.
     * @param sectionId The ID of the section.
     * @return The Bounds of the section, or null if not found.
     */
    @Nullable
    public Bounds getSectionBounds(String columnId, String sectionId) {
        List<SectionBounds> boundsList = columnSections.get(columnId);
        if (boundsList != null) {
            for (SectionBounds sb : boundsList) {
                if (sb.sectionId().equals(sectionId)) {
                    return sb.bounds();
                }
            }
        }
        return null;
    }

    /**
     * Gets all section bounds for a given column.
     * @param columnId The ID of the column.
     * @return A list of SectionBounds, or null if the column is not found.
     */
    @Nullable
    public List<SectionBounds> getSectionsForColumn(String columnId) {
        return columnSections.get(columnId);
    }

    private void validateGridAlignment(int value, String context) {
        if (value % 4 != 0) {
            LOGGER.debug("[EditorLayout] Grid alignment warning: {} value ({}) is not aligned to 4px grid", context, value);
        }
    }

    private void validateBounds(Bounds bounds, String context) {
        validateGridAlignment(bounds.x(), context + ".x");
        validateGridAlignment(bounds.y(), context + ".y");
        validateGridAlignment(bounds.width(), context + ".width");
        validateGridAlignment(bounds.height(), context + ".height");
    }
}
