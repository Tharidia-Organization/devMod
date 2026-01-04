package com.devmod.client.endurance.wis;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Represents a single combat event captured during a wave.
 * Immutable record for thread-safe collection.
 */
public sealed interface CombatEvent {

    /**
     * Timestamp in game ticks since wave start.
     */
    int ticksSinceWaveStart();

    /**
     * Kill event - player killed a mob.
     */
    record KillEvent(
        int ticksSinceWaveStart,
        UUID mobId,
        ResourceLocation mobType,
        BlockPos position,
        float damageDealt,
        String weaponUsed,
        boolean isElite,
        boolean isCritical,
        int comboCount,
        long timeToKillMs
    ) implements CombatEvent {}

    /**
     * Damage dealt event - player dealt damage to a mob.
     */
    record DamageDealtEvent(
        int ticksSinceWaveStart,
        UUID mobId,
        ResourceLocation mobType,
        float damage,
        String damageSource,
        boolean isCritical,
        BlockPos position
    ) implements CombatEvent {}

    /**
     * Damage taken event - player received damage.
     */
    record DamageTakenEvent(
        int ticksSinceWaveStart,
        @Nullable UUID sourceId,
        @Nullable ResourceLocation sourceType,
        float damage,
        String damageSource,
        float remainingHealth,
        BlockPos playerPosition
    ) implements CombatEvent {}

    /**
     * Combo event - combo milestone or break.
     */
    record ComboEvent(
        int ticksSinceWaveStart,
        ComboEventType type,
        int comboCount,
        int styleScore,
        String rankName
    ) implements CombatEvent {
        public enum ComboEventType {
            MILESTONE_5, MILESTONE_10, MILESTONE_25, MILESTONE_50, MILESTONE_100,
            RANK_UP, RANK_DOWN, COMBO_BREAK
        }
    }

    /**
     * Position snapshot - periodic player position for heatmap.
     */
    record PositionSnapshot(
        int ticksSinceWaveStart,
        BlockPos position,
        float health,
        int mobsNearby,
        boolean inCombat
    ) implements CombatEvent {}

    /**
     * Special action event - dodge, parry, counter, etc.
     */
    record SpecialActionEvent(
        int ticksSinceWaveStart,
        String actionType,
        int pointsEarned,
        int styleEarned,
        BlockPos position
    ) implements CombatEvent {}

    /**
     * Mob spawn event - mob appeared in the wave.
     */
    record MobSpawnEvent(
        int ticksSinceWaveStart,
        UUID mobId,
        ResourceLocation mobType,
        BlockPos position,
        boolean isElite,
        String affix
    ) implements CombatEvent {}

    /**
     * Ability used event - player used an ability/skill.
     */
    record AbilityUsedEvent(
        int ticksSinceWaveStart,
        String abilityId,
        @Nullable UUID targetId,
        BlockPos position,
        float manaCost,
        boolean successful
    ) implements CombatEvent {}
}
