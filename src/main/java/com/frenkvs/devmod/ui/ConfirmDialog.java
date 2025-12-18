package com.frenkvs.devmod.ui;

import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.editor.core.BaseOverlay;
import com.frenkvs.devmod.ui.editor.core.ScaledCoord;
import com.frenkvs.devmod.ui.editor.core.Typography;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
    * Reusable confirmation dialog overlay that leverages the unified editor primitives
    * (BaseOverlay, EditorButton, Typography) so it can be dropped into any screen.
    */
public final class ConfirmDialog extends BaseOverlay {

    private static final int PANEL_WIDTH = 340;
    private static final int BASE_HEIGHT = 140;
    private static final int LINE_HEIGHT = 14;
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 28;

    public enum Style {
        PRIMARY,
        WARNING,
        DANGER,
        SUCCESS
    }

    private String title;
    private List<String> messageLines;
    private final String confirmLabel;
    private final String cancelLabel;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private final Style style;

    private final EditorButton confirmButton;
    private final EditorButton cancelButton;

    // Cached button positions for click handling
    private int btnY;
    private int btnWidth;
    private int btnHeight;
    private int confirmX;
    private int cancelX;

    public ConfirmDialog(String title,
                         List<String> messageLines,
                         String confirmLabel,
                         String cancelLabel,
                         Style style,
                         Runnable onConfirm,
                         Runnable onCancel) {
        this.title = Objects.requireNonNull(title, "title");
        this.messageLines = new ArrayList<>(Objects.requireNonNull(messageLines, "messageLines"));
        this.confirmLabel = Objects.requireNonNull(confirmLabel, "confirmLabel");
        this.cancelLabel = Objects.requireNonNull(cancelLabel, "cancelLabel");
        this.style = Objects.requireNonNull(style, "style");
        this.onConfirm = Objects.requireNonNull(onConfirm, "onConfirm");
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");

        this.confirmButton = new EditorButton("confirm", this.confirmLabel)
            .style(mapStyle(style))
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> {
                hide();
                this.onConfirm.run();
            });

        this.cancelButton = new EditorButton("cancel", this.cancelLabel)
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> {
                hide();
                this.onCancel.run();
            });
    }

    /**
     * Update the dialog title and body text before showing it.
     */
    public ConfirmDialog configure(String newTitle, List<String> newLines) {
        this.title = Objects.requireNonNull(newTitle, "title");
        this.messageLines = new ArrayList<>(Objects.requireNonNull(newLines, "lines"));
        return this;
    }

    /**
     * Convenience factory with varargs body lines.
     */
    public static ConfirmDialog create(String title,
                                       String confirmLabel,
                                       String cancelLabel,
                                       Style style,
                                       Runnable onConfirm,
                                       Runnable onCancel,
                                       String... bodyLines) {
        return new ConfirmDialog(
            title,
            Arrays.asList(bodyLines),
            confirmLabel,
            cancelLabel,
            style,
            onConfirm,
            onCancel
        );
    }

    @Override
    protected int getPanelWidth() {
        return PANEL_WIDTH;
    }

    @Override
    protected int getPanelHeight() {
        return calculatePanelHeight();
    }

    @Override
    protected boolean usesDynamicHeight() {
        return true;
    }

    @Override
    protected void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        super.renderPanel(graphics, x, y, width, height);
        // Accent bar at the top using the dialog style color
        graphics.fill(x, y, x + width, y + ScaledCoord.scaleDim(2), getAccentColor());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font,
                                 int x, int y, int width, int height,
                                 int mouseX, int mouseY) {
        float titleScale = Typography.withUiScale(Typography.BODY);
        float bodyScale = Typography.withUiScale(Typography.VALUE);
        int padding = ScaledCoord.scaleDim(com.frenkvs.devmod.ui.editor.core.UIConstants.Spacing.XL);

        // Title
        Typography.drawText(
            graphics,
            font,
            title,
            x + padding,
            y + padding,
            com.frenkvs.devmod.ui.editor.core.UIConstants.Text.TITLE(),
            titleScale
        );

        // Body lines
        int lineY = y + padding + ScaledCoord.scaleDim(20);
        for (String line : messageLines) {
            Typography.drawText(
                graphics,
                font,
                line,
                x + padding,
                lineY,
                com.frenkvs.devmod.ui.editor.core.UIConstants.Text.PRIMARY(),
                bodyScale
            );
            lineY += ScaledCoord.scaleDim(LINE_HEIGHT);
        }

        // Buttons
        btnWidth = ScaledCoord.scaleDim(BUTTON_WIDTH);
        btnHeight = ScaledCoord.scaleDim(BUTTON_HEIGHT);
        int gap = ScaledCoord.scaleDim(com.frenkvs.devmod.ui.editor.core.UIConstants.Spacing.MD);
        btnY = y + height - btnHeight - padding;
        confirmX = x + width / 2 - btnWidth - gap;
        cancelX = x + width / 2 + gap;

        confirmButton.render(graphics, confirmX, btnY, btnWidth, btnHeight, mouseX, mouseY);
        cancelButton.render(graphics, cancelX, btnY, btnWidth, btnHeight, mouseX, mouseY);
    }

    @Override
    protected void onEscapePressed() {
        hide();
        onCancel.run();
    }

    @Override
    protected boolean handleKeyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            hide();
            onConfirm.run();
            return true;
        }
        return true; // consume other keys while visible
    }

    @Override
    protected boolean shouldCloseOnClickOutside() {
        // Avoid accidental dismissal for destructive dialogs
        return false;
    }

    @Override
    protected boolean handleMouseClicked(double mouseX, double mouseY, int panelX, int panelY, int panelW, int panelH) {
        if (confirmButton.mouseClicked(mouseX, mouseY, 0)) {
            confirmButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }
        if (cancelButton.mouseClicked(mouseX, mouseY, 0)) {
            cancelButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }
        return true;
    }

    @Override
    protected boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDelta,
                                          int panelX, int panelY, int panelW, int panelH) {
        // Modal dialogs should consume scroll to prevent background interaction
        return true;
    }

    private int calculatePanelHeight() {
        int minHeight = BASE_HEIGHT;
        int linesHeight = LINE_HEIGHT * Math.max(1, messageLines.size());
        int bodySpace = 60 + linesHeight;
        int buttonsSpace = BUTTON_HEIGHT + com.frenkvs.devmod.ui.editor.core.UIConstants.Spacing.XL;
        int total = ScaledCoord.alignTo4(bodySpace + buttonsSpace);
        return Math.max(minHeight, total);
    }

    private int getAccentColor() {
        return switch (style) {
            case PRIMARY -> com.frenkvs.devmod.ui.editor.core.UIConstants.Accent.CYAN();
            case WARNING -> com.frenkvs.devmod.ui.editor.core.UIConstants.Accent.ORANGE();
            case DANGER -> com.frenkvs.devmod.ui.editor.core.UIConstants.Accent.RED();
            case SUCCESS -> com.frenkvs.devmod.ui.editor.core.UIConstants.Accent.GREEN();
        };
    }

    private static EditorButton.Style mapStyle(Style style) {
        return switch (style) {
            case PRIMARY -> EditorButton.Style.PRIMARY;
            case WARNING -> EditorButton.Style.DANGER;
            case DANGER -> EditorButton.Style.DANGER;
            case SUCCESS -> EditorButton.Style.SUCCESS;
        };
    }
}
