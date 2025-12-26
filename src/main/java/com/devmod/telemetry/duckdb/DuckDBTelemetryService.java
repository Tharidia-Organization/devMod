package com.devmod.telemetry.duckdb;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;

public class DuckDBTelemetryService {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DuckDBTelemetryService INSTANCE = new DuckDBTelemetryService();

    private @Nullable DuckDBConnectionManager connectionManager;
    private @Nullable DuckDBBatchWriter batchWriter;
    private @Nullable DuckDBQueryAPI queryAPI;

    private volatile boolean initialized = false;
    private volatile boolean enabled = false;
    private @Nullable Path dbPath;

    // Server environment info
    private boolean isDedicatedServer = false;
    private boolean isSingleplayer = false;

    // Dedup guards for progression events (P1)
    // Advancement: (player_uuid, advancement_id) -> logged this session
    private final Set<String> loggedAdvancements = ConcurrentHashMap.newKeySet();
    // Dimension change: player_uuid -> last change timestamp (TTL 5s)
    private final Map<UUID, Long> lastDimensionChange = new ConcurrentHashMap<>();
    private static final long DIMENSION_CHANGE_TTL_MS = 5000;
    // XP: player_uuid -> (lastLogTime, accumulatedXp) for batching
    private final Map<UUID, XpBatch> xpBatches = new ConcurrentHashMap<>();
    private static final long XP_BATCH_INTERVAL_MS = 1000;
    // Session dedup/enrichment: session_uuid -> logged state
    private final Map<UUID, SessionLogState> sessionLogState = new ConcurrentHashMap<>();
    private static final long SESSION_LOG_TTL_MS = 15 * 60 * 1000L;

    private DuckDBTelemetryService() {}

    // ============================================
    // LIFECYCLE
    // ============================================

    /**
     * Initialize DuckDB telemetry for the current server instance.
     *
     * @param server The Minecraft server instance
     * @return true if initialization successful
     */
    public boolean initialize(MinecraftServer server) {
        if (!DuckDBConfig.ENABLED) {
            LOGGER.info("[DuckDB] DuckDB telemetry disabled in config");
            return false;
        }

        if (initialized) {
            LOGGER.warn("[DuckDB] Already initialized, skipping");
            return true;
        }

        try {
            // Determine server type
            isDedicatedServer = server.isDedicatedServer();
            isSingleplayer = !isDedicatedServer;

            // Build database path
            Path serverDir = server.getServerDirectory();
            dbPath = serverDir.resolve(DuckDBConfig.TELEMETRY_DIR).resolve(DuckDBConfig.DB_FILENAME);

            LOGGER.info("[DuckDB] Initializing DuckDB at: {}", dbPath);
            LOGGER.info("[DuckDB] Server type: {}", isDedicatedServer ? "Dedicated" : "Singleplayer");

            // Initialize connection manager
            connectionManager = new DuckDBConnectionManager(dbPath);

            // Ensure schema exists
            DuckDBSchemaManager.ensureSchema(connectionManager.getConnection());

            // Initialize batch writer
            batchWriter = new DuckDBBatchWriter(connectionManager);
            batchWriter.start();

            // Initialize query API
            queryAPI = new DuckDBQueryAPI(connectionManager);

            initialized = true;
            enabled = true;

            LOGGER.info("[DuckDB] Initialization complete");
            LOGGER.info("[DuckDB] === TELEMETRY CONFIG ===");
            LOGGER.info("[DuckDB]   ENABLED = {}", DuckDBConfig.ENABLED);
            LOGGER.info("[DuckDB]   NDJSON_FALLBACK = {}", DuckDBConfig.NDJSON_FALLBACK);
            LOGGER.info("[DuckDB]   FALLBACK_ON_ERROR = {}", DuckDBConfig.FALLBACK_ON_ERROR);
            LOGGER.info("[DuckDB] With NDJSON_FALLBACK=false, NDJSON writes will be SKIPPED");
            return true;

        } catch (SQLException e) {
            LOGGER.error("[DuckDB] Failed to initialize: {}", e.getMessage());
            cleanup();
            return false;
        }
    }

    /**
     * Shutdown DuckDB telemetry gracefully.
     * Flushes pending writes and closes connections.
     */
    public void shutdown() {
        if (!initialized) return;

        LOGGER.info("[DuckDB] Shutting down DuckDB telemetry...");
        enabled = false;

        // Shutdown batch writer (flushes pending writes)
        if (batchWriter != null) {
            batchWriter.shutdown();
            batchWriter = null;
        }

        // Shutdown connection manager
        if (connectionManager != null) {
            connectionManager.shutdown();
            connectionManager = null;
        }

        queryAPI = null;
        initialized = false;

        LOGGER.info("[DuckDB] Shutdown complete");
    }

    /**
     * Cleanup on failed initialization.
     */
    private void cleanup() {
        if (batchWriter != null) {
            try { batchWriter.shutdown(); } catch (Exception ignored) {}
            batchWriter = null;
        }
        if (connectionManager != null) {
            try { connectionManager.shutdown(); } catch (Exception ignored) {}
            connectionManager = null;
        }
        queryAPI = null;
        initialized = false;
        enabled = false;
    }

    // ============================================
    // STATUS
    // ============================================

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Called by DuckDBBatchWriter when circuit breaker is triggered.
     * Behavior depends on FALLBACK_ON_ERROR config:
     * - true: Enable NDJSON fallback for this session
     * - false: Disable telemetry entirely (strict mode)
     */
    public void triggerCircuitBreaker() {
        if (!enabled) return;

        LOGGER.error("[DuckDB] Circuit breaker activated - disabling DuckDB writes");
        enabled = false;

        // Respect FALLBACK_ON_ERROR policy
        if (DuckDBConfig.FALLBACK_ON_ERROR) {
            if (!DuckDBConfig.NDJSON_FALLBACK) {
                LOGGER.warn("[DuckDB] FALLBACK_ON_ERROR=true: enabling NDJSON fallback for this session");
                DuckDBConfig.NDJSON_FALLBACK = true;
            }
        } else {
            LOGGER.error("[DuckDB] FALLBACK_ON_ERROR=false: telemetry DISABLED for this session (strict mode)");
            // Don't enable NDJSON - telemetry is OFF
        }
    }

