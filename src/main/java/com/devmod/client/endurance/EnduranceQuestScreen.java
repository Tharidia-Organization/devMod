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

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.KitManager;
import com.devmod.endurance.KitPreset;
import com.devmod.endurance.PersonalRecordsSyncPayload;
import com.devmod.endurance.RequestPersonalRecordsPayload;
import com.devmod.endurance.StartQuestPayload;
import com.devmod.util.I18n;

/**
 * Main UI screen for browsing and starting Endurance Quests.
 */
@OnlyIn(Dist.CLIENT)
public class EnduranceQuestScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceQuestScreen.class);

    // Layout constants - using UIConstants for consistency
    private static final int SIDEBAR_WIDTH = UIConstants.Size.SIDEBAR_WIDTH_NARROW;  // Slightly narrower for more content space
    private static final int HEADER_HEIGHT = 50;   // Taller for better separation
    private static final int RIGHT_PANEL_WIDTH = 260;  // Wider for settings
    private static final int QUEST_CARD_HEIGHT = 72;   // Compact cards
    private static final int QUEST_CARD_MARGIN = 6;

    // Colors - standardized to UIConstants
    private static final int COLOR_BG = UIConstants.Background.PANEL();
    private static final int COLOR_SIDEBAR_BG = UIConstants.Background.HEADER();
    private static final int COLOR_CARD_BG = UIConstants.Background.INPUT();
    private static final int COLOR_CARD_HOVER = UIConstants.Background.HOVER();
    private static final int COLOR_CARD_SELECTED = UIConstants.Background.ACTIVE();
    private static final int COLOR_ACCENT = UIConstants.Border.DEFAULT();  // Blue instead of pink
    private static final int COLOR_TEXT = UIConstants.Text.PRIMARY();
    private static final int COLOR_TEXT_DIM = UIConstants.Text.SECONDARY();
    private static final int COLOR_SUCCESS = UIConstants.Accent.GREEN();
    private static final int COLOR_WARNING = UIConstants.Accent.GOLD();
    private static final int COLOR_DANGER = UIConstants.Accent.RED();

    // Tier colors - using UIConstants where applicable
    private static final Map<EnduranceQuestRegistry.MobTier, Integer> TIER_COLORS = Map.of(
        EnduranceQuestRegistry.MobTier.TRIVIAL, UIConstants.Text.MUTED(),
        EnduranceQuestRegistry.MobTier.EASY, UIConstants.Accent.GREEN(),
        EnduranceQuestRegistry.MobTier.MEDIUM, UIConstants.Accent.GOLD(),
        EnduranceQuestRegistry.MobTier.HARD, UIConstants.Accent.ORANGE(),
        EnduranceQuestRegistry.MobTier.ELITE, UIConstants.Accent.PURPLE(),
        EnduranceQuestRegistry.MobTier.BOSS, UIConstants.Accent.RED()
    );

    // State
    private List<EnduranceQuestRegistry.MobQuestConfig> allQuests = new ArrayList<>();
    private List<EnduranceQuestRegistry.MobQuestConfig> filteredQuests = new ArrayList<>();
    @Nullable
    private EnduranceQuestRegistry.MobQuestConfig selectedQuest = null;

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
    private EditorButton introDismissButton;

    // Quest settings
    private int questWaves = 10;
    private boolean endlessMode = false;
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

    // Reset filters button
    @Nullable
    private EditorButton resetFiltersButton;

    // Config button for game mechanics settings
    @Nullable
    private EditorButton configButton;

    // Pre-selection from QuickTestWizard
    @Nullable
    private net.minecraft.resources.ResourceLocation preselectedMob = null;
    private boolean hasPreselection = false;

    // Intro overlay for first-time users
    private boolean showIntroOverlay = false;

    // Error feedback for no selection
    @Nullable
    private String errorMessage = null;
    private long errorMessageTime = 0;
    private static final long ERROR_DISPLAY_DURATION = 3000; // 3 seconds

    public EnduranceQuestScreen() {
        super(I18n.screenTitle("endurance_quests"));
    }

    /**
     * Constructor with pre-selected mob and settings from QuickTestWizard.
     *
     * @param mobId Pre-selected mob ID
     * @param waves Number of waves (0 for endless)
     */
    public EnduranceQuestScreen(@javax.annotation.Nullable net.minecraft.resources.ResourceLocation mobId, int waves) {
        super(I18n.screenTitle("endurance_quests"));
        this.preselectedMob = mobId;
        this.questWaves = waves > 0 ? waves : 10;
        this.endlessMode = waves <= 0;
        this.hasPreselection = mobId != null;
    }

    @Override
    protected void init() {
        super.init();

        // Notify onboarding overlay that Endurance Quest was opened
        com.devmod.client.overlay.OnboardingOverlay.onEnduranceQuestOpened();

        // Request personal records from server
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new RequestPersonalRecordsPayload());

        // Check if first time opening Endurance Quest
        if (!SettingsManager.INSTANCE.getSettings().onboarding.hasSeenEnduranceIntro) {
            showIntroOverlay = true;
        }

        // Load quests
        loadQuests();

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
        addRenderableWidget(Objects.requireNonNull(searchBox));

        // All buttons are now rendered custom via renderActionButtons()
        initButtons();
    }

    private void initButtons() {
        startButton = EditorButton.builder("start-quest", I18n.ui("start_quest").getString())
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.LARGE)
            .onClick(this::startSelectedQuest)
            .build();

        shopButton = EditorButton.builder("open-shop", I18n.ui("shop").getString())
            .style(EditorButton.Style.PRIMARY)
            .accent(UIConstants.Accent.GOLD())
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
            .onToggle(enabled -> endlessMode = enabled)
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
    }

    private void openConfigScreen() {
        net.minecraft.client.Minecraft.getInstance().setScreen(
            new EnduranceSettingsScreen(this)
        );
    }

    private void resetFilters() {
        searchQuery = "";
        selectedNamespace = ALL_NAMESPACE;
        selectedTier = null;
        if (searchBox != null) {
            searchBox.setValue("");
        }
        applyFilters();
    }

    private void prevKit() {
        KitPreset[] kits = KitPreset.values();
        int index = selectedKit.ordinal();
        index = (index - 1 + kits.length) % kits.length;
        selectedKit = kits[index];
        usingCustomKit = false;
        customKitName = null;
    }

    private void nextKit() {
        KitPreset[] kits = KitPreset.values();
        int index = selectedKit.ordinal();
        index = (index + 1) % kits.length;
        selectedKit = kits[index];
        usingCustomKit = false;
        customKitName = null;
    }

    private void openKitEditor() {
        net.minecraft.client.Minecraft.getInstance().setScreen(
            new KitSelectionScreen(this, items -> {
                if (items != null && !items.isEmpty()) {
                    usingCustomKit = true;
                    customKitName = KitManager.INSTANCE.getTemporaryKitName();
                } else {
                    usingCustomKit = false;
                    customKitName = null;
                }
            })
        );
    }

    private void openShop() {
        ActionRegistry.invoke(ActionIds.UI_ENDURANCE_SHOP_OPEN,
            ClientActionContexts.forClient(ActionOrigin.UI));
    }

    private void loadQuests() {
        allQuests = new ArrayList<>(EnduranceQuestRegistry.INSTANCE.getAllMobConfigs());
        applyFilters();

        // Apply pre-selection from QuickTestWizard if present
        if (hasPreselection && preselectedMob != null) {
            for (var quest : filteredQuests) {
                if (quest.mobId.equals(preselectedMob)) {
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
                q.displayName.toLowerCase(Locale.ROOT).contains(queryLower) ||
                q.mobId.toString().toLowerCase(Locale.ROOT).contains(queryLower))
            .filter(q -> selectedNamespace.equals(ALL_NAMESPACE) || q.namespace.equals(selectedNamespace))
            .filter(q -> selectedTier == null || q.tier == selectedTier)
            .sorted(Comparator.comparing(q -> q.displayName))
            .collect(Collectors.toList());

        scrollOffset = 0;
        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        int contentHeight = filteredQuests.size() * (QUEST_CARD_HEIGHT + QUEST_CARD_MARGIN);
        int viewportHeight = height - HEADER_HEIGHT - 55;
        maxScroll = Math.max(0, contentHeight - viewportHeight);
    }

    @Override
    public void render(@javax.annotation.Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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
        renderRightPanel(graphics);

        // Header (center top)
        renderHeader(graphics);

        // Quest list (center)
        renderQuestList(graphics, mouseX, mouseY);

        // Custom action buttons (rendered after panels to be on top)
        renderActionButtons(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Error message feedback (rendered on top of buttons)
        if (errorMessage != null) {
            long elapsed = System.currentTimeMillis() - errorMessageTime;
            if (elapsed < ERROR_DISPLAY_DURATION) {
                // Fade out effect
                float alpha = 1.0f - (elapsed / (float) ERROR_DISPLAY_DURATION);
                int alphaInt = (int) (alpha * 255);

                // Draw error message near the Start Quest button
                var safeFont = Objects.requireNonNull(font);
                int msgWidth = safeFont.width(Objects.requireNonNull(errorMessage));
                int msgX;
                int msgY;
                var startBtn = startButton;
                if (startBtn != null && !startBtn.getBounds().isEmpty()) {
                    var bounds = startBtn.getBounds();
                    msgX = bounds.centerX() - msgWidth / 2;
                    msgY = bounds.y() - 18;
                } else {
                    msgX = width - 130 + 60 - msgWidth / 2; // Fallback
                    msgY = height - 60;
                }

                // Background (darker version of danger color)
                int bgColor = UIConstants.setAlpha(COLOR_DANGER, alphaInt / 3);
                graphics.fill(msgX - 6, msgY - 3, msgX + msgWidth + 6, msgY + 12, bgColor);
                // Border (full danger color with alpha)
                int borderColor = UIConstants.setAlpha(COLOR_DANGER, alphaInt);
                graphics.fill(msgX - 6, msgY - 3, msgX + msgWidth + 6, msgY - 2, borderColor);
                // Text
                graphics.drawString(safeFont, Objects.requireNonNull(errorMessage), msgX, msgY, (alphaInt << 24) | 0xFFFFFF);
            } else {
                errorMessage = null;
            }
        }
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
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, UIConstants.Background.PANEL_SOLID());

        int y = panelY + 15;
        int centerX = panelX + panelW / 2;

        // Title
        graphics.drawCenteredString(safeFont, Objects.requireNonNull(I18n.translate("devmod.endurance.quest.intro.title").getString()),
            centerX, y, 0xFFFFFFFF);
        y += 25;

        // Separator
        graphics.fill(panelX + 20, y, panelX + panelW - 20, y + 1, UIConstants.Border.SEPARATOR());
        y += 15;

        // Description
        String[] lines = getIntroLines();

        for (String line : lines) {
            graphics.drawString(safeFont, line, panelX + 25, y, 0xFFFFFFFF, false);
            y += 12;
        }

        // Button area
        y = panelY + panelH - 40;
        int btnW = UIConstants.Size.BUTTON_WIDTH_SMALL + 20;
        int btnX = centerX - btnW / 2;
        int btnH = UIConstants.Size.BUTTON_HEIGHT_PROMINENT;
        if (introDismissButton != null) {
            introDismissButton.render(graphics, btnX, y, btnW, btnH, mouseX, mouseY);
        }
    }

    /**
     * Dismiss intro overlay and save preference.
     */
    private void dismissIntroOverlay() {
        showIntroOverlay = false;
        SettingsManager.INSTANCE.getSettings().onboarding.hasSeenEnduranceIntro = true;
        SettingsManager.INSTANCE.markDirty();
        SettingsManager.INSTANCE.save();
    }

    private void renderSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);

        // Sidebar background
        graphics.fill(0, 0, SIDEBAR_WIDTH, height, COLOR_SIDEBAR_BG);
        // Right border accent
        graphics.fill(SIDEBAR_WIDTH - 2, 0, SIDEBAR_WIDTH, height, COLOR_ACCENT);

        int y = 8;

        // === HEADER: Title with reset button ===
        graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.filters.title").getString(), 10, y, COLOR_TEXT);

        // Reset button (only show if any filter is active)
        boolean hasActiveFilters = selectedTier != null || !selectedNamespace.equals(ALL_NAMESPACE) || !searchQuery.isEmpty();
        var resetBtn = resetFiltersButton;
        if (hasActiveFilters && resetBtn != null) {
            int btnW = 40;
            int btnH = 14;
            int btnX = SIDEBAR_WIDTH - btnW - 12;
            resetBtn.render(graphics, btnX, y - 1, btnW, btnH, mouseX, mouseY);
        }
        y += 20;

        // Divider
        graphics.fill(8, y, SIDEBAR_WIDTH - 10, y + 1, UIConstants.Border.SEPARATOR());
        y += 8;

        // Calculate layout bounds
        int bottomReserved = 95; // Shop button + Search box + labels
        int tiersSectionHeight = 120; // Fixed height for tiers (7 items * ~14px + header)
        int modsSectionTop = y;
        int modsSectionBottom = height - bottomReserved - tiersSectionHeight;
        int modsViewportHeight = modsSectionBottom - modsSectionTop - 20; // -20 for header

        // === SECTION: Mod Filters (scrollable) ===
        graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.filters.mod_source").getString(), 10, y, COLOR_TEXT_DIM);
        y += 14;

        Set<String> namespaces = EnduranceQuestRegistry.INSTANCE.getAvailableNamespaces();
        List<String> sortedNamespaces = new ArrayList<>(namespaces);
        sortedNamespaces.sort(String::compareTo);
        sortedNamespaces.add(0, ALL_NAMESPACE);

        // Calculate total content height and max scroll
        int modsContentHeight = sortedNamespaces.size() * 14;
        sidebarMaxScroll = Math.max(0, modsContentHeight - modsViewportHeight);

        // Clamp scroll offset
        sidebarScrollOffset = Math.max(0, Math.min(sidebarMaxScroll, sidebarScrollOffset));

        // Scissor for mod list clipping
        int clipTop = y;
        int clipBottom = modsSectionBottom;
        graphics.enableScissor(0, clipTop, SIDEBAR_WIDTH - 4, clipBottom);

        int modY = y - sidebarScrollOffset;
        for (String ns : sortedNamespaces) {
            // Skip items above viewport
            if (modY + 14 < clipTop) {
                modY += 14;
                continue;
            }
            // Stop if below viewport
            if (modY > clipBottom) break;

            boolean isSelected = ns.equals(selectedNamespace);
            boolean isHovered = mouseX >= 8 && mouseX <= SIDEBAR_WIDTH - 10
                && mouseY >= Math.max(clipTop, modY) && mouseY < Math.min(clipBottom, modY + 13);

            // Selection/hover background
            if (isSelected) {
                graphics.fill(8, modY - 1, SIDEBAR_WIDTH - 14, modY + 12, COLOR_ACCENT);
            } else if (isHovered) {
                graphics.fill(8, modY - 1, SIDEBAR_WIDTH - 14, modY + 12, UIConstants.Background.HOVER());
            }

            String displayName = ns.equals(ALL_NAMESPACE)
                ? I18n.translate("devmod.endurance.quest.filters.all").getString()
                : ns;
            if (displayName.length() > 12) {
                displayName = displayName.substring(0, 10) + "..";
            }
            long count = ns.equals(ALL_NAMESPACE) ? allQuests.size() :
                allQuests.stream().filter(q -> q.namespace.equals(ns)).count();

            int textColor = isSelected ? 0xFFFFFFFF : COLOR_TEXT;
            graphics.drawString(safeFont, displayName, 12, modY, textColor);
            String countStr = Objects.requireNonNull(String.valueOf(count));
            int countW = safeFont.width(countStr);
            graphics.drawString(safeFont, "§8" + countStr, SIDEBAR_WIDTH - countW - 18, modY, COLOR_TEXT_DIM);

            modY += 14;
        }

        graphics.disableScissor();

        // Scrollbar for mods section
        if (sidebarMaxScroll > 0) {
            int scrollbarX = SIDEBAR_WIDTH - 6;
            int scrollbarH = Math.max(15, (int) ((float) modsViewportHeight / modsContentHeight * modsViewportHeight));
            int scrollbarY = clipTop + (int) ((float) sidebarScrollOffset / sidebarMaxScroll * (modsViewportHeight - scrollbarH));
            graphics.fill(scrollbarX, clipTop, scrollbarX + 3, clipBottom, UIConstants.Background.INPUT());
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarH, COLOR_ACCENT);
        }

        y = modsSectionBottom + 8;

        // === SECTION: Difficulty Filters (fixed) ===
        graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.filters.difficulty").getString(), 10, y, COLOR_TEXT_DIM);
        y += 14;

        // All tiers option
        boolean allTiersSelected = selectedTier == null;
        boolean allTiersHovered = mouseX >= 8 && mouseX <= SIDEBAR_WIDTH - 10 && mouseY >= y && mouseY < y + 13;
        if (allTiersSelected) {
            graphics.fill(8, y - 1, SIDEBAR_WIDTH - 14, y + 12, COLOR_ACCENT);
        } else if (allTiersHovered) {
            graphics.fill(8, y - 1, SIDEBAR_WIDTH - 14, y + 12, UIConstants.Background.HOVER());
        }
        graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.filters.all").getString(),
            12, y, allTiersSelected ? 0xFFFFFFFF : COLOR_TEXT);
        y += 14;

        for (EnduranceQuestRegistry.MobTier tier : EnduranceQuestRegistry.MobTier.values()) {
            boolean isSelected = tier == selectedTier;
            boolean isHovered = mouseX >= 8 && mouseX <= SIDEBAR_WIDTH - 10 && mouseY >= y && mouseY < y + 13;

            int tierColor = Objects.requireNonNull(TIER_COLORS.get(tier));
            if (isSelected) {
                graphics.fill(8, y - 1, SIDEBAR_WIDTH - 14, y + 12, tierColor);
            } else if (isHovered) {
                graphics.fill(8, y - 1, SIDEBAR_WIDTH - 14, y + 12, UIConstants.Background.HOVER());
            }

            long count = allQuests.stream().filter(q -> q.tier == tier).count();
            int textColor = isSelected ? 0xFFFFFFFF : tierColor;
            graphics.drawString(safeFont, getTierDisplayName(tier), 12, y, textColor);
            String countStr = Objects.requireNonNull(String.valueOf(count));
            int countW = safeFont.width(countStr);
            graphics.drawString(safeFont, "§8" + countStr, SIDEBAR_WIDTH - countW - 18, y, COLOR_TEXT_DIM);

            y += 14;
        }

        // === BOTTOM AREA: Shop button label + Search ===
        int searchLabelY = height - 45;
        graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.filters.search_label").getString(),
            10, searchLabelY, COLOR_TEXT_DIM);
    }


    private void renderHeader(GuiGraphics graphics) {
        var safeFont = Objects.requireNonNull(font);
        int headerX = SIDEBAR_WIDTH;
        int headerW = width - SIDEBAR_WIDTH - RIGHT_PANEL_WIDTH;

        // Header background with subtle gradient effect
        graphics.fill(headerX, 0, headerX + headerW, HEADER_HEIGHT, COLOR_SIDEBAR_BG);
        // Bottom accent line
        graphics.fill(headerX, HEADER_HEIGHT - 2, headerX + headerW, HEADER_HEIGHT, COLOR_ACCENT);

        // Title - larger and bolder looking
        String title = I18n.translate("devmod.endurance.quest.header.title").getString();
        graphics.drawString(safeFont, title, headerX + 15, 8, COLOR_TEXT);

        // Filter status bar (below title)
        int filterY = 24;
        boolean hasActiveFilters = selectedTier != null || !selectedNamespace.equals(ALL_NAMESPACE) || !searchQuery.isEmpty();

        if (hasActiveFilters) {
            // Build filter tags
            StringBuilder tags = new StringBuilder();
            if (selectedTier != null) {
                tags.append("§7[§f").append(getTierDisplayName(selectedTier)).append("§7] ");
            }
            if (!selectedNamespace.equals(ALL_NAMESPACE)) {
                tags.append("§7[§f").append(selectedNamespace).append("§7] ");
            }
            if (!searchQuery.isEmpty()) {
                tags.append("§7[§f\"").append(searchQuery).append("\"§7]");
            }

            String filterLine = I18n.translate("devmod.endurance.quest.header.filter_line",
                filteredQuests.size(), allQuests.size(), tags.toString()).getString();
            graphics.drawString(safeFont, filterLine, headerX + 15, filterY, COLOR_TEXT);
        } else {
            String countLine = I18n.translate("devmod.endurance.quest.header.count_line", filteredQuests.size()).getString();
            graphics.drawString(safeFont, countLine, headerX + 15, filterY, COLOR_TEXT);
        }

        // Results count badge (right side of header)
        String countBadge = Objects.requireNonNull(String.valueOf(filteredQuests.size()));
        int badgeW = safeFont.width(countBadge) + 12;
        int badgeX = headerX + headerW - badgeW - 15;
        int badgeY = 12;

        // Badge background
        int badgeColor = hasActiveFilters ? COLOR_WARNING : COLOR_SUCCESS;
        graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 16, badgeColor);
        graphics.drawCenteredString(safeFont, Objects.requireNonNull(countBadge), badgeX + badgeW / 2, badgeY + 4, 0xFFFFFFFF);
    }

    private void renderQuestList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = SIDEBAR_WIDTH + 8;
        int listY = HEADER_HEIGHT + 4;
        int listWidth = width - SIDEBAR_WIDTH - RIGHT_PANEL_WIDTH - 16;
        int listHeight = height - HEADER_HEIGHT - 55;  // Leave space for bottom bar

        // Account for scrollbar width to prevent content shifting
        boolean hasScrollbar = maxScroll > 0;
        int effectiveListWidth = hasScrollbar ? listWidth - 8 : listWidth;

        // Clip area
        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int y = listY - scrollOffset;
        for (EnduranceQuestRegistry.MobQuestConfig quest : filteredQuests) {
            if (y + QUEST_CARD_HEIGHT > listY && y < listY + listHeight) {
                renderQuestCard(graphics, quest, listX, y, effectiveListWidth, mouseX, mouseY);
            }
            y += QUEST_CARD_HEIGHT + QUEST_CARD_MARGIN;
        }

        graphics.disableScissor();

        // Scrollbar
        if (hasScrollbar) {
            int scrollbarHeight = (int) ((float) listHeight / (listHeight + maxScroll) * listHeight);
            int scrollbarY = listY + (int) ((float) scrollOffset / maxScroll * (listHeight - scrollbarHeight));
            graphics.fill(listX + listWidth - 5, listY, listX + listWidth, listY + listHeight, UIConstants.Border.SEPARATOR());
            graphics.fill(listX + listWidth - 5, scrollbarY, listX + listWidth, scrollbarY + scrollbarHeight, COLOR_ACCENT);
        }
    }

    private void renderQuestCard(GuiGraphics graphics, EnduranceQuestRegistry.MobQuestConfig quest,
                                  int x, int y, int width, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY < y + QUEST_CARD_HEIGHT;
        boolean isSelected = quest == selectedQuest;

        // Card background with subtle border effect
        int bgColor = isSelected ? COLOR_CARD_SELECTED : (isHovered ? COLOR_CARD_HOVER : COLOR_CARD_BG);
        graphics.fill(x, y, x + width, y + QUEST_CARD_HEIGHT, bgColor);

        // Left tier indicator (thicker for selected)
        int tierColor = Objects.requireNonNull(TIER_COLORS.get(quest.tier));
        int indicatorWidth = isSelected ? 5 : 3;
        graphics.fill(x, y, x + indicatorWidth, y + QUEST_CARD_HEIGHT, tierColor);

        // Selection border
        if (isSelected) {
            graphics.fill(x, y, x + width, y + 1, tierColor);
            graphics.fill(x, y + QUEST_CARD_HEIGHT - 1, x + width, y + QUEST_CARD_HEIGHT, tierColor);
            graphics.fill(x + width - 1, y, x + width, y + QUEST_CARD_HEIGHT, tierColor);
        }

        int contentX = x + indicatorWidth + 8;

        // Row 1: Mob name + Tier badge
        graphics.drawString(safeFont, quest.displayName, contentX, y + 6, COLOR_TEXT);

        // Compact tier badge (pill style)
        String tierText = Objects.requireNonNull(getTierShortLabel(quest.tier));
        int tierWidth = safeFont.width(tierText) + 6;
        int tierBadgeX = x + width - tierWidth - 6;
        graphics.fill(tierBadgeX - 1, y + 4, tierBadgeX + tierWidth + 1, y + 16, tierColor);
        graphics.drawString(safeFont, tierText, tierBadgeX + 3, y + 6, 0xFFFFFFFF);

        // Row 2: Namespace (mod name) - smaller and dimmer
        graphics.drawString(safeFont, "§8" + quest.namespace, contentX, y + 20, COLOR_TEXT_DIM);

        // Row 3: Stats (HP | DMG) - compact format
        var actualStats = EnduranceQuestRegistry.INSTANCE.getActualStats(quest.mobId);
        String statsLine;
        if (actualStats.isPresent() && actualStats.get().isValid()) {
            var stats = actualStats.get();
            statsLine = I18n.translate("devmod.endurance.quest.card.stats_actual",
                stats.health(), stats.damage(), quest.pointsPerKill).getString();
        } else {
            statsLine = I18n.translate("devmod.endurance.quest.card.stats_estimated",
                quest.baseHealth, quest.baseDamage, quest.pointsPerKill).getString();
        }
        graphics.drawString(safeFont, statsLine, contentX, y + 36, COLOR_TEXT);

        // Row 4: Personal best (if any) - right aligned
        PersonalRecordsSyncPayload.MobRecord record = ClientPersonalRecordsCache.getMobRecord(quest.mobId.toString());
        if (record.highestWave() > 0 || record.bestScore() > 0) {
            String bestText = I18n.translate("devmod.endurance.quest.card.best",
                record.highestWave(), record.bestScore()).getString();
            graphics.drawString(safeFont, bestText, contentX, y + 52, COLOR_TEXT);
        } else {
            graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.card.not_attempted").getString(),
                contentX, y + 52, COLOR_TEXT_DIM);
        }
    }

    /**
     * Renders the unified right panel containing both quest details and settings.
     * This is the main control panel for configuring and starting quests.
     */
    private void renderRightPanel(GuiGraphics graphics) {
        var safeFont = Objects.requireNonNull(font);
        int panelX = width - RIGHT_PANEL_WIDTH;
        int panelY = 0;
        int panelW = RIGHT_PANEL_WIDTH;
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
            String mobName = quest.displayName;
            if (mobName.length() > 22) {
                mobName = mobName.substring(0, 20) + "..";
            }
            graphics.drawString(safeFont, "§l" + mobName, contentX, y, COLOR_TEXT);
            y += 14;

            // Tier badge inline
            int tierColor = Objects.requireNonNull(TIER_COLORS.get(quest.tier));
            String tierName = getTierDisplayName(quest.tier);
            String tierLine = I18n.translate("devmod.endurance.quest.details.tier_with_namespace",
                tierName, quest.namespace).getString();
            graphics.drawString(safeFont, tierLine, contentX, y, tierColor);
            y += 18;

            // Divider
            graphics.fill(contentX, y, contentX + contentW, y + 1, UIConstants.Border.SEPARATOR());
            y += 8;

            // Mob stats in compact grid format
            var actualStats = EnduranceQuestRegistry.INSTANCE.getActualStats(quest.mobId);
            boolean hasActual = actualStats.isPresent() && actualStats.get().isValid();

            // Stats row 1: HP and DMG
            if (hasActual) {
                var stats = actualStats.get();
                graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.details.stat.health",
                    stats.health()).getString(), contentX, y, COLOR_TEXT);
                graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.details.stat.damage",
                    stats.damage()).getString(), contentX + 70, y, COLOR_TEXT);
                graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.details.stat.armor",
                    stats.armor()).getString(), contentX + 140, y, COLOR_TEXT);
            } else {
                graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.details.stat.health_estimated",
                    quest.baseHealth).getString(), contentX, y, COLOR_TEXT);
                graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.details.stat.damage_estimated",
                    quest.baseDamage).getString(), contentX + 70, y, COLOR_TEXT);
            }
            y += 14;

            // Stats row 2: Points and Elite chance
            graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.details.points_per_kill",
                quest.pointsPerKill).getString(), contentX, y, COLOR_TEXT);
            graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.details.elite_chance",
                quest.eliteChance * 100).getString(), contentX + 100, y, COLOR_TEXT);
            y += 20;

        } else {
            // No quest selected state
            graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.details.no_selection_line1").getString(),
                contentX, y, COLOR_TEXT_DIM);
            y += 14;
            graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.details.no_selection_line2").getString(),
                contentX, y, COLOR_TEXT_DIM);
            y += 30;
        }

        // === SECTION 2: QUEST SETTINGS ===
        // Section header with background
        graphics.fill(contentX - 4, y, contentX + contentW + 4, y + 18, UIConstants.Background.INPUT());
        graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.section.settings").getString(),
            contentX, y + 5, COLOR_ACCENT);
        // Config button position (rendered in renderActionButtons)
        configButtonY = y + 1;
        y += 26;

        // Wave selector with visual bar
        graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.label.waves").getString(),
            contentX, y + 2, COLOR_TEXT);

        // Wave count display (large)
        String waveText = endlessMode ? "§c∞" : String.format("§f%d", questWaves);
        graphics.drawString(safeFont, waveText, contentX + 50, y, COLOR_TEXT);

        // Mini wave bar visualization
        int barX = contentX + 75;
        int barW = contentW - 85;
        int barH = 8;
        graphics.fill(barX, y + 2, barX + barW, y + 2 + barH, UIConstants.Background.INPUT());
        if (!endlessMode) {
            int fillW = (int) ((questWaves / 50.0f) * barW);
            int barColor = questWaves <= 10 ? COLOR_SUCCESS : (questWaves <= 25 ? COLOR_WARNING : COLOR_DANGER);
            graphics.fill(barX + 1, y + 3, barX + 1 + fillW, y + 1 + barH, barColor);
        } else {
            // Endless mode - gradient fill
            graphics.fill(barX + 1, y + 3, barX + barW - 1, y + 1 + barH, COLOR_DANGER);
        }
        y += 20;

        // Wave control buttons area (rendered in renderActionButtons)
        waveControlY = y;
        y += 30;

        // Endless toggle area
        endlessToggleY = y;
        y += 30;

        // Divider
        graphics.fill(contentX, y, contentX + contentW, y + 1, UIConstants.Border.SEPARATOR());
        y += 10;

        // === SECTION 3: KIT SELECTION ===
        graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.section.kit").getString(),
            contentX, y, COLOR_ACCENT);
        y += 18;

        // Kit name with color (show custom kit if selected)
        String kitName;
        String kitDesc;
        int kitColor;
        if (usingCustomKit && customKitName != null) {
            kitName = customKitName;
            kitDesc = I18n.translate("devmod.endurance.quest.kit.custom_desc", I18n.ui("edit").getString()).getString();
            kitColor = UIConstants.Accent.GOLD();
        } else {
            kitName = selectedKit.getDisplayName();
            kitDesc = selectedKit.getDescription();
            kitColor = selectedKit.getColor();
        }
        graphics.drawString(safeFont, "§f" + kitName, contentX, y, kitColor);
        y += 12;

        // Kit description
        graphics.drawString(safeFont, "§7" + kitDesc, contentX, y, COLOR_TEXT_DIM);
        y += 16;

        // Kit navigation area (buttons rendered separately)
        kitControlY = y;
        y += 28;

        // Kit preview items (show first 8 items as icons in 2 rows)
        if (!selectedKit.isCustom()) {
            var previewItems = selectedKit.getPreviewItems();
            int itemX = contentX;
            int itemCount = Math.min(previewItems.size(), 8);
            for (int i = 0; i < itemCount; i++) {
                var item = Objects.requireNonNull(previewItems.get(i));
                // Draw item background
                graphics.fill(itemX - 1, y - 1, itemX + 17, y + 17, UIConstants.Background.INPUT());
                graphics.renderItem(item, itemX, y);
                itemX += 20;
                if (i == 3) {
                    // Second row
                    itemX = contentX;
                    y += 20;
                }
            }
            if (itemCount > 4) y += 20;
            else y += 24;
        } else {
            graphics.drawString(safeFont, I18n.translate("devmod.endurance.quest.kit.uses_inventory").getString(),
                contentX, y, COLOR_TEXT_DIM);
            y += 20;
        }

        // Store the Y position for the start button
        startButtonY = height - 50;
    }

    // Y positions for control elements (set during renderRightPanel, used in renderActionButtons)
    private int waveControlY = 0;
    private int endlessToggleY = 0;
    private int kitControlY = 0;
    private int startButtonY = 0;
    private int configButtonY = 0;

    /**
     * Render custom action buttons with Impact styling.
     * Uses Y positions calculated in renderRightPanel for proper alignment.
     */
    private void renderActionButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = width - RIGHT_PANEL_WIDTH;
        int controlX = panelX + 12;
        int controlW = RIGHT_PANEL_WIDTH - 24;

        // === CONFIG BUTTON (in Quest Settings header) ===
        int configBtnW = 22;
        int configBtnH = 16;
        int configBtnX = panelX + RIGHT_PANEL_WIDTH - configBtnW - 16;
        if (configButton != null) {
            configButton.render(graphics, configBtnX, configButtonY, configBtnW, configBtnH, mouseX, mouseY);
        }

        // === WAVE CONTROL BUTTONS ===
        int waveBtnSize = 22;
        if (decreaseWaveButton != null) {
            decreaseWaveButton.render(graphics, controlX, waveControlY, waveBtnSize, waveBtnSize, mouseX, mouseY);
        }
        if (increaseWaveButton != null) {
            increaseWaveButton.render(graphics, controlX + waveBtnSize + 8, waveControlY, waveBtnSize, waveBtnSize, mouseX, mouseY);
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
            endlessBtn.render(graphics, controlX, endlessToggleY, toggleW, toggleH, mouseX, mouseY);
        }

        // === KIT SELECTION BUTTONS ===
        int kitBtnW = 28;
        int kitBtnH = 22;
        int editBtnW = 40;
        if (prevKitButton != null) {
            prevKitButton.render(graphics, controlX, kitControlY, kitBtnW, kitBtnH, mouseX, mouseY);
        }
        if (nextKitButton != null) {
            nextKitButton.render(graphics, controlX + controlW - kitBtnW - editBtnW - 4, kitControlY, kitBtnW, kitBtnH, mouseX, mouseY);
        }
        // Edit button to open kit editor
        if (editKitButton != null) {
            editKitButton.render(graphics, controlX + controlW - editBtnW, kitControlY, editBtnW, kitBtnH, mouseX, mouseY);
        }

        // === START QUEST BUTTON (bottom of right panel) ===
        int startW = controlW;
        int startH = 32;
        int startX = controlX;
        var startBtn2 = startButton;
        if (startBtn2 != null) {
            startBtn2.setEnabled(selectedQuest != null);
            startBtn2.render(graphics, startX, startButtonY, startW, startH, mouseX, mouseY);
        }

        // === SHOP BUTTON (bottom of sidebar, above search) ===
        int shopW = SIDEBAR_WIDTH - 20;
        int shopH = 24;
        int shopX = 10;
        int shopY = height - 65;  // Above search box
        if (shopButton != null) {
            shopButton.render(graphics, shopX, shopY, shopW, shopH, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int my = (int) mouseY;

        if (showIntroOverlay) {
            var dismissBtn = introDismissButton;
            if (button == 0 && dismissBtn != null && dismissBtn.mouseClicked(mouseX, mouseY, button)) {
                dismissBtn.mouseReleased(mouseX, mouseY, button);
            }
            return true;
        }

        if (button == 0) {
            if (startButton != null && startButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (shopButton != null && shopButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (decreaseWaveButton != null && decreaseWaveButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (increaseWaveButton != null && increaseWaveButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (endlessToggleButton != null && endlessToggleButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (prevKitButton != null && prevKitButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (nextKitButton != null && nextKitButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (editKitButton != null && editKitButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (resetFiltersButton != null && resetFiltersButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (configButton != null && configButton.mouseClicked(mouseX, mouseY, button)) return true;
        }

        // Check sidebar clicks - but let the search box handle its own clicks first
        if (mouseX < SIDEBAR_WIDTH) {
            // Check if click is on search box area (bottom of sidebar)
            int searchY = height - 28;
            if (mouseY >= searchY && mouseY <= searchY + 18 && mouseX >= 10 && mouseX <= SIDEBAR_WIDTH - 10) {
                // Let super handle it so the EditBox can receive focus
                return super.mouseClicked(mouseX, mouseY, button);
            }
            handleSidebarClick(my);
            return true;
        }

        // Check quest list clicks
        int listX = SIDEBAR_WIDTH + 8;
        int listY = HEADER_HEIGHT + 4;
        int listWidth = width - SIDEBAR_WIDTH - RIGHT_PANEL_WIDTH - 16;
        int listHeight = height - HEADER_HEIGHT - 55;

        if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
            int relativeY = (int) mouseY - listY + scrollOffset;
            int index = relativeY / (QUEST_CARD_HEIGHT + QUEST_CARD_MARGIN);
            if (index >= 0 && index < filteredQuests.size()) {
                selectedQuest = filteredQuests.get(index);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
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
        if (prevKitButton != null) handled |= prevKitButton.mouseReleased(mouseX, mouseY, button);
        if (nextKitButton != null) handled |= nextKitButton.mouseReleased(mouseX, mouseY, button);
        if (editKitButton != null) handled |= editKitButton.mouseReleased(mouseX, mouseY, button);
        if (resetFiltersButton != null) handled |= resetFiltersButton.mouseReleased(mouseX, mouseY, button);
        if (configButton != null) handled |= configButton.mouseReleased(mouseX, mouseY, button);

        if (handled) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void handleSidebarClick(int mouseY) {
        // Must match renderSidebar() layout exactly
        int bottomReserved = 95;
        int tiersSectionHeight = 120;

        int y = 8;   // Start at title position
        y += 20;     // After title
        y += 8;      // After divider -> "Mod Source" label
        y += 14;     // After label -> first mod item

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
            int index = relativeY / 14;
            if (index >= 0 && index < sortedNamespaces.size()) {
                selectedNamespace = sortedNamespaces.get(index);
                applyFilters();
                return;
            }
        }

        // Difficulty section starts after mods
        y = modsSectionBottom + 8;  // Gap
        y += 14;  // "Difficulty" label

        // All tiers
        if (mouseY >= y - 1 && mouseY < y + 12) {
            selectedTier = null;
            applyFilters();
            return;
        }
        y += 14;

        // Tier clicks
        for (EnduranceQuestRegistry.MobTier tier : EnduranceQuestRegistry.MobTier.values()) {
            if (mouseY >= y - 1 && mouseY < y + 12) {
                selectedTier = tier;
                applyFilters();
                return;
            }
            y += 14;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showIntroOverlay) {
            return true;
        }

        // Check if mouse is over sidebar (for mods scroll)
        if (mouseX < SIDEBAR_WIDTH) {
            // Calculate mods section bounds
            int bottomReserved = 95;
            int tiersSectionHeight = 120;
            int modsSectionTop = 8 + 20 + 8 + 14; // header + gap + label
            int modsSectionBottom = height - bottomReserved - tiersSectionHeight;

            if (mouseY >= modsSectionTop && mouseY < modsSectionBottom) {
                sidebarScrollOffset = Math.max(0, Math.min(sidebarMaxScroll, sidebarScrollOffset - (int) (scrollY * 20)));
                return true;
            }
            return false;
        }

        // Check if mouse is over quest list area
        int listX = SIDEBAR_WIDTH + 8;
        int listY = HEADER_HEIGHT + 4;
        int listWidth = width - SIDEBAR_WIDTH - RIGHT_PANEL_WIDTH - 16;
        int listHeight = height - HEADER_HEIGHT - 55;

        if (mouseX >= listX && mouseX <= listX + listWidth &&
            mouseY >= listY && mouseY <= listY + listHeight) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 20)));
            return true;
        }

        return false;
    }

    private void adjustWaves(int delta) {
        questWaves = Math.max(1, Math.min(50, questWaves + delta));
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
            // Show error feedback
            errorMessage = I18n.translate("devmod.endurance.quest.error.select_mob").getString();
            errorMessageTime = System.currentTimeMillis();

            // Play error sound
            if (minecraft != null) {
                minecraft.getSoundManager().play(
                    Objects.requireNonNull(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        Objects.requireNonNull(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value()), 0.5f)));
            }
            return;
        }

        // Send packet to server to start quest
        // Use "TEMPORARY" kit ID if using custom kit, otherwise use the preset name
        String kitId = (usingCustomKit && KitManager.INSTANCE.hasTemporaryKit())
            ? "TEMPORARY"
            : selectedKit.name();

        LOGGER.info("[EnduranceQuest] Starting quest: {} with {} waves, endless={}, kit={}",
            currentQuest.displayName, questWaves, endlessMode, kitId);

        StartQuestPayload payload = new StartQuestPayload(
            currentQuest.mobId.toString(),
            questWaves,
            endlessMode,
            kitId
        );
        ActionRegistry.invoke(ActionIds.ENDURANCE_QUEST_START,
            ClientActionContexts.forClient(ActionOrigin.UI, payload));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
