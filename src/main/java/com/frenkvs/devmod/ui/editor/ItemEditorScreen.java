package com.frenkvs.devmod.ui.editor;

import com.frenkvs.devmod.ArmorConfigManager;
import com.frenkvs.devmod.ArmorStats;
import com.frenkvs.devmod.EditorClientConfig;
import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.ItemEditorDataManager;
import com.frenkvs.devmod.WeaponConfigManager;
import com.frenkvs.devmod.WeaponStats;
import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.core.EditorCache;
import com.frenkvs.devmod.ui.editor.core.EditorConfig;
import com.frenkvs.devmod.ui.editor.core.EditorLayout;
import com.frenkvs.devmod.ui.editor.core.EditorScaleCalculator;
import com.frenkvs.devmod.ui.editor.core.EditorSpacing;
import com.frenkvs.devmod.ui.editor.core.ResponsiveLayout;
import com.frenkvs.devmod.ui.editor.core.GridValidator;
import com.frenkvs.devmod.ui.editor.core.ScaledCoord;
import com.frenkvs.devmod.ui.editor.core.Typography;
import com.frenkvs.devmod.ui.editor.components.FooterComponent;
import com.frenkvs.devmod.ui.editor.components.HeaderComponent;
import com.frenkvs.devmod.ui.editor.components.LeftColumnComponent;
import com.frenkvs.devmod.ui.editor.components.ModeBadge;
import com.frenkvs.devmod.ui.editor.components.ScrollableContentArea;
import com.frenkvs.devmod.ui.editor.components.SlotSelector;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import com.frenkvs.devmod.ui.editor.favorites.FavoritePresetStore;
import com.frenkvs.devmod.ui.editor.modules.ArmorModule;
import com.frenkvs.devmod.ui.editor.modules.FoodModule;
import com.frenkvs.devmod.ui.editor.modules.FuelModule;
import com.frenkvs.devmod.ui.editor.modules.RangedModule;
import com.frenkvs.devmod.ui.editor.modules.UsableModule;
import com.frenkvs.devmod.ui.editor.modules.WeaponModule;
import com.frenkvs.devmod.ui.editor.systems.ConfirmDialog;
import com.frenkvs.devmod.ui.editor.systems.HelpOverlay;
import com.frenkvs.devmod.ui.editor.systems.CraftingInfoPanel;
import com.frenkvs.devmod.ui.editor.systems.MultiEditManager;
import com.frenkvs.devmod.ui.editor.systems.MultiEditPanel;
import com.frenkvs.devmod.ui.editor.systems.TemplateOverlay;
import com.frenkvs.devmod.ui.editor.systems.PresetSelectorOverlay;
import com.frenkvs.devmod.ui.editor.systems.DebugPanel;
import com.frenkvs.devmod.ui.editor.debug.DebugOverlay;
import com.frenkvs.devmod.network.ArmorStatsPayloadV2;
import com.frenkvs.devmod.network.EditorApplyConfirmPayload;
import com.frenkvs.devmod.util.DatapackIO;
import com.frenkvs.devmod.integration.PufferfishCompat;
import net.neoforged.fml.ModList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Unified Item Editor Screen.
 * Main entry point for all item editing functionality.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#31-unified-editor-architecture
 */
public class ItemEditorScreen extends Screen {

    // ═══════════════════════════════════════════════════════════════
    // LAYOUT
    // ═══════════════════════════════════════════════════════════════

    private final ResponsiveLayout layout;
    private final EditorLayout editorLayout = new EditorLayout();
    private static final String DEFAULT_DATAPACK_NAME = "devmod_balance_auto";
    private static final int LOW_CONF_DIALOG_WIDTH = 320;
    private static final int LOW_CONF_DIALOG_HEIGHT = 176;
    private static final int LOW_CONF_DIALOG_PADDING = 12;
    private static final int LOW_CONF_DIALOG_TEXT_START_Y = 30;
    private static final int LOW_CONF_DIALOG_TEXT_LINE_STEP = 12;
    private static final int LOW_CONF_DIALOG_HINT_START_Y = 72;
    private static final int LOW_CONF_DIALOG_STATUS_OFFSET_Y = 44;
    private static final int LOW_CONF_DIALOG_BTN_WIDTH = 88;
    private static final int LOW_CONF_DIALOG_BTN_HEIGHT = 16;
    private static final int LOW_CONF_DIALOG_BTN_TEXT_Y = 4;
    private static final int LOW_CONF_DIALOG_BTN_TEXT_CONTINUE_X = 12;
    private static final int LOW_CONF_DIALOG_BTN_TEXT_WHITELIST_X = 10;
    private static final int LOW_CONF_DIALOG_BTN_TEXT_CANCEL_X = 18;
    private static final int STATUS_MESSAGE_BOTTOM_OFFSET = 60;
    private static final int STATUS_MESSAGE_PADDING_X = 10;
    private static final int STATUS_MESSAGE_PADDING_Y = 4;
    private static final int STATUS_MESSAGE_SCREEN_MARGIN = 4;
    private static final int STATUS_MESSAGE_BG = 0xE0000000;
    private static final int HISTORY_PANEL_WIDTH = 260;
    private static final int HISTORY_PANEL_HEIGHT = 200;
    private static final int HISTORY_PANEL_MARGIN_RIGHT = 8;
    private static final int HISTORY_PANEL_MARGIN_TOP = 8;
    private static final int HISTORY_PANEL_TITLE_OFFSET_X = 8;
    private static final int HISTORY_PANEL_TITLE_OFFSET_Y = 8;
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
    private static final String PINNED_PREFIX = "Pinned ";
    private static final String UNPINNED_PREFIX = "Unpinned ";
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
    private static final String SOURCE_GLOBAL_OVERRIDE = "Global override available (serverconfig/devmod/devmod-items.toml)";
    private static final String SOURCE_GLOBAL = "Global (serverconfig/devmod/devmod-items.toml)";
    private static final String SOURCE_VANILLA = "Vanilla (no overrides)";
    private static final int TOOLTIP_WIDTH_PADDING = 8;
    private static final int TOOLTIP_HEIGHT = 14;
    private static final int TOOLTIP_OFFSET_X = 12;
    private static final int TOOLTIP_OFFSET_Y = 20;
    private static final int TOOLTIP_SCREEN_MARGIN = 4;
    private static final int TOOLTIP_TEXT_OFFSET_X = 4;
    private static final int TOOLTIP_TEXT_OFFSET_Y = 3;
    private static final int TOOLTIP_BG = 0xF0100010;
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
    private static final int DEV_PANEL_BG = 0xE0101020;
    private static final int DEV_PANEL_TITLE_COLOR = 0xFFFFFFFF;
    private static final String DEV_PANEL_TITLE_TEXT = "§b[Dev Mode]";
    private static final int STATUS_TICKS_DURATION = 60;
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_SLOT_SIZE = 20;
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int HOTBAR_BOTTOM_OFFSET = 22;

    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private final ItemStack item;
    private final ItemStack originalItem;
    private final EditorStartTab requestedTab;
    private EditorModule activeModule;
    private SlotSelector.SlotInfo selectedSlot;
    private static final float MIN_CONFIDENCE_FOR_AUTO_EDIT = 0.8f;
    // Mode flags
    private boolean isPreviewMode = true;
    private boolean isGlobalMode = false;
    private boolean showDevPanel = false;
    private boolean f3Held = false;
    private final PerformanceMonitor perfMonitor = PerformanceMonitor.INSTANCE;

    // Components
    private final HeaderComponent header = new HeaderComponent();
    private final LeftColumnComponent leftColumn = new LeftColumnComponent();
    private final FooterComponent footer = new FooterComponent();
    private final ScrollableContentArea scrollArea = new ScrollableContentArea();

    // UI state
    private String statusMessage = null;
    private int statusColor = 0;
    private int statusTicks = 0;

    // Tooltip state
    private String tooltipText = null;
    private int tooltipX = 0;
    private int tooltipY = 0;

    // Dialog state
    private ConfirmDialog activeDialog = null;
    private long lastSaveTimestamp = 0;
    private boolean showHistoryPanel = false;
    private int historyScrollOffset = 0;
    private boolean showPresetsPanel = false;
    private boolean showTemplatesPanel = false;
    private boolean showCraftingPanel = false;
    private boolean showLowConfidenceDialog = false;
    private WeaponTypeDetector.DetectionResult pendingDetection = null;
    private String lowConfidenceStatus = null;
    private String lastLoadedPreset = null;
    private final FavoritePresetStore favoriteStore = new FavoritePresetStore();
    private OverlayType activeOverlay = OverlayType.NONE;
    private OverlayType overlayBeforeSwitch = OverlayType.NONE;
    private final CraftingInfoPanel craftingPanel = new CraftingInfoPanel();

    // Help overlay
    private final HelpOverlay helpOverlay = new HelpOverlay();

    // Debug panel
    private DebugPanel debugPanel;

    // Multi-edit subsystem
    private MultiEditManager multiEditManager;
    private MultiEditPanel multiEditPanel;
    private boolean showMultiEditPanel = false;
    private TemplateOverlay templateOverlay;
    private PresetSelectorOverlay presetSelectorOverlay;

    private enum OverlayType { NONE, HISTORY, PRESETS, TEMPLATES, CRAFTING }

    // ═══════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════

