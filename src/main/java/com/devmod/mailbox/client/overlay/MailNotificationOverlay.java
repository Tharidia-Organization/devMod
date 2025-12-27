package com.devmod.mailbox.client.overlay;

import java.util.Objects;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.mailbox.MessageType;
import com.devmod.mailbox.client.ClientMailboxCache;
import com.devmod.mailbox.network.payload.MailboxNotifyPayload;

/**
 * Overlay for displaying new mail notifications as toasts.
 * Shows a slide-in notification when new mail arrives.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class MailNotificationOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailNotificationOverlay.class);

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "mail_notification");

    // Timing
    private static final long DISPLAY_DURATION_MS = 5000;
    private static final long SLIDE_IN_MS = 300;
    private static final long FADE_OUT_MS = 400;

    // Layout - positioned in top right
    private static final int BOX_WIDTH = 280;
    private static final int BOX_HEIGHT = 50;
    private static final int MARGIN_RIGHT = 10;
    private static final int MARGIN_TOP = 10;

    // Colors
    private static final int BG_COLOR = 0xEE1A1A2E;
    private static final int BORDER_COLOR_PLAYER = 0xFF5C8AFF;
    private static final int BORDER_COLOR_SYSTEM = 0xFF888888;
    private static final int BORDER_COLOR_ADMIN = 0xFFFFD700;
    private static final int BORDER_COLOR_REWARD = 0xFF4CAF50;
    private static final int TITLE_COLOR = 0xFF81C784;
    private static final int SENDER_COLOR = 0xFF5C8AFF;
    private static final int SUBJECT_COLOR = 0xFFCCCCCC;

    // State
    private static volatile boolean active = false;
    private static volatile long startTime = 0;
    @Nullable
    private static volatile MailboxNotifyPayload currentNotification = null;

    private MailNotificationOverlay() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.CHAT),
            Objects.requireNonNull(LAYER_ID),
            MailNotificationOverlay::render
        );
        LOGGER.debug("[MailNotificationOverlay] Registered GUI layer");
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!active || currentNotification == null) {
            // Check for pending notifications
            MailboxNotifyPayload pending = ClientMailboxCache.getDisplayableNotification();
            if (pending != null && !Objects.equals(currentNotification, pending)) {
                showNotification(pending);
            }
            if (!active) return;
        }

        MailboxNotifyPayload notification = currentNotification;
        if (notification == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.screen != null) return; // Don't show when in a screen

        Font font = mc.font;
        if (font == null) return;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > DISPLAY_DURATION_MS) {
            dismiss();
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Calculate animation
        float slideProgress = calculateSlideProgress(elapsed);
        float alpha = calculateAlpha(elapsed);

        // Position - slides in from right
        int targetX = screenWidth - BOX_WIDTH - MARGIN_RIGHT;
        int currentX = (int) Mth.lerp(slideProgress, screenWidth + 10, targetX);
        int y = MARGIN_TOP;

        if (alpha < 0.01f) return;

        int boxAlpha = (int) (alpha * 240);
        int borderAlpha = (int) (alpha * 255);
        int textAlpha = (int) (alpha * 255);

        // Get border color based on message type
        int baseBorderColor = getBorderColor(notification.messageTypeOrdinal());

        // Draw border
        int borderColor = (borderAlpha << 24) | (baseBorderColor & 0x00FFFFFF);
        graphics.fill(currentX - 2, y - 2, currentX + BOX_WIDTH + 2, y + BOX_HEIGHT + 2, borderColor);

        // Draw background
        int bgColor = (boxAlpha << 24) | (BG_COLOR & 0x00FFFFFF);
        graphics.fill(currentX, y, currentX + BOX_WIDTH, y + BOX_HEIGHT, bgColor);

        // Mail icon
        int iconX = currentX + 10;
        int iconY = y + 8;
        int iconColor = (textAlpha << 24) | (TITLE_COLOR & 0x00FFFFFF);
        graphics.drawString(font, "✉", iconX, iconY, iconColor, false);

        // Title
        int contentX = currentX + 28;
        int contentY = y + 8;
        String title = "New Mail";
        int titleColor = (textAlpha << 24) | (TITLE_COLOR & 0x00FFFFFF);
        graphics.drawString(font, title, contentX, contentY, titleColor, false);

        // Sender
        contentY += 14;
        String sender = notification.senderName() != null
            ? notification.senderName()
            : getTypeName(notification.messageTypeOrdinal());
        int senderColor = (textAlpha << 24) | (SENDER_COLOR & 0x00FFFFFF);
        graphics.drawString(font, "From: " + truncate(sender, 24), contentX, contentY, senderColor, false);

        // Subject
        contentY += 12;
        String subject = truncate(notification.subject(), 32);
        int subjectColor = (textAlpha << 24) | (SUBJECT_COLOR & 0x00FFFFFF);
        graphics.drawString(font, subject, contentX, contentY, subjectColor, false);

        // Attachment indicator
        if (notification.hasAttachment()) {
            int attachX = currentX + BOX_WIDTH - 25;
            int attachY = y + 18;
            int attachColor = (textAlpha << 24) | (0xFFD700 & 0x00FFFFFF);
            graphics.drawString(font, "📎", attachX, attachY, attachColor, false);
        }

        // Unread count badge if more notifications pending
        int pendingCount = ClientMailboxCache.getPendingNotificationCount();
        if (pendingCount > 1) {
            int badgeX = currentX + BOX_WIDTH - 30;
            int badgeY = y + BOX_HEIGHT - 16;
            String badgeText = "+" + (pendingCount - 1);
            int badgeColor = (textAlpha << 24) | (0xFFAA00 & 0x00FFFFFF);
            graphics.drawString(font, badgeText, badgeX, badgeY, badgeColor, false);
        }
    }

    private static float calculateSlideProgress(long elapsed) {
        if (elapsed >= SLIDE_IN_MS) return 1f;
        float t = elapsed / (float) SLIDE_IN_MS;
        // Ease out cubic
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float calculateAlpha(long elapsed) {
        if (elapsed < SLIDE_IN_MS / 2) {
            float t = elapsed / (float) (SLIDE_IN_MS / 2);
            return t;
        } else if (elapsed > DISPLAY_DURATION_MS - FADE_OUT_MS) {
            float t = (DISPLAY_DURATION_MS - elapsed) / (float) FADE_OUT_MS;
            return t * t;
        }
        return 1f;
    }

    private static int getBorderColor(int typeOrdinal) {
        if (typeOrdinal < 0 || typeOrdinal >= MessageType.values().length) {
            return BORDER_COLOR_SYSTEM;
        }
        MessageType type = MessageType.values()[typeOrdinal];
        return switch (type) {
            case PLAYER -> BORDER_COLOR_PLAYER;
            case SYSTEM -> BORDER_COLOR_SYSTEM;
            case ADMIN -> BORDER_COLOR_ADMIN;
            case REWARD -> BORDER_COLOR_REWARD;
        };
    }

    private static String getTypeName(int typeOrdinal) {
        if (typeOrdinal < 0 || typeOrdinal >= MessageType.values().length) {
            return "System";
        }
        MessageType type = MessageType.values()[typeOrdinal];
        return switch (type) {
            case PLAYER -> "Player";
            case SYSTEM -> "System";
            case ADMIN -> "Admin";
            case REWARD -> "Rewards";
        };
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    // ========== PUBLIC API ==========

    /**
     * Show a notification for a new message.
     */
    public static void showNotification(MailboxNotifyPayload notification) {
        if (notification == null) return;

        currentNotification = notification;
        active = true;
        startTime = System.currentTimeMillis();

        // Play notification sound
        Minecraft mc = Minecraft.getInstance();
        var soundManager = mc.getSoundManager();
        var soundEvent = SoundEvents.NOTE_BLOCK_BELL.value();
        if (soundManager != null && soundEvent != null) {
            soundManager.play(Objects.requireNonNull(
                SimpleSoundInstance.forUI(soundEvent, 1.0f, 0.6f)
            ));
        }

        LOGGER.debug("[MailNotificationOverlay] Showing notification for message: {}", notification.messageId());
    }

    /**
     * Dismiss the current notification.
     */
    public static void dismiss() {
        if (!active) return;

        active = false;
        startTime = 0;

        // Dismiss from cache too
        ClientMailboxCache.dismissNotification();

        // Check for next pending
        MailboxNotifyPayload next = ClientMailboxCache.getDisplayableNotification();
        if (next != null) {
            showNotification(next);
        } else {
            currentNotification = null;
        }

        LOGGER.debug("[MailNotificationOverlay] Notification dismissed");
    }

    /**
     * Check if a notification is currently showing.
     */
    public static boolean isActive() {
        return active;
    }

    /**
     * Handle ESC key to dismiss.
     * @return true if notification was dismissed
     */
    public static boolean handleEscape() {
        if (active) {
            dismiss();
            return true;
        }
        return false;
    }

    /**
     * Handle click to open mailbox.
     * @return true if click was handled
     */
    public static boolean handleClick(double mouseX, double mouseY) {
        if (!active) return false;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        int targetX = screenWidth - BOX_WIDTH - MARGIN_RIGHT;
        int y = MARGIN_TOP;

        if (mouseX >= targetX && mouseX <= targetX + BOX_WIDTH &&
            mouseY >= y && mouseY <= y + BOX_HEIGHT) {

            dismiss();
            // Open mailbox screen
            com.devmod.mailbox.client.screen.MailboxScreen.open();
            return true;
        }

        return false;
    }
}
