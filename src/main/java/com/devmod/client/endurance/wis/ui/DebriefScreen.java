package com.devmod.client.endurance.wis.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.endurance.ClientPartyStatsCache;
import com.devmod.client.endurance.wis.CombatEvent;
import com.devmod.client.endurance.wis.WaveIntelligenceManager;
import com.devmod.client.endurance.wis.WaveTelemetryCollector;
import com.devmod.client.input.KeyInputHandler;
import com.devmod.client.ui.BaseDevModScreen;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.PartyWaveStats;
import com.devmod.util.I18n;

/**
 * Post-wave debrief screen with detailed analytics.
 *
 * Tabs:
 * 1. Overview - Summary stats, grade, comparison
 * 2. Combat - Per-mob breakdown, weapon stats, damage charts
 * 3. Spatial - Heatmaps, movement patterns, death locations
 * 4. Timeline - Event timeline, combo history
 * 5. Report - Generate ticket with wave data
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings({"UnusedVariable", "NullAway", "JavaTimeDefaultTimeZone"}) // Layout params reserved; fields init'd in initContent(); client timestamp is fine
public class DebriefScreen extends BaseDevModScreen {

    private static final String SCREEN_ID = "wave_debrief";

    // Layout
    private static final int TAB_HEIGHT = 32;
    private static final int CONTENT_PADDING = 16;

    // Colors
    private static final int COLOR_BG = DesignTokens.Bg.LEVEL_0;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_ACCENT = DesignTokens.Accent.PRIMARY;
    private static final int COLOR_SUCCESS = DesignTokens.Semantic.SUCCESS;
    private static final int COLOR_WARNING = DesignTokens.Semantic.WARNING;
    private static final int COLOR_ERROR = DesignTokens.Semantic.ERROR;

    // Tabs
    private enum Tab {
        OVERVIEW("Overview", "O"),
        COMBAT("Combat", "C"),
        SPATIAL("Spatial", "S"),
        TIMELINE("Timeline", "T"),
        PARTY("Party", "P"),
        REPORT("Report", "R");

        final String label;
        final String icon;

        Tab(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private Tab activeTab = Tab.OVERVIEW;
    private final WaveTelemetryCollector collector;
    private final int waveNumber;

    // UI components - initialized in initContent(), never null after init
    private final List<Button> tabButtons = new ArrayList<>();
    private Button continueButton;
    private Button submitTicketButton;
    // Timeline navigation buttons (UX: visual controls for accessibility)
    private Button timelinePrevButton;
    private Button timelineNextButton;

    // Scroll state for content
    private int scrollOffset = 0;
    private int maxScrollOffset = 0; // For scroll indicator (Fix #3)

    // Timeline pagination (Fix #9)
    private static final int TIMELINE_PAGE_SIZE = 20;
    private int timelinePage = 0;

    // Grade component caps (single source of truth for UI and calculation)
    private static final float CAP_DPS = 30f;
    private static final float CAP_NO_DAMAGE = 20f;
    private static final float CAP_COMBO = 20f;
    private static final float CAP_TTK = 20f;
    private static final float CAP_CRIT = 10f;
    private static final float CAP_TOTAL = CAP_DPS + CAP_NO_DAMAGE + CAP_COMBO + CAP_TTK + CAP_CRIT;

    // Cached grade breakdown (Fix #3: avoid recalculating every frame)
    @Nullable
    private GradeBreakdown cachedGradeBreakdown;
    // FIX AUDIT #9: Track event count for cache invalidation
    private int cachedEventCount = -1;

    // Rate limiting for ticket submission (Fix #5)
    private boolean ticketSubmitted = false;

    // Gson for proper JSON serialization (Fix #8)
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public DebriefScreen(WaveTelemetryCollector collector) {
        super(I18n.screenTitle("wave_debrief"), SCREEN_ID, "endurance");
        this.collector = collector;
        this.waveNumber = collector.getWaveNumber();
    }

    @Override
    protected void initContent() {
        int centerX = this.width / 2;
        int bottomY = this.height - 40;

        // Tab buttons
        tabButtons.clear();
        int tabWidth = 80;
        int tabSpacing = 4;
        int totalTabWidth = Tab.values().length * tabWidth + (Tab.values().length - 1) * tabSpacing;
        int tabStartX = (this.width - totalTabWidth) / 2;

        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
            int tabX = tabStartX + i * (tabWidth + tabSpacing);
            Button tabBtn = Button.builder(
                Objects.requireNonNull(Component.literal(tab.icon + " " + tab.label)),
                btn -> {
                    activeTab = tab;
                    scrollOffset = 0;
                    updateButtonVisibility();
                })
                .bounds(tabX, 50, tabWidth, TAB_HEIGHT)
                .build();
            tabButtons.add(tabBtn);
            addRenderableWidget(Objects.requireNonNull(tabBtn));
        }

        // Continue button
        continueButton = Button.builder(
            Objects.requireNonNull(Component.literal("Continue")),
            btn -> {
                WaveIntelligenceManager.INSTANCE.skipDebrief();
                onClose();
            })
            .bounds(centerX - 60, bottomY, 120, 24)
            .build();
        addRenderableWidget(Objects.requireNonNull(continueButton));

        // Submit ticket button (only in Report tab)
        submitTicketButton = Button.builder(
            Objects.requireNonNull(Component.literal("Submit Ticket")),
            btn -> submitTicket())
            .bounds(centerX - 80, bottomY - 30, 160, 24)
            .build();
        submitTicketButton.visible = false;
        addRenderableWidget(Objects.requireNonNull(submitTicketButton));

        // Timeline navigation buttons (UX: accessible visual controls)
        // UX Q3: Position adjacent with small gap (no 140px hole)
        int timelineNavY = this.height - 115;
        int buttonWidth = 60;
        int buttonGap = 10;
        timelinePrevButton = Button.builder(
            Objects.requireNonNull(Component.literal("◀ Prev")),
            btn -> {
                if (timelinePage > 0) {
                    timelinePage--;
                    updateTimelineButtonState(); // UX Q8: Update enabled state
                }
            })
            .bounds(centerX - buttonWidth - buttonGap / 2, timelineNavY, buttonWidth, 20)
            .build();
        timelinePrevButton.visible = false;
        addRenderableWidget(Objects.requireNonNull(timelinePrevButton));

        timelineNextButton = Button.builder(
            Objects.requireNonNull(Component.literal("Next ▶")),
            btn -> {
                List<CombatEvent> events = collector.getEvents();
                int totalPages = Math.max(1, (events.size() + TIMELINE_PAGE_SIZE - 1) / TIMELINE_PAGE_SIZE);
                if (timelinePage < totalPages - 1) {
                    timelinePage++;
                    updateTimelineButtonState(); // UX Q8: Update enabled state
                }
            })
            .bounds(centerX + buttonGap / 2, timelineNavY, buttonWidth, 20)
            .build();
        timelineNextButton.visible = false;
        addRenderableWidget(Objects.requireNonNull(timelineNextButton));

        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        if (submitTicketButton != null) {
            submitTicketButton.visible = activeTab == Tab.REPORT;
            // Fix #5: Disable button after submission
            submitTicketButton.active = !ticketSubmitted;
        }
        // UX: Show timeline navigation buttons only on Timeline tab
        boolean showTimelineNav = activeTab == Tab.TIMELINE;
        if (timelinePrevButton != null) {
            timelinePrevButton.visible = showTimelineNav;
        }
        if (timelineNextButton != null) {
            timelineNextButton.visible = showTimelineNav;
        }
        // UX Q8: Update timeline button enabled state based on current page
        updateTimelineButtonState();
        // Fix #7: Reset maxScrollOffset when switching tabs
        maxScrollOffset = 0;
        scrollOffset = 0;
    }

    /**
     * UX Q8: Disable prev/next buttons at boundaries to prevent silent no-ops.
     * FIX AUDIT #4: Clamp timelinePage when events list shrinks to prevent out-of-bounds.
     */
    private void updateTimelineButtonState() {
        if (timelinePrevButton == null || timelineNextButton == null) {
            return;
        }
        List<CombatEvent> events = collector.getEvents();
        int totalPages = Math.max(1, (events.size() + TIMELINE_PAGE_SIZE - 1) / TIMELINE_PAGE_SIZE);

        // FIX AUDIT #4: Clamp page if events were cleared or reduced
        if (timelinePage >= totalPages) {
            timelinePage = Math.max(0, totalPages - 1);
        }

        // Disable Prev at first page, Next at last page
        timelinePrevButton.active = timelinePage > 0;
        timelineNextButton.active = timelinePage < totalPages - 1;
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(0, 0, this.width, this.height, COLOR_BG);

        // Header
        renderHeader(graphics);

        // Tab bar highlight
        renderTabHighlight(graphics);

        // Content area
        int contentX = CONTENT_PADDING;
        int contentY = 50 + TAB_HEIGHT + CONTENT_PADDING;
        int contentWidth = this.width - CONTENT_PADDING * 2;
        int contentHeight = this.height - contentY - 80;

        // Content panel background
        graphics.fill(contentX - 2, contentY - 2,
            contentX + contentWidth + 2, contentY + contentHeight + 2,
            DesignTokens.Surface.LEVEL_0);

        // Render active tab content
        switch (activeTab) {
            case OVERVIEW -> renderOverviewTab(graphics, contentX, contentY, contentWidth, contentHeight);
            case COMBAT -> renderCombatTab(graphics, contentX, contentY, contentWidth, contentHeight);
            case SPATIAL -> renderSpatialTab(graphics, contentX, contentY, contentWidth, contentHeight);
            case TIMELINE -> renderTimelineTab(graphics, contentX, contentY, contentWidth, contentHeight);
            case PARTY -> renderPartyTab(graphics, contentX, contentY, contentWidth, contentHeight);
            case REPORT -> renderReportTab(graphics, contentX, contentY, contentWidth, contentHeight);
        }

        // Fix #3: Render scroll indicator if content overflows
        if (maxScrollOffset > 0) {
            renderScrollIndicator(graphics, contentX + contentWidth - 8, contentY, 6, contentHeight);
        }
    }

    /**
     * Fix #3: Render a scroll indicator bar on the right side of content.
     * UX: Also shows percentage text for orientation.
     */
    private void renderScrollIndicator(GuiGraphics graphics, int x, int y, int width, int height) {
        // Track background
        graphics.fill(x, y, x + width, y + height, DesignTokens.Surface.LEVEL_1);

        // Calculate thumb size and position
        float viewRatio = (float) height / (height + maxScrollOffset);
        int thumbHeight = Math.max(20, (int)(height * viewRatio));
        float scrollRatio = maxScrollOffset > 0 ? (float) scrollOffset / maxScrollOffset : 0f;
        int thumbY = y + (int)((height - thumbHeight) * scrollRatio);

        // Thumb
        graphics.fill(x + 1, thumbY, x + width - 1, thumbY + thumbHeight, COLOR_ACCENT);

        // UX: Show scroll percentage text for orientation
        int percent = (int)(scrollRatio * 100);
        String percentText = percent + "%";
        Font font = Minecraft.getInstance().font;
        int textX = x - font.width(percentText) - 4;
        int textY = thumbY + (thumbHeight - font.lineHeight) / 2;
        graphics.drawString(font, percentText, textX, textY, COLOR_TEXT_DIM, false);
    }

    private void renderHeader(GuiGraphics graphics) {
        Font font = Minecraft.getInstance().font;
        String title = Objects.requireNonNull(String.format("Wave %d Debrief", waveNumber));
        int titleWidth = font.width(title);
        graphics.drawString(font, title, (this.width - titleWidth) / 2, 20, COLOR_ACCENT, true);
    }

    private void renderTabHighlight(GuiGraphics graphics) {
        int tabWidth = 80;
        int tabSpacing = 4;
        int totalTabWidth = Tab.values().length * tabWidth + (Tab.values().length - 1) * tabSpacing;
        int tabStartX = (this.width - totalTabWidth) / 2;

        int activeIndex = activeTab.ordinal();
        int activeX = tabStartX + activeIndex * (tabWidth + tabSpacing);
        graphics.fill(activeX, 50 + TAB_HEIGHT - 2, activeX + tabWidth, 50 + TAB_HEIGHT, COLOR_ACCENT);
    }

    // ========== Tab Content ==========

    private void renderOverviewTab(GuiGraphics graphics, int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 4;
        int col1X = x + 20;
        int col2X = x + w / 2 + 20;

        // Grade with breakdown (Fix #3: use cached breakdown)
        GradeBreakdown breakdown = getOrCalculateGradeBreakdown();
        int gradeColor = getGradeColor(breakdown.grade);
        graphics.drawString(font, "GRADE", col1X, y, COLOR_TEXT_DIM, false);
        graphics.pose().pushPose();
        graphics.pose().translate(col1X, y + lineHeight, 0);
        graphics.pose().scale(3f, 3f, 1f);
        graphics.drawString(font, breakdown.grade, 0, 0, gradeColor, true);
        graphics.pose().popPose();

        // Fix #7: Show grade breakdown next to grade with caps for transparency
        // UX Q2: Use constants for single source of truth
        int breakdownX = col1X + 60;
        int breakdownY = y + lineHeight;
        graphics.drawString(font, String.format("DPS: +%.0f/%.0f", breakdown.dpsScore, CAP_DPS), breakdownX, breakdownY, COLOR_TEXT_DIM, false);
        breakdownY += font.lineHeight;
        if (breakdown.noDamageBonus > 0) {
            graphics.drawString(font, String.format("No DMG: +%.0f/%.0f", breakdown.noDamageBonus, CAP_NO_DAMAGE), breakdownX, breakdownY, COLOR_SUCCESS, false);
            breakdownY += font.lineHeight;
        }
        graphics.drawString(font, String.format("Combo: +%.0f/%.0f", breakdown.comboScore, CAP_COMBO), breakdownX, breakdownY, COLOR_TEXT_DIM, false);
        breakdownY += font.lineHeight;
        graphics.drawString(font, String.format("TTK: +%.0f/%.0f", breakdown.ttkScore, CAP_TTK), breakdownX, breakdownY, COLOR_TEXT_DIM, false);
        breakdownY += font.lineHeight;
        graphics.drawString(font, String.format("Crit: +%.0f/%.0f", breakdown.critScore, CAP_CRIT), breakdownX, breakdownY, COLOR_TEXT_DIM, false);
        breakdownY += font.lineHeight;
        graphics.drawString(font, String.format("Total: %.0f/%.0f", breakdown.totalScore, CAP_TOTAL), breakdownX, breakdownY, COLOR_ACCENT, false);

        // Stats column 1
        int statsY = y + 80;
        renderStatLine(graphics, font, col1X, statsY, "Duration",
            formatDuration(collector.getWaveDurationMs()));
        statsY += lineHeight;
        renderStatLine(graphics, font, col1X, statsY, "Kills",
            String.valueOf(collector.getTotalKills()));
        statsY += lineHeight;
        renderStatLine(graphics, font, col1X, statsY, "Elite Kills",
            String.valueOf(collector.getEliteKills()));
        statsY += lineHeight;
        renderStatLine(graphics, font, col1X, statsY, "Max Combo",
            collector.getMaxCombo() + "x");

        // Stats column 2
        statsY = y + 80;
        renderStatLine(graphics, font, col2X, statsY, "DPS",
            String.format("%.1f", collector.getDPS()));
        statsY += lineHeight;
        renderStatLine(graphics, font, col2X, statsY, "Damage Dealt",
            String.format("%.0f", collector.getTotalDamageDealt()));
        statsY += lineHeight;
        renderStatLine(graphics, font, col2X, statsY, "Damage Taken",
            String.format("%.0f", collector.getTotalDamageTaken()));
        statsY += lineHeight;
        renderStatLine(graphics, font, col2X, statsY, "Avg TTK",
            String.format("%.0fms", collector.getAverageTTK()));

        // Achievements
        int achY = statsY + 30;
        graphics.drawString(font, "ACHIEVEMENTS", col1X, achY, COLOR_ACCENT, false);
        achY += lineHeight;

        // UX: Use ✓ symbol for colorblind accessibility (not just green color)
        if (collector.wasNoDamageWave()) {
            graphics.drawString(font, "✓ No Damage!", col1X, achY, COLOR_SUCCESS, false);
            achY += lineHeight;
        }
        if (collector.getMaxCombo() >= 50) {
            graphics.drawString(font, "✓ Combo Master (50+)", col1X, achY, COLOR_SUCCESS, false);
            achY += lineHeight;
        }
        if (collector.getCriticalHitRate() > 0.5f) {
            graphics.drawString(font, "✓ Precision (>50% crit)", col1X, achY, COLOR_SUCCESS, false);
        }

        // Comparison with previous wave
        WaveTelemetryCollector prev = WaveIntelligenceManager.INSTANCE.getPreviousWaveCollector();
        if (prev != null) {
            int compY = y + h - 60;
            graphics.drawString(font, "vs Previous Wave:", col1X, compY, COLOR_TEXT_DIM, false);
            compY += lineHeight;

            // UX: Use ▲/▼ symbols for colorblind accessibility
            float dpsDiff = collector.getDPS() - prev.getDPS();
            String dpsSymbol = dpsDiff >= 0 ? "▲" : "▼";
            String dpsChange = dpsDiff >= 0 ? "+" + String.format("%.1f", dpsDiff) : String.format("%.1f", dpsDiff);
            int dpsColor = dpsDiff >= 0 ? COLOR_SUCCESS : COLOR_ERROR;
            graphics.drawString(font, "DPS: " + dpsSymbol + dpsChange, col1X + 20, compY, dpsColor, false);

            float ttkDiff = prev.getAverageTTK() - collector.getAverageTTK(); // Lower is better
            String ttkSymbol = ttkDiff >= 0 ? "▲" : "▼";
            String ttkChange = ttkDiff >= 0 ? "-" + String.format("%.0f", ttkDiff) + "ms" :
                               "+" + String.format("%.0f", -ttkDiff) + "ms";
            int ttkColor = ttkDiff >= 0 ? COLOR_SUCCESS : COLOR_ERROR;
            graphics.drawString(font, "TTK: " + ttkSymbol + ttkChange, col2X, compY, ttkColor, false);
        }
    }

    private void renderCombatTab(GuiGraphics graphics, int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 4;

        // Mob breakdown
        graphics.drawString(font, "MOB BREAKDOWN", x + 10, y, COLOR_ACCENT, false);
        y += lineHeight + 8;

        Map<ResourceLocation, WaveTelemetryCollector.MobTypeStats> mobStats = collector.getMobStats();
        List<Map.Entry<ResourceLocation, WaveTelemetryCollector.MobTypeStats>> sorted =
            new ArrayList<>(mobStats.entrySet());
        sorted.sort(Comparator.comparingInt(e -> -e.getValue().kills));

        int shown = 0;
        for (var entry : sorted) {
            if (shown >= 8) break;
            String mobName = entry.getKey().getPath();
            WaveTelemetryCollector.MobTypeStats stats = entry.getValue();

            String line = String.format("%-20s  K:%d  DMG:%.0f  TTK:%.0fms",
                mobName, stats.kills, stats.totalDamageDealt, stats.getAverageTimeToKill());
            graphics.drawString(font, line, x + 10, y, COLOR_TEXT, false);
            y += lineHeight;
            shown++;
        }

        // Weapon stats
        y += 20;
        graphics.drawString(font, "WEAPON STATS", x + 10, y, COLOR_ACCENT, false);
        y += lineHeight + 8;

        Map<String, WaveTelemetryCollector.WeaponStats> weaponStats = collector.getWeaponStats();
        for (var entry : weaponStats.entrySet()) {
            String weapon = entry.getKey();
            WaveTelemetryCollector.WeaponStats stats = entry.getValue();

            String line = String.format("%-20s  K:%d  DMG:%.0f  Crit:%.0f%%",
                weapon, stats.kills, stats.totalDamage, stats.getCriticalRate() * 100);
            graphics.drawString(font, line, x + 10, y, COLOR_TEXT, false);
            y += lineHeight;
        }
    }

    private void renderSpatialTab(GuiGraphics graphics, int x, int y, int w, int h) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font);

        graphics.drawString(font, "SPATIAL ANALYSIS", x + 10, y, COLOR_ACCENT, false);
        y += font.lineHeight + 10;

        graphics.drawString(font, "Position Samples: " + collector.getPlayerPositions().size(),
            x + 10, y, COLOR_TEXT, false);
        y += font.lineHeight + 4;

        graphics.drawString(font, "Kill Locations: " + collector.getKillPositions().size(),
            x + 10, y, COLOR_TEXT, false);
        y += font.lineHeight + 4;

        graphics.drawString(font, "Damage Locations: " + collector.getDamagePositions().size(),
            x + 10, y, COLOR_TEXT, false);
        y += font.lineHeight + 4;

        // UX: Use ✓/✗ symbols for colorblind accessibility
        boolean noDeath = collector.getDeathPositions().isEmpty();
        String deathSymbol = noDeath ? "✓" : "✗";
        graphics.drawString(font, deathSymbol + " Death Locations: " + collector.getDeathPositions().size(),
            x + 10, y, noDeath ? COLOR_SUCCESS : COLOR_ERROR, false);

        y += 30;
        graphics.drawString(font, "(Full heatmap visualization available in ticket report)",
            x + 10, y, COLOR_TEXT_DIM, false);
    }

    private void renderTimelineTab(GuiGraphics graphics, int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 2;

        List<CombatEvent> events = collector.getEvents();
        int totalPages = Math.max(1, (events.size() + TIMELINE_PAGE_SIZE - 1) / TIMELINE_PAGE_SIZE);
        timelinePage = Math.min(timelinePage, totalPages - 1);

        // Fix #9: Header with pagination info
        String header = String.format("EVENT TIMELINE (Page %d/%d) - %d events",
            timelinePage + 1, totalPages, events.size());
        graphics.drawString(font, header, x + 10, y, COLOR_ACCENT, false);
        y += font.lineHeight + 4;

        // Pagination controls hint
        graphics.drawString(font, "[Scroll or ←/→ to navigate pages]", x + 10, y, COLOR_TEXT_DIM, false);
        y += font.lineHeight + 8;

        // Calculate page bounds
        int startIdx = timelinePage * TIMELINE_PAGE_SIZE;
        int endIdx = Math.min(startIdx + TIMELINE_PAGE_SIZE, events.size());

        // Update maxScrollOffset for scroll indicator
        maxScrollOffset = Math.max(0, (totalPages - 1) * 10);

        for (int i = startIdx; i < endIdx && y < this.height - 100; i++) {
            CombatEvent event = events.get(i);
            String eventText = formatEvent(event);
            int eventColor = getEventColor(event);

            float seconds = event.ticksSinceWaveStart() / 20f;
            String timePrefix = Objects.requireNonNull(String.format("[%.1fs] ", seconds));

            graphics.drawString(font, timePrefix, x + 10, y, COLOR_TEXT_DIM, false);
            graphics.drawString(font, eventText, x + 10 + font.width(timePrefix), y, eventColor, false);
            y += lineHeight;
        }

        // Page indicator at bottom
        if (totalPages > 1) {
            String pageInfo = Objects.requireNonNull(String.format("← Page %d of %d →", timelinePage + 1, totalPages));
            int pageInfoWidth = font.width(pageInfo);
            graphics.drawString(font, pageInfo, x + (w - pageInfoWidth) / 2, this.height - 110, COLOR_ACCENT, false);
        }
    }

    /**
     * Party Stats tab - shows aggregated stats from all party members.
     * Only shows meaningful data if ClientPartyStatsCache has party data.
     */
    private void renderPartyTab(GuiGraphics graphics, int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 4;

        PartyWaveStats partyStats = ClientPartyStatsCache.getStatsForWave(waveNumber);

        if (partyStats == null || !partyStats.isPartyRun()) {
            // No party data available - UX: provide actionable guidance
            graphics.drawString(font, "PARTY STATS", x + 10, y, COLOR_ACCENT, false);
            y += lineHeight + 10;
            graphics.drawString(font, "No party data for this wave.", x + 10, y, COLOR_TEXT_DIM, false);
            y += lineHeight + 8;
            graphics.drawString(font, "To see party stats:", x + 10, y, COLOR_TEXT, false);
            y += lineHeight;
            // UX Q4/Q5: Use dynamic keybind name with fallback for unknown/empty
            String partyKey = KeyInputHandler.OPEN_PARTY_KEY.getKey().getDisplayName().getString();
            if (partyKey == null || partyKey.isBlank() || partyKey.contains("unknown")) {
                partyKey = "P"; // Sensible default fallback
            }
            graphics.drawString(font, "  1. Open Party Screen (" + partyKey + ")", x + 10, y, COLOR_TEXT_DIM, false);
            y += lineHeight;
            graphics.drawString(font, "  2. Create or join a party", x + 10, y, COLOR_TEXT_DIM, false);
            y += lineHeight;
            graphics.drawString(font, "  3. Start an Endurance quest together", x + 10, y, COLOR_TEXT_DIM, false);
            y += lineHeight + 8;
            graphics.drawString(font, "Party members' kills, damage, and combos", x + 10, y, COLOR_TEXT_DIM, false);
            y += lineHeight;
            graphics.drawString(font, "will be aggregated here after each wave.", x + 10, y, COLOR_TEXT_DIM, false);
            return;
        }

        // Header
        graphics.drawString(font, "PARTY STATS", x + 10, y, COLOR_ACCENT, false);
        y += lineHeight + 10;

        // Party totals
        graphics.drawString(font, "PARTY TOTALS", x + 10, y, COLOR_TEXT_DIM, false);
        y += lineHeight;
        renderStatLine(graphics, font, x + 20, y, "Total Kills", String.valueOf(partyStats.partyTotalKills()));
        y += lineHeight;
        // FIX UX #7: Show elite kills as PARTY total, not individual (prevents "stealing" elites)
        renderStatLine(graphics, font, x + 20, y, "Elite Kills", String.valueOf(partyStats.partyEliteKills()));
        y += lineHeight;
        renderStatLine(graphics, font, x + 20, y, "Total Damage", String.format("%,d", partyStats.partyTotalDamageDealt()));
        y += lineHeight;
        renderStatLine(graphics, font, x + 20, y, "Party DPS", String.format("%.1f", partyStats.partyDPS()));
        y += lineHeight;
        renderStatLine(graphics, font, x + 20, y, "Damage Taken", String.format("%,d", partyStats.partyTotalDamageTaken()));
        y += lineHeight;

        // No damage bonus - UX: Use ✓ with star for colorblind accessibility
        if (partyStats.partyNoDamageWave()) {
            graphics.drawString(font, "✓★ PARTY FLAWLESS WAVE!", x + 20, y, COLOR_SUCCESS, true);
            y += lineHeight;
        }

        y += lineHeight;

        // MVP
        String mvpId = partyStats.mvpPlayerId();
        String mvpReason = partyStats.mvpReason();
        if (mvpId != null && !mvpId.isEmpty()) {
            PartyWaveStats.PlayerWaveData mvpData = partyStats.getPlayerData(mvpId);
            String mvpName = mvpData != null ? mvpData.playerName() : "Unknown";
            graphics.drawString(font, "MVP: " + mvpName, x + 10, y, DesignTokens.Overlay.Text.GOLD, true);
            if (mvpReason != null && !mvpReason.isEmpty()) {
                graphics.drawString(font, " (" + mvpReason + ")", x + 10 + font.width("MVP: " + mvpName), y, COLOR_TEXT_DIM, false);
            }
            y += lineHeight + 10;
        }

        // Per-player breakdown
        graphics.drawString(font, "PLAYER BREAKDOWN", x + 10, y, COLOR_TEXT_DIM, false);
        y += lineHeight;

        // Table header
        int col1 = x + 20;
        int col2 = x + 150;
        int col3 = x + 210;
        int col4 = x + 280;
        int col5 = x + 350;

        graphics.drawString(font, "Player", col1, y, COLOR_TEXT_DIM, false);
        graphics.drawString(font, "Kills", col2, y, COLOR_TEXT_DIM, false);
        graphics.drawString(font, "Kill%", col3, y, COLOR_TEXT_DIM, false);
        graphics.drawString(font, "DPS", col4, y, COLOR_TEXT_DIM, false);
        graphics.drawString(font, "Combo", col5, y, COLOR_TEXT_DIM, false);
        y += lineHeight;

        // Draw separator
        graphics.fill(col1, y - 2, x + w - 20, y - 1, DesignTokens.Stroke.DEFAULT);

        // Player rows
        for (PartyWaveStats.PlayerWaveData playerData : partyStats.playerStats()) {
            if (playerData.wasSpectator()) {
                graphics.drawString(font, playerData.playerName(), col1, y, COLOR_TEXT_DIM, false);
                graphics.drawString(font, "(Spectator)", col2, y, COLOR_TEXT_DIM, false);
            } else {
                boolean isMvp = playerData.playerId().equals(mvpId);
                int nameColor = isMvp ? DesignTokens.Overlay.Text.GOLD : COLOR_TEXT;

                graphics.drawString(font, playerData.playerName(), col1, y, nameColor, isMvp);
                graphics.drawString(font, String.valueOf(playerData.kills()), col2, y, COLOR_TEXT, false);
                graphics.drawString(font, String.format("%.0f%%", playerData.killPercent()), col3, y, COLOR_TEXT, false);
                graphics.drawString(font, String.format("%.1f", playerData.dps()), col4, y, COLOR_TEXT, false);
                graphics.drawString(font, String.valueOf(playerData.maxCombo()), col5, y, COLOR_TEXT, false);
            }
            y += lineHeight;
        }
    }

    @SuppressWarnings("UnusedVariable") // w/h reserved for future layout constraints
    private void renderReportTab(GuiGraphics graphics, int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 4;

        graphics.drawString(font, "WAVE REPORT", x + 10, y, COLOR_ACCENT, false);
        y += lineHeight + 10;

        graphics.drawString(font, "Generate a ticket with full wave analytics:", x + 10, y, COLOR_TEXT, false);
        y += lineHeight * 2;

        graphics.drawString(font, "Report includes:", x + 10, y, COLOR_TEXT_DIM, false);
        y += lineHeight;
        graphics.drawString(font, "- Complete performance summary", x + 20, y, COLOR_TEXT, false);
        y += lineHeight;
        graphics.drawString(font, "- Per-mob kill breakdown", x + 20, y, COLOR_TEXT, false);
        y += lineHeight;
        graphics.drawString(font, "- Weapon effectiveness analysis", x + 20, y, COLOR_TEXT, false);
        y += lineHeight;
        graphics.drawString(font, "- Spatial heatmap data", x + 20, y, COLOR_TEXT, false);
        y += lineHeight;
        graphics.drawString(font, "- Full event timeline", x + 20, y, COLOR_TEXT, false);
        y += lineHeight * 2;

        graphics.drawString(font, "Click 'Submit Ticket' to send this data to the dev team.",
            x + 10, y, COLOR_TEXT_DIM, false);
    }

    // ========== Helpers ==========

    private void renderStatLine(GuiGraphics graphics, Font font, int x, int y, String label, String value) {
        Font safeFont = Objects.requireNonNull(font);
        graphics.drawString(safeFont, label + ":", x, y, COLOR_TEXT_DIM, false);
        graphics.drawString(safeFont, value, x + 100, y, COLOR_TEXT, false);
    }

    /**
     * Fix #7: Grade breakdown record for transparent scoring.
     */
    private record GradeBreakdown(
        String grade,
        float dpsScore,
        float noDamageBonus,
        float comboScore,
        float ttkScore,
        float critScore,
        float totalScore
    ) {}

    /**
     * Fix #3: Get cached breakdown or calculate if not yet cached.
     * FIX AUDIT #9: Invalidate cache when event count changes (late-arriving events).
     */
    private GradeBreakdown getOrCalculateGradeBreakdown() {
        int currentEventCount = collector.getEvents().size();
        // Invalidate cache if event count changed
        if (cachedGradeBreakdown != null && currentEventCount != cachedEventCount) {
            cachedGradeBreakdown = null;
        }
        if (cachedGradeBreakdown == null) {
            cachedGradeBreakdown = calculateGradeWithBreakdown();
            cachedEventCount = currentEventCount;
        }
        return cachedGradeBreakdown;
    }

    /**
     * Fix #7: Calculate grade with full breakdown for UI display.
     * UX Q2: Uses CAP_* constants for single source of truth.
     */
    private GradeBreakdown calculateGradeWithBreakdown() {
        float dpsScore = Math.min(CAP_DPS, collector.getDPS());
        float noDamageBonus = collector.wasNoDamageWave() ? CAP_NO_DAMAGE : 0;
        float comboScore = Math.min(CAP_COMBO, collector.getMaxCombo() / 5f);

        // TTK score: instant kills (avgTtk <= 0) get max bonus, slower kills scale down
        float ttkScore = 0;
        float avgTtk = collector.getAverageTTK();
        if (avgTtk <= 0) {
            // Instant kills or no data: award full TTK bonus
            ttkScore = CAP_TTK;
        } else if (avgTtk < 2000) {
            // Scale linearly: 0ms = 20pts, 2000ms = 0pts
            ttkScore = CAP_TTK * (1 - avgTtk / 2000f);
        }
        // avgTtk >= 2000: ttkScore remains 0

        float critScore = collector.getCriticalHitRate() * CAP_CRIT;

        float totalScore = dpsScore + noDamageBonus + comboScore + ttkScore + critScore;

        String grade;
        if (totalScore >= 80) grade = "S";
        else if (totalScore >= 60) grade = "A";
        else if (totalScore >= 40) grade = "B";
        else if (totalScore >= 20) grade = "C";
        else grade = "D";

        return new GradeBreakdown(grade, dpsScore, noDamageBonus, comboScore, ttkScore, critScore, totalScore);
    }

    private int getGradeColor(String grade) {
        return switch (grade) {
            case "S" -> DesignTokens.Overlay.Text.GOLD;
            case "A" -> COLOR_SUCCESS;
            case "B" -> COLOR_ACCENT;
            case "C" -> COLOR_WARNING;
            default -> COLOR_ERROR;
        };
    }

    private String formatDuration(long ms) {
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Format event for display.
     * UX Q10: Added symbols for colorblind accessibility (not just colors).
     */
    private String formatEvent(CombatEvent event) {
        return switch (event) {
            case CombatEvent.KillEvent k -> "✓ Killed " + k.mobType().getPath() +
                (k.isElite() ? " ★ELITE" : "") +
                (k.isCritical() ? " ⚡CRIT!" : "");
            case CombatEvent.DamageTakenEvent d -> String.format("✗ Took %.1f dmg from %s",
                d.damage(), d.damageSource());
            case CombatEvent.ComboEvent c -> "◆ Combo: " + c.type().name() + " (" + c.comboCount() + "x)";
            case CombatEvent.SpecialActionEvent s -> "◆ Action: " + s.actionType();
            case CombatEvent.MobSpawnEvent m -> "○ Spawned: " + m.mobType().getPath();
            default -> "· " + event.getClass().getSimpleName();
        };
    }

    private int getEventColor(CombatEvent event) {
        return switch (event) {
            case CombatEvent.KillEvent k -> k.isElite() ? COLOR_WARNING : COLOR_SUCCESS;
            case CombatEvent.DamageTakenEvent d -> COLOR_ERROR;
            case CombatEvent.ComboEvent c -> COLOR_ACCENT;
            case CombatEvent.SpecialActionEvent s -> COLOR_ACCENT;
            case CombatEvent.MobSpawnEvent m -> COLOR_TEXT_DIM;
            default -> COLOR_TEXT;
        };
    }

    /**
     * Export wave report to JSON file in the game directory.
     * Fix #5: Rate limited - can only submit once per screen instance.
     * Fix #8: Uses Gson for proper JSON escaping.
     */
    private void submitTicket() {
        // Fix #5: Prevent double submission
        if (ticketSubmitted) {
            return;
        }
        ticketSubmitted = true;
        updateButtonVisibility();

        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.player.Player player = mc.player;
        if (player == null) {
            onClose();
            return;
        }

        try {
            // Create reports directory
            java.nio.file.Path reportsDir = mc.gameDirectory.toPath().resolve("wis-reports");
            java.nio.file.Files.createDirectories(reportsDir);

            // Generate filename with timestamp
            String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String filename = String.format("wave_%d_report_%s.json", waveNumber, timestamp);
            java.nio.file.Path reportFile = reportsDir.resolve(filename);

            // Fix #8: Build report using proper data structures for Gson serialization
            GradeBreakdown grade = getOrCalculateGradeBreakdown();

            Map<String, Object> report = new LinkedHashMap<>();
            report.put("waveNumber", waveNumber);
            report.put("timestamp", timestamp);
            report.put("grade", grade.grade());
            report.put("totalScore", grade.totalScore());

            Map<String, Object> scoreBreakdown = new LinkedHashMap<>();
            scoreBreakdown.put("dps", grade.dpsScore());
            scoreBreakdown.put("noDamageBonus", grade.noDamageBonus());
            scoreBreakdown.put("combo", grade.comboScore());
            scoreBreakdown.put("ttk", grade.ttkScore());
            scoreBreakdown.put("crit", grade.critScore());
            report.put("scoreBreakdown", scoreBreakdown);

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("durationMs", collector.getWaveDurationMs());
            stats.put("kills", collector.getTotalKills());
            stats.put("eliteKills", collector.getEliteKills());
            stats.put("dps", collector.getDPS());
            stats.put("damageDealt", collector.getTotalDamageDealt());
            stats.put("damageTaken", collector.getTotalDamageTaken());
            stats.put("maxCombo", collector.getMaxCombo());
            stats.put("criticalHits", collector.getCriticalHits());
            stats.put("critRate", collector.getCriticalHitRate());
            stats.put("avgTTK", collector.getAverageTTK());
            stats.put("dodges", collector.getDodges());
            stats.put("parries", collector.getParries());
            stats.put("eventCount", collector.getEventCount());
            report.put("stats", stats);

            report.put("noDamageWave", collector.wasNoDamageWave());

            // Add party context if available
            PartyWaveStats partyStats = ClientPartyStatsCache.getStatsForWave(waveNumber);
            if (partyStats != null && partyStats.isPartyRun()) {
                Map<String, Object> partyData = new LinkedHashMap<>();
                partyData.put("partySize", partyStats.playerStats().size());
                partyData.put("partyTotalKills", partyStats.partyTotalKills());
                partyData.put("partyTotalDamage", partyStats.partyTotalDamageDealt());
                partyData.put("partyDPS", partyStats.partyDPS());
                partyData.put("partyNoDamageWave", partyStats.partyNoDamageWave());
                partyData.put("mvpPlayerId", partyStats.mvpPlayerId());
                partyData.put("mvpReason", partyStats.mvpReason());

                // Per-player breakdown
                List<Map<String, Object>> playerBreakdown = new ArrayList<>();
                for (PartyWaveStats.PlayerWaveData pd : partyStats.playerStats()) {
                    Map<String, Object> playerEntry = new LinkedHashMap<>();
                    playerEntry.put("playerName", pd.playerName());
                    playerEntry.put("kills", pd.kills());
                    playerEntry.put("killPercent", pd.killPercent());
                    playerEntry.put("dps", pd.dps());
                    playerEntry.put("maxCombo", pd.maxCombo());
                    playerEntry.put("wasSpectator", pd.wasSpectator());
                    playerBreakdown.add(playerEntry);
                }
                partyData.put("players", playerBreakdown);
                report.put("party", partyData);
            }

            // Write file using Gson for proper escaping
            String json = GSON.toJson(report);
            java.nio.file.Files.writeString(reportFile, json);

            // Notify player - UX: Make path clickable to copy or open folder
            // UX Q9: Add HoverEvents for clarity on what each button does
            // UX Q10: Show just filename to avoid long path wrapping, full path in hover
            String fullPath = Objects.requireNonNull(reportFile.toAbsolutePath().toString());
            String folderPath = Objects.requireNonNull(reportsDir.toAbsolutePath().toString());
            player.sendSystemMessage(Objects.requireNonNull(
                Component.literal("[WIS] Report saved: ")
                    .withStyle(style -> style.withColor(DesignTokens.Semantic.SUCCESS))
                    .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(filename))
                        .withStyle(style -> style
                            .withColor(COLOR_ACCENT)
                            .withUnderlined(true)
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                Objects.requireNonNull(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT),
                                Objects.requireNonNull(Component.literal("Click to copy full path:\n" + fullPath))))
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD,
                                fullPath)))))
                    .append(Objects.requireNonNull(Component.literal(" ")
                        .withStyle(style -> style.withColor(DesignTokens.Semantic.SUCCESS))))
                    .append(Objects.requireNonNull(Component.literal("[Open Folder]")
                        .withStyle(style -> style
                            .withColor(COLOR_ACCENT)
                            .withUnderlined(true)
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                Objects.requireNonNull(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT),
                                Objects.requireNonNull(Component.literal("Click to open folder"))))
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.OPEN_FILE,
                                folderPath)))))));

            org.slf4j.LoggerFactory.getLogger(DebriefScreen.class).info(
                "[WIS] Wave {} report exported to {}", waveNumber, reportFile);

        } catch (Exception e) {
            // Reset flag on error so user can retry
            ticketSubmitted = false;
            updateButtonVisibility();

            player.sendSystemMessage(Objects.requireNonNull(
                Component.literal("[WIS] Failed to save report: " + e.getMessage())
                    .withStyle(style -> style.withColor(DesignTokens.Semantic.ERROR))));
            org.slf4j.LoggerFactory.getLogger(DebriefScreen.class).error(
                "[WIS] Failed to export wave report", e);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizAmount, double vertAmount) {
        // Fix #9: Handle timeline pagination via scroll
        if (activeTab == Tab.TIMELINE) {
            List<CombatEvent> events = collector.getEvents();
            int totalPages = Math.max(1, (events.size() + TIMELINE_PAGE_SIZE - 1) / TIMELINE_PAGE_SIZE);

            if (vertAmount < 0 && timelinePage < totalPages - 1) {
                timelinePage++;
                updateTimelineButtonState(); // UX Q8
            } else if (vertAmount > 0 && timelinePage > 0) {
                timelinePage--;
                updateTimelineButtonState(); // UX Q8
            }
            return true;
        }

        // Default scroll behavior for other tabs
        scrollOffset = Math.max(0, scrollOffset - (int)(vertAmount * 10));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Fix #9: Arrow key navigation for timeline
        if (activeTab == Tab.TIMELINE) {
            List<CombatEvent> events = collector.getEvents();
            int totalPages = Math.max(1, (events.size() + TIMELINE_PAGE_SIZE - 1) / TIMELINE_PAGE_SIZE);

            if (keyCode == 262 && timelinePage < totalPages - 1) { // Right arrow
                timelinePage++;
                updateTimelineButtonState(); // UX Q8
                return true;
            } else if (keyCode == 263 && timelinePage > 0) { // Left arrow
                timelinePage--;
                updateTimelineButtonState(); // UX Q8
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
