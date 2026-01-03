package com.devmod.endurance;

import com.devmod.shared.SharedColorTokens;

/**
 * Shared color palette for endurance systems (server-safe).
 */
public final class EnduranceColors {

    private EnduranceColors() {}

    public static final class Text {
        public static final int WHITE = SharedColorTokens.Endurance.Text.WHITE;
        public static final int MUTED = SharedColorTokens.Endurance.Text.MUTED;

        private Text() {}
    }

    public static final class Currency {
        public static final int TOKENS = SharedColorTokens.Endurance.Currency.TOKENS;
        public static final int COINS = SharedColorTokens.Endurance.Currency.COINS;
        public static final int PRESTIGE = SharedColorTokens.Endurance.Currency.PRESTIGE;
        public static final int GEMS = SharedColorTokens.Endurance.Currency.GEMS;
        public static final int BLOOD_GEMS = SharedColorTokens.Endurance.Currency.BLOOD_GEMS;

        private Currency() {}
    }

    public static final class LootTier {
        public static final int COMMON = SharedColorTokens.Endurance.LootTier.COMMON;
        public static final int UNCOMMON = SharedColorTokens.Endurance.LootTier.UNCOMMON;
        public static final int RARE = SharedColorTokens.Endurance.LootTier.RARE;
        public static final int EPIC = SharedColorTokens.Endurance.LootTier.EPIC;
        public static final int LEGENDARY = SharedColorTokens.Endurance.LootTier.LEGENDARY;
        public static final int MYTHIC = SharedColorTokens.Endurance.LootTier.MYTHIC;

        private LootTier() {}
    }

    public static final class RewardCategory {
        public static final int STATS = SharedColorTokens.Endurance.RewardCategory.STATS;
        public static final int PERKS = SharedColorTokens.Endurance.RewardCategory.PERKS;
        public static final int UTILITY = SharedColorTokens.Endurance.RewardCategory.UTILITY;
        public static final int COSMETICS = SharedColorTokens.Endurance.RewardCategory.COSMETICS;

        private RewardCategory() {}
    }

    public static final class StyleRank {
        public static final int D = SharedColorTokens.Endurance.StyleRank.D;
        public static final int C = SharedColorTokens.Endurance.StyleRank.C;
        public static final int B = SharedColorTokens.Endurance.StyleRank.B;
        public static final int A = SharedColorTokens.Endurance.StyleRank.A;
        public static final int S = SharedColorTokens.Endurance.StyleRank.S;
        public static final int SS = SharedColorTokens.Endurance.StyleRank.SS;
        public static final int SSS = SharedColorTokens.Endurance.StyleRank.SSS;

        private StyleRank() {}
    }

    public static final class Momentum {
        public static final int STAGNANT = SharedColorTokens.Endurance.Momentum.STAGNANT;
        public static final int BUILDING = SharedColorTokens.Endurance.Momentum.BUILDING;
        public static final int HEATED = SharedColorTokens.Endurance.Momentum.HEATED;
        public static final int OVERDRIVE = SharedColorTokens.Endurance.Momentum.OVERDRIVE;

        private Momentum() {}
    }

    public static final class Flow {
        public static final int STALE = SharedColorTokens.Endurance.Flow.STALE;
        public static final int NEUTRAL = SharedColorTokens.Endurance.Flow.NEUTRAL;
        public static final int FRESH = SharedColorTokens.Endurance.Flow.FRESH;
        public static final int VIRTUOSO = SharedColorTokens.Endurance.Flow.VIRTUOSO;

        private Flow() {}
    }

    public static final class Tension {
        public static final int CALM = SharedColorTokens.Endurance.Tension.CALM;
        public static final int BUILDING = SharedColorTokens.Endurance.Tension.BUILDING;
        public static final int MODERATE = SharedColorTokens.Endurance.Tension.MODERATE;
        public static final int HIGH = SharedColorTokens.Endurance.Tension.HIGH;
        public static final int CRITICAL = SharedColorTokens.Endurance.Tension.CRITICAL;
        public static final int DEFAULT = SharedColorTokens.Endurance.Tension.DEFAULT;

        private Tension() {}
    }

    public static final class Tide {
        public static final int CALM = SharedColorTokens.Endurance.Tide.CALM;
        public static final int RISING = SharedColorTokens.Endurance.Tide.RISING;
        public static final int HIGH = SharedColorTokens.Endurance.Tide.HIGH;
        public static final int STORM = SharedColorTokens.Endurance.Tide.STORM;
        public static final int APOCALYPSE = SharedColorTokens.Endurance.Tide.APOCALYPSE;

        private Tide() {}
    }

