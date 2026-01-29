package com.devmod.abilities;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import com.devmod.endurance.ComboSystem;
import com.devmod.telemetry.player.AbilityTelemetryService;

public class DodgeAbilitySystem {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DodgeAbilitySystem INSTANCE = new DodgeAbilitySystem();

    // Default configuration
    private static final float DEFAULT_DODGE_SPEED = 0.8f; // Velocity multiplier
    private static final int DEFAULT_COOLDOWN_TICKS = 30; // 1.5 seconds
    private static final float DEFAULT_STAMINA_COST = StaminaSystem.StaminaCosts.DODGE;
    private static final int DEFAULT_IFRAMES_TICKS = 8; // 0.4 seconds of invincibility
    private static final int DEFAULT_DODGE_DURATION_TICKS = 6; // Duration of dodge animation

    // Perfect dodge window (ticks before enemy attack lands)
    private static final int PERFECT_DODGE_WINDOW_TICKS = 5;

    // Player dodge data
    private final Map<UUID, DodgeData> playerDodge = new ConcurrentHashMap<>();

    private DodgeAbilitySystem() {}

    /**
     * Get or create dodge data for a player.
     */
    @Nonnull
    public DodgeData getDodgeData(UUID playerId) {
        return Objects.requireNonNull(playerDodge.computeIfAbsent(playerId, id -> new DodgeData()));
    }

    /**
     * Check if dodge is available (off cooldown and has stamina).
     */
    public boolean canDodge(UUID playerId) {
        DodgeData data = getDodgeData(playerId);
        return data.cooldownTicks <= 0 && StaminaSystem.INSTANCE.hasStamina(playerId, data.staminaCost);
    }

    /**
     * Get remaining cooldown in ticks.
     */
    public int getCooldownTicks(UUID playerId) {
        return getDodgeData(playerId).cooldownTicks;
    }

    /**
     * Get remaining cooldown as percentage (0.0 = ready, 1.0 = just used).
     */
    public float getCooldownPercent(UUID playerId) {
        DodgeData data = getDodgeData(playerId);
        return data.maxCooldownTicks > 0 ? (float) data.cooldownTicks / data.maxCooldownTicks : 0;
    }

    /**
     * Check if dodge is available.
     */
    public boolean isDodgeAvailable(UUID playerId) {
        return getCooldownTicks(playerId) <= 0;
    }

    /**
     * Check if player currently has invincibility frames.
     */
    public boolean hasIFrames(UUID playerId) {
        return getDodgeData(playerId).iframesTicks > 0;
    }

    /**
     * Attempt to perform a dodge.
     * @param direction The direction to dodge (LEFT, RIGHT, BACK, or null for auto)
     * @return true if dodge was successful
     */
    public boolean tryDodge(ServerPlayer player, DodgeDirection direction) {
        UUID playerId = player.getUUID();
        DodgeData data = getDodgeData(playerId);
        float staminaBefore = StaminaSystem.INSTANCE.getStamina(playerId);

        // Check cooldown
        if (data.cooldownTicks > 0) {
            LOGGER.debug("[Dodge] Player {} dodge on cooldown ({} ticks)", player.getName().getString(), data.cooldownTicks);
            AbilityTelemetryService.INSTANCE.recordDodgeAttempt(playerId, false, direction, staminaBefore, staminaBefore);
            return false;
        }

        // Check stamina
        if (!StaminaSystem.INSTANCE.consumeStamina(playerId, data.staminaCost)) {
            LOGGER.debug("[Dodge] Player {} insufficient stamina for dodge", player.getName().getString());
            AbilityTelemetryService.INSTANCE.recordDodgeAttempt(playerId, false, direction, staminaBefore, staminaBefore);
            return false;
        }

        float staminaAfter = StaminaSystem.INSTANCE.getStamina(playerId);

        // Perform dodge
        performDodge(player, data, direction);

        // Set cooldown
        data.cooldownTicks = data.maxCooldownTicks;
        data.dodgeCount++;

        // Record telemetry
        AbilityTelemetryService.INSTANCE.recordDodgeAttempt(playerId, true, direction, staminaBefore, staminaAfter);

        LOGGER.debug("[Dodge] Player {} performed {} dodge (count: {})",
            player.getName().getString(), direction, data.dodgeCount);
        return true;
    }

    /**
     * Perform the actual dodge movement.
     */
    private void performDodge(ServerPlayer player, DodgeData data, DodgeDirection direction) {
        // Calculate dodge direction based on player orientation
        Vec3 lookVec = player.getLookAngle();
        Vec3 rightVec = new Vec3(-lookVec.z, 0, lookVec.x).normalize();
        Vec3 dodgeVec;

        switch (direction != null ? direction : DodgeDirection.BACK) {
            case LEFT -> dodgeVec = rightVec.scale(-1);
            case RIGHT -> dodgeVec = rightVec;
            case BACK -> dodgeVec = new Vec3(-lookVec.x, 0, -lookVec.z).normalize();
            case FORWARD -> dodgeVec = new Vec3(lookVec.x, 0, lookVec.z).normalize();
            default -> dodgeVec = new Vec3(-lookVec.x, 0, -lookVec.z).normalize();
        }

        // Apply dodge velocity
        Vec3 dodgeVelocity = dodgeVec.scale(data.dodgeSpeed);
        player.setDeltaMovement(dodgeVelocity.x, player.getDeltaMovement().y + 0.1, dodgeVelocity.z);
        player.hurtMarked = true; // Force velocity sync

        // Play dodge sound
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            Objects.requireNonNull(SoundEvents.PLAYER_ATTACK_SWEEP), SoundSource.PLAYERS, 0.3f, 1.8f);

