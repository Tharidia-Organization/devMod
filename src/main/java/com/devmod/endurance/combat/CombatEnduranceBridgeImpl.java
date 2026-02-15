package com.devmod.endurance.combat;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;

import com.devmod.combat.ExecutionSystem;
import com.devmod.combat.HitHelper;
import com.devmod.combat.bridge.CombatEnduranceBridge;
import com.devmod.combat.signature.SoulImprintManager;
import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceEventCombat;
import com.devmod.endurance.EnduranceEventHandler;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.EnduranceTags;
import com.devmod.endurance.MomentumTracker;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.perk.PerkSynergyWeb;

/**
 * Real implementation of {@link CombatEnduranceBridge} backed by
 * endurance-module singletons.
 * <p>
 * Registered during server startup in {@code ModLifecycleEvents}.
 */
public final class CombatEnduranceBridgeImpl implements CombatEnduranceBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(CombatEnduranceBridgeImpl.class);

    /** Singleton - cheap to create, no mutable state of its own. */
    public static final CombatEnduranceBridgeImpl INSTANCE = new CombatEnduranceBridgeImpl();

    private CombatEnduranceBridgeImpl() {}

    /**
     * Convenience: register this implementation with the bridge holder.
     */
    public static void register() {
        CombatEnduranceBridge.setInstance(INSTANCE);
        LOGGER.info("[CombatEnduranceBridge] Real implementation registered");
    }

    // -------- Quest session checks --------

    @Override
    public boolean hasActiveQuestSession(Player player) {
        return EnduranceQuestManager.INSTANCE.getActiveSession(player).isPresent();
    }

    @Nullable
    @Override
    public UUID getQuestIdFromMobData(CompoundTag data) {
        if (data == null || !data.contains(EnduranceTags.QUEST_ID)) {
            return null;
        }
        return data.getUUID(EnduranceTags.QUEST_ID);
    }

    @Override
    public String getQuestIdTagKey() {
        return EnduranceTags.QUEST_ID;
    }

    @Override
    public Optional<UUID> getActiveQuestId(Player player) {
        return EnduranceQuestManager.INSTANCE.getActiveSession(player)
                .map(session -> session.getQuest().getQuestId());
    }

    // -------- Combo system --------

    @Override
    public int registerComboAction(UUID playerId, String actionName, float damage) {
        if (!ComboSystemFacade.isInitialized()) {
            return 0;
        }
        ComboSystem.ActionType actionType;
        try {
            actionType = ComboSystem.ActionType.valueOf(actionName);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[CombatEnduranceBridge] Unknown action type: {}", actionName);
            return 0;
        }
        return ComboSystemFacade.get()
                .getSession(playerId)
                .map(session -> session.registerAction(actionType, damage).styleEarned())
                .orElse(0);
    }

    @Override
    public boolean isComboSystemAvailable() {
        return ComboSystemFacade.isInitialized();
    }

    // -------- Shield parry --------

    @Override
    public void onParry(ServerPlayer player) {
        IComboSession comboSession = EnduranceEventHandler.getComboSession(player.getUUID());
        if (comboSession != null) {
            IComboSession.ActionResult actionResult =
                    comboSession.registerAction(ComboSystem.ActionType.PARRY, 0);
            EnduranceEventCombat.syncCombatFlowToClient(
                    player,
                    ComboSystem.ActionType.PARRY.getDisplayName(),
                    actionResult.styleEarned()
            );
        }
    }

    // -------- Momentum --------

    @Override
    public void onPlayerKill(UUID playerId) {
        MomentumTracker.INSTANCE.onPlayerKill(playerId);
    }

    // -------- Perk discovery --------

    @Override
    public void recordExecution(ServerPlayer player) {
        PerkSynergyWeb.INSTANCE.recordExecution(player);
    }

    // -------- Attack classification --------

    @Override
    public void classifyAndRegisterAttack(UUID attackerId, ItemStack weapon,
                                           boolean isRanged, DamageSource source,
                                           float damageDealt) {
        if (!ComboSystemFacade.isInitialized()) {
            return;
        }

        // Check for counter-attack window (bonus style from dodge/parry)
        com.devmod.abilities.DodgeAbilitySystem.DodgeData dodgeData =
                com.devmod.abilities.DodgeAbilitySystem.INSTANCE.getDodgeData(attackerId);
        if (dodgeData.counterAttackWindowTicks > 0) {
            ComboSystemFacade.get()
                    .getSession(attackerId)
                    .ifPresent(session ->
                            session.registerAction(ComboSystem.ActionType.COUNTER_ATTACK, damageDealt));
            return;
        }

        ComboSystem.ActionType actionType = classifyAttackType(weapon, isRanged, source);
        ComboSystemFacade.get()
                .getSession(attackerId)
                .ifPresent(session -> session.registerAction(actionType, damageDealt));
    }

    /**
     * Classify an attack based on the weapon and damage source.
     * Maps weapon types to the available ActionType enum values.
     */
    private static ComboSystem.ActionType classifyAttackType(ItemStack weapon, boolean isRanged,
                                                              DamageSource source) {
        // Heavy weapons (axe, mace) -> HEAVY_ATTACK; everything else -> LIGHT_ATTACK
        if (!isRanged && !weapon.isEmpty()) {
            var item = weapon.getItem();
            if (item instanceof net.minecraft.world.item.AxeItem || item instanceof MaceItem) {
                return ComboSystem.ActionType.HEAVY_ATTACK;
            }
        }
        return ComboSystem.ActionType.LIGHT_ATTACK;
    }

    // -------- Quest lifecycle cleanup --------

    @Override
    public void onQuestEnded(UUID playerId) {
        ExecutionSystem.INSTANCE.onPlayerLeave(playerId);
    }

    // -------- Execution state --------

    @Override
    public boolean isExecuting(ServerPlayer player) {
        return ExecutionSystem.INSTANCE.isExecuting(player);
    }

    @Override
    public boolean interruptExecution(ServerPlayer player) {
        return ExecutionSystem.INSTANCE.interruptExecution(player) != null;
    }

    @Override
    public float getVulnerabilityMultiplier(ServerPlayer player) {
        return ExecutionSystem.getVulnerabilityMultiplier(player);
    }

    @Override
    public void tickExecutionSystem() {
        ExecutionSystem.INSTANCE.tick();
    }

    @Override
    public void tickExecutionPlayer(ServerPlayer player) {
        ExecutionSystem.INSTANCE.tickPlayer(player);
    }

    @Override
    public void onPlayerLeave(UUID playerId) {
        ExecutionSystem.INSTANCE.onPlayerLeave(playerId);
    }

    // -------- Signature weapon tracking --------

    @Override
    public void recordSoulDamage(ServerPlayer player, float damage) {
        SoulImprintManager.INSTANCE.recordDamage(player, damage);
    }

    @Override
    public void recordSoulHeadshot(ServerPlayer player) {
        SoulImprintManager.INSTANCE.recordHeadshot(player);
    }

    @Override
    public void recordSoulCriticalHit(ServerPlayer player) {
        SoulImprintManager.INSTANCE.recordCriticalHit(player);
    }

    @Override
    public void recordSoulKill(ServerPlayer player, LivingEntity target, boolean isBoss) {
        SoulImprintManager.INSTANCE.recordKill(player, target, isBoss);
    }

    @Override
    public void recordSoulExecuteKill(ServerPlayer player) {
        SoulImprintManager.INSTANCE.recordExecuteKill(player);
    }

    @Override
    public void recordSoulSSSWave(ServerPlayer player) {
        SoulImprintManager.INSTANCE.recordSSSWave(player);
    }

    @Override
    public void recordSoulNoHitWave(ServerPlayer player) {
        SoulImprintManager.INSTANCE.recordNoHitWave(player);
    }

    // -------- Body part detection --------

    @Override
    public String rayTraceBodyPart(LivingEntity attacker, LivingEntity target) {
        return HitHelper.rayTraceBodyPartAABB(attacker, target).name();
    }

    @Override
    public String getBodyPartFromHitY(LivingEntity target, double hitY) {
        return HitHelper.getBodyPart(target, hitY).name();
    }
}
