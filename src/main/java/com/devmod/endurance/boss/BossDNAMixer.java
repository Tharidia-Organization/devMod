package com.devmod.endurance.boss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.endurance.BossWaveSystem.BossAbility;
import com.devmod.endurance.BossWaveSystem.BossArchetype;
import com.devmod.endurance.BossWaveSystem.BossFight;

/**
 * Boss DNA Mixing System - Dynamic Boss Generation.
 *
 * Creates unique bosses by mixing archetypes:
 * - 70% primary archetype (stats, main abilities)
 * - 30% secondary archetype (1-2 inherited abilities, color blend)
 *
 * RARE VARIANTS:
 * - CHIMERA (5%): 3 archetypes mixed, all abilities available
 * - MIRROR (3%): Copies player's equipped gear stats
 * - EVOLVING (2%): Changes archetype at each phase transition
 *
 * Design Philosophy:
 * Prevents predictable boss encounters. Players can't memorize
 * a single boss pattern because each spawn is procedurally unique.
 * This keeps veteran players engaged and creates stories
 * ("I fought a Berserker-Elemental that teleported AND threw fireballs!")
 */
public class BossDNAMixer {
    private static final Logger LOGGER = LoggerFactory.getLogger(BossDNAMixer.class);

    public static final BossDNAMixer INSTANCE = new BossDNAMixer();

    // Mixing configuration
    private static final float PRIMARY_WEIGHT = 0.70f;
    private static final float SECONDARY_WEIGHT = 0.30f;

    // Rare variant chances
    private static final float CHIMERA_CHANCE = 0.05f;     // 5%
    private static final float MIRROR_CHANCE = 0.03f;      // 3%
    private static final float EVOLVING_CHANCE = 0.02f;    // 2%

    // Name generation
    private static final String[] PREFIXES = {
        "Corrupted", "Ancient", "Vengeful", "Shadow", "Burning",
        "Frost-touched", "Void-born", "Bloodthirsty", "Spectral", "Doom"
    };

    private static final String[] SUFFIXES = {
        "the Destroyer", "of Nightmares", "the Undying", "of Chaos",
        "the Merciless", "Worldbreaker", "the Devourer", "of Agony",
        "Soulrender", "the Inevitable"
    };

    private static final String[] CHIMERA_TITLES = {
        "Abomination", "Nightmare", "Aberration", "Monstrosity", "Horror"
    };

    private final Random random = new Random();

    /**
     * Variant type for special boss behaviors.
     */
    public enum BossVariant {
        STANDARD("", 1.0f, 1.0f, 1.0f),
        CHIMERA("Chimera", 1.3f, 1.2f, 1.5f),    // 3 archetypes, stronger
        MIRROR("Mirror", 1.0f, 1.0f, 1.0f),       // Copies player stats
        EVOLVING("Evolving", 1.1f, 1.1f, 1.2f);   // Changes per phase

        public final String titlePrefix;
        public final float healthMod;
        public final float damageMod;
        public final float rewardMod;

        BossVariant(String titlePrefix, float health, float damage, float reward) {
            this.titlePrefix = titlePrefix;
            this.healthMod = health;
            this.damageMod = damage;
            this.rewardMod = reward;
        }
    }

    /**
     * Result of DNA mixing process.
     */
    public record MixedBossData(
        BossArchetype primaryArchetype,
        BossArchetype secondaryArchetype,
        BossArchetype tertiaryArchetype,  // Only for CHIMERA
        BossVariant variant,
        List<BossAbility> abilities,
        int blendedColor,
        String generatedName,
        float healthMultiplier,
        float damageMultiplier,
        float speedMultiplier,
        float rewardMultiplier
    ) {
        public boolean isChimera() {
            return variant == BossVariant.CHIMERA;
        }

        public boolean isMirror() {
            return variant == BossVariant.MIRROR;
        }

        public boolean isEvolving() {
            return variant == BossVariant.EVOLVING;
        }

        public boolean isRareVariant() {
            return variant != BossVariant.STANDARD;
        }
    }

