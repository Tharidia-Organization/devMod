package com.devmod.foundry.tool.material;

/**
 * Melting parameters for a material (temperature range and impurity base).
 */
public record FoundryMaterialMelting(
    int temperature,
    int optimalLow,
    int optimalHigh,
    float impurityBase
) {
    public boolean hasOptimalRange() {
        return optimalLow > 0 && optimalHigh > 0;
    }

    public int getOptimalLow(int fallback) {
        return hasOptimalRange() ? optimalLow : fallback;
    }

    public int getOptimalHigh(int fallback) {
        return hasOptimalRange() ? optimalHigh : fallback;
    }

    public float getBasePurity() {
        return clamp(1.0f - impurityBase);
    }

    private static float clamp(float value) {
        return Math.max(0.1f, Math.min(1.0f, value));
    }
}
