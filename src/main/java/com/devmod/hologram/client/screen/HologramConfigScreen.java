package com.devmod.hologram.client.screen;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorSlider;
import com.devmod.client.ui.editor.components.EditorToggle;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.UiSounds;
import com.devmod.hologram.network.HologramConfigPayload;

/**
 * Configuration screen for the Hologram Projector block.
 * Allows players to adjust scan size, block scale, rotation, and transparency.
 */
@OnlyIn(Dist.CLIENT)
public class HologramConfigScreen extends Screen {

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 260;
    private static final int PADDING = 16;
    private static final int ROW_HEIGHT = 32;
    private static final int BUTTON_HEIGHT = 24;

    private static final int[] SCAN_SIZE_OPTIONS = {16, 32, 48, 64};

    private final BlockPos pos;

    // Current settings
    private int scanSize;
    private int blockSize;
    private boolean rotationEnabled;
    private boolean transparentMode;

    // UI Components
    @Nullable private EditorSlider scanSizeSlider;
    @Nullable private EditorSlider blockSizeSlider;
    @Nullable private EditorToggle rotationToggle;
    @Nullable private EditorToggle transparencyToggle;
    @Nullable private EditorButton rescanButton;
    @Nullable private EditorButton applyButton;
    @Nullable private EditorButton closeButton;

    // Layout
    private int panelX;
    private int panelY;

    public HologramConfigScreen(BlockPos pos, int scanSize, int blockSize,
                                boolean rotationEnabled, boolean transparentMode) {
        super(Objects.requireNonNull(Component.translatable("screen.devmod.hologram_config")));
        this.pos = Objects.requireNonNull(pos);
        this.scanSize = scanSize;
        this.blockSize = blockSize;
        this.rotationEnabled = rotationEnabled;
        this.transparentMode = transparentMode;
    }

    @Override
    protected void init() {
        super.init();

        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        initSliders();
        initToggles();
        initButtons();
    }

    private void initSliders() {
        // Scan Size Slider (16, 32, 48, 64)
        scanSizeSlider = new EditorSlider("scan_size", "Scan Size", 16, 64, scanSize)
            .step(16)
            .format("%.0f")
            .suffix(" blocks")
            .onChange(val -> scanSize = snapToScanSize(Math.round(val)));

        // Block Size Slider (1-4)
        blockSizeSlider = new EditorSlider("block_size", "Display Scale", 1, 4, blockSize)
            .step(1)
            .format("%.0f")
            .suffix("x")
            .onChange(val -> blockSize = Math.round(val));
    }

    private void initToggles() {
        rotationToggle = new EditorToggle("rotation", "Auto-Rotate", rotationEnabled)
            .onChange(val -> rotationEnabled = val);

        transparencyToggle = new EditorToggle("transparency", "Transparent Blocks", transparentMode)
            .onChange(val -> transparentMode = val);
    }

    private void initButtons() {
        rescanButton = EditorButton.builder("rescan", "Rescan Area")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onRescanClicked)
            .build();

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

    private int snapToScanSize(int value) {
        // Snap to nearest valid scan size
        int closest = SCAN_SIZE_OPTIONS[0];
        int minDist = Math.abs(value - closest);
        for (int option : SCAN_SIZE_OPTIONS) {
            int dist = Math.abs(value - option);
            if (dist < minDist) {
                minDist = dist;
                closest = option;
            }
        }
        return closest;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderPanel(graphics);
        renderTitle(graphics);
        renderComponents(graphics, mouseX, mouseY);
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
        String titleText = "Hologram Projector";
        graphics.drawString(font, titleText, panelX + PADDING, panelY + 14,
            DesignTokens.Text.PRIMARY, false);
    }

    private void renderComponents(GuiGraphics graphics, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font);
        int contentX = panelX + PADDING;
        int sliderWidth = PANEL_WIDTH - PADDING * 2;
        int y = panelY + 50;

        // Scan Size
        if (scanSizeSlider != null) {
            scanSizeSlider.render(graphics, contentX, y, sliderWidth, mouseX, mouseY);
        }
        y += ROW_HEIGHT + 8;

        // Block Size
        if (blockSizeSlider != null) {
            blockSizeSlider.render(graphics, contentX, y, sliderWidth, mouseX, mouseY);
        }
        y += ROW_HEIGHT + 16;

        // Toggles side by side
        int toggleWidth = (sliderWidth - 16) / 2;
        if (rotationToggle != null) {
            rotationToggle.render(graphics, contentX, y, toggleWidth, mouseX, mouseY);
        }
        if (transparencyToggle != null) {
            transparencyToggle.render(graphics, contentX + toggleWidth + 16, y, toggleWidth, mouseX, mouseY);
        }
        y += ROW_HEIGHT + 16;

        // Position info
        String posText = String.format("Position: %d, %d, %d", pos.getX(), pos.getY(), pos.getZ());
        graphics.drawString(font, posText, contentX, y, DesignTokens.Text.MUTED, false);
    }

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int buttonWidth = 90;
        int buttonY = panelY + PANEL_HEIGHT - BUTTON_HEIGHT - PADDING;

        // Close button (left)
        if (closeButton != null) {
            closeButton.render(graphics, panelX + PADDING, buttonY, buttonWidth, BUTTON_HEIGHT, mouseX, mouseY);
        }

        // Rescan button (center)
        if (rescanButton != null) {
            int rescanX = panelX + (PANEL_WIDTH - buttonWidth) / 2;
            rescanButton.render(graphics, rescanX, buttonY, buttonWidth, BUTTON_HEIGHT, mouseX, mouseY);
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

        // Check sliders
        if (scanSizeSlider != null && scanSizeSlider.mouseClicked(mouseX, mouseY, button)) return true;
        if (blockSizeSlider != null && blockSizeSlider.mouseClicked(mouseX, mouseY, button)) return true;

        // Check toggles
        if (rotationToggle != null && rotationToggle.mouseClicked(mouseX, mouseY, button)) return true;
        if (transparencyToggle != null && transparencyToggle.mouseClicked(mouseX, mouseY, button)) return true;

        // Check buttons
        if (closeButton != null && closeButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (rescanButton != null && rescanButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (applyButton != null && applyButton.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scanSizeSlider != null) scanSizeSlider.mouseReleased(mouseX, mouseY, button);
        if (blockSizeSlider != null) blockSizeSlider.mouseReleased(mouseX, mouseY, button);
        if (closeButton != null) closeButton.mouseReleased(mouseX, mouseY, button);
        if (rescanButton != null) rescanButton.mouseReleased(mouseX, mouseY, button);
        if (applyButton != null) applyButton.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scanSizeSlider != null && scanSizeSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        if (blockSizeSlider != null && blockSizeSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESCAPE
            onClose();
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

    private void onRescanClicked() {
        sendConfig(true);
        UiSounds.success();
    }

    private void onApplyClicked() {
        sendConfig(false);
        UiSounds.click();
        onClose();
    }

    private void sendConfig(boolean rescan) {
        HologramConfigPayload payload = new HologramConfigPayload(
            pos,
            snapToScanSize(scanSize),
            blockSize,
            rotationEnabled,
            transparentMode,
            rescan
        );
        PacketDistributor.sendToServer(payload);
    }
}
