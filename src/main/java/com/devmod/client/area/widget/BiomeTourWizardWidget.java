package com.devmod.client.area.widget;

import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.area.AreaBuilderGuiConstants;
import com.devmod.area.data.BiomeTourConfig;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.area.data.BiomeTourSection;
import com.devmod.area.data.BiomeTourSection.TransitionStyle;
import com.devmod.client.ui.editor.core.DesignTokens;
/**
 * Widget for creating and editing Biome Tours - multi-biome areas with transitions.
 * Displays a list of biome sections with controls to add, remove, and configure each.
 */
@OnlyIn(Dist.CLIENT)
public class BiomeTourWizardWidget extends AbstractWidget {

    private static final int ROW_HEIGHT = 28;
    private static final int BUTTON_SIZE = 18;
    private static final int SECTION_SPACING = 4;
    private static final int HEADER_HEIGHT = 30;

    private BiomeTourConfig config;
    private final Consumer<BiomeTourConfig> onConfigChanged;

    // Scrolling
    private int scrollOffset = 0;
    private int maxVisibleSections;

    // Selection for editing
    private int selectedSection = -1;

    public BiomeTourWizardWidget(int x, int y, int width, int height,
                                 BiomeTourConfig initialConfig,
                                 Consumer<BiomeTourConfig> onConfigChanged) {
        super(x, y, width, height, Component.empty());
        this.config = initialConfig != null ? initialConfig : BiomeTourConfig.defaultTour();
        this.onConfigChanged = onConfigChanged;
        this.maxVisibleSections = Math.max(1, (height - s(HEADER_HEIGHT) - s(60)) / (s(ROW_HEIGHT) + s(SECTION_SPACING)));
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        int currentY = getY();
        int rowHeight = s(ROW_HEIGHT);
        int buttonSize = s(BUTTON_SIZE);
        int sectionSpacing = s(SECTION_SPACING);
        int headerHeight = s(HEADER_HEIGHT);
        int titleGap = s(12);
        int sectionInset = s(4);
        int sectionWidthInset = s(8);
        int addButtonWidth = s(120);
        updateMaxVisibleSections();

        // Title
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.biome_tour.title")),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );
        currentY += titleGap;

        // Intro text
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.biome_tour.intro")),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_MUTED
        );
        currentY += headerHeight - titleGap;

        // Section list background
        int listHeight = maxVisibleSections * (rowHeight + sectionSpacing);
        graphics.fill(getX(), currentY, getX() + getWidth(), currentY + listHeight, AreaBuilderGuiConstants.COLOR_PANEL);
        graphics.renderOutline(getX(), currentY, getWidth(), listHeight, AreaBuilderGuiConstants.COLOR_BORDER);

        // Render sections
        int sectionY = currentY + sectionInset;
        for (int i = scrollOffset; i < Math.min(config.getSectionCount(), scrollOffset + maxVisibleSections); i++) {
            BiomeTourSection section = config.getSection(i);
            boolean isSelected = i == selectedSection;
            boolean isHovered = mouseY >= sectionY && mouseY < sectionY + rowHeight &&
                               mouseX >= getX() && mouseX < getX() + getWidth();

            renderSection(graphics, section, i, getX() + sectionInset, sectionY, getWidth() - sectionWidthInset,
                         isSelected, isHovered, mouseX, mouseY);
            sectionY += rowHeight + sectionSpacing;
        }

        currentY += listHeight + s(8);

