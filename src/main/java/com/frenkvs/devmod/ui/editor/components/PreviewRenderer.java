package com.frenkvs.devmod.ui.editor.components;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.ScaledCoord;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

import java.util.Objects;

/**
 * Preview renderer for displaying items or entity models.
 * Supports drag-to-rotate interaction for 3D preview.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 2.5 (Left Column - Preview)
 * @see EDITOR_DESIGN_SYSTEM.md Section 4.9 (Preview Component)
 */
public class PreviewRenderer {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS (from Section 2.5)
    // ═══════════════════════════════════════════════════════════════

    private static final int SIZE = UIConstants.PanelDimensions.PREVIEW_SIZE;  // 130x130
    private static final int HINT_HEIGHT = 12;

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

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect previewBounds = ResponsiveLayout.Rect.EMPTY;

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

        // Background
        graphics.fill(x, y, x + size, y + size, UIConstants.Background.INPUT);

        // Border
        int borderColor = dragging ? UIConstants.Border.ACCENT : UIConstants.Border.DEFAULT;
        AxiomRenderer.drawBorder(graphics, x, y, size, size, borderColor);

        // Auto-rotation update
        if (autoRotate && !dragging) {
            rotationY += autoRotateSpeed;
            if (rotationY > 360f) rotationY -= 360f;
        }

        // Render content based on mode
        if (mode == PreviewMode.ENTITY) {
            renderEntityPreview(graphics, x, y, size);
        } else {
            renderItemPreview(graphics, x, y, size);
        }

        // Hint text below preview
        if (showHint && hintText != null) {
            int hintY = y + size + ScaledCoord.scaleDim(2);
            String safeHintText = Objects.requireNonNull(hintText, "hintText cannot be null");
            int hintWidth = font.width(safeHintText);
            int hintX = x + (size - hintWidth) / 2;
            graphics.drawString(font, safeHintText, hintX, hintY, UIConstants.Text.MUTED, false);
        }

        return totalHeight;
    }

    private void renderEntityPreview(GuiGraphics graphics, int x, int y, int size) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Create a preview entity (the player) equipped with the item
        LivingEntity entity = Objects.requireNonNull(mc.player, "player entity cannot be null");

        // Center position
        int centerX = x + size / 2;
        int centerY = y + size - ScaledCoord.scaleDim(10);

        // Scale to fit the preview area
        int scale = ScaledCoord.scaleDim(45);

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
                0.0625f,
                centerX + rotationY * 0.5f,
                centerY + rotationX * 0.5f,
                entity
            );
            Lighting.setupFor3DItems();
        } catch (Exception e) {
            // Fallback to item preview if entity rendering fails
            renderItemPreview(graphics, x, y, size);
        }
    }

    private void renderItemPreview(GuiGraphics graphics, int x, int y, int size) {
        if (item.isEmpty()) {
            // Show placeholder
            var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
            String placeholder = "?";
            int textX = x + (size - font.width(placeholder)) / 2;
            int textY = y + (size - font.lineHeight) / 2;
            graphics.drawString(font, placeholder, textX, textY, UIConstants.Text.MUTED, false);
            return;
        }

        // Use pose stack for rotation
        graphics.pose().pushPose();
        graphics.pose().translate(x + size / 2f, y + size / 2f, 100f);
        Quaternionf rotY = Objects.requireNonNull(new Quaternionf().rotateY((float) Math.toRadians(rotationY)), "Y rotation cannot be null");
        Quaternionf rotX = Objects.requireNonNull(new Quaternionf().rotateX((float) Math.toRadians(rotationX)), "X rotation cannot be null");
        graphics.pose().mulPose(rotY);
        graphics.pose().mulPose(rotX);
        graphics.pose().scale(4f, 4f, 4f);
        graphics.pose().translate(-8f, -8f, 0f);

        // Render the item
        ItemStack safeItem = Objects.requireNonNull(item, "item cannot be null");
        graphics.renderItem(safeItem, 0, 0);

        graphics.pose().popPose();
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

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
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            double deltaX = mouseX - lastMouseX;
            double deltaY = mouseY - lastMouseY;

            rotationY += (float) deltaX * 0.5f;
            rotationX += (float) deltaY * 0.5f;

            // Clamp vertical rotation
            rotationX = Math.max(-45f, Math.min(45f, rotationX));

            // Wrap horizontal rotation
            if (rotationY > 360f) rotationY -= 360f;
            if (rotationY < 0f) rotationY += 360f;

            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (previewBounds.contains(mouseX, mouseY)) {
            // Scroll to rotate
            rotationY += (float) scrollY * 10f;
            if (rotationY > 360f) rotationY -= 360f;
            if (rotationY < 0f) rotationY += 360f;
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
        this.slot = slot;
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
}
