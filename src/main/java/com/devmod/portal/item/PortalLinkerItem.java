package com.devmod.portal.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.portal.PortalColor;
import com.devmod.portal.PortalData;
import com.devmod.portal.PortalRegistry;
import com.devmod.portal.block.CustomPortalBlock;

/**
 * Item used to link two portals together for bidirectional teleportation.
 * Right-click a portal to select it, right-click another to link them.
 */
public class PortalLinkerItem extends Item {
    private static final String TAG_SELECTED_PORTAL = "selected_portal";
    private static final String TAG_SELECTED_DIM = "selected_dimension";
    private static final String TAG_SELECTED_COLOR = "selected_color";

    public PortalLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    @Nonnull
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState state = level.getBlockState(clickedPos);

        if (!(state.getBlock() instanceof CustomPortalBlock)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        ItemStack stack = context.getItemInHand();

        // Get color from the clicked portal block
        PortalColor clickedColor = state.getValue(CustomPortalBlock.COLOR);

        PortalRegistry registry = PortalRegistry.get(serverLevel);
        // Use findPortalContaining to find portal from any interior block, not just center
        Optional<PortalData> portalOpt = registry.findPortalContaining(serverLevel, clickedPos, clickedColor);

        if (portalOpt.isEmpty()) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("item.devmod.portal_linker.not_registered")
                    .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }

        PortalData portal = portalOpt.get();

        // Check if we have a previously selected portal using CustomData component
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : new CompoundTag();

        if (tag.contains(TAG_SELECTED_PORTAL)) {
            UUID selectedId = tag.getUUID(TAG_SELECTED_PORTAL);
            int selectedColorIdx = tag.getInt(TAG_SELECTED_COLOR);
            PortalColor selectedColor = PortalColor.byIndex(selectedColorIdx);

            // Trying to link the same portal
            if (portal.id().equals(selectedId)) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("item.devmod.portal_linker.same_portal")
                        .withStyle(ChatFormatting.YELLOW), true);
                }
                return InteractionResult.FAIL;
            }

            // Check color match
            if (portal.color() != selectedColor) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("item.devmod.portal_linker.color_mismatch")
                        .withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.FAIL;
            }

            // Check if either portal is already linked
            if (portal.isLinked()) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("item.devmod.portal_linker.already_linked")
                        .withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.FAIL;
            }

            Optional<PortalData> selectedOpt = registry.get(selectedId);
            if (selectedOpt.isEmpty()) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("item.devmod.portal_linker.portal_destroyed")
                        .withStyle(ChatFormatting.RED), true);
                }
                clearSelection(stack);
                return InteractionResult.FAIL;
            }

            if (selectedOpt.get().isLinked()) {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("item.devmod.portal_linker.already_linked")
                        .withStyle(ChatFormatting.RED), true);
                }
                clearSelection(stack);
                return InteractionResult.FAIL;
            }

            // Link the portals (with server to update block states)
            boolean success = registry.link(selectedId, portal.id(), level.getServer());

            if (success) {
                level.playSound(null, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.5F);
                if (player != null) {
                    player.displayClientMessage(Component.translatable("item.devmod.portal_linker.linked")
                        .withStyle(ChatFormatting.GREEN), true);
                }
            } else {
                if (player != null) {
                    player.displayClientMessage(Component.translatable("item.devmod.portal_linker.link_failed")
                        .withStyle(ChatFormatting.RED), true);
                }
            }

            // Clear selection
            clearSelection(stack);
        } else {
            // Select this portal
            tag.putUUID(TAG_SELECTED_PORTAL, portal.id());
            tag.putString(TAG_SELECTED_DIM, serverLevel.dimension().location().toString());
            tag.putInt(TAG_SELECTED_COLOR, portal.color().getIndex());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            level.playSound(null, clickedPos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 1.0F);

            if (player != null) {
                player.displayClientMessage(Component.translatable("item.devmod.portal_linker.selected",
                    Component.translatable("color.devmod." + portal.color().getSerializedName()))
                    .withStyle(ChatFormatting.AQUA), true);
            }
        }

        return InteractionResult.CONSUME;
    }

    private void clearSelection(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains(TAG_SELECTED_PORTAL)) {
                int colorIdx = tag.getInt(TAG_SELECTED_COLOR);
                PortalColor color = PortalColor.byIndex(colorIdx);
                String dimStr = tag.getString(TAG_SELECTED_DIM);

                tooltip.add(Component.translatable("item.devmod.portal_linker.has_selection")
                    .withStyle(ChatFormatting.YELLOW));
                tooltip.add(Component.translatable("item.devmod.portal_linker.selection_color",
                    Component.translatable("color.devmod." + color.getSerializedName()))
                    .withStyle(style -> style.withColor(color.getColor())));
                tooltip.add(Component.literal("  " + dimStr).withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("item.devmod.portal_linker.tooltip"));
            }
        } else {
            tooltip.add(Component.translatable("item.devmod.portal_linker.tooltip"));
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("item.devmod.portal_linker.usage").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            return customData.copyTag().contains(TAG_SELECTED_PORTAL);
        }
        return false;
    }
}
