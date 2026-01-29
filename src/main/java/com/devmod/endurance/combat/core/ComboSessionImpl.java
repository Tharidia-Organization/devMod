package com.devmod.endurance.combat.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.devmod.endurance.ComboSystem.ActionType;
import com.devmod.endurance.ComboSystem.StyleRank;
import com.devmod.endurance.FlowStateTracker;
import com.devmod.endurance.combat.api.ComboEvent;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.combat.events.ComboEventDispatcher;
import com.devmod.endurance.combat.scoring.StyleRankResolver;
import com.devmod.endurance.config.EnduranceConfigManager;

/**
 * Implementation of {@link IComboSession} using composition of specialized trackers.
 *
 * <p>This class composes:</p>
 * <ul>
 *   <li>{@link ComboTracker} - Combo count and timeout</li>
 *   <li>{@link StyleTracker} - Style score and rank</li>
 *   <li>{@link CombatStatsTracker} - Combat statistics</li>
 *   <li>{@link FlowStateTracker} - Flow state (STALE/FRESH/VIRTUOSO)</li>
 * </ul>
 *
 * <p>Events are dispatched to the {@link ComboEventDispatcher} instead of
 * calling dependent systems directly. This enables loose coupling with
 * telemetry, challenges, and notifications.</p>
 */
public final class ComboSessionImpl implements IComboSession {

    // Identity
    private final UUID playerId;
    private final UUID questId;

    // Composed trackers
    private final ComboTracker comboTracker;
    private final StyleTracker styleTracker;
    private final CombatStatsTracker statsTracker;
    private final FlowStateTracker flowTracker;

    // Event dispatch
    private final ComboEventDispatcher dispatcher;

    // Announcements
    private final List<ActionAnnouncement> recentAnnouncements = new ArrayList<>();
    private static final int MAX_ANNOUNCEMENTS = 5;
    private static final long ANNOUNCEMENT_LIFETIME_MS = 2000;

    // Grace period after wave start
    private long waveStartTime = 0;
    private static final long WAVE_GRACE_PERIOD_MS = 2000;

    /**
     * Create session with default configuration.
     */
    public ComboSessionImpl(UUID playerId, UUID questId, ComboEventDispatcher dispatcher) {
        this(playerId, questId, dispatcher, createDefaultConfig(questId));
    }

    /**
     * Create session with custom configuration.
     */
    public ComboSessionImpl(UUID playerId, UUID questId, ComboEventDispatcher dispatcher,
                             SessionConfig config) {
        this.playerId = playerId;
        this.questId = questId;
        this.dispatcher = dispatcher;

        // Initialize trackers
        this.comboTracker = new ComboTracker(config.comboTimeoutMs());
        this.styleTracker = new StyleTracker(questId, config.rankResolver(),
            config.styleDecayIntervalMs(), config.styleDecayRate());
        this.statsTracker = new CombatStatsTracker();
        this.flowTracker = new FlowStateTracker();

        this.waveStartTime = System.currentTimeMillis();
    }

    // === IComboSession Implementation ===

    @Override
    public UUID getPlayerId() {
        return playerId;
    }

    @Override
    public UUID getQuestId() {
        return questId;
    }

    @Override
    public int getCurrentCombo() {
        return comboTracker.getCurrentCombo();
    }

    @Override
    public int getMaxCombo() {
        return comboTracker.getMaxCombo();
    }

    @Override
    public boolean isComboActive() {
        return comboTracker.isActive();
    }

    @Override
    public int getStyleScore() {
        return styleTracker.getStyleScore();
    }

    @Override
    public int getTotalStyleEarned() {
        return styleTracker.getTotalStyleEarned();
    }

    @Override
    public StyleRank getCurrentRank() {
        return styleTracker.getCurrentRank();
    }

    @Override
    public StyleRank getHighestRank() {
        return styleTracker.getHighestRank();
    }

    @Override
    public FlowStateTracker.FlowState getFlowState() {
        return flowTracker.getCurrentState();
    }

    @Override
    public float getVirtuosoProgress() {
        return flowTracker.getVirtuosoProgress();
    }

    @Override
    public float getStaleRisk() {
        return flowTracker.getStaleRisk();
    }

    @Override
    public int getUniqueActionCount() {
        return comboTracker.getUniqueActionCount();
    }

