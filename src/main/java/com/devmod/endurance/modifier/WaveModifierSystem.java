package com.devmod.endurance.modifier;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P1-002: Extracted wave modifier system from WaveManager.
 *
 * Manages roguelike wave modifiers that add variety and challenge to waves.
 * Modifiers are randomly applied based on wave number and tier thresholds.
 */
public class WaveModifierSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaveModifierSystem.class);

    public static final WaveModifierSystem INSTANCE = new WaveModifierSystem();

    // Configuration constants
    private static final int MODIFIER_START_WAVE = 3;
    private static final int MAX_MODIFIERS_EARLY = 1;  // Waves 3-4
    private static final int MAX_MODIFIERS_MID = 2;    // Waves 5-7
    private static final int MAX_MODIFIERS_LATE = 3;   // Waves 8+

    // Modifier chance per tier
    private static final float MODIFIER_CHANCE_TIER_1 = 0.10f;  // Waves 3-4
    private static final float MODIFIER_CHANCE_TIER_2 = 0.20f;  // Waves 5-7
    private static final float MODIFIER_CHANCE_TIER_3 = 0.35f;  // Waves 8+

    private final Random random = new Random();

    private WaveModifierSystem() {}

    /**
     * Wave modifiers that can be applied to waves.
     * Each modifier provides a unique challenge or buff to enemies.
     */
    public enum Modifier {
        SPEED_BOOST("Swift", "Mobs move 25% faster"),
        DAMAGE_BOOST("Empowered", "Mobs deal 25% more damage"),
        HEALTH_BOOST("Fortified", "Mobs have 50% more health"),
        ARMOR_BOOST("Armored", "Mobs have increased armor"),
        FIRE_ASPECT("Blazing", "Mobs inflict fire on hit"),
        INVISIBILITY("Phantom", "Mobs are partially invisible"),
        REGEN("Regenerating", "Mobs slowly regenerate health"),
        DOUBLE_SPAWN("Horde", "Double the number of mobs");

        private final String displayName;
        private final String description;

        Modifier(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }

        /**
         * Returns the multiplier this modifier applies to mob count.
         * Only DOUBLE_SPAWN affects mob count.
         */
        public int getMobCountMultiplier() {
            return this == DOUBLE_SPAWN ? 2 : 1;
        }

        /**
         * Check if this modifier affects spawning (count) vs stats.
         */
        public boolean isSpawnModifier() {
            return this == DOUBLE_SPAWN;
        }

        /**
         * Check if this modifier should be applied via effects.
         */
        public boolean isEffectBased() {
            return this == INVISIBILITY || this == REGEN;
        }

        /**
         * Check if this modifier modifies mob attributes.
         */
        public boolean isAttributeModifier() {
            return this == SPEED_BOOST || this == DAMAGE_BOOST ||
                   this == HEALTH_BOOST || this == ARMOR_BOOST;
        }

        /**
         * Check if this modifier needs special handling in combat.
         */
        public boolean isCombatModifier() {
            return this == FIRE_ASPECT;
        }
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Roll random modifiers for a wave based on wave number.
     * Earlier waves have fewer and less frequent modifiers.
     *
     * @param waveNumber The current wave number
     * @return Set of modifiers to apply to this wave (may be empty)
     */
    public Set<Modifier> rollModifiers(int waveNumber) {
        if (waveNumber < MODIFIER_START_WAVE) {
            return Set.of();
        }

        float modifierChance = getModifierChance(waveNumber);
        int maxModifiers = getMaxModifiers(waveNumber);
        Set<Modifier> modifiers = new HashSet<>();

        for (Modifier modifier : Modifier.values()) {
            if (random.nextFloat() < modifierChance) {
                modifiers.add(modifier);

                if (modifiers.size() >= maxModifiers) {
                    break;
                }
            }
        }

        if (!modifiers.isEmpty()) {
            LOGGER.debug("[WaveModifier] Wave {} rolled modifiers: {}", waveNumber, modifiers);
        }

        return modifiers;
    }

    /**
     * Calculate the modifier chance based on wave number tier.
     *
     * @param waveNumber The current wave number
     * @return Probability (0.0 to 1.0) of each modifier being applied
     */
    public float getModifierChance(int waveNumber) {
        if (waveNumber < MODIFIER_START_WAVE) {
            return 0f;
        }
        if (waveNumber < 5) {
            return MODIFIER_CHANCE_TIER_1;
        }
        if (waveNumber < 8) {
            return MODIFIER_CHANCE_TIER_2;
        }
        return MODIFIER_CHANCE_TIER_3;
    }

    /**
     * Calculate the maximum number of modifiers for a wave.
     *
     * @param waveNumber The current wave number
     * @return Maximum number of modifiers allowed
     */
    public int getMaxModifiers(int waveNumber) {
        if (waveNumber < MODIFIER_START_WAVE) {
            return 0;
        }
        if (waveNumber < 5) {
            return MAX_MODIFIERS_EARLY;
        }
        if (waveNumber < 8) {
            return MAX_MODIFIERS_MID;
        }
        return MAX_MODIFIERS_LATE;
    }

    /**
     * Check if modifiers can be applied at this wave number.
     *
     * @param waveNumber The current wave number
     * @return true if modifiers can be rolled
     */
    public boolean canHaveModifiers(int waveNumber) {
        return waveNumber >= MODIFIER_START_WAVE;
    }

    /**
     * Get the wave number at which modifiers start appearing.
     *
     * @return First wave that can have modifiers
     */
    public int getModifierStartWave() {
        return MODIFIER_START_WAVE;
    }

    /**
     * Calculate total mob count multiplier from a set of modifiers.
     *
     * @param modifiers Set of active modifiers
     * @return Multiplier to apply to base mob count
     */
    public int calculateMobCountMultiplier(Set<Modifier> modifiers) {
        int multiplier = 1;
        for (Modifier modifier : modifiers) {
            multiplier *= modifier.getMobCountMultiplier();
        }
        return multiplier;
    }

    /**
     * Get a human-readable summary of active modifiers.
     *
     * @param modifiers Set of active modifiers
     * @return Formatted string with modifier names
     */
    public String formatModifiers(Set<Modifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return "None";
        }
        StringBuilder sb = new StringBuilder();
        for (Modifier modifier : modifiers) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(modifier.displayName);
        }
        return sb.toString();
    }

    // =========================================================================
    // MODIFIER CONVERSION (for WaveManager compatibility)
    // =========================================================================

    /**
     * Convert from WaveManager.WaveModifier to WaveModifierSystem.Modifier.
     * Used during transition period while WaveManager still uses its enum.
     */
    public static Modifier fromWaveManagerModifier(com.devmod.endurance.WaveManager.WaveModifier wmModifier) {
        return switch (wmModifier) {
            case SPEED_BOOST -> Modifier.SPEED_BOOST;
            case DAMAGE_BOOST -> Modifier.DAMAGE_BOOST;
            case HEALTH_BOOST -> Modifier.HEALTH_BOOST;
            case ARMOR_BOOST -> Modifier.ARMOR_BOOST;
            case FIRE_ASPECT -> Modifier.FIRE_ASPECT;
            case INVISIBILITY -> Modifier.INVISIBILITY;
            case REGEN -> Modifier.REGEN;
            case DOUBLE_SPAWN -> Modifier.DOUBLE_SPAWN;
        };
    }

    /**
     * Convert from WaveModifierSystem.Modifier to WaveManager.WaveModifier.
     * Used during transition period while WaveManager still uses its enum.
     */
    public static com.devmod.endurance.WaveManager.WaveModifier toWaveManagerModifier(Modifier modifier) {
        return switch (modifier) {
            case SPEED_BOOST -> com.devmod.endurance.WaveManager.WaveModifier.SPEED_BOOST;
            case DAMAGE_BOOST -> com.devmod.endurance.WaveManager.WaveModifier.DAMAGE_BOOST;
            case HEALTH_BOOST -> com.devmod.endurance.WaveManager.WaveModifier.HEALTH_BOOST;
            case ARMOR_BOOST -> com.devmod.endurance.WaveManager.WaveModifier.ARMOR_BOOST;
            case FIRE_ASPECT -> com.devmod.endurance.WaveManager.WaveModifier.FIRE_ASPECT;
            case INVISIBILITY -> com.devmod.endurance.WaveManager.WaveModifier.INVISIBILITY;
            case REGEN -> com.devmod.endurance.WaveManager.WaveModifier.REGEN;
            case DOUBLE_SPAWN -> com.devmod.endurance.WaveManager.WaveModifier.DOUBLE_SPAWN;
        };
    }
}
