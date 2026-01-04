package com.devmod.client.endurance.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.EnduranceQuestRegistry.MobQuestConfig;
import com.devmod.endurance.EnduranceQuestRegistry.MobTier;
import com.devmod.endurance.config.EnduranceMobConfig;
import com.devmod.util.I18n;

/**
 * UI panel for displaying and filtering the list of mobs available for Endurance mode.
 * Features:
 * - Checkbox to enable/disable each mob
 * - Click to select for detailed editing
 * - Filter by namespace (minecraft, alexsmobs, etc.)
 * - Filter by tier (TRIVIAL, EASY, MEDIUM, HARD, BOSS)
 * - Search by name
 * - Scrollable list
 */
@OnlyIn(Dist.CLIENT)
public class MobListPanel {

    private static final int ITEM_HEIGHT = 22;
    private static final int CHECKBOX_SIZE = 12;
    private static final int PADDING = 4;

    // Position and size
    private int x;
    private int y;
    private int width;
    private int height;

    // Mob data
    private final List<MobListEntry> allMobs = new ArrayList<>();
    private final List<MobListEntry> filteredMobs = new ArrayList<>();
    private final Set<ResourceLocation> enabledMobs = new HashSet<>();

    // Selection state
    @Nullable
    private ResourceLocation selectedMobId;
    private int scrollOffset = 0;
    private int hoveredIndex = -1;

    // Filter state
    private String namespaceFilter = "all";
    @Nullable
    private MobTier tierFilter = null;
    private String searchQuery = "";

    // Callbacks
    @Nullable
    private Consumer<ResourceLocation> onMobSelected;
    @Nullable
    private BiConsumer<ResourceLocation, Boolean> onMobToggled;

    // Available namespaces from loaded mobs
    private final List<String> availableNamespaces = new ArrayList<>();

