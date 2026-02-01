package com.devmod.hologram.client.renderer;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.devmod.hologram.block.entity.HologramProjectorBlockEntity;

/**
 * Dedicated renderer for entities within the hologram projection.
 * Handles both simple cube rendering and full 3D model rendering with frustum culling.
 *
 * <p>Extracted from HologramRenderer for Single Responsibility Principle compliance.
 * All entity rendering logic is centralized here, including:
 * <ul>
 *   <li>Frustum culling for performance optimization</li>
 *   <li>Simple cube rendering with holographic shader</li>
 *   <li>Full 3D model rendering with live position interpolation</li>
 * </ul>
 */
public class HologramEntityRenderer {

    /** Maximum render distance squared for hologram entity visibility (128 blocks). */
    private static final double MAX_HOLOGRAM_DISTANCE_SQ = 128.0 * 128.0;

    /** Maximum render distance squared for individual entity visibility (64 blocks). */
    private static final double MAX_ENTITY_DISTANCE_SQ = 64.0 * 64.0;

    private final EntityRenderDispatcher entityDispatcher;

    public HologramEntityRenderer() {
        this.entityDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
    }

    /**
     * Main entry point for rendering entities within the hologram.
     * Applies frustum culling and delegates to appropriate render method.
     *
     * @param poseStack The pose stack with hologram transformations applied
     * @param level The current level
     * @param blockEntity The projector block entity
     * @param partialTicks Partial tick time for smooth interpolation
     */
    public void renderEntities(@Nonnull PoseStack poseStack, @Nonnull Level level,
                                @Nonnull HologramProjectorBlockEntity blockEntity,
                                float partialTicks) {
        List<HologramEntityData> entities = blockEntity.getEntitiesForRendering(level, level.getGameTime());
        if (entities == null || entities.isEmpty()) {
            // DEBUG: Log why no entities
            if (level.getGameTime() % 100 == 0) { // Log every 5 seconds
                org.slf4j.LoggerFactory.getLogger("Hologram").info(
                    "[EntityRender] No entities from getEntitiesForRendering. showEntities={}, activeFilters={}",
                    blockEntity.isShowEntities(),
                    blockEntity.getActiveEntityFilters()
                );
            }
            return;
        }

        // Apply frustum culling
        List<HologramEntityData> visibleEntities = cullEntities(entities, blockEntity);
        if (visibleEntities.isEmpty()) {
            // DEBUG: Log culling issue
            if (level.getGameTime() % 100 == 0) {
                org.slf4j.LoggerFactory.getLogger("Hologram").info(
                    "[EntityRender] All {} entities culled by distance", entities.size()
                );
            }
            return;
        }

        // DEBUG: Log successful render
        if (level.getGameTime() % 100 == 0) {
            org.slf4j.LoggerFactory.getLogger("Hologram").info(
                "[EntityRender] Rendering {} entities (from {} before cull)",
                visibleEntities.size(), entities.size()
            );
        }

        int maxModels = blockEntity.getMaxEntityModels();
        boolean useFullModels = blockEntity.isFullEntityModels();

        if (useFullModels && maxModels > 0) {
            // Render first N entities as 3D models, rest as cubes
            int modelCount = Math.min(visibleEntities.size(), maxModels);
            List<HologramEntityData> modelEntities = visibleEntities.subList(0, modelCount);
            List<HologramEntityData> cubeEntities = visibleEntities.subList(modelCount, visibleEntities.size());

            renderFullModels(poseStack, level, modelEntities, blockEntity, partialTicks);
            if (!cubeEntities.isEmpty()) {
                renderSimpleCubes(poseStack, level, blockEntity, cubeEntities, partialTicks);
            }
        } else {
            renderSimpleCubes(poseStack, level, blockEntity, visibleEntities, partialTicks);
        }
    }

    /**
     * Apply frustum culling to filter entities outside camera view.
     * Two-stage culling:
     * 1. Skip all entities if hologram is too far (> 128 blocks)
     * 2. Skip individual entities that are too far (> 64 blocks)
     *
     * @param entities The full list of entities to render
     * @param blockEntity The projector block entity
     * @return Filtered list of visible entities
     */
    @Nonnull
    private List<HologramEntityData> cullEntities(@Nonnull List<HologramEntityData> entities,
                                                   @Nonnull HologramProjectorBlockEntity blockEntity) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        // Projector world position (hologram center)
        double projX = blockEntity.getBlockPos().getX() + 0.5;
        double projY = blockEntity.getBlockPos().getY() + 2.0;
        double projZ = blockEntity.getBlockPos().getZ() + 0.5;

        // Stage 1: Skip ALL entities if hologram is too far from camera
        double distSq = cameraPos.distanceToSqr(projX, projY, projZ);
        if (distSq > MAX_HOLOGRAM_DISTANCE_SQ) {
            return List.of();
        }

