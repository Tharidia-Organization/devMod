package com.devmod.client.endurance;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.endurance.ui.MobListPanel;
import com.devmod.client.endurance.ui.MobStatsPanel;
import com.devmod.client.notification.ClientNotificationManager;
import com.devmod.client.party.ClientPartyCache;
import com.devmod.client.ui.ConfirmDialog;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.EnduranceMobConfigSyncPayload;
import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.EnduranceQuestRegistry.MobTier;
import com.devmod.endurance.MobPoolConfigSyncPayload;
import com.devmod.endurance.RequestMobPoolConfigPayload;
import com.devmod.endurance.SpawnAffix;
import com.devmod.endurance.config.ConfigScope;
import com.devmod.endurance.config.EnduranceMobConfig;
import com.devmod.endurance.config.EnduranceMobPoolConfig;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;
import com.devmod.util.I18n;
/**
 * Screen for editing the Endurance mob pool configuration.
 *
 * Layout:
 * ┌─────────────────────────────────────────────────────────────┐
 * │  [< Back]     Mob Pool Editor            [Save] [Cancel]    │
 * ├─────────────────┬───────────────────────────────────────────┤
 * │  FILTER:        │  SELECTED MOB: Zombie                     │
 * │  [All▼] [🔍___] │  ┌───────────────────────────────────────┐│
 * │                 │  │ [✓] Enabled                           ││
 * │  ☑ Zombie       │  │                                       ││
 * │  ☑ Skeleton     │  │ Base Health:  [====●====] 20.0       ││
 * │  ☐ Creeper      │  │ Base Damage:  [===●=====]  3.0       ││
 * │  ☑ Spider       │  │ ...                                  ││
 * │  ...            │  └───────────────────────────────────────┘│
 * ├─────────────────┴───────────────────────────────────────────┤
 * │  GLOBAL MULTIPLIERS:                                        │
 * │  Health: [====●====]  Damage: [====●====]  Speed: [====●]  │
 * ├─────────────────────────────────────────────────────────────┤
 * │  [Apply Global] [Propose Changes] [Apply to Session]        │
 * └─────────────────────────────────────────────────────────────┘
 */
@OnlyIn(Dist.CLIENT)
public class MobPoolEditorScreen extends Screen {

    private static final int HEADER_HEIGHT = 36;
    private static final int FOOTER_HEIGHT = 50;
    private static final int GLOBAL_SECTION_HEIGHT = 96;
    private static final int FILTER_BAR_HEIGHT = 42;
    private static final int FILTER_ROW_HEIGHT = 18;
    private static final int FILTER_ROW_PADDING = 2;
    private static final int FILTER_ROW_GAP = 2;
    private static final int AFFIX_COLUMNS = 3;
    private static final int AFFIX_LABEL_WIDTH = 54;
    private static final int AFFIX_VALUE_WIDTH = 34;
    private static final int PADDING = 8;
    private static final int LEFT_PANEL_WIDTH = 200;

    private final Screen parent;

    // UI Panels
    @Nullable
    private MobListPanel mobListPanel;
    @Nullable
    private MobStatsPanel mobStatsPanel;

    // Modified mob configs (tracked by mob ID)
    private final Map<ResourceLocation, EnduranceMobConfig> modifiedConfigs = new HashMap<>();
    // Baseline mob configs received from server (explicit overrides only)
    private final Map<ResourceLocation, EnduranceMobConfig> baseConfigs = new HashMap<>();
    // Configs for mobs not present in the local registry (preserved on save)
    private final Map<ResourceLocation, EnduranceMobConfig> orphanedConfigs = new HashMap<>();

    // Global multipliers
    private float globalHealthMult = 1.0f;
    private float globalDamageMult = 1.0f;
    private float globalSpeedMult = 1.0f;
    private float globalEliteChanceMult = 1.0f;

    // Affix weights
    private final Map<SpawnAffix, Float> affixWeights = new EnumMap<>(SpawnAffix.class);

    // Filter state
    private String namespaceFilter = "all";
    @Nullable
    private MobTier tierFilter = null;
    private String searchQuery = "";
    private boolean searchFocused = false;

    // UI State
    private int activeGlobalSlider = -1;
    private int activeAffixSlider = -1;
    private boolean hasChanges = false;
    private boolean tierDropdownOpen = false;
    @Nullable
    private ConfirmDialog confirmDialog;
    @Nullable
    private MobPoolConfigSyncPayload pendingServerConfig;
    private boolean awaitingSessionConfig = false;
    private boolean awaitingGlobalConfig = false;
    private boolean initialConfigApplied = false;

    // Buttons
    @Nullable
    private EditorButton backButton;
    @Nullable
    private EditorButton applyGlobalButton;
    @Nullable
    private EditorButton proposeButton;
    @Nullable
    private EditorButton applySessionButton;
    @Nullable
    private EditorButton selectAllButton;
    @Nullable
    private EditorButton deselectAllButton;
    @Nullable
    private EditorButton resetButton;

    // Namespace dropdown state
    private boolean namespaceDropdownOpen = false;
    private List<String> availableNamespaces = new ArrayList<>();

    // Preselected mob from EnduranceQuestScreen
    @Nullable
    private final ResourceLocation preselectedMobId;

    public MobPoolEditorScreen(Screen parent) {
        this(parent, null);
    }

    /**
     * Constructor with preselected mob.
     * Opens the editor with the specified mob already selected and scrolled into view.
     *
     * @param parent Parent screen to return to
     * @param preselectedMobId Mob to preselect, or null for default behavior
     */
    public MobPoolEditorScreen(Screen parent, @Nullable ResourceLocation preselectedMobId) {
        super(Component.translatable("devmod.endurance.mob_editor.title"));
        this.parent = parent;
        this.preselectedMobId = preselectedMobId;

        // Initialize affix weights
        for (SpawnAffix affix : SpawnAffix.values()) {
            affixWeights.put(affix, 1.0f);
        }
    }

    @Override
    protected void init() {
        super.init();

        int contentY = HEADER_HEIGHT;
        int contentHeight = height - HEADER_HEIGHT - FOOTER_HEIGHT - GLOBAL_SECTION_HEIGHT;

        // Initialize mob list panel
        var listPanel = new MobListPanel(
            PADDING,
            contentY,
            LEFT_PANEL_WIDTH,
            contentHeight
        );
        listPanel.setTopInset(FILTER_BAR_HEIGHT);
        listPanel.setOnMobSelected(this::onMobSelected);
        listPanel.setOnMobToggled(this::onMobToggled);
        listPanel.setNamespaceFilter(namespaceFilter);
        listPanel.setTierFilter(tierFilter);
        listPanel.setSearchQuery(searchQuery);
        availableNamespaces = listPanel.getAvailableNamespaces();
        mobListPanel = listPanel;

        // Initialize mob stats panel
        int statsX = PADDING + LEFT_PANEL_WIDTH + PADDING;
        int statsWidth = width - statsX - PADDING;
        var statsPanel = new MobStatsPanel(
            statsX,
            contentY,
            statsWidth,
            contentHeight
        );
        statsPanel.setOnConfigChanged(this::onMobConfigChanged);
        mobStatsPanel = statsPanel;

        initButtons();

        // Apply preselection if provided
        if (preselectedMobId != null && mobListPanel != null) {
            mobListPanel.selectAndScrollTo(preselectedMobId);
        }

        requestInitialMobPoolConfig();
    }

    private void initButtons() {
        backButton = EditorButton.builder("back", "< " + I18n.ui("back").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::attemptClose)
            .build();

        applyGlobalButton = EditorButton.builder("apply-global", I18n.translate("devmod.settings.button.apply_global").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> attemptApplyChanges(ConfigScope.GLOBAL))
            .build();

        proposeButton = EditorButton.builder("propose", I18n.translate("devmod.settings.button.propose").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> attemptApplyChanges(ConfigScope.PROPOSAL))
            .build();

        applySessionButton = EditorButton.builder("apply-session", I18n.translate("devmod.settings.button.apply_session").getString())
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> attemptApplyChanges(ConfigScope.SESSION))
            .build();

