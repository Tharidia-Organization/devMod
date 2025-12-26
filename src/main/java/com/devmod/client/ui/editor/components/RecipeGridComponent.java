package com.devmod.client.ui.editor.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.recipe.CraftingRecipeData;
import com.devmod.recipe.CraftingType;
import com.devmod.recipe.IngredientData;

/**
 * Interactive 3x3 grid component for crafting recipe editing.
 * Supports drag-drop, selection, and ingredient display.
 */
public class RecipeGridComponent {

    // ═══════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    private static final int BASE_CELL_SIZE = 24;
    private static final int GRID_SIZE = 3;
    private static final int GRID_GAP = 2;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private final IngredientSlot[] slots = new IngredientSlot[9];
    private int hoveredSlot = -1;
    private int selectedSlot = -1;
    private ItemStack draggedItem = ItemStack.EMPTY;
    private boolean isDragging = false;

    // Grid position (set during render)
    private int gridX, gridY;
    private int scaledCellSize;
    private int totalGridSize;

    // Animation
    private long lastTick = 0;

    // ═══════════════════════════════════════════════════════════════
    // CALLBACKS
    // ═══════════════════════════════════════════════════════════════

    private Consumer<Integer> onSlotClick;
    private BiConsumer<Integer, IngredientData> onSlotChange;
    private Runnable onGridChange;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public RecipeGridComponent() {
        for (int i = 0; i < 9; i++) {
            slots[i] = new IngredientSlot(i, IngredientData.empty());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the 3x3 grid.
     * @return Total height rendered
     */
    public int render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        lastTick = System.currentTimeMillis();
        scaledCellSize = ScaledCoord.scaleDim(BASE_CELL_SIZE);
        totalGridSize = scaledCellSize * GRID_SIZE + GRID_GAP * (GRID_SIZE - 1);

        gridX = x;
        gridY = y;

        // Update hover state
        hoveredSlot = getSlotAt(mouseX, mouseY);

        // Grid background
        int bgPad = 4;
        graphics.fill(
            x - bgPad, y - bgPad,
            x + totalGridSize + bgPad, y + totalGridSize + bgPad,
            UIConstants.Background.INPUT()
        );

        // Render each slot
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int index = row * GRID_SIZE + col;
                int cellX = x + col * (scaledCellSize + GRID_GAP);
                int cellY = y + row * (scaledCellSize + GRID_GAP);

                renderSlot(graphics, font, cellX, cellY, index);
            }
        }

