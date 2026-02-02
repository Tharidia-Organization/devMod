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

import com.devmod.client.endurance.KitSelectionScreen;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.UiSounds;
import com.devmod.endurance.CustomKit;
import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.EnduranceQuestRegistry.MobTier;
import com.devmod.endurance.KitManager;
import com.devmod.endurance.KitPreset;
import com.devmod.endurance.KitSyncConfirmPayload;
import com.devmod.endurance.KitSyncPayload;
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
    private static final long KIT_SYNC_TIMEOUT_MS = 5_000L;
    private static final int KIT_SYNC_MAX_ATTEMPTS = 3;

    // Animation
    private float animationTick = 0f;
    private float glowPulse = 0f;
    private float titleGlow = 0f;
    private static final int MAX_VISIBLE_MEMBERS = 6;
    private final float[] memberAnimations = new float[MAX_VISIBLE_MEMBERS];

    // Hover animations (lerp-based smooth transitions)
    private static final float HOVER_LERP_IN = 0.25f;   // Speed entering hover
    private static final float HOVER_LERP_OUT = 0.15f;  // Speed exiting hover (slower = smoother)
    private final float[] mobHoverAnimations = new float[MAX_VISIBLE_MOBS];
    private final float[] tierHoverAnimations = new float[MobTier.values().length];
    private final float[] memberHoverAnimations = new float[MAX_VISIBLE_MEMBERS];

    // Scrollbar drag state
    private boolean isDraggingMobScrollbar = false;
    private int scrollbarDragStartY = 0;
    private int scrollbarDragStartOffset = 0;

    // Party State
    private List<PartySyncPayload.PartyMemberInfo> members = new ArrayList<>();
    @Nullable
    private UUID leaderId = null;
    private QuestType questType = QuestType.PVE_COOP;
    private PartyData.PartyState partyState = PartyData.PartyState.FORMING;
    private boolean isInParty = false;
    private boolean isLeader = false;
    private boolean isReady = false;
    private boolean isSpectator = false;
    private int memberListScrollOffset = 0;

    // Kit Selection (local player)
    private KitPreset selectedKit = KitPreset.STARTER;
    @Nullable private CustomKit selectedCustomKit = null;
    private boolean usingCustomKit = false;
    @Nullable private String customKitName = null;
    private List<CustomKit> savedCustomKits = new ArrayList<>();
    private String localKitId = "STARTER";
    private boolean kitSyncInFlight = false;
    private long kitSyncStartTime = 0L;
    private int kitSyncAttempts = 0;
    @Nullable private KitSyncPayload pendingKitSyncPayload = null;
    @Nullable private String pendingKitId = null;

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
    @Nullable private EditorButton kitPrevButton;
    @Nullable private EditorButton kitNextButton;
    @Nullable private EditorButton kitEditButton;

    @Nullable private ButtonArea inviteButtonBounds;
    @Nullable private ButtonArea readyButtonBounds;
    @Nullable private ButtonArea startButtonBounds;
    @Nullable private ButtonArea leaveButtonBounds;
    @Nullable private ButtonArea disbandButtonBounds;
    @Nullable private ButtonArea createPartyButtonBounds;
    @Nullable private ButtonArea kitPrevButtonBounds;
    @Nullable private ButtonArea kitNextButtonBounds;
    @Nullable private ButtonArea kitEditButtonBounds;

    private int hoveredMemberIndex = -1;
    private int hoveredMobIndex = -1;
    private int hoveredQuestTab = -1;
    private int hoveredTierIndex = -1;

    private int panelX, panelY;
    private int scaledPanelWidth, scaledPanelHeight;
    private int originalBlurValue = 0;

    // Renderer delegate
    @Nullable private PartyScreenRenderer renderer;

    public static record PartyLayout(int membersX, int membersY, int membersW, int membersH,
                               int mobX, int mobY, int mobW, int mobH,
                               int previewX, int previewY, int previewW, int previewH,
                               int contentTop, int contentBottom, int gap, int padding) {}

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

    private int s(int value) {
        float scale = Math.max(1f, UIScaleManager.getEffectiveScale());
        return UIScaleManager.snap((int) (value * scale));
    }

    private int line() {
        return getFont().lineHeight;
    }

    private int lineGap() {
        return s(2);
    }

    private int panelHeaderHeight() {
        return Math.max(s(22), line() + s(6));
    }

    private int memberRowHeight() {
        return Math.max(s(32), line() * 2 + s(8));
    }

    private int mobRowHeight() {
        return Math.max(s(24), line() * 2 + s(6));
    }

    private int inputHeight() {
        return Math.max(s(20), line() + s(6));
    }

    private int filterButtonHeight() {
        return Math.max(s(12), line() + s(2));
    }

    private int tierButtonHeight() {
        return Math.max(s(14), line() + s(2));
    }

    private int inviteBlockHeight() {
        return line() + lineGap() + inputHeight();
    }

    private int buttonRowHeight() {
        return Math.max(s(24), line() + s(10));
    }

    private int buttonRowY() {
        return panelY + scaledPanelHeight - s(18) - buttonRowHeight();
    }

    private int waveBarHeight() {
        return Math.max(s(64), line() * 2 + s(26));
    }

    private int waveBarY() {
        return buttonRowY() - s(12) - waveBarHeight();
    }

    private int memberFooterReserve() {
        return line() + s(8);
    }

    private int memberListTop(PartyLayout layout) {
        return layout.membersY + panelHeaderHeight() + s(6);
    }

    private int mobListTop(PartyLayout layout) {
        // Compact layout: search + namespace filters on same row, tier buttons below
        int searchY = layout.mobY + panelHeaderHeight() + s(6); // Match initMobSearchBox
        int searchH = inputHeight();
        // Tier buttons directly below search
        int tierY = searchY + searchH + s(2);
        return tierY + tierButtonHeight() + s(2);
    }

    private int mobFooterReserve() {
        return line() + s(4);  // Compact: just enough for count label
    }

    private int headerLineCount() {
        int lines = 1; // title
        if (isInParty) {
            lines += 1; // subtitle
            if (partyState != PartyData.PartyState.IN_QUEST) {
                String startReason = getStartBlockReason();
                if (startReason != null && !startReason.isBlank()) {
                    lines += 1;
                }
                lines += 1; // next action hint
            }
        }
        return lines;
    }

    private int tabBarHeight() {
        return Math.max(s(28), line() * 2 + s(8));
    }

    private int headerBlockHeight() {
        int topPad = s(10);
        int bottomPad = s(8);
        int lines = headerLineCount();
        return topPad + lines * line() + Math.max(0, lines - 1) * lineGap() + bottomPad;
    }

    // ========== PUBLIC ACCESSORS FOR RENDERER ==========

    /** Returns font for renderer use. */
    @Nonnull
    public net.minecraft.client.gui.Font getScreenFont() {
        return getFont();
    }

    public int getPanelX() { return panelX; }
    public int getPanelY() { return panelY; }
    public int getPanelWidth() { return scaledPanelWidth; }
    public int getPanelHeight() { return scaledPanelHeight; }
    public int getKitRowY() { return waveBarY() - s(10) - line(); }
    public int getWaveBarY() { return waveBarY(); }
    @Nullable public EditBox getInviteBox() { return inviteBox; }
    @Nullable public EditBox getMobSearchBox() { return mobSearchBox; }

    public float getGlowPulse() { return glowPulse; }
    public float getTitleGlow() { return titleGlow; }

    public boolean isInParty() { return isInParty; }
    public PartyData.PartyState getPartyState() { return partyState; }
    public QuestType getQuestType() { return questType; }

    public List<PartySyncPayload.PartyMemberInfo> getMembers() { return members; }
    @Nullable public UUID getLeaderId() { return leaderId; }
    public boolean isLeader() { return isLeader; }
    public boolean isLocalSpectator() { return isSpectator; }
    public boolean isLocalReady() { return isReady; }
    public boolean isKitSyncInFlight() { return kitSyncInFlight; }
    public String getLocalKitLabel() {
        if (selectedCustomKit != null) {
            return selectedCustomKit.getName();
        }
        if (usingCustomKit) {
            if (customKitName != null && !customKitName.isBlank()) {
                return customKitName;
            }
            return "Custom Kit";
        }
        return selectedKit != null ? selectedKit.getDisplayName() : "Starter Kit";
    }
    @Nullable
    public String getStartBlockReason() {
        if (!isInParty) return null;
        if (partyState == PartyData.PartyState.IN_QUEST) return "Run active";
        if (!isLeader) return "Leader only";
        int memberCount = members.size();
        if (questType.allowsSoloPlay() && memberCount == 1) {
            long notReady = members.stream().filter(m -> !m.isReady()).count();
            return notReady > 0 ? "Press READY" : null;
        }
        if (memberCount < questType.getMinPlayers()) {
            return "Need " + questType.getMinPlayers() + " players";
        }
        long notReady = members.stream().filter(m -> !m.isReady()).count();
        if (notReady > 0) {
            return "Waiting " + notReady + " ready";
        }
        return null;
    }
    public float[] getMemberAnimations() { return memberAnimations; }
    public float[] getMobHoverAnimations() { return mobHoverAnimations; }
    public float[] getTierHoverAnimations() { return tierHoverAnimations; }
    public float[] getMemberHoverAnimations() { return memberHoverAnimations; }
    public int getHoveredTierIndex() { return hoveredTierIndex; }
    public void setHoveredTierIndex(int index) { this.hoveredTierIndex = index; }
    public boolean isDraggingMobScrollbar() { return isDraggingMobScrollbar; }
    public int getMemberListScrollOffset() { return memberListScrollOffset; }
    public int getMaxVisibleMembers() {
        PartyLayout layout = getLayout();
        int available = layout.membersH - (memberListTop(layout) - layout.membersY) - memberFooterReserve();
        int rows = available > 0 ? (available / memberRowHeight()) : 1;
        return Mth.clamp(rows, 1, MAX_VISIBLE_MEMBERS);
    }

    public List<EnduranceQuestRegistry.MobQuestConfig> getFilteredMobs() { return filteredMobs; }
    public int getSelectedMobIndex() { return selectedMobIndex; }
    public int getMobListScrollOffset() { return mobListScrollOffset; }
    public int getMaxVisibleMobs() {
        PartyLayout layout = getLayout();
        int listTop = mobListTop(layout);
        int available = layout.mobH - (listTop - layout.mobY) - mobFooterReserve();
        int rows = available > 0 ? (available / mobRowHeight()) : 1;
        return Mth.clamp(rows, 1, MAX_VISIBLE_MOBS);
    }
    public int getMobListTop() {
        return mobListTop(getLayout());
    }
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

    PartyLayout getLayout() {
        int padding = s(15);
        int gap = s(10);
        int contentTop = panelY + headerBlockHeight() + tabBarHeight() + s(6);
        int contentBottom = getKitRowY() - s(12) - inviteBlockHeight();
        int contentH = Math.max(0, contentBottom - contentTop);
        int innerW = scaledPanelWidth - padding * 2;

        int minMemberW = s(170);
        int minPreviewW = s(170);
        int minMidW = s(160);
        int maxMemberW = s(260);
        int maxPreviewW = s(260);

        int memberW = clampInt((int) (innerW * 0.28f), minMemberW, maxMemberW);
        int previewW = clampInt((int) (innerW * 0.30f), minPreviewW, maxPreviewW);
        int midW = innerW - memberW - previewW - gap * 2;
        if (midW < minMidW) {
            int deficit = minMidW - midW;
            int reduceMember = Math.min((deficit + 1) / 2, memberW - minMemberW);
            memberW -= reduceMember;
            int reducePreview = Math.min(deficit - reduceMember, previewW - minPreviewW);
            previewW -= reducePreview;
            midW = innerW - memberW - previewW - gap * 2;
        }
        midW = Math.max(0, midW);

        int membersX = panelX + padding;
        int mobX = membersX + memberW + gap;
        int previewX = mobX + midW + gap;
        return new PartyLayout(membersX, contentTop, memberW, contentH,
            mobX, contentTop, midW, contentH,
            previewX, contentTop, previewW, contentH,
            contentTop, contentBottom, gap, padding);
    }

    @Nullable
    public EnduranceQuestRegistry.MobQuestConfig getSelectedMobConfig() {
        ResourceLocation mobId = selectedMobId;
        if (mobId != null) {
            for (EnduranceQuestRegistry.MobQuestConfig config : availableMobs) {
                if (config.getMobId().equals(mobId)) {
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
        UIScaleManager.update();

        scaledPanelWidth = Math.max(PANEL_WIDTH, s(PANEL_WIDTH));
        scaledPanelHeight = Math.max(PANEL_HEIGHT, s(PANEL_HEIGHT));
        int actualWidth = Math.min(scaledPanelWidth, width - s(40));
        int actualHeight = Math.min(scaledPanelHeight, height - s(40));
        scaledPanelWidth = actualWidth;
        scaledPanelHeight = actualHeight;
        panelX = (width - actualWidth) / 2;
        panelY = (height - actualHeight) / 2;

        // Initialize renderer
        this.renderer = new PartyScreenRenderer(this);

        loadAvailableMobs();
        savedCustomKits = KitManager.INSTANCE.getAllCustomKits();
        refreshFromCache();

        // === Create UI Components ===
        initInviteSection();
        initMobSearchBox();
        initActionButtons();
        initKitButtons();
        applyLayout(getLayout());

        updateButtonStates();
        updatePreviewEntity();
    }

    private void initInviteSection() {
        PartyLayout layout = getLayout();
        int inviteSectionY = layout.membersY + layout.membersH + s(8);
        int inviteBoxX = layout.membersX + s(5);
        int inviteBoxY = inviteSectionY + line() + lineGap();
        int inviteBoxH = inputHeight();
        int inviteBtnW = s(50);
        int inviteBoxW = Math.max(s(90), layout.membersW - inviteBtnW - s(12));
        var box = new EditBox(getFont(), inviteBoxX, inviteBoxY, inviteBoxW, inviteBoxH,
                Objects.requireNonNull(Component.literal("Player name...")));
        box.setHint(Objects.requireNonNull(Component.literal("Enter name...")));
        box.setMaxLength(16);
        box.setBordered(false);
        box.setTextColor(DesignTokens.Text.PRIMARY);
        box.setTextColorUneditable(DesignTokens.Text.MUTED);
        addRenderableWidget(box);
        inviteBox = box;

        // Invite button - positioned right after the input background
        int inviteButtonX = inviteBoxX + inviteBoxW + s(3);
        inviteButton = EditorButton.builder("invite", "INVITE")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onInviteClicked)
            .build();
        inviteButtonBounds = new ButtonArea(inviteButtonX, inviteBoxY, inviteBtnW, inviteBoxH);
    }

    private void initMobSearchBox() {
        PartyLayout layout = getLayout();
        int mobSearchX = layout.mobX + s(5);
        int mobSearchY = layout.mobY + panelHeaderHeight() + s(6);
        // Leave space on right for namespace filter buttons (All, MC)
        int mobSearchW = Math.max(s(60), layout.mobW - s(70));
        int mobSearchH = inputHeight();
        var searchBox = new EditBox(getFont(), mobSearchX, mobSearchY, mobSearchW, mobSearchH,
                Objects.requireNonNull(Component.literal("Search...")));
        searchBox.setHint(Objects.requireNonNull(Component.literal("Search mobs...")));
        searchBox.setMaxLength(32);
        searchBox.setBordered(false);
        searchBox.setTextColor(DesignTokens.Text.PRIMARY);
        searchBox.setTextColorUneditable(DesignTokens.Text.MUTED);
        searchBox.setResponder(this::onMobSearchChanged);
        addRenderableWidget(searchBox);
        mobSearchBox = searchBox;
    }

    private void initActionButtons() {
        // Bottom action buttons
        int buttonY = buttonRowY();
        int centerX = panelX + scaledPanelWidth / 2;
        int buttonH = buttonRowHeight();
        int readyW = s(80);
        int leaveW = s(70);
        int startW = s(120);
        int disbandW = s(70);

        readyButton = EditorButton.builder("ready", getReadyButtonText().getString())
            .style(EditorButton.Style.PRIMARY)
            .toggleable(true)
            .toggled(isReady)
            .size(EditorButton.Size.LARGE)
            .onClick(this::onReadyClicked)
            .build();
        readyButtonBounds = new ButtonArea(centerX - s(180), buttonY, readyW, buttonH);

        leaveButton = EditorButton.builder("leave", "LEAVE")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.LARGE)
            .onClick(this::onLeaveClicked)
            .build();
        leaveButtonBounds = new ButtonArea(centerX - s(90), buttonY, leaveW, buttonH);

        startButton = EditorButton.builder("start-quest", "START QUEST")
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.LARGE)
            .onClick(this::onStartClicked)
            .build();
        startButtonBounds = new ButtonArea(centerX - s(10), buttonY, startW, buttonH);

        disbandButton = EditorButton.builder("disband", "DISBAND")
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.LARGE)
            .onClick(this::onDisbandClicked)
            .build();
        disbandButtonBounds = new ButtonArea(centerX + s(120), buttonY, disbandW, buttonH);

        // Create Party button (when not in party)
        createPartyButton = EditorButton.builder("create-party", "CREATE PARTY")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.LARGE)
            .onClick(this::onCreatePartyClicked)
            .build();
        int createW = s(160);
        int createH = Math.max(s(30), line() + s(12));
        int createX = centerX - createW / 2;
        int createY = panelY + scaledPanelHeight / 2 + s(20);
        createPartyButtonBounds = new ButtonArea(createX, createY, createW, createH);
    }

    private void initKitButtons() {
        int kitY = getKitRowY();
        int rightX = panelX + scaledPanelWidth - s(15);
        int btnH = Math.max(s(18), line() + s(4));
        int navW = s(22);
        int editW = s(40);
        int gap = s(4);

        kitEditButton = EditorButton.builder("kit-edit", "EDIT")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.SMALL)
            .onClick(this::openKitEditor)
            .build();
        kitNextButton = EditorButton.builder("kit-next", ">")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(this::nextKit)
            .build();
        kitPrevButton = EditorButton.builder("kit-prev", "<")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(this::prevKit)
            .build();

        int editX = rightX - editW;
        int nextX = editX - gap - navW;
        int prevX = nextX - gap - navW;

        kitEditButtonBounds = new ButtonArea(editX, kitY - s(2), editW, btnH);
        kitNextButtonBounds = new ButtonArea(nextX, kitY - s(2), navW, btnH);
        kitPrevButtonBounds = new ButtonArea(prevX, kitY - s(2), navW, btnH);
    }

    private void applyLayout(PartyLayout layout) {
        if (layout == null) {
            return;
        }
        int inviteSectionY = layout.membersY + layout.membersH + s(8);
        int inviteBoxX = layout.membersX + s(5);
        int inviteBoxY = inviteSectionY + line() + lineGap();
        int inviteBoxH = inputHeight();
        int inviteBtnW = s(50);
        int inviteBoxW = Math.max(s(90), layout.membersW - inviteBtnW - s(12));
        EditBox invite = inviteBox;
        if (invite != null) {
            invite.setX(inviteBoxX);
            invite.setY(inviteBoxY);
            invite.setWidth(inviteBoxW);
            invite.setHeight(inviteBoxH);
        }
        inviteButtonBounds = new ButtonArea(inviteBoxX + inviteBoxW + s(3), inviteBoxY, inviteBtnW, inviteBoxH);

        int mobSearchX = layout.mobX + s(5);
        int mobSearchY = layout.mobY + panelHeaderHeight() + s(6);
        // Leave space on right for namespace filter buttons (All, MC)
        int mobSearchW = Math.max(s(60), layout.mobW - s(70));
        int mobSearchH = inputHeight();
        EditBox search = mobSearchBox;
        if (search != null) {
            search.setX(mobSearchX);
            search.setY(mobSearchY);
            search.setWidth(mobSearchW);
            search.setHeight(mobSearchH);
        }

        int kitY = getKitRowY();
        int rightX = panelX + scaledPanelWidth - s(15);
        int btnH = Math.max(s(18), line() + s(4));
        int navW = s(22);
        int editW = s(40);
        int gap = s(4);
        int editX = rightX - editW;
        int nextX = editX - gap - navW;
        int prevX = nextX - gap - navW;
        kitEditButtonBounds = new ButtonArea(editX, kitY - s(2), editW, btnH);
        kitNextButtonBounds = new ButtonArea(nextX, kitY - s(2), navW, btnH);
        kitPrevButtonBounds = new ButtonArea(prevX, kitY - s(2), navW, btnH);
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void loadAvailableMobs() {
        availableMobs = new ArrayList<>(EnduranceQuestRegistry.INSTANCE.getAllMobConfigs());
        availableNamespaces = availableMobs.stream()
                .map(m -> m.getNamespace())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        availableMobs.sort((a, b) -> {
            int tierComp = a.getTier().compareTo(b.getTier());
            if (tierComp != 0) return tierComp;
            return a.getDisplayName().compareTo(b.getDisplayName());
        });

        if (selectedMobId == null) {
            for (EnduranceQuestRegistry.MobQuestConfig config : availableMobs) {
                if ("zombie".equals(config.getMobId().getPath())) {
                    selectedMobId = config.getMobId();
                    break;
                }
            }
        }

        filterMobs();
    }

    private void filterMobs() {
        filteredMobs = availableMobs.stream()
                .filter(m -> {
                    if (selectedNamespace != null && !m.getNamespace().equals(selectedNamespace)) return false;
                    if (selectedTierFilter != null && m.getTier() != selectedTierFilter) return false;
                    if (!mobSearchText.isEmpty()) {
                        String search = mobSearchText.toLowerCase(Locale.ROOT);
                        return m.getDisplayName().toLowerCase(Locale.ROOT).contains(search) ||
                                m.getMobId().toString().toLowerCase(Locale.ROOT).contains(search);
                    }
                    return true;
                })
                .collect(Collectors.toList());

        int newIndex = findMobIndex(selectedMobId, filteredMobs);
        if (newIndex < 0 && selectedMobId == null && !filteredMobs.isEmpty()) {
            selectedMobIndex = 0;
            selectedMobId = filteredMobs.get(0).getMobId();
        } else {
            selectedMobIndex = newIndex;
        }
        mobListScrollOffset = 0;
        updatePreviewEntity();
    }

    private void clampMemberScroll() {
        int maxScroll = Math.max(0, members.size() - getMaxVisibleMembers());
        memberListScrollOffset = Mth.clamp(memberListScrollOffset, 0, maxScroll);
    }

    private void clampMobScroll() {
        int maxScroll = Math.max(0, filteredMobs.size() - getMaxVisibleMobs());
        mobListScrollOffset = Mth.clamp(mobListScrollOffset, 0, maxScroll);
    }

    private void onMobSearchChanged(String text) {
        mobSearchText = text;
        filterMobs();
    }

    private void refreshFromCache() {
        isInParty = ClientPartyCache.isInParty();

        if (isInParty) {
            members = new ArrayList<>(ClientPartyCache.getMembers());
            UUID localId = null;
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                localId = client.player.getUUID();
            }
            UUID finalLocalId = localId;
            members.sort((a, b) -> {
                int leaderCmp = Boolean.compare(b.isLeader(), a.isLeader());
                if (leaderCmp != 0) return leaderCmp;
                if (finalLocalId != null) {
                    boolean aLocal = a.playerId().equals(finalLocalId);
                    boolean bLocal = b.playerId().equals(finalLocalId);
                    if (aLocal != bLocal) return aLocal ? -1 : 1;
                }
                int statusCmp = Integer.compare(getMemberStatusRank(a), getMemberStatusRank(b));
                if (statusCmp != 0) return statusCmp;
                int readyCmp = Boolean.compare(b.isReady(), a.isReady());
                if (readyCmp != 0) return readyCmp;
                return a.playerName().compareToIgnoreCase(b.playerName());
            });
            clampMemberScroll();
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

            var localPlayer = client.player;
            if (localPlayer != null) {
                isLeader = localPlayer.getUUID().equals(leaderId);
                PartySyncPayload.PartyMemberInfo localInfo = members.stream()
                        .filter(m -> m.playerId().equals(localPlayer.getUUID()))
                        .findFirst()
                        .orElse(null);
                isReady = localInfo != null && localInfo.isReady();
                isSpectator = localInfo != null && localInfo.isSpectator();
                if (localInfo != null) {
                    syncLocalKitSelection(localInfo, false);
                }
            }
        } else {
            members.clear();
            memberListScrollOffset = 0;
            leaderId = null;
            isLeader = false;
            isReady = false;
            isSpectator = false;
            localKitId = "STARTER";
            selectedKit = KitPreset.STARTER;
            selectedCustomKit = null;
            usingCustomKit = false;
            customKitName = null;
            kitSyncInFlight = false;
            kitSyncAttempts = 0;
            kitSyncStartTime = 0L;
            pendingKitSyncPayload = null;
            pendingKitId = null;
        }
    }

    private int getMemberStatusRank(PartySyncPayload.PartyMemberInfo member) {
        if (!member.isOnline()) return 2;
        if (member.isSpectator()) return 1;
        return 0;
    }

    private void syncLocalKitSelection(PartySyncPayload.PartyMemberInfo localInfo, boolean force) {
        if (kitSyncInFlight || localInfo == null) {
            return;
        }
        String kitId = localInfo.kitId();
        if (kitId == null || kitId.isBlank()) {
            kitId = "STARTER";
        }
        if (!force && kitId.equals(localKitId)) {
            // Defensive null check for @Nullable customKitName
            String kitName = customKitName;
            if (usingCustomKit && (kitName == null || kitName.isBlank())) {
                String label = localInfo.kitLabel();
                if (label != null && !label.isBlank()) {
                    customKitName = label;
                }
            }
            return;
        }
        localKitId = kitId;
        if ("TEMPORARY".equals(kitId)) {
            selectedCustomKit = null;
            usingCustomKit = true;
            customKitName = localInfo.kitLabel();
            return;
        }
        if (kitId.length() == 8) {
            savedCustomKits = KitManager.INSTANCE.getAllCustomKits();
            CustomKit resolved = null;
            for (CustomKit kit : savedCustomKits) {
                if (kit.getId().equals(kitId)) {
                    resolved = kit;
                    break;
                }
            }
            selectedCustomKit = resolved;
            usingCustomKit = true;
            customKitName = resolved != null ? resolved.getName() : localInfo.kitLabel();
            return;
        }
        usingCustomKit = false;
        selectedCustomKit = null;
        customKitName = null;
        KitPreset preset = KitManager.INSTANCE.getKitById(kitId);
        selectedKit = preset != null ? preset : KitPreset.STARTER;
    }

    private void forceRefreshKitSelection() {
        PartySyncPayload party = ClientPartyCache.getParty();
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer localPlayer = mc.player;
        if (party == null || localPlayer == null) {
            return;
        }
        PartySyncPayload.PartyMemberInfo localInfo = party.getMember(localPlayer.getUUID());
        if (localInfo != null) {
            syncLocalKitSelection(localInfo, true);
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
        int maxVisibleMembers = getMaxVisibleMembers();
        int visibleMembers = Math.min(memberAnimations.length,
            Math.min(maxVisibleMembers, Math.max(0, members.size() - memberListScrollOffset)));
        for (int i = 0; i < memberAnimations.length; i++) {
            if (i < visibleMembers) {
                memberAnimations[i] = Math.min(1f, memberAnimations[i] + 0.1f);
            } else {
                memberAnimations[i] = 0f;
            }
        }

        // Hover animations - smooth lerp transitions per element
        // Mob list hover
        for (int i = 0; i < mobHoverAnimations.length; i++) {
            float target = (i == hoveredMobIndex - mobListScrollOffset && hoveredMobIndex >= 0) ? 1f : 0f;
            float lerpSpeed = target > mobHoverAnimations[i] ? HOVER_LERP_IN : HOVER_LERP_OUT;
            mobHoverAnimations[i] = Mth.lerp(lerpSpeed, mobHoverAnimations[i], target);
        }

        // Tier button hover
        for (int i = 0; i < tierHoverAnimations.length; i++) {
            float target = (i == hoveredTierIndex) ? 1f : 0f;
            float lerpSpeed = target > tierHoverAnimations[i] ? HOVER_LERP_IN : HOVER_LERP_OUT;
            tierHoverAnimations[i] = Mth.lerp(lerpSpeed, tierHoverAnimations[i], target);
        }

        // Member hover
        for (int i = 0; i < memberHoverAnimations.length; i++) {
            float target = (i == hoveredMemberIndex - memberListScrollOffset && hoveredMemberIndex >= 0) ? 1f : 0f;
            float lerpSpeed = target > memberHoverAnimations[i] ? HOVER_LERP_IN : HOVER_LERP_OUT;
            memberHoverAnimations[i] = Mth.lerp(lerpSpeed, memberHoverAnimations[i], target);
        }

        // Sync
        long now = System.currentTimeMillis();
        if (now - lastSyncTime > SYNC_INTERVAL_MS) {
            lastSyncTime = now;
            refreshFromCache();
            updateButtonStates();
            clampMemberScroll();
            clampMobScroll();
        }

        if (kitSyncInFlight && now - kitSyncStartTime > KIT_SYNC_TIMEOUT_MS) {
            if (kitSyncAttempts < KIT_SYNC_MAX_ATTEMPTS && pendingKitSyncPayload != null) {
                kitSyncAttempts++;
                kitSyncStartTime = now;
                sendKitSyncPayload(pendingKitSyncPayload);
            } else {
                kitSyncInFlight = false;
                pendingKitSyncPayload = null;
                pendingKitId = null;
                notifyKitSyncFailure("Kit sync timeout");
                forceRefreshKitSelection();
                updateButtonStates();
            }
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
        if (readyButton != null) {
            readyButton.enabled(!kitSyncInFlight);
        }
        if (leaveButton != null) {
            leaveButton.enabled(isInParty);
        }
        if (createPartyButton != null) {
            createPartyButton.enabled(!isInParty);
        }
        boolean kitEditable = canEditKit();
        if (kitPrevButton != null) {
            kitPrevButton.enabled(kitEditable);
        }
        if (kitNextButton != null) {
            kitNextButton.enabled(kitEditable);
        }
        if (kitEditButton != null) {
            kitEditButton.enabled(kitEditable);
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

    private boolean canEditKit() {
        return isInParty
            && partyState != PartyData.PartyState.IN_QUEST
            && !kitSyncInFlight;
    }

    private KitPreset[] getSelectablePresets() {
        return java.util.Arrays.stream(KitPreset.values())
            .filter(p -> p != KitPreset.CUSTOM)
            .toArray(KitPreset[]::new);
    }

    private void prevKit() {
        if (!canEditKit()) {
            UiSounds.error();
            return;
        }
        KitPreset[] presets = getSelectablePresets();

        if (selectedCustomKit != null) {
            int idx = savedCustomKits.indexOf(selectedCustomKit);
            if (idx > 0) {
                selectedCustomKit = savedCustomKits.get(idx - 1);
            } else {
                selectedCustomKit = null;
                selectedKit = presets[presets.length - 1];
            }
        } else {
            int idx = java.util.Arrays.asList(presets).indexOf(selectedKit);
            if (idx > 0) {
                selectedKit = presets[idx - 1];
            } else if (!savedCustomKits.isEmpty()) {
                selectedCustomKit = savedCustomKits.get(savedCustomKits.size() - 1);
            } else {
                selectedKit = presets[presets.length - 1];
            }
        }

        usingCustomKit = selectedCustomKit != null;
        customKitName = selectedCustomKit != null ? selectedCustomKit.getName() : null;
        submitKitSelection();
    }

    private void nextKit() {
        if (!canEditKit()) {
            UiSounds.error();
            return;
        }
        KitPreset[] presets = getSelectablePresets();

        if (selectedCustomKit != null) {
            int idx = savedCustomKits.indexOf(selectedCustomKit);
            if (idx < savedCustomKits.size() - 1) {
                selectedCustomKit = savedCustomKits.get(idx + 1);
            } else {
                selectedCustomKit = null;
                selectedKit = presets[0];
            }
        } else {
            int idx = java.util.Arrays.asList(presets).indexOf(selectedKit);
            if (idx < presets.length - 1) {
                selectedKit = presets[idx + 1];
            } else if (!savedCustomKits.isEmpty()) {
                selectedCustomKit = savedCustomKits.get(0);
            } else {
                selectedKit = presets[0];
            }
        }

        usingCustomKit = selectedCustomKit != null;
        customKitName = selectedCustomKit != null ? selectedCustomKit.getName() : null;
        submitKitSelection();
    }

    private void openKitEditor() {
        if (!canEditKit()) {
            UiSounds.error();
            return;
        }
        CustomKit kitToEdit = selectedCustomKit;
        com.devmod.client.ui.ScreenSafety.openSafe(
            "kit_selection",
            this,
            () -> new KitSelectionScreen(this, items -> {
                savedCustomKits = KitManager.INSTANCE.getAllCustomKits();
                if (items != null && !items.isEmpty()) {
                    usingCustomKit = true;
                    customKitName = KitManager.INSTANCE.getTemporaryKitName();
                    selectedCustomKit = null;
                } else {
                    usingCustomKit = false;
                    customKitName = null;
                    selectedCustomKit = null;
                }
                submitKitSelection();
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
                    submitKitSelection();
                }
            }, kitToEdit)
        );
    }

    private void submitKitSelection() {
        if (!isInParty || partyState == PartyData.PartyState.IN_QUEST) {
            return;
        }
        if (kitSyncInFlight) {
            return;
        }
        String kitId = resolveSelectedKitId();
        if (kitId == null || kitId.isBlank()) {
            kitId = "STARTER";
        }
        if (kitId.equals(localKitId)) {
            return;
        }
        if (requiresKitSync(kitId)) {
            KitSyncPayload payload = buildKitSyncPayload(kitId);
            if (payload == null) {
                UiSounds.error();
                return;
            }
            pendingKitId = kitId;
            pendingKitSyncPayload = payload;
            kitSyncInFlight = true;
            kitSyncAttempts = 1;
            kitSyncStartTime = System.currentTimeMillis();
            sendKitSyncPayload(payload);
            updateButtonStates();
            return;
        }
        sendKitSelectionToServer(kitId);
    }

    private String resolveSelectedKitId() {
        if (selectedCustomKit != null) {
            return selectedCustomKit.getId();
        }
        if (usingCustomKit && KitManager.INSTANCE.hasTemporaryKit()) {
            return "TEMPORARY";
        }
        if (selectedKit != null) {
            return selectedKit.name();
        }
        return "STARTER";
    }

    private boolean requiresKitSync(String kitId) {
        return "TEMPORARY".equals(kitId) || kitId.length() == 8;
    }

    @Nullable
    private KitSyncPayload buildKitSyncPayload(String kitId) {
        Minecraft mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) {
            return null;
        }
        var registryAccess = level.registryAccess();

        if ("TEMPORARY".equals(kitId)) {
            List<net.minecraft.world.item.ItemStack> items = KitManager.INSTANCE.getTemporaryKitItems();
            if (items.isEmpty()) {
                notifyKitSyncFailure("Temporary kit is empty");
                return null;
            }
            if (items.size() > KitSyncPayload.MAX_ITEMS) {
                notifyKitSyncFailure("Kit too large (max " + KitSyncPayload.MAX_ITEMS + " items)");
                return null;
            }
            String kitName = KitManager.INSTANCE.getTemporaryKitName();
            return KitSyncPayload.fromTemporary(
                kitName != null ? kitName : "Temporary Kit",
                items,
                registryAccess
            );
        }

        if (kitId.length() == 8) {
            CustomKit kit = selectedCustomKit;
            if (kit == null) {
                kit = KitManager.INSTANCE.getCustomKit(kitId).orElse(null);
            }
            if (kit == null) {
                notifyKitSyncFailure("Custom kit not found");
                return null;
            }
            List<net.minecraft.world.item.ItemStack> stacks = kit.toItemStacks(registryAccess);
            if (stacks.size() > KitSyncPayload.MAX_ITEMS) {
                notifyKitSyncFailure("Kit too large (max " + KitSyncPayload.MAX_ITEMS + " items)");
                return null;
            }
            return KitSyncPayload.fromCustomKit(kit, registryAccess);
        }
        return null;
    }

    private void sendKitSyncPayload(KitSyncPayload payload) {
        PacketDistributor.sendToServer(Objects.requireNonNull(payload));
    }

    private void sendKitSelectionToServer(String kitId) {
        localKitId = kitId;
        PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.setKit(kitId)));
        if (isReady) {
            isReady = false;
            ClientPartyCache.setLocalPlayerReady(false);
        }
        updateButtonStates();
    }

    public void onKitSyncConfirm(KitSyncConfirmPayload payload) {
        if (!kitSyncInFlight) {
            return;
        }
        String kitId = payload != null ? payload.kitId() : "";
        // Use local variable to satisfy NullAway null-check flow analysis
        String pendingId = pendingKitId;
        if (pendingId != null && kitId != null && !kitId.isBlank() && !pendingId.equals(kitId)) {
            return;
        }

        kitSyncInFlight = false;
        kitSyncAttempts = 0;
        kitSyncStartTime = 0L;
        pendingKitSyncPayload = null;
        String pending = pendingKitId;
        pendingKitId = null;

        if (payload != null && payload.success()) {
            if (pending != null && !pending.isBlank()) {
                sendKitSelectionToServer(pending);
            }
            UiSounds.success();
        } else {
            String message = payload != null ? payload.message() : "Kit sync failed";
            notifyKitSyncFailure(message);
            forceRefreshKitSelection();
            updateButtonStates();
        }
    }

    private void notifyKitSyncFailure(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Objects.requireNonNull(Component.literal("[DevMod] " + message)));
        }
    }

    private boolean canStartQuest() {
        if (!isInParty) return false;
        if (kitSyncInFlight) return false;
        if (questType.allowsSoloPlay() && members.size() == 1) return true;
        if (members.size() < questType.getMinPlayers()) return false;
        return members.stream().allMatch(PartySyncPayload.PartyMemberInfo::isReady);
    }

    @Nonnull
    private Component getReadyButtonText() {
        if (partyState == PartyData.PartyState.IN_QUEST) {
            if (isSpectator) {
                return Objects.requireNonNull(Component.literal("REJOIN"));
            }
            return Objects.requireNonNull(Component.literal(isReady ? "READY" : "CONTINUE"));
        }
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
        var level = mc.level;
        if (level != null) {
            try {
                Entity entity = config.getEntityType().create(level);
                if (entity instanceof LivingEntity living) {
                    previewEntity = living;
                } else {
                    previewEntity = null;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to create preview entity for {}", config.getMobId(), e);
                previewEntity = null;
            }
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        applyLayout(getLayout());
        // Dark cinematic background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Delegate all rendering to renderer
        PartyScreenRenderer safeRenderer = renderer;
        if (safeRenderer == null) {
            return;
        }
        safeRenderer.renderMainPanel(graphics);

        if (isInParty) {
            if (partyState == PartyData.PartyState.IN_QUEST) {
                hoveredQuestTab = -1;
            } else {
                hoveredQuestTab = safeRenderer.renderQuestTypeTabs(graphics, mouseX, mouseY);
            }
            hoveredMemberIndex = safeRenderer.renderMembersPanel(graphics, mouseX, mouseY);
            if (partyState == PartyData.PartyState.IN_QUEST) {
                hoveredMobIndex = -1;
                safeRenderer.renderRunStatusPanel(graphics, mouseX, mouseY);
            } else {
                hoveredMobIndex = safeRenderer.renderMobSelectionPanel(graphics, mouseX, mouseY);
                safeRenderer.renderMobPreviewPanel(graphics, mouseX, mouseY);
            }
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
        if (kitSyncInFlight) {
            UiSounds.error();
            return;
        }
        if (partyState == PartyData.PartyState.IN_QUEST) {
            PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.toggleReady()));
            updateButtonStates();
            UiSounds.toggleOn();
            return;
        }
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
        return filteredMobs.get(selectedMobIndex).getMobId();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Quest type tabs
        if (isInParty && partyState != PartyData.PartyState.IN_QUEST
            && isLeader && hoveredQuestTab >= 0 && hoveredQuestTab < QuestType.values().length) {
            questType = QuestType.values()[hoveredQuestTab];
            ClientPartyCache.setQuestType(questType);
            PacketDistributor.sendToServer(Objects.requireNonNull(PartyActionPayload.setQuestType(questType)));
            UiSounds.click();
            updateButtonStates();
            return true;
        }

        // Namespace filter clicks (now at right side of search row)
        if (isInParty) {
            PartyLayout layout = getLayout();
            int searchY = layout.mobY + panelHeaderHeight() + s(6); // Match actual position
            int searchH = inputHeight();
            int nsBtnH = Math.min(searchH, filterButtonHeight());
            int nsBtnY = searchY + (searchH - nsBtnH) / 2;
            int nsBtnX = layout.mobX + layout.mobW - s(60);

            // "All" button
            int allW = s(22);
            if (mouseX >= nsBtnX && mouseX < nsBtnX + allW &&
                mouseY >= nsBtnY && mouseY < nsBtnY + nsBtnH) {
                selectedNamespace = null;
                filterMobs();
                UiSounds.click();
                return true;
            }
            nsBtnX += allW + s(2);

            // "MC" button
            int mcW = s(22);
            if (mouseX >= nsBtnX && mouseX < nsBtnX + mcW &&
                mouseY >= nsBtnY && mouseY < nsBtnY + nsBtnH) {
                selectedNamespace = "minecraft".equals(selectedNamespace) ? null : "minecraft";
                filterMobs();
                UiSounds.click();
                return true;
            }
        }

        // Tier filter clicks (now directly below search)
        if (isInParty) {
            PartyLayout layout = getLayout();
            int searchY = layout.mobY + panelHeaderHeight() + s(6); // Match actual position
            int searchH = inputHeight();
            int filterY = searchY + searchH + s(2);
            int btnW = s(20);
            int btnH = tierButtonHeight();
            int btnX = layout.mobX + s(5);

            for (MobTier tier : MobTier.values()) {
                if (mouseX >= btnX && mouseX < btnX + btnW &&
                    mouseY >= filterY && mouseY < filterY + btnH) {
                    selectedTierFilter = (tier == selectedTierFilter) ? null : tier;
                    filterMobs();
                    UiSounds.click();
                    return true;
                }
                btnX += btnW + s(2);
            }
        }

        // Mob list clicks
        if (isInParty && partyState != PartyData.PartyState.IN_QUEST
            && isLeader && hoveredMobIndex >= 0 && hoveredMobIndex < filteredMobs.size()) {
            selectedMobIndex = hoveredMobIndex;
            selectedMobId = filteredMobs.get(selectedMobIndex).getMobId();
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
            int sliderX = panelX + s(115);
            int sliderY = getWaveBarY();
            int sliderW = s(150);
            int sliderH = Math.max(s(14), line() + s(4));

            if (mouseX >= sliderX && mouseX < sliderX + sliderW &&
                    mouseY >= sliderY && mouseY < sliderY + sliderH) {
                float progress = (float) (mouseX - sliderX) / sliderW;
                previewWaveNumber = 1 + (int) (progress * (MAX_PREVIEW_WAVE - 1));
                previewWaveNumber = Mth.clamp(previewWaveNumber, 1, MAX_PREVIEW_WAVE);
                return true;
            }
        }

        // Mob list scrollbar drag
        if (isInParty && filteredMobs.size() > getMaxVisibleMobs()) {
            PartyLayout layout = getLayout();
            int scrollbarX = layout.mobX + layout.mobW - s(8);
            int scrollbarW = s(6);
            int listY = getMobListTop();
            int listHeight = getMaxVisibleMobs() * mobRowHeight();

            if (mouseX >= scrollbarX && mouseX < scrollbarX + scrollbarW &&
                    mouseY >= listY && mouseY < listY + listHeight) {
                isDraggingMobScrollbar = true;
                scrollbarDragStartY = (int) mouseY;
                scrollbarDragStartOffset = mobListScrollOffset;
                return true;
            }
        }

        // 3D Preview drag
        PartyLayout layout = getLayout();
        int previewX = layout.previewX + s(5);
        int previewY = layout.previewY + panelHeaderHeight();
        int boxW = Math.max(0, layout.previewW - s(10));
        int boxH = s(120);

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
        if (hoveredMemberIndex >= 0 && hoveredMemberIndex < members.size() && isLeader
            && button == 1 && partyState != PartyData.PartyState.IN_QUEST) {
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
        isDraggingMobScrollbar = false;
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

        // Scrollbar drag
        if (isDraggingMobScrollbar) {
            int listHeight = getMaxVisibleMobs() * mobRowHeight();
            int maxScroll = Math.max(0, filteredMobs.size() - getMaxVisibleMobs());
            if (maxScroll > 0 && listHeight > 0) {
                float thumbRatio = (float) getMaxVisibleMobs() / filteredMobs.size();
                int thumbH = Math.max(s(20), (int) (listHeight * thumbRatio));
                int trackH = listHeight - thumbH;

                if (trackH > 0) {
                    int deltaY = (int) mouseY - scrollbarDragStartY;
                    float scrollRatio = (float) deltaY / trackH;
                    int newOffset = scrollbarDragStartOffset + (int) (scrollRatio * maxScroll);
                    mobListScrollOffset = Mth.clamp(newOffset, 0, maxScroll);
                }
            }
            return true;
        }

        // Wave slider drag
        int sliderX = panelX + s(115);
        int sliderY = getWaveBarY();
        int sliderW = s(150);
        int sliderH = Math.max(s(14), line() + s(4));

        if (mouseX >= sliderX - s(10) && mouseX < sliderX + sliderW + s(10) &&
                mouseY >= sliderY - s(5) && mouseY < sliderY + sliderH + s(6)) {
            float progress = (float) (mouseX - sliderX) / sliderW;
            previewWaveNumber = 1 + (int) (progress * (MAX_PREVIEW_WAVE - 1));
            previewWaveNumber = Mth.clamp(previewWaveNumber, 1, MAX_PREVIEW_WAVE);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        PartyLayout layout = getLayout();
        int memberPanelX = layout.membersX;
        int memberPanelY = layout.membersY;
        int memberPanelW = layout.membersW;
        int memberPanelH = layout.membersH;

        if (isInParty && mouseX >= memberPanelX && mouseX < memberPanelX + memberPanelW &&
                mouseY >= memberPanelY && mouseY < memberPanelY + memberPanelH) {
            int maxScroll = Math.max(0, members.size() - getMaxVisibleMembers());
            memberListScrollOffset = Mth.clamp(memberListScrollOffset - (int) verticalAmount, 0, maxScroll);
            return true;
        }

        int listX = layout.mobX + s(5);
        int listY = getMobListTop();
        int listW = Math.max(0, layout.mobW - s(10));
        int listH = getMaxVisibleMobs() * mobRowHeight();

        if (mouseX >= listX && mouseX < listX + listW &&
                mouseY >= listY && mouseY < listY + listH) {
            int maxScroll = Math.max(0, filteredMobs.size() - getMaxVisibleMobs());
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
            var localKitPrev = kitPrevButton;
            var localKitPrevBounds = kitPrevButtonBounds;
            if (localKitPrev != null && localKitPrevBounds != null) {
                localKitPrev.render(graphics, localKitPrevBounds.x, localKitPrevBounds.y,
                    localKitPrevBounds.width, localKitPrevBounds.height, mouseX, mouseY);
            }
            var localKitNext = kitNextButton;
            var localKitNextBounds = kitNextButtonBounds;
            if (localKitNext != null && localKitNextBounds != null) {
                localKitNext.render(graphics, localKitNextBounds.x, localKitNextBounds.y,
                    localKitNextBounds.width, localKitNextBounds.height, mouseX, mouseY);
            }
            var localKitEdit = kitEditButton;
            var localKitEditBounds = kitEditButtonBounds;
            if (localKitEdit != null && localKitEditBounds != null) {
                localKitEdit.render(graphics, localKitEditBounds.x, localKitEditBounds.y,
                    localKitEditBounds.width, localKitEditBounds.height, mouseX, mouseY);
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
            if (kitPrevButton != null && kitPrevButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (kitNextButton != null && kitNextButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (kitEditButton != null && kitEditButton.mouseClicked(mouseX, mouseY, button)) return true;
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
            if (kitPrevButton != null) handled |= kitPrevButton.mouseReleased(mouseX, mouseY, button);
            if (kitNextButton != null) handled |= kitNextButton.mouseReleased(mouseX, mouseY, button);
            if (kitEditButton != null) handled |= kitEditButton.mouseReleased(mouseX, mouseY, button);
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
            if (list.get(i).getMobId().equals(mobId)) {
                return i;
            }
        }
        return -1;
    }
}
