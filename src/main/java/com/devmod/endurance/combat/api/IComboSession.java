package com.devmod.endurance.combat.api;

import javax.annotation.Nullable;
import java.util.UUID;

import com.devmod.endurance.ComboSystem.ActionType;
import com.devmod.endurance.ComboSystem.StyleRank;
import com.devmod.endurance.FlowStateTracker;

/**
 * Interface for combo session management.
 * Provides a clean contract for tracking player combo state during quests.
 *
 * <p>This interface follows the Interface Segregation Principle - clients
 * only depend on the methods they actually use.</p>
 *
 * @see ComboEvent for event-driven notifications of state changes
 */
public interface IComboSession {

    // === Identity ===

    /**
     * Get the player UUID this session belongs to.
     */
    UUID getPlayerId();

    /**
     * Get the quest UUID this session is tracking.
     */
    UUID getQuestId();

    // === Combo State ===

    /**
     * Get current combo count.
     */
    int getCurrentCombo();

    /**
     * Get maximum combo achieved this session.
     */
    int getMaxCombo();

    /**
     * Check if combo is currently active (not timed out).
     */
    boolean isComboActive();

    // === Style State ===

    /**
     * Get current style score.
     */
    int getStyleScore();

    /**
     * Get total style points earned this session.
     */
    int getTotalStyleEarned();

    /**
     * Get current style rank based on score.
     */
    StyleRank getCurrentRank();

    /**
     * Get highest rank achieved this session.
     */
    StyleRank getHighestRank();

    // === Flow State ===

    /**
     * Get current flow state (STALE, NEUTRAL, FRESH, VIRTUOSO).
     */
    FlowStateTracker.FlowState getFlowState();

    /**
     * Get progress towards VIRTUOSO state (0.0 - 1.0).
     */
    float getVirtuosoProgress();

    /**
     * Get risk of entering STALE state (0.0 - 1.0).
     */
    float getStaleRisk();

    /**
     * Get count of unique action types used in current combo.
     */
    int getUniqueActionCount();

    // === Actions ===

    /**
     * Register a combat action and return the result.
     *
     * @param action The type of action performed
     * @param damage The damage dealt (for scaling)
     * @return Result containing points earned and state changes
     */
    ActionResult registerAction(ActionType action, float damage);

    /**
     * Register a kill and return the result.
     *
     * @param wasQuick Whether the kill was fast (< 5 seconds)
     * @param overkillDamage Extra damage beyond killing blow
     * @return Result containing points earned and state changes
     */
    ActionResult registerKill(boolean wasQuick, float overkillDamage);

    /**
     * Called when player takes damage.
     * Applies combo and style penalties.
     *
     * @param damage Amount of damage taken
     */
    void onDamageTaken(float damage);

    // === Wave Management ===

    /**
     * Start a new wave. Resets wave-specific tracking.
     */
    void startNewWave();

    /**
     * Add bonus points directly (for wave completion bonuses, etc.)
     *
     * @param points Points to add
     */
    void addBonusPoints(int points);

    // === Lifecycle ===

    /**
     * Process tick for decay and timeout logic.
     * Should be called every server tick.
     */
    void tick();

    /**
     * Get final calculated score with all multipliers.
     */
    int getFinalScore();

    // === Combat Stats (Read-only) ===

    /**
     * Get combat statistics for this session.
     */
    CombatStats getStats();

    /**
     * Record containing combat statistics.
     */
    record CombatStats(
        int totalHits,
        int totalKills,
        float totalDamage,
        int perfectDodges,
        int parries,
        int counterAttacks
    ) {}

    /**
     * Result of registering an action.
     */
    record ActionResult(
        int pointsEarned,
        int styleEarned,
        int currentCombo,
        StyleRank currentRank,
        ActionAnnouncement announcement
    ) {}

    /**
     * Announcement to display to player.
     */
    record ActionAnnouncement(
        ActionType action,
        int styleEarned,
        @Nullable StyleRank newRank,
        long timestamp
    ) {
        public boolean isRankUp() {
            return newRank != null;
        }
    }
}
