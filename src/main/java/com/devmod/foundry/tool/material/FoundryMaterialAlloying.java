package com.devmod.foundry.tool.material;

/**
 * Alloying properties for a material.
 * When this material is used in an alloying recipe, these bonuses apply.
 */
public record FoundryMaterialAlloying(
    float ratioTolerance,  // How much ratio requirements are relaxed (0.0-0.2 typical)
    float purityBonus,     // Bonus purity when alloying (0.0-0.15 typical)
    float efficiency,      // Output multiplier (1.0 = 100%, 1.1 = 110%)
    float speedMultiplier  // Alloying speed multiplier (1.0 = normal, 0.8 = 20% faster)
) {
    public static final FoundryMaterialAlloying NONE = new FoundryMaterialAlloying(0f, 0f, 1f, 1f);

    public FoundryMaterialAlloying {
        ratioTolerance = Math.max(0f, Math.min(0.3f, ratioTolerance));
        purityBonus = Math.max(-0.1f, Math.min(0.2f, purityBonus));
        efficiency = Math.max(0.5f, Math.min(1.5f, efficiency));
        speedMultiplier = Math.max(0.5f, Math.min(2f, speedMultiplier));
    }

    public boolean hasBonus() {
        return ratioTolerance > 0 || purityBonus > 0 || efficiency != 1f || speedMultiplier != 1f;
    }
}
