package com.devmod.client.ui.editor;
import java.awt.Desktop;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.google.gson.JsonElement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.DevMod;
import com.devmod.client.telemetry.UiTelemetry;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.ConfirmDialog;
import com.devmod.client.ui.editor.components.FooterComponent;
import com.devmod.client.ui.editor.components.HeaderComponent;
import com.devmod.client.ui.editor.components.LeftColumnComponent;
import com.devmod.client.ui.editor.components.ModeBadge;
import com.devmod.client.ui.editor.components.PreviewRenderer;
import com.devmod.client.ui.editor.components.ScrollableContentArea;
import com.devmod.client.ui.editor.components.SlotSelector;
import com.devmod.client.ui.editor.controller.InputRouter;
import com.devmod.client.ui.editor.controller.ModeController;
import com.devmod.client.ui.editor.controller.OverlayController;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.editor.core.EditorCache;
import com.devmod.client.ui.editor.core.EditorConfig;
import com.devmod.client.ui.editor.core.EditorLayout;
import com.devmod.client.ui.editor.core.EditorScaleCalculator;
import com.devmod.client.ui.editor.core.EditorSpacing;
import com.devmod.client.ui.editor.core.GridValidator;
import com.devmod.client.ui.editor.core.ResponsiveLayout;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.TooltipManager;
import com.devmod.client.ui.editor.core.Typography;
import com.devmod.client.ui.editor.debug.DebugOverlay;
import com.devmod.client.ui.editor.favorites.FavoritePresetStore;
import com.devmod.client.ui.editor.modules.ArmorModule;
import com.devmod.client.ui.editor.modules.FoodModule;
import com.devmod.client.ui.editor.modules.FuelModule;
import com.devmod.client.ui.editor.modules.GeneralModule;
import com.devmod.client.ui.editor.modules.RangedModule;
import com.devmod.client.ui.editor.modules.UsableModule;
import com.devmod.client.ui.editor.modules.WeaponModule;
import com.devmod.client.ui.editor.snapshot.ItemEditorHistoryEntry;
import com.devmod.client.ui.editor.snapshot.ItemEditorSnapshot;
import com.devmod.client.ui.editor.state.ItemEditorState;
import com.devmod.client.ui.editor.systems.CraftingInfoPanel;
import com.devmod.client.ui.editor.systems.DebugPanel;
import com.devmod.client.ui.editor.systems.HelpOverlay;
import com.devmod.client.ui.editor.systems.LowConfidenceDetector;
import com.devmod.client.ui.editor.systems.MultiEditManager;
import com.devmod.client.ui.editor.systems.MultiEditPanel;
import com.devmod.client.ui.editor.systems.PresetSelectorOverlay;
import com.devmod.client.ui.editor.systems.TemplateOverlay;
import com.devmod.config.EditorClientConfig;
import com.devmod.config.handler.impl.ArmorConfigHandler;
import com.devmod.integration.PufferfishIntegration;
import com.devmod.network.ArmorStatsPayload;
import com.devmod.network.EditorApplyConfirmPayload;
import com.devmod.recipe.CraftingRecipeData;
import com.devmod.recipe.RecipeConfigManager;
import com.devmod.stats.ArmorStats;
import com.devmod.stats.WeaponStats;
import com.devmod.util.DatapackIO;

@OnlyIn(Dist.CLIENT)
public class ItemEditorScreen extends Screen implements InputRouter.InputContext {

    // ═══════════════════════════════════════════════════════════════
    // LAYOUT
    // ═══════════════════════════════════════════════════════════════

    private final ResponsiveLayout layout;
    private final EditorLayout editorLayout = new EditorLayout();
    private static final String DEFAULT_DATAPACK_NAME = "devmod_balance_auto";
    private static final int STATUS_MESSAGE_BOTTOM_OFFSET = 60;
    private static final int STATUS_MESSAGE_PADDING_X = 10;
    private static final int STATUS_MESSAGE_PADDING_Y = 4;
    private static final int STATUS_MESSAGE_SCREEN_MARGIN = 4;
    private static final int STATUS_MESSAGE_BG = DesignTokens.ItemEditor.STATUS_MESSAGE_BG;
    private static final int HISTORY_PANEL_WIDTH = 260;
    private static final int HISTORY_PANEL_HEIGHT = 200;
    private static final int HISTORY_PANEL_MARGIN_RIGHT = 8;
    private static final int HISTORY_PANEL_MARGIN_TOP = 8;
    private static final int HISTORY_PANEL_TITLE_OFFSET_X = 8;
    private static final int HISTORY_PANEL_TITLE_OFFSET_Y = 8;
    private static final int HISTORY_FILTER_OFFSET_Y = 18;
    private static final int HISTORY_FILTER_HEIGHT = 12;
    private static final int HISTORY_FILTER_GAP = 6;
    private static final int HISTORY_PANEL_LIST_OFFSET_Y = 24;
    private static final int HISTORY_PANEL_LIST_HEIGHT_PADDING = 50;
    private static final int HISTORY_PANEL_LINE_HEIGHT = 14;
    private static final int HISTORY_PANEL_FOOTER_OFFSET_Y = 18;
    private static final int HISTORY_PANEL_TEXT_OFFSET_X = 8;
    private static final int HISTORY_PANEL_CLEAR_WIDTH = 60;
    private static final int HISTORY_PANEL_CLEAR_HEIGHT = 14;
    private static final int HISTORY_PANEL_CLEAR_OFFSET_X = 8;
    private static final int HISTORY_PANEL_CLEAR_OFFSET_Y = 2;
    private static final int HISTORY_PANEL_CLEAR_TEXT_OFFSET_X = 12;
    private static final int HISTORY_PANEL_CLEAR_TEXT_OFFSET_Y = 3;
    private static final int HISTORY_PANEL_ROLLBACK_WIDTH = 80;
    private static final int HISTORY_PANEL_ROLLBACK_HEIGHT = 14;
    private static final int HISTORY_PANEL_ROLLBACK_OFFSET_X = 76;
    private static final int HISTORY_PANEL_ROLLBACK_TEXT_OFFSET_X = 8;
    private static final int HISTORY_PANEL_ROLLBACK_TEXT_OFFSET_Y = 3;
    private static final String FAVORITES_TITLE_TEXT = "Favorites";
    private static final String FAVORITES_LABEL_PREFIX = "★ ";
    private static final String FAVORITES_LABEL_FALLBACK = "Preset";
    private static final String FAVORITES_PIN_TEXT = "Pin last";
    private static final String FAVORITES_UNPIN_TEXT = "Unpin last";
    private static final String DEV_PANEL_HEADER_TEXT = "Dev Mode";
    private static final String HISTORY_TITLE_TEXT = "Edit History";
    private static final String HISTORY_EMPTY_TEXT = "No history yet";
    private static final String HISTORY_SHOWING_EMPTY = "Showing 0/0";
    private static final String HISTORY_SHOWING_PREFIX = "Showing ";
    private static final String HISTORY_SHOWING_SEPARATOR = "/";
    private static final String HISTORY_CLEAR_TEXT = "Clear";
    private static final String HISTORY_ROLLBACK_TEXT = "Rollback";
    private static final String PINNED_PREFIX = "Pinned ";
    private static final String UNPINNED_PREFIX = "Unpinned ";
    private static final boolean PRESETS_ENABLED = false;
    private static final String WEAPON_STATS_KEY = "WeaponModStats";
    private static final String ARMOR_STATS_KEY = "ArmorModStats";
    private static final String ARMOR_STATS_COMPONENT_KEY = "armor_stats_component";
    private static final String CUSTOM_TAG_MISSING = "custom tag missing";
    private static final String WEAPON_STATS_MISSING = "weapon stats tag missing";
    private static final String ARMOR_STATS_MISSING = "armor stats tag missing";
    private static final String MULTI_EDIT_PERSIST_PREFIX = "MultiEdit persist slot ";
    private static final String MULTI_EDIT_PERSIST_FAILED_PREFIX = "MultiEdit persist failed: ";
    private static final String SOURCE_SPECIFIC_ARMOR = "Specific (CustomData: ArmorModStats)";
    private static final String SOURCE_SPECIFIC_WEAPON = "Specific (CustomData: WeaponModStats)";
    private static final String SOURCE_VANILLA = "Vanilla (no overrides)";
    private static final int FAVORITES_TITLE_OFFSET_X = 8;
    private static final int FAVORITES_TITLE_OFFSET_Y = 8;
    private static final int FAVORITES_ROW_HEIGHT = 18;
    private static final int FAVORITES_LIST_START_Y = 20;
    private static final int FAVORITES_LIST_BOTTOM_PADDING = 28;
    private static final int FAVORITES_ROW_HIT_INSET = 4;
    private static final int FAVORITES_ROW_HOVER_INSET = 2;
    private static final int FAVORITES_ROW_TEXT_OFFSET_X = 8;
    private static final int FAVORITES_ROW_TEXT_OFFSET_Y = 5;
    private static final int FAVORITES_PIN_BUTTON_OFFSET_X = 8;
    private static final int FAVORITES_PIN_BUTTON_OFFSET_Y = 18;
    private static final int FAVORITES_PIN_BUTTON_WIDTH = 80;
    private static final int FAVORITES_PIN_BUTTON_HEIGHT = 14;
    private static final int FAVORITES_PIN_TEXT_OFFSET_X = 12;
    private static final int FAVORITES_PIN_TEXT_OFFSET_Y = 3;
    private static final int DEV_PANEL_FALLBACK_WIDTH = 150;
    private static final int DEV_PANEL_FALLBACK_HEIGHT = 200;
    private static final int DEV_PANEL_FALLBACK_MARGIN_TOP = 10;
    private static final int DEV_PANEL_FALLBACK_MARGIN_RIGHT = 10;
    private static final int DEV_PANEL_TEXT_OFFSET_X = 8;
    private static final int DEV_PANEL_TEXT_OFFSET_Y = 8;
    private static final int DEV_PANEL_TITLE_LINE_STEP = 12;
    private static final int DEV_PANEL_LINE_STEP = 10;
    private static final int DEV_PANEL_BG = DesignTokens.ItemEditor.DEV_PANEL_BG;
    private static final int DEV_PANEL_TITLE_COLOR = DesignTokens.ItemEditor.DEV_PANEL_TITLE;
    private static final String DEV_PANEL_TITLE_TEXT = "§b[Dev Mode]";
    private static final int STATUS_TICKS_DURATION = 60;
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_SLOT_SIZE = 20;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int HOTBAR_BOTTOM_OFFSET = 22;
    private static final int LOCAL_ONLY_BANNER_HEIGHT = 18;
    private static final int LOCAL_ONLY_BANNER_MARGIN = 6;
    private static final int LOCAL_ONLY_BANNER_PADDING_X = 8;
    private static final int LOCAL_ONLY_BANNER_BG = DesignTokens.Semantic.WARNING_MUTED;
    private static final int LOCAL_ONLY_BANNER_BORDER = DesignTokens.Semantic.WARNING;
    private static final String LOCAL_ONLY_BANNER_TEXT =
        "Local kit edit: use Save to Kit to keep changes.";

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    /** Centralized editor state - subsystems should use this for decoupled access */
    private final ItemEditorState editorState;

    /** Mode controller - handles preview/apply mode switching */
    private final ModeController modeController;

    private final EditorStartTab requestedTab;

    /** Optional parent screen to return to on close */
    @Nullable
    private final Screen parentScreen;

    /** Optional callback when item editing is complete */
    @Nullable
    private final Consumer<ItemStack> onItemEdited;
    @Nullable
    private EditorModule activeModule;

    // Mode flags are now managed entirely by editorState
    private boolean showDevPanel = false;
    private final PerformanceMonitor perfMonitor = PerformanceMonitor.INSTANCE;

    // Components
    private final HeaderComponent header = new HeaderComponent();
    private final LeftColumnComponent leftColumn = new LeftColumnComponent();
    private final FooterComponent footer = new FooterComponent();
    private final ScrollableContentArea scrollArea = new ScrollableContentArea();

    // UI state
    @Nullable
    private String statusMessage = null;
    private int statusColor = 0;
    private int statusTicks = 0;

    // Dialog state
    @Nullable
    private ConfirmDialog activeDialog = null;
    private long lastSaveTimestamp = 0;
    private int historyScrollOffset = 0;
    private final List<ItemEditorHistoryEntry> historyEntries = new ArrayList<>();
    private HistoryFilter historyFilter = HistoryFilter.ALL;
    private int historySelectedIndex = -1;
    private final List<HistoryFilterHit> historyFilterHits = new ArrayList<>();
    private ResponsiveLayout.Rect historyRollbackBounds = ResponsiveLayout.Rect.EMPTY;
    @Nullable
    private ItemEditorSnapshot baselineSnapshot = null;
    // Low-confidence detection system
    private final LowConfidenceDetector lowConfidenceDetector = new LowConfidenceDetector();
    @Nullable
    private String lastLoadedPreset = null;
    private final FavoritePresetStore favoriteStore = new FavoritePresetStore();
    private final CraftingInfoPanel craftingPanel = new CraftingInfoPanel();

    // Overlay controller manages overlay visibility state
    private final OverlayController overlayController = new OverlayController();

    // Input router - centralizes all input handling
    private InputRouter inputRouter;

    // Help overlay
    private final HelpOverlay helpOverlay = new HelpOverlay();

    // Debug panel
    @Nullable
    private DebugPanel debugPanel;

    // Multi-edit subsystem
    @Nullable
    private MultiEditManager multiEditManager;
    @Nullable
    private MultiEditPanel multiEditPanel;
    private boolean showMultiEditPanel = false;
    @Nullable
    private TemplateOverlay templateOverlay;
    @Nullable
    private PresetSelectorOverlay presetSelectorOverlay;

    // Preview error logging flag
    private boolean previewItemErrorLogged = false;
    private boolean commitEditsOnClose = true;
    private final boolean localOnlyMode;

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    @SuppressWarnings("this-escape") // Callback registration in constructor is intentional
    public ItemEditorScreen(ItemStack item, EditorStartTab startTab) {
        this(item, startTab, null, null, false);
    }

    @SuppressWarnings("this-escape") // Callback registration in constructor is intentional
    public ItemEditorScreen(ItemStack item, EditorStartTab startTab,
                            @Nullable Screen parentScreen, @Nullable Consumer<ItemStack> onItemEdited) {
        this(item, startTab, parentScreen, onItemEdited, false);
    }

