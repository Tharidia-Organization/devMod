package com.devmod.client.area.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.area.aesthetic.AreaBuilderGuiConstants;
import com.devmod.area.network.TemplateListPayload.TemplateSummary;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Widget for selecting templates from a searchable list.
 * Used for loading template configurations into AreaBuilderScreen.
 */
@OnlyIn(Dist.CLIENT)
public class TemplateSelectorWidget extends AbstractWidget {

    private static final int ITEM_HEIGHT = 24;
    private static final int MAX_VISIBLE_ITEMS = 5;
    private static final int HEADER_GAP = 6;
    private static final int DELETE_BTN_SIZE = 16;

    @Nullable
    private UUID selectedTemplateId;
    private final Consumer<UUID> onTemplateSelected;
    @Nullable
    private final Consumer<UUID> onTemplateDeleted;

    private String searchQuery = "";
    private List<TemplateSummary> allTemplates = new ArrayList<>();
    private List<TemplateSummary> filteredTemplates = new ArrayList<>();
    private int scrollOffset = 0;
    private final EditBox searchBox;
    private int listY;
    private int listHeight;
    private int visibleItems = MAX_VISIBLE_ITEMS;

    /**
     * Creates a new template selector widget.
     *
     * @param x                  X position
     * @param y                  Y position
     * @param width              Widget width
     * @param height             Widget height
     * @param onTemplateSelected Callback when a template is selected
     */
    public TemplateSelectorWidget(int x, int y, int width, int height,
                                  @Nonnull Consumer<UUID> onTemplateSelected) {
        this(x, y, width, height, onTemplateSelected, null);
    }

