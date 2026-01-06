package com.devmod.client.ui.radial.config;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Default colors for the radial menu theme and presets.
 */
public final class RadialMenuThemeDefaults {
    private RadialMenuThemeDefaults() {}

    public static final class Core {
        public static final int BG_DARK = DesignTokens.RadialMenu.Core.BG_DARK;
        public static final int SELECTED_BG = DesignTokens.RadialMenu.Core.SELECTED_BG;
        public static final int MACRO_SELECTED_BASE = DesignTokens.RadialMenu.Core.MACRO_SELECTED_BASE;
        public static final int UNSELECTED_BG = DesignTokens.RadialMenu.Core.UNSELECTED_BG;
        public static final int BORDER = DesignTokens.RadialMenu.Core.BORDER;
        public static final int DIVIDER = DesignTokens.RadialMenu.Core.DIVIDER;
        public static final int MACRO_HOVER_BORDER = DesignTokens.RadialMenu.Core.MACRO_HOVER_BORDER;
        public static final int INNER_RING = DesignTokens.RadialMenu.Core.INNER_RING;
        public static final int CLOSE_HOVER = DesignTokens.RadialMenu.Core.CLOSE_HOVER;
        public static final int CLOSE_NORMAL = DesignTokens.RadialMenu.Core.CLOSE_NORMAL;
        public static final int CLOSE_BORDER_HOVER = DesignTokens.RadialMenu.Core.CLOSE_BORDER_HOVER;
        public static final int CENTER_ICON_BACK = DesignTokens.RadialMenu.Core.CENTER_ICON_BACK;
        public static final int TEXT_PRIMARY = DesignTokens.RadialMenu.Core.TEXT_PRIMARY;
        public static final int TEXT_SECONDARY = DesignTokens.RadialMenu.Core.TEXT_SECONDARY;
        public static final int INACTIVE = DesignTokens.RadialMenu.Core.INACTIVE;

        private Core() {}
    }

    public static final class Badge {
        public static final int BG = DesignTokens.RadialMenu.Badge.BG;

        private Badge() {}
    }

    public static final class Favorites {
        public static final int BG_SELECTED = DesignTokens.RadialMenu.Favorites.BG_SELECTED;
        public static final int BG_UNSELECTED = DesignTokens.RadialMenu.Favorites.BG_UNSELECTED;
        public static final int STAR = DesignTokens.RadialMenu.Favorites.STAR;

        private Favorites() {}
    }

    public static final class Item {
        public static final int STATUS_INACTIVE = DesignTokens.RadialMenu.Item.STATUS_INACTIVE;

        private Item() {}
    }

    public static final class Overlay {
        public static final int BACKGROUND_RGB = DesignTokens.RadialMenu.Overlay.BACKGROUND_RGB;
        public static final int TOOLTIP_BG = DesignTokens.RadialMenu.Overlay.TOOLTIP_BG;
        public static final int SEARCH_BOX_BG = DesignTokens.RadialMenu.Overlay.SEARCH_BOX_BG;
        public static final int SEARCH_RESULT_BG = DesignTokens.RadialMenu.Overlay.SEARCH_RESULT_BG;
        public static final int BREADCRUMB = DesignTokens.RadialMenu.Overlay.BREADCRUMB;
        public static final int EDIT_MODE_BG = DesignTokens.RadialMenu.Overlay.EDIT_MODE_BG;
        public static final int EDIT_MODE_TEXT = DesignTokens.RadialMenu.Overlay.EDIT_MODE_TEXT;
        public static final int THEME_INDICATOR_RGB = DesignTokens.RadialMenu.Overlay.THEME_INDICATOR_RGB;

        private Overlay() {}
    }

    public static final class Base {
        public static final int BG_DARK = DesignTokens.RadialMenu.Base.BG_DARK;
        public static final int BG_LIGHT = DesignTokens.RadialMenu.Base.BG_LIGHT;
        public static final int SELECTED = DesignTokens.RadialMenu.Base.SELECTED;
        public static final int HOVER = DesignTokens.RadialMenu.Base.HOVER;
        public static final int ACTIVE = DesignTokens.RadialMenu.Base.ACTIVE;
        public static final int ACTIVE_GLOW = DesignTokens.RadialMenu.Base.ACTIVE_GLOW;
        public static final int INACTIVE = DesignTokens.RadialMenu.Base.INACTIVE;
        public static final int TEXT_PRIMARY = DesignTokens.RadialMenu.Base.TEXT_PRIMARY;
        public static final int TEXT_SECONDARY = DesignTokens.RadialMenu.Base.TEXT_SECONDARY;
        public static final int TEXT_HIGHLIGHT = DesignTokens.RadialMenu.Base.TEXT_HIGHLIGHT;
        public static final int BORDER = DesignTokens.RadialMenu.Base.BORDER;
        public static final int BORDER_GLOW = DesignTokens.RadialMenu.Base.BORDER_GLOW;
        private static final int[] CATEGORY_COLORS = DesignTokens.RadialMenu.Base.categoryColors();

        public static int[] categoryColors() {
            return CATEGORY_COLORS.clone();
        }

        private Base() {}
    }

    public static final class ThemePresetValues {
        public final int bgDark;
        public final int bgLight;
        public final int selected;
        public final int hover;
        public final int active;
        public final int activeGlow;
        public final int border;
        public final int textPrimary;
        public final int textSecondary;

        private ThemePresetValues(int bgDark, int bgLight, int selected, int hover,
                                  int active, int activeGlow, int border,
                                  int textPrimary, int textSecondary) {
            this.bgDark = bgDark;
            this.bgLight = bgLight;
            this.selected = selected;
            this.hover = hover;
            this.active = active;
            this.activeGlow = activeGlow;
            this.border = border;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
        }
    }

    private static ThemePresetValues fromPreset(DesignTokens.RadialMenu.PresetValues preset) {
        return new ThemePresetValues(
            preset.bgDark,
            preset.bgLight,
            preset.selected,
            preset.hover,
            preset.active,
            preset.activeGlow,
            preset.border,
            preset.textPrimary,
            preset.textSecondary
        );
    }

    public static final class Presets {
        public static final ThemePresetValues DEFAULT = fromPreset(DesignTokens.RadialMenu.Presets.DEFAULT);
        public static final ThemePresetValues NEON = fromPreset(DesignTokens.RadialMenu.Presets.NEON);
        public static final ThemePresetValues CRIMSON = fromPreset(DesignTokens.RadialMenu.Presets.CRIMSON);
        public static final ThemePresetValues FOREST = fromPreset(DesignTokens.RadialMenu.Presets.FOREST);
        public static final ThemePresetValues GOLD = fromPreset(DesignTokens.RadialMenu.Presets.GOLD);
        public static final ThemePresetValues MIDNIGHT = fromPreset(DesignTokens.RadialMenu.Presets.MIDNIGHT);
        public static final ThemePresetValues MINIMAL = fromPreset(DesignTokens.RadialMenu.Presets.MINIMAL);
        public static final ThemePresetValues COLORBLIND = fromPreset(DesignTokens.RadialMenu.Presets.COLORBLIND);
        public static final ThemePresetValues HIGH_CONTRAST = fromPreset(DesignTokens.RadialMenu.Presets.HIGH_CONTRAST);

        private Presets() {}
    }
}
