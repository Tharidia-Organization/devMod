package com.devmod.client.notification;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.client.notification.ui.NotificationBadgeOverlay;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;
import com.devmod.notification.network.UnifiedNotificationPayload;

/**
 * Central manager for all client-side notifications.
 * Handles queuing, display timing, and rendering of unified notifications.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class ClientNotificationManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientNotificationManager.class);

    public static final ClientNotificationManager INSTANCE = new ClientNotificationManager();

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "unified_notification");

    // Configuration
    private static final int MAX_ACTIVE_TOASTS = 5;
    private static final int MAX_QUEUE_SIZE = 50;
    private static final int MAX_HISTORY_SIZE = 200;
    private static final int TOAST_WIDTH = 280;
    private static final int TOAST_HEIGHT = 48;
    private static final int TOAST_MARGIN = 8;
    private static final int BANNER_HEIGHT = 56;

    // Animation timing
    private static final int SLIDE_IN_MS = 250;
    private static final int FADE_OUT_MS = 300;

    // Toast queue and active toasts
    private final Queue<QueuedNotification> toastQueue = new ArrayDeque<>();
    private final List<DisplayedToast> activeToasts = new ArrayList<>();

    // Banner (high-priority, one at a time)
    @Nullable
    private DisplayedBanner activeBanner;
    private final Queue<QueuedNotification> bannerQueue = new ArrayDeque<>();

    // Client preferences (loaded from config/server)
    private final ClientNotificationPreferences preferences = ClientNotificationPreferences.INSTANCE;

    // Notification history and read state
    private final List<Notification> history = new ArrayList<>();
    private final Set<UUID> readIds = new HashSet<>();

    private ClientNotificationManager() {}

    /**
     * Register the GUI layer for rendering.
     */
    @SubscribeEvent
    public static void registerGuiLayer(RegisterGuiLayersEvent event) {
        event.registerAbove(Objects.requireNonNull(VanillaGuiLayers.CHAT), Objects.requireNonNull(LAYER_ID), (graphics, deltaTracker) -> {
            INSTANCE.tick();
            INSTANCE.render(graphics, deltaTracker);
        });
    }

    /**
     * Handle incoming notification from network.
     */
    public void handleNotification(UnifiedNotificationPayload payload) {
        Notification notification = payload.toNotification();
        handleNotification(notification);
    }

    /**
     * Handle a notification object.
     */
    public void handleNotification(Notification notification) {
        addToHistory(notification);
        updateBadgeCounts(true);

        // Check if category is muted
        if (preferences.isCategoryMuted(notification.category())) {
            LOGGER.debug("Notification muted by category: {}", notification.category());
            return;
        }

        // Check priority threshold
        if (!notification.priority().isAtLeast(preferences.getMinOverlayPriority())) {
            LOGGER.debug("Notification below priority threshold: {}", notification.priority());
            return;
        }

        // Play sound if not muted
        playNotificationSound(notification);

        // Queue for display
        QueuedNotification queued = new QueuedNotification(notification, System.currentTimeMillis());

        if (notification.shouldUseBanner()) {
            queueBanner(queued);
        } else {
            queueToast(queued);
        }

        if (shouldAutoInvokeAction(notification)) {
            NotificationActionResolver.invoke(notification, ActionOrigin.EVENT);
        }
    }

    private void queueToast(QueuedNotification notification) {
        if (toastQueue.size() >= MAX_QUEUE_SIZE) {
            toastQueue.poll(); // Remove oldest
        }
        toastQueue.offer(notification);
    }

    private void queueBanner(QueuedNotification notification) {
        if (bannerQueue.size() >= 10) {
            bannerQueue.poll();
        }
        bannerQueue.offer(notification);
    }

    private void playNotificationSound(Notification notification) {
        NotificationSoundManager.INSTANCE.play(
                notification.soundId(),
                notification.category(),
                notification.priority()
        );
    }

    /**
     * Update notification states each frame.
     */
    public void tick() {
        long now = System.currentTimeMillis();

        // Update banner
        if (activeBanner != null) {
            activeBanner.tick();
            if (activeBanner.isExpired()) {
                activeBanner = null;
            }
        }

        // Promote from banner queue
        if (activeBanner == null && !bannerQueue.isEmpty()) {
            QueuedNotification next = bannerQueue.poll();
            if (next != null) {
                activeBanner = new DisplayedBanner(next.notification(), now);
            }
        }

        // Update active toasts
        activeToasts.removeIf(toast -> {
            toast.tick();
            return toast.isExpired();
        });

        // Promote from toast queue
        while (activeToasts.size() < MAX_ACTIVE_TOASTS && !toastQueue.isEmpty()) {
            QueuedNotification next = toastQueue.poll();
            if (next != null) {
                activeToasts.add(new DisplayedToast(next.notification(), now, activeToasts.size()));
            }
        }

        // Update positions for stacking
        for (int i = 0; i < activeToasts.size(); i++) {
            activeToasts.get(i).updateTargetIndex(i);
        }
    }

    /**
     * Render all active notifications.
     */
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        Font font = mc.font;

        // Render banner at top-center
        if (activeBanner != null) {
            renderBanner(graphics, font, screenWidth, activeBanner);
        }

        // Render toasts at bottom-right
        for (DisplayedToast toast : activeToasts) {
            renderToast(graphics, font, screenWidth, screenHeight, toast);
        }
    }

    private void renderBanner(GuiGraphics graphics, Font font, int screenWidth, DisplayedBanner banner) {
        float alpha = banner.getAlpha();
        float slideProgress = banner.getSlideProgress();

        int bannerWidth = 320;
        int x = (screenWidth - bannerWidth) / 2;
        int baseY = 10;
        int y = (int) (baseY - (1 - slideProgress) * 40);

        int bgAlpha = (int) (210 * alpha);
        int bgTop = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_INNER_TOP, bgAlpha);
        int bgBottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_PANEL_INNER_BOTTOM, bgAlpha);

        // Background
        graphics.fillGradient(x, y, x + bannerWidth, y + BANNER_HEIGHT, bgTop, bgBottom);

        // Border based on priority
        int borderColor = getBorderColor(banner.notification.priority(), alpha);
        graphics.fill(x, y, x + bannerWidth, y + 2, borderColor);
        graphics.fill(x, y + BANNER_HEIGHT - 2, x + bannerWidth, y + BANNER_HEIGHT, borderColor);

        // Title
        int textAlpha = (int) (255 * alpha);
        Component title = buildComponent(banner.notification.titleKey(), banner.notification);
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(title), x + 10, y + 8,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, textAlpha), true);

        // Message if present
        String msgKey = banner.notification.messageKey();
        if (msgKey != null) {
            Component message = buildComponent(msgKey, banner.notification);
            graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(message), x + 10, y + 24,
                    NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, textAlpha), true);
        }
    }

    private void renderToast(GuiGraphics graphics, Font font, int screenWidth, int screenHeight,
                             DisplayedToast toast) {
        float alpha = toast.getAlpha();
        float slideProgress = toast.getSlideProgress();

        int x = screenWidth - TOAST_WIDTH - TOAST_MARGIN;
        int baseY = screenHeight - 60 - (toast.targetIndex * (TOAST_HEIGHT + 4));
        x = (int) (x + (1 - slideProgress) * (TOAST_WIDTH + 20));

        int bgAlpha = (int) (200 * alpha);
        int bgTop = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_TOP, bgAlpha);
        int bgBottom = NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_BOTTOM, bgAlpha);

        // Background
        graphics.fillGradient(x, baseY, x + TOAST_WIDTH, baseY + TOAST_HEIGHT, bgTop, bgBottom);

        // Left accent bar based on category
        int accentColor = getCategoryColor(toast.notification.category(), alpha);
        graphics.fill(x, baseY, x + 3, baseY + TOAST_HEIGHT, accentColor);

        // Title
        int textAlpha = (int) (255 * alpha);
        Component title = buildComponent(toast.notification.titleKey(), toast.notification);
        graphics.drawString(Objects.requireNonNull(font), Objects.requireNonNull(title), x + 10, baseY + 8,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, textAlpha), true);

        // Message if present
        String msgKey = toast.notification.messageKey();
        if (msgKey != null) {
            Component message = buildComponent(msgKey, toast.notification);
            int maxWidth = TOAST_WIDTH - 20;
            String text = message.getString();
            if (font.width(Objects.requireNonNull(text)) > maxWidth) {
                text = font.plainSubstrByWidth(Objects.requireNonNull(text), maxWidth - 10) + "...";
            }
            graphics.drawString(font, text, x + 10, baseY + 24,
                    NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, textAlpha), true);
        }
    }

    private int getBorderColor(NotificationPriority priority, float alpha) {
        int a = (int) (255 * alpha);
        return NotificationUiTheme.withAlpha(NotificationUiTheme.getPriorityColor(priority), a);
    }

    private int getCategoryColor(NotificationCategory category, float alpha) {
        int a = (int) (255 * alpha);
        return NotificationUiTheme.withAlpha(NotificationUiTheme.getCategoryColor(category), a);
    }

    private Component buildComponent(String key, Notification notification) {
        Object[] args = notification.params().values().toArray();
        return Component.translatable(Objects.requireNonNull(key), args);
    }

    private boolean shouldAutoInvokeAction(Notification notification) {
        return ActionIds.UI_PARTY_INVITE_POPUP_OPEN.equals(notification.actionId());
    }

    /**
     * Update client preferences.
     */
    public void setPreferences(ClientNotificationPreferences preferences) {
        if (preferences == null) {
            return;
        }

        this.preferences.setGlobalMute(preferences.isGlobalMute());
        this.preferences.setMinOverlayPriority(preferences.getMinOverlayPriority());
        this.preferences.setPreferChatOverOverlay(preferences.prefersChatOverOverlay());
        this.preferences.setMasterVolume(preferences.getMasterVolume());

        for (NotificationCategory category : NotificationCategory.values()) {
            ClientNotificationPreferences.CategoryPreference pref = preferences.getCategoryPreference(category);
            if (pref != null) {
                this.preferences.setCategoryOverlayEnabled(category, pref.overlayEnabled());
                this.preferences.setCategorySoundEnabled(category, pref.soundEnabled());
                this.preferences.setCategorySoundVolume(category, pref.soundVolume());
            }
        }
    }

    /**
     * Clear all notifications.
     */
    public void clear() {
        toastQueue.clear();
        bannerQueue.clear();
        activeToasts.clear();
        activeBanner = null;
        history.clear();
        readIds.clear();
        updateBadgeCounts(false);
    }

    public List<Notification> getHistory() {
        return List.copyOf(history);
    }

    public boolean isRead(UUID notificationId) {
        return readIds.contains(notificationId);
    }

    public void markRead(UUID notificationId) {
        if (readIds.add(notificationId)) {
            updateBadgeCounts(false);
        }
    }

    public void markAllRead() {
        for (Notification notification : history) {
            readIds.add(notification.id());
        }
        updateBadgeCounts(false);
    }

    public int getUnreadCount() {
        int unread = 0;
        for (Notification notification : history) {
            if (!readIds.contains(notification.id())) {
                unread++;
            }
        }
        return unread;
    }

    public int getUrgentUnreadCount() {
        int urgent = 0;
        for (Notification notification : history) {
            if (!readIds.contains(notification.id())
                    && notification.priority().isAtLeast(NotificationPriority.URGENT)) {
                urgent++;
            }
        }
        return urgent;
    }

    private void addToHistory(Notification notification) {
        history.add(0, notification);
        trimHistory();
    }

    private void trimHistory() {
        while (history.size() > MAX_HISTORY_SIZE) {
            Notification removed = history.remove(history.size() - 1);
            readIds.remove(removed.id());
        }
    }

    private void updateBadgeCounts(boolean animate) {
        int unread = getUnreadCount();
        int urgent = getUrgentUnreadCount();
        NotificationBadgeOverlay.updateCount(unread, urgent);
        if (animate) {
            NotificationBadgeOverlay.notifyNew();
        }
    }

    // ===== Internal classes =====

    private record QueuedNotification(Notification notification, long queuedAt) {}

    private static class DisplayedToast {
        final Notification notification;
        final long startTime;
        int targetIndex;

        DisplayedToast(Notification notification, long startTime, int index) {
            this.notification = notification;
            this.startTime = startTime;
            this.targetIndex = index;
        }

        void tick() {
            // Smooth position interpolation would go here
        }

        void updateTargetIndex(int index) {
            this.targetIndex = index;
        }

        float getSlideProgress() {
            long elapsed = System.currentTimeMillis() - startTime;
            return Math.min(1.0f, elapsed / (float) SLIDE_IN_MS);
        }

        float getAlpha() {
            long elapsed = System.currentTimeMillis() - startTime;
            long duration = notification.displayDurationMs();

            if (elapsed < SLIDE_IN_MS) {
                return elapsed / (float) SLIDE_IN_MS;
            } else if (elapsed > duration - FADE_OUT_MS) {
                return Math.max(0, (duration - elapsed) / (float) FADE_OUT_MS);
            }
            return 1.0f;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > notification.displayDurationMs();
        }
    }

    private static class DisplayedBanner {
        final Notification notification;
        final long startTime;

        DisplayedBanner(Notification notification, long startTime) {
            this.notification = notification;
            this.startTime = startTime;
        }

        void tick() {
            // Animation updates would go here
        }

        float getSlideProgress() {
            long elapsed = System.currentTimeMillis() - startTime;
            return Math.min(1.0f, elapsed / (float) SLIDE_IN_MS);
        }

        float getAlpha() {
            long elapsed = System.currentTimeMillis() - startTime;
            long duration = notification.displayDurationMs();

            if (elapsed < SLIDE_IN_MS) {
                return elapsed / (float) SLIDE_IN_MS;
            } else if (elapsed > duration - FADE_OUT_MS) {
                return Math.max(0, (duration - elapsed) / (float) FADE_OUT_MS);
            }
            return 1.0f;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > notification.displayDurationMs();
        }
    }
}
