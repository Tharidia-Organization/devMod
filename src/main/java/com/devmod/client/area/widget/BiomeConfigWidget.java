package com.devmod.client.area.widget;

import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import com.google.common.primitives.Longs;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.area.aesthetic.AreaBuilderGuiConstants;
import com.devmod.area.data.BiomeGenerationConfig;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.area.data.BiomeGenerationConfig.TerrainStyle;
import com.devmod.client.ui.AxiomRenderer;

/**
 * Widget for configuring biome generation options.
 * Includes seed, terrain style, and feature toggles.
 */
@OnlyIn(Dist.CLIENT)
public class BiomeConfigWidget extends AbstractWidget {

    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 18;
    private static final int TOGGLE_WIDTH = 40;
    private static final int SEED_FIELD_WIDTH = 120;
    private static final int SECTION_GAP = 8;

    private BiomeGenerationConfig config;
    private boolean biomeEnabled;
    private final Consumer<Boolean> onBiomeEnabledChanged;
    private final Consumer<BiomeGenerationConfig> onConfigChanged;

    private final EditBox seedField;
    private int enableToggleY;
    private int seedLabelY;
    private int seedFieldWidth;
    private int seedFieldY;
    private int randomButtonX;
    private int terrainLabelY;
    private int terrainButtonsY;
    private int featureStartY;

    // Terrain style buttons
    private static final TerrainStyle[] TERRAIN_STYLES = TerrainStyle.values();

    public BiomeConfigWidget(int x, int y, int width, int height,
                            BiomeGenerationConfig initialConfig,
                            boolean biomeEnabled,
                            Consumer<Boolean> onBiomeEnabledChanged,
                            Consumer<BiomeGenerationConfig> onConfigChanged) {
        super(x, y, width, height, Component.empty());
        this.config = initialConfig != null ? initialConfig : BiomeGenerationConfig.DEFAULT;
        this.biomeEnabled = biomeEnabled;
        this.onBiomeEnabledChanged = onBiomeEnabledChanged;
        this.onConfigChanged = onConfigChanged;

        seedField = new EditBox(Objects.requireNonNull(
            net.minecraft.client.Minecraft.getInstance().font, "font"),
            x, y, s(SEED_FIELD_WIDTH), AreaBuilderGuiConstants.scaledFieldHeight(),
            Objects.requireNonNull(Component.translatable("area.biome.seed"), "seedLabel"));
        seedField.setMaxLength(20);
        seedField.setBordered(false);
        seedField.setHint(Objects.requireNonNull(
            Component.translatable("area.biome.seed_hint"), "seedHint"));
        seedField.setFilter(this::isValidSeedInput);
        seedField.setResponder(this::onSeedChanged);
        seedField.setValue(Objects.requireNonNull(
            config.seed() == 0L ? "" : Long.toString(config.seed()), "seedValue"));
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(
            net.minecraft.client.Minecraft.getInstance().font, "font");
        updateLayout(UIScaleManager.getScaledLineHeight(font, 10));
        int buttonHeight = s(BUTTON_HEIGHT);
        int buttonWidth = s(BUTTON_WIDTH);

        // Enable toggle
        renderToggle(graphics, "area.biome.enable", biomeEnabled,
            getX(), enableToggleY, mouseX, mouseY, true);

        // Tooltip for enable toggle
        int toggleLabelWidth = font.width(
            Objects.requireNonNull(Component.translatable("area.biome.enable"), "enableLabel")) + s(TOGGLE_WIDTH) + s(8);
        if (mouseX >= getX() && mouseX < getX() + toggleLabelWidth &&
            mouseY >= enableToggleY && mouseY < enableToggleY + buttonHeight) {
            graphics.renderTooltip(font,
                Objects.requireNonNull(Component.translatable("area.biome.enable.tooltip"), "enableTooltip"),
                mouseX, mouseY);
        }

        int primaryColor = biomeEnabled
            ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
            : AreaBuilderGuiConstants.COLOR_TEXT_DISABLED;
        int secondaryColor = biomeEnabled
            ? AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
            : AreaBuilderGuiConstants.COLOR_TEXT_DISABLED;

        // Seed section
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.biome.seed"), "seedLabel"),
            getX(), seedLabelY,
            primaryColor
        );

