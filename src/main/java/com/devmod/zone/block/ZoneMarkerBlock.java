package com.devmod.zone.block;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.devmod.zone.ZoneBlockEntities;
import com.devmod.zone.block.entity.ZoneMarkerBlockEntity;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZonePresets;
import com.devmod.zone.data.ZoneRegistry;
import com.devmod.zone.network.ZoneNetworkHandler;

/**
 * Zone Marker block - marks zone boundaries and opens zone editor.
 * When placed, registers its position with ZoneRegistry.
 * On interaction, opens the Zone Editor GUI.
 */
public class ZoneMarkerBlock extends BaseEntityBlock {
    public static final MapCodec<ZoneMarkerBlock> CODEC = simpleCodec(ZoneMarkerBlock::new);

    public ZoneMarkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return Objects.requireNonNull(CODEC);
    }

    @Override
    @Nonnull
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new ZoneMarkerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@Nonnull Level level,
            @Nonnull BlockState state, @Nonnull BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, Objects.requireNonNull(ZoneBlockEntities.ZONE_MARKER.get()),
            ZoneMarkerBlockEntity::serverTick);
    }

    @Override
    @Nonnull
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level,
            @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull BlockHitResult hitResult) {

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        // Require OP level 2 to edit zones
        if (!serverPlayer.hasPermissions(2)) {
            serverPlayer.displayClientMessage(
                Objects.requireNonNull(Component.translatable("zone.marker.no_permission")), true);
            return InteractionResult.FAIL;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ZoneMarkerBlockEntity markerBE) {
            var server = serverPlayer.getServer();
            if (server == null) {
                return InteractionResult.FAIL;
            }

            // Validate and clear orphan references (zone was deleted)
            markerBE.validateAndClearOrphan();

            // Get linked zone if exists
            Optional<ZoneDefinition> linkedZone = markerBE.getLinkedZone();

            // Open Zone Editor GUI via network
            boolean canDelete = linkedZone.isPresent();
            ZoneNetworkHandler.openZoneEditor(serverPlayer, linkedZone.orElse(null), pos, canDelete);
            serverPlayer.displayClientMessage(
                Objects.requireNonNull(Component.translatable("zone.marker.opening")), true);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    protected void onPlace(@Nonnull BlockState state, @Nonnull Level level,
            @Nonnull BlockPos pos, @Nonnull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ZoneMarkerBlockEntity markerBE) {
                var zoneId = markerBE.getValidatedZoneId();
                if (zoneId != null) {
                    ZoneRegistry registry = ZoneRegistry.get(serverLevel.getServer());
                    registry.registerMarker(pos, zoneId);
                }
            }
        }
    }

    @Override
    protected void onRemove(@Nonnull BlockState state, @Nonnull Level level,
            @Nonnull BlockPos pos, @Nonnull BlockState newState, boolean movedByPiston) {
        if (!state.is(Objects.requireNonNull(newState.getBlock()))) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                ZoneRegistry registry = ZoneRegistry.get(serverLevel.getServer());
                registry.unregisterMarker(pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void animateTick(@Nonnull BlockState state, @Nonnull Level level,
            @Nonnull BlockPos pos, @Nonnull RandomSource random) {
        // Ambient particles in zone color
        if (random.nextInt(3) == 0) {
            int color = ZonePresets.COLOR_HUB; // Default color

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ZoneMarkerBlockEntity markerBE) {
                color = markerBE.getZoneColor();
            }

            // Convert color to RGB components (0-1 range)
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            DustParticleOptions dust = new DustParticleOptions(new Vector3f(r, g, b), 1.0f);

            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 0.5 + random.nextDouble() * 0.5;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4;

            level.addParticle(dust, x, y, z, 0, 0.05, 0);
        }
    }
}
