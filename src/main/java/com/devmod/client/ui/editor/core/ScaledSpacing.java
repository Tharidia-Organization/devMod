package com.devmod.ui.editor.core;

/**
 * Scaled spacing values for use in render code.
 * All methods return spacing tokens scaled by the current UI scale
 * and aligned to the 4px grid.
 *
 * @see EditorSpacing for base unscaled values
 * @see ScaledCoord#scale(int) for the scaling logic
 * @see docs/editor-design-system/12-grid-spacing.md
 */
public final class ScaledSpacing {
    private ScaledSpacing() {}

    // =========================================================================
    // NAMED SPACING TOKENS (scaled)
    // =========================================================================

    /** @return Scaled XS spacing (base: 4px) - intra-component gaps */
    public static int xs() {
        return ScaledCoord.scale(EditorSpacing.XS);
    }

    /** @return Scaled S spacing (base: 8px) - component padding, small gaps */
    public static int s() {
        return ScaledCoord.scale(EditorSpacing.S);
    }

    /** @return Scaled M spacing (base: 12px) - section padding, medium gaps */
    public static int m() {
        return ScaledCoord.scale(EditorSpacing.M);
    }

    /** @return Scaled L spacing (base: 16px) - zone padding, large gaps */
    public static int l() {
        return ScaledCoord.scale(EditorSpacing.L);
    }

    /** @return Scaled XL spacing (base: 24px) - panel margins */
    public static int xl() {
        return ScaledCoord.scale(EditorSpacing.XL);
    }

    // =========================================================================
    // SEMANTIC ALIASES (scaled)
    // =========================================================================

    /** @return Scaled gap between components in a row (base: 8px) */
    public static int componentGap() {
        return ScaledCoord.scale(EditorSpacing.COMPONENT_GAP);
    }

    /** @return Scaled gap between sections (base: 12px) */
    public static int sectionGap() {
        return ScaledCoord.scale(EditorSpacing.SECTION_GAP);
    }

    /** @return Scaled gap between rows in a section (base: 8px) */
    public static int rowGap() {
        return ScaledCoord.scale(EditorSpacing.ROW_GAP);
    }

    /** @return Scaled content area padding (base: 8px) */
    public static int contentPadding() {
        return ScaledCoord.scale(EditorSpacing.CONTENT_PADDING);
    }

    /** @return Scaled horizontal button padding (base: 8px) */
    public static int buttonPaddingH() {
        return ScaledCoord.scale(EditorSpacing.BUTTON_PADDING_H);
    }

    /** @return Scaled vertical button padding (base: 4px) */
    public static int buttonPaddingV() {
        return ScaledCoord.scale(EditorSpacing.BUTTON_PADDING_V);
    }
}
