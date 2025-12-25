package com.devmod.endurance.bargain;

/**
 * Curse definitions for Devil's Bargain system.
 *
 * Each curse provides a difficulty modifier in exchange for reward multipliers.
 * Curses are designed to stack, creating increasingly challenging (and rewarding) runs.
 *
 * Design Philosophy:
 * - Minor curses (1.1x-1.25x reward) are manageable but annoying
 * - Major curses (1.5x-2.0x reward) significantly change gameplay
 * - Cursed curses (2.5x+ reward) are run-ending if not respected
 */
public enum Curse {
    // === MINOR CURSES (Tier 1) - Manageable debuffs ===

    GLASS_CANNON(
        "Glass Cannon",
        "Take 50% more damage, deal 25% more damage",
        CurseTier.MINOR,
        1.15f,
        0xFF6B6B,
        "Your attacks hit harder, but so do theirs..."
    ),

    SLUGGISH(
        "Sluggish",
        "Movement speed reduced by 20%",
        CurseTier.MINOR,
        1.1f,
        0x888888,
        "Your legs feel heavy as lead..."
    ),

    FUMBLING(
        "Fumbling",
        "Attack speed reduced by 15%",
        CurseTier.MINOR,
        1.1f,
        0xFFAA00,
        "Your weapons feel unwieldy..."
    ),

    HUNGER(
        "Insatiable Hunger",
        "Drain 1 hunger every 10 seconds",
        CurseTier.MINOR,
        1.15f,
        0x8B4513,
        "A gnawing emptiness consumes you..."
    ),

    ECHO_DAMAGE(
        "Echo of Pain",
        "Take 10% of damage dealt as self-damage",
        CurseTier.MINOR,
        1.2f,
        0x9932CC,
        "Every strike echoes through your soul..."
    ),

    // === MAJOR CURSES (Tier 2) - Significant gameplay changes ===

    BLOOD_TITHE(
        "Blood Tithe",
        "Lose 1 HP per kill, gain 2% lifesteal",
        CurseTier.MAJOR,
        1.35f,
        0xDC143C,
        "The altar demands blood for blood..."
    ),

    CROWD_PRESSURE(
        "Crowd Pressure",
        "+30% mob spawn rate",
        CurseTier.MAJOR,
        1.4f,
        0x4B0082,
        "They sense your weakness and gather..."
    ),

    FRAILTY(
        "Frailty",
        "Max HP reduced by 30%",
        CurseTier.MAJOR,
        1.5f,
        0x708090,
        "Your body withers at the altar's touch..."
    ),

    BURNING_SOUL(
        "Burning Soul",
        "Periodically set on fire (every 30 sec)",
        CurseTier.MAJOR,
        1.35f,
        0xFF4500,
        "Flames flicker within your chest..."
    ),

    COMBO_BREAKER(
        "Combo Breaker",
        "Combo resets if you don't hit for 2 seconds",
        CurseTier.MAJOR,
        1.45f,
        0xFFD700,
        "Hesitation means death to your rhythm..."
    ),

    MOMENTUM_DRAIN(
        "Momentum Drain",
        "Momentum decays 3x faster",
        CurseTier.MAJOR,
        1.4f,
        0x00CED1,
        "Your energy bleeds into the void..."
    ),

    // === CURSED CURSES (Tier 3) - Run-defining challenges ===

    ONE_SHOT(
        "One Shot",
        "Any hit that deals >50% max HP is instant death",
        CurseTier.CURSED,
        2.0f,
        0x000000,
        "Death waits in every shadow..."
    ),

    ELITE_HUNTER(
        "Elite Hunter",
        "All mobs are elite variants (+50% stats)",
        CurseTier.CURSED,
        2.5f,
        0x8A2BE2,
        "Only the strongest dare face you now..."
    ),

    NO_HEALING(
        "Sealed Wounds",
        "Cannot regenerate HP by any means",
        CurseTier.CURSED,
        2.0f,
        0x2F4F4F,
        "Your wounds refuse to close..."
    ),

    EXECUTIONER(
        "Executioner's Mark",
        "Mobs below 25% HP become enraged (+100% damage)",
        CurseTier.CURSED,
        1.75f,
        0xB22222,
        "The dying fight hardest..."
    );

    public final String displayName;
    public final String description;
    public final CurseTier tier;
    public final float rewardMultiplier;
    public final int color;
    public final String flavorText;

    Curse(String displayName, String description, CurseTier tier,
          float rewardMultiplier, int color, String flavorText) {
        this.displayName = displayName;
        this.description = description;
        this.tier = tier;
        this.rewardMultiplier = rewardMultiplier;
        this.color = color;
        this.flavorText = flavorText;
    }

    /**
     * Curse tiers determine how impactful the curse is.
     */
    public enum CurseTier {
        MINOR("Minor", 0x55FF55, 1),
        MAJOR("Major", 0xFFAA00, 2),
        CURSED("Cursed", 0xFF5555, 3);

        public final String displayName;
        public final int color;
        public final int weight; // For random selection weighting

        CurseTier(String displayName, int color, int weight) {
            this.displayName = displayName;
            this.color = color;
            this.weight = weight;
        }
    }

    /**
     * Get a formatted display string with tier color.
     */
    public String getFormattedName() {
        String tierColor = switch (tier) {
            case MINOR -> "§a";
            case MAJOR -> "§6";
            case CURSED -> "§c§l";
        };
        return tierColor + displayName;
    }

    /**
     * Check if this curse conflicts with another (some shouldn't stack).
     */
    public boolean conflictsWith(Curse other) {
        // ONE_SHOT and FRAILTY together would be too punishing
        if (this == ONE_SHOT && other == FRAILTY) return true;
        if (this == FRAILTY && other == ONE_SHOT) return true;

        // NO_HEALING and BLOOD_TITHE don't make sense together
        if (this == NO_HEALING && other == BLOOD_TITHE) return true;
        if (this == BLOOD_TITHE && other == NO_HEALING) return true;

        return false;
    }
}
