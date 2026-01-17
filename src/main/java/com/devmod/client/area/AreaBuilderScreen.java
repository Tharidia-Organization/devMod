package com.devmod.client.area;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

import com.devmod.area.aesthetic.AreaBuilderGuiConstants;
import com.devmod.area.aesthetic.AreaBuilderNaming;
import com.devmod.area.builder.AreaPalettePresets;
import com.devmod.area.builder.BiomeRegistry;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaGenerationType;
import com.devmod.area.data.AreaOptions;
import com.devmod.area.data.AreaPalette;
import com.devmod.area.data.AreaShape;
import com.devmod.area.data.BiomeGenerationConfig;
import com.devmod.area.network.AreaPreviewPayload;
import com.devmod.area.network.BuildAreaPayload;
import com.devmod.area.network.BuildStatusPayload;
import com.devmod.area.network.DeleteTemplatePayload;
import com.devmod.area.network.LoadTemplatePayload;
import com.devmod.area.network.PauseBuildPayload;
import com.devmod.area.network.RequestTemplateListPayload;
import com.devmod.area.network.RequestZoneListPayload;
import com.devmod.area.network.ResumeBuildPayload;
import com.devmod.area.network.SaveAreaPayload;
import com.devmod.area.network.SaveAreaResultPayload;
import com.devmod.area.network.TemplateDataPayload;
import com.devmod.area.network.TemplateListPayload;
import com.devmod.area.network.ZoneListPayload;
import com.devmod.area.data.GridSettings;
import com.devmod.client.area.widget.BiomeConfigWidget;
import com.devmod.client.area.widget.BiomeSelectorWidget;
import com.devmod.client.area.widget.DimensionsWidget;
import com.devmod.client.area.widget.GridSettingsWidget;
import com.devmod.client.area.widget.OptionsWidget;
import com.devmod.client.area.widget.PaletteEditorWidget;
import com.devmod.client.area.widget.ShapeConfigWidget;
import com.devmod.client.area.widget.TemplateSelectorWidget;
import com.devmod.client.area.widget.ZoneSelectorWidget;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.BaseDevModScreen;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.components.EditorButtonWidget;
import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Area Builder screen - the main interface for configuring and building areas.
 * Contains 5 tabs: Shape, Dimensions, Materials, Biome, Options.
 */
