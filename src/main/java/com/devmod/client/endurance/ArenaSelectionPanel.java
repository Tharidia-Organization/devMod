package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.arena.policy.TemplateSuggestion;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.ArenaSuggestionsPayload;
import com.devmod.endurance.RequestArenaSuggestionsPayload;

/**
 * UI panel for selecting arena templates based on MobRequirements.
 * Shows auto-selected template with score breakdown and allows manual override.
 */
@OnlyIn(Dist.CLIENT)
public class ArenaSelectionPanel {

    private static final int ITEM_HEIGHT = 24;
    private static final int MAX_VISIBLE_ITEMS = 5;

    private int x;
    private int y;
    private int width;
    private int height;

    private List<TemplateSuggestion> suggestions = new ArrayList<>();
    @Nullable
    private String selectedTemplateId = null;
    @Nullable
    private String autoSelectedTemplateId = null;
    private boolean expanded = false;
    private int scrollOffset = 0;

    @Nullable
    private Consumer<String> onSelectionChanged;

    // Track pending request to avoid race conditions with late responses
    @Nullable
    private volatile String pendingMobId;

    // Store listener reference to avoid method reference identity issues
    private final Consumer<ArenaSuggestionsPayload> suggestionsListener = this::onSuggestionsReceived;

    public ArenaSelectionPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        // Register for suggestion updates
        ClientArenaSuggestionsCache.INSTANCE.addListener(suggestionsListener);
    }

    /**
     * Request suggestions for a mob from the server.
     */
    public void requestSuggestions(String mobId) {
        this.pendingMobId = mobId;
        PacketDistributor.sendToServer(new RequestArenaSuggestionsPayload(mobId));
    }

    /**
     * Called when suggestions are received from the server.
     */
    private void onSuggestionsReceived(ArenaSuggestionsPayload payload) {
        // Ignore late responses from previous requests (race condition protection)
        String pending = this.pendingMobId;
        if (pending != null && !pending.equals(payload.mobId())) {
            return;
        }

        this.suggestions = new ArrayList<>(payload.suggestions());
        this.autoSelectedTemplateId = payload.selectedTemplateId();

        // If no manual selection, use auto-selected
        if (selectedTemplateId == null) {
            selectedTemplateId = autoSelectedTemplateId;
        }
    }

    /**
     * Gets the currently selected template ID (may be null for auto).
     */
    @Nullable
    public String getSelectedTemplateId() {
        return selectedTemplateId;
    }

    /**
     * Gets the override template ID (null if using auto-selection).
     */
    @Nullable
    public String getOverrideTemplateId() {
        // If selection matches auto-selection, return null (no override)
        if (selectedTemplateId != null && selectedTemplateId.equals(autoSelectedTemplateId)) {
            return null;
        }
        return selectedTemplateId;
    }

    /**
     * Sets a listener for selection changes.
     */
    public void setOnSelectionChanged(Consumer<String> listener) {
        this.onSelectionChanged = listener;
    }

    /**
     * Resets selection to auto mode.
     */
    public void resetToAuto() {
        this.selectedTemplateId = autoSelectedTemplateId;
        if (onSelectionChanged != null) {
            onSelectionChanged.accept(selectedTemplateId);
        }
    }

    /**
     * Clears all state.
     */
    public void clear() {
        this.suggestions.clear();
        this.selectedTemplateId = null;
        this.autoSelectedTemplateId = null;
        this.pendingMobId = null;
        this.expanded = false;
        this.scrollOffset = 0;
    }

    /**
     * Update the panel's position dynamically.
     * Call this before render() to reposition the panel.
     */
    public void setPosition(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Render the panel.
     */
    public void render(GuiGraphics graphics, @Nonnull Font font, int mouseX, int mouseY) {
        Font safeFont = Objects.requireNonNull(font);

        // Background
        graphics.fill(x, y, x + width, y + height, DesignTokens.Surface.LEVEL_0);
        graphics.renderOutline(x, y, width, height, DesignTokens.Border.DEFAULT);

        // Title
        graphics.drawString(safeFont, "Arena", x + 6, y + 4, DesignTokens.Text.PRIMARY, false);

        if (suggestions.isEmpty()) {
            graphics.drawString(safeFont, "Select a mob...", x + 6, y + 18, DesignTokens.Text.MUTED, false);
            return;
        }

        // Current selection display
        TemplateSuggestion current = getCurrentSuggestion();
        if (current != null) {
            int textY = y + 18;
            String displayName = truncate(current.templateName(), 20);
            graphics.drawString(safeFont, displayName, x + 6, textY, DesignTokens.Text.PRIMARY, false);

            // Score indicator
            String scoreText = String.format("%.1f", current.compatibilityScore());
            int scoreColor = current.compatibilityScore() >= 8 ? DesignTokens.Semantic.SUCCESS
                : current.compatibilityScore() >= 4 ? DesignTokens.Semantic.WARNING
                : DesignTokens.Text.SECONDARY;
            graphics.drawString(safeFont, scoreText, x + width - 30, textY, scoreColor, false);

            // Auto/Manual indicator
            String currentSelection = selectedTemplateId;
            boolean isAuto = currentSelection == null || currentSelection.equals(autoSelectedTemplateId);
            String modeText = isAuto ? "[Auto]" : "[Manual]";
            int modeColor = isAuto ? DesignTokens.Accent.PRIMARY : DesignTokens.Semantic.WARNING;
            graphics.drawString(safeFont, modeText, x + 6, textY + 12, modeColor, false);
        }

        // Dropdown arrow
        String arrow = expanded ? "\u25B2" : "\u25BC";
        graphics.drawString(safeFont, arrow, x + width - 14, y + 4, DesignTokens.Text.SECONDARY, false);

        // Expanded dropdown
        if (expanded) {
            renderDropdown(graphics, safeFont, mouseX, mouseY);
        }
    }

    private void renderDropdown(GuiGraphics graphics, @Nonnull Font font, int mouseX, int mouseY) {
        int dropdownY = y + height;
        int visibleItems = Math.min(MAX_VISIBLE_ITEMS, suggestions.size());
        int dropdownHeight = visibleItems * ITEM_HEIGHT + 4;

        // Dropdown background
        graphics.fill(x, dropdownY, x + width, dropdownY + dropdownHeight, DesignTokens.Surface.LEVEL_1);
        graphics.renderOutline(x, dropdownY, width, dropdownHeight, DesignTokens.Border.DEFAULT);

        // Items
        for (int i = 0; i < visibleItems; i++) {
            int index = i + scrollOffset;
            if (index >= suggestions.size()) break;

            TemplateSuggestion suggestion = suggestions.get(index);
            int itemY = dropdownY + 2 + i * ITEM_HEIGHT;

            // Hover/selected highlight
            boolean hovered = mouseX >= x && mouseX < x + width
                && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean selected = suggestion.templateId().equals(selectedTemplateId);

            if (selected) {
                graphics.fill(x + 2, itemY, x + width - 2, itemY + ITEM_HEIGHT - 1,
                    DesignTokens.Surface.LEVEL_2);
            } else if (hovered) {
                graphics.fill(x + 2, itemY, x + width - 2, itemY + ITEM_HEIGHT - 1,
                    DesignTokens.withAlpha(DesignTokens.Surface.LEVEL_1, DesignTokens.Alpha.A25));
            }

            // Template name
            int textColor = suggestion.isCompatible()
                ? DesignTokens.Text.PRIMARY
                : DesignTokens.Text.MUTED;
            String displayName = truncate(suggestion.templateName(), 18);
            graphics.drawString(font, displayName, x + 6, itemY + 3, textColor, false);

            // Size and score
            String info = suggestion.size() + "b";
            if (suggestion.isCompatible()) {
                info += String.format(" (%.1f)", suggestion.compatibilityScore());
            }
            graphics.drawString(font, info, x + 6, itemY + 13, DesignTokens.Text.SECONDARY, false);

            // Incompatibility warning
            if (!suggestion.isCompatible()) {
                graphics.drawString(font, "\u26A0", x + width - 16, itemY + 6,
                    DesignTokens.Semantic.ERROR, false);
            }
        }
    }

    @Nullable
    private TemplateSuggestion getCurrentSuggestion() {
        if (selectedTemplateId == null) return null;
        return suggestions.stream()
            .filter(s -> s.templateId().equals(selectedTemplateId))
            .findFirst()
            .orElse(null);
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 2) + "..";
    }

    /**
     * Handle mouse click.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Toggle dropdown
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            expanded = !expanded;
            return true;
        }

        // Click in dropdown
        if (expanded) {
            int dropdownY = y + height;
            int visibleItems = Math.min(MAX_VISIBLE_ITEMS, suggestions.size());
            int dropdownHeight = visibleItems * ITEM_HEIGHT + 4;

            if (mouseX >= x && mouseX < x + width
                && mouseY >= dropdownY && mouseY < dropdownY + dropdownHeight) {

                int relY = (int) mouseY - dropdownY - 2;
                int clickedIndex = relY / ITEM_HEIGHT + scrollOffset;

                if (clickedIndex >= 0 && clickedIndex < suggestions.size()) {
                    TemplateSuggestion clicked = suggestions.get(clickedIndex);
                    if (clicked.isCompatible()) {
                        selectedTemplateId = clicked.templateId();
                        if (onSelectionChanged != null) {
                            onSelectionChanged.accept(selectedTemplateId);
                        }
                    }
                }
                expanded = false;
                return true;
            }

            // Click outside closes dropdown
            expanded = false;
        }

        return false;
    }

    /**
     * Handle mouse scroll.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!expanded) return false;

        int dropdownY = y + height;
        int visibleItems = Math.min(MAX_VISIBLE_ITEMS, suggestions.size());
        int dropdownHeight = visibleItems * ITEM_HEIGHT + 4;

        if (mouseX >= x && mouseX < x + width
            && mouseY >= dropdownY && mouseY < dropdownY + dropdownHeight) {

            int maxScroll = Math.max(0, suggestions.size() - MAX_VISIBLE_ITEMS);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) deltaY));
            return true;
        }

        return false;
    }

    /**
     * Cleanup when panel is removed.
     */
    public void cleanup() {
        ClientArenaSuggestionsCache.INSTANCE.removeListener(suggestionsListener);
    }
}
