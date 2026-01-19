package com.devmod.foundry.client.renderer;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.block.ChannelConnectionState;
import com.devmod.foundry.block.FoundryChannelBlock;
import com.devmod.foundry.block.entity.FoundryChannelBlockEntity;
import com.devmod.foundry.quality.FoundryFluidQuality;

/**
 * Renders the molten fluid overlay for foundry channels.
 */
public class FoundryChannelRenderer implements BlockEntityRenderer<FoundryChannelBlockEntity> {
    private static final float CHANNEL_MIN = 3.0f / 16.0f;
    private static final float CHANNEL_MAX = 13.0f / 16.0f;
    private static final float ARM_MIN = 0.0f / 16.0f;
    private static final float ARM_MAX = 3.0f / 16.0f;
    private static final float ARM_OUT_MAX = 16.0f / 16.0f;
    private static final float FLUID_MIN_Y = 1.0f / 16.0f;
    private static final float FLUID_MAX_Y = 5.0f / 16.0f;
    private static final float INSET = 0.001f;

    // Direction indicator colors
    private static final float IN_RED = 0.2f, IN_GREEN = 0.6f, IN_BLUE = 1.0f;    // Blue for IN
    private static final float OUT_RED = 1.0f, OUT_GREEN = 0.6f, OUT_BLUE = 0.2f;  // Orange for OUT
    private static final float INDICATOR_ALPHA = 0.8f;
    private static final float INDICATOR_Y = 5.5f / 16.0f;

    public FoundryChannelRenderer(BlockEntityRendererProvider.Context context) {
        // No-op
    }

    @Override
    public void render(
        FoundryChannelBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        int amount = blockEntity.getBufferAmount();
        if (amount <= 0) {
            return;
        }

        FluidStack stack = blockEntity.getBuffer();
        if (stack.isEmpty()) {
            return;
        }

        float ratio = Math.min(1.0f, amount / (float) blockEntity.getMaxBuffer());
        float maxY = FLUID_MIN_Y + (FLUID_MAX_Y - FLUID_MIN_Y) * ratio;
        if (maxY <= FLUID_MIN_Y + INSET) {
            return;
        }

        IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = clientFluid.getStillTexture();
        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getModelManager()
            .getAtlas(InventoryMenu.BLOCK_ATLAS)
            .getSprite(stillTexture);

        int color = FoundryFluidQuality.getPurityTintColor(stack, clientFluid.getTintColor());
        float alpha = ((color >> 24) & 0xFF) / 255f;
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;
        if (alpha <= 0f) {
            alpha = 1f;
        }

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
        BlockState state = blockEntity.getBlockState();

        renderCuboid(
            poseStack, consumer, sprite,
            CHANNEL_MIN, FLUID_MIN_Y, CHANNEL_MIN,
            CHANNEL_MAX, maxY, CHANNEL_MAX,
            red, green, blue, alpha,
            packedLight, packedOverlay
        );

        if (state.getValue(FoundryChannelBlock.NORTH)) {
            renderCuboid(
                poseStack, consumer, sprite,
                CHANNEL_MIN, FLUID_MIN_Y, ARM_MIN,
                CHANNEL_MAX, maxY, ARM_MAX,
                red, green, blue, alpha,
                packedLight, packedOverlay
            );
        }
        if (state.getValue(FoundryChannelBlock.SOUTH)) {
            renderCuboid(
                poseStack, consumer, sprite,
                CHANNEL_MIN, FLUID_MIN_Y, CHANNEL_MAX,
                CHANNEL_MAX, maxY, ARM_OUT_MAX,
                red, green, blue, alpha,
                packedLight, packedOverlay
            );
        }
        if (state.getValue(FoundryChannelBlock.WEST)) {
            renderCuboid(
                poseStack, consumer, sprite,
                ARM_MIN, FLUID_MIN_Y, CHANNEL_MIN,
                ARM_MAX, maxY, CHANNEL_MAX,
                red, green, blue, alpha,
                packedLight, packedOverlay
            );
        }
        if (state.getValue(FoundryChannelBlock.EAST)) {
            renderCuboid(
                poseStack, consumer, sprite,
                CHANNEL_MAX, FLUID_MIN_Y, CHANNEL_MIN,
                ARM_OUT_MAX, maxY, CHANNEL_MAX,
                red, green, blue, alpha,
                packedLight, packedOverlay
            );
        }

        // Render direction indicators for tri-state connections
        renderDirectionIndicators(blockEntity, poseStack, bufferSource, packedLight, packedOverlay);
    }

