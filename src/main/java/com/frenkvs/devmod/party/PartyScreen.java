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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Epic Party Management Screen - Guild Hall Style
 * Features animated borders, glow effects, and RPG-style presentation.
 */
public class PartyScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyScreen.class);

    // === EPIC DESIGN CONSTANTS ===
    private static final int PANEL_WIDTH = 600;
    private static final int PANEL_HEIGHT = 420;

    // Colors - Epic Impact Style
    private static final int COLOR_BG_DARK = 0xF0080810;
    private static final int COLOR_HEADER_GRADIENT_TOP = 0xFF1A1A35;
    private static final int COLOR_HEADER_GRADIENT_BOT = 0xFF0D0D1A;
    private static final int COLOR_GLOW_BLUE = 0xFF3D5AFE;
    private static final int COLOR_GLOW_CYAN = 0xFF00FFFF;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY = 0xFFAABBCC;
    private static final int COLOR_TEXT_DIM = 0xFF667788;
    private static final int COLOR_READY = 0xFF00FF88;
    private static final int COLOR_NOT_READY = 0xFFFF6677;
    private static final int COLOR_LEADER_GOLD = 0xFFFFD700;

    // Animation
    private float animationTick = 0f;
    private float glowPulse = 0f;
    private float titleGlow = 0f;
    private final float[] memberAnimations = new float[8];

    // Party State
    private List<PartySyncPayload.PartyMemberInfo> members = new ArrayList<>();
    private UUID leaderId = null;
    private QuestType questType = QuestType.PVE_COOP;
    private PartyData.PartyState partyState = PartyData.PartyState.FORMING;
    private boolean isInParty = false;
    private boolean isLeader = false;
    private boolean isReady = false;

    // Mob Selection
    private List<EnduranceQuestRegistry.MobQuestConfig> availableMobs = new ArrayList<>();
    private List<EnduranceQuestRegistry.MobQuestConfig> filteredMobs = new ArrayList<>();
    private int selectedMobIndex = 0;
    private int mobListScrollOffset = 0;
    private String mobSearchText = "";
    private static final int MAX_VISIBLE_MOBS = 6;

    // Filters
    private Set<String> availableNamespaces = new LinkedHashSet<>();
    @Nullable private String selectedNamespace = null;
    @Nullable private MobTier selectedTierFilter = null;

    // Preview
    private int previewWaveNumber = 1;
    private static final int MAX_PREVIEW_WAVE = 20;
    private float mobRotationY = -30f;
    private float targetMobRotationY = -30f;
    private float mobRotationX = 0f;
    private boolean isDraggingPreview = false;
    private int dragStartX, dragStartY;
    private float dragStartRotX, dragStartRotY;
    @Nullable private LivingEntity previewEntity = null;

    // Sync
    private long lastSyncTime = 0;
    private static final long SYNC_INTERVAL_MS = 500;

    // UI Components
    private EditBox inviteBox;
    private EditBox mobSearchBox;
    private Button createPartyButton;
    private Button readyButton;
    private Button startButton;
    private Button leaveButton;
    private Button disbandButton;
    private Button inviteButton;

    private int hoveredMemberIndex = -1;
    private int hoveredMobIndex = -1;
    private int hoveredQuestTab = -1;

    private int panelX, panelY;
    private int originalBlurValue = 0;

    public PartyScreen() {
        super(Objects.requireNonNull(Component.translatable("devmod.party.title")));
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            OptionInstance<Integer> blurOption = mc.options.menuBackgroundBlurriness();
            originalBlurValue = blurOption.get();
            blurOption.set(0);
        }
    }

    /**
     * Returns a non-null font reference for rendering operations.
     * This helper ensures null safety for all font-dependent drawing calls.
     */
    @Nonnull
    private net.minecraft.client.gui.Font getFont() {
        return Objects.requireNonNull(this.font, "Font not initialized");
    }

    @Override
    protected void init() {
        super.init();

        int actualWidth = Math.min(PANEL_WIDTH, width - 40);
        int actualHeight = Math.min(PANEL_HEIGHT, height - 40);
        panelX = (width - actualWidth) / 2;
        panelY = (height - actualHeight) / 2;

        loadAvailableMobs();
        refreshFromCache();

        // === Create UI Components ===

        // Use same coordinates as renderMembersPanel for invite section
        // Members panel: panelLeft = panelX + 15, panelTop = panelY + 80, panelH = 200
        int membersPanelLeft = panelX + 15;
        int membersPanelTop = panelY + 80;
        int membersPanelH = 200;
        int inviteSectionY = membersPanelTop + membersPanelH + 8;  // = panelY + 288
        // Input background at: (membersPanelLeft + 5, inviteSectionY + 12) with size 140x20
        int inviteBoxX = membersPanelLeft + 5 + 3;   // 3px inside border
        int inviteBoxY = inviteSectionY + 12 + 3;    // 3px inside border
        inviteBox = new EditBox(getFont(), inviteBoxX, inviteBoxY, 130, 14,
                Objects.requireNonNull(Component.literal("Player name...")));
        inviteBox.setHint(Objects.requireNonNull(Component.literal("Enter name...")));
        inviteBox.setMaxLength(16);
        inviteBox.setBordered(false);
        addRenderableWidget(Objects.requireNonNull(inviteBox));

        // Invite button - positioned right after the input background
        int inviteButtonX = membersPanelLeft + 5 + 140 + 3;  // After input bg (140 wide) + gap
        inviteButton = Objects.requireNonNull(Button.builder(
                Objects.requireNonNull(Component.literal("INVITE")), this::onInviteClicked)
                .bounds(inviteButtonX, inviteSectionY + 12, 50, 20)
                .build());
        addRenderableWidget(inviteButton);

        // Mob search box - inside mob selection panel after header
        // Mob panel: panelLeft = panelX + 225, panelTop = panelY + 80
        // Search background at: (panelLeft + 5, panelTop + 28) with size (panelW - 10) x 20
        int mobPanelLeft = panelX + 225;
        int mobPanelTop = panelY + 80;
        int mobSearchY = mobPanelTop + 28 + 3;  // 3px inside the search background
        mobSearchBox = new EditBox(getFont(), mobPanelLeft + 8, mobSearchY, 140, 14,
                Objects.requireNonNull(Component.literal("Search...")));
        mobSearchBox.setHint(Objects.requireNonNull(Component.literal("Search mobs...")));
        mobSearchBox.setMaxLength(32);
        mobSearchBox.setBordered(false);
        mobSearchBox.setResponder(this::onMobSearchChanged);
        addRenderableWidget(mobSearchBox);

        // Bottom action buttons
        int buttonY = panelY + PANEL_HEIGHT - 45;
        int centerX = panelX + PANEL_WIDTH / 2;

        readyButton = Objects.requireNonNull(Button.builder(
                Objects.requireNonNull(getReadyButtonText()), this::onReadyClicked)
                .bounds(centerX - 180, buttonY, 80, 24)
                .build());
        addRenderableWidget(readyButton);

        leaveButton = Objects.requireNonNull(Button.builder(
                Objects.requireNonNull(Component.literal("LEAVE")), this::onLeaveClicked)
                .bounds(centerX - 90, buttonY, 70, 24)
                .build());
        addRenderableWidget(leaveButton);

        startButton = Objects.requireNonNull(Button.builder(
                Objects.requireNonNull(Component.literal("START QUEST")), this::onStartClicked)
                .bounds(centerX - 10, buttonY, 120, 24)
                .build());
        addRenderableWidget(startButton);

        disbandButton = Objects.requireNonNull(Button.builder(
                Objects.requireNonNull(Component.literal("DISBAND")), this::onDisbandClicked)
                .bounds(centerX + 120, buttonY, 70, 24)
                .build());
        addRenderableWidget(disbandButton);

        // Create Party button (when not in party)
        createPartyButton = Objects.requireNonNull(Button.builder(
                Objects.requireNonNull(Component.literal("CREATE PARTY")), this::onCreatePartyClicked)
                .bounds(centerX - 80, panelY + PANEL_HEIGHT / 2 + 20, 160, 30)
                .build());
        addRenderableWidget(createPartyButton);

        updateButtonStates();
        updatePreviewEntity();
    }

    private void loadAvailableMobs() {
        availableMobs = new ArrayList<>(EnduranceQuestRegistry.INSTANCE.getAllMobConfigs());
        availableNamespaces = availableMobs.stream()
                .map(m -> m.namespace)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        availableMobs.sort((a, b) -> {
            int tierComp = a.tier.compareTo(b.tier);
            if (tierComp != 0) return tierComp;
            return a.displayName.compareTo(b.displayName);
        });

        filterMobs();

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
                    if (selectedNamespace != null && !m.namespace.equals(selectedNamespace)) return false;
                    if (selectedTierFilter != null && m.tier != selectedTierFilter) return false;
                    if (!mobSearchText.isEmpty()) {
                        String search = mobSearchText.toLowerCase();
                        return m.displayName.toLowerCase().contains(search) ||
                                m.mobId.toString().toLowerCase().contains(search);
                    }
                    return true;
                })
                .collect(Collectors.toList());

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
            if (cachedType != null) questType = cachedType;
            PartyData.PartyState cachedState = ClientPartyCache.getPartyState();
            if (cachedState != null) partyState = cachedState;

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
            var localPlayer = mc.player;
            if (localPlayer != null) {
                isLeader = localPlayer.getUUID().equals(leaderId);
                UUID localId = localPlayer.getUUID();
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

        // Animation updates
        animationTick += 0.05f;
        glowPulse = (float) (Math.sin(animationTick * 2) * 0.5 + 0.5);
        titleGlow = (float) (Math.sin(animationTick * 3) * 0.3 + 0.7);

        // Member entrance animations
        for (int i = 0; i < memberAnimations.length; i++) {
            if (i < members.size()) {
                memberAnimations[i] = Math.min(1f, memberAnimations[i] + 0.1f);
            } else {
                memberAnimations[i] = 0f;
            }
        }

        // Sync
        long now = System.currentTimeMillis();
        if (now - lastSyncTime > SYNC_INTERVAL_MS) {
            lastSyncTime = now;
            boolean wasInParty = isInParty;
            refreshFromCache();
            if (wasInParty != isInParty) updateButtonStates();
        }
    }

    private void updateButtonStates() {
        createPartyButton.visible = !isInParty;
        createPartyButton.active = !isInParty;

        readyButton.visible = isInParty;
        leaveButton.visible = isInParty;
        startButton.visible = isInParty;
        inviteBox.visible = isInParty;
        inviteButton.visible = isInParty;
        mobSearchBox.visible = isInParty;

        readyButton.setMessage(getReadyButtonText());
        startButton.active = isLeader && canStartQuest();
        disbandButton.active = isLeader;
        disbandButton.visible = isInParty && isLeader;
        inviteBox.setEditable(isLeader);
    }

    private boolean canStartQuest() {
        if (!isInParty) return false;
        if (questType.allowsSoloPlay() && members.size() == 1) return true;
        if (members.size() < questType.minPlayers) return false;
        return members.stream().allMatch(PartySyncPayload.PartyMemberInfo::isReady);
    }

    @Nonnull
    private Component getReadyButtonText() {
        return Objects.requireNonNull(Component.literal(isReady ? "READY" : "NOT READY"));
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
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark cinematic background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Main panel with glow
        renderMainPanel(graphics);

        if (isInParty) {
            // Quest type tabs with glow
            renderQuestTypeTabs(graphics, mouseX, mouseY);

            // Left: Party members panel
            renderMembersPanel(graphics, mouseX, mouseY);

            // Center: Mob selection
            renderMobSelectionPanel(graphics, mouseX, mouseY);

            // Right: 3D Preview with pedestal
            renderMobPreviewPanel(graphics, mouseX, mouseY);

            // Bottom: Wave stats
            renderWaveStatsBar(graphics, mouseX, mouseY);
        } else {
            renderNoPartyState(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMainPanel(GuiGraphics graphics) {
        // Outer glow effect
        int glowAlpha = (int) (30 + glowPulse * 20);
        int glowColor = (glowAlpha << 24) | (COLOR_GLOW_BLUE & 0x00FFFFFF);
        graphics.fill(panelX - 4, panelY - 4, panelX + PANEL_WIDTH + 4, panelY + PANEL_HEIGHT + 4, glowColor);
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, glowColor);

        // Main background
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, COLOR_BG_DARK);

        // Header gradient
        for (int i = 0; i < 40; i++) {
            float t = i / 40f;
            int color = UIConstants.lerp(COLOR_HEADER_GRADIENT_TOP, COLOR_HEADER_GRADIENT_BOT, t);
            graphics.fill(panelX, panelY + i, panelX + PANEL_WIDTH, panelY + i + 1, color);
        }

        // Animated border
        int borderAlpha = (int) (180 + glowPulse * 75);
        int borderColor = (borderAlpha << 24) | (COLOR_GLOW_BLUE & 0x00FFFFFF);
        drawAnimatedBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, borderColor);

        // Title with glow
        String title = "PARTY MANAGEMENT";
        int titleX = panelX + PANEL_WIDTH / 2 - getFont().width(title) / 2;

        // Title glow
        int titleGlowAlpha = (int) (titleGlow * 100);
        net.minecraft.client.gui.Font f = getFont();
        graphics.drawString(f, title, titleX - 1, panelY + 14, (titleGlowAlpha << 24) | (COLOR_GLOW_CYAN & 0x00FFFFFF), false);
        graphics.drawString(f, title, titleX + 1, panelY + 14, (titleGlowAlpha << 24) | (COLOR_GLOW_CYAN & 0x00FFFFFF), false);
        graphics.drawString(f, title, titleX, panelY + 13, (titleGlowAlpha << 24) | (COLOR_GLOW_CYAN & 0x00FFFFFF), false);
        graphics.drawString(f, title, titleX, panelY + 15, (titleGlowAlpha << 24) | (COLOR_GLOW_CYAN & 0x00FFFFFF), false);

        // Main title
        graphics.drawString(f, title, titleX, panelY + 14, COLOR_GLOW_CYAN, false);

        // Subtitle
        if (isInParty) {
            String subtitle = partyState.name() + " - " + members.size() + "/" + questType.maxPlayers + " Warriors";
            int subX = panelX + PANEL_WIDTH / 2 - f.width(subtitle) / 2;
            graphics.drawString(f, subtitle, subX, panelY + 28, COLOR_TEXT_DIM, false);
        }
    }

    private void drawAnimatedBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        // Top
        graphics.fill(x, y, x + w, y + 2, color);
        // Bottom
        graphics.fill(x, y + h - 2, x + w, y + h, color);
        // Left
        graphics.fill(x, y, x + 2, y + h, color);
        // Right
        graphics.fill(x + w - 2, y, x + w, y + h, color);

        // Corner accents
        int cornerSize = 8;
        // Top-left
        graphics.fill(x, y, x + cornerSize, y + 3, COLOR_GLOW_CYAN);
        graphics.fill(x, y, x + 3, y + cornerSize, COLOR_GLOW_CYAN);
        // Top-right
        graphics.fill(x + w - cornerSize, y, x + w, y + 3, COLOR_GLOW_CYAN);
        graphics.fill(x + w - 3, y, x + w, y + cornerSize, COLOR_GLOW_CYAN);
        // Bottom-left
        graphics.fill(x, y + h - 3, x + cornerSize, y + h, COLOR_GLOW_CYAN);
        graphics.fill(x, y + h - cornerSize, x + 3, y + h, COLOR_GLOW_CYAN);
        // Bottom-right
        graphics.fill(x + w - cornerSize, y + h - 3, x + w, y + h, COLOR_GLOW_CYAN);
        graphics.fill(x + w - 3, y + h - cornerSize, x + w, y + h, COLOR_GLOW_CYAN);
    }

    private void renderQuestTypeTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int tabY = panelY + 45;
        int tabWidth = (PANEL_WIDTH - 40) / 3;
        int tabHeight = 28;
        net.minecraft.client.gui.Font f = getFont();

        hoveredQuestTab = -1;

        for (int i = 0; i < QuestType.values().length; i++) {
            QuestType type = QuestType.values()[i];
            int tabX = panelX + 20 + i * tabWidth;

            boolean isActive = type == questType;
            boolean isHovered = mouseX >= tabX && mouseX < tabX + tabWidth - 4 &&
                    mouseY >= tabY && mouseY < tabY + tabHeight;

            if (isHovered) hoveredQuestTab = i;

            // Tab background
            int bgColor = isActive ? 0xFF1A2A4A : (isHovered ? 0xFF151525 : 0xFF0A0A15);
            graphics.fill(tabX, tabY, tabX + tabWidth - 4, tabY + tabHeight, bgColor);

            // Active indicator
            if (isActive) {
                int glowIntensity = (int) (150 + glowPulse * 50);
                graphics.fill(tabX, tabY + tabHeight - 3, tabX + tabWidth - 4, tabY + tabHeight,
                    (glowIntensity << 24) | (COLOR_GLOW_BLUE & 0x00FFFFFF));
                graphics.fill(tabX, tabY + tabHeight - 2, tabX + tabWidth - 4, tabY + tabHeight, COLOR_GLOW_BLUE);
            }

            // Border
            int borderColor = isActive ? COLOR_GLOW_BLUE : (isHovered ? 0xFF3D5AFE : 0xFF2A2A4A);
            AxiomRenderer.drawBorder(graphics, tabX, tabY, tabWidth - 4, tabHeight, borderColor);

            // Icon + Name
            String icon = getQuestTypeIcon(type);
            String name = type.displayName;
            int textColor = isActive ? COLOR_GLOW_CYAN : (isHovered ? COLOR_TEXT_WHITE : COLOR_TEXT_GRAY);

            graphics.drawCenteredString(f, icon + " " + name, tabX + (tabWidth - 4) / 2, tabY + 6, textColor);

            // Player range
            String range = type.minPlayers + "-" + type.maxPlayers + " players";
            graphics.drawCenteredString(f, range, tabX + (tabWidth - 4) / 2, tabY + 17, COLOR_TEXT_DIM);
        }
    }

    private String getQuestTypeIcon(QuestType type) {
        return switch (type) {
            case PVE_COOP -> "[CO-OP]";
            case RAID_BOSS -> "[RAID]";
            case EVENT -> "[EVENT]";
        };
    }

    private void renderMembersPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelLeft = panelX + 15;
        int panelTop = panelY + 80;
        int panelW = 200;
        int panelH = 200;
        net.minecraft.client.gui.Font f = getFont();

        // Panel background
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, 0xCC0A0A18);
        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, 0xFF2A3A5A);

        // Header
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + 22, 0xFF151528);
        graphics.drawString(f, "PARTY MEMBERS", panelLeft + 8, panelTop + 7, COLOR_GLOW_CYAN, false);

        hoveredMemberIndex = -1;
        int memberY = panelTop + 28;

        for (int i = 0; i < Math.min(members.size(), 6); i++) {
            PartySyncPayload.PartyMemberInfo member = members.get(i);
            float anim = memberAnimations[i];

            int rowY = memberY + i * 28;
            int rowH = 26;

            // Slide-in animation
            int offsetX = (int) ((1f - anim) * -50);
            int rowX = panelLeft + 5 + offsetX;
            int rowW = panelW - 10;

            boolean isHovered = mouseX >= panelLeft + 5 && mouseX < panelLeft + panelW - 5 &&
                    mouseY >= rowY && mouseY < rowY + rowH;
            if (isHovered) hoveredMemberIndex = i;

            // Row background
            int rowBg = isHovered ? 0x40FFFFFF : 0x20FFFFFF;
            graphics.fill(rowX, rowY, rowX + rowW, rowY + rowH, (int)(rowBg * anim));

            boolean isMemberLeader = member.playerId().equals(leaderId);

            // Ready/status indicator with glow
            int statusX = rowX + 5;
            int statusColor = member.isReady() ? COLOR_READY : COLOR_NOT_READY;
            int statusGlow = member.isReady() ? 0x4000FF88 : 0x40FF4466;

            // Status glow
            graphics.fill(statusX - 2, rowY + 5, statusX + 10, rowY + rowH - 5, statusGlow);
            graphics.fill(statusX, rowY + 7, statusX + 8, rowY + rowH - 7, statusColor);

            // Leader crown or player icon
            String prefix = isMemberLeader ? "[L] " : "    ";
            int nameColor = isMemberLeader ? COLOR_LEADER_GOLD : COLOR_TEXT_WHITE;

            // Player name
            String displayName = member.playerName();
            Minecraft mc = Minecraft.getInstance();
            var localPlayer = mc.player;
            if (localPlayer != null && member.playerId().equals(localPlayer.getUUID())) {
                displayName += " (You)";
            }
            graphics.drawString(f, prefix + displayName, rowX + 18, rowY + 5, nameColor, false);

            // Status text
            String status = member.isReady() ? "Ready" : "Waiting";
            int statusTextColor = member.isReady() ? COLOR_READY : COLOR_NOT_READY;
            graphics.drawString(f, status, rowX + 18, rowY + 15, statusTextColor, false);

            // Kick hint on hover (for leader)
            if (isHovered && isLeader && !member.playerId().equals(leaderId)) {
                graphics.drawString(f, "[Right-click: Kick]", rowX + rowW - 85, rowY + 10, COLOR_TEXT_DIM, false);
            }
        }

        // Empty state
        if (members.isEmpty()) {
            graphics.drawCenteredString(f, "No warriors yet...", panelLeft + panelW / 2, panelTop + 80, COLOR_TEXT_DIM);
            graphics.drawCenteredString(f, "Invite players below", panelLeft + panelW / 2, panelTop + 95, COLOR_TEXT_DIM);
        }

        // Invite section header
        int inviteSectionY = panelTop + panelH + 8;
        graphics.drawString(f, "> INVITE PLAYER", panelLeft + 5, inviteSectionY, COLOR_TEXT_DIM, false);

        // Input background - extends to make room for invite button beside it
        graphics.fill(panelLeft + 5, inviteSectionY + 12, panelLeft + 145, inviteSectionY + 32, 0xFF0A0A15);
        AxiomRenderer.drawBorder(graphics, panelLeft + 5, inviteSectionY + 12, 140, 20, 0xFF2A3A5A);
    }

    private void renderMobSelectionPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelLeft = panelX + 225;
        int panelTop = panelY + 80;
        int panelW = 160;
        int panelH = 230;
        net.minecraft.client.gui.Font f = getFont();

        // Panel background
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, 0xCC0A0A18);
        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, 0xFF2A3A5A);

        // Header
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + 22, 0xFF151528);
        graphics.drawString(f, "SELECT ENEMY", panelLeft + 8, panelTop + 7, COLOR_GLOW_CYAN, false);

        // Search background
        int searchY = panelTop + 28;
        graphics.fill(panelLeft + 5, searchY, panelLeft + panelW - 5, searchY + 20, 0xFF0A0A15);
        AxiomRenderer.drawBorder(graphics, panelLeft + 5, searchY, panelW - 10, 20, 0xFF2A3A5A);

        // Namespace filter buttons (All / MC / Mods)
        int nsFilterY = searchY + 25;
        int nsBtnX = panelLeft + 5;

        // "All" button
        boolean allActive = selectedNamespace == null;
        int allW = 28;
        int allColor = allActive ? 0xFF3D5AFE : 0xFF1A1A2A;
        boolean allHovered = mouseX >= nsBtnX && mouseX < nsBtnX + allW &&
                            mouseY >= nsFilterY && mouseY < nsFilterY + 12;
        if (allHovered) allColor = UIConstants.lighten(allColor, 0.2f);
        graphics.fill(nsBtnX, nsFilterY, nsBtnX + allW, nsFilterY + 12, allColor);
        AxiomRenderer.drawBorder(graphics, nsBtnX, nsFilterY, allW, 12, allActive ? 0xFF3D5AFE : 0xFF3A3A5A);
        graphics.drawCenteredString(f, "All", nsBtnX + allW / 2, nsFilterY + 2, COLOR_TEXT_WHITE);
        nsBtnX += allW + 2;

        // "MC" button for minecraft namespace
        boolean mcActive = "minecraft".equals(selectedNamespace);
        int mcW = 24;
        int mcColor = mcActive ? 0xFF44AA44 : 0xFF1A1A2A;
        boolean mcHovered = mouseX >= nsBtnX && mouseX < nsBtnX + mcW &&
                           mouseY >= nsFilterY && mouseY < nsFilterY + 12;
        if (mcHovered) mcColor = UIConstants.lighten(mcColor, 0.2f);
        graphics.fill(nsBtnX, nsFilterY, nsBtnX + mcW, nsFilterY + 12, mcColor);
        AxiomRenderer.drawBorder(graphics, nsBtnX, nsFilterY, mcW, 12, mcActive ? 0xFF44AA44 : 0xFF3A3A5A);
        graphics.drawCenteredString(f, "MC", nsBtnX + mcW / 2, nsFilterY + 2, COLOR_TEXT_WHITE);
        nsBtnX += mcW + 2;

        // Mod count indicator
        long modCount = availableNamespaces.stream().filter(ns -> !"minecraft".equals(ns)).count();
        if (modCount > 0) {
            graphics.drawString(f, "+" + modCount, nsBtnX + 2, nsFilterY + 2, COLOR_TEXT_DIM, false);
        }

        // Tier filter buttons (second row)
        int filterY = nsFilterY + 16;
        int btnW = 22;
        int btnX = panelLeft + 5;

        for (MobTier tier : MobTier.values()) {
            boolean active = tier == selectedTierFilter;
            int color = active ? getTierColor(tier) : 0xFF1A1A2A;
            boolean hovered = mouseX >= btnX && mouseX < btnX + btnW &&
                             mouseY >= filterY && mouseY < filterY + 14;

            if (hovered) color = UIConstants.lighten(color, 0.2f);

            graphics.fill(btnX, filterY, btnX + btnW, filterY + 14, color);
            AxiomRenderer.drawBorder(graphics, btnX, filterY, btnW, 14,
                active ? getTierColor(tier) : 0xFF3A3A5A);

            String initial = Objects.requireNonNull(tier.name().substring(0, 1));
            graphics.drawCenteredString(f, initial, btnX + btnW / 2, filterY + 3, COLOR_TEXT_WHITE);

            btnX += btnW + 2;
        }

        // Mob list
        int listY = filterY + 18;
        hoveredMobIndex = -1;

        for (int i = 0; i < MAX_VISIBLE_MOBS; i++) {
            int mobIndex = mobListScrollOffset + i;
            if (mobIndex >= filteredMobs.size()) break;

            EnduranceQuestRegistry.MobQuestConfig config = filteredMobs.get(mobIndex);
            int rowY = listY + i * 26;
            int rowH = 24;

            boolean isSelected = mobIndex == selectedMobIndex;
            boolean isHovered = mouseX >= panelLeft + 5 && mouseX < panelLeft + panelW - 5 &&
                    mouseY >= rowY && mouseY < rowY + rowH;

            if (isHovered) hoveredMobIndex = mobIndex;

            // Row background with selection effect
            if (isSelected) {
                int selectGlow = (int) (100 + glowPulse * 50);
                graphics.fill(panelLeft + 5, rowY, panelLeft + panelW - 5, rowY + rowH,
                    (selectGlow << 24) | (COLOR_GLOW_BLUE & 0x00FFFFFF));
                graphics.fill(panelLeft + 6, rowY + 1, panelLeft + panelW - 6, rowY + rowH - 1, 0xFF1A2A4A);
            } else if (isHovered) {
                graphics.fill(panelLeft + 5, rowY, panelLeft + panelW - 5, rowY + rowH, 0x30FFFFFF);
            }

            // Tier color bar
            int tierColor = getTierColor(config.tier);
            graphics.fill(panelLeft + 5, rowY + 2, panelLeft + 9, rowY + rowH - 2, tierColor);

            // Mob name
            String name = Objects.requireNonNull(config.displayName);
            if (f.width(name) > panelW - 40) {
                name = Objects.requireNonNull(f.plainSubstrByWidth(name, panelW - 45)) + "..";
            }
            int nameColor = isSelected ? COLOR_GLOW_CYAN : COLOR_TEXT_WHITE;
            graphics.drawString(f, name, panelLeft + 14, rowY + 4, nameColor, false);

            // Difficulty preset icon
            String preset = getPresetSymbol(config.difficultyPreset);
            graphics.drawString(f, preset, panelLeft + 14, rowY + 14, COLOR_TEXT_DIM, false);
        }

        // Scroll indicators
        if (mobListScrollOffset > 0) {
            graphics.drawCenteredString(f, "^", panelLeft + panelW / 2, listY - 8, COLOR_GLOW_BLUE);
        }
        if (mobListScrollOffset + MAX_VISIBLE_MOBS < filteredMobs.size()) {
            graphics.drawCenteredString(f, "v", panelLeft + panelW / 2, listY + MAX_VISIBLE_MOBS * 26 + 2, COLOR_GLOW_BLUE);
        }

        // Count
        graphics.drawString(f, filteredMobs.size() + " enemies", panelLeft + 8, panelTop + panelH - 14, COLOR_TEXT_DIM, false);
    }

    private void renderMobPreviewPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelLeft = panelX + 395;
        int panelTop = panelY + 80;
        int panelW = 190;
        int panelH = 230;
        net.minecraft.client.gui.Font f = getFont();

        // Panel background with gradient
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, 0xCC0A0A18);

        // Inner darker area for preview
        int previewArea = panelTop + 22;
        graphics.fill(panelLeft + 5, previewArea, panelLeft + panelW - 5, previewArea + 120, 0xFF050510);

        // Animated circular platform effect
        int centerX = panelLeft + panelW / 2;
        int platformY = previewArea + 110;
        int platformRadius = 40;

        // Platform glow
        for (int r = platformRadius; r > platformRadius - 5; r--) {
            int alpha = (int) ((1 - (platformRadius - r) / 5f) * (50 + glowPulse * 30));
            int glowC = (alpha << 24) | (COLOR_GLOW_BLUE & 0x00FFFFFF);
            graphics.fill(centerX - r, platformY - 3, centerX + r, platformY, glowC);
        }

        // Platform line
        graphics.fill(centerX - platformRadius + 5, platformY - 1, centerX + platformRadius - 5, platformY, COLOR_GLOW_BLUE);

        AxiomRenderer.drawBorder(graphics, panelLeft, panelTop, panelW, panelH, 0xFF2A3A5A);

        // Header
        graphics.fill(panelLeft, panelTop, panelLeft + panelW, panelTop + 22, 0xFF151528);
        graphics.drawString(f, "PREVIEW", panelLeft + 8, panelTop + 7, COLOR_GLOW_CYAN, false);

        // Smooth rotation
        mobRotationY = Mth.lerp(0.15f, mobRotationY, targetMobRotationY);

        LivingEntity entity = previewEntity;
        if (entity != null && !filteredMobs.isEmpty() && selectedMobIndex < filteredMobs.size()) {
            int entityCenterY = previewArea + 85;

            // Calculate scale
            float mobHeight = entity.getBbHeight();
            float mobWidth = entity.getBbWidth();
            float maxDim = Math.max(mobHeight, mobWidth);
            int scale = (int) Math.min(40, 80 / maxDim);

            Quaternionf rotation = Objects.requireNonNull(new Quaternionf()
                    .rotateY((float) Math.toRadians(mobRotationY))
                    .rotateX((float) Math.toRadians(mobRotationX))
                    .rotateZ((float) Math.PI));

            try {
                InventoryScreen.renderEntityInInventory(
                        graphics, centerX, entityCenterY, scale,
                        new Vector3f(0, 0, 0), rotation, null, entity
                );
            } catch (Exception e) {
                graphics.drawCenteredString(f, "[Preview Error]", centerX, entityCenterY - 20, COLOR_TEXT_DIM);
            }

            EnduranceQuestRegistry.MobQuestConfig config = filteredMobs.get(selectedMobIndex);

            // Stats section
            int statsY = previewArea + 125;

            // Name with tier color
            graphics.drawCenteredString(f, Objects.requireNonNull(config.displayName), centerX, statsY, getTierColor(config.tier));

            // Tier badge
            String tierBadge = Objects.requireNonNull(getTierBadge(config.tier));
            graphics.drawCenteredString(f, tierBadge, centerX, statsY + 12, getTierColor(config.tier));

            // Stats grid
            int statY = statsY + 28;
            int col1 = panelLeft + 15;
            int col2 = panelLeft + panelW / 2 + 5;

            // Base stats
            graphics.drawString(f, "HP", col1, statY, 0xFFFF6666, false);
            graphics.drawString(f, String.format("%.0f", config.baseHealth), col1 + 25, statY, COLOR_TEXT_WHITE, false);

            graphics.drawString(f, "DMG", col2, statY, 0xFFFFAA00, false);
            graphics.drawString(f, String.format("%.0f", config.baseDamage), col2 + 30, statY, COLOR_TEXT_WHITE, false);

            // Scaled stats
            statY += 14;
            int playerCount = Math.max(1, members.size());
            float scaledHP = config.getScaledHealth(playerCount, questType);
            float scaledDMG = config.getScaledDamage(playerCount);

            graphics.drawString(f, "Scaled", col1, statY, COLOR_TEXT_DIM, false);
            graphics.drawString(f, String.format("%.0f", scaledHP), col1 + 40, statY, COLOR_READY, false);
            graphics.drawString(f, String.format("%.0f", scaledDMG), col2 + 30, statY, 0xFFFFFF00, false);

            // Points
            statY += 14;
            graphics.drawString(f, "Points/Kill: " + config.pointsPerKill, col1, statY, COLOR_TEXT_GRAY, false);

        } else {
            graphics.drawCenteredString(f, "Select an enemy", centerX, previewArea + 60, COLOR_TEXT_DIM);
        }

        // Drag hint
        boolean hovering = mouseX >= panelLeft + 5 && mouseX < panelLeft + panelW - 5 &&
                mouseY >= previewArea && mouseY < previewArea + 120;
        if (hovering && !isDraggingPreview) {
            graphics.drawString(f, "[Drag to rotate]", panelLeft + 8, previewArea + 4, 0x60FFFFFF, false);
        }
    }

    private void renderWaveStatsBar(GuiGraphics graphics, int mouseX, int mouseY) {
        if (filteredMobs.isEmpty() || selectedMobIndex >= filteredMobs.size()) return;

        int barY = panelY + PANEL_HEIGHT - 85;
        int barX = panelX + 15;
        int barW = PANEL_WIDTH - 30;
        net.minecraft.client.gui.Font f = getFont();

        // Separator
        graphics.fill(barX, barY - 5, barX + barW, barY - 3, 0xFF2A3A5A);

        // Wave slider section
        graphics.drawString(f, "> WAVE PREVIEW", barX, barY, COLOR_TEXT_DIM, false);

        // Slider
        int sliderX = barX + 100;
        int sliderW = 150;
        int sliderY = barY;

        // Slider background
        graphics.fill(sliderX, sliderY + 2, sliderX + sliderW, sliderY + 10, 0xFF151525);
        AxiomRenderer.drawBorder(graphics, sliderX, sliderY + 2, sliderW, 8, 0xFF2A3A5A);

        // Slider fill
        float progress = (previewWaveNumber - 1) / (float) (MAX_PREVIEW_WAVE - 1);
        int fillW = (int) (sliderW * progress);
        graphics.fill(sliderX + 1, sliderY + 3, sliderX + fillW, sliderY + 9, COLOR_GLOW_BLUE);

        // Wave number
        graphics.drawString(f, "Wave " + previewWaveNumber, sliderX + sliderW + 10, sliderY, COLOR_TEXT_WHITE, false);

        // Stats row
        int statsY = barY + 16;
        EnduranceQuestRegistry.MobQuestConfig config = filteredMobs.get(selectedMobIndex);
        int playerCount = Math.max(1, members.size());

        int mobCount = config.getMobCountForWave(previewWaveNumber, playerCount, questType);
        float scaledHP = config.getScaledHealth(playerCount, questType);
        float scaledDMG = config.getScaledDamage(playerCount);
        float waveMultiplier = 1.0f + (previewWaveNumber - 1) * 0.05f;

        // Stats with labels
        int col = barX;
        graphics.drawString(f, "Mobs: " + mobCount, col, statsY, COLOR_TEXT_WHITE, false);
        col += 80;
        graphics.drawString(f, String.format("HP: %.0f", scaledHP * waveMultiplier), col, statsY, 0xFFFF6666, false);
        col += 90;
        graphics.drawString(f, String.format("DMG: %.0f", scaledDMG * waveMultiplier), col, statsY, 0xFFFFAA00, false);
        col += 90;
        graphics.drawString(f, String.format("Points: %d", mobCount * config.pointsPerKill), col, statsY, 0xFFFFFF00, false);
        col += 100;
        graphics.drawString(f, String.format("Difficulty: %.1fx", questType.difficultyMultiplier * waveMultiplier), col, statsY, 0xFFAA66FF, false);
    }

    private void renderNoPartyState(GuiGraphics graphics) {
        int centerX = panelX + PANEL_WIDTH / 2;
        int centerY = panelY + PANEL_HEIGHT / 2;
        net.minecraft.client.gui.Font f = getFont();

        // Clear title
        String noParty = "NO ACTIVE PARTY";

        // Glow effect
        int glowAlpha = (int) (glowPulse * 80);
        graphics.drawString(f, noParty, centerX - f.width(noParty) / 2 - 1, centerY - 70, (glowAlpha << 24) | (COLOR_GLOW_CYAN & 0x00FFFFFF), false);
        graphics.drawString(f, noParty, centerX - f.width(noParty) / 2 + 1, centerY - 70, (glowAlpha << 24) | (COLOR_GLOW_CYAN & 0x00FFFFFF), false);

        graphics.drawCenteredString(f, noParty, centerX, centerY - 70, COLOR_GLOW_CYAN);

        // Clear instructions
        graphics.drawCenteredString(f, "Click CREATE PARTY to start a new group,", centerX, centerY - 45, COLOR_TEXT_WHITE);
        graphics.drawCenteredString(f, "then invite other players to join you.", centerX, centerY - 30, COLOR_TEXT_GRAY);

        // Decorative line
        int lineW = 140;
        graphics.fill(centerX - lineW, centerY - 15, centerX + lineW, centerY - 14, 0xFF2A3A5A);
        graphics.fill(centerX - 40, centerY - 15, centerX + 40, centerY - 14, COLOR_GLOW_BLUE);

        // Or wait for invite
        graphics.drawCenteredString(f, "- OR -", centerX, centerY, COLOR_TEXT_DIM);
        graphics.drawCenteredString(f, "Wait for another player to invite you.", centerX, centerY + 15, COLOR_TEXT_GRAY);
        graphics.drawCenteredString(f, "Invites will appear as a popup notification.", centerX, centerY + 30, COLOR_TEXT_DIM);
    }

    private String getPresetSymbol(MobDifficultyPreset preset) {
        return switch (preset) {
            case SWARM -> "[Swarm]";
            case STANDARD -> "[Standard]";
            case TANK -> "[Tank]";
            case GLASS_CANNON -> "[Glass]";
            case BOSS_STYLE -> "[Boss]";
        };
    }

    private int getTierColor(MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> 0xFF666666;
            case EASY -> 0xFF55FF55;
            case MEDIUM -> 0xFFFFFF55;
            case HARD -> 0xFFFF8800;
            case ELITE -> 0xFFFF5555;
            case BOSS -> 0xFFAA00FF;
        };
    }

    private String getTierBadge(MobTier tier) {
        return switch (tier) {
            case TRIVIAL -> "* Trivial";
            case EASY -> "** Easy";
            case MEDIUM -> "*** Medium";
            case HARD -> "**** Hard";
            case ELITE -> "***** Elite";
            case BOSS -> "BOSS";
        };
    }

    // === EVENT HANDLERS ===

    private void onInviteClicked(Button button) {
        String playerName = inviteBox.getValue().trim();
        if (playerName.isEmpty()) return;

        LOGGER.info("[PartyScreen] Sending invite to: {}", playerName);
        PacketDistributor.sendToServer(Objects.requireNonNull(NamedInvitePayload.create(playerName, questType)));
        inviteBox.setValue("");
        UIConstants.Sound.success();
    }

    private void onReadyClicked(Button button) {
        isReady = !isReady;
        ClientPartyCache.setLocalPlayerReady(isReady);
        PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.toggleReady()));
        updateButtonStates();
        UIConstants.Sound.toggleOn();
    }

    private void onLeaveClicked(Button button) {
        LOGGER.info("[PartyScreen] Leaving party");
        PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.leaveParty()));
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
        PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.startQuest()));
        onClose();
        UIConstants.Sound.success();
    }

    private void onDisbandClicked(Button button) {
        LOGGER.info("[PartyScreen] Disbanding party");
        PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.disbandParty()));
        onClose();
        UIConstants.Sound.warning();
    }

    private void onCreatePartyClicked(Button button) {
        LOGGER.info("[PartyScreen] Creating party with type: {}", questType);
        PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.createParty(questType)));
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
        // Quest type tabs
        if (isInParty && isLeader && hoveredQuestTab >= 0 && hoveredQuestTab < QuestType.values().length) {
            questType = QuestType.values()[hoveredQuestTab];
            ClientPartyCache.setQuestType(questType);
            PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.setQuestType(questType)));
            UIConstants.Sound.click();
            updateButtonStates();
            return true;
        }

        // Namespace filter clicks
        if (isInParty) {
            int nsFilterY = panelY + 80 + 28 + 25;
            int nsBtnX = panelX + 230;

            // "All" button
            int allW = 28;
            if (mouseX >= nsBtnX && mouseX < nsBtnX + allW &&
                mouseY >= nsFilterY && mouseY < nsFilterY + 12) {
                selectedNamespace = null;
                filterMobs();
                UIConstants.Sound.click();
                return true;
            }
            nsBtnX += allW + 2;

            // "MC" button
            int mcW = 24;
            if (mouseX >= nsBtnX && mouseX < nsBtnX + mcW &&
                mouseY >= nsFilterY && mouseY < nsFilterY + 12) {
                selectedNamespace = "minecraft".equals(selectedNamespace) ? null : "minecraft";
                filterMobs();
                UIConstants.Sound.click();
                return true;
            }
        }

        // Tier filter clicks
        if (isInParty) {
            int filterY = panelY + 80 + 28 + 25 + 16;
            int btnW = 22;
            int btnX = panelX + 230;

            for (MobTier tier : MobTier.values()) {
                if (mouseX >= btnX && mouseX < btnX + btnW &&
                    mouseY >= filterY && mouseY < filterY + 14) {
                    selectedTierFilter = (tier == selectedTierFilter) ? null : tier;
                    filterMobs();
                    UIConstants.Sound.click();
                    return true;
                }
                btnX += btnW + 2;
            }
        }

        // Mob list clicks
        if (isInParty && isLeader && hoveredMobIndex >= 0 && hoveredMobIndex < filteredMobs.size()) {
            selectedMobIndex = hoveredMobIndex;
            updatePreviewEntity();
            ResourceLocation mobId = getSelectedMobId();
            if (mobId != null) {
                PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.setMobType(mobId)));
            }
            UIConstants.Sound.click();
            return true;
        }

        // Wave slider
        if (isInParty) {
            int sliderX = panelX + 115;
            int sliderY = panelY + PANEL_HEIGHT - 85;
            int sliderW = 150;

            if (mouseX >= sliderX && mouseX < sliderX + sliderW &&
                    mouseY >= sliderY && mouseY < sliderY + 14) {
                float progress = (float) (mouseX - sliderX) / sliderW;
                previewWaveNumber = 1 + (int) (progress * (MAX_PREVIEW_WAVE - 1));
                previewWaveNumber = Mth.clamp(previewWaveNumber, 1, MAX_PREVIEW_WAVE);
                return true;
            }
        }

        // 3D Preview drag
        int previewX = panelX + 400;
        int previewY = panelY + 102;
        int boxW = 180;
        int boxH = 120;

        if (mouseX >= previewX && mouseX < previewX + boxW &&
                mouseY >= previewY && mouseY < previewY + boxH) {
            isDraggingPreview = true;
            dragStartX = (int) mouseX;
            dragStartY = (int) mouseY;
            dragStartRotX = mobRotationX;
            dragStartRotY = mobRotationY;
            return true;
        }

        // Member kick
        if (hoveredMemberIndex >= 0 && hoveredMemberIndex < members.size() && isLeader && button == 1) {
            PartySyncPayload.PartyMemberInfo member = members.get(hoveredMemberIndex);
            if (!member.playerId().equals(leaderId)) {
                PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.kickMember(member.playerId())));
                UIConstants.Sound.warning();
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
        int sliderX = panelX + 115;
        int sliderY = panelY + PANEL_HEIGHT - 85;
        int sliderW = 150;

        if (mouseX >= sliderX - 10 && mouseX < sliderX + sliderW + 10 &&
                mouseY >= sliderY - 5 && mouseY < sliderY + 20) {
            float progress = (float) (mouseX - sliderX) / sliderW;
            previewWaveNumber = 1 + (int) (progress * (MAX_PREVIEW_WAVE - 1));
            previewWaveNumber = Mth.clamp(previewWaveNumber, 1, MAX_PREVIEW_WAVE);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listX = panelX + 230;
        int listY = panelY + 80 + 28 + 25 + 16 + 18; // searchY + nsFilterY offset + tierFilterY offset + listOffset
        int listW = 150;
        int listH = MAX_VISIBLE_MOBS * 26;

        if (mouseX >= listX && mouseX < listX + listW &&
                mouseY >= listY && mouseY < listY + listH) {
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
