package com.devmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.devmod.debug.NativeDebugSender;

/**
 * Feeds {@code DebugFeature.BLOCK_UPDATES} from the point where a neighbour update is dispatched.
 * <p>
 * {@code BlockStateBase.handleNeighborChanged} is the one place that performs the virtual call
 * into {@code Block.neighborChanged}, so hooking it sees every update no matter what the block
 * class does. Every other candidate misses cases:
 * <ul>
 *   <li>{@code BlockBehaviour.neighborChanged} is the base implementation, which the 23 block
 *       classes that override it without calling {@code super} never reach - including
 *       redstone wire, repeaters, comparators, pistons and doors.</li>
 *   <li>{@code Level.neighborChanged} is bypassed by {@code updateNeighborsAt} and
 *       {@code updateNeighborsAtExceptFromFacing}, which go straight to the level's
 *       {@code NeighborUpdater} and are the most common source of updates.</li>
 * </ul>
 * Every updater path - {@code CollectingNeighborUpdater}'s simple, full and multi updates,
 * both {@code InstantNeighborUpdater} overloads, and NeoForge's extra call from
 * {@code ComparatorBlock.onNeighborChange} - converges here, so one hook covers all of them
 * with no double recording.
 * <p>
 * Shape updates ({@code neighborShapeChanged} / {@code executeShapeUpdate}) are deliberately not
 * covered: they are a separate mechanism, and vanilla never reported them as neighbour updates.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockStateNeighborUpdateMixin {

    @Inject(method = "handleNeighborChanged", at = @At("HEAD"))
    private void devmod$recordNeighborUpdate(Level level, BlockPos pos, Block block, BlockPos neighborPos,
                                             boolean movedByPiston, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        NativeDebugSender.INSTANCE.recordBlockUpdate(serverLevel, pos);
    }
}
