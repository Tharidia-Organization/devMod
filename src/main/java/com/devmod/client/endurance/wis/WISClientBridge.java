package com.devmod.client.endurance.wis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.endurance.ClientQuestCache;
import com.devmod.endurance.QuestSyncPayload;

/**
 * Bridge between client-side quest data and the Wave Intelligence System.
 * Call methods from this class when receiving quest/wave sync packets.
 *
 * This decouples WIS from the network layer and provides a clean API
 * for integration with existing client caches.
 */
@OnlyIn(Dist.CLIENT)
public final class WISClientBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(WISClientBridge.class);

    // ========== Threat Level Constants (Fix #6) ==========
    // These weights determine how threat level is calculated from wave data.
    // Adjust these to tune the difficulty perception.

    /** Points per mob in the wave (e.g., 20 mobs = 10 points) */
    private static final float THREAT_MOB_WEIGHT = 0.5f;

    /** Points per wave number (e.g., wave 10 = 20 points) */
    private static final float THREAT_WAVE_WEIGHT = 2.0f;

    /** Bonus points for endless mode late waves (wave 10+) */
    private static final float THREAT_ENDLESS_BONUS = 15.0f;

    /** Threshold for LOW threat (score < this) */
    private static final float THREAT_THRESHOLD_LOW = 15.0f;

    /** Threshold for MEDIUM threat (score < this) */
    private static final float THREAT_THRESHOLD_MEDIUM = 30.0f;

    /** Threshold for HIGH threat (score < this, above = EXTREME) */
    private static final float THREAT_THRESHOLD_HIGH = 50.0f;

    /** Health percentage threshold for low health warning */
    private static final float LOW_HEALTH_THRESHOLD = 50.0f;

    private WISClientBridge() {}

    // ========== Quest Lifecycle ==========

    /**
     * Called when a new quest starts.
     */
    public static void onQuestStart(UUID questId) {
        WaveIntelligenceManager.INSTANCE.startQuest(questId);
        LOGGER.debug("[WIS Bridge] Quest started: {}", questId);
    }

    /**
     * Called when quest ends (success, failure, or abandon).
     */
    public static void onQuestEnd() {
        // End any active combat first
        if (WaveIntelligenceManager.INSTANCE.getCurrentPhase() == WavePhase.COMBAT) {
            WaveIntelligenceManager.INSTANCE.endCombat();
        }
        WaveIntelligenceManager.INSTANCE.endQuest();
        LOGGER.debug("[WIS Bridge] Quest ended");
    }

    // ========== Wave Lifecycle ==========

    /**
     * Called when wave countdown starts (10 seconds before wave).
     * Builds briefing data from the current quest cache state.
     */
    public static void onWaveCountdownStart(int waveNumber, int totalWaves) {
        // Build briefing data from ClientQuestCache
        WaveBriefingData briefing = buildBriefingData(waveNumber, totalWaves);
        WaveIntelligenceManager.INSTANCE.beginPreBrief(waveNumber, briefing);
        LOGGER.debug("[WIS Bridge] Wave {} countdown started", waveNumber);
    }

    /**
     * Called when wave countdown starts with explicit mob data.
     */
    public static void onWaveCountdownStart(int waveNumber, int totalWaves,
                                             List<MobInfo> expectedMobs,
                                             Set<String> modifiers,
                                             String objective) {
        WaveBriefingData briefing = buildBriefingDataFromMobInfo(
            waveNumber, totalWaves, expectedMobs, modifiers, objective);
        WaveIntelligenceManager.INSTANCE.beginPreBrief(waveNumber, briefing);
        LOGGER.debug("[WIS Bridge] Wave {} countdown started with {} mob types",
            waveNumber, expectedMobs.size());
    }

    /**
     * Called when wave actually starts (mobs begin spawning).
     */
    public static void onWaveStart() {
        WaveIntelligenceManager.INSTANCE.beginCombat();
        LOGGER.debug("[WIS Bridge] Wave combat started");
    }

    /**
     * Called when wave is complete.
     * Ensures a collector exists for the debrief screen, creating one if necessary.
     */
    public static void onWaveComplete() {
        var wis = WaveIntelligenceManager.INSTANCE;

        // Ensure we have a collector for the debrief screen
        // This handles cases where WIS wasn't fully initialized (no pre-brief phase)
        if (wis.getCurrentCollector() == null) {
            QuestSyncPayload cached = ClientQuestCache.getData();
            int waveNumber = cached != null ? cached.currentWave() : 1;

            // Create a minimal collector with data from cache
            LOGGER.debug("[WIS Bridge] Creating fallback collector for wave {}", waveNumber);
            wis.ensureCollectorExists(waveNumber);
        }

        wis.endCombat();
        LOGGER.debug("[WIS Bridge] Wave complete");
    }

    // ========== Combat Events ==========

    /**
     * Called when player kills a mob.
     */
    public static void onMobKill(UUID mobId, ResourceLocation mobType, BlockPos position,
                                  float damageDealt, String weaponUsed, boolean isElite,
                                  boolean isCritical, int comboCount, long timeToKillMs) {
        WaveIntelligenceManager.INSTANCE.recordKill(mobId, mobType, position,
            damageDealt, weaponUsed, isElite, isCritical, comboCount, timeToKillMs);
    }

    /**
     * Called when player deals damage to a mob.
     */
    public static void onDamageDealt(UUID mobId, ResourceLocation mobType, float damage,
                                      String damageSource, boolean isCritical, BlockPos position) {
        WaveIntelligenceManager.INSTANCE.recordDamageDealt(mobId, mobType, damage,
            damageSource, isCritical, position);
    }

    /**
     * Called when player takes damage.
     */
    public static void onDamageTaken(@Nullable UUID sourceId, @Nullable ResourceLocation sourceType,
                                      float damage, String damageSource, float remainingHealth) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            WaveIntelligenceManager.INSTANCE.recordDamageTaken(sourceId, sourceType,
                damage, damageSource, remainingHealth, player.blockPosition());
        }
    }

    /**
     * Called when combo milestone is reached.
     */
    public static void onComboMilestone(int comboCount, int styleScore, String rankName) {
        CombatEvent.ComboEvent.ComboEventType type = switch (comboCount) {
            case 5 -> CombatEvent.ComboEvent.ComboEventType.MILESTONE_5;
            case 10 -> CombatEvent.ComboEvent.ComboEventType.MILESTONE_10;
            case 25 -> CombatEvent.ComboEvent.ComboEventType.MILESTONE_25;
            case 50 -> CombatEvent.ComboEvent.ComboEventType.MILESTONE_50;
            case 100 -> CombatEvent.ComboEvent.ComboEventType.MILESTONE_100;
            default -> null;
        };
        if (type != null) {
            WaveIntelligenceManager.INSTANCE.recordComboEvent(type, comboCount, styleScore, rankName);
        }
    }

    /**
     * Called when style rank changes.
     */
    public static void onStyleRankChange(boolean isRankUp, int comboCount, int styleScore, String rankName) {
        CombatEvent.ComboEvent.ComboEventType type = isRankUp
            ? CombatEvent.ComboEvent.ComboEventType.RANK_UP
            : CombatEvent.ComboEvent.ComboEventType.RANK_DOWN;
        WaveIntelligenceManager.INSTANCE.recordComboEvent(type, comboCount, styleScore, rankName);
    }

    /**
     * Called when combo breaks.
     */
    public static void onComboBreak(int comboLost, int styleScore, String rankName) {
        WaveIntelligenceManager.INSTANCE.recordComboEvent(
            CombatEvent.ComboEvent.ComboEventType.COMBO_BREAK,
            0, styleScore, rankName);
    }

    /**
     * Called when special action is performed (dodge, parry, etc).
     */
    public static void onSpecialAction(String actionType, int pointsEarned, int styleEarned) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            WaveIntelligenceManager.INSTANCE.recordSpecialAction(
                actionType, pointsEarned, styleEarned, player.blockPosition());
        }
    }

    /**
     * Called when a mob spawns.
     */
    public static void onMobSpawn(UUID mobId, ResourceLocation mobType, BlockPos position,
                                   boolean isElite, String affix) {
        WaveIntelligenceManager.INSTANCE.recordMobSpawn(mobId, mobType, position, isElite, affix);
    }

    /**
     * Called when player dies.
     */
    public static void onPlayerDeath() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            WaveIntelligenceManager.INSTANCE.recordPlayerDeath(player.blockPosition());
        }
    }

    // ========== Briefing Data Builders ==========

    /**
     * Build briefing data from ClientQuestCache.
     * Fix #4: ThreatLevel is now estimated from cached data, not hardcoded MEDIUM.
     */
    private static WaveBriefingData buildBriefingData(int waveNumber, int totalWaves) {
        // Try to get data from cache
        QuestSyncPayload cached = ClientQuestCache.getData();

        // Fix #4: Estimate threat level from cached data
        WaveBriefingData.ThreatLevel threat = WaveBriefingData.ThreatLevel.MEDIUM;
        int totalMobCount = 0;
        String objectiveDesc = "Kill all enemies";
        List<String> warnings = new ArrayList<>();

        // Fix #10: Get player for health check
        Player player = Minecraft.getInstance().player;

        if (cached != null && cached.hasActiveQuest()) {
            totalMobCount = cached.totalMobsInWave();

            // Fix #6: Estimate threat using documented constants
            float score = 0;
            score += totalMobCount * THREAT_MOB_WEIGHT;
            score += waveNumber * THREAT_WAVE_WEIGHT;
            if (cached.endlessMode() && waveNumber > 10) {
                score += THREAT_ENDLESS_BONUS;
            }

            if (score < THREAT_THRESHOLD_LOW) threat = WaveBriefingData.ThreatLevel.LOW;
            else if (score < THREAT_THRESHOLD_MEDIUM) threat = WaveBriefingData.ThreatLevel.MEDIUM;
            else if (score < THREAT_THRESHOLD_HIGH) threat = WaveBriefingData.ThreatLevel.HIGH;
            else threat = WaveBriefingData.ThreatLevel.EXTREME;

            // Build objective from cached data
            if (totalMobCount > 0) {
                objectiveDesc = String.format("Kill all %d enemies", totalMobCount);
            }

            // Add warnings based on cached state (Fix #10)
            if (cached.endlessMode() && waveNumber >= 10) {
                warnings.add("Endless mode - scaling difficulty");
            }

            // Fix #10: Actually check player health instead of hardcoding
            if (player != null) {
                float healthPercent = (player.getHealth() / player.getMaxHealth()) * 100f;
                if (healthPercent < LOW_HEALTH_THRESHOLD) {
                    warnings.add(String.format("Low health (%.0f%%) - play carefully", healthPercent));
                }
            }
        }

        WaveBriefingData.Builder builder = WaveBriefingData.builder()
            .waveNumber(waveNumber)
            .totalWaves(totalWaves)
            .totalMobCount(totalMobCount)
            .threatLevel(threat)
            .objective(new WaveBriefingData.ObjectiveInfo(
                "kill_all", objectiveDesc, totalMobCount, -1));

        if (!warnings.isEmpty()) {
            builder.warnings(warnings);
        }

        if (cached != null) {
            // Extract modifiers from cached state
            Set<String> modifiers = new HashSet<>(cached.waveModifiers());
            builder.activeModifiers(modifiers);
        }

        // Get player status (player already retrieved above for health check)
        if (player != null) {
            builder.playerStatus(new WaveBriefingData.PlayerStatus(
                player.getHealth(),
                player.getMaxHealth(),
                (float) player.getArmorValue(),
                0, // perks - would need to query
                0, // combo
                "D" // starting rank
            ));
        }

        return builder.build();
    }

    /**
     * Build briefing data from explicit mob info.
     */
    private static WaveBriefingData buildBriefingDataFromMobInfo(
            int waveNumber, int totalWaves,
            List<MobInfo> expectedMobs,
            Set<String> modifiers,
            String objective) {

        List<WaveBriefingData.MobComposition> mobComp = new ArrayList<>();
        int totalCount = 0;
        int eliteCount = 0;

        for (MobInfo mob : expectedMobs) {
            mobComp.add(new WaveBriefingData.MobComposition(
                mob.mobType(),
                mob.displayName(),
                mob.count(),
                mob.eliteCount(),
                mob.estimatedHealth(),
                mob.estimatedDamage(),
                mob.isBoss()
            ));
            totalCount += mob.count();
            eliteCount += mob.eliteCount();
        }

        // Calculate threat level
        WaveBriefingData.ThreatLevel threat = calculateThreatLevel(totalCount, eliteCount, expectedMobs);

        WaveBriefingData.Builder builder = WaveBriefingData.builder()
            .waveNumber(waveNumber)
            .totalWaves(totalWaves)
            .mobComposition(mobComp)
            .totalMobCount(totalCount)
            .eliteCount(eliteCount)
            .activeModifiers(modifiers)
            .threatLevel(threat)
            .objective(new WaveBriefingData.ObjectiveInfo(
                "kill_all", objective, totalCount, -1));

        // Add warnings for special conditions
        List<String> warnings = new ArrayList<>();
        if (expectedMobs.stream().anyMatch(MobInfo::isBoss)) {
            warnings.add("Boss wave!");
        }
        if (eliteCount > totalCount * 0.3) {
            warnings.add("High elite density");
        }
        if (!warnings.isEmpty()) {
            builder.warnings(warnings);
        }

        // Get player status
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            builder.playerStatus(new WaveBriefingData.PlayerStatus(
                player.getHealth(),
                player.getMaxHealth(),
                (float) player.getArmorValue(),
                0, 0, "D"
            ));
        }

        return builder.build();
    }

    private static WaveBriefingData.ThreatLevel calculateThreatLevel(
            int totalCount, int eliteCount, List<MobInfo> mobs) {
        // Simple threat calculation
        float score = 0;
        score += totalCount * 0.5f;
        score += eliteCount * 2f;

        if (mobs.stream().anyMatch(MobInfo::isBoss)) {
            score += 30;
        }

        if (score < 15) return WaveBriefingData.ThreatLevel.LOW;
        if (score < 30) return WaveBriefingData.ThreatLevel.MEDIUM;
        if (score < 50) return WaveBriefingData.ThreatLevel.HIGH;
        return WaveBriefingData.ThreatLevel.EXTREME;
    }

    // ========== Mob Info Record ==========

    /**
     * Information about mobs expected in a wave.
     */
    public record MobInfo(
        ResourceLocation mobType,
        String displayName,
        int count,
        int eliteCount,
        float estimatedHealth,
        float estimatedDamage,
        boolean isBoss
    ) {}
}
