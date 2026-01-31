package com.devmod.client.notification.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.notification.ClientNotificationPreferences;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.notification.NotificationUiTheme;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;

@OnlyIn(Dist.CLIENT)
public class NotificationSettingsScreen extends Screen {

    private static final int PANEL_MAX_WIDTH = 880;
    private static final int PANEL_MAX_HEIGHT = 560;
    private static final int PANEL_PADDING = 18;
    private static final int HEADER_HEIGHT = 64;
    private static final int COLUMN_GAP = 16;
    private static final int ROW_HEIGHT = 28;
    private static final int CATEGORY_ROW_HEIGHT = 30;
    private static final int TOGGLE_WIDTH = 34;
    private static final int SLIDER_HEIGHT = 10;

    @Nullable
    private final Screen parent;
    private final ClientNotificationPreferences prefs = ClientNotificationPreferences.INSTANCE;
    private final List<CategoryRow> categoryRows = new ArrayList<>();

    private boolean globalMute;
    private boolean preferChat;
    private float masterVolume;
    private NotificationPriority minPriority = NotificationPriority.LOW;

    private int scrollOffset;
    private int maxScroll;
    private boolean draggingMaster;
    @Nullable
    private NotificationCategory draggingCategory;
    private boolean pendingSync;

    private Rect listRect = new Rect(0, 0, 0, 0);
    private Rect backRect = new Rect(0, 0, 0, 0);
    private Rect resetRect = new Rect(0, 0, 0, 0);
    private Rect globalMuteRect = new Rect(0, 0, 0, 0);
    private Rect preferChatRect = new Rect(0, 0, 0, 0);
    private Rect masterSliderRect = new Rect(0, 0, 0, 0);
    private Rect minPriorityRect = new Rect(0, 0, 0, 0);

    private long openedAt;

    public NotificationSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("devmod.notification.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();
        scrollOffset = 0;
        maxScroll = 0;
        loadPreferences();
        initCategoryRows();
    }

    private void loadPreferences() {
        globalMute = prefs.isGlobalMute();
        masterVolume = prefs.getMasterVolume();
        minPriority = prefs.getMinOverlayPriority();
        preferChat = prefs.prefersChatOverOverlay();
    }

