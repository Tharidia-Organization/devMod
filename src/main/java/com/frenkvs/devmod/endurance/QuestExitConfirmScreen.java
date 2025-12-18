package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.ui.ConfirmDialog;
import com.frenkvs.devmod.ui.ConfirmDialog.Style;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Confirmation screen before exiting an Endurance Quest.
 * Prevents accidental exits by requiring explicit confirmation.
 * Uses standard UIConstants for consistent theming.
 */

public class QuestExitConfirmScreen extends Screen {

    private final Screen parentScreen;
    private ConfirmDialog exitDialog;

    public QuestExitConfirmScreen(Screen parent) {
        super(I18n.screenTitle("exit_quest_confirm"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        exitDialog = ConfirmDialog.create(
            I18n.screenTitle("exit_quest_confirm").getString(),
            I18n.ui("yes_exit_quest").getString(),
            I18n.ui("cancel").getString(),
            Style.DANGER,
            this::confirmExit,
            this::cancel,
            "You will lose all progress for this run!",
            "Points earned will be saved."
        );
        exitDialog.show();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        if (exitDialog != null && exitDialog.isVisible()) {
            exitDialog.render(graphics, font, width, height, mouseX, mouseY);
        }
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
        if (exitDialog != null && exitDialog.keyPressed(keyCode)) {
            return true;
        }

        // ESC cancels
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (exitDialog != null && exitDialog.mouseClicked(mouseX, mouseY, width, height)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (exitDialog != null && exitDialog.mouseScrolled(mouseX, mouseY, scrollY, width, height)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (exitDialog != null && exitDialog.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
