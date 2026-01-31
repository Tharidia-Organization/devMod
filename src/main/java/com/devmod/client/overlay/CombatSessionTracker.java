package com.devmod.client.overlay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.devmod.combat.HitHelper.BodyPart;

/**
 * Tracks combat session statistics for the Combat Recap feature.
 * Collects data per-target and provides aggregated analytics.
 */
@SuppressWarnings("null") // Integer::sum lambda null-safety false positive
public final class CombatSessionTracker {

    public static final CombatSessionTracker INSTANCE = new CombatSessionTracker();

    // === Session State ===
    private boolean sessionActive = false;
    private long sessionStartTime = 0;
    private long sessionEndTime = 0;
    private long lastDamageTime = 0;

    // === Aggregated Stats ===
    private float totalDamageDealt = 0f;
    private int totalHits = 0;
    private int criticalHits = 0;
    private int headshots = 0;
    private float highestSingleHit = 0f;
    private String highestHitTarget = "";

    // === Per-Target Stats ===
    private final Map<UUID, TargetStats> targetStats = new HashMap<>();

    // === Body Part Distribution ===
    private final EnumMap<BodyPart, Integer> bodyPartHits = new EnumMap<>(BodyPart.class);

    // === DPS Timeline (for graph) ===
    private final Deque<DpsSnapshot> dpsTimeline = new ArrayDeque<>();
    private static final int MAX_TIMELINE_ENTRIES = 60; // 60 seconds of data

    // === Session Timeout ===
    private static final long SESSION_TIMEOUT_MS = 8000; // 8 seconds without damage = session ends

    private CombatSessionTracker() {
        for (BodyPart part : BodyPart.values()) {
            bodyPartHits.put(part, 0);
        }
    }

    /**
     * Records a hit from an ImpactData.
     */
    public void recordHit(ImpactData data) {
        if (data == null) return;

        long now = System.currentTimeMillis();

        // Start new session if needed
        if (!sessionActive || (now - lastDamageTime > SESSION_TIMEOUT_MS)) {
            startNewSession();
        }

        lastDamageTime = now;

        float damage = data.hasActualDamage() ? data.getActualDamageDealt() : data.getBreakdown().getFinalDamage();
        if (damage <= 0) return;

        // Update aggregated stats
        totalDamageDealt += damage;
        totalHits++;

        if (data.getBodyPartMultiplier() > 1.5f) {
            criticalHits++;
        }

        if (data.getBodyPart() == BodyPart.HEAD) {
            headshots++;
        }

        if (damage > highestSingleHit) {
            highestSingleHit = damage;
            highestHitTarget = data.getTargetName();
        }

        // Update body part distribution
        bodyPartHits.merge(data.getBodyPart(), 1, Integer::sum);

        // Update per-target stats
        var target = data.getTarget();
        if (target != null) {
            targetStats.computeIfAbsent(target.getUUID(), id -> new TargetStats(data.getTargetName()))
                .addHit(damage, data.getBodyPart(), data.getBodyPartMultiplier() > 1.5f);
        }

        // Update DPS timeline
        updateDpsTimeline(now);
    }

    /**
     * Starts a new combat session, resetting all stats.
     */
    public void startNewSession() {
        sessionActive = true;
        sessionStartTime = System.currentTimeMillis();
        sessionEndTime = 0;
        lastDamageTime = sessionStartTime;

        totalDamageDealt = 0f;
        totalHits = 0;
        criticalHits = 0;
        headshots = 0;
        highestSingleHit = 0f;
        highestHitTarget = "";

        targetStats.clear();
        dpsTimeline.clear();

        for (BodyPart part : BodyPart.values()) {
            bodyPartHits.put(part, 0);
        }
    }

    /**
     * Ends the current session.
     */
    public void endSession() {
        if (sessionActive) {
            sessionEndTime = System.currentTimeMillis();
            sessionActive = false;
        }
    }

    /**
     * Checks if session should auto-end due to timeout.
     * Call this from tick events.
     */
    public void tick() {
        if (sessionActive) {
            long now = System.currentTimeMillis();
            if (now - lastDamageTime > SESSION_TIMEOUT_MS) {
                endSession();
            }
        }
    }

