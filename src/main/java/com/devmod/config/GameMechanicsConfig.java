package com.devmod.config;

import net.neoforged.neoforge.common.ModConfigSpec;
public class GameMechanicsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ============================================
    // EXECUTION SYSTEM
    // ============================================

    public static final ModConfigSpec.DoubleValue EXECUTION_HP_THRESHOLD;
    public static final ModConfigSpec.IntValue EXECUTION_DURATION_TICKS;
    public static final ModConfigSpec.IntValue EXECUTION_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue EXECUTION_STYLE_REWARD;
    public static final ModConfigSpec.DoubleValue EXECUTION_HP_REGEN_PERCENT;
    public static final ModConfigSpec.DoubleValue EXECUTION_DROP_BOOST;
    public static final ModConfigSpec.DoubleValue EXECUTION_INTERRUPT_VULNERABILITY;
    public static final ModConfigSpec.DoubleValue EXECUTION_RANGE;

    // ============================================
    // COMBO SYSTEM
    // ============================================

    public static final ModConfigSpec.IntValue COMBO_TIMEOUT_TICKS;
    public static final ModConfigSpec.IntValue COMBO_BASE_POINTS;
    public static final ModConfigSpec.DoubleValue COMBO_MULTIPLIER_INCREMENT;
    public static final ModConfigSpec.DoubleValue COMBO_MAX_MULTIPLIER;
    public static final ModConfigSpec.IntValue COMBO_FINISHER_THRESHOLD;
    public static final ModConfigSpec.IntValue COMBO_JUGGLE_BONUS;
    public static final ModConfigSpec.IntValue COMBO_HEADSHOT_BONUS;
    public static final ModConfigSpec.IntValue COMBO_EXECUTION_BONUS;

    // ============================================
    // STYLE RANK SYSTEM
    // ============================================

    public static final ModConfigSpec.IntValue STYLE_RANK_D_THRESHOLD;
    public static final ModConfigSpec.IntValue STYLE_RANK_C_THRESHOLD;
    public static final ModConfigSpec.IntValue STYLE_RANK_B_THRESHOLD;
    public static final ModConfigSpec.IntValue STYLE_RANK_A_THRESHOLD;
    public static final ModConfigSpec.IntValue STYLE_RANK_S_THRESHOLD;
    public static final ModConfigSpec.IntValue STYLE_RANK_SS_THRESHOLD;
    public static final ModConfigSpec.IntValue STYLE_RANK_SSS_THRESHOLD;
    public static final ModConfigSpec.DoubleValue STYLE_DECAY_RATE;
    public static final ModConfigSpec.IntValue STYLE_DECAY_DELAY_TICKS;

    // ============================================
    // MOMENTUM / FLOW STATE
    // ============================================

    public static final ModConfigSpec.DoubleValue MOMENTUM_DECAY_RATE;
    public static final ModConfigSpec.DoubleValue MOMENTUM_KILL_BOOST;
    public static final ModConfigSpec.DoubleValue MOMENTUM_HIT_BOOST;
    public static final ModConfigSpec.DoubleValue MOMENTUM_OVERDRIVE_THRESHOLD;
    public static final ModConfigSpec.IntValue MOMENTUM_OVERDRIVE_DURATION_TICKS;

    public static final ModConfigSpec.IntValue FLOW_STALE_THRESHOLD_TICKS;
    public static final ModConfigSpec.DoubleValue FLOW_FRESH_BONUS_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue FLOW_VIRTUOSO_THRESHOLD;
    public static final ModConfigSpec.IntValue FLOW_VIRTUOSO_DURATION_TICKS;

    // ============================================
    // DEVIL'S BARGAIN (Curse Altars)
    // ============================================

    public static final ModConfigSpec.BooleanValue BARGAIN_ENABLED;
    public static final ModConfigSpec.IntValue BARGAIN_ALTAR_SPAWN_WAVE;
    public static final ModConfigSpec.IntValue BARGAIN_ALTAR_INTERVAL_WAVES;
    public static final ModConfigSpec.IntValue BARGAIN_MAX_CURSES_PER_RUN;
    public static final ModConfigSpec.IntValue BARGAIN_CHOICE_TIMEOUT_TICKS;
    public static final ModConfigSpec.DoubleValue BARGAIN_CURSE_POWER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BARGAIN_BOON_POWER_MULTIPLIER;

    // ============================================
    // ARENA HAZARDS
    // ============================================

    public static final ModConfigSpec.BooleanValue HAZARD_ENABLED;
    public static final ModConfigSpec.IntValue HAZARD_FLOOR_CRUMBLE_WAVE;
    public static final ModConfigSpec.IntValue HAZARD_BLOOD_MOON_WAVE;
    public static final ModConfigSpec.IntValue HAZARD_ARENA_SHRINK_WAVE;
    public static final ModConfigSpec.IntValue HAZARD_LIGHTNING_STORM_WAVE;
    public static final ModConfigSpec.IntValue HAZARD_VOID_RIFTS_WAVE;

    public static final ModConfigSpec.IntValue HAZARD_FLOOR_CRUMBLE_DURATION;
    public static final ModConfigSpec.IntValue HAZARD_BLOOD_MOON_DURATION;
    public static final ModConfigSpec.IntValue HAZARD_ARENA_SHRINK_DURATION;
    public static final ModConfigSpec.IntValue HAZARD_LIGHTNING_STORM_DURATION;
    public static final ModConfigSpec.IntValue HAZARD_VOID_RIFTS_DURATION;

    public static final ModConfigSpec.DoubleValue HAZARD_LIGHTNING_DAMAGE;
    public static final ModConfigSpec.DoubleValue HAZARD_VOID_RIFT_DAMAGE;
    public static final ModConfigSpec.DoubleValue HAZARD_BLOOD_MOON_MOB_BUFF;
    public static final ModConfigSpec.DoubleValue HAZARD_ARENA_SHRINK_RATE;

    // ============================================
    // PERK SYNERGY WEB
    // ============================================

    public static final ModConfigSpec.BooleanValue PERK_SYNERGY_ENABLED;
    public static final ModConfigSpec.BooleanValue PERK_HIDDEN_PERKS_ENABLED;
    public static final ModConfigSpec.BooleanValue PERK_SACRIFICE_ENABLED;
    public static final ModConfigSpec.IntValue PERK_SACRIFICE_STYLE_COST;
    public static final ModConfigSpec.IntValue PERK_DISCOVERY_XP_BASE;
    public static final ModConfigSpec.DoubleValue PERK_SYNERGY_BONUS_MULTIPLIER;

    // ============================================
    // WAVE CONFIGURATION
    // ============================================

    public static final ModConfigSpec.IntValue WAVE_BASE_MOB_COUNT;
    public static final ModConfigSpec.DoubleValue WAVE_MOB_SCALING;
    public static final ModConfigSpec.IntValue WAVE_INTERMISSION_TICKS;
    public static final ModConfigSpec.DoubleValue WAVE_ELITE_CHANCE_BASE;
    public static final ModConfigSpec.DoubleValue WAVE_ELITE_CHANCE_SCALING;
    public static final ModConfigSpec.IntValue WAVE_BOSS_INTERVAL;

    // ============================================
    // REWARD CONFIGURATION
    // ============================================

    public static final ModConfigSpec.DoubleValue REWARD_XP_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue REWARD_STYLE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue REWARD_DROP_RATE_BONUS;
    public static final ModConfigSpec.IntValue REWARD_BONUS_CHEST_WAVE_INTERVAL;

    // ============================================
    // SEASON PASS SYSTEM
    // ============================================

    public static final ModConfigSpec.IntValue SEASON_MAX_TIER;
    public static final ModConfigSpec.IntValue SEASON_XP_PER_TIER;
    public static final ModConfigSpec.IntValue SEASON_DURATION_DAYS;
    public static final ModConfigSpec.IntValue SEASON_XP_PER_KILL;
    public static final ModConfigSpec.IntValue SEASON_XP_PER_WAVE;
    public static final ModConfigSpec.IntValue SEASON_XP_PER_BOSS;
    public static final ModConfigSpec.IntValue SEASON_XP_DAILY_CHALLENGE;
    public static final ModConfigSpec.IntValue SEASON_XP_WEEKLY_CHALLENGE;
    public static final ModConfigSpec.IntValue SEASON_XP_STYLE_S;
    public static final ModConfigSpec.IntValue SEASON_XP_STYLE_SS;
    public static final ModConfigSpec.IntValue SEASON_XP_STYLE_SSS;
    public static final ModConfigSpec.IntValue SEASON_XP_FLAWLESS_WAVE;
    public static final ModConfigSpec.IntValue SEASON_XP_HIGH_COMBO_50;
    public static final ModConfigSpec.IntValue SEASON_XP_HIGH_COMBO_100;
    public static final ModConfigSpec.DoubleValue SEASON_XP_BOOST_MULTIPLIER;
    public static final ModConfigSpec.IntValue SEASON_XP_BOOST_DURATION_MINUTES;

    // ============================================
    // GUILD SYSTEM
    // ============================================

    public static final ModConfigSpec.IntValue GUILD_MAX_SIZE;
    public static final ModConfigSpec.IntValue GUILD_MAX_LEVEL;
    public static final ModConfigSpec.IntValue GUILD_CREATE_COST;
    public static final ModConfigSpec.IntValue GUILD_XP_PER_LEVEL_BASE;
    public static final ModConfigSpec.DoubleValue GUILD_XP_SCALING;
    public static final ModConfigSpec.IntValue GUILD_BANK_TAX_PERCENT;
    public static final ModConfigSpec.IntValue GUILD_OBJECTIVE_KILLS_TARGET;
    public static final ModConfigSpec.IntValue GUILD_OBJECTIVE_WAVES_TARGET;
    public static final ModConfigSpec.IntValue GUILD_OBJECTIVE_BOSSES_TARGET;
    public static final ModConfigSpec.IntValue GUILD_OBJECTIVE_TOKENS_TARGET;
    public static final ModConfigSpec.IntValue GUILD_OBJECTIVE_CHALLENGES_TARGET;
    public static final ModConfigSpec.IntValue GUILD_PERK_UNLOCK_LEVEL_1;
    public static final ModConfigSpec.IntValue GUILD_PERK_UNLOCK_LEVEL_2;
    public static final ModConfigSpec.IntValue GUILD_PERK_UNLOCK_LEVEL_3;
    public static final ModConfigSpec.DoubleValue GUILD_BONUS_TOKENS_5;
    public static final ModConfigSpec.DoubleValue GUILD_BONUS_TOKENS_10;
    public static final ModConfigSpec.DoubleValue GUILD_BONUS_TOKENS_15;
    public static final ModConfigSpec.DoubleValue GUILD_BONUS_XP_5;
    public static final ModConfigSpec.DoubleValue GUILD_BONUS_XP_10;

    // ============================================
    // ASCENSION/PRESTIGE RESET SYSTEM
    // ============================================

    public static final ModConfigSpec.IntValue ASCENSION_MAX_LEVEL;
    public static final ModConfigSpec.IntValue ASCENSION_MIN_PRESTIGE;
    public static final ModConfigSpec.IntValue ASCENSION_COST_SCALING;
    public static final ModConfigSpec.DoubleValue ASCENSION_DAMAGE_BONUS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue ASCENSION_DEFENSE_BONUS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue ASCENSION_TOKEN_MULTIPLIER_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue ASCENSION_LIFESTEAL_BONUS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue ASCENSION_CRIT_BONUS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue ASCENSION_COMBO_DECAY_REDUCTION_PER_LEVEL;
    public static final ModConfigSpec.IntValue ASCENSION_EXTRA_PERK_SLOTS_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue ASCENSION_STARTING_HP_BONUS_PER_LEVEL;

    // ============================================
    // TENSION SYSTEM (Boss Spawn Mechanics)
    // ============================================

    public static final ModConfigSpec.DoubleValue TENSION_BASE_WAVE_GAIN;
    public static final ModConfigSpec.DoubleValue TENSION_NO_HIT_BONUS;
    public static final ModConfigSpec.DoubleValue TENSION_MIN_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TENSION_MAX_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TENSION_ELITE_KILL_BONUS;
    public static final ModConfigSpec.DoubleValue TENSION_COMBO_BONUS_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TENSION_STYLE_S_BONUS;
    public static final ModConfigSpec.IntValue TENSION_MIN_WAVES_BEFORE_BOSS;
    public static final ModConfigSpec.IntValue TENSION_MAX_WAVES_WITHOUT_BOSS;
    public static final ModConfigSpec.DoubleValue TENSION_DECAY_AFTER_BOSS;

    // ============================================
    // PERK SYSTEM (Rarity and Values)
    // ============================================

    public static final ModConfigSpec.IntValue PERK_COMMON_WEIGHT;
    public static final ModConfigSpec.IntValue PERK_UNCOMMON_WEIGHT;
    public static final ModConfigSpec.IntValue PERK_RARE_WEIGHT;
    public static final ModConfigSpec.IntValue PERK_EPIC_WEIGHT;
    public static final ModConfigSpec.IntValue PERK_LEGENDARY_WEIGHT;
    public static final ModConfigSpec.IntValue PERK_CHOICES_PER_SELECTION;
    public static final ModConfigSpec.IntValue PERK_REROLL_COST_BASE;
    public static final ModConfigSpec.IntValue PERK_REROLL_COST_INCREMENT;
    public static final ModConfigSpec.DoubleValue PERK_DAMAGE_BONUS_COMMON;
    public static final ModConfigSpec.DoubleValue PERK_DAMAGE_BONUS_UNCOMMON;
    public static final ModConfigSpec.DoubleValue PERK_DAMAGE_BONUS_RARE;
    public static final ModConfigSpec.DoubleValue PERK_DAMAGE_BONUS_EPIC;
    public static final ModConfigSpec.DoubleValue PERK_DAMAGE_BONUS_LEGENDARY;

    // ============================================
    // CHALLENGE SYSTEM (Daily/Weekly)
    // ============================================

    public static final ModConfigSpec.IntValue CHALLENGE_DAILY_COUNT;
    public static final ModConfigSpec.IntValue CHALLENGE_WEEKLY_COUNT;
    public static final ModConfigSpec.IntValue CHALLENGE_DAILY_RESET_HOUR;
    public static final ModConfigSpec.IntValue CHALLENGE_WEEKLY_RESET_DAY;
    public static final ModConfigSpec.IntValue CHALLENGE_KILLS_TARGET_MIN;
    public static final ModConfigSpec.IntValue CHALLENGE_KILLS_TARGET_MAX;
    public static final ModConfigSpec.IntValue CHALLENGE_WAVES_TARGET_MIN;
    public static final ModConfigSpec.IntValue CHALLENGE_WAVES_TARGET_MAX;
    public static final ModConfigSpec.IntValue CHALLENGE_COMBO_TARGET_MIN;
    public static final ModConfigSpec.IntValue CHALLENGE_COMBO_TARGET_MAX;
    public static final ModConfigSpec.IntValue CHALLENGE_DAILY_TOKEN_REWARD;
    public static final ModConfigSpec.IntValue CHALLENGE_WEEKLY_TOKEN_REWARD;
    public static final ModConfigSpec.IntValue CHALLENGE_DAILY_XP_REWARD;
    public static final ModConfigSpec.IntValue CHALLENGE_WEEKLY_XP_REWARD;

    static {
        // ============================================
        // EXECUTION SYSTEM
        // ============================================
        BUILDER.push("execution");

        EXECUTION_HP_THRESHOLD = BUILDER
                .comment("HP percentage threshold to allow execution (0.15 = 15%)")
                .defineInRange("hpThreshold", 0.15, 0.01, 0.50);

        EXECUTION_DURATION_TICKS = BUILDER
                .comment("Duration of execution animation in ticks (20 = 1 second)")
                .defineInRange("durationTicks", 40, 10, 100);

        EXECUTION_COOLDOWN_TICKS = BUILDER
                .comment("Cooldown between executions in ticks")
                .defineInRange("cooldownTicks", 60, 0, 200);

        EXECUTION_STYLE_REWARD = BUILDER
                .comment("Style points awarded for successful execution")
                .defineInRange("styleReward", 200, 0, 1000);

        EXECUTION_HP_REGEN_PERCENT = BUILDER
                .comment("Percentage of max HP restored on execution (0.05 = 5%)")
                .defineInRange("hpRegenPercent", 0.05, 0.0, 0.50);

        EXECUTION_DROP_BOOST = BUILDER
                .comment("Drop rate boost for executed enemies (0.30 = 30% extra)")
                .defineInRange("dropBoost", 0.30, 0.0, 2.0);

        EXECUTION_INTERRUPT_VULNERABILITY = BUILDER
                .comment("Damage multiplier if execution is interrupted (2.0 = 2x damage)")
                .defineInRange("interruptVulnerability", 2.0, 1.0, 5.0);

        EXECUTION_RANGE = BUILDER
                .comment("Range in blocks to detect executable targets")
                .defineInRange("range", 4.0, 1.0, 10.0);

        BUILDER.pop();

        // ============================================
        // COMBO SYSTEM
        // ============================================
        BUILDER.push("combo");

        COMBO_TIMEOUT_TICKS = BUILDER
                .comment("Ticks before combo resets (60 = 3 seconds)")
                .defineInRange("timeoutTicks", 60, 20, 200);

        COMBO_BASE_POINTS = BUILDER
                .comment("Base style points per hit in combo")
                .defineInRange("basePoints", 10, 1, 100);

        COMBO_MULTIPLIER_INCREMENT = BUILDER
                .comment("Multiplier increase per combo hit")
                .defineInRange("multiplierIncrement", 0.1, 0.01, 0.5);

        COMBO_MAX_MULTIPLIER = BUILDER
                .comment("Maximum combo multiplier")
                .defineInRange("maxMultiplier", 4.0, 1.0, 10.0);

        COMBO_FINISHER_THRESHOLD = BUILDER
                .comment("Combo count to unlock finisher moves")
                .defineInRange("finisherThreshold", 10, 3, 50);

        COMBO_JUGGLE_BONUS = BUILDER
                .comment("Bonus points for juggling (airborne hits)")
                .defineInRange("juggleBonus", 25, 0, 200);

        COMBO_HEADSHOT_BONUS = BUILDER
                .comment("Bonus points for headshots")
                .defineInRange("headshotBonus", 15, 0, 200);

        COMBO_EXECUTION_BONUS = BUILDER
                .comment("Bonus points for execution during combo")
                .defineInRange("executionBonus", 50, 0, 500);

        BUILDER.pop();

        // ============================================
        // STYLE RANK SYSTEM
        // ============================================
        BUILDER.push("styleRank");

        STYLE_RANK_D_THRESHOLD = BUILDER
                .comment("Points required for D rank")
                .defineInRange("dThreshold", 0, 0, 100);

        STYLE_RANK_C_THRESHOLD = BUILDER
                .comment("Points required for C rank")
                .defineInRange("cThreshold", 100, 50, 500);

        STYLE_RANK_B_THRESHOLD = BUILDER
                .comment("Points required for B rank")
                .defineInRange("bThreshold", 300, 100, 1000);

        STYLE_RANK_A_THRESHOLD = BUILDER
                .comment("Points required for A rank")
                .defineInRange("aThreshold", 600, 200, 2000);

        STYLE_RANK_S_THRESHOLD = BUILDER
                .comment("Points required for S rank")
                .defineInRange("sThreshold", 1000, 400, 5000);

        STYLE_RANK_SS_THRESHOLD = BUILDER
                .comment("Points required for SS rank")
                .defineInRange("ssThreshold", 1500, 600, 8000);

        STYLE_RANK_SSS_THRESHOLD = BUILDER
                .comment("Points required for SSS rank")
                .defineInRange("sssThreshold", 2500, 1000, 15000);

        STYLE_DECAY_RATE = BUILDER
                .comment("Points lost per second when not in combat")
                .defineInRange("decayRate", 5.0, 0.0, 50.0);

        STYLE_DECAY_DELAY_TICKS = BUILDER
                .comment("Ticks of inactivity before style starts decaying")
                .defineInRange("decayDelayTicks", 100, 0, 400);

        BUILDER.pop();

        // ============================================
        // MOMENTUM / FLOW STATE
        // ============================================
        BUILDER.push("momentum");

        MOMENTUM_DECAY_RATE = BUILDER
                .comment("Momentum decay per second (0.1 = 10% per second)")
                .defineInRange("decayRate", 0.05, 0.0, 0.5);

        MOMENTUM_KILL_BOOST = BUILDER
                .comment("Momentum gained on kill (0.2 = 20%)")
                .defineInRange("killBoost", 0.2, 0.0, 1.0);

        MOMENTUM_HIT_BOOST = BUILDER
                .comment("Momentum gained per hit (0.05 = 5%)")
                .defineInRange("hitBoost", 0.05, 0.0, 0.5);

        MOMENTUM_OVERDRIVE_THRESHOLD = BUILDER
                .comment("Momentum threshold to enter overdrive (0.9 = 90%)")
                .defineInRange("overdriveThreshold", 0.9, 0.5, 1.0);

        MOMENTUM_OVERDRIVE_DURATION_TICKS = BUILDER
                .comment("Overdrive duration in ticks after reaching threshold")
                .defineInRange("overdriveDurationTicks", 200, 60, 600);

        BUILDER.pop();

        BUILDER.push("flowState");

        FLOW_STALE_THRESHOLD_TICKS = BUILDER
                .comment("Ticks without action before combat becomes 'stale'")
                .defineInRange("staleThresholdTicks", 100, 20, 400);

        FLOW_FRESH_BONUS_MULTIPLIER = BUILDER
                .comment("Style multiplier when combat is 'fresh' (varied)")
                .defineInRange("freshBonusMultiplier", 1.5, 1.0, 3.0);

        FLOW_VIRTUOSO_THRESHOLD = BUILDER
                .comment("Action variety threshold for virtuoso bonus (0-1)")
                .defineInRange("virtuosoThreshold", 0.8, 0.5, 1.0);

        FLOW_VIRTUOSO_DURATION_TICKS = BUILDER
                .comment("Duration of virtuoso bonus in ticks")
                .defineInRange("virtuosoDurationTicks", 100, 20, 300);

        BUILDER.pop();

        // ============================================
        // DEVIL'S BARGAIN
        // ============================================
        BUILDER.push("bargain");

        BARGAIN_ENABLED = BUILDER
                .comment("Enable devil's bargain curse altars")
                .define("enabled", true);

        BARGAIN_ALTAR_SPAWN_WAVE = BUILDER
                .comment("First wave where altars can spawn")
                .defineInRange("altarSpawnWave", 3, 1, 20);

        BARGAIN_ALTAR_INTERVAL_WAVES = BUILDER
                .comment("Waves between altar spawns")
                .defineInRange("altarIntervalWaves", 2, 1, 10);

        BARGAIN_MAX_CURSES_PER_RUN = BUILDER
                .comment("Maximum curses a player can accept per run")
                .defineInRange("maxCursesPerRun", 5, 1, 20);

        BARGAIN_CHOICE_TIMEOUT_TICKS = BUILDER
                .comment("Ticks to make a choice at altar before it disappears")
                .defineInRange("choiceTimeoutTicks", 400, 100, 1200);

        BARGAIN_CURSE_POWER_MULTIPLIER = BUILDER
                .comment("Multiplier for curse severity")
                .defineInRange("cursePowerMultiplier", 1.0, 0.1, 3.0);

        BARGAIN_BOON_POWER_MULTIPLIER = BUILDER
                .comment("Multiplier for boon strength")
                .defineInRange("boonPowerMultiplier", 1.0, 0.1, 3.0);

        BUILDER.pop();

        // ============================================
        // ARENA HAZARDS
        // ============================================
        BUILDER.push("hazards");

        HAZARD_ENABLED = BUILDER
                .comment("Enable dynamic arena hazards")
                .define("enabled", true);

        BUILDER.push("triggerWaves");
        HAZARD_FLOOR_CRUMBLE_WAVE = BUILDER
                .comment("Wave when floor crumble hazard activates")
                .defineInRange("floorCrumble", 3, 1, 50);

        HAZARD_BLOOD_MOON_WAVE = BUILDER
                .comment("Wave when blood moon hazard activates")
                .defineInRange("bloodMoon", 5, 1, 50);

        HAZARD_ARENA_SHRINK_WAVE = BUILDER
                .comment("Wave when arena shrink hazard activates")
                .defineInRange("arenaShrink", 7, 1, 50);

        HAZARD_LIGHTNING_STORM_WAVE = BUILDER
                .comment("Wave when lightning storm hazard activates")
                .defineInRange("lightningStorm", 9, 1, 50);

        HAZARD_VOID_RIFTS_WAVE = BUILDER
                .comment("Wave when void rifts hazard activates")
                .defineInRange("voidRifts", 11, 1, 50);
        BUILDER.pop();

        BUILDER.push("durations");
        HAZARD_FLOOR_CRUMBLE_DURATION = BUILDER
                .comment("Floor crumble duration in ticks")
                .defineInRange("floorCrumble", 120, 20, 600);

        HAZARD_BLOOD_MOON_DURATION = BUILDER
                .comment("Blood moon duration in ticks")
                .defineInRange("bloodMoon", 600, 100, 2400);

        HAZARD_ARENA_SHRINK_DURATION = BUILDER
                .comment("Arena shrink duration in ticks")
                .defineInRange("arenaShrink", 400, 100, 1200);

        HAZARD_LIGHTNING_STORM_DURATION = BUILDER
                .comment("Lightning storm duration in ticks")
                .defineInRange("lightningStorm", 300, 60, 1200);

        HAZARD_VOID_RIFTS_DURATION = BUILDER
                .comment("Void rifts duration in ticks")
                .defineInRange("voidRifts", 200, 60, 800);
        BUILDER.pop();

        BUILDER.push("damage");
        HAZARD_LIGHTNING_DAMAGE = BUILDER
                .comment("Damage dealt by lightning strikes")
                .defineInRange("lightning", 6.0, 1.0, 20.0);

        HAZARD_VOID_RIFT_DAMAGE = BUILDER
                .comment("Damage per tick in void rift")
                .defineInRange("voidRift", 2.0, 0.5, 10.0);

        HAZARD_BLOOD_MOON_MOB_BUFF = BUILDER
                .comment("Damage multiplier for mobs during blood moon")
                .defineInRange("bloodMoonMobBuff", 1.5, 1.0, 3.0);

        HAZARD_ARENA_SHRINK_RATE = BUILDER
                .comment("Blocks shrunk per tick (0.05 = 1 block per second)")
                .defineInRange("shrinkRate", 0.05, 0.01, 0.5);
        BUILDER.pop();

        BUILDER.pop(); // hazards

        // ============================================
        // PERK SYNERGY WEB
        // ============================================
        BUILDER.push("perkSynergy");

        PERK_SYNERGY_ENABLED = BUILDER
                .comment("Enable perk synergy system")
                .define("enabled", true);

        PERK_HIDDEN_PERKS_ENABLED = BUILDER
                .comment("Enable hidden perk discoveries")
                .define("hiddenPerksEnabled", true);

        PERK_SACRIFICE_ENABLED = BUILDER
                .comment("Enable perk sacrifice mechanic")
                .define("sacrificeEnabled", true);

        PERK_SACRIFICE_STYLE_COST = BUILDER
                .comment("Style points required for perk sacrifice")
                .defineInRange("sacrificeStyleCost", 500, 0, 5000);

        PERK_DISCOVERY_XP_BASE = BUILDER
                .comment("Base XP for discovering hidden perks")
                .defineInRange("discoveryXpBase", 100, 10, 1000);

        PERK_SYNERGY_BONUS_MULTIPLIER = BUILDER
                .comment("Multiplier for synergy bonuses between perks")
                .defineInRange("synergyBonusMultiplier", 1.0, 0.1, 3.0);

        BUILDER.pop();

        // ============================================
        // WAVE CONFIGURATION
        // ============================================
        BUILDER.push("waves");

        WAVE_BASE_MOB_COUNT = BUILDER
                .comment("Base number of mobs in wave 1")
                .defineInRange("baseMobCount", 5, 1, 50);

        WAVE_MOB_SCALING = BUILDER
                .comment("Mobs added per wave (1.5 = +50% more each wave)")
                .defineInRange("mobScaling", 1.2, 1.0, 3.0);

        WAVE_INTERMISSION_TICKS = BUILDER
                .comment("Ticks between waves")
                .defineInRange("intermissionTicks", 100, 20, 600);

        WAVE_ELITE_CHANCE_BASE = BUILDER
                .comment("Base chance for elite mobs (0.1 = 10%)")
                .defineInRange("eliteChanceBase", 0.1, 0.0, 1.0);

        WAVE_ELITE_CHANCE_SCALING = BUILDER
                .comment("Elite chance increase per wave")
                .defineInRange("eliteChanceScaling", 0.02, 0.0, 0.1);

        WAVE_BOSS_INTERVAL = BUILDER
                .comment("Boss spawns every N waves")
                .defineInRange("bossInterval", 5, 1, 20);

        BUILDER.pop();

        // ============================================
        // REWARD CONFIGURATION
        // ============================================
        BUILDER.push("rewards");

        REWARD_XP_MULTIPLIER = BUILDER
                .comment("Global XP multiplier for quest rewards")
                .defineInRange("xpMultiplier", 1.0, 0.1, 10.0);

        REWARD_STYLE_MULTIPLIER = BUILDER
                .comment("Global style points multiplier")
                .defineInRange("styleMultiplier", 1.0, 0.1, 10.0);

        REWARD_DROP_RATE_BONUS = BUILDER
                .comment("Global drop rate bonus (0.0 = no bonus)")
                .defineInRange("dropRateBonus", 0.0, 0.0, 5.0);

        REWARD_BONUS_CHEST_WAVE_INTERVAL = BUILDER
                .comment("Bonus reward chest spawns every N waves")
                .defineInRange("bonusChestWaveInterval", 3, 1, 20);

        BUILDER.pop();

        // ============================================
        // SEASON PASS SYSTEM
        // ============================================
        BUILDER.push("seasonPass");

        SEASON_MAX_TIER = BUILDER
                .comment("Maximum tier in season pass (100 = 100 tiers)")
                .defineInRange("maxTier", 100, 10, 500);

        SEASON_XP_PER_TIER = BUILDER
                .comment("XP required to advance one tier")
                .defineInRange("xpPerTier", 1000, 100, 10000);

        SEASON_DURATION_DAYS = BUILDER
                .comment("Season duration in days")
                .defineInRange("durationDays", 90, 7, 365);

        SEASON_XP_PER_KILL = BUILDER
                .comment("Season XP gained per mob kill")
                .defineInRange("xpPerKill", 2, 0, 100);

        SEASON_XP_PER_WAVE = BUILDER
                .comment("Season XP gained per wave completion")
                .defineInRange("xpPerWave", 50, 0, 500);

        SEASON_XP_PER_BOSS = BUILDER
                .comment("Season XP gained per boss kill")
                .defineInRange("xpPerBoss", 200, 0, 1000);

        SEASON_XP_DAILY_CHALLENGE = BUILDER
                .comment("Season XP for completing daily challenge")
                .defineInRange("xpDailyChallenge", 500, 0, 5000);

        SEASON_XP_WEEKLY_CHALLENGE = BUILDER
                .comment("Season XP for completing weekly challenge")
                .defineInRange("xpWeeklyChallenge", 2000, 0, 20000);

        SEASON_XP_STYLE_S = BUILDER
                .comment("Season XP for achieving S rank")
                .defineInRange("xpStyleS", 100, 0, 1000);

        SEASON_XP_STYLE_SS = BUILDER
                .comment("Season XP for achieving SS rank")
                .defineInRange("xpStyleSS", 200, 0, 2000);

        SEASON_XP_STYLE_SSS = BUILDER
                .comment("Season XP for achieving SSS rank")
                .defineInRange("xpStyleSSS", 500, 0, 5000);

        SEASON_XP_FLAWLESS_WAVE = BUILDER
                .comment("Season XP for completing wave without damage")
                .defineInRange("xpFlawlessWave", 75, 0, 500);

        SEASON_XP_HIGH_COMBO_50 = BUILDER
                .comment("Season XP for 50+ combo")
                .defineInRange("xpHighCombo50", 150, 0, 1000);

        SEASON_XP_HIGH_COMBO_100 = BUILDER
                .comment("Season XP for 100+ combo")
                .defineInRange("xpHighCombo100", 300, 0, 2000);

        SEASON_XP_BOOST_MULTIPLIER = BUILDER
                .comment("XP boost multiplier (1.5 = 50% bonus)")
                .defineInRange("xpBoostMultiplier", 1.5, 1.0, 5.0);

        SEASON_XP_BOOST_DURATION_MINUTES = BUILDER
                .comment("XP boost duration in minutes")
                .defineInRange("xpBoostDurationMinutes", 60, 5, 1440);

        BUILDER.pop();

        // ============================================
        // GUILD SYSTEM
        // ============================================
        BUILDER.push("guild");

        GUILD_MAX_SIZE = BUILDER
                .comment("Maximum members per guild")
                .defineInRange("maxSize", 50, 5, 200);

        GUILD_MAX_LEVEL = BUILDER
                .comment("Maximum guild level")
                .defineInRange("maxLevel", 20, 5, 100);

        GUILD_CREATE_COST = BUILDER
                .comment("Token cost to create a guild")
                .defineInRange("createCost", 5000, 0, 100000);

        GUILD_XP_PER_LEVEL_BASE = BUILDER
                .comment("Base XP required per guild level")
                .defineInRange("xpPerLevelBase", 1000, 100, 10000);

        GUILD_XP_SCALING = BUILDER
                .comment("XP requirement scaling per level (1.5 = +50% per level)")
                .defineInRange("xpScaling", 1.5, 1.0, 3.0);

        GUILD_BANK_TAX_PERCENT = BUILDER
                .comment("Percentage of deposits taxed for guild bank")
                .defineInRange("bankTaxPercent", 10, 0, 50);

        GUILD_OBJECTIVE_KILLS_TARGET = BUILDER
                .comment("Weekly objective: total kills target")
                .defineInRange("objectiveKillsTarget", 10000, 100, 100000);

        GUILD_OBJECTIVE_WAVES_TARGET = BUILDER
                .comment("Weekly objective: total waves target")
                .defineInRange("objectiveWavesTarget", 500, 10, 5000);

        GUILD_OBJECTIVE_BOSSES_TARGET = BUILDER
                .comment("Weekly objective: total boss kills target")
                .defineInRange("objectiveBossesTarget", 100, 5, 1000);

        GUILD_OBJECTIVE_TOKENS_TARGET = BUILDER
                .comment("Weekly objective: total tokens earned target")
                .defineInRange("objectiveTokensTarget", 50000, 1000, 1000000);

        GUILD_OBJECTIVE_CHALLENGES_TARGET = BUILDER
                .comment("Weekly objective: challenges completed target")
                .defineInRange("objectiveChallengesTarget", 50, 5, 500);

        GUILD_PERK_UNLOCK_LEVEL_1 = BUILDER
                .comment("Guild level for first perk unlock")
                .defineInRange("perkUnlockLevel1", 5, 1, 20);

        GUILD_PERK_UNLOCK_LEVEL_2 = BUILDER
                .comment("Guild level for second perk unlock")
                .defineInRange("perkUnlockLevel2", 10, 2, 30);

        GUILD_PERK_UNLOCK_LEVEL_3 = BUILDER
                .comment("Guild level for third perk unlock")
                .defineInRange("perkUnlockLevel3", 15, 3, 40);

        GUILD_BONUS_TOKENS_5 = BUILDER
                .comment("Token bonus percentage at tier 1 (0.05 = 5%)")
                .defineInRange("bonusTokens5", 0.05, 0.0, 0.5);

        GUILD_BONUS_TOKENS_10 = BUILDER
                .comment("Token bonus percentage at tier 2 (0.10 = 10%)")
                .defineInRange("bonusTokens10", 0.10, 0.0, 0.5);

        GUILD_BONUS_TOKENS_15 = BUILDER
                .comment("Token bonus percentage at tier 3 (0.15 = 15%)")
                .defineInRange("bonusTokens15", 0.15, 0.0, 0.5);

        GUILD_BONUS_XP_5 = BUILDER
                .comment("XP bonus percentage at tier 1 (0.05 = 5%)")
                .defineInRange("bonusXp5", 0.05, 0.0, 0.5);

        GUILD_BONUS_XP_10 = BUILDER
                .comment("XP bonus percentage at tier 2 (0.10 = 10%)")
                .defineInRange("bonusXp10", 0.10, 0.0, 0.5);

        BUILDER.pop();

        // ============================================
        // ASCENSION/PRESTIGE RESET SYSTEM
        // ============================================
        BUILDER.push("ascension");

        ASCENSION_MAX_LEVEL = BUILDER
                .comment("Maximum ascension level")
                .defineInRange("maxLevel", 10, 1, 50);

        ASCENSION_MIN_PRESTIGE = BUILDER
                .comment("Minimum prestige required for first ascension")
                .defineInRange("minPrestige", 100, 10, 1000);

        ASCENSION_COST_SCALING = BUILDER
                .comment("Additional prestige cost per ascension level")
                .defineInRange("costScaling", 50, 0, 500);

        ASCENSION_DAMAGE_BONUS_PER_LEVEL = BUILDER
                .comment("Damage bonus per ascension level (0.05 = 5%)")
                .defineInRange("damageBonusPerLevel", 0.05, 0.0, 0.5);

        ASCENSION_DEFENSE_BONUS_PER_LEVEL = BUILDER
                .comment("Defense bonus per ascension level (0.03 = 3%)")
                .defineInRange("defenseBonusPerLevel", 0.03, 0.0, 0.3);

        ASCENSION_TOKEN_MULTIPLIER_PER_LEVEL = BUILDER
                .comment("Token multiplier per ascension level (0.10 = 10%)")
                .defineInRange("tokenMultiplierPerLevel", 0.10, 0.0, 1.0);

        ASCENSION_LIFESTEAL_BONUS_PER_LEVEL = BUILDER
                .comment("Lifesteal bonus per ascension level (0.01 = 1%)")
                .defineInRange("lifestealBonusPerLevel", 0.01, 0.0, 0.1);

        ASCENSION_CRIT_BONUS_PER_LEVEL = BUILDER
                .comment("Critical hit bonus per ascension level (0.02 = 2%)")
                .defineInRange("critBonusPerLevel", 0.02, 0.0, 0.2);

        ASCENSION_COMBO_DECAY_REDUCTION_PER_LEVEL = BUILDER
                .comment("Combo decay reduction per ascension level (0.05 = 5%)")
                .defineInRange("comboDecayReductionPerLevel", 0.05, 0.0, 0.5);

        ASCENSION_EXTRA_PERK_SLOTS_PER_LEVEL = BUILDER
                .comment("Extra perk slots per ascension level (capped at 3)")
                .defineInRange("extraPerkSlotsPerLevel", 1, 0, 3);

        ASCENSION_STARTING_HP_BONUS_PER_LEVEL = BUILDER
                .comment("Starting HP bonus per ascension level (0.05 = 5%)")
                .defineInRange("startingHpBonusPerLevel", 0.05, 0.0, 0.5);

        BUILDER.pop();

        // ============================================
        // TENSION SYSTEM
        // ============================================
        BUILDER.push("tension");

        TENSION_BASE_WAVE_GAIN = BUILDER
                .comment("Base tension gained per wave (0.12 = 12%)")
                .defineInRange("baseWaveGain", 0.12, 0.0, 1.0);

        TENSION_NO_HIT_BONUS = BUILDER
                .comment("Bonus tension for completing wave without damage (0.20 = 20%)")
                .defineInRange("noHitBonus", 0.20, 0.0, 1.0);

        TENSION_MIN_THRESHOLD = BUILDER
                .comment("Minimum tension to spawn boss (0.70 = 70%)")
                .defineInRange("minThreshold", 0.70, 0.1, 1.0);

        TENSION_MAX_THRESHOLD = BUILDER
                .comment("Maximum tension (auto boss spawn) (1.0 = 100%)")
                .defineInRange("maxThreshold", 1.0, 0.5, 2.0);

        TENSION_ELITE_KILL_BONUS = BUILDER
                .comment("Bonus tension for elite kill (0.05 = 5%)")
                .defineInRange("eliteKillBonus", 0.05, 0.0, 0.5);

        TENSION_COMBO_BONUS_THRESHOLD = BUILDER
                .comment("Combo threshold for tension bonus")
                .defineInRange("comboBonusThreshold", 0.03, 0.0, 0.2);

        TENSION_STYLE_S_BONUS = BUILDER
                .comment("Tension bonus for S+ style rank (0.10 = 10%)")
                .defineInRange("styleSBonus", 0.10, 0.0, 0.5);

        TENSION_MIN_WAVES_BEFORE_BOSS = BUILDER
                .comment("Minimum waves before boss can spawn")
                .defineInRange("minWavesBeforeBoss", 3, 1, 20);

        TENSION_MAX_WAVES_WITHOUT_BOSS = BUILDER
                .comment("Maximum waves without boss (forced spawn)")
                .defineInRange("maxWavesWithoutBoss", 8, 3, 30);

        TENSION_DECAY_AFTER_BOSS = BUILDER
                .comment("Tension decay after boss defeat (0.50 = 50%)")
                .defineInRange("decayAfterBoss", 0.50, 0.0, 1.0);

        BUILDER.pop();

        // ============================================
        // PERK SYSTEM (Rarity and Values)
        // ============================================
        BUILDER.push("perkRarity");

        PERK_COMMON_WEIGHT = BUILDER
                .comment("Drop weight for common perks")
                .defineInRange("commonWeight", 60, 0, 100);

        PERK_UNCOMMON_WEIGHT = BUILDER
                .comment("Drop weight for uncommon perks")
                .defineInRange("uncommonWeight", 25, 0, 100);

        PERK_RARE_WEIGHT = BUILDER
                .comment("Drop weight for rare perks")
                .defineInRange("rareWeight", 10, 0, 100);

        PERK_EPIC_WEIGHT = BUILDER
                .comment("Drop weight for epic perks")
                .defineInRange("epicWeight", 4, 0, 100);

        PERK_LEGENDARY_WEIGHT = BUILDER
                .comment("Drop weight for legendary perks")
                .defineInRange("legendaryWeight", 1, 0, 100);

        PERK_CHOICES_PER_SELECTION = BUILDER
                .comment("Number of perk choices offered per selection")
                .defineInRange("choicesPerSelection", 3, 2, 6);

        PERK_REROLL_COST_BASE = BUILDER
                .comment("Base token cost to reroll perks")
                .defineInRange("rerollCostBase", 50, 0, 500);

        PERK_REROLL_COST_INCREMENT = BUILDER
                .comment("Additional cost per reroll")
                .defineInRange("rerollCostIncrement", 25, 0, 200);

        PERK_DAMAGE_BONUS_COMMON = BUILDER
                .comment("Damage bonus for common perk (0.05 = 5%)")
                .defineInRange("damageBonusCommon", 0.05, 0.0, 0.5);

        PERK_DAMAGE_BONUS_UNCOMMON = BUILDER
                .comment("Damage bonus for uncommon perk (0.10 = 10%)")
                .defineInRange("damageBonusUncommon", 0.10, 0.0, 0.5);

        PERK_DAMAGE_BONUS_RARE = BUILDER
                .comment("Damage bonus for rare perk (0.15 = 15%)")
                .defineInRange("damageBonusRare", 0.15, 0.0, 0.5);

        PERK_DAMAGE_BONUS_EPIC = BUILDER
                .comment("Damage bonus for epic perk (0.20 = 20%)")
                .defineInRange("damageBonusEpic", 0.20, 0.0, 0.5);

        PERK_DAMAGE_BONUS_LEGENDARY = BUILDER
                .comment("Damage bonus for legendary perk (0.30 = 30%)")
                .defineInRange("damageBonusLegendary", 0.30, 0.0, 1.0);

        BUILDER.pop();

        // ============================================
        // CHALLENGE SYSTEM (Daily/Weekly)
        // ============================================
        BUILDER.push("challenges");

        CHALLENGE_DAILY_COUNT = BUILDER
                .comment("Number of daily challenges")
                .defineInRange("dailyCount", 3, 1, 10);

        CHALLENGE_WEEKLY_COUNT = BUILDER
                .comment("Number of weekly challenges")
                .defineInRange("weeklyCount", 2, 1, 5);

        CHALLENGE_DAILY_RESET_HOUR = BUILDER
                .comment("Hour of day for daily reset (0-23, UTC)")
                .defineInRange("dailyResetHour", 0, 0, 23);

        CHALLENGE_WEEKLY_RESET_DAY = BUILDER
                .comment("Day of week for weekly reset (1=Monday, 7=Sunday)")
                .defineInRange("weeklyResetDay", 1, 1, 7);

        CHALLENGE_KILLS_TARGET_MIN = BUILDER
                .comment("Minimum kill target for challenges")
                .defineInRange("killsTargetMin", 50, 5, 200);

        CHALLENGE_KILLS_TARGET_MAX = BUILDER
                .comment("Maximum kill target for challenges")
                .defineInRange("killsTargetMax", 200, 50, 1000);

        CHALLENGE_WAVES_TARGET_MIN = BUILDER
                .comment("Minimum wave target for challenges")
                .defineInRange("wavesTargetMin", 5, 1, 20);

        CHALLENGE_WAVES_TARGET_MAX = BUILDER
                .comment("Maximum wave target for challenges")
                .defineInRange("wavesTargetMax", 15, 5, 50);

        CHALLENGE_COMBO_TARGET_MIN = BUILDER
                .comment("Minimum combo target for challenges")
                .defineInRange("comboTargetMin", 25, 5, 50);

        CHALLENGE_COMBO_TARGET_MAX = BUILDER
                .comment("Maximum combo target for challenges")
                .defineInRange("comboTargetMax", 100, 50, 500);

        CHALLENGE_DAILY_TOKEN_REWARD = BUILDER
                .comment("Token reward for daily challenge completion")
                .defineInRange("dailyTokenReward", 100, 0, 1000);

        CHALLENGE_WEEKLY_TOKEN_REWARD = BUILDER
                .comment("Token reward for weekly challenge completion")
                .defineInRange("weeklyTokenReward", 500, 0, 5000);

        CHALLENGE_DAILY_XP_REWARD = BUILDER
                .comment("Season XP for daily challenge completion")
                .defineInRange("dailyXpReward", 500, 0, 5000);

        CHALLENGE_WEEKLY_XP_REWARD = BUILDER
                .comment("Season XP for weekly challenge completion")
                .defineInRange("weeklyXpReward", 2000, 0, 20000);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ============================================
    // HELPER METHODS FOR RUNTIME ACCESS
    // ============================================

    /**
     * Get effective value with optional per-instance override.
     */
    public static double getEffective(ModConfigSpec.DoubleValue configValue, Double override) {
        return override != null ? override : configValue.get();
    }

    /**
     * Get effective value with optional per-instance override.
     */
    public static int getEffective(ModConfigSpec.IntValue configValue, Integer override) {
        return override != null ? override : configValue.get();
    }

    /**
     * Get effective value with optional per-instance override.
     */
    public static boolean getEffective(ModConfigSpec.BooleanValue configValue, Boolean override) {
        return override != null ? override : configValue.get();
    }
}
