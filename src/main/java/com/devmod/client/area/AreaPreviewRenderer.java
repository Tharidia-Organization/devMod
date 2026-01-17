package com.devmod.client.area;

import java.util.Locale;
import java.util.Objects;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.core.BlockPos;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.DevMod;
import com.devmod.area.data.AreaDimensions;
import com.devmod.area.data.AreaPalette;
import com.devmod.area.data.AreaShape;
import com.devmod.area.network.AreaPreviewPayload;

/**
 * Client-side renderer for area preview visualization.
 * Renders the preview as a holographic mesh showing floor, walls, and ceiling.
 */
@OnlyIn(Dist.CLIENT)
public class AreaPreviewRenderer {
    public static final AreaPreviewRenderer INSTANCE = new AreaPreviewRenderer();

    @Nullable
    private AreaPreviewPayload currentPreview;

    @Nullable
    private AreaPreviewMesh currentMesh;

    @Nullable
    private VertexBuffer vertexBuffer;
    private int vertexCount;

    private long previewExpireTime;

    private static final long PREVIEW_DURATION_MS = 15_000; // 15 seconds
    private static final float HOLOGRAM_ALPHA = 0.4f;

    private AreaPreviewRenderer() {}

    /**
     * Sets the current preview to display.
     * Builds the mesh and VBO for efficient rendering.
     */
    public void setPreview(@Nullable AreaPreviewPayload preview) {
        // Clear previous resources
        clearMesh();

        this.currentPreview = preview;
        this.previewExpireTime = System.currentTimeMillis() + PREVIEW_DURATION_MS;

        if (preview == null) {
            return;
        }

        // Build the mesh
        buildMesh(preview);
    }

    /**
     * Builds the holographic mesh from the preview data.
     */
    private void buildMesh(AreaPreviewPayload preview) {
        try {
            BlockPos center = preview.center();
            AreaShape shape = AreaShape.valueOf(preview.shape().toUpperCase(Locale.ROOT));

            // Use preview's floorY for the dimensions
            AreaDimensions dimensions = new AreaDimensions(
                preview.width(),
                preview.length(),
                preview.height(),
                center.getY() // Use center Y as floorY
            );

            // Create empty palette (colors are hardcoded in mesh for now)
            AreaPalette palette = Objects.requireNonNull(AreaPalette.empty("default"));

            // Build the mesh with biome info if available
            AreaPreviewMesh mesh = new AreaPreviewMesh(
                shape,
                center,
                dimensions,
                palette,
                preview.useBiome(),
                preview.biomeId(),
                preview.terrainStyle(),
                preview.biomeSeed()
            );
            currentMesh = mesh;

            if (mesh.isEmpty()) {
                DevMod.LOGGER.warn("[Area] Preview mesh is empty");
                return;
            }

            // Upload to VBO
            uploadToVBO();

            DevMod.LOGGER.info("[Area] Built preview mesh: {} quads for {}x{}x{} {} (biome: {})",
                mesh.getQuadCount(), preview.width(), preview.length(), preview.height(),
                shape, preview.useBiome() ? preview.biomeId() : "none");

        } catch (Exception e) {
            DevMod.LOGGER.error("[Area] Failed to build preview mesh", e);
            clearMesh();
        }
    }

    /**
     * Uploads the mesh to a VBO for efficient rendering.
     */
    private void uploadToVBO() {
        AreaPreviewMesh mesh = currentMesh;
        if (mesh == null || mesh.isEmpty()) {
            return;
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS,
            Objects.requireNonNull(DefaultVertexFormat.POSITION_COLOR));
        PoseStack poseStack = new PoseStack();

        // Render mesh into buffer
        mesh.render(Objects.requireNonNull(builder), poseStack, HOLOGRAM_ALPHA);

        MeshData meshData = builder.buildOrThrow();
        this.vertexCount = meshData.drawState().vertexCount();

        // Create and upload VBO
        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.vertexBuffer = vbo;
        vbo.bind();
        vbo.upload(meshData);
        VertexBuffer.unbind();

        meshData.close();
    }

    /**
     * Clears the current preview.
     */
    public void clearPreview() {
        clearMesh();
        this.currentPreview = null;
    }

    /**
     * Clears mesh and releases GPU resources.
     */
    private void clearMesh() {
        currentMesh = null;
        if (vertexBuffer != null) {
            VertexBuffer bufferToClose = vertexBuffer;
            vertexBuffer = null;
            vertexCount = 0;
            RenderSystem.recordRenderCall(bufferToClose::close);
        }
    }

    /**
     * Returns the current preview, or null if expired or not set.
     */
    @Nullable
    public AreaPreviewPayload getPreview() {
        if (currentPreview != null && System.currentTimeMillis() > previewExpireTime) {
            clearPreview();
        }
        return currentPreview;
    }

    /**
     * Returns true if a preview is currently active.
     */
    public boolean hasActivePreview() {
        return getPreview() != null;
    }

    /**
     * Returns true if the mesh and VBO are ready for rendering.
     */
    public boolean hasRenderable() {
        return vertexBuffer != null && vertexCount > 0 && hasActivePreview();
    }

    /**
     * Gets the vertex buffer for rendering.
     */
    @Nullable
    public VertexBuffer getVertexBuffer() {
        return vertexBuffer;
    }

    /**
     * Gets the vertex count.
     */
    public int getVertexCount() {
        return vertexCount;
    }

    /**
     * UX-05 fix: Atomically gets all render data if valid, preventing race condition
     * where preview could expire between hasRenderable() and getPreview() calls.
     *
     * @return RenderData if preview is valid and renderable, null otherwise
     */
    @Nullable
    public synchronized RenderData getRenderDataIfValid() {
        // Check expiration and clear if needed
        if (currentPreview != null && System.currentTimeMillis() > previewExpireTime) {
            clearPreview();
        }

        // Return null if any component is missing
        if (currentPreview == null || vertexBuffer == null || vertexCount <= 0) {
            return null;
        }

        return new RenderData(currentPreview, vertexBuffer, vertexCount);
    }

    /**
     * UX-05 fix: Immutable container for render data to prevent race conditions.
     */
    public record RenderData(AreaPreviewPayload preview, VertexBuffer vertexBuffer, int vertexCount) {}
}
