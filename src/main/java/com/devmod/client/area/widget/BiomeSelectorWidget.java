package com.devmod.client.area.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.area.aesthetic.AreaBuilderGuiConstants;
import com.devmod.area.builder.BiomeRegistry;
import com.devmod.client.ui.AxiomRenderer;

/**
 * Widget for selecting biomes from a searchable list.
 */
@OnlyIn(Dist.CLIENT)
public class BiomeSelectorWidget extends AbstractWidget {

    private static final int ITEM_HEIGHT = 20;
    private static final int MAX_VISIBLE_ITEMS = 8;
    private static final int HEADER_GAP = 6;
    private static final int FOOTER_GAP = 6;

    private ResourceLocation selectedBiome;
    private final Consumer<ResourceLocation> onBiomeSelected;

    private String searchQuery = "";
    private List<BiomeRegistry.BiomeEntry> filteredBiomes = new ArrayList<>();
    private int scrollOffset = 0;
    private final EditBox searchBox;
    private int searchY;
    private int listY;
    private int listHeight;
    private int visibleItems = MAX_VISIBLE_ITEMS;

    // Fallback biomes if BiomeRegistry not initialized
    private static final List<BiomeRegistry.BiomeEntry> FALLBACK_BIOMES = List.of(
        new BiomeRegistry.BiomeEntry(ResourceLocation.withDefaultNamespace("plains"), "Plains", "minecraft", true),
        new BiomeRegistry.BiomeEntry(ResourceLocation.withDefaultNamespace("forest"), "Forest", "minecraft", true),
        new BiomeRegistry.BiomeEntry(ResourceLocation.withDefaultNamespace("desert"), "Desert", "minecraft", true),
        new BiomeRegistry.BiomeEntry(ResourceLocation.withDefaultNamespace("mountains"), "Mountains", "minecraft", true),
        new BiomeRegistry.BiomeEntry(ResourceLocation.withDefaultNamespace("ocean"), "Ocean", "minecraft", true),
        new BiomeRegistry.BiomeEntry(ResourceLocation.withDefaultNamespace("swamp"), "Swamp", "minecraft", true),
        new BiomeRegistry.BiomeEntry(ResourceLocation.withDefaultNamespace("jungle"), "Jungle", "minecraft", true),
        new BiomeRegistry.BiomeEntry(ResourceLocation.withDefaultNamespace("taiga"), "Taiga", "minecraft", true)
    );

