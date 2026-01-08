package com.devmod.portal.item;

import com.devmod.portal.PortalColor;
import com.devmod.portal.PortalData;
import com.devmod.portal.PortalFrameDetector;
import com.devmod.portal.PortalRegistry;
import com.devmod.portal.PortalRuneEffects;
import com.devmod.portal.RuneType;
import com.devmod.portal.block.CustomPortalBlock;
import com.devmod.portal.block.RuneBlock;
import com.devmod.portal.PortalBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.DevMod;

import javax.annotation.Nonnull;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Item used to ignite (create) custom portals within a frame.
 * Each igniter is associated with a specific portal color.
 */
public class PortalIgniterItem extends Item {
    private final PortalColor color;

    public PortalIgniterItem(PortalColor color, Properties properties) {
        super(properties);
        this.color = color;
    }

    public PortalColor getColor() {
        return color;
    }

    @Override
    @Nonnull
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        // Get position inside potential portal frame
        BlockPos interiorPos = clickedPos.relative(clickedFace);

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;

        // Try both horizontal axes
        PortalFrameDetector detector = new PortalFrameDetector();
        DevMod.LOGGER.info("[Portal] Igniter used at clicked={} face={} interior={}", clickedPos, clickedFace, interiorPos);
        DevMod.LOGGER.info("[Portal] Block at interior: {}", level.getBlockState(interiorPos));

        Optional<PortalFrameDetector.FrameResult> result = detector.detectFrame(level, interiorPos, Direction.Axis.X);
        DevMod.LOGGER.info("[Portal] X-axis detection result: {}", result.isPresent() ? "FOUND" : "empty");
        if (result.isEmpty()) {
            result = detector.detectFrame(level, interiorPos, Direction.Axis.Z);
            DevMod.LOGGER.info("[Portal] Z-axis detection result: {}", result.isPresent() ? "FOUND" : "empty");
        }

        if (result.isEmpty()) {
            DevMod.LOGGER.warn("[Portal] No valid frame detected from interior position {}", interiorPos);
            return InteractionResult.FAIL;
        }

        PortalFrameDetector.FrameResult frame = result.get();

        // Create portal blocks in the interior
        BlockState portalState = PortalBlocks.CUSTOM_PORTAL.get().defaultBlockState()
            .setValue(CustomPortalBlock.AXIS, frame.axis())
            .setValue(CustomPortalBlock.COLOR, color);

        for (BlockPos pos : frame.interiorPositions()) {
            level.setBlock(pos, portalState, 3);
        }

        // Register the portal
        PortalRegistry registry = PortalRegistry.get(serverLevel);
        ResourceLocation dimension = serverLevel.dimension().location();

        // Include player UUID as creator for privatePortals support
        UUID creatorId = context.getPlayer() != null ? context.getPlayer().getUUID() : null;
        PortalData portalData = PortalData.create(color, dimension, frame.centerPosition(), frame.frameBlockCount())
            .withCreator(creatorId);
        registry.register(portalData);

        // Notify player of creation (auto-linking disabled - use linker item to connect portals)
        if (context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(
                Component.translatable("message.devmod.portal.created"), true);
        }

        // Play ignition sound
        level.playSound(null, interiorPos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);

        // Damage the item
        ItemStack stack = context.getItemInHand();
        if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
            stack.hurtAndBreak(1, context.getPlayer(), context.getPlayer().getEquipmentSlotForItem(stack));
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.devmod.portal_igniter.color",
            Component.translatable("color.devmod." + color.getSerializedName()))
            .withStyle(style -> style.withColor(color.getColor())));
        tooltip.add(Component.translatable("item.devmod.portal_igniter.tooltip"));
    }

    /**
     * Scans for rune blocks around the portal frame and calculates combined effects.
     */
    private PortalRuneEffects scanForRuneEffects(Level level, PortalFrameDetector.FrameResult frame) {
        Set<RuneType> foundRunes = EnumSet.noneOf(RuneType.class);
        Set<BlockPos> visited = new HashSet<>();

        // Check frame blocks and their adjacent blocks for runes
        for (BlockPos framePos : frame.framePositions()) {
            checkForRune(level, framePos, foundRunes, visited);
        }

        return PortalRuneEffects.calculate(foundRunes);
    }

    /**
     * Checks if a position or its neighbors contain a rune block.
     */
    private void checkForRune(Level level, BlockPos pos, Set<RuneType> foundRunes, Set<BlockPos> visited) {
        if (visited.contains(pos)) {
            return;
        }
        visited.add(pos);

        // Check adjacent blocks for runes
        for (Direction dir : Direction.values()) {
            BlockPos adjacentPos = pos.relative(dir);
            if (!visited.contains(adjacentPos)) {
                BlockState adjacentState = level.getBlockState(adjacentPos);
                if (adjacentState.getBlock() instanceof RuneBlock runeBlock) {
                    visited.add(adjacentPos);
                    foundRunes.add(runeBlock.getRuneType());
                }
            }
        }
    }
}
