package com.devmod.client.npc;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.npc.data.NpcAppearance;
import com.devmod.npc.data.NpcBehavior;
import com.devmod.npc.data.NpcConfiguration;
import com.devmod.npc.network.SaveNpcConfigPayload;

/**
 * Client screen for configuring NPC properties.
 * Allows setting name, skin, behavior, and appearance options.
 */
@OnlyIn(Dist.CLIENT)
public class NpcConfigScreen extends Screen {

    // === Colors ===
    private static final int COLOR_PANEL_BG = DesignTokens.Panel.BG;
    private static final int COLOR_BORDER = DesignTokens.Stroke.DEFAULT;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_LABEL = DesignTokens.Text.SECONDARY;

    // === Dimensions ===
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 340;
    private static final int PADDING = DesignTokens.Spacing.LG;
    private static final int ROW_HEIGHT = 24;
    private static final int FIELD_WIDTH = 180;
    private static final int BUTTON_WIDTH = DesignTokens.Size.BUTTON_WIDTH_MEDIUM;
    private static final int BUTTON_HEIGHT = EditorButton.Size.MEDIUM.height();

    // === Data ===
    private final NpcConfiguration originalConfig;
    @Nullable private final InteractionHand hand;
    @Nullable private final UUID existingNpcId;

    // === Widgets ===
    @Nullable private EditBox nameField;
    @Nullable private EditBox skinNameField;
    @Nullable private EditBox dialogSetIdField;
    @Nullable private EditorButton floatingToggle;
    @Nullable private EditorButton lookAtPlayerToggle;
    @Nullable private EditorButton invulnerableToggle;
    @Nullable private EditorButton particlesToggle;
    @Nullable private EditorButton glowToggle;
    @Nullable private EditorButton confirmButton;
    @Nullable private EditorButton cancelButton;

    public NpcConfigScreen(
        @Nonnull NpcConfiguration config,
        @Nullable InteractionHand hand,
        @Nullable UUID existingNpcId
    ) {
        super(Component.translatable("gui.devmod.npc.config.title"));
        this.originalConfig = Objects.requireNonNull(config, "config");
        this.hand = hand;
        this.existingNpcId = existingNpcId;
    }

