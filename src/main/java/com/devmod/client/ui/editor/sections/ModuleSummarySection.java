package com.devmod.client.ui.editor.sections;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.devmod.client.ui.editor.EditorSection;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.UIConstants;
public final class ModuleSummarySection implements EditorSection.CustomSection {

    private static final int HEADER_HEIGHT = 20;
    private static final int LINE_HEIGHT = 14;
    private static final int PADDING = 8;
    private static final int BADGE_WIDTH = 32;
    private static final int BADGE_HEIGHT = 12;

    private final String id;
    private final String title;
    private final int accentColor;
    private final List<StatEntry> stats;

    private ModuleSummarySection(String id, String title, int accentColor, List<StatEntry> stats) {
        this.id = id;
        this.title = title;
        this.accentColor = accentColor;
        this.stats = new ArrayList<>(stats);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getLabel() {
        return title;
    }

    @Override
    public int getHeight() {
        return HEADER_HEIGHT + stats.size() * LINE_HEIGHT + PADDING;
    }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font");

        int x = bounds.x();
        int y = bounds.y();
        int w = bounds.width();

        // Header background
        graphics.fill(x, y, x + w, y + HEADER_HEIGHT, UIConstants.Background.HEADER());

        // Accent bar
        graphics.fill(x, y, x + 3, y + HEADER_HEIGHT, accentColor);

        // Title
        String safeTitle = Objects.requireNonNull(title, "title");
        graphics.drawString(font, safeTitle, x + PADDING, y + 6, UIConstants.Text.TITLE(), false);

        // Stats
        int statY = y + HEADER_HEIGHT;
        for (StatEntry stat : stats) {
            // Label
            String label = Objects.requireNonNull(stat.label, "label");
            graphics.drawString(font, label, x + PADDING, statY + 3, UIConstants.Text.SECONDARY(), false);

            // Value (right-aligned)
            String valueStr = stat.format != null ? String.format(stat.format, stat.value) : String.valueOf(stat.value);
            valueStr = Objects.requireNonNull(valueStr, "valueStr");
            int valueWidth = font.width(valueStr);
            int valueX = x + w - PADDING - valueWidth;

            // Source badge if present
            if (stat.source != null) {
                valueX -= BADGE_WIDTH + 4;
                int badgeX = x + w - PADDING - BADGE_WIDTH;
                int badgeY = statY + 2;
                int badgeColor = getBadgeColor(stat.source);
                graphics.fill(badgeX, badgeY, badgeX + BADGE_WIDTH, badgeY + BADGE_HEIGHT, badgeColor);
                String badgeText = stat.source.substring(0, Math.min(3, stat.source.length())).toUpperCase();
                badgeText = Objects.requireNonNull(badgeText, "badgeText");
                int badgeTextX = badgeX + (BADGE_WIDTH - font.width(badgeText)) / 2;
                graphics.drawString(font, badgeText, badgeTextX, badgeY + 2, 0xFFFFFFFF, false);
            }

            // Value with color
            graphics.drawString(font, valueStr, valueX, statY + 3, stat.color, false);

            statY += LINE_HEIGHT;
        }
    }

    private int getBadgeColor(String source) {
        return switch (source.toUpperCase()) {
            case "DEV" -> UIConstants.Accent.BLUE();
            case "NBT" -> UIConstants.Accent.ORANGE();
            case "VAN", "VANILLA" -> 0xFF666666;
            default -> UIConstants.Background.INPUT();
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // STAT ENTRY
    // ═══════════════════════════════════════════════════════════════

    public record StatEntry(String label, double value, String format, int color, String source) {
        public StatEntry(String label, double value) {
            this(label, value, "%.1f", UIConstants.Text.PRIMARY(), null);
        }

        public StatEntry(String label, double value, String format) {
            this(label, value, format, UIConstants.Text.PRIMARY(), null);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════

    public static Builder builder(String id, String title) {
        return new Builder(id, title);
    }

    public static final class Builder {
        private final String id;
        private final String title;
        private int accentColor = UIConstants.Accent.BLUE();
        private final List<StatEntry> stats = new ArrayList<>();

        private Builder(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public Builder accentColor(int color) {
            this.accentColor = color;
            return this;
        }

        public Builder addStat(String label, double value) {
            stats.add(new StatEntry(label, value));
            return this;
        }

        public Builder addStat(String label, double value, String format) {
            stats.add(new StatEntry(label, value, format));
            return this;
        }

        public Builder addStat(String label, double value, String format, int color) {
            stats.add(new StatEntry(label, value, format, color, null));
            return this;
        }

        public Builder addStat(String label, double value, String format, int color, String source) {
            stats.add(new StatEntry(label, value, format, color, source));
            return this;
        }

        public Builder addStat(StatEntry entry) {
            stats.add(entry);
            return this;
        }

        public ModuleSummarySection build() {
            return new ModuleSummarySection(id, title, accentColor, stats);
        }
    }
}