        AxiomRenderer.drawInputBackground(graphics, seedField.getX(), seedField.getY(), seedField.getWidth(),
            seedField.getHeight(), seedField.isFocused());
        seedField.setTextColor(primaryColor);
        seedField.setTextColorUneditable(AreaBuilderGuiConstants.COLOR_TEXT_DISABLED);
        seedField.render(graphics, mouseX, mouseY, partialTick);

        // Random seed button
        boolean randomHovered = biomeEnabled &&
            mouseX >= randomButtonX && mouseX < randomButtonX + buttonWidth &&
            mouseY >= seedFieldY && mouseY < seedFieldY + buttonHeight;
        int randomBg = biomeEnabled
            ? (randomHovered ? AreaBuilderGuiConstants.COLOR_HOVER : AreaBuilderGuiConstants.COLOR_PANEL)
            : AreaBuilderGuiConstants.COLOR_PANEL;
        graphics.fill(randomButtonX, seedFieldY, randomButtonX + buttonWidth, seedFieldY + buttonHeight, randomBg);
        graphics.renderOutline(randomButtonX, seedFieldY, buttonWidth, buttonHeight,
            AreaBuilderGuiConstants.COLOR_BORDER);
        UIScaleManager.drawScaledCenteredString(graphics, font,
            Component.translatable("area.biome.random_seed").getString(),
            randomButtonX + buttonWidth / 2, seedFieldY + s(5), primaryColor);

