package com.frenkvs.devmod.ui.editor.debug;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import org.lwjgl.glfw.GLFW;
import java.util.Objects;
import com.frenkvs.devmod.ui.editor.core.Bounds;

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
        
        // Layer 1: Grid (if enabled)
        if (showGrid || detailLevel == DetailLevel.HIGH) {
            renderGrid(safeGraphics, panelX, panelY, panelWidth, panelHeight);
        }
        
        // Layer 2: Zone boundaries
        renderZoneBoundaries(safeGraphics, safeFont, headerBounds, leftBounds, footerBounds, panelBounds);
        
        // Layer 3: Panel bounding boxes when bounds are shown
        if (showBounds || detailLevel != DetailLevel.LOW) {
            renderBoundingBoxes(safeGraphics, panelBounds, contentBounds);
        }

        // Layer 4: Overflow warning for content
        renderWarnings(safeGraphics, safeFont, contentBounds, contentTotalHeight, scrollOffset);

        // Layer 5: Info panel
        renderInfoPanel(safeGraphics, safeFont, panelBounds, contentBounds, mouseX, mouseY, contentTotalHeight, sectionCount, scrollOffset);
    }

    private static void renderWarnings(GuiGraphics graphics, Font font, Bounds contentBounds, int contentTotalHeight, float scrollOffset) {
        Font safeFont = java.util.Objects.requireNonNull(font, "font");
        int viewport = contentBounds.height();
        if (contentTotalHeight > viewport) {
            int overflow = contentTotalHeight - viewport;
            graphics.fill(contentBounds.x(), contentBounds.bottom() - 6, contentBounds.right(), contentBounds.bottom(),
                COLOR_OVERFLOW);
            graphics.drawString(safeFont, "Overflow +" + overflow + "px",
                contentBounds.x() + 4, contentBounds.bottom() - 14, COLOR_WARNING, false);
        }
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
        for (int x = startX; x <= endX; x += 4) {
            int color = (x % 16 == 0) ? 0x60FFFFFF : COLOR_GRID;
            graphics.vLine(x, startY, endY, color);
        }
        
        // Horizontal lines
        for (int y = startY; y <= endY; y += 4) {
            int color = (y % 16 == 0) ? 0x60FFFFFF : COLOR_GRID;
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
            graphics.drawString(safeFont, "HEADER", panelX + 4, panelY + 4, COLOR_INFO_TEXT, false);
            graphics.drawString(safeFont, "LEFT", panelX + 4, headerBottom + 4, COLOR_INFO_TEXT, false);
            graphics.drawString(safeFont, "CONTENT", leftRight + 4, headerBottom + 4, COLOR_INFO_TEXT, false);
            graphics.drawString(safeFont, "FOOTER", panelX + 4, footerTop + 4, COLOR_INFO_TEXT, false);
        }
    }

    /**
     * Render bounding boxes for quick visual reference.
     */
    private static void renderBoundingBoxes(GuiGraphics graphics, Bounds panelBounds,
                                            Bounds contentBounds) {
        graphics.renderOutline(panelBounds.x(), panelBounds.y(), panelBounds.width(), panelBounds.height(), COLOR_BBOX);
        graphics.renderOutline(contentBounds.x(), contentBounds.y(), contentBounds.width(), contentBounds.height(), COLOR_BBOX_HOVERED);
        graphics.fill(panelBounds.x() + 6, panelBounds.y() + 6, panelBounds.x() + 10, panelBounds.y() + 10, COLOR_WARNING);
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
                                        float scrollOffset) {
        Font safeFont = Objects.requireNonNull(font, "font");
        int panelX = panelBounds.x();
        int panelY = panelBounds.y();
        int panelWidth = panelBounds.width();
        int panelHeight = panelBounds.height();

        int viewport = contentBounds.height();
        int maxScroll = Math.max(0, contentTotalHeight - viewport);
        String[] lines = {
            "Debug: " + detailLevel.name() + " | F9:Toggle F10:Grid F11:Bounds",
            "Mouse: " + mouseX + "," + mouseY + " | Panel: " + panelWidth + "x" + panelHeight,
            "Grid: " + (showGrid ? "ON" : "OFF") + " | Bounds: " + (showBounds ? "ON" : "OFF"),
            "Scroll: " + (int) scrollOffset + "/" + maxScroll + " | Sections: " + sectionCount + " | Height: " + contentTotalHeight,
            perfLine == null ? "" : perfLine
        };
        
        // Calculate panel size
        int infoWidth = 320;
        int infoHeight = lines.length * 12 + 8;
        int infoX = panelX + panelWidth - infoWidth - 8;
        int infoY = panelY + panelHeight - 60 - infoHeight - 8; // Above footer
        
        // Background
        graphics.fill(infoX, infoY, infoX + infoWidth, infoY + infoHeight, COLOR_INFO_BG);
        
        // Text
        int y = infoY + 4;
        for (String line : lines) {
            if (!line.isEmpty()) {
                graphics.drawString(safeFont, line, infoX + 4, y, COLOR_INFO_TEXT, false);
                y += 12;
            }
            else {
                // skip empty lines without changing y
            }
        }
    }
    
    /**
     * Get current detail level.
     */
    public static DetailLevel getDetailLevel() {
        return detailLevel;
    }
}
