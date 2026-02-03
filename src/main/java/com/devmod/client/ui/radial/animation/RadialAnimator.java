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
    // FEEDBACK ANIMATION STATE (shake, flash)
    // ================================================================

    /** Which type of element is being animated */
    public enum TargetType { NONE, ITEM, FAVORITE, CATEGORY }

    /** Type of flash effect */
    public enum FlashType { NONE, BLOCKED, SUCCESS, FAILED }

    /** Shake progress (1 = start, decays to 0) */
    private float shakeProgress = 0f;

    /** Shake target index (-1 for none) */
    private int shakeTargetIndex = -1;

    /** Shake target type */
    private TargetType shakeTargetType = TargetType.NONE;

    /** Flash progress (1 = start, decays to 0) */
    private float flashProgress = 0f;

    /** Flash target index (-1 for none) */
    private int flashTargetIndex = -1;

    /** Flash target type */
    private TargetType flashTargetType = TargetType.NONE;

    /** Current flash type */
    private FlashType flashType = FlashType.NONE;

    /** Shake decay speed (how fast shake fades) */
    private static final float SHAKE_DECAY = 0.25f;

    /** Flash decay speed (how fast flash fades) */
    private static final float FLASH_DECAY = 0.08f;

    /** Shake amplitude in pixels */
    private static final float SHAKE_AMPLITUDE = 3f;

    /** Shake frequency (oscillations during decay) */
    private static final float SHAKE_FREQUENCY = 25f;

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
            pulsePhase = 0f;
            wavePhase = 0f;
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

        // Shake animation decay
        if (shakeProgress > 0f) {
            shakeProgress = Math.max(0f, shakeProgress - SHAKE_DECAY);
            if (shakeProgress <= 0f) {
                shakeTargetIndex = -1;
                shakeTargetType = TargetType.NONE;
            }
        }

        // Flash animation decay
        if (flashProgress > 0f) {
            flashProgress = Math.max(0f, flashProgress - FLASH_DECAY);
            if (flashProgress <= 0f) {
                flashTargetIndex = -1;
                flashTargetType = TargetType.NONE;
                flashType = FlashType.NONE;
            }
        }
    }

    /**
     * Updates category selection animations.
     *
     * @param selectedIndex currently selected category index (-1 for none)
     * @param categoryCount total number of categories
     */
    public void updateCategoryAnimations(int selectedIndex, int categoryCount, boolean animate) {
        for (int i = 0; i < Math.min(categoryCount, categoryAnimations.length); i++) {
            float target = (i == selectedIndex) ? 1f : 0f;
            if (animate) {
                categoryAnimations[i] = Mth.lerp(RadialMenuConstants.CATEGORY_ANIM_LERP,
                    categoryAnimations[i], target);
            } else {
                categoryAnimations[i] = target;
            }
        }
    }

    /**
     * Updates item hover animations.
     *
     * @param selectedIndex currently selected item index (-1 for none)
     * @param itemCount     total number of items
     */
    public void updateItemAnimations(int selectedIndex, int itemCount, boolean animate) {
        for (int i = 0; i < Math.min(itemCount, itemAnimations.length); i++) {
            float target = (i == selectedIndex) ? 1f : 0f;
            if (animate) {
                itemAnimations[i] = Mth.lerp(RadialMenuConstants.ITEM_ANIM_LERP,
                    itemAnimations[i], target);
            } else {
                itemAnimations[i] = target;
            }
        }
    }

    /**
     * Updates favorite item animations.
     *
     * @param selectedIndex currently selected favorite index (-1 for none)
     * @param favoriteCount total number of favorites
     */
    public void updateFavoriteAnimations(int selectedIndex, int favoriteCount, boolean animate) {
        for (int i = 0; i < Math.min(favoriteCount, favoriteAnimations.length); i++) {
            float target = (i == selectedIndex) ? 1f : 0f;
            if (animate) {
                favoriteAnimations[i] = Mth.lerp(RadialMenuConstants.ITEM_ANIM_LERP,
                    favoriteAnimations[i], target);
            } else {
                favoriteAnimations[i] = target;
            }
        }
    }

    /**
     * Updates macro segment animations.
     *
     * @param selectedIndex currently selected macro index
     * @param hoveredIndex  currently hovered macro index (-1 for none)
     */
    public void updateMacroSegmentAnimations(int selectedIndex, int hoveredIndex, boolean animate) {
        for (int i = 0; i < macroSegmentAnimations.length; i++) {
            float target = (i == selectedIndex || i == hoveredIndex) ? 1f : 0f;
            if (animate) {
                macroSegmentAnimations[i] = Mth.lerp(RadialMenuConstants.LERP_FACTOR,
                    macroSegmentAnimations[i], target);
            } else {
                macroSegmentAnimations[i] = target;
            }
        }
    }

    /**
     * Updates center button hover animation.
     *
     * @param hovered whether center is being hovered
     */
    public void updateCenterHoverAnimation(boolean hovered, boolean animate) {
        float target = hovered ? 1f : 0f;
        if (animate) {
            float speed = hovered ? config.hoverInSpeed : config.hoverOutSpeed;
            centerHoverAnimation = Mth.lerp(speed, centerHoverAnimation, target);
        } else {
            centerHoverAnimation = target;
        }
    }

    /**
     * Updates search box animation.
     *
     * @param searchModeActive whether search mode is active
     */
    public void updateSearchBoxAnimation(boolean searchModeActive, boolean animate) {
        float target = searchModeActive ? 1f : 0f;
        if (animate) {
            searchBoxAnimation = Mth.lerp(config.searchBoxSpeed, searchBoxAnimation, target);
        } else {
            searchBoxAnimation = target;
        }
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
        // Reset feedback animations
        shakeProgress = 0f;
        shakeTargetIndex = -1;
        shakeTargetType = TargetType.NONE;
        flashProgress = 0f;
        flashTargetIndex = -1;
        flashTargetType = TargetType.NONE;
        flashType = FlashType.NONE;
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
    // SHAKE ANIMATION (micro-shake for blocked clicks)
    // ================================================================

    /**
     * Starts a micro-shake animation for an item.
     *
     * @param index item index
     */
    public void startItemShake(int index) {
        shakeProgress = 1f;
        shakeTargetIndex = index;
        shakeTargetType = TargetType.ITEM;
    }

    /**
     * Starts a micro-shake animation for a favorite.
     *
     * @param index favorite index
     */
    public void startFavoriteShake(int index) {
        shakeProgress = 1f;
        shakeTargetIndex = index;
        shakeTargetType = TargetType.FAVORITE;
    }

    /**
     * Calculates shake offset for an item.
     * Returns 0 if not shaking or if reduced motion is enabled.
     *
     * @param index item index
     * @param targetType the type to check against
     * @return horizontal shake offset in pixels
     */
    public float getShakeOffset(int index, TargetType targetType) {
        if (shakeProgress <= 0f || shakeTargetType != targetType || shakeTargetIndex != index) {
            return 0f;
        }
        // Damped oscillation: sin wave with decaying amplitude
        float amplitude = SHAKE_AMPLITUDE * shakeProgress;
        float phase = (1f - shakeProgress) * SHAKE_FREQUENCY;
        return amplitude * (float) Math.sin(phase);
    }

    /**
     * Checks if an item is currently shaking.
     */
    public boolean isShaking(int index, TargetType targetType) {
        return shakeProgress > 0f && shakeTargetType == targetType && shakeTargetIndex == index;
    }

    // ================================================================
    // FLASH ANIMATION (blocked/success/failed feedback)
    // ================================================================

    /**
     * Starts a blocked flash animation for an item.
     *
     * @param index item index
     */
    public void startItemBlockedFlash(int index) {
        flashProgress = 1f;
        flashTargetIndex = index;
        flashTargetType = TargetType.ITEM;
        flashType = FlashType.BLOCKED;
    }

    /**
     * Starts a blocked flash animation for a favorite.
     *
     * @param index favorite index
     */
    public void startFavoriteBlockedFlash(int index) {
        flashProgress = 1f;
        flashTargetIndex = index;
        flashTargetType = TargetType.FAVORITE;
        flashType = FlashType.BLOCKED;
    }

    /**
     * Starts a success flash animation for a favorite.
     *
     * @param index favorite index
     */
    public void startFavoriteSuccessFlash(int index) {
        flashProgress = 1f;
        flashTargetIndex = index;
        flashTargetType = TargetType.FAVORITE;
        flashType = FlashType.SUCCESS;
    }

    /**
     * Starts a failed flash animation for a favorite.
     *
     * @param index favorite index
     */
    public void startFavoriteFailedFlash(int index) {
        flashProgress = 1f;
        flashTargetIndex = index;
        flashTargetType = TargetType.FAVORITE;
        flashType = FlashType.FAILED;
    }

    /**
     * Starts a success flash animation for an item.
     *
     * @param index item index
     */
    public void startItemSuccessFlash(int index) {
        flashProgress = 1f;
        flashTargetIndex = index;
        flashTargetType = TargetType.ITEM;
        flashType = FlashType.SUCCESS;
    }

    /**
     * Starts a failed flash animation for an item.
     *
     * @param index item index
     */
    public void startItemFailedFlash(int index) {
        flashProgress = 1f;
        flashTargetIndex = index;
        flashTargetType = TargetType.ITEM;
        flashType = FlashType.FAILED;
    }

    /**
     * Gets the flash alpha for an element (0-1).
     * Returns 0 if not flashing.
     *
     * @param index element index
     * @param targetType the type to check against
     * @return flash alpha (0-1)
     */
    public float getFlashAlpha(int index, TargetType targetType) {
        if (flashProgress <= 0f || flashTargetType != targetType || flashTargetIndex != index) {
            return 0f;
        }
        // Ease out for smooth fade
        return Easing.easeOutQuad(flashProgress);
    }

    /**
     * Gets the current flash type for an element.
     *
     * @param index element index
     * @param targetType the type to check against
     * @return flash type, or NONE if not flashing
     */
    public FlashType getFlashType(int index, TargetType targetType) {
        if (flashProgress <= 0f || flashTargetType != targetType || flashTargetIndex != index) {
            return FlashType.NONE;
        }
        return flashType;
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
