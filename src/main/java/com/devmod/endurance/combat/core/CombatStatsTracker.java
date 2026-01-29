package com.devmod.endurance.combat.core;

import java.util.ArrayList;
import java.util.List;

import com.devmod.endurance.combat.api.IComboSession.CombatStats;

/**
 * Tracks combat statistics during a quest session.
 *
 * <p>Single Responsibility: Only manages combat statistics counters
 * (hits, kills, damage, defensive actions). Does not handle combo,
 * style, or other concerns.</p>
 *
 * <p>Thread Safety: Not thread-safe. Should be accessed from a single
 * thread (server tick thread).</p>
 */
public final class CombatStatsTracker {

    // Hit tracking
    private int totalHits = 0;
    private float totalDamage = 0;
    private static final float MAX_DAMAGE_PER_HIT = 10000f; // Cap to prevent overflow

    // Kill tracking
    private int totalKills = 0;
    private final List<Long> recentKillTimes = new ArrayList<>();
    private static final long MULTI_KILL_WINDOW_MS = 2000;

    // Defensive action tracking
    private int perfectDodges = 0;
    private int parries = 0;
    private int counterAttacks = 0;

    // Wave tracking
    private float damageTakenThisWave = 0;

    /**
     * Record a hit dealt to an enemy.
     *
     * @param damage Damage dealt
     */
    public void recordHit(float damage) {
        totalHits++;
        // Cap damage to prevent overflow from corrupting analytics
        totalDamage += Math.min(damage, MAX_DAMAGE_PER_HIT);
    }

    /**
     * Record a kill.
     *
     * @return The number of kills in the current multi-kill window
     */
    public int recordKill() {
        long now = System.currentTimeMillis();
        totalKills++;

        // Track for multi-kills
        recentKillTimes.removeIf(t -> now - t > MULTI_KILL_WINDOW_MS);
        recentKillTimes.add(now);

        return recentKillTimes.size();
    }

    /**
     * Check if current kills qualify as a multi-kill.
     *
     * @return true if 3+ kills in the multi-kill window
     */
    public boolean isMultiKill() {
        return recentKillTimes.size() >= 3;
    }

    /**
     * Record a perfect dodge.
     */
    public void recordPerfectDodge() {
        perfectDodges++;
    }

    /**
     * Record a parry.
     */
    public void recordParry() {
        parries++;
    }

    /**
     * Record a counter attack.
     */
    public void recordCounterAttack() {
        counterAttacks++;
    }

    /**
     * Record damage taken by the player.
     *
     * @param damage Damage taken
     */
    public void recordDamageTaken(float damage) {
        damageTakenThisWave += damage;
    }

    /**
     * Check if current wave is no-hit.
     */
    public boolean isNoDamageWave() {
        return damageTakenThisWave < 0.01f;
    }

    /**
     * Start new wave, resetting wave-specific tracking.
     */
    public void startNewWave() {
        damageTakenThisWave = 0;
    }

    /**
     * Get immutable snapshot of current stats.
     */
    public CombatStats snapshot() {
        return new CombatStats(
            totalHits,
            totalKills,
            totalDamage,
            perfectDodges,
            parries,
            counterAttacks
        );
    }

    // === Getters ===

    public int getTotalHits() {
        return totalHits;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public float getTotalDamage() {
        return totalDamage;
    }

    public int getPerfectDodges() {
        return perfectDodges;
    }

    public int getParries() {
        return parries;
    }

    public int getCounterAttacks() {
        return counterAttacks;
    }

    public float getDamageTakenThisWave() {
        return damageTakenThisWave;
    }

    /**
     * Calculate perfection bonus for final score.
     * Formula: (dodges + parries*2 + counters*3) * 50
     */
    public int getPerfectionBonus() {
        return (perfectDodges + parries * 2 + counterAttacks * 3) * 50;
    }

    /**
     * Get recent kills count (in multi-kill window).
     */
    public int getRecentKillCount() {
        long now = System.currentTimeMillis();
        recentKillTimes.removeIf(t -> now - t > MULTI_KILL_WINDOW_MS);
        return recentKillTimes.size();
    }
}
