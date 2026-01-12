package com.devmod.clone.client.renderer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.clone.block.entity.ClonePulverizerBlockEntity;
import com.devmod.clone.client.model.ClonePulverizerModel;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * GeckoLib renderer for the Clone Pulverizer block.
 * Renders the animated model and items being processed between the rollers.
 */
public class ClonePulverizerRenderer extends GeoBlockRenderer<ClonePulverizerBlockEntity> {

    public ClonePulverizerRenderer() {
        super(new ClonePulverizerModel());
    }

    @Override
    public void postRender(
            @Nonnull PoseStack poseStack,
            @Nonnull ClonePulverizerBlockEntity entity,
            @Nonnull BakedGeoModel model,
            @Nonnull MultiBufferSource bufferSource,
            @Nullable com.mojang.blaze3d.vertex.VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int colour
    ) {
        super.postRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);

        // Only render items if not a re-render pass
        if (isReRender) {
            return;
        }

        // Render processing item between rollers
        renderProcessingItem(poseStack, entity, bufferSource, packedLight, packedOverlay, partialTick);

        // Render output item on discharge tray
        renderOutputItem(poseStack, entity, bufferSource, packedLight, packedOverlay, partialTick);
    }

    /**
     * Render the item being crushed between the rollers.
     */
    private void renderProcessingItem(
            PoseStack poseStack,
            ClonePulverizerBlockEntity entity,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            float partialTick
    ) {
        ItemStack processingItem = entity.getProcessingItem();
        if (processingItem.isEmpty()) {
            return;
        }

        poseStack.pushPose();

        // Get facing direction for proper rotation
        BlockState state = entity.getBlockState();
        Direction facing = Direction.NORTH;
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            facing = state.getValue(HorizontalDirectionalBlock.FACING);
        }

        // Position item between the rollers (center of block, at roller height)
        // The rollers are at Y=9 in the model (9/16 = 0.5625)
        poseStack.translate(0.5, 0.5625, 0.5);

        // Rotate based on block facing
        float rotation = switch (facing) {
            case SOUTH -> 180f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 0f; // NORTH
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));

        // Spin the item based on game time for visual effect
        if (entity.getLevel() != null) {
            float spin = (entity.getLevel().getGameTime() + partialTick) * 10f;
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        }

        // Squeeze the item based on processing progress
        float progress = entity.getProgressPercent();
        float squeeze = 1.0f - (progress * 0.5f); // Squeeze to 50% at max progress
        float scale = 0.4f; // Base item scale
        poseStack.scale(scale, scale * squeeze, scale);

        // Render the item
        Minecraft.getInstance().getItemRenderer().renderStatic(
                processingItem,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                entity.getLevel(),
                0
        );

        poseStack.popPose();
    }

    /**
     * Render the output item sitting on the discharge tray.
     * The discharge tray position depends on block facing.
     */
    private void renderOutputItem(
            PoseStack poseStack,
            ClonePulverizerBlockEntity entity,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            float partialTick
    ) {
        ItemStack outputItem = entity.getOutputItem();
        if (outputItem.isEmpty()) {
            return;
        }

        poseStack.pushPose();

        // Get facing direction
        BlockState state = entity.getBlockState();
        Direction facing = Direction.NORTH;
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            facing = state.getValue(HorizontalDirectionalBlock.FACING);
        }

        // Origin is at block/model center. Discharge tray is at Z=+0.53 from center.
        // Need to offset based on facing direction.
        double trayOffset = 0.53; // 8.5/16 model units from center
        double offsetX = 0;
        double offsetZ = 0;

        switch (facing) {
            case NORTH -> offsetZ = -trayOffset;  // Discharge toward -Z
            case SOUTH -> offsetZ = trayOffset;   // Discharge toward +Z
            case EAST -> offsetX = -trayOffset;   // Discharge toward -X
            case WEST -> offsetX = trayOffset;    // Discharge toward +X
        }

        poseStack.translate(offsetX, 0.1, offsetZ);

        // Scale down the item
        float scale = 0.35f;
        poseStack.scale(scale, scale, scale);

        // Render the output item
        Minecraft.getInstance().getItemRenderer().renderStatic(
                outputItem,
                ItemDisplayContext.GROUND,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                entity.getLevel(),
                0
        );

        poseStack.popPose();
    }
}
