package com.devmod.endurance;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.endurance.combat.api.IComboSession;

public class LeaderboardSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaderboardSystem.class);
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
        .create();

    private static class InstantTypeAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toEpochMilli());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.ofEpochMilli(in.nextLong());
        }
    }

    public static final LeaderboardSystem INSTANCE = new LeaderboardSystem();

    private static final int MAX_ENTRIES_PER_BOARD = 100;

    // Leaderboard storage: category -> arena_id -> sorted entries
    private final Map<LeaderboardCategory, Map<String, List<LeaderboardEntry>>> boards = new ConcurrentHashMap<>();

    // Global boards (across all arenas)
    private final Map<LeaderboardCategory, List<LeaderboardEntry>> globalBoards = new ConcurrentHashMap<>();

    // Weekly boards (reset every week)
    private final Map<LeaderboardCategory, List<LeaderboardEntry>> weeklyBoards = new ConcurrentHashMap<>();
    private long weeklyResetTimestamp = 0;

    private Path dataDirectory;

    /**
     * Leaderboard categories.
     */
    public enum LeaderboardCategory {
        WAVES_COMPLETED("Waves Completed", "Most waves cleared", true),  // Higher is better
        BEST_TIME("Best Time", "Fastest completion", false),             // Lower is better
        HIGHEST_STYLE("Style Master", "Highest style score", true),
        MOST_KILLS("Executioner", "Most kills in one run", true),
        ENDLESS_STREAK("Endless Legend", "Highest endless wave", true),
        TOTAL_PRESTIGE("Prestige", "Total prestige earned", true),
        FLAWLESS_RUNS("Flawless", "No-hit completions", true);

        private final String displayName;
        private final String description;
        private final boolean higherIsBetter;

        LeaderboardCategory(String displayName, String description, boolean higherIsBetter) {
            this.displayName = displayName;
            this.description = description;
            this.higherIsBetter = higherIsBetter;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public boolean isHigherIsBetter() { return higherIsBetter; }
    }

    /**
     * A single leaderboard entry.
     */
    public record LeaderboardEntry(
        UUID playerId,
        String playerName,
        long score,
        String arenaId,
        Instant timestamp,
        Map<String, Object> metadata
    ) implements Comparable<LeaderboardEntry> {

        @Override
        public int compareTo(LeaderboardEntry other) {
            // Default: higher score is better
            return Long.compare(other.score, this.score);
        }

        /**
         * Compare for categories where lower is better (like time).
         */
        public int compareToLowerBetter(LeaderboardEntry other) {
            return Long.compare(this.score, other.score);
        }
    }

    private LeaderboardSystem() {
        // Initialize all boards
        for (LeaderboardCategory category : LeaderboardCategory.values()) {
            boards.put(category, new ConcurrentHashMap<>());
            globalBoards.put(category, new ArrayList<>());
            weeklyBoards.put(category, new ArrayList<>());
        }
    }

    // ========== Initialization ==========

    public void initialize(Path configDir) {
        this.dataDirectory = configDir.resolve("leaderboards");
        try {
            Files.createDirectories(dataDirectory);
            loadAllBoards();
            checkWeeklyReset();
            LOGGER.info("[LeaderboardSystem] Initialized with {} categories", LeaderboardCategory.values().length);
        } catch (IOException e) {
            LOGGER.error("[LeaderboardSystem] Failed to create data directory", e);
        }
    }

    // ========== Score Submission ==========

    /**
     * Submit a score for a completed quest.
     */
    public void submitQuestResult(ServerPlayer player, EnduranceQuest quest,
                                   IComboSession comboSession,
                                   String arenaId) {
        UUID playerId = player.getUUID();
        String playerName = player.getName().getString();
        Instant now = Instant.now();

        // Waves completed
        submitScore(LeaderboardCategory.WAVES_COMPLETED, playerId, playerName,
            quest.getCurrentWave(), arenaId, now, Map.of("totalWaves", quest.getTotalWaves()));

        // Best time (only for completed fixed-wave quests)
        if (quest.getState() == EnduranceQuestState.COMPLETED && !quest.isEndlessMode()) {
            submitScore(LeaderboardCategory.BEST_TIME, playerId, playerName,
                quest.getSessionDuration(), arenaId, now,
                Map.of("waves", quest.getTotalWaves()));
        }

        // Highest style score
        if (comboSession != null) {
            submitScore(LeaderboardCategory.HIGHEST_STYLE, playerId, playerName,
                comboSession.getTotalStyleEarned(), arenaId, now,
                Map.of("peakRank", comboSession.getHighestRank().name()));
        }

        // Most kills
        submitScore(LeaderboardCategory.MOST_KILLS, playerId, playerName,
            quest.getMobsKilledThisSession(), arenaId, now, Map.of());

        // Endless streak (only for endless mode)
        if (quest.isEndlessMode()) {
            submitScore(LeaderboardCategory.ENDLESS_STREAK, playerId, playerName,
                quest.getCurrentWave(), arenaId, now, Map.of());
        }

        // Flawless runs (no damage taken)
        if (quest.getState() == EnduranceQuestState.COMPLETED && quest.getDamageTakenThisSession() < 1.0f) {
            // Increment flawless counter
            long currentFlawless = getPlayerBestScore(LeaderboardCategory.FLAWLESS_RUNS, playerId, null);
            submitScore(LeaderboardCategory.FLAWLESS_RUNS, playerId, playerName,
                currentFlawless + 1, null, now, Map.of("arenaId", arenaId));
        }

        // Save after submission
        saveAllBoards();
    }

    /**
     * Submit a single score to a category.
     */
    public void submitScore(LeaderboardCategory category, UUID playerId, String playerName,
                            long score, String arenaId, Instant timestamp, Map<String, Object> metadata) {

        LeaderboardEntry entry = new LeaderboardEntry(playerId, playerName, score, arenaId, timestamp, metadata);

        // Track old rank for telemetry
        int oldGlobalRank = getPlayerRank(category, playerId, null);

        // Update arena-specific board
        if (arenaId != null) {
            Map<String, List<LeaderboardEntry>> arenaBoards = boards.get(category);
            List<LeaderboardEntry> arenaBoard = arenaBoards.computeIfAbsent(arenaId, k -> new ArrayList<>());
            updateBoard(arenaBoard, entry, category.isHigherIsBetter());
        }

        // Update global board
        List<LeaderboardEntry> globalBoard = globalBoards.get(category);
        updateBoard(globalBoard, entry, category.isHigherIsBetter());

        // Update weekly board
        checkWeeklyReset();
        List<LeaderboardEntry> weeklyBoard = weeklyBoards.get(category);
        updateBoard(weeklyBoard, entry, category.isHigherIsBetter());

        // Emit telemetry if rank changed
        int newGlobalRank = getPlayerRank(category, playerId, null);
        if (newGlobalRank > 0 && (oldGlobalRank <= 0 || newGlobalRank != oldGlobalRank)) {
            com.devmod.telemetry.endurance.EnduranceTelemetryService.INSTANCE.recordLeaderboardChange(
                playerId, category.name(), oldGlobalRank, newGlobalRank, (int) score
            );
        }

        LOGGER.debug("[LeaderboardSystem] Submitted score {} for {} in category {} (arena: {})",
            score, playerName, category.name(), arenaId);
    }

    /**
     * Update a leaderboard with a new entry.
     */
    private void updateBoard(List<LeaderboardEntry> board, LeaderboardEntry entry, boolean higherIsBetter) {
        synchronized (board) {
            // Check if player already has an entry
            Optional<LeaderboardEntry> existing = board.stream()
                .filter(e -> e.playerId().equals(entry.playerId()))
                .findFirst();

            if (existing.isPresent()) {
                LeaderboardEntry old = existing.get();
                boolean isBetter = higherIsBetter
                    ? entry.score() > old.score()
                    : entry.score() < old.score();

                if (isBetter) {
                    board.remove(old);
                    board.add(entry);
                }
            } else {
                board.add(entry);
            }

            // Sort and trim
            if (higherIsBetter) {
                board.sort(LeaderboardEntry::compareTo);
            } else {
                board.sort(LeaderboardEntry::compareToLowerBetter);
            }

            while (board.size() > MAX_ENTRIES_PER_BOARD) {
                board.remove(board.size() - 1);
            }
        }
    }

    // ========== Queries ==========

    /**
     * Get top entries for a category (global).
     */
    public List<LeaderboardEntry> getTopEntries(LeaderboardCategory category, int limit) {
        List<LeaderboardEntry> board = globalBoards.get(category);
        synchronized (board) {
            return board.stream().limit(limit).toList();
        }
    }

    /**
     * Get top entries for a category in a specific arena.
     */
    public List<LeaderboardEntry> getTopEntries(LeaderboardCategory category, String arenaId, int limit) {
        Map<String, List<LeaderboardEntry>> arenaBoards = boards.get(category);
        List<LeaderboardEntry> board = arenaBoards.get(arenaId);
        if (board == null) return List.of();

        synchronized (board) {
            return board.stream().limit(limit).toList();
        }
    }

    /**
     * Get weekly top entries.
     */
    public List<LeaderboardEntry> getWeeklyTopEntries(LeaderboardCategory category, int limit) {
        checkWeeklyReset();
        List<LeaderboardEntry> board = weeklyBoards.get(category);
        synchronized (board) {
            return board.stream().limit(limit).toList();
        }
    }

    /**
     * Get a player's rank in a category.
     * @return 1-based rank, or -1 if not on board
     */
    public int getPlayerRank(LeaderboardCategory category, UUID playerId, String arenaId) {
        List<LeaderboardEntry> board = arenaId != null
            ? boards.get(category).get(arenaId)
            : globalBoards.get(category);

        if (board == null) return -1;

        synchronized (board) {
            for (int i = 0; i < board.size(); i++) {
                if (board.get(i).playerId().equals(playerId)) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    /**
     * Get a player's best score in a category.
     */
    public long getPlayerBestScore(LeaderboardCategory category, UUID playerId, String arenaId) {
        List<LeaderboardEntry> board = arenaId != null
            ? boards.get(category).get(arenaId)
            : globalBoards.get(category);

        if (board == null) return 0;

        synchronized (board) {
            return board.stream()
                .filter(e -> e.playerId().equals(playerId))
                .findFirst()
                .map(LeaderboardEntry::score)
                .orElse(0L);
        }
    }

    /**
     * Get entries around a player's rank (for context display).
     */
    public List<LeaderboardEntry> getEntriesAroundPlayer(LeaderboardCategory category, UUID playerId,
                                                          String arenaId, int contextSize) {
        List<LeaderboardEntry> board = arenaId != null
            ? boards.get(category).get(arenaId)
            : globalBoards.get(category);

        if (board == null) return List.of();

        synchronized (board) {
            int playerIndex = -1;
            for (int i = 0; i < board.size(); i++) {
                if (board.get(i).playerId().equals(playerId)) {
                    playerIndex = i;
                    break;
                }
            }

            if (playerIndex == -1) {
                // Player not on board, return top entries
                return board.stream().limit(contextSize * 2 + 1).toList();
            }

            int start = Math.max(0, playerIndex - contextSize);
            int end = Math.min(board.size(), playerIndex + contextSize + 1);
            return board.subList(start, end);
        }
    }

    // ========== Weekly Reset ==========

    private void checkWeeklyReset() {
        long now = System.currentTimeMillis();
        long weekMs = 7L * 24 * 60 * 60 * 1000;

        if (weeklyResetTimestamp == 0 || now - weeklyResetTimestamp >= weekMs) {
            LOGGER.info("[LeaderboardSystem] Weekly leaderboard reset");
            for (LeaderboardCategory category : LeaderboardCategory.values()) {
                weeklyBoards.get(category).clear();
            }
            weeklyResetTimestamp = now;
        }
    }

    // ========== Persistence ==========

    private void loadAllBoards() {
        Path file = dataDirectory.resolve("leaderboards.json");
        if (!Files.exists(file)) return;

        try (Reader reader = Files.newBufferedReader(file)) {
            Type type = new TypeToken<LeaderboardData>(){}.getType();
            LeaderboardData data = GSON.fromJson(reader, type);
            if (data != null) {
                data.restore(this);
                LOGGER.info("[LeaderboardSystem] Loaded leaderboard data");
            }
        } catch (Exception e) {
            LOGGER.error("[LeaderboardSystem] Failed to load leaderboards", e);
        }
    }

    private void saveAllBoards() {
        if (dataDirectory == null) return;

        Path file = dataDirectory.resolve("leaderboards.json");
        Path tempFile = dataDirectory.resolve("leaderboards.json.tmp");

        try {
            LeaderboardData data = new LeaderboardData(this);

            try (Writer writer = Files.newBufferedWriter(tempFile)) {
                GSON.toJson(data, writer);
            }

            Files.move(tempFile, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.error("[LeaderboardSystem] Failed to save leaderboards", e);
        }
    }

    public void saveAll() {
        saveAllBoards();
    }

    // ========== Data Transfer Object ==========

    /**
     * Serializable data class for persistence.
     */
    private static class LeaderboardData {
        Map<String, Map<String, List<LeaderboardEntry>>> boards = new HashMap<>();
        Map<String, List<LeaderboardEntry>> globalBoards = new HashMap<>();
        Map<String, List<LeaderboardEntry>> weeklyBoards = new HashMap<>();
        long weeklyResetTimestamp;

        /** Required by Gson for deserialization via reflection. */
        @SuppressWarnings("unused")
        LeaderboardData() {}

        LeaderboardData(LeaderboardSystem system) {
            for (LeaderboardCategory cat : LeaderboardCategory.values()) {
                String key = cat.name();
                boards.put(key, new HashMap<>(system.boards.get(cat)));
                globalBoards.put(key, new ArrayList<>(system.globalBoards.get(cat)));
                weeklyBoards.put(key, new ArrayList<>(system.weeklyBoards.get(cat)));
            }
            this.weeklyResetTimestamp = system.weeklyResetTimestamp;
        }

        void restore(LeaderboardSystem system) {
            for (LeaderboardCategory cat : LeaderboardCategory.values()) {
                String key = cat.name();
                if (boards.containsKey(key)) {
                    system.boards.get(cat).putAll(boards.get(key));
                }
                if (globalBoards.containsKey(key)) {
                    system.globalBoards.get(cat).addAll(globalBoards.get(key));
                }
                if (weeklyBoards.containsKey(key)) {
                    system.weeklyBoards.get(cat).addAll(weeklyBoards.get(key));
                }
            }
            system.weeklyResetTimestamp = this.weeklyResetTimestamp;
        }
    }

    // ========== Utility ==========

    /**
     * Format a score for display based on category.
     */
    public static String formatScore(LeaderboardCategory category, long score) {
        return switch (category) {
            case BEST_TIME -> formatTime(score);
            case HIGHEST_STYLE -> String.format("%,d pts", score);
            case TOTAL_PRESTIGE -> String.format("%,d", score);
            default -> String.valueOf(score);
        };
    }

    private static String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        long ms = millis % 1000;
        return String.format("%d:%02d.%03d", minutes, seconds, ms);
    }
}
