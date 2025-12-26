package com.devmod.endurance;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.minecraft.resources.ResourceLocation;

public class EnduranceAnalytics {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnduranceAnalytics.class);
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create();

    public static final EnduranceAnalytics INSTANCE = new EnduranceAnalytics();

    private Path dataDirectory;
    private final List<QuestSessionRecord> recentSessions = new ArrayList<>();
    private final Map<String, MobAnalytics> mobAnalytics = new HashMap<>();
    private final Map<String, WeaponAnalytics> weaponAnalytics = new HashMap<>();

    private static final int MAX_RECENT_SESSIONS = 100;

    private EnduranceAnalytics() {}

    /**
     * Initialize analytics storage.
     */
    public void initialize(Path configDir) {
        this.dataDirectory = configDir.resolve("devmod").resolve("endurance_analytics");
        try {
            Files.createDirectories(dataDirectory);
            Files.createDirectories(dataDirectory.resolve("sessions"));
            Files.createDirectories(dataDirectory.resolve("aggregates"));
            loadAggregates();
            LOGGER.info("[EnduranceAnalytics] Initialized analytics storage at {}", dataDirectory);
        } catch (IOException e) {
            LOGGER.error("[EnduranceAnalytics] Failed to initialize storage", e);
        }
    }

    // ========== Data Structures ==========

    /**
     * Complete record of a quest session.
     */
    public static class QuestSessionRecord {
        public String sessionId;
        public String playerId;
        public String playerName;
        public String mobType;
        public String mobDisplayName;
        public long timestamp;
        public String dateTime;
        public long durationMs;
        public boolean completed;
        public int wavesReached;
        public int totalWaves;

        // Combat stats
        public float totalDamageDealt;
        public float totalDamageTaken;
        public int totalKills;
        public int totalDeaths;
        public float dps;
        public float averageKillTime;
        public float criticalHitRate;

        // Weapon breakdown
        public List<WeaponUsageRecord> weaponUsage = new ArrayList<>();

        // Wave breakdown
        public List<WaveRecord> waves = new ArrayList<>();

        // Body part targeting
        public Map<String, Integer> bodyPartHits = new HashMap<>();

        // Damage type breakdown
        public Map<String, Float> damageByType = new HashMap<>();
        public Map<String, Float> damageTakenByType = new HashMap<>();
    }

    public static class WeaponUsageRecord {
        public String weaponId;
        public String weaponName;
        public float totalDamage;
        public int hits;
        public int criticalHits;
        public float maxSingleHit;
        public float averageDamage;
        public float critRate;
        public float damageShare; // percentage of total damage
    }

    public static class WaveRecord {
        public int waveNumber;
        public float damageDealt;
        public float damageTaken;
        public int kills;
        public int deaths;
        public long durationMs;
    }

    /**
     * Aggregated analytics for a mob type.
     */
    public static class MobAnalytics {
        public String mobId;
        public String displayName;
        public int totalAttempts = 0;
        public int totalCompletions = 0;
        public float averageWavesReached = 0;
        public float averageDamageDealt = 0;
        public float averageDamageTaken = 0;
        public float averageKillTime = 0;
        public float averageDPS = 0;
        public long averageDuration = 0;

        // Difficulty indicators
        public float completionRate = 0;
        public float deathRate = 0;
        public float damageRatio = 0; // dealt/taken

        // Best weapons against this mob
        public Map<String, Float> weaponEffectiveness = new HashMap<>();

        // Best body parts to target
        public Map<String, Float> bodyPartEffectiveness = new HashMap<>();

        public void recalculate(List<QuestSessionRecord> sessions) {
            if (sessions.isEmpty()) return;

            totalAttempts = sessions.size();
            totalCompletions = (int) sessions.stream().filter(s -> s.completed).count();

            averageWavesReached = (float) sessions.stream().mapToInt(s -> s.wavesReached).average().orElse(0);
            averageDamageDealt = (float) sessions.stream().mapToDouble(s -> s.totalDamageDealt).average().orElse(0);
            averageDamageTaken = (float) sessions.stream().mapToDouble(s -> s.totalDamageTaken).average().orElse(0);
            averageKillTime = (float) sessions.stream().mapToDouble(s -> s.averageKillTime).average().orElse(0);
            averageDPS = (float) sessions.stream().mapToDouble(s -> s.dps).average().orElse(0);
            averageDuration = (long) sessions.stream().mapToLong(s -> s.durationMs).average().orElse(0);

            completionRate = totalAttempts > 0 ? (float) totalCompletions / totalAttempts : 0;
            deathRate = (float) sessions.stream().mapToInt(s -> s.totalDeaths).sum() / totalAttempts;
            damageRatio = averageDamageTaken > 0 ? averageDamageDealt / averageDamageTaken : 0;

            // Calculate weapon effectiveness
            Map<String, List<Float>> weaponDamages = new HashMap<>();
            for (QuestSessionRecord session : sessions) {
                for (WeaponUsageRecord weapon : session.weaponUsage) {
                    weaponDamages.computeIfAbsent(weapon.weaponId, k -> new ArrayList<>())
                        .add(weapon.averageDamage);
                }
            }
            weaponEffectiveness.clear();
            for (Map.Entry<String, List<Float>> entry : weaponDamages.entrySet()) {
                float avg = (float) entry.getValue().stream().mapToDouble(Float::doubleValue).average().orElse(0);
                weaponEffectiveness.put(entry.getKey(), avg);
            }

            // Calculate body part effectiveness
            Map<String, Integer> bodyPartTotals = new HashMap<>();
            for (QuestSessionRecord session : sessions) {
                session.bodyPartHits.forEach((part, count) ->
                    bodyPartTotals.merge(part, count, (a, b) -> Objects.requireNonNull(Integer.sum(a, b))));
            }
            int totalHits = bodyPartTotals.values().stream().mapToInt(Integer::intValue).sum();
            bodyPartEffectiveness.clear();
            if (totalHits > 0) {
                bodyPartTotals.forEach((part, count) ->
                    bodyPartEffectiveness.put(part, (float) count / totalHits));
            }
        }
    }

    /**
     * Aggregated analytics for a weapon.
     */
    public static class WeaponAnalytics {
        public String weaponId;
        public String displayName;
        public int totalUses = 0;
        public float totalDamage = 0;
        public float averageDamagePerHit = 0;
        public float averageCritRate = 0;
        public float maxSingleHit = 0;

        // Per-mob effectiveness
        public Map<String, Float> effectivenessVsMob = new HashMap<>();

        public void recalculate(List<WeaponUsageRecord> usages) {
            if (usages.isEmpty()) return;

            totalUses = usages.size();
            totalDamage = (float) usages.stream().mapToDouble(u -> u.totalDamage).sum();
            averageDamagePerHit = (float) usages.stream().mapToDouble(u -> u.averageDamage).average().orElse(0);
            averageCritRate = (float) usages.stream().mapToDouble(u -> u.critRate).average().orElse(0);
            maxSingleHit = (float) usages.stream().mapToDouble(u -> u.maxSingleHit).max().orElse(0);
        }
    }

    // ========== Recording ==========

    /**
     * Record a completed quest session.
     */
    public void recordSession(CombatTracker.QuestCombatSession combatSession, EnduranceQuest quest, String playerName) {
        QuestSessionRecord record = new QuestSessionRecord();

        // Basic info
        record.sessionId = UUID.randomUUID().toString();
        record.playerId = combatSession.getPlayerId().toString();
        record.playerName = playerName;
        record.mobType = quest.getMobId().toString();
        record.mobDisplayName = quest.getMobConfig().displayName;
        record.timestamp = System.currentTimeMillis();
        record.dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.timestamp), ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        record.durationMs = combatSession.getSessionDuration();
        record.completed = quest.getState() == EnduranceQuestState.COMPLETED;
        record.wavesReached = quest.getCurrentWave();
        record.totalWaves = quest.getTotalWaves();

        // Combat stats
        record.totalDamageDealt = combatSession.getTotalDamageDealt();
        record.totalDamageTaken = combatSession.getTotalDamageTaken();
        record.totalKills = combatSession.getKills();
        record.totalDeaths = combatSession.getDeaths();
        record.dps = combatSession.getDPS();
        record.averageKillTime = combatSession.getAverageKillTime();
        record.criticalHitRate = combatSession.getCriticalHitRate();

        // Weapon usage
        float totalWeaponDamage = combatSession.getWeaponStats().values().stream()
            .map(w -> w.totalDamage).reduce(0f, (a, b) -> Objects.requireNonNull(Float.sum(a, b)));

        for (CombatTracker.WeaponStats weaponStats : combatSession.getWeaponStats().values()) {
            WeaponUsageRecord weaponRecord = new WeaponUsageRecord();
            weaponRecord.weaponId = weaponStats.weaponId;
            weaponRecord.weaponName = getWeaponDisplayName(weaponStats.weaponId);
            weaponRecord.totalDamage = weaponStats.totalDamage;
            weaponRecord.hits = weaponStats.hits;
            weaponRecord.criticalHits = weaponStats.criticalHits;
            weaponRecord.maxSingleHit = weaponStats.maxSingleHit;
            weaponRecord.averageDamage = weaponStats.getAverageDamage();
            weaponRecord.critRate = weaponStats.getCritRate();
            weaponRecord.damageShare = totalWeaponDamage > 0 ? weaponStats.totalDamage / totalWeaponDamage : 0;
            record.weaponUsage.add(weaponRecord);
        }

        // Wave breakdown
        for (CombatTracker.WaveCombatStats waveStats : combatSession.getWaveStats()) {
            WaveRecord waveRecord = new WaveRecord();
            waveRecord.waveNumber = waveStats.waveNumber;
            waveRecord.damageDealt = waveStats.damageDealt;
            waveRecord.damageTaken = waveStats.damageTaken;
            waveRecord.kills = waveStats.kills;
            waveRecord.deaths = waveStats.deaths;
            waveRecord.durationMs = waveStats.duration;
            record.waves.add(waveRecord);
        }

        // Body parts and damage types
        record.bodyPartHits.putAll(combatSession.getBodyPartHits());
        record.damageByType.putAll(combatSession.getDamageByType());
        record.damageTakenByType.putAll(combatSession.getDamageTakenByType());

        // Store
        saveSession(record);
        recentSessions.add(record);
        if (recentSessions.size() > MAX_RECENT_SESSIONS) {
            recentSessions.remove(0);
        }

        // Update aggregates
        updateAggregates(record);

        LOGGER.info("[EnduranceAnalytics] Recorded session {} - {} vs {} - {} damage, {} kills",
            record.sessionId, playerName, record.mobDisplayName, record.totalDamageDealt, record.totalKills);
    }

    private String getWeaponDisplayName(String weaponId) {
        // Simple conversion from ID to display name
        String path = weaponId.contains(":") ? weaponId.split(":")[1] : weaponId;
        return Arrays.stream(path.split("_"))
            .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
            .collect(Collectors.joining(" "));
    }

    // ========== Storage ==========

    private void saveSession(QuestSessionRecord record) {
        String filename = String.format("%s_%s.json",
            record.dateTime.replace(":", "-").replace("T", "_"),
            record.sessionId.substring(0, 8));
        Path sessionFile = dataDirectory.resolve("sessions").resolve(filename);

        try (Writer writer = Files.newBufferedWriter(sessionFile)) {
            GSON.toJson(record, writer);
        } catch (IOException e) {
            LOGGER.error("[EnduranceAnalytics] Failed to save session", e);
        }
    }

    private void updateAggregates(QuestSessionRecord record) {
        // Update mob analytics
        MobAnalytics mob = mobAnalytics.computeIfAbsent(record.mobType, k -> {
            MobAnalytics m = new MobAnalytics();
            m.mobId = record.mobType;
            m.displayName = record.mobDisplayName;
            return m;
        });

        // Get all sessions for this mob
        List<QuestSessionRecord> mobSessions = recentSessions.stream()
            .filter(s -> s.mobType.equals(record.mobType))
            .collect(Collectors.toList());
        mob.recalculate(mobSessions);

        // Update weapon analytics
        for (WeaponUsageRecord weaponUsage : record.weaponUsage) {
            WeaponAnalytics weapon = weaponAnalytics.computeIfAbsent(weaponUsage.weaponId, k -> {
                WeaponAnalytics w = new WeaponAnalytics();
                w.weaponId = weaponUsage.weaponId;
                w.displayName = weaponUsage.weaponName;
                return w;
            });

            // Collect all usages of this weapon
            List<WeaponUsageRecord> allUsages = recentSessions.stream()
                .flatMap(s -> s.weaponUsage.stream())
                .filter(w -> w.weaponId.equals(weaponUsage.weaponId))
                .collect(Collectors.toList());
            weapon.recalculate(allUsages);
        }

        saveAggregates();
    }

    private void loadAggregates() {
        Path aggregateFile = dataDirectory.resolve("aggregates").resolve("analytics.json");
        if (Files.exists(aggregateFile)) {
            try (Reader reader = Files.newBufferedReader(aggregateFile)) {
                Type type = new TypeToken<AggregateData>(){}.getType();
                AggregateData data = GSON.fromJson(reader, type);
                if (data != null) {
                    if (data.mobAnalytics != null) mobAnalytics.putAll(data.mobAnalytics);
                    if (data.weaponAnalytics != null) weaponAnalytics.putAll(data.weaponAnalytics);
                }
                LOGGER.info("[EnduranceAnalytics] Loaded aggregates: {} mobs, {} weapons",
                    mobAnalytics.size(), weaponAnalytics.size());
            } catch (Exception e) {
                LOGGER.error("[EnduranceAnalytics] Failed to load aggregates", e);
            }
        }
    }

    private void saveAggregates() {
        Path aggregateFile = dataDirectory.resolve("aggregates").resolve("analytics.json");
        try (Writer writer = Files.newBufferedWriter(aggregateFile)) {
            AggregateData data = new AggregateData();
            data.mobAnalytics = mobAnalytics;
            data.weaponAnalytics = weaponAnalytics;
            data.lastUpdated = System.currentTimeMillis();
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("[EnduranceAnalytics] Failed to save aggregates", e);
        }
    }

    /** JSON serialization container for aggregate analytics data. */
    @SuppressWarnings("unused") // Fields used by GSON serialization
    private static class AggregateData {
        public Map<String, MobAnalytics> mobAnalytics;
        public Map<String, WeaponAnalytics> weaponAnalytics;
        public long lastUpdated;
    }

    // ========== Queries ==========

    /**
     * Get analytics for a specific mob.
     */
    public Optional<MobAnalytics> getMobAnalytics(ResourceLocation mobId) {
        return Optional.ofNullable(mobAnalytics.get(mobId.toString()));
    }

    /**
     * Get analytics for a specific weapon.
     */
    public Optional<WeaponAnalytics> getWeaponAnalytics(String weaponId) {
        return Optional.ofNullable(weaponAnalytics.get(weaponId));
    }

    /**
     * Get all mob analytics sorted by difficulty (completion rate).
     */
    public List<MobAnalytics> getMobsByDifficulty() {
        return mobAnalytics.values().stream()
            .sorted(Comparator.comparingDouble(m -> m.completionRate))
            .collect(Collectors.toList());
    }

    /**
     * Get all weapons sorted by effectiveness.
     */
    public List<WeaponAnalytics> getWeaponsByEffectiveness() {
        return weaponAnalytics.values().stream()
            .sorted(Comparator.comparingDouble(w -> -w.averageDamagePerHit))
            .collect(Collectors.toList());
    }

    /**
     * Get recent sessions.
     */
    public List<QuestSessionRecord> getRecentSessions(int limit) {
        int start = Math.max(0, recentSessions.size() - limit);
        return new ArrayList<>(recentSessions.subList(start, recentSessions.size()));
    }

    /**
     * Get balance report - mobs that might need adjustment.
     */
    public BalanceReport generateBalanceReport() {
        BalanceReport report = new BalanceReport();
        report.generatedAt = System.currentTimeMillis();

        // Find too-easy mobs (high completion rate)
        report.tooEasyMobs = mobAnalytics.values().stream()
            .filter(m -> m.completionRate > 0.8 && m.totalAttempts >= 5)
            .map(m -> m.mobId)
            .collect(Collectors.toList());

        // Find too-hard mobs (low completion rate)
        report.tooHardMobs = mobAnalytics.values().stream()
            .filter(m -> m.completionRate < 0.2 && m.totalAttempts >= 5)
            .map(m -> m.mobId)
            .collect(Collectors.toList());

        // Find overpowered weapons
        double avgWeaponDamage = weaponAnalytics.values().stream()
            .mapToDouble(w -> w.averageDamagePerHit).average().orElse(0);
        report.overpoweredWeapons = weaponAnalytics.values().stream()
            .filter(w -> w.averageDamagePerHit > avgWeaponDamage * 1.5 && w.totalUses >= 10)
            .map(w -> w.weaponId)
            .collect(Collectors.toList());

        // Find underpowered weapons
        report.underpoweredWeapons = weaponAnalytics.values().stream()
            .filter(w -> w.averageDamagePerHit < avgWeaponDamage * 0.5 && w.totalUses >= 10)
            .map(w -> w.weaponId)
            .collect(Collectors.toList());

        return report;
    }

    public static class BalanceReport {
        public long generatedAt;
        public List<String> tooEasyMobs = new ArrayList<>();
        public List<String> tooHardMobs = new ArrayList<>();
        public List<String> overpoweredWeapons = new ArrayList<>();
        public List<String> underpoweredWeapons = new ArrayList<>();
    }
}
