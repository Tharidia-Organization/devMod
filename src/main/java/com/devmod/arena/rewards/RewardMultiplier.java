package com.devmod.arena.rewards;

/**
 * Reward Multiplier implementing DD53: Reward Multipliers.
 *
 * <p>Key features:
 * <ul>
 *   <li>Formula: weight * 0.05 + 1.0</li>
 *   <li>Bounds: [0.5, 2.0]</li>
 *   <li>Anti-exploit measures</li>
 * </ul>
 */
public record RewardMultiplier(
    double multiplier,
    double originalWeight,
    boolean wasClamped,
    ClampDirection clampDirection
) {

    /**
     * Direction of clamping applied.
     */
    public enum ClampDirection {
        NONE,
        LOWER,
        UPPER
    }

    /**
     * Minimum allowed multiplier (DD53).
     */
    public static final double MIN_MULTIPLIER = 0.5;

    /**
     * Maximum allowed multiplier (DD53).
     */
    public static final double MAX_MULTIPLIER = 2.0;

    /**
     * Weight factor in formula (DD53: weight * 0.05 + 1.0).
     */
    public static final double WEIGHT_FACTOR = 0.05;

    /**
     * Base value in formula (DD53: weight * 0.05 + 1.0).
     */
    public static final double BASE_VALUE = 1.0;

    /**
     * Creates a RewardMultiplier from a weight value.
     * Implements DD53: weight * 0.05 + 1.0, bounds [0.5, 2.0].
     *
     * @param weight the weight value to convert
     * @return RewardMultiplier with calculated and clamped multiplier
     */
    public static RewardMultiplier fromWeight(double weight) {
        double calculated = weight * WEIGHT_FACTOR + BASE_VALUE;

        ClampDirection direction = ClampDirection.NONE;
        double clamped = calculated;
        boolean wasClamped = false;

        if (calculated < MIN_MULTIPLIER) {
            clamped = MIN_MULTIPLIER;
            direction = ClampDirection.LOWER;
            wasClamped = true;
        } else if (calculated > MAX_MULTIPLIER) {
            clamped = MAX_MULTIPLIER;
            direction = ClampDirection.UPPER;
            wasClamped = true;
        }

        return new RewardMultiplier(clamped, weight, wasClamped, direction);
    }

    /**
     * Creates a RewardMultiplier with a direct multiplier value.
     * The multiplier will be clamped to bounds [0.5, 2.0].
     *
     * @param multiplier the direct multiplier value
     * @return RewardMultiplier with clamped value
     */
    public static RewardMultiplier fromMultiplier(double multiplier) {
        ClampDirection direction = ClampDirection.NONE;
        double clamped = multiplier;
        boolean wasClamped = false;

        if (multiplier < MIN_MULTIPLIER) {
            clamped = MIN_MULTIPLIER;
            direction = ClampDirection.LOWER;
            wasClamped = true;
        } else if (multiplier > MAX_MULTIPLIER) {
            clamped = MAX_MULTIPLIER;
            direction = ClampDirection.UPPER;
            wasClamped = true;
        }

        // Calculate the original weight that would produce this multiplier
        double originalWeight = (multiplier - BASE_VALUE) / WEIGHT_FACTOR;

        return new RewardMultiplier(clamped, originalWeight, wasClamped, direction);
    }

    /**
     * Returns a neutral (1.0) multiplier.
     *
     * @return RewardMultiplier with 1.0 value
     */
    public static RewardMultiplier neutral() {
        return new RewardMultiplier(1.0, 0.0, false, ClampDirection.NONE);
    }

    /**
     * Returns the minimum allowed multiplier.
     *
     * @return RewardMultiplier with 0.5 value
     */
    public static RewardMultiplier minimum() {
        return new RewardMultiplier(MIN_MULTIPLIER, -10.0, false, ClampDirection.NONE);
    }

    /**
     * Returns the maximum allowed multiplier.
     *
     * @return RewardMultiplier with 2.0 value
     */
    public static RewardMultiplier maximum() {
        return new RewardMultiplier(MAX_MULTIPLIER, 20.0, false, ClampDirection.NONE);
    }

    /**
     * Applies this multiplier to a base reward amount.
     *
     * @param baseAmount the base reward amount
     * @return the multiplied amount
     */
    public long apply(long baseAmount) {
        return Math.round(baseAmount * multiplier);
    }

    /**
     * Applies this multiplier to a base reward amount (double version).
     *
     * @param baseAmount the base reward amount
     * @return the multiplied amount
     */
    public double apply(double baseAmount) {
        return baseAmount * multiplier;
    }

    /**
     * Combines this multiplier with another (multiplicative).
     * Result is clamped to bounds [0.5, 2.0].
     *
     * @param other the other multiplier to combine with
     * @return combined RewardMultiplier
     */
    public RewardMultiplier combine(RewardMultiplier other) {
        double combined = this.multiplier * other.multiplier;
        return fromMultiplier(combined);
    }

    /**
     * Checks if this multiplier is within valid bounds.
     *
     * @return true if within [0.5, 2.0]
     */
    public boolean isValid() {
        return multiplier >= MIN_MULTIPLIER && multiplier <= MAX_MULTIPLIER;
    }

    /**
     * Returns the weight that would produce this exact multiplier
     * (before any clamping).
     *
     * @return the calculated weight
     */
    public double toWeight() {
        return (multiplier - BASE_VALUE) / WEIGHT_FACTOR;
    }

    /**
     * Returns a human-readable percentage representation.
     *
     * @return string like "+50%", "-25%", or "+0%"
     */
    public String toPercentageString() {
        int percentage = (int) Math.round((multiplier - 1.0) * 100);
        if (percentage >= 0) {
            return "+" + percentage + "%";
        }
        return percentage + "%";
    }
}
