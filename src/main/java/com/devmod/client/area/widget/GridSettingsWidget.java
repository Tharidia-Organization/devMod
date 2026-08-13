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

import com.devmod.client.area.AreaBuilderGuiConstants;
import com.devmod.area.data.GridSettings;
import com.devmod.area.data.GridSettings.GridAlignment;
import com.devmod.area.data.GridSettings.GridSize;
import com.devmod.client.ui.core.UIScaleManager;

/**
 * Widget for configuring grid snap settings for area positioning.
 * Used for Plot System alignment and organized area layouts.
 * Uses standardized constants from AreaBuilderGuiConstants for consistent layout.
 */
@OnlyIn(Dist.CLIENT)
public class GridSettingsWidget extends AbstractWidget {

    private GridSettings settings;
    private final Consumer<GridSettings> onSettingsChanged;

    /** Cached layout info to ensure render and click use same calculations */
    private int sizeButtonsPerRow;
    private int alignmentButtonsPerRow;

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
        int rowHeight = AreaBuilderGuiConstants.scaledRowHeight();
        int buttonHeight = AreaBuilderGuiConstants.scaledOptionButtonHeight();
        int toggleWidth = AreaBuilderGuiConstants.scaledToggleWidth();
        int sizeButtonWidth = AreaBuilderGuiConstants.scaledSizeButtonWidth();
        int headerGap = s(4);
        int sectionGap = AreaBuilderGuiConstants.scaledSectionGap();
        int blockGap = AreaBuilderGuiConstants.scaledBlockGap();
        int buttonSpacing = s(4);

        // Calculate buttons per row based on available width
        sizeButtonsPerRow = AreaBuilderGuiConstants.buttonsPerRow(getWidth(), sizeButtonWidth, buttonSpacing);
        alignmentButtonsPerRow = sizeButtonsPerRow; // Same width buttons

        // Title
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.grid.enable")),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Master toggle on same line as title
        int toggleX = getX() + getWidth() - toggleWidth;
        renderToggle(graphics, settings.enabled(), toggleX, currentY, mouseX, mouseY);
        currentY += rowHeight + headerGap;

        // If grid is disabled, show "Grid Disabled" message
        if (!settings.enabled()) {
            UIScaleManager.drawScaledString(graphics, font,
                "Grid snap disabled",
                getX(), currentY,
                AreaBuilderGuiConstants.COLOR_TEXT_MUTED
            );
            return;
        }

        // Grid size section
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.grid.size")),
            getX(), currentY, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        currentY += sectionGap;

        // Grid size buttons with proper row wrapping
        GridSize[] sizes = GridSize.values();
        int sizeCount = 0;
        for (GridSize size : sizes) {
            if (size == GridSize.CUSTOM) continue;
            sizeCount++;
        }

