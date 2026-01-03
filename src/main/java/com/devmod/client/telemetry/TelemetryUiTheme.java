package com.devmod.client.telemetry;

import com.devmod.client.ui.overlay.OverlayTheme;

/**
 * Centralized color tokens for telemetry overlays.
 */
public final class TelemetryUiTheme {
    private TelemetryUiTheme() {}

    public static final class Panel {
        public static final int BG = OverlayTheme.Panel.BG_STANDARD;
        public static final int BORDER = OverlayTheme.Border.ACCENT;
        public static final int GLOW = OverlayTheme.withAlpha(OverlayTheme.Border.ACCENT, OverlayTheme.Alpha.GHOST);

        private Panel() {}
    }

    public static final class Text {
        public static final int CYAN = OverlayTheme.Text.TITLE;
        public static final int GREEN = OverlayTheme.Text.VALUE;
        public static final int YELLOW = OverlayTheme.Text.GOLD;
        public static final int RED = OverlayTheme.Text.DANGER;
        public static final int GRAY = OverlayTheme.Text.MUTED;

        private Text() {}
    }

    public static final class Graph {
        public static final int BG = OverlayTheme.Utility.BLACK;
        public static final int LINE_60 = OverlayTheme.withAlpha(OverlayTheme.Text.VALUE, OverlayTheme.Alpha.DIVIDER);
        public static final int LINE_30 = OverlayTheme.withAlpha(OverlayTheme.Text.WARNING, OverlayTheme.Alpha.DIVIDER);
        public static final int TARGET_LINE = OverlayTheme.withAlpha(OverlayTheme.Utility.WHITE, OverlayTheme.Alpha.DIVIDER);
        public static final int BAR_BG = OverlayTheme.Neutral.N840;
        public static final int BAR_BORDER = OverlayTheme.Neutral.N740;

        private Graph() {}
    }
}
