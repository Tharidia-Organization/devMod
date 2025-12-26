package com.devmod.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import com.devmod.arena.policy.ArenaPolicy.ExecutionOverrides;
import com.devmod.config.GameMechanicsConfig;
import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.MomentumTracker;

/**
 * Execution System - Finisher Mechanics.
 *
 * Creates power fantasy moments with risk/reward:
 * - Execute low HP enemies (<15%) for massive style points
 * - During execution: 2 sec i-frames (invincibility)
 * - Rewards: +200 style, 5% HP regen, 30% item drop chance boost
 * - Risk: If interrupted before completion = 2x damage vulnerability
 * - 3 sec cooldown between executions
 *
 * Design Philosophy:
 * Executions are high-risk, high-reward "finishing moves" that
 * create memorable combat moments. They reward aggressive play
 * (getting enemies low) but punish greed (trying to execute
 * while surrounded by other threats).
 */
public class ExecutionSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionSystem.class);

    public static final ExecutionSystem INSTANCE = new ExecutionSystem();

    // Configuration - now read from GameMechanicsConfig with optional per-instance overrides
    // These methods allow runtime configuration changes and per-arena customization

    private ExecutionOverrides currentOverrides = null;

    /**
     * Set per-instance overrides (called when entering an arena with custom config).
     */
    public void setOverrides(ExecutionOverrides overrides) {
        this.currentOverrides = overrides;
    }

    /**
     * Clear per-instance overrides (called when leaving an arena).
     */
    public void clearOverrides() {
        this.currentOverrides = null;
    }

    private float getExecutionThreshold() {
        if (currentOverrides != null && currentOverrides.hpThreshold() != null) {
            return currentOverrides.hpThreshold().floatValue();
        }
        return GameMechanicsConfig.EXECUTION_HP_THRESHOLD.get().floatValue();
    }

    private int getExecutionDurationTicks() {
        if (currentOverrides != null && currentOverrides.durationTicks() != null) {
            return currentOverrides.durationTicks();
        }
        return GameMechanicsConfig.EXECUTION_DURATION_TICKS.get();
    }

    private int getCooldownTicks() {
        if (currentOverrides != null && currentOverrides.cooldownTicks() != null) {
            return currentOverrides.cooldownTicks();
        }
        return GameMechanicsConfig.EXECUTION_COOLDOWN_TICKS.get();
    }

    private int getStyleReward() {
        if (currentOverrides != null && currentOverrides.styleReward() != null) {
            return currentOverrides.styleReward();
        }
        return GameMechanicsConfig.EXECUTION_STYLE_REWARD.get();
    }

    private float getHpRegenPercent() {
        if (currentOverrides != null && currentOverrides.hpRegenPercent() != null) {
            return currentOverrides.hpRegenPercent().floatValue();
        }
        return GameMechanicsConfig.EXECUTION_HP_REGEN_PERCENT.get().floatValue();
    }

    private float getDropBoost() {
        if (currentOverrides != null && currentOverrides.dropBoost() != null) {
            return currentOverrides.dropBoost().floatValue();
        }
        return GameMechanicsConfig.EXECUTION_DROP_BOOST.get().floatValue();
    }

    private float getInterruptVulnerability() {
        if (currentOverrides != null && currentOverrides.interruptVulnerability() != null) {
            return currentOverrides.interruptVulnerability().floatValue();
        }
        return GameMechanicsConfig.EXECUTION_INTERRUPT_VULNERABILITY.get().floatValue();
    }

    private double getExecutionRange() {
        if (currentOverrides != null && currentOverrides.range() != null) {
            return currentOverrides.range();
        }
        return GameMechanicsConfig.EXECUTION_RANGE.get();
    }

    // Active execution state
    private final Map<UUID, ExecutionState> activeExecutions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    /**
     * State of an ongoing execution.
     */
    public static class ExecutionState {
        public final UUID playerId;
        public final UUID targetId;
        public final long startTime;
        public final int durationTicks;
        public int ticksRemaining;
        public boolean completed = false;
        public boolean interrupted = false;

        public ExecutionState(UUID playerId, UUID targetId, int durationTicks) {
            this.playerId = playerId;
            this.targetId = targetId;
            this.startTime = System.currentTimeMillis();
            this.durationTicks = durationTicks;
            this.ticksRemaining = durationTicks;
        }

        public float getProgress() {
            return 1.0f - ((float) ticksRemaining / durationTicks);
        }
    }

    /**
     * Result of an execution attempt.
     */
    public record ExecutionResult(
        boolean success,
        String message,
        int styleGained,
        float hpRestored,
        boolean wasInterrupted
    ) {
        public static ExecutionResult fail(String message) {
            return new ExecutionResult(false, message, 0, 0, false);
        }

        public static ExecutionResult success(int style, float hp) {
            return new ExecutionResult(true, "EXECUTED!", style, hp, false);
        }

        public static ExecutionResult interrupted() {
            return new ExecutionResult(false, "INTERRUPTED!", 0, 0, true);
        }
    }

    /**
     * Check if an entity can be executed (low HP, is a mob, etc).
     */
    public boolean canBeExecuted(LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        if (!(target instanceof Mob)) return false;

        float healthPercent = target.getHealth() / target.getMaxHealth();
        return healthPercent <= getExecutionThreshold();
    }

    /**
     * Check if a player can perform an execution right now.
     */
    public boolean canExecute(ServerPlayer player) {
        UUID playerId = player.getUUID();

        // Check if already executing
        if (activeExecutions.containsKey(playerId)) {
            return false;
        }

        // Check cooldown
        Long cooldownEnd = cooldowns.get(playerId);
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
            return false;
        }

        // Check if in quest
        return EnduranceQuestManager.INSTANCE.getActiveSession(player).isPresent();
    }

    /**
     * Get the closest executable target within range.
     */
    public Optional<Mob> getExecutableTarget(ServerPlayer player) {
        if (!canExecute(player)) return Optional.empty();

        return player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(getExecutionRange()))
            .stream()
            .filter(this::canBeExecuted)
            .filter(mob -> isQuestMob(mob, player))
            .min(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
    }

    /**
     * Check if mob is from player's active quest.
     */
    private boolean isQuestMob(Mob mob, ServerPlayer player) {
        CompoundTag data = mob.getPersistentData();
        if (!data.contains("endurance_quest_id")) return false;

        return EnduranceQuestManager.INSTANCE.getActiveSession(player)
            .map(session -> {
                UUID mobQuestId = data.getUUID("endurance_quest_id");
                return mobQuestId.equals(session.getQuest().getQuestId());
            })
            .orElse(false);
    }

    /**
     * Start an execution attempt.
     */
    public ExecutionResult startExecution(ServerPlayer player, Mob target) {
        UUID playerId = player.getUUID();

        // Validate
        if (!canExecute(player)) {
            return ExecutionResult.fail("Not ready to execute!");
        }

        if (!canBeExecuted(target)) {
            return ExecutionResult.fail("Target cannot be executed!");
        }

        // Start execution
        int durationTicks = getExecutionDurationTicks();
        ExecutionState state = new ExecutionState(playerId, target.getUUID(), durationTicks);
        activeExecutions.put(playerId, state);

        // Apply invincibility
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 4, false, false));

        // Lock target in place
        target.setNoAi(true);

        // Visual/audio feedback
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0f, 1.5f);

            // Initial particles
            for (int i = 0; i < 20; i++) {
                double x = target.getX() + (level.random.nextDouble() - 0.5) * 2;
                double y = target.getY() + level.random.nextDouble() * target.getBbHeight();
                double z = target.getZ() + (level.random.nextDouble() - 0.5) * 2;
                level.sendParticles(ParticleTypes.CRIT, x, y, z, 1, 0, 0, 0, 0.1);
            }
        }

        LOGGER.debug("[Execution] {} started executing {}", player.getName().getString(), target.getName().getString());

        return new ExecutionResult(true, "EXECUTING...", 0, 0, false);
    }

    /**
     * Tick execution progress. Called each server tick.
     */
    public void tick() {
        Iterator<Map.Entry<UUID, ExecutionState>> iterator = activeExecutions.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, ExecutionState> entry = iterator.next();
            ExecutionState state = entry.getValue();

            state.ticksRemaining--;

            // Find player and target
            // This is called from server tick, need to find the entities
            // We'll handle this in the event handler where we have access to the server

            if (state.ticksRemaining <= 0) {
                state.completed = true;
            }
        }
    }

    /**
     * Process execution tick for a specific player.
     * Call this from the server tick handler.
     */
    public void tickPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ExecutionState state = activeExecutions.get(playerId);

        if (state == null) return;

        state.ticksRemaining--;

        // Get target
        if (player.level() instanceof ServerLevel level) {
            Mob target = (Mob) level.getEntity(state.targetId);

            if (target == null || !target.isAlive()) {
                // Target died or despawned - count as success
                completeExecution(player, null, state);
                return;
            }

            // Tick visual effects
            if (state.ticksRemaining % 5 == 0) {
                for (int i = 0; i < 5; i++) {
                    double x = target.getX() + (level.random.nextDouble() - 0.5) * 1.5;
                    double y = target.getY() + level.random.nextDouble() * target.getBbHeight();
                    double z = target.getZ() + (level.random.nextDouble() - 0.5) * 1.5;
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 1, 0, 0, 0, 0);
                }
            }

            // Check completion
            if (state.ticksRemaining <= 0) {
                completeExecution(player, target, state);
            }
        }
    }

    /**
     * Complete an execution successfully.
     */
    private void completeExecution(ServerPlayer player, Mob target, ExecutionState state) {
        UUID playerId = player.getUUID();
        activeExecutions.remove(playerId);

        // Set cooldown
        cooldowns.put(playerId, System.currentTimeMillis() + (getCooldownTicks() * 50L));

        // Calculate rewards
        int styleGain = getStyleReward();
        float maxHp = player.getMaxHealth();
        float hpRegen = maxHp * getHpRegenPercent();

        // Apply HP regen
        player.heal(hpRegen);

        // Apply style reward via combo session if active
        ComboSystem.INSTANCE.getSession(playerId).ifPresent(session ->
            session.registerAction(ComboSystem.ActionType.EXECUTION, 0)
        );

        // Track execution for hidden perk discoveries
        com.devmod.endurance.perk.PerkSynergyWeb.INSTANCE.recordExecution(player);

        // Apply momentum boost
        MomentumTracker.INSTANCE.onPlayerKill(playerId);

        // Kill target with boosted drops
        if (target != null && target.isAlive()) {
            // Re-enable AI first
            target.setNoAi(false);

            // Mark for boosted drops
            target.getPersistentData().putBoolean("execution_kill", true);
            target.getPersistentData().putFloat("execution_drop_boost", getDropBoost());

            // Kill instantly
            target.hurt(player.damageSources().playerAttack(player), Float.MAX_VALUE);
        }

        // Visual/audio feedback
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.5f);

            // Explosion of particles
            for (int i = 0; i < 50; i++) {
                double x = player.getX() + (level.random.nextDouble() - 0.5) * 3;
                double y = player.getY() + level.random.nextDouble() * 2;
                double z = player.getZ() + (level.random.nextDouble() - 0.5) * 3;
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 1, 0, 0.5, 0, 0.2);
            }
        }

        // Send feedback
        player.displayClientMessage(
            Component.literal("§6§lEXECUTED! §r§a+" + styleGain + " Style §r§c+" + String.format("%.1f", hpRegen) + " HP"),
            true
        );

        LOGGER.info("[Execution] {} completed execution: +{} style, +{} HP",
            player.getName().getString(), styleGain, hpRegen);

        state.completed = true;
    }

    /**
     * Interrupt an execution (called when player takes damage).
     */
    public ExecutionResult interruptExecution(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ExecutionState state = activeExecutions.remove(playerId);

        if (state == null) return null;

        // Remove invincibility
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);

        // Apply vulnerability debuff
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, true));

        // Mark player as vulnerable for damage calculation
        player.getPersistentData().putLong("execution_interrupted", System.currentTimeMillis() + 3000);
        player.getPersistentData().putFloat("execution_vulnerability", getInterruptVulnerability());

        // Restore target AI
        if (player.level() instanceof ServerLevel level) {
            Mob target = (Mob) level.getEntity(state.targetId);
            if (target != null) {
                target.setNoAi(false);
            }

            // Negative feedback
            level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
        }

        // Send feedback
        player.displayClientMessage(
            Component.literal("§c§lINTERRUPTED! §r§7Vulnerability: 2x damage for 3 sec"),
            true
        );

        LOGGER.info("[Execution] {} execution was interrupted!", player.getName().getString());

        state.interrupted = true;
        return ExecutionResult.interrupted();
    }

    /**
     * Check if player is currently executing.
     */
    public boolean isExecuting(ServerPlayer player) {
        return activeExecutions.containsKey(player.getUUID());
    }

    /**
     * Get current execution state for a player.
     */
    public Optional<ExecutionState> getExecutionState(UUID playerId) {
        return Optional.ofNullable(activeExecutions.get(playerId));
    }

    /**
     * Get remaining cooldown in milliseconds.
     */
    public long getRemainingCooldown(UUID playerId) {
        Long cooldownEnd = cooldowns.get(playerId);
        if (cooldownEnd == null) return 0;
        return Math.max(0, cooldownEnd - System.currentTimeMillis());
    }

    /**
     * Apply execution drop boost to loot.
     * Call this from loot modification events.
     */
    public static void applyDropBoost(Mob mob, List<ItemStack> drops) {
        CompoundTag data = mob.getPersistentData();
        if (!data.getBoolean("execution_kill")) return;

        float boost = data.getFloat("execution_drop_boost");
        if (boost <= 0) return;

        // Chance to duplicate each drop
        List<ItemStack> bonusDrops = new ArrayList<>();
        Random random = new Random();

        for (ItemStack drop : drops) {
            if (random.nextFloat() < boost) {
                bonusDrops.add(drop.copy());
            }
        }

        drops.addAll(bonusDrops);

        LOGGER.debug("[Execution] Applied drop boost: {} bonus items", bonusDrops.size());
    }

    /**
     * Check if player has execution vulnerability (was interrupted recently).
     */
    public static float getVulnerabilityMultiplier(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains("execution_interrupted")) return 1.0f;

        long vulnerableUntil = data.getLong("execution_interrupted");
        if (System.currentTimeMillis() > vulnerableUntil) {
            // Expired, clean up
            data.remove("execution_interrupted");
            data.remove("execution_vulnerability");
            return 1.0f;
        }

        return data.getFloat("execution_vulnerability");
    }

    /**
     * Clean up state for a player leaving.
     */
    public void onPlayerLeave(UUID playerId) {
        activeExecutions.remove(playerId);
        cooldowns.remove(playerId);
    }

    /**
     * Reset all state.
     */
    public void reset() {
        activeExecutions.clear();
        cooldowns.clear();
    }

    private ExecutionSystem() {}
}
