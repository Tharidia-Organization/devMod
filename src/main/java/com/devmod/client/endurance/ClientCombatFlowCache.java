package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.List;

import com.devmod.endurance.CombatFlowSyncPayload;
import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceColors;
import com.devmod.endurance.FlowStateTracker;
import com.devmod.endurance.MomentumTracker;

/**
 * Client-side cache for combat flow state received from server.
 * Used by CombatFlowHudOverlay for rendering.
 *
 * Includes animation state for smooth transitions between updates.
 */
public final class ClientCombatFlowCache {

    public static final ClientCombatFlowCache INSTANCE = new ClientCombatFlowCache();

    // Current synced state
    private CombatFlowSyncPayload currentState = CombatFlowSyncPayload.EMPTY;
    private long lastUpdateTime = 0;

    // Animation state
    private int displayCombo = 0;
    private float displayStyleScore = 0;
    private float displayMomentum = 50;
    private float comboScale = 1.0f;
    private float rankFlashAlpha = 0f;
    private float momentumPulse = 0f;

    // State change tracking for animations
    private ComboSystem.StyleRank previousRank = ComboSystem.StyleRank.D;

    // State change flags - consumed by CombatFlowHudOverlay.playSoundsForStateChanges()
    private boolean comboJustIncreased = false;
    private boolean rankJustChanged = false;
    private boolean overdriveJustStarted = false;

    // Action announcements queue
    private final List<ActionAnnouncement> announcements = new ArrayList<>();
    private static final int MAX_ANNOUNCEMENTS = 4;
    private static final long ANNOUNCEMENT_DURATION_MS = 2000;

    private ClientCombatFlowCache() {}

    /**
     * Update cache with new server state.
     * Called when CombatFlowSyncPayload is received.
     */
    public void update(CombatFlowSyncPayload payload) {
        long now = System.currentTimeMillis();

        // Detect state changes for animations
        if (payload.combo() > currentState.combo()) {
            comboJustIncreased = true;
            comboScale = 1.5f; // Pop effect
        }

        ComboSystem.StyleRank newRank = payload.getStyleRank();
        if (newRank.ordinal() > previousRank.ordinal()) {
            rankJustChanged = true;
            rankFlashAlpha = 1.0f;
            // Add rank-up announcement
            addAnnouncement("RANK UP!", newRank.getDisplayName(), newRank.getColor(), true);
        }
        previousRank = newRank;

        if (payload.isInOverdrive() && !currentState.isInOverdrive()) {
            overdriveJustStarted = true;
            addAnnouncement("OVERDRIVE!", "MAXIMUM POWER!", EnduranceColors.Momentum.OVERDRIVE, true);
        }

        // Add action announcement if present
        if (!payload.lastActionName().isEmpty() && payload.lastActionPoints() > 0) {
            addAnnouncement(payload.lastActionName(), "+" + payload.lastActionPoints(), getFlowColor(payload), false);
        }

        currentState = payload;
        lastUpdateTime = now;
    }

    private int getFlowColor(CombatFlowSyncPayload payload) {
        return payload.getFlowState().getColor();
    }

    /**
     * Tick animation state. Call every frame.
     */
    public void tickAnimations(float partialTick) {
        // Smooth combo counter
        if (displayCombo < currentState.combo()) {
            displayCombo = Math.min(currentState.combo(), displayCombo + 1);
        } else if (displayCombo > currentState.combo()) {
            displayCombo = currentState.combo();
        }

        // Smooth style score
        float targetStyle = currentState.styleScore();
        displayStyleScore += (targetStyle - displayStyleScore) * 0.15f;

        // Smooth momentum
        float targetMomentum = currentState.momentumPercent();
        displayMomentum += (targetMomentum - displayMomentum) * 0.1f;

        // Decay combo scale
        if (comboScale > 1.0f) {
            comboScale = Math.max(1.0f, comboScale - 0.05f);
        }

        // Decay rank flash
        if (rankFlashAlpha > 0) {
            rankFlashAlpha = Math.max(0, rankFlashAlpha - 0.03f);
        }

        // Momentum pulse for overdrive
        if (currentState.isInOverdrive()) {
            momentumPulse = (float) Math.sin(System.currentTimeMillis() * 0.01) * 0.5f + 0.5f;
        } else {
            momentumPulse = 0;
        }

        // Reset state change flags after tick (consumers should read before this)
        comboJustIncreased = false;
        rankJustChanged = false;
        overdriveJustStarted = false;

        // Clean expired announcements
        long now = System.currentTimeMillis();
        announcements.removeIf(a -> now - a.timestamp() > ANNOUNCEMENT_DURATION_MS);
    }

