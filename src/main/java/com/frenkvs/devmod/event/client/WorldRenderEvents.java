package com.frenkvs.devmod.event.client;

import com.frenkvs.devmod.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
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
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
public class WorldRenderEvents {

    // --- AGGRO LINES ---
    private static class AggroLine { int sourceId; int targetId; int age; AggroLine(int s, int t) { sourceId = s; targetId = t; age = 0; } }
    private static final List<AggroLine> activeLines = new ArrayList<>();
    public static void addAggroLine(int source, int target) { activeLines.add(new AggroLine(source, target)); }

    // --- ARROW HITS ---
    private static final List<ArrowHit> arrowHits = new ArrayList<>();
    private static class ArrowHit {
        final double x, y, z; final long timestamp; final double speed;
        ArrowHit(double x, double y, double z, long timestamp, double speed) { this.x = x; this.y = y; this.z = z; this.timestamp = timestamp; this.speed = speed; }
    }
    public static void addArrowHit(double x, double y, double z, double speed) {
        if (Minecraft.getInstance().level != null) arrowHits.add(new ArrowHit(x, y, z, Minecraft.getInstance().level.getGameTime(), speed));
    }

    // --- PATH RENDERING (NUOVO) ---
    private static class MobPathData {
        List<BlockPos> nodes; BlockPos endNode; BlockPos stuckPos; long lastUpdate;
        MobPathData(List<BlockPos> n, BlockPos e, BlockPos s) { 
            nodes = n; 
            endNode = e; 
            stuckPos = s; 
            lastUpdate = System.currentTimeMillis();
        }
    }
    private static final Map<Integer, MobPathData> activePaths = new HashMap<>();

    // Questo è il metodo che il NetworkHandler cercava!
    public static void updateMobPath(int mobId, List<BlockPos> nodes, BlockPos endNode, BlockPos stuckPos) {
        // Debug logging
        System.out.println("Path update: mobId=" + mobId + ", nodes=" + (nodes != null ? nodes.size() : 0) + 
                          ", endNode=" + endNode + ", stuckPos=" + stuckPos);
        System.out.println("Active paths before update: " + activePaths.keySet());
        
        // If path is empty or null, and no endNode/stuckPos, remove it immediately (mob reached destination)
        if ((nodes == null || nodes.isEmpty()) && endNode == null && stuckPos == null) {
            activePaths.remove(mobId);
            System.out.println("Removed path for mob " + mobId + " (reached destination)");
        } else {
            MobPathData data = new MobPathData(nodes, endNode, stuckPos);
            activePaths.put(mobId, data);
            System.out.println("Added/updated path for mob " + mobId);
        }
        
        System.out.println("Active paths after update: " + activePaths.keySet());
    }

    // --- RENDER LOOP ---
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        // 1. FRECCE
        long currentTime = mc.level.getGameTime();
        arrowHits.removeIf(hit -> currentTime - hit.timestamp > 100);
        if (ModConfig.showArrowHits && !arrowHits.isEmpty()) renderArrowHits(poseStack, cameraPos);

        // 2. PATH RENDERING (NUOVO)
        if (ModConfig.showMobPath) {
            renderPaths(poseStack, cameraPos);
        }

        // 3. SFERE MOB (Solo se showRender è attivo)
        if (ModConfig.showRender) {
            renderMobSpheres(poseStack, cameraPos, mc);
        }