    /**
     * Generate mixed boss data for a given wave.
     *
     * @param waveNumber Current wave number
     * @param seed Optional seed for reproducibility (null for random)
     * @return Mixed boss configuration
     */
    public MixedBossData generateMixedBoss(int waveNumber, Long seed) {
        Random rng = seed != null ? new Random(seed) : random;

        // Determine variant
        BossVariant variant = rollVariant(rng, waveNumber);

        // Select archetypes
        BossArchetype primary = selectPrimaryArchetype(rng, waveNumber);
        BossArchetype secondary = selectSecondaryArchetype(rng, primary);
        BossArchetype tertiary = null;

        if (variant == BossVariant.CHIMERA) {
            tertiary = selectTertiaryArchetype(rng, primary, secondary);
        }

        // Build ability pool
        List<BossAbility> abilities = buildAbilityPool(primary, secondary, tertiary, variant, rng);

        // Calculate blended color
        int color = blendColors(primary, secondary, tertiary);

        // Generate name
        String name = generateName(primary, secondary, variant, rng);

        // Calculate stat multipliers (weighted average)
        float healthMult = calculateStatMultiplier(primary.healthMultiplier, secondary.healthMultiplier,
            tertiary != null ? tertiary.healthMultiplier : 0, variant);
        float damageMult = calculateStatMultiplier(primary.damageMultiplier, secondary.damageMultiplier,
            tertiary != null ? tertiary.damageMultiplier : 0, variant);
        float speedMult = calculateStatMultiplier(primary.speedMultiplier, secondary.speedMultiplier,
            tertiary != null ? tertiary.speedMultiplier : 0, variant);

        float rewardMult = variant.rewardMod * (1.0f + (waveNumber * 0.05f));

        MixedBossData data = new MixedBossData(
            primary, secondary, tertiary, variant,
            abilities, color, name,
            healthMult, damageMult, speedMult, rewardMult
        );

        LOGGER.info("[BossDNA] Generated: {} ({}+{}{}) variant={} abilities={}",
            name, primary, secondary,
            tertiary != null ? "+" + tertiary : "",
            variant, abilities.size());

        return data;
    }

    /**
     * Roll for rare variant based on wave progression.
     * Higher waves have slightly increased rare chances.
     */
    private BossVariant rollVariant(Random rng, int waveNumber) {
        // Increase rare chances slightly for higher waves
        float waveBonus = Math.min(0.05f, waveNumber * 0.002f);

        float roll = rng.nextFloat();

        if (roll < EVOLVING_CHANCE + waveBonus) {
            return BossVariant.EVOLVING;
        }
        if (roll < EVOLVING_CHANCE + MIRROR_CHANCE + waveBonus) {
            return BossVariant.MIRROR;
        }
        if (roll < EVOLVING_CHANCE + MIRROR_CHANCE + CHIMERA_CHANCE + waveBonus) {
            return BossVariant.CHIMERA;
        }

        return BossVariant.STANDARD;
    }

    /**
     * Select primary archetype based on wave number.
     * Higher waves unlock more archetypes.
     */
    private BossArchetype selectPrimaryArchetype(Random rng, int waveNumber) {
        BossArchetype[] archetypes = BossArchetype.values();
        // Unlock more archetypes as waves progress
        int maxIndex = Math.min(archetypes.length, 1 + (waveNumber / 5));
        return archetypes[rng.nextInt(maxIndex)];
    }

    /**
     * Select secondary archetype, ensuring it's different from primary.
     */
    private BossArchetype selectSecondaryArchetype(Random rng, BossArchetype primary) {
        BossArchetype[] archetypes = BossArchetype.values();
        BossArchetype secondary;
        do {
            secondary = archetypes[rng.nextInt(archetypes.length)];
        } while (secondary == primary);
        return secondary;
    }