    @SuppressWarnings("this-escape") // Callback registration in constructor is intentional
    public ItemEditorScreen(ItemStack item, EditorStartTab startTab,
                            @Nullable Screen parentScreen, @Nullable Consumer<ItemStack> onItemEdited,
                            boolean localOnlyMode) {
        super(java.util.Objects.requireNonNull(Component.literal("Item Editor"), "title"));
        this.editorState = new ItemEditorState(item);
        this.parentScreen = parentScreen;
        this.onItemEdited = onItemEdited;
        this.localOnlyMode = localOnlyMode;
        this.modeController = new ModeController(editorState)
            .setStatusCallback((msg, color) -> showStatus(msg, color == null ? DesignTokens.Semantic.INFO : color));
        this.overlayController.setOnOverlayChanged(this::onOverlayChanged);
        this.inputRouter = new InputRouter(this);
        this.requestedTab = startTab;
        this.layout = new ResponsiveLayout();
        modeController.loadPreference();
        if (localOnlyMode) {
            editorState.setPreviewMode(true);
            editorState.setGlobalMode(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        // Track screen open for telemetry
        UiTelemetry.screenOpened("editor", "item_editor");

        // Resolve UI scale (discrete) and apply to coordinate helper
        float uiScale = EditorScaleCalculator.getEffectiveScale(width, height, EditorConfig.getUiScaleSetting());
        ScaledCoord.setScale(uiScale);
        var screenFit = EditorScaleCalculator.calculateFit(width, height, uiScale);

        // Calculate layout for screen dimensions
        layout.calculate(width, height, screenFit, uiScale);
        editorLayout.computePositions(screenFit, uiScale);
        EditorSpacing.ENABLE_GRID_VALIDATION = EditorClientConfig.EDITOR_GRID_VALIDATION.get();
        if (EditorSpacing.ENABLE_GRID_VALIDATION) {
            GridValidator.validate("editor", editorLayout.getPanelBounds(), editorLayout.getHeaderBounds(),
                editorLayout.getFooterBounds(), editorLayout.getLeftColumnBounds(), editorLayout.getContentBounds());
        }

        // Resolve and initialize the module
        EditorModule resolvedModule = Objects.requireNonNull(resolveModule(getEditedItem(), requestedTab), "active module");
        activeModule = resolvedModule;
        editorState.setActiveModule(resolvedModule);
        resolvedModule.setStatusConsumer((msg, color) -> showStatus(msg, color == null ? DesignTokens.Semantic.INFO : color));
        resolvedModule.setModuleSwitchCallback(this::switchModule);
        resolvedModule.setItem(getEditedItem());
        resolvedModule.init(layout);

        // Configure and check low-confidence detection system
        lowConfidenceDetector.setStatusConsumer((msg, color) -> showStatus(msg, color));
        lowConfidenceDetector.setOnCancelCallback(() -> {
            closeEditor(false);
            Minecraft.getInstance().setScreen(null);
        });
        lowConfidenceDetector.checkAndWarn(getEditedItem(), requestedTab, resolvedModule);
        if (lowConfidenceDetector.hasPendingDetection()) {
            scrollArea.setScrollOffset(0); // ensure dialog visible
        }

        resolvedModule.setDirtyTrackingEnabled(true);
        resolvedModule.clearDirty();
        configureHeader();
        configureLeftColumn();
        configureFooterCallbacks();

        // Initialize multi-edit subsystem
        MultiEditManager editManager = new MultiEditManager();
        multiEditManager = editManager;
        MultiEditPanel editPanel = new MultiEditPanel(editManager, () -> !isPreviewMode(), this::getActiveItemType);
        multiEditPanel = editPanel;
        editPanel.setShowDialogCallback(dialog -> {
            activeDialog = dialog;
            dialog.show();
        });
        editManager.setPersistenceHandler((stack, slot) -> persistMultiEditItem(stack, slot == null ? -1 : slot));
        debugPanel = new DebugPanel();
        TemplateOverlay templateOverlayInstance = new TemplateOverlay();
        templateOverlay = templateOverlayInstance;
        templateOverlayInstance.onClose(this::closeOverlay);
        templateOverlayInstance.onApply(this::handleTemplateApply);
        if (PRESETS_ENABLED) {
            PresetSelectorOverlay presetOverlay = new PresetSelectorOverlay();
            presetSelectorOverlay = presetOverlay;
            presetOverlay.setContext(getActiveItemType());
            presetOverlay.onClose(this::closeOverlay);
            presetOverlay.onApply(this::applyPreset);
            presetOverlay.onDelete(this::deletePreset);
            presetOverlay.onRename(this::renamePreset);
            presetOverlay.onSaveCurrent(this::saveCurrentAsPreset);
        } else {
            presetSelectorOverlay = null;
        }

        // Invalidate cache on init
        EditorCache.INSTANCE.invalidateAll();
        historyEntries.clear();
        historyFilter = HistoryFilter.ALL;
        historySelectedIndex = -1;
        baselineSnapshot = captureSnapshot("open", "Editor opened");
    }

    private EditorModule resolveModule(ItemStack stack, EditorStartTab requested) {
        return switch (requested) {
            case WEAPON -> {
                var detection = WeaponTypeDetector.detectDetailed(stack);
                if (WeaponTypeDetector.isRanged(detection.type())) {
                    yield new RangedModule(detection.type() == WeaponTypeDetector.WeaponType.CROSSBOW
                        ? com.devmod.client.ui.editor.modules.RangedModule.RangedVariant.CROSSBOW
                        : com.devmod.client.ui.editor.modules.RangedModule.RangedVariant.BOW);
                }
                if (detection.type() == WeaponTypeDetector.WeaponType.TRIDENT) {
                    yield new WeaponModule(com.devmod.client.ui.editor.modules.WeaponModule.WeaponVariant.TRIDENT);
                }
                if (detection.type() == WeaponTypeDetector.WeaponType.MACE) {
                    yield new WeaponModule(com.devmod.client.ui.editor.modules.WeaponModule.WeaponVariant.MACE);
                }
                yield new WeaponModule();
            }
            case ARMOR -> new ArmorModule();
            case GENERAL -> {
                // Auto-detect based on item type
                if (ArmorConfigHandler.isArmor(stack)) {
                    DevMod.LOGGER.warn("[ItemEditor] Requested GENERAL but item is armor; falling back to ARMOR module.");
                    yield new ArmorModule();
                }
                var detailed = WeaponTypeDetector.detectDetailed(stack);
                if (WeaponTypeDetector.isRanged(detailed.type())) {
                    DevMod.LOGGER.warn("[ItemEditor] Requested GENERAL but item is ranged weapon; falling back to RANGED module.");
                    yield new RangedModule(detailed.type() == WeaponTypeDetector.WeaponType.CROSSBOW
                        ? com.devmod.client.ui.editor.modules.RangedModule.RangedVariant.CROSSBOW
                        : com.devmod.client.ui.editor.modules.RangedModule.RangedVariant.BOW);
                }
                if (WeaponTypeDetector.isMelee(detailed.type())) {
                    DevMod.LOGGER.warn("[ItemEditor] Requested GENERAL but item is melee weapon; falling back to WEAPON module.");
                    if (detailed.type() == WeaponTypeDetector.WeaponType.TRIDENT) {
                        yield new WeaponModule(com.devmod.client.ui.editor.modules.WeaponModule.WeaponVariant.TRIDENT);
                    }
                    if (detailed.type() == WeaponTypeDetector.WeaponType.MACE) {
                        yield new WeaponModule(com.devmod.client.ui.editor.modules.WeaponModule.WeaponVariant.MACE);
                    }
                    yield new WeaponModule();
                }
                yield new GeneralModule();
            }
            case RECIPE -> new com.devmod.client.ui.editor.modules.RecipeModule();
            case USABLE -> new UsableModule(detectUsableVariant(stack));
            case FOOD -> new FoodModule();
            case FUEL -> new FuelModule();
        };
    }

    /**
     * Detect the appropriate UsableModule variant for the given item.
     */
    private UsableModule.UsableVariant detectUsableVariant(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return UsableModule.UsableVariant.STANDARD;
        }
        var itemType = stack.getItem();
        // Throwable items
        if (itemType instanceof net.minecraft.world.item.SnowballItem ||
            itemType instanceof net.minecraft.world.item.EggItem ||
            itemType instanceof net.minecraft.world.item.EnderpearlItem ||
            itemType instanceof net.minecraft.world.item.ThrowablePotionItem) {
            return UsableModule.UsableVariant.THROWABLE;
        }
        // Chargeable items (bows, crossbows)
        if (itemType instanceof net.minecraft.world.item.BowItem ||
            itemType instanceof net.minecraft.world.item.CrossbowItem) {
            return UsableModule.UsableVariant.CHARGEABLE;
        }
        // Blockable items (shields)
        if (itemType instanceof net.minecraft.world.item.ShieldItem) {
            return UsableModule.UsableVariant.BLOCKABLE;
        }
        return UsableModule.UsableVariant.STANDARD;
    }

    /**
     * Switch to a different editor module.
     * Called by GeneralModule (Navigation Hub) when user clicks a module card.
     *
     * @param targetTab The target module to switch to
     */
    private void switchModule(EditorStartTab targetTab) {
        if (targetTab == null) return;

        // Check for unsaved changes
        if (activeModule != null && activeModule.hasUnsavedChanges()) {
            showStatus("Unsaved changes - save or discard first", DesignTokens.Semantic.WARNING);
            return;
        }

        // Close current module
        if (activeModule != null) {
            activeModule.onClose();
        }

        // Resolve and initialize the new module
        EditorModule resolvedModule = Objects.requireNonNull(resolveModule(getEditedItem(), targetTab), "active module");
        activeModule = resolvedModule;
        editorState.setActiveModule(resolvedModule);
        resolvedModule.setStatusConsumer((msg, color) -> showStatus(msg, color == null ? DesignTokens.Semantic.INFO : color));
        resolvedModule.setModuleSwitchCallback(this::switchModule);
        resolvedModule.setItem(getEditedItem());
        resolvedModule.init(layout);
        resolvedModule.setDirtyTrackingEnabled(true);
        resolvedModule.clearDirty();

        // Update UI components
        configureHeader();
        configureLeftColumn();

        // Reset scroll
        scrollArea.setScrollOffset(0);

        showStatus("Switched to " + resolvedModule.getTitle(), DesignTokens.Semantic.INFO);
        DevMod.LOGGER.info("[ItemEditor] Switched module to: {}", targetTab);
    }

    private void configureHeader() {
        header.clearTabs();
        EditorModule module = activeModule;
        if (module != null) {
            List<ModuleTab> tabs = module.getTabs();
            for (ModuleTab tab : tabs) {
                header.addTab(tab.id(), tab.label());
            }
            header.selectTab(module.getActiveTabIndex());
        }

        header.onTabChange(index -> {
            EditorModule mod = activeModule;
            if (mod != null) {
                mod.setActiveTab(index);
                scrollArea.scrollToTop();
            }
        });
        header.onClose(this::handleCloseRequest);
        header.showScopeBadge(false);

        header.getModeBadge()
            .badgeType(ModeBadge.BadgeType.MODE)
            .mode(isPreviewMode() ? ModeBadge.Mode.PREVIEW : ModeBadge.Mode.APPLY)
            .onModeChange(this::handleModeChange);

        header.getModeBadge().clickable(!localOnlyMode);
    }

    private void configureLeftColumn() {
        SlotSelector.SlotType slotType = switch (requestedTab) {
            case ARMOR -> SlotSelector.SlotType.ARMOR;
            default -> SlotSelector.SlotType.WEAPON;
        };
        leftColumn.slotType(slotType);
        leftColumn.item(getEditedItem());
        leftColumn.onSlotSelect(this::handleSlotSwitch);
        if (localOnlyMode) {
            leftColumn.previewMode(PreviewRenderer.PreviewMode.ITEM);
            leftColumn.showPreviewSlots(false);
            leftColumn.showSlotSelector(false);
        }
        setSelectedSlotInfo(leftColumn.getSelectedSlot());
    }

    private void updateLeftColumnStats() {
        leftColumn.clearStats();
        // If preview is active, prefer the module's preview item for display to avoid mutating real item
        ItemStack displayItem = resolvePreviewItem(getEditedItem());
        leftColumn.item(displayItem);
        if (activeModule instanceof WeaponModule weaponModule) {
            var stats = weaponModule.getStats();
            leftColumn.addStat("Attack", stats.getAttackDamage(), "+%.1f");
            leftColumn.addStat("Speed", stats.getAttackSpeed(), "+%.1f");
            leftColumn.addStat("Crit", stats.getCritChance() * 100f, "%.0f%%");
        } else if (activeModule instanceof RangedModule rangedModule) {
            var stats = rangedModule.getStats();
            leftColumn.addStat("Draw", stats.drawSpeed, "%.2fx");
            leftColumn.addStat("Proj Spd", stats.projectileSpeed, "%.2f");
            leftColumn.addStat("Accuracy", stats.accuracy * 100f, "%.0f%%");
        } else if (activeModule instanceof ArmorModule armorModule) {
            var stats = armorModule.getStats();
            leftColumn.addStat("Defense", stats.getPhysicalReduction() * 100f, "%.0f%%");
            leftColumn.addStat("Fire", stats.getFireReduction() * 100f, "%.0f%%");
            leftColumn.addStat("Magic", stats.getMagicReduction() * 100f, "%.0f%%");
        }

        EditorModule module = activeModule;
        if (module != null) {
            leftColumn.pendingChanges(module.getPendingChanges().size());
        }
        if (lastSaveTimestamp > 0) {
            leftColumn.lastSaved(lastSaveTimestamp);
        }
    }

    private ItemStack resolvePreviewItem(ItemStack fallback) {
        EditorModule module = activeModule;
        if (isPreviewMode() && module != null) {
            try {
                ItemStack preview = module.getPreviewItem();
                if (preview != null) {
                    return preview;
                }
            } catch (Exception e) {
                if (!previewItemErrorLogged) {
                    DevMod.LOGGER.debug("[ItemEditor] Failed to resolve preview item", e);
                    previewItemErrorLogged = true;
                }
            }
        }
        return fallback;
    }

    private void handleSlotSwitch(SlotSelector.SlotInfo slot) {
        if (slot == null || Objects.equals(getSelectedSlotInfo(), slot)) {
            return;
        }
        EditorModule module = activeModule;
        if (!isPreviewMode() && module != null && module.hasUnsavedChanges()) {
            ConfirmDialog dialog = ConfirmDialog.switchSlot(slot.label(), module.getPendingChanges().size(),
                () -> {
                    setSelectedSlotInfo(slot);
                    showStatus("Switched to " + slot.label(), DesignTokens.Semantic.INFO);
                },
                () -> {});
            activeDialog = dialog;
            dialog.show();
            return;
        }
        setSelectedSlotInfo(slot);
        showStatus("Switched to " + slot.label(), DesignTokens.Semantic.INFO);
        if (showMultiEditPanel) {
            refreshMultiEditSelection();
        }
    }

    @Override
    public void handleModeChange(ModeBadge.Mode mode) {
        if (localOnlyMode && mode == ModeBadge.Mode.APPLY) {
            showStatus("Local kit edit only", DesignTokens.Semantic.WARNING);
            editorState.setPreviewMode(true);
            header.getModeBadge().setMode(ModeBadge.Mode.PREVIEW);
            header.getModeBadge().closeDropdown();
            return;
        }
        boolean preview = mode == ModeBadge.Mode.PREVIEW;
        if (preview == isPreviewMode()) {
            return;
        }

        // Switching from APPLY -> PREVIEW with pending changes: confirm discard
        EditorModule module = activeModule;
        if (preview && !isPreviewMode() && module != null && module.hasUnsavedChanges()) {
            ConfirmDialog dialog = ConfirmDialog.switchModeToPreview(
                module.getPendingChanges().size(),
                () -> {
                    switchToPreviewMode(true);
                    header.getModeBadge().setMode(ModeBadge.Mode.PREVIEW);
                    header.getModeBadge().closeDropdown();
                    saveUserModePreference();
                },
                () -> {
                    header.getModeBadge().setMode(ModeBadge.Mode.APPLY);
                    header.getModeBadge().closeDropdown();
                }
            );
            activeDialog = dialog;
            dialog.show();
            return;
        }

        if (preview) {
            switchToPreviewMode(false);
        } else {
            switchToApplyMode();
        }
        saveUserModePreference();
    }

    private void switchToPreviewMode(boolean discardChanges) {
        editorState.setPreviewMode(true);
        EditorModule module = activeModule;
        if (module != null) {
            if (discardChanges) {
                module.resetToOriginal();
                module.clearDirty();
            }
            module.applyPreview();
            module.logEvent(discardChanges
                ? "Switched to PREVIEW (discarded changes)"
                : "Switched to PREVIEW");
        }
        showStatus("Preview Mode", DesignTokens.Accent.PRIMARY);
    }

    private void switchToApplyMode() {
        if (localOnlyMode) {
            showStatus("Local kit edit only", DesignTokens.Semantic.WARNING);
            return;
        }
        editorState.setPreviewMode(false);
        EditorModule module = activeModule;
        if (module != null) {
            module.clearPreview();
            if (!module.hasUnsavedChanges() && module.hasPendingDiff()) {
                module.markDirty("Pending changes from preview");
            }
            module.logEvent("Switched to APPLY (dirty on)");
        }
        showStatus("Apply Mode", DesignTokens.Semantic.SUCCESS);
    }

    /**
     * Persist user preference for editor mode.
     */
    private void saveUserModePreference() {
        try {
            EditorClientConfig.EDITOR_DEFAULT_MODE.set(isPreviewMode() ? EditorClientConfig.EditorDefaultMode.PREVIEW : EditorClientConfig.EditorDefaultMode.APPLY);
            showStatus("Default mode saved", DesignTokens.Semantic.INFO);
        } catch (Exception ignored) {
            // Best-effort: config may be read-only in some contexts
        }
    }

    private void configureFooterCallbacks() {
        footer
            .onUndo(() -> {
                EditorModule module = activeModule;
                if (module != null && module.canUndo()) {
                    module.undo();
                    showStatus("Undone", DesignTokens.Semantic.INFO);
                }
            })
            .onRedo(() -> {
                EditorModule module = activeModule;
                if (module != null && module.canRedo()) {
                    module.redo();
                    showStatus("Redone", DesignTokens.Semantic.INFO);
                }
            })
            .onApply(this::applyChanges)
            .onAction(this::handleFooterAction);
    }

    private void handleFooterAction(String actionId) {
        if (actionId == null) return;
        switch (actionId) {
            case "reset" -> {
                EditorModule module = activeModule;
                if (module != null) {
                    ConfirmDialog dialog = ConfirmDialog.resetToDefault(() -> {
                        ItemEditorSnapshot before = captureSnapshot("reset", "Before reset");
                        module.resetToDefaults();
                        if (isPreviewMode()) {
                            module.applyPreview();
                        } else {
                            module.clearPreview();
                        }
                        ItemEditorSnapshot after = captureSnapshot("reset", "After reset");
                        recordHistoryEntry("reset", before, after);
                        showStatus("Reset to defaults", DesignTokens.Semantic.WARNING);
                        if (!localOnlyMode) {
                            sendResetPayload(module);
                        }
                    }, () -> {});
                    activeDialog = dialog;
                    dialog.show();
                }
            }
            case "cancel" -> handleCloseRequest();
            case "history" -> {
                overlayController.toggle(OverlayController.OverlayType.HISTORY);
                historyScrollOffset = 0;
            }
            case "export" -> handleExport();
            case "import" -> handleImport();
            case "templates" -> {
                openTemplatesOverlay();
            }
            case "recipe" -> {
                craftingPanel.show(getEditedItem());
                overlayController.toggle(OverlayController.OverlayType.CRAFTING);
            }
            default -> {}
        }
    }

    @Override
    public void closeOverlay() {
        overlayController.close();
    }

    /**
     * Called when overlay state changes - syncs UI components.
     */
    private void onOverlayChanged(OverlayController.OverlayType newOverlay) {
        TemplateOverlay tOverlay = templateOverlay;
        if (tOverlay != null) {
            if (newOverlay == OverlayController.OverlayType.TEMPLATES) {
                if (!tOverlay.isVisible()) {
                    tOverlay.show();
                }
            } else {
                tOverlay.resetSearch();
                tOverlay.hide();
            }
        }
        if (newOverlay == OverlayController.OverlayType.CRAFTING) {
            if (!craftingPanel.isVisible()) {
                craftingPanel.show(getEditedItem());
            }
        } else {
            craftingPanel.hide();
        }
        // Manage PresetSelectorOverlay visibility (disabled but kept for future)
        if (PRESETS_ENABLED) {
            PresetSelectorOverlay psOverlay = presetSelectorOverlay;
            if (psOverlay != null) {
                if (newOverlay == OverlayController.OverlayType.PRESETS) {
                    psOverlay.setContext(getActiveItemType());
                    psOverlay.show();
                } else {
                    psOverlay.resetSearch();
                    psOverlay.hide();
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        perfMonitor.startFrame();
        TooltipManager.INSTANCE.beginFrame(width, height);
        boolean blockBaseOverlays = hasBlockingOverlay();
        // Dark overlay background, but leave vanilla hotbar area unobscured
        int hotbarReserve = ScaledCoord.scaleDim(DesignTokens.Space._7);
        graphics.fill(0, 0, width, Math.max(0, height - hotbarReserve), DesignTokens.Bg.LEVEL_0);

        // Get layout areas (centralized in EditorLayout)
        var panelBounds = editorLayout.getPanelBounds();
        var headerBounds = editorLayout.getHeaderBounds();
        var footerBounds = editorLayout.getFooterBounds();
        var leftBounds = editorLayout.getLeftColumnBounds();
        var contentBounds = editorLayout.getContentBounds();
        int baseContentY = contentBounds.y();
        int baseContentHeight = contentBounds.height();
        int contentY = baseContentY;
        int footerY = footerBounds.y();
        int contentHeight = baseContentHeight;
        int bannerOffset = localOnlyMode ? getLocalOnlyBannerOffset() : 0;
        if (bannerOffset > 0) {
            contentY += bannerOffset;
            contentHeight = Math.max(0, contentHeight - bannerOffset);
        }

        // Main panel background
        graphics.fill(panelBounds.x(), panelBounds.y(),
                     panelBounds.right(), panelBounds.bottom(),
                     DesignTokens.Bg.LEVEL_2);

        // Panel border
        AxiomRenderer.drawBorder(graphics, panelBounds.x(), panelBounds.y(),
                                panelBounds.width(), panelBounds.height(),
                                DesignTokens.Stroke.DEFAULT);

        // Header (tabs + badges + close)
        header.render(graphics, headerBounds.x(), headerBounds.y(), headerBounds.width(), mouseX, mouseY);
        if (!blockBaseOverlays) {
            String modeTip = header.getModeBadge().getTooltipText();
            if (modeTip != null) {
                var bounds = header.getModeBadge().getBounds();
                TooltipManager.INSTANCE.queueTooltip(
                    modeTip, bounds.x(), bounds.y(), bounds.width(), bounds.height(), TooltipManager.TooltipPosition.AUTO);
            }
        }

        // Left column (preview + slots + info)
        EditorModule module = activeModule;
        if (module != null) {
            updateLeftColumnStats();
            leftColumn.pendingChanges(module.getPendingChanges().size());
        }
        leftColumn.render(graphics, leftBounds.x(), leftBounds.y(), leftBounds.width(), leftBounds.height(), mouseX, mouseY, partialTick);

        // Content area (scrollable)
        int contentX = contentBounds.x() + DesignTokens.Spacing.MD;
        int contentWidth = contentBounds.width() - DesignTokens.Spacing.MD * 2;
        graphics.fill(contentX, baseContentY, contentX + contentWidth, baseContentY + baseContentHeight,
            DesignTokens.Background.CONTENT());
        if (localOnlyMode) {
            renderLocalOnlyBanner(graphics, contentX, baseContentY, contentWidth);
        }
        if (module != null) {
            perfMonitor.startTiming("render_content");
            int viewportHeight = Math.max(0, contentHeight - DesignTokens.Spacing.MD * 2);
            EditorModule renderModule = module; // Capture for lambda
            scrollArea.render(graphics, contentX, contentY, contentWidth, contentHeight,
                mouseX, mouseY, partialTick, (g, x, y, w, mx, my) -> {
                    ResponsiveLayout.Rect bounds = new ResponsiveLayout.Rect(x, y, w, viewportHeight);
                    // Pass raw mouseY (screen space) - render Y coordinates are also screen space
                    // Scroll adjustment is only needed for click handling, not hover detection
                    renderModule.renderContent(g, bounds, mx, my);
                    return renderModule.calculateContentHeight();
                });
            perfMonitor.endTiming("render_content");
        }

        // Render multi-edit panel if visible (placed beneath left column area)
        MultiEditPanel mePanel = multiEditPanel;
        if (showMultiEditPanel && mePanel != null) {
            int panelX = leftBounds.x() + DesignTokens.Spacing.MD;
            int panelY = contentY + DesignTokens.Spacing.MD;
            int panelW = leftBounds.width() - DesignTokens.Spacing.MD * 2;
            mePanel.render(graphics, font, panelX, panelY, panelW, mouseX, mouseY);
        }

        // Footer
        int pending = module != null ? module.getPendingChanges().size() : 0;
        boolean dirty = module != null && (module.hasUnsavedChanges() || pending > 0);
        footer
            .canUndo(module != null && module.canUndo())
            .canRedo(module != null && module.canRedo())
            .canApply(module != null)
            .isDirty(dirty)
            .pendingCount(pending)
            .applyLabel(localOnlyMode ? "Save to Kit" : null);
        footer.render(graphics, panelBounds.x(), footerY, panelBounds.width(), mouseX, mouseY);

        // Render side panels if visible
        if (layout.showSidePanels()) {
            renderSidePanels(graphics, mouseX, mouseY, !blockBaseOverlays);
        }

        // Status message overlay
        if (statusMessage != null && statusTicks > 0) {
            renderStatusMessage(graphics);
        }

        if (blockBaseOverlays) {
            header.getModeBadge().closeDropdown();
            TooltipManager.INSTANCE.beginFrame(width, height);
        }

        if (overlayController.isHistoryVisible() && activeModule != null) {
            renderHistoryPanel(graphics, mouseX, mouseY);
        }

        // Dev panel
        if (showDevPanel) {
            renderDevPanel(graphics, mouseX, mouseY);
        }

        if (!blockBaseOverlays) {
            // Render header dropdown overlays above base content when no modal overlay is active
            header.renderOverlays(graphics, mouseX, mouseY);
        }

        if (PRESETS_ENABLED) {
            PresetSelectorOverlay psOverlay = presetSelectorOverlay;
            if (overlayController.isPresetsVisible() && supportsDataOps() && psOverlay != null) {
                psOverlay.render(graphics, font, width, height, mouseX, mouseY);
            }
        }
        TemplateOverlay tOverlay = templateOverlay;
        if (overlayController.isTemplatesVisible() && tOverlay != null) {
            tOverlay.render(graphics, font, width, height, mouseX, mouseY);
        }
        if (overlayController.isCraftingVisible()) {
            craftingPanel.render(graphics, font, width, height, mouseX, mouseY);
        }

        // Module overlay (e.g., ItemPickerOverlay from RecipeModule)
        // Rendered after other overlays but before modal dialogs
        EditorModule overlayModule = activeModule;
        if (overlayModule != null && overlayModule.hasActiveOverlay()) {
            overlayModule.renderOverlay(graphics, font, width, height, mouseX, mouseY);
        }

        // Modal dialog (rendered last, on top of everything)
        ConfirmDialog dialog = activeDialog;
        if (dialog != null && dialog.isVisible()) {
            dialog.render(graphics, font, width, height, mouseX, mouseY);
        }

        // Help overlay (rendered on top of dialogs)
        if (helpOverlay.isVisible()) {
            helpOverlay.render(graphics, font, width, height, mouseX, mouseY);
        }

        // Low-confidence dialog
        if (lowConfidenceDetector.isVisible()) {
            lowConfidenceDetector.render(graphics, font, width, height, mouseX, mouseY);
        }

        // Performance/Debug overlay (rendered on top of everything)
        perfMonitor.endRender();
        var perf = perfMonitor.getMetrics();
        DebugOverlay.setPerformanceLine(String.format("Frame %.2fms | Render %.2fms | Entities %.0f | %s",
            perf.frameTime(), perf.renderTime(), perf.entityCount(), perf.bottleneck()));
        var perfPanelBounds = editorLayout.getPanelBounds();
        int contentTotalHeight = activeModule == null ? 0 : activeModule.calculateContentHeight();
        int sectionCount = activeModule == null ? 0 : activeModule.getSections().size();
        DebugOverlay.render(graphics, font, perfPanelBounds, headerBounds, leftBounds, contentBounds, footerBounds,
            mouseX, mouseY, contentTotalHeight, scrollArea.getScrollOffset(), sectionCount);
        TooltipManager.INSTANCE.renderAll(graphics);
    }

    private void renderSidePanels(GuiGraphics graphics, int mouseX, int mouseY, boolean allowTooltips) {
        var safeFont = Objects.requireNonNull(font, "font cannot be null");

        // Left panel (favorites)
        ResponsiveLayout.Rect leftPanel = PRESETS_ENABLED ? layout.getFavoritesPanelArea() : ResponsiveLayout.Rect.EMPTY;
        if (!leftPanel.isEmpty()) {
            graphics.fill(leftPanel.x(), leftPanel.y(), leftPanel.right(), leftPanel.bottom(),
                         DesignTokens.Bg.LEVEL_2);
            AxiomRenderer.drawBorder(graphics, leftPanel.x(), leftPanel.y(),
                                    leftPanel.width(), leftPanel.height(), DesignTokens.Stroke.DEFAULT);
            graphics.drawString(safeFont, FAVORITES_TITLE_TEXT, leftPanel.x() + FAVORITES_TITLE_OFFSET_X,
                leftPanel.y() + FAVORITES_TITLE_OFFSET_Y,
                               DesignTokens.Text.SECONDARY(), false);

            List<ItemEditorDataManager.PresetData> favorites = getFavoritePresetsForActiveType();
            int rowHeight = FAVORITES_ROW_HEIGHT;
            int startY = leftPanel.y() + FAVORITES_LIST_START_Y;
            int maxRows = Math.max(0, (leftPanel.height() - FAVORITES_LIST_BOTTOM_PADDING) / rowHeight);
            for (int i = 0; i < Math.min(maxRows, favorites.size()); i++) {
                ItemEditorDataManager.PresetData preset = favorites.get(i);
                int rowY = startY + i * rowHeight;
                String label = preset.name == null ? FAVORITES_LABEL_FALLBACK : preset.name;
                boolean hovered = mouseX >= leftPanel.x() + FAVORITES_ROW_HIT_INSET
                    && mouseX <= leftPanel.right() - FAVORITES_ROW_HIT_INSET
                    && mouseY >= rowY && mouseY <= rowY + rowHeight;
                if (hovered) {
                    graphics.fill(leftPanel.x() + FAVORITES_ROW_HOVER_INSET, rowY,
                        leftPanel.right() - FAVORITES_ROW_HOVER_INSET, rowY + rowHeight, DesignTokens.Background.HOVER());
                    if (allowTooltips) {
                        TooltipManager.INSTANCE.queueTooltip(
                            label, leftPanel.x(), rowY, leftPanel.width(), rowHeight, TooltipManager.TooltipPosition.AUTO);
                    }
                }
                graphics.drawString(safeFont, FAVORITES_LABEL_PREFIX + label, leftPanel.x() + FAVORITES_ROW_TEXT_OFFSET_X,
                    rowY + FAVORITES_ROW_TEXT_OFFSET_Y,
                    hovered ? DesignTokens.Text.PRIMARY() : DesignTokens.Text.SECONDARY(), false);
            }

            // Pin toggle for last loaded preset
            if (lastLoadedPreset != null) {
                boolean isFav = favoriteStore.isFavorite(getActiveItemType(), lastLoadedPreset);
                String pinLabel = isFav ? FAVORITES_UNPIN_TEXT : FAVORITES_PIN_TEXT;
                int btnY = leftPanel.bottom() - FAVORITES_PIN_BUTTON_OFFSET_Y;
                int btnW = FAVORITES_PIN_BUTTON_WIDTH;
                int btnH = FAVORITES_PIN_BUTTON_HEIGHT;
                int btnX = leftPanel.x() + FAVORITES_PIN_BUTTON_OFFSET_X;
                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, DesignTokens.Button.NORMAL());
                AxiomRenderer.drawBorder(graphics, btnX, btnY, btnW, btnH, DesignTokens.Stroke.DEFAULT);
                graphics.drawString(safeFont, pinLabel, btnX + FAVORITES_PIN_TEXT_OFFSET_X,
                    btnY + FAVORITES_PIN_TEXT_OFFSET_Y, DesignTokens.Text.PRIMARY(), false);
            }
        }

        // Right panel (dev mode)
        if (showDevPanel) {
            ResponsiveLayout.Rect rightPanel = layout.getDevModePanelArea();
            if (!rightPanel.isEmpty()) {
                graphics.fill(rightPanel.x(), rightPanel.y(), rightPanel.right(), rightPanel.bottom(),
                             DesignTokens.Bg.LEVEL_2);
                AxiomRenderer.drawBorder(graphics, rightPanel.x(), rightPanel.y(),
                                        rightPanel.width(), rightPanel.height(), DesignTokens.Stroke.DEFAULT);
                graphics.drawString(safeFont, DEV_PANEL_HEADER_TEXT, rightPanel.x() + DEV_PANEL_TEXT_OFFSET_X,
                    rightPanel.y() + DEV_PANEL_TEXT_OFFSET_Y,
                                   DesignTokens.Text.SECONDARY(), false);
            }
        }
    }

    @Override
    public void refreshMultiEditSelection() {
        if (localOnlyMode) {
            return;
        }
        var manager = multiEditManager;
        if (manager == null) return;
        manager.clearSelection();
        var mc = Minecraft.getInstance();
        if (mc == null) return;
        var player = mc.player;
        if (player == null) return;
        var inv = player.getInventory();
        var targetItem = Objects.requireNonNull(getEditedItem().getItem(), "item type cannot be null");
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (!stack.isEmpty() && stack.getItem() == targetItem) {
                manager.addToSelection(stack, i);
            }
        }
    }

    private void renderStatusMessage(GuiGraphics graphics) {
        var safeFont = Objects.requireNonNull(font, "font cannot be null");
        String safeMessage = Objects.requireNonNull(statusMessage, "statusMessage cannot be null");
        int msgWidth = safeFont.width(safeMessage) + STATUS_MESSAGE_PADDING_X * 2;
        int msgX = (width - msgWidth) / 2;
        int msgY = height - STATUS_MESSAGE_BOTTOM_OFFSET;
        float fontScale = Typography.withUiScale(1.0f);

        int paddingX = STATUS_MESSAGE_PADDING_X;
        int paddingY = STATUS_MESSAGE_PADDING_Y;
        int msgHeight = safeFont.lineHeight + paddingY * 2;
        msgWidth = safeFont.width(safeMessage) + paddingX * 2;
        msgX = Math.max(STATUS_MESSAGE_SCREEN_MARGIN, Math.min(width - msgWidth - STATUS_MESSAGE_SCREEN_MARGIN, msgX));

        graphics.fill(msgX, msgY, msgX + msgWidth, msgY + msgHeight, STATUS_MESSAGE_BG);
        AxiomRenderer.drawBorder(graphics, msgX, msgY, msgWidth, msgHeight, statusColor);
        Typography.drawText(graphics, safeFont, safeMessage, msgX + paddingX, msgY + paddingY, statusColor, fontScale);
    }

    private void renderHistoryPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font, "font cannot be null");
        ResponsiveLayout.Rect historyBounds = getHistoryPanelBounds();
        int panelWidth = historyBounds.width();
        int panelHeight = historyBounds.height();
        int x = historyBounds.x();
        int y = historyBounds.y();

        graphics.fill(x, y, x + panelWidth, y + panelHeight, DesignTokens.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, x, y, panelWidth, panelHeight, DesignTokens.Stroke.DEFAULT);

        graphics.drawString(safeFont, HISTORY_TITLE_TEXT, x + HISTORY_PANEL_TITLE_OFFSET_X, y + HISTORY_PANEL_TITLE_OFFSET_Y,
            DesignTokens.Text.TITLE(), false);
        renderHistoryFilters(graphics, safeFont, x, y, mouseX, mouseY);
        int listY = y + HISTORY_PANEL_LIST_OFFSET_Y + HISTORY_FILTER_HEIGHT;
        List<ItemEditorHistoryEntry> entries = getFilteredHistoryEntries();
        int lineHeight = HISTORY_PANEL_LINE_HEIGHT;
        int listHeight = panelHeight - HISTORY_PANEL_LIST_HEIGHT_PADDING - HISTORY_FILTER_HEIGHT;
        int maxScroll = Math.max(0, Math.max(0, entries.size() * lineHeight - listHeight));
        historyScrollOffset = Math.max(0, Math.min(historyScrollOffset, maxScroll));

        int visibleLines = listHeight / lineHeight;
        if (entries.isEmpty()) {
            graphics.drawString(safeFont, HISTORY_EMPTY_TEXT, x + HISTORY_PANEL_TEXT_OFFSET_X, listY,
                DesignTokens.Text.MUTED(), false);
        } else {
            int startIndex = Math.max(0, historyScrollOffset / lineHeight);
            int endIndex = Math.min(entries.size(), startIndex + visibleLines);

            for (int i = startIndex; i < endIndex; i++) {
                ItemEditorHistoryEntry entry = entries.get(i);
                int entryY = listY + (i - startIndex) * lineHeight;
                boolean hovered = mouseX >= x && mouseX <= x + panelWidth
                    && mouseY >= entryY && mouseY <= entryY + lineHeight;
                boolean selected = i == historySelectedIndex;
                if (selected) {
                    graphics.fill(x + 2, entryY - 1, x + panelWidth - 2, entryY + lineHeight - 1,
                        DesignTokens.Background.ACTIVE());
                } else if (hovered) {
                    graphics.fill(x + 2, entryY - 1, x + panelWidth - 2, entryY + lineHeight - 1,
                        DesignTokens.Background.HOVER());
                }
                String label = entry.formatLabel();
                graphics.drawString(safeFont, label, x + HISTORY_PANEL_TEXT_OFFSET_X, entryY,
                    selected ? DesignTokens.Text.PRIMARY() : DesignTokens.Text.SECONDARY(), false);
                if (hovered) {
                    TooltipManager.INSTANCE.queueTooltip(
                        entry.formatDiffSummary(),
                        x + HISTORY_PANEL_TEXT_OFFSET_X,
                        entryY,
                        panelWidth - HISTORY_PANEL_TEXT_OFFSET_X * 2,
                        lineHeight,
                        TooltipManager.TooltipPosition.AUTO
                    );
                }
            }
        }

        // Footer with clear button and counter
        int footerY = y + panelHeight - HISTORY_PANEL_FOOTER_OFFSET_Y;
        String countText = entries.isEmpty()
            ? HISTORY_SHOWING_EMPTY
            : HISTORY_SHOWING_PREFIX + Math.min(visibleLines, entries.size()) + HISTORY_SHOWING_SEPARATOR + entries.size();
        graphics.drawString(safeFont, countText, x + HISTORY_PANEL_TEXT_OFFSET_X, footerY,
            DesignTokens.Text.MUTED(), false);

        // Clear button
        int clearW = HISTORY_PANEL_CLEAR_WIDTH;
        int clearH = HISTORY_PANEL_CLEAR_HEIGHT;
        int clearX = x + panelWidth - clearW - HISTORY_PANEL_CLEAR_OFFSET_X;
        int clearY = footerY - HISTORY_PANEL_CLEAR_OFFSET_Y;
        int clearBg = entries.isEmpty() ? DesignTokens.Button.DISABLED() : DesignTokens.Background.INPUT();
        graphics.fill(clearX, clearY, clearX + clearW, clearY + clearH, clearBg);
        AxiomRenderer.drawBorder(graphics, clearX, clearY, clearW, clearH, DesignTokens.Stroke.DEFAULT);
        int clearColor = entries.isEmpty() ? DesignTokens.Text.DISABLED() : DesignTokens.Text.PRIMARY();
        graphics.drawString(safeFont, HISTORY_CLEAR_TEXT, clearX + HISTORY_PANEL_CLEAR_TEXT_OFFSET_X,
            clearY + HISTORY_PANEL_CLEAR_TEXT_OFFSET_Y, clearColor, false);

        // Rollback button
        int rollbackW = HISTORY_PANEL_ROLLBACK_WIDTH;
        int rollbackH = HISTORY_PANEL_ROLLBACK_HEIGHT;
        int rollbackX = clearX - rollbackW - HISTORY_PANEL_ROLLBACK_OFFSET_X;
        int rollbackY = clearY;
        boolean canRollback = historySelectedIndex >= 0 && historySelectedIndex < entries.size();
        historyRollbackBounds = new ResponsiveLayout.Rect(rollbackX, rollbackY, rollbackW, rollbackH);
        int rollbackBg = canRollback ? DesignTokens.Background.INPUT() : DesignTokens.Button.DISABLED();
        graphics.fill(rollbackX, rollbackY, rollbackX + rollbackW, rollbackY + rollbackH, rollbackBg);
        AxiomRenderer.drawBorder(graphics, rollbackX, rollbackY, rollbackW, rollbackH, DesignTokens.Stroke.DEFAULT);
        int rollbackColor = canRollback ? DesignTokens.Text.PRIMARY() : DesignTokens.Text.DISABLED();
        graphics.drawString(safeFont, HISTORY_ROLLBACK_TEXT, rollbackX + HISTORY_PANEL_ROLLBACK_TEXT_OFFSET_X,
            rollbackY + HISTORY_PANEL_ROLLBACK_TEXT_OFFSET_Y, rollbackColor, false);
    }

