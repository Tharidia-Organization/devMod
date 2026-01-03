package com.devmod.client.compat.mods.epicfight;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.compat.mods.epicfight.EpicFightCompat;

/**
 * Client-side cache for Epic Fight state.
 * Provides smooth animation interpolation and state change detection for HUD rendering.
 *
 * <p>Updated every tick via RenderEvents, reads state from EpicFightCompat API.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientEpicFightCache {

    public static final ClientEpicFightCache INSTANCE = new ClientEpicFightCache();

    // === Current State ===
    private boolean battleMode;
    private boolean guarding;
    private boolean parrying;
    private boolean perfectParry;
    private float stamina;
    private float maxStamina;
    @Nullable private String currentSkillName;

    // === Displayed/Animated Values ===
    private float displayedStamina;
    private float guardFlashAlpha;
    private float parryFlashAlpha;
    private float perfectParryScale = 1.0f;

    // === State Change Flags (consumed by HUD) ===
    private boolean guardJustStarted;
    private boolean parryJustSucceeded;
    private boolean perfectParryJustOccurred;
    private boolean guardImpactJustOccurred;
    private boolean staminaLowWarningTriggered;

    // === Previous State (for edge detection) ===
    private boolean prevGuarding;
    private boolean prevParrying;
    private float prevStamina;

    // === Animation Constants ===
    private static final float STAMINA_LERP_SPEED = 0.15f;
    private static final float FLASH_DECAY_RATE = 0.12f;
    private static final float PERFECT_PARRY_SCALE_DECAY = 0.08f;
    private static final float PERFECT_PARRY_MAX_SCALE = 1.5f;
    private static final float STAMINA_LOW_THRESHOLD = 0.20f;
    private static final float GUARD_IMPACT_STAMINA_DROP = 0.05f; // 5% drop indicates impact

    private ClientEpicFightCache() {}

    /**
     * Update cache from local player state.
     * Call every client tick when player is not null.
     */
    public void updateFromLocalPlayer(@Nullable LocalPlayer player) {
        if (player == null || !EpicFightCompat.isApiAvailable()) {
            clearState();
            return;
        }

        // Store previous values for edge detection
        prevGuarding = guarding;
        prevParrying = parrying;
        prevStamina = stamina;

        // Reset consumed flags
        guardJustStarted = false;
        parryJustSucceeded = false;
        perfectParryJustOccurred = false;
        guardImpactJustOccurred = false;
        staminaLowWarningTriggered = false;

        // Read current state from EpicFightCompat
        battleMode = EpicFightCompat.isInBattleMode(player);
        guarding = EpicFightCompat.isGuarding(player);
        parrying = EpicFightCompat.isParrying(player);
        perfectParry = EpicFightCompat.isPerfectParry(player);
        stamina = EpicFightCompat.getStamina(player);
        maxStamina = EpicFightCompat.getMaxStamina(player);
        currentSkillName = EpicFightCompat.getHoldingSkillName(player);

        // Detect state changes
        if (guarding && !prevGuarding) {
            guardJustStarted = true;
            guardFlashAlpha = 1.0f;
        }

        if (parrying && !prevParrying) {
            parryJustSucceeded = true;
            parryFlashAlpha = 1.0f;
        }

        if (perfectParry && parryFlashAlpha > 0.5f) {
            perfectParryJustOccurred = true;
            perfectParryScale = PERFECT_PARRY_MAX_SCALE;
        }

        // Detect guard impact (stamina dropped significantly while guarding)
        if (guarding && prevGuarding && maxStamina > 0) {
            float staminaDrop = (prevStamina - stamina) / maxStamina;
            if (staminaDrop >= GUARD_IMPACT_STAMINA_DROP) {
                guardImpactJustOccurred = true;
            }
        }

        // Detect stamina low warning (crossed below threshold)
        if (maxStamina > 0) {
            float prevPercent = prevStamina / maxStamina;
            float currentPercent = stamina / maxStamina;
            if (prevPercent >= STAMINA_LOW_THRESHOLD && currentPercent < STAMINA_LOW_THRESHOLD) {
                staminaLowWarningTriggered = true;
            }
        }
    }

    /**
     * Tick animations (call every render tick or client tick).
     *
     * @param deltaTime Time since last tick (typically 1.0f for game tick, or partial tick for render)
     */
    public void tickAnimations(float deltaTime) {
        // Smooth stamina bar
        if (maxStamina > 0) {
            float targetDisplayed = stamina / maxStamina;
            displayedStamina = lerp(displayedStamina, targetDisplayed, STAMINA_LERP_SPEED * deltaTime);
        }

        // Decay flash effects
        guardFlashAlpha = Math.max(0, guardFlashAlpha - FLASH_DECAY_RATE * deltaTime);
        parryFlashAlpha = Math.max(0, parryFlashAlpha - FLASH_DECAY_RATE * deltaTime);

        // Decay perfect parry scale back to 1.0
        if (perfectParryScale > 1.0f) {
            perfectParryScale = Math.max(1.0f, perfectParryScale - PERFECT_PARRY_SCALE_DECAY * deltaTime);
        }
    }

    /**
     * Clear all state (call on logout or world change).
     */
    public void clear() {
        clearState();
        displayedStamina = 0;
        guardFlashAlpha = 0;
        parryFlashAlpha = 0;
        perfectParryScale = 1.0f;
    }

    private void clearState() {
        battleMode = false;
        guarding = false;
        parrying = false;
        perfectParry = false;
        stamina = 0;
        maxStamina = 0;
        currentSkillName = null;
        guardJustStarted = false;
        parryJustSucceeded = false;
        perfectParryJustOccurred = false;
        guardImpactJustOccurred = false;
        staminaLowWarningTriggered = false;
    }

    // === Getters ===

    /**
     * Check if Epic Fight is active and player is in battle mode.
     */
    public boolean isActive() {
        return EpicFightCompat.isApiAvailable() && battleMode;
    }

    public boolean isBattleMode() {
        return battleMode;
    }

    public boolean isGuarding() {
        return guarding;
    }

    public boolean isParrying() {
        return parrying;
    }

    public boolean isPerfectParry() {
        return perfectParry;
    }

    /**
     * Get stamina percentage (0-1) with smooth animation.
     */
    public float getDisplayedStaminaPercent() {
        return displayedStamina;
    }

    @Nullable
    public String getCurrentSkillName() {
        return currentSkillName;
    }

    // === Animation Getters ===

    public float getGuardFlashAlpha() {
        return guardFlashAlpha;
    }

    public float getParryFlashAlpha() {
        return parryFlashAlpha;
    }

    public float getPerfectParryScale() {
        return perfectParryScale;
    }

    // === State Change Flags (consumed once) ===

    /**
     * Check and consume guard start flag.
     */
    public boolean wasGuardJustStarted() {
        boolean result = guardJustStarted;
        guardJustStarted = false;
        return result;
    }

    /**
     * Check and consume parry success flag.
     */
    public boolean wasParryJustSucceeded() {
        boolean result = parryJustSucceeded;
        parryJustSucceeded = false;
        return result;
    }

    /**
     * Check and consume perfect parry flag.
     */
    public boolean wasPerfectParryJustOccurred() {
        boolean result = perfectParryJustOccurred;
        perfectParryJustOccurred = false;
        return result;
    }

    /**
     * Check and consume guard impact flag (blocked an attack).
     */
    public boolean wasGuardImpactJustOccurred() {
        boolean result = guardImpactJustOccurred;
        guardImpactJustOccurred = false;
        return result;
    }

    /**
     * Check and consume stamina low warning flag.
     */
    public boolean wasStaminaLowWarningTriggered() {
        boolean result = staminaLowWarningTriggered;
        staminaLowWarningTriggered = false;
        return result;
    }

    // === Utility ===

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.min(1.0f, Math.max(0.0f, t));
    }

    /**
     * Check if cache needs updating (player exists and not paused).
     */
    public static boolean shouldUpdate() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && !mc.isPaused() && EpicFightCompat.isApiAvailable();
    }
}
