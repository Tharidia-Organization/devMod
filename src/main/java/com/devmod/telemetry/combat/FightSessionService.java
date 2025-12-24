package com.devmod.telemetry.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Service for managing fight sessions.
 *
 * Tracks:
 * - Fight sessions per room (start, duration, participants)
 * - Kill count per mob type
 * - Death count per player
 * - TTK (Time To Kill) per entity type
 * - Burst damage (max damage in time window)
 */
// Minecraft API (getGameProfile) guaranteed non-null for ServerPlayer

public class FightSessionService {
    public static final FightSessionService INSTANCE = new FightSessionService();

    private final Map<String, FightSession> activeFights = new ConcurrentHashMap<>();
    private static final long FIGHT_TIMEOUT_MS = 10_000; // 10s of inactivity closes the fight

    private FightSessionService() {}

    // ============================================
    // FIGHT SESSION MANAGEMENT
    // ============================================

    /**
     * Registers a hit in a combat session.
     * Creates the session if it doesn't exist.
     */
    public void registerHit(String room, String worldId, Entity attacker, LivingEntity target, boolean isKill,
                            @javax.annotation.Nullable CombatContext context) {
        long now = System.currentTimeMillis();
        FightSession session = activeFights.computeIfAbsent(room,
            id -> new FightSession(worldId, now));

        session.lastHitMs = now;
        session.hits++;

        if (context != null) {
            if (session.templateId == null && context.templateId() != null) {
                session.templateId = context.templateId();
            }
            if (session.templateVersion == null && context.templateVersion() != null) {
                session.templateVersion = context.templateVersion();
            }
            if (session.policyId == null && context.policyId() != null) {
                session.policyId = context.policyId();
            }
            if (session.policyVersion == null && context.policyVersion() != null) {
                session.policyVersion = context.policyVersion();
            }
            if (session.arenaId == null && context.arenaId() != null) {
                session.arenaId = context.arenaId();
            }
            if (session.sessionId == null && context.sessionId() != null) {
                session.sessionId = context.sessionId();
            }
        }

        // Add player to participants list
        if (attacker instanceof ServerPlayer player) {
            session.players.add(player.getGameProfile().getName());
        }
        if (target instanceof ServerPlayer player) {
            session.players.add(player.getGameProfile().getName());
        }

        // Register kill
        if (isKill) {
            if (target instanceof ServerPlayer) {
                session.playerDeaths++;
                session.playerDeathsByName.merge(target.getName().getString(), 1, FightSessionService::sumCounts);
            } else {
                session.mobKills++;
                session.mobKillsByType.merge(getEntityTypeName(target), 1, FightSessionService::sumCounts);
            }
        }
    }

    /**
     * Registers burst damage (for tracking DPS spikes).
     */
    public void registerBurstDamage(String room, double damage) {
        FightSession session = activeFights.get(room);
        if (session != null) {
            session.burst.update(damage, System.currentTimeMillis());
        }
    }

    /**
     * Registers TTK for a dead entity.
     */
    public void registerTTK(String room, LivingEntity entity, long ttkMs) {
        FightSession session = activeFights.get(room);
        if (session != null) {
            String type = getEntityTypeName(entity);
            session.ttkByType.merge(type,
                new TTKAggregate(1, ttkMs, ttkMs),
                (old, newAgg) -> old.add(newAgg.maxMs));
        }
    }

    /**
     * Registers remaining HP after a hit (for average statistics).
     */
    public void registerHpAfterHit(String room, LivingEntity target, double hpAfter) {
        FightSession session = activeFights.get(room);
        if (session != null) {
            if (target instanceof ServerPlayer) {
                session.hpAfterPlayersTotal += hpAfter;
                session.hpSamplesPlayers++;
            } else {
                session.hpAfterMobsTotal += hpAfter;
                session.hpSamplesMobs++;
            }
        }
    }

