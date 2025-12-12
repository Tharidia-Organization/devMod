package com.frenkvs.devmod.abilities;

import com.frenkvs.devmod.endurance.ComboSystem;
import com.frenkvs.devmod.telemetry.player.AbilityTelemetryService;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dash Ability System - Quick burst of speed in movement direction.
 *
 * Features:
 * - Configurable dash distance and duration
 * - Cooldown between dashes
 * - Stamina cost
 * - Integration with combo system for style points
 * - Ground and air dash variants
 */
public class DashAbilitySystem {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DashAbilitySystem INSTANCE = new DashAbilitySystem();

    // Default configuration
    private static final float DEFAULT_DASH_DISTANCE = 5.0f; // Blocks - target dash distance
    private static final int DEFAULT_DASH_DURATION_TICKS = 5; // Duration of dash impulse (0.25 seconds)
    private static final float DEFAULT_DASH_SPEED = DEFAULT_DASH_DISTANCE / (DEFAULT_DASH_DURATION_TICKS / 20.0f); // Velocity to cover distance in duration
    private static final int DEFAULT_COOLDOWN_TICKS = 40; // 2 seconds
    private static final float DEFAULT_STAMINA_COST = StaminaSystem.StaminaCosts.DASH;

    // Player dash data
    private final Map<UUID, DashData> playerDash = new ConcurrentHashMap<>();

    private DashAbilitySystem() {}

    /**
     * Get or create dash data for a player.
     */
    public DashData getDashData(UUID playerId) {
        return playerDash.computeIfAbsent(playerId, id -> new DashData());
    }

    /**
     * Check if dash is available (off cooldown and has stamina).
     */
    public boolean canDash(UUID playerId) {
        DashData data = getDashData(playerId);
        return data.cooldownTicks <= 0 && StaminaSystem.INSTANCE.hasStamina(playerId, data.staminaCost);
    }

    /**
     * Get remaining cooldown in ticks.
     */
    public int getCooldownTicks(UUID playerId) {
        return getDashData(playerId).cooldownTicks;
    }

    /**
     * Get remaining cooldown as percentage (0.0 = ready, 1.0 = just used).
     */
    public float getCooldownPercent(UUID playerId) {
        DashData data = getDashData(playerId);
        return data.maxCooldownTicks > 0 ? (float) data.cooldownTicks / data.maxCooldownTicks : 0;
    }

    /**
     * Check if dash is available.
     */
    public boolean isDashAvailable(UUID playerId) {
        return getCooldownTicks(playerId) <= 0;
    }

    /**
     * Attempt to perform a dash.
     * @return true if dash was successful
     */
    public boolean tryDash(ServerPlayer player) {
        UUID playerId = player.getUUID();
        DashData data = getDashData(playerId);
        float staminaBefore = StaminaSystem.INSTANCE.getStamina(playerId);

        // Check cooldown
        if (data.cooldownTicks > 0) {
            LOGGER.debug("[Dash] Player {} dash on cooldown ({} ticks)", player.getName().getString(), data.cooldownTicks);
            AbilityTelemetryService.INSTANCE.recordDashAttempt(playerId, false, staminaBefore, staminaBefore);
            return false;
        }

        // Check stamina
        if (!StaminaSystem.INSTANCE.consumeStamina(playerId, data.staminaCost)) {
            LOGGER.debug("[Dash] Player {} insufficient stamina for dash", player.getName().getString());
            AbilityTelemetryService.INSTANCE.recordDashAttempt(playerId, false, staminaBefore, staminaBefore);
            return false;
        }

        float staminaAfter = StaminaSystem.INSTANCE.getStamina(playerId);

        // Perform dash
        performDash(player, data);

        // Set cooldown
        data.cooldownTicks = data.maxCooldownTicks;
        data.lastDashTime = System.currentTimeMillis();
        data.dashCount++;

        // Register with combo system for style points
        ComboSystem.INSTANCE.getSession(playerId).ifPresent(session -> {
            session.registerAction(ComboSystem.ActionType.PERFECT_DODGE, 0); // Use dodge action for style
        });

        // Record telemetry
        AbilityTelemetryService.INSTANCE.recordDashAttempt(playerId, true, staminaBefore, staminaAfter);

        LOGGER.debug("[Dash] Player {} performed dash (count: {})", player.getName().getString(), data.dashCount);
        return true;
    }

