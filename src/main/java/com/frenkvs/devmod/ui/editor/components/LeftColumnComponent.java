package com.frenkvs.devmod.ui.editor.components;

import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

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

    private static final int WIDTH = UIConstants.PanelDimensions.LEFT_COLUMN_WIDTH;  // 140px

    // Component positions (relative to left column origin)
    private static final int PREVIEW_X = 20;
    private static final int PREVIEW_Y = 10;
    private static final int SLOT_SELECTOR_X = 5;
    private static final int SLOT_SELECTOR_Y = 130;
    private static final int ITEM_INFO_X = 5;
    private static final int ITEM_INFO_Y = 208;

    // ═══════════════════════════════════════════════════════════════
    // COMPONENTS
    // ═══════════════════════════════════════════════════════════════

    private final PreviewRenderer preview;
    private final SlotSelector slotSelector;
    private final ItemInfoPanel itemInfo;

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
            if (onSlotSelect != null) {
                onSlotSelect.accept(slot);
            }
        });
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
    public int render(GuiGraphics graphics, int x, int y, int height, int mouseX, int mouseY, float partialTick) {
        this.bounds = new ResponsiveLayout.Rect(x, y, WIDTH, height);

        // Background
        graphics.fill(x, y, x + WIDTH, y + height, UIConstants.Background.CONTENT);

        // Right border (separator from content area)
        graphics.fill(x + WIDTH - 1, y, x + WIDTH, y + height, UIConstants.Border.SEPARATOR);

        // Preview
        int previewX = x + PREVIEW_X;
        int previewY = y + PREVIEW_Y;
        preview.render(graphics, previewX, previewY, mouseX, mouseY, partialTick);

        // Slot selector
        int slotX = x + SLOT_SELECTOR_X;
        int slotY = y + SLOT_SELECTOR_Y;
        slotSelector.render(graphics, slotX, slotY, WIDTH - SLOT_SELECTOR_X * 2, mouseX, mouseY);

        // Item info
        int infoX = x + ITEM_INFO_X;
        int infoY = y + ITEM_INFO_Y;
        itemInfo.render(graphics, infoX, infoY, WIDTH - ITEM_INFO_X * 2, mouseX, mouseY);

        return WIDTH;
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
        return WIDTH;
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
