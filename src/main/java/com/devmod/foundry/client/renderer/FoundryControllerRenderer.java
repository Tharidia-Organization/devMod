package com.devmod.foundry.client.renderer;

import java.util.List;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import com.devmod.foundry.block.entity.FoundryControllerBlockEntity;
import com.devmod.foundry.fluid.FoundryFluidTank;
import com.devmod.foundry.menu.FoundryControllerMenu;
import com.devmod.foundry.quality.FoundryFluidQuality;

/**
 * Renders the molten fluid and melting items inside the foundry multiblock.
 * Features:
 * - 3D fluid rendering (top + 4 sides)
 * - Smooth animated fluid level changes
 * - Multi-layer rendering for multiple fluid types
 * - Wave animation on fluid surface
 */
public class FoundryControllerRenderer implements BlockEntityRenderer<FoundryControllerBlockEntity> {
    private static final float INSET = 0.01f;
    private static final float ITEM_SCALE = 0.5f;
    private static final float ITEM_BOB_SPEED = 0.05f;
    private static final float ITEM_BOB_HEIGHT = 0.1f;
    private static final float WAVE_AMPLITUDE = 0.02f;

    public FoundryControllerRenderer(BlockEntityRendererProvider.Context context) {
        // No-op
    }

    @Override
    public void render(
        @Nonnull FoundryControllerBlockEntity blockEntity,
        float partialTick,
        @Nonnull PoseStack poseStack,
        @Nonnull MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        if (!blockEntity.isFormed()) {
            return;
        }

        BlockPos minPos = blockEntity.getClientMinPos();
        BlockPos maxPos = blockEntity.getClientMaxPos();
        if (minPos == null || maxPos == null) {
            return;
        }

        BlockPos controllerPos = blockEntity.getBlockPos();

        // Calculate interior bounds (excluding walls)
        float interiorMinX = minPos.getX() + 1 - controllerPos.getX();
        float interiorMinY = minPos.getY() + 1 - controllerPos.getY();
        float interiorMinZ = minPos.getZ() + 1 - controllerPos.getZ();
        float interiorMaxX = maxPos.getX() - controllerPos.getX();
        float interiorMaxY = maxPos.getY() + 1 - controllerPos.getY();
        float interiorMaxZ = maxPos.getZ() - controllerPos.getZ();

        // Tick client-side animation
        FoundryFluidTank tank = blockEntity.getMoltenTank();
        tank.tickClient();

        // Render molten fluid with 3D sides and multi-layer support
        renderMoltenFluid3D(blockEntity, poseStack, bufferSource, packedLight, packedOverlay,
            interiorMinX, interiorMinY, interiorMinZ, interiorMaxX, interiorMaxY, interiorMaxZ, partialTick);

        // Render melting items
        renderMeltingItems(blockEntity, poseStack, bufferSource, packedLight, packedOverlay,
            interiorMinX, interiorMinY, interiorMinZ, interiorMaxX, interiorMaxY, interiorMaxZ, partialTick);

        // Spawn steam/smoke particles above fluid surface
        spawnFluidParticles(controllerPos, tank, partialTick,
            interiorMinX, interiorMinY, interiorMinZ, interiorMaxX, interiorMaxY, interiorMaxZ);
    }

