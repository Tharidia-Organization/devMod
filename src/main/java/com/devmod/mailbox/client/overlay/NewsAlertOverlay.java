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
import com.devmod.mailbox.client.ClientNewsCache;
import com.devmod.mailbox.news.NewsArticle;
import com.devmod.mailbox.news.NewsCategory;

/**
 * Overlay for displaying news alerts as toasts.
 * Shows a slide-in notification when new news articles are published.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class NewsAlertOverlay {
    private static final Logger LOGGER = LoggerFactory.getLogger(NewsAlertOverlay.class);

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "news_alert");

    // Timing
    private static final long DISPLAY_DURATION_MS = 6000;
    private static final long SLIDE_IN_MS = 350;
    private static final long FADE_OUT_MS = 500;

    // Layout - positioned in top left (different from mail which is top right)
    private static final int BOX_WIDTH = 300;
    private static final int BOX_HEIGHT = 60;
    private static final int MARGIN_LEFT = 10;
    private static final int MARGIN_TOP = 10;

    // Colors
    private static final int BG_COLOR = 0xEE1A1A2E;
    private static final int BORDER_COLOR_PATCH = 0xFF4CAF50;
    private static final int BORDER_COLOR_EVENT = 0xFFE91E63;
    private static final int BORDER_COLOR_ANNOUNCEMENT = 0xFF2196F3;
    private static final int BORDER_COLOR_MAINTENANCE = 0xFFFF9800;
    private static final int TITLE_COLOR = 0xFFFFD700;
    private static final int CATEGORY_COLOR = 0xFF81C784;
    private static final int CONTENT_COLOR = 0xFFCCCCCC;

    // State
    private static volatile boolean active = false;
    private static volatile long startTime = 0;
    @Nullable
    private static volatile NewsArticle currentArticle = null;

    private NewsAlertOverlay() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.CHAT),
            Objects.requireNonNull(LAYER_ID),
            NewsAlertOverlay::render
        );
        LOGGER.debug("[NewsAlertOverlay] Registered GUI layer");
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!active || currentArticle == null) {
            // Check for pending news notifications
            NewsArticle pending = ClientNewsCache.getDisplayableNews();
            if (pending != null && !Objects.equals(currentArticle, pending)) {
                showAlert(pending);
            }
            if (!active) return;
        }

        NewsArticle article = currentArticle;
        if (article == null) {
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

        // Calculate animation
        float slideProgress = calculateSlideProgress(elapsed);
        float alpha = calculateAlpha(elapsed);

        // Position - slides in from left
        int targetX = MARGIN_LEFT;
        int currentX = (int) Mth.lerp(slideProgress, -BOX_WIDTH - 10, targetX);
        int y = MARGIN_TOP;

        if (alpha < 0.01f) return;

        int boxAlpha = (int) (alpha * 240);
        int borderAlpha = (int) (alpha * 255);
        int textAlpha = (int) (alpha * 255);

        // Get border color based on category
        int baseBorderColor = getBorderColor(article.category());

        // Draw border
        int borderColor = (borderAlpha << 24) | (baseBorderColor & 0x00FFFFFF);
        graphics.fill(currentX - 2, y - 2, currentX + BOX_WIDTH + 2, y + BOX_HEIGHT + 2, borderColor);

        // Draw background
        int bgColor = (boxAlpha << 24) | (BG_COLOR & 0x00FFFFFF);
        graphics.fill(currentX, y, currentX + BOX_WIDTH, y + BOX_HEIGHT, bgColor);

        // News icon
        int iconX = currentX + 10;
        int iconY = y + 8;
        int iconColor = (textAlpha << 24) | (TITLE_COLOR & 0x00FFFFFF);
        graphics.drawString(font, "\uD83D\uDCF0", iconX, iconY, iconColor, false); // Newspaper emoji

        // Category badge
        int categoryX = currentX + 28;
        int categoryY = y + 8;
        String categoryText = Objects.requireNonNull(getCategoryName(article.category()), "category name");
        int catBgColor = (borderAlpha << 24) | (baseBorderColor & 0x00FFFFFF);
        int catWidth = font.width(categoryText) + 8;
        graphics.fill(categoryX, categoryY - 2, categoryX + catWidth, categoryY + 10, catBgColor);
        int categoryColor = (textAlpha << 24) | 0xFFFFFF;
        graphics.drawString(font, categoryText, categoryX + 4, categoryY, categoryColor, false);

        // Title
        int titleX = categoryX + catWidth + 8;
        int titleY = y + 8;
        String title = truncate(article.title(), 28);
        int titleColor = (textAlpha << 24) | (TITLE_COLOR & 0x00FFFFFF);
        graphics.drawString(font, title, titleX, titleY, titleColor, false);

        // Content preview
        int contentX = currentX + 28;
        int contentY = y + 24;
        String content = truncate(article.content(), 45);
        int contentColor = (textAlpha << 24) | (CONTENT_COLOR & 0x00FFFFFF);
        graphics.drawString(font, content, contentX, contentY, contentColor, false);

        // Author
        int authorY = y + 40;
        String authorText = "by " + (article.authorName() != null ? article.authorName() : "Admin");
        int authorColor = (textAlpha << 24) | (CATEGORY_COLOR & 0x00FFFFFF);
        graphics.drawString(font, authorText, contentX, authorY, authorColor, false);

        // Priority indicator if high
        if (article.priority() >= 2) {
            int priorityX = currentX + BOX_WIDTH - 25;
            int priorityY = y + 8;
            int priorityColor = (textAlpha << 24) | (0xFF5555 & 0x00FFFFFF);
            String priorityIcon = article.priority() >= 3 ? "!!!" : "!";
            graphics.drawString(font, priorityIcon, priorityX, priorityY, priorityColor, false);
        }

        // Pending count badge
        int pendingCount = ClientNewsCache.getUnreadCount();
        if (pendingCount > 1) {
            int badgeX = currentX + BOX_WIDTH - 30;
            int badgeY = y + BOX_HEIGHT - 16;
            String badgeText = "+" + (pendingCount - 1) + " more";
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

    private static int getBorderColor(NewsCategory category) {
        if (category == null) return BORDER_COLOR_ANNOUNCEMENT;
        return switch (category) {
            case PATCH -> BORDER_COLOR_PATCH;
            case EVENT -> BORDER_COLOR_EVENT;
            case ANNOUNCEMENT -> BORDER_COLOR_ANNOUNCEMENT;
            case MAINTENANCE -> BORDER_COLOR_MAINTENANCE;
            case DEV_BLOG -> 0xFF9C27B0; // Purple for dev blog
            case COMMUNITY -> 0xFF00BCD4; // Cyan for community
        };
    }

    private static String getCategoryName(NewsCategory category) {
        if (category == null) return "NEWS";
        return switch (category) {
            case PATCH -> "PATCH";
            case EVENT -> "EVENT";
            case ANNOUNCEMENT -> "NEWS";
            case MAINTENANCE -> "MAINT";
            case DEV_BLOG -> "DEV";
            case COMMUNITY -> "COMMUNITY";
        };
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    // ========== PUBLIC API ==========

    /**
     * Show an alert for a news article.
     */
    public static void showAlert(NewsArticle article) {
        if (article == null) return;

        currentArticle = article;
        active = true;
        startTime = System.currentTimeMillis();

        // Play notification sound
        Minecraft mc = Minecraft.getInstance();
        var soundManager = mc.getSoundManager();
        var soundEvent = SoundEvents.NOTE_BLOCK_CHIME.value();
        if (soundManager != null && soundEvent != null) {
            soundManager.play(Objects.requireNonNull(
                SimpleSoundInstance.forUI(soundEvent, 1.2f, 0.7f)
            ));
        }

        LOGGER.debug("[NewsAlertOverlay] Showing alert for article: {}", article.title());
    }

    /**
     * Dismiss the current alert.
     */
    public static void dismiss() {
        if (!active) return;

        active = false;
        startTime = 0;

        // Mark as viewed in cache
        if (currentArticle != null) {
            ClientNewsCache.markAsViewed(currentArticle.id());
        }

        // Check for next pending
        NewsArticle next = ClientNewsCache.getDisplayableNews();
        if (next != null) {
            showAlert(next);
        } else {
            currentArticle = null;
        }

        LOGGER.debug("[NewsAlertOverlay] Alert dismissed");
    }

    /**
     * Check if an alert is currently showing.
     */
    public static boolean isActive() {
        return active;
    }

    /**
     * Handle ESC key to dismiss.
     * @return true if alert was dismissed
     */
    public static boolean handleEscape() {
        if (active) {
            dismiss();
            return true;
        }
        return false;
    }

    /**
     * Handle click to open news screen.
     * @return true if click was handled
     */
    public static boolean handleClick(double mouseX, double mouseY) {
        if (!active) return false;

        int x = MARGIN_LEFT;
        int y = MARGIN_TOP;

        if (mouseX >= x && mouseX <= x + BOX_WIDTH &&
            mouseY >= y && mouseY <= y + BOX_HEIGHT) {

            dismiss();
            // Open news screen
            com.devmod.mailbox.client.screen.NewsScreen.open();
            return true;
        }

        return false;
    }
}