    public static final class Boss {
        public static final int BERSERKER = SharedColorTokens.Endurance.Boss.BERSERKER;
        public static final int SUMMONER = SharedColorTokens.Endurance.Boss.SUMMONER;
        public static final int JUGGERNAUT = SharedColorTokens.Endurance.Boss.JUGGERNAUT;
        public static final int ASSASSIN = SharedColorTokens.Endurance.Boss.ASSASSIN;
        public static final int ELEMENTAL = SharedColorTokens.Endurance.Boss.ELEMENTAL;

        private Boss() {}
    }

    public static final class Contract {
        public static final int MINOR = SharedColorTokens.Endurance.Contract.MINOR;
        public static final int STANDARD = SharedColorTokens.Endurance.Contract.STANDARD;
        public static final int MAJOR = SharedColorTokens.Endurance.Contract.MAJOR;
        public static final int BLOOD = SharedColorTokens.Endurance.Contract.BLOOD;

        private Contract() {}
    }

    public static final class Kit {
        public static final int STARTER = SharedColorTokens.Endurance.Kit.STARTER;
        public static final int WARRIOR = SharedColorTokens.Endurance.Kit.WARRIOR;
        public static final int RANGER = SharedColorTokens.Endurance.Kit.RANGER;
        public static final int TANK = SharedColorTokens.Endurance.Kit.TANK;
        public static final int MAGE = SharedColorTokens.Endurance.Kit.MAGE;
        public static final int BERSERKER = SharedColorTokens.Endurance.Kit.BERSERKER;
        public static final int CUSTOM = SharedColorTokens.Endurance.Kit.CUSTOM;

        private Kit() {}
    }

    public static final class Prestige {
        public static final int PERK_SLOT = SharedColorTokens.Endurance.Prestige.PERK_SLOT;
        public static final int MUTATOR_UNLOCK = SharedColorTokens.Endurance.Prestige.MUTATOR_UNLOCK;
        public static final int ARENA_UNLOCK = SharedColorTokens.Endurance.Prestige.ARENA_UNLOCK;
        public static final int COSMETIC_TITLE = SharedColorTokens.Endurance.Prestige.COSMETIC_TITLE;
        public static final int STARTING_BONUS = SharedColorTokens.Endurance.Prestige.STARTING_BONUS;
        public static final int TOKEN_MULTIPLIER = SharedColorTokens.Endurance.Prestige.TOKEN_MULTIPLIER;
        public static final int EXCLUSIVE_PERK = SharedColorTokens.Endurance.Prestige.EXCLUSIVE_PERK;

        private Prestige() {}
    }

    public static final class PerkRarity {
        public static final int COMMON = SharedColorTokens.Endurance.PerkRarity.COMMON;
        public static final int UNCOMMON = SharedColorTokens.Endurance.PerkRarity.UNCOMMON;
        public static final int RARE = SharedColorTokens.Endurance.PerkRarity.RARE;
        public static final int EPIC = SharedColorTokens.Endurance.PerkRarity.EPIC;
        public static final int LEGENDARY = SharedColorTokens.Endurance.PerkRarity.LEGENDARY;

        private PerkRarity() {}
    }

    public static final class PerkCategory {
        public static final int OFFENSE = SharedColorTokens.Endurance.PerkCategory.OFFENSE;
        public static final int DEFENSE = SharedColorTokens.Endurance.PerkCategory.DEFENSE;
        public static final int UTILITY = SharedColorTokens.Endurance.PerkCategory.UTILITY;
        public static final int VAMPIRIC = SharedColorTokens.Endurance.PerkCategory.VAMPIRIC;
        public static final int ELEMENTAL = SharedColorTokens.Endurance.PerkCategory.ELEMENTAL;
        public static final int COMBO = SharedColorTokens.Endurance.PerkCategory.COMBO;
        public static final int CURSE = SharedColorTokens.Endurance.PerkCategory.CURSE;

        private PerkCategory() {}
    }

    public static final class Mutator {
        public static final int POSITIVE = SharedColorTokens.Endurance.Mutator.POSITIVE;
        public static final int NEGATIVE = SharedColorTokens.Endurance.Mutator.NEGATIVE;
        public static final int NEUTRAL = SharedColorTokens.Endurance.Mutator.NEUTRAL;
        public static final int CHAOTIC = SharedColorTokens.Endurance.Mutator.CHAOTIC;

        private Mutator() {}
    }

    public static final class Synergy {
        public static final int MINOR = SharedColorTokens.Endurance.Synergy.MINOR;
        public static final int MODERATE = SharedColorTokens.Endurance.Synergy.MODERATE;
        public static final int STRONG = SharedColorTokens.Endurance.Synergy.STRONG;
        public static final int LEGENDARY = SharedColorTokens.Endurance.Synergy.LEGENDARY;

