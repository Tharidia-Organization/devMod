package com.devmod.client.zone;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.BaseDevModScreen;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.zone.widget.ZoneBoundsWidget;
import com.devmod.runtime.NexusDimensionManager;
import com.devmod.zone.data.ZoneBounds;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZoneNaming;
import com.devmod.zone.network.DeleteZonePayload;
import com.devmod.zone.network.SaveZonePayload;

/**
 * Zone Editor screen - the main interface for configuring zone markers.
 * Contains 4 tabs: General, Bounds, Style, Options.
 */
@OnlyIn(Dist.CLIENT)
public class ZoneEditorScreen extends BaseDevModScreen {

    // ========================================================================
    // Constants
    // ========================================================================

    private static final int EDITOR_WIDTH = 420;
    private static final int EDITOR_HEIGHT = 320;
    private static final int TAB_HEIGHT = 24;
    private static final int FIELD_HEIGHT = 20;
    private static final int PADDING = 12;

    private static final int COLOR_EDITOR_BG = DesignTokens.Background.PANEL();
    private static final int COLOR_EDITOR_BORDER = DesignTokens.Border.DEFAULT();
    private static final int COLOR_TAB_ACTIVE = DesignTokens.Accent.PRIMARY();
    private static final int COLOR_TEXT_PRIMARY = DesignTokens.Text.PRIMARY();
    private static final int COLOR_TEXT_MUTED = DesignTokens.Text.MUTED();

    private static final String[] TAB_NAMES = {"general", "bounds", "style", "options"};

    // Zone colors - synced with DesignTokens.Nexus.ZONE_* constants
    // Using RGB values without alpha (alpha added during rendering)
    private static final int[] ZONE_COLORS = {
        0x44FFFF, // HUB - Cyan (matches DesignTokens.Nexus.ZONE_TITLE)
        0xFF4444, // COMBAT - Red (matches DesignTokens.Nexus.ZONE_COMBAT)
        0xFF8800, // ARENA - Orange (matches DesignTokens.Nexus.ZONE_ARENA)
        0x4488FF, // UI - Light blue (matches DesignTokens.Nexus.ZONE_UI)
        0x44FF44, // TELEMETRY - Green (matches DesignTokens.Nexus.ZONE_TELEMETRY)
        0xFFFF44, // SHOWCASE - Yellow (matches DesignTokens.Nexus.ZONE_SHOWCASE)
        0xAA44FF, // INTEGRATION - Purple (matches DesignTokens.Nexus.ZONE_INTEGRATION)
        0x44FFFF, // SANDBOX - Cyan (matches DesignTokens.Nexus.ZONE_SANDBOX)
        0xAAAAAA, // MECHANICS - Gray (matches DesignTokens.Nexus.ZONE_MECHANICS)
        0xFFAA00, // OVERVIEW - Gold
        0x666666  // SPECIAL - Dark gray
    };

    private static final String[] ZONE_COLOR_NAMES = {
        "Hub (Cyan)", "Combat (Red)", "Arena (Orange)", "UI (Light Blue)",
        "Telemetry (Green)", "Showcase (Yellow)", "Integration (Purple)",
        "Sandbox (Cyan)", "Mechanics (Gray)", "Overview (Gold)", "Special (Dark Gray)"
    };

    // ========================================================================
    // State
    // ========================================================================

    @Nullable
    private final ZoneDefinition existingZone;
    @Nullable
    private final BlockPos markerPosition;
    private final boolean canDelete;
    private final boolean isNewZone;

    // Tab management
    private int currentTab = 0;

    // Editable fields
    private String zoneId;
    private String displayName;
    private int selectedColorIndex = 0;
    private ZoneBounds bounds;
    @Nullable private BlockPos spawnOffset;
    private String hintMessage;
    private boolean allowBuilding;
    private boolean isSpecial;
    private boolean hasTeleport;
    @Nullable private BlockPos portalOffset;
    private int priority;

