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
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.area.aesthetic.AreaBuilderGuiConstants;
import com.devmod.area.builder.AreaShapeGenerator;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Widget for configuring PATH shape waypoints.
 * Allows adding, removing, and editing waypoints for corridor/path areas.
 */
@OnlyIn(Dist.CLIENT)
public class PathWaypointWidget extends AbstractWidget {

    private static final int ROW_HEIGHT = 22;
    private static final int BUTTON_SIZE = 16;
    private static final int MAX_VISIBLE_WAYPOINTS = 6;
    private static final int DEFAULT_CORRIDOR_WIDTH = 8;
    private static final int MIN_CORRIDOR_WIDTH = 2;
    private static final int MAX_CORRIDOR_WIDTH = 32;

    private final List<WaypointEntry> waypoints = new ArrayList<>();
    private final Consumer<CompoundTag> onPathDataChanged;

    private int corridorWidth = DEFAULT_CORRIDOR_WIDTH;
    private int scrollOffset = 0;

    // Input fields for new waypoint
    private final EditBox xInput;
    private final EditBox yInput;
    private final EditBox zInput;
    private final EditBox widthInput;

    @Nullable
    private BlockPos referenceCenter;

    /**
     * Creates a new path waypoint widget.
     *
     * @param x                  X position
     * @param y                  Y position
     * @param width              Widget width
     * @param height             Widget height
     * @param onPathDataChanged  Callback when path data changes (receives NBT)
     */
    @SuppressWarnings("this-escape")
    public PathWaypointWidget(int x, int y, int width, int height,
                              @Nonnull Consumer<CompoundTag> onPathDataChanged) {
        super(x, y, width, height, Component.empty());
        this.onPathDataChanged = Objects.requireNonNull(onPathDataChanged);

        Font font = Objects.requireNonNull(Minecraft.getInstance().font);
        int inputWidth = s(45);
        int inputY = y + height - s(50);

        // X input
        this.xInput = new EditBox(font, x + s(25), inputY, inputWidth, s(16),
            Objects.requireNonNull(Component.translatable("area.path.x")));
        this.xInput.setValue("0");
        this.xInput.setMaxLength(6);

        // Y input
        this.yInput = new EditBox(font, x + s(80 + 25), inputY, inputWidth, s(16),
            Objects.requireNonNull(Component.translatable("area.path.y")));
        this.yInput.setValue("0");
        this.yInput.setMaxLength(6);

        // Z input
        this.zInput = new EditBox(font, x + s(160 + 25), inputY, inputWidth, s(16),
            Objects.requireNonNull(Component.translatable("area.path.z")));
        this.zInput.setValue("0");
        this.zInput.setMaxLength(6);

        // Width input (corridor width)
        this.widthInput = new EditBox(font, x + s(100), y + s(35), s(40), s(16),
            Objects.requireNonNull(Component.translatable("area.path.width")));
        this.widthInput.setValue(Objects.requireNonNull(String.valueOf(corridorWidth)));
        this.widthInput.setMaxLength(3);
        this.widthInput.setResponder(this::onWidthChanged);

        // Add default waypoints (origin and one offset point)
        waypoints.add(new WaypointEntry(0, 0, 0));
        waypoints.add(new WaypointEntry(20, 0, 0));
    }

    private void onWidthChanged(String value) {
        try {
            int newWidth = Integer.parseInt(value);
            if (newWidth >= MIN_CORRIDOR_WIDTH && newWidth <= MAX_CORRIDOR_WIDTH) {
                corridorWidth = newWidth;
                notifyChange();
            }
        } catch (NumberFormatException ignored) {
            // Invalid input, ignore
        }
    }

    /**
     * Sets the reference center position for relative coordinate display.
     */
    public void setReferenceCenter(@Nullable BlockPos center) {
        this.referenceCenter = center;
    }

