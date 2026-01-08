package com.devmod.endurance.nutrition;

import java.util.Arrays;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Tracks nutrition state for a player during an Endurance quest.
 *
 * <p>This session captures the player's nutritional values at quest start
 * and tracks changes throughout the quest, calculating combat modifiers
 * based on nutrition balance.
 *
 * <p>Combat modifiers based on nutrition state:
 * <ul>
 *   <li><b>Well-fed (all > 60%):</b> +15% damage, +10% speed, +20% damage reduction</li>
 *   <li><b>Malnourished (any < 20%):</b> -25% damage, -15% speed, +50% damage taken</li>
 *   <li><b>Critical (any < 10%):</b> Health drain, no executions, +50% combo decay</li>
 * </ul>
 *
 * @author DevMod Team
 */
public class NutritionSession {

    // =========================================================================
    // THRESHOLDS
    // =========================================================================

    /** Threshold for well-fed buff (all categories must be above). */
    public static final float WELL_FED_THRESHOLD = 0.6f;

    /** Threshold for malnourished debuff (any category below triggers). */
    public static final float MALNOURISHED_THRESHOLD = 0.2f;

    /** Threshold for critical state (any category below triggers). */
    public static final float CRITICAL_THRESHOLD = 0.1f;

    // =========================================================================
    // BUFF/DEBUFF VALUES
    // =========================================================================

    /** Damage multiplier when well-fed. */
    public static final float WELL_FED_DAMAGE_BONUS = 0.15f;

    /** Speed multiplier when well-fed. */
    public static final float WELL_FED_SPEED_BONUS = 0.10f;

    /** Damage reduction when well-fed. */
    public static final float WELL_FED_DAMAGE_REDUCTION = 0.20f;

    /** Health regen per second when well-fed. */
    public static final float WELL_FED_REGEN_PER_SEC = 0.5f;

    /** Damage penalty when malnourished. */
    public static final float MALNOURISHED_DAMAGE_PENALTY = 0.25f;

    /** Speed penalty when malnourished. */
    public static final float MALNOURISHED_SPEED_PENALTY = 0.15f;

    /** Extra damage taken when malnourished. */
    public static final float MALNOURISHED_DAMAGE_TAKEN_INCREASE = 0.50f;

    /** Health drain per second when critical. */
    public static final float CRITICAL_HEALTH_DRAIN_PER_SEC = 0.33f;

    /** Combo decay multiplier when critical. */
    public static final float CRITICAL_COMBO_DECAY_MULTIPLIER = 1.5f;

    // =========================================================================
    // STATE
    // =========================================================================

    private final UUID playerId;
    private final UUID questId;
    private final long startTimeMs;

    /** Snapshot of diet values at quest start. */
    private final float[] snapshotValues;

    /** Current diet values (updated periodically). */
    private final float[] currentValues;

    /** Last update tick. */
    private long lastUpdateTick;

