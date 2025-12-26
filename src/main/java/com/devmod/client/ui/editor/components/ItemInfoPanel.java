package com.devmod.client.ui.editor.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.EditorConstants;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.StringBuilderCache;
import com.devmod.client.ui.editor.core.Typography;
import com.devmod.client.ui.editor.core.UIConstants;

/**
 * Item info panel showing item name, key stats, and dirty indicator.
 * Displayed in the left column below the slot selector.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 2.5 (Left Column - Item Info)
 * @see EDITOR_DESIGN_SYSTEM.md Section 4.10 (Item Info Panel)
 */
public class ItemInfoPanel {

    // ═══════════════════════════════════════════════════════════════
    // DIMENSIONS (from Section 2.5)
    // ═══════════════════════════════════════════════════════════════

    private static final int HEIGHT = EditorConstants.INFO_PANEL_HEIGHT;  // 100px
    private static final int LINE_HEIGHT = 12;
    private static final int PADDING = 8;
    private static final String LABEL_SEPARATOR = ":";
    private static final String UNSAVED_CHANGES_PREFIX = "● ";
    private static final String UNSAVED_CHANGES_SUFFIX = " unsaved changes";
    private static final String SAVED_PREFIX = "✓ Saved ";
    private static final String ELLIPSIS = "...";
    private static final String TIME_JUST_NOW = "just now";
    private static final String TIME_MINUTES_SUFFIX = "m ago";
    private static final String TIME_HOURS_SUFFIX = "h ago";
    private static final String TIME_YESTERDAY = "yesterday";
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long MINUTES_PER_HOUR = 60L;
    private static final long HOURS_PER_DAY = 24L;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private ItemStack item = ItemStack.EMPTY;
    private String itemName = "";
    private final List<StatLine> stats = new ArrayList<>();
    private int pendingChanges = 0;
    private long lastSaveTimestamp = 0;

    // Bounds
    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;

    // ═══════════════════════════════════════════════════════════════
    // STAT LINE RECORD
    // ═══════════════════════════════════════════════════════════════

