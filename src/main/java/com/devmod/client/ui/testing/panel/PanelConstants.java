package com.devmod.client.ui.testing.panel;

import com.devmod.client.ui.editor.core.DesignTokens;

// Shared constants for the panel system.
public final class PanelConstants {

    private PanelConstants() {}

    // Layout
    public static final int PANEL_SPACING = 8;
    public static final int ROW_SPACING = 4;
    public static final int CONTENT_PADDING = 4;

    // Heights
    public static final int TITLE_HEIGHT = 14;
    public static final int HEADER_HEIGHT = 20;
    public static final int ROW_HEIGHT = 14;
    public static final int DESCRIPTION_HEIGHT = 10;
    public static final int SEPARATOR_HEIGHT = 8;
    public static final int SLIDER_HEIGHT = 36;

    // Buttons
    public static final int BUTTON_HEIGHT_NORMAL = 22;
    public static final int BUTTON_HEIGHT_MEDIUM = 20;
    public static final int BUTTON_HEIGHT_SMALL = 18;
    public static final int BUTTON_SPACING = 8;

    // Scrollbar
    public static final int SCROLLBAR_WIDTH = 6;
    public static final int SCROLLBAR_PADDING = 2;
    public static final int SCROLLBAR_THUMB_MIN_HEIGHT = 20;

    // Animation
    public static final float ANIMATION_SPEED = 0.15f;

    // Colors (defaults)
    public static final int COLOR_SEPARATOR = DesignTokens.Testing.SEPARATOR;
    public static final int COLOR_THUMB_NORMAL = DesignTokens.Testing.THUMB_NORMAL;
    public static final int COLOR_THUMB_HOVER = DesignTokens.Testing.THUMB_HOVER;
    public static final int COLOR_TRACK = DesignTokens.Testing.TRACK;
    public static final int COLOR_HEADER_ACCENT = DesignTokens.Testing.HEADER_ACCENT;
    public static final int COLOR_SUCCESS = DesignTokens.Testing.SUCCESS;
    public static final int COLOR_ERROR = DesignTokens.Testing.ERROR;
    public static final int COLOR_CYAN = DesignTokens.Testing.CYAN;
}
