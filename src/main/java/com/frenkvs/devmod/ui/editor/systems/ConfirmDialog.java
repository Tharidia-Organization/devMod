package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.editor.core.BaseOverlay;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.frenkvs.devmod.ui.editor.core.ScaledCoord;
import com.frenkvs.devmod.ui.editor.core.Typography;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * Modal confirmation dialog.
 * Extends BaseOverlay for consistent modal behavior.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#37-confirmation-dialogs
 */
public final class ConfirmDialog extends BaseOverlay {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 140;

    private final String title;
    private final String message;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    // Buttons using EditorButton component
    private final EditorButton confirmButton;
    private final EditorButton cancelButton;

    // Cached button positions for click handling
    private int btnY, btnWidth, btnHeight, confirmX, cancelX;

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
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        // Determine button style based on confirmColor
        EditorButton.Style confirmStyle = EditorButton.Style.PRIMARY;
        if (confirmColor == UIConstants.Accent.RED) {
            confirmStyle = EditorButton.Style.DANGER;
        } else if (confirmColor == UIConstants.Accent.ORANGE) {
            confirmStyle = EditorButton.Style.DANGER;  // Use danger for orange warnings too
        } else if (confirmColor == UIConstants.Accent.GREEN) {
            confirmStyle = EditorButton.Style.SUCCESS;
        }

        // Create buttons with appropriate styles
        this.confirmButton = new EditorButton("confirm", confirmText)
            .style(confirmStyle)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> {
                hide();
                onConfirm.run();
            });

        this.cancelButton = new EditorButton("cancel", cancelText)
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> {
                hide();
                onCancel.run();
            });
    }

    // =========================================================================
    // BaseOverlay IMPLEMENTATION
    // =========================================================================

    @Override
    protected int getPanelWidth() {
        return WIDTH;
    }

    @Override
    protected int getPanelHeight() {
        return HEIGHT;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font,
                                  int x, int y, int width, int height,
                                  int mouseX, int mouseY) {
        float textScale = Typography.withUiScale(Typography.BODY);

        // Title
        Typography.drawText(graphics, font, Objects.requireNonNull(title, "title cannot be null"),
            x + ScaledCoord.scaleDim(16), y + ScaledCoord.scaleDim(16),
            UIConstants.Text.TITLE, textScale);

        // Message (multi-line support)
        int msgY = y + ScaledCoord.scaleDim(40);
        for (String line : message.split("\n")) {
            Typography.drawText(graphics, font, Objects.requireNonNull(line, "line cannot be null"),
                x + ScaledCoord.scaleDim(16), msgY, UIConstants.Text.PRIMARY, textScale);
            msgY += ScaledCoord.scaleDim(12);
        }

        // Calculate and cache button positions
        this.btnY = y + height - ScaledCoord.scaleDim(44);
        this.btnWidth = ScaledCoord.scaleDim(100);
        this.btnHeight = ScaledCoord.scaleDim(28);
        this.confirmX = x + width / 2 - btnWidth - ScaledCoord.scaleDim(8);
        this.cancelX = x + width / 2 + ScaledCoord.scaleDim(8);

        // Render buttons using EditorButton component
        confirmButton.render(graphics, confirmX, btnY, btnWidth, btnHeight, mouseX, mouseY);
        cancelButton.render(graphics, cancelX, btnY, btnWidth, btnHeight, mouseX, mouseY);
    }

    @Override
    protected void onEscapePressed() {
        onCancel.run();
    }

    @Override
    protected boolean handleKeyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            hide();
            onConfirm.run();
            return true;
        }
        return true; // Consume all keys when visible
    }

    @Override
    protected boolean shouldCloseOnClickOutside() {
        return false; // Dialog requires explicit button click
    }

    @Override
    protected boolean handleMouseClicked(double mouseX, double mouseY,
                                          int panelX, int panelY, int panelW, int panelH) {
        // Delegate to EditorButton components
        if (confirmButton.mouseClicked(mouseX, mouseY, 0)) {
            confirmButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }
        if (cancelButton.mouseClicked(mouseX, mouseY, 0)) {
            cancelButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }
        return true; // Consume click to prevent interaction with editor behind
    }
}
