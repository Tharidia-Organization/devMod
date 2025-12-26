package com.devmod.client.ui.radial.animation;

import java.util.Arrays;
import java.util.Objects;

import net.minecraft.util.Mth;

import com.devmod.client.ui.radial.config.RadialMenuConstants;
public final class RadialAnimator {

    // ================================================================
    // ANIMATION STATE
    // ================================================================

    /** Open/close animation progress (0 = closed, 1 = fully open) */
    private float openAnimation = 0f;

    /** Whether menu is currently closing */
    private boolean closing = false;

    /** Macro category transition progress (0 = transitioning, 1 = complete) */
    private float macroTransitionProgress = 1f;

    /** Search box animation progress (0 = hidden, 1 = shown) */
    private float searchBoxAnimation = 0f;

    /** Category hover animations (one per category slot) */
    private final float[] categoryAnimations;

    /** Item hover animations (one per item slot) */
    private final float[] itemAnimations;

    /** Favorite item animations */
    private final float[] favoriteAnimations;

    /** Macro segment animations (one per macro category) */
    private final float[] macroSegmentAnimations;

    /** Center button hover animation */
    private float centerHoverAnimation = 0f;

    /** Pulse phase for badges and effects */
    private float pulsePhase = 0f;

    /** Wave phase for staggered animations */
    private float wavePhase = 0f;

    /** Morph animation for category transitions */
    private float morphProgress = 1f;

    // ================================================================
    // CONFIGURATION
    // ================================================================

    private final AnimationConfig config;

    // ================================================================
    // CONSTRUCTOR
    // ================================================================

    /**
     * Creates a new RadialAnimator with default configuration.
     *
     * @param maxCategories maximum number of categories to animate
     * @param maxItems      maximum number of items per category to animate
     * @param maxFavorites  maximum number of favorites to animate
     */
    public RadialAnimator(int maxCategories, int maxItems, int maxFavorites) {
        this(maxCategories, maxItems, maxFavorites, AnimationConfig.DEFAULT);
    }