    /**
     * Loads existing path data from NBT.
     */
    public void loadFromNbt(@Nullable CompoundTag pathNbt, @Nullable BlockPos center) {
        waypoints.clear();
        this.referenceCenter = center;

        if (pathNbt != null && center != null) {
            List<BlockPos> parsed = AreaShapeGenerator.parsePathWaypoints(center, pathNbt);
            for (BlockPos pos : parsed) {
                // Convert to relative coordinates
                waypoints.add(new WaypointEntry(
                    pos.getX() - center.getX(),
                    pos.getY() - center.getY(),
                    pos.getZ() - center.getZ()
                ));
            }

            if (pathNbt.contains(AreaShapeGenerator.NBT_PATH_WIDTH)) {
                corridorWidth = pathNbt.getInt(AreaShapeGenerator.NBT_PATH_WIDTH);
                widthInput.setValue(Objects.requireNonNull(String.valueOf(corridorWidth)));
            }
        }

        // Ensure at least 2 waypoints
        if (waypoints.size() < 2) {
            waypoints.clear();
            waypoints.add(new WaypointEntry(0, 0, 0));
            waypoints.add(new WaypointEntry(20, 0, 0));
        }
    }

    /**
     * Gets the current path data as NBT.
     */
    @Nonnull
    public CompoundTag getPathNbt() {
        BlockPos center = referenceCenter != null ? referenceCenter : BlockPos.ZERO;
        List<BlockPos> absolutePositions = new ArrayList<>();

        for (WaypointEntry entry : waypoints) {
            absolutePositions.add(Objects.requireNonNull(center.offset(entry.x, entry.y, entry.z)));
        }

        return Objects.requireNonNull(AreaShapeGenerator.createPathNbt(Objects.requireNonNull(center), absolutePositions, corridorWidth));
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font);
        int rowHeight = s(ROW_HEIGHT);
        int buttonSize = s(BUTTON_SIZE);
        int listTopGap = s(60);
        int listHeaderGap = s(12);
        int listHeight = MAX_VISIBLE_WAYPOINTS * rowHeight;
        int addSectionGap = s(10);
        int addLabelGap = s(15);
        int inputHeight = s(16);
        int inputWidth = s(45);
        int inputLabelOffset = s(4);
        int addButtonWidth = s(50);
        int addButtonHeight = s(18);

        // Title
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.path.waypoints")),
            getX(), getY(),
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Corridor width label and input
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.path.corridor_width")),
            getX(), getY() + s(22),
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
        widthInput.setX(getX() + s(100));
        widthInput.setY(getY() + s(35));
        widthInput.setWidth(s(40));
        widthInput.setHeight(inputHeight);
        widthInput.render(graphics, mouseX, mouseY, partialTick);

        // Waypoint list header
        int listY = getY() + listTopGap;
        UIScaleManager.drawScaledString(graphics, font,
            "Waypoints (relative to center):",
            getX(), listY - listHeaderGap,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );

        // Waypoint list background
        graphics.fill(getX(), listY, getX() + getWidth(), listY + listHeight, AreaBuilderGuiConstants.COLOR_PANEL);
        graphics.renderOutline(getX(), listY, getWidth(), listHeight, AreaBuilderGuiConstants.COLOR_BORDER);

