package com.devmod.endurance.challenges;

import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceQuest;
import com.devmod.endurance.RewardSystem;
import com.devmod.endurance.config.EnduranceConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages weekly challenges for the Endurance Quest system.
 *
 * Features:
 * - Rotates 2 challenges weekly (Standard/Epic + Epic/Legendary)
 * - Tracks cumulative progress across sessions
 * - Awards significant rewards on completion
 * - Persists progress across sessions
 * - Resets every Monday at midnight UTC
 */
public class WeeklyChallengeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WeeklyChallengeManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final WeeklyChallengeManager INSTANCE = new WeeklyChallengeManager();

    // Config manager reference
    private final EnduranceConfigManager config = EnduranceConfigManager.INSTANCE;

    // Config-aware getters for rewards
    public int getWeeklyTokenReward(UUID questId) {
        return config.getChallengeWeeklyTokenReward(questId);
    }

    public int getWeeklyXpReward(UUID questId) {
        return config.getChallengeWeeklyXpReward(questId);
    }

    public int getWeeklyChallengeCount(UUID questId) {
        return config.getChallengeWeeklyCount(questId);
    }

    // All available weekly challenges (templates)
    private final List<WeeklyChallenge> challengePool = new ArrayList<>();

    // This week's active challenges (2 per week)
    private final List<WeeklyChallenge> activeChallenges = new ArrayList<>();

    // Player progress: playerId -> challengeId -> progress
    private final Map<UUID, Map<String, WeeklyChallenge.WeeklyProgress>> playerProgress = new ConcurrentHashMap<>();

    // Current week tracker for rotation (week-of-year)
    private int currentWeek;
    private int currentYear;

    // Persistence
    private Path dataDirectory;

    private WeeklyChallengeManager() {
        initializeChallengePool();
    }

    // ========== Initialization ==========

    /**
     * Initialize the pool of all possible weekly challenges.
     */
    private void initializeChallengePool() {
        // ========== STANDARD TIER (Base: 2500 tokens, 15 prestige) ==========

        // Total Kills - Cumulative
        challengePool.add(new WeeklyChallenge(
                "weekly_kills_500",
                "devmod.weekly.total_kills",
                "devmod.weekly.total_kills.desc",
                WeeklyChallenge.WeeklyChallengeType.TOTAL_KILLS,
                WeeklyChallenge.WeeklyChallengeTier.STANDARD,
                500, 2500, 15,
                (progress, target) -> progress.getCurrentValue() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_waves_50",
                "devmod.weekly.total_waves",
                "devmod.weekly.total_waves.desc",
                WeeklyChallenge.WeeklyChallengeType.TOTAL_WAVES,
                WeeklyChallenge.WeeklyChallengeTier.STANDARD,
                50, 2500, 15,
                (progress, target) -> progress.getCurrentValue() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_runs_3",
                "devmod.weekly.total_runs",
                "devmod.weekly.total_runs.desc",
                WeeklyChallenge.WeeklyChallengeType.TOTAL_RUNS,
                WeeklyChallenge.WeeklyChallengeTier.STANDARD,
                3, 2500, 15,
                (progress, target) -> progress.getCurrentValue() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_bosses_5",
                "devmod.weekly.boss_slayer",
                "devmod.weekly.boss_slayer.desc",
                WeeklyChallenge.WeeklyChallengeType.BOSS_SLAYER,
                WeeklyChallenge.WeeklyChallengeTier.STANDARD,
                5, 2500, 15,
                (progress, target) -> progress.getCurrentValue() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_combo_50_x3",
                "devmod.weekly.combo_master",
                "devmod.weekly.combo_master.desc",
                WeeklyChallenge.WeeklyChallengeType.COMBO_MASTER,
                WeeklyChallenge.WeeklyChallengeTier.STANDARD,
                3, 2500, 15, // Reach 50+ combo 3 times
                (progress, target) -> progress.getCurrentValue() >= target
        ));

        // ========== EPIC TIER (Base: 2500 tokens, 15 prestige -> 2x = 5000, 30) ==========

        challengePool.add(new WeeklyChallenge(
                "weekly_kills_1000",
                "devmod.weekly.total_kills",
                "devmod.weekly.total_kills.desc",
                WeeklyChallenge.WeeklyChallengeType.TOTAL_KILLS,
                WeeklyChallenge.WeeklyChallengeTier.EPIC,
                1000, 2500, 15,
                (progress, target) -> progress.getCurrentValue() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_waves_100",
                "devmod.weekly.total_waves",
                "devmod.weekly.total_waves.desc",
                WeeklyChallenge.WeeklyChallengeType.TOTAL_WAVES,
                WeeklyChallenge.WeeklyChallengeTier.EPIC,
                100, 2500, 15,
                (progress, target) -> progress.getCurrentValue() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_perfect_3",
                "devmod.weekly.perfect_runs",
                "devmod.weekly.perfect_runs.desc",
                WeeklyChallenge.WeeklyChallengeType.PERFECT_RUNS,
                WeeklyChallenge.WeeklyChallengeTier.EPIC,
                3, 2500, 15, // Complete 3 runs without dying
                (progress, target) -> progress.getPerfectRunsThisWeek() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_bosses_10",
                "devmod.weekly.boss_slayer",
                "devmod.weekly.boss_slayer.desc",
                WeeklyChallenge.WeeklyChallengeType.BOSS_SLAYER,
                WeeklyChallenge.WeeklyChallengeTier.EPIC,
                10, 2500, 15,
                (progress, target) -> progress.getCurrentValue() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_s_rank_x5",
                "devmod.weekly.style_master",
                "devmod.weekly.style_master.desc",
                WeeklyChallenge.WeeklyChallengeType.STYLE_MASTER,
                WeeklyChallenge.WeeklyChallengeTier.EPIC,
                5, 2500, 15, // Reach S rank 5 times
                (progress, target) -> progress.getTimesReachedTargetRank() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_runs_7",
                "devmod.weekly.total_runs",
                "devmod.weekly.total_runs.desc",
                WeeklyChallenge.WeeklyChallengeType.TOTAL_RUNS,
                WeeklyChallenge.WeeklyChallengeTier.EPIC,
                7, 2500, 15, // Complete a run every day
                (progress, target) -> progress.getCurrentValue() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_combo_75_x5",
                "devmod.weekly.combo_master",
                "devmod.weekly.combo_master.desc",
                WeeklyChallenge.WeeklyChallengeType.COMBO_MASTER,
                WeeklyChallenge.WeeklyChallengeTier.EPIC,
                5, 2500, 15, // Reach 75+ combo 5 times
                (progress, target) -> progress.getCurrentValue() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_endless_25",
                "devmod.weekly.endless_warrior",
                "devmod.weekly.endless_warrior.desc",
                WeeklyChallenge.WeeklyChallengeType.ENDLESS_WARRIOR,
                WeeklyChallenge.WeeklyChallengeTier.EPIC,
                25, 2500, 15, // Reach wave 25 in endless mode
                (progress, target) -> progress.getHighestWaveThisWeek() >= target
        ));

        // ========== LEGENDARY TIER (Base: 2500 tokens, 15 prestige -> 4x = 10000, 60) ==========

        challengePool.add(new WeeklyChallenge(
                "weekly_kills_2500",
                "devmod.weekly.total_kills",
                "devmod.weekly.total_kills.desc",
                WeeklyChallenge.WeeklyChallengeType.TOTAL_KILLS,
                WeeklyChallenge.WeeklyChallengeTier.LEGENDARY,
                2500, 2500, 15,
                (progress, target) -> progress.getCurrentValue() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_perfect_5",
                "devmod.weekly.perfect_runs",
                "devmod.weekly.perfect_runs.desc",
                WeeklyChallenge.WeeklyChallengeType.PERFECT_RUNS,
                WeeklyChallenge.WeeklyChallengeTier.LEGENDARY,
                5, 2500, 15,
                (progress, target) -> progress.getPerfectRunsThisWeek() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_bosses_20",
                "devmod.weekly.boss_slayer",
                "devmod.weekly.boss_slayer.desc",
                WeeklyChallenge.WeeklyChallengeType.BOSS_SLAYER,
                WeeklyChallenge.WeeklyChallengeTier.LEGENDARY,
                20, 2500, 15,
                (progress, target) -> progress.getCurrentValue() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_ss_rank_x3",
                "devmod.weekly.style_master",
                "devmod.weekly.style_master.desc",
                WeeklyChallenge.WeeklyChallengeType.STYLE_MASTER,
                WeeklyChallenge.WeeklyChallengeTier.LEGENDARY,
                3, 2500, 15, // Reach SS rank 3 times
                (progress, target) -> progress.getTimesReachedTargetRank() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_multi_boss_5",
                "devmod.weekly.multi_boss",
                "devmod.weekly.multi_boss.desc",
                WeeklyChallenge.WeeklyChallengeType.MULTI_BOSS_RUN,
                WeeklyChallenge.WeeklyChallengeTier.LEGENDARY,
                5, 2500, 15, // Kill 5 bosses in a single run
                (progress, target) -> progress.getCurrentValue() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_endless_50",
                "devmod.weekly.endless_warrior",
                "devmod.weekly.endless_warrior.desc",
                WeeklyChallenge.WeeklyChallengeType.ENDLESS_WARRIOR,
                WeeklyChallenge.WeeklyChallengeTier.LEGENDARY,
                50, 2500, 15, // Reach wave 50 in endless mode
                (progress, target) -> progress.getHighestWaveThisWeek() >= target
        ));

        challengePool.add(new WeeklyChallenge(
                "weekly_sss_run",
                "devmod.weekly.sss_master",
                "devmod.weekly.sss_master.desc",
                WeeklyChallenge.WeeklyChallengeType.STYLE_MASTER,
                WeeklyChallenge.WeeklyChallengeTier.LEGENDARY,
                1, 3000, 20, // Reach SSS rank once
                (progress, target) -> progress.getTimesReachedTargetRank() >= target
        ));

        LOGGER.info("[WeeklyChallengeManager] Initialized {} weekly challenges in pool", challengePool.size());
    }

    // ========== Weekly Rotation ==========

    /**
     * Initialize the manager with server data directory.
     */
    public void initialize(Path serverDataDir) {
        this.dataDirectory = serverDataDir.resolve("devmod").resolve("challenges");
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            LOGGER.error("[WeeklyChallengeManager] Failed to create data directory", e);
        }

        loadProgress();
        checkAndRotateChallenges();
    }

    /**
     * Check if we need to rotate challenges (new week) and do so if needed.
     */
    public void checkAndRotateChallenges() {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        WeekFields weekFields = WeekFields.of(DayOfWeek.MONDAY, 1);
        int weekOfYear = today.get(weekFields.weekOfWeekBasedYear());
        int year = today.getYear();

        if (currentYear != year || currentWeek != weekOfYear) {
            rotateChallenges(weekOfYear, year);
        }
    }

    /**
     * Rotate to new weekly challenges.
     */
    private void rotateChallenges(int week, int year) {
        currentWeek = week;
        currentYear = year;
        activeChallenges.clear();

        // Use week+year as seed for deterministic rotation
        long seed = (long) year * 100 + week;
        Random weekRandom = new Random(seed);

        // Selection: 2 challenges per week
        // Slot 1: Standard (50%) or Epic (50%)
        // Slot 2: Epic (70%) or Legendary (30%)

        List<WeeklyChallenge> standardChallenges = challengePool.stream()
                .filter(c -> c.getTier() == WeeklyChallenge.WeeklyChallengeTier.STANDARD)
                .toList();

        List<WeeklyChallenge> epicChallenges = challengePool.stream()
                .filter(c -> c.getTier() == WeeklyChallenge.WeeklyChallengeTier.EPIC)
                .toList();

        List<WeeklyChallenge> legendaryChallenges = challengePool.stream()
                .filter(c -> c.getTier() == WeeklyChallenge.WeeklyChallengeTier.LEGENDARY)
                .toList();

        // Slot 1: Standard or Epic
        if (weekRandom.nextFloat() < 0.5f && !standardChallenges.isEmpty()) {
            activeChallenges.add(standardChallenges.get(weekRandom.nextInt(standardChallenges.size())));
        } else if (!epicChallenges.isEmpty()) {
            activeChallenges.add(epicChallenges.get(weekRandom.nextInt(epicChallenges.size())));
        }

        // Slot 2: Epic or Legendary (different from slot 1)
        List<WeeklyChallenge> slot2Pool = new ArrayList<>();
        if (weekRandom.nextFloat() < 0.3f) {
            slot2Pool.addAll(legendaryChallenges);
        } else {
            slot2Pool.addAll(epicChallenges);
        }

        // Remove any challenge already selected
        slot2Pool.removeAll(activeChallenges);

        if (!slot2Pool.isEmpty()) {
            activeChallenges.add(slot2Pool.get(weekRandom.nextInt(slot2Pool.size())));
        }

        // Reset player progress for new week
        playerProgress.clear();

        LOGGER.info("[WeeklyChallengeManager] Rotated to week {}/{} with {} challenges: {}",
                week, year, activeChallenges.size(),
                activeChallenges.stream().map(WeeklyChallenge::getId).toList());

        saveProgress();
    }

    /**
     * Get the next Monday (reset time) for UI display.
     */
    public LocalDate getNextResetDate() {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    // ========== Progress Tracking ==========

    /**
     * Get or create player progress for a weekly challenge.
     */
    public WeeklyChallenge.WeeklyProgress getProgress(UUID playerId, String challengeId) {
        return playerProgress
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(challengeId, k -> new WeeklyChallenge.WeeklyProgress(playerId, challengeId));
    }

    /**
     * Update progress for kill-based challenges.
     */
    public void onMobKill(UUID playerId, boolean isBoss) {
        for (WeeklyChallenge challenge : activeChallenges) {
            WeeklyChallenge.WeeklyProgress progress = getProgress(playerId, challenge.getId());
            if (progress.isCompleted()) continue;

            switch (challenge.getType()) {
                case TOTAL_KILLS:
                    progress.increment();
                    break;
                case BOSS_SLAYER:
                    if (isBoss) progress.increment();
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Update progress for wave completion.
     */
    public void onWaveComplete(UUID playerId, int waveNumber, boolean isEndless) {
        for (WeeklyChallenge challenge : activeChallenges) {
            WeeklyChallenge.WeeklyProgress progress = getProgress(playerId, challenge.getId());
            if (progress.isCompleted()) continue;

            switch (challenge.getType()) {
                case TOTAL_WAVES:
                    progress.increment();
                    break;
                case ENDLESS_WARRIOR:
                    if (isEndless) {
                        progress.updateHighestWave(waveNumber);
                        progress.setCurrentValue(Math.max(progress.getCurrentValue(), waveNumber));
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Update progress for combo achievements.
     */
    public void onComboUpdate(UUID playerId, int currentCombo) {
        for (WeeklyChallenge challenge : activeChallenges) {
            if (challenge.getType() != WeeklyChallenge.WeeklyChallengeType.COMBO_MASTER) continue;

            WeeklyChallenge.WeeklyProgress progress = getProgress(playerId, challenge.getId());
            if (progress.isCompleted()) continue;

            progress.updateHighestCombo(currentCombo);

            // Count times reaching threshold based on challenge
            int comboThreshold = challenge.getId().contains("75") ? 75 : 50;
            if (currentCombo >= comboThreshold && progress.getHighestComboThisWeek() < currentCombo) {
                progress.increment(); // Count as reaching threshold
            }
        }
    }

    /**
     * Update progress for style rank achievements.
     */
    public void onStyleRankAchieved(UUID playerId, ComboSystem.StyleRank rank) {
        for (WeeklyChallenge challenge : activeChallenges) {
            if (challenge.getType() != WeeklyChallenge.WeeklyChallengeType.STYLE_MASTER) continue;

            WeeklyChallenge.WeeklyProgress progress = getProgress(playerId, challenge.getId());
            if (progress.isCompleted()) continue;

            // Determine target rank from challenge ID
            int targetRank;
            if (challenge.getId().contains("sss")) {
                targetRank = ComboSystem.StyleRank.SSS.ordinal();
            } else if (challenge.getId().contains("ss")) {
                targetRank = ComboSystem.StyleRank.SS.ordinal();
            } else {
                targetRank = ComboSystem.StyleRank.S.ordinal();
            }

            if (rank.ordinal() >= targetRank) {
                progress.incrementRankAchievement();
                progress.setCurrentValue(progress.getTimesReachedTargetRank());
            }
        }
    }

    /**
     * Update progress for quest completion.
     */
    public void onQuestComplete(UUID playerId, EnduranceQuest quest, int bossesKilledThisRun) {
        for (WeeklyChallenge challenge : activeChallenges) {
            WeeklyChallenge.WeeklyProgress progress = getProgress(playerId, challenge.getId());
            if (progress.isCompleted()) continue;

            switch (challenge.getType()) {
                case TOTAL_RUNS:
                    progress.increment();
                    break;
                case PERFECT_RUNS:
                    if (quest.getDeathsThisSession() == 0) {
                        progress.incrementPerfectRuns();
                        progress.setCurrentValue(progress.getPerfectRunsThisWeek());
                    }
                    break;
                case MULTI_BOSS_RUN:
                    if (bossesKilledThisRun >= challenge.getTargetValue()) {
                        progress.setCurrentValue(bossesKilledThisRun);
                    }
                    break;
                default:
                    break;
            }
        }

        checkAndAwardCompletions(playerId);
        saveProgress();
    }

    /**
     * Check all weekly challenges for completion.
     */
    private void checkAndAwardCompletions(UUID playerId) {
        for (WeeklyChallenge challenge : activeChallenges) {
            WeeklyChallenge.WeeklyProgress progress = getProgress(playerId, challenge.getId());
            if (progress.isCompleted()) continue;

            if (challenge.isComplete(progress)) {
                progress.markCompleted();
                LOGGER.info("[WeeklyChallengeManager] Player {} completed weekly challenge {}",
                        playerId, challenge.getId());
            }
        }
    }

    /**
     * Award rewards for a completed weekly challenge.
     */
    public void awardRewards(ServerPlayer player, WeeklyChallenge challenge) {
        WeeklyChallenge.WeeklyProgress progress = getProgress(player.getUUID(), challenge.getId());

        if (!progress.isCompleted() || progress.isRewarded()) {
            return;
        }

        progress.markRewarded();

        // Award tokens
        RewardSystem.INSTANCE.getWallet(player.getUUID())
                .addCurrency(RewardSystem.Currency.TOKENS, challenge.getTokenReward());

        // Award prestige
        RewardSystem.INSTANCE.getWallet(player.getUUID())
                .addCurrency(RewardSystem.Currency.PRESTIGE, challenge.getPrestigeReward());

        LOGGER.info("[WeeklyChallengeManager] Awarded {} tokens and {} prestige to {} for weekly challenge {}",
                challenge.getTokenReward(), challenge.getPrestigeReward(),
                player.getName().getString(), challenge.getId());

        saveProgress();
    }

    // ========== Accessors ==========

    /**
     * Get this week's active challenges.
     */
    public List<WeeklyChallenge> getActiveChallenges() {
        checkAndRotateChallenges();
        return Collections.unmodifiableList(activeChallenges);
    }

    /**
     * Get all weekly progress for a player.
     */
    public Map<String, WeeklyChallenge.WeeklyProgress> getPlayerProgress(UUID playerId) {
        return playerProgress.getOrDefault(playerId, Collections.emptyMap());
    }

    /**
     * Check if a weekly challenge is completed by a player.
     */
    public boolean isChallengeCompleted(UUID playerId, String challengeId) {
        WeeklyChallenge.WeeklyProgress progress = getProgress(playerId, challengeId);
        return progress.isCompleted();
    }

    /**
     * Check if a weekly challenge reward was claimed by a player.
     */
    public boolean isChallengeRewarded(UUID playerId, String challengeId) {
        WeeklyChallenge.WeeklyProgress progress = getProgress(playerId, challengeId);
        return progress.isRewarded();
    }

    /**
     * Get current week info for display.
     */
    public String getWeekLabel() {
        return "Week " + currentWeek + " / " + currentYear;
    }

    // ========== Persistence ==========

    private void saveProgress() {
        if (dataDirectory == null) return;

        try {
            Path file = dataDirectory.resolve("weekly_progress.json");

            Map<String, Object> data = new HashMap<>();
            data.put("currentWeek", currentWeek);
            data.put("currentYear", currentYear);
            data.put("activeChallenges", activeChallenges.stream().map(WeeklyChallenge::getId).toList());

            // Serialize player progress
            Map<String, Map<String, Map<String, Object>>> progressData = new HashMap<>();
            for (var entry : playerProgress.entrySet()) {
                Map<String, Map<String, Object>> playerData = new HashMap<>();
                for (var progEntry : entry.getValue().entrySet()) {
                    WeeklyChallenge.WeeklyProgress prog = progEntry.getValue();
                    Map<String, Object> progData = new HashMap<>();
                    progData.put("currentValue", prog.getCurrentValue());
                    progData.put("completed", prog.isCompleted());
                    progData.put("rewarded", prog.isRewarded());
                    progData.put("completedAt", prog.getCompletedAt());
                    progData.put("highestCombo", prog.getHighestComboThisWeek());
                    progData.put("highestWave", prog.getHighestWaveThisWeek());
                    progData.put("timesReachedRank", prog.getTimesReachedTargetRank());
                    progData.put("perfectRuns", prog.getPerfectRunsThisWeek());
                    playerData.put(progEntry.getKey(), progData);
                }
                progressData.put(entry.getKey().toString(), playerData);
            }
            data.put("playerProgress", progressData);

            Files.writeString(file, GSON.toJson(data));
        } catch (IOException e) {
            LOGGER.error("[WeeklyChallengeManager] Failed to save progress", e);
        }
    }

    private void loadProgress() {
        if (dataDirectory == null) return;

        Path file = dataDirectory.resolve("weekly_progress.json");
        if (!Files.exists(file)) return;

        try {
            String content = Files.readString(file);
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> data = GSON.fromJson(content, type);

            // Load current week/year
            currentWeek = ((Number) data.get("currentWeek")).intValue();
            currentYear = ((Number) data.get("currentYear")).intValue();

            // Load active challenges by ID
            @SuppressWarnings("unchecked")
            List<String> activeIds = (List<String>) data.get("activeChallenges");
            if (activeIds != null) {
                activeChallenges.clear();
                for (String id : activeIds) {
                    challengePool.stream()
                            .filter(c -> c.getId().equals(id))
                            .findFirst()
                            .ifPresent(activeChallenges::add);
                }
            }

            // Load player progress
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Map<String, Object>>> progressData =
                    (Map<String, Map<String, Map<String, Object>>>) data.get("playerProgress");

            if (progressData != null) {
                for (var playerEntry : progressData.entrySet()) {
                    UUID playerId = UUID.fromString(playerEntry.getKey());
                    Map<String, WeeklyChallenge.WeeklyProgress> playerProgressMap = new ConcurrentHashMap<>();

                    for (var progEntry : playerEntry.getValue().entrySet()) {
                        String challengeId = progEntry.getKey();
                        Map<String, Object> progData = progEntry.getValue();

                        WeeklyChallenge.WeeklyProgress progress =
                                new WeeklyChallenge.WeeklyProgress(playerId, challengeId);

                        progress.setCurrentValue(((Number) progData.get("currentValue")).intValue());
                        if ((Boolean) progData.get("completed")) progress.markCompleted();
                        if ((Boolean) progData.get("rewarded")) progress.markRewarded();

                        // Load extended tracking (with null checks for backwards compatibility)
                        if (progData.get("highestCombo") != null) {
                            int combo = ((Number) progData.get("highestCombo")).intValue();
                            progress.updateHighestCombo(combo);
                        }
                        if (progData.get("highestWave") != null) {
                            int wave = ((Number) progData.get("highestWave")).intValue();
                            progress.updateHighestWave(wave);
                        }

                        playerProgressMap.put(challengeId, progress);
                    }

                    playerProgress.put(playerId, playerProgressMap);
                }
            }

            LOGGER.info("[WeeklyChallengeManager] Loaded progress for {} players, {} active challenges (week {}/{})",
                    playerProgress.size(), activeChallenges.size(), currentWeek, currentYear);
        } catch (Exception e) {
            LOGGER.error("[WeeklyChallengeManager] Failed to load progress", e);
        }
    }
}
