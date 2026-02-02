package com.devmod.client.notification.ui;

import java.util.Objects;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.notification.NotificationUiTheme;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.notification.NotificationPriority;
/**
 * HUD overlay showing notification badge with unread count.
 *
 * <p>Features:
 * <ul>
 *   <li>Animated badge with pulse effect on new notifications</li>
 *   <li>Smooth count update animations</li>
 *   <li>Hover tooltip with quick stats</li>
 *   <li>Click to open Notification Center</li>
 *   <li>Position: Top-right corner below hotbar icons</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public class NotificationBadgeOverlay {

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath("devmod", "notification_badge");

    // ============================================================================
    // DESIGN TOKENS
    // ============================================================================

    // Layout
    private static final int BADGE_SIZE = 28;
    private static final int BADGE_MARGIN = 8;

    // Colors
    private static final int RGB_BG_NORMAL = NotificationUiTheme.RGB_SURFACE_TOP;
    private static final int RGB_BG_HOVER = NotificationUiTheme.RGB_SURFACE_HOVER_TOP;
    private static final int RGB_ACCENT = NotificationUiTheme.RGB_ACCENT;
    private static final int RGB_BADGE = NotificationUiTheme.RGB_ACCENT_ALT;
    private static final int RGB_BADGE_URGENT = NotificationUiTheme.getPriorityColor(NotificationPriority.URGENT);

    // Animation
    private static final long PULSE_DURATION = 600;
    private static final long BOUNCE_DURATION = 400;
    private static final float HOVER_SCALE = 1.1f;

    // ============================================================================
    // STATE
    // ============================================================================

    private static boolean visible = true;
    private static int displayedCount = 0;
    private static int targetCount = 0;
    private static long lastPulseTime = 0;
    private static long lastBounceTime = 0;
    private static float hoverProgress = 0;
    private static int urgentCount = 0;

    // Position (calculated on render)
    private static int badgeX, badgeY;

    // ============================================================================
    // REGISTRATION
    // ============================================================================

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
                Objects.requireNonNull(LAYER_ID),
                NotificationBadgeOverlay::render
        );
    }

    // ============================================================================
    // PUBLIC API
    // ============================================================================

    // Update the badge count. Triggers animations if count increases.
    public static void updateCount(int newCount, int newUrgentCount) {
        if (newCount > targetCount) {
            // New notifications - trigger animations
            lastPulseTime = System.currentTimeMillis();
            lastBounceTime = System.currentTimeMillis();
        }
        targetCount = newCount;
        urgentCount = newUrgentCount;
    }

    // Notify of a new notification (triggers attention animation).
    public static void notifyNew() {
        lastPulseTime = System.currentTimeMillis();
        lastBounceTime = System.currentTimeMillis();
    }

    // Set badge visibility.
    public static void setVisible(boolean isVisible) {
        visible = isVisible;
    }

    // Check if a point is within the badge area.
    public static boolean isHovered(double mouseX, double mouseY) {
        int sBadgeSize = UIScaleManager.scale(BADGE_SIZE);
        return mouseX >= badgeX && mouseX < badgeX + sBadgeSize &&
                mouseY >= badgeY && mouseY < badgeY + sBadgeSize;
    }

    // Handle click on the badge.
    public static boolean handleClick(double mouseX, double mouseY) {
        if (!visible) return false;
        if (isHovered(mouseX, mouseY)) {
            openNotificationCenter();
            return true;
        }
        return false;
    }

    private static void openNotificationCenter() {
        NotificationCenterScreen.open("NOTIFICATIONS", null);
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_1 || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        double mouseX = mc.mouseHandler.xpos() * screenWidth / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * screenHeight / mc.getWindow().getScreenHeight();

        if (handleClick(mouseX, mouseY)) {
            event.setCanceled(true);
        }
    }

    // ============================================================================
    // RENDERING
    // ============================================================================

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!visible) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.screen != null) return;

        Font font = mc.font;
        if (font == null) return;

        // Update UIScaleManager for responsive scaling
        UIScaleManager.update();

        long now = System.currentTimeMillis();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Scaled layout values
        int sBadgeSize = UIScaleManager.scale(BADGE_SIZE);
        int sBadgeMargin = UIScaleManager.scale(BADGE_MARGIN);

        // Update display count with smooth animation
        if (displayedCount < targetCount) {
            displayedCount = Math.min(displayedCount + 1, targetCount);
        } else if (displayedCount > targetCount) {
            displayedCount = Math.max(displayedCount - 1, targetCount);
        }

        // Calculate position (top-right, below potential other HUD elements)
        badgeX = screenWidth - sBadgeSize - sBadgeMargin;
        badgeY = sBadgeMargin + Math.min(UIScaleManager.scale(60), screenHeight / 6); // Below potential boss bar area

        // Check hover
        double mouseX = mc.mouseHandler.xpos() * screenWidth / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * screenHeight / mc.getWindow().getScreenHeight();
        boolean isHovered = isHovered(mouseX, mouseY);

        // Update hover animation
        float targetHover = isHovered ? 1f : 0f;
        hoverProgress += (targetHover - hoverProgress) * 0.15f;

        // Calculate animations
        float pulseProgress = getPulseProgress(now);
        float bounceProgress = getBounceProgress(now);

        // Apply scale from hover and bounce
        float scale = 1f + (HOVER_SCALE - 1f) * hoverProgress;
        scale += bounceProgress * 0.15f;

        int scaledSize = (int) (sBadgeSize * scale);
        int offsetX = (scaledSize - sBadgeSize) / 2;
        int offsetY = (scaledSize - sBadgeSize) / 2;

        int renderX = badgeX - offsetX;
        int renderY = badgeY - offsetY;

        // Render badge background
        int bgAlpha = isHovered ? DesignTokens.Alpha.A93 : DesignTokens.Alpha.A80;
        int bgRgb = isHovered ? RGB_BG_HOVER : RGB_BG_NORMAL;
        int sCornerRadius = UIScaleManager.scale(6);
        renderRoundedRect(graphics, renderX, renderY, scaledSize, scaledSize, sCornerRadius,
                NotificationUiTheme.withAlpha(bgRgb, bgAlpha));
        graphics.fill(renderX + 4, renderY + 2, renderX + scaledSize - 4, renderY + 3,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_WHITE, (bgAlpha * DesignTokens.Alpha.A13) / 255));

        // Pulse glow effect
        if (pulseProgress > 0) {
            int glowAlpha = (int) (pulseProgress * DesignTokens.Alpha.A40);
            int glowSize = (int) (pulseProgress * UIScaleManager.scale(4));
            int glowColor = NotificationUiTheme.withAlpha(RGB_ACCENT, glowAlpha);
            renderRoundedRect(graphics, renderX - glowSize, renderY - glowSize,
                    scaledSize + glowSize * 2, scaledSize + glowSize * 2, UIScaleManager.scale(8), glowColor);
        }

        // Border
        int borderAlpha = isHovered ? DesignTokens.Alpha.A53 : DesignTokens.Alpha.A27;
        int borderColor = NotificationUiTheme.withAlpha(RGB_ACCENT, borderAlpha);
        renderRoundedRectBorder(graphics, renderX, renderY, scaledSize, scaledSize, sCornerRadius, borderColor);

        // Bell icon
        int iconCenterX = renderX + scaledSize / 2;
        int iconCenterY = renderY + scaledSize / 2;
        // Use simple characters for broad font support.
        String icon = displayedCount > 0 ? "!" : "o";
        int iconWidth = UIScaleManager.getScaledStringWidth(font, icon);
        int iconColor = displayedCount > 0 ? NotificationUiTheme.RGB_TEXT_PRIMARY : NotificationUiTheme.RGB_TEXT_MUTED;
        UIScaleManager.drawScaledString(graphics, font, icon, iconCenterX - iconWidth / 2, iconCenterY - 4,
                NotificationUiTheme.withAlpha(iconColor, DesignTokens.Alpha.A100));

        // Notification count badge (if > 0)
        if (displayedCount > 0) {
            int countBadgeX = renderX + scaledSize - UIScaleManager.scale(8);
            int countBadgeY = renderY - UIScaleManager.scale(4);

            String countText = Objects.requireNonNull(displayedCount > 99 ? "99+" : String.valueOf(displayedCount));
            int countWidth = UIScaleManager.getScaledStringWidth(font, countText);
            int badgeWidth = Math.max(UIScaleManager.scale(16), countWidth + UIScaleManager.scale(8));
            int badgeHeight = UIScaleManager.scale(14);

            // Badge background color based on urgency
            int badgeBgColor = urgentCount > 0 ? RGB_BADGE_URGENT : RGB_BADGE;

            // Pulse the badge if urgent
            if (urgentCount > 0 && pulseProgress > 0) {
                badgeBgColor = NotificationUiTheme.mix(badgeBgColor, NotificationUiTheme.RGB_WHITE, pulseProgress * 0.3f);
            }

            // Badge background
            renderRoundedRect(graphics, countBadgeX - badgeWidth / 2, countBadgeY,
                    badgeWidth, badgeHeight, UIScaleManager.scale(7), NotificationUiTheme.withAlpha(badgeBgColor, DesignTokens.Alpha.A100));

            // Badge text
            UIScaleManager.drawScaledString(graphics, font, countText,
                    countBadgeX - countWidth / 2, countBadgeY + UIScaleManager.scale(3),
                    NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, DesignTokens.Alpha.A100));
        }

        // Hover tooltip
        if (isHovered && hoverProgress > 0.8f) {
            renderTooltip(graphics, font, renderX, renderY + scaledSize + UIScaleManager.scale(4));
        }
    }

    private static void renderTooltip(GuiGraphics graphics, Font font, int x, int y) {
        String title = Objects.requireNonNull(net.minecraft.network.chat.Component
                .translatable("devmod.notification.badge.tooltip.title")
                .getString());
        String subtitle;
        if (displayedCount == 0) {
            subtitle = Objects.requireNonNull(net.minecraft.network.chat.Component
                    .translatable("devmod.notification.badge.tooltip.empty")
                    .getString());
        } else if (urgentCount > 0) {
            subtitle = Objects.requireNonNull(net.minecraft.network.chat.Component
                    .translatable("devmod.notification.badge.tooltip.unread_urgent",
                            displayedCount, urgentCount)
                    .getString());
        } else {
            subtitle = Objects.requireNonNull(net.minecraft.network.chat.Component
                    .translatable("devmod.notification.badge.tooltip.unread", displayedCount)
                    .getString());
        }

        int lineHeight = UIScaleManager.getScaledLineHeight(font, 10);
        int sPadding = UIScaleManager.scale(8);
        int sLineGap = UIScaleManager.scale(2);
        int tooltipWidth = Math.max(UIScaleManager.getScaledStringWidth(font, title), UIScaleManager.getScaledStringWidth(font, subtitle))
            + sPadding * 2;
        int tooltipHeight = sPadding * 2 + lineHeight * 2 + sLineGap;

        // Ensure tooltip stays on screen
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int sMargin = UIScaleManager.scale(8);
        if (x + tooltipWidth > screenWidth - sMargin) {
            x = screenWidth - tooltipWidth - sMargin;
        }

        int tooltipAlpha = DesignTokens.Alpha.A88;
        graphics.fillGradient(x, y, x + tooltipWidth, y + tooltipHeight,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_TOP, tooltipAlpha),
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_SURFACE_BOTTOM, tooltipAlpha));

        graphics.fill(x, y, x + tooltipWidth, y + 1,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT, DesignTokens.Alpha.A40));
        graphics.fill(x, y + tooltipHeight - 1, x + tooltipWidth, y + tooltipHeight,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_ACCENT_SOFT, DesignTokens.Alpha.A27));

        int textX = x + sPadding;
        int textY = y + sPadding;
        UIScaleManager.drawScaledString(graphics, font, title, textX, textY,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_PRIMARY, DesignTokens.Alpha.A100));
        UIScaleManager.drawScaledString(graphics, font, subtitle, textX, textY + lineHeight + sLineGap,
                NotificationUiTheme.withAlpha(NotificationUiTheme.RGB_TEXT_SECONDARY, DesignTokens.Alpha.A100));
    }

    private static void renderRoundedRect(GuiGraphics graphics, int x, int y, int width, int height,
                                           int radius, int color) {
        // Simple approximation of rounded rect with regular fills
        graphics.fill(x + radius, y, x + width - radius, y + height, color);
        graphics.fill(x, y + radius, x + width, y + height - radius, color);

        // Corners (rough approximation)
        graphics.fill(x + 1, y + 1, x + radius, y + radius, color);
        graphics.fill(x + width - radius, y + 1, x + width - 1, y + radius, color);
        graphics.fill(x + 1, y + height - radius, x + radius, y + height - 1, color);
        graphics.fill(x + width - radius, y + height - radius, x + width - 1, y + height - 1, color);
    }

    private static void renderRoundedRectBorder(GuiGraphics graphics, int x, int y, int width, int height,
                                                 int radius, int color) {
        // Top and bottom edges
        graphics.fill(x + radius, y, x + width - radius, y + 1, color);
        graphics.fill(x + radius, y + height - 1, x + width - radius, y + height, color);

        // Left and right edges
        graphics.fill(x, y + radius, x + 1, y + height - radius, color);
        graphics.fill(x + width - 1, y + radius, x + width, y + height - radius, color);
    }

    // ============================================================================
    // ANIMATION HELPERS
    // ============================================================================

    private static float getPulseProgress(long now) {
        if (lastPulseTime == 0) return 0;
        long elapsed = now - lastPulseTime;
        if (elapsed >= PULSE_DURATION) return 0;

        float t = (float) elapsed / PULSE_DURATION;
        // Fade out with slight pulse
        return (float) (Math.sin(t * Math.PI * 2) * 0.5 + 0.5) * (1f - t);
    }

    private static float getBounceProgress(long now) {
        if (lastBounceTime == 0) return 0;
        long elapsed = now - lastBounceTime;
        if (elapsed >= BOUNCE_DURATION) return 0;

        float t = (float) elapsed / BOUNCE_DURATION;
        // Elastic bounce
        return (float) (Math.sin(t * Math.PI * 3) * Math.pow(1 - t, 2));
    }

}
