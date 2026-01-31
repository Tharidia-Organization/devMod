package com.devmod.endurance;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import com.devmod.endurance.lifecycle.QuestContext;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestEnded;
import com.devmod.endurance.lifecycle.QuestLifecycleEvent.QuestStarted;
import com.devmod.endurance.lifecycle.QuestLifecycleListener;
public class ComebackSystem implements QuestLifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComebackSystem.class);

    public static final ComebackSystem INSTANCE = new ComebackSystem();

    // Configuration
    private static final float TRIGGER_HEALTH_PERCENT = 0.20f;  // 20% health
    private static final int BUFF_DURATION_TICKS = 200;         // 10 seconds
    private static final long COOLDOWN_MS = 60000;              // 60 second cooldown
    private static final int RESISTANCE_AMPLIFIER = 1;          // Resistance II
    private static final int STRENGTH_AMPLIFIER = 1;            // Strength II
    private static final int SPEED_AMPLIFIER = 0;               // Speed I
    private static final int REGENERATION_AMPLIFIER = 1;        // Regen II

    // Track player cooldowns
    private final Map<UUID, Long> lastTriggerTime = new ConcurrentHashMap<>();

    // Track active phoenix states for combo integration
    private final Map<UUID, PhoenixState> activeStates = new ConcurrentHashMap<>();

    private ComebackSystem() {}

    /**
     * State tracking for an active Phoenix buff.
     */
    public static class PhoenixState {
        private final long startTime;
        private int killsDuringPhoenix = 0;
        private float damageDealtDuringPhoenix = 0f;
        private boolean survivedUntilEnd = false;

        public PhoenixState() {
            this.startTime = System.currentTimeMillis();
        }

        public void recordKill() {
            killsDuringPhoenix++;
        }

        public void recordDamage(float damage) {
            damageDealtDuringPhoenix += damage;
        }

        public void markSurvived() {
            survivedUntilEnd = true;
        }

        public int getKillsDuringPhoenix() { return killsDuringPhoenix; }
        public float getDamageDealtDuringPhoenix() { return damageDealtDuringPhoenix; }
        public boolean didSurvive() { return survivedUntilEnd; }
        public long getDurationMs() { return System.currentTimeMillis() - startTime; }
    }

    /**
     * Check if comeback should trigger based on player's current health.
     * Called when player takes damage during an Endurance Quest.
     *
     * @param player The player who took damage
     * @param newHealthPercent Health percentage after damage (0.0 - 1.0)
     * @param previousHealthPercent Health percentage before damage
     * @return true if Phoenix Rising was triggered
     */
    public boolean checkAndTrigger(ServerPlayer player, float newHealthPercent, float previousHealthPercent) {
        UUID playerId = player.getUUID();

        // Already active?
        if (activeStates.containsKey(playerId)) {
            return false;
        }

        // Check if we crossed the threshold
        if (newHealthPercent > TRIGGER_HEALTH_PERCENT || previousHealthPercent <= TRIGGER_HEALTH_PERCENT) {
            return false;
        }

        // Check cooldown
        Long lastTrigger = lastTriggerTime.get(playerId);
        if (lastTrigger != null && System.currentTimeMillis() - lastTrigger < COOLDOWN_MS) {
            return false;
        }

        // Trigger Phoenix Rising!
        triggerPhoenixRising(player);
        return true;
    }

    /**
     * Trigger the Phoenix Rising buff on a player.
     */
    private void triggerPhoenixRising(ServerPlayer player) {
        UUID playerId = player.getUUID();

        // Record trigger time for cooldown
        lastTriggerTime.put(playerId, System.currentTimeMillis());

        // Create state tracker
        PhoenixState state = new PhoenixState();
        activeStates.put(playerId, state);

        // Apply buffs
        applyPhoenixBuffs(player);

        // Visual and audio feedback
        playPhoenixEffects(player);

        // Notify combo system for style bonus
        notifyComboSystem(player);

        LOGGER.info("[ComebackSystem] Phoenix Rising triggered for {} at {}% health",
            player.getName().getString(), (int)(player.getHealth() / player.getMaxHealth() * 100));

        // Schedule state cleanup
        scheduleStateCleanup(playerId, BUFF_DURATION_TICKS * 50L); // ticks to ms
    }

    /**
     * Apply the Phoenix Rising buffs to the player.
     */
    private void applyPhoenixBuffs(ServerPlayer player) {
        // Resistance - survive incoming damage
        player.addEffect(new MobEffectInstance(
            Objects.requireNonNull(MobEffects.DAMAGE_RESISTANCE, "DAMAGE_RESISTANCE effect"),
            BUFF_DURATION_TICKS,
            RESISTANCE_AMPLIFIER,
            false, true, true));

        // Strength - deal more damage to fight back
        player.addEffect(new MobEffectInstance(
            Objects.requireNonNull(MobEffects.DAMAGE_BOOST, "DAMAGE_BOOST effect"),
            BUFF_DURATION_TICKS,
            STRENGTH_AMPLIFIER,
            false, true, true));

        // Speed - evade and reposition
        player.addEffect(new MobEffectInstance(
            Objects.requireNonNull(MobEffects.MOVEMENT_SPEED, "MOVEMENT_SPEED effect"),
            BUFF_DURATION_TICKS,
            SPEED_AMPLIFIER,
            false, true, true));

        // Regeneration - recover health
        player.addEffect(new MobEffectInstance(
            Objects.requireNonNull(MobEffects.REGENERATION, "REGENERATION effect"),
            BUFF_DURATION_TICKS,
            REGENERATION_AMPLIFIER,
            false, true, true));
    }

    /**
     * Play visual and audio effects for Phoenix Rising.
     */
    private void playPhoenixEffects(ServerPlayer player) {
        // Epic sound
        player.level().playSound(null,
            player.getX(), player.getY(), player.getZ(),
            Objects.requireNonNull(SoundEvents.TOTEM_USE, "TOTEM_USE sound"),
            SoundSource.PLAYERS,
            1.0f, 1.0f);

        // Particle effects handled via client-side ComebackEffectHandler
    }

    /**
     * Notify the combo system that Phoenix Rising was triggered.
     * This can grant bonus style points.
     */
    private void notifyComboSystem(ServerPlayer player) {
        // Get active combo session if any
        if (com.devmod.endurance.combat.ComboSystemFacade.isInitialized()) {
            com.devmod.endurance.combat.ComboSystemFacade.get()
                .getSession(player.getUUID())
                .ifPresent(session -> session.addBonusPoints(500));
        }
    }

    /**
     * Schedule cleanup of the Phoenix state after buff expires.
     */
    private void scheduleStateCleanup(UUID playerId, long delayMs) {
        // Use a simple delayed task
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                PhoenixState state = activeStates.remove(playerId);
                if (state != null) {
                    state.markSurvived();
                    LOGGER.info("[ComebackSystem] Phoenix Rising ended for player {} - Kills: {}, Damage: {}",
                        playerId, state.getKillsDuringPhoenix(), state.getDamageDealtDuringPhoenix());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Record a kill made during Phoenix Rising (for bonus rewards).
     */
    public void recordKill(UUID playerId) {
        PhoenixState state = activeStates.get(playerId);
        if (state != null) {
            state.recordKill();
        }
    }

    /**
     * Record damage dealt during Phoenix Rising.
     */
    public void recordDamage(UUID playerId, float damage) {
        PhoenixState state = activeStates.get(playerId);
        if (state != null) {
            state.recordDamage(damage);
        }
    }

    /**
     * Check if player currently has Phoenix Rising active.
     */
    public boolean isPhoenixActive(UUID playerId) {
        return activeStates.containsKey(playerId);
    }

    /**
     * Get remaining cooldown in milliseconds.
     */
    public long getRemainingCooldown(UUID playerId) {
        Long lastTrigger = lastTriggerTime.get(playerId);
        if (lastTrigger == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastTrigger;
        return Math.max(0, COOLDOWN_MS - elapsed);
    }

    /**
     * Called when player dies during an Endurance Quest.
     * Cleans up any active Phoenix state.
     */
    public void onPlayerDeath(UUID playerId) {
        PhoenixState state = activeStates.remove(playerId);
        if (state != null) {
            LOGGER.info("[ComebackSystem] Player {} died during Phoenix Rising - Kills: {}, Damage: {}",
                playerId, state.getKillsDuringPhoenix(), state.getDamageDealtDuringPhoenix());
        }
    }

    /**
     * Called when a quest ends. Cleans up state and potentially awards bonus.
     */
    public PhoenixState onQuestEnd(UUID playerId) {
        return activeStates.remove(playerId);
    }

    /**
     * Reset cooldown for a player (e.g., at start of new quest).
     */
    public void resetCooldown(UUID playerId) {
        lastTriggerTime.remove(playerId);
    }

    /**
     * Get Phoenix Rising statistics for end-of-quest rewards.
     */
    public PhoenixState getState(UUID playerId) {
        return activeStates.get(playerId);
    }

    // ========== QuestLifecycleListener Implementation ==========

    @Override
    public void onQuestStarted(QuestStarted event) {
        QuestContext ctx = event.context();
        UUID playerId = ctx.playerId();

        // Reset cooldown for fresh quest
        resetCooldown(playerId);
        LOGGER.debug("[ComebackSystem] Reset cooldown for player {} via event bus", playerId);
    }

    @Override
    public void onQuestEnded(QuestEnded event) {
        QuestContext ctx = event.context();
        UUID playerId = ctx.playerId();

        // Cleanup state
        onQuestEnd(playerId);
        LOGGER.debug("[ComebackSystem] Cleaned up state for player {} via event bus", playerId);
    }

    @Override
    public int getPriority() {
        return 50; // Low priority
    }

    @Override
    public String getListenerName() {
        return "ComebackSystem";
    }
}
