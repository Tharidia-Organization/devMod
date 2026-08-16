package com.devmod.endurance;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import com.devmod.DevMod;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.mailbox.news.NewsCategory;
import com.devmod.mailbox.news.NewsManager;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;

public class GamificationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GamificationManager.class);

    // Custom TypeAdapter for LocalDate (Gson cannot serialize java.time classes by default)
    private static final TypeAdapter<LocalDate> LOCAL_DATE_ADAPTER = new TypeAdapter<LocalDate>() {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

        @Override
        public void write(JsonWriter out, LocalDate value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(FORMATTER));
            }
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDate.parse(in.nextString(), FORMATTER);
        }
    };

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(LocalDate.class, LOCAL_DATE_ADAPTER)
        .create();

    public static final GamificationManager INSTANCE = new GamificationManager();

    private Path dataDirectory;

    // Player profiles - thread-safe for concurrent access
    private final Map<UUID, PlayerProfile> playerProfiles = new java.util.concurrent.ConcurrentHashMap<>();

    // Global leaderboards
    private final Leaderboard allTimeLeaderboard = new Leaderboard("All Time");
    private Leaderboard weeklyLeaderboard = new Leaderboard("Weekly");
    private Leaderboard dailyLeaderboard = new Leaderboard("Daily");
    private Leaderboard templateCoverageLeaderboard = new Leaderboard("Template Coverage");

    // Available badges
    private final Map<String, Badge> availableBadges = new LinkedHashMap<>();

    // Active challenges
    private final List<Challenge> dailyChallenges = new ArrayList<>();
    private final List<Challenge> weeklyChallenges = new ArrayList<>();

    // Date tracking for resets
    private LocalDate lastDailyReset;
    private LocalDate lastWeeklyReset;

    private GamificationManager() {
        initializeBadges();
    }

    /**
     * Initialize with data directory.
     */
    public void initialize(Path configDir) {
        // Same double-nesting as EnduranceAnalytics had: getConfigDir() already ends in "devmod".
        this.dataDirectory = configDir.resolve("gamification");
        try {
            Files.createDirectories(dataDirectory);
            loadData();
            checkForResets();
            LOGGER.info("[Gamification] Initialized with {} badges, {} player profiles",
                availableBadges.size(), playerProfiles.size());
        } catch (IOException e) {
            LOGGER.error("[Gamification] Failed to initialize", e);
        }
    }

    // ========== Badges ==========

    private void initializeBadges() {
        // First steps
        addBadge("first_blood", "First Blood", "Complete your first quest", 10, BadgeRarity.COMMON);
        addBadge("getting_started", "Getting Started", "Complete 5 quests", 25, BadgeRarity.COMMON);
        addBadge("veteran", "Veteran", "Complete 50 quests", 100, BadgeRarity.RARE);
        addBadge("master", "Master", "Complete 200 quests", 500, BadgeRarity.EPIC);
        addBadge("legend", "Legend", "Complete 1000 quests", 2000, BadgeRarity.LEGENDARY);

        // Difficulty badges
        addBadge("trivial_clear", "Pest Control", "Complete a Trivial difficulty quest", 5, BadgeRarity.COMMON);
        addBadge("easy_clear", "Monster Hunter", "Complete an Easy difficulty quest", 10, BadgeRarity.COMMON);
        addBadge("medium_clear", "Seasoned Fighter", "Complete a Medium difficulty quest", 25, BadgeRarity.UNCOMMON);
        addBadge("hard_clear", "Elite Slayer", "Complete a Hard difficulty quest", 50, BadgeRarity.RARE);
        addBadge("elite_clear", "Champion", "Complete an Elite difficulty quest", 100, BadgeRarity.EPIC);
        addBadge("boss_clear", "Boss Killer", "Complete a Boss difficulty quest", 250, BadgeRarity.LEGENDARY);

        // Wave achievements
        addBadge("wave_5", "Halfway There", "Reach wave 5", 15, BadgeRarity.COMMON);
        addBadge("wave_10", "Full Clear", "Complete all 10 waves", 50, BadgeRarity.UNCOMMON);
        addBadge("endless_10", "Endless Warrior", "Reach wave 10 in Endless mode", 75, BadgeRarity.RARE);
        addBadge("endless_20", "Unstoppable", "Reach wave 20 in Endless mode", 150, BadgeRarity.EPIC);
        addBadge("endless_50", "Immortal", "Reach wave 50 in Endless mode", 500, BadgeRarity.LEGENDARY);

        // Combat badges
        addBadge("critical_master", "Critical Master", "Land 100 critical hits in one session", 30, BadgeRarity.UNCOMMON);
        addBadge("damage_dealer", "Damage Dealer", "Deal 10,000 damage in one session", 40, BadgeRarity.UNCOMMON);
        addBadge("glass_cannon", "Glass Cannon", "Complete a quest without taking damage", 100, BadgeRarity.EPIC);
        addBadge("speed_demon", "Speed Demon", "Complete a quest in under 5 minutes", 75, BadgeRarity.RARE);
        addBadge("flawless", "Flawless Victory", "Complete 10 waves without dying", 200, BadgeRarity.LEGENDARY);

        // Variety badges
        addBadge("diverse_hunter", "Diverse Hunter", "Complete quests against 10 different mobs", 50, BadgeRarity.UNCOMMON);
        addBadge("completionist", "Completionist", "Complete quests against 50 different mobs", 200, BadgeRarity.EPIC);
        addBadge("master_of_all", "Master of All", "Complete quests against every mob type", 1000, BadgeRarity.LEGENDARY);

        // Point milestones
        addBadge("points_1k", "Point Collector", "Earn 1,000 total points", 20, BadgeRarity.COMMON);
        addBadge("points_10k", "Point Hoarder", "Earn 10,000 total points", 75, BadgeRarity.UNCOMMON);
        addBadge("points_100k", "Point Master", "Earn 100,000 total points", 300, BadgeRarity.EPIC);
        addBadge("points_1m", "Point Legend", "Earn 1,000,000 total points", 1500, BadgeRarity.LEGENDARY);

        // Special badges
        addBadge("daily_warrior", "Daily Warrior", "Complete a daily challenge", 25, BadgeRarity.UNCOMMON);
        addBadge("weekly_champion", "Weekly Champion", "Complete all weekly challenges", 100, BadgeRarity.RARE);
        addBadge("streak_7", "Week Streak", "Play 7 days in a row", 50, BadgeRarity.UNCOMMON);
        addBadge("streak_30", "Monthly Dedication", "Play 30 days in a row", 200, BadgeRarity.EPIC);

        // Template badges
        addBadge("template_explorer", "Template Explorer", "Complete quests in 2 different templates", 60, BadgeRarity.UNCOMMON);
        addBadge("smoke_ranger", "Smoke Ranger", "Complete a smoke template quest", 75, BadgeRarity.RARE);
        addBadge("arena_master", "Arena Master", "Complete every available arena template", 150, BadgeRarity.RARE);
        addBadge("speedrunner", "Speedrunner", "Complete a quest in under 3 minutes", 120, BadgeRarity.RARE);
    }

    private void addBadge(String id, String name, String description, int bonusPoints, BadgeRarity rarity) {
        availableBadges.put(id, new Badge(id, name, description, bonusPoints, rarity));
    }

    public enum BadgeRarity {
        COMMON(EnduranceColors.PerkRarity.COMMON, "Common"),
        UNCOMMON(EnduranceColors.PerkRarity.UNCOMMON, "Uncommon"),
        RARE(EnduranceColors.PerkRarity.RARE, "Rare"),
        EPIC(EnduranceColors.PerkRarity.EPIC, "Epic"),
        LEGENDARY(EnduranceColors.PerkRarity.LEGENDARY, "Legendary");

        private final int color;
        private final String displayName;

        BadgeRarity(int color, String displayName) {
            this.color = color;
            this.displayName = displayName;
        }

        public int getColor() { return color; }
        public String getDisplayName() { return displayName; }
    }

    public static class Badge {
        private final String id;
        private final String name;
        private final String description;
        private final int bonusPoints;
        private final BadgeRarity rarity;

        public Badge(String id, String name, String description, int bonusPoints, BadgeRarity rarity) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.bonusPoints = bonusPoints;
            this.rarity = rarity;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getBonusPoints() { return bonusPoints; }
        public BadgeRarity getRarity() { return rarity; }
    }

    // ========== Player Profiles ==========

    /**
     * Thread-safe player profile with volatile primitives and ConcurrentHashMap-backed sets.
     * Uses statsLock for atomic compound operations on counters.
     */
    public static class PlayerProfile {
        public volatile UUID playerId;
        public volatile String playerName;

        // Lock for atomic compound operations on stats counters
        public final Object statsLock = new Object();

        // Points - volatile for visibility across threads
        public volatile int totalPoints = 0;
        public volatile int weeklyPoints = 0;
        public volatile int dailyPoints = 0;

        /**
         * Best points earned in a single quest, used for the HIGH SCORE record.
         *
         * <p>Distinct from {@link #totalPoints}, which only ever grows, so no
         * comparison against it can express a personal best. Profiles saved
         * before this field existed deserialize it as 0.
         */
        public volatile int bestRunPoints = 0;

        // Stats - volatile for visibility across threads
        public volatile int totalQuestsCompleted = 0;
        public volatile int totalMobsKilled = 0;
        public volatile int highestWaveReached = 0;
        public volatile int currentStreak = 0;
        public volatile int longestStreak = 0;
        public volatile LocalDate lastPlayDate;

        // Badges earned - thread-safe set
        public Set<String> earnedBadges = java.util.concurrent.ConcurrentHashMap.newKeySet();

        // Completed challenges - thread-safe sets
        public Set<String> completedDailyChallenges = java.util.concurrent.ConcurrentHashMap.newKeySet();
        public Set<String> completedWeeklyChallenges = java.util.concurrent.ConcurrentHashMap.newKeySet();

        // Mobs defeated (for variety tracking) - thread-safe set
        public Set<String> defeatedMobTypes = java.util.concurrent.ConcurrentHashMap.newKeySet();

        // Templates completed (for template coverage) - thread-safe sets
        public Set<String> completedTemplates = java.util.concurrent.ConcurrentHashMap.newKeySet();
        public Set<String> completedTemplateVersions = java.util.concurrent.ConcurrentHashMap.newKeySet();

        public PlayerProfile() {}

        public PlayerProfile(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
        }

        public int getRank() {
            // Simple rank calculation based on total points
            if (totalPoints >= 100000) return 10; // Legend
            if (totalPoints >= 50000) return 9;
            if (totalPoints >= 25000) return 8;
            if (totalPoints >= 10000) return 7;
            if (totalPoints >= 5000) return 6;
            if (totalPoints >= 2500) return 5;
            if (totalPoints >= 1000) return 4;
            if (totalPoints >= 500) return 3;
            if (totalPoints >= 100) return 2;
            return 1;
        }

        public String getRankName() {
            return switch (getRank()) {
                case 10 -> "Legend";
                case 9 -> "Master";
                case 8 -> "Expert";
                case 7 -> "Veteran";
                case 6 -> "Elite";
                case 5 -> "Skilled";
                case 4 -> "Adept";
                case 3 -> "Apprentice";
                case 2 -> "Novice";
                default -> "Beginner";
            };
        }
    }

    // ========== Challenges ==========

    public static class Challenge {
        public String id;
        public String name;
        public String description;
        public ChallengeType type;
        public int targetValue;
        public int rewardPoints;
        public Map<String, String> parameters = new HashMap<>();

        public Challenge() {}

        public Challenge(String id, String name, String description, ChallengeType type,
                        int targetValue, int rewardPoints) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.type = type;
            this.targetValue = targetValue;
            this.rewardPoints = rewardPoints;
        }
    }

    public enum ChallengeType {
        COMPLETE_QUESTS,
        KILL_MOBS,
        REACH_WAVE,
        DEAL_DAMAGE,
        COMPLETE_SPECIFIC_MOB,
        COMPLETE_DIFFICULTY,
        COMPLETE_TEMPLATE,
        NO_DEATH_RUN,
        SPEED_RUN
    }

    // ========== Leaderboard ==========

    public static class Leaderboard {
        public String name;
        public List<LeaderboardEntry> entries = new ArrayList<>();

        public Leaderboard() {}

        public Leaderboard(String name) {
            this.name = name;
        }

        public void updateEntry(UUID playerId, String playerName, int points) {
            // Find existing entry or create new
            LeaderboardEntry entry = entries.stream()
                .filter(e -> e.playerId.equals(playerId))
                .findFirst()
                .orElse(null);

            int oldRank = getPosition(playerId);

            if (entry == null) {
                entry = new LeaderboardEntry(playerId, playerName, points);
                entries.add(entry);
            } else {
                entry.points = points;
                entry.playerName = playerName;
            }

            // Sort by points descending
            entries.sort((a, b) -> Integer.compare(b.points, a.points));

            // Keep top 100 (truncate in place - reassigning to a subList would leave the
            // tail alive in the backing list and grow it without bound)
            if (entries.size() > 100) {
                entries.subList(100, entries.size()).clear();
            }

            // Telemetry: record leaderboard change if rank improved
            int newRank = getPosition(playerId);
            if (newRank > 0 && (oldRank < 0 || newRank < oldRank)) {
                EnduranceTelemetryService.INSTANCE.recordLeaderboardChange(
                    playerId, name, oldRank, newRank, points
                );

                // Auto-publish news for top 3 leaderboard entries
                if (newRank <= 3 && (oldRank < 0 || oldRank > 3)) {
                    publishLeaderboardNews(entry.playerName, this.name, newRank, points);
                }
            }
        }

        public int getPosition(UUID playerId) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).playerId.equals(playerId)) {
                    return i + 1;
                }
            }
            return -1;
        }

        public void clear() {
            entries.clear();
        }
    }

    public static class LeaderboardEntry {
        public UUID playerId;
        public String playerName;
        public int points;

        public LeaderboardEntry() {}

        public LeaderboardEntry(UUID playerId, String playerName, int points) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.points = points;
        }
    }

    // ========== Core Methods ==========

    /**
     * Get or create player profile.
     */
    public PlayerProfile getProfile(UUID playerId, String playerName) {
        return playerProfiles.computeIfAbsent(playerId, id -> new PlayerProfile(id, playerName));
    }

    /**
     * Award points to a player.
     * Thread-safe: Uses synchronized block for atomic point updates.
     */
    public void awardPoints(UUID playerId, String playerName, int points, String reason) {
        PlayerProfile profile = getProfile(playerId, playerName);
        int newTotal, newWeekly, newDaily;
        synchronized (profile.statsLock) {
            profile.totalPoints += points;
            profile.weeklyPoints += points;
            profile.dailyPoints += points;
            newTotal = profile.totalPoints;
            newWeekly = profile.weeklyPoints;
            newDaily = profile.dailyPoints;
        }

        // Update leaderboards
        allTimeLeaderboard.updateEntry(playerId, playerName, newTotal);
        weeklyLeaderboard.updateEntry(playerId, playerName, newWeekly);
        dailyLeaderboard.updateEntry(playerId, playerName, newDaily);

        LOGGER.debug("[Gamification] Awarded {} points to {} for: {}", points, playerName, reason);

        // Check for point milestone badges
        checkPointBadges(profile);
        saveData();
    }

    /**
     * Result class for quest completion tracking.
     */
    public static class QuestCompletionResult {
        private final List<Badge> newBadges = new ArrayList<>();
        private final boolean isNewHighScore;
        private final boolean isNewWaveRecord;
        private final int previousHighScore;
        private final int previousHighWave;

        public QuestCompletionResult(boolean isNewHighScore, boolean isNewWaveRecord,
                                     int previousHighScore, int previousHighWave) {
            this.isNewHighScore = isNewHighScore;
            this.isNewWaveRecord = isNewWaveRecord;
            this.previousHighScore = previousHighScore;
            this.previousHighWave = previousHighWave;
        }

        public List<Badge> getNewBadges() { return newBadges; }
        public boolean isNewHighScore() { return isNewHighScore; }
        public boolean isNewWaveRecord() { return isNewWaveRecord; }
        public int getPreviousHighScore() { return previousHighScore; }
        public int getPreviousHighWave() { return previousHighWave; }
    }

    /**
     * Record quest completion and return tracking result for notifications.
     */
    public QuestCompletionResult recordQuestCompletion(UUID playerId, String playerName, EnduranceQuest quest,
                                      CombatTracker.QuestCombatSession combatSession) {
        return recordQuestCompletion(playerId, playerName, quest, combatSession, null);
    }

    /**
     * Thread-safe: Uses synchronized block for atomic stats updates.
     */
    public QuestCompletionResult recordQuestCompletion(UUID playerId, String playerName, EnduranceQuest quest,
                                      CombatTracker.QuestCombatSession combatSession,
                                      @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session) {
        PlayerProfile profile = getProfile(playerId, playerName);

        // Track previous records for notification
        int previousHighScore;
        int previousHighWave;
        boolean isNewWaveRecord;

        // Update stats atomically
        synchronized (profile.statsLock) {
            previousHighScore = profile.bestRunPoints;
            previousHighWave = profile.highestWaveReached;

            profile.totalQuestsCompleted++;
            profile.totalMobsKilled += combatSession.getKills();
            isNewWaveRecord = quest.getCurrentWave() > profile.highestWaveReached;
            if (isNewWaveRecord) {
                profile.highestWaveReached = quest.getCurrentWave();
            }
        }
        profile.defeatedMobTypes.add(quest.getMobId().toString());

        ArenaTemplate template = resolveTemplateForSession(session);
        if (quest.getState() == EnduranceQuestState.COMPLETED && session != null) {
            String templateId = session.getTemplateId();
            if (templateId != null) {
                profile.completedTemplates.add(templateId);
                Integer templateVersion = session.getTemplateVersion();
                if (templateVersion != null) {
                    profile.completedTemplateVersions.add(templateId + ":" + templateVersion);
                }
            }
        }

        // Update streak atomically
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        synchronized (profile.statsLock) {
            if (profile.lastPlayDate == null || !profile.lastPlayDate.equals(today.minusDays(1))) {
                if (profile.lastPlayDate == null || !profile.lastPlayDate.equals(today)) {
                    profile.currentStreak = 1;
                }
            } else {
                profile.currentStreak++;
            }
            if (profile.currentStreak > profile.longestStreak) {
                profile.longestStreak = profile.currentStreak;
            }
        }
        profile.lastPlayDate = today;

        // Capture badges before checking (for new badge detection)
        Set<String> badgesBefore = new HashSet<>(profile.earnedBadges);

        // Award base points
        int basePoints = quest.getPointsEarnedThisSession();
        awardPoints(playerId, playerName, basePoints, "Quest completion");

        boolean isNewHighScore;
        synchronized (profile.statsLock) {
            isNewHighScore = basePoints > profile.bestRunPoints;
            if (isNewHighScore) {
                profile.bestRunPoints = basePoints;
            }
        }

        // Check for badges
        checkQuestBadges(profile, quest, combatSession, template);

        // Check challenges
        checkChallenges(profile, quest, combatSession, session, template);

        saveData();

        // Build result with new badges
        QuestCompletionResult result = new QuestCompletionResult(
            isNewHighScore, isNewWaveRecord, previousHighScore, previousHighWave);

        // Find newly earned badges
        for (String badgeId : profile.earnedBadges) {
            if (!badgesBefore.contains(badgeId)) {
                Badge badge = availableBadges.get(badgeId);
                if (badge != null) {
                    result.newBadges.add(badge);
                }
            }
        }

        if (template != null) {
            templateCoverageLeaderboard.updateEntry(
                profile.playerId,
                profile.playerName,
                profile.completedTemplates.size()
            );
        }

        return result;
    }

    private @javax.annotation.Nullable ArenaTemplate resolveTemplateForSession(
        @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession session) {
        if (session == null || session.getTemplateId() == null) {
            return null;
        }
        ArenaTemplateRegistry registry = DevMod.getArenaTemplateRegistry();
        return registry.get(session.getTemplateId()).orElse(null);
    }

    /**
     * Award a badge to a player.
     * Thread-safe: Uses synchronized block for atomic point updates.
     */
    public boolean awardBadge(PlayerProfile profile, String badgeId) {
        if (profile.earnedBadges.contains(badgeId)) {
            return false; // Already has badge
        }

        Badge badge = availableBadges.get(badgeId);
        if (badge == null) {
            return false;
        }

        profile.earnedBadges.add(badgeId);
        synchronized (profile.statsLock) {
            profile.totalPoints += badge.getBonusPoints();
            profile.weeklyPoints += badge.getBonusPoints();
            profile.dailyPoints += badge.getBonusPoints();
        }

        // Telemetry: record badge unlocked
        EnduranceTelemetryService.INSTANCE.recordBadgeUnlocked(
            profile.playerId, badgeId, badge.getName(), badge.getBonusPoints()
        );

        LOGGER.info("[Gamification] {} earned badge: {} (+{} points)",
            profile.playerName, badge.getName(), badge.getBonusPoints());

        return true;
    }

    // ========== Badge Checks ==========

    private void checkPointBadges(PlayerProfile profile) {
        if (profile.totalPoints >= 1000) awardBadge(profile, "points_1k");
        if (profile.totalPoints >= 10000) awardBadge(profile, "points_10k");
        if (profile.totalPoints >= 100000) awardBadge(profile, "points_100k");
        if (profile.totalPoints >= 1000000) awardBadge(profile, "points_1m");
    }

    private void checkQuestBadges(PlayerProfile profile, EnduranceQuest quest,
                                  CombatTracker.QuestCombatSession session,
                                  @javax.annotation.Nullable ArenaTemplate template) {
        // Completion count badges
        if (profile.totalQuestsCompleted >= 1) awardBadge(profile, "first_blood");
        if (profile.totalQuestsCompleted >= 5) awardBadge(profile, "getting_started");
        if (profile.totalQuestsCompleted >= 50) awardBadge(profile, "veteran");
        if (profile.totalQuestsCompleted >= 200) awardBadge(profile, "master");
        if (profile.totalQuestsCompleted >= 1000) awardBadge(profile, "legend");

        // Difficulty badges
        if (quest.getState() == EnduranceQuestState.COMPLETED) {
            switch (quest.getTier()) {
                case TRIVIAL -> awardBadge(profile, "trivial_clear");
                case EASY -> awardBadge(profile, "easy_clear");
                case MEDIUM -> awardBadge(profile, "medium_clear");
                case HARD -> awardBadge(profile, "hard_clear");
                case ELITE -> awardBadge(profile, "elite_clear");
                case BOSS -> awardBadge(profile, "boss_clear");
            }
        }

        // Wave badges
        if (quest.getCurrentWave() >= 5) awardBadge(profile, "wave_5");
        if (quest.getCurrentWave() >= 10 && !quest.isEndlessMode()) awardBadge(profile, "wave_10");
        if (quest.isEndlessMode()) {
            if (quest.getCurrentWave() >= 10) awardBadge(profile, "endless_10");
            if (quest.getCurrentWave() >= 20) awardBadge(profile, "endless_20");
            if (quest.getCurrentWave() >= 50) awardBadge(profile, "endless_50");
        }

        // Combat badges
        if (session.getCriticalHits() >= 100) awardBadge(profile, "critical_master");
        if (session.getTotalDamageDealt() >= 10000) awardBadge(profile, "damage_dealer");
        if (session.getTotalDamageTaken() == 0 && quest.getState() == EnduranceQuestState.COMPLETED) {
            awardBadge(profile, "glass_cannon");
        }
        if (session.getSessionDuration() < 300000 && quest.getState() == EnduranceQuestState.COMPLETED) {
            awardBadge(profile, "speed_demon"); // Under 5 minutes
        }
        if (session.getSessionDuration() < 180000 && quest.getState() == EnduranceQuestState.COMPLETED) {
            awardBadge(profile, "speedrunner"); // Under 3 minutes
        }
        if (session.getDeaths() == 0 && quest.getCurrentWave() >= 10) {
            awardBadge(profile, "flawless");
        }

        // Variety badges
        if (profile.defeatedMobTypes.size() >= 10) awardBadge(profile, "diverse_hunter");
        if (profile.defeatedMobTypes.size() >= 50) awardBadge(profile, "completionist");
        if (profile.defeatedMobTypes.size() >= EnduranceQuestRegistry.INSTANCE.getTotalMobCount()) {
            awardBadge(profile, "master_of_all");
        }

        // Streak badges
        if (profile.currentStreak >= 7) awardBadge(profile, "streak_7");
        if (profile.currentStreak >= 30) awardBadge(profile, "streak_30");

        if (profile.completedTemplates.size() >= 2) {
            awardBadge(profile, "template_explorer");
        }
        if (template != null && template.tags() != null
            && template.tags().contains("smoke")
            && quest.getState() == EnduranceQuestState.COMPLETED) {
            awardBadge(profile, "smoke_ranger");
        }
        ArenaTemplateRegistry registry = DevMod.getArenaTemplateRegistry();
        if (registry != null && !registry.all().isEmpty()) {
            Set<String> allTemplates = registry.all().stream()
                .map(ArenaTemplate::id)
                .collect(Collectors.toSet());
            if (!allTemplates.isEmpty() && profile.completedTemplates.containsAll(allTemplates)) {
                awardBadge(profile, "arena_master");
            }
        }
    }

    private void checkChallenges(PlayerProfile profile, EnduranceQuest quest,
                                 CombatTracker.QuestCombatSession session,
                                 @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession questSession,
                                 @javax.annotation.Nullable ArenaTemplate template) {
        // Check daily challenges
        for (Challenge challenge : dailyChallenges) {
            if (!profile.completedDailyChallenges.contains(challenge.id)) {
                if (isChallengeCompleted(profile, challenge, quest, session, questSession, template)) {
                    profile.completedDailyChallenges.add(challenge.id);
                    awardPoints(profile.playerId, profile.playerName, challenge.rewardPoints,
                        "Daily challenge: " + challenge.name);
                    awardBadge(profile, "daily_warrior");
                }
            }
        }

        // Check weekly challenges
        for (Challenge challenge : weeklyChallenges) {
            if (!profile.completedWeeklyChallenges.contains(challenge.id)) {
                if (isChallengeCompleted(profile, challenge, quest, session, questSession, template)) {
                    profile.completedWeeklyChallenges.add(challenge.id);
                    awardPoints(profile.playerId, profile.playerName, challenge.rewardPoints,
                        "Weekly challenge: " + challenge.name);
                }
            }
        }

        // Check if all weekly challenges completed
        if (profile.completedWeeklyChallenges.containsAll(
            weeklyChallenges.stream().map(c -> c.id).collect(Collectors.toSet()))) {
            awardBadge(profile, "weekly_champion");
        }
    }

    private boolean isChallengeCompleted(PlayerProfile profile, Challenge challenge,
                                         EnduranceQuest quest, CombatTracker.QuestCombatSession session,
                                         @javax.annotation.Nullable EnduranceQuestManager.ActiveQuestSession questSession,
                                         @javax.annotation.Nullable ArenaTemplate template) {
        return switch (challenge.type) {
            case COMPLETE_QUESTS -> profile.totalQuestsCompleted >= challenge.targetValue;
            case KILL_MOBS -> session.getKills() >= challenge.targetValue;
            case REACH_WAVE -> quest.getCurrentWave() >= challenge.targetValue;
            case DEAL_DAMAGE -> session.getTotalDamageDealt() >= challenge.targetValue;
            case COMPLETE_SPECIFIC_MOB -> quest.getMobId().toString().equals(challenge.parameters.get("mobId")) &&
                quest.getState() == EnduranceQuestState.COMPLETED;
            case COMPLETE_DIFFICULTY -> quest.getTier().name().equals(challenge.parameters.get("difficulty")) &&
                quest.getState() == EnduranceQuestState.COMPLETED;
            case COMPLETE_TEMPLATE -> {
                if (quest.getState() != EnduranceQuestState.COMPLETED || template == null) {
                    yield false;
                }
                String requiredId = challenge.parameters.get("templateId");
                if (requiredId == null || !requiredId.equals(template.id())) {
                    yield false;
                }
                String requiredVersion = challenge.parameters.get("templateVersion");
                if (requiredVersion != null && questSession != null && questSession.getTemplateVersion() != null) {
                    yield requiredVersion.equals(String.valueOf(questSession.getTemplateVersion()));
                }
                yield true;
            }
            case NO_DEATH_RUN -> session.getDeaths() == 0 && quest.getState() == EnduranceQuestState.COMPLETED;
            case SPEED_RUN -> session.getSessionDuration() < challenge.targetValue &&
                quest.getState() == EnduranceQuestState.COMPLETED;
        };
    }

    // ========== Resets ==========

    /**
     * Called periodically to check if daily/weekly reset is needed.
     * Should be called from server tick (e.g., every minute).
     */
    public void tickResets() {
        checkForResets();
    }

    private void checkForResets() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        // Daily reset
        if (lastDailyReset == null || !lastDailyReset.equals(today)) {
            resetDaily();
            lastDailyReset = today;
        }

        // Weekly reset (Monday)
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (lastWeeklyReset == null || lastWeeklyReset.isBefore(thisMonday)) {
            resetWeekly();
            lastWeeklyReset = thisMonday;
        }
    }

    private void resetDaily() {
        LOGGER.info("[Gamification] Performing daily reset");
        dailyLeaderboard.clear();
        for (PlayerProfile profile : playerProfiles.values()) {
            profile.dailyPoints = 0;
            profile.completedDailyChallenges.clear();
        }
        generateDailyChallenges();
    }

    private void resetWeekly() {
        LOGGER.info("[Gamification] Performing weekly reset");
        weeklyLeaderboard.clear();
        for (PlayerProfile profile : playerProfiles.values()) {
            profile.weeklyPoints = 0;
            profile.completedWeeklyChallenges.clear();
        }
        generateWeeklyChallenges();
    }

    private void generateDailyChallenges() {
        dailyChallenges.clear();

        // Generate 3 daily challenges
        dailyChallenges.add(new Challenge(
            "daily_quests_" + LocalDate.now(ZoneId.systemDefault()),
            "Quest Master",
            "Complete 3 quests today",
            ChallengeType.COMPLETE_QUESTS,
            3, 50
        ));

        dailyChallenges.add(new Challenge(
            "daily_kills_" + LocalDate.now(ZoneId.systemDefault()),
            "Mob Slayer",
            "Kill 50 mobs in one session",
            ChallengeType.KILL_MOBS,
            50, 75
        ));

        ArenaTemplate template = pickTemplateForChallenge(t ->
            t.tags() != null && t.tags().contains("smoke"));
        if (template != null) {
            Challenge challenge = new Challenge(
                "daily_template_" + LocalDate.now(ZoneId.systemDefault()),
                "Smoke Trial",
                "Complete template " + template.id(),
                ChallengeType.COMPLETE_TEMPLATE,
                1, 80
            );
            challenge.parameters.put("templateId", template.id());
            challenge.parameters.put("templateVersion", String.valueOf(template.version()));
            dailyChallenges.add(challenge);
        } else {
            dailyChallenges.add(new Challenge(
                "daily_wave_" + LocalDate.now(ZoneId.systemDefault()),
                "Wave Crusher",
                "Reach wave 7 in any quest",
                ChallengeType.REACH_WAVE,
                7, 60
            ));
        }

        normalizeChallengeDescriptions(dailyChallenges);
    }

    private void generateWeeklyChallenges() {
        weeklyChallenges.clear();

        weeklyChallenges.add(new Challenge(
            "weekly_quests_" + LocalDate.now(ZoneId.systemDefault()),
            "Weekly Warrior",
            "Complete 20 quests this week",
            ChallengeType.COMPLETE_QUESTS,
            20, 200
        ));

        weeklyChallenges.add(new Challenge(
            "weekly_damage_" + LocalDate.now(ZoneId.systemDefault()),
            "Damage King",
            "Deal 50,000 damage in one session",
            ChallengeType.DEAL_DAMAGE,
            50000, 150
        ));

        weeklyChallenges.add(new Challenge(
            "weekly_nodeath_" + LocalDate.now(ZoneId.systemDefault()),
            "Survivor",
            "Complete a quest without dying",
            ChallengeType.NO_DEATH_RUN,
            1, 175
        ));

        weeklyChallenges.add(new Challenge(
            "weekly_speed_" + LocalDate.now(ZoneId.systemDefault()),
            "Speed Runner",
            "Complete a quest in under 10 minutes",
            ChallengeType.SPEED_RUN,
            600000, 125
        ));

        ArenaTemplate bossTemplate = pickTemplateForChallenge(t ->
            t.tags() != null && t.tags().contains("boss"));
        if (bossTemplate != null) {
            Challenge challenge = new Challenge(
                "weekly_template_" + LocalDate.now(ZoneId.systemDefault()),
                "Boss Arena",
                "Complete template " + bossTemplate.id(),
                ChallengeType.COMPLETE_TEMPLATE,
                1, 160
            );
            challenge.parameters.put("templateId", bossTemplate.id());
            challenge.parameters.put("templateVersion", String.valueOf(bossTemplate.version()));
            weeklyChallenges.add(challenge);
        }

        normalizeChallengeDescriptions(weeklyChallenges);
    }

    private void normalizeChallengeDescriptions(List<Challenge> challenges) {
        for (Challenge challenge : challenges) {
            String description = challenge.description;
            if (description == null || description.isBlank()) {
                challenge.description = challenge.name;
            }
        }
    }

    private @javax.annotation.Nullable ArenaTemplate pickTemplateForChallenge(
        java.util.function.Predicate<ArenaTemplate> predicate) {
        ArenaTemplateRegistry registry = DevMod.getArenaTemplateRegistry();
        List<ArenaTemplate> templates = registry.all().stream()
            .filter(Objects::nonNull)
            .filter(predicate)
            .toList();
        if (templates.isEmpty()) {
            return null;
        }
        return templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
    }

    // ========== Persistence ==========

    private void loadData() {
        if (dataDirectory == null) {
            LOGGER.warn("[Gamification] Data directory not set; skipping load");
            return;
        }
        // Load player profiles
        Path profilesFile = dataDirectory.resolve("profiles.json");
        if (Files.exists(profilesFile)) {
            try (Reader reader = Files.newBufferedReader(profilesFile)) {
                Type type = new TypeToken<Map<String, PlayerProfile>>(){}.getType();
                Map<String, PlayerProfile> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    loaded.forEach((key, value) -> playerProfiles.put(UUID.fromString(key), value));
                }
            } catch (Exception e) {
                LOGGER.error("[Gamification] Failed to load profiles", e);
            }
        }

        // Load leaderboards
        Path leaderboardsFile = dataDirectory.resolve("leaderboards.json");
        if (Files.exists(leaderboardsFile)) {
            try (Reader reader = Files.newBufferedReader(leaderboardsFile)) {
                LeaderboardData data = GSON.fromJson(reader, LeaderboardData.class);
                if (data != null) {
                    if (data.allTime != null) allTimeLeaderboard.entries = data.allTime.entries;
                    if (data.weekly != null) weeklyLeaderboard.entries = data.weekly.entries;
                    if (data.daily != null) dailyLeaderboard.entries = data.daily.entries;
                    if (data.templateCoverage != null) templateCoverageLeaderboard.entries = data.templateCoverage.entries;
                    lastDailyReset = data.lastDailyReset;
                    lastWeeklyReset = data.lastWeeklyReset;
                }
            } catch (Exception e) {
                LOGGER.error("[Gamification] Failed to load leaderboards", e);
            }
        }

        // Generate challenges if needed
        if (dailyChallenges.isEmpty()) generateDailyChallenges();
        if (weeklyChallenges.isEmpty()) generateWeeklyChallenges();
    }

    /**
     * Save all gamification data. Called on server shutdown.
     */
    public void saveAll() {
        saveData();
        LOGGER.info("[Gamification] Saved all data ({} profiles, {} leaderboard entries)",
            playerProfiles.size(), allTimeLeaderboard.entries.size());
    }

    /**
     * Reset all gamification data. Called during full player reset.
     */
    public void resetAll() {
        LOGGER.info("[Gamification] Resetting all gamification data...");

        // Clear in-memory data
        playerProfiles.clear();
        allTimeLeaderboard.entries.clear();
        weeklyLeaderboard.entries.clear();
        dailyLeaderboard.entries.clear();
        templateCoverageLeaderboard.entries.clear();
        dailyChallenges.clear();
        weeklyChallenges.clear();
        lastDailyReset = null;
        lastWeeklyReset = null;

        // Delete data files
        if (dataDirectory != null) {
            try {
                Files.deleteIfExists(dataDirectory.resolve("profiles.json"));
                Files.deleteIfExists(dataDirectory.resolve("leaderboards.json"));
                LOGGER.info("[Gamification] All gamification data reset successfully");
            } catch (IOException e) {
                LOGGER.error("[Gamification] Failed to delete data files", e);
            }
        }

        // Regenerate challenges
        generateDailyChallenges();
        generateWeeklyChallenges();
    }

    private void saveData() {
        if (dataDirectory == null) {
            LOGGER.warn("[Gamification] Data directory not set; skipping save");
            return;
        }
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            LOGGER.error("[Gamification] Failed to create data directory", e);
            return;
        }
        // Save player profiles
        Path profilesFile = dataDirectory.resolve("profiles.json");
        try (Writer writer = Files.newBufferedWriter(profilesFile)) {
            Map<String, PlayerProfile> toSave = new HashMap<>();
            playerProfiles.forEach((uuid, profile) -> toSave.put(uuid.toString(), profile));
            GSON.toJson(toSave, writer);
        } catch (IOException e) {
            LOGGER.error("[Gamification] Failed to save profiles", e);
        }

        // Save leaderboards
        Path leaderboardsFile = dataDirectory.resolve("leaderboards.json");
        try (Writer writer = Files.newBufferedWriter(leaderboardsFile)) {
            LeaderboardData data = new LeaderboardData();
            data.allTime = allTimeLeaderboard;
            data.weekly = weeklyLeaderboard;
            data.daily = dailyLeaderboard;
            data.templateCoverage = templateCoverageLeaderboard;
            data.lastDailyReset = lastDailyReset;
            data.lastWeeklyReset = lastWeeklyReset;
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("[Gamification] Failed to save leaderboards", e);
        }
    }

    private static class LeaderboardData {
        Leaderboard allTime;
        Leaderboard weekly;
        Leaderboard daily;
        Leaderboard templateCoverage;
        LocalDate lastDailyReset;
        LocalDate lastWeeklyReset;
    }

    // ========== News Auto-Publish ==========

    /**
     * Publish news when a player enters the top 3 of a leaderboard.
     */
    private static void publishLeaderboardNews(String playerName, String leaderboardName, int rank, int points) {
        try {
            String ordinal = switch (rank) {
                case 1 -> "1st";
                case 2 -> "2nd";
                case 3 -> "3rd";
                default -> rank + "th";
            };

            String title = playerName + " reaches " + ordinal + " place!";
            String content = String.format(
                "%s has climbed to %s place on the %s leaderboard with %,d points!%n%n" +
                "Congratulations on this impressive achievement!",
                playerName, ordinal, leaderboardName, points
            );

            NewsManager.getInstance().publishNews(
                    title,
                    content,
                    NewsCategory.EVENT,
                    "Leaderboard System"
            ).exceptionally(ex -> {
                LOGGER.warn("[Gamification] Failed to publish leaderboard news", ex);
                return null;
            });

            LOGGER.info("[Gamification] Published news for {} reaching {} on {}", playerName, ordinal, leaderboardName);
        } catch (Exception e) {
            LOGGER.error("[Gamification] Failed to publish leaderboard news", e);
        }
    }

    // ========== Getters ==========

    public Collection<Badge> getAllBadges() {
        return Collections.unmodifiableCollection(availableBadges.values());
    }

    public Leaderboard getAllTimeLeaderboard() { return allTimeLeaderboard; }
    public Leaderboard getWeeklyLeaderboard() { return weeklyLeaderboard; }
    public Leaderboard getDailyLeaderboard() { return dailyLeaderboard; }
    public Leaderboard getTemplateCoverageLeaderboard() { return templateCoverageLeaderboard; }
    public List<Challenge> getDailyChallenges() { return Collections.unmodifiableList(dailyChallenges); }
    public List<Challenge> getWeeklyChallenges() { return Collections.unmodifiableList(weeklyChallenges); }
}
