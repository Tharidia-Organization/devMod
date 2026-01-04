package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.EnduranceMobConfigSyncPayload;
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
    private static final int GLOBAL_SECTION_HEIGHT = 60;
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

    // Global multipliers
    private float globalHealthMult = 1.0f;
    private float globalDamageMult = 1.0f;
    private float globalSpeedMult = 1.0f;
    private float globalEliteChanceMult = 1.0f;

    // Affix weights
    private final Map<SpawnAffix, Float> affixWeights = new EnumMap<>(SpawnAffix.class);

    // Filter state
    private String namespaceFilter = "all";

    // UI State
    private int activeGlobalSlider = -1;
    private boolean hasChanges = false;

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
        listPanel.setOnMobSelected(this::onMobSelected);
        listPanel.setOnMobToggled(this::onMobToggled);
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
    }

    private void initButtons() {
        backButton = EditorButton.builder("back", "< " + I18n.ui("back").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();

        applyGlobalButton = EditorButton.builder("apply-global", I18n.translate("devmod.settings.button.apply_global").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> applyChanges(ConfigScope.GLOBAL))
            .build();

        proposeButton = EditorButton.builder("propose", I18n.translate("devmod.settings.button.propose").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> applyChanges(ConfigScope.PROPOSAL))
            .build();

        applySessionButton = EditorButton.builder("apply-session", I18n.translate("devmod.settings.button.apply_session").getString())
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> applyChanges(ConfigScope.SESSION))
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
                // Use default from registry
                config = EnduranceMobConfig.fromRegistryOrDefault(mobId);
            }
            statsPanel.setMobConfig(config);
        }
    }

    private void onMobToggled(ResourceLocation mobId, Boolean enabled) {
        hasChanges = true;
        // The enabled state is tracked by MobListPanel
    }

    private void onMobConfigChanged(EnduranceMobConfig config) {
        if (config != null) {
            modifiedConfigs.put(config.mobId(), config);
            hasChanges = true;
        }
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
        modifiedConfigs.clear();
        globalHealthMult = 1.0f;
        globalDamageMult = 1.0f;
        globalSpeedMult = 1.0f;
        globalEliteChanceMult = 1.0f;
        for (SpawnAffix affix : SpawnAffix.values()) {
            affixWeights.put(affix, 1.0f);
        }

        // Reload panels
        var listPanel = mobListPanel;
        var statsPanel = mobStatsPanel;
        if (listPanel != null && statsPanel != null) {
            ResourceLocation selectedMob = listPanel.getSelectedMobId();
            if (selectedMob != null) {
                statsPanel.setMobConfig(EnduranceMobConfig.fromRegistryOrDefault(selectedMob));
            }
        }

        hasChanges = false;

        Notification notification = Notification.builder(NotificationCategory.SYSTEM)
            .titleKey("devmod.settings.reset.title")
            .messageKey("devmod.settings.reset.all")
            .priority(NotificationPriority.NORMAL)
            .displayDurationMs(2000)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }

    private void applyChanges(ConfigScope scope) {
        if (!hasChanges && modifiedConfigs.isEmpty()) {
            return;
        }

        // Build the pool config
        EnduranceMobPoolConfig poolConfig = new EnduranceMobPoolConfig();

        // Add modified mob configs
        for (EnduranceMobConfig config : modifiedConfigs.values()) {
            poolConfig.setMobConfig(config);
        }

        // Set disabled mobs from the list panel
        if (mobListPanel != null) {
            for (ResourceLocation mobId : mobListPanel.getDisabledMobs()) {
                poolConfig.disableMob(mobId);
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
            .param("count", String.valueOf(modifiedConfigs.size() + (mobListPanel != null ? mobListPanel.getDisabledMobs().size() : 0)))
            .priority(NotificationPriority.NORMAL)
            .displayDurationMs(2500)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);

        // Clear changes tracking for SESSION scope (not GLOBAL/PROPOSAL as those need server confirmation)
        if (scope == ConfigScope.SESSION) {
            hasChanges = false;
        }
    }

    // ========== Rendering ==========

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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

        // Namespace dropdown (render last to be on top)
        if (namespaceDropdownOpen) {
            renderNamespaceDropdown(graphics, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        // Background
        graphics.fill(0, 0, width, HEADER_HEIGHT, DesignTokens.Surface.LEVEL_1);
        graphics.hLine(0, width, HEADER_HEIGHT - 1, DesignTokens.Border.DEFAULT);

        // Title
        var safeFont = font;
        if (safeFont != null) {
            String title = Objects.requireNonNull(I18n.translate("devmod.endurance.mob_editor.title").getString());
            graphics.drawString(safeFont, title, width / 2 - safeFont.width(title) / 2, 12, DesignTokens.Text.PRIMARY, false);

            // Changes indicator
            if (hasChanges) {
                graphics.drawString(safeFont, "*", width / 2 + safeFont.width(title) / 2 + 4, 12, DesignTokens.Semantic.WARNING, false);
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

        int filterY = HEADER_HEIGHT - 24;
        int filterX = PADDING;

        // Namespace dropdown button
        int dropdownWidth = 80;
        int dropdownHeight = 18;

        // Check hover state for dropdown button
        boolean dropdownHovered = mouseX >= filterX && mouseX < filterX + dropdownWidth
            && mouseY >= filterY && mouseY < filterY + dropdownHeight;

        int bgColor = dropdownHovered ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_1;
        int borderColor = dropdownHovered ? DesignTokens.Border.LIGHT : DesignTokens.Border.DEFAULT;

        graphics.fill(filterX, filterY, filterX + dropdownWidth, filterY + dropdownHeight, bgColor);
        graphics.renderOutline(filterX, filterY, dropdownWidth, dropdownHeight, borderColor);

        String displayNs = namespaceFilter.length() > 8 ? namespaceFilter.substring(0, 7) + ".." : namespaceFilter;
        int textColor = dropdownHovered ? DesignTokens.Text.WHITE : DesignTokens.Text.PRIMARY;
        graphics.drawString(safeFont, displayNs, filterX + 4, filterY + 5, textColor, false);
        graphics.drawString(safeFont, "\u25BC", filterX + dropdownWidth - 12, filterY + 5,
            dropdownHovered ? DesignTokens.Text.WHITE : DesignTokens.Text.SECONDARY, false);

        // Tier filter buttons (below namespace)
        // This is simplified - just show count
        int countX = filterX + dropdownWidth + 8;
        var listPanel = mobListPanel;
        if (listPanel != null) {
            String countText = listPanel.getFilteredMobCount() + "/" + listPanel.getTotalMobCount();
            graphics.drawString(safeFont, countText, countX, filterY + 5, DesignTokens.Text.SECONDARY, false);
        }
    }

    private void renderNamespaceDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = font;
        if (safeFont == null) return;

        int dropdownX = PADDING;
        int dropdownY = HEADER_HEIGHT - 6;
        int dropdownWidth = 100;
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
            graphics.drawString(safeFont, ns, dropdownX + 6, itemY + 4, textColor, false);
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
        graphics.drawString(safeFont, Objects.requireNonNull(I18n.translate("devmod.endurance.mob_editor.global_multipliers").getString()),
            PADDING, sectionY + 6, DesignTokens.Text.SECONDARY, false);

        // Sliders
        int sliderY = sectionY + 22;
        int sliderWidth = (width - PADDING * 5) / 4;

        renderGlobalSlider(graphics, safeFont, PADDING, sliderY, sliderWidth, 0, "HP", globalHealthMult, mouseX, mouseY);
        renderGlobalSlider(graphics, safeFont, PADDING + sliderWidth + PADDING, sliderY, sliderWidth, 1, "DMG", globalDamageMult, mouseX, mouseY);
        renderGlobalSlider(graphics, safeFont, PADDING + (sliderWidth + PADDING) * 2, sliderY, sliderWidth, 2, "SPD", globalSpeedMult, mouseX, mouseY);
        renderGlobalSlider(graphics, safeFont, PADDING + (sliderWidth + PADDING) * 3, sliderY, sliderWidth, 3, "Elite", globalEliteChanceMult, mouseX, mouseY);
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
        graphics.drawString(safeFont, label, x, y, labelColor, false);

        // Track background (highlight on hover)
        int trackBgColor = isHovered ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_0;
        graphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, trackBgColor);

        // Slider fill (0.1 to 3.0 range, center at 1.0)
        float minVal = 0.1f;
        float maxVal = 3.0f;
        float ratio = (value - minVal) / (maxVal - minVal);
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
        graphics.drawString(safeFont, valueStr, trackX + trackW + 4, y, valueColor, false);
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

        if (applySessionButton != null) {
            applySessionButton.render(graphics, btnX, btnY, btnWidth, btnHeight, mouseX, mouseY);
        }
        btnX -= btnWidth + spacing;

        if (proposeButton != null) {
            proposeButton.render(graphics, btnX, btnY, btnWidth, btnHeight, mouseX, mouseY);
        }
        btnX -= btnWidth + spacing;

        if (applyGlobalButton != null) {
            applyGlobalButton.render(graphics, btnX, btnY, btnWidth, btnHeight, mouseX, mouseY);
        }

        // Left-aligned reset button
        if (resetButton != null) {
            resetButton.render(graphics, PADDING, btnY, 80, btnHeight, mouseX, mouseY);
        }
    }

    // ========== Input Handling ==========

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Close namespace dropdown if clicking outside
        if (namespaceDropdownOpen) {
            if (handleNamespaceDropdownClick(mouseX, mouseY)) {
                return true;
            }
            namespaceDropdownOpen = false;
        }

        // Header buttons
        if (backButton != null && backButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Namespace dropdown toggle
        int filterY = HEADER_HEIGHT - 24;
        if (mouseX >= PADDING && mouseX < PADDING + 80 && mouseY >= filterY && mouseY < filterY + 18) {
            namespaceDropdownOpen = !namespaceDropdownOpen;
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

        // Footer buttons
        if (applyGlobalButton != null && applyGlobalButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (proposeButton != null && proposeButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (applySessionButton != null && applySessionButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (resetButton != null && resetButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
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
        int dropdownX = PADDING;
        int dropdownY = HEADER_HEIGHT - 6;
        int dropdownWidth = 100;
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

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
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

        // Panels
        if (mobStatsPanel != null && mobStatsPanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Global sliders
        if (activeGlobalSlider >= 0) {
            int sliderWidth = (width - PADDING * 5) / 4;
            int sliderX = PADDING + activeGlobalSlider * (sliderWidth + PADDING) + 35;
            int trackW = sliderWidth - 70;
            updateGlobalSlider(mouseX, sliderX, trackW);
            return true;
        }

        // Stats panel
        if (mobStatsPanel != null && mobStatsPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void updateGlobalSlider(double mouseX, int sliderX, int trackW) {
        float minVal = 0.1f;
        float maxVal = 3.0f;
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mobListPanel != null && mobListPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (mobStatsPanel != null && mobStatsPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
