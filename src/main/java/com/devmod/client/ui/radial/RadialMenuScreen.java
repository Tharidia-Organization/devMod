package com.devmod.client.ui.radial;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.ActionResult;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.overlay.OnboardingOverlay;
import com.devmod.client.panels.context.ContextDetector;
import com.devmod.client.panels.context.ContextMode;
import com.devmod.client.telemetry.UiTelemetry;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.radial.animation.RadialAnimator;
import com.devmod.client.ui.radial.config.RadialMenuConstants;
import com.devmod.client.ui.radial.config.RadialMenuScaler;
import com.devmod.client.ui.radial.config.VisibilitySupplierRegistry;
import com.devmod.client.ui.radial.input.RadialSearchHandler;
import com.devmod.client.ui.radial.model.MacroCategory;
import com.devmod.client.ui.radial.render.RadialCategoryRenderer;
import com.devmod.client.ui.radial.render.RadialGeometry;
import com.devmod.client.ui.radial.render.RadialHubRenderer;
import com.devmod.client.ui.radial.render.RadialTooltipRenderer;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.devmod.util.I18n;
@OnlyIn(Dist.CLIENT)
public final class RadialMenuScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(RadialMenuScreen.class);
    private static final Set<String> FOCUSED_ACTION_IDS = Set.of(
        ActionIds.UI_TESTING_HUB_OPEN,
        ActionIds.UI_QA_TESTING_OPEN,
        ActionIds.UI_RESPONSIVENESS_TEST_OPEN,
        ActionIds.UI_ITEM_EDITOR_OPEN_AUTO,
        ActionIds.UI_MOB_CONFIG_OPEN,
        ActionIds.UI_MOB_EQUIPMENT_OPEN,
        ActionIds.UI_TELEMETRY_DASHBOARD_OPEN,
        ActionIds.UI_EDITOR_HUB_OPEN,
        ActionIds.UI_ENDURANCE_SCREEN_OPEN,
        ActionIds.UI_ENDURANCE_EDITOR_OPEN,
        ActionIds.UI_PARTY_OPEN,
        ActionIds.UI_QUEST_EDITOR_OPEN,
        ActionIds.UI_VOXELLAB_OPEN,
        ActionIds.UI_SETTINGS_OPEN,
        ActionIds.UI_RADIAL_SETTINGS_OPEN,
        ActionIds.ARENA_CREATE,
        ActionIds.ARENA_STATUS,
        ActionIds.ARENA_TEMPLATE_LIST,
        ActionIds.ARENA_TEMPLATE_STATUS,
        ActionIds.ARENA_VALIDATE,
        ActionIds.ARENA_METRICS,
        ActionIds.ARENA_QUICK_TEST_WIZARD_OPEN
    );

    // ================================================================
    // MACRO-CATEGORY SYSTEM
    // ================================================================

    // MacroCategory enum extracted to com.devmod.client.ui.radial.model.MacroCategory

    // === Macro-Category State ===
    private static MacroCategory selectedMacro = MacroCategory.ANALYZE; // Persisted across opens
    @Nullable
    private MacroCategory hoveredMacro = null;
    private final Map<MacroCategory, List<RadialCategory>> macroCategoryMap = new EnumMap<>(MacroCategory.class);
    @Nullable
    private MacroCategory transitionFromMacro = null; // Previous macro during transition for cross-fade effect
    private final boolean[] macroAvailability = new boolean[MacroCategory.count()];

    // === Configuration ===
    private final RadialMenuConfig config = RadialMenuConfig.INSTANCE;

    // === Layout Constants ===
    private int innerRadius;
    private int outerRadius;
    private int itemRadius;
    private int centerButtonRadius;
    private int favoritesRadius;
    private int macroHubRadius; // Radius of the macro-category hub in the center

    // === Menu Structure ===
    private final List<RadialCategory> rootCategories = new ArrayList<>();
    private final Deque<RadialCategory> navigationStack = new ArrayDeque<>();
    @Nullable
    private RadialCategory currentCategory = null;

    // === Selection State ===
    private int selectedCategoryIndex = RadialMenuConstants.NO_SELECTION;
    private int selectedItemIndex = RadialMenuConstants.NO_SELECTION;
    private int selectedFavoriteIndex = RadialMenuConstants.NO_SELECTION;
    private int prevSelectedCategory = RadialMenuConstants.NO_SELECTION;
    private int centerX, centerY;
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private long lastHoverSoundAt = 0L;

    // === Input Source Tracking (Keyboard vs Mouse) ===
    private boolean keyboardFocusActive = false;
    private long lastKeyboardInputAt = 0L;
    private double lastMouseInputX = Double.NaN;
    private double lastMouseInputY = Double.NaN;

    // === Animation System ===
    private final RadialAnimator animator = new RadialAnimator(
        RadialMenuConstants.CATEGORIES_PER_MACRO,
        RadialMenuConstants.MAX_ITEMS_PER_CATEGORY,
        RadialMenuConstants.MAX_FAVORITES,
        buildAnimConfig());
    private long openTime;

    // === Telemetry Tracking ===
    private boolean firstActionExecuted = false;
    private int actionsExecutedCount = 0;

    // === Search System ===
    private boolean searchMode = false;
    private final StringBuilder searchQuery = new StringBuilder();
    private List<RadialSearchHandler.SearchResult> searchResults = new ArrayList<>();
    private int selectedSearchResult = RadialMenuConstants.NO_SELECTION;

    // === Favorites System ===
    private final List<FavoriteItem> favorites = new ArrayList<>();
    private boolean favoritesDirty = false;

    // === Quick Actions ===
    private final List<PinnedItem> quickActions = new ArrayList<>();
    private boolean pinsDirty = false;

    // === Usage Statistics ===
    private final Map<String, Integer> usageStats = new HashMap<>();
    private final List<String> recentActionIds = new ArrayList<>();
    private boolean usageDirty = false;
    private boolean recentsDirty = false;

    // === Category Index ===
    private final Map<RadialCategory, MacroCategory> categoryMacroIndex = new HashMap<>();

    // === Edit Mode ===
    private boolean editMode = false;

    // === Cached Entity ===
    @Nullable
    private net.minecraft.world.entity.Entity cachedTargetEntity = null;

    // === Context Indicator ===
    private ContextMode lastContextMode = ContextDetector.INSTANCE.getCurrentMode();
    private long contextPulseStartMs = 0L;

    public RadialMenuScreen() {
        super(java.util.Objects.requireNonNull(Component.translatable("devmod.radial.title"), "title"));
        loadConfig();
        initializeCategories();
        ensureSelectedMacroVisible();
        loadUsageStats();
        loadFavorites();
        loadPins();
        openTime = System.currentTimeMillis();
        cacheTargetEntity();
        // Removed: particles, gestures, context analysis for cleaner UX
    }

    private void loadConfig() {
        config.load();
        if (config.menuProfile == null) {
            config.menuProfile = RadialMenuConfig.MenuProfile.ALL;
        }
        if (config.favoriteActionIds == null) {
            config.favoriteActionIds = new ArrayList<>();
        }
        if (config.recentActionIds == null) {
            config.recentActionIds = new ArrayList<>();
        }
        if (config.pinnedActionIds == null) {
            config.pinnedActionIds = new ArrayList<>();
        }
        if (config.usageStats == null) {
            config.usageStats = new HashMap<>();
        }
        // Layout values are now computed dynamically by RadialMenuScaler
        updateScaledLayout();
    }

    /**
     * Updates layout values from RadialMenuScaler.
     * Called on init and each render to respond to window changes.
     */
    private void updateScaledLayout() {
        RadialMenuScaler.updateWithConfig(config);
        innerRadius = RadialMenuScaler.getInnerRadius();
        outerRadius = RadialMenuScaler.getOuterRadius();
        itemRadius = RadialMenuScaler.getItemRadius();
        centerButtonRadius = RadialMenuScaler.getCenterButtonRadius();
        favoritesRadius = RadialMenuScaler.getFavoritesRadius();
        macroHubRadius = RadialMenuScaler.getMacroHubRadius();
    }

    private void updateContextIndicator() {
        ContextMode currentMode = ContextDetector.INSTANCE.getCurrentMode();
        if (currentMode != lastContextMode) {
            lastContextMode = currentMode;
            contextPulseStartMs = System.currentTimeMillis();
        }
    }

    private void markKeyboardFocus() {
        keyboardFocusActive = true;
        lastKeyboardInputAt = System.currentTimeMillis();
    }

    private void clearKeyboardFocus() {
        keyboardFocusActive = false;
    }

    private void updateKeyboardFocusTimeout() {
        if (!keyboardFocusActive) {
            return;
        }
        long elapsed = System.currentTimeMillis() - lastKeyboardInputAt;
        if (elapsed > RadialMenuConstants.FOCUS_RING_TIMEOUT_MS) {
            keyboardFocusActive = false;
        }
    }

    private void updateMouseInput(double mouseX, double mouseY) {
        if (Double.isNaN(lastMouseInputX)) {
            lastMouseInputX = mouseX;
            lastMouseInputY = mouseY;
            return;
        }
        double dx = mouseX - lastMouseInputX;
        double dy = mouseY - lastMouseInputY;
        if (Math.hypot(dx, dy) >= RadialMenuConstants.FOCUS_RING_MOUSE_MOVE_THRESHOLD) {
            keyboardFocusActive = false;
        }
        lastMouseInputX = mouseX;
        lastMouseInputY = mouseY;
    }

    private void cacheTargetEntity() {
        Minecraft mc = Minecraft.getInstance();
        var hitResult = mc.hitResult;
        if (hitResult != null && hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            cachedTargetEntity = ((net.minecraft.world.phys.EntityHitResult) hitResult).getEntity();
        }
    }

    private static RadialAnimator.AnimationConfig buildAnimConfig() {
        RadialMenuConfig cfg = RadialMenuConfig.INSTANCE;
        float openSpeed = cfg.openAnimationSpeed > 0 ? cfg.openAnimationSpeed : RadialMenuConstants.OPEN_ANIM_SPEED;
        float closeSpeed = cfg.closeAnimationSpeed > 0 ? cfg.closeAnimationSpeed : RadialMenuConstants.CLOSE_ANIM_SPEED;
        float hoverIn = cfg.hoverInSpeed > 0 ? cfg.hoverInSpeed : RadialMenuConstants.HOVER_ANIM_IN;
        float hoverOut = cfg.hoverOutSpeed > 0 ? cfg.hoverOutSpeed : RadialMenuConstants.HOVER_ANIM_OUT;
        float morphSpeed = cfg.morphAnimationSpeed > 0 ? cfg.morphAnimationSpeed : RadialMenuConstants.MORPH_SPEED;
        float searchSpeed = cfg.searchBoxAnimationSpeed > 0 ? cfg.searchBoxAnimationSpeed : RadialMenuConstants.SEARCH_BOX_LERP;
        return new RadialAnimator.AnimationConfig(
            openSpeed,
            closeSpeed,
            RadialMenuConstants.TRANSITION_SPEED,
            hoverIn,
            hoverOut,
            morphSpeed,
            searchSpeed,
            cfg.enableAnimations
        );
    }

    // ================================================================
    // SEARCH SYSTEM
    // ================================================================

    private void updateSearchResults() {
        searchResults = RadialSearchHandler.search(searchQuery.toString(), rootCategories, this::isItemAllowedByProfile);
        selectedSearchResult = searchResults.isEmpty() ? RadialMenuConstants.NO_SELECTION : 0;
    }

    // ================================================================
    // USAGE STATISTICS
    // ================================================================

    private void loadUsageStats() {
        usageStats.clear();
        if (config.usageStats != null) {
            usageStats.putAll(config.usageStats);
        }
        recentActionIds.clear();
        if (config.recentActionIds != null) {
            for (String id : config.recentActionIds) {
                if (id != null && !id.isBlank()) {
                    recentActionIds.add(id);
                }
            }
        }
    }

    private void recordUsage(RadialMenuItem item) {
        String actionId = item.getAction().getRegistryId();
        String key = actionId != null ? actionId : item.getName();
        usageStats.put(key, usageStats.getOrDefault(key, 0) + 1);
        usageDirty = true;

        if (actionId != null) {
            recentActionIds.remove(actionId);
            recentActionIds.add(0, actionId);
            while (recentActionIds.size() > RadialMenuConstants.MAX_RECENT_ACTIONS) {
                recentActionIds.remove(recentActionIds.size() - 1);
            }
            recentsDirty = true;
        }
    }

    // ================================================================
    // FAVORITES SYSTEM
    // ================================================================

    private void loadFavorites() {
        favorites.clear();
        if (config.favoriteActionIds == null) {
            return;
        }
        boolean removed = false;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Iterator<String> iterator = config.favoriteActionIds.iterator();
        while (iterator.hasNext()) {
            String actionId = iterator.next();
            if (actionId == null || actionId.isBlank() || !seen.add(actionId)) {
                iterator.remove();
                removed = true;
                continue;
            }
            if (ActionRegistry.getAction(actionId) == null) {
                iterator.remove();
                removed = true;
                LOGGER.warn("[RadialMenuScreen] Removing stale favorite action id: {}", actionId);
                continue;
            }
            RadialMenuItem item = RadialMenuItem.registry(actionId);
            if (item != null && item.isVisible() && isItemAllowedByProfile(item)) {
                favorites.add(new FavoriteItem(actionId, item));
            }
        }
        if (removed) {
            favoritesDirty = true;
        }
    }

    private void loadPins() {
        quickActions.clear();
        if (config.pinnedActionIds == null) {
            return;
        }
        boolean removed = false;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Iterator<String> iterator = config.pinnedActionIds.iterator();
        while (iterator.hasNext()) {
            String actionId = iterator.next();
            if (actionId == null || actionId.isBlank() || !seen.add(actionId)) {
                iterator.remove();
                removed = true;
                continue;
            }
            if (ActionRegistry.getAction(actionId) == null) {
                iterator.remove();
                removed = true;
                LOGGER.warn("[RadialMenuScreen] Removing stale pinned action id: {}", actionId);
                continue;
            }
            RadialMenuItem item = RadialMenuItem.registry(actionId);
            if (item != null && item.isVisible() && isItemAllowedByProfile(item)) {
                quickActions.add(new PinnedItem(actionId, item));
            }
            if (quickActions.size() >= RadialMenuConstants.MAX_QUICK_ACTIONS) {
                break;
            }
        }
        if (removed) {
            pinsDirty = true;
        }
    }

    private void toggleFavorite(RadialMenuItem item, RadialCategory category) {
        String key = favoriteKey(item, category);

        // Check if already favorite
        for (int i = 0; i < favorites.size(); i++) {
            if (favorites.get(i).key.equals(key)) {
                favorites.remove(i);
                if (item.getAction().getRegistryId() != null) {
                    config.favoriteActionIds.remove(key);
                    favoritesDirty = true;
                }
                playSound(RadialMenuConstants.SOUND_PITCH_FAVORITE_REMOVE);
                UiTelemetry.action("radial", "menu", "favorite_removed", Map.of("actionId", key));
                return;
            }
        }

        // Add to favorites
        if (favorites.size() < RadialMenuConstants.MAX_FAVORITES) {
            favorites.add(new FavoriteItem(key, item));
            if (item.getAction().getRegistryId() != null && !config.favoriteActionIds.contains(key)) {
                config.favoriteActionIds.add(key);
                favoritesDirty = true;
            }
            playSound(RadialMenuConstants.SOUND_PITCH_FAVORITE_ADD);
            UiTelemetry.action("radial", "menu", "favorite_added", Map.of("actionId", key));
        } else {
            showMessage(Minecraft.getInstance(), I18n.translate("devmod.radial.message.favorites_full"));
        }
    }

    private void removeFavorite(int index) {
        if (index < 0 || index >= favorites.size()) {
            return;
        }
        FavoriteItem fav = favorites.remove(index);
        String actionId = fav.item.getAction().getRegistryId();
        if (actionId != null && config.favoriteActionIds != null) {
            config.favoriteActionIds.remove(actionId);
            favoritesDirty = true;
        }
        playSound(RadialMenuConstants.SOUND_PITCH_FAVORITE_REMOVE);
        UiTelemetry.action("radial", "menu", "favorite_removed",
            Map.of("actionId", actionId != null ? actionId : "unknown"));
    }

    private String favoriteKey(RadialMenuItem item, RadialCategory category) {
        String actionId = item.getAction().getRegistryId();
        if (actionId != null) {
            return actionId;
        }
        return category.getId() + ":" + item.getName();
    }

    private void togglePin(RadialMenuItem item) {
        String actionId = item.getAction().getRegistryId();
        if (actionId == null || ActionRegistry.getAction(actionId) == null) {
            showMessage(Minecraft.getInstance(), I18n.translate("devmod.radial.message.pin_invalid"));
            return;
        }

        for (int i = 0; i < quickActions.size(); i++) {
            if (quickActions.get(i).actionId.equals(actionId)) {
                quickActions.remove(i);
                if (config.pinnedActionIds != null) {
                    config.pinnedActionIds.remove(actionId);
                }
                pinsDirty = true;
                playSound(RadialMenuConstants.SOUND_PITCH_FAVORITE_REMOVE);
                return;
            }
        }

        if (quickActions.size() < RadialMenuConstants.MAX_QUICK_ACTIONS) {
            quickActions.add(new PinnedItem(actionId, item));
            if (config.pinnedActionIds != null && !config.pinnedActionIds.contains(actionId)) {
                config.pinnedActionIds.add(actionId);
            }
            pinsDirty = true;
            playSound(RadialMenuConstants.SOUND_PITCH_FAVORITE_ADD);
        } else {
            showMessage(Minecraft.getInstance(), I18n.translate("devmod.radial.message.quick_full"));
        }
    }

    private void nextCategory() {
        List<RadialCategory> categories = getActiveCategories();
        int count = categories.size();
        if (count == 0) {
            return;
        }
        if (isInSubcategory()) {
            clearSubcategory();
        }
        int nextIndex = selectedCategoryIndex >= 0 ? (selectedCategoryIndex + 1) % count : 0;
        selectedCategoryIndex = nextIndex;
        selectedItemIndex = RadialMenuConstants.NO_SELECTION;
        selectedFavoriteIndex = RadialMenuConstants.NO_SELECTION;
        animator.startMorph();
        playSound(RadialMenuConstants.SOUND_PITCH_CATEGORY_NEXT);
    }

    private void prevCategory() {
        List<RadialCategory> categories = getActiveCategories();
        int count = categories.size();
        if (count == 0) {
            return;
        }
        if (isInSubcategory()) {
            clearSubcategory();
        }
        int prevIndex = selectedCategoryIndex >= 0
            ? (selectedCategoryIndex - 1 + count) % count
            : count - 1;
        selectedCategoryIndex = prevIndex;
        selectedItemIndex = RadialMenuConstants.NO_SELECTION;
        selectedFavoriteIndex = RadialMenuConstants.NO_SELECTION;
        animator.startMorph();
        playSound(RadialMenuConstants.SOUND_PITCH_CATEGORY_PREV);
    }

    // ================================================================
    // CATEGORY INITIALIZATION - MACRO-CATEGORY ARCHITECTURE
    // ================================================================

    /**
     * Initializes the macro-categories and their sub-categories.
     * Total: 24 categories organized hierarchically for better UX.
     *
     * Categories are defined in RadialMenuRegistry for better separation of concerns.
     *
     * ANALYZE: Debug, Spatial, Performance
     * TELEMETRY: Ops, Data, Scan, Dashboard
     * COMBAT: HUD, Heatmaps, Abilities
     * ARENA: Ops, Templates, Force, Autosmoke, HUD
     * PLAY: Endurance, Quests, Party
     * TOOLS: Settings, Testing, Mob tools, Item editors, Commands
     */
    private void initializeCategories() {
        // Ensure visibility suppliers are registered for dynamic item visibility
        VisibilitySupplierRegistry.registerDefaults();

        // Initialize runtime registry if needed, then get categories
        Map<MacroCategory, List<RadialCategory>> registryCategories;
        if (RadialMenuRuntimeRegistry.isInitialized()) {
            registryCategories = RadialMenuRuntimeRegistry.getAllCategories();
        } else {
            // Fallback: initialize with mob editor supplier and get categories
            RadialMenuRuntimeRegistry.initialize(this::createMobEditorItem);
            registryCategories = RadialMenuRuntimeRegistry.getAllCategories();
        }

        // Copy to our internal map and rootCategories
        macroCategoryMap.putAll(registryCategories);

        // Populate rootCategories in correct order (all categories flat for search)
        for (MacroCategory macro : MacroCategory.values()) {
            rootCategories.addAll(macroCategoryMap.get(macro));
        }

        indexCategories();

        // Animator handles animation arrays internally with proper sizing
    }

    private void ensureSelectedMacroVisible() {
        if (macroHasVisibleCategories(selectedMacro)) {
            return;
        }
        for (MacroCategory macro : MacroCategory.values()) {
            if (macroHasVisibleCategories(macro)) {
                selectedMacro = macro;
                return;
            }
        }
    }

    private RadialMenuItem createMobEditorItem() {
        ItemStack leadStack = Objects.requireNonNull(Items.LEAD, "lead").getDefaultInstance();
        return new MobEditorItem(this, leadStack.copy());
    }

    private String getMobEditorDescription() {
        if (cachedTargetEntity instanceof net.minecraft.world.entity.Mob mob) {
            return I18n.translate("devmod.radial.mob_editor.target", mob.getName().getString()).getString();
        }
        return I18n.translate("devmod.radial.mob_editor.missing").getString();
    }

    private void indexCategories() {
        categoryMacroIndex.clear();
        for (MacroCategory macro : MacroCategory.values()) {
            List<RadialCategory> categories = macroCategoryMap.get(macro);
            if (categories == null) {
                continue;
            }
            for (RadialCategory category : categories) {
                indexCategory(macro, category, new java.util.HashSet<>());
            }
        }
    }

    private void indexCategory(MacroCategory macro, RadialCategory category, java.util.Set<RadialCategory> visited) {
        if (category == null || !visited.add(category)) {
            return;
        }
        categoryMacroIndex.put(category, macro);
        for (RadialMenuItem item : category.getItems()) {
            if (item.isSubcategoryLink()) {
                RadialCategory subcategory = item.getLinkedSubcategory();
                if (subcategory != null) {
                    indexCategory(macro, subcategory, visited);
                }
            }
        }
    }

    @Nullable
    private MacroCategory resolveMacroForCategory(@Nullable RadialCategory category) {
        if (category == null) {
            return null;
        }
        MacroCategory macro = categoryMacroIndex.get(category);
        if (macro != null) {
            return macro;
        }
        RadialCategory root = category;
        while (root.getParent() != null) {
            root = Objects.requireNonNull(root.getParent(), "parent");
            MacroCategory parentMacro = categoryMacroIndex.get(root);
            if (parentMacro != null) {
                return parentMacro;
            }
        }
        return null;
    }

    private static final class MobEditorItem extends RadialMenuItem {
        private final RadialMenuScreen screen;

        private MobEditorItem(RadialMenuScreen screen, ItemStack iconStack) {
            super("Mob Editor",
                RadialAction.registry(ActionIds.UI_MOB_CONFIG_OPEN),
                "",
                iconStack);
            this.screen = Objects.requireNonNull(screen, "screen");
        }

        @Override
        public boolean isActive() {
            return screen.cachedTargetEntity instanceof net.minecraft.world.entity.Mob;
        }

        @Override
        public String getDescription() {
            return screen.getMobEditorDescription();
        }
    }

    // ================================================================
    // SCREEN LIFECYCLE
    // ================================================================

    @Override
    protected void init() {
        // Ensure UI scale is synced with Window (fixes Windows DPI issues)
        UIScaleManager.forceRecalculate();
        if (!UIScaleManager.verifySyncOrWarn()) {
            UIScaleManager.forceRecalculate(); // Retry once if mismatch detected
        }

        // Track screen open for telemetry
        UiTelemetry.screenOpened("radial", "radial_menu");

        // Check if config was reset due to error and notify user
        if (config.wasResetDueToError()) {
            showMessage(Minecraft.getInstance(), I18n.translate("devmod.radial.message.config_reset"));
            UiTelemetry.action("radial", "menu", "config_reset_notification", Map.of());
        }

        // Initialize responsive layout
        updateScaledLayout();
        centerX = RadialMenuScaler.getCenterX();
        centerY = RadialMenuScaler.getCenterY();
        OnboardingOverlay.onRadialMenuOpened();

        // Reset telemetry tracking for this session
        openTime = System.currentTimeMillis();
        firstActionExecuted = false;
        actionsExecutedCount = 0;

        // Log menu opened event
        logMenuOpened();

        // Layout and telemetry ready
    }

    private void logMenuOpened() {
        var player = Minecraft.getInstance().player;
        String playerName = player != null ? player.getGameProfile().getName() : "unknown";

        String line = "{\"ts\":\"" + java.time.Instant.now() + "\"," +
            "\"type\":\"radial_menu_opened\"," +
            "\"screenId\":\"RadialMenuScreen\"," +
            "\"macroCategory\":\"" + selectedMacro.name() + "\"," +
            "\"player\":\"" + com.devmod.telemetry.TelemetryJson.escape(playerName) + "\"}";

        com.devmod.telemetry.TelemetryService.INSTANCE.appendActionLine(line);
    }

    private void logTimeToFirstAction(long timeMs) {
        var player = Minecraft.getInstance().player;
        String playerName = player != null ? player.getGameProfile().getName() : "unknown";

        String line = "{\"ts\":\"" + java.time.Instant.now() + "\"," +
            "\"type\":\"radial_time_to_first_action\"," +
            "\"screenId\":\"RadialMenuScreen\"," +
            "\"timeMs\":" + timeMs + "," +
            "\"player\":\"" + com.devmod.telemetry.TelemetryJson.escape(playerName) + "\"}";

        com.devmod.telemetry.TelemetryService.INSTANCE.appendActionLine(line);
    }

    private void logMenuClosed() {
        long durationMs = System.currentTimeMillis() - openTime;
        var player = Minecraft.getInstance().player;
        String playerName = player != null ? player.getGameProfile().getName() : "unknown";

        String line = "{\"ts\":\"" + java.time.Instant.now() + "\"," +
            "\"type\":\"radial_menu_closed\"," +
            "\"screenId\":\"RadialMenuScreen\"," +
            "\"actionsExecuted\":" + actionsExecutedCount + "," +
            "\"durationMs\":" + durationMs + "," +
            "\"player\":\"" + com.devmod.telemetry.TelemetryJson.escape(playerName) + "\"}";

        com.devmod.telemetry.TelemetryService.INSTANCE.appendActionLine(line);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        updateMouseInput(mouseX, mouseY);
        updateKeyboardFocusTimeout();

        // Update responsive layout (handles window resize, GUI scale changes)
        updateScaledLayout();
        centerX = RadialMenuScaler.getCenterX();
        centerY = RadialMenuScaler.getCenterY();

        updateContextIndicator();

        // Update animations
        updateAnimations(partialTick);

        if (animator.isFullyClosed()) {
            onClose();
            return;
        }

        updateSelection(mouseX, mouseY);

        // Render background
        renderBackground(graphics);

        var pose = graphics.pose();
        pose.pushPose();

        // Simple smooth scale animation (no elastic bounce, no rotation)
        float scale = animator.getOpenAnimationEased();
        pose.translate(centerX, centerY, 0);
        pose.scale(scale, scale, 1f);
        pose.translate(-centerX, -centerY, 0);

        // Subtle glow on selected category only
        if (selectedCategoryIndex >= 0) {
            renderCategoryGlow(graphics, selectedCategoryIndex);
        }

        renderCategories(graphics);
        renderFavoritesRing(graphics);
        renderCenterHub(graphics, mouseX, mouseY);

        RadialCategory itemCategory = getActiveItemCategory();
        if (itemCategory != null
            && selectedCategoryIndex >= 0
            && selectedCategoryIndex < getActiveCategories().size()) {
            renderCategoryItems(graphics, itemCategory);
        }

        if (config.showTooltips) {
            renderTooltip(graphics, mouseX, mouseY);
        }

        pose.popPose();

        // Search mode overlay
        if (searchMode) {
            renderSearchOverlay(graphics);
        }

        renderHelpText(graphics);
        renderBreadcrumb(graphics);

        if (editMode) {
            renderEditModeIndicator(graphics);
        }

        renderThemeIndicator(graphics);
    }

    // ================================================================
    // ANIMATION SYSTEM
    // ================================================================

    private void updateAnimations(float partialTick) {
        boolean animate = config.enableAnimations && !config.isReducedMotionEnabled();
        // Delegate to centralized animator
        animator.update(partialTick, animate);

        // Update search box animation
        animator.updateSearchBoxAnimation(searchMode, animate);

        // Update category/item/favorite selection animations
        List<RadialCategory> categories = getActiveCategories();
        animator.updateCategoryAnimations(selectedCategoryIndex, categories.size(), animate);

        RadialCategory itemCategory = getActiveItemCategory();
        if (itemCategory != null) {
            animator.updateItemAnimations(selectedItemIndex, getVisibleItemCount(itemCategory), animate);
        }

        animator.updateFavoriteAnimations(selectedFavoriteIndex, favorites.size(), animate);
    }

    // ================================================================
    // SELECTION
    // ================================================================

    private void updateSelection(int mouseX, int mouseY) {
        if (searchMode) {
            clearSelection();
            return;
        }

        if (keyboardFocusActive) {
            return;
        }

        prevSelectedCategory = selectedCategoryIndex;
        int prevSelectedFavorite = selectedFavoriteIndex;

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        selectedCategoryIndex = RadialMenuConstants.NO_SELECTION;
        selectedItemIndex = RadialMenuConstants.NO_SELECTION;
        selectedFavoriteIndex = RadialMenuConstants.NO_SELECTION;

        // Macro hub area (center with macro segments) - handled in renderCenterHub
        if (distance < macroHubRadius) {
            return;
        }

        double angle = Math.atan2(dy, dx);
        if (angle < 0) angle += RadialMenuConstants.TWO_PI;

        // Check favorites ring (between macro hub and inner radius)
        if (RadialMenuScaler.hasFavoritesRing()
            && !favorites.isEmpty()
            && distance >= macroHubRadius
            && distance < innerRadius) {
            int numFavorites = favorites.size();
            double favSegmentAngle = RadialMenuConstants.TWO_PI / numFavorites;
            double favStartOffset = RadialMenuConstants.CATEGORY_START_OFFSET;

            double favAdjustedAngle = angle - favStartOffset;
            if (favAdjustedAngle < 0) favAdjustedAngle += RadialMenuConstants.TWO_PI;

            selectedFavoriteIndex = (int)(favAdjustedAngle / favSegmentAngle) % numFavorites;

            double favMidAngle = favStartOffset + selectedFavoriteIndex * favSegmentAngle;
            double favX = centerX + Math.cos(favMidAngle) * favoritesRadius;
            double favY = centerY + Math.sin(favMidAngle) * favoritesRadius;
            double favDist = Math.hypot(mouseX - favX, mouseY - favY);
            // Ensure minimum 44px hit target (22px radius) for accessibility
            int favHitRadius = Math.max(
                RadialMenuScaler.scaleConstant(RadialMenuConstants.FAVORITE_HIT_RADIUS),
                RadialMenuConstants.MIN_HIT_RADIUS);
            if (favDist > favHitRadius) {
                selectedFavoriteIndex = RadialMenuConstants.NO_SELECTION;
                return;
            }

            // Play sound on favorite hover change
            if (selectedFavoriteIndex != prevSelectedFavorite && selectedFavoriteIndex >= 0) {
                playHoverSound(RadialMenuConstants.SOUND_PITCH_HOVER, RadialMenuConstants.SOUND_VOLUME_HOVER);
            }
            return;
        }

        if (!RadialMenuScaler.hasFavoritesRing() && distance < innerRadius) {
            return;
        }

        List<RadialCategory> categories = getActiveCategories();
        int numCategories = categories.size();
        if (numCategories == 0) return;

        double segmentAngle = RadialMenuConstants.TWO_PI / numCategories;
        double startOffset = RadialMenuConstants.CATEGORY_START_OFFSET - segmentAngle / 2;

        double adjustedAngle = angle - startOffset;
        if (adjustedAngle < 0) adjustedAngle += RadialMenuConstants.TWO_PI;

        int computedCategoryIndex = (int)(adjustedAngle / segmentAngle) % numCategories;
        boolean inSubcategory = isInSubcategory();
        if (inSubcategory) {
            int topLevelIndex = resolveTopLevelCategoryIndex(currentCategory);
            if (topLevelIndex == RadialMenuConstants.NO_SELECTION) {
                clearSubcategory();
                inSubcategory = false;
            } else {
                selectedCategoryIndex = topLevelIndex;
            }
        }
        if (!inSubcategory) {
            selectedCategoryIndex = computedCategoryIndex;
        }

        // Select item if mouse is beyond the outer ring
        // Use filtered visible items to only consider visible items for selection
        if (distance > outerRadius && selectedCategoryIndex >= 0 && selectedCategoryIndex < categories.size()) {
            RadialCategory itemCategory = inSubcategory ? currentCategory : categories.get(selectedCategoryIndex);
            int numVisibleItems = itemCategory != null ? getVisibleItemCount(itemCategory) : 0;
            if (numVisibleItems > 0) {
                double itemSegment = segmentAngle / numVisibleItems;
                double catStartAngle = startOffset + selectedCategoryIndex * segmentAngle;
                double relativeAngle = angle - catStartAngle;
                if (relativeAngle < 0) relativeAngle += RadialMenuConstants.TWO_PI;
                if (relativeAngle > Math.PI) relativeAngle -= RadialMenuConstants.TWO_PI;

                selectedItemIndex = Mth.clamp((int)(relativeAngle / itemSegment), 0, numVisibleItems - 1);

                // Enforce minimum hit target size: only select if mouse is within item hit radius
                if (selectedItemIndex >= 0 && selectedItemIndex < numVisibleItems) {
                    double itemAngle = catStartAngle + (selectedItemIndex + 0.5) * itemSegment;
                    int baseRadius = outerRadius + RadialMenuScaler.getItemRingOffset();
                    int hoverOffset = RadialMenuScaler.getItemHoverOffset();
                    float itemAnim = animator.getItemAnimation(selectedItemIndex);
                    int itemX = (int) Math.round(centerX + Math.cos(itemAngle) * (baseRadius + hoverOffset * itemAnim));
                    int itemY = (int) Math.round(centerY + Math.sin(itemAngle) * (baseRadius + hoverOffset * itemAnim));
                    float shakeOffset = animator.getShakeOffset(selectedItemIndex,
                        com.devmod.client.ui.radial.animation.RadialAnimator.TargetType.ITEM);
                    if (shakeOffset != 0f) {
                        itemX += (int) shakeOffset;
                    }
                    double itemDist = Math.hypot(mouseX - itemX, mouseY - itemY);
                    int hitRadius = Math.max(RadialMenuScaler.getItemSize(), RadialMenuConstants.MIN_HIT_RADIUS);
                    if (itemDist > hitRadius) {
                        selectedItemIndex = RadialMenuConstants.NO_SELECTION;
                    }
                }
            }
        }

        // Play selection sound on category change
        if (selectedCategoryIndex != prevSelectedCategory && selectedCategoryIndex >= 0) {
            playHoverSound(RadialMenuConstants.SOUND_PITCH_CATEGORY_CHANGE,
                RadialMenuConstants.SOUND_VOLUME_CATEGORY_CHANGE);
        }
    }

    /**
     * Returns the 6 categories for the currently selected macro-category.
     * This is the core of the macro-category system - outer ring shows only
     * categories belonging to the active macro.
     */
    private List<RadialCategory> getActiveCategories() {
        return getVisibleCategoriesForMacro(selectedMacro);
    }

    private List<RadialCategory> getVisibleCategoriesForMacro(@Nullable MacroCategory macro) {
        if (macro == null) {
            return new ArrayList<>();
        }
        List<RadialCategory> categories = macroCategoryMap.get(macro);
        if (categories == null || categories.isEmpty()) {
            return new ArrayList<>();
        }
        List<RadialCategory> visible = new ArrayList<>();
        for (RadialCategory category : categories) {
            if (getVisibleItemCount(category) > 0) {
                visible.add(category);
            }
        }
        return visible;
    }

    private boolean macroHasVisibleCategories(@Nullable MacroCategory macro) {
        return !getVisibleCategoriesForMacro(macro).isEmpty();
    }

    private void updateMacroAvailability() {
        MacroCategory[] macros = MacroCategory.values();
        for (int i = 0; i < macros.length; i++) {
            macroAvailability[i] = macroHasVisibleCategories(macros[i]);
        }
    }

    private int getVisibleItemCount(RadialCategory category) {
        return getVisibleItems(category).size();
    }

    private List<RadialMenuItem> getVisibleItems(RadialCategory category) {
        if (category == null) {
            return List.of();
        }
        return getVisibleItems(category, new java.util.HashSet<>());
    }

    private List<RadialMenuItem> getVisibleItems(RadialCategory category, java.util.Set<RadialCategory> visited) {
        if (category == null || !visited.add(category)) {
            return List.of();
        }
        List<RadialMenuItem> visible = new ArrayList<>();
        for (RadialMenuItem item : category.getItems()) {
            if (item.isSubcategoryLink()) {
                RadialCategory subcategory = item.getLinkedSubcategory();
                if (subcategory != null && subcategoryHasVisibleItems(subcategory)) {
                    visible.add(item);
                }
                continue;
            }
            if (!item.isVisible()) {
                continue;
            }
            if (!isItemAllowedByProfile(item)) {
                continue;
            }
            visible.add(item);
        }
        if (!config.useUsageOrdering || visible.size() < 2) {
            return visible;
        }
        List<RadialMenuItem> links = new ArrayList<>();
        List<IndexedItem> items = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            RadialMenuItem item = visible.get(i);
            if (item.isSubcategoryLink()) {
                links.add(item);
            } else {
                items.add(new IndexedItem(item, i));
            }
        }
        items.sort((a, b) -> {
            int usageCompare = Integer.compare(usageCountFor(b.item), usageCountFor(a.item));
            if (usageCompare != 0) {
                return usageCompare;
            }
            return Integer.compare(a.index, b.index);
        });
        List<RadialMenuItem> ordered = new ArrayList<>(visible.size());
        ordered.addAll(links);
        for (IndexedItem entry : items) {
            ordered.add(entry.item);
        }
        return ordered;
    }

    private boolean subcategoryHasVisibleItems(RadialCategory subcategory) {
        if (subcategory == null) {
            return false;
        }
        List<RadialMenuItem> items = getVisibleItems(subcategory, new java.util.HashSet<>());
        return !items.isEmpty();
    }

    private int usageCountFor(RadialMenuItem item) {
        if (item == null) {
            return 0;
        }
        String actionId = item.getAction().getRegistryId();
        if (actionId == null) {
            return 0;
        }
        return usageStats.getOrDefault(actionId, 0);
    }

    private boolean isItemAllowedByProfile(RadialMenuItem item) {
        if (item == null) {
            return false;
        }
        String actionId = item.getAction().getRegistryId();
        com.devmod.actions.RadialAction action = actionId != null ? ActionRegistry.getAction(actionId) : null;
        if (config.menuProfile != RadialMenuConfig.MenuProfile.ALL && action != null) {
            ActionCategory category = action.getCategory();
            String menuPath = action.getMenuPath();
            if (menuPath != null && menuPath.contains("Item Editor")) {
                return true;
            }
            if (config.menuProfile == RadialMenuConfig.MenuProfile.FOCUSED) {
                if (actionId != null && FOCUSED_ACTION_IDS.contains(actionId)) {
                    return true;
                }
                if (menuPath != null) {
                    if (menuPath.startsWith("Root/Tools/Mob Editor")
                        || menuPath.startsWith("Root/Tools/Editors")
                        || menuPath.startsWith("Root/Tools/Testing")
                        || menuPath.startsWith("Root/Tools/Commands")
                        || menuPath.startsWith("Root/Arena/Ops")
                        || menuPath.startsWith("Root/Arena/Templates")
                        || menuPath.startsWith("Root/Telemetry/Dashboard")
                        || menuPath.startsWith("Root/Debug")
                        || menuPath.startsWith("Root/Combat")
                        || menuPath.startsWith("Root/Play/Endurance")
                        || menuPath.startsWith("Root/Play/Endurance HUD")
                        || menuPath.startsWith("Root/Play/Party")
                        || menuPath.startsWith("Root/Play/Quest Flow")
                        || menuPath.startsWith("Root/Play/Quest Tools")
                        || menuPath.startsWith("Root/Play/Leaderboards")
                        || menuPath.startsWith("Root/Nexus")) {
                        return true;
                    }
                }
                return false;
            }
            if (config.menuProfile == RadialMenuConfig.MenuProfile.PLAYER) {
                if (!(category == ActionCategory.ENDURANCE
                    || category == ActionCategory.COMBAT
                    || category == ActionCategory.PARTY
                    || category == ActionCategory.UI)) {
                    return false;
                }
            } else if (config.menuProfile == RadialMenuConfig.MenuProfile.DEV) {
                if (!(category == ActionCategory.DEBUG
                    || category == ActionCategory.TELEMETRY
                    || category == ActionCategory.CONFIG
                    || category == ActionCategory.TESTING
                    || category == ActionCategory.ADMIN
                    || category == ActionCategory.TOOLS
                    || category == ActionCategory.ARENA
                    || category == ActionCategory.MISC)) {
                    return false;
                }
            }
        }
        if (config.safeMode && !RadialActionSafety.isSafe(item)) {
            return false;
        }
        return true;
    }

    private boolean isInSubcategory() {
        return currentCategory != null && currentCategory.hasParent();
    }

    @Nullable
    private RadialCategory getSelectedCategory() {
        List<RadialCategory> categories = getActiveCategories();
        if (selectedCategoryIndex < 0 || selectedCategoryIndex >= categories.size()) {
            return null;
        }
        return categories.get(selectedCategoryIndex);
    }

    @Nullable
    private RadialCategory getActiveItemCategory() {
        if (isInSubcategory()) {
            return currentCategory;
        }
        return getSelectedCategory();
    }

    @Nullable
    private RadialMenuItem getSelectedItem() {
        RadialCategory category = getActiveItemCategory();
        if (category == null) {
            return null;
        }
        List<RadialMenuItem> visibleItems = getVisibleItems(category);
        if (selectedItemIndex < 0 || selectedItemIndex >= visibleItems.size()) {
            return null;
        }
        return visibleItems.get(selectedItemIndex);
    }

    private int resolveTopLevelCategoryIndex(@Nullable RadialCategory category) {
        if (category == null) {
            return RadialMenuConstants.NO_SELECTION;
        }
        RadialCategory root = category;
        while (root.getParent() != null) {
            root = Objects.requireNonNull(root.getParent());
        }
        List<RadialCategory> categories = getActiveCategories();
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i) == root) {
                return i;
            }
        }
        return RadialMenuConstants.NO_SELECTION;
    }

    private void clearSubcategory() {
        currentCategory = null;
        navigationStack.clear();
    }

    @Nonnull
    private Font requireFont() {
        return Objects.requireNonNull(font, "font");
    }

    // ================================================================
    // RENDERING - FAVORITES RING
    // ================================================================

    private void renderFavoritesRing(GuiGraphics graphics) {
        if (favorites.isEmpty() || !RadialMenuScaler.hasFavoritesRing()) return;

        @Nonnull Font safeFont = requireFont();
        int numFavorites = favorites.size();
        double segmentAngle = RadialMenuConstants.TWO_PI / numFavorites;
        double startOffset = RadialMenuConstants.CATEGORY_START_OFFSET;
        int ringThickness = Math.max(1, RadialMenuScaler.scaleConstant(2));
        int ringInner = Math.max(0, favoritesRadius - ringThickness);
        int ringOuter = favoritesRadius + ringThickness;
        int ringColor = com.devmod.client.ui.radial.render.RadialGeometry.applyAlpha(
            RadialMenuConstants.COLOR_DIVIDER, 120);
        com.devmod.client.ui.radial.render.RadialGeometry.renderRing(graphics, centerX, centerY,
            ringInner, ringOuter, ringColor);

        for (int i = 0; i < numFavorites; i++) {
            FavoriteItem fav = favorites.get(i);
            boolean selected = (i == selectedFavoriteIndex);
            boolean canExecute = fav.item.canExecute();
            float anim = animator.getFavoriteAnimation(i);

            double midAngle = startOffset + i * segmentAngle;
            int favX = (int)(centerX + Math.cos(midAngle) * favoritesRadius);
            int favY = (int)(centerY + Math.sin(midAngle) * favoritesRadius);

            // Apply micro-shake offset for blocked feedback
            float shakeOffset = animator.getShakeOffset(i, com.devmod.client.ui.radial.animation.RadialAnimator.TargetType.FAVORITE);
            if (shakeOffset != 0f) {
                favX += (int) shakeOffset;
            }

            int baseSize = RadialMenuScaler.scaleConstant(RadialMenuConstants.FAVORITE_BASE_SIZE);
            int bonusSize = RadialMenuScaler.scaleConstant(RadialMenuConstants.FAVORITE_SIZE_BONUS);
            int size = baseSize + (int) (bonusSize * anim);

            // Star background - BLOCKED state uses distinct warning tint
            int bgColor;
            if (!canExecute) {
                bgColor = RadialMenuConstants.FAVORITE_BG_BLOCKED;
            } else if (selected) {
                bgColor = RadialMenuConstants.FAVORITE_BG_SELECTED;
            } else {
                bgColor = RadialMenuConstants.FAVORITE_BG_UNSELECTED;
            }
            RadialGeometry.renderCircle(graphics, favX, favY, size, bgColor);

            // Warning border ring for BLOCKED favorites
            if (!canExecute) {
                int borderWidth = RadialMenuScaler.scaleConstant(RadialMenuConstants.BORDER_WIDTH_DEFAULT);
                RadialGeometry.renderRing(graphics, favX, favY, size - borderWidth, size,
                    RadialMenuConstants.COLOR_BLOCKED_BORDER);
            }

            // Flash overlay for blocked/success/failed feedback
            float flashAlpha = animator.getFlashAlpha(i, com.devmod.client.ui.radial.animation.RadialAnimator.TargetType.FAVORITE);
            if (flashAlpha > 0f) {
                com.devmod.client.ui.radial.animation.RadialAnimator.FlashType flashType =
                    animator.getFlashType(i, com.devmod.client.ui.radial.animation.RadialAnimator.TargetType.FAVORITE);
                int flashColor = switch (flashType) {
                    case SUCCESS -> RadialMenuConstants.COLOR_FLASH_SUCCESS;
                    case FAILED -> RadialMenuConstants.COLOR_FLASH_FAILED;
                    case BLOCKED -> RadialMenuConstants.COLOR_FLASH_BLOCKED;
                    case NONE -> RadialMenuConstants.COLOR_FLASH_BLOCKED;
                };
                flashColor = RadialGeometry.applyAlpha(flashColor, (int) (flashAlpha * 128));
                RadialGeometry.renderCircle(graphics, favX, favY, size - 2, flashColor);
            }

            // Icon
            ItemStack iconStack = Objects.requireNonNullElse(fav.item.getIconStack(), ItemStack.EMPTY);
            if (!iconStack.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(
                    favX + RadialMenuScaler.scaleConstant(RadialMenuConstants.FAVORITE_ICON_OFFSET_X),
                    favY + RadialMenuScaler.scaleConstant(RadialMenuConstants.FAVORITE_ICON_OFFSET_Y),
                    0);
                graphics.pose().scale(RadialMenuConstants.FAVORITE_ICON_SCALE,
                    RadialMenuConstants.FAVORITE_ICON_SCALE, 1f);
                graphics.renderItem(iconStack, 0, 0);
                graphics.pose().popPose();
            } else {
                int starY = favY + RadialMenuScaler.scaleConstant(RadialMenuConstants.FAVORITE_STAR_OFFSET_Y);
                int starColor = canExecute ? RadialMenuConstants.FAVORITE_STAR_COLOR : RadialMenuConstants.COLOR_BLOCKED_ICON;
                float fontScale = RadialMenuScaler.getFontScale();
                if (fontScale <= 0f || Math.abs(fontScale - 1f) < 0.001f) {
                    UIScaleManager.drawScaledCenteredString(graphics, safeFont, "*", favX, starY, starColor);
                } else {
                    float inv = 1f / fontScale;
                    graphics.pose().pushPose();
                    graphics.pose().scale(fontScale, fontScale, 1f);
                    UIScaleManager.drawScaledCenteredString(graphics, safeFont, "*", Math.round(favX * inv),
                        Math.round(starY * inv), starColor);
                    graphics.pose().popPose();
                }
            }
        }
    }

    // ================================================================
    // RENDERING - SEARCH OVERLAY
    // ================================================================

    private void renderSearchOverlay(GuiGraphics graphics) {
        float searchBoxAnimation = animator.getSearchBoxAnimation();
        if (searchBoxAnimation < RadialMenuConstants.SEARCH_ANIMATION_EPSILON) return;

        @Nonnull Font safeFont = requireFont();
        RadialTooltipRenderer.SearchConfig searchConfig = new RadialTooltipRenderer.SearchConfig(
            width, height, centerX, config.theme
        );

        // Convert search results to display format
        List<RadialTooltipRenderer.SearchResultDisplay> displayResults = searchResults.stream()
            .map(r -> {
                MacroCategory macro = resolveMacroForCategory(r.getCategory());
                String macroName = macro != null ? macro.getName() : "";
                return new RadialTooltipRenderer.SearchResultDisplay(
                    Objects.requireNonNullElse(r.getItem().getIconStack(), ItemStack.EMPTY),
                    r.getItem().getName(),
                    r.getCategory().getName(),
                    macroName,
                    r.getItem().isToggle(),
                    r.getItem().isActive(),
                    r.getItem().canExecute()
                );
            })
            .toList();

        RadialTooltipRenderer.renderSearchOverlay(graphics, safeFont, searchConfig,
            searchQuery.toString(), displayResults, selectedSearchResult, searchBoxAnimation);
    }

    // ================================================================
    // RENDERING - BACKGROUND & HUB (similar to V2)
    // ================================================================

    private void renderBackground(GuiGraphics graphics) {
        // Solid dark background - more opaque for visibility
        int alpha = (int) (RadialMenuConstants.BACKGROUND_ALPHA_MAX * animator.getOpenAnimation());
        graphics.fill(0, 0, width, height, (alpha << 24) | RadialMenuConstants.BACKGROUND_COLOR);
    }

    private void renderCategoryGlow(GuiGraphics graphics, int categoryIndex) {
        List<RadialCategory> categories = getActiveCategories();
        RadialCategoryRenderer.renderCategoryGlow(graphics, categories, categoryIndex,
            centerX, centerY, itemRadius, animator.getCategoryAnimations());
    }

    /**
     * Renders the center hub with macro-category segments.
     * Each segment is clickable and switches the active macro-category.
     * The selected macro is highlighted with its color.
     */
    private void renderCenterHub(GuiGraphics graphics, int mouseX, int mouseY) {
        // Detect hover state
        RadialHubRenderer.HoverResult hoverResult = RadialHubRenderer.detectHover(
            mouseX, mouseY, centerX, centerY, centerButtonRadius, macroHubRadius);

        hoveredMacro = hoverResult.hoveredMacro();
        boolean centerHovered = hoverResult.centerHovered();

        // Update hover animation via animator
        animator.updateCenterHoverAnimation(centerHovered, config.enableAnimations && !config.isReducedMotionEnabled());

        // Update segment animations via animator
        animator.updateMacroSegmentAnimations(selectedMacro.getIndex(),
            hoveredMacro != null ? hoveredMacro.getIndex() : RadialMenuConstants.NO_SELECTION,
            config.enableAnimations && !config.isReducedMotionEnabled());

        updateMacroAvailability();

        // Build hub state and render
        boolean inSubcategory = currentCategory != null && currentCategory.hasParent();
        RadialHubRenderer.HubState hubState = new RadialHubRenderer.HubState(
            centerX,
            centerY,
            centerButtonRadius,
            macroHubRadius,
            selectedMacro,
            hoveredMacro,
            animator.getMacroSegmentAnimations(),
            macroAvailability,
            animator.getCenterHoverAnimation(),
            searchMode,
            inSubcategory,
            lastContextMode,
            contextPulseStartMs,
            config.isReducedMotionEnabled()
        );

        @Nonnull Font safeFont = requireFont();
        RadialHubRenderer.render(graphics, safeFont, hubState);
    }

    private void renderCategories(GuiGraphics graphics) {
        // Draw ring borders
        RadialCategoryRenderer.renderRingBorders(graphics, centerX, centerY, innerRadius, outerRadius);

        // Build ring config
        RadialCategoryRenderer.RingConfig ringConfig = new RadialCategoryRenderer.RingConfig(
            centerX, centerY, innerRadius, outerRadius, itemRadius,
            selectedCategoryIndex, animator.getCategoryAnimations(), config.theme, config.iconMode, animator.getPulsePhase()
        );
        @Nonnull Font safeFont = requireFont();

        // Cross-fade transition: render outgoing categories (fading out) then incoming (fading in)
        boolean isTransitioning = animator.isTransitioning() && transitionFromMacro != null;

        if (isTransitioning) {
            // Render outgoing categories with decreasing alpha
            float outgoingAlpha = 1f - animator.getMacroTransitionProgress();
            List<RadialCategory> outgoingCategories = getVisibleCategoriesForMacro(transitionFromMacro);
            RadialCategoryRenderer.renderCategoryRing(graphics, safeFont, outgoingCategories, ringConfig, outgoingAlpha, false);
        }

        // Render current (incoming) categories
        List<RadialCategory> categories = getActiveCategories();
        float incomingAlpha = isTransitioning ? animator.getMacroTransitionProgress() : 1f;
        RadialCategoryRenderer.renderCategoryRing(graphics, safeFont, categories, ringConfig, incomingAlpha, true);

        if (keyboardFocusActive) {
            RadialCategoryRenderer.renderCategoryFocusRing(graphics, categories, ringConfig,
                selectedCategoryIndex, incomingAlpha);
        }
    }

    private void renderCategoryItems(GuiGraphics graphics, RadialCategory category) {
        List<RadialCategory> categories = getActiveCategories();
        List<RadialMenuItem> visibleItems = getVisibleItems(category);

        // Build shake offsets and flash alphas for each item
        int itemCount = visibleItems.size();
        float[] shakeOffsets = new float[itemCount];
        float[] flashAlphas = new float[itemCount];
        int[] flashColors = new int[itemCount];
        for (int i = 0; i < itemCount; i++) {
            shakeOffsets[i] = animator.getShakeOffset(i, com.devmod.client.ui.radial.animation.RadialAnimator.TargetType.ITEM);
            flashAlphas[i] = animator.getFlashAlpha(i, com.devmod.client.ui.radial.animation.RadialAnimator.TargetType.ITEM);
            com.devmod.client.ui.radial.animation.RadialAnimator.FlashType flashType =
                animator.getFlashType(i, com.devmod.client.ui.radial.animation.RadialAnimator.TargetType.ITEM);
            flashColors[i] = switch (flashType) {
                case SUCCESS -> RadialMenuConstants.COLOR_FLASH_SUCCESS;
                case FAILED -> RadialMenuConstants.COLOR_FLASH_FAILED;
                case BLOCKED -> RadialMenuConstants.COLOR_FLASH_BLOCKED;
                case NONE -> 0;
            };
        }

        RadialCategoryRenderer.ItemsConfig itemsConfig = new RadialCategoryRenderer.ItemsConfig(
            centerX, centerY, outerRadius,
            selectedCategoryIndex, selectedItemIndex,
            animator.getItemAnimations(), config.theme,
            shakeOffsets, flashAlphas, flashColors, RadialMenuConstants.COLOR_FLASH_BLOCKED
        );

        @Nonnull Font safeFont = requireFont();
        RadialCategoryRenderer.renderCategoryItems(graphics, safeFont, category, visibleItems, categories, itemsConfig);
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        boolean centerHovered = distance < centerButtonRadius * RadialMenuConstants.CLOSE_BUTTON_RATIO;
        boolean macroHubHovered = distance < macroHubRadius;

        // Build favorites reference list for tooltip generation
        List<RadialTooltipRenderer.FavoriteRef> favoriteRefs = favorites.stream()
            .map(f -> new RadialTooltipRenderer.FavoriteRef(f.item.getName()))
            .toList();

        RadialMenuItem selectedItem = getSelectedItem();
        String tooltip = RadialTooltipRenderer.generateTooltip(
            hoveredMacro, selectedMacro,
            selectedFavoriteIndex, favoriteRefs,
            selectedCategoryIndex, selectedItemIndex,
            getActiveCategories(), getActiveItemCategory(),
            selectedItem,
            centerHovered, macroHubHovered, editMode
        );

        if (tooltip != null) {
            RadialTooltipRenderer.TooltipContext context = new RadialTooltipRenderer.TooltipContext(
                width, height, centerX, centerY, outerRadius, config.theme
            );
            @Nonnull Font safeFont = requireFont();
            RadialTooltipRenderer.renderTooltip(graphics, safeFont, tooltip, context);
        }
    }

    private void renderHelpText(GuiGraphics graphics) {
        if (!config.showKeyHints) return;
        @Nonnull Font safeFont = requireFont();
        int helpTop = centerY + RadialMenuScaler.getMenuOuterExtent()
            + RadialMenuScaler.scaleConstant(6);
        RadialTooltipRenderer.renderHelpText(graphics, safeFont, width, height,
            buildHelpLines(), openTime, config.theme, helpTop);
    }

    private void renderBreadcrumb(GuiGraphics graphics) {
        @Nonnull Font safeFont = requireFont();
        RadialTooltipRenderer.renderBreadcrumb(graphics, safeFont,
            width, new ArrayList<>(navigationStack), currentCategory);
    }

    private void renderEditModeIndicator(GuiGraphics graphics) {
        @Nonnull Font safeFont = requireFont();
        RadialTooltipRenderer.renderEditModeIndicator(graphics, safeFont, width);
    }

    private void renderThemeIndicator(GuiGraphics graphics) {
        @Nonnull Font safeFont = requireFont();
        RadialTooltipRenderer.renderThemeIndicator(graphics, safeFont, width, config.theme.presetName, openTime);
    }

    // ================================================================
    // INPUT HANDLING
    // ================================================================

    private boolean primaryMouseDown = false;
    private long primaryMouseDownAt = 0L;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        clearKeyboardFocus();
        if (button == 0) {
            primaryMouseDown = true;
            primaryMouseDownAt = System.currentTimeMillis();
            return true;
        } else if (button == 1 && config.rightClickToEdit) {
            openSelectedDetails();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        clearKeyboardFocus();
        if (button != 0) {
            return super.mouseReleased(mouseX, mouseY, button);
        }

        long now = System.currentTimeMillis();
        boolean wasHeld = primaryMouseDown && (now - primaryMouseDownAt) >= RadialMenuConstants.LONG_PRESS_DURATION_MS;
        primaryMouseDown = false;

        if (wasHeld) {
            return handleLongPress();
        }

        // Alcuni trackpad su macOS inviano solo mouseReleased: ripeti la logica del click primario.
        return handlePrimaryClick(hasShiftDown(), hasControlDown(), mouseX, mouseY);
    }

    private boolean handlePrimaryClick(boolean shiftHeld, boolean ctrlHeld, double mouseX, double mouseY) {
        // Ensure selection reflects the actual click location, not just the last hover frame.
        updateSelection((int) Math.round(mouseX), (int) Math.round(mouseY));
        RadialHubRenderer.HoverResult hoverResult = RadialHubRenderer.detectHover(
            (int) Math.round(mouseX),
            (int) Math.round(mouseY),
            centerX,
            centerY,
            centerButtonRadius,
            macroHubRadius
        );
        hoveredMacro = hoverResult.hoveredMacro();

        if (hoverResult.centerHovered()) {
            if (currentCategory != null && currentCategory.hasParent()) {
                navigateBack();
            } else {
                animator.startClose();
            }
            return true;
        }

        if (hoveredMacro != null) {
            if (!macroHasVisibleCategories(hoveredMacro)) {
                showMessage(Minecraft.getInstance(), I18n.translate("devmod.radial.message.no_items_mode"));
                return true;
            }
            if (hoveredMacro != selectedMacro) {
                transitionFromMacro = selectedMacro;
                selectedMacro = hoveredMacro;
                animator.startMacroTransition();
                selectedCategoryIndex = RadialMenuConstants.NO_SELECTION;
                selectedItemIndex = RadialMenuConstants.NO_SELECTION;
                clearSubcategory();
                playSound(RadialMenuConstants.SOUND_PITCH_MACRO_SWITCH);
                String fromMacro = transitionFromMacro != null ? transitionFromMacro.name() : "unknown";
                UiTelemetry.action("radial", "menu", "macro_changed",
                    Map.of("from", fromMacro, "to", selectedMacro.name(), "source", "mouse"));
            }
            return true;
        }

        // Check favorites first
        if (editMode && selectedFavoriteIndex >= 0 && selectedFavoriteIndex < favorites.size()) {
            removeFavorite(selectedFavoriteIndex);
            return true;
        }
        if (selectedFavoriteIndex >= 0 && selectedFavoriteIndex < favorites.size()) {
            FavoriteItem fav = favorites.get(selectedFavoriteIndex);
            executeItem(fav.item, ActionSource.FAVORITE, null, null);
            return true;
        }

        // Ctrl+click to toggle quick action pin
        if (ctrlHeld && selectedItemIndex >= 0 && selectedCategoryIndex >= 0) {
            RadialCategory itemCategory = getActiveItemCategory();
            if (itemCategory != null) {
                List<RadialMenuItem> visibleItems = getVisibleItems(itemCategory);
                if (selectedItemIndex < visibleItems.size()) {
                    RadialMenuItem item = visibleItems.get(selectedItemIndex);
                    togglePin(item);
                }
            }
            return true;
        }

        // Shift+click to toggle favorite
        if (shiftHeld && selectedItemIndex >= 0 && selectedCategoryIndex >= 0) {
            RadialCategory itemCategory = getActiveItemCategory();
            if (itemCategory != null) {
                List<RadialMenuItem> visibleItems = getVisibleItems(itemCategory);
                if (selectedItemIndex < visibleItems.size()) {
                    RadialMenuItem item = visibleItems.get(selectedItemIndex);
                    toggleFavorite(item, itemCategory);
                }
            }
            return true;
        }

        if (editMode && selectedItemIndex >= 0) {
            RadialCategory itemCategory = getActiveItemCategory();
            if (itemCategory != null) {
                List<RadialMenuItem> visibleItems = getVisibleItems(itemCategory);
                if (selectedItemIndex < visibleItems.size()) {
                    RadialMenuItem item = visibleItems.get(selectedItemIndex);
                    if (item.isSubcategoryLink()) {
                        RadialCategory subcategory = item.getLinkedSubcategory();
                        if (subcategory != null) {
                            navigateTo(subcategory);
                        }
                    } else {
                        toggleFavorite(item, itemCategory);
                    }
                }
            }
        } else {
            activateSelection(mouseX, mouseY);
        }
        return true;
    }

    private boolean handleLongPress() {
        if (selectedFavoriteIndex >= 0 && selectedFavoriteIndex < favorites.size()) {
            FavoriteItem fav = favorites.get(selectedFavoriteIndex);
            openItemDetails(fav.item);
            return true;
        }

        RadialCategory itemCategory = getActiveItemCategory();
        if (itemCategory != null) {
            List<RadialMenuItem> visibleItems = getVisibleItems(itemCategory);
            if (selectedItemIndex >= 0 && selectedItemIndex < visibleItems.size()) {
                RadialMenuItem item = visibleItems.get(selectedItemIndex);
                if (item.isSubcategoryLink()) {
                    RadialCategory subcategory = item.getLinkedSubcategory();
                    if (subcategory != null) {
                        navigateTo(subcategory);
                        return true;
                    }
                }
                openItemDetails(item);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        clearKeyboardFocus();
        if (searchMode) {
            if (!searchResults.isEmpty()) {
                if (scrollY > 0) {
                    selectedSearchResult = Math.max(0, selectedSearchResult - 1);
                } else {
                    selectedSearchResult = Math.min(searchResults.size() - 1, selectedSearchResult + 1);
                }
            }
            return true;
        }

        // Scroll to navigate categories
        if (scrollY > 0) {
            prevCategory();
        } else if (scrollY < 0) {
            nextCategory();
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == config.input.keyReleaseSelect && !searchMode) {
            // Always consume the event for the release key
            if (!config.releaseToSelect) {
                // releaseToSelect disabled - just close
                animator.startClose();
                return true;
            }

            // Guard 1: debounce - too fast, just close
            long elapsed = System.currentTimeMillis() - openTime;
            if (elapsed < RadialMenuConstants.RELEASE_DEBOUNCE_MS) {
                animator.startClose();
                return true;
            }

            // Guard 2: no selection - just close
            RadialMenuItem selected = getSelectedItem();
            if (selected == null) {
                animator.startClose();
                return true;
            }

            // Guard 3: blocked - show feedback, stay open so user can see why
            if (!selected.canExecute()) {
                showBlockedFeedback(selected, null, true);
                return true; // Don't close - let user see the feedback
            }

            // All guards passed - execute and close
            activateSelection(lastMouseX, lastMouseY);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    /**
     * Show feedback when an action is blocked by precondition.
     * Uses action bar message, error sound, and micro-shake animation.
     */
    private void showBlockedFeedback(RadialMenuItem item, @Nullable Component reason, boolean showMessage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Get the failure reason as Component (preserves localization)
        @Nonnull Component resolvedReason = Objects.requireNonNull(
            reason != null ? reason : resolveBlockReasonComponent(item), "block reason");
        if (showMessage) {
            showMessage(mc, resolvedReason);
        }

        // Play error sound
        if (config.enableSounds) {
            @Nonnull SoundEvent sound = Objects.requireNonNull(
                SoundEvents.NOTE_BLOCK_BASS.value(), "bass sound");
            mc.getSoundManager().play(
                Objects.requireNonNull(SimpleSoundInstance.forUI(sound, 0.5f), "sound instance")
            );
        }

        // Trigger micro-shake animation (respects reduced motion in update loop)
        if (config.enableAnimations && !config.isReducedMotionEnabled()) {
            if (selectedItemIndex >= 0) {
                animator.startItemShake(selectedItemIndex);
                animator.startItemBlockedFlash(selectedItemIndex);
            } else if (selectedFavoriteIndex >= 0) {
                animator.startFavoriteShake(selectedFavoriteIndex);
                animator.startFavoriteBlockedFlash(selectedFavoriteIndex);
            }
        }

        // Telemetry for blocked action
        String actionId = item.getAction().getRegistryId();
        if (actionId != null) {
            Map<String, Object> context = new HashMap<>();
            context.put("actionId", actionId);
            context.put("reasonKey", resolveBlockReasonKey(resolvedReason, item));
            RadialBlockedHelpResolver.BlockedHelp help =
                RadialBlockedHelpResolver.resolve(resolvedReason, resolvedReason.getString());
            if (help != null) {
                context.put("helpKey", help.key());
            }
            UiTelemetry.action("radial", "menu", "action_blocked", context);
        }
    }

    /**
     * Resolve the block reason key for telemetry.
     */
    private String resolveBlockReasonKey(@Nullable Component reasonComponent, RadialMenuItem item) {
        if (reasonComponent != null && reasonComponent.getContents() instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }
        return resolveBlockReasonKeyFromPrecondition(item);
    }

    private String resolveBlockReasonKeyFromPrecondition(RadialMenuItem item) {
        String actionId = item.getAction().getRegistryId();
        if (actionId == null) {
            return "unknown";
        }
        com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
        if (action == null) {
            return "action_not_found";
        }
        // Prefer translation key for stable telemetry
        @Nonnull Component reason = Objects.requireNonNull(
            action.getPrecondition().failureMessage(ClientActionContexts.forRadial()),
            "block reason");
        if (reason.getContents() instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }
        // Fallback to precondition type if not translatable
        return action.getPrecondition().getClass().getSimpleName();
    }

    /**
     * Resolve the block reason as a Component with red formatting.
     */
    private Component resolveBlockReasonComponent(RadialMenuItem item) {
        String actionId = item.getAction().getRegistryId();
        if (actionId == null) {
            return Component.translatable("devmod.action.unavailable")
                .withStyle(net.minecraft.ChatFormatting.RED);
        }
        com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
        if (action == null) {
            return Component.translatable("devmod.action.unavailable")
                .withStyle(net.minecraft.ChatFormatting.RED);
        }
        return action.getPrecondition()
            .failureMessage(ClientActionContexts.forRadial())
            .copy()
            .withStyle(net.minecraft.ChatFormatting.RED);
    }

    private Component resolveBlockReasonComponentForResult(RadialMenuItem item,
                                                           @Nullable com.devmod.actions.RadialAction action,
                                                           ActionResult result) {
        String errorCode = result.errorCode();
        if (ActionResult.ERROR_REQUIRES_CONFIRM.equals(errorCode)) {
            return Component.translatable("devmod.action.requires_confirm")
                .withStyle(net.minecraft.ChatFormatting.RED);
        }
        if (ActionResult.ERROR_UNKNOWN_ACTION.equals(errorCode)) {
            return Component.translatable("devmod.action.unavailable")
                .withStyle(net.minecraft.ChatFormatting.RED);
        }
        if (ActionResult.ERROR_UNTRUSTED_ACTION.equals(errorCode)) {
            @Nonnull String message = Objects.requireNonNull(
                Objects.requireNonNullElse(result.message(),
                    I18n.translate("devmod.action.unavailable").getString()),
                "message");
            return Component.literal(message).withStyle(net.minecraft.ChatFormatting.RED);
        }
        if (action != null) {
            return action.getPrecondition()
                .failureMessage(ClientActionContexts.forRadial())
                .copy()
                .withStyle(net.minecraft.ChatFormatting.RED);
        }
        return resolveBlockReasonComponent(item);
    }

    private boolean shouldShowBlockedMessage(ActionResult result) {
        String errorCode = result.errorCode();
        return ActionResult.ERROR_UNKNOWN_ACTION.equals(errorCode)
            || ActionResult.ERROR_UNTRUSTED_ACTION.equals(errorCode);
    }

    private boolean handleActionResult(@Nonnull ActionResult result,
                                       @Nonnull RadialMenuItem item,
                                       @Nullable com.devmod.actions.RadialAction action,
                                       @Nonnull ActionSource source) {
        if (result.isSuccess()) {
            return true;
        }
        if (result.isBlocked()) {
            Component reason = resolveBlockReasonComponentForResult(item, action, result);
            boolean showMessage = shouldShowBlockedMessage(result);
            showBlockedFeedback(item, reason, showMessage);
            return false;
        }
        if (result.isFailed()) {
            triggerActionFlash(source, com.devmod.client.ui.radial.animation.RadialAnimator.FlashType.FAILED);
            return false;
        }
        return false;
    }

    private void triggerActionFlash(@Nonnull ActionSource source,
                                    @Nonnull com.devmod.client.ui.radial.animation.RadialAnimator.FlashType flashType) {
        if (!config.enableAnimations || config.isReducedMotionEnabled()) {
            return;
        }
        if (source == ActionSource.FAVORITE && selectedFavoriteIndex >= 0) {
            switch (flashType) {
                case SUCCESS -> animator.startFavoriteSuccessFlash(selectedFavoriteIndex);
                case FAILED -> animator.startFavoriteFailedFlash(selectedFavoriteIndex);
                case BLOCKED -> animator.startFavoriteBlockedFlash(selectedFavoriteIndex);
                case NONE -> {}
            }
            return;
        }
        if (selectedItemIndex >= 0) {
            switch (flashType) {
                case SUCCESS -> animator.startItemSuccessFlash(selectedItemIndex);
                case FAILED -> animator.startItemFailedFlash(selectedItemIndex);
                case BLOCKED -> animator.startItemBlockedFlash(selectedItemIndex);
                case NONE -> {}
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyboardFocusActive) {
            lastKeyboardInputAt = System.currentTimeMillis();
        }
        // Search mode input
        if (searchMode) {
            if (keyCode == config.input.keyMenuClose) {
                searchMode = false;
                searchQuery.setLength(0);
                searchResults.clear();
                return true;
            }
            if (keyCode == config.input.keySearchConfirm && selectedSearchResult >= 0) {
                RadialSearchHandler.SearchResult result = searchResults.get(selectedSearchResult);
                executeItem(result.getItem(), ActionSource.SEARCH, result.getCategory(), searchQuery.toString());
                searchMode = false;
                return true;
            }
            if (keyCode == config.input.keySearchBackspace && searchQuery.length() > 0) {
                searchQuery.deleteCharAt(searchQuery.length() - 1);
                updateSearchResults();
                return true;
            }
            if (keyCode == config.input.keySearchUp) {
                selectedSearchResult = Math.max(0, selectedSearchResult - 1);
                return true;
            }
            if (keyCode == config.input.keySearchDown) {
                selectedSearchResult = Math.min(searchResults.size() - 1, selectedSearchResult + 1);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == config.input.keyMenuClose) {
            animator.startClose();
            return true;
        }

        // / or F to toggle search
        if (keyCode == config.input.keySearchTogglePrimary || keyCode == config.input.keySearchToggleSecondary) {
            searchMode = !searchMode;
            if (searchMode) {
                clearSelection();
                clearKeyboardFocus();
            } else {
                searchQuery.setLength(0);
                searchResults.clear();
            }
            return true;
        }

        if (keyCode == config.input.keyEditModeToggleLeft || keyCode == config.input.keyEditModeToggleRight) {
            editMode = !editMode;
            return true;
        }

        if (keyCode == config.input.keyThemeCycle) {
            config.cycleTheme();
            playSound(RadialMenuConstants.SOUND_PITCH_THEME_CYCLE);
            UiTelemetry.action("radial", "menu", "theme_changed",
                Map.of("preset", config.theme.presetName != null ? config.theme.presetName : "custom"));
            return true;
        }

        if (keyCode == config.input.keyProfileCycle) {
            config.cycleProfile();
            clearSelection();
            clearSubcategory();
            ensureSelectedMacroVisible();
            clearKeyboardFocus();
            playSound(RadialMenuConstants.SOUND_PITCH_THEME_CYCLE);
            UiTelemetry.action("radial", "menu", "profile_changed",
                Map.of("profile", config.menuProfile.name()));
            return true;
        }

        if (keyCode == config.input.keySafeModeToggle) {
            config.safeMode = !config.safeMode;
            config.save();
            clearSelection();
            clearSubcategory();
            ensureSelectedMacroVisible();
            clearKeyboardFocus();
            playSound(RadialMenuConstants.SOUND_PITCH_THEME_CYCLE);
            return true;
        }

        if (hasAltDown()) {
            int quickIndex = indexOfKey(keyCode, config.input.macroKeys);
            if (quickIndex != RadialMenuConstants.NO_SELECTION && quickIndex < quickActions.size()) {
                PinnedItem pinned = quickActions.get(quickIndex);
                if (!isItemAllowedByProfile(pinned.item)) {
                    showMessage(Minecraft.getInstance(), I18n.translate("devmod.radial.message.no_items_mode"));
                    return true;
                }
                executeItem(pinned.item, ActionSource.PINNED, null, null);
                return true;
            }
        }

        // Number keys switch macro-categories (defaults: GLFW_KEY_1 .. GLFW_KEY_6)
        int macroIndex = indexOfKey(keyCode, config.input.macroKeys);
        if (macroIndex != RadialMenuConstants.NO_SELECTION) {
            clearKeyboardFocus();
            MacroCategory[] macros = MacroCategory.values();
            if (macroIndex < macros.length) {
                MacroCategory targetMacro = macros[macroIndex];
                if (!macroHasVisibleCategories(targetMacro)) {
                    showMessage(Minecraft.getInstance(), I18n.translate("devmod.radial.message.no_items_mode"));
                    return true;
                }
                if (targetMacro != selectedMacro) {
                    transitionFromMacro = selectedMacro;
                    selectedMacro = targetMacro;
                    animator.startMacroTransition();
                    selectedCategoryIndex = RadialMenuConstants.NO_SELECTION; // selectedCategoryIndex = -1 (reset on macro change)
                    selectedItemIndex = RadialMenuConstants.NO_SELECTION;
                    clearSubcategory();
                    playSound(RadialMenuConstants.SOUND_PITCH_MACRO_SWITCH);
                    String fromMacro = transitionFromMacro != null ? transitionFromMacro.name() : "unknown";
                    UiTelemetry.action("radial", "menu", "macro_changed",
                        Map.of("from", fromMacro, "to", selectedMacro.name(), "source", "keyboard"));
                }
            }
            return true;
        }

        // Keys 7-0 and -/= select categories within current macro (defaults: GLFW_KEY_7 .. GLFW_KEY_0, GLFW_KEY_MINUS, GLFW_KEY_EQUAL)
        int categoryIndex = indexOfKey(keyCode, config.input.categoryKeys);
        if (categoryIndex != RadialMenuConstants.NO_SELECTION) {
            if (categoryIndex < getActiveCategories().size()) {
                clearSubcategory();
                animator.startMorph();
                selectedCategoryIndex = categoryIndex;
                selectedItemIndex = RadialMenuConstants.NO_SELECTION;
                selectedFavoriteIndex = RadialMenuConstants.NO_SELECTION;
                markKeyboardFocus();
            }
            return true;
        }

        // Left/Right arrows for categories
        if (keyCode == config.input.keyCategoryLeft) {
            prevCategory();
            if (selectedCategoryIndex != RadialMenuConstants.NO_SELECTION) {
                markKeyboardFocus();
            }
            return true;
        }
        if (keyCode == config.input.keyCategoryRight) {
            nextCategory();
            if (selectedCategoryIndex != RadialMenuConstants.NO_SELECTION) {
                markKeyboardFocus();
            }
            return true;
        }

        RadialCategory cat = getActiveItemCategory();
        if (cat != null) {
            List<RadialMenuItem> visibleItems = getVisibleItems(cat);
            int itemNum = getItemKeyIndex(keyCode);

            if (itemNum >= 0 && itemNum < visibleItems.size()) {
                if (editMode) {
                    selectedItemIndex = itemNum;
                    RadialMenuItem item = visibleItems.get(itemNum);
                    if (item.isSubcategoryLink()) {
                        RadialCategory subcategory = item.getLinkedSubcategory();
                        if (subcategory != null) {
                            navigateTo(subcategory);
                        }
                    } else {
                        toggleFavorite(item, cat);
                    }
                } else {
                    executeItem(visibleItems.get(itemNum), ActionSource.NORMAL, cat, null);
                }
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if ((searchMode && Character.isLetterOrDigit(chr)) || chr == config.input.searchQuerySpace) {
            searchQuery.append(chr);
            updateSearchResults();
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private int getItemKeyIndex(int keyCode) {
        return indexOfKey(keyCode, config.input.itemKeys);
    }

    private static int indexOfKey(int keyCode, int[] keys) {
        if (keys == null || keys.length == 0) {
            return RadialMenuConstants.NO_SELECTION;
        }
        for (int i = 0; i < keys.length; i++) {
            if (keyCode == keys[i]) {
                return i;
            }
        }
        return RadialMenuConstants.NO_SELECTION;
    }

    private void activateSelection(double mouseX, double mouseY) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        // Check if clicking on center close button (very center)
        if (distance < centerButtonRadius * RadialMenuConstants.CLOSE_BUTTON_RATIO) {
            if (currentCategory != null && currentCategory.hasParent()) {
                navigateBack();
            } else {
                animator.startClose();
            }
            return;
        }

        // Check if clicking on a macro-category segment
        if (distance < macroHubRadius) {
            if (hoveredMacro != null && hoveredMacro != selectedMacro) {
                if (!macroHasVisibleCategories(hoveredMacro)) {
                    showMessage(Minecraft.getInstance(), I18n.translate("devmod.radial.message.no_items_mode"));
                    return;
                }
                // Switch to the hovered macro-category
                transitionFromMacro = selectedMacro;
                selectedMacro = hoveredMacro;
                animator.startMacroTransition();
                selectedCategoryIndex = RadialMenuConstants.NO_SELECTION;
                selectedItemIndex = RadialMenuConstants.NO_SELECTION;
                clearSubcategory();
                playSound(RadialMenuConstants.SOUND_PITCH_MACRO_SWITCH);
            }
            return;
        }

        // Check favorites
        if (selectedFavoriteIndex >= 0 && selectedFavoriteIndex < favorites.size()) {
            FavoriteItem fav = favorites.get(selectedFavoriteIndex);
            if (editMode) {
                removeFavorite(selectedFavoriteIndex);
            } else {
                executeItem(fav.item, ActionSource.FAVORITE, null, null);
            }
            return;
        }

        if (selectedCategoryIndex >= 0 && selectedCategoryIndex < getActiveCategories().size()) {
            RadialCategory cat = getActiveItemCategory();
            if (cat == null) {
                return;
            }
            List<RadialMenuItem> visibleItems = getVisibleItems(cat);
            if (selectedItemIndex == RadialMenuConstants.NO_SELECTION && !visibleItems.isEmpty()) {
                RadialMenuItem item = visibleItems.get(0);
                if (item.isSubcategoryLink()) {
                    RadialCategory subcategory = item.getLinkedSubcategory();
                    if (subcategory != null) {
                        navigateTo(subcategory);
                        return;
                    }
                }
                if (editMode) {
                    toggleFavorite(item, cat);
                } else {
                    executeItem(item, ActionSource.NORMAL, cat, null);
                }
                return;
            }
            if (selectedItemIndex >= 0 && selectedItemIndex < visibleItems.size()) {
                RadialMenuItem item = visibleItems.get(selectedItemIndex);

                if (item.isSubcategoryLink()) {
                    RadialCategory subcategory = item.getLinkedSubcategory();
                    if (subcategory != null) {
                        navigateTo(subcategory);
                        return;
                    }
                }

                if (editMode) {
                    toggleFavorite(item, cat);
                } else {
                    executeItem(item, ActionSource.NORMAL, cat, null);
                }
            }
        }
    }

    private enum ActionSource {
        NORMAL,
        FAVORITE,
        PINNED,
        SEARCH
    }

    private void executeItem(RadialMenuItem item, ActionSource source,
                             @Nullable RadialCategory contextCategory,
                             @Nullable String searchQuerySnapshot) {
        try {
            // Track time to first action
            if (!firstActionExecuted) {
                firstActionExecuted = true;
                long timeToFirstAction = System.currentTimeMillis() - openTime;
                logTimeToFirstAction(timeToFirstAction);
            }
            actionsExecutedCount++;

            String actionId = item.getAction().getRegistryId();
            if (actionId != null) {
                com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
                RadialActionSafety.RiskLevel riskLevel = RadialActionSafety.evaluate(item);
                if (action != null && (action.requiresConfirm() || riskLevel == RadialActionSafety.RiskLevel.DANGER)) {
                    openItemDetails(item, riskLevel);
                    return;
                }
                ActionResult result = ActionRegistry.invokeWithResult(actionId, ClientActionContexts.forRadial());
                if (!handleActionResult(result, item, action, source)) {
                    return;
                }
            } else {
                item.execute();
            }

            recordUsage(item);
            logActionTelemetry(item, contextCategory, source, searchQuerySnapshot);

            SettingsManager.INSTANCE.syncFromSystems();
            SettingsManager.INSTANCE.save();

            OnboardingOverlay.onOverlayToggled();
            OnboardingOverlay.onCategorySelected();

            Minecraft mc = Minecraft.getInstance();
            var player = mc.player;
            if (player != null && item.isToggle()) {
                String statusKey = item.isActive() ? "devmod.status.on" : "devmod.status.off";
                player.displayClientMessage(
                    I18n.translate("devmod.message.item_toggled", item.getName(),
                        I18n.translate(statusKey).getString()),
                    true);
            }

            triggerActionFlash(source, com.devmod.client.ui.radial.animation.RadialAnimator.FlashType.SUCCESS);

            if (config.enableSounds) {
                float pitch = item.isToggle()
                    ? (item.isActive()
                        ? RadialMenuConstants.SOUND_PITCH_TOGGLE_ON
                        : RadialMenuConstants.SOUND_PITCH_TOGGLE_OFF)
                    : RadialMenuConstants.SOUND_PITCH_ACTION_DEFAULT;
                playSound(pitch);
            }

            if (config.closeOnToggle && item.isToggle()) {
                animator.startClose();
            }
        } catch (Exception e) {
            String actionId = item.getAction().getRegistryId();
            String actionLabel = actionId != null ? actionId : item.getName();
            LOGGER.error("[RadialMenuScreen] Action execution failed: {}", actionLabel, e);
            showMessage(Minecraft.getInstance(), I18n.translate("devmod.radial.message.action_error"));
            UiTelemetry.actionError("radial", "menu", Objects.requireNonNull(actionLabel != null ? actionLabel : "unknown"), e);
        }
    }

    private void logActionTelemetry(RadialMenuItem item,
                                    @Nullable RadialCategory contextCategory,
                                    ActionSource source,
                                    @Nullable String searchQuerySnapshot) {
        String actionId = item.getAction().getRegistryId();
        String actionLabel = actionId != null ? actionId : item.getName();

        RadialCategory category = contextCategory != null ? contextCategory : getActiveItemCategory();
        if (category == null) {
            category = resolveCategoryForItem(item);
        }
        MacroCategory macro = resolveMacroForCategory(category);

        String macroId = macro != null ? macro.name() : "unknown";
        String macroName = macro != null ? macro.getName() : "unknown";
        String categoryId = category != null ? category.getId() : "unknown";
        String categoryName = category != null ? category.getName() : "unknown";
        String itemName = item.getName() != null ? item.getName() : actionLabel;

        String path = macroId + " / " + categoryId + " / " + actionLabel;

        Map<String, Object> contextMode = new HashMap<>();
        contextMode.put("mode", ContextDetector.INSTANCE.getCurrentMode().name());
        contextMode.put("actionId", actionLabel);
        UiTelemetry.action("radial", "menu", "radial_context_mode", contextMode);

        Map<String, Object> navPath = new HashMap<>();
        navPath.put("macro", macroId);
        navPath.put("macroName", macroName);
        navPath.put("category", categoryId);
        navPath.put("categoryName", categoryName);
        navPath.put("item", actionLabel);
        navPath.put("itemName", itemName);
        navPath.put("path", path);
        UiTelemetry.action("radial", "menu", "radial_navigation_path", navPath);

        if (source == ActionSource.FAVORITE || source == ActionSource.PINNED) {
            Map<String, Object> favoriteContext = new HashMap<>();
            favoriteContext.put("actionId", actionLabel);
            favoriteContext.put("source", source.name().toLowerCase(Locale.ROOT));
            UiTelemetry.action("radial", "menu", "radial_favorite_used", favoriteContext);
        }

        if (source == ActionSource.SEARCH) {
            Map<String, Object> searchContext = new HashMap<>();
            searchContext.put("actionId", actionLabel);
            if (searchQuerySnapshot != null && !searchQuerySnapshot.isBlank()) {
                searchContext.put("query", searchQuerySnapshot);
            }
            UiTelemetry.action("radial", "menu", "radial_search_used", searchContext);
        }
    }

    @Nullable
    private RadialCategory resolveCategoryForItem(RadialMenuItem item) {
        java.util.Set<RadialCategory> visited = new java.util.HashSet<>();
        for (MacroCategory macro : MacroCategory.values()) {
            List<RadialCategory> categories = macroCategoryMap.get(macro);
            if (categories == null) {
                continue;
            }
            for (RadialCategory category : categories) {
                RadialCategory found = findCategoryForItem(category, item, visited);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Nullable
    private RadialCategory findCategoryForItem(RadialCategory category, RadialMenuItem item,
                                               java.util.Set<RadialCategory> visited) {
        if (category == null || !visited.add(category)) {
            return null;
        }
        for (RadialMenuItem candidate : category.getItems()) {
            if (matchesItem(candidate, item)) {
                return category;
            }
            if (candidate.isSubcategoryLink()) {
                RadialCategory subcategory = candidate.getLinkedSubcategory();
                if (subcategory != null) {
                    RadialCategory found = findCategoryForItem(subcategory, item, visited);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private boolean matchesItem(RadialMenuItem candidate, RadialMenuItem target) {
        String candidateId = candidate.getAction().getRegistryId();
        String targetId = target.getAction().getRegistryId();
        if (candidateId != null && targetId != null) {
            return candidateId.equals(targetId);
        }
        return Objects.equals(candidate.getName(), target.getName());
    }

    private void openSelectedDetails() {
        if (selectedFavoriteIndex >= 0 && selectedFavoriteIndex < favorites.size()) {
            openItemDetails(favorites.get(selectedFavoriteIndex).item);
            return;
        }

        RadialCategory itemCategory = getActiveItemCategory();
        if (itemCategory == null) {
            return;
        }
        List<RadialMenuItem> visibleItems = getVisibleItems(itemCategory);
        if (selectedItemIndex < 0 || selectedItemIndex >= visibleItems.size()) {
            return;
        }
        RadialMenuItem item = visibleItems.get(selectedItemIndex);
        if (item.isSubcategoryLink()) {
            RadialCategory subcategory = item.getLinkedSubcategory();
            if (subcategory != null) {
                navigateTo(subcategory);
            }
            return;
        }
        openItemDetails(item);
    }

    private void openItemDetails(RadialMenuItem item) {
        openItemDetails(item, RadialActionSafety.evaluate(item));
    }

    private void openItemDetails(RadialMenuItem item, RadialActionSafety.RiskLevel riskLevel) {
        com.devmod.client.ui.ScreenSafety.openSafe(
            "radial_action_detail",
            this,
            () -> new RadialActionDetailScreen(this, item, riskLevel));
    }

    private void navigateTo(RadialCategory category) {
        if (currentCategory != null) {
            navigationStack.addFirst(currentCategory);
        }
        currentCategory = category;
        selectedItemIndex = RadialMenuConstants.NO_SELECTION;
        int topLevelIndex = resolveTopLevelCategoryIndex(category);
        if (topLevelIndex != RadialMenuConstants.NO_SELECTION) {
            selectedCategoryIndex = topLevelIndex;
        }
        playSound(RadialMenuConstants.SOUND_PITCH_NAVIGATE_TO);
    }

    private void navigateBack() {
        if (!navigationStack.isEmpty()) {
            currentCategory = navigationStack.removeFirst();
        } else {
            currentCategory = null;
        }
        selectedItemIndex = RadialMenuConstants.NO_SELECTION;
        if (currentCategory != null) {
            int topLevelIndex = resolveTopLevelCategoryIndex(currentCategory);
            if (topLevelIndex != RadialMenuConstants.NO_SELECTION) {
                selectedCategoryIndex = topLevelIndex;
            }
        }
        playSound(RadialMenuConstants.SOUND_PITCH_NAVIGATE_BACK);
    }

    // ================================================================
    // UTILITY
    // ================================================================

    private void playSound(float pitch) {
        playSound(pitch, RadialMenuConstants.SOUND_VOLUME_DEFAULT);
    }

    private void playSound(float pitch, float volume) {
        if (config.enableSounds) {
            @Nonnull SoundEvent soundEvent = Objects.requireNonNull(SoundEvents.UI_BUTTON_CLICK.value(), "uiClick");
            @Nonnull SoundInstance sound = Objects.requireNonNull(SimpleSoundInstance.forUI(soundEvent, pitch, volume), "uiSound");
            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }

    private void playHoverSound(float pitch, float volume) {
        if (!config.enableSounds) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastHoverSoundAt < RadialMenuConstants.HOVER_SOUND_COOLDOWN_MS) {
            return;
        }
        lastHoverSoundAt = now;
        playSound(pitch, volume);
    }

    private void showMessage(Minecraft mc, @Nonnull Component message) {
        var player = mc.player;
        if (player != null) {
            @Nonnull Component safeMessage = Objects.requireNonNull(message, "message");
            player.displayClientMessage(
                Objects.requireNonNull(safeMessage, "messageComponent"),
                true);
        }
    }

    private void persistUserState() {
        if (!favoritesDirty && !usageDirty && !recentsDirty && !pinsDirty) {
            return;
        }
        if (config.favoriteActionIds == null) {
            config.favoriteActionIds = new ArrayList<>();
        }
        config.favoriteActionIds.clear();
        java.util.Set<String> favoriteIds = new java.util.HashSet<>();
        for (FavoriteItem fav : favorites) {
            String actionId = fav.item.getAction().getRegistryId();
            if (actionId != null && favoriteIds.add(actionId)) {
                config.favoriteActionIds.add(actionId);
            }
        }

        if (config.usageStats == null) {
            config.usageStats = new HashMap<>();
        }
        config.usageStats.clear();
        config.usageStats.putAll(usageStats);

        if (config.recentActionIds == null) {
            config.recentActionIds = new ArrayList<>();
        }
        config.recentActionIds.clear();
        config.recentActionIds.addAll(recentActionIds);

        if (config.pinnedActionIds == null) {
            config.pinnedActionIds = new ArrayList<>();
        }
        config.pinnedActionIds.clear();
        for (PinnedItem pinned : quickActions) {
            if (pinned.actionId != null && !pinned.actionId.isBlank()) {
                config.pinnedActionIds.add(pinned.actionId);
            }
        }

        config.save();
        favoritesDirty = false;
        usageDirty = false;
        recentsDirty = false;
        pinsDirty = false;
    }

    @Override
    public void onClose() {
        persistUserState();
        // Track screen close for telemetry
        UiTelemetry.screenClosed("radial", "radial_menu");
        logMenuClosed();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================
    // INNER CLASSES
    // ================================================================

    // SearchResult moved to RadialSearchHandler.SearchResult

    private static class FavoriteItem {
        final String key;
        final RadialMenuItem item;

        FavoriteItem(String key, RadialMenuItem item) {
            this.key = key;
            this.item = item;
        }
    }

    private static class PinnedItem {
        final String actionId;
        final RadialMenuItem item;

        PinnedItem(String actionId, RadialMenuItem item) {
            this.actionId = actionId;
            this.item = item;
        }
    }

    private static class IndexedItem {
        final RadialMenuItem item;
        final int index;

        IndexedItem(RadialMenuItem item, int index) {
            this.item = item;
            this.index = index;
        }
    }

    private void clearSelection() {
        selectedCategoryIndex = RadialMenuConstants.NO_SELECTION;
        selectedItemIndex = RadialMenuConstants.NO_SELECTION;
        selectedFavoriteIndex = RadialMenuConstants.NO_SELECTION;
    }

    private List<String> buildHelpLines() {
        List<String> lines = new ArrayList<>();
        if (searchMode) {
            lines.add(I18n.translate("devmod.radial.help.search").getString());
            lines.add(I18n.translate("devmod.radial.help.search_scroll").getString());
            return lines;
        }

        String searchKey = keyName(config.input.keySearchTogglePrimary);
        lines.add(I18n.translate("devmod.radial.help.macro_line", selectedMacro.getName(), searchKey).getString());

        String keyHints = buildKeyHintLine();
        if (!keyHints.isBlank()) {
            lines.add(keyHints);
        }

        String itemHints = buildItemKeyLine();
        if (!itemHints.isBlank()) {
            lines.add(itemHints);
        }

        String profileLine = buildProfileLine();
        if (!profileLine.isBlank()) {
            lines.add(profileLine);
        }

        String quickLine = buildQuickActionsLine();
        if (!quickLine.isBlank()) {
            lines.add(quickLine);
        }

        String recentLine = buildRecentLine();
        if (!recentLine.isBlank()) {
            lines.add(recentLine);
        }
        return lines;
    }

    private String buildKeyHintLine() {
        String macroKeys = formatKeyRange(config.input.macroKeys,
            new RadialMenuConfig.InputBindings().macroKeys, "1-6");
        String categoryKeys = formatKeyRange(config.input.categoryKeys,
            new RadialMenuConfig.InputBindings().categoryKeys, "7-0-/=");
        StringBuilder hint = new StringBuilder(I18n.translate("devmod.radial.help.keys",
            macroKeys, categoryKeys).getString());

        if (config.releaseToSelect) {
            String releaseKey = keyName(config.input.keyReleaseSelect);
            hint.append(I18n.translate("devmod.radial.help.release_select", releaseKey).getString());
        }

        if (favorites.isEmpty()) {
            hint.append(I18n.translate("devmod.radial.help.favorite_hint").getString());
        }

        return hint.toString();
    }

    private String buildItemKeyLine() {
        String itemKeys = formatKeyRange(config.input.itemKeys,
            new RadialMenuConfig.InputBindings().itemKeys, "QWERTYUIOP");
        return I18n.translate("devmod.radial.help.items", itemKeys).getString();
    }

    private String buildProfileLine() {
        String profileKey = keyName(config.input.keyProfileCycle);
        String safeKey = keyName(config.input.keySafeModeToggle);
        String safeLabel = config.safeMode
            ? I18n.translate("devmod.radial.safe.on").getString()
            : I18n.translate("devmod.radial.safe.off").getString();
        RadialMenuConfig.MenuProfile profile = config.menuProfile == RadialMenuConfig.MenuProfile.FOCUSED
            ? RadialMenuConfig.MenuProfile.PLAYER
            : config.menuProfile;
        String profileLabel = I18n.translate("devmod.radial.profile."
            + profile.name().toLowerCase(Locale.ROOT)).getString();
        return I18n.translate("devmod.radial.help.profile", profileLabel, profileKey, safeLabel, safeKey).getString();
    }

    private String buildQuickActionsLine() {
        if (quickActions.isEmpty()) {
            return I18n.translate("devmod.radial.help.quick_empty").getString();
        }
        StringBuilder line = new StringBuilder(I18n.translate("devmod.radial.help.quick_prefix").getString());
        int added = 0;
        for (int i = 0; i < quickActions.size(); i++) {
            PinnedItem pinned = quickActions.get(i);
            com.devmod.actions.RadialAction action = ActionRegistry.getAction(pinned.actionId);
            if (action == null) {
                continue;
            }
            if (!isItemAllowedByProfile(pinned.item) || !pinned.item.isVisible()) {
                continue;
            }
            if (added > 0) {
                line.append(" \u00A78| ");
            }
            String key = i < config.input.macroKeys.length ? keyName(config.input.macroKeys[i]) : String.valueOf(i + 1);
            String name = truncateLabel(action.getLabel().getString(), 14);
            line.append(I18n.translate("devmod.radial.help.quick_item", key, name).getString());
            added++;
        }
        return added == 0 ? "" : line.toString();
    }

    private String buildRecentLine() {
        if (recentActionIds.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (String id : recentActionIds) {
            com.devmod.actions.RadialAction action = ActionRegistry.getAction(id);
            if (action == null) {
                continue;
            }
            String name = action.getLabel().getString();
            names.add(truncateLabel(name, 18));
            if (names.size() >= RadialMenuConstants.MAX_RECENT_ACTIONS) {
                break;
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        return I18n.translate("devmod.radial.help.recent", String.join(" \u00A78| \u00A7f", names)).getString();
    }

    private static String truncateLabel(String label, int maxLength) {
        if (label == null) {
            return "";
        }
        if (label.length() <= maxLength) {
            return label;
        }
        return label.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String formatKeyRange(int[] keys, int[] defaults, String defaultRange) {
        if (Arrays.equals(keys, defaults)) {
            return defaultRange;
        }
        String list = formatKeyList(keys, 20);
        return list.isEmpty() ? I18n.translate("devmod.radial.help.custom").getString() : list;
    }

    private static String formatKeyList(int[] keys, int maxChars) {
        if (keys == null || keys.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(keyName(keys[i]));
            if (builder.length() > maxChars) {
                return "";
            }
        }
        return builder.toString();
    }

    private static String keyName(int keyCode) {
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        if (name != null && !name.isBlank()) {
            return name.toUpperCase(Locale.ROOT);
        }
        return "KEY_" + keyCode;
    }
}
