package com.devmod.telemetry.duckdb.packets;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;

import com.devmod.telemetry.duckdb.DuckDBTelemetryService;
import com.devmod.telemetry.duckdb.aggregation.AggregationConfig;
import com.devmod.telemetry.duckdb.aggregation.SnapshotSampler.SnapshotData;
import com.devmod.telemetry.duckdb.aggregation.TelemetryAggregator;
import com.devmod.telemetry.duckdb.aggregation.TelemetryAggregatorRegistry;
import com.devmod.telemetry.duckdb.packets.TelemetryBatchPayload.CompressedEvent;
import com.devmod.telemetry.duckdb.packets.TelemetryBatchPayload.EventType;

public class TelemetryPacketHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final TelemetryPacketHandler INSTANCE = new TelemetryPacketHandler();

    // Rate limiting configuration
    private static final int MAX_EVENTS_PER_SECOND = 100;
    private static final long RATE_WINDOW_MS = 1000;
    private static final long MAX_TIMESTAMP_DRIFT_MS = 60_000; // 1 minute

    // Per-player rate limiting state
    private final Map<UUID, RateLimitState> playerRateLimits = new ConcurrentHashMap<>();

    private TelemetryPacketHandler() {}

    /**
     * Handle incoming telemetry batch from a client.
     *
     * @param player The player who sent the batch
     * @param payload The telemetry batch payload
     */
    public void handleBatch(ServerPlayer player, TelemetryBatchPayload payload) {
        UUID playerId = player.getUUID();
        String playerName = player.getName().getString();

        // Check rate limit
        RateLimitState rateLimit = playerRateLimits.computeIfAbsent(playerId, id -> new RateLimitState());
        int eventCount = payload.events().size();

        if (!rateLimit.tryConsume(eventCount)) {
            LOGGER.warn("[TelemetryPacket] Rate limit exceeded for player {} ({} events blocked)",
                playerName, eventCount);
            return;
        }

        // Validate batch timestamp
        long now = System.currentTimeMillis();
        long batchTs = payload.batchTimestamp();

        if (batchTs > now + MAX_TIMESTAMP_DRIFT_MS) {
            LOGGER.warn("[TelemetryPacket] Future timestamp from player {}: {} (now: {})",
                playerName, batchTs, now);
            return;
        }

        if (batchTs < now - MAX_TIMESTAMP_DRIFT_MS * 60) { // 1 hour old max
            LOGGER.warn("[TelemetryPacket] Very old timestamp from player {}: {} (now: {})",
                playerName, batchTs, now);
            return;
        }

        // Process each event
        int processed = 0;
        int failed = 0;

        for (CompressedEvent event : payload.events()) {
            try {
                processEvent(player, batchTs, event);
                processed++;
            } catch (Exception e) {
                failed++;
                LOGGER.debug("[TelemetryPacket] Failed to process event type {} from {}: {}",
                    event.typeId(), playerName, e.getMessage());
            }
        }

        if (processed > 0) {
            LOGGER.debug("[TelemetryPacket] Processed {} events from {} ({} failed)",
                processed, playerName, failed);
        }
    }

    /**
     * Process a single telemetry event.
     */
    private void processEvent(ServerPlayer player, long batchTimestamp, CompressedEvent event) {
        EventType type = EventType.fromId(event.typeId());
        if (type == null) {
            LOGGER.debug("[TelemetryPacket] Unknown event type: {}", event.typeId());
            return;
        }

        long eventTimestamp = event.getFullTimestamp(batchTimestamp);
        String jsonData = event.getDataAsString();

        // Route to appropriate handler based on event type
        DuckDBTelemetryService duckDB = DuckDBTelemetryService.INSTANCE;
        if (!duckDB.isEnabled()) {
            return;
        }

        switch (type) {
            case COMBAT_HIT -> processCombatHit(player, jsonData);
            case COMBAT_DEATH -> processCombatDeath(player, jsonData);
            case COMBAT_HEAL -> processCombatHeal(player, jsonData);
            case COMBAT_SPAWN -> processCombatSpawn(player, jsonData);
            case PLAYER_SNAPSHOT -> processPlayerSnapshot(player, jsonData);
            case PLAYER_ABILITY -> processPlayerAbility(player, jsonData);
            case PLAYER_ATTRIBUTE_CHANGE -> processPlayerAttributeChange(player, jsonData);
            case HEATMAP_UPDATE -> processHeatmapUpdate(player, jsonData);
            case ROOM_TRANSITION -> processRoomTransition(player, jsonData);
            case ENDURANCE_SESSION -> processEnduranceSession(player, eventTimestamp, jsonData);
            case ENDURANCE_WAVE -> processEnduranceWave(player, jsonData);
            case ENDURANCE_KILL -> processEnduranceKill(player, jsonData);
            case ENDURANCE_COMBO -> processEnduranceCombo(player, jsonData);
            case ENDURANCE_PERK -> processEndurancePerk(player, jsonData);
            case ENDURANCE_REWARD -> processEnduranceReward(player, jsonData);
            case PERFORMANCE_SAMPLE -> {
                // Performance samples from clients are logged but not stored
                // Server has its own authoritative performance metrics
                LOGGER.trace("[TelemetryPacket] Client performance sample received (ignored)");
            }
            case UI_SCREEN_OPEN, UI_SCREEN_CLOSE, UI_ACTION -> {
                // UI events are client-only; ignore on server.
                LOGGER.trace("[TelemetryPacket] UI event received (ignored)");
            }
        }
    }

    // ============================================
    // EVENT PROCESSORS
    // ============================================

    private void processCombatHit(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Combat hit from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
        EnduranceContext ctx = getEnduranceContext(json);

        double damage = json.has("damage") ? json.get("damage").getAsDouble() : 0;
        boolean isMiss = json.has("isMiss") && json.get("isMiss").getAsBoolean();
        String weapon = getStringOrNull(json, "attackerType"); // Use attacker type as weapon proxy
        String targetType = getStringOrNull(json, "targetType");
        String safeWeapon = (weapon == null || weapon.isBlank()) ? "unknown" : weapon;
        String safeTargetType = (targetType == null || targetType.isBlank()) ? "unknown" : targetType;
        Double hpAfter = getDoubleOrNull(json, "hpAfter");
        boolean isKill = hpAfter != null && hpAfter <= 0;
        boolean isCritical = json.has("isCritical") && json.get("isCritical").getAsBoolean();

        // Route through aggregation if enabled
        if (AggregationConfig.AGGREGATION_ENABLED) {
            TelemetryAggregator agg = TelemetryAggregatorRegistry.INSTANCE.getAggregator(player.getUUID());
            if (agg != null) {
                if (isMiss) {
                    agg.processMiss();
                } else {
                    // Returns false if aggregated (don't write immediately)
                    if (!agg.processHit(damage, isKill, isCritical, safeWeapon, safeTargetType)) {
                        return; // Aggregated, don't write to DB now
                    }
                }
            }
        }

        // BYPASS: Write immediately (aggregation disabled or no aggregator)
        DuckDBTelemetryService.INSTANCE.logHit(
            getStringOrNull(json, "room"),
            getStringOrNull(json, "world"),
            ctx.templateId,
            ctx.templateVersion,
            ctx.policyId,
            ctx.policyVersion,
            ctx.arenaId,
            ctx.sessionId,
            getStringOrNull(json, "attackerName"),
            getStringOrNull(json, "attackerType"),
            getStringOrNull(json, "targetName"),
            targetType,
            damage,
            getStringOrNull(json, "damageType"),
            getDoubleOrNull(json, "hpBefore"),
            hpAfter,
            getStringOrNull(json, "bodyPart"),
            getDoubleOrNull(json, "distance"),
            getDoubleOrNull(json, "armorPenBonus"),
            isMiss,
            json.has("isHazard") && json.get("isHazard").getAsBoolean(),
            getStringOrNull(json, "hazardType"),
            getStringOrNull(json, "attackerState"),
            getStringOrNull(json, "targetState")
        );
    }

    private void processCombatDeath(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Combat death from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
        EnduranceContext ctx = getEnduranceContext(json);

        // Update LVC for player death (always, regardless of aggregation)
        if (AggregationConfig.LVC_ENABLED) {
            String targetType = getStringOrNull(json, "targetType");
            // Only update LVC if this is the player dying
            if ("player".equalsIgnoreCase(targetType)) {
                TelemetryAggregator agg = TelemetryAggregatorRegistry.INSTANCE.getAggregator(player.getUUID());
                if (agg != null) {
                    agg.processDeath();
                }
            }
        }

        // Deaths always BYPASS aggregation - write immediately
        DuckDBTelemetryService.INSTANCE.logDeath(
            getStringOrNull(json, "room"),
            getStringOrNull(json, "world"),
            ctx.templateId,
            ctx.templateVersion,
            ctx.policyId,
            ctx.policyVersion,
            ctx.arenaId,
            ctx.sessionId,
            getStringOrNull(json, "targetName"),
            getStringOrNull(json, "targetType"),
            getStringOrNull(json, "cause"),
            getLongOrNull(json, "ttkFirstHitMs"),
            getLongOrNull(json, "ttkSpawnMs")
        );
    }

    private void processCombatHeal(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Combat heal from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
        EnduranceContext ctx = getEnduranceContext(json);

        DuckDBTelemetryService.INSTANCE.logHeal(
            getStringOrNull(json, "room"),
            getStringOrNull(json, "world"),
            ctx.templateId,
            ctx.templateVersion,
            ctx.policyId,
            ctx.policyVersion,
            ctx.arenaId,
            ctx.sessionId,
            getStringOrNull(json, "targetName"),
            getStringOrNull(json, "targetType"),
            json.has("healAmount") ? json.get("healAmount").getAsDouble() : 0,
            getDoubleOrNull(json, "hpBefore"),
            getDoubleOrNull(json, "hpAfter"),
            getStringOrNull(json, "source")
        );
    }

    private void processCombatSpawn(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Combat spawn from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
        EnduranceContext ctx = getEnduranceContext(json);

        DuckDBTelemetryService.INSTANCE.logSpawn(
            getStringOrNull(json, "room"),
            getStringOrNull(json, "world"),
            ctx.templateId,
            ctx.templateVersion,
            ctx.policyId,
            ctx.policyVersion,
            ctx.arenaId,
            ctx.sessionId,
            getStringOrNull(json, "entityName"),
            getStringOrNull(json, "entityType"),
            getStringOrNull(json, "reason"),
            json.has("spawnFail") && json.get("spawnFail").getAsBoolean(),
            getDoubleOrNull(json, "x"),
            getDoubleOrNull(json, "y"),
            getDoubleOrNull(json, "z")
        );
    }

    private void processPlayerSnapshot(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Player snapshot from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
        String dimension = getStringOrNull(json, "dimension");
        if (dimension == null || dimension.isBlank()) {
            dimension = "overworld";
        }

        // Route through aggregation (sampling) if enabled
        if (AggregationConfig.AGGREGATION_ENABLED) {
            TelemetryAggregator agg = TelemetryAggregatorRegistry.INSTANCE.getAggregator(player.getUUID());
            if (agg != null) {
                // Create snapshot data for sampling
                SnapshotData snapshot = new SnapshotData(
                    java.time.Instant.now(),
                    player.getUUID(),
                    player.getName().getString(),
                    json.has("x") ? json.get("x").getAsDouble() : 0,
                    json.has("y") ? json.get("y").getAsDouble() : 0,
                    json.has("z") ? json.get("z").getAsDouble() : 0,
                    dimension,
                    (float) (json.has("healthHp") ? json.get("healthHp").getAsDouble() : 20),
                    (float) (json.has("maxHealthHp") ? json.get("maxHealthHp").getAsDouble() : 20),
                    (float) (json.has("armorValue") ? json.get("armorValue").getAsDouble() : 0),
                    (float) (json.has("stamina") ? json.get("stamina").getAsDouble() : 100),
                    (float) (json.has("maxStamina") ? json.get("maxStamina").getAsDouble() : 100),
                    getStringOrNull(json, "heldItem"),
                    json.has("inCombat") && json.get("inCombat").getAsBoolean(),
                    json.has("currentCombo") ? json.get("currentCombo").getAsInt() : 0,
                    getUuidOrNull(json, "questId"),
                    json.has("currentWave") ? json.get("currentWave").getAsInt() : 0,
                    json.has("clientFps") ? json.get("clientFps").getAsFloat() : 60f
                );

                // Returns false if rate-limited (drop this snapshot)
                if (!agg.processSnapshot(snapshot)) {
                    return; // Rate-limited, don't write to DB
                }
            }
        }

        // Write snapshot (not rate-limited or aggregation disabled)
        DuckDBTelemetryService.INSTANCE.logPlayerSnapshot(
            player.getUUID(),
            player.getName().getString(),
            getStringOrNull(json, "trigger"),
            json.has("healthHp") ? json.get("healthHp").getAsDouble() : 0,
            json.has("maxHealthHp") ? json.get("maxHealthHp").getAsDouble() : 0,
            json.has("healthHearts") ? json.get("healthHearts").getAsInt() : 0,
            json.has("absorptionHp") ? json.get("absorptionHp").getAsDouble() : 0,
            json.has("hungerLevel") ? json.get("hungerLevel").getAsInt() : 0,
            json.has("saturation") ? json.get("saturation").getAsDouble() : 0,
            json.has("exhaustion") ? json.get("exhaustion").getAsDouble() : 0,
            json.has("movementSpeed") ? json.get("movementSpeed").getAsDouble() : 0,
            json.has("velocityX") ? json.get("velocityX").getAsDouble() : 0,
            json.has("velocityY") ? json.get("velocityY").getAsDouble() : 0,
            json.has("velocityZ") ? json.get("velocityZ").getAsDouble() : 0,
            json.has("movementFlags") ? json.get("movementFlags").getAsInt() : 0,
            json.has("meleeDamageMult") ? json.get("meleeDamageMult").getAsDouble() : 1.0,
            json.has("meleeReduction") ? json.get("meleeReduction").getAsDouble() : 0,
            json.has("magicDamageMult") ? json.get("magicDamageMult").getAsDouble() : 1.0,
            json.has("magicReduction") ? json.get("magicReduction").getAsDouble() : 0,
            json.has("rangedDamageMult") ? json.get("rangedDamageMult").getAsDouble() : 1.0,
            json.has("rangedReduction") ? json.get("rangedReduction").getAsDouble() : 0,
            json.has("armorValue") ? json.get("armorValue").getAsDouble() : 0,
            json.has("armorToughness") ? json.get("armorToughness").getAsDouble() : 0,
            json.has("knockbackResistance") ? json.get("knockbackResistance").getAsDouble() : 0,
            json.has("totalDamageReduction") ? json.get("totalDamageReduction").getAsDouble() : 0,
            json.has("reach") ? json.get("reach").getAsDouble() : 0,
            json.has("hitboxWidth") ? json.get("hitboxWidth").getAsDouble() : 0,
            json.has("hitboxHeight") ? json.get("hitboxHeight").getAsDouble() : 0,
            getDoubleOrNull(json, "pehkuiScale"),
            getDoubleOrNull(json, "pehkuiHitboxScale"),
            json.has("stamina") ? json.get("stamina").getAsDouble() : 0,
            json.has("maxStamina") ? json.get("maxStamina").getAsDouble() : 0,
            json.has("dashCooldown") ? json.get("dashCooldown").getAsDouble() : 0,
            json.has("dodgeCooldown") ? json.get("dodgeCooldown").getAsDouble() : 0,
            json.has("abilityFlags") ? json.get("abilityFlags").getAsInt() : 0,
            json.has("currentCombo") ? json.get("currentCombo").getAsInt() : 0,
            getStringOrNull(json, "styleRank"),
            json.has("styleScore") ? json.get("styleScore").getAsInt() : 0,
            json.has("x") ? json.get("x").getAsDouble() : 0,
            json.has("y") ? json.get("y").getAsDouble() : 0,
            json.has("z") ? json.get("z").getAsDouble() : 0,
            getStringOrNull(json, "dimension")
        );
    }

    private void processPlayerAbility(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Player ability from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();

        String abilityType = getStringOrNull(json, "abilityType");
        boolean success = json.has("success") && json.get("success").getAsBoolean();
        Double staminaCost = getDoubleOrNull(json, "staminaCost");
        Double damageNegated = getDoubleOrNull(json, "damageNegated");

        // Route through aggregation if enabled
        if (AggregationConfig.AGGREGATION_ENABLED && abilityType != null) {
            TelemetryAggregator agg = TelemetryAggregatorRegistry.INSTANCE.getAggregator(player.getUUID());
            if (agg != null) {
                // Returns false if aggregated (don't write immediately)
                if (!agg.processAbility(
                        abilityType,
                        success,
                        staminaCost != null ? staminaCost : 0,
                        damageNegated != null ? damageNegated : 0)) {
                    return; // Aggregated, don't write to DB now
                }
            }
        }

        // BYPASS: Write immediately (aggregation disabled or no aggregator)
        DuckDBTelemetryService.INSTANCE.logAbility(
            player.getUUID(),
            abilityType,
            json.has("success") ? json.get("success").getAsBoolean() : null,
            json.has("result") ? json.get("result").getAsInt() : null,
            getDoubleOrNull(json, "staminaBefore"),
            getDoubleOrNull(json, "staminaAfter"),
            staminaCost,
            damageNegated,
            getStringOrNull(json, "damageSource"),
            getStringOrNull(json, "context"),
            getLongOrNull(json, "regenTimeMs")
        );
    }

    private void processPlayerAttributeChange(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Player attribute change from {}", player.getName().getString());
        // Attribute changes are typically server-authoritative, client events are for validation
        // Log as ability event with "attribute_change" type, storing full JSON as context

        DuckDBTelemetryService.INSTANCE.logAbility(
            player.getUUID(),
            "attribute_change",
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            jsonData, // Store full JSON as context
            null
        );
    }

    private void processHeatmapUpdate(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Heatmap update from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();

        int x = json.has("x") ? json.get("x").getAsInt() : 0;
        int y = json.has("y") ? json.get("y").getAsInt() : 0;
        int z = json.has("z") ? json.get("z").getAsInt() : 0;
        String heatmapType = getStringOrNull(json, "heatmapType");

        // Route through aggregation if enabled
        if (AggregationConfig.AGGREGATION_ENABLED) {
            TelemetryAggregator agg = TelemetryAggregatorRegistry.INSTANCE.getAggregator(player.getUUID());
            if (agg != null) {
                // Returns false if aggregated (don't write immediately)
                if (!agg.processHeatmap(x, y, z, heatmapType != null ? heatmapType : "movement")) {
                    return; // Aggregated, don't write to DB now
                }
            }
        }

        // BYPASS: Write immediately (aggregation disabled or no aggregator)
        DuckDBTelemetryService.INSTANCE.logHeatmap(
            heatmapType,
            getStringOrNull(json, "room"),
            x, y, z,
            json.has("count") ? json.get("count").getAsInt() : 1
        );
    }

    private void processRoomTransition(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Room transition from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();

        DuckDBTelemetryService.INSTANCE.logRoomTransition(
            player.getUUID(),
            player.getName().getString(),
            getStringOrNull(json, "room")
        );
    }

    private void processEnduranceSession(ServerPlayer player, long timestamp, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Endurance session from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();

        String eventType = getStringOrNull(json, "eventType");
        UUID sessionId = json.has("sessionId") ? UUID.fromString(json.get("sessionId").getAsString()) : null;

        if (sessionId == null) return;

        if ("start".equals(eventType)) {
            DuckDBTelemetryService.INSTANCE.logSessionStart(
                sessionId,
                player.getUUID(),
                player.getName().getString(),
                getStringOrNull(json, "questName"),
                getStringOrNull(json, "questType"),
                json.has("totalWaves") ? json.get("totalWaves").getAsInt() : 0,
                json.has("isEndless") && json.get("isEndless").getAsBoolean(),
                json.has("playerCount") ? json.get("playerCount").getAsInt() : 1,
                getStringOrNull(json, "templateId"),
                json.has("templateVersion") ? json.get("templateVersion").getAsInt() : null,
                getStringOrNull(json, "policyId"),
                json.has("policyVersion") ? json.get("policyVersion").getAsInt() : null,
                json.has("instanceId") ? UUID.fromString(json.get("instanceId").getAsString()) : null,
                json.has("arenaId") ? UUID.fromString(json.get("arenaId").getAsString()) : null
            );
        } else if ("end".equals(eventType)) {
            long startTs = json.has("startTimestamp") ? json.get("startTimestamp").getAsLong() : timestamp;
            DuckDBTelemetryService.INSTANCE.logSessionEnd(
                sessionId,
                player.getUUID(),
                player.getName().getString(),
                getStringOrNull(json, "questName"),
                getStringOrNull(json, "questType"),
                json.has("totalWaves") ? json.get("totalWaves").getAsInt() : 0,
                json.has("isEndless") && json.get("isEndless").getAsBoolean(),
                json.has("playerCount") ? json.get("playerCount").getAsInt() : 1,
                Instant.ofEpochMilli(startTs),
                getStringOrNull(json, "outcome"),
                json.has("wavesCompleted") ? json.get("wavesCompleted").getAsInt() : 0,
                json.has("totalKills") ? json.get("totalKills").getAsInt() : 0,
                json.has("damageDealt") ? json.get("damageDealt").getAsDouble() : 0,
                json.has("damageTaken") ? json.get("damageTaken").getAsDouble() : 0,
                json.has("tokensEarned") ? json.get("tokensEarned").getAsInt() : 0,
                json.has("prestigeEarned") ? json.get("prestigeEarned").getAsInt() : 0,
                json.has("bloodGemsEarned") ? json.get("bloodGemsEarned").getAsInt() : 0,
                json.has("noDamageWaves") ? json.get("noDamageWaves").getAsInt() : 0,
                getStringOrNull(json, "templateId"),
                json.has("templateVersion") ? json.get("templateVersion").getAsInt() : null,
                getStringOrNull(json, "policyId"),
                json.has("policyVersion") ? json.get("policyVersion").getAsInt() : null,
                json.has("instanceId") ? UUID.fromString(json.get("instanceId").getAsString()) : null,
                json.has("arenaId") ? UUID.fromString(json.get("arenaId").getAsString()) : null,
                json.has("countdownStarted") ? json.get("countdownStarted").getAsInt() : null,
                json.has("countdownCancelled") ? json.get("countdownCancelled").getAsInt() : null,
                json.has("giveupDuringRespawn") ? json.get("giveupDuringRespawn").getAsInt() : null,
                json.has("inventoryRestoreSuccess") ? json.get("inventoryRestoreSuccess").getAsInt() : null,
                json.has("inventoryRestoreFallback") ? json.get("inventoryRestoreFallback").getAsInt() : null,
                json.has("externalDeathRespawnCount") ? json.get("externalDeathRespawnCount").getAsInt() : null,
                json.has("waveBlockedDetected") ? json.get("waveBlockedDetected").getAsInt() : null
            );
        }
    }

    private void processEnduranceWave(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Endurance wave from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();

        UUID sessionId = json.has("sessionId") ? UUID.fromString(json.get("sessionId").getAsString()) : null;
        if (sessionId == null) return;

        EnduranceContext ctx = getEnduranceContext(json);
        String eventType = getStringOrNull(json, "eventType");
        int waveNumber = json.has("waveNumber") ? json.get("waveNumber").getAsInt() : 0;

        if ("start".equals(eventType)) {
            // Update LVC for wave start
            if (AggregationConfig.LVC_ENABLED) {
                TelemetryAggregator agg = TelemetryAggregatorRegistry.INSTANCE.getAggregator(player.getUUID());
                if (agg != null) {
                    agg.processWaveStart(waveNumber);
                }
            }

            String[] modifiers = json.has("modifiers")
                ? new Gson().fromJson(json.get("modifiers"), String[].class)
                : new String[0];

            DuckDBTelemetryService.INSTANCE.logWaveStart(
                sessionId,
                waveNumber,
                json.has("mobCount") ? json.get("mobCount").getAsInt() : 0,
                json.has("playerCount") ? json.get("playerCount").getAsInt() : 1,
                getStringOrNull(json, "questType"),
                modifiers,
                ctx.templateId,
                ctx.templateVersion,
                ctx.policyId,
                ctx.policyVersion,
                ctx.arenaId
            );
        } else if ("complete".equals(eventType)) {
            // Update LVC for wave complete
            if (AggregationConfig.LVC_ENABLED) {
                TelemetryAggregator agg = TelemetryAggregatorRegistry.INSTANCE.getAggregator(player.getUUID());
                if (agg != null) {
                    agg.processWaveComplete(waveNumber);
                }
            }

            DuckDBTelemetryService.INSTANCE.logWaveComplete(
                sessionId,
                waveNumber,
                json.has("mobsKilled") ? json.get("mobsKilled").getAsInt() : 0,
                json.has("durationMs") ? json.get("durationMs").getAsLong() : 0,
                json.has("noDamage") && json.get("noDamage").getAsBoolean(),
                json.has("killsPerSecond") ? json.get("killsPerSecond").getAsDouble() : 0,
                ctx.templateId,
                ctx.templateVersion,
                ctx.policyId,
                ctx.policyVersion,
                ctx.arenaId
            );
        }
    }

    private void processEnduranceKill(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Endurance kill from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();

        UUID sessionId = json.has("sessionId") ? UUID.fromString(json.get("sessionId").getAsString()) : null;
        if (sessionId == null) return;

        EnduranceContext ctx = getEnduranceContext(json);
        DuckDBTelemetryService.INSTANCE.logWaveKill(
            sessionId,
            json.has("waveNumber") ? json.get("waveNumber").getAsInt() : 0,
            getStringOrNull(json, "mobType"),
            json.has("isElite") && json.get("isElite").getAsBoolean(),
            getStringOrNull(json, "killerWeapon"),
            json.has("damageDealt") ? json.get("damageDealt").getAsDouble() : 0,
            ctx.templateId,
            ctx.templateVersion,
            ctx.policyId,
            ctx.policyVersion,
            ctx.arenaId
        );
    }

    private void processEnduranceCombo(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Endurance combo from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();

        UUID sessionId = json.has("sessionId") ? UUID.fromString(json.get("sessionId").getAsString()) : null;
        if (sessionId == null) return;

        EnduranceContext ctx = getEnduranceContext(json);
        String eventType = getStringOrNull(json, "eventType");

        if ("milestone".equals(eventType)) {
            DuckDBTelemetryService.INSTANCE.logComboMilestone(
                player.getUUID(),
                sessionId,
                json.has("milestone") ? json.get("milestone").getAsInt() : 0,
                json.has("pointsEarned") ? json.get("pointsEarned").getAsInt() : 0,
                json.has("styleEarned") ? json.get("styleEarned").getAsInt() : 0,
                getStringOrNull(json, "rank"),
                ctx.templateId,
                ctx.templateVersion,
                ctx.policyId,
                ctx.policyVersion,
                ctx.arenaId
            );
        } else if ("break".equals(eventType)) {
            DuckDBTelemetryService.INSTANCE.logComboBreak(
                player.getUUID(),
                sessionId,
                json.has("comboLost") ? json.get("comboLost").getAsInt() : 0,
                getStringOrNull(json, "previousRank"),
                getStringOrNull(json, "newRank"),
                json.has("damageTaken") ? json.get("damageTaken").getAsDouble() : 0,
                ctx.templateId,
                ctx.templateVersion,
                ctx.policyId,
                ctx.policyVersion,
                ctx.arenaId
            );
        } else {
            // Generic combo event (rank up/down)
            DuckDBTelemetryService.INSTANCE.logComboEvent(
                player.getUUID(),
                sessionId,
                eventType,
                getStringOrNull(json, "oldRank"),
                getStringOrNull(json, "newRank"),
                json.has("styleScore") ? json.get("styleScore").getAsInt() : 0,
                json.has("currentCombo") ? json.get("currentCombo").getAsInt() : 0,
                ctx.templateId,
                ctx.templateVersion,
                ctx.policyId,
                ctx.policyVersion,
                ctx.arenaId
            );
        }
    }

    private void processEndurancePerk(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Endurance perk from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();

        UUID sessionId = json.has("sessionId") ? UUID.fromString(json.get("sessionId").getAsString()) : null;
        if (sessionId == null) return;

        EnduranceContext ctx = getEnduranceContext(json);
        String eventType = getStringOrNull(json, "eventType");

        if ("choices".equals(eventType)) {
            DuckDBTelemetryService.INSTANCE.logPerkChoices(
                player.getUUID(),
                sessionId,
                json.has("waveNumber") ? json.get("waveNumber").getAsInt() : 0,
                getStringOrNull(json, "choicesJson"),
                ctx.templateId,
                ctx.templateVersion,
                ctx.policyId,
                ctx.policyVersion,
                ctx.arenaId
            );
        } else {
            // Perk selected
            DuckDBTelemetryService.INSTANCE.logPerkSelected(
                player.getUUID(),
                sessionId,
                getStringOrNull(json, "perkId"),
                getStringOrNull(json, "perkName"),
                getStringOrNull(json, "tier"),
                getStringOrNull(json, "category"),
                json.has("stackCount") ? json.get("stackCount").getAsInt() : 1,
                json.has("totalPerks") ? json.get("totalPerks").getAsInt() : 0,
                json.has("waveNumber") ? json.get("waveNumber").getAsInt() : 0,
                ctx.templateId,
                ctx.templateVersion,
                ctx.policyId,
                ctx.policyVersion,
                ctx.arenaId
            );
        }
    }

    private void processEnduranceReward(ServerPlayer player, String jsonData) {
        LOGGER.trace("[TelemetryPacket] Endurance reward from {}", player.getName().getString());
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();

        UUID sessionId = json.has("sessionId") ? UUID.fromString(json.get("sessionId").getAsString()) : null;
        if (sessionId == null) return;

        EnduranceContext ctx = getEnduranceContext(json);
        String rewardType = getStringOrNull(json, "rewardType");

        if ("currency".equals(rewardType)) {
            DuckDBTelemetryService.INSTANCE.logCurrencyEarned(
                player.getUUID(),
                sessionId,
                getStringOrNull(json, "currency"),
                json.has("amount") ? json.get("amount").getAsInt() : 0,
                getStringOrNull(json, "source"),
                ctx.templateId,
                ctx.templateVersion,
                ctx.policyId,
                ctx.policyVersion,
                ctx.arenaId
            );
        } else if ("loot".equals(rewardType)) {
            DuckDBTelemetryService.INSTANCE.logLootDrop(
                player.getUUID(),
                sessionId,
                getStringOrNull(json, "itemId"),
                json.has("itemCount") ? json.get("itemCount").getAsInt() : 1,
                getStringOrNull(json, "lootTier"),
                ctx.templateId,
                ctx.templateVersion,
                ctx.policyId,
                ctx.policyVersion,
                ctx.arenaId
            );
        }
    }

    // ============================================
    // JSON HELPER METHODS
    // ============================================

    private static @Nullable String getStringOrNull(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }

    private static @Nullable Double getDoubleOrNull(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsDouble() : null;
    }

    private static @Nullable Long getLongOrNull(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsLong() : null;
    }

    private static @Nullable UUID getUuidOrNull(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull()
            ? UUID.fromString(json.get(key).getAsString())
            : null;
    }

    private static EnduranceContext getEnduranceContext(JsonObject json) {
        String templateId = getStringOrNull(json, "templateId");
        Integer templateVersion = json.has("templateVersion") && !json.get("templateVersion").isJsonNull()
            ? json.get("templateVersion").getAsInt()
            : null;
        String policyId = getStringOrNull(json, "policyId");
        Integer policyVersion = json.has("policyVersion") && !json.get("policyVersion").isJsonNull()
            ? json.get("policyVersion").getAsInt()
            : null;
        UUID arenaId = getUuidOrNull(json, "arenaId");
        UUID sessionId = getUuidOrNull(json, "sessionId");
        if (sessionId == null) {
            sessionId = getUuidOrNull(json, "questId");
        }
        return new EnduranceContext(templateId, templateVersion, policyId, policyVersion, arenaId, sessionId);
    }

    private record EnduranceContext(
        @Nullable String templateId,
        @Nullable Integer templateVersion,
        @Nullable String policyId,
        @Nullable Integer policyVersion,
        @Nullable UUID arenaId,
        @Nullable UUID sessionId
    ) {}

    // ============================================
    // RATE LIMITING
    // ============================================

    /**
     * Clean up rate limit state for disconnected players.
     */
    public void cleanupPlayer(UUID playerId) {
        playerRateLimits.remove(playerId);
    }

    /**
     * Get current rate limit stats for a player.
     */
    public RateLimitStats getRateLimitStats(UUID playerId) {
        RateLimitState state = playerRateLimits.get(playerId);
        if (state == null) {
            return new RateLimitStats(0, MAX_EVENTS_PER_SECOND, 0);
        }
        return new RateLimitStats(
            state.currentWindowCount.get(),
            MAX_EVENTS_PER_SECOND,
            state.totalEventsProcessed.get()
        );
    }

    /**
     * Rate limit statistics for monitoring.
     */
    public record RateLimitStats(int currentWindowCount, int maxPerSecond, long totalProcessed) {}

    /**
     * Per-player rate limiting state using sliding window.
     */
    private static class RateLimitState {
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicInteger currentWindowCount = new AtomicInteger(0);
        private final AtomicLong totalEventsProcessed = new AtomicLong(0);

        /**
         * Try to consume event quota.
         * @param count Number of events to consume
         * @return true if allowed, false if rate limited
         */
        boolean tryConsume(int count) {
            long now = System.currentTimeMillis();
            long windowStartTime = windowStart.get();

            // Reset window if expired
            if (now - windowStartTime >= RATE_WINDOW_MS) {
                windowStart.set(now);
                currentWindowCount.set(0);
            }

            // Check if within limit
            int currentCount = currentWindowCount.get();
            if (currentCount + count > MAX_EVENTS_PER_SECOND) {
                return false;
            }

            // Consume quota
            currentWindowCount.addAndGet(count);
            totalEventsProcessed.addAndGet(count);
            return true;
        }
    }
}