        // 4. LINEE AGGRO
        if (ModConfig.showRender && !activeLines.isEmpty()) {
            renderAggroLines(poseStack, cameraPos, mc);
        }
    }

    // --- METODI DI DISEGNO ---

    private static void renderPaths(PoseStack poseStack, Vec3 cameraPos) {
        if (activePaths.isEmpty()) {
            return;
        }
        
        // Clean up old paths (remove paths not updated for 30 seconds)
        long currentTime = System.currentTimeMillis();
        activePaths.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue().lastUpdate > 30000) {
                System.out.println("Removed stale path for mob " + entry.getKey());
                return true;
            }
            return false;
        });
        
        if (activePaths.isEmpty()) {
            return;
        }
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        // Render all paths without any age-based removal
        for (Map.Entry<Integer, MobPathData> entry : activePaths.entrySet()) {
            MobPathData data = entry.getValue();

            // Render path segments as wide strips (green)
            if (data.nodes != null && !data.nodes.isEmpty()) {
                for (int i = 0; i < data.nodes.size() - 1; i++) {
                    BlockPos p1 = data.nodes.get(i);
                    BlockPos p2 = data.nodes.get(i+1);
                    renderPathStrip(bufferBuilder, matrix, p1, p2, 0.0f, 1.0f, 0.0f, 0.7f);
                }
            }

            // Render destination block as a filled cube (gold/yellow)
            if (data.endNode != null) {
                BlockPos dest = data.endNode;
                renderDestinationBlock(bufferBuilder, matrix, dest, 1.0f, 1.0f, 0.0f, 0.8f);
                
                // Also draw line from last path node to destination
                if (!data.nodes.isEmpty()) {
                    BlockPos last = data.nodes.get(data.nodes.size()-1);
                    renderPathStrip(bufferBuilder, matrix, last, dest, 1.0f, 1.0f, 0.0f, 0.6f);
                }
            }

            // Render stuck indicator as red X
            if (data.stuckPos != null) {
                renderStuckIndicator(bufferBuilder, matrix, data.stuckPos, 1.0f, 0.0f, 0.0f, 0.9f);
            }
        }
        
        com.mojang.blaze3d.vertex.MeshData mesh = bufferBuilder.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        poseStack.popPose();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderPathStrip(BufferBuilder bufferBuilder, Matrix4f matrix, 
                                          BlockPos p1, BlockPos p2, 
                                          float r, float g, float b, float a) {
        float x1 = p1.getX() + 0.5f;
        float y1 = p1.getY() + 0.02f; // Small offset to prevent Z-fighting
        float z1 = p1.getZ() + 0.5f;
        float x2 = p2.getX() + 0.5f;
        float y2 = p2.getY() + 0.02f;
        float z2 = p2.getZ() + 0.5f;
        
        // Check if this is a vertical segment
        if (Math.abs(x2 - x1) < 0.1f && Math.abs(z2 - z1) < 0.1f) {
            // Vertical segment - render as a pillar
            float minY = Math.min(y1, y2);
            float maxY = Math.max(y1, y2);
            float size = 0.15f;
            
            // Draw 4 sides of the pillar
            // North
            bufferBuilder.addVertex(matrix, x1 - size, minY, z1 + size).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1 + size, minY, z1 + size).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1 + size, maxY, z1 + size).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1 - size, maxY, z1 + size).setColor(r, g, b, a);
            
            // South
            bufferBuilder.addVertex(matrix, x1 + size, minY, z1 - size).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1 - size, minY, z1 - size).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1 - size, maxY, z1 - size).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1 + size, maxY, z1 - size).setColor(r, g, b, a);
            
            // East
            bufferBuilder.addVertex(matrix, x1 + size, minY, z1 - size).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1 + size, minY, z1 + size).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1 + size, maxY, z1 + size).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1 + size, maxY, z1 - size).setColor(r, g, b, a);
            
            // West
            bufferBuilder.addVertex(matrix, x1 - size, minY, z1 + size).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1 - size, minY, z1 - size).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1 - size, maxY, z1 - size).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1 - size, maxY, z1 + size).setColor(r, g, b, a);
            return;
        }
        
        // Horizontal segment - calculate perpendicular direction for width
        float dx = x2 - x1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01f) return; // Skip if points are too close
        
        // Normalize and get perpendicular
        dx /= len;
        dz /= len;
        float perpX = -dz * 0.25f; // Half block width
        float perpZ = dx * 0.25f;
        
        // Draw quad strip
        bufferBuilder.addVertex(matrix, x1 - perpX, y1, z1 - perpZ).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x1 + perpX, y1, z1 + perpZ).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x2 + perpX, y2, z2 + perpZ).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x2 - perpX, y2, z2 - perpZ).setColor(r, g, b, a);
    }
    
    private static void renderDestinationBlock(BufferBuilder bufferBuilder, Matrix4f matrix,
                                               BlockPos pos, float r, float g, float b, float a) {
        float x = pos.getX() + 0.5f;
        float y = pos.getY() + 0.02f;
        float z = pos.getZ() + 0.5f;
        float size = 0.4f;
        
        // Top face
        bufferBuilder.addVertex(matrix, x - size, y, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y, z + size).setColor(r, g, b, a);
        
        // Sides (short walls)
        float height = 0.1f;
        // North side
        bufferBuilder.addVertex(matrix, x - size, y, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y + height, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y + height, z + size).setColor(r, g, b, a);
        
        // South side
        bufferBuilder.addVertex(matrix, x + size, y, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y + height, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y + height, z - size).setColor(r, g, b, a);
        
        // East side
        bufferBuilder.addVertex(matrix, x + size, y, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y + height, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y + height, z - size).setColor(r, g, b, a);
        
        // West side
        bufferBuilder.addVertex(matrix, x - size, y, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y + height, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y + height, z + size).setColor(r, g, b, a);
    }
    
    private static void renderStuckIndicator(BufferBuilder bufferBuilder, Matrix4f matrix,
                                             BlockPos pos, float r, float g, float b, float a) {
        float x = pos.getX() + 0.5f;
        float y = pos.getY() + 0.02f;
        float z = pos.getZ() + 0.5f;
        float size = 0.3f;
        float height = 0.05f;
        
        // Draw X shape on ground
        bufferBuilder.addVertex(matrix, x - size, y, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size + 0.1f, y, z - size + 0.1f).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size - 0.1f, y, z + size - 0.1f).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y, z + size).setColor(r, g, b, a);
        
        bufferBuilder.addVertex(matrix, x + size, y, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size - 0.1f, y, z - size + 0.1f).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size + 0.1f, y, z + size - 0.1f).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y, z + size).setColor(r, g, b, a);
    }
    
    private static void renderAggroLines(PoseStack poseStack, Vec3 cameraPos, Minecraft mc) {
        VertexConsumer builder = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();
        Iterator<AggroLine> it = activeLines.iterator();
        while (it.hasNext()) {
            AggroLine line = it.next();
            line.age++;
            if (line.age > 40) { it.remove(); continue; }
            Entity source = mc.level.getEntity(line.sourceId);
            Entity target = mc.level.getEntity(line.targetId);
            if (source != null && target != null) {
                float alpha = 1.0f - (line.age / 40.0f);
                Vec3 sPos = source.position().add(0, source.getBbHeight()/2, 0);
                Vec3 tPos = target.position().add(0, target.getBbHeight()/2, 0);
                builder.addVertex(matrix, (float)sPos.x, (float)sPos.y, (float)sPos.z).setColor(1.0f, 0.2f, 0.0f, alpha).setNormal(0, 1, 0);
                builder.addVertex(matrix, (float)tPos.x, (float)tPos.y, (float)tPos.z).setColor(1.0f, 0.2f, 0.0f, alpha).setNormal(0, 1, 0);
            }
        }
        poseStack.popPose();
    }

    private static void renderMobSpheres(PoseStack poseStack, Vec3 cameraPos, Minecraft mc) {
        List<SphereData> spheresToRender = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Mob mob) {
                if (mob.distanceToSqr(mc.player) > Math.pow(ModConfig.renderDistanceChunks * 16, 2)) continue;

                // Follow Range
                double followRange = 0;
                if (mob.getAttribute(Attributes.FOLLOW_RANGE) != null) followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
                if (followRange > 0 && followRange <= 64) {
                    boolean isHostile = isHostileMob(mob);
                    if ((isHostile && ModConfig.renderHostileAggro) || (!isHostile && ModConfig.renderFriendlyAggro)) {
                        spheresToRender.add(new SphereData(mob.getX() - cameraPos.x, mob.getY() - cameraPos.y + mob.getBbHeight()/2, mob.getZ() - cameraPos.z, followRange, ModConfig.followRangeColor));
                    }
                }

                // Attack Range
                double attackReach = 0;
                if (mob.getAttribute(Attributes.ENTITY_INTERACTION_RANGE) != null) attackReach = mob.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
                if (attackReach <= 0.1) attackReach = mob.getBbWidth() * 2.0 + 1.0;
                if (attackReach > 0) {
                    boolean isHostile = isHostileMob(mob);
                    if ((isHostile && ModConfig.renderHostileAttack) || (!isHostile && ModConfig.renderFriendlyAttack)) {
                        spheresToRender.add(new SphereData(mob.getX() - cameraPos.x, mob.getY() - cameraPos.y + mob.getBbHeight()/2, mob.getZ() - cameraPos.z, attackReach, 0xFFFFFF00));
                    }
                }
            }
        }
        if (!spheresToRender.isEmpty()) renderAllSpheres(poseStack, spheresToRender);
    }

    private static void renderArrowHits(PoseStack poseStack, Vec3 cameraPos) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableDepthTest(); RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance(); BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Minecraft mc = Minecraft.getInstance();
        for (ArrowHit hit : arrowHits) {
            poseStack.pushPose();
            poseStack.translate(hit.x - cameraPos.x, hit.y - cameraPos.y + 0.1, hit.z - cameraPos.z);
            poseStack.mulPose(mc.gameRenderer.getMainCamera().rotation());
            Matrix4f matrix = poseStack.last().pose();
            float size = 0.3f;
            double minSpeed = 1.0; double maxSpeed = 3.2; double normalizedSpeed = Math.max(0, Math.min(1, (hit.speed - minSpeed) / (maxSpeed - minSpeed)));
            int r, g, b; if (normalizedSpeed < 0.5) { r = 0; g = (int)(255 * (normalizedSpeed * 2)); b = (int)(255 * (1 - normalizedSpeed * 2)); } else { r = (int)(255 * ((normalizedSpeed - 0.5) * 2)); g = (int)(255 * (1 - (normalizedSpeed - 0.5) * 2)); b = 0; }
            bufferBuilder.addVertex(matrix, -size, -size, 0).setColor(r, g, b, 200); bufferBuilder.addVertex(matrix, size, -size, 0).setColor(r, g, b, 200);
            bufferBuilder.addVertex(matrix, size, size, 0).setColor(r, g, b, 200); bufferBuilder.addVertex(matrix, -size, size, 0).setColor(r, g, b, 200);
            poseStack.popPose();
        }
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        RenderSystem.enableDepthTest(); RenderSystem.disableBlend();
    }

    // Helper SFERE (Compatto)
    private static record SphereData(double x, double y, double z, double radius, int color) {}
    private static void renderAllSpheres(PoseStack poseStack, List<SphereData> spheres) {
        if (ModConfig.sphereRenderMode == ModConfig.SphereRenderMode.FILLED) {
            renderAllSpheresFilled(poseStack, spheres);
        } else {
            renderAllSpheresWireframe(poseStack, spheres);
        }
    }
    
    private static void renderAllSpheresFilled(PoseStack poseStack, List<SphereData> spheres) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        RenderSystem.enableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();

        for (SphereData s : spheres) {
            float a = 0.15f;
            float red = ((s.color >> 16) & 0xFF) / 255f;
            float grn = ((s.color >> 8) & 0xFF) / 255f;
            float blu = (s.color & 0xFF) / 255f;

            renderSphereFilled(bufferBuilder, matrix, s.x, s.y, s.z, s.radius, red, grn, blu, a);
        }

        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend();
    }
    
    private static void renderAllSpheresWireframe(PoseStack poseStack, List<SphereData> spheres) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader); RenderSystem.lineWidth(2.0f);
        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        for (SphereData s : spheres) renderSphereWireframe(bufferBuilder, matrix, s.x, s.y, s.z, s.radius, s.color);
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend(); RenderSystem.lineWidth(1.0f);
    }
    private static void renderSphereWireframe(BufferBuilder b, Matrix4f m, double x, double y, double z, double rad, int col) {
        float a = ((col >> 24) & 0xFF) / 255f; if (a == 0) a = 0.9f;
        float red = ((col >> 16) & 0xFF) / 255f * 0.8f; float grn = ((col >> 8) & 0xFF) / 255f * 0.8f; float blu = (col & 0xFF) / 255f * 0.8f;
        int lat = 12; int lon = 16; float r = (float) rad;
        for (int i = 0; i <= lat; i++) { double t = (i * Math.PI) / lat; float st = (float)Math.sin(t), ct = (float)Math.cos(t);
            for (int j = 0; j < lon; j++) { double p1 = (j * 2 * Math.PI) / lon; double p2 = ((j + 1) * 2 * Math.PI) / lon;
                b.addVertex(m, st*(float)Math.cos(p1)*r+(float)x, ct*r+(float)y, st*(float)Math.sin(p1)*r+(float)z).setColor(red, grn, blu, a);
                b.addVertex(m, st*(float)Math.cos(p2)*r+(float)x, ct*r+(float)y, st*(float)Math.sin(p2)*r+(float)z).setColor(red, grn, blu, a); } }
        for (int j = 0; j < lon; j++) { double p = (j * 2 * Math.PI) / lon; float cp = (float)Math.cos(p), sp = (float)Math.sin(p);
            for (int i = 0; i < lat; i++) { double t1 = (i * Math.PI) / lat; double t2 = ((i + 1) * Math.PI) / lat;
                b.addVertex(m, (float)Math.sin(t1)*cp*r+(float)x, (float)Math.cos(t1)*r+(float)y, (float)Math.sin(t1)*sp*r+(float)z).setColor(red, grn, blu, a);
                b.addVertex(m, (float)Math.sin(t2)*cp*r+(float)x, (float)Math.cos(t2)*r+(float)y, (float)Math.sin(t2)*sp*r+(float)z).setColor(red, grn, blu, a); } }
    }
    private static void renderSphereFilled(BufferBuilder bufferBuilder, Matrix4f matrix,
                                       double x, double y, double z, double radius,
                                       float red, float green, float blue, float alpha) {
        int latitudes = 8;
        int longitudes = 16;

        float cx = (float) x;
        float cy = (float) y;
        float cz = (float) z;
        float r = (float) radius;

        for (int lat = 0; lat < latitudes; lat++) {
            float theta1 = lat * (float) Math.PI / latitudes;
            float theta2 = (lat + 1) * (float) Math.PI / latitudes;

            for (int lon = 0; lon < longitudes; lon++) {
                float phi1 = lon * 2 * (float) Math.PI / longitudes;
                float phi2 = (lon + 1) * 2 * (float) Math.PI / longitudes;

                float x1 = r * (float) (Math.sin(theta1) * Math.cos(phi1));
                float y1 = r * (float) Math.cos(theta1);
                float z1 = r * (float) (Math.sin(theta1) * Math.sin(phi1));

                float x2 = r * (float) (Math.sin(theta1) * Math.cos(phi2));
                float y2 = r * (float) Math.cos(theta1);
                float z2 = r * (float) (Math.sin(theta1) * Math.sin(phi2));

                float x3 = r * (float) (Math.sin(theta2) * Math.cos(phi2));
                float y3 = r * (float) Math.cos(theta2);
                float z3 = r * (float) (Math.sin(theta2) * Math.sin(phi2));

                float x4 = r * (float) (Math.sin(theta2) * Math.cos(phi1));
                float y4 = r * (float) Math.cos(theta2);
                float z4 = r * (float) (Math.sin(theta2) * Math.sin(phi1));

                quadSimple(bufferBuilder, matrix,
                    cx + x1, cy + y1, cz + z1,
                    cx + x2, cy + y2, cz + z2,
                    cx + x3, cy + y3, cz + z3,
                    cx + x4, cy + y4, cz + z4,
                    red, green, blue, alpha);
            }
        }
    }
    
    private static void quadSimple(BufferBuilder bufferBuilder, Matrix4f matrix,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2,
                                   float x3, float y3, float z3,
                                   float x4, float y4, float z4,
                                   float red, float green, float blue, float alpha) {
        bufferBuilder.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(matrix, x3, y3, z3).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(matrix, x4, y4, z4).setColor(red, green, blue, alpha);
    }
    
    private static boolean isHostileMob(Mob mob) {
        String n = mob.getType().toString().toLowerCase();
        return n.contains("zombie")||n.contains("skeleton")||n.contains("creeper")||n.contains("spider")||n.contains("enderman")||n.contains("witch")||n.contains("piglin")||n.contains("ghast")||n.contains("blaze")||n.contains("wither")||n.contains("dragon")||n.contains("slime")||n.contains("pillager")||n.contains("vindicator")||n.contains("ravager")||n.contains("evoker")||n.contains("vex")||n.contains("illusioner");
    }
}