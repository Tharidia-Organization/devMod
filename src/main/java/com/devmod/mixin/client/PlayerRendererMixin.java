package com.devmod.mixin.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.devmod.clone.block.TelepadBlock;
import com.devmod.clone.block.entity.TelepadBlockEntity;
import com.devmod.clone.client.renderer.TelepadDepthRenderer;
import com.devmod.clone.client.renderer.TelepadPortalGeometry;

/**
 * Cancels player rendering when the player is fully behind an active telepad portal.
 */
@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    @SuppressWarnings({"UnusedMethod", "UnusedVariable"})
    private void devmod$checkPlayerOcclusion(AbstractClientPlayer player, float entityYaw, float partialTick,
                                             PoseStack poseStack, MultiBufferSource buffer,
                                             int packedLight, CallbackInfo ci) {
        if (shouldHidePlayer(player, partialTick)) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean shouldHidePlayer(AbstractClientPlayer player, float partialTick) {
        Set<BlockPos> telepads = TelepadDepthRenderer.getActiveTelepadPositions();
        if (telepads.isEmpty()) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return false;
        }

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 playerCenter = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);

        List<BlockPos> stale = null;
        for (BlockPos pos : telepads) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof TelepadBlockEntity telepad)) {
                if (stale == null) {
                    stale = new ArrayList<>();
                }
                stale.add(pos);
                continue;
            }

            BlockState state = telepad.getBlockState();
            if (!state.hasProperty(TelepadBlock.ACTIVE)) {
                continue;
            }

            boolean active = state.getValue(TelepadBlock.ACTIVE);
            float charge = telepad.getChargeProgress(partialTick);
            if (!active && charge <= 0.01f) {
                continue;
            }

            Direction facing = state.getValue(TelepadBlock.FACING);
            Vec3 center = TelepadPortalGeometry.getCenter(pos);
            Vec3 toCamera = cameraPos.subtract(center);
            Vec3 toPlayer = playerCenter.subtract(center);

            Vec3 normal = TelepadPortalGeometry.getNormal(facing);
            double cameraSide = toCamera.dot(normal);
            double playerSide = toPlayer.dot(normal);
            if (cameraSide == 0.0 || playerSide == 0.0) {
                continue;
            }
            if (cameraSide * playerSide >= 0.0) {
                continue;
            }

            Vec3 localPlayer = TelepadPortalGeometry.toLocal(toPlayer, facing);
            if (TelepadPortalGeometry.isInsidePortalEllipse(localPlayer)) {
                return true;
            }
        }

        if (stale != null) {
            for (BlockPos pos : stale) {
                TelepadDepthRenderer.unregisterTelepad(pos);
            }
        }

        return false;
    }
}
