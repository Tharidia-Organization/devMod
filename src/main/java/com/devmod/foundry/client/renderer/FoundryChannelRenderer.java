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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.block.FoundryChannelBlock;
import com.devmod.foundry.block.entity.FoundryChannelBlockEntity;

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

        int color = clientFluid.getTintColor();
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
