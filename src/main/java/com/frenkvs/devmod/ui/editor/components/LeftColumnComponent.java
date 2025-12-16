package com.frenkvs.devmod.ui.editor.components;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.ScaledCoord;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

import java.util.function.Consumer;

/**
 * Left column containing preview, slot selector, and item info.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 2.5 (Left Column)
 */
public class LeftColumnComponent {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS (from Section 2.5)
    // ═══════════════════════════════════════════════════════════════

    // Component positions (relative to left column origin)
    private static final int PREVIEW_Y = 20;
    private static final int SLOT_SELECTOR_X = 5;
    private static final int SLOT_SELECTOR_Y = 170;
    private static final int ITEM_INFO_X = 5;
    private static final int ITEM_INFO_Y = 260;
    private static final int ARMOR_CARD_HEIGHT = 46;

    // ═══════════════════════════════════════════════════════════════
    // COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    private final PreviewRenderer preview;
    private final SlotSelector slotSelector;
    private final ItemInfoPanel itemInfo;
    private ResponsiveLayout.Rect armorCardBounds = ResponsiveLayout.Rect.EMPTY;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;

    // Callbacks
    private Consumer<SlotSelector.SlotInfo> onSlotSelect;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public LeftColumnComponent() {
        this.preview = new PreviewRenderer();
        this.slotSelector = new SlotSelector();
        this.itemInfo = new ItemInfoPanel();

        // Wire up slot selector callback
        slotSelector.onSelect(slot -> {
            preview.setSlot(slot.slot());
            if (onSlotSelect != null) {
                onSlotSelect.accept(slot);
            }
        });
        preview.onSlotClick(slotSelector::selectSlot);
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Set the slot type (ARMOR or WEAPON).
     */
    public LeftColumnComponent slotType(SlotSelector.SlotType type) {
        slotSelector.setType(type);
        return this;
    }

    /**
     * Set the current item in a slot.
     */
    public LeftColumnComponent setSlotItem(EquipmentSlot slot, ItemStack item) {
        slotSelector.setSlotItem(slot, item);
        return this;
    }

    /**
     * Set the item being edited.
     */
    public LeftColumnComponent item(ItemStack item) {
        itemInfo.item(item);
        preview.item(item);
        return this;
    }

    /**
     * Set preview mode.
     */
    public LeftColumnComponent previewMode(PreviewRenderer.PreviewMode mode) {
        preview.mode(mode);
        return this;
    }

    /**
     * Add a stat to the info panel.
     */
    public LeftColumnComponent addStat(String label, String value) {
        itemInfo.addStat(label, value);
        return this;
    }

    /**
     * Add a stat with color.
     */
    public LeftColumnComponent addStat(String label, String value, int color) {
        itemInfo.addStat(label, value, color);
        return this;
    }

    /**
     * Add a numeric stat with auto-formatting.
     */
    public LeftColumnComponent addStat(String label, float value, String format) {
        itemInfo.addStat(label, value, format);
        return this;
    }

    /**
     * Clear all stats.
     */
    public LeftColumnComponent clearStats() {
        itemInfo.clearStats();
        return this;
    }

    /**
     * Set pending changes count.
     */
    public LeftColumnComponent pendingChanges(int count) {
        itemInfo.pendingChanges(count);
        return this;
    }

    /**
     * Set last saved timestamp for indicator.
     */
    public LeftColumnComponent lastSaved(long timestamp) {
        itemInfo.lastSaved(timestamp);
        return this;
    }

    /**
     * Set callback for slot selection.
     */
    public LeftColumnComponent onSlotSelect(Consumer<SlotSelector.SlotInfo> callback) {
        this.onSlotSelect = callback;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the left column at the given position.
     * @return The width consumed
     */
    public int render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick) {
        // Prefer provided width (already scaled by layout) to avoid double scaling
        int columnWidth = width > 0 ? width : ScaledCoord.scaleDim(UIConstants.PanelDimensions.LEFT_COLUMN_WIDTH);
        this.bounds = new ResponsiveLayout.Rect(x, y, columnWidth, height);

        // Background
        graphics.fill(x, y, x + columnWidth, y + height, UIConstants.Background.CONTENT);

        // Right border (separator from content area)
        graphics.fill(x + columnWidth - 1, y, x + columnWidth, y + height, UIConstants.Border.SEPARATOR);

        // Preview
        int previewSize = ScaledCoord.scaleDim(UIConstants.PanelDimensions.PREVIEW_SIZE);
        int previewY = y + ScaledCoord.scaleDim(PREVIEW_Y);
        int previewX = x + (columnWidth - previewSize) / 2;
        preview.render(graphics, previewX, previewY, mouseX, mouseY, partialTick);

        // Slot selector
        int slotX = x + ScaledCoord.scaleDim(SLOT_SELECTOR_X);
        int slotY = y + ScaledCoord.scaleDim(SLOT_SELECTOR_Y);
        slotSelector.render(graphics, slotX, slotY, columnWidth - ScaledCoord.scaleDim(SLOT_SELECTOR_X * 2), mouseX, mouseY);

        // Selected armor piece card (click to cycle slots)
        int slotAreaHeight = ScaledCoord.scaleDim(UIConstants.PanelDimensions.SLOT_AREA_HEIGHT);
        renderSelectedPieceCard(graphics, slotX, slotY + slotAreaHeight + ScaledCoord.scaleDim(8),
            columnWidth - ScaledCoord.scaleDim(SLOT_SELECTOR_X * 2), mouseX, mouseY);

        // Item info
        int infoX = x + ScaledCoord.scaleDim(ITEM_INFO_X);
        int infoY = y + ScaledCoord.scaleDim(ITEM_INFO_Y);
        itemInfo.render(graphics, infoX, infoY, columnWidth - ScaledCoord.scaleDim(ITEM_INFO_X * 2), mouseX, mouseY);

        return columnWidth;
    }

    private void renderSelectedPieceCard(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        int cardHeight = ScaledCoord.scaleDim(ARMOR_CARD_HEIGHT);
        armorCardBounds = new ResponsiveLayout.Rect(x, y, width, cardHeight);
        graphics.fill(x, y, x + width, y + cardHeight, UIConstants.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, x, y, width, cardHeight, UIConstants.Border.DEFAULT);

        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font, "font cannot be null");
        String title = "Selected piece";
        graphics.drawString(font, title, x + ScaledCoord.scaleDim(6), y + ScaledCoord.scaleDim(4), UIConstants.Text.SECONDARY, false);

        SlotSelector.SlotInfo info = slotSelector.getSelectedSlot();
        String label = info != null && info.label() != null ? info.label() : "Slot";
        graphics.drawString(font, label, x + ScaledCoord.scaleDim(6), y + ScaledCoord.scaleDim(18), UIConstants.Text.PRIMARY, false);

        // Render item icon on the right
        int iconSize = ScaledCoord.scaleDim(16);
        int iconX = x + width - iconSize - ScaledCoord.scaleDim(6);
        int iconY = y + (cardHeight - iconSize) / 2;
        if (info != null && info.item() != null && !info.item().isEmpty()) {
            ItemStack item = Objects.requireNonNull(info.item(), "slot item cannot be null");
            graphics.renderItem(item, iconX, iconY);
        } else {
            graphics.drawString(font, "⟳", iconX + 3, iconY + 3, UIConstants.Text.MUTED, false);
        }

        // Hover cue
        if (armorCardBounds.contains(mouseX, mouseY)) {
            graphics.fill(x, y, x + width, y + cardHeight, 0x2000D4FF);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check preview
        if (preview.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Check slot selector
        if (slotSelector.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Cycle slot when clicking the armor card
        if (armorCardBounds.contains(mouseX, mouseY)) {
            int current = slotSelector.getSelectedIndex();
            int next = (current + 1) % slotSelector.getSlots().size();
            slotSelector.setSelectedIndex(next);
            if (onSlotSelect != null) {
                onSlotSelect.accept(slotSelector.getSelectedSlot());
            }
            return true;
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (preview.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (preview.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (preview.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Bracket keys to navigate slots
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET) {
            // Previous slot
            int current = slotSelector.getSelectedIndex();
            if (current > 0) {
                slotSelector.setSelectedIndex(current - 1);
                if (onSlotSelect != null) {
                    onSlotSelect.accept(slotSelector.getSelectedSlot());
                }
            }
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET) {
            // Next slot
            int current = slotSelector.getSelectedIndex();
            if (current < slotSelector.getSlots().size() - 1) {
                slotSelector.setSelectedIndex(current + 1);
                if (onSlotSelect != null) {
                    onSlotSelect.accept(slotSelector.getSelectedSlot());
                }
            }
            return true;
        }

        // Arrow keys for slot navigation
        if (slotSelector.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public PreviewRenderer getPreview() {
        return preview;
    }

    public SlotSelector getSlotSelector() {
        return slotSelector;
    }

    public ItemInfoPanel getItemInfo() {
        return itemInfo;
    }

    public int getWidth() {
        if (bounds != ResponsiveLayout.Rect.EMPTY && bounds.width() > 0) {
            return bounds.width();
        }
        return ScaledCoord.scaleDim(UIConstants.PanelDimensions.LEFT_COLUMN_WIDTH);
    }

    public ResponsiveLayout.Rect getBounds() {
        return bounds;
    }

    public SlotSelector.SlotInfo getSelectedSlot() {
        return slotSelector.getSelectedSlot();
    }

    public EquipmentSlot getSelectedEquipmentSlot() {
        SlotSelector.SlotInfo info = slotSelector.getSelectedSlot();
        return info != null ? info.slot() : null;
    }
}
