package com.frenkvs.devmod.ui.radial;

import com.frenkvs.devmod.hud.OnboardingOverlay;
import com.frenkvs.devmod.ui.radial.animation.RadialAnimator;
import com.frenkvs.devmod.ui.radial.config.RadialMenuConstants;
import com.frenkvs.devmod.ui.radial.input.RadialSearchHandler;
import com.frenkvs.devmod.ui.radial.model.MacroCategory;
import com.frenkvs.devmod.ui.radial.render.RadialCategoryRenderer;
import com.frenkvs.devmod.ui.radial.render.RadialGeometry;
import com.frenkvs.devmod.ui.radial.render.RadialHubRenderer;
import com.frenkvs.devmod.ui.radial.render.RadialTooltipRenderer;
import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * RADIAL MENU V3 - MACRO-CATEGORY EDITION
 *
 * Architecture:
 * - Center hub divided into 4 macro-category segments (ANALYZE, COMBAT, TOOLS, PLAY)
 * - Each macro-category controls 6 categories in the outer ring
 * - Total capacity: 24 categories (4 macros × 6 categories)
 * - Clicking center segment switches active macro-category
 * - Smooth transitions between macro-categories
 * - State persisted across menu opens
 *
 * UX Features:
 * - Clean, readable layout with proper spacing
 * - Large clickable areas - no overlap
 * - Fast, non-distracting animations
 * - Fuzzy search (type to filter)
 * - Keyboard shortcuts for power users
 */

public class RadialMenuScreenV3 extends Screen {

    // ================================================================
    // MACRO-CATEGORY SYSTEM
    // ================================================================

    // MacroCategory enum extracted to com.frenkvs.devmod.ui.radial.model.MacroCategory

    // === Macro-Category State ===
    private static MacroCategory selectedMacro = MacroCategory.ANALYZE; // Persisted across opens
    private MacroCategory hoveredMacro = null;
    private final Map<MacroCategory, List<RadialCategory>> macroCategoryMap = new EnumMap<>(MacroCategory.class);
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
    private final Stack<RadialCategory> navigationStack = new Stack<>();
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

    // === Search System ===
    private boolean searchMode = false;
    private StringBuilder searchQuery = new StringBuilder();
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

    public RadialMenuScreenV3() {
        super(Component.translatable("devmod.radial.title"));
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
        selectedCategoryIndex = (selectedCategoryIndex + 1) % rootCategories.size();
        animator.startMorph();
        playSound(RadialMenuConstants.SOUND_PITCH_CATEGORY_NEXT);
    }

    private void prevCategory() {
        selectedCategoryIndex = (selectedCategoryIndex - 1 + rootCategories.size()) % rootCategories.size();
        animator.startMorph();
        playSound(RadialMenuConstants.SOUND_PITCH_CATEGORY_PREV);
    }

    // ================================================================
    // CATEGORY INITIALIZATION - MACRO-CATEGORY ARCHITECTURE
    // ================================================================

