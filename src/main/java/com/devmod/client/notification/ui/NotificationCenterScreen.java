package com.devmod.client.notification.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionOrigin;
import com.devmod.client.notification.ClientNotificationManager;
import com.devmod.client.notification.NotificationActionResolver;
import com.devmod.client.notification.NotificationUiTheme;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;

@OnlyIn(Dist.CLIENT)
public class NotificationCenterScreen extends Screen {

    private static final int PANEL_MAX_WIDTH = 900;
    private static final int PANEL_MAX_HEIGHT = 560;
    private static final int PANEL_PADDING = 18;
    private static final int HEADER_HEIGHT = 68;
    private static final int CHIP_HEIGHT = 22;
    private static final int CHIP_GAP = 6;
    private static final int CARD_HEIGHT = 68;
    private static final int CARD_GAP = 8;
    private static final int ACTION_HEIGHT = 20;

    @Nullable
    private final Screen parent;
    private final List<FilterChip> filterChips = new ArrayList<>();

    @Nullable
    private NotificationCategory activeFilter;

    private long openedAt;
    private int scrollOffset;
    private int maxScroll;

    private Rect listRect = new Rect(0, 0, 0, 0);
    private Rect backRect = new Rect(0, 0, 0, 0);
    private Rect settingsRect = new Rect(0, 0, 0, 0);
    private Rect markAllRect = new Rect(0, 0, 0, 0);

    public NotificationCenterScreen(@Nullable Screen parent) {
        super(Component.translatable("devmod.notification.center.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();
        scrollOffset = 0;
        maxScroll = 0;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        long now = System.currentTimeMillis();
        float openProgress = easeOutCubic(Math.min(1f, (now - openedAt) / 280f));

        int panelWidth = Math.min(PANEL_MAX_WIDTH, width - 32);
        int panelHeight = Math.min(PANEL_MAX_HEIGHT, height - 32);
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        int panelAlpha = (int) (235 * openProgress);

        graphics.pose().pushPose();
        graphics.pose().translate(0, (1f - openProgress) * 12f, 0);

        renderPanel(graphics, panelX, panelY, panelWidth, panelHeight, panelAlpha, mouseX, mouseY, now);

        graphics.pose().popPose();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int top = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_TOP, 0xFF);
        int bottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_BOTTOM, 0xFF);
        graphics.fillGradient(0, 0, width, height, top, bottom);

        int stripeColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT_ALT, 0x1A);
        for (int x = -height; x < width; x += 40) {
            graphics.fill(x, 0, x + 2, height, stripeColor);
        }