    private void addAnnouncement(String title, String subtitle, int color, boolean important) {
        announcements.add(new ActionAnnouncement(title, subtitle, color, important, System.currentTimeMillis()));
        while (announcements.size() > MAX_ANNOUNCEMENTS) {
            announcements.remove(0);
        }
    }

    // === Getters for HUD rendering ===

    public boolean isActive() {
        return !currentState.isEmpty() && System.currentTimeMillis() - lastUpdateTime < 10000;
    }

    public int getCombo() {
        return displayCombo;
    }

    public int getMaxCombo() {
        return currentState.maxCombo();
    }

    /**
     * Get smoothed style score for display.
     * Currently unused but available for future UI showing numeric score.
     */
    public float getStyleScore() {
        return displayStyleScore;
    }

    public ComboSystem.StyleRank getStyleRank() {
        return currentState.getStyleRank();
    }

    public FlowStateTracker.FlowState getFlowState() {
        return currentState.getFlowState();
    }

    public float getVirtuosoProgress() {
        return currentState.virtuosoProgress();
    }

    public float getStaleRisk() {
        return currentState.staleRisk();
    }

    public float getMomentumPercent() {
        return displayMomentum;
    }

    public MomentumTracker.MomentumState getMomentumState() {
        return currentState.getMomentumState();
    }

    public boolean isInOverdrive() {
        return currentState.isInOverdrive();
    }

    public long getOverdriveRemainingMs() {
        return currentState.overdriveRemainingMs();
    }

    public float getComboScale() {
        return comboScale;
    }

    public float getRankFlashAlpha() {
        return rankFlashAlpha;
    }

    public float getMomentumPulse() {
        return momentumPulse;
    }

    public List<ActionAnnouncement> getAnnouncements() {
        return announcements;
    }

    // === State change flags for sound/VFX consumers ===

    /** Returns true if combo just increased this tick (resets after tickAnimations). */
    public boolean wasComboJustIncreased() {
        return comboJustIncreased;
    }

    /** Returns true if rank just changed this tick (resets after tickAnimations). */
    public boolean wasRankJustChanged() {
        return rankJustChanged;
    }

    /** Returns true if overdrive just started this tick (resets after tickAnimations). */
    public boolean wasOverdriveJustStarted() {
        return overdriveJustStarted;
    }

    /**
     * Get progress to next style rank (0.0 - 1.0).
     */
    public float getRankProgress() {
        ComboSystem.StyleRank current = getStyleRank();
        ComboSystem.StyleRank next = current.getNext();
        if (current == next) return 1.0f; // Already max rank

        int score = (int) displayStyleScore;
        int currentThreshold = current.getThreshold();
        int nextThreshold = next.getThreshold();
        int range = nextThreshold - currentThreshold;

        return range > 0 ? Math.min(1.0f, (float)(score - currentThreshold) / range) : 0f;
    }

    /**
     * Clear cache when leaving quest.
     */
    public void clear() {
        currentState = CombatFlowSyncPayload.EMPTY;
        displayCombo = 0;
        displayStyleScore = 0;
        displayMomentum = 50;
        previousRank = ComboSystem.StyleRank.D;
        announcements.clear();
    }

    /**
     * Announcement for display.
     */
    public record ActionAnnouncement(
        String title,
        String subtitle,
        int color,
        boolean important,
        long timestamp
    ) {
        public float getProgress() {
            float elapsed = System.currentTimeMillis() - timestamp;
            return Math.min(1.0f, elapsed / ANNOUNCEMENT_DURATION_MS);
        }

        public float getAlpha() {
            float progress = getProgress();
            if (progress < 0.1f) {
                // Fade in
                return progress / 0.1f;
            } else if (progress > 0.7f) {
                // Fade out
                return 1.0f - ((progress - 0.7f) / 0.3f);
            }
            return 1.0f;
        }
    }
}