    /**
     * Render small arrow indicators showing flow direction for each connected side.
     */
    private void renderDirectionIndicators(
        FoundryChannelBlockEntity blockEntity,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        BlockState state = blockEntity.getBlockState();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
        Matrix4f matrix = poseStack.last().pose();

        // Get white texture for solid color indicators
        TextureAtlasSprite whiteSprite = Minecraft.getInstance()
            .getModelManager()
            .getAtlas(InventoryMenu.BLOCK_ATLAS)
            .getSprite(ResourceLocation.withDefaultNamespace("block/white_concrete"));

        // Check each horizontal direction
        if (state.getValue(FoundryChannelBlock.NORTH)) {
            renderHorizontalIndicator(blockEntity, Direction.NORTH, matrix, consumer, whiteSprite, packedLight, packedOverlay);
        }
        if (state.getValue(FoundryChannelBlock.SOUTH)) {
            renderHorizontalIndicator(blockEntity, Direction.SOUTH, matrix, consumer, whiteSprite, packedLight, packedOverlay);
        }
        if (state.getValue(FoundryChannelBlock.WEST)) {
            renderHorizontalIndicator(blockEntity, Direction.WEST, matrix, consumer, whiteSprite, packedLight, packedOverlay);
        }
        if (state.getValue(FoundryChannelBlock.EAST)) {
            renderHorizontalIndicator(blockEntity, Direction.EAST, matrix, consumer, whiteSprite, packedLight, packedOverlay);
        }
    }

    private void renderHorizontalIndicator(
        FoundryChannelBlockEntity blockEntity,
        Direction direction,
        Matrix4f matrix,
        VertexConsumer consumer,
        TextureAtlasSprite sprite,
        int light,
        int overlay
    ) {
        ChannelConnectionState connectionState = blockEntity.getConnectionState(direction);
        if (connectionState == ChannelConnectionState.NONE) {
            return; // No indicator for closed connections
        }

        float red, green, blue;
        if (connectionState == ChannelConnectionState.IN) {
            red = IN_RED; green = IN_GREEN; blue = IN_BLUE;
        } else {
            red = OUT_RED; green = OUT_GREEN; blue = OUT_BLUE;
        }

        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        // Position arrow at edge of channel arm
        float edgeOffset = direction == Direction.NORTH || direction == Direction.SOUTH
            ? (direction == Direction.NORTH ? ARM_MIN + 0.5f/16f : ARM_OUT_MAX - 0.5f/16f)
            : (direction == Direction.WEST ? ARM_MIN + 0.5f/16f : ARM_OUT_MAX - 0.5f/16f);

        // Arrow pointing direction (IN points toward center, OUT points away)
        boolean pointToward = connectionState == ChannelConnectionState.IN;

        // Render a small triangle/arrow indicator on top of the channel
        renderArrowIndicator(matrix, consumer, sprite, direction, edgeOffset, pointToward,
            red, green, blue, INDICATOR_ALPHA, u0, v0, u1, v1, light, overlay);
    }