    /**
     * Creates a new RadialAnimator with custom configuration.
     *
     * @param maxCategories maximum number of categories to animate
     * @param maxItems      maximum number of items per category to animate
     * @param maxFavorites  maximum number of favorites to animate
     * @param config        animation configuration
     */
    public RadialAnimator(int maxCategories, int maxItems, int maxFavorites, AnimationConfig config) {
        this.categoryAnimations = new float[maxCategories];
        this.itemAnimations = new float[maxItems];
        this.favoriteAnimations = new float[maxFavorites];
        this.macroSegmentAnimations = new float[RadialMenuConstants.MACRO_COUNT];
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    // ================================================================
    // ANIMATION CONFIGURATION
    // ================================================================

    /**
     * Configuration for animation speeds and behavior.
     *
     * @param openSpeed         speed for opening animation
     * @param closeSpeed        speed for closing animation
     * @param transitionSpeed   speed for macro transitions
     * @param hoverInSpeed      speed for hover-in animations
     * @param hoverOutSpeed     speed for hover-out animations
     * @param morphSpeed        speed for morph animations
     * @param searchBoxSpeed    speed for search box animation
     * @param enabled           whether animations are enabled
     */
    public record AnimationConfig(
        float openSpeed,
        float closeSpeed,
        float transitionSpeed,
        float hoverInSpeed,
        float hoverOutSpeed,
        float morphSpeed,
        float searchBoxSpeed,
        boolean enabled
    ) {
        public static final AnimationConfig DEFAULT = new AnimationConfig(
            RadialMenuConstants.OPEN_ANIM_SPEED,
            RadialMenuConstants.CLOSE_ANIM_SPEED,
            RadialMenuConstants.TRANSITION_SPEED,
            RadialMenuConstants.HOVER_ANIM_IN,
            RadialMenuConstants.HOVER_ANIM_OUT,
            RadialMenuConstants.MORPH_SPEED,
            RadialMenuConstants.SEARCH_BOX_LERP,
            true
        );

        public static final AnimationConfig INSTANT = new AnimationConfig(
            1f, 1f, 1f, 1f, 1f, 1f, 1f, false
        );
    }

    // ================================================================
    // UPDATE METHODS
    // ================================================================

    /**
     * Updates all animations. Call this once per render tick.
     *
     * @param partialTick partial tick for smooth animation
     * @param animationsEnabled whether animations are globally enabled
     */
    public void update(float partialTick, boolean animationsEnabled) {
        if (!animationsEnabled || !config.enabled) {
            // Snap to target states
            openAnimation = closing ? 0f : 1f;
            macroTransitionProgress = 1f;
            morphProgress = 1f;
            return;
        }

        float delta = partialTick * RadialMenuConstants.ANIMATION_TIME_SCALE;

        // Open/close animation
        float targetAnim = closing ? 0f : 1f;
        float animSpeed = closing ? config.closeSpeed : config.openSpeed;
        openAnimation = Mth.lerp(animSpeed, openAnimation, targetAnim);

        // Macro transition
        if (macroTransitionProgress < 1f) {
            macroTransitionProgress = Math.min(1f, macroTransitionProgress + config.transitionSpeed);
        }

        // Morph animation
        if (morphProgress < 1f) {
            morphProgress = Math.min(1f, morphProgress + config.morphSpeed);
        }

        // Pulse and wave phases
        pulsePhase += delta * RadialMenuConstants.PULSE_PHASE_SPEED;
        wavePhase += delta * RadialMenuConstants.WAVE_PHASE_SPEED;
        if (pulsePhase > RadialMenuConstants.TWO_PI) pulsePhase -= (float) RadialMenuConstants.TWO_PI;
        if (wavePhase > RadialMenuConstants.TWO_PI) wavePhase -= (float) RadialMenuConstants.TWO_PI;
    }

    /**
     * Updates category selection animations.
     *
     * @param selectedIndex currently selected category index (-1 for none)
     * @param categoryCount total number of categories
     */
    public void updateCategoryAnimations(int selectedIndex, int categoryCount) {
        for (int i = 0; i < Math.min(categoryCount, categoryAnimations.length); i++) {
            float target = (i == selectedIndex) ? 1f : 0f;
            categoryAnimations[i] = Mth.lerp(RadialMenuConstants.CATEGORY_ANIM_LERP,
                categoryAnimations[i], target);
        }
    }

    /**
     * Updates item hover animations.
     *
     * @param selectedIndex currently selected item index (-1 for none)
     * @param itemCount     total number of items
     */
    public void updateItemAnimations(int selectedIndex, int itemCount) {
        for (int i = 0; i < Math.min(itemCount, itemAnimations.length); i++) {
            float target = (i == selectedIndex) ? 1f : 0f;
            itemAnimations[i] = Mth.lerp(RadialMenuConstants.ITEM_ANIM_LERP,
                itemAnimations[i], target);
        }
    }

    /**
     * Updates favorite item animations.
     *
     * @param selectedIndex currently selected favorite index (-1 for none)
     * @param favoriteCount total number of favorites
     */
    public void updateFavoriteAnimations(int selectedIndex, int favoriteCount) {
        for (int i = 0; i < Math.min(favoriteCount, favoriteAnimations.length); i++) {
            float target = (i == selectedIndex) ? 1f : 0f;
            favoriteAnimations[i] = Mth.lerp(RadialMenuConstants.ITEM_ANIM_LERP,
                favoriteAnimations[i], target);
        }
    }

    /**
     * Updates macro segment animations.
     *
     * @param selectedIndex currently selected macro index
     * @param hoveredIndex  currently hovered macro index (-1 for none)
     */
    public void updateMacroSegmentAnimations(int selectedIndex, int hoveredIndex) {
        for (int i = 0; i < macroSegmentAnimations.length; i++) {
            float target = (i == selectedIndex || i == hoveredIndex) ? 1f : 0f;
            macroSegmentAnimations[i] = Mth.lerp(RadialMenuConstants.LERP_FACTOR, macroSegmentAnimations[i], target);
        }
    }

    /**
     * Updates center button hover animation.
     *
     * @param hovered whether center is being hovered
     */
    public void updateCenterHoverAnimation(boolean hovered) {
        float target = hovered ? 1f : 0f;
        float speed = hovered ? config.hoverInSpeed : config.hoverOutSpeed;
        centerHoverAnimation = Mth.lerp(speed, centerHoverAnimation, target);
    }

    /**
     * Updates search box animation.
     *
     * @param searchModeActive whether search mode is active
     */
    public void updateSearchBoxAnimation(boolean searchModeActive) {
        float target = searchModeActive ? 1f : 0f;
        searchBoxAnimation = Mth.lerp(config.searchBoxSpeed, searchBoxAnimation, target);
    }

    // ================================================================
    // TRANSITION TRIGGERS
    // ================================================================

    /**
     * Starts the menu closing animation.
     */
    public void startClose() {
        closing = true;
    }

    /**
     * Starts a macro category transition.
     */
    public void startMacroTransition() {
        macroTransitionProgress = 0f;
        // Reset category animations during transition
        Arrays.fill(categoryAnimations, 0f);
    }

    /**
     * Starts a category morph animation.
     */
    public void startMorph() {
        morphProgress = 0f;
    }

    /**
     * Resets all animations to initial state.
     */
    public void reset() {
        openAnimation = 0f;
        closing = false;
        macroTransitionProgress = 1f;
        searchBoxAnimation = 0f;
        morphProgress = 1f;
        pulsePhase = 0f;
        wavePhase = 0f;
        centerHoverAnimation = 0f;
        Arrays.fill(categoryAnimations, 0f);
        Arrays.fill(itemAnimations, 0f);
        Arrays.fill(favoriteAnimations, 0f);
        Arrays.fill(macroSegmentAnimations, 0f);
    }

    // ================================================================
    // GETTERS
    // ================================================================

    public float getOpenAnimation() {
        return openAnimation;
    }

    public float getOpenAnimationEased() {
        return Easing.easeOutQuad(openAnimation);
    }

    public boolean isClosing() {
        return closing;
    }

    public boolean isFullyClosed() {
        return closing && openAnimation < RadialMenuConstants.FULLY_CLOSED_THRESHOLD;
    }

    public float getMacroTransitionProgress() {
        return macroTransitionProgress;
    }

    public boolean isTransitioning() {
        return macroTransitionProgress < 1f;
    }

    public float getSearchBoxAnimation() {
        return searchBoxAnimation;
    }

    public float getMorphProgress() {
        return morphProgress;
    }

    public float getPulsePhase() {
        return pulsePhase;
    }

    public float getWavePhase() {
        return wavePhase;
    }

    public float getCenterHoverAnimation() {
        return centerHoverAnimation;
    }

    public float[] getCategoryAnimations() {
        return categoryAnimations;
    }

    public float getCategoryAnimation(int index) {
        return index >= 0 && index < categoryAnimations.length ? categoryAnimations[index] : 0f;
    }

    public float[] getItemAnimations() {
        return itemAnimations;
    }

    public float getItemAnimation(int index) {
        return index >= 0 && index < itemAnimations.length ? itemAnimations[index] : 0f;
    }

    public float[] getFavoriteAnimations() {
        return favoriteAnimations;
    }

    public float getFavoriteAnimation(int index) {
        return index >= 0 && index < favoriteAnimations.length ? favoriteAnimations[index] : 0f;
    }

    public float[] getMacroSegmentAnimations() {
        return macroSegmentAnimations;
    }

    public float getMacroSegmentAnimation(int index) {
        return index >= 0 && index < macroSegmentAnimations.length ? macroSegmentAnimations[index] : 0f;
    }

    // ================================================================
    // UTILITY METHODS
    // ================================================================

    /**
     * Calculates a pulsing value for visual effects.
     *
     * @param baseValue base value
     * @param amplitude pulse amplitude
     * @return pulsed value
     */
    public float pulse(float baseValue, float amplitude) {
        return baseValue + amplitude *
            (float) Math.sin(pulsePhase * RadialMenuConstants.PULSE_PHASE_MULTIPLIER);
    }

    /**
     * Calculates a staggered animation value based on index.
     *
     * @param index    element index
     * @param progress base progress
     * @param stagger  stagger amount per element
     * @return staggered progress (clamped to 0-1)
     */
    public static float stagger(int index, float progress, float stagger) {
        float staggeredProgress = progress - (index * stagger);
        return Easing.clamp01(staggeredProgress / (1 - stagger * index));
    }
}
