package com.devmod.mailbox.client;

import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Centralized color tokens for mailbox-related screens.
 */
public final class MailboxUiTheme {
    private MailboxUiTheme() {}

    public static final class Panel {
        public static final int BG = DesignTokens.Mailbox.Panel.BG;

        private Panel() {}
    }

    public static final class Divider {
        public static final int LINE = DesignTokens.Mailbox.Divider.LINE;

        private Divider() {}
    }

    public static final class List {
        public static final int SELECTED_BG = DesignTokens.Mailbox.List.SELECTED_BG;
        public static final int HOVER_BG = DesignTokens.Mailbox.List.HOVER_BG;

        private List() {}
    }

    public static final class Scrollbar {
        public static final int TRACK = DesignTokens.Mailbox.Scrollbar.TRACK;
        public static final int THUMB = DesignTokens.Mailbox.Scrollbar.THUMB;

        private Scrollbar() {}
    }

    public static final class News {
        public static final int PATCH_NOTES = DesignTokens.Mailbox.News.PATCH_NOTES;
        public static final int EVENTS = DesignTokens.Mailbox.News.EVENTS;
        public static final int ANNOUNCEMENTS = DesignTokens.Mailbox.News.ANNOUNCEMENTS;
        public static final int MAINTENANCE = DesignTokens.Mailbox.News.MAINTENANCE;
        public static final int DEV_BLOG = DesignTokens.Mailbox.News.DEV_BLOG;
        public static final int COMMUNITY = DesignTokens.Mailbox.News.COMMUNITY;

        private static final int[] CATEGORY_COLORS = {
            PATCH_NOTES,
            EVENTS,
            ANNOUNCEMENTS,
            MAINTENANCE,
            DEV_BLOG,
            COMMUNITY
        };

        public static int[] categoryColors() {
            return CATEGORY_COLORS.clone();
        }

        private News() {}
    }

    public static final class TesterTasks {
        public static final int PANEL_BG = DesignTokens.Mailbox.TesterTasks.PANEL_BG;
        public static final int PANEL_OUTLINE = DesignTokens.Mailbox.TesterTasks.PANEL_OUTLINE;
        public static final int LIST_BG = DesignTokens.Mailbox.TesterTasks.LIST_BG;
        public static final int SCROLLBAR = DesignTokens.Mailbox.TesterTasks.SCROLLBAR;

        public static final int ENTRY_DEFAULT = DesignTokens.Mailbox.TesterTasks.ENTRY_DEFAULT;
        public static final int ENTRY_HOVER = DesignTokens.Mailbox.TesterTasks.ENTRY_HOVER;
        public static final int ENTRY_SELECTED = DesignTokens.Mailbox.TesterTasks.ENTRY_SELECTED;

        public static final int TEXT_PRIMARY = DesignTokens.Mailbox.TesterTasks.TEXT_PRIMARY;
        public static final int TEXT_MUTED = DesignTokens.Mailbox.TesterTasks.TEXT_MUTED;
        public static final int TEXT_DIM = DesignTokens.Mailbox.TesterTasks.TEXT_DIM;

        public static final int DUE_OVERDUE = DesignTokens.Mailbox.TesterTasks.DUE_OVERDUE;
        public static final int DUE_SOON = DesignTokens.Mailbox.TesterTasks.DUE_SOON;

        public static final int PRIORITY_HIGH = DesignTokens.Mailbox.TesterTasks.PRIORITY_HIGH;
        public static final int PRIORITY_MEDIUM = DesignTokens.Mailbox.TesterTasks.PRIORITY_MEDIUM;
        public static final int PRIORITY_LOW = DesignTokens.Mailbox.TesterTasks.PRIORITY_LOW;

        public static final int STATUS_PENDING = DesignTokens.Mailbox.TesterTasks.STATUS_PENDING;
        public static final int STATUS_IN_PROGRESS = DesignTokens.Mailbox.TesterTasks.STATUS_IN_PROGRESS;
        public static final int STATUS_COMPLETED = DesignTokens.Mailbox.TesterTasks.STATUS_COMPLETED;

        private TesterTasks() {}
    }
}
