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
import com.devmod.area.data.AreaOptions;
import com.devmod.area.data.AreaOptions.WallStyle;
import com.devmod.client.ui.core.UIScaleManager;

/**
 * Widget for configuring area construction options.
 * Uses standardized constants from AreaBuilderGuiConstants for consistent layout.
 */
@OnlyIn(Dist.CLIENT)
public class OptionsWidget extends AbstractWidget {

    private AreaOptions options;
    private final Consumer<AreaOptions> onOptionsChanged;

    /** Cached layout info to ensure render and click use same calculations */
    private int wallStyleButtonsPerRow;

    public OptionsWidget(int x, int y, int width, int height,
                        AreaOptions initialOptions,
                        Consumer<AreaOptions> onOptionsChanged) {
        super(x, y, width, height, Component.empty());
        this.options = initialOptions != null ? initialOptions : AreaOptions.DEFAULT;
        this.onOptionsChanged = onOptionsChanged;
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        int currentY = getY();
        int rowHeight = AreaBuilderGuiConstants.scaledRowHeight();
        int buttonHeight = AreaBuilderGuiConstants.scaledOptionButtonHeight();
        int styleButtonWidth = AreaBuilderGuiConstants.scaledStyleButtonWidth();
        int titleGap = AreaBuilderGuiConstants.scaledTitleGap();
        int sectionGap = AreaBuilderGuiConstants.scaledSectionGap();
        int blockGap = AreaBuilderGuiConstants.scaledBlockGap();
        int buttonSpacing = s(4);

        // Calculate buttons per row based on available width
        wallStyleButtonsPerRow = AreaBuilderGuiConstants.buttonsPerRow(getWidth(), styleButtonWidth, buttonSpacing);

        // Title
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.builder.tab.options")),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );
        currentY += titleGap;

        // Structure toggles
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.options.section.structure")),
            getX(), currentY, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        currentY += sectionGap;

        // Walls toggle
        renderToggle(graphics, "area.options.walls", options.hasWalls(),
            getX(), currentY, mouseX, mouseY);
        currentY += rowHeight;

        // Ceiling toggle
        renderToggle(graphics, "area.options.ceiling", options.hasCeiling(),
            getX(), currentY, mouseX, mouseY);
        currentY += rowHeight;

        // Floor toggle
        renderToggle(graphics, "area.options.floor", options.hasFloor(),
            getX(), currentY, mouseX, mouseY);
        currentY += rowHeight + blockGap;

        // Wall style section
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.options.wall_style")),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
        currentY += sectionGap;

        // Wall style buttons with proper row wrapping
        WallStyle[] styles = WallStyle.values();
        for (int i = 0; i < styles.length; i++) {
            WallStyle style = styles[i];
            int col = i % wallStyleButtonsPerRow;
            int row = i / wallStyleButtonsPerRow;

            int btnX = getX() + col * (styleButtonWidth + buttonSpacing);
            int btnY = currentY + row * rowHeight;

            boolean isSelected = options.wallStyle() == style;
            boolean isHovered = mouseX >= btnX && mouseX < btnX + styleButtonWidth &&
                               mouseY >= btnY && mouseY < btnY + buttonHeight;

            int bgColor = isSelected ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE :
                         (isHovered ? AreaBuilderGuiConstants.COLOR_HOVER :
                                     AreaBuilderGuiConstants.COLOR_PANEL);

            graphics.fill(btnX, btnY, btnX + styleButtonWidth, btnY + buttonHeight, bgColor);
            graphics.renderOutline(btnX, btnY, styleButtonWidth, buttonHeight,
                isSelected ? AreaBuilderGuiConstants.COLOR_SELECTED_BORDER : AreaBuilderGuiConstants.COLOR_BORDER);

            String styleName = Component.translatable("area.wall_style." + style.getSerializedName()).getString();
            int maxTextWidth = styleButtonWidth - s(8);
            if (UIScaleManager.getScaledStringWidth(font, styleName) > maxTextWidth) {
                // Truncate with ellipsis
                while (styleName.length() > 2 && UIScaleManager.getScaledStringWidth(font, styleName + "..") > maxTextWidth) {
                    styleName = styleName.substring(0, styleName.length() - 1);
                }
                styleName = styleName + "..";
            }
            UIScaleManager.drawScaledCenteredString(graphics, font, styleName,
                btnX + styleButtonWidth / 2, btnY + s(5),
                isSelected ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        }

        // Calculate rows used by wall style buttons
        int wallStyleRows = (styles.length + wallStyleButtonsPerRow - 1) / wallStyleButtonsPerRow;
        currentY += wallStyleRows * rowHeight + blockGap;

        // Additional options
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.options.section.gameplay")),
            getX(), currentY, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        currentY += sectionGap;

        // Spawn portals toggle
        renderToggle(graphics, "area.options.spawn_portals", options.spawnPortals(),
            getX(), currentY, mouseX, mouseY);
        currentY += rowHeight;

        // Allow building toggle
        renderToggle(graphics, "area.options.allow_building", options.allowBuilding(),
            getX(), currentY, mouseX, mouseY);
    }

    private void renderToggle(GuiGraphics graphics, String translationKey, boolean value,
                             int x, int y, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        int toggleWidth = AreaBuilderGuiConstants.scaledToggleWidth();
        int buttonHeight = AreaBuilderGuiConstants.scaledOptionButtonHeight();

        // Toggle button
        boolean isHovered = mouseX >= x && mouseX < x + toggleWidth &&
                           mouseY >= y && mouseY < y + buttonHeight;

        int bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF;
        if (isHovered) {
            bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON_HOVER : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF_HOVER;
        }

        graphics.fill(x, y, x + toggleWidth, y + buttonHeight, bgColor);
        graphics.renderOutline(x, y, toggleWidth, buttonHeight,
            AreaBuilderGuiConstants.COLOR_BORDER);

        String toggleText = Component.translatable(value ? "area.toggle.on" : "area.toggle.off").getString();
        UIScaleManager.drawScaledCenteredString(graphics, font, toggleText, x + toggleWidth / 2, y + s(5), AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);

        // Label
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable(Objects.requireNonNull(translationKey))),
            x + toggleWidth + s(8), y + s(5),
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        int rowHeight = AreaBuilderGuiConstants.scaledRowHeight();
        int styleButtonWidth = AreaBuilderGuiConstants.scaledStyleButtonWidth();
        int buttonHeight = AreaBuilderGuiConstants.scaledOptionButtonHeight();
        int titleGap = AreaBuilderGuiConstants.scaledTitleGap();
        int sectionGap = AreaBuilderGuiConstants.scaledSectionGap();
        int blockGap = AreaBuilderGuiConstants.scaledBlockGap();
        int buttonSpacing = s(4);

        int currentY = getY() + titleGap + sectionGap;

        // Walls toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            options = options.withWalls(!options.hasWalls());
            notifyChange();
            return;
        }
        currentY += rowHeight;