    /**
     * A single stat line to display.
     */
    public record StatLine(String label, String value, int valueColor) {
        public StatLine(String label, String value) {
            this(label, value, UIConstants.Text.VALUE());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public ItemInfoPanel() {}

    // ═══════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════

    public ItemInfoPanel item(ItemStack item) {
        this.item = item != null ? item : ItemStack.EMPTY;
        this.itemName = this.item.isEmpty() ? "" : this.item.getHoverName().getString();
        return this;
    }

    public ItemInfoPanel itemName(String name) {
        this.itemName = name != null ? name : "";
        return this;
    }

    public ItemInfoPanel clearStats() {
        this.stats.clear();
        return this;
    }

    public ItemInfoPanel addStat(String label, String value) {
        this.stats.add(new StatLine(label, value));
        return this;
    }

    public ItemInfoPanel addStat(String label, String value, int valueColor) {
        this.stats.add(new StatLine(label, value, valueColor));
        return this;
    }

    public ItemInfoPanel addStat(String label, float value, String format) {
        String formatted = String.format(format, value);
        int color = value > 0 ? UIConstants.Accent.GREEN() :
                   (value < 0 ? UIConstants.Accent.RED() : UIConstants.Text.VALUE());
        String prefix = value > 0 ? "+" : "";
        this.stats.add(new StatLine(label, prefix + formatted, color));
        return this;
    }

    public ItemInfoPanel pendingChanges(int count) {
        this.pendingChanges = count;
        return this;
    }

    public ItemInfoPanel lastSaved(long timestamp) {
        this.lastSaveTimestamp = timestamp;
        return this;
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Render the info panel at the given position.
     * @return The height consumed
     */
    public int render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        var font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
        float nameScale = Typography.sectionHeaderScale();
        float statScale = Typography.valueScale();
        float dirtyScale = Typography.valueScale();

        int panelHeight = ScaledCoord.scaleDim(HEIGHT);
        int padding = ScaledCoord.scaleDim(PADDING);
        int lineHeight = Math.max(ScaledCoord.scaleDim(LINE_HEIGHT), Math.round(font.lineHeight * statScale));

        this.bounds = new ResponsiveLayout.Rect(x, y, width, panelHeight);

        // Background
        graphics.fill(x, y, x + width, y + panelHeight, UIConstants.Background.INPUT());

        // Border
        AxiomRenderer.drawBorder(graphics, x, y, width, panelHeight, UIConstants.Border.DEFAULT());

        int contentX = x + padding;
        int contentWidth = width - padding * 2;
        int currentY = y + padding;

        // Item name (title)
        if (!itemName.isEmpty()) {
            // Truncate if too long
            String displayName = truncateText(font, itemName, contentWidth);
            int nameHeight = Math.round(font.lineHeight * nameScale);
            Typography.drawText(graphics, font, displayName, contentX, currentY, UIConstants.Text.TITLE(), nameScale);
            currentY += Math.max(lineHeight, nameHeight);
        }

        // Stats
        for (StatLine stat : stats) {
            if (currentY + lineHeight > y + panelHeight - padding - lineHeight) {
                // Not enough space, stop rendering stats
                break;
            }
            renderStatLine(graphics, font, contentX, currentY, contentWidth, lineHeight, stat, statScale);
            currentY += lineHeight;
        }

        // Dirty indicator at bottom
        renderDirtyIndicator(graphics, font, x,
            y + panelHeight - lineHeight - ScaledCoord.scaleDim(UIConstants.Spacing.SM),
            width, lineHeight, dirtyScale);

        return panelHeight;
    }

    private void renderStatLine(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                int x, int y, int width, int lineHeight, StatLine stat, float scale) {
        // Label on left
        String label = Objects.requireNonNull(stat.label(), "stat label cannot be null");
        Typography.drawText(graphics, Objects.requireNonNull(font, "font cannot be null"),
            label + LABEL_SEPARATOR, x, y, UIConstants.Text.SECONDARY(), scale);

        // Value on right
        String value = stat.value();
        if (value != null) {
            int valueWidth = Math.round(font.width(value) * scale);
            int valueX = x + width - valueWidth;
            Typography.drawText(graphics, font, value, valueX, y, stat.valueColor(), scale);
        }
    }

    private void renderDirtyIndicator(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                      int x, int y, int width, int lineHeight, float scale) {
        String text;
        int color;

        if (pendingChanges > 0) {
            text = UNSAVED_CHANGES_PREFIX + pendingChanges + UNSAVED_CHANGES_SUFFIX;
            color = UIConstants.Accent.ORANGE();
        } else if (lastSaveTimestamp > 0) {
            long ago = System.currentTimeMillis() - lastSaveTimestamp;
            String timeText = formatTimeAgo(ago);
            text = SAVED_PREFIX + timeText;
            color = UIConstants.Accent.GREEN();
        } else {
            // No indicator needed
            return;
        }

        // Center the indicator
        int textWidth = Math.round(font.width(text) * scale);
        int textX = x + (width - textWidth) / 2;
        Typography.drawText(graphics, font, text, textX, y, color, scale);
    }

    private String truncateText(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        String safeText = Objects.requireNonNull(text, "text cannot be null");
        if (font.width(safeText) <= maxWidth) {
            return safeText;
        }

        int ellipsisWidth = font.width(ELLIPSIS);
        int availableWidth = maxWidth - ellipsisWidth;

        // Use StringBuilderCache to avoid allocations in render loop
        StringBuilder sb = StringBuilderCache.acquire();
        for (char c : text.toCharArray()) {
            if (font.width(sb.toString() + c) > availableWidth) {
                break;
            }
            sb.append(c);
        }

        return StringBuilderCache.release(sb) + ELLIPSIS;
    }

    private String formatTimeAgo(long millis) {
        long seconds = millis / MILLIS_PER_SECOND;
        if (seconds < SECONDS_PER_MINUTE) return TIME_JUST_NOW;
        long minutes = seconds / SECONDS_PER_MINUTE;
        if (minutes < MINUTES_PER_HOUR) return minutes + TIME_MINUTES_SUFFIX;
        long hours = minutes / MINUTES_PER_HOUR;
        if (hours < HOURS_PER_DAY) return hours + TIME_HOURS_SUFFIX;
        return TIME_YESTERDAY;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS & SETTERS
    // ═══════════════════════════════════════════════════════════════

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item != null ? item : ItemStack.EMPTY;
        this.itemName = this.item.isEmpty() ? "" : this.item.getHoverName().getString();
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String name) {
        this.itemName = name != null ? name : "";
    }

    public List<StatLine> getStats() {
        return stats;
    }

    public int getPendingChanges() {
        return pendingChanges;
    }

    public void setPendingChanges(int count) {
        this.pendingChanges = count;
    }

    public long getLastSaveTimestamp() {
        return lastSaveTimestamp;
    }

    public void setLastSaveTimestamp(long timestamp) {
        this.lastSaveTimestamp = timestamp;
    }

    public int getHeight() {
        return HEIGHT;
    }

    public ResponsiveLayout.Rect getBounds() {
        return bounds;
    }

    /**
     * Check if there are pending changes.
     */
    public boolean isDirty() {
        return pendingChanges > 0;
    }

    /**
     * Mark as saved (clears pending changes, updates timestamp).
     */
    public void markSaved() {
        this.pendingChanges = 0;
        this.lastSaveTimestamp = System.currentTimeMillis();
    }

    /**
     * Reset dirty state completely.
     */
    public void resetDirtyState() {
        this.pendingChanges = 0;
        this.lastSaveTimestamp = 0;
    }
}
