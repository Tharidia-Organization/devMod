package com.devmod.arena.metrics;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Context for arena metrics and telemetry (DD13).
 *
 * <p>All arena.build.* events MUST include this context for proper correlation.
 * The context is immutable and thread-safe.
 *
 * <p>Events and extra fields:
 * <ul>
 *   <li>arena.build.start: estimatedMs, estimatedBlocks</li>
 *   <li>arena.build.end: actualMs, actualBlocks, success</li>
 *   <li>arena.build.fail: reason, exception, blocksPlaced, rollbackMs</li>
 *   <li>arena.build.rollback: blocksReverted, entitiesRemoved, durationMs</li>
 * </ul>
 */
public record ArenaMetricsContext(
    String templateId,
    int templateVersion,
    UUID instanceId,
    UUID arenaId,
    @Nullable UUID playerId,
    @Nullable UUID partyId,
    Instant timestamp
) {
    /**
     * Creates a context with current timestamp.
     */
    public static ArenaMetricsContext now(
            String templateId,
            int templateVersion,
            UUID instanceId,
            UUID arenaId) {
        return new ArenaMetricsContext(
            templateId,
            templateVersion,
            instanceId,
            arenaId,
            null,
            null,
            Instant.now()
        );
    }

    /**
     * Creates a context with player info.
     */
    public static ArenaMetricsContext forPlayer(
            String templateId,
            int templateVersion,
            UUID instanceId,
            UUID arenaId,
            UUID playerId) {
        return new ArenaMetricsContext(
            templateId,
            templateVersion,
            instanceId,
            arenaId,
            playerId,
            null,
            Instant.now()
        );
    }

    /**
     * Creates a context with party info.
     */
    public static ArenaMetricsContext forParty(
            String templateId,
            int templateVersion,
            UUID instanceId,
            UUID arenaId,
            UUID playerId,
            UUID partyId) {
        return new ArenaMetricsContext(
            templateId,
            templateVersion,
            instanceId,
            arenaId,
            playerId,
            partyId,
            Instant.now()
        );
    }

    /**
     * Returns a copy with player ID set.
     */
    public ArenaMetricsContext withPlayerId(UUID playerId) {
        return new ArenaMetricsContext(
            templateId,
            templateVersion,
            instanceId,
            arenaId,
            playerId,
            partyId,
            timestamp
        );
    }

    /**
     * Returns a copy with party ID set.
     */
    public ArenaMetricsContext withPartyId(UUID partyId) {
        return new ArenaMetricsContext(
            templateId,
            templateVersion,
            instanceId,
            arenaId,
            playerId,
            partyId,
            timestamp
        );
    }

    /**
     * Converts to a Map for telemetry emission.
     * Uses LinkedHashMap to preserve field order.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("templateId", templateId);
        map.put("templateVersion", templateVersion);
        map.put("instanceId", instanceId.toString());
        map.put("arenaId", arenaId.toString());
        if (playerId != null) {
            map.put("playerId", playerId.toString());
        }
        if (partyId != null) {
            map.put("partyId", partyId.toString());
        }
        map.put("ts", timestamp.toString());
        return map;
    }

    /**
     * Returns a correlation ID for log correlation.
     */
    public String correlationId() {
        return "%s:%d:%s".formatted(
            templateId,
            templateVersion,
            arenaId.toString().substring(0, 8)
        );
    }
}
