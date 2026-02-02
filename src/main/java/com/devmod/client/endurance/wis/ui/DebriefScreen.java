package com.devmod.client.endurance.wis.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.endurance.ClientPartyStatsCache;
import com.devmod.client.endurance.wis.CombatEvent;
import com.devmod.client.endurance.wis.WaveIntelligenceManager;
import com.devmod.client.endurance.wis.WaveTelemetryCollector;
import com.devmod.client.input.KeyInputHandler;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.BaseDevModScreen;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.PartyWaveStats;
import com.devmod.mailbox.client.ClientTicketCache;
import com.devmod.mailbox.network.payload.TicketCreatePayload;
import com.devmod.mailbox.ticket.TicketCategory;
import com.devmod.mailbox.ticket.TicketPriority;
import com.devmod.mailbox.ticket.TicketStatus;
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

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(DebriefScreen.class);
    private static final String SCREEN_ID = "wave_debrief";

    // Layout
    private static final int PANEL_MAX_WIDTH = 960;
    private static final int PANEL_MAX_HEIGHT = 640;
    private static final int PANEL_MARGIN = DesignTokens.Space._8;
    private static final int PANEL_PADDING = DesignTokens.Space._7;
    private static final int TAB_HEIGHT = DesignTokens.Component.TAB_HEIGHT;
    private static final int TAB_GAP = DesignTokens.Component.TAB_GAP;
    private static final int FOOTER_HEIGHT = DesignTokens.Space._8;
    private static final int HEADER_GAP = DesignTokens.Space._4;
    private static final int ACTION_BUTTON_WIDTH = 140;
    private static final int ACTION_BUTTON_GAP = DesignTokens.Space._4;

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
        OVERVIEW("devmod.endurance.debrief.tab.overview", "O"),
        COMBAT("devmod.endurance.debrief.tab.combat", "C"),
        SPATIAL("devmod.endurance.debrief.tab.spatial", "S"),
        TIMELINE("devmod.endurance.debrief.tab.timeline", "T"),
        PARTY("devmod.endurance.debrief.tab.party", "P"),
        REPORT("devmod.endurance.debrief.tab.report", "R");

        final String labelKey;
        final String icon;

        Tab(String labelKey, String icon) {
            this.labelKey = labelKey;
            this.icon = icon;
        }
    }

    private Tab activeTab = Tab.OVERVIEW;
    private final WaveTelemetryCollector collector;
    private final int waveNumber;

    // UI components - initialized in initContent(), never null after init
    private final List<EditorButtonWidget> tabButtons = new ArrayList<>();
    private EditorButtonWidget continueButton;
    private EditorButtonWidget submitTicketButton;
    @Nullable private EditBox ticketSubjectField;
    @Nullable private EditBox ticketNotesField;
    // Timeline navigation buttons (UX: visual controls for accessibility)
    private EditorButtonWidget timelinePrevButton;
    private EditorButtonWidget timelineNextButton;

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

    // Layout state
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int headerHeight;
    private int tabStartX;
    private int tabY;
    private int tabWidth;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;
    private int footerY;

    // Ticket inputs
    private static final int TICKET_SUBJECT_MAX = 120;
    private static final int TICKET_NOTES_MAX = 600;

    public DebriefScreen(WaveTelemetryCollector collector) {
        super(I18n.screenTitle("wave_debrief"), SCREEN_ID, "endurance");
        this.collector = collector;
        this.waveNumber = collector.getWaveNumber();
    }

    @Override
    protected void initContent() {
        updateLayout();
        Font font = Minecraft.getInstance().font;
        int centerX = panelX + panelWidth / 2;
        int actionButtonHeight = DesignTokens.Component.BUTTON_HEIGHT_MD;
        int actionButtonY = footerY + Math.max(0, (FOOTER_HEIGHT - actionButtonHeight) / 2);

        // Tab buttons
        tabButtons.clear();
        int totalTabWidth = Tab.values().length * tabWidth + (Tab.values().length - 1) * TAB_GAP;
        tabStartX = panelX + (panelWidth - totalTabWidth) / 2;

        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
            int tabX = tabStartX + i * (tabWidth + TAB_GAP);
            String tabLabel = Objects.requireNonNull(tab.icon) + " " + Objects.requireNonNull(Component.translatable(Objects.requireNonNull(tab.labelKey)).getString());
            EditorButton tabBtn = EditorButton.builder("debrief-tab-" + tab.name().toLowerCase(Locale.ROOT), tabLabel)
                .style(EditorButton.Style.GHOST)
                .size(EditorButton.Size.MEDIUM)
                .onClick(() -> {
                    activeTab = tab;
                    scrollOffset = 0;
                    updateButtonVisibility();
                })
                .build();
            EditorButtonWidget tabWidget = new EditorButtonWidget(tabBtn, tabX, tabY, tabWidth, TAB_HEIGHT);
            tabButtons.add(tabWidget);
            addRenderableWidget(tabWidget);
        }

        // Continue button
        EditorButton continueBtn = EditorButton.builder("debrief-continue",
                Component.translatable("devmod.endurance.debrief.action.continue").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> {
                WaveIntelligenceManager.INSTANCE.skipDebrief();
                onClose();
            })
            .build();
        continueButton = new EditorButtonWidget(continueBtn,
            centerX - ACTION_BUTTON_WIDTH / 2, actionButtonY, ACTION_BUTTON_WIDTH, actionButtonHeight);
        addRenderableWidget(continueButton);

        // Submit ticket button (only in Report tab)
        EditorButton submitBtn = EditorButton.builder("debrief-submit-ticket",
                Component.translatable("devmod.endurance.debrief.action.submit_ticket").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::submitTicket)
            .build();
        EditorButtonWidget submitWidget = new EditorButtonWidget(submitBtn,
            centerX - ACTION_BUTTON_WIDTH / 2, actionButtonY, ACTION_BUTTON_WIDTH, actionButtonHeight);
        submitTicketButton = submitWidget;
        submitTicketButton.visible = false;
        addRenderableWidget(Objects.requireNonNull(submitWidget));

        // Timeline navigation buttons (UX: accessible visual controls)
        // UX Q3: Position adjacent with small gap (no 140px hole)
        int timelineNavY = contentY + contentHeight - actionButtonHeight - DesignTokens.Space._4;
        int buttonWidth = 60;
        int buttonGap = DesignTokens.Space._3;
        EditorButton prevBtn = EditorButton.builder("debrief-timeline-prev",
                Component.translatable("devmod.endurance.debrief.action.prev").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> {
                if (timelinePage > 0) {
                    timelinePage--;
                    updateTimelineButtonState(); // UX Q8: Update enabled state
                }
            })
            .build();
        EditorButtonWidget prevWidget = new EditorButtonWidget(prevBtn,
            centerX - buttonWidth - buttonGap / 2, timelineNavY, buttonWidth, actionButtonHeight - 4);
        timelinePrevButton = prevWidget;
        timelinePrevButton.visible = false;
        addRenderableWidget(Objects.requireNonNull(prevWidget));

        EditorButton nextBtn = EditorButton.builder("debrief-timeline-next",
                Component.translatable("devmod.endurance.debrief.action.next").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> {
                List<CombatEvent> events = collector.getEvents();
                int totalPages = Math.max(1, (events.size() + TIMELINE_PAGE_SIZE - 1) / TIMELINE_PAGE_SIZE);
                if (timelinePage < totalPages - 1) {
                    timelinePage++;
                    updateTimelineButtonState(); // UX Q8: Update enabled state
                }
            })
            .build();
        EditorButtonWidget nextWidget = new EditorButtonWidget(nextBtn,
            centerX + buttonGap / 2, timelineNavY, buttonWidth, actionButtonHeight - 4);
        timelineNextButton = nextWidget;
        timelineNextButton.visible = false;
        addRenderableWidget(Objects.requireNonNull(nextWidget));

        // Ticket fields (report tab)
        Font safeFont = Objects.requireNonNull(font);
        EditBox subjectField = new EditBox(safeFont, contentX, contentY, contentWidth, 20,
            Objects.requireNonNull(Component.translatable("devmod.endurance.debrief.report.subject")));
        subjectField.setMaxLength(TICKET_SUBJECT_MAX);
        subjectField.setHint(Objects.requireNonNull(Component.translatable(
            "devmod.endurance.debrief.report.subject.placeholder")));
        subjectField.setValue(Objects.requireNonNull(
            Component.translatable("devmod.endurance.debrief.report.subject.default", waveNumber).getString()));
        subjectField.setBordered(false);
        subjectField.setTextColor(DesignTokens.Text.PRIMARY);
        subjectField.setTextColorUneditable(DesignTokens.Text.MUTED);
        subjectField.visible = false;
        addRenderableWidget(subjectField);
        ticketSubjectField = subjectField;

        EditBox notesField = new EditBox(safeFont, contentX, contentY, contentWidth, 40,
            Objects.requireNonNull(Component.translatable("devmod.endurance.debrief.report.notes")));
        notesField.setMaxLength(TICKET_NOTES_MAX);
        notesField.setHint(Objects.requireNonNull(Component.translatable(
            "devmod.endurance.debrief.report.notes.placeholder")));
        notesField.setBordered(false);
        notesField.setTextColor(DesignTokens.Text.PRIMARY);
        notesField.setTextColorUneditable(DesignTokens.Text.MUTED);
        notesField.visible = false;
        addRenderableWidget(notesField);
        ticketNotesField = notesField;

        updateButtonVisibility();
    }

    private void updateLayout() {
        Font font = Minecraft.getInstance().font;
        UIScaleManager.update();
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 10);
        int margin = PANEL_MARGIN;
        panelWidth = Math.min(PANEL_MAX_WIDTH, Math.max(DesignTokens.Component.MODAL_MIN_WIDTH, width - margin * 2));
        panelHeight = Math.min(PANEL_MAX_HEIGHT, height - margin * 2);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        headerHeight = lineHeight * 2 + HEADER_GAP;
        tabY = panelY + PANEL_PADDING + headerHeight + HEADER_GAP;
        footerY = panelY + panelHeight - FOOTER_HEIGHT;

        contentX = panelX + PANEL_PADDING;
        contentY = tabY + TAB_HEIGHT + HEADER_GAP;
        int contentBottom = footerY - HEADER_GAP;
        contentWidth = panelWidth - PANEL_PADDING * 2;
        contentHeight = Math.max(0, contentBottom - contentY);

        int totalGap = (Tab.values().length - 1) * TAB_GAP;
        int usableWidth = Math.max(0, contentWidth - totalGap);
        tabWidth = Math.max(DesignTokens.Component.TAB_MIN_WIDTH, usableWidth / Tab.values().length);
    }

    private void updateButtonVisibility() {
        boolean reportTab = activeTab == Tab.REPORT;
        if (submitTicketButton != null) {
            submitTicketButton.visible = reportTab;
            // Fix #5: Disable button after submission
            submitTicketButton.getButton().setEnabled(!ticketSubmitted);
        }
        EditBox subjectBox = ticketSubjectField;
        if (subjectBox != null) {
            subjectBox.visible = reportTab;
            if (!reportTab) {
                subjectBox.setFocused(false);
            }
        }
        EditBox notesBox = ticketNotesField;
        if (notesBox != null) {
            notesBox.visible = reportTab;
            if (!reportTab) {
                notesBox.setFocused(false);
            }
        }
        layoutActionButtons(reportTab);
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

    private void layoutActionButtons(boolean showSubmit) {
        if (continueButton == null) {
            return;
        }
        int actionButtonY = footerY + Math.max(0, (FOOTER_HEIGHT - continueButton.getHeight()) / 2);
        int centerX = panelX + panelWidth / 2;
        continueButton.setY(actionButtonY);

        if (showSubmit && submitTicketButton != null) {
            int totalWidth = ACTION_BUTTON_WIDTH * 2 + ACTION_BUTTON_GAP;
            int startX = panelX + (panelWidth - totalWidth) / 2;
            continueButton.setX(startX);
            submitTicketButton.setX(startX + ACTION_BUTTON_WIDTH + ACTION_BUTTON_GAP);
            submitTicketButton.setY(actionButtonY);
        } else {
            continueButton.setX(centerX - ACTION_BUTTON_WIDTH / 2);
            if (submitTicketButton != null) {
                submitTicketButton.setY(actionButtonY);
            }
        }
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
        timelinePrevButton.getButton().setEnabled(timelinePage > 0);
        timelineNextButton.getButton().setEnabled(timelinePage < totalPages - 1);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background overlay
        graphics.fill(0, 0, this.width, this.height, COLOR_BG);

        // Panel
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, DesignTokens.Background.PANEL_SOLID);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, DesignTokens.Stroke.DEFAULT);
        graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, DesignTokens.Stroke.DEFAULT);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, DesignTokens.Stroke.DEFAULT);
        graphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, DesignTokens.Stroke.DEFAULT);

        // Header
        renderHeader(graphics);

        // Tab bar highlight
        renderTabHighlight(graphics);

        // Content panel background
        graphics.fill(contentX - 2, contentY - 2,
            contentX + contentWidth + 2, contentY + contentHeight + 2,
            DesignTokens.Surface.LEVEL_0);

        // Render active tab content
        boolean scissor = contentWidth > 0 && contentHeight > 0;
        if (scissor) {
            graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollOffset, 0);
        switch (activeTab) {
            case OVERVIEW -> renderOverviewTab(graphics, contentX, contentY, contentWidth, contentHeight);
            case COMBAT -> renderCombatTab(graphics, contentX, contentY, contentWidth, contentHeight);
            case SPATIAL -> renderSpatialTab(graphics, contentX, contentY, contentWidth, contentHeight);
            case TIMELINE -> renderTimelineTab(graphics, contentX, contentY, contentWidth, contentHeight);
            case PARTY -> renderPartyTab(graphics, contentX, contentY, contentWidth, contentHeight);
            case REPORT -> renderReportTab(graphics, contentX, contentY, contentWidth, contentHeight);
        }
        graphics.pose().popPose();
        if (scissor) {
            graphics.disableScissor();
        }

        // Fix #3: Render scroll indicator if content overflows
        if (maxScrollOffset > 0) {
            renderScrollIndicator(graphics, contentX + contentWidth - 8, contentY, 6, contentHeight);
        }

        renderInputBackgrounds(graphics);
    }

    private void renderInputBackgrounds(GuiGraphics graphics) {
        EditBox subjectBox = ticketSubjectField;
        if (subjectBox != null && subjectBox.visible) {
            AxiomRenderer.drawInputBackground(graphics, subjectBox.getX(), subjectBox.getY(),
                subjectBox.getWidth(), subjectBox.getHeight(), subjectBox.isFocused());
        }
        EditBox notesBox = ticketNotesField;
        if (notesBox != null && notesBox.visible) {
            AxiomRenderer.drawInputBackground(graphics, notesBox.getX(), notesBox.getY(),
                notesBox.getWidth(), notesBox.getHeight(), notesBox.isFocused());
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
        int textX = x - UIScaleManager.getScaledStringWidth(font, percentText) - 4;
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 10);
        int textY = thumbY + (thumbHeight - lineHeight) / 2;
        UIScaleManager.drawScaledString(graphics, font, percentText, textX, textY, COLOR_TEXT_DIM, false);
    }

    private void renderHeader(GuiGraphics graphics) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font);
        String title = Objects.requireNonNull(
            Component.translatable("devmod.endurance.debrief.header.title", waveNumber).getString());
        UIScaleManager.drawScaledString(graphics, font, title, panelX + PANEL_PADDING, panelY + PANEL_PADDING, COLOR_ACCENT, true);

        String subtitle = Component.translatable(
            "devmod.endurance.debrief.header.subtitle",
            formatDuration(collector.getWaveDurationMs()),
            collector.getTotalKills(),
            String.format("%.1f", collector.getDPS())
        ).getString();
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 10);
        UIScaleManager.drawScaledString(graphics, font, subtitle, panelX + PANEL_PADDING,
            panelY + PANEL_PADDING + lineHeight + HEADER_GAP, COLOR_TEXT_DIM, false);
    }

    private void renderTabHighlight(GuiGraphics graphics) {
        int activeIndex = 0;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] == activeTab) {
                activeIndex = i;
                break;
            }
        }
        int activeX = tabStartX + activeIndex * (tabWidth + TAB_GAP);
        graphics.fill(activeX, tabY + TAB_HEIGHT - 2, activeX + tabWidth, tabY + TAB_HEIGHT, COLOR_ACCENT);
    }

    // ========== Tab Content ==========

    private void renderOverviewTab(GuiGraphics graphics, int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 12);
        int columnGap = DesignTokens.Space._6;
        int columnWidth = Math.max(1, (w - columnGap) / 2);
        int leftX = x;
        int rightX = x + columnWidth + columnGap;

        int cursorLeft = y;
        int cursorRight = y;

        GradeBreakdown breakdown = getOrCalculateGradeBreakdown();
        int gradeColor = getGradeColor(breakdown.grade);

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.section.grade"), leftX, cursorLeft, COLOR_TEXT_DIM, false);
        cursorLeft += lineHeight;

        float gradeScale = 2.4f;
        int gradeHeight = Math.round(lineHeight * gradeScale);
        graphics.pose().pushPose();
        graphics.pose().translate(leftX, cursorLeft, 0);
        graphics.pose().scale(gradeScale, gradeScale, 1f);
        UIScaleManager.drawScaledString(graphics, font, breakdown.grade, 0, 0, gradeColor, true);
        graphics.pose().popPose();
        cursorLeft += gradeHeight + DesignTokens.Space._2;

        int breakdownY = cursorLeft;
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.grade.dps", String.format("%.0f", breakdown.dpsScore), String.format("%.0f", CAP_DPS)),
            leftX, breakdownY, COLOR_TEXT_DIM, false);
        breakdownY += lineHeight;
        if (breakdown.noDamageBonus > 0) {
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.grade.no_damage",
                String.format("%.0f", breakdown.noDamageBonus), String.format("%.0f", CAP_NO_DAMAGE)),
                leftX, breakdownY, COLOR_SUCCESS, false);
            breakdownY += lineHeight;
        }
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.grade.combo", String.format("%.0f", breakdown.comboScore), String.format("%.0f", CAP_COMBO)),
            leftX, breakdownY, COLOR_TEXT_DIM, false);
        breakdownY += lineHeight;
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.grade.ttk", String.format("%.0f", breakdown.ttkScore), String.format("%.0f", CAP_TTK)),
            leftX, breakdownY, COLOR_TEXT_DIM, false);
        breakdownY += lineHeight;
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.grade.crit", String.format("%.0f", breakdown.critScore), String.format("%.0f", CAP_CRIT)),
            leftX, breakdownY, COLOR_TEXT_DIM, false);
        breakdownY += lineHeight;
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.grade.total", String.format("%.0f", breakdown.totalScore), String.format("%.0f", CAP_TOTAL)),
            leftX, breakdownY, COLOR_ACCENT, false);
        cursorLeft = breakdownY + lineHeight;

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.section.achievements"), leftX, cursorLeft, COLOR_ACCENT, false);
        cursorLeft += lineHeight;

        boolean hasAchievement = false;
        if (collector.wasNoDamageWave()) {
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.achievement.no_damage"), leftX, cursorLeft, COLOR_SUCCESS, false);
            cursorLeft += lineHeight;
            hasAchievement = true;
        }
        if (collector.getMaxCombo() >= 50) {
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.achievement.combo_master"), leftX, cursorLeft, COLOR_SUCCESS, false);
            cursorLeft += lineHeight;
            hasAchievement = true;
        }
        if (collector.getCriticalHitRate() > 0.5f) {
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.achievement.precision"), leftX, cursorLeft, COLOR_SUCCESS, false);
            cursorLeft += lineHeight;
            hasAchievement = true;
        }
        if (!hasAchievement) {
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.achievement.none"), leftX, cursorLeft, COLOR_TEXT_DIM, false);
            cursorLeft += lineHeight;
        }

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.section.stats"), rightX, cursorRight, COLOR_TEXT_DIM, false);
        cursorRight += lineHeight;

        renderStatLine(graphics, font, rightX, cursorRight,
            tr("devmod.endurance.debrief.stat.duration"), formatDuration(collector.getWaveDurationMs()));
        cursorRight += lineHeight;
        renderStatLine(graphics, font, rightX, cursorRight,
            tr("devmod.endurance.debrief.stat.kills"), String.valueOf(collector.getTotalKills()));
        cursorRight += lineHeight;
        renderStatLine(graphics, font, rightX, cursorRight,
            tr("devmod.endurance.debrief.stat.elite_kills"), String.valueOf(collector.getEliteKills()));
        cursorRight += lineHeight;
        renderStatLine(graphics, font, rightX, cursorRight,
            tr("devmod.endurance.debrief.stat.max_combo"), collector.getMaxCombo() + "x");
        cursorRight += lineHeight;
        renderStatLine(graphics, font, rightX, cursorRight,
            tr("devmod.endurance.debrief.stat.dps"), String.format("%.1f", collector.getDPS()));
        cursorRight += lineHeight;
        renderStatLine(graphics, font, rightX, cursorRight,
            tr("devmod.endurance.debrief.stat.damage_dealt"), String.format("%.0f", collector.getTotalDamageDealt()));
        cursorRight += lineHeight;
        renderStatLine(graphics, font, rightX, cursorRight,
            tr("devmod.endurance.debrief.stat.damage_taken"), String.format("%.0f", collector.getTotalDamageTaken()));
        cursorRight += lineHeight;
        renderStatLine(graphics, font, rightX, cursorRight,
            tr("devmod.endurance.debrief.stat.avg_ttk"), String.format("%.0fms", collector.getAverageTTK()));
        cursorRight += lineHeight + DesignTokens.Space._2;

        WaveTelemetryCollector prev = WaveIntelligenceManager.INSTANCE.getPreviousWaveCollector();
        if (prev != null) {
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.section.comparison"), rightX, cursorRight, COLOR_TEXT_DIM, false);
            cursorRight += lineHeight;

            float dpsDiff = collector.getDPS() - prev.getDPS();
            String dpsSymbol = dpsDiff >= 0 ? "▲" : "▼";
            String dpsChange = dpsDiff >= 0 ? "+" + String.format("%.1f", dpsDiff) : String.format("%.1f", dpsDiff);
            int dpsColor = dpsDiff >= 0 ? COLOR_SUCCESS : COLOR_ERROR;
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.comparison.dps", dpsSymbol, dpsChange),
                rightX + 6, cursorRight, dpsColor, false);
            cursorRight += lineHeight;

            float ttkDiff = prev.getAverageTTK() - collector.getAverageTTK();
            String ttkSymbol = ttkDiff >= 0 ? "▲" : "▼";
            String ttkChange = ttkDiff >= 0 ? "-" + String.format("%.0f", ttkDiff) + "ms" :
                               "+" + String.format("%.0f", -ttkDiff) + "ms";
            int ttkColor = ttkDiff >= 0 ? COLOR_SUCCESS : COLOR_ERROR;
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.comparison.ttk", ttkSymbol, ttkChange),
                rightX + 6, cursorRight, ttkColor, false);
            cursorRight += lineHeight;
        }

        updateScrollBounds(y, Math.max(cursorLeft, cursorRight), h);
    }

    private void renderCombatTab(GuiGraphics graphics, int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 12);
        int columnGap = DesignTokens.Space._6;
        int columnWidth = Math.max(1, (w - columnGap) / 2);
        int leftX = x;
        int rightX = x + columnWidth + columnGap;

        int cursorLeft = y;
        int cursorRight = y;

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.combat.mob_breakdown"), leftX, cursorLeft, COLOR_ACCENT, false);
        cursorLeft += lineHeight + 6;

        Map<ResourceLocation, WaveTelemetryCollector.MobTypeStats> mobStats = collector.getMobStats();
        List<Map.Entry<ResourceLocation, WaveTelemetryCollector.MobTypeStats>> sorted =
            new ArrayList<>(mobStats.entrySet());
        sorted.sort(Comparator.comparingInt(e -> -e.getValue().kills));

        int maxRows = 8;
        int shown = 0;
        for (var entry : sorted) {
            if (shown >= maxRows) break;
            String mobName = entry.getKey().getPath();
            WaveTelemetryCollector.MobTypeStats stats = entry.getValue();

            String line = tr("devmod.endurance.debrief.combat.mob_line",
                mobName, stats.kills, String.format("%.0f", stats.totalDamageDealt),
                String.format("%.0f", stats.getAverageTimeToKill()));
            UIScaleManager.drawScaledString(graphics, font, line, leftX, cursorLeft, COLOR_TEXT, false);
            cursorLeft += lineHeight;
            shown++;
        }
        if (sorted.size() > maxRows) {
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.combat.more", sorted.size() - maxRows),
                leftX, cursorLeft, COLOR_TEXT_DIM, false);
            cursorLeft += lineHeight;
        }

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.combat.weapon_stats"), rightX, cursorRight, COLOR_ACCENT, false);
        cursorRight += lineHeight + 6;

        Map<String, WaveTelemetryCollector.WeaponStats> weaponStats = collector.getWeaponStats();
        List<Map.Entry<String, WaveTelemetryCollector.WeaponStats>> weapons = new ArrayList<>(weaponStats.entrySet());
        weapons.sort(Comparator.comparingInt(e -> -e.getValue().kills));

        shown = 0;
        for (var entry : weapons) {
            if (shown >= maxRows) break;
            String weapon = entry.getKey();
            WaveTelemetryCollector.WeaponStats stats = entry.getValue();

            String line = tr("devmod.endurance.debrief.combat.weapon_line",
                weapon, stats.kills, String.format("%.0f", stats.totalDamage),
                String.format("%.0f", stats.getCriticalRate() * 100));
            UIScaleManager.drawScaledString(graphics, font, line, rightX, cursorRight, COLOR_TEXT, false);
            cursorRight += lineHeight;
            shown++;
        }
        if (weapons.size() > maxRows) {
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.combat.more", weapons.size() - maxRows),
                rightX, cursorRight, COLOR_TEXT_DIM, false);
            cursorRight += lineHeight;
        }

        updateScrollBounds(y, Math.max(cursorLeft, cursorRight), h);
    }

    private void renderSpatialTab(GuiGraphics graphics, int x, int y, int w, int h) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font);
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 12);
        int cursorY = y;
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.spatial.title"), x, cursorY, COLOR_ACCENT, false);
        cursorY += lineHeight + DesignTokens.Space._4;

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.spatial.positions", collector.getPlayerPositions().size()),
            x, cursorY, COLOR_TEXT, false);
        cursorY += lineHeight + DesignTokens.Space._2;

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.spatial.kills", collector.getKillPositions().size()),
            x, cursorY, COLOR_TEXT, false);
        cursorY += lineHeight + DesignTokens.Space._2;

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.spatial.damage", collector.getDamagePositions().size()),
            x, cursorY, COLOR_TEXT, false);
        cursorY += lineHeight + DesignTokens.Space._2;

        boolean noDeath = collector.getDeathPositions().isEmpty();
        String deathSymbol = noDeath ? "✓" : "✗";
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.spatial.deaths", deathSymbol, collector.getDeathPositions().size()),
            x, cursorY, noDeath ? COLOR_SUCCESS : COLOR_ERROR, false);
        cursorY += lineHeight + DesignTokens.Space._6;

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.spatial.heatmap_note"),
            x, cursorY, COLOR_TEXT_DIM, false);

        updateScrollBounds(y, cursorY + lineHeight, h);
    }

    private void renderTimelineTab(GuiGraphics graphics, int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 11);
        int textX = x + DesignTokens.Space._2;

        List<CombatEvent> events = collector.getEvents();
        int totalPages = Math.max(1, (events.size() + TIMELINE_PAGE_SIZE - 1) / TIMELINE_PAGE_SIZE);
        timelinePage = Math.min(timelinePage, totalPages - 1);

        String header = tr("devmod.endurance.debrief.timeline.header",
            timelinePage + 1, totalPages, events.size());
        UIScaleManager.drawScaledString(graphics, font, header, textX, y, COLOR_ACCENT, false);
        y += lineHeight + DesignTokens.Space._2;

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.timeline.hint"), textX, y, COLOR_TEXT_DIM, false);
        y += lineHeight + DesignTokens.Space._4;

        // Calculate page bounds
        int startIdx = timelinePage * TIMELINE_PAGE_SIZE;
        int endIdx = Math.min(startIdx + TIMELINE_PAGE_SIZE, events.size());

        maxScrollOffset = 0;

        int listBottom = y + h - (DesignTokens.Component.BUTTON_HEIGHT_MD + DesignTokens.Space._4);
        for (int i = startIdx; i < endIdx && y < listBottom; i++) {
            CombatEvent event = events.get(i);
            String eventText = formatEvent(event);
            int eventColor = getEventColor(event);

            float seconds = event.ticksSinceWaveStart() / 20f;
            String timePrefix = Objects.requireNonNull(String.format("[%.1fs] ", seconds));

            UIScaleManager.drawScaledString(graphics, font, timePrefix, textX, y, COLOR_TEXT_DIM, false);
            UIScaleManager.drawScaledString(graphics, font, eventText, textX + UIScaleManager.getScaledStringWidth(font, timePrefix), y, eventColor, false);
            y += lineHeight;
        }

        // Page indicator at bottom
        if (totalPages > 1) {
            String pageInfo = Objects.requireNonNull(tr("devmod.endurance.debrief.timeline.page", timelinePage + 1, totalPages));
            int pageInfoWidth = UIScaleManager.getScaledStringWidth(font, pageInfo);
            UIScaleManager.drawScaledString(graphics, font, pageInfo, x + (w - pageInfoWidth) / 2,
                listBottom + DesignTokens.Space._2, COLOR_ACCENT, false);
        }
    }

    /**
     * Party Stats tab - shows aggregated stats from all party members.
     * Only shows meaningful data if ClientPartyStatsCache has party data.
     */
    private void renderPartyTab(GuiGraphics graphics, int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 12);

        PartyWaveStats partyStats = ClientPartyStatsCache.getStatsForWave(waveNumber);

        if (partyStats == null || !partyStats.isPartyRun()) {
            // No party data available - UX: provide actionable guidance
            int cursorY = y;
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.title"), x, cursorY, COLOR_ACCENT, false);
            cursorY += lineHeight + 6;
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.empty"), x, cursorY, COLOR_TEXT_DIM, false);
            cursorY += lineHeight + DesignTokens.Space._3;
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.how_to"), x, cursorY, COLOR_TEXT, false);
            cursorY += lineHeight;
            // UX Q4/Q5: Use dynamic keybind name with fallback for unknown/empty
            String partyKey = KeyInputHandler.OPEN_PARTY_KEY.getKey().getDisplayName().getString();
            if (partyKey == null || partyKey.isBlank() || partyKey.contains("unknown")) {
                partyKey = "P"; // Sensible default fallback
            }
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.step_open", partyKey), x, cursorY, COLOR_TEXT_DIM, false);
            cursorY += lineHeight;
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.step_join"), x, cursorY, COLOR_TEXT_DIM, false);
            cursorY += lineHeight;
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.step_start"), x, cursorY, COLOR_TEXT_DIM, false);
            cursorY += lineHeight + DesignTokens.Space._3;
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.note_line1"), x, cursorY, COLOR_TEXT_DIM, false);
            cursorY += lineHeight;
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.note_line2"), x, cursorY, COLOR_TEXT_DIM, false);
            updateScrollBounds(y, cursorY + lineHeight, h);
            return;
        }

        int cursorY = y;
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.title"), x, cursorY, COLOR_ACCENT, false);
        cursorY += lineHeight + 6;

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.totals"), x, cursorY, COLOR_TEXT_DIM, false);
        cursorY += lineHeight;
        renderStatLine(graphics, font, x, cursorY, tr("devmod.endurance.debrief.party.total_kills"),
            String.valueOf(partyStats.partyTotalKills()));
        cursorY += lineHeight;
        renderStatLine(graphics, font, x, cursorY, tr("devmod.endurance.debrief.party.elite_kills"),
            String.valueOf(partyStats.partyEliteKills()));
        cursorY += lineHeight;
        renderStatLine(graphics, font, x, cursorY, tr("devmod.endurance.debrief.party.total_damage"),
            String.format("%,d", partyStats.partyTotalDamageDealt()));
        cursorY += lineHeight;
        renderStatLine(graphics, font, x, cursorY, tr("devmod.endurance.debrief.party.party_dps"),
            String.format("%.1f", partyStats.partyDPS()));
        cursorY += lineHeight;
        renderStatLine(graphics, font, x, cursorY, tr("devmod.endurance.debrief.party.damage_taken"),
            String.format("%,d", partyStats.partyTotalDamageTaken()));
        cursorY += lineHeight;

        if (partyStats.partyNoDamageWave()) {
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.flawless"), x, cursorY, COLOR_SUCCESS, true);
            cursorY += lineHeight;
        }

        cursorY += lineHeight;

        String mvpId = partyStats.mvpPlayerId();
        String mvpReason = partyStats.mvpReason();
        if (mvpId != null && !mvpId.isEmpty()) {
            PartyWaveStats.PlayerWaveData mvpData = partyStats.getPlayerData(mvpId);
            String mvpName = mvpData != null ? mvpData.playerName() : tr("devmod.endurance.debrief.party.mvp_unknown");
            String mvpLabel = tr("devmod.endurance.debrief.party.mvp", mvpName);
            UIScaleManager.drawScaledString(graphics, font, mvpLabel, x, cursorY, DesignTokens.Overlay.Text.GOLD, true);
            if (mvpReason != null && !mvpReason.isEmpty()) {
                UIScaleManager.drawScaledString(graphics, font, " (" + mvpReason + ")", x + UIScaleManager.getScaledStringWidth(font, Objects.requireNonNull(mvpLabel)), cursorY, COLOR_TEXT_DIM, false);
            }
            cursorY += lineHeight + DesignTokens.Space._4;
        }

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.breakdown"), x, cursorY, COLOR_TEXT_DIM, false);
        cursorY += lineHeight;

        int col1 = x;
        int col2 = x + (int)(w * 0.42f);
        int col3 = x + (int)(w * 0.60f);
        int col4 = x + (int)(w * 0.74f);
        int col5 = x + (int)(w * 0.86f);

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.col.player"), col1, cursorY, COLOR_TEXT_DIM, false);
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.col.kills"), col2, cursorY, COLOR_TEXT_DIM, false);
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.col.kill_pct"), col3, cursorY, COLOR_TEXT_DIM, false);
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.col.dps"), col4, cursorY, COLOR_TEXT_DIM, false);
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.col.combo"), col5, cursorY, COLOR_TEXT_DIM, false);
        cursorY += lineHeight;

        graphics.fill(col1, cursorY - 2, x + w, cursorY - 1, DesignTokens.Stroke.DEFAULT);

        for (PartyWaveStats.PlayerWaveData playerData : partyStats.playerStats()) {
            if (playerData.wasSpectator()) {
                UIScaleManager.drawScaledString(graphics, font, playerData.playerName(), col1, cursorY, COLOR_TEXT_DIM, false);
                UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.party.spectator"), col2, cursorY, COLOR_TEXT_DIM, false);
            } else {
                boolean isMvp = playerData.playerId().equals(mvpId);
                int nameColor = isMvp ? DesignTokens.Overlay.Text.GOLD : COLOR_TEXT;

                UIScaleManager.drawScaledString(graphics, font, playerData.playerName(), col1, cursorY, nameColor, isMvp);
                UIScaleManager.drawScaledString(graphics, font, String.valueOf(playerData.kills()), col2, cursorY, COLOR_TEXT, false);
                UIScaleManager.drawScaledString(graphics, font, String.format("%.0f%%", playerData.killPercent()), col3, cursorY, COLOR_TEXT, false);
                UIScaleManager.drawScaledString(graphics, font, String.format("%.1f", playerData.dps()), col4, cursorY, COLOR_TEXT, false);
                UIScaleManager.drawScaledString(graphics, font, String.valueOf(playerData.maxCombo()), col5, cursorY, COLOR_TEXT, false);
            }
            cursorY += lineHeight;
        }

        updateScrollBounds(y, cursorY, h);
    }

    @SuppressWarnings("UnusedVariable") // w/h reserved for future layout constraints
    private void renderReportTab(GuiGraphics graphics, int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = UIScaleManager.getScaledLineHeight(font, 12);
        int cursorY = y;
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.report.title"), x, cursorY, COLOR_ACCENT, false);
        cursorY += lineHeight + DesignTokens.Space._3;

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.report.subtitle"), x, cursorY, COLOR_TEXT, false);
        cursorY += lineHeight + DesignTokens.Space._4;

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.report.subject.label"), x, cursorY, COLOR_TEXT_DIM, false);
        cursorY += lineHeight;

        EditBox subjectField = ticketSubjectField;
        if (subjectField != null) {
            subjectField.setX(x);
            subjectField.setY(cursorY - scrollOffset);
            subjectField.setWidth(w);
            cursorY += subjectField.getHeight() + DesignTokens.Space._4;
        }

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.report.notes.label"), x, cursorY, COLOR_TEXT_DIM, false);
        cursorY += lineHeight;

        EditBox notesField = ticketNotesField;
        if (notesField != null) {
            notesField.setX(x);
            notesField.setY(cursorY - scrollOffset);
            notesField.setWidth(w);
            cursorY += notesField.getHeight() + DesignTokens.Space._4;
        }

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.report.includes.title"), x, cursorY, COLOR_TEXT_DIM, false);
        cursorY += lineHeight;
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.report.includes.summary"), x, cursorY, COLOR_TEXT, false);
        cursorY += lineHeight;
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.report.includes.mobs"), x, cursorY, COLOR_TEXT, false);
        cursorY += lineHeight;
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.report.includes.weapons"), x, cursorY, COLOR_TEXT, false);
        cursorY += lineHeight;
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.report.includes.spatial"), x, cursorY, COLOR_TEXT, false);
        cursorY += lineHeight;
        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.report.includes.timeline"), x, cursorY, COLOR_TEXT, false);
        cursorY += lineHeight + DesignTokens.Space._2;

        UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.report.submit_hint"), x, cursorY, COLOR_TEXT_DIM, false);
        if (ticketSubmitted) {
            cursorY += lineHeight;
            UIScaleManager.drawScaledString(graphics, font, tr("devmod.endurance.debrief.report.submitted"), x, cursorY, COLOR_SUCCESS, false);
        }

        updateScrollBounds(y, cursorY + lineHeight, h);
    }

    // ========== Helpers ==========

    private void renderStatLine(GuiGraphics graphics, Font font, int x, int y, String label, String value) {
        Font safeFont = Objects.requireNonNull(font);
        UIScaleManager.drawScaledString(graphics, safeFont, label + ":", x, y, COLOR_TEXT_DIM, false);
        UIScaleManager.drawScaledString(graphics, safeFont, value, x + 100, y, COLOR_TEXT, false);
    }

    private void updateScrollBounds(int startY, int contentBottom, int viewHeight) {
        maxScrollOffset = Math.max(0, contentBottom - startY - viewHeight);
        if (scrollOffset > maxScrollOffset) {
            scrollOffset = maxScrollOffset;
        }
    }

    private static String tr(String key, Object... args) {
        String safeKey = Objects.requireNonNull(key);
        if (args == null || args.length == 0) {
            return Objects.requireNonNull(Component.translatable(safeKey).getString());
        }
        return Objects.requireNonNull(Component.translatable(safeKey, args).getString());
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
            case CombatEvent.KillEvent k -> tr("devmod.endurance.debrief.timeline.event.kill",
                k.mobType().getPath(),
                k.isElite() ? tr("devmod.endurance.debrief.timeline.event.elite") : "",
                k.isCritical() ? tr("devmod.endurance.debrief.timeline.event.crit") : "");
            case CombatEvent.DamageTakenEvent d -> tr("devmod.endurance.debrief.timeline.event.damage",
                String.format("%.1f", d.damage()), d.damageSource());
            case CombatEvent.ComboEvent c -> tr("devmod.endurance.debrief.timeline.event.combo",
                c.type().name(), c.comboCount());
            case CombatEvent.SpecialActionEvent s -> tr("devmod.endurance.debrief.timeline.event.action", s.actionType());
            case CombatEvent.MobSpawnEvent m -> tr("devmod.endurance.debrief.timeline.event.spawn", m.mobType().getPath());
            default -> tr("devmod.endurance.debrief.timeline.event.unknown", event.getClass().getSimpleName());
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

    private Map<String, Object> buildReportData(String timestamp, GradeBreakdown grade) {
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

        return report;
    }

    private String buildTicketDescription(GradeBreakdown grade, String notes, String reportFileName) {
        String summary = tr("devmod.endurance.debrief.ticket.summary",
            waveNumber,
            grade.grade(),
            formatDuration(collector.getWaveDurationMs()),
            collector.getTotalKills(),
            collector.getEliteKills(),
            String.format("%.1f", collector.getDPS()),
            String.format("%.0f", collector.getTotalDamageTaken()),
            collector.getMaxCombo(),
            String.format("%.0fms", collector.getAverageTTK()));

        StringBuilder description = new StringBuilder(summary);
        if (reportFileName != null && !reportFileName.isBlank()) {
            description.append(" ").append(tr("devmod.endurance.debrief.ticket.report_file", reportFileName));
        }
        if (notes != null && !notes.isBlank()) {
            description.append(" ").append(tr("devmod.endurance.debrief.ticket.notes", notes));
        }
        return description.toString();
    }

    private String resolveTicketSubject() {
        String fallback = Objects.requireNonNull(tr("devmod.endurance.debrief.report.subject.default", waveNumber));
        EditBox subjectField = ticketSubjectField;
        if (subjectField == null) {
            return fallback;
        }
        String subject = subjectField.getValue() != null ? subjectField.getValue().trim() : "";
        if (subject.isBlank()) {
            subjectField.setValue(fallback);
            return fallback;
        }
        return Objects.requireNonNull(subject);
    }

    private String resolveTicketNotes() {
        EditBox notesField = ticketNotesField;
        if (notesField == null) {
            return "";
        }
        String notes = notesField.getValue() != null ? notesField.getValue().trim() : "";
        if (notes.isBlank()) {
            return "";
        }
        return notes.replaceAll("\\s+", " ");
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
            ticketSubmitted = false;
            updateButtonVisibility();
            return;
        }

        try {
            String subject = resolveTicketSubject();
            if (subject.isBlank()) {
                ticketSubmitted = false;
                updateButtonVisibility();
                showError(tr("devmod.endurance.debrief.report.error.subject"));
                return;
            }

            String notes = resolveTicketNotes();

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
            Map<String, Object> report = buildReportData(timestamp, grade);

            // Write file using Gson for proper escaping
            String json = GSON.toJson(report);
            java.nio.file.Files.writeString(reportFile, json);

            TicketCategory category = TicketCategory.BUG;
            TicketPriority priority = category.getDefaultPriority();
            String description = buildTicketDescription(grade, notes, filename);

            PacketDistributor.sendToServer(new TicketCreatePayload(category, priority, subject, description));

            UUID tempId = UUID.randomUUID();
            ClientTicketCache.addTicket(new ClientTicketCache.TicketData(
                tempId,
                category,
                priority,
                TicketStatus.OPEN,
                subject,
                description,
                java.time.Instant.now(),
                null,
                0
            ));

            // Notify player - UX: Make path clickable to copy or open folder
            String fullPath = Objects.requireNonNull(reportFile.toAbsolutePath().toString());
            String folderPath = Objects.requireNonNull(reportsDir.toAbsolutePath().toString());
            net.minecraft.network.chat.MutableComponent reportMessage = Component.translatable("devmod.endurance.debrief.report.sent_prefix")
                .append(Objects.requireNonNull(Component.literal(" ")))
                .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(filename))
                    .withStyle(style -> style
                        .withColor(COLOR_ACCENT)
                        .withUnderlined(true)
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                            Objects.requireNonNull(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT),
                            Objects.requireNonNull(Component.translatable(
                                "devmod.endurance.debrief.report.copy_path", fullPath))))
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.COPY_TO_CLIPBOARD,
                            fullPath)))))
                .append(Objects.requireNonNull(Component.literal(" ")))
                .append(Objects.requireNonNull(Component.translatable("devmod.endurance.debrief.report.open_folder")
                    .withStyle(style -> style
                        .withColor(COLOR_ACCENT)
                        .withUnderlined(true)
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                            Objects.requireNonNull(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT),
                            Objects.requireNonNull(Component.translatable("devmod.endurance.debrief.report.open_folder_hint"))))
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.OPEN_FILE,
                            folderPath)))));
            player.sendSystemMessage(Objects.requireNonNull(reportMessage.withStyle(
                style -> style.withColor(DesignTokens.Semantic.SUCCESS))));

            org.slf4j.LoggerFactory.getLogger(DebriefScreen.class).info(
                "[WIS] Wave {} report exported to {}", waveNumber, reportFile);

        } catch (Exception e) {
            // Reset flag on error so user can retry
            ticketSubmitted = false;
            updateButtonVisibility();

            player.sendSystemMessage(Objects.requireNonNull(
                Component.translatable("devmod.endurance.debrief.report.error", e.getMessage())
                    .withStyle(style -> style.withColor(DesignTokens.Semantic.ERROR))));
            org.slf4j.LoggerFactory.getLogger(DebriefScreen.class).error(
                "[WIS] Failed to export wave report / ticket", e);
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
        scrollOffset = net.minecraft.util.Mth.clamp(scrollOffset - (int)(vertAmount * 10), 0, maxScrollOffset);
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

    @Override
    protected void onContentClose() {
        LOGGER.info("[DebriefScreen] onContentClose called");

        // Ensure WIS transitions out of DEBRIEF phase when screen closes
        // This handles ESC key, Continue button, and any other close path
        if (WaveIntelligenceManager.INSTANCE.getCurrentPhase() == com.devmod.client.endurance.wis.WavePhase.DEBRIEF) {
            WaveIntelligenceManager.INSTANCE.skipDebrief();
        }

        // Open checkpoint screen if quest is still at WAVE_COMPLETE state
        // This ensures the user can continue to the next wave after viewing the debrief
        // IMPORTANT: Use delayedExecutor to run AFTER super.onClose() sets screen to null
        // mc.execute() runs synchronously on render thread, so we need a real delay
        var mc = net.minecraft.client.Minecraft.getInstance();
        java.util.concurrent.CompletableFuture.delayedExecutor(
            50, java.util.concurrent.TimeUnit.MILLISECONDS
        ).execute(() -> mc.execute(() -> {
            var data = com.devmod.client.endurance.ClientQuestCache.getData();
            var state = data != null ? data.getState() : null;
            boolean hasQuest = data != null && data.hasActiveQuest();
            LOGGER.info("[DebriefScreen] Checkpoint check: hasQuest={}, state={}", hasQuest, state);

            if (hasQuest && state == com.devmod.endurance.EnduranceQuestState.WAVE_COMPLETE) {
                LOGGER.info("[DebriefScreen] Opening WaveCheckpointScreen, currentScreen={}",
                    mc.screen != null ? mc.screen.getClass().getSimpleName() : "null");
                com.devmod.client.ui.ScreenSafety.openSafe(
                    "wave_checkpoint",
                    () -> new com.devmod.client.endurance.WaveCheckpointScreen());
            } else {
                LOGGER.info("[DebriefScreen] NOT opening checkpoint (hasQuest={}, state={})", hasQuest, state);
            }
        }));
    }
}