    /**
     * Updates the DPS timeline for graphing.
     */
    private void updateDpsTimeline(long now) {
        // Remove old entries
        while (!dpsTimeline.isEmpty() && now - dpsTimeline.peekFirst().timestamp > 60000) {
            dpsTimeline.removeFirst();
        }

        // Add current DPS snapshot
        float currentDps = calculateCurrentDps();
        dpsTimeline.addLast(new DpsSnapshot(now, currentDps, totalDamageDealt));

        // Limit size
        while (dpsTimeline.size() > MAX_TIMELINE_ENTRIES) {
            dpsTimeline.removeFirst();
        }
    }

    // === Getters ===

    public boolean hasData() {
        return totalHits > 0;
    }

    public boolean isSessionActive() {
        return sessionActive;
    }

    public float getTotalDamage() {
        return totalDamageDealt;
    }

    public int getTotalHits() {
        return totalHits;
    }

    public int getCriticalHits() {
        return criticalHits;
    }

    public int getHeadshots() {
        return headshots;
    }

    public float getCritRate() {
        return totalHits > 0 ? (float) criticalHits / totalHits : 0f;
    }

    public float getHeadshotRate() {
        return totalHits > 0 ? (float) headshots / totalHits : 0f;
    }

    public float getHighestHit() {
        return highestSingleHit;
    }

    public String getHighestHitTarget() {
        return highestHitTarget;
    }

    public float getAverageDamagePerHit() {
        return totalHits > 0 ? totalDamageDealt / totalHits : 0f;
    }

    public long getSessionDurationMs() {
        if (!sessionActive && sessionEndTime > 0) {
            return sessionEndTime - sessionStartTime;
        }
        return System.currentTimeMillis() - sessionStartTime;
    }

    public float getSessionDps() {
        long duration = getSessionDurationMs();
        return duration > 0 ? totalDamageDealt / (duration / 1000f) : 0f;
    }

    /**
     * Calculates current DPS using a 5-second rolling window.
     */
    public float calculateCurrentDps() {
        UUID playerId = getLocalPlayerUUID();
        if (playerId == null) {
            return 0f;
        }
        return ImpactDpsTracker.getCurrentDps(playerId);
    }

    @Nullable
    private UUID getLocalPlayerUUID() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player != null ? mc.player.getUUID() : null;
    }

    public Map<BodyPart, Integer> getBodyPartDistribution() {
        return new EnumMap<>(bodyPartHits);
    }

    public List<TargetStats> getTopTargets(int limit) {
        return targetStats.values().stream()
            .sorted((a, b) -> Float.compare(b.getTotalDamage(), a.getTotalDamage()))
            .limit(limit)
            .toList();
    }

    public List<DpsSnapshot> getDpsTimeline() {
        return new ArrayList<>(dpsTimeline);
    }

    // === Inner Classes ===

    public static class TargetStats {
        private final String name;
        private float totalDamage = 0f;
        private int hits = 0;
        private int crits = 0;
        private final EnumMap<BodyPart, Integer> bodyPartHits = new EnumMap<>(BodyPart.class);

        public TargetStats(String name) {
            this.name = name;
            for (BodyPart part : BodyPart.values()) {
                bodyPartHits.put(part, 0);
            }
        }

        public void addHit(float damage, BodyPart part, boolean isCrit) {
            totalDamage += damage;
            hits++;
            if (isCrit) crits++;
            bodyPartHits.merge(part, 1, Integer::sum);
        }

        public String getName() { return name; }
        public float getTotalDamage() { return totalDamage; }
        public int getHits() { return hits; }
        public int getCrits() { return crits; }
        public EnumMap<BodyPart, Integer> getBodyPartHits() { return bodyPartHits; }

        public float getCritRate() {
            return hits > 0 ? (float) crits / hits : 0f;
        }
    }

    public record DpsSnapshot(long timestamp, float dps, float cumulativeDamage) {}
}