    @Override
    public ActionResult registerAction(ActionType action, float damage) {
        int previousCombo = comboTracker.getCurrentCombo();
        StyleRank previousRank = styleTracker.getCurrentRank();

        // Process flow state BEFORE calculating points
        FlowStateTracker.FlowResult flowResult = flowTracker.processAction(action);

        // Increment combo and track variety
        comboTracker.incrementCombo(action);

        // Calculate style gain with multipliers
        float comboMultiplier = calculateComboMultiplier();
        float varietyMultiplier = comboTracker.getVarietyMultiplier();
        float rankMultiplier = styleTracker.getRankMultiplier();
        float flowMultiplier = flowResult.styleMultiplier();

        int basePoints = (int) (action.getBasePoints() * comboMultiplier * varietyMultiplier * rankMultiplier);
        int styleGain = (int) (action.getStylePoints() * varietyMultiplier * flowMultiplier);

        // Add style
        boolean rankUp = styleTracker.addStyle(styleGain);

        // Track combat stats
        statsTracker.recordHit(damage);

        // Dispatch events
        int newCombo = comboTracker.getCurrentCombo();
        if (newCombo > previousCombo) {
            dispatcher.dispatch(new ComboEvent.ComboIncreased(
                playerId, questId, System.currentTimeMillis(),
                previousCombo, newCombo
            ));
        }

        if (rankUp) {
            dispatcher.dispatch(ComboEvent.RankChanged.promotion(
                playerId, questId, previousRank, styleTracker.getCurrentRank(),
                styleTracker.getStyleScore()
            ));
        }

        // Check flow state change
        if (flowResult.stateChanged()) {
            dispatcher.dispatch(new ComboEvent.FlowStateChanged(
                playerId, questId, System.currentTimeMillis(),
                null, // Previous state not tracked - could add if needed
                flowResult.state()
            ));
        }

        // Check milestones
        checkAndDispatchMilestones();

        // Dispatch special action if high value
        if (action.getStylePoints() >= 100) {
            dispatcher.dispatch(new ComboEvent.SpecialAction(
                playerId, questId, System.currentTimeMillis(),
                action, basePoints, styleGain, newCombo
            ));
        }

        // Create announcement if significant
        ActionAnnouncement announcement = null;
        if (action.getStylePoints() >= 100 || rankUp) {
            announcement = new ActionAnnouncement(
                action, styleGain, rankUp ? styleTracker.getCurrentRank() : null,
                System.currentTimeMillis()
            );
            addAnnouncement(announcement);
        }

        return new ActionResult(basePoints, styleGain, newCombo, styleTracker.getCurrentRank(), announcement);
    }

    @Override
    public ActionResult registerKill(boolean wasQuick, float overkillDamage) {
        int recentKills = statsTracker.recordKill();

        // Determine kill type
        ActionType killType;
        if (recentKills >= 3) {
            killType = ActionType.MULTI_KILL;
        } else if (wasQuick) {
            killType = ActionType.QUICK_KILL;
        } else if (overkillDamage > 10) {
            killType = ActionType.OVERKILL;
        } else {
            killType = ActionType.LIGHT_ATTACK;
        }

        return registerAction(killType, overkillDamage);
    }

    @Override
    public void onDamageTaken(float damage) {
        StyleRank previousRank = styleTracker.getCurrentRank();
        // Track damage taken
        statsTracker.recordDamageTaken(damage);

        // Check if in grace period
        long now = System.currentTimeMillis();
        boolean inGracePeriod = (now - waveStartTime) < WAVE_GRACE_PERIOD_MS;

        // Apply penalties
        int comboLost = inGracePeriod
            ? comboTracker.applyReducedDamagePenalty()
            : comboTracker.applyDamagePenalty();

        boolean rankChanged = styleTracker.applyDamagePenalty(damage, inGracePeriod);

        // Dispatch combo break event if significant
        if (comboLost >= 3) {
            dispatcher.dispatch(new ComboEvent.ComboBreak(
                playerId, questId, System.currentTimeMillis(),
                comboLost, ComboEvent.ComboBreak.BreakReason.DAMAGE_TAKEN, damage
            ));
        }

        // Dispatch rank demotion event
        if (rankChanged && styleTracker.wasDemoted()) {
            dispatcher.dispatch(ComboEvent.RankChanged.demotion(
                playerId, questId, previousRank, styleTracker.getCurrentRank(),
                styleTracker.getStyleScore()
            ));
        }
    }

    @Override
    public void startNewWave() {
        waveStartTime = System.currentTimeMillis();
        statsTracker.startNewWave();
    }

    @Override
    public void addBonusPoints(int points) {
        StyleRank previousRank = styleTracker.getCurrentRank();
        boolean rankUp = styleTracker.addBonusPoints(points);

        if (rankUp) {
            dispatcher.dispatch(ComboEvent.RankChanged.promotion(
                playerId, questId, previousRank, styleTracker.getCurrentRank(),
                styleTracker.getStyleScore()
            ));
        }
    }

    @Override
    public void tick() {
        // Check combo timeout
        if (comboTracker.isTimedOut()) {
            int lostCombo = comboTracker.breakCombo();
            flowTracker.onComboEnd();

            if (lostCombo > 0) {
                dispatcher.dispatch(new ComboEvent.ComboBreak(
                    playerId, questId, System.currentTimeMillis(),
                    lostCombo, ComboEvent.ComboBreak.BreakReason.TIMEOUT, 0
                ));
            }
        }

        // Process style decay
        styleTracker.processDecay();

        // Clean old announcements
        long now = System.currentTimeMillis();
        recentAnnouncements.removeIf(a -> now - a.timestamp() > ANNOUNCEMENT_LIFETIME_MS);
    }

    @Override
    public int getFinalScore() {
        float baseScore = styleTracker.getTotalStyleEarned();
        float comboBonus = comboTracker.getMaxCombo() * 10;
        float rankBonus = styleTracker.getHighestRank().ordinal() * 500;
        float perfectionBonus = statsTracker.getPerfectionBonus();

        return (int) (baseScore + comboBonus + rankBonus + perfectionBonus);
    }

