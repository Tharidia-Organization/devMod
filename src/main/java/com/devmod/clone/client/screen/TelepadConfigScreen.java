package com.devmod.clone.client.screen;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.UiSounds;
import com.devmod.clone.network.TelepadConfigPayload;

/**
 * Configuration screen for the Telepad block.
 * Allows players to set the telepad network name.
 */
@OnlyIn(Dist.CLIENT)
public class TelepadConfigScreen extends Screen {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 160;
    private static final int PADDING = 16;
    private static final int BUTTON_HEIGHT = 24;

    private final BlockPos pos;
    private String telepadName;

    // UI Components
    @Nullable private EditBox nameField;
    @Nullable private EditorButton applyButton;
    @Nullable private EditorButton closeButton;

    // Layout
    private int panelX;
    private int panelY;

    public TelepadConfigScreen(BlockPos pos, String telepadName) {
        super(Objects.requireNonNull(Component.translatable("screen.devmod.telepad_config")));
        this.pos = Objects.requireNonNull(pos);
        this.telepadName = telepadName != null ? telepadName : "";
    }

    @Override
    protected void init() {
        super.init();

        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        initTextField();
        initButtons();
    }

    private void initTextField() {
        var font = Objects.requireNonNull(Minecraft.getInstance().font);
        int fieldWidth = PANEL_WIDTH - PADDING * 2;
        int fieldY = panelY + 60;

        nameField = new EditBox(font, panelX + PADDING, fieldY, fieldWidth, 20,
            Component.translatable("screen.devmod.telepad.name"));
        nameField.setMaxLength(TelepadConfigPayload.MAX_TELEPAD_NAME_LENGTH);
        nameField.setValue(telepadName);
        nameField.setHint(Component.literal("Enter network name..."));
        nameField.setBordered(false);
        nameField.setTextColor(DesignTokens.Text.PRIMARY);
        nameField.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(nameField);
    }

    private void initButtons() {
        applyButton = EditorButton.builder("apply", "Apply")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onApplyClicked)
            .build();

        closeButton = EditorButton.builder("close", "Close")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderPanel(graphics);
        renderTitle(graphics);
        renderHelpText(graphics);
        renderInputBackground(graphics);
        renderButtons(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphics graphics) {
        // Panel background
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT,
            DesignTokens.Bg.LEVEL_2);

        // Border
        int border = DesignTokens.Stroke.DEFAULT;
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, border);
        graphics.fill(panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, border);
        graphics.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, border);
        graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, border);

        // Header separator
        int headerY = panelY + 36;
        graphics.fill(panelX + PADDING, headerY, panelX + PANEL_WIDTH - PADDING, headerY + 1, border);
    }

    private void renderTitle(GuiGraphics graphics) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font);
        String titleText = "Telepad Configuration";
        UIScaleManager.drawScaledString(graphics, font, titleText, panelX + PADDING, panelY + 14,
            DesignTokens.Text.PRIMARY, false);
    }

    private void renderHelpText(GuiGraphics graphics) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font);
        int y = panelY + 45;
        UIScaleManager.drawScaledString(graphics, font, "Network Name:", panelX + PADDING, y,
            DesignTokens.Text.SECONDARY, false);

        // Help text below field
        int helpY = panelY + 90;
        UIScaleManager.drawScaledString(graphics, font, "Telepads with the same name are linked.",
            panelX + PADDING, helpY, DesignTokens.Text.MUTED, false);
    }

    private void renderInputBackground(GuiGraphics graphics) {
        EditBox field = nameField;
        if (field == null) {
            return;
        }
        AxiomRenderer.drawInputBackground(graphics, field.getX(), field.getY(), field.getWidth(),
            field.getHeight(), field.isFocused());
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonWidth = 80;
        int buttonY = panelY + PANEL_HEIGHT - BUTTON_HEIGHT - PADDING;

        // Close button (left)
        if (closeButton != null) {
            closeButton.render(graphics, panelX + PADDING, buttonY, buttonWidth, BUTTON_HEIGHT, mouseX, mouseY);
        }

        // Apply button (right)
        if (applyButton != null) {
            applyButton.render(graphics, panelX + PANEL_WIDTH - PADDING - buttonWidth, buttonY,
                buttonWidth, BUTTON_HEIGHT, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Check buttons
        if (closeButton != null && closeButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (applyButton != null && applyButton.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (closeButton != null) closeButton.mouseReleased(mouseX, mouseY, button);
        if (applyButton != null) applyButton.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESCAPE
            onClose();
            return true;
        }
        if (keyCode == 257 && nameField != null && nameField.isFocused()) { // ENTER
            onApplyClicked();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    private void onApplyClicked() {
        if (nameField != null) {
            telepadName = nameField.getValue().trim();
        }
        sendConfig();
        UiSounds.click();
        onClose();
    }

    private void sendConfig() {
        TelepadConfigPayload payload = new TelepadConfigPayload(pos, telepadName);
        PacketDistributor.sendToServer(payload);
    }
}
