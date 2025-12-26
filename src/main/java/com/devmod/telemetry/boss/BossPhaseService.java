package com.devmod.telemetry.boss;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossPhaseService {
    public static final BossPhaseService INSTANCE = new BossPhaseService();

    private final Map<UUID, BossPhase> bossPhases = new ConcurrentHashMap<>();

    private BossPhaseService() {}

    /**
     * Start tracking a new boss phase.
     *
     * @param bossId Boss entity UUID
     * @param phase Phase identifier
     * @param room Current room ID
     * @param worldId World dimension
     */
    public void startPhase(UUID bossId, String phase, String room, String worldId) {
        bossPhases.put(bossId, new BossPhase(phase, System.currentTimeMillis(), room, worldId));
    }

    /**
     * End current boss phase and get result for logging.
     *
     * @param bossId Boss entity UUID
     * @param bossName Boss display name
     * @param bossType Boss entity type
     * @return Optional phase result with duration
     */
    public Optional<PhaseResult> endPhase(UUID bossId, String bossName, String bossType) {
        BossPhase phase = bossPhases.remove(bossId);
        if (phase == null) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        return Optional.of(new PhaseResult(
            bossName, bossType, phase.phase,
            phase.startMs, now, now - phase.startMs,
            phase.room, phase.world
        ));
    }

    /**
     * Check if boss is in a tracked phase.
     *
     * @param bossId Boss entity UUID
     * @return true if boss has active phase
     */
    public boolean hasActivePhase(UUID bossId) {
        return bossPhases.containsKey(bossId);
    }

    /**
     * Get current phase for boss.
     *
     * @param bossId Boss entity UUID
     * @return Optional current phase name
     */
    public Optional<String> getCurrentPhase(UUID bossId) {
        BossPhase phase = bossPhases.get(bossId);
        return phase != null ? Optional.of(phase.phase) : Optional.empty();
    }

    /**
     * Cleanup tracker for boss (on death).
     *
     * @param bossId Boss entity UUID
     */
    public void cleanupEntity(UUID bossId) {
        bossPhases.remove(bossId);
    }

    /**
     * Clear all trackers (for reload).
     */
    public void clear() {
        bossPhases.clear();
    }

    /**
     * Data class for phase transition results.
     */
    public record PhaseResult(
        String bossName,
        String bossType,
        String phase,
        long startMs,
        long endMs,
        long durationMs,
        String room,
        String world
    ) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                + "\"type\":\"boss_phase\","
                + "\"boss\":\"" + escape(bossName) + "\","
                + "\"bossType\":\"" + escape(bossType) + "\","
                + "\"phase\":\"" + escape(phase) + "\","
                + "\"start\":\"" + Instant.ofEpochMilli(startMs) + "\","
                + "\"end\":\"" + Instant.ofEpochMilli(endMs) + "\","
                + "\"durationMs\":" + durationMs + ","
                + "\"room\":\"" + escape(room) + "\","
                + "\"world\":\"" + escape(world) + "\"}";
        }

        private static String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }

    /**
     * Internal data class for tracking boss phases.
     */
    private record BossPhase(String phase, long startMs, String room, String world) {}
}
