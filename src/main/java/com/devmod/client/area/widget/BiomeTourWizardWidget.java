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

import com.devmod.area.aesthetic.AreaBuilderGuiConstants;
import com.devmod.area.data.BiomeTourConfig;
import com.devmod.area.data.BiomeTourSection;
import com.devmod.area.data.BiomeTourSection.TransitionStyle;

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
        this.maxVisibleSections = (height - HEADER_HEIGHT - 60) / (ROW_HEIGHT + SECTION_SPACING);
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        int currentY = getY();

        // Title
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.biome_tour.title")),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );
        currentY += 12;

        // Intro text
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.biome_tour.intro")),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_MUTED
        );
        currentY += HEADER_HEIGHT - 12;

        // Section list background
        int listHeight = maxVisibleSections * (ROW_HEIGHT + SECTION_SPACING);
        graphics.fill(getX(), currentY, getX() + getWidth(), currentY + listHeight, AreaBuilderGuiConstants.COLOR_PANEL);
        graphics.renderOutline(getX(), currentY, getWidth(), listHeight, AreaBuilderGuiConstants.COLOR_BORDER);

        // Render sections
        int sectionY = currentY + 4;
        for (int i = scrollOffset; i < Math.min(config.getSectionCount(), scrollOffset + maxVisibleSections); i++) {
            BiomeTourSection section = config.getSection(i);
            boolean isSelected = i == selectedSection;
            boolean isHovered = mouseY >= sectionY && mouseY < sectionY + ROW_HEIGHT &&
                               mouseX >= getX() && mouseX < getX() + getWidth();

            renderSection(graphics, section, i, getX() + 4, sectionY, getWidth() - 8,
                         isSelected, isHovered, mouseX, mouseY);
            sectionY += ROW_HEIGHT + SECTION_SPACING;
        }

        currentY += listHeight + 8;

        // Add section button
        boolean addHovered = mouseX >= getX() && mouseX < getX() + 120 &&
                            mouseY >= currentY && mouseY < currentY + BUTTON_SIZE;
        int addBgColor = addHovered ? AreaBuilderGuiConstants.COLOR_HOVER : AreaBuilderGuiConstants.COLOR_PANEL;
        graphics.fill(getX(), currentY, getX() + 120, currentY + BUTTON_SIZE, addBgColor);
        graphics.renderOutline(getX(), currentY, 120, BUTTON_SIZE, AreaBuilderGuiConstants.COLOR_BORDER);
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.biome_tour.add_section")),
            getX() + 6, currentY + 5,
            config.getSectionCount() < BiomeTourConfig.MAX_SECTIONS ?
                AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_MUTED
        );

        currentY += BUTTON_SIZE + 12;

        // Summary
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.biome_tour.total_length", config.getTotalLength())),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
        currentY += 12;

        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.biome_tour.sections_count", config.getSectionCount())),
            getX(), currentY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
    }

    private void renderSection(GuiGraphics graphics, BiomeTourSection section, int index,
                              int x, int y, int width, boolean isSelected, boolean isHovered,
                              int mouseX, int mouseY) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);

        // Background
        int bgColor = isSelected ? AreaBuilderGuiConstants.COLOR_TAB_ACTIVE :
                     (isHovered ? AreaBuilderGuiConstants.COLOR_HOVER : 0x00000000);
        if (bgColor != 0) {
            graphics.fill(x, y, x + width, y + ROW_HEIGHT, bgColor);
        }

        // Section number
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.biome_tour.section", index + 1)),
            x + 4, y + 4,
            AreaBuilderGuiConstants.COLOR_TEXT_MUTED
        );

        // Biome name
        graphics.drawString(font,
            section.getBiomeDisplayName(),
            x + 4, y + 14,
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Length
        String lengthText = section.length() + " blocks";
        graphics.drawString(font, lengthText, x + 100, y + 14, AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

        // Transition style
        String transitionKey = "area.biome_tour.transition." + section.transition().getId();
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable(transitionKey)),
            x + 180, y + 14,
            AreaBuilderGuiConstants.COLOR_TEXT_MUTED
        );

        // Remove button (X)
        int removeX = x + width - BUTTON_SIZE - 4;
        boolean removeHovered = mouseX >= removeX && mouseX < removeX + BUTTON_SIZE &&
                               mouseY >= y + 4 && mouseY < y + 4 + BUTTON_SIZE;
        int removeBgColor = removeHovered ? 0xFFCC4444 : AreaBuilderGuiConstants.COLOR_PANEL;
        graphics.fill(removeX, y + 4, removeX + BUTTON_SIZE, y + 4 + BUTTON_SIZE, removeBgColor);
        graphics.renderOutline(removeX, y + 4, BUTTON_SIZE, BUTTON_SIZE, AreaBuilderGuiConstants.COLOR_BORDER);
        graphics.drawCenteredString(font, "X", removeX + BUTTON_SIZE / 2, y + 8,
            removeHovered ? 0xFFFFFFFF : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

        // Transition cycle button
        int transX = x + width - BUTTON_SIZE * 2 - 8;
        boolean transHovered = mouseX >= transX && mouseX < transX + BUTTON_SIZE &&
                              mouseY >= y + 4 && mouseY < y + 4 + BUTTON_SIZE;
        int transBgColor = transHovered ? AreaBuilderGuiConstants.COLOR_HOVER : AreaBuilderGuiConstants.COLOR_PANEL;
        graphics.fill(transX, y + 4, transX + BUTTON_SIZE, y + 4 + BUTTON_SIZE, transBgColor);
        graphics.renderOutline(transX, y + 4, BUTTON_SIZE, BUTTON_SIZE, AreaBuilderGuiConstants.COLOR_BORDER);
        graphics.drawCenteredString(font, "T", transX + BUTTON_SIZE / 2, y + 8,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        int listY = getY() + HEADER_HEIGHT;
        int listHeight = maxVisibleSections * (ROW_HEIGHT + SECTION_SPACING);

        // Check add section button
        int addY = listY + listHeight + 8;
        if (mouseX >= getX() && mouseX < getX() + 120 &&
            mouseY >= addY && mouseY < addY + BUTTON_SIZE) {
            if (config.getSectionCount() < BiomeTourConfig.MAX_SECTIONS) {
                addNewSection();
            }
            return;
        }

        // Check section clicks
        int sectionY = listY + 4;
        for (int i = scrollOffset; i < Math.min(config.getSectionCount(), scrollOffset + maxVisibleSections); i++) {
            if (mouseY >= sectionY && mouseY < sectionY + ROW_HEIGHT) {
                int sectionWidth = getWidth() - 8;
                int sectionX = getX() + 4;

                // Remove button
                int removeX = sectionX + sectionWidth - BUTTON_SIZE - 4;
                if (mouseX >= removeX && mouseX < removeX + BUTTON_SIZE &&
                    mouseY >= sectionY + 4 && mouseY < sectionY + 4 + BUTTON_SIZE) {
                    removeSection(i);
                    return;
                }

                // Transition cycle button
                int transX = sectionX + sectionWidth - BUTTON_SIZE * 2 - 8;
                if (mouseX >= transX && mouseX < transX + BUTTON_SIZE &&
                    mouseY >= sectionY + 4 && mouseY < sectionY + 4 + BUTTON_SIZE) {
                    cycleTransition(i);
                    return;
                }

                // Select section for editing
                selectedSection = (selectedSection == i) ? -1 : i;
                return;
            }
            sectionY += ROW_HEIGHT + SECTION_SPACING;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
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
        int nextIndex = (section.transition().ordinal() + 1) % styles.length;
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
}