        // Activate i-frames
        data.isDodging = true;
        data.iframesTicks = data.maxIframesTicks;
        data.dodgeDurationTicks = data.maxDodgeDurationTicks;
        data.lastDodgeDirection = direction;
    }

    /**
     * Called every server tick for each player.
     * Handles cooldown countdown and i-frames.
     */
    public void tick(ServerPlayer player) {
        UUID playerId = player.getUUID();
        DodgeData data = getDodgeData(playerId);

        // Count down cooldown
        if (data.cooldownTicks > 0) {
            data.cooldownTicks--;
        }

        // Count down i-frames
        if (data.iframesTicks > 0) {
            data.iframesTicks--;
        }

        // Handle active dodge
        if (data.isDodging) {
            data.dodgeDurationTicks--;
            if (data.dodgeDurationTicks <= 0) {
                data.isDodging = false;
            }
        }
    }

    /**
     * Called when player takes damage during dodge.
     * Returns true if damage should be negated (i-frames active).
     * Perfect dodge is awarded when damage is avoided within the perfect dodge window.
     */
    public boolean onDamageDuringDodge(ServerPlayer player, float damageAmount, String damageSource) {
        UUID playerId = player.getUUID();
        DodgeData data = getDodgeData(playerId);

        if (data.iframesTicks > 0) {
            // Check if within perfect dodge window (early in the dodge)
            int ticksIntoDodge = data.maxIframesTicks - data.iframesTicks;
            boolean isPerfectDodge = ticksIntoDodge < data.perfectDodgeWindowTicks;

            if (isPerfectDodge) {
                // Perfect dodge! Negate damage and award style points
                data.perfectDodgeCount++;

                // Register perfect dodge with combo system
                if (com.devmod.endurance.combat.ComboSystemFacade.isInitialized()) {
                    com.devmod.endurance.combat.ComboSystemFacade.get()
                        .getSession(playerId)
                        .ifPresent(session -> session.registerAction(ComboSystem.ActionType.PERFECT_DODGE, 0));
                }

                // Play perfect dodge sound
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    Objects.requireNonNull(SoundEvents.PLAYER_ATTACK_CRIT), SoundSource.PLAYERS, 0.5f, 1.5f);

                // Record telemetry
                AbilityTelemetryService.INSTANCE.recordPerfectDodge(playerId, damageAmount, damageSource);

                LOGGER.debug("[Dodge] Player {} perfect dodge! (total: {})",
                    player.getName().getString(), data.perfectDodgeCount);
            }

            return true; // Negate damage (i-frames active)
        }

        return false; // Take damage normally
    }

    /**
     * Check if player is currently dodging.
     */
    public boolean isDodging(UUID playerId) {
        return getDodgeData(playerId).isDodging;
    }

    /**
     * Set cooldown multiplier (from perks).
     */
    public void setCooldownMultiplier(UUID playerId, float multiplier) {
        DodgeData data = getDodgeData(playerId);
        data.maxCooldownTicks = (int) (DEFAULT_COOLDOWN_TICKS * multiplier);
    }

    /**
     * Set i-frames duration multiplier (from perks).
     */
    public void setIFramesMultiplier(UUID playerId, float multiplier) {
        DodgeData data = getDodgeData(playerId);
        data.maxIframesTicks = (int) (DEFAULT_IFRAMES_TICKS * multiplier);
    }

    /**
     * Set stamina cost multiplier (from perks).
     */
    public void setStaminaCostMultiplier(UUID playerId, float multiplier) {
        DodgeData data = getDodgeData(playerId);
        data.staminaCost = DEFAULT_STAMINA_COST * multiplier;
    }

    /**
     * Reset all modifiers to default.
     */
    public void resetModifiers(UUID playerId) {
        DodgeData data = getDodgeData(playerId);
        data.maxCooldownTicks = DEFAULT_COOLDOWN_TICKS;
        data.maxIframesTicks = DEFAULT_IFRAMES_TICKS;
        data.dodgeSpeed = DEFAULT_DODGE_SPEED;
        data.staminaCost = DEFAULT_STAMINA_COST;
    }

    /**
     * Clean up player data on disconnect.
     */
    public void cleanupPlayer(UUID playerId) {
        playerDodge.remove(playerId);
    }

    /**
     * Get total dodge count for a player.
     */
    public int getDodgeCount(UUID playerId) {
        return getDodgeData(playerId).dodgeCount;
    }

    /**
     * Get perfect dodge count for a player.
     */
    public int getPerfectDodgeCount(UUID playerId) {
        return getDodgeData(playerId).perfectDodgeCount;
    }

    /**
     * Dodge direction enum.
     */
    public enum DodgeDirection {
        LEFT,
        RIGHT,
        BACK,
        FORWARD
    }

    /**
     * Dodge data container for a player.
     */
    public static class DodgeData {
        public int cooldownTicks = 0;
        public int maxCooldownTicks = DEFAULT_COOLDOWN_TICKS;
        public float dodgeSpeed = DEFAULT_DODGE_SPEED;
        public float staminaCost = DEFAULT_STAMINA_COST;
        public int iframesTicks = 0;
        public int maxIframesTicks = DEFAULT_IFRAMES_TICKS;
        public boolean isDodging = false;
        public int dodgeDurationTicks = 0;
        public int maxDodgeDurationTicks = DEFAULT_DODGE_DURATION_TICKS;
        public int perfectDodgeWindowTicks = PERFECT_DODGE_WINDOW_TICKS; // Configurable perfect dodge timing window
        @Nullable
        public DodgeDirection lastDodgeDirection = null;
        public int dodgeCount = 0;
        public int perfectDodgeCount = 0;
    }
}