    /**
     * Check if circuit breaker was triggered (for diagnostics).
     */
    public boolean isCircuitBroken() {
        return initialized && !enabled;
    }

    public boolean isDedicatedServer() {
        return isDedicatedServer;
    }

    public boolean isSingleplayer() {
        return isSingleplayer;
    }

    public @Nullable Path getDbPath() {
        return dbPath;
    }

    /**
     * Get the query API for analytics queries.
     */
    public @Nullable DuckDBQueryAPI getQueryAPI() {
        return queryAPI;
    }

    /**
     * Get the batch writer for direct access to queue methods.
     * Useful for arena telemetry that needs to queue custom events.
     */
    public @Nullable DuckDBBatchWriter getBatchWriter() {
        return batchWriter;
    }

    /**
     * Get statistics about the batch writer.
     */
    public String getStats() {
        if (batchWriter == null) return "Not initialized";
        return String.format("Inserts: %d, Batches: %d, Pending: %d, Dropped: %d",
            batchWriter.getTotalInserts(),
            batchWriter.getTotalBatches(),
            batchWriter.getPendingInserts(),
            batchWriter.getDroppedInserts());
    }

    /**
     * Get a read-only connection for analytics queries.
     * Used by the dashboard REST API.
     *
     * @return The DuckDB connection, or null if not initialized or on error
     */
    public @Nullable java.sql.Connection getConnection() {
        if (!initialized || connectionManager == null) {
            return null;
        }
        try {
            return connectionManager.getConnection();
        } catch (SQLException e) {
            LOGGER.error("[DuckDB] Failed to get connection for dashboard: {}", e.getMessage());
            return null;
        }
    }

    // ============================================
    // COMBAT EVENTS
    // ============================================

    /**
     * Log a hit event.
     */
    public void logHit(@Nullable String room, @Nullable String world,
                       @Nullable String attackerName, @Nullable String attackerType,
                       @Nullable String targetName, @Nullable String targetType,
                       double damage, String damageType,
                       @Nullable Double hpBefore, @Nullable Double hpAfter,
                       @Nullable String bodyPart, @Nullable Double distance,
                       @Nullable Double armorPenBonus, boolean isMiss,
                       boolean isHazard, @Nullable String hazardType,
                       @Nullable String attackerStateJson, @Nullable String targetStateJson) {
        logHit(room, world, null, null, null, null, null, null,
            attackerName, attackerType, targetName, targetType,
            damage, damageType, hpBefore, hpAfter, bodyPart, distance,
            armorPenBonus, isMiss, isHazard, hazardType,
            attackerStateJson, targetStateJson);
    }

