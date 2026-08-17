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

    /**
     * How many recheck windows to skip after the mob refused our target.
     *
     * <p>Age of Fight's BoneboundVanguard overrides {@code setTarget} to do nothing while it holds
     * an interdiction lease on a target of its own choosing. Without this back-off the goal retried
     * every ten ticks for the whole lease -- start, nothing written, canContinueToUse false, stop --
     * churning against the very runtime we are trying to cooperate with.
     */
    private static final int REFUSED_BACKOFF_CHECKS = 6;

    private static final double TARGET_RANGE_SQ = 48.0 * 48.0;
    private static final double CONTINUE_RANGE_SQ = 64.0 * 64.0;

    @Nullable
    private ServerPlayer targetPlayer;
    private int recheckDelay = 0;

    private final boolean deferToOwner;

    /**
     * Target the nearest player, taking ownership of target selection.
     *
     * @param mob the mob to give a target to
     */
    public EnduranceTargetPlayerGoal(Mob mob) {
        this(mob, false);
    }

    /**
     * @param mob the mob to give a target to
     * @param deferToOwner true when another mod owns this mob's target selection, so we only
     *     bootstrap a target it does not have and never replace one it chose
     */
    public EnduranceTargetPlayerGoal(Mob mob, boolean deferToOwner) {
        super(mob, false, true); // mustSee=false, mustReach=true
        this.deferToOwner = deferToOwner;
        this.setFlags(Objects.requireNonNull(EnumSet.of(Goal.Flag.TARGET)));
    }

    @Override
    public boolean canUse() {
        // Don't re-check too frequently
        if (--this.recheckDelay > 0) {
            return false;
        }
        this.recheckDelay = 10; // Check every 0.5 seconds

        LivingEntity currentTarget = this.mob.getTarget();
        // Already has a valid target
        if (currentTarget instanceof ServerPlayer && currentTarget.isAlive()) {
            return false;
        }
        // When another mod owns target selection we bootstrap only: any live target it picked --
        // an ally under a protective order, a designated interdiction target -- is its decision to
        // make and outranks ours. We exist for the case where it has none at all, which is every
        // freshly spawned arena mob, because a never-aggressed player counts as neutral to it.
        if (this.deferToOwner && currentTarget != null && currentTarget.isAlive()) {
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
            if (this.deferToOwner && this.mob.getTarget() != this.targetPlayer) {
                // setTarget was swallowed: the mob is holding a lease of its own. Wait longer
                // rather than retrying into it every window.
                this.recheckDelay = 10 * REFUSED_BACKOFF_CHECKS;
            }
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
            // Diagnostic logging for targeting debug
            if (this.recheckDelay <= 0 && serverLevel.players().size() == 1) {
                boolean alive = player.isAlive();
                boolean spectator = player.isSpectator();
                boolean creative = player.isCreative();
                net.minecraft.world.level.GameType gameMode = player.gameMode.getGameModeForPlayer();
                if (spectator || creative || !alive) {
                    org.slf4j.LoggerFactory.getLogger(EnduranceTargetPlayerGoal.class)
                        .info("[AIDebug] Mob {} skipping player {} (alive={}, spectator={}, creative={}, gameMode={})",
                            this.mob.getType().toString(),
                            player.getName().getString(),
                            alive, spectator, creative, gameMode);
                }
            }

            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }

            double distSq = this.mob.distanceToSqr(player);
            if (distSq < nearestDistSq && distSq < TARGET_RANGE_SQ) {
                // Check if we can see the player
                boolean hasLOS = this.mob.getSensing().hasLineOfSight(player);
                if (hasLOS) {
                    nearestDistSq = distSq;
                    nearest = player;
                } else if (this.recheckDelay <= 0 && serverLevel.players().size() == 1) {
                    // Log line of sight failure (only when single player)
                    org.slf4j.LoggerFactory.getLogger(EnduranceTargetPlayerGoal.class)
                        .info("[AIDebug] Mob {} no LOS to player {} (dist={}, mobPos={}, playerPos={}, mobDim={}, playerDim={})",
                            this.mob.getType().toString(),
                            player.getName().getString(),
                            Math.sqrt(distSq),
                            this.mob.blockPosition(),
                            player.blockPosition(),
                            this.mob.level().dimension().location(),
                            player.level().dimension().location());
                }
            }
        }

        return nearest;
    }
}
