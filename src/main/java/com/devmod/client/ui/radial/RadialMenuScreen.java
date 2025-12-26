package com.devmod.client.ui.radial;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.ActionResult;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.overlay.OnboardingOverlay;
import com.devmod.client.telemetry.UiTelemetry;
import com.devmod.client.ui.radial.animation.RadialAnimator;
import com.devmod.client.ui.radial.config.RadialMenuConstants;
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

    // === Usage Statistics ===
    private final Map<String, Integer> usageStats = new HashMap<>();

    // === Edit Mode ===
    private boolean editMode = false;

    // === Cached Entity ===
    @Nullable
    private net.minecraft.world.entity.Entity cachedTargetEntity = null;

    public RadialMenuScreen() {
        super(java.util.Objects.requireNonNull(Component.translatable("devmod.radial.title"), "title"));
        loadConfig();
        initializeCategories();
        loadUsageStats();
        loadFavorites();
        openTime = System.currentTimeMillis();
        cacheTargetEntity();
        // Removed: particles, gestures, context analysis for cleaner UX
    }

    private void loadConfig() {
        config.load();
        innerRadius = config.innerRadius;
        outerRadius = config.outerRadius;
        itemRadius = config.itemRadius;
        centerButtonRadius = config.centerButtonRadius;
        favoritesRadius = innerRadius - RadialMenuConstants.FAVORITES_OFFSET;
        macroHubRadius = centerButtonRadius + RadialMenuConstants.MACRO_HUB_OFFSET;
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
        searchResults = RadialSearchHandler.search(searchQuery.toString(), rootCategories);
        selectedSearchResult = searchResults.isEmpty() ? RadialMenuConstants.NO_SELECTION : 0;
    }

    // ================================================================
    // USAGE STATISTICS
    // ================================================================

    private void loadUsageStats() {
        // In production, load from config file
        // For now, initialize empty
    }

    private void recordUsage(RadialMenuItem item) {
        String key = item.getName();
        usageStats.put(key, usageStats.getOrDefault(key, 0) + 1);
        // In production, save to config file
    }

    // ================================================================
    // FAVORITES SYSTEM
    // ================================================================

    private void loadFavorites() {
        // Would load from config in production
        // Animator handles favorite animations internally
    }

    private void toggleFavorite(RadialMenuItem item, RadialCategory category) {
        String key = category.getId() + ":" + item.getName();

        // Check if already favorite
        for (int i = 0; i < favorites.size(); i++) {
            if (favorites.get(i).key.equals(key)) {
                favorites.remove(i);
                playSound(RadialMenuConstants.SOUND_PITCH_FAVORITE_REMOVE);
                return;
            }
        }

        // Add to favorites
        if (favorites.size() < RadialMenuConstants.MAX_FAVORITES) {
            favorites.add(new FavoriteItem(key, item, category));
            playSound(RadialMenuConstants.SOUND_PITCH_FAVORITE_ADD);
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
        // Build categories from registry with mob editor supplier for context-dependent items
        Map<MacroCategory, List<RadialCategory>> registryCategories =
            RadialMenuRegistry.createDefaultCategories(this::createMobEditorItem);

        // Copy to our internal map and rootCategories
        macroCategoryMap.putAll(registryCategories);

        // Populate rootCategories in correct order (all categories flat for search)
        for (MacroCategory macro : MacroCategory.values()) {
            rootCategories.addAll(macroCategoryMap.get(macro));
        }

        // Animator handles animation arrays internally with proper sizing
    }

    private RadialMenuItem createMobEditorItem() {
        ItemStack leadStack = Objects.requireNonNull(Items.LEAD, "lead").getDefaultInstance();
        return new MobEditorItem(this, leadStack.copy());
    }

    private String getMobEditorDescription() {
        if (cachedTargetEntity instanceof net.minecraft.world.entity.Mob mob) {
            return "Edit: " + mob.getName().getString();
        }
        return "§cLook at a mob first, then open menu";
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
        // Track screen open for telemetry
        UiTelemetry.screenOpened("radial", "radial_menu");

        centerX = width / 2;
        centerY = height / 2;
        OnboardingOverlay.onRadialMenuOpened();

        // Reset telemetry tracking for this session
        openTime = System.currentTimeMillis();
        firstActionExecuted = false;
        actionsExecutedCount = 0;

        // Log menu opened event
        logMenuOpened();
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
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

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
        // Delegate to centralized animator
        animator.update(partialTick, config.enableAnimations);

        // Update search box animation
        animator.updateSearchBoxAnimation(searchMode);

        // Update category/item/favorite selection animations
        List<RadialCategory> categories = getActiveCategories();
        animator.updateCategoryAnimations(selectedCategoryIndex, categories.size());

        RadialCategory itemCategory = getActiveItemCategory();
        if (itemCategory != null) {
            animator.updateItemAnimations(selectedItemIndex, itemCategory.getVisibleItemCount());
        }

        animator.updateFavoriteAnimations(selectedFavoriteIndex, favorites.size());
    }

    // ================================================================
    // SELECTION
    // ================================================================

    private void updateSelection(int mouseX, int mouseY) {
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
        if (!favorites.isEmpty() && distance >= macroHubRadius && distance < innerRadius) {
            int numFavorites = favorites.size();
            double favSegmentAngle = RadialMenuConstants.TWO_PI / numFavorites;
            double favStartOffset = RadialMenuConstants.CATEGORY_START_OFFSET;

            double favAdjustedAngle = angle - favStartOffset;
            if (favAdjustedAngle < 0) favAdjustedAngle += RadialMenuConstants.TWO_PI;

            selectedFavoriteIndex = (int)(favAdjustedAngle / favSegmentAngle) % numFavorites;

            // Play sound on favorite hover change
            if (config.enableSounds && selectedFavoriteIndex != prevSelectedFavorite && selectedFavoriteIndex >= 0) {
                playSound(RadialMenuConstants.SOUND_PITCH_HOVER, RadialMenuConstants.SOUND_VOLUME_HOVER);
            }
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
        // Use getVisibleItems() to only consider visible items for selection
        if (distance > outerRadius && selectedCategoryIndex >= 0 && selectedCategoryIndex < categories.size()) {
            RadialCategory itemCategory = inSubcategory ? currentCategory : categories.get(selectedCategoryIndex);
            int numVisibleItems = itemCategory != null ? itemCategory.getVisibleItemCount() : 0;
            if (numVisibleItems > 0) {
                double itemSegment = segmentAngle / numVisibleItems;
                double catStartAngle = startOffset + selectedCategoryIndex * segmentAngle;
                double relativeAngle = angle - catStartAngle;
                if (relativeAngle < 0) relativeAngle += RadialMenuConstants.TWO_PI;
                if (relativeAngle > Math.PI) relativeAngle -= RadialMenuConstants.TWO_PI;

                selectedItemIndex = Mth.clamp((int)(relativeAngle / itemSegment), 0, numVisibleItems - 1);
            }
        }

        // Play selection sound on category change
        if (config.enableSounds && selectedCategoryIndex != prevSelectedCategory && selectedCategoryIndex >= 0) {
            playSound(RadialMenuConstants.SOUND_PITCH_CATEGORY_CHANGE,
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
            return Collections.emptyList();
        }
        List<RadialCategory> categories = macroCategoryMap.getOrDefault(macro, Collections.emptyList());
        if (categories.isEmpty()) {
            return categories;
        }
        List<RadialCategory> visible = new ArrayList<>();
        for (RadialCategory category : categories) {
            if (category.getVisibleItemCount() > 0) {
                visible.add(category);
            }
        }
        return visible;
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
        if (favorites.isEmpty()) return;

        @Nonnull Font safeFont = requireFont();
        int numFavorites = favorites.size();
        double segmentAngle = RadialMenuConstants.TWO_PI / numFavorites;
        double startOffset = RadialMenuConstants.CATEGORY_START_OFFSET;

        for (int i = 0; i < numFavorites; i++) {
            FavoriteItem fav = favorites.get(i);
            boolean selected = (i == selectedFavoriteIndex);
            float anim = animator.getFavoriteAnimation(i);

            double midAngle = startOffset + i * segmentAngle;
            int favX = (int)(centerX + Math.cos(midAngle) * favoritesRadius);
            int favY = (int)(centerY + Math.sin(midAngle) * favoritesRadius);

            int size = RadialMenuConstants.FAVORITE_BASE_SIZE +
                (int) (RadialMenuConstants.FAVORITE_SIZE_BONUS * anim);

            // Star background
            int bgColor = selected
                ? RadialMenuConstants.FAVORITE_BG_SELECTED
                : RadialMenuConstants.FAVORITE_BG_UNSELECTED;
            RadialGeometry.renderCircle(graphics, favX, favY, size, bgColor);

            // Icon
            ItemStack iconStack = Objects.requireNonNullElse(fav.item.getIconStack(), ItemStack.EMPTY);
            if (!iconStack.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(
                    favX + RadialMenuConstants.FAVORITE_ICON_OFFSET_X,
                    favY + RadialMenuConstants.FAVORITE_ICON_OFFSET_Y,
                    0);
                graphics.pose().scale(RadialMenuConstants.FAVORITE_ICON_SCALE,
                    RadialMenuConstants.FAVORITE_ICON_SCALE, 1f);
                graphics.renderItem(iconStack, 0, 0);
                graphics.pose().popPose();
            } else {
                graphics.drawCenteredString(safeFont, "*", favX,
                    favY + RadialMenuConstants.FAVORITE_STAR_OFFSET_Y,
                    RadialMenuConstants.FAVORITE_STAR_COLOR);
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
            .map(r -> new RadialTooltipRenderer.SearchResultDisplay(
                Objects.requireNonNullElse(r.getItem().getIconStack(), ItemStack.EMPTY),
                r.getItem().getName(),
                r.getCategory().getName(),
                r.getItem().isToggle(),
                r.getItem().isActive(),
                r.getItem().canExecute()
            ))
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
        animator.updateCenterHoverAnimation(centerHovered);

        // Update segment animations via animator
        animator.updateMacroSegmentAnimations(selectedMacro.ordinal(),
            hoveredMacro != null ? hoveredMacro.ordinal() : RadialMenuConstants.NO_SELECTION);

        // Build hub state and render
        boolean inSubcategory = currentCategory != null && currentCategory.hasParent();
        RadialHubRenderer.HubState hubState = new RadialHubRenderer.HubState(
            centerX, centerY, centerButtonRadius, macroHubRadius,
            selectedMacro, hoveredMacro, animator.getMacroSegmentAnimations(),
            animator.getCenterHoverAnimation(), searchMode, inSubcategory
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
    }

    private void renderCategoryItems(GuiGraphics graphics, RadialCategory category) {
        List<RadialCategory> categories = getActiveCategories();

        RadialCategoryRenderer.ItemsConfig itemsConfig = new RadialCategoryRenderer.ItemsConfig(
            centerX, centerY, outerRadius,
            selectedCategoryIndex, selectedItemIndex,
            animator.getItemAnimations(), config.theme
        );

        @Nonnull Font safeFont = requireFont();
        RadialCategoryRenderer.renderCategoryItems(graphics, safeFont, category, categories, itemsConfig);
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

        String tooltip = RadialTooltipRenderer.generateTooltip(
            hoveredMacro, selectedMacro,
            selectedFavoriteIndex, favoriteRefs,
            selectedCategoryIndex, selectedItemIndex,
            getActiveCategories(), getActiveItemCategory(),
            centerHovered, macroHubHovered, editMode
        );

        if (tooltip != null) {
            RadialTooltipRenderer.TooltipContext context = new RadialTooltipRenderer.TooltipContext(
                centerX, centerY, outerRadius, config.theme
            );
            @Nonnull Font safeFont = requireFont();
            RadialTooltipRenderer.renderTooltip(graphics, safeFont, tooltip, context);
        }
    }

    private void renderHelpText(GuiGraphics graphics) {
        if (!config.showKeyHints) return;
        @Nonnull Font safeFont = requireFont();
        RadialTooltipRenderer.renderHelpText(graphics, safeFont, width, height,
            selectedMacro, searchMode, openTime, config.theme);
    }

    private void renderBreadcrumb(GuiGraphics graphics) {
        @Nonnull Font safeFont = requireFont();
        RadialTooltipRenderer.renderBreadcrumb(graphics, safeFont,
            new ArrayList<>(navigationStack), currentCategory);
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
        if (button == 0) {
            primaryMouseDown = true;
            primaryMouseDownAt = System.currentTimeMillis();
            return true;
        } else if (button == 1 && config.rightClickToEdit) {
            if (selectedItemIndex >= 0) {
                openItemEditor();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseReleased(mouseX, mouseY, button);
        }

        long now = System.currentTimeMillis();
        boolean wasHeld = primaryMouseDown && (now - primaryMouseDownAt) >= RadialMenuConstants.LONG_PRESS_DURATION_MS;
        primaryMouseDown = false;

        if (wasHeld) {
            return handleLongPress(mouseX, mouseY);
        }

        // Alcuni trackpad su macOS inviano solo mouseReleased: ripeti la logica del click primario.
        return handlePrimaryClick(hasShiftDown(), mouseX, mouseY);
    }

    private boolean handlePrimaryClick(boolean shiftHeld, double mouseX, double mouseY) {
        // Check favorites first
        if (selectedFavoriteIndex >= 0 && selectedFavoriteIndex < favorites.size()) {
            FavoriteItem fav = favorites.get(selectedFavoriteIndex);
            executeItem(fav.item, fav.category);
            return true;
        }

        // Shift+click to toggle favorite
        if (shiftHeld && selectedItemIndex >= 0 && selectedCategoryIndex >= 0) {
            RadialCategory itemCategory = getActiveItemCategory();
            if (itemCategory != null) {
                List<RadialMenuItem> visibleItems = itemCategory.getVisibleItems();
                if (selectedItemIndex < visibleItems.size()) {
                    RadialMenuItem item = visibleItems.get(selectedItemIndex);
                    toggleFavorite(item, itemCategory);
                }
            }
            return true;
        }

        if (editMode && selectedItemIndex >= 0) {
            openItemEditor();
        } else {
            activateSelection(mouseX, mouseY);
        }
        return true;
    }

    private boolean handleLongPress(double ignoredMouseX, double ignoredMouseY) {
        if (selectedFavoriteIndex >= 0 && selectedFavoriteIndex < favorites.size()) {
            FavoriteItem fav = favorites.get(selectedFavoriteIndex);
            openItemDetails(fav.item);
            return true;
        }

        RadialCategory itemCategory = getActiveItemCategory();
        if (itemCategory != null) {
            List<RadialMenuItem> visibleItems = itemCategory.getVisibleItems();
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
        if (searchMode && !searchResults.isEmpty()) {
            if (scrollY > 0) {
                selectedSearchResult = Math.max(0, selectedSearchResult - 1);
            } else {
                selectedSearchResult = Math.min(searchResults.size() - 1, selectedSearchResult + 1);
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
        if (keyCode == config.input.keyReleaseSelect && config.releaseToSelect) {
                activateSelection(lastMouseX, lastMouseY);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
                executeItem(result.getItem(), result.getCategory());
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
            if (!searchMode) {
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
            return true;
        }

        // Number keys switch macro-categories (defaults: GLFW_KEY_1 .. GLFW_KEY_6)
        int macroIndex = indexOfKey(keyCode, config.input.macroKeys);
        if (macroIndex != RadialMenuConstants.NO_SELECTION) {
            MacroCategory[] macros = MacroCategory.values();
            if (macroIndex < macros.length && macros[macroIndex] != selectedMacro) {
                transitionFromMacro = selectedMacro;
                selectedMacro = macros[macroIndex];
                animator.startMacroTransition();
                selectedCategoryIndex = RadialMenuConstants.NO_SELECTION; // selectedCategoryIndex = -1 (reset on macro change)
                selectedItemIndex = RadialMenuConstants.NO_SELECTION;
                clearSubcategory();
                playSound(RadialMenuConstants.SOUND_PITCH_MACRO_SWITCH);
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
            }
            return true;
        }

        // Left/Right arrows for categories
        if (keyCode == config.input.keyCategoryLeft) {
            prevCategory();
            return true;
        }
        if (keyCode == config.input.keyCategoryRight) {
            nextCategory();
            return true;
        }

        RadialCategory cat = getActiveItemCategory();
        if (cat != null) {
            List<RadialMenuItem> visibleItems = cat.getVisibleItems();
            int itemNum = getItemKeyIndex(keyCode);

            if (itemNum >= 0 && itemNum < visibleItems.size()) {
                if (editMode) {
                    selectedItemIndex = itemNum;
                    openItemEditor();
                } else {
                    executeItem(visibleItems.get(itemNum), cat);
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
            executeItem(fav.item, fav.category);
            return;
        }

        if (selectedCategoryIndex >= 0 && selectedCategoryIndex < getActiveCategories().size()) {
            RadialCategory cat = getActiveItemCategory();
            if (cat == null) {
                return;
            }
            List<RadialMenuItem> visibleItems = cat.getVisibleItems();
            if (selectedItemIndex >= 0 && selectedItemIndex < visibleItems.size()) {
                RadialMenuItem item = visibleItems.get(selectedItemIndex);

                if (item.isSubcategoryLink()) {
                    RadialCategory subcategory = item.getLinkedSubcategory();
                    if (subcategory != null) {
                        navigateTo(subcategory);
                        return;
                    }
                }

                executeItem(item, cat);
            }
        }
    }

    private void executeItem(RadialMenuItem item, RadialCategory ignoredCategory) {
        // Track time to first action
        if (!firstActionExecuted) {
            firstActionExecuted = true;
            long timeToFirstAction = System.currentTimeMillis() - openTime;
            logTimeToFirstAction(timeToFirstAction);
        }
        actionsExecutedCount++;

        boolean executed = true;
        String actionId = item.getAction().getRegistryId();
        if (actionId != null) {
            com.devmod.actions.RadialAction action = ActionRegistry.getAction(actionId);
            if (action != null && action.requiresConfirm()) {
                openItemDetails(item);
                return;
            }
            ActionResult result = ActionRegistry.invokeWithResult(actionId, ClientActionContexts.forRadial());
            executed = result.isSuccess();
        } else {
            item.execute();
        }

        if (!executed) {
            return;
        }

        recordUsage(item);

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

        if (config.enableSounds) {
            float pitch = item.isToggle()
                ? (item.isActive()
                    ? RadialMenuConstants.SOUND_PITCH_TOGGLE_ON
                    : RadialMenuConstants.SOUND_PITCH_TOGGLE_OFF)
                : RadialMenuConstants.SOUND_PITCH_ACTION_DEFAULT;
            playSound(pitch);
        }

        if (config.closeOnToggle && !item.isToggle()) {
            animator.startClose();
        }
    }

    private void openItemDetails(RadialMenuItem item) {
        Minecraft.getInstance().setScreen(new RadialActionDetailScreen(this, item));
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

    private void openItemEditor() {
        if (selectedCategoryIndex >= 0 && selectedItemIndex >= 0) {
            RadialCategory cat = getActiveItemCategory();
            if (cat != null) {
                List<RadialMenuItem> visibleItems = cat.getVisibleItems();
                if (selectedItemIndex < visibleItems.size()) {
                    RadialMenuItem item = visibleItems.get(selectedItemIndex);
                    showMessage(Minecraft.getInstance(),
                        "§6[Edit] " + item.getName() + " - Shift+Click to toggle * favorite");
                }
            }
        }
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

    private void showMessage(Minecraft mc, @Nonnull String message) {
        var player = mc.player;
        if (player != null) {
            @Nonnull String safeMessage = Objects.requireNonNull(message, "message");
            player.displayClientMessage(
                Objects.requireNonNull(Component.literal(safeMessage), "messageComponent"),
                true);
        }
    }

    @Override
    public void onClose() {
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
        final RadialCategory category;

        FavoriteItem(String key, RadialMenuItem item, RadialCategory category) {
            this.key = key;
            this.item = item;
            this.category = category;
        }
    }
}
