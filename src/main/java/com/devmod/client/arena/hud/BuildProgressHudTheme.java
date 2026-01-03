package com.devmod.client.arena.hud;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Color tokens for the arena build progress HUD.
 */
public final class BuildProgressHudTheme {
    private BuildProgressHudTheme() {}

    public static final class Panel {
        public static final int BACKGROUND = DesignTokens.BuildProgressHud.Panel.BACKGROUND;
        public static final int BORDER = DesignTokens.BuildProgressHud.Panel.BORDER;
        public static final int BAR_EMPTY = DesignTokens.BuildProgressHud.Panel.BAR_EMPTY;
        public static final int TEXT = DesignTokens.BuildProgressHud.Panel.TEXT;
        public static final int TEXT_SHADOW = DesignTokens.BuildProgressHud.Panel.TEXT_SHADOW;

        private Panel() {}
    }

    public static final class Progress {
        public static final int NORMAL = DesignTokens.BuildProgressHud.Progress.NORMAL;
        public static final int WARNING = DesignTokens.BuildProgressHud.Progress.WARNING;
        public static final int COMPLETE = DesignTokens.BuildProgressHud.Progress.COMPLETE;
        public static final int FAILED = DesignTokens.BuildProgressHud.Progress.FAILED;

        private Progress() {}
    }
}