    @Override
    protected void init() {
        super.init();

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        int contentX = panelX + PADDING;
        int contentY = panelY + PADDING + 20; // +20 for title
        int labelWidth = 100;
        int fieldX = contentX + labelWidth;

        // Display Name
        nameField = new EditBox(font, fieldX, contentY, FIELD_WIDTH, 18,
            Component.translatable("gui.devmod.npc.config.name"));
        nameField.setMaxLength(NpcConfiguration.MAX_DISPLAY_NAME_LENGTH);
        nameField.setValue(originalConfig.displayName());
        nameField.setBordered(false);
        nameField.setTextColor(DesignTokens.Text.PRIMARY);
        nameField.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(nameField);
        contentY += ROW_HEIGHT;

        // Skin Player Name
        skinNameField = new EditBox(font, fieldX, contentY, FIELD_WIDTH, 18,
            Component.translatable("gui.devmod.npc.config.skin"));
        skinNameField.setMaxLength(NpcConfiguration.MAX_SKIN_NAME_LENGTH);
        skinNameField.setValue(originalConfig.skinPlayerName() != null ? originalConfig.skinPlayerName() : "");
        skinNameField.setBordered(false);
        skinNameField.setTextColor(DesignTokens.Text.PRIMARY);
        skinNameField.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(skinNameField);
        contentY += ROW_HEIGHT;

        // Dialog Set ID
        dialogSetIdField = new EditBox(font, fieldX, contentY, FIELD_WIDTH, 18,
            Component.translatable("gui.devmod.npc.config.dialog"));
        dialogSetIdField.setMaxLength(NpcConfiguration.MAX_DIALOG_SET_ID_LENGTH);
        dialogSetIdField.setValue(originalConfig.dialogSetId() != null ? originalConfig.dialogSetId() : "");
        dialogSetIdField.setBordered(false);
        dialogSetIdField.setTextColor(DesignTokens.Text.PRIMARY);
        dialogSetIdField.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(dialogSetIdField);
        contentY += ROW_HEIGHT + 8;

        // Behavior section
        NpcBehavior behavior = originalConfig.behavior();
        int toggleWidth = FIELD_WIDTH + labelWidth;

        floatingToggle = EditorButton.builder("npc-floating",
                Component.translatable("gui.devmod.npc.config.floating").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .toggleable(true)
            .toggled(behavior.floating())
            .build();
        addRenderableWidget(new EditorButtonWidget(floatingToggle, contentX, contentY, toggleWidth, BUTTON_HEIGHT));
        contentY += ROW_HEIGHT;

        lookAtPlayerToggle = EditorButton.builder("npc-look-at-player",
                Component.translatable("gui.devmod.npc.config.look_at_player").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .toggleable(true)
            .toggled(behavior.lookAtPlayer())
            .build();
        addRenderableWidget(new EditorButtonWidget(lookAtPlayerToggle, contentX, contentY, toggleWidth, BUTTON_HEIGHT));
        contentY += ROW_HEIGHT;

        invulnerableToggle = EditorButton.builder("npc-invulnerable",
                Component.translatable("gui.devmod.npc.config.invulnerable").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .toggleable(true)
            .toggled(behavior.invulnerable())
            .build();
        addRenderableWidget(new EditorButtonWidget(invulnerableToggle, contentX, contentY, toggleWidth, BUTTON_HEIGHT));
        contentY += ROW_HEIGHT + 8;

        // Appearance section
        NpcAppearance appearance = originalConfig.appearance();
        particlesToggle = EditorButton.builder("npc-particles",
                Component.translatable("gui.devmod.npc.config.particles").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .toggleable(true)
            .toggled(appearance.particlesEnabled())
            .build();
        addRenderableWidget(new EditorButtonWidget(particlesToggle, contentX, contentY, toggleWidth, BUTTON_HEIGHT));
        contentY += ROW_HEIGHT;

        glowToggle = EditorButton.builder("npc-glow",
                Component.translatable("gui.devmod.npc.config.glow").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .toggleable(true)
            .toggled(appearance.glowEffect())
            .build();
        addRenderableWidget(new EditorButtonWidget(glowToggle, contentX, contentY, toggleWidth, BUTTON_HEIGHT));
        contentY += ROW_HEIGHT + 16;

        // Buttons
        int buttonY = panelY + PANEL_HEIGHT - PADDING - BUTTON_HEIGHT;
        int confirmX = panelX + PANEL_WIDTH / 2 - BUTTON_WIDTH - 4;
        int cancelX = panelX + PANEL_WIDTH / 2 + 4;

        confirmButton = EditorButton.builder("npc_confirm", Component.translatable("gui.devmod.confirm").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onConfirm)
            .build();
        addRenderableWidget(new EditorButtonWidget(confirmButton, confirmX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT));

        cancelButton = EditorButton.builder("npc_cancel", Component.translatable("gui.devmod.cancel").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
        addRenderableWidget(new EditorButtonWidget(cancelButton, cancelX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        // Draw panel background
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_PANEL_BG);

        // Draw border
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, COLOR_BORDER);
        graphics.fill(panelX, panelY + PANEL_HEIGHT - 1, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_BORDER);
        graphics.fill(panelX, panelY, panelX + 1, panelY + PANEL_HEIGHT, COLOR_BORDER);
        graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_BORDER);

        // Draw title
        graphics.drawCenteredString(font, title, width / 2, panelY + PADDING, COLOR_TEXT);

        // Draw labels
        int contentX = panelX + PADDING;
        int contentY = panelY + PADDING + 20;

        graphics.drawString(font, Component.translatable("gui.devmod.npc.config.name"), contentX, contentY + 5, COLOR_LABEL);
        contentY += ROW_HEIGHT;

        graphics.drawString(font, Component.translatable("gui.devmod.npc.config.skin"), contentX, contentY + 5, COLOR_LABEL);
        contentY += ROW_HEIGHT;

        graphics.drawString(font, Component.translatable("gui.devmod.npc.config.dialog"), contentX, contentY + 5, COLOR_LABEL);
        contentY += ROW_HEIGHT + 8;

        // Section headers
        graphics.drawString(font, Component.translatable("gui.devmod.npc.config.behavior")
            .withStyle(s -> s.withBold(true)), contentX, contentY - 2, COLOR_TEXT);

        renderInputBackgrounds(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderInputBackgrounds(GuiGraphics graphics) {
        if (nameField != null) {
            AxiomRenderer.drawInputBackground(graphics, nameField.getX(), nameField.getY(), nameField.getWidth(),
                nameField.getHeight(), nameField.isFocused());
        }
        if (skinNameField != null) {
            AxiomRenderer.drawInputBackground(graphics, skinNameField.getX(), skinNameField.getY(), skinNameField.getWidth(),
                skinNameField.getHeight(), skinNameField.isFocused());
        }
        if (dialogSetIdField != null) {
            AxiomRenderer.drawInputBackground(graphics, dialogSetIdField.getX(), dialogSetIdField.getY(),
                dialogSetIdField.getWidth(), dialogSetIdField.getHeight(), dialogSetIdField.isFocused());
        }
    }

    private void onConfirm() {
        if (nameField == null
            || skinNameField == null
            || dialogSetIdField == null
            || floatingToggle == null
            || lookAtPlayerToggle == null
            || invulnerableToggle == null
            || particlesToggle == null
            || glowToggle == null) {
            return;
        }
        // Build new configuration
        NpcBehavior newBehavior = new NpcBehavior(
            floatingToggle.isToggled(),
            originalConfig.behavior().floatAmplitude(),
            originalConfig.behavior().floatSpeed(),
            lookAtPlayerToggle.isToggled(),
            invulnerableToggle.isToggled()
        );

        NpcAppearance newAppearance = new NpcAppearance(
            particlesToggle.isToggled(),
            originalConfig.appearance().particleTypeId(),
            originalConfig.appearance().particleInterval(),
            glowToggle.isToggled()
        );

        // Parse skin UUID from name if possible
        UUID skinUuid = originalConfig.skinPlayerUUID();
        String skinName = skinNameField.getValue().trim();

        NpcConfiguration newConfig = new NpcConfiguration(
            originalConfig.id(),
            nameField.getValue().trim(),
            skinUuid,
            skinName.isEmpty() ? null : skinName,
            newBehavior,
            newAppearance,
            dialogSetIdField.getValue().trim().isEmpty() ? null : dialogSetIdField.getValue().trim(),
            originalConfig.ownerUUID(),
            originalConfig.spawnPosition(),
            originalConfig.dimension(),
            originalConfig.createdAt(),
            System.currentTimeMillis(),
            originalConfig.revision() + 1
        );

        // Send to server
        PacketDistributor.sendToServer(new SaveNpcConfigPayload(
            newConfig,
            hand,
            existingNpcId,
            originalConfig.revision()
        ));

        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