    private void renderHistoryFilters(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                      int x, int y, int mouseX, int mouseY) {
        historyFilterHits.clear();
        int cursorX = x + HISTORY_PANEL_TITLE_OFFSET_X;
        int cursorY = y + HISTORY_PANEL_TITLE_OFFSET_Y + HISTORY_FILTER_OFFSET_Y;
        for (HistoryFilter filter : HistoryFilter.values()) {
            String label = filter.label;
            int width = font.width(label) + 10;
            ResponsiveLayout.Rect rect = new ResponsiveLayout.Rect(cursorX, cursorY, width, HISTORY_FILTER_HEIGHT);
            boolean selected = historyFilter == filter;
            boolean hovered = rect.contains(mouseX, mouseY);
            int bg = selected ? DesignTokens.Background.ACTIVE() : (hovered ? DesignTokens.Background.HOVER() : DesignTokens.Background.INPUT());
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), bg);
            AxiomRenderer.drawBorder(graphics, rect.x(), rect.y(), rect.width(), rect.height(), DesignTokens.Stroke.DEFAULT);
            int color = selected ? DesignTokens.Text.PRIMARY() : DesignTokens.Text.SECONDARY();
            graphics.drawString(font, label, rect.x() + 5, rect.y() + 2, color, false);
            historyFilterHits.add(new HistoryFilterHit(filter, rect));
            cursorX = rect.right() + HISTORY_FILTER_GAP;
        }
    }

    private List<ItemEditorHistoryEntry> getFilteredHistoryEntries() {
        if (historyFilter == HistoryFilter.ALL) {
            return historyEntries;
        }
        List<ItemEditorHistoryEntry> filtered = new ArrayList<>();
        for (ItemEditorHistoryEntry entry : historyEntries) {
            if (entry == null) continue;
            if (historyFilter.matches(entry.action)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    @Override
    public boolean handleHistoryClick(double mouseX, double mouseY) {
        ResponsiveLayout.Rect historyBounds = getHistoryPanelBounds();
        int panelWidth = historyBounds.width();
        int panelHeight = historyBounds.height();
        int x = historyBounds.x();
        int y = historyBounds.y();

        // Click outside closes panel
        if (mouseX < x || mouseX > x + panelWidth || mouseY < y || mouseY > y + panelHeight) {
            overlayController.closeAll();
            return true;
        }

        for (HistoryFilterHit hit : historyFilterHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                historyFilter = hit.filter;
                historySelectedIndex = -1;
                return true;
            }
        }

        List<ItemEditorHistoryEntry> entries = getFilteredHistoryEntries();
        int lineHeight = HISTORY_PANEL_LINE_HEIGHT;
        int listY = y + HISTORY_PANEL_LIST_OFFSET_Y + HISTORY_FILTER_HEIGHT;
        int listHeight = panelHeight - HISTORY_PANEL_LIST_HEIGHT_PADDING - HISTORY_FILTER_HEIGHT;
        int visibleLines = listHeight / lineHeight;
        int startIndex = Math.max(0, historyScrollOffset / lineHeight);
        int endIndex = Math.min(entries.size(), startIndex + visibleLines);
        for (int i = startIndex; i < endIndex; i++) {
            int entryY = listY + (i - startIndex) * lineHeight;
            if (mouseY >= entryY && mouseY <= entryY + lineHeight) {
                historySelectedIndex = i;
                return true;
            }
        }

        if (historyRollbackBounds.contains(mouseX, mouseY)) {
            if (historySelectedIndex >= 0 && historySelectedIndex < entries.size()) {
                ItemEditorHistoryEntry entry = entries.get(historySelectedIndex);
                ConfirmDialog dialog = ConfirmDialog.create(
                    "Rollback",
                    "Rollback",
                    "Cancel",
                    ConfirmDialog.Style.WARNING,
                    () -> {
                        ItemEditorSnapshot before = captureSnapshot("rollback", "Before rollback");
                        applySnapshotToModule(entry.after, "Rollback to " + entry.action);
                        ItemEditorSnapshot after = captureSnapshot("rollback", "After rollback");
                        recordHistoryEntry("rollback", before, after);
                        baselineSnapshot = after;
                        showStatus("Rolled back", DesignTokens.Semantic.INFO);
                    },
                    () -> {},
                    "Rollback to the selected step?"
                );
                activeDialog = dialog;
                dialog.show();
                return true;
            }
        }

        // Clear button
        int clearW = HISTORY_PANEL_CLEAR_WIDTH;
        int clearH = HISTORY_PANEL_CLEAR_HEIGHT;
        int footerY = y + panelHeight - HISTORY_PANEL_FOOTER_OFFSET_Y;
        int clearX = x + panelWidth - clearW - HISTORY_PANEL_CLEAR_OFFSET_X;
        int clearY = footerY - HISTORY_PANEL_CLEAR_OFFSET_Y;
        if (mouseX >= clearX && mouseX <= clearX + clearW && mouseY >= clearY && mouseY <= clearY + clearH) {
            historyEntries.clear();
            historyScrollOffset = 0;
            historySelectedIndex = -1;
            return true;
        }

        return false;
    }

    @Override
    public boolean isPointInHistoryPanel(double mouseX, double mouseY) {
        return getHistoryPanelBounds().contains(mouseX, mouseY);
    }

    private ResponsiveLayout.Rect getHistoryPanelBounds() {
        int x = layout.getEditorX() + layout.getEditorWidth() - HISTORY_PANEL_WIDTH - HISTORY_PANEL_MARGIN_RIGHT;
        int y = layout.getEditorY() + DesignTokens.Size.HEADER_HEIGHT + HISTORY_PANEL_MARGIN_TOP;
        return new ResponsiveLayout.Rect(x, y, HISTORY_PANEL_WIDTH, HISTORY_PANEL_HEIGHT);
    }

    @Override
    public void scrollHistory(int delta) {
        historyScrollOffset += delta * HISTORY_PANEL_LINE_HEIGHT;
        historyScrollOffset = Math.max(0, historyScrollOffset);
    }

    private void renderDevPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        ResponsiveLayout.Rect devArea = layout.getDevModePanelArea();
        if (devArea.isEmpty()) {
            devArea = new ResponsiveLayout.Rect(
                width - DEV_PANEL_FALLBACK_WIDTH - DEV_PANEL_FALLBACK_MARGIN_RIGHT,
                DEV_PANEL_FALLBACK_MARGIN_TOP,
                DEV_PANEL_FALLBACK_WIDTH,
                DEV_PANEL_FALLBACK_HEIGHT
            );
        }

        // Delegate to DebugPanel for richer info
        DebugPanel panel = debugPanel;
        if (panel != null) {
            // Prefer preview item when preview mode is active so debug reflects the preview state
            ItemStack displayItem = resolvePreviewItem(getEditedItem());
            panel.setStatSources(buildStatSources(displayItem));
            panel.setStatDiffs(buildStatDiffs());
            panel.render(graphics, font, devArea.x(), devArea.y(), devArea.width(), devArea.height(), mouseX, mouseY, displayItem);
            return;
        }

        var safeFont = Objects.requireNonNull(font, "font cannot be null");
        graphics.fill(devArea.x(), devArea.y(), devArea.right(), devArea.bottom(), DEV_PANEL_BG);
        AxiomRenderer.drawBorder(graphics, devArea.x(), devArea.y(), devArea.width(), devArea.height(), DesignTokens.Accent.PRIMARY);

        int textY = devArea.y() + DEV_PANEL_TEXT_OFFSET_Y;
        graphics.drawString(safeFont, DEV_PANEL_TITLE_TEXT, devArea.x() + DEV_PANEL_TEXT_OFFSET_X, textY,
            DEV_PANEL_TITLE_COLOR, false);
        textY += DEV_PANEL_TITLE_LINE_STEP;

        EditorCache.CacheStats stats = EditorCache.INSTANCE.getStats();
        graphics.drawString(safeFont, "Cache: " + stats.valid() + "/" + stats.total(), devArea.x() + DEV_PANEL_TEXT_OFFSET_X,
            textY, DesignTokens.Text.SECONDARY(), false);
        textY += DEV_PANEL_LINE_STEP;
        graphics.drawString(safeFont, String.format("Hit: %.1f%%", stats.hitRate() * 100), devArea.x() + DEV_PANEL_TEXT_OFFSET_X,
            textY, DesignTokens.Text.SECONDARY(), false);
        textY += DEV_PANEL_LINE_STEP;

        graphics.drawString(safeFont, "Size: " + layout.getScreenSize(), devArea.x() + DEV_PANEL_TEXT_OFFSET_X,
            textY, DesignTokens.Text.SECONDARY(), false);
        textY += DEV_PANEL_LINE_STEP;
        graphics.drawString(safeFont, "Scroll: " + (int) scrollArea.getScrollOffset(), devArea.x() + DEV_PANEL_TEXT_OFFSET_X,
            textY, DesignTokens.Text.SECONDARY(), false);
    }

    private int getLocalOnlyBannerOffset() {
        int height = ScaledCoord.scaleDim(LOCAL_ONLY_BANNER_HEIGHT);
        int margin = ScaledCoord.scaleDim(LOCAL_ONLY_BANNER_MARGIN);
        return height + margin * 2;
    }

    private void renderLocalOnlyBanner(GuiGraphics graphics, int x, int y, int width) {
        var safeFont = Objects.requireNonNull(font, "font cannot be null");
        int margin = ScaledCoord.scaleDim(LOCAL_ONLY_BANNER_MARGIN);
        int bannerHeight = ScaledCoord.scaleDim(LOCAL_ONLY_BANNER_HEIGHT);
        int padX = ScaledCoord.scaleDim(LOCAL_ONLY_BANNER_PADDING_X);
        int bannerX = x + margin;
        int bannerY = y + margin;
        int bannerWidth = Math.max(0, width - margin * 2);

        graphics.fill(bannerX, bannerY, bannerX + bannerWidth, bannerY + bannerHeight, LOCAL_ONLY_BANNER_BG);
        AxiomRenderer.drawBorder(graphics, bannerX, bannerY, bannerWidth, bannerHeight, LOCAL_ONLY_BANNER_BORDER);

        String text = LOCAL_ONLY_BANNER_TEXT;
        int textY = bannerY + Math.max(0, (bannerHeight - safeFont.lineHeight) / 2);
        graphics.drawString(safeFont, text, bannerX + padX, textY, DesignTokens.Text.PRIMARY(), false);
    }

    private boolean hasBlockingOverlay() {
        ConfirmDialog dialog = activeDialog;
        if (dialog != null && dialog.isVisible()) {
            return true;
        }
        if (helpOverlay.isVisible() || lowConfidenceDetector.isVisible()) {
            return true;
        }
        if (overlayController.hasActiveOverlay()) {
            return true;
        }
        EditorModule module = activeModule;
        return module != null && module.hasActiveOverlay();
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return inputRouter.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return inputRouter.mouseReleased(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Minimal hotbar interaction while the editor screen is open.
     * Left-click to pick up / place items using the carried stack semantics.
     */
    @Override
    public boolean handleHotbarClick(double mouseX, double mouseY, int button) {
        return handleHotbarClickInternal(mouseX, mouseY, button, false);
    }

    /**
     * Hotbar release for drag-and-drop scenarios (place only mode).
     */
    @Override
    public boolean handleHotbarRelease(double mouseX, double mouseY, int button) {
        return handleHotbarClickInternal(mouseX, mouseY, button, true);
    }

    /**
     * @param placeOnly when true, only process if there's a carried stack (for drag-release scenarios)
     */
    private boolean handleHotbarClickInternal(double mouseX, double mouseY, int button, boolean placeOnly) {
        if (button != 0) return false;
        if (localOnlyMode) return false;
        var player = Minecraft.getInstance().player;
        if (player == null) return false;

        // Mirror the vanilla HUD placement: centered at bottom, 9 slots
        final int hotbarWidth = HOTBAR_WIDTH;
        final int slotSize = HOTBAR_SLOT_SIZE;
        final int startX = (this.width - hotbarWidth) / 2;
        final int startY = this.height - HOTBAR_BOTTOM_OFFSET;

        for (int i = 0; i < HOTBAR_SLOT_COUNT; i++) {
            int slotX = startX + i * slotSize;
            if (mouseX >= slotX && mouseX < slotX + slotSize && mouseY >= startY && mouseY < startY + slotSize) {
                var inv = player.getInventory();
                ItemStack slotStack = Objects.requireNonNull(inv.getItem(i), "hotbar slot cannot be null");
                ItemStack carried = Objects.requireNonNull(player.containerMenu.getCarried(), "carried stack cannot be null");

                if (placeOnly && carried.isEmpty()) {
                    return false;
                }

                if (carried.isEmpty()) {
                    player.containerMenu.setCarried(Objects.requireNonNull(slotStack.copy(), "copy cannot be null"));
                    inv.setItem(i, Objects.requireNonNull(ItemStack.EMPTY, "empty cannot be null"));
                } else {
                    ItemStack carriedCopy = Objects.requireNonNull(carried.copy(), "copy cannot be null");
                    ItemStack slotCopy = Objects.requireNonNull(slotStack.copy(), "copy cannot be null");
                    inv.setItem(i, carriedCopy);
                    player.containerMenu.setCarried(slotCopy);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return inputRouter.mouseDragged(mouseX, mouseY, button, dragX, dragY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return inputRouter.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return inputRouter.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean forceCloseAllOverlays() {
        boolean closed = false;

        EditorModule module = activeModule;
        if (module != null && module.hasActiveOverlay()) {
            module.overlayKeyPressed(GLFW.GLFW_KEY_ESCAPE);
            closed = true;
        }

        if (overlayController.hasActiveOverlay()) {
            overlayController.closeAll();
            closed = true;
        }

        TemplateOverlay tOverlay = templateOverlay;
        if (tOverlay != null && tOverlay.isVisible()) {
            tOverlay.hide();
            closed = true;
        }

        PresetSelectorOverlay psOverlay = presetSelectorOverlay;
        if (psOverlay != null && psOverlay.isVisible()) {
            psOverlay.hide();
            closed = true;
        }

        if (craftingPanel.isVisible()) {
            craftingPanel.hide();
            closed = true;
        }

        ConfirmDialog dialog = activeDialog;
        if (dialog != null && dialog.isVisible()) {
            dialog.hide();
            closed = true;
        }

        if (helpOverlay.isVisible()) {
            helpOverlay.hide();
            closed = true;
        }

        if (lowConfidenceDetector.isVisible()) {
            lowConfidenceDetector.reset();
            closed = true;
        }

        return closed;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return inputRouter.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return inputRouter.keyReleased(keyCode, scanCode, modifiers) || super.keyReleased(keyCode, scanCode, modifiers);
    }

    // ═══════════════════════════════════════════════════════════════
    // ACTIONS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void applyChanges() {
        if (localOnlyMode) {
            closeEditor(true);
            return;
        }
        EditorModule module = activeModule;
        if (module == null) return;
        ItemStack workingItem = module.getItem();
        if (workingItem == null || workingItem.isEmpty()) {
            showStatus("Apply failed: no item selected", DesignTokens.Semantic.ERROR);
            module.logEvent("Apply skipped: empty item");
            return;
        }

        if (!module.hasUnsavedChanges()) {
            showStatus("No changes to apply", DesignTokens.Semantic.WARNING);
            module.logEvent("Apply skipped (no changes)");
            return;
        }

        try {
            ItemEditorSnapshot before = resolveBaselineSnapshot();
            module.logEvent("Apply requested (" + (isGlobalMode() ? "GLOBAL" : "SPECIFIC") + ")");
            // Build and send payload
            CustomPacketPayload payload;
            if (module instanceof com.devmod.client.ui.editor.modules.ArmorModule armorModule) {
                ArmorStats stats = armorModule.getStats().copy();
                CompoundTag statsTag = new CompoundTag();
                CompoundTag armorStats = new CompoundTag();
                stats.save(armorStats);
                statsTag.put(ARMOR_STATS_KEY, Objects.requireNonNull(armorStats.copy()));
                statsTag.put(ARMOR_STATS_COMPONENT_KEY, Objects.requireNonNull(armorStats));

                int slotIndex = -1;
                SlotSelector.SlotInfo slot = getSelectedSlotInfo();
                if (!isGlobalMode() && slot != null && slot.slot() != null) {
                    slotIndex = switch (slot.slot()) {
                        case HEAD -> 0;
                        case CHEST -> 1;
                        case LEGS -> 2;
                        case FEET -> 3;
                        default -> -1;
                    };
                }
                ItemStack armorItem = Objects.requireNonNull(armorModule.getItem(), "armor item");
                payload = new ArmorStatsPayload(Objects.requireNonNull(armorItem.copy()), statsTag, isGlobalMode(), slotIndex);
            } else {
                payload = module.buildPayload(isGlobalMode());
            }
            if (payload == null) {
                showStatus("Apply not available for this module (no payload)", DesignTokens.Semantic.WARNING);
                module.logEvent("Apply skipped: no payload");
                return;
            }
            PacketDistributor.sendToServer(payload);
            module.logEvent("Waiting for server confirm...");

            // Apply preview locally to reflect new state
            module.applyPreview();

            // Clear dirty state
            module.clearDirty();
            lastSaveTimestamp = System.currentTimeMillis();

            // Invalidate cache
            EditorCache.INSTANCE.invalidateAll();

            playSound(SoundEvents.UI_BUTTON_CLICK.value());
            showStatus("Changes applied!", DesignTokens.Semantic.SUCCESS);
            module.logEvent("Apply sent to server");
            ItemEditorSnapshot after = captureSnapshot("apply", "Apply");
            recordHistoryEntry("apply", before, after);
            baselineSnapshot = after;

        } catch (Exception e) {
            showStatus("Failed to apply: " + e.getMessage(), DesignTokens.Semantic.ERROR);
            EditorModule errorModule = activeModule;
            if (errorModule != null) {
                errorModule.logEvent("Apply error: " + e.getMessage());
            }
        }
    }

    private void sendResetPayload(EditorModule module) {
        if (module == null) return;
        if (localOnlyMode) return;

        CompoundTag resetTag = new CompoundTag();
        resetTag.putBoolean("reset", true);
        CustomPacketPayload payload = null;

        if (module instanceof com.devmod.client.ui.editor.modules.ArmorModule armorModule) {
            int slotIndex = -1;
            SlotSelector.SlotInfo slot = getSelectedSlotInfo();
            if (!isGlobalMode() && slot != null && slot.slot() != null) {
                slotIndex = switch (slot.slot()) {
                    case HEAD -> 0;
                    case CHEST -> 1;
                    case LEGS -> 2;
                    case FEET -> 3;
                    default -> -1;
                };
            }
            ItemStack armorItem = Objects.requireNonNull(armorModule.getItem(), "armor item");
            payload = new ArmorStatsPayload(Objects.requireNonNull(armorItem.copy()), resetTag, isGlobalMode(), slotIndex);
        } else if (module instanceof com.devmod.client.ui.editor.modules.WeaponModule weaponModule) {
            ItemStack weaponItem = Objects.requireNonNull(weaponModule.getItem(), "weapon item");
            payload = new com.devmod.network.WeaponStatsPayload(Objects.requireNonNull(weaponItem.copy()), resetTag, isGlobalMode());
        } else if (module instanceof com.devmod.client.ui.editor.modules.RangedModule rangedModule) {
            ItemStack rangedItem = Objects.requireNonNull(rangedModule.getItem(), "ranged item");
            payload = new com.devmod.network.RangedWeaponStatsPayload(Objects.requireNonNull(rangedItem.copy()), resetTag, isGlobalMode());
        } else if (module instanceof com.devmod.client.ui.editor.modules.FoodModule foodModule) {
            ItemStack foodItem = Objects.requireNonNull(foodModule.getItem(), "food item");
            payload = new com.devmod.network.FoodStatsPayload(Objects.requireNonNull(foodItem.copy()), resetTag, isGlobalMode());
        } else if (module instanceof com.devmod.client.ui.editor.modules.FuelModule fuelModule) {
            ItemStack fuelItem = Objects.requireNonNull(fuelModule.getItem(), "fuel item");
            payload = new com.devmod.network.FuelStatsPayload(Objects.requireNonNull(fuelItem.copy()), resetTag, isGlobalMode());
        } else if (module instanceof com.devmod.client.ui.editor.modules.UsableModule usableModule) {
            ItemStack usableItem = Objects.requireNonNull(usableModule.getItem(), "usable item");
            payload = new com.devmod.network.UsableStatsPayload(Objects.requireNonNull(usableItem.copy()), resetTag, isGlobalMode());
        }

        if (payload == null) {
            showStatus("Reset not supported for this module", DesignTokens.Semantic.WARNING);
            return;
        }

        PacketDistributor.sendToServer(payload);
        module.logEvent("Reset sent to server");
    }

    private ItemEditorSnapshot captureSnapshot(String action, @Nullable String note) {
        ItemStack current = getEditedItem();
        ItemEditorSnapshot.SnapshotMeta meta = new ItemEditorSnapshot.SnapshotMeta();
        meta.action = action == null ? "snapshot" : action;
        meta.note = note;
        meta.previewMode = isPreviewMode();
        meta.itemId = getCurrentItemId();
        meta.itemName = current.getHoverName().getString();
        EditorModule module = activeModule;
        if (module != null) {
            meta.moduleId = module.getId();
            meta.tabIndex = module.getActiveTabIndex();
            if (meta.tabIndex >= 0 && meta.tabIndex < module.getTabs().size()) {
                meta.tabId = module.getTabs().get(meta.tabIndex).id();
            }
        }
        SlotSelector.SlotInfo slot = getSelectedSlotInfo();
        if (slot != null && slot.slot() != null) {
            meta.slotId = slot.slot().getName();
        }
        CraftingRecipeData recipe = null;
        if (module instanceof com.devmod.client.ui.editor.modules.RecipeModule recipeModule) {
            recipe = recipeModule.buildCurrentRecipeSnapshot();
        }
        return ItemEditorSnapshot.capture(current, meta, recipe);
    }

    private ItemEditorSnapshot resolveBaselineSnapshot() {
        if (baselineSnapshot != null) return baselineSnapshot;
        baselineSnapshot = captureSnapshot("baseline", "Baseline");
        return baselineSnapshot;
    }

    private void recordHistoryEntry(String action, ItemEditorSnapshot before, ItemEditorSnapshot after) {
        historyEntries.add(new ItemEditorHistoryEntry(before, after, action));
        while (historyEntries.size() > 100) {
            historyEntries.remove(0);
        }
    }

    private void applySnapshotToModule(ItemEditorSnapshot snapshot, String reason) {
        if (snapshot == null) return;
        EditorModule module = activeModule;
        if (module == null) return;
        ItemStack next = snapshot.toItemStack();
        if (next.isEmpty()) {
            next = module.getItem();
        }
        module.saveUndoState();
        module.setItem(next);
        int tabIndex = snapshot.meta.tabIndex;
        if (tabIndex >= 0 && tabIndex < module.getTabs().size()) {
            module.setActiveTab(tabIndex);
        }
        if (module instanceof com.devmod.client.ui.editor.modules.RecipeModule recipeModule) {
            CraftingRecipeData recipe = snapshot.toRecipe();
            if (recipe != null) {
                recipeModule.applyRecipeSnapshot(recipe);
            }
        }
        module.applyPreview();
        module.clearDirty();
        if (reason != null && !reason.isBlank()) {
            module.markDirty(reason);
        }
    }

    public void onServerConfirm(EditorApplyConfirmPayload payload) {
        if (payload == null) return;
        String scope = payload.scope() == null ? "editor" : payload.scope();
        String summary = (payload.success() ? "Server confirmed" : "Server rejected") +
            " [" + scope + "] " + (payload.global() ? "GLOBAL" : "SPECIFIC") +
            " " + (payload.itemId() == null ? "" : payload.itemId());
        String detail = payload.message() == null ? "" : payload.message();
        String full = detail.isBlank() ? summary : summary + " - " + detail;
        EditorModule module = activeModule;
        if (module != null) {
            module.logEvent(full);
        }
        DebugPanel panel = debugPanel;
        if (panel != null) {
            panel.log(full);
        }
        // Persist ACK/FAIL to session history file for tracking
        String action = payload.success() ? "apply_ack" : "apply_fail";
        String itemId = payload.itemId() == null ? getEditedItem().getHoverName().getString() : payload.itemId();
        String details = (payload.global() ? "GLOBAL" : "SPECIFIC") + (detail.isBlank() ? "" : " - " + detail);
        ItemEditorDataManager.INSTANCE.addHistoryEntry(action, itemId, details);

        String statusMsg = detail.isBlank() ? (payload.success() ? "Server confirmed" : "Server rejected") : detail;
        showStatus(statusMsg, payload.success() ? DesignTokens.Semantic.SUCCESS : DesignTokens.Semantic.ERROR);
    }

    @Override
    public void handleCloseRequest() {
        EditorModule module = activeModule;
        if (!isPreviewMode() && module != null && module.hasUnsavedChanges()) {
            ConfirmDialog dialog = ConfirmDialog.unsavedChanges(
                module.getPendingChanges().size(),
                () -> closeEditor(false),
                () -> {}
            );
            activeDialog = dialog;
            dialog.show();
            return;
        }
        closeEditor(true);
    }

    // ═══════════════════════════════════════════════════════════════
    // DATA OPS: EXPORT / IMPORT / PRESETS
    // ═══════════════════════════════════════════════════════════════

    private void handleExport() {
        if (!supportsDataOps()) {
            showStatus("Export available only for weapons/armors/recipes", DesignTokens.Semantic.WARNING);
            return;
        }

        // Handle RecipeModule export separately
        if (activeModule instanceof com.devmod.client.ui.editor.modules.RecipeModule recipeModule) {
            handleRecipeExport(recipeModule);
            return;
        }

        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        ItemStack currentItem = getEditedItem();
        String fileBase = buildSafeFileName(getCurrentItemId() + "_export_" + System.currentTimeMillis());
        ItemEditorSnapshot snapshot = captureSnapshot("export", "Export snapshot");
        boolean okSnapshot = data.exportSnapshotToFile(snapshot, fileBase + "_snapshot");
        boolean okVanilla = data.exportVanillaItemToFile(currentItem, fileBase + "_vanilla");

        if (okVanilla) {
            showStatus("Exported vanilla JSON: " + fileBase + "_vanilla.json", DesignTokens.Semantic.SUCCESS);
        }
        if (okSnapshot) {
            data.addHistoryEntry("export", snapshot.meta.itemName, fileBase + "_snapshot");
            showStatus("Snapshot saved: " + fileBase + "_snapshot.json", DesignTokens.Semantic.INFO);
        }
        if (!okVanilla && !okSnapshot) {
            showStatus("Export failed", DesignTokens.Semantic.ERROR);
        } else {
            int exported = DatapackIO.exportOverrides(DEFAULT_DATAPACK_NAME);
            if (exported > 0) {
                showStatus("Datapack: " + DEFAULT_DATAPACK_NAME + " (" + exported + " items)", DesignTokens.Semantic.INFO);
            }
        }
    }

    /**
     * Export current recipe from RecipeModule.
     * NOTE: Currently only supports CraftingRecipeData. SmeltingRecipeData, SmithingRecipeData,
     * and StonecuttingRecipeData would require extending RecipeModule and RecipeModuleCore.
     */
    private void handleRecipeExport(com.devmod.client.ui.editor.modules.RecipeModule recipeModule) {
        CraftingRecipeData recipe = recipeModule.buildCurrentRecipeSnapshot();
        if (recipe == null) {
            showStatus("No recipe to export", DesignTokens.Semantic.WARNING);
            return;
        }

        // Validate recipe before export
        com.devmod.recipe.RecipeValidator.ValidationResult validation =
            com.devmod.recipe.RecipeValidator.validateCrafting(recipe);
        if (!validation.valid()) {
            showStatus("Invalid recipe: " + validation.getFirstError(), DesignTokens.Semantic.ERROR);
            return;
        }

        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        // Add unique counter suffix to prevent same-millisecond collision
        String fileBase = buildSafeFileName(recipe.id().toString().replace(":", "_")
            + "_" + System.currentTimeMillis()
            + "_" + (exportCounter++ % 1000)); // Rolling counter 0-999

        // Export .ds FIRST (listRecipeExportFiles uses .ds presence to identify recipe exports)
        // This prevents race condition where .json exists but .ds doesn't yet
        boolean okCommand = data.exportRecipeCommandToFile(recipe, fileBase);
        boolean okJson = data.exportRecipeToFile(recipe, fileBase);

        // Rollback: if .ds succeeded but .json failed, delete orphaned .ds
        if (okCommand && !okJson) {
            try {
                java.nio.file.Path dsFile = data.getExportsDirectory().resolve(fileBase + ".ds");
                java.nio.file.Files.deleteIfExists(dsFile);
                DevMod.LOGGER.warn("[ItemEditor] Rolled back orphaned .ds file after .json export failure");
            } catch (Exception e) {
                DevMod.LOGGER.error("[ItemEditor] Failed to rollback .ds file: {}", e.getMessage());
            }
        }

        if (!okJson && !okCommand) {
            showStatus("Recipe export failed", DesignTokens.Semantic.ERROR);
            return;
        }

        // Copy command string to clipboard for easy pasting
        String commandString = com.devmod.recipe.export.RecipeExportHelper.toCommandString(recipe, false);
        copyToClipboard(commandString);

        // Build consolidated status message - include path as fallback if folder won't open
        java.nio.file.Path exportDir = data.getExportsDirectory();
        StringBuilder msg = new StringBuilder("Exported: ").append(fileBase);
        if (okJson && okCommand) {
            msg.append(" (.json + .ds) - command copied to clipboard");
        } else if (okJson) {
            msg.append(".json");
        }
        // Fallback: show path if Desktop not supported
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            msg.append(" | Path: ").append(exportDir);
        }

        data.addHistoryEntry("export_recipe", recipe.id().toString(), fileBase);
        showStatus(msg.toString(), DesignTokens.Semantic.SUCCESS);

        // Open export folder - doesn't call showStatus anymore to avoid overwriting
        tryOpenExportFolder();
    }

    /** Counter to prevent same-millisecond filename collisions */
    private static int exportCounter = 0;

    /** Copy text to system clipboard */
    private void copyToClipboard(String text) {
        try {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        } catch (Exception e) {
            DevMod.LOGGER.warn("[ItemEditor] Could not copy to clipboard: {}", e.getMessage());
        }
    }

    /**
     * Try to open export folder in system file manager.
     * Does NOT call showStatus to avoid overwriting the export success message.
     */
    private void tryOpenExportFolder() {
        Path exportDir = com.devmod.util.ConfigPaths.getItemEditorDir().resolve("exports");
        try {
            // Ensure directory exists before trying to open
            java.nio.file.Files.createDirectories(exportDir);

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(exportDir.toFile());
            }
            // If Desktop not supported, we silently skip - the success message already shows
        } catch (Exception e) {
            // Log but don't show status - preserves the success message
            DevMod.LOGGER.warn("[ItemEditor] Could not open export folder {}: {}", exportDir, e.getMessage());
        }
    }

    /**
     * Import a recipe from the most recent export file.
     * NOTE: Currently only supports CraftingRecipeData. Other recipe types would need
     * type detection and corresponding module support.
     */
    private void handleRecipeImport() {
        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        List<String> recipeFiles = data.listRecipeExportFiles();

        if (recipeFiles.isEmpty()) {
            showStatus("No recipe exports found", DesignTokens.Semantic.WARNING);
            return;
        }

        // Get the most recent recipe file (list is sorted DESCENDING - first = most recent)
        String fileName = recipeFiles.getFirst();
        Path filePath = data.getExportsDirectory().resolve(fileName + ".json");

        // Verify file exists before attempting import
        if (!java.nio.file.Files.exists(filePath)) {
            showStatus("Recipe file not found: " + fileName, DesignTokens.Semantic.ERROR);
            return;
        }

        try {
            // Load and validate the recipe JSON before importing
            String jsonContent = java.nio.file.Files.readString(filePath, java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonObject jsonObj = com.google.gson.JsonParser.parseString(jsonContent).getAsJsonObject();

            // Parse recipe to validate structure
            net.minecraft.resources.ResourceLocation recipeId = net.minecraft.resources.ResourceLocation.parse(
                fileName.contains("_") ? "devmod:" + fileName.substring(0, fileName.lastIndexOf('_')) : "devmod:" + fileName
            );
            CraftingRecipeData loadedRecipe = CraftingRecipeData.fromJson(recipeId, jsonObj);

            // Validate recipe before applying
            com.devmod.recipe.RecipeValidator.ValidationResult validation =
                com.devmod.recipe.RecipeValidator.validateCrafting(loadedRecipe);
            if (!validation.valid()) {
                showStatus("Invalid recipe in file: " + validation.getFirstError(), DesignTokens.Semantic.ERROR);
                return;
            }

            // Apply to RecipeConfigManager (persistent storage)
            int imported = RecipeConfigManager.importFromFile(filePath);

            // Also apply to current RecipeModule UI if active
            if (activeModule instanceof com.devmod.client.ui.editor.modules.RecipeModule recipeModule) {
                recipeModule.applyRecipeSnapshot(loadedRecipe);
            }

            if (imported > 0) {
                data.addHistoryEntry("import_recipe", fileName, filePath.toString());
                showStatus("Imported and applied: " + fileName, DesignTokens.Semantic.SUCCESS);
            } else {
                // Recipe was already in config, but we still applied to UI
                showStatus("Loaded to editor: " + fileName + " (already in config)", DesignTokens.Semantic.INFO);
            }
        } catch (com.google.gson.JsonParseException e) {
            DevMod.LOGGER.error("[ItemEditor] Invalid JSON in recipe file: {}", e.getMessage());
            showStatus("Invalid JSON format: " + e.getMessage(), DesignTokens.Semantic.ERROR);
        } catch (Exception e) {
            DevMod.LOGGER.error("[ItemEditor] Failed to import recipe: {}", e.getMessage());
            showStatus("Import failed: " + e.getMessage(), DesignTokens.Semantic.ERROR);
        }
    }

    @SuppressWarnings("unused") // Reserved for future export presets.
    private List<ItemEditorDataManager.EnchantData> collectEnchantmentsForExport() {
        List<ItemEditorDataManager.EnchantData> result = new ArrayList<>();
        final var enchantType = Objects.requireNonNull(DataComponents.ENCHANTMENTS, "enchantments component");
        ItemStack currentItem = getEditedItem();
        ItemEnchantments enchants = currentItem.has(enchantType)
            ? Objects.requireNonNull(currentItem.get(enchantType), "enchantments value")
            : ItemEnchantments.EMPTY;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc != null ? mc.level : null;
        if (level == null) return result;
        ResourceKey<Registry<Enchantment>> enchantKey = Objects.requireNonNull(Registries.ENCHANTMENT, "enchantment registry key");
        Registry<Enchantment> registry = Objects.requireNonNull(
            level.registryAccess().registryOrThrow(enchantKey),
            "enchantment registry"
        );
        enchants.entrySet().forEach(entry -> {
            Holder<Enchantment> holder = entry.getKey();
            int enchantLevel = entry.getIntValue();
            Enchantment enchant = Objects.requireNonNull(holder.value(), "enchantment value");
            var key = registry.getKey(enchant);
            if (key != null) {
                result.add(new ItemEditorDataManager.EnchantData(key.toString(), enchantLevel));
            }
        });
        return result;
    }

    @SuppressWarnings("unused") // Reserved for future export presets.
    private List<ItemEditorDataManager.AttrData> collectAttributesForExport() {
        List<ItemEditorDataManager.AttrData> result = new ArrayList<>();
        final var attrType = Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS, "attribute modifiers component");
        ItemStack currentItem = getEditedItem();
        ItemAttributeModifiers modifiers = currentItem.has(attrType)
            ? Objects.requireNonNull(currentItem.get(attrType), "attribute modifiers")
            : ItemAttributeModifiers.EMPTY;
        Objects.requireNonNull(modifiers.modifiers(), "attribute modifier entries").forEach(entry -> {
            Holder<Attribute> attr = entry.attribute();
            AttributeModifier mod = entry.modifier();
            var key = BuiltInRegistries.ATTRIBUTE.getKey(Objects.requireNonNull(attr.value(), "attribute value"));
            if (key != null) {
                int opOrdinal = mod.operation().ordinal();
                result.add(new ItemEditorDataManager.AttrData(key.toString(), mod.amount(), opOrdinal));
            }
        });
        return result;
    }

    private void handleImport() {
        if (!supportsDataOps()) {
            showStatus("Import available only for weapons/armors/recipes", DesignTokens.Semantic.WARNING);
            return;
        }

        // Handle RecipeModule import separately
        if (activeModule instanceof com.devmod.client.ui.editor.modules.RecipeModule) {
            handleRecipeImport();
            return;
        }

        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        List<String> snapshots = data.listSnapshotFiles();
        List<String> exports = data.listExportFiles();
        if (snapshots.isEmpty() && exports.isEmpty()) {
            showStatus("No exports found", DesignTokens.Semantic.WARNING);
            return;
        }

        ItemEditorSnapshot before = captureSnapshot("import", "Before import");
        if (!snapshots.isEmpty()) {
            String fileName = snapshots.get(snapshots.size() - 1);
            try {
                ItemEditorSnapshot imported = data.importSnapshotFromFile(fileName);
                if (imported != null) {
                    applySnapshotToModule(imported, "Imported " + fileName);
                    ItemEditorSnapshot after = captureSnapshot("import", "After import");
                    recordHistoryEntry("import", before, after);
                    baselineSnapshot = after;
                    showStatus("Imported snapshot " + fileName, DesignTokens.Semantic.INFO);
                } else {
                    showStatus("Import failed: snapshot missing", DesignTokens.Semantic.ERROR);
                }
            } catch (Exception e) {
                showStatus("Import failed: " + e.getMessage(), DesignTokens.Semantic.ERROR);
            }
            return;
        }

        String fileName = exports.get(exports.size() - 1);
        try {
            JsonElement json = data.importVanillaJsonFromFile(fileName);
            ItemEditorSnapshot imported = new ItemEditorSnapshot();
            imported.item = json;
            imported.meta = new ItemEditorSnapshot.SnapshotMeta();
            imported.meta.action = "import";
            imported.meta.itemId = getCurrentItemId();
            imported.meta.itemName = getEditedItem().getHoverName().getString();
            applySnapshotToModule(imported, "Imported " + fileName);
            ItemEditorSnapshot after = captureSnapshot("import", "After import");
            recordHistoryEntry("import", before, after);
            baselineSnapshot = after;
            showStatus("Imported vanilla JSON " + fileName, DesignTokens.Semantic.INFO);
        } catch (Exception e) {
            showStatus("Import failed: " + e.getMessage(), DesignTokens.Semantic.ERROR);
        }
    }

    private void openTemplatesOverlay() {
        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        List<ItemEditorDataManager.TemplateData> list = new ArrayList<>(data.getTemplates().values());
        @Nullable String category = detectTemplateCategory();
        if (templateOverlay != null) {
            templateOverlay.setTemplates(list, category);
        }
        overlayController.toggle(OverlayController.OverlayType.TEMPLATES);
    }

    private boolean supportsDataOps() {
        return activeModule instanceof WeaponModule
            || activeModule instanceof ArmorModule
            || activeModule instanceof com.devmod.client.ui.editor.modules.RecipeModule;
    }

    private List<ItemEditorDataManager.PresetData> getFavoritePresetsForActiveType() {
        List<ItemEditorDataManager.PresetData> result = new ArrayList<>();
        for (String name : favoriteStore.getFavorites(getActiveItemType())) {
            ItemEditorDataManager.INSTANCE.getPreset(name).ifPresent(result::add);
        }
        return result;
    }

    private String getActiveItemType() {
        if (activeModule instanceof WeaponModule) return "weapon";
        if (activeModule instanceof ArmorModule) return "armor";
        return "item";
    }

    private String getCurrentItemId() {
        var key = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(getEditedItem().getItem(), "item cannot be null"));
        return key == null ? "unknown" : key.toString();
    }

    private @Nullable String detectTemplateCategory() {
        String itemId = getCurrentItemId();
        return ItemEditorDataManager.INSTANCE.suggestTemplate(itemId)
            .map(t -> t.itemCategory)
            .orElse(null);
    }

    private String buildSafeFileName(String raw) {
        return raw.replace(":", "_").replace("/", "_");
    }

    private String buildPresetName(String itemType) {
        String base = buildSafeFileName(getCurrentItemId());
        return base + "_preset_" + itemType + "_" + System.currentTimeMillis();
    }

    private String getDevModVersion() {
        return ModList.get()
            .getModContainerById(DevMod.MODID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElse("unknown");
    }

    private List<Float> collectStatsForExport() {
        List<Float> stats = new ArrayList<>();
        if (activeModule instanceof WeaponModule weaponModule) {
            var s = weaponModule.getStats();
            stats.add(s.getHeadMult());
            stats.add(s.getBodyMult());
            stats.add(s.getArmsMult());
            stats.add(s.getLegsMult());
            stats.add(s.getAttackDamage());
            stats.add(s.getAttackSpeed());
            stats.add(s.getAttackReach());
            stats.add(s.getAttackKnockback());
            stats.add(s.getArmorPenetration());
            stats.add(s.getBaseDamageBonus());
            stats.add(s.getCritChance());
            stats.add(s.getCritDamage());
            stats.add(s.getLifesteal());
            stats.add(s.getFireDamageBonus());
            stats.add(s.getMagicDamageBonus());
            stats.add(s.getDamageBonus());
            stats.add(s.getArmorShred());
            stats.add(s.getDamageVsUndead());
            stats.add(s.getDamageVsArthropods());
            stats.add(s.getDamageVsPlayers());
            stats.add(s.getTrueDamagePercent());
        } else if (activeModule instanceof ArmorModule armorModule) {
            var s = armorModule.getStats();
            stats.add(s.getPhysicalReduction());
            stats.add(s.getFireReduction());
            stats.add(s.getMagicReduction());
            stats.add(s.getExplosionReduction());
            stats.add(s.getProjectileReduction());
            stats.add(s.getArmorBonus());
            stats.add(s.getToughnessBonus());
            stats.add(s.getKnockbackResistance());
            stats.add(s.getThornsPercent());
            stats.add(s.isThornsReflect() ? 1f : 0f);
        }
        return stats;
    }

    private ItemEditorDataManager.PresetData buildPresetFromCurrent(String name, String itemType) {
        ItemEditorDataManager.PresetData preset = new ItemEditorDataManager.PresetData(name);
        preset.itemType = itemType;
        preset.scope = isGlobalMode() ? "GLOBAL" : "SPECIFIC";
        preset.devmodVersion = getDevModVersion();
        preset.statValues = collectStatsForExport();
        return preset;
    }

    private void applyImportedStats(ItemEditorDataManager.ItemConfigExport config, String reason) {
        if (config == null || config.stats == null) {
            showStatus("Import file missing stats", DesignTokens.Semantic.ERROR);
            return;
        }

        if (activeModule instanceof WeaponModule weaponModule) {
            WeaponStats newStats = weaponModule.getStats().copy();
            List<Float> values = config.stats;
            newStats.setHeadMult(getStat(values, 0, newStats.getHeadMult()));
            newStats.setBodyMult(getStat(values, 1, newStats.getBodyMult()));
            newStats.setArmsMult(getStat(values, 2, newStats.getArmsMult()));
            newStats.setLegsMult(getStat(values, 3, newStats.getLegsMult()));
            newStats.setAttackDamage(getStat(values, 4, newStats.getAttackDamage()));
            newStats.setAttackSpeed(getStat(values, 5, newStats.getAttackSpeed()));
            newStats.setAttackReach(getStat(values, 6, newStats.getAttackReach()));
            newStats.setAttackKnockback(getStat(values, 7, newStats.getAttackKnockback()));
            newStats.setArmorPenetration(getStat(values, 8, newStats.getArmorPenetration()));
            newStats.setBaseDamageBonus(getStat(values, 9, newStats.getBaseDamageBonus()));
            newStats.setCritChance(getStat(values, 10, newStats.getCritChance()));
            newStats.setCritDamage(getStat(values, 11, newStats.getCritDamage()));
            newStats.setLifesteal(getStat(values, 12, newStats.getLifesteal()));
            newStats.setFireDamageBonus(getStat(values, 13, newStats.getFireDamageBonus()));
            newStats.setMagicDamageBonus(getStat(values, 14, newStats.getMagicDamageBonus()));
            newStats.setDamageBonus(getStat(values, 15, newStats.getDamageBonus()));
            newStats.setArmorShred(getStat(values, 16, newStats.getArmorShred()));
            newStats.setDamageVsUndead(getStat(values, 17, newStats.getDamageVsUndead()));
            newStats.setDamageVsArthropods(getStat(values, 18, newStats.getDamageVsArthropods()));
            newStats.setDamageVsPlayers(getStat(values, 19, newStats.getDamageVsPlayers()));
            newStats.setTrueDamagePercent(getStat(values, 20, newStats.getTrueDamagePercent()));
            weaponModule.applyExternalStats(newStats, reason);
            weaponModule.applyPreview();
        } else if (activeModule instanceof ArmorModule armorModule) {
            ArmorStats newStats = armorModule.getStats().copy();
            List<Float> values = config.stats;
            newStats.setPhysicalReduction(getStat(values, 0, newStats.getPhysicalReduction()));
            newStats.setFireReduction(getStat(values, 1, newStats.getFireReduction()));
            newStats.setMagicReduction(getStat(values, 2, newStats.getMagicReduction()));
            newStats.setExplosionReduction(getStat(values, 3, newStats.getExplosionReduction()));
            newStats.setProjectileReduction(getStat(values, 4, newStats.getProjectileReduction()));
            newStats.setArmorBonus(getStat(values, 5, newStats.getArmorBonus()));
            newStats.setToughnessBonus(getStat(values, 6, newStats.getToughnessBonus()));
            newStats.setKnockbackResistance(getStat(values, 7, newStats.getKnockbackResistance()));
            newStats.setThornsPercent(getStat(values, 8, newStats.getThornsPercent()));
            newStats.setThornsReflect(getStat(values, 9, newStats.isThornsReflect() ? 1f : 0f) > 0.5f);
            armorModule.applyExternalStats(newStats, reason);
            armorModule.applyPreview();
        } else {
            showStatus("Unsupported editor for import", DesignTokens.Semantic.ERROR);
        }
    }

    private void handleTemplateApply(ItemEditorDataManager.TemplateData template) {
        if (template == null) return;
        EditorModule module = activeModule;
        if (!isPreviewMode() && module != null && module.hasUnsavedChanges()) {
            ConfirmDialog dialog = ConfirmDialog.unsavedChanges(
                module.getPendingChanges().size(),
                () -> {
                    ItemEditorSnapshot before = captureSnapshot("template", "Before template");
                    applyTemplateInternal(template);
                    ItemEditorSnapshot after = captureSnapshot("template", "After template");
                    recordHistoryEntry("template", before, after);
                    baselineSnapshot = after;
                    closeOverlay();
                },
                () -> {});
            activeDialog = dialog;
            dialog.show();
            return;
        }
        ItemEditorSnapshot before = captureSnapshot("template", "Before template");
        applyTemplateInternal(template);
        ItemEditorSnapshot after = captureSnapshot("template", "After template");
        recordHistoryEntry("template", before, after);
        baselineSnapshot = after;
        closeOverlay();
    }

    private void applyTemplateInternal(ItemEditorDataManager.TemplateData template) {
        var snapshot = template.snapshot;
        if (snapshot != null && snapshot.item != null) {
            applySnapshotToModule(snapshot, "Applied template: " + template.name);
            return;
        }
        ItemStack target = isPreviewMode() ? getEditedItem().copy() : getEditedItem();

        // Enchantments
        if (template.enchantments != null && !template.enchantments.isEmpty()) {
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(Objects.requireNonNull(ItemEnchantments.EMPTY));
            var mc = Minecraft.getInstance();
            var level = mc != null ? mc.level : null;
            var enchantmentRegistry = level != null
                ? level.registryAccess().registryOrThrow(Objects.requireNonNull(Registries.ENCHANTMENT))
                : null;
            for (ItemEditorDataManager.EnchantData ench : template.enchantments) {
                if (ench == null || ench.id == null) continue;
                String enchantId = Objects.requireNonNull(ench.id, "template enchant id");
                ResourceLocation id = ResourceLocation.tryParse(enchantId);
                if (id == null) continue;
                ResourceKey<Enchantment> key = ResourceKey.create(Objects.requireNonNull(Registries.ENCHANTMENT), id);
                Holder<Enchantment> holder = enchantmentRegistry != null
                    ? enchantmentRegistry.getHolder(Objects.requireNonNull(key)).orElse(null)
                    : null;
                if (holder != null) {
                    mutable.set(holder, ench.level);
                }
            }
            target.set(
                Objects.requireNonNull(DataComponents.ENCHANTMENTS),
                Objects.requireNonNull(mutable.toImmutable())
            );
        }

        // Attributes
        if (template.attributes != null && !template.attributes.isEmpty()) {
            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
            for (ItemEditorDataManager.AttrData attr : template.attributes) {
                if (attr == null || attr.id == null) continue;
                String attrId = Objects.requireNonNull(attr.id, "template attr id");
                ResourceLocation id = ResourceLocation.tryParse(attrId);
                if (id == null) continue;
                ResourceKey<Attribute> key = ResourceKey.create(Objects.requireNonNull(Registries.ATTRIBUTE), id);
                Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(Objects.requireNonNull(key)).orElse(null);
                // Map to Pufferfish equivalent if present
                if (holder == null) {
                    holder = PufferfishIntegration.map(id, BuiltInRegistries.ATTRIBUTE);
                }
                if (holder == null) continue;
                AttributeModifier.Operation op = switch (attr.operation) {
                    case 1 -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
                    case 2 -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
                    default -> AttributeModifier.Operation.ADD_VALUE;
                };
                ResourceLocation modifierId = Objects.requireNonNull(
                    ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "template_" + id.getPath())
                );
                AttributeModifier modifier = new AttributeModifier(modifierId, attr.value, op);
                builder.add(Objects.requireNonNull(holder), modifier, EquipmentSlotGroup.ANY);
            }
            target.set(
                Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS),
                builder.build()
            );
        }

        EditorModule module = activeModule;
        if (module != null) {
            module.setItem(target);
            module.applyPreview();
            if (!isPreviewMode()) {
                module.markDirty("Applied template: " + template.name);
            }
        }

        String name = template.name == null ? "template" : template.name;
        ItemEditorDataManager.INSTANCE.addHistoryEntry("template_apply", getEditedItem().getHoverName().getString(), name);
        lastLoadedPreset = name;
        showStatus("Applied template " + name, DesignTokens.Semantic.SUCCESS);
        EditorCache.INSTANCE.invalidateItem(getCurrentItemId());
    }

    private void applyPreset(ItemEditorDataManager.PresetData preset) {
        if (preset == null) {
            showStatus("Preset not found", DesignTokens.Semantic.ERROR);
            return;
        }
        ItemEditorDataManager.ItemConfigExport wrapper = new ItemEditorDataManager.ItemConfigExport();
        wrapper.stats = preset.statValues;
        applyImportedStats(wrapper, "Preset: " + preset.name);
        lastLoadedPreset = preset.name;
    }

    private void deletePreset(ItemEditorDataManager.PresetData preset) {
        if (preset == null || preset.name == null) return;
        String presetName = preset.name;
        PresetSelectorOverlay psOverlay = presetSelectorOverlay;
        ConfirmDialog dialog = ConfirmDialog.deletePreset(presetName,
            () -> {
                ItemEditorDataManager.INSTANCE.deletePreset(presetName);
                ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_delete", getEditedItem().getHoverName().getString(), presetName);
                DevMod.LOGGER.info("[Editor] Preset deleted: {}", presetName);
                showStatus("Preset deleted: " + presetName, DesignTokens.Semantic.INFO);
                if (psOverlay != null) {
                    psOverlay.refreshPresets();
                }
            },
            () -> {}  // onCancel - no action needed
        );
        activeDialog = dialog;
        dialog.show();
    }

    private void renamePreset(ItemEditorDataManager.PresetData preset, String newName) {
        if (preset == null || preset.name == null || newName == null || newName.isBlank()) return;
        String oldName = Objects.requireNonNull(preset.name, "preset name cannot be null after check");

        // Update the preset with new name
        preset.name = newName.trim();
        ItemEditorDataManager.INSTANCE.deletePreset(oldName);
        ItemEditorDataManager.INSTANCE.savePreset(preset);
        ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_rename", getEditedItem().getHoverName().getString(), oldName + " -> " + newName);
        DevMod.LOGGER.info("[Editor] Preset renamed: {} -> {}", oldName, newName);
        showStatus("Preset renamed: " + newName, DesignTokens.Semantic.INFO);

        // Update lastLoadedPreset if it was the renamed one
        if (oldName.equals(lastLoadedPreset)) {
            lastLoadedPreset = newName;
        }

        PresetSelectorOverlay psOverlay = presetSelectorOverlay;
        if (psOverlay != null) {
            psOverlay.refreshPresets();
        }
    }

    private void saveCurrentAsPreset() {
        String itemType = getActiveItemType();
        String presetName = buildPresetName(itemType);
        ItemEditorDataManager.PresetData preset = buildPresetFromCurrent(presetName, itemType);
        ItemEditorDataManager.INSTANCE.savePreset(preset);
        ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_save", getEditedItem().getHoverName().getString(), presetName);
        DevMod.LOGGER.info("[Editor] Preset saved: {}", presetName);
        showStatus("Preset saved: " + presetName, DesignTokens.Semantic.SUCCESS);
        lastLoadedPreset = presetName;
        PresetSelectorOverlay psOverlay = presetSelectorOverlay;
        if (psOverlay != null) {
            psOverlay.refreshPresets();
        }
    }

    private float getStat(List<Float> values, int index, float fallback) {
        if (values == null || index < 0 || index >= values.size()) {
            return fallback;
        }
        Float value = values.get(index);
        return value == null ? fallback : value;
    }

    @Override
    public boolean handleFavoritesClick(double mouseX, double mouseY) {
        if (!PRESETS_ENABLED) {
            return false;
        }
        ResponsiveLayout.Rect leftPanel = layout.getFavoritesPanelArea();
        if (leftPanel.isEmpty()) return false;
        if (mouseX < leftPanel.x() || mouseX > leftPanel.right() || mouseY < leftPanel.y() || mouseY > leftPanel.bottom()) {
            return false;
        }

        List<ItemEditorDataManager.PresetData> favorites = getFavoritePresetsForActiveType();
        int rowHeight = FAVORITES_ROW_HEIGHT;
        int startY = leftPanel.y() + FAVORITES_LIST_START_Y;
        int maxRows = Math.max(0, (leftPanel.height() - FAVORITES_LIST_BOTTOM_PADDING) / rowHeight);
        for (int i = 0; i < Math.min(maxRows, favorites.size()); i++) {
            int rowY = startY + i * rowHeight;
            if (mouseY >= rowY && mouseY <= rowY + rowHeight) {
                ItemEditorDataManager.PresetData preset = favorites.get(i);
                String presetName = preset.name == null ? FAVORITES_LABEL_FALLBACK : preset.name;
                Runnable loadAction = () -> {
                    applyPreset(preset);
                    ItemEditorDataManager.INSTANCE.addHistoryEntry("favorite_load", getEditedItem().getHoverName().getString(), presetName);
                    showStatus("Favorite applied: " + presetName, DesignTokens.Semantic.INFO);
                };
                if (activeModule != null && activeModule.hasUnsavedChanges()) {
                    ConfirmDialog dialog = ConfirmDialog.create(
                        "Apply favorite",
                        "Apply",
                        "Cancel",
                        ConfirmDialog.Style.WARNING,
                        loadAction,
                        () -> {},
                        "Overwrite current changes with favorite '" + presetName + "'?"
                    );
                    activeDialog = dialog;
                    dialog.show();
                } else {
                    loadAction.run();
                }
                return true;
            }
        }

        // Pin/unpin last loaded
        if (lastLoadedPreset != null) {
            int btnY = leftPanel.bottom() - FAVORITES_PIN_BUTTON_OFFSET_Y;
            int btnW = FAVORITES_PIN_BUTTON_WIDTH;
            int btnH = FAVORITES_PIN_BUTTON_HEIGHT;
            int btnX = leftPanel.x() + FAVORITES_PIN_BUTTON_OFFSET_X;
            if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                favoriteStore.toggleFavorite(getActiveItemType(), lastLoadedPreset);
                boolean nowFav = favoriteStore.isFavorite(getActiveItemType(), lastLoadedPreset);
                String msg = nowFav ? PINNED_PREFIX : UNPINNED_PREFIX;
                showStatus(msg + lastLoadedPreset, DesignTokens.Semantic.INFO);
                return true;
            }
        }

        return true; // consume clicks inside panel
    }

    private boolean persistMultiEditItem(ItemStack item, int slot) {
        if (localOnlyMode) {
            showStatus("MultiEdit disabled in kit edit", DesignTokens.Semantic.WARNING);
            return false;
        }
        DebugPanel panel = debugPanel;
        try {
            var mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                var inv = mc.player.getInventory();
                if (slot >= 0 && slot < inv.items.size()) {
                    // Update local inventory immediately
                    inv.items.set(slot, item.copy());
                }
            }
            var custom = item.getOrDefault(
                Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA),
                Objects.requireNonNull(net.minecraft.world.item.component.CustomData.EMPTY)
            );
            var tag = Objects.requireNonNull(custom.copyTag(), CUSTOM_TAG_MISSING);
            if (tag.contains(WEAPON_STATS_KEY)) {
                CompoundTag statsTag = Objects.requireNonNull(tag.getCompound(WEAPON_STATS_KEY), WEAPON_STATS_MISSING);
                // Use WeaponStatsPayload for proper server-side handling
                // Note: WeaponStatsPayload doesn't have slot parameter, item is matched by type
                CompoundTag fullTag = new CompoundTag();
                fullTag.put(WEAPON_STATS_KEY, Objects.requireNonNull(statsTag.copy()));
                ItemStack itemCopy = Objects.requireNonNull(item.copy());
                PacketDistributor.sendToServer(
                    new com.devmod.network.WeaponStatsPayload(itemCopy, fullTag, isGlobalMode())
                );
            } else if (tag.contains(ARMOR_STATS_KEY) || tag.contains(ARMOR_STATS_COMPONENT_KEY)) {
                // Prefer armor_stats_component if available, fallback to ArmorModStats
                CompoundTag statsTag = tag.contains(ARMOR_STATS_COMPONENT_KEY)
                    ? Objects.requireNonNull(tag.getCompound(ARMOR_STATS_COMPONENT_KEY), ARMOR_STATS_MISSING)
                    : Objects.requireNonNull(tag.getCompound(ARMOR_STATS_KEY), ARMOR_STATS_MISSING);
                // Use ArmorStatsPayload with slot info for proper server-side handling
                CompoundTag fullTag = new CompoundTag();
                fullTag.put(ARMOR_STATS_KEY, Objects.requireNonNull(statsTag.copy()));
                fullTag.put(ARMOR_STATS_COMPONENT_KEY, Objects.requireNonNull(statsTag.copy()));
                ItemStack itemCopy = Objects.requireNonNull(item.copy());
                PacketDistributor.sendToServer(
                    new ArmorStatsPayload(itemCopy, fullTag, isGlobalMode(), slot)
                );
            }
            if (panel != null) {
                panel.log(MULTI_EDIT_PERSIST_PREFIX + slot + " (" + item.getHoverName().getString() + ")");
            }
            return true;
        } catch (Exception e) {
            if (panel != null) panel.log(MULTI_EDIT_PERSIST_FAILED_PREFIX + e.getMessage());
            DevMod.LOGGER.warn("[MultiEdit] Persist failed for slot {}: {}", slot, e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void showStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
        this.statusTicks = STATUS_TICKS_DURATION; // 3 seconds at 20 TPS
    }

    @Override
    public void clearTooltip() {
        // Tooltips are managed via TooltipManager per frame.
    }

    @Override
    public boolean showDevPanel() {
        return this.showDevPanel;
    }

    @Override
    public void toggleDevPanel() {
        this.showDevPanel = !this.showDevPanel;
    }

    @Override
    public void toggleMultiEditPanel() {
        if (localOnlyMode) {
            showStatus("MultiEdit disabled in kit edit", DesignTokens.Semantic.WARNING);
            return;
        }
        this.showMultiEditPanel = !this.showMultiEditPanel;
    }

    @Override
    public boolean showMultiEditPanel() {
        return this.showMultiEditPanel;
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT CONTEXT INTERFACE IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public int screenWidth() {
        return this.width;
    }

    @Override
    public int screenHeight() {
        return this.height;
    }

    @Override
    public HeaderComponent header() {
        return this.header;
    }

    @Override
    public FooterComponent footer() {
        return this.footer;
    }

    @Override
    public LeftColumnComponent leftColumn() {
        return this.leftColumn;
    }

    @Override
    public ScrollableContentArea scrollArea() {
        return this.scrollArea;
    }

    @Override
    public HelpOverlay helpOverlay() {
        return this.helpOverlay;
    }

    @Override
    public LowConfidenceDetector lowConfidenceDetector() {
        return this.lowConfidenceDetector;
    }

    @Override
    public @Nullable ConfirmDialog activeDialog() {
        return this.activeDialog;
    }

    @Override
    public @Nullable TemplateOverlay templateOverlay() {
        return this.templateOverlay;
    }

    @Override
    public @Nullable PresetSelectorOverlay presetSelectorOverlay() {
        return this.presetSelectorOverlay;
    }

    @Override
    public CraftingInfoPanel craftingPanel() {
        return this.craftingPanel;
    }

    @Override
    public @Nullable DebugPanel debugPanel() {
        return this.debugPanel;
    }

    @Override
    public OverlayController overlayController() {
        return this.overlayController;
    }

    @Override
    public @Nullable EditorModule activeModule() {
        return this.activeModule;
    }

    @Override
    public @Nullable MultiEditManager multiEditManager() {
        return this.multiEditManager;
    }

    @Override
    public @Nullable MultiEditPanel multiEditPanel() {
        return this.multiEditPanel;
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && sound != null) {
            mc.player.playSound(Objects.requireNonNull(sound, "sound cannot be null"), 0.5f, 1.0f);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (statusTicks > 0) {
            statusTicks--;
        }

        EditorModule module = activeModule;
        if (module != null) {
            module.tick();
        }
    }

    @Override
    public void onClose() {
        // Track screen close for telemetry
        UiTelemetry.screenClosed("editor", "item_editor");

        EditorModule module = activeModule;
        if (module != null) {
            module.onClose();
        }

        // Invoke callback with edited item if available
        if (onItemEdited != null && commitEditsOnClose) {
            onItemEdited.accept(resolveEditedItemForCallback());
        }

        // Return to parent screen if available, otherwise close normally
        if (parentScreen != null && this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void closeEditor(boolean commitEdits) {
        this.commitEditsOnClose = commitEdits;
        onClose();
    }

    private ItemStack resolveEditedItemForCallback() {
        EditorModule module = activeModule;
        if (module != null) {
            try {
                module.applyPreview();
                ItemStack preview = module.getPreviewItem();
                if (preview != null && !preview.isEmpty()) {
                    return preview.copy();
                }
                ItemStack moduleItem = module.getItem();
                if (moduleItem != null && !moduleItem.isEmpty()) {
                    return moduleItem.copy();
                }
            } catch (Exception e) {
                DevMod.LOGGER.debug("[ItemEditor] Failed to resolve edited item for callback", e);
            }
        }
        return getEditedItem().copy();
    }

    private List<String> buildStatSources(ItemStack stack) {
        List<String> sources = new ArrayList<>();
        if (stack == null || stack.isEmpty()) {
            return sources;
        }

        CustomData data = stack.get(Objects.requireNonNull(DataComponents.CUSTOM_DATA));
        boolean hasWeaponSpecific = false;
        boolean hasArmorSpecific = false;
        if (data != null) {
            try {
                var tag = data.copyTag();
                hasWeaponSpecific = tag != null && tag.contains(WEAPON_STATS_KEY);
                hasArmorSpecific = tag != null && tag.contains(ARMOR_STATS_KEY);
            } catch (Exception ignored) {
                // fall through
            }
        }

        boolean isArmor = ArmorConfigHandler.isArmor(stack);

        if (isArmor) {
            if (hasArmorSpecific) {
                sources.add(SOURCE_SPECIFIC_ARMOR);
            } else {
                sources.add(SOURCE_VANILLA);
            }
        } else {
            if (hasWeaponSpecific) {
                sources.add(SOURCE_SPECIFIC_WEAPON);
            } else {
                sources.add(SOURCE_VANILLA);
            }
        }

        return sources;
    }

    /**
     * Build stat diffs for the debug panel comparing current vs original (baseline) stats.
     * Uses DebugPanel.buildWeaponDiffs/buildArmorDiffs to generate comparison entries.
     */
    private List<DebugPanel.StatDiff> buildStatDiffs() {
        EditorModule module = activeModule;
        if (module instanceof WeaponModule weaponModule) {
            return DebugPanel.buildWeaponDiffs(weaponModule.getStats(), weaponModule.getOriginalStats());
        } else if (module instanceof ArmorModule armorModule) {
            return DebugPanel.buildArmorDiffs(armorModule.getStats(), armorModule.getOriginalStats());
        }
        return List.of();
    }

    // ═══════════════════════════════════════════════════════════════
    // MODE ACCESSORS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean isPreviewMode() {
        return editorState.isPreviewMode();
    }

    public boolean isGlobalMode() {
        return editorState.isGlobalMode();
    }

    // ═══════════════════════════════════════════════════════════════
    // STATE DELEGATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the item being edited (delegated to editorState).
     */
    private ItemStack getEditedItem() {
        return editorState.getItem();
    }

    /**
     * Get the currently selected slot (delegated to editorState).
     */
    @Nullable
    private SlotSelector.SlotInfo getSelectedSlotInfo() {
        return editorState.getSelectedSlot();
    }

    /**
     * Set the currently selected slot (delegated to editorState).
     */
    private void setSelectedSlotInfo(@Nullable SlotSelector.SlotInfo slot) {
        editorState.setSelectedSlot(slot);
    }

    private enum HistoryFilter {
        ALL("All", ""),
        APPLY("Apply", "apply"),
        IMPORT("Import", "import"),
        TEMPLATE("Template", "template"),
        RESET("Reset", "reset"),
        ROLLBACK("Rollback", "rollback");

        @Nonnull private final String label;
        @Nonnull private final String actionKey;

        HistoryFilter(String label, String actionKey) {
            this.label = label;
            this.actionKey = actionKey;
        }

        public boolean matches(String action) {
            if (this == ALL) return true;
            if (action == null) return false;
            return action.equalsIgnoreCase(actionKey);
        }
    }

    private record HistoryFilterHit(HistoryFilter filter, ResponsiveLayout.Rect bounds) {}
}
