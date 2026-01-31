package com.devmod.client.transport;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
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
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.transport.TransportColor;
import com.devmod.transport.TransportData;
import com.devmod.transport.TransportMode;
import com.devmod.transport.network.TransportConfigOpenPayload;
import com.devmod.transport.network.TransportConfigSavePayload;

/**
 * Configuration screen for transport nodes.
 *
 * <p>Opens when player shift-clicks a Warp Core block.
 * Allows configuration of:
 * <ul>
 *   <li>Transport mode (Network, Linked, Fixed, etc.)</li>
 *   <li>Network name</li>
 *   <li>Display name</li>
 *   <li>Color (via color selector)</li>
 *   <li>Selection mode for network</li>
 * </ul>
 *
 * <p>From Bibbia Estetica:
 * <ul>
 *   <li>Font: Minecraft default, NEVER custom</li>
 *   <li>Background: DesignTokens panel with ~88% opacity</li>
 *   <li>Border color = TransportColor</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class TransportConfigScreen extends Screen {

    private static final int PANEL_BG = DesignTokens.withAlpha(DesignTokens.Background.PANEL, DesignTokens.Alpha.A88);
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 220;

    private final BlockPos nodePos;
    // Current configuration
    private TransportMode mode;
    private TransportColor color;
    private String networkName;
    private String displayName;
    private TransportData.NetworkSelectionMode selectionMode;

    // Readonly info
    private final int chargeTime;
    private final int cooldownTime;
    private final boolean hasLinkedNode;
    private final int frameCount;

    // Widgets
    @Nullable private EditBox networkNameField;
    @Nullable private EditBox displayNameField;
    @Nullable private CycleButton<TransportMode> modeButton;
    @Nullable private CycleButton<TransportColor> colorButton;
    @Nullable private CycleButton<TransportData.NetworkSelectionMode> selectionModeButton;
    @Nullable private EditorButton saveButton;
    @Nullable private EditorButton cancelButton;

    public TransportConfigScreen(@Nonnull TransportConfigOpenPayload payload) {
        super(Component.translatable("screen.devmod.transport_config"));

        this.nodePos = payload.nodePos();
        this.mode = payload.getMode();
        this.color = payload.getColor();
        this.networkName = payload.networkName();
        this.displayName = payload.displayName();
        this.chargeTime = payload.chargeTime();
        this.cooldownTime = payload.cooldownTime();
        this.hasLinkedNode = payload.hasLinkedNode();
        this.frameCount = payload.frameCount();
        this.selectionMode = TransportData.NetworkSelectionMode.RANDOM;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = width / 2;
        int centerY = height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelTop = centerY - PANEL_HEIGHT / 2;

        int widgetWidth = PANEL_WIDTH - 40;
        int currentY = panelTop + 30;

        // Mode selector
        modeButton = CycleButton.<TransportMode>builder(m -> Component.translatable("transport.mode." + m.getSerializedName()))
            .withValues(TransportMode.values())
            .withInitialValue(mode)
            .create(panelLeft + 20, currentY, widgetWidth, 20,
                Component.translatable("screen.devmod.transport_config.mode"),
                (btn, value) -> {
                    mode = value;
                    updateWidgetVisibility();
                });
        addRenderableWidget(modeButton);
        currentY += 26;

        // Network name field (only for NETWORK mode)
        networkNameField = new EditBox(font, panelLeft + 20, currentY, widgetWidth, 20,
            Component.translatable("screen.devmod.transport_config.network_name"));
        networkNameField.setMaxLength(TransportConfigSavePayload.MAX_NETWORK_NAME_LENGTH);
        networkNameField.setValue(networkName);
        networkNameField.setHint(Component.literal("network_name"));
        networkNameField.setBordered(false);
        networkNameField.setTextColor(DesignTokens.Text.PRIMARY);
        networkNameField.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(networkNameField);
        currentY += 26;

        // Selection mode (only for NETWORK mode)
        selectionModeButton = CycleButton.<TransportData.NetworkSelectionMode>builder(
            m -> Component.translatable("transport.selection." + m.name().toLowerCase(Locale.ROOT)))
            .withValues(TransportData.NetworkSelectionMode.values())
            .withInitialValue(selectionMode)
            .create(panelLeft + 20, currentY, widgetWidth, 20,
                Component.translatable("screen.devmod.transport_config.selection"),
                (btn, value) -> selectionMode = value);
        addRenderableWidget(selectionModeButton);
        currentY += 26;

        // Display name field
        displayNameField = new EditBox(font, panelLeft + 20, currentY, widgetWidth, 20,
            Component.translatable("screen.devmod.transport_config.display_name"));
        displayNameField.setMaxLength(TransportConfigSavePayload.MAX_DISPLAY_NAME_LENGTH);
        displayNameField.setValue(displayName);
        displayNameField.setHint(Component.literal("Display Name"));
        displayNameField.setBordered(false);
        displayNameField.setTextColor(DesignTokens.Text.PRIMARY);
        displayNameField.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(displayNameField);
        currentY += 26;

        // Color selector
        colorButton = CycleButton.<TransportColor>builder(c -> Component.literal(c.getDisplayName()))
            .withValues(TransportColor.values())
            .withInitialValue(color)
            .create(panelLeft + 20, currentY, widgetWidth, 20,
                Component.translatable("screen.devmod.transport_config.color"),
                (btn, value) -> color = value);
        addRenderableWidget(colorButton);
        currentY += 32;

        // Save and Cancel buttons
        int buttonHeight = EditorButton.Size.MEDIUM.height();
        int buttonGap = DesignTokens.Spacing.SM;
        int buttonWidth = (widgetWidth - buttonGap) / 2;
        saveButton = EditorButton.builder("transport_save", Component.translatable("gui.devmod.save").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::save)
            .build();
        addRenderableWidget(new EditorButtonWidget(saveButton, panelLeft + 20, currentY, buttonWidth, buttonHeight));

        cancelButton = EditorButton.builder("transport_cancel", Component.translatable("gui.cancel").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::cancel)
            .build();
        addRenderableWidget(new EditorButtonWidget(cancelButton, panelLeft + 20 + buttonWidth + buttonGap, currentY,
            buttonWidth, buttonHeight));

        // Update widget visibility based on initial mode
        updateWidgetVisibility();
    }

    /**
     * Updates widget visibility based on current mode.
     */
    private void updateWidgetVisibility() {
        boolean isNetworkMode = mode == TransportMode.NETWORK;
        if (networkNameField != null) {
            networkNameField.visible = isNetworkMode;
            networkNameField.active = isNetworkMode;
        }
        if (selectionModeButton != null) {
            selectionModeButton.visible = isNetworkMode;
            selectionModeButton.active = isNetworkMode;
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        // Render darkened background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int centerY = height / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelTop = centerY - PANEL_HEIGHT / 2;

        // Render panel background
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, PANEL_BG);

        // Render panel border (color based on node color)
        int borderColor = color.getColorWithAlpha();
        renderBorder(graphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, borderColor, 2);

        // Render title
        Component title = Component.translatable("screen.devmod.transport_config");
        int titleWidth = font.width(title);
        graphics.drawString(font, title, centerX - titleWidth / 2, panelTop + 8, DesignTokens.Text.PRIMARY, false);

        renderInputBackgrounds(graphics);

        // Render info section at bottom
        int infoY = panelTop + PANEL_HEIGHT - 40;
        String infoText = String.format("Charge: %d ticks | Cooldown: %d ticks | Frames: %d",
            chargeTime, cooldownTime, frameCount);
        graphics.drawString(font, infoText, panelLeft + 10, infoY, DesignTokens.Text.SECONDARY, false);

        if (hasLinkedNode) {
            graphics.drawString(font, "Linked", panelLeft + PANEL_WIDTH - 50, infoY, DesignTokens.Semantic.SUCCESS, false);
        }

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderInputBackgrounds(GuiGraphics graphics) {
        if (networkNameField != null && networkNameField.visible) {
            AxiomRenderer.drawInputBackground(
                graphics,
                networkNameField.getX(),
                networkNameField.getY(),
                networkNameField.getWidth(),
                networkNameField.getHeight(),
                networkNameField.isFocused()
            );
        }
        if (displayNameField != null) {
            AxiomRenderer.drawInputBackground(
                graphics,
                displayNameField.getX(),
                displayNameField.getY(),
                displayNameField.getWidth(),
                displayNameField.getHeight(),
                displayNameField.isFocused()
            );
        }
    }

    /**
     * Renders a border around a rectangle.
     */
    private void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color, int thickness) {
        // Top
        graphics.fill(x, y, x + width, y + thickness, color);
        // Bottom
        graphics.fill(x, y + height - thickness, x + width, y + height, color);
        // Left
        graphics.fill(x, y, x + thickness, y + height, color);
        // Right
        graphics.fill(x + width - thickness, y, x + width, y + height, color);
    }

    /**
     * Saves the configuration and closes the screen.
     */
    private void save() {
        // Get values from widgets
        String newNetworkName = networkNameField != null ? networkNameField.getValue().trim() : networkName;
        String newDisplayName = displayNameField != null ? displayNameField.getValue().trim() : displayName;

        // Create save payload
        TransportConfigSavePayload payload = new TransportConfigSavePayload(
            nodePos,
            mode.ordinal(),
            color.getIndex(),
            newNetworkName,
            selectionMode.ordinal(),
            newDisplayName
        );

        // Send to server
        PacketDistributor.sendToServer(payload);

        // Close screen
        onClose();
    }

    /**
     * Cancels and closes the screen.
     */
    private void cancel() {
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle escape key
        if (keyCode == 256) { // ESCAPE
            cancel();
            return true;
        }
        // Handle enter key in text fields
        if (keyCode == 257) { // ENTER
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Opens this screen from a payload received from the server.
     */
    public static void open(@Nonnull TransportConfigOpenPayload payload) {
        Minecraft.getInstance().setScreen(new TransportConfigScreen(payload));
    }
}