        selectAllButton = EditorButton.builder("select-all", I18n.translate("devmod.endurance.mob_editor.select_all").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(this::selectAll)
            .build();

        deselectAllButton = EditorButton.builder("deselect-all", I18n.translate("devmod.endurance.mob_editor.deselect_all").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(this::deselectAll)
            .build();

        resetButton = EditorButton.builder("reset", I18n.translate("devmod.settings.button.reset_all").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(this::resetAll)
            .build();
    }

    // ========== Callbacks ==========

    private void onMobSelected(ResourceLocation mobId) {
        var statsPanel = mobStatsPanel;
        if (statsPanel != null) {
            // Check if we have a modified config for this mob
            EnduranceMobConfig config = modifiedConfigs.get(mobId);
            if (config == null) {
                // Fall back to baseline override or registry default
                config = baseConfigs.get(mobId);
                if (config == null) {
                    config = EnduranceMobConfig.fromRegistryOrDefault(mobId);
                }
            }
            statsPanel.setMobConfig(config);
        }
    }

    private void onMobToggled(ResourceLocation mobId, Boolean enabled) {
        hasChanges = true;
        // The enabled state is tracked by MobListPanel
    }

    private void onMobConfigChanged(EnduranceMobConfig config) {
        if (config == null) {
            return;
        }
        ResourceLocation mobId = config.mobId();
        EnduranceMobConfig baseline = getBaselineConfig(mobId).withEnabled(true);
        EnduranceMobConfig normalized = config.withEnabled(true);
        if (normalized.equals(baseline)) {
            modifiedConfigs.remove(mobId);
        } else {
            modifiedConfigs.put(mobId, normalized);
        }
        hasChanges = true;
    }

    // ========== Actions ==========

    private void selectAll() {
        if (mobListPanel != null) {
            mobListPanel.selectAll();
            hasChanges = true;
        }
    }

    private void deselectAll() {
        if (mobListPanel != null) {
            mobListPanel.deselectAll();
            hasChanges = true;
        }
    }

    private void resetAll() {
        pendingServerConfig = null;
        ConfigScope scope = resolvePreferredScope();
        MobPoolConfigSyncPayload cached = ClientMobPoolConfigCache.get(scope);
        if (cached != null) {
            applyServerConfig(cached, true);
        } else {
            applyPoolConfig(null);
        }
        requestMobPoolConfig(scope);

        Notification notification = Notification.builder(NotificationCategory.SYSTEM)
            .titleKey("devmod.settings.reset.title")
            .messageKey("devmod.endurance.mob_editor.reset.server")
            .priority(NotificationPriority.NORMAL)
            .displayDurationMs(2000)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }

    private void resetToDefaults() {
        pendingServerConfig = null;
        applyPoolConfig(null);
        hasChanges = true;

        Notification notification = Notification.builder(NotificationCategory.SYSTEM)
            .titleKey("devmod.settings.reset.title")
            .messageKey("devmod.endurance.mob_editor.reset.defaults")
            .priority(NotificationPriority.NORMAL)
            .displayDurationMs(2000)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }

    private void attemptApplyChanges(ConfigScope scope) {
        if (!hasChanges && modifiedConfigs.isEmpty()) {
            return;
        }
        if (isInitialSyncPending()) {
            return;
        }
        ConfirmDialog dialog = confirmDialog;
        if (dialog != null && dialog.isVisible()) {
            return;
        }
        if (pendingServerConfig != null) {
            MobPoolConfigSyncPayload pending = pendingServerConfig;
            String title = I18n.translate("devmod.endurance.mob_editor.conflict.title").getString();
            String applyLabel = I18n.translate("devmod.endurance.mob_editor.conflict.apply_local").getString();
            String reviewLabel = I18n.translate("devmod.endurance.mob_editor.conflict.review_server").getString();
            String line1 = I18n.translate("devmod.endurance.mob_editor.conflict.body").getString();
            String line2 = I18n.translate("devmod.endurance.mob_editor.conflict.prompt").getString();
            ConfirmDialog conflictDialog = ConfirmDialog.create(
                title,
                applyLabel,
                reviewLabel,
                ConfirmDialog.Style.WARNING,
                () -> {
                    confirmDialog = null;
                    pendingServerConfig = null;
                    attemptApplyChanges(scope);
                },
                () -> {
                    confirmDialog = null;
                    pendingServerConfig = null;
                    applyServerConfig(pending, true);
                },
                line1,
                line2
            );
            confirmDialog = conflictDialog;
            conflictDialog.show();
            return;
        }
        if (!orphanedConfigs.isEmpty()) {
            String title = I18n.translate("devmod.endurance.mob_editor.orphaned.apply.title").getString();
            String keepLabel = I18n.translate("devmod.endurance.mob_editor.orphaned.apply.keep").getString();
            String dropLabel = I18n.translate("devmod.endurance.mob_editor.orphaned.apply.drop").getString();
            String line1 = I18n.translate("devmod.endurance.mob_editor.orphaned.apply.body",
                String.valueOf(orphanedConfigs.size())).getString();
            String line2 = I18n.translate("devmod.endurance.mob_editor.orphaned.apply.prompt").getString();
            ConfirmDialog orphanDialog = ConfirmDialog.create(
                title,
                keepLabel,
                dropLabel,
                ConfirmDialog.Style.WARNING,
                () -> {
                    confirmDialog = null;
                    applyChanges(scope, true);
                },
                () -> {
                    confirmDialog = null;
                    applyChanges(scope, false);
                },
                line1,
                line2
            );
            confirmDialog = orphanDialog;
            orphanDialog.show();
            return;
        }
        applyChanges(scope, true);
    }

    private void applyChanges(ConfigScope scope, boolean includeOrphans) {
        if (!hasChanges && modifiedConfigs.isEmpty()) {
            return;
        }
        pendingServerConfig = null;

        // Build the pool config
        EnduranceMobPoolConfig poolConfig = new EnduranceMobPoolConfig();

        var listPanel = mobListPanel;

        // Start from baseline overrides to preserve existing settings
        for (EnduranceMobConfig config : baseConfigs.values()) {
            EnduranceMobConfig resolved = config;
            if (listPanel != null) {
                boolean enabled = listPanel.isMobEnabled(config.mobId());
                if (enabled != config.enabled()) {
                    resolved = config.withEnabled(enabled);
                }
            }
            poolConfig.setMobConfig(resolved);
        }

        // Apply modified mob configs (override baseline)
        for (EnduranceMobConfig config : modifiedConfigs.values()) {
            EnduranceMobConfig resolved = config;
            if (listPanel != null) {
                boolean enabled = listPanel.isMobEnabled(config.mobId());
                if (enabled != config.enabled()) {
                    resolved = config.withEnabled(enabled);
                }
            }
            poolConfig.setMobConfig(resolved);
        }

        if (includeOrphans) {
            // Preserve configs for mobs not present in the local registry
            for (EnduranceMobConfig config : orphanedConfigs.values()) {
                poolConfig.setMobConfig(config);
            }
        }

        // Ensure disabled mobs are represented in payload
        if (listPanel != null) {
            for (ResourceLocation mobId : listPanel.getDisabledMobs()) {
                if (poolConfig.getMobConfigOrNull(mobId) == null) {
                    EnduranceMobConfig baseConfig = EnduranceMobConfig.fromRegistryOrDefault(mobId)
                        .withEnabled(false);
                    poolConfig.setMobConfig(baseConfig);
                }
            }
        }

        // Set global multipliers
        poolConfig.setGlobalMultipliers(globalHealthMult, globalDamageMult, globalSpeedMult, globalEliteChanceMult);

        // Set affix weights
        for (var entry : affixWeights.entrySet()) {
            poolConfig.setAffixWeight(entry.getKey(), entry.getValue());
        }

        // Create and send payload
        EnduranceMobConfigSyncPayload payload = EnduranceMobConfigSyncPayload.fromPoolConfig(poolConfig, scope);
        PacketDistributor.sendToServer(Objects.requireNonNull(payload, "Payload creation failed"));

        // Show notification
        String messageKey = switch (scope) {
            case GLOBAL -> "devmod.settings.applied.global";
            case SESSION -> "devmod.settings.applied.session";
            case PROPOSAL -> "devmod.settings.applied.proposal";
        };

        Notification notification = Notification.builder(NotificationCategory.SYSTEM)
            .titleKey("devmod.settings.applied.title")
            .messageKey(messageKey)
            .param("count", String.valueOf(poolConfig.getConfiguredMobCount()))
            .priority(NotificationPriority.NORMAL)
            .displayDurationMs(2500)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);

        // Clear changes tracking for SESSION scope (not GLOBAL/PROPOSAL as those need server confirmation)
        if (scope == ConfigScope.SESSION) {
            hasChanges = false;
        }
    }

    private void requestInitialMobPoolConfig() {
        if (initialConfigApplied) {
            return;
        }
        ConfigScope scope = resolvePreferredScope();
        MobPoolConfigSyncPayload cached = ClientMobPoolConfigCache.get(scope);
        if (cached != null) {
            applyServerConfig(cached);
        }
        requestMobPoolConfig(scope);
    }

    private void requestMobPoolConfig(ConfigScope scope) {
        if (scope == ConfigScope.SESSION) {
            awaitingSessionConfig = true;
        } else {
            awaitingGlobalConfig = true;
        }
        PacketDistributor.sendToServer(new RequestMobPoolConfigPayload(scope));
    }

    public void applyServerConfig(MobPoolConfigSyncPayload payload) {
        applyServerConfig(payload, false);
    }

    private void applyServerConfig(MobPoolConfigSyncPayload payload, boolean force) {
        if (payload == null || payload.data() == null) {
            return;
        }
        if (!force && hasChanges) {
            pendingServerConfig = payload;
            ConfigScope scope = payload.data().scope();
            if (scope == ConfigScope.SESSION) {
                awaitingSessionConfig = false;
            } else {
                awaitingGlobalConfig = false;
            }
            initialConfigApplied = true;
            return;
        }
        pendingServerConfig = null;
        ConfigScope scope = payload.data().scope();
        if (scope == ConfigScope.SESSION) {
            awaitingSessionConfig = false;
        } else {
            awaitingGlobalConfig = false;
        }
        applyPoolConfig(payload.data().toPoolConfig());
        if (scope == ConfigScope.SESSION && !payload.hasConfig()) {
            Notification notification = Notification.builder(NotificationCategory.SYSTEM)
                .titleKey("devmod.endurance.mob_editor.session_fallback.title")
                .messageKey("devmod.endurance.mob_editor.session_fallback.body")
                .priority(NotificationPriority.LOW)
                .displayDurationMs(2200)
                .build();
            ClientNotificationManager.INSTANCE.handleNotification(notification);
        }
        initialConfigApplied = true;
    }

    private void maybeShowPendingServerUpdate() {
        if (pendingServerConfig == null) {
            return;
        }
        if (isEditingActive()) {
            return;
        }
        ConfirmDialog dialog = confirmDialog;
        if (dialog != null && dialog.isVisible()) {
            return;
        }
        MobPoolConfigSyncPayload pending = pendingServerConfig;
        if (pending == null) {
            return;
        }
        String title = I18n.translate("devmod.endurance.mob_editor.sync.title").getString();
        String applyLabel = I18n.translate("devmod.endurance.mob_editor.sync.apply").getString();
        String keepLabel = I18n.translate("devmod.endurance.mob_editor.sync.keep").getString();
        String line1 = I18n.translate("devmod.endurance.mob_editor.sync.body").getString();
        String line2 = I18n.translate("devmod.endurance.mob_editor.sync.prompt").getString();
        ConfirmDialog syncDialog = ConfirmDialog.create(
            title,
            applyLabel,
            keepLabel,
            ConfirmDialog.Style.WARNING,
            () -> {
                confirmDialog = null;
                pendingServerConfig = null;
                applyServerConfig(pending, true);
            },
            () -> {
                confirmDialog = null;
                pendingServerConfig = null;
            },
            line1,
            line2
        );
        confirmDialog = syncDialog;
        syncDialog.show();
    }

    private void applyPoolConfig(@Nullable EnduranceMobPoolConfig poolConfig) {
        EnduranceMobPoolConfig resolved = poolConfig != null ? poolConfig.copy() : new EnduranceMobPoolConfig();

        baseConfigs.clear();
        modifiedConfigs.clear();
        orphanedConfigs.clear();

        Set<ResourceLocation> disabled = Set.copyOf(resolved.getDisabledMobs());
        for (ResourceLocation mobId : disabled) {
            if (resolved.getMobConfigOrNull(mobId) == null) {
                EnduranceMobConfig baseConfig = EnduranceMobConfig.fromRegistryOrDefault(mobId)
                    .withEnabled(false);
                resolved.setMobConfig(baseConfig);
            }
        }

        for (EnduranceMobConfig config : resolved.getAllMobConfigs()) {
            if (EnduranceQuestRegistry.INSTANCE.getMobConfig(config.mobId()).isPresent()) {
                baseConfigs.put(config.mobId(), config);
            } else {
                orphanedConfigs.put(config.mobId(), config);
            }
        }

        globalHealthMult = resolved.getGlobalHealthMult();
        globalDamageMult = resolved.getGlobalDamageMult();
        globalSpeedMult = resolved.getGlobalSpeedMult();
        globalEliteChanceMult = resolved.getGlobalEliteChanceMult();
        for (SpawnAffix affix : SpawnAffix.values()) {
            affixWeights.put(affix, resolved.getAffixWeight(affix));
        }

        var listPanel = mobListPanel;
        var statsPanel = mobStatsPanel;
        if (listPanel != null) {
            listPanel.reloadMobs();
            availableNamespaces = listPanel.getAvailableNamespaces();
            if (!availableNamespaces.contains(namespaceFilter)) {
                namespaceFilter = "all";
            }
            listPanel.setNamespaceFilter(namespaceFilter);
            listPanel.setTierFilter(tierFilter);
            listPanel.setSearchQuery(searchQuery);
            listPanel.applyPoolConfig(resolved);
            if (statsPanel != null) {
                ResourceLocation selectedMob = listPanel.getSelectedMobId();
                if (selectedMob != null) {
                    EnduranceMobConfig config = baseConfigs.get(selectedMob);
                    if (config == null) {
                        config = EnduranceMobConfig.fromRegistryOrDefault(selectedMob);
                    }
                    statsPanel.setMobConfig(config);
                }
            }
        }

        if (!orphanedConfigs.isEmpty()) {
            Notification notification = Notification.builder(NotificationCategory.SYSTEM)
                .titleKey("devmod.endurance.mob_editor.orphaned.title")
                .messageKey("devmod.endurance.mob_editor.orphaned.body")
                .param("count", String.valueOf(orphanedConfigs.size()))
                .priority(NotificationPriority.NORMAL)
                .displayDurationMs(2600)
                .build();
            ClientNotificationManager.INSTANCE.handleNotification(notification);
        }

        hasChanges = false;
    }

    private EnduranceMobConfig getBaselineConfig(ResourceLocation mobId) {
        EnduranceMobConfig config = baseConfigs.get(mobId);
        return config != null ? config : EnduranceMobConfig.fromRegistryOrDefault(mobId);
    }

    private void setSearchQuery(String query) {
        searchQuery = query != null ? query : "";
        if (mobListPanel != null) {
            mobListPanel.setSearchQuery(searchQuery);
        }
    }

    private boolean hasActiveSessionContext() {
        if (ClientQuestCache.hasActiveQuest()) {
            return true;
        }
        if (!ClientPartyCache.isInParty()) {
            return false;
        }
        var partyState = ClientPartyCache.getPartyState();
        return partyState != null && partyState != com.devmod.party.PartyData.PartyState.DISBANDED;
    }

    private ConfigScope resolvePreferredScope() {
        return hasActiveSessionContext() ? ConfigScope.SESSION : ConfigScope.GLOBAL;
    }

    private boolean isInitialSyncPending() {
        return !initialConfigApplied && (awaitingGlobalConfig || awaitingSessionConfig);
    }

    private boolean isEditingActive() {
        if (activeGlobalSlider >= 0 || activeAffixSlider >= 0) {
            return true;
        }
        var statsPanel = mobStatsPanel;
        return statsPanel != null && statsPanel.isSliderActive();
    }

    private void notifySessionApplyDisabled() {
        String titleKey = "devmod.endurance.mob_editor.session_disabled.title";
        String messageKey = null;
        if (isInitialSyncPending()) {
            messageKey = "devmod.endurance.mob_editor.syncing";
        } else if (!hasActiveSessionContext()) {
            messageKey = "devmod.endurance.mob_editor.session_disabled.no_quest";
        } else {
            var mc = minecraft;
            var player = mc != null ? mc.player : null;
            if (player != null && ClientPartyCache.isInParty()
                && !ClientPartyCache.isLeader(player.getUUID())) {
                messageKey = "devmod.endurance.mob_editor.session_disabled.not_leader";
            }
        }
        if (messageKey == null) {
            return;
        }
        Notification notification = Notification.builder(NotificationCategory.SYSTEM)
            .titleKey(titleKey)
            .messageKey(messageKey)
            .priority(NotificationPriority.LOW)
            .displayDurationMs(2200)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }

    // ========== Rendering ==========

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        // Background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Header
        renderHeader(graphics, mouseX, mouseY);

        // Panels
        if (mobListPanel != null && font != null) {
            mobListPanel.render(graphics, font, mouseX, mouseY);
        }
        if (mobStatsPanel != null && font != null) {
            mobStatsPanel.render(graphics, font, mouseX, mouseY);
        }

        // Filter controls (above mob list)
        renderFilterControls(graphics, mouseX, mouseY);

        // Select/Deselect buttons (below mob list)
        renderListButtons(graphics, mouseX, mouseY);

        // Global multipliers section
        renderGlobalMultipliers(graphics, mouseX, mouseY);

        // Footer buttons
        renderFooter(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Dropdowns and modal overlays on top
        if (namespaceDropdownOpen) {
            renderNamespaceDropdown(graphics, mouseX, mouseY);
        }
        if (tierDropdownOpen) {
            renderTierDropdown(graphics, mouseX, mouseY);
        }
        maybeShowPendingServerUpdate();
        ConfirmDialog dialog = confirmDialog;
        if (dialog != null && dialog.isVisible() && font != null) {
            dialog.render(graphics, font, width, height, mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        // Background
        graphics.fill(0, 0, width, HEADER_HEIGHT, DesignTokens.Surface.LEVEL_1);
        graphics.hLine(0, width, HEADER_HEIGHT - 1, DesignTokens.Border.DEFAULT);

        // Title
        var safeFont = font;
        if (safeFont != null) {
            String title = Objects.requireNonNull(I18n.translate("devmod.endurance.mob_editor.title").getString());
            UIScaleManager.drawScaledCenteredString(graphics, safeFont, title, width / 2, 12, DesignTokens.Text.PRIMARY);

            // Changes indicator
            if (hasChanges) {
                UIScaleManager.drawScaledString(graphics, safeFont, "*", width / 2 + UIScaleManager.getScaledStringWidth(safeFont, title) / 2 + 4, 12, DesignTokens.Semantic.WARNING, false);
            }
        }

        // Back button
        if (backButton != null) {
            backButton.render(graphics, PADDING, 8, 70, 22, mouseX, mouseY);
        }
    }

    private void renderFilterControls(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = font;
        if (safeFont == null) return;
        FilterLayout layout = getFilterLayout();

        // Filter bar background
        graphics.fill(layout.panelX, layout.panelY, layout.panelX + layout.panelWidth,
            layout.panelY + layout.panelHeight, DesignTokens.Surface.LEVEL_1);
        graphics.hLine(layout.panelX, layout.panelX + layout.panelWidth,
            layout.panelY + layout.panelHeight - 1, DesignTokens.Border.DEFAULT);

        // Namespace dropdown button
        boolean dropdownHovered = mouseX >= layout.namespaceX && mouseX < layout.namespaceX + layout.namespaceW
            && mouseY >= layout.namespaceY && mouseY < layout.namespaceY + layout.namespaceH;
        int bgColor = dropdownHovered ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_1;
        int borderColor = dropdownHovered ? DesignTokens.Border.LIGHT : DesignTokens.Border.DEFAULT;
        graphics.fill(layout.namespaceX, layout.namespaceY, layout.namespaceX + layout.namespaceW,
            layout.namespaceY + layout.namespaceH, bgColor);
        graphics.renderOutline(layout.namespaceX, layout.namespaceY, layout.namespaceW, layout.namespaceH, borderColor);

        String nsLabelRaw = "all".equals(namespaceFilter)
            ? I18n.translate("devmod.endurance.mob_editor.filter_all").getString()
            : namespaceFilter;
        String nsLabel = Objects.requireNonNull(
            safeFont.plainSubstrByWidth(Objects.requireNonNull(nsLabelRaw, "nsLabelRaw"), layout.namespaceW - 14),
            "nsLabel");
        int textColor = dropdownHovered ? DesignTokens.Text.WHITE : DesignTokens.Text.PRIMARY;
        UIScaleManager.drawScaledString(graphics, safeFont, nsLabel, layout.namespaceX + 4, layout.namespaceY + 5, textColor, false);
        UIScaleManager.drawScaledString(graphics, safeFont, "\u25BC", layout.namespaceX + layout.namespaceW - 12, layout.namespaceY + 5,
            dropdownHovered ? DesignTokens.Text.WHITE : DesignTokens.Text.SECONDARY, false);

        // Search box
        renderSearchBox(graphics, safeFont, layout.searchX, layout.searchY, layout.searchW, layout.searchH,
            mouseX, mouseY);

        // Tier dropdown button
        boolean tierHovered = mouseX >= layout.tierX && mouseX < layout.tierX + layout.tierW
            && mouseY >= layout.tierY && mouseY < layout.tierY + layout.tierH;
        int tierBg = tierHovered ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_1;
        int tierBorder = tierHovered ? DesignTokens.Border.LIGHT : DesignTokens.Border.DEFAULT;
        graphics.fill(layout.tierX, layout.tierY, layout.tierX + layout.tierW,
            layout.tierY + layout.tierH, tierBg);
        graphics.renderOutline(layout.tierX, layout.tierY, layout.tierW, layout.tierH, tierBorder);

        String tierLabelRaw = formatTierLabel(tierFilter);
        String tierLabel = Objects.requireNonNull(
            safeFont.plainSubstrByWidth(Objects.requireNonNull(tierLabelRaw, "tierLabelRaw"), layout.tierW - 14),
            "tierLabel");
        int tierColor = tierHovered ? DesignTokens.Text.WHITE : DesignTokens.Text.PRIMARY;
        UIScaleManager.drawScaledString(graphics, safeFont, tierLabel, layout.tierX + 4, layout.tierY + 5, tierColor, false);
        UIScaleManager.drawScaledString(graphics, safeFont, "\u25BC", layout.tierX + layout.tierW - 12, layout.tierY + 5,
            tierHovered ? DesignTokens.Text.WHITE : DesignTokens.Text.SECONDARY, false);

        var listPanel = mobListPanel;
        if (listPanel != null) {
            String countText = listPanel.getFilteredMobCount() + "/" + listPanel.getTotalMobCount();
            int countX = layout.panelX + layout.panelWidth - UIScaleManager.getScaledStringWidth(safeFont, countText) - 4;
            UIScaleManager.drawScaledString(graphics, safeFont, countText, countX, layout.tierY + 5, DesignTokens.Text.SECONDARY, false);
        }
    }

    private void renderNamespaceDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = font;
        if (safeFont == null) return;

        FilterLayout layout = getFilterLayout();
        int dropdownX = layout.namespaceX;
        int dropdownY = layout.namespaceY + layout.namespaceH;
        int dropdownWidth = layout.namespaceW;
        int itemHeight = 16;
        int dropdownHeight = availableNamespaces.size() * itemHeight + 4;

        // Background
        graphics.fill(dropdownX, dropdownY, dropdownX + dropdownWidth, dropdownY + dropdownHeight,
            DesignTokens.Surface.LEVEL_2);
        graphics.renderOutline(dropdownX, dropdownY, dropdownWidth, dropdownHeight, DesignTokens.Border.DEFAULT);

        // Items
        for (int i = 0; i < availableNamespaces.size(); i++) {
            String ns = availableNamespaces.get(i);
            int itemY = dropdownY + 2 + i * itemHeight;

            boolean hovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth
                && mouseY >= itemY && mouseY < itemY + itemHeight;
            boolean selected = ns.equals(namespaceFilter);

            if (hovered || selected) {
                graphics.fill(dropdownX + 2, itemY, dropdownX + dropdownWidth - 2, itemY + itemHeight - 1,
                    DesignTokens.Surface.LEVEL_1);
            }

            int textColor = selected ? DesignTokens.Accent.PRIMARY : DesignTokens.Text.PRIMARY;
            UIScaleManager.drawScaledString(graphics, safeFont, ns, dropdownX + 6, itemY + 4, textColor, false);
        }
    }

    private void renderTierDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = font;
        if (safeFont == null) return;

        FilterLayout layout = getFilterLayout();
        int dropdownX = layout.tierX;
        int dropdownY = layout.tierY + layout.tierH;
        int dropdownWidth = layout.tierW;
        int itemHeight = 16;
        int itemCount = MobTier.values().length + 1;
        int dropdownHeight = itemCount * itemHeight + 4;

        graphics.fill(dropdownX, dropdownY, dropdownX + dropdownWidth, dropdownY + dropdownHeight,
            DesignTokens.Surface.LEVEL_2);
        graphics.renderOutline(dropdownX, dropdownY, dropdownWidth, dropdownHeight, DesignTokens.Border.DEFAULT);

        int itemY = dropdownY + 2;
        for (int i = 0; i < itemCount; i++) {
            MobTier option = i == 0 ? null : MobTier.values()[i - 1];
            boolean selected = Objects.equals(option, tierFilter);
            boolean hovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth
                && mouseY >= itemY && mouseY < itemY + itemHeight;

            if (hovered || selected) {
                graphics.fill(dropdownX + 2, itemY, dropdownX + dropdownWidth - 2, itemY + itemHeight - 1,
                    DesignTokens.Surface.LEVEL_1);
            }

            String label = formatTierLabel(option);
            int textColor = selected ? DesignTokens.Accent.PRIMARY : DesignTokens.Text.PRIMARY;
            UIScaleManager.drawScaledString(graphics, safeFont, label, dropdownX + 6, itemY + 4, textColor, false);
            itemY += itemHeight;
        }
    }

    private void renderSearchBox(GuiGraphics graphics, Font safeFont, int x, int y, int width, int height,
            int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        boolean active = searchFocused;
        int bgColor = (hovered || active) ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_1;
        int borderColor = active ? DesignTokens.Accent.PRIMARY
            : (hovered ? DesignTokens.Border.LIGHT : DesignTokens.Border.DEFAULT);
        graphics.fill(x, y, x + width, y + height, bgColor);
        graphics.renderOutline(x, y, width, height, borderColor);

        boolean showPlaceholder = searchQuery.isEmpty() && !searchFocused;
        String text = showPlaceholder ? I18n.ui("search").getString() : searchQuery;
        int textColor = showPlaceholder ? DesignTokens.Text.MUTED : DesignTokens.Text.PRIMARY;
        String displayText = Objects.requireNonNull(
            safeFont.plainSubstrByWidth(Objects.requireNonNull(text, "text"), width - 20),
            "displayText");
        UIScaleManager.drawScaledString(graphics, safeFont, displayText, x + 4, y + 5, textColor, false);

        if (!searchQuery.isEmpty()) {
            int clearWidth = 14;
            int clearX = x + width - clearWidth;
            boolean clearHover = mouseX >= clearX && mouseX < x + width && mouseY >= y && mouseY < y + height;
            int clearBg = clearHover ? DesignTokens.withAlpha(DesignTokens.Semantic.ERROR, 0x33)
                : DesignTokens.withAlpha(DesignTokens.Surface.LEVEL_1, 0x55);
            graphics.fill(clearX, y + 1, x + width - 1, y + height - 1, clearBg);
            int clearColor = clearHover ? DesignTokens.Semantic.ERROR : DesignTokens.Text.SECONDARY;
            UIScaleManager.drawScaledString(graphics, safeFont, "x", clearX + 4, y + 5, clearColor, false);
        }
    }

    private String formatTierLabel(@Nullable MobTier tier) {
        if (tier == null) {
            return I18n.translate("devmod.endurance.mob_editor.filter_all_tiers").getString();
        }
        String key = switch (tier) {
            case TRIVIAL -> "devmod.endurance.quest.tier.trivial";
            case EASY -> "devmod.endurance.quest.tier.easy";
            case MEDIUM -> "devmod.endurance.quest.tier.medium";
            case HARD -> "devmod.endurance.quest.tier.hard";
            case ELITE -> "devmod.endurance.quest.tier.elite";
            case BOSS -> "devmod.endurance.quest.tier.boss";
        };
        return I18n.translate(key).getString();
    }

    private FilterLayout getFilterLayout() {
        int panelX = PADDING;
        int panelY = HEADER_HEIGHT;
        int panelWidth = LEFT_PANEL_WIDTH;
        int panelHeight = FILTER_BAR_HEIGHT;

        int innerX = panelX + 4;
        int innerWidth = panelWidth - 8;

        int rowHeight = FILTER_ROW_HEIGHT;
        int row1Y = panelY + FILTER_ROW_PADDING;
        int row2Y = row1Y + rowHeight + FILTER_ROW_GAP;

        int namespaceW = Math.min(84, innerWidth - 40);
        int searchW = innerWidth - namespaceW - 4;
        if (searchW < 40) {
            searchW = Math.max(24, searchW);
            namespaceW = innerWidth - searchW - 4;
        }

        int countReserve = 0;
        var listPanel = mobListPanel;
        if (font != null && listPanel != null) {
            String countText = listPanel.getFilteredMobCount() + "/" + listPanel.getTotalMobCount();
            countReserve = UIScaleManager.getScaledStringWidth(font, countText) + 6;
        }

        int tierW = Math.max(60, innerWidth - countReserve);
        if (tierW > innerWidth) {
            tierW = innerWidth;
        }

        return new FilterLayout(
            panelX, panelY, panelWidth, panelHeight,
            innerX, row1Y, namespaceW, rowHeight,
            innerX + namespaceW + 4, row1Y, searchW, rowHeight,
            innerX, row2Y, tierW, rowHeight
        );
    }

    private static final class FilterLayout {
        final int panelX;
        final int panelY;
        final int panelWidth;
        final int panelHeight;
        final int namespaceX;
        final int namespaceY;
        final int namespaceW;
        final int namespaceH;
        final int searchX;
        final int searchY;
        final int searchW;
        final int searchH;
        final int tierX;
        final int tierY;
        final int tierW;
        final int tierH;

        private FilterLayout(int panelX, int panelY, int panelWidth, int panelHeight,
                             int namespaceX, int namespaceY, int namespaceW, int namespaceH,
                             int searchX, int searchY, int searchW, int searchH,
                             int tierX, int tierY, int tierW, int tierH) {
            this.panelX = panelX;
            this.panelY = panelY;
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.namespaceX = namespaceX;
            this.namespaceY = namespaceY;
            this.namespaceW = namespaceW;
            this.namespaceH = namespaceH;
            this.searchX = searchX;
            this.searchY = searchY;
            this.searchW = searchW;
            this.searchH = searchH;
            this.tierX = tierX;
            this.tierY = tierY;
            this.tierW = tierW;
            this.tierH = tierH;
        }
    }

    private void renderListButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        int btnY = height - FOOTER_HEIGHT - GLOBAL_SECTION_HEIGHT - 28;
        int btnWidth = 90;
        int btnHeight = 20;

        if (selectAllButton != null) {
            selectAllButton.render(graphics, PADDING, btnY, btnWidth, btnHeight, mouseX, mouseY);
        }
        if (deselectAllButton != null) {
            deselectAllButton.render(graphics, PADDING + btnWidth + 4, btnY, btnWidth, btnHeight, mouseX, mouseY);
        }
    }