    public MobListPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        loadMobs();
    }

    /**
     * Load all mobs from the EnduranceQuestRegistry.
     */
    private void loadMobs() {
        allMobs.clear();
        availableNamespaces.clear();

        Set<String> namespaces = new HashSet<>();

        Collection<MobQuestConfig> configs = EnduranceQuestRegistry.INSTANCE.getAllMobConfigs();
        for (MobQuestConfig config : configs) {
            EnduranceMobConfig mobConfig = EnduranceMobConfig.fromMobQuestConfig(config);
            allMobs.add(new MobListEntry(mobConfig));
            enabledMobs.add(config.mobId);
            namespaces.add(config.mobId.getNamespace());
        }

        availableNamespaces.add("all");
        availableNamespaces.addAll(namespaces.stream().sorted().toList());

        applyFilters();
    }

    /**
     * Apply current filters to refresh the filtered list.
     */
    private void applyFilters() {
        filteredMobs.clear();
        scrollOffset = 0;

        String lowerSearch = searchQuery.toLowerCase(java.util.Locale.ROOT);

        for (MobListEntry entry : allMobs) {
            // Namespace filter
            if (!"all".equals(namespaceFilter) && !entry.config.mobId().getNamespace().equals(namespaceFilter)) {
                continue;
            }

            // Tier filter
            if (tierFilter != null && entry.config.tier() != tierFilter) {
                continue;
            }

            // Search filter
            if (!searchQuery.isEmpty()) {
                String displayName = entry.config.getDisplayName().toLowerCase(java.util.Locale.ROOT);
                String mobId = entry.config.mobId().toString().toLowerCase(java.util.Locale.ROOT);
                if (!displayName.contains(lowerSearch) && !mobId.contains(lowerSearch)) {
                    continue;
                }
            }

            filteredMobs.add(entry);
        }
    }

    // ========== Setters ==========

    public void setPosition(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setNamespaceFilter(String namespace) {
        if (!Objects.equals(this.namespaceFilter, namespace)) {
            this.namespaceFilter = namespace;
            applyFilters();
        }
    }

    public void setTierFilter(@Nullable MobTier tier) {
        if (this.tierFilter != tier) {
            this.tierFilter = tier;
            applyFilters();
        }
    }

    public void setSearchQuery(String query) {
        if (!Objects.equals(this.searchQuery, query)) {
            this.searchQuery = query;
            applyFilters();
        }
    }

    public void setOnMobSelected(Consumer<ResourceLocation> callback) {
        this.onMobSelected = callback;
    }

    public void setOnMobToggled(BiConsumer<ResourceLocation, Boolean> callback) {
        this.onMobToggled = callback;
    }

    // ========== Getters ==========

    @Nullable
    public ResourceLocation getSelectedMobId() {
        return selectedMobId;
    }

    public boolean isMobEnabled(ResourceLocation mobId) {
        return enabledMobs.contains(mobId);
    }

    public Set<ResourceLocation> getEnabledMobs() {
        return new HashSet<>(enabledMobs);
    }

    public Set<ResourceLocation> getDisabledMobs() {
        Set<ResourceLocation> disabled = new HashSet<>();
        for (MobListEntry entry : allMobs) {
            if (!enabledMobs.contains(entry.config.mobId())) {
                disabled.add(entry.config.mobId());
            }
        }
        return disabled;
    }

    public List<String> getAvailableNamespaces() {
        return new ArrayList<>(availableNamespaces);
    }

    public int getTotalMobCount() {
        return allMobs.size();
    }

    public int getFilteredMobCount() {
        return filteredMobs.size();
    }

    public int getEnabledCount() {
        return enabledMobs.size();
    }

    // ========== Actions ==========

    public void selectAll() {
        for (MobListEntry entry : filteredMobs) {
            enabledMobs.add(entry.config.mobId());
        }
        notifyAllToggled();
    }

    public void deselectAll() {
        for (MobListEntry entry : filteredMobs) {
            enabledMobs.remove(entry.config.mobId());
        }
        notifyAllToggled();
    }

    private void notifyAllToggled() {
        var callback = onMobToggled;
        if (callback != null) {
            for (MobListEntry entry : filteredMobs) {
                callback.accept(entry.config.mobId(), enabledMobs.contains(entry.config.mobId()));
            }
        }
    }

    public void setMobEnabled(ResourceLocation mobId, boolean enabled) {
        if (enabled) {
            enabledMobs.add(mobId);
        } else {
            enabledMobs.remove(mobId);
        }
    }

    /**
     * Programmatically select a mob and scroll to make it visible.
     * Used when opening the editor with a preselected mob from EnduranceQuestScreen.
     *
     * @param mobId The mob ID to select
     * @return true if the mob was found and selected
     */
    public boolean selectAndScrollTo(@Nullable ResourceLocation mobId) {
        if (mobId == null) {
            return false;
        }

        // Find the mob in the filtered list
        int index = -1;
        for (int i = 0; i < filteredMobs.size(); i++) {
            if (filteredMobs.get(i).config.mobId().equals(mobId)) {
                index = i;
                break;
            }
        }

        // If not found in filtered list, try to find in all mobs and clear filters
        if (index < 0) {
            boolean foundInAll = false;
            for (MobListEntry entry : allMobs) {
                if (entry.config.mobId().equals(mobId)) {
                    foundInAll = true;
                    break;
                }
            }

            if (foundInAll) {
                // Clear filters to show the mob
                namespaceFilter = "all";
                tierFilter = null;
                searchQuery = "";
                applyFilters();

                // Find again after clearing filters
                for (int i = 0; i < filteredMobs.size(); i++) {
                    if (filteredMobs.get(i).config.mobId().equals(mobId)) {
                        index = i;
                        break;
                    }
                }
            }
        }

        if (index < 0) {
            return false;
        }

        // Select the mob
        selectedMobId = mobId;

        // Scroll to make it visible (center it if possible)
        int contentHeight = height - PADDING * 2;
        int visibleItems = contentHeight / ITEM_HEIGHT;
        int targetScroll = Math.max(0, index - visibleItems / 2);
        int maxScroll = Math.max(0, filteredMobs.size() - visibleItems);
        scrollOffset = Math.min(targetScroll, maxScroll);

        // Notify callback
        if (onMobSelected != null) {
            onMobSelected.accept(mobId);
        }

        return true;
    }

    // ========== Rendering ==========

    public void render(GuiGraphics graphics, @Nonnull Font font, int mouseX, int mouseY) {
        // Background
        graphics.fill(x, y, x + width, y + height, DesignTokens.Surface.LEVEL_0);
        graphics.renderOutline(x, y, width, height, DesignTokens.Border.DEFAULT);

        // Calculate visible range
        int contentHeight = height - PADDING * 2;
        int visibleItems = contentHeight / ITEM_HEIGHT;
        int maxScroll = Math.max(0, filteredMobs.size() - visibleItems);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        // Track hover
        hoveredIndex = -1;

        // Render items
        int itemY = y + PADDING;
        for (int i = 0; i < visibleItems && (i + scrollOffset) < filteredMobs.size(); i++) {
            int index = i + scrollOffset;
            MobListEntry entry = filteredMobs.get(index);
            boolean isSelected = entry.config.mobId().equals(selectedMobId);
            boolean isEnabled = enabledMobs.contains(entry.config.mobId());
            boolean isHovered = mouseX >= x && mouseX < x + width
                && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;

            if (isHovered) {
                hoveredIndex = index;
            }

            renderItem(graphics, font, entry, itemY, isSelected, isEnabled, isHovered, mouseX);
            itemY += ITEM_HEIGHT;
        }

        // Scrollbar
        if (filteredMobs.size() > visibleItems) {
            renderScrollbar(graphics, visibleItems, maxScroll);
        }

        // Empty state
        if (filteredMobs.isEmpty()) {
            String emptyKey = searchQuery.isEmpty()
                ? "devmod.endurance.mob_editor.no_mobs"
                : "devmod.endurance.mob_editor.no_results";
            String emptyText = I18n.translate(emptyKey).getString();
            if (emptyText != null) {
                int textWidth = font.width(emptyText);
                graphics.drawString(font, emptyText, x + (width - textWidth) / 2, y + height / 2 - 4,
                    DesignTokens.Text.MUTED, false);
            }
        }
    }

    private void renderItem(GuiGraphics graphics, @Nonnull Font font, MobListEntry entry,
            int itemY, boolean isSelected, boolean isEnabled, boolean isHovered, int mouseX) {

        int itemX = x + PADDING;
        int itemWidth = width - PADDING * 2 - 8; // Reserve space for scrollbar

        // Highlight
        if (isSelected) {
            graphics.fill(itemX, itemY, itemX + itemWidth, itemY + ITEM_HEIGHT - 2,
                DesignTokens.Surface.LEVEL_2);
        } else if (isHovered) {
            graphics.fill(itemX, itemY, itemX + itemWidth, itemY + ITEM_HEIGHT - 2,
                DesignTokens.withAlpha(DesignTokens.Surface.LEVEL_1, DesignTokens.Alpha.A25));
        }

        // Checkbox
        int checkboxX = itemX + 2;
        int checkboxY = itemY + (ITEM_HEIGHT - CHECKBOX_SIZE) / 2;
        renderCheckbox(graphics, checkboxX, checkboxY, isEnabled, mouseX >= checkboxX && mouseX < checkboxX + CHECKBOX_SIZE);

        // Mob name
        int textX = checkboxX + CHECKBOX_SIZE + 6;
        int textColor = isEnabled ? DesignTokens.Text.PRIMARY : DesignTokens.Text.MUTED;
        String displayName = entry.config.getDisplayName();
        if (displayName.length() > 0 && font.width(displayName) > itemWidth - 60) {
            String truncated = font.plainSubstrByWidth(displayName, itemWidth - 70);
            displayName = truncated + "..";
        }
        graphics.drawString(font, displayName, textX, itemY + 3, textColor, false);

        // Tier badge
        int badgeColor = getTierColor(entry.config.tier());
        String tierLabel = entry.config.tier().name().substring(0, 1);
        int badgeX = itemX + itemWidth - 18;
        graphics.fill(badgeX, itemY + 2, badgeX + 14, itemY + 14, badgeColor);
        graphics.drawString(font, tierLabel, badgeX + 4, itemY + 4, DesignTokens.Text.WHITE, false);

        // Namespace (small, below name)
        String namespace = entry.config.mobId().getNamespace();
        graphics.drawString(font, namespace, textX, itemY + 12, DesignTokens.Text.SECONDARY, false);
    }

    private void renderCheckbox(GuiGraphics graphics, int x, int y, boolean checked, boolean hovered) {
        int bgColor = hovered ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_1;
        int borderColor = checked ? DesignTokens.Accent.PRIMARY : DesignTokens.Border.DEFAULT;

        graphics.fill(x, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, bgColor);
        graphics.renderOutline(x, y, CHECKBOX_SIZE, CHECKBOX_SIZE, borderColor);

        if (checked) {
            // Checkmark
            graphics.fill(x + 3, y + 6, x + 5, y + 9, DesignTokens.Accent.PRIMARY);
            graphics.fill(x + 5, y + 4, x + 7, y + 9, DesignTokens.Accent.PRIMARY);
            graphics.fill(x + 7, y + 3, x + 9, y + 7, DesignTokens.Accent.PRIMARY);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int visibleItems, int maxScroll) {
        int scrollbarX = x + width - 6;
        int scrollbarHeight = height - PADDING * 2;
        int scrollbarY = y + PADDING;

        // Track
        graphics.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight,
            DesignTokens.Surface.LEVEL_1);

        // Thumb
        float scrollRatio = (float) scrollOffset / maxScroll;
        float thumbRatio = (float) visibleItems / filteredMobs.size();
        int thumbHeight = Math.max(20, (int) (scrollbarHeight * thumbRatio));
        int thumbY = scrollbarY + (int) ((scrollbarHeight - thumbHeight) * scrollRatio);

        graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight,
            DesignTokens.Border.LIGHT);
    }

    private int getTierColor(MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> DesignTokens.Text.MUTED;
            case EASY -> DesignTokens.Semantic.SUCCESS;
            case MEDIUM -> DesignTokens.Semantic.WARNING;
            case HARD -> DesignTokens.Semantic.ERROR;
            case ELITE -> DesignTokens.Accent.GOLD();
            case BOSS -> DesignTokens.Accent.PURPLE();
        };
    }

    // ========== Input Handling ==========

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }

        if (hoveredIndex >= 0 && hoveredIndex < filteredMobs.size()) {
            MobListEntry entry = filteredMobs.get(hoveredIndex);

            // Check if clicking on checkbox
            int checkboxX = x + PADDING + 2;
            if (mouseX >= checkboxX && mouseX < checkboxX + CHECKBOX_SIZE) {
                // Toggle enabled
                boolean newState = !enabledMobs.contains(entry.config.mobId());
                if (newState) {
                    enabledMobs.add(entry.config.mobId());
                } else {
                    enabledMobs.remove(entry.config.mobId());
                }
                if (onMobToggled != null) {
                    onMobToggled.accept(entry.config.mobId(), newState);
                }
                return true;
            }

            // Select mob
            selectedMobId = entry.config.mobId();
            if (onMobSelected != null) {
                onMobSelected.accept(selectedMobId);
            }
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }

        int contentHeight = height - PADDING * 2;
        int visibleItems = contentHeight / ITEM_HEIGHT;
        int maxScroll = Math.max(0, filteredMobs.size() - visibleItems);

        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) deltaY));
        return true;
    }

    // ========== Entry Class ==========

    private static class MobListEntry {
        final EnduranceMobConfig config;

        MobListEntry(EnduranceMobConfig config) {
            this.config = config;
        }
    }
}
