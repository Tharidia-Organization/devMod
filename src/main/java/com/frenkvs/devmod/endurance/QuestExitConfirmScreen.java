package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Confirmation screen before exiting an Endurance Quest.
 * Prevents accidental exits by requiring explicit confirmation.
 * Uses standard UIConstants for consistent theming.
 */
@SuppressWarnings("null")
public class QuestExitConfirmScreen extends Screen {

    // Colors - standardized to UIConstants
    private static final int COLOR_BG = UIConstants.Background.PANEL_SOLID;
    private static final int COLOR_BORDER = UIConstants.Border.DEFAULT;  // Blue instead of orange
    private static final int COLOR_TEXT = UIConstants.Text.PRIMARY;
    private static final int COLOR_WARNING = UIConstants.Accent.ORANGE;
    private static final int COLOR_DANGER = UIConstants.Accent.RED;

    // Dimensions
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 150;

    private final Screen parentScreen;

    public QuestExitConfirmScreen(Screen parent) {
        super(I18n.screenTitle("exit_quest_confirm"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = width / 2;
        int centerY = height / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;

        // Confirm Exit button
        addRenderableWidget(Button.builder(I18n.ui("yes_exit_quest"), btn -> confirmExit())
            .bounds(panelX + 20, panelY + PANEL_HEIGHT - 45, 120, 25)
            .build());

        // Cancel button
        addRenderableWidget(Button.builder(I18n.ui("cancel"), btn -> cancel())
            .bounds(panelX + PANEL_WIDTH - 140, panelY + PANEL_HEIGHT - 45, 120, 25)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int centerY = height / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = centerY - PANEL_HEIGHT / 2;

        // Panel border
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, COLOR_BORDER);
        // Panel background
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_BG);

        // Title
        graphics.drawCenteredString(font, "Exit Endurance Quest?", centerX, panelY + 15, COLOR_DANGER);

        // Warning message
        graphics.drawCenteredString(font, "You will lose all progress for this run!", centerX, panelY + 45, COLOR_WARNING);
        graphics.drawCenteredString(font, "Points earned will be saved.", centerX, panelY + 60, COLOR_TEXT);

        // Hint
        graphics.drawCenteredString(font, "Press ESC to cancel", centerX, panelY + 85, 0xFF777777);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void confirmExit() {
        // Send exit action to server
        PacketDistributor.sendToServer(
            new QuestActionPayload(QuestActionPayload.Action.GIVE_UP_AFTER_DEATH)
        );

        // Close this screen
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    private void cancel() {
        // Return to parent screen or close
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC cancels
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
