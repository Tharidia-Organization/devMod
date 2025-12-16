package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.frenkvs.devmod.ui.editor.core.ScaledCoord;
import com.frenkvs.devmod.ui.editor.core.Typography;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * Modal confirmation dialog.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#37-confirmation-dialogs
 */
public final class ConfirmDialog {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 140;

    private final String title;
    private final String message;
    private final String confirmText;
    private final String cancelText;
    private final int confirmColor;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    private boolean visible = false;
    private int hoveredButton = -1;  // -1=none, 0=confirm, 1=cancel

    // =========================================================================
    // FACTORY METHODS (Common dialogs)
    // =========================================================================

    public static ConfirmDialog unsavedChanges(int changeCount,
                                               Runnable onDiscard,
                                               Runnable onCancel) {
        return new ConfirmDialog(
            "Unsaved Changes",
            "You have " + changeCount + " unsaved changes.\nDiscard and close?",
            "Discard",
            "Cancel",
            UIConstants.Accent.RED,
            onDiscard,
            onCancel
        );
    }

    public static ConfirmDialog resetToDefault(Runnable onReset, Runnable onCancel) {
        return new ConfirmDialog(
            "Reset to Default",
            "This will reset all values to their defaults.\nThis cannot be undone.",
            "Reset",
            "Cancel",
            UIConstants.Accent.ORANGE,
            onReset,
            onCancel
        );
    }

    public static ConfirmDialog switchSlot(String slotName, int changeCount,
                                           Runnable onSwitch, Runnable onCancel) {
        return new ConfirmDialog(
            "Switch Slot",
            "Discard " + changeCount + " changes to " + slotName + "?",
            "Discard & Switch",
            "Cancel",
            UIConstants.Accent.ORANGE,
            onSwitch,
            onCancel
        );
    }

    public static ConfirmDialog deletePreset(String presetName,
                                             Runnable onDelete, Runnable onCancel) {
        return new ConfirmDialog(
            "Delete Preset",
            "Delete preset '" + presetName + "'?\nThis cannot be undone.",
            "Delete",
            "Cancel",
            UIConstants.Accent.RED,
            onDelete,
            onCancel
        );
    }

    public static ConfirmDialog switchModeToPreview(int changeCount,
                                                    Runnable onDiscard,
                                                    Runnable onCancel) {
        return new ConfirmDialog(
            "Switch to Preview",
            "Discard " + changeCount + " unsaved changes and switch to PREVIEW mode?",
            "Discard",
            "Cancel",
            UIConstants.Accent.ORANGE,
            onDiscard,
            onCancel
        );
    }

    // =========================================================================
    // IMPLEMENTATION
    // =========================================================================

    public ConfirmDialog(String title, String message,
                         String confirmText, String cancelText,
                         int confirmColor,
                         Runnable onConfirm, Runnable onCancel) {
        this.title = title;
        this.message = message;
        this.confirmText = confirmText;
        this.cancelText = cancelText;
        this.confirmColor = confirmColor;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    public void show() {
        visible = true;
    }

    public void hide() {
        visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void render(GuiGraphics g, Font font, int screenWidth, int screenHeight,
                       int mouseX, int mouseY) {
        if (!visible) return;

        float textScale = Typography.withUiScale(Typography.BODY);

        // Dark overlay
        g.fill(0, 0, screenWidth, screenHeight, UIConstants.Background.OVERLAY);

        // Center dialog
        int panelW = ScaledCoord.scaleDim(WIDTH);
        int panelH = ScaledCoord.scaleDim(HEIGHT);
        int x = (screenWidth - panelW) / 2;
        int y = (screenHeight - panelH) / 2;

        // Panel
        g.fill(x, y, x + panelW, y + panelH, UIConstants.Background.PANEL_SOLID);
        AxiomRenderer.drawBorder(g, x, y, panelW, panelH, UIConstants.Border.DEFAULT);

        // Title
        Typography.drawText(g, font, Objects.requireNonNull(title, "title cannot be null"),
            x + ScaledCoord.scaleDim(16), y + ScaledCoord.scaleDim(16),
            UIConstants.Text.TITLE, textScale);

        // Message (multi-line support)
        int msgY = y + ScaledCoord.scaleDim(40);
        for (String line : message.split("\n")) {
            Typography.drawText(g, font, Objects.requireNonNull(line, "line cannot be null"),
                x + ScaledCoord.scaleDim(16), msgY, UIConstants.Text.PRIMARY, textScale);
            msgY += ScaledCoord.scaleDim(12);
        }

        // Buttons
        int btnY = y + panelH - ScaledCoord.scaleDim(44);
        int btnWidth = ScaledCoord.scaleDim(100);
        int btnHeight = ScaledCoord.scaleDim(28);

        // Update hover state
        int confirmX = x + panelW / 2 - btnWidth - ScaledCoord.scaleDim(8);
        int cancelX = x + panelW / 2 + ScaledCoord.scaleDim(8);

        hoveredButton = -1;
        if (mouseY >= btnY && mouseY < btnY + btnHeight) {
            if (mouseX >= confirmX && mouseX < confirmX + btnWidth) {
                hoveredButton = 0;
            } else if (mouseX >= cancelX && mouseX < cancelX + btnWidth) {
                hoveredButton = 1;
            }
        }

        // Confirm button
        renderButton(g, font, confirmX, btnY, btnWidth, btnHeight,
                    confirmText, confirmColor, hoveredButton == 0, textScale);

        // Cancel button
        renderButton(g, font, cancelX, btnY, btnWidth, btnHeight,
                    cancelText, UIConstants.Border.DEFAULT, hoveredButton == 1, textScale);
    }

    public boolean mouseClicked(int mouseX, int mouseY) {
        if (!visible) return false;

        if (hoveredButton == 0) {
            playButtonClick();
            hide();
            onConfirm.run();
            return true;
        } else if (hoveredButton == 1) {
            playButtonClick();
            hide();
            onCancel.run();
            return true;
        }

        return true;  // Consume click to prevent interaction with editor behind
    }

    public boolean keyPressed(int keyCode) {
        if (!visible) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            hide();
            onCancel.run();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            hide();
            onConfirm.run();
            return true;
        }

        return true;
    }

    private void renderButton(GuiGraphics g, Font font,
                              int x, int y, int w, int h,
                              String text, int borderColor, boolean hovered, float textScale) {
        int bg = hovered ? UIConstants.Background.HOVER : UIConstants.Background.INPUT;
        g.fill(x, y, x + w, y + h, bg);
        AxiomRenderer.drawBorder(g, x, y, w, h, borderColor);

        int textWidth = Math.round(font.width(Objects.requireNonNull(text, "text cannot be null")) * textScale);
        int textX = x + (w - textWidth) / 2;
        int textY = y + (h - Math.round(font.lineHeight * textScale)) / 2;
        Typography.drawText(g, font, text, textX, textY, UIConstants.Text.PRIMARY, textScale);
    }

    private void playButtonClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(Objects.requireNonNull(SoundEvents.UI_BUTTON_CLICK.value()), 0.5f, 1.0f);
        }
    }
}
