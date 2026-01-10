package com.devmod.clone.client.renderer;

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import com.devmod.DevMod;

/**
 * Renders entities to textures for use in billboard sprites.
 *
 * <p>This class creates off-screen render targets and captures entity
 * renders as 2D images that can be stitched into the billboard atlas.
 */
public final class EntityBillboardCache {

    private static EntityBillboardCache instance;

    @Nullable
    private RenderTarget renderTarget;
    private int targetSize = 64;

    private EntityBillboardCache() {}

    /**
     * Get the singleton instance.
     */
    @Nonnull
    public static EntityBillboardCache getInstance() {
        if (instance == null) {
            instance = new EntityBillboardCache();
        }
        return instance;
    }

    /**
     * Render an entity to the atlas image at the specified position.
     *
     * @param entityTypeString The entity type (e.g., "minecraft:zombie")
     * @param atlasImage The atlas image to render into
     * @param x X position in the atlas
     * @param y Y position in the atlas
     * @param size Size of the sprite (width and height)
     * @return true if rendering succeeded
     */
    public boolean renderEntityToAtlas(@Nonnull String entityTypeString,
                                        @Nonnull NativeImage atlasImage,
                                        int x, int y, int size) {
        RenderSystem.assertOnRenderThread();

        // Parse entity type
        Optional<EntityType<?>> optType = EntityType.byString(entityTypeString);
        if (optType.isEmpty()) {
            DevMod.LOGGER.warn("[Clone] Unknown entity type for billboard: {}", entityTypeString);
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        // Create temporary entity for rendering
        EntityType<?> type = optType.get();
        Entity entity;
        try {
            entity = type.create(mc.level);
            if (entity == null) {
                DevMod.LOGGER.warn("[Clone] Failed to create entity for billboard: {}", entityTypeString);
                return false;
            }
        } catch (Exception e) {
            DevMod.LOGGER.warn("[Clone] Exception creating entity for billboard: {}", entityTypeString, e);
            return false;
        }

        // Configure entity for static rendering
        entity.noPhysics = true;
        entity.setNoGravity(true);
        entity.setSilent(true);
        entity.setInvulnerable(true);
        entity.setPos(0, -1000, 0);

        if (entity instanceof LivingEntity living) {
            living.yBodyRot = 0;
            living.yBodyRotO = 0;
            living.yHeadRot = 0;
            living.yHeadRotO = 0;
            living.hurtTime = 0;
            living.deathTime = 0;
        }

        try {
            // Render entity to off-screen buffer
            NativeImage snapshot = renderEntityToImage(entity, size);
            if (snapshot == null) {
                return false;
            }

            // Copy snapshot to atlas
            copyToAtlas(snapshot, atlasImage, x, y, size);
            snapshot.close();

            return true;
        } catch (Exception e) {
            DevMod.LOGGER.warn("[Clone] Exception rendering entity billboard: {}", entityTypeString, e);
            return false;
        }
    }

    /**
     * Render an entity to an off-screen image.
     */
    @Nullable
    private NativeImage renderEntityToImage(@Nonnull Entity entity, int size) {
        Minecraft mc = Minecraft.getInstance();

        // Create or resize render target
        if (renderTarget == null || renderTarget.width != size || renderTarget.height != size) {
            if (renderTarget != null) {
                renderTarget.destroyBuffers();
            }
            renderTarget = new TextureTarget(size, size, true, Minecraft.ON_OSX);
        }

        // Setup render target
        renderTarget.setClearColor(0, 0, 0, 0);
        renderTarget.clear(Minecraft.ON_OSX);
        renderTarget.bindWrite(true);

        // Setup orthographic projection
        Matrix4f projectionMatrix = new Matrix4f().ortho(
            -1.0f, 1.0f, -1.0f, 1.0f, -10.0f, 10.0f
        );

        // Calculate scale to fit entity in view
        float entityWidth = entity.getBbWidth();
        float entityHeight = entity.getBbHeight();
        float maxDimension = Math.max(entityWidth, entityHeight);
        float scale = maxDimension > 0 ? 1.6f / maxDimension : 1.0f;

        // Setup pose stack
        PoseStack poseStack = new PoseStack();
        poseStack.setIdentity();

        // Position entity in center, looking at camera
        poseStack.translate(0, -entityHeight * scale * 0.4, 0);
        poseStack.scale(scale, scale, scale);

        // Rotate to face camera (front view with slight angle)
        poseStack.mulPose(new Quaternionf().rotationY((float) Math.toRadians(200)));

        // Setup model-view matrix
        Matrix4f modelViewMatrix = new Matrix4f();
        modelViewMatrix.identity();

        // Push to RenderSystem
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projectionMatrix, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);

        Matrix4f oldModelView = RenderSystem.getModelViewMatrix();
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.applyModelViewMatrix();

        try {
            // Get buffer source
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

            // Render entity
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            dispatcher.setRenderShadow(false);
            dispatcher.render(
                entity,
                0, 0, 0,
                0, 0,
                poseStack,
                bufferSource,
                LightTexture.FULL_BRIGHT
            );
            dispatcher.setRenderShadow(true);

            // Flush buffers
            bufferSource.endBatch();

        } finally {
            // Restore matrices
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }

        // Unbind render target
        renderTarget.unbindWrite();
        mc.getMainRenderTarget().bindWrite(true);

        // Read pixels from render target
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        RenderSystem.bindTexture(renderTarget.getColorTextureId());
        image.downloadTexture(0, false);

        // Flip image vertically (OpenGL renders upside down)
        flipImageVertically(image);

        return image;
    }

    /**
     * Flip an image vertically in-place.
     */
    private void flipImageVertically(@Nonnull NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height / 2; y++) {
            int oppositeY = height - 1 - y;
            for (int x = 0; x < width; x++) {
                int top = image.getPixelRGBA(x, y);
                int bottom = image.getPixelRGBA(x, oppositeY);
                image.setPixelRGBA(x, y, bottom);
                image.setPixelRGBA(x, oppositeY, top);
            }
        }
    }

    /**
     * Copy a snapshot image to the atlas at the specified position.
     */
    private void copyToAtlas(@Nonnull NativeImage source,
                              @Nonnull NativeImage atlas,
                              int destX, int destY, int size) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int srcX = x * source.getWidth() / size;
                int srcY = y * source.getHeight() / size;
                int pixel = source.getPixelRGBA(srcX, srcY);
                atlas.setPixelRGBA(destX + x, destY + y, pixel);
            }
        }
    }

    /**
     * Release GPU resources.
     */
    public void close() {
        if (renderTarget != null) {
            renderTarget.destroyBuffers();
            renderTarget = null;
        }
    }

    /**
     * Reset the singleton instance.
     */
    public static void reset() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}
