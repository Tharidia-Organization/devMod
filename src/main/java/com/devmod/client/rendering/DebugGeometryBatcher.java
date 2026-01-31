package com.devmod.client.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Batches debug geometry (boxes, lines) into a single VBO for efficient rendering.
 * Significantly reduces draw calls compared to immediate mode rendering.
 *
 * <p>Usage:
 * <pre>
 * batcher.addBox(box, color, false);
 * batcher.addLine(from, to, color);
 * batcher.rebuildIfDirty();
 * batcher.render(poseStack, cameraPos);
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null") // Minecraft API lacks @Nonnull annotations
public class DebugGeometryBatcher {
    public static final DebugGeometryBatcher INSTANCE = new DebugGeometryBatcher();

    // Pending geometry to batch
    private final List<BatchedBox> boxes = new CopyOnWriteArrayList<>();
    private final List<BatchedLine> lines = new CopyOnWriteArrayList<>();

    // VBOs for different geometry types
    @Nullable
    private VertexBuffer solidBoxVBO;
    @Nullable
    private VertexBuffer wireframeVBO;
    @Nullable
    private VertexBuffer lineVBO;

    private int solidBoxVertexCount;
    private int wireframeVertexCount;
    private int lineVertexCount;

    private volatile boolean dirty = true;
    private long lastRebuildTime = 0;
    private static final long REBUILD_COOLDOWN_MS = 50; // Don't rebuild too often

    private DebugGeometryBatcher() {}

    /**
     * Add a box to the batch.
     *
     * @param box The AABB to render
     * @param color ARGB color
     * @param wireframe True for wireframe, false for solid
     */
    public void addBox(@Nonnull AABB box, int color, boolean wireframe) {
        boxes.add(new BatchedBox(box, color, wireframe));
        dirty = true;
    }

    /**
     * Add a line to the batch.
     *
     * @param from Start position
     * @param to End position
     * @param color ARGB color
     */
    public void addLine(@Nonnull Vec3 from, @Nonnull Vec3 to, int color) {
        lines.add(new BatchedLine(from, to, color));
        dirty = true;
    }

    /**
     * Clear all batched geometry.
     */
    public void clear() {
        boxes.clear();
        lines.clear();
        dirty = true;
    }

    /**
     * Mark the batch as dirty, requiring a rebuild.
     */
    public void markDirty() {
        dirty = true;
    }

    /**
     * Rebuild the VBOs if dirty.
     * Call this once per frame before rendering.
     */
    public void rebuildIfDirty() {
        if (!dirty) {
            return;
        }

        // Throttle rebuilds
        long now = System.currentTimeMillis();
        if (now - lastRebuildTime < REBUILD_COOLDOWN_MS) {
            return;
        }
        lastRebuildTime = now;

        rebuildVBOs();
        dirty = false;
    }

    private void rebuildVBOs() {
        // Close existing VBOs
        closeVBOs();

        // Separate boxes into solid and wireframe
        List<BatchedBox> solidBoxes = new ArrayList<>();
        List<BatchedBox> wireframeBoxes = new ArrayList<>();
        for (BatchedBox box : boxes) {
            if (box.wireframe) {
                wireframeBoxes.add(box);
            } else {
                solidBoxes.add(box);
            }
        }

        // Build solid boxes VBO (QUADS)
        if (!solidBoxes.isEmpty()) {
            solidBoxVBO = buildSolidBoxVBO(solidBoxes);
        }

        // Build wireframe VBO (LINES)
        if (!wireframeBoxes.isEmpty()) {
            wireframeVBO = buildWireframeVBO(wireframeBoxes);
        }

        // Build line VBO
        if (!lines.isEmpty()) {
            lineVBO = buildLineVBO(new ArrayList<>(lines));
        }
    }

    @Nullable
    private VertexBuffer buildSolidBoxVBO(List<BatchedBox> solidBoxes) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (BatchedBox box : solidBoxes) {
            addSolidBoxVertices(builder, box.box, box.color);
        }

        MeshData meshData = builder.build();
        if (meshData == null) {
            return null;
        }

        solidBoxVertexCount = meshData.drawState().vertexCount();
        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(meshData);
        VertexBuffer.unbind();
        meshData.close();