    private void initCategoryRows() {
        categoryRows.clear();
        for (NotificationCategory category : NotificationCategory.values()) {
            ClientNotificationPreferences.CategoryPreference pref = prefs.getCategoryPreference(category);
            boolean overlay = pref != null ? pref.overlayEnabled() : category.isDefaultOverlayEnabled();
            boolean sound = pref == null || pref.soundEnabled();
            float volume = pref != null ? pref.soundVolume() : 1.0f;
            categoryRows.add(new CategoryRow(category, overlay, sound, volume));
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        super.render(graphics, mouseX, mouseY, partialTick);

        int panelWidth = Math.min(PANEL_MAX_WIDTH, width - 32);
        int panelHeight = Math.min(PANEL_MAX_HEIGHT, height - 32);
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        int panelAlpha = (int) (240 * easeOutCubic(Math.min(1f, (System.currentTimeMillis() - openedAt) / 240f)));

        int shadowAlpha = Math.min(DesignTokens.Alpha.A50, (int) (panelAlpha * 0.6f));
        int shadowColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_BLACK, shadowAlpha);
        graphics.fill(panelX - 8, panelY - 6, panelX + panelWidth + 8, panelY + panelHeight + 8, shadowColor);

        renderPanel(graphics, panelX, panelY, panelWidth, panelHeight, panelAlpha, mouseX, mouseY);
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int top = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_BACKDROP_TOP, DesignTokens.Alpha.A100);
        int bottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_BACKDROP_BOTTOM, DesignTokens.Alpha.A100);
        graphics.fillGradient(0, 0, width, height, top, bottom);

        int stripeColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT_ALT, 18);
        for (int x = -height; x < width + height; x += 44) {
            graphics.fill(x, 0, x + 2, height, stripeColor);
        }
    }

    private void renderPanel(GuiGraphics graphics, int panelX, int panelY, int panelWidth, int panelHeight,
                              int panelAlpha, int mouseX, int mouseY) {
        int panelTop = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_TOP, panelAlpha);
        int panelBottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_BOTTOM, panelAlpha);
        graphics.fillGradient(panelX, panelY, panelX + panelWidth, panelY + panelHeight, panelTop, panelBottom);

        int borderAlpha = Math.min(DesignTokens.Alpha.A40, panelAlpha);
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

        int leftW = Math.min(280, (int) (contentW * 0.34f));
        int rightW = contentW - leftW - COLUMN_GAP;
        int leftX = contentX;
        int rightX = leftX + leftW + COLUMN_GAP;

        renderGlobalPanel(graphics, leftX, contentY, leftW, contentH, mouseX, mouseY);
        renderCategoryPanel(graphics, rightX, contentY, rightW, contentH, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics graphics, int panelX, int panelY, int panelWidth,
                               int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(this.font);
        String title = Component.translatable("devmod.notification.settings.title").getString();
        int titleX = panelX + PANEL_PADDING;

        graphics.pose().pushPose();
        graphics.pose().translate(titleX, panelY + 16, 0);
        graphics.pose().scale(1.3f, 1.3f, 1.0f);
        graphics.drawString(font, title, 0, 0,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, DesignTokens.Alpha.A100), true);
        graphics.pose().popPose();

        String subtitle = Component.translatable("devmod.notification.settings.subtitle").getString();
        graphics.drawString(font, subtitle, titleX, panelY + 38,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, DesignTokens.Alpha.A100), false);

        String backLabel = Objects.requireNonNull(Component.translatable("gui.back").getString());
        int backW = font.width(backLabel) + 14;
        backRect = new Rect(panelX + panelWidth - PANEL_PADDING - backW, panelY + 18, backW, 20);
        renderActionButton(graphics, backRect, backLabel, mouseX, mouseY, false);

        String resetLabel = Objects.requireNonNull(Component.translatable("controls.reset").getString());
        int resetW = font.width(resetLabel) + 14;
        resetRect = new Rect(backRect.x() - resetW - 8, panelY + 18, resetW, 20);
        renderActionButton(graphics, resetRect, resetLabel, mouseX, mouseY, true);
    }

    private void renderActionButton(GuiGraphics graphics, Rect rect, String label,
                                    int mouseX, int mouseY, boolean accent) {
        Font font = Objects.requireNonNull(this.font);
        boolean hovered = rect.contains(mouseX, mouseY);

        int base = accent ? NotificationUiTheme.RGB_ACCENT_SOFT : NotificationUiTheme.RGB_SURFACE_TOP;
        int top = NotificationUiTheme.withAlpha(
            hovered ? NotificationUiTheme.mix(base, NotificationUiTheme.RGB_WHITE, 0.08f) : base,
            DesignTokens.Alpha.A80);
        int bottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_BOTTOM, DesignTokens.Alpha.A80);

        graphics.fillGradient(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), top, bottom);
        graphics.fill(rect.x(), rect.y() + rect.h() - 1, rect.x() + rect.w(), rect.y() + rect.h(),
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT, DesignTokens.Alpha.A40));

        int textColor = accent ? NotificationUiTheme.RGB_TEXT_PRIMARY : NotificationUiTheme.RGB_TEXT_SECONDARY;
        graphics.drawString(font, label, rect.x() + 7, rect.y() + 6,
                NotificationUiTheme.withAlpha(textColor, DesignTokens.Alpha.A100), false);
    }

    private void renderGlobalPanel(GuiGraphics graphics, int x, int y, int width, int height,
                                   int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(this.font);
        int top = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_INNER_TOP, 208);
        int bottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_INNER_BOTTOM, 208);
        graphics.fillGradient(x, y, x + width, y + height, top, bottom);

        int headerColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT, DesignTokens.Alpha.A40);
        graphics.fill(x, y, x + width, y + 2, headerColor);

        int cursorY = y + 14;
        graphics.drawString(font, Objects.requireNonNull(Component.translatable("devmod.notification.settings.global")),
                x + 12, cursorY, NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, DesignTokens.Alpha.A100), true);

        cursorY += 18;

        // Global mute
        globalMuteRect = new Rect(x + width - TOGGLE_WIDTH - 12, cursorY + 2, TOGGLE_WIDTH, 16);
        drawSettingLabel(graphics, x + 12, cursorY, "devmod.notification.settings.global_mute");
        renderToggle(graphics, globalMuteRect, globalMute, mouseX, mouseY, NotificationUiTheme.RGB_ACCENT);
        cursorY += ROW_HEIGHT;

        // Master volume slider
        masterSliderRect = new Rect(x + width - 130, cursorY + 4, 110, SLIDER_HEIGHT);
        drawSettingLabel(graphics, x + 12, cursorY, "devmod.notification.settings.master_volume");
        renderSlider(graphics, masterSliderRect, masterVolume, mouseX, mouseY,
                NotificationUiTheme.RGB_ACCENT_ALT);
        cursorY += ROW_HEIGHT;

        // Minimum priority
        minPriorityRect = new Rect(x + width - 130, cursorY + 2, 110, 18);
        drawSettingLabel(graphics, x + 12, cursorY, "devmod.notification.settings.min_priority");
        renderPrioritySelector(graphics, minPriorityRect, minPriority, mouseX, mouseY);
        cursorY += ROW_HEIGHT;

        // Prefer chat
        preferChatRect = new Rect(x + width - TOGGLE_WIDTH - 12, cursorY + 2, TOGGLE_WIDTH, 16);
        drawSettingLabel(graphics, x + 12, cursorY, "devmod.notification.settings.prefer_chat");
        renderToggle(graphics, preferChatRect, preferChat, mouseX, mouseY, NotificationUiTheme.RGB_ACCENT);
    }

    private void drawSettingLabel(GuiGraphics graphics, int x, int y, String key) {
        Font font = Objects.requireNonNull(this.font);
        graphics.drawString(font, Objects.requireNonNull(Component.translatable(Objects.requireNonNull(key))), x, y + 6,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, DesignTokens.Alpha.A100), false);
    }

    private void renderCategoryPanel(GuiGraphics graphics, int x, int y, int width, int height,
                                     int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(this.font);
        int top = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_INNER_TOP, 208);
        int bottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_INNER_BOTTOM, 208);
        graphics.fillGradient(x, y, x + width, y + height, top, bottom);

        graphics.fill(x, y, x + width, y + 2,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT_ALT, DesignTokens.Alpha.A40));

        int headerY = y + 12;
        graphics.drawString(font, Objects.requireNonNull(Component.translatable("devmod.notification.settings.categories")),
                x + 10, headerY, NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, DesignTokens.Alpha.A100), true);

        int labelY = headerY + 16;
        int overlayX = x + width - TOGGLE_WIDTH * 2 - 80;
        int soundX = overlayX + TOGGLE_WIDTH + 10;
        int volumeX = soundX + TOGGLE_WIDTH + 10;

        graphics.drawString(font, Objects.requireNonNull(Component.translatable("devmod.notification.settings.overlay")),
                overlayX, labelY, NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, DesignTokens.Alpha.A80), false);
        graphics.drawString(font, Objects.requireNonNull(Component.translatable("devmod.notification.settings.sound")),
                soundX, labelY, NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, DesignTokens.Alpha.A80), false);
        graphics.drawString(font, Objects.requireNonNull(Component.translatable("devmod.notification.settings.volume")),
                volumeX, labelY, NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, DesignTokens.Alpha.A80), false);

        int listTop = labelY + 12;
        int listHeight = height - (listTop - y) - 12;
        listRect = new Rect(x + 8, listTop, width - 16, listHeight);

        int contentHeight = categoryRows.size() * (CATEGORY_ROW_HEIGHT + 6);
        maxScroll = Math.max(0, contentHeight - listRect.h());
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int rowY = listTop - scrollOffset;
        for (CategoryRow row : categoryRows) {
            if (rowY + CATEGORY_ROW_HEIGHT < listTop || rowY > listTop + listHeight) {
                rowY += CATEGORY_ROW_HEIGHT + 6;
                continue;
            }
            row.render(graphics, listRect.x(), rowY, listRect.w(), mouseX, mouseY);
            rowY += CATEGORY_ROW_HEIGHT + 6;
        }
    }

    private void renderToggle(GuiGraphics graphics, Rect rect, boolean enabled,
                               int mouseX, int mouseY, int accent) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int base = enabled ? accent : NotificationUiTheme.RGB_SURFACE_BOTTOM;
        int trackTop = NotificationUiTheme.withAlpha(
            hovered ? NotificationUiTheme.mix(base, NotificationUiTheme.RGB_WHITE, 0.1f) : base,
            DesignTokens.Alpha.A80);
        int trackBottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_BOTTOM, DesignTokens.Alpha.A80);

        graphics.fillGradient(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), trackTop, trackBottom);
        int knob = enabled ? rect.x() + rect.w() - rect.h() + 2 : rect.x() + 2;
        graphics.fill(knob, rect.y() + 2, knob + rect.h() - 4, rect.y() + rect.h() - 2,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, DesignTokens.Alpha.A100));
    }

    private void renderSlider(GuiGraphics graphics, Rect rect, float value,
                               int mouseX, int mouseY, int accent) {
        int trackColor = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_BOTTOM, DesignTokens.Alpha.A67);
        graphics.fill(rect.x(), rect.y() + rect.h() / 2 - 1, rect.x() + rect.w(), rect.y() + rect.h() / 2 + 1, trackColor);

        int filled = (int) (rect.w() * value);
        graphics.fill(rect.x(), rect.y() + rect.h() / 2 - 1, rect.x() + filled, rect.y() + rect.h() / 2 + 1,
                NotificationUiTheme.withAlpha(accent, DesignTokens.Alpha.A80));

        int handleX = rect.x() + Math.max(0, Math.min(rect.w() - 6, filled - 3));
        graphics.fill(handleX, rect.y(), handleX + 6, rect.y() + rect.h(),
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, DesignTokens.Alpha.A100));

        if (rect.contains(mouseX, mouseY)) {
            String label = Math.round(value * 100) + "%";
            graphics.drawString(Objects.requireNonNull(font), label, rect.x() + rect.w() + 6, rect.y() - 2,
                    NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_MUTED, DesignTokens.Alpha.A100), false);
        }
    }

    private void renderPrioritySelector(GuiGraphics graphics, Rect rect, NotificationPriority priority,
                                        int mouseX, int mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int base = NotificationUiTheme.RGB_SURFACE_TOP;
        int top = NotificationUiTheme.withAlpha(
            hovered ? NotificationUiTheme.mix(base, NotificationUiTheme.RGB_WHITE, 0.1f) : base,
            DesignTokens.Alpha.A80);
        int bottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_BOTTOM, DesignTokens.Alpha.A80);
        graphics.fillGradient(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), top, bottom);

        String label = priority.name().toLowerCase(Locale.ROOT);
        int textColor = NotificationUiTheme.getPriorityColor(priority);
        graphics.drawString(Objects.requireNonNull(font), label, rect.x() + 6, rect.y() + 5,
                NotificationUiTheme.withAlpha(textColor, DesignTokens.Alpha.A100), false);
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

        if (resetRect.contains(mouseX, mouseY)) {
            resetToDefaults();
            syncPreferences();
            return true;
        }

        if (globalMuteRect.contains(mouseX, mouseY)) {
            globalMute = !globalMute;
            prefs.setGlobalMute(globalMute);
            syncPreferences();
            return true;
        }

        if (preferChatRect.contains(mouseX, mouseY)) {
            preferChat = !preferChat;
            prefs.setPreferChatOverOverlay(preferChat);
            syncPreferences();
            return true;
        }

        if (minPriorityRect.contains(mouseX, mouseY)) {
            minPriority = NotificationPriority.values()[(minPriority.ordinal() + 1) % NotificationPriority.values().length];
            prefs.setMinOverlayPriority(minPriority);
            syncPreferences();
            return true;
        }

        if (masterSliderRect.contains(mouseX, mouseY)) {
            masterVolume = valueFromSlider(masterSliderRect, mouseX);
            prefs.setMasterVolume(masterVolume);
            draggingMaster = true;
            pendingSync = true;
            return true;
        }

        if (listRect.contains(mouseX, mouseY)) {
            int rowY = listRect.y() - scrollOffset;
            for (CategoryRow row : categoryRows) {
                if (row.handleClick(mouseX, mouseY, listRect.x(), rowY, listRect.w())) {
                    return true;
                }
                rowY += CATEGORY_ROW_HEIGHT + 6;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        if (draggingMaster) {
            masterVolume = valueFromSlider(masterSliderRect, mouseX);
            prefs.setMasterVolume(masterVolume);
            pendingSync = true;
            return true;
        }

        if (draggingCategory != null) {
            for (CategoryRow row : categoryRows) {
                if (row.category == draggingCategory) {
                    row.setVolumeFromDrag(mouseX);
                    pendingSync = true;
                    return true;
                }
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingMaster = false;
            draggingCategory = null;
            if (pendingSync) {
                syncPreferences();
                pendingSync = false;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
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

    private float valueFromSlider(Rect rect, double mouseX) {
        double value = (mouseX - rect.x()) / rect.w();
        return (float) Math.max(0, Math.min(1, value));
    }

    private void resetToDefaults() {
        prefs.reset();
        loadPreferences();
        initCategoryRows();
    }

    private void syncPreferences() {
        PacketDistributor.sendToServer(Objects.requireNonNull(prefs.toUpdatePayload()));
    }

    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private boolean isMouseOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }
    }

    private final class CategoryRow {
        private final NotificationCategory category;
        private boolean overlayEnabled;
        private boolean soundEnabled;
        private float volume;
        private Rect overlayRect = new Rect(0, 0, 0, 0);
        private Rect soundRect = new Rect(0, 0, 0, 0);
        private Rect volumeRect = new Rect(0, 0, 0, 0);

        private CategoryRow(NotificationCategory category, boolean overlayEnabled, boolean soundEnabled, float volume) {
            this.category = category;
            this.overlayEnabled = overlayEnabled;
            this.soundEnabled = soundEnabled;
            this.volume = volume;
        }

        void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
            Font font = Objects.requireNonNull(NotificationSettingsScreen.this.font);
            boolean hovered = isMouseOver(mouseX, mouseY, x, y, width, CATEGORY_ROW_HEIGHT);

            int baseTop = hovered ? NotificationUiTheme.RGB_SURFACE_HOVER_TOP : NotificationUiTheme.RGB_SURFACE_TOP;
            int baseBottom = hovered ? NotificationUiTheme.RGB_SURFACE_HOVER_BOTTOM : NotificationUiTheme.RGB_SURFACE_BOTTOM;
            graphics.fillGradient(x, y, x + width, y + CATEGORY_ROW_HEIGHT,
                    NotificationUiTheme.withAlpha(baseTop, 216),
                    NotificationUiTheme.withAlpha(baseBottom, 216));

            int accent = NotificationUiTheme.getCategoryColor(category);
            graphics.fill(x, y + 6, x + 4, y + CATEGORY_ROW_HEIGHT - 6,
                    NotificationUiTheme.withAlpha(accent, DesignTokens.Alpha.A80));

            int labelX = x + 10;
            String label = Objects.requireNonNull(Component.translatable(Objects.requireNonNull(category.getTranslationKey())).getString());
            int labelMax = width - 200;
            if (font.width(label) > labelMax) {
                label = Objects.requireNonNull(font.plainSubstrByWidth(label, labelMax - 6)) + "...";
            }
            graphics.drawString(font, label, labelX, y + 10,
                    NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, DesignTokens.Alpha.A100), false);

            int overlayX = x + width - TOGGLE_WIDTH * 2 - 80;
            overlayRect = new Rect(overlayX, y + 7, TOGGLE_WIDTH, 16);
            renderToggle(graphics, overlayRect, overlayEnabled, mouseX, mouseY, accent);

            int soundX = overlayX + TOGGLE_WIDTH + 10;
            soundRect = new Rect(soundX, y + 7, TOGGLE_WIDTH, 16);
            renderToggle(graphics, soundRect, soundEnabled, mouseX, mouseY, accent);

            int volumeX = soundX + TOGGLE_WIDTH + 10;
            volumeRect = new Rect(volumeX, y + 10, 70, SLIDER_HEIGHT);
            renderSlider(graphics, volumeRect, volume, mouseX, mouseY, accent);
        }

        boolean handleClick(double mouseX, double mouseY, int x, int y, int width) {
            if (!isMouseOver(mouseX, mouseY, x, y, width, CATEGORY_ROW_HEIGHT)) {
                return false;
            }

            if (overlayRect.contains(mouseX, mouseY)) {
                overlayEnabled = !overlayEnabled;
                prefs.setCategoryOverlayEnabled(category, overlayEnabled);
                syncPreferences();
                return true;
            }

            if (soundRect.contains(mouseX, mouseY)) {
                soundEnabled = !soundEnabled;
                prefs.setCategorySoundEnabled(category, soundEnabled);
                syncPreferences();
                return true;
            }

            if (volumeRect.contains(mouseX, mouseY)) {
                setVolumeFromDrag(mouseX);
                draggingCategory = category;
                pendingSync = true;
                return true;
            }

            return false;
        }

        void setVolumeFromDrag(double mouseX) {
            volume = valueFromSlider(volumeRect, mouseX);
            prefs.setCategorySoundVolume(category, volume);
        }
    }
}
