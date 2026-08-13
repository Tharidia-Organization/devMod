package com.devmod.client.area.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.area.AreaBuilderGuiConstants;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Widget for configuring CUSTOM_NBT shape.
 * Allows manual input of block positions for custom area shapes.
 *
 * Since Minecraft doesn't have a native file picker, this widget provides:
 * 1. Manual coordinate entry for custom positions
 * 2. Import from clipboard (paste NBT string)
 * 3. Preset patterns (hollow box, plus sign, etc.)
 */
@OnlyIn(Dist.CLIENT)
public class CustomNbtWidget extends AbstractWidget {

    private static final int ROW_HEIGHT = 20;
    private static final int BUTTON_SIZE = 16;
    private static final int MAX_VISIBLE_POSITIONS = 5;
    private static final int MAX_POSITIONS = 10000;

    private final List<BlockPos> positions = new ArrayList<>();
    private final Consumer<CompoundTag> onNbtChanged;

    private int scrollOffset = 0;

    // Input fields for new position
    private final EditBox xInput;
    private final EditBox yInput;
    private final EditBox zInput;

    // Preset patterns
    private static final String[] PRESET_NAMES = {"None", "Hollow Box", "Plus", "Diamond", "Ring"};
    private int selectedPreset = 0;

    /**
     * Creates a new custom NBT widget.
     *
     * @param x              X position
     * @param y              Y position
     * @param width          Widget width
     * @param height         Widget height
     * @param onNbtChanged   Callback when NBT data changes
     */
    @SuppressWarnings("this-escape")
    public CustomNbtWidget(int x, int y, int width, int height,
                           @Nonnull Consumer<CompoundTag> onNbtChanged) {
        super(x, y, width, height, Component.empty());
        this.onNbtChanged = Objects.requireNonNull(onNbtChanged);

        Font font = Objects.requireNonNull(Minecraft.getInstance().font);
        int inputWidth = s(45);
        int inputY = y + height - s(50);

        // X input
        this.xInput = new EditBox(font, x + s(25), inputY, inputWidth, s(16),
            Objects.requireNonNull(Component.translatable("area.custom.x")));
        this.xInput.setValue("0");
        this.xInput.setMaxLength(6);

        // Y input
        this.yInput = new EditBox(font, x + s(80 + 25), inputY, inputWidth, s(16),
            Objects.requireNonNull(Component.translatable("area.custom.y")));
        this.yInput.setValue("0");
        this.yInput.setMaxLength(6);

        // Z input
        this.zInput = new EditBox(font, x + s(160 + 25), inputY, inputWidth, s(16),
            Objects.requireNonNull(Component.translatable("area.custom.z")));
        this.zInput.setValue("0");
        this.zInput.setMaxLength(6);
    }

    /**
     * Loads existing custom NBT data.
     *
     * @param customNbt The NBT data to load
     * @param center    The reference center (currently unused, reserved for future relative display)
     */
    public void loadFromNbt(@Nullable CompoundTag customNbt, @Nullable BlockPos center) {
        positions.clear();

        if (customNbt != null && customNbt.contains("positions", Tag.TAG_LIST)) {
            ListTag posList = customNbt.getList("positions", Tag.TAG_COMPOUND);
            for (int i = 0; i < posList.size() && i < MAX_POSITIONS; i++) {
                CompoundTag posTag = posList.getCompound(i);
                int px = posTag.getInt("x");
                int py = posTag.getInt("y");
                int pz = posTag.getInt("z");
                positions.add(new BlockPos(px, py, pz));
            }
        }

        scrollOffset = 0;
    }

    /**
     * Gets the current custom shape data as NBT.
     */
    @Nonnull
    public CompoundTag getCustomNbt() {
        CompoundTag nbt = new CompoundTag();
        ListTag posList = new ListTag();

        for (BlockPos pos : positions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            posList.add(posTag);
        }

        nbt.put("positions", posList);
        return nbt;
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font);
        int rowHeight = s(ROW_HEIGHT);
        int buttonSize = s(BUTTON_SIZE);
        int listPadding = s(4);
        int listHeaderGap = s(12);
        int listTopGap = s(45);
        int presetY = getY() + s(18);
        int presetButtonPadding = s(4);
        int presetButtonHeight = s(14);
        int listHeight = MAX_VISIBLE_POSITIONS * rowHeight;
        int addSectionGap = s(10);
        int addLabelGap = s(15);
        int inputHeight = s(16);
        int inputWidth = s(45);
        int inputLabelOffset = s(4);
        int addButtonWidth = s(50);
        int addButtonHeight = s(18);
        int clearButtonWidth = s(55);
        int clearButtonHeight = s(14);
        int clearButtonInset = s(60);

