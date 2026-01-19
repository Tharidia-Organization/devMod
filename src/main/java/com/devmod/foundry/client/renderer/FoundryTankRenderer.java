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

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.block.entity.FoundryTankBlockEntity;

/**
 * Renders the molten fluid overlay for foundry tanks.
 */
public class FoundryTankRenderer implements BlockEntityRenderer<FoundryTankBlockEntity> {
    private static final float TANK_MIN = 2.0f / 16.0f;
    private static final float TANK_MAX = 14.0f / 16.0f;
    private static final float FLUID_MIN_Y = 2.0f / 16.0f;
    private static final float FLUID_MAX_Y = 14.0f / 16.0f;
    private static final float INSET = 0.001f;

    public FoundryTankRenderer(BlockEntityRendererProvider.Context context) {
        // No-op
    }

    @Override
    public void render(
        FoundryTankBlockEntity blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        int capacity = blockEntity.getDisplayCapacity();
        if (capacity <= 0) {
            return;
        }
        int amount = blockEntity.getDisplayAmount();
        if (amount <= 0) {
            return;
        }

        FluidStack stack = blockEntity.getDisplayFluid();
        if (stack.isEmpty()) {
            return;
        }

        float ratio = Math.min(1.0f, amount / (float) capacity);
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

        renderCuboid(
            poseStack, consumer, sprite,
            TANK_MIN, FLUID_MIN_Y, TANK_MIN,
            TANK_MAX, maxY, TANK_MAX,
            red, green, blue, alpha,
            packedLight, packedOverlay
        );
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

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        // North face (z-)
        vertex(consumer, matrix, pose, x1, y1, z1, u0, v1, red, green, blue, alpha, light, overlay, 0f, 0f, -1f);
        vertex(consumer, matrix, pose, x2, y1, z1, u1, v1, red, green, blue, alpha, light, overlay, 0f, 0f, -1f);
        vertex(consumer, matrix, pose, x2, y2, z1, u1, v0, red, green, blue, alpha, light, overlay, 0f, 0f, -1f);
        vertex(consumer, matrix, pose, x1, y2, z1, u0, v0, red, green, blue, alpha, light, overlay, 0f, 0f, -1f);

        // South face (z+)
        vertex(consumer, matrix, pose, x2, y1, z2, u1, v1, red, green, blue, alpha, light, overlay, 0f, 0f, 1f);
        vertex(consumer, matrix, pose, x1, y1, z2, u0, v1, red, green, blue, alpha, light, overlay, 0f, 0f, 1f);
        vertex(consumer, matrix, pose, x1, y2, z2, u0, v0, red, green, blue, alpha, light, overlay, 0f, 0f, 1f);
        vertex(consumer, matrix, pose, x2, y2, z2, u1, v0, red, green, blue, alpha, light, overlay, 0f, 0f, 1f);

        // West face (x-)
        vertex(consumer, matrix, pose, x1, y1, z2, u0, v1, red, green, blue, alpha, light, overlay, -1f, 0f, 0f);
        vertex(consumer, matrix, pose, x1, y1, z1, u1, v1, red, green, blue, alpha, light, overlay, -1f, 0f, 0f);
        vertex(consumer, matrix, pose, x1, y2, z1, u1, v0, red, green, blue, alpha, light, overlay, -1f, 0f, 0f);
        vertex(consumer, matrix, pose, x1, y2, z2, u0, v0, red, green, blue, alpha, light, overlay, -1f, 0f, 0f);

        // East face (x+)
        vertex(consumer, matrix, pose, x2, y1, z1, u0, v1, red, green, blue, alpha, light, overlay, 1f, 0f, 0f);
        vertex(consumer, matrix, pose, x2, y1, z2, u1, v1, red, green, blue, alpha, light, overlay, 1f, 0f, 0f);
        vertex(consumer, matrix, pose, x2, y2, z2, u1, v0, red, green, blue, alpha, light, overlay, 1f, 0f, 0f);
        vertex(consumer, matrix, pose, x2, y2, z1, u0, v0, red, green, blue, alpha, light, overlay, 1f, 0f, 0f);

        // Up face (y+)
        vertex(consumer, matrix, pose, x1, y2, z1, u0, v1, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
        vertex(consumer, matrix, pose, x2, y2, z1, u1, v1, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
        vertex(consumer, matrix, pose, x2, y2, z2, u1, v0, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
        vertex(consumer, matrix, pose, x1, y2, z2, u0, v0, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);

        // Down face (y-)
        vertex(consumer, matrix, pose, x1, y1, z2, u0, v1, red, green, blue, alpha, light, overlay, 0f, -1f, 0f);
        vertex(consumer, matrix, pose, x2, y1, z2, u1, v1, red, green, blue, alpha, light, overlay, 0f, -1f, 0f);
        vertex(consumer, matrix, pose, x2, y1, z1, u1, v0, red, green, blue, alpha, light, overlay, 0f, -1f, 0f);
        vertex(consumer, matrix, pose, x1, y1, z1, u0, v0, red, green, blue, alpha, light, overlay, 0f, -1f, 0f);
    }

    private static void vertex(
        VertexConsumer consumer,
        Matrix4f matrix,
        PoseStack.Pose pose,
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
            .setNormal(pose, normalX, normalY, normalZ);
    }
}