@OnlyIn(Dist.CLIENT)
public class AreaBuilderScreen extends BaseDevModScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaBuilderScreen.class);
    private static final int SUMMARY_GAP = 4;

    @Nullable
    private final AreaDefinition existingArea;
    @Nullable
    private final BlockPos editorPosition;
    private final boolean isMainHub;

    // Area ID - generated on creation, preserved on edit
    private UUID areaId;
    private int currentRevision = 0;
    @Nullable
    private UUID pendingSaveRequestId = null;
    private boolean awaitingSaveAck = false;
    // CLI-01 fix: Track save request timestamp for timeout feedback
    private long saveRequestTimestamp = 0;
    private static final long SAVE_REQUEST_TIMEOUT_MS = 10_000; // 10 seconds
    private boolean pendingBuildAfterSave = false;
    private boolean dirtySinceSaveRequest = false;
    private boolean hasServerArea = false;
    private boolean areaSaved = false;
    private boolean useBiomeGeneration = false;

    // Current configuration state
    private String areaName;
    private AreaShape selectedShape = AreaShape.RECTANGULAR;
    private AreaDimensions dimensions = AreaDimensions.DEFAULT;
    private String selectedPreset = "default";
    private AreaOptions options = AreaOptions.DEFAULT;
    private ResourceLocation selectedBiome = ResourceLocation.withDefaultNamespace("plains");
    private BiomeGenerationConfig biomeConfig = BiomeGenerationConfig.DEFAULT;
    @Nullable
    private String linkedZoneId = null;

    // Tab management
    private int currentTab = 0;
    private static final String[] TAB_NAMES = {"shape", "dimensions", "materials", "biome", "options"};

    // Widgets (created on demand per tab)
    @Nullable private EditBox nameField;
    @Nullable private ShapeConfigWidget shapeWidget;
    @Nullable private GridSettingsWidget gridSettingsWidget;
    @Nullable private PaletteEditorWidget paletteWidget;
    @Nullable private BiomeSelectorWidget biomeSelector;
    @Nullable private BiomeConfigWidget biomeConfigWidget;
    @Nullable private OptionsWidget optionsWidget;
    @Nullable private ZoneSelectorWidget zoneSelectorWidget;

    // Grid settings for area alignment
    private GridSettings gridSettings = GridSettings.DISABLED;

    @Nullable private EditorButtonWidget previewButton;
    @Nullable private EditorButtonWidget buildButton;
    @Nullable private EditorButtonWidget saveButton;
    @Nullable private EditorButtonWidget pauseButton;
    @Nullable private EditorButtonWidget resumeButton;
    @Nullable private String buildButtonLabelKey;
    private int actionButtonY;
    private int actionButtonWidth;
    private int actionButtonSpacing;
    private int actionButtonsStartX;

    // CRIT-06 fix: Track build state for pause/resume UI
    private boolean buildActive = false;
    private boolean buildPaused = false;

    // Template UI
    @Nullable private TemplateSelectorWidget templateSelector;
    @Nullable private SaveTemplateDialog saveTemplateDialog;
    private boolean showTemplateSelector = false;

    // Template cache with TTL
    private static final long TEMPLATE_CACHE_TTL_MS = 30_000L; // 30 seconds
    @Nullable private static List<TemplateListPayload.TemplateSummary> cachedTemplates;
    private static long templateCacheTime;

    public AreaBuilderScreen(
            @Nullable AreaDefinition existingArea,
            @Nullable BlockPos editorPosition,
            boolean isMainHub
    ) {
        super(Component.translatable("area.builder.title"), "area_builder");
        this.existingArea = existingArea;
        this.editorPosition = editorPosition;
        this.isMainHub = isMainHub;

        // Use existing ID or generate new one
        this.areaId = existingArea != null ? existingArea.id() : UUID.randomUUID();
        this.areaSaved = existingArea != null;
        this.currentRevision = existingArea != null ? existingArea.revision() : 0;
        this.hasServerArea = existingArea != null;

        // Load existing values if editing
        if (existingArea != null) {
            this.areaName = existingArea.name();
            this.selectedShape = existingArea.shape();
            this.dimensions = existingArea.dimensions();
            this.selectedPreset = existingArea.palette().presetId() != null
                ? existingArea.palette().presetId() : "default";
            this.options = existingArea.options();
            this.useBiomeGeneration = existingArea.generationType() == AreaGenerationType.BIOME;
            this.linkedZoneId = existingArea.linkedZoneId();
            BiomeGenerationConfig existingBiomeConfig = existingArea.biomeConfig();
            if (existingBiomeConfig != null) {
                this.biomeConfig = existingBiomeConfig;
                this.selectedBiome = existingBiomeConfig.biomeId();
            } else if (this.useBiomeGeneration) {
                this.biomeConfig = BiomeGenerationConfig.DEFAULT;
                this.selectedBiome = this.biomeConfig.biomeId();
            }
        } else {
            this.areaName = AreaBuilderNaming.DEFAULT_AREA_NAME;
        }
    }

    private Layout layout() {
        int fontHeight = this.font != null ? this.font.lineHeight : 9;
        return Layout.forScreen(this.width, this.height, fontHeight);
    }

    @Override
    protected void initContent() {
        Layout layout = layout();
        int tabWidth = AreaBuilderGuiConstants.TAB_WIDTH;
        int tabHeight = AreaBuilderGuiConstants.TAB_HEIGHT;
        int tabStartX = layout.tabStartX;
        int tabY = layout.tabY;

        EditBox nameBox = new EditBox(Objects.requireNonNull(this.font), layout.nameFieldX, layout.nameFieldY,
            layout.nameFieldWidth, AreaBuilderGuiConstants.FIELD_HEIGHT,
            Objects.requireNonNull(Component.translatable("area.builder.name_label")));
        nameBox.setValue(areaName != null ? areaName : "");
        nameBox.setMaxLength(AreaBuilderNaming.MAX_DISPLAY_NAME_LENGTH);
        nameBox.setHint(Objects.requireNonNull(Component.translatable("area.builder.name_hint")));
        nameBox.setResponder(value -> {
            this.areaName = value;
            markDirty();
        });
        nameBox.setBordered(false);
        nameBox.setTextColor(AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);
        nameBox.setTextColorUneditable(AreaBuilderGuiConstants.COLOR_TEXT_MUTED);
        this.nameField = nameBox;
        this.addRenderableWidget(nameBox);

        // Tab buttons with icons
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int tabIndex = i;

            EditorButton tabButton = EditorButton.builder("area-builder-tab-" + TAB_NAMES[i],
                    Component.translatable("area.builder.tab." + TAB_NAMES[i]).getString())
                .style(EditorButton.Style.GHOST)
                .size(EditorButton.Size.MEDIUM)
                .onClick(() -> selectTab(tabIndex))
                .build();

            this.addRenderableWidget(new EditorButtonWidget(tabButton,
                tabStartX + i * (tabWidth + AreaBuilderGuiConstants.TAB_SPACING), tabY, tabWidth, tabHeight));
        }

        // Initialize current tab content
        initTabContent();

        // Bottom buttons
        actionButtonY = layout.buttonY;
        actionButtonWidth = AreaBuilderGuiConstants.BUTTON_WIDTH;
        actionButtonSpacing = AreaBuilderGuiConstants.BUTTON_SPACING;
        int totalButtonsWidth = actionButtonWidth * 3 + actionButtonSpacing * 2;
        actionButtonsStartX = (this.width - totalButtonsWidth) / 2;

        // Preview button
        EditorButton preview = EditorButton.builder("area-builder-preview",
                Component.translatable("area.builder.preview").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::requestPreview)
            .build();
        previewButton = new EditorButtonWidget(preview, actionButtonsStartX, actionButtonY,
            actionButtonWidth, AreaBuilderGuiConstants.BUTTON_HEIGHT);
        this.addRenderableWidget(previewButton);

        // Save button
        EditorButton save = EditorButton.builder("area-builder-save",
                Component.translatable("area.builder.save").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::saveArea)
            .build();
        saveButton = new EditorButtonWidget(save,
            actionButtonsStartX + (actionButtonWidth + actionButtonSpacing) * 2, actionButtonY, actionButtonWidth,
            AreaBuilderGuiConstants.BUTTON_HEIGHT);
        this.addRenderableWidget(saveButton);

        // CRIT-06 fix: Pause button (initially hidden)
        EditorButton pause = EditorButton.builder("area-builder-pause",
                Component.translatable("area.builder.pause").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::pauseBuild)
            .build();
        EditorButtonWidget pauseBtn = new EditorButtonWidget(pause,
            actionButtonsStartX + (actionButtonWidth + actionButtonSpacing) * 3, actionButtonY, actionButtonWidth,
            AreaBuilderGuiConstants.BUTTON_HEIGHT);
        pauseBtn.visible = false;
        this.addRenderableWidget(pauseBtn);
        pauseButton = pauseBtn;

        // CRIT-06 fix: Resume button (initially hidden)
        EditorButton resume = EditorButton.builder("area-builder-resume",
                Component.translatable("area.builder.resume").getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::resumeBuild)
            .build();
        EditorButtonWidget resumeBtn = new EditorButtonWidget(resume,
            actionButtonsStartX + (actionButtonWidth + actionButtonSpacing) * 3, actionButtonY, actionButtonWidth,
            AreaBuilderGuiConstants.BUTTON_HEIGHT);
        resumeBtn.visible = false;
        this.addRenderableWidget(resumeBtn);
        resumeButton = resumeBtn;

        updateActionButtons();

        // Template buttons (left side of action bar)
        int templateBtnWidth = 70;
        int templateBtnSpacing = 4;
        int templateBtnX = actionButtonsStartX - templateBtnWidth * 2 - templateBtnSpacing - 20;

        EditorButton loadTemplate = EditorButton.builder("area-builder-load-template",
                Component.translatable("area.builder.load_template").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onClick(this::toggleTemplateSelector)
            .build();
        this.addRenderableWidget(new EditorButtonWidget(loadTemplate, templateBtnX, actionButtonY,
            templateBtnWidth, AreaBuilderGuiConstants.BUTTON_HEIGHT));

        EditorButton saveTemplate = EditorButton.builder("area-builder-save-template",
                Component.translatable("area.builder.save_template").getString())
            .style(EditorButton.Style.GHOST)
            .size(EditorButton.Size.SMALL)
            .onClick(this::showSaveTemplateDialog)
            .build();
        this.addRenderableWidget(new EditorButtonWidget(saveTemplate,
            templateBtnX + templateBtnWidth + templateBtnSpacing, actionButtonY,
            templateBtnWidth, AreaBuilderGuiConstants.BUTTON_HEIGHT));

        // Request zone list from server for zone selector
        PacketDistributor.sendToServer(new RequestZoneListPayload());
    }

    private void selectTab(int tabIndex) {
        this.currentTab = tabIndex;
        this.rebuildWidgets();
    }

    private void markDirty() {
        if (awaitingSaveAck) {
            dirtySinceSaveRequest = true;
        }
        if (areaSaved) {
            areaSaved = false;
            updateActionButtons();
        }
    }

    private void updateActionButtons() {
        refreshBuildButton();
        if (saveButton != null) {
            saveButton.getButton().setEnabled(!areaSaved);
        }
    }

    private void refreshBuildButton() {
        String labelKey = areaSaved ? "area.builder.build" : "area.builder.save_build";
        if (buildButton != null && labelKey.equals(buildButtonLabelKey)) {
            return;
        }
        buildButtonLabelKey = labelKey;
        if (buildButton != null) {
            removeWidget(buildButton);
        }
        EditorButton build = EditorButton.builder("area-builder-build",
                Component.translatable(labelKey).getString())
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::buildArea)
            .build();
        buildButton = new EditorButtonWidget(build,
            actionButtonsStartX + actionButtonWidth + actionButtonSpacing, actionButtonY, actionButtonWidth,
            AreaBuilderGuiConstants.BUTTON_HEIGHT);
        addRenderableWidget(buildButton);
    }

    private void initTabContent() {
        Layout layout = layout();
        int contentX = layout.contentX;
        int contentY = layout.contentY;
        int contentWidth = layout.contentWidth;
        int contentHeight = layout.contentHeight;

        // Clear previous widgets
        shapeWidget = null;
        paletteWidget = null;
        biomeSelector = null;
        biomeConfigWidget = null;
        optionsWidget = null;
        zoneSelectorWidget = null;
        gridSettingsWidget = null;

        switch (currentTab) {
            case 0 -> initShapeTab(contentX, contentY, contentWidth, contentHeight);
            case 1 -> initDimensionsTab(contentX, contentY, contentWidth, contentHeight);
            case 2 -> initMaterialsTab(contentX, contentY, contentWidth, contentHeight);
            case 3 -> initBiomeTab(contentX, contentY, contentWidth, contentHeight);
            case 4 -> initOptionsTab(contentX, contentY, contentWidth, contentHeight);
        }
    }

    private void initShapeTab(int x, int y, int width, int height) {
        shapeWidget = new ShapeConfigWidget(
            x, y, width, height,
            selectedShape,
            shape -> {
                this.selectedShape = shape;
                markDirty();
            }
        );
        this.addRenderableWidget(shapeWidget);
    }

    private void initDimensionsTab(int x, int y, int width, int height) {
        // Split the area: left for dimensions, right for grid settings
        int dimensionsWidth = width / 2 - 10;
        int gridWidth = width / 2 - 10;

        DimensionsWidget dimWidget = new DimensionsWidget(
            x, y, dimensionsWidth, height,
            dimensions,
            dims -> {
                this.dimensions = dims;
                markDirty();
            }
        );
        dimWidget.setGridSettings(gridSettings);
        this.addRenderableWidget(dimWidget);

        gridSettingsWidget = new GridSettingsWidget(
            x + dimensionsWidth + 20, y, gridWidth, height,
            gridSettings,
            settings -> {
                this.gridSettings = settings;
                // Update dimensions widget with new grid settings
                dimWidget.setGridSettings(settings);
                markDirty();
            }
        );
        this.addRenderableWidget(gridSettingsWidget);
    }

    private void initMaterialsTab(int x, int y, int width, int height) {
        paletteWidget = new PaletteEditorWidget(
            x, y, width, height,
            selectedPreset,
            preset -> {
                this.selectedPreset = preset;
                markDirty();
            }
        );
        this.addRenderableWidget(paletteWidget);
    }

    private void initBiomeTab(int x, int y, int width, int height) {
        // Split the area: left for selector, right for config
        int selectorWidth = width / 2 - 10;

        biomeSelector = new BiomeSelectorWidget(
            x, y, selectorWidth, height,
            selectedBiome,
            biome -> {
                ResourceLocation validBiome = Objects.requireNonNull(biome);
                this.selectedBiome = validBiome;
                this.biomeConfig = biomeConfig.withBiome(validBiome);
                if (biomeConfigWidget != null) {
                    biomeConfigWidget.setBiome(validBiome);
                }
                markDirty();
            }
        );
        this.addRenderableWidget(biomeSelector);

        biomeConfigWidget = new BiomeConfigWidget(
            x + selectorWidth + 20, y, selectorWidth, height,
            biomeConfig,
            useBiomeGeneration,
            enabled -> {
                this.useBiomeGeneration = enabled;
                markDirty();
            },
            config -> {
                this.biomeConfig = config;
                markDirty();
            }
        );
        this.addRenderableWidget(biomeConfigWidget);
    }

    private void initOptionsTab(int x, int y, int width, int height) {
        // Split the area: left for options, right for zone selector
        int optionsWidth = width / 2 - 10;
        int zoneSelectorWidth = width / 2 - 10;

        optionsWidget = new OptionsWidget(
            x, y, optionsWidth, height,
            options,
            opts -> {
                this.options = opts;
                markDirty();
            }
        );
        this.addRenderableWidget(optionsWidget);

        zoneSelectorWidget = new ZoneSelectorWidget(
            x + optionsWidth + 20, y, zoneSelectorWidth, height,
            linkedZoneId,
            zoneId -> {
                this.linkedZoneId = zoneId;
                markDirty();
            }
        );
        this.addRenderableWidget(zoneSelectorWidget);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // CLI-01 fix: Check for save request timeout
        if (awaitingSaveAck && saveRequestTimestamp > 0) {
            long elapsed = System.currentTimeMillis() - saveRequestTimestamp;
            if (elapsed > SAVE_REQUEST_TIMEOUT_MS) {
                // Timeout - reset state and show error
                awaitingSaveAck = false;
                pendingSaveRequestId = null;
                saveRequestTimestamp = 0;
                pendingBuildAfterSave = false;
                showError(Component.translatable("area.builder.error.save_timeout").getString());
                LOGGER.warn("[AreaBuilder] Save request timed out after {}ms", elapsed);
            }
        }

        Layout layout = layout();
        int centerX = this.width / 2;

        // Title
        Component title = existingArea != null
            ? Component.translatable("area.builder.editing", areaName)
            : Component.translatable("area.builder.creating");
        if (!areaSaved) {
            title = title.copy().append(Objects.requireNonNull(Component.translatable("area.builder.unsaved")));
        }
        graphics.drawCenteredString(
            Objects.requireNonNull(this.font),
            Objects.requireNonNull(title),
            centerX, layout.titleY,
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Name label
        graphics.drawString(
            Objects.requireNonNull(this.font),
            Objects.requireNonNull(Component.translatable("area.builder.name_label")),
            layout.contentX, layout.nameLabelY,
            AreaBuilderGuiConstants.COLOR_TEXT_SECONDARY
        );

        // Highlight current tab
        int tabWidth = AreaBuilderGuiConstants.TAB_WIDTH;
        int tabHeight = AreaBuilderGuiConstants.TAB_HEIGHT;
        int activeTabX = layout.tabStartX + currentTab * (tabWidth + AreaBuilderGuiConstants.TAB_SPACING);
        graphics.fill(activeTabX, layout.tabY + tabHeight - 2, activeTabX + tabWidth, layout.tabY + tabHeight,
            AreaBuilderGuiConstants.COLOR_TAB_ACTIVE);

        // Summary bar at bottom
        var fontRef = Objects.requireNonNull(this.font);
        int summaryY = layout.actionBarY - fontRef.lineHeight - SUMMARY_GAP;
        Component shapeName = Component.translatable("area.shape." + selectedShape.getSerializedName());
        Component presetName = Component.translatable("area.preset." + selectedPreset);
        Component biomeName = useBiomeGeneration
            ? Component.literal(BiomeRegistry.getBiomeDisplayName(Objects.requireNonNull(selectedBiome)))
            : Component.translatable("area.biome.disabled");
        Component summary = Component.translatable("area.builder.summary",
            shapeName,
            dimensions.width(), dimensions.length(), dimensions.height(),
            presetName,
            biomeName
        );
        graphics.drawCenteredString(fontRef, Objects.requireNonNull(summary), centerX, summaryY, AreaBuilderGuiConstants.COLOR_TEXT_MUTED);

        renderInputBackgrounds(graphics);

        // Render template selector overlay if visible
        TemplateSelectorWidget selector = templateSelector;
        if (showTemplateSelector && selector != null) {
            // Draw semi-transparent background
            graphics.fill(0, 0, this.width, this.height, DesignTokens.Background.OVERLAY());
            selector.render(graphics, mouseX, mouseY, partialTick);
        }

        // Render save template dialog if visible
        SaveTemplateDialog dialog = saveTemplateDialog;
        if (dialog != null && dialog.isVisible()) {
            dialog.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
        }

        // UX-01 fix: Render saving overlay with spinner when awaiting save acknowledgment
        if (awaitingSaveAck) {
            renderSavingOverlay(graphics);
        }
    }

    /**
     * UX-01 fix: Renders a semi-transparent overlay with spinner animation during save.
     */
    private void renderSavingOverlay(GuiGraphics graphics) {
        // Semi-transparent dark overlay
        graphics.fill(0, 0, this.width, this.height, AreaBuilderGuiConstants.COLOR_OVERLAY_DARK);

        // Centered "Saving..." text with animated dots
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Animated dots based on time (cycles every 1.5 seconds)
        long time = System.currentTimeMillis();
        int dotCount = (int) ((time / 500) % 4); // 0, 1, 2, or 3 dots
        String dots = ".".repeat(dotCount);
        String savingText = Component.translatable("area.builder.saving").getString() + dots;

        // Draw saving text
        graphics.drawCenteredString(
            Objects.requireNonNull(this.font),
            savingText,
            centerX, centerY,
            AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY
        );

        // Draw elapsed time hint after 2 seconds
        if (saveRequestTimestamp > 0) {
            long elapsed = System.currentTimeMillis() - saveRequestTimestamp;
            if (elapsed > 2000) {
                String elapsedText = Objects.requireNonNull(String.format("(%.1fs)", elapsed / 1000.0));
                graphics.drawCenteredString(
                    Objects.requireNonNull(this.font),
                    elapsedText,
                    centerX, centerY + 15,
                    AreaBuilderGuiConstants.COLOR_TEXT_MUTED
                );
            }
        }
    }

    private void renderInputBackgrounds(GuiGraphics graphics) {
        EditBox nameBox = nameField;
        if (nameBox != null) {
            AxiomRenderer.drawInputBackground(graphics, nameBox.getX(), nameBox.getY(), nameBox.getWidth(),
                nameBox.getHeight(), nameBox.isFocused());
        }
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(Objects.requireNonNull(graphics), mouseX, mouseY, partialTick);
        Layout layout = layout();
        graphics.fill(0, 0, this.width, this.height, AreaBuilderGuiConstants.COLOR_BACKGROUND);
        graphics.fill(0, layout.actionBarY, this.width, layout.actionBarY + AreaBuilderGuiConstants.ACTION_BAR_HEIGHT,
            AreaBuilderGuiConstants.COLOR_PANEL);
        graphics.fill(0, layout.actionBarY, this.width, layout.actionBarY + 1, AreaBuilderGuiConstants.COLOR_BORDER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle save template dialog first if visible
        SaveTemplateDialog dialog = saveTemplateDialog;
        if (dialog != null && dialog.isVisible()) {
            if (dialog.mouseClicked(mouseX, mouseY, this.width, this.height)) {
                return true;
            }
        }

        // Handle template selector if visible
        if (showTemplateSelector && templateSelector != null) {
            if (templateSelector.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            // Click outside closes selector
            showTemplateSelector = false;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle save template dialog
        SaveTemplateDialog dialog = saveTemplateDialog;
        if (dialog != null && dialog.isVisible()) {
            if (dialog.keyPressed(keyCode)) {
                return true;
            }
        }

        // Handle template selector - ESC to close
        if (showTemplateSelector) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                showTemplateSelector = false;
                return true;
            }
            if (templateSelector != null && templateSelector.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        // UX-06 fix: Undo/Redo hotkeys for snapshot management
        boolean ctrlHeld = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_Z) {
            if (shiftHeld) {
                // Ctrl+Shift+Z = Redo (not implemented yet - would need redo stack)
                LOGGER.debug("[AreaBuilder] Redo hotkey pressed (not yet implemented)");
            } else {
                // Ctrl+Z = Undo (restore last snapshot)
                requestUndoViaSnapshot();
            }
            return true;
        }

        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_Y) {
            // Ctrl+Y = Redo (not implemented yet - would need redo stack)
            LOGGER.debug("[AreaBuilder] Redo hotkey pressed (not yet implemented)");
            return true;
        }

        // Ctrl+S = Save
        if (ctrlHeld && keyCode == GLFW.GLFW_KEY_S) {
            saveArea();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * UX-06 fix: Requests undo by restoring the most recent snapshot.
     * Sends a request to the server to get the latest snapshot for this area.
     */
    private void requestUndoViaSnapshot() {
        if (!areaSaved) {
            showError(Component.translatable("area.builder.error.save_first_for_undo").getString());
            return;
        }
        LOGGER.info("[AreaBuilder] Undo hotkey pressed - requesting latest snapshot for area {}", areaId);
        // Request snapshot list - the server will respond with SnapshotListPayload
        // The client hooks will handle showing the restore confirmation or auto-restoring
        PacketDistributor.sendToServer(new com.devmod.area.network.RequestSnapshotListPayload(areaId));
        showStatus(Component.translatable("area.builder.undo_requested").getString(), AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        // Handle save template dialog
        SaveTemplateDialog dialog = saveTemplateDialog;
        if (dialog != null && dialog.isVisible()) {
            if (dialog.charTyped(chr, modifiers)) {
                return true;
            }
        }

        // Handle template selector
        if (showTemplateSelector && templateSelector != null) {
            if (templateSelector.charTyped(chr, modifiers)) {
                return true;
            }
        }

        return super.charTyped(chr, modifiers);
    }

    private void requestPreview() {
        LOGGER.info("[AreaBuilder] requestPreview() called");
        // Get current player position as center if no existing area
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            LOGGER.warn("[AreaBuilder] requestPreview: player is null!");
            return;
        }
        var player = Objects.requireNonNull(mc.player);

        BlockPos center = existingArea != null
            ? existingArea.centerPosition()
            : player.blockPosition();

        // Check if biome generation is enabled
        boolean useBiome = useBiomeGeneration && biomeConfig != null;

        LOGGER.info("[AreaBuilder] Preview center: {}, dims: {}x{}x{}, shape: {}",
            center, dimensions.width(), dimensions.length(), dimensions.height(), selectedShape);
        LOGGER.info("[AreaBuilder] useBiome={}, biomeId={}, terrainStyle={}, seed={}",
            useBiome,
            useBiome ? biomeConfig.biomeId() : "N/A",
            useBiome ? biomeConfig.terrainStyle() : "N/A",
            useBiome ? biomeConfig.getEffectiveSeed() : 0);

        // Create client-side preview with biome info if enabled
        AreaPreviewPayload preview = AreaPreviewPayload.boundingBox(
            center,
            dimensions.width(),
            dimensions.length(),
            dimensions.height(),
            selectedShape.getSerializedName(),
            useBiome,
            useBiome ? biomeConfig.biomeId().toString() : "",
            useBiome ? biomeConfig.terrainStyle().getSerializedName().toUpperCase(Locale.ROOT) : "NATURAL",
            useBiome ? biomeConfig.getEffectiveSeed() : 0L
        );

        // Store for rendering
        AreaPreviewRenderer.INSTANCE.setPreview(preview);
        LOGGER.info("[AreaBuilder] Preview set with {} outline blocks", preview.outlineBlocks().size());

        // Close the screen so the player can see the preview in the world
        this.onClose();
    }

    private void buildArea() {
        LOGGER.info("[AreaBuilder] buildArea() called, areaSaved={}, areaId={}", areaSaved, areaId);
        // Must save first if not saved
        if (!areaSaved) {
            LOGGER.info("[AreaBuilder] Area not saved yet, calling saveAreaInternal(true)");
            // Save, then build
            saveAreaInternal(true);
            return;
        }
        if (awaitingSaveAck) {
            LOGGER.info("[AreaBuilder] Save in progress; build deferred until ack");
            pendingBuildAfterSave = true;
            return;
        }

        // Send build request to server
        LOGGER.info("[AreaBuilder] Sending BuildAreaPayload for areaId={}", areaId);
        BuildAreaPayload payload = Objects.requireNonNull(BuildAreaPayload.withClear(areaId));
        PacketDistributor.sendToServer(payload);

        // Close screen so player can see the building result
        this.onClose();
    }

    private void saveArea() {
        LOGGER.info("[AreaBuilder] saveArea() called");
        saveAreaInternal(false);
    }

    private void saveAreaInternal(boolean buildAfterSave) {
        LOGGER.info("[AreaBuilder] saveAreaInternal(buildAfterSave={})", buildAfterSave);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            LOGGER.warn("[AreaBuilder] saveAreaInternal: player is null!");
            return;
        }
        var player = Objects.requireNonNull(mc.player);

        String trimmedName = areaName != null ? areaName.trim() : "";
        Component nameError = AreaBuilderNaming.validateDisplayName(trimmedName);
        if (nameError != null) {
            showError(Component.translatable("area.builder.error.invalid_name", nameError).getString());
            return;
        }
        areaName = trimmedName;
        EditBox nameBox = nameField;
        if (nameBox != null && !Objects.requireNonNull(nameBox.getValue()).equals(Objects.requireNonNull(trimmedName))) {
            nameBox.setValue(trimmedName);
        }

        // Get center position
        BlockPos center = existingArea != null
            ? existingArea.centerPosition()
            : player.blockPosition();

        // Get dimension ID
        ResourceLocation dimensionId = player.level().dimension().location();

        // Create palette from preset
        AreaPalette palette = AreaPalettePresets.fromPreset(Objects.requireNonNull(selectedPreset));
        LOGGER.info("[AreaBuilder] Palette preset='{}', materials count={}", selectedPreset, palette.size());

        // Determine generation type based on biome toggle
        boolean useBiome = useBiomeGeneration && biomeConfig != null;
        AreaGenerationType genType = useBiome
            ? AreaGenerationType.BIOME
            : AreaGenerationType.CUSTOM;

        // Adjust dimensions to use center's Y as floorY
        // This ensures the area is built at the player's location, not at Y=64
        AreaDimensions adjustedDimensions = dimensions.withFloorY(center.getY());
        LOGGER.info("[AreaBuilder] Adjusted dimensions: {}x{}x{} at floorY={}",
            adjustedDimensions.width(), adjustedDimensions.length(),
            adjustedDimensions.height(), adjustedDimensions.floorY());

        // Build the AreaDefinition
        AreaDefinition definition = AreaDefinition.builder()
            .id(Objects.requireNonNull(areaId))
            .name(Objects.requireNonNull(areaName))
            .generationType(genType)
            .shape(Objects.requireNonNull(selectedShape))
            .dimensions(adjustedDimensions)
            .center(center)
            .dimension(Objects.requireNonNull(dimensionId))
            .palette(palette)
            .biomeConfig(genType == AreaGenerationType.BIOME ? biomeConfig : null)
            .options(Objects.requireNonNull(options))
            .creator(player.getUUID())
            .mainHub(isMainHub)
            .linkedZone(linkedZoneId)
            .build();

        LOGGER.info("[AreaBuilder] Created AreaDefinition: id={}, name='{}', center={}, genType={}",
            definition.id(), definition.name(), definition.centerPosition(), definition.generationType());

        // Send save payload
        UUID existingAreaId = existingArea != null ? existingArea.id()
            : (hasServerArea ? areaId : null);
        int expectedRevision = existingArea != null ? currentRevision
            : (hasServerArea ? currentRevision : 0);
        SaveAreaPayload payload = new SaveAreaPayload(
            definition,
            editorPosition,
            existingAreaId,
            expectedRevision
        );
        LOGGER.info("[AreaBuilder] Sending SaveAreaPayload to server");
        PacketDistributor.sendToServer(payload);

        pendingSaveRequestId = areaId;
        awaitingSaveAck = true;
        saveRequestTimestamp = System.currentTimeMillis(); // CLI-01 fix: Track request time
        pendingBuildAfterSave = buildAfterSave;
        dirtySinceSaveRequest = false;

        // Defer build until we receive save result with authoritative ID
    }

    /**
     * Applies server save result (authoritative ID/revision) to the local screen state.
     */
    public void handleSaveResult(@Nonnull SaveAreaResultPayload payload) {
        UUID requestId = pendingSaveRequestId;
        if (requestId == null || !requestId.equals(payload.requestId())) {
            LOGGER.debug("[AreaBuilder] Ignoring save result for requestId={}", payload.requestId());
            return;
        }

        areaId = payload.areaId();
        currentRevision = payload.revision();
        hasServerArea = true;
        areaSaved = !dirtySinceSaveRequest;
        awaitingSaveAck = false;
        pendingSaveRequestId = null;
        saveRequestTimestamp = 0; // CLI-01 fix: Clear timeout tracker
        dirtySinceSaveRequest = false;

        updateActionButtons();
        showSuccess(Component.translatable("area.builder.saved").getString());

        boolean shouldBuild = pendingBuildAfterSave && areaSaved;
        pendingBuildAfterSave = false;
        if (shouldBuild) {
            LOGGER.info("[AreaBuilder] Sending BuildAreaPayload after save ack");
            BuildAreaPayload buildPayload = Objects.requireNonNull(BuildAreaPayload.withClear(areaId));
            PacketDistributor.sendToServer(buildPayload);
            this.onClose();
        }
    }

    /**
     * Handles build status updates from the server.
     * Called by AreaClientHooks when BuildStatusPayload is received.
     *
     * @param payload The build status payload
     */
    public void handleBuildStatus(@Nonnull BuildStatusPayload payload) {
        // Only process if this is for our area
        if (!payload.areaId().equals(areaId)) {
            return;
        }

        LOGGER.debug("[AreaBuilder] Build status: {} at {}%",
            payload.status().getSerializedName(), payload.progressPercent());

        // CRIT-06 fix: Track build state for pause/resume UI
        switch (payload.status()) {
            case STARTED, RESUMED, IN_PROGRESS, QUEUED -> {
                buildActive = true;
                buildPaused = false;
            }
            case PAUSED -> {
                buildActive = true;
                buildPaused = true;
            }
            case COMPLETED, FAILED -> {
                buildActive = false;
                buildPaused = false;
            }
        }
        updateBuildControlButtons();

        // Show status message to user
        switch (payload.status()) {
            case STARTED -> showStatus(Component.translatable("area.build.started").getString(), AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);
            case PAUSED -> showStatus(Component.translatable("area.build.paused").getString(), AreaBuilderGuiConstants.COLOR_STATUS_WARNING);
            case RESUMED -> showStatus(Component.translatable("area.build.resumed").getString(), AreaBuilderGuiConstants.COLOR_TEXT_PRIMARY);
            case COMPLETED -> showSuccess(Component.translatable("area.build.completed").getString());
            case FAILED -> showError(Component.translatable("area.build.failed").getString());
            case QUEUED -> {
                // UX-02 fix: Show queue position (progressPercent carries queue position for QUEUED status)
                int queuePosition = payload.progressPercent();
                if (queuePosition > 0) {
                    showStatus(Component.translatable("area.build.queued_position", queuePosition).getString(), AreaBuilderGuiConstants.COLOR_TEXT_MUTED);
                } else {
                    showStatus(Component.translatable("area.build.queued").getString(), AreaBuilderGuiConstants.COLOR_TEXT_MUTED);
                }
            }
            default -> { /* IN_PROGRESS - no message needed */ }
        }
    }

    /**
     * CRIT-06 fix: Updates pause/resume button visibility based on build state.
     */
    private void updateBuildControlButtons() {
        // Use local variables to satisfy null checker
        EditorButtonWidget pause = pauseButton;
        if (pause != null) {
            // Show pause button when build is active and not paused
            pause.visible = buildActive && !buildPaused;
        }
        EditorButtonWidget resume = resumeButton;
        if (resume != null) {
            // Show resume button when build is paused
            resume.visible = buildActive && buildPaused;
        }
    }

    /**
     * CRIT-06 fix: Pauses the current build.
     */
    private void pauseBuild() {
        if (!buildActive || buildPaused) {
            return;
        }
        LOGGER.info("[AreaBuilder] Requesting pause for area {}", areaId);
        PacketDistributor.sendToServer(new PauseBuildPayload(areaId));
    }

    /**
     * CRIT-06 fix: Resumes a paused build.
     */
    private void resumeBuild() {
        if (!buildActive || !buildPaused) {
            return;
        }
        LOGGER.info("[AreaBuilder] Requesting resume for area {}", areaId);
        PacketDistributor.sendToServer(new ResumeBuildPayload(areaId));
    }

    // Getters for external access
    public AreaShape getSelectedShape() {
        return selectedShape;
    }

    public AreaDimensions getDimensions() {
        return dimensions;
    }

    public String getSelectedPreset() {
        return selectedPreset;
    }

    public AreaOptions getOptions() {
        return options;
    }

    public ResourceLocation getSelectedBiome() {
        return selectedBiome;
    }

    public BiomeGenerationConfig getBiomeConfig() {
        return biomeConfig;
    }

    @Nullable
    public BlockPos getEditorPosition() {
        return editorPosition;
    }

    public boolean isMainHub() {
        return isMainHub;
    }

    // ========== Template UI Methods ==========

    private void toggleTemplateSelector() {
        showTemplateSelector = !showTemplateSelector;
        if (showTemplateSelector) {
            // Create selector widget if needed
            if (templateSelector == null) {
                Layout layout = layout();
                templateSelector = new TemplateSelectorWidget(
                    layout.contentX, layout.contentY, 220, layout.contentHeight,
                    this::onTemplateSelected,
                    this::onTemplateDeleted
                );
            }

            // Use cached templates if valid, otherwise request from server
            TemplateSelectorWidget selector = templateSelector;
            if (isCacheValid()) {
                List<TemplateListPayload.TemplateSummary> cached = cachedTemplates;
                if (cached != null && selector != null) {
                    selector.setTemplates(cached);
                }
            } else {
                PacketDistributor.sendToServer(new RequestTemplateListPayload());
            }
        }
    }

    private static boolean isCacheValid() {
        return cachedTemplates != null &&
               (System.currentTimeMillis() - templateCacheTime) < TEMPLATE_CACHE_TTL_MS;
    }

    private void showSaveTemplateDialog() {
        // Can only save template if area is saved first
        if (!areaSaved) {
            showError(Component.translatable("area.builder.save").getString());
            return;
        }
        saveTemplateDialog = new SaveTemplateDialog(areaId, () -> {
            showSuccess(Component.translatable("area.message.template_saved", areaName).getString());
        });
        saveTemplateDialog.show();
    }

    private void onTemplateSelected(UUID templateId) {
        // Request template data from server
        PacketDistributor.sendToServer(new LoadTemplatePayload(templateId));
        // Hide selector
        showTemplateSelector = false;
    }

    private void onTemplateDeleted(UUID templateId) {
        // Send delete request to server
        PacketDistributor.sendToServer(new DeleteTemplatePayload(templateId));
        // Invalidate cache and refresh template list
        invalidateTemplateCache();
        PacketDistributor.sendToServer(new RequestTemplateListPayload());
    }

    /**
     * Updates the zone selector with the list of zones from the server.
     * Called from AreaClientHooks when ZoneListPayload is received.
     *
     * @param zones The list of zone summaries
     */
    public void updateZoneList(@Nonnull List<ZoneListPayload.ZoneSummary> zones) {
        if (zoneSelectorWidget != null) {
            zoneSelectorWidget.setZones(zones);
        }
    }

    /**
     * Updates the template list from the server.
     * Called from AreaClientHooks when TemplateListPayload is received.
     *
     * @param templates The list of template summaries
     */
    public void updateTemplateList(@Nonnull List<TemplateListPayload.TemplateSummary> templates) {
        // Update cache
        cachedTemplates = new ArrayList<>(templates);
        templateCacheTime = System.currentTimeMillis();

        if (templateSelector != null) {
            templateSelector.setTemplates(templates);
        }
        LOGGER.debug("[AreaBuilder] Received {} templates from server (cached)", templates.size());
    }

    /**
     * Invalidates the template cache. Call after saving or deleting templates.
     */
    public static void invalidateTemplateCache() {
        cachedTemplates = null;
        templateCacheTime = 0L;
    }

    /**
     * Applies a template's configuration to this screen.
     * Called from AreaClientHooks when TemplateDataPayload is received.
     *
     * @param data The template data payload
     */
    public void applyTemplate(@Nonnull TemplateDataPayload data) {
        this.selectedShape = data.shape();
        this.dimensions = data.dimensions();
        this.selectedPreset = data.presetId();
        this.options = data.options();
        this.useBiomeGeneration = data.generationType() == AreaGenerationType.BIOME;
        BiomeGenerationConfig templateBiomeConfig = data.biomeConfig();
        if (templateBiomeConfig != null) {
            this.biomeConfig = templateBiomeConfig;
            this.selectedBiome = templateBiomeConfig.biomeId();
        }
        markDirty();
        rebuildWidgets();
        showSuccess(Component.translatable("area.builder.template_loaded").getString());
        LOGGER.info("[AreaBuilder] Applied template: shape={}, dims={}x{}x{}, preset={}",
            selectedShape, dimensions.width(), dimensions.length(), dimensions.height(), selectedPreset);
    }

    private static final class Layout {
        private final int titleY;
        private final int nameLabelY;
        private final int nameFieldX;
        private final int nameFieldY;
        private final int nameFieldWidth;
        private final int tabY;
        private final int tabStartX;
        private final int contentX;
        private final int contentY;
        private final int contentWidth;
        private final int contentHeight;
        private final int actionBarY;
        private final int buttonY;

        private Layout(int titleY, int nameLabelY, int nameFieldX, int nameFieldY, int nameFieldWidth,
                       int tabY, int tabStartX,
                       int contentX, int contentY, int contentWidth, int contentHeight,
                       int actionBarY, int buttonY) {
            this.titleY = titleY;
            this.nameLabelY = nameLabelY;
            this.nameFieldX = nameFieldX;
            this.nameFieldY = nameFieldY;
            this.nameFieldWidth = nameFieldWidth;
            this.tabY = tabY;
            this.tabStartX = tabStartX;
            this.contentX = contentX;
            this.contentY = contentY;
            this.contentWidth = contentWidth;
            this.contentHeight = contentHeight;
            this.actionBarY = actionBarY;
            this.buttonY = buttonY;
        }

        private static Layout forScreen(int screenWidth, int screenHeight, int fontHeight) {
            int padding = AreaBuilderGuiConstants.CONTENT_PADDING;
            int titleY = padding;
            int nameLabelY = titleY + fontHeight + (padding / 2);
            int nameFieldX = padding;
            int nameFieldY = nameLabelY + fontHeight + (padding / 2);
            int nameFieldWidth = screenWidth - padding * 2;
            int tabY = nameFieldY + AreaBuilderGuiConstants.FIELD_HEIGHT + padding;
            int tabStartX = (screenWidth - AreaBuilderGuiConstants.getTabBarWidth()) / 2;
            int contentX = padding;
            int contentY = tabY + AreaBuilderGuiConstants.TAB_HEIGHT + padding;
            int contentWidth = screenWidth - padding * 2;
            int actionBarY = screenHeight - padding - AreaBuilderGuiConstants.ACTION_BAR_HEIGHT;
            int contentHeight = actionBarY - contentY - padding;
            int buttonY = actionBarY + (AreaBuilderGuiConstants.ACTION_BAR_HEIGHT - AreaBuilderGuiConstants.BUTTON_HEIGHT) / 2;
            return new Layout(titleY, nameLabelY, nameFieldX, nameFieldY, nameFieldWidth,
                tabY, tabStartX, contentX, contentY, contentWidth, contentHeight, actionBarY, buttonY);
        }
    }
}