        return vbo;
    }

    @Nullable
    private VertexBuffer buildWireframeVBO(List<BatchedBox> wireframeBoxes) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);

        for (BatchedBox box : wireframeBoxes) {
            addWireframeBoxVertices(builder, box.box, box.color);
        }

        MeshData meshData = builder.build();
        if (meshData == null) {
            return null;
        }

        wireframeVertexCount = meshData.drawState().vertexCount();
        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(meshData);
        VertexBuffer.unbind();
        meshData.close();

        return vbo;
    }

    @Nullable
    private VertexBuffer buildLineVBO(List<BatchedLine> lineList) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);

        for (BatchedLine line : lineList) {
            float r = ((line.color >> 16) & 0xFF) / 255f;
            float g = ((line.color >> 8) & 0xFF) / 255f;
            float b = (line.color & 0xFF) / 255f;
            float a = ((line.color >> 24) & 0xFF) / 255f;
            if (a == 0) a = 1.0f;

            builder.addVertex((float) line.from.x, (float) line.from.y, (float) line.from.z)
                .setColor(r, g, b, a);
            builder.addVertex((float) line.to.x, (float) line.to.y, (float) line.to.z)
                .setColor(r, g, b, a);
        }

        MeshData meshData = builder.build();
        if (meshData == null) {
            return null;
        }

        lineVertexCount = meshData.drawState().vertexCount();
        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(meshData);
        VertexBuffer.unbind();
        meshData.close();

        return vbo;
    }

    private void addSolidBoxVertices(BufferBuilder builder, AABB box, int color) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1.0f;

        // Bottom face (Y-)
        builder.addVertex(minX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(maxX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(maxX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(minX, minY, maxZ).setColor(r, g, b, a);

        // Top face (Y+)
        builder.addVertex(minX, maxY, minZ).setColor(r, g, b, a);
        builder.addVertex(minX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(maxX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(maxX, maxY, minZ).setColor(r, g, b, a);

        // North face (Z-)
        builder.addVertex(minX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(minX, maxY, minZ).setColor(r, g, b, a);
        builder.addVertex(maxX, maxY, minZ).setColor(r, g, b, a);
        builder.addVertex(maxX, minY, minZ).setColor(r, g, b, a);

        // South face (Z+)
        builder.addVertex(minX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(maxX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(maxX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(minX, maxY, maxZ).setColor(r, g, b, a);

        // West face (X-)
        builder.addVertex(minX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(minX, minY, maxZ).setColor(r, g, b, a);
        builder.addVertex(minX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(minX, maxY, minZ).setColor(r, g, b, a);

        // East face (X+)
        builder.addVertex(maxX, minY, minZ).setColor(r, g, b, a);
        builder.addVertex(maxX, maxY, minZ).setColor(r, g, b, a);
        builder.addVertex(maxX, maxY, maxZ).setColor(r, g, b, a);
        builder.addVertex(maxX, minY, maxZ).setColor(r, g, b, a);
    }

    private void addWireframeBoxVertices(BufferBuilder builder, AABB box, int color) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0) a = 1.0f;

        // Bottom face edges
        line(builder, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        line(builder, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        line(builder, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        line(builder, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // Top face edges
        line(builder, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(builder, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        line(builder, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        line(builder, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // Vertical edges
        line(builder, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        line(builder, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        line(builder, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        line(builder, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private void line(BufferBuilder builder, float x1, float y1, float z1, float x2, float y2, float z2,
                      float r, float g, float b, float a) {
        builder.addVertex(x1, y1, z1).setColor(r, g, b, a);
        builder.addVertex(x2, y2, z2).setColor(r, g, b, a);
    }

    /**
     * Render all batched geometry.
     *
     * @param poseStack The pose stack
     * @param cameraPos Camera position for translation
     */
    public void render(@Nonnull PoseStack poseStack, @Nonnull Vec3 cameraPos) {
        RenderSystem.assertOnRenderThread();

        // Setup render state
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.0f);

        try {
            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            Matrix4f modelView = poseStack.last().pose();
            Matrix4f projection = RenderSystem.getProjectionMatrix();

            // Render solid boxes
            if (solidBoxVBO != null && solidBoxVertexCount > 0) {
                RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
                renderVBO(solidBoxVBO, modelView, projection);
            }

            // Render wireframe boxes
            if (wireframeVBO != null && wireframeVertexCount > 0) {
                RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
                renderVBO(wireframeVBO, modelView, projection);
            }

            // Render lines
            if (lineVBO != null && lineVertexCount > 0) {
                RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
                renderVBO(lineVBO, modelView, projection);
            }

            poseStack.popPose();
        } finally {
            // Restore default render state
            RenderSystem.disableBlend();
            RenderSystem.lineWidth(1.0f);
        }
    }

    private void renderVBO(@Nonnull VertexBuffer vbo, @Nonnull Matrix4f modelView, @Nonnull Matrix4f projection) {
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            return;
        }

        var modelViewMatrix = shader.MODEL_VIEW_MATRIX;
        var projectionMatrix = shader.PROJECTION_MATRIX;
        var colorModulator = shader.COLOR_MODULATOR;

        if (modelViewMatrix != null) {
            modelViewMatrix.set(modelView);
        }
        if (projectionMatrix != null) {
            projectionMatrix.set(projection);
        }
        if (colorModulator != null) {
            colorModulator.set(1.0f, 1.0f, 1.0f, 1.0f);
        }

        vbo.bind();
        shader.apply();
        vbo.draw();
        VertexBuffer.unbind();
        shader.clear();
    }

    /**
     * Close all VBOs and release GPU resources.
     */
    public void close() {
        closeVBOs();
        boxes.clear();
        lines.clear();
    }

    private void closeVBOs() {
        if (solidBoxVBO != null) {
            VertexBuffer vbo = solidBoxVBO;
            solidBoxVBO = null;
            solidBoxVertexCount = 0;
            RenderSystem.recordRenderCall(vbo::close);
        }
        if (wireframeVBO != null) {
            VertexBuffer vbo = wireframeVBO;
            wireframeVBO = null;
            wireframeVertexCount = 0;
            RenderSystem.recordRenderCall(vbo::close);
        }
        if (lineVBO != null) {
            VertexBuffer vbo = lineVBO;
            lineVBO = null;
            lineVertexCount = 0;
            RenderSystem.recordRenderCall(vbo::close);
        }
    }

    /**
     * Get the total number of boxes in the batch.
     */
    public int getBoxCount() {
        return boxes.size();
    }

    /**
     * Get the total number of lines in the batch.
     */
    public int getLineCount() {
        return lines.size();
    }

    /**
     * Check if the batch is empty.
     */
    public boolean isEmpty() {
        return boxes.isEmpty() && lines.isEmpty();
    }

    // Internal data classes
    private record BatchedBox(AABB box, int color, boolean wireframe) {}
    private record BatchedLine(Vec3 from, Vec3 to, int color) {}
}
