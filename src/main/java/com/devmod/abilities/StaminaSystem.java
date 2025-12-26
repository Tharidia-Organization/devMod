package com.devmod.abilities;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.devmod.network.NetworkHandler;

public class StaminaSystem {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final StaminaSystem INSTANCE = new StaminaSystem();

    // Default configuration
    private static final float DEFAULT_MAX_STAMINA = 100.0f;
    private static final float DEFAULT_REGEN_RATE = 10.0f; // Per second
    private static final float DEFAULT_REGEN_DELAY = 1.0f; // Seconds before regen starts after use
    private static final float SPRINT_COST_PER_SECOND = 5.0f;
    private static final float JUMP_COST = 5.0f;

    // Player stamina data
    private final Map<UUID, StaminaData> playerStamina = new ConcurrentHashMap<>();

    private StaminaSystem() {}

    /**
     * Get or create stamina data for a player.
     */
    @Nonnull
    public StaminaData getStaminaData(UUID playerId) {
        return Objects.requireNonNull(playerStamina.computeIfAbsent(playerId, id -> new StaminaData()));
    }

    /**
     * Get current stamina for a player.
     */
    public float getStamina(UUID playerId) {
        return getStaminaData(playerId).currentStamina;
    }

    /**
     * Get max stamina for a player (including modifiers).
     */
    public float getMaxStamina(UUID playerId) {
        StaminaData data = getStaminaData(playerId);
        return data.maxStamina * data.maxStaminaMultiplier;
    }

    /**
     * Get stamina as percentage (0.0 - 1.0).
     */
    public float getStaminaPercent(UUID playerId) {
        float max = getMaxStamina(playerId);
        return max > 0 ? getStamina(playerId) / max : 0;
    }

    /**
     * Check if player has enough stamina for an action.
     */
    public boolean hasStamina(UUID playerId, float amount) {
        return getStamina(playerId) >= amount;
    }

    /**
     * Consume stamina for an action. Returns true if successful.
     */
    public boolean consumeStamina(UUID playerId, float amount) {
        StaminaData data = getStaminaData(playerId);

        if (data.currentStamina < amount) {
            return false;
        }

        data.currentStamina -= amount;
        data.regenDelayTicks = (int) (data.regenDelay * 20); // Convert seconds to ticks
        data.lastUseTime = System.currentTimeMillis();

        LOGGER.debug("[Stamina] Player {} consumed {} stamina (now: {})",
            playerId, amount, data.currentStamina);

        return true;
    }

    /**
     * Force consume stamina (can go negative for exhaustion effects).
     */
    public void forceConsumeStamina(UUID playerId, float amount) {
        StaminaData data = getStaminaData(playerId);
        data.currentStamina = Math.max(-20, data.currentStamina - amount); // Cap negative at -20
        data.regenDelayTicks = (int) (data.regenDelay * 20);
        data.lastUseTime = System.currentTimeMillis();
    }

    /**
     * Restore stamina (from items, perks, etc.).
     */
    public void restoreStamina(UUID playerId, float amount) {
        StaminaData data = getStaminaData(playerId);
        float maxStamina = getMaxStamina(playerId);
        data.currentStamina = Math.min(maxStamina, data.currentStamina + amount);
    }

    /**
     * Set stamina to full.
     */
    public void fillStamina(UUID playerId) {
        StaminaData data = getStaminaData(playerId);
        data.currentStamina = getMaxStamina(playerId);
        data.regenDelayTicks = 0;
    }

    /**
     * Check if player is exhausted (stamina <= 0).
     */
    public boolean isExhausted(UUID playerId) {
        return getStamina(playerId) <= 0;
    }

    /**
     * Apply a temporary modifier to max stamina.
     */
    public void setMaxStaminaMultiplier(UUID playerId, float multiplier) {
        getStaminaData(playerId).maxStaminaMultiplier = multiplier;
    }

    /**
     * Apply a temporary modifier to regen rate.
     */
    public void setRegenRateMultiplier(UUID playerId, float multiplier) {
        getStaminaData(playerId).regenRateMultiplier = multiplier;
    }

