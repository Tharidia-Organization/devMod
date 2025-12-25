package com.devmod.client.ui.editor.debug;

import java.util.List;

/**
 * Debug information displayed in overlay.
 * Aggregates all debug state for a single frame.
 *
 * @see docs/editor-design-system/14-debug-overlay.md
 */
public record DebugInfo(
    // Layout
    float scale,
    int gridSize,
    int panelWidth,
    int panelHeight,

    // Scroll
    float scrollOffset,
    float maxScroll,
    int visibleSections,
    int totalSections,

    // Performance
    int fps,
    long frameTimeMs,
    int renderCalls,

    // Interaction
    int mouseX,
    int mouseY,
    String hoveredComponent,
    String focusedComponent,

    // Warnings
    List<DebugWarning> warnings
) {
    /**
     * Builder for creating DebugInfo instances.
     */
    public static final class Builder {
        private float scale = 1.0f;
        private int gridSize = 4;
        private int panelWidth;
        private int panelHeight;
        private float scrollOffset;
        private float maxScroll;
        private int visibleSections;
        private int totalSections;
        private int fps;
        private long frameTimeMs;
        private int renderCalls;
        private int mouseX;
        private int mouseY;
        private String hoveredComponent = "";
        private String focusedComponent = "";
        private List<DebugWarning> warnings = List.of();

        public Builder scale(float scale) { this.scale = scale; return this; }
        public Builder gridSize(int gridSize) { this.gridSize = gridSize; return this; }
        public Builder panelSize(int width, int height) {
            this.panelWidth = width;
            this.panelHeight = height;
            return this;
        }
        public Builder scroll(float offset, float max) {
            this.scrollOffset = offset;
            this.maxScroll = max;
            return this;
        }
        public Builder sections(int visible, int total) {
            this.visibleSections = visible;
            this.totalSections = total;
            return this;
        }
        public Builder performance(int fps, long frameTimeMs, int renderCalls) {
            this.fps = fps;
            this.frameTimeMs = frameTimeMs;
            this.renderCalls = renderCalls;
            return this;
        }
        public Builder mouse(int x, int y) {
            this.mouseX = x;
            this.mouseY = y;
            return this;
        }
        public Builder hoveredComponent(String component) {
            this.hoveredComponent = component != null ? component : "";
            return this;
        }
        public Builder focusedComponent(String component) {
            this.focusedComponent = component != null ? component : "";
            return this;
        }
        public Builder warnings(List<DebugWarning> warnings) {
            this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
            return this;
        }

        public DebugInfo build() {
            return new DebugInfo(
                scale, gridSize, panelWidth, panelHeight,
                scrollOffset, maxScroll, visibleSections, totalSections,
                fps, frameTimeMs, renderCalls,
                mouseX, mouseY, hoveredComponent, focusedComponent,
                warnings
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