    /**
     * Perform the actual dash movement.
     */
    private void performDash(ServerPlayer player, DashData data) {
        // Get look direction (horizontal only for ground dash)
        Vec3 lookVec = player.getLookAngle();
        Vec3 dashDirection;

        if (player.onGround()) {
            // Ground dash: horizontal only
            dashDirection = new Vec3(lookVec.x, 0, lookVec.z).normalize();
        } else {
            // Air dash: include vertical component
            dashDirection = lookVec.normalize();
        }

        // Calculate dash velocity
        Vec3 dashVelocity = dashDirection.scale(data.dashSpeed);

        // Apply velocity
        player.setDeltaMovement(Objects.requireNonNull(dashVelocity));
        player.hurtMarked = true; // Force velocity sync to client

        // Play dash sound
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            Objects.requireNonNull(SoundEvents.PHANTOM_FLAP), SoundSource.PLAYERS, 0.5f, 1.5f);

        // Mark as dashing for duration
        data.isDashing = true;
        data.dashDurationTicks = data.maxDashDurationTicks;
    }

    /**
     * Called every server tick for each player.
     * Handles cooldown countdown and dash duration.
     */
    public void tick(ServerPlayer player) {
        UUID playerId = player.getUUID();
        DashData data = getDashData(playerId);

        // Count down cooldown
        if (data.cooldownTicks > 0) {
            data.cooldownTicks--;
        }

        // Handle active dash
        if (data.isDashing) {
            data.dashDurationTicks--;
            if (data.dashDurationTicks <= 0) {
                data.isDashing = false;
            }
        }
    }

    /**
     * Check if player is currently dashing.
     */
    public boolean isDashing(UUID playerId) {
        return getDashData(playerId).isDashing;
    }

    /**
     * Set cooldown multiplier (from perks).
     */
    public void setCooldownMultiplier(UUID playerId, float multiplier) {
        DashData data = getDashData(playerId);
        data.maxCooldownTicks = (int) (DEFAULT_COOLDOWN_TICKS * multiplier);
    }

    /**
     * Set dash distance multiplier (from perks).
     */
    public void setDashSpeedMultiplier(UUID playerId, float multiplier) {
        DashData data = getDashData(playerId);
        data.dashSpeed = DEFAULT_DASH_SPEED * multiplier;
    }

    /**
     * Set stamina cost multiplier (from perks).
     */
    public void setStaminaCostMultiplier(UUID playerId, float multiplier) {
        DashData data = getDashData(playerId);
        data.staminaCost = DEFAULT_STAMINA_COST * multiplier;
    }

    /**
     * Reset all modifiers to default.
     */
    public void resetModifiers(UUID playerId) {
        DashData data = getDashData(playerId);
        data.maxCooldownTicks = DEFAULT_COOLDOWN_TICKS;
        data.dashSpeed = DEFAULT_DASH_SPEED;
        data.staminaCost = DEFAULT_STAMINA_COST;
    }

    /**
     * Clean up player data on disconnect.
     */
    public void cleanupPlayer(UUID playerId) {
        playerDash.remove(playerId);
    }

    /**
     * Get total dash count for a player.
     */
    public int getDashCount(UUID playerId) {
        return getDashData(playerId).dashCount;
    }

    /**
     * Dash data container for a player.
     */
    public static class DashData {
        public int cooldownTicks = 0;
        public int maxCooldownTicks = DEFAULT_COOLDOWN_TICKS;
        public float dashSpeed = DEFAULT_DASH_SPEED;
        public float staminaCost = DEFAULT_STAMINA_COST;
        public boolean isDashing = false;
        public int dashDurationTicks = 0;
        public int maxDashDurationTicks = DEFAULT_DASH_DURATION_TICKS;
        public long lastDashTime = 0;
        public int dashCount = 0;
    }
}
