package com.devmod.clone.client.renderer;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.clone.block.entity.ClonePulverizerBlockEntity;
import com.devmod.clone.client.model.ClonePulverizerModel;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * GeckoLib renderer for the Clone Pulverizer block.
 * Renders the animated model and items being processed between the rollers.
 *
 * <p>Coordinate System Notes (GeckoLib postRender):
 * <ul>
 *   <li>Origin is at block/model CENTER, not corner</li>
 *   <li>X and Z axes are INVERTED compared to Minecraft world coordinates</li>
 *   <li>FACING direction mapping: NORTH→-Z, SOUTH→+Z, EAST→-X, WEST→+X</li>
 * </ul>
 */
public class ClonePulverizerRenderer extends GeoBlockRenderer<ClonePulverizerBlockEntity> {

    // === Rendering Constants ===

    /** Height of rollers in model units (9/16 blocks) */
    private static final float ROLLER_HEIGHT = 0.5625f;

    /** Scale for item being processed */
    private static final float PROCESSING_SCALE = 0.4f;

    /** Spin rate for processing item (degrees per tick) */
    private static final float SPIN_RATE = 10f;

    /** Maximum squeeze factor (0.5 = squeeze to 50% at max progress) */
    private static final float MAX_SQUEEZE = 0.5f;

    /** Offset to discharge tray (GeckoLib coordinates - inverted) */
    private static final float TRAY_OFFSET = 0.53f;

    /** Height of output item on tray */
    private static final float OUTPUT_HEIGHT = 0.15f;

    /** Scale for output item (Create-style smaller display) */
    private static final float OUTPUT_SCALE = 0.5f;

    public ClonePulverizerRenderer() {
        super(new ClonePulverizerModel());
    }

    @Override
    public void preRender(
            @Nonnull PoseStack poseStack,
            @Nonnull ClonePulverizerBlockEntity entity,
            @Nonnull BakedGeoModel model,
            @Nullable MultiBufferSource bufferSource,
            @Nullable com.mojang.blaze3d.vertex.VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            int colour
    ) {
        super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);

        // Hide roller bones if grinders are not installed
        GeoBone leftRoller = model.getBone("roller_left").orElse(null);
        GeoBone rightRoller = model.getBone("roller_right").orElse(null);

        if (leftRoller != null) {
            leftRoller.setHidden(!entity.hasLeftGrinder());
        }
        if (rightRoller != null) {
            rightRoller.setHidden(!entity.hasRightGrinder());
        }
    }

    @Override
    public void postRender(
            @Nonnull PoseStack poseStack,
            @Nonnull ClonePulverizerBlockEntity entity,
            @Nonnull BakedGeoModel model,
            @Nullable MultiBufferSource bufferSource,
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

        if (bufferSource == null) {
            return;
        }

        // Render processing item between rollers
        renderProcessingItem(poseStack, entity, bufferSource, packedLight, packedOverlay, partialTick);

        // Render output item on discharge tray (Create-style visual display)
        renderOutputItem(poseStack, entity, bufferSource, packedLight, packedOverlay);
    }

    /**
     * Get the facing direction from block state, defaulting to NORTH.
     */
    private Direction getFacing(BlockState state) {
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return state.getValue(HorizontalDirectionalBlock.FACING);
        }
        return Direction.NORTH;
    }


    /**
     * Get Y-axis rotation for facing direction.
     */
    private float getFacingRotation(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 0f; // NORTH
        };
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

        Level level = entity.getLevel();
        if (level == null) {
            return;
        }

        poseStack.pushPose();

        Direction facing = getFacing(entity.getBlockState());

        // Position item between the rollers (GeckoLib origin is at block center)
        poseStack.translate(0, ROLLER_HEIGHT, 0);

        // Rotate based on block facing
        poseStack.mulPose(Axis.YP.rotationDegrees(getFacingRotation(facing)));

        // Spin the item based on game time for visual effect
        float spin = (level.getGameTime() + partialTick) * SPIN_RATE;
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));

        // Squeeze the item based on processing progress
        float progress = entity.getProgressPercent();
        float squeeze = 1.0f - (progress * MAX_SQUEEZE);
        poseStack.scale(PROCESSING_SCALE, PROCESSING_SCALE * squeeze, PROCESSING_SCALE);

        // Render the item
        Minecraft.getInstance().getItemRenderer().renderStatic(
                processingItem,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                level,
                0
        );

        poseStack.popPose();
    }

    /**
     * Render the output item on the discharge tray.
     * Create-style: smaller scale, fixed position, no physics.
     *
     * <p>GeckoLib coordinate system - axes are INVERTED:
     * NORTH→-Z, SOUTH→+Z, EAST→-X, WEST→+X
     */
    private void renderOutputItem(
            PoseStack poseStack,
            ClonePulverizerBlockEntity entity,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        ItemStack outputItem = entity.getOutputItem();
        if (outputItem.isEmpty()) {
            return;
        }

        Level level = entity.getLevel();
        if (level == null) {
            return;
        }

        poseStack.pushPose();

        Direction facing = getFacing(entity.getBlockState());

        // GeckoLib origin is at block center
        // Position on discharge tray with INVERTED coordinates
        float offsetX = 0;
        float offsetZ = 0;
        switch (facing) {
            case NORTH -> offsetZ = -TRAY_OFFSET;  // Inverted: back is -Z
            case SOUTH -> offsetZ = TRAY_OFFSET;
            case EAST -> offsetX = -TRAY_OFFSET;   // Inverted: back is -X
            case WEST -> offsetX = TRAY_OFFSET;
            default -> { }
        }

        poseStack.translate(offsetX, OUTPUT_HEIGHT, offsetZ);

        // Scale down for Create-style display
        poseStack.scale(OUTPUT_SCALE, OUTPUT_SCALE, OUTPUT_SCALE);

        // Render the item
        Minecraft.getInstance().getItemRenderer().renderStatic(
                outputItem,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                level,
                0
        );

        poseStack.popPose();
    }

}
