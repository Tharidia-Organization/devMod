package com.devmod.client.ui.editor.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.core.Bounds;
import com.devmod.client.ui.editor.core.RenderObjectPool;
import com.devmod.client.ui.editor.core.StringBuilderCache;

/**
 * Debug overlay system for editor development and troubleshooting.
 * Activated with F9/F10/F11 shortcuts.
 */
public final class DebugOverlay {
    
    private static boolean enabled = false;
    private static boolean showGrid = false;
    private static boolean showBounds = false;
    private static DetailLevel detailLevel = DetailLevel.MEDIUM;
    private static String perfLine = null;
    
    // Colors
    private static final int COLOR_GRID = 0x40FFFFFF;        // White 25%
    private static final int COLOR_ZONE_BOUNDARY = 0x80FFFF00; // Yellow 50%
    private static final int COLOR_BBOX = 0x8000FFFF;        // Cyan 50%
    private static final int COLOR_BBOX_HOVERED = 0xC000FFFF; // Cyan 75%
    private static final int COLOR_WARNING = 0xFFFF4444;     // Red solid
    private static final int COLOR_OVERFLOW = 0x80FF0000;    // Red 50%
    private static final int COLOR_INFO_BG = 0xE0000000;     // Black 88%
    private static final int COLOR_INFO_TEXT = 0xFFCCCCCC;   // Light gray
    private static final int COLOR_GRID_MAJOR = 0x60FFFFFF;  // White 37%

    // Layout constants
    private static final int GRID_SIZE = 4;
    private static final int GRID_MAJOR_STEP = 16;
    private static final int ZONE_LABEL_OFFSET = 4;
    private static final int OVERFLOW_BAR_HEIGHT = 6;
    private static final int OVERFLOW_TEXT_OFFSET_X = 4;
    private static final int OVERFLOW_TEXT_OFFSET_Y = 14;
    private static final int BBOX_MARKER_OFFSET = 6;
    private static final int BBOX_MARKER_SIZE = 4;
    private static final int INFO_PANEL_WIDTH = 340;
    private static final int INFO_PANEL_LINE_HEIGHT = 12;
    private static final int INFO_PANEL_PADDING = 4;
    private static final int INFO_PANEL_PADDING_DOUBLE = 8;
    private static final int INFO_PANEL_MARGIN_RIGHT = 8;
    private static final int INFO_PANEL_MARGIN_BOTTOM = 8;
    private static final int INFO_PANEL_FOOTER_OFFSET = 60;
    private static final int REPORTER_LABEL_OFFSET = 2;
    private static final int REPORTER_DETAIL_OFFSET_Y = 12;
    private static final int WARNING_LABEL_OFFSET_Y = 10;
    private static final int WARNING_OVERLAY_ALPHA = 0x40000000; // 25% alpha
    
    public enum DetailLevel {
        LOW,    // Only warnings
        MEDIUM, // Warnings + bounds + basic info
        HIGH    // Everything including grid + coordinates
    }
    
    private DebugOverlay() {} // Static utility class
    