    /** Cached state flags. */
    private boolean wellFed;
    private boolean malnourished;
    private boolean critical;
    private float balance;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * Create a new nutrition session.
     *
     * @param playerId the player UUID
     * @param questId the quest UUID
     * @param initialValues initial diet values (6 categories)
     */
    public NutritionSession(@Nonnull UUID playerId, @Nonnull UUID questId, float[] initialValues) {
        this.playerId = playerId;
        this.questId = questId;
        this.startTimeMs = System.currentTimeMillis();
        this.lastUpdateTick = 0;

        // Copy initial values
        this.snapshotValues = new float[NutritionCategory.COUNT];
        this.currentValues = new float[NutritionCategory.COUNT];

        if (initialValues != null && initialValues.length >= NutritionCategory.COUNT) {
            System.arraycopy(initialValues, 0, snapshotValues, 0, NutritionCategory.COUNT);
            System.arraycopy(initialValues, 0, currentValues, 0, NutritionCategory.COUNT);
        } else {
            // Default to 50% (neutral - no buff/debuff)
            Arrays.fill(snapshotValues, 0.5f);
            Arrays.fill(currentValues, 0.5f);
        }

        updateCachedState();
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getQuestId() {
        return questId;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    /**
     * Get the last update tick.
     *
     * @return game tick of last update, or 0 if never updated
     */
    public long getLastUpdateTick() {
        return lastUpdateTick;
    }

    public float[] getSnapshotValues() {
        return Arrays.copyOf(snapshotValues, snapshotValues.length);
    }

    public float[] getCurrentValues() {
        return Arrays.copyOf(currentValues, currentValues.length);
    }

    public float getValue(NutritionCategory category) {
        return currentValues[category.index];
    }

    public float getValue(int categoryIndex) {
        if (categoryIndex < 0 || categoryIndex >= NutritionCategory.COUNT) {
            return 0f;
        }
        return currentValues[categoryIndex];
    }

    // =========================================================================
    // STATE UPDATE
    // =========================================================================

    /**
     * Update current values from Easy-Diet.
     *
     * @param newValues new diet values
     * @param currentTick current game tick
     */
    public void updateValues(float[] newValues, long currentTick) {
        if (newValues != null && newValues.length >= NutritionCategory.COUNT) {
            System.arraycopy(newValues, 0, currentValues, 0, NutritionCategory.COUNT);
            lastUpdateTick = currentTick;
            updateCachedState();
        }
    }

    /**
     * Recalculate cached state flags.
     */
    private void updateCachedState() {
        wellFed = true;
        malnourished = false;
        critical = false;
        float sum = 0;

        for (float v : currentValues) {
            sum += v;
            if (v < WELL_FED_THRESHOLD) {
                wellFed = false;
            }
            if (v < MALNOURISHED_THRESHOLD) {
                malnourished = true;
            }
            if (v < CRITICAL_THRESHOLD) {
                critical = true;
            }
        }

        balance = sum / NutritionCategory.COUNT;
    }

    // =========================================================================
    // STATE QUERIES
    // =========================================================================

    /**
     * Check if player is well-fed.
     *
     * @return true if all categories >= 60%
     */
    public boolean isWellFed() {
        return wellFed;
    }

    /**
     * Check if player is malnourished.
     *
     * @return true if any category < 20%
     */
    public boolean isMalnourished() {
        return malnourished;
    }

    /**
     * Check if player is in critical state.
     *
     * @return true if any category < 10%
     */
    public boolean isCritical() {
        return critical;
    }

    /**
     * Get overall nutrition balance.
     *
     * @return average value (0.0 to 1.0)
     */
    public float getBalance() {
        return balance;
    }

    /**
     * Get the lowest category value.
     *
     * @return minimum value across all categories
     */
    public float getLowestValue() {
        float min = Float.MAX_VALUE;
        for (float v : currentValues) {
            if (v < min) {
                min = v;
            }
        }
        return min;
    }

    /**
     * Get the category with the lowest value.
     *
     * @return the weakest category
     */
    public NutritionCategory getLowestCategory() {
        int minIdx = 0;
        float min = currentValues[0];
        for (int i = 1; i < currentValues.length; i++) {
            if (currentValues[i] < min) {
                min = currentValues[i];
                minIdx = i;
            }
        }
        NutritionCategory category = NutritionCategory.byIndex(minIdx);
        return category != null ? category : NutritionCategory.GRAIN;
    }

    // =========================================================================
    // COMBAT MODIFIERS
    // =========================================================================

    /**
     * Get damage multiplier based on nutrition state.
     *
     * @return multiplier (1.0 = no change, > 1.0 = bonus, < 1.0 = penalty)
     */
    public float getDamageMultiplier() {
        if (critical || malnourished) {
            return 1.0f - MALNOURISHED_DAMAGE_PENALTY;
        }
        if (wellFed) {
            return 1.0f + WELL_FED_DAMAGE_BONUS;
        }
        return 1.0f;
    }

    /**
     * Get damage reduction based on nutrition state.
     *
     * @return reduction factor (0.0 = no reduction, 0.2 = 20% reduction)
     */
    public float getDamageReduction() {
        if (critical || malnourished) {
            return -MALNOURISHED_DAMAGE_TAKEN_INCREASE; // Negative = more damage taken
        }
        if (wellFed) {
            return WELL_FED_DAMAGE_REDUCTION;
        }
        return 0f;
    }

    /**
     * Get speed multiplier based on nutrition state.
     *
     * @return multiplier (1.0 = no change)
     */
    public float getSpeedMultiplier() {
        if (critical || malnourished) {
            return 1.0f - MALNOURISHED_SPEED_PENALTY;
        }
        if (wellFed) {
            return 1.0f + WELL_FED_SPEED_BONUS;
        }
        return 1.0f;
    }

    /**
     * Check if healing is allowed.
     *
     * @return false if malnourished or critical
     */
    public boolean isHealingAllowed() {
        return !malnourished && !critical;
    }

    /**
     * Check if executions are allowed.
     *
     * @return false if critical
     */
    public boolean canPerformExecutions() {
        return !critical;
    }

    /**
     * Get health change per second based on nutrition.
     *
     * @return positive = regen, negative = drain
     */
    public float getHealthChangePerSecond() {
        if (critical) {
            return -CRITICAL_HEALTH_DRAIN_PER_SEC;
        }
        if (wellFed) {
            return WELL_FED_REGEN_PER_SEC;
        }
        return 0f;
    }

    /**
     * Get combo decay multiplier.
     *
     * @return multiplier (> 1.0 = faster decay)
     */
    public float getComboDecayMultiplier() {
        if (critical) {
            return CRITICAL_COMBO_DECAY_MULTIPLIER;
        }
        return 1.0f;
    }

    // =========================================================================
    // DEBUG
    // =========================================================================

    @Override
    public String toString() {
        return String.format("NutritionSession[player=%s, quest=%s, balance=%.1f%%, wellFed=%s, malnourished=%s, critical=%s]",
            playerId.toString().substring(0, 8),
            questId.toString().substring(0, 8),
            balance * 100,
            wellFed, malnourished, critical);
    }
}
