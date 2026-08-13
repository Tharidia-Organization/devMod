package com.devmod.client.notification.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nonnull;

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
import com.devmod.client.notification.NotificationSoundManager;
import com.devmod.client.notification.NotificationUiTheme;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;
/**
 * Premium unified toast overlay system with stacking, animations, and category styling.
 *
 * <p>Features:
 * <ul>
 *   <li>Smooth slide-in/slide-out animations with elastic easing</li>
 *   <li>Stacking with automatic repositioning</li>
 *   <li>Category-specific color accents and icons</li>
 *   <li>Priority-based glow effects</li>
 *   <li>Progress bar for duration</li>
 *   <li>Hover to pause dismiss timer</li>
 *   <li>Click to open Notification Center</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class UnifiedToastOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnifiedToastOverlay.class);

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath("devmod", "unified_toast");

    // ============================================================================
    // DESIGN TOKENS
    // ============================================================================

    // Layout
    private static final int TOAST_MIN_WIDTH = 260;
    private static final int TOAST_MAX_WIDTH = 380;
    private static final int TOAST_HEIGHT = 64;
    private static final int TOAST_SPACING = 8;
    private static final int SCREEN_MARGIN = 16;
    private static final int MAX_VISIBLE_TOASTS = 4;
    private static final int PADDING = 12;
    private static final int ICON_SIZE = 24;

    // Animation timing (ms)
    private static final long SLIDE_IN_DURATION = 400;
    private static final long SLIDE_OUT_DURATION = 300;
    private static final long DEFAULT_DISPLAY_DURATION = 4000;

    // Colors
    private static final int RGB_TOAST_TOP = NotificationUiTheme.RGB_SURFACE_TOP;
    private static final int RGB_TOAST_BOTTOM = NotificationUiTheme.RGB_SURFACE_BOTTOM;
    private static final int RGB_TOAST_HOVER_TOP = NotificationUiTheme.RGB_SURFACE_HOVER_TOP;
    private static final int RGB_TOAST_HOVER_BOTTOM = NotificationUiTheme.RGB_SURFACE_HOVER_BOTTOM;

    // ============================================================================
    // STATE
    // ============================================================================

    private static final List<ToastEntry> activeToasts = new ArrayList<>();
    private static final List<ToastEntry> pendingToasts = new ArrayList<>();
    private static int hoveredToastIndex = -1;

    // ============================================================================
    // REGISTRATION
    // ============================================================================

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                Objects.requireNonNull(VanillaGuiLayers.BOSS_OVERLAY),
                Objects.requireNonNull(LAYER_ID),
                UnifiedToastOverlay::render
        );
    }

    // ============================================================================
    // PUBLIC API
    // ============================================================================

    /**
     * Show a notification toast.
     */
    public static void show(Notification notification) {
        if (notification == null) return;

        ToastEntry entry = new ToastEntry(notification);

        // If we have room, show immediately
        if (activeToasts.size() < MAX_VISIBLE_TOASTS) {
            entry.startTime = System.currentTimeMillis();
            entry.state = ToastState.ENTERING;
            activeToasts.add(entry);
            playSound(notification);
        } else {
            // Queue for later
            pendingToasts.add(entry);
        }

        LOGGER.debug("[Toast] Showing notification: {} (queued: {})",
                notification.category(), pendingToasts.size());
    }

    /**
     * Dismiss all active toasts.
     */
    public static void dismissAll() {
        for (ToastEntry entry : activeToasts) {
            if (entry.state != ToastState.EXITING) {
                entry.state = ToastState.EXITING;
                entry.exitStartTime = System.currentTimeMillis();
            }
        }
        pendingToasts.clear();
    }

    /**
     * Get count of active + pending toasts.
     */
    public static int getActiveCount() {
        return activeToasts.size() + pendingToasts.size();
    }

    // ============================================================================
    // RENDERING
    // ============================================================================

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.screen != null) return; // Don't show during screens

        Font font = mc.font;
        if (font == null) return;

        // Update UIScaleManager for responsive scaling
        UIScaleManager.update();

        long now = System.currentTimeMillis();
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Scaled layout values
        int sScreenMargin = UIScaleManager.scale(SCREEN_MARGIN);
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 10);
        int sPadding = UIScaleManager.scale(PADDING);
        int sIconSize = UIScaleManager.scale(ICON_SIZE);
        int sTextGap = UIScaleManager.scale(2);
        int textBlockHeight = lineHeight * 3 + sTextGap * 2;
        int contentHeight = Math.max(textBlockHeight, sIconSize);
        int sToastHeight = Math.max(UIScaleManager.scale(TOAST_HEIGHT), sPadding * 2 + contentHeight);
        int sToastSpacing = UIScaleManager.scale(TOAST_SPACING);
        int toastWidth = getToastWidth(screenWidth);

        // Update hover state
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        hoveredToastIndex = -1;

        // Process and render toasts
        int yOffset = sScreenMargin;

        for (int i = 0; i < activeToasts.size(); i++) {
            ToastEntry entry = activeToasts.get(i);
            updateToastState(entry, now);

            // Calculate position with animation
            float slideProgress = getSlideProgress(entry, now);
            float exitProgress = entry.state == ToastState.EXITING ? getExitProgress(entry, now) : 0;

            int toastX = screenWidth - toastWidth - sScreenMargin;
            int toastY = yOffset;

            // Slide in from right
            float slideOffset = (1f - slideProgress) * (toastWidth + sScreenMargin);
            // Slide out to right
            slideOffset += exitProgress * (toastWidth + sScreenMargin);

            int renderX = toastX + (int) slideOffset;
            int renderY = toastY;

            // Check hover
            if (mouseX >= renderX && mouseX < renderX + toastWidth &&
                    mouseY >= renderY && mouseY < renderY + sToastHeight) {
                hoveredToastIndex = i;
                // Pause dismiss timer while hovering; keep the original pause start so the
                // resume branch credits the whole hover, not a single frame
                if (entry.state == ToastState.VISIBLE && entry.pausedAt == 0) {
                    entry.pausedAt = now;
                }
            } else if (entry.pausedAt > 0 && entry.state == ToastState.VISIBLE) {
                // Resume timer - extend by paused duration
                long pauseDuration = now - entry.pausedAt;
                entry.startTime += pauseDuration;
                entry.pausedAt = 0;
            }

            // Calculate opacity
            float opacity = 1f;
            if (slideProgress < 1f) {
                opacity = slideProgress;
            } else if (exitProgress > 0) {
                opacity = 1f - exitProgress;
            }

            boolean hovered = hoveredToastIndex == i;
            renderToast(graphics, font, entry, renderX, renderY, toastWidth, sToastHeight, lineHeight, opacity, hovered, now);

            yOffset += sToastHeight + sToastSpacing;
        }

        // Remove finished toasts
        Iterator<ToastEntry> iter = activeToasts.iterator();
        while (iter.hasNext()) {
            ToastEntry entry = iter.next();
            if (entry.state == ToastState.FINISHED) {
                iter.remove();
            }
        }

        // Promote pending toasts
        while (activeToasts.size() < MAX_VISIBLE_TOASTS && !pendingToasts.isEmpty()) {
            ToastEntry next = pendingToasts.remove(0);
            next.startTime = now;
            next.state = ToastState.ENTERING;
            activeToasts.add(next);
            playSound(next.notification);
        }
    }

    private static int getToastWidth(int screenWidth) {
        int sScreenMargin = UIScaleManager.scale(SCREEN_MARGIN);
        int sMinWidth = UIScaleManager.scale(TOAST_MIN_WIDTH);
        int sMaxWidth = UIScaleManager.scale(TOAST_MAX_WIDTH);
        int maxAllowed = screenWidth - sScreenMargin * 2;
        int minWidth = Math.min(sMinWidth, maxAllowed);
        int width = Math.min(sMaxWidth, maxAllowed);
        return Math.max(minWidth, width);
    }

    private static void renderToast(GuiGraphics graphics, @Nonnull Font font, ToastEntry entry,
                                     int x, int y, int width, int height, int lineHeight,
                                     float opacity, boolean hovered, long now) {
        Notification notification = entry.notification;
        int accentColor = entry.accentColor;
        int alpha = (int) (opacity * 255);

        // Scaled layout values
        int sPadding = UIScaleManager.scale(PADDING);
        int sIconSize = UIScaleManager.scale(ICON_SIZE);
        int sTextGap = UIScaleManager.scale(2);

        // Background with hover effect
        int bgAlpha = (alpha * DesignTokens.Alpha.A88) / 255;
        int top = NotificationUiTheme.withAlpha(hovered ? RGB_TOAST_HOVER_TOP : RGB_TOAST_TOP, bgAlpha);
        int bottom = NotificationUiTheme.withAlpha(hovered ? RGB_TOAST_HOVER_BOTTOM : RGB_TOAST_BOTTOM, bgAlpha);
        graphics.fillGradient(x, y, x + width, y + height, top, bottom);
        graphics.fill(x, y, x + width, y + 1,
            NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_WHITE, (alpha * 24) / 255));

        // Accent glow on left edge
        int glowAlpha = (alpha * DesignTokens.Alpha.A80) / 255;
        graphics.fill(x, y, x + 3, y + height, NotificationUiTheme.withAlpha(accentColor, glowAlpha));

        // Priority glow (for HIGH+ priority)
        if (notification.priority().ordinal() >= NotificationPriority.HIGH.ordinal()) {
            int glowSize = UIScaleManager.scale(8);
            int priorityColor = NotificationUiTheme.getPriorityGlowColor(notification.priority());
            for (int i = 0; i < glowSize; i++) {
                int glowA = (alpha * (glowSize - i) * 8) / (255 * glowSize);
                graphics.fill(x - i, y - i, x + width + i, y,
                        NotificationUiTheme.withAlpha(priorityColor, glowA));
                graphics.fill(x - i, y + height, x + width + i, y + height + i,
                        NotificationUiTheme.withAlpha(priorityColor, glowA));
            }
        }

        // Subtle border
        int borderAlpha = (alpha * DesignTokens.Alpha.A20) / 255;
        graphics.fill(x, y, x + width, y + 1, NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_WHITE, borderAlpha));
        graphics.fill(x, y + height - 1, x + width, y + height,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_WHITE, borderAlpha));
        graphics.fill(x + width - 1, y, x + width, y + height,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_WHITE, borderAlpha));

        // Icon area
        int iconX = x + sPadding;
        int iconY = y + (height - sIconSize) / 2;

        // Icon background circle
        int iconBgAlpha = (alpha * DesignTokens.Alpha.A27) / 255;
        renderCircle(graphics, iconX + sIconSize / 2, iconY + sIconSize / 2, sIconSize / 2 + 2,
                NotificationUiTheme.withAlpha(accentColor, iconBgAlpha));

        // Content area
        int contentX = iconX + sIconSize + sPadding;
        int contentWidth = x + width - sPadding - contentX;

        entry.ensureLayout(font, contentWidth);

        // Icon
        String icon = entry.icon;
        int iconTextAlpha = (alpha * DesignTokens.Alpha.A100) / 255;
        int iconWidth = entry.cachedIconWidth;
        UIScaleManager.drawScaledString(graphics, font, icon, iconX + (sIconSize - iconWidth) / 2, iconY + UIScaleManager.scale(8),
                NotificationUiTheme.withAlpha(accentColor, iconTextAlpha));

        // Title
        int titleY = y + sPadding;
        String title = entry.cachedTitle;
        int titleAlpha = (alpha * DesignTokens.Alpha.A100) / 255;
        UIScaleManager.drawScaledString(graphics, font, title, contentX, titleY,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, titleAlpha), true);

        // Message
        int msgY = titleY + lineHeight + sTextGap;
        String message = entry.cachedMessage;
        int msgAlpha = (alpha * 187) / 255;
        UIScaleManager.drawScaledString(graphics, font, message, contentX, msgY,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, msgAlpha));

        // Category label (bottom right)
        String category = entry.categoryLabel;
        int catWidth = entry.cachedCategoryWidth;
        int catX = x + width - sPadding - catWidth;
        int catY = y + height - sPadding - lineHeight;
        int catAlpha = (alpha * DesignTokens.Alpha.A53) / 255;
        UIScaleManager.drawScaledString(graphics, font, category, catX, catY,
                NotificationUiTheme.withAlpha(accentColor, catAlpha));

        // Progress bar (time remaining)
        if (entry.state == ToastState.VISIBLE && entry.pausedAt == 0) {
            long displayDuration = notification.displayDurationMs() > 0
                    ? notification.displayDurationMs()
                    : DEFAULT_DISPLAY_DURATION;
            long elapsed = now - entry.startTime - SLIDE_IN_DURATION;
            float progress = 1f - Math.min(1f, (float) elapsed / displayDuration);

            int progressY = y + height - 2;
            int progressWidth = (int) (width * progress);

            // Progress background
            int progressBgAlpha = (alpha * DesignTokens.Alpha.A13) / 255;
            graphics.fill(x, progressY, x + width, y + height,
                    NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_WHITE, progressBgAlpha));

            // Progress fill
            int progressAlpha = (alpha * DesignTokens.Alpha.A53) / 255;
            graphics.fill(x, progressY, x + progressWidth, y + height,
                    NotificationUiTheme.withAlpha(accentColor, progressAlpha));
        }

        // Hover: show close button
        if (hovered) {
            int closeX = x + width - UIScaleManager.scale(24);
            int closeY = y + UIScaleManager.scale(8);
            int closeAlpha = (alpha * DesignTokens.Alpha.A67) / 255;

            // Close button background
            graphics.fill(closeX - 2, closeY - 2, closeX + UIScaleManager.scale(14), closeY + UIScaleManager.scale(14),
                    NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_BOTTOM, closeAlpha));
            UIScaleManager.drawScaledString(graphics, font, "x", closeX + 2, closeY + 2,
                    NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, closeAlpha));
        }
    }

    private static void renderCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        // Approximate circle with rectangles
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(centerX - dx, centerY + dy, centerX + dx, centerY + dy + 1, color);
        }
    }

    // ============================================================================
    // STATE MANAGEMENT
    // ============================================================================

    private static void updateToastState(ToastEntry entry, long now) {
        long elapsed = now - entry.startTime;

        switch (entry.state) {
            case ENTERING -> {
                if (elapsed >= SLIDE_IN_DURATION) {
                    entry.state = ToastState.VISIBLE;
                }
            }
            case VISIBLE -> {
                long displayDuration = entry.notification.displayDurationMs() > 0
                        ? entry.notification.displayDurationMs()
                        : DEFAULT_DISPLAY_DURATION;

                if (elapsed >= SLIDE_IN_DURATION + displayDuration && entry.pausedAt == 0) {
                    entry.state = ToastState.EXITING;
                    entry.exitStartTime = now;
                }
            }
            case EXITING -> {
                if (now - entry.exitStartTime >= SLIDE_OUT_DURATION) {
                    entry.state = ToastState.FINISHED;
                }
            }
            case FINISHED -> {
                // Will be removed in main loop
            }
        }
    }

    private static float getSlideProgress(ToastEntry entry, long now) {
        long elapsed = now - entry.startTime;
        if (elapsed >= SLIDE_IN_DURATION) return 1f;

        float t = (float) elapsed / SLIDE_IN_DURATION;
        // Elastic ease out
        return (float) (1 - Math.pow(2, -10 * t) * Math.cos((t * 10 - 0.75) * (2 * Math.PI) / 3));
    }

    private static float getExitProgress(ToastEntry entry, long now) {
        if (entry.exitStartTime == 0) return 0f;
        long elapsed = now - entry.exitStartTime;
        if (elapsed >= SLIDE_OUT_DURATION) return 1f;

        float t = (float) elapsed / SLIDE_OUT_DURATION;
        // Ease in cubic
        return t * t * t;
    }

    // ============================================================================
    // SOUND
    // ============================================================================

    private static void playSound(Notification notification) {
        String soundId = notification.soundId();
        if (soundId == null || soundId.isBlank()) {
            soundId = "default";
        }
        NotificationSoundManager.INSTANCE.play(soundId, notification.category(), notification.priority());
    }

    // ============================================================================
    // HELPERS
    // ============================================================================


    @Nonnull
    private static String resolveTitleText(Notification notification) {
        String titleKey = notification.titleKey();
        if (titleKey != null && !titleKey.isBlank()) {
            Object[] args = notification.params().values().toArray(new Object[0]);
            return Objects.requireNonNull(Component.translatable(titleKey, java.util.Objects.requireNonNull(args)).getString());
        }

        var params = notification.params();
        if (params != null) {
            if (params.containsKey("badge")) return "Badge Unlocked: " + params.get("badge");
            if (params.containsKey("record")) return "New Record: " + params.get("record");
            if (params.containsKey("tier")) return "Tier " + params.get("tier") + " Unlocked!";
            if (params.containsKey("wave")) return "Wave " + params.get("wave");
            if (params.containsKey("amount") && notification.category() == NotificationCategory.TOKEN) {
                return "+" + params.get("amount") + " Tokens";
            }
        }

        return switch (notification.category()) {
            case ACHIEVEMENT -> "Achievement Unlocked!";
            case RECORD -> "New Personal Record!";
            case SEASON -> "Season Progress";
            case TOKEN -> "Tokens Earned";
            case REWARD -> "Reward Received";
            case PARTY -> "Party Update";
            case QUEST -> "Quest Event";
            case COMBAT -> "Combat";
            case RESONANCE -> "Resonance Chain";
            case ADMIN -> "Admin Notice";
            case SYSTEM -> "System Message";
            case MAILBOX -> "New Mail";
            case NEWS -> "News";
        };
    }

    @Nonnull
    private static String resolveMessageText(Notification notification) {
        String messageKey = notification.messageKey();
        if (messageKey != null && !messageKey.isBlank()) {
            Object[] args = notification.params().values().toArray(new Object[0]);
            return Objects.requireNonNull(Component.translatable(messageKey, java.util.Objects.requireNonNull(args)).getString());
        }

        var params = notification.params();
        if (params != null && !params.isEmpty()) {
            // Build a readable message from params
            List<String> parts = new ArrayList<>();
            for (var entry : params.entrySet()) {
                String key = entry.getKey();
                if (key.equals("badge") || key.equals("record") || key.equals("tier") || key.equals("wave")) {
                    continue; // Already in title
                }
                parts.add(formatParamKey(key) + ": " + entry.getValue());
            }
            if (!parts.isEmpty()) {
                return Objects.requireNonNull(String.join(" | ", parts));
            }
        }
        return "Click to view details";
    }

    private static String formatParamKey(String key) {
        if (key.isEmpty()) return key;
        return key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1).replace("_", " ");
    }

    // ============================================================================
    // INNER CLASSES
    // ============================================================================

    private enum ToastState {
        ENTERING,
        VISIBLE,
        EXITING,
        FINISHED
    }

    private static String truncateToWidth(Font font, @Nonnull String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (UIScaleManager.getScaledStringWidth(font, text) <= width) {
            return text;
        }
        String result = font.plainSubstrByWidth(text, Math.max(0, width - 10));
        String trimmed = result != null ? result : "";
        return trimmed + "...";
    }

    private static class ToastEntry {
        final Notification notification;
        final int accentColor;
        @Nonnull final String icon;
        @Nonnull final String baseTitle;
        @Nonnull final String baseMessage;
        @Nonnull final String categoryLabel;
        int cachedContentWidth = -1;
        String cachedTitle = "";
        String cachedMessage = "";
        int cachedCategoryWidth = 0;
        int cachedIconWidth = 0;
        long startTime;
        long exitStartTime;
        long pausedAt;
        ToastState state = ToastState.ENTERING;

        ToastEntry(Notification notification) {
            this.notification = notification;
            this.accentColor = NotificationUiTheme.getCategoryColor(notification.category());
            this.icon = Objects.requireNonNull(NotificationUiTheme.getCategoryIcon(notification.category()));
            this.baseTitle = resolveTitleText(notification);
            this.baseMessage = resolveMessageText(notification);
            this.categoryLabel = notification.category().name().substring(0, 1) +
                notification.category().name().substring(1).toLowerCase(Locale.ROOT);
        }

        void ensureLayout(@Nonnull Font font, int contentWidth) {
            if (contentWidth == cachedContentWidth) {
                return;
            }
            cachedContentWidth = contentWidth;
            cachedTitle = truncateToWidth(font, baseTitle, contentWidth);
            cachedMessage = truncateToWidth(font, baseMessage, contentWidth);
            cachedCategoryWidth = UIScaleManager.getScaledStringWidth(font, categoryLabel);
            cachedIconWidth = UIScaleManager.getScaledStringWidth(font, icon);
        }
    }
}