    /**
     * Handle debug overlay keyboard shortcuts.
     */
    public static boolean handleKeyPressed(int keyCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_F9 -> {
                if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                    cycleDetailLevel();
                } else {
                    toggle();
                }
                return true;
            }
            case GLFW.GLFW_KEY_F10 -> {
                if (enabled) {
                    showGrid = !showGrid;
                }
                return true;
            }
            case GLFW.GLFW_KEY_F11 -> {
                if (enabled) {
                    showBounds = !showBounds;
                }
                return true;
            }
        }
        return false;
    }
    
    /**
     * Toggle master debug mode.
     */
    public static void toggle() {
        enabled = !enabled;
        if (!enabled) {
            showGrid = false;
            showBounds = false;
        }
    }
    
    /**
     * Cycle detail level with Shift+F9.
     */
    public static void cycleDetailLevel() {
        detailLevel = switch (detailLevel) {
            case LOW -> DetailLevel.MEDIUM;
            case MEDIUM -> DetailLevel.HIGH;
            case HIGH -> DetailLevel.LOW;
        };
    }
    
    /**
     * Check if debug overlay is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Render debug overlay on top of editor.
     */
    public static void render(GuiGraphics graphics, Font font,
                              Bounds panelBounds,
                              Bounds headerBounds,
                              Bounds leftBounds,
                              Bounds contentBounds,
                              Bounds footerBounds,
                              int mouseX, int mouseY,
                              int contentTotalHeight,
                              float scrollOffset,
                              int sectionCount) {
        if (!enabled) return;
        GuiGraphics safeGraphics = Objects.requireNonNull(graphics, "graphics");
        Font safeFont = Objects.requireNonNull(font, "font");
        int panelX = panelBounds.x();
        int panelY = panelBounds.y();
        int panelWidth = panelBounds.width();
        int panelHeight = panelBounds.height();

        // Collect warnings from reporters at start of frame
        collectWarnings();

        // Layer 1: Grid (if enabled)
        if (showGrid || detailLevel == DetailLevel.HIGH) {
            renderGrid(safeGraphics, panelX, panelY, panelWidth, panelHeight);
        }

        // Layer 2: Zone boundaries
        renderZoneBoundaries(safeGraphics, safeFont, headerBounds, leftBounds, footerBounds, panelBounds);

        // Layer 3: Panel bounding boxes when bounds are shown
        if (showBounds || detailLevel != DetailLevel.LOW) {
            renderBoundingBoxes(safeGraphics, panelBounds, contentBounds);
            // Also render bounds for all registered reporters
            renderReporterBounds(safeGraphics, safeFont, mouseX, mouseY);
        }

        // Layer 4: Overflow warning for content + reporter warnings
        renderWarnings(safeGraphics, safeFont, contentBounds, contentTotalHeight, scrollOffset);

        // Layer 5: Info panel (now includes warning count and hovered component)
        String hoveredComponent = getHoveredReporterName(mouseX, mouseY);
        renderInfoPanel(safeGraphics, safeFont, panelBounds, contentBounds, mouseX, mouseY,
            contentTotalHeight, sectionCount, scrollOffset, hoveredComponent);
    }

    /**
     * Get the name of the reporter under the mouse cursor.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @return Name of hovered reporter, or empty string if none
     */
    public static String getHoveredReporterName(int mouseX, int mouseY) {
        for (DebugReporter reporter : reporters) {
            Bounds bounds = reporter.getDebugBounds();
            if (bounds != null && bounds != Bounds.EMPTY && bounds.contains(mouseX, mouseY)) {
                return reporter.getDebugName();
            }
        }
        return "";
    }

    private static void renderWarnings(GuiGraphics graphics, Font font, Bounds contentBounds, int contentTotalHeight, float scrollOffset) {
        Font safeFont = Objects.requireNonNull(font, "font");
        int viewport = contentBounds.height();

        // Use OverflowDetector to check content overflow
        var overflowWarning = OverflowDetector.checkViewportOverflow(
            "Content",
            contentBounds.x(), contentBounds.y(),
            contentBounds.width(), contentTotalHeight,
            contentBounds.x(), contentBounds.y(),
            contentBounds.width(), viewport
        );

        if (overflowWarning.isPresent()) {
            DebugWarning warning = overflowWarning.get();
            // Render overflow indicator at bottom of content area
            graphics.fill(contentBounds.x(), contentBounds.bottom() - OVERFLOW_BAR_HEIGHT,
                contentBounds.right(), contentBounds.bottom(), COLOR_OVERFLOW);
            graphics.drawString(safeFont, warning.type().getIcon() + " " + warning.message(),
                contentBounds.x() + OVERFLOW_TEXT_OFFSET_X, contentBounds.bottom() - OVERFLOW_TEXT_OFFSET_Y,
                warning.type().getColor(), false);
        }

        // Also render warnings from registered reporters
        renderCollectedWarnings(graphics, safeFont);
    }

    /**
     * Inject performance info (optional).
     */
    public static void setPerformanceLine(String line) {
        perfLine = line;
    }
    
    /**
     * Render 4px grid overlay.
     */
    private static void renderGrid(GuiGraphics graphics, int startX, int startY, int width, int height) {
        int endX = startX + width;
        int endY = startY + height;
        
        // Vertical lines
        for (int x = startX; x <= endX; x += GRID_SIZE) {
            int color = (x % GRID_MAJOR_STEP == 0) ? COLOR_GRID_MAJOR : COLOR_GRID;
            graphics.vLine(x, startY, endY, color);
        }
        
        // Horizontal lines
        for (int y = startY; y <= endY; y += GRID_SIZE) {
            int color = (y % GRID_MAJOR_STEP == 0) ? COLOR_GRID_MAJOR : COLOR_GRID;
            graphics.hLine(startX, endX, y, color);
        }
    }
    
    /**
     * Render zone boundaries (header, left, content, footer).
     */
    private static void renderZoneBoundaries(GuiGraphics graphics, Font font,
                                             Bounds headerBounds,
                                             Bounds leftBounds,
                                             Bounds footerBounds,
                                             Bounds panelBounds) {
        Font safeFont = Objects.requireNonNull(font, "font");
        int panelX = panelBounds.x();
        int panelY = panelBounds.y();
        int panelWidth = panelBounds.width();

        int headerBottom = headerBounds.bottom();
        int leftRight = leftBounds.right();
        int footerTop = footerBounds.y();

        graphics.hLine(panelX, panelX + panelWidth, headerBottom, COLOR_ZONE_BOUNDARY);
        graphics.vLine(leftRight, headerBottom, footerTop, COLOR_ZONE_BOUNDARY);
        graphics.hLine(panelX, panelX + panelWidth, footerTop, COLOR_ZONE_BOUNDARY);

        if (detailLevel == DetailLevel.HIGH) {
            graphics.drawString(safeFont, "HEADER", panelX + ZONE_LABEL_OFFSET, panelY + ZONE_LABEL_OFFSET,
                COLOR_INFO_TEXT, false);
            graphics.drawString(safeFont, "LEFT", panelX + ZONE_LABEL_OFFSET, headerBottom + ZONE_LABEL_OFFSET,
                COLOR_INFO_TEXT, false);
            graphics.drawString(safeFont, "CONTENT", leftRight + ZONE_LABEL_OFFSET, headerBottom + ZONE_LABEL_OFFSET,
                COLOR_INFO_TEXT, false);
            graphics.drawString(safeFont, "FOOTER", panelX + ZONE_LABEL_OFFSET, footerTop + ZONE_LABEL_OFFSET,
                COLOR_INFO_TEXT, false);
        }
    }

    /**
     * Render bounding boxes for quick visual reference.
     */
    private static void renderBoundingBoxes(GuiGraphics graphics, Bounds panelBounds,
                                            Bounds contentBounds) {
        graphics.renderOutline(panelBounds.x(), panelBounds.y(), panelBounds.width(), panelBounds.height(), COLOR_BBOX);
        graphics.renderOutline(contentBounds.x(), contentBounds.y(), contentBounds.width(), contentBounds.height(), COLOR_BBOX_HOVERED);
        int markerX = panelBounds.x() + BBOX_MARKER_OFFSET;
        int markerY = panelBounds.y() + BBOX_MARKER_OFFSET;
        graphics.fill(markerX, markerY, markerX + BBOX_MARKER_SIZE, markerY + BBOX_MARKER_SIZE, COLOR_WARNING);
    }
    
    /**
     * Render debug info panel in corner.
     */
    private static void renderInfoPanel(GuiGraphics graphics, Font font,
                                        Bounds panelBounds,
                                        Bounds contentBounds,
                                        int mouseX, int mouseY,
                                        int contentTotalHeight,
                                        int sectionCount,
                                        float scrollOffset,
                                        String hoveredComponent) {
        Font safeFont = Objects.requireNonNull(font, "font");
        int panelX = panelBounds.x();
        int panelY = panelBounds.y();
        int panelWidth = panelBounds.width();
        int panelHeight = panelBounds.height();

        int viewport = contentBounds.height();
        int maxScroll = Math.max(0, contentTotalHeight - viewport);

        // Build info lines using pooled list to reduce allocations
        RenderObjectPool pool = RenderObjectPool.INSTANCE;
        List<String> lines = pool.borrowStringList();

        // Use StringBuilderCache for formatted strings
        lines.add(StringBuilderCache.build(sb -> sb
            .append("Debug: ").append(detailLevel.name())
            .append(" | F9:Toggle F10:Grid F11:Bounds")));
        lines.add(StringBuilderCache.build(sb -> sb
            .append("Mouse: ").append(mouseX).append(",").append(mouseY)
            .append(" | Panel: ").append(panelWidth).append("x").append(panelHeight)));
        lines.add(StringBuilderCache.build(sb -> sb
            .append("Grid: ").append(showGrid ? "ON" : "OFF")
            .append(" | Bounds: ").append(showBounds ? "ON" : "OFF")));
        lines.add(StringBuilderCache.build(sb -> sb
            .append("Scroll: ").append((int) scrollOffset).append("/").append(maxScroll)
            .append(" | Sections: ").append(sectionCount)
            .append(" | H:").append(contentTotalHeight)));

        // Add hovered component info
        if (hoveredComponent != null && !hoveredComponent.isEmpty()) {
            lines.add("Hovered: " + hoveredComponent);
        }

        // Add warnings count
        if (!collectedWarnings.isEmpty()) {
            lines.add(StringBuilderCache.build(sb -> sb
                .append("\u26A0 Warnings: ").append(collectedWarnings.size())));
        }

        // Add performance line if set
        if (perfLine != null && !perfLine.isEmpty()) {
            lines.add(perfLine);
        }

        // Calculate panel size
        int infoWidth = INFO_PANEL_WIDTH;
        int infoHeight = lines.size() * INFO_PANEL_LINE_HEIGHT + INFO_PANEL_PADDING_DOUBLE;
        int infoX = panelX + panelWidth - infoWidth - INFO_PANEL_MARGIN_RIGHT;
        int infoY = panelY + panelHeight - INFO_PANEL_FOOTER_OFFSET - infoHeight - INFO_PANEL_MARGIN_BOTTOM;

        // Background
        graphics.fill(infoX, infoY, infoX + infoWidth, infoY + infoHeight, COLOR_INFO_BG);

        // Text
        int y = infoY + INFO_PANEL_PADDING;
        for (String line : lines) {
            int color = line.startsWith("\u26A0") ? COLOR_WARNING : COLOR_INFO_TEXT;
            graphics.drawString(safeFont, line, infoX + INFO_PANEL_PADDING, y, color, false);
            y += INFO_PANEL_LINE_HEIGHT;
        }

        // Return list to pool
        pool.returnStringList(lines);
    }
    
    /**
     * Get current detail level.
     */
    public static DetailLevel getDetailLevel() {
        return detailLevel;
    }

    /**
     * Check if grid overlay is enabled.
     */
    public static boolean isGridEnabled() {
        return showGrid;
    }

    /**
     * Check if bounds overlay is enabled.
     */
    public static boolean isBoundsEnabled() {
        return showBounds;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DebugReporter support
    // ═══════════════════════════════════════════════════════════════════════════

    private static final List<DebugReporter> reporters = new ArrayList<>();
    private static final List<DebugWarning> collectedWarnings = new ArrayList<>();

    /**
     * Register a component as a debug reporter.
     * Registered reporters will have their bounds drawn and warnings collected.
     *
     * @param reporter The component to register
     */
    public static void registerReporter(DebugReporter reporter) {
        if (reporter != null && !reporters.contains(reporter)) {
            reporters.add(reporter);
        }
    }

    /**
     * Unregister a debug reporter.
     *
     * @param reporter The component to unregister
     */
    public static void unregisterReporter(DebugReporter reporter) {
        reporters.remove(reporter);
    }

    /**
     * Clear all registered reporters.
     * Call this when closing the editor screen.
     */
    public static void clearReporters() {
        reporters.clear();
        collectedWarnings.clear();
    }

    /**
     * Collect warnings from all registered reporters.
     * Call this at the start of each frame before rendering.
     */
    public static void collectWarnings() {
        collectedWarnings.clear();
        for (DebugReporter reporter : reporters) {
            collectedWarnings.addAll(reporter.getDebugWarnings());
        }
    }

    /**
     * Get all collected warnings.
     *
     * @return List of warnings from all reporters
     */
    public static List<DebugWarning> getCollectedWarnings() {
        return List.copyOf(collectedWarnings);
    }

    /**
     * Render bounds for all registered reporters.
     *
     * @param graphics The graphics context
     * @param font     The font for labels
     * @param mouseX   Current mouse X
     * @param mouseY   Current mouse Y
     */
    public static void renderReporterBounds(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!enabled || detailLevel == DetailLevel.LOW) return;

        Font safeFont = Objects.requireNonNull(font, "font");
        for (DebugReporter reporter : reporters) {
            Bounds bounds = reporter.getDebugBounds();
            if (bounds == null || bounds == Bounds.EMPTY) continue;

            boolean hovered = bounds.contains(mouseX, mouseY);
            int color = hovered ? COLOR_BBOX_HOVERED : COLOR_BBOX;

            graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);

            // Show name on hover or in HIGH detail
            if (hovered || detailLevel == DetailLevel.HIGH) {
                String name = reporter.getDebugName();
                if (name != null && !name.isEmpty()) {
                    graphics.drawString(safeFont, name, bounds.x() + REPORTER_LABEL_OFFSET, bounds.y() + REPORTER_LABEL_OFFSET,
                        COLOR_INFO_TEXT, false);
                }

                // Show details on hover
                if (hovered) {
                    String details = reporter.getDebugDetails();
                    if (details != null) {
                        graphics.drawString(safeFont, details, bounds.x() + REPORTER_LABEL_OFFSET,
                            bounds.y() + REPORTER_DETAIL_OFFSET_Y, COLOR_INFO_TEXT, false);
                    }
                }
            }
        }
    }

    /**
     * Render all collected warnings.
     *
     * @param graphics The graphics context
     * @param font     The font for labels
     */
    public static void renderCollectedWarnings(GuiGraphics graphics, Font font) {
        if (!enabled) return;

        Font safeFont = Objects.requireNonNull(font, "font");
        for (DebugWarning warning : collectedWarnings) {
            // Red/colored overlay on problem area
            int overlayColor = (warning.type().getColor() & 0x00FFFFFF) | WARNING_OVERLAY_ALPHA;
            graphics.fill(warning.x(), warning.y(),
                warning.x() + warning.width(), warning.y() + warning.height(),
                overlayColor);

            // Warning icon and message
            String text = warning.type().getIcon() + ": " + warning.message();
            graphics.drawString(safeFont, text, warning.x(), warning.y() - WARNING_LABEL_OFFSET_Y,
                warning.type().getColor(), false);
        }
    }

    /**
     * Build a DebugInfo record from current state.
     *
     * @param panelWidth         Panel width
     * @param panelHeight        Panel height
     * @param scrollOffset       Current scroll offset
     * @param maxScroll          Maximum scroll value
     * @param visibleSections    Number of visible sections
     * @param totalSections      Total number of sections
     * @param mouseX             Mouse X position
     * @param mouseY             Mouse Y position
     * @param hoveredComponent   Name of hovered component
     * @param focusedComponent   Name of focused component
     * @return DebugInfo record with current state
     */
    public static DebugInfo buildDebugInfo(
            int panelWidth, int panelHeight,
            float scrollOffset, float maxScroll,
            int visibleSections, int totalSections,
            int mouseX, int mouseY,
            String hoveredComponent, String focusedComponent) {

        return DebugInfo.builder()
            .scale(1.0f) // NOTE: Use ScaledCoord value when wiring editor scale here.
            .gridSize(GRID_SIZE)
            .panelSize(panelWidth, panelHeight)
            .scroll(scrollOffset, maxScroll)
            .sections(visibleSections, totalSections)
            .mouse(mouseX, mouseY)
            .hoveredComponent(hoveredComponent)
            .focusedComponent(focusedComponent)
            .warnings(collectedWarnings)
            .build();
    }
}
