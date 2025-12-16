package com.frenkvs.devmod.ui.editor.components;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.EditorDimensions;
import com.frenkvs.devmod.ui.editor.core.EditorSounds;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.ScaledCoord;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Slot selector for choosing equipment slots to edit.
 * Shows armor slots (4) or weapon slots (main/offhand).
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 4.7 (Slot Selectors)
 * @see EDITOR_DESIGN_SYSTEM.md Section 2.5 (Left Column)
 */
public final class SlotSelector {

    // ═══════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    private static final int SLOT_SIZE = EditorDimensions.SLOT_SIZE;  // 30px per spec
    private static final int SLOT_GAP = 5;
    private static final int HEIGHT = 70;

    // ═══════════════════════════════════════════════════════════════
    // SLOT TYPE
    // ═══════════════════════════════════════════════════════════════

    public enum SlotType {
        ARMOR,   // Head, Chest, Legs, Feet
        WEAPON   // Mainhand, Offhand
    }

    // ═══════════════════════════════════════════════════════════════
    // SLOT INFO
    // ═══════════════════════════════════════════════════════════════

    public record SlotInfo(
        EquipmentSlot slot,
        String label,
        String shortLabel,
        ItemStack item
    ) {}

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private SlotType type = SlotType.WEAPON;
    private final List<SlotInfo> slots = new ArrayList<>();
    private int selectedIndex = 0;
    private int hoveredIndex = -1;

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;
    private final List<ResponsiveLayout.Rect> slotBounds = new ArrayList<>();

    // Callback
    private Consumer<SlotInfo> onSelect;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public SlotSelector() {
        // Default weapon slots
        setType(SlotType.WEAPON);
    }

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    public void setType(SlotType type) {
        this.type = type;
        slots.clear();
        slotBounds.clear();

        if (type == SlotType.ARMOR) {
            slots.add(new SlotInfo(EquipmentSlot.HEAD, "Helmet", "H", ItemStack.EMPTY));
            slots.add(new SlotInfo(EquipmentSlot.CHEST, "Chestplate", "C", ItemStack.EMPTY));
            slots.add(new SlotInfo(EquipmentSlot.LEGS, "Leggings", "L", ItemStack.EMPTY));
            slots.add(new SlotInfo(EquipmentSlot.FEET, "Boots", "F", ItemStack.EMPTY));
        } else {
            slots.add(new SlotInfo(EquipmentSlot.MAINHAND, "Main Hand", "M", ItemStack.EMPTY));
            slots.add(new SlotInfo(EquipmentSlot.OFFHAND, "Off Hand", "O", ItemStack.EMPTY));
        }

        selectedIndex = 0;
    }