    @Override
    public CombatStats getStats() {
        return statsTracker.snapshot();
    }

    // === Additional Methods ===

    /**
     * Register a perfect dodge.
     */
    public ActionResult registerPerfectDodge() {
        statsTracker.recordPerfectDodge();
        return registerAction(ActionType.PERFECT_DODGE, 0);
    }

    /**
     * Register a parry.
     */
    public ActionResult registerParry() {
        statsTracker.recordParry();
        return registerAction(ActionType.PARRY, 0);
    }

    /**
     * Register a counter attack.
     */
    public ActionResult registerCounterAttack(float damage) {
        statsTracker.recordCounterAttack();
        return registerAction(ActionType.COUNTER_ATTACK, damage);
    }

    /**
     * Check for and potentially award no-damage wave bonus.
     */
    @Nullable
    public ActionResult checkNoDamageWave() {
        if (statsTracker.isNoDamageWave()) {
            return registerAction(ActionType.NO_DAMAGE_WAVE, 0);
        }
        return null;
    }

    /**
     * Get recent announcements for UI display.
     */
    public List<ActionAnnouncement> getRecentAnnouncements() {
        return recentAnnouncements;
    }

    /**
     * Get total hits (for stats).
     */
    public int getTotalHits() {
        return statsTracker.getTotalHits();
    }

    /**
     * Get total kills (for stats).
     */
    public int getTotalKills() {
        return statsTracker.getTotalKills();
    }

    /**
     * Get total damage dealt (for stats).
     */
    public float getTotalDamage() {
        return statsTracker.getTotalDamage();
    }

    /**
     * Get perfect dodges count (for stats).
     */
    public int getPerfectDodges() {
        return statsTracker.getPerfectDodges();
    }

    /**
     * Get parries count (for stats).
     */
    public int getParries() {
        return statsTracker.getParries();
    }

    /**
     * Get counter attacks count (for stats).
     */
    public int getCounterAttacks() {
        return statsTracker.getCounterAttacks();
    }

    // === Private Helpers ===

    private float calculateComboMultiplier() {
        double comboIncrement = 0.02;
        double maxMultiplier = 5.0;

        if (questId != null) {
            EnduranceConfigManager config = EnduranceConfigManager.INSTANCE;
            comboIncrement = config.getComboMultiplierIncrement(questId);
            maxMultiplier = config.getComboMaxMultiplier(questId);
        }

        return (float) Math.min(1.0 + (comboTracker.getCurrentCombo() * comboIncrement), maxMultiplier);
    }

    private void checkAndDispatchMilestones() {
        int[] milestones = {5, 10, 25, 50, 100};
        for (int milestone : milestones) {
            if (comboTracker.hasReachedMilestone(milestone)) {
                ActionType milestoneType = getMilestoneActionType(milestone);
                int styleEarned = (int) (milestoneType.getStylePoints() * comboTracker.getVarietyMultiplier());

                // Add milestone style bonus
                styleTracker.addBonusPoints(styleEarned);

                // Dispatch event
                dispatcher.dispatch(new ComboEvent.MilestoneReached(
                    playerId, questId, System.currentTimeMillis(),
                    milestoneType, milestone, styleEarned
                ));

                // Add announcement
                addAnnouncement(new ActionAnnouncement(
                    milestoneType, styleEarned, null, System.currentTimeMillis()
                ));
            }
        }
    }

    private ActionType getMilestoneActionType(int combo) {
        return switch (combo) {
            case 5 -> ActionType.COMBO_5;
            case 10 -> ActionType.COMBO_10;
            case 25 -> ActionType.COMBO_25;
            case 50 -> ActionType.COMBO_50;
            case 100 -> ActionType.COMBO_100;
            default -> ActionType.COMBO_5;
        };
    }

    private void addAnnouncement(ActionAnnouncement announcement) {
        recentAnnouncements.add(announcement);
        while (recentAnnouncements.size() > MAX_ANNOUNCEMENTS) {
            recentAnnouncements.remove(0);
        }
    }

    private static SessionConfig createDefaultConfig(UUID questId) {
        EnduranceConfigManager config = EnduranceConfigManager.INSTANCE;

        long comboTimeoutMs = 3000L;
        long styleDecayIntervalMs = 1000L;
        int styleDecayRate = 50;

        if (questId != null) {
            comboTimeoutMs = config.getComboTimeoutTicks(questId) * 50L;
            styleDecayIntervalMs = config.getStyleDecayDelayTicks(questId) * 50L;
            styleDecayRate = (int) config.getStyleDecayRate(questId);
        }

        return new SessionConfig(
            comboTimeoutMs,
            styleDecayIntervalMs,
            styleDecayRate,
            new StyleRankResolver()
        );
    }

    /**
     * Configuration for session creation.
     */
    public record SessionConfig(
        long comboTimeoutMs,
        long styleDecayIntervalMs,
        int styleDecayRate,
        StyleRankResolver rankResolver
    ) {}
}