    // Widgets
    @Nullable private EditBox zoneIdField;
    @Nullable private EditBox displayNameField;
    @Nullable private EditBox hintMessageField;
    @Nullable private ZoneBoundsWidget boundsWidget;

    // Delete confirmation state
    private boolean deleteConfirmPending = false;
    @Nullable private EditorButtonWidget deleteButton;
    @Nullable private EditorButtonWidget allowBuildingButton;
    @Nullable private EditorButtonWidget specialZoneButton;
    @Nullable private EditorButtonWidget teleportableButton;
    @Nullable private EditorButtonWidget priorityUpButton;
    @Nullable private EditorButtonWidget priorityDownButton;

    private int optionsTabX;
    private int optionsTabY;
    private int optionsTabWidth;

    // ========================================================================
    // Constructor
    // ========================================================================

    public ZoneEditorScreen(
            @Nullable ZoneDefinition existingZone,
            @Nullable BlockPos markerPosition,
            boolean canDelete
    ) {
        super(Component.translatable("zone.editor.title"), "zone_editor");
        this.existingZone = existingZone;
        this.markerPosition = markerPosition;
        this.canDelete = canDelete;
        this.isNewZone = existingZone == null;

        // Initialize fields from existing zone or defaults
        if (existingZone != null) {
            this.zoneId = existingZone.zoneId();
            this.displayName = existingZone.displayName();
            this.bounds = existingZone.bounds();
            this.spawnOffset = existingZone.spawnOffset();
            this.hintMessage = existingZone.hintMessage();
            this.allowBuilding = existingZone.allowBuilding();
            this.isSpecial = existingZone.isSpecial();
            this.hasTeleport = existingZone.hasTeleport();
            this.portalOffset = existingZone.portalOffset();
            this.priority = existingZone.priority();

            // Find matching color index
            int color = existingZone.color();
            for (int i = 0; i < ZONE_COLORS.length; i++) {
                if (ZONE_COLORS[i] == color) {
                    selectedColorIndex = i;
                    break;
                }
            }
        } else {
            // Defaults for new zone
            this.zoneId = "";
            this.displayName = "New Zone";
            this.bounds = markerPosition != null
                ? ZoneBounds.fromCenterAndSize(markerPosition, 16, 8, 16)
                : ZoneBounds.fromCenterAndSize(Objects.requireNonNull(BlockPos.ZERO), 16, 8, 16);
            this.spawnOffset = null;
            this.hintMessage = "";
            this.allowBuilding = false;
            this.isSpecial = false;
            this.hasTeleport = true;
            this.portalOffset = null;
            this.priority = 0;
        }
    }

    // ========================================================================
    // Initialization
    // ========================================================================

    @Override
    protected void initContent() {
        int centerX = this.width / 2;
        int leftX = centerX - EDITOR_WIDTH / 2;
        int topY = (this.height - EDITOR_HEIGHT) / 2;

        // Tab buttons
        int tabWidth = (EDITOR_WIDTH - PADDING * 2) / TAB_NAMES.length - 2;
        int tabStartX = leftX + PADDING;
        int tabY = topY + 30;

        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int tabIndex = i;
            EditorButton tabButton = EditorButton.builder("zone-editor-tab-" + TAB_NAMES[i],
                    Component.translatable("zone.editor.tab." + TAB_NAMES[i]).getString())
                .style(EditorButton.Style.GHOST)
                .size(EditorButton.Size.MEDIUM)
                .onClick(() -> selectTab(tabIndex))
                .build();
            this.addRenderableWidget(new EditorButtonWidget(tabButton,
                tabStartX + i * (tabWidth + 2), tabY, tabWidth, TAB_HEIGHT));
        }

        // Initialize current tab content
        initTabContent(leftX, topY);

        // Bottom buttons
        int buttonY = topY + EDITOR_HEIGHT - 35;
        int buttonWidth = 80;
        int spacing = 10;

