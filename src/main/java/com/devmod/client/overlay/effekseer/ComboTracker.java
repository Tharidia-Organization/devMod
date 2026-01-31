package com.devmod.client.overlay.effekseer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;

/**
 * Tracks consecutive hits with skill-based combo mechanics.
 *
 * <h2>Game Design Philosophy</h2>
 * <ul>
 *   <li><b>Skill Expression</b> - Combo window shrinks at higher counts, rewarding fast play</li>
 *   <li><b>Tension</b> - Visual/audio feedback as window expires creates urgency</li>
 *   <li><b>Clear Feedback</b> - Combo end triggers distinct "ender" effect</li>
 *   <li><b>Fair Reset</b> - Switching targets resets combo (intentional design)</li>
 * </ul>
 *
 * <h2>Combo Window Decay</h2>
 * <pre>
 * Combo 1-3:   1.5s window (forgiving, learning phase)
 * Combo 4-6:   1.2s window (getting serious)
 * Combo 7-10:  1.0s window (skilled play)
 * Combo 11-20: 0.8s window (expert timing)
 * Combo 21+:   0.6s window (mastery required)
 * </pre>
 *
 * <h2>Multiplier Curve (exponential feel)</h2>
 * <pre>
 * Combo 1-2:   1.0x  (baseline)
 * Combo 3-4:   1.15x (noticeable boost)
 * Combo 5-7:   1.3x  (significant)
 * Combo 8-12:  1.5x  (powerful)
 * Combo 13-20: 1.8x  (devastating)
 * Combo 21+:   2.0x  (maximum, capped for balance)
 * </pre>
 */
public final class ComboTracker {

    // ═══════════════════════════════════════════════════════════════════════════
    // COMBO WINDOW DECAY (skill expression)
    // ═══════════════════════════════════════════════════════════════════════════

    /** Base combo window for first few hits. */
    private static final long BASE_WINDOW_MS = 1500;

    /** Minimum combo window at high combos (mastery). */
    private static final long MIN_WINDOW_MS = 600;

    /** Grace period after target switch before full reset. */
    private static final long TARGET_SWITCH_GRACE_MS = 300;

    /** Maximum combo count for calculations. */
    private static final int MAX_COMBO = 99;

    /** Combo state per player (multiplayer support). */
    private static final Map<UUID, ComboState> PLAYER_COMBOS = new ConcurrentHashMap<>();

    /** Callback for combo end events (for VFX/sound). */
    @Nullable
    private static Consumer<ComboEndEvent> onComboEnd;

    private ComboTracker() {}

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Records a hit and returns combo info.
     *
     * @param attackerUUID The attacker's UUID
     * @param target The target entity
     * @param damage Damage dealt (affects combo "weight")
     * @return ComboInfo with current state
     */
    public static ComboInfo recordHit(UUID attackerUUID, LivingEntity target, float damage) {
        if (attackerUUID == null || target == null) {
            return ComboInfo.NONE;
        }

        long now = System.currentTimeMillis();
        UUID targetUUID = target.getUUID();

        ComboState newState = PLAYER_COMBOS.compute(attackerUUID, (key, existing) -> {
            if (existing == null) {
                // First hit - start new combo
                return new ComboState(targetUUID, 1, now, damage, now);
            }

            long window = getComboWindow(existing.comboCount);
            boolean withinWindow = (now - existing.lastHitTime) <= window;
            boolean sameTarget = targetUUID.equals(existing.targetUUID);

            if (!withinWindow) {
                // Combo expired - fire end event, start new
                fireComboEnd(attackerUUID, existing, ComboEndReason.TIMEOUT);
                return new ComboState(targetUUID, 1, now, damage, now);
            }

            if (!sameTarget) {
                // Target switched
                long sinceLast = now - existing.lastHitTime;
                if (sinceLast <= TARGET_SWITCH_GRACE_MS && existing.comboCount >= 3) {
                    // Grace period: continue combo on new target (rewards aggression)
                    int newCount = Math.min(existing.comboCount + 1, MAX_COMBO);
                    return new ComboState(targetUUID, newCount, now,
                        existing.totalDamage + damage, existing.startTime);
                } else {
                    // Full reset on target switch
                    fireComboEnd(attackerUUID, existing, ComboEndReason.TARGET_SWITCH);
                    return new ComboState(targetUUID, 1, now, damage, now);
                }
            }

            // Continue combo on same target
            int newCount = Math.min(existing.comboCount + 1, MAX_COMBO);
            return new ComboState(targetUUID, newCount, now,
                existing.totalDamage + damage, existing.startTime);
        });

        return ComboInfo.from(newState, now);
    }

