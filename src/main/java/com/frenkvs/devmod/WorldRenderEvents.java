package com.frenkvs.devmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
public class WorldRenderEvents {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Se l'utente ha disattivato il render nelle impostazioni, ci fermiamo subito
        if (!ModConfig.showRender) return;

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 cameraPos = event.getCamera().getPosition();

        // Collect all spheres to render (like wall function collects claims)
        java.util.List<SphereData> spheresToRender = new java.util.ArrayList<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Mob mob) {
                if (mob.distanceToSqr(mc.player) > 1600) continue;

                // 1. RAGGIO DI VISTA (Follow Range)
                double followRange = 0;
                if (mob.getAttribute(Attributes.FOLLOW_RANGE) != null) {
                    followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
                }

                if (followRange > 0 && followRange <= 64) {
                    double x = mob.getX() - cameraPos.x;
                    double y = mob.getY() - cameraPos.y + mob.getBbHeight() / 2.0;
                    double z = mob.getZ() - cameraPos.z;
                    spheresToRender.add(new SphereData(x, y, z, followRange, ModConfig.followRangeColor));
                }

                // 2. CERCHIO GIALLO (Linea) - Attacco
                double attackReach = 0;

                // Prima controlliamo se abbiamo impostato un valore custom
                if (mob.getAttribute(Attributes.ENTITY_INTERACTION_RANGE) != null) {
                    attackReach = mob.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
                }

                // Se è ancora 0 (o quasi), usiamo la formula vanilla di fallback
                if (attackReach <= 0.1) {
                    attackReach = mob.getBbWidth() * 2.0 + 1.0;
                }

                if (attackReach > 0) {
                    double x = mob.getX() - cameraPos.x;
                    double y = mob.getY() - cameraPos.y + mob.getBbHeight() / 2.0;
                    double z = mob.getZ() - cameraPos.z;
                    spheresToRender.add(new SphereData(x, y, z, attackReach, 0xFFFFFF00));
                }
            }
        }

        if (spheresToRender.isEmpty()) return;

        // Render all spheres in one batch (like wall function)
        renderAllSpheres(event.getPoseStack(), spheresToRender);
    }

    private static record SphereData(double x, double y, double z, double radius, int color) {}

    private static void renderAllSpheres(PoseStack poseStack, java.util.List<SphereData> spheres) {
        // Setup rendering system for wireframe lines
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0f); // Make lines thicker

        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();

        // Render all spheres as wireframes
        for (SphereData sphere : spheres) {
            renderSphereWireframe(bufferBuilder, matrix, sphere.x(), sphere.y(), sphere.z(), sphere.radius(), sphere.color());
        }

        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());

        // Restore render state
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);

        poseStack.popPose();
    }

    private static void renderSphereWireframe(BufferBuilder bufferBuilder, Matrix4f matrix,
                                              double offsetX, double offsetY, double offsetZ,
                                              double radius, int color) {
        float alpha = ((color >> 24) & 0xFF) / 255f;
        if (alpha == 0) alpha = 0.9f; // More visible for wireframe
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;
        
        // Make colors darker for wireframe
        red *= 0.8f;
        green *= 0.8f;
        blue *= 0.8f;

        // Parametri per buona qualità
        int latitudeSegments = 12;
        int longitudeSegments = 16;
        float r = (float) radius;
        
        // Draw latitude lines (horizontal circles)
        for (int lat = 0; lat <= latitudeSegments; lat++) {
            double theta = (lat * Math.PI) / latitudeSegments;
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);
            
            for (int lon = 0; lon < longitudeSegments; lon++) {
                double phi1 = (lon * 2 * Math.PI) / longitudeSegments;
                double phi2 = ((lon + 1) * 2 * Math.PI) / longitudeSegments;
                
                float x1 = sinTheta * (float) Math.cos(phi1) * r + (float)offsetX;
                float y1 = cosTheta * r + (float)offsetY;
                float z1 = sinTheta * (float) Math.sin(phi1) * r + (float)offsetZ;
                
                float x2 = sinTheta * (float) Math.cos(phi2) * r + (float)offsetX;
                float y2 = cosTheta * r + (float)offsetY;
                float z2 = sinTheta * (float) Math.sin(phi2) * r + (float)offsetZ;
                
                bufferBuilder.addVertex(matrix, x1, y1, z1)
                    .setColor(red, green, blue, alpha);
                bufferBuilder.addVertex(matrix, x2, y2, z2)
                    .setColor(red, green, blue, alpha);
            }
        }
        
        // Draw longitude lines (vertical circles)
        for (int lon = 0; lon < longitudeSegments; lon++) {
            double phi = (lon * 2 * Math.PI) / longitudeSegments;
            float cosPhi = (float) Math.cos(phi);
            float sinPhi = (float) Math.sin(phi);
            
            for (int lat = 0; lat < latitudeSegments; lat++) {
                double theta1 = (lat * Math.PI) / latitudeSegments;
                double theta2 = ((lat + 1) * Math.PI) / latitudeSegments;
                
                float x1 = (float) Math.sin(theta1) * cosPhi * r + (float)offsetX;
                float y1 = (float) Math.cos(theta1) * r + (float)offsetY;
                float z1 = (float) Math.sin(theta1) * sinPhi * r + (float)offsetZ;
                
                float x2 = (float) Math.sin(theta2) * cosPhi * r + (float)offsetX;
                float y2 = (float) Math.cos(theta2) * r + (float)offsetY;
                float z2 = (float) Math.sin(theta2) * sinPhi * r + (float)offsetZ;
                
                bufferBuilder.addVertex(matrix, x1, y1, z1)
                    .setColor(red, green, blue, alpha);
                bufferBuilder.addVertex(matrix, x2, y2, z2)
                    .setColor(red, green, blue, alpha);
            }
        }
    }
}