    /**
     * Creates a new template selector widget with delete support.
     *
     * @param x                  X position
     * @param y                  Y position
     * @param width              Widget width
     * @param height             Widget height
     * @param onTemplateSelected Callback when a template is selected
     * @param onTemplateDeleted  Callback when a template delete is requested (nullable)
     */
    @SuppressWarnings("this-escape")
    public TemplateSelectorWidget(int x, int y, int width, int height,
                                  @Nonnull Consumer<UUID> onTemplateSelected,
                                  @Nullable Consumer<UUID> onTemplateDeleted) {
        super(x, y, width, height, Component.empty());
        this.onTemplateSelected = Objects.requireNonNull(onTemplateSelected);
        this.onTemplateDeleted = onTemplateDeleted;
        this.searchBox = new EditBox(Objects.requireNonNull(Minecraft.getInstance().font),
            x + 2, y + 15 + 2, width - 4, AreaBuilderGuiConstants.FIELD_HEIGHT,
            Objects.requireNonNull(Component.translatable("area.template.search")));
        this.searchBox.setHint(Objects.requireNonNull(Component.translatable("area.template.search")));
        this.searchBox.setMaxLength(64);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);
        this.searchBox.setTextColorUneditable(AreaBuilderGuiConstants.COLOR_TEXT_DISABLED);
        this.searchBox.setResponder(this::onSearchChanged);
        updateLayout(Minecraft.getInstance().font.lineHeight);
    }

    /**
     * Sets the list of available templates.
     * Called when TemplateListPayload is received from server.
     *
     * @param templates The templates to display
     */
    public void setTemplates(@Nonnull List<TemplateSummary> templates) {
        this.allTemplates = new ArrayList<>(Objects.requireNonNull(templates));
        refreshFilteredList();
    }

    /**
     * Gets the currently selected template ID.
     *
     * @return The selected template ID, or null if none
     */
    @Nullable
    public UUID getSelectedTemplateId() {
        return selectedTemplateId;
    }

    /**
     * Clears the selection.
     */
    public void clearSelection() {
        this.selectedTemplateId = null;
    }

    private void refreshFilteredList() {
        if (searchQuery.isEmpty()) {
            filteredTemplates = new ArrayList<>(allTemplates);
        } else {
            String query = searchQuery.toLowerCase(Locale.ROOT);
            filteredTemplates = allTemplates.stream()
                .filter(t -> t.name().toLowerCase(Locale.ROOT).contains(query) ||
                             t.description().toLowerCase(Locale.ROOT).contains(query) ||
                             t.author().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        }

        // Reset scroll if out of bounds
        int maxScroll = Math.max(0, filteredTemplates.size() - Math.max(1, visibleItems));
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    private void onSearchChanged(String query) {
        searchQuery = query != null ? query : "";
        refreshFilteredList();
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font);
        updateLayout(font.lineHeight);

        // Title
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.template.select")),
            getX(), getY(),
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Search box area
        AxiomRenderer.drawInputBackground(graphics, searchBox.getX(), searchBox.getY(),
            searchBox.getWidth(), searchBox.getHeight(), searchBox.isFocused());
        searchBox.render(graphics, mouseX, mouseY, partialTick);

        // List background
        graphics.fill(getX(), listY, getX() + getWidth(), listY + listHeight,
            AreaBuilderGuiConstants.COLOR_PANEL);
        graphics.renderOutline(getX(), listY, getWidth(), listHeight,
            AreaBuilderGuiConstants.COLOR_BORDER);

        if (filteredTemplates.isEmpty()) {
            String msgKey = allTemplates.isEmpty() ? "area.template.loading" : "area.template.empty";
            graphics.drawCenteredString(font,
                Objects.requireNonNull(Component.translatable(msgKey)),
                getX() + getWidth() / 2, listY + listHeight / 2 - 4,
                AreaBuilderGuiConstants.COLOR_TEXT_MUTED);
            return;
        }

        // Draw visible templates
        for (int i = 0; i < visibleItems && i + scrollOffset < filteredTemplates.size(); i++) {
            TemplateSummary template = filteredTemplates.get(i + scrollOffset);
            int itemY = listY + i * ITEM_HEIGHT;

            boolean isHovered = mouseX >= getX() && mouseX < getX() + getWidth() &&
                               mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean isSelected = Objects.equals(template.id(), selectedTemplateId);

            // Item background
            if (isSelected) {
                graphics.fill(getX() + 1, itemY, getX() + getWidth() - 1, itemY + ITEM_HEIGHT - 1,
                    AreaBuilderGuiConstants.COLOR_TAB_ACTIVE);
            } else if (isHovered) {
                graphics.fill(getX() + 1, itemY, getX() + getWidth() - 1, itemY + ITEM_HEIGHT - 1,
                    AreaBuilderGuiConstants.COLOR_HOVER);
            }

            // Template name
            graphics.drawString(font, template.name(), getX() + 4, itemY + 4,
                isSelected ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
                          : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

            // Shape + Author info
            String info = template.shape().getSerializedName() + " - " + template.author();
            graphics.drawString(font, info, getX() + 4, itemY + 14,
                AreaBuilderGuiConstants.COLOR_TEXT_MUTED);

            // Delete button [X] on hover (only if delete callback is set)
            if (isHovered && onTemplateDeleted != null) {
                int btnX = getX() + getWidth() - DELETE_BTN_SIZE - 8;
                int btnY = itemY + (ITEM_HEIGHT - DELETE_BTN_SIZE) / 2;
                boolean btnHovered = mouseX >= btnX && mouseX < btnX + DELETE_BTN_SIZE
                                  && mouseY >= btnY && mouseY < btnY + DELETE_BTN_SIZE;

                // Button background
                graphics.fill(btnX, btnY, btnX + DELETE_BTN_SIZE, btnY + DELETE_BTN_SIZE,
                    btnHovered ? DesignTokens.AreaBuilder.DANGER_BG_HOVER : DesignTokens.AreaBuilder.DANGER_BG);

                // X symbol
                int xColor = btnHovered ? DesignTokens.AreaBuilder.DANGER_ICON_HOVER
                    : DesignTokens.AreaBuilder.DANGER_ICON;
                graphics.drawCenteredString(font, "×", btnX + DELETE_BTN_SIZE / 2, btnY + 4, xColor);
            }
        }

        // Scrollbar if needed
        if (filteredTemplates.size() > visibleItems) {
            int scrollbarX = getX() + getWidth() - 6;
            int scrollbarHeight = listHeight;
            int thumbHeight = Math.max(20, scrollbarHeight * visibleItems / filteredTemplates.size());
            int maxScroll = filteredTemplates.size() - visibleItems;
            int thumbY = listY + (scrollbarHeight - thumbHeight) * scrollOffset / maxScroll;

            // Track
            graphics.fill(scrollbarX, listY, scrollbarX + 4, listY + scrollbarHeight,
                AreaBuilderGuiConstants.COLOR_SCROLLBAR_TRACK);
            // Thumb
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight,
                AreaBuilderGuiConstants.COLOR_SCROLLBAR_THUMB);
        }
    }

    private void updateLayout(int fontHeight) {
        int searchY = getY() + fontHeight + HEADER_GAP;
        listY = searchY + AreaBuilderGuiConstants.FIELD_HEIGHT + HEADER_GAP;
        int availableHeight = getHeight() - (listY - getY());
        visibleItems = Math.max(1, Math.min(MAX_VISIBLE_ITEMS, availableHeight / ITEM_HEIGHT));
        listHeight = visibleItems * ITEM_HEIGHT;
        searchBox.setX(getX() + 2);
        searchBox.setY(searchY);
        searchBox.setWidth(Math.max(40, getWidth() - 4));
        int maxScroll = Math.max(0, filteredTemplates.size() - Math.max(1, visibleItems));
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        // Check if clicking in list area
        if (mouseY >= listY && mouseY < listY + listHeight) {
            int clickedIndex = (int) ((mouseY - listY) / ITEM_HEIGHT) + scrollOffset;
            if (clickedIndex >= 0 && clickedIndex < filteredTemplates.size()) {
                TemplateSummary template = filteredTemplates.get(clickedIndex);
                int itemY = listY + (clickedIndex - scrollOffset) * ITEM_HEIGHT;

                // Check if clicking on delete button
                Consumer<UUID> deleteCallback = onTemplateDeleted;
                if (deleteCallback != null) {
                    int btnX = getX() + getWidth() - DELETE_BTN_SIZE - 8;
                    int btnY = itemY + (ITEM_HEIGHT - DELETE_BTN_SIZE) / 2;
                    if (mouseX >= btnX && mouseX < btnX + DELETE_BTN_SIZE
                        && mouseY >= btnY && mouseY < btnY + DELETE_BTN_SIZE) {
                        // Delete button clicked
                        deleteCallback.accept(template.id());
                        return;
                    }
                }

                // Normal selection
                selectedTemplateId = template.id();
                onTemplateSelected.accept(selectedTemplateId);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int fontHeight = Minecraft.getInstance().font.lineHeight;
        updateLayout(fontHeight);
        if (searchBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (mouseX >= getX() && mouseX < getX() + getWidth()
            && mouseY >= listY && mouseY < listY + listHeight) {
            searchBox.setFocused(false);
        }
        if (!isMouseOver(mouseX, mouseY)) {
            searchBox.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int fontHeight = Minecraft.getInstance().font.lineHeight;
        updateLayout(fontHeight);
        if (mouseX >= getX() && mouseX < getX() + getWidth()
            && mouseY >= listY && mouseY < listY + listHeight) {
            int maxScroll = Math.max(0, filteredTemplates.size() - visibleItems);
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchBox.isFocused() && searchBox.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused() && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Objects.requireNonNull(Component.translatable("area.template.select")));
    }
}
