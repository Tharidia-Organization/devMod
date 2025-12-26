package com.devmod.testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.devmod.util.ConfigPaths;

public class TesterProfile {
    private static final Logger LOGGER = LoggerFactory.getLogger(TesterProfile.class);
    // Lazy initialization to avoid NPE during class loading (FMLPaths not ready yet)
    private static Path getProfileFile() {
        return ConfigPaths.getTesterProfileFile();
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final TesterProfile INSTANCE = new TesterProfile();

    // === XP AND LEVELING ===
    private int totalXP = 0;
    private int currentLevel = 1;

    // Level thresholds (XP required for each level)
    private static final int[] LEVEL_THRESHOLDS = {
        0,      // Level 1: Newbie Tester
        100,    // Level 2: Apprentice Tester
        300,    // Level 3: Journeyman Tester
        600,    // Level 4: Expert Tester
        1000,   // Level 5: Master Tester
        1500,   // Level 6: Elite Tester
        2500    // Level 7: Legendary Tester
    };

    private static final String[] LEVEL_TITLES = {
        "Newbie Tester",
        "Apprentice Tester",
        "Journeyman Tester",
        "Expert Tester",
        "Master Tester",
        "Elite Tester",
        "Legendary Tester"
    };

    private static final int[] LEVEL_COLORS = {
        0xFF888888,  // Gray
        0xFF55FF55,  // Green
        0xFF5555FF,  // Blue
        0xFFAA00AA,  // Purple
        0xFFFFAA00,  // Gold
        0xFFFF5555,  // Red
        0xFFFF55FF   // Magenta (Legendary)
    };

    // === STREAKS ===
    private int currentDailyStreak = 0;
    private int maxDailyStreak = 0;
    private LocalDate lastTestDate = null;
    private int testsCompletedToday = 0;

    // Streak bonuses
    private static final float STREAK_3_BONUS = 0.5f;   // +50% XP at 3-day streak
    private static final float STREAK_7_BONUS = 1.0f;   // +100% XP at 7-day streak
    private static final float STREAK_30_BONUS = 2.0f;  // +200% XP at 30-day streak

    // === ACHIEVEMENTS ===
    private final Set<String> unlockedAchievements = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> achievementUnlockTimes = new ConcurrentHashMap<>();

    // Achievement definitions
    public enum Achievement {
        // Kill Achievements
        FIRST_BLOOD("first_blood", "First Blood", "Kill your first mob", 10, AchievementCategory.COMBAT),
        KILL_10("kill_10", "Getting Started", "Kill 10 mobs", 20, AchievementCategory.COMBAT),
        KILL_50("kill_50", "Monster Hunter", "Kill 50 mobs", 50, AchievementCategory.COMBAT),
        KILL_100("kill_100", "Exterminator", "Kill 100 mobs", 100, AchievementCategory.COMBAT),
        KILL_500("kill_500", "Genocide", "Kill 500 mobs", 250, AchievementCategory.COMBAT),

        // Headshot Achievements
        FIRST_HEADSHOT("first_headshot", "Bullseye!", "Land your first headshot", 15, AchievementCategory.PRECISION),
        HEADSHOT_10("headshot_10", "Sharpshooter", "Land 10 headshots", 30, AchievementCategory.PRECISION),
        HEADSHOT_50("headshot_50", "Marksman", "Land 50 headshots", 75, AchievementCategory.PRECISION),
        HEADSHOT_100("headshot_100", "Sniper Elite", "Land 100 headshots", 150, AchievementCategory.PRECISION),

        // Body Part Achievements
        BODY_PART_MASTER("body_part_master", "Anatomist", "Kill mobs hitting all body parts", 50, AchievementCategory.PRECISION),
        ARM_SPECIALIST("arm_specialist", "Disarmer", "Land 25 arm hits", 30, AchievementCategory.PRECISION),
        LEG_SPECIALIST("leg_specialist", "Kneecapper", "Land 25 leg hits", 30, AchievementCategory.PRECISION),

        // Mace Achievements
        FIRST_MACE_SMASH("first_mace_smash", "Ground Pound!", "Perform your first Mace Smash", 20, AchievementCategory.COMBAT),
        MACE_SMASH_10("mace_smash_10", "Gravity Master", "Perform 10 Mace Smashes", 40, AchievementCategory.COMBAT),
        MACE_SMASH_25("mace_smash_25", "Meteor Strike", "Perform 25 Mace Smashes", 75, AchievementCategory.COMBAT),

        // Arrow Achievements
        ARROW_KILL_10("arrow_kill_10", "Archer", "Kill 10 mobs with arrows", 25, AchievementCategory.RANGED),
        ARROW_KILL_50("arrow_kill_50", "Bowmaster", "Kill 50 mobs with arrows", 60, AchievementCategory.RANGED),
        LONG_SHOT("long_shot", "Long Shot", "Kill a mob from 50+ blocks away", 50, AchievementCategory.RANGED),

        // Damage Achievements
        DAMAGE_1000("damage_1000", "Pain Dealer", "Deal 1000 total damage", 40, AchievementCategory.COMBAT),
        DAMAGE_10000("damage_10000", "Destructor", "Deal 10000 total damage", 100, AchievementCategory.COMBAT),
        BIG_HIT("big_hit", "Critical Mass", "Deal 50+ damage in one hit", 35, AchievementCategory.COMBAT),
        MASSIVE_HIT("massive_hit", "Overkill", "Deal 100+ damage in one hit", 75, AchievementCategory.COMBAT),

        // Environmental Achievements
        SURVIVED_EXPLOSION("survived_explosion", "Blast Resistant", "Survive an explosion", 20, AchievementCategory.SURVIVAL),
        FIRE_WALKER("fire_walker", "Fire Walker", "Take 100 fire damage total", 25, AchievementCategory.SURVIVAL),
        LAVA_SWIMMER("lava_swimmer", "Lava Swimmer", "Take 50 lava damage", 30, AchievementCategory.SURVIVAL),
        FALL_DAMAGE_100("fall_damage_100", "Gravity Tester", "Take 100 fall damage total", 25, AchievementCategory.SURVIVAL),

        // Explosion Achievements
        TNT_LOVER("tnt_lover", "Explosive Personality", "Detonate 10 TNT", 30, AchievementCategory.EXPLOSION),
        CREEPER_FARMER("creeper_farmer", "Creeper Whisperer", "Witness 10 Creeper explosions", 25, AchievementCategory.EXPLOSION),
        EXPLOSION_KILLS_10("explosion_kills_10", "Demolition Expert", "Kill 10 mobs with explosions", 40, AchievementCategory.EXPLOSION),

        // Potion Achievements
        POTION_USER("potion_user", "Alchemist Apprentice", "Use 10 potions", 20, AchievementCategory.ALCHEMY),
        POTION_MASTER("potion_master", "Master Alchemist", "Use 50 potions", 50, AchievementCategory.ALCHEMY),
        EFFECT_VARIETY("effect_variety", "Effect Collector", "Experience 10 different effects", 40, AchievementCategory.ALCHEMY),

        // Overlay/UI Achievements
        OVERLAY_MASTER("overlay_master", "Overlay Master", "Use all 7 overlays", 50, AchievementCategory.EXPLORER),
        DASHBOARD_EXPLORER("dashboard_explorer", "Data Analyst", "Open Telemetry Dashboard 5 times", 20, AchievementCategory.EXPLORER),
        CONFIG_TWEAKER("config_tweaker", "Tweaker", "Open Mob/Weapon config screens 10 times", 25, AchievementCategory.EXPLORER),

        // Streak Achievements
        STREAK_3("streak_3", "Consistent", "Maintain a 3-day testing streak", 30, AchievementCategory.DEDICATION),
        STREAK_7("streak_7", "Dedicated", "Maintain a 7-day testing streak", 75, AchievementCategory.DEDICATION),
        STREAK_30("streak_30", "Committed", "Maintain a 30-day testing streak", 200, AchievementCategory.DEDICATION),

        // Test Completion Achievements
        TESTS_10("tests_10", "Test Runner", "Complete 10 tests", 25, AchievementCategory.TESTING),
        TESTS_25("tests_25", "Quality Assurance", "Complete 25 tests", 50, AchievementCategory.TESTING),
        TESTS_50("tests_50", "Bug Hunter", "Complete 50 tests", 100, AchievementCategory.TESTING),
        TESTS_100("tests_100", "QA Master", "Complete 100 tests", 200, AchievementCategory.TESTING),
        ALL_TESTS("all_tests", "Completionist", "Complete ALL tests", 500, AchievementCategory.TESTING),

        // Special Achievements
        SPEED_TESTER("speed_tester", "Speed Tester", "Complete 5 tests in one session", 35, AchievementCategory.SPECIAL),
        PERFECTIONIST("perfectionist", "Perfectionist", "Pass 10 tests without any failures", 50, AchievementCategory.SPECIAL),
        AUTO_MASTER("auto_master", "Auto Master", "Have 20 tests auto-complete", 75, AchievementCategory.SPECIAL),
        KILL_STREAK_5("kill_streak_5", "Rampage", "Get a 5 kill streak", 25, AchievementCategory.COMBAT),
        KILL_STREAK_10("kill_streak_10", "Unstoppable", "Get a 10 kill streak", 50, AchievementCategory.COMBAT),
        KILL_STREAK_25("kill_streak_25", "Godlike", "Get a 25 kill streak", 100, AchievementCategory.COMBAT);

        private final String id;
        private final String name;
        private final String description;
        private final int xpReward;
        private final AchievementCategory category;

        Achievement(String id, String name, String description, int xpReward, AchievementCategory category) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.xpReward = xpReward;
            this.category = category;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getXpReward() { return xpReward; }
        public AchievementCategory getCategory() { return category; }
    }

    public enum AchievementCategory {
        COMBAT("Combat", 0xFFFF5555),
        PRECISION("Precision", 0xFFFFAA00),
        RANGED("Ranged", 0xFF55FF55),
        SURVIVAL("Survival", 0xFF5555FF),
        EXPLOSION("Explosion", 0xFFFF8800),
        ALCHEMY("Alchemy", 0xFFAA00AA),
        EXPLORER("Explorer", 0xFF00AAAA),
        DEDICATION("Dedication", 0xFFFFFF00),
        TESTING("Testing", 0xFF55FFFF),
        SPECIAL("Special", 0xFFFF55FF);

        private final String name;
        private final int color;

        AchievementCategory(String name, int color) {
            this.name = name;
            this.color = color;
        }

        public String getName() { return name; }
        public int getColor() { return color; }
    }

    // === BADGES ===
    private final Set<String> earnedBadges = ConcurrentHashMap.newKeySet();

    public enum Badge {
        BRONZE_TESTER("bronze_tester", "Bronze Tester", "Reach Level 2", 0xFFCD7F32),
        SILVER_TESTER("silver_tester", "Silver Tester", "Reach Level 4", 0xFFC0C0C0),
        GOLD_TESTER("gold_tester", "Gold Tester", "Reach Level 6", 0xFFFFD700),
        DIAMOND_TESTER("diamond_tester", "Diamond Tester", "Reach Level 7", 0xFFB9F2FF),
        COMBAT_SPECIALIST("combat_specialist", "Combat Specialist", "Earn all Combat achievements", 0xFFFF5555),
        PRECISION_EXPERT("precision_expert", "Precision Expert", "Earn all Precision achievements", 0xFFFFAA00),
        COMPLETIONIST("completionist", "Completionist", "Earn all achievements", 0xFFFF55FF);

        private final String id;
        private final String name;
        private final String requirement;
        private final int color;

        Badge(String id, String name, String requirement, int color) {
            this.id = id;
            this.name = name;
            this.requirement = requirement;
            this.color = color;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getRequirement() { return requirement; }
        public int getColor() { return color; }
    }

    // === LISTENERS ===
    private final List<ProfileEventListener> listeners = new ArrayList<>();

    public interface ProfileEventListener {
        default void onXPGained(int amount, int total) {}
        default void onLevelUp(int newLevel, String title) {}
        default void onAchievementUnlocked(Achievement achievement) {}
        default void onBadgeEarned(Badge badge) {}
        default void onStreakUpdated(int currentStreak) {}
    }

    private TesterProfile() {
        load();
    }

    // ==================== XP SYSTEM ====================

    /**
     * Award XP to the tester with streak bonuses applied.
     */
    public void awardXP(int baseXP, String reason) {
        float multiplier = 1.0f + getStreakBonus();
        int finalXP = Math.round(baseXP * multiplier);

        totalXP += finalXP;
        LOGGER.info("Awarded {} XP ({} base x{} streak) for: {}", finalXP, baseXP, multiplier, reason);

        // Check for level up
        int newLevel = calculateLevel();
        if (newLevel > currentLevel) {
            int oldLevel = currentLevel;
            currentLevel = newLevel;
            String title = getLevelTitle();
            LOGGER.info("LEVEL UP! {} -> {} ({})", oldLevel, newLevel, title);

            // Notify listeners
            for (ProfileEventListener listener : listeners) {
                listener.onLevelUp(newLevel, title);
            }

            // Check for level-based badges
            checkLevelBadges();
        }

        // Notify XP gained
        for (ProfileEventListener listener : listeners) {
            listener.onXPGained(finalXP, totalXP);
        }

        save();
    }

    private int calculateLevel() {
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (totalXP >= LEVEL_THRESHOLDS[i]) {
                return i + 1;
            }
        }
        return 1;
    }

    public int getTotalXP() { return totalXP; }
    public int getCurrentLevel() { return currentLevel; }

    public String getLevelTitle() {
        return LEVEL_TITLES[Math.min(currentLevel - 1, LEVEL_TITLES.length - 1)];
    }

    public int getLevelColor() {
        return LEVEL_COLORS[Math.min(currentLevel - 1, LEVEL_COLORS.length - 1)];
    }

    public int getXPForNextLevel() {
        if (currentLevel >= LEVEL_THRESHOLDS.length) {
            return 0; // Max level
        }
        return LEVEL_THRESHOLDS[currentLevel];
    }

    public int getXPProgress() {
        if (currentLevel >= LEVEL_THRESHOLDS.length) {
            return 0;
        }
        int currentThreshold = LEVEL_THRESHOLDS[currentLevel - 1];
        return totalXP - currentThreshold;
    }

    public int getXPRequired() {
        if (currentLevel >= LEVEL_THRESHOLDS.length) {
            return 0;
        }
        int currentThreshold = LEVEL_THRESHOLDS[currentLevel - 1];
        int nextThreshold = LEVEL_THRESHOLDS[currentLevel];
        return nextThreshold - currentThreshold;
    }

    public float getLevelProgress() {
        int required = getXPRequired();
        if (required == 0) return 1.0f;
        return (float) getXPProgress() / required;
    }

    // ==================== STREAK SYSTEM ====================

    /**
     * Update daily streak when a test is completed.
     */
    public void onTestCompleted() {
        LocalDate today = LocalDate.now();

        if (lastTestDate == null) {
            // First test ever
            currentDailyStreak = 1;
            lastTestDate = today;
            testsCompletedToday = 1;
        } else if (lastTestDate.equals(today)) {
            // Same day
            testsCompletedToday++;
        } else if (lastTestDate.plusDays(1).equals(today)) {
            // Consecutive day - streak continues!
            currentDailyStreak++;
            lastTestDate = today;
            testsCompletedToday = 1;

            if (currentDailyStreak > maxDailyStreak) {
                maxDailyStreak = currentDailyStreak;
            }

            LOGGER.info("Daily streak: {} days!", currentDailyStreak);

            // Check streak achievements
            checkStreakAchievements();
        } else {
            // Streak broken
            LOGGER.info("Streak broken! Was {} days", currentDailyStreak);
            currentDailyStreak = 1;
            lastTestDate = today;
            testsCompletedToday = 1;
        }

        // Notify listeners
        for (ProfileEventListener listener : listeners) {
            listener.onStreakUpdated(currentDailyStreak);
        }

        save();
    }

    public float getStreakBonus() {
        if (currentDailyStreak >= 30) return STREAK_30_BONUS;
        if (currentDailyStreak >= 7) return STREAK_7_BONUS;
        if (currentDailyStreak >= 3) return STREAK_3_BONUS;
        return 0f;
    }

    public int getCurrentDailyStreak() { return currentDailyStreak; }
    public int getMaxDailyStreak() { return maxDailyStreak; }
    public int getTestsCompletedToday() { return testsCompletedToday; }

    // ==================== ACHIEVEMENT SYSTEM ====================

    /**
     * Unlock an achievement and award XP.
     */
    public boolean unlockAchievement(Achievement achievement) {
        if (unlockedAchievements.contains(achievement.getId())) {
            return false; // Already unlocked
        }

        unlockedAchievements.add(achievement.getId());
        achievementUnlockTimes.put(achievement.getId(), Instant.now());

        LOGGER.info("Achievement Unlocked: {} - {}", achievement.getName(), achievement.getDescription());

        // Award XP (without streak bonus for achievements)
        totalXP += achievement.getXpReward();

        // Notify listeners
        for (ProfileEventListener listener : listeners) {
            listener.onAchievementUnlocked(achievement);
        }

        // Check for category completion badges
        checkCategoryBadges();

        save();
        return true;
    }

    /**
     * Check and unlock achievements based on TesterProgress.
     */
    public void checkAchievements(TesterProgress progress) {
        // Kill achievements
        if (progress.getTotalKills() >= 1) unlockAchievement(Achievement.FIRST_BLOOD);
        if (progress.getTotalKills() >= 10) unlockAchievement(Achievement.KILL_10);
        if (progress.getTotalKills() >= 50) unlockAchievement(Achievement.KILL_50);
        if (progress.getTotalKills() >= 100) unlockAchievement(Achievement.KILL_100);
        if (progress.getTotalKills() >= 500) unlockAchievement(Achievement.KILL_500);

        // Headshot achievements
        if (progress.getHeadshots() >= 1) unlockAchievement(Achievement.FIRST_HEADSHOT);
        if (progress.getHeadshots() >= 10) unlockAchievement(Achievement.HEADSHOT_10);
        if (progress.getHeadshots() >= 50) unlockAchievement(Achievement.HEADSHOT_50);
        if (progress.getHeadshots() >= 100) unlockAchievement(Achievement.HEADSHOT_100);

        // Body part achievements
        if (progress.hasKilledWithEveryBodyPart()) unlockAchievement(Achievement.BODY_PART_MASTER);
        if (progress.getHitsOnBodyPart("ARMS") >= 25) unlockAchievement(Achievement.ARM_SPECIALIST);
        if (progress.getHitsOnBodyPart("LEGS") >= 25) unlockAchievement(Achievement.LEG_SPECIALIST);

        // Mace achievements
        if (progress.getMaceSmashKills() >= 1) unlockAchievement(Achievement.FIRST_MACE_SMASH);
        if (progress.getMaceSmashKills() >= 10) unlockAchievement(Achievement.MACE_SMASH_10);
        if (progress.getMaceSmashKills() >= 25) unlockAchievement(Achievement.MACE_SMASH_25);

        // Arrow achievements
        if (progress.getArrowKills() >= 10) unlockAchievement(Achievement.ARROW_KILL_10);
        if (progress.getArrowKills() >= 50) unlockAchievement(Achievement.ARROW_KILL_50);

        // Damage achievements
        if (progress.getTotalDamageDealt() >= 1000) unlockAchievement(Achievement.DAMAGE_1000);
        if (progress.getTotalDamageDealt() >= 10000) unlockAchievement(Achievement.DAMAGE_10000);
        if (progress.getHighestSingleHit() >= 50) unlockAchievement(Achievement.BIG_HIT);
        if (progress.getHighestSingleHit() >= 100) unlockAchievement(Achievement.MASSIVE_HIT);

        // Environmental achievements
        if (progress.hasSurvivedExplosion()) unlockAchievement(Achievement.SURVIVED_EXPLOSION);
        if (progress.getFireDamageTaken() >= 100) unlockAchievement(Achievement.FIRE_WALKER);
        if (progress.getLavaDamageTaken() >= 50) unlockAchievement(Achievement.LAVA_SWIMMER);
        if (progress.getFallDamageTaken() >= 100) unlockAchievement(Achievement.FALL_DAMAGE_100);

        // Explosion achievements
        if (progress.getTntDetonated() >= 10) unlockAchievement(Achievement.TNT_LOVER);
        if (progress.getCreeperExplosions() >= 10) unlockAchievement(Achievement.CREEPER_FARMER);
        if (progress.getMobsKilledByExplosion() >= 10) unlockAchievement(Achievement.EXPLOSION_KILLS_10);

        // Potion achievements
        if (progress.getPotionsUsed() >= 10) unlockAchievement(Achievement.POTION_USER);
        if (progress.getPotionsUsed() >= 50) unlockAchievement(Achievement.POTION_MASTER);
        if (progress.getUniqueEffectsUsed() >= 10) unlockAchievement(Achievement.EFFECT_VARIETY);

        // Overlay/UI achievements
        if (progress.hasUsedAllOverlays()) unlockAchievement(Achievement.OVERLAY_MASTER);
        if (progress.getScreenOpens("telemetry") >= 5) unlockAchievement(Achievement.DASHBOARD_EXPLORER);
        if (progress.getScreenOpens("mob_config") + progress.getScreenOpens("weapon_editor") >= 10) {
            unlockAchievement(Achievement.CONFIG_TWEAKER);
        }

        // Kill streak achievements
        if (progress.getMaxKillStreak() >= 5) unlockAchievement(Achievement.KILL_STREAK_5);
        if (progress.getMaxKillStreak() >= 10) unlockAchievement(Achievement.KILL_STREAK_10);
        if (progress.getMaxKillStreak() >= 25) unlockAchievement(Achievement.KILL_STREAK_25);

        // Test completion achievements
        if (progress.getTestsCompleted() >= 10) unlockAchievement(Achievement.TESTS_10);
        if (progress.getTestsCompleted() >= 25) unlockAchievement(Achievement.TESTS_25);
        if (progress.getTestsCompleted() >= 50) unlockAchievement(Achievement.TESTS_50);
        if (progress.getTestsCompleted() >= 100) unlockAchievement(Achievement.TESTS_100);

        // Auto-complete achievements
        if (progress.getTestsAutoCompleted() >= 20) unlockAchievement(Achievement.AUTO_MASTER);
    }

    private void checkStreakAchievements() {
        if (currentDailyStreak >= 3) unlockAchievement(Achievement.STREAK_3);
        if (currentDailyStreak >= 7) unlockAchievement(Achievement.STREAK_7);
        if (currentDailyStreak >= 30) unlockAchievement(Achievement.STREAK_30);
    }

    public boolean hasAchievement(Achievement achievement) {
        return unlockedAchievements.contains(achievement.getId());
    }

    public boolean hasAchievement(String achievementId) {
        return unlockedAchievements.contains(achievementId);
    }

    public int getUnlockedAchievementCount() {
        return unlockedAchievements.size();
    }

    public int getTotalAchievementCount() {
        return Achievement.values().length;
    }

    public Set<String> getUnlockedAchievements() {
        return Collections.unmodifiableSet(unlockedAchievements);
    }

    // ==================== BADGE SYSTEM ====================

    private void checkLevelBadges() {
        if (currentLevel >= 2) earnBadge(Badge.BRONZE_TESTER);
        if (currentLevel >= 4) earnBadge(Badge.SILVER_TESTER);
        if (currentLevel >= 6) earnBadge(Badge.GOLD_TESTER);
        if (currentLevel >= 7) earnBadge(Badge.DIAMOND_TESTER);
    }

    private void checkCategoryBadges() {
        // Check if all Combat achievements unlocked
        boolean allCombat = Arrays.stream(Achievement.values())
            .filter(a -> a.getCategory() == AchievementCategory.COMBAT)
            .allMatch(a -> unlockedAchievements.contains(a.getId()));
        if (allCombat) earnBadge(Badge.COMBAT_SPECIALIST);

        // Check if all Precision achievements unlocked
        boolean allPrecision = Arrays.stream(Achievement.values())
            .filter(a -> a.getCategory() == AchievementCategory.PRECISION)
            .allMatch(a -> unlockedAchievements.contains(a.getId()));
        if (allPrecision) earnBadge(Badge.PRECISION_EXPERT);

        // Check if ALL achievements unlocked
        if (unlockedAchievements.size() == Achievement.values().length) {
            earnBadge(Badge.COMPLETIONIST);
        }
    }

    private void earnBadge(Badge badge) {
        if (earnedBadges.contains(badge.getId())) {
            return;
        }

        earnedBadges.add(badge.getId());
        LOGGER.info("Badge Earned: {} - {}", badge.getName(), badge.getRequirement());

        for (ProfileEventListener listener : listeners) {
            listener.onBadgeEarned(badge);
        }

        save();
    }

    public boolean hasBadge(Badge badge) {
        return earnedBadges.contains(badge.getId());
    }

    public Set<String> getEarnedBadges() {
        return Collections.unmodifiableSet(earnedBadges);
    }

    // ==================== LISTENERS ====================

    public void addListener(ProfileEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ProfileEventListener listener) {
        listeners.remove(listener);
    }

    // ==================== PERSISTENCE ====================

    public void save() {
        try {
            Files.createDirectories(getProfileFile().getParent());

            JsonObject root = new JsonObject();

            // XP and Level
            root.addProperty("totalXP", totalXP);
            root.addProperty("currentLevel", currentLevel);

            // Streaks
            root.addProperty("currentDailyStreak", currentDailyStreak);
            root.addProperty("maxDailyStreak", maxDailyStreak);
            root.addProperty("lastTestDate", lastTestDate != null ? lastTestDate.toString() : null);
            root.addProperty("testsCompletedToday", testsCompletedToday);

            // Achievements
            JsonArray achievementsArray = new JsonArray();
            for (String id : unlockedAchievements) {
                achievementsArray.add(id);
            }
            root.add("unlockedAchievements", achievementsArray);

            // Achievement times
            JsonObject timesObj = new JsonObject();
            for (Map.Entry<String, Instant> entry : achievementUnlockTimes.entrySet()) {
                timesObj.addProperty(entry.getKey(), entry.getValue().toString());
            }
            root.add("achievementTimes", timesObj);

            // Badges
            JsonArray badgesArray = new JsonArray();
            for (String id : earnedBadges) {
                badgesArray.add(id);
            }
            root.add("earnedBadges", badgesArray);

            Files.writeString(getProfileFile(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to save tester profile: {}", e.getMessage());
        }
    }

    public void load() {
        try {
            // Safety check - getProfileFile() might have issues in certain thread contexts
            if (getProfileFile() == null || !Files.exists(getProfileFile())) {
                return;
            }
        } catch (Exception e) {
            // Files.exists() can throw on certain file systems or thread contexts
            LOGGER.debug("Could not check profile file existence: {}", e.getMessage());
            return;
        }

        try {
            String content = Files.readString(getProfileFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();

            // XP and Level
            totalXP = root.has("totalXP") ? root.get("totalXP").getAsInt() : 0;
            currentLevel = root.has("currentLevel") ? root.get("currentLevel").getAsInt() : 1;

            // Streaks
            currentDailyStreak = root.has("currentDailyStreak") ? root.get("currentDailyStreak").getAsInt() : 0;
            maxDailyStreak = root.has("maxDailyStreak") ? root.get("maxDailyStreak").getAsInt() : 0;
            testsCompletedToday = root.has("testsCompletedToday") ? root.get("testsCompletedToday").getAsInt() : 0;

            if (root.has("lastTestDate") && !root.get("lastTestDate").isJsonNull()) {
                lastTestDate = LocalDate.parse(root.get("lastTestDate").getAsString());

                // Check if streak is still valid
                LocalDate today = LocalDate.now();
                if (lastTestDate.plusDays(1).isBefore(today)) {
                    // Streak expired
                    currentDailyStreak = 0;
                    testsCompletedToday = 0;
                }
            }

            // Achievements
            if (root.has("unlockedAchievements")) {
                JsonArray arr = root.getAsJsonArray("unlockedAchievements");
                for (JsonElement elem : arr) {
                    unlockedAchievements.add(elem.getAsString());
                }
            }

            // Achievement times
            if (root.has("achievementTimes")) {
                JsonObject times = root.getAsJsonObject("achievementTimes");
                for (String key : times.keySet()) {
                    achievementUnlockTimes.put(key, Instant.parse(times.get(key).getAsString()));
                }
            }

            // Badges
            if (root.has("earnedBadges")) {
                JsonArray arr = root.getAsJsonArray("earnedBadges");
                for (JsonElement elem : arr) {
                    earnedBadges.add(elem.getAsString());
                }
            }

            LOGGER.info("Tester profile loaded: Level {}, {} XP, {}/{} achievements",
                currentLevel, totalXP, unlockedAchievements.size(), Achievement.values().length);
        } catch (Exception e) {
            LOGGER.error("Failed to load tester profile: {}", e.getMessage());
        }
    }

    /**
     * Reset the entire profile (for fresh start).
     */
    public void reset() {
        totalXP = 0;
        currentLevel = 1;
        currentDailyStreak = 0;
        maxDailyStreak = 0;
        lastTestDate = null;
        testsCompletedToday = 0;
        unlockedAchievements.clear();
        achievementUnlockTimes.clear();
        earnedBadges.clear();
        save();
    }

    /**
     * Get a summary of the profile for display.
     */
    public String getSummary() {
        return String.format(
            "%s (Level %d)\n" +
            "XP: %d | Next: %d\n" +
            "Streak: %d days (max: %d)\n" +
            "Achievements: %d/%d\n" +
            "Badges: %d/%d",
            getLevelTitle(), currentLevel,
            totalXP, getXPForNextLevel(),
            currentDailyStreak, maxDailyStreak,
            unlockedAchievements.size(), Achievement.values().length,
            earnedBadges.size(), Badge.values().length
        );
    }
}
