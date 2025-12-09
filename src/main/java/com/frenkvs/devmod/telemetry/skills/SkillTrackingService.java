package com.frenkvs.devmod.telemetry.skills;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Service for tracking skill casts and whiffs (misses).
 * Extracted from TelemetryService for better separation of concerns.
 *
 * Thread-safe for concurrent access from multiple server threads.
 */
public class SkillTrackingService {
    public static final SkillTrackingService INSTANCE = new SkillTrackingService();

    private static final long WHIFF_WINDOW_MS = 2000;

    private final Map<UUID, SkillTracker> skillTrackers = new ConcurrentHashMap<>();

    // Aggregated skill stats for overlay display (skillId -> stats)
    private final Map<String, SkillStats> skillStats = new ConcurrentHashMap<>();

    private SkillTrackingService() {}

    /**
     * Record a skill cast for an entity.
     *
     * @param casterId Entity UUID
     * @param skillId Skill identifier
     * @param room Current room ID
     * @param worldId World dimension
     * @param casterName Caster display name
     * @param casterType Entity type string
     */
    public void recordCast(UUID casterId, String skillId, String room, String worldId,
                           String casterName, String casterType) {
        SkillTracker tracker = skillTrackers.computeIfAbsent(casterId, k -> new SkillTracker());
        tracker.cast(skillId, System.currentTimeMillis(), room, worldId, casterName, casterType);
    }

    /**
     * Record a successful skill hit (removes pending whiff check).
     *
     * @param casterId Entity UUID
     * @param skillId Skill identifier
     */
    public void recordHit(UUID casterId, String skillId) {
        SkillTracker tracker = skillTrackers.get(casterId);
        if (tracker != null) {
            tracker.hit(skillId);
        }
    }

    /**
     * Tick skill trackers to detect whiffs (casts that didn't hit within window).
     *
     * @param now Current timestamp
     * @param whiffLogger Consumer for whiff events
     */
    public void tick(long now, Consumer<SkillWhiff> whiffLogger) {
        skillTrackers.forEach((id, tracker) -> tracker.checkWhiff(now, whiffLogger));
    }

    /**
     * Cleanup tracker for entity.
     *
     * @param entityId Entity UUID
     */
    public void cleanupEntity(UUID entityId) {
        skillTrackers.remove(entityId);
    }

    /**
     * Clear all trackers (for reload).
     */
    public void clear() {
        skillTrackers.clear();
    }

    /**
     * Get recent skill statistics for overlay display.
     *
     * @param limit Maximum number of skills to return
     * @return List of skill stats sorted by last use time (most recent first)
     */
    public List<SkillStats> getRecentSkills(int limit) {
        return skillStats.values().stream()
                .sorted(Comparator.comparingLong(SkillStats::lastUseMs).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Record damage dealt by a skill (for stats tracking).
     *
     * @param skillId Skill identifier
     * @param damage Damage amount
     */
    public void recordDamage(String skillId, float damage) {
        skillStats.computeIfAbsent(skillId, SkillStats::new).addDamage(damage);
    }

    /**
     * Aggregated statistics for a skill (for overlay display).
     */
    public static final class SkillStats {
        private final String skillId;
        private int uses = 0;
        private int hits = 0;
        private float totalDamage = 0;
        private long lastUseMs = 0;

        public SkillStats(String skillId) {
            this.skillId = skillId;
        }

        public void recordUse() {
            uses++;
            lastUseMs = System.currentTimeMillis();
        }

        public void recordHit() {
            hits++;
        }

        public void addDamage(float damage) {
            totalDamage += damage;
        }

        public String skillId() { return skillId; }
        public int uses() { return uses; }
        public int hits() { return hits; }
        public float totalDamage() { return totalDamage; }
        public long lastUseMs() { return lastUseMs; }

        public float getHitRate() {
            return uses > 0 ? (float) hits / uses : 0;
        }
    }

    /**
     * Data class for skill whiff events.
     */
    public record SkillWhiff(
        String skillId,
        String room,
        String worldId,
        String casterName,
        String casterType,
        long castTime
    ) {
        public String toJson() {
            return "{\"ts\":\"" + Instant.now() + "\","
                + "\"type\":\"skill_whiff\","
                + "\"caster\":\"" + escape(casterName) + "\","
                + "\"casterType\":\"" + escape(casterType) + "\","
                + "\"skill\":\"" + escape(skillId) + "\","
                + "\"room\":\"" + escape(room) + "\","
                + "\"world\":\"" + escape(worldId) + "\"}";
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
     * Internal tracker for a single entity's skill casts.
     */
    private static final class SkillTracker {
        private final Map<String, SkillCast> pending = new HashMap<>();

        void cast(String id, long now, String room, String worldId, String casterName, String casterType) {
            pending.put(id, new SkillCast(id, now, room, worldId, casterName, casterType));
        }

        void hit(String id) {
            pending.remove(id);
        }

        void checkWhiff(long now, Consumer<SkillWhiff> logger) {
            var iter = pending.values().iterator();
            while (iter.hasNext()) {
                SkillCast cast = iter.next();
                if (now - cast.time > WHIFF_WINDOW_MS) {
                    logger.accept(new SkillWhiff(
                        cast.id, cast.room, cast.worldId,
                        cast.casterName, cast.casterType, cast.time
                    ));
                    iter.remove();
                }
            }
        }

        private record SkillCast(String id, long time, String room, String worldId,
                                  String casterName, String casterType) {}
    }
}