    /**
     * Records a kill, returning final combo info.
     * Kills don't reset combo - they're the PEAK of the combo.
     *
     * @param attackerUUID The attacker's UUID
     * @param target The killed target
     * @param damage Final damage dealt
     * @return Final combo info before potential reset
     */
    public static ComboInfo recordKill(UUID attackerUUID, LivingEntity target, float damage) {
        // Record the hit first
        ComboInfo info = recordHit(attackerUUID, target, damage);

        // Fire kill event (special combo milestone)
        ComboState state = PLAYER_COMBOS.get(attackerUUID);
        if (state != null) {
            fireComboEnd(attackerUUID, state, ComboEndReason.KILL);
            // Don't remove - let natural timeout handle it for "overkill" hits
        }

        return info;
    }

    /**
     * Checks and expires stale combos. Call from client tick.
     */
    public static void tick() {
        long now = System.currentTimeMillis();

        PLAYER_COMBOS.entrySet().removeIf(entry -> {
            ComboState state = entry.getValue();
            long window = getComboWindow(state.comboCount);

            if (now - state.lastHitTime > window) {
                fireComboEnd(entry.getKey(), state, ComboEndReason.TIMEOUT);
                return true;
            }
            return false;
        });
    }

    /**
     * Gets current combo info without recording a hit.
     */
    public static ComboInfo getComboInfo(UUID attackerUUID) {
        if (attackerUUID == null) return ComboInfo.NONE;

        ComboState state = PLAYER_COMBOS.get(attackerUUID);
        if (state == null) return ComboInfo.NONE;

        long now = System.currentTimeMillis();
        long window = getComboWindow(state.comboCount);

        if (now - state.lastHitTime > window) {
            return ComboInfo.NONE;
        }

        return ComboInfo.from(state, now);
    }

    /**
     * Sets callback for combo end events.
     */
    public static void setOnComboEnd(@Nullable Consumer<ComboEndEvent> callback) {
        onComboEnd = callback;
    }

    /**
     * Clears combo for a specific player.
     */
    public static void clearForPlayer(UUID playerUUID) {
        if (playerUUID != null) {
            ComboState state = PLAYER_COMBOS.remove(playerUUID);
            if (state != null && state.comboCount >= 3) {
                fireComboEnd(playerUUID, state, ComboEndReason.MANUAL);
            }
        }
    }

