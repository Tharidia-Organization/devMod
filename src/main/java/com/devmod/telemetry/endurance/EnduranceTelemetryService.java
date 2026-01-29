package com.devmod.telemetry.endurance;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;

import com.devmod.arena.api.ArenaHandle;
import com.devmod.endurance.CombatTracker;
import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceQuestState;
import com.devmod.endurance.MutatorSystem;
import com.devmod.endurance.PerkSystem;
import com.devmod.endurance.QuestType;
import com.devmod.endurance.RewardSystem;
import com.devmod.endurance.WaveManager;
import com.devmod.telemetry.TelemetryService;
import com.devmod.telemetry.duckdb.DuckDBConfig;
import com.devmod.telemetry.duckdb.DuckDBTelemetryService;

public class EnduranceTelemetryService {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final EnduranceTelemetryService INSTANCE = new EnduranceTelemetryService();

    // Per-quest session stats for aggregation
    private final Map<UUID, QuestSessionStats> questStats = new ConcurrentHashMap<>();

    // DIAGNOSTIC: Track NDJSON skip events (rate-limited logging)
    private static final AtomicInteger ndjsonSkipCount = new AtomicInteger(0);
    private static final AtomicLong lastSkipLogTime = new AtomicLong(0);
    private static final long SKIP_LOG_INTERVAL_MS = 30_000; // Log at most once per 30 seconds

    private static final int ARENA_HEATMAP_GRID = 16;

    private EnduranceTelemetryService() {}

    /**
     * DIAGNOSTIC: Log that NDJSON was skipped (rate-limited to 1 per 30 seconds).
     */
    private static void logNdjsonSkip() {
        int count = ndjsonSkipCount.incrementAndGet();
        long now = System.currentTimeMillis();
        long lastLog = lastSkipLogTime.get();
        if (now - lastLog > SKIP_LOG_INTERVAL_MS && lastSkipLogTime.compareAndSet(lastLog, now)) {
            LOGGER.debug("[Endurance] NDJSON writes SKIPPED (DuckDB primary mode) - skip count: {}", count);
        }
    }

    // ===== WAVE EVENTS =====

    /**
     * Record wave start.
     */
    public void recordWaveStart(UUID questId, int waveNumber, int mobCount, int playerCount,
                                 QuestType questType, Set<WaveManager.WaveModifier> modifiers) {
        String modifierList = modifiers.isEmpty() ? "none" :
            modifiers.stream().map(m -> m.name()).reduce((a, b) -> a + "," + b).orElse("none");
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(260);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"wave_start\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"wave\":").append(waveNumber).append(",");
        json.append("\"mobCount\":").append(mobCount).append(",");
        json.append("\"playerCount\":").append(playerCount).append(",");
        json.append("\"questType\":\"").append(questType.name()).append("\",");
        json.append("\"modifiers\":\"").append(escape(modifierList)).append("\"");
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        String[] modifierArray = modifiers.stream().map(Enum::name).toArray(String[]::new);
        DuckDBTelemetryService.INSTANCE.logWaveStart(questId, waveNumber, mobCount,
            playerCount, questType.name(), modifierArray,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        } else {
            logNdjsonSkip();
        }

        LOGGER.debug("[Endurance] Wave {} started with {} mobs", waveNumber, mobCount);

