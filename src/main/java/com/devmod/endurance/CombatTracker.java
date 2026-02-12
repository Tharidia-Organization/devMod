package com.devmod.endurance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestEnded;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestStarted;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.WaveCompleted;
import com.devmod.endurance.lifecycle.QuestLifecycleListener;

/**
 * Combat statistics tracker for Endurance quests.
 *
 * <p>Implements {@link QuestLifecycleListener} to receive quest lifecycle events.
 * Note: CombatTracker is quest-scoped (shared between party members), so it only
 * creates one session per questId and only cleans up when cleanupShared is true.</p>
 *
 * <h2>Priority: 1000 (Critical)</h2>
 * <p>Runs early as other systems depend on combat stats.</p>
 */
public class CombatTracker implements QuestLifecycleListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(CombatTracker.class);

    public static final CombatTracker INSTANCE = new CombatTracker();

    // Active tracking sessions per quest (concurrent for safe access from event handlers)
    private final Map<UUID, QuestCombatSession> activeSessions = new java.util.concurrent.ConcurrentHashMap<>();

    private CombatTracker() {}

    /**
     * Combat session for a single quest attempt.
     */
    public static class QuestCombatSession {
        private final UUID questId;
        private final UUID playerId;
        private final ResourceLocation mobType;
        private final long startTime;

        // Aggregated stats
        private float totalDamageDealt = 0;
        private float totalDamageTaken = 0;
        private int totalHitsLanded = 0;
        private int totalHitsTaken = 0;
        private int criticalHits = 0;
        private int kills = 0;
        private int deaths = 0;

        // Per-weapon stats
        private final Map<String, WeaponStats> weaponStats = new HashMap<>();

        // Per-wave stats
        private final List<WaveCombatStats> waveStats = new ArrayList<>();
        private WaveCombatStats currentWaveStats;

        // Damage breakdown
        private final Map<String, Float> damageByType = new HashMap<>();
        private final Map<String, Float> damageTakenByType = new HashMap<>();

        // Time tracking
        private final List<Long> killTimes = new ArrayList<>();
        private long lastKillTime;

        // Body part hits (if using body part system)
        private final Map<String, Integer> bodyPartHits = new HashMap<>();

        // Per-player stats for party runs
        private final Map<UUID, PlayerCombatStats> playerStats = new HashMap<>();

        public QuestCombatSession(UUID questId, UUID playerId, ResourceLocation mobType) {
            this.questId = questId;
            this.playerId = playerId;
            this.mobType = mobType;
            this.startTime = System.currentTimeMillis();
            this.lastKillTime = startTime;
            // Initialize first wave directly to avoid this-escape
            this.currentWaveStats = new WaveCombatStats(1);
        }

        public void startNewWave(int waveNumber) {
            if (currentWaveStats != null) {
                currentWaveStats.finalizeDuration();
                waveStats.add(currentWaveStats);
            }
            currentWaveStats = new WaveCombatStats(waveNumber);

            // Advance all player stats to the new wave
            for (PlayerCombatStats pStats : playerStats.values()) {
                pStats.startNewWave(waveNumber);
            }
        }

        public void recordDamageDealt(float damage, ItemStack weapon, DamageSource source, String bodyPart) {
            recordDamageDealt(damage, weapon, source, bodyPart, null);
        }

        public void recordDamageDealt(float damage, ItemStack weapon, DamageSource source, String bodyPart, UUID attackerId) {
            // Cap damage to prevent Float.MAX_VALUE or overflow from corrupting analytics
            float cappedDamage = Math.min(damage, 10000f);

            totalDamageDealt += cappedDamage;
            totalHitsLanded++;

            if (currentWaveStats != null) {
                currentWaveStats.damageDealt += cappedDamage;
                currentWaveStats.hitsLanded++;
            }

            // Track per-player stats
            if (attackerId != null) {
                PlayerCombatStats pStats = playerStats.computeIfAbsent(attackerId, PlayerCombatStats::new);
                pStats.totalDamageDealt += cappedDamage;
                pStats.hitsLanded++;
                PlayerWaveCombatStats pwStats = pStats.getCurrentWaveStats();
                pwStats.damageDealt += cappedDamage;
                pwStats.hitsLanded++;
            }

            // Track weapon stats
            String weaponId = getWeaponId(weapon);
            WeaponStats stats = weaponStats.computeIfAbsent(weaponId, k -> new WeaponStats(weaponId));
            stats.totalDamage += cappedDamage;
            stats.hits++;
            if (cappedDamage > stats.maxSingleHit) {
                stats.maxSingleHit = cappedDamage;
            }

            // Track damage type
            String damageType = source.type().msgId();
            float existing = damageByType.getOrDefault(damageType, 0f);
            damageByType.put(damageType, existing + cappedDamage);

            // Track body part
            if (bodyPart != null && !bodyPart.isEmpty()) {
                int hits = bodyPartHits.getOrDefault(bodyPart, 0);
                bodyPartHits.put(bodyPart, hits + 1);
            }
        }

        public void recordCriticalHit(float damage, ItemStack weapon) {
            recordCriticalHit(damage, weapon, null);
        }

        public void recordCriticalHit(float damage, ItemStack weapon, UUID attackerId) {
            criticalHits++;
            if (currentWaveStats != null) {
                currentWaveStats.criticalHits++;
            }

            // Track per-player stats
            if (attackerId != null) {
                PlayerCombatStats pStats = playerStats.computeIfAbsent(attackerId, PlayerCombatStats::new);
                pStats.criticalHits++;
                PlayerWaveCombatStats pwStats = pStats.getCurrentWaveStats();
                pwStats.criticalHits++;
            }

            String weaponId = getWeaponId(weapon);
            WeaponStats stats = weaponStats.computeIfAbsent(weaponId, k -> new WeaponStats(weaponId));
            stats.criticalHits++;
        }

        public void recordDamageTaken(float damage, DamageSource source) {
            recordDamageTaken(damage, source, null);
        }

        public void recordDamageTaken(float damage, DamageSource source, UUID victimId) {
            // Cap damage to prevent Float.MAX_VALUE from /kill or similar commands
            // polluting the analytics with Infinity values
            float cappedDamage = Math.min(damage, 10000f);

            totalDamageTaken += cappedDamage;
            totalHitsTaken++;

            if (currentWaveStats != null) {
                currentWaveStats.damageTaken += cappedDamage;
                currentWaveStats.hitsTaken++;
            }

            // Track per-player stats
            if (victimId != null) {
                PlayerCombatStats pStats = playerStats.computeIfAbsent(victimId, PlayerCombatStats::new);
                pStats.totalDamageTaken += cappedDamage;
                PlayerWaveCombatStats pwStats = pStats.getCurrentWaveStats();
                pwStats.damageTaken += cappedDamage;
            }

            String damageType = source.type().msgId();
            float existing = damageTakenByType.getOrDefault(damageType, 0f);
            damageTakenByType.put(damageType, existing + cappedDamage);
        }

        public void recordKill(long timeSinceLastKill) {
            recordKill(timeSinceLastKill, null, false);
        }

        public void recordKill(long timeSinceLastKill, UUID killerId) {
            recordKill(timeSinceLastKill, killerId, false);
        }

        public void recordKill(long timeSinceLastKill, UUID killerId, boolean isElite) {
            kills++;
            killTimes.add(timeSinceLastKill);
            lastKillTime = System.currentTimeMillis();

            if (currentWaveStats != null) {
                currentWaveStats.kills++;
            }

            // Track per-player stats
            if (killerId != null) {
                PlayerCombatStats pStats = playerStats.computeIfAbsent(killerId, PlayerCombatStats::new);
                pStats.totalKills++;
                PlayerWaveCombatStats pwStats = pStats.getCurrentWaveStats();
                pwStats.kills++;
                if (isElite) {
                    pwStats.eliteKills++;
                }
            }
        }

        /**
         * Update max combo for a player in the current wave.
         * Called when combo changes to track per-wave max.
         */
        public void updatePlayerCombo(UUID playerId, int currentCombo) {
            if (playerId == null) return;
            PlayerCombatStats pStats = playerStats.get(playerId);
            if (pStats != null) {
                PlayerWaveCombatStats pwStats = pStats.getCurrentWaveStats();
                pwStats.updateMaxCombo(currentCombo);
            }
        }

        public void recordDeath() {
            recordDeath(null);
        }

        public void recordDeath(UUID victimId) {
            deaths++;
            if (currentWaveStats != null) {
                currentWaveStats.deaths++;
            }

            // Track per-player stats
            if (victimId != null) {
                PlayerCombatStats pStats = playerStats.computeIfAbsent(victimId, PlayerCombatStats::new);
                pStats.totalDeaths++;
                PlayerWaveCombatStats pwStats = pStats.getCurrentWaveStats();
                pwStats.deaths++;
            }
        }

        private String getWeaponId(ItemStack weapon) {
            if (weapon == null || weapon.isEmpty()) {
                return "minecraft:fist";
            }
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(weapon.getItem()));
            return key.toString();
        }

        // Getters
        public UUID getQuestId() { return questId; }
        public UUID getPlayerId() { return playerId; }
        public ResourceLocation getMobType() { return mobType; }
        public long getStartTime() { return startTime; }
        public float getTotalDamageDealt() { return totalDamageDealt; }
        public float getTotalDamageTaken() { return totalDamageTaken; }
        public int getTotalHitsLanded() { return totalHitsLanded; }
        public int getTotalHitsTaken() { return totalHitsTaken; }
        public int getCriticalHits() { return criticalHits; }
        public int getKills() { return kills; }
        public int getDeaths() { return deaths; }
        public Map<String, WeaponStats> getWeaponStats() { return weaponStats; }
        public List<WaveCombatStats> getWaveStats() { return waveStats; }
        public WaveCombatStats getCurrentWaveStats() { return currentWaveStats; }

        /**
         * Get wave stats by wave number. Checks both completed waves and current wave.
         */
        public WaveCombatStats getWaveStats(int waveNumber) {
            // Check if it's the current wave
            if (currentWaveStats != null && currentWaveStats.getWaveNumber() == waveNumber) {
                return currentWaveStats;
            }
            // Check completed waves
            for (WaveCombatStats ws : waveStats) {
                if (ws.getWaveNumber() == waveNumber) {
                    return ws;
                }
            }
            return null;
        }

        public Map<String, Float> getDamageByType() { return damageByType; }
        public Map<String, Float> getDamageTakenByType() { return damageTakenByType; }
        public Map<String, Integer> getBodyPartHits() { return bodyPartHits; }
        public Map<UUID, PlayerCombatStats> getPlayerStats() { return playerStats; }

        /**
         * Get per-player stats for a specific player.
         */
        public PlayerCombatStats getPlayerStats(UUID playerId) {
            return playerStats.get(playerId);
        }

        /**
         * Get per-player wave stats for a specific player and wave.
         */
        public PlayerWaveCombatStats getPlayerWaveStats(UUID playerId, int waveNumber) {
            PlayerCombatStats pStats = playerStats.get(playerId);
            return pStats != null ? pStats.getWaveStats(waveNumber) : null;
        }

        /**
         * Get current wave stats for a specific player.
         */
        public PlayerWaveCombatStats getPlayerCurrentWaveStats(UUID playerId) {
            PlayerCombatStats pStats = playerStats.get(playerId);
            return pStats != null ? pStats.getCurrentWaveStats() : null;
        }

        /**
         * Snapshot all player wave stats for a specific wave before advancing.
         * Returns a map of playerId -> PlayerWaveCombatStats for the given wave.
         * This is safe to call even after startNewWave() since stats are preserved.
         */
        public java.util.Map<UUID, PlayerWaveCombatStats> snapshotPlayerWaveStats(int waveNumber) {
            java.util.Map<UUID, PlayerWaveCombatStats> snapshot = new java.util.HashMap<>();
            for (java.util.Map.Entry<UUID, PlayerCombatStats> entry : playerStats.entrySet()) {
                PlayerWaveCombatStats waveStats = entry.getValue().getWaveStats(waveNumber);
                if (waveStats != null) {
                    snapshot.put(entry.getKey(), waveStats);
                }
            }
            return snapshot;
        }

        public float getAverageDamagePerHit() {
            return totalHitsLanded > 0 ? totalDamageDealt / totalHitsLanded : 0;
        }

        public float getCriticalHitRate() {
            return totalHitsLanded > 0 ? (float) criticalHits / totalHitsLanded : 0;
        }

        public float getAverageKillTime() {
            if (killTimes.isEmpty()) return 0;
            long total = killTimes.stream().mapToLong(Long::longValue).sum();
            return (float) total / killTimes.size();
        }

        public long getSessionDuration() {
            return System.currentTimeMillis() - startTime;
        }

        public float getDPS() {
            long duration = getSessionDuration();
            return duration > 0 ? totalDamageDealt / (duration / 1000f) : 0;
        }

        public void finalizeSession() {
            if (currentWaveStats != null) {
                waveStats.add(currentWaveStats);
            }
        }
    }

    /**
     * Stats for a single weapon.
     */
    public static class WeaponStats {
        private final String weaponId;
        public float totalDamage = 0;
        public int hits = 0;
        public int criticalHits = 0;
        public float maxSingleHit = 0;

        public WeaponStats(String weaponId) {
            this.weaponId = weaponId;
        }

        public String getWeaponId() { return weaponId; }

        public float getAverageDamage() {
            return hits > 0 ? totalDamage / hits : 0;
        }

        public float getCritRate() {
            return hits > 0 ? (float) criticalHits / hits : 0;
        }
    }

    /**
     * Stats for a single wave.
     */
    public static class WaveCombatStats {
        private final int waveNumber;
        public float damageDealt = 0;
        public float damageTaken = 0;
        public int hitsLanded = 0;
        public int hitsTaken = 0;
        public int criticalHits = 0;
        public int kills = 0;
        public int deaths = 0;
        public long duration = 0;
        public long startTime = System.currentTimeMillis();

        public WaveCombatStats(int waveNumber) {
            this.waveNumber = waveNumber;
        }

        public int getWaveNumber() { return waveNumber; }

        /**
         * Finalize this wave's duration.
         */
        public void finalizeDuration() {
            if (duration == 0) {
                duration = System.currentTimeMillis() - startTime;
            }
        }
    }

    /**
     * Per-player combat stats within a quest session.
     * Used for party runs to track individual contributions.
     */
    public static class PlayerCombatStats {
        private final UUID playerId;
        public float totalDamageDealt = 0;
        public float totalDamageTaken = 0;
        public int totalKills = 0;
        public int totalDeaths = 0;
        public int criticalHits = 0;
        public int hitsLanded = 0;

        // Per-wave stats for this player
        private final Map<Integer, PlayerWaveCombatStats> waveStats = new HashMap<>();
        private int currentWaveNumber = 1;

        public PlayerCombatStats(UUID playerId) {
            this.playerId = playerId;
            this.waveStats.put(1, new PlayerWaveCombatStats(1));
        }

        public UUID getPlayerId() { return playerId; }

        public void startNewWave(int waveNumber) {
            PlayerWaveCombatStats current = waveStats.get(currentWaveNumber);
            if (current != null) {
                current.finalizeDuration();
            }
            currentWaveNumber = waveNumber;
            waveStats.computeIfAbsent(waveNumber, w -> new PlayerWaveCombatStats(w));
        }

        public PlayerWaveCombatStats getCurrentWaveStats() {
            return waveStats.computeIfAbsent(currentWaveNumber, w -> new PlayerWaveCombatStats(w));
        }

        public PlayerWaveCombatStats getWaveStats(int waveNumber) {
            return waveStats.get(waveNumber);
        }

        public Map<Integer, PlayerWaveCombatStats> getAllWaveStats() {
            return waveStats;
        }
    }

    /**
     * Per-player per-wave combat stats.
     */
    public static class PlayerWaveCombatStats {
        public final int waveNumber;
        public float damageDealt = 0;
        public float damageTaken = 0;
        public int kills = 0;
        public int eliteKills = 0;
        public int deaths = 0;
        public int criticalHits = 0;
        public int hitsLanded = 0;
        public int maxCombo = 0;

        public PlayerWaveCombatStats(int waveNumber) {
            this.waveNumber = waveNumber;
        }

        public void updateMaxCombo(int combo) {
            if (combo > maxCombo) {
                maxCombo = combo;
            }
        }

        public void finalizeDuration() {
            // Duration is now tracked via shared WaveCombatStats, not per-player
        }
    }

    // ========== Session Management ==========

    /**
     * Start tracking combat for a quest.
     */
    public QuestCombatSession startTracking(UUID questId, UUID playerId, ResourceLocation mobType) {
        QuestCombatSession session = new QuestCombatSession(questId, playerId, mobType);
        activeSessions.put(questId, session);
        LOGGER.debug("[CombatTracker] Started tracking for quest {}", questId);
        return session;
    }

    /**
     * Stop tracking and return final session data.
     */
    public QuestCombatSession stopTracking(UUID questId) {
        QuestCombatSession session = activeSessions.remove(questId);
        if (session != null) {
            session.finalizeSession();
            LOGGER.debug("[CombatTracker] Stopped tracking for quest {} - {} kills, {} damage dealt",
                questId, session.kills, session.totalDamageDealt);
        }
        return session;
    }

    /**
     * Get active session for a quest.
     */
    public Optional<QuestCombatSession> getSession(UUID questId) {
        return Optional.ofNullable(activeSessions.get(questId));
    }

    // ========== QuestLifecycleListener Implementation ==========

    @Override
    public void onQuestStarted(QuestStarted event) {
        UUID questId = event.questId();
        // Only create session if none exists (shared per quest)
        if (getSession(questId).isEmpty()) {
            UUID playerId = event.context().playerId();
            ResourceLocation mobType = event.context().quest().getMobConfig().getMobId();
            startTracking(questId, playerId, mobType);
        }
    }

    @Override
    public void onQuestEnded(QuestEnded event) {
        // Only stop if cleanupShared is true (last player leaving)
        if (event.cleanupShared()) {
            stopTracking(event.questId());
        }
    }

    @Override
    public void onWaveCompleted(WaveCompleted event) {
        // Advance to next wave in combat tracker
        if (event.applyShared()) {
            QuestCombatSession session = activeSessions.get(event.questId());
            if (session != null) {
                session.startNewWave(event.context().waveNumber() + 1);
            }
        }
    }

    @Override
    public int getPriority() {
        return 1000; // Critical - runs early as other systems depend on combat stats
    }

    @Override
    public String getListenerName() {
        return "CombatTracker";
    }

    // ========== Event Handlers ==========

    /**
     * Called when player deals damage to a quest mob.
     */
    public void onPlayerDealsDamage(Player player, Entity target, float damage, DamageSource source, String bodyPart) {
        // Find the quest this player is in
        EnduranceQuestManager.INSTANCE.getActiveSession(player).ifPresent(questSession -> {
            QuestCombatSession combatSession = activeSessions.get(questSession.getQuest().getQuestId());
            if (combatSession != null) {
                ItemStack weapon = player.getMainHandItem();
                // Pass player UUID for per-player tracking
                combatSession.recordDamageDealt(damage, weapon, source, bodyPart, player.getUUID());
            }
        });
    }

    /**
     * Called when player lands a critical hit.
     */
    public void onCriticalHit(Player player, float damage) {
        EnduranceQuestManager.INSTANCE.getActiveSession(player).ifPresent(questSession -> {
            QuestCombatSession combatSession = activeSessions.get(questSession.getQuest().getQuestId());
            if (combatSession != null) {
                ItemStack weapon = player.getMainHandItem();
                // Pass player UUID for per-player tracking
                combatSession.recordCriticalHit(damage, weapon, player.getUUID());
            }
        });
    }

    /**
     * Called when player takes damage.
     */
    public void onPlayerTakesDamage(Player player, float damage, DamageSource source) {
        EnduranceQuestManager.INSTANCE.getActiveSession(player).ifPresent(questSession -> {
            QuestCombatSession combatSession = activeSessions.get(questSession.getQuest().getQuestId());
            if (combatSession != null) {
                // Pass player UUID for per-player tracking
                combatSession.recordDamageTaken(damage, source, player.getUUID());
            }
        });
    }

    /**
     * Called when player kills a quest mob.
     */
    public void onMobKilled(Player player, LivingEntity mob) {
        onMobKilled(player, mob, false);
    }

    /**
     * Called when player kills a quest mob with elite tracking.
     */
    public void onMobKilled(Player player, LivingEntity mob, boolean isElite) {
        EnduranceQuestManager.INSTANCE.getActiveSession(player).ifPresent(questSession -> {
            QuestCombatSession combatSession = activeSessions.get(questSession.getQuest().getQuestId());
            if (combatSession != null) {
                long timeSinceLastKill = System.currentTimeMillis() - combatSession.lastKillTime;
                // Pass player UUID and elite status for per-player tracking
                combatSession.recordKill(timeSinceLastKill, player.getUUID(), isElite);
            }
        });
    }

    /**
     * Called when player dies.
     */
    public void onPlayerDeath(Player player) {
        EnduranceQuestManager.INSTANCE.getActiveSession(player).ifPresent(questSession -> {
            QuestCombatSession combatSession = activeSessions.get(questSession.getQuest().getQuestId());
            if (combatSession != null) {
                // Pass player UUID for per-player tracking
                combatSession.recordDeath(player.getUUID());
            }
        });
    }

    /**
     * Called when a new wave starts.
     */
    public void onWaveStart(UUID questId, int waveNumber) {
        QuestCombatSession session = activeSessions.get(questId);
        if (session != null) {
            session.startNewWave(waveNumber);
        }
    }

    // ========== Analytics Queries ==========

    /**
     * Get most effective weapon from a session.
     */
    public static String getMostEffectiveWeapon(QuestCombatSession session) {
        return session.weaponStats.entrySet().stream()
            .max(Comparator.comparingDouble(e -> e.getValue().totalDamage))
            .map(Map.Entry::getKey)
            .orElse("none");
    }

    /**
     * Get most targeted body part.
     */
    public static String getMostTargetedBodyPart(QuestCombatSession session) {
        return session.bodyPartHits.entrySet().stream()
            .max(Comparator.comparingInt(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse("none");
    }
}
