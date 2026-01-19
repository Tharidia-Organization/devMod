package com.devmod.hologram.client.renderer;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;

import com.devmod.hologram.block.entity.HologramProjectorBlockEntity;
import com.devmod.hologram.block.entity.HologramProjectorBlockEntity.BuildState;

/**
 * Renderer for the hologram projector block entity.
 * Uses a state machine to manage async mesh building and VBO rendering.
 *
 * <p>Build pipeline:
 * <ol>
 *   <li>EMPTY → Start async mesh build</li>
 *   <li>BUILDING → Check if build is complete</li>
 *   <li>READY → Upload mesh to VBO</li>
 *   <li>UPLOADED → Render VBO each frame</li>
 * </ol>
 */
public class HologramRenderer implements BlockEntityRenderer<HologramProjectorBlockEntity> {

    public HologramRenderer(BlockEntityRendererProvider.Context context) {
        // No resources needed
    }

    @Override
    public void render(@Nonnull HologramProjectorBlockEntity blockEntity, float partialTick,
                       @Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer,
                       int light, int overlay) {
        if (!blockEntity.hasValidRegion() || blockEntity.getLevel() == null) {
            return;
        }

        Level level = Objects.requireNonNull(blockEntity.getLevel());
        int minX = blockEntity.getScanMinX();
        int maxX = blockEntity.getScanMaxX();
        int minZ = blockEntity.getScanMinZ();
        int maxZ = blockEntity.getScanMaxZ();

        poseStack.pushPose();

        // Position hologram above the block
        poseStack.translate(0.5, 2.0, 0.5);

        // Apply rotation if enabled
        if (blockEntity.isRotationEnabled()) {
            long time = level.getGameTime();
            float rotation = (time + partialTick) * 0.5f;
            poseStack.mulPose(Objects.requireNonNull(new Quaternionf().rotationY((float) Math.toRadians(rotation))));
        }

        // Scale to fit within blockSize
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        int blockSize = blockEntity.getBlockSize();
        float scale = (float) blockSize / Math.max(width, depth);
        poseStack.scale(scale, scale, scale);

        // Center the hologram
        poseStack.translate(-width / 2.0f, 0.0f, -depth / 2.0f);

        // Build and render using state machine
        buildAndRenderVBO(level, minX, maxX, minZ, maxZ, poseStack, blockEntity);

        poseStack.popPose();
    }

    /**
     * State machine for async build and render.
     */
    private void buildAndRenderVBO(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ,
                                    @Nonnull PoseStack poseStack,
                                    @Nonnull HologramProjectorBlockEntity blockEntity) {
        BuildState state = blockEntity.getBuildState();

        switch (state) {
            case EMPTY -> startAsyncBuild(level, minX, maxX, minZ, maxZ, blockEntity);
            case BUILDING -> checkBuildProgress(blockEntity);
            case READY -> uploadToVBO(blockEntity);
            case UPLOADED -> renderVBO(poseStack, blockEntity);
        }
    }

    /**
     * Start async mesh build.
     */
    private void startAsyncBuild(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ,
                                  @Nonnull HologramProjectorBlockEntity blockEntity) {
        blockEntity.setBuildState(BuildState.BUILDING);

        CompletableFuture<HologramMesh> buildTask = HologramMeshBuilder.buildAsync(level, minX, maxX, minZ, maxZ)
            .thenApply(mesh -> {
                blockEntity.setMesh(mesh);
                blockEntity.setBuildState(BuildState.READY);
                return mesh;
            });
        blockEntity.setBuildTask(buildTask);
    }

    /**
     * Check if async build is complete.
     */
    private void checkBuildProgress(@Nonnull HologramProjectorBlockEntity blockEntity) {
        CompletableFuture<HologramMesh> buildTask = blockEntity.getBuildTask();
        if (buildTask == null || buildTask.isDone()) {
            // Build task completed or was cancelled
            // State will be updated by thenAccept callback
        }
    }

    /**
     * Upload mesh to VBO.
     */
    private void uploadToVBO(@Nonnull HologramProjectorBlockEntity blockEntity) {
        HologramMesh mesh = blockEntity.getMesh();
        if (mesh == null || mesh.isEmpty()) {
            blockEntity.setBuildState(BuildState.EMPTY);
            return;
        }

        HologramVBO vbo = new HologramVBO();
        vbo.upload(mesh);
        blockEntity.setVBO(vbo);
        blockEntity.setBuildState(BuildState.UPLOADED);
    }

    /**
     * Render the VBO.
     */
    private void renderVBO(@Nonnull PoseStack poseStack, @Nonnull HologramProjectorBlockEntity blockEntity) {
        HologramVBO vbo = blockEntity.getVBO();
        if (vbo == null || !vbo.isValid()) {
            blockEntity.setBuildState(BuildState.EMPTY);
            return;
        }

        // Set shader
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Build matrices
        Matrix4f modelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        modelViewMatrix.mul(poseStack.last().pose());
        Matrix4f projectionMatrix = Objects.requireNonNull(RenderSystem.getProjectionMatrix());

        // Render with transparency if enabled
        float alpha = blockEntity.isTransparentMode() ? 0.7f : 1.0f;
        vbo.render(modelViewMatrix, Objects.requireNonNull(projectionMatrix), alpha);
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull HologramProjectorBlockEntity blockEntity) {
        // Hologram extends above the block, so render from further away
        return true;
    }
}
