package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main UI screen for browsing and starting Endurance Quests.
 */
public class EnduranceQuestScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceQuestScreen.class);

    // Layout constants
    private static final int SIDEBAR_WIDTH = 200;
    private static final int HEADER_HEIGHT = 40;
    private static final int QUEST_CARD_HEIGHT = 80;
    private static final int QUEST_CARD_MARGIN = 5;

    // Colors
    private static final int COLOR_BG = 0xDD1a1a2e;
    private static final int COLOR_SIDEBAR_BG = 0xDD16213e;
    private static final int COLOR_CARD_BG = 0xDD0f3460;
    private static final int COLOR_CARD_HOVER = 0xDD1a5490;
    private static final int COLOR_CARD_SELECTED = 0xDD2a74b0;
    private static final int COLOR_ACCENT = 0xFFe94560;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_SUCCESS = 0xFF4ade80;
    private static final int COLOR_WARNING = 0xFFfbbf24;
    private static final int COLOR_DANGER = 0xFFef4444;

    // Tier colors
    private static final Map<EnduranceQuestRegistry.MobTier, Integer> TIER_COLORS = Map.of(
        EnduranceQuestRegistry.MobTier.TRIVIAL, 0xFF94a3b8,
        EnduranceQuestRegistry.MobTier.EASY, 0xFF4ade80,
        EnduranceQuestRegistry.MobTier.MEDIUM, 0xFFfbbf24,
        EnduranceQuestRegistry.MobTier.HARD, 0xFFf97316,
        EnduranceQuestRegistry.MobTier.ELITE, 0xFFa855f7,
        EnduranceQuestRegistry.MobTier.BOSS, 0xFFef4444
    );

    // State
    private List<EnduranceQuestRegistry.MobQuestConfig> allQuests = new ArrayList<>();
    private List<EnduranceQuestRegistry.MobQuestConfig> filteredQuests = new ArrayList<>();
    private EnduranceQuestRegistry.MobQuestConfig selectedQuest = null;

    // Filters
    private String searchQuery = "";
    private String selectedNamespace = "all";
    private EnduranceQuestRegistry.MobTier selectedTier = null;

    // UI Components
    private EditBox searchBox;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Quest settings
    private int questWaves = 10;
    private boolean endlessMode = false;

    // Pre-selection from QuickTestWizard
    @javax.annotation.Nullable
    private net.minecraft.resources.ResourceLocation preselectedMob = null;
    private boolean hasPreselection = false;

    // Intro overlay for first-time users
    private boolean showIntroOverlay = false;

    // Error feedback for no selection
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

        // Request personal records from server
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new RequestPersonalRecordsPayload());

        // Check if first time opening Endurance Quest
        if (!SettingsManager.INSTANCE.getSettings().onboarding.hasSeenEnduranceIntro) {
            showIntroOverlay = true;
        }

        // Load quests
        loadQuests();

        // Search box
        searchBox = new EditBox(font, SIDEBAR_WIDTH + 10, 10, 200, 20, I18n.ui("search"));
        searchBox.setHint(I18n.translate("devmod.quest.search_mobs"));
        searchBox.setResponder(query -> {
            searchQuery = query;
            applyFilters();
        });
        addRenderableWidget(searchBox);

        // Start Quest button
        addRenderableWidget(Button.builder(I18n.ui("start_quest"), btn -> startSelectedQuest())
            .bounds(width - 130, height - 40, 120, 30)
            .build());

        // Settings buttons
        addRenderableWidget(Button.builder(I18n.ui("minus_symbol"), btn -> adjustWaves(-1))
            .bounds(width - 200, height - 80, 20, 20)
            .build());
        addRenderableWidget(Button.builder(I18n.ui("plus_symbol"), btn -> adjustWaves(1))
            .bounds(width - 130, height - 80, 20, 20)
            .build());

        addRenderableWidget(Button.builder(I18n.translate("devmod.endurance.endless_off"), btn -> toggleEndless(btn))
            .bounds(width - 200, height - 55, 100, 20)
            .build());

        // Shop button
        addRenderableWidget(Button.builder(I18n.ui("shop"), btn -> openShop())
            .bounds(10, height - 40, 80, 30)
            .build());
    }

    private void openShop() {
        minecraft.setScreen(new EnduranceShopScreen());
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
        filteredQuests = allQuests.stream()
            .filter(q -> searchQuery.isEmpty() ||
                q.displayName.toLowerCase().contains(searchQuery.toLowerCase()) ||
                q.mobId.toString().toLowerCase().contains(searchQuery.toLowerCase()))
            .filter(q -> selectedNamespace.equals("all") || q.namespace.equals(selectedNamespace))
            .filter(q -> selectedTier == null || q.tier == selectedTier)
            .sorted(Comparator.comparing(q -> q.displayName))
            .collect(Collectors.toList());

        scrollOffset = 0;
        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        int contentHeight = filteredQuests.size() * (QUEST_CARD_HEIGHT + QUEST_CARD_MARGIN);
        int viewportHeight = height - HEADER_HEIGHT - 60;
        maxScroll = Math.max(0, contentHeight - viewportHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(0, 0, width, height, COLOR_BG);

        // Sidebar
        renderSidebar(graphics, mouseX, mouseY);

        // Header
        renderHeader(graphics);

        // Quest list
        renderQuestList(graphics, mouseX, mouseY);

        // Selected quest details
        if (selectedQuest != null) {
            renderQuestDetails(graphics);
        }

        // Quest settings panel
        renderSettingsPanel(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Error message feedback (rendered on top of buttons)
        if (errorMessage != null) {
            long elapsed = System.currentTimeMillis() - errorMessageTime;
            if (elapsed < ERROR_DISPLAY_DURATION) {
                // Fade out effect
                float alpha = 1.0f - (elapsed / (float) ERROR_DISPLAY_DURATION);
                int alphaInt = (int) (alpha * 255);

                // Draw error message near the Start Quest button
                int msgWidth = font.width(errorMessage);
                int msgX = width - 130 + 60 - msgWidth / 2; // Center over button
                int msgY = height - 60;

                // Background
                graphics.fill(msgX - 6, msgY - 3, msgX + msgWidth + 6, msgY + 12, (alphaInt << 24) | 0x660000);
                // Border
                graphics.fill(msgX - 6, msgY - 3, msgX + msgWidth + 6, msgY - 2, (alphaInt << 24) | 0xFF0000);
                // Text
                graphics.drawString(font, errorMessage, msgX, msgY, (alphaInt << 24) | 0xFFFFFF);
            } else {
                errorMessage = null;
            }
        }

        // Intro overlay for first-time users (rendered on top)
        if (showIntroOverlay) {
            renderIntroOverlay(graphics, mouseX, mouseY);
        }
    }

    /**
     * Render intro overlay explaining Endurance Quest to new users.
     */
    private void renderIntroOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        // Darken background
        graphics.fill(0, 0, width, height, 0xCC000000);

        int panelW = 400;
        int panelH = 280;
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        // Panel background
        graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, COLOR_ACCENT);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1a1a2e);

        int y = panelY + 15;
        int centerX = panelX + panelW / 2;

        // Title
        graphics.drawCenteredString(font, "§6§lWelcome to Endurance Quest!", centerX, y, 0xFFFFFFFF);
        y += 25;

        // Separator
        graphics.fill(panelX + 20, y, panelX + panelW - 20, y + 1, 0x44FFFFFF);
        y += 15;

        // Description
        String[] lines = {
            "§fEndurance Quest is a wave-based survival mode.",
            "",
            "§7• Select a mob type from the list",
            "§7• Configure waves (or enable Endless mode)",
            "§7• Survive waves of enemies in an arena",
            "§7• Earn §eTokens §7and §dGems §7as rewards",
            "§7• Build combos for bonus points (DMC-style!)",
            "§7• Visit the §bShop §7to buy upgrades",
            "",
            "§aTip: §7Higher tier mobs = better rewards!"
        };

        for (String line : lines) {
            graphics.drawString(font, line, panelX + 25, y, 0xFFFFFFFF, false);
            y += 12;
        }

        // Button area
        y = panelY + panelH - 40;
        int btnW = 120;
        int btnX = centerX - btnW / 2;
        boolean btnHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= y && mouseY <= y + 22;

        // Button
        int btnColor = btnHovered ? 0xFF2a74b0 : 0xFF0f3460;
        graphics.fill(btnX, y, btnX + btnW, y + 22, btnColor);
        graphics.fill(btnX, y, btnX + btnW, y + 1, COLOR_ACCENT);
        graphics.fill(btnX, y + 21, btnX + btnW, y + 22, COLOR_ACCENT);
        graphics.drawCenteredString(font, "Got it!", centerX, y + 7, 0xFFFFFFFF);
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
        graphics.fill(0, 0, SIDEBAR_WIDTH, height, COLOR_SIDEBAR_BG);

        int y = 10;

        // Title
        graphics.drawCenteredString(font, "Filters", SIDEBAR_WIDTH / 2, y, COLOR_TEXT);
        y += 25;

        // Namespace filters
        graphics.drawString(font, "Mod:", 10, y, COLOR_TEXT_DIM);
        y += 12;

        Set<String> namespaces = EnduranceQuestRegistry.INSTANCE.getAvailableNamespaces();
        List<String> sortedNamespaces = new ArrayList<>(namespaces);
        sortedNamespaces.sort(String::compareTo);
        sortedNamespaces.add(0, "all");

        for (String ns : sortedNamespaces) {
            if (y > height - 200) break;

            boolean isSelected = ns.equals(selectedNamespace);
            boolean isHovered = mouseX >= 10 && mouseX <= SIDEBAR_WIDTH - 10 && mouseY >= y && mouseY < y + 14;

            int bgColor = isSelected ? COLOR_ACCENT : (isHovered ? 0x44FFFFFF : 0x00000000);
            if (bgColor != 0) {
                graphics.fill(10, y - 1, SIDEBAR_WIDTH - 10, y + 13, bgColor);
            }

            String displayName = ns.equals("all") ? "All Mods" : ns;
            long count = ns.equals("all") ? allQuests.size() :
                allQuests.stream().filter(q -> q.namespace.equals(ns)).count();

            graphics.drawString(font, displayName + " (" + count + ")", 15, y, COLOR_TEXT);
            y += 16;
        }

        y += 10;

        // Tier filters
        graphics.drawString(font, "Difficulty:", 10, y, COLOR_TEXT_DIM);
        y += 12;

        // All tiers option
        boolean allTiersSelected = selectedTier == null;
        boolean allTiersHovered = mouseX >= 10 && mouseX <= SIDEBAR_WIDTH - 10 && mouseY >= y && mouseY < y + 14;
        if (allTiersSelected || allTiersHovered) {
            graphics.fill(10, y - 1, SIDEBAR_WIDTH - 10, y + 13, allTiersSelected ? COLOR_ACCENT : 0x44FFFFFF);
        }
        graphics.drawString(font, "All Difficulties", 15, y, COLOR_TEXT);
        y += 16;

        for (EnduranceQuestRegistry.MobTier tier : EnduranceQuestRegistry.MobTier.values()) {
            boolean isSelected = tier == selectedTier;
            boolean isHovered = mouseX >= 10 && mouseX <= SIDEBAR_WIDTH - 10 && mouseY >= y && mouseY < y + 14;

            int bgColor = isSelected ? TIER_COLORS.get(tier) : (isHovered ? 0x44FFFFFF : 0x00000000);
            if (bgColor != 0) {
                graphics.fill(10, y - 1, SIDEBAR_WIDTH - 10, y + 13, bgColor);
            }

            long count = allQuests.stream().filter(q -> q.tier == tier).count();
            graphics.drawString(font, tier.name() + " (" + count + ")", 15, y, TIER_COLORS.get(tier));
            y += 16;
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        int x = SIDEBAR_WIDTH;

        // Title
        graphics.drawString(font, "Endurance Quests", x + 10, 15, COLOR_TEXT);

        // Stats
        String stats = String.format("%d quests available", filteredQuests.size());
        graphics.drawString(font, stats, x + 220, 15, COLOR_TEXT_DIM);
    }

    private void renderQuestList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = SIDEBAR_WIDTH + 10;
        int listY = HEADER_HEIGHT;
        int listWidth = width - SIDEBAR_WIDTH - 250;
        int listHeight = height - HEADER_HEIGHT - 60;

        // Clip area
        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int y = listY - scrollOffset;
        for (EnduranceQuestRegistry.MobQuestConfig quest : filteredQuests) {
            if (y + QUEST_CARD_HEIGHT > listY && y < listY + listHeight) {
                renderQuestCard(graphics, quest, listX, y, listWidth, mouseX, mouseY);
            }
            y += QUEST_CARD_HEIGHT + QUEST_CARD_MARGIN;
        }

        graphics.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarHeight = (int) ((float) listHeight / (listHeight + maxScroll) * listHeight);
            int scrollbarY = listY + (int) ((float) scrollOffset / maxScroll * (listHeight - scrollbarHeight));
            graphics.fill(listX + listWidth - 5, listY, listX + listWidth, listY + listHeight, 0x44FFFFFF);
            graphics.fill(listX + listWidth - 5, scrollbarY, listX + listWidth, scrollbarY + scrollbarHeight, COLOR_ACCENT);
        }
    }

    private void renderQuestCard(GuiGraphics graphics, EnduranceQuestRegistry.MobQuestConfig quest,
                                  int x, int y, int width, int mouseX, int mouseY) {
        boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY < y + QUEST_CARD_HEIGHT;
        boolean isSelected = quest == selectedQuest;

        int bgColor = isSelected ? COLOR_CARD_SELECTED : (isHovered ? COLOR_CARD_HOVER : COLOR_CARD_BG);
        graphics.fill(x, y, x + width, y + QUEST_CARD_HEIGHT, bgColor);

        // Tier indicator
        int tierColor = TIER_COLORS.get(quest.tier);
        graphics.fill(x, y, x + 4, y + QUEST_CARD_HEIGHT, tierColor);

        // Mob name
        graphics.drawString(font, quest.displayName, x + 12, y + 8, COLOR_TEXT);

        // Tier badge
        String tierText = quest.tier.name();
        int tierWidth = font.width(tierText) + 8;
        graphics.fill(x + width - tierWidth - 8, y + 5, x + width - 8, y + 18, tierColor);
        graphics.drawString(font, tierText, x + width - tierWidth - 4, y + 7, COLOR_TEXT);

        // Mod name
        graphics.drawString(font, quest.namespace, x + 12, y + 22, COLOR_TEXT_DIM);

        // Stats
        graphics.drawString(font, String.format("Base mobs: %d | Points/kill: %d",
            quest.baseCountPerWave, quest.pointsPerKill), x + 12, y + 40, COLOR_TEXT_DIM);

        // Best record from player stats cache
        PersonalRecordsSyncPayload.MobRecord record = ClientPersonalRecordsCache.getMobRecord(quest.mobId.toString());
        if (record.highestWave() > 0 || record.bestScore() > 0) {
            graphics.drawString(font, String.format("Best: Wave %d | %d pts", record.highestWave(), record.bestScore()),
                x + 12, y + 55, COLOR_SUCCESS);
        } else {
            graphics.drawString(font, "Best: Not attempted", x + 12, y + 55, COLOR_TEXT_DIM);
        }
    }

    private void renderQuestDetails(GuiGraphics graphics) {
        int panelX = width - 230;
        int panelY = HEADER_HEIGHT;
        int panelWidth = 220;
        int panelHeight = height - HEADER_HEIGHT - 120;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_SIDEBAR_BG);

        int y = panelY + 10;

        // Quest name
        graphics.drawCenteredString(font, selectedQuest.displayName, panelX + panelWidth / 2, y, COLOR_TEXT);
        y += 20;

        // Tier with color
        int tierColor = TIER_COLORS.get(selectedQuest.tier);
        graphics.drawCenteredString(font, selectedQuest.tier.name() + " Difficulty", panelX + panelWidth / 2, y, tierColor);
        y += 25;

        // Divider
        graphics.fill(panelX + 10, y, panelX + panelWidth - 10, y + 1, 0x44FFFFFF);
        y += 10;

        // Stats
        graphics.drawString(font, "Wave Configuration:", panelX + 10, y, COLOR_ACCENT);
        y += 14;
        graphics.drawString(font, String.format("  Base count: %d", selectedQuest.baseCountPerWave), panelX + 10, y, COLOR_TEXT_DIM);
        y += 12;
        graphics.drawString(font, String.format("  Scaling: +%.1f/wave", selectedQuest.countScalingPerWave), panelX + 10, y, COLOR_TEXT_DIM);
        y += 12;
        graphics.drawString(font, String.format("  Max per wave: %d", selectedQuest.maxPerWave), panelX + 10, y, COLOR_TEXT_DIM);
        y += 20;

        graphics.drawString(font, "Rewards:", panelX + 10, y, COLOR_ACCENT);
        y += 14;
        graphics.drawString(font, String.format("  Per kill: %d pts", selectedQuest.pointsPerKill), panelX + 10, y, COLOR_SUCCESS);
        y += 12;
        graphics.drawString(font, String.format("  Wave bonus: %d pts", selectedQuest.bonusPointsForWaveClear), panelX + 10, y, COLOR_SUCCESS);
        y += 20;

        graphics.drawString(font, "Spawn Info:", panelX + 10, y, COLOR_ACCENT);
        y += 14;
        graphics.drawString(font, String.format("  Group spawn: %s", selectedQuest.canSpawnInGroups ? "Yes" : "No"), panelX + 10, y, COLOR_TEXT_DIM);
        y += 12;
        graphics.drawString(font, String.format("  Elite chance: %.0f%%", selectedQuest.eliteChance * 100), panelX + 10, y, COLOR_WARNING);
    }

    private void renderSettingsPanel(GuiGraphics graphics) {
        int panelX = width - 230;
        int panelY = height - 110;

        graphics.drawString(font, "Quest Settings:", panelX + 10, panelY, COLOR_ACCENT);
        panelY += 15;

        graphics.drawString(font, String.format("Waves: %d", questWaves), panelX + 50, panelY + 5, COLOR_TEXT);
        panelY += 25;

        // Endless mode status shown on button
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle intro overlay click first
        if (showIntroOverlay) {
            int panelH = 280;
            int panelY = (height - panelH) / 2;
            int btnW = 120;
            int btnX = (width - btnW) / 2;
            int btnY = panelY + panelH - 40;

            // Check if clicked on "Got it!" button
            if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 22) {
                dismissIntroOverlay();
                return true;
            }
            // Block all other clicks while overlay is shown
            return true;
        }

        // Check sidebar clicks
        if (mouseX < SIDEBAR_WIDTH) {
            handleSidebarClick((int) mouseX, (int) mouseY);
            return true;
        }

        // Check quest list clicks
        int listX = SIDEBAR_WIDTH + 10;
        int listY = HEADER_HEIGHT;
        int listWidth = width - SIDEBAR_WIDTH - 250;
        int listHeight = height - HEADER_HEIGHT - 60;

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

    private void handleSidebarClick(int mouseX, int mouseY) {
        int y = 35; // Start after title

        // Namespace clicks
        y += 12;
        Set<String> namespaces = EnduranceQuestRegistry.INSTANCE.getAvailableNamespaces();
        List<String> sortedNamespaces = new ArrayList<>(namespaces);
        sortedNamespaces.sort(String::compareTo);
        sortedNamespaces.add(0, "all");

        for (String ns : sortedNamespaces) {
            if (mouseY >= y && mouseY < y + 14) {
                selectedNamespace = ns;
                applyFilters();
                return;
            }
            y += 16;
        }

        y += 22; // Skip to tier section

        // All tiers
        if (mouseY >= y && mouseY < y + 14) {
            selectedTier = null;
            applyFilters();
            return;
        }
        y += 16;

        // Tier clicks
        for (EnduranceQuestRegistry.MobTier tier : EnduranceQuestRegistry.MobTier.values()) {
            if (mouseY >= y && mouseY < y + 14) {
                selectedTier = tier;
                applyFilters();
                return;
            }
            y += 16;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 20)));
        return true;
    }

    private void adjustWaves(int delta) {
        questWaves = Math.max(1, Math.min(50, questWaves + delta));
    }

    private void toggleEndless(Button btn) {
        endlessMode = !endlessMode;
        btn.setMessage(I18n.translate(endlessMode ? "devmod.endurance.endless_on" : "devmod.endurance.endless_off"));
    }

    private void startSelectedQuest() {
        if (selectedQuest == null) {
            // Show error feedback
            errorMessage = "Please select a mob first!";
            errorMessageTime = System.currentTimeMillis();

            // Play error sound
            if (minecraft != null) {
                minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f));
            }
            return;
        }

        // Send packet to server to start quest
        LOGGER.info("[EnduranceQuest] Starting quest: {} with {} waves, endless={}",
            selectedQuest.displayName, questWaves, endlessMode);

        // Send StartQuestPayload to server
        PacketDistributor.sendToServer(new StartQuestPayload(
            selectedQuest.mobId.toString(),
            questWaves,
            endlessMode
        ));

        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
