package com.frenkvs.devmod.party;

import com.frenkvs.devmod.endurance.EnduranceQuestRegistry;
import com.frenkvs.devmod.endurance.EnduranceQuestRegistry.MobDifficultyPreset;
import com.frenkvs.devmod.endurance.EnduranceQuestRegistry.MobTier;
import com.frenkvs.devmod.endurance.QuestType;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Party management screen with 3D mob preview.
 * Features:
 * - Quest type selection tabs
 * - Mob type selector with 3D preview and filters
 * - Member list with ready status
 * - Wave preview slider with scaling info
 * - Realtime party sync refresh
 */
@SuppressWarnings("null")
public class PartyScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyScreen.class);

    // Layout constants
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 460;
    private static final int HEADER_HEIGHT = 30;
    private static final int TAB_HEIGHT = 28;
    private static final int MEMBER_ROW_HEIGHT = 26;
    private static final int MOB_PREVIEW_SIZE = 100;
    private static final int MOB_LIST_WIDTH = 140;

    // Colors
    private static final int COLOR_BG = 0xE8101018;
    private static final int COLOR_HEADER = 0xFF1A1A28;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_LEADER = 0xFFFFD700;
    private static final int COLOR_READY = 0xFF00FF00;
    private static final int COLOR_NOT_READY = 0xFFFF6666;
    private static final int COLOR_TAB_ACTIVE = 0xFF3366FF;
    private static final int COLOR_TAB_INACTIVE = 0xFF333344;
    private static final int COLOR_MOB_SELECTED = 0xFF4488FF;
    private static final int COLOR_MOB_HOVER = 0x40FFFFFF;
    private static final int COLOR_FILTER_ACTIVE = 0xFF44AA44;
    private static final int COLOR_FILTER_INACTIVE = 0xFF444444;

    // State from ClientPartyCache
    private List<PartySyncPayload.PartyMemberInfo> members = new ArrayList<>();
    private UUID leaderId = null;
    private QuestType questType = QuestType.PVE_COOP;
    private PartyData.PartyState partyState = PartyData.PartyState.FORMING;
    private boolean isInParty = false;
    private boolean isLeader = false;
    private boolean isReady = false;

    // Mob selection
    private List<EnduranceQuestRegistry.MobQuestConfig> availableMobs = new ArrayList<>();
    private List<EnduranceQuestRegistry.MobQuestConfig> filteredMobs = new ArrayList<>();
    private int selectedMobIndex = 0;
    private int mobListScrollOffset = 0;
    private String mobSearchText = "";
    private static final int MAX_VISIBLE_MOBS = 7;

    // Filters
    private Set<String> availableNamespaces = new LinkedHashSet<>();
    @Nullable
    private String selectedNamespace = null; // null = all
    @Nullable
    private MobTier selectedTierFilter = null; // null = all

    // Wave preview slider
    private int previewWaveNumber = 1;
    private static final int MAX_PREVIEW_WAVE = 20;

    // 3D Preview state
    private float mobRotationY = -30f;
    private float targetMobRotationY = -30f;
    private float mobRotationX = 0f;
    private boolean isDraggingPreview = false;
    private int dragStartX, dragStartY;
    private float dragStartRotX, dragStartRotY;
    @Nullable
    private LivingEntity previewEntity = null;

    // Realtime sync
    private long lastSyncTime = 0;
    private static final long SYNC_INTERVAL_MS = 500; // Refresh every 500ms

    // UI Components
    private EditBox inviteBox;
    private EditBox mobSearchBox;
    private Button createPartyButton;
    private Button readyButton;
    private Button startButton;
    private Button leaveButton;
    private Button disbandButton;

    // Selected member for kick
    @Nullable
    private UUID selectedMemberId = null;
    private int hoveredMemberIndex = -1;

    // Panel position
    private int panelX;
    private int panelY;

    // Blur control
    private int originalBlurValue = 0;

    public PartyScreen() {
        super(Component.translatable("devmod.party.title"));

        // Disable blur
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            OptionInstance<Integer> blurOption = mc.options.menuBackgroundBlurriness();
            originalBlurValue = blurOption.get();
            blurOption.set(0);
        }
    }

    @Override
    protected void init() {
        super.init();

        // Center the panel (with clamping for small screens)
        int actualWidth = Math.min(PANEL_WIDTH, width - 20);
        int actualHeight = Math.min(PANEL_HEIGHT, height - 20);
        panelX = (width - actualWidth) / 2;
        panelY = (height - actualHeight) / 2;

        // Load available mobs
        loadAvailableMobs();

        // Refresh party data from cache
        refreshFromCache();

        // === UI Components ===

        // Invite box (below member list)
        int inviteY = panelY + HEADER_HEIGHT + TAB_HEIGHT + 180;
        inviteBox = new EditBox(font, panelX + 15, inviteY, 130, 18,
                Component.translatable("devmod.party.invite_placeholder"));
        inviteBox.setHint(Component.translatable("devmod.party.invite_hint"));
        inviteBox.setMaxLength(16);
        addRenderableWidget(inviteBox);

        // Invite button
        addRenderableWidget(Button.builder(Component.literal("Invite"), this::onInviteClicked)
                .bounds(panelX + 150, inviteY, 45, 18)
                .build());

        // Mob search box
        int mobSectionX = panelX + 205;
        mobSearchBox = new EditBox(font, mobSectionX, panelY + HEADER_HEIGHT + TAB_HEIGHT + 8, MOB_LIST_WIDTH, 16,
                Component.literal("Search mobs..."));
        mobSearchBox.setHint(Component.literal("Search..."));
        mobSearchBox.setMaxLength(32);
        mobSearchBox.setResponder(this::onMobSearchChanged);
        addRenderableWidget(mobSearchBox);

        // Bottom buttons
        int buttonY = panelY + PANEL_HEIGHT - 35;
        int buttonWidth = 70;
        int buttonGap = 5;

        // Ready button
        readyButton = Button.builder(getReadyButtonText(), this::onReadyClicked)
                .bounds(panelX + 15, buttonY, buttonWidth, 20)
                .build();
        addRenderableWidget(readyButton);

        // Leave button
        leaveButton = Button.builder(Component.translatable("devmod.party.leave_party"), this::onLeaveClicked)
                .bounds(panelX + 15 + buttonWidth + buttonGap, buttonY, buttonWidth, 20)
                .build();
        addRenderableWidget(leaveButton);

        // Start Quest button (leader only)
        startButton = Button.builder(Component.translatable("devmod.party.start_quest"), this::onStartClicked)
                .bounds(panelX + PANEL_WIDTH - buttonWidth * 2 - buttonGap - 15, buttonY, buttonWidth + 15, 20)
                .build();
        addRenderableWidget(startButton);

        // Disband button (leader only)
        disbandButton = Button.builder(Component.translatable("devmod.party.disband_party"), this::onDisbandClicked)
                .bounds(panelX + PANEL_WIDTH - buttonWidth - 15, buttonY, buttonWidth, 20)
                .build();
        addRenderableWidget(disbandButton);

        // Create Party button (shown when not in a party)
        createPartyButton = Button.builder(Component.translatable("devmod.party.create_party"), this::onCreatePartyClicked)
                .bounds(panelX + PANEL_WIDTH / 2 - 70, panelY + PANEL_HEIGHT / 2 - 10, 140, 24)
                .build();
        addRenderableWidget(createPartyButton);

        updateButtonStates();
        updatePreviewEntity();
    }

    private void loadAvailableMobs() {
        availableMobs = new ArrayList<>(EnduranceQuestRegistry.INSTANCE.getAllMobConfigs());

        // Collect namespaces
        availableNamespaces = availableMobs.stream()
                .map(m -> m.namespace)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Sort by tier then by name
        availableMobs.sort((a, b) -> {
            int tierComp = a.tier.compareTo(b.tier);
            if (tierComp != 0) return tierComp;
            return a.displayName.compareTo(b.displayName);
        });

        filterMobs();

        // Find default selection (zombie)
        for (int i = 0; i < filteredMobs.size(); i++) {
            if (filteredMobs.get(i).mobId.getPath().equals("zombie")) {
                selectedMobIndex = i;
                break;
            }
        }
    }

    private void filterMobs() {
        filteredMobs = availableMobs.stream()
                .filter(m -> {
                    // Namespace filter
                    if (selectedNamespace != null && !m.namespace.equals(selectedNamespace)) {
                        return false;
                    }
                    // Tier filter
                    if (selectedTierFilter != null && m.tier != selectedTierFilter) {
                        return false;
                    }
                    // Search filter
                    if (!mobSearchText.isEmpty()) {
                        String search = mobSearchText.toLowerCase();
                        return m.displayName.toLowerCase().contains(search) ||
                                m.mobId.toString().toLowerCase().contains(search);
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // Ensure selectedMobIndex is valid
        if (selectedMobIndex >= filteredMobs.size()) {
            selectedMobIndex = Math.max(0, filteredMobs.size() - 1);
        }
        mobListScrollOffset = 0;
        updatePreviewEntity();
    }

    private void onMobSearchChanged(String text) {
        mobSearchText = text;
        filterMobs();
    }

    private void refreshFromCache() {
        isInParty = ClientPartyCache.isInParty();

        if (isInParty) {
            members = new ArrayList<>(ClientPartyCache.getMembers());
            leaderId = ClientPartyCache.getLeaderId();
            QuestType cachedType = ClientPartyCache.getQuestType();
            if (cachedType != null) {
                questType = cachedType;
            }
            PartyData.PartyState cachedState = ClientPartyCache.getPartyState();
            if (cachedState != null) {
                partyState = cachedState;
            }

            // Get selected mob from cache
            PartySyncPayload party = ClientPartyCache.getParty();
            if (party != null) {
                ResourceLocation mobId = party.getSelectedMobResourceLocation();
                if (mobId != null) {
                    for (int i = 0; i < filteredMobs.size(); i++) {
                        if (filteredMobs.get(i).mobId.equals(mobId)) {
                            if (selectedMobIndex != i) {
                                selectedMobIndex = i;
                                updatePreviewEntity();
                            }
                            break;
                        }
                    }
                }
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                isLeader = mc.player.getUUID().equals(leaderId);
                // Get ready state from member list
                UUID localId = mc.player.getUUID();
                isReady = members.stream()
                        .filter(m -> m.playerId().equals(localId))
                        .findFirst()
                        .map(PartySyncPayload.PartyMemberInfo::isReady)
                        .orElse(false);
            }
        } else {
            members.clear();
            leaderId = null;
            isLeader = false;
            isReady = false;
        }
    }

    @Override
    public void tick() {
        super.tick();

        // Realtime sync refresh
        long now = System.currentTimeMillis();
        if (now - lastSyncTime > SYNC_INTERVAL_MS) {
            lastSyncTime = now;
            boolean wasInParty = isInParty;
            refreshFromCache();

            // Update UI if state changed
            if (wasInParty != isInParty) {
                updateButtonStates();
            }
        }
    }

    private void updateButtonStates() {
        // Create party button only visible when not in a party
        createPartyButton.visible = !isInParty;
        createPartyButton.active = !isInParty;

        // Other buttons only visible when in a party
        readyButton.visible = isInParty;
        leaveButton.visible = isInParty;
        startButton.visible = isInParty;
        inviteBox.visible = isInParty;
        mobSearchBox.visible = isInParty;

        // Ready button message
        readyButton.setMessage(getReadyButtonText());

        // Start button only for leader when enough players are ready
        startButton.active = isLeader && canStartQuest();

        // Disband button only for leader
        disbandButton.active = isLeader;
        disbandButton.visible = isInParty && isLeader;

        // Invite box only for leader
        inviteBox.setEditable(isLeader);
    }

    private boolean canStartQuest() {
        if (!isInParty) return false;
        // Allow solo for PVE_COOP testing
        if (questType.allowsSoloPlay() && members.size() == 1) return true;
        if (members.size() < questType.minPlayers) return false;
        // Check if all members are ready
        return members.stream().allMatch(PartySyncPayload.PartyMemberInfo::isReady);
    }

    private Component getReadyButtonText() {
        return isReady
                ? Component.translatable("devmod.party.not_ready")
                : Component.translatable("devmod.party.ready");
    }

    private void updatePreviewEntity() {
        if (filteredMobs.isEmpty() || selectedMobIndex >= filteredMobs.size()) {
            previewEntity = null;
            return;
        }

        EnduranceQuestRegistry.MobQuestConfig config = filteredMobs.get(selectedMobIndex);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            try {
                Entity entity = config.entityType.create(mc.level);
                if (entity instanceof LivingEntity living) {
                    previewEntity = living;
                } else {
                    previewEntity = null;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to create preview entity for {}", config.mobId, e);
                previewEntity = null;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Transparent background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Main panel background
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_BG);

        // Header
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + HEADER_HEIGHT, COLOR_HEADER);
        graphics.drawCenteredString(font, title, panelX + PANEL_WIDTH / 2, panelY + 10, COLOR_TEXT);

        // Party state indicator
        if (isInParty) {
            String stateText = "§7[" + partyState.name() + "]";
            graphics.drawString(font, stateText, panelX + PANEL_WIDTH - font.width(stateText) - 10, panelY + 10, COLOR_TEXT_DIM);
        }

        // Border
        AxiomRenderer.drawBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, UIConstants.Border.DEFAULT);

        if (isInParty) {
            // Quest type tabs
            renderQuestTypeTabs(graphics, mouseX, mouseY);

            // Left side: Members list
            renderMembersList(graphics, mouseX, mouseY);

            // Center: Mob selection with filters
            renderMobSection(graphics, mouseX, mouseY);

            // Right: 3D Preview with stats
            renderMobPreviewSection(graphics, mouseX, mouseY);

            // Bottom: Wave preview with slider
            renderWavePreview(graphics, mouseX, mouseY);
        } else {
            // Show "No Party" message
            graphics.drawCenteredString(font, Component.translatable("devmod.party.no_party"),
                    panelX + PANEL_WIDTH / 2, panelY + PANEL_HEIGHT / 2 - 40, COLOR_TEXT_DIM);
            graphics.drawCenteredString(font, Component.literal("Create a party to start an Endurance Quest"),
                    panelX + PANEL_WIDTH / 2, panelY + PANEL_HEIGHT / 2 - 25, COLOR_TEXT_DIM);
        }

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderQuestTypeTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int tabY = panelY + HEADER_HEIGHT;
        int tabWidth = PANEL_WIDTH / 3;

        for (int i = 0; i < QuestType.values().length; i++) {
            QuestType type = QuestType.values()[i];
            int tabX = panelX + i * tabWidth;

            boolean isActive = type == questType;
            boolean isHovered = mouseX >= tabX && mouseX < tabX + tabWidth &&
                    mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            int tabColor = isActive ? COLOR_TAB_ACTIVE :
                    (isHovered ? 0xFF444466 : COLOR_TAB_INACTIVE);

            graphics.fill(tabX, tabY, tabX + tabWidth, tabY + TAB_HEIGHT, tabColor);

            // Tab text
            graphics.drawCenteredString(font, type.displayName, tabX + tabWidth / 2, tabY + 6, COLOR_TEXT);

            // Player range
            String range = String.format("%d-%d players", type.minPlayers, type.maxPlayers);
            graphics.drawCenteredString(font, range, tabX + tabWidth / 2, tabY + 16, COLOR_TEXT_DIM);
        }
    }

    private void renderMembersList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = panelX + 10;
        int listY = panelY + HEADER_HEIGHT + TAB_HEIGHT + 10;
        int listWidth = 185;

        // Section title
        graphics.drawString(font, "§lParty Members", listX, listY, COLOR_TEXT);
        listY += 15;

        hoveredMemberIndex = -1;

        for (int i = 0; i < members.size() && i < 6; i++) {
            PartySyncPayload.PartyMemberInfo member = members.get(i);
            int rowY = listY + i * MEMBER_ROW_HEIGHT;

            // Check hover
            if (mouseX >= listX && mouseX < listX + listWidth &&
                    mouseY >= rowY && mouseY < rowY + MEMBER_ROW_HEIGHT) {
                hoveredMemberIndex = i;
                graphics.fill(listX, rowY, listX + listWidth, rowY + MEMBER_ROW_HEIGHT, 0x30FFFFFF);
            }

            // Ready indicator
            int indicatorColor = member.isReady() ? COLOR_READY : COLOR_NOT_READY;
            graphics.fill(listX + 2, rowY + 6, listX + 8, rowY + MEMBER_ROW_HEIGHT - 8, indicatorColor);

            // Leader star
            boolean isMemberLeader = member.playerId().equals(leaderId);
            String prefix = isMemberLeader ? "§6★ " : "  ";
            int nameColor = isMemberLeader ? COLOR_LEADER : COLOR_TEXT;

            // Player name
            String displayName = member.playerName();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && member.playerId().equals(mc.player.getUUID())) {
                displayName += " §7(You)";
            }
            graphics.drawString(font, prefix + displayName, listX + 12, rowY + 8, nameColor);

            // Ready/Not Ready text on right
            String readyText = member.isReady() ? "§aReady" : "§cWaiting";
            int textWidth = font.width(readyText.replace("§a", "").replace("§c", ""));
            graphics.drawString(font, readyText, listX + listWidth - textWidth - 5, rowY + 8, COLOR_TEXT);
        }

        // Empty state
        if (members.isEmpty()) {
            graphics.drawString(font, "§7No members yet", listX + 10, listY + 20, COLOR_TEXT_DIM);
        }

        // Player count
        int infoY = listY + 6 * MEMBER_ROW_HEIGHT + 5;
        String countText = String.format("§7Players: %d/%d", members.size(), questType.maxPlayers);
        graphics.drawString(font, countText, listX, infoY, COLOR_TEXT_DIM);
    }

    private void renderMobSection(GuiGraphics graphics, int mouseX, int mouseY) {
        int sectionX = panelX + 205;
        int sectionY = panelY + HEADER_HEIGHT + TAB_HEIGHT + 8;

        // Section title
        graphics.drawString(font, "§lMob Type", sectionX, sectionY - 2, COLOR_TEXT);

        // Filter buttons below search
        int filterY = sectionY + 26;
        renderFilters(graphics, sectionX, filterY, mouseX, mouseY);

        // Mob list
        int listY = filterY + 22;
        int visibleMobs = Math.min(MAX_VISIBLE_MOBS, filteredMobs.size());

        for (int i = 0; i < visibleMobs; i++) {
            int mobIndex = mobListScrollOffset + i;
            if (mobIndex >= filteredMobs.size()) break;

            EnduranceQuestRegistry.MobQuestConfig config = filteredMobs.get(mobIndex);
            int rowY = listY + i * 18;

            boolean isSelected = mobIndex == selectedMobIndex;
            boolean isHovered = mouseX >= sectionX && mouseX < sectionX + MOB_LIST_WIDTH &&
                    mouseY >= rowY && mouseY < rowY + 18;

            // Row background
            if (isSelected) {
                graphics.fill(sectionX, rowY, sectionX + MOB_LIST_WIDTH, rowY + 18, COLOR_MOB_SELECTED);
            } else if (isHovered) {
                graphics.fill(sectionX, rowY, sectionX + MOB_LIST_WIDTH, rowY + 18, COLOR_MOB_HOVER);
            }

            // Tier indicator (color bar)
            int tierColor = getTierColor(config.tier);
            graphics.fill(sectionX, rowY + 2, sectionX + 3, rowY + 16, tierColor);

            // Mob name (truncated if needed)
            String name = config.displayName;
            if (font.width(name) > MOB_LIST_WIDTH - 35) {
                name = font.plainSubstrByWidth(name, MOB_LIST_WIDTH - 40) + "..";
            }
            graphics.drawString(font, name, sectionX + 6, rowY + 5, COLOR_TEXT);

            // Preset icon
            String presetIcon = getPresetIcon(config.difficultyPreset);
            graphics.drawString(font, presetIcon, sectionX + MOB_LIST_WIDTH - 12, rowY + 5, COLOR_TEXT_DIM);
        }

        // Scroll indicators
        if (mobListScrollOffset > 0) {
            graphics.drawString(font, "§7▲", sectionX + MOB_LIST_WIDTH / 2 - 3, listY - 8, COLOR_TEXT_DIM);
        }
        if (mobListScrollOffset + MAX_VISIBLE_MOBS < filteredMobs.size()) {
            graphics.drawString(font, "§7▼", sectionX + MOB_LIST_WIDTH / 2 - 3, listY + MAX_VISIBLE_MOBS * 18 + 2, COLOR_TEXT_DIM);
        }

        // Mob count
        graphics.drawString(font, String.format("§7%d mobs", filteredMobs.size()),
                sectionX, listY + MAX_VISIBLE_MOBS * 18 + 12, COLOR_TEXT_DIM);
    }

    private void renderFilters(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        // Namespace filter buttons (compact)
        int btnX = x;
        int btnH = 12;

        // "All" button
        boolean allActive = selectedNamespace == null;
        int allColor = allActive ? COLOR_FILTER_ACTIVE : COLOR_FILTER_INACTIVE;
        int allW = font.width("All") + 6;
        graphics.fill(btnX, y, btnX + allW, y + btnH, allColor);
        graphics.drawString(font, "All", btnX + 3, y + 2, COLOR_TEXT);
        btnX += allW + 2;

        // "MC" button for minecraft namespace
        boolean mcActive = "minecraft".equals(selectedNamespace);
        int mcColor = mcActive ? COLOR_FILTER_ACTIVE : COLOR_FILTER_INACTIVE;
        int mcW = font.width("MC") + 6;
        graphics.fill(btnX, y, btnX + mcW, y + btnH, mcColor);
        graphics.drawString(font, "MC", btnX + 3, y + 2, COLOR_TEXT);
        btnX += mcW + 2;

        // Mod count indicator
        long modCount = availableNamespaces.stream().filter(ns -> !ns.equals("minecraft")).count();
        if (modCount > 0) {
            String modText = "+" + modCount + " mods";
            graphics.drawString(font, "§7" + modText, btnX + 2, y + 2, COLOR_TEXT_DIM);
        }

        // Tier filter (second row)
        int tierY = y + btnH + 3;
        int tierBtnW = 18;
        int tierBtnX = x;

        for (MobTier tier : MobTier.values()) {
            boolean active = tier == selectedTierFilter;
            int color = active ? getTierColor(tier) : COLOR_FILTER_INACTIVE;

            graphics.fill(tierBtnX, tierY, tierBtnX + tierBtnW, tierY + btnH, color);

            // Tier initial
            String initial = tier.name().substring(0, 1);
            graphics.drawCenteredString(font, initial, tierBtnX + tierBtnW / 2, tierY + 2, COLOR_TEXT);

            tierBtnX += tierBtnW + 1;
        }
    }

    private void renderMobPreviewSection(GuiGraphics graphics, int mouseX, int mouseY) {
        int previewX = panelX + 355;
        int previewY = panelY + HEADER_HEIGHT + TAB_HEIGHT + 8;
        int boxW = MOB_PREVIEW_SIZE + 50;
        int boxH = MOB_PREVIEW_SIZE + 80;

        // Preview box background
        graphics.fill(previewX, previewY, previewX + boxW, previewY + boxH, 0xFF1A1A2A);
        AxiomRenderer.drawBorder(graphics, previewX, previewY, boxW, boxH, UIConstants.Border.MUTED);

        // Smooth rotation
        mobRotationY = Mth.lerp(0.1f, mobRotationY, targetMobRotationY);

        if (previewEntity != null && !filteredMobs.isEmpty() && selectedMobIndex < filteredMobs.size()) {
            int centerX = previewX + boxW / 2;
            int centerY = previewY + 85;

            // Calculate scale based on mob size
            float mobHeight = previewEntity.getBbHeight();
            float mobWidth = previewEntity.getBbWidth();
            float maxDim = Math.max(mobHeight, mobWidth);
            int scale = (int) Math.min(35, 70 / maxDim);

            // Create rotation quaternion
            Quaternionf rotation = new Quaternionf()
                    .rotateY((float) Math.toRadians(mobRotationY))
                    .rotateX((float) Math.toRadians(mobRotationX))
                    .rotateZ((float) Math.PI);

            try {
                InventoryScreen.renderEntityInInventory(
                        graphics, centerX, centerY, scale,
                        new Vector3f(0, 0, 0), rotation, null, previewEntity
                );
            } catch (Exception e) {
                graphics.drawCenteredString(font, "Preview", centerX, centerY - 20, COLOR_TEXT_DIM);
            }

            // Mob info below preview
            EnduranceQuestRegistry.MobQuestConfig config = filteredMobs.get(selectedMobIndex);

            // Name
            graphics.drawCenteredString(font, config.displayName, previewX + boxW / 2, previewY + boxH - 70, COLOR_TEXT);

            // Tier badge
            String tierText = getTierDisplayName(config.tier);
            int tierColor = getTierColor(config.tier);
            graphics.drawCenteredString(font, tierText, previewX + boxW / 2, previewY + boxH - 58, tierColor);

            // Preset type
            graphics.drawCenteredString(font, "§7" + config.difficultyPreset.displayName,
                    previewX + boxW / 2, previewY + boxH - 46, COLOR_TEXT_DIM);

            // Base stats
            int statsY = previewY + boxH - 32;
            graphics.drawString(font, String.format("§cHP: %.0f", config.baseHealth),
                    previewX + 8, statsY, COLOR_TEXT);
            graphics.drawString(font, String.format("§6DMG: %.0f", config.baseDamage),
                    previewX + 8, statsY + 10, COLOR_TEXT);

            // Scaled stats (for current party)
            int playerCount = Math.max(1, members.size());
            float scaledHP = config.getScaledHealth(playerCount, questType);
            float scaledDMG = config.getScaledDamage(playerCount);
            graphics.drawString(font, String.format("§7→ %.0f", scaledHP),
                    previewX + boxW - 45, statsY, COLOR_TEXT_DIM);
            graphics.drawString(font, String.format("§7→ %.0f", scaledDMG),
                    previewX + boxW - 45, statsY + 10, COLOR_TEXT_DIM);

        } else {
            graphics.drawCenteredString(font, "No Preview", previewX + boxW / 2, previewY + boxH / 2, COLOR_TEXT_DIM);
        }

        // Drag hint
        boolean hovering = mouseX >= previewX && mouseX < previewX + boxW &&
                mouseY >= previewY && mouseY < previewY + boxH - 70;
        if (hovering && !isDraggingPreview) {
            graphics.drawString(font, "§8Drag to rotate", previewX + 4, previewY + 4, COLOR_TEXT_DIM);
        }
    }

    private void renderWavePreview(GuiGraphics graphics, int mouseX, int mouseY) {
        if (filteredMobs.isEmpty() || selectedMobIndex >= filteredMobs.size()) return;

        int previewY = panelY + PANEL_HEIGHT - 90;
        int previewX = panelX + 15;

        // Separator line
        graphics.fill(panelX + 10, previewY - 5, panelX + PANEL_WIDTH - 10, previewY - 4, 0xFF333344);

        // Title with wave slider
        graphics.drawString(font, "§lWave Preview", previewX, previewY, COLOR_TEXT);

        // Wave slider
        int sliderX = previewX + 90;
        int sliderY = previewY;
        int sliderW = 120;
        int sliderH = 10;

        // Slider background
        graphics.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, 0xFF333344);

        // Slider filled portion
        float progress = (previewWaveNumber - 1) / (float) (MAX_PREVIEW_WAVE - 1);
        int fillW = (int) (sliderW * progress);
        graphics.fill(sliderX, sliderY, sliderX + fillW, sliderY + sliderH, COLOR_TAB_ACTIVE);

        // Wave number
        String waveText = "Wave " + previewWaveNumber;
        graphics.drawString(font, waveText, sliderX + sliderW + 5, sliderY, COLOR_TEXT);

        previewY += 16;

        EnduranceQuestRegistry.MobQuestConfig config = filteredMobs.get(selectedMobIndex);
        int playerCount = Math.max(1, members.size());

        // Stats for selected wave
        int mobCount = config.getMobCountForWave(previewWaveNumber, playerCount, questType);
        float scaledHP = config.getScaledHealth(playerCount, questType);
        float scaledDMG = config.getScaledDamage(playerCount);

        // Calculate wave multiplier
        float waveMultiplier = 1.0f + (previewWaveNumber - 1) * 0.05f;

        // Row 1: Counts
        int col1 = previewX;
        int col2 = previewX + 130;
        int col3 = previewX + 260;
        int col4 = previewX + 390;

        graphics.drawString(font, String.format("§7Mobs: §f%d", mobCount), col1, previewY, COLOR_TEXT);
        graphics.drawString(font, String.format("§7HP: §c%.0f", scaledHP * waveMultiplier), col2, previewY, COLOR_TEXT);
        graphics.drawString(font, String.format("§7DMG: §6%.0f", scaledDMG * waveMultiplier), col3, previewY, COLOR_TEXT);
        graphics.drawString(font, String.format("§7Difficulty: §e%.2fx", questType.difficultyMultiplier * waveMultiplier), col4, previewY, COLOR_TEXT);

        previewY += 12;

        // Row 2: Points and Elite
        int pointsPerWave = mobCount * config.pointsPerKill + config.bonusPointsForWaveClear;
        graphics.drawString(font, String.format("§7Points/Kill: §e%d", config.pointsPerKill), col1, previewY, COLOR_TEXT);
        graphics.drawString(font, String.format("§7Wave Total: §e%d", pointsPerWave), col2, previewY, COLOR_TEXT);
        graphics.drawString(font, String.format("§7Elite%%: §c%.0f%%", config.eliteChance * 100), col3, previewY, COLOR_TEXT);
        graphics.drawString(font, String.format("§7Players: §b%d", playerCount), col4, previewY, COLOR_TEXT);
    }

    private String getPresetIcon(MobDifficultyPreset preset) {
        return switch (preset) {
            case SWARM -> "S";
            case STANDARD -> "=";
            case TANK -> "T";
            case GLASS_CANNON -> "G";
            case BOSS_STYLE -> "B";
        };
    }

    private int getTierColor(MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> 0xFF888888;
            case EASY -> 0xFF55FF55;
            case MEDIUM -> 0xFFFFFF55;
            case HARD -> 0xFFFF8800;
            case ELITE -> 0xFFFF5555;
            case BOSS -> 0xFFAA00AA;
        };
    }

    private String getTierDisplayName(MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> "§7★ Trivial";
            case EASY -> "§a★★ Easy";
            case MEDIUM -> "§e★★★ Medium";
            case HARD -> "§6★★★★ Hard";
            case ELITE -> "§c★★★★★ Elite";
            case BOSS -> "§5💀 Boss";
        };
    }

    // === Button Handlers ===

    private void onInviteClicked(Button button) {
        String playerName = inviteBox.getValue().trim();
        if (playerName.isEmpty()) return;

        LOGGER.info("[PartyScreen] Sending invite to: {}", playerName);
        PacketDistributor.sendToServer(NamedInvitePayload.create(playerName, questType));
        inviteBox.setValue("");
        UIConstants.Sound.success();
    }

    private void onReadyClicked(Button button) {
        isReady = !isReady;
        ClientPartyCache.setLocalPlayerReady(isReady);
        PacketDistributor.sendToServer(PartyActionPayload.toggleReady());
        updateButtonStates();
        UIConstants.Sound.toggleOn();
    }

    private void onLeaveClicked(Button button) {
        LOGGER.info("[PartyScreen] Leaving party");
        PacketDistributor.sendToServer(PartyActionPayload.leaveParty());
        onClose();
        UIConstants.Sound.click();
    }

    private void onStartClicked(Button button) {
        if (!canStartQuest()) {
            UIConstants.Sound.error();
            return;
        }

        LOGGER.info("[PartyScreen] Starting quest with {} players, mob: {}",
                members.size(), getSelectedMobId());
        PacketDistributor.sendToServer(PartyActionPayload.startQuest());
        onClose();
        UIConstants.Sound.success();
    }

    private void onDisbandClicked(Button button) {
        LOGGER.info("[PartyScreen] Disbanding party");
        PacketDistributor.sendToServer(PartyActionPayload.disbandParty());
        onClose();
        UIConstants.Sound.warning();
    }

    private void onCreatePartyClicked(Button button) {
        LOGGER.info("[PartyScreen] Creating party with type: {}", questType);
        PacketDistributor.sendToServer(PartyActionPayload.createParty(questType));
        isInParty = true;
        isLeader = true;
        updateButtonStates();
        UIConstants.Sound.success();
    }

    @Nullable
    private ResourceLocation getSelectedMobId() {
        if (filteredMobs.isEmpty() || selectedMobIndex >= filteredMobs.size()) return null;
        return filteredMobs.get(selectedMobIndex).mobId;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check quest type tab clicks
        int tabY = panelY + HEADER_HEIGHT;
        int tabWidth = PANEL_WIDTH / 3;

        if (mouseY >= tabY && mouseY < tabY + TAB_HEIGHT && isLeader && isInParty) {
            for (int i = 0; i < QuestType.values().length; i++) {
                int tabX = panelX + i * tabWidth;
                if (mouseX >= tabX && mouseX < tabX + tabWidth) {
                    questType = QuestType.values()[i];
                    ClientPartyCache.setQuestType(questType);
                    PacketDistributor.sendToServer(PartyActionPayload.setQuestType(questType));
                    UIConstants.Sound.click();
                    updateButtonStates();
                    return true;
                }
            }
        }

        // Check filter clicks
        if (isInParty) {
            int filterX = panelX + 205;
            int filterY = panelY + HEADER_HEIGHT + TAB_HEIGHT + 34;

            // "All" button
            int allW = font.width("All") + 6;
            if (mouseX >= filterX && mouseX < filterX + allW &&
                    mouseY >= filterY && mouseY < filterY + 12) {
                selectedNamespace = null;
                filterMobs();
                UIConstants.Sound.click();
                return true;
            }

            // "MC" button
            int mcX = filterX + allW + 2;
            int mcW = font.width("MC") + 6;
            if (mouseX >= mcX && mouseX < mcX + mcW &&
                    mouseY >= filterY && mouseY < filterY + 12) {
                selectedNamespace = "minecraft".equals(selectedNamespace) ? null : "minecraft";
                filterMobs();
                UIConstants.Sound.click();
                return true;
            }

            // Tier filter buttons
            int tierY = filterY + 15;
            int tierBtnW = 18;
            int tierBtnX = filterX;
            for (MobTier tier : MobTier.values()) {
                if (mouseX >= tierBtnX && mouseX < tierBtnX + tierBtnW &&
                        mouseY >= tierY && mouseY < tierY + 12) {
                    selectedTierFilter = (tier == selectedTierFilter) ? null : tier;
                    filterMobs();
                    UIConstants.Sound.click();
                    return true;
                }
                tierBtnX += tierBtnW + 1;
            }
        }

        // Check mob list clicks
        if (isInParty && isLeader) {
            int sectionX = panelX + 205;
            int listY = panelY + HEADER_HEIGHT + TAB_HEIGHT + 56;

            if (mouseX >= sectionX && mouseX < sectionX + MOB_LIST_WIDTH) {
                for (int i = 0; i < MAX_VISIBLE_MOBS; i++) {
                    int rowY = listY + i * 18;
                    if (mouseY >= rowY && mouseY < rowY + 18) {
                        int mobIndex = mobListScrollOffset + i;
                        if (mobIndex < filteredMobs.size()) {
                            selectedMobIndex = mobIndex;
                            updatePreviewEntity();

                            ResourceLocation mobId = getSelectedMobId();
                            if (mobId != null) {
                                PacketDistributor.sendToServer(PartyActionPayload.setMobType(mobId));
                            }

                            UIConstants.Sound.click();
                            return true;
                        }
                    }
                }
            }
        }

        // Wave slider
        if (isInParty) {
            int sliderX = panelX + 105;
            int sliderY = panelY + PANEL_HEIGHT - 90;
            int sliderW = 120;

            if (mouseX >= sliderX && mouseX < sliderX + sliderW &&
                    mouseY >= sliderY && mouseY < sliderY + 12) {
                float progress = (float) (mouseX - sliderX) / sliderW;
                previewWaveNumber = 1 + (int) (progress * (MAX_PREVIEW_WAVE - 1));
                previewWaveNumber = Mth.clamp(previewWaveNumber, 1, MAX_PREVIEW_WAVE);
                return true;
            }
        }

        // Check 3D preview drag start
        int previewX = panelX + 355;
        int previewY = panelY + HEADER_HEIGHT + TAB_HEIGHT + 8;
        int boxW = MOB_PREVIEW_SIZE + 50;
        int boxH = MOB_PREVIEW_SIZE + 10;

        if (mouseX >= previewX && mouseX < previewX + boxW &&
                mouseY >= previewY && mouseY < previewY + boxH) {
            isDraggingPreview = true;
            dragStartX = (int) mouseX;
            dragStartY = (int) mouseY;
            dragStartRotX = mobRotationX;
            dragStartRotY = mobRotationY;
            return true;
        }

        // Check member selection for kick
        if (hoveredMemberIndex >= 0 && hoveredMemberIndex < members.size() && isLeader) {
            PartySyncPayload.PartyMemberInfo member = members.get(hoveredMemberIndex);
            if (!member.playerId().equals(leaderId)) {
                selectedMemberId = member.playerId();
                if (button == 1) {
                    PacketDistributor.sendToServer(PartyActionPayload.kickMember(selectedMemberId));
                    UIConstants.Sound.warning();
                    return true;
                }
                UIConstants.Sound.click();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingPreview = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingPreview) {
            float deltaX = (float) (mouseX - dragStartX);
            float deltaY = (float) (mouseY - dragStartY);
            targetMobRotationY = dragStartRotY + deltaX * 0.8f;
            mobRotationX = Mth.clamp(dragStartRotX + deltaY * 0.5f, -30, 30);
            return true;
        }

        // Wave slider drag
        int sliderX = panelX + 105;
        int sliderY = panelY + PANEL_HEIGHT - 90;
        int sliderW = 120;

        if (mouseX >= sliderX - 10 && mouseX < sliderX + sliderW + 10 &&
                mouseY >= sliderY - 5 && mouseY < sliderY + 17) {
            float progress = (float) (mouseX - sliderX) / sliderW;
            previewWaveNumber = 1 + (int) (progress * (MAX_PREVIEW_WAVE - 1));
            previewWaveNumber = Mth.clamp(previewWaveNumber, 1, MAX_PREVIEW_WAVE);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Scroll mob list
        int sectionX = panelX + 205;
        int listY = panelY + HEADER_HEIGHT + TAB_HEIGHT + 56;
        int listHeight = MAX_VISIBLE_MOBS * 18;

        if (mouseX >= sectionX && mouseX < sectionX + MOB_LIST_WIDTH &&
                mouseY >= listY && mouseY < listY + listHeight) {
            int maxScroll = Math.max(0, filteredMobs.size() - MAX_VISIBLE_MOBS);
            mobListScrollOffset = Mth.clamp(mobListScrollOffset - (int) verticalAmount, 0, maxScroll);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.menuBackgroundBlurriness().set(originalBlurValue);
        }

        if (previewEntity != null) {
            previewEntity.discard();
            previewEntity = null;
        }

        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
