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

public class CombatTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(CombatTracker.class);

    public static final CombatTracker INSTANCE = new CombatTracker();

    // Active tracking sessions per quest
    private final Map<UUID, QuestCombatSession> activeSessions = new HashMap<>();

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
                waveStats.add(currentWaveStats);
            }
            currentWaveStats = new WaveCombatStats(waveNumber);
        }

        public void recordDamageDealt(float damage, ItemStack weapon, DamageSource source, String bodyPart) {
            // Cap damage to prevent Float.MAX_VALUE or overflow from corrupting analytics
            float cappedDamage = Math.min(damage, 10000f);

            totalDamageDealt += cappedDamage;
            totalHitsLanded++;

            if (currentWaveStats != null) {
                currentWaveStats.damageDealt += cappedDamage;
                currentWaveStats.hitsLanded++;
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
            criticalHits++;
            if (currentWaveStats != null) {
                currentWaveStats.criticalHits++;
            }

            String weaponId = getWeaponId(weapon);
            WeaponStats stats = weaponStats.computeIfAbsent(weaponId, k -> new WeaponStats(weaponId));
            stats.criticalHits++;
        }

        public void recordDamageTaken(float damage, DamageSource source) {
            // Cap damage to prevent Float.MAX_VALUE from /kill or similar commands
            // polluting the analytics with Infinity values
            float cappedDamage = Math.min(damage, 10000f);

            totalDamageTaken += cappedDamage;
            totalHitsTaken++;

            if (currentWaveStats != null) {
                currentWaveStats.damageTaken += cappedDamage;
                currentWaveStats.hitsTaken++;
            }

            String damageType = source.type().msgId();
            float existing = damageTakenByType.getOrDefault(damageType, 0f);
            damageTakenByType.put(damageType, existing + cappedDamage);
        }

        public void recordKill(long timeSinceLastKill) {
            kills++;
            killTimes.add(timeSinceLastKill);
            lastKillTime = System.currentTimeMillis();

            if (currentWaveStats != null) {
                currentWaveStats.kills++;
            }
        }

        public void recordDeath() {
            deaths++;
            if (currentWaveStats != null) {
                currentWaveStats.deaths++;
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
        public Map<String, Float> getDamageByType() { return damageByType; }
        public Map<String, Float> getDamageTakenByType() { return damageTakenByType; }
        public Map<String, Integer> getBodyPartHits() { return bodyPartHits; }

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
        public final String weaponId;
        public float totalDamage = 0;
        public int hits = 0;
        public int criticalHits = 0;
        public float maxSingleHit = 0;

        public WeaponStats(String weaponId) {
            this.weaponId = weaponId;
        }

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
        public final int waveNumber;
        public float damageDealt = 0;
        public float damageTaken = 0;
        public int hitsLanded = 0;
        public int hitsTaken = 0;
        public int criticalHits = 0;
        public int kills = 0;
        public int deaths = 0;
        public long duration = 0;

        public WaveCombatStats(int waveNumber) {
            this.waveNumber = waveNumber;
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
                combatSession.recordDamageDealt(damage, weapon, source, bodyPart);
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
                combatSession.recordCriticalHit(damage, weapon);
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
                combatSession.recordDamageTaken(damage, source);
            }
        });
    }

    /**
     * Called when player kills a quest mob.
     */
    public void onMobKilled(Player player, LivingEntity mob) {
        EnduranceQuestManager.INSTANCE.getActiveSession(player).ifPresent(questSession -> {
            QuestCombatSession combatSession = activeSessions.get(questSession.getQuest().getQuestId());
            if (combatSession != null) {
                long timeSinceLastKill = System.currentTimeMillis() - combatSession.lastKillTime;
                combatSession.recordKill(timeSinceLastKill);
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
                combatSession.recordDeath();
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
