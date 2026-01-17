package com.devmod.client.area.widget;

import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.area.aesthetic.AreaBuilderGuiConstants;
import com.devmod.area.data.EntitySpawnConfig;
import com.devmod.area.data.EntitySpawnPoint;

/**
 * Widget for configuring entity spawn points within an area.
 * Displays a list of spawn points with controls to add, remove, and configure each.
 */
@OnlyIn(Dist.CLIENT)
public class EntitySpawnWidget extends AbstractWidget {

    private static final int ROW_HEIGHT = 32;
    private static final int BUTTON_SIZE = 18;
    private static final int TOGGLE_WIDTH = 36;
    private static final int SECTION_SPACING = 4;
    private static final int HEADER_HEIGHT = 30;

    private EntitySpawnConfig config;
    private final Consumer<EntitySpawnConfig> onConfigChanged;
    private final BlockPos areaCenter;

    // Scrolling
    private int scrollOffset = 0;
    private int maxVisiblePoints;

    // Selection for editing
    private int selectedIndex = -1;

    public EntitySpawnWidget(int x, int y, int width, int height,
                            EntitySpawnConfig initialConfig,
                            BlockPos areaCenter,
                            Consumer<EntitySpawnConfig> onConfigChanged) {
        super(x, y, width, height, Component.empty());
        this.config = initialConfig != null ? initialConfig : EntitySpawnConfig.empty();
        this.areaCenter = areaCenter != null ? areaCenter : BlockPos.ZERO;
        this.onConfigChanged = onConfigChanged;
        this.maxVisiblePoints = (height - HEADER_HEIGHT - 60) / (ROW_HEIGHT + SECTION_SPACING);
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        int currentY = getY();

        // Title
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.spawns.title")),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Global toggle
        int toggleX = getX() + getWidth() - TOGGLE_WIDTH;
        renderToggle(graphics, config.globalEnabled(), toggleX, currentY, mouseX, mouseY);
        currentY += HEADER_HEIGHT;

        // If globally disabled, show disabled message
        if (!config.globalEnabled()) {
            graphics.drawString(font,
                Component.literal("Spawning disabled"),
                getX(), currentY,
                AreaBuilderGuiConstants.COLOR_TEXT_MUTED
            );
            return;
        }

        // Spawn point list background
        int listHeight = maxVisiblePoints * (ROW_HEIGHT + SECTION_SPACING);
        graphics.fill(getX(), currentY, getX() + getWidth(), currentY + listHeight, AreaBuilderGuiConstants.COLOR_PANEL);
        graphics.renderOutline(getX(), currentY, getWidth(), listHeight, AreaBuilderGuiConstants.COLOR_BORDER);

        // Render spawn points or empty message
        if (config.getSpawnPointCount() == 0) {
            graphics.drawCenteredString(font,
                Objects.requireNonNull(Component.translatable("area.spawns.empty")),
                getX() + getWidth() / 2, currentY + listHeight / 2 - 4,
                AreaBuilderGuiConstants.COLOR_TEXT_MUTED
            );
        } else {
            int pointY = currentY + 4;
            for (int i = scrollOffset; i < Math.min(config.getSpawnPointCount(), scrollOffset + maxVisiblePoints); i++) {
                EntitySpawnPoint point = config.getSpawnPoint(i);
                boolean isSelected = i == selectedIndex;
                boolean isHovered = mouseY >= pointY && mouseY < pointY + ROW_HEIGHT &&
                                   mouseX >= getX() && mouseX < getX() + getWidth();

                renderSpawnPoint(graphics, point, i, getX() + 4, pointY, getWidth() - 8,
                               isSelected, isHovered, mouseX, mouseY);
                pointY += ROW_HEIGHT + SECTION_SPACING;
            }
        }

        currentY += listHeight + 8;

        // Add spawn point button
        boolean canAdd = config.canAddSpawnPoint();
        boolean addHovered = canAdd && mouseX >= getX() && mouseX < getX() + 140 &&
                            mouseY >= currentY && mouseY < currentY + BUTTON_SIZE;
        int addBgColor = addHovered ? AreaBuilderGuiConstants.COLOR_HOVER : AreaBuilderGuiConstants.COLOR_PANEL;
        graphics.fill(getX(), currentY, getX() + 140, currentY + BUTTON_SIZE, addBgColor);
        graphics.renderOutline(getX(), currentY, 140, BUTTON_SIZE, AreaBuilderGuiConstants.COLOR_BORDER);
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.spawns.add")),
            getX() + 6, currentY + 5,
            canAdd ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_MUTED
        );

