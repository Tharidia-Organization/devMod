package com.devmod.client.area;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.area.network.SaveAreaTemplatePayload;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.BaseOverlay;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.Typography;

/**
 * Dialog for saving an area configuration as a reusable template.
 */
@OnlyIn(Dist.CLIENT)
public final class SaveTemplateDialog extends BaseOverlay {

    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 200;

    private final UUID sourceAreaId;
    private final Runnable onComplete;

    @Nullable private EditBox nameField;
    @Nullable private EditBox descriptionField;
    private final EditorButton saveButton;
    private final EditorButton cancelButton;

    private String templateName = "";
    private String templateDescription = "";
    private int padding;
    private int fieldWidth;
    private int fieldY;
    private int descFieldY;
    private int btnY;
    private int btnWidth;
    private int btnHeight;
    private int saveX;
    private int cancelX;

    public SaveTemplateDialog(UUID sourceAreaId, Runnable onComplete) {
        this.sourceAreaId = Objects.requireNonNull(sourceAreaId);
        this.onComplete = Objects.requireNonNull(onComplete);

        this.saveButton = new EditorButton("save",
            Component.translatable("area.template.save").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::save);

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
    public void show() {
        super.show();
        // Reset fields when showing
        templateName = "";
        templateDescription = "";
        if (nameField != null) {
            nameField.setValue("");
        }
        if (descriptionField != null) {
            descriptionField.setValue("");
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, Font font,
                                 int x, int y, int width, int height,
                                 int mouseX, int mouseY) {
        padding = ScaledCoord.scaleDim(DesignTokens.Spacing.XL);
        int labelHeight = font.lineHeight;

        // Title
        Typography.drawText(graphics, font,
            Component.translatable("area.template.save_title").getString(),
            x + padding, y + padding,
            DesignTokens.Text.TITLE(),
            Typography.withUiScale(Typography.BODY));

        // Name label
        int nameLabelY = y + padding + UIScaleManager.getScaledLineHeight() + ScaledCoord.scaleDim(DesignTokens.Spacing.MD);
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.template.name")),
            x + padding, nameLabelY,
            DesignTokens.Text.SECONDARY());

        // Name field
        fieldY = nameLabelY + labelHeight + 4;
        fieldWidth = width - padding * 2;
        int fieldHeight = ScaledCoord.scaleDim(20);

        EditBox nameBox = nameField;
        if (nameBox == null) {
            nameBox = new EditBox(font, x + padding, fieldY, fieldWidth, fieldHeight,
                Objects.requireNonNull(Component.translatable("area.template.name")));
            nameBox.setMaxLength(SaveAreaTemplatePayload.MAX_NAME_LENGTH);
            nameBox.setHint(Objects.requireNonNull(Component.translatable("area.template.name_hint")));
            nameBox.setBordered(false);
            nameBox.setResponder(val -> templateName = val != null ? val : "");
            nameField = nameBox;
        }
        nameBox.setX(x + padding);
        nameBox.setY(fieldY);
        nameBox.setWidth(fieldWidth);

        AxiomRenderer.drawInputBackground(graphics, nameBox.getX(), nameBox.getY(),
            nameBox.getWidth(), nameBox.getHeight(), nameBox.isFocused());
        nameBox.render(graphics, mouseX, mouseY, 0);

        // Description label
        int descLabelY = fieldY + fieldHeight + ScaledCoord.scaleDim(DesignTokens.Spacing.MD);
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.template.description")),
            x + padding, descLabelY,
            DesignTokens.Text.SECONDARY());

        // Description field
        descFieldY = descLabelY + labelHeight + 4;

        EditBox descBox = descriptionField;
        if (descBox == null) {
            descBox = new EditBox(font, x + padding, descFieldY, fieldWidth, fieldHeight,
                Objects.requireNonNull(Component.translatable("area.template.description")));
            descBox.setMaxLength(SaveAreaTemplatePayload.MAX_DESCRIPTION_LENGTH);
            descBox.setHint(Objects.requireNonNull(Component.translatable("area.template.description_hint")));
            descBox.setBordered(false);
            descBox.setResponder(val -> templateDescription = val != null ? val : "");
            descriptionField = descBox;
        }
        descBox.setX(x + padding);
        descBox.setY(descFieldY);
        descBox.setWidth(fieldWidth);

        AxiomRenderer.drawInputBackground(graphics, descBox.getX(), descBox.getY(),
            descBox.getWidth(), descBox.getHeight(), descBox.isFocused());
        descBox.render(graphics, mouseX, mouseY, 0);

        // Buttons
        btnWidth = ScaledCoord.scaleDim(100);
        btnHeight = ScaledCoord.scaleDim(28);
        int gap = ScaledCoord.scaleDim(DesignTokens.Spacing.MD);
        btnY = y + height - btnHeight - padding;
        saveX = x + width / 2 - btnWidth - gap;
        cancelX = x + width / 2 + gap;

        saveButton.render(graphics, saveX, btnY, btnWidth, btnHeight, mouseX, mouseY);
        cancelButton.render(graphics, cancelX, btnY, btnWidth, btnHeight, mouseX, mouseY);
    }

    private void save() {
        String name = templateName.trim();
        // CLI-07 fix: Validate template name with user feedback
        if (name.isEmpty()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.template.error_name_empty")), true);
            }
            return;
        }
        if (name.length() > 64) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("area.template.error_name_too_long")), true);
            }
            return;
        }

        String desc = templateDescription.trim();

        PacketDistributor.sendToServer(new SaveAreaTemplatePayload(sourceAreaId, name, desc));
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
        // Check name field
        if (nameField != null && nameField.mouseClicked(mouseX, mouseY, 0)) {
            if (descriptionField != null) {
                descriptionField.setFocused(false);
            }
            return true;
        }
        // Check description field
        if (descriptionField != null && descriptionField.mouseClicked(mouseX, mouseY, 0)) {
            if (nameField != null) {
                nameField.setFocused(false);
            }
            return true;
        }
        // Check buttons
        if (saveButton.mouseClicked(mouseX, mouseY, 0)) {
            saveButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }
        if (cancelButton.mouseClicked(mouseX, mouseY, 0)) {
            cancelButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }
        // Unfocus all fields if clicking elsewhere
        if (nameField != null) nameField.setFocused(false);
        if (descriptionField != null) descriptionField.setFocused(false);
        return true;
    }

    @Override
    protected boolean handleCharTyped(char chr, int modifiers) {
        EditBox nameBox = nameField;
        if (nameBox != null && nameBox.isFocused()) {
            return nameBox.charTyped(chr, modifiers);
        }
        EditBox descBox = descriptionField;
        if (descBox != null && descBox.isFocused()) {
            return descBox.charTyped(chr, modifiers);
        }
        return false;
    }

    @Override
    protected boolean handleKeyPressed(int keyCode) {
        EditBox nameBox = nameField;
        if (nameBox != null && nameBox.isFocused()) {
            return nameBox.keyPressed(keyCode, 0, 0);
        }
        EditBox descBox = descriptionField;
        if (descBox != null && descBox.isFocused()) {
            return descBox.keyPressed(keyCode, 0, 0);
        }
        return true;
    }
}
