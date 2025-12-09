package com.frenkvs.devmod.endurance;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Client-side cache for quest data received from the server.
 * Used by HUD overlays to display quest information.
 *
 * This is a simple static cache that gets updated when sync packets arrive.
 */
@OnlyIn(Dist.CLIENT)
public class ClientQuestCache {

    private static volatile QuestSyncPayload cachedData = null;
    private static volatile long lastUpdateTime = 0;

    // Track previous state to detect changes
    private static volatile int lastWave = 0;
    private static volatile boolean wasActive = false;

    /**
     * Update the cache with new data from server.
     * Also synchronizes with IntegratedTestSession if active.
     */
    public static void update(QuestSyncPayload payload) {
        QuestSyncPayload oldData = cachedData;
        cachedData = payload;
        lastUpdateTime = System.currentTimeMillis();

        // Sync with IntegratedTestSession
        syncWithIntegratedSession(oldData, payload);
    }

    /**
     * Synchronize quest events with IntegratedTestSession for unified tracking.
     */
    private static void syncWithIntegratedSession(QuestSyncPayload oldData, QuestSyncPayload newData) {
        var session = com.frenkvs.devmod.testing.IntegratedTestSession.INSTANCE;
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
                case COMPLETED -> com.frenkvs.devmod.testing.IntegratedTestSession.SessionOutcome.COMPLETED;
                case FAILED -> com.frenkvs.devmod.testing.IntegratedTestSession.SessionOutcome.DEATH;
                default -> com.frenkvs.devmod.testing.IntegratedTestSession.SessionOutcome.ABANDONED;
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
     * Clear the cache (e.g., when disconnecting).
     */
    public static void clear() {
        cachedData = null;
        lastUpdateTime = 0;
    }

    /**
     * Check if there's an active quest.
     */
    public static boolean hasActiveQuest() {
        return cachedData != null && cachedData.hasActiveQuest();
    }

    /**
     * Get the cached quest data, or null if none.
     */
    public static QuestSyncPayload getData() {
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
        if (cachedData == null || cachedData.totalMobsInWave() <= 0) return 0;
        return (float) cachedData.mobsKilledInWave() / cachedData.totalMobsInWave();
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

    public static boolean isAwaitingRespawn() {
        return cachedData != null && cachedData.getState() == EnduranceQuestState.FAILED;
    }

    public static boolean isAtCheckpoint() {
        return cachedData != null && cachedData.getState() == EnduranceQuestState.WAVE_COMPLETE;
    }
}
