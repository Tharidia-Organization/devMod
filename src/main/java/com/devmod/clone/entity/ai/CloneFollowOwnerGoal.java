package com.devmod.clone.entity.ai;

import java.util.EnumSet;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import com.devmod.clone.entity.PlayerCloneEntity;

/**
 * AI goal for player clones to follow their owner.
 * Similar to FollowOwnerGoal but with clone-specific behavior.
 */
public class CloneFollowOwnerGoal extends Goal {

    private final PlayerCloneEntity clone;
    private final double speedModifier;
    private final float stopDistance;
    private final float startDistance;
    private final PathNavigation navigation;
    private final LevelReader level;

    private LivingEntity owner;
    private int timeToRecalcPath;
    private float oldWaterCost;

    public CloneFollowOwnerGoal(PlayerCloneEntity clone, double speed, float startDist, float stopDist) {
        this.clone = clone;
        this.speedModifier = speed;
        this.startDistance = startDist;
        this.stopDistance = stopDist;
        this.navigation = clone.getNavigation();
        this.level = clone.level();
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = clone.getOwner();
        if (owner == null) {
            return false;
        }
        if (owner.isSpectator()) {
            return false;
        }
        if (clone.isOrderedToSit()) {
            return false;
        }
        if (clone.distanceToSqr(owner) < startDistance * startDistance) {
            return false;
        }
        this.owner = owner;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (navigation.isDone()) {
            return false;
        }
        if (clone.isOrderedToSit()) {
            return false;
        }
        return clone.distanceToSqr(owner) > stopDistance * stopDistance;
    }

    @Override
    public void start() {
        timeToRecalcPath = 0;
        oldWaterCost = clone.getPathfindingMalus(PathType.WATER);
        clone.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public void stop() {
        owner = null;
        navigation.stop();
        clone.setPathfindingMalus(PathType.WATER, oldWaterCost);
    }

    @Override
    public void tick() {
        clone.getLookControl().setLookAt(owner, 10.0F, clone.getMaxHeadXRot());

        if (--timeToRecalcPath <= 0) {
            timeToRecalcPath = adjustedTickDelay(10);

            if (clone.distanceToSqr(owner) >= 144.0) {
                // Teleport if too far
                tryTeleportToOwner();
            } else {
                navigation.moveTo(owner, speedModifier);
            }
        }
    }

    private void tryTeleportToOwner() {
        BlockPos ownerPos = owner.blockPosition();

        for (int i = 0; i < 10; i++) {
            int dx = randomIntInclusive(-3, 3);
            int dy = randomIntInclusive(-1, 1);
            int dz = randomIntInclusive(-3, 3);

            if (maybeTeleportTo(ownerPos.getX() + dx, ownerPos.getY() + dy, ownerPos.getZ() + dz)) {
                return;
            }
        }
    }

    private boolean maybeTeleportTo(int x, int y, int z) {
        if (Math.abs(x - owner.getX()) < 2.0 && Math.abs(z - owner.getZ()) < 2.0) {
            return false;
        }

        if (!canTeleportTo(new BlockPos(x, y, z))) {
            return false;
        }

        clone.moveTo(x + 0.5, y, z + 0.5, clone.getYRot(), clone.getXRot());
        navigation.stop();
        return true;
    }

    private boolean canTeleportTo(@Nonnull BlockPos pos) {
        PathType pathType = WalkNodeEvaluator.getPathTypeStatic(clone, pos);
        if (pathType != PathType.WALKABLE) {
            return false;
        }

        BlockState state = level.getBlockState(pos.below());
        if (state.getBlock() instanceof LeavesBlock) {
            return false;
        }

        BlockPos diff = pos.subtract(clone.blockPosition());
        return level.noCollision(clone, clone.getBoundingBox().move(diff));
    }

    private int randomIntInclusive(int min, int max) {
        return clone.getRandom().nextInt(max - min + 1) + min;
    }
}
