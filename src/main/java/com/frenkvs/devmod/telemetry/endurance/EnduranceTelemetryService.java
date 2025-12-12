package com.frenkvs.devmod.telemetry.endurance;

import com.frenkvs.devmod.endurance.*;
import com.frenkvs.devmod.telemetry.TelemetryService;
import com.frenkvs.devmod.telemetry.duckdb.DuckDBConfig;
import com.frenkvs.devmod.telemetry.duckdb.DuckDBTelemetryService;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized telemetry service for Endurance Quest systems.
 *
 * Tracks:
 * - Wave progression (start, end, kills, timing)
 * - Combo/Style system (rank changes, milestones, breaks)
 * - Perk selection (choices, tier distribution)
 * - Mutator system (active mutators, effects)
 * - Reward system (currency, loot, achievements)
 * - Party events (create, join, leave)
 * - Boss encounters (archetypes, abilities, phases)
 *
 * All events are written to endurance.ndjson via TelemetryService.
 */
public class EnduranceTelemetryService {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final EnduranceTelemetryService INSTANCE = new EnduranceTelemetryService();

    // Per-quest session stats for aggregation
    private final Map<UUID, QuestSessionStats> questStats = new ConcurrentHashMap<>();

    // DIAGNOSTIC: Track NDJSON skip events (rate-limited logging)
    private static final AtomicInteger ndjsonSkipCount = new AtomicInteger(0);
    private static final AtomicLong lastSkipLogTime = new AtomicLong(0);
    private static final long SKIP_LOG_INTERVAL_MS = 30_000; // Log at most once per 30 seconds

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

        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"wave_start\",\"questId\":\"%s\",\"wave\":%d,\"mobCount\":%d," +
            "\"playerCount\":%d,\"questType\":\"%s\",\"modifiers\":\"%s\"}",
            Instant.now(), questId, waveNumber, mobCount, playerCount, questType.name(), modifierList
        );

        // DuckDB: Primary storage
        String[] modifierArray = modifiers.stream().map(Enum::name).toArray(String[]::new);
        DuckDBTelemetryService.INSTANCE.logWaveStart(questId, waveNumber, mobCount,
            playerCount, questType.name(), modifierArray);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        } else {
            logNdjsonSkip();
        }

        LOGGER.debug("[Endurance] Wave {} started with {} mobs", waveNumber, mobCount);

        // Update session stats
        getOrCreateStats(questId).waveStarted(waveNumber);
    }

    /**
     * Record wave completion.
     * PERFORMANCE: Uses StringBuilder instead of String.format (~10x faster).
     */
    public void recordWaveComplete(UUID questId, int waveNumber, int mobsKilled, long durationMs,
                                    boolean noDamage, float killsPerSecond) {
        StringBuilder json = new StringBuilder(180);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"wave_complete\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"wave\":").append(waveNumber).append(",");
        json.append("\"mobsKilled\":").append(mobsKilled).append(",");
        json.append("\"durationMs\":").append(durationMs).append(",");
        json.append("\"noDamage\":").append(noDamage).append(",");
        json.append("\"killsPerSecond\":"); appendFloat2(json, killsPerSecond);
        json.append("}");

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logWaveComplete(questId, waveNumber, mobsKilled,
            durationMs, noDamage, killsPerSecond);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }

        LOGGER.debug("[Endurance] Wave {} complete: {} kills in {}ms", waveNumber, mobsKilled, durationMs);

        getOrCreateStats(questId).waveCompleted(waveNumber, durationMs, noDamage);
    }

    /**
     * Record mob kill during wave.
     * PERFORMANCE: Uses StringBuilder instead of String.format (~10x faster).
     * This is a hot path - called on every mob kill.
     */
    public void recordWaveKill(UUID questId, int waveNumber, String mobType, boolean isElite,
                                String killerWeapon, float damageDealt) {
        StringBuilder json = new StringBuilder(200);
        json.append("{\"ts\":\"").append(Instant.now()).append("\",");
        json.append("\"type\":\"wave_kill\",");
        json.append("\"questId\":\"").append(questId).append("\",");
        json.append("\"wave\":").append(waveNumber).append(",");
        json.append("\"mobType\":\"").append(escape(mobType)).append("\",");
        json.append("\"isElite\":").append(isElite).append(",");
        json.append("\"weapon\":\"").append(escape(killerWeapon)).append("\",");
        json.append("\"damage\":"); appendFloat1(json, damageDealt);
        json.append("}");

        // DuckDB: Primary storage (HOT PATH - high volume)
        DuckDBTelemetryService.INSTANCE.logWaveKill(questId, waveNumber, mobType,
            isElite, killerWeapon, damageDealt);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json.toString());
        }
    }

    // ===== COMBO/STYLE EVENTS =====

    /**
     * Record style rank change.
     */
    public void recordStyleRankChange(UUID playerId, UUID questId, ComboSystem.StyleRank oldRank,
                                       ComboSystem.StyleRank newRank, int styleScore, int currentCombo) {
        boolean isRankUp = newRank.ordinal() > oldRank.ordinal();
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"style_rank_change\",\"player\":\"%s\",\"questId\":\"%s\"," +
            "\"oldRank\":\"%s\",\"newRank\":\"%s\",\"isRankUp\":%b,\"styleScore\":%d,\"combo\":%d}",
            Instant.now(), playerId, questId, oldRank.name(), newRank.name(), isRankUp, styleScore, currentCombo
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logComboEvent(playerId, questId,
            isRankUp ? "rank_up" : "rank_down", oldRank.name(), newRank.name(), styleScore, currentCombo);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
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
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"combo_milestone\",\"player\":\"%s\",\"questId\":\"%s\"," +
            "\"milestone\":%d,\"styleEarned\":%d,\"rank\":\"%s\"}",
            Instant.now(), playerId, questId, milestone, styleEarned, currentRank.name()
        );

        // DuckDB: Primary storage
        // Note: pointsEarned not available in this method, using styleEarned for both
        DuckDBTelemetryService.INSTANCE.logComboMilestone(playerId, questId, milestone,
            styleEarned, styleEarned, currentRank.name());

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }
    }

    /**
     * Record combo break.
     */
    public void recordComboBreak(UUID playerId, UUID questId, int comboLost,
                                  ComboSystem.StyleRank previousRank, ComboSystem.StyleRank newRank,
                                  float damageTaken) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"combo_break\",\"player\":\"%s\",\"questId\":\"%s\"," +
            "\"comboLost\":%d,\"previousRank\":\"%s\",\"newRank\":\"%s\",\"damageTaken\":%.1f}",
            Instant.now(), playerId, questId, comboLost, previousRank.name(), newRank.name(), damageTaken
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logComboBreak(playerId, questId, comboLost,
            previousRank.name(), newRank.name(), damageTaken);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }
    }

    /**
     * Record special action (perfect dodge, parry, counter, etc.).
     */
    public void recordSpecialAction(UUID playerId, UUID questId, ComboSystem.ActionType action,
                                     int pointsEarned, int styleEarned, int currentCombo) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"special_action\",\"player\":\"%s\",\"questId\":\"%s\"," +
            "\"action\":\"%s\",\"points\":%d,\"style\":%d,\"combo\":%d}",
            Instant.now(), playerId, questId, action.name(), pointsEarned, styleEarned, currentCombo
        );

        // NDJSON: Fallback only (no DuckDB mapping for special_action yet)
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }
    }

    // ===== PERK EVENTS =====

    /**
     * Record perk selection.
     */
    public void recordPerkSelected(UUID playerId, UUID questId, String perkId, String perkName,
                                    PerkSystem.PerkTier tier, PerkSystem.PerkCategory category,
                                    int stackCount, int totalPerks) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"perk_selected\",\"player\":\"%s\",\"questId\":\"%s\"," +
            "\"perkId\":\"%s\",\"perkName\":\"%s\",\"tier\":\"%s\",\"category\":\"%s\"," +
            "\"stackCount\":%d,\"totalPerks\":%d}",
            Instant.now(), playerId, questId, escape(perkId), escape(perkName),
            tier.name(), category.name(), stackCount, totalPerks
        );

        // DuckDB: Primary storage
        // Note: waveNumber not available here, passing 0 as placeholder
        DuckDBTelemetryService.INSTANCE.logPerkSelected(playerId, questId, perkId, perkName,
            tier.name(), category.name(), stackCount, totalPerks, 0);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }

        LOGGER.debug("[Endurance] Perk selected: {} ({}) - stack {}", perkName, tier.displayName, stackCount);

        getOrCreateStats(questId).perkSelected(tier);
    }

    /**
     * Record perk choices offered.
     */
    public void recordPerkChoicesOffered(UUID playerId, UUID questId, int waveNumber,
                                          List<PerkSystem.Perk> choices) {
        StringBuilder choicesJson = new StringBuilder("[");
        for (int i = 0; i < choices.size(); i++) {
            PerkSystem.Perk perk = choices.get(i);
            if (i > 0) choicesJson.append(",");
            choicesJson.append(String.format("{\"id\":\"%s\",\"tier\":\"%s\"}",
                escape(perk.id), perk.tier.name()));
        }
        choicesJson.append("]");

        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"perk_choices\",\"player\":\"%s\",\"questId\":\"%s\"," +
            "\"wave\":%d,\"choices\":%s}",
            Instant.now(), playerId, questId, waveNumber, choicesJson
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logPerkChoices(playerId, questId, waveNumber, choicesJson.toString());

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }
    }

    // ===== MUTATOR EVENTS =====

    /**
     * Record mutators assigned to quest.
     */
    public void recordMutatorsAssigned(UUID questId, List<MutatorSystem.Mutator> mutators,
                                        float totalRewardMultiplier) {
        StringBuilder mutatorJson = new StringBuilder("[");
        for (int i = 0; i < mutators.size(); i++) {
            MutatorSystem.Mutator m = mutators.get(i);
            if (i > 0) mutatorJson.append(",");
            mutatorJson.append(String.format("{\"id\":\"%s\",\"category\":\"%s\"}",
                escape(m.id), m.category.name()));
        }
        mutatorJson.append("]");

        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"mutators_assigned\",\"questId\":\"%s\"," +
            "\"mutators\":%s,\"rewardMultiplier\":%.2f,\"count\":%d}",
            Instant.now(), questId, mutatorJson, totalRewardMultiplier, mutators.size()
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logMutatorsAssigned(questId, mutatorJson.toString(),
            totalRewardMultiplier, mutators.size());

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }

        LOGGER.debug("[Endurance] {} mutators assigned ({}x rewards)", mutators.size(), totalRewardMultiplier);
    }

    /**
     * Record new mutator added mid-quest.
     */
    public void recordMutatorAdded(UUID questId, MutatorSystem.Mutator mutator, int waveNumber) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"mutator_added\",\"questId\":\"%s\"," +
            "\"mutatorId\":\"%s\",\"category\":\"%s\",\"wave\":%d}",
            Instant.now(), questId, escape(mutator.id), mutator.category.name(), waveNumber
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logMutatorAdded(questId, mutator.id, mutator.category.name(), waveNumber);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }
    }

    // ===== REWARD EVENTS =====

    /**
     * Record currency earned.
     */
    public void recordCurrencyEarned(UUID playerId, UUID questId, RewardSystem.Currency currency,
                                      int amount, String source) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"currency_earned\",\"player\":\"%s\",\"questId\":\"%s\"," +
            "\"currency\":\"%s\",\"amount\":%d,\"source\":\"%s\"}",
            Instant.now(), playerId, questId, currency.name(), amount, escape(source)
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logCurrencyEarned(playerId, questId, currency.name(), amount, source);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }

        getOrCreateStats(questId).currencyEarned(currency, amount);
    }

    /**
     * Record loot drop.
     */
    public void recordLootDrop(UUID playerId, UUID questId, String itemId, int count,
                                RewardSystem.LootTier tier) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"loot_drop\",\"player\":\"%s\",\"questId\":\"%s\"," +
            "\"item\":\"%s\",\"count\":%d,\"tier\":\"%s\"}",
            Instant.now(), playerId, questId, escape(itemId), count, tier.name()
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logLootDrop(playerId, questId, itemId, count, tier.name());

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }
    }

    /**
     * Record achievement unlocked.
     */
    public void recordAchievementUnlocked(UUID playerId, UUID questId, String achievementId,
                                           String achievementName, RewardSystem.Currency rewardCurrency,
                                           int rewardAmount) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"achievement_unlocked\",\"player\":\"%s\",\"questId\":\"%s\"," +
            "\"achievementId\":\"%s\",\"name\":\"%s\",\"rewardCurrency\":\"%s\",\"rewardAmount\":%d}",
            Instant.now(), playerId, questId, escape(achievementId), escape(achievementName),
            rewardCurrency.name(), rewardAmount
        );

        // NDJSON: Fallback only (no DuckDB mapping for achievement yet)
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }
        LOGGER.info("[Endurance] Achievement unlocked: {}", achievementName);
    }

    /**
     * Record shop purchase.
     */
    public void recordShopPurchase(UUID playerId, String itemId, RewardSystem.Currency currency,
                                    int price, int purchaseCount) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"shop_purchase\",\"player\":\"%s\"," +
            "\"itemId\":\"%s\",\"currency\":\"%s\",\"price\":%d,\"purchaseCount\":%d}",
            Instant.now(), playerId, escape(itemId), currency.name(), price, purchaseCount
        );

        // NDJSON: Fallback only (no DuckDB mapping for shop_purchase yet)
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
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
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
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
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
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
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
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
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
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
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
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
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }
    }

    // ===== BOSS EVENTS =====

    /**
     * Record boss wave start.
     */
    public void recordBossWaveStart(UUID questId, int waveNumber, String bossArchetype,
                                     float bossMaxHealth, int playerCount) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"boss_wave_start\",\"questId\":\"%s\",\"wave\":%d," +
            "\"archetype\":\"%s\",\"maxHealth\":%.1f,\"playerCount\":%d}",
            Instant.now(), questId, waveNumber, escape(bossArchetype), bossMaxHealth, playerCount
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logBossWaveStart(questId, waveNumber, bossArchetype,
            bossMaxHealth, playerCount);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }

        LOGGER.info("[Endurance] Boss wave {} started: {}", waveNumber, bossArchetype);
    }

    /**
     * Record boss ability used.
     */
    public void recordBossAbility(UUID questId, String bossArchetype, String abilityName,
                                   int playersHit, float damageDealt) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"boss_ability\",\"questId\":\"%s\"," +
            "\"archetype\":\"%s\",\"ability\":\"%s\",\"playersHit\":%d,\"damage\":%.1f}",
            Instant.now(), questId, escape(bossArchetype), escape(abilityName), playersHit, damageDealt
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logBossAbility(questId, bossArchetype, abilityName,
            playersHit, damageDealt);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }
    }

    /**
     * Record boss defeated.
     */
    public void recordBossDefeated(UUID questId, int waveNumber, String bossArchetype,
                                    long fightDurationMs, int bonusPoints, float damageDealtToBoss) {
        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"boss_defeated\",\"questId\":\"%s\",\"wave\":%d," +
            "\"archetype\":\"%s\",\"durationMs\":%d,\"bonusPoints\":%d,\"damageDealt\":%.1f}",
            Instant.now(), questId, waveNumber, escape(bossArchetype), fightDurationMs,
            bonusPoints, damageDealtToBoss
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logBossDefeated(questId, waveNumber, bossArchetype,
            fightDurationMs, bonusPoints, damageDealtToBoss);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }

        LOGGER.info("[Endurance] Boss {} defeated in {}ms", bossArchetype, fightDurationMs);
    }

    // ===== QUEST LIFECYCLE =====

    /**
     * Record quest start.
     */
    public void recordQuestStart(UUID questId, UUID playerId, String questName, int totalWaves,
                                  boolean isEndless, int playerCount, QuestType questType) {
        Instant startTs = Instant.now();

        String json = String.format(
            "{\"ts\":\"%s\",\"type\":\"quest_start\",\"questId\":\"%s\",\"playerId\":\"%s\"," +
            "\"questName\":\"%s\",\"totalWaves\":%d,\"isEndless\":%b,\"playerCount\":%d,\"questType\":\"%s\"}",
            startTs, questId, playerId, escape(questName), totalWaves, isEndless, playerCount, questType.name()
        );

        // DuckDB: Primary storage
        DuckDBTelemetryService.INSTANCE.logSessionStart(questId, playerId, null,
            questName, questType.name(), totalWaves, isEndless, playerCount);

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
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
        questStats.put(questId, stats);
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
            "\"noDamageWaves\":%d,\"tokensEarned\":%d,\"prestigeEarned\":%d,\"bloodGemsEarned\":%d}",
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
            stats != null ? stats.bloodGemsEarned : 0
        );

        // DuckDB: Primary storage
        if (stats != null && stats.startTs != null) {
            DuckDBTelemetryService.INSTANCE.logSessionEnd(
                trackedQuestId, stats.playerId, null,
                stats.questName, stats.questType, stats.totalWaves,
                stats.isEndless, stats.playerCount, stats.startTs,
                outcome.name(), trackedWaves, totalKills,
                (double) totalDamageDealt, (double) totalDamageTaken,
                stats.tokensEarned, stats.prestigeEarned,
                stats.bloodGemsEarned, stats.noDamageWaves
            );
        }

        // NDJSON: Fallback only
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
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
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
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
        if (DuckDBConfig.NDJSON_FALLBACK || !DuckDBTelemetryService.INSTANCE.isEnabled()) {
            TelemetryService.INSTANCE.appendEnduranceLine(json);
        }
    }

    // ===== SESSION STATS HELPER =====

    private QuestSessionStats getOrCreateStats(UUID questId) {
        return questStats.computeIfAbsent(questId, id -> new QuestSessionStats(id, null));
    }

    /**
     * Internal stats aggregation per quest session.
     */
    private static class QuestSessionStats {
        final UUID questId;
        final UUID playerId;
        int wavesCompleted = 0;
        int noDamageWaves = 0;
        final Map<PerkSystem.PerkTier, Integer> perksByTier = new EnumMap<>(PerkSystem.PerkTier.class);
        int tokensEarned = 0;
        int prestigeEarned = 0;
        int bloodGemsEarned = 0;

        // Additional fields for DuckDB session end tracking
        String questName;
        String questType;
        int totalWaves;
        boolean isEndless;
        int playerCount;
        Instant startTs;

        QuestSessionStats(UUID questId, UUID playerId) {
            this.questId = questId;
            this.playerId = playerId;
        }

        void waveStarted(int waveNumber) {
            // Track wave start
        }

        void waveCompleted(int waveNumber, long durationMs, boolean noDamage) {
            wavesCompleted++;
            if (noDamage) noDamageWaves++;
        }

        void perkSelected(PerkSystem.PerkTier tier) {
            perksByTier.merge(tier, 1, (a, b) -> a + b);
        }

        void currencyEarned(RewardSystem.Currency currency, int amount) {
            switch (currency) {
                case TOKENS -> tokensEarned += amount;
                case PRESTIGE -> prestigeEarned += amount;
                case BLOOD_GEMS -> bloodGemsEarned += amount;
            }
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

    private static String escape(String s) {
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
