package com.devmod.portal;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Resolves teleport destinations that an entity can actually occupy.
 *
 * <p>Stored destinations outlive the world they point at: the target may have been built
 * over since it was recorded, and a dimension's build limits may not contain it at all.
 * Both cases are fatal for the entity (suffocation, or a fall out of the world) and are
 * followed by a teleport cooldown that stops it walking back, so every teleport has to
 * re-check its target immediately before moving the entity.
 */
public final class PortalDestinationResolver {
    /** How far above the requested position to look for a gap, in blocks. */
    private static final int SEARCH_HEIGHT = 4;

    private PortalDestinationResolver() {
    }

    /**
     * Finds a position at, or just above, {@code desired} with two blocks of clearance.
     *
     * <p>Loads the destination chunk first: block reads in an unloaded chunk report air
     * and would pass any clearance check.
     *
     * @param level the destination level
     * @param desired the position the destination data points at
     * @return a safe position for the entity's feet, or empty if the destination is
     *     buried; callers must abort the teleport rather than move the entity anyway
     */
    @Nonnull
    public static Optional<BlockPos> resolveSafeDestination(@Nonnull ServerLevel level, @Nonnull BlockPos desired) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(desired, "desired");

        int minY = level.getMinBuildHeight();
        // Two blocks of clearance are needed, so the highest usable feet position is one
        // below the last addressable block.
        int maxFeetY = level.getMaxBuildHeight() - 2;
        if (maxFeetY < minY) {
            return Objects.requireNonNull(Optional.empty());
        }

        int startY = Mth.clamp(desired.getY(), minY, maxFeetY);
        int endY = Math.min(startY + SEARCH_HEIGHT, maxFeetY);

        level.getChunk(Objects.requireNonNull(desired));

        for (int y = startY; y <= endY; y++) {
            BlockPos feet = new BlockPos(desired.getX(), y, desired.getZ());
            if (isPassable(level, feet) && isPassable(level, Objects.requireNonNull(feet.above()))) {
                return Objects.requireNonNull(Optional.of(feet));
            }
        }

        return Objects.requireNonNull(Optional.empty());
    }

    /**
     * Tests whether an entity can stand in the block at {@code pos}.
     *
     * <p>Portal and transport blocks are the normal destinations and are not air, so this
     * tests for collision rather than emptiness.
     */
    private static boolean isPassable(@Nonnull ServerLevel level, @Nonnull BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty();
    }
}
