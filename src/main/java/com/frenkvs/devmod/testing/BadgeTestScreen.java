package com.frenkvs.devmod.testing;

import com.frenkvs.devmod.endurance.GamificationManager.BadgeRarity;
import com.frenkvs.devmod.hud.BadgePopupOverlay;
import com.frenkvs.devmod.ui.UIConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Test screen for badge popup system.
 * Allows testers to trigger badge popups of each rarity to verify:
 * - Animations (slide-in, bounce, fade-out)
 * - Sounds (per-rarity sounds, fanfare for legendary)
 * - Visual effects (glow for rare+, particles for epic+)
 * - Queue system (multiple badges in sequence)
 */
public class BadgeTestScreen extends Screen {

    private static final int BUTTON_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_SPACING = 30;

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
    private static Button createButton(Component text, Button.OnPress onPress, int x, int y, int width, int height) {
        return Objects.requireNonNull(
            Button.builder(
                Objects.requireNonNull(text),
                Objects.requireNonNull(onPress)
            ).bounds(x, y, width, height).build()
        );
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;

        // Individual rarity test buttons
        int y = startY + 30;

        for (BadgeRarity rarity : BadgeRarity.values()) {
            final BadgeRarity finalRarity = rarity;
            Component buttonText = Objects.requireNonNull(
                Component.literal("Test " + rarity.displayName + " Badge")
            );

            Button button = createButton(
                buttonText,
                btn -> BadgePopupOverlay.testBadge(finalRarity),
                centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT
            );
            addRenderableWidget(button);

            y += BUTTON_SPACING;
        }

        // Separator
        y += 10;

        // Test all badges button
        Button testAllButton = createButton(
            Objects.requireNonNull(Component.literal("Test ALL Badges (Queue)")),
            btn -> BadgePopupOverlay.testAllBadges(),
            centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT
        );
        addRenderableWidget(testAllButton);

        y += BUTTON_SPACING;

        // Clear queue button
        Button clearButton = createButton(
            Objects.requireNonNull(Component.literal("Clear Queue")),
            btn -> BadgePopupOverlay.clearQueue(),
            centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT
        );
        addRenderableWidget(clearButton);

        y += BUTTON_SPACING + 20;

        // Back button
        Button backButton = createButton(
            Objects.requireNonNull(Component.literal("Back")),
            btn -> onClose(),
            centerX - 50, y, 100, 20
        );
        addRenderableWidget(backButton);
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
        super.render(graphics, mouseX, mouseY, partialTick);

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
}