    /**
     * Tick to close inactive sessions.
     * Call every server tick or periodically.
     */
    public void tick(Consumer<FightSessionResult> onFightEnd) {
        long now = System.currentTimeMillis();

        List<String> toClose = activeFights.entrySet().stream()
            .filter(e -> now - e.getValue().lastHitMs > FIGHT_TIMEOUT_MS)
            .map(Map.Entry::getKey)
            .toList();

        for (String room : toClose) {
            FightSession session = activeFights.remove(room);
            if (session != null) {
                FightSessionResult result = createResult(session, room, now);
                onFightEnd.accept(result);
            }
        }
    }

    /**
     * Gets active sessions.
     */
    public List<FightSessionSummary> getActiveFightSummaries() {
        List<FightSessionSummary> summaries = new ArrayList<>();
        long now = System.currentTimeMillis();

        activeFights.forEach((room, session) -> {
            long duration = now - session.startMs;
            summaries.add(new FightSessionSummary(
                room, duration, session.hits, session.players.size(),
                session.mobKills, session.playerDeaths
            ));
        });

        return summaries;
    }

    /**
     * Checks if there's an active fight in a room.
     */
    public boolean isFightActive(String room) {
        return activeFights.containsKey(room);
    }

    // ============================================
    // INTERNAL CLASSES
    // ============================================

    private FightSessionResult createResult(FightSession session, String room, long endMs) {
        return new FightSessionResult(
            room,
            session.worldId,
            session.templateId,
            session.templateVersion,
            session.policyId,
            session.policyVersion,
            session.arenaId,
            session.sessionId,
            session.startMs,
            endMs,
            session.hits,
            session.mobKills,
            session.playerDeaths,
            new HashSet<>(session.players),
            new HashMap<>(session.mobKillsByType),
            new HashMap<>(session.playerDeathsByName),
            new HashMap<>(session.ttkByType),
            session.burst.maxBurst,
            session.hpSamplesPlayers > 0 ? session.hpAfterPlayersTotal / session.hpSamplesPlayers : -1,
            session.hpSamplesMobs > 0 ? session.hpAfterMobsTotal / session.hpSamplesMobs : -1
        );
    }

    private String getEntityTypeName(Entity entity) {
        return entity.getType().toShortString();
    }

    private static Integer sumCounts(Integer left, Integer right) {
        if (left == null) {
            return right == null ? 0 : right;
        }
        if (right == null) {
            return left;
        }
        return left + right;
    }

    // ============================================
    // DATA CLASSES
    // ============================================

    private static class FightSession {
        final String worldId;
        final long startMs;
        long lastHitMs;
        int hits;
        int mobKills;
        int playerDeaths;
        String templateId;
        Integer templateVersion;
        String policyId;
        Integer policyVersion;
        UUID arenaId;
        UUID sessionId;
        final Set<String> players = new HashSet<>();
        final Map<String, Integer> mobKillsByType = new HashMap<>();
        final Map<String, Integer> playerDeathsByName = new HashMap<>();
        final Map<String, TTKAggregate> ttkByType = new HashMap<>();
        final BurstTracker burst = new BurstTracker();
        double hpAfterPlayersTotal = 0;
        int hpSamplesPlayers = 0;
        double hpAfterMobsTotal = 0;
        int hpSamplesMobs = 0;

        FightSession(String worldId, long startMs) {
            this.worldId = worldId;
            this.startMs = startMs;
            this.lastHitMs = startMs;
        }
    }

    private static class BurstTracker {
        double maxBurst = 0;
        long windowStart = 0;
        double windowDamage = 0;
        static final long WINDOW_MS = 2000;

        void update(double damage, long nowMs) {
            if (nowMs - windowStart > WINDOW_MS) {
                windowStart = nowMs;
                windowDamage = 0;
            }
            windowDamage += damage;
            if (windowDamage > maxBurst) {
                maxBurst = windowDamage;
            }
        }
    }

    /**
     * Aggregate for Time To Kill.
     */
    public record TTKAggregate(long count, long totalMs, long maxMs) {
        public TTKAggregate add(long ms) {
            return new TTKAggregate(count + 1, totalMs + ms, Math.max(maxMs, ms));
        }

        public double avgMs() {
            return count > 0 ? (double) totalMs / count : -1;
        }
    }

    /**
     * Optional arena context attached to combat events.
     */
    public record CombatContext(
        String templateId,
        Integer templateVersion,
        String policyId,
        Integer policyVersion,
        UUID arenaId,
        UUID sessionId
    ) {}

