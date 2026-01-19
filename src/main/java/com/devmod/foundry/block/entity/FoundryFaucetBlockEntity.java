package com.devmod.foundry.block.entity;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.block.FoundryFaucetBlock;

/**
 * Faucet block entity that pours molten fluid into casting blocks.
 */
public class FoundryFaucetBlockEntity extends BlockEntity {
    private static final int POUR_AMOUNT = 50;
    private static final int POUR_COOLDOWN_TICKS = 4;

    private int cooldown = 0;

    public FoundryFaucetBlockEntity(BlockPos pos, BlockState state) {
        super(Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_FAUCET.get()), pos, state);
    }

    public void tickServer() {
        Level level = Objects.requireNonNull(getLevel());
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        BlockState state = getBlockState();
        Direction facing = state.getValue(FoundryFaucetBlock.FACING);
        BlockPos sourcePos = worldPosition.relative(facing.getOpposite());
        BlockPos targetPos = worldPosition.below();

        BlockEntity sourceBe = level.getBlockEntity(sourcePos);
        FoundryControllerBlockEntity controller = null;
        if (sourceBe instanceof FoundryDrainBlockEntity drain) {
            controller = drain.getController(level);
        } else if (sourceBe instanceof FoundryTankBlockEntity tank) {
            controller = tank.getController(level);
        } else if (sourceBe instanceof FoundryControllerBlockEntity direct) {
            controller = direct;
        }

        if (controller == null || !controller.isFormed()) {
            return;
        }

        BlockEntity targetBe = level.getBlockEntity(targetPos);
        FoundryCastingBlockEntity casting = null;
        FoundryChannelBlockEntity channel = null;
        if (targetBe instanceof FoundryCastingTableBlockEntity table) {
            casting = table;
        } else if (targetBe instanceof FoundryCastingBasinBlockEntity basin) {
            casting = basin;
        } else if (targetBe instanceof FoundryChannelBlockEntity foundryChannel) {
            channel = foundryChannel;
        }

        if (casting == null && channel == null) {
            return;
        }

        FluidStack drained = controller.drainMolten(POUR_AMOUNT, false);
        if (drained.isEmpty()) {
            return;
        }

        int accepted;
        if (casting != null) {
            accepted = casting.tryFillFromFaucet(drained);
        } else if (channel != null) {
            accepted = channel.tryFillFromFaucet(drained);
        } else {
            return;
        }
        if (accepted > 0) {
            controller.drainMolten(accepted, true);
            cooldown = POUR_COOLDOWN_TICKS;
        }
    }
}