        // Add section button
        boolean addHovered = mouseX >= getX() && mouseX < getX() + addButtonWidth &&
                            mouseY >= currentY && mouseY < currentY + buttonSize;
        int addBgColor = addHovered ? AreaBuilderGuiConstants.COLOR_HOVER : AreaBuilderGuiConstants.COLOR_PANEL;
        graphics.fill(getX(), currentY, getX() + addButtonWidth, currentY + buttonSize, addBgColor);
        graphics.renderOutline(getX(), currentY, addButtonWidth, buttonSize, AreaBuilderGuiConstants.COLOR_BORDER);
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.biome_tour.add_section")),
            getX() + s(6), currentY + s(5),
            config.getSectionCount() < BiomeTourConfig.MAX_SECTIONS ?
                AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_MUTED
        );

        currentY += buttonSize + s(12);

        // Summary
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.biome_tour.total_length", config.getTotalLength())),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
        currentY += s(12);

        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.biome_tour.sections_count", config.getSectionCount())),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
    }

    private void renderSection(GuiGraphics graphics, BiomeTourSection section, int index,
                              int x, int y, int width, boolean isSelected, boolean isHovered,
                              int mouseX, int mouseY) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        int rowHeight = s(ROW_HEIGHT);
        int buttonSize = s(BUTTON_SIZE);
        int padding = s(4);
        int textOffset = s(14);

        // Background
        int bgColor = isSelected ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE :
                     (isHovered ? AreaBuilderGuiConstants.COLOR_HOVER : DesignTokens.AreaBuilder.TRANSPARENT);
        if (bgColor != 0) {
            graphics.fill(x, y, x + width, y + rowHeight, bgColor);
        }

        // Section number
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.biome_tour.section", index + 1)),
            x + padding, y + padding,
            AreaBuilderGuiConstants.COLOR_TEXT_MUTED
        );

        // Biome name
        UIScaleManager.drawScaledString(graphics, font,
            section.getBiomeDisplayName(),
            x + padding, y + textOffset,
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Length
        String lengthText = section.length() + " blocks";
        UIScaleManager.drawScaledString(graphics, font, lengthText, x + s(100), y + textOffset, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

        // Transition style
        String transitionKey = "area.biome_tour.transition." + section.transition().getId();
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable(transitionKey)),
            x + s(180), y + textOffset,
            AreaBuilderGuiConstants.COLOR_TEXT_MUTED
        );

        // Remove button (X)
        int removeX = x + width - buttonSize - padding;
        boolean removeHovered = mouseX >= removeX && mouseX < removeX + buttonSize &&
                               mouseY >= y + padding && mouseY < y + padding + buttonSize;
        int removeBgColor = removeHovered ? DesignTokens.AreaBuilder.DANGER_BG_HOVER : AreaBuilderGuiConstants.COLOR_PANEL;
        graphics.fill(removeX, y + padding, removeX + buttonSize, y + padding + buttonSize, removeBgColor);
        graphics.renderOutline(removeX, y + padding, buttonSize, buttonSize, AreaBuilderGuiConstants.COLOR_BORDER);
        UIScaleManager.drawScaledCenteredString(graphics, font, "X", removeX + buttonSize / 2, y + s(8),
            removeHovered ? DesignTokens.AreaBuilder.TEXT_WHITE : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

        // Transition cycle button
        int transX = x + width - buttonSize * 2 - s(8);
        boolean transHovered = mouseX >= transX && mouseX < transX + buttonSize &&
                              mouseY >= y + padding && mouseY < y + padding + buttonSize;
        int transBgColor = transHovered ? AreaBuilderGuiConstants.COLOR_HOVER : AreaBuilderGuiConstants.COLOR_PANEL;
        graphics.fill(transX, y + padding, transX + buttonSize, y + padding + buttonSize, transBgColor);
        graphics.renderOutline(transX, y + padding, buttonSize, buttonSize, AreaBuilderGuiConstants.COLOR_BORDER);
        UIScaleManager.drawScaledCenteredString(graphics, font, "T", transX + buttonSize / 2, y + s(8),
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        int rowHeight = s(ROW_HEIGHT);
        int buttonSize = s(BUTTON_SIZE);
        int sectionSpacing = s(SECTION_SPACING);
        int listY = getY() + s(HEADER_HEIGHT);
        updateMaxVisibleSections();
        int listHeight = maxVisibleSections * (rowHeight + sectionSpacing);

        // Check add section button
        int addY = listY + listHeight + s(8);
        if (mouseX >= getX() && mouseX < getX() + s(120) &&
            mouseY >= addY && mouseY < addY + buttonSize) {
            if (config.getSectionCount() < BiomeTourConfig.MAX_SECTIONS) {
                addNewSection();
            }
            return;
        }

        // Check section clicks
        int sectionY = listY + s(4);
        for (int i = scrollOffset; i < Math.min(config.getSectionCount(), scrollOffset + maxVisibleSections); i++) {
            if (mouseY >= sectionY && mouseY < sectionY + rowHeight) {
                int sectionWidth = getWidth() - s(8);
                int sectionX = getX() + s(4);

                // Remove button
                int removeX = sectionX + sectionWidth - buttonSize - s(4);
                if (mouseX >= removeX && mouseX < removeX + buttonSize &&
                    mouseY >= sectionY + s(4) && mouseY < sectionY + s(4) + buttonSize) {
                    removeSection(i);
                    return;
                }

                // Transition cycle button
                int transX = sectionX + sectionWidth - buttonSize * 2 - s(8);
                if (mouseX >= transX && mouseX < transX + buttonSize &&
                    mouseY >= sectionY + s(4) && mouseY < sectionY + s(4) + buttonSize) {
                    cycleTransition(i);
                    return;
                }

                // Select section for editing
                selectedSection = (selectedSection == i) ? -1 : i;
                return;
            }
            sectionY += rowHeight + sectionSpacing;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        updateMaxVisibleSections();
        int maxScroll = Math.max(0, config.getSectionCount() - maxVisibleSections);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
        return true;
    }

    private void addNewSection() {
        // Add a new plains section by default
        BiomeTourSection newSection = new BiomeTourSection(
            ResourceLocation.withDefaultNamespace("plains"),
            BiomeTourSection.DEFAULT_LENGTH,
            TransitionStyle.BLEND
        );
        config = config.withSectionAdded(newSection);
        notifyChange();
    }

    private void removeSection(int index) {
        if (config.getSectionCount() > 0) {
            config = config.withSectionRemoved(index);
            if (selectedSection >= config.getSectionCount()) {
                selectedSection = config.getSectionCount() - 1;
            }
            notifyChange();
        }
    }

    private void cycleTransition(int index) {
        BiomeTourSection section = config.getSection(index);
        TransitionStyle[] styles = TransitionStyle.values();
        int currentIndex = 0;
        for (int i = 0; i < styles.length; i++) {
            if (styles[i] == section.transition()) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % styles.length;
        TransitionStyle nextStyle = Objects.requireNonNull(styles[nextIndex]);
        BiomeTourSection updated = Objects.requireNonNull(section.withTransition(nextStyle));
        config = config.withSectionUpdated(index, updated);
        notifyChange();
    }

    private void notifyChange() {
        if (onConfigChanged != null) {
            onConfigChanged.accept(config);
        }
    }

    public BiomeTourConfig getConfig() {
        return config;
    }

    public void setConfig(BiomeTourConfig newConfig) {
        if (newConfig != null) {
            this.config = newConfig;
        }
    }

    /**
     * Updates the biome for the selected section.
     */
    public void setSelectedBiome(ResourceLocation biome) {
        if (selectedSection >= 0 && selectedSection < config.getSectionCount()) {
            BiomeTourSection section = config.getSection(selectedSection);
            BiomeTourSection updated = Objects.requireNonNull(section.withBiome(Objects.requireNonNull(biome)));
            config = config.withSectionUpdated(selectedSection, updated);
            notifyChange();
        }
    }

    /**
     * Updates the length for the selected section.
     */
    public void setSelectedLength(int length) {
        if (selectedSection >= 0 && selectedSection < config.getSectionCount()) {
            BiomeTourSection section = config.getSection(selectedSection);
            BiomeTourSection updated = Objects.requireNonNull(section.withLength(length));
            config = config.withSectionUpdated(selectedSection, updated);
            notifyChange();
        }
    }

    /**
     * Gets the currently selected section index, or -1 if none.
     */
    public int getSelectedSection() {
        return selectedSection;
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Objects.requireNonNull(Component.translatable("area.biome_tour.title")));
    }

    private void updateMaxVisibleSections() {
        int rowHeight = s(ROW_HEIGHT);
        int sectionSpacing = s(SECTION_SPACING);
        int headerHeight = s(HEADER_HEIGHT);
        int available = Math.max(0, getHeight() - headerHeight - s(60));
        maxVisibleSections = Math.max(1, available / Math.max(1, rowHeight + sectionSpacing));
    }

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }
}
