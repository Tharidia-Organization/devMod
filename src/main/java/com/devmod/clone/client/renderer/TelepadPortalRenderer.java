package com.devmod.clone.client.renderer;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.DevMod;
import com.devmod.clone.block.TelepadBlock;
import com.devmod.clone.block.entity.TelepadBlockEntity;

@OnlyIn(Dist.CLIENT)
public class TelepadPortalRenderer implements BlockEntityRenderer<TelepadBlockEntity> {
    private static boolean loggedRender = false;
    public TelepadPortalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TelepadBlockEntity be, float partialTick, com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }

        BlockState state = be.getBlockState();
        boolean active = state.getValue(TelepadBlock.ACTIVE);
        float charge = be.getChargeProgress(partialTick);

        if (!active && charge <= 0.01f) {
            charge = 0.0f;
        }

        float idleBoost = active ? 1.0f : 0.75f;
        float intensity = Mth.clamp(idleBoost * (0.45f + 0.65f * charge), 0.0f, 1.5f);
        if (!loggedRender) {
            DevMod.LOGGER.info("[TelepadPortalRenderer] render tick active={} charge={} intensity={}",
                active, charge, intensity);
            loggedRender = true;
        }
        TelepadEffekseerController.update(be, intensity);
    }
}