        // Render dragged item (follows mouse)
        if (isDragging && !draggedItem.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200); // Above everything
            graphics.renderItem(Objects.requireNonNull(draggedItem, "draggedItem"), mouseX - 8, mouseY - 8);
            graphics.pose().popPose();
        }

        return totalGridSize + bgPad * 2;
    }

    private void renderSlot(GuiGraphics g, Font font, int x, int y, int index) {
        IngredientSlot slot = slots[index];

        // === Background ===
        int bgColor;
        if (index == selectedSlot) {
            bgColor = UIConstants.Background.ACTIVE();
        } else if (index == hoveredSlot) {
            bgColor = UIConstants.Background.HOVER();
        } else {
            bgColor = UIConstants.Background.PANEL();
        }
        g.fill(x, y, x + scaledCellSize, y + scaledCellSize, bgColor);

        // === Border ===
        int borderColor;
        if (index == selectedSlot) {
            borderColor = UIConstants.Border.ACCENT();
        } else if (index == hoveredSlot) {
            borderColor = UIConstants.Border.HOVER();
        } else {
            borderColor = UIConstants.Border.MUTED();
        }
        drawBorder(g, x, y, scaledCellSize, scaledCellSize, borderColor);

        // === Content ===
        if (!slot.isEmpty()) {
            int itemPad = (scaledCellSize - 16) / 2;
            int itemX = x + itemPad;
            int itemY = y + itemPad;

            if (slot.data().isTag()) {
                // Render cycling item from tag
                ItemStack display = slot.data().getDisplayStack(lastTick / 50);
                if (!display.isEmpty()) {
                    g.renderItem(display, itemX, itemY);

                    // Tag indicator (orange corner)
                    int indicatorSize = 4;
                    g.fill(
                        x + scaledCellSize - indicatorSize - 2,
                        y + scaledCellSize - indicatorSize - 2,
                        x + scaledCellSize - 2,
                        y + scaledCellSize - 2,
                        0xFFFF9800 // Orange
                    );
                }
            } else {
                // Regular item
                ItemStack display = slot.data().getDisplayStack(lastTick / 50);
                if (!display.isEmpty()) {
                    g.renderItem(display, itemX, itemY);
                }
            }
        } else {
            // Empty slot indicator
            int centerX = x + scaledCellSize / 2;
            int centerY = y + scaledCellSize / 2;
            int dotSize = 2;
            g.fill(centerX - dotSize, centerY - dotSize, centerX + dotSize, centerY + dotSize,
                UIConstants.Border.MUTED());
        }
    }

    /**
     * Render tooltips for hovered slot.
     */
    public void renderTooltip(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (hoveredSlot >= 0 && hoveredSlot < 9 && !slots[hoveredSlot].isEmpty()) {
            IngredientSlot slot = slots[hoveredSlot];

            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(Objects.requireNonNull(slot.data().getDisplayName(), "displayName")));

            if (slot.data().isTag()) {
                tooltip.add(Component.literal("Tag ingredient").withStyle(s -> s.withColor(0xAAAAAA)));
                int tagCount = slot.data().getTagItems().size();
                tooltip.add(Component.literal(tagCount + " items in tag").withStyle(s -> s.withColor(0x888888)));
            }

            graphics.renderComponentTooltip(Objects.requireNonNull(font, "font"), tooltip, mouseX, mouseY);
        }
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);         // Top
        g.fill(x, y + h - 1, x + w, y + h, color); // Bottom
        g.fill(x, y, x + 1, y + h, color);         // Left
        g.fill(x + w - 1, y, x + w, y + h, color); // Right
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slot = getSlotAt((int) mouseX, (int) mouseY);

        if (slot >= 0) {
            if (button == 0) { // Left click - select
                selectedSlot = slot;
                if (onSlotClick != null) {
                    onSlotClick.accept(slot);
                }
                return true;
            } else if (button == 1) { // Right click - clear
                clearSlot(slot);
                return true;
            }
        } else {
            // Click outside grid - deselect
            selectedSlot = -1;
        }

        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && selectedSlot >= 0 && !slots[selectedSlot].isEmpty()) {
            if (!isDragging) {
                isDragging = true;
                draggedItem = slots[selectedSlot].data().getDisplayStack(0);
            }
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDragging) {
            int targetSlot = getSlotAt((int) mouseX, (int) mouseY);

            if (targetSlot >= 0 && targetSlot != selectedSlot) {
                // Swap slots
                IngredientData temp = slots[targetSlot].data();
                slots[targetSlot] = new IngredientSlot(targetSlot, slots[selectedSlot].data());
                slots[selectedSlot] = new IngredientSlot(selectedSlot, temp);

                notifyChange(targetSlot);
                notifyChange(selectedSlot);
                notifyGridChange();
            }

            isDragging = false;
            draggedItem = ItemStack.EMPTY;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Could be used for quantity adjustment in future
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // SLOT MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    private int getSlotAt(int mouseX, int mouseY) {
        if (mouseX < gridX || mouseY < gridY) return -1;
        if (mouseX >= gridX + totalGridSize || mouseY >= gridY + totalGridSize) return -1;

        int relX = mouseX - gridX;
        int relY = mouseY - gridY;

        int cellWithGap = scaledCellSize + GRID_GAP;
        int col = relX / cellWithGap;
        int row = relY / cellWithGap;

        // Check not in gap
        if (relX % cellWithGap >= scaledCellSize || relY % cellWithGap >= scaledCellSize) {
            return -1;
        }

        if (col >= 0 && col < GRID_SIZE && row >= 0 && row < GRID_SIZE) {
            return row * GRID_SIZE + col;
        }

        return -1;
    }

    public void setSlot(int index, IngredientData data) {
        if (index < 0 || index >= 9) return;
        slots[index] = new IngredientSlot(index, data != null ? data : IngredientData.empty());
        notifyChange(index);
        notifyGridChange();
    }

    public void setSlotFromItem(int index, ItemStack item) {
        if (index < 0 || index >= 9) return;
        IngredientData data = item.isEmpty() ? IngredientData.empty() : IngredientData.ofItem(item.getItem());
        setSlot(index, data);
    }

    public void clearSlot(int index) {
        setSlot(index, IngredientData.empty());
    }

    public void clear() {
        for (int i = 0; i < 9; i++) {
            slots[i] = new IngredientSlot(i, IngredientData.empty());
        }
        selectedSlot = -1;
        notifyGridChange();
    }

    public IngredientSlot getSlot(int index) {
        if (index < 0 || index >= 9) return new IngredientSlot(0, IngredientData.empty());
        return slots[index];
    }

    public IngredientSlot[] getSlots() {
        return slots.clone();
    }

    public List<IngredientData> getIngredientList() {
        List<IngredientData> list = new ArrayList<>(9);
        for (IngredientSlot slot : slots) {
            list.add(slot.data());
        }
        return list;
    }

    public List<IngredientData> getNonEmptyIngredients() {
        List<IngredientData> list = new ArrayList<>();
        for (IngredientSlot slot : slots) {
            if (!slot.isEmpty()) {
                list.add(slot.data());
            }
        }
        return list;
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    public void setSelectedSlot(int index) {
        this.selectedSlot = index;
    }

    public boolean hasAnyIngredient() {
        for (IngredientSlot slot : slots) {
            if (!slot.isEmpty()) return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // RECIPE LOADING
    // ═══════════════════════════════════════════════════════════════

    public void loadFromRecipe(CraftingRecipeData recipe) {
        clear();

        if (recipe == null) return;

        List<IngredientData> grid = recipe.getGridIngredients();
        for (int i = 0; i < Math.min(grid.size(), 9); i++) {
            slots[i] = new IngredientSlot(i, grid.get(i));
        }
    }

    public void loadFromIngredients(List<IngredientData> ingredients, CraftingType type) {
        clear();

        if (ingredients == null) return;

        if (type == CraftingType.SHAPELESS) {
            // Fill sequentially
            int idx = 0;
            for (IngredientData ing : ingredients) {
                if (idx >= 9) break;
                if (ing != null && !ing.isEmpty()) {
                    slots[idx] = new IngredientSlot(idx, ing);
                    idx++;
                }
            }
        } else {
            // Assume ingredients are already in grid order
            for (int i = 0; i < Math.min(ingredients.size(), 9); i++) {
                IngredientData ing = ingredients.get(i);
                slots[i] = new IngredientSlot(i, ing != null ? ing : IngredientData.empty());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CALLBACKS
    // ═══════════════════════════════════════════════════════════════

    public void setOnSlotClick(Consumer<Integer> callback) {
        this.onSlotClick = callback;
    }

    public void setOnSlotChange(BiConsumer<Integer, IngredientData> callback) {
        this.onSlotChange = callback;
    }

    public void setOnGridChange(Runnable callback) {
        this.onGridChange = callback;
    }

    private void notifyChange(int index) {
        if (onSlotChange != null && index >= 0 && index < 9) {
            onSlotChange.accept(index, slots[index].data());
        }
    }

    private void notifyGridChange() {
        if (onGridChange != null) {
            onGridChange.run();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Represents a single slot in the grid.
     */
    public record IngredientSlot(int index, IngredientData data) {
        public boolean isEmpty() {
            return data == null || data.isEmpty();
        }
    }
}