    private void renderArrowIndicator(
        Matrix4f matrix,
        VertexConsumer consumer,
        TextureAtlasSprite sprite,
        Direction direction,
        float edgePos,
        boolean pointToward,
        float red, float green, float blue, float alpha,
        float u0, float v0, float u1, float v1,
        int light, int overlay
    ) {
        float y = INDICATOR_Y;
        float arrowHalfWidth = 1.5f / 16.0f;
        float arrowLength = 2.0f / 16.0f;

        // Calculate arrow vertices based on direction
        float tipX, tipZ, base1X, base1Z, base2X, base2Z;
        float centerX = 0.5f;
        float centerZ = 0.5f;

        switch (direction) {
            case NORTH -> {
                float baseZ = pointToward ? edgePos + arrowLength : edgePos;
                float tipZ2 = pointToward ? edgePos : edgePos + arrowLength;
                tipX = centerX;
                tipZ = tipZ2;
                base1X = centerX - arrowHalfWidth;
                base1Z = baseZ;
                base2X = centerX + arrowHalfWidth;
                base2Z = baseZ;
            }
            case SOUTH -> {
                float baseZ = pointToward ? edgePos - arrowLength : edgePos;
                float tipZ2 = pointToward ? edgePos : edgePos - arrowLength;
                tipX = centerX;
                tipZ = tipZ2;
                base1X = centerX + arrowHalfWidth;
                base1Z = baseZ;
                base2X = centerX - arrowHalfWidth;
                base2Z = baseZ;
            }
            case WEST -> {
                float baseX = pointToward ? edgePos + arrowLength : edgePos;
                float tipX2 = pointToward ? edgePos : edgePos + arrowLength;
                tipX = tipX2;
                tipZ = centerZ;
                base1X = baseX;
                base1Z = centerZ + arrowHalfWidth;
                base2X = baseX;
                base2Z = centerZ - arrowHalfWidth;
            }
            case EAST -> {
                float baseX = pointToward ? edgePos - arrowLength : edgePos;
                float tipX2 = pointToward ? edgePos : edgePos - arrowLength;
                tipX = tipX2;
                tipZ = centerZ;
                base1X = baseX;
                base1Z = centerZ - arrowHalfWidth;
                base2X = baseX;
                base2Z = centerZ + arrowHalfWidth;
            }
            default -> { return; }
        }

        // Render triangle (arrow) on Y+ face
        float uMid = (u0 + u1) / 2;
        vertex(consumer, matrix, tipX, y, tipZ, uMid, v0, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
        vertex(consumer, matrix, base1X, y, base1Z, u0, v1, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
        vertex(consumer, matrix, base2X, y, base2Z, u1, v1, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
        // Duplicate last vertex to complete quad (degenerate triangle)
        vertex(consumer, matrix, base2X, y, base2Z, u1, v1, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
    }

    private static void renderCuboid(
        PoseStack poseStack,
        VertexConsumer consumer,
        TextureAtlasSprite sprite,
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ,
        float red,
        float green,
        float blue,
        float alpha,
        int light,
        int overlay
    ) {
        float x1 = minX + INSET;
        float y1 = minY + INSET;
        float z1 = minZ + INSET;
        float x2 = maxX - INSET;
        float y2 = maxY - INSET;
        float z2 = maxZ - INSET;
        if (x2 <= x1 || y2 <= y1 || z2 <= z1) {
            return;
        }

        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        Matrix4f matrix = poseStack.last().pose();

        // North face (z-)
        vertex(consumer, matrix, x1, y1, z1, u0, v1, red, green, blue, alpha, light, overlay, 0f, 0f, -1f);
        vertex(consumer, matrix, x2, y1, z1, u1, v1, red, green, blue, alpha, light, overlay, 0f, 0f, -1f);
        vertex(consumer, matrix, x2, y2, z1, u1, v0, red, green, blue, alpha, light, overlay, 0f, 0f, -1f);
        vertex(consumer, matrix, x1, y2, z1, u0, v0, red, green, blue, alpha, light, overlay, 0f, 0f, -1f);

        // South face (z+)
        vertex(consumer, matrix, x2, y1, z2, u1, v1, red, green, blue, alpha, light, overlay, 0f, 0f, 1f);
        vertex(consumer, matrix, x1, y1, z2, u0, v1, red, green, blue, alpha, light, overlay, 0f, 0f, 1f);
        vertex(consumer, matrix, x1, y2, z2, u0, v0, red, green, blue, alpha, light, overlay, 0f, 0f, 1f);
        vertex(consumer, matrix, x2, y2, z2, u1, v0, red, green, blue, alpha, light, overlay, 0f, 0f, 1f);

        // West face (x-)
        vertex(consumer, matrix, x1, y1, z2, u0, v1, red, green, blue, alpha, light, overlay, -1f, 0f, 0f);
        vertex(consumer, matrix, x1, y1, z1, u1, v1, red, green, blue, alpha, light, overlay, -1f, 0f, 0f);
        vertex(consumer, matrix, x1, y2, z1, u1, v0, red, green, blue, alpha, light, overlay, -1f, 0f, 0f);
        vertex(consumer, matrix, x1, y2, z2, u0, v0, red, green, blue, alpha, light, overlay, -1f, 0f, 0f);

        // East face (x+)
        vertex(consumer, matrix, x2, y1, z1, u0, v1, red, green, blue, alpha, light, overlay, 1f, 0f, 0f);
        vertex(consumer, matrix, x2, y1, z2, u1, v1, red, green, blue, alpha, light, overlay, 1f, 0f, 0f);
        vertex(consumer, matrix, x2, y2, z2, u1, v0, red, green, blue, alpha, light, overlay, 1f, 0f, 0f);
        vertex(consumer, matrix, x2, y2, z1, u0, v0, red, green, blue, alpha, light, overlay, 1f, 0f, 0f);

        // Up face (y+)
        vertex(consumer, matrix, x1, y2, z1, u0, v1, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
        vertex(consumer, matrix, x2, y2, z1, u1, v1, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
        vertex(consumer, matrix, x2, y2, z2, u1, v0, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
        vertex(consumer, matrix, x1, y2, z2, u0, v0, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);

        // Down face (y-)
        vertex(consumer, matrix, x1, y1, z2, u0, v1, red, green, blue, alpha, light, overlay, 0f, -1f, 0f);
        vertex(consumer, matrix, x2, y1, z2, u1, v1, red, green, blue, alpha, light, overlay, 0f, -1f, 0f);
        vertex(consumer, matrix, x2, y1, z1, u1, v0, red, green, blue, alpha, light, overlay, 0f, -1f, 0f);
        vertex(consumer, matrix, x1, y1, z1, u0, v0, red, green, blue, alpha, light, overlay, 0f, -1f, 0f);
    }

    private static void vertex(
        VertexConsumer consumer,
        Matrix4f matrix,
        float x,
        float y,
        float z,
        float u,
        float v,
        float red,
        float green,
        float blue,
        float alpha,
        int light,
        int overlay,
        float normalX,
        float normalY,
        float normalZ
    ) {
        consumer.addVertex(matrix, x, y, z)
            .setColor(red, green, blue, alpha)
            .setUv(u, v)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(normalX, normalY, normalZ);
    }
}