    @SuppressWarnings("this-escape")
    public BiomeSelectorWidget(int x, int y, int width, int height,
                              ResourceLocation initialBiome,
                              Consumer<ResourceLocation> onBiomeSelected) {
        super(x, y, width, height, Component.empty());
        this.selectedBiome = initialBiome != null ? initialBiome : ResourceLocation.withDefaultNamespace("plains");
        this.onBiomeSelected = onBiomeSelected;
        this.searchBox = new EditBox(Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font),
            x + 2, y + 15 + 2, width - 4, AreaBuilderGuiConstants.FIELD_HEIGHT,
            Objects.requireNonNull(Component.translatable("area.biome.search")));
        this.searchBox.setHint(Objects.requireNonNull(Component.translatable("area.biome.search")));
        this.searchBox.setMaxLength(64);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);
        this.searchBox.setTextColorUneditable(AreaBuilderGuiConstants.COLOR_TEXT_DISABLED);
        this.searchBox.setResponder(this::onSearchChanged);
        updateLayout(net.minecraft.client.Minecraft.getInstance().font.lineHeight);
        refreshBiomeList();
    }

    private void refreshBiomeList() {
        List<BiomeRegistry.BiomeEntry> allBiomes;

        if (BiomeRegistry.isInitialized()) {
            allBiomes = BiomeRegistry.getAllBiomes();
        } else {
            allBiomes = FALLBACK_BIOMES;
        }

        if (searchQuery.isEmpty()) {
            filteredBiomes = new ArrayList<>(allBiomes);
        } else {
            String query = searchQuery.toLowerCase(Locale.ROOT);
            filteredBiomes = allBiomes.stream()
                .filter(b -> b.displayName().toLowerCase(Locale.ROOT).contains(query) ||
                            b.id().toString().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        }

        // Reset scroll if out of bounds
        int maxScroll = Math.max(0, filteredBiomes.size() - Math.max(1, visibleItems));
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    private void onSearchChanged(String query) {
        searchQuery = query != null ? query : "";
        refreshBiomeList();
    }

    @Override
    protected void renderWidget(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Objects.requireNonNull(net.minecraft.client.Minecraft.getInstance().font);
        updateLayout(font.lineHeight);

        // Title
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.builder.tab.biome")),
            getX(), getY(),
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Search box area
        AxiomRenderer.drawInputBackground(graphics, searchBox.getX(), searchBox.getY(), searchBox.getWidth(),
            searchBox.getHeight(), searchBox.isFocused());
        searchBox.render(graphics, mouseX, mouseY, partialTick);

        // Biome list
        // List background
        graphics.fill(getX(), listY, getX() + getWidth(), listY + listHeight,
            AreaBuilderGuiConstants.COLOR_PANEL);
        graphics.renderOutline(getX(), listY, getWidth(), listHeight,
            AreaBuilderGuiConstants.COLOR_BORDER);

        if (filteredBiomes.isEmpty()) {
            graphics.drawCenteredString(font,
                Objects.requireNonNull(Component.translatable("area.biome.empty")),
                getX() + getWidth() / 2, listY + listHeight / 2 - 4,
                AreaBuilderGuiConstants.COLOR_TEXT_MUTED);
        }

        // Draw visible biomes
        for (int i = 0; i < visibleItems && i + scrollOffset < filteredBiomes.size(); i++) {
            BiomeRegistry.BiomeEntry biome = filteredBiomes.get(i + scrollOffset);
            int itemY = listY + i * ITEM_HEIGHT;

            boolean isHovered = mouseX >= getX() && mouseX < getX() + getWidth() &&
                               mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean isSelected = biome.id().equals(selectedBiome);

            // Item background
            if (isSelected) {
                graphics.fill(getX() + 1, itemY, getX() + getWidth() - 1, itemY + ITEM_HEIGHT - 1,
                    AreaBuilderGuiConstants.COLOR_TAB_ACTIVE);
            } else if (isHovered) {
                graphics.fill(getX() + 1, itemY, getX() + getWidth() - 1, itemY + ITEM_HEIGHT - 1,
                    AreaBuilderGuiConstants.COLOR_HOVER);
            }

            // Biome name
            graphics.drawString(font, biome.displayName(), getX() + 4, itemY + 6,
                isSelected ? AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY : AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY);

            // Mod indicator
            if (!biome.isVanilla()) {
                String modIndicator = "[" + biome.modId() + "]";
                int modWidth = font.width(modIndicator);
                graphics.drawString(font, modIndicator,
                    getX() + getWidth() - modWidth - 4, itemY + 6, AreaBuilderGuiConstants.COLOR_TEXT_MUTED);
            }
        }

        // Scrollbar if needed
        if (filteredBiomes.size() > visibleItems) {
            int scrollbarX = getX() + getWidth() - 6;
            int scrollbarHeight = listHeight;
            int thumbHeight = Math.max(20, scrollbarHeight * visibleItems / filteredBiomes.size());
            int maxScroll = filteredBiomes.size() - visibleItems;
            int thumbY = listY + (scrollbarHeight - thumbHeight) * scrollOffset / maxScroll;

            // Track
            graphics.fill(scrollbarX, listY, scrollbarX + 4, listY + scrollbarHeight, AreaBuilderGuiConstants.COLOR_SCROLLBAR_TRACK);
            // Thumb
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, AreaBuilderGuiConstants.COLOR_SCROLLBAR_THUMB);
        }

        // Selected biome info
        int infoY = listY + listHeight + 8;
        String selectedName = BiomeRegistry.getBiomeDisplayName(Objects.requireNonNull(selectedBiome));
        graphics.drawString(font,
            Objects.requireNonNull(Component.translatable("area.builder.selected_biome", selectedName)),
            getX(), infoY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );
    }

    private void updateLayout(int fontHeight) {
        searchY = getY() + fontHeight + HEADER_GAP;
        listY = searchY + AreaBuilderGuiConstants.FIELD_HEIGHT + HEADER_GAP;
        int infoHeight = fontHeight + FOOTER_GAP;
        int availableHeight = getHeight() - (listY - getY()) - infoHeight;
        visibleItems = Math.max(1, Math.min(MAX_VISIBLE_ITEMS, availableHeight / ITEM_HEIGHT));
        listHeight = visibleItems * ITEM_HEIGHT;
        searchBox.setX(getX() + 2);
        searchBox.setY(searchY);
        searchBox.setWidth(Math.max(40, getWidth() - 4));
        int maxScroll = Math.max(0, filteredBiomes.size() - Math.max(1, visibleItems));
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        // Check if clicking in list area
        if (mouseY >= listY && mouseY < listY + listHeight) {
            int clickedIndex = (int) ((mouseY - listY) / ITEM_HEIGHT) + scrollOffset;
            if (clickedIndex >= 0 && clickedIndex < filteredBiomes.size()) {
                BiomeRegistry.BiomeEntry biome = filteredBiomes.get(clickedIndex);
                if (!biome.id().equals(selectedBiome)) {
                    selectedBiome = biome.id();
                    if (onBiomeSelected != null) {
                        onBiomeSelected.accept(selectedBiome);
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int fontHeight = net.minecraft.client.Minecraft.getInstance().font.lineHeight;
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
        int fontHeight = net.minecraft.client.Minecraft.getInstance().font.lineHeight;
        updateLayout(fontHeight);
        if (mouseX >= getX() && mouseX < getX() + getWidth()
            && mouseY >= listY && mouseY < listY + listHeight) {
            int maxScroll = Math.max(0, filteredBiomes.size() - visibleItems);
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

    public ResourceLocation getSelectedBiome() {
        return selectedBiome;
    }

    public void setSelectedBiome(ResourceLocation biome) {
        if (biome != null) {
            this.selectedBiome = biome;
        }
    }

    @Override
    protected void updateWidgetNarration(@Nonnull NarrationElementOutput narration) {
        narration.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            Objects.requireNonNull(Component.translatable("area.builder.tab.biome")));
    }
}
