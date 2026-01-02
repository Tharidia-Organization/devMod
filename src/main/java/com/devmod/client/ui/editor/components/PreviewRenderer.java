package com.devmod.client.ui.editor.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.joml.Quaternionf;

import com.mojang.blaze3d.platform.Lighting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.EditorConstants;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.ScaledCoord;

public class PreviewRenderer {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS (from Section 2.5)
    // ═══════════════════════════════════════════════════════════════

    private static final int SIZE = EditorConstants.PREVIEW_SIZE;  // 130x130
    private static final int HINT_HEIGHT = 12;
    private static final int ENTITY_CENTER_Y_OFFSET = 10;
    private static final int ENTITY_SCALE = 45;
    private static final int SLOT_BOX_SIZE = 28;
    private static final int SLOT_BOX_PADDING = 6;
    private static final int SLOT_GROUP_SIZE = 3;
    private static final int SELECTED_SLOT_BG = 0x4020C0FF;
    private static final int PREVIEW_BG = 0xFF111419;
    private static final int PREVIEW_BASE_SHADOW = 0x10101060;
    private static final int BASE_RADIUS_DIVISOR = 6;
    private static final float BASE_CENTER_Y_RATIO = 0.72f;
    private static final float INVENTORY_RENDER_SCALE = 0.0625f;
    private static final float ENTITY_ROTATION_OFFSET = 0.5f;
    private static final float ITEM_PREVIEW_Z = DesignTokens.ZOrder.PANEL;
    private static final float ITEM_PREVIEW_SCALE = 4f;
    private static final float ITEM_PREVIEW_OFFSET = 8f;
    private static final float ROTATION_DRAG_SPEED = 0.5f;
    private static final float ROTATION_SCROLL_STEP = 10f;
    private static final float ROTATION_WRAP = 360f;
    private static final float ROTATION_X_LIMIT = 45f;
    private static final int CIRCLE_ALPHA_MIN = 20;
    private static final int CIRCLE_ALPHA_STEP = 2;

    // ═══════════════════════════════════════════════════════════════
    // PREVIEW MODE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Preview mode - what to display.
     */
    public enum PreviewMode {
        /** Show just the item in 3D */
        ITEM,
        /** Show entity wearing the armor/holding weapon */
        ENTITY
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private PreviewMode mode = PreviewMode.ENTITY;
    private ItemStack item = ItemStack.EMPTY;
    private EquipmentSlot slot = EquipmentSlot.MAINHAND;
    // Uses player inventory directly; no local buffers

    // Rotation state
    private float rotationX = 0f;
    private float rotationY = 0f;
    private boolean dragging = false;
    private double lastMouseX = 0;
    private double lastMouseY = 0;

    // Auto-rotation
    private boolean autoRotate = false;
    private float autoRotateSpeed = 0.5f;

    // Display options
    private boolean showHint = true;
    private String hintText = "Drag to rotate";
    @Nullable
    private Consumer<EquipmentSlot> onSlotClick;

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect previewBounds = ResponsiveLayout.Rect.EMPTY;
    private final List<SlotBox> slotBoxes = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public PreviewRenderer() {}

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    public PreviewRenderer mode(PreviewMode mode) {
        this.mode = mode;
        return this;
    }

    public PreviewRenderer item(ItemStack item) {
        this.item = item != null ? item : ItemStack.EMPTY;
        return this;
    }

    public PreviewRenderer slot(EquipmentSlot slot) {
        this.slot = slot;
        return this;
    }

    public PreviewRenderer showHint(boolean show) {
        this.showHint = show;
        return this;
    }

    public PreviewRenderer hintText(String text) {
        this.hintText = text;
        return this;
    }

    public PreviewRenderer onSlotClick(Consumer<EquipmentSlot> callback) {
        this.onSlotClick = callback;
        return this;
    }

    public PreviewRenderer autoRotate(boolean auto) {
        this.autoRotate = auto;
        return this;
    }

