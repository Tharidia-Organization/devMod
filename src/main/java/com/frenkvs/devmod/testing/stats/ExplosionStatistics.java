package com.frenkvs.devmod.testing.stats;

import com.google.gson.JsonObject;

/**
 * Tracks explosion-related statistics.
 * Extracted from TesterProgress for single responsibility.
 */
public class ExplosionStatistics {
    public static final ExplosionStatistics INSTANCE = new ExplosionStatistics();

    private int explosionsCaused = 0;
    private int tntDetonated = 0;
    private int creeperExplosions = 0;
    private int mobsKilledByExplosion = 0;

    private ExplosionStatistics() {}

    /**
     * Record an explosion event.
     */
    public void recordExplosion(boolean isTNT, boolean isCreeper, int mobsKilled) {
        explosionsCaused++;
        if (isTNT) tntDetonated++;
        if (isCreeper) creeperExplosions++;
        mobsKilledByExplosion += mobsKilled;
    }

    // === Getters ===
    public int getExplosionsCaused() { return explosionsCaused; }
    public int getTntDetonated() { return tntDetonated; }
    public int getCreeperExplosions() { return creeperExplosions; }
    public int getMobsKilledByExplosion() { return mobsKilledByExplosion; }

    // === Persistence ===
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("caused", explosionsCaused);
        json.addProperty("tnt", tntDetonated);
        json.addProperty("creeper", creeperExplosions);
        json.addProperty("mobsKilled", mobsKilledByExplosion);
        return json;
    }

    public void fromJson(JsonObject json) {
        explosionsCaused = getInt(json, "caused", 0);
        tntDetonated = getInt(json, "tnt", 0);
        creeperExplosions = getInt(json, "creeper", 0);
        mobsKilledByExplosion = getInt(json, "mobsKilled", 0);
    }

    public void reset() {
        explosionsCaused = 0;
        tntDetonated = 0;
        creeperExplosions = 0;
        mobsKilledByExplosion = 0;
    }

    private int getInt(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultValue;
    }
}