        // Delete button (if allowed)
        if (canDelete && !isNewZone) {
            String deleteKey = deleteConfirmPending ? "zone.editor.delete_confirm" : "zone.editor.delete";
            EditorButton delete = EditorButton.builder("zone-editor-delete",
                    Component.translatable(deleteKey).getString())
                .style(EditorButton.Style.DANGER)
                .size(EditorButton.Size.MEDIUM)
                .onClick(this::handleDeleteClick)
                .build();
            deleteButton = new EditorButtonWidget(delete, leftX + PADDING, buttonY, buttonWidth, 20);
            this.addRenderableWidget(deleteButton);
        }

        // Save button
        int saveX = leftX + EDITOR_WIDTH - PADDING - buttonWidth;
        EditorButton saveButton = EditorButton.builder("zone-editor-save",
                Component.translatable("zone.editor.save").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::saveZone)
            .build();
        this.addRenderableWidget(new EditorButtonWidget(saveButton, saveX, buttonY, buttonWidth, 20));

        // Cancel button
        EditorButton cancelButton = EditorButton.builder("zone-editor-cancel",
                Component.translatable("zone.editor.cancel").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();
        this.addRenderableWidget(new EditorButtonWidget(cancelButton,
            saveX - buttonWidth - spacing, buttonY, buttonWidth, 20));
    }

    private void selectTab(int tabIndex) {
        this.currentTab = tabIndex;
        this.rebuildWidgets();
    }

    private void initTabContent(int leftX, int topY) {
        int contentX = leftX + PADDING;
        int contentY = topY + 60;
        int contentWidth = EDITOR_WIDTH - PADDING * 2;
        int contentHeight = EDITOR_HEIGHT - 110;

        // Clear widgets
        zoneIdField = null;
        displayNameField = null;
        hintMessageField = null;
        boundsWidget = null;

        switch (currentTab) {
            case 0 -> initGeneralTab(contentX, contentY, contentWidth);
            case 1 -> initBoundsTab(contentX, contentY, contentWidth, contentHeight);
            case 2 -> initStyleTab(contentX, contentY, contentWidth);
            case 3 -> initOptionsTab(contentX, contentY, contentWidth);
        }
    }

