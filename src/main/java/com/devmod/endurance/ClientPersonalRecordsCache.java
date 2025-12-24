package com.devmod.endurance;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache for player's personal quest records.
 * Updated via PersonalRecordsSyncPayload from server.
 */
@OnlyIn(Dist.CLIENT)
public class ClientPersonalRecordsCache {

    private static int totalQuestsAttempted = 0;
    private static int totalQuestsCompleted = 0;
    private static int totalPointsEarned = 0;
    private static final Map<String, PersonalRecordsSyncPayload.MobRecord> mobRecords = new HashMap<>();
    private static boolean hasData = false;
    private static long lastSyncTime = 0;

    /**
     * Update cache from server sync payload.
     */
    public static void update(PersonalRecordsSyncPayload payload) {
        totalQuestsAttempted = payload.totalQuestsAttempted();
        totalQuestsCompleted = payload.totalQuestsCompleted();
        totalPointsEarned = payload.totalPointsEarned();
        mobRecords.clear();
        mobRecords.putAll(payload.mobRecords());
        hasData = true;
        lastSyncTime = System.currentTimeMillis();
    }

    /**
     * Get record for a specific mob ID.
     * Returns a record with zeros if not found.
     */
    public static PersonalRecordsSyncPayload.MobRecord getMobRecord(String mobId) {
        return mobRecords.getOrDefault(mobId, new PersonalRecordsSyncPayload.MobRecord(0, 0, 0, 0));
    }

    /**
     * Check if we have received data from server.
     */
    public static boolean hasData() {
        return hasData;
    }

    /**
     * Check if cache is stale (older than 30 seconds).
     */
    public static boolean isStale() {
        return System.currentTimeMillis() - lastSyncTime > 30000;
    }

    public static int getTotalQuestsAttempted() {
        return totalQuestsAttempted;
    }

    public static int getTotalQuestsCompleted() {
        return totalQuestsCompleted;
    }

    public static int getTotalPointsEarned() {
        return totalPointsEarned;
    }

    /**
     * Clear cache (on disconnect).
     */
    public static void clear() {
        totalQuestsAttempted = 0;
        totalQuestsCompleted = 0;
        totalPointsEarned = 0;
        mobRecords.clear();
        hasData = false;
        lastSyncTime = 0;
    }
}
