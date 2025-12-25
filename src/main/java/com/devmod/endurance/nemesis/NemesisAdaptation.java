package com.devmod.endurance.nemesis;

/**
 * Nemesis Adaptation - Abilities bosses unlock based on player combat patterns.
 *
 * Each adaptation changes how the boss fights, making repeat encounters
 * progressively more challenging.
 */
public enum NemesisAdaptation {

    // ========== Combat Pattern Adaptations ==========

    /**
     * 30% chance to deflect projectiles back at the player.
     * Triggered when: Player uses 70%+ ranged attacks.
     */
    PROJECTILE_DEFLECTION(
        "Projectile Deflection",
        "Boss has a 30% chance to deflect ranged attacks",
        0x55AAFF,
        AdaptationType.DEFENSE
    ),

    /**
     * Boss leads attacks toward player's preferred dodge direction.
     * Adds a sweeping attack that covers more area.
     * Triggered when: Player consistently dodges in one direction.
     */
    SWEEPING_BLADE(
        "Sweeping Blade",
        "Boss anticipates dodge patterns with wide sweeping attacks",
        0xFF5555,
        AdaptationType.OFFENSE
    ),

    /**
     * Boss starts with phase 1 abilities already active.
     * Reduces phase transition time by 50%.
     * Triggered when: Player kills phases in under 30 seconds average.
     */
    EARLY_PHASE_ACTIVATION(
        "Early Activation",
        "Boss starts the fight more prepared, with reduced phase times",
        0xFFAA00,
        AdaptationType.UTILITY
    ),

    /**
     * 25% resistance to the player's most-used damage type.
     * Triggered when: Player uses one weapon type for 60%+ of hits.
     */
    DAMAGE_RESISTANCE(
        "Damage Resistance",
        "Boss has developed resistance to frequently used attack types",
        0x888888,
        AdaptationType.DEFENSE
    ),

    /**
     * Head hitbox reduced by 30%, making headshots harder.
     * Triggered when: Player hits 30%+ headshots.
     */
    PROTECTIVE_HELMET(
        "Protective Helmet",
        "Boss protects weak points, reducing headshot effectiveness",
        0xAA8800,
        AdaptationType.DEFENSE
    ),

    // ========== Defeat Count Adaptations ==========

    /**
     * 15% faster attack speed and movement.
     * Triggered at: 3+ defeats.
     */
    IMPROVED_REFLEXES(
        "Improved Reflexes",
        "Boss moves and attacks 15% faster",
        0x55FF55,
        AdaptationType.UTILITY
    ),

    /**
     * +20% damage, +10% attack speed when below 50% health.
     * Triggered at: 5+ defeats.
     */
    ENRAGED(
        "Enraged",
        "Boss enters a fury state when wounded, dealing more damage",
        0xFF0000,
        AdaptationType.OFFENSE
    ),

    /**
     * +15% all stats, gains a special attack.
     * Triggered at: 10+ defeats.
     */
    VETERAN(
        "Veteran",
        "Battle-hardened boss with enhanced abilities and a signature move",
        0xAA00AA,
        AdaptationType.SPECIAL
    ),

    // ========== Special Adaptations ==========

    /**
     * Boss can dodge one attack every 10 seconds.
     * Earned through special conditions.
     */
    EVASION(
        "Evasion",
        "Boss can occasionally dodge attacks entirely",
        0x00FFFF,
        AdaptationType.DEFENSE
    ),

    /**
     * Boss regenerates 1% health per second when out of combat.
     * Earned through special conditions.
     */
    REGENERATION(
        "Regeneration",
        "Boss slowly heals when not taking damage",
        0x00FF00,
        AdaptationType.UTILITY
    ),

    /**
     * Boss summons minions when health drops to 75%, 50%, 25%.
     * Earned through special conditions.
     */
    SUMMONER(
        "Summoner",
        "Boss calls reinforcements when wounded",
        0x8800AA,
        AdaptationType.SPECIAL
    );

    public final String displayName;
    public final String description;
    public final int color;
    public final AdaptationType type;

    NemesisAdaptation(String displayName, String description, int color, AdaptationType type) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.type = type;
    }

    /**
     * Get the stat modifier provided by this adaptation.
     */
    public float getStatModifier(StatType stat) {
        return switch (this) {
            case PROJECTILE_DEFLECTION -> stat == StatType.PROJECTILE_RESIST ? 0.30f : 0f;
            case DAMAGE_RESISTANCE -> stat == StatType.DAMAGE_RESIST ? 0.25f : 0f;
            case PROTECTIVE_HELMET -> stat == StatType.HEAD_DAMAGE_RESIST ? 0.30f : 0f;
            case IMPROVED_REFLEXES -> switch (stat) {
                case ATTACK_SPEED -> 0.15f;
                case MOVEMENT_SPEED -> 0.15f;
                default -> 0f;
            };
            case ENRAGED -> stat == StatType.DAMAGE ? 0.20f : 0f; // Only when below 50% HP
            case VETERAN -> switch (stat) {
                case DAMAGE -> 0.15f;
                case HEALTH -> 0.15f;
                case ATTACK_SPEED -> 0.15f;
                default -> 0f;
            };
            case REGENERATION -> stat == StatType.REGEN ? 0.01f : 0f; // 1% per second
            default -> 0f;
        };
    }

    public enum AdaptationType {
        OFFENSE,    // Increases damage output
        DEFENSE,    // Reduces damage taken
        UTILITY,    // Movement, phase timing, etc.
        SPECIAL     // Unique mechanics
    }

    public enum StatType {
        DAMAGE,
        HEALTH,
        ATTACK_SPEED,
        MOVEMENT_SPEED,
        PROJECTILE_RESIST,
        DAMAGE_RESIST,
        HEAD_DAMAGE_RESIST,
        REGEN
    }
}
