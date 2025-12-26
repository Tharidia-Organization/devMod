package com.devmod.client.telemetry;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.devmod.telemetry.network.LVCSyncPayload;

/**
 * Client-side cache for LVC (Last Value Cache) telemetry data.
 *
 * <p>Stores the latest telemetry stats received from the server via LVCSyncPayload.
 * Used by HUD overlays to display real-time combat statistics.
 *
 * <p>This is a simple cache that stores the most recent payload. The server
 * sends updates periodically (every 1-2 seconds) during active combat.
 */
public final class ClientLVCCache {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Singleton instance */
    public static final ClientLVCCache INSTANCE = new ClientLVCCache();

    /** Cached payload from server */
    @Nullable
    private volatile LVCSyncPayload cachedData;

    /** Timestamp of last update (client time) */
    private volatile long lastUpdateMs;

    /** Maximum age before data is considered stale (5 seconds) */
    private static final long STALE_THRESHOLD_MS = 5000;

    private ClientLVCCache() {}

    /**
     * Update cache with new data from server.
     * Called by packet handler when LVCSyncPayload is received.
     */
    public void update(LVCSyncPayload payload) {
        this.cachedData = payload;
        this.lastUpdateMs = System.currentTimeMillis();
        LOGGER.debug("[ClientLVC] Updated: DPS={}, kills={}, combo={}",
            payload.currentDPS(), payload.killCount(), payload.currentCombo());
    }

    /**
     * Get current DPS (rolling 60-second window from server).
     */
    public double getCurrentDPS() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.currentDPS() : 0.0;
    }

    /**
     * Get peak DPS (highest single second).
     */
    public double getPeakDPS() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.peakDPS() : 0.0;
    }

    /**
     * Get hit count.
     */
    public int getHitCount() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.hitCount() : 0;
    }

    /**
     * Get miss count.
     */
    public int getMissCount() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.missCount() : 0;
    }

    /**
     * Get accuracy (0.0 - 1.0).
     */
    public double getAccuracy() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.getAccuracy() : 0.0;
    }

    /**
     * Get total damage dealt.
     */
    public double getTotalDamage() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.totalDamage() : 0.0;
    }

    /**
     * Get kill count.
     */
    public int getKillCount() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.killCount() : 0;
    }

    /**
     * Get death count.
     */
    public int getDeathCount() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.deathCount() : 0;
    }

    /**
     * Get K/D ratio.
     */
    public double getKDRatio() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.getKDRatio() : 0.0;
    }

    /**
     * Get current combo.
     */
    public int getCurrentCombo() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.currentCombo() : 0;
    }

    /**
     * Get max combo this session.
     */
    public int getMaxCombo() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.maxCombo() : 0;
    }

    /**
     * Get highest wave reached.
     */
    public int getHighestWave() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.highestWave() : 0;
    }

    /**
     * Get waves completed.
     */
    public int getWavesCompleted() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.wavesCompleted() : 0;
    }

    /**
     * Get critical hit count.
     */
    public int getCriticalHitCount() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.criticalHitCount() : 0;
    }

    /**
     * Get dash ability usage count.
     */
    public int getDashCount() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.dashCount() : 0;
    }

    /**
     * Get dodge ability usage count.
     */
    public int getDodgeCount() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.dodgeCount() : 0;
    }

    /**
     * Get perfect dodge count.
     */
    public int getPerfectDodgeCount() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.perfectDodgeCount() : 0;
    }

    /**
     * Get total damage negated by defensive abilities.
     */
    public double getTotalDamageNegated() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.totalDamageNegated() : 0.0;
    }

    /**
     * Get total stamina spent on abilities.
     */
    public double getTotalStaminaSpent() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.totalStaminaSpent() : 0.0;
    }

    /**
     * Get top weapons string (name:kills pairs).
     */
    public String getTopWeapons() {
        LVCSyncPayload data = cachedData;
        return data != null ? data.topWeapons() : "";
    }

    /**
     * Check if cache has any data.
     */
    public boolean hasData() {
        LVCSyncPayload data = cachedData;
        return data != null && data.hasData();
    }

    /**
     * Check if cached data is stale (no update in 5+ seconds).
     */
    public boolean isStale() {
        return System.currentTimeMillis() - lastUpdateMs > STALE_THRESHOLD_MS;
    }

    /**
     * Get age of cached data in milliseconds.
     */
    public long getDataAgeMs() {
        return System.currentTimeMillis() - lastUpdateMs;
    }

    /**
     * Clear cached data (called on disconnect).
     */
    public void clear() {
        cachedData = null;
        lastUpdateMs = 0;
        LOGGER.debug("[ClientLVC] Cache cleared");
    }

    /**
     * Get the raw cached payload (may be null).
     */
    @Nullable
    public LVCSyncPayload getCachedPayload() {
        return cachedData;
    }
}