    private void renderGlobalMultipliers(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = font;
        if (safeFont == null) return;

        int sectionY = height - FOOTER_HEIGHT - GLOBAL_SECTION_HEIGHT;

        // Background
        graphics.fill(0, sectionY, width, sectionY + GLOBAL_SECTION_HEIGHT, DesignTokens.Surface.LEVEL_1);
        graphics.hLine(0, width, sectionY, DesignTokens.Border.DEFAULT);

        // Title
        UIScaleManager.drawScaledString(graphics, safeFont, Objects.requireNonNull(I18n.translate("devmod.endurance.mob_editor.global_multipliers").getString()),
            PADDING, sectionY + 6, DesignTokens.Text.SECONDARY, false);

        // Sliders
        int sliderY = sectionY + 22;
        int sliderWidth = (width - PADDING * 5) / 4;

        renderGlobalSlider(graphics, safeFont, PADDING, sliderY, sliderWidth, 0, "HP", globalHealthMult, mouseX, mouseY);
        renderGlobalSlider(graphics, safeFont, PADDING + sliderWidth + PADDING, sliderY, sliderWidth, 1, "DMG", globalDamageMult, mouseX, mouseY);
        renderGlobalSlider(graphics, safeFont, PADDING + (sliderWidth + PADDING) * 2, sliderY, sliderWidth, 2, "SPD", globalSpeedMult, mouseX, mouseY);
        renderGlobalSlider(graphics, safeFont, PADDING + (sliderWidth + PADDING) * 3, sliderY, sliderWidth, 3, "Elite", globalEliteChanceMult, mouseX, mouseY);

        // Affix weights
        int affixLabelY = sliderY + 20;
        UIScaleManager.drawScaledString(graphics, safeFont, Objects.requireNonNull(I18n.translate("devmod.endurance.mob_editor.affix_weights").getString()),
            PADDING, affixLabelY, DesignTokens.Text.SECONDARY, false);

        int affixY = affixLabelY + 12;
        int affixWidth = (width - PADDING * (AFFIX_COLUMNS + 1)) / AFFIX_COLUMNS;
        int rowHeight = 16;
        SpawnAffix[] affixes = SpawnAffix.values();
        for (int i = 0; i < affixes.length; i++) {
            int col = i % AFFIX_COLUMNS;
            int row = i / AFFIX_COLUMNS;
            int x = PADDING + col * (affixWidth + PADDING);
            int y = affixY + row * rowHeight;
            float value = affixWeights.getOrDefault(affixes[i], 1.0f);
            renderAffixSlider(graphics, safeFont, x, y, affixWidth, affixes[i], value, i, mouseX, mouseY);
        }
    }