        private Synergy() {}
    }

    public static final class ResonanceChain {
        public static final int DUO = SharedColorTokens.Endurance.ResonanceChain.DUO;
        public static final int TRINITY = SharedColorTokens.Endurance.ResonanceChain.TRINITY;
        public static final int APOCALYPSE = SharedColorTokens.Endurance.ResonanceChain.APOCALYPSE;
        public static final int DEFAULT = SharedColorTokens.Endurance.ResonanceChain.DEFAULT;

        private ResonanceChain() {}
    }

    public static final class Hazard {
        public static final int FIRE = SharedColorTokens.Endurance.Hazard.FIRE;
        public static final int BLEED = SharedColorTokens.Endurance.Hazard.BLEED;
        public static final int VOID = SharedColorTokens.Endurance.Hazard.VOID;
        public static final int ARC = SharedColorTokens.Endurance.Hazard.ARC;
        public static final int PSI = SharedColorTokens.Endurance.Hazard.PSI;

        private Hazard() {}
    }

    public static final class Nemesis {
        public static final int PROJECTILE_DEFLECTION = SharedColorTokens.Endurance.Nemesis.PROJECTILE_DEFLECTION;
        public static final int SWEEPING_BLADE = SharedColorTokens.Endurance.Nemesis.SWEEPING_BLADE;
        public static final int EARLY_PHASE_ACTIVATION = SharedColorTokens.Endurance.Nemesis.EARLY_PHASE_ACTIVATION;
        public static final int DAMAGE_RESISTANCE = SharedColorTokens.Endurance.Nemesis.DAMAGE_RESISTANCE;
        public static final int PROTECTIVE_HELMET = SharedColorTokens.Endurance.Nemesis.PROTECTIVE_HELMET;
        public static final int IMPROVED_REFLEXES = SharedColorTokens.Endurance.Nemesis.IMPROVED_REFLEXES;
        public static final int ENRAGED = SharedColorTokens.Endurance.Nemesis.ENRAGED;
        public static final int VETERAN = SharedColorTokens.Endurance.Nemesis.VETERAN;
        public static final int EVASION = SharedColorTokens.Endurance.Nemesis.EVASION;
        public static final int REGENERATION = SharedColorTokens.Endurance.Nemesis.REGENERATION;
        public static final int SUMMONER = SharedColorTokens.Endurance.Nemesis.SUMMONER;

        private Nemesis() {}
    }

    public static final class Bargain {
        public static final int GLASS_CANNON = SharedColorTokens.Endurance.Bargain.GLASS_CANNON;
        public static final int SLUGGISH = SharedColorTokens.Endurance.Bargain.SLUGGISH;
        public static final int FUMBLING = SharedColorTokens.Endurance.Bargain.FUMBLING;
        public static final int HUNGER = SharedColorTokens.Endurance.Bargain.HUNGER;
        public static final int ECHO_DAMAGE = SharedColorTokens.Endurance.Bargain.ECHO_DAMAGE;
        public static final int BLOOD_TITHE = SharedColorTokens.Endurance.Bargain.BLOOD_TITHE;
        public static final int CROWD_PRESSURE = SharedColorTokens.Endurance.Bargain.CROWD_PRESSURE;
        public static final int FRAILTY = SharedColorTokens.Endurance.Bargain.FRAILTY;
        public static final int BURNING_SOUL = SharedColorTokens.Endurance.Bargain.BURNING_SOUL;
        public static final int COMBO_BREAKER = SharedColorTokens.Endurance.Bargain.COMBO_BREAKER;
        public static final int MOMENTUM_DRAIN = SharedColorTokens.Endurance.Bargain.MOMENTUM_DRAIN;
        public static final int ONE_SHOT = SharedColorTokens.Endurance.Bargain.ONE_SHOT;
        public static final int ELITE_HUNTER = SharedColorTokens.Endurance.Bargain.ELITE_HUNTER;
        public static final int NO_HEALING = SharedColorTokens.Endurance.Bargain.NO_HEALING;
        public static final int EXECUTIONER = SharedColorTokens.Endurance.Bargain.EXECUTIONER;

        private Bargain() {}
    }

    public static final class BargainTier {
        public static final int MINOR = SharedColorTokens.Endurance.BargainTier.MINOR;
        public static final int MAJOR = SharedColorTokens.Endurance.BargainTier.MAJOR;
        public static final int CURSED = SharedColorTokens.Endurance.BargainTier.CURSED;

        private BargainTier() {}
    }
}
