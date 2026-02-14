package com.devmod.abilities;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.devmod.config.GameMechanicsConfig;

/**
 * Charge-based shared cooldown for dash and dodge abilities.
 * Players have a pool of charges (default 2) that are shared between dash and dodge.
 * When a charge is consumed, it begins recharging independently.
 * Inspired by Combat Roll's RollManager charge system.
 */
public class SharedAbilityCooldown {

    public static final SharedAbilityCooldown INSTANCE = new SharedAbilityCooldown();

    /** Default max charges in the shared pool. */
    public static final int DEFAULT_MAX_CHARGES = 2;

    /** Default recharge time per charge in ticks (60 = 3 seconds). */
    public static final int DEFAULT_RECHARGE_TICKS = 60;

    /** Legacy constant kept for compile compat — maps to recharge ticks. */
    public static final int DEFAULT_COOLDOWN_TICKS = DEFAULT_RECHARGE_TICKS;

    private final Map<UUID, ChargeData> playerCharges = new ConcurrentHashMap<>();

    private SharedAbilityCooldown() {}

    // Config accessors with safe fallback
    private static int maxCharges() {
        try { return GameMechanicsConfig.ABILITY_MAX_CHARGES.get(); }
        catch (Exception e) { return DEFAULT_MAX_CHARGES; }
    }

    private static int rechargeTicks() {
        try { return GameMechanicsConfig.ABILITY_RECHARGE_TICKS.get(); }
        catch (Exception e) { return DEFAULT_RECHARGE_TICKS; }
    }

    /** Get or create charge data for a player, syncing max charges from config. */
    private ChargeData getOrCreate(UUID playerId) {
        return playerCharges.computeIfAbsent(playerId, id -> {
            int max = maxCharges();
            return new ChargeData(max, max, 0);
        });
    }

    /**
     * Returns true if at least one charge is available.
     */
    public boolean isReady(UUID playerId) {
        return getOrCreate(playerId).availableCharges > 0;
    }

    /**
     * Consume one charge and begin recharging.
     * @param playerId the player
     * @param ignoredTicks kept for API compat — recharge time is read from config
     */
    public void trigger(UUID playerId, int ignoredTicks) {
        ChargeData data = getOrCreate(playerId);
        if (data.availableCharges <= 0) return;

        data.availableCharges--;

        // If we weren't already recharging (were at max), start the timer
        if (data.rechargeTicksRemaining <= 0) {
            data.rechargeTicksRemaining = rechargeTicks();
        }
    }

    /**
     * Get cooldown progress for the next recharging charge.
     * 0.0 = fully charged (or charge ready), 1.0 = just consumed.
     * When all charges are full, returns 0.0.
     */
    public float getCooldownPercent(UUID playerId, int ignoredMaxTicks) {
        ChargeData data = getOrCreate(playerId);
        if (data.availableCharges >= maxCharges()) return 0f;
        int maxRecharge = rechargeTicks();
        if (maxRecharge <= 0) return 0f;
        return Math.max(0f, Math.min(1f, (float) data.rechargeTicksRemaining / maxRecharge));
    }

    /**
     * Get number of currently available charges.
     */
    public int getAvailableCharges(UUID playerId) {
        return getOrCreate(playerId).availableCharges;
    }

    /**
     * Get max charges from config.
     */
    public int getMaxCharges(UUID playerId) {
        return maxCharges();
    }

    /**
     * Tick the recharge timer. Call once per server tick per player.
     * When a charge finishes recharging, the next one begins automatically.
     */
    public void tick(UUID playerId) {
        ChargeData data = playerCharges.get(playerId);
        if (data == null) return;

        int max = maxCharges();

        // Sync max charges if config changed at runtime
        if (data.availableCharges > max) {
            data.availableCharges = max;
        }

        // Nothing to recharge
        if (data.availableCharges >= max) {
            data.rechargeTicksRemaining = 0;
            return;
        }

        // Tick down recharge
        if (data.rechargeTicksRemaining > 0) {
            data.rechargeTicksRemaining--;
            if (data.rechargeTicksRemaining <= 0) {
                data.availableCharges++;
                // If still below max, start next charge recharge
                if (data.availableCharges < max) {
                    data.rechargeTicksRemaining = rechargeTicks();
                }
            }
        } else if (data.availableCharges < max) {
            // Edge case: no timer running but below max — start one
            data.rechargeTicksRemaining = rechargeTicks();
        }
    }

    /** Remove player data on disconnect. */
    public void cleanup(UUID playerId) {
        playerCharges.remove(playerId);
    }

    /** Internal charge state per player. */
    static class ChargeData {
        int availableCharges;
        final int initialMax;
        int rechargeTicksRemaining;

        ChargeData(int availableCharges, int initialMax, int rechargeTicksRemaining) {
            this.availableCharges = availableCharges;
            this.initialMax = initialMax;
            this.rechargeTicksRemaining = rechargeTicksRemaining;
        }
    }
}
