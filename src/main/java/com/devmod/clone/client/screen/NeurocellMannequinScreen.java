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
 *
 * Layout:
 * - Top: Title
 * - Left column: 4 armor slots (Head, Chest, Legs, Feet)
 * - Center: Mannequin preview
 * - Right column: 2 hand slots (Main, Off)
 * - Below preview: Skin customization section
 * - Bottom: Player inventory
 */
public class NeurocellMannequinScreen extends AbstractContainerScreen<NeurocellMannequinMenu> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("devmod", "textures/gui/neurocell_mannequin.png");

    // Preview area bounds (relative to GUI left/top)
    private static final int PREVIEW_LEFT = 40;
    private static final int PREVIEW_TOP = 14;
    private static final int PREVIEW_RIGHT = 136;
    private static final int PREVIEW_BOTTOM = 80;
    private static final int PREVIEW_SCALE = 32;
    private static final float PREVIEW_Y_OFFSET = 0.0625F;

    /** Preview entity for rendering in GUI */
    @Nullable
    private ArmorStand previewEntity;

    /** Text field for player name input */
    @Nullable
    private EditBox skinNameField;

    /** Button to apply skin */
    @Nullable
    private Button applySkinButton;

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
        this.imageWidth = 176;
        this.imageHeight = 190;
        // Layout: Equipment(0-80), SkinControls(82-100), InventoryLabel(110), Inventory(112-166), Hotbar(170-188)
        this.inventoryLabelY = 110;
        this.titleLabelY = 6;
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

        // Skin controls positioned below the mannequin preview area
        // Calculate layout based on control area bounds for symmetrical padding
        int skinControlY = topPos + 82;
        int controlLeftEdge = leftPos + 7;
        int controlRightEdge = leftPos + 169;
        int labelWidth = 36;      // Space for "Skin:" label
        int buttonWidth = 40;
        int buttonGap = 2;
        int rightPadding = 4;

        int fieldX = controlLeftEdge + labelWidth;
        int fieldWidth = controlRightEdge - fieldX - buttonGap - buttonWidth - rightPadding;

        // Skin name input field - dynamically sized to fit available space
        EditBox nameField = new EditBox(
            Objects.requireNonNull(this.font),
            fieldX, skinControlY,
            fieldWidth, 16,
            Objects.requireNonNull(Component.translatable("gui.devmod.mannequin.skin_name"))
        );
        nameField.setMaxLength(16);
        nameField.setHint(Objects.requireNonNull(Component.literal("Player name...")));
        nameField.setBordered(true);
        skinNameField = nameField;
        this.addRenderableWidget(Objects.requireNonNull(skinNameField));

        // Apply skin button - aligned with right padding
        int applyX = fieldX + fieldWidth + buttonGap;
        Button applyBtn = Button.builder(
            Objects.requireNonNull(Component.translatable("gui.devmod.mannequin.apply")),
            btn -> applySkin()
        ).bounds(applyX, skinControlY, buttonWidth, 16).build();
        applySkinButton = applyBtn;
        this.addRenderableWidget(Objects.requireNonNull(applySkinButton));

        // Show current skin status
        UUID currentSkin = menu.getSkinUUID();
        if (currentSkin != null) {
            statusMessage = Component.translatable("gui.devmod.mannequin.skin_active").getString();
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
                    statusMessage = Component.translatable("gui.devmod.mannequin.skin_applied").getString();
                    statusColor = DesignTokens.Neurocell.STATUS_SUCCESS;
                } else {
                    statusMessage = Component.translatable("gui.devmod.mannequin.player_not_found").getString();
                    statusColor = DesignTokens.Neurocell.STATUS_ERROR;
                }
            } catch (Exception e) {
                statusMessage = Component.translatable("gui.devmod.mannequin.lookup_failed").getString();
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
            // If empty, reset to default
            resetSkin();
            return;
        }

        statusMessage = Component.translatable("gui.devmod.mannequin.looking_up").getString();
        statusColor = DesignTokens.Neurocell.STATUS_LOADING;

        // Start async lookup
        lookupFuture = PlayerUuidLookup.lookup(playerName);
    }

    /**
     * Reset to default Steve skin.
     */
    private void resetSkin() {
        sendSkinUpdate(null);
        statusMessage = Component.translatable("gui.devmod.mannequin.skin_reset").getString();
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
        int baseTextureHeight = 186;
        graphics.blit(Objects.requireNonNull(TEXTURE), leftPos, topPos, 0, 0, imageWidth, baseTextureHeight);
        if (imageHeight > baseTextureHeight) {
            int extra = imageHeight - baseTextureHeight;
            graphics.blit(Objects.requireNonNull(TEXTURE), leftPos, topPos + baseTextureHeight,
                0, baseTextureHeight - extra, imageWidth, extra);
        }

        // Draw equipment section backgrounds
        drawEquipmentBackgrounds(graphics);

        // Draw ghost labels for empty slots
        drawSlotGhosts(graphics);

        // Render mannequin preview in center
        ArmorStand preview = previewEntity;
        if (preview != null) {
            // Preview area: centered between armor and hand slots
            int previewX1 = leftPos + PREVIEW_LEFT;
            int previewY1 = topPos + PREVIEW_TOP;
            int previewX2 = leftPos + PREVIEW_RIGHT;
            int previewY2 = topPos + PREVIEW_BOTTOM;

            InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics,
                previewX1, previewY1,
                previewX2, previewY2,
                PREVIEW_SCALE,
                PREVIEW_Y_OFFSET,
                mouseX, mouseY,
                Objects.requireNonNull(preview)
            );
        }

        // Draw skin control area background (consistent sizing)
        int skinBgX = leftPos + 7;
        int skinBgY = topPos + 80;
        int skinBgWidth = 162;
        int skinBgHeight = 20;
        graphics.fill(skinBgX, skinBgY, skinBgX + skinBgWidth, skinBgY + skinBgHeight,
            DesignTokens.Neurocell.CONTROL_BG);
        graphics.renderOutline(skinBgX, skinBgY, skinBgWidth, skinBgHeight,
            DesignTokens.Neurocell.CONTROL_BORDER);
    }

    /**
     * Draw subtle backgrounds for equipment slot areas.
     */
    private void drawEquipmentBackgrounds(GuiGraphics graphics) {
        int slotBg = DesignTokens.Neurocell.SLOT_BG;

        // Left armor column background (4 slots at x=8, y=8/26/44/62, each 16x16)
        // Allineato con slot reali: x=8 to x=24, y=8 to y=78
        graphics.fill(leftPos + 8, topPos + 8, leftPos + 24, topPos + 78, slotBg);

        // Right hands column background (2 slots at x=152, y=26/44, each 16x16)
        // Allineato: x=152 to x=168, y=26 to y=60
        graphics.fill(leftPos + 152, topPos + 26, leftPos + 168, topPos + 60, slotBg);
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
                // Ghost highlight 16x16 aligned with slot at x=8
                graphics.fill(leftPos + 8, topPos + armorY[i], leftPos + 24, topPos + armorY[i] + 16, ghostColor);

                // Draw label centered in slot using UIScaleManager
                var renderFont = Objects.requireNonNull(this.font);
                String label = Objects.requireNonNull(armorLabels[i]);
                int textWidth = renderFont.width(label);
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
                // Ghost highlight 16x16 aligned with slot at x=152
                graphics.fill(leftPos + 152, topPos + handY[i], leftPos + 168, topPos + handY[i] + 16, ghostColor);

                // Draw label centered in slot using UIScaleManager
                var renderFont = Objects.requireNonNull(this.font);
                String label = Objects.requireNonNull(handLabels[i]);
                int textWidth = renderFont.width(label);
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

        // Title (centered at top) using UIScaleManager for consistent scaling
        String titleStr = Objects.requireNonNull(this.title).getString();
        int titleWidth = renderFont.width(titleStr);
        UIScaleManager.drawScaledString(graphics, renderFont, titleStr,
            (imageWidth - titleWidth) / 2, this.titleLabelY,
            DesignTokens.Neurocell.LABEL_TEXT, false);

        // Inventory label
        UIScaleManager.drawScaledString(graphics, renderFont,
            Objects.requireNonNull(this.playerInventoryTitle).getString(),
            this.inventoryLabelX, this.inventoryLabelY, DesignTokens.Neurocell.LABEL_TEXT, false);

        // Skin label OR status message (same line, y=85)
        String msg = statusMessage;
        if (msg != null && !msg.isEmpty()) {
            // Show status message with truncation
            String displayMsg = msg;
            int maxWidth = 36; // Space for "Skin:" label area
            while (renderFont.width(displayMsg) > maxWidth && displayMsg.length() > 3) {
                displayMsg = displayMsg.substring(0, displayMsg.length() - 1);
            }
            if (displayMsg.length() < msg.length()) {
                displayMsg = displayMsg + "..";
            }
            UIScaleManager.drawScaledString(graphics, renderFont, displayMsg, 8, 85, statusColor, false);
        } else {
            // Show skin label
            String skinLabel = Component.translatable("gui.devmod.mannequin.skin").getString();
            UIScaleManager.drawScaledString(graphics, renderFont, skinLabel, 8, 85,
                DesignTokens.Neurocell.LABEL_TEXT, false);
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
