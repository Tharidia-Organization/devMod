package com.devmod.clone.client.renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Batches billboard rendering for efficient draw calls.
 *
 * <p>Collects billboard instances during the render phase, then
 * renders them all in a single batched draw call using the texture atlas.
 *
 * <p>Usage:
 * <pre>
 * // During block entity rendering
 * BillboardBatcher.getInstance().addBillboard(pos, entityType, scale, alpha);
 *
 * // At end of render phase (in a render event)
 * BillboardBatcher.getInstance().renderBatch(poseStack, camera);
 * </pre>
 */
public final class BillboardBatcher {

    @Nullable
    private static BillboardBatcher instance;

    private final List<BillboardInstance> instances = new ArrayList<>();
    private boolean batchActive = false;

    private BillboardBatcher() {}

    /**
     * Get the singleton instance.
     */
    @Nonnull
    public static BillboardBatcher getInstance() {
        if (instance == null) {
            instance = new BillboardBatcher();
        }
        return Objects.requireNonNull(instance);
    }

    /**
     * Begin collecting billboards for a new frame.
     */
    public void beginBatch() {
        instances.clear();
        batchActive = true;
    }

    /**
     * Add a billboard to the current batch.
     *
     * @param worldX World X position
     * @param worldY World Y position
     * @param worldZ World Z position
     * @param entityType Entity type string
     * @param scale Billboard scale
     * @param alpha Transparency (0-1)
     */
    public void addBillboard(double worldX, double worldY, double worldZ,
                              @Nonnull String entityType, float scale, float alpha) {
        if (!batchActive) {
            return;
        }

        // Get or create UV region for this entity
        EntityBillboardAtlas.UVRegion uv = EntityBillboardAtlas.getInstance().getOrCreateUV(entityType);
        if (uv == null) {
            return;
        }

        instances.add(new BillboardInstance(
            (float) worldX, (float) worldY, (float) worldZ,
            uv, scale, alpha
        ));
    }

    /**
     * Render all collected billboards and clear the batch.
     *
     * @param poseStack Current pose stack
     * @param camera The camera
     */
    public void renderBatch(@Nonnull PoseStack poseStack, @Nonnull Camera camera) {
        if (!batchActive || instances.isEmpty()) {
            batchActive = false;
            return;
        }

        EntityBillboardAtlas atlas = EntityBillboardAtlas.getInstance();
        ResourceLocation atlasLocation = atlas.getAtlasLocation();
        if (atlasLocation == null || !atlas.isReady()) {
            instances.clear();
            batchActive = false;
            return;
        }

        RenderSystem.assertOnRenderThread();

        // Setup render state
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Bind atlas texture
        RenderSystem.setShaderTexture(0, atlasLocation);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        // Calculate billboard orientation (always face camera)
        float cameraYaw = camera.getYRot();

        // FIX: Get camera position for world-to-camera coordinate conversion
        // The PoseStack at AFTER_BLOCK_ENTITIES stage contains camera transformations,
        // so we need to convert world coordinates to camera-relative coordinates
        Vec3 cameraPos = camera.getPosition();
        float camX = (float) cameraPos.x();
        float camY = (float) cameraPos.y();
        float camZ = (float) cameraPos.z();

        // Build vertex buffer
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(
            VertexFormat.Mode.QUADS,
            Objects.requireNonNull(DefaultVertexFormat.POSITION_TEX_COLOR)
        );

        Matrix4f pose = Objects.requireNonNull(poseStack.last().pose());

        for (BillboardInstance billboard : instances) {
            // FIX: Convert world coordinates to camera-relative before rendering
            float relX = billboard.x - camX;
            float relY = billboard.y - camY;
            float relZ = billboard.z - camZ;
            renderBillboard(Objects.requireNonNull(builder), pose, Objects.requireNonNull(billboard), cameraYaw, relX, relY, relZ);
        }

        // Upload and draw
        MeshData meshData = builder.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        // Cleanup
        RenderSystem.disableBlend();
        instances.clear();
        batchActive = false;
    }

    /**
     * Render a single billboard quad.
     *
     * @param builder The vertex buffer builder
     * @param pose The pose matrix
     * @param billboard The billboard instance (for UV and alpha)
     * @param cameraYaw Camera yaw for billboard orientation
     * @param relX Camera-relative X position
     * @param relY Camera-relative Y position
     * @param relZ Camera-relative Z position
     */
    private void renderBillboard(@Nonnull BufferBuilder builder,
                                  @Nonnull Matrix4f pose,
                                  @Nonnull BillboardInstance billboard,
                                  float cameraYaw,
                                  float relX, float relY, float relZ) {
        // Use camera-relative coordinates instead of world coordinates
        float x = relX;
        float y = relY;
        float z = relZ;
        float scale = billboard.scale;
        float alpha = billboard.alpha;
        EntityBillboardAtlas.UVRegion uv = billboard.uv;

        // Calculate billboard corners (camera-facing quad)
        // Billboard is always perpendicular to camera view
        float halfSize = scale * 0.5f;

        // Calculate right and up vectors based on camera orientation
        float yawRad = (float) Math.toRadians(-cameraYaw);
        // Right vector (perpendicular to camera forward, horizontal)
        float rightX = (float) Math.cos(yawRad);
        float rightZ = (float) Math.sin(yawRad);

        // Calculate corner positions
        float dx1 = -halfSize * rightX;
        float dz1 = -halfSize * rightZ;
        float dx2 = halfSize * rightX;
        float dz2 = halfSize * rightZ;
        float dy1 = -halfSize;
        float dy2 = halfSize;

        // Color (white with alpha)
        int r = 255, g = 255, b = 255;
        int a = (int) (alpha * 255);

        // Add quad vertices (counter-clockwise)
        builder.addVertex(pose, x + dx1, y + dy1, z + dz1)
               .setUv(uv.u0(), uv.v1())
               .setColor(r, g, b, a);
        builder.addVertex(pose, x + dx2, y + dy1, z + dz2)
               .setUv(uv.u1(), uv.v1())
               .setColor(r, g, b, a);
        builder.addVertex(pose, x + dx2, y + dy2, z + dz2)
               .setUv(uv.u1(), uv.v0())
               .setColor(r, g, b, a);
        builder.addVertex(pose, x + dx1, y + dy2, z + dz1)
               .setUv(uv.u0(), uv.v0())
               .setColor(r, g, b, a);
    }

    /**
     * Get the number of billboards in the current batch.
     */
    public int getBatchSize() {
        return instances.size();
    }

    /**
     * Check if a batch is currently active.
     */
    public boolean isBatchActive() {
        return batchActive;
    }

    /**
     * Reset the singleton instance.
     */
    public static void reset() {
        var inst = instance;
        if (inst != null) {
            inst.instances.clear();
            inst.batchActive = false;
            instance = null;
        }
    }

    /**
     * A billboard instance to be rendered.
     */
    private static class BillboardInstance {
        final float x, y, z;
        final EntityBillboardAtlas.UVRegion uv;
        final float scale;
        final float alpha;

        BillboardInstance(float x, float y, float z,
                          EntityBillboardAtlas.UVRegion uv,
                          float scale, float alpha) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.uv = uv;
            this.scale = scale;
            this.alpha = alpha;
        }
    }
}