        // Terrain style section
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.biome.terrain_style"), "terrainStyle"),
            getX(), terrainLabelY,
            primaryColor
        );

        // Terrain style buttons
        int btnX = getX();
        for (TerrainStyle style : TERRAIN_STYLES) {
            boolean isSelected = config.terrainStyle() == style;
            boolean isHovered = biomeEnabled &&
                mouseX >= btnX && mouseX < btnX + buttonWidth &&
                mouseY >= terrainButtonsY && mouseY < terrainButtonsY + buttonHeight;

            int bgColor;
            if (!biomeEnabled) {
                bgColor = AreaBuilderGuiConstants.COLOR_PANEL;
            } else if (isSelected) {
                bgColor = AreaBuilderGuiConstants.COLOR_TAB_ACTIVE;
            } else if (isHovered) {
                bgColor = AreaBuilderGuiConstants.COLOR_HOVER;
            } else {
                bgColor = AreaBuilderGuiConstants.COLOR_PANEL;
            }

            graphics.fill(btnX, terrainButtonsY, btnX + buttonWidth, terrainButtonsY + buttonHeight, bgColor);
            graphics.renderOutline(btnX, terrainButtonsY, buttonWidth, buttonHeight,
                isSelected ? AreaBuilderGuiConstants.COLOR_SELECTED_BORDER : AreaBuilderGuiConstants.COLOR_BORDER);

            String styleName = Component.translatable("area.biome.terrain." + style.getSerializedName()).getString();
            int maxTextWidth = buttonWidth - s(8);
            if (UIScaleManager.getScaledStringWidth(font, styleName) > maxTextWidth) {
                while (styleName.length() > 2 && UIScaleManager.getScaledStringWidth(font, styleName + "..") > maxTextWidth) {
                    styleName = styleName.substring(0, styleName.length() - 1);
                }
                styleName = styleName + "..";
            }
            UIScaleManager.drawScaledCenteredString(graphics, font, styleName,
                btnX + buttonWidth / 2, terrainButtonsY + s(5),
                isSelected ? primaryColor : secondaryColor);

            btnX += buttonWidth + s(4);
        }

        // Feature toggles
        renderToggle(graphics, "area.biome.generate_features", config.generateFeatures(),
            getX(), featureStartY, mouseX, mouseY, biomeEnabled);

        renderToggle(graphics, "area.biome.generate_structures", config.generateStructures(),
            getX(), featureStartY + ROW_HEIGHT, mouseX, mouseY, biomeEnabled);

        renderToggle(graphics, "area.biome.generate_ores", config.generateOres(),
            getX(), featureStartY + ROW_HEIGHT * 2, mouseX, mouseY, biomeEnabled);
    }

    private void renderToggle(@Nonnull GuiGraphics graphics, @Nonnull String translationKey, boolean value,
                             int x, int y, int mouseX, int mouseY, boolean enabled) {
        var font = Objects.requireNonNull(
            net.minecraft.client.Minecraft.getInstance().font, "font");
        int toggleWidth = s(TOGGLE_WIDTH);
        int buttonHeight = s(BUTTON_HEIGHT);

        // Toggle button
        int toggleX = x;
        boolean isHovered = enabled &&
            mouseX >= toggleX && mouseX < toggleX + toggleWidth &&
            mouseY >= y && mouseY < y + buttonHeight;

        int bgColor;
        if (!enabled) {
            bgColor = AreaBuilderGuiConstants.COLOR_PANEL;
        } else {
            bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF;
            if (isHovered) {
                bgColor = value ? AreaBuilderGuiConstants.COLOR_TOGGLE_ON_HOVER : AreaBuilderGuiConstants.COLOR_TOGGLE_OFF_HOVER;
            }
        }

        graphics.fill(toggleX, y, toggleX + toggleWidth, y + buttonHeight, bgColor);
        graphics.renderOutline(toggleX, y, toggleWidth, buttonHeight,
            AreaBuilderGuiConstants.COLOR_BORDER);

        String toggleText = Component.translatable(value ? "area.toggle.on" : "area.toggle.off").getString();
        int toggleColor = enabled ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_DISABLED;
        UIScaleManager.drawScaledCenteredString(graphics, font, toggleText, toggleX + toggleWidth / 2, y + s(5), toggleColor);

        // Label
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable(translationKey), "toggleLabel"),
            toggleX + toggleWidth + s(8), y + s(5),
            enabled ? AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY : AreaBuilderGuiConstants.COLOR_TEXT_DISABLED
        );
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        updateLayout(UIScaleManager.getScaledLineHeight(
            net.minecraft.client.Minecraft.getInstance().font, 10));
        int toggleWidth = s(TOGGLE_WIDTH);
        int buttonWidth = s(BUTTON_WIDTH);
        int buttonHeight = s(BUTTON_HEIGHT);

        // Enable toggle
        if (mouseX >= getX() && mouseX < getX() + toggleWidth &&
            mouseY >= enableToggleY && mouseY < enableToggleY + buttonHeight) {
            biomeEnabled = !biomeEnabled;
            if (onBiomeEnabledChanged != null) {
                onBiomeEnabledChanged.accept(biomeEnabled);
            }
            if (!biomeEnabled) {
                seedField.setFocused(false);
            }
            return;
        }

        if (!biomeEnabled) {
            return;
        }

        // Random seed button
        if (mouseX >= randomButtonX && mouseX < randomButtonX + buttonWidth &&
            mouseY >= seedFieldY && mouseY < seedFieldY + buttonHeight) {
            config = config.withRandomSeed();
            seedField.setValue(Objects.requireNonNull(Long.toString(config.seed()), "seedString"));
            notifyChange();
            return;
        }

        // Terrain style buttons
        int btnX = getX();
        for (TerrainStyle style : TERRAIN_STYLES) {
            if (mouseX >= btnX && mouseX < btnX + buttonWidth &&
                mouseY >= terrainButtonsY && mouseY < terrainButtonsY + buttonHeight) {
                TerrainStyle currentStyle = Objects.requireNonNull(config.terrainStyle(), "terrainStyle");
                if (currentStyle != style) {
                    config = config.withTerrainStyle(Objects.requireNonNull(style, "style"));
                    notifyChange();
                }
                return;
            }
            btnX += buttonWidth + s(4);
        }

        // Feature toggles
        if (mouseX >= getX() && mouseX < getX() + toggleWidth) {
            if (mouseY >= featureStartY && mouseY < featureStartY + buttonHeight) {
                config = config.withFeatures(!config.generateFeatures());
                notifyChange();
                return;
            }

            if (mouseY >= featureStartY + s(ROW_HEIGHT) && mouseY < featureStartY + s(ROW_HEIGHT) + buttonHeight) {
                config = new BiomeGenerationConfig(
                    config.biomeId(), config.seed(), !config.generateStructures(),
                    config.generateFeatures(), config.generateOres(),
                    config.seaLevel(), config.terrainStyle()
                );
                notifyChange();
                return;
            }

            if (mouseY >= featureStartY + s(ROW_HEIGHT) * 2 && mouseY < featureStartY + s(ROW_HEIGHT) * 2 + buttonHeight) {
                config = new BiomeGenerationConfig(
                    config.biomeId(), config.seed(), config.generateStructures(),
                    config.generateFeatures(), !config.generateOres(),
                    config.seaLevel(), config.terrainStyle()
                );
                notifyChange();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateLayout(UIScaleManager.getScaledLineHeight(
            net.minecraft.client.Minecraft.getInstance().font, 10));
        if (biomeEnabled && seedField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!isMouseOver(mouseX, mouseY)) {
            seedField.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (biomeEnabled && seedField.isFocused() && seedField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (biomeEnabled && seedField.isFocused() && seedField.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void updateLayout(int fontHeight) {
        int currentY = getY();
        enableToggleY = currentY;
        currentY += s(ROW_HEIGHT) + s(SECTION_GAP);

        seedLabelY = currentY;
        currentY += fontHeight + s(3);

        seedFieldY = currentY;
        int buttonWidth = s(BUTTON_WIDTH);
        seedFieldWidth = Math.max(s(60), Math.min(s(SEED_FIELD_WIDTH), getWidth() - buttonWidth - s(10)));
        randomButtonX = getX() + seedFieldWidth + s(10);
        seedField.setX(getX());
        seedField.setY(seedFieldY);
        seedField.setWidth(seedFieldWidth);
        seedField.setHeight(AreaBuilderGuiConstants.scaledFieldHeight());
        currentY += s(ROW_HEIGHT) + s(SECTION_GAP);

        terrainLabelY = currentY;
        currentY += fontHeight + s(5);
        terrainButtonsY = currentY;
        currentY += s(ROW_HEIGHT) + s(SECTION_GAP);

        featureStartY = currentY;
    }

    private boolean isValidSeedInput(String text) {
        if (text == null || text.isEmpty() || "-".equals(text)) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '-' && i == 0) {
                continue;
            }
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private void onSeedChanged(String text) {
        if (text == null || text.isEmpty() || "-".equals(text)) {
            if (config.seed() != 0L) {
                config = config.withSeed(0L);
                notifyChange();
            }
            return;
        }
        Long seed = Longs.tryParse(text);
        if (seed == null) {
            return;
        }
        if (config.seed() != seed) {
            config = config.withSeed(seed);
            notifyChange();
        }
    }

    private void notifyChange() {
        if (onConfigChanged != null) {
            onConfigChanged.accept(config);
        }
    }

    public BiomeGenerationConfig getConfig() {
        return config;
    }

    public void setConfig(@Nonnull BiomeGenerationConfig newConfig) {
        this.config = Objects.requireNonNull(newConfig, "newConfig");
        seedField.setValue(Objects.requireNonNull(
            newConfig.seed() == 0L ? "" : Long.toString(newConfig.seed()), "seedValue"));
    }

    public void setBiome(@Nonnull ResourceLocation biomeId) {
        config = config.withBiome(Objects.requireNonNull(biomeId, "biomeId"));
        seedField.setValue(Objects.requireNonNull(
            config.seed() == 0L ? "" : Long.toString(config.seed()), "seedValue"));
        notifyChange(); // Sync changes back to screen
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Objects.requireNonNull(Component.translatable("area.biome.config.title"), "narrationTitle"));
    }

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }
}
