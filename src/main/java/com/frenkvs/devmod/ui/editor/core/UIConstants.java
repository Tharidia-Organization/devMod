package com.frenkvs.devmod.ui.editor.core;

/**
 * Centralized color constants for the editor UI.
 * ALL colors must be defined here - no magic numbers in components.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#14-color-system
 */
public final class UIConstants {

    private UIConstants() {}

    // ═══════════════════════════════════════════════════════════════
    // BACKGROUND COLORS
    // ═══════════════════════════════════════════════════════════════

    public static final class Background {
        /** Main panel background - semi-transparent dark */
        public static int PANEL() { return ThemeManager.get().panelBg(); }
        public static final int PANEL = 0xE0181818;

        /** Solid variant for dialogs/overlays */
        public static int PANEL_SOLID() { return ThemeManager.get().panelBgSolid(); }
        public static final int PANEL_SOLID = 0xFF181818;

        /** Input fields, inactive areas */
        public static int INPUT() { return ThemeManager.get().inputBg(); }
        public static final int INPUT = 0xFF252525;

        /** Hover state for buttons/items */
        public static int HOVER() { return ThemeManager.get().hoverBg(); }
        public static final int HOVER = 0xFF353535;

        /** Active/pressed state */
        public static int ACTIVE() { return ThemeManager.get().activeBg(); }
        public static final int ACTIVE = 0xFF454545;

        /** Darker areas (scrollbar track, separators) */
        public static int DARKER() { return ThemeManager.get().current().darkerBackground(); }
        public static final int DARKER = 0xFF0D0D0D;

        /** Header/footer background */
        public static int HEADER() { return ThemeManager.get().headerBg(); }
        public static final int HEADER = 0xFF1A1A1A;

        /** Content area background */
        public static int CONTENT() { return ThemeManager.get().contentBg(); }
        public static final int CONTENT = 0xFF202020;

        /** Tab inactive background */
        public static int TAB_INACTIVE() { return ThemeManager.get().current().tabInactiveBackground(); }
        public static final int TAB_INACTIVE = 0xFF282828;

        /** Tab active background */
        public static int TAB_ACTIVE() { return ThemeManager.get().current().tabActiveBackground(); }
        public static final int TAB_ACTIVE = 0xFF383838;

        /** Overlay/modal backdrop */
        public static int OVERLAY() { return ThemeManager.get().overlayBg(); }
        public static final int OVERLAY = 0x80000000;

        private Background() {}
    }

    // ═══════════════════════════════════════════════════════════════
    // BORDER COLORS
    // ═══════════════════════════════════════════════════════════════

    public static final class Border {
        /** Default border */
        public static int DEFAULT() { return ThemeManager.get().border(); }
        public static final int DEFAULT = 0xFF3A3A3A;

        /** Muted/subtle border */
        public static int MUTED() { return ThemeManager.get().borderMuted(); }
        public static final int MUTED = 0xFF2A2A2A;

        /** Accent/focused border */
        public static int ACCENT() { return ThemeManager.get().borderAccent(); }
        public static final int ACCENT = 0xFF00D4FF;

        /** Separator lines */
        public static int SEPARATOR() { return ThemeManager.get().separator(); }
        public static final int SEPARATOR = 0xFF333333;

        /** Success state border */
        public static int SUCCESS() { return ThemeManager.get().success(); }
        public static final int SUCCESS = 0xFF4CAF50;

        /** Error state border */
        public static int ERROR() { return ThemeManager.get().error(); }
        public static final int ERROR = 0xFFE53935;

        /** Warning state border */
        public static int WARNING() { return ThemeManager.get().warning(); }
        public static final int WARNING = 0xFFFF9800;

        /** Hover state border */
        public static int HOVER() { return ThemeManager.get().current().borderHover(); }
        public static final int HOVER = 0xFF5A5A5A;

        private Border() {}
    }

    // ═══════════════════════════════════════════════════════════════
    // TEXT COLORS
    // ═══════════════════════════════════════════════════════════════

    public static final class Text {
        /** Primary text - high contrast */
        public static int PRIMARY() { return ThemeManager.get().textPrimary(); }
        public static final int PRIMARY = 0xFFE0E0E0;

        /** Secondary text - medium contrast */
        public static int SECONDARY() { return ThemeManager.get().textSecondary(); }
        public static final int SECONDARY = 0xFFAAAAAA;

        /** Muted text - low contrast */
        public static int MUTED() { return ThemeManager.get().textMuted(); }
        public static final int MUTED = 0xFF666666;

