package com.devmod.clone.client.screen;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.clone.block.entity.NeurocellMannequinBlockEntity;
import com.devmod.clone.client.util.PlayerUuidLookup;
import com.devmod.clone.menu.NeurocellMannequinMenu;
import com.devmod.clone.network.MannequinSkinPayload;

/**
 * GUI screen for the Neurocell Mannequin container.
 * Shows 6 equipment slots arranged around a mannequin preview.
 * Includes skin customization via player name input.
 */
public class NeurocellMannequinScreen extends AbstractContainerScreen<NeurocellMannequinMenu> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("devmod", "textures/gui/neurocell_mannequin.png");

    /** Preview entity for rendering in GUI */
    @Nullable
    private ArmorStand previewEntity;

    /** Text field for player name input */
    @Nullable
    private EditBox skinNameField;

    /** Button to apply skin */
    @Nullable
    private Button applySkinButton;

    /** Button to reset to Steve */
    @Nullable
    private Button resetSkinButton;

    /** Status message for skin lookup */
    @Nullable
    private String statusMessage;

    /** Status message color */
    private int statusColor = DesignTokens.Neurocell.STATUS_DEFAULT;

    /** Current lookup future */
    @Nullable
    private CompletableFuture<UUID> lookupFuture;

    public NeurocellMannequinScreen(NeurocellMannequinMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageHeight = 186; // Increased for skin controls
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        // Create preview armor stand
        var mc = minecraft;
        if (mc != null && mc.level != null) {
            ArmorStand entity = new ArmorStand(mc.level, 0, 0, 0);
            entity.setShowArms(true);
            entity.setNoBasePlate(true);
            previewEntity = entity;
        }

        // Skin name input field
        EditBox nameField = new EditBox(
            Objects.requireNonNull(this.font),
            leftPos + 30, topPos + 166,
            80, 14,
            Objects.requireNonNull(Component.translatable("gui.devmod.mannequin.skin_name"))
        );
        nameField.setMaxLength(16);
        nameField.setHint(Objects.requireNonNull(Component.literal("Player name")));
        skinNameField = nameField;
        this.addRenderableWidget(Objects.requireNonNull(skinNameField));

        // Apply skin button
        Button applyBtn = Button.builder(
            Objects.requireNonNull(Component.literal("Set")),
            btn -> applySkin()
        ).bounds(leftPos + 114, topPos + 164, 28, 18).build();
        applySkinButton = applyBtn;
        this.addRenderableWidget(Objects.requireNonNull(applySkinButton));

        // Reset skin button
        Button resetBtn = Button.builder(
            Objects.requireNonNull(Component.literal("Reset")),
            btn -> resetSkin()
        ).bounds(leftPos + 144, topPos + 164, 32, 18).build();
        resetSkinButton = resetBtn;
        this.addRenderableWidget(Objects.requireNonNull(resetSkinButton));

        // Show current skin if set
        UUID currentSkin = menu.getSkinUUID();
        if (currentSkin != null) {
            statusMessage = "Custom skin active";
            statusColor = DesignTokens.Neurocell.STATUS_SUCCESS;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        // Update preview entity equipment
        ArmorStand preview = previewEntity;
        if (preview != null) {
            for (int i = 0; i < 6; i++) {
                ItemStack stack = Objects.requireNonNull(menu.getContainer().getItem(i));
                EquipmentSlot slot = NeurocellMannequinBlockEntity.indexToEquipmentSlot(i);
                preview.setItemSlot(slot, stack);
            }
        }

        // Check if lookup is complete
        CompletableFuture<UUID> future = lookupFuture;
        if (future != null && future.isDone()) {
            try {
                UUID uuid = future.join();
                if (uuid != null) {
                    sendSkinUpdate(uuid);
                    statusMessage = "Skin applied!";
                    statusColor = DesignTokens.Neurocell.STATUS_SUCCESS;
                } else {
                    statusMessage = "Player not found";
                    statusColor = DesignTokens.Neurocell.STATUS_ERROR;
                }
            } catch (Exception e) {
                statusMessage = "Lookup failed";
                statusColor = DesignTokens.Neurocell.STATUS_ERROR;
            }
            lookupFuture = null;
        }
    }

    /**
     * Apply the skin from the entered player name.
     */
    private void applySkin() {
        EditBox nameField = skinNameField;
        if (nameField == null || lookupFuture != null) {
            return;
        }

        String playerName = nameField.getValue().trim();
        if (playerName.isEmpty()) {
            statusMessage = "Enter a name";
            statusColor = DesignTokens.Neurocell.STATUS_WAITING;
            return;
        }

        statusMessage = "Looking up...";
        statusColor = DesignTokens.Neurocell.STATUS_LOADING;

        // Start async lookup
        lookupFuture = PlayerUuidLookup.lookup(playerName);
    }

    /**
     * Reset to default Steve skin.
     */
    private void resetSkin() {
        sendSkinUpdate(null);
        statusMessage = "Reset to Steve";
        statusColor = DesignTokens.Neurocell.STATUS_SUCCESS;
        EditBox nameField = skinNameField;
        if (nameField != null) {
            nameField.setValue("");
        }
    }

    /**
     * Send skin update to server.
     */
    private void sendSkinUpdate(@Nullable UUID skinUUID) {
        BlockPos pos = menu.getBlockPos();
        if (pos != null) {
            PacketDistributor.sendToServer(new MannequinSkinPayload(pos, skinUUID));
            menu.setSkinUUID(skinUUID);
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@Nonnull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Draw background texture
        graphics.blit(Objects.requireNonNull(TEXTURE), leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Draw ghost icons for empty slots
        drawSlotGhosts(graphics);

        // Render mannequin preview in center
        ArmorStand preview = previewEntity;
        if (preview != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics,
                leftPos + 52,          // x1
                topPos + 8,            // y1
                leftPos + 124,         // x2
                topPos + 78,           // y2
                35,                    // scale
                0.0625F,               // yOffset
                mouseX,                // mouseX
                mouseY,                // mouseY
                Objects.requireNonNull(preview)
            );
        }

        // Draw skin control area background
        graphics.fill(leftPos + 28, topPos + 162, leftPos + 148, topPos + 182, DesignTokens.Neurocell.CONTROL_BG);
    }

    /**
     * Draw ghost icons for empty equipment slots.
     */
    private void drawSlotGhosts(GuiGraphics graphics) {
        int ghostColor = DesignTokens.Neurocell.SLOT_GHOST;

        // Armor slots (left column: 8, 8/26/44/62)
        String[] armorLabels = {"H", "C", "L", "B"};
        int[] armorY = {8, 26, 44, 62};

        for (int i = 0; i < 4; i++) {
            ItemStack stack = menu.getContainer().getItem(i);
            if (stack.isEmpty()) {
                // Draw slot background highlight
                graphics.fill(leftPos + 7, topPos + armorY[i] - 1, leftPos + 25, topPos + armorY[i] + 17, ghostColor);

                // Draw label
                var renderFont = Objects.requireNonNull(this.font);
                String label = Objects.requireNonNull(armorLabels[i]);
                int textWidth = UIScaleManager.getScaledStringWidth(renderFont, label);
                UIScaleManager.drawScaledString(graphics, renderFont, label,
                    leftPos + 8 + (16 - textWidth) / 2,
                    topPos + armorY[i] + 4,
                    DesignTokens.Neurocell.SLOT_LABEL, false);
            }
        }

        // Hand slots (right column: 152, 26/44)
        String[] handLabels = {"M", "O"};
        int[] handY = {26, 44};

        for (int i = 0; i < 2; i++) {
            ItemStack stack = menu.getContainer().getItem(4 + i);
            if (stack.isEmpty()) {
                // Draw slot background highlight
                graphics.fill(leftPos + 151, topPos + handY[i] - 1, leftPos + 169, topPos + handY[i] + 17, ghostColor);

                // Draw label
                var renderFont = Objects.requireNonNull(this.font);
                String label = Objects.requireNonNull(handLabels[i]);
                int textWidth = UIScaleManager.getScaledStringWidth(renderFont, label);
                UIScaleManager.drawScaledString(graphics, renderFont, label,
                    leftPos + 152 + (16 - textWidth) / 2,
                    topPos + handY[i] + 4,
                    DesignTokens.Neurocell.SLOT_LABEL, false);
            }
        }
    }

    @Override
    protected void renderLabels(@Nonnull GuiGraphics graphics, int mouseX, int mouseY) {
        var renderFont = Objects.requireNonNull(this.font);

        // Title (centered)
        String titleStr = Objects.requireNonNull(this.title).getString();
        int titleWidth = UIScaleManager.getScaledStringWidth(renderFont, titleStr);
        UIScaleManager.drawScaledString(graphics, renderFont, titleStr, (imageWidth - titleWidth) / 2, this.titleLabelY,
            DesignTokens.Neurocell.LABEL_TEXT, false);

        // Inventory label
        UIScaleManager.drawScaledString(graphics, renderFont, Objects.requireNonNull(this.playerInventoryTitle).getString(),
            this.inventoryLabelX, this.inventoryLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);

        // Slot labels
        UIScaleManager.drawScaledString(graphics, renderFont, Objects.requireNonNull(Component.translatable("gui.devmod.mannequin.armor")).getString(),
            8, 78, DesignTokens.Neurocell.LABEL_TEXT, false);
        UIScaleManager.drawScaledString(graphics, renderFont, Objects.requireNonNull(Component.translatable("gui.devmod.mannequin.hands")).getString(),
            140, 78, DesignTokens.Neurocell.LABEL_TEXT, false);

        // Skin label
        UIScaleManager.drawScaledString(graphics, renderFont, "Skin:", 8, 168, DesignTokens.Neurocell.LABEL_TEXT, false);

        // Status message
        if (statusMessage != null) {
            UIScaleManager.drawScaledString(graphics, renderFont, statusMessage, 8, 152, statusColor, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Allow typing in the edit box
        EditBox nameField = skinNameField;
        if (nameField != null && nameField.isFocused()) {
            if (keyCode == 256) { // Escape
                nameField.setFocused(false);
                return true;
            }
            if (keyCode == 257) { // Enter
                applySkin();
                return true;
            }
            return nameField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        EditBox nameField = skinNameField;
        if (nameField != null && nameField.isFocused()) {
            return nameField.charTyped(c, modifiers);
        }
        return super.charTyped(c, modifiers);
    }
}
