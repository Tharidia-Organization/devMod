package com.frenkvs.devmod.event.common;

import com.frenkvs.devmod.config.ModConfig;
import com.frenkvs.devmod.permission.PermissionManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = "devmod", value = Dist.CLIENT) // BACK to Dist.CLIENT - will split server logic
public class AnchorEvents {

    // MEMORIA CLIENT: Qui salviamo le posizioni ricevute dal server
    private static List<Vec3> markerCache = new ArrayList<>();

    // Metodo chiamato dal NetworkHandler quando arriva un pacchetto
    public static void updateMarkerCache(List<Vec3> newPositions) {
        markerCache = newPositions;
    }

    
    // --- LOGICA CLIENT: Disegna ---
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Check if player has OP level 4 or higher
        if (!PermissionManager.isClientOp()) return;
        
        if (!ModConfig.showAnchors && !ModConfig.showMarkers) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0f);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        BufferBuilder bufferBuilder = null;
        Tesselator tesselator = Tesselator.getInstance();

        // 1. Disegna Anchor (Name Tags) - Questo usa i dati Client standard
        if (ModConfig.showAnchors) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity.hasCustomName() && entity != mc.player && !(entity instanceof Player) && !(entity instanceof Marker)) {
                    if (bufferBuilder == null) bufferBuilder = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

                    double w = entity.getBbWidth() > 0 ? entity.getBbWidth() : 0.5;
                    double h = entity.getBbHeight() > 0 ? entity.getBbHeight() : 0.5;

                    // CIANO
                    renderBox(bufferBuilder, matrix, entity.getX(), entity.getY(), entity.getZ(), w, h, 0.0f, 1.0f, 1.0f);
                    renderLine(bufferBuilder, matrix, entity.getX(), entity.getY(), entity.getZ(), 5.0, 0.0f, 1.0f, 1.0f);
                }
            }
        }

        // 2. Disegna Markers (Dati dal Server)
        if (ModConfig.showMarkers) {
            for (Vec3 pos : markerCache) {
                // Disegna solo se è "vicino" (entro 100 blocchi)
                if (pos.distanceToSqr(mc.player.position()) < 10000) {
                    if (bufferBuilder == null) bufferBuilder = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

                    // VERDE LIME
                    renderBox(bufferBuilder, matrix, pos.x, pos.y, pos.z, 0.5, 0.5, 0.0f, 1.0f, 0.0f);
                    renderLine(bufferBuilder, matrix, pos.x, pos.y, pos.z, 2.0, 0.0f, 1.0f, 0.0f);
                }
            }
        }

        if (bufferBuilder != null) {
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }

        poseStack.popPose();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    private static void renderBox(BufferBuilder builder, Matrix4f matrix, double x, double y, double z, double w, double h, float r, float g, float b) {
        float x1 = (float)(x - w/2); float x2 = (float)(x + w/2);
        float y1 = (float)y;         float y2 = (float)(y + h);
        float z1 = (float)(z - w/2); float z2 = (float)(z + w/2);
        // ... (Logica di disegno linee uguale a prima) ...
        builder.addVertex(matrix, x1, y1, z1).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x2, y1, z1).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x2, y1, z1).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x2, y1, z2).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x2, y1, z2).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x1, y1, z2).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x1, y1, z2).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x1, y1, z1).setColor(r, g, b, 1.0f);

        builder.addVertex(matrix, x1, y2, z1).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x2, y2, z1).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x2, y2, z1).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x2, y2, z2).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x2, y2, z2).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x1, y2, z2).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x1, y2, z2).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x1, y2, z1).setColor(r, g, b, 1.0f);

        builder.addVertex(matrix, x1, y1, z1).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x1, y2, z1).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x2, y1, z1).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x2, y2, z1).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x2, y1, z2).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x2, y2, z2).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x1, y1, z2).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x1, y2, z2).setColor(r, g, b, 1.0f);
    }

    private static void renderLine(BufferBuilder builder, Matrix4f matrix, double x, double y, double z, double h, float r, float g, float b) {
        builder.addVertex(matrix, (float)x, (float)y, (float)z).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, (float)x, (float)(y + h), (float)z).setColor(r, g, b, 1.0f);
    }
}