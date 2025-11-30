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
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;

@EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
public class WorldRenderEvents {

    // Arrow hit tracking system
    private static final java.util.List<ArrowHit> arrowHits = new java.util.ArrayList<>();
    
    private static class ArrowHit {
        final double x, y, z;
        final long timestamp;
        final double speed;
        
        ArrowHit(double x, double y, double z, long timestamp, double speed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = timestamp;
            this.speed = speed;
        }
    }
    
    public static void addArrowHit(double x, double y, double z, double speed) {
        if (Minecraft.getInstance().level == null) return;
        long currentTime = Minecraft.getInstance().level.getGameTime();
        arrowHits.add(new ArrowHit(x, y, z, currentTime, speed));
    }

    private static boolean isHostileMob(Mob mob) {
        // Controlla se il mob è ostile basandosi sul suo tipo
        String mobName = mob.getType().toString().toLowerCase();
        return mobName.contains("zombie") || mobName.contains("skeleton") || mobName.contains("creeper") ||
               mobName.contains("spider") || mobName.contains("enderman") || mobName.contains("witch") ||
               mobName.contains("piglin") || mobName.contains("ghast") || mobName.contains("blaze") ||
               mobName.contains("wither") || mobName.contains("dragon") || mobName.contains("slime") ||
               mobName.contains("pillager") || mobName.contains("vindicator") || mobName.contains("ravager") ||
               mobName.contains("evoker") || mobName.contains("vex") || mobName.contains("illusioner");
    }

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
                if (mob.distanceToSqr(mc.player) > Math.pow(ModConfig.renderDistanceChunks * 16, 2)) continue;

                // 1. RAGGIO DI VISTA (Follow Range)
                double followRange = 0;
                if (mob.getAttribute(Attributes.FOLLOW_RANGE) != null) {
                    followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
                }

                if (followRange > 0 && followRange <= 64) {
                    boolean isHostile = isHostileMob(mob);
                    if ((isHostile && ModConfig.renderHostileAggro) || (!isHostile && ModConfig.renderFriendlyAggro)) {
                        double x = mob.getX() - cameraPos.x;
                        double y = mob.getY() - cameraPos.y + mob.getBbHeight() / 2.0;
                        double z = mob.getZ() - cameraPos.z;
                        spheresToRender.add(new SphereData(x, y, z, followRange, ModConfig.followRangeColor));
                    }
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
                    boolean isHostile = isHostileMob(mob);
                    if ((isHostile && ModConfig.renderHostileAttack) || (!isHostile && ModConfig.renderFriendlyAttack)) {
                        double x = mob.getX() - cameraPos.x;
                        double y = mob.getY() - cameraPos.y + mob.getBbHeight() / 2.0;
                        double z = mob.getZ() - cameraPos.z;
                        spheresToRender.add(new SphereData(x, y, z, attackReach, 0xFFFFFF00));
                    }
                }
                // 3. ATTACCO RANGED (Viola) - ADATTATO AL NUOVO SISTEMA
                double rangedDist = getRangedAttackRange(mob);
                if (rangedDist > 0) {
                    // Invece di renderCircle, aggiungiamo i dati alla lista spheresToRender
                    double x = mob.getX() - cameraPos.x;
                    double y = mob.getY() - cameraPos.y + mob.getBbHeight() / 2.0;
                    double z = mob.getZ() - cameraPos.z;

                    // Colore Viola: 0xFFAA00FF
                    spheresToRender.add(new SphereData(x, y, z, rangedDist, 0xFFAA00FF));
                }
            }
        }

        // Clean up expired arrow hits (older than 5 seconds = 100 ticks)
        long currentTime = Minecraft.getInstance().level.getGameTime();
        arrowHits.removeIf(hit -> currentTime - hit.timestamp > 100);

        // Render blue squares for active arrow hits
        if (!arrowHits.isEmpty()) {
            renderArrowHits(event.getPoseStack(), cameraPos);
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

    private static void renderArrowHits(PoseStack poseStack, Vec3 cameraPos) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        Minecraft mc = Minecraft.getInstance();
        
        for (ArrowHit hit : arrowHits) {
            poseStack.pushPose();
            
            // Translate to hit position
            poseStack.translate(hit.x - cameraPos.x, hit.y - cameraPos.y + 0.1, hit.z - cameraPos.z);
            
            // Billboard rotation - make square face the camera
            poseStack.mulPose(mc.gameRenderer.getMainCamera().rotation());
            
            BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f matrix = poseStack.last().pose();
            
            float size = 0.3f; // Small square size (0.6 blocks wide)
            
            // Map speed to color gradient: light blue (min speed) -> red (max speed)
            // Normal arrow speeds range from ~1.0 (slow) to ~3.5 (fast), max possible ~4.0
            double minSpeed = 1.0;
            double maxSpeed = 3.2;
            double normalizedSpeed = Math.max(0, Math.min(1, (hit.speed - minSpeed) / (maxSpeed - minSpeed)));
            
            // Color interpolation: light blue -> cyan -> green -> yellow -> red
            int red, green, blue;
            if (normalizedSpeed < 0.25) {
                // Light blue to cyan
                double t = normalizedSpeed * 4;
                red = 0;
                green = (int)(128 + 127 * t);
                blue = 255;
            } else if (normalizedSpeed < 0.5) {
                // Cyan to green
                double t = (normalizedSpeed - 0.25) * 4;
                red = 0;
                green = 255;
                blue = (int)(255 * (1 - t));
            } else if (normalizedSpeed < 0.75) {
                // Green to yellow
                double t = (normalizedSpeed - 0.5) * 4;
                red = (int)(255 * t);
                green = 255;
                blue = 0;
            } else {
                // Yellow to red
                double t = (normalizedSpeed - 0.75) * 4;
                red = 255;
                green = (int)(255 * (1 - t));
                blue = 0;
            }
            
            int alpha = 220;   // Semi-transparent
            
            // Draw billboard square (always faces camera)
            // Bottom-left, Bottom-right, Top-right, Top-left
            bufferBuilder.addVertex(matrix, -size, -size, 0)
                .setColor(red, green, blue, alpha);
            bufferBuilder.addVertex(matrix, size, -size, 0)
                .setColor(red, green, blue, alpha);
            bufferBuilder.addVertex(matrix, size, size, 0)
                .setColor(red, green, blue, alpha);
            bufferBuilder.addVertex(matrix, -size, size, 0)
                .setColor(red, green, blue, alpha);
            
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
            
            poseStack.popPose();
        }
        
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
    private static double getRangedAttackRange(Mob mob) {
        if (mob instanceof AbstractSkeleton) return 15.0;
        if (mob instanceof Pillager) return 24.0;
        if (mob instanceof Blaze) return 48.0;
        if (mob instanceof Ghast) return 64.0;
        if (mob instanceof Witch) return 10.0;
        if (mob instanceof Guardian) return 15.0;

        // Controllo armi generiche
        if (mob.getMainHandItem().getItem() instanceof BowItem) return 20.0;
        if (mob.getMainHandItem().getItem() instanceof CrossbowItem) return 24.0;
        if (mob.getMainHandItem().getItem() instanceof TridentItem) return 20.0;

        return 0.0;
    }
}
