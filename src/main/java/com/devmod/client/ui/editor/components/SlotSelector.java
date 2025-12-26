package com.devmod.client.ui.editor.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.EditorConstants;
import com.devmod.client.ui.editor.core.EditorSounds;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.UIConstants;

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

    private static final int SLOT_SIZE = EditorConstants.SLOT_SIZE;  // 32px per spec
    private static final int SLOT_GAP = EditorConstants.SLOT_GAP;
    private static final int HEIGHT = EditorConstants.SLOT_AREA_HEIGHT;
    private static final int DEFAULT_SELECTED_INDEX = 0;
    private static final int NO_HOVER_INDEX = -1;
    private static final int PRIMARY_MOUSE_BUTTON = 0;

    private static final String LABEL_HELMET = "Helmet";
    private static final String LABEL_CHESTPLATE = "Chestplate";
    private static final String LABEL_LEGGINGS = "Leggings";
    private static final String LABEL_BOOTS = "Boots";
    private static final String LABEL_MAIN_HAND = "Main Hand";
    private static final String LABEL_OFF_HAND = "Off Hand";
    private static final String SHORT_LABEL_HEAD = "H";
    private static final String SHORT_LABEL_CHEST = "C";
    private static final String SHORT_LABEL_LEGS = "L";
    private static final String SHORT_LABEL_FEET = "F";
    private static final String SHORT_LABEL_MAIN_HAND = "M";
    private static final String SHORT_LABEL_OFF_HAND = "O";
    private static final String PLACEHOLDER_UNKNOWN = "?";
    private static final String PLACEHOLDER_HEAD = "🪖";
    private static final String PLACEHOLDER_CHEST = "🦺";
    private static final String PLACEHOLDER_LEGS = "👖";
    private static final String PLACEHOLDER_FEET = "👢";
    private static final String PLACEHOLDER_OFFHAND = "🛡";
    private static final String PLACEHOLDER_MAINHAND = "⚔";

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
    private int selectedIndex = DEFAULT_SELECTED_INDEX;
    private int hoveredIndex = NO_HOVER_INDEX;

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
            slots.add(new SlotInfo(EquipmentSlot.HEAD, LABEL_HELMET, SHORT_LABEL_HEAD, ItemStack.EMPTY));
            slots.add(new SlotInfo(EquipmentSlot.CHEST, LABEL_CHESTPLATE, SHORT_LABEL_CHEST, ItemStack.EMPTY));
            slots.add(new SlotInfo(EquipmentSlot.LEGS, LABEL_LEGGINGS, SHORT_LABEL_LEGS, ItemStack.EMPTY));
            slots.add(new SlotInfo(EquipmentSlot.FEET, LABEL_BOOTS, SHORT_LABEL_FEET, ItemStack.EMPTY));
        } else {
            slots.add(new SlotInfo(EquipmentSlot.MAINHAND, LABEL_MAIN_HAND, SHORT_LABEL_MAIN_HAND, ItemStack.EMPTY));
            slots.add(new SlotInfo(EquipmentSlot.OFFHAND, LABEL_OFF_HAND, SHORT_LABEL_OFF_HAND, ItemStack.EMPTY));
        }

        selectedIndex = DEFAULT_SELECTED_INDEX;
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
        int innerPadding = ScaledCoord.scaleDim(UIConstants.Spacing.MD);
        int slotY = y + innerPadding;

        // Update hover state
        hoveredIndex = NO_HOVER_INDEX;

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
            int bgColor = isSelected ? UIConstants.Background.ACTIVE() :
                         (isHovered ? UIConstants.Background.HOVER() : UIConstants.Background.INPUT());
            graphics.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, bgColor);

            // Slot border
            int borderColor = isSelected ? UIConstants.Border.ACCENT() :
                             (isHovered ? UIConstants.Border.HOVER() : UIConstants.Border.DEFAULT());
            AxiomRenderer.drawBorder(graphics, slotX, slotY, slotSize, slotSize, borderColor);

            // Render item or short label
            ItemStack itemStack = Objects.requireNonNull(slotInfo.item(), "slot item cannot be null");
            if (!itemStack.isEmpty()) {
                // Scale item to fit slot (16px base item size -> slot size with padding)
                int itemPadding = ScaledCoord.scaleDim(UIConstants.Spacing.XS);
                int targetItemSize = slotSize - itemPadding * 2;
                float itemScale = targetItemSize / 16.0f;  // 16px is Minecraft's base item render size

                int itemX = slotX + itemPadding;
                int itemY = slotY + itemPadding;

                // Use pose matrix to scale the item rendering
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(itemX, itemY, 0);
                pose.scale(itemScale, itemScale, 1.0f);
                graphics.renderItem(itemStack, 0, 0);
                pose.popPose();
            } else {
                // Show placeholder glyph when empty
                int textColor = isSelected ? UIConstants.Text.PRIMARY() : UIConstants.Text.MUTED();
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
            int labelY = slotY + slotSize + innerPadding;
            graphics.drawString(font, label, labelX, labelY, UIConstants.Text.SECONDARY(), false);
        }

        return height;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != PRIMARY_MOUSE_BUTTON) return false;

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
        if (info == null || info.slot() == null) return PLACEHOLDER_UNKNOWN;
        return switch (info.slot()) {
            case HEAD -> PLACEHOLDER_HEAD;
            case CHEST -> PLACEHOLDER_CHEST;
            case LEGS -> PLACEHOLDER_LEGS;
            case FEET -> PLACEHOLDER_FEET;
            case OFFHAND -> PLACEHOLDER_OFFHAND;
            case MAINHAND -> PLACEHOLDER_MAINHAND;
            default -> Objects.requireNonNullElse(info.shortLabel(), PLACEHOLDER_UNKNOWN);
        };
    }
}
