package com.devmod.shared;

/**
 * Shared color tokens for common (client/server-safe) usage.
 */
public final class SharedColorTokens {

    private SharedColorTokens() {}

    public static class Mask {
        public static final int RGB = 0x00FFFFFF;
        public static final int ALPHA = 0xFF000000;
        public static final int NONE = 0x00000000;

        protected Mask() {}
    }

    public static class Basic {
        public static final int RED = 0xFFFF0000;
        public static final int YELLOW = 0xFFFFFF00;
        public static final int GREEN = 0xFF00FF00;
        public static final int CYAN = 0xFF00FFFF;
        public static final int BLUE = 0xFF0000FF;

        protected Basic() {}
    }

    public static class Message {
        public static final int RGB_SUCCESS = 0x55FF55;
        public static final int RGB_ERROR = 0xFF5555;
        public static final int RGB_MUTED = 0xAAAAAA;

        protected Message() {}
    }

    public static class FollowRange {
        public static final int RED = Basic.RED;
        public static final int YELLOW = Basic.YELLOW;
        public static final int GREEN = Basic.GREEN;
        public static final int CYAN = Basic.CYAN;
        public static final int BLUE = Basic.BLUE;

        protected FollowRange() {}
    }

    public static class BodyPart {
        public static final int HEAD = 0xFF00FFFF;
        public static final int BODY = 0xFF00FF00;
        public static final int ARMS = 0xFFFFFF00;
        public static final int LEGS = 0xFFFF0000;

        protected BodyPart() {}
    }

    public static class Shield {
        public static final int DEFAULT = 0x3D5AFE;

        protected Shield() {}
    }

    public static class Combat {
        public static final class Text {
            public static final int PRIMARY = 0xFFFFFF;
            public static final int MUTED = 0xAAAAAA;
            public static final int WARNING = 0xFFAA00;

            private Text() {}
        }

        public static final class BodyPart {
            public static final int DEFAULT = Combat.Text.PRIMARY;
            public static final int HEAD = 0xFF5555;
            public static final int BODY = 0x55FF55;
            public static final int ARMS = 0xFFAA00;
            public static final int LEGS = 0x55FFFF;

            private BodyPart() {}
        }

        public static final class ImprintStage {
            public static final int OWNER = Combat.Text.MUTED;
            public static final int ENHANCED = 0x55FF55;
            public static final int LEGENDARY = 0x5555FF;
            public static final int ASCENDED = 0xFF55FF;

            private ImprintStage() {}
        }

        public static final class WeaponTrait {
            public static final int EXECUTIONER = 0xFF4444;
            public static final int TYRANT_SLAYER = 0x8844FF;
            public static final int STYLISH = 0xFFAA00;
            public static final int BLOODTHIRSTY = 0xCC0000;
            public static final int HARMONIC = 0x44FFFF;
            public static final int PRECISION = 0xFFFF44;
            public static final int RELENTLESS = 0xFF8800;
            public static final int GUARDIAN = 0x44FF44;
            public static final int DEVASTATING = 0xFF6644;
            public static final int FINISHER = 0xAA0000;
            public static final int CLEAVING = 0x8888FF;
            public static final int RETALIATING = Combat.Text.PRIMARY;

            private WeaponTrait() {}
        }

        protected Combat() {}
    }

    public static class Testing {
        public static final int SEPARATOR = 0x44FFFFFF;
        public static final int THUMB_NORMAL = 0x88FFFFFF;
        public static final int THUMB_HOVER = 0xAAFFFFFF;
        public static final int TRACK = 0x44FFFFFF;
        public static final int HEADER_ACCENT = 0xFF3D5AFE;
        public static final int SUCCESS = 0xFF44FF88;
        public static final int ERROR = 0xFFFF4444;
        public static final int CYAN = 0xFF00E5FF;
        public static final int TELEMETRY_ACCENT = 0xFF00AAFF;
        public static final int WARNING = 0xFFFFAA00;
        public static final int ALERT = 0xFFFF5500;

        public static final class Status {
            public static final int PENDING = 0xFF888888;
            public static final int IN_PROGRESS = 0xFFFFAA00;
            public static final int PASSED = 0xFF55FF55;
            public static final int FAILED = 0xFFFF5555;
            public static final int SKIPPED = PENDING;

            private Status() {}
        }

        public static final class Priority {
            public static final int CRITICAL = 0xFFFF0000;
            public static final int HIGH = 0xFFFF8800;
            public static final int MEDIUM = 0xFFFFFF00;
            public static final int LOW = 0xFF88FF88;

            private Priority() {}
        }

        public static final class Level {
            private static final int[] COLORS = {
                0xFF888888,
                0xFF55FF55,
                0xFF5555FF,
                0xFFAA00AA,
                0xFFFFAA00,
                0xFFFF5555,
                0xFFFF55FF
            };

            private Level() {}

