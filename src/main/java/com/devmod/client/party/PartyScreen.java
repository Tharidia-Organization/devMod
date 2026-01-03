package com.devmod.client.party;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.UiSounds;
import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.EnduranceQuestRegistry.MobTier;
import com.devmod.endurance.QuestType;
import com.devmod.party.NamedInvitePayload;
import com.devmod.party.PartyActionPayload;
import com.devmod.party.PartyData;
import com.devmod.party.PartySyncPayload;

@OnlyIn(Dist.CLIENT)
public class PartyScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyScreen.class);

    // === DESIGN CONSTANTS ===
    private static final int PANEL_WIDTH = 600;
    private static final int PANEL_HEIGHT = 420;

    // Animation
    private float animationTick = 0f;
    private float glowPulse = 0f;
    private float titleGlow = 0f;
    private final float[] memberAnimations = new float[8];

    // Party State
    private List<PartySyncPayload.PartyMemberInfo> members = new ArrayList<>();
    @Nullable
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
    @Nullable private ResourceLocation selectedMobId = null;
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
    @Nullable private EditBox inviteBox;
    @Nullable private EditBox mobSearchBox;
    @Nullable private EditorButton createPartyButton;
    @Nullable private EditorButton readyButton;
    @Nullable private EditorButton startButton;
    @Nullable private EditorButton leaveButton;
    @Nullable private EditorButton disbandButton;
    @Nullable private EditorButton inviteButton;

    @Nullable private ButtonArea inviteButtonBounds;
    @Nullable private ButtonArea readyButtonBounds;
    @Nullable private ButtonArea startButtonBounds;
    @Nullable private ButtonArea leaveButtonBounds;
    @Nullable private ButtonArea disbandButtonBounds;
    @Nullable private ButtonArea createPartyButtonBounds;

    private int hoveredMemberIndex = -1;
    private int hoveredMobIndex = -1;
    private int hoveredQuestTab = -1;

    private int panelX, panelY;
    private int originalBlurValue = 0;

    // Renderer delegate
    @Nullable private PartyScreenRenderer renderer;

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

    // ========== PUBLIC ACCESSORS FOR RENDERER ==========

    /** Returns font for renderer use. */
    @Nonnull
    public net.minecraft.client.gui.Font getScreenFont() {
        return getFont();
    }

    public int getPanelX() { return panelX; }
    public int getPanelY() { return panelY; }
    public int getPanelWidth() { return PANEL_WIDTH; }
    public int getPanelHeight() { return PANEL_HEIGHT; }

    public float getGlowPulse() { return glowPulse; }
    public float getTitleGlow() { return titleGlow; }

    public boolean isInParty() { return isInParty; }
    public PartyData.PartyState getPartyState() { return partyState; }
    public QuestType getQuestType() { return questType; }

    public List<PartySyncPayload.PartyMemberInfo> getMembers() { return members; }
    @Nullable public UUID getLeaderId() { return leaderId; }
    public boolean isLeader() { return isLeader; }
    public float[] getMemberAnimations() { return memberAnimations; }

    public List<EnduranceQuestRegistry.MobQuestConfig> getFilteredMobs() { return filteredMobs; }
    public int getSelectedMobIndex() { return selectedMobIndex; }
    public int getMobListScrollOffset() { return mobListScrollOffset; }
    public boolean isSelectedMobFilteredOut() {
        ResourceLocation mobId = selectedMobId;
        if (mobId == null) {
            return false;
        }
        return findMobIndex(mobId, filteredMobs) < 0;
    }

    @Nullable public String getSelectedNamespace() { return selectedNamespace; }
    @Nullable public MobTier getSelectedTierFilter() { return selectedTierFilter; }
    public java.util.Set<String> getAvailableNamespaces() { return availableNamespaces; }

    @Nullable public LivingEntity getPreviewEntity() { return previewEntity; }
    public boolean isDraggingPreview() { return isDraggingPreview; }

    public float getMobRotationY() { return mobRotationY; }
    public float getTargetMobRotationY() { return targetMobRotationY; }
    public float getMobRotationX() { return mobRotationX; }
    public void setMobRotationY(float value) { this.mobRotationY = value; }

    public int getPreviewWaveNumber() { return previewWaveNumber; }

    @Nullable
    public EnduranceQuestRegistry.MobQuestConfig getSelectedMobConfig() {
        ResourceLocation mobId = selectedMobId;
        if (mobId != null) {
            for (EnduranceQuestRegistry.MobQuestConfig config : availableMobs) {
                if (config.mobId.equals(mobId)) {
                    return config;
                }
            }
            return null;
        }
        if (selectedMobIndex >= 0 && selectedMobIndex < filteredMobs.size()) {
            return filteredMobs.get(selectedMobIndex);
        }
        return null;
    }

    @Override
    protected void init() {
        super.init();

        int actualWidth = Math.min(PANEL_WIDTH, width - 40);
        int actualHeight = Math.min(PANEL_HEIGHT, height - 40);
        panelX = (width - actualWidth) / 2;
        panelY = (height - actualHeight) / 2;

        // Initialize renderer
        this.renderer = new PartyScreenRenderer(this);

        loadAvailableMobs();
        refreshFromCache();

        // === Create UI Components ===
        initInviteSection();
        initMobSearchBox();
        initActionButtons();

        updateButtonStates();
        updatePreviewEntity();
    }

    private void initInviteSection() {
        // Use same coordinates as renderMembersPanel for invite section
        // Members panel: panelLeft = panelX + 15, panelTop = panelY + 80, panelH = 200
        int membersPanelLeft = panelX + 15;
        int membersPanelTop = panelY + 80;
        int membersPanelH = 200;
        int inviteSectionY = membersPanelTop + membersPanelH + 8;  // = panelY + 288
        // Input background at: (membersPanelLeft + 5, inviteSectionY + 12) with size 140x20
        int inviteBoxX = membersPanelLeft + 5 + 3;   // 3px inside border
        int inviteBoxY = inviteSectionY + 12 + 3;    // 3px inside border
        var box = new EditBox(getFont(), inviteBoxX, inviteBoxY, 130, 14,
                Objects.requireNonNull(Component.literal("Player name...")));
        box.setHint(Objects.requireNonNull(Component.literal("Enter name...")));
        box.setMaxLength(16);
        box.setBordered(false);
        addRenderableWidget(box);
        inviteBox = box;

        // Invite button - positioned right after the input background
        int inviteButtonX = membersPanelLeft + 5 + 140 + 3;  // After input bg (140 wide) + gap
        inviteButton = EditorButton.builder("invite", "INVITE")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onInviteClicked)
            .build();
        inviteButtonBounds = new ButtonArea(inviteButtonX, inviteSectionY + 12, 50, 20);
    }

    private void initMobSearchBox() {
        // Mob search box - inside mob selection panel after header
        // Mob panel: panelLeft = panelX + 225, panelTop = panelY + 80
        // Search background at: (panelLeft + 5, panelTop + 28) with size (panelW - 10) x 20
        int mobPanelLeft = panelX + 225;
        int mobPanelTop = panelY + 80;
        int mobSearchY = mobPanelTop + 28 + 3;  // 3px inside the search background
        var searchBox = new EditBox(getFont(), mobPanelLeft + 8, mobSearchY, 140, 14,
                Objects.requireNonNull(Component.literal("Search...")));
        searchBox.setHint(Objects.requireNonNull(Component.literal("Search mobs...")));
        searchBox.setMaxLength(32);
        searchBox.setBordered(false);
        searchBox.setResponder(this::onMobSearchChanged);
        addRenderableWidget(searchBox);
        mobSearchBox = searchBox;
    }

    private void initActionButtons() {
        // Bottom action buttons
        int buttonY = panelY + PANEL_HEIGHT - 45;
        int centerX = panelX + PANEL_WIDTH / 2;

        readyButton = EditorButton.builder("ready", getReadyButtonText().getString())
            .style(EditorButton.Style.PRIMARY)
            .toggleable(true)
            .toggled(isReady)
            .size(EditorButton.Size.LARGE)
            .onClick(this::onReadyClicked)
            .build();
        readyButtonBounds = new ButtonArea(centerX - 180, buttonY, 80, 24);

        leaveButton = EditorButton.builder("leave", "LEAVE")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.LARGE)
            .onClick(this::onLeaveClicked)
            .build();
        leaveButtonBounds = new ButtonArea(centerX - 90, buttonY, 70, 24);

        startButton = EditorButton.builder("start-quest", "START QUEST")
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.LARGE)
            .onClick(this::onStartClicked)
            .build();
        startButtonBounds = new ButtonArea(centerX - 10, buttonY, 120, 24);

        disbandButton = EditorButton.builder("disband", "DISBAND")
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.LARGE)
            .onClick(this::onDisbandClicked)
            .build();
        disbandButtonBounds = new ButtonArea(centerX + 120, buttonY, 70, 24);

        // Create Party button (when not in party)
        createPartyButton = EditorButton.builder("create-party", "CREATE PARTY")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.LARGE)
            .onClick(this::onCreatePartyClicked)
            .build();
        createPartyButtonBounds = new ButtonArea(centerX - 80, panelY + PANEL_HEIGHT / 2 + 20, 160, 30);
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

        if (selectedMobId == null) {
            for (EnduranceQuestRegistry.MobQuestConfig config : availableMobs) {
                if ("zombie".equals(config.mobId.getPath())) {
                    selectedMobId = config.mobId;
                    break;
                }
            }
        }

        filterMobs();
    }

    private void filterMobs() {
        filteredMobs = availableMobs.stream()
                .filter(m -> {
                    if (selectedNamespace != null && !m.namespace.equals(selectedNamespace)) return false;
                    if (selectedTierFilter != null && m.tier != selectedTierFilter) return false;
                    if (!mobSearchText.isEmpty()) {
                        String search = mobSearchText.toLowerCase(Locale.ROOT);
                        return m.displayName.toLowerCase(Locale.ROOT).contains(search) ||
                                m.mobId.toString().toLowerCase(Locale.ROOT).contains(search);
                    }
                    return true;
                })
                .collect(Collectors.toList());

        int newIndex = findMobIndex(selectedMobId, filteredMobs);
        if (newIndex < 0 && selectedMobId == null && !filteredMobs.isEmpty()) {
            selectedMobIndex = 0;
            selectedMobId = filteredMobs.get(0).mobId;
        } else {
            selectedMobIndex = newIndex;
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
                ResourceLocation previousMobId = selectedMobId;
                ResourceLocation mobId = party.getSelectedMobResourceLocation();
                if (mobId != null) {
                    selectedMobId = mobId;
                }

                int newIndex = findMobIndex(selectedMobId, filteredMobs);
                boolean selectionChanged = !Objects.equals(previousMobId, selectedMobId);
                if (newIndex != selectedMobIndex || selectionChanged) {
                    selectedMobIndex = newIndex;
                    updatePreviewEntity();
                } else if (previewEntity == null && selectedMobId != null) {
                    updatePreviewEntity();
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
            refreshFromCache();
            updateButtonStates();
        }
    }

    private void updateButtonStates() {
        readyButton = EditorButton.builder("ready", getReadyButtonText().getString())
            .style(EditorButton.Style.PRIMARY)
            .toggleable(true)
            .toggled(isReady)
            .size(EditorButton.Size.LARGE)
            .onClick(this::onReadyClicked)
            .build();

        if (startButton != null) {
            startButton.enabled(isLeader && canStartQuest());
        }
        if (disbandButton != null) {
            disbandButton.enabled(isLeader);
        }
        if (inviteButton != null) {
            inviteButton.enabled(isInParty && isLeader);
        }
        if (leaveButton != null) {
            leaveButton.enabled(isInParty);
        }
        if (createPartyButton != null) {
            createPartyButton.enabled(!isInParty);
        }
        var localInviteBox = inviteBox;
        if (localInviteBox != null) {
            localInviteBox.visible = isInParty;
            localInviteBox.setEditable(isLeader);
        }
        var localMobSearchBox = mobSearchBox;
        if (localMobSearchBox != null) {
            localMobSearchBox.visible = isInParty;
        }
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
        if (previewEntity != null) {
            previewEntity.discard();
            previewEntity = null;
        }
        EnduranceQuestRegistry.MobQuestConfig config = getSelectedMobConfig();
        if (config == null) {
            return;
        }

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

        // Delegate all rendering to renderer
        PartyScreenRenderer safeRenderer = renderer;
        if (safeRenderer == null) {
            return;
        }
        safeRenderer.renderMainPanel(graphics);

        if (isInParty) {
            hoveredQuestTab = safeRenderer.renderQuestTypeTabs(graphics, mouseX, mouseY);
            hoveredMemberIndex = safeRenderer.renderMembersPanel(graphics, mouseX, mouseY);
            hoveredMobIndex = safeRenderer.renderMobSelectionPanel(graphics, mouseX, mouseY);
            safeRenderer.renderMobPreviewPanel(graphics, mouseX, mouseY);
            safeRenderer.renderWaveStatsBar(graphics, mouseX, mouseY);
        } else {
            safeRenderer.renderNoPartyState(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        renderButtons(graphics, mouseX, mouseY);
    }

    // === EVENT HANDLERS ===

    private void onInviteClicked() {
        var box = inviteBox;
        if (box == null) {
            return;
        }
        String playerName = box.getValue().trim();
        if (playerName.isEmpty()) return;

        LOGGER.info("[PartyScreen] Sending invite to: {}", playerName);
        PacketDistributor.sendToServer(Objects.requireNonNull(NamedInvitePayload.create(playerName, questType)));
        box.setValue("");
        UiSounds.success();
    }

    private void onReadyClicked() {
        isReady = !isReady;
        ClientPartyCache.setLocalPlayerReady(isReady);
        PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.toggleReady()));
        updateButtonStates();
        UiSounds.toggleOn();
    }

    private void onLeaveClicked() {
        LOGGER.info("[PartyScreen] Leaving party");
        PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.leaveParty()));
        onClose();
        UiSounds.click();
    }

    private void onStartClicked() {
        if (!canStartQuest()) {
            UiSounds.error();
            return;
        }

        LOGGER.info("[PartyScreen] Starting quest with {} players, mob: {}",
                members.size(), getSelectedMobId());
        PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.startQuest()));
        onClose();
        UiSounds.success();
    }

    private void onDisbandClicked() {
        LOGGER.info("[PartyScreen] Disbanding party");
        PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.disbandParty()));
        onClose();
        UiSounds.warning();
    }

    private void onCreatePartyClicked() {
        LOGGER.info("[PartyScreen] Creating party with type: {}", questType);
        PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.createParty(questType)));
        isInParty = true;
        isLeader = true;
        updateButtonStates();
        UiSounds.success();
    }

    @Nullable
    private ResourceLocation getSelectedMobId() {
        if (selectedMobId != null) {
            return selectedMobId;
        }
        if (selectedMobIndex < 0 || selectedMobIndex >= filteredMobs.size()) return null;
        return filteredMobs.get(selectedMobIndex).mobId;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Quest type tabs
        if (isInParty && isLeader && hoveredQuestTab >= 0 && hoveredQuestTab < QuestType.values().length) {
            questType = QuestType.values()[hoveredQuestTab];
            ClientPartyCache.setQuestType(questType);
            PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.setQuestType(questType)));
            UiSounds.click();
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
                UiSounds.click();
                return true;
            }
            nsBtnX += allW + 2;

            // "MC" button
            int mcW = 24;
            if (mouseX >= nsBtnX && mouseX < nsBtnX + mcW &&
                mouseY >= nsFilterY && mouseY < nsFilterY + 12) {
                selectedNamespace = "minecraft".equals(selectedNamespace) ? null : "minecraft";
                filterMobs();
                UiSounds.click();
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
                    UiSounds.click();
                    return true;
                }
                btnX += btnW + 2;
            }
        }

        // Mob list clicks
        if (isInParty && isLeader && hoveredMobIndex >= 0 && hoveredMobIndex < filteredMobs.size()) {
            selectedMobIndex = hoveredMobIndex;
            selectedMobId = filteredMobs.get(selectedMobIndex).mobId;
            updatePreviewEntity();
            ResourceLocation mobId = getSelectedMobId();
            if (mobId != null) {
                PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.setMobType(mobId)));
            }
            UiSounds.click();
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

        if (handleButtonClick(mouseX, mouseY, button)) {
            return true;
        }

        // Member kick
        if (hoveredMemberIndex >= 0 && hoveredMemberIndex < members.size() && isLeader && button == 1) {
            PartySyncPayload.PartyMemberInfo member = members.get(hoveredMemberIndex);
            if (!member.playerId().equals(leaderId)) {
                PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.kickMember(member.playerId())));
                UiSounds.warning();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingPreview = false;
        if (handleButtonRelease(mouseX, mouseY, button)) {
            return true;
        }
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

    private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isInParty) {
            var localInviteBtn = inviteButton;
            var localInviteBounds = inviteButtonBounds;
            if (localInviteBtn != null && localInviteBounds != null) {
                localInviteBtn.enabled(isInParty && isLeader);
                localInviteBtn.render(graphics, localInviteBounds.x, localInviteBounds.y,
                    localInviteBounds.width, localInviteBounds.height, mouseX, mouseY);
            }
            var localReadyBtn = readyButton;
            var localReadyBounds = readyButtonBounds;
            if (localReadyBtn != null && localReadyBounds != null) {
                localReadyBtn.render(graphics, localReadyBounds.x, localReadyBounds.y,
                    localReadyBounds.width, localReadyBounds.height, mouseX, mouseY);
            }
            var localLeaveBtn = leaveButton;
            var localLeaveBounds = leaveButtonBounds;
            if (localLeaveBtn != null && localLeaveBounds != null) {
                localLeaveBtn.enabled(isInParty);
                localLeaveBtn.render(graphics, localLeaveBounds.x, localLeaveBounds.y,
                    localLeaveBounds.width, localLeaveBounds.height, mouseX, mouseY);
            }
            var localStartBtn = startButton;
            var localStartBounds = startButtonBounds;
            if (localStartBtn != null && localStartBounds != null) {
                localStartBtn.enabled(isLeader && canStartQuest());
                localStartBtn.render(graphics, localStartBounds.x, localStartBounds.y,
                    localStartBounds.width, localStartBounds.height, mouseX, mouseY);
            }
            var localDisbandBtn = disbandButton;
            var localDisbandBounds = disbandButtonBounds;
            if (localDisbandBtn != null && localDisbandBounds != null && isLeader) {
                localDisbandBtn.enabled(isLeader);
                localDisbandBtn.render(graphics, localDisbandBounds.x, localDisbandBounds.y,
                    localDisbandBounds.width, localDisbandBounds.height, mouseX, mouseY);
            }
        } else {
            var localCreateBtn = createPartyButton;
            var localCreateBounds = createPartyButtonBounds;
            if (localCreateBtn != null && localCreateBounds != null) {
                localCreateBtn.enabled(!isInParty);
                localCreateBtn.render(graphics, localCreateBounds.x, localCreateBounds.y,
                    localCreateBounds.width, localCreateBounds.height, mouseX, mouseY);
            }
        }
    }

    private boolean handleButtonClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (isInParty) {
            if (inviteButton != null && inviteButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (readyButton != null && readyButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (leaveButton != null && leaveButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (startButton != null && startButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (disbandButton != null && isLeader && disbandButton.mouseClicked(mouseX, mouseY, button)) return true;
        } else {
            if (createPartyButton != null && createPartyButton.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    private boolean handleButtonRelease(double mouseX, double mouseY, int button) {
        boolean handled = false;
        if (isInParty) {
            if (inviteButton != null) handled |= inviteButton.mouseReleased(mouseX, mouseY, button);
            if (readyButton != null) handled |= readyButton.mouseReleased(mouseX, mouseY, button);
            if (leaveButton != null) handled |= leaveButton.mouseReleased(mouseX, mouseY, button);
            if (startButton != null) handled |= startButton.mouseReleased(mouseX, mouseY, button);
            if (disbandButton != null && isLeader) handled |= disbandButton.mouseReleased(mouseX, mouseY, button);
        } else {
            if (createPartyButton != null) handled |= createPartyButton.mouseReleased(mouseX, mouseY, button);
        }
        return handled;
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

    private record ButtonArea(int x, int y, int width, int height) { }

    private int findMobIndex(@Nullable ResourceLocation mobId, List<EnduranceQuestRegistry.MobQuestConfig> list) {
        if (mobId == null) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).mobId.equals(mobId)) {
                return i;
            }
        }
        return -1;
    }
}
