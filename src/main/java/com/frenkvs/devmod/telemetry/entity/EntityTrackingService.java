package com.frenkvs.devmod.telemetry.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servizio per il tracking delle entità e comportamenti.
 *
 * Gestisce:
 * - Position tracking (stuck detection)
 * - Aggro tracking (aggro drop detection)
 * - Spawn info (posizione e mondo di spawn)
 * - Camping detection (player che attaccano dalla stessa posizione)
 * - Minion wave tracking (concurrent minions)
 */
public class EntityTrackingService {
    public static final EntityTrackingService INSTANCE = new EntityTrackingService();

    // Position trackers for stuck detection
    private final Map<UUID, PositionTracker> positionTrackers = new ConcurrentHashMap<>();

    // Aggro trackers
    private final Map<UUID, AggroTracker> aggroTrackers = new ConcurrentHashMap<>();

    // Spawn info
    private final Map<UUID, SpawnInfo> spawnInfo = new ConcurrentHashMap<>();

    // Camping trackers
    private final Map<UUID, CampingTracker> campingTrackers = new ConcurrentHashMap<>();

    // Minion wave trackers per room
    private final Map<String, MinionWaveTracker> minionWaves = new ConcurrentHashMap<>();

    // Path trackers for kiting/spin detection
    private final Map<UUID, PathTracker> pathTrackers = new ConcurrentHashMap<>();

    // Configuration
    private long stuckThresholdMs = 3000;
    private long aggroDropThresholdMs = 5000;
    private int campingHitsThreshold = 5;
    private long campingTimeThresholdMs = 5000;
    private double bossHpThreshold = 100.0;

    private EntityTrackingService() {}

    // ============================================
    // CONFIGURATION
    // ============================================

    public void setStuckThresholdMs(long ms) { this.stuckThresholdMs = ms; }
    public void setAggroDropThresholdMs(long ms) { this.aggroDropThresholdMs = ms; }
    public void setCampingHitsThreshold(int hits) { this.campingHitsThreshold = hits; }
    public void setCampingTimeThresholdMs(long ms) { this.campingTimeThresholdMs = ms; }
    public void setBossHpThreshold(double hp) { this.bossHpThreshold = hp; }

    // ============================================
    // STUCK DETECTION
    // ============================================

    /**
     * Aggiorna la posizione di un'entità e controlla se è bloccata.
     * @return true se l'entità è stata rilevata come bloccata
     */
    public boolean checkStuck(LivingEntity entity) {
        if (entity.level().isClientSide()) return false;

        UUID id = entity.getUUID();
        long now = System.currentTimeMillis();
        PositionTracker tracker = positionTrackers.get(id);

        if (tracker == null) {
            positionTrackers.put(id, new PositionTracker(
                    entity.level().dimension(),
                    entity.getX(), entity.getY(), entity.getZ(), now));
            return false;
        }

        double dx = tracker.x - entity.getX();
        double dz = tracker.z - entity.getZ();
        double distSqr = dx * dx + dz * dz;

        if (distSqr < 0.01 && now - tracker.lastMoveMs > stuckThresholdMs) {
            // Entity is stuck - reset timer to avoid spam
            tracker.lastMoveMs = now;
            return true;
        } else if (distSqr >= 0.01) {
            // Entity moved - update position
            tracker.x = entity.getX();
            tracker.y = entity.getY();
            tracker.z = entity.getZ();
            tracker.lastMoveMs = now;
        }

        return false;
    }

    /**
     * Ottiene la posizione dell'ultimo movimento di un'entità.
     */
    public Optional<BlockPos> getLastPosition(UUID entityId) {
        PositionTracker tracker = positionTrackers.get(entityId);
        if (tracker == null) return Optional.empty();
        return Optional.of(new BlockPos((int) tracker.x, (int) tracker.y, (int) tracker.z));
    }

    // ============================================
    // AGGRO TRACKING
    // ============================================

    /**
     * Aggiorna lo stato di aggro di un mob.
     * EDGE-BASED: fires only on transition hadTarget=true -> hasTarget=false
     * with cooldown to prevent spam.
     * @return true se il mob ha perso l'aggro (edge transition + cooldown passed)
     */
    public boolean checkAggroDrop(Mob mob) {
        UUID id = mob.getUUID();
        long now = System.currentTimeMillis();
        AggroTracker tracker = aggroTrackers.computeIfAbsent(id, k -> new AggroTracker());

        boolean hasTarget = mob.getTarget() != null;

        if (hasTarget) {
            // Currently has target - update state
            tracker.hadTarget = true;
            return false;
        }

        // No target now - check for edge transition
        if (tracker.hadTarget) {
            // Edge: had target -> no target
            tracker.hadTarget = false;

            // Check cooldown (30s default via aggroDropThresholdMs, repurposed)
            if (now - tracker.lastFiredMs > aggroDropThresholdMs) {
                tracker.lastFiredMs = now;
                return true;
            }
        }

        return false;
    }