    public ItemEditorScreen(ItemStack item, EditorStartTab startTab) {
        super(Component.literal("Item Editor"));
        this.item = item.copy();
        this.originalItem = item.copy();
        this.requestedTab = startTab;
        this.layout = new ResponsiveLayout();
        loadUserModePreference();
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void init() {
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
        activeModule = resolveModule(item, requestedTab);
        activeModule.setStatusConsumer((msg, color) -> showStatus(msg, color == null ? UIConstants.Accent.INFO() : color));
        activeModule.setModuleSwitchCallback(this::switchModule);
        activeModule.setItem(item);
        activeModule.init(layout);
        warnLowConfidence(item);
        activeModule.setDirtyTrackingEnabled(true);
        activeModule.clearDirty();
        configureHeader();
        configureLeftColumn();
        configureFooterCallbacks();

        // Initialize multi-edit subsystem
        this.multiEditManager = new MultiEditManager();
        this.multiEditPanel = new MultiEditPanel(multiEditManager, () -> !isPreviewMode, this::getActiveItemType);
        this.multiEditPanel.setShowDialogCallback(dialog -> {
            activeDialog = dialog;
            activeDialog.show();
        });
        this.multiEditManager.setPersistenceHandler((stack, slot) -> persistMultiEditItem(stack, slot == null ? -1 : slot));
        this.debugPanel = new DebugPanel();
        this.templateOverlay = new TemplateOverlay();
        this.templateOverlay.onClose(this::closeOverlay);
        this.templateOverlay.onApply(this::handleTemplateApply);

        this.presetSelectorOverlay = new PresetSelectorOverlay();
        this.presetSelectorOverlay.setContext(getActiveItemType());
        this.presetSelectorOverlay.onClose(this::closeOverlay);
        this.presetSelectorOverlay.onApply(this::applyPreset);
        this.presetSelectorOverlay.onDelete(this::deletePreset);
        this.presetSelectorOverlay.onRename(this::renamePreset);
        this.presetSelectorOverlay.onSaveCurrent(this::saveCurrentAsPreset);

        // Invalidate cache on init
        EditorCache.INSTANCE.invalidateAll();
    }

    private EditorModule resolveModule(ItemStack stack, EditorStartTab requested) {
        return switch (requested) {
            case WEAPON -> {
                var detection = WeaponTypeDetector.detectDetailed(stack);
                if (WeaponTypeDetector.isRanged(detection.type())) {
                    yield new RangedModule(detection.type() == WeaponTypeDetector.WeaponType.CROSSBOW
                        ? com.frenkvs.devmod.ui.editor.modules.RangedModule.RangedVariant.CROSSBOW
                        : com.frenkvs.devmod.ui.editor.modules.RangedModule.RangedVariant.BOW);
                }
                if (detection.type() == WeaponTypeDetector.WeaponType.TRIDENT) {
                    yield new WeaponModule(com.frenkvs.devmod.ui.editor.modules.WeaponModule.WeaponVariant.TRIDENT);
                }
                if (detection.type() == WeaponTypeDetector.WeaponType.MACE) {
                    yield new WeaponModule(com.frenkvs.devmod.ui.editor.modules.WeaponModule.WeaponVariant.MACE);
                }
                yield new WeaponModule();
            }
            case ARMOR -> new ArmorModule();
            case GENERAL -> {
                // Auto-detect based on item type
                if (ArmorConfigManager.isArmor(stack)) {
                    DevMod.LOGGER.warn("[ItemEditor] Requested GENERAL but item is armor; falling back to ARMOR module.");
                    yield new ArmorModule();
                }
                var detailed = WeaponTypeDetector.detectDetailed(stack);
                if (WeaponTypeDetector.isRanged(detailed.type())) {
                    DevMod.LOGGER.warn("[ItemEditor] Requested GENERAL but item is ranged weapon; falling back to RANGED module.");
                    yield new RangedModule(detailed.type() == WeaponTypeDetector.WeaponType.CROSSBOW
                        ? com.frenkvs.devmod.ui.editor.modules.RangedModule.RangedVariant.CROSSBOW
                        : com.frenkvs.devmod.ui.editor.modules.RangedModule.RangedVariant.BOW);
                }
                if (WeaponTypeDetector.isMelee(detailed.type())) {
                    DevMod.LOGGER.warn("[ItemEditor] Requested GENERAL but item is melee weapon; falling back to WEAPON module.");
                    if (detailed.type() == WeaponTypeDetector.WeaponType.TRIDENT) {
                        yield new WeaponModule(com.frenkvs.devmod.ui.editor.modules.WeaponModule.WeaponVariant.TRIDENT);
                    }
                    if (detailed.type() == WeaponTypeDetector.WeaponType.MACE) {
                        yield new WeaponModule(com.frenkvs.devmod.ui.editor.modules.WeaponModule.WeaponVariant.MACE);
                    }
                    yield new WeaponModule();
                }
                yield new PlaceholderModule("general", "Item Editor");
            }
            case RECIPE -> new com.frenkvs.devmod.ui.editor.modules.RecipeModule();
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
            showStatus("Unsaved changes - save or discard first", UIConstants.Accent.ORANGE());
            return;
        }

        // Close current module
        if (activeModule != null) {
            activeModule.onClose();
        }

        // Resolve and initialize the new module
        activeModule = resolveModule(item, targetTab);
        activeModule.setStatusConsumer((msg, color) -> showStatus(msg, color == null ? UIConstants.Accent.INFO() : color));
        activeModule.setModuleSwitchCallback(this::switchModule);
        activeModule.setItem(item);
        activeModule.init(layout);
        activeModule.setDirtyTrackingEnabled(true);
        activeModule.clearDirty();

        // Update UI components
        configureHeader();
        configureLeftColumn();

        // Reset scroll
        scrollArea.setScrollOffset(0);

        showStatus("Switched to " + activeModule.getTitle(), UIConstants.Accent.INFO());
        DevMod.LOGGER.info("[ItemEditor] Switched module to: {}", targetTab);
    }

    private void warnLowConfidence(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        var detection = WeaponTypeDetector.detectDetailed(stack);
        if (detection == null) return;
        boolean isWeaponPath = requestedTab == EditorStartTab.WEAPON || activeModule instanceof WeaponModule || activeModule instanceof RangedModule;
        if (!isWeaponPath) return;
        float threshold = (float) Math.max(EditorClientConfig.EDITOR_WEAPON_MIN_CONFIDENCE.get(), (double) MIN_CONFIDENCE_FOR_AUTO_EDIT);
        if (detection.confidence() < threshold
            && EditorClientConfig.EDITOR_WEAPON_HEURISTIC_ENABLED.get()
            && detection.method() == WeaponTypeDetector.DetectionMethod.ATTRIBUTE_HEURISTIC) {
            pendingDetection = detection;
            showLowConfidenceDialog = true;
            lowConfidenceStatus = null;
            DevMod.LOGGER.warn("[ItemEditor] Low confidence detection: {} ({})", detection.type(), detection.method());
            scrollArea.setScrollOffset(0); // ensure dialog visible
        }
    }

    private void renderLowConfidenceDialog(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (pendingDetection == null) return;
        Font safeFont = Objects.requireNonNull(font, "font");
        int w = EditorSpacing.snapToGrid(LOW_CONF_DIALOG_WIDTH);
        int h = EditorSpacing.snapToGrid(LOW_CONF_DIALOG_HEIGHT);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        g.fill(x, y, x + w, y + h, UIConstants.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(g, x, y, w, h, UIConstants.Border.ACCENT());
        String title = "Low Confidence Detection";
        g.drawString(safeFont, title, x + LOW_CONF_DIALOG_PADDING, y + LOW_CONF_DIALOG_PADDING,
            UIConstants.Text.TITLE(), false);
        var id = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item.getItem()));
        String itemId = id == null ? "<unknown>" : id.toString();
        int textX = x + LOW_CONF_DIALOG_PADDING;
        int textY = y + LOW_CONF_DIALOG_TEXT_START_Y;
        g.drawString(safeFont, "Item: " + itemId, textX, textY, UIConstants.Text.SECONDARY(), false);
        textY += LOW_CONF_DIALOG_TEXT_LINE_STEP;
        g.drawString(safeFont, "Detected: " + pendingDetection.type() + " via " + pendingDetection.method(),
            textX, textY, UIConstants.Text.SECONDARY(), false);
        textY += LOW_CONF_DIALOG_TEXT_LINE_STEP;
        g.drawString(safeFont, "Confidence: " + String.format("%.0f%%", pendingDetection.confidence() * 100),
            textX, textY, UIConstants.Text.SECONDARY(), false);
        textY = y + LOW_CONF_DIALOG_HINT_START_Y;
        g.drawString(safeFont, "Add to whitelist/tag to skip this warning next time.",
            textX, textY, UIConstants.Text.MUTED(), false);
        textY += LOW_CONF_DIALOG_TEXT_LINE_STEP;
        g.drawString(safeFont, "Config: config/devmod/weapon_whitelist.json",
            textX, textY, UIConstants.Text.MUTED(), false);
        if (lowConfidenceStatus != null) {
            g.drawString(safeFont, lowConfidenceStatus, textX, y + h - LOW_CONF_DIALOG_STATUS_OFFSET_Y,
                UIConstants.Accent.ORANGE(), false);
        }

        // Buttons
        int btnW = EditorSpacing.snapToGrid(LOW_CONF_DIALOG_BTN_WIDTH);
        int btnH = EditorSpacing.snapToGrid(LOW_CONF_DIALOG_BTN_HEIGHT);
        int btnSpacing = EditorSpacing.S;
        int totalBtnWidth = btnW * 3 + btnSpacing * 2;
        int btnStartX = x + (w - totalBtnWidth) / 2;
        int btnY = y + h - EditorSpacing.L;
        int btnContinueX = btnStartX;
        int btnWhitelistX = btnStartX + btnW + btnSpacing;
        int btnCancelX = btnStartX + (btnW + btnSpacing) * 2;
        g.fill(btnContinueX, btnY, btnContinueX + btnW, btnY + btnH, UIConstants.Background.INPUT());
        g.fill(btnWhitelistX, btnY, btnWhitelistX + btnW, btnY + btnH, UIConstants.Background.INPUT());
        g.fill(btnCancelX, btnY, btnCancelX + btnW, btnY + btnH, UIConstants.Background.INPUT());
        g.drawString(safeFont, "Continue", btnContinueX + LOW_CONF_DIALOG_BTN_TEXT_CONTINUE_X,
            btnY + LOW_CONF_DIALOG_BTN_TEXT_Y, UIConstants.Text.PRIMARY(), false);
        g.drawString(safeFont, "Whitelist", btnWhitelistX + LOW_CONF_DIALOG_BTN_TEXT_WHITELIST_X,
            btnY + LOW_CONF_DIALOG_BTN_TEXT_Y, UIConstants.Text.PRIMARY(), false);
        g.drawString(safeFont, "Cancel", btnCancelX + LOW_CONF_DIALOG_BTN_TEXT_CANCEL_X,
            btnY + LOW_CONF_DIALOG_BTN_TEXT_Y, UIConstants.Text.PRIMARY(), false);
        lowConfidenceContinueBounds = new ResponsiveLayout.Rect(btnContinueX, btnY, btnW, btnH);
        lowConfidenceWhitelistBounds = new ResponsiveLayout.Rect(btnWhitelistX, btnY, btnW, btnH);
        lowConfidenceCancelBounds = new ResponsiveLayout.Rect(btnCancelX, btnY, btnW, btnH);
    }

    private ResponsiveLayout.Rect lowConfidenceContinueBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect lowConfidenceWhitelistBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect lowConfidenceCancelBounds = ResponsiveLayout.Rect.EMPTY;

    private boolean handleLowConfidenceClick(double mouseX, double mouseY, int button) {
        if (button != 0 || pendingDetection == null) return false;
        if (lowConfidenceContinueBounds.contains(mouseX, mouseY)) {
            showLowConfidenceDialog = false;
            pendingDetection = null;
            lowConfidenceStatus = null;
            return true;
        }
        if (lowConfidenceWhitelistBounds.contains(mouseX, mouseY)) {
            boolean added = addCurrentItemToWhitelist();
            if (!added) {
                lowConfidenceStatus = "Whitelist update failed. Check logs.";
            } else {
                showLowConfidenceDialog = false;
                pendingDetection = null;
                lowConfidenceStatus = null;
            }
            return true;
        }
        if (lowConfidenceCancelBounds.contains(mouseX, mouseY)) {
            onClose();
            Minecraft.getInstance().setScreen(null);
            showLowConfidenceDialog = false;
            pendingDetection = null;
            lowConfidenceStatus = null;
            return true;
        }
        return lowConfidenceContinueBounds.contains(mouseX, mouseY)
            || lowConfidenceWhitelistBounds.contains(mouseX, mouseY)
            || lowConfidenceCancelBounds.contains(mouseX, mouseY);
    }