            public static int forLevel(int level) {
                int index = Math.max(1, level) - 1;
                if (index >= COLORS.length) {
                    index = COLORS.length - 1;
                }
                return COLORS[index];
            }
        }

        public static final class AchievementCategory {
            public static final int COMBAT = 0xFFFF5555;
            public static final int PRECISION = 0xFFFFAA00;
            public static final int RANGED = 0xFF55FF55;
            public static final int SURVIVAL = 0xFF5555FF;
            public static final int EXPLOSION = 0xFFFF8800;
            public static final int ALCHEMY = 0xFFAA00AA;
            public static final int EXPLORER = 0xFF00AAAA;
            public static final int DEDICATION = 0xFFFFFF00;
            public static final int TESTING = 0xFF55FFFF;
            public static final int SPECIAL = 0xFFFF55FF;

            private AchievementCategory() {}
        }

        public static final class Badge {
            public static final int BRONZE_TESTER = 0xFFCD7F32;
            public static final int SILVER_TESTER = 0xFFC0C0C0;
            public static final int GOLD_TESTER = 0xFFFFD700;
            public static final int DIAMOND_TESTER = 0xFFB9F2FF;
            public static final int COMBAT_SPECIALIST = AchievementCategory.COMBAT;
            public static final int PRECISION_EXPERT = AchievementCategory.PRECISION;
            public static final int COMPLETIONIST = AchievementCategory.SPECIAL;

            private Badge() {}
        }

        protected Testing() {}
    }

    public static class Endurance {
        public static final class Text {
            public static final int WHITE = 0xFFFFFF;
            public static final int MUTED = 0xAAAAAA;

            private Text() {}
        }

        public static final class Currency {
            public static final int TOKENS = 0xFFD700;
            public static final int COINS = 0xC0C0C0;
            public static final int PRESTIGE = 0xFF00FF;
            public static final int GEMS = 0x00B4FF;
            public static final int BLOOD_GEMS = 0xFF3333;

            private Currency() {}
        }

        public static final class LootTier {
            public static final int COMMON = Endurance.Text.MUTED;
            public static final int UNCOMMON = 0x55FF55;
            public static final int RARE = 0x5555FF;
            public static final int EPIC = 0xAA00AA;
            public static final int LEGENDARY = 0xFFAA00;
            public static final int MYTHIC = 0xFF5555;

            private LootTier() {}
        }

        public static final class RewardCategory {
            public static final int STATS = LootTier.UNCOMMON;
            public static final int PERKS = LootTier.RARE;
            public static final int UTILITY = 0xFFFF55;
            public static final int COSMETICS = 0xFF55FF;

            private RewardCategory() {}
        }

        public static final class StyleRank {
            public static final int D = 0x888888;
            public static final int C = 0x66FF66;
            public static final int B = 0x6666FF;
            public static final int A = 0xFFFF00;
            public static final int S = 0xFF9900;
            public static final int SS = 0xFF3300;
            public static final int SSS = 0xFF00FF;

            private StyleRank() {}
        }

        public static final class Momentum {
            public static final int STAGNANT = 0xFF4444;
            public static final int BUILDING = Endurance.Text.WHITE;
            public static final int HEATED = 0xFFAA00;
            public static final int OVERDRIVE = 0xFF00FF;

            private Momentum() {}
        }

        public static final class Flow {
            public static final int STALE = Momentum.STAGNANT;
            public static final int NEUTRAL = Endurance.Text.MUTED;
            public static final int FRESH = 0x44FF44;
            public static final int VIRTUOSO = Momentum.HEATED;

            private Flow() {}
        }

        public static final class Tension {
            public static final int CALM = 0x44FF44;
            public static final int BUILDING = 0xAAFF44;
            public static final int MODERATE = 0xFFFF44;
            public static final int HIGH = 0xFFAA44;
            public static final int CRITICAL = 0xFF4444;
            public static final int DEFAULT = Endurance.Text.WHITE;

            private Tension() {}
        }

        public static final class Tide {
            public static final int CALM = 0x55FF55;
            public static final int RISING = 0xFFFF55;
            public static final int HIGH = 0xFFAA00;
            public static final int STORM = 0xFF5555;
            public static final int APOCALYPSE = 0xAA0000;

            private Tide() {}
        }

        public static final class Boss {
            public static final int BERSERKER = 0xFF4444;
            public static final int SUMMONER = 0x9944FF;
            public static final int JUGGERNAUT = 0x44FF44;
            public static final int ASSASSIN = 0x4444FF;
            public static final int ELEMENTAL = 0xFFAA00;

            private Boss() {}
        }

        public static final class Contract {
            public static final int MINOR = 0xFFAA00;
            public static final int STANDARD = 0xFF6600;
            public static final int MAJOR = 0xFF3300;
            public static final int BLOOD = 0xFF0000;

            private Contract() {}
        }