    /**
     * Spawns steam and smoke particles above the molten fluid surface.
     * Particles spawn randomly to avoid performance issues.
     */
    private void spawnFluidParticles(
        BlockPos controllerPos,
        FoundryFluidTank tank,
        float partialTick,
        float minX, float minY, float minZ,
        float maxX, float maxY, float maxZ
    ) {
        if (tank.isEmpty()) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        // Only spawn particles occasionally (roughly 1 in 10 frames)
        if (level.random.nextFloat() > 0.1f) {
            return;
        }

        // Calculate fluid surface height
        float fillRatio = tank.getRenderFillRatio(partialTick);
        float totalHeight = maxY - minY;
        float fluidSurfaceY = minY + (totalHeight * fillRatio);

        // World coordinates
        double worldX = controllerPos.getX();
        double worldY = controllerPos.getY();
        double worldZ = controllerPos.getZ();

        // Spawn 1-2 particles at random positions on the fluid surface
        int particleCount = level.random.nextInt(2) + 1;
        for (int i = 0; i < particleCount; i++) {
            double px = worldX + minX + level.random.nextFloat() * (maxX - minX);
            double py = worldY + fluidSurfaceY + 0.1;
            double pz = worldZ + minZ + level.random.nextFloat() * (maxZ - minZ);

            // Slow upward velocity
            double vx = (level.random.nextFloat() - 0.5) * 0.02;
            double vy = 0.02 + level.random.nextFloat() * 0.03;
            double vz = (level.random.nextFloat() - 0.5) * 0.02;

            // Mix of smoke and campfire cosy smoke for steam effect
            if (level.random.nextFloat() < 0.7f) {
                level.addParticle(ParticleTypes.SMOKE, px, py, pz, vx, vy, vz);
            } else {
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz, vx * 0.5, vy * 0.5, vz * 0.5);
            }
        }
    }

    private void renderMoltenFluid3D(
        FoundryControllerBlockEntity blockEntity,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay,
        float minX, float minY, float minZ,
        float maxX, float maxY, float maxZ,
        float partialTick
    ) {
        FoundryFluidTank tank = blockEntity.getMoltenTank();
        List<FluidStack> fluids = tank.getFluids();

        if (fluids.isEmpty() || tank.getCapacity() <= 0) {
            return;
        }

        // Get animation time for wave effects
        long gameTime = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0;
        float animTime = (gameTime + partialTick) * 0.02f;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        // Calculate total height available
        float totalHeight = maxY - minY;

        // Render each fluid layer from bottom to top
        float currentY = minY;
        int capacity = tank.getCapacity();

        for (int i = 0; i < fluids.size(); i++) {
            FluidStack fluidStack = fluids.get(i);
            if (fluidStack.isEmpty()) continue;

            // Calculate this fluid's height proportion
            float fluidRatio = (float) fluidStack.getAmount() / capacity;
            float fluidHeight = totalHeight * fluidRatio;

            if (fluidHeight < 0.001f) continue;

            float layerMinY = currentY;
            float layerMaxY = currentY + fluidHeight;

            // Get fluid texture and color
            IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(fluidStack.getFluid());
            ResourceLocation stillTexture = clientFluid.getStillTexture();
            TextureAtlasSprite sprite = Minecraft.getInstance()
                .getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(stillTexture);

            int color = FoundryFluidQuality.getPurityTintColor(fluidStack, clientFluid.getTintColor());
            float alpha = ((color >> 24) & 0xFF) / 255f;
            float red = ((color >> 16) & 0xFF) / 255f;
            float green = ((color >> 8) & 0xFF) / 255f;
            float blue = (color & 0xFF) / 255f;
            if (alpha <= 0f) {
                alpha = 0.9f;
            }

            // Is this the top layer?
            boolean isTopLayer = (i == fluids.size() - 1);

            // Render this fluid layer
            renderFluidLayer(
                consumer, matrix, pose, sprite,
                minX + INSET, layerMinY, minZ + INSET,
                maxX - INSET, layerMaxY, maxZ - INSET,
                red, green, blue, alpha,
                packedLight, packedOverlay,
                animTime, isTopLayer
            );

            currentY = layerMaxY;
        }
    }

    private void renderFluidLayer(
        VertexConsumer consumer,
        Matrix4f matrix,
        PoseStack.Pose pose,
        TextureAtlasSprite sprite,
        float minX, float minY, float minZ,
        float maxX, float maxY, float maxZ,
        float red, float green, float blue, float alpha,
        int light, int overlay,
        float animTime,
        boolean isTopLayer
    ) {
        // Get sprite UV
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        // Calculate wave offsets for top surface (only for top layer)
        float wave1 = isTopLayer ? (float) Math.sin(animTime * 2.0f) * WAVE_AMPLITUDE : 0f;
        float wave2 = isTopLayer ? (float) Math.sin(animTime * 2.5f + 1.0f) * WAVE_AMPLITUDE * 0.75f : 0f;
        float wave3 = isTopLayer ? (float) Math.sin(animTime * 1.5f + 2.0f) * WAVE_AMPLITUDE * 0.5f : 0f;
        float wave4 = isTopLayer ? (float) Math.sin(animTime * 3.0f + 0.5f) * WAVE_AMPLITUDE * 0.6f : 0f;

        // Top face (with wave animation for top layer)
        if (isTopLayer) {
            vertex(consumer, matrix, pose, minX, maxY + wave1, minZ, u0, v0, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
            vertex(consumer, matrix, pose, minX, maxY + wave2, maxZ, u0, v1, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
            vertex(consumer, matrix, pose, maxX, maxY + wave3, maxZ, u1, v1, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
            vertex(consumer, matrix, pose, maxX, maxY + wave4, minZ, u1, v0, red, green, blue, alpha, light, overlay, 0f, 1f, 0f);
        }

        // Calculate UV scaling for sides based on height
        float height = maxY - minY;
        float vScale = Math.min(1.0f, height);
        float sideV1 = v0 + (v1 - v0) * vScale;

        // North face (facing -Z)
        vertex(consumer, matrix, pose, minX, minY, minZ, u0, v0, red, green, blue, alpha, light, overlay, 0f, 0f, -1f);
        vertex(consumer, matrix, pose, maxX, minY, minZ, u1, v0, red, green, blue, alpha, light, overlay, 0f, 0f, -1f);
        vertex(consumer, matrix, pose, maxX, maxY + wave4, minZ, u1, sideV1, red, green, blue, alpha, light, overlay, 0f, 0f, -1f);
        vertex(consumer, matrix, pose, minX, maxY + wave1, minZ, u0, sideV1, red, green, blue, alpha, light, overlay, 0f, 0f, -1f);

        // South face (facing +Z)
        vertex(consumer, matrix, pose, maxX, minY, maxZ, u0, v0, red, green, blue, alpha, light, overlay, 0f, 0f, 1f);
        vertex(consumer, matrix, pose, minX, minY, maxZ, u1, v0, red, green, blue, alpha, light, overlay, 0f, 0f, 1f);
        vertex(consumer, matrix, pose, minX, maxY + wave2, maxZ, u1, sideV1, red, green, blue, alpha, light, overlay, 0f, 0f, 1f);
        vertex(consumer, matrix, pose, maxX, maxY + wave3, maxZ, u0, sideV1, red, green, blue, alpha, light, overlay, 0f, 0f, 1f);

        // West face (facing -X)
        vertex(consumer, matrix, pose, minX, minY, maxZ, u0, v0, red, green, blue, alpha, light, overlay, -1f, 0f, 0f);
        vertex(consumer, matrix, pose, minX, minY, minZ, u1, v0, red, green, blue, alpha, light, overlay, -1f, 0f, 0f);
        vertex(consumer, matrix, pose, minX, maxY + wave1, minZ, u1, sideV1, red, green, blue, alpha, light, overlay, -1f, 0f, 0f);
        vertex(consumer, matrix, pose, minX, maxY + wave2, maxZ, u0, sideV1, red, green, blue, alpha, light, overlay, -1f, 0f, 0f);

        // East face (facing +X)
        vertex(consumer, matrix, pose, maxX, minY, minZ, u0, v0, red, green, blue, alpha, light, overlay, 1f, 0f, 0f);
        vertex(consumer, matrix, pose, maxX, minY, maxZ, u1, v0, red, green, blue, alpha, light, overlay, 1f, 0f, 0f);
        vertex(consumer, matrix, pose, maxX, maxY + wave3, maxZ, u1, sideV1, red, green, blue, alpha, light, overlay, 1f, 0f, 0f);
        vertex(consumer, matrix, pose, maxX, maxY + wave4, minZ, u0, sideV1, red, green, blue, alpha, light, overlay, 1f, 0f, 0f);
    }

    private void renderMeltingItems(
        FoundryControllerBlockEntity blockEntity,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay,
        float minX, float minY, float minZ,
        float maxX, float maxY, float maxZ,
        float partialTick
    ) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        float centerX = (minX + maxX) / 2f;
        float centerZ = (minZ + maxZ) / 2f;

        // Calculate fluid surface height using animated ratio
        FoundryFluidTank tank = blockEntity.getMoltenTank();
        float fillRatio = tank.getRenderFillRatio(partialTick);
        float fluidSurfaceY = minY + (maxY - minY) * fillRatio;

        // Get game time for animation
        long gameTime = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0;
        float bobOffset = (float) Math.sin((gameTime + partialTick) * ITEM_BOB_SPEED) * ITEM_BOB_HEIGHT;

        // Render each input slot item
        for (int i = 0; i < FoundryControllerMenu.SLOT_INPUT_COUNT; i++) {
            ItemStack stack = blockEntity.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }

            // Position items in a grid pattern above the fluid
            float offsetX = (i % 2 == 0) ? -0.3f : 0.3f;
            float offsetZ = (i < 2) ? -0.3f : 0.3f;

            float itemX = centerX + offsetX;
            float itemZ = centerZ + offsetZ;
            float itemY = Math.max(fluidSurfaceY + 0.3f, minY + 0.5f) + bobOffset;

            poseStack.pushPose();
            poseStack.translate(itemX, itemY, itemZ);
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);

            // Rotate item slowly
            float rotation = (gameTime + partialTick) * 2f + i * 90f;
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotation));

            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getLevel(), 0);

            poseStack.popPose();
        }
    }

    private static void vertex(
        VertexConsumer consumer,
        Matrix4f matrix,
        PoseStack.Pose pose,
        float x, float y, float z,
        float u, float v,
        float red, float green, float blue, float alpha,
        int light, int overlay,
        float normalX, float normalY, float normalZ
    ) {
        consumer.addVertex(matrix, x, y, z)
            .setColor(red, green, blue, alpha)
            .setUv(u, v)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose, normalX, normalY, normalZ);
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull FoundryControllerBlockEntity blockEntity) {
        return true; // Multiblock can extend beyond single block
    }

    @Override
    public int getViewDistance() {
        return 64; // Visible from further away
    }
}