    public PreviewRenderer autoRotateSpeed(float speed) {
        this.autoRotateSpeed = speed;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the preview at the given position.
     * @return The total height consumed (preview + hint)
     */
    public int render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        // Calculate total height
        int size = ScaledCoord.scaleDim(SIZE);
        int hintHeight = showHint ? ScaledCoord.scaleDim(HINT_HEIGHT) : 0;
        int totalHeight = size + hintHeight;
        this.bounds = new ResponsiveLayout.Rect(x, y, size, totalHeight);
        this.previewBounds = new ResponsiveLayout.Rect(x, y, size, size);

        // Background layers (dark + glow)
        renderBackdrop(graphics, x, y, size);

        // Auto-rotation update
        if (autoRotate && !dragging) {
            rotationY += autoRotateSpeed;
            if (rotationY > ROTATION_WRAP) rotationY -= ROTATION_WRAP;
        }

        slotBoxes.clear();
        renderSlots(graphics, font, x, y, size, mouseX, mouseY);

        // Render content based on mode
        if (mode == PreviewMode.ENTITY) {
            renderEntityPreview(graphics, x, y, size);
        } else {
            renderItemPreview(graphics, x, y, size);
        }

        // Hint text below preview
        if (showHint && hintText != null) {
            int hintY = y + size + ScaledCoord.scaleDim(DesignTokens.Spacing.XS);
            String safeHintText = Objects.requireNonNull(hintText, "hintText cannot be null");
            int hintWidth = font.width(safeHintText);
            int hintX = x + (size - hintWidth) / 2;
            graphics.drawString(font, safeHintText, hintX, hintY, DesignTokens.Text.MUTED(), false);
        }

        // Render carried stack (from vanilla "carried" slot) following the cursor
        var player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                graphics.renderItem(carried, mouseX - ScaledCoord.scaleDim(DesignTokens.Spacing.SM),
                    mouseY - ScaledCoord.scaleDim(DesignTokens.Spacing.LG));
            }
        }