    /**
     * Select tertiary archetype for Chimera, different from both primary and secondary.
     */
    private BossArchetype selectTertiaryArchetype(Random rng, BossArchetype primary, BossArchetype secondary) {
        BossArchetype[] archetypes = BossArchetype.values();
        BossArchetype tertiary;
        do {
            tertiary = archetypes[rng.nextInt(archetypes.length)];
        } while (tertiary == primary || tertiary == secondary);
        return tertiary;
    }

    /**
     * Build ability pool from mixed archetypes.
     *
     * Standard: All primary abilities + 1-2 secondary abilities
     * Chimera: All abilities from all 3 archetypes
     * Mirror: Primary abilities only (stats from player)
     * Evolving: Primary abilities (changes at phase transitions)
     */
    private List<BossAbility> buildAbilityPool(
            BossArchetype primary,
            BossArchetype secondary,
            BossArchetype tertiary,
            BossVariant variant,
            Random rng) {

        List<BossAbility> pool = new ArrayList<>();

        // Always include all primary abilities
        pool.addAll(BossFight.getAbilitiesForArchetype(primary));

        switch (variant) {
            case CHIMERA -> {
                // Chimera gets ALL abilities from all archetypes
                pool.addAll(BossFight.getAbilitiesForArchetype(secondary));
                if (tertiary != null) {
                    pool.addAll(BossFight.getAbilitiesForArchetype(tertiary));
                }
            }
            case MIRROR -> {
                // Mirror only uses primary (relies on copied player stats)
            }
            case EVOLVING -> {
                // Evolving starts with primary, changes at phase transitions
            }
            default -> {
                // Standard: Add 1-2 random abilities from secondary
                List<BossAbility> secondaryAbilities = new ArrayList<>(
                    BossFight.getAbilitiesForArchetype(secondary));
                Collections.shuffle(secondaryAbilities, rng);
                int inheritCount = 1 + rng.nextInt(2); // 1-2 abilities
                for (int i = 0; i < Math.min(inheritCount, secondaryAbilities.size()); i++) {
                    pool.add(secondaryAbilities.get(i));
                }
            }
        }

        // Remove duplicates while preserving order
        return new ArrayList<>(new LinkedHashSet<>(pool));
    }

