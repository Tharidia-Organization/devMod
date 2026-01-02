package com.devmod.client.notification.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.actions.ActionOrigin;
import com.devmod.client.input.KeyInputHandler;
import com.devmod.client.notification.ClientNotificationManager;
import com.devmod.client.notification.NotificationActionResolver;
import com.devmod.client.notification.NotificationUiTheme;
import com.devmod.mailbox.MessageType;
import com.devmod.mailbox.client.ClientMailboxAccess;
import com.devmod.mailbox.client.ClientMailboxCache;
import com.devmod.mailbox.client.ClientNewsCache;
import com.devmod.mailbox.client.ClientTaskCache;
import com.devmod.mailbox.client.ClientTicketCache;
import com.devmod.mailbox.client.ClientTicketCache.TicketData;
import com.devmod.mailbox.client.screen.MailboxScreen;
import com.devmod.mailbox.client.screen.NewsScreen;
import com.devmod.mailbox.client.screen.TesterTaskScreen;
import com.devmod.mailbox.client.screen.TicketCommentScreen;
import com.devmod.mailbox.client.screen.TicketCreateScreen;
import com.devmod.mailbox.network.payload.MailboxActionPayload;
import com.devmod.mailbox.network.payload.MailboxSyncPayload.MailboxMessageData;
import com.devmod.mailbox.network.payload.NewsReadPayload;
import com.devmod.mailbox.network.payload.NewsSyncPayload.NewsArticleData;
import com.devmod.mailbox.network.payload.TaskActionPayload;
import com.devmod.mailbox.network.payload.TicketActionPayload;
import com.devmod.mailbox.network.payload.TicketSyncRequestPayload;
import com.devmod.mailbox.news.NewsCategory;
import com.devmod.mailbox.task.TestTask;
import com.devmod.mailbox.ticket.TicketPriority;
import com.devmod.mailbox.ticket.TicketStatus;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;

@OnlyIn(Dist.CLIENT)
public class NotificationCenterScreen extends Screen {

    private static final int PANEL_MAX_WIDTH = 960;
    private static final int PANEL_MAX_HEIGHT = 620;
    private static final int PANEL_PADDING = 18;
    private static final int HEADER_HEIGHT = 68;
    private static final int NAV_WIDTH = 170;
    private static final int NAV_ITEM_HEIGHT = 28;
    private static final int NAV_ITEM_GAP = 6;
    private static final int CONTENT_GAP = 14;
    private static final int CHIP_HEIGHT = 22;
    private static final int CHIP_GAP = 6;
    private static final int LIST_GAP = 8;
    private static final int ACTION_HEIGHT = 20;
    private static final int DETAIL_ACTION_HEIGHT = 20;
    private static final int DETAIL_ACTION_GAP = 6;
    private static final int MIN_SPLIT_WIDTH = 560;

    private static final int NOTIFICATION_ROW_HEIGHT = 68;
    private static final int MAILBOX_ROW_HEIGHT = 52;
    private static final int NEWS_ROW_HEIGHT = 54;
    private static final int TICKET_ROW_HEIGHT = 54;
    private static final int TASK_ROW_HEIGHT = 54;

    /**
     * Helper to get translated string - satisfies Eclipse null checker.
     */
    @Nonnull
    private static String tr(@Nullable String key) {
        if (key == null) return "";
        String result = Component.translatable(key).getString();
        if (result == null) return "";
        return result;
    }

    /**
     * Helper to get translated string with args - satisfies Eclipse null checker.
     */
    @Nonnull
    private static String tr(@Nullable String key, @Nullable Object... args) {
        if (key == null) return "";
        if (args == null) {
            String result = Component.translatable(key).getString();
            if (result == null) return "";
            return result;
        }
        String result = Component.translatable(key, args).getString();
        if (result == null) return "";
        return result;
    }

    /**
     * Helper to ensure non-null String - satisfies Eclipse null checker.
     */
    @Nonnull
    private static String nn(@Nullable String s) {
        return s != null ? s : "";
    }

    @Nullable
    private final Screen parent;
    private final Tab initialTab;
    @Nullable
    private UUID pendingEntityId;

    private final List<FilterChip> filterChips = new ArrayList<>();
    private final List<TabEntry> tabEntries = new ArrayList<>();
    private final List<ActionButton> actionButtons = new ArrayList<>();

    @Nullable
    private NotificationCategory activeFilter;
    private Tab activeTab = Tab.NOTIFICATIONS;

    private long openedAt;
    private int scrollOffset;
    private int maxScroll;
    private int detailScrollOffset;
    private int detailScrollMax;

    private Rect listRect = new Rect(0, 0, 0, 0);
    private Rect detailRect = new Rect(0, 0, 0, 0);
    private Rect backRect = new Rect(0, 0, 0, 0);

    @Nullable
    private UUID selectedNotificationId;
    @Nullable
    private UUID selectedMailboxId;
    @Nullable
    private UUID selectedNewsId;
    @Nullable
    private UUID selectedTicketId;
    @Nullable
    private UUID selectedTaskId;

    public NotificationCenterScreen(@Nullable Screen parent) {
        this(parent, Tab.NOTIFICATIONS, null);
    }

    private NotificationCenterScreen(@Nullable Screen parent, Tab initialTab, @Nullable UUID initialEntityId) {
        super(Component.translatable("devmod.notification.center.title"));
        this.parent = parent;
        this.initialTab = initialTab;
        this.pendingEntityId = initialEntityId;
    }