        /** Title text - bright */
        public static int TITLE() { return ThemeManager.get().textTitle(); }
        public static final int TITLE = 0xFFFFFFFF;

        /** Value text - slightly cyan tinted */
        public static int VALUE() { return ThemeManager.get().current().textValue(); }
        public static final int VALUE = 0xFFB0E0E6;

        /** Formula/code text - monospace color */
        public static int FORMULA() { return ThemeManager.get().current().textFormula(); }
        public static final int FORMULA = 0xFF98D4A4;

        /** Disabled text */
        public static int DISABLED() { return ThemeManager.get().textDisabled(); }
        public static final int DISABLED = 0xFF555555;

        /** Link text */
        public static final int LINK = 0xFF4FC3F7;

        /** Info text - blue tint */
        public static int INFO() { return ThemeManager.get().info(); }
        public static final int INFO = 0xFF2196F3;

        /** Warning text - orange tint */
        public static int WARNING() { return ThemeManager.get().warning(); }
        public static final int WARNING = 0xFFFF9800;

        private Text() {}
    }

    // ═══════════════════════════════════════════════════════════════
    // ACCENT COLORS (Semantic)
    // ═══════════════════════════════════════════════════════════════

    public static final class Accent {
        /** Primary accent - cyan */
        public static int CYAN() { return ThemeManager.get().accent(); }
        public static final int CYAN = 0xFF00D4FF;

        /** Success - green */
        public static int GREEN() { return ThemeManager.get().success(); }
        public static final int GREEN = 0xFF4CAF50;

        /** Warning - orange */
        public static int ORANGE() { return ThemeManager.get().warning(); }
        public static final int ORANGE = 0xFFFF9800;

        /** Error/danger - red */
        public static int RED() { return ThemeManager.get().error(); }
        public static final int RED = 0xFFE53935;

        /** Info - blue */
        public static int BLUE() { return ThemeManager.get().info(); }
        public static final int BLUE = 0xFF2196F3;

        /** Special/rare - purple */
        public static final int PURPLE = 0xFFAB47BC;

        /** Highlight - yellow */
        public static final int YELLOW = 0xFFFFEB3B;

        // Semantic aliases
        public static int PRIMARY() { return ThemeManager.get().accent(); }
        public static final int PRIMARY = CYAN;
        public static final int POSITIVE = GREEN;
        public static final int WARNING = ORANGE;
        public static final int NEGATIVE = RED;
        public static int INFO() { return ThemeManager.get().info(); }
        public static final int INFO = BLUE;

        private Accent() {}
    }

    // ═══════════════════════════════════════════════════════════════
    // SLIDER TRACK COLORS (per attribute type)
    // ═══════════════════════════════════════════════════════════════

    public static final class SliderColors {
        /** Damage-related attributes */
        public static final int DAMAGE = 0xFFE53935;

        /** Defense-related attributes */
        public static final int DEFENSE = 0xFF2196F3;

        /** Speed-related attributes */
        public static final int SPEED = 0xFF4CAF50;

        /** Durability-related attributes */
        public static final int DURABILITY = 0xFFFF9800;

        /** Special/magic attributes */
        public static final int SPECIAL = 0xFFAB47BC;

        /** Generic/neutral attributes */
        public static final int NEUTRAL = 0xFF78909C;

        /** Percentage-based attributes */
        public static final int PERCENT = 0xFF26C6DA;

        private SliderColors() {}
    }

    // ═══════════════════════════════════════════════════════════════
    // MODE COLORS
    // ═══════════════════════════════════════════════════════════════

    public static final class Mode {
        /** GLOBAL mode - affects all items of type */
        public static final int GLOBAL_BORDER = 0xFFFF9800;
        public static final int GLOBAL_BG = 0x40FF9800;

        /** SPECIFIC mode - affects only this item */
        public static final int SPECIFIC_BORDER = 0xFF4CAF50;
        public static final int SPECIFIC_BG = 0x404CAF50;

        /** PREVIEW mode - no changes saved */
        public static final int PREVIEW_BORDER = 0xFFFFEB3B;
        public static final int PREVIEW_BG = 0x40FFEB3B;

        /** APPLY mode - changes will be saved */
        public static final int APPLY_BORDER = 0xFF4CAF50;
        public static final int APPLY_BG = 0x404CAF50;

        private Mode() {}
    }

    // ═══════════════════════════════════════════════════════════════
    // RARITY COLORS (for item value display)
    // ═══════════════════════════════════════════════════════════════

