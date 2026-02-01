package com.devmod.hologram.client.renderer;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.inventory.InventoryMenu;
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
 *
 * <p>Entity rendering is delegated to {@link HologramEntityRenderer} for Single Responsibility.
 */
public class HologramRenderer implements BlockEntityRenderer<HologramProjectorBlockEntity> {

    /** Dedicated renderer for entities within the hologram. */
    private final HologramEntityRenderer entityRenderer = new HologramEntityRenderer();

    /** Store partial ticks for shader animations. */
    private float partialTicks = 0.0f;

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

        // Store partial ticks for shader animations
        this.partialTicks = partialTick;

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

        // DEBUG: Log build state every 5 seconds
        if (level.getGameTime() % 100 == 0) {
            HologramVBO vbo = blockEntity.getVBO();
            org.slf4j.LoggerFactory.getLogger("Hologram").info(
                "[Renderer] BuildState={}, showEntities={}, VBO valid={}",
                state,
                blockEntity.isShowEntities(),
                vbo != null && vbo.isValid()
            );
        }

        switch (state) {
            case EMPTY -> startAsyncBuild(level, minX, maxX, minZ, maxZ, blockEntity);
            case BUILDING -> checkBuildProgress(blockEntity);
            case READY -> uploadToVBO(blockEntity);
            case UPLOADED -> renderVBO(poseStack, level, blockEntity);
        }
    }

    /**
     * Start async mesh build.
     */
    private void startAsyncBuild(@Nonnull Level level, int minX, int maxX, int minZ, int maxZ,
                                  @Nonnull HologramProjectorBlockEntity blockEntity) {
        blockEntity.setBuildState(BuildState.BUILDING);

        // Determine if textured mode should be used
        // Auto-disable for large scans (> 128 blocks) for performance
        int scanSize = blockEntity.getScanSize();
        boolean texturedMode = blockEntity.isTexturedMode() && scanSize <= 128;

        // Y-slice settings
        boolean ySliceEnabled = blockEntity.isYSliceEnabled();
        int ySliceLevel = blockEntity.getYSliceLevel();
        int ySliceThickness = blockEntity.getYSliceThickness();

        CompletableFuture<HologramMesh> buildTask = HologramMeshBuilder.buildAsync(
                level, minX, maxX, minZ, maxZ,
                blockEntity.getActiveFilters(),
                blockEntity.isFilterHighlightOnly(),
                texturedMode,
                ySliceEnabled,
                ySliceLevel,
                ySliceThickness)
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
     * Upload mesh to VBO and start fade-in animation.
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

        // Store mesh origin Y for entity coordinate alignment
        // CRITICAL: Entities must use the same Y origin as the mesh
        blockEntity.setMeshOriginY(mesh.getOriginY());

        // Start fade-in animation
        Level level = blockEntity.getLevel();
        if (level != null) {
            blockEntity.startAnimation(level.getGameTime());
            // Estimate max Y from scan size (actual would require mesh info)
            blockEntity.setMeshMaxY(level.getMaxBuildHeight() - level.getMinBuildHeight());
        }
    }

    /**
     * Render the VBO with holographic shader effects.
     */
    private void renderVBO(@Nonnull PoseStack poseStack, @Nonnull Level level,
                           @Nonnull HologramProjectorBlockEntity blockEntity) {
        HologramVBO vbo = blockEntity.getVBO();
        if (vbo == null || !vbo.isValid()) {
            blockEntity.setBuildState(BuildState.EMPTY);
            return;
        }

        // CRITICAL: Check actual VBO format, not just what mesh wanted
        // VBO tracks whether it was built with UV coords - must match shader expectations
        boolean vboHasUV = vbo.hasUVCoords();
        boolean texturedMode = vbo.isTexturedMode();

        // SHADER HOT-RELOAD: If shader became ready after VBO was built without UV,
        // trigger a rebuild to use the shader's full features
        if (!vboHasUV && HologramShaderRegistry.isTexturedModeSupported()) {
            org.slf4j.LoggerFactory.getLogger("Hologram").info(
                "[Renderer] Shader became ready - triggering VBO rebuild for hologram at {}",
                blockEntity.getBlockPos());
            blockEntity.setBuildState(BuildState.EMPTY);
            return; // Will rebuild on next frame
        }

        // Bind block atlas if in textured mode
        if (texturedMode) {
            RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        }

        // CRITICAL: Only use custom shader if VBO was built with matching vertex format
        // Custom shader expects POSITION_TEX_COLOR (Position, UV0, Color)
        // Fallback expects POSITION_COLOR (Position, Color)
        // Using wrong shader = vertex attribute mismatch = garbage colors
        ShaderInstance shader = vboHasUV ? HologramShaderRegistry.getShader() : null;

        if (shader != null) {
            RenderSystem.setShader(() -> shader);

            // Calculate game time for animations
            float gameTime = (level.getGameTime() + partialTicks) / 20.0f;

            // Set shader uniforms for holographic effects
            float alpha = blockEntity.isTransparentMode() ? 0.7f : 0.9f;
            float glitchIntensity = 0.15f; // Subtle glitch
            float scanLineSpeed = 1.0f;

            // Cyan hologram color
            float[] holoColor = {0.2f, 0.85f, 1.0f};

            HologramShaderRegistry.setUniforms(gameTime, alpha, glitchIntensity, scanLineSpeed, holoColor);

            // Set animation uniforms
            float fadeInProgress = blockEntity.getFadeInProgress(level.getGameTime(), partialTicks);
            float wavePhase = gameTime; // Wave moves with time
            float waveIntensity = 0.3f;
            float maxY = blockEntity.getMeshMaxY();

            HologramShaderRegistry.setAnimationUniforms(fadeInProgress, wavePhase, waveIntensity, maxY);

            // Set textured mode uniforms
            HologramShaderRegistry.setTexturedModeUniforms(texturedMode, 0.3f);
        } else {
            // Fallback to basic shader (matches POSITION_COLOR format)
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        }

        // Build matrices
        Matrix4f modelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        modelViewMatrix.mul(poseStack.last().pose());
        Matrix4f projectionMatrix = Objects.requireNonNull(RenderSystem.getProjectionMatrix());

        // Render with transparency
        float alpha = blockEntity.isTransparentMode() ? 0.7f : 1.0f;
        vbo.render(modelViewMatrix, Objects.requireNonNull(projectionMatrix), alpha);

        // Delegate entity rendering to dedicated renderer
        if (blockEntity.isShowEntities()) {
            entityRenderer.renderEntities(poseStack, level, blockEntity, partialTicks);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull HologramProjectorBlockEntity blockEntity) {
        // Hologram extends above the block, so render from further away
        return true;
    }
}
