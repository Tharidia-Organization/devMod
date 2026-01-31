package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.notification.ClientNotificationManager;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.BaseDevModScreen;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.compat.mods.dummmmmmy.DummmmmmyCompat;
import com.devmod.endurance.CustomKit;
import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.KitManager;
import com.devmod.endurance.KitPreset;
import com.devmod.endurance.KitSyncConfirmPayload;
import com.devmod.endurance.KitSyncPayload;
import com.devmod.endurance.PersonalRecordsSyncPayload;
import com.devmod.endurance.RequestPersonalRecordsPayload;
import com.devmod.endurance.StartQuestPayload;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null") // Minecraft API lacks @Nonnull annotations
public class EnduranceQuestScreen extends BaseDevModScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceQuestScreen.class);
    private static final String SCREEN_ID = "endurance_quest";

    // Layout constants - using DesignTokens for consistency
    private static final int SIDEBAR_WIDTH = 170;  // Wider sidebar for readable filters
    private static final int HEADER_HEIGHT = 50;   // Taller for better separation
    private static final int RIGHT_PANEL_WIDTH = 300;  // Wider for settings
    private static final int QUEST_CARD_HEIGHT = 72;   // Compact cards
    private static final int QUEST_CARD_MARGIN = 6;

    // Colors - standardized to DesignTokens
    private static final int COLOR_BG = DesignTokens.Bg.LEVEL_0;
    private static final int COLOR_SIDEBAR_BG = DesignTokens.Bg.LEVEL_1;
    private static final int COLOR_CARD_BG = DesignTokens.Surface.LEVEL_0;
    private static final int COLOR_CARD_HOVER = DesignTokens.Surface.LEVEL_1;
    private static final int COLOR_CARD_SELECTED = DesignTokens.Surface.LEVEL_2;
    private static final int COLOR_ACCENT = DesignTokens.Accent.PRIMARY;
    private static final int COLOR_TEXT = DesignTokens.Text.PRIMARY;
    private static final int COLOR_TEXT_DIM = DesignTokens.Text.SECONDARY;
    private static final int COLOR_SUCCESS = DesignTokens.Semantic.SUCCESS;
    private static final int COLOR_WARNING = DesignTokens.Semantic.WARNING;
    private static final int COLOR_DANGER = DesignTokens.Semantic.ERROR;
    private static final long KIT_SYNC_TIMEOUT_MS = 5_000L;
    private static final int KIT_SYNC_MAX_ATTEMPTS = 3;
    private static final long UI_LOCK_NOTICE_COOLDOWN_MS = 1500L;
    private static final long START_NOTICE_COOLDOWN_MS = 1200L;
    private static final long KIT_CHANGE_NOTICE_COOLDOWN_MS = 600L;

    // Tier colors - using DesignTokens where applicable
    private static final int COLOR_TIER_TRIVIAL = DesignTokens.Text.MUTED;
    private static final int COLOR_TIER_EASY = DesignTokens.Semantic.SUCCESS;
    private static final int COLOR_TIER_MEDIUM = DesignTokens.Semantic.WARNING;
    private static final int COLOR_TIER_HARD = EnduranceUiTheme.QuestTier.HARD;  // Orange - no direct token
    private static final int COLOR_TIER_ELITE = EnduranceUiTheme.QuestTier.ELITE; // Purple - no direct token
    private static final int COLOR_TIER_BOSS = DesignTokens.Semantic.ERROR;
    private static final Map<EnduranceQuestRegistry.MobTier, Integer> TIER_COLORS = Map.of(
        EnduranceQuestRegistry.MobTier.TRIVIAL, COLOR_TIER_TRIVIAL,
        EnduranceQuestRegistry.MobTier.EASY, COLOR_TIER_EASY,
        EnduranceQuestRegistry.MobTier.MEDIUM, COLOR_TIER_MEDIUM,
        EnduranceQuestRegistry.MobTier.HARD, COLOR_TIER_HARD,
        EnduranceQuestRegistry.MobTier.ELITE, COLOR_TIER_ELITE,
        EnduranceQuestRegistry.MobTier.BOSS, COLOR_TIER_BOSS
    );

    // State
    private List<EnduranceQuestRegistry.MobQuestConfig> allQuests = new ArrayList<>();
    private List<EnduranceQuestRegistry.MobQuestConfig> filteredQuests = new ArrayList<>();
    @Nullable
    private EnduranceQuestRegistry.MobQuestConfig selectedQuest = null;
    @Nullable
    private StartQuestPayload pendingStartPayload = null;
    @Nullable
    private String pendingKitId = null;
    @Nullable
    private KitSyncPayload pendingKitSyncPayload = null;
    private boolean kitSyncInFlight = false;
    private long kitSyncStartTime = 0L;
    private int kitSyncAttempts = 0;
    private long lastUiLockNoticeAt = 0L;
    private long lastStartNoticeAt = 0L;
    private long lastKitNoticeAt = 0L;
    private boolean personalRecordsRequested = false;

    // Filters
    private static final String ALL_NAMESPACE = "all";
    private String searchQuery = "";
    private String selectedNamespace = ALL_NAMESPACE;
    @Nullable
    private EnduranceQuestRegistry.MobTier selectedTier = null;

    // UI Components
    @Nullable
    private EditBox searchBox;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Sidebar scroll state
    private int sidebarScrollOffset = 0;
    private int sidebarMaxScroll = 0;
    @Nullable
    private EditorButton startButton;
    @Nullable
    private EditorButton shopButton;
    @Nullable
    private EditorButton decreaseWaveButton;
    @Nullable
    private EditorButton increaseWaveButton;
    @Nullable
    private EditorButton endlessToggleButton;
    @Nullable
    private EditorButton practiceToggleButton;
    @Nullable
    private EditorButton introDismissButton;

    // Quest settings
    private int questWaves = 10;
    private boolean endlessMode = false;
    private boolean practiceMode = false;
    private KitPreset selectedKit = KitPreset.STARTER;

    // Kit selection buttons
    @Nullable
    private EditorButton prevKitButton;
    @Nullable
    private EditorButton nextKitButton;
    @Nullable
    private EditorButton editKitButton;

    // Track if using custom kit
    private boolean usingCustomKit = false;
    @Nullable
    private String customKitName = null;

    // Saved custom kits support
    private List<CustomKit> savedCustomKits = new ArrayList<>();
    @Nullable
    private CustomKit selectedCustomKit = null;  // null = using preset or temporary

    // Arena selection panel
    @Nullable
    private ArenaSelectionPanel arenaPanel;

    // Reset filters button
    @Nullable
    private EditorButton resetFiltersButton;

    // Config button for game mechanics settings
    @Nullable
    private EditorButton configButton;

    // Configure Mob button - opens MobPoolEditorScreen with selected mob
    @Nullable
    private EditorButton configureMobButton;

    // Pre-selection from QuickTestWizard
    @Nullable
    private net.minecraft.resources.ResourceLocation preselectedMob = null;
    private boolean hasPreselection = false;

    // Intro overlay for first-time users
    private boolean showIntroOverlay = false;

    public EnduranceQuestScreen() {
        super(I18n.screenTitle("endurance_quests"), SCREEN_ID, "endurance");
    }

    /**
     * Constructor with pre-selected mob and settings from QuickTestWizard.
     *
     * @param mobId Pre-selected mob ID
     * @param waves Number of waves (0 for endless)
     */
    public EnduranceQuestScreen(@javax.annotation.Nullable net.minecraft.resources.ResourceLocation mobId, int waves) {
        super(I18n.screenTitle("endurance_quests"), SCREEN_ID, "endurance");
        this.preselectedMob = mobId;
        this.questWaves = waves > 0 ? waves : 10;
        this.endlessMode = waves <= 0;
        this.hasPreselection = mobId != null;
    }

    @Override
    protected void initContent() {
        // Notify onboarding overlay that Endurance Quest was opened
        com.devmod.client.overlay.OnboardingOverlay.onEnduranceQuestOpened();

        // Request personal records from server (only once per screen instance, not on resize)
        if (!personalRecordsRequested) {
            personalRecordsRequested = true;
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new RequestPersonalRecordsPayload());
        }

        // Check if first time opening Endurance Quest
        if (!SettingsManager.INSTANCE.getSettings().onboarding.hasSeenEnduranceIntro) {
            showIntroOverlay = true;
        }

        // Load quests
        loadQuests();

        // Load saved custom kits for kit selection
        savedCustomKits = KitManager.INSTANCE.getAllCustomKits();

        // Search box - positioned at very bottom of sidebar
        var safeFont = Objects.requireNonNull(font);
        int searchY = height - 28;
        searchBox = new EditBox(safeFont, 10, searchY, SIDEBAR_WIDTH - 20, 18, I18n.ui("search"));
        var searchBoxLocal = Objects.requireNonNull(searchBox);
        searchBoxLocal.setHint(Objects.requireNonNull(I18n.translate("devmod.quest.search_mobs")));
        searchBoxLocal.setResponder(query -> {
            searchQuery = query;
            applyFilters();
        });
        searchBoxLocal.setBordered(false);
        searchBoxLocal.setTextColor(DesignTokens.Text.PRIMARY());
        searchBoxLocal.setTextColorUneditable(DesignTokens.Text.MUTED());
        addRenderableWidget(Objects.requireNonNull(searchBox));

        // All buttons are now rendered custom via renderActionButtons()
        initButtons();
    }

    private void initButtons() {
        // Initialize arena selection panel in the right panel area
        int rightPanelX = width - RIGHT_PANEL_WIDTH + 10;
        int arenaPanelY = HEADER_HEIGHT + 200; // Below kit selector
        arenaPanel = new ArenaSelectionPanel(rightPanelX, arenaPanelY, RIGHT_PANEL_WIDTH - 20, 40);
        arenaPanel.setOnSelectionChanged(selection -> {
            var panel = Objects.requireNonNull(arenaPanel);
            boolean auto = panel.isAutoSelected();
            String label = panel.getSelectedTemplateLabel();
            Notification notification = Notification.builder(NotificationCategory.QUEST)
                .titleKey("devmod.endurance.quest.notify.arena.title")
                .messageKey(auto
                    ? "devmod.endurance.quest.notify.arena.auto"
                    : "devmod.endurance.quest.notify.arena.manual")
                .param("arena", label != null ? label : I18n.translate("devmod.endurance.quest.arena.auto").getString())
                .priority(NotificationPriority.LOW)
                .displayDurationMs(1600)
                .build();
            ClientNotificationManager.INSTANCE.handleNotification(notification);
        });

        startButton = EditorButton.builder("start-quest", I18n.ui("start_quest").getString())
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.LARGE)
            .onClick(this::startSelectedQuest)
            .build();

        shopButton = EditorButton.builder("open-shop", I18n.ui("shop").getString())
            .style(EditorButton.Style.PRIMARY)
            .accent(DesignTokens.Semantic.WARNING)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::openShop)
            .build();

        decreaseWaveButton = EditorButton.builder("wave-minus", "-")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> adjustWaves(-1))
            .build();

        increaseWaveButton = EditorButton.builder("wave-plus", "+")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> adjustWaves(1))
            .build();

        endlessToggleButton = EditorButton.builder("endless-toggle", I18n.translate("devmod.endurance.quest.button.endless").getString())
            .style(EditorButton.Style.PRIMARY)
            .toggleable(true)
            .toggled(endlessMode)
            .size(EditorButton.Size.MEDIUM)
            .onToggle(enabled -> setEndlessMode(Boolean.TRUE.equals(enabled)))
            .build();

        // Practice mode toggle (only shown if Dummmmmmy mod is available)
        practiceToggleButton = EditorButton.builder("practice-toggle", I18n.translate("devmod.endurance.quest.button.practice").getString())
            .style(EditorButton.Style.NORMAL)
            .toggleable(true)
            .toggled(practiceMode)
            .size(EditorButton.Size.SMALL)
            .onToggle(enabled -> setPracticeMode(Boolean.TRUE.equals(enabled)))
            .build();

        introDismissButton = EditorButton.builder("intro-dismiss", I18n.ui("got_it").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::dismissIntroOverlay)
            .build();

        prevKitButton = EditorButton.builder("kit-prev", "<")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::prevKit)
            .build();

        nextKitButton = EditorButton.builder("kit-next", ">")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::nextKit)
            .build();

        editKitButton = EditorButton.builder("kit-edit", I18n.ui("edit").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(this::openKitEditor)
            .build();

        resetFiltersButton = EditorButton.builder("reset-filters", I18n.ui("reset").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(this::resetFilters)
            .build();

        configButton = EditorButton.builder("config-mechanics", "⚙")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(this::openConfigScreen)
            .build();

        configureMobButton = EditorButton.builder("configure-mob", I18n.translate("devmod.endurance.quest.button.configure_mob").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(this::openMobConfigEditor)
            .build();

        // Request arena suggestions if a quest is already pre-selected
        var preselected = selectedQuest;
        if (preselected != null && arenaPanel != null) {
            arenaPanel.requestSuggestions(preselected.getMobId().toString());
        }
    }

    private void openConfigScreen() {
        com.devmod.client.ui.ScreenSafety.openSafe(
            "endurance_settings",
            this,
            () -> new EnduranceSettingsScreen(this));
    }

    /**
     * Open the MobPoolEditorScreen with the currently selected mob preselected.
     * This provides a quick way to configure the selected mob's Endurance settings.
     */
    private void openMobConfigEditor() {
        var quest = selectedQuest;
        if (quest == null) {
            return;
        }
        com.devmod.client.ui.ScreenSafety.openSafe(
            "mob_pool_editor",
            this,
            () -> new MobPoolEditorScreen(this, quest.getMobId()));
    }

    private void resetFilters() {
        boolean hadFilters = selectedTier != null || !selectedNamespace.equals(ALL_NAMESPACE) || !searchQuery.isEmpty();
        searchQuery = "";
        selectedNamespace = ALL_NAMESPACE;
        selectedTier = null;
        if (searchBox != null) {
            searchBox.setValue("");
        }
        applyFilters();
        if (hadFilters) {
            Notification notification = Notification.builder(NotificationCategory.SYSTEM)
                .titleKey("devmod.endurance.quest.notify.filters_reset.title")
                .messageKey("devmod.endurance.quest.notify.filters_reset.message")
                .priority(NotificationPriority.LOW)
                .displayDurationMs(1400)
                .build();
            ClientNotificationManager.INSTANCE.handleNotification(notification);
        }
    }

    private void prevKit() {
        if (kitSyncInFlight) {
            notifyUiLocked();
            return;
        }
        KitPreset[] presets = getSelectablePresets();

        if (selectedCustomKit != null) {
            // Currently on a saved kit - go to previous saved or last preset
            int idx = savedCustomKits.indexOf(selectedCustomKit);
            if (idx > 0) {
                selectedCustomKit = savedCustomKits.get(idx - 1);
            } else {
                // Go to last preset
                selectedCustomKit = null;
                selectedKit = presets[presets.length - 1];
            }
        } else {
            // Currently on a preset
            int idx = java.util.Arrays.asList(presets).indexOf(selectedKit);
            if (idx > 0) {
                selectedKit = presets[idx - 1];
            } else if (!savedCustomKits.isEmpty()) {
                // Wrap to last saved kit
                selectedCustomKit = savedCustomKits.get(savedCustomKits.size() - 1);
            } else {
                // Wrap to last preset
                selectedKit = presets[presets.length - 1];
            }
        }

        usingCustomKit = selectedCustomKit != null;
        customKitName = selectedCustomKit != null ? selectedCustomKit.getName() : null;
        notifyKitSelected();
    }

    private void nextKit() {
        if (kitSyncInFlight) {
            notifyUiLocked();
            return;
        }
        KitPreset[] presets = getSelectablePresets();

        if (selectedCustomKit != null) {
            // Currently on a saved kit - go to next saved or first preset
            int idx = savedCustomKits.indexOf(selectedCustomKit);
            if (idx < savedCustomKits.size() - 1) {
                selectedCustomKit = savedCustomKits.get(idx + 1);
            } else {
                // Wrap to first preset
                selectedCustomKit = null;
                selectedKit = presets[0];
            }
        } else {
            // Currently on a preset
            int idx = java.util.Arrays.asList(presets).indexOf(selectedKit);
            if (idx < presets.length - 1) {
                selectedKit = presets[idx + 1];
            } else if (!savedCustomKits.isEmpty()) {
                // Go to first saved kit
                selectedCustomKit = savedCustomKits.get(0);
            } else {
                // Wrap to first preset
                selectedKit = presets[0];
            }
        }

        usingCustomKit = selectedCustomKit != null;
        customKitName = selectedCustomKit != null ? selectedCustomKit.getName() : null;
        notifyKitSelected();
    }

    /**
     * Get presets that can be selected (excludes CUSTOM which is special).
     */
    private KitPreset[] getSelectablePresets() {
        return java.util.Arrays.stream(KitPreset.values())
            .filter(p -> p != KitPreset.CUSTOM)
            .toArray(KitPreset[]::new);
    }

    private void openKitEditor() {
        if (kitSyncInFlight) {
            notifyUiLocked();
            return;
        }
        CustomKit kitToEdit = selectedCustomKit;
        com.devmod.client.ui.ScreenSafety.openSafe(
            "kit_selection",
            this,
            () -> new KitSelectionScreen(this, items -> {
                // Refresh saved kits in case a new one was created
                savedCustomKits = KitManager.INSTANCE.getAllCustomKits();

                if (items != null && !items.isEmpty()) {
                    // Using temporary on-the-fly kit (not a saved custom kit)
                    usingCustomKit = true;
                    customKitName = KitManager.INSTANCE.getTemporaryKitName();
                    selectedCustomKit = null;  // Clear saved kit selection
                } else {
                    usingCustomKit = false;
                    customKitName = null;
                    selectedCustomKit = null;
                }
            }, savedKit -> {
                savedCustomKits = KitManager.INSTANCE.getAllCustomKits();
                if (savedKit != null) {
                    CustomKit resolved = null;
                    for (CustomKit kit : savedCustomKits) {
                        if (kit.getId().equals(savedKit.getId())) {
                            resolved = kit;
                            break;
                        }
                    }
                    selectedCustomKit = resolved != null ? resolved : savedKit;
                    customKitName = selectedCustomKit.getName();
                    usingCustomKit = true;
                }
            }, kitToEdit)
        );
    }

    private void openShop() {
        playClickSound();
        ActionRegistry.invoke(ActionIds.UI_ENDURANCE_SHOP_OPEN,
            ClientActionContexts.forClient(ActionOrigin.UI));
    }

    private void loadQuests() {
        allQuests = new ArrayList<>(EnduranceQuestRegistry.INSTANCE.getAllMobConfigs());
        applyFilters();

        // Apply pre-selection from QuickTestWizard if present
        if (hasPreselection && preselectedMob != null) {
            for (var quest : filteredQuests) {
                if (quest.getMobId().equals(preselectedMob)) {
                    selectedQuest = quest;
                    LOGGER.info("[EnduranceQuestScreen] Pre-selected mob: {}", preselectedMob);
                    break;
                }
            }
        }
    }

    private void applyFilters() {
        String queryLower = searchQuery.toLowerCase(Locale.ROOT);
        filteredQuests = allQuests.stream()
            .filter(q -> queryLower.isEmpty() ||
                q.getDisplayName().toLowerCase(Locale.ROOT).contains(queryLower) ||
                q.getMobId().toString().toLowerCase(Locale.ROOT).contains(queryLower))
            .filter(q -> selectedNamespace.equals(ALL_NAMESPACE) || q.getNamespace().equals(selectedNamespace))
            .filter(q -> selectedTier == null || q.getTier() == selectedTier)
            .sorted(Comparator.comparing(q -> q.getDisplayName()))
            .collect(Collectors.toList());

        // Clear selection if it no longer matches the filtered list
        if (selectedQuest != null && !filteredQuests.contains(selectedQuest)) {
            selectedQuest = null;
            if (arenaPanel != null) {
                arenaPanel.clear();
            }
        }

        scrollOffset = 0;
        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        // Use scaled values for correct scroll calculation (with same minimums as render)
        int scaledCardHeight = Math.max(60, UIScaleManager.scale(QUEST_CARD_HEIGHT));
        int scaledCardMargin = Math.max(4, UIScaleManager.scale(QUEST_CARD_MARGIN));
        int scaledHeader = UIScaleManager.scale(HEADER_HEIGHT);
        int contentHeight = filteredQuests.size() * (scaledCardHeight + scaledCardMargin);
        int viewportHeight = height - scaledHeader - UIScaleManager.scale(55);
        maxScroll = Math.max(0, contentHeight - viewportHeight);
    }

    // Scaled layout values - updated each frame
    private int scaledSidebarWidth;
    private int scaledHeaderHeight;
    private int scaledRightPanelWidth;
    private int scaledQuestCardHeight;
    private int scaledQuestCardMargin;
    private float cardTextScale; // Text scale factor for card content

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Update scaling
        UIScaleManager.update();
        int minSidebar = UIScaleManager.snap(160);
        int maxSidebar = UIScaleManager.snap(240);
        int minRight = UIScaleManager.snap(260);
        int maxRight = UIScaleManager.snap(380);
        int targetSidebar = UIScaleManager.snap((int) (width * 0.20f));
        int targetRight = UIScaleManager.snap((int) (width * 0.30f));
        scaledSidebarWidth = clampInt(targetSidebar, minSidebar, maxSidebar);
        scaledRightPanelWidth = clampInt(targetRight, minRight, maxRight);
        int minCenter = UIScaleManager.snap(360);
        int remaining = width - scaledSidebarWidth - scaledRightPanelWidth;
        if (remaining < minCenter) {
            int deficit = minCenter - remaining;
            int reduceRight = Math.min(deficit, scaledRightPanelWidth - minRight);
            scaledRightPanelWidth -= reduceRight;
            deficit -= reduceRight;
            int reduceSidebar = Math.min(deficit, scaledSidebarWidth - minSidebar);
            scaledSidebarWidth -= reduceSidebar;
        }
        scaledHeaderHeight = UIScaleManager.scale(HEADER_HEIGHT);
        // Card needs minimum height to fit 4 rows of text (9px line height * 4 + padding = ~60px min)
        scaledQuestCardHeight = Math.max(60, UIScaleManager.scale(QUEST_CARD_HEIGHT));
        scaledQuestCardMargin = Math.max(4, UIScaleManager.scale(QUEST_CARD_MARGIN));

        // Keep search box aligned with current sidebar width and screen height
        if (searchBox != null && font != null) {
            int searchX = 10;
            int searchW = scaledSidebarWidth - 20;
            int searchH = searchBox.getHeight();
            int searchY = height - searchH - 10;
            searchBox.setX(searchX);
            searchBox.setY(searchY);
            searchBox.setWidth(searchW);
        }
        // Text scale proportional to card height (base is 72px), clamped for readability
        cardTextScale = Math.max(0.7f, Math.min(1.3f, (float) scaledQuestCardHeight / QUEST_CARD_HEIGHT));

        // Background
        graphics.fill(0, 0, width, height, COLOR_BG);

        // If showing intro overlay, only render that (not the main content)
        if (showIntroOverlay) {
            renderIntroOverlay(graphics, mouseX, mouseY);
            return; // Skip rendering everything else
        }

        // Sidebar (left)
        renderSidebar(graphics, mouseX, mouseY);

        // Right panel (unified details + settings) - render first to set Y positions
        renderRightPanel(graphics, mouseX, mouseY);

        // Header (center top)
        renderHeader(graphics);

        // Quest list (center)
        renderQuestList(graphics, mouseX, mouseY);

        // Custom action buttons (rendered after panels to be on top)
        renderActionButtons(graphics, mouseX, mouseY);
        // Error messages are rendered by base class via showError()
    }

    /**
     * Render intro overlay explaining Endurance Quest to new users.
     */
    private void renderIntroOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);

        int panelW = 400;
        int panelH = 280;
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        // Panel background
        graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, COLOR_ACCENT);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, DesignTokens.Bg.LEVEL_1);

        int y = panelY + 15;
        int centerX = panelX + panelW / 2;

        // Title
        UIScaleManager.drawScaledCenteredString(graphics, safeFont,
            Objects.requireNonNull(I18n.translate("devmod.endurance.quest.intro.title").getString()),
            centerX, y, DesignTokens.Text.WHITE);
        y += UIScaleManager.getScaledLineHeight() + 12;

        // Separator
        graphics.fill(panelX + 20, y, panelX + panelW - 20, y + 1, DesignTokens.Stroke.MUTED);
        y += 15;

        // Description
        String[] lines = getIntroLines();

        for (String line : lines) {
            drawScaledText(graphics, safeFont, line, panelX + 25, y, DesignTokens.Text.WHITE);
            y += UIScaleManager.getScaledLineHeight();
        }

        // Button area
        y = panelY + panelH - 40;
        int btnW = 100;  // Intro dismiss button width
        int btnX = centerX - btnW / 2;
        int btnH = DesignTokens.Component.BUTTON_HEIGHT_LG;
        if (introDismissButton != null) {
            introDismissButton.render(graphics, btnX, y, btnW, btnH, mouseX, mouseY);
        }
    }

    /**
     * Dismiss intro overlay and save preference.
     */
    private void dismissIntroOverlay() {
        playClickSound();
        showIntroOverlay = false;
        SettingsManager.INSTANCE.getSettings().onboarding.hasSeenEnduranceIntro = true;
        SettingsManager.INSTANCE.markDirty();
        SettingsManager.INSTANCE.save();
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        int itemHeight = getSidebarItemHeight(safeFont);

        // Sidebar background
        graphics.fill(0, 0, scaledSidebarWidth, height, COLOR_SIDEBAR_BG);
        // Right border accent
        graphics.fill(scaledSidebarWidth - 2, 0, scaledSidebarWidth, height, COLOR_ACCENT);

        int y = 8;

        // === HEADER: Title with reset button ===
        drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.filters.title").getString(), 10, y, COLOR_TEXT);

        // Reset button (only show if any filter is active)
        boolean hasActiveFilters = selectedTier != null || !selectedNamespace.equals(ALL_NAMESPACE) || !searchQuery.isEmpty();
        var resetBtn = resetFiltersButton;
        if (hasActiveFilters && resetBtn != null) {
            int btnW = 40;
            int btnH = 14;
            int btnX = scaledSidebarWidth - btnW - 12;
            resetBtn.setEnabled(!kitSyncInFlight);
            resetBtn.render(graphics, btnX, y - 1, btnW, btnH, mouseX, mouseY);
        }
        y += itemHeight + 4;

        // Divider
        graphics.fill(8, y, scaledSidebarWidth - 10, y + 1, DesignTokens.Stroke.MUTED);
        y += 8;

        // Calculate layout bounds
        int searchBoxY = searchBox != null ? searchBox.getY() : height - 28;
        int searchLabelY = searchBoxY - (itemHeight + 2);
        int shopH = 24;
        int shopGap = 8;
        int shopY = searchLabelY - shopH - shopGap;
        int bottomReserved = height - shopY; // Shop button + Search label + Search box
        int tiersSectionHeight = (itemHeight + 2) * (EnduranceQuestRegistry.MobTier.values().length + 2);
        int modsSectionTop = y;
        int modsSectionBottom = height - bottomReserved - tiersSectionHeight;
        int modsViewportHeight = modsSectionBottom - modsSectionTop - (itemHeight + 2);

        // === SECTION: Mod Filters (scrollable) ===
        drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.filters.mod_source").getString(), 10, y, COLOR_TEXT_DIM);
        y += itemHeight;

        Set<String> namespaces = EnduranceQuestRegistry.INSTANCE.getAvailableNamespaces();
        List<String> sortedNamespaces = new ArrayList<>(namespaces);
        sortedNamespaces.sort(String::compareTo);
        sortedNamespaces.add(0, ALL_NAMESPACE);

        // Calculate total content height and max scroll
        int modsContentHeight = sortedNamespaces.size() * itemHeight;
        sidebarMaxScroll = Math.max(0, modsContentHeight - modsViewportHeight);

        // Clamp scroll offset
        sidebarScrollOffset = Math.max(0, Math.min(sidebarMaxScroll, sidebarScrollOffset));

        // Scissor for mod list clipping
        int clipTop = y;
        int clipBottom = modsSectionBottom;
        graphics.enableScissor(0, clipTop, scaledSidebarWidth - 4, clipBottom);

        int modY = y - sidebarScrollOffset;
        for (String ns : sortedNamespaces) {
            // Skip items above viewport
            if (modY + itemHeight < clipTop) {
                modY += itemHeight;
                continue;
            }
            // Stop if below viewport
            if (modY > clipBottom) break;

            boolean isSelected = ns.equals(selectedNamespace);
            boolean isHovered = mouseX >= 8 && mouseX <= scaledSidebarWidth - 10
                && mouseY >= Math.max(clipTop, modY) && mouseY < Math.min(clipBottom, modY + itemHeight - 1);

            // Selection/hover background
            if (isSelected) {
                graphics.fill(8, modY - 1, scaledSidebarWidth - 14, modY + itemHeight - 2, COLOR_ACCENT);
            } else if (isHovered) {
                graphics.fill(8, modY - 1, scaledSidebarWidth - 14, modY + itemHeight - 2, DesignTokens.Surface.LEVEL_1);
            }

            String displayName = ns.equals(ALL_NAMESPACE)
                ? I18n.translate("devmod.endurance.quest.filters.all").getString()
                : ns;
            if (displayName.length() > 12) {
                displayName = displayName.substring(0, 10) + "..";
            }
            long count = ns.equals(ALL_NAMESPACE) ? allQuests.size() :
                allQuests.stream().filter(q -> q.getNamespace().equals(ns)).count();

            int textColor = isSelected ? DesignTokens.Text.WHITE : COLOR_TEXT;
            drawScaledText(graphics, safeFont, displayName, 12, modY, textColor);
            String countStr = Objects.requireNonNull(String.valueOf(count));
            int countW = UIScaleManager.getScaledStringWidth(safeFont, countStr);
            drawScaledText(graphics, safeFont, "\u00A78" + countStr, scaledSidebarWidth - countW - 18, modY, COLOR_TEXT_DIM);

            modY += itemHeight;
        }

        graphics.disableScissor();

        // Scrollbar for mods section
        if (sidebarMaxScroll > 0) {
            int scrollbarX = scaledSidebarWidth - 6;
            int scrollbarH = Math.max(15, (int) ((float) modsViewportHeight / modsContentHeight * modsViewportHeight));
            int scrollbarY = clipTop + (int) ((float) sidebarScrollOffset / sidebarMaxScroll * (modsViewportHeight - scrollbarH));
            graphics.fill(scrollbarX, clipTop, scrollbarX + 3, clipBottom, DesignTokens.Surface.LEVEL_0);
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarH, COLOR_ACCENT);
        }

        y = modsSectionBottom + 8;

        // === SECTION: Difficulty Filters (fixed) ===
        drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.filters.difficulty").getString(), 10, y, COLOR_TEXT_DIM);
        y += itemHeight;

        // All tiers option
        boolean allTiersSelected = selectedTier == null;
        boolean allTiersHovered = mouseX >= 8 && mouseX <= scaledSidebarWidth - 10 && mouseY >= y && mouseY < y + itemHeight - 1;
        if (allTiersSelected) {
            graphics.fill(8, y - 1, scaledSidebarWidth - 14, y + itemHeight - 2, COLOR_ACCENT);
        } else if (allTiersHovered) {
            graphics.fill(8, y - 1, scaledSidebarWidth - 14, y + itemHeight - 2, DesignTokens.Surface.LEVEL_1);
        }
        drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.filters.all").getString(),
            12, y, allTiersSelected ? DesignTokens.Text.WHITE : COLOR_TEXT);
        y += itemHeight;

        for (EnduranceQuestRegistry.MobTier tier : EnduranceQuestRegistry.MobTier.values()) {
            boolean isSelected = tier == selectedTier;
            boolean isHovered = mouseX >= 8 && mouseX <= scaledSidebarWidth - 10 && mouseY >= y && mouseY < y + itemHeight - 1;

            int tierColor = Objects.requireNonNull(TIER_COLORS.get(tier));
            if (isSelected) {
                graphics.fill(8, y - 1, scaledSidebarWidth - 14, y + itemHeight - 2, tierColor);
            } else if (isHovered) {
                graphics.fill(8, y - 1, scaledSidebarWidth - 14, y + itemHeight - 2, DesignTokens.Surface.LEVEL_1);
            }

            long count = allQuests.stream().filter(q -> q.getTier() == tier).count();
            int textColor = isSelected ? DesignTokens.Text.WHITE : tierColor;
            drawScaledText(graphics, safeFont, getTierDisplayName(tier), 12, y, textColor);
            String countStr = Objects.requireNonNull(String.valueOf(count));
            int countW = UIScaleManager.getScaledStringWidth(safeFont, countStr);
            drawScaledText(graphics, safeFont, "\u00A78" + countStr, scaledSidebarWidth - countW - 18, y, COLOR_TEXT_DIM);

            y += itemHeight;
        }

        // === BOTTOM AREA: Shop button label + Search ===
        drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.filters.search_label").getString(),
            10, searchLabelY, COLOR_TEXT_DIM);

        if (searchBox != null) {
            AxiomRenderer.drawInputBackground(graphics, searchBox.getX(), searchBox.getY(), searchBox.getWidth(),
                searchBox.getHeight(), searchBox.isFocused());
        }
    }

    private int getSidebarItemHeight(net.minecraft.client.gui.Font safeFont) {
        return Math.max(14, UIScaleManager.getScaledLineHeight() + 2);
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void drawScaledText(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                String text, int x, int y, int color) {
        UIScaleManager.drawScaledString(graphics, font, text, x, y, color, false);
    }


    private void renderHeader(GuiGraphics graphics) {
        var safeFont = Objects.requireNonNull(font);
        int line = UIScaleManager.getScaledLineHeight();
        int headerX = scaledSidebarWidth;
        int headerW = width - scaledSidebarWidth - scaledRightPanelWidth;

        // Header background with subtle gradient effect
        graphics.fill(headerX, 0, headerX + headerW, scaledHeaderHeight, COLOR_SIDEBAR_BG);
        // Bottom accent line
        graphics.fill(headerX, scaledHeaderHeight - 2, headerX + headerW, scaledHeaderHeight, COLOR_ACCENT);

        // Title - larger and bolder looking
        String title = I18n.translate("devmod.endurance.quest.header.title").getString();
        drawScaledText(graphics, safeFont, title, headerX + 15, 8, COLOR_TEXT);

        // Filter status bar (below title)
        int filterY = 8 + line + 4;
        boolean hasActiveFilters = selectedTier != null || !selectedNamespace.equals(ALL_NAMESPACE) || !searchQuery.isEmpty();

        if (hasActiveFilters) {
            // Build filter tags
            StringBuilder tags = new StringBuilder();
            if (selectedTier != null) {
                tags.append("\u00A77[\u00A7f").append(getTierDisplayName(selectedTier)).append("\u00A77] ");
            }
            if (!selectedNamespace.equals(ALL_NAMESPACE)) {
                tags.append("\u00A77[\u00A7f").append(selectedNamespace).append("\u00A77] ");
            }
            if (!searchQuery.isEmpty()) {
                tags.append("\u00A77[\u00A7f\"").append(searchQuery).append("\"\u00A77]");
            }

            String filterLine = I18n.translate("devmod.endurance.quest.header.filter_line",
                filteredQuests.size(), allQuests.size(), tags.toString()).getString();
            drawScaledText(graphics, safeFont, filterLine, headerX + 15, filterY, COLOR_TEXT);
        } else {
            String countLine = I18n.translate("devmod.endurance.quest.header.count_line", filteredQuests.size()).getString();
            drawScaledText(graphics, safeFont, countLine, headerX + 15, filterY, COLOR_TEXT);
        }

        // Results count badge (right side of header)
        String countBadge = Objects.requireNonNull(String.valueOf(filteredQuests.size()));
        int badgeW = UIScaleManager.getScaledStringWidth(safeFont, countBadge) + 12;
        int badgeX = headerX + headerW - badgeW - 15;
        int badgeY = 12;

        // Badge background
        int badgeColor = hasActiveFilters ? COLOR_WARNING : COLOR_SUCCESS;
        graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 16, badgeColor);
        UIScaleManager.drawScaledCenteredString(graphics, safeFont, Objects.requireNonNull(countBadge),
            badgeX + badgeW / 2, badgeY + 4, DesignTokens.Text.WHITE);
    }

    private void renderQuestList(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        int listX = scaledSidebarWidth + 8;
        int listY = scaledHeaderHeight + 4;
        int listWidth = width - scaledSidebarWidth - scaledRightPanelWidth - 16;
        int listHeight = height - scaledHeaderHeight - 55;  // Leave space for bottom bar

        // Account for scrollbar width to prevent content shifting
        boolean hasScrollbar = maxScroll > 0;
        int effectiveListWidth = hasScrollbar ? listWidth - 8 : listWidth;

        // Clip area
        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        if (filteredQuests.isEmpty()) {
            renderEmptyQuestList(graphics, safeFont, listX, listY, listWidth, listHeight);
            graphics.disableScissor();
            return;
        }

        int y = listY - scrollOffset;
        for (EnduranceQuestRegistry.MobQuestConfig quest : filteredQuests) {
            if (y + scaledQuestCardHeight > listY && y < listY + listHeight) {
                renderQuestCard(graphics, quest, listX, y, effectiveListWidth, mouseX, mouseY);
            }
            y += scaledQuestCardHeight + scaledQuestCardMargin;
        }

        graphics.disableScissor();

        // Scrollbar
        if (hasScrollbar) {
            int scrollbarHeight = (int) ((float) listHeight / (listHeight + maxScroll) * listHeight);
            int scrollbarY = listY + (int) ((float) scrollOffset / maxScroll * (listHeight - scrollbarHeight));
            graphics.fill(listX + listWidth - 5, listY, listX + listWidth, listY + listHeight, DesignTokens.Stroke.MUTED);
            graphics.fill(listX + listWidth - 5, scrollbarY, listX + listWidth, scrollbarY + scrollbarHeight, COLOR_ACCENT);
        }
    }

    private void renderEmptyQuestList(GuiGraphics graphics, net.minecraft.client.gui.Font safeFont,
                                       int listX, int listY, int listWidth, int listHeight) {
        String title = Objects.requireNonNullElse(
            I18n.translate("devmod.endurance.quest.empty.title").getString(), "");
        String subtitle = Objects.requireNonNullElse(
            I18n.translate("devmod.endurance.quest.empty.subtitle").getString(), "");
        int titleX = listX + (listWidth - UIScaleManager.getScaledStringWidth(safeFont, title)) / 2;
        int titleY = listY + (listHeight / 2) - (UIScaleManager.getScaledLineHeight() / 2);
        drawScaledText(graphics, safeFont, title, titleX, titleY, COLOR_TEXT_DIM);
        int subtitleX = listX + (listWidth - UIScaleManager.getScaledStringWidth(safeFont, subtitle)) / 2;
        drawScaledText(graphics, safeFont, subtitle, subtitleX, titleY + UIScaleManager.getScaledLineHeight(), COLOR_TEXT_DIM);
    }

    private void renderQuestCard(GuiGraphics graphics, EnduranceQuestRegistry.MobQuestConfig quest,
                                  int x, int y, int width, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY < y + scaledQuestCardHeight;
        boolean isSelected = quest == selectedQuest;

        // Card background with subtle border effect
        int bgColor = isSelected ? COLOR_CARD_SELECTED : (isHovered ? COLOR_CARD_HOVER : COLOR_CARD_BG);
        graphics.fill(x, y, x + width, y + scaledQuestCardHeight, bgColor);

        // Left tier indicator (thicker for selected)
        int tierColor = Objects.requireNonNull(TIER_COLORS.get(quest.getTier()));
        int indicatorWidth = isSelected ? 5 : 3;
        graphics.fill(x, y, x + indicatorWidth, y + scaledQuestCardHeight, tierColor);

        // Selection border
        if (isSelected) {
            graphics.fill(x, y, x + width, y + 1, tierColor);
            graphics.fill(x, y + scaledQuestCardHeight - 1, x + width, y + scaledQuestCardHeight, tierColor);
            graphics.fill(x + width - 1, y, x + width, y + scaledQuestCardHeight, tierColor);
        }

        int contentX = x + indicatorWidth + 8;

        // Scale row offsets proportionally to card height (base height is 72)
        int row1Y = y + scaledQuestCardHeight * 6 / QUEST_CARD_HEIGHT;
        int row2Y = y + scaledQuestCardHeight * 20 / QUEST_CARD_HEIGHT;
        int row3Y = y + scaledQuestCardHeight * 36 / QUEST_CARD_HEIGHT;
        int row4Y = y + scaledQuestCardHeight * 52 / QUEST_CARD_HEIGHT;
        int badgeTopY = y + scaledQuestCardHeight * 4 / QUEST_CARD_HEIGHT;
        int badgeBottomY = y + scaledQuestCardHeight * 16 / QUEST_CARD_HEIGHT;

        // Row 1: Mob name + Tier badge (with scaled text)
        drawScaledCardText(graphics, safeFont, quest.getDisplayName(), contentX, row1Y, COLOR_TEXT);

        // Compact tier badge (pill style) - calculate width with scaled font
        String tierText = Objects.requireNonNull(getTierShortLabel(quest.getTier()));
        int tierWidth = (int) (safeFont.width(tierText) * cardTextScale) + 6;
        int tierBadgeX = x + width - tierWidth - 6;
        graphics.fill(tierBadgeX - 1, badgeTopY, tierBadgeX + tierWidth + 1, badgeBottomY, tierColor);
        drawScaledCardText(graphics, safeFont, tierText, tierBadgeX + 3, row1Y, DesignTokens.Text.WHITE);

        // Row 2: Namespace (mod name) - smaller and dimmer
        drawScaledCardText(graphics, safeFont, "\u00A78" + quest.getNamespace(), contentX, row2Y, COLOR_TEXT_DIM);

        // Row 3: Stats (HP | DMG) - compact format
        var actualStats = EnduranceQuestRegistry.INSTANCE.getActualStats(quest.getMobId());
        String statsLine;
        if (actualStats.isPresent() && actualStats.get().isValid()) {
            var stats = actualStats.get();
            statsLine = I18n.translate("devmod.endurance.quest.card.stats_actual",
                stats.health(), stats.damage(), quest.getPointsPerKill()).getString();
        } else {
            statsLine = I18n.translate("devmod.endurance.quest.card.stats_estimated",
                quest.getBaseHealth(), quest.getBaseDamage(), quest.getPointsPerKill()).getString();
        }
        drawScaledCardText(graphics, safeFont, statsLine, contentX, row3Y, COLOR_TEXT);

        // Row 4: Personal best (if any) - right aligned
        PersonalRecordsSyncPayload.MobRecord record = ClientPersonalRecordsCache.getMobRecord(quest.getMobId().toString());
        if (record.highestWave() > 0 || record.bestScore() > 0) {
            String bestText = I18n.translate("devmod.endurance.quest.card.best",
                record.highestWave(), record.bestScore()).getString();
            drawScaledCardText(graphics, safeFont, bestText, contentX, row4Y, COLOR_TEXT);
        } else {
            drawScaledCardText(graphics, safeFont, I18n.translate("devmod.endurance.quest.card.not_attempted").getString(),
                contentX, row4Y, COLOR_TEXT_DIM);
        }
    }

    /**
     * Helper method to draw text with card-proportional scaling.
     * Uses poseStack transformation to scale text proportionally with card size.
     */
    private void drawScaledCardText(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                     String text, int x, int y, int color) {
        if (Math.abs(cardTextScale - 1.0f) < 0.05f) {
            // No scaling needed, use UIScaleManager for consistency
            UIScaleManager.drawScaledString(graphics, font, text, x, y, color, false);
            return;
        }
        // Card-proportional scaling still needs custom handling
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(cardTextScale, cardTextScale, 1.0f);
        UIScaleManager.drawScaledString(graphics, font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /**
     * Renders the unified right panel containing both quest details and settings.
     * This is the main control panel for configuring and starting quests.
     */
    private void renderRightPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        int line = UIScaleManager.getScaledLineHeight();
        int gapSm = 4;
        int gapMd = 8;
        int panelX = width - scaledRightPanelWidth;
        int panelY = 0;
        int panelW = scaledRightPanelWidth;
        int panelH = height;

        // Panel background
        graphics.fill(panelX, panelY, panelX + panelW, panelH, COLOR_SIDEBAR_BG);
        // Left border accent
        graphics.fill(panelX, panelY, panelX + 2, panelH, COLOR_ACCENT);

        int contentX = panelX + 12;
        int contentW = panelW - 24;
        int y = 12;

        // === SECTION 1: SELECTED MOB INFO ===
        var quest = selectedQuest;
        if (quest != null) {
            // Mob name header
            String mobName = quest.getDisplayName();
            if (mobName.length() > 22) {
                mobName = mobName.substring(0, 20) + "..";
            }
            drawScaledText(graphics, safeFont, "\u00A7l" + mobName, contentX, y, COLOR_TEXT);
            y += line + gapSm;

            // Tier badge inline
            int tierColor = Objects.requireNonNull(TIER_COLORS.get(quest.getTier()));
            String tierName = getTierDisplayName(quest.getTier());
            String tierLine = I18n.translate("devmod.endurance.quest.details.tier_with_namespace",
                tierName, quest.getNamespace()).getString();
            drawScaledText(graphics, safeFont, tierLine, contentX, y, tierColor);
            y += line + gapMd;

            // Divider
            graphics.fill(contentX, y, contentX + contentW, y + 1, DesignTokens.Stroke.MUTED);
            y += gapMd;

            // Mob stats in compact grid format
            var actualStats = EnduranceQuestRegistry.INSTANCE.getActualStats(quest.getMobId());
            boolean hasActual = actualStats.isPresent() && actualStats.get().isValid();

            // Stats row 1: HP and DMG
            if (hasActual) {
                var stats = actualStats.get();
                drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.details.stat.health",
                    stats.health()).getString(), contentX, y, COLOR_TEXT);
                drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.details.stat.damage",
                    stats.damage()).getString(), contentX + 70, y, COLOR_TEXT);
                drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.details.stat.armor",
                    stats.armor()).getString(), contentX + 140, y, COLOR_TEXT);
            } else {
                drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.details.stat.health_estimated",
                    quest.getBaseHealth()).getString(), contentX, y, COLOR_TEXT);
                drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.details.stat.damage_estimated",
                    quest.getBaseDamage()).getString(), contentX + 70, y, COLOR_TEXT);
            }
            y += line + gapSm;

            // Stats row 2: Points and Elite chance
            drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.details.points_per_kill",
                quest.getPointsPerKill()).getString(), contentX, y, COLOR_TEXT);
            drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.details.elite_chance",
                quest.getEliteChance() * 100).getString(), contentX + 100, y, COLOR_TEXT);
            y += line + gapMd;

            // Configure Mob button Y position
            configureMobButtonY = y;
            y += line + gapMd + 4;

        } else {
            // No quest selected state
            drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.details.no_selection_line1").getString(),
                contentX, y, COLOR_TEXT_DIM);
            y += line + gapSm;
            drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.details.no_selection_line2").getString(),
                contentX, y, COLOR_TEXT_DIM);
            y += line + gapMd + 8;
        }

        // === SECTION 2: QUEST SETTINGS ===
        // Section header with background
        graphics.fill(contentX - 4, y, contentX + contentW + 4, y + 18, DesignTokens.Surface.LEVEL_0);
        drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.section.settings").getString(),
            contentX, y + 5, COLOR_ACCENT);
        // Config button position (rendered in renderActionButtons)
        configButtonY = y + 1;
        y += 18 + gapMd;

        // Wave selector with visual bar
        drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.label.waves").getString(),
            contentX, y + 2, COLOR_TEXT);

        // Wave count display (large)
        String waveText = endlessMode ? "\u00A7c∞" : String.format("\u00A7f%d", questWaves);
        drawScaledText(graphics, safeFont, waveText, contentX + 50, y, COLOR_TEXT);

        // Mini wave bar visualization
        int barX = contentX + 75;
        int barW = contentW - 85;
        int barH = 8;
        graphics.fill(barX, y + 2, barX + barW, y + 2 + barH, DesignTokens.Surface.LEVEL_0);
        if (!endlessMode) {
            int fillW = (int) ((questWaves / 50.0f) * barW);
            int barColor = questWaves <= 10 ? COLOR_SUCCESS : (questWaves <= 25 ? COLOR_WARNING : COLOR_DANGER);
            graphics.fill(barX + 1, y + 3, barX + 1 + fillW, y + 1 + barH, barColor);
        } else {
            // Endless mode - gradient fill
            graphics.fill(barX + 1, y + 3, barX + barW - 1, y + 1 + barH, COLOR_DANGER);
        }
        y += line + gapSm;

        // Wave control buttons area (rendered in renderActionButtons)
        waveControlY = y;
        y += line + gapMd + 6;

        // Endless toggle area
        endlessToggleY = y;
        y += line + gapMd + 6;

        // Practice mode toggle (only if Dummmmmmy available)
        if (DummmmmmyCompat.isAvailable()) {
            practiceModeY = y;
            y += line + gapMd + 4;
        }

        // Divider
        graphics.fill(contentX, y, contentX + contentW, y + 1, DesignTokens.Stroke.MUTED);
        y += gapMd + 2;

        // === SECTION 3: KIT SELECTION ===
        drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.section.kit").getString(),
            contentX, y, COLOR_ACCENT);
        y += line + gapSm;

        // Kit name with color (show custom kit if selected)
        String kitName;
        String kitDesc;
        int kitColor;
        CustomKit customKit = selectedCustomKit;  // Local copy for null safety
        if (customKit != null) {
            // Saved custom kit
            kitName = customKit.getName();
            kitDesc = I18n.translate("devmod.endurance.quest.kit.saved_desc").getString();
            kitColor = customKit.getColor();
        } else if (usingCustomKit && customKitName != null) {
            // Temporary on-the-fly kit
            kitName = customKitName;
            kitDesc = I18n.translate("devmod.endurance.quest.kit.custom_desc", I18n.ui("edit").getString()).getString();
            kitColor = DesignTokens.Semantic.WARNING;
        } else {
            // Preset kit
            kitName = selectedKit.getDisplayName();
            kitDesc = selectedKit.getDescription();
            kitColor = selectedKit.getColor();
        }
        drawScaledText(graphics, safeFont, "\u00A7f" + kitName, contentX, y, kitColor);
        y += line + gapSm;

        // Kit description
        drawScaledText(graphics, safeFont, "\u00A77" + kitDesc, contentX, y, COLOR_TEXT_DIM);
        y += line + gapSm;

        // Resolve preview items once for count + rendering
        List<net.minecraft.world.item.ItemStack> previewItems;
        if (customKit != null) {
            // Saved custom kit - show its items
            var mc = Minecraft.getInstance();
            var registryAccess = mc.level != null ? mc.level.registryAccess() : null;
            previewItems = customKit.toItemStacks(registryAccess);
        } else if (usingCustomKit && KitManager.INSTANCE.hasTemporaryKit()) {
            // Temporary on-the-fly kit
            previewItems = KitManager.INSTANCE.getTemporaryKitItems();
        } else {
            // Preset kit
            previewItems = selectedKit.getPreviewItems();
        }

        int kitItemCount = 0;
        for (var stack : previewItems) {
            if (stack != null && !stack.isEmpty()) {
                kitItemCount++;
            }
        }
        if (kitItemCount > 0) {
            String countLine = I18n.translate("devmod.endurance.quest.kit.item_count", kitItemCount).getString();
            drawScaledText(graphics, safeFont, "\u00A77" + countLine, contentX, y, COLOR_TEXT_DIM);
            y += line + gapSm;
        }

        // Kit navigation area (buttons rendered separately)
        kitControlY = y;
        y += line + gapMd + 6;

        // Kit preview items (show first 8 items as icons in 2 rows)
        if (!previewItems.isEmpty()) {
            int itemX = contentX;
            int itemCount = Math.min(previewItems.size(), 8);
            for (int i = 0; i < itemCount; i++) {
                var item = Objects.requireNonNull(previewItems.get(i));
                if (item.isEmpty()) continue;
                // Draw item background
                graphics.fill(itemX - 1, y - 1, itemX + 17, y + 17, DesignTokens.Surface.LEVEL_0);
                graphics.renderItem(item, itemX, y);
                itemX += 20;
                if (i == 3) {
                    // Second row
                    itemX = contentX;
                    y += 20;
                }
            }
            if (itemCount > 4) y += 22;
            else y += 22;
        } else {
            drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.kit.uses_inventory").getString(),
                contentX, y, COLOR_TEXT_DIM);
            y += line + gapSm;
        }

        // === SECTION 4: ARENA SELECTION ===
        graphics.fill(contentX - 4, y, contentX + contentW + 4, y + 18, DesignTokens.Surface.LEVEL_0);
        drawScaledText(graphics, safeFont, I18n.translate("devmod.endurance.quest.section.arena").getString(),
            contentX, y + 5, COLOR_ACCENT);
        y += 18 + gapMd;

        // Render arena selection panel with dynamic position
        var panel = arenaPanel;  // Local copy for null safety
        if (panel != null) {
            // Update panel position dynamically based on current Y
            panel.setPosition(contentX, y, contentW, 40);
            panel.render(graphics, safeFont, mouseX, mouseY);
        }
        y += 45;

        // Store the Y position for the start button
        startButtonY = height - 50;
    }

    // Y positions for control elements (set during renderRightPanel, used in renderActionButtons)
    private int waveControlY = 0;
    private int endlessToggleY = 0;
    private int practiceModeY = 0;
    private int kitControlY = 0;
    private int startButtonY = 0;
    private int configButtonY = 0;
    private int configureMobButtonY = 0;

    /**
     * Render custom action buttons with Impact styling.
     * Uses Y positions calculated in renderRightPanel for proper alignment.
     */
    private void renderActionButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = width - scaledRightPanelWidth;
        int controlX = panelX + 12;
        int controlW = scaledRightPanelWidth - 24;
        int line = UIScaleManager.getScaledLineHeight();

        // === CONFIG BUTTON (in Quest Settings header) ===
        int configBtnW = 22;
        int configBtnH = 16;
        int configBtnX = panelX + scaledRightPanelWidth - configBtnW - 16;
        var cfgBtn = configButton;
        if (cfgBtn != null) {
            cfgBtn.setEnabled(!kitSyncInFlight);
            cfgBtn.render(graphics, configBtnX, configButtonY, configBtnW, configBtnH, mouseX, mouseY);
        }

        // === CONFIGURE MOB BUTTON (in mob details section, only when quest selected) ===
        var configMobBtn = configureMobButton;
        if (selectedQuest != null && configMobBtn != null) {
            int mobConfigBtnW = 100;
            int mobConfigBtnH = 18;
            configMobBtn.setEnabled(!kitSyncInFlight);
            configMobBtn.render(graphics, controlX, configureMobButtonY, mobConfigBtnW, mobConfigBtnH, mouseX, mouseY);
        }

        // === WAVE CONTROL BUTTONS ===
        int waveBtnSize = 22;
        var decBtn = decreaseWaveButton;
        if (decBtn != null) {
            decBtn.setEnabled(!endlessMode && !kitSyncInFlight);
            decBtn.render(graphics, controlX, waveControlY, waveBtnSize, waveBtnSize, mouseX, mouseY);
        }
        var incBtn = increaseWaveButton;
        if (incBtn != null) {
            incBtn.setEnabled(!endlessMode && !kitSyncInFlight);
            incBtn.render(graphics, controlX + waveBtnSize + 8, waveControlY, waveBtnSize, waveBtnSize, mouseX, mouseY);
        }

        // === ENDLESS TOGGLE ===
        int toggleW = 100;
        int toggleH = 20;
        var endlessBtn = endlessToggleButton;
        if (endlessBtn != null) {
            endlessBtn
                .toggled(endlessMode)
                .style(endlessMode ? EditorButton.Style.DANGER : EditorButton.Style.NORMAL)
                .hotkeyHint(endlessMode
                    ? I18n.translate("devmod.overlay.on").getString()
                    : I18n.translate("devmod.overlay.off").getString());
            endlessBtn.setEnabled(!kitSyncInFlight);
            endlessBtn.render(graphics, controlX, endlessToggleY, toggleW, toggleH, mouseX, mouseY);
        }

        // === PRACTICE MODE TOGGLE (only if Dummmmmmy available) ===
        var practiceBtn = practiceToggleButton;
        if (DummmmmmyCompat.isAvailable() && practiceBtn != null) {
            int practiceW = 120;
            int practiceH = 18;
            practiceBtn
                .toggled(practiceMode)
                .style(practiceMode ? EditorButton.Style.SUCCESS : EditorButton.Style.NORMAL)
                .hotkeyHint(practiceMode
                    ? I18n.translate("devmod.overlay.on").getString()
                    : I18n.translate("devmod.overlay.off").getString());
            practiceBtn.setEnabled(!kitSyncInFlight);
            practiceBtn.render(graphics, controlX, practiceModeY, practiceW, practiceH, mouseX, mouseY);
        }

        // === KIT SELECTION BUTTONS ===
        int kitBtnW = 28;
        int kitBtnH = 22;
        int editBtnW = 40;
        boolean kitControlsEnabled = !kitSyncInFlight;
        var prevKit = prevKitButton;
        if (prevKit != null) {
            prevKit.setEnabled(kitControlsEnabled);
            prevKit.render(graphics, controlX, kitControlY, kitBtnW, kitBtnH, mouseX, mouseY);
        }
        var nextKit = nextKitButton;
        if (nextKit != null) {
            nextKit.setEnabled(kitControlsEnabled);
            nextKit.render(graphics, controlX + controlW - kitBtnW - editBtnW - 4, kitControlY, kitBtnW, kitBtnH, mouseX, mouseY);
        }
        // Edit button to open kit editor
        var editKit = editKitButton;
        if (editKit != null) {
            editKit.setEnabled(kitControlsEnabled);
            editKit.render(graphics, controlX + controlW - editBtnW, kitControlY, editBtnW, kitBtnH, mouseX, mouseY);
        }

        // === START QUEST BUTTON (bottom of right panel) ===
        int startW = controlW;
        int startH = 32;
        int startX = controlX;
        var startBtn2 = startButton;
        if (startBtn2 != null) {
            startBtn2.setEnabled(selectedQuest != null && !kitSyncInFlight);
            startBtn2.render(graphics, startX, startButtonY, startW, startH, mouseX, mouseY);
        }
        if (kitSyncInFlight) {
            var safeFont = Objects.requireNonNull(font);
            String message = I18n.translate("devmod.loading.syncing").getString();
            if (kitSyncAttempts > 1) {
                message = message + " (" + kitSyncAttempts + "/" + KIT_SYNC_MAX_ATTEMPTS + ")";
            }
            int msgY = startButtonY - (line * 2);
            drawScaledText(graphics, safeFont, message, startX, msgY, COLOR_TEXT_DIM);
            String lockedMsg = I18n.translate("devmod.endurance.quest.sync.locked").getString();
            drawScaledText(graphics, safeFont, lockedMsg, startX, startButtonY - line, COLOR_TEXT_DIM);
        } else {
            var quest = selectedQuest;
            if (quest == null) {
                var safeFont = Objects.requireNonNull(font);
                String hint = I18n.translate("devmod.endurance.quest.hint.select_mob").getString();
                drawScaledText(graphics, safeFont, hint, startX, startButtonY - line, COLOR_TEXT_DIM);
            } else {
                var safeFont = Objects.requireNonNull(font);
                String ready = I18n.translate("devmod.endurance.quest.ready").getString();
                drawScaledText(graphics, safeFont, ready, startX, startButtonY - (line * 2), COLOR_SUCCESS);
                String waveLabel = endlessMode ? "∞" : String.valueOf(questWaves);
                String kitLabel = resolveKitSummaryLabel();
                String arenaLabel = resolveArenaSummaryLabel();
                String summary = I18n.translate("devmod.endurance.quest.summary_line",
                    truncateLabel(quest.getDisplayName(), 12),
                    waveLabel,
                    truncateLabel(kitLabel, 12),
                    truncateLabel(arenaLabel, 12)).getString();
                drawScaledText(graphics, safeFont, summary, startX, startButtonY - line, COLOR_TEXT_DIM);
            }
        }

        // === SHOP BUTTON (bottom of sidebar, above search) ===
        int shopW = scaledSidebarWidth - 20;
        int shopH = 24;
        int shopX = 10;
        var safeFont = Objects.requireNonNull(font);
        int itemHeight = getSidebarItemHeight(safeFont);
        int searchBoxY = searchBox != null ? searchBox.getY() : height - 28;
        int searchLabelY = searchBoxY - (itemHeight + 2);
        int shopY = searchLabelY - shopH - 8;  // Above search label
        var shop = shopButton;
        if (shop != null) {
            shop.setEnabled(!kitSyncInFlight);
            shop.render(graphics, shopX, shopY, shopW, shopH, mouseX, mouseY);
        }
    }

    @Override
    protected boolean handleMouseClick(double mouseX, double mouseY, int button) {
        int my = (int) mouseY;

        if (showIntroOverlay) {
            var dismissBtn = introDismissButton;
            if (button == 0 && dismissBtn != null && dismissBtn.mouseClicked(mouseX, mouseY, button)) {
                dismissBtn.mouseReleased(mouseX, mouseY, button);
            }
            return true;
        }

        if (kitSyncInFlight) {
            notifyUiLocked();
            return true;
        }

        if (button == 0) {
            if (startButton != null && startButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (shopButton != null && shopButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (decreaseWaveButton != null && decreaseWaveButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (increaseWaveButton != null && increaseWaveButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (endlessToggleButton != null && endlessToggleButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (practiceToggleButton != null && practiceToggleButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (prevKitButton != null && prevKitButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (nextKitButton != null && nextKitButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (editKitButton != null && editKitButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (resetFiltersButton != null && resetFiltersButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (configButton != null && configButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (configureMobButton != null && configureMobButton.mouseClicked(mouseX, mouseY, button)) return true;
        }

        // Check sidebar clicks - but let the search box handle its own clicks first
        if (mouseX < scaledSidebarWidth) {
            // Check if click is on search box area (bottom of sidebar)
            int searchY = searchBox != null ? searchBox.getY() : height - 28;
            int searchH = searchBox != null ? searchBox.getHeight() : 18;
            if (mouseY >= searchY && mouseY <= searchY + searchH && mouseX >= 10 && mouseX <= scaledSidebarWidth - 10) {
                // Let super handle it so the EditBox can receive focus
                return false;
            }
            handleSidebarClick(my);
            return true;
        }

        // Check quest list clicks
        int listX = scaledSidebarWidth + 8;
        int listY = scaledHeaderHeight + 4;
        int listWidth = width - scaledSidebarWidth - scaledRightPanelWidth - 16;
        int listHeight = height - scaledHeaderHeight - 55;

        if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
            int relativeY = (int) mouseY - listY + scrollOffset;
            int index = relativeY / (scaledQuestCardHeight + scaledQuestCardMargin);
            if (index >= 0 && index < filteredQuests.size()) {
                var quest = filteredQuests.get(index);
                selectedQuest = quest;
                // Request arena suggestions for the selected mob
                var panel = arenaPanel;
                if (panel != null) {
                    panel.clear();
                    panel.requestSuggestions(quest.getMobId().toString());
                }
                playClickSound();
                return true;
            }
        }

        // Arena panel click handling
        var aPanel = arenaPanel;
        if (aPanel != null && aPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;

        if (showIntroOverlay) {
            if (introDismissButton != null) {
                handled = introDismissButton.mouseReleased(mouseX, mouseY, button);
            }
            return handled || super.mouseReleased(mouseX, mouseY, button);
        }

        if (startButton != null) handled |= startButton.mouseReleased(mouseX, mouseY, button);
        if (shopButton != null) handled |= shopButton.mouseReleased(mouseX, mouseY, button);
        if (decreaseWaveButton != null) handled |= decreaseWaveButton.mouseReleased(mouseX, mouseY, button);
        if (increaseWaveButton != null) handled |= increaseWaveButton.mouseReleased(mouseX, mouseY, button);
        if (endlessToggleButton != null) handled |= endlessToggleButton.mouseReleased(mouseX, mouseY, button);
        if (practiceToggleButton != null) handled |= practiceToggleButton.mouseReleased(mouseX, mouseY, button);
        if (prevKitButton != null) handled |= prevKitButton.mouseReleased(mouseX, mouseY, button);
        if (nextKitButton != null) handled |= nextKitButton.mouseReleased(mouseX, mouseY, button);
        if (editKitButton != null) handled |= editKitButton.mouseReleased(mouseX, mouseY, button);
        if (resetFiltersButton != null) handled |= resetFiltersButton.mouseReleased(mouseX, mouseY, button);
        if (configButton != null) handled |= configButton.mouseReleased(mouseX, mouseY, button);
        if (configureMobButton != null) handled |= configureMobButton.mouseReleased(mouseX, mouseY, button);

        if (handled) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void handleSidebarClick(int mouseY) {
        // Must match renderSidebar() layout exactly
        var safeFont = Objects.requireNonNull(font);
        int itemHeight = getSidebarItemHeight(safeFont);
        int searchBoxY = searchBox != null ? searchBox.getY() : height - 28;
        int searchLabelY = searchBoxY - (itemHeight + 2);
        int shopH = 24;
        int shopGap = 8;
        int shopY = searchLabelY - shopH - shopGap;
        int bottomReserved = height - shopY;
        int tiersSectionHeight = (itemHeight + 2) * (EnduranceQuestRegistry.MobTier.values().length + 2);

        int y = 8;   // Start at title position
        y += itemHeight + 4;     // After title
        y += 8;      // After divider -> "Mod Source" label
        y += itemHeight;     // After label -> first mod item

        int modsSectionTop = y;
        int modsSectionBottom = height - bottomReserved - tiersSectionHeight;

        Set<String> namespaces = EnduranceQuestRegistry.INSTANCE.getAvailableNamespaces();
        List<String> sortedNamespaces = new ArrayList<>(namespaces);
        sortedNamespaces.sort(String::compareTo);
        sortedNamespaces.add(0, ALL_NAMESPACE);

        // Check if click is in mods scrollable area
        if (mouseY >= modsSectionTop && mouseY < modsSectionBottom) {
            // Calculate which mod was clicked considering scroll offset
            int relativeY = mouseY - modsSectionTop + sidebarScrollOffset;
            int index = relativeY / itemHeight;
            if (index >= 0 && index < sortedNamespaces.size()) {
                selectedNamespace = sortedNamespaces.get(index);
                applyFilters();
                playClickSound();
                return;
            }
        }

        // Difficulty section starts after mods
        y = modsSectionBottom + 8;  // Gap
        y += itemHeight;  // "Difficulty" label

        // All tiers
        if (mouseY >= y - 1 && mouseY < y + itemHeight - 2) {
            selectedTier = null;
            applyFilters();
            playClickSound();
            return;
        }
        y += itemHeight;

        // Tier clicks
        for (EnduranceQuestRegistry.MobTier tier : EnduranceQuestRegistry.MobTier.values()) {
            if (mouseY >= y - 1 && mouseY < y + itemHeight - 2) {
                selectedTier = tier;
                applyFilters();
                playClickSound();
                return;
            }
            y += itemHeight;
        }
    }

    @Override
    protected boolean handleMouseScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showIntroOverlay) {
            return true;
        }
        if (kitSyncInFlight) {
            return true;
        }

        // Check if mouse is over sidebar (for mods scroll)
        if (mouseX < scaledSidebarWidth) {
            // Calculate mods section bounds
            var safeFont = Objects.requireNonNull(font);
            int itemHeight = getSidebarItemHeight(safeFont);
            int searchBoxY = searchBox != null ? searchBox.getY() : height - 28;
            int searchLabelY = searchBoxY - (itemHeight + 2);
            int shopH = 24;
            int shopGap = 8;
            int shopY = searchLabelY - shopH - shopGap;
            int bottomReserved = height - shopY;
            int tiersSectionHeight = (itemHeight + 2) * (EnduranceQuestRegistry.MobTier.values().length + 2);
            int modsSectionTop = 8 + (itemHeight + 4) + 8 + itemHeight; // header + gap + label
            int modsSectionBottom = height - bottomReserved - tiersSectionHeight;

            if (mouseY >= modsSectionTop && mouseY < modsSectionBottom) {
                sidebarScrollOffset = Math.max(0, Math.min(sidebarMaxScroll, sidebarScrollOffset - (int) (scrollY * 20)));
                return true;
            }
            return false;
        }

        // Check if mouse is over quest list area
        int listX = scaledSidebarWidth + 8;
        int listY = scaledHeaderHeight + 4;
        int listWidth = width - scaledSidebarWidth - scaledRightPanelWidth - 16;
        int listHeight = height - scaledHeaderHeight - 55;

        if (mouseX >= listX && mouseX <= listX + listWidth &&
            mouseY >= listY && mouseY <= listY + listHeight) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 20)));
            return true;
        }

        // Arena panel scroll handling
        if (arenaPanel != null && arenaPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }

        return false;
    }

    private void adjustWaves(int delta) {
        if (endlessMode) {
            return;
        }
        int previous = questWaves;
        questWaves = Math.max(1, Math.min(50, questWaves + delta));
        if (questWaves == previous && delta != 0) {
            String messageKey = questWaves <= 1
                ? "devmod.endurance.quest.notify.waves_min"
                : "devmod.endurance.quest.notify.waves_max";
            Notification notification = Notification.builder(NotificationCategory.SYSTEM)
                .titleKey("devmod.endurance.quest.notify.waves_title")
                .messageKey(messageKey)
                .priority(NotificationPriority.LOW)
                .displayDurationMs(1200)
                .build();
            ClientNotificationManager.INSTANCE.handleNotification(notification);
        }
    }

    private void setEndlessMode(boolean enabled) {
        if (endlessMode == enabled) {
            return;
        }
        endlessMode = enabled;
        Notification notification = Notification.builder(NotificationCategory.QUEST)
            .titleKey("devmod.endurance.quest.notify.endless.title")
            .messageKey(enabled
                ? "devmod.endurance.quest.notify.endless.on"
                : "devmod.endurance.quest.notify.endless.off")
            .priority(NotificationPriority.LOW)
            .displayDurationMs(1600)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }

    private void setPracticeMode(boolean enabled) {
        if (practiceMode == enabled) {
            return;
        }
        practiceMode = enabled;
        Notification notification = Notification.builder(NotificationCategory.QUEST)
            .titleKey("devmod.endurance.quest.notify.practice.title")
            .messageKey(enabled
                ? "devmod.endurance.quest.notify.practice.on"
                : "devmod.endurance.quest.notify.practice.off")
            .priority(NotificationPriority.LOW)
            .displayDurationMs(1600)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }

    private void notifyUiLocked() {
        long now = System.currentTimeMillis();
        if (now - lastUiLockNoticeAt < UI_LOCK_NOTICE_COOLDOWN_MS) {
            return;
        }
        lastUiLockNoticeAt = now;
        Notification notification = Notification.builder(NotificationCategory.SYSTEM)
            .titleKey("devmod.endurance.quest.notify.locked.title")
            .messageKey("devmod.endurance.quest.notify.locked.message")
            .priority(NotificationPriority.LOW)
            .displayDurationMs(1600)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }

    private void notifyStartRequested(EnduranceQuestRegistry.MobQuestConfig quest, boolean syncingKit) {
        if (quest == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastStartNoticeAt < START_NOTICE_COOLDOWN_MS) {
            return;
        }
        lastStartNoticeAt = now;
        String messageKey = syncingKit
            ? "devmod.endurance.quest.notify.start.syncing"
            : "devmod.endurance.quest.notify.start.sent";
        Notification notification = Notification.builder(NotificationCategory.QUEST)
            .titleKey("devmod.endurance.quest.notify.start.title")
            .messageKey(messageKey)
            .param("mob", quest.getDisplayName())
            .priority(NotificationPriority.NORMAL)
            .displayDurationMs(2200)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }

    private void notifyKitSelected() {
        long now = System.currentTimeMillis();
        if (now - lastKitNoticeAt < KIT_CHANGE_NOTICE_COOLDOWN_MS) {
            return;
        }
        lastKitNoticeAt = now;
        String kitLabel = resolveKitSummaryLabel();
        Notification notification = Notification.builder(NotificationCategory.QUEST)
            .titleKey("devmod.endurance.quest.notify.kit.title")
            .messageKey("devmod.endurance.quest.notify.kit.changed")
            .param("kit", kitLabel != null ? kitLabel : "")
            .priority(NotificationPriority.LOW)
            .displayDurationMs(1400)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }

    private String resolveKitSummaryLabel() {
        if (selectedCustomKit != null) {
            return selectedCustomKit.getName();
        }
        if (usingCustomKit && customKitName != null && !customKitName.isBlank()) {
            return customKitName;
        }
        return selectedKit.getDisplayName();
    }

    private String resolveArenaSummaryLabel() {
        var panel = arenaPanel;
        if (panel == null) {
            return I18n.translate("devmod.endurance.quest.arena.auto").getString();
        }
        if (panel.isAutoSelected()) {
            return I18n.translate("devmod.endurance.quest.arena.auto").getString();
        }
        String label = panel.getSelectedTemplateLabel();
        if (label == null || label.isBlank()) {
            return I18n.translate("devmod.endurance.quest.arena.auto").getString();
        }
        return label;
    }

    private String truncateLabel(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 2)) + "..";
    }

    private String[] getIntroLines() {
        return new String[] {
            I18n.translate("devmod.endurance.quest.intro.line1").getString(),
            "",
            I18n.translate("devmod.endurance.quest.intro.line2").getString(),
            I18n.translate("devmod.endurance.quest.intro.line3").getString(),
            I18n.translate("devmod.endurance.quest.intro.line4").getString(),
            I18n.translate("devmod.endurance.quest.intro.line5").getString(),
            I18n.translate("devmod.endurance.quest.intro.line6").getString(),
            I18n.translate("devmod.endurance.quest.intro.line7").getString(),
            "",
            I18n.translate("devmod.endurance.quest.intro.tip").getString()
        };
    }

    private String getTierDisplayName(EnduranceQuestRegistry.MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> I18n.translate("devmod.endurance.quest.tier.trivial").getString();
            case EASY -> I18n.translate("devmod.endurance.quest.tier.easy").getString();
            case MEDIUM -> I18n.translate("devmod.endurance.quest.tier.medium").getString();
            case HARD -> I18n.translate("devmod.endurance.quest.tier.hard").getString();
            case ELITE -> I18n.translate("devmod.endurance.quest.tier.elite").getString();
            case BOSS -> I18n.translate("devmod.endurance.quest.tier.boss").getString();
        };
    }

    private String getTierShortLabel(EnduranceQuestRegistry.MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> I18n.translate("devmod.endurance.quest.tier.trivial.short").getString();
            case EASY -> I18n.translate("devmod.endurance.quest.tier.easy.short").getString();
            case MEDIUM -> I18n.translate("devmod.endurance.quest.tier.medium.short").getString();
            case HARD -> I18n.translate("devmod.endurance.quest.tier.hard.short").getString();
            case ELITE -> I18n.translate("devmod.endurance.quest.tier.elite.short").getString();
            case BOSS -> I18n.translate("devmod.endurance.quest.tier.boss.short").getString();
        };
    }

    private void startSelectedQuest() {
        var currentQuest = selectedQuest;
        if (currentQuest == null) {
            // Show error feedback using base class status message system
            showError(I18n.translate("devmod.endurance.quest.error.select_mob").getString());
            return;
        }

        if (kitSyncInFlight) {
            notifyUiLocked();
            return;
        }

        playClickSound();

        // Send packet to server to start quest
        // Determine kit ID based on selection type
        String kitId;
        if (selectedCustomKit != null) {
            // Saved custom kit - send its 8-char ID
            kitId = selectedCustomKit.getId();
        } else if (usingCustomKit && KitManager.INSTANCE.hasTemporaryKit()) {
            // On-the-fly temporary kit
            kitId = "TEMPORARY";
        } else {
            // Preset kit
            kitId = selectedKit.name();
        }

        int wavesToSend = endlessMode ? 0 : questWaves;

        // Get template override from arena panel (null if using auto-selection)
        String templateOverride = arenaPanel != null ? arenaPanel.getOverrideTemplateId() : null;

        LOGGER.info("[EnduranceQuest] Starting quest: {} with {} waves, endless={}, kit={}, template={}, practice={}",
            currentQuest.getDisplayName(), wavesToSend, endlessMode, kitId,
            templateOverride != null ? templateOverride : "auto", practiceMode);

        StartQuestPayload payload = new StartQuestPayload(
            currentQuest.getMobId().toString(),
            wavesToSend,
            endlessMode,
            kitId,
            templateOverride,
            practiceMode
        );

        boolean needsSync = requiresKitSync();
        if (needsSync) {
            if (beginKitSync(payload)) {
                notifyStartRequested(currentQuest, true);
            }
            return;
        }

        sendStartQuest(payload);
        notifyStartRequested(currentQuest, false);
    }

    private boolean requiresKitSync() {
        if (selectedCustomKit != null) {
            return true;
        }
        return usingCustomKit && KitManager.INSTANCE.hasTemporaryKit();
    }

    private boolean beginKitSync(StartQuestPayload payload) {
        KitSyncPayload kitPayload = buildKitSyncPayload();
        if (kitPayload == null) {
            pendingStartPayload = null;
            pendingKitId = null;
            showError("Kit sync failed");
            return false;
        }
        pendingStartPayload = payload;
        pendingKitId = payload.kitId();
        pendingKitSyncPayload = kitPayload;
        kitSyncInFlight = true;
        kitSyncAttempts = 1;
        kitSyncStartTime = System.currentTimeMillis();
        sendKitSyncPayload(kitPayload);
        return true;
    }

    private void sendStartQuest(StartQuestPayload payload) {
        ActionRegistry.invoke(ActionIds.ENDURANCE_QUEST_START,
            ClientActionContexts.forClient(ActionOrigin.UI, payload));
    }

    @Nullable
    private KitSyncPayload buildKitSyncPayload() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var level = mc.level;
        if (level == null) {
            return null;
        }
        var registryAccess = level.registryAccess();

        if (selectedCustomKit != null) {
            List<ItemStack> stacks = selectedCustomKit.toItemStacks(registryAccess);
            if (stacks.size() > KitSyncPayload.MAX_ITEMS) {
                showError("Kit too large (max " + KitSyncPayload.MAX_ITEMS + " items)");
                return null;
            }
            return KitSyncPayload.fromCustomKit(selectedCustomKit, registryAccess);
        }

        if (usingCustomKit && KitManager.INSTANCE.hasTemporaryKit()) {
            String kitName = KitManager.INSTANCE.getTemporaryKitName();
            List<ItemStack> items = KitManager.INSTANCE.getTemporaryKitItems();
            if (items.size() > KitSyncPayload.MAX_ITEMS) {
                showError("Kit too large (max " + KitSyncPayload.MAX_ITEMS + " items)");
                return null;
            }
            return KitSyncPayload.fromTemporary(
                kitName != null ? kitName : "Temporary Kit",
                items,
                registryAccess
            );
        }
        return null;
    }

    private void sendKitSyncPayload(KitSyncPayload payload) {
        PacketDistributor.sendToServer(Objects.requireNonNull(payload));
    }

    public void onKitSyncConfirm(KitSyncConfirmPayload payload) {
        if (!kitSyncInFlight) {
            return;
        }

        String kitId = payload != null ? payload.kitId() : "";
        String pending = this.pendingKitId;
        if (pending != null && !pending.isBlank()
            && kitId != null && !kitId.isBlank()
            && !pending.equals(kitId)) {
            return;
        }

        kitSyncInFlight = false;
        kitSyncAttempts = 0;
        pendingKitId = null;
        pendingKitSyncPayload = null;

        if (payload == null || !payload.success()) {
            pendingStartPayload = null;
            String message = payload != null && payload.message() != null && !payload.message().isBlank()
                ? payload.message()
                : "Kit sync failed";
            showError(message);
            Notification notification = Notification.builder(NotificationCategory.SYSTEM)
                .titleKey("devmod.kit.sync.title")
                .messageKey("devmod.kit.sync.failure")
                .param("reason", message)
                .priority(NotificationPriority.HIGH)
                .displayDurationMs(3500)
                .build();
            ClientNotificationManager.INSTANCE.handleNotification(notification);
            return;
        }

        String kitName = null;
        CustomKit resolvedCustom = selectedCustomKit;
        if (resolvedCustom != null) {
            kitName = resolvedCustom.getName();
        } else if (usingCustomKit) {
            kitName = customKitName;
        }
        if (kitName == null || kitName.isBlank()) {
            kitName = payload.kitId();
        }
        if (kitName == null || kitName.isBlank()) {
            kitName = "Kit";
        }
        Notification successToast = Notification.builder(NotificationCategory.SYSTEM)
            .titleKey("devmod.kit.sync.title")
            .messageKey("devmod.kit.sync.success")
            .param("name", kitName)
            .priority(NotificationPriority.NORMAL)
            .displayDurationMs(2200)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(successToast);

        StartQuestPayload queued = pendingStartPayload;
        pendingStartPayload = null;
        if (queued != null) {
            sendStartQuest(queued);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (kitSyncInFlight && System.currentTimeMillis() - kitSyncStartTime > KIT_SYNC_TIMEOUT_MS) {
            if (kitSyncAttempts < KIT_SYNC_MAX_ATTEMPTS) {
                kitSyncAttempts++;
                kitSyncStartTime = System.currentTimeMillis();
                KitSyncPayload payload = pendingKitSyncPayload;
                if (payload != null) {
                    sendKitSyncPayload(payload);
                    return;
                }
            }
            kitSyncInFlight = false;
            kitSyncAttempts = 0;
            pendingStartPayload = null;
            pendingKitId = null;
            pendingKitSyncPayload = null;
            String message = "Kit sync timeout. Retry.";
            showError(message);
            Notification notification = Notification.builder(NotificationCategory.SYSTEM)
                .titleKey("devmod.kit.sync.title")
                .messageKey("devmod.kit.sync.timeout")
                .priority(NotificationPriority.HIGH)
                .displayDurationMs(3500)
                .build();
            ClientNotificationManager.INSTANCE.handleNotification(notification);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        // Cleanup arena panel listener
        if (arenaPanel != null) {
            arenaPanel.cleanup();
        }
        kitSyncInFlight = false;
        kitSyncAttempts = 0;
        pendingStartPayload = null;
        pendingKitId = null;
        pendingKitSyncPayload = null;
    }
}
