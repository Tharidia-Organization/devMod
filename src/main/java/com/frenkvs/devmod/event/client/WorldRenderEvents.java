package com.frenkvs.devmod.event.client;

import com.frenkvs.devmod.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
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

    // --- SISTEMA LINEE AGGRO ---
    private static class AggroLine {
        int sourceId;
        int targetId;
        int age;
        AggroLine(int s, int t) { sourceId = s; targetId = t; age = 0; }
    }

    private static final java.util.List<AggroLine> activeLines = new java.util.ArrayList<>();

    public static void addAggroLine(int source, int target) {
        activeLines.add(new AggroLine(source, target));
    }
    // ---------------------------

    // --- SISTEMA PATH RENDERING ---
    private static class MobPathData {
        java.util.List<BlockPos> pathNodes;
        BlockPos endNode;
        BlockPos stuckPos;
        long lastUpdate;
        
        MobPathData(java.util.List<BlockPos> nodes, BlockPos end, BlockPos stuck) {
            this.pathNodes = nodes;
            this.endNode = end;
            this.stuckPos = stuck;
            this.lastUpdate = System.currentTimeMillis();
        }
    }

    private static final java.util.Map<Integer, MobPathData> mobPaths = new java.util.HashMap<>();

    public static void updateMobPath(int mobId, java.util.List<BlockPos> pathNodes, BlockPos endNode, BlockPos stuckPos) {
        mobPaths.put(mobId, new MobPathData(pathNodes, endNode, stuckPos));
        // Debug log
        /*System.out.println("[DevMod] Received path data for mob " + mobId + ": " +
            pathNodes.size() + " nodes, endNode=" + endNode + ", stuckPos=" + stuckPos);*/
    }
    // ---------------------------

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

        // Render mob paths
        if (!mobPaths.isEmpty()) {
            renderMobPaths(event.getPoseStack(), cameraPos);
        }

        if (spheresToRender.isEmpty()) return;

        // Render all spheres in one batch (like wall function)
        renderAllSpheres(event.getPoseStack(), spheresToRender);

        // --- RENDER LINEE AGGRO ---
        if (!activeLines.isEmpty()) {
            // QUESTA È LA RIGA CHE MANCAVA:
            PoseStack poseStack = event.getPoseStack();

            // Imposta spessore linea più spesso per le linee aggro
            RenderSystem.lineWidth(4.0f);
            
            VertexConsumer builder = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            Matrix4f matrix = poseStack.last().pose();

            java.util.Iterator<AggroLine> it = activeLines.iterator();
            while (it.hasNext()) {
                AggroLine line = it.next();
                line.age++;

                // Rimuovi dopo 2 secondi (40 tick)
                if (line.age > 40) {
                    it.remove();
                    continue;
                }

                Entity source = mc.level.getEntity(line.sourceId);
                Entity target = mc.level.getEntity(line.targetId);

                if (source != null && target != null) {
                    float alpha = 1.0f - (line.age / 40.0f);

                    Vec3 sPos = source.position().add(0, source.getBbHeight() / 2, 0);
                    Vec3 tPos = target.position().add(0, target.getBbHeight() / 2, 0);

                    // Disegna linea Arancione/Rossa
                    builder.addVertex(matrix, (float)sPos.x, (float)sPos.y, (float)sPos.z)
                            .setColor(1.0f, 0.2f, 0.0f, alpha).setNormal(0, 1, 0);
                    builder.addVertex(matrix, (float)tPos.x, (float)tPos.y, (float)tPos.z)
                            .setColor(1.0f, 0.2f, 0.0f, alpha).setNormal(0, 1, 0);
                }
            }
            poseStack.popPose();
            
            // Ripristina spessore linea di default
            RenderSystem.lineWidth(1.0f);
        }
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

    private static void renderMobPaths(PoseStack poseStack, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        long currentTime = System.currentTimeMillis();
        
        // Clean up old paths (older than 2 seconds)
        mobPaths.entrySet().removeIf(entry -> currentTime - entry.getValue().lastUpdate > 2000);
        
        if (mobPaths.isEmpty()) return;
        
        // Setup render state
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        
        for (MobPathData pathData : mobPaths.values()) {
            // Render path nodes (green squares)
            if (pathData.pathNodes != null) {
                for (BlockPos pos : pathData.pathNodes) {
                    addBlockSquare(bufferBuilder, matrix, pos, 0, 255, 0, 180); // Green
                }
            }
            
            // Render end node (cyan blue square)
            if (pathData.endNode != null) {
                addBlockSquare(bufferBuilder, matrix, pathData.endNode, 0, 150, 255, 220); // Cyan blue
            }
            
            // Render stuck position (red pulsing square)
            if (pathData.stuckPos != null) {
                // Pulsing effect
                float pulse = (float) (Math.sin(currentTime / 200.0) * 0.5 + 0.5);
                int alpha = (int) (150 + pulse * 105);
                addBlockSquare(bufferBuilder, matrix, pathData.stuckPos, 255, 0, 0, alpha); // Red
            }
        }
        
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        
        poseStack.popPose();
        
        // Restore render state
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void addBlockSquare(BufferBuilder bufferBuilder, Matrix4f matrix, BlockPos pos, int red, int green, int blue, int alpha) {
        // Position on top of the block
        float x = pos.getX();
        float y = pos.getY() + 0.25f; // 0.25 blocks above to avoid z-fighting
        float z = pos.getZ();
        
        // Draw a square on top of the block (1x1)
        // We need to draw both sides to be visible from any angle
        
        // Top face (visible from above)
        bufferBuilder.addVertex(matrix, x, y, z)
            .setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(matrix, x + 1, y, z)
            .setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(matrix, x + 1, y, z + 1)
            .setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(matrix, x, y, z + 1)
            .setColor(red, green, blue, alpha);
        
        // Bottom face (visible from below)
        bufferBuilder.addVertex(matrix, x, y, z + 1)
            .setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(matrix, x + 1, y, z + 1)
            .setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(matrix, x + 1, y, z)
            .setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(matrix, x, y, z)
            .setColor(red, green, blue, alpha);
    }
}