    /**
     * Reset modifiers to default.
     */
    public void resetModifiers(UUID playerId) {
        StaminaData data = getStaminaData(playerId);
        data.maxStaminaMultiplier = 1.0f;
        data.regenRateMultiplier = 1.0f;
    }

    /**
     * Called every server tick for each player.
     * Handles stamina regeneration and sprint consumption.
     */
    public void tick(ServerPlayer player) {
        UUID playerId = player.getUUID();
        StaminaData data = getStaminaData(playerId);

        // Sprint consumption
        if (player.isSprinting() && !player.isCreative() && !player.isSpectator()) {
            float sprintCost = (SPRINT_COST_PER_SECOND / 20.0f) * data.consumptionMultiplier;
            if (data.currentStamina > 0) {
                data.currentStamina -= sprintCost;
                data.regenDelayTicks = (int) (data.regenDelay * 20);
            } else {
                // Out of stamina - stop sprinting
                player.setSprinting(false);
            }
        }

        // Regeneration (after delay)
        if (data.regenDelayTicks > 0) {
            data.regenDelayTicks--;
        } else {
            float maxStamina = getMaxStamina(playerId);
            if (data.currentStamina < maxStamina) {
                float regenAmount = (data.regenRate * data.regenRateMultiplier) / 20.0f;
                data.currentStamina = Math.min(maxStamina, data.currentStamina + regenAmount);
            }
        }

        // Sync to client periodically (every 10 ticks)
        if (player.tickCount % 10 == 0) {
            syncToClient(player, data);
        }
    }

    /**
     * Handle player jump - consume stamina.
     */
    public void onPlayerJump(Player player) {
        if (player.isCreative() || player.isSpectator()) return;

        StaminaData data = getStaminaData(player.getUUID());
        float cost = JUMP_COST * data.consumptionMultiplier;

        if (data.currentStamina >= cost) {
            data.currentStamina -= cost;
            data.regenDelayTicks = (int) (data.regenDelay * 20);
        }
    }

    /**
     * Sync stamina data to client for HUD display.
     */
    private void syncToClient(ServerPlayer player, StaminaData data) {
        // Send packet to client with stamina data
        NetworkHandler.sendStaminaSync(player, data.currentStamina, getMaxStamina(player.getUUID()));
    }

    /**
     * Clean up player data on disconnect.
     */
    public void cleanupPlayer(UUID playerId) {
        playerStamina.remove(playerId);
    }

    /**
     * Get stamina cost multiplier for a player.
     */
    public float getConsumptionMultiplier(UUID playerId) {
        return getStaminaData(playerId).consumptionMultiplier;
    }

    /**
     * Set stamina cost multiplier (from perks).
     */
    public void setConsumptionMultiplier(UUID playerId, float multiplier) {
        getStaminaData(playerId).consumptionMultiplier = multiplier;
    }

    /**
     * Stamina data container for a player.
     */
    public static class StaminaData {
        public float currentStamina = DEFAULT_MAX_STAMINA;
        public float maxStamina = DEFAULT_MAX_STAMINA;
        public float regenRate = DEFAULT_REGEN_RATE;
        public float regenDelay = DEFAULT_REGEN_DELAY;
        public int regenDelayTicks = 0;
        public long lastUseTime = 0;

        // Modifiers (from perks, effects, etc.)
        public float maxStaminaMultiplier = 1.0f;
        public float regenRateMultiplier = 1.0f;
        public float consumptionMultiplier = 1.0f;
    }

    /**
     * Stamina costs for various actions.
     */
    public static class StaminaCosts {
        public static final float DASH = 25.0f;
        public static final float DODGE = 20.0f;
        public static final float DOUBLE_JUMP = 15.0f;
        public static final float SPRINT_PER_SECOND = SPRINT_COST_PER_SECOND;
        public static final float JUMP = JUMP_COST;
        public static final float HEAVY_ATTACK = 30.0f;
        public static final float BLOCK = 10.0f;
        public static final float PARRY = 15.0f;
    }
}
