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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.clone.block.entity.CentrifugeBlockEntity;
import com.devmod.clone.client.model.CentrifugeModel;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * GeckoLib renderer for the Centrifuge block.
 * Renders the animated model and items being processed in the drum.
 *
 * <p>Coordinate System Notes (GeckoLib postRender):
 * <ul>
 *   <li>Origin is at block/model CENTER, not corner</li>
 *   <li>X and Z axes are INVERTED compared to Minecraft world coordinates</li>
 *   <li>FACING direction mapping: NORTH→-Z, SOUTH→+Z, EAST→-X, WEST→+X</li>
 * </ul>
 */
public class CentrifugeRenderer extends GeoBlockRenderer<CentrifugeBlockEntity> {

    // === Rendering Constants ===

    /**
     * Height of drum center in block units.
     * Drum spans Y=7-13 in model (6 units), center at Y=10.
     * Position = 10 / 16 = 0.625 blocks
     */
    private static final float DRUM_CENTER_HEIGHT = 0.625f;

    /** Scale for item being processed */
    private static final float PROCESSING_SCALE = 0.3f;

    /** Spin rate for processing item (degrees per tick) */
    private static final float SPIN_RATE = 18f;

    /** Vertical bob amplitude */
    private static final float BOB_AMPLITUDE = 0.02f;

    /** Vertical bob speed */
    private static final float BOB_SPEED = 0.08f;

    public CentrifugeRenderer() {
        super(new CentrifugeModel());
    }

    @Override
    public void postRender(
            @Nonnull PoseStack poseStack,
            @Nonnull CentrifugeBlockEntity entity,
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

        // Render processing item inside the drum
        renderProcessingItem(poseStack, entity, bufferSource, packedLight, packedOverlay, partialTick);
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
     * Render the item being processed inside the centrifuge drum.
     */
    private void renderProcessingItem(
            PoseStack poseStack,
            CentrifugeBlockEntity entity,
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

        // Calculate animation time
        float time = (level.getGameTime() + partialTick);

        // Position item in the center of the drum (GeckoLib origin is at block center)
        float bobOffset = (float) Math.sin(time * BOB_SPEED) * BOB_AMPLITUDE;
        poseStack.translate(0, DRUM_CENTER_HEIGHT + bobOffset, 0);

        // Rotate based on block facing
        poseStack.mulPose(Axis.YP.rotationDegrees(getFacingRotation(facing)));

        // Spin the item for centrifuge effect
        float spin = time * SPIN_RATE;
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));

        // Scale down to fit inside the drum
        poseStack.scale(PROCESSING_SCALE, PROCESSING_SCALE, PROCESSING_SCALE);

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
}