        // Calculate transform parameters
        int width = blockEntity.getScanMaxX() - blockEntity.getScanMinX() + 1;
        int depth = blockEntity.getScanMaxZ() - blockEntity.getScanMinZ() + 1;
        float scale = (float) blockEntity.getBlockSize() / Math.max(width, depth);
        float centerX = width / 2.0f;
        float centerZ = depth / 2.0f;

        // Stage 2: Filter individual entities by distance
        List<HologramEntityData> visibleEntities = new ArrayList<>();
        for (HologramEntityData entity : entities) {
            // Transform entity position to world coords
            double worldX = projX + (entity.relativeX() - centerX) * scale;
            double worldY = projY + entity.relativeY() * scale;
            double worldZ = projZ + (entity.relativeZ() - centerZ) * scale;

            // Distance cull
            if (cameraPos.distanceToSqr(worldX, worldY, worldZ) < MAX_ENTITY_DISTANCE_SQ) {
                visibleEntities.add(entity);
            }
        }

        return visibleEntities;
    }

    /**
     * Render entities as simple colored cubes with holographic shader effects.
     * All cubes are batched into a single draw call for efficiency.
     * Uses live interpolated entity positions for smooth movement.
     *
     * @param poseStack The pose stack with hologram transformations applied
     * @param level The current level for live entity lookup
     * @param blockEntity The projector for origin calculation
     * @param entities The entities to render as cubes
     * @param partialTicks Partial tick time for smooth interpolation
     */
    private void renderSimpleCubes(@Nonnull PoseStack poseStack, @Nonnull Level level,
                                    @Nonnull HologramProjectorBlockEntity blockEntity,
                                    @Nonnull List<HologramEntityData> entities,
                                    float partialTicks) {
        if (entities.isEmpty()) return;

        // CRITICAL: Check if custom shader is available BEFORE choosing vertex format
        // Vertex format MUST match shader expectations to avoid attribute mismatch
        boolean useCustomShader = HologramShaderRegistry.isTexturedModeSupported();
        ShaderInstance shader = useCustomShader ? HologramShaderRegistry.getShader() : null;

        if (shader != null) {
            RenderSystem.setShader(() -> shader);

            float gameTime = (level.getGameTime() + partialTicks) / 20.0f;

            // Cyan hologram color (same as terrain)
            float[] holoColor = {0.2f, 0.85f, 1.0f};

            HologramShaderRegistry.setUniforms(gameTime, 0.8f, 0.1f, 1.0f, holoColor);
            HologramShaderRegistry.setAnimationUniforms(1.0f, gameTime, 0.2f, 256.0f);
            HologramShaderRegistry.setTexturedModeUniforms(false, 0.0f);
        } else {
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();

        // Combine RenderSystem modelview with hologram transforms
        Matrix4f combinedModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        combinedModelView.mul(poseStack.last().pose());
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().set(combinedModelView);
        RenderSystem.applyModelViewMatrix();

        // CRITICAL: Vertex format MUST match shader
        // Custom shader (hologram.vsh) expects: Position, UV0, Color (POSITION_TEX_COLOR)
        // Fallback (position_color) expects: Position, Color (POSITION_COLOR)
        VertexFormat format = useCustomShader ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, format);

        for (HologramEntityData entityData : entities) {
            // Use CACHED positions for maximum stability (no jitter)
            // The cache updates every 2 ticks which is smooth enough for minimap display
            // Live interpolation causes jitter due to physics micro-adjustments
            float relX = entityData.relativeX();
            float relY = entityData.relativeY();
            float relZ = entityData.relativeZ();
            addCubeVerticesAt(buffer, relX, relY, relZ, entityData, useCustomShader);
        }

        // Single draw call for all cubes
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        // Restore RenderSystem state COMPLETELY
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        // Clear shader to avoid affecting subsequent rendering
        if (shader != null) {
            shader.clear();
        }
    }

    /**
     * Add vertices for a single entity cube to the buffer at the specified position.
     *
     * @param buffer The buffer to add vertices to
     * @param x Relative X position (live interpolated)
     * @param y Relative Y position (live interpolated)
     * @param z Relative Z position (live interpolated)
     * @param entityData The entity data for color and bounding box
     * @param includeUV Whether to include UV coordinates (must match buffer's vertex format)
     */
    private void addCubeVerticesAt(@Nonnull BufferBuilder buffer, float x, float y, float z,
                                    @Nonnull HologramEntityData entityData, boolean includeUV) {
        float halfWidth = entityData.bbWidth() / 2.0f;
        float height = entityData.bbHeight();

        // Extract RGB from color
        int color = entityData.color();
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = entityData.isHostile() ? 0.9f : 0.7f;

        float minX = x - halfWidth;
        float maxX = x + halfWidth;
        float minY = y;
        float maxY = y + height;
        float minZ = z - halfWidth;
        float maxZ = z + halfWidth;

        // Add vertices with or without UV based on format
        if (includeUV) {
            // POSITION_TEX_COLOR format
            // Bottom face
            buffer.addVertex(minX, minY, minZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(maxX, minY, minZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(maxX, minY, maxZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(minX, minY, maxZ).setUv(0, 0).setColor(r, g, b, a);

            // Top face
            buffer.addVertex(minX, maxY, maxZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(maxX, maxY, maxZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(maxX, maxY, minZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(minX, maxY, minZ).setUv(0, 0).setColor(r, g, b, a);

            // North face (-Z)
            buffer.addVertex(minX, minY, minZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(minX, maxY, minZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(maxX, maxY, minZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(maxX, minY, minZ).setUv(0, 0).setColor(r, g, b, a);

            // South face (+Z)
            buffer.addVertex(maxX, minY, maxZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(maxX, maxY, maxZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(minX, maxY, maxZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(minX, minY, maxZ).setUv(0, 0).setColor(r, g, b, a);

            // West face (-X)
            buffer.addVertex(minX, minY, maxZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(minX, maxY, maxZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(minX, maxY, minZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(minX, minY, minZ).setUv(0, 0).setColor(r, g, b, a);

            // East face (+X)
            buffer.addVertex(maxX, minY, minZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(maxX, maxY, minZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(maxX, maxY, maxZ).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(maxX, minY, maxZ).setUv(0, 0).setColor(r, g, b, a);
        } else {
            // POSITION_COLOR format (no UV)
            // Bottom face
            buffer.addVertex(minX, minY, minZ).setColor(r, g, b, a);
            buffer.addVertex(maxX, minY, minZ).setColor(r, g, b, a);
            buffer.addVertex(maxX, minY, maxZ).setColor(r, g, b, a);
            buffer.addVertex(minX, minY, maxZ).setColor(r, g, b, a);

            // Top face
            buffer.addVertex(minX, maxY, maxZ).setColor(r, g, b, a);
            buffer.addVertex(maxX, maxY, maxZ).setColor(r, g, b, a);
            buffer.addVertex(maxX, maxY, minZ).setColor(r, g, b, a);
            buffer.addVertex(minX, maxY, minZ).setColor(r, g, b, a);

            // North face (-Z)
            buffer.addVertex(minX, minY, minZ).setColor(r, g, b, a);
            buffer.addVertex(minX, maxY, minZ).setColor(r, g, b, a);
            buffer.addVertex(maxX, maxY, minZ).setColor(r, g, b, a);
            buffer.addVertex(maxX, minY, minZ).setColor(r, g, b, a);

            // South face (+Z)
            buffer.addVertex(maxX, minY, maxZ).setColor(r, g, b, a);
            buffer.addVertex(maxX, maxY, maxZ).setColor(r, g, b, a);
            buffer.addVertex(minX, maxY, maxZ).setColor(r, g, b, a);
            buffer.addVertex(minX, minY, maxZ).setColor(r, g, b, a);

            // West face (-X)
            buffer.addVertex(minX, minY, maxZ).setColor(r, g, b, a);
            buffer.addVertex(minX, maxY, maxZ).setColor(r, g, b, a);
            buffer.addVertex(minX, maxY, minZ).setColor(r, g, b, a);
            buffer.addVertex(minX, minY, minZ).setColor(r, g, b, a);

            // East face (+X)
            buffer.addVertex(maxX, minY, minZ).setColor(r, g, b, a);
            buffer.addVertex(maxX, maxY, minZ).setColor(r, g, b, a);
            buffer.addVertex(maxX, maxY, maxZ).setColor(r, g, b, a);
            buffer.addVertex(maxX, minY, maxZ).setColor(r, g, b, a);
        }
    }

    /**
     * Render entities as full 3D models.
     * Uses live interpolated positions for smooth movement.
     *
     * @param poseStack The pose stack with hologram transformations applied
     * @param level The current level
     * @param entities The entities to render as 3D models
     * @param blockEntity The projector block entity
     * @param partialTicks Partial tick time for smooth interpolation
     */
    private void renderFullModels(@Nonnull PoseStack poseStack, @Nonnull Level level,
                                   @Nonnull List<HologramEntityData> entities,
                                   @Nonnull HologramProjectorBlockEntity blockEntity,
                                   float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // Disable shadows once for all entities
        entityDispatcher.setRenderShadow(false);
        try {
            for (HologramEntityData entityData : entities) {
                Entity entity = level.getEntity(entityData.entityId());
                if (entity == null) continue;

                poseStack.pushPose();

                // Use CACHED positions for maximum stability (no jitter)
                float relX = entityData.relativeX();
                float relY = entityData.relativeY();
                float relZ = entityData.relativeZ();
                poseStack.translate(relX, relY, relZ);

                entityDispatcher.render(entity, 0, 0, 0, 0, partialTicks, poseStack, bufferSource, LightTexture.FULL_BRIGHT);

                poseStack.popPose();
            }
        } finally {
            entityDispatcher.setRenderShadow(true);
        }

        bufferSource.endBatch();
    }
}
