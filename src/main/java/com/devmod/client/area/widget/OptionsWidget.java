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
 */
@OnlyIn(Dist.CLIENT)
public class OptionsWidget extends AbstractWidget {

    private static final int ROW_HEIGHT = 24;
    private static final int TOGGLE_WIDTH = 40;
    private static final int BUTTON_HEIGHT = 18;
    private static final int STYLE_BUTTON_WIDTH = 70;

    private AreaOptions options;
    private final Consumer<AreaOptions> onOptionsChanged;

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

        // Title
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.builder.tab.options")),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );
        currentY += 16;

        // Structure toggles
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.options.section.structure")),
            getX(), currentY, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        currentY += 14;

        // Walls toggle
        renderToggle(graphics, "area.options.walls", options.hasWalls(),
            getX(), currentY, mouseX, mouseY);
        currentY += ROW_HEIGHT;

        // Ceiling toggle
        renderToggle(graphics, "area.options.ceiling", options.hasCeiling(),
            getX(), currentY, mouseX, mouseY);
        currentY += ROW_HEIGHT;

        // Floor toggle
        renderToggle(graphics, "area.options.floor", options.hasFloor(),
            getX(), currentY, mouseX, mouseY);
        currentY += ROW_HEIGHT + 8;

        // Wall style section
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.options.wall_style")),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
        currentY += 14;

        // Wall style buttons
        int btnX = getX();
        for (WallStyle style : WallStyle.values()) {
            boolean isSelected = options.wallStyle() == style;
            boolean isHovered = mouseX >= btnX && mouseX < btnX + STYLE_BUTTON_WIDTH &&
                               mouseY >= currentY && mouseY < currentY + BUTTON_HEIGHT;

            int bgColor = isSelected ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE :
                         (isHovered ? AreaBuilderGuiConstants.COLOR_HOVER :
                                     AreaBuilderGuiConstants.COLOR_PANEL);

            graphics.fill(btnX, currentY, btnX + STYLE_BUTTON_WIDTH, currentY + BUTTON_HEIGHT, bgColor);
            graphics.renderOutline(btnX, currentY, STYLE_BUTTON_WIDTH, BUTTON_HEIGHT,
                isSelected ? AreaBuilderGuiConstants.COLOR_SELECTED_BORDER : AreaBuilderGuiConstants.COLOR_BORDER);

            String styleName = Component.translatable("area.wall_style." + style.getSerializedName()).getString();
            if (styleName.length() > 10) {
                styleName = styleName.substring(0, 8) + "..";
            }
            UIScaleManager.drawScaledCenteredString(graphics, font, styleName,
                btnX + STYLE_BUTTON_WIDTH / 2, currentY + 5,
                isSelected ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

            btnX += STYLE_BUTTON_WIDTH + 4;

            // Wrap to next line after 2 buttons
            if (btnX > getX() + getWidth() - STYLE_BUTTON_WIDTH) {
                btnX = getX();
                currentY += ROW_HEIGHT;
            }
        }

        currentY += ROW_HEIGHT + 8;

        // Additional options
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.options.section.gameplay")),
            getX(), currentY, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
        currentY += 14;

        // Spawn portals toggle
        renderToggle(graphics, "area.options.spawn_portals", options.spawnPortals(),
            getX(), currentY, mouseX, mouseY);
        currentY += ROW_HEIGHT;

        // Allow building toggle
        renderToggle(graphics, "area.options.allow_building", options.allowBuilding(),
            getX(), currentY, mouseX, mouseY);
    }

    private void renderToggle(GuiGraphics graphics, String translationKey, boolean value,
                             int x, int y, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);

        // Toggle button
        boolean isHovered = mouseX >= x && mouseX < x + TOGGLE_WIDTH &&
                           mouseY >= y && mouseY < y + BUTTON_HEIGHT;

        int bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF;
        if (isHovered) {
            bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON_HOVER : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF_HOVER;
        }

        graphics.fill(x, y, x + TOGGLE_WIDTH, y + BUTTON_HEIGHT, bgColor);
        graphics.renderOutline(x, y, TOGGLE_WIDTH, BUTTON_HEIGHT,
            AreaBuilderGuiConstants.COLOR_BORDER);

        String toggleText = Component.translatable(value ? "area.toggle.on" : "area.toggle.off").getString();
        UIScaleManager.drawScaledCenteredString(graphics, font, toggleText, x + TOGGLE_WIDTH / 2, y + 5, AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);

        // Label
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable(Objects.requireNonNull(translationKey))),
            x + TOGGLE_WIDTH + 8, y + 5,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        int currentY = getY() + 16 + 14;

        // Walls toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            options = options.withWalls(!options.hasWalls());
            notifyChange();
            return;
        }
        currentY += ROW_HEIGHT;

        // Ceiling toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            options = options.withCeiling(!options.hasCeiling());
            notifyChange();
            return;
        }
        currentY += ROW_HEIGHT;

        // Floor toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            options = options.withFloor(!options.hasFloor());
            notifyChange();
            return;
        }
        currentY += ROW_HEIGHT + 8 + 14;

        // Wall style buttons
        int btnX = getX();
        int styleY = currentY;
        for (WallStyle style : WallStyle.values()) {
            if (mouseX >= btnX && mouseX < btnX + STYLE_BUTTON_WIDTH &&
                mouseY >= styleY && mouseY < styleY + BUTTON_HEIGHT) {
                if (options.wallStyle() != style) {
                    options = options.withWallStyle(style);
                    notifyChange();
                }
                return;
            }

            btnX += STYLE_BUTTON_WIDTH + 4;

            // Wrap to next line
            if (btnX > getX() + getWidth() - STYLE_BUTTON_WIDTH) {
                btnX = getX();
                styleY += ROW_HEIGHT;
            }
        }

        // Calculate where gameplay toggles start
        currentY = styleY + ROW_HEIGHT + 8 + 14;

        // Spawn portals toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            options = options.withSpawnPortals(!options.spawnPortals());
            notifyChange();
            return;
        }
        currentY += ROW_HEIGHT;

        // Allow building toggle
        if (isToggleClicked(mouseX, mouseY, getX(), currentY)) {
            options = options.withAllowBuilding(!options.allowBuilding());
            notifyChange();
        }
    }

    private boolean isToggleClicked(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + TOGGLE_WIDTH &&
               mouseY >= y && mouseY < y + BUTTON_HEIGHT;
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
}
