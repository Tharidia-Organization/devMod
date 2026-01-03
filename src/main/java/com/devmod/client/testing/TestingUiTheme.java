package com.devmod.client.testing;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Centralized color tokens for QA/testing client UI.
 */
public final class TestingUiTheme {
    private TestingUiTheme() {}

    public static final class Screen {
        public static final int BG = DesignTokens.TestingUi.Screen.BG;

        private Screen() {}
    }

    public static final class Accent {
        public static final int GOLD = DesignTokens.TestingUi.Accent.GOLD;

        private Accent() {}
    }

    public static final class Panel {
        public static final int BG = DesignTokens.TestingUi.Panel.BG;
        public static final int HEADER = DesignTokens.TestingUi.Panel.HEADER;
        public static final int HEADER_ALT = DesignTokens.TestingUi.Panel.HEADER_ALT;
        public static final int XP_BG = DesignTokens.TestingUi.Panel.XP_BG;

        private Panel() {}
    }

    public static final class Scrollbar {
        public static final int TRACK = DesignTokens.TestingUi.Scrollbar.TRACK;
        public static final int THUMB = DesignTokens.TestingUi.Scrollbar.THUMB;

        private Scrollbar() {}
    }

    public static final class Hud {
        public static final int PANEL_BG = DesignTokens.TestingUi.Hud.PANEL_BG;
        public static final int PANEL_BORDER = DesignTokens.TestingUi.Hud.PANEL_BORDER;
        public static final int PANEL_HEADER_BG = DesignTokens.TestingUi.Hud.PANEL_HEADER_BG;

        public static final int TEXT_TITLE = DesignTokens.TestingUi.Hud.TEXT_TITLE;
        public static final int TEXT_PRIMARY = DesignTokens.TestingUi.Hud.TEXT_PRIMARY;
        public static final int TEXT_SECONDARY = DesignTokens.TestingUi.Hud.TEXT_SECONDARY;
        public static final int TEXT_MUTED = DesignTokens.TestingUi.Hud.TEXT_MUTED;
        public static final int TEXT_SUCCESS = DesignTokens.TestingUi.Hud.TEXT_SUCCESS;
        public static final int TEXT_WARNING = DesignTokens.TestingUi.Hud.TEXT_WARNING;

        public static final int PROGRESS_BG = DesignTokens.TestingUi.Hud.PROGRESS_BG;
        public static final int PROGRESS_FILL = DesignTokens.TestingUi.Hud.PROGRESS_FILL;
        public static final int PROGRESS_COMPLETE = DesignTokens.TestingUi.Hud.PROGRESS_COMPLETE;

        public static final int HINT_BG = DesignTokens.TestingUi.Hud.HINT_BG;
        public static final int NEW_TEST_GLOW_RGB = DesignTokens.TestingUi.Hud.NEW_TEST_GLOW_RGB;
        public static final int HEADER_IN_PROGRESS = DesignTokens.TestingUi.Hud.HEADER_IN_PROGRESS;

        private Hud() {}
    }

    public static final class Notification {
        public static final int BG_RGB = DesignTokens.TestingUi.Notification.BG_RGB;
        public static final int TEXT_RGB = DesignTokens.TestingUi.Notification.TEXT_RGB;
        public static final int TEXT_MUTED_RGB = DesignTokens.TestingUi.Notification.TEXT_MUTED_RGB;

        public static final int TEST_PASSED = DesignTokens.TestingUi.Notification.TEST_PASSED;
        public static final int TEST_FAILED = DesignTokens.TestingUi.Notification.TEST_FAILED;
        public static final int ACHIEVEMENT = DesignTokens.TestingUi.Notification.ACHIEVEMENT;
        public static final int LEVEL_UP = DesignTokens.TestingUi.Notification.LEVEL_UP;
        public static final int BADGE = DesignTokens.TestingUi.Notification.BADGE;
        public static final int STREAK = DesignTokens.TestingUi.Notification.STREAK;
        public static final int XP_GAIN = DesignTokens.TestingUi.Notification.XP_GAIN;

        private Notification() {}
    }

    public static final class Badge {
        public static final int UNREAD = DesignTokens.TestingUi.Badge.UNREAD;

        private Badge() {}
    }
}
