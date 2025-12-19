package com.frenkvs.devmod.testing.stats;

import com.google.gson.JsonObject;

/**
 * Tracks session and play time statistics.
 * Extracted from TesterProgress for single responsibility.
 */
public class SessionStatistics {
    public static final SessionStatistics INSTANCE = new SessionStatistics();

    private long playTimeMs = 0;
    private long sessionStartTime = 0;
    private int sessionsStarted = 0;
    private int testsCompletedManually = 0;
    private int testsAutoCompleted = 0;

    private SessionStatistics() {}

    /**
     * Record session start.
     */
    public void recordSessionStart() {
        sessionsStarted++;
        sessionStartTime = System.currentTimeMillis();
    }

    /**
     * Record test completion.
     */
    public void recordTestCompleted(boolean wasAutomatic) {
        if (wasAutomatic) {
            testsAutoCompleted++;
        } else {
            testsCompletedManually++;
        }
    }

    /**
     * Update play time (call periodically).
     * @return true if time was updated
     */
    public boolean updatePlayTime() {
        if (sessionStartTime > 0) {
            playTimeMs += System.currentTimeMillis() - sessionStartTime;
            sessionStartTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    // === Getters ===
    public long getPlayTimeMs() { return playTimeMs; }
    public int getSessionsStarted() { return sessionsStarted; }
    public int getTestsCompleted() { return testsCompletedManually + testsAutoCompleted; }
    public int getTestsCompletedManually() { return testsCompletedManually; }
    public int getTestsAutoCompleted() { return testsAutoCompleted; }

    // === Persistence ===
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("playTimeMs", playTimeMs);
        json.addProperty("sessions", sessionsStarted);
        json.addProperty("testsManual", testsCompletedManually);
        json.addProperty("testsAuto", testsAutoCompleted);
        return json;
    }

    public void fromJson(JsonObject json) {
        playTimeMs = getLong(json, "playTimeMs", 0);
        sessionsStarted = getInt(json, "sessions", 0);
        testsCompletedManually = getInt(json, "testsManual", 0);
        testsAutoCompleted = getInt(json, "testsAuto", 0);
    }

    public void reset() {
        playTimeMs = 0;
        sessionStartTime = 0;
        sessionsStarted = 0;
        testsCompletedManually = 0;
        testsAutoCompleted = 0;
    }

    private int getInt(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultValue;
    }

    private long getLong(JsonObject obj, String key, long defaultValue) {
        return obj.has(key) ? obj.get(key).getAsLong() : defaultValue;
    }
}
