package com.devmod.client.area;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.area.network.DeleteAreaPayload;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.BaseOverlay;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.Typography;

/**
 * Confirmation dialog for deleting an area.
 * Shows a warning message and requires explicit confirmation.
 */
@OnlyIn(Dist.CLIENT)
public final class DeleteAreaConfirmDialog extends BaseOverlay {

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 160;

    private final UUID areaId;
    private final String areaName;
    private final Runnable onComplete;

    private final EditorButton deleteButton;
    private final EditorButton cancelButton;

    private int padding;
    private int btnY;
    private int btnWidth;
    private int btnHeight;
    private int deleteX;
    private int cancelX;

    public DeleteAreaConfirmDialog(UUID areaId, String areaName, Runnable onComplete) {
        this.areaId = Objects.requireNonNull(areaId);
        this.areaName = Objects.requireNonNull(areaName);
        this.onComplete = Objects.requireNonNull(onComplete);

        this.deleteButton = new EditorButton("delete",
            Component.translatable("area.delete.confirm").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::doDelete);

        this.cancelButton = new EditorButton("cancel",
            Component.translatable("gui.cancel").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::hide);
    }

    @Override
    protected int getPanelWidth() {
        return PANEL_WIDTH;
    }

    @Override
    protected int getPanelHeight() {
        return PANEL_HEIGHT;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font,
                                 int x, int y, int width, int height,
                                 int mouseX, int mouseY) {
        padding = ScaledCoord.scaleDim(DesignTokens.Spacing.XL);

        // Title
        Typography.drawText(graphics, font,
            Component.translatable("area.delete.title").getString(),
            x + padding, y + padding,
            DesignTokens.Text.TITLE(),
            Typography.withUiScale(Typography.BODY));

        // Warning message
        int messageY = y + padding + font.lineHeight + ScaledCoord.scaleDim(DesignTokens.Spacing.MD);
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.delete.warning", areaName)),
            x + padding, messageY,
            DesignTokens.Text.SECONDARY());

        // Additional warning
        int warning2Y = messageY + font.lineHeight + 4;
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.delete.irreversible")),
            x + padding, warning2Y,
            DesignTokens.AreaBuilder.WARNING_TEXT);

        // Buttons
        btnWidth = ScaledCoord.scaleDim(100);
        btnHeight = ScaledCoord.scaleDim(28);
        int gap = ScaledCoord.scaleDim(DesignTokens.Spacing.MD);
        btnY = y + height - btnHeight - padding;
        deleteX = x + width / 2 - btnWidth - gap;
        cancelX = x + width / 2 + gap;

        deleteButton.render(graphics, deleteX, btnY, btnWidth, btnHeight, mouseX, mouseY);
        cancelButton.render(graphics, cancelX, btnY, btnWidth, btnHeight, mouseX, mouseY);
    }

    private void doDelete() {
        PacketDistributor.sendToServer(new DeleteAreaPayload(areaId));
        hide();
        onComplete.run();
    }

    @Override
    protected void onEscapePressed() {
        hide();
    }

    @Override
    protected boolean shouldCloseOnClickOutside() {
        return false;
    }

    @Override
    protected boolean handleMouseClicked(double mouseX, double mouseY,
                                         int panelX, int panelY, int panelW, int panelH) {
        if (deleteButton.mouseClicked(mouseX, mouseY, 0)) {
            deleteButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }
        if (cancelButton.mouseClicked(mouseX, mouseY, 0)) {
            cancelButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }
        return true;
    }

    @Override
    protected boolean handleCharTyped(char chr, int modifiers) {
        return false;
    }

    @Override
    protected boolean handleKeyPressed(int keyCode) {
        return true;
    }
}
