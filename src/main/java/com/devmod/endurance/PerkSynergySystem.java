package com.devmod.endurance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerkSynergySystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(PerkSynergySystem.class);

    public static final PerkSynergySystem INSTANCE = new PerkSynergySystem();

    // All defined synergies
    private final List<PerkSynergy> synergies = new ArrayList<>();

    // Quick lookup: perk ID -> synergies it's involved in
    private final Map<String, List<PerkSynergy>> synergiesByPerk = new HashMap<>();

    /**
     * Synergy rating for UI display.
     */
    public enum SynergyStrength {
        MINOR(1, EnduranceColors.Synergy.MINOR, "Minor"),         // Small bonus
        MODERATE(2, EnduranceColors.Synergy.MODERATE, "Good"),    // Noticeable improvement
        STRONG(3, EnduranceColors.Synergy.STRONG, "Strong"),      // Significant combo
        LEGENDARY(4, EnduranceColors.Synergy.LEGENDARY, "Legendary"); // Game-changing combo

        public final int value;
        public final int color;
        public final String displayName;

        SynergyStrength(int value, int color, String displayName) {
            this.value = value;
            this.color = color;
            this.displayName = displayName;
        }
    }

    /**
     * Type of synergy relationship.
     */
    public enum SynergyType {
        COMBO,      // Direct perk-to-perk enhancement
        THRESHOLD,  // Category count threshold bonus
        ARCHETYPE,  // Build archetype synergy
        SPECIAL     // Unique legendary combo
    }

    /**
     * A defined synergy between perks.
     */
    public static class PerkSynergy {
        public final String id;
        public final String name;
        public final String description;
        public final SynergyType type;
        public final SynergyStrength strength;
        public final Set<String> requiredPerks;  // All must be owned for synergy
        public final Set<String> optionalPerks;  // Any of these enhance the synergy
        public final String bonusEffect;         // Description of bonus when active

        public PerkSynergy(String id, String name, String description, SynergyType type,
                          SynergyStrength strength, Set<String> requiredPerks,
                          Set<String> optionalPerks, String bonusEffect) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.type = type;
            this.strength = strength;
            this.requiredPerks = requiredPerks;
            this.optionalPerks = optionalPerks != null ? optionalPerks : Set.of();
            this.bonusEffect = bonusEffect;
        }

        /**
         * Check if this synergy is active for a given set of owned perks.
         */
        public boolean isActive(Set<String> ownedPerks) {
            return ownedPerks.containsAll(requiredPerks);
        }

        /**
         * Check if a specific perk is part of this synergy.
         */
        public boolean involvesPerk(String perkId) {
            return requiredPerks.contains(perkId) || optionalPerks.contains(perkId);
        }

        /**
         * Get missing perks needed to activate this synergy.
         */
        public Set<String> getMissingPerks(Set<String> ownedPerks) {
            Set<String> missing = new HashSet<>(requiredPerks);
            missing.removeAll(ownedPerks);
            return missing;
        }

        /**
         * Calculate completion percentage (0.0 to 1.0).
         */
        public float getCompletionPercent(Set<String> ownedPerks) {
            if (requiredPerks.isEmpty()) return 1.0f;
            long owned = requiredPerks.stream().filter(ownedPerks::contains).count();
            return (float) owned / requiredPerks.size();
        }
    }

    /**
     * Result of synergy analysis for a perk choice.
     */
    public record SynergyPreview(
        String perkId,
        List<ActiveSynergy> activeSynergies,      // Already active with this perk
        List<PotentialSynergy> potentialSynergies, // Would become closer to active
        List<NewSynergy> newSynergies,             // Would be newly activated
        int totalSynergyScore                       // Sum of all synergy values
    ) {
        public boolean hasSynergies() {
            return !activeSynergies.isEmpty() || !potentialSynergies.isEmpty() || !newSynergies.isEmpty();
        }
    }

    public record ActiveSynergy(PerkSynergy synergy, String bonusText) {}
    public record PotentialSynergy(PerkSynergy synergy, Set<String> missingPerks, float progress) {}
    public record NewSynergy(PerkSynergy synergy, String effectDescription) {}

    // ========== Initialization ==========

    private PerkSynergySystem() {
        initializeSynergies();
    }

    private void initializeSynergies() {
        // === COMBO SYNERGIES - Direct perk interactions ===

        // Critical Build
        registerSynergy(new PerkSynergy(
            "crit_master", "Critical Master",
            "Critical Eye + Executioner create devastating crits",
            SynergyType.COMBO, SynergyStrength.STRONG,
            Set.of("critical_eye", "executioner"),
            Set.of("glass_cannon", "berserker"),
            "+25% crit chance, +100% crit damage combined"
        ));

        // Lifesteal + Damage
        registerSynergy(new PerkSynergy(
            "blood_warrior", "Blood Warrior",
            "Lifesteal + Sharp Blades sustain through damage",
            SynergyType.COMBO, SynergyStrength.MODERATE,
            Set.of("lifesteal", "sharp_blades"),
            Set.of("fury", "blood_frenzy"),
            "Heal 5% of boosted damage dealt"
        ));

        // Elemental Combo
        registerSynergy(new PerkSynergy(
            "elemental_storm", "Elemental Storm",
            "Fire + Frost + Lightning for massive proc chance",
            SynergyType.COMBO, SynergyStrength.STRONG,
            Set.of("fire_aspect", "frost_touch", "lightning_strike"),
            Set.of("elemental_mastery"),
            "60%+ chance to trigger elemental effects"
        ));

        // Elemental Mastery Boost
        registerSynergy(new PerkSynergy(
            "element_master", "Elemental Mastery Combo",
            "Elemental Mastery amplifies existing elemental perks",
            SynergyType.COMBO, SynergyStrength.MODERATE,
            Set.of("elemental_mastery"),
            Set.of("fire_aspect", "frost_touch", "lightning_strike"),
            "+50% to all elemental effect chances"
        ));

        // Speed Demon
        registerSynergy(new PerkSynergy(
            "speed_demon", "Speed Demon",
            "Swift Feet + Fury for rapid attacks and movement",
            SynergyType.COMBO, SynergyStrength.MODERATE,
            Set.of("swift_feet", "fury"),
            Set.of("momentum"),
            "Attack and move faster for aggressive play"
        ));

        // Tank Build
        registerSynergy(new PerkSynergy(
            "iron_wall", "Iron Wall",
            "Tough Skin + Vitality for maximum survivability",
            SynergyType.COMBO, SynergyStrength.MODERATE,
            Set.of("tough_skin", "vitality"),
            Set.of("regeneration", "second_wind"),
            "Survive longer with stacked defenses"
        ));

        // Vampire Lord
        registerSynergy(new PerkSynergy(
            "vampire_lord", "Vampire Lord",
            "Stack vampiric perks for immortal combat",
            SynergyType.COMBO, SynergyStrength.STRONG,
            Set.of("lifesteal", "blood_frenzy", "soul_drain"),
            Set.of("berserker"),
            "Constant health regeneration through combat"
        ));

        // Combo Specialist
        registerSynergy(new PerkSynergy(
            "combo_specialist", "Combo Specialist",
            "Combo Master + Showoff + Momentum for style points",
            SynergyType.COMBO, SynergyStrength.STRONG,
            Set.of("combo_master", "showoff"),
            Set.of("momentum", "style_is_substance"),
            "Maintain combos longer with bigger rewards"
        ));

        // Risk/Reward
        registerSynergy(new PerkSynergy(
            "risk_reward", "High Stakes",
            "Glass Cannon + Berserker for maximum damage",
            SynergyType.COMBO, SynergyStrength.STRONG,
            Set.of("glass_cannon", "berserker"),
            Set.of("lifesteal", "adrenaline_surge"),
            "Extreme damage output at low health"
        ));

        // === TRANSFORMATIVE PERK SYNERGIES ===

        // Echo + Chain Reaction
        registerSynergy(new PerkSynergy(
            "aftershock", "Aftershock",
            "Echo Strike + Chain Reaction for delayed devastation",
            SynergyType.COMBO, SynergyStrength.STRONG,
            Set.of("echo_strike", "chain_reaction"),
            Set.of("executioners_wrath"),
            "Echoes can trigger explosions on kills"
        ));

        // Revenge + Bullet Time
        registerSynergy(new PerkSynergy(
            "last_stand", "Last Stand",
            "Revenge + Bullet Time at low HP",
            SynergyType.COMBO, SynergyStrength.STRONG,
            Set.of("revenge", "bullet_time"),
            Set.of("adrenaline_surge", "berserker"),
            "Low HP = slow enemies + massive damage boost"
        ));

        // Soul Harvest + Executioner's Wrath
        registerSynergy(new PerkSynergy(
            "reaper", "Grim Reaper",
            "Soul Harvest + Executioner's Wrath execute and empower",
            SynergyType.COMBO, SynergyStrength.LEGENDARY,
            Set.of("soul_harvest", "executioners_wrath"),
            Set.of("chain_reaction"),
            "Execute low HP enemies, store souls for massive burst"
        ));

        // Phantom Shift + Regeneration
        registerSynergy(new PerkSynergy(
            "ghost_recovery", "Ghost Recovery",
            "Phantom Shift + Regeneration for safe healing windows",
            SynergyType.COMBO, SynergyStrength.MODERATE,
            Set.of("phantom_shift", "regeneration"),
            Set.of("second_wind"),
            "Invulnerability phases allow regeneration"
        ));

        // Blood Pact + Iron Wall
        registerSynergy(new PerkSynergy(
            "brotherhood", "Brotherhood",
            "Blood Pact + defensive perks protect the party",
            SynergyType.COMBO, SynergyStrength.STRONG,
            Set.of("blood_pact", "tough_skin"),
            Set.of("vitality", "regeneration"),
            "Shared damage reduction benefits all linked players"
        ));

        // Unstoppable Force + Momentum
        registerSynergy(new PerkSynergy(
            "juggernaut", "Juggernaut",
            "Unstoppable Force + Momentum for aggressive rushdown",
            SynergyType.COMBO, SynergyStrength.MODERATE,
            Set.of("unstoppable_force", "momentum"),
            Set.of("fury", "swift_feet"),
            "Charge through enemies building combo damage"
        ));

        // === ARCHETYPE SYNERGIES - Category-based builds ===

        // Full Offense
        registerSynergy(new PerkSynergy(
            "berserker_path", "Path of the Berserker",
            "Focus purely on offense perks",
            SynergyType.ARCHETYPE, SynergyStrength.STRONG,
            Set.of("sharp_blades", "fury", "critical_eye"),
            Set.of("executioner", "berserker", "glass_cannon"),
            "Unlocks Avatar of War at 5+ offense perks"
        ));

        // Full Defense
        registerSynergy(new PerkSynergy(
            "guardian_path", "Path of the Guardian",
            "Focus purely on defense perks",
            SynergyType.ARCHETYPE, SynergyStrength.STRONG,
            Set.of("tough_skin", "vitality", "regeneration"),
            Set.of("second_wind", "immortal"),
            "Unlocks Unkillable at 5+ defense perks"
        ));

        // Curse Stacker
        registerSynergy(new PerkSynergy(
            "cursed_gambler", "Cursed Gambler",
            "Stack curses for massive reward multipliers",
            SynergyType.ARCHETYPE, SynergyStrength.MODERATE,
            Set.of("curse_fragility", "curse_weakness"),
            Set.of("curse_doom", "ultimate_curse"),
            "+100% to +400% reward multiplier at extreme risk"
        ));

        // === SPECIAL LEGENDARY SYNERGIES ===

        // Avatar of War unlock hint
        registerSynergy(new PerkSynergy(
            "war_ascension", "War Ascension",
            "Acquire 5+ offense perks to unlock Avatar of War",
            SynergyType.SPECIAL, SynergyStrength.LEGENDARY,
            Set.of("avatar_of_war"),
            Set.of("sharp_blades", "fury", "critical_eye", "executioner", "berserker"),
            "Attacks cause explosions, +100% damage"
        ));

        // Unkillable unlock hint
        registerSynergy(new PerkSynergy(
            "immortal_ascension", "Immortal Ascension",
            "Acquire 5+ defense perks to unlock Unkillable",
            SynergyType.SPECIAL, SynergyStrength.LEGENDARY,
            Set.of("unkillable"),
            Set.of("tough_skin", "vitality", "regeneration", "second_wind"),
            "Immune to one-shots, +50% max health"
        ));

        // Ultimate Risk/Reward
        registerSynergy(new PerkSynergy(
            "death_dance", "Dance with Death",
            "Pact with Death + Adrenaline Surge",
            SynergyType.SPECIAL, SynergyStrength.LEGENDARY,
            Set.of("ultimate_curse", "adrenaline_surge"),
            Set.of("lifesteal", "blood_frenzy"),
            "One-hit death but 3s invincibility saves can proc"
        ));

        LOGGER.info("[PerkSynergySystem] Registered {} synergies", synergies.size());
    }

    private void registerSynergy(PerkSynergy synergy) {
        synergies.add(synergy);

        // Index by perk for quick lookup
        for (String perkId : synergy.requiredPerks) {
            synergiesByPerk.computeIfAbsent(perkId, k -> new ArrayList<>()).add(synergy);
        }
        for (String perkId : synergy.optionalPerks) {
            synergiesByPerk.computeIfAbsent(perkId, k -> new ArrayList<>()).add(synergy);
        }
    }

    // ========== Synergy Analysis ==========

    /**
     * Analyze synergies for a perk choice given current owned perks.
     */
    public SynergyPreview analyzePerk(String perkId, Set<String> ownedPerks) {
        List<ActiveSynergy> active = new ArrayList<>();
        List<PotentialSynergy> potential = new ArrayList<>();
        List<NewSynergy> newSynergies = new ArrayList<>();
        int totalScore = 0;

        // Simulate owning this perk
        Set<String> simulatedOwned = new HashSet<>(ownedPerks);
        simulatedOwned.add(perkId);

        for (PerkSynergy synergy : synergies) {
            if (!synergy.involvesPerk(perkId)) continue;

            boolean wasActive = synergy.isActive(ownedPerks);
            boolean wouldBeActive = synergy.isActive(simulatedOwned);

            if (wouldBeActive && !wasActive) {
                // This perk would complete the synergy!
                newSynergies.add(new NewSynergy(synergy, synergy.bonusEffect));
                totalScore += synergy.strength.value * 2; // Bonus for completing
            } else if (wouldBeActive) {
                // Already active, this perk enhances it
                active.add(new ActiveSynergy(synergy, synergy.bonusEffect));
                totalScore += synergy.strength.value;
            } else {
                // Not complete yet, show progress
                Set<String> missing = synergy.getMissingPerks(simulatedOwned);
                float progress = synergy.getCompletionPercent(simulatedOwned);
                if (progress > 0 && missing.size() <= 2) {
                    potential.add(new PotentialSynergy(synergy, missing, progress));
                    totalScore += (int) (synergy.strength.value * progress);
                }
            }
        }

        return new SynergyPreview(perkId, active, potential, newSynergies, totalScore);
    }

    /**
     * Get all synergies involving a specific perk.
     */
    public List<PerkSynergy> getSynergiesForPerk(String perkId) {
        return synergiesByPerk.getOrDefault(perkId, List.of());
    }

    /**
     * Get active synergies for current owned perks.
     */
    public List<PerkSynergy> getActiveSynergies(Set<String> ownedPerks) {
        return synergies.stream()
            .filter(s -> s.isActive(ownedPerks))
            .toList();
    }

    /**
     * Get synergies close to completion (1-2 perks away).
     */
    public List<PerkSynergy> getNearCompleteSynergies(Set<String> ownedPerks) {
        return synergies.stream()
            .filter(s -> !s.isActive(ownedPerks))
            .filter(s -> s.getMissingPerks(ownedPerks).size() <= 2)
            .filter(s -> s.getCompletionPercent(ownedPerks) >= 0.3f)
            .toList();
    }

    /**
     * Calculate total synergy score for current build.
     */
    public int calculateBuildSynergyScore(Set<String> ownedPerks) {
        int score = 0;
        for (PerkSynergy synergy : synergies) {
            if (synergy.isActive(ownedPerks)) {
                score += synergy.strength.value;
            } else {
                // Partial credit for progress
                float progress = synergy.getCompletionPercent(ownedPerks);
                if (progress >= 0.5f) {
                    score += (int) (synergy.strength.value * progress * 0.5f);
                }
            }
        }
        return score;
    }

    /**
     * Get recommended perks to complete near-active synergies.
     */
    public Set<String> getRecommendedPerks(Set<String> ownedPerks) {
        Set<String> recommended = new HashSet<>();

        for (PerkSynergy synergy : synergies) {
            Set<String> missing = synergy.getMissingPerks(ownedPerks);
            if (missing.size() == 1) {
                // Just one perk away - highly recommended
                recommended.addAll(missing);
            } else if (missing.size() == 2 && synergy.strength.value >= SynergyStrength.STRONG.value) {
                // Two perks away from a strong synergy
                recommended.addAll(missing);
            }
        }

        return recommended;
    }

    /**
     * Get all defined synergies.
     */
    public List<PerkSynergy> getAllSynergies() {
        return Collections.unmodifiableList(synergies);
    }
}