        // Title
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.custom.title")),
            getX(), getY(),
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Preset selector
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.custom.preset")),
            getX(), presetY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );

        // Preset buttons
        int presetBtnX = getX() + s(60);
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            String presetName = Objects.requireNonNull(PRESET_NAMES[i]);
            int btnWidth = font.width(presetName) + s(8);
            boolean selected = i == selectedPreset;
            boolean hovered = mouseX >= presetBtnX && mouseX < presetBtnX + btnWidth &&
                              mouseY >= presetY - s(2) && mouseY < presetY + presetButtonHeight;

            int bgColor = selected ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE :
                          (hovered ? AreaBuilderGuiConstants.COLOR_HOVER : AreaBuilderGuiConstants.COLOR_PANEL);
            graphics.fill(presetBtnX, presetY - s(2), presetBtnX + btnWidth, presetY + presetButtonHeight, bgColor);
            UIScaleManager.drawScaledString(graphics, font, presetName, presetBtnX + presetButtonPadding, presetY, AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);

            presetBtnX += btnWidth + s(4);
        }

        // Position list header
        int listY = getY() + listTopGap;
        UIScaleManager.drawScaledString(graphics, font,
            "Positions (relative to center): " + positions.size() + "/" + MAX_POSITIONS,
            getX(), listY - listHeaderGap,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );

        // Position list background
        graphics.fill(getX(), listY, getX() + getWidth(), listY + listHeight, AreaBuilderGuiConstants.COLOR_PANEL);
        graphics.renderOutline(getX(), listY, getWidth(), listHeight, AreaBuilderGuiConstants.COLOR_BORDER);

        // Render visible positions
        int visibleCount = Math.min(positions.size(), MAX_VISIBLE_POSITIONS);
        for (int i = 0; i < visibleCount; i++) {
            int idx = i + scrollOffset;
            if (idx >= positions.size()) break;

            BlockPos pos = positions.get(idx);
            int rowY = listY + i * rowHeight + s(2);

            // Index
            UIScaleManager.drawScaledString(graphics, font,
                String.valueOf(idx + 1) + ".",
                getX() + listPadding, rowY + inputLabelOffset,
                AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
            );

            // Coordinates
            String coordText = String.format("(%+d, %+d, %+d)", pos.getX(), pos.getY(), pos.getZ());
            UIScaleManager.drawScaledString(graphics, font, coordText, getX() + s(30), rowY + inputLabelOffset, AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);

            // Delete button
            int btnX = getX() + getWidth() - buttonSize - s(8);
            int btnY = rowY;
            boolean hovered = mouseX >= btnX && mouseX < btnX + buttonSize &&
                              mouseY >= btnY && mouseY < btnY + buttonSize;
            int btnColor = hovered ? DesignTokens.AreaBuilder.DELETE_BTN_HOVER : DesignTokens.AreaBuilder.DELETE_BTN;
            graphics.fill(btnX, btnY, btnX + buttonSize, btnY + buttonSize, btnColor);
            UIScaleManager.drawScaledCenteredString(graphics, font, "X", btnX + buttonSize / 2, btnY + inputLabelOffset, DesignTokens.AreaBuilder.TEXT_WHITE);
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            UIScaleManager.drawScaledCenteredString(graphics, font, "^", getX() + getWidth() / 2, listY + s(2), AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        }
        if (scrollOffset + MAX_VISIBLE_POSITIONS < positions.size()) {
            UIScaleManager.drawScaledCenteredString(graphics, font, "v", getX() + getWidth() / 2, listY + listHeight - s(10), AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        }

        // Add position section
        int addY = listY + listHeight + addSectionGap;
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.custom.add_position")),
            getX(), addY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );

        // X/Y/Z labels and inputs
        int inputY = addY + addLabelGap;
        UIScaleManager.drawScaledString(graphics, font, "X:", getX() + s(5), inputY + inputLabelOffset, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        xInput.setX(getX() + s(25));
        xInput.setY(inputY);
        xInput.setWidth(inputWidth);
        xInput.setHeight(inputHeight);
        xInput.render(graphics, mouseX, mouseY, partialTick);

        UIScaleManager.drawScaledString(graphics, font, "Y:", getX() + s(85), inputY + inputLabelOffset, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        yInput.setX(getX() + s(80 + 25));
        yInput.setY(inputY);
        yInput.setWidth(inputWidth);
        yInput.setHeight(inputHeight);
        yInput.render(graphics, mouseX, mouseY, partialTick);

        UIScaleManager.drawScaledString(graphics, font, "Z:", getX() + s(165), inputY + inputLabelOffset, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        zInput.setX(getX() + s(160 + 25));
        zInput.setY(inputY);
        zInput.setWidth(inputWidth);
        zInput.setHeight(inputHeight);
        zInput.render(graphics, mouseX, mouseY, partialTick);

        // Add button
        int addBtnX = getX() + s(230);
        int addBtnY = inputY;
        int addBtnW = addButtonWidth;
        int addBtnH = addButtonHeight;
        boolean addHovered = mouseX >= addBtnX && mouseX < addBtnX + addBtnW &&
                             mouseY >= addBtnY && mouseY < addBtnY + addBtnH;
        graphics.fill(addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + addBtnH,
            addHovered ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE : AreaBuilderGuiConstants.COLOR_PANEL);
        graphics.renderOutline(addBtnX, addBtnY, addBtnW, addBtnH, AreaBuilderGuiConstants.COLOR_BORDER);
        UIScaleManager.drawScaledCenteredString(graphics, font, "+ Add", addBtnX + addBtnW / 2, addBtnY + s(5), AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);

        // Clear all button
        int clearBtnX = getX() + getWidth() - clearButtonInset;
        int clearBtnY = addY - s(2);
        int clearBtnW = clearButtonWidth;
        int clearBtnH = clearButtonHeight;
        boolean clearHovered = mouseX >= clearBtnX && mouseX < clearBtnX + clearBtnW &&
                               mouseY >= clearBtnY && mouseY < clearBtnY + clearBtnH;
        graphics.fill(clearBtnX, clearBtnY, clearBtnX + clearBtnW, clearBtnY + clearBtnH,
            clearHovered ? DesignTokens.AreaBuilder.DELETE_BTN : AreaBuilderGuiConstants.COLOR_PANEL);
        graphics.renderOutline(clearBtnX, clearBtnY, clearBtnW, clearBtnH, AreaBuilderGuiConstants.COLOR_BORDER);
        UIScaleManager.drawScaledCenteredString(graphics, font, "Clear All", clearBtnX + clearBtnW / 2, clearBtnY + s(3), DesignTokens.AreaBuilder.TEXT_WHITE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check input fields
        if (xInput.mouseClicked(mouseX, mouseY, button)) return true;
        if (yInput.mouseClicked(mouseX, mouseY, button)) return true;
        if (zInput.mouseClicked(mouseX, mouseY, button)) return true;

        Font font = Objects.requireNonNull(Minecraft.getInstance().font);

        // Check preset buttons
        int presetY = getY() + s(18);
        int presetBtnX = getX() + s(60);
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            String presetName = Objects.requireNonNull(PRESET_NAMES[i]);
            int btnWidth = font.width(presetName) + s(8);
            if (mouseX >= presetBtnX && mouseX < presetBtnX + btnWidth &&
                mouseY >= presetY - s(2) && mouseY < presetY + s(14)) {
                selectedPreset = i;
                applyPreset(i);
                return true;
            }
            presetBtnX += btnWidth + s(4);
        }

        int listY = getY() + s(45);
        int listHeight = MAX_VISIBLE_POSITIONS * s(ROW_HEIGHT);

        // Check add button
        int addY = listY + listHeight + s(10);
        int inputY = addY + s(15);
        int addBtnX = getX() + s(230);
        int addBtnW = s(50);
        int addBtnH = s(18);
        if (mouseX >= addBtnX && mouseX < addBtnX + addBtnW &&
            mouseY >= inputY && mouseY < inputY + addBtnH) {
            addPosition();
            return true;
        }

        // Check clear all button
        int clearBtnX = getX() + getWidth() - s(60);
        int clearBtnY = addY - s(2);
        int clearBtnW = s(55);
        int clearBtnH = s(14);
        if (mouseX >= clearBtnX && mouseX < clearBtnX + clearBtnW &&
            mouseY >= clearBtnY && mouseY < clearBtnY + clearBtnH) {
            positions.clear();
            scrollOffset = 0;
            notifyChange();
            return true;
        }

        // Check delete buttons
        int visibleCount = Math.min(positions.size(), MAX_VISIBLE_POSITIONS);
        for (int i = 0; i < visibleCount; i++) {
            int idx = i + scrollOffset;
            if (idx >= positions.size()) break;

            int rowY = listY + i * s(ROW_HEIGHT) + s(2);
            int btnX = getX() + getWidth() - s(BUTTON_SIZE) - s(8);
            int btnY = rowY;

            if (mouseX >= btnX && mouseX < btnX + s(BUTTON_SIZE) &&
                mouseY >= btnY && mouseY < btnY + s(BUTTON_SIZE)) {
                positions.remove(idx);
                if (scrollOffset > 0 && scrollOffset >= positions.size() - MAX_VISIBLE_POSITIONS + 1) {
                    scrollOffset--;
                }
                notifyChange();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listY = getY() + s(45);
        int listHeight = MAX_VISIBLE_POSITIONS * s(ROW_HEIGHT);

        if (mouseX >= getX() && mouseX < getX() + getWidth() &&
            mouseY >= listY && mouseY < listY + listHeight) {
            if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (scrollY < 0 && scrollOffset + MAX_VISIBLE_POSITIONS < positions.size()) {
                scrollOffset++;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (xInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (yInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (zInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (xInput.charTyped(c, modifiers)) return true;
        if (yInput.charTyped(c, modifiers)) return true;
        if (zInput.charTyped(c, modifiers)) return true;
        return super.charTyped(c, modifiers);
    }

    private void addPosition() {
        if (positions.size() >= MAX_POSITIONS) return;

        try {
            int x = parseIntOrZero(xInput.getValue());
            int y = parseIntOrZero(yInput.getValue());
            int z = parseIntOrZero(zInput.getValue());

            BlockPos newPos = new BlockPos(x, y, z);
            if (!positions.contains(newPos)) {
                positions.add(newPos);

                // Reset inputs
                xInput.setValue("0");
                yInput.setValue("0");
                zInput.setValue("0");

                // Scroll to show new position
                if (positions.size() > MAX_VISIBLE_POSITIONS) {
                    scrollOffset = positions.size() - MAX_VISIBLE_POSITIONS;
                }

                notifyChange();
            }
        } catch (NumberFormatException ignored) {
            // Invalid input
        }
    }

    private void applyPreset(int presetIndex) {
        positions.clear();
        scrollOffset = 0;

        switch (presetIndex) {
            case 0 -> { /* None - just clear */ }
            case 1 -> generateHollowBox(16, 16, 8);
            case 2 -> generatePlus(20, 5);
            case 3 -> generateDiamond(12);
            case 4 -> generateRing(10, 3);
        }

        notifyChange();
    }

    private void generateHollowBox(int width, int length, int height) {
        int hw = width / 2;
        int hl = length / 2;

        // Floor and ceiling edges
        for (int x = -hw; x <= hw; x++) {
            for (int z = -hl; z <= hl; z++) {
                boolean isEdge = x == -hw || x == hw || z == -hl || z == hl;
                if (isEdge) {
                    positions.add(new BlockPos(x, 0, z));
                    positions.add(new BlockPos(x, height - 1, z));
                }
            }
        }

        // Vertical edges
        for (int y = 1; y < height - 1; y++) {
            positions.add(new BlockPos(-hw, y, -hl));
            positions.add(new BlockPos(hw, y, -hl));
            positions.add(new BlockPos(-hw, y, hl));
            positions.add(new BlockPos(hw, y, hl));
        }
    }

    private void generatePlus(int armLength, int armWidth) {
        int halfWidth = armWidth / 2;

        // Horizontal arm
        for (int x = -armLength; x <= armLength; x++) {
            for (int z = -halfWidth; z <= halfWidth; z++) {
                positions.add(new BlockPos(x, 0, z));
            }
        }

        // Vertical arm (avoid center duplication)
        for (int z = -armLength; z <= armLength; z++) {
            if (Math.abs(z) > halfWidth) {
                for (int x = -halfWidth; x <= halfWidth; x++) {
                    positions.add(new BlockPos(x, 0, z));
                }
            }
        }
    }

    private void generateDiamond(int size) {
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                if (Math.abs(x) + Math.abs(z) <= size) {
                    positions.add(new BlockPos(x, 0, z));
                }
            }
        }
    }

    private void generateRing(int outerRadius, int innerRadius) {
        for (int x = -outerRadius; x <= outerRadius; x++) {
            for (int z = -outerRadius; z <= outerRadius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= outerRadius && dist >= innerRadius) {
                    positions.add(new BlockPos(x, 0, z));
                }
            }
        }
    }

    private int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void notifyChange() {
        if (onNbtChanged != null) {
            onNbtChanged.accept(getCustomNbt());
        }
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Objects.requireNonNull(Component.translatable("area.custom.title")));
    }

    /**
     * Gets the position count.
     */
    public int getPositionCount() {
        return positions.size();
    }

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }
}
