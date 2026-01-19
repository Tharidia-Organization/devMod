package com.devmod.clone.block;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.block.entity.TelepadBlockEntity;
import com.devmod.clone.network.TelepadOpenScreenPayload;
import com.devmod.network.NetworkHandler;

/**
 * Teleportation pad block.
 * Players standing on the pad charge up and teleport to another telepad with the same name.
 *
 * <p>Features:
 * <ul>
 *   <li>Right-click to configure network name</li>
 *   <li>Stand on pad to charge (40 ticks)</li>
 *   <li>Teleports to random destination with same name</li>
 *   <li>Cross-dimensional teleportation</li>
 *   <li>Cooldown on arrival to prevent loops</li>
 * </ul>
 */
public final class TelepadBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<TelepadBlock> CODEC = simpleCodec(TelepadBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    public TelepadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(ACTIVE, false));
    }

    @Override
    @Nonnull
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new TelepadBlockEntity(pos, state);
    }

    @Override
    @Nonnull
    protected RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nonnull
    protected VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                                   @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected float getShadeBrightness(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return 1.0f;
    }

    @Override
    @Nonnull
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level,
                                                @Nonnull BlockPos pos, @Nonnull Player player,
                                                @Nonnull BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TelepadBlockEntity telepad) {
                // Send packet to open config screen on client
                TelepadOpenScreenPayload payload = new TelepadOpenScreenPayload(
                    pos,
                    telepad.getTelepadName()
                );
                NetworkHandler.sendTelepadOpenScreen(serverPlayer, payload);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                            @Nonnull BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof TelepadBlockEntity telepad) {
                    telepad.removeFromRegistry();
                }
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Override
    protected void entityInside(@Nonnull BlockState state, @Nonnull Level level,
                                @Nonnull BlockPos pos, @Nonnull Entity entity) {
        if (!(entity instanceof Player player)) {
            return;
        }
        if (level.isClientSide) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TelepadBlockEntity telepad) {
            telepad.startCharging(player);
        }
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@Nonnull Level level,
                                                                    @Nonnull BlockState state,
                                                                    @Nonnull BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        if (type == CloneBlockEntities.TELEPAD.get()) {
            return (lvl, pos, st, be) -> {
                if (be instanceof TelepadBlockEntity telepad && telepad.needsTicking()) {
                    telepad.tick();
                }
            };
        }
        return null;
    }

    /**
     * Sets the active state of the telepad block.
     */
    public static void setActive(Level level, BlockPos pos, BlockState state, boolean active) {
        if (state.getValue(ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ACTIVE, active), Block.UPDATE_ALL);
        }
    }
}
