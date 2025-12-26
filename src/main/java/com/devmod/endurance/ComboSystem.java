package com.devmod.endurance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import com.devmod.endurance.challenges.DailyChallengeManager;
import com.devmod.endurance.config.EnduranceConfigManager;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;

public class ComboSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComboSystem.class);

    public static final ComboSystem INSTANCE = new ComboSystem();

    // Active combo sessions per player
    private final Map<UUID, ComboSession> activeSessions = new ConcurrentHashMap<>();

    // Style rank thresholds
    public enum StyleRank {
        D("Dull", 0, 0x888888, 1.0f),
        C("Crazy", 500, 0x66FF66, 1.2f),
        B("Brutal", 1500, 0x6666FF, 1.5f),
        A("Apocalyptic", 3500, 0xFFFF00, 2.0f),
        S("Savage", 7000, 0xFF9900, 3.0f),
        SS("Sadistic", 12000, 0xFF3300, 4.0f),
        SSS("Sensational", 20000, 0xFF00FF, 5.0f);

        public final String displayName;
        public final int threshold;
        public final int color;
        public final float multiplier;

        StyleRank(String displayName, int threshold, int color, float multiplier) {
            this.displayName = displayName;
            this.threshold = threshold;
            this.color = color;
            this.multiplier = multiplier;
        }

        public static StyleRank fromScore(int styleScore) {
            StyleRank result = D;
            for (StyleRank rank : values()) {
                if (styleScore >= rank.threshold) {
                    result = rank;
                }
            }
            return result;
        }

        public StyleRank getNext() {
            int idx = this.ordinal();
            StyleRank[] values = values();
            return idx < values.length - 1 ? values[idx + 1] : this;
        }
    }

    /**
     * Types of combat actions that contribute to style.
     */
    public enum ActionType {
        // Basic attacks
        LIGHT_ATTACK("Light Attack", 10, 50),
        HEAVY_ATTACK("Heavy Attack", 25, 100),
        CRITICAL_HIT("Critical Hit!", 50, 150),

        // Special attacks
        AERIAL_ATTACK("Aerial!", 40, 120),
        BACKSTAB("Backstab!", 60, 180),
        HEADSHOT("Headshot!", 80, 200),

        // Defensive actions
        PERFECT_DODGE("Perfect Dodge!", 100, 250),
        PARRY("Parry!", 120, 300),
        COUNTER_ATTACK("Counter!", 150, 350),

        // Kills
        QUICK_KILL("Quick Kill!", 75, 200),
        MULTI_KILL("Multi-Kill!", 150, 400),
        OVERKILL("Overkill!", 100, 250),

        // Combos
        COMBO_5("5 Hit Combo!", 50, 100),
        COMBO_10("10 Hit Combo!", 100, 200),
        COMBO_25("25 Hit Combo!", 250, 500),
        COMBO_50("50 Hit Combo!", 500, 1000),
        COMBO_100("100 Hit Combo!", 1000, 2000),

        // Finishers
        EXECUTION("EXECUTED!", 200, 500),

        // Special
        NO_DAMAGE_WAVE("Untouchable!", 500, 1000),
        SPEED_CLEAR("Speed Demon!", 300, 600),
        VARIETY_MASTER("Variety Master!", 200, 400);

        public final String displayName;
        public final int basePoints;
        public final int stylePoints;

        ActionType(String displayName, int basePoints, int stylePoints) {
            this.displayName = displayName;
            this.basePoints = basePoints;
            this.stylePoints = stylePoints;
        }
    }

    /**
     * Tracks combo state for a single player during a quest.
     */
    public static class ComboSession {
        private final UUID playerId;
        private final UUID questId;

        // Config manager reference
        private static final EnduranceConfigManager config = EnduranceConfigManager.INSTANCE;

        // Combo tracking
        private int currentCombo = 0;
        private int maxCombo = 0;
        private long lastHitTime = 0;
        // Default: 60 ticks = 3 seconds (1000ms * 3)
        private static final long DEFAULT_COMBO_TIMEOUT_MS = 3000;

        // Style tracking
        private int styleScore = 0;
        private int totalStyleEarned = 0;
        private StyleRank currentRank = StyleRank.D;
        private StyleRank highestRank = StyleRank.D;

        // Style decay
        private long lastStyleGainTime = 0;
        // Default: Check every second (20 ticks = 1000ms)
        private static final long DEFAULT_STYLE_DECAY_INTERVAL_MS = 1000;
        private static final int DEFAULT_STYLE_DECAY_RATE = 50; // Points lost per second when idle

        // Config-aware getters
        private long getComboTimeoutMs() {
            if (questId != null) {
                // Convert ticks to ms (20 ticks = 1000ms)
                return config.getComboTimeoutTicks(questId) * 50L;
            }
            return DEFAULT_COMBO_TIMEOUT_MS;
        }

        private long getStyleDecayIntervalMs() {
            if (questId != null) {
                // Convert ticks to ms (20 ticks = 1000ms)
                return config.getStyleDecayDelayTicks(questId) * 50L;
            }
            return DEFAULT_STYLE_DECAY_INTERVAL_MS;
        }

        private int getStyleDecayRate() {
            if (questId != null) {
                return (int) config.getStyleDecayRate(questId);
            }
            return DEFAULT_STYLE_DECAY_RATE;
        }

        // Variety tracking (rewards different attack types)
        private final Set<ActionType> actionsUsedThisCombo = new HashSet<>();
        private int varietyBonus = 0;

        // Flow State tracking (STALE/FRESH/VIRTUOSO)
        private final FlowStateTracker flowState = new FlowStateTracker();
        private FlowStateTracker.FlowState lastFlowState = FlowStateTracker.FlowState.NEUTRAL;

        // Combat stats
        private int totalHits = 0;
        private int totalKills = 0;
        private float totalDamage = 0;
        private float damageTakenThisWave = 0;

        // Perfect timing tracking
        private int perfectDodges = 0;
        private int parries = 0;
        private int counterAttacks = 0;

        // Kill timing for multi-kills
        private final List<Long> recentKillTimes = new ArrayList<>();
        private static final long MULTI_KILL_WINDOW_MS = 2000;

        // Action history for announcements
        private final List<ActionAnnouncement> recentAnnouncements = new ArrayList<>();
        private static final int MAX_ANNOUNCEMENTS = 5;

        // Combo decay tracking for notifications
        private int pendingComboLost = 0;
        private StyleRank pendingPreviousRank = null;
        private StyleRank pendingNewRank = null;

        public ComboSession(UUID playerId, UUID questId) {
            this.playerId = playerId;
            this.questId = questId;
            this.lastHitTime = System.currentTimeMillis();
            this.lastStyleGainTime = System.currentTimeMillis();
        }

        /**
         * Check if there's a pending combo decay notification.
         * Returns true and clears the pending notification if present.
         */
        public boolean hasPendingDecayNotification() {
            return pendingComboLost > 0 || pendingPreviousRank != null;
        }

        /**
         * Get and clear the pending combo lost count.
         */
        public int consumePendingComboLost() {
            int lost = pendingComboLost;
            pendingComboLost = 0;
            return lost;
        }

        /**
         * Get and clear the pending rank change.
         * Returns [previousRank, newRank] or null if no rank change.
         */
        public StyleRank[] consumePendingRankChange() {
            if (pendingPreviousRank == null) return null;
            StyleRank[] result = new StyleRank[] { pendingPreviousRank, pendingNewRank };
            pendingPreviousRank = null;
            pendingNewRank = null;
            return result;
        }

        /**
         * Register a combat action.
         */
        public ActionResult registerAction(ActionType action, float damage) {
            long now = System.currentTimeMillis();

            // Check for combo timeout
            if (now - lastHitTime > getComboTimeoutMs() && currentCombo > 0) {
                endCombo();
            }

            // Update combo
            currentCombo++;
            if (currentCombo > maxCombo) {
                maxCombo = currentCombo;
            }
            lastHitTime = now;

            // Track variety
            boolean isNewAction = actionsUsedThisCombo.add(action);
            if (isNewAction) {
                varietyBonus += 25;
            }

            // Process flow state BEFORE calculating points
            FlowStateTracker.FlowResult flowResult = flowState.processAction(action);
            lastFlowState = flowResult.state();

            // Calculate points with multipliers (including flow state)
            float comboMultiplier = 1.0f + (currentCombo * 0.02f); // +2% per combo hit
            float varietyMultiplier = 1.0f + (varietyBonus * 0.01f);
            float rankMultiplier = currentRank.multiplier;
            float flowMultiplier = flowResult.styleMultiplier();

            int basePoints = (int) (action.basePoints * comboMultiplier * varietyMultiplier * rankMultiplier);
            int styleGain = (int) (action.stylePoints * varietyMultiplier * flowMultiplier);

            // Add style
            styleScore += styleGain;
            totalStyleEarned += styleGain;
            lastStyleGainTime = now;

            // Update rank
            StyleRank newRank = StyleRank.fromScore(styleScore);
            boolean rankUp = newRank.ordinal() > currentRank.ordinal();
            StyleRank oldRank = currentRank;
            currentRank = newRank;
            if (newRank.ordinal() > highestRank.ordinal()) {
                highestRank = newRank;
            }

            // Track daily challenge progress (style rank and combo)
            DailyChallengeManager.INSTANCE.onStyleRankUpdate(playerId, newRank);
            DailyChallengeManager.INSTANCE.onComboUpdate(playerId, currentCombo);

            // Track weekly challenge progress (style rank and combo)
            com.devmod.endurance.challenges.WeeklyChallengeManager.INSTANCE.onStyleRankAchieved(playerId, newRank);
            com.devmod.endurance.challenges.WeeklyChallengeManager.INSTANCE.onComboUpdate(playerId, currentCombo);

            // Telemetry: record rank change
            if (rankUp && questId != null) {
                EnduranceTelemetryService.INSTANCE.recordStyleRankChange(
                    playerId, questId, oldRank, newRank, styleScore, currentCombo
                );
            }

            // Telemetry: record special actions
            if (action.stylePoints >= 100 && questId != null) {
                EnduranceTelemetryService.INSTANCE.recordSpecialAction(
                    playerId, questId, action, basePoints, styleGain, currentCombo
                );
            }

            // Track stats
            totalHits++;
            totalDamage += damage;

            // Check for combo milestones
            checkComboMilestones();

            // Create announcement if significant
            ActionAnnouncement announcement = null;
            if (action.stylePoints >= 100 || rankUp) {
                announcement = new ActionAnnouncement(action, styleGain, rankUp ? newRank : null);
                addAnnouncement(announcement);
            }

            return new ActionResult(basePoints, styleGain, currentCombo, currentRank, announcement);
        }

        /**
         * Register a kill.
         */
        public ActionResult registerKill(boolean wasQuick, float overkillDamage) {
            long now = System.currentTimeMillis();
            totalKills++;

            // Track for multi-kills
            recentKillTimes.removeIf(t -> now - t > MULTI_KILL_WINDOW_MS);
            recentKillTimes.add(now);

            ActionType killType = ActionType.LIGHT_ATTACK;

            // Determine kill type
            if (recentKillTimes.size() >= 3) {
                killType = ActionType.MULTI_KILL;
            } else if (wasQuick) {
                killType = ActionType.QUICK_KILL;
            } else if (overkillDamage > 10) {
                killType = ActionType.OVERKILL;
            }

            return registerAction(killType, overkillDamage);
        }

        /**
         * Called when player takes damage - affects style.
         */
        public void onDamageTaken(float damage) {
            damageTakenThisWave += damage;

            // Track previous state for decay notification
            StyleRank previousRank = currentRank;
            int previousCombo = currentCombo;

            // Style penalty for getting hit
            int penalty = (int) (damage * 10);
            styleScore = Math.max(0, styleScore - penalty);

            // Combo penalty
            currentCombo = Math.max(0, currentCombo / 2);

            // Update rank
            currentRank = StyleRank.fromScore(styleScore);

            // Set pending notification if significant decay occurred
            int comboLost = previousCombo - currentCombo;
            if (comboLost >= 3 || currentRank.ordinal() < previousRank.ordinal()) {
                pendingComboLost = comboLost;
                if (currentRank.ordinal() < previousRank.ordinal()) {
                    pendingPreviousRank = previousRank;
                    pendingNewRank = currentRank;
                }

                // Telemetry: record combo break
                if (questId != null && comboLost > 0) {
                    EnduranceTelemetryService.INSTANCE.recordComboBreak(
                        playerId, questId, comboLost, previousRank, currentRank, damage
                    );
                }
            }
        }

        /**
         * Called when player perfectly dodges an attack.
         */
        public ActionResult registerPerfectDodge() {
            perfectDodges++;
            return registerAction(ActionType.PERFECT_DODGE, 0);
        }

        /**
         * Called when player parries an attack.
         */
        public ActionResult registerParry() {
            parries++;
            return registerAction(ActionType.PARRY, 0);
        }

        /**
         * Called when player counter-attacks after dodge/parry.
         */
        public ActionResult registerCounterAttack(float damage) {
            counterAttacks++;
            return registerAction(ActionType.COUNTER_ATTACK, damage);
        }

        /**
         * Called when wave is completed without taking damage.
         */
        public ActionResult registerNoDamageWave() {
            if (damageTakenThisWave == 0) {
                return registerAction(ActionType.NO_DAMAGE_WAVE, 0);
            }
            return null;
        }

        /**
         * Process style decay (call every tick or second).
         */
        public void tick() {
            long now = System.currentTimeMillis();

            // Style decay when not attacking
            long decayInterval = getStyleDecayIntervalMs();
            if (now - lastStyleGainTime > decayInterval) {
                int decayAmount = (int) ((now - lastStyleGainTime) / decayInterval * getStyleDecayRate());
                styleScore = Math.max(0, styleScore - decayAmount);
                lastStyleGainTime = now - (now - lastStyleGainTime) % decayInterval;

                // Update rank after decay
                currentRank = StyleRank.fromScore(styleScore);
            }

            // Clean up old announcements
            recentAnnouncements.removeIf(a -> now - a.timestamp > 2000);
        }

        /**
         * Start new wave.
         */
        public void startNewWave() {
            damageTakenThisWave = 0;
        }

        /**
         * Add bonus points directly to style score (for wave completion, etc.)
         */
        public void addBonusPoints(int points) {
            styleScore += points;
            totalStyleEarned += points;

            // Update rank after bonus
            StyleRank newRank = StyleRank.fromScore(styleScore);
            if (newRank.ordinal() > currentRank.ordinal()) {
                currentRank = newRank;
            }
            if (newRank.ordinal() > highestRank.ordinal()) {
                highestRank = newRank;
            }
        }

        /**
         * End current combo.
         */
        private void endCombo() {
            currentCombo = 0;
            actionsUsedThisCombo.clear();
            varietyBonus = 0;
            flowState.onComboEnd();
            lastFlowState = FlowStateTracker.FlowState.NEUTRAL;
        }

        /**
         * Check and award combo milestone bonuses.
         * Note: Uses awardMilestoneBonus instead of registerAction to avoid
         * incrementing the combo counter for milestone rewards themselves.
         */
        private void checkComboMilestones() {
            if (currentCombo == 5) {
                awardMilestoneBonus(ActionType.COMBO_5);
            } else if (currentCombo == 10) {
                awardMilestoneBonus(ActionType.COMBO_10);
            } else if (currentCombo == 25) {
                awardMilestoneBonus(ActionType.COMBO_25);
            } else if (currentCombo == 50) {
                awardMilestoneBonus(ActionType.COMBO_50);
            } else if (currentCombo == 100) {
                awardMilestoneBonus(ActionType.COMBO_100);
            }
        }

        /**
         * Award milestone bonus without affecting combo count.
         */
        private void awardMilestoneBonus(ActionType milestone) {
            long now = System.currentTimeMillis();

            // Calculate style gain (without combo increment)
            float varietyMultiplier = 1.0f + (varietyBonus * 0.01f);
            int styleGain = (int) (milestone.stylePoints * varietyMultiplier);

            styleScore += styleGain;
            totalStyleEarned += styleGain;
            lastStyleGainTime = now;

            // Update rank
            StyleRank newRank = StyleRank.fromScore(styleScore);
            boolean rankUp = newRank.ordinal() > currentRank.ordinal();
            currentRank = newRank;
            if (newRank.ordinal() > highestRank.ordinal()) {
                highestRank = newRank;
            }

            // Telemetry: record combo milestone
            if (questId != null) {
                EnduranceTelemetryService.INSTANCE.recordComboMilestone(
                    playerId, questId, currentCombo, styleGain, currentRank
                );
            }

            // Create announcement
            ActionAnnouncement announcement = new ActionAnnouncement(milestone, styleGain, rankUp ? newRank : null);
            addAnnouncement(announcement);
        }

        private void addAnnouncement(ActionAnnouncement announcement) {
            recentAnnouncements.add(announcement);
            while (recentAnnouncements.size() > MAX_ANNOUNCEMENTS) {
                recentAnnouncements.remove(0);
            }
        }

        // Getters
        public UUID getPlayerId() { return playerId; }
        public UUID getQuestId() { return questId; }
        public int getCurrentCombo() { return currentCombo; }
        public int getMaxCombo() { return maxCombo; }
        public int getStyleScore() { return styleScore; }
        public int getTotalStyleEarned() { return totalStyleEarned; }
        public StyleRank getCurrentRank() { return currentRank; }
        public StyleRank getHighestRank() { return highestRank; }
        public int getTotalHits() { return totalHits; }
        public int getTotalKills() { return totalKills; }
        public float getTotalDamage() { return totalDamage; }
        public int getPerfectDodges() { return perfectDodges; }
        public int getParries() { return parries; }
        public int getCounterAttacks() { return counterAttacks; }
        public List<ActionAnnouncement> getRecentAnnouncements() { return recentAnnouncements; }

        // Flow State getters (for HUD display)
        public FlowStateTracker.FlowState getFlowState() { return lastFlowState; }
        public boolean isVirtuoso() { return flowState.isVirtuoso(); }
        public boolean isStale() { return flowState.isStale(); }
        public float getVirtuosoProgress() { return flowState.getVirtuosoProgress(); }
        public float getStaleRisk() { return flowState.getStaleRisk(); }
        public int getUniqueActionCount() { return flowState.getUniqueActionCount(); }

        /**
         * Get final score with all multipliers.
         */
        public int getFinalScore() {
            float baseScore = totalStyleEarned;
            float comboBonus = maxCombo * 10;
            float rankBonus = highestRank.ordinal() * 500;
            float perfectionBonus = (perfectDodges + parries * 2 + counterAttacks * 3) * 50;

            return (int) (baseScore + comboBonus + rankBonus + perfectionBonus);
        }
    }

    /**
     * Result of registering an action.
     */
    public record ActionResult(
        int pointsEarned,
        int styleEarned,
        int currentCombo,
        StyleRank currentRank,
        ActionAnnouncement announcement
    ) {}

    /**
     * Announcement to display to player.
     */
    public static class ActionAnnouncement {
        public final ActionType action;
        public final int styleEarned;
        public final StyleRank newRank; // null if no rank up
        public final long timestamp;

        public ActionAnnouncement(ActionType action, int styleEarned, StyleRank newRank) {
            this.action = action;
            this.styleEarned = styleEarned;
            this.newRank = newRank;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isRankUp() {
            return newRank != null;
        }
    }

    // ========== Session Management ==========

    /**
     * Start a combo session for a player.
     */
    public ComboSession startSession(UUID playerId, UUID questId) {
        ComboSession session = new ComboSession(playerId, questId);
        activeSessions.put(playerId, session);
        LOGGER.debug("[ComboSystem] Started session for player {}", playerId);
        return session;
    }

    /**
     * Get active session for a player.
     */
    public Optional<ComboSession> getSession(UUID playerId) {
        return Optional.ofNullable(activeSessions.get(playerId));
    }

    /**
     * End session and return final stats.
     */
    public ComboSession endSession(UUID playerId) {
        ComboSession session = activeSessions.remove(playerId);
        if (session != null) {
            LOGGER.info("[ComboSystem] Session ended for player {} - Final: {} style, {} max combo, {} rank",
                playerId, session.totalStyleEarned, session.maxCombo, session.highestRank);
        }
        return session;
    }

    /**
     * Process tick for all active sessions.
     */
    public void tick() {
        for (ComboSession session : activeSessions.values()) {
            session.tick();
        }
    }

    // ========== Event Handlers ==========

    /**
     * Called when player attacks a mob.
     */
    public ActionResult onPlayerAttack(ServerPlayer player, LivingEntity target, float damage,
                                        boolean isCritical, boolean isAerial, boolean isBackstab) {
        ComboSession session = activeSessions.get(player.getUUID());
        if (session == null) return null;

        ActionType action;
        if (isCritical) {
            action = ActionType.CRITICAL_HIT;
        } else if (isAerial) {
            action = ActionType.AERIAL_ATTACK;
        } else if (isBackstab) {
            action = ActionType.BACKSTAB;
        } else if (damage >= 10) {
            action = ActionType.HEAVY_ATTACK;
        } else {
            action = ActionType.LIGHT_ATTACK;
        }

        ActionResult result = session.registerAction(action, damage);

        // Play sound on rank up
        if (result.announcement() != null && result.announcement().isRankUp()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                Objects.requireNonNull(SoundEvents.PLAYER_LEVELUP), SoundSource.PLAYERS, 1.0f, 1.5f);
        }

        return result;
    }

    /**
     * Called when player kills a mob.
     */
    public ActionResult onMobKilled(ServerPlayer player, LivingEntity mob, float overkillDamage, long killTime) {
        ComboSession session = activeSessions.get(player.getUUID());
        if (session == null) return null;

        boolean wasQuick = killTime < 5000; // Under 5 seconds
        return session.registerKill(wasQuick, overkillDamage);
    }

    /**
     * Called when player takes damage.
     */
    public void onPlayerDamaged(ServerPlayer player, float damage) {
        ComboSession session = activeSessions.get(player.getUUID());
        if (session != null) {
            session.onDamageTaken(damage);
        }
    }

    /**
     * Called when wave is completed.
     */
    public ActionResult onWaveCompleted(ServerPlayer player) {
        ComboSession session = activeSessions.get(player.getUUID());
        if (session != null) {
            ActionResult result = session.registerNoDamageWave();
            session.startNewWave();
            return result;
        }
        return null;
    }

    private ComboSystem() {}
}