        int dotColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT, 0x22);
        for (int i = 0; i < width; i += 64) {
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
        int chipStartY = panelY + HEADER_HEIGHT;
        int chipEndY = renderFilters(graphics, panelX, chipStartY, panelWidth, mouseX, mouseY);

        int listTop = chipEndY + 12;
        int listBottom = panelY + panelHeight - PANEL_PADDING;
        int listHeight = Math.max(0, listBottom - listTop);
        listRect = new Rect(panelX + PANEL_PADDING, listTop, panelWidth - PANEL_PADDING * 2, listHeight);

        List<Notification> filtered = getFilteredNotifications();
        int contentHeight = filtered.size() * (CARD_HEIGHT + CARD_GAP);
        maxScroll = Math.max(0, contentHeight - listHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        if (filtered.isEmpty()) {
            renderEmptyState(graphics, panelX, listTop, panelWidth, listHeight, panelAlpha);
            return;
        }

        Font font = Objects.requireNonNull(this.font);
        int startX = panelX + PANEL_PADDING;
        int y = listTop - scrollOffset;

        for (int i = 0; i < filtered.size(); i++) {
            Notification notification = filtered.get(i);
            int cardY = y + i * (CARD_HEIGHT + CARD_GAP);
            if (cardY + CARD_HEIGHT < listTop || cardY > listBottom) {
                continue;
            }

            float cardProgress = easeOutCubic(Math.min(1f, (now - openedAt - i * 50f) / 240f));
            int cardAlpha = (int) (panelAlpha * Math.min(1f, cardProgress + 0.2f));
            int slideOffset = (int) ((1f - cardProgress) * 16f);

            int renderX = startX + slideOffset;
            boolean hovered = isMouseOver(mouseX, mouseY, renderX, cardY, listRect.w(), CARD_HEIGHT);

            renderCard(graphics, font, notification, renderX, cardY, listRect.w(), cardAlpha, hovered, now);
        }
    }

    private void renderHeader(GuiGraphics graphics, int panelX, int panelY, int panelWidth, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(this.font);
        String title = Component.translatable("devmod.notification.center.title").getString();

        int titleX = panelX + PANEL_PADDING;
        int titleY = panelY + 16;

        graphics.pose().pushPose();
        graphics.pose().translate(titleX, titleY, 0);
        graphics.pose().scale(1.3f, 1.3f, 1.0f);
        graphics.drawString(font, title, 0, 0,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, 0xFF), true);
        graphics.pose().popPose();

        int total = ClientNotificationManager.INSTANCE.getHistory().size();
        int unread = ClientNotificationManager.INSTANCE.getUnreadCount();
        Component subtitle = Objects.requireNonNull(Component.translatable("devmod.notification.center.subtitle", total, unread));
        graphics.drawString(font, subtitle, titleX, panelY + 38,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, 0xFF), false);

        int actionY = panelY + 20;
        int actionX = panelX + panelWidth - PANEL_PADDING;

        String settingsLabel = Objects.requireNonNull(Component.translatable("devmod.notification.center.settings").getString());
        int settingsW = font.width(settingsLabel) + 14;
        settingsRect = new Rect(actionX - settingsW, actionY, settingsW, ACTION_HEIGHT);
        renderActionButton(graphics, settingsRect, settingsLabel, mouseX, mouseY, false);

        actionX = settingsRect.x() - 8;

        String markAllLabel = Objects.requireNonNull(Component.translatable("devmod.notification.center.mark_all_read").getString());
        int markAllW = font.width(markAllLabel) + 14;
        markAllRect = new Rect(actionX - markAllW, actionY, markAllW, ACTION_HEIGHT);
        renderActionButton(graphics, markAllRect, markAllLabel, mouseX, mouseY, true);

        String backLabel = Objects.requireNonNull(Component.translatable("gui.back").getString());
        int backW = font.width(backLabel) + 14;
        backRect = new Rect(panelX + panelWidth - PANEL_PADDING - backW, panelY + HEADER_HEIGHT - 26, backW, ACTION_HEIGHT);
        renderActionButton(graphics, backRect, backLabel, mouseX, mouseY, false);
    }

    private void renderActionButton(GuiGraphics graphics, Rect rect, String label,
                                    int mouseX, int mouseY, boolean accent) {
        Font font = Objects.requireNonNull(this.font);
        boolean hovered = rect.contains(mouseX, mouseY);

        int base = accent ? NotificationUiTheme.RGB_ACCENT_SOFT : NotificationUiTheme.RGB_SURFACE_TOP;
        int top = NotificationUiTheme.withAlpha(hovered ? NotificationUiTheme.mix(base, 0xFFFFFF, 0.1f) : base, 0xCC);
        int bottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_BOTTOM, 0xCC);

        graphics.fillGradient(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), top, bottom);
        graphics.fill(rect.x(), rect.y() + rect.h() - 1, rect.x() + rect.w(), rect.y() + rect.h(),
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT, 0x66));

        int textColor = accent ? NotificationUiTheme.RGB_TEXT_PRIMARY : NotificationUiTheme.RGB_TEXT_SECONDARY;
        graphics.drawString(font, label, rect.x() + 7, rect.y() + 6,
                NotificationUiTheme.withAlpha(textColor, 0xFF), false);
    }

    private int renderFilters(GuiGraphics graphics, int panelX, int startY, int panelWidth, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(this.font);
        filterChips.clear();

        int chipX = panelX + PANEL_PADDING;
        int chipY = startY;
        int chipMaxX = panelX + panelWidth - PANEL_PADDING;

        List<FilterChip> filters = buildFilterChips();
        for (FilterChip chip : filters) {
            String label = Objects.requireNonNull(chip.label());
            int chipW = font.width(label) + 18;
            if (chipX + chipW > chipMaxX) {
                chipX = panelX + PANEL_PADDING;
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

    private void renderCard(GuiGraphics graphics, Font font, Notification notification, int x, int y,
                             int width, int alpha, boolean hovered, long now) {
        boolean read = ClientNotificationManager.INSTANCE.isRead(notification.id());

        int baseTop = read ? NotificationUiTheme.RGB_SURFACE_READ : NotificationUiTheme.RGB_SURFACE_TOP;
        int baseBottom = read ? NotificationUiTheme.RGB_SURFACE_BOTTOM : NotificationUiTheme.RGB_SURFACE_BOTTOM;
        if (hovered) {
            baseTop = NotificationUiTheme.RGB_SURFACE_HOVER_TOP;
            baseBottom = NotificationUiTheme.RGB_SURFACE_HOVER_BOTTOM;
        }

        int top = NotificationUiTheme.withAlpha(baseTop, alpha);
        int bottom = NotificationUiTheme.withAlpha(baseBottom, alpha);
        renderRoundedRect(graphics, x, y, width, CARD_HEIGHT, 8, top, bottom);

        int accent = NotificationUiTheme.getCategoryColor(notification.category());
        graphics.fill(x, y + 4, x + 4, y + CARD_HEIGHT - 4,
                NotificationUiTheme.withAlpha(accent, Math.min(alpha, 0xE0)));

        if (!read) {
            graphics.fill(x + width - 6, y + 6, x + width - 4, y + 12,
                    NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT, Math.min(alpha, 0xEE)));
        }

        int iconX = x + 14;
        int iconY = y + 18;
        int iconSize = 16;
        String iconText = getCategoryInitial(notification.category());
        int iconColor = NotificationUiTheme.withAlpha(accent, Math.min(alpha, 0xFF));
        graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize,
                NotificationUiTheme.withAlpha(NotificationUiTheme.mix(accent, 0x000000, 0.5f), Math.min(alpha, 0x99)));
        graphics.drawString(Objects.requireNonNull(font), iconText, iconX + 4, iconY + 4, iconColor, false);

        int contentX = iconX + iconSize + 10;
        int contentWidth = width - contentX - 16;

        String title = Objects.requireNonNull(resolveTitle(notification));
        if (font.width(title) > contentWidth) {
            title = Objects.requireNonNull(font.plainSubstrByWidth(title, contentWidth - 6)) + "...";
        }
        graphics.drawString(font, title, contentX, y + 12,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, Math.min(alpha, 0xFF)), true);

        String message = Objects.requireNonNull(resolveMessage(notification));
        if (font.width(message) > contentWidth) {
            message = Objects.requireNonNull(font.plainSubstrByWidth(message, contentWidth - 6)) + "...";
        }
        graphics.drawString(font, message, contentX, y + 30,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, Math.min(alpha, 0xDD)), false);

        String categoryLabel = Objects.requireNonNull(Component.translatable(Objects.requireNonNull(notification.category().getTranslationKey())).getString());
        String priorityLabel = Objects.requireNonNull(notification.priority().name().toLowerCase(Locale.ROOT));
        String timeLabel = Objects.requireNonNull(formatAge(notification.createdAt()));

        int metaY = y + CARD_HEIGHT - 14;
        graphics.drawString(font, categoryLabel, contentX, metaY,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, Math.min(alpha, 0xCC)), false);

        int priorityColor = NotificationUiTheme.getPriorityColor(notification.priority());
        int priorityWidth = font.width(priorityLabel);
        graphics.drawString(font, priorityLabel, x + width - priorityWidth - 12, metaY,
                NotificationUiTheme.withAlpha(priorityColor, Math.min(alpha, 0xDD)), false);

        int timeWidth = font.width(timeLabel);
        graphics.drawString(font, timeLabel, x + width - timeWidth - 12, y + 10,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, Math.min(alpha, 0xCC)), false);
    }

    private void renderEmptyState(GuiGraphics graphics, int panelX, int listTop, int panelWidth, int listHeight,
                                  int panelAlpha) {
        Font font = Objects.requireNonNull(this.font);
        String title = Objects.requireNonNull(Component.translatable("devmod.notification.center.empty.title").getString());
        String subtitle = Objects.requireNonNull(Component.translatable("devmod.notification.center.empty.subtitle").getString());

        int centerX = panelX + panelWidth / 2;
        int centerY = listTop + listHeight / 2 - 10;

        int titleWidth = font.width(title);
        int subtitleWidth = font.width(subtitle);

        graphics.drawString(font, title, centerX - titleWidth / 2, centerY,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, panelAlpha), true);
        graphics.drawString(font, subtitle, centerX - subtitleWidth / 2, centerY + 14,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, panelAlpha), false);
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
        filters.add(new FilterChip(null, null, Objects.requireNonNull(Component.translatable("devmod.notification.center.filter.all").getString())));
        for (NotificationCategory category : NotificationCategory.values()) {
            filters.add(new FilterChip(category, null, Objects.requireNonNull(Component.translatable(Objects.requireNonNull(category.getTranslationKey())).getString())));
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

        if (settingsRect.contains(mouseX, mouseY)) {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new NotificationSettingsScreen(this));
            return true;
        }

        if (markAllRect.contains(mouseX, mouseY)) {
            ClientNotificationManager.INSTANCE.markAllRead();
            return true;
        }

        for (FilterChip chip : filterChips) {
            Rect chipRect = chip.rect();
            if (chipRect != null && chipRect.contains(mouseX, mouseY)) {
                activeFilter = chip.category();
                scrollOffset = 0;
                return true;
            }
        }

        if (listRect.contains(mouseX, mouseY)) {
            List<Notification> filtered = getFilteredNotifications();
            int y = listRect.y() - scrollOffset;
            for (Notification notification : filtered) {
                int cardY = y;
                if (isMouseOver((int) mouseX, (int) mouseY, listRect.x(), cardY, listRect.w(), CARD_HEIGHT)) {
                    ClientNotificationManager.INSTANCE.markRead(notification.id());
                    NotificationActionResolver.invoke(notification, ActionOrigin.UI);
                    return true;
                }
                y += CARD_HEIGHT + CARD_GAP;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (listRect.contains(mouseX, mouseY) && maxScroll > 0) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 20));
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

    private String resolveTitle(Notification notification) {
        String titleKey = notification.titleKey();
        Map<String, String> params = notification.params();
        if (titleKey != null && !titleKey.isBlank()) {
            return Component.translatable(titleKey, Objects.requireNonNull(params.values().toArray())).getString();
        }
        return defaultTitle(notification.category(), params);
    }

    private String resolveMessage(Notification notification) {
        String messageKey = notification.messageKey();
        Map<String, String> params = notification.params();
        if (messageKey != null && !messageKey.isBlank()) {
            return Component.translatable(messageKey, Objects.requireNonNull(params.values().toArray())).getString();
        }

        String details = Component.translatable("devmod.notification.center.details_hint").getString();
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

    private String getCategoryInitial(NotificationCategory category) {
        String name = category.name();
        if (name.isEmpty()) return "";
        return name.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private void renderRoundedRect(GuiGraphics graphics, int x, int y, int width, int height, int radius,
                                    int topColor, int bottomColor) {
        graphics.fillGradient(x, y, x + width, y + height, topColor, bottomColor);
    }

    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
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
}