    public static void open(String tabId, @Nullable UUID entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        Tab tab = Tab.fromId(tabId);
        if (mc.screen instanceof NotificationCenterScreen center) {
            center.selectTab(tab, entityId);
            return;
        }
        mc.setScreen(new NotificationCenterScreen(mc.screen, tab, entityId));
    }

    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();
        scrollOffset = 0;
        maxScroll = 0;
        detailScrollOffset = 0;
        detailScrollMax = 0;
        activeTab = initialTab;
        selectTab(activeTab, pendingEntityId);
    }

    private void selectTab(Tab tab, @Nullable UUID entityId) {
        if (!isTabEnabled(tab)) {
            return;
        }
        activeTab = tab;
        pendingEntityId = entityId;
        scrollOffset = 0;
        maxScroll = 0;
        detailScrollOffset = 0;
        detailScrollMax = 0;
        ensureSelectionForActiveTab();
        maybeRefreshTabData();
    }

    private void maybeRefreshTabData() {
        if (activeTab == Tab.MAILBOX && ClientMailboxCache.isStale()) {
            PacketDistributor.sendToServer(Objects.requireNonNull(MailboxActionPayload.refresh()));
        }
        if (activeTab == Tab.TICKETS && ClientTicketCache.needsRefresh()) {
            PacketDistributor.sendToServer(new TicketSyncRequestPayload());
        }
    }

    private void ensureSelectionForActiveTab() {
        switch (activeTab) {
            case NOTIFICATIONS -> ensureNotificationSelection();
            case MAILBOX -> ensureMailboxSelection();
            case NEWS -> ensureNewsSelection();
            case TICKETS -> ensureTicketSelection();
            case TASKS -> ensureTaskSelection();
        }
    }

    private void ensureNotificationSelection() {
        List<Notification> notifications = getFilteredNotifications();
        if (pendingEntityId != null) {
            for (Notification notification : notifications) {
                if (notification.id().equals(pendingEntityId)) {
                    selectedNotificationId = notification.id();
                    pendingEntityId = null;
                    return;
                }
            }
            pendingEntityId = null;
        }
        if (selectedNotificationId != null) {
            for (Notification notification : notifications) {
                if (notification.id().equals(selectedNotificationId)) {
                    return;
                }
            }
        }
        if (notifications.isEmpty()) {
            selectedNotificationId = null;
            return;
        }
        for (Notification notification : notifications) {
            if (!ClientNotificationManager.INSTANCE.isRead(notification.id())) {
                selectedNotificationId = notification.id();
                return;
            }
        }
        selectedNotificationId = notifications.get(0).id();
    }

    private void ensureMailboxSelection() {
        List<MailboxMessageData> messages = ClientMailboxCache.getMessages();
        if (pendingEntityId != null) {
            for (MailboxMessageData message : messages) {
                if (message.id().equals(pendingEntityId)) {
                    selectedMailboxId = message.id();
                    pendingEntityId = null;
                    return;
                }
            }
            pendingEntityId = null;
        }
        if (selectedMailboxId != null) {
            for (MailboxMessageData message : messages) {
                if (message.id().equals(selectedMailboxId)) {
                    return;
                }
            }
        }
        if (messages.isEmpty()) {
            selectedMailboxId = null;
            return;
        }
        for (MailboxMessageData message : messages) {
            if (!message.isRead()) {
                selectedMailboxId = message.id();
                return;
            }
        }
        selectedMailboxId = messages.get(0).id();
    }

    private void ensureNewsSelection() {
        List<NewsArticleData> articles = ClientNewsCache.getArticles();
        if (pendingEntityId != null) {
            for (NewsArticleData article : articles) {
                if (article.id().equals(pendingEntityId)) {
                    selectedNewsId = article.id();
                    pendingEntityId = null;
                    return;
                }
            }
            pendingEntityId = null;
        }
        if (selectedNewsId != null) {
            for (NewsArticleData article : articles) {
                if (article.id().equals(selectedNewsId)) {
                    return;
                }
            }
        }
        if (articles.isEmpty()) {
            selectedNewsId = null;
            return;
        }
        for (NewsArticleData article : articles) {
            if (!article.isRead()) {
                selectedNewsId = article.id();
                return;
            }
        }
        selectedNewsId = articles.get(0).id();
    }

    private void ensureTicketSelection() {
        List<TicketData> tickets = ClientTicketCache.getTickets();
        if (pendingEntityId != null) {
            for (TicketData ticket : tickets) {
                if (ticket.id().equals(pendingEntityId)) {
                    selectedTicketId = ticket.id();
                    pendingEntityId = null;
                    return;
                }
            }
            pendingEntityId = null;
        }
        if (selectedTicketId != null) {
            for (TicketData ticket : tickets) {
                if (ticket.id().equals(selectedTicketId)) {
                    return;
                }
            }
        }
        selectedTicketId = tickets.isEmpty() ? null : tickets.get(0).id();
    }

    private void ensureTaskSelection() {
        if (!ClientMailboxAccess.isTester()) {
            selectedTaskId = null;
            return;
        }
        List<TestTask> tasks = ClientTaskCache.getTasks();
        if (pendingEntityId != null) {
            for (TestTask task : tasks) {
                if (task.id().equals(pendingEntityId)) {
                    selectedTaskId = task.id();
                    pendingEntityId = null;
                    return;
                }
            }
            pendingEntityId = null;
        }
        if (selectedTaskId != null) {
            for (TestTask task : tasks) {
                if (task.id().equals(selectedTaskId)) {
                    return;
                }
            }
        }
        if (tasks.isEmpty()) {
            selectedTaskId = null;
            return;
        }
        for (TestTask task : tasks) {
            if (task.status() == TestTask.TaskStatus.PENDING
                    || task.status() == TestTask.TaskStatus.IN_PROGRESS) {
                selectedTaskId = task.id();
                return;
            }
        }
        selectedTaskId = tasks.get(0).id();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        long now = System.currentTimeMillis();
        float openProgress = easeOutCubic(Math.min(1f, (now - openedAt) / 280f));

        int panelWidth = Math.min(PANEL_MAX_WIDTH, width - 32);
        int panelHeight = Math.min(PANEL_MAX_HEIGHT, height - 32);
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        int panelAlpha = (int) (235 * openProgress);

        graphics.pose().pushPose();
        graphics.pose().translate(0, (1f - openProgress) * 12f, 0);

        int shadowAlpha = (int) (140 * openProgress);
        int shadowColor = NotificationUiTheme.withAlpha(0x000000, shadowAlpha);
        graphics.fill(panelX - 8, panelY - 6, panelX + panelWidth + 8, panelY + panelHeight + 8, shadowColor);

        renderPanel(graphics, panelX, panelY, panelWidth, panelHeight, panelAlpha, mouseX, mouseY, now);

        graphics.pose().popPose();

    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int top = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_BACKDROP_TOP, 0xFF);
        int bottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_BACKDROP_BOTTOM, 0xFF);
        graphics.fillGradient(0, 0, width, height, top, bottom);

        int stripeColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT_ALT, 0x12);
        for (int x = -height; x < width + height; x += 48) {
            graphics.fill(x, 0, x + 2, height, stripeColor);
        }

        int dotColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT, 0x14);
        for (int i = 0; i < width; i += 72) {
            int dotX = i + 12;
            int dotY = (i * 13) % Math.max(1, height - 6);
            graphics.fill(dotX, dotY, dotX + 2, dotY + 2, dotColor);
        }
    }

    private void renderPanel(GuiGraphics graphics, int panelX, int panelY, int panelWidth, int panelHeight,
                              int panelAlpha, int mouseX, int mouseY, long now) {
        int panelTop = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_TOP, panelAlpha);
        int panelBottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_BOTTOM, panelAlpha);
        graphics.fillGradient(panelX, panelY, panelX + panelWidth, panelY + panelHeight, panelTop, panelBottom);

        int borderAlpha = Math.min(0x66, panelAlpha);
        int borderColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT, borderAlpha);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, borderColor);
        graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, borderColor);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, borderColor);
        graphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, borderColor);

        renderHeader(graphics, panelX, panelY, panelWidth, mouseX, mouseY);

        int contentX = panelX + PANEL_PADDING;
        int contentY = panelY + HEADER_HEIGHT;
        int contentW = panelWidth - PANEL_PADDING * 2;
        int contentH = panelHeight - HEADER_HEIGHT - PANEL_PADDING;

        int navWidth = Math.min(NAV_WIDTH, Math.max(120, contentW / 4));
        Rect navRect = new Rect(contentX, contentY, navWidth, contentH);
        renderNav(graphics, navRect, mouseX, mouseY, panelAlpha);

        int mainX = navRect.x() + navRect.w() + CONTENT_GAP;
        int mainW = contentW - navRect.w() - CONTENT_GAP;
        renderContent(graphics, new Rect(mainX, contentY, mainW, contentH), panelAlpha, mouseX, mouseY, now);
    }

    private void renderHeader(GuiGraphics graphics, int panelX, int panelY, int panelWidth, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(this.font);
        String title = tr("devmod.notification.center.title");
        int titleX = panelX + PANEL_PADDING;
        int titleY = panelY + 16;

        graphics.pose().pushPose();
        graphics.pose().translate(titleX, titleY, 0);
        graphics.pose().scale(1.3f, 1.3f, 1.0f);
        graphics.drawString(font, title, 0, 0,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, 0xFF), true);
        graphics.pose().popPose();

        String subtitle = getActiveSubtitle();
        graphics.drawString(font, subtitle, titleX, panelY + 38,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, 0xFF), false);

        String tabLabel = tr(activeTab.labelKey);
        renderTabChip(graphics, font, titleX + font.width(title) + 12, panelY + 18, tabLabel, getTabColor(activeTab));

        actionButtons.clear();

        int actionX = panelX + panelWidth - PANEL_PADDING;
        String backLabel = tr("gui.back");
        int backW = font.width(backLabel) + 14;
        backRect = new Rect(actionX - backW, panelY + 18, backW, ACTION_HEIGHT);
        renderActionButton(graphics, backRect, backLabel, mouseX, mouseY, false, true);
        actionX = backRect.x() - 8;

        if (activeTab == Tab.NOTIFICATIONS) {
            actionX = renderHeaderAction(graphics, font, actionX, panelY + 18,
                "devmod.notification.center.settings", false, true, mouseX, mouseY,
                () -> Minecraft.getInstance().setScreen(new NotificationSettingsScreen(this)));
            actionX = renderHeaderAction(graphics, font, actionX, panelY + 18,
                "devmod.notification.center.mark_all_read", true, true, mouseX, mouseY,
                ClientNotificationManager.INSTANCE::markAllRead);
        } else if (activeTab == Tab.MAILBOX) {
            actionX = renderHeaderAction(graphics, font, actionX, panelY + 18,
                "devmod.notification.center.action.open_mailbox", false, true, mouseX, mouseY,
                MailboxScreen::open);
            actionX = renderHeaderAction(graphics, font, actionX, panelY + 18,
                "devmod.notification.center.action.refresh_mailbox", true, true, mouseX, mouseY,
                () -> PacketDistributor.sendToServer(Objects.requireNonNull(MailboxActionPayload.refresh())));
        } else if (activeTab == Tab.NEWS) {
            actionX = renderHeaderAction(graphics, font, actionX, panelY + 18,
                "devmod.notification.center.action.open_news", false, true, mouseX, mouseY,
                NewsScreen::open);
        } else if (activeTab == Tab.TICKETS) {
            actionX = renderHeaderAction(graphics, font, actionX, panelY + 18,
                "devmod.notification.center.action.new_ticket", true, true, mouseX, mouseY,
                () -> Minecraft.getInstance().setScreen(new TicketCreateScreen(this)));
            actionX = renderHeaderAction(graphics, font, actionX, panelY + 18,
                "devmod.notification.center.action.refresh_tickets", false, true, mouseX, mouseY,
                () -> PacketDistributor.sendToServer(new TicketSyncRequestPayload()));
        } else if (activeTab == Tab.TASKS) {
            boolean enabled = ClientMailboxAccess.isTester();
            actionX = renderHeaderAction(graphics, font, actionX, panelY + 18,
                "devmod.notification.center.action.open_tasks", false, enabled, mouseX, mouseY,
                TesterTaskScreen::open);
        }
    }

    private int renderHeaderAction(GuiGraphics graphics, Font font, int rightX, int y, String labelKey,
                                   boolean accent, boolean enabled, int mouseX, int mouseY, Runnable handler) {
        String label = tr(labelKey);
        int width = font.width(label) + 14;
        Rect rect = new Rect(rightX - width, y, width, ACTION_HEIGHT);
        renderActionButton(graphics, rect, label, mouseX, mouseY, accent, enabled);
        actionButtons.add(new ActionButton(rect, handler, enabled));
        return rect.x() - 8;
    }

    private void renderTabChip(GuiGraphics graphics, Font font, int x, int y, @Nonnull String label, int accent) {
        int chipW = font.width(label) + 14;
        int chipH = 18;
        Rect rect = new Rect(x, y, chipW, chipH);
        int top = NotificationUiTheme.withAlpha(NotificationUiTheme.mix(accent, 0x000000, 0.4f), 0xAA);
        int bottom = NotificationUiTheme.withAlpha(NotificationUiTheme.mix(accent, 0x000000, 0.55f), 0xAA);
        renderRoundedRect(graphics, rect.x(), rect.y(), rect.w(), rect.h(), 6, top, bottom);
        graphics.drawString(font, label, rect.x() + 7, rect.y() + 5,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, 0xFF), false);
    }

    private void renderNav(GuiGraphics graphics, Rect rect, int mouseX, int mouseY, int panelAlpha) {
        tabEntries.clear();
        int top = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_INNER_TOP, Math.min(panelAlpha, 0xE0));
        int bottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_INNER_BOTTOM, Math.min(panelAlpha, 0xE0));
        renderRoundedRect(graphics, rect.x(), rect.y(), rect.w(), rect.h(), 8, top, bottom);

        int headerColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT, 0x44);
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + 2, headerColor);

        int y = rect.y() + 10;
        for (Tab tab : Tab.values()) {
            int itemHeight = NAV_ITEM_HEIGHT;
            Rect itemRect = new Rect(rect.x() + 8, y, rect.w() - 16, itemHeight);
            boolean enabled = isTabEnabled(tab);
            boolean active = tab == activeTab;
            boolean hovered = itemRect.contains(mouseX, mouseY);
            renderNavItem(graphics, itemRect, tab, active, hovered, enabled, panelAlpha);
            tabEntries.add(new TabEntry(tab, itemRect, enabled));
            y += itemHeight + NAV_ITEM_GAP;
        }
    }

    private void renderNavItem(GuiGraphics graphics, Rect rect, Tab tab, boolean active, boolean hovered,
                               boolean enabled, int panelAlpha) {
        Font font = Objects.requireNonNull(this.font);
        int accent = getTabColor(tab);
        int baseTop = active ? NotificationUiTheme.RGB_SURFACE_HOVER_TOP : NotificationUiTheme.RGB_SURFACE_TOP;
        int baseBottom = active ? NotificationUiTheme.RGB_SURFACE_HOVER_BOTTOM : NotificationUiTheme.RGB_SURFACE_BOTTOM;
        if (hovered) {
            baseTop = NotificationUiTheme.mix(baseTop, 0xFFFFFF, 0.05f);
            baseBottom = NotificationUiTheme.mix(baseBottom, 0xFFFFFF, 0.04f);
        }
        int top = NotificationUiTheme.withAlpha(baseTop, Math.min(panelAlpha, 0xE0));
        int bottom = NotificationUiTheme.withAlpha(baseBottom, Math.min(panelAlpha, 0xE0));
        renderRoundedRect(graphics, rect.x(), rect.y(), rect.w(), rect.h(), 6, top, bottom);

        int accentAlpha = active ? 0xCC : 0x66;
        graphics.fill(rect.x(), rect.y() + rect.h() - 2, rect.x() + rect.w(), rect.y() + rect.h(),
                NotificationUiTheme.withAlpha(accent, accentAlpha));

        String label = tr(tab.labelKey);
        int labelColor = enabled ? NotificationUiTheme.RGB_TEXT_PRIMARY : NotificationUiTheme.RGB_TEXT_MUTED;
        graphics.drawString(font, label, rect.x() + 8, rect.y() + 8,
                NotificationUiTheme.withAlpha(labelColor, 0xFF), false);

        int count = getTabBadgeCount(tab);
        String countLabel = nn(String.valueOf(count));
        if (count > 0) {
            int countW = font.width(countLabel);
            graphics.drawString(font, countLabel, rect.x() + rect.w() - countW - 8, rect.y() + 8,
                    NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, 0xFF), false);
        }

        if (!enabled && tab == Tab.TASKS) {
            String locked = tr("devmod.notification.center.tab.locked");
            int lockW = font.width(locked);
            graphics.drawString(font, locked, rect.x() + rect.w() - lockW - 8, rect.y() + 8,
                    NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, 0xCC), false);
        }
    }

    private void renderContent(GuiGraphics graphics, Rect rect, int panelAlpha, int mouseX, int mouseY, long now) {
        boolean split = rect.w() >= MIN_SPLIT_WIDTH;
        int listWidth = split ? (int) (rect.w() * 0.54f) : rect.w();
        int detailWidth = split ? rect.w() - listWidth - CONTENT_GAP : 0;

        Rect listPanel = new Rect(rect.x(), rect.y(), listWidth, rect.h());
        Rect detailPanel = detailWidth > 0
            ? new Rect(rect.x() + listWidth + CONTENT_GAP, rect.y(), detailWidth, rect.h())
            : new Rect(0, 0, 0, 0);

        detailRect = detailPanel;
        detailScrollMax = 0;

        switch (activeTab) {
            case NOTIFICATIONS -> renderNotificationsTab(graphics, listPanel, detailPanel, panelAlpha, mouseX, mouseY, now);
            case MAILBOX -> renderMailboxTab(graphics, listPanel, detailPanel, panelAlpha, mouseX, mouseY);
            case NEWS -> renderNewsTab(graphics, listPanel, detailPanel, panelAlpha, mouseX, mouseY);
            case TICKETS -> renderTicketsTab(graphics, listPanel, detailPanel, panelAlpha, mouseX, mouseY);
            case TASKS -> renderTasksTab(graphics, listPanel, detailPanel, panelAlpha, mouseX, mouseY);
        }
    }

    private void renderNotificationsTab(GuiGraphics graphics, Rect listPanel, Rect detailPanel,
                                        int panelAlpha, int mouseX, int mouseY, long now) {
        renderInsetPanel(graphics, listPanel, panelAlpha);

        int filterX = listPanel.x() + 8;
        int filterY = listPanel.y() + 8;
        int filterWidth = listPanel.w() - 16;
        int filterEndY = renderFilters(graphics, filterX, filterY, filterWidth, mouseX, mouseY);

        int listTop = filterEndY + 10;
        int listHeight = listPanel.y() + listPanel.h() - listTop - 8;
        listRect = new Rect(listPanel.x() + 8, listTop, listPanel.w() - 16, Math.max(0, listHeight));

        List<Notification> filtered = getFilteredNotifications();
        int contentHeight = getListContentHeight(NOTIFICATION_ROW_HEIGHT, filtered.size());
        maxScroll = Math.max(0, contentHeight - listRect.h());
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        if (filtered.isEmpty()) {
            renderEmptyState(graphics, listRect, "devmod.notification.center.empty.title",
                "devmod.notification.center.empty.subtitle", panelAlpha);
        } else {
            boolean scissor = listRect.w() > 0 && listRect.h() > 0;
            if (scissor) {
                graphics.enableScissor(listRect.x(), listRect.y(), listRect.x() + listRect.w(), listRect.y() + listRect.h());
            }
            try {
                int y = listRect.y() - scrollOffset;
                for (int i = 0; i < filtered.size(); i++) {
                    Notification notification = filtered.get(i);
                    int rowY = y + i * (NOTIFICATION_ROW_HEIGHT + LIST_GAP);
                    if (rowY + NOTIFICATION_ROW_HEIGHT < listRect.y() || rowY > listRect.y() + listRect.h()) {
                        continue;
                    }
                    Rect rowRect = new Rect(listRect.x(), rowY, listRect.w(), NOTIFICATION_ROW_HEIGHT);
                    boolean hovered = rowRect.contains(mouseX, mouseY);
                    boolean selected = notification.id().equals(selectedNotificationId);
                    renderNotificationRow(graphics, rowRect, notification, selected, hovered, panelAlpha, now);
                }
            } finally {
                if (scissor) {
                    graphics.disableScissor();
                }
            }
        }

        if (detailPanel.w() > 0) {
            renderInsetPanel(graphics, detailPanel, panelAlpha);
            renderNotificationDetail(graphics, detailPanel, panelAlpha, mouseX, mouseY);
        }
    }

    private void renderMailboxTab(GuiGraphics graphics, Rect listPanel, Rect detailPanel,
                                  int panelAlpha, int mouseX, int mouseY) {
        renderInsetPanel(graphics, listPanel, panelAlpha);
        listRect = new Rect(listPanel.x() + 8, listPanel.y() + 8, listPanel.w() - 16, listPanel.h() - 16);

        List<MailboxMessageData> messages = ClientMailboxCache.getMessages();
        int contentHeight = getListContentHeight(MAILBOX_ROW_HEIGHT, messages.size());
        maxScroll = Math.max(0, contentHeight - listRect.h());
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        if (messages.isEmpty()) {
            renderEmptyState(graphics, listRect, "devmod.notification.center.empty.mailbox.title",
                "devmod.notification.center.empty.mailbox.subtitle", panelAlpha);
        } else {
            boolean scissor = listRect.w() > 0 && listRect.h() > 0;
            if (scissor) {
                graphics.enableScissor(listRect.x(), listRect.y(), listRect.x() + listRect.w(), listRect.y() + listRect.h());
            }
            try {
                int y = listRect.y() - scrollOffset;
                for (MailboxMessageData message : messages) {
                    int rowY = y;
                    if (rowY + MAILBOX_ROW_HEIGHT >= listRect.y() && rowY <= listRect.y() + listRect.h()) {
                        Rect rowRect = new Rect(listRect.x(), rowY, listRect.w(), MAILBOX_ROW_HEIGHT);
                        boolean hovered = rowRect.contains(mouseX, mouseY);
                        boolean selected = message.id().equals(selectedMailboxId);
                        renderMailboxRow(graphics, rowRect, message, selected, hovered, panelAlpha);
                    }
                    y += MAILBOX_ROW_HEIGHT + LIST_GAP;
                }
            } finally {
                if (scissor) {
                    graphics.disableScissor();
                }
            }
        }

        if (detailPanel.w() > 0) {
            renderInsetPanel(graphics, detailPanel, panelAlpha);
            renderMailboxDetail(graphics, detailPanel, panelAlpha, mouseX, mouseY);
        }
    }

    private void renderNewsTab(GuiGraphics graphics, Rect listPanel, Rect detailPanel,
                               int panelAlpha, int mouseX, int mouseY) {
        renderInsetPanel(graphics, listPanel, panelAlpha);
        listRect = new Rect(listPanel.x() + 8, listPanel.y() + 8, listPanel.w() - 16, listPanel.h() - 16);

        List<NewsArticleData> articles = ClientNewsCache.getArticles();
        int contentHeight = getListContentHeight(NEWS_ROW_HEIGHT, articles.size());
        maxScroll = Math.max(0, contentHeight - listRect.h());
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        if (articles.isEmpty()) {
            renderEmptyState(graphics, listRect, "devmod.notification.center.empty.news.title",
                "devmod.notification.center.empty.news.subtitle", panelAlpha);
        } else {
            boolean scissor = listRect.w() > 0 && listRect.h() > 0;
            if (scissor) {
                graphics.enableScissor(listRect.x(), listRect.y(), listRect.x() + listRect.w(), listRect.y() + listRect.h());
            }
            try {
                int y = listRect.y() - scrollOffset;
                for (NewsArticleData article : articles) {
                    int rowY = y;
                    if (rowY + NEWS_ROW_HEIGHT >= listRect.y() && rowY <= listRect.y() + listRect.h()) {
                        Rect rowRect = new Rect(listRect.x(), rowY, listRect.w(), NEWS_ROW_HEIGHT);
                        boolean hovered = rowRect.contains(mouseX, mouseY);
                        boolean selected = article.id().equals(selectedNewsId);
                        renderNewsRow(graphics, rowRect, article, selected, hovered, panelAlpha);
                    }
                    y += NEWS_ROW_HEIGHT + LIST_GAP;
                }
            } finally {
                if (scissor) {
                    graphics.disableScissor();
                }
            }
        }

        if (detailPanel.w() > 0) {
            renderInsetPanel(graphics, detailPanel, panelAlpha);
            renderNewsDetail(graphics, detailPanel, panelAlpha, mouseX, mouseY);
        }
    }

    private void renderTicketsTab(GuiGraphics graphics, Rect listPanel, Rect detailPanel,
                                  int panelAlpha, int mouseX, int mouseY) {
        renderInsetPanel(graphics, listPanel, panelAlpha);
        listRect = new Rect(listPanel.x() + 8, listPanel.y() + 8, listPanel.w() - 16, listPanel.h() - 16);

        List<TicketData> tickets = ClientTicketCache.getTickets();
        int contentHeight = getListContentHeight(TICKET_ROW_HEIGHT, tickets.size());
        maxScroll = Math.max(0, contentHeight - listRect.h());
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        if (tickets.isEmpty()) {
            renderEmptyState(graphics, listRect, "devmod.notification.center.empty.tickets.title",
                "devmod.notification.center.empty.tickets.subtitle", panelAlpha);
        } else {
            boolean scissor = listRect.w() > 0 && listRect.h() > 0;
            if (scissor) {
                graphics.enableScissor(listRect.x(), listRect.y(), listRect.x() + listRect.w(), listRect.y() + listRect.h());
            }
            try {
                int y = listRect.y() - scrollOffset;
                for (TicketData ticket : tickets) {
                    int rowY = y;
                    if (rowY + TICKET_ROW_HEIGHT >= listRect.y() && rowY <= listRect.y() + listRect.h()) {
                        Rect rowRect = new Rect(listRect.x(), rowY, listRect.w(), TICKET_ROW_HEIGHT);
                        boolean hovered = rowRect.contains(mouseX, mouseY);
                        boolean selected = ticket.id().equals(selectedTicketId);
                        renderTicketRow(graphics, rowRect, ticket, selected, hovered, panelAlpha);
                    }
                    y += TICKET_ROW_HEIGHT + LIST_GAP;
                }
            } finally {
                if (scissor) {
                    graphics.disableScissor();
                }
            }
        }

        if (detailPanel.w() > 0) {
            renderInsetPanel(graphics, detailPanel, panelAlpha);
            renderTicketDetail(graphics, detailPanel, panelAlpha, mouseX, mouseY);
        }
    }

    private void renderTasksTab(GuiGraphics graphics, Rect listPanel, Rect detailPanel,
                                int panelAlpha, int mouseX, int mouseY) {
        renderInsetPanel(graphics, listPanel, panelAlpha);
        listRect = new Rect(listPanel.x() + 8, listPanel.y() + 8, listPanel.w() - 16, listPanel.h() - 16);

        if (!ClientMailboxAccess.isTester()) {
            maxScroll = 0;
            renderEmptyState(graphics, listRect, "devmod.notification.center.locked.tasks.title",
                "devmod.notification.center.locked.tasks.subtitle", panelAlpha);
            return;
        }

        List<TestTask> tasks = ClientTaskCache.getTasks();
        int contentHeight = getListContentHeight(TASK_ROW_HEIGHT, tasks.size());
        maxScroll = Math.max(0, contentHeight - listRect.h());
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        if (tasks.isEmpty()) {
            renderEmptyState(graphics, listRect, "devmod.notification.center.empty.tasks.title",
                "devmod.notification.center.empty.tasks.subtitle", panelAlpha);
        } else {
            boolean scissor = listRect.w() > 0 && listRect.h() > 0;
            if (scissor) {
                graphics.enableScissor(listRect.x(), listRect.y(), listRect.x() + listRect.w(), listRect.y() + listRect.h());
            }
            try {
                int y = listRect.y() - scrollOffset;
                for (TestTask task : tasks) {
                    int rowY = y;
                    if (rowY + TASK_ROW_HEIGHT >= listRect.y() && rowY <= listRect.y() + listRect.h()) {
                        Rect rowRect = new Rect(listRect.x(), rowY, listRect.w(), TASK_ROW_HEIGHT);
                        boolean hovered = rowRect.contains(mouseX, mouseY);
                        boolean selected = task.id().equals(selectedTaskId);
                        renderTaskRow(graphics, rowRect, task, selected, hovered, panelAlpha);
                    }
                    y += TASK_ROW_HEIGHT + LIST_GAP;
                }
            } finally {
                if (scissor) {
                    graphics.disableScissor();
                }
            }
        }

        if (detailPanel.w() > 0) {
            renderInsetPanel(graphics, detailPanel, panelAlpha);
            renderTaskDetail(graphics, detailPanel, panelAlpha, mouseX, mouseY);
        }
    }

    private void renderNotificationRow(GuiGraphics graphics, Rect rect, Notification notification,
                                        boolean selected, boolean hovered, int panelAlpha, long now) {
        // now available for animation timing
        if (now < 0) throw new IllegalArgumentException("now must be non-negative");
        boolean read = ClientNotificationManager.INSTANCE.isRead(notification.id());
        int accent = NotificationUiTheme.getCategoryColor(notification.category());
        renderListRowBackground(graphics, rect, selected, hovered, panelAlpha, accent);

        if (!read) {
            graphics.fill(rect.x() + rect.w() - 6, rect.y() + 8, rect.x() + rect.w() - 4, rect.y() + 14,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT, Math.min(panelAlpha, 0xEE)));
        }

        Font font = Objects.requireNonNull(this.font);
        String title = resolveTitle(notification);
        String message = resolveMessage(notification);
        String timeLabel = nn(formatAge(notification.createdAt()));

        int iconX = rect.x() + 10;
        int iconY = rect.y() + 12;
        int iconSize = 18;
        graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize,
            NotificationUiTheme.withAlpha(NotificationUiTheme.mix(accent, 0x000000, 0.5f), 0xA0));
        graphics.drawString(font, NotificationUiTheme.getCategoryIcon(notification.category()),
            iconX + 5, iconY + 5, NotificationUiTheme.withAlpha(accent, 0xFF), false);

        int contentX = iconX + iconSize + 10;
        int contentWidth = rect.w() - contentX - 10;

        String titleText = trimText(font, nn(title), contentWidth - 40);
        graphics.drawString(font, titleText, contentX, rect.y() + 10,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, 0xFF), true);

        String messageText = trimText(font, nn(message), contentWidth);
        graphics.drawString(font, messageText, contentX, rect.y() + 28,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, 0xDD), false);

        int timeWidth = font.width(timeLabel);
        graphics.drawString(font, timeLabel, rect.x() + rect.w() - timeWidth - 8, rect.y() + 10,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, 0xCC), false);
    }

    private void renderMailboxRow(GuiGraphics graphics, Rect rect, MailboxMessageData message,
                                  boolean selected, boolean hovered, int panelAlpha) {
        int accent = NotificationUiTheme.getCategoryColor(NotificationCategory.MAILBOX);
        renderListRowBackground(graphics, rect, selected, hovered, panelAlpha, accent);

        Font font = Objects.requireNonNull(this.font);
        String subject = trimText(font, nn(message.subject()), rect.w() - 60);
        String sender = message.senderName();
        String meta = nn(formatAge(Instant.ofEpochMilli(message.createdAtMillis())));

        if (!message.isRead()) {
            graphics.fill(rect.x() + rect.w() - 6, rect.y() + 8, rect.x() + rect.w() - 4, rect.y() + 14,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT, Math.min(panelAlpha, 0xEE)));
        }

        graphics.drawString(font, subject, rect.x() + 10, rect.y() + 8,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, 0xFF), true);
        graphics.drawString(font, sender, rect.x() + 10, rect.y() + 24,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, 0xDD), false);

        int metaW = font.width(meta);
        graphics.drawString(font, meta, rect.x() + rect.w() - metaW - 8, rect.y() + 24,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, 0xCC), false);
    }

    private void renderNewsRow(GuiGraphics graphics, Rect rect, NewsArticleData article,
                               boolean selected, boolean hovered, int panelAlpha) {
        int accent = NotificationUiTheme.getCategoryColor(NotificationCategory.NEWS);
        renderListRowBackground(graphics, rect, selected, hovered, panelAlpha, accent);

        Font font = Objects.requireNonNull(this.font);
        String title = trimText(font, nn(article.title()), rect.w() - 60);
        String category = resolveNewsCategoryLabel(article.categoryOrdinal());
        String meta = nn(formatAge(Instant.ofEpochMilli(article.publishedAtMillis())));

        if (!article.isRead()) {
            graphics.fill(rect.x() + rect.w() - 6, rect.y() + 8, rect.x() + rect.w() - 4, rect.y() + 14,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT, Math.min(panelAlpha, 0xEE)));
        }

        graphics.drawString(font, title, rect.x() + 10, rect.y() + 8,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, 0xFF), true);
        graphics.drawString(font, category, rect.x() + 10, rect.y() + 26,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, 0xDD), false);

        int metaW = font.width(meta);
        graphics.drawString(font, meta, rect.x() + rect.w() - metaW - 8, rect.y() + 26,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, 0xCC), false);
    }

    private void renderTicketRow(GuiGraphics graphics, Rect rect, TicketData ticket,
                                 boolean selected, boolean hovered, int panelAlpha) {
        int accent = NotificationUiTheme.RGB_ACCENT_SOFT;
        renderListRowBackground(graphics, rect, selected, hovered, panelAlpha, accent);

        Font font = Objects.requireNonNull(this.font);
        String subject = trimText(font, nn(ticket.subject()), rect.w() - 60);
        String status = ticket.status().getDisplayName();
        String meta = nn(formatAge(ticket.createdAt()));

        graphics.drawString(font, subject, rect.x() + 10, rect.y() + 8,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, 0xFF), true);
        graphics.drawString(font, status, rect.x() + 10, rect.y() + 26,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, 0xDD), false);

        int metaW = font.width(meta);
        graphics.drawString(font, meta, rect.x() + rect.w() - metaW - 8, rect.y() + 26,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, 0xCC), false);
    }

    private void renderTaskRow(GuiGraphics graphics, Rect rect, TestTask task,
                               boolean selected, boolean hovered, int panelAlpha) {
        int accent = NotificationUiTheme.RGB_ACCENT_ALT;
        renderListRowBackground(graphics, rect, selected, hovered, panelAlpha, accent);

        Font font = Objects.requireNonNull(this.font);
        String title = trimText(font, nn(task.title()), rect.w() - 60);
        String status = task.status().name().replace('_', ' ').toLowerCase(Locale.ROOT);
        String meta = nn(formatAge(Instant.ofEpochMilli(task.createdAt())));

        graphics.drawString(font, title, rect.x() + 10, rect.y() + 8,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, 0xFF), true);
        graphics.drawString(font, status, rect.x() + 10, rect.y() + 26,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, 0xDD), false);

        int metaW = font.width(meta);
        graphics.drawString(font, meta, rect.x() + rect.w() - metaW - 8, rect.y() + 26,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, 0xCC), false);
    }

    private void renderNotificationDetail(GuiGraphics graphics, Rect rect, int panelAlpha,
                                          int mouseX, int mouseY) {
        Notification notification = getSelectedNotification();
        if (notification == null) {
            renderEmptyState(graphics, rect, "devmod.notification.center.detail.empty.title",
                "devmod.notification.center.detail.empty.subtitle", panelAlpha);
            return;
        }

        Font font = Objects.requireNonNull(this.font);
        String title = resolveTitle(notification);
        String message = resolveMessage(notification);
        String categoryLabel = tr(notification.category().getTranslationKey());
        String priorityLabel = notification.priority().name().toLowerCase(Locale.ROOT);
        String timeLabel = nn(formatAge(notification.createdAt()));

        List<DetailAction> actions = new ArrayList<>();
        if (notification.actionId() != null && !notification.actionId().isBlank()) {
            String label = tr("devmod.notification.center.action.open_notification");
            actions.add(new DetailAction(label, true, true, () -> invokeNotificationAction(notification)));
        }

        int contentX = rect.x() + 12;
        int contentWidth = rect.w() - 24;
        int cursorY = rect.y() + 12;

        graphics.drawString(font, trimText(font, nn(title), contentWidth), contentX, cursorY,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, 0xFF), true);
        cursorY += 16;

        graphics.drawString(font, categoryLabel + " • " + priorityLabel, contentX, cursorY,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, 0xCC), false);

        int timeW = font.width(timeLabel);
        graphics.drawString(font, timeLabel, rect.x() + rect.w() - timeW - 12, cursorY,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, 0xCC), false);
        cursorY += 16;

        List<String> lines = new ArrayList<>();
        lines.addAll(wrapText(font, message, contentWidth));
        List<String> params = buildParamLines(notification);
        if (!params.isEmpty()) {
            lines.add("");
            lines.addAll(params);
        }

        int contentTop = cursorY + 6;
        int actionAreaHeight = getDetailActionAreaHeight(actions, rect.w());
        int contentHeight = rect.h() - (contentTop - rect.y()) - actionAreaHeight;
        detailScrollMax = Math.max(0, lines.size() * 12 - contentHeight);
        detailScrollOffset = Mth.clamp(detailScrollOffset, 0, detailScrollMax);

        boolean scissor = contentWidth > 0 && contentHeight > 0;
        if (scissor) {
            graphics.enableScissor(contentX, contentTop, contentX + contentWidth, contentTop + contentHeight);
        }
        try {
            int lineY = contentTop - detailScrollOffset;
            for (String line : lines) {
                if (lineY + 10 >= contentTop && lineY <= contentTop + contentHeight) {
                    int color = line.isEmpty()
                        ? NotificationUiTheme.RGB_TEXT_MUTED
                        : NotificationUiTheme.RGB_TEXT_SECONDARY;
                    graphics.drawString(font, line, contentX, lineY,
                        NotificationUiTheme.withAlpha(color, 0xDD), false);
                }
                lineY += 12;
            }
        } finally {
            if (scissor) {
                graphics.disableScissor();
            }
        }

        renderDetailActions(graphics, rect, mouseX, mouseY, actions);
    }

    private void renderMailboxDetail(GuiGraphics graphics, Rect rect, int panelAlpha, int mouseX, int mouseY) {
        MailboxMessageData message = getSelectedMailboxMessage();
        if (message == null) {
            renderEmptyState(graphics, rect, "devmod.notification.center.detail.empty.title",
                "devmod.notification.center.detail.empty.subtitle", panelAlpha);
            return;
        }

        Font font = Objects.requireNonNull(this.font);
        List<DetailAction> actions = new ArrayList<>();
        if (message.hasAttachment()) {
            boolean canClaim = message.canClaimAttachment();
            String label = tr("devmod.mailbox.claim_attachment");
            actions.add(new DetailAction(label, true, canClaim, () -> claimMailboxMessage(message)));
        }
        boolean canDelete = !message.canClaimAttachment();
        String deleteLabel = tr("devmod.mailbox.delete");
        actions.add(new DetailAction(deleteLabel, false, canDelete, () -> deleteMailboxMessage(message)));
        if (!message.isRead()) {
            String readLabel = tr("devmod.mailbox.mark_read");
            actions.add(new DetailAction(readLabel, false, true, () -> markMailboxRead(message)));
        }

        int contentX = rect.x() + 12;
        int contentWidth = rect.w() - 24;
        int cursorY = rect.y() + 12;

        graphics.drawString(font, trimText(font, nn(message.subject()), contentWidth), contentX, cursorY,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, 0xFF), true);
        cursorY += 16;

        MessageType type = resolveMessageType(message.messageTypeOrdinal());
        String meta = message.senderName() + " • " + type.getId();
        graphics.drawString(font, meta, contentX, cursorY,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, 0xCC), false);
        cursorY += 16;

        String body = message.body() != null ? message.body() : tr(
            "devmod.notification.center.mailbox.preview_missing");
        List<String> lines = wrapText(font, body, contentWidth);

        int contentTop = cursorY + 6;
        int actionAreaHeight = getDetailActionAreaHeight(actions, rect.w());
        int contentHeight = rect.h() - (contentTop - rect.y()) - actionAreaHeight;
        detailScrollMax = Math.max(0, lines.size() * 12 - contentHeight);
        detailScrollOffset = Mth.clamp(detailScrollOffset, 0, detailScrollMax);

        boolean scissor = contentWidth > 0 && contentHeight > 0;
        if (scissor) {
            graphics.enableScissor(contentX, contentTop, contentX + contentWidth, contentTop + contentHeight);
        }
        try {
            int lineY = contentTop - detailScrollOffset;
            for (String line : lines) {
                if (lineY + 10 >= contentTop && lineY <= contentTop + contentHeight) {
                    graphics.drawString(font, line, contentX, lineY,
                        NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, 0xDD), false);
                }
                lineY += 12;
            }
        } finally {
            if (scissor) {
                graphics.disableScissor();
            }
        }

        renderDetailActions(graphics, rect, mouseX, mouseY, actions);
    }

    private void renderNewsDetail(GuiGraphics graphics, Rect rect, int panelAlpha, int mouseX, int mouseY) {
        NewsArticleData article = getSelectedNewsArticle();
        if (article == null) {
            renderEmptyState(graphics, rect, "devmod.notification.center.detail.empty.title",
                "devmod.notification.center.detail.empty.subtitle", panelAlpha);
            return;
        }

        Font font = Objects.requireNonNull(this.font);
        List<DetailAction> actions = List.of();
        int contentX = rect.x() + 12;
        int contentWidth = rect.w() - 24;
        int cursorY = rect.y() + 12;

        graphics.drawString(font, trimText(font, nn(article.title()), contentWidth), contentX, cursorY,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, 0xFF), true);
        cursorY += 16;

        String meta = resolveNewsCategoryLabel(article.categoryOrdinal()) + " • " + article.authorName();
        graphics.drawString(font, meta, contentX, cursorY,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, 0xCC), false);
        cursorY += 16;

        List<String> lines = wrapText(font, article.content(), contentWidth);
        int contentTop = cursorY + 6;
        int actionAreaHeight = getDetailActionAreaHeight(actions, rect.w());
        int contentHeight = rect.h() - (contentTop - rect.y()) - actionAreaHeight;
        detailScrollMax = Math.max(0, lines.size() * 12 - contentHeight);
        detailScrollOffset = Mth.clamp(detailScrollOffset, 0, detailScrollMax);

        boolean scissor = contentWidth > 0 && contentHeight > 0;
        if (scissor) {
            graphics.enableScissor(contentX, contentTop, contentX + contentWidth, contentTop + contentHeight);
        }
        try {
            int lineY = contentTop - detailScrollOffset;
            for (String line : lines) {
                if (lineY + 10 >= contentTop && lineY <= contentTop + contentHeight) {
                    graphics.drawString(font, line, contentX, lineY,
                        NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, 0xDD), false);
                }
                lineY += 12;
            }
        } finally {
            if (scissor) {
                graphics.disableScissor();
            }
        }

        renderDetailActions(graphics, rect, mouseX, mouseY, actions);
    }

    private void renderTicketDetail(GuiGraphics graphics, Rect rect, int panelAlpha, int mouseX, int mouseY) {
        TicketData ticket = getSelectedTicket();
        if (ticket == null) {
            renderEmptyState(graphics, rect, "devmod.notification.center.detail.empty.title",
                "devmod.notification.center.detail.empty.subtitle", panelAlpha);
            return;
        }

        Font font = Objects.requireNonNull(this.font);
        List<DetailAction> actions = new ArrayList<>();
        String commentLabel = tr("devmod.ticket.action.add_comment");
        actions.add(new DetailAction(commentLabel, false, true, () -> openTicketComment(ticket)));
        if (ticket.status() == TicketStatus.CLOSED || ticket.status() == TicketStatus.RESOLVED) {
            String reopenLabel = tr("devmod.ticket.action.reopen");
            actions.add(new DetailAction(reopenLabel, true, true,
                () -> updateTicketStatus(ticket, TicketStatus.OPEN)));
        } else {
            String closeLabel = tr("devmod.ticket.action.close");
            actions.add(new DetailAction(closeLabel, true, true,
                () -> updateTicketStatus(ticket, TicketStatus.CLOSED)));
        }
        int contentX = rect.x() + 12;
        int contentWidth = rect.w() - 24;
        int cursorY = rect.y() + 12;

        graphics.drawString(font, trimText(font, nn(ticket.subject()), contentWidth), contentX, cursorY,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, 0xFF), true);
        cursorY += 16;

        String meta = ticket.category().getDisplayName() + " • " + ticket.status().getDisplayName();
        graphics.drawString(font, meta, contentX, cursorY,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, 0xCC), false);
        cursorY += 16;

        String priority = ticket.priority().getDisplayName();
        int priorityColor = parseHexColor(ticket.priority(), NotificationUiTheme.RGB_TEXT_SECONDARY);
        graphics.drawString(font, "Priority: " + priority, contentX, cursorY,
            NotificationUiTheme.withAlpha(priorityColor, 0xFF), false);
        cursorY += 16;

        String description = ticket.description() != null ? ticket.description()
            : tr("devmod.notification.center.ticket.no_description");
        List<String> lines = wrapText(font, description, contentWidth);
        int contentTop = cursorY + 6;
        int actionAreaHeight = getDetailActionAreaHeight(actions, rect.w());
        int contentHeight = rect.h() - (contentTop - rect.y()) - actionAreaHeight;
        detailScrollMax = Math.max(0, lines.size() * 12 - contentHeight);
        detailScrollOffset = Mth.clamp(detailScrollOffset, 0, detailScrollMax);

        boolean scissor = contentWidth > 0 && contentHeight > 0;
        if (scissor) {
            graphics.enableScissor(contentX, contentTop, contentX + contentWidth, contentTop + contentHeight);
        }
        try {
            int lineY = contentTop - detailScrollOffset;
            for (String line : lines) {
                if (lineY + 10 >= contentTop && lineY <= contentTop + contentHeight) {
                    graphics.drawString(font, line, contentX, lineY,
                        NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, 0xDD), false);
                }
                lineY += 12;
            }
        } finally {
            if (scissor) {
                graphics.disableScissor();
            }
        }

        renderDetailActions(graphics, rect, mouseX, mouseY, actions);
    }

    private void renderTaskDetail(GuiGraphics graphics, Rect rect, int panelAlpha, int mouseX, int mouseY) {
        TestTask task = getSelectedTask();
        if (task == null) {
            renderEmptyState(graphics, rect, "devmod.notification.center.detail.empty.title",
                "devmod.notification.center.detail.empty.subtitle", panelAlpha);
            return;
        }

        Font font = Objects.requireNonNull(this.font);
        List<DetailAction> actions = new ArrayList<>();
        if (task.status() == TestTask.TaskStatus.PENDING) {
            String label = tr("devmod.tester.start");
            actions.add(new DetailAction(label, true, true,
                () -> updateTaskStatus(task, TestTask.TaskStatus.IN_PROGRESS)));
        } else if (task.status() == TestTask.TaskStatus.IN_PROGRESS) {
            String label = tr("devmod.tester.complete");
            actions.add(new DetailAction(label, true, true,
                () -> updateTaskStatus(task, TestTask.TaskStatus.COMPLETED)));
        }
        String notesLabel = tr("devmod.tester.add_notes");
        actions.add(new DetailAction(notesLabel, false, true, () -> openTaskNotes(task)));

        int contentX = rect.x() + 12;
        int contentWidth = rect.w() - 24;
        int cursorY = rect.y() + 12;

        graphics.drawString(font, trimText(font, nn(task.title()), contentWidth), contentX, cursorY,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, 0xFF), true);
        cursorY += 16;

        String meta = task.status().name().replace('_', ' ').toLowerCase(Locale.ROOT) + " • P" + task.priority();
        graphics.drawString(font, meta, contentX, cursorY,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, 0xCC), false);
        cursorY += 16;

        String description = task.description() != null ? task.description()
            : tr("devmod.notification.center.task.no_description");
        List<String> lines = wrapText(font, description, contentWidth);
        int contentTop = cursorY + 6;
        int actionAreaHeight = getDetailActionAreaHeight(actions, rect.w());
        int contentHeight = rect.h() - (contentTop - rect.y()) - actionAreaHeight;
        detailScrollMax = Math.max(0, lines.size() * 12 - contentHeight);
        detailScrollOffset = Mth.clamp(detailScrollOffset, 0, detailScrollMax);

        boolean scissor = contentWidth > 0 && contentHeight > 0;
        if (scissor) {
            graphics.enableScissor(contentX, contentTop, contentX + contentWidth, contentTop + contentHeight);
        }
        try {
            int lineY = contentTop - detailScrollOffset;
            for (String line : lines) {
                if (lineY + 10 >= contentTop && lineY <= contentTop + contentHeight) {
                    graphics.drawString(font, line, contentX, lineY,
                        NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, 0xDD), false);
                }
                lineY += 12;
            }
        } finally {
            if (scissor) {
                graphics.disableScissor();
            }
        }

        renderDetailActions(graphics, rect, mouseX, mouseY, actions);
    }

    private void renderListRowBackground(GuiGraphics graphics, Rect rect, boolean selected, boolean hovered,
                                         int panelAlpha, int accent) {
        int baseTop = selected ? NotificationUiTheme.RGB_SURFACE_HOVER_TOP : NotificationUiTheme.RGB_SURFACE_TOP;
        int baseBottom = selected ? NotificationUiTheme.RGB_SURFACE_HOVER_BOTTOM : NotificationUiTheme.RGB_SURFACE_BOTTOM;
        if (hovered) {
            baseTop = NotificationUiTheme.mix(baseTop, 0xFFFFFF, 0.04f);
            baseBottom = NotificationUiTheme.mix(baseBottom, 0xFFFFFF, 0.03f);
        }
        int top = NotificationUiTheme.withAlpha(baseTop, Math.min(panelAlpha, 0xE0));
        int bottom = NotificationUiTheme.withAlpha(baseBottom, Math.min(panelAlpha, 0xE0));
        renderRoundedRect(graphics, rect.x(), rect.y(), rect.w(), rect.h(), 6, top, bottom);
        graphics.fill(rect.x(), rect.y() + 4, rect.x() + 3, rect.y() + rect.h() - 4,
            NotificationUiTheme.withAlpha(accent, Math.min(panelAlpha, 0xE0)));
    }

    private void renderInsetPanel(GuiGraphics graphics, Rect rect, int panelAlpha) {
        int top = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_INNER_TOP, Math.min(panelAlpha, 0xE0));
        int bottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_INNER_BOTTOM, Math.min(panelAlpha, 0xE0));
        renderRoundedRect(graphics, rect.x(), rect.y(), rect.w(), rect.h(), 8, top, bottom);
    }

    private void renderActionButton(GuiGraphics graphics, Rect rect, String label,
                                    int mouseX, int mouseY, boolean accent, boolean enabled) {
        Font font = Objects.requireNonNull(this.font);
        boolean hovered = rect.contains(mouseX, mouseY);

        int base = accent ? NotificationUiTheme.RGB_ACCENT_SOFT : NotificationUiTheme.RGB_SURFACE_TOP;
        if (!enabled) {
            base = NotificationUiTheme.RGB_SURFACE_BOTTOM;
        }
        int top = NotificationUiTheme.withAlpha(hovered && enabled ? NotificationUiTheme.mix(base, 0xFFFFFF, 0.08f) : base, 0xCC);
        int bottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_BOTTOM, 0xCC);

        graphics.fillGradient(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), top, bottom);
        graphics.fill(rect.x(), rect.y() + rect.h() - 1, rect.x() + rect.w(), rect.y() + rect.h(),
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT, enabled ? 0x66 : 0x22));

        int textColor = accent ? NotificationUiTheme.RGB_TEXT_PRIMARY : NotificationUiTheme.RGB_TEXT_SECONDARY;
        if (!enabled) {
            textColor = NotificationUiTheme.RGB_TEXT_MUTED;
        }
        graphics.drawString(font, label, rect.x() + 7, rect.y() + 6,
                NotificationUiTheme.withAlpha(textColor, 0xFF), false);
    }

    private int getDetailActionAreaHeight(List<DetailAction> actions, int rectWidth) {
        int rows = countDetailActionRows(actions, rectWidth - 24);
        if (rows <= 0) {
            return 18;
        }
        return rows * DETAIL_ACTION_HEIGHT + (rows - 1) * DETAIL_ACTION_GAP + 16;
    }

    private int countDetailActionRows(List<DetailAction> actions, int maxWidth) {
        if (actions.isEmpty()) {
            return 0;
        }
        Font font = Objects.requireNonNull(this.font);
        int rows = 1;
        int rightX = maxWidth;
        for (DetailAction action : actions) {
            int width = Math.min(maxWidth, font.width(nn(action.label())) + 14);
            if (rightX - width < 0) {
                rows++;
                rightX = maxWidth;
            }
            rightX -= width + 8;
        }
        return rows;
    }

    private void renderDetailActions(GuiGraphics graphics, Rect rect, int mouseX, int mouseY,
                                     List<DetailAction> actions) {
        if (actions.isEmpty()) {
            return;
        }
        Font font = Objects.requireNonNull(this.font);
        int rightX = rect.x() + rect.w() - 12;
        int leftBound = rect.x() + 12;
        int y = rect.y() + rect.h() - DETAIL_ACTION_HEIGHT - 12;

        for (DetailAction action : actions) {
            int width = font.width(nn(action.label())) + 14;
            if (rightX - width < leftBound) {
                y -= DETAIL_ACTION_HEIGHT + DETAIL_ACTION_GAP;
                rightX = rect.x() + rect.w() - 12;
            }
            Rect buttonRect = new Rect(rightX - width, y, width, DETAIL_ACTION_HEIGHT);
            renderActionButton(graphics, buttonRect, action.label(), mouseX, mouseY, action.accent(), action.enabled());
            actionButtons.add(new ActionButton(buttonRect, action.onClick(), action.enabled()));
            rightX = buttonRect.x() - 8;
        }
    }

    private int renderFilters(GuiGraphics graphics, int startX, int startY, int width,
                              int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(this.font);
        filterChips.clear();

        int chipX = startX;
        int chipY = startY;
        int chipMaxX = startX + width;

        List<FilterChip> filters = buildFilterChips();
        for (FilterChip chip : filters) {
            String label = nn(chip.label());
            int chipW = font.width(label) + 18;
            if (chipX + chipW > chipMaxX) {
                chipX = startX;
                chipY += CHIP_HEIGHT + CHIP_GAP;
            }

            Rect rect = new Rect(chipX, chipY, chipW, CHIP_HEIGHT);
            boolean active = Objects.equals(activeFilter, chip.category());
            boolean hovered = rect.contains(mouseX, mouseY);
            renderFilterChip(graphics, rect, label, active, hovered, chip.category());

            filterChips.add(new FilterChip(chip.category(), rect, label));
            chipX += chipW + CHIP_GAP;
        }

        return chipY + CHIP_HEIGHT;
    }

    private void renderFilterChip(GuiGraphics graphics, Rect rect, String label, boolean active, boolean hovered,
                                  @Nullable NotificationCategory category) {
        Font font = Objects.requireNonNull(this.font);

        int baseTop = active ? NotificationUiTheme.RGB_SURFACE_HOVER_TOP : NotificationUiTheme.RGB_SURFACE_TOP;
        int baseBottom = active ? NotificationUiTheme.RGB_SURFACE_HOVER_BOTTOM : NotificationUiTheme.RGB_SURFACE_BOTTOM;
        if (hovered) {
            baseTop = NotificationUiTheme.mix(baseTop, 0xFFFFFF, 0.08f);
            baseBottom = NotificationUiTheme.mix(baseBottom, 0xFFFFFF, 0.05f);
        }

        int top = NotificationUiTheme.withAlpha(baseTop, 0xDD);
        int bottom = NotificationUiTheme.withAlpha(baseBottom, 0xDD);
        renderRoundedRect(graphics, rect.x(), rect.y(), rect.w(), rect.h(), 6, top, bottom);

        int accent = category != null ? NotificationUiTheme.getCategoryColor(category) : NotificationUiTheme.RGB_ACCENT;
        int accentAlpha = active ? 0xCC : 0x66;
        graphics.fill(rect.x(), rect.y() + rect.h() - 1, rect.x() + rect.w(), rect.y() + rect.h(),
                NotificationUiTheme.withAlpha(accent, accentAlpha));

        int textColor = active ? NotificationUiTheme.RGB_TEXT_PRIMARY : NotificationUiTheme.RGB_TEXT_SECONDARY;
        graphics.drawString(font, label, rect.x() + 9, rect.y() + 6,
                NotificationUiTheme.withAlpha(textColor, 0xFF), false);
    }

    private void renderEmptyState(GuiGraphics graphics, Rect rect, String titleKey, String subtitleKey, int alpha) {
        Font font = Objects.requireNonNull(this.font);
        String title = tr(titleKey);
        String subtitle = tr(subtitleKey);

        int centerX = rect.x() + rect.w() / 2;
        int centerY = rect.y() + rect.h() / 2 - 10;

        graphics.drawString(font, title, centerX - font.width(title) / 2, centerY,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, alpha), true);
        graphics.drawString(font, subtitle, centerX - font.width(subtitle) / 2, centerY + 14,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, alpha), false);
    }

    private int getListContentHeight(int rowHeight, int count) {
        if (count <= 0) {
            return 0;
        }
        return count * rowHeight + (count - 1) * LIST_GAP;
    }

    private List<Notification> getFilteredNotifications() {
        List<Notification> history = ClientNotificationManager.INSTANCE.getHistory();
        if (activeFilter == null) {
            return history;
        }
        List<Notification> filtered = new ArrayList<>();
        for (Notification notification : history) {
            if (notification.category() == activeFilter) {
                filtered.add(notification);
            }
        }
        return filtered;
    }

    private List<FilterChip> buildFilterChips() {
        List<FilterChip> filters = new ArrayList<>();
        filters.add(new FilterChip(null, null,
            tr("devmod.notification.center.filter.all")));
        for (NotificationCategory category : NotificationCategory.values()) {
            filters.add(new FilterChip(category, null,
                tr(category.getTranslationKey())));
        }
        return filters;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (backRect.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }

        for (ActionButton action : actionButtons) {
            if (action.enabled() && action.rect().contains(mouseX, mouseY)) {
                action.onClick().run();
                return true;
            }
        }

        for (TabEntry entry : tabEntries) {
            if (entry.rect().contains(mouseX, mouseY)) {
                if (entry.enabled()) {
                    selectTab(entry.tab(), null);
                }
                return true;
            }
        }

        if (activeTab == Tab.NOTIFICATIONS) {
            for (FilterChip chip : filterChips) {
                Rect chipRect = chip.rect();
                if (chipRect != null && chipRect.contains(mouseX, mouseY)) {
                    activeFilter = chip.category();
                    scrollOffset = 0;
                    detailScrollOffset = 0;
                    ensureNotificationSelection();
                    return true;
                }
            }
        }

        if (listRect.contains(mouseX, mouseY)) {
            switch (activeTab) {
                case NOTIFICATIONS -> handleNotificationClick(mouseX, mouseY);
                case MAILBOX -> handleMailboxClick(mouseX, mouseY);
                case NEWS -> handleNewsClick(mouseX, mouseY);
                case TICKETS -> handleTicketClick(mouseX, mouseY);
                case TASKS -> handleTaskClick(mouseX, mouseY);
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleNotificationClick(double mouseX, double mouseY) {
        List<Notification> filtered = getFilteredNotifications();
        int y = listRect.y() - scrollOffset;
        for (Notification notification : filtered) {
            Rect rowRect = new Rect(listRect.x(), y, listRect.w(), NOTIFICATION_ROW_HEIGHT);
            if (rowRect.contains(mouseX, mouseY)) {
                selectedNotificationId = notification.id();
                detailScrollOffset = 0;
                ClientNotificationManager.INSTANCE.markRead(notification.id());
                if (detailRect.w() == 0 && notification.actionId() != null && !notification.actionId().isBlank()) {
                    invokeNotificationAction(notification);
                }
                return;
            }
            y += NOTIFICATION_ROW_HEIGHT + LIST_GAP;
        }
    }

    private void handleMailboxClick(double mouseX, double mouseY) {
        List<MailboxMessageData> messages = ClientMailboxCache.getMessages();
        int y = listRect.y() - scrollOffset;
        for (MailboxMessageData message : messages) {
            Rect rowRect = new Rect(listRect.x(), y, listRect.w(), MAILBOX_ROW_HEIGHT);
            if (rowRect.contains(mouseX, mouseY)) {
                selectedMailboxId = message.id();
                detailScrollOffset = 0;
                markMailboxRead(message);
                if (message.body() == null || (message.hasAttachment() && message.attachmentData() == null)) {
                    PacketDistributor.sendToServer(Objects.requireNonNull(MailboxActionPayload.refresh()));
                }
                if (detailRect.w() == 0) {
                    MailboxScreen.open();
                }
                return;
            }
            y += MAILBOX_ROW_HEIGHT + LIST_GAP;
        }
    }

    private void handleNewsClick(double mouseX, double mouseY) {
        List<NewsArticleData> articles = ClientNewsCache.getArticles();
        int y = listRect.y() - scrollOffset;
        for (NewsArticleData article : articles) {
            Rect rowRect = new Rect(listRect.x(), y, listRect.w(), NEWS_ROW_HEIGHT);
            if (rowRect.contains(mouseX, mouseY)) {
                selectedNewsId = article.id();
                detailScrollOffset = 0;
                if (!article.isRead()) {
                    PacketDistributor.sendToServer(new NewsReadPayload(article.id()));
                    ClientNewsCache.markAsRead(article.id());
                }
                if (detailRect.w() == 0) {
                    NewsScreen.open();
                }
                return;
            }
            y += NEWS_ROW_HEIGHT + LIST_GAP;
        }
    }

    private void handleTicketClick(double mouseX, double mouseY) {
        List<TicketData> tickets = ClientTicketCache.getTickets();
        int y = listRect.y() - scrollOffset;
        for (TicketData ticket : tickets) {
            Rect rowRect = new Rect(listRect.x(), y, listRect.w(), TICKET_ROW_HEIGHT);
            if (rowRect.contains(mouseX, mouseY)) {
                selectedTicketId = ticket.id();
                detailScrollOffset = 0;
                return;
            }
            y += TICKET_ROW_HEIGHT + LIST_GAP;
        }
    }

    private void handleTaskClick(double mouseX, double mouseY) {
        if (!ClientMailboxAccess.isTester()) {
            return;
        }
        List<TestTask> tasks = ClientTaskCache.getTasks();
        int y = listRect.y() - scrollOffset;
        for (TestTask task : tasks) {
            Rect rowRect = new Rect(listRect.x(), y, listRect.w(), TASK_ROW_HEIGHT);
            if (rowRect.contains(mouseX, mouseY)) {
                selectedTaskId = task.id();
                detailScrollOffset = 0;
                if (detailRect.w() == 0) {
                    TesterTaskScreen.open(task.id());
                }
                return;
            }
            y += TASK_ROW_HEIGHT + LIST_GAP;
        }
    }

    private void markMailboxRead(MailboxMessageData message) {
        if (message.isRead()) {
            return;
        }
        PacketDistributor.sendToServer(Objects.requireNonNull(MailboxActionPayload.read(message.id())));
        ClientMailboxCache.markAsRead(message.id());
    }

    private void claimMailboxMessage(MailboxMessageData message) {
        if (!message.canClaimAttachment()) {
            return;
        }
        PacketDistributor.sendToServer(Objects.requireNonNull(MailboxActionPayload.claim(message.id())));
        ClientMailboxCache.markAttachmentClaimed(message.id());
    }

    private void deleteMailboxMessage(MailboxMessageData message) {
        if (message.canClaimAttachment()) {
            return;
        }
        PacketDistributor.sendToServer(Objects.requireNonNull(MailboxActionPayload.delete(message.id())));
        ClientMailboxCache.removeMessage(message.id());
        if (message.id().equals(selectedMailboxId)) {
            List<MailboxMessageData> messages = ClientMailboxCache.getMessages();
            selectedMailboxId = messages.isEmpty() ? null : messages.get(0).id();
            detailScrollOffset = 0;
        }
    }

    private void updateTaskStatus(TestTask task, TestTask.TaskStatus status) {
        ClientTaskCache.updateTaskStatus(task.id(), status);
        PacketDistributor.sendToServer(Objects.requireNonNull(TaskActionPayload.updateStatus(task.id(), status)));
    }

    private void updateTicketStatus(TicketData ticket, TicketStatus status) {
        ClientTicketCache.updateStatus(ticket.id(), status);
        PacketDistributor.sendToServer(Objects.requireNonNull(TicketActionPayload.updateStatus(ticket.id(), status)));
    }

    private void openTaskNotes(TestTask task) {
        TesterTaskScreen.openForNotes(task.id());
    }

    private void openTicketComment(TicketData ticket) {
        TicketCommentScreen.open(this, ticket);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (matchesKeybind(KeyInputHandler.OPEN_NOTIFICATION_CENTER_KEY, keyCode, scanCode)) {
            if (activeTab != Tab.NOTIFICATIONS) {
                selectTab(Tab.NOTIFICATIONS, null);
            }
            return true;
        }
        if (matchesKeybind(KeyInputHandler.OPEN_MAILBOX_KEY, keyCode, scanCode)) {
            if (activeTab != Tab.MAILBOX) {
                selectTab(Tab.MAILBOX, null);
            }
            return true;
        }
        if (matchesKeybind(KeyInputHandler.OPEN_TESTER_TASKS_KEY, keyCode, scanCode)) {
            if (activeTab != Tab.TASKS) {
                selectTab(Tab.TASKS, null);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean matchesKeybind(KeyMapping mapping, int keyCode, int scanCode) {
        return mapping.matches(keyCode, scanCode);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (detailRect.contains(mouseX, mouseY) && detailScrollMax > 0) {
            detailScrollOffset = (int) Mth.clamp(detailScrollOffset - scrollY * 16, 0, detailScrollMax);
            return true;
        }
        if (listRect.contains(mouseX, mouseY) && maxScroll > 0) {
            scrollOffset = (int) Mth.clamp(scrollOffset - scrollY * 20, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void invokeNotificationAction(Notification notification) {
        if (notification == null) {
            return;
        }
        NotificationActionResolver.invoke(notification, ActionOrigin.UI);
    }

    @Nullable
    private Notification getSelectedNotification() {
        if (selectedNotificationId == null) {
            return null;
        }
        for (Notification notification : getFilteredNotifications()) {
            if (notification.id().equals(selectedNotificationId)) {
                return notification;
            }
        }
        return null;
    }

    @Nullable
    private MailboxMessageData getSelectedMailboxMessage() {
        if (selectedMailboxId == null) {
            return null;
        }
        return ClientMailboxCache.getMessage(selectedMailboxId);
    }

    @Nullable
    private NewsArticleData getSelectedNewsArticle() {
        if (selectedNewsId == null) {
            return null;
        }
        return ClientNewsCache.getArticle(selectedNewsId);
    }

    @Nullable
    private TicketData getSelectedTicket() {
        if (selectedTicketId == null) {
            return null;
        }
        return ClientTicketCache.getTicket(selectedTicketId);
    }

    @Nullable
    private TestTask getSelectedTask() {
        if (selectedTaskId == null) {
            return null;
        }
        return ClientTaskCache.getTask(selectedTaskId);
    }

    private String resolveTitle(Notification notification) {
        String titleKey = notification.titleKey();
        Map<String, String> params = notification.params();
        if (titleKey != null && !titleKey.isBlank()) {
            return tr(titleKey, params.values().toArray(new Object[0]));
        }
        return defaultTitle(notification.category(), params);
    }

    private String resolveMessage(Notification notification) {
        String messageKey = notification.messageKey();
        Map<String, String> params = notification.params();
        if (messageKey != null && !messageKey.isBlank()) {
            return tr(messageKey, params.values().toArray(new Object[0]));
        }

        String details = tr("devmod.notification.center.details_hint");
        if (params == null || params.isEmpty()) {
            return details;
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (isTitleParam(entry.getKey())) {
                continue;
            }
            parts.add(formatParamKey(entry.getKey()) + ": " + entry.getValue());
        }
        if (parts.isEmpty()) {
            return details;
        }
        return String.join(" | ", parts);
    }

    private List<String> buildParamLines(Notification notification) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : notification.params().entrySet()) {
            if (isTitleParam(entry.getKey())) {
                continue;
            }
            lines.add(formatParamKey(entry.getKey()) + ": " + entry.getValue());
        }
        return lines;
    }

    private String defaultTitle(NotificationCategory category, Map<String, String> params) {
        if (params != null) {
            if (params.containsKey("badge")) return "Badge: " + params.get("badge");
            if (params.containsKey("record")) return "Record: " + params.get("record");
            if (params.containsKey("tier")) return "Tier " + params.get("tier");
            if (params.containsKey("wave")) return "Wave " + params.get("wave");
            if (params.containsKey("amount") && category == NotificationCategory.TOKEN) {
                return "+" + params.get("amount") + " tokens";
            }
        }
        return switch (category) {
            case ACHIEVEMENT -> "Achievement";
            case RECORD -> "Record";
            case SEASON -> "Season";
            case TOKEN -> "Tokens";
            case REWARD -> "Reward";
            case PARTY -> "Party";
            case QUEST -> "Quest";
            case COMBAT -> "Combat";
            case RESONANCE -> "Resonance";
            case ADMIN -> "Admin";
            case SYSTEM -> "System";
            case MAILBOX -> "Mailbox";
            case NEWS -> "News";
        };
    }

    private boolean isTitleParam(String key) {
        return key.equals("badge")
                || key.equals("record")
                || key.equals("tier")
                || key.equals("wave");
    }

    private String formatParamKey(String key) {
        if (key.isEmpty()) return key;
        String cleaned = key.replace('_', ' ');
        return cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
    }

    private String formatAge(Instant createdAt) {
        Duration duration = Duration.between(createdAt, Instant.now());
        long seconds = Math.max(0, duration.getSeconds());
        if (seconds < 60) {
            return "now";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h";
        }
        long days = hours / 24;
        return days + "d";
    }

    private String trimText(Font font, @Nonnull String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - 8)) + "...";
    }

    private List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        String[] words = text.split(" ", -1);
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.isEmpty()) {
                current.append(word);
            } else if (font.width(current + " " + word) <= maxWidth) {
                current.append(" ").append(word);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private int getTabColor(Tab tab) {
        return switch (tab) {
            case NOTIFICATIONS -> NotificationUiTheme.RGB_ACCENT;
            case MAILBOX -> NotificationUiTheme.getCategoryColor(NotificationCategory.MAILBOX);
            case NEWS -> NotificationUiTheme.getCategoryColor(NotificationCategory.NEWS);
            case TICKETS -> NotificationUiTheme.RGB_ACCENT_SOFT;
            case TASKS -> NotificationUiTheme.RGB_ACCENT_ALT;
        };
    }

    private int getTabBadgeCount(Tab tab) {
        return switch (tab) {
            case NOTIFICATIONS -> ClientNotificationManager.INSTANCE.getUnreadCount();
            case MAILBOX -> ClientMailboxCache.getUnreadCount();
            case NEWS -> ClientNewsCache.getUnreadCount();
            case TICKETS -> ClientTicketCache.getOpenCount();
            case TASKS -> ClientTaskCache.getPendingCount();
        };
    }

    private String getActiveSubtitle() {
        return switch (activeTab) {
            case NOTIFICATIONS -> tr("devmod.notification.center.subtitle",
                ClientNotificationManager.INSTANCE.getHistory().size(),
                ClientNotificationManager.INSTANCE.getUnreadCount());
            case MAILBOX -> tr("devmod.notification.center.subtitle.mailbox",
                ClientMailboxCache.getUnreadCount(),
                ClientMailboxCache.getMessageCount());
            case NEWS -> tr("devmod.notification.center.subtitle.news",
                ClientNewsCache.getUnreadCount(),
                ClientNewsCache.getArticleCount());
            case TICKETS -> tr("devmod.notification.center.subtitle.tickets",
                ClientTicketCache.getOpenCount(),
                ClientTicketCache.getTotalCount());
            case TASKS -> tr("devmod.notification.center.subtitle.tasks",
                ClientTaskCache.getPendingCount(),
                ClientTaskCache.getTaskCount());
        };
    }

    private boolean isTabEnabled(Tab tab) {
        if (tab == Tab.TASKS) {
            return ClientMailboxAccess.isTester();
        }
        return true;
    }

    private MessageType resolveMessageType(int ordinal) {
        MessageType[] values = MessageType.values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return MessageType.SYSTEM;
    }

    private String resolveNewsCategoryLabel(int ordinal) {
        NewsCategory[] values = NewsCategory.values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal].getDisplayName();
        }
        return "News";
    }

    private int parseHexColor(TicketPriority priority, int fallback) {
        if (priority == null || priority.getColorHex() == null) {
            return fallback;
        }
        String hex = priority.getColorHex().replace("#", "");
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void renderRoundedRect(GuiGraphics graphics, int x, int y, int width, int height, int radius,
                                   int topColor, int bottomColor) {
        // radius reserved for future rounded corner implementation
        if (radius < 0) throw new IllegalArgumentException("radius must be non-negative");
        graphics.fillGradient(x, y, x + width, y + height, topColor, bottomColor);
    }

    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }
    }

    private record FilterChip(@Nullable NotificationCategory category, @Nullable Rect rect, String label) {}

    private record TabEntry(Tab tab, Rect rect, boolean enabled) {}

    private record ActionButton(Rect rect, Runnable onClick, boolean enabled) {}

    private record DetailAction(String label, boolean accent, boolean enabled, Runnable onClick) {}

    private enum Tab {
        NOTIFICATIONS("NOTIFICATIONS", "devmod.notification.center.tab.notifications"),
        MAILBOX("MAILBOX", "devmod.notification.center.tab.mailbox"),
        NEWS("NEWS", "devmod.notification.center.tab.news"),
        TICKETS("TICKETS", "devmod.notification.center.tab.tickets"),
        TASKS("TASKS", "devmod.notification.center.tab.tasks");

        private final String id;
        private final String labelKey;

        Tab(String id, String labelKey) {
            this.id = id;
            this.labelKey = labelKey;
        }

        public static Tab fromId(String id) {
            if (id == null) {
                return NOTIFICATIONS;
            }
            for (Tab tab : values()) {
                if (tab.id.equalsIgnoreCase(id)) {
                    return tab;
                }
            }
            return NOTIFICATIONS;
        }
    }
}