    // ============================================
    // SPAWN INFO
    // ============================================

    /**
     * Registra informazioni di spawn per un'entità.
     */
    public void registerSpawn(UUID entityId, String worldId, Vec3 position) {
        spawnInfo.put(entityId, new SpawnInfo(worldId, position));
    }

    /**
     * Ottiene le informazioni di spawn di un'entità.
     */
    public Optional<SpawnInfo> getSpawnInfo(UUID entityId) {
        return Optional.ofNullable(spawnInfo.get(entityId));
    }

    /**
     * Rimuove e restituisce le informazioni di spawn.
     */
    public Optional<SpawnInfo> removeSpawnInfo(UUID entityId) {
        return Optional.ofNullable(spawnInfo.remove(entityId));
    }

    // ============================================
    // CAMPING DETECTION
    // ============================================

    /**
     * Registra un hit da un player e controlla se sta campando.
     * @return true se il player è stato rilevato come campante
     */
    public boolean checkCamping(ServerPlayer player, LivingEntity target) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        BlockPos pos = player.blockPosition();
        CampingTracker tracker = campingTrackers.get(id);

        if (tracker == null || !tracker.pos.equals(pos)) {
            campingTrackers.put(id, new CampingTracker(pos, now, 1));
            return false;
        }

        tracker.hits++;

        if (tracker.hits >= campingHitsThreshold && now - tracker.startMs > campingTimeThresholdMs) {
            // Reset to avoid spam
            tracker.startMs = now;
            tracker.hits = 0;
            return true;
        }

