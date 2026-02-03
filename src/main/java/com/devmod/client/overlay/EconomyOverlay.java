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

import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.telemetry.economy.EconomyMetricsService;

/**
 * Economy tracking overlay for loot and item statistics.
 * Registration is handled by ClientModEvents - do not add @EventBusSubscriber.
 */
public class EconomyOverlay {
    private static final Splitter UNDERSCORE_SPLITTER = Splitter.on('_');

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "economy_overlay");

    // === UI Colors (delegating to OverlayTheme) ===
    private static final int PANEL_BG = OverlayTheme.Economy.BG;
    private static final int PANEL_BG_HEADER = OverlayTheme.Panel.BG_HEADER;
    private static final int PANEL_BORDER = OverlayTheme.Border.GOLD;
    private static final int TEXT_TITLE = OverlayTheme.Text.GOLD;
    private static final int TEXT_WHITE = OverlayTheme.Text.PRIMARY;
    private static final int TEXT_VALUE = OverlayTheme.Text.VALUE_BRIGHT;
    private static final int TEXT_WARNING = OverlayTheme.Text.WARNING_ORANGE;
    private static final int TEXT_DANGER = OverlayTheme.Text.DANGER_BRIGHT;
    private static final int TEXT_MUTED = OverlayTheme.Text.HINT;
    private static final int TEXT_CYAN = OverlayTheme.Text.CYAN;
    private static final int TEXT_PURPLE = OverlayTheme.Text.PURPLE;

    private static final int BAR_BG = OverlayTheme.Progress.BG_ALT;
    private static final int BAR_GREEN = OverlayTheme.Progress.FILL_GREEN;
    private static final int BAR_YELLOW = OverlayTheme.Progress.FILL_YELLOW;
    private static final int BAR_RED = OverlayTheme.Progress.FILL_RED;

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

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            EconomyOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        UIScaleManager.update();
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

        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int sPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        int sLineHeight = UIScaleManager.getScaledLineHeight(font, LINE_HEIGHT);
        int sHeaderHeight = UIScaleManager.scale(HEADER_HEIGHT);
        int sMobEntryHeight = UIScaleManager.scale(MOB_ENTRY_HEIGHT);
        int sMarginRight = UIScaleManager.scale(MARGIN_RIGHT);
        int sMarginTop = UIScaleManager.scale(MARGIN_TOP);

        int panelX = screenWidth - sPanelWidth - sMarginRight;
        int panelY = sMarginTop;

        // If EntityDensityOverlay is active, position below it
        if (EntityDensityOverlay.isEnabled()) {
            panelY += UIScaleManager.scale(120);
        }

        int panelHeight = viewMode == 0
            ? calculateEconomyPanelHeight(sLineHeight)
            : calculateMobPanelHeightSafe(cachedMobStats, sLineHeight);
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        panelX = Math.max(safeLeft, Math.min(panelX, safeRight - sPanelWidth));
        panelY = Math.max(safeTop, Math.min(panelY, safeBottom - panelHeight));

        if (viewMode == 0) {
            renderEconomyPanel(graphics, font, panelX, panelY, sPanelWidth, sPanelPadding, sLineHeight, sHeaderHeight);
        } else {
            renderMobLootPanel(graphics, font, panelX, panelY, sPanelWidth, sPanelPadding, sLineHeight, sHeaderHeight, sMobEntryHeight);
        }
    }

    private static void updateCache() {
        cachedStats = EconomyMetricsService.INSTANCE.getSessionStats();
        cachedTotalKills = EconomyMetricsService.INSTANCE.getTotalMobsKilled();

        // Get all mob stats and sort them
        List<EconomyMetricsService.MobDropSummary> allMobs =
            EconomyMetricsService.INSTANCE.getTopKilledMobs(50);

        final List<EconomyMetricsService.MobDropSummary> mobStats;
        if (allMobs != null && !allMobs.isEmpty()) {
            mobStats = new ArrayList<>(allMobs);
            cachedMobStats = mobStats;
            sortMobStats();
        } else {
            mobStats = new ArrayList<>();
            cachedMobStats = mobStats;
        }

        // Clamp scroll offset
        int maxScroll = Math.max(0, mobStats.size() - MAX_VISIBLE_MOBS);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private static void sortMobStats() {
        final var safeMobStats = cachedMobStats;
        if (safeMobStats == null || safeMobStats.isEmpty()) return;

        switch (sortMode) {
            case 0 -> safeMobStats.sort(Comparator.comparingInt(
                EconomyMetricsService.MobDropSummary::killCount).reversed());
            case 1 -> safeMobStats.sort(Comparator.comparingDouble(
                EconomyMetricsService.MobDropSummary::lootDropPercentage).reversed());
            case 2 -> {} // Already sorted by recent (default order from service)
        }
    }

    // ==================== ECONOMY PANEL ====================

    private static void renderEconomyPanel(GuiGraphics graphics, Font font, int x, int y,
                                           int panelWidth, int panelPadding,
                                           int lineHeight, int headerHeight) {
        var safeFont = Objects.requireNonNull(font);
        int height = calculateEconomyPanelHeight(lineHeight);

        // Background with gradient effect
        graphics.fill(x, y, x + panelWidth, y + headerHeight, PANEL_BG_HEADER);
        graphics.fill(x, y + headerHeight, x + panelWidth, y + height, PANEL_BG);
        drawBorder(graphics, x, y, panelWidth, height, PANEL_BORDER);

        int textX = x + panelPadding;
        int textY = y + 6;

        // Header
        UIScaleManager.drawScaledString(graphics, safeFont, "\u2726 ECONOMY TRACKER", textX, textY, TEXT_TITLE, false);
        textY = y + headerHeight + 4;

        final var safeStats = cachedStats;
        if (safeStats == null) {
            UIScaleManager.drawScaledString(graphics, safeFont, "Loading...", textX, textY, TEXT_MUTED, false);
            return;
        }

        // Session duration with icon
        String duration = Objects.requireNonNull(formatDuration(safeStats.durationMs()));
        UIScaleManager.drawScaledString(graphics, safeFont, "\u23F1 Session: " + duration, textX, textY, TEXT_WHITE, false);
        textY += lineHeight + 6;

        // Stats grid (2 columns)
        int col2X = x + panelWidth / 2 + 4;

        // Row 1: Picked / Dropped
        UIScaleManager.drawScaledString(graphics, safeFont, "\u2191 Picked", textX, textY, TEXT_MUTED, false);
        UIScaleManager.drawScaledString(graphics, safeFont, "\u2193 Dropped", col2X, textY, TEXT_MUTED, false);
        textY += lineHeight;

        UIScaleManager.drawScaledString(graphics, safeFont, String.valueOf(safeStats.itemsPickedUp()), textX + 4, textY, TEXT_VALUE, false);
        UIScaleManager.drawScaledString(graphics, safeFont, String.valueOf(safeStats.itemsDropped()), col2X + 4, textY, TEXT_CYAN, false);
        textY += lineHeight + 4;

        // Row 2: Used / Chests
        UIScaleManager.drawScaledString(graphics, safeFont, "\u2716 Used", textX, textY, TEXT_MUTED, false);
        UIScaleManager.drawScaledString(graphics, safeFont, "\u2610 Chests", col2X, textY, TEXT_MUTED, false);
        textY += lineHeight;

        UIScaleManager.drawScaledString(graphics, safeFont, String.valueOf(safeStats.itemsUsed()), textX + 4, textY, TEXT_WARNING, false);
        UIScaleManager.drawScaledString(graphics, safeFont, String.valueOf(safeStats.chestsOpened()), col2X + 4, textY, TEXT_PURPLE, false);
        textY += lineHeight + 6;

        // Separator
        graphics.fill(x + 8, textY, x + panelWidth - 8, textY + 1, OverlayTheme.Border.divider(TEXT_WHITE));
        textY += 6;

        // Acquisition rate with color coding
        double rate = safeStats.itemsPerMinute();
        String rateStr = String.format("%.1f", rate);
        int rateColor = rate > 10 ? TEXT_VALUE : rate > 5 ? TEXT_WARNING : TEXT_DANGER;
        UIScaleManager.drawScaledString(graphics, safeFont, "Rate: ", textX, textY, TEXT_MUTED, false);
        UIScaleManager.drawScaledString(graphics, safeFont, rateStr + " items/min", textX + UIScaleManager.getScaledStringWidth(safeFont, "Rate: "), textY, rateColor, false);
        textY += lineHeight + 4;

        // Most acquired item
        if (!safeStats.mostAcquiredItem().isEmpty()) {
            UIScaleManager.drawScaledString(graphics, safeFont, "Top Item:", textX, textY, TEXT_MUTED, false);
            textY += lineHeight;
            String itemName = Objects.requireNonNull(formatItemNameFull(safeStats.mostAcquiredItem()));
            if (safeFont.width(itemName) > panelWidth - 40) {
                itemName = truncateString(itemName, safeFont, panelWidth - 40);
            }
            UIScaleManager.drawScaledString(graphics, safeFont, "  " + itemName, textX, textY, TEXT_CYAN, false);
            UIScaleManager.drawScaledString(graphics, safeFont, "x" + safeStats.mostAcquiredCount(),
                x + panelWidth - panelPadding - UIScaleManager.getScaledStringWidth(safeFont, "x" + safeStats.mostAcquiredCount()),
                textY, TEXT_VALUE, false);
            textY += lineHeight + 4;
        }

        // Scarcity warning
        if (!safeStats.mostScarceItem().isEmpty() && safeStats.scarcityIndex() > 0.7) {
            int scarceColor = safeStats.scarcityIndex() > 0.9 ? TEXT_DANGER : TEXT_WARNING;
            UIScaleManager.drawScaledString(graphics, safeFont, "\u26A0 Scarce Resource:", textX, textY, scarceColor, false);
            textY += lineHeight;
            String scarceItem = Objects.requireNonNull(formatItemNameFull(safeStats.mostScarceItem()));
            if (safeFont.width(scarceItem) > panelWidth - 60) {
                scarceItem = truncateString(scarceItem, safeFont, panelWidth - 60);
            }
            UIScaleManager.drawScaledString(graphics, safeFont, "  " + scarceItem, textX, textY, TEXT_WHITE, false);
            String pct = Objects.requireNonNull(String.format("%.0f%%", safeStats.scarcityIndex() * 100));
            UIScaleManager.drawScaledString(graphics, safeFont, pct,
                x + panelWidth - panelPadding - UIScaleManager.getScaledStringWidth(safeFont, pct), textY, scarceColor, false);
        }

        // Footer hint
        int footerY = y + height - lineHeight - 4;
        String hint = "[Shift+F3] Mob Loot";
        UIScaleManager.drawScaledString(graphics, safeFont, hint, x + panelWidth / 2 - UIScaleManager.getScaledStringWidth(safeFont, hint) / 2, footerY, TEXT_MUTED, false);
    }

    // ==================== MOB LOOT PANEL ====================

    private static void renderMobLootPanel(GuiGraphics graphics, Font font, int x, int y,
                                           int panelWidth, int panelPadding,
                                           int lineHeight, int headerHeight,
                                           int mobEntryHeight) {
        var safeFont = Objects.requireNonNull(font);
        final var safeMobStats = cachedMobStats;
        int height = calculateMobPanelHeightSafe(safeMobStats, lineHeight);

        // Background
        graphics.fill(x, y, x + panelWidth, y + headerHeight, PANEL_BG_HEADER);
        graphics.fill(x, y + headerHeight, x + panelWidth, y + height, PANEL_BG);
        drawBorder(graphics, x, y, panelWidth, height, PANEL_BORDER);

        int textX = x + panelPadding;
        int textY = y + 6;

        // Header with sort indicator
        UIScaleManager.drawScaledString(graphics, safeFont, "\u2694 MOB LOOT TRACKER", textX, textY, TEXT_TITLE, false);

        // Sort badge
        String sortBadge = "Sort: " + SORT_NAMES[sortMode];
        int badgeX = x + panelWidth - panelPadding - UIScaleManager.getScaledStringWidth(safeFont, sortBadge);
        UIScaleManager.drawScaledString(graphics, safeFont, sortBadge, badgeX, textY, TEXT_MUTED, false);

        textY = y + headerHeight + 4;

        // Total kills summary
        UIScaleManager.drawScaledString(graphics, safeFont, "Total Kills: ", textX, textY, TEXT_MUTED, false);
        UIScaleManager.drawScaledString(graphics, safeFont, String.valueOf(cachedTotalKills),
            textX + UIScaleManager.getScaledStringWidth(safeFont, "Total Kills: "), textY, TEXT_VALUE, false);

        // Mob count
        int mobCount = safeMobStats != null ? safeMobStats.size() : 0;
        String mobCountStr = mobCount + " types";
        UIScaleManager.drawScaledString(graphics, safeFont, mobCountStr,
            x + panelWidth - panelPadding - UIScaleManager.getScaledStringWidth(safeFont, mobCountStr), textY, TEXT_MUTED, false);
        textY += lineHeight + 4;

        // Separator
        graphics.fill(x + 8, textY, x + panelWidth - 8, textY + 1, OverlayTheme.Border.divider(TEXT_WHITE));
        textY += 6;

        if (safeMobStats == null || safeMobStats.isEmpty()) {
            UIScaleManager.drawScaledString(graphics, safeFont, "No mob kills yet...", textX, textY, TEXT_MUTED, false);
            textY += lineHeight;
            UIScaleManager.drawScaledString(graphics, safeFont, "Kill mobs to track", textX, textY, TEXT_MUTED, false);
            textY += lineHeight;
            UIScaleManager.drawScaledString(graphics, safeFont, "their drop rates!", textX, textY, TEXT_MUTED, false);
        } else {
            // Render visible mobs
            int visibleCount = Math.min(MAX_VISIBLE_MOBS, safeMobStats.size() - scrollOffset);
            for (int i = 0; i < visibleCount; i++) {
                int mobIndex = scrollOffset + i;
                if (mobIndex >= safeMobStats.size()) break;

                EconomyMetricsService.MobDropSummary mob = safeMobStats.get(mobIndex);
                renderMobEntry(graphics, safeFont, x, textY, panelWidth, panelPadding, lineHeight, mobEntryHeight, mob, mobIndex + 1);
                textY += mobEntryHeight;
            }

            // Scroll indicator
            if (safeMobStats.size() > MAX_VISIBLE_MOBS) {
                int scrollBarX = x + panelWidth - 6;
                int scrollAreaHeight = MAX_VISIBLE_MOBS * mobEntryHeight;
                int scrollBarTop = y + headerHeight + lineHeight + 12;

                // Background track
                graphics.fill(scrollBarX, scrollBarTop, scrollBarX + 3,
                    scrollBarTop + scrollAreaHeight, OverlayTheme.Border.divider(TEXT_WHITE));

                // Scroll thumb
                float scrollRatio = (float) scrollOffset / (safeMobStats.size() - MAX_VISIBLE_MOBS);
                int thumbHeight = Math.max(20, scrollAreaHeight * MAX_VISIBLE_MOBS / safeMobStats.size());
                int thumbY = scrollBarTop + (int) ((scrollAreaHeight - thumbHeight) * scrollRatio);
                graphics.fill(scrollBarX, thumbY, scrollBarX + 3, thumbY + thumbHeight, TEXT_TITLE);
            }
        }

        // Footer hints
        int footerY = y + height - lineHeight - 4;
        String hint1 = "[Shift+F3] View";
        String hint2 = "[Ctrl+F3] Sort";

        // Show scroll hint only if there are more mobs than visible
        if (safeMobStats != null && safeMobStats.size() > MAX_VISIBLE_MOBS) {
            String hint3 = "[PgUp/Dn]";
            UIScaleManager.drawScaledString(graphics, safeFont, hint3, textX, footerY, TEXT_MUTED, false);
            int midX = textX + UIScaleManager.getScaledStringWidth(safeFont, hint3) + 6;
            UIScaleManager.drawScaledString(graphics, safeFont, hint1, midX, footerY, TEXT_MUTED, false);
            UIScaleManager.drawScaledString(graphics, safeFont, hint2, x + panelWidth - panelPadding - UIScaleManager.getScaledStringWidth(safeFont, hint2), footerY, TEXT_MUTED, false);
        } else {
            UIScaleManager.drawScaledString(graphics, safeFont, hint1, textX, footerY, TEXT_MUTED, false);
            UIScaleManager.drawScaledString(graphics, safeFont, hint2, x + panelWidth - panelPadding - UIScaleManager.getScaledStringWidth(safeFont, hint2), footerY, TEXT_MUTED, false);
        }
    }

    private static void renderMobEntry(GuiGraphics graphics, Font font, int panelX, int y,
                                        int panelWidth, int panelPadding, int lineHeight, int mobEntryHeight,
                                        EconomyMetricsService.MobDropSummary mob, int rank) {
        var safeFont = Objects.requireNonNull(font);
        int x = panelX + panelPadding;
        int width = panelWidth - panelPadding * 2 - 8; // Account for scrollbar

        // Rank badge
        int rankColor = rank <= 3 ? TEXT_TITLE : TEXT_MUTED;
        UIScaleManager.drawScaledString(graphics, safeFont, "#" + rank, x, y, rankColor, false);

        // Mob name
        String mobName = Objects.requireNonNull(formatMobName(mob.mobType()));
        if (safeFont.width(mobName) > width - 30) {
            mobName = truncateString(mobName, safeFont, width - 30);
        }
        UIScaleManager.drawScaledString(graphics, safeFont, mobName, x + 20, y, TEXT_WHITE, false);
        y += lineHeight;

        // Stats row: kills | drop% | avg
        String killsStr = mob.killCount() + " kills";
        String dropStr = String.format("%.0f%% drop", mob.lootDropPercentage());
        String avgStr = Objects.requireNonNull(String.format("%.1f avg", mob.avgItemsPerKill()));

        int dropColor = mob.lootDropPercentage() > 80 ? TEXT_VALUE :
                        mob.lootDropPercentage() > 50 ? TEXT_WARNING : TEXT_DANGER;

        UIScaleManager.drawScaledString(graphics, safeFont, killsStr, x + 4, y, TEXT_CYAN, false);
        int dropX = x + UIScaleManager.scale(70);
        UIScaleManager.drawScaledString(graphics, safeFont, dropStr, dropX, y, dropColor, false);
        UIScaleManager.drawScaledString(graphics, safeFont, avgStr, x + width - UIScaleManager.getScaledStringWidth(safeFont, avgStr), y, TEXT_MUTED, false);
        y += lineHeight;

        // Progress bar for drop rate
        int barX = x + 4;
        int barWidth = width - 8;
        int barHeight = UIScaleManager.scale(4);
        int barColor = mob.lootDropPercentage() > 80 ? BAR_GREEN :
                       mob.lootDropPercentage() > 50 ? BAR_YELLOW : BAR_RED;

        graphics.fill(barX, y, barX + barWidth, y + barHeight, BAR_BG);
        int fillWidth = (int) (barWidth * Math.min(mob.lootDropPercentage(), 100) / 100.0);
        if (fillWidth > 0) {
            graphics.fill(barX, y, barX + fillWidth, y + barHeight, barColor);
        }
        y += barHeight + UIScaleManager.scale(2);

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
            UIScaleManager.drawScaledString(graphics, safeFont, itemsStr, x + 4, y, TEXT_PURPLE, false);
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

    private static int calculateEconomyPanelHeight(int lineHeight) {
        int headerHeight = UIScaleManager.scale(HEADER_HEIGHT);
        int height = headerHeight;
        height += lineHeight + 6;   // Session
        height += lineHeight * 4 + 8; // Stats grid
        height += 6;                  // Separator
        height += lineHeight + 4;    // Rate
        height += lineHeight * 2 + 4; // Top item
        height += lineHeight * 2 + 4; // Scarcity (optional, but reserve space)
        height += lineHeight + 8;    // Footer
        return height;
    }

    private static int calculateMobPanelHeightSafe(@Nullable List<EconomyMetricsService.MobDropSummary> mobStats,
                                                   int lineHeight) {
        int headerHeight = UIScaleManager.scale(HEADER_HEIGHT);
        int mobEntryHeight = UIScaleManager.scale(MOB_ENTRY_HEIGHT);
        int height = headerHeight;
        height += lineHeight + 4;    // Total kills header
        height += 6;                  // Separator

        int mobCount = mobStats != null ? Math.min(mobStats.size(), MAX_VISIBLE_MOBS) : 0;
        if (mobCount == 0) {
            height += lineHeight * 3 + 8; // "No mob kills" message
        } else {
            height += mobCount * mobEntryHeight + 4;
        }

        height += lineHeight + 8;    // Footer
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
        final var safeMobStats = cachedMobStats;
        if (!enabled || viewMode != 1 || safeMobStats == null) return;
        int maxScroll = Math.max(0, safeMobStats.size() - MAX_VISIBLE_MOBS);
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