    public void logHit(@Nullable String room, @Nullable String world,
                       @Nullable String templateId, @Nullable Integer templateVersion,
                       @Nullable String policyId, @Nullable Integer policyVersion,
                       @Nullable UUID arenaId, @Nullable UUID sessionId,
                       @Nullable String attackerName, @Nullable String attackerType,
                       @Nullable String targetName, @Nullable String targetType,
                       double damage, @Nullable String damageType,
                       @Nullable Double hpBefore, @Nullable Double hpAfter,
                       @Nullable String bodyPart, @Nullable Double distance,
                       @Nullable Double armorPenBonus, boolean isMiss,
                       boolean isHazard, @Nullable String hazardType,
                       @Nullable String attackerStateJson, @Nullable String targetStateJson) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueCombatHit(Instant.now(), room, world,
            templateId, templateVersion, policyId, policyVersion, arenaId, sessionId,
            attackerName, attackerType, targetName, targetType,
            damage, damageType, hpBefore, hpAfter, bodyPart, distance,
            armorPenBonus, isMiss, isHazard, hazardType,
            attackerStateJson, targetStateJson);
    }

    /**
     * Log a death event.
     */
    public void logDeath(@Nullable String room, @Nullable String world,
                         @Nullable String targetName, @Nullable String targetType,
                         @Nullable String cause, @Nullable Long ttkFirstHitMs, @Nullable Long ttkSpawnMs) {
        logDeath(room, world, null, null, null, null, null, null,
            targetName, targetType, cause, ttkFirstHitMs, ttkSpawnMs);
    }

    public void logDeath(@Nullable String room, @Nullable String world,
                         @Nullable String templateId, @Nullable Integer templateVersion,
                         @Nullable String policyId, @Nullable Integer policyVersion,
                         @Nullable UUID arenaId, @Nullable UUID sessionId,
                         @Nullable String targetName, @Nullable String targetType,
                         @Nullable String cause, @Nullable Long ttkFirstHitMs, @Nullable Long ttkSpawnMs) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueCombatDeath(Instant.now(), room, world,
            templateId, templateVersion, policyId, policyVersion, arenaId, sessionId,
            targetName, targetType, cause, ttkFirstHitMs, ttkSpawnMs);
    }

    /**
     * Log a heal event.
     */
    public void logHeal(@Nullable String room, @Nullable String world,
                        @Nullable String targetName, @Nullable String targetType,
                        double healAmount, @Nullable Double hpBefore, @Nullable Double hpAfter,
                        @Nullable String source) {
        logHeal(room, world, null, null, null, null, null, null,
            targetName, targetType, healAmount, hpBefore, hpAfter, source);
    }

    public void logHeal(@Nullable String room, @Nullable String world,
                        @Nullable String templateId, @Nullable Integer templateVersion,
                        @Nullable String policyId, @Nullable Integer policyVersion,
                        @Nullable UUID arenaId, @Nullable UUID sessionId,
                        @Nullable String targetName, @Nullable String targetType,
                        double healAmount, @Nullable Double hpBefore, @Nullable Double hpAfter,
                        @Nullable String source) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueCombatHeal(Instant.now(), room, world,
            templateId, templateVersion, policyId, policyVersion, arenaId, sessionId,
            targetName, targetType, healAmount, hpBefore, hpAfter, source);
    }

    /**
     * Log a spawn event.
     */
    public void logSpawn(@Nullable String room, @Nullable String world,
                         @Nullable String entityName, @Nullable String entityType,
                         @Nullable String reason, boolean spawnFail,
                         @Nullable Double x, @Nullable Double y, @Nullable Double z) {
        logSpawn(room, world, null, null, null, null, null, null,
            entityName, entityType, reason, spawnFail, x, y, z);
    }

    public void logSpawn(@Nullable String room, @Nullable String world,
                         @Nullable String templateId, @Nullable Integer templateVersion,
                         @Nullable String policyId, @Nullable Integer policyVersion,
                         @Nullable UUID arenaId, @Nullable UUID sessionId,
                         @Nullable String entityName, @Nullable String entityType,
                         @Nullable String reason, boolean spawnFail,
                         @Nullable Double x, @Nullable Double y, @Nullable Double z) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueCombatSpawn(Instant.now(), room, world,
            templateId, templateVersion, policyId, policyVersion, arenaId, sessionId,
            entityName, entityType, reason, spawnFail, x, y, z);
    }

    /**
     * Log a fight session result.
     */
    public void logFight(String room, String world, Instant startTs, Instant endTs,
                         long durationMs, int hits, int mobKills, int playerDeaths,
                         String[] players, String mobKillsByTypeJson,
                         String playerDeathsByNameJson, String ttkByTypeJson,
                         double burstMax, double hpAfterPlayersAvg, double hpAfterMobsAvg) {
        logFight(room, world, null, null, null, null, null, null,
            startTs, endTs, durationMs, hits, mobKills, playerDeaths,
            players, mobKillsByTypeJson, playerDeathsByNameJson, ttkByTypeJson,
            burstMax, hpAfterPlayersAvg, hpAfterMobsAvg);
    }

    public void logFight(String room, String world,
                         @Nullable String templateId, @Nullable Integer templateVersion,
                         @Nullable String policyId, @Nullable Integer policyVersion,
                         @Nullable UUID arenaId, @Nullable UUID sessionId,
                         Instant startTs, Instant endTs,
                         long durationMs, int hits, int mobKills, int playerDeaths,
                         String[] players, String mobKillsByTypeJson,
                         String playerDeathsByNameJson, String ttkByTypeJson,
                         double burstMax, double hpAfterPlayersAvg, double hpAfterMobsAvg) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueCombatFight(room, world,
            templateId, templateVersion, policyId, policyVersion, arenaId, sessionId,
            startTs, endTs, durationMs, hits, mobKills,
            playerDeaths, players, mobKillsByTypeJson, playerDeathsByNameJson, ttkByTypeJson,
            burstMax, hpAfterPlayersAvg, hpAfterMobsAvg);
    }

    // ============================================
    // ENDURANCE EVENTS
    // ============================================

    /**
     * Log a wave start event.
     */
    public void logWaveStart(UUID sessionId, int waveNumber, int mobCount,
                             int playerCount, @Nullable String questType, String[] modifiers) {
        logWaveStart(sessionId, waveNumber, mobCount, playerCount, questType, modifiers,
            null, null, null, null, null);
    }

    public void logWaveStart(UUID sessionId, int waveNumber, int mobCount,
                             int playerCount, @Nullable String questType, String[] modifiers,
                             @Nullable String templateId, @Nullable Integer templateVersion,
                             @Nullable String policyId, @Nullable Integer policyVersion,
                             @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceWave(Instant.now(), sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            waveNumber, "start", mobCount, playerCount, questType, modifiers,
            null, null, null, null);
    }

    /**
     * Log a wave complete event.
     */
    public void logWaveComplete(UUID sessionId, int waveNumber, int mobsKilled,
                                long durationMs, boolean noDamage, double killsPerSecond) {
        logWaveComplete(sessionId, waveNumber, mobsKilled, durationMs, noDamage, killsPerSecond,
            null, null, null, null, null);
    }

    public void logWaveComplete(UUID sessionId, int waveNumber, int mobsKilled,
                                long durationMs, boolean noDamage, double killsPerSecond,
                                @Nullable String templateId, @Nullable Integer templateVersion,
                                @Nullable String policyId, @Nullable Integer policyVersion,
                                @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceWave(Instant.now(), sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            waveNumber, "complete", null, null, null, null,
            mobsKilled, durationMs, noDamage, killsPerSecond);
    }

    /**
     * Log a wave kill event.
     */
    public void logWaveKill(UUID sessionId, int waveNumber, @Nullable String mobType,
                            boolean isElite, @Nullable String killerWeapon, double damageDealt) {
        logWaveKill(sessionId, waveNumber, mobType, isElite, killerWeapon, damageDealt,
            null, null, null, null, null);
    }

    public void logWaveKill(UUID sessionId, int waveNumber, @Nullable String mobType,
                            boolean isElite, @Nullable String killerWeapon, double damageDealt,
                            @Nullable String templateId, @Nullable Integer templateVersion,
                            @Nullable String policyId, @Nullable Integer policyVersion,
                            @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceWaveKill(Instant.now(), sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            waveNumber, mobType, isElite, killerWeapon, damageDealt);
    }

    /**
     * Log a combo/style event.
     */
    public void logComboEvent(UUID playerId, UUID sessionId, @Nullable String eventType,
                              @Nullable String oldRank, @Nullable String newRank,
                              int styleScore, int currentCombo) {
        logComboEvent(playerId, sessionId, eventType, oldRank, newRank, styleScore, currentCombo,
            null, null, null, null, null);
    }

    public void logComboEvent(UUID playerId, UUID sessionId, @Nullable String eventType,
                              @Nullable String oldRank, @Nullable String newRank,
                              int styleScore, int currentCombo,
                              @Nullable String templateId, @Nullable Integer templateVersion,
                              @Nullable String policyId, @Nullable Integer policyVersion,
                              @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceCombo(Instant.now(), playerId, sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            eventType, oldRank, newRank, styleScore, currentCombo,
            null, null, null, null, null, null);
    }

    /**
     * Log a combo milestone.
     */
    public void logComboMilestone(UUID playerId, UUID sessionId, int milestone,
                                  int pointsEarned, int styleEarned, @Nullable String rank) {
        logComboMilestone(playerId, sessionId, milestone, pointsEarned, styleEarned, rank,
            null, null, null, null, null);
    }

    public void logComboMilestone(UUID playerId, UUID sessionId, int milestone,
                                  int pointsEarned, int styleEarned, @Nullable String rank,
                                  @Nullable String templateId, @Nullable Integer templateVersion,
                                  @Nullable String policyId, @Nullable Integer policyVersion,
                                  @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceCombo(Instant.now(), playerId, sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            "milestone", null, rank, null, null,
            milestone, null, null, null, pointsEarned, styleEarned);
    }

    /**
     * Log a combo break.
     */
    public void logComboBreak(UUID playerId, UUID sessionId, int comboLost,
                              @Nullable String previousRank, @Nullable String newRank, double damageTaken) {
        logComboBreak(playerId, sessionId, comboLost, previousRank, newRank, damageTaken,
            null, null, null, null, null);
    }

    public void logComboBreak(UUID playerId, UUID sessionId, int comboLost,
                              @Nullable String previousRank, @Nullable String newRank, double damageTaken,
                              @Nullable String templateId, @Nullable Integer templateVersion,
                              @Nullable String policyId, @Nullable Integer policyVersion,
                              @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceCombo(Instant.now(), playerId, sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            "break", previousRank, newRank, null, null,
            null, comboLost, damageTaken, null, null, null);
    }

    /**
     * Log a perk selection.
     */
    public void logPerkSelected(UUID playerId, UUID sessionId, @Nullable String perkId,
                                @Nullable String perkName, @Nullable String tier, @Nullable String category,
                                int stackCount, int totalPerks, int waveNumber) {
        logPerkSelected(playerId, sessionId, perkId, perkName, tier, category,
            stackCount, totalPerks, waveNumber, null, null, null, null, null);
    }

    public void logPerkSelected(UUID playerId, UUID sessionId, @Nullable String perkId,
                                @Nullable String perkName, @Nullable String tier, @Nullable String category,
                                int stackCount, int totalPerks, int waveNumber,
                                @Nullable String templateId, @Nullable Integer templateVersion,
                                @Nullable String policyId, @Nullable Integer policyVersion,
                                @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEndurancePerk(Instant.now(), playerId, sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            "selected", perkId, perkName, tier, category,
            stackCount, totalPerks, waveNumber, null);
    }

    /**
     * Log perk choices offered.
     */
    public void logPerkChoices(UUID playerId, UUID sessionId, int waveNumber,
                               @Nullable String choicesJson) {
        logPerkChoices(playerId, sessionId, waveNumber, choicesJson,
            null, null, null, null, null);
    }

    public void logPerkChoices(UUID playerId, UUID sessionId, int waveNumber,
                               @Nullable String choicesJson,
                               @Nullable String templateId, @Nullable Integer templateVersion,
                               @Nullable String policyId, @Nullable Integer policyVersion,
                               @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEndurancePerk(Instant.now(), playerId, sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            "choices", null, null, null, null,
            null, null, waveNumber, choicesJson);
    }

    /**
     * Log currency earned.
     */
    public void logCurrencyEarned(UUID playerId, UUID sessionId, @Nullable String currency,
                                  int amount, @Nullable String source) {
        logCurrencyEarned(playerId, sessionId, currency, amount, source,
            null, null, null, null, null);
    }

    public void logCurrencyEarned(UUID playerId, UUID sessionId, @Nullable String currency,
                                  int amount, @Nullable String source,
                                  @Nullable String templateId, @Nullable Integer templateVersion,
                                  @Nullable String policyId, @Nullable Integer policyVersion,
                                  @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceReward(Instant.now(), playerId, sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            "currency", currency, amount, source,
            null, null, null, null, null, null, null);
    }

    /**
     * Log loot drop.
     */
    public void logLootDrop(UUID playerId, UUID sessionId, @Nullable String itemId,
                            int itemCount, @Nullable String lootTier) {
        logLootDrop(playerId, sessionId, itemId, itemCount, lootTier,
            null, null, null, null, null);
    }

    public void logLootDrop(UUID playerId, UUID sessionId, @Nullable String itemId,
                            int itemCount, @Nullable String lootTier,
                            @Nullable String templateId, @Nullable Integer templateVersion,
                            @Nullable String policyId, @Nullable Integer policyVersion,
                            @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceReward(Instant.now(), playerId, sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            "loot", null, null, null,
            itemId, itemCount, lootTier, null, null, null, null);
    }

    /**
     * Log achievement unlocked.
     */
    public void logAchievementUnlocked(UUID playerId, UUID sessionId,
                                       String achievementId, String achievementName,
                                       String rewardCurrency, int rewardAmount,
                                       @Nullable String templateId, @Nullable Integer templateVersion,
                                       @Nullable String policyId, @Nullable Integer policyVersion,
                                       @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceReward(Instant.now(), playerId, sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            "achievement_unlocked", rewardCurrency, rewardAmount, null,
            null, null, null, achievementId, achievementName, null, null);
    }

    // ============================================
    // SESSION EVENTS
    // ============================================

    /**
     * Log quest session start.
     */
    public void logSessionStart(UUID sessionId, UUID playerId, @Nullable String playerName,
                                 @Nullable String questName, @Nullable String questType, int totalWaves,
                                 boolean isEndless, int playerCount,
                                 @Nullable String templateId, @Nullable Integer templateVersion,
                                 @Nullable String policyId, @Nullable Integer policyVersion,
                                 @Nullable UUID instanceId, @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;
        if (!shouldLogSessionStart(sessionId, playerName, questName, questType)) return;

        batchWriter.queueEnduranceSession(sessionId, playerId, playerName,
            questName, questType, totalWaves, isEndless, playerCount,
            Instant.now(), null, null, null, null, null, null, null, null, null, null,
            templateId, templateVersion, policyId, policyVersion, instanceId, arenaId,
            null, null, null, null, null, null, null);
    }

    /**
     * Log quest session end.
     */
    public void logSessionEnd(UUID sessionId, UUID playerId, @Nullable String playerName,
                               @Nullable String questName, @Nullable String questType, int totalWaves,
                               boolean isEndless, int playerCount, Instant startTs,
                               @Nullable String outcome, int wavesCompleted, int totalKills,
                               double damageDealt, double damageTaken,
                               int tokensEarned, int prestigeEarned,
                               int bloodGemsEarned, int noDamageWaves,
                               @Nullable String templateId, @Nullable Integer templateVersion,
                               @Nullable String policyId, @Nullable Integer policyVersion,
                               @Nullable UUID instanceId, @Nullable UUID arenaId,
                               @Nullable Integer countdownStarted, @Nullable Integer countdownCancelled,
                               @Nullable Integer giveupDuringRespawn,
                               @Nullable Integer inventoryRestoreSuccess, @Nullable Integer inventoryRestoreFallback,
                               @Nullable Integer externalDeathRespawnCount, @Nullable Integer waveBlockedDetected) {
        if (!enabled || batchWriter == null) return;
        if (!shouldLogSessionEnd(sessionId, playerName, questName, questType)) return;

        batchWriter.queueEnduranceSession(sessionId, playerId, playerName,
            questName, questType, totalWaves, isEndless, playerCount,
            startTs, Instant.now(), outcome, wavesCompleted, totalKills,
            damageDealt, damageTaken, tokensEarned, prestigeEarned,
            bloodGemsEarned, noDamageWaves,
            templateId, templateVersion, policyId, policyVersion, instanceId, arenaId,
            countdownStarted, countdownCancelled, giveupDuringRespawn,
            inventoryRestoreSuccess, inventoryRestoreFallback,
            externalDeathRespawnCount, waveBlockedDetected);
    }

    /**
     * Log quest performance summary (TTK/KPS/DTPS).
     */
    public void logEndurancePerformance(UUID sessionId, UUID playerId, String questType,
                                        long durationMs, int wavesCompleted, int kills,
                                        double damageDealt, double damageTaken,
                                        double avgTtkMs, double kps, double dtps, double dps,
                                        @Nullable String templateId, @Nullable Integer templateVersion,
                                        @Nullable String policyId, @Nullable Integer policyVersion,
                                        @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEndurancePerformance(Instant.now(), sessionId, playerId,
            questType, durationMs, wavesCompleted, kills,
            damageDealt, damageTaken, avgTtkMs, kps, dtps, dps,
            templateId, templateVersion, policyId, policyVersion, arenaId);
    }

    private boolean shouldLogSessionStart(UUID sessionId, @Nullable String playerName,
                                          @Nullable String questName, @Nullable String questType) {
        long now = System.currentTimeMillis();
        SessionLogState state = sessionLogState.computeIfAbsent(sessionId, id -> new SessionLogState());
        boolean shouldLog;
        synchronized (state) {
            boolean hasNewInfo = updateSessionInfo(state, playerName, questName, questType);
            state.lastUpdatedMs = now;
            shouldLog = !state.startLogged || hasNewInfo;
            state.startLogged = true;
        }
        pruneSessionLogState(now);
        return shouldLog;
    }

    private boolean shouldLogSessionEnd(UUID sessionId, @Nullable String playerName,
                                        @Nullable String questName, @Nullable String questType) {
        long now = System.currentTimeMillis();
        SessionLogState state = sessionLogState.computeIfAbsent(sessionId, id -> new SessionLogState());
        boolean shouldLog;
        synchronized (state) {
            boolean hasNewInfo = updateSessionInfo(state, playerName, questName, questType);
            state.lastUpdatedMs = now;
            shouldLog = !state.endLogged || hasNewInfo;
            state.endLogged = true;
        }
        pruneSessionLogState(now);
        return shouldLog;
    }

    private boolean updateSessionInfo(SessionLogState state, @Nullable String playerName,
                                      @Nullable String questName, @Nullable String questType) {
        boolean hasNewInfo = false;
        if (playerName != null && !playerName.isBlank() && !state.hasPlayerName) {
            state.hasPlayerName = true;
            hasNewInfo = true;
        }
        if (questName != null && !questName.isBlank() && !state.hasQuestName) {
            state.hasQuestName = true;
            hasNewInfo = true;
        }
        if (questType != null && !questType.isBlank() && !state.hasQuestType) {
            state.hasQuestType = true;
            hasNewInfo = true;
        }
        return hasNewInfo;
    }

    private void pruneSessionLogState(long nowMs) {
        if (sessionLogState.size() < 500) {
            return;
        }
        for (var entry : sessionLogState.entrySet()) {
            SessionLogState state = entry.getValue();
            if (state == null) {
                sessionLogState.remove(entry.getKey());
                continue;
            }
            if (state.endLogged && nowMs - state.lastUpdatedMs > SESSION_LOG_TTL_MS) {
                sessionLogState.remove(entry.getKey(), state);
            }
        }
    }

    // ============================================
    // MUTATOR EVENTS
    // ============================================

    /**
     * Log mutators assigned to quest.
     */
    public void logMutatorsAssigned(UUID sessionId, String mutatorsJson,
                                     double rewardMultiplier, int mutatorCount) {
        logMutatorsAssigned(sessionId, mutatorsJson, rewardMultiplier, mutatorCount,
            null, null, null, null, null);
    }

    public void logMutatorsAssigned(UUID sessionId, String mutatorsJson,
                                    double rewardMultiplier, int mutatorCount,
                                    @Nullable String templateId, @Nullable Integer templateVersion,
                                    @Nullable String policyId, @Nullable Integer policyVersion,
                                    @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceMutator(Instant.now(), sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            "assigned", null, null, null, rewardMultiplier, mutatorCount, mutatorsJson);
    }

    /**
     * Log mutator added mid-quest.
     */
    public void logMutatorAdded(UUID sessionId, String mutatorId, String category, int waveNumber) {
        logMutatorAdded(sessionId, mutatorId, category, waveNumber,
            null, null, null, null, null);
    }

    public void logMutatorAdded(UUID sessionId, String mutatorId, String category, int waveNumber,
                                @Nullable String templateId, @Nullable Integer templateVersion,
                                @Nullable String policyId, @Nullable Integer policyVersion,
                                @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceMutator(Instant.now(), sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            "added", mutatorId, category, waveNumber, null, null, null);
    }

    // ============================================
    // PARTY EVENTS
    // ============================================

    /**
     * Log party created.
     */
    public void logPartyCreated(UUID partyId, UUID leaderId, String leaderName, String questType) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceParty(Instant.now(), partyId, "created",
            leaderId, leaderName, null, null, questType, 1, null, null);
    }

    /**
     * Log party member joined.
     */
    public void logPartyJoin(UUID partyId, UUID memberId, String memberName, int partySize) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceParty(Instant.now(), partyId, "join",
            null, null, memberId, memberName, null, partySize, null, null);
    }

    /**
     * Log party member left.
     */
    public void logPartyLeave(UUID partyId, UUID memberId, String reason, int partySize) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceParty(Instant.now(), partyId, "leave",
            null, null, memberId, null, null, partySize, reason, null);
    }

    /**
     * Log party disbanded.
     */
    public void logPartyDisbanded(UUID partyId, int memberCount, String reason) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceParty(Instant.now(), partyId, "disbanded",
            null, null, null, null, null, memberCount, reason, null);
    }

    /**
     * Log invite sent.
     */
    public void logInviteSent(UUID partyId, UUID senderId, UUID targetId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceParty(Instant.now(), partyId, "invite_sent",
            senderId, null, targetId, null, null, null, null, null);
    }

    /**
     * Log invite response.
     */
    public void logInviteResponse(UUID partyId, UUID targetId, boolean accepted) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceParty(Instant.now(), partyId, "invite_response",
            null, null, targetId, null, null, null, null, accepted);
    }

    // ============================================
    // BOSS EVENTS
    // ============================================

    /**
     * Log boss wave start.
     */
    public void logBossWaveStart(UUID sessionId, int waveNumber, String bossArchetype,
                                  double bossMaxHealth, int playerCount) {
        logBossWaveStart(sessionId, waveNumber, bossArchetype, bossMaxHealth, playerCount,
            null, null, null, null, null);
    }

    public void logBossWaveStart(UUID sessionId, int waveNumber, String bossArchetype,
                                  double bossMaxHealth, int playerCount,
                                  @Nullable String templateId, @Nullable Integer templateVersion,
                                  @Nullable String policyId, @Nullable Integer policyVersion,
                                  @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceBoss(Instant.now(), sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            "wave_start", waveNumber, bossArchetype, bossMaxHealth, playerCount,
            null, null, null, null, null, null);
    }

    /**
     * Log boss ability used.
     */
    public void logBossAbility(UUID sessionId, String bossArchetype, String abilityName,
                                int playersHit, double damageDealt) {
        logBossAbility(sessionId, bossArchetype, abilityName, playersHit, damageDealt,
            null, null, null, null, null);
    }

    public void logBossAbility(UUID sessionId, String bossArchetype, String abilityName,
                                int playersHit, double damageDealt,
                                @Nullable String templateId, @Nullable Integer templateVersion,
                                @Nullable String policyId, @Nullable Integer policyVersion,
                                @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceBoss(Instant.now(), sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            "ability", null, bossArchetype, null, null,
            abilityName, playersHit, damageDealt, null, null, null);
    }

    /**
     * Log boss defeated.
     */
    public void logBossDefeated(UUID sessionId, int waveNumber, String bossArchetype,
                                 long fightDurationMs, int bonusPoints, double damageDealtToBoss) {
        logBossDefeated(sessionId, waveNumber, bossArchetype, fightDurationMs, bonusPoints,
            damageDealtToBoss, null, null, null, null, null);
    }

    public void logBossDefeated(UUID sessionId, int waveNumber, String bossArchetype,
                                 long fightDurationMs, int bonusPoints, double damageDealtToBoss,
                                 @Nullable String templateId, @Nullable Integer templateVersion,
                                 @Nullable String policyId, @Nullable Integer policyVersion,
                                 @Nullable UUID arenaId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEnduranceBoss(Instant.now(), sessionId,
            templateId, templateVersion, policyId, policyVersion, arenaId,
            "defeated", waveNumber, bossArchetype, null, null,
            null, null, null, fightDurationMs, bonusPoints, damageDealtToBoss);
    }

    // ============================================
    // PLAYER EVENTS
    // ============================================

    /**
     * Log a player attribute snapshot.
     */
    public void logPlayerSnapshot(UUID playerId, String playerName, @Nullable String trigger,
                                  double healthHp, double maxHealthHp, int healthHearts, double absorptionHp,
                                  int hungerLevel, double saturation, double exhaustion,
                                  double movementSpeed, double velocityX, double velocityY, double velocityZ,
                                  int movementFlags, double meleeDamageMult, double meleeReduction,
                                  double magicDamageMult, double magicReduction,
                                  double rangedDamageMult, double rangedReduction,
                                  double armorValue, double armorToughness, double knockbackResistance,
                                  double totalDamageReduction, double reach,
                                  double hitboxWidth, double hitboxHeight,
                                  @Nullable Double pehkuiScale, @Nullable Double pehkuiHitboxScale,
                                  double stamina, double maxStamina,
                                  double dashCooldown, double dodgeCooldown, int abilityFlags,
                                  int currentCombo, @Nullable String styleRank, int styleScore,
                                  double x, double y, double z, @Nullable String dimension) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queuePlayerSnapshot(Instant.now(), playerId, playerName, trigger,
            healthHp, maxHealthHp, healthHearts, absorptionHp,
            hungerLevel, saturation, exhaustion,
            movementSpeed, velocityX, velocityY, velocityZ, movementFlags,
            meleeDamageMult, meleeReduction, magicDamageMult, magicReduction,
            rangedDamageMult, rangedReduction, armorValue, armorToughness,
            knockbackResistance, totalDamageReduction, reach, hitboxWidth, hitboxHeight,
            pehkuiScale, pehkuiHitboxScale, stamina, maxStamina,
            dashCooldown, dodgeCooldown, abilityFlags,
            currentCombo, styleRank, styleScore, x, y, z, dimension);
    }

    /**
     * Log an ability usage event.
     */
    public void logAbility(UUID playerId, @Nullable String abilityType, @Nullable Boolean success,
                           @Nullable Integer result, @Nullable Double staminaBefore, @Nullable Double staminaAfter,
                           @Nullable Double staminaCost, @Nullable Double damageNegated, @Nullable String damageSource,
                           @Nullable String context, @Nullable Long regenTimeMs) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queuePlayerAbility(Instant.now(), playerId, abilityType,
            success, result, staminaBefore, staminaAfter, staminaCost,
            damageNegated, damageSource, context, regenTimeMs);
    }

    /**
     * Log a player attribute change event.
     */
    public void logPlayerAttributeChange(UUID playerId, String attributeName,
                                          double oldValue, double newValue) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queuePlayerAttributeChange(Instant.now(), playerId, attributeName,
            oldValue, newValue);
    }

    // ============================================
    // SPATIAL EVENTS
    // ============================================

    /**
     * Log a heatmap point.
     */
    public void logHeatmap(@Nullable String heatmapType, @Nullable String room, int x, int y, int z, int count) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueSpatialHeatmap(Instant.now(), heatmapType, room, x, y, z, count);
    }

    /**
     * Log a template-scoped spatial event for arena heatmaps.
     */
    public void logArenaSpatialEvent(@Nullable String templateId, @Nullable Integer templateVersion,
                                     @Nullable UUID sessionId, String eventType,
                                     int gridX, int gridZ, double worldX, double worldY, double worldZ,
                                     @Nullable UUID playerId) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueArenaSpatialEvent(Instant.now(), templateId, templateVersion, sessionId, eventType,
            gridX, gridZ, worldX, worldY, worldZ, playerId);
    }

    /**
     * Log a spatial alert.
     */
    public void logAlert(String alertType, @Nullable String playerName, @Nullable String entityName,
                         @Nullable String entityType, @Nullable String room,
                         double x, double y, double z, @Nullable String extraDataJson) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueSpatialAlert(Instant.now(), alertType, playerName,
            entityName, entityType, room, x, y, z, extraDataJson);
    }

    /**
     * Log a room transition.
     */
    public void logRoomTransition(UUID playerId, @Nullable String playerName, @Nullable String room) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueRoomTransition(Instant.now(), playerId, playerName, room);
    }

    // ============================================
    // PERFORMANCE EVENTS
    // ============================================

    /**
     * Log a performance sample.
     */
    public void logPerformance(double mspt, double tps) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queuePerformanceSample(Instant.now(), mspt, tps);
    }

    // ============================================
    // ECONOMY LOGGING (P1)
    // ============================================

    /**
     * Log a mob kill event.
     * Maps 1:1 to economy_mob_kills table.
     */
    public void logMobKill(String mobType, int totalKills, boolean hadLoot) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEconomyMobKill(Instant.now(), mobType, totalKills, hadLoot);
    }

    /**
     * Log a mob drop event.
     * Maps 1:1 to economy_mob_drops table.
     */
    public void logMobDrop(String mobType, String room, String itemId, int itemCount, int x, int y, int z) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEconomyMobDrop(Instant.now(), mobType, room, itemId, itemCount, x, y, z);
    }

    /**
     * Log an item pickup event.
     * Maps 1:1 to economy_item_pickups table.
     */
    public void logItemPickup(UUID playerId, String playerName, String room, String itemId, int itemCount, int x, int y, int z) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEconomyItemPickup(Instant.now(), playerId, playerName, room, itemId, itemCount, x, y, z);
    }

    /**
     * Log an item usage event (consumed, discarded, etc.).
     * Maps 1:1 to economy_item_usage table.
     */
    public void logItemUsage(UUID playerId, String playerName, String eventType, String itemId, int itemCount, String useType) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueEconomyItemUsage(Instant.now(), playerId, playerName, eventType, itemId, itemCount, useType);
    }

    // ============================================
    // PROGRESSION LOGGING (P1)
    // ============================================

    /**
     * Log a block break/place event.
     * Maps 1:1 to progression_blocks table.
     * HIGH volume - assigned NORMAL priority.
     */
    public void logBlock(UUID playerId, String playerName, String worldId, String room,
                         String eventType, String blockId, int x, int y, int z) {
        if (!enabled || batchWriter == null) return;
        if (blockId == null || blockId.isEmpty()) return; // Guard: no empty block_id

        batchWriter.queueProgressionBlock(Instant.now(), playerId, playerName, worldId, room,
            eventType, blockId, x, y, z);
    }

    /**
     * Log an XP gain event with batching.
     * Aggregates XP events per player over 1s intervals to reduce spam.
     * Maps to progression_xp table.
     */
    public void logXp(UUID playerId, String playerName, String worldId, String room,
                      String eventType, int xpAmount, int oldLevel, int newLevel,
                      int x, int y, int z) {
        if (!enabled || batchWriter == null) return;
        if (xpAmount == 0 && oldLevel == newLevel) return; // Guard: no-op events

        long now = System.currentTimeMillis();

        // Level change events are always logged immediately
        if (!"pickup".equals(eventType) || newLevel != oldLevel) {
            batchWriter.queueProgressionXp(Instant.now(), playerId, playerName, worldId, room,
                eventType, xpAmount, oldLevel, newLevel, x, y, z);
            return;
        }

        // Batch XP pickup events
        XpBatch batch = xpBatches.computeIfAbsent(playerId, k -> new XpBatch());
        synchronized (batch) {
            batch.accumulatedXp += xpAmount;
            batch.lastLevel = newLevel;
            batch.lastRoom = room;
            batch.lastWorldId = worldId;
            batch.lastX = x;
            batch.lastY = y;
            batch.lastZ = z;

            // Flush if interval elapsed
            if (now - batch.lastFlush >= XP_BATCH_INTERVAL_MS && batch.accumulatedXp > 0) {
                batchWriter.queueProgressionXp(Instant.now(), playerId, playerName,
                    batch.lastWorldId, batch.lastRoom, "pickup_batch",
                    batch.accumulatedXp, batch.startLevel, batch.lastLevel,
                    batch.lastX, batch.lastY, batch.lastZ);
                batch.reset(now, newLevel);
            }
        }
    }

    /**
     * Log an advancement earned event.
     * Deduplicates by (player_uuid, advancement_id) per session.
     * Maps 1:1 to progression_advancements table.
     */
    public void logAdvancement(UUID playerId, String playerName, String worldId, String room,
                               String advancementId, String title, int x, int y, int z) {
        if (!enabled || batchWriter == null) return;

        // Dedup: only log once per session per player+advancement
        String dedupKey = playerId + ":" + advancementId;
        if (!loggedAdvancements.add(dedupKey)) {
            return; // Already logged this session
        }

        batchWriter.queueProgressionAdvancement(Instant.now(), playerId, playerName, worldId, room,
            advancementId, title, x, y, z);
    }

    /**
     * Log a dimension change event.
     * Deduplicates rapid changes (TTL 5s per player).
     * Maps 1:1 to progression_dimensions table.
     */
    public void logDimensionChange(UUID playerId, String playerName, String worldId,
                                   String fromDimension, String toDimension,
                                   int x, int y, int z) {
        if (!enabled || batchWriter == null) return;
        if (fromDimension.equals(toDimension)) return; // Guard: no actual change

        long now = System.currentTimeMillis();

        // Dedup: throttle to one event per 5s per player
        Long lastChange = lastDimensionChange.get(playerId);
        if (lastChange != null && now - lastChange < DIMENSION_CHANGE_TTL_MS) {
            return; // Too soon, skip
        }
        lastDimensionChange.put(playerId, now);

        batchWriter.queueProgressionDimension(Instant.now(), playerId, playerName, worldId,
            fromDimension, toDimension, x, y, z);
    }

    /**
     * Log a villager trade event.
     * Maps 1:1 to progression_trades table.
     */
    public void logTrade(UUID playerId, String playerName, String worldId, String room,
                         String villagerType, String profession,
                         String itemBought, int itemBoughtCount,
                         String itemSold, int itemSoldCount,
                         int x, int y, int z) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueProgressionTrade(Instant.now(), playerId, playerName, worldId, room,
            villagerType, profession, itemBought, itemBoughtCount, itemSold, itemSoldCount,
            x, y, z);
    }

    /**
     * Log a fishing catch event.
     * Maps 1:1 to progression_fishing table.
     */
    public void logFishing(UUID playerId, String playerName, String worldId, String room,
                           String itemId, int itemCount, int x, int y, int z) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueProgressionFishing(Instant.now(), playerId, playerName, worldId, room,
            itemId, itemCount, x, y, z);
    }

    // ============================================
    // DUNGEON LOGGING (P2-B)
    // ============================================

    /**
     * Log a dungeon run completion event.
     * Maps 1:1 to dungeon_runs table.
     */
    public void logDungeonRun(Instant startTs, Instant endTs, long durationMs,
                               String playerId, String playerName, String dungeonId,
                               String outcome, int roomsVisited, String roomsList,
                               int deaths, int kills, String enemiesKilled,
                               float damageDealt, float damageTaken,
                               int rewardCount, String lootCollected, String lastDeathRoom) {
        if (!enabled || batchWriter == null) return;

        batchWriter.queueDungeonRun(startTs, endTs, durationMs, playerId, playerName, dungeonId,
            outcome, roomsVisited, roomsList, deaths, kills, enemiesKilled,
            damageDealt, damageTaken, rewardCount, lootCollected, lastDeathRoom);
    }

    /**
     * Clear dedup state for a player (called on logout).
     */
    public void clearPlayerDedupState(UUID playerId) {
        xpBatches.remove(playerId);
        lastDimensionChange.remove(playerId);
        // Remove all advancement entries for this player
        loggedAdvancements.removeIf(key -> key.startsWith(playerId.toString() + ":"));
    }

    // ============================================
    // HELPER CLASSES
    // ============================================

    /**
     * Batching state for XP events per player.
     */
    private static class XpBatch {
        int accumulatedXp = 0;
        int startLevel = 0;
        int lastLevel = 0;
        String lastRoom = "unknown";
        String lastWorldId = "";
        int lastX, lastY, lastZ;
        long lastFlush = System.currentTimeMillis();

        void reset(long now, int currentLevel) {
            accumulatedXp = 0;
            startLevel = currentLevel;
            lastLevel = currentLevel;
            lastFlush = now;
        }
    }

    private static class SessionLogState {
        boolean startLogged;
        boolean endLogged;
        boolean hasPlayerName;
        boolean hasQuestName;
        boolean hasQuestType;
        long lastUpdatedMs;
    }
}
