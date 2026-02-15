package com.devmod.combat.bridge;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Bridge interface decoupling combat-package code from endurance-module classes.
 * <p>
 * When the endurance module is loaded it registers the real implementation via
 * {@link #setInstance}. Without endurance every method is a safe no-op
 * (the default implementation).
 * <p>
 * Thread-safe: the singleton is stored in an {@link AtomicReference}.
 *
 * @see CombatVisualsBridge for the analogous client-side visuals bridge
 */
public interface CombatEnduranceBridge {

    // -------- singleton holder --------

    // Use Holder class to break initialization cycle (ErrorProne ClassInitializationDeadlock).
    final class Holder {
        private static final AtomicReference<CombatEnduranceBridge> INSTANCE =
                new AtomicReference<>(new NoOp());
        private Holder() {}
    }

    static CombatEnduranceBridge get() {
        return Holder.INSTANCE.get();
    }

    static void setInstance(CombatEnduranceBridge impl) {
        Holder.INSTANCE.set(impl);
    }

    // -------- Quest session checks (ExecutionSystem) --------

    /**
     * Returns {@code true} if the player has an active endurance quest session.
     */
    default boolean hasActiveQuestSession(Player player) {
        return false;
    }

    /**
     * Returns the quest-id UUID stored in a mob's persistent data under the
     * endurance quest-id tag, or {@code null} when the tag is absent.
     */
    @Nullable
    default UUID getQuestIdFromMobData(net.minecraft.nbt.CompoundTag data) {
        return null;
    }

    /**
     * Returns the NBT tag key used by the endurance module to stamp
     * quest-owned mobs. Combat code uses this to check whether a tag
     * is present without importing {@code EnduranceTags} directly.
     */
    default String getQuestIdTagKey() {
        return "endurance_quest_id";
    }

    /**
     * Returns the quest-id of the player's active session, or empty.
     */
    default Optional<UUID> getActiveQuestId(Player player) {
        return Optional.empty();
    }

    // -------- Combo system (DamageHandler, ShieldBlockHandler, ExecutionSystem) --------

    /**
     * Registers a combat action with the combo system.
     *
     * @param playerId   the player's UUID
     * @param actionName the canonical action name (e.g. "SLASH", "PARRY", "EXECUTION")
     * @param damage     damage dealt (0 for non-damage actions)
     * @return style points earned, or 0 if no active session
     */
    default int registerComboAction(UUID playerId, String actionName, float damage) {
        return 0;
    }

    /**
     * Returns {@code true} when the combo / style subsystem is available.
     */
    default boolean isComboSystemAvailable() {
        return false;
    }

    // -------- Shield parry (ShieldBlockHandler) --------

    /**
     * Processes a successful parry: registers the action with the combo system
     * and syncs the combat-flow HUD to the client.
     *
     * @param player the player who parried
     */
    default void onParry(ServerPlayer player) {}

    // -------- Momentum (ExecutionSystem) --------

    /**
     * Notifies the momentum system that the player scored a kill.
     */
    default void onPlayerKill(UUID playerId) {}

    // -------- Perk discovery (ExecutionSystem) --------

    /**
     * Records an execution for hidden perk discovery tracking.
     */
    default void recordExecution(ServerPlayer player) {}

    // -------- Classify attack type (DamageHandler) --------

    /**
     * Classifies an attack and registers it with the combo system.
     * This encapsulates the weapon-type classification logic that was
     * previously inlined in DamageHandler.
     *
     * @param attackerId  the attacker's UUID
     * @param weapon      the weapon ItemStack
     * @param isRanged    whether this was a ranged attack
     * @param source      the damage source
     * @param damageDealt the damage dealt
     */
    default void classifyAndRegisterAttack(UUID attackerId,
                                            net.minecraft.world.item.ItemStack weapon,
                                            boolean isRanged,
                                            net.minecraft.world.damagesource.DamageSource source,
                                            float damageDealt) {}

    // -------- Quest lifecycle cleanup (ExecutionSystem) --------

    /**
     * Called when a quest ends to clean up execution state.
     * The real implementation delegates to {@code ExecutionSystem.INSTANCE.onPlayerLeave()}.
     *
     * <p>Note: quest-end cleanup is also handled by {@code ExecutionCleanupListener}
     * on the QuestEventBus; this method is provided for programmatic use.</p>
     */
    default void onQuestEnded(UUID playerId) {}

    // -------- Execution state queries (ExecutionSystem) --------

    /**
     * Returns {@code true} if the player is currently performing an execution.
     */
    default boolean isExecuting(ServerPlayer player) {
        return false;
    }

    /**
     * Interrupts an execution in progress and returns whether one was interrupted.
     */
    default boolean interruptExecution(ServerPlayer player) {
        return false;
    }

    /**
     * Returns the vulnerability damage multiplier for a player whose execution
     * was recently interrupted.  Returns {@code 1.0f} when no vulnerability applies.
     */
    default float getVulnerabilityMultiplier(ServerPlayer player) {
        return 1.0f;
    }

    /**
     * Ticks the execution system (called once per server tick).
     */
    default void tickExecutionSystem() {}

    /**
     * Ticks execution state for a single player (per-player per-tick).
     */
    default void tickExecutionPlayer(ServerPlayer player) {}

    /**
     * Cleans up execution state when a player leaves.
     */
    default void onPlayerLeave(UUID playerId) {}

    // -------- Signature weapon tracking (SoulImprintManager) --------

    /**
     * Records damage dealt by the player for signature weapon imprint tracking.
     */
    default void recordSoulDamage(ServerPlayer player, float damage) {}

    /**
     * Records a headshot for signature weapon imprint tracking.
     */
    default void recordSoulHeadshot(ServerPlayer player) {}

    /**
     * Records a critical hit for signature weapon imprint tracking.
     */
    default void recordSoulCriticalHit(ServerPlayer player) {}

    /**
     * Records a kill for signature weapon imprint tracking.
     */
    default void recordSoulKill(ServerPlayer player, LivingEntity target, boolean isBoss) {}

    /**
     * Records an execute kill for signature weapon imprint tracking.
     */
    default void recordSoulExecuteKill(ServerPlayer player) {}

    /**
     * Records an SSS-rank wave completion for signature weapon tracking.
     */
    default void recordSoulSSSWave(ServerPlayer player) {}

    /**
     * Records a no-hit wave completion for signature weapon tracking.
     */
    default void recordSoulNoHitWave(ServerPlayer player) {}

    // -------- Body part detection (HitHelper) --------

    /**
     * Raycasts from attacker to target and returns the body part name hit.
     *
     * @return the body part name (e.g. "HEAD", "BODY", "ARMS", "LEGS")
     */
    default String rayTraceBodyPart(LivingEntity attacker, LivingEntity target) {
        return "BODY";
    }

    /**
     * Determines the body part hit based on the Y coordinate of impact.
     *
     * @return the body part name (e.g. "HEAD", "BODY", "ARMS", "LEGS")
     */
    default String getBodyPartFromHitY(LivingEntity target, double hitY) {
        return "BODY";
    }

    // -------- no-op implementation --------

    final class NoOp implements CombatEnduranceBridge {}
}