    /**
     * Summary of an active fight session.
     */
    public record FightSessionSummary(
        String room,
        long durationMs,
        int hits,
        int playerCount,
        int mobKills,
        int playerDeaths
    ) {
        @Override
        public String toString() {
            return String.format(
                "%s: duration=%dms hits=%d players=%d kills=%d deaths=%d",
                room, durationMs, hits, playerCount, mobKills, playerDeaths
            );
        }
    }

    /**
     * Final result of a completed fight session.
     */
    public record FightSessionResult(
        String room,
        String worldId,
        String templateId,
        Integer templateVersion,
        String policyId,
        Integer policyVersion,
        UUID arenaId,
        UUID sessionId,
        long startMs,
        long endMs,
        int hits,
        int mobKills,
        int playerDeaths,
        Set<String> players,
        Map<String, Integer> mobKillsByType,
        Map<String, Integer> playerDeathsByName,
        Map<String, TTKAggregate> ttkByType,
        double maxBurst,
        double avgHpAfterPlayers,
        double avgHpAfterMobs
    ) {
        public long durationMs() {
            return endMs - startMs;
        }

        public Instant startInstant() {
            return Instant.ofEpochMilli(startMs);
        }

        public Instant endInstant() {
            return Instant.ofEpochMilli(endMs);
        }

        /**
         * Serializes to JSON format.
         */
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"room\":\"").append(escapeJson(room)).append("\",");
            sb.append("\"world\":\"").append(escapeJson(worldId)).append("\",");
            sb.append("\"templateId\":").append(templateId != null ? "\"" + escapeJson(templateId) + "\"" : "null").append(",");
            sb.append("\"templateVersion\":").append(templateVersion != null ? templateVersion : "null").append(",");
            sb.append("\"policyId\":").append(policyId != null ? "\"" + escapeJson(policyId) + "\"" : "null").append(",");
            sb.append("\"policyVersion\":").append(policyVersion != null ? policyVersion : "null").append(",");
            sb.append("\"arenaId\":").append(arenaId != null ? "\"" + arenaId + "\"" : "null").append(",");
            sb.append("\"sessionId\":").append(sessionId != null ? "\"" + sessionId + "\"" : "null").append(",");
            sb.append("\"start\":\"").append(startInstant()).append("\",");
            sb.append("\"end\":\"").append(endInstant()).append("\",");
            sb.append("\"durationMs\":").append(durationMs()).append(",");
            sb.append("\"hits\":").append(hits).append(",");
            sb.append("\"players\":").append(players.size()).append(",");
            sb.append("\"mobKills\":").append(mobKills).append(",");
            sb.append("\"playerDeaths\":").append(playerDeaths).append(",");
            sb.append("\"mobKillsByType\":").append(mapToJson(mobKillsByType)).append(",");
            sb.append("\"playerDeathsByName\":").append(mapToJson(playerDeathsByName)).append(",");
            sb.append("\"ttkByType\":").append(ttkMapToJson(ttkByType)).append(",");
            sb.append("\"burstMax\":").append(maxBurst).append(",");
            sb.append("\"hpAfterPlayersAvg\":").append(avgHpAfterPlayers).append(",");
            sb.append("\"hpAfterMobsAvg\":").append(avgHpAfterMobs).append("}");
            return sb.toString();
        }

        private String mapToJson(Map<String, Integer> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Integer> e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeJson(e.getKey())).append("\":").append(e.getValue());
            }
            sb.append("}");
            return sb.toString();
        }

        private String ttkMapToJson(Map<String, TTKAggregate> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, TTKAggregate> e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                TTKAggregate ttk = e.getValue();
                sb.append("\"").append(escapeJson(e.getKey())).append("\":{");
                sb.append("\"avg\":").append(ttk.avgMs()).append(",");
                sb.append("\"max\":").append(ttk.maxMs()).append(",");
                sb.append("\"count\":").append(ttk.count()).append("}");
            }
            sb.append("}");
            return sb.toString();
        }

        private String escapeJson(String s) {
            return com.devmod.telemetry.TelemetryJson.escape(s);
        }
    }
}
