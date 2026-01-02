package com.devmod.endurance.ai;

import java.util.EnumSet;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;

/**
 * A simple targeting goal for endurance mobs that targets the nearest player.
 * This goal intentionally has NO dimension or biome checks, making it work
 * in dynamic dimensions where modded mob targeting goals might fail.
 *
 * This is added to endurance mobs at spawn time to ensure they can always
 * target players, regardless of dimension-specific checks in their normal AI.
 */
public final class EnduranceTargetPlayerGoal extends TargetGoal {

    private static final double TARGET_RANGE_SQ = 48.0 * 48.0;
    private static final double CONTINUE_RANGE_SQ = 64.0 * 64.0;

    @Nullable
    private ServerPlayer targetPlayer;
    private int recheckDelay = 0;

    public EnduranceTargetPlayerGoal(Mob mob) {
        super(mob, false, true); // mustSee=false, mustReach=true
        this.setFlags(Objects.requireNonNull(EnumSet.of(Goal.Flag.TARGET)));
    }

    @Override
    public boolean canUse() {
        // Don't re-check too frequently
        if (--this.recheckDelay > 0) {
            return false;
        }
        this.recheckDelay = 10; // Check every 0.5 seconds

        // Already has a valid target
        LivingEntity currentTarget = this.mob.getTarget();
        if (currentTarget instanceof ServerPlayer && currentTarget.isAlive()) {
            return false;
        }

        // Find nearest player in the level
        this.targetPlayer = findNearestPlayer();
        return this.targetPlayer != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        // Continue as long as target is within range
        return this.mob.distanceToSqr(target) < CONTINUE_RANGE_SQ;
    }

    @Override
    public void start() {
        if (this.targetPlayer != null) {
            this.mob.setTarget(this.targetPlayer);
        }
        super.start();
    }

    @Override
    public void stop() {
        this.targetPlayer = null;
        super.stop();
    }

    @Nullable
    private ServerPlayer findNearestPlayer() {
        var level = this.mob.level();
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }

        ServerPlayer nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (ServerPlayer player : serverLevel.players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }

            double distSq = this.mob.distanceToSqr(player);
            if (distSq < nearestDistSq && distSq < TARGET_RANGE_SQ) {
                // Check if we can see the player
                if (this.mob.getSensing().hasLineOfSight(player)) {
                    nearestDistSq = distSq;
                    nearest = player;
                }
            }
        }

        return nearest;
    }
}