    private void initGeneralTab(int x, int y, int width) {
        int fieldWidth = width - 100;
        int lineSpacing = 24;
        int spawnLabelY = y + 100;
        int portalLabelY = spawnLabelY + 50;

        // Zone ID field
        EditBox zoneIdBox = new EditBox(safeFont(), x + 100, y, fieldWidth, FIELD_HEIGHT,
            Objects.requireNonNull(Component.translatable("zone.editor.field.zone_id")));
        zoneIdBox.setMaxLength(ZoneNaming.MAX_ID_LENGTH);
        zoneIdBox.setValue(Objects.requireNonNull(zoneId));
        zoneIdBox.setResponder(value -> this.zoneId = value);
        zoneIdBox.setEditable(isNewZone); // Can only edit ID for new zones
        zoneIdBox.setBordered(false);
        zoneIdBox.setTextColor(DesignTokens.Text.PRIMARY);
        zoneIdBox.setTextColorUneditable(DesignTokens.Text.MUTED);
        this.zoneIdField = zoneIdBox;
        this.addRenderableWidget(zoneIdBox);

        // Display name field
        EditBox displayNameBox = new EditBox(safeFont(), x + 100, y + 30, fieldWidth, FIELD_HEIGHT,
            Objects.requireNonNull(Component.translatable("zone.editor.field.display_name")));
        displayNameBox.setMaxLength(ZoneNaming.MAX_NAME_LENGTH);
        displayNameBox.setValue(Objects.requireNonNull(displayName));
        displayNameBox.setResponder(value -> this.displayName = value);
        displayNameBox.setBordered(false);
        displayNameBox.setTextColor(DesignTokens.Text.PRIMARY);
        displayNameBox.setTextColorUneditable(DesignTokens.Text.MUTED);
        this.displayNameField = displayNameBox;
        this.addRenderableWidget(displayNameBox);

        // Hint message field
        EditBox hintMessageBox = new EditBox(safeFont(), x + 100, y + 60, fieldWidth, FIELD_HEIGHT,
            Objects.requireNonNull(Component.translatable("zone.editor.field.hint")));
        hintMessageBox.setMaxLength(ZoneNaming.MAX_HINT_LENGTH);
        hintMessageBox.setValue(Objects.requireNonNull(hintMessage));
        hintMessageBox.setResponder(value -> this.hintMessage = value);
        hintMessageBox.setBordered(false);
        hintMessageBox.setTextColor(DesignTokens.Text.PRIMARY);
        hintMessageBox.setTextColorUneditable(DesignTokens.Text.MUTED);
        this.hintMessageField = hintMessageBox;
        this.addRenderableWidget(hintMessageBox);

        // Spawn offset buttons
        int spawnY = spawnLabelY;
        EditorButton usePosition = EditorButton.builder("zone-editor-use-position",
                Component.translatable("zone.editor.use_position").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::useCurrentPosition)
            .build();
        this.addRenderableWidget(new EditorButtonWidget(usePosition, x + 100, spawnY, fieldWidth, FIELD_HEIGHT));

        EditorButton clearSpawn = EditorButton.builder("zone-editor-clear-spawn",
                Component.translatable("zone.editor.clear_spawn").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::clearSpawnOffset)
            .build();
        this.addRenderableWidget(new EditorButtonWidget(clearSpawn, x + 100, spawnY + lineSpacing, fieldWidth, FIELD_HEIGHT));

        // Portal offset buttons
        EditorButton setPortal = EditorButton.builder("zone-editor-use-portal",
                Component.translatable("zone.editor.use_portal_position").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .enabled(hasTeleport)
            .onClick(this::useCurrentPortalPosition)
            .build();
        this.addRenderableWidget(new EditorButtonWidget(setPortal, x + 100, portalLabelY, fieldWidth, FIELD_HEIGHT));

        EditorButton clearPortal = EditorButton.builder("zone-editor-clear-portal",
                Component.translatable("zone.editor.clear_portal").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .enabled(hasTeleport)
            .onClick(this::clearPortalOffset)
            .build();
        this.addRenderableWidget(new EditorButtonWidget(clearPortal, x + 100, portalLabelY + lineSpacing, fieldWidth, FIELD_HEIGHT));
    }

    private void initBoundsTab(int x, int y, int width, int height) {
        boundsWidget = new ZoneBoundsWidget(x, y, width, height, Objects.requireNonNull(bounds), updatedBounds -> {
            this.bounds = updatedBounds;
        });
        this.addRenderableWidget(boundsWidget);
    }

    private void initStyleTab(int x, int y, int width) {
        // Color selection buttons (grid)
        int buttonsPerRow = 4;
        int buttonWidth = (width - PADDING * (buttonsPerRow - 1)) / buttonsPerRow;
        int buttonHeight = 24;

        for (int i = 0; i < ZONE_COLORS.length; i++) {
            final int colorIndex = i;
            int row = i / buttonsPerRow;
            int col = i % buttonsPerRow;
            int btnX = x + col * (buttonWidth + PADDING);
            int btnY = y + row * (buttonHeight + 4);

            EditorButton colorButton = EditorButton.builder("zone-color-" + colorIndex, ZONE_COLOR_NAMES[i])
                .style(EditorButton.Style.NORMAL)
                .size(EditorButton.Size.MEDIUM)
                .onClick(() -> {
                    this.selectedColorIndex = colorIndex;
                    playClickSound();
                })
                .build();

            this.addRenderableWidget(new EditorButtonWidget(colorButton, btnX, btnY, buttonWidth, buttonHeight));
        }
    }

    private void initOptionsTab(int x, int y, int width) {
        this.optionsTabX = x;
        this.optionsTabY = y;
        this.optionsTabWidth = width;
        buildOptionsButtons();
    }

    private void buildOptionsButtons() {
        if (currentTab != 3) {
            return;
        }

        removeOptionsButtons();

        int buttonWidth = optionsTabWidth / 2 - 10;

        // Allow building toggle
        EditorButton allowBuildingButton = EditorButton.builder("zone-allow-building",
                "Allow Building: " + (allowBuilding ? "YES" : "NO"))
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> {
                this.allowBuilding = !this.allowBuilding;
                buildOptionsButtons();
            })
            .build();
        this.allowBuildingButton = new EditorButtonWidget(allowBuildingButton, optionsTabX, optionsTabY,
            buttonWidth, FIELD_HEIGHT);
        this.addRenderableWidget(this.allowBuildingButton);

        // Is special toggle
        EditorButton specialButton = EditorButton.builder("zone-special",
                "Special Zone: " + (isSpecial ? "YES" : "NO"))
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> {
                this.isSpecial = !this.isSpecial;
                buildOptionsButtons();
            })
            .build();
        this.specialZoneButton = new EditorButtonWidget(specialButton, optionsTabX + buttonWidth + 20, optionsTabY,
            buttonWidth, FIELD_HEIGHT);
        this.addRenderableWidget(this.specialZoneButton);

        // Has teleport toggle
        EditorButton teleportableButton = EditorButton.builder("zone-teleportable",
                "Teleportable: " + (hasTeleport ? "YES" : "NO"))
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> {
                this.hasTeleport = !this.hasTeleport;
                if (!this.hasTeleport) {
                    this.portalOffset = null;
                }
                buildOptionsButtons();
            })
            .build();
        this.teleportableButton = new EditorButtonWidget(teleportableButton, optionsTabX, optionsTabY + 30,
            buttonWidth, FIELD_HEIGHT);
        this.addRenderableWidget(this.teleportableButton);

