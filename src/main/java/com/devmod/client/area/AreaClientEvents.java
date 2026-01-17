package com.devmod.client.area;

import java.util.Objects;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.devmod.DevMod;
import com.devmod.area.network.AreaPreviewPayload;

/**
 * Client-side event handlers for Area Builder system.
 * Handles holographic preview rendering and cleanup on disconnect.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
public final class AreaClientEvents {

    private AreaClientEvents() {}

    private static long lastLogTime = 0;

    /**
     * Clear preview when player disconnects.
     */
    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DevMod.LOGGER.debug("[Area] Player logout - clearing preview");
        AreaPreviewRenderer.INSTANCE.clearPreview();
    }

    /**
     * Render area preview hologram in the world.
     * Shows floor, walls, and ceiling as semi-transparent colored blocks.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // Only render after translucent blocks for proper blending
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        // UX-05 fix: Use atomic method to prevent race condition where preview could expire
        // between hasRenderable() check and getPreview()/getVertexBuffer() calls
        AreaPreviewRenderer.RenderData renderData = AreaPreviewRenderer.INSTANCE.getRenderDataIfValid();
        if (renderData == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Log every 2 seconds to avoid spam
        long now = System.currentTimeMillis();
        if (now - lastLogTime > 2000) {
            AreaPreviewPayload preview = renderData.preview();
            DevMod.LOGGER.info("[Area] Rendering hologram preview: center={}, {}x{}x{}",
                preview.center(), preview.width(), preview.length(), preview.height());
            lastLogTime = now;
        }

        // Get camera position for translation
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();

        // Translate to world position (offset by camera)
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Render the holographic mesh
        renderHologramVBO(poseStack, renderData.vertexBuffer());

        poseStack.popPose();
    }

    /**
     * Renders the hologram VBO with proper blending and depth settings.
     */
    private static void renderHologramVBO(PoseStack poseStack, VertexBuffer vertexBuffer) {
        RenderSystem.assertOnRenderThread();

        // Setup render state for translucent hologram
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc(); // SRC_ALPHA, ONE_MINUS_SRC_ALPHA
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false); // Don't write to depth buffer (see-through)
        RenderSystem.disableCull(); // Render both sides of faces

        // Get shader
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            return;
        }

        // Build matrices
        Matrix4f modelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        modelViewMatrix.mul(poseStack.last().pose());
        Matrix4f projectionMatrix = RenderSystem.getProjectionMatrix();

        // Set shader uniforms
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(modelViewMatrix);
        }
        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(Objects.requireNonNull(projectionMatrix));
        }
        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // Draw the VBO
        vertexBuffer.bind();
        shader.apply();
        vertexBuffer.draw();
        VertexBuffer.unbind();
        shader.clear();

        // Restore render state
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
