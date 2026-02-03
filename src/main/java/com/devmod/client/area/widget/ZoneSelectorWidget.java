package com.devmod.client.area.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
import com.devmod.area.network.ZoneListPayload.ZoneSummary;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;

/**
 * Widget for selecting zones from a searchable list.
 * Used in AreaBuilderScreen for linking areas to zones.
 */
@OnlyIn(Dist.CLIENT)
public class ZoneSelectorWidget extends AbstractWidget {

    private static final int ITEM_HEIGHT = 20;
    private static final int MAX_VISIBLE_ITEMS = 6;
    private static final int HEADER_GAP = 6;
    private static final int FOOTER_GAP = 6;

    @Nullable
    private String selectedZoneId;
    private final Consumer<String> onZoneSelected;

    private String searchQuery = "";
    private List<ZoneSummary> allZones = new ArrayList<>();
    private List<ZoneSummary> filteredZones = new ArrayList<>();
    private int scrollOffset = 0;
    private final EditBox searchBox;
    private int searchY;
    private int listY;
    private int listHeight;
    private int visibleItems = MAX_VISIBLE_ITEMS;

    /**
     * Creates a new zone selector widget.
     *
     * @param x               X position
     * @param y               Y position
     * @param width           Widget width
     * @param height          Widget height
     * @param initialZoneId   Initially selected zone ID (may be null)
     * @param onZoneSelected  Callback when a zone is selected
     */
    @SuppressWarnings("this-escape")
    public ZoneSelectorWidget(int x, int y, int width, int height,
                              @Nullable String initialZoneId,
                              @Nonnull Consumer<String> onZoneSelected) {
        super(x, y, width, height, Component.empty());
        this.selectedZoneId = initialZoneId;
        this.onZoneSelected = Objects.requireNonNull(onZoneSelected);
        this.searchBox = new EditBox(Objects.requireNonNull(Minecraft.getInstance().font),
            x + s(2), y + s(15) + s(2), width - s(4), AreaBuilderGuiConstants.scaledFieldHeight(),
            Objects.requireNonNull(Component.translatable("area.zone.search")));
        this.searchBox.setHint(Objects.requireNonNull(Component.translatable("area.zone.search")));
        this.searchBox.setMaxLength(64);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);
        this.searchBox.setTextColorUneditable(AreaBuilderGuiConstants.COLOR_TEXT_DISABLED);
        this.searchBox.setResponder(this::onSearchChanged);
        updateLayout(UIScaleManager.getScaledLineHeight(
            Objects.requireNonNull(Minecraft.getInstance().font), 10));
    }

    /**
     * Sets the list of available zones.
     * Called when ZoneListPayload is received from server.
     *
     * @param zones The zones to display
     */
    public void setZones(@Nonnull List<ZoneSummary> zones) {
        this.allZones = new ArrayList<>(Objects.requireNonNull(zones));
        refreshFilteredList();
    }

    /**
     * Gets the currently selected zone ID.
     *
     * @return The selected zone ID, or null if none
     */
    @Nullable
    public String getSelectedZoneId() {
        return selectedZoneId;
    }

    /**
     * Sets the currently selected zone ID.
     *
     * @param zoneId The zone ID to select (may be null to clear)
     */
    public void setSelectedZoneId(@Nullable String zoneId) {
        this.selectedZoneId = zoneId;
    }

    /**
     * Clears the selection.
     */
    public void clearSelection() {
        this.selectedZoneId = null;
        onZoneSelected.accept(null);
    }

    private void refreshFilteredList() {
        if (searchQuery.isEmpty()) {
            filteredZones = new ArrayList<>(allZones);
        } else {
            String query = searchQuery.toLowerCase(Locale.ROOT);
            filteredZones = allZones.stream()
                .filter(z -> z.displayName().toLowerCase(Locale.ROOT).contains(query) ||
                             z.id().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        }

        // Reset scroll if out of bounds
        int maxScroll = Math.max(0, filteredZones.size() - Math.max(1, visibleItems));
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
        updateLayout(UIScaleManager.getScaledLineHeight(font, 10));
        int itemHeight = s(ITEM_HEIGHT);
        int itemInset = s(1);
        int itemTextYOffset = s(6);
        int listPadding = s(4);
        int scrollbarWidth = s(4);
        int scrollbarInset = s(6);
        int scrollbarMinThumb = s(20);

        // Title
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.builder.linked_zone")),
            getX(), getY(),
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Search box area
        AxiomRenderer.drawInputBackground(graphics, searchBox.getX(), searchBox.getY(), searchBox.getWidth(),
            searchBox.getHeight(), searchBox.isFocused());
        searchBox.render(graphics, mouseX, mouseY, partialTick);

        // List background
        graphics.fill(getX(), listY, getX() + getWidth(), listY + listHeight,
            AreaBuilderGuiConstants.COLOR_PANEL);
        graphics.renderOutline(getX(), listY, getWidth(), listHeight,
            AreaBuilderGuiConstants.COLOR_BORDER);

        if (filteredZones.isEmpty()) {
            String msgKey = allZones.isEmpty() ? "area.zone.loading" : "area.zone.empty";
            UIScaleManager.drawScaledCenteredString(graphics, font,
                Component.translatable(msgKey).getString(),
                getX() + getWidth() / 2, listY + listHeight / 2 - s(4),
                AreaBuilderGuiConstants.COLOR_TEXT_MUTED);
        }

        // Draw visible zones
        for (int i = 0; i < visibleItems && i + scrollOffset < filteredZones.size(); i++) {
            ZoneSummary zone = filteredZones.get(i + scrollOffset);
            int itemY = listY + i * itemHeight;

            boolean isHovered = mouseX >= getX() && mouseX < getX() + getWidth() &&
                               mouseY >= itemY && mouseY < itemY + itemHeight;
            boolean isSelected = Objects.equals(zone.id(), selectedZoneId);

            // Item background
            if (isSelected) {
                graphics.fill(getX() + itemInset, itemY, getX() + getWidth() - itemInset, itemY + itemHeight - itemInset,
                    AreaBuilderGuiConstants.COLOR_TAB_ACTIVE);
            } else if (isHovered) {
                graphics.fill(getX() + itemInset, itemY, getX() + getWidth() - itemInset, itemY + itemHeight - itemInset,
                    AreaBuilderGuiConstants.COLOR_HOVER);
            }

            // Zone name
            UIScaleManager.drawScaledString(graphics, font, zone.displayName(), getX() + listPadding, itemY + itemTextYOffset,
                isSelected ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

            // Area count indicator
            if (zone.areaCount() > 0) {
                String countStr = "(" + zone.areaCount() + ")";
                int countWidth = UIScaleManager.getScaledStringWidth(font, countStr);
                UIScaleManager.drawScaledString(graphics, font, countStr,
                    getX() + getWidth() - countWidth - listPadding, itemY + itemTextYOffset, AreaBuilderGuiConstants.COLOR_TEXT_MUTED);
            }
        }

        // Scrollbar if needed
        if (filteredZones.size() > visibleItems) {
            int scrollbarX = getX() + getWidth() - scrollbarInset;
            int scrollbarHeight = listHeight;
            int thumbHeight = Math.max(scrollbarMinThumb, scrollbarHeight * visibleItems / filteredZones.size());
            int maxScroll = filteredZones.size() - visibleItems;
            int thumbY = listY + (scrollbarHeight - thumbHeight) * scrollOffset / maxScroll;

            // Track
            graphics.fill(scrollbarX, listY, scrollbarX + scrollbarWidth, listY + scrollbarHeight, AreaBuilderGuiConstants.COLOR_SCROLLBAR_TRACK);
            // Thumb
            graphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, AreaBuilderGuiConstants.COLOR_SCROLLBAR_THUMB);
        }

        // Selected zone info
        int infoY = listY + listHeight + s(8);
        String selectedName = selectedZoneId != null
            ? getZoneDisplayName(selectedZoneId)
            : Objects.requireNonNull(Objects.requireNonNull(Component.translatable("area.zone.none")).getString());
        UIScaleManager.drawScaledString(graphics, font,
            Objects.requireNonNull(Component.translatable("area.builder.selected_zone", selectedName)),
            getX(), infoY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
    }

    @Nonnull
    private String getZoneDisplayName(@Nonnull String zoneId) {
        for (ZoneSummary zone : allZones) {
            if (zone.id().equals(zoneId)) {
                return Objects.requireNonNull(zone.displayName());
            }
        }
        return zoneId; // Fallback to ID if not found
    }

    private void updateLayout(int fontHeight) {
        int itemHeight = s(ITEM_HEIGHT);
        int headerGap = s(HEADER_GAP);
        int footerGap = s(FOOTER_GAP);
        int fieldHeight = AreaBuilderGuiConstants.scaledFieldHeight();
        searchY = getY() + fontHeight + headerGap;
        listY = searchY + fieldHeight + headerGap;
        int infoHeight = fontHeight + footerGap;
        int availableHeight = getHeight() - (listY - getY()) - infoHeight;
        visibleItems = Math.max(1, Math.min(MAX_VISIBLE_ITEMS, availableHeight / itemHeight));
        listHeight = visibleItems * itemHeight;
        searchBox.setX(getX() + s(2));
        searchBox.setY(searchY);
        searchBox.setWidth(Math.max(s(40), getWidth() - s(4)));
        searchBox.setHeight(fieldHeight);
        int maxScroll = Math.max(0, filteredZones.size() - Math.max(1, visibleItems));
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        // Check if clicking in list area
        if (mouseY >= listY && mouseY < listY + listHeight) {
            int clickedIndex = (int) ((mouseY - listY) / s(ITEM_HEIGHT)) + scrollOffset;
            if (clickedIndex >= 0 && clickedIndex < filteredZones.size()) {
                ZoneSummary zone = filteredZones.get(clickedIndex);
                if (!Objects.equals(zone.id(), selectedZoneId)) {
                    selectedZoneId = zone.id();
                    onZoneSelected.accept(selectedZoneId);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateLayout(UIScaleManager.getScaledLineHeight(Minecraft.getInstance().font, 10));
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
        updateLayout(UIScaleManager.getScaledLineHeight(Minecraft.getInstance().font, 10));
        if (mouseX >= getX() && mouseX < getX() + getWidth()
            && mouseY >= listY && mouseY < listY + listHeight) {
            int maxScroll = Math.max(0, filteredZones.size() - visibleItems);
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
            Objects.requireNonNull(Component.translatable("area.builder.linked_zone")));
    }

    private static int s(int value) {
        return UIScaleManager.scale(value);
    }
}