    public static final class Rarity {
        public static final int COMMON = 0xFF888888;
        public static final int UNCOMMON = 0xFF55FF55;
        public static final int RARE = 0xFF5555FF;
        public static final int EPIC = 0xFFAA00AA;
        public static final int LEGENDARY = 0xFFFFAA00;

        private Rarity() {}
    }

    // ═══════════════════════════════════════════════════════════════
    // COLOR PALETTE - BUTTONS
    // ═══════════════════════════════════════════════════════════════

    public static final class Button {
        /** Normal state */
        public static int NORMAL() { return ThemeManager.get().btnNormal(); }
        public static final int NORMAL = 0xFF2A2A2A;
        /** Hover state */
        public static int HOVER() { return ThemeManager.get().btnHover(); }
        public static final int HOVER = 0xFF3A3A3A;
        /** Pressed state */
        public static int PRESSED() { return ThemeManager.get().btnPressed(); }
        public static final int PRESSED = 0xFF1A1A1A;
        /** Disabled state */
        public static int DISABLED() { return ThemeManager.get().btnDisabled(); }
        public static final int DISABLED = 0xFF1A1A1A;
        /** Focused state */
        public static final int FOCUSED = 0xFF3A3A5A;
        /** Primary action (Apply) */
        public static final int PRIMARY = 0xFF2A5A2A;
        /** Primary hover */
        public static final int PRIMARY_HOVER = 0xFF3A7A3A;
        /** Primary pressed */
        public static final int PRIMARY_PRESS = 0xFF1A4A1A;
        /** Danger action (Delete) */
        public static final int DANGER = 0xFF5A2A2A;
        /** Danger hover */
        public static final int DANGER_HOVER = 0xFF7A3A3A;

        // Aliases
        public static final int PRESS = PRESSED;

        private Button() {}
    }

    /**
     * Palette Impact HUD per i bottoni (usata da EditorButton).
     */
    public static final class ImpactButton {
        // Base neutro
        public static final int DEFAULT_BASE = darken(Background.PANEL_SOLID, 0.08f);
        public static final int DEFAULT_BORDER = Border.DEFAULT;
        public static final int GHOST_BASE = darken(Background.PANEL_SOLID, 0.16f);
        public static final int GHOST_BORDER = Border.MUTED;

        // Primari (teal)
        public static final int PRIMARY_BASE = 0xFF0E5569;
        public static final int PRIMARY_BORDER = 0xFF1A8BAA;

        // Danger (rosso scuro)
        public static final int DANGER_BASE = 0xFF7A1A1E;
        public static final int DANGER_BORDER = 0xFFB23036;

        // Success (verde bosco)
        public static final int SUCCESS_BASE = 0xFF1F6A3F;
        public static final int SUCCESS_BORDER = 0xFF2DA45A;

        private ImpactButton() {}
    }

    // ═══════════════════════════════════════════════════════════════
    // COLOR PALETTE - SLIDERS
    // ═══════════════════════════════════════════════════════════════

    public static final class Slider {
        /** Track background */
        public static final int TRACK = 0xFF2A2A2A;
        /** Track active */
        public static final int TRACK_ACTIVE = 0xFF4A4A4A;
        /** Track disabled */
        public static final int TRACK_DISABLED = 0xFF1A1A1A;
        /** Filled portion */
        public static final int FILLED = 0xFF3A7AFF;
        /** Thumb normal */
        public static final int THUMB = 0xFF5A5A5A;
        /** Thumb hover */
        public static final int THUMB_HOVER = 0xFF7A7A7A;
        /** Thumb dragging */
        public static final int THUMB_DRAG = 0xFF3A7AFF;
        /** Thumb disabled */
        public static final int THUMB_DISABLED = 0xFF3A3A3A;

        private Slider() {}
    }

    // ═══════════════════════════════════════════════════════════════
    // COLOR PALETTE - TABS
    // ═══════════════════════════════════════════════════════════════

    public static final class Tab {
        /** Normal background */
        public static final int NORMAL = 0xFF1E1E1E;
        /** Hover background */
        public static final int HOVER = 0xFF2E2E2E;
        /** Selected background */
        public static final int SELECTED = 0xFF3A3A3A;
        /** Disabled background */
        public static final int DISABLED = 0xFF151515;
        /** Selected indicator */
        public static final int INDICATOR = 0xFF3A7AFF;

        private Tab() {}
    }

    // ═══════════════════════════════════════════════════════════════
    // SPACING & DIMENSIONS
    // ═══════════════════════════════════════════════════════════════