    public void setSlotItem(EquipmentSlot slot, ItemStack item) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).slot() == slot) {
                SlotInfo old = slots.get(i);
                slots.set(i, new SlotInfo(old.slot(), old.label(), old.shortLabel(), item));
                break;
            }
        }
    }

    public SlotSelector onSelect(Consumer<SlotInfo> callback) {
        this.onSelect = callback;
        return this;
    }

    /**
     * Programmatically select a slot by EquipmentSlot (sync from preview).
     */
    public void selectSlot(EquipmentSlot slot) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).slot() == slot) {
                selectedIndex = i;
                if (onSelect != null) {
                    onSelect.accept(slots.get(i));
                }
                break;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    public int render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");

        int slotSize = ScaledCoord.scaleDim(SLOT_SIZE);
        int slotGap = ScaledCoord.scaleDim(SLOT_GAP);
        int height = ScaledCoord.scaleDim(HEIGHT);

        this.bounds = new ResponsiveLayout.Rect(x, y, width, height);
        slotBounds.clear();

        // Calculate slot positions - centered horizontally
        int totalSlotWidth = slots.size() * slotSize + (slots.size() - 1) * slotGap;
        int startX = x + (width - totalSlotWidth) / 2;
        int slotY = y + ScaledCoord.scaleDim(8);

        // Update hover state
        hoveredIndex = -1;

        // Render slots
        for (int i = 0; i < slots.size(); i++) {
            SlotInfo slotInfo = slots.get(i);
            int slotX = startX + i * (slotSize + slotGap);
            ResponsiveLayout.Rect slotRect = new ResponsiveLayout.Rect(slotX, slotY, slotSize, slotSize);
            slotBounds.add(slotRect);

            boolean isHovered = slotRect.contains(mouseX, mouseY);
            boolean isSelected = (i == selectedIndex);

            if (isHovered) {
                hoveredIndex = i;
            }

            // Slot background
            int bgColor = isSelected ? UIConstants.Background.ACTIVE :
                         (isHovered ? UIConstants.Background.HOVER : UIConstants.Background.INPUT);
            graphics.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, bgColor);

            // Slot border
            int borderColor = isSelected ? UIConstants.Border.ACCENT :
                             (isHovered ? UIConstants.Border.HOVER : UIConstants.Border.DEFAULT);
            AxiomRenderer.drawBorder(graphics, slotX, slotY, slotSize, slotSize, borderColor);

            // Render item or short label
            ItemStack itemStack = Objects.requireNonNull(slotInfo.item(), "slot item cannot be null");
            if (!itemStack.isEmpty()) {
                int iconPad = ScaledCoord.scaleDim(4);
                graphics.renderItem(itemStack, slotX + iconPad, slotY + iconPad);
            } else {
                // Show placeholder glyph when empty
                int textColor = isSelected ? UIConstants.Text.PRIMARY : UIConstants.Text.MUTED;
                String placeholder = Objects.requireNonNull(placeholderFor(slotInfo), "placeholder cannot be null");
                int textX = slotX + (slotSize - font.width(placeholder)) / 2;
                int textY = slotY + (slotSize - font.lineHeight) / 2;
                graphics.drawString(font, placeholder, textX, textY, textColor, false);
            }
        }

        // Render selected slot label below
        if (selectedIndex >= 0 && selectedIndex < slots.size()) {
            String label = Objects.requireNonNull(slots.get(selectedIndex).label(), "label cannot be null");
            int labelX = x + (width - font.width(label)) / 2;
            int labelY = slotY + slotSize + ScaledCoord.scaleDim(8);
            graphics.drawString(font, label, labelX, labelY, UIConstants.Text.SECONDARY, false);
        }

        return height;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        for (int i = 0; i < slotBounds.size(); i++) {
            if (slotBounds.get(i).contains(mouseX, mouseY)) {
                if (i != selectedIndex) {
                    selectedIndex = i;
                    EditorSounds.playSlotSelect();
                    if (onSelect != null) {
                        onSelect.accept(slots.get(i));
                    }
                }
                return true;
            }
        }

        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Left/Right arrow keys to navigate
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            if (selectedIndex > 0) {
                selectedIndex--;
                EditorSounds.playSlotSelect();
                if (onSelect != null) {
                    onSelect.accept(slots.get(selectedIndex));
                }
            }
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            if (selectedIndex < slots.size() - 1) {
                selectedIndex++;
                EditorSounds.playSlotSelect();
                if (onSelect != null) {
                    onSelect.accept(slots.get(selectedIndex));
                }
            }
            return true;
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public SlotType getType() {
        return type;
    }

    public List<SlotInfo> getSlots() {
        return slots;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < slots.size()) {
            this.selectedIndex = index;
        }
    }

    public SlotInfo getSelectedSlot() {
        if (selectedIndex >= 0 && selectedIndex < slots.size()) {
            return slots.get(selectedIndex);
        }
        return null;
    }

    public int getHeight() {
        return HEIGHT;
    }

    public ResponsiveLayout.Rect getBounds() {
        return bounds;
    }

    /**
     * Get the currently hovered slot index, or -1 if none.
     * Useful for rendering tooltips.
     */
    public int getHoveredIndex() {
        return hoveredIndex;
    }

    /**
     * Get the currently hovered slot info, or null if none.
     */
    public SlotInfo getHoveredSlot() {
        if (hoveredIndex >= 0 && hoveredIndex < slots.size()) {
            return slots.get(hoveredIndex);
        }
        return null;
    }

    private String placeholderFor(SlotInfo info) {
        if (info == null || info.slot() == null) return "?";
        return switch (info.slot()) {
            case HEAD -> "🪖";
            case CHEST -> "🦺";
            case LEGS -> "👖";
            case FEET -> "👢";
            case OFFHAND -> "🛡";
            case MAINHAND -> "⚔";
            default -> Objects.requireNonNullElse(info.shortLabel(), "?");
        };
    }
}