        return totalHeight;
    }

    private void renderBackdrop(GuiGraphics graphics, int x, int y, int size) {
        // Base scuro uniforme, niente glow acceso
        graphics.fill(x, y, x + size, y + size, PREVIEW_BG);

        int centerX = x + size / 2;
        int baseRadius = size / BASE_RADIUS_DIVISOR;
        int baseCenterY = y + (int) (size * BASE_CENTER_Y_RATIO);

        // Nessun alone acceso; solo un leggero scurimento per appoggio
        drawFilledCircle(graphics, centerX, baseCenterY, baseRadius, PREVIEW_BASE_SHADOW);
    }

    private void renderEntityPreview(GuiGraphics graphics, int x, int y, int size) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Create a preview entity (the player) equipped with the item
        LivingEntity entity = Objects.requireNonNull(mc.player, "player entity cannot be null");

        // Center position
        int centerX = x + size / 2;
        int centerY = y + size - ScaledCoord.scaleDim(ENTITY_CENTER_Y_OFFSET);

        // Scale to fit the preview area
        int scale = ScaledCoord.scaleDim(ENTITY_SCALE);

        // Apply rotation as quaternion
        Quaternionf rotation = new Quaternionf();
        rotation.rotateY((float) Math.toRadians(rotationY));
        rotation.rotateX((float) Math.toRadians(rotationX));

        // Render the entity
        try {
            Lighting.setupForEntityInInventory();
            GuiGraphics safeGraphics = Objects.requireNonNull(graphics, "graphics cannot be null");
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                safeGraphics,
                x, y, x + size, y + size,
                scale,
                INVENTORY_RENDER_SCALE,
                centerX + rotationY * ENTITY_ROTATION_OFFSET,
                centerY + rotationX * ENTITY_ROTATION_OFFSET,
                entity
            );
            Lighting.setupFor3DItems();
        } catch (Exception e) {
            // Fallback to item preview if entity rendering fails
            renderItemPreview(graphics, x, y, size);
        }
    }

    private void renderSlots(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                             int x, int y, int size, int mouseX, int mouseY) {
        EquipmentSlot[] slots = {
            EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.OFFHAND,
            EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.FEET
        };

        int slotSize = ScaledCoord.scaleDim(SLOT_BOX_SIZE);
        int gap = ScaledCoord.scaleDim(DesignTokens.Spacing.LG);
        int leftX = x + ScaledCoord.scaleDim(SLOT_BOX_PADDING);
        int rightX = x + size - slotSize - ScaledCoord.scaleDim(SLOT_BOX_PADDING);
        int startY = y + ScaledCoord.scaleDim(SLOT_BOX_PADDING);

        LivingEntity entity = Minecraft.getInstance().player;

        for (int i = 0; i < slots.length; i++) {
            EquipmentSlot slot = slots[i];
            boolean leftSide = i < SLOT_GROUP_SIZE;
            int slotX = leftSide ? leftX : rightX;
            int slotY = startY + (i % SLOT_GROUP_SIZE) * (slotSize + gap);

            ResponsiveLayout.Rect rect = new ResponsiveLayout.Rect(slotX, slotY, slotSize, slotSize);
            slotBoxes.add(new SlotBox(slot, rect));

            boolean hovered = rect.contains(mouseX, mouseY);
            boolean selected = slot == this.slot;

            int bg = selected ? SELECTED_SLOT_BG : (hovered ? DesignTokens.Background.HOVER() : DesignTokens.Background.INPUT());
            graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), bg);

            int border = selected ? DesignTokens.Accent.CYAN() : (hovered ? DesignTokens.Border.HOVER() : DesignTokens.Border.DEFAULT());
            AxiomRenderer.drawBorder(graphics, rect.x(), rect.y(), rect.width(), rect.height(), border);

            ItemStack stack = ItemStack.EMPTY;
            if (entity != null) {
                stack = entity.getItemBySlot(slot);
            }

            if (!stack.isEmpty()) {
                int pad = ScaledCoord.scaleDim(DesignTokens.Spacing.SM);
                ItemStack safeStack = Objects.requireNonNull(stack, "stack cannot be null");
                graphics.renderItem(safeStack, rect.x() + pad, rect.y() + pad);
                // Tooltip-like name
                if (hovered) {
                    String name = safeStack.getHoverName().getString();
                    graphics.drawString(Objects.requireNonNull(font, "font cannot be null"), name,
                        rect.x() + rect.width() + ScaledCoord.scaleDim(DesignTokens.Spacing.SM),
                        rect.y(), DesignTokens.Text.PRIMARY(), false);
                }
            } else {
                String glyph = Objects.requireNonNull(placeholderFor(slot), "placeholder cannot be null");
                var safeFont = Objects.requireNonNull(font, "font cannot be null");
                int textColor = selected ? DesignTokens.Text.PRIMARY() : DesignTokens.Text.MUTED();
                int textX = rect.x() + (slotSize - safeFont.width(glyph)) / 2;
                int textY = rect.y() + (slotSize - safeFont.lineHeight) / 2;
                graphics.drawString(safeFont, glyph, textX, textY, textColor, false);
            }
        }
    }

    private void renderItemPreview(GuiGraphics graphics, int x, int y, int size) {
        if (item.isEmpty()) {
            // Show placeholder
            var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
            String placeholder = "?";
            int textX = x + (size - font.width(placeholder)) / 2;
            int textY = y + (size - font.lineHeight) / 2;
            graphics.drawString(font, placeholder, textX, textY, DesignTokens.Text.MUTED(), false);
            return;
        }

        // Use pose stack for rotation
        graphics.pose().pushPose();
        graphics.pose().translate(x + size / 2f, y + size / 2f, ITEM_PREVIEW_Z);
        Quaternionf rotY = Objects.requireNonNull(new Quaternionf().rotateY((float) Math.toRadians(rotationY)), "Y rotation cannot be null");
        Quaternionf rotX = Objects.requireNonNull(new Quaternionf().rotateX((float) Math.toRadians(rotationX)), "X rotation cannot be null");
        graphics.pose().mulPose(rotY);
        graphics.pose().mulPose(rotX);
        graphics.pose().scale(ITEM_PREVIEW_SCALE, ITEM_PREVIEW_SCALE, ITEM_PREVIEW_SCALE);
        graphics.pose().translate(-ITEM_PREVIEW_OFFSET, -ITEM_PREVIEW_OFFSET, 0f);

        // Render the item
        ItemStack safeItem = Objects.requireNonNull(item, "item cannot be null");
        graphics.renderItem(safeItem, 0, 0);

        graphics.pose().popPose();
    }

    private void drawFilledCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int r = radius; r >= 0; r--) {
            int a = Math.max(CIRCLE_ALPHA_MIN, ((color >> 24) & 0xFF) - (radius - r) * CIRCLE_ALPHA_STEP);
            int rgb = (a << 24) | (color & 0x00FFFFFF);
            graphics.fill(cx - r, cy - r, cx + r, cy + r, rgb);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Slot click
        for (SlotBox box : slotBoxes) {
            if (box.bounds.contains(mouseX, mouseY)) {
                EquipmentSlot clickedSlot = Objects.requireNonNull(box.slot, "slot cannot be null");
                this.slot = clickedSlot;
                if (onSlotClick != null) {
                    onSlotClick.accept(clickedSlot);
                }
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    ItemStack carried = Objects.requireNonNull(player.containerMenu.getCarried(), "carried stack cannot be null");
                    ItemStack current = Objects.requireNonNull(player.getItemBySlot(clickedSlot), "slot stack cannot be null");
                    if (carried.isEmpty()) {
                        // pick up
                        player.containerMenu.setCarried(Objects.requireNonNull(current.copy(), "copy cannot be null"));
                        player.setItemSlot(clickedSlot, Objects.requireNonNull(ItemStack.EMPTY, "empty cannot be null"));
                    } else {
                        // place/swap
                        player.setItemSlot(clickedSlot, Objects.requireNonNull(carried.copy(), "copy cannot be null"));
                        player.containerMenu.setCarried(Objects.requireNonNull(current.copy(), "copy cannot be null"));
                    }
                }
                return true;
            }
        }

        if (previewBounds.contains(mouseX, mouseY)) {
            dragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            return true;
        }

        // Drop carried stack into equip slots on release
        if (button == 0) {
            for (SlotBox box : slotBoxes) {
                if (box.bounds.contains(mouseX, mouseY)) {
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        EquipmentSlot targetSlot = Objects.requireNonNull(box.slot, "slot cannot be null");
                        ItemStack carried = Objects.requireNonNull(player.containerMenu.getCarried(), "carried stack cannot be null");
                        ItemStack current = Objects.requireNonNull(player.getItemBySlot(targetSlot), "slot stack cannot be null");
                        if (!carried.isEmpty()) {
                            player.setItemSlot(targetSlot, Objects.requireNonNull(carried.copy(), "copy cannot be null"));
                            player.containerMenu.setCarried(Objects.requireNonNull(current.copy(), "copy cannot be null"));
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            double deltaX = mouseX - lastMouseX;
            double deltaY = mouseY - lastMouseY;

            rotationY += (float) deltaX * ROTATION_DRAG_SPEED;
            rotationX += (float) deltaY * ROTATION_DRAG_SPEED;

            // Clamp vertical rotation
            rotationX = Math.max(-ROTATION_X_LIMIT, Math.min(ROTATION_X_LIMIT, rotationX));

            // Wrap horizontal rotation
            if (rotationY > ROTATION_WRAP) rotationY -= ROTATION_WRAP;
            if (rotationY < 0f) rotationY += ROTATION_WRAP;

            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        // Allow drag visual with carried stack
        var player = Minecraft.getInstance().player;
        if (player != null && !player.containerMenu.getCarried().isEmpty() && previewBounds.contains(mouseX, mouseY)) {
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (previewBounds.contains(mouseX, mouseY)) {
            // Scroll to rotate
            rotationY += (float) scrollY * ROTATION_SCROLL_STEP;
            if (rotationY > ROTATION_WRAP) rotationY -= ROTATION_WRAP;
            if (rotationY < 0f) rotationY += ROTATION_WRAP;
            return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & SETTERS
    // ═══════════════════════════════════════════════════════════════

    public PreviewMode getMode() {
        return mode;
    }

    public void setMode(PreviewMode mode) {
        this.mode = mode;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item != null ? item : ItemStack.EMPTY;
    }

    public EquipmentSlot getSlot() {
        return slot;
    }

    public void setSlot(EquipmentSlot slot) {
        this.slot = Objects.requireNonNull(slot, "slot cannot be null");
        var player = Minecraft.getInstance().player;
        if (player != null) {
            EquipmentSlot safeSlot = Objects.requireNonNull(this.slot, "slot cannot be null");
            this.item = Objects.requireNonNull(player.getItemBySlot(safeSlot), "slot stack cannot be null");
        }
    }

    public float getRotationX() {
        return rotationX;
    }

    public float getRotationY() {
        return rotationY;
    }

    public void setRotation(float x, float y) {
        this.rotationX = x;
        this.rotationY = y;
    }

    public void resetRotation() {
        this.rotationX = 0f;
        this.rotationY = 0f;
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean isAutoRotate() {
        return autoRotate;
    }

    public void setAutoRotate(boolean autoRotate) {
        this.autoRotate = autoRotate;
    }

    public int getSize() {
        return SIZE;
    }

    public int getHeight() {
        return SIZE + (showHint ? HINT_HEIGHT : 0);
    }

    public ResponsiveLayout.Rect getBounds() {
        return bounds;
    }

    public ResponsiveLayout.Rect getPreviewBounds() {
        return previewBounds;
    }

    private String placeholderFor(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "H";
            case CHEST -> "C";
            case LEGS -> "L";
            case FEET -> "F";
            case MAINHAND -> "M";
            case OFFHAND -> "O";
            default -> "?";
        };
    }

    private record SlotBox(EquipmentSlot slot, ResponsiveLayout.Rect bounds) {}
}