        // Priority field
        EditorButton priorityUp = EditorButton.builder("zone-priority-up", "Priority: " + priority + " [+]")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> {
                this.priority++;
                buildOptionsButtons();
            })
            .build();
        this.priorityUpButton = new EditorButtonWidget(priorityUp,
            optionsTabX + buttonWidth + 20, optionsTabY + 30, buttonWidth / 2, FIELD_HEIGHT);
        this.addRenderableWidget(this.priorityUpButton);

        EditorButton priorityDown = EditorButton.builder("zone-priority-down", "[-]")
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.MEDIUM)
            .onClick(() -> {
                this.priority = Math.max(0, priority - 1);
                buildOptionsButtons();
            })
            .build();
        this.priorityDownButton = new EditorButtonWidget(priorityDown,
            optionsTabX + buttonWidth + 20 + buttonWidth / 2 + 5, optionsTabY + 30, buttonWidth / 2 - 5, FIELD_HEIGHT);
        this.addRenderableWidget(this.priorityDownButton);
    }

    private void removeOptionsButtons() {
        if (allowBuildingButton != null) {
            removeWidget(allowBuildingButton);
            allowBuildingButton = null;
        }
        if (specialZoneButton != null) {
            removeWidget(specialZoneButton);
            specialZoneButton = null;
        }
        if (teleportableButton != null) {
            removeWidget(teleportableButton);
            teleportableButton = null;
        }
        if (priorityUpButton != null) {
            removeWidget(priorityUpButton);
            priorityUpButton = null;
        }
        if (priorityDownButton != null) {
            removeWidget(priorityDownButton);
            priorityDownButton = null;
        }
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        int leftX = centerX - EDITOR_WIDTH / 2;
        int topY = (this.height - EDITOR_HEIGHT) / 2;

        // Background panel
        graphics.fill(leftX, topY, leftX + EDITOR_WIDTH, topY + EDITOR_HEIGHT, COLOR_EDITOR_BG);

        // Border
        graphics.renderOutline(leftX, topY, EDITOR_WIDTH, EDITOR_HEIGHT, COLOR_EDITOR_BORDER);

        // Title
        String titleText = isNewZone
            ? Objects.requireNonNull(Component.translatable("zone.editor.creating").getString())
            : Objects.requireNonNull(Component.translatable("zone.editor.editing", displayName).getString());
        graphics.drawCenteredString(safeFont(), titleText, centerX, topY + 10, COLOR_TEXT_PRIMARY);

        // Tab indicator
        int tabWidth = (EDITOR_WIDTH - PADDING * 2) / TAB_NAMES.length - 2;
        int tabStartX = leftX + PADDING;
        int indicatorX = tabStartX + currentTab * (tabWidth + 2);
        int indicatorY = topY + 30 + TAB_HEIGHT;
        graphics.fill(indicatorX, indicatorY, indicatorX + tabWidth, indicatorY + 2, COLOR_TAB_ACTIVE);

        // Tab-specific content labels
        int contentX = leftX + PADDING;
        int contentY = topY + 60;

        switch (currentTab) {
            case 0 -> renderGeneralLabels(graphics, contentX, contentY);
            case 1 -> renderBoundsLabels(graphics, contentX, contentY);
            case 2 -> renderStyleLabels(graphics, contentX, contentY);
            case 3 -> renderOptionsLabels(graphics, contentX, contentY);
        }

        // Color preview (small square)
        int previewX = leftX + EDITOR_WIDTH - PADDING - 20;
        int previewY = topY + 10;
        graphics.fill(previewX, previewY, previewX + 16, previewY + 16, 0xFF000000 | ZONE_COLORS[selectedColorIndex]);
        graphics.renderOutline(previewX - 1, previewY - 1, 18, 18, COLOR_EDITOR_BORDER);

        // Summary at bottom
        int summaryY = topY + EDITOR_HEIGHT - 55;
        String colorName = ZONE_COLOR_NAMES[selectedColorIndex];
        int colorSpace = colorName.indexOf(' ');
        String summary = Objects.requireNonNull(String.format("%s | %s | %dx%dx%d",
            zoneId.isEmpty() ? "?" : zoneId,
            colorSpace > 0 ? colorName.substring(0, colorSpace) : colorName,
            bounds.width(), bounds.height(), bounds.length()
        ));
        graphics.drawCenteredString(safeFont(), summary, centerX, summaryY, COLOR_TEXT_MUTED);

        renderInputBackgrounds(graphics);
    }

    private void renderInputBackgrounds(GuiGraphics graphics) {
        EditBox zoneId = zoneIdField;
        if (zoneId != null) {
            AxiomRenderer.drawInputBackground(graphics, zoneId.getX(), zoneId.getY(), zoneId.getWidth(),
                zoneId.getHeight(), zoneId.isFocused());
        }
        EditBox displayName = displayNameField;
        if (displayName != null) {
            AxiomRenderer.drawInputBackground(graphics, displayName.getX(), displayName.getY(),
                displayName.getWidth(), displayName.getHeight(), displayName.isFocused());
        }
        EditBox hintMessage = hintMessageField;
        if (hintMessage != null) {
            AxiomRenderer.drawInputBackground(graphics, hintMessage.getX(), hintMessage.getY(),
                hintMessage.getWidth(), hintMessage.getHeight(), hintMessage.isFocused());
        }
    }

    private void renderGeneralLabels(GuiGraphics graphics, int x, int y) {
        graphics.drawString(safeFont(), "Zone ID:", x, y + 5, COLOR_TEXT_MUTED, false);
        graphics.drawString(safeFont(), "Display:", x, y + 35, COLOR_TEXT_MUTED, false);
        graphics.drawString(safeFont(), "Hint:", x, y + 65, COLOR_TEXT_MUTED, false);
        graphics.drawString(safeFont(), "Spawn Offset:", x, y + 105, COLOR_TEXT_MUTED, false);

        // Show current spawn offset
        BlockPos spawn = spawnOffset;
        String spawnText = spawn != null
            ? String.format("(%d, %d, %d)", spawn.getX(), spawn.getY(), spawn.getZ())
            : "(not set)";
        graphics.drawString(safeFont(), spawnText, x, y + 125, COLOR_TEXT_MUTED, false);

        graphics.drawString(safeFont(), "Portal Offset:", x, y + 155, COLOR_TEXT_MUTED, false);
        BlockPos portal = portalOffset;
        String portalText = portal != null
            ? String.format("(%d, %d, %d)", portal.getX(), portal.getY(), portal.getZ())
            : "(not set)";
        graphics.drawString(safeFont(), portalText, x, y + 175, COLOR_TEXT_MUTED, false);
    }

    private void renderBoundsLabels(GuiGraphics graphics, int x, int y) {
        graphics.drawString(safeFont(), "Configure zone boundaries:", x, y - 10, COLOR_TEXT_PRIMARY, false);

        // Instructions
        graphics.drawString(safeFont(), "Use the fields below to set min/max coordinates.", x, y + 150, COLOR_TEXT_MUTED, false);
        graphics.drawString(safeFont(), "Tip: use markers to note corner positions.", x, y + 165, COLOR_TEXT_MUTED, false);
    }

    private void renderStyleLabels(GuiGraphics graphics, int x, int y) {
        graphics.drawString(safeFont(), "Select zone color:", x, y - 15, COLOR_TEXT_PRIMARY, false);

        // Show selected color preview
        int previewY = y + 100;
        graphics.drawString(safeFont(), "Selected:", x, previewY, COLOR_TEXT_MUTED, false);
        graphics.fill(x + 60, previewY - 2, x + 90, previewY + 12, 0xFF000000 | ZONE_COLORS[selectedColorIndex]);
        graphics.drawString(safeFont(), ZONE_COLOR_NAMES[selectedColorIndex], x + 100, previewY, COLOR_TEXT_PRIMARY, false);
    }

    private void renderOptionsLabels(GuiGraphics graphics, int x, int y) {
        graphics.drawString(safeFont(), "Zone behavior options:", x, y - 15, COLOR_TEXT_PRIMARY, false);
    }

    // ========================================================================
    // Actions
    // ========================================================================

    private void useCurrentPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            BlockPos playerPos = Objects.requireNonNull(mc.player.blockPosition());
            BlockPos offset = toHubOffset(playerPos);
            this.spawnOffset = offset;
            showError(Component.translatable("zone.editor.spawn_set", offset.toShortString()).getString());
        }
    }

    private void clearSpawnOffset() {
        this.spawnOffset = null;
        showError(Component.translatable("zone.editor.spawn_cleared").getString());
    }

    private void useCurrentPortalPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            BlockPos playerPos = Objects.requireNonNull(mc.player.blockPosition());
            BlockPos offset = toHubOffset(playerPos);
            this.portalOffset = offset;
            showError(Component.translatable("zone.editor.portal_set", offset.toShortString()).getString());
        }
    }

    private void clearPortalOffset() {
        this.portalOffset = null;
        showError(Component.translatable("zone.editor.portal_cleared").getString());
    }

    private BlockPos toHubOffset(@Nonnull BlockPos worldPos) {
        BlockPos hubOrigin = NexusDimensionManager.getHubOrigin();
        return new BlockPos(
            worldPos.getX() - hubOrigin.getX(),
            worldPos.getY() - hubOrigin.getY(),
            worldPos.getZ() - hubOrigin.getZ()
        );
    }

    private void saveZone() {
        // Validate fields
        if (!ZoneNaming.isValidZoneId(Objects.requireNonNull(zoneId))) {
            showError(Component.translatable("zone.editor.error.invalid_id").getString());
            return;
        }

        if (!ZoneNaming.isValidDisplayName(Objects.requireNonNull(displayName))) {
            showError(Component.translatable("zone.editor.error.invalid_name").getString());
            return;
        }

        // Validate bounds
        if (!bounds.isFullyValid()) {
            if (!bounds.isWithinWorldLimits()) {
                showError(Component.translatable("zone.editor.error.bounds_limits").getString());
            } else if (!bounds.isSizeReasonable()) {
                showError(Component.translatable("zone.editor.error.bounds_size",
                    ZoneBounds.MAX_ZONE_SIZE).getString());
            } else {
                showError(Component.translatable("zone.editor.error.bounds_invalid").getString());
            }
            return;
        }

        // Get cue sound based on color
        SoundEvent cueSound = getCueSoundForColor(selectedColorIndex);

        // Build zone definition
        ZoneDefinition.Builder builder = ZoneDefinition.builder(Objects.requireNonNull(zoneId))
            .displayName(Objects.requireNonNull(displayName))
            .bounds(Objects.requireNonNull(bounds))
            .spawnOffset(spawnOffset)
            .color(ZONE_COLORS[selectedColorIndex])
            .cueSound(Objects.requireNonNull(cueSound))
            .hintMessage(Objects.requireNonNull(hintMessage))
            .allowBuilding(allowBuilding)
            .isSpecial(isSpecial)
            .hasTeleport(hasTeleport)
            .portalOffset(portalOffset)
            .priority(priority);

        // Preserve UUID if editing
        if (existingZone != null) {
            builder.id(existingZone.id());
        }

        ZoneDefinition definition = builder.build();

        // Send to server
        SaveZonePayload payload = new SaveZonePayload(
            definition,
            markerPosition,
            existingZone != null ? existingZone.id() : null,
            markerPosition != null
        );
        PacketDistributor.sendToServer(payload);

        showError(Component.translatable("zone.editor.saved").getString());
        this.onClose();
    }

    private void handleDeleteClick() {
        if (existingZone == null) return;

        if (!deleteConfirmPending) {
            // First click - show confirmation
            deleteConfirmPending = true;
            this.rebuildWidgets();
            showError(Component.translatable("zone.message.delete_confirm").getString());
        } else {
            // Second click - actually delete
            deleteZone();
        }
    }

    private void deleteZone() {
        if (existingZone == null) return;

        // Send delete request to server (removeMarkers = true)
        DeleteZonePayload payload = new DeleteZonePayload(existingZone.id(), true);
        PacketDistributor.sendToServer(payload);

        showError(Component.translatable("zone.editor.deleted").getString());
        this.onClose();
    }

    private SoundEvent getCueSoundForColor(int colorIndex) {
        // Map colors to sounds per Bibbia Estetica
        return switch (colorIndex) {
            case 0 -> SoundEvents.NOTE_BLOCK_CHIME.value(); // HUB
            case 1 -> SoundEvents.NOTE_BLOCK_PLING.value(); // COMBAT
            case 2 -> SoundEvents.NOTE_BLOCK_BELL.value(); // ARENA
            case 3 -> SoundEvents.NOTE_BLOCK_BIT.value(); // UI
            case 4 -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value(); // TELEMETRY
            case 5 -> SoundEvents.NOTE_BLOCK_HARP.value(); // SHOWCASE
            case 6 -> SoundEvents.NOTE_BLOCK_BASS.value(); // INTEGRATION
            case 7 -> SoundEvents.NOTE_BLOCK_COW_BELL.value(); // SANDBOX
            case 8 -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(); // MECHANICS
            case 9 -> SoundEvents.NOTE_BLOCK_CHIME.value(); // OVERVIEW
            default -> SoundEvents.NOTE_BLOCK_HAT.value(); // SPECIAL
        };
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Get font with null safety.
     */
    @Nonnull
    private Font safeFont() {
        return Objects.requireNonNull(font, "font");
    }
}