    /**
     * Initializes the 4 macro-categories, each containing 6 sub-categories.
     * Total: 24 categories organized hierarchically for better UX.
     *
     * Categories are defined in RadialMenuRegistry for better separation of concerns.
     *
     * ANALYZE (👁): Debug overlays, Spatial analysis, Performance, Light/Spawn, Pathfinding, Entity Debug
     * COMBAT (⚔): Combat Tools, Editors, Heatmaps, Boss/Skills, Economy, Attributes
     * TOOLS (🔧): Settings, Dashboard, Testing, Mob Editor, Weapon/Armor Editor, Commands
     * PLAY (📜): Quest System, Endurance Mode, Party/Multiplayer, Achievements, Challenges, Leaderboards
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
        return new RadialMenuItem("Mob Editor",
            RadialAction.custom("Mob Editor",
                getMobEditorDescription(),
                "🐾",
                new ItemStack(Items.LEAD),
                this::openMobEditor),
            "🐾",
            new ItemStack(Items.LEAD)) {
            @Override
            public boolean isActive() {
                return cachedTargetEntity instanceof net.minecraft.world.entity.Mob;
            }
        };
    }

    private String getMobEditorDescription() {
        if (cachedTargetEntity instanceof net.minecraft.world.entity.Mob mob) {
            return "Edit: " + mob.getName().getString();
        }
        return "§cLook at a mob first, then open menu";
    }

    private void openMobEditor() {
        animator.startClose();
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            if (cachedTargetEntity instanceof net.minecraft.world.entity.Mob mob && mob.isAlive()) {
                mc.setScreen(new com.frenkvs.devmod.MobConfigScreen(mob));
            } else {
                showMessage(mc, "§c" + I18n.translate("devmod.message.look_at_mob_first").getString());
                mc.setScreen(null);
            }
        });
    }

    // ================================================================
    // SCREEN LIFECYCLE
    // ================================================================

    @Override
    protected void init() {
        centerX = width / 2;
        centerY = height / 2;
        OnboardingOverlay.onRadialMenuOpened();
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

        if (selectedCategoryIndex >= 0 && selectedCategoryIndex < getActiveCategories().size()) {
            renderCategoryItems(graphics, getActiveCategories().get(selectedCategoryIndex));
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

        if (selectedCategoryIndex >= 0 && selectedCategoryIndex < categories.size()) {
            RadialCategory cat = categories.get(selectedCategoryIndex);
            animator.updateItemAnimations(selectedItemIndex, cat.getVisibleItemCount());
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

        // Macro hub area (center with 4 segments) - handled in renderCenterHub
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

        selectedCategoryIndex = (int)(adjustedAngle / segmentAngle) % numCategories;

        // Select item if mouse is beyond the outer ring
        // Use getVisibleItems() to only consider visible items for selection
        if (distance > outerRadius && selectedCategoryIndex >= 0 && selectedCategoryIndex < categories.size()) {
            RadialCategory cat = categories.get(selectedCategoryIndex);
            int numVisibleItems = cat.getVisibleItemCount();
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
        return macroCategoryMap.getOrDefault(selectedMacro, Collections.emptyList());
    }

    // ================================================================
    // RENDERING - FAVORITES RING
    // ================================================================

    private void renderFavoritesRing(GuiGraphics graphics) {
        if (favorites.isEmpty()) return;

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
            if (fav.item.getIconStack() != null) {
                graphics.pose().pushPose();
                graphics.pose().translate(
                    favX + RadialMenuConstants.FAVORITE_ICON_OFFSET_X,
                    favY + RadialMenuConstants.FAVORITE_ICON_OFFSET_Y,
                    0);
                graphics.pose().scale(RadialMenuConstants.FAVORITE_ICON_SCALE,
                    RadialMenuConstants.FAVORITE_ICON_SCALE, 1f);
                graphics.renderItem(fav.item.getIconStack(), 0, 0);
                graphics.pose().popPose();
            } else {
                graphics.drawCenteredString(font, "★", favX,
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

        RadialTooltipRenderer.SearchConfig searchConfig = new RadialTooltipRenderer.SearchConfig(
            width, height, centerX, config.theme
        );

        // Convert search results to display format
        List<RadialTooltipRenderer.SearchResultDisplay> displayResults = searchResults.stream()
            .map(r -> new RadialTooltipRenderer.SearchResultDisplay(
                r.getItem().getIconEmoji(),
                r.getItem().getName(),
                r.getCategory().getName(),
                r.getItem().isToggle(),
                r.getItem().isActive()
            ))
            .toList();

        RadialTooltipRenderer.renderSearchOverlay(graphics, font, searchConfig,
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
     * Renders the center hub with 4 macro-category segments.
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

        RadialHubRenderer.render(graphics, font, hubState);
    }

    private void renderCategories(GuiGraphics graphics) {
        // Draw ring borders
        RadialCategoryRenderer.renderRingBorders(graphics, centerX, centerY, innerRadius, outerRadius);

        // Build ring config
        RadialCategoryRenderer.RingConfig ringConfig = new RadialCategoryRenderer.RingConfig(
            centerX, centerY, innerRadius, outerRadius, itemRadius,
            selectedCategoryIndex, animator.getCategoryAnimations(), config.theme, config.iconMode, animator.getPulsePhase()
        );

        // Cross-fade transition: render outgoing categories (fading out) then incoming (fading in)
        boolean isTransitioning = animator.isTransitioning() && transitionFromMacro != null;

        if (isTransitioning) {
            // Render outgoing categories with decreasing alpha
            float outgoingAlpha = 1f - animator.getMacroTransitionProgress();
            List<RadialCategory> outgoingCategories = macroCategoryMap.getOrDefault(transitionFromMacro, Collections.emptyList());
            RadialCategoryRenderer.renderCategoryRing(graphics, font, outgoingCategories, ringConfig, outgoingAlpha, false);
        }

        // Render current (incoming) categories
        List<RadialCategory> categories = getActiveCategories();
        float incomingAlpha = isTransitioning ? animator.getMacroTransitionProgress() : 1f;
        RadialCategoryRenderer.renderCategoryRing(graphics, font, categories, ringConfig, incomingAlpha, true);
    }

    private void renderCategoryItems(GuiGraphics graphics, RadialCategory category) {
        List<RadialCategory> categories = getActiveCategories();

        RadialCategoryRenderer.ItemsConfig itemsConfig = new RadialCategoryRenderer.ItemsConfig(
            centerX, centerY, outerRadius,
            selectedCategoryIndex, selectedItemIndex,
            animator.getItemAnimations(), config.theme
        );

        RadialCategoryRenderer.renderCategoryItems(graphics, font, category, categories, itemsConfig);
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
            getActiveCategories(),
            centerHovered, macroHubHovered, editMode
        );

        if (tooltip != null) {
            RadialTooltipRenderer.TooltipContext context = new RadialTooltipRenderer.TooltipContext(
                centerX, centerY, outerRadius, config.theme
            );
            RadialTooltipRenderer.renderTooltip(graphics, font, tooltip, context);
        }
    }

    private void renderHelpText(GuiGraphics graphics) {
        if (!config.showKeyHints) return;
        RadialTooltipRenderer.renderHelpText(graphics, font, width, height,
            selectedMacro, searchMode, openTime, config.theme);
    }

    private void renderBreadcrumb(GuiGraphics graphics) {
        RadialTooltipRenderer.renderBreadcrumb(graphics, font,
            new ArrayList<>(navigationStack), currentCategory);
    }

    private void renderEditModeIndicator(GuiGraphics graphics) {
        RadialTooltipRenderer.renderEditModeIndicator(graphics, font, width);
    }

    private void renderThemeIndicator(GuiGraphics graphics) {
        RadialTooltipRenderer.renderThemeIndicator(graphics, font, width, config.theme.presetName, openTime);
    }

    // ================================================================
    // INPUT HANDLING
    // ================================================================

    private boolean primaryClickHandled = false;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean shiftHeld = hasShiftDown();

        if (button == 0) {
            primaryClickHandled = handlePrimaryClick(shiftHeld, mouseX, mouseY);
            return primaryClickHandled;
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
        try {
            if (button == 0 && !primaryClickHandled) {
                // Alcuni trackpad su macOS inviano solo mouseReleased: ripeti la logica del click primario.
                return handlePrimaryClick(hasShiftDown(), mouseX, mouseY);
            }
            return super.mouseReleased(mouseX, mouseY, button);
        } finally {
            primaryClickHandled = false;
        }
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
            RadialCategory cat = getActiveCategories().get(selectedCategoryIndex);
            List<RadialMenuItem> visibleItems = cat.getVisibleItems();
            if (selectedItemIndex < visibleItems.size()) {
                RadialMenuItem item = visibleItems.get(selectedItemIndex);
                toggleFavorite(item, cat);
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

        // Keys 1-4 switch macro-categories (defaults: GLFW_KEY_1 .. GLFW_KEY_4)
        int macroIndex = indexOfKey(keyCode, config.input.macroKeys);
        if (macroIndex != RadialMenuConstants.NO_SELECTION) {
            MacroCategory[] macros = MacroCategory.values();
            if (macroIndex < macros.length && macros[macroIndex] != selectedMacro) {
                transitionFromMacro = selectedMacro;
                selectedMacro = macros[macroIndex];
                animator.startMacroTransition();
                selectedCategoryIndex = RadialMenuConstants.NO_SELECTION; // selectedCategoryIndex = -1 (reset on macro change)
                selectedItemIndex = RadialMenuConstants.NO_SELECTION;
                playSound(RadialMenuConstants.SOUND_PITCH_MACRO_SWITCH);
            }
            return true;
        }

        // Keys 5-9 and 0 select categories within current macro (defaults: GLFW_KEY_5 .. GLFW_KEY_9, GLFW_KEY_0)
        int categoryIndex = indexOfKey(keyCode, config.input.categoryKeys);
        if (categoryIndex != RadialMenuConstants.NO_SELECTION) {
            if (categoryIndex < getActiveCategories().size()) {
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

        if (selectedCategoryIndex >= 0) {
            RadialCategory cat = getActiveCategories().get(selectedCategoryIndex);
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
            RadialCategory cat = getActiveCategories().get(selectedCategoryIndex);
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

    private void executeItem(RadialMenuItem item, RadialCategory category) {
        item.execute();
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

    private void navigateTo(RadialCategory category) {
        if (currentCategory != null) {
            navigationStack.push(currentCategory);
        }
        currentCategory = category;
        selectedItemIndex = RadialMenuConstants.NO_SELECTION;
        playSound(RadialMenuConstants.SOUND_PITCH_NAVIGATE_TO);
    }

    private void navigateBack() {
        if (!navigationStack.isEmpty()) {
            currentCategory = navigationStack.pop();
        } else {
            currentCategory = null;
        }
        selectedItemIndex = RadialMenuConstants.NO_SELECTION;
        playSound(RadialMenuConstants.SOUND_PITCH_NAVIGATE_BACK);
    }

    private void openItemEditor() {
        if (selectedCategoryIndex >= 0 && selectedItemIndex >= 0) {
            RadialCategory cat = getActiveCategories().get(selectedCategoryIndex);
            List<RadialMenuItem> visibleItems = cat.getVisibleItems();
            if (selectedItemIndex < visibleItems.size()) {
                RadialMenuItem item = visibleItems.get(selectedItemIndex);
                showMessage(Minecraft.getInstance(),
                    "§6[Edit] " + item.getName() + " - Shift+Click to toggle ★ favorite");
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
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
        }
    }

    private void showMessage(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), true);
        }
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