    private void renderGlobalSlider(GuiGraphics graphics, @Nonnull Font safeFont, int x, int y, int sliderWidth, int sliderId,
            String label, float value, int mouseX, int mouseY) {
        // Slider track dimensions (for hover detection)
        int trackX = x + 35;
        int trackW = sliderWidth - 70;
        int trackY = y + 3;
        int trackH = 6;

        // Check if mouse is hovering over this slider's track area
        boolean isHovered = mouseX >= trackX && mouseX < trackX + trackW
            && mouseY >= trackY - 4 && mouseY < trackY + trackH + 4;
        boolean isActive = activeGlobalSlider == sliderId;

        // Label (highlight on hover)
        int labelColor = (isHovered || isActive) ? DesignTokens.Text.WHITE : DesignTokens.Text.PRIMARY;
        UIScaleManager.drawScaledString(graphics, safeFont, label, x, y, labelColor, false);

        // Track background (highlight on hover)
        int trackBgColor = isHovered ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_0;
        graphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, trackBgColor);

        // Slider fill
        float minVal = getGlobalSliderMin(sliderId);
        float maxVal = getGlobalSliderMax(sliderId);
        float ratio = Math.max(0f, Math.min(1f, (value - minVal) / (maxVal - minVal)));
        int fillW = (int) (ratio * trackW);
        int fillColor = Math.abs(value - 1.0f) < 0.01f ? DesignTokens.Text.SECONDARY : DesignTokens.Accent.PRIMARY;
        graphics.fill(trackX, trackY, trackX + fillW, trackY + trackH, fillColor);

