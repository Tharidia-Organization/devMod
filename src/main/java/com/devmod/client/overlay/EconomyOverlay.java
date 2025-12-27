package com.devmod.client.overlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import com.google.common.base.Splitter;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.telemetry.economy.EconomyMetricsService;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class EconomyOverlay {
    private static final Splitter UNDERSCORE_SPLITTER = Splitter.on('_');

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "economy_overlay");

    // === UI Colors ===
    private static final int PANEL_BG = 0xE0101020;           // Dark blue 88% opacity
    private static final int PANEL_BG_HEADER = 0xFF1A1A30;    // Slightly lighter header
    private static final int PANEL_BORDER = 0xFFFFD700;       // Gold
    private static final int TEXT_TITLE = 0xFFFFD700;         // Gold
    private static final int TEXT_WHITE = 0xFFFFFFFF;         // White
    private static final int TEXT_VALUE = 0xFF55FF55;         // Bright green
    private static final int TEXT_WARNING = 0xFFFFAA00;       // Orange
    private static final int TEXT_DANGER = 0xFFFF5555;        // Red
    private static final int TEXT_MUTED = 0xFF888888;         // Gray
    private static final int TEXT_CYAN = 0xFF55FFFF;          // Cyan
    private static final int TEXT_PURPLE = 0xFFAA55FF;        // Purple

    private static final int BAR_BG = 0xFF333344;             // Progress bar background
    private static final int BAR_GREEN = 0xFF44AA44;          // High drop rate
    private static final int BAR_YELLOW = 0xFFAAAA44;         // Medium drop rate
    private static final int BAR_RED = 0xFFAA4444;            // Low drop rate

    // === Dimensions ===
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_PADDING = 8;
    private static final int LINE_HEIGHT = 11;
    private static final int MARGIN_RIGHT = 10;
    private static final int MARGIN_TOP = 140; // Below ImpactHudOverlay (~10 + panelHeight)
    private static final int HEADER_HEIGHT = 24;
    private static final int MOB_ENTRY_HEIGHT = 42;
    private static final int MAX_VISIBLE_MOBS = 5;

    // === State ===
    private static boolean enabled = false;
    private static long lastUpdateMs = 0;
    private static final long UPDATE_INTERVAL_MS = 500; // Update every 0.5s for smoother feel

    // View mode: 0 = economy stats, 1 = mob loot stats
    private static int viewMode = 0;
    private static final int VIEW_MODE_COUNT = 2;

    // Sort mode for mob loot: 0 = by kills, 1 = by drop rate, 2 = by recent
    private static int sortMode = 0;
    private static final int SORT_MODE_COUNT = 3;
    private static final String[] SORT_NAMES = {"Kills", "Drop %", "Recent"};

    // Scroll offset for mob list
    private static int scrollOffset = 0;

    // Cached stats
    @Nullable
    private static EconomyMetricsService.SessionEconomyStats cachedStats = null;
    @Nullable
    private static List<EconomyMetricsService.MobDropSummary> cachedMobStats = null;
    private static int cachedTotalKills = 0;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            EconomyOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        // Update stats periodically
        long now = System.currentTimeMillis();
        if (now - lastUpdateMs > UPDATE_INTERVAL_MS || cachedStats == null) {
            updateCache();
            lastUpdateMs = now;
        }

        Font font = mc.font;
        int screenWidth = graphics.guiWidth();

        int panelX = screenWidth - PANEL_WIDTH - MARGIN_RIGHT;
        int panelY = MARGIN_TOP;

        // If EntityDensityOverlay is active, position below it
        if (EntityDensityOverlay.isEnabled()) {
            panelY += 120;
        }

        if (viewMode == 0) {
            renderEconomyPanel(graphics, font, panelX, panelY);
        } else {
            renderMobLootPanel(graphics, font, panelX, panelY);
        }
    }

    private static void updateCache() {
        cachedStats = EconomyMetricsService.INSTANCE.getSessionStats();
        cachedTotalKills = EconomyMetricsService.INSTANCE.getTotalMobsKilled();

        // Get all mob stats and sort them
        List<EconomyMetricsService.MobDropSummary> allMobs =
            EconomyMetricsService.INSTANCE.getTopKilledMobs(50);

        if (allMobs != null && !allMobs.isEmpty()) {
            cachedMobStats = new ArrayList<>(allMobs);
            sortMobStats();
        } else {
            cachedMobStats = new ArrayList<>();
        }

        // Clamp scroll offset
        int maxScroll = Math.max(0, cachedMobStats.size() - MAX_VISIBLE_MOBS);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private static void sortMobStats() {
        if (cachedMobStats == null || cachedMobStats.isEmpty()) return;

        switch (sortMode) {
            case 0 -> cachedMobStats.sort(Comparator.comparingInt(
                EconomyMetricsService.MobDropSummary::killCount).reversed());
            case 1 -> cachedMobStats.sort(Comparator.comparingDouble(
                EconomyMetricsService.MobDropSummary::lootDropPercentage).reversed());
            case 2 -> {} // Already sorted by recent (default order from service)
        }
    }

    // ==================== ECONOMY PANEL ====================

    private static void renderEconomyPanel(GuiGraphics graphics, Font font, int x, int y) {
        var safeFont = Objects.requireNonNull(font);
        int height = calculateEconomyPanelHeight();

        // Background with gradient effect
        graphics.fill(x, y, x + PANEL_WIDTH, y + HEADER_HEIGHT, PANEL_BG_HEADER);
        graphics.fill(x, y + HEADER_HEIGHT, x + PANEL_WIDTH, y + height, PANEL_BG);
        drawBorder(graphics, x, y, PANEL_WIDTH, height, PANEL_BORDER);

        int textX = x + PANEL_PADDING;
        int textY = y + 6;

        // Header
        graphics.drawString(safeFont, "\u2726 ECONOMY TRACKER", textX, textY, TEXT_TITLE, false);
        textY = y + HEADER_HEIGHT + 4;

        if (cachedStats == null) {
            graphics.drawString(safeFont, "Loading...", textX, textY, TEXT_MUTED, false);
            return;
        }

        // Session duration with icon
        String duration = Objects.requireNonNull(formatDuration(cachedStats.durationMs()));
        graphics.drawString(safeFont, "\u23F1 Session: " + duration, textX, textY, TEXT_WHITE, false);
        textY += LINE_HEIGHT + 6;

        // Stats grid (2 columns)
        int col2X = x + PANEL_WIDTH / 2 + 4;

        // Row 1: Picked / Dropped
        graphics.drawString(safeFont, "\u2191 Picked", textX, textY, TEXT_MUTED, false);
        graphics.drawString(safeFont, "\u2193 Dropped", col2X, textY, TEXT_MUTED, false);
        textY += LINE_HEIGHT;

        graphics.drawString(safeFont, String.valueOf(cachedStats.itemsPickedUp()), textX + 4, textY, TEXT_VALUE, false);
        graphics.drawString(safeFont, String.valueOf(cachedStats.itemsDropped()), col2X + 4, textY, TEXT_CYAN, false);
        textY += LINE_HEIGHT + 4;

        // Row 2: Used / Chests
        graphics.drawString(safeFont, "\u2716 Used", textX, textY, TEXT_MUTED, false);
        graphics.drawString(safeFont, "\u2610 Chests", col2X, textY, TEXT_MUTED, false);
        textY += LINE_HEIGHT;

        graphics.drawString(safeFont, String.valueOf(cachedStats.itemsUsed()), textX + 4, textY, TEXT_WARNING, false);
        graphics.drawString(safeFont, String.valueOf(cachedStats.chestsOpened()), col2X + 4, textY, TEXT_PURPLE, false);
        textY += LINE_HEIGHT + 6;

        // Separator
        graphics.fill(x + 8, textY, x + PANEL_WIDTH - 8, textY + 1, 0x44FFFFFF);
        textY += 6;

        // Acquisition rate with color coding
        double rate = cachedStats.itemsPerMinute();
        String rateStr = String.format("%.1f", rate);
        int rateColor = rate > 10 ? TEXT_VALUE : rate > 5 ? TEXT_WARNING : TEXT_DANGER;
        graphics.drawString(safeFont, "Rate: ", textX, textY, TEXT_MUTED, false);
        graphics.drawString(safeFont, rateStr + " items/min", textX + safeFont.width("Rate: "), textY, rateColor, false);
        textY += LINE_HEIGHT + 4;

        // Most acquired item
        if (!cachedStats.mostAcquiredItem().isEmpty()) {
            graphics.drawString(safeFont, "Top Item:", textX, textY, TEXT_MUTED, false);
            textY += LINE_HEIGHT;
            String itemName = Objects.requireNonNull(formatItemNameFull(cachedStats.mostAcquiredItem()));
            if (safeFont.width(itemName) > PANEL_WIDTH - 40) {
                itemName = truncateString(itemName, safeFont, PANEL_WIDTH - 40);
            }
            graphics.drawString(safeFont, "  " + itemName, textX, textY, TEXT_CYAN, false);
            graphics.drawString(safeFont, "x" + cachedStats.mostAcquiredCount(),
                x + PANEL_WIDTH - PANEL_PADDING - safeFont.width("x" + cachedStats.mostAcquiredCount()),
                textY, TEXT_VALUE, false);
            textY += LINE_HEIGHT + 4;
        }

        // Scarcity warning
        if (!cachedStats.mostScarceItem().isEmpty() && cachedStats.scarcityIndex() > 0.7) {
            int scarceColor = cachedStats.scarcityIndex() > 0.9 ? TEXT_DANGER : TEXT_WARNING;
            graphics.drawString(safeFont, "\u26A0 Scarce Resource:", textX, textY, scarceColor, false);
            textY += LINE_HEIGHT;
            String scarceItem = Objects.requireNonNull(formatItemNameFull(cachedStats.mostScarceItem()));
            if (safeFont.width(scarceItem) > PANEL_WIDTH - 60) {
                scarceItem = truncateString(scarceItem, safeFont, PANEL_WIDTH - 60);
            }
            graphics.drawString(safeFont, "  " + scarceItem, textX, textY, TEXT_WHITE, false);
            String pct = Objects.requireNonNull(String.format("%.0f%%", cachedStats.scarcityIndex() * 100));
            graphics.drawString(safeFont, pct,
                x + PANEL_WIDTH - PANEL_PADDING - safeFont.width(pct), textY, scarceColor, false);
        }

        // Footer hint
        int footerY = y + height - LINE_HEIGHT - 4;
        String hint = "[Shift+F3] Mob Loot";
        graphics.drawString(safeFont, hint, x + PANEL_WIDTH / 2 - safeFont.width(hint) / 2, footerY, TEXT_MUTED, false);
    }

    // ==================== MOB LOOT PANEL ====================

    private static void renderMobLootPanel(GuiGraphics graphics, Font font, int x, int y) {
        var safeFont = Objects.requireNonNull(font);
        int height = calculateMobPanelHeight();

        // Background
        graphics.fill(x, y, x + PANEL_WIDTH, y + HEADER_HEIGHT, PANEL_BG_HEADER);
        graphics.fill(x, y + HEADER_HEIGHT, x + PANEL_WIDTH, y + height, PANEL_BG);
        drawBorder(graphics, x, y, PANEL_WIDTH, height, PANEL_BORDER);

        int textX = x + PANEL_PADDING;
        int textY = y + 6;

        // Header with sort indicator
        graphics.drawString(safeFont, "\u2694 MOB LOOT TRACKER", textX, textY, TEXT_TITLE, false);

        // Sort badge
        String sortBadge = "Sort: " + SORT_NAMES[sortMode];
        int badgeX = x + PANEL_WIDTH - PANEL_PADDING - safeFont.width(sortBadge);
        graphics.drawString(safeFont, sortBadge, badgeX, textY, TEXT_MUTED, false);

        textY = y + HEADER_HEIGHT + 4;

        // Total kills summary
        graphics.drawString(safeFont, "Total Kills: ", textX, textY, TEXT_MUTED, false);
        graphics.drawString(safeFont, String.valueOf(cachedTotalKills),
            textX + safeFont.width("Total Kills: "), textY, TEXT_VALUE, false);

        // Mob count
        int mobCount = cachedMobStats != null ? cachedMobStats.size() : 0;
        String mobCountStr = mobCount + " types";
        graphics.drawString(safeFont, mobCountStr,
            x + PANEL_WIDTH - PANEL_PADDING - safeFont.width(mobCountStr), textY, TEXT_MUTED, false);
        textY += LINE_HEIGHT + 4;

        // Separator
        graphics.fill(x + 8, textY, x + PANEL_WIDTH - 8, textY + 1, 0x44FFFFFF);
        textY += 6;

        if (cachedMobStats == null || cachedMobStats.isEmpty()) {
            graphics.drawString(safeFont, "No mob kills yet...", textX, textY, TEXT_MUTED, false);
            textY += LINE_HEIGHT;
            graphics.drawString(safeFont, "Kill mobs to track", textX, textY, TEXT_MUTED, false);
            textY += LINE_HEIGHT;
            graphics.drawString(safeFont, "their drop rates!", textX, textY, TEXT_MUTED, false);
        } else {
            // Render visible mobs
            int visibleCount = Math.min(MAX_VISIBLE_MOBS, cachedMobStats.size() - scrollOffset);
            for (int i = 0; i < visibleCount; i++) {
                int mobIndex = scrollOffset + i;
                if (mobIndex >= cachedMobStats.size()) break;

                EconomyMetricsService.MobDropSummary mob = cachedMobStats.get(mobIndex);
                renderMobEntry(graphics, safeFont, x, textY, mob, mobIndex + 1);
                textY += MOB_ENTRY_HEIGHT;
            }

            // Scroll indicator
            if (cachedMobStats.size() > MAX_VISIBLE_MOBS) {
                int scrollBarX = x + PANEL_WIDTH - 6;
                int scrollAreaHeight = MAX_VISIBLE_MOBS * MOB_ENTRY_HEIGHT;
                int scrollBarTop = y + HEADER_HEIGHT + LINE_HEIGHT + 12;

                // Background track
                graphics.fill(scrollBarX, scrollBarTop, scrollBarX + 3,
                    scrollBarTop + scrollAreaHeight, 0x44FFFFFF);

                // Scroll thumb
                float scrollRatio = (float) scrollOffset / (cachedMobStats.size() - MAX_VISIBLE_MOBS);
                int thumbHeight = Math.max(20, scrollAreaHeight * MAX_VISIBLE_MOBS / cachedMobStats.size());
                int thumbY = scrollBarTop + (int) ((scrollAreaHeight - thumbHeight) * scrollRatio);
                graphics.fill(scrollBarX, thumbY, scrollBarX + 3, thumbY + thumbHeight, TEXT_TITLE);
            }
        }

        // Footer hints
        int footerY = y + height - LINE_HEIGHT - 4;
        String hint1 = "[Shift+F3] View";
        String hint2 = "[Ctrl+F3] Sort";

        // Show scroll hint only if there are more mobs than visible
        if (cachedMobStats != null && cachedMobStats.size() > MAX_VISIBLE_MOBS) {
            String hint3 = "[PgUp/Dn]";
            graphics.drawString(safeFont, hint3, textX, footerY, TEXT_MUTED, false);
            int midX = textX + safeFont.width(hint3) + 6;
            graphics.drawString(safeFont, hint1, midX, footerY, TEXT_MUTED, false);
            graphics.drawString(safeFont, hint2, x + PANEL_WIDTH - PANEL_PADDING - safeFont.width(hint2), footerY, TEXT_MUTED, false);
        } else {
            graphics.drawString(safeFont, hint1, textX, footerY, TEXT_MUTED, false);
            graphics.drawString(safeFont, hint2, x + PANEL_WIDTH - PANEL_PADDING - safeFont.width(hint2), footerY, TEXT_MUTED, false);
        }
    }

    private static void renderMobEntry(GuiGraphics graphics, Font font, int panelX, int y,
                                        EconomyMetricsService.MobDropSummary mob, int rank) {
        var safeFont = Objects.requireNonNull(font);
        int x = panelX + PANEL_PADDING;
        int width = PANEL_WIDTH - PANEL_PADDING * 2 - 8; // Account for scrollbar

        // Rank badge
        int rankColor = rank <= 3 ? TEXT_TITLE : TEXT_MUTED;
        graphics.drawString(safeFont, "#" + rank, x, y, rankColor, false);

        // Mob name
        String mobName = Objects.requireNonNull(formatMobName(mob.mobType()));
        if (safeFont.width(mobName) > width - 30) {
            mobName = truncateString(mobName, safeFont, width - 30);
        }
        graphics.drawString(safeFont, mobName, x + 20, y, TEXT_WHITE, false);
        y += LINE_HEIGHT;

        // Stats row: kills | drop% | avg
        String killsStr = mob.killCount() + " kills";
        String dropStr = String.format("%.0f%% drop", mob.lootDropPercentage());
        String avgStr = Objects.requireNonNull(String.format("%.1f avg", mob.avgItemsPerKill()));

        int dropColor = mob.lootDropPercentage() > 80 ? TEXT_VALUE :
                        mob.lootDropPercentage() > 50 ? TEXT_WARNING : TEXT_DANGER;

        graphics.drawString(safeFont, killsStr, x + 4, y, TEXT_CYAN, false);
        int dropX = x + 70;
        graphics.drawString(safeFont, dropStr, dropX, y, dropColor, false);
        graphics.drawString(safeFont, avgStr, x + width - safeFont.width(avgStr), y, TEXT_MUTED, false);
        y += LINE_HEIGHT;

        // Progress bar for drop rate
        int barX = x + 4;
        int barWidth = width - 8;
        int barHeight = 4;
        int barColor = mob.lootDropPercentage() > 80 ? BAR_GREEN :
                       mob.lootDropPercentage() > 50 ? BAR_YELLOW : BAR_RED;

        graphics.fill(barX, y, barX + barWidth, y + barHeight, BAR_BG);
        int fillWidth = (int) (barWidth * Math.min(mob.lootDropPercentage(), 100) / 100.0);
        if (fillWidth > 0) {
            graphics.fill(barX, y, barX + fillWidth, y + barHeight, barColor);
        }
        y += barHeight + 2;

        // Top items dropped
        List<EconomyMetricsService.ItemDropInfo> items = mob.itemDrops();
        if (items != null && !items.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (EconomyMetricsService.ItemDropInfo item : items) {
                if (shown >= 3) break;
                if (shown > 0) sb.append(" | ");
                String itemName = formatItemNameShort(item.itemId());
                sb.append(String.format("%s %.0f%%", itemName, item.dropRate()));
                shown++;
            }
            String itemsStr = Objects.requireNonNull(sb.toString());
            if (safeFont.width(itemsStr) > width - 4) {
                itemsStr = truncateString(itemsStr, safeFont, width - 4);
            }
            graphics.drawString(safeFont, itemsStr, x + 4, y, TEXT_PURPLE, false);
        }
    }

    // ==================== UTILITY METHODS ====================

    private static String formatMobName(String mobType) {
        String name = mobType;
        // Handle "entity.minecraft.zombie" format
        if (name.contains(".")) {
            int lastDot = name.lastIndexOf('.');
            name = lastDot >= 0 ? name.substring(lastDot + 1) : name;
        }
        // Handle "minecraft:zombie" format
        if (name.contains(":")) {
            name = name.substring(name.lastIndexOf(':') + 1);
        }
        // Convert snake_case to Title Case
        if (name.contains("_")) {
            StringBuilder sb = new StringBuilder();
            for (String part : UNDERSCORE_SPLITTER.split(name)) {
                if (!part.isEmpty()) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) sb.append(part.substring(1));
                }
            }
            name = sb.toString();
        } else if (!name.isEmpty()) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    private static String formatItemNameFull(String itemId) {
        String name = itemId;
        if (name.contains(":")) {
            name = name.substring(name.lastIndexOf(':') + 1);
        }
        if (name.contains("_")) {
            StringBuilder sb = new StringBuilder();
            for (String part : UNDERSCORE_SPLITTER.split(name)) {
                if (!part.isEmpty()) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) sb.append(part.substring(1));
                }
            }
            return sb.toString();
        }
        if (!name.isEmpty()) {
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    private static String formatItemNameShort(String itemId) {
        String name = itemId;
        if (name.contains(":")) {
            name = name.substring(name.lastIndexOf(':') + 1);
        }
        // Just capitalize first letter, keep short
        int underscoreIndex = name.indexOf('_');
        if (underscoreIndex >= 0) {
            name = name.substring(0, underscoreIndex);
        }
        if (name.length() > 8) {
            name = name.substring(0, 7) + ".";
        }
        if (!name.isEmpty()) {
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    private static String truncateString(String str, Font font, int maxWidth) {
        if (font.width(Objects.requireNonNull(str)) <= maxWidth) return str;
        String ellipsis = "...";
        int minChars = Math.min(6, str.length()); // Keep at least 6 chars for readability
        while (font.width(Objects.requireNonNull(str + ellipsis)) > maxWidth && str.length() > minChars) {
            str = str.substring(0, str.length() - 1);
        }
        return str + ellipsis;
    }

    private static String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %02dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %02ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private static int calculateEconomyPanelHeight() {
        int height = HEADER_HEIGHT;
        height += LINE_HEIGHT + 6;   // Session
        height += LINE_HEIGHT * 4 + 8; // Stats grid
        height += 6;                  // Separator
        height += LINE_HEIGHT + 4;    // Rate
        height += LINE_HEIGHT * 2 + 4; // Top item
        height += LINE_HEIGHT * 2 + 4; // Scarcity (optional, but reserve space)
        height += LINE_HEIGHT + 8;    // Footer
        return height;
    }

    private static int calculateMobPanelHeight() {
        int height = HEADER_HEIGHT;
        height += LINE_HEIGHT + 4;    // Total kills header
        height += 6;                  // Separator

        int mobCount = cachedMobStats != null ? Math.min(cachedMobStats.size(), MAX_VISIBLE_MOBS) : 0;
        if (mobCount == 0) {
            height += LINE_HEIGHT * 3 + 8; // "No mob kills" message
        } else {
            height += mobCount * MOB_ENTRY_HEIGHT + 4;
        }

        height += LINE_HEIGHT + 8;    // Footer
        return height;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);                    // Top
        graphics.fill(x, y + height - 1, x + width, y + height, color);  // Bottom
        graphics.fill(x, y, x + 1, y + height, color);                   // Left
        graphics.fill(x + width - 1, y, x + width, y + height, color);   // Right
    }

    // ==================== PUBLIC API ====================

    public static void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            cachedStats = null;
            cachedMobStats = null;
            lastUpdateMs = 0;
            scrollOffset = 0;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        setEnabled(!enabled);
    }

    /**
     * Cycle through view modes (economy stats <-> mob loot stats).
     */
    public static void cycleView() {
        if (!enabled) return;
        viewMode = (viewMode + 1) % VIEW_MODE_COUNT;
        scrollOffset = 0; // Reset scroll when changing view
    }

    /**
     * Cycle through sort modes (kills, drop rate, recent).
     * Only affects mob loot view.
     */
    public static void cycleSortMode() {
        if (!enabled || viewMode != 1) return;
        sortMode = (sortMode + 1) % SORT_MODE_COUNT;
        if (cachedMobStats != null) {
            sortMobStats();
        }
        scrollOffset = 0;
    }

    /**
     * Scroll the mob list up.
     */
    public static void scrollUp() {
        if (!enabled || viewMode != 1) return;
        scrollOffset = Math.max(0, scrollOffset - 1);
    }

    /**
     * Scroll the mob list down.
     */
    public static void scrollDown() {
        if (!enabled || viewMode != 1 || cachedMobStats == null) return;
        int maxScroll = Math.max(0, cachedMobStats.size() - MAX_VISIBLE_MOBS);
        scrollOffset = Math.min(maxScroll, scrollOffset + 1);
    }

    /**
     * Get current view mode name.
     */
    public static String getViewModeName() {
        return viewMode == 0 ? "Economy" : "Mob Loot";
    }

    /**
     * Get current sort mode name.
     */
    public static String getSortModeName() {
        return SORT_NAMES[sortMode];
    }
}