        int sizeIndex = 0;
        for (GridSize size : sizes) {
            if (size == GridSize.CUSTOM) continue;

            int col = sizeIndex % sizeButtonsPerRow;
            int row = sizeIndex / sizeButtonsPerRow;

            int btnX = getX() + col * (sizeButtonWidth + buttonSpacing);
            int btnY = currentY + row * rowHeight;

            boolean isSelected = settings.size() == size;
            boolean isHovered = mouseX >= btnX && mouseX < btnX + sizeButtonWidth &&
                               mouseY >= btnY && mouseY < btnY + buttonHeight;

            int bgColor = isSelected ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE :
                         (isHovered ? AreaBuilderGuiConstants.COLOR_HOVER :
                                     AreaBuilderGuiConstants.COLOR_PANEL);

            graphics.fill(btnX, btnY, btnX + sizeButtonWidth, btnY + buttonHeight, bgColor);
            graphics.renderOutline(btnX, btnY, sizeButtonWidth, buttonHeight,
                isSelected ? AreaBuilderGuiConstants.COLOR_SELECTED_BORDER : AreaBuilderGuiConstants.COLOR_BORDER);

            String sizeName = Component.translatable("area.grid.size_" + size.getId()).getString();
            UIScaleManager.drawScaledCenteredString(graphics, font, sizeName,
                btnX + sizeButtonWidth / 2, btnY + s(5),
                isSelected ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

            sizeIndex++;
        }

        // Calculate rows used by size buttons
        int sizeRows = (sizeCount + sizeButtonsPerRow - 1) / sizeButtonsPerRow;
        currentY += sizeRows * rowHeight + blockGap;

        // Snap options
        renderToggle(graphics, "area.grid.snap_position", settings.snapPosition(),
            getX(), currentY, mouseX, mouseY, true);
        currentY += rowHeight;

        renderToggle(graphics, "area.grid.snap_dimensions", settings.snapDimensions(),
            getX(), currentY, mouseX, mouseY, true);
        currentY += rowHeight;

        renderToggle(graphics, "area.grid.show_lines", settings.showGridLines(),
            getX(), currentY, mouseX, mouseY, true);
        currentY += rowHeight + blockGap;

        // Alignment section
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.grid.alignment")),
            getX(), currentY, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        currentY += sectionGap;

        // Alignment buttons with proper row wrapping
        GridAlignment[] alignments = GridAlignment.values();
        for (int i = 0; i < alignments.length; i++) {
            GridAlignment alignment = alignments[i];
            int col = i % alignmentButtonsPerRow;
            int row = i / alignmentButtonsPerRow;

            int btnX = getX() + col * (sizeButtonWidth + buttonSpacing);
            int btnY = currentY + row * rowHeight;

            boolean isSelected = settings.alignment() == alignment;
            boolean isHovered = mouseX >= btnX && mouseX < btnX + sizeButtonWidth &&
                               mouseY >= btnY && mouseY < btnY + buttonHeight;

            int bgColor = isSelected ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE :
                         (isHovered ? AreaBuilderGuiConstants.COLOR_HOVER :
                                     AreaBuilderGuiConstants.COLOR_PANEL);

            graphics.fill(btnX, btnY, btnX + sizeButtonWidth, btnY + buttonHeight, bgColor);
            graphics.renderOutline(btnX, btnY, sizeButtonWidth, buttonHeight,
                isSelected ? AreaBuilderGuiConstants.COLOR_SELECTED_BORDER : AreaBuilderGuiConstants.COLOR_BORDER);

            String alignName = Component.translatable("area.grid.alignment." + alignment.getId()).getString();
            UIScaleManager.drawScaledCenteredString(graphics, font, alignName,
                btnX + sizeButtonWidth / 2, btnY + s(5),
                isSelected ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        }
    }

    private void renderToggle(GuiGraphics graphics, boolean value, int x, int y, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        int toggleWidth = AreaBuilderGuiConstants.scaledToggleWidth();
        int buttonHeight = AreaBuilderGuiConstants.scaledOptionButtonHeight();

        boolean isHovered = mouseX >= x && mouseX < x + toggleWidth &&
                           mouseY >= y && mouseY < y + buttonHeight;

        int bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF;
        if (isHovered) {
            bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON_HOVER : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF_HOVER;
        }

        graphics.fill(x, y, x + toggleWidth, y + buttonHeight, bgColor);
        graphics.renderOutline(x, y, toggleWidth, buttonHeight, AreaBuilderGuiConstants.COLOR_BORDER);

        String toggleText = Component.translatable(value ? "area.toggle.on" : "area.toggle.off").getString();
        UIScaleManager.drawScaledCenteredString(graphics, font, toggleText, x + toggleWidth / 2, y + s(5), AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);
    }

    private void renderToggle(GuiGraphics graphics, String translationKey, boolean value,
                             int x, int y, int mouseX, int mouseY, boolean withLabel) {
        renderToggle(graphics, value, x, y, mouseX, mouseY);

        if (withLabel) {
            var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
            UIScaleManager.drawScaledString(graphics, font,
                Objects.requireNonNull(Component.translatable(Objects.requireNonNull(translationKey))),
                x + AreaBuilderGuiConstants.scaledToggleWidth() + s(8), y + s(5),
                AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
            );
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        int currentY = getY();
        int rowHeight = AreaBuilderGuiConstants.scaledRowHeight();
        int sizeButtonWidth = AreaBuilderGuiConstants.scaledSizeButtonWidth();
        int buttonHeight = AreaBuilderGuiConstants.scaledOptionButtonHeight();
        int toggleWidth = AreaBuilderGuiConstants.scaledToggleWidth();
        int sectionGap = AreaBuilderGuiConstants.scaledSectionGap();
        int blockGap = AreaBuilderGuiConstants.scaledBlockGap();
        int buttonSpacing = s(4);
        int headerGap = s(4);

        // Use cached buttons per row from render, or calculate if not set
        int buttonsPerRow = sizeButtonsPerRow > 0 ? sizeButtonsPerRow :
            AreaBuilderGuiConstants.buttonsPerRow(getWidth(), sizeButtonWidth, buttonSpacing);

        // Master toggle
        int toggleX = getX() + getWidth() - toggleWidth;
        if (isToggleClicked(mouseX, mouseY, toggleX, currentY)) {
            settings = settings.withEnabled(!settings.enabled());
            notifyChange();
            return;
        }
        currentY += rowHeight + headerGap;

        if (!settings.enabled()) {
            return; // Grid disabled, no other controls active
        }

        currentY += sectionGap; // Skip "Grid Size" label

        // Grid size buttons
        GridSize[] sizes = GridSize.values();
        int sizeCount = 0;
        for (GridSize size : sizes) {
            if (size != GridSize.CUSTOM) sizeCount++;
        }

        int sizeIndex = 0;
        for (GridSize size : sizes) {
            if (size == GridSize.CUSTOM) continue;

            int col = sizeIndex % buttonsPerRow;
            int row = sizeIndex / buttonsPerRow;

            int btnX = getX() + col * (sizeButtonWidth + buttonSpacing);
            int btnY = currentY + row * rowHeight;

            if (mouseX >= btnX && mouseX < btnX + sizeButtonWidth &&
                mouseY >= btnY && mouseY < btnY + buttonHeight) {
                if (settings.size() != size) {
                    settings = settings.withSize(size);
                    notifyChange();
                }
                return;
            }
            sizeIndex++;
        }

        // Calculate rows used by size buttons
        int sizeRows = (sizeCount + buttonsPerRow - 1) / buttonsPerRow;
        currentY += sizeRows * rowHeight + blockGap;

        // Snap position toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            settings = settings.withSnapPosition(!settings.snapPosition());
            notifyChange();
            return;
        }
        currentY += rowHeight;

        // Snap dimensions toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            settings = settings.withSnapDimensions(!settings.snapDimensions());
            notifyChange();
            return;
        }
        currentY += rowHeight;

        // Show grid lines toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            settings = settings.withShowGridLines(!settings.showGridLines());
            notifyChange();
            return;
        }
        currentY += rowHeight + blockGap;

        currentY += sectionGap; // Skip "Alignment" label

        // Alignment buttons
        GridAlignment[] alignments = GridAlignment.values();
        for (int i = 0; i < alignments.length; i++) {
            GridAlignment alignment = alignments[i];
            int col = i % buttonsPerRow;
            int row = i / buttonsPerRow;

            int btnX = getX() + col * (sizeButtonWidth + buttonSpacing);
            int btnY = currentY + row * rowHeight;

            if (mouseX >= btnX && mouseX < btnX + sizeButtonWidth &&
                mouseY >= btnY && mouseY < btnY + buttonHeight) {
                if (settings.alignment() != alignment) {
                    settings = settings.withAlignment(alignment);
                    notifyChange();
                }
                return;
            }
        }
    }

    private boolean isToggleClicked(double mouseX, double mouseY, int x, int y) {
        int toggleWidth = AreaBuilderGuiConstants.scaledToggleWidth();
        int buttonHeight = AreaBuilderGuiConstants.scaledOptionButtonHeight();
        return mouseX >= x && mouseX < x + toggleWidth &&
               mouseY >= y && mouseY < y + buttonHeight;
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

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }
}