        // Handle (brighter on hover/active)
        int handleX = trackX + fillW - 2;
        int handleColor = (isActive || isHovered) ? DesignTokens.Text.WHITE : DesignTokens.Border.LIGHT;
        graphics.fill(handleX, trackY - 2, handleX + 4, trackY + trackH + 2, handleColor);

        // Value (highlight on hover)
        String valueStr = String.format("%.1fx", value);
        int valueColor = (isHovered || isActive) ? DesignTokens.Text.WHITE : DesignTokens.Text.SECONDARY;
        UIScaleManager.drawScaledString(graphics, safeFont, valueStr, trackX + trackW + 4, y, valueColor, false);
    }

    private void renderAffixSlider(GuiGraphics graphics, @Nonnull Font safeFont, int x, int y, int width,
            SpawnAffix affix, float value, int sliderIndex, int mouseX, int mouseY) {
        int labelWidth = AFFIX_LABEL_WIDTH;
        int valueWidth = AFFIX_VALUE_WIDTH;
        int trackX = x + labelWidth;
        int trackW = Math.max(10, width - labelWidth - valueWidth - 8);
        int trackY = y + 3;
        int trackH = 6;

        boolean hovered = mouseX >= trackX && mouseX < trackX + trackW
            && mouseY >= trackY - 4 && mouseY < trackY + trackH + 4;
        boolean active = activeAffixSlider == sliderIndex;

        String label = formatAffixLabel(affix);
        int labelColor = (hovered || active) ? DesignTokens.Text.WHITE : DesignTokens.Text.PRIMARY;
        UIScaleManager.drawScaledString(graphics, safeFont, label, x, y, labelColor, false);

        int trackBg = hovered ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_0;
        graphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, trackBg);

        float minVal = 0.0f;
        float maxVal = 5.0f;
        float ratio = Math.max(0f, Math.min(1f, (value - minVal) / (maxVal - minVal)));
        int fillW = (int) (ratio * trackW);
        int fillColor = Math.abs(value - 1.0f) < 0.01f ? DesignTokens.Text.SECONDARY : DesignTokens.Accent.PRIMARY;
        graphics.fill(trackX, trackY, trackX + fillW, trackY + trackH, fillColor);

        int handleX = trackX + fillW - 2;
        int handleColor = (active || hovered) ? DesignTokens.Text.WHITE : DesignTokens.Border.LIGHT;
        graphics.fill(handleX, trackY - 2, handleX + 4, trackY + trackH + 2, handleColor);

        String valueStr = (value <= 0.01f && !hovered && !active) ? "OFF" : String.format("%.2fx", value);
        int valueColor = (hovered || active) ? DesignTokens.Text.WHITE : DesignTokens.Text.SECONDARY;
        UIScaleManager.drawScaledString(graphics, safeFont, valueStr, trackX + trackW + 4, y, valueColor, false);
    }

    private String formatAffixLabel(SpawnAffix affix) {
        return switch (affix) {
            case BASE -> "Base";
            case RUSH -> "Rush";
            case BRUTE -> "Brute";
            case SNIPER -> "Sniper";
            case ELITE -> "Elite";
            case OBJECTIVE_ELITE -> "ObjElite";
        };
    }

    private float getGlobalSliderMin(int sliderId) {
        return switch (sliderId) {
            case 0, 1, 2 -> 0.1f;
            case 3 -> 0.0f;
            default -> 0.1f;
        };
    }

    private float getGlobalSliderMax(int sliderId) {
        return switch (sliderId) {
            case 0, 1 -> 10.0f;
            case 2 -> 5.0f;
            case 3 -> 5.0f;
            default -> 10.0f;
        };
    }

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int footerY = height - FOOTER_HEIGHT;

        // Background
        graphics.fill(0, footerY, width, height, DesignTokens.Surface.LEVEL_1);
        graphics.hLine(0, width, footerY, DesignTokens.Border.DEFAULT);

        // Buttons
        int btnY = footerY + 15;
        int btnHeight = 24;
        int btnWidth = 110;
        int spacing = 8;

        // Right-aligned buttons
        int btnX = width - PADDING - btnWidth;
        boolean canApply = !isInitialSyncPending();

        EditorButton sessionBtn = applySessionButton;
        if (sessionBtn != null) {
            sessionBtn.setEnabled(canApply);
            sessionBtn.render(graphics, btnX, btnY, btnWidth, btnHeight, mouseX, mouseY);
        }
        btnX -= btnWidth + spacing;

        EditorButton propBtn = proposeButton;
        if (propBtn != null) {
            propBtn.setEnabled(canApply);
            propBtn.render(graphics, btnX, btnY, btnWidth, btnHeight, mouseX, mouseY);
        }
        btnX -= btnWidth + spacing;

        EditorButton globalBtn = applyGlobalButton;
        if (globalBtn != null) {
            globalBtn.setEnabled(canApply);
            globalBtn.render(graphics, btnX, btnY, btnWidth, btnHeight, mouseX, mouseY);
        }

        // Left-aligned reset button
        EditorButton rstBtn = resetButton;
        if (rstBtn != null) {
            rstBtn.render(graphics, PADDING, btnY, 80, btnHeight, mouseX, mouseY);
        }

        if (canApply) {
            return;
        }
        var safeFont = font;
        if (safeFont == null) {
            return;
        }
        String syncing = I18n.translate("devmod.endurance.mob_editor.syncing").getString();
        int syncX = PADDING + 88;
        int syncY = btnY + 7;
        UIScaleManager.drawScaledString(graphics, safeFont, syncing, syncX, syncY, DesignTokens.Text.MUTED, false);
    }

    // ========== Input Handling ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        UIScaleManager.update();
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        ConfirmDialog dialog = confirmDialog;
        if (dialog != null && dialog.isVisible() && dialog.mouseClicked(mouseX, mouseY, width, height)) {
            return true;
        }

        FilterLayout layout = getFilterLayout();

        // Close dropdowns if clicking outside
        if (namespaceDropdownOpen) {
            if (handleNamespaceDropdownClick(mouseX, mouseY)) {
                return true;
            }
            namespaceDropdownOpen = false;
        }
        if (tierDropdownOpen) {
            if (handleTierDropdownClick(mouseX, mouseY)) {
                return true;
            }
            tierDropdownOpen = false;
        }

        // Header buttons
        if (backButton != null && backButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Namespace dropdown toggle
        if (mouseX >= layout.namespaceX && mouseX < layout.namespaceX + layout.namespaceW
            && mouseY >= layout.namespaceY && mouseY < layout.namespaceY + layout.namespaceH) {
            namespaceDropdownOpen = !namespaceDropdownOpen;
            tierDropdownOpen = false;
            searchFocused = false;
            return true;
        }

        // Search box focus / clear
        if (mouseX >= layout.searchX && mouseX < layout.searchX + layout.searchW
            && mouseY >= layout.searchY && mouseY < layout.searchY + layout.searchH) {
            searchFocused = true;
            if (!searchQuery.isEmpty()) {
                int clearX = layout.searchX + layout.searchW - 14;
                if (mouseX >= clearX) {
                    setSearchQuery("");
                }
            }
            return true;
        }
        searchFocused = false;

        // Tier dropdown toggle
        if (mouseX >= layout.tierX && mouseX < layout.tierX + layout.tierW
            && mouseY >= layout.tierY && mouseY < layout.tierY + layout.tierH) {
            tierDropdownOpen = !tierDropdownOpen;
            namespaceDropdownOpen = false;
            return true;
        }

        // List buttons
        if (selectAllButton != null && selectAllButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (deselectAllButton != null && deselectAllButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Global multiplier sliders
        int sectionY = height - FOOTER_HEIGHT - GLOBAL_SECTION_HEIGHT;
        int sliderY = sectionY + 22;
        int sliderWidth = (width - PADDING * 5) / 4;

        for (int i = 0; i < 4; i++) {
            int sliderX = PADDING + i * (sliderWidth + PADDING) + 35;
            int trackW = sliderWidth - 70;

            if (mouseX >= sliderX && mouseX <= sliderX + trackW
                && mouseY >= sliderY && mouseY <= sliderY + 12) {
                activeGlobalSlider = i;
                updateGlobalSlider(mouseX, sliderX, trackW);
                return true;
            }
        }

        // Affix sliders
        int affixLabelY = sliderY + 20;
        int affixY = affixLabelY + 12;
        int affixWidth = (width - PADDING * (AFFIX_COLUMNS + 1)) / AFFIX_COLUMNS;
        int rowHeight = 16;
        SpawnAffix[] affixes = SpawnAffix.values();
        for (int i = 0; i < affixes.length; i++) {
            int col = i % AFFIX_COLUMNS;
            int row = i / AFFIX_COLUMNS;
            int x = PADDING + col * (affixWidth + PADDING);
            int y = affixY + row * rowHeight;
            int trackX = x + AFFIX_LABEL_WIDTH;
            int trackW = Math.max(10, affixWidth - AFFIX_LABEL_WIDTH - AFFIX_VALUE_WIDTH - 8);
            int trackY = y + 3;
            int trackH = 6;
            if (mouseX >= trackX && mouseX <= trackX + trackW
                && mouseY >= trackY - 4 && mouseY <= trackY + trackH + 4) {
                activeAffixSlider = i;
                updateAffixSlider(mouseX, trackX, trackW, affixes[i]);
                return true;
            }
        }

        // Footer buttons
        EditorButton globalBtn = applyGlobalButton;
        if (globalBtn != null && globalBtn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        EditorButton propBtn = proposeButton;
        if (propBtn != null && propBtn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        EditorButton sessionBtn = applySessionButton;
        if (sessionBtn != null) {
            if (!sessionBtn.isEnabled() && sessionBtn.getBounds().contains(mouseX, mouseY)) {
                notifySessionApplyDisabled();
                return true;
            }
            if (sessionBtn.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        EditorButton rstBtn = resetButton;
        if (rstBtn != null) {
            if (rstBtn.getBounds().contains(mouseX, mouseY) && hasShiftDown()) {
                resetToDefaults();
                return true;
            }
            if (rstBtn.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        // Panels
        if (mobListPanel != null && mobListPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (mobStatsPanel != null && mobStatsPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleNamespaceDropdownClick(double mouseX, double mouseY) {
        FilterLayout layout = getFilterLayout();
        int dropdownX = layout.namespaceX;
        int dropdownY = layout.namespaceY + layout.namespaceH;
        int dropdownWidth = layout.namespaceW;
        int itemHeight = 16;

        for (int i = 0; i < availableNamespaces.size(); i++) {
            int itemY = dropdownY + 2 + i * itemHeight;
            if (mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth
                && mouseY >= itemY && mouseY < itemY + itemHeight) {
                namespaceFilter = availableNamespaces.get(i);
                if (mobListPanel != null) {
                    mobListPanel.setNamespaceFilter(namespaceFilter);
                }
                namespaceDropdownOpen = false;
                return true;
            }
        }
        return false;
    }

    private boolean handleTierDropdownClick(double mouseX, double mouseY) {
        FilterLayout layout = getFilterLayout();
        int dropdownX = layout.tierX;
        int dropdownY = layout.tierY + layout.tierH;
        int dropdownWidth = layout.tierW;
        int itemHeight = 16;
        int itemCount = MobTier.values().length + 1;

        for (int i = 0; i < itemCount; i++) {
            int itemY = dropdownY + 2 + i * itemHeight;
            if (mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth
                && mouseY >= itemY && mouseY < itemY + itemHeight) {
                tierFilter = (i == 0) ? null : MobTier.values()[i - 1];
                if (mobListPanel != null) {
                    mobListPanel.setTierFilter(tierFilter);
                }
                tierDropdownOpen = false;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        UIScaleManager.update();
        // Release buttons
        if (backButton != null) backButton.mouseReleased(mouseX, mouseY, button);
        if (applyGlobalButton != null) applyGlobalButton.mouseReleased(mouseX, mouseY, button);
        if (proposeButton != null) proposeButton.mouseReleased(mouseX, mouseY, button);
        if (applySessionButton != null) applySessionButton.mouseReleased(mouseX, mouseY, button);
        if (selectAllButton != null) selectAllButton.mouseReleased(mouseX, mouseY, button);
        if (deselectAllButton != null) deselectAllButton.mouseReleased(mouseX, mouseY, button);
        if (resetButton != null) resetButton.mouseReleased(mouseX, mouseY, button);

        // Global slider
        if (activeGlobalSlider >= 0) {
            activeGlobalSlider = -1;
            return true;
        }
        if (activeAffixSlider >= 0) {
            activeAffixSlider = -1;
            return true;
        }

        // Panels
        if (mobStatsPanel != null && mobStatsPanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        UIScaleManager.update();
        // Global sliders
        if (activeGlobalSlider >= 0) {
            int sliderWidth = (width - PADDING * 5) / 4;
            int sliderX = PADDING + activeGlobalSlider * (sliderWidth + PADDING) + 35;
            int trackW = Math.max(10, sliderWidth - 70);
            updateGlobalSlider(mouseX, sliderX, trackW);
            return true;
        }

        if (activeAffixSlider >= 0) {
            int affixWidth = (width - PADDING * (AFFIX_COLUMNS + 1)) / AFFIX_COLUMNS;
            int col = activeAffixSlider % AFFIX_COLUMNS;
            int x = PADDING + col * (affixWidth + PADDING);
            int trackX = x + AFFIX_LABEL_WIDTH;
            int trackW = Math.max(10, affixWidth - AFFIX_LABEL_WIDTH - AFFIX_VALUE_WIDTH - 8);
            SpawnAffix[] affixes = SpawnAffix.values();
            if (activeAffixSlider < affixes.length) {
                updateAffixSlider(mouseX, trackX, trackW, affixes[activeAffixSlider]);
                return true;
            }
        }

        // Stats panel
        if (mobStatsPanel != null && mobStatsPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void updateGlobalSlider(double mouseX, int sliderX, int trackW) {
        float minVal = getGlobalSliderMin(activeGlobalSlider);
        float maxVal = getGlobalSliderMax(activeGlobalSlider);
        float ratio = (float) Math.max(0, Math.min(1, (mouseX - sliderX) / trackW));
        float newValue = minVal + ratio * (maxVal - minVal);

        switch (activeGlobalSlider) {
            case 0 -> globalHealthMult = newValue;
            case 1 -> globalDamageMult = newValue;
            case 2 -> globalSpeedMult = newValue;
            case 3 -> globalEliteChanceMult = newValue;
        }
        hasChanges = true;
    }

    private void updateAffixSlider(double mouseX, int sliderX, int trackW, SpawnAffix affix) {
        float minVal = 0.0f;
        float maxVal = 5.0f;
        float ratio = (float) Math.max(0, Math.min(1, (mouseX - sliderX) / trackW));
        float newValue = minVal + ratio * (maxVal - minVal);
        affixWeights.put(affix, newValue);
        hasChanges = true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        UIScaleManager.update();
        ConfirmDialog dialog = confirmDialog;
        if (dialog != null && dialog.isVisible() && dialog.mouseScrolled(mouseX, mouseY, scrollY, width, height)) {
            return true;
        }
        if (mobListPanel != null && mobListPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (mobStatsPanel != null && mobStatsPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ConfirmDialog dialog = confirmDialog;
        if (dialog != null && dialog.isVisible() && dialog.keyPressed(keyCode)) {
            return true;
        }

        if (searchFocused) {
            if (keyCode == 259) { // Backspace
                if (!searchQuery.isEmpty()) {
                    setSearchQuery(searchQuery.substring(0, searchQuery.length() - 1));
                }
                return true;
            }
            if (keyCode == 256) { // Escape
                searchFocused = false;
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                searchFocused = false;
                return true;
            }
        }

        if (keyCode == 256) {
            attemptClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        ConfirmDialog dialog = confirmDialog;
        if (dialog != null && dialog.isVisible() && dialog.charTyped(codePoint, modifiers)) {
            return true;
        }

        if (searchFocused && !Character.isISOControl(codePoint)) {
            if (searchQuery.length() < 32) {
                setSearchQuery(searchQuery + codePoint);
            }
            return true;
        }

        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        attemptClose();
    }

    private void attemptClose() {
        ConfirmDialog dialog = confirmDialog;
        if (dialog != null && dialog.isVisible()) {
            return;
        }

        if (hasChanges) {
            String title = I18n.translate("devmod.ui.unsaved.title").getString();
            String confirmLabel = I18n.translate("devmod.ui.unsaved.discard").getString();
            String cancelLabel = I18n.ui("cancel").getString();
            String line1 = I18n.translate("devmod.ui.unsaved.body").getString();
            String line2 = I18n.translate("devmod.ui.unsaved.prompt").getString();
            ConfirmDialog unsavedDialog = ConfirmDialog.create(
                title,
                confirmLabel,
                cancelLabel,
                ConfirmDialog.Style.DANGER,
                () -> {
                    confirmDialog = null;
                    closeScreen();
                },
                () -> confirmDialog = null,
                line1,
                line2
            );
            confirmDialog = unsavedDialog;
            unsavedDialog.show();
            return;
        }

        closeScreen();
    }

    private void closeScreen() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
