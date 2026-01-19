package com.devmod.clone.client;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.DevMod;
import com.devmod.clone.block.entity.NeurocellBlockEntity;
import com.devmod.clone.block.entity.NeurocellItemBlockEntity;
import com.devmod.clone.block.entity.NeurocellLBlockEntity;
import com.devmod.clone.block.entity.NeurocellMannequinBlockEntity;
import com.devmod.clone.network.MannequinRotationPayload;

/**
 * Client-side input handler for Neurocell rotation.
 *
 * <p>When the player scrolls while sneaking and looking at any Neurocell type in MANUAL mode,
 * this handler sends a rotation update packet to the server.
 *
 * <p>Supports: NeurocellMannequin, Neurocell, NeurocellL, NeurocellItem
 *
 * <p>Scroll up = rotate clockwise, scroll down = rotate counter-clockwise
 * 15 degrees per scroll notch.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class MannequinInputHandler {

    /** Degrees of rotation per scroll notch */
    private static final float ROTATION_PER_SCROLL = 15.0f;

    /** Maximum reach distance for neurocell interaction */
    private static final double MAX_REACH = 6.0;

    private MannequinInputHandler() {}

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();

        // Skip if not in game or in a GUI
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.screen != null) {
            return;
        }

        // Skip if player is not sneaking (shift held)
        if (!player.isShiftKeyDown()) {
            return;
        }

        // Only process vertical scroll
        double scrollY = event.getScrollDeltaY();
        if (scrollY == 0) {
            return;
        }

        // Raycast to find what block the player is looking at
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 endPos = eyePos.add(lookVec.x * MAX_REACH, lookVec.y * MAX_REACH, lookVec.z * MAX_REACH);

        ClipContext clipContext = new ClipContext(
            eyePos, Objects.requireNonNull(endPos),
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            player
        );

        HitResult hitResult = level.clip(clipContext);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hitResult;
        BlockPos hitPos = blockHit.getBlockPos();

        // Try to find a Neurocell block entity
        BlockPos neurocellPos = findNeurocellPos(level, hitPos);
        if (neurocellPos == null) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(neurocellPos);
        if (!isNeurocellInManualMode(blockEntity)) {
            return;
        }

        // Calculate rotation delta (scroll up = clockwise = positive)
        float rotationDelta = (float) scrollY * ROTATION_PER_SCROLL;

        // Send packet to server
        PacketDistributor.sendToServer(
            new MannequinRotationPayload(neurocellPos, rotationDelta)
        );

        // Cancel the event to prevent hotbar scrolling
        event.setCanceled(true);
    }

    /**
     * Find the block entity position for a Neurocell, checking both hit pos and below.
     * Returns null if no Neurocell found.
     */
    @Nullable
    private static BlockPos findNeurocellPos(ClientLevel level, BlockPos hitPos) {
        // Check direct hit position
        BlockEntity blockEntity = level.getBlockEntity(Objects.requireNonNull(hitPos));
        if (isNeurocellType(blockEntity)) {
            return hitPos;
        }

        // If we hit the upper half, check below (for double-height blocks)
        BlockPos belowPos = Objects.requireNonNull(hitPos.below());
        blockEntity = level.getBlockEntity(belowPos);
        if (isNeurocellType(blockEntity)) {
            return belowPos;
        }

        return null;
    }

    /**
     * Check if a block entity is any type of Neurocell.
     */
    private static boolean isNeurocellType(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof NeurocellMannequinBlockEntity
            || blockEntity instanceof NeurocellBlockEntity
            || blockEntity instanceof NeurocellLBlockEntity
            || blockEntity instanceof NeurocellItemBlockEntity;
    }

    /**
     * Check if the Neurocell is in MANUAL rotation mode.
     */
    private static boolean isNeurocellInManualMode(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof NeurocellMannequinBlockEntity mannequin) {
            return mannequin.getRotationMode() == NeurocellMannequinBlockEntity.RotationMode.MANUAL;
        }
        if (blockEntity instanceof NeurocellBlockEntity neurocell) {
            return neurocell.getRotationMode() == NeurocellBlockEntity.RotationMode.MANUAL;
        }
        if (blockEntity instanceof NeurocellLBlockEntity neurocellL) {
            return neurocellL.getRotationMode() == NeurocellLBlockEntity.RotationMode.MANUAL;
        }
        if (blockEntity instanceof NeurocellItemBlockEntity neurocellItem) {
            return neurocellItem.getRotationMode() == NeurocellItemBlockEntity.RotationMode.MANUAL;
        }
        return false;
    }
}
