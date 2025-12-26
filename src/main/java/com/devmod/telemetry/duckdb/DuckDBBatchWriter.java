package com.devmod.telemetry.duckdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronous batch writer for DuckDB telemetry.
 *
 * <p><b>Performance optimizations:</b>
 * <ul>
 *   <li>Non-blocking queue per table type (~0.1ms enqueue overhead)</li>
 *   <li>Batch inserts (100 rows per batch for optimal throughput)</li>
 *   <li>Scheduled flush every 5 seconds or when batch size reached</li>
 *   <li>Prepared statement caching for reuse</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b>
 * This class is thread-safe. Multiple threads may safely call queue*() methods concurrently.
 * <ul>
 *   <li>All queue*() methods are non-blocking and safe for game thread use</li>
 *   <li>Internal queues use {@link java.util.concurrent.ConcurrentHashMap} and
 *       {@link java.util.concurrent.LinkedBlockingQueue}</li>
 *   <li>Statistics counters use {@link java.util.concurrent.atomic.AtomicLong} /
 *       {@link java.util.concurrent.atomic.AtomicInteger}</li>
 *   <li>Flush operations are serialized via scheduled executor (single writer thread)</li>
 *   <li>Shutdown performs graceful flush before closing connections</li>
 * </ul>
 *
 * <p><b>Thread model:</b>
 * <ul>
 *   <li>Game thread(s): call queue*() methods (non-blocking)</li>
 *   <li>Writer thread: single scheduled thread flushes batches to DuckDB</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>
 *   writer.queueCombatHit(ts, room, attacker, target, damage, ...);
 *   // Later, on shutdown:
 *   writer.shutdown();
 * </pre>
 */
