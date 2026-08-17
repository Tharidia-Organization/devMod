package com.devmod.endurance.ai;

import java.util.EnumSet;
import java.util.Objects;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

/**
 * A simple melee attack goal for endurance mobs that has NO dimension or biome checks.
 * This ensures mobs can attack players in dynamic dimensions where modded attack goals fail.
 *
 * Based on vanilla MeleeAttackGoal but stripped of unnecessary complexity.
 */
public final class EnduranceMeleeAttackGoal extends Goal {

    private final Mob mob;
    private final double speedModifier;
    private final boolean followingTargetEvenIfNotSeen;
    private Path path;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private long lastCanUseCheck;
    private static final long COOLDOWN_BETWEEN_CAN_USE_CHECKS = 20L;

    /**
     * Permitted checks between diagnostic lines.
     *
     * <p>The failure logs below used to be gated on {@code gameTime % 100 == 0}. canUse() only
     * runs on the subset of ticks that clear the cooldown above, so the log also needed that tick
     * to be a multiple of 100 -- the two conditions almost never coincide, and the lines were
     * effectively unreachable. Their absence from a log where mobs were visibly frozen read as
     * "navigation is fine" when it was really "the instrument never fired". Counting permitted
     * checks instead makes the rate predictable: one line roughly every five seconds per mob.
     */
    private static final int CHECKS_BETWEEN_DIAGNOSTICS = 5;

    private int checksSinceDiagnostic;