        currentY += BUTTON_SIZE + 12;

        // Summary
        int totalCount = config.getTotalSpawnCount();
        graphics.drawString(font,
            Component.literal("Total entities: " + totalCount),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
    }

    private void renderSpawnPoint(GuiGraphics graphics, EntitySpawnPoint point, int index,
                                 int x, int y, int width, boolean isSelected, boolean isHovered,
                                 int mouseX, int mouseY) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);

        // Background
        int bgColor = isSelected ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE :
                     (isHovered ? AreaBuilderGuiConstants.COLOR_HOVER : 0x00000000);
        if (bgColor != 0) {
            graphics.fill(x, y, x + width, y + ROW_HEIGHT, bgColor);
        }

        // Enabled indicator
        int indicatorColor = point.enabled() ? 0xFF44CC44 : 0xFF666666;
        graphics.fill(x, y + 2, x + 4, y + ROW_HEIGHT - 2, indicatorColor);

        // Entity name
        graphics.drawString(font,
            point.getEntityDisplayName(),
            x + 10, y + 4,
            point.enabled() ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_MUTED
        );

        // Position
        BlockPos pos = point.position();
        String posText = String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
        graphics.drawString(font, posText, x + 10, y + 16,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

        // Spawn count
        String countText = "x" + point.spawnCount();
        graphics.drawString(font, countText, x + 140, y + 4,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

        // Respawn delay
        graphics.drawString(font, point.getFormattedRespawnDelay(), x + 140, y + 16,
            AreaBuilderGuiConstants.COLOR_TEXT_MUTED);

        // Toggle enabled button
        int toggleX = x + width - TOGGLE_WIDTH - BUTTON_SIZE - 8;
        boolean toggleHovered = mouseX >= toggleX && mouseX < toggleX + TOGGLE_WIDTH &&
                               mouseY >= y + 6 && mouseY < y + 6 + BUTTON_SIZE;
        renderSmallToggle(graphics, point.enabled(), toggleX, y + 6, toggleHovered);

        // Remove button (X)
        int removeX = x + width - BUTTON_SIZE - 4;
        boolean removeHovered = mouseX >= removeX && mouseX < removeX + BUTTON_SIZE &&
                               mouseY >= y + 6 && mouseY < y + 6 + BUTTON_SIZE;
        int removeBgColor = removeHovered ? 0xFFCC4444 : AreaBuilderGuiConstants.COLOR_PANEL;
        graphics.fill(removeX, y + 6, removeX + BUTTON_SIZE, y + 6 + BUTTON_SIZE, removeBgColor);
        graphics.renderOutline(removeX, y + 6, BUTTON_SIZE, BUTTON_SIZE, AreaBuilderGuiConstants.COLOR_BORDER);
        graphics.drawCenteredString(font, "X", removeX + BUTTON_SIZE / 2, y + 10,
            removeHovered ? 0xFFFFFFFF : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
    }

    private void renderToggle(GuiGraphics graphics, boolean value, int x, int y, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);

        boolean isHovered = mouseX >= x && mouseX < x + TOGGLE_WIDTH &&
                           mouseY >= y && mouseY < y + BUTTON_SIZE;

        int bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF;
        if (isHovered) {
            bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON_HOVER : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF_HOVER;
        }

        graphics.fill(x, y, x + TOGGLE_WIDTH, y + BUTTON_SIZE, bgColor);
        graphics.renderOutline(x, y, TOGGLE_WIDTH, BUTTON_SIZE, AreaBuilderGuiConstants.COLOR_BORDER);

        Component toggleText = Objects.requireNonNull(Component.translatable(value ? "area.toggle.on" : "area.toggle.off"));
        graphics.drawCenteredString(font, toggleText, x + TOGGLE_WIDTH / 2, y + 5, AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);
    }

    private void renderSmallToggle(GuiGraphics graphics, boolean value, int x, int y, boolean isHovered) {
        int bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF;
        if (isHovered) {
            bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON_HOVER : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF_HOVER;
        }

        graphics.fill(x, y, x + TOGGLE_WIDTH, y + BUTTON_SIZE, bgColor);
        graphics.renderOutline(x, y, TOGGLE_WIDTH, BUTTON_SIZE, AreaBuilderGuiConstants.COLOR_BORDER);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        // Global toggle
        int toggleX = getX() + getWidth() - TOGGLE_WIDTH;
        if (mouseX >= toggleX && mouseX < toggleX + TOGGLE_WIDTH &&
            mouseY >= getY() && mouseY < getY() + BUTTON_SIZE) {
            config = config.withGlobalEnabled(!config.globalEnabled());
            notifyChange();
            return;
        }

        if (!config.globalEnabled()) {
            return;
        }

        int listY = getY() + HEADER_HEIGHT;
        int listHeight = maxVisiblePoints * (ROW_HEIGHT + SECTION_SPACING);

        // Check add button
        int addY = listY + listHeight + 8;
        if (mouseX >= getX() && mouseX < getX() + 140 &&
            mouseY >= addY && mouseY < addY + BUTTON_SIZE) {
            if (config.canAddSpawnPoint()) {
                addNewSpawnPoint();
            }
            return;
        }

        // Check spawn point clicks
        int pointY = listY + 4;
        for (int i = scrollOffset; i < Math.min(config.getSpawnPointCount(), scrollOffset + maxVisiblePoints); i++) {
            if (mouseY >= pointY && mouseY < pointY + ROW_HEIGHT) {
                int pointWidth = getWidth() - 8;
                int pointX = getX() + 4;

                // Remove button
                int removeX = pointX + pointWidth - BUTTON_SIZE - 4;
                if (mouseX >= removeX && mouseX < removeX + BUTTON_SIZE &&
                    mouseY >= pointY + 6 && mouseY < pointY + 6 + BUTTON_SIZE) {
                    removeSpawnPoint(i);
                    return;
                }

                // Toggle enabled
                int toggleX2 = pointX + pointWidth - TOGGLE_WIDTH - BUTTON_SIZE - 8;
                if (mouseX >= toggleX2 && mouseX < toggleX2 + TOGGLE_WIDTH &&
                    mouseY >= pointY + 6 && mouseY < pointY + 6 + BUTTON_SIZE) {
                    toggleSpawnPointEnabled(i);
                    return;
                }

                // Select for editing
                selectedIndex = (selectedIndex == i) ? -1 : i;
                return;
            }
            pointY += ROW_HEIGHT + SECTION_SPACING;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, config.getSpawnPointCount() - maxVisiblePoints);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
        return true;
    }

    private void addNewSpawnPoint() {
        // Add a zombie spawn point at area center by default
        EntitySpawnPoint newPoint = EntitySpawnPoint.create(
            ResourceLocation.withDefaultNamespace("zombie"),
            areaCenter
        );
        config = config.withSpawnPointAdded(newPoint);
        notifyChange();
    }

    private void removeSpawnPoint(int index) {
        config = config.withSpawnPointRemoved(index);
        if (selectedIndex >= config.getSpawnPointCount()) {
            selectedIndex = config.getSpawnPointCount() - 1;
        }
        notifyChange();
    }

    private void toggleSpawnPointEnabled(int index) {
        EntitySpawnPoint point = config.getSpawnPoint(index);
        EntitySpawnPoint updated = Objects.requireNonNull(point.withEnabled(!point.enabled()));
        config = config.withSpawnPointUpdated(index, updated);
        notifyChange();
    }

    private void notifyChange() {
        if (onConfigChanged != null) {
            onConfigChanged.accept(config);
        }
    }

    public EntitySpawnConfig getConfig() {
        return config;
    }

    public void setConfig(EntitySpawnConfig newConfig) {
        if (newConfig != null) {
            this.config = newConfig;
        }
    }

    /**
     * Gets the currently selected spawn point index, or -1 if none.
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Updates the entity type for the selected spawn point.
     */
    public void setSelectedEntityType(ResourceLocation entityType) {
        if (selectedIndex >= 0 && selectedIndex < config.getSpawnPointCount()) {
            EntitySpawnPoint point = config.getSpawnPoint(selectedIndex);
            EntitySpawnPoint updated = Objects.requireNonNull(point.withEntityType(Objects.requireNonNull(entityType)));
            config = config.withSpawnPointUpdated(selectedIndex, updated);
            notifyChange();
        }
    }

    /**
     * Updates the spawn count for the selected spawn point.
     */
    public void setSelectedSpawnCount(int count) {
        if (selectedIndex >= 0 && selectedIndex < config.getSpawnPointCount()) {
            EntitySpawnPoint point = config.getSpawnPoint(selectedIndex);
            EntitySpawnPoint updated = Objects.requireNonNull(point.withSpawnCount(count));
            config = config.withSpawnPointUpdated(selectedIndex, updated);
            notifyChange();
        }
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Objects.requireNonNull(Component.translatable("area.spawns.title")));
    }
}
