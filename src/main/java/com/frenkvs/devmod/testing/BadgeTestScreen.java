package com.frenkvs.devmod.testing;

import com.frenkvs.devmod.endurance.GamificationManager.BadgeRarity;
import com.frenkvs.devmod.hud.BadgePopupOverlay;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Test screen for badge popup system.
 * Allows testers to trigger badge popups of each rarity to verify:
 * - Animations (slide-in, bounce, fade-out)
 * - Sounds (per-rarity sounds, fanfare for legendary)
 * - Visual effects (glow for rare+, particles for epic+)
 * - Queue system (multiple badges in sequence)
 */
@OnlyIn(Dist.CLIENT)
public class BadgeTestScreen extends Screen {

    private static final int BUTTON_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_SPACING = 30;

    private final List<PositionedButton> buttons = new ArrayList<>();

    public BadgeTestScreen() {
        super(Objects.requireNonNull(Component.literal("Badge Popup Tests")));
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
                                       EditorButton.Style style, Integer accent) {
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
                Component.literal("Test " + rarity.displayName + " Badge")
            );
            EditorButton.Style style = EditorButton.Style.PRIMARY;
            Integer accent = 0xFF000000 | rarity.color;
            if (rarity == BadgeRarity.LEGENDARY) {
                style = EditorButton.Style.SUCCESS;
            } else if (rarity == BadgeRarity.COMMON) {
                style = EditorButton.Style.NORMAL;
            }

            PositionedButton button = createButton(
                "badge-" + rarity.name().toLowerCase(),
                buttonText,
                () -> BadgePopupOverlay.testBadge(finalRarity),
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
            Objects.requireNonNull(Component.literal("Test ALL Badges (Queue)")),
            BadgePopupOverlay::testAllBadges,
            centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT,
            EditorButton.Style.PRIMARY, null
        );
        buttons.add(testAllButton);

        y += BUTTON_SPACING;

        // Clear queue button
        PositionedButton clearButton = createButton(
            "badge-clear-queue",
            Objects.requireNonNull(Component.literal("Clear Queue")),
            BadgePopupOverlay::clearQueue,
            centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT,
            EditorButton.Style.GHOST, null
        );
        buttons.add(clearButton);

        y += BUTTON_SPACING + 20;

        // Back button
        PositionedButton backButton = createButton(
            "badge-back",
            Objects.requireNonNull(Component.literal("Back")),
            this::onClose,
            centerX - 50, y, 100, 20,
            EditorButton.Style.GHOST, null
        );
        buttons.add(backButton);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        Font font = getFont();
        Component titleComponent = getTitleComponent();

        // Title
        graphics.drawCenteredString(font, titleComponent, this.width / 2, 20, UIConstants.Text.PRIMARY());

        // Subtitle
        graphics.drawCenteredString(font,
            "Click a button to test badge popup",
            this.width / 2, 40, UIConstants.Text.MUTED());

        // Queue status
        int queueSize = BadgePopupOverlay.getQueueSize();
        String queueText = "Queue: " + queueSize + " badge" + (queueSize != 1 ? "s" : "");
        graphics.drawCenteredString(font, queueText, this.width / 2, this.height - 30,
            queueSize > 0 ? 0xFF00FF00 : UIConstants.Text.MUTED());

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
            graphics.fill(dotX, y + 6, dotX + 12, y + 18, 0xFF000000 | rarity.color);
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
}