    public EnduranceMeleeAttackGoal(Mob mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.followingTargetEvenIfNotSeen = followingTargetEvenIfNotSeen;
        this.setFlags(Objects.requireNonNull(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)));
    }

    @Override
    public boolean canUse() {
        long gameTime = this.mob.level().getGameTime();
        if (gameTime - this.lastCanUseCheck < COOLDOWN_BETWEEN_CAN_USE_CHECKS) {
            return false;
        }
        this.lastCanUseCheck = gameTime;
        this.checksSinceDiagnostic++;
        boolean logNow = this.checksSinceDiagnostic >= CHECKS_BETWEEN_DIAGNOSTICS;
        if (logNow) {
            this.checksSinceDiagnostic = 0;
        }

        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            if (logNow) {
                org.slf4j.LoggerFactory.getLogger("EnduranceMeleeAttackGoal").info(
                    "[AIDebug] canUse=false: mob={}, target={}, targetAlive={}",
                    this.mob.getUUID().toString().substring(0, 8),
                    target != null ? target.getName().getString() : "null",
                    target != null && target.isAlive());
            }
            return false;
        }

        // PathfinderMob check for pathing
        if (this.mob instanceof PathfinderMob pathfinderMob) {
            this.path = pathfinderMob.getNavigation().createPath(target, 0);
            if (this.path != null) {
                return true;
            }
            // Log path creation failure
            if (logNow) {
                org.slf4j.LoggerFactory.getLogger("EnduranceMeleeAttackGoal").info(
                    "[AIDebug] canUse path=null: mob={}, target={}, mobPos={}, targetPos={}, dist={}",
                    this.mob.getUUID().toString().substring(0, 8),
                    target.getName().getString(),
                    this.mob.blockPosition(),
                    target.blockPosition(),
                    Math.sqrt(this.mob.distanceToSqr(target)));
            }
        }

        // Fallback: check if we're close enough to attack without pathing
        return this.getAttackReachSqr(target) >= this.mob.distanceToSqr(target);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }

        if (!this.followingTargetEvenIfNotSeen) {
            return !this.mob.getNavigation().isDone();
        }

        // Keep attacking even if target goes out of sight briefly
        return true;
    }

    @Override
    public void start() {
        org.slf4j.LoggerFactory.getLogger("EnduranceMeleeAttackGoal").info(
            "[AIDebug] Goal START: mob={}, target={}, path={}, pos={}",
            this.mob.getUUID().toString().substring(0, 8),
            this.mob.getTarget() != null ? this.mob.getTarget().getName().getString() : "null",
            this.path != null ? "valid" : "null",
            this.mob.blockPosition());
        if (this.mob instanceof PathfinderMob pathfinderMob) {
            pathfinderMob.getNavigation().moveTo(this.path, this.speedModifier);
        }
        this.mob.setAggressive(true);
        this.ticksUntilNextPathRecalculation = 0;
        this.ticksUntilNextAttack = 0;
    }

    @Override
    public void stop() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            this.mob.setAggressive(false);
        }
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        // Look at target
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distanceSqr = this.mob.distanceToSqr(target);

        // Debug tick - log periodically
        long gameTime = this.mob.level().getGameTime();
        if (gameTime % 40 == 0) { // Every 2 seconds
            double attackReach = this.getAttackReachSqr(target);
            org.slf4j.LoggerFactory.getLogger("EnduranceMeleeAttackGoal").info(
                "[AIDebug] tick: mob={}, mobPos={}, targetPos={}, distSq={}, reachSq={}, inRange={}, cooldown={}",
                this.mob.getUUID().toString().substring(0, 8),
                this.mob.blockPosition(),
                target.blockPosition(),
                String.format("%.1f", distanceSqr),
                String.format("%.1f", attackReach),
                distanceSqr <= attackReach,
                this.ticksUntilNextAttack);
        }
        this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);

        // Recalculate path periodically
        if ((this.followingTargetEvenIfNotSeen || this.mob.getSensing().hasLineOfSight(target))
                && this.ticksUntilNextPathRecalculation <= 0
                && ((this.pathedTargetX == 0.0 && this.pathedTargetY == 0.0 && this.pathedTargetZ == 0.0)
                    || target.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0
                    || this.mob.getRandom().nextFloat() < 0.05F)) {

            this.pathedTargetX = target.getX();
            this.pathedTargetY = target.getY();
            this.pathedTargetZ = target.getZ();
            this.ticksUntilNextPathRecalculation = 4 + this.mob.getRandom().nextInt(7);

            if (distanceSqr > 1024.0) {
                this.ticksUntilNextPathRecalculation += 10;
            } else if (distanceSqr > 256.0) {
                this.ticksUntilNextPathRecalculation += 5;
            }

            if (!this.mob.getNavigation().moveTo(target, this.speedModifier)) {
                this.ticksUntilNextPathRecalculation += 15;
            }

            this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
        }

        // Attack when in range
        this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
        this.checkAndPerformAttack(target, distanceSqr);
    }

    private void checkAndPerformAttack(LivingEntity target, double distanceSqr) {
        double attackReachSqr = this.getAttackReachSqr(target);
        if (distanceSqr <= attackReachSqr && this.ticksUntilNextAttack <= 0) {
            // Debug: Log attack attempt
            var attackAttribute = this.mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (attackAttribute == null) {
                return;
            }
            this.resetAttackCooldown();
            this.mob.swing(InteractionHand.MAIN_HAND);
            float attackDamage = (float) attackAttribute.getValue();
            float targetHealthBefore = target.getHealth();

            // Check for damage resistance effect that might block damage
            var resistanceEffect = target.getEffect(Objects.requireNonNull(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE));
            int resistanceLevel = resistanceEffect != null ? resistanceEffect.getAmplifier() + 1 : 0;
            int resistanceDuration = resistanceEffect != null ? resistanceEffect.getDuration() : 0;

            // Debug: Check target armor before attack
            int targetArmor = target.getArmorValue();
            float targetArmorToughness = 0;
            // target is already LivingEntity, no instanceof check needed
            var toughnessHolder = net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS;
            if (toughnessHolder != null) {
                var toughnessAttr = target.getAttribute(toughnessHolder);
                if (toughnessAttr != null) {
                    targetArmorToughness = (float) toughnessAttr.getValue();
                }
            }

            boolean success = this.mob.doHurtTarget(target);

            float targetHealthAfter = target.getHealth();
            org.slf4j.LoggerFactory.getLogger("EnduranceMeleeAttackGoal").info(
                "[Attack] {} -> {} | damage={}, success={}, healthBefore={}, healthAfter={}, diff={}, armor={}, toughness={}, resistanceLvl={}, resistanceDur={}",
                this.mob.getType().toString(),
                target.getName().getString(),
                attackDamage,
                success,
                targetHealthBefore,
                targetHealthAfter,
                targetHealthBefore - targetHealthAfter,
                targetArmor,
                targetArmorToughness,
                resistanceLevel,
                resistanceDuration
            );
        }
    }

    private void resetAttackCooldown() {
        this.ticksUntilNextAttack = this.adjustedTickDelay(20);
    }

    private double getAttackReachSqr(LivingEntity target) {
        // Attack reach = mob width + target width + 0.5 buffer
        float reach = this.mob.getBbWidth() * 2.0F + target.getBbWidth() + 0.5F;
        return reach * reach;
    }

    @Override
    protected int adjustedTickDelay(int ticks) {
        return ticks;
    }
}
