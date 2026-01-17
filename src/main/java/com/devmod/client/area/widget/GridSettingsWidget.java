package com.devmod.client.area.widget;

import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.area.aesthetic.AreaBuilderGuiConstants;
import com.devmod.area.data.GridSettings;
import com.devmod.area.data.GridSettings.GridAlignment;
import com.devmod.area.data.GridSettings.GridSize;

/**
 * Widget for configuring grid snap settings for area positioning.
 * Used for Plot System alignment and organized area layouts.
 */
@OnlyIn(Dist.CLIENT)
public class GridSettingsWidget extends AbstractWidget {

    private static final int ROW_HEIGHT = 24;
    private static final int TOGGLE_WIDTH = 40;
    private static final int BUTTON_HEIGHT = 18;
    private static final int SIZE_BUTTON_WIDTH = 80;

    private GridSettings settings;
    private final Consumer<GridSettings> onSettingsChanged;

    public GridSettingsWidget(int x, int y, int width, int height,
                             GridSettings initialSettings,
                             Consumer<GridSettings> onSettingsChanged) {
        super(x, y, width, height, Component.empty());
        this.settings = initialSettings != null ? initialSettings : GridSettings.DISABLED;
        this.onSettingsChanged = onSettingsChanged;
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        int currentY = getY();

        // Title
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.grid.enable")),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Master toggle on same line as title
        int toggleX = getX() + getWidth() - TOGGLE_WIDTH;
        renderToggle(graphics, settings.enabled(), toggleX, currentY, mouseX, mouseY);
        currentY += ROW_HEIGHT + 4;

        // If grid is disabled, show "Grid Disabled" message
        if (!settings.enabled()) {
            graphics.drawString(font,
                Component.literal("Grid snap disabled"),
                getX(), currentY,
                AreaBuilderGuiConstants.COLOR_TEXT_MUTED
            );
            return;
        }

        // Grid size section
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.grid.size")),
            getX(), currentY, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        currentY += 14;

        // Grid size buttons
        int btnX = getX();
        for (GridSize size : GridSize.values()) {
            if (size == GridSize.CUSTOM) continue; // Skip custom for now

            boolean isSelected = settings.size() == size;
            boolean isHovered = mouseX >= btnX && mouseX < btnX + SIZE_BUTTON_WIDTH &&
                               mouseY >= currentY && mouseY < currentY + BUTTON_HEIGHT;

            int bgColor = isSelected ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE :
                         (isHovered ? AreaBuilderGuiConstants.COLOR_HOVER :
                                     AreaBuilderGuiConstants.COLOR_PANEL);

            graphics.fill(btnX, currentY, btnX + SIZE_BUTTON_WIDTH, currentY + BUTTON_HEIGHT, bgColor);
            graphics.renderOutline(btnX, currentY, SIZE_BUTTON_WIDTH, BUTTON_HEIGHT,
                isSelected ? AreaBuilderGuiConstants.COLOR_SELECTED_BORDER : AreaBuilderGuiConstants.COLOR_BORDER);

            String sizeName = Component.translatable("area.grid.size_" + size.getId()).getString();
            graphics.drawCenteredString(font, sizeName,
                btnX + SIZE_BUTTON_WIDTH / 2, currentY + 5,
                isSelected ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

            btnX += SIZE_BUTTON_WIDTH + 4;
        }
        currentY += ROW_HEIGHT + 8;

        // Snap options
        renderToggle(graphics, "area.grid.snap_position", settings.snapPosition(),
            getX(), currentY, mouseX, mouseY, true);
        currentY += ROW_HEIGHT;

        renderToggle(graphics, "area.grid.snap_dimensions", settings.snapDimensions(),
            getX(), currentY, mouseX, mouseY, true);
        currentY += ROW_HEIGHT;

        renderToggle(graphics, "area.grid.show_lines", settings.showGridLines(),
            getX(), currentY, mouseX, mouseY, true);
        currentY += ROW_HEIGHT + 8;

        // Alignment section
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.grid.alignment")),
            getX(), currentY, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        currentY += 14;

        // Alignment buttons
        btnX = getX();
        for (GridAlignment alignment : GridAlignment.values()) {
            boolean isSelected = settings.alignment() == alignment;
            boolean isHovered = mouseX >= btnX && mouseX < btnX + SIZE_BUTTON_WIDTH &&
                               mouseY >= currentY && mouseY < currentY + BUTTON_HEIGHT;

            int bgColor = isSelected ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE :
                         (isHovered ? AreaBuilderGuiConstants.COLOR_HOVER :
                                     AreaBuilderGuiConstants.COLOR_PANEL);

            graphics.fill(btnX, currentY, btnX + SIZE_BUTTON_WIDTH, currentY + BUTTON_HEIGHT, bgColor);
            graphics.renderOutline(btnX, currentY, SIZE_BUTTON_WIDTH, BUTTON_HEIGHT,
                isSelected ? AreaBuilderGuiConstants.COLOR_SELECTED_BORDER : AreaBuilderGuiConstants.COLOR_BORDER);

            String alignName = Component.translatable("area.grid.alignment." + alignment.getId()).getString();
            graphics.drawCenteredString(font, alignName,
                btnX + SIZE_BUTTON_WIDTH / 2, currentY + 5,
                isSelected ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

            btnX += SIZE_BUTTON_WIDTH + 4;
        }
    }

    private void renderToggle(GuiGraphics graphics, boolean value, int x, int y, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);

        boolean isHovered = mouseX >= x && mouseX < x + TOGGLE_WIDTH &&
                           mouseY >= y && mouseY < y + BUTTON_HEIGHT;

        int bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF;
        if (isHovered) {
            bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON_HOVER : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF_HOVER;
        }

        graphics.fill(x, y, x + TOGGLE_WIDTH, y + BUTTON_HEIGHT, bgColor);
        graphics.renderOutline(x, y, TOGGLE_WIDTH, BUTTON_HEIGHT, AreaBuilderGuiConstants.COLOR_BORDER);

        Component toggleText = Objects.requireNonNull(Component.translatable(value ? "area.toggle.on" : "area.toggle.off"));
        graphics.drawCenteredString(font, toggleText, x + TOGGLE_WIDTH / 2, y + 5, AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);
    }

    private void renderToggle(GuiGraphics graphics, String translationKey, boolean value,
                             int x, int y, int mouseX, int mouseY, boolean withLabel) {
        renderToggle(graphics, value, x, y, mouseX, mouseY);

        if (withLabel) {
            var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
            graphics.drawString(font,
                Objects.requireNonNull(Component.translatable(Objects.requireNonNull(translationKey))),
                x + TOGGLE_WIDTH + 8, y + 5,
                AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
            );
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        int currentY = getY();

        // Master toggle
        int toggleX = getX() + getWidth() - TOGGLE_WIDTH;
        if (isToggleClicked(mouseX, mouseY, toggleX, currentY)) {
            settings = settings.withEnabled(!settings.enabled());
            notifyChange();
            return;
        }
        currentY += ROW_HEIGHT + 4;

        if (!settings.enabled()) {
            return; // Grid disabled, no other controls active
        }

        currentY += 14; // Skip "Grid Size" label

        // Grid size buttons
        int btnX = getX();
        for (GridSize size : GridSize.values()) {
            if (size == GridSize.CUSTOM) continue;

            if (mouseX >= btnX && mouseX < btnX + SIZE_BUTTON_WIDTH &&
                mouseY >= currentY && mouseY < currentY + BUTTON_HEIGHT) {
                if (settings.size() != size) {
                    settings = settings.withSize(size);
                    notifyChange();
                }
                return;
            }
            btnX += SIZE_BUTTON_WIDTH + 4;
        }
        currentY += ROW_HEIGHT + 8;

        // Snap position toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            settings = settings.withSnapPosition(!settings.snapPosition());
            notifyChange();
            return;
        }
        currentY += ROW_HEIGHT;

        // Snap dimensions toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            settings = settings.withSnapDimensions(!settings.snapDimensions());
            notifyChange();
            return;
        }
        currentY += ROW_HEIGHT;

        // Show grid lines toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            settings = settings.withShowGridLines(!settings.showGridLines());
            notifyChange();
            return;
        }
        currentY += ROW_HEIGHT + 8;

        currentY += 14; // Skip "Alignment" label

        // Alignment buttons
        btnX = getX();
        for (GridAlignment alignment : GridAlignment.values()) {
            if (mouseX >= btnX && mouseX < btnX + SIZE_BUTTON_WIDTH &&
                mouseY >= currentY && mouseY < currentY + BUTTON_HEIGHT) {
                if (settings.alignment() != alignment) {
                    settings = settings.withAlignment(alignment);
                    notifyChange();
                }
                return;
            }
            btnX += SIZE_BUTTON_WIDTH + 4;
        }
    }

    private boolean isToggleClicked(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + TOGGLE_WIDTH &&
               mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    private void notifyChange() {
        if (onSettingsChanged != null) {
            onSettingsChanged.accept(settings);
        }
    }

    public GridSettings getSettings() {
        return settings;
    }

    public void setSettings(GridSettings newSettings) {
        if (newSettings != null) {
            this.settings = newSettings;
        }
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Objects.requireNonNull(Component.translatable("area.grid.enable")));
    }
}