    /**
     * Blend colors from archetypes for visual distinction.
     * Uses weighted RGB blending.
     */
    private int blendColors(BossArchetype primary, BossArchetype secondary, BossArchetype tertiary) {
        int r1 = (primary.color >> 16) & 0xFF;
        int g1 = (primary.color >> 8) & 0xFF;
        int b1 = primary.color & 0xFF;

        int r2 = (secondary.color >> 16) & 0xFF;
        int g2 = (secondary.color >> 8) & 0xFF;
        int b2 = secondary.color & 0xFF;

        int r, g, b;

        if (tertiary != null) {
            // Chimera: equal blend of all 3
            int r3 = (tertiary.color >> 16) & 0xFF;
            int g3 = (tertiary.color >> 8) & 0xFF;
            int b3 = tertiary.color & 0xFF;

            r = (r1 + r2 + r3) / 3;
            g = (g1 + g2 + g3) / 3;
            b = (b1 + b2 + b3) / 3;
        } else {
            // Standard: 70/30 blend
            r = (int) (r1 * PRIMARY_WEIGHT + r2 * SECONDARY_WEIGHT);
            g = (int) (g1 * PRIMARY_WEIGHT + g2 * SECONDARY_WEIGHT);
            b = (int) (b1 * PRIMARY_WEIGHT + b2 * SECONDARY_WEIGHT);
        }

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Generate a procedural name for the boss.
     */
    private String generateName(BossArchetype primary, BossArchetype secondary,
                                BossVariant variant, Random rng) {
        StringBuilder name = new StringBuilder();

        // Variant prefix
        if (!variant.titlePrefix.isEmpty()) {
            name.append(variant.titlePrefix).append(" ");
        }

        // Random prefix (50% chance)
        if (rng.nextFloat() < 0.5f) {
            name.append(PREFIXES[rng.nextInt(PREFIXES.length)]).append(" ");
        }

        // Archetype hybrid name
        if (variant == BossVariant.CHIMERA) {
            name.append(CHIMERA_TITLES[rng.nextInt(CHIMERA_TITLES.length)]);
        } else {
            // Combine archetype names: e.g., "Berserk-Summoner" or "Juggersassin"
            name.append(createHybridName(primary, secondary, rng));
        }

        // Random suffix (40% chance)
        if (rng.nextFloat() < 0.4f) {
            name.append(" ").append(SUFFIXES[rng.nextInt(SUFFIXES.length)]);
        }

        return name.toString().trim();
    }

    /**
     * Create a hybrid name from two archetypes.
     */
    private String createHybridName(BossArchetype primary, BossArchetype secondary, Random rng) {
        String pName = primary.displayName;
        String sName = secondary.displayName;

        // Different blending strategies
        int strategy = rng.nextInt(3);

        return switch (strategy) {
            case 0 -> // First half of primary + second half of secondary
                pName.substring(0, Math.min(4, pName.length())) +
                sName.substring(Math.max(0, sName.length() - 4)).toLowerCase();
            case 1 -> // Full primary + "-" + partial secondary
                pName + "-" + sName.substring(0, Math.min(3, sName.length()));
            default -> // Simple combination
                pName + " " + sName;
        };
    }

    /**
     * Calculate weighted stat multiplier.
     */
    private float calculateStatMultiplier(float primary, float secondary, float tertiary,
                                          BossVariant variant) {
        float base;
        if (tertiary > 0) {
            // Chimera: average of all 3
            base = (primary + secondary + tertiary) / 3.0f;
        } else {
            // Standard: weighted average
            base = primary * PRIMARY_WEIGHT + secondary * SECONDARY_WEIGHT;
        }

        // Apply variant modifier
        return switch (variant) {
            case CHIMERA -> base * variant.healthMod;
            case MIRROR -> base; // Mirror uses player stats, not archetype
            case EVOLVING -> base * variant.healthMod;
            default -> base;
        };
    }

    /**
     * Get the next archetype for an Evolving boss at phase transition.
     * Called when Evolving boss enters a new phase.
     */
    public BossArchetype getEvolvingNextArchetype(BossArchetype current, int phase, long bossId) {
        Random rng = new Random(bossId + phase);
        BossArchetype[] archetypes = BossArchetype.values();
        BossArchetype next;
        do {
            next = archetypes[rng.nextInt(archetypes.length)];
        } while (next == current);

        LOGGER.info("[BossDNA] Evolving boss transitioning: {} → {} (phase {})", current, next, phase);
        return next;
    }

    /**
     * Calculate Mirror boss stats based on player gear.
     * Returns a multiplier for each stat.
     */
    public MirrorStats calculateMirrorStats(net.minecraft.world.entity.player.Player player) {
        float armorValue = player.getArmorValue();
        float attackDamage = 1.0f;

        // Get player's attack damage if available
        var damageAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            attackDamage = (float) damageAttr.getValue();
        }

        // Scale based on player stats
        float healthMult = 1.0f + (armorValue / 20.0f);  // Max +100% health from armor
        float damageMult = 0.5f + (attackDamage / 10.0f); // Scales with player damage
        float speedMult = 1.0f;

        // Mirror is tough but fair - never exceeds 2x multipliers
        healthMult = Math.min(2.0f, healthMult);
        damageMult = Math.min(2.0f, damageMult);

        LOGGER.debug("[BossDNA] Mirror stats from player: armor={} dmg={} → health={} dmg={}",
            armorValue, attackDamage, healthMult, damageMult);

        return new MirrorStats(healthMult, damageMult, speedMult);
    }

    /**
     * Mirror boss stat multipliers.
     */
    public record MirrorStats(float healthMult, float damageMult, float speedMult) {}

    private BossDNAMixer() {}
}
