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
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.area.data.EntitySpawnPoint;
import com.devmod.client.ui.editor.core.DesignTokens;
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
        this.maxVisiblePoints = Math.max(1, (height - s(HEADER_HEIGHT) - s(60)) / (s(ROW_HEIGHT) + s(SECTION_SPACING)));
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        int currentY = getY();
        int rowHeight = s(ROW_HEIGHT);
        int buttonSize = s(BUTTON_SIZE);
        int toggleWidth = s(TOGGLE_WIDTH);
        int sectionSpacing = s(SECTION_SPACING);
        int headerHeight = s(HEADER_HEIGHT);
        int listInset = s(4);
        int listWidthInset = s(8);
        updateMaxVisiblePoints();

        // Title
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.spawns.title")),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Global toggle
        int toggleX = getX() + getWidth() - toggleWidth;
        renderToggle(graphics, config.globalEnabled(), toggleX, currentY, mouseX, mouseY);
        currentY += headerHeight;

        // If globally disabled, show disabled message
        if (!config.globalEnabled()) {
            UIScaleManager.drawScaledString(graphics, font,
                "Spawning disabled",
                getX(), currentY,
                AreaBuilderGuiConstants.COLOR_TEXT_MUTED
            );
            return;
        }

        // Spawn point list background
        int listHeight = maxVisiblePoints * (rowHeight + sectionSpacing);
        graphics.fill(getX(), currentY, getX() + getWidth(), currentY + listHeight, AreaBuilderGuiConstants.COLOR_PANEL);
        graphics.renderOutline(getX(), currentY, getWidth(), listHeight, AreaBuilderGuiConstants.COLOR_BORDER);

        // Render spawn points or empty message
        if (config.getSpawnPointCount() == 0) {
            UIScaleManager.drawScaledCenteredString(graphics, font,
                Component.translatable("area.spawns.empty").getString(),
                getX() + getWidth() / 2, currentY + listHeight / 2 - s(4),
                AreaBuilderGuiConstants.COLOR_TEXT_MUTED
            );
        } else {
            int pointY = currentY + listInset;
            for (int i = scrollOffset; i < Math.min(config.getSpawnPointCount(), scrollOffset + maxVisiblePoints); i++) {
                EntitySpawnPoint point = config.getSpawnPoint(i);
                boolean isSelected = i == selectedIndex;
                boolean isHovered = mouseY >= pointY && mouseY < pointY + rowHeight &&
                                   mouseX >= getX() && mouseX < getX() + getWidth();

                renderSpawnPoint(graphics, point, getX() + listInset, pointY, getWidth() - listWidthInset,
                               isSelected, isHovered, mouseX, mouseY);
                pointY += rowHeight + sectionSpacing;
            }
        }

        currentY += listHeight + s(8);

        // Add spawn point button
        boolean canAdd = config.canAddSpawnPoint();
        boolean addHovered = canAdd && mouseX >= getX() && mouseX < getX() + s(140) &&
                            mouseY >= currentY && mouseY < currentY + buttonSize;
        int addBgColor = addHovered ? AreaBuilderGuiConstants.COLOR_HOVER : AreaBuilderGuiConstants.COLOR_PANEL;
        graphics.fill(getX(), currentY, getX() + s(140), currentY + buttonSize, addBgColor);
        graphics.renderOutline(getX(), currentY, s(140), buttonSize, AreaBuilderGuiConstants.COLOR_BORDER);
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.spawns.add")),
            getX() + s(6), currentY + s(5),
            canAdd ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_MUTED
        );

        currentY += buttonSize + s(12);

        // Summary
        int totalCount = config.getTotalSpawnCount();
        UIScaleManager.drawScaledString(graphics, font,
            "Total entities: " + totalCount,
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
    }

    private void renderSpawnPoint(GuiGraphics graphics, EntitySpawnPoint point,
                                 int x, int y, int width, boolean isSelected, boolean isHovered,
                                 int mouseX, int mouseY) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        int rowHeight = s(ROW_HEIGHT);
        int buttonSize = s(BUTTON_SIZE);
        int toggleWidth = s(TOGGLE_WIDTH);
        int pad = s(4);
        int innerPad = s(10);

        // Background
        int bgColor = isSelected ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE :
                     (isHovered ? AreaBuilderGuiConstants.COLOR_HOVER : DesignTokens.AreaBuilder.TRANSPARENT);
        if (bgColor != 0) {
            graphics.fill(x, y, x + width, y + rowHeight, bgColor);
        }

        // Enabled indicator
        int indicatorColor = point.enabled() ? DesignTokens.AreaBuilder.INDICATOR_ENABLED : DesignTokens.AreaBuilder.INDICATOR_DISABLED;
        graphics.fill(x, y + s(2), x + s(4), y + rowHeight - s(2), indicatorColor);

        // Entity name
        UIScaleManager.drawScaledString(graphics, font,
            point.getEntityDisplayName(),
            x + innerPad, y + pad,
            point.enabled() ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_MUTED
        );

        // Position
        BlockPos pos = point.position();
        String posText = String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
        UIScaleManager.drawScaledString(graphics, font, posText, x + innerPad, y + s(16),
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

        // Spawn count
        String countText = "x" + point.spawnCount();
        UIScaleManager.drawScaledString(graphics, font, countText, x + s(140), y + pad,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

        // Respawn delay
        UIScaleManager.drawScaledString(graphics, font, point.getFormattedRespawnDelay(), x + s(140), y + s(16),
            AreaBuilderGuiConstants.COLOR_TEXT_MUTED);

        // Toggle enabled button
        int toggleX = x + width - toggleWidth - buttonSize - s(8);
        boolean toggleHovered = mouseX >= toggleX && mouseX < toggleX + toggleWidth &&
                               mouseY >= y + s(6) && mouseY < y + s(6) + buttonSize;
        renderSmallToggle(graphics, point.enabled(), toggleX, y + s(6), toggleHovered);

        // Remove button (X)
        int removeX = x + width - buttonSize - s(4);
        boolean removeHovered = mouseX >= removeX && mouseX < removeX + buttonSize &&
                               mouseY >= y + s(6) && mouseY < y + s(6) + buttonSize;
        int removeBgColor = removeHovered ? DesignTokens.AreaBuilder.DANGER_BG_HOVER : AreaBuilderGuiConstants.COLOR_PANEL;
        graphics.fill(removeX, y + s(6), removeX + buttonSize, y + s(6) + buttonSize, removeBgColor);
        graphics.renderOutline(removeX, y + s(6), buttonSize, buttonSize, AreaBuilderGuiConstants.COLOR_BORDER);
        UIScaleManager.drawScaledCenteredString(graphics, font, "X", removeX + buttonSize / 2, y + s(10),
            removeHovered ? DesignTokens.AreaBuilder.TEXT_WHITE : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
    }

    private void renderToggle(GuiGraphics graphics, boolean value, int x, int y, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        int toggleWidth = s(TOGGLE_WIDTH);
        int buttonSize = s(BUTTON_SIZE);

        boolean isHovered = mouseX >= x && mouseX < x + toggleWidth &&
                           mouseY >= y && mouseY < y + buttonSize;

        int bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF;
        if (isHovered) {
            bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON_HOVER : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF_HOVER;
        }

        graphics.fill(x, y, x + toggleWidth, y + buttonSize, bgColor);
        graphics.renderOutline(x, y, toggleWidth, buttonSize, AreaBuilderGuiConstants.COLOR_BORDER);

        String toggleText = Component.translatable(value ? "area.toggle.on" : "area.toggle.off").getString();
        UIScaleManager.drawScaledCenteredString(graphics, font, toggleText, x + toggleWidth / 2, y + s(5), AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);
    }

    private void renderSmallToggle(GuiGraphics graphics, boolean value, int x, int y, boolean isHovered) {
        int toggleWidth = s(TOGGLE_WIDTH);
        int buttonSize = s(BUTTON_SIZE);
        int bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF;
        if (isHovered) {
            bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON_HOVER : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF_HOVER;
        }

        graphics.fill(x, y, x + toggleWidth, y + buttonSize, bgColor);
        graphics.renderOutline(x, y, toggleWidth, buttonSize, AreaBuilderGuiConstants.COLOR_BORDER);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        int toggleWidth = s(TOGGLE_WIDTH);
        int buttonSize = s(BUTTON_SIZE);
        int rowHeight = s(ROW_HEIGHT);
        int sectionSpacing = s(SECTION_SPACING);
        // Global toggle
        int toggleX = getX() + getWidth() - toggleWidth;
        if (mouseX >= toggleX && mouseX < toggleX + toggleWidth &&
            mouseY >= getY() && mouseY < getY() + buttonSize) {
            config = config.withGlobalEnabled(!config.globalEnabled());
            notifyChange();
            return;
        }

        if (!config.globalEnabled()) {
            return;
        }

        int listY = getY() + s(HEADER_HEIGHT);
        updateMaxVisiblePoints();
        int listHeight = maxVisiblePoints * (rowHeight + sectionSpacing);

        // Check add button
        int addY = listY + listHeight + s(8);
        if (mouseX >= getX() && mouseX < getX() + s(140) &&
            mouseY >= addY && mouseY < addY + buttonSize) {
            if (config.canAddSpawnPoint()) {
                addNewSpawnPoint();
            }
            return;
        }

        // Check spawn point clicks
        int pointY = listY + s(4);
        for (int i = scrollOffset; i < Math.min(config.getSpawnPointCount(), scrollOffset + maxVisiblePoints); i++) {
            if (mouseY >= pointY && mouseY < pointY + rowHeight) {
                int pointWidth = getWidth() - s(8);
                int pointX = getX() + s(4);

                // Remove button
                int removeX = pointX + pointWidth - buttonSize - s(4);
                if (mouseX >= removeX && mouseX < removeX + buttonSize &&
                    mouseY >= pointY + s(6) && mouseY < pointY + s(6) + buttonSize) {
                    removeSpawnPoint(i);
                    return;
                }

                // Toggle enabled
                int toggleX2 = pointX + pointWidth - toggleWidth - buttonSize - s(8);
                if (mouseX >= toggleX2 && mouseX < toggleX2 + toggleWidth &&
                    mouseY >= pointY + s(6) && mouseY < pointY + s(6) + buttonSize) {
                    toggleSpawnPointEnabled(i);
                    return;
                }

                // Select for editing
                selectedIndex = (selectedIndex == i) ? -1 : i;
                return;
            }
            pointY += rowHeight + sectionSpacing;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        updateMaxVisiblePoints();
        int maxScroll = Math.max(0, config.getSpawnPointCount() - maxVisiblePoints);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
        return true;
    }

    private void addNewSpawnPoint() {
        // Add a zombie spawn point at area center by default
        EntitySpawnPoint newPoint = EntitySpawnPoint.create(
            Objects.requireNonNull(ResourceLocation.withDefaultNamespace("zombie")),
            Objects.requireNonNull(areaCenter)
        );
        config = config.withSpawnPointAdded(Objects.requireNonNull(newPoint));
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

    private void updateMaxVisiblePoints() {
        int rowHeight = s(ROW_HEIGHT);
        int sectionSpacing = s(SECTION_SPACING);
        int headerHeight = s(HEADER_HEIGHT);
        int available = Math.max(0, getHeight() - headerHeight - s(60));
        maxVisiblePoints = Math.max(1, available / Math.max(1, rowHeight + sectionSpacing));
    }

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }
}
