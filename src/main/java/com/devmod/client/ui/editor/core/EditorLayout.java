package com.devmod.client.ui.editor.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        int leftWidth = ScaledCoord.scaleDim(EditorConstants.LEFT_COLUMN_WIDTH, scale);

        int panelX = fit.panelX();
        int panelY = fit.panelY();

        // ensure we don't cover the vanilla hotbar; keep minimum height for header/footer/content
        int minHeight = headerHeight + footerHeight + ScaledCoord.scaleDim(MIN_CONTENT_HEIGHT, scale);
        panelHeight = Math.max(panelHeight - hotbarReserve, minHeight);

        panelBounds = new Bounds(panelX, panelY, panelWidth, panelHeight);
        headerBounds = new Bounds(panelX, panelY, panelWidth, headerHeight);
        footerBounds = new Bounds(panelX, panelY + panelHeight - footerHeight, panelWidth, footerHeight);
        leftColumnBounds = new Bounds(panelX, panelY + headerHeight, leftWidth,
            panelHeight - headerHeight - footerHeight);
        contentBounds = new Bounds(panelX + leftWidth, panelY + headerHeight,
            panelWidth - leftWidth, panelHeight - headerHeight - footerHeight);
        
        validateBounds(panelBounds, "panelBounds");
        validateBounds(headerBounds, "headerBounds");
        validateBounds(footerBounds, "footerBounds");
        validateBounds(leftColumnBounds, "leftColumnBounds");
        validateBounds(contentBounds, "contentBounds");
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

    /**
     * Gets the pre-calculated bounds for a specific section within a column.
     * @param columnId The ID of the column.
     * @param sectionId The ID of the section.
     * @return The Bounds of the section, or null if not found.
     */
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
