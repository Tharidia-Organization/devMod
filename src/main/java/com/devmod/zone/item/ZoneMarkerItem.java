package com.devmod.zone.item;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.devmod.zone.ZoneBlocks;
import com.devmod.zone.block.entity.ZoneMarkerBlockEntity;

/**
 * Zone Marker item - places zone marker blocks to define zone boundaries.
 * Only OP 2+ can use this item.
 * Can be pre-configured with a zone ID via DataComponents.
 */
public class ZoneMarkerItem extends Item {
    public static final String TAG_ZONE_ID = "zone_id";
    public static final String TAG_ZONE_NAME = "zone_name";

    public ZoneMarkerItem(Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    public InteractionResult useOn(@Nonnull UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        // Require OP level 2
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(
                Component.translatable("zone.marker.no_permission"), true);
            return InteractionResult.FAIL;
        }

        BlockPos placePos = context.getClickedPos().relative(context.getClickedFace());

        // Check if position is valid for placement
        if (!level.getBlockState(placePos).canBeReplaced()) {
            return InteractionResult.FAIL;
        }

        // Place the marker block
        level.setBlock(placePos, ZoneBlocks.ZONE_MARKER.get().defaultBlockState(), 3);

        // Initialize block entity with component data if present
        BlockEntity be = level.getBlockEntity(placePos);
        if (be instanceof ZoneMarkerBlockEntity markerBE) {
            ItemStack stack = context.getItemInHand();
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.hasUUID(TAG_ZONE_ID)) {
                    UUID zoneId = tag.getUUID(TAG_ZONE_ID);
                    markerBE.setZoneId(zoneId);
                }
            }
        }

        // Play placement sound
        level.playSound(null, placePos,
            ZoneBlocks.ZONE_MARKER.get().defaultBlockState().getSoundType(level, placePos, null).getPlaceSound(),
            player.getSoundSource(), 1.0f, 1.0f);

        player.displayClientMessage(
            Component.translatable("zone.marker.placed"), true);

        // Consume item in survival
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context,
            @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        tooltipComponents.add(Component.translatable("zone.marker.tooltip"));

        // Show linked zone if configured
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains(TAG_ZONE_NAME)) {
                String zoneName = tag.getString(TAG_ZONE_NAME);
                tooltipComponents.add(Component.translatable("zone.marker.linked_to", zoneName)
                    .withStyle(style -> style.withColor(0x00D4AA)));
            }
        }
    }

    /**
     * Creates a zone marker item pre-configured for a specific zone.
     *
     * @param zoneId   The zone UUID
     * @param zoneName The zone display name (for tooltip)
     * @return Configured item stack
     */
    @Nonnull
    public static ItemStack createForZone(@Nonnull UUID zoneId, @Nonnull String zoneName) {
        ItemStack stack = new ItemStack(com.devmod.zone.ZoneItems.ZONE_MARKER.get());
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ZONE_ID, zoneId);
        tag.putString(TAG_ZONE_NAME, zoneName);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }
}