        return false;
    }

    /**
     * Ottiene la posizione di camping di un player.
     */
    public Optional<BlockPos> getCampingPosition(UUID playerId) {
        CampingTracker tracker = campingTrackers.get(playerId);
        if (tracker == null) return Optional.empty();
        return Optional.of(tracker.pos);
    }

    // ============================================
    // MINION WAVE TRACKING
    // ============================================

    /**
     * Registra lo spawn di un minion in una stanza.
     */
    public void registerMinionSpawn(String room, UUID minionId, double maxHp) {
        if (maxHp >= bossHpThreshold) return; // Skip bosses
        minionWaves.computeIfAbsent(room, k -> new MinionWaveTracker()).spawn(minionId);
    }

    /**
     * Registra la morte di un minion.
     */
    public void registerMinionDeath(String room, UUID minionId, double damageDone) {
        MinionWaveTracker tracker = minionWaves.get(room);
        if (tracker != null) {
            tracker.death(minionId, damageDone);
        }
    }

    /**
     * Ottiene le statistiche dei minion per una stanza.
     */
    public Optional<MinionWaveStats> getMinionWaveStats(String room) {
        MinionWaveTracker tracker = minionWaves.get(room);
        if (tracker == null) return Optional.empty();
        return Optional.of(new MinionWaveStats(
                tracker.peakConcurrent,
                tracker.totalSpawned,
                tracker.totalDamage,
                tracker.getCurrentCount()
        ));
    }

    /**
     * Ottiene le statistiche di tutti i minion wave.
     */
    public List<String> getAllMinionWaveStats() {
        List<String> stats = new ArrayList<>();
        minionWaves.forEach((room, tracker) -> {
            stats.add(String.format("%s peak=%d spawned=%d dmg=%.1f active=%d",
                    room, tracker.peakConcurrent, tracker.totalSpawned,
                    tracker.totalDamage, tracker.getCurrentCount()));
        });
        return stats;
    }

    // ============================================
    // PATH TRACKING (KITING/SPIN)
    // ============================================

    /**
     * Aggiorna il path tracker per un mob e rileva problemi.
     * @return tipo di problema rilevato, o null se nessuno
     */
    public String updatePathAndDetectIssue(Mob mob) {
        if (mob.getMaxHealth() < 40) return null; // Only track likely bosses

        UUID id = mob.getUUID();
        PathTracker tracker = pathTrackers.computeIfAbsent(id, k -> new PathTracker());
        tracker.addSample(mob.position(), mob.getYRot(), System.currentTimeMillis());

        String issue = tracker.detectIssue();
        if (issue != null) {
            tracker.reset();
        }
        return issue;
    }

    // ============================================
    // CLEANUP
    // ============================================

    /**
     * Pulisce tutti i dati per un'entità rimossa.
     */
    public void cleanupEntity(UUID entityId) {
        positionTrackers.remove(entityId);
        aggroTrackers.remove(entityId);
        spawnInfo.remove(entityId);
        campingTrackers.remove(entityId);
        pathTrackers.remove(entityId);
    }

    /**
     * Pulisce i dati di una stanza.
     */
    public void cleanupRoom(String room) {
        minionWaves.remove(room);
    }

    /**
     * Resetta tutti i tracker.
     */
    public void clearAll() {
        positionTrackers.clear();
        aggroTrackers.clear();
        spawnInfo.clear();
        campingTrackers.clear();
        minionWaves.clear();
        pathTrackers.clear();
    }

    // ============================================
    // DATA CLASSES
    // ============================================

    private static class PositionTracker {
        final ResourceKey<Level> dimension;
        double x, y, z;
        long lastMoveMs;

        PositionTracker(ResourceKey<Level> dim, double x, double y, double z, long ms) {
            this.dimension = dim;
            com.mojang.logging.LogUtils.getLogger().debug("[EntityTracking] Created position tracker for dimension {}", getDimension());
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastMoveMs = ms;
        }

        public ResourceKey<Level> getDimension() {
            return dimension;
        }
    }

    private static class AggroTracker {
        long lastFiredMs = 0;  // Cooldown: when we last fired aggro_drop
        boolean hadTarget = false;  // Edge detection: was targeting last check
    }

    /**
     * Informazioni di spawn di un'entità.
     */
    public record SpawnInfo(String worldId, Vec3 position) {
        public double distanceFrom(Vec3 currentPos) {
            return Objects.requireNonNull(position).distanceTo(Objects.requireNonNull(currentPos));
        }
    }

    private static class CampingTracker {
        final BlockPos pos;
        long startMs;
        int hits;

        CampingTracker(BlockPos pos, long startMs, int hits) {
            this.pos = pos;
            this.startMs = startMs;
            this.hits = hits;
        }
    }

    private static class MinionWaveTracker {
        final Set<UUID> activeMinions = ConcurrentHashMap.newKeySet();
        int peakConcurrent = 0;
        int totalSpawned = 0;
        double totalDamage = 0;

        void spawn(UUID minionId) {
            activeMinions.add(minionId);
            totalSpawned++;
            if (activeMinions.size() > peakConcurrent) {
                peakConcurrent = activeMinions.size();
            }
        }

        void death(UUID minionId, double damageDone) {
            activeMinions.remove(minionId);
            totalDamage += damageDone;
        }

        int getCurrentCount() {
            return activeMinions.size();
        }
    }

    /**
     * Statistiche aggregate per minion wave.
     */
    public record MinionWaveStats(int peakConcurrent, int totalSpawned, double totalDamage, int currentActive) {
        @Override
        public String toString() {
            return String.format("MinionWave{peak=%d, spawned=%d, dmg=%.1f, active=%d}",
                    peakConcurrent, totalSpawned, totalDamage, currentActive);
        }
    }

    private static class PathTracker {
        private static final int MAX_SAMPLES = 40;
        private final ArrayDeque<Sample> samples = new ArrayDeque<>();

        void addSample(Vec3 pos, float yaw, long now) {
            samples.addLast(new Sample(pos, yaw, now));
            while (samples.size() > MAX_SAMPLES) {
                samples.removeFirst();
            }
        }

        String detectIssue() {
            if (samples.size() < 5) return null;

            double pathLen = 0;
            double yawChange = 0;
            Vec3 last = Objects.requireNonNull(samples.getFirst().pos);
            float lastYaw = samples.getFirst().yaw;

            for (Sample s : samples) {
                Vec3 samplePos = Objects.requireNonNull(s.pos);
                pathLen += samplePos.distanceTo(last);
                yawChange += Math.abs(wrapYaw(s.yaw - lastYaw));
                last = samplePos;
                lastYaw = s.yaw;
            }

            double displacement = Objects.requireNonNull(samples.getFirst().pos).distanceTo(Objects.requireNonNull(samples.getLast().pos));

            if (pathLen < 1.0 && yawChange > 720) {
                return "spin";
            }
            if (pathLen > 12 && displacement < 4.0) {
                return "kiting_path";
            }
            return null;
        }

        void reset() {
            samples.clear();
        }

        private float wrapYaw(float yaw) {
            yaw = yaw % 360f;
            if (yaw > 180f) yaw -= 360f;
            if (yaw < -180f) yaw += 360f;
            return yaw;
        }

        private record Sample(Vec3 pos, float yaw, long time) {}
    }
}