    private boolean addCurrentItemToWhitelist() {
        if (item == null || item.isEmpty()) {
            return false;
        }
        try {
            WeaponTypeDetector.WeaponType type = pendingDetection == null
                ? WeaponTypeDetector.WeaponType.GENERIC_MELEE
                : pendingDetection.type();
            if (type == WeaponTypeDetector.WeaponType.UNKNOWN
                || type == WeaponTypeDetector.WeaponType.NOT_A_WEAPON
                || type == WeaponTypeDetector.WeaponType.SHIELD) {
                type = WeaponTypeDetector.WeaponType.GENERIC_MELEE;
            }
            boolean added = WeaponTypeDetector.addToWhitelist(Objects.requireNonNull(item.getItem()), type);
            var id = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item.getItem()));
            String label = id == null ? "<unknown>" : id.toString();
            if (added) {
                showStatus("Whitelisted " + label, UIConstants.Accent.GREEN());
            }
            return added;
        } catch (Exception e) {
            DevMod.LOGGER.warn("[ItemEditor] Failed to add item to whitelist", e);
            return false;
        }
    }

    private void configureHeader() {
        header.clearTabs();
        if (activeModule != null) {
            List<ModuleTab> tabs = activeModule.getTabs();
            for (ModuleTab tab : tabs) {
                header.addTab(tab.id(), tab.label());
            }
            header.selectTab(activeModule.getActiveTabIndex());
        }

        header.onTabChange(index -> {
            if (activeModule != null) {
                activeModule.setActiveTab(index);
                scrollArea.scrollToTop();
            }
        });
        header.onClose(this::handleCloseRequest);

        header.getModeBadge()
            .badgeType(ModeBadge.BadgeType.MODE)
            .mode(isPreviewMode ? ModeBadge.Mode.PREVIEW : ModeBadge.Mode.APPLY)
            .onModeChange(this::handleModeChange);

        header.getScopeBadge()
            .badgeType(ModeBadge.BadgeType.SCOPE)
            .scope(isGlobalMode ? ModeBadge.Scope.GLOBAL : ModeBadge.Scope.SPECIFIC)
            .onScopeChange(scope -> isGlobalMode = scope == ModeBadge.Scope.GLOBAL);
    }

    private void configureLeftColumn() {
        SlotSelector.SlotType slotType = switch (requestedTab) {
            case ARMOR -> SlotSelector.SlotType.ARMOR;
            default -> SlotSelector.SlotType.WEAPON;
        };
        leftColumn.slotType(slotType);
        leftColumn.item(item);
        leftColumn.onSlotSelect(this::handleSlotSwitch);
        selectedSlot = leftColumn.getSelectedSlot();
    }

    private void updateLeftColumnStats() {
        leftColumn.clearStats();
        // If preview is active, prefer the module's preview item for display to avoid mutating real item
        ItemStack displayItem = item;
        if (isPreviewMode && activeModule != null) {
            try {
                var preview = activeModule.getPreviewItem();
                if (preview != null) displayItem = preview;
            } catch (Exception ignored) {}
        }
        leftColumn.item(displayItem);
        if (activeModule instanceof WeaponModule weaponModule) {
            var stats = weaponModule.getStats();
            leftColumn.addStat("Attack", stats.attackDamage, "+%.1f");
            leftColumn.addStat("Speed", stats.attackSpeed, "+%.1f");
            leftColumn.addStat("Crit", stats.critChance * 100f, "%.0f%%");
        } else if (activeModule instanceof RangedModule rangedModule) {
            var stats = rangedModule.getStats();
            leftColumn.addStat("Draw", stats.drawSpeed, "%.2fx");
            leftColumn.addStat("Proj Spd", stats.projectileSpeed, "%.2f");
            leftColumn.addStat("Accuracy", stats.accuracy * 100f, "%.0f%%");
        } else if (activeModule instanceof ArmorModule armorModule) {
            var stats = armorModule.getStats();
            leftColumn.addStat("Defense", stats.physicalReduction * 100f, "%.0f%%");
            leftColumn.addStat("Fire", stats.fireReduction * 100f, "%.0f%%");
            leftColumn.addStat("Magic", stats.magicReduction * 100f, "%.0f%%");
        }

        if (activeModule != null) {
            leftColumn.pendingChanges(activeModule.getPendingChanges().size());
        }
        if (lastSaveTimestamp > 0) {
            leftColumn.lastSaved(lastSaveTimestamp);
        }
    }

    private void handleSlotSwitch(SlotSelector.SlotInfo slot) {
        if (slot == null || Objects.equals(selectedSlot, slot)) {
            return;
        }
        if (!isPreviewMode && activeModule != null && activeModule.hasUnsavedChanges()) {
            activeDialog = ConfirmDialog.switchSlot(slot.label(), activeModule.getPendingChanges().size(),
                () -> {
                    selectedSlot = slot;
                    showStatus("Switched to " + slot.label(), UIConstants.Accent.BLUE());
                },
                () -> {});
            activeDialog.show();
            return;
        }
        selectedSlot = slot;
        showStatus("Switched to " + slot.label(), UIConstants.Accent.BLUE());
        if (showMultiEditPanel) {
            refreshMultiEditSelection();
        }
    }

    private void handleModeChange(ModeBadge.Mode mode) {
        boolean preview = mode == ModeBadge.Mode.PREVIEW;
        if (preview == isPreviewMode) {
            return;
        }

        // Switching from APPLY -> PREVIEW with pending changes: confirm discard
        if (preview && !isPreviewMode && activeModule != null && activeModule.hasUnsavedChanges()) {
            activeDialog = ConfirmDialog.switchModeToPreview(
                activeModule.getPendingChanges().size(),
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
            activeDialog.show();
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
        isPreviewMode = true;
        if (activeModule != null) {
            if (discardChanges) {
                activeModule.resetToOriginal();
                activeModule.clearDirty();
            }
            activeModule.applyPreview();
            activeModule.logEvent(discardChanges
                ? "Switched to PREVIEW (discarded changes)"
                : "Switched to PREVIEW");
        }
        showStatus("Preview Mode", UIConstants.Accent.CYAN());
    }

    private void switchToApplyMode() {
        isPreviewMode = false;
        if (activeModule != null) {
            activeModule.clearPreview();
            if (!activeModule.hasUnsavedChanges() && activeModule.hasPendingDiff()) {
                activeModule.markDirty("Pending changes from preview");
            }
            activeModule.logEvent("Switched to APPLY (dirty on)");
        }
        showStatus("Apply Mode", UIConstants.Accent.GREEN());
    }

    /**
     * Persist user preference for editor mode.
     */
    private void saveUserModePreference() {
        try {
            EditorClientConfig.EDITOR_DEFAULT_MODE.set(isPreviewMode ? EditorClientConfig.EditorDefaultMode.PREVIEW : EditorClientConfig.EditorDefaultMode.APPLY);
            showStatus("Default mode saved", UIConstants.Accent.BLUE());
        } catch (Exception ignored) {
            // Best-effort: config may be read-only in some contexts
        }
    }

    /**
     * Load user preference for editor mode from config.
     */
    private void loadUserModePreference() {
        try {
            EditorClientConfig.EditorDefaultMode pref = EditorClientConfig.EDITOR_DEFAULT_MODE.get();
            isPreviewMode = pref != EditorClientConfig.EditorDefaultMode.APPLY;
        } catch (Exception ignored) {
            isPreviewMode = true;
        }
    }

    private void configureFooterCallbacks() {
        footer
            .onUndo(() -> {
                if (activeModule != null && activeModule.canUndo()) {
                    activeModule.undo();
                    showStatus("Undone", UIConstants.Accent.BLUE());
                }
            })
            .onRedo(() -> {
                if (activeModule != null && activeModule.canRedo()) {
                    activeModule.redo();
                    showStatus("Redone", UIConstants.Accent.BLUE());
                }
            })
            .onApply(this::applyChanges)
            .onAction(this::handleFooterAction);
    }

    private void handleFooterAction(String actionId) {
        if (actionId == null) return;
        switch (actionId) {
            case "reset" -> {
                if (activeModule != null) {
                    activeDialog = ConfirmDialog.resetToDefault(() -> {
                        activeModule.resetToOriginal();
                        showStatus("Reset to original", UIConstants.Accent.ORANGE());
                    }, () -> {});
                    activeDialog.show();
                }
            }
            case "cancel" -> handleCloseRequest();
            case "history" -> {
                toggleOverlay(OverlayType.HISTORY);
                historyScrollOffset = 0;
            }
            case "export" -> handleExport();
            case "import" -> handleImport();
            case "presets" -> {
                if (!supportsDataOps()) {
                    showStatus("Presets available only for weapons/armors", UIConstants.Accent.ORANGE());
                    return;
                }
                toggleOverlay(OverlayType.PRESETS);
            }
            case "templates" -> {
                openTemplatesOverlay();
            }
            case "recipe" -> {
                craftingPanel.show(item);
                toggleOverlay(OverlayType.CRAFTING);
            }
            default -> {}
        }
    }

    private void toggleOverlay(OverlayType type) {
        if (activeOverlay == type) {
            closeOverlay();
            return;
        }
        overlayBeforeSwitch = type == OverlayType.NONE ? OverlayType.NONE : activeOverlay;
        activeOverlay = type;
        updateOverlayFlags();
    }

    private void closeOverlay() {
        activeOverlay = overlayBeforeSwitch;
        overlayBeforeSwitch = OverlayType.NONE;
        updateOverlayFlags();
    }

    private void updateOverlayFlags() {
        showHistoryPanel = activeOverlay == OverlayType.HISTORY;
        showPresetsPanel = activeOverlay == OverlayType.PRESETS;
        showTemplatesPanel = activeOverlay == OverlayType.TEMPLATES;
        showCraftingPanel = activeOverlay == OverlayType.CRAFTING;
        if (activeOverlay != OverlayType.TEMPLATES && templateOverlay != null) {
            templateOverlay.resetSearch();
        }
        if (activeOverlay != OverlayType.CRAFTING) {
            craftingPanel.hide();
        }
        // Manage PresetSelectorOverlay visibility
        if (presetSelectorOverlay != null) {
            if (activeOverlay == OverlayType.PRESETS) {
                presetSelectorOverlay.setContext(getActiveItemType());
                presetSelectorOverlay.show();
            } else {
                presetSelectorOverlay.resetSearch();
                presetSelectorOverlay.hide();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        perfMonitor.startFrame();
        // Dark overlay background, but leave vanilla hotbar area unobscured
        int hotbarReserve = ScaledCoord.scaleDim(UIConstants.Spacing.XXL);
        graphics.fill(0, 0, width, Math.max(0, height - hotbarReserve), UIConstants.Background.OVERLAY());

        // Get layout areas (centralized in EditorLayout)
        var panelBounds = editorLayout.getPanelBounds();
        var headerBounds = editorLayout.getHeaderBounds();
        var footerBounds = editorLayout.getFooterBounds();
        var leftBounds = editorLayout.getLeftColumnBounds();
        var contentBounds = editorLayout.getContentBounds();
        int contentY = contentBounds.y();
        int footerY = footerBounds.y();
        int contentHeight = contentBounds.height();

        // Main panel background
        graphics.fill(panelBounds.x(), panelBounds.y(),
                     panelBounds.right(), panelBounds.bottom(),
                     UIConstants.Background.PANEL());

        // Panel border
        AxiomRenderer.drawBorder(graphics, panelBounds.x(), panelBounds.y(),
                                panelBounds.width(), panelBounds.height(),
                                UIConstants.Border.DEFAULT());

        // Header (tabs + badges + close)
        header.render(graphics, headerBounds.x(), headerBounds.y(), headerBounds.width(), mouseX, mouseY);
        if (header.getModeBadge().isHovered()) {
            tooltipText = header.getModeBadge().getTooltipText();
            tooltipX = mouseX;
            tooltipY = mouseY;
        } else if (header.getScopeBadge().isHovered()) {
            tooltipText = header.getScopeBadge().getTooltipText();
            tooltipX = mouseX;
            tooltipY = mouseY;
        }

        // Left column (preview + slots + info)
        if (activeModule != null) {
            updateLeftColumnStats();
            leftColumn.pendingChanges(activeModule.getPendingChanges().size());
        }
        leftColumn.render(graphics, leftBounds.x(), leftBounds.y(), leftBounds.width(), leftBounds.height(), mouseX, mouseY, partialTick);

        // Render multi-edit panel if visible (placed beneath left column area)
        if (showMultiEditPanel && multiEditPanel != null) {
            int panelX = leftBounds.x() + UIConstants.Spacing.MD;
            int panelY = contentY + UIConstants.Spacing.MD;
            int panelW = leftBounds.width() - UIConstants.Spacing.MD * 2;
            multiEditPanel.render(graphics, font, panelX, panelY, panelW, mouseX, mouseY);
        }

        // Content area (scrollable)
        int contentX = contentBounds.x() + UIConstants.Spacing.MD;
        int contentWidth = contentBounds.width() - UIConstants.Spacing.MD * 2;
        graphics.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, UIConstants.Background.CONTENT());
        if (activeModule != null) {
            perfMonitor.startTiming("render_content");
            int viewportHeight = contentHeight - UIConstants.Spacing.MD * 2;
            scrollArea.render(graphics, contentX, contentY, contentWidth, contentHeight,
                mouseX, mouseY, partialTick, (g, x, y, w, mx, my) -> {
                    ResponsiveLayout.Rect bounds = new ResponsiveLayout.Rect(x, y, w, viewportHeight);
                    // Pass raw mouseY (screen space) - render Y coordinates are also screen space
                    // Scroll adjustment is only needed for click handling, not hover detection
                    activeModule.renderContent(g, bounds, mx, my);
                    return activeModule.calculateContentHeight();
                });
            perfMonitor.endTiming("render_content");
        }

        // Footer
        int pending = activeModule != null ? activeModule.getPendingChanges().size() : 0;
        boolean dirty = activeModule != null && (activeModule.hasUnsavedChanges() || pending > 0);
        footer
            .canUndo(activeModule != null && activeModule.canUndo())
            .canRedo(activeModule != null && activeModule.canRedo())
            .canApply(activeModule != null)
            .isDirty(dirty)
            .pendingCount(pending);
        footer.render(graphics, panelBounds.x(), footerY, panelBounds.width(), mouseX, mouseY);

        // Render side panels if visible
        if (layout.showSidePanels()) {
            renderSidePanels(graphics, mouseX, mouseY);
        }

        // Status message overlay
        if (statusMessage != null && statusTicks > 0) {
            renderStatusMessage(graphics);
        }

        if (showHistoryPanel && activeModule != null) {
            renderHistoryPanel(graphics);
        }

        if (showPresetsPanel && supportsDataOps() && presetSelectorOverlay != null) {
            presetSelectorOverlay.render(graphics, font, width, height, mouseX, mouseY);
        }
        if (showTemplatesPanel) {
            templateOverlay.render(graphics, font, width, height, mouseX, mouseY);
        }
        if (showCraftingPanel) {
            craftingPanel.render(graphics, font, width, height, mouseX, mouseY);
        }

        // Render header dropdown overlays last so they appear above content
        header.renderOverlays(graphics, mouseX, mouseY);

        // Tooltip (rendered last, on top)
        if (tooltipText != null) {
            renderTooltip(graphics, tooltipText, tooltipX, tooltipY);
        }

        // Dev panel
        if (showDevPanel) {
            renderDevPanel(graphics, mouseX, mouseY);
        }

        // Module overlay (e.g., ItemPickerOverlay from RecipeModule)
        // Rendered after other overlays but before modal dialogs
        if (activeModule != null && activeModule.hasActiveOverlay()) {
            activeModule.renderOverlay(graphics, font, width, height, mouseX, mouseY);
        }

        // Modal dialog (rendered last, on top of everything)
        if (activeDialog != null && activeDialog.isVisible()) {
            activeDialog.render(graphics, font, width, height, mouseX, mouseY);
        }

        // Help overlay (rendered on top of dialogs)
        if (helpOverlay.isVisible()) {
            helpOverlay.render(graphics, font, width, height, mouseX, mouseY);
        }

        // Low-confidence dialog
        if (showLowConfidenceDialog && pendingDetection != null) {
            renderLowConfidenceDialog(graphics, font, mouseX, mouseY);
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
    }

    private void renderSidePanels(GuiGraphics graphics, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font, "font cannot be null");

        // Left panel (favorites)
        ResponsiveLayout.Rect leftPanel = layout.getFavoritesPanelArea();
        if (!leftPanel.isEmpty()) {
            graphics.fill(leftPanel.x(), leftPanel.y(), leftPanel.right(), leftPanel.bottom(),
                         UIConstants.Background.PANEL());
            AxiomRenderer.drawBorder(graphics, leftPanel.x(), leftPanel.y(),
                                    leftPanel.width(), leftPanel.height(), UIConstants.Border.DEFAULT());
            graphics.drawString(safeFont, FAVORITES_TITLE_TEXT, leftPanel.x() + FAVORITES_TITLE_OFFSET_X,
                leftPanel.y() + FAVORITES_TITLE_OFFSET_Y,
                               UIConstants.Text.SECONDARY(), false);

            List<ItemEditorDataManager.PresetData> favorites = getFavoritePresetsForActiveType();
            int rowHeight = FAVORITES_ROW_HEIGHT;
            int startY = leftPanel.y() + FAVORITES_LIST_START_Y;
            int maxRows = Math.max(0, (leftPanel.height() - FAVORITES_LIST_BOTTOM_PADDING) / rowHeight);
            for (int i = 0; i < Math.min(maxRows, favorites.size()); i++) {
                ItemEditorDataManager.PresetData preset = favorites.get(i);
                int rowY = startY + i * rowHeight;
                boolean hovered = mouseX >= leftPanel.x() + FAVORITES_ROW_HIT_INSET
                    && mouseX <= leftPanel.right() - FAVORITES_ROW_HIT_INSET
                    && mouseY >= rowY && mouseY <= rowY + rowHeight;
                if (hovered) {
                    graphics.fill(leftPanel.x() + FAVORITES_ROW_HOVER_INSET, rowY,
                        leftPanel.right() - FAVORITES_ROW_HOVER_INSET, rowY + rowHeight, UIConstants.Background.HOVER());
                    if (tooltipText == null) {
                        tooltipText = preset.name;
                        tooltipX = mouseX;
                        tooltipY = mouseY;
                    }
                }
                String label = preset.name == null ? FAVORITES_LABEL_FALLBACK : preset.name;
                graphics.drawString(safeFont, FAVORITES_LABEL_PREFIX + label, leftPanel.x() + FAVORITES_ROW_TEXT_OFFSET_X,
                    rowY + FAVORITES_ROW_TEXT_OFFSET_Y,
                    hovered ? UIConstants.Text.PRIMARY() : UIConstants.Text.SECONDARY(), false);
            }

            // Pin toggle for last loaded preset
            if (lastLoadedPreset != null) {
                boolean isFav = favoriteStore.isFavorite(getActiveItemType(), lastLoadedPreset);
                String pinLabel = isFav ? FAVORITES_UNPIN_TEXT : FAVORITES_PIN_TEXT;
                int btnY = leftPanel.bottom() - FAVORITES_PIN_BUTTON_OFFSET_Y;
                int btnW = FAVORITES_PIN_BUTTON_WIDTH;
                int btnH = FAVORITES_PIN_BUTTON_HEIGHT;
                int btnX = leftPanel.x() + FAVORITES_PIN_BUTTON_OFFSET_X;
                graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, UIConstants.Button.NORMAL());
                AxiomRenderer.drawBorder(graphics, btnX, btnY, btnW, btnH, UIConstants.Border.DEFAULT());
                graphics.drawString(safeFont, pinLabel, btnX + FAVORITES_PIN_TEXT_OFFSET_X,
                    btnY + FAVORITES_PIN_TEXT_OFFSET_Y, UIConstants.Text.PRIMARY(), false);
            }
        }

        // Right panel (dev mode)
        if (showDevPanel) {
            ResponsiveLayout.Rect rightPanel = layout.getDevModePanelArea();
            if (!rightPanel.isEmpty()) {
                graphics.fill(rightPanel.x(), rightPanel.y(), rightPanel.right(), rightPanel.bottom(),
                             UIConstants.Background.PANEL());
                AxiomRenderer.drawBorder(graphics, rightPanel.x(), rightPanel.y(),
                                        rightPanel.width(), rightPanel.height(), UIConstants.Border.DEFAULT());
                graphics.drawString(safeFont, DEV_PANEL_HEADER_TEXT, rightPanel.x() + DEV_PANEL_TEXT_OFFSET_X,
                    rightPanel.y() + DEV_PANEL_TEXT_OFFSET_Y,
                                   UIConstants.Text.SECONDARY(), false);
            }
        }
    }

    private void refreshMultiEditSelection() {
        if (multiEditManager == null) return;
        multiEditManager.clearSelection();
        var mc = Minecraft.getInstance();
        if (mc == null) return;
        var player = mc.player;
        if (player == null) return;
        var inv = player.getInventory();
        var targetItem = Objects.requireNonNull(item.getItem(), "item type cannot be null");
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (!stack.isEmpty() && stack.is(targetItem)) {
                multiEditManager.addToSelection(stack, i);
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

    private void renderHistoryPanel(GuiGraphics graphics) {
        if (activeModule == null) return;
        var safeFont = Objects.requireNonNull(font, "font cannot be null");
        ResponsiveLayout.Rect historyBounds = getHistoryPanelBounds();
        int panelWidth = historyBounds.width();
        int panelHeight = historyBounds.height();
        int x = historyBounds.x();
        int y = historyBounds.y();

        graphics.fill(x, y, x + panelWidth, y + panelHeight, UIConstants.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, x, y, panelWidth, panelHeight, UIConstants.Border.DEFAULT());

        graphics.drawString(safeFont, HISTORY_TITLE_TEXT, x + HISTORY_PANEL_TITLE_OFFSET_X, y + HISTORY_PANEL_TITLE_OFFSET_Y,
            UIConstants.Text.TITLE(), false);
        int listY = y + HISTORY_PANEL_LIST_OFFSET_Y;
        var entries = activeModule.getHistoryEntries();
        int lineHeight = HISTORY_PANEL_LINE_HEIGHT;
        int listHeight = panelHeight - HISTORY_PANEL_LIST_HEIGHT_PADDING;
        int maxScroll = Math.max(0, Math.max(0, entries.size() * lineHeight - listHeight));
        historyScrollOffset = Math.max(0, Math.min(historyScrollOffset, maxScroll));

        int visibleLines = listHeight / lineHeight;
        if (entries.isEmpty()) {
            graphics.drawString(safeFont, HISTORY_EMPTY_TEXT, x + HISTORY_PANEL_TEXT_OFFSET_X, listY,
                UIConstants.Text.MUTED(), false);
        } else {
            int startFromEnd = historyScrollOffset / lineHeight;
            int startIndex = Math.max(0, entries.size() - visibleLines - startFromEnd);
            int endIndex = Math.min(entries.size(), startIndex + visibleLines);

            for (int i = startIndex; i < endIndex; i++) {
                String entry = entries.get(entries.size() - 1 - i);
                int entryY = listY + (i - startIndex) * lineHeight;
                graphics.drawString(safeFont, entry, x + HISTORY_PANEL_TEXT_OFFSET_X, entryY,
                    UIConstants.Text.SECONDARY(), false);
            }
        }

        // Footer with clear button and counter
        int footerY = y + panelHeight - HISTORY_PANEL_FOOTER_OFFSET_Y;
        String countText = entries.isEmpty()
            ? HISTORY_SHOWING_EMPTY
            : HISTORY_SHOWING_PREFIX + Math.min(visibleLines, entries.size()) + HISTORY_SHOWING_SEPARATOR + entries.size();
        graphics.drawString(safeFont, countText, x + HISTORY_PANEL_TEXT_OFFSET_X, footerY,
            UIConstants.Text.MUTED(), false);

        // Clear button
        int clearW = HISTORY_PANEL_CLEAR_WIDTH;
        int clearH = HISTORY_PANEL_CLEAR_HEIGHT;
        int clearX = x + panelWidth - clearW - HISTORY_PANEL_CLEAR_OFFSET_X;
        int clearY = footerY - HISTORY_PANEL_CLEAR_OFFSET_Y;
        int clearBg = entries.isEmpty() ? UIConstants.Button.DISABLED() : UIConstants.Background.INPUT();
        graphics.fill(clearX, clearY, clearX + clearW, clearY + clearH, clearBg);
        AxiomRenderer.drawBorder(graphics, clearX, clearY, clearW, clearH, UIConstants.Border.DEFAULT());
        int clearColor = entries.isEmpty() ? UIConstants.Text.DISABLED() : UIConstants.Text.PRIMARY();
        graphics.drawString(safeFont, HISTORY_CLEAR_TEXT, clearX + HISTORY_PANEL_CLEAR_TEXT_OFFSET_X,
            clearY + HISTORY_PANEL_CLEAR_TEXT_OFFSET_Y, clearColor, false);
    }

    private boolean handleHistoryClick(double mouseX, double mouseY) {
        ResponsiveLayout.Rect historyBounds = getHistoryPanelBounds();
        int panelWidth = historyBounds.width();
        int panelHeight = historyBounds.height();
        int x = historyBounds.x();
        int y = historyBounds.y();

        // Click outside closes panel
        if (mouseX < x || mouseX > x + panelWidth || mouseY < y || mouseY > y + panelHeight) {
            toggleOverlay(OverlayType.NONE);
            return true;
        }

        // Clear button
        int clearW = HISTORY_PANEL_CLEAR_WIDTH;
        int clearH = HISTORY_PANEL_CLEAR_HEIGHT;
        int footerY = y + panelHeight - HISTORY_PANEL_FOOTER_OFFSET_Y;
        int clearX = x + panelWidth - clearW - HISTORY_PANEL_CLEAR_OFFSET_X;
        int clearY = footerY - HISTORY_PANEL_CLEAR_OFFSET_Y;
        if (mouseX >= clearX && mouseX <= clearX + clearW && mouseY >= clearY && mouseY <= clearY + clearH) {
            if (activeModule != null) {
                activeModule.clearHistory();
                historyScrollOffset = 0;
            }
            return true;
        }

        return false;
    }

    private boolean isPointInHistoryPanel(double mouseX, double mouseY) {
        return getHistoryPanelBounds().contains(mouseX, mouseY);
    }

    private ResponsiveLayout.Rect getHistoryPanelBounds() {
        int x = layout.getEditorX() + layout.getEditorWidth() - HISTORY_PANEL_WIDTH - HISTORY_PANEL_MARGIN_RIGHT;
        int y = layout.getEditorY() + UIConstants.Size.HEADER_HEIGHT + HISTORY_PANEL_MARGIN_TOP;
        return new ResponsiveLayout.Rect(x, y, HISTORY_PANEL_WIDTH, HISTORY_PANEL_HEIGHT);
    }

    private void renderTooltip(GuiGraphics graphics, String text, int x, int y) {
        var safeFont = Objects.requireNonNull(font, "font cannot be null");
        String safeText = Objects.requireNonNull(text, "text cannot be null");
        int tipWidth = safeFont.width(safeText) + TOOLTIP_WIDTH_PADDING;
        int tipX = Math.min(x + TOOLTIP_OFFSET_X, width - tipWidth - TOOLTIP_SCREEN_MARGIN);
        int tipY = Math.max(y - TOOLTIP_OFFSET_Y, TOOLTIP_SCREEN_MARGIN);

        graphics.fill(tipX, tipY, tipX + tipWidth, tipY + TOOLTIP_HEIGHT, TOOLTIP_BG);
        AxiomRenderer.drawBorder(graphics, tipX, tipY, tipWidth, TOOLTIP_HEIGHT, UIConstants.Border.DEFAULT());
        graphics.drawString(safeFont, safeText, tipX + TOOLTIP_TEXT_OFFSET_X, tipY + TOOLTIP_TEXT_OFFSET_Y,
            UIConstants.Text.PRIMARY(), false);

        tooltipText = null;
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
        if (debugPanel != null) {
            // Prefer preview item when preview mode is active so debug reflects the preview state
            ItemStack displayItem = item;
            if (isPreviewMode && activeModule != null) {
                try {
                    var preview = activeModule.getPreviewItem();
                    if (preview != null) displayItem = preview;
                } catch (Exception ignored) {}
            }
            debugPanel.setStatSources(buildStatSources(displayItem));
            debugPanel.render(graphics, font, devArea.x(), devArea.y(), devArea.width(), devArea.height(), mouseX, mouseY, displayItem);
            return;
        }

        var safeFont = Objects.requireNonNull(font, "font cannot be null");
        graphics.fill(devArea.x(), devArea.y(), devArea.right(), devArea.bottom(), DEV_PANEL_BG);
        AxiomRenderer.drawBorder(graphics, devArea.x(), devArea.y(), devArea.width(), devArea.height(), UIConstants.Border.ACCENT());

        int textY = devArea.y() + DEV_PANEL_TEXT_OFFSET_Y;
        graphics.drawString(safeFont, DEV_PANEL_TITLE_TEXT, devArea.x() + DEV_PANEL_TEXT_OFFSET_X, textY,
            DEV_PANEL_TITLE_COLOR, false);
        textY += DEV_PANEL_TITLE_LINE_STEP;

        EditorCache.CacheStats stats = EditorCache.INSTANCE.getStats();
        graphics.drawString(safeFont, "Cache: " + stats.valid() + "/" + stats.total(), devArea.x() + DEV_PANEL_TEXT_OFFSET_X,
            textY, UIConstants.Text.SECONDARY(), false);
        textY += DEV_PANEL_LINE_STEP;
        graphics.drawString(safeFont, String.format("Hit: %.1f%%", stats.hitRate() * 100), devArea.x() + DEV_PANEL_TEXT_OFFSET_X,
            textY, UIConstants.Text.SECONDARY(), false);
        textY += DEV_PANEL_LINE_STEP;

        graphics.drawString(safeFont, "Size: " + layout.getScreenSize(), devArea.x() + DEV_PANEL_TEXT_OFFSET_X,
            textY, UIConstants.Text.SECONDARY(), false);
        textY += DEV_PANEL_LINE_STEP;
        graphics.drawString(safeFont, "Scroll: " + (int) scrollArea.getScrollOffset(), devArea.x() + DEV_PANEL_TEXT_OFFSET_X,
            textY, UIConstants.Text.SECONDARY(), false);
    }

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle help overlay first
        if (helpOverlay.isVisible()) {
            return helpOverlay.mouseClicked(mouseX, mouseY, width, height);
        }

        if (showLowConfidenceDialog && pendingDetection != null) {
            return handleLowConfidenceClick(mouseX, mouseY, button);
        }

        // Handle modal dialog
        if (activeDialog != null && activeDialog.isVisible()) {
            return activeDialog.mouseClicked(mouseX, mouseY, width, height);
        }

        // Handle module overlay (e.g., ItemPickerOverlay from RecipeModule)
        if (activeModule != null && activeModule.hasActiveOverlay()) {
            return activeModule.overlayMouseClicked(mouseX, mouseY, button, width, height);
        }

        if (showCraftingPanel && craftingPanel.isVisible()) {
            if (craftingPanel.mouseClicked(mouseX, mouseY, width, height)) {
                if (!craftingPanel.isVisible()) {
                    toggleOverlay(OverlayType.NONE);
                }
                return true;
            }
        }

        if (showTemplatesPanel && templateOverlay != null) {
            if (templateOverlay.mouseClicked(mouseX, mouseY, width, height)) {
                return true;
            }
        }

        if (showDevPanel && debugPanel != null && debugPanel.handleClick(mouseX, mouseY)) {
            showStatus("Copied debug log", UIConstants.Accent.BLUE());
            return true;
        }

        if (showHistoryPanel && handleHistoryClick(mouseX, mouseY)) {
            return true;
        }
        if (showPresetsPanel && presetSelectorOverlay != null) {
            if (presetSelectorOverlay.mouseClicked(mouseX, mouseY, width, height)) {
                return true;
            }
        }
        if (handleFavoritesClick(mouseX, mouseY)) {
            return true;
        }

        // Reset tooltip
        tooltipText = null;

        if (header.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // Allow interaction with the real vanilla hotbar while this screen is open
        if (handleHotbarClick(mouseX, mouseY, button)) {
            return true;
        }
        if (footer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (leftColumn.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (scrollArea.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (activeModule != null) {
            // Pass raw mouseY (screen space) - module components store bounds in screen space
            // The scrollArea handles clipping, we just need hit detection on visible elements
            return activeModule.mouseClicked(mouseX, mouseY, button);
        }

        // If not handled by other components, give multi-edit panel a chance
        if (showMultiEditPanel && multiEditPanel != null && multiEditPanel.mouseClicked(mouseX, mouseY, button)) {
            // If the panel performed a batch operation, surface the result
            var result = multiEditPanel.takeLastResult();
            if (result != null) {
                int succ = result.successCount();
                int fail = result.failureCount();
                if (fail == 0) {
                    showStatus("Applied preset to " + succ + " items", UIConstants.Accent.GREEN());
                    if (debugPanel != null) debugPanel.log("MultiEdit apply: " + succ + " successes");
                } else {
                    // Show brief summary and log detailed failures
                    showStatus("Applied: " + succ + " successes, " + fail + " failures", UIConstants.Accent.ORANGE());
                    try {
                        var failures = result.failures();
                        // Build short list (up to 5) for immediate feedback
                        StringBuilder sb = new StringBuilder();
                        int limit = Math.min(5, failures.size());
                        for (int i = 0; i < limit; i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(failures.get(i));
                        }
                        if (failures.size() > limit) sb.append(" (+" + (failures.size() - limit) + " more)");
                        DevMod.LOGGER.warn("MultiEdit apply failures: {}", failures);
                        showStatus("Failures: " + sb.toString(), UIConstants.Accent.RED());
                        if (debugPanel != null) {
                            String first = failures.isEmpty() ? "" : failures.get(0);
                            debugPanel.log("MultiEdit apply: " + succ + " successes, " + fail + " failures - first: " + first);
                        }
                    } catch (Exception ignore) {
                        // ignore logging failure
                    }
                }
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (showLowConfidenceDialog) return false;
        if (handleHotbarClick(mouseX, mouseY, button, true)) {
            return true;
        }
        if (scrollArea.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (activeModule != null) {
            // Pass raw mouseY (screen space) - consistent with mouseClicked
            return activeModule.mouseReleased(mouseX, mouseY, button);
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Minimal hotbar interaction while the editor screen is open.
     * Left-click to pick up / place items using the carried stack semantics.
     */
    private boolean handleHotbarClick(double mouseX, double mouseY, int button) {
        return handleHotbarClick(mouseX, mouseY, button, false);
    }

    /**
     * @param placeOnly when true, only process if there's a carried stack (for drag-release scenarios)
     */
    private boolean handleHotbarClick(double mouseX, double mouseY, int button, boolean placeOnly) {
        if (button != 0) return false;
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
        if (showLowConfidenceDialog) return false;
        if (scrollArea.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (activeModule != null) {
            // Pass raw mouseY (screen space) - consistent with mouseClicked
            return activeModule.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showLowConfidenceDialog && pendingDetection != null) {
            return true;
        }
        // Handle module overlay scroll (e.g., ItemPickerOverlay from RecipeModule)
        if (activeModule != null && activeModule.hasActiveOverlay()) {
            return activeModule.overlayMouseScrolled(mouseX, mouseY, scrollY, width, height);
        }
        if (showHistoryPanel) {
            if (isPointInHistoryPanel(mouseX, mouseY)) {
                historyScrollOffset -= (int) (scrollY * HISTORY_PANEL_LINE_HEIGHT);
                return true;
            }
        }
        if (showTemplatesPanel && templateOverlay != null) {
            if (templateOverlay.mouseScrolled(mouseX, mouseY, scrollY, width, height)) {
                return true;
            }
        }
        if (showCraftingPanel && craftingPanel.isVisible()) {
            if (craftingPanel.mouseScrolled(mouseX, mouseY, scrollY, width, height)) {
                return true;
            }
        }
        if (showPresetsPanel && presetSelectorOverlay != null) {
            if (presetSelectorOverlay.mouseScrolled(mouseX, mouseY, scrollY, width, height)) {
                return true;
            }
        }
        if (header.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (footer.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (showMultiEditPanel && multiEditPanel != null && multiEditPanel.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (scrollArea.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (activeModule != null) {
            return activeModule.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle debug overlay shortcuts first
        if (DebugOverlay.handleKeyPressed(keyCode, modifiers)) {
            return true;
        }

        // Handle help overlay first
        if (helpOverlay.isVisible()) {
            return helpOverlay.keyPressed(keyCode);
        }

        // Handle module overlay keys (e.g., ItemPickerOverlay from RecipeModule)
        if (activeModule != null && activeModule.hasActiveOverlay()) {
            return activeModule.overlayKeyPressed(keyCode);
        }

        if (showTemplatesPanel && templateOverlay != null) {
            if (templateOverlay.keyPressed(keyCode, modifiers)) {
                return true;
            }
        }

        // Quick shortcut: refresh MultiEdit selection and expand panel (M)
        if (keyCode == GLFW.GLFW_KEY_M) {
            refreshMultiEditSelection();
            if (multiEditPanel != null) {
                multiEditPanel.setExpanded(true);
            }
            showStatus("MultiEdit refreshed", UIConstants.Accent.BLUE());
            return true;
        }

        if (showHistoryPanel && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeOverlay();
            return true;
        }
        if (showPresetsPanel && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeOverlay();
            return true;
        }
        if (showTemplatesPanel && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeOverlay();
            return true;
        }

        // Handle modal dialog
        if (activeDialog != null && activeDialog.isVisible()) {
            return activeDialog.keyPressed(keyCode);
        }

        // Low-confidence dialog blocks input and supports Esc/Enter
        if (showLowConfidenceDialog && pendingDetection != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                showLowConfidenceDialog = false;
                pendingDetection = null;
                lowConfidenceStatus = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                showLowConfidenceDialog = false;
                pendingDetection = null;
                lowConfidenceStatus = null;
                return true;
            }
            return true; // swallow other keys while modal is up
        }

        if (showPresetsPanel && presetSelectorOverlay != null) {
            if (presetSelectorOverlay.keyPressed(keyCode)) {
                return true;
            }
        }

        // Escape to close
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            handleCloseRequest();
            return true;
        }

        // F5 toggle preview/apply
        if (keyCode == GLFW.GLFW_KEY_F5) {
            handleModeChange(isPreviewMode ? ModeBadge.Mode.APPLY : ModeBadge.Mode.PREVIEW);
            return true;
        }

        // Ctrl+Enter quick apply (only APPLY + dirty)
        if (keyCode == GLFW.GLFW_KEY_ENTER && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (isPreviewMode) {
                showStatus("Preview mode: cannot apply", UIConstants.Accent.ORANGE());
            } else if (activeModule != null && activeModule.hasUnsavedChanges()) {
                applyChanges();
            } else {
                showStatus("No changes to apply", UIConstants.Accent.ORANGE());
            }
            return true;
        }

        // Ctrl+Z batch undo (when MultiEdit snapshot is available)
        if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (multiEditManager != null && multiEditManager.hasSnapshot()) {
                var result = multiEditManager.restoreSnapshot();
                if (result.failureCount() == 0) {
                    showStatus("Batch undo: " + result.successCount() + " items restored", UIConstants.Accent.GREEN());
                } else {
                    showStatus("Batch undo: " + result.successCount() + " ok, " + result.failureCount() + " failed", UIConstants.Accent.ORANGE());
                }
                return true;
            }
            // If no batch snapshot, let module handle undo
        }

        // Component shortcuts
        if (header.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (footer.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (leftColumn.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (scrollArea.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        // Track F3 for dev toggle combo
        if (keyCode == GLFW.GLFW_KEY_F3) {
            f3Held = true;
        }

        // F3 + D for dev panel toggle (matching design spec)
        if (f3Held && keyCode == GLFW.GLFW_KEY_D) {
            showDevPanel = !showDevPanel;
            return true;
        }

        // Debug panel shortcuts when visible
        if (showDevPanel && debugPanel != null) {
            // Ctrl+E -> export recent (10) entries
            if (keyCode == GLFW.GLFW_KEY_E && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                try {
                    var p = debugPanel.exportRecentToTempFile(10);
                    debugPanel.log("Exported recent to: " + p.toString());
                    showStatus("Exported debug recent", UIConstants.Accent.BLUE());
                } catch (Exception e) {
                    debugPanel.log("Export failed: " + e.getMessage());
                    showStatus("Export failed", UIConstants.Accent.ORANGE());
                }
                return true;
            }

            // Ctrl+L -> clear debug log
            if (keyCode == GLFW.GLFW_KEY_L && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                debugPanel.clear();
                showStatus("Debug log cleared", UIConstants.Accent.BLUE());
                return true;
            }
        }

        // Toggle multi-edit panel (M)
        if (keyCode == GLFW.GLFW_KEY_M) {
            showMultiEditPanel = !showMultiEditPanel;
            if (showMultiEditPanel) {
                refreshMultiEditSelection();
                if (multiEditPanel != null) {
                    multiEditPanel.setExpanded(true);
                }
            }
            return true;
        }

        // F1 for help
        if (keyCode == GLFW.GLFW_KEY_F1) {
            helpOverlay.toggle();
            return true;
        }

        if (showCraftingPanel && craftingPanel.isVisible() && craftingPanel.keyPressed(keyCode)) {
            toggleOverlay(OverlayType.NONE);
            return true;
        }

        // Pass to module
        if (activeModule != null) {
            return activeModule.keyPressed(keyCode, scanCode, modifiers);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (showLowConfidenceDialog && pendingDetection != null) {
            return true;
        }
        // Handle module overlay char input (e.g., ItemPickerOverlay search)
        if (activeModule != null && activeModule.hasActiveOverlay()) {
            return activeModule.overlayCharTyped(chr, modifiers);
        }
        if (showTemplatesPanel && templateOverlay != null) {
            return templateOverlay.charTyped(chr, modifiers);
        }
        if (showPresetsPanel && presetSelectorOverlay != null) {
            return presetSelectorOverlay.charTyped(chr, modifiers);
        }
        if (activeModule != null) {
            return activeModule.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F3) {
            f3Held = false;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    // ═══════════════════════════════════════════════════════════════
    // ACTIONS
    // ═══════════════════════════════════════════════════════════════

    private void applyChanges() {
        if (activeModule == null) return;

        if (!activeModule.hasUnsavedChanges()) {
            showStatus("No changes to apply", UIConstants.Accent.ORANGE());
            activeModule.logEvent("Apply skipped (no changes)");
            return;
        }

        try {
            if (activeModule != null) {
                activeModule.logEvent("Apply requested (" + (isGlobalMode ? "GLOBAL" : "SPECIFIC") + ")");
            }
            // Build and send payload
            CustomPacketPayload payload;
            if (activeModule instanceof com.frenkvs.devmod.ui.editor.modules.ArmorModule armorModule) {
                ArmorStats stats = armorModule.getStats().copy();
                CompoundTag statsTag = new CompoundTag();
                CompoundTag armorStats = new CompoundTag();
                stats.save(armorStats);
                statsTag.put(ARMOR_STATS_KEY, Objects.requireNonNull(armorStats.copy()));
                statsTag.put(ARMOR_STATS_COMPONENT_KEY, Objects.requireNonNull(armorStats));

                int slotIndex = -1;
                if (!isGlobalMode && selectedSlot != null && selectedSlot.slot() != null) {
                    slotIndex = switch (selectedSlot.slot()) {
                        case HEAD -> 0;
                        case CHEST -> 1;
                        case LEGS -> 2;
                        case FEET -> 3;
                        default -> -1;
                    };
                }
                ItemStack armorItem = Objects.requireNonNull(armorModule.getItem(), "armor item");
                payload = new ArmorStatsPayloadV2(Objects.requireNonNull(armorItem.copy()), statsTag, isGlobalMode, slotIndex);
            } else {
                payload = activeModule.buildPayload(isGlobalMode);
            }
            if (payload == null) {
                showStatus("Apply not available for this module (no payload)", UIConstants.Accent.ORANGE());
                activeModule.logEvent("Apply skipped: no payload");
                return;
            }
            PacketDistributor.sendToServer(payload);
            activeModule.logEvent("Waiting for server confirm...");

            // Apply preview locally to reflect new state
            activeModule.applyPreview();

            // Clear dirty state
            activeModule.clearDirty();
            lastSaveTimestamp = System.currentTimeMillis();

            // Invalidate cache
            EditorCache.INSTANCE.invalidateAll();

            playSound(SoundEvents.UI_BUTTON_CLICK.value());
            showStatus("Changes applied!", UIConstants.Accent.GREEN());
            activeModule.logEvent("Apply sent to server");

        } catch (Exception e) {
            showStatus("Failed to apply: " + e.getMessage(), UIConstants.Accent.RED());
            if (activeModule != null) {
                activeModule.logEvent("Apply error: " + e.getMessage());
            }
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
        if (activeModule != null) {
            activeModule.logEvent(full);
        }
        if (debugPanel != null) {
            debugPanel.log(full);
        }
        // Persist ACK/FAIL to session history file for tracking
        String action = payload.success() ? "apply_ack" : "apply_fail";
        String itemId = payload.itemId() == null ? item.getHoverName().getString() : payload.itemId();
        String details = (payload.global() ? "GLOBAL" : "SPECIFIC") + (detail.isBlank() ? "" : " - " + detail);
        ItemEditorDataManager.INSTANCE.addHistoryEntry(action, itemId, details);

        String statusMsg = detail.isBlank() ? (payload.success() ? "Server confirmed" : "Server rejected") : detail;
        showStatus(statusMsg, payload.success() ? UIConstants.Accent.GREEN() : UIConstants.Accent.RED());
    }

    private void handleCloseRequest() {
        if (!isPreviewMode && activeModule != null && activeModule.hasUnsavedChanges()) {
            activeDialog = ConfirmDialog.unsavedChanges(
                activeModule.getPendingChanges().size(),
                this::onClose,
                () -> {}
            );
            activeDialog.show();
            return;
        }
        onClose();
    }

    // ═══════════════════════════════════════════════════════════════
    // DATA OPS: EXPORT / IMPORT / PRESETS
    // ═══════════════════════════════════════════════════════════════

    private void handleExport() {
        if (!supportsDataOps()) {
            showStatus("Export available only for weapons/armors", UIConstants.Accent.ORANGE());
            return;
        }

        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        ItemEditorDataManager.ItemConfigExport config = new ItemEditorDataManager.ItemConfigExport();
        config.itemId = getCurrentItemId();
        config.itemName = item.getHoverName().getString();
        config.stats = collectStatsForExport();

        // Export enchantments from item
        config.enchantments = collectEnchantmentsForExport();
        // Export attributes from item
        config.attributes = collectAttributesForExport();
        // Export durability info
        config.durability = item.isDamageableItem() ? item.getMaxDamage() - item.getDamageValue() : null;
        final var unbreakableType = Objects.requireNonNull(DataComponents.UNBREAKABLE, "unbreakable component");
        config.unbreakable = item.has(unbreakableType);
        final var repairCostType = Objects.requireNonNull(DataComponents.REPAIR_COST, "repair cost component");
        config.repairCost = item.has(repairCostType) ? item.get(repairCostType) : null;

        String fileName = buildSafeFileName(config.itemId + "_export_" + System.currentTimeMillis());
        boolean ok = data.exportToFile(config, fileName);
        if (ok) {
            data.addHistoryEntry("export", config.itemName, fileName);
            showStatus("Exported to " + fileName + ".json", UIConstants.Accent.GREEN());
            // Also emit datapack with current overrides for sharing (partial spec 06-persistence)
            int exported = DatapackIO.exportOverrides(DEFAULT_DATAPACK_NAME);
            if (exported > 0) {
                showStatus("Datapack: " + DEFAULT_DATAPACK_NAME + " (" + exported + " items)", UIConstants.Accent.BLUE());
            }
        } else {
            showStatus("Export failed", UIConstants.Accent.RED());
        }
    }

    private List<ItemEditorDataManager.EnchantData> collectEnchantmentsForExport() {
        List<ItemEditorDataManager.EnchantData> result = new ArrayList<>();
        final var enchantType = Objects.requireNonNull(DataComponents.ENCHANTMENTS, "enchantments component");
        ItemEnchantments enchants = item.has(enchantType)
            ? Objects.requireNonNull(item.get(enchantType), "enchantments value")
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

    private List<ItemEditorDataManager.AttrData> collectAttributesForExport() {
        List<ItemEditorDataManager.AttrData> result = new ArrayList<>();
        final var attrType = Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS, "attribute modifiers component");
        ItemAttributeModifiers modifiers = item.has(attrType)
            ? Objects.requireNonNull(item.get(attrType), "attribute modifiers")
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
            showStatus("Import available only for weapons/armors", UIConstants.Accent.ORANGE());
            return;
        }

        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        List<String> exports = data.listExportFiles();
        if (exports.isEmpty()) {
            showStatus("No exports found", UIConstants.Accent.ORANGE());
            return;
        }

        // Use most recent export (last in sorted list)
        String fileName = exports.get(exports.size() - 1);
        try {
            ItemEditorDataManager.ItemConfigExport imported = data.importFromFile(fileName);
            applyImportedStats(imported, "Imported " + fileName);
            data.addHistoryEntry("import", item.getHoverName().getString(), fileName);
            showStatus("Imported " + fileName, UIConstants.Accent.BLUE());
            int applied = DatapackIO.importOverrides(DEFAULT_DATAPACK_NAME);
            if (applied > 0) {
                showStatus("Imported datapack overrides (" + applied + ")", UIConstants.Accent.BLUE());
            }
        } catch (Exception e) {
            showStatus("Import failed: " + e.getMessage(), UIConstants.Accent.RED());
        }
    }

    private void openTemplatesOverlay() {
        ItemEditorDataManager data = ItemEditorDataManager.INSTANCE;
        List<ItemEditorDataManager.TemplateData> list = new ArrayList<>(data.getTemplates().values());
        String category = detectTemplateCategory();
        templateOverlay.setTemplates(list, category);
        toggleOverlay(OverlayType.TEMPLATES);
    }

    private boolean supportsDataOps() {
        return activeModule instanceof WeaponModule || activeModule instanceof ArmorModule;
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
        var key = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item.getItem(), "item cannot be null"));
        return key == null ? "unknown" : key.toString();
    }

    private String detectTemplateCategory() {
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
            stats.add(s.headMult);
            stats.add(s.bodyMult);
            stats.add(s.armsMult);
            stats.add(s.legsMult);
            stats.add(s.attackDamage);
            stats.add(s.attackSpeed);
            stats.add(s.attackReach);
            stats.add(s.attackKnockback);
            stats.add(s.armorPenetration);
            stats.add(s.baseDamageBonus);
            stats.add(s.critChance);
            stats.add(s.critDamage);
            stats.add(s.lifesteal);
            stats.add(s.fireDamageBonus);
            stats.add(s.magicDamageBonus);
            stats.add(s.damageBonus);
            stats.add(s.armorShred);
            stats.add(s.damageVsUndead);
            stats.add(s.damageVsArthropods);
            stats.add(s.damageVsPlayers);
            stats.add(s.trueDamagePercent);
        } else if (activeModule instanceof ArmorModule armorModule) {
            var s = armorModule.getStats();
            stats.add(s.physicalReduction);
            stats.add(s.fireReduction);
            stats.add(s.magicReduction);
            stats.add(s.explosionReduction);
            stats.add(s.projectileReduction);
            stats.add(s.armorBonus);
            stats.add(s.toughnessBonus);
            stats.add(s.knockbackResistance);
            stats.add(s.thornsPercent);
            stats.add(s.thornsReflect ? 1f : 0f);
        }
        return stats;
    }

    private ItemEditorDataManager.PresetData buildPresetFromCurrent(String name, String itemType) {
        ItemEditorDataManager.PresetData preset = new ItemEditorDataManager.PresetData(name);
        preset.itemType = itemType;
        preset.scope = isGlobalMode ? "GLOBAL" : "SPECIFIC";
        preset.devmodVersion = getDevModVersion();
        preset.statValues = collectStatsForExport();
        return preset;
    }

    private void applyImportedStats(ItemEditorDataManager.ItemConfigExport config, String reason) {
        if (config == null || config.stats == null) {
            showStatus("Import file missing stats", UIConstants.Accent.RED());
            return;
        }

        if (activeModule instanceof WeaponModule weaponModule) {
            WeaponStats newStats = weaponModule.getStats().copy();
            List<Float> values = config.stats;
            newStats.headMult = getStat(values, 0, newStats.headMult);
            newStats.bodyMult = getStat(values, 1, newStats.bodyMult);
            newStats.armsMult = getStat(values, 2, newStats.armsMult);
            newStats.legsMult = getStat(values, 3, newStats.legsMult);
            newStats.attackDamage = getStat(values, 4, newStats.attackDamage);
            newStats.attackSpeed = getStat(values, 5, newStats.attackSpeed);
            newStats.attackReach = getStat(values, 6, newStats.attackReach);
            newStats.attackKnockback = getStat(values, 7, newStats.attackKnockback);
            newStats.armorPenetration = getStat(values, 8, newStats.armorPenetration);
            newStats.baseDamageBonus = getStat(values, 9, newStats.baseDamageBonus);
            newStats.critChance = getStat(values, 10, newStats.critChance);
            newStats.critDamage = getStat(values, 11, newStats.critDamage);
            newStats.lifesteal = getStat(values, 12, newStats.lifesteal);
            newStats.fireDamageBonus = getStat(values, 13, newStats.fireDamageBonus);
            newStats.magicDamageBonus = getStat(values, 14, newStats.magicDamageBonus);
            newStats.damageBonus = getStat(values, 15, newStats.damageBonus);
            newStats.armorShred = getStat(values, 16, newStats.armorShred);
            newStats.damageVsUndead = getStat(values, 17, newStats.damageVsUndead);
            newStats.damageVsArthropods = getStat(values, 18, newStats.damageVsArthropods);
            newStats.damageVsPlayers = getStat(values, 19, newStats.damageVsPlayers);
            newStats.trueDamagePercent = getStat(values, 20, newStats.trueDamagePercent);
            weaponModule.applyExternalStats(newStats, reason);
            weaponModule.applyPreview();
        } else if (activeModule instanceof ArmorModule armorModule) {
            ArmorStats newStats = armorModule.getStats().copy();
            List<Float> values = config.stats;
            newStats.physicalReduction = getStat(values, 0, newStats.physicalReduction);
            newStats.fireReduction = getStat(values, 1, newStats.fireReduction);
            newStats.magicReduction = getStat(values, 2, newStats.magicReduction);
            newStats.explosionReduction = getStat(values, 3, newStats.explosionReduction);
            newStats.projectileReduction = getStat(values, 4, newStats.projectileReduction);
            newStats.armorBonus = getStat(values, 5, newStats.armorBonus);
            newStats.toughnessBonus = getStat(values, 6, newStats.toughnessBonus);
            newStats.knockbackResistance = getStat(values, 7, newStats.knockbackResistance);
            newStats.thornsPercent = getStat(values, 8, newStats.thornsPercent);
            newStats.thornsReflect = getStat(values, 9, newStats.thornsReflect ? 1f : 0f) > 0.5f;
            armorModule.applyExternalStats(newStats, reason);
            armorModule.applyPreview();
        } else {
            showStatus("Unsupported editor for import", UIConstants.Accent.RED());
        }
    }

    private void handleTemplateApply(ItemEditorDataManager.TemplateData template) {
        if (template == null) return;
        if (!isPreviewMode && activeModule != null && activeModule.hasUnsavedChanges()) {
            activeDialog = ConfirmDialog.unsavedChanges(
                activeModule.getPendingChanges().size(),
                () -> {
                    applyTemplateInternal(template);
                    closeOverlay();
                },
                () -> {});
            activeDialog.show();
            return;
        }
        applyTemplateInternal(template);
        closeOverlay();
    }

    private void applyTemplateInternal(ItemEditorDataManager.TemplateData template) {
        ItemStack target = isPreviewMode ? item.copy() : item;

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
                    holder = PufferfishCompat.map(id, BuiltInRegistries.ATTRIBUTE);
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

        if (activeModule != null) {
            activeModule.setItem(target);
            activeModule.applyPreview();
            if (!isPreviewMode) {
                activeModule.markDirty("Applied template: " + template.name);
            }
        }

        String name = template.name == null ? "template" : template.name;
        ItemEditorDataManager.INSTANCE.addHistoryEntry("template_apply", item.getHoverName().getString(), name);
        lastLoadedPreset = name;
        showStatus("Applied template " + name, UIConstants.Accent.GREEN());
        EditorCache.INSTANCE.invalidateItem(getCurrentItemId());
    }

    private void applyPreset(ItemEditorDataManager.PresetData preset) {
        if (preset == null) {
            showStatus("Preset not found", UIConstants.Accent.RED());
            return;
        }
        ItemEditorDataManager.ItemConfigExport wrapper = new ItemEditorDataManager.ItemConfigExport();
        wrapper.stats = preset.statValues;
        applyImportedStats(wrapper, "Preset: " + preset.name);
        lastLoadedPreset = preset.name;
    }

    private void deletePreset(ItemEditorDataManager.PresetData preset) {
        if (preset == null || preset.name == null) return;
        activeDialog = ConfirmDialog.deletePreset(preset.name,
            () -> {
                ItemEditorDataManager.INSTANCE.deletePreset(preset.name);
                ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_delete", item.getHoverName().getString(), preset.name);
                DevMod.LOGGER.info("[Editor] Preset deleted: {}", preset.name);
                showStatus("Preset deleted: " + preset.name, UIConstants.Accent.BLUE());
                if (presetSelectorOverlay != null) {
                    presetSelectorOverlay.refreshPresets();
                }
            },
            () -> {}  // onCancel - no action needed
        );
        activeDialog.show();
    }

    private void renamePreset(ItemEditorDataManager.PresetData preset, String newName) {
        if (preset == null || preset.name == null || newName == null || newName.isBlank()) return;
        String oldName = preset.name;

        // Update the preset with new name
        preset.name = newName.trim();
        ItemEditorDataManager.INSTANCE.deletePreset(oldName);
        ItemEditorDataManager.INSTANCE.savePreset(preset);
        ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_rename", item.getHoverName().getString(), oldName + " -> " + newName);
        DevMod.LOGGER.info("[Editor] Preset renamed: {} -> {}", oldName, newName);
        showStatus("Preset renamed: " + newName, UIConstants.Accent.BLUE());

        // Update lastLoadedPreset if it was the renamed one
        if (oldName.equals(lastLoadedPreset)) {
            lastLoadedPreset = newName;
        }

        if (presetSelectorOverlay != null) {
            presetSelectorOverlay.refreshPresets();
        }
    }

    private void saveCurrentAsPreset() {
        String itemType = getActiveItemType();
        String presetName = buildPresetName(itemType);
        ItemEditorDataManager.PresetData preset = buildPresetFromCurrent(presetName, itemType);
        ItemEditorDataManager.INSTANCE.savePreset(preset);
        ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_save", item.getHoverName().getString(), presetName);
        DevMod.LOGGER.info("[Editor] Preset saved: {}", presetName);
        showStatus("Preset saved: " + presetName, UIConstants.Accent.GREEN());
        lastLoadedPreset = presetName;
        if (presetSelectorOverlay != null) {
            presetSelectorOverlay.refreshPresets();
        }
    }

    private float getStat(List<Float> values, int index, float fallback) {
        if (values == null || index < 0 || index >= values.size()) {
            return fallback;
        }
        Float value = values.get(index);
        return value == null ? fallback : value;
    }

    private boolean handleFavoritesClick(double mouseX, double mouseY) {
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
                Runnable loadAction = () -> {
                    applyPreset(preset);
                    ItemEditorDataManager.INSTANCE.addHistoryEntry("favorite_load", item.getHoverName().getString(), preset.name);
                    showStatus("Favorite applied: " + preset.name, UIConstants.Accent.BLUE());
                };
                if (activeModule != null && activeModule.hasUnsavedChanges()) {
                    activeDialog = new ConfirmDialog(
                        "Apply favorite",
                        "Overwrite current changes with favorite '" + preset.name + "'?",
                        "Apply",
                        "Cancel",
                        UIConstants.Accent.ORANGE(),
                        loadAction,
                        () -> {}
                    );
                    activeDialog.show();
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
                showStatus(msg + lastLoadedPreset, UIConstants.Accent.BLUE());
                return true;
            }
        }

        return true; // consume clicks inside panel
    }

    private boolean persistMultiEditItem(ItemStack item, int slot) {
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
                // Use WeaponStatsPayloadV2 for proper server-side handling
                // Note: WeaponStatsPayloadV2 doesn't have slot parameter, item is matched by type
                CompoundTag fullTag = new CompoundTag();
                fullTag.put(WEAPON_STATS_KEY, Objects.requireNonNull(statsTag.copy()));
                ItemStack itemCopy = Objects.requireNonNull(item.copy());
                PacketDistributor.sendToServer(
                    new com.frenkvs.devmod.network.WeaponStatsPayloadV2(itemCopy, fullTag, isGlobalMode)
                );
            } else if (tag.contains(ARMOR_STATS_KEY) || tag.contains(ARMOR_STATS_COMPONENT_KEY)) {
                // Prefer armor_stats_component if available, fallback to ArmorModStats
                CompoundTag statsTag = tag.contains(ARMOR_STATS_COMPONENT_KEY)
                    ? Objects.requireNonNull(tag.getCompound(ARMOR_STATS_COMPONENT_KEY), ARMOR_STATS_MISSING)
                    : Objects.requireNonNull(tag.getCompound(ARMOR_STATS_KEY), ARMOR_STATS_MISSING);
                // Use ArmorStatsPayloadV2 with slot info for proper server-side handling
                CompoundTag fullTag = new CompoundTag();
                fullTag.put(ARMOR_STATS_KEY, Objects.requireNonNull(statsTag.copy()));
                fullTag.put(ARMOR_STATS_COMPONENT_KEY, Objects.requireNonNull(statsTag.copy()));
                ItemStack itemCopy = Objects.requireNonNull(item.copy());
                PacketDistributor.sendToServer(
                    new ArmorStatsPayloadV2(itemCopy, fullTag, isGlobalMode, slot)
                );
            }
            if (debugPanel != null) {
                debugPanel.log(MULTI_EDIT_PERSIST_PREFIX + slot + " (" + item.getHoverName().getString() + ")");
            }
            return true;
        } catch (Exception e) {
            if (debugPanel != null) debugPanel.log(MULTI_EDIT_PERSIST_FAILED_PREFIX + e.getMessage());
            DevMod.LOGGER.warn("[MultiEdit] Persist failed for slot {}: {}", slot, e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════

    private void showStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
        this.statusTicks = STATUS_TICKS_DURATION; // 3 seconds at 20 TPS
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

        if (activeModule != null) {
            activeModule.tick();
        }
    }

    @Override
    public void onClose() {
        if (activeModule != null) {
            activeModule.onClose();
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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

        boolean isArmor = ArmorConfigManager.isArmor(stack);
        boolean hasGlobal = isArmor
            ? ArmorConfigManager.hasGlobalConfig(stack.getItem())
            : WeaponConfigManager.hasGlobalConfig(stack.getItem());

        if (isArmor) {
            if (hasArmorSpecific) {
                sources.add(SOURCE_SPECIFIC_ARMOR);
                if (hasGlobal) {
                    sources.add(SOURCE_GLOBAL_OVERRIDE);
                }
            } else if (hasGlobal) {
                sources.add(SOURCE_GLOBAL);
            } else {
                sources.add(SOURCE_VANILLA);
            }
        } else {
            if (hasWeaponSpecific) {
                sources.add(SOURCE_SPECIFIC_WEAPON);
                if (hasGlobal) {
                    sources.add(SOURCE_GLOBAL_OVERRIDE);
                }
            } else if (hasGlobal) {
                sources.add(SOURCE_GLOBAL);
            } else {
                sources.add(SOURCE_VANILLA);
            }
        }

        return sources;
    }

    // ═══════════════════════════════════════════════════════════════
    // GETTERS
    // ═══════════════════════════════════════════════════════════════

    public ItemStack getItem() {
        return item;
    }

    public ItemStack getOriginalItem() {
        return originalItem;
    }

    public boolean isPreviewMode() {
        return isPreviewMode;
    }

    public boolean isGlobalMode() {
        return isGlobalMode;
    }

    public EditorModule getActiveModule() {
        return activeModule;
    }
}