        // Update session stats
        getOrCreateStats(questId).waveStarted();
    }

    /**
     * Record wave completion.
     * PERFORMANCE: Uses StringBuilder instead of String.format (~10x faster).
     */
    public void recordWaveComplete(UUID questId, int waveNumber, int mobsKilled, long durationMs,
                                    boolean noDamage, float killsPerSecond) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(180);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"wave_complete\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"wave\":").append(waveNumber).append(",");
        json.append("\"mobsKilled\":").append(mobsKilled).append(",");
        json.append("\"durationMs\":").append(durationMs).append(",");
        json.append("\"noDamage\":").append(noDamage).append(",");
        json.append("\"killsPerSecond\":"); appendFloat2(json, killsPerSecond);
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logWaveComplete(questId, waveNumber, mobsKilled,
            durationMs, noDamage, killsPerSecond,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }

        LOGGER.debug("[Endurance] Wave {} complete: {} kills in {}ms", waveNumber, mobsKilled, durationMs);

        getOrCreateStats(questId).waveCompleted(noDamage);
    }

    /**
     * Record mob kill during wave.
     * PERFORMANCE: Uses StringBuilder instead of String.format (~10x faster).
     * This is a hot path - called on every mob kill.
     */
    public void recordWaveKill(UUID questId, int waveNumber, String mobType, boolean isElite,
                                String killerWeapon, float damageDealt) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(200);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"wave_kill\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"wave\":").append(waveNumber).append(",");
        json.append("\"mobType\":\"").append(escape(mobType)).append("\",");
        json.append("\"isElite\":").append(isElite).append(",");
        json.append("\"weapon\":\"").append(escape(killerWeapon)).append("\",");
        json.append("\"damage\":"); appendFloat1(json, damageDealt);
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage (HOT PATH - high volume)
        DuckDBTelemetryService.INSTANCE.logWaveKill(questId, waveNumber, mobType,
            isElite, killerWeapon, damageDealt,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    /**
     * Record a spawn slot fallback (e.g., pool exhausted or missing slots).
     */
    public void recordSpawnFallback(UUID questId, int waveNumber, String poolTag,
                                    @javax.annotation.Nullable String templateId,
                                    @javax.annotation.Nullable String reason) {
        StringBuilder json = new StringBuilder(200);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"spawn_fallback\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"wave\":").append(waveNumber).append(",");
        json.append("\"pool\":\"").append(escape(poolTag)).append("\",");
        json.append("\"templateId\":\"").append(escape(templateId != null ? templateId : "")).append("\",");
        json.append("\"reason\":\"").append(escape(reason != null ? reason : "")).append("\"");
        json.append("}");

        TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
    }

    /**
     * Record a spawn failure after exhausting all pools.
     */
    public void recordSpawnFailure(UUID questId, int waveNumber, String reason,
                                   @javax.annotation.Nullable String templateId) {
        StringBuilder json = new StringBuilder(180);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"spawn_failure\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"wave\":").append(waveNumber).append(",");
        json.append("\"reason\":\"").append(escape(reason != null ? reason : "")).append("\",");
        json.append("\"templateId\":\"").append(escape(templateId != null ? templateId : "")).append("\"");
        json.append("}");

        TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
    }

    /**
     * Record zone allocation metrics for a wave spawn batch.
     * Tracks how many spawns used zone allocation vs falling back to pool-based selection.
     *
     * @param questId The quest ID
     * @param waveNumber The current wave number
     * @param zoneAllocSuccess Number of spawns that successfully used zone allocation
     * @param zoneAllocFallback Number of spawns that fell back to pool-based selection
     * @param totalSpawns Total number of spawns attempted
     * @param templateId The arena template ID (may be null)
     */
    public void recordZoneAllocationMetrics(UUID questId, int waveNumber,
                                             int zoneAllocSuccess, int zoneAllocFallback, int totalSpawns,
                                             @javax.annotation.Nullable String templateId) {
        if (totalSpawns <= 0) return; // Skip empty batches

        float successRate = (float) zoneAllocSuccess / totalSpawns;

        StringBuilder json = new StringBuilder(250);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"zone_allocation\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"wave\":").append(waveNumber).append(",");
        json.append("\"zoneAllocSuccess\":").append(zoneAllocSuccess).append(",");
        json.append("\"zoneAllocFallback\":").append(zoneAllocFallback).append(",");
        json.append("\"totalSpawns\":").append(totalSpawns).append(",");
        json.append("\"successRate\":"); appendFloat2(json, successRate);
        json.append(",\"templateId\":\"").append(escape(templateId != null ? templateId : "")).append("\"");
        json.append("}");

        TelemetryService.INSTANCE.appendEnduranceLine(json.toString());

        if (zoneAllocSuccess > 0) {
            LOGGER.debug("[Endurance] Zone allocation: {}/{} ({:.1f}%) used zone-aware spawning",
                zoneAllocSuccess, totalSpawns, successRate * 100);
        }
    }

    /**
     * P1: Record when entity budget is exceeded during spawn.
     *
     * @param questId The quest session ID
     * @param waveNumber The current wave number
     * @param currentEntities Current entity count
     * @param maxEntities Maximum allowed entities (from template)
     * @param skippedSpawns Number of spawns skipped due to budget
     */
    public void recordEntityBudgetExceeded(UUID questId, int waveNumber,
                                            int currentEntities, int maxEntities, int skippedSpawns) {
        QuestSessionStats stats = questStats.get(questId);

        StringBuilder json = new StringBuilder(280);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"entity_budget_exceeded\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"wave\":").append(waveNumber).append(",");
        json.append("\"currentEntities\":").append(currentEntities).append(",");
        json.append("\"maxEntities\":").append(maxEntities).append(",");
        json.append("\"skippedSpawns\":").append(skippedSpawns);
        appendContext(json, stats);
        json.append("}");

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        } else {
            logNdjsonSkip();
        }

        LOGGER.warn("[Endurance] Entity budget exceeded in wave {}: {}/{} entities, {} spawns skipped",
            waveNumber, currentEntities, maxEntities, skippedSpawns);
    }

    public void recordSpawnHeatmap(UUID questId, ArenaHandle handle, BlockPos pos) {
        recordArenaSpatialEvent("spawn", questId, handle, pos, null);
    }

    public void recordDeathHeatmap(UUID questId, ArenaHandle handle, BlockPos pos, UUID playerId) {
        recordArenaSpatialEvent("death", questId, handle, pos, playerId);
    }

    private void recordArenaSpatialEvent(String eventType, UUID questId, ArenaHandle handle,
                                         BlockPos pos, @javax.annotation.Nullable UUID playerId) {
        if (handle == null || pos == null || handle.bounds() == null) {
            return;
        }
        String templateId = handle.templateId();
        if (templateId == null || templateId.isBlank()) {
            return;
        }
        ArenaHandle.AABB bounds = handle.bounds();
        int gridX = toGridIndex(pos.getX(), bounds.minX(), bounds.maxX(), ARENA_HEATMAP_GRID);
        int gridZ = toGridIndex(pos.getZ(), bounds.minZ(), bounds.maxZ(), ARENA_HEATMAP_GRID);

        DuckDBTelemetryService.INSTANCE.logArenaSpatialEvent(
            templateId,
            handle.templateVersion(),
            questId,
            eventType,
            gridX,
            gridZ,
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            playerId
        );
    }

    private int toGridIndex(int coord, int min, int max, int gridSize) {
        int size = Math.max(1, max - min + 1);
        int relative = coord - min;
        int index = (int) Math.floor((relative * (double) gridSize) / size);
        if (index < 0) return 0;
        if (index >= gridSize) return gridSize - 1;
        return index;
    }

    // ===== COMBO/STYLE EVENTS =====

    /**
     * Record style rank change.
     */
    public void recordStyleRankChange(UUID playerId, UUID questId, ComboSystem.StyleRank oldRank,
                                       ComboSystem.StyleRank newRank, int styleScore, int currentCombo) {
        boolean isRankUp = newRank.ordinal() > oldRank.ordinal();
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(220);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"style_rank_change\",");
        json.append("\"player\":\"").append(playerId).append("\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"oldRank\":\"").append(oldRank.name()).append("\",");
        json.append("\"newRank\":\"").append(newRank.name()).append("\",");
        json.append("\"isRankUp\":").append(isRankUp).append(",");
        json.append("\"styleScore\":").append(styleScore).append(",");
        json.append("\"combo\":").append(currentCombo);
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logComboEvent(playerId, questId,
            isRankUp ? "rank_up" : "rank_down", oldRank.name(), newRank.name(), styleScore, currentCombo,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }

        if (isRankUp) {
            LOGGER.debug("[Endurance] Style rank up: {} -> {} (score: {})", oldRank, newRank, styleScore);
        }
    }

    /**
     * Record combo milestone (5, 10, 25, 50, 100 hits).
     */
    public void recordComboMilestone(UUID playerId, UUID questId, int milestone, int styleEarned,
                                      ComboSystem.StyleRank currentRank) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(200);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"combo_milestone\",");
        json.append("\"player\":\"").append(playerId).append("\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"milestone\":").append(milestone).append(",");
        json.append("\"styleEarned\":").append(styleEarned).append(",");
        json.append("\"rank\":\"").append(currentRank.name()).append("\"");
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        // Note: pointsEarned not available in this method, using styleEarned for both
        DuckDBTelemetryService.INSTANCE.logComboMilestone(playerId, questId, milestone,
            styleEarned, styleEarned, currentRank.name(),
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    /**
     * Record combo break.
     */
    public void recordComboBreak(UUID playerId, UUID questId, int comboLost,
                                  ComboSystem.StyleRank previousRank, ComboSystem.StyleRank newRank,
                                  float damageTaken) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(210);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"combo_break\",");
        json.append("\"player\":\"").append(playerId).append("\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"comboLost\":").append(comboLost).append(",");
        json.append("\"previousRank\":\"").append(previousRank.name()).append("\",");
        json.append("\"newRank\":\"").append(newRank.name()).append("\",");
        json.append("\"damageTaken\":"); appendFloat1(json, damageTaken);
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logComboBreak(playerId, questId, comboLost,
            previousRank.name(), newRank.name(), damageTaken,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    /**
     * Record special action (perfect dodge, parry, counter, etc.).
     */
    public void recordSpecialAction(UUID playerId, UUID questId, ComboSystem.ActionType action,
                                     int pointsEarned, int styleEarned, int currentCombo) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(200);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"special_action\",");
        json.append("\"player\":\"").append(playerId).append("\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"action\":\"").append(action.name()).append("\",");
        json.append("\"points\":").append(pointsEarned).append(",");
        json.append("\"style\":").append(styleEarned).append(",");
        json.append("\"combo\":").append(currentCombo);
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logSpecialAction(playerId, questId, action.name(),
            pointsEarned, styleEarned, currentCombo,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    /**
     * Record resonance chain event (party combo synergy).
     */
    public void recordResonanceChain(UUID questId, String tierName, int playerCount,
                                      long timeSpanMs, int targetEntityId) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(200);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"resonance_chain\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"tier\":\"").append(tierName).append("\",");
        json.append("\"playerCount\":").append(playerCount).append(",");
        json.append("\"timeSpanMs\":").append(timeSpanMs).append(",");
        json.append("\"targetEntity\":").append(targetEntityId);
        appendContext(json, stats);
        json.append("}");

        // Log to NDJSON (resonance events are always logged for analysis)
        TelemetryService.INSTANCE.appendEnduranceLine(json.toString());

        LOGGER.debug("[Endurance] Resonance {} triggered by {} players ({}ms)", tierName, playerCount, timeSpanMs);
    }

    // ===== CONTRACT EVENTS =====

    /**
     * Record blood contract signed.
     */
    public void recordContractSigned(UUID questId, UUID playerId, String contractId, float multiplier) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(200);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"contract_signed\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"player\":\"").append(playerId).append("\",");
        json.append("\"contractId\":\"").append(escape(contractId)).append("\",");
        json.append("\"multiplier\":").append(multiplier);
        appendContext(json, stats);
        json.append("}");

        TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        LOGGER.debug("[Endurance] Contract {} signed by player {} ({}x)", contractId, playerId, multiplier);
    }

    /**
     * Record blood contract violated.
     */
    public void recordContractViolated(UUID questId, UUID playerId, String contractId) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(180);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"contract_violated\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"player\":\"").append(playerId).append("\",");
        json.append("\"contractId\":\"").append(escape(contractId)).append("\"");
        appendContext(json, stats);
        json.append("}");

        TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        LOGGER.debug("[Endurance] Contract {} violated by player {}", contractId, playerId);
    }

    // ===== PERK EVENTS =====

    /**
     * Record perk selection.
     */
    public void recordPerkSelected(UUID playerId, UUID questId, String perkId, String perkName,
                                    PerkSystem.PerkTier tier, PerkSystem.PerkCategory category,
                                    int stackCount, int totalPerks, int waveNumber) {
        QuestSessionStats stats = questId != null ? questStats.get(questId) : null;
        StringBuilder json = new StringBuilder(260);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"perk_selected\",");
        json.append("\"player\":\"").append(playerId).append("\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"perkId\":\"").append(escape(perkId)).append("\",");
        json.append("\"perkName\":\"").append(escape(perkName)).append("\",");
        json.append("\"tier\":\"").append(tier.name()).append("\",");
        json.append("\"category\":\"").append(category.name()).append("\",");
        json.append("\"stackCount\":").append(stackCount).append(",");
        json.append("\"totalPerks\":").append(totalPerks).append(",");
        json.append("\"waveNumber\":").append(waveNumber);
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logPerkSelected(playerId, questId, perkId, perkName,
            tier.name(), category.name(), stackCount, totalPerks, waveNumber,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }

        LOGGER.debug("[Endurance] Perk selected: {} ({}) - stack {} on wave {}",
            perkName, tier.getDisplayName(), stackCount, waveNumber);

        getOrCreateStats(questId).perkSelected(tier);
    }

    /**
     * Record perk choices offered.
     */
    public void recordPerkChoicesOffered(UUID playerId, UUID questId, int waveNumber,
                                          List<PerkSystem.Perk> choices) {
        QuestSessionStats stats = questId != null ? questStats.get(questId) : null;
        StringBuilder choicesJson = new StringBuilder("[");
        for (int i = 0; i < choices.size(); i++) {
            PerkSystem.Perk perk = choices.get(i);
            if (i > 0) choicesJson.append(",");
            choicesJson.append("{\"id\":\"").append(escape(perk.getId()))
                       .append("\",\"tier\":\"").append(perk.getTier().name()).append("\"}");
        }
        choicesJson.append("]");

        StringBuilder json = new StringBuilder(200);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"perk_choices\",");
        json.append("\"player\":\"").append(playerId).append("\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"wave\":").append(waveNumber).append(",");
        json.append("\"choices\":").append(choicesJson);
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logPerkChoices(playerId, questId, waveNumber, choicesJson.toString(),
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    // ===== MUTATOR EVENTS =====

    /**
     * Record mutators assigned to quest.
     */
    public void recordMutatorsAssigned(UUID questId, List<MutatorSystem.Mutator> mutators,
                                        float totalRewardMultiplier) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder mutatorJson = new StringBuilder("[");
        for (int i = 0; i < mutators.size(); i++) {
            MutatorSystem.Mutator m = mutators.get(i);
            if (i > 0) mutatorJson.append(",");
            mutatorJson.append("{\"id\":\"").append(escape(m.getId()))
                       .append("\",\"category\":\"").append(m.getCategory().name()).append("\"}");
        }
        mutatorJson.append("]");

        StringBuilder json = new StringBuilder(220);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"mutators_assigned\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"mutators\":").append(mutatorJson).append(",");
        json.append("\"rewardMultiplier\":"); appendFloat2(json, totalRewardMultiplier);
        json.append(",\"count\":").append(mutators.size());
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logMutatorsAssigned(questId, mutatorJson.toString(),
            totalRewardMultiplier, mutators.size(),
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }

        LOGGER.debug("[Endurance] {} mutators assigned ({}x rewards)", mutators.size(), totalRewardMultiplier);
    }

    /**
     * Record new mutator added mid-quest.
     */
    public void recordMutatorAdded(UUID questId, MutatorSystem.Mutator mutator, int waveNumber) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(200);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"mutator_added\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"mutatorId\":\"").append(escape(mutator.getId())).append("\",");
        json.append("\"category\":\"").append(mutator.getCategory().name()).append("\",");
        json.append("\"wave\":").append(waveNumber);
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logMutatorAdded(questId, mutator.getId(), mutator.getCategory().name(), waveNumber,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    // ===== REWARD EVENTS =====

    /**
     * Record currency earned.
     */
    public void recordCurrencyEarned(UUID playerId, UUID questId, RewardSystem.Currency currency,
                                      int amount, String source) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(220);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"currency_earned\",");
        json.append("\"player\":\"").append(playerId).append("\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"currency\":\"").append(currency.name()).append("\",");
        json.append("\"amount\":").append(amount).append(",");
        json.append("\"source\":\"").append(escape(source)).append("\"");
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logCurrencyEarned(playerId, questId, currency.name(), amount, source,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }

        getOrCreateStats(questId).currencyEarned(currency, amount);
    }

    /**
     * Record loot drop.
     */
    public void recordLootDrop(UUID playerId, UUID questId, String itemId, int count,
                                RewardSystem.LootTier tier) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(200);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"loot_drop\",");
        json.append("\"player\":\"").append(playerId).append("\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"item\":\"").append(escape(itemId)).append("\",");
        json.append("\"count\":").append(count).append(",");
        json.append("\"tier\":\"").append(tier.name()).append("\"");
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logLootDrop(playerId, questId, itemId, count, tier.name(),
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    /**
     * Record achievement unlocked.
     */
    public void recordAchievementUnlocked(UUID playerId, UUID questId, String achievementId,
                                           String achievementName, RewardSystem.Currency rewardCurrency,
                                           int rewardAmount) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(240);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"achievement_unlocked\",");
        json.append("\"player\":\"").append(playerId).append("\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"achievementId\":\"").append(escape(achievementId)).append("\",");
        json.append("\"name\":\"").append(escape(achievementName)).append("\",");
        json.append("\"rewardCurrency\":\"").append(rewardCurrency.name()).append("\",");
        json.append("\"rewardAmount\":").append(rewardAmount);
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logAchievementUnlocked(playerId, questId, achievementId,
            achievementName, rewardCurrency.name(), rewardAmount,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
        LOGGER.info("[Endurance] Achievement unlocked: {}", achievementName);
    }

    /**
     * Record shop purchase.
     */
    public void recordShopPurchase(UUID playerId, String itemId, RewardSystem.Currency currency,
                                    int price, int purchaseCount) {
        StringBuilder json = new StringBuilder(180);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"shop_purchase\",");
        json.append("\"player\":\"").append(playerId).append("\",");
        json.append("\"itemId\":\"").append(escape(itemId)).append("\",");
        json.append("\"currency\":\"").append(currency.name()).append("\",");
        json.append("\"price\":").append(price).append(",");
        json.append("\"purchaseCount\":").append(purchaseCount);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logShopPurchase(playerId, itemId,
            currency.name(), price, purchaseCount);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    // ===== PARTY EVENTS =====

    /**
     * Record party created.
     */
    public void recordPartyCreated(UUID partyId, UUID leaderId, String leaderName, QuestType questType) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"party_created\",\"partyId\":\"%s\"," +
            "\"leaderId\":\"%s\",\"leaderName\":\"%s\",\"questType\":\"%s\"}",
            Instant.now(), partyId, leaderId, escape(leaderName), questType.name()
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logPartyCreated(partyId, leaderId, leaderName, questType.name());

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }

        LOGGER.debug("[Endurance] Party created: {} (leader: {})", partyId, leaderName);
    }

    /**
     * Record party member joined.
     */
    public void recordPartyJoin(UUID partyId, UUID memberId, String memberName, int partySize) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"party_join\",\"partyId\":\"%s\"," +
            "\"memberId\":\"%s\",\"memberName\":\"%s\",\"partySize\":%d}",
            Instant.now(), partyId, memberId, escape(memberName), partySize
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logPartyJoin(partyId, memberId, memberName, partySize);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    /**
     * Record party member left.
     */
    public void recordPartyLeave(UUID partyId, UUID memberId, String reason, int partySize) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"party_leave\",\"partyId\":\"%s\"," +
            "\"memberId\":\"%s\",\"reason\":\"%s\",\"partySize\":%d}",
            Instant.now(), partyId, memberId, escape(reason), partySize
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logPartyLeave(partyId, memberId, reason, partySize);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    /**
     * Record party disbanded.
     */
    public void recordPartyDisbanded(UUID partyId, int memberCount, String reason) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"party_disbanded\",\"partyId\":\"%s\"," +
            "\"memberCount\":%d,\"reason\":\"%s\"}",
            Instant.now(), partyId, memberCount, escape(reason)
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logPartyDisbanded(partyId, memberCount, reason);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    /**
     * Record invite sent.
     */
    public void recordInviteSent(UUID partyId, UUID senderId, UUID targetId) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"party_invite_sent\",\"partyId\":\"%s\"," +
            "\"senderId\":\"%s\",\"targetId\":\"%s\"}",
            Instant.now(), partyId, senderId, targetId
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logInviteSent(partyId, senderId, targetId);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    /**
     * Record invite response.
     */
    public void recordInviteResponse(UUID partyId, UUID targetId, boolean accepted) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"party_invite_response\",\"partyId\":\"%s\"," +
            "\"targetId\":\"%s\",\"accepted\":%b}",
            Instant.now(), partyId, targetId, accepted
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logInviteResponse(partyId, targetId, accepted);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    // ===== BOSS EVENTS =====

    /**
     * Record boss wave start.
     */
    public void recordBossWaveStart(UUID questId, int waveNumber, String bossArchetype,
                                     float bossMaxHealth, int playerCount) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(220);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"boss_wave_start\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"wave\":").append(waveNumber).append(",");
        json.append("\"archetype\":\"").append(escape(bossArchetype)).append("\",");
        json.append("\"maxHealth\":"); appendFloat1(json, bossMaxHealth);
        json.append(",\"playerCount\":").append(playerCount);
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logBossWaveStart(questId, waveNumber, bossArchetype,
            bossMaxHealth, playerCount,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }

        LOGGER.info("[Endurance] Boss wave {} started: {}", waveNumber, bossArchetype);
    }

    /**
     * Record boss ability used.
     */
    public void recordBossAbility(UUID questId, String bossArchetype, String abilityName,
                                   int playersHit, float damageDealt) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(220);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"boss_ability\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"archetype\":\"").append(escape(bossArchetype)).append("\",");
        json.append("\"ability\":\"").append(escape(abilityName)).append("\",");
        json.append("\"playersHit\":").append(playersHit).append(",");
        json.append("\"damage\":"); appendFloat1(json, damageDealt);
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logBossAbility(questId, bossArchetype, abilityName,
            playersHit, damageDealt,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    /**
     * Record boss defeated.
     */
    public void recordBossDefeated(UUID questId, int waveNumber, String bossArchetype,
                                    long fightDurationMs, int bonusPoints, float damageDealtToBoss) {
        QuestSessionStats stats = questStats.get(questId);
        StringBuilder json = new StringBuilder(240);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"boss_defeated\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"wave\":").append(waveNumber).append(",");
        json.append("\"archetype\":\"").append(escape(bossArchetype)).append("\",");
        json.append("\"durationMs\":").append(fightDurationMs).append(",");
        json.append("\"bonusPoints\":").append(bonusPoints).append(",");
        json.append("\"damageDealt\":"); appendFloat1(json, damageDealtToBoss);
        appendContext(json, stats);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logBossDefeated(questId, waveNumber, bossArchetype,
            fightDurationMs, bonusPoints, damageDealtToBoss,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }

        LOGGER.info("[Endurance] Boss {} defeated in {}ms", bossArchetype, fightDurationMs);
    }

    // ===== QUEST LIFECYCLE =====

    /**
     * Record quest start.
     */
    public void recordQuestStart(UUID questId, UUID playerId, String questName, int totalWaves,
                                  boolean isEndless, int playerCount, QuestType questType,
                                  String templateId, Integer templateVersion,
                                  String policyId, Integer policyVersion,
                                  UUID instanceId, UUID arenaId) {
        Instant startTs = Instant.now();

        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"quest_start\",\"questId\":\"%s\",\"playerId\":\"%s\"," +
            "\"questName\":\"%s\",\"totalWaves\":%d,\"isEndless\":%b,\"playerCount\":%d,\"questType\":\"%s\"," +
            "\"templateId\":\"%s\",\"templateVersion\":%d,\"policyId\":\"%s\",\"policyVersion\":%d," +
            "\"instanceId\":\"%s\",\"arenaId\":\"%s\"}",
            startTs, questId, playerId, escape(questName), totalWaves, isEndless, playerCount, questType.name(),
            escape(templateId != null ? templateId : ""), templateVersion != null ? templateVersion.intValue() : 0,
            escape(policyId != null ? policyId : ""), policyVersion != null ? policyVersion.intValue() : 0,
            instanceId != null ? instanceId : "", arenaId != null ? arenaId : ""
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logSessionStart(questId, playerId, null,
            questName, questType.name(), totalWaves, isEndless, playerCount,
            templateId, templateVersion, policyId, policyVersion, instanceId, arenaId);

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }

        LOGGER.info("[Endurance] Quest started: {} ({} waves, {} players)", questName, totalWaves, playerCount);

        // Create session stats with additional tracking data for end event
        QuestSessionStats stats = new QuestSessionStats(questId, playerId);
        stats.questName = questName;
        stats.questType = questType.name();
        stats.totalWaves = totalWaves;
        stats.isEndless = isEndless;
        stats.playerCount = playerCount;
        stats.startTs = startTs;
        stats.templateId = templateId;
        stats.templateVersion = templateVersion;
        stats.policyId = policyId;
        stats.policyVersion = policyVersion;
        stats.instanceId = instanceId;
        stats.arenaId = arenaId;
        questStats.put(questId, stats);
    }

    /**
     * Record countdown start (pre-teleport, pre-wave, respawn).
     */
    public void recordCountdownStarted(UUID questId) {
        getOrCreateStats(questId).countdownStarted();
    }

    /**
     * Record countdown cancellation (abandon/giveup/disconnect).
     */
    public void recordCountdownCancelled(UUID questId) {
        getOrCreateStats(questId).countdownCancelled();
    }

    /**
     * Record giveup during respawn countdown.
     */
    public void recordGiveupDuringRespawn(UUID questId) {
        getOrCreateStats(questId).giveupDuringRespawn();
    }

    /**
     * Record inventory restore outcome.
     */
    public void recordInventoryRestore(UUID questId, boolean success, boolean fallbackUsed) {
        getOrCreateStats(questId).inventoryRestore(success, fallbackUsed);
    }

    /**
     * Record respawn of externally killed mobs.
     */
    public void recordExternalDeathRespawn(UUID questId, int count) {
        getOrCreateStats(questId).externalDeathRespawn(count);
    }

    /**
     * Record wave blocked detection (spawn/respawn failures).
     */
    public void recordWaveBlocked(UUID questId) {
        getOrCreateStats(questId).waveBlockedDetected();
    }

    /**
     * P0-005: Record mob tracking leak detection (mobs died outside hooks).
     * This tracks when the periodic validation finds stale mob entries.
     */
    public void recordMobTrackingLeak(UUID questId, int waveNumber, int staleMobCount) {
        if (questId == null || staleMobCount <= 0) {
            return;
        }
        QuestSessionStats stats = getOrCreateStats(questId);
        stats.mobTrackingLeakDetected(staleMobCount);

        // Log to NDJSON for tracking
        StringBuilder json = new StringBuilder(180);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"mob_tracking_leak\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"wave\":").append(waveNumber).append(",");
        json.append("\"staleMobCount\":").append(staleMobCount);
        appendContext(json, stats);
        json.append("}");

        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }

        LOGGER.warn("[Endurance] Mob tracking leak detected: {} stale mobs in wave {} (quest {})",
            staleMobCount, waveNumber, questId);
    }

    /**
     * Record end-of-quest performance summary (TTK/KPS/DTPS).
     */
    public void recordQuestPerformance(UUID questId, UUID playerId, QuestType questType,
                                        CombatTracker.QuestCombatSession combatSession,
                                        int wavesCompleted) {
        if (combatSession == null) {
            return;
        }

        QuestSessionStats stats = questStats.get(questId);
        long durationMs = combatSession.getSessionDuration();
        int kills = combatSession.getKills();
        double damageDealt = combatSession.getTotalDamageDealt();
        double damageTaken = combatSession.getTotalDamageTaken();
        double avgTtkMs = combatSession.getAverageKillTime();
        double seconds = durationMs > 0 ? durationMs / 1000.0 : 0.0;
        double kps = seconds > 0 ? kills / seconds : 0.0;
        double dtps = seconds > 0 ? damageTaken / seconds : 0.0;
        double dps = seconds > 0 ? damageDealt / seconds : 0.0;

        StringBuilder json = new StringBuilder(260);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"quest_performance\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"playerId\":\"").append(playerId).append("\",");
        json.append("\"questType\":\"").append(questType.name()).append("\",");
        json.append("\"durationMs\":").append(durationMs).append(",");
        json.append("\"wavesCompleted\":").append(wavesCompleted).append(",");
        json.append("\"kills\":").append(kills).append(",");
        json.append("\"damageDealt\":"); appendFloat1(json, (float) damageDealt);
        json.append(",\"damageTaken\":"); appendFloat1(json, (float) damageTaken);
        json.append(",\"avgTtkMs\":"); appendFloat1(json, (float) avgTtkMs);
        json.append(",\"kps\":"); appendFloat2(json, (float) kps);
        json.append(",\"dtps\":"); appendFloat2(json, (float) dtps);
        json.append(",\"dps\":"); appendFloat2(json, (float) dps);
        appendContext(json, stats);
        json.append("}");

        DuckDBTelemetryService.INSTANCE.logEndurancePerformance(
            questId, playerId, questType.name(), durationMs, wavesCompleted, kills,
            damageDealt, damageTaken, avgTtkMs, kps, dtps, dps,
            stats != null ? stats.templateId : null,
            stats != null ? stats.templateVersion : null,
            stats != null ? stats.policyId : null,
            stats != null ? stats.policyVersion : null,
            stats != null ? stats.arenaId : null);

        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    /**
     * Record quest end.
     */
    public void recordQuestEnd(UUID questId, EnduranceQuestState outcome, int wavesCompleted,
                                long sessionDurationMs, int totalKills, float totalDamageDealt,
                                float totalDamageTaken) {
        QuestSessionStats stats = questStats.remove(questId);

        // Use tracked stats when available, fall back to parameters
        int trackedWaves = stats != null ? stats.wavesCompleted : wavesCompleted;
        UUID trackedQuestId = stats != null ? stats.questId : questId;
        String playerIdStr = stats != null && stats.playerId != null ? stats.playerId.toString() : "unknown";

        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"quest_end\",\"questId\":\"%s\",\"playerId\":\"%s\",\"outcome\":\"%s\"," +
            "\"wavesCompleted\":%d,\"trackedWavesCompleted\":%d,\"durationMs\":%d,\"totalKills\":%d," +
            "\"damageDealt\":%.1f,\"damageTaken\":%.1f," +
            "\"perksCommon\":%d,\"perksUncommon\":%d,\"perksRare\":%d,\"perksEpic\":%d,\"perksLegendary\":%d," +
            "\"noDamageWaves\":%d,\"tokensEarned\":%d,\"prestigeEarned\":%d,\"bloodGemsEarned\":%d," +
            "\"countdownStarted\":%d,\"countdownCancelled\":%d,\"giveupDuringRespawn\":%d," +
            "\"inventoryRestoreSuccess\":%d,\"inventoryRestoreFallback\":%d," +
            "\"externalDeathRespawnCount\":%d,\"waveBlockedDetected\":%d," +
            "\"templateId\":\"%s\",\"templateVersion\":%d,\"policyId\":\"%s\",\"policyVersion\":%d," +
            "\"instanceId\":\"%s\",\"arenaId\":\"%s\"}",
            Instant.now(), trackedQuestId, playerIdStr, outcome.name(), wavesCompleted, trackedWaves, sessionDurationMs, totalKills,
            totalDamageDealt, totalDamageTaken,
            stats != null ? stats.perksByTier.getOrDefault(PerkSystem.PerkTier.COMMON, 0) : 0,
            stats != null ? stats.perksByTier.getOrDefault(PerkSystem.PerkTier.UNCOMMON, 0) : 0,
            stats != null ? stats.perksByTier.getOrDefault(PerkSystem.PerkTier.RARE, 0) : 0,
            stats != null ? stats.perksByTier.getOrDefault(PerkSystem.PerkTier.EPIC, 0) : 0,
            stats != null ? stats.perksByTier.getOrDefault(PerkSystem.PerkTier.LEGENDARY, 0) : 0,
            stats != null ? stats.noDamageWaves : 0,
            stats != null ? stats.tokensEarned : 0,
            stats != null ? stats.prestigeEarned : 0,
            stats != null ? stats.bloodGemsEarned : 0,
            stats != null ? stats.countdownStarted : 0,
            stats != null ? stats.countdownCancelled : 0,
            stats != null ? stats.giveupDuringRespawn : 0,
            stats != null ? stats.inventoryRestoreSuccess : 0,
            stats != null ? stats.inventoryRestoreFallback : 0,
            stats != null ? stats.externalDeathRespawnCount : 0,
            stats != null ? stats.waveBlockedDetected : 0,
            escape(stats != null ? stats.templateId : ""),
            stats != null && stats.templateVersion != null ? stats.templateVersion.intValue() : 0,
            escape(stats != null ? stats.policyId : ""),
            stats != null && stats.policyVersion != null ? stats.policyVersion.intValue() : 0,
            stats != null && stats.instanceId != null ? stats.instanceId : "",
            stats != null && stats.arenaId != null ? stats.arenaId : ""
        );

        // DuckDB: Primary storage
        if (stats != null && stats.startTs != null && stats.playerId != null) {
            UUID playerId = stats.playerId;
            DuckDBTelemetryService.INSTANCE.logSessionEnd(
                trackedQuestId, playerId, null,
                stats.questName, stats.questType, stats.totalWaves,
                stats.isEndless, stats.playerCount, stats.startTs,
                outcome.name(), trackedWaves, totalKills,
                (double) totalDamageDealt, (double) totalDamageTaken,
                stats.tokensEarned, stats.prestigeEarned,
                stats.bloodGemsEarned, stats.noDamageWaves,
                stats.templateId, stats.templateVersion,
                stats.policyId, stats.policyVersion,
                stats.instanceId, stats.arenaId,
                stats.countdownStarted, stats.countdownCancelled,
                stats.giveupDuringRespawn,
                stats.inventoryRestoreSuccess, stats.inventoryRestoreFallback,
                stats.externalDeathRespawnCount, stats.waveBlockedDetected
            );
        }

        // NDJSON: Fallback only
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }

        LOGGER.info("[Endurance] Quest ended: {} - {} waves in {}ms", outcome, trackedWaves, sessionDurationMs);
    }

    // ===== GAMIFICATION EVENTS =====

    /**
     * Record badge/challenge unlocked.
     */
    public void recordBadgeUnlocked(UUID playerId, String badgeId, String badgeName, int pointsAwarded) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"badge_unlocked\",\"player\":\"%s\"," +
            "\"badgeId\":\"%s\",\"name\":\"%s\",\"points\":%d}",
            Instant.now(), playerId, escape(badgeId), escape(badgeName), pointsAwarded
        );

        // NDJSON: Fallback only (no DuckDB mapping for badge yet)
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    /**
     * Record leaderboard position change.
     */
    public void recordLeaderboardChange(UUID playerId, String leaderboardType, int oldRank,
                                         int newRank, int score) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"leaderboard_change\",\"player\":\"%s\"," +
            "\"leaderboard\":\"%s\",\"oldRank\":%d,\"newRank\":%d,\"score\":%d}",
            Instant.now(), playerId, escape(leaderboardType), oldRank, newRank, score
        );

        // NDJSON: Fallback only (no DuckDB mapping for leaderboard yet)
        if (DuckDBConfig.isNdjsonFallbackEnabled() || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    // ===== SESSION STATS HELPER =====

    private QuestSessionStats getOrCreateStats(UUID questId) {
        return questStats.computeIfAbsent(questId, id -> new QuestSessionStats(id, null));
    }

    private static void appendContext(StringBuilder json, @javax.annotation.Nullable QuestSessionStats stats) {
        json.append(",\"templateId\":\"").append(escape(stats != null ? stats.templateId : null)).append("\"");
        Integer templateVersion = stats != null ? stats.templateVersion : null;
        json.append(",\"templateVersion\":").append(templateVersion != null ? templateVersion.intValue() : 0);
        json.append(",\"policyId\":\"").append(escape(stats != null ? stats.policyId : null)).append("\"");
        Integer policyVersion = stats != null ? stats.policyVersion : null;
        json.append(",\"policyVersion\":").append(policyVersion != null ? policyVersion.intValue() : 0);
        UUID instanceId = stats != null ? stats.instanceId : null;
        json.append(",\"instanceId\":\"").append(instanceId != null ? instanceId : "").append("\"");
        UUID arenaId = stats != null ? stats.arenaId : null;
        json.append(",\"arenaId\":\"").append(arenaId != null ? arenaId : "").append("\"");
    }

    /**
     * Internal stats aggregation per quest session.
     */
    private static class QuestSessionStats {
        final UUID questId;
        final @javax.annotation.Nullable UUID playerId;
        int wavesCompleted = 0;
        int noDamageWaves = 0;
        final Map<PerkSystem.PerkTier, Integer> perksByTier = new EnumMap<>(PerkSystem.PerkTier.class);
        int tokensEarned = 0;
        int prestigeEarned = 0;
        int bloodGemsEarned = 0;
        int countdownStarted = 0;
        int countdownCancelled = 0;
        int giveupDuringRespawn = 0;
        int inventoryRestoreSuccess = 0;
        int inventoryRestoreFallback = 0;
        int externalDeathRespawnCount = 0;
        int waveBlockedDetected = 0;

        // Additional fields for DuckDB session end tracking
        @javax.annotation.Nullable String questName;
        @javax.annotation.Nullable String questType;
        int totalWaves;
        boolean isEndless;
        int playerCount;
        @javax.annotation.Nullable Instant startTs;
        @javax.annotation.Nullable String templateId;
        @javax.annotation.Nullable Integer templateVersion;
        @javax.annotation.Nullable String policyId;
        @javax.annotation.Nullable Integer policyVersion;
        @javax.annotation.Nullable UUID instanceId;
        @javax.annotation.Nullable UUID arenaId;

        QuestSessionStats(UUID questId, @javax.annotation.Nullable UUID playerId) {
            this.questId = questId;
            this.playerId = playerId;
        }

        void waveStarted() {
            // Track wave start
        }

        void waveCompleted(boolean noDamage) {
            wavesCompleted++;
            if (noDamage) noDamageWaves++;
        }

        void perkSelected(PerkSystem.PerkTier tier) {
            perksByTier.merge(tier, 1, (a, b) -> a + b);
        }

        void currencyEarned(RewardSystem.Currency currency, int amount) {
            switch (currency) {
                case TOKENS -> tokensEarned += amount;
                case COINS, GEMS -> {
                }
                case PRESTIGE -> prestigeEarned += amount;
                case BLOOD_GEMS -> bloodGemsEarned += amount;
            }
        }

        void countdownStarted() {
            countdownStarted++;
        }

        void countdownCancelled() {
            countdownCancelled++;
        }

        void giveupDuringRespawn() {
            giveupDuringRespawn++;
        }

        void inventoryRestore(boolean success, boolean fallbackUsed) {
            if (success) {
                inventoryRestoreSuccess++;
            }
            if (fallbackUsed) {
                inventoryRestoreFallback++;
            }
        }

        void externalDeathRespawn(int count) {
            externalDeathRespawnCount += Math.max(0, count);
        }

        void waveBlockedDetected() {
            waveBlockedDetected++;
        }

        void mobTrackingLeakDetected(int staleMobCount) {
            // Aggregate mob tracking leaks per session for debugging
            // Could add a field if needed for final stats
            LOGGER.debug("[EnduranceStats] Mob tracking leak: {} stale mobs", staleMobCount);
        }
    }

    // ===== CLEANUP =====

    /**
     * Cleanup stats for a quest.
     */
    public void cleanupQuest(UUID questId) {
        questStats.remove(questId);
    }

    /**
     * Clear all stats.
     */
    public void clear() {
        questStats.clear();
    }

    // ===== UTILITIES =====

    private static String escape(@javax.annotation.Nullable String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ============================================
    // PERFORMANCE: Fast number formatting helpers
    // ============================================

    /** Append float with 1 decimal place without String.format() */
    private static void appendFloat1(StringBuilder sb, float value) {
        long scaled = Math.round(value * 10.0);
        long intPart = scaled / 10;
        long decPart = Math.abs(scaled % 10);
        sb.append(intPart).append('.').append(decPart);
    }

    /** Append float with 2 decimal places without String.format() */
    private static void appendFloat2(StringBuilder sb, float value) {
        long scaled = Math.round(value * 100.0);
        long intPart = scaled / 100;
        long decPart = Math.abs(scaled % 100);
        sb.append(intPart).append('.');
        if (decPart < 10) sb.append('0');
        sb.append(decPart);
    }
}