        public static final class Kit {
            public static final int STARTER = Endurance.Text.MUTED;
            public static final int WARRIOR = 0x55FFFF;
            public static final int RANGER = 0x55FF55;
            public static final int TANK = 0x5555FF;
            public static final int MAGE = 0xAA00AA;
            public static final int BERSERKER = 0xFF5555;
            public static final int CUSTOM = 0xFFAA00;

            private Kit() {}
        }

        public static final class Prestige {
            public static final int PERK_SLOT = 0xFF55FF;
            public static final int MUTATOR_UNLOCK = 0xFF5555;
            public static final int ARENA_UNLOCK = 0x5555FF;
            public static final int COSMETIC_TITLE = 0xFFAA00;
            public static final int STARTING_BONUS = 0x55FF55;
            public static final int TOKEN_MULTIPLIER = 0xFFD700;
            public static final int EXCLUSIVE_PERK = 0xFF00FF;

            private Prestige() {}
        }

        public static final class PerkRarity {
            public static final int COMMON = 0xFFAAAAAA;
            public static final int UNCOMMON = 0xFF4ade80;
            public static final int RARE = 0xFF60a5fa;
            public static final int EPIC = 0xFFa855f7;
            public static final int LEGENDARY = 0xFFfbbf24;

            private PerkRarity() {}
        }

        public static final class PerkCategory {
            public static final int OFFENSE = 0xFF6B6B;
            public static final int DEFENSE = 0x4ECDC4;
            public static final int UTILITY = 0xFFE66D;
            public static final int VAMPIRIC = 0x9B59B6;
            public static final int ELEMENTAL = 0xE74C3C;
            public static final int COMBO = 0xF39C12;
            public static final int CURSE = 0x2C3E50;

            private PerkCategory() {}
        }

        public static final class Mutator {
            public static final int POSITIVE = 0x4ade80;
            public static final int NEGATIVE = 0xFF6B6B;
            public static final int NEUTRAL = 0x60a5fa;
            public static final int CHAOTIC = 0xFFAA00;

            private Mutator() {}
        }

        public static final class Synergy {
            public static final int MINOR = 0x7FFF7F;
            public static final int MODERATE = 0xFFFF7F;
            public static final int STRONG = 0xFFA500;
            public static final int LEGENDARY = 0xFF55FF;

            private Synergy() {}
        }

        public static final class ResonanceChain {
            public static final int DUO = 0xFFD700;
            public static final int TRINITY = 0x9400D3;
            public static final int APOCALYPSE = 0xFF0000;
            public static final int DEFAULT = Endurance.Text.WHITE;

            private ResonanceChain() {}
        }

        public static final class Hazard {
            public static final int FIRE = 0xFF6600;
            public static final int BLEED = 0xCC0000;
            public static final int VOID = 0x9900FF;
            public static final int ARC = 0x00CCFF;
            public static final int PSI = 0x660099;

            private Hazard() {}
        }

        public static final class Nemesis {
            public static final int PROJECTILE_DEFLECTION = 0x55AAFF;
            public static final int SWEEPING_BLADE = 0xFF5555;
            public static final int EARLY_PHASE_ACTIVATION = 0xFFAA00;
            public static final int DAMAGE_RESISTANCE = 0x888888;
            public static final int PROTECTIVE_HELMET = 0xAA8800;
            public static final int IMPROVED_REFLEXES = 0x55FF55;
            public static final int ENRAGED = 0xFF0000;
            public static final int VETERAN = 0xAA00AA;
            public static final int EVASION = 0x00FFFF;
            public static final int REGENERATION = 0x00FF00;
            public static final int SUMMONER = 0x8800AA;

            private Nemesis() {}
        }

        public static final class Bargain {
            public static final int GLASS_CANNON = 0xFF6B6B;
            public static final int SLUGGISH = 0x888888;
            public static final int FUMBLING = 0xFFAA00;
            public static final int HUNGER = 0x8B4513;
            public static final int ECHO_DAMAGE = 0x9932CC;
            public static final int BLOOD_TITHE = 0xDC143C;
            public static final int CROWD_PRESSURE = 0x4B0082;
            public static final int FRAILTY = 0x708090;
            public static final int BURNING_SOUL = 0xFF4500;
            public static final int COMBO_BREAKER = 0xFFD700;
            public static final int MOMENTUM_DRAIN = 0x00CED1;
            public static final int ONE_SHOT = 0x000000;
            public static final int ELITE_HUNTER = 0x8A2BE2;
            public static final int NO_HEALING = 0x2F4F4F;
            public static final int EXECUTIONER = 0xB22222;

            private Bargain() {}
        }

        public static final class BargainTier {
            public static final int MINOR = LootTier.UNCOMMON;
            public static final int MAJOR = LootTier.LEGENDARY;
            public static final int CURSED = LootTier.MYTHIC;

            private BargainTier() {}
        }

        protected Endurance() {}
    }
}