    /**
     * Clears all combos.
     */
    public static void clearAll() {
        PLAYER_COMBOS.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMBO WINDOW CALCULATION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the combo window for a given combo count.
     * Window shrinks as combo increases (skill expression).
     */
    public static long getComboWindow(int comboCount) {
        if (comboCount <= 3) return BASE_WINDOW_MS;           // 1.5s - learning
        if (comboCount <= 6) return 1200;                      // 1.2s - serious
        if (comboCount <= 10) return 1000;                     // 1.0s - skilled
        if (comboCount <= 20) return 800;                      // 0.8s - expert
        return MIN_WINDOW_MS;                                  // 0.6s - mastery
    }

    /**
     * Gets multiplier for damage/VFX based on combo count.
     * Exponential curve for satisfying progression.
     */
    public static float getComboMultiplier(int comboCount) {
        if (comboCount <= 2) return 1.0f;
        if (comboCount <= 4) return 1.15f;
        if (comboCount <= 7) return 1.3f;
        if (comboCount <= 12) return 1.5f;
        if (comboCount <= 20) return 1.8f;
        return 2.0f; // Cap at 2x
    }

    /**
     * Gets combo tier for visual feedback.
     */
    public static ComboTier getComboTier(int comboCount) {
        if (comboCount < 3) return ComboTier.NONE;
        if (comboCount < 5) return ComboTier.NICE;      // 3-4
        if (comboCount < 8) return ComboTier.GREAT;     // 5-7
        if (comboCount < 13) return ComboTier.AWESOME;  // 8-12
        if (comboCount < 21) return ComboTier.INSANE;   // 13-20
        return ComboTier.GODLIKE;                        // 21+
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private static void fireComboEnd(UUID attackerUUID, ComboState state, ComboEndReason reason) {
        if (state.comboCount < 3) return; // Don't fire for trivial combos

        Consumer<ComboEndEvent> callback = onComboEnd;
        if (callback != null) {
            callback.accept(new ComboEndEvent(
                attackerUUID,
                state.comboCount,
                state.totalDamage,
                System.currentTimeMillis() - state.startTime,
                getComboTier(state.comboCount),
                reason
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Internal combo state.
     */
    private record ComboState(
        UUID targetUUID,
        int comboCount,
        long lastHitTime,
        float totalDamage,
        long startTime
    ) {}

    /**
     * Combo tiers for visual/audio feedback.
     */
    public enum ComboTier {
        NONE("", com.devmod.client.ui.editor.core.DesignTokens.Combat.ComboTier.NONE),
        NICE("Nice!", com.devmod.client.ui.editor.core.DesignTokens.Combat.ComboTier.NICE),       // Green
        GREAT("Great!", com.devmod.client.ui.editor.core.DesignTokens.Combat.ComboTier.GREAT),     // Cyan
        AWESOME("Awesome!", com.devmod.client.ui.editor.core.DesignTokens.Combat.ComboTier.AWESOME), // Yellow
        INSANE("INSANE!", com.devmod.client.ui.editor.core.DesignTokens.Combat.ComboTier.INSANE),   // Red
        GODLIKE("GODLIKE!", com.devmod.client.ui.editor.core.DesignTokens.Combat.ComboTier.GODLIKE); // Magenta

        private final String displayText;
        private final int color;

        ComboTier(String displayText, int color) {
            this.displayText = displayText;
            this.color = color;
        }

        public String getDisplayText() { return displayText; }
        public int getColor() { return color; }
    }

    /**
     * Reasons for combo ending.
     */
    public enum ComboEndReason {
        TIMEOUT,       // Window expired
        TARGET_SWITCH, // Changed targets
        KILL,          // Target died (celebration!)
        MANUAL         // Cleared programmatically
    }

    /**
     * Event fired when a combo ends.
     */
    public record ComboEndEvent(
        UUID attackerUUID,
        int finalCount,
        float totalDamage,
        long durationMs,
        ComboTier tier,
        ComboEndReason reason
    ) {
        /**
         * Gets a score for this combo (for leaderboards/achievements).
         */
        public int getScore() {
            // Base: combo count squared
            int base = finalCount * finalCount;
            // Bonus for speed (combos under 5s get bonus)
            float speedBonus = durationMs < 5000 ? 1.5f : 1.0f;
            // Bonus for kill finish
            float killBonus = reason == ComboEndReason.KILL ? 1.25f : 1.0f;

            return (int) (base * speedBonus * killBonus);
        }
    }

    /**
     * Public combo info returned from API.
     */
    public record ComboInfo(
        int count,
        float multiplier,
        ComboTier tier,
        long remainingMs,
        long windowMs,
        float totalDamage,
        boolean isActive
    ) {
        public static final ComboInfo NONE = new ComboInfo(0, 1.0f, ComboTier.NONE, 0, 0, 0, false);

        static ComboInfo from(ComboState state, long now) {
            long window = getComboWindow(state.comboCount);
            long elapsed = now - state.lastHitTime;
            long remaining = Math.max(0, window - elapsed);

            return new ComboInfo(
                state.comboCount,
                getComboMultiplier(state.comboCount),
                getComboTier(state.comboCount),
                remaining,
                window,
                state.totalDamage,
                true
            );
        }

        /**
         * Gets urgency factor (0-1) for visual feedback.
         * 1.0 = just hit, 0.0 = about to expire
         */
        public float getUrgency() {
            if (windowMs <= 0) return 0;
            return (float) remainingMs / windowMs;
        }
    }
}
