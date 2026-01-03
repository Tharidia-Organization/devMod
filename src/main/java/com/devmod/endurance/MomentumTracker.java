package com.devmod.endurance;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MomentumTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(MomentumTracker.class);

    public static final MomentumTracker INSTANCE = new MomentumTracker();

    // Configuration
    private static final float KILL_MOMENTUM_GAIN = 15f;       // +15% per kill
    private static final float IDLE_DECAY_PER_SECOND = 3f;     // -3% per second idle
    private static final float OVERDRIVE_THRESHOLD = 100f;
    private static final long OVERDRIVE_DURATION_MS = 15000;   // 15 seconds
    private static final float STAGNANT_SPAWN_MULTIPLIER = 1.2f; // +20% spawn rate
    private static final float STAGNANT_STYLE_DECAY_MULTIPLIER = 2.0f;
    private static final float OVERDRIVE_DAMAGE_MULTIPLIER = 1.5f;
    private static final float OVERDRIVE_STYLE_MULTIPLIER = 2.0f;

    // Time before decay starts after last kill
    private static final long GRACE_PERIOD_MS = 2000; // 2 seconds grace

    // Active momentum sessions per player
    private final Map<UUID, MomentumSession> sessions = new ConcurrentHashMap<>();

    private MomentumTracker() {}

    /**
     * Momentum states that affect gameplay.
     */
    public enum MomentumState {
        STAGNANT("STAGNANT", EnduranceColors.Momentum.STAGNANT, "Too passive! Attack!"),
        BUILDING("", EnduranceColors.Momentum.BUILDING, ""),
        HEATED("HEATED!", EnduranceColors.Momentum.HEATED, "Keep it up!"),
        OVERDRIVE("OVERDRIVE!", EnduranceColors.Momentum.OVERDRIVE, "MAXIMUM POWER!");

        public final String displayName;
        public final int color;
        public final String message;

        MomentumState(String displayName, int color, String message) {
            this.displayName = displayName;
            this.color = color;
            this.message = message;
        }
    }

    /**
     * Tracks momentum for a single player.
     */
    public static class MomentumSession {
        private final UUID playerId;
        private float momentum = 50f; // Start at 50%
        private long lastKillTime;
        private long lastTickTime;
        private MomentumState currentState = MomentumState.BUILDING;
        private boolean inOverdrive = false;
        private long overdriveStartTime = 0;
        private boolean stateChanged = false;

        // Statistics
        private int overdriveCount = 0;
        private long totalOverdriveTime = 0;
        private int stagnantCount = 0;

        public MomentumSession(UUID playerId) {
            this.playerId = playerId;
            this.lastKillTime = System.currentTimeMillis();
            this.lastTickTime = System.currentTimeMillis();
        }

        /**
         * Called when player kills an enemy.
         */
        public MomentumResult onKill() {
            long now = System.currentTimeMillis();
            lastKillTime = now;
            stateChanged = false;

            // If in overdrive, don't add more momentum but refresh timer
            if (inOverdrive) {
                // Extend overdrive slightly with each kill (max +3 sec)
                long elapsed = now - overdriveStartTime;
                if (elapsed < OVERDRIVE_DURATION_MS) {
                    overdriveStartTime = now - Math.max(0, elapsed - 1000);
                }
                return new MomentumResult(currentState, getDamageMultiplier(), getStyleMultiplier(), false, true);
            }

            // Add momentum
            float oldMomentum = momentum;
            momentum = Math.min(OVERDRIVE_THRESHOLD, momentum + KILL_MOMENTUM_GAIN);

            // Check for overdrive trigger
            if (momentum >= OVERDRIVE_THRESHOLD && !inOverdrive) {
                triggerOverdrive(now);
                stateChanged = true;
            }

            // Update state
            updateState();

            LOGGER.debug("[Momentum] Player {} kill: {}% -> {}%, state: {}",
                playerId, (int)oldMomentum, (int)momentum, currentState);

            return new MomentumResult(currentState, getDamageMultiplier(), getStyleMultiplier(), stateChanged, inOverdrive);
        }

        /**
         * Called every tick to process decay.
         */
        public void tick() {
            long now = System.currentTimeMillis();
            long deltaMs = now - lastTickTime;
            lastTickTime = now;

            stateChanged = false;

            // Handle overdrive expiration
            if (inOverdrive) {
                if (now - overdriveStartTime >= OVERDRIVE_DURATION_MS) {
                    endOverdrive();
                    stateChanged = true;
                }
                return; // No decay during overdrive
            }

            // Check grace period
            if (now - lastKillTime < GRACE_PERIOD_MS) {
                return; // No decay during grace period
            }

            // Apply decay
            float decayAmount = (IDLE_DECAY_PER_SECOND * deltaMs) / 1000f;
            momentum = Math.max(0, momentum - decayAmount);

            // Check for stagnant
            MomentumState oldState = currentState;
            updateState();

            if (currentState != oldState) {
                stateChanged = true;
                if (currentState == MomentumState.STAGNANT) {
                    stagnantCount++;
                    LOGGER.debug("[Momentum] Player {} became STAGNANT", playerId);
                }
            }
        }

        private void triggerOverdrive(long now) {
            inOverdrive = true;
            overdriveStartTime = now;
            currentState = MomentumState.OVERDRIVE;
            overdriveCount++;
            LOGGER.info("[Momentum] Player {} triggered OVERDRIVE! (count: {})", playerId, overdriveCount);
        }

        private void endOverdrive() {
            long duration = System.currentTimeMillis() - overdriveStartTime;
            totalOverdriveTime += duration;
            inOverdrive = false;
            momentum = 60f; // Reset to 60% after overdrive
            updateState();
            LOGGER.info("[Momentum] Player {} OVERDRIVE ended after {}ms", playerId, duration);
        }

        private void updateState() {
            if (inOverdrive) {
                currentState = MomentumState.OVERDRIVE;
            } else if (momentum <= 0) {
                currentState = MomentumState.STAGNANT;
            } else if (momentum >= 75) {
                currentState = MomentumState.HEATED;
            } else {
                currentState = MomentumState.BUILDING;
            }
        }

        /**
         * Get current damage multiplier.
         */
        public float getDamageMultiplier() {
            if (inOverdrive) return OVERDRIVE_DAMAGE_MULTIPLIER;
            return 1.0f;
        }

        /**
         * Get current style multiplier.
         */
        public float getStyleMultiplier() {
            if (inOverdrive) return OVERDRIVE_STYLE_MULTIPLIER;
            if (currentState == MomentumState.STAGNANT) return 1.0f / STAGNANT_STYLE_DECAY_MULTIPLIER;
            return 1.0f;
        }

        /**
         * Get spawn rate multiplier (for wave director).
         */
        public float getSpawnMultiplier() {
            if (currentState == MomentumState.STAGNANT) return STAGNANT_SPAWN_MULTIPLIER;
            return 1.0f;
        }

        /**
         * Get style decay multiplier.
         */
        public float getStyleDecayMultiplier() {
            if (currentState == MomentumState.STAGNANT) return STAGNANT_STYLE_DECAY_MULTIPLIER;
            return 1.0f;
        }

        // Getters
        public UUID getPlayerId() { return playerId; }
        public float getMomentum() { return momentum; }
        public MomentumState getState() { return currentState; }
        public boolean isInOverdrive() { return inOverdrive; }
        public boolean hasStateChanged() { return stateChanged; }
        public int getOverdriveCount() { return overdriveCount; }
        public long getTotalOverdriveTime() { return totalOverdriveTime; }
        public int getStagnantCount() { return stagnantCount; }

        /**
         * Get remaining overdrive time in milliseconds.
         */
        public long getOverdriveRemaining() {
            if (!inOverdrive) return 0;
            return Math.max(0, OVERDRIVE_DURATION_MS - (System.currentTimeMillis() - overdriveStartTime));
        }

        /**
         * Get momentum as percentage (0-100).
         */
        public int getMomentumPercent() {
            return (int) Math.min(100, Math.max(0, momentum));
        }
    }

    /**
     * Result of a momentum-affecting action.
     */
    public record MomentumResult(
        MomentumState state,
        float damageMultiplier,
        float styleMultiplier,
        boolean stateChanged,
        boolean isOverdrive
    ) {}

    // ========== Session Management ==========

    /**
     * Start momentum tracking for a player.
     */
    public MomentumSession startSession(UUID playerId) {
        MomentumSession session = new MomentumSession(playerId);
        sessions.put(playerId, session);
        LOGGER.debug("[Momentum] Started session for player {}", playerId);
        return session;
    }

    /**
     * Get active session for a player.
     */
    public MomentumSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    /**
     * End session and return stats.
     */
    public MomentumSession endSession(UUID playerId) {
        MomentumSession session = sessions.remove(playerId);
        if (session != null) {
            LOGGER.info("[Momentum] Session ended for {} - Overdrives: {}, Stagnant: {}, Total OD time: {}ms",
                playerId, session.overdriveCount, session.stagnantCount, session.totalOverdriveTime);
        }
        return session;
    }

    /**
     * Process tick for all active sessions.
     */
    public void tick() {
        for (MomentumSession session : sessions.values()) {
            session.tick();
        }
    }

    /**
     * Called when a player kills an enemy.
     */
    public MomentumResult onPlayerKill(UUID playerId) {
        MomentumSession session = sessions.get(playerId);
        if (session == null) return null;
        return session.onKill();
    }

    /**
     * Check if player is in overdrive.
     */
    public boolean isInOverdrive(UUID playerId) {
        MomentumSession session = sessions.get(playerId);
        return session != null && session.isInOverdrive();
    }

    /**
     * Get damage multiplier for player.
     */
    public float getDamageMultiplier(UUID playerId) {
        MomentumSession session = sessions.get(playerId);
        return session != null ? session.getDamageMultiplier() : 1.0f;
    }

    /**
     * Get spawn rate multiplier for player/party.
     */
    public float getSpawnMultiplier(UUID playerId) {
        MomentumSession session = sessions.get(playerId);
        return session != null ? session.getSpawnMultiplier() : 1.0f;
    }
}