        // Ceiling toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            options = options.withCeiling(!options.hasCeiling());
            notifyChange();
            return;
        }
        currentY += rowHeight;

        // Floor toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            options = options.withFloor(!options.hasFloor());
            notifyChange();
            return;
        }
        currentY += rowHeight + blockGap + sectionGap;

        // Wall style buttons - use cached layout from render
        WallStyle[] styles = WallStyle.values();
        int buttonsPerRow = wallStyleButtonsPerRow > 0 ? wallStyleButtonsPerRow :
            AreaBuilderGuiConstants.buttonsPerRow(getWidth(), styleButtonWidth, buttonSpacing);

        for (int i = 0; i < styles.length; i++) {
            WallStyle style = styles[i];
            int col = i % buttonsPerRow;
            int row = i / buttonsPerRow;

            int btnX = getX() + col * (styleButtonWidth + buttonSpacing);
            int btnY = currentY + row * rowHeight;

            if (mouseX >= btnX && mouseX < btnX + styleButtonWidth &&
                mouseY >= btnY && mouseY < btnY + buttonHeight) {
                if (options.wallStyle() != style) {
                    options = options.withWallStyle(style);
                    notifyChange();
                }
                return;
            }
        }

        // Calculate rows used by wall style buttons
        int wallStyleRows = (styles.length + buttonsPerRow - 1) / buttonsPerRow;
        currentY += wallStyleRows * rowHeight + blockGap + sectionGap;

        // Spawn portals toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            options = options.withSpawnPortals(!options.spawnPortals());
            notifyChange();
            return;
        }
        currentY += rowHeight;

        // Allow building toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            options = options.withAllowBuilding(!options.allowBuilding());
            notifyChange();
        }
    }

    private boolean isToggleClicked(double mouseX, double mouseY, int x, int y) {
        int toggleWidth = AreaBuilderGuiConstants.scaledToggleWidth();
        int buttonHeight = AreaBuilderGuiConstants.scaledOptionButtonHeight();
        return mouseX >= x && mouseX < x + toggleWidth &&
               mouseY >= y && mouseY < y + buttonHeight;
    }

    private void notifyChange() {
        if (onOptionsChanged != null) {
            onOptionsChanged.accept(options);
        }
    }

    public AreaOptions getOptions() {
        return options;
    }

    public void setOptions(AreaOptions newOptions) {
        if (newOptions != null) {
            this.options = newOptions;
        }
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Objects.requireNonNull(Component.translatable("area.builder.tab.options")));
    }

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }
}
