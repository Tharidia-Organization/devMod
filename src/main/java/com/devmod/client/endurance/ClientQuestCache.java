package com.devmod.client.endurance;

import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceQuestState;
import com.devmod.endurance.QuestSyncPayload;
import com.devmod.endurance.WaveObjectiveState;

@OnlyIn(Dist.CLIENT)
public class ClientQuestCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientQuestCache.class);

    private static volatile @Nullable QuestSyncPayload cachedData = null;
    private static volatile long lastUpdateTime = 0;

    // Track previous state to detect changes
    private static volatile int lastWave = 0;
    private static volatile boolean wasActive = false;
    private static volatile EnduranceQuestState lastState = EnduranceQuestState.AVAILABLE;

    /**
     * Flag to suppress vanilla DeathScreen during quest death/respawn flow.
     * Set when we receive QUEST_DEATH packet, cleared after respawn or timeout.
     * This persists even when hasActiveQuest() becomes false during respawn.
     */
    private static volatile boolean suppressingVanillaDeathScreen = false;
    private static volatile long suppressionStartTime = 0;
    private static final long SUPPRESSION_TIMEOUT_MS = 10_000; // 10 seconds max

    /**
     * Update the cache with new data from server.
     * Also synchronizes with IntegratedTestSession if active.
     */
    public static void update(QuestSyncPayload payload) {
        @Nullable QuestSyncPayload oldData = cachedData;
        cachedData = payload;
        lastUpdateTime = System.currentTimeMillis();

        // If quest is no longer active, reset tracking state
        if (!payload.hasActiveQuest()) {
            lastWave = 0;
            wasActive = false;
            com.devmod.client.overlay.QuestSequenceOverlay.INSTANCE.clear();
            com.devmod.client.config.ClientMechanicsCache.INSTANCE.clearActiveQuestContext();
        }

        // Sync with IntegratedTestSession
        syncWithIntegratedSession(oldData, payload);

        // Sync with Wave Intelligence System
        syncWithWIS(oldData, payload);
    }

    /**
     * Synchronize quest events with IntegratedTestSession for unified tracking.
     */
    private static void syncWithIntegratedSession(@Nullable QuestSyncPayload oldData, QuestSyncPayload newData) {
        var session = com.devmod.client.testing.IntegratedTestSession.INSTANCE;
        if (!session.isSessionActive()) return;

        // Detect wave completion
        int prevWave = oldData != null ? oldData.currentWave() : lastWave;
        int newWave = newData.currentWave();
        if (newWave > prevWave && newData.hasActiveQuest()) {
            int killsThisWave = newData.mobsKilledInWave();
            session.onWaveCompleted(newWave, killsThisWave);
        }
        lastWave = newWave;

        // Detect quest end
        boolean nowActive = newData.hasActiveQuest();
        if (wasActive && !nowActive) {
            // Quest ended - determine outcome from state
            EnduranceQuestState state = newData.getState();
            var outcome = switch (state) {
                case COMPLETED -> com.devmod.client.testing.IntegratedTestSession.SessionOutcome.COMPLETED;
                case FAILED -> com.devmod.client.testing.IntegratedTestSession.SessionOutcome.DEATH;
                default -> com.devmod.client.testing.IntegratedTestSession.SessionOutcome.ABANDONED;
            };
            session.endSession(outcome);
        }
        wasActive = nowActive;

        // Update kills in session results
        if (nowActive) {
            session.getResults().totalKills = newData.mobsKilled();
            session.getResults().deaths = newData.deaths();
            session.getResults().damageDealt = newData.damageDealt();
            session.getResults().damageTaken = newData.damageTaken();
        }
    }

    /**
     * Synchronize quest state changes with Wave Intelligence System.
     * Detects state transitions to trigger WIS phase changes.
     */
    private static void syncWithWIS(@Nullable QuestSyncPayload oldData, QuestSyncPayload newData) {
        var wis = com.devmod.client.endurance.wis.WaveIntelligenceManager.INSTANCE;
        if (!wis.isEnabled()) return;

        EnduranceQuestState prevState = oldData != null ? oldData.getState() : lastState;
        EnduranceQuestState newState = newData.getState();

        // Detect transition TO WAVE_COMPLETE (wave finished, debrief should show)
        if (newState == EnduranceQuestState.WAVE_COMPLETE && prevState == EnduranceQuestState.IN_PROGRESS) {
            com.devmod.client.endurance.wis.WISClientBridge.onWaveComplete();
        }

        // Detect transition TO IN_PROGRESS from WAVE_COMPLETE (new wave starting)
        if (newState == EnduranceQuestState.IN_PROGRESS && prevState == EnduranceQuestState.WAVE_COMPLETE) {
            // Wave combat is starting
            com.devmod.client.endurance.wis.WISClientBridge.onWaveStart();
        }

        // Update last state for next comparison
        lastState = newState;
    }

    /**
     * Clear the cache (e.g., when disconnecting).
     */
    public static void clear() {
        cachedData = null;
        lastUpdateTime = 0;
        suppressingVanillaDeathScreen = false;
        lastState = EnduranceQuestState.AVAILABLE;
        com.devmod.client.overlay.QuestSequenceOverlay.INSTANCE.clear();
        EnduranceUiCache.clear();
        com.devmod.client.config.ClientMechanicsCache.INSTANCE.clearAll();
    }

    /**
     * Check if there's an active quest.
     */
    public static boolean hasActiveQuest() {
        return cachedData != null && cachedData.hasActiveQuest();
    }

    /**
     * Start suppressing vanilla DeathScreen.
     * Called when QUEST_DEATH packet is received.
     */
    public static void startDeathScreenSuppression() {
        suppressingVanillaDeathScreen = true;
        suppressionStartTime = System.currentTimeMillis();
        LOGGER.info("[EnduranceQuest][Client] startDeathScreenSuppression (hasActiveQuest={}, playerDead={})",
            hasActiveQuest(),
            isPlayerDead());
    }

    /**
     * Stop suppressing vanilla DeathScreen.
     * Called after respawn completes or quest ends.
     */
    public static void stopDeathScreenSuppression() {
        if (isPlayerDead()) {
            suppressionStartTime = System.currentTimeMillis();
            suppressingVanillaDeathScreen = true;
            LOGGER.info("[EnduranceQuest][Client] stopDeathScreenSuppression deferred (playerDead=true)");
            return;
        }
        suppressingVanillaDeathScreen = false;
        LOGGER.info("[EnduranceQuest][Client] stopDeathScreenSuppression (playerDead=false)");
    }

    /**
     * Check if vanilla DeathScreen should be suppressed.
     * Returns true if:
     * - There's an active quest, OR
     * - We're in the death/respawn flow (suppression flag is set and not timed out)
     */
    public static boolean shouldSuppressVanillaDeathScreen() {
        if (hasActiveQuest()) {
            return true;
        }
        if (suppressingVanillaDeathScreen) {
            if (isPlayerDead()) {
                return true;
            }
            // Check timeout to prevent infinite suppression if something goes wrong
            if (System.currentTimeMillis() - suppressionStartTime < SUPPRESSION_TIMEOUT_MS) {
                return true;
            }
            // Timed out, clear flag
            suppressingVanillaDeathScreen = false;
        }
        return false;
    }

    private static boolean isPlayerDead() {
        Minecraft mc = Minecraft.getInstance();
        var player = mc != null ? mc.player : null;
        if (player == null) {
            return false;
        }
        return !player.isAlive() || player.isDeadOrDying();
    }

    public static boolean isInInstanceDimension() {
        Minecraft mc = Minecraft.getInstance();
        var player = mc != null ? mc.player : null;
        if (player == null) {
            return false;
        }
        ResourceLocation id = player.level().dimension().location();
        if (id == null) {
            return false;
        }
        return "devmod".equals(id.getNamespace()) && id.getPath().startsWith("instance_");
    }

    /**
     * Get the cached quest data, or null if none.
     */
    public static @Nullable QuestSyncPayload getData() {
        return cachedData;
    }

    /**
     * Get time since last update in milliseconds.
     */
    public static long getTimeSinceUpdate() {
        return System.currentTimeMillis() - lastUpdateTime;
    }

    /**
     * Check if data is stale (older than specified threshold).
     */
    public static boolean isStale(long thresholdMs) {
        return getTimeSinceUpdate() > thresholdMs;
    }

    // === Convenience getters for HUD ===

    public static String getQuestName() {
        return cachedData != null ? cachedData.questName() : "";
    }

    public static String getTemplateId() {
        return cachedData != null ? cachedData.templateId() : "";
    }

    public static int getTemplateVersion() {
        return cachedData != null ? cachedData.templateVersion() : 0;
    }

    public static String getPolicyId() {
        return cachedData != null ? cachedData.policyId() : "";
    }

    public static int getPolicyVersion() {
        return cachedData != null ? cachedData.policyVersion() : 0;
    }

    public static String getInstanceId() {
        return cachedData != null ? cachedData.instanceId() : "";
    }

    public static String getArenaId() {
        return cachedData != null ? cachedData.arenaId() : "";
    }

    public static String getDifficultyLabel() {
        return cachedData != null ? cachedData.difficultyLabel() : "";
    }

    public static String getQuestTypeLabel() {
        return cachedData != null ? cachedData.questTypeLabel() : "";
    }

    public static int getCurrentWave() {
        return cachedData != null ? cachedData.currentWave() : 0;
    }

    public static int getTotalWaves() {
        return cachedData != null ? cachedData.totalWaves() : 0;
    }

    public static boolean isEndlessMode() {
        return cachedData != null && cachedData.endlessMode();
    }

    public static int getPointsEarned() {
        return cachedData != null ? cachedData.pointsEarned() : 0;
    }

    public static int getMobsKilled() {
        return cachedData != null ? cachedData.mobsKilled() : 0;
    }

    public static int getMobsKilledInWave() {
        return cachedData != null ? cachedData.mobsKilledInWave() : 0;
    }

    public static int getTotalMobsInWave() {
        return cachedData != null ? cachedData.totalMobsInWave() : 0;
    }

    public static float getWaveProgress() {
        return getWaveProgress(cachedData);
    }

    /**
     * FIX AUDIT2 #4: Compute wave progress from a specific payload snapshot.
     * Use this overload when you need frame-consistent rendering with a pre-captured snapshot.
     *
     * @param data the quest data snapshot (may be null)
     * @return progress 0.0-1.0, or 0 if data is null or no target
     */
    public static float getWaveProgress(@Nullable QuestSyncPayload data) {
        if (data == null) return 0;
        WaveObjectiveState.Type type = data.getObjectiveType();
        int target = type == WaveObjectiveState.Type.KILL_ALL
            ? data.totalMobsInWave()
            : data.objectiveTarget();
        int progress = type == WaveObjectiveState.Type.KILL_ALL
            ? data.mobsKilledInWave()
            : data.objectiveProgress();
        if (target <= 0) return 0;
        return (float) progress / target;
    }

    public static int getDamageDealt() {
        return cachedData != null ? cachedData.damageDealt() : 0;
    }

    public static int getDamageTaken() {
        return cachedData != null ? cachedData.damageTaken() : 0;
    }

    public static int getDeaths() {
        return cachedData != null ? cachedData.deaths() : 0;
    }

    public static long getSessionDuration() {
        return cachedData != null ? cachedData.sessionDurationMs() : 0;
    }

    public static EnduranceQuestState getState() {
        return cachedData != null ? cachedData.getState() : EnduranceQuestState.AVAILABLE;
    }

    public static List<String> getWaveModifiers() {
        return cachedData != null ? cachedData.waveModifiers() : List.of();
    }

    public static int getCurrentCombo() {
        return cachedData != null ? cachedData.currentCombo() : 0;
    }

    public static int getMaxCombo() {
        return cachedData != null ? cachedData.maxCombo() : 0;
    }

    public static int getStyleScore() {
        return cachedData != null ? cachedData.styleScore() : 0;
    }

    public static ComboSystem.StyleRank getStyleRank() {
        return cachedData != null ? cachedData.getStyleRank() : ComboSystem.StyleRank.D;
    }

    public static WaveObjectiveState.Type getObjectiveType() {
        return cachedData != null ? cachedData.getObjectiveType() : WaveObjectiveState.Type.KILL_ALL;
    }

    public static String getObjectiveTitle() {
        return cachedData != null ? cachedData.objectiveTitle() : "";
    }

    public static String getObjectiveDescription() {
        return cachedData != null ? cachedData.objectiveDescription() : "";
    }

    public static int getObjectiveProgress() {
        return cachedData != null ? cachedData.objectiveProgress() : 0;
    }

    public static int getObjectiveTarget() {
        return cachedData != null ? cachedData.objectiveTarget() : 0;
    }

    public static boolean isObjectiveComplete() {
        return cachedData != null && cachedData.objectiveComplete();
    }

    public static boolean isObjectiveFailed() {
        return cachedData != null && cachedData.objectiveFailed();
    }

    public static boolean isAwaitingRespawn() {
        return cachedData != null && cachedData.getState() == EnduranceQuestState.FAILED;
    }

    public static boolean isAtCheckpoint() {
        return cachedData != null && cachedData.getState() == EnduranceQuestState.WAVE_COMPLETE;
    }
}