        // Render visible waypoints
        int visibleCount = Math.min(waypoints.size(), MAX_VISIBLE_WAYPOINTS);
        for (int i = 0; i < visibleCount; i++) {
            int idx = i + scrollOffset;
            if (idx >= waypoints.size()) break;

            WaypointEntry entry = waypoints.get(idx);
            int rowY = listY + i * rowHeight + s(3);

            // Index
            UIScaleManager.drawScaledString(graphics, font,
                String.valueOf(idx + 1) + ".",
                getX() + s(4), rowY + inputLabelOffset,
                AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
            );

            // Coordinates
            String coordText = String.format("(%+d, %+d, %+d)", entry.x, entry.y, entry.z);
            UIScaleManager.drawScaledString(graphics, font, coordText, getX() + s(25), rowY + inputLabelOffset, AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);

            // Delete button (only if more than 2 waypoints)
            if (waypoints.size() > 2) {
                int btnX = getX() + getWidth() - buttonSize - s(8);
                int btnY = rowY;
                boolean hovered = mouseX >= btnX && mouseX < btnX + buttonSize &&
                                  mouseY >= btnY && mouseY < btnY + buttonSize;
                int btnColor = hovered ? DesignTokens.AreaBuilder.DELETE_BTN_HOVER : DesignTokens.AreaBuilder.DELETE_BTN;
                graphics.fill(btnX, btnY, btnX + buttonSize, btnY + buttonSize, btnColor);
                UIScaleManager.drawScaledCenteredString(graphics, font, "X", btnX + buttonSize / 2, btnY + inputLabelOffset, DesignTokens.AreaBuilder.TEXT_WHITE);
            }
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            UIScaleManager.drawScaledCenteredString(graphics, font, "^", getX() + getWidth() / 2, listY + s(2), AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        }
        if (scrollOffset + MAX_VISIBLE_WAYPOINTS < waypoints.size()) {
            UIScaleManager.drawScaledCenteredString(graphics, font, "v", getX() + getWidth() / 2, listY + listHeight - s(10), AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        }

        // Add waypoint section
        int addY = listY + listHeight + addSectionGap;
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.path.add_waypoint")),
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

        // Info text
        int infoY = inputY + s(25);
        UIScaleManager.drawScaledString(graphics, font,
            "Total waypoints: " + waypoints.size(),
            getX(), infoY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check input fields
        if (widthInput.mouseClicked(mouseX, mouseY, button)) return true;
        if (xInput.mouseClicked(mouseX, mouseY, button)) return true;
        if (yInput.mouseClicked(mouseX, mouseY, button)) return true;
        if (zInput.mouseClicked(mouseX, mouseY, button)) return true;

        int listY = getY() + s(60);
        int listHeight = MAX_VISIBLE_WAYPOINTS * s(ROW_HEIGHT);

        // Check add button
        int addBtnX = getX() + s(230);
        int addBtnY = listY + listHeight + s(25);
        int addBtnW = s(50);
        int addBtnH = s(18);
        if (mouseX >= addBtnX && mouseX < addBtnX + addBtnW &&
            mouseY >= addBtnY && mouseY < addBtnY + addBtnH) {
            addWaypoint();
            return true;
        }

        // Check delete buttons
        if (waypoints.size() > 2) {
            int visibleCount = Math.min(waypoints.size(), MAX_VISIBLE_WAYPOINTS);
            for (int i = 0; i < visibleCount; i++) {
                int idx = i + scrollOffset;
                if (idx >= waypoints.size()) break;

                int rowY = listY + i * s(ROW_HEIGHT) + s(3);
                int btnX = getX() + getWidth() - s(BUTTON_SIZE) - s(8);
                int btnY = rowY;

                if (mouseX >= btnX && mouseX < btnX + s(BUTTON_SIZE) &&
                    mouseY >= btnY && mouseY < btnY + s(BUTTON_SIZE)) {
                    waypoints.remove(idx);
                    if (scrollOffset > 0 && scrollOffset >= waypoints.size() - MAX_VISIBLE_WAYPOINTS + 1) {
                        scrollOffset--;
                    }
                    notifyChange();
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listY = getY() + s(60);
        int listHeight = MAX_VISIBLE_WAYPOINTS * s(ROW_HEIGHT);

        if (mouseX >= getX() && mouseX < getX() + getWidth() &&
            mouseY >= listY && mouseY < listY + listHeight) {
            if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (scrollY < 0 && scrollOffset + MAX_VISIBLE_WAYPOINTS < waypoints.size()) {
                scrollOffset++;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (widthInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (xInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (yInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (zInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (widthInput.charTyped(c, modifiers)) return true;
        if (xInput.charTyped(c, modifiers)) return true;
        if (yInput.charTyped(c, modifiers)) return true;
        if (zInput.charTyped(c, modifiers)) return true;
        return super.charTyped(c, modifiers);
    }

    private void addWaypoint() {
        try {
            int x = parseIntOrZero(xInput.getValue());
            int y = parseIntOrZero(yInput.getValue());
            int z = parseIntOrZero(zInput.getValue());

            waypoints.add(new WaypointEntry(x, y, z));

            // Reset inputs
            xInput.setValue("0");
            yInput.setValue("0");
            zInput.setValue("0");

            // Scroll to show new waypoint
            if (waypoints.size() > MAX_VISIBLE_WAYPOINTS) {
                scrollOffset = waypoints.size() - MAX_VISIBLE_WAYPOINTS;
            }

            notifyChange();
        } catch (NumberFormatException ignored) {
            // Invalid input
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
        if (onPathDataChanged != null) {
            onPathDataChanged.accept(getPathNbt());
        }
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Objects.requireNonNull(Component.translatable("area.path.waypoints")));
    }

    /**
     * Gets the current corridor width.
     */
    public int getCorridorWidth() {
        return corridorWidth;
    }

    /**
     * Gets the waypoint count.
     */
    public int getWaypointCount() {
        return waypoints.size();
    }

    /**
     * Internal record for waypoint entries (relative coordinates).
     */
    private record WaypointEntry(int x, int y, int z) {}

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }
}
