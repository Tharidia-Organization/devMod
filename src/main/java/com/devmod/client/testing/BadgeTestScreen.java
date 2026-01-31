package com.devmod.client.testing;

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

import com.devmod.client.notification.ClientNotificationManager;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.GamificationManager.BadgeRarity;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;

@OnlyIn(Dist.CLIENT)
public class BadgeTestScreen extends Screen {

    private static final int BUTTON_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_SPACING = 30;

    private final List<PositionedButton> buttons = new ArrayList<>();

    public BadgeTestScreen() {
        super(Objects.requireNonNull(Component.translatable("devmod.testing.badge_tests.title")));
    }

    @Nonnull
    private Font getFont() {
        return Objects.requireNonNull(this.font, "Font not initialized");
    }

    @Nonnull
    private Component getTitleComponent() {
        return Objects.requireNonNull(this.title, "Title not initialized");
    }

    @Nonnull
    private static PositionedButton createButton(String id, Component text, Runnable onPress,
                                       int x, int y, int width, int height,
                                       EditorButton.Style style, @Nullable Integer accent) {
        EditorButton.Builder builder = EditorButton.builder(id, Objects.requireNonNull(text).getString())
            .style(style)
            .size(EditorButton.Size.LARGE)
            .onClick(Objects.requireNonNull(onPress));
        if (accent != null) {
            builder.accent(accent);
        }
        return new PositionedButton(builder.build(), x, y, width, height);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        buttons.clear();
        int startY = 60;

        // Individual rarity test buttons
        int y = startY + 30;

        for (BadgeRarity rarity : BadgeRarity.values()) {
            final BadgeRarity finalRarity = rarity;
            Component buttonText = Objects.requireNonNull(
                Component.translatable("devmod.testing.badge_tests.button.test_rarity", rarity.getDisplayName())
            );
            EditorButton.Style style = EditorButton.Style.PRIMARY;
            Integer accent = DesignTokens.Mask.ALPHA | rarity.getColor();
            if (rarity == BadgeRarity.LEGENDARY) {
                style = EditorButton.Style.SUCCESS;
            } else if (rarity == BadgeRarity.COMMON) {
                style = EditorButton.Style.NORMAL;
            }

            PositionedButton button = createButton(
                "badge-" + rarity.name().toLowerCase(Locale.ROOT),
                buttonText,
                () -> showBadgeNotification(finalRarity),
                centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                style, accent
            );
            buttons.add(button);

            y += BUTTON_SPACING;
        }

        // Separator
        y += 10;

        // Test all badges button
        PositionedButton testAllButton = createButton(
            "badge-test-all",
            Objects.requireNonNull(Component.translatable("devmod.testing.badge_tests.button.test_all")),
            this::testAllBadges,
            centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT,
            EditorButton.Style.PRIMARY, null
        );
        buttons.add(testAllButton);

        y += BUTTON_SPACING;

        // Clear queue button
        PositionedButton clearButton = createButton(
            "badge-clear-queue",
            Objects.requireNonNull(Component.translatable("devmod.testing.badge_tests.button.clear")),
            ClientNotificationManager.INSTANCE::clear,
            centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT,
            EditorButton.Style.GHOST, null
        );
        buttons.add(clearButton);

        y += BUTTON_SPACING + 20;

        // Back button
        PositionedButton backButton = createButton(
            "badge-back",
            Objects.requireNonNull(Component.translatable("devmod.testing.badge_tests.button.back")),
            this::onClose,
            centerX - 50, y, 100, 20,
            EditorButton.Style.GHOST, null
        );
        buttons.add(backButton);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        // Background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        Font font = getFont();
        Component titleComponent = getTitleComponent();

        // Title
        UIScaleManager.drawScaledCenteredString(graphics, font, titleComponent, this.width / 2, 20, DesignTokens.Text.PRIMARY);

        // Subtitle
        UIScaleManager.drawScaledCenteredString(graphics, font,
            Component.translatable("devmod.testing.badge_tests.subtitle"),
            this.width / 2, 40, DesignTokens.Text.MUTED);

        // Queue status
        int unreadCount = ClientNotificationManager.INSTANCE.getUnreadCount();
        Component queueText = Component.translatable("devmod.testing.badge_tests.unread", unreadCount);
        UIScaleManager.drawScaledCenteredString(graphics, font, queueText, this.width / 2, this.height - 30,
            unreadCount > 0 ? TestingUiTheme.Badge.UNREAD : DesignTokens.Text.MUTED);

        // Render widgets
        for (PositionedButton pb : buttons) {
            pb.button.render(graphics, pb.x, pb.y, pb.width, pb.height, mouseX, mouseY);
        }

        // Rarity color indicators next to buttons
        int centerX = this.width / 2;
        int y = 90;
        for (BadgeRarity rarity : BadgeRarity.values()) {
            // Color dot
            int dotX = centerX + BUTTON_WIDTH / 2 + 10;
            graphics.fill(dotX, y + 6, dotX + 12, y + 18, DesignTokens.Mask.ALPHA | rarity.getColor());
            y += BUTTON_SPACING;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Don't pause so we can see the popup animate
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (PositionedButton pb : buttons) {
                if (pb.button.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        for (PositionedButton pb : buttons) {
            handled |= pb.button.mouseReleased(mouseX, mouseY, button);
        }
        if (handled) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private record PositionedButton(EditorButton button, int x, int y, int width, int height) {}

    private void testAllBadges() {
        for (BadgeRarity rarity : BadgeRarity.values()) {
            showBadgeNotification(rarity);
        }
    }

    private void showBadgeNotification(BadgeRarity rarity) {
        String rarityId = rarity.name().toLowerCase(Locale.ROOT);
        NotificationPriority priority = switch (rarity) {
            case LEGENDARY -> NotificationPriority.URGENT;
            case EPIC -> NotificationPriority.HIGH;
            case RARE -> NotificationPriority.NORMAL;
            default -> NotificationPriority.NORMAL;
        };

        Notification notification = Notification.builder(NotificationCategory.ACHIEVEMENT)
            .titleKey("devmod.notification.badge_unlock.title")
            .messageKey("devmod.notification.badge_unlock.message")
            .param("badge", rarity.getDisplayName() + " Badge")
            .param("rarity", rarity.getDisplayName())
            .priority(priority)
            .soundId("badge." + rarityId)
            .iconId("badge." + rarityId)
            .persistToMailbox(false)
            .build();

        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }
}
