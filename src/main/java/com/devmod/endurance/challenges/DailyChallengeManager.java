package com.devmod.endurance.challenges;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceQuest;
import com.devmod.endurance.RewardSystem;
import com.devmod.endurance.config.EnduranceConfigManager;
import com.devmod.notification.NotificationService;

public class DailyChallengeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DailyChallengeManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final DailyChallengeManager INSTANCE = new DailyChallengeManager();

    // Config manager reference
    private final EnduranceConfigManager config = EnduranceConfigManager.INSTANCE;

    // Config-aware getters for rewards
    public int getDailyTokenReward(UUID questId) {
        return config.getChallengeDailyTokenReward(questId);
    }

    public int getDailyXpReward(UUID questId) {
        return config.getChallengeDailyXpReward(questId);
    }

    public int getDailyChallengeCount(UUID questId) {
        return config.getChallengeDailyCount(questId);
    }

    // All available challenges (templates)
    private final List<DailyChallenge> challengePool = new ArrayList<>();

    // Today's active challenges (3 per day)
    private final List<DailyChallenge> activeChallenges = new ArrayList<>();

    // Player progress: playerId -> challengeId -> progress
    private final Map<UUID, Map<String, DailyChallenge.ChallengeProgress>> playerProgress = new ConcurrentHashMap<>();

    // Current day tracker for rotation
    private LocalDate currentDay;

    // Persistence
    private Path dataDirectory;

    private DailyChallengeManager() {
        initializeChallengePool();
    }

    // ========== Initialization ==========

    /**
     * Initialize the pool of all possible daily challenges.
     */
    private void initializeChallengePool() {
        // EASY Challenges (40% rotation chance, 500 tokens, 3 prestige base)
        challengePool.add(new DailyChallenge(
                "easy_wave_5",
                "devmod.challenge.wave_reach",
                "devmod.challenge.wave_reach.desc",
                DailyChallenge.ChallengeType.WAVE_REACH,
                DailyChallenge.ChallengeDifficulty.EASY,
                5, 500, 3,
                (quest, progress) -> quest.getCurrentWave() >= 5
        ));

        challengePool.add(new DailyChallenge(
                "easy_kills_30",
                "devmod.challenge.kills",
                "devmod.challenge.kills.desc",
                DailyChallenge.ChallengeType.KILLS,
                DailyChallenge.ChallengeDifficulty.EASY,
                30, 500, 3,
                (quest, progress) -> progress.getCurrentValue() >= 30
        ));

        challengePool.add(new DailyChallenge(
                "easy_c_rank",
                "devmod.challenge.style_rank",
                "devmod.challenge.style_rank.desc",
                DailyChallenge.ChallengeType.STYLE_RANK,
                DailyChallenge.ChallengeDifficulty.EASY,
                2, 500, 3, // C rank = 2
                (quest, progress) -> progress.getCurrentValue() >= 2
        ));

        challengePool.add(new DailyChallenge(
                "easy_combo_10",
                "devmod.challenge.combo",
                "devmod.challenge.combo.desc",
                DailyChallenge.ChallengeType.COMBO,
                DailyChallenge.ChallengeDifficulty.EASY,
                10, 500, 3,
                (quest, progress) -> progress.getCurrentValue() >= 10
        ));

        // MEDIUM Challenges (35% rotation chance, 750 tokens, 5 prestige base)
        challengePool.add(new DailyChallenge(
                "medium_wave_10",
                "devmod.challenge.wave_reach",
                "devmod.challenge.wave_reach.desc",
                DailyChallenge.ChallengeType.WAVE_REACH,
                DailyChallenge.ChallengeDifficulty.MEDIUM,
                10, 750, 5,
                (quest, progress) -> quest.getCurrentWave() >= 10
        ));

        challengePool.add(new DailyChallenge(
                "medium_kills_100",
                "devmod.challenge.kills",
                "devmod.challenge.kills.desc",
                DailyChallenge.ChallengeType.KILLS,
                DailyChallenge.ChallengeDifficulty.MEDIUM,
                100, 750, 5,
                (quest, progress) -> progress.getCurrentValue() >= 100
        ));

        challengePool.add(new DailyChallenge(
                "medium_b_rank",
                "devmod.challenge.style_rank",
                "devmod.challenge.style_rank.desc",
                DailyChallenge.ChallengeType.STYLE_RANK,
                DailyChallenge.ChallengeDifficulty.MEDIUM,
                3, 750, 5, // B rank = 3
                (quest, progress) -> progress.getCurrentValue() >= 3
        ));

        challengePool.add(new DailyChallenge(
                "medium_boss_1",
                "devmod.challenge.boss_kills",
                "devmod.challenge.boss_kills.desc",
                DailyChallenge.ChallengeType.BOSS_KILLS,
                DailyChallenge.ChallengeDifficulty.MEDIUM,
                1, 750, 5,
                (quest, progress) -> progress.getCurrentValue() >= 1
        ));

        challengePool.add(new DailyChallenge(
                "medium_crit_20",
                "devmod.challenge.critical_kills",
                "devmod.challenge.critical_kills.desc",
                DailyChallenge.ChallengeType.CRITICAL_KILLS,
                DailyChallenge.ChallengeDifficulty.MEDIUM,
                20, 750, 5,
                (quest, progress) -> progress.getCurrentValue() >= 20
        ));

        challengePool.add(new DailyChallenge(
                "medium_combo_25",
                "devmod.challenge.combo",
                "devmod.challenge.combo.desc",
                DailyChallenge.ChallengeType.COMBO,
                DailyChallenge.ChallengeDifficulty.MEDIUM,
                25, 750, 5,
                (quest, progress) -> progress.getCurrentValue() >= 25
        ));

        // HARD Challenges (20% rotation chance, 1000 tokens, 8 prestige base)
        challengePool.add(new DailyChallenge(
                "hard_wave_15",
                "devmod.challenge.wave_reach",
                "devmod.challenge.wave_reach.desc",
                DailyChallenge.ChallengeType.WAVE_REACH,
                DailyChallenge.ChallengeDifficulty.HARD,
                15, 1000, 8,
                (quest, progress) -> quest.getCurrentWave() >= 15
        ));

        challengePool.add(new DailyChallenge(
                "hard_a_rank",
                "devmod.challenge.style_rank",
                "devmod.challenge.style_rank.desc",
                DailyChallenge.ChallengeType.STYLE_RANK,
                DailyChallenge.ChallengeDifficulty.HARD,
                4, 1000, 8, // A rank = 4
                (quest, progress) -> progress.getCurrentValue() >= 4
        ));

        challengePool.add(new DailyChallenge(
                "hard_no_death",
                "devmod.challenge.no_death",
                "devmod.challenge.no_death.desc",
                DailyChallenge.ChallengeType.NO_DEATH,
                DailyChallenge.ChallengeDifficulty.HARD,
                10, 1000, 8, // Complete wave 10 without dying
                (quest, progress) -> quest.getCurrentWave() >= 10 && quest.getDeathsThisSession() == 0
        ));

        challengePool.add(new DailyChallenge(
                "hard_boss_2",
                "devmod.challenge.boss_kills",
                "devmod.challenge.boss_kills.desc",
                DailyChallenge.ChallengeType.BOSS_KILLS,
                DailyChallenge.ChallengeDifficulty.HARD,
                2, 1000, 8,
                (quest, progress) -> progress.getCurrentValue() >= 2
        ));

        challengePool.add(new DailyChallenge(
                "hard_combo_50",
                "devmod.challenge.combo",
                "devmod.challenge.combo.desc",
                DailyChallenge.ChallengeType.COMBO,
                DailyChallenge.ChallengeDifficulty.HARD,
                50, 1000, 8,
                (quest, progress) -> progress.getCurrentValue() >= 50
        ));

        // EXTREME Challenges (5% rotation chance, 1500 tokens, 12 prestige base)
        challengePool.add(new DailyChallenge(
                "extreme_wave_20",
                "devmod.challenge.wave_reach",
                "devmod.challenge.wave_reach.desc",
                DailyChallenge.ChallengeType.WAVE_REACH,
                DailyChallenge.ChallengeDifficulty.EXTREME,
                20, 1500, 12,
                (quest, progress) -> quest.getCurrentWave() >= 20
        ));

        challengePool.add(new DailyChallenge(
                "extreme_s_rank",
                "devmod.challenge.style_rank",
                "devmod.challenge.style_rank.desc",
                DailyChallenge.ChallengeType.STYLE_RANK,
                DailyChallenge.ChallengeDifficulty.EXTREME,
                5, 1500, 12, // S rank = 5
                (quest, progress) -> progress.getCurrentValue() >= 5
        ));

        challengePool.add(new DailyChallenge(
                "extreme_no_death_15",
                "devmod.challenge.no_death",
                "devmod.challenge.no_death.desc",
                DailyChallenge.ChallengeType.NO_DEATH,
                DailyChallenge.ChallengeDifficulty.EXTREME,
                15, 1500, 12,
                (quest, progress) -> quest.getCurrentWave() >= 15 && quest.getDeathsThisSession() == 0
        ));

        challengePool.add(new DailyChallenge(
                "extreme_boss_3",
                "devmod.challenge.boss_kills",
                "devmod.challenge.boss_kills.desc",
                DailyChallenge.ChallengeType.BOSS_KILLS,
                DailyChallenge.ChallengeDifficulty.EXTREME,
                3, 1500, 12,
                (quest, progress) -> progress.getCurrentValue() >= 3
        ));

        challengePool.add(new DailyChallenge(
                "extreme_sss_run",
                "devmod.challenge.style_rank",
                "devmod.challenge.style_rank.desc",
                DailyChallenge.ChallengeType.STYLE_RANK,
                DailyChallenge.ChallengeDifficulty.EXTREME,
                7, 2000, 15, // SSS rank = 7
                (quest, progress) -> progress.getCurrentValue() >= 7
        ));

        LOGGER.info("[DailyChallengeManager] Initialized {} challenges in pool", challengePool.size());
    }

    // ========== Daily Rotation ==========

    /**
     * Initialize the manager with server data directory.
     */
    public void initialize(Path serverDataDir) {
        this.dataDirectory = serverDataDir.resolve("devmod").resolve("challenges");
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            LOGGER.error("[DailyChallengeManager] Failed to create data directory", e);
        }

        loadProgress();
        checkAndRotateChallenges();
    }

    /**
     * Check if we need to rotate challenges (new day) and do so if needed.
     */
    public void checkAndRotateChallenges() {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));

        if (currentDay == null || !currentDay.equals(today)) {
            rotateChallenges(today);
        }
    }

    /**
     * Rotate to new daily challenges.
     */
    private void rotateChallenges(LocalDate day) {
        currentDay = day;
        activeChallenges.clear();

        // Use day as seed for deterministic rotation
        long seed = day.toEpochDay();
        SplittableRandom dayRandom = new SplittableRandom(seed);

        // Select challenges by difficulty tier
        // Slot 1: Easy (always)
        // Slot 2: Medium (always)
        // Slot 3: Hard (80%) or Extreme (20%)

        List<DailyChallenge> easyChallenges = challengePool.stream()
                .filter(c -> c.getDifficulty() == DailyChallenge.ChallengeDifficulty.EASY)
                .toList();

        List<DailyChallenge> mediumChallenges = challengePool.stream()
                .filter(c -> c.getDifficulty() == DailyChallenge.ChallengeDifficulty.MEDIUM)
                .toList();

        List<DailyChallenge> hardChallenges = challengePool.stream()
                .filter(c -> c.getDifficulty() == DailyChallenge.ChallengeDifficulty.HARD)
                .toList();

        List<DailyChallenge> extremeChallenges = challengePool.stream()
                .filter(c -> c.getDifficulty() == DailyChallenge.ChallengeDifficulty.EXTREME)
                .toList();

        // Slot 1: Easy
        if (!easyChallenges.isEmpty()) {
            activeChallenges.add(easyChallenges.get(dayRandom.nextInt(easyChallenges.size())));
        }

        // Slot 2: Medium
        if (!mediumChallenges.isEmpty()) {
            activeChallenges.add(mediumChallenges.get(dayRandom.nextInt(mediumChallenges.size())));
        }

        // Slot 3: Hard or Extreme
        if (dayRandom.nextDouble() < 0.2 && !extremeChallenges.isEmpty()) {
            activeChallenges.add(extremeChallenges.get(dayRandom.nextInt(extremeChallenges.size())));
        } else if (!hardChallenges.isEmpty()) {
            activeChallenges.add(hardChallenges.get(dayRandom.nextInt(hardChallenges.size())));
        }

        // Reset player progress for new day
        playerProgress.clear();

        LOGGER.info("[DailyChallengeManager] Rotated to day {} with {} challenges: {}",
                day, activeChallenges.size(),
                activeChallenges.stream().map(DailyChallenge::getId).toList());

        saveProgress();
    }

    // ========== Progress Tracking ==========

    /**
     * Get or create player progress for a challenge.
     */
    public DailyChallenge.ChallengeProgress getProgress(UUID playerId, String challengeId) {
        return playerProgress
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(challengeId, k -> new DailyChallenge.ChallengeProgress(playerId, challengeId));
    }

    /**
     * Update progress for kill-based challenges.
     */
    public void onMobKill(UUID playerId, boolean isCritical, boolean isBoss) {
        for (DailyChallenge challenge : activeChallenges) {
            DailyChallenge.ChallengeProgress progress = getProgress(playerId, challenge.getId());
            if (progress.isCompleted()) continue;

            switch (challenge.getType()) {
                case KILLS:
                    progress.increment();
                    break;
                case CRITICAL_KILLS:
                    if (isCritical) progress.increment();
                    break;
                case BOSS_KILLS:
                    if (isBoss) progress.increment();
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Update progress for combo-based challenges.
     */
    public void onComboUpdate(UUID playerId, int currentCombo) {
        for (DailyChallenge challenge : activeChallenges) {
            if (challenge.getType() != DailyChallenge.ChallengeType.COMBO) continue;

            DailyChallenge.ChallengeProgress progress = getProgress(playerId, challenge.getId());
            if (progress.isCompleted()) continue;

            // Track max combo
            if (currentCombo > progress.getCurrentValue()) {
                progress.setCurrentValue(currentCombo);
            }
        }
    }

    /**
     * Update progress for style rank challenges.
     */
    public void onStyleRankUpdate(UUID playerId, ComboSystem.StyleRank rank) {
        int rankValue = rank.ordinal(); // D=0, C=1, B=2, A=3, S=4, SS=5, SSS=6
        // Shift by 1 to match our target values (D=1, C=2, etc.)
        int adjustedRank = rankValue + 1;

        for (DailyChallenge challenge : activeChallenges) {
            if (challenge.getType() != DailyChallenge.ChallengeType.STYLE_RANK) continue;

            DailyChallenge.ChallengeProgress progress = getProgress(playerId, challenge.getId());
            if (progress.isCompleted()) continue;

            if (adjustedRank > progress.getCurrentValue()) {
                progress.setCurrentValue(adjustedRank);
            }
        }
    }

    /**
     * Check all challenges for completion at quest end.
     */
    public List<DailyChallenge> checkQuestCompletion(ServerPlayer player, EnduranceQuest quest) {
        List<DailyChallenge> completedChallenges = new ArrayList<>();

        for (DailyChallenge challenge : activeChallenges) {
            DailyChallenge.ChallengeProgress progress = getProgress(player.getUUID(), challenge.getId());

            if (progress.isCompleted() || progress.isRewarded()) continue;

            // Update wave-based progress
            if (challenge.getType() == DailyChallenge.ChallengeType.WAVE_REACH) {
                progress.setCurrentValue(quest.getCurrentWave());
            }

            if (challenge.isComplete(quest, progress)) {
                progress.markCompleted();
                completedChallenges.add(challenge);
                LOGGER.info("[DailyChallengeManager] Player {} completed challenge {}",
                        player.getName().getString(), challenge.getId());
            }
        }

        saveProgress();
        return completedChallenges;
    }

    /**
     * Award rewards for a completed challenge.
     */
    public void awardRewards(ServerPlayer player, DailyChallenge challenge) {
        DailyChallenge.ChallengeProgress progress = getProgress(player.getUUID(), challenge.getId());

        if (!progress.isCompleted() || progress.isRewarded()) {
            return;
        }

        progress.markRewarded();

        // Award tokens
        RewardSystem.INSTANCE.getWallet(player.getUUID()).addCurrency(RewardSystem.Currency.TOKENS, challenge.getTokenReward());

        // Award prestige
        RewardSystem.INSTANCE.getWallet(player.getUUID()).addCurrency(RewardSystem.Currency.PRESTIGE, challenge.getPrestigeReward());

        NotificationService.INSTANCE.notifyChallengeReward(
            player.getUUID(),
            "daily",
            challenge.getName().getString(),
            challenge.getTokenReward(),
            challenge.getPrestigeReward()
        );

        LOGGER.info("[DailyChallengeManager] Awarded {} tokens and {} prestige to {} for challenge {}",
                challenge.getTokenReward(), challenge.getPrestigeReward(),
                player.getName().getString(), challenge.getId());

        saveProgress();
    }

    // ========== Accessors ==========

    /**
     * Get today's active challenges.
     */
    public List<DailyChallenge> getActiveChallenges() {
        checkAndRotateChallenges();
        return Collections.unmodifiableList(activeChallenges);
    }

    /**
     * Get all progress for a player.
     */
    public Map<String, DailyChallenge.ChallengeProgress> getPlayerProgress(UUID playerId) {
        return playerProgress.getOrDefault(playerId, Collections.emptyMap());
    }

    /**
     * Check if a challenge is completed by a player.
     */
    public boolean isChallengeCompleted(UUID playerId, String challengeId) {
        DailyChallenge.ChallengeProgress progress = getProgress(playerId, challengeId);
        return progress.isCompleted();
    }

    /**
     * Check if a challenge reward was claimed by a player.
     */
    public boolean isChallengeRewarded(UUID playerId, String challengeId) {
        DailyChallenge.ChallengeProgress progress = getProgress(playerId, challengeId);
        return progress.isRewarded();
    }

    // ========== Persistence ==========

    private void saveProgress() {
        if (dataDirectory == null) return;

        try {
            Path file = dataDirectory.resolve("daily_progress.json");

            Map<String, Object> data = new HashMap<>();
            data.put("currentDay", currentDay != null ? currentDay.toString() : null);
            data.put("activeChallenges", activeChallenges.stream().map(DailyChallenge::getId).toList());

            // Serialize player progress
            Map<String, Map<String, Map<String, Object>>> progressData = new HashMap<>();
            for (var entry : playerProgress.entrySet()) {
                Map<String, Map<String, Object>> playerData = new HashMap<>();
                for (var progEntry : entry.getValue().entrySet()) {
                    DailyChallenge.ChallengeProgress prog = progEntry.getValue();
                    Map<String, Object> progData = new HashMap<>();
                    progData.put("currentValue", prog.getCurrentValue());
                    progData.put("completed", prog.isCompleted());
                    progData.put("rewarded", prog.isRewarded());
                    progData.put("completedAt", prog.getCompletedAt());
                    playerData.put(progEntry.getKey(), progData);
                }
                progressData.put(entry.getKey().toString(), playerData);
            }
            data.put("playerProgress", progressData);

            Files.writeString(file, GSON.toJson(data));
        } catch (IOException e) {
            LOGGER.error("[DailyChallengeManager] Failed to save progress", e);
        }
    }

    private void loadProgress() {
        if (dataDirectory == null) return;

        Path file = dataDirectory.resolve("daily_progress.json");
        if (!Files.exists(file)) return;

        try {
            String content = Files.readString(file);
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> data = GSON.fromJson(content, type);

            // Load current day
            String dayStr = (String) data.get("currentDay");
            if (dayStr != null) {
                currentDay = LocalDate.parse(dayStr);
            }

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
                    Map<String, DailyChallenge.ChallengeProgress> playerProgressMap = new ConcurrentHashMap<>();

                    for (var progEntry : playerEntry.getValue().entrySet()) {
                        String challengeId = progEntry.getKey();
                        Map<String, Object> progData = progEntry.getValue();

                        DailyChallenge.ChallengeProgress progress =
                                new DailyChallenge.ChallengeProgress(playerId, challengeId);

                        progress.setCurrentValue(((Number) progData.get("currentValue")).intValue());
                        if ((Boolean) progData.get("completed")) progress.markCompleted();
                        if ((Boolean) progData.get("rewarded")) progress.markRewarded();

                        playerProgressMap.put(challengeId, progress);
                    }

                    playerProgress.put(playerId, playerProgressMap);
                }
            }

            LOGGER.info("[DailyChallengeManager] Loaded progress for {} players, {} active challenges",
                    playerProgress.size(), activeChallenges.size());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[DailyChallengeManager] Failed to load progress", e);
        }
    }
}