    public static final class Spacing {
        /** Extra small padding */
        public static final int XS = 2;
        /** Small padding */
        public static final int SM = 4;
        /** Medium padding */
        public static final int MD = 8;
        /** Large padding */
        public static final int LG = 12;
        /** Extra large padding */
        public static final int XL = 16;
        /** Double extra large */
        public static final int XXL = 24;

        private Spacing() {}
    }

    public static final class Size {
        /** Default button height */
        public static final int BUTTON_HEIGHT = EditorDimensions.BTN_HEIGHT_SMALL;
        /** Default button width */
        public static final int BUTTON_WIDTH = 80;
        /** Tab height - EDITOR_DESIGN_SYSTEM.md Section 2.4 */
        public static final int TAB_HEIGHT = EditorDimensions.TAB_HEIGHT;
        /** Tab width (aligned to 4px grid) */
        public static final int TAB_WIDTH = EditorDimensions.TAB_MIN_WIDTH;
        /** Tab gap */
        public static final int TAB_GAP = EditorDimensions.TAB_GAP;
        /** Slider height */
        public static final int SLIDER_HEIGHT = EditorDimensions.SLIDER_HEIGHT;
        /** Slider thumb size */
        public static final int SLIDER_THUMB = EditorDimensions.SLIDER_THUMB_SIZE;
        /** Default slider width */
        public static final int SLIDER_WIDTH = 200;
        /** Input field height */
        public static final int INPUT_HEIGHT = EditorDimensions.INPUT_HEIGHT;
        /** Line height for text */
        public static final int LINE_HEIGHT = 10;
        /** Header height - EDITOR_DESIGN_SYSTEM.md Section 2.4 */
        public static final int HEADER_HEIGHT = EditorConstants.HEADER_HEIGHT;
        /** Footer height - EDITOR_DESIGN_SYSTEM.md Section 2.7 */
        public static final int FOOTER_HEIGHT = EditorConstants.FOOTER_HEIGHT;
        /** Icon size */
        public static final int ICON = EditorDimensions.ICON_NORMAL;
        /** Small icon */
        public static final int ICON_SM = EditorDimensions.ICON_SMALL;
        /** Large icon */
        public static final int ICON_LG = EditorDimensions.ICON_LARGE;
        /** Scrollbar width */
        public static final int SCROLLBAR_WIDTH = EditorDimensions.SCROLLBAR_WIDTH;
        /** Slot size */
        public static final int SLOT_SIZE = EditorDimensions.SLOT_SIZE;

        private Size() {}
    }

    /**
     * Panel dimension constants.
     * Based on EDITOR_DESIGN_SYSTEM.md Section 2.1
     */
    public static final class PanelDimensions {
        public static final int PANEL_WIDTH = EditorConstants.PANEL_WIDTH;
        public static final int PANEL_HEIGHT = EditorConstants.PANEL_HEIGHT;
        public static final int LEFT_COLUMN_WIDTH = EditorConstants.LEFT_COLUMN_WIDTH;
        public static final int CONTENT_WIDTH = EditorConstants.CONTENT_WIDTH;
        public static final int CONTENT_HEIGHT = EditorConstants.CONTENT_HEIGHT;
        public static final int PREVIEW_SIZE = EditorConstants.PREVIEW_SIZE;
        public static final int SLOT_AREA_HEIGHT = EditorConstants.SLOT_AREA_HEIGHT;
        public static final int INFO_PANEL_HEIGHT = EditorConstants.INFO_PANEL_HEIGHT;

        private PanelDimensions() {}
    }

    // ═══════════════════════════════════════════════════════════════
    // ANIMATION TIMING (all 0 for immediate mode)
    // ═══════════════════════════════════════════════════════════════

    public static final class Timing {
        /** Fade duration (disabled) */
        public static final int FADE_MS = 0;
        /** Slide duration (disabled) */
        public static final int SLIDE_MS = 0;
        /** Tooltip delay */
        public static final int TOOLTIP_DELAY_MS = 200;
        /** Button press feedback */
        public static final int BUTTON_PRESS_MS = 0;

        private Timing() {}
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get color with modified alpha.
     */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /**
     * Interpolate between two colors.
     */
    public static int lerp(int color1, int color2, float t) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Darken a color by a factor.
     */
    public static int darken(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * (1 - factor));
        int g = (int) (((color >> 8) & 0xFF) * (1 - factor));
        int b = (int) ((color & 0xFF) * (1 - factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Lighten a color by a factor.
     */
    public static int lighten(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) + (255 - (color & 0xFF)) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