public class DuckDBBatchWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DuckDBBatchWriter.class);

    private final DuckDBConnectionManager connectionManager;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> flushTask;

    // Per-table queues for efficient batching
    private final Map<String, BlockingQueue<Object[]>> tableQueues = new ConcurrentHashMap<>();

    // Prepared statement cache
    private final Map<String, String> insertSqlCache = new ConcurrentHashMap<>();

    // Statistics - basic
    private final AtomicLong totalInserts = new AtomicLong(0);
    private final AtomicLong totalBatches = new AtomicLong(0);
    private final AtomicLong droppedInserts = new AtomicLong(0);
    private final AtomicLong consecutiveErrors = new AtomicLong(0);

    // Statistics - detailed (for monitoring/debugging)
    private final AtomicLong droppedByPriorityLow = new AtomicLong(0);
    private final AtomicLong droppedByPriorityNormal = new AtomicLong(0);
    private final AtomicLong droppedByQueueFull = new AtomicLong(0);
    private final AtomicLong flushLatencyTotalMs = new AtomicLong(0);
    private final AtomicLong flushCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);

    private volatile boolean running = false;

    // Flush lock: DuckDB is single-writer, prevent concurrent flushes
    private final Object flushLock = new Object();

    // Circuit breaker: after N consecutive errors, trigger fallback
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    private volatile boolean circuitBroken = false;

    // Backpressure: track queue pressure level
    private final AtomicInteger pressureLevel = new AtomicInteger(0); // 0=normal, 1=elevated, 2=critical
    private static final int PRESSURE_THRESHOLD_ELEVATED = (int)(DuckDBConfig.QUEUE_CAPACITY * 0.5);
    private static final int PRESSURE_THRESHOLD_CRITICAL = (int)(DuckDBConfig.QUEUE_CAPACITY * 0.8);

    /**
     * Event priority for backpressure management.
     * CRITICAL events are never dropped, LOW events are dropped first.
     */
    public enum EventPriority {
        CRITICAL,  // hit, death, wave_end, session_end - NEVER drop
        HIGH,      // spawn, heal, perk_selected, combo_break
        NORMAL,    // ability, alert, room_transition
        LOW        // movement sampling, attribute snapshots - drop first
    }

    // Tables by priority (for backpressure decisions)
    private static final Map<String, EventPriority> TABLE_PRIORITY = Map.ofEntries(
        // CRITICAL - Never drop
        Map.entry("combat_hits", EventPriority.CRITICAL),
        Map.entry("combat_deaths", EventPriority.CRITICAL),
        Map.entry("combat_fights", EventPriority.CRITICAL),
        Map.entry("endurance_waves", EventPriority.CRITICAL),
        Map.entry("endurance_rewards", EventPriority.CRITICAL),
        Map.entry("endurance_sessions", EventPriority.CRITICAL),
        Map.entry("performance_samples", EventPriority.CRITICAL),
        Map.entry("endurance_performance", EventPriority.CRITICAL),

        // HIGH - Drop only under extreme pressure
        Map.entry("combat_spawns", EventPriority.HIGH),
        Map.entry("combat_heals", EventPriority.HIGH),
        Map.entry("endurance_combos", EventPriority.HIGH),
        Map.entry("endurance_perks", EventPriority.HIGH),
        Map.entry("endurance_wave_kills", EventPriority.HIGH),
        Map.entry("endurance_bosses", EventPriority.HIGH),
        Map.entry("endurance_parties", EventPriority.HIGH),

        // NORMAL - Drop under elevated pressure
        Map.entry("player_abilities", EventPriority.NORMAL),
        Map.entry("spatial_alerts", EventPriority.NORMAL),
        Map.entry("spatial_room_transitions", EventPriority.NORMAL),
        Map.entry("endurance_mutators", EventPriority.NORMAL),
        Map.entry("arena_spatial_events", EventPriority.NORMAL),

        // ARENA (HIGH priority for builds/usage since they're session-level events)
        Map.entry("arena_template_builds", EventPriority.HIGH),
        Map.entry("arena_template_usage", EventPriority.HIGH),

        // LOW - Drop first under any pressure
        Map.entry("player_snapshots", EventPriority.LOW),
        Map.entry("player_attribute_changes", EventPriority.LOW),
        Map.entry("spatial_heatmaps", EventPriority.LOW),

        // ECONOMY (P1) - High priority for kills/drops, normal for pickups/usage
        Map.entry("economy_mob_kills", EventPriority.HIGH),
        Map.entry("economy_mob_drops", EventPriority.HIGH),
        Map.entry("economy_item_pickups", EventPriority.NORMAL),
        Map.entry("economy_item_usage", EventPriority.NORMAL),

        // PROGRESSION (P1) - Advancements/dimensions are high, blocks/xp are low (high volume)
        Map.entry("progression_blocks", EventPriority.LOW),
        Map.entry("progression_xp", EventPriority.LOW),
        Map.entry("progression_advancements", EventPriority.HIGH),
        Map.entry("progression_dimensions", EventPriority.HIGH),
        Map.entry("progression_trades", EventPriority.NORMAL),
        Map.entry("progression_fishing", EventPriority.NORMAL),

        // DUNGEON (P2-B) - Dungeon run outcomes are high priority (session-level data)
        Map.entry("dungeon_runs", EventPriority.HIGH)
    );

    public DuckDBBatchWriter(DuckDBConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DuckDB-BatchWriter");
            t.setDaemon(true);
            return t;
        });

        // Initialize SQL templates
        initializeSqlTemplates();
    }

    /**
     * Start the batch writer (schedules periodic flushes).
     */
    public void start() {
        if (running) return;
        running = true;

        // Schedule periodic flush
        flushTask = scheduler.scheduleAtFixedRate(
            this::flushAllBatches,
            DuckDBConfig.FLUSH_INTERVAL_MS,
            DuckDBConfig.FLUSH_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        LOGGER.info("[DuckDB] BatchWriter started (batch={}, interval={}ms)",
            DuckDBConfig.BATCH_SIZE, DuckDBConfig.FLUSH_INTERVAL_MS);
    }

    /**
     * Stop the batch writer and flush remaining data.
     */
    public void shutdown() {
        LOGGER.info("[DuckDB] Shutting down BatchWriter...");
        running = false;

        if (flushTask != null) {
            flushTask.cancel(false);
        }

        // Final flush
        flushAllBatches();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("[DuckDB] BatchWriter did not terminate in time");
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("[DuckDB] BatchWriter shutdown complete. Total inserts: {}, batches: {}, dropped: {}",
            totalInserts.get(), totalBatches.get(), droppedInserts.get());
    }

    // ============================================
    // QUEUE METHODS (called from game thread)
    // ============================================

    /**
     * Queue a combat hit event.
     */
    public void queueCombatHit(Instant ts, String room, String world,
                               String attackerName, String attackerType,
                               String targetName, String targetType,
                               double damage, String damageType,
                               Double hpBefore, Double hpAfter,
                               String bodyPart, Double distance,
                               Double armorPenBonus, boolean isMiss,
                               boolean isHazard, String hazardType,
                               String attackerStateJson, String targetStateJson) {
        queueCombatHit(ts, room, world, null, null, null, null, null, null,
            attackerName, attackerType, targetName, targetType,
            damage, damageType, hpBefore, hpAfter, bodyPart, distance,
            armorPenBonus, isMiss, isHazard, hazardType, attackerStateJson, targetStateJson);
    }

    /**
     * Queue a combat hit event with arena context.
     */
    public void queueCombatHit(Instant ts, String room, String world,
                               String templateId, Integer templateVersion,
                               String policyId, Integer policyVersion,
                               UUID arenaId, UUID sessionId,
                               String attackerName, String attackerType,
                               String targetName, String targetType,
                               double damage, String damageType,
                               Double hpBefore, Double hpAfter,
                               String bodyPart, Double distance,
                               Double armorPenBonus, boolean isMiss,
                               boolean isHazard, String hazardType,
                               String attackerStateJson, String targetStateJson) {
        queueInsert("combat_hits", new Object[] {
            ts, room, world, templateId, templateVersion, policyId, policyVersion, arenaId, sessionId,
            attackerName, attackerType, targetName, targetType,
            damage, damageType, hpBefore, hpAfter, bodyPart, distance,
            armorPenBonus, isMiss, isHazard, hazardType, attackerStateJson, targetStateJson
        });
    }

    /**
     * Queue a combat death event.
     */
    public void queueCombatDeath(Instant ts, String room, String world,
                                  String targetName, String targetType,
                                  String cause, Long ttkFirstHitMs, Long ttkSpawnMs) {
        queueCombatDeath(ts, room, world, null, null, null, null, null, null,
            targetName, targetType, cause, ttkFirstHitMs, ttkSpawnMs);
    }

    /**
     * Queue a combat death event with arena context.
     */
    public void queueCombatDeath(Instant ts, String room, String world,
                                  String templateId, Integer templateVersion,
                                  String policyId, Integer policyVersion,
                                  UUID arenaId, UUID sessionId,
                                  String targetName, String targetType,
                                  String cause, Long ttkFirstHitMs, Long ttkSpawnMs) {
        queueInsert("combat_deaths", new Object[] {
            ts, room, world, templateId, templateVersion, policyId, policyVersion, arenaId, sessionId,
            targetName, targetType, cause, ttkFirstHitMs, ttkSpawnMs
        });
    }

    /**
     * Queue a combat heal event.
     */
    public void queueCombatHeal(Instant ts, String room, String world,
                                 String targetName, String targetType,
                                 double healAmount, Double hpBefore, Double hpAfter,
                                 String source) {
        queueCombatHeal(ts, room, world, null, null, null, null, null, null,
            targetName, targetType, healAmount, hpBefore, hpAfter, source);
    }

    /**
     * Queue a combat heal event with arena context.
     */
    public void queueCombatHeal(Instant ts, String room, String world,
                                 String templateId, Integer templateVersion,
                                 String policyId, Integer policyVersion,
                                 UUID arenaId, UUID sessionId,
                                 String targetName, String targetType,
                                 double healAmount, Double hpBefore, Double hpAfter,
                                 String source) {
        queueInsert("combat_heals", new Object[] {
            ts, room, world, templateId, templateVersion, policyId, policyVersion, arenaId, sessionId,
            targetName, targetType, healAmount, hpBefore, hpAfter, source
        });
    }

    /**
     * Queue a combat spawn event.
     */
    public void queueCombatSpawn(Instant ts, String room, String world,
                                  String entityName, String entityType,
                                  String reason, boolean spawnFail,
                                  Double x, Double y, Double z) {
        queueCombatSpawn(ts, room, world, null, null, null, null, null, null,
            entityName, entityType, reason, spawnFail, x, y, z);
    }

    /**
     * Queue a combat spawn event with arena context.
     */
    public void queueCombatSpawn(Instant ts, String room, String world,
                                  String templateId, Integer templateVersion,
                                  String policyId, Integer policyVersion,
                                  UUID arenaId, UUID sessionId,
                                  String entityName, String entityType,
                                  String reason, boolean spawnFail,
                                  Double x, Double y, Double z) {
        queueInsert("combat_spawns", new Object[] {
            ts, room, world, templateId, templateVersion, policyId, policyVersion, arenaId, sessionId,
            entityName, entityType, reason, spawnFail, x, y, z
        });
    }

    /**
     * Queue a combat fight session result.
     */
    public void queueCombatFight(String room, String world, Instant startTs, Instant endTs,
                                  long durationMs, int hits, int mobKills, int playerDeaths,
                                  String[] players, String mobKillsByTypeJson,
                                  String playerDeathsByNameJson, String ttkByTypeJson,
                                  double burstMax, double hpAfterPlayersAvg, double hpAfterMobsAvg) {
        queueCombatFight(room, world, null, null, null, null, null, null,
            startTs, endTs, durationMs, hits, mobKills, playerDeaths,
            players, mobKillsByTypeJson, playerDeathsByNameJson, ttkByTypeJson,
            burstMax, hpAfterPlayersAvg, hpAfterMobsAvg);
    }

    /**
     * Queue a combat fight session result with arena context.
     */
    public void queueCombatFight(String room, String world,
                                  String templateId, Integer templateVersion,
                                  String policyId, Integer policyVersion,
                                  UUID arenaId, UUID sessionId,
                                  Instant startTs, Instant endTs,
                                  long durationMs, int hits, int mobKills, int playerDeaths,
                                  String[] players, String mobKillsByTypeJson,
                                  String playerDeathsByNameJson, String ttkByTypeJson,
                                  double burstMax, double hpAfterPlayersAvg, double hpAfterMobsAvg) {
        queueInsert("combat_fights", new Object[] {
            room, world, templateId, templateVersion, policyId, policyVersion, arenaId, sessionId,
            startTs, endTs, durationMs, hits, mobKills, playerDeaths,
            players, mobKillsByTypeJson, playerDeathsByNameJson, ttkByTypeJson,
            burstMax, hpAfterPlayersAvg, hpAfterMobsAvg
        });
    }

    /**
     * Queue an endurance wave event.
     */
    public void queueEnduranceWave(Instant ts, UUID sessionId,
                                    String templateId, Integer templateVersion,
                                    String policyId, Integer policyVersion,
                                    UUID arenaId, int waveNumber,
                                    String eventType, Integer mobCount, Integer playerCount,
                                    String questType, String[] modifiers,
                                    Integer mobsKilled, Long durationMs,
                                    Boolean noDamage, Double killsPerSecond) {
        queueInsert("endurance_waves", new Object[] {
            ts, sessionId, templateId, templateVersion, policyId, policyVersion, arenaId,
            waveNumber, eventType, mobCount, playerCount, questType, modifiers,
            mobsKilled, durationMs, noDamage, killsPerSecond
        });
    }

    /**
     * Queue an endurance wave kill event.
     */
    public void queueEnduranceWaveKill(Instant ts, UUID sessionId,
                                        String templateId, Integer templateVersion,
                                        String policyId, Integer policyVersion,
                                        UUID arenaId, int waveNumber,
                                        String mobType, boolean isElite,
                                        String killerWeapon, double damageDealt) {
        queueInsert("endurance_wave_kills", new Object[] {
            ts, sessionId, templateId, templateVersion, policyId, policyVersion, arenaId,
            waveNumber, mobType, isElite, killerWeapon, damageDealt
        });
    }

    /**
     * Queue an endurance combo event.
     */
    public void queueEnduranceCombo(Instant ts, UUID playerId, UUID sessionId,
                                     String templateId, Integer templateVersion,
                                     String policyId, Integer policyVersion,
                                     UUID arenaId, String eventType, String oldRank, String newRank,
                                     Integer styleScore, Integer currentCombo,
                                     Integer milestone, Integer comboLost,
                                     Double damageTaken, String actionType,
                                     Integer pointsEarned, Integer styleEarned) {
        queueInsert("endurance_combos", new Object[] {
            ts, playerId, sessionId, templateId, templateVersion, policyId, policyVersion, arenaId,
            eventType, oldRank, newRank, styleScore, currentCombo,
            milestone, comboLost, damageTaken, actionType, pointsEarned, styleEarned
        });
    }

    /**
     * Queue an endurance perk event.
     */
    public void queueEndurancePerk(Instant ts, UUID playerId, UUID sessionId,
                                    String templateId, Integer templateVersion,
                                    String policyId, Integer policyVersion,
                                    UUID arenaId, String eventType, String perkId, String perkName,
                                    String tier, String category, Integer stackCount,
                                    Integer totalPerks, Integer waveNumber, String choicesJson) {
        queueInsert("endurance_perks", new Object[] {
            ts, playerId, sessionId, templateId, templateVersion, policyId, policyVersion, arenaId,
            eventType, perkId, perkName, tier, category, stackCount, totalPerks, waveNumber, choicesJson
        });
    }

    /**
     * Queue an endurance mutator event.
     */
    public void queueEnduranceMutator(Instant ts, UUID sessionId,
                                       String templateId, Integer templateVersion,
                                       String policyId, Integer policyVersion,
                                       UUID arenaId, String eventType,
                                       String mutatorId, String mutatorCategory,
                                       Integer waveNumber, Double rewardMultiplier,
                                       Integer mutatorCount, String mutatorsJson) {
        queueInsert("endurance_mutators", new Object[] {
            ts, sessionId, templateId, templateVersion, policyId, policyVersion, arenaId,
            eventType, mutatorId, mutatorCategory, waveNumber,
            rewardMultiplier, mutatorCount, mutatorsJson
        });
    }

    /**
     * Queue an endurance reward event.
     */
    public void queueEnduranceReward(Instant ts, UUID playerId, UUID sessionId,
                                      String templateId, Integer templateVersion,
                                      String policyId, Integer policyVersion,
                                      UUID arenaId, String eventType, String currency, Integer amount,
                                      String source, String itemId, Integer itemCount,
                                      String lootTier, String achievementId,
                                      String achievementName, Integer price, Integer purchaseCount) {
        queueInsert("endurance_rewards", new Object[] {
            ts, playerId, sessionId, templateId, templateVersion, policyId, policyVersion, arenaId,
            eventType, currency, amount, source, itemId, itemCount, lootTier,
            achievementId, achievementName, price, purchaseCount
        });
    }

    /**
     * Queue an endurance session event (start or end).
     * Note: Sessions use UUID as primary key, not sequence.
     */
    public void queueEnduranceSession(UUID sessionId, UUID playerId, String playerName,
                                       String questName, String questType, Integer totalWaves,
                                       Boolean isEndless, Integer playerCount, Instant startTs,
                                       Instant endTs, String outcome, Integer wavesCompleted,
                                       Integer totalKills, Double damageDealt, Double damageTaken,
                                       Integer tokensEarned, Integer prestigeEarned,
                                       Integer bloodGemsEarned, Integer noDamageWaves,
                                       String templateId, Integer templateVersion,
                                       String policyId, Integer policyVersion,
                                       UUID instanceId, UUID arenaId,
                                       Integer countdownStarted, Integer countdownCancelled,
                                       Integer giveupDuringRespawn,
                                       Integer inventoryRestoreSuccess, Integer inventoryRestoreFallback,
                                       Integer externalDeathRespawnCount, Integer waveBlockedDetected) {
        queueInsert("endurance_sessions", new Object[] {
            sessionId, playerId, playerName, questName, questType, totalWaves,
            isEndless, playerCount, startTs, endTs, outcome, wavesCompleted,
            totalKills, damageDealt, damageTaken, tokensEarned, prestigeEarned,
            bloodGemsEarned, noDamageWaves,
            templateId, templateVersion, policyId, policyVersion, instanceId, arenaId,
            countdownStarted, countdownCancelled, giveupDuringRespawn,
            inventoryRestoreSuccess, inventoryRestoreFallback,
            externalDeathRespawnCount, waveBlockedDetected
        });
    }

    /**
     * Queue an endurance party event.
     */
    public void queueEnduranceParty(Instant ts, UUID partyId, String eventType,
                                     UUID leaderId, String leaderName, UUID memberId,
                                     String memberName, String questType, Integer partySize,
                                     String reason, Boolean accepted) {
        queueInsert("endurance_parties", new Object[] {
            ts, partyId, eventType, leaderId, leaderName, memberId,
            memberName, questType, partySize, reason, accepted
        });
    }

    /**
     * Queue an endurance boss event.
     */
    public void queueEnduranceBoss(Instant ts, UUID sessionId,
                                    String templateId, Integer templateVersion,
                                    String policyId, Integer policyVersion,
                                    UUID arenaId, String eventType,
                                    Integer waveNumber, String archetype, Double bossMaxHealth,
                                    Integer playerCount, String abilityName, Integer playersHit,
                                    Double abilityDamage, Long fightDurationMs,
                                    Integer bonusPoints, Double damageDealtToBoss) {
        queueInsert("endurance_bosses", new Object[] {
            ts, sessionId, templateId, templateVersion, policyId, policyVersion, arenaId,
            eventType, waveNumber, archetype, bossMaxHealth, playerCount,
            abilityName, playersHit, abilityDamage, fightDurationMs,
            bonusPoints, damageDealtToBoss
        });
    }

    /**
     * Queue an endurance performance summary.
     */
    public void queueEndurancePerformance(Instant ts, UUID sessionId, UUID playerId,
                                           String questType, Long durationMs, Integer wavesCompleted,
                                           Integer kills, Double damageDealt, Double damageTaken,
                                           Double avgTtkMs, Double kps, Double dtps, Double dps,
                                           String templateId, Integer templateVersion,
                                           String policyId, Integer policyVersion,
                                           UUID arenaId) {
        queueInsert("endurance_performance", new Object[] {
            ts, sessionId, playerId, questType, durationMs, wavesCompleted, kills,
            damageDealt, damageTaken, avgTtkMs, kps, dtps, dps,
            templateId, templateVersion, policyId, policyVersion, arenaId
        });
    }

    /**
     * Queue a player snapshot.
     */
    public void queuePlayerSnapshot(Instant ts, UUID playerId, String playerName,
                                     String triggerType, double healthHp, double maxHealthHp,
                                     int healthHearts, double absorptionHp,
                                     int hungerLevel, double saturation, double exhaustion,
                                     double movementSpeed, double velocityX, double velocityY, double velocityZ,
                                     int movementFlags, double meleeDamageMult, double meleeReduction,
                                     double magicDamageMult, double magicReduction,
                                     double rangedDamageMult, double rangedReduction,
                                     double armorValue, double armorToughness, double knockbackResistance,
                                     double totalDamageReduction, double reach,
                                     double hitboxWidth, double hitboxHeight,
                                     Double pehkuiScale, Double pehkuiHitboxScale,
                                     double stamina, double maxStamina,
                                     double dashCooldown, double dodgeCooldown, int abilityFlags,
                                     int currentCombo, String styleRank, int styleScore,
                                     double x, double y, double z, String dimension) {
        queueInsert("player_snapshots", new Object[] {
            ts, playerId, playerName, triggerType, healthHp, maxHealthHp, healthHearts,
            absorptionHp, hungerLevel, saturation, exhaustion, movementSpeed,
            velocityX, velocityY, velocityZ, movementFlags, meleeDamageMult, meleeReduction,
            magicDamageMult, magicReduction, rangedDamageMult, rangedReduction,
            armorValue, armorToughness, knockbackResistance, totalDamageReduction,
            reach, hitboxWidth, hitboxHeight, pehkuiScale, pehkuiHitboxScale,
            stamina, maxStamina, dashCooldown, dodgeCooldown, abilityFlags,
            currentCombo, styleRank, styleScore, x, y, z, dimension
        });
    }

    /**
     * Queue a player ability event.
     */
    public void queuePlayerAbility(Instant ts, UUID playerId, String abilityType,
                                    Boolean success, Integer result,
                                    Double staminaBefore, Double staminaAfter, Double staminaCost,
                                    Double damageNegated, String damageSource,
                                    String context, Long regenTimeMs) {
        queueInsert("player_abilities", new Object[] {
            ts, playerId, abilityType, success, result, staminaBefore, staminaAfter,
            staminaCost, damageNegated, damageSource, context, regenTimeMs
        });
    }

    /**
     * Queue a player attribute change event.
     */
    public void queuePlayerAttributeChange(Instant ts, UUID playerId, String attributeName,
                                            double oldValue, double newValue) {
        queueInsert("player_attribute_changes", new Object[] {
            ts, playerId, attributeName, oldValue, newValue, newValue - oldValue
        });
    }

    /**
     * Queue a spatial heatmap point.
     */
    public void queueSpatialHeatmap(Instant ts, String heatmapType, String room,
                                     int x, int y, int z, int count) {
        queueInsert("spatial_heatmaps", new Object[] {
            ts, heatmapType, room, x, y, z, count
        });
    }

    public void queueArenaSpatialEvent(Instant ts, String templateId, Integer templateVersion,
                                       UUID sessionId, String eventType, int gridX, int gridZ,
                                       double worldX, double worldY, double worldZ, UUID playerId) {
        queueInsert("arena_spatial_events", new Object[] {
            ts, templateId, templateVersion, sessionId, eventType,
            gridX, gridZ, worldX, worldY, worldZ, playerId
        });
    }

    /**
     * Queue an arena template build event.
     */
    public void queueArenaTemplateBuild(Instant ts, UUID arenaId, String templateId, Integer templateVersion,
                                         String policyId, Integer policyVersion,
                                         Integer originX, Integer originY, Integer originZ, String dimension,
                                         Integer estimatedBlocks, Integer actualBlocks,
                                         Long estimatedMs, Long actualMs,
                                         boolean success, String errorMessage,
                                         Long rollbackMs, Integer blocksReverted,
                                         Double baselineMspt, Double avgMspt, Double peakMspt,
                                         Double maxBuildImpactMs, Integer pauseCount,
                                         Integer throttleCount, Boolean perfAborted) {
        queueInsert("arena_template_builds", new Object[] {
            ts, arenaId, templateId, templateVersion, policyId, policyVersion,
            originX, originY, originZ, dimension,
            estimatedBlocks, actualBlocks, estimatedMs, actualMs,
            success, errorMessage, rollbackMs, blocksReverted,
            baselineMspt, avgMspt, peakMspt, maxBuildImpactMs,
            pauseCount, throttleCount, perfAborted
        });
    }

    /**
     * Queue an arena template build event from BuildEventRecord.
     */
    public void queueArenaTemplateBuild(ArenaRecords.BuildEventRecord record) {
        queueArenaTemplateBuild(
            record.startedAt() != null ? record.startedAt() : Instant.now(),
            record.arenaId(),
            record.templateId(),
            record.templateVersion(),
            record.policyId(),
            record.policyVersion(),
            record.originX(),
            record.originY(),
            record.originZ(),
            record.dimension(),
            record.estimatedBlocks() != null ? record.estimatedBlocks().intValue() : null,
            record.actualBlocks() != null ? record.actualBlocks().intValue() : null,
            record.estimatedMs(),
            record.actualMs(),
            record.success() != null && record.success(),
            record.errorMessage(),
            record.rollbackMs(),
            record.blocksReverted(),
            record.baselineMspt(),
            record.avgMspt(),
            record.peakMspt(),
            record.maxBuildImpactMs(),
            record.pauseCount(),
            record.throttleCount(),
            record.perfAborted()
        );
    }

    /**
     * Queue an arena template usage event.
     */
    public void queueArenaTemplateUsage(Instant ts, String templateId, Integer templateVersion,
                                         String policyId, Integer policyVersion,
                                         UUID playerId, String playerName,
                                         String questType, String mobId, String difficulty,
                                         Integer playerCount, UUID sessionId,
                                         String eventType, Long durationMs,
                                         Integer wavesCompleted, String outcome) {
        queueInsert("arena_template_usage", new Object[] {
            ts, templateId, templateVersion, policyId, policyVersion,
            playerId, playerName, questType, mobId, difficulty,
            playerCount, sessionId, eventType, durationMs, wavesCompleted, outcome
        });
    }

    /**
     * Queue an arena template usage start event.
     */
    public void queueArenaUsageStart(String templateId, Integer templateVersion,
                                      String policyId, Integer policyVersion,
                                      UUID playerId, String playerName,
                                      String questType, String mobId, String difficulty,
                                      Integer playerCount, UUID sessionId) {
        queueArenaTemplateUsage(Instant.now(), templateId, templateVersion,
            policyId, policyVersion, playerId, playerName,
            questType, mobId, difficulty, playerCount, sessionId,
            "start", null, null, null);
    }

    /**
     * Queue an arena template usage end event.
     */
    public void queueArenaUsageEnd(String templateId, Integer templateVersion,
                                    String policyId, Integer policyVersion,
                                    UUID playerId, String playerName,
                                    String questType, String mobId, String difficulty,
                                    Integer playerCount, UUID sessionId,
                                    Long durationMs, Integer wavesCompleted, String outcome) {
        queueArenaTemplateUsage(Instant.now(), templateId, templateVersion,
            policyId, policyVersion, playerId, playerName,
            questType, mobId, difficulty, playerCount, sessionId,
            "end", durationMs, wavesCompleted, outcome);
    }

    /**
     * Queue a spatial alert.
     */
    public void queueSpatialAlert(Instant ts, String alertType, String playerName,
                                   String entityName, String entityType, String room,
                                   double x, double y, double z, String extraDataJson) {
        queueInsert("spatial_alerts", new Object[] {
            ts, alertType, playerName, entityName, entityType, room, x, y, z, extraDataJson
        });
    }

    /**
     * Queue a room transition.
     */
    public void queueRoomTransition(Instant ts, UUID playerId, String playerName, String room) {
        queueInsert("spatial_room_transitions", new Object[] {
            ts, playerId, playerName, room
        });
    }

    /**
     * Queue a performance sample.
     */
    public void queuePerformanceSample(Instant ts, double mspt, double tps) {
        queueInsert("performance_samples", new Object[] {
            ts, mspt, tps
        });
    }

    // ============================================
    // ECONOMY EVENTS (P1)
    // ============================================

    /**
     * Queue an economy mob kill event.
     */
    public void queueEconomyMobKill(Instant ts, String mobType, int totalKills, boolean hadLoot) {
        queueInsert("economy_mob_kills", new Object[] {
            ts, mobType, totalKills, hadLoot
        });
    }

    /**
     * Queue an economy mob drop event.
     */
    public void queueEconomyMobDrop(Instant ts, String mobType, String room, String itemId,
                                     int itemCount, int x, int y, int z) {
        queueInsert("economy_mob_drops", new Object[] {
            ts, mobType, room, itemId, itemCount, x, y, z
        });
    }

    /**
     * Queue an economy item pickup event.
     */
    public void queueEconomyItemPickup(Instant ts, UUID playerId, String playerName, String room,
                                        String itemId, int itemCount, int x, int y, int z) {
        queueInsert("economy_item_pickups", new Object[] {
            ts, playerId, playerName, room, itemId, itemCount, x, y, z
        });
    }

    /**
     * Queue an economy item usage event.
     */
    public void queueEconomyItemUsage(Instant ts, UUID playerId, String playerName,
                                       String eventType, String itemId, int itemCount, String useType) {
        queueInsert("economy_item_usage", new Object[] {
            ts, playerId, playerName, eventType, itemId, itemCount, useType
        });
    }

    // ============================================
    // PROGRESSION EVENTS (P1)
    // ============================================

    /**
     * Queue a progression block event.
     */
    public void queueProgressionBlock(Instant ts, UUID playerId, String playerName,
                                       String worldId, String room, String eventType,
                                       String blockId, int x, int y, int z) {
        queueInsert("progression_blocks", new Object[] {
            ts, playerId, playerName, worldId, room, eventType, blockId, x, y, z
        });
    }

    /**
     * Queue a progression XP event.
     */
    public void queueProgressionXp(Instant ts, UUID playerId, String playerName,
                                    String worldId, String room, String eventType,
                                    int xpAmount, int oldLevel, int newLevel,
                                    int x, int y, int z) {
        queueInsert("progression_xp", new Object[] {
            ts, playerId, playerName, worldId, room, eventType, xpAmount, oldLevel, newLevel, x, y, z
        });
    }

    /**
     * Queue a progression advancement event.
     */
    public void queueProgressionAdvancement(Instant ts, UUID playerId, String playerName,
                                             String worldId, String room,
                                             String advancementId, String title,
                                             int x, int y, int z) {
        queueInsert("progression_advancements", new Object[] {
            ts, playerId, playerName, worldId, room, advancementId, title, x, y, z
        });
    }

    /**
     * Queue a progression dimension change event.
     */
    public void queueProgressionDimension(Instant ts, UUID playerId, String playerName,
                                           String worldId, String fromDimension, String toDimension,
                                           int x, int y, int z) {
        queueInsert("progression_dimensions", new Object[] {
            ts, playerId, playerName, worldId, fromDimension, toDimension, x, y, z
        });
    }

    /**
     * Queue a progression trade event.
     */
    public void queueProgressionTrade(Instant ts, UUID playerId, String playerName,
                                       String worldId, String room,
                                       String villagerType, String profession,
                                       String itemBought, int itemBoughtCount,
                                       String itemSold, int itemSoldCount,
                                       int x, int y, int z) {
        queueInsert("progression_trades", new Object[] {
            ts, playerId, playerName, worldId, room, villagerType, profession,
            itemBought, itemBoughtCount, itemSold, itemSoldCount, x, y, z
        });
    }

    /**
     * Queue a progression fishing event.
     */
    public void queueProgressionFishing(Instant ts, UUID playerId, String playerName,
                                         String worldId, String room,
                                         String itemId, int itemCount,
                                         int x, int y, int z) {
        queueInsert("progression_fishing", new Object[] {
            ts, playerId, playerName, worldId, room, itemId, itemCount, x, y, z
        });
    }

    // ============================================
    // DUNGEON EVENTS (P2-B)
    // ============================================

    /**
     * Queue a dungeon run completion event.
     */
    public void queueDungeonRun(Instant startTs, Instant endTs, long durationMs,
                                 String playerId, String playerName, String dungeonId,
                                 String outcome, int roomsVisited, String roomsList,
                                 int deaths, int kills, String enemiesKilled,
                                 float damageDealt, float damageTaken,
                                 int rewardCount, String lootCollected, String lastDeathRoom) {
        queueInsert("dungeon_runs", new Object[] {
            startTs, endTs, durationMs, playerId, playerName, dungeonId,
            outcome, roomsVisited, roomsList, deaths, kills, enemiesKilled,
            damageDealt, damageTaken, rewardCount, lootCollected, lastDeathRoom
        });
    }

    // ============================================
    // INTERNAL QUEUE LOGIC
    // ============================================

    /**
     * Queue an insert for a specific table with backpressure.
     */
    private void queueInsert(String tableName, Object[] values) {
        if (!running) return;

        // Update pressure level based on total queue size (atomic update)
        int totalPending = getPendingInserts();
        if (totalPending >= PRESSURE_THRESHOLD_CRITICAL) {
            pressureLevel.set(2);
        } else if (totalPending >= PRESSURE_THRESHOLD_ELEVATED) {
            pressureLevel.set(1);
        } else {
            pressureLevel.set(0);
        }

        // Backpressure: check if this event should be dropped
        EventPriority priority = TABLE_PRIORITY.getOrDefault(tableName, EventPriority.NORMAL);
        if (shouldDropEvent(priority)) {
            droppedInserts.incrementAndGet();
            // Track by priority for detailed stats
            if (priority == EventPriority.LOW) {
                droppedByPriorityLow.incrementAndGet();
            } else if (priority == EventPriority.NORMAL) {
                droppedByPriorityNormal.incrementAndGet();
            }
            if (DuckDBConfig.LOG_INSERTS) {
                LOGGER.debug("[DuckDB] Backpressure drop: {} (priority={}, pressure={})",
                    tableName, priority, pressureLevel.get());
            }
            return;
        }

        BlockingQueue<Object[]> queue = tableQueues.computeIfAbsent(tableName,
            k -> new LinkedBlockingQueue<>(DuckDBConfig.QUEUE_CAPACITY));

        if (!queue.offer(values)) {
            boolean enqueued = false;
            if (priority == EventPriority.CRITICAL) {
                // For critical events, attempt an immediate flush to free space and retry once.
                flushTable(tableName);
                enqueued = queue.offer(values);
                if (!enqueued) {
                    try {
                        enqueued = queue.offer(values, 50, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            if (!enqueued) {
                // Queue full - drop event (non-critical or retry failed)
                droppedInserts.incrementAndGet();
                droppedByQueueFull.incrementAndGet();
                if (DuckDBConfig.LOG_INSERTS) {
                    LOGGER.warn("[DuckDB] Queue full for {}, dropping insert", tableName);
                }
            }
        } else {
            // Check if batch size reached for immediate flush
            if (queue.size() >= DuckDBConfig.BATCH_SIZE) {
                scheduler.execute(() -> flushTable(tableName));
            }
        }
    }

    /**
     * Determine if an event should be dropped based on backpressure.
     */
    private boolean shouldDropEvent(EventPriority priority) {
        switch (pressureLevel.get()) {
            case 2: // CRITICAL pressure - drop everything except CRITICAL priority
                return priority != EventPriority.CRITICAL;
            case 1: // ELEVATED pressure - drop LOW and NORMAL
                return priority == EventPriority.LOW || priority == EventPriority.NORMAL;
            default: // NORMAL pressure - no drops (removed random drop to ensure test reliability)
                return false;
        }
    }

    // ============================================
    // FLUSH LOGIC (runs on writer thread)
    // ============================================

    /**
     * Flush all table batches.
     * Synchronized to prevent concurrent DuckDB writes (single-writer constraint).
     */
    private void flushAllBatches() {
        if (connectionManager.isShuttingDown()) return;

        synchronized (flushLock) {
            long startTime = System.nanoTime();
            int totalFlushed = 0;

            for (String tableName : tableQueues.keySet()) {
                totalFlushed += flushTableUnlocked(tableName);
            }

            // Track flush metrics
            if (totalFlushed > 0) {
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                flushLatencyTotalMs.addAndGet(elapsedMs);
                flushCount.incrementAndGet();

                if (DuckDBConfig.LOG_BATCH_TIMING) {
                    LOGGER.debug("[DuckDB] Flushed {} rows in {}ms", totalFlushed, elapsedMs);
                }
            }
        }
    }

    /**
     * Flush a single table's batch with transaction and circuit breaker.
     */
    private int flushTable(String tableName) {
        synchronized (flushLock) {
            return flushTableUnlocked(tableName);
        }
    }

    private int flushTableUnlocked(String tableName) {
        // Circuit breaker: skip if broken
        if (circuitBroken) return 0;

        BlockingQueue<Object[]> queue = tableQueues.get(tableName);
        if (queue == null || queue.isEmpty()) return 0;

        Connection conn = null;
        boolean autoCommitOriginal = true;
        int flushed = 0;
        List<Object[]> batch = null;

        try {
            conn = connectionManager.getConnection();
            String sql = insertSqlCache.get(tableName);
            if (sql == null) {
                LOGGER.warn("[DuckDB] No SQL template for table: {}", tableName);
                return 0;
            }

            autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Allow final flush even after running=false (shutdown)
            while (!queue.isEmpty()) {
                batch = new ArrayList<>(DuckDBConfig.BATCH_SIZE);
                queue.drainTo(batch, DuckDBConfig.BATCH_SIZE);
                if (batch.isEmpty()) break;

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (Object[] row : batch) {
                        setParameters(stmt, row);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }

                flushed += batch.size();
                totalInserts.addAndGet(batch.size());
                totalBatches.incrementAndGet();
            }

            if (flushed > 0) {
                conn.commit();
                consecutiveErrors.set(0);

                if (DuckDBConfig.LOG_INSERTS) {
                    LOGGER.debug("[DuckDB] Inserted {} rows into {}", flushed, tableName);
                }
            }

            return flushed;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    LOGGER.error("[DuckDB] Rollback failed, forcing reconnect: {}", rollbackEx.getMessage());
                    // Connection may be in inconsistent state; force reconnect
                    try {
                        connectionManager.reconnect();
                    } catch (SQLException reconnectEx) {
                        LOGGER.error("[DuckDB] Reconnect failed: {}", reconnectEx.getMessage());
                    }
                }
            }

            // Re-queue drained batch so we don't lose data on transient errors
            if (batch != null && !batch.isEmpty()) {
                for (Object[] row : batch) {
                    queue.offer(row);
                }
            }

            errorCount.incrementAndGet();
            long errors = consecutiveErrors.incrementAndGet();
            if (errors >= CIRCUIT_BREAKER_THRESHOLD) {
                circuitBroken = true;
                LOGGER.error("[DuckDB] CIRCUIT BREAKER TRIGGERED after {} consecutive errors. " +
                    "DuckDB writes disabled. Error: {}", errors, e.getMessage());
                DuckDBTelemetryService.INSTANCE.triggerCircuitBreaker();
            } else {
                LOGGER.error("[DuckDB] Failed to flush batch for {} (error {}/{}): {}",
                    tableName, errors, CIRCUIT_BREAKER_THRESHOLD, e.getMessage());
            }
            return flushed;

        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(autoCommitOriginal);
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Set prepared statement parameters from an object array.
     */
    private void setParameters(PreparedStatement stmt, Object[] values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            int paramIndex = i + 1;

            if (value == null) {
                stmt.setNull(paramIndex, java.sql.Types.NULL);
            } else if (value instanceof String s) {
                stmt.setString(paramIndex, s);
            } else if (value instanceof Integer n) {
                stmt.setInt(paramIndex, n);
            } else if (value instanceof Long n) {
                stmt.setLong(paramIndex, n);
            } else if (value instanceof Double n) {
                stmt.setDouble(paramIndex, n);
            } else if (value instanceof Float n) {
                stmt.setFloat(paramIndex, n);
            } else if (value instanceof Boolean b) {
                stmt.setBoolean(paramIndex, b);
            } else if (value instanceof UUID u) {
                stmt.setObject(paramIndex, u);
            } else if (value instanceof Instant instant) {
                stmt.setTimestamp(paramIndex, Timestamp.from(instant));
            } else if (value instanceof String[] arr) {
                // DuckDB JDBC doesn't support setArray - convert to JSON string
                String json = "[" + String.join(",", java.util.Arrays.stream(arr)
                    .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
                    .toArray(String[]::new)) + "]";
                stmt.setString(paramIndex, json);
            } else {
                // Fallback: convert to string
                stmt.setString(paramIndex, value.toString());
            }
        }
    }

    // ============================================
    // SQL TEMPLATES
    // ============================================

    private void initializeSqlTemplates() {
        // Combat tables
        insertSqlCache.put("combat_hits",
            "INSERT INTO combat_hits (id, ts, room, world, template_id, template_version, policy_id, policy_version, arena_id, session_id, " +
            "attacker_name, attacker_type, target_name, target_type, damage, damage_type, hp_before, hp_after, body_part, " +
            "distance, armor_pen_bonus, is_miss, is_hazard, hazard_type, attacker_state, target_state) " +
            "VALUES (nextval('seq_combat_hits'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::JSON, ?::JSON)");

        insertSqlCache.put("combat_deaths",
            "INSERT INTO combat_deaths (id, ts, room, world, template_id, template_version, policy_id, policy_version, arena_id, session_id, " +
            "target_name, target_type, cause, ttk_first_hit_ms, ttk_spawn_ms) " +
            "VALUES (nextval('seq_combat_deaths'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("combat_heals",
            "INSERT INTO combat_heals (id, ts, room, world, template_id, template_version, policy_id, policy_version, arena_id, session_id, " +
            "target_name, target_type, heal_amount, hp_before, hp_after, source) " +
            "VALUES (nextval('seq_combat_heals'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("combat_spawns",
            "INSERT INTO combat_spawns (id, ts, room, world, template_id, template_version, policy_id, policy_version, arena_id, session_id, " +
            "entity_name, entity_type, reason, spawn_fail, x, y, z) " +
            "VALUES (nextval('seq_combat_spawns'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("combat_fights",
            "INSERT INTO combat_fights (id, room, world, template_id, template_version, policy_id, policy_version, arena_id, session_id, " +
            "start_ts, end_ts, duration_ms, hits, mob_kills, player_deaths, players, mob_kills_by_type, " +
            "player_deaths_by_name, ttk_by_type, burst_max, hp_after_players_avg, hp_after_mobs_avg) " +
            "VALUES (nextval('seq_combat_fights'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::JSON, ?::JSON, ?::JSON, ?, ?, ?)");

        // Endurance tables
        insertSqlCache.put("endurance_waves",
            "INSERT INTO endurance_waves (id, ts, session_id, template_id, template_version, policy_id, policy_version, arena_id, " +
            "wave_number, event_type, mob_count, player_count, quest_type, modifiers, mobs_killed, duration_ms, no_damage, kills_per_second) " +
            "VALUES (nextval('seq_endurance_waves'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("endurance_wave_kills",
            "INSERT INTO endurance_wave_kills (id, ts, session_id, template_id, template_version, policy_id, policy_version, arena_id, " +
            "wave_number, mob_type, is_elite, killer_weapon, damage_dealt) " +
            "VALUES (nextval('seq_endurance_wave_kills'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("endurance_combos",
            "INSERT INTO endurance_combos (id, ts, player_id, session_id, template_id, template_version, policy_id, policy_version, arena_id, " +
            "event_type, old_rank, new_rank, style_score, current_combo, milestone, combo_lost, damage_taken, action_type, points_earned, style_earned) " +
            "VALUES (nextval('seq_endurance_combos'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("endurance_perks",
            "INSERT INTO endurance_perks (id, ts, player_id, session_id, template_id, template_version, policy_id, policy_version, arena_id, " +
            "event_type, perk_id, perk_name, tier, category, stack_count, total_perks, wave_number, choices) " +
            "VALUES (nextval('seq_endurance_perks'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::JSON)");

        insertSqlCache.put("endurance_mutators",
            "INSERT INTO endurance_mutators (id, ts, session_id, template_id, template_version, policy_id, policy_version, arena_id, " +
            "event_type, mutator_id, mutator_category, wave_number, reward_multiplier, mutator_count, mutators) " +
            "VALUES (nextval('seq_endurance_mutators'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::JSON)");

        insertSqlCache.put("endurance_rewards",
            "INSERT INTO endurance_rewards (id, ts, player_id, session_id, template_id, template_version, policy_id, policy_version, arena_id, " +
            "event_type, currency, amount, source, item_id, item_count, loot_tier, achievement_id, achievement_name, price, purchase_count) " +
            "VALUES (nextval('seq_endurance_rewards'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("endurance_sessions",
            "INSERT INTO endurance_sessions (id, player_id, player_name, quest_name, quest_type, total_waves, " +
            "is_endless, player_count, start_ts, end_ts, outcome, waves_completed, total_kills, damage_dealt, " +
            "damage_taken, tokens_earned, prestige_earned, blood_gems_earned, no_damage_waves, " +
            "template_id, template_version, policy_id, policy_version, instance_id, arena_id, " +
            "countdown_started, countdown_cancelled, giveup_during_respawn, " +
            "inventory_restore_success, inventory_restore_fallback, " +
            "external_death_respawn_count, wave_blocked_detected) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(id) DO UPDATE SET " +
            "player_name = CASE WHEN excluded.player_name IS NULL THEN endurance_sessions.player_name ELSE excluded.player_name END, " +
            "quest_name = CASE WHEN excluded.quest_name IS NULL THEN endurance_sessions.quest_name ELSE excluded.quest_name END, " +
            "quest_type = CASE WHEN excluded.quest_type IS NULL THEN endurance_sessions.quest_type ELSE excluded.quest_type END, " +
            "total_waves = CASE WHEN excluded.total_waves IS NULL THEN endurance_sessions.total_waves ELSE excluded.total_waves END, " +
            "is_endless = CASE WHEN excluded.is_endless IS NULL THEN endurance_sessions.is_endless ELSE excluded.is_endless END, " +
            "player_count = CASE WHEN excluded.player_count IS NULL THEN endurance_sessions.player_count ELSE excluded.player_count END, " +
            "start_ts = COALESCE(endurance_sessions.start_ts, excluded.start_ts), " +
            "end_ts = CASE WHEN excluded.end_ts IS NULL THEN endurance_sessions.end_ts ELSE excluded.end_ts END, " +
            "outcome = CASE WHEN excluded.outcome IS NULL THEN endurance_sessions.outcome ELSE excluded.outcome END, " +
            "waves_completed = CASE WHEN excluded.waves_completed IS NULL THEN endurance_sessions.waves_completed ELSE excluded.waves_completed END, " +
            "total_kills = CASE WHEN excluded.total_kills IS NULL THEN endurance_sessions.total_kills ELSE excluded.total_kills END, " +
            "damage_dealt = CASE WHEN excluded.damage_dealt IS NULL THEN endurance_sessions.damage_dealt ELSE excluded.damage_dealt END, " +
            "damage_taken = CASE WHEN excluded.damage_taken IS NULL THEN endurance_sessions.damage_taken ELSE excluded.damage_taken END, " +
            "tokens_earned = CASE WHEN excluded.tokens_earned IS NULL THEN endurance_sessions.tokens_earned ELSE excluded.tokens_earned END, " +
            "prestige_earned = CASE WHEN excluded.prestige_earned IS NULL THEN endurance_sessions.prestige_earned ELSE excluded.prestige_earned END, " +
            "blood_gems_earned = CASE WHEN excluded.blood_gems_earned IS NULL THEN endurance_sessions.blood_gems_earned ELSE excluded.blood_gems_earned END, " +
            "no_damage_waves = CASE WHEN excluded.no_damage_waves IS NULL THEN endurance_sessions.no_damage_waves ELSE excluded.no_damage_waves END, " +
            "template_id = CASE WHEN excluded.template_id IS NULL THEN endurance_sessions.template_id ELSE excluded.template_id END, " +
            "template_version = CASE WHEN excluded.template_version IS NULL THEN endurance_sessions.template_version ELSE excluded.template_version END, " +
            "policy_id = CASE WHEN excluded.policy_id IS NULL THEN endurance_sessions.policy_id ELSE excluded.policy_id END, " +
            "policy_version = CASE WHEN excluded.policy_version IS NULL THEN endurance_sessions.policy_version ELSE excluded.policy_version END, " +
            "instance_id = CASE WHEN excluded.instance_id IS NULL THEN endurance_sessions.instance_id ELSE excluded.instance_id END, " +
            "arena_id = CASE WHEN excluded.arena_id IS NULL THEN endurance_sessions.arena_id ELSE excluded.arena_id END, " +
            "countdown_started = GREATEST(COALESCE(endurance_sessions.countdown_started, 0), COALESCE(excluded.countdown_started, 0)), " +
            "countdown_cancelled = GREATEST(COALESCE(endurance_sessions.countdown_cancelled, 0), COALESCE(excluded.countdown_cancelled, 0)), " +
            "giveup_during_respawn = GREATEST(COALESCE(endurance_sessions.giveup_during_respawn, 0), COALESCE(excluded.giveup_during_respawn, 0)), " +
            "inventory_restore_success = GREATEST(COALESCE(endurance_sessions.inventory_restore_success, 0), COALESCE(excluded.inventory_restore_success, 0)), " +
            "inventory_restore_fallback = GREATEST(COALESCE(endurance_sessions.inventory_restore_fallback, 0), COALESCE(excluded.inventory_restore_fallback, 0)), " +
            "external_death_respawn_count = GREATEST(COALESCE(endurance_sessions.external_death_respawn_count, 0), COALESCE(excluded.external_death_respawn_count, 0)), " +
            "wave_blocked_detected = GREATEST(COALESCE(endurance_sessions.wave_blocked_detected, 0), COALESCE(excluded.wave_blocked_detected, 0))");

        insertSqlCache.put("endurance_parties",
            "INSERT INTO endurance_parties (id, ts, party_id, event_type, leader_id, leader_name, member_id, " +
            "member_name, quest_type, party_size, reason, accepted) " +
            "VALUES (nextval('seq_endurance_parties'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("endurance_bosses",
            "INSERT INTO endurance_bosses (id, ts, session_id, template_id, template_version, policy_id, policy_version, arena_id, " +
            "event_type, wave_number, archetype, boss_max_health, player_count, ability_name, players_hit, ability_damage, " +
            "fight_duration_ms, bonus_points, damage_dealt_to_boss) " +
            "VALUES (nextval('seq_endurance_bosses'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("endurance_performance",
            "INSERT INTO endurance_performance (id, ts, session_id, player_id, quest_type, duration_ms, waves_completed, " +
            "kills, damage_dealt, damage_taken, avg_ttk_ms, kps, dtps, dps, " +
            "template_id, template_version, policy_id, policy_version, arena_id) " +
            "VALUES (nextval('seq_endurance_performance'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        // Player tables
        insertSqlCache.put("player_snapshots",
            "INSERT INTO player_snapshots (id, ts, player_id, player_name, trigger_type, health_hp, max_health_hp, " +
            "health_hearts, absorption_hp, hunger_level, saturation, exhaustion, movement_speed, velocity_x, velocity_y, " +
            "velocity_z, movement_flags, melee_damage_mult, melee_reduction, magic_damage_mult, magic_reduction, " +
            "ranged_damage_mult, ranged_reduction, armor_value, armor_toughness, knockback_resistance, total_damage_reduction, " +
            "reach, hitbox_width, hitbox_height, pehkui_scale, pehkui_hitbox_scale, stamina, max_stamina, dash_cooldown, " +
            "dodge_cooldown, ability_flags, current_combo, style_rank, style_score, x, y, z, dimension) " +
            "VALUES (nextval('seq_player_snapshots'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("player_abilities",
            "INSERT INTO player_abilities (id, ts, player_id, ability_type, success, result, stamina_before, stamina_after, " +
            "stamina_cost, damage_negated, damage_source, context, regen_time_ms) " +
            "VALUES (nextval('seq_player_abilities'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("player_attribute_changes",
            "INSERT INTO player_attribute_changes (id, ts, player_id, attribute_name, old_value, new_value, delta) " +
            "VALUES (nextval('seq_player_attribute_changes'), ?, ?, ?, ?, ?, ?)");

        // Spatial tables
        insertSqlCache.put("spatial_heatmaps",
            "INSERT INTO spatial_heatmaps (id, ts, heatmap_type, room, x, y, z, count) " +
            "VALUES (nextval('seq_spatial_heatmaps'), ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("spatial_alerts",
            "INSERT INTO spatial_alerts (id, ts, alert_type, player_name, entity_name, entity_type, room, x, y, z, extra_data) " +
            "VALUES (nextval('seq_spatial_alerts'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::JSON)");

        insertSqlCache.put("spatial_room_transitions",
            "INSERT INTO spatial_room_transitions (id, ts, player_id, player_name, room) " +
            "VALUES (nextval('seq_spatial_room_transitions'), ?, ?, ?, ?)");

        insertSqlCache.put("arena_spatial_events",
            "INSERT INTO arena_spatial_events (id, ts, template_id, template_version, session_id, event_type, " +
            "grid_x, grid_z, world_x, world_y, world_z, player_uuid) " +
            "VALUES (nextval('seq_arena_spatial_events'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        // Arena tables (builds & usage)
        insertSqlCache.put("arena_template_builds",
            "INSERT INTO arena_template_builds (id, ts, arena_id, template_id, template_version, " +
            "policy_id, policy_version, origin_x, origin_y, origin_z, dimension, " +
            "estimated_blocks, actual_blocks, estimated_ms, actual_ms, " +
            "success, error_message, rollback_ms, blocks_reverted, " +
            "baseline_mspt, avg_mspt, peak_mspt, max_build_impact_ms, " +
            "pause_count, throttle_count, perf_aborted) " +
            "VALUES (nextval('seq_arena_template_builds'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("arena_template_usage",
            "INSERT INTO arena_template_usage (id, ts, template_id, template_version, " +
            "policy_id, policy_version, player_id, player_name, " +
            "quest_type, mob_id, difficulty, player_count, session_id, " +
            "event_type, duration_ms, waves_completed, outcome) " +
            "VALUES (nextval('seq_arena_template_usage'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        // System tables
        insertSqlCache.put("performance_samples",
            "INSERT INTO performance_samples (id, ts, mspt, tps) " +
            "VALUES (nextval('seq_performance_samples'), ?, ?, ?)");

        // Economy tables (P1)
        insertSqlCache.put("economy_mob_kills",
            "INSERT INTO economy_mob_kills (id, ts, mob_type, total_kills, had_loot) " +
            "VALUES (nextval('seq_economy_mob_kills'), ?, ?, ?, ?)");

        insertSqlCache.put("economy_mob_drops",
            "INSERT INTO economy_mob_drops (id, ts, mob_type, room, item_id, item_count, x, y, z) " +
            "VALUES (nextval('seq_economy_mob_drops'), ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("economy_item_pickups",
            "INSERT INTO economy_item_pickups (id, ts, player_id, player_name, room, item_id, item_count, x, y, z) " +
            "VALUES (nextval('seq_economy_item_pickups'), ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("economy_item_usage",
            "INSERT INTO economy_item_usage (id, ts, player_id, player_name, event_type, item_id, item_count, use_type) " +
            "VALUES (nextval('seq_economy_item_usage'), ?, ?, ?, ?, ?, ?, ?)");

        // Dungeon tables (P2-B)
        insertSqlCache.put("dungeon_runs",
            "INSERT INTO dungeon_runs (id, start_ts, end_ts, duration_ms, player_id, player_name, dungeon_id, " +
            "outcome, rooms_visited, rooms_list, deaths, kills, enemies_killed, damage_dealt, damage_taken, " +
            "reward_count, loot_collected, last_death_room) " +
            "VALUES (nextval('seq_dungeon_runs'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        // Progression tables (P1)
        insertSqlCache.put("progression_blocks",
            "INSERT INTO progression_blocks (id, ts, player_id, player_name, world_id, room, event_type, block_id, x, y, z) " +
            "VALUES (nextval('seq_progression_blocks'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("progression_xp",
            "INSERT INTO progression_xp (id, ts, player_id, player_name, world_id, room, event_type, xp_amount, old_level, new_level, x, y, z) " +
            "VALUES (nextval('seq_progression_xp'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("progression_advancements",
            "INSERT INTO progression_advancements (id, ts, player_id, player_name, world_id, room, advancement_id, title, x, y, z) " +
            "VALUES (nextval('seq_progression_advancements'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("progression_dimensions",
            "INSERT INTO progression_dimensions (id, ts, player_id, player_name, world_id, from_dimension, to_dimension, x, y, z) " +
            "VALUES (nextval('seq_progression_dimensions'), ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("progression_trades",
            "INSERT INTO progression_trades (id, ts, player_id, player_name, world_id, room, villager_type, profession, item_bought, item_bought_count, item_sold, item_sold_count, x, y, z) " +
            "VALUES (nextval('seq_progression_trades'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

        insertSqlCache.put("progression_fishing",
            "INSERT INTO progression_fishing (id, ts, player_id, player_name, world_id, room, item_id, item_count, x, y, z) " +
            "VALUES (nextval('seq_progression_fishing'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
    }

    // ============================================
    // STATISTICS
    // ============================================

    public long getTotalInserts() {
        return totalInserts.get();
    }

    public long getTotalBatches() {
        return totalBatches.get();
    }

    public long getDroppedInserts() {
        return droppedInserts.get();
    }

    public int getPendingInserts() {
        return tableQueues.values().stream().mapToInt(BlockingQueue::size).sum();
    }

    /**
     * Force an immediate flush of all pending batches.
     * Primarily for testing and graceful shutdown scenarios.
     */
    public void forceFlush() {
        flushAllBatches();
    }

    /**
     * Reset error state and reconnect. For testing only.
     * Clears circuit breaker and consecutive errors, forcing a fresh connection.
     */
    public void resetForTest() throws java.sql.SQLException {
        synchronized (flushLock) {
            circuitBroken = false;
            consecutiveErrors.set(0);
            connectionManager.reconnect();
        }
    }

    /**
     * Check if circuit breaker is triggered.
     */
    public boolean isCircuitBroken() {
        return circuitBroken;
    }

    /**
     * Get current backpressure level (0=normal, 1=elevated, 2=critical).
     */
    public int getPressureLevel() {
        return pressureLevel.get();
    }

    /**
     * Get detailed statistics string for monitoring/debugging.
     * Format: "inserts=N batches=N dropped=N(low=N normal=N full=N) avgFlushMs=N.NN errors=N pressure=N circuit=bool"
     */
    public String getDetailedStats() {
        double avgFlushMs = flushCount.get() > 0
            ? (double) flushLatencyTotalMs.get() / flushCount.get()
            : 0.0;

        return String.format(
            "inserts=%d batches=%d dropped=%d(low=%d normal=%d full=%d) avgFlushMs=%.2f errors=%d pressure=%d circuit=%b",
            totalInserts.get(),
            totalBatches.get(),
            droppedInserts.get(),
            droppedByPriorityLow.get(),
            droppedByPriorityNormal.get(),
            droppedByQueueFull.get(),
            avgFlushMs,
            errorCount.get(),
            pressureLevel.get(),
            circuitBroken
        );
    }

    /**
     * Get error count for monitoring.
     */
    public long getErrorCount() {
        return errorCount.get();
    }

    /**
     * Get average flush latency in milliseconds.
     */
    public double getAverageFlushLatencyMs() {
        return flushCount.get() > 0
            ? (double) flushLatencyTotalMs.get() / flushCount.get()
            : 0.0;
    }
}
