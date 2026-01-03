package com.devmod.client.testing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.testing.TestCase;
import com.devmod.testing.TesterProfile;

public class QANotificationSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(QANotificationSystem.class);

    public static final QANotificationSystem INSTANCE = new QANotificationSystem();

    // Active notifications queue
    private final List<Notification> notifications = new ArrayList<>();
    private static final int MAX_VISIBLE = 3;
    private static final int NOTIFICATION_WIDTH = 200;
    private static final int NOTIFICATION_HEIGHT = 40;
    private static final int MARGIN = 10;
    private static final long DISPLAY_DURATION_MS = 4000;
    private static final long FADE_DURATION_MS = 500;

    public enum NotificationType {
        TEST_PASSED(TestingUiTheme.Notification.TEST_PASSED, "Test Passed"),
        TEST_FAILED(TestingUiTheme.Notification.TEST_FAILED, "Test Failed"),
        ACHIEVEMENT(TestingUiTheme.Notification.ACHIEVEMENT, "Achievement Unlocked"),
        LEVEL_UP(TestingUiTheme.Notification.LEVEL_UP, "Level Up!"),
        BADGE(TestingUiTheme.Notification.BADGE, "Badge Earned"),
        STREAK(TestingUiTheme.Notification.STREAK, "Streak Bonus"),
        XP_GAIN(TestingUiTheme.Notification.XP_GAIN, "+XP");

        private final int color;
        private final String prefix;

        NotificationType(int color, String prefix) {
            this.color = color;
            this.prefix = prefix;
        }

        public int getColor() { return color; }
        public String getPrefix() { return prefix; }
    }

    private static class Notification {
        final NotificationType type;
        final String title;
        final String subtitle;
        final long createdAt;

        Notification(NotificationType type, String title, String subtitle) {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > DISPLAY_DURATION_MS + FADE_DURATION_MS;
        }

        float getAlpha() {
            long age = System.currentTimeMillis() - createdAt;
            if (age < 200) {
                // Slide in
                return age / 200f;
            } else if (age > DISPLAY_DURATION_MS) {
                // Fade out
                float fadeProgress = (age - DISPLAY_DURATION_MS) / (float) FADE_DURATION_MS;
                return Math.max(0, 1 - fadeProgress);
            }
            return 1f;
        }

        float getSlideOffset() {
            long age = System.currentTimeMillis() - createdAt;
            if (age < 200) {
                // Slide in from right
                float progress = age / 200f;
                return (1 - progress) * (NOTIFICATION_WIDTH + MARGIN);
            }
            return 0f;
        }
    }

    private QANotificationSystem() {}

    /**
     * Show notification for test completion.
     */
    public void showTestCompleted(TestCase test) {
        addNotification(
            NotificationType.TEST_PASSED,
            test.getName(),
            "Auto-completed!"
        );
        playSound(NotificationType.TEST_PASSED);
    }

    /**
     * Show notification for test failure.
     */
    public void showTestFailed(TestCase test, String reason) {
        addNotification(
            NotificationType.TEST_FAILED,
            test.getName(),
            reason
        );
        playSound(NotificationType.TEST_FAILED);
    }

    /**
     * Show notification for achievement unlock.
     */
    public void showAchievement(TesterProfile.Achievement achievement) {
        addNotification(
            NotificationType.ACHIEVEMENT,
            achievement.getName(),
            "+" + achievement.getXpReward() + " XP"
        );
        playSound(NotificationType.ACHIEVEMENT);
    }

    /**
     * Show notification for level up.
     */
    public void showLevelUp(int newLevel, String title) {
        addNotification(
            NotificationType.LEVEL_UP,
            "Level " + newLevel,
            title
        );
        playSound(NotificationType.LEVEL_UP);
    }

    /**
     * Show notification for badge earned.
     */
    public void showBadge(TesterProfile.Badge badge) {
        addNotification(
            NotificationType.BADGE,
            badge.getName(),
            badge.getRequirement()
        );
        playSound(NotificationType.BADGE);
    }

    /**
     * Show notification for streak milestone.
     */
    public void showStreak(int days, float bonusMultiplier) {
        addNotification(
            NotificationType.STREAK,
            days + " Day Streak!",
            "+" + (int)(bonusMultiplier * 100) + "% XP Bonus"
        );
        playSound(NotificationType.STREAK);
    }

    /**
     * Show XP gain notification.
     */
    public void showXPGain(int amount, String reason) {
        addNotification(
            NotificationType.XP_GAIN,
            "+" + amount + " XP",
            reason
        );
        // No sound for XP (too frequent)
    }

    private void addNotification(NotificationType type, String title, String subtitle) {
        synchronized (notifications) {
            // Limit queue size
            while (notifications.size() >= MAX_VISIBLE * 2) {
                notifications.remove(0);
            }
            notifications.add(new Notification(type, title, subtitle));
        }
        LOGGER.debug("Notification: [{}] {} - {}", type.getPrefix(), title, subtitle);
    }

    private void playSound(NotificationType type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var level = mc.level;
        if (level == null) return;

        var player = Objects.requireNonNull(mc.player);
        var blockPos = Objects.requireNonNull(player.blockPosition());

        // Play appropriate sound
        var sound = switch (type) {
            case TEST_PASSED -> SoundEvents.PLAYER_LEVELUP;
            case TEST_FAILED -> SoundEvents.VILLAGER_NO;
            case ACHIEVEMENT -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case LEVEL_UP -> SoundEvents.PLAYER_LEVELUP;
            case BADGE -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case STREAK -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case XP_GAIN -> SoundEvents.EXPERIENCE_ORB_PICKUP;
        };

        level.playSound(
            player,
            blockPos,
            Objects.requireNonNull(sound),
            SoundSource.MASTER,
            0.5f,
            type == NotificationType.LEVEL_UP ? 1.2f : 1.0f
        );
    }

    /**
     * Render all active notifications.
     * Call from GUI overlay render event.
     */
    public void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        synchronized (notifications) {
            // Remove expired
            Iterator<Notification> iter = notifications.iterator();
            while (iter.hasNext()) {
                if (iter.next().isExpired()) {
                    iter.remove();
                }
            }

            // Render visible notifications (most recent at top)
            int y = MARGIN;
            int count = 0;
            for (int i = notifications.size() - 1; i >= 0 && count < MAX_VISIBLE; i--) {
                Notification notif = notifications.get(i);
                renderNotification(graphics, font, notif, screenWidth, y);
                y += NOTIFICATION_HEIGHT + 5;
                count++;
            }
        }
    }

    private void renderNotification(GuiGraphics graphics, Font font, Notification notif, int screenWidth, int y) {
        float alpha = notif.getAlpha();
        if (alpha <= 0) return;

        float slideOffset = notif.getSlideOffset();
        int x = (int) (screenWidth - NOTIFICATION_WIDTH - MARGIN + slideOffset);

        // Background with alpha
        int bgAlpha = (int) (200 * alpha);
        int bgColor = (bgAlpha << 24) | TestingUiTheme.Notification.BG_RGB;
        graphics.fill(x, y, x + NOTIFICATION_WIDTH, y + NOTIFICATION_HEIGHT, bgColor);

        // Left accent bar
        int accentAlpha = (int) (255 * alpha);
        int accentColor = (accentAlpha << 24) | (notif.type.getColor() & DesignTokens.Mask.RGB);
        graphics.fill(x, y, x + 3, y + NOTIFICATION_HEIGHT, accentColor);

        // Text with alpha
        int textAlpha = (int) (255 * alpha);
        int titleColor = (textAlpha << 24) | TestingUiTheme.Notification.TEXT_RGB;
        int subtitleColor = (textAlpha << 24) | TestingUiTheme.Notification.TEXT_MUTED_RGB;
        int typeColor = (textAlpha << 24) | (notif.type.getColor() & DesignTokens.Mask.RGB);

        Font fontNonNull = Objects.requireNonNull(font);

        // Type prefix
        graphics.drawString(fontNonNull, notif.type.getPrefix(), x + 8, y + 5, typeColor, false);

        // Title
        graphics.drawString(fontNonNull, notif.title, x + 8, y + 16, titleColor, false);

        // Subtitle
        if (notif.subtitle != null && !notif.subtitle.isEmpty()) {
            graphics.drawString(fontNonNull, notif.subtitle, x + 8, y + 27, subtitleColor, false);
        }
    }

    /**
     * Clear all notifications.
     */
    public void clear() {
        synchronized (notifications) {
            notifications.clear();
        }
    }

    /**
     * Check if there are active notifications.
     */
    public boolean hasNotifications() {
        synchronized (notifications) {
            return !notifications.isEmpty();
        }
    }
}
