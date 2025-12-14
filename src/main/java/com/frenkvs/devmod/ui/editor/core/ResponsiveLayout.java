package com.frenkvs.devmod.ui.editor.core;

/**
 * Responsive layout system for editor screens.
 * Adapts UI to different screen sizes.
 *
 * Based on EDITOR_DESIGN_SYSTEM.md Section 2.40.2
 */
public class ResponsiveLayout {

    // ═══════════════════════════════════════════════════════════════
    // SCREEN SIZE ENUM
    // ═══════════════════════════════════════════════════════════════

    public enum ScreenSize {
        SMALL(0, 480),
        MEDIUM(480, 720),
        LARGE(720, 1080),
        XLARGE(1080, Integer.MAX_VALUE);

        private final int minWidth;
        private final int maxWidth;

        ScreenSize(int minWidth, int maxWidth) {
            this.minWidth = minWidth;
            this.maxWidth = maxWidth;
        }

        public static ScreenSize fromWidth(int width) {
            for (ScreenSize size : values()) {
                if (width >= size.minWidth && width < size.maxWidth) {
                    return size;
                }
            }
            return MEDIUM;
        }

        public boolean isAtLeast(ScreenSize other) {
            return this.ordinal() >= other.ordinal();
        }

        public int getMinWidth() { return minWidth; }
        public int getMaxWidth() { return maxWidth; }
    }

    // ═══════════════════════════════════════════════════════════════
    // LAYOUT CONSTRAINTS
    // ═══════════════════════════════════════════════════════════════

    public record LayoutConstraints(
        int minWidth,
        int maxWidth,
        int minHeight,
        int maxHeight,
        int paddingHorizontal,
        int paddingVertical
    ) {
        /**
         * Editor panel constraints.
         * Based on EDITOR_DESIGN_SYSTEM.md Section 2.1:
         * PANEL_WIDTH = 550, PANEL_HEIGHT = 420
         */
        public static final LayoutConstraints EDITOR_DEFAULT = new LayoutConstraints(
            320, 550, 240, 420, 10, 8
        );

        public static final LayoutConstraints DIALOG_DEFAULT = new LayoutConstraints(
            250, 400, 100, 200, 15, 12
        );

        public static final LayoutConstraints HELP_DEFAULT = new LayoutConstraints(
            300, 450, 200, 350, 12, 10
        );

        public int calculateWidth(int availableWidth) {
            int desired = availableWidth - paddingHorizontal * 2;
            return Math.max(minWidth, Math.min(maxWidth, desired));
        }

        public int calculateHeight(int availableHeight) {
            int desired = availableHeight - paddingVertical * 2;
            return Math.max(minHeight, Math.min(maxHeight, desired));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RECT HELPER
    // ═══════════════════════════════════════════════════════════════

    public record Rect(int x, int y, int width, int height) {
        public static final Rect EMPTY = new Rect(0, 0, 0, 0);

        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }

        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public int centerX() { return x + width / 2; }
        public int centerY() { return y + height / 2; }

        public Rect shrink(int amount) {
            return new Rect(x + amount, y + amount, width - amount * 2, height - amount * 2);
        }

        public Rect expand(int amount) {
            return new Rect(x - amount, y - amount, width + amount * 2, height + amount * 2);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INSTANCE FIELDS
    // ═══════════════════════════════════════════════════════════════

    private final LayoutConstraints constraints;
    private ScreenSize currentSize = ScreenSize.MEDIUM;
    private int screenWidth;
    private int screenHeight;

    // Calculated values
    private int editorX;
    private int editorY;
    private int editorWidth;
    private int editorHeight;
    private int contentPadding;
    private int sliderWidth;
    private int tabWidth;
    private boolean showSidePanels;
    private boolean compactMode;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public ResponsiveLayout() {
        this(LayoutConstraints.EDITOR_DEFAULT);
    }

    public ResponsiveLayout(LayoutConstraints constraints) {
        this.constraints = constraints;
    }

    // ═══════════════════════════════════════════════════════════════
    // CALCULATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Recalculate layout for screen dimensions.
     */
    public void calculate(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.currentSize = ScreenSize.fromWidth(screenWidth);

        // Calculate editor panel dimensions
        editorWidth = constraints.calculateWidth(screenWidth);
        editorHeight = constraints.calculateHeight(screenHeight);

        // Center the editor
        editorX = (screenWidth - editorWidth) / 2;
        editorY = (screenHeight - editorHeight) / 2;

        // Adjust internal layout based on size
        calculateInternalLayout();
    }

    private void calculateInternalLayout() {
        switch (currentSize) {
            case SMALL -> {
                contentPadding = 6;
                sliderWidth = editorWidth - 40;
                tabWidth = 50;
                showSidePanels = false;
                compactMode = true;
            }
            case MEDIUM -> {
                contentPadding = 8;
                sliderWidth = Math.min(250, editorWidth - 60);
                tabWidth = 60;
                showSidePanels = false;
                compactMode = false;
            }
            case LARGE -> {
                contentPadding = 10;
                sliderWidth = 280;
                tabWidth = 70;
                showSidePanels = true;
                compactMode = false;
            }
            case XLARGE -> {
                contentPadding = 12;
                sliderWidth = 320;
                tabWidth = 80;
                showSidePanels = true;
                compactMode = false;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public int getEditorX() { return editorX; }
    public int getEditorY() { return editorY; }
    public int getEditorWidth() { return editorWidth; }
    public int getEditorHeight() { return editorHeight; }
    public int getContentPadding() { return contentPadding; }
    public int getSliderWidth() { return sliderWidth; }
    public int getTabWidth() { return tabWidth; }
    public boolean showSidePanels() { return showSidePanels; }
    public boolean isCompactMode() { return compactMode; }
    public ScreenSize getScreenSize() { return currentSize; }
    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }
    public LayoutConstraints getConstraints() { return constraints; }

    // ═══════════════════════════════════════════════════════════════
    // AREA CALCULATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the main editor panel bounds.
     */
    public Rect getEditorArea() {
        return new Rect(editorX, editorY, editorWidth, editorHeight);
    }

    /**
     * Get header area bounds.
     */
    public Rect getHeaderArea() {
        return new Rect(
            editorX,
            editorY,
            editorWidth,
            UIConstants.Size.HEADER_HEIGHT
        );
    }

    /**
     * Get tab bar area bounds.
     */
    public Rect getTabBarArea() {
        return new Rect(
            editorX,
            editorY + UIConstants.Size.HEADER_HEIGHT,
            editorWidth,
            UIConstants.Size.TAB_HEIGHT + 4
        );
    }

    /**
     * Get content area bounds.
     */
    public Rect getContentArea() {
        int topOffset = UIConstants.Size.HEADER_HEIGHT + UIConstants.Size.TAB_HEIGHT + 8;
        int bottomOffset = UIConstants.Size.FOOTER_HEIGHT + 8;
        return new Rect(
            editorX + contentPadding,
            editorY + topOffset,
            editorWidth - contentPadding * 2,
            editorHeight - topOffset - bottomOffset
        );
    }

    /**
     * Get footer area bounds.
     */
    public Rect getFooterArea() {
        return new Rect(
            editorX + contentPadding,
            editorY + editorHeight - UIConstants.Size.FOOTER_HEIGHT - 4,
            editorWidth - contentPadding * 2,
            UIConstants.Size.FOOTER_HEIGHT
        );
    }

    /**
     * Get side panel bounds (if visible).
     */
    public Rect getSidePanelArea(boolean left) {
        if (!showSidePanels) return Rect.EMPTY;

        int panelWidth = 150;
        int x = left ? editorX - panelWidth - 10 : editorX + editorWidth + 10;

        return new Rect(x, editorY, panelWidth, editorHeight);
    }

    /**
     * Get left sidebar for favorites.
     */
    public Rect getFavoritesPanelArea() {
        return getSidePanelArea(true);
    }

    /**
     * Get right sidebar for dev mode.
     */
    public Rect getDevModePanelArea() {
        return getSidePanelArea(false);
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Calculate centered X position for an element.
     */
    public int centerX(int elementWidth) {
        return editorX + (editorWidth - elementWidth) / 2;
    }

    /**
     * Calculate centered Y position for an element.
     */
    public int centerY(int elementHeight) {
        return editorY + (editorHeight - elementHeight) / 2;
    }

    /**
     * Calculate slider X position (centered in content area).
     */
    public int getSliderX() {
        Rect content = getContentArea();
        return content.x() + (content.width() - sliderWidth) / 2;
    }

    /**
     * Get number of visible tabs based on width.
     */
    public int getVisibleTabCount() {
        int availableWidth = editorWidth - contentPadding * 2;
        return Math.max(3, availableWidth / (tabWidth + 4));
    }

    /**
     * Check if a point is within the editor bounds.
     */
    public boolean isInEditor(int x, int y) {
        return getEditorArea().contains(x, y);
    }

    /**
     * Check if a point is within the content area.
     */
    public boolean isInContent(int x, int y) {
        return getContentArea().contains(x, y);
    }
}
