package com.frenkvs.devmod.event.client;

import com.frenkvs.devmod.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
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
        // If path is empty or null, and no endNode/stuckPos, remove it immediately (mob reached destination)
        if ((nodes == null || nodes.isEmpty()) && endNode == null && stuckPos == null) {
            activePaths.remove(mobId);
        } else {
            MobPathData data = new MobPathData(nodes, endNode, stuckPos);
            activePaths.put(mobId, data);
        }
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
                return true;
            }
            return false;
        });
        
        if (activePaths.isEmpty()) {
            return;
        }
        
        // Complex animation system
        float time = currentTime * 0.001f; // Convert to seconds
        float pulse1 = (float) Math.sin(time * Math.PI) * 0.5f + 0.5f;
        float pulse2 = (float) Math.sin(time * Math.PI * 1.5f) * 0.5f + 0.5f;
        float pulse3 = (float) Math.sin(time * Math.PI * 2.0f) * 0.5f + 0.5f;
        
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

        // Render all paths with sci-fi effects
        for (Map.Entry<Integer, MobPathData> entry : activePaths.entrySet()) {
            MobPathData data = entry.getValue();

            if (data.nodes != null && !data.nodes.isEmpty()) {
                // 1. DNA Helix path structure
                renderDNAHelixPath(bufferBuilder, matrix, data.nodes, time, pulse1, pulse2);
                
                // 2. Energy flow particles
                renderEnergyParticles(bufferBuilder, matrix, data.nodes, time, pulse3);
                
                // 3. Hexagonal node markers
                renderHexagonalNodes(bufferBuilder, matrix, data.nodes, pulse1);
            }

            // 4. Holographic destination with quantum effects
            if (data.endNode != null) {
                renderHolographicDestination(bufferBuilder, matrix, data.endNode, time, pulse1, pulse2, pulse3);
            }

            // 5. Quantum stuck indicator with probability clouds
            if (data.stuckPos != null) {
                renderQuantumStuckIndicator(bufferBuilder, matrix, data.stuckPos, time, pulse1, pulse2);
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

    private static void renderDNAHelixPath(BufferBuilder bufferBuilder, Matrix4f matrix, 
                                                   List<BlockPos> nodes, float time, float pulse1, float pulse2) {
        if (nodes.size() < 2) return;
        
        // Render double helix structure along the path
        for (int i = 0; i < nodes.size() - 1; i++) {
            BlockPos p1 = nodes.get(i);
            BlockPos p2 = nodes.get(i+1);
            
            float x1 = p1.getX() + 0.5f;
            float y1 = p1.getY() + 0.5f;
            float z1 = p1.getZ() + 0.5f;
            float x2 = p2.getX() + 0.5f;
            float y2 = p2.getY() + 0.5f;
            float z2 = p2.getZ() + 0.5f;
            
            // Calculate helix parameters
            float helixPhase = time * 2 + i * 0.5f;
            float helixRadius = 0.2f;
            
            // First strand of helix
            float strand1X = (float) Math.cos(helixPhase) * helixRadius;
            float strand1Z = (float) Math.sin(helixPhase) * helixRadius;
            
            // Second strand of helix (opposite phase)
            float strand2X = (float) Math.cos(helixPhase + Math.PI) * helixRadius;
            float strand2Z = (float) Math.sin(helixPhase + Math.PI) * helixRadius;
            
            // Color gradient from cyan to purple
            float progress = (float) i / (nodes.size() - 1);
            float r = 0.2f + progress * 0.8f;
            float g = 0.5f + (1.0f - progress) * 0.5f;
            float b = 1.0f - progress * 0.5f;
            
            // Render helix strands as connected segments
            renderHelixSegment(bufferBuilder, matrix, x1, y1, z1, x2, y2, z2, 
                              strand1X, strand1Z, r, g, b, 0.8f * pulse1);
            renderHelixSegment(bufferBuilder, matrix, x1, y1, z1, x2, y2, z2, 
                              strand2X, strand2Z, r * 0.7f, g * 0.7f, b, 0.8f * pulse2);
            
            // Connect the strands with glowing rungs
            if (i % 2 == 0) {
                renderHelixRung(bufferBuilder, matrix, x1, y1, z1, 
                               strand1X, strand1Z, strand2X, strand2Z, 
                               r, g, b, 0.4f * pulse1);
            }
        }
    }
    
    private static void renderHelixSegment(BufferBuilder bufferBuilder, Matrix4f matrix,
                                          float x1, float y1, float z1, float x2, float y2, float z2,
                                          float offsetX, float offsetZ, float r, float g, float b, float a) {
        // Calculate perpendicular for width
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 0.01f) return;
        
        dx /= len; dy /= len; dz /= len;
        
        // Create perpendicular vectors for tube
        float perp1X = -dz * 0.05f;
        float perp1Y = 0;
        float perp1Z = dx * 0.05f;
        
        float perp2X = dy * perp1Z - dz * perp1Y;
        float perp2Y = dz * perp1X - dx * perp1Z;
        float perp2Z = dx * perp1Y - dy * perp1X;
        
        // Render tube segments
        for (int angle = 0; angle < 360; angle += 60) {
            float rad = (float) Math.toRadians(angle);
            float px = (float) (Math.cos(rad) * perp1X + Math.sin(rad) * perp2X);
            float py = (float) (Math.cos(rad) * perp1Y + Math.sin(rad) * perp2Y);
            float pz = (float) (Math.cos(rad) * perp1Z + Math.sin(rad) * perp2Z);
            
            float rad2 = (float) Math.toRadians(angle + 60);
            float px2 = (float) (Math.cos(rad2) * perp1X + Math.sin(rad2) * perp2X);
            float py2 = (float) (Math.cos(rad2) * perp1Y + Math.sin(rad2) * perp2Y);
            float pz2 = (float) (Math.cos(rad2) * perp1Z + Math.sin(rad2) * perp2Z);
            
            bufferBuilder.addVertex(matrix, x1 + offsetX + px, y1 + py, z1 + offsetZ + pz).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x1 + offsetX + px2, y1 + py2, z1 + offsetZ + pz2).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x2 + offsetX + px2, y2 + py2, z2 + offsetZ + pz2).setColor(r, g, b, a);
            bufferBuilder.addVertex(matrix, x2 + offsetX + px, y2 + py, z2 + offsetZ + pz).setColor(r, g, b, a);
        }
    }
    
    private static void renderHelixRung(BufferBuilder bufferBuilder, Matrix4f matrix,
                                       float x, float y, float z,
                                       float x1, float z1, float x2, float z2,
                                       float r, float g, float b, float a) {
        // Connect the two helix strands
        bufferBuilder.addVertex(matrix, x + x1, y, z + z1).setColor(r, g, b, a * 0.5f);
        bufferBuilder.addVertex(matrix, x + x2, y, z + z2).setColor(r, g, b, a * 0.5f);
        bufferBuilder.addVertex(matrix, x + x2, y + 0.1f, z + z2).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + x1, y + 0.1f, z + z1).setColor(r, g, b, a);
    }
    
    private static void renderEnergyParticles(BufferBuilder bufferBuilder, Matrix4f matrix,
                                                   List<BlockPos> nodes, float time, float pulse3) {
        if (nodes.size() < 2) return;
        
        // Calculate number of particles based on path length
        int totalParticles = Math.min(20, nodes.size() * 2);
        float pathLength = nodes.size() - 1;
        
        for (int p = 0; p < totalParticles; p++) {
            // Each particle has its own phase offset for variety
            float particlePhase = (p / (float) totalParticles) * (float) Math.PI * 2.0f;
            float particleTime = time * 3 + particlePhase;
            
            // Calculate position along path (0 to 1)
            float pathPosition = (particleTime % ((float) Math.PI * 2.0f)) / ((float) Math.PI * 2.0f);
            
            // Find current segment
            int segmentIndex = (int) (pathPosition * pathLength);
            if (segmentIndex >= nodes.size() - 1) segmentIndex = nodes.size() - 2;
            
            float segmentProgress = (pathPosition * pathLength) - segmentIndex;
            
            BlockPos p1 = nodes.get(segmentIndex);
            BlockPos p2 = nodes.get(segmentIndex + 1);
            
            float x1 = p1.getX() + 0.5f;
            float y1 = p1.getY() + 0.5f;
            float z1 = p1.getZ() + 0.5f;
            float x2 = p2.getX() + 0.5f;
            float y2 = p2.getY() + 0.5f;
            float z2 = p2.getZ() + 0.5f;
            
            // Interpolate position
            float px = x1 + (x2 - x1) * segmentProgress;
            float py = y1 + (y2 - y1) * segmentProgress;
            float pz = z1 + (z2 - z1) * segmentProgress;
            
            // Add spiral motion around path
            float spiralRadius = 0.15f;
            float spiralAngle = particleTime * 5;
            float spiralX = (float) Math.cos(spiralAngle) * spiralRadius;
            float spiralZ = (float) Math.sin(spiralAngle) * spiralRadius;
            
            // Particle color shifts from white to blue
            float particleR = 1.0f - segmentProgress * 0.5f;
            float particleG = 1.0f - segmentProgress * 0.3f;
            float particleB = 1.0f;
            
            // Particle size and alpha pulse
            float particleSize = 0.05f + 0.03f * (float) Math.sin(particleTime * 10);
            float particleAlpha = 0.8f + 0.2f * pulse3;
            
            // Render glowing particle
            renderEnergyParticle(bufferBuilder, matrix, px + spiralX, py, pz + spiralZ,
                                particleSize, particleR, particleG, particleB, particleAlpha);
            
            // Add trailing effect
            for (int trail = 1; trail <= 3; trail++) {
                float trailTime = particleTime - trail * 0.1f;
                float trailProgress = Math.max(0, segmentProgress - trail * 0.05f);
                float trailX = x1 + (x2 - x1) * trailProgress;
                float trailY = y1 + (y2 - y1) * trailProgress;
                float trailZ = z1 + (z2 - z1) * trailProgress;
                
                float trailSpiralX = (float) Math.cos(trailTime * 5) * spiralRadius;
                float trailSpiralZ = (float) Math.sin(trailTime * 5) * spiralRadius;
                
                float trailAlpha = particleAlpha * (1.0f - trail * 0.3f);
                float trailSize = particleSize * (1.0f - trail * 0.2f);
                
                renderEnergyParticle(bufferBuilder, matrix, trailX + trailSpiralX, trailY, trailZ + trailSpiralZ,
                                    trailSize, particleR, particleG, particleB, trailAlpha);
            }
        }
    }
    
    private static void renderEnergyParticle(BufferBuilder bufferBuilder, Matrix4f matrix,
                                            float x, float y, float z, float size,
                                            float r, float g, float b, float a) {
        // Render particle as glowing cube
        bufferBuilder.addVertex(matrix, x - size, y - size, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y - size, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y + size, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y + size, z - size).setColor(r, g, b, a);
        
        bufferBuilder.addVertex(matrix, x + size, y - size, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y - size, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y + size, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y + size, z - size).setColor(r, g, b, a);
        
        bufferBuilder.addVertex(matrix, x + size, y - size, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y - size, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y + size, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y + size, z + size).setColor(r, g, b, a);
        
        bufferBuilder.addVertex(matrix, x - size, y - size, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y - size, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y + size, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y + size, z + size).setColor(r, g, b, a);
        
        // Top and bottom
        bufferBuilder.addVertex(matrix, x - size, y + size, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y + size, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y + size, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y + size, z + size).setColor(r, g, b, a);
        
        bufferBuilder.addVertex(matrix, x - size, y - size, z - size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x - size, y - size, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y - size, z + size).setColor(r, g, b, a);
        bufferBuilder.addVertex(matrix, x + size, y - size, z - size).setColor(r, g, b, a);
    }
    private static void renderHexagonalNodes(BufferBuilder bufferBuilder, Matrix4f matrix,
                                                   List<BlockPos> nodes, float pulse1) {
        // Render hexagonal markers at key path nodes
        for (int i = 0; i < nodes.size(); i += 3) { // Every 3rd node
            BlockPos pos = nodes.get(i);
            float x = pos.getX() + 0.5f;
            float y = pos.getY() + 0.02f;
            float z = pos.getZ() + 0.5f;
            
            // Pulsing size
            float nodeSize = 0.1f + 0.05f * pulse1;
            
            // Color gradient from cyan to purple
            float progress = (float) i / (nodes.size() - 1);
            float r = 0.2f + progress * 0.8f;
            float g = 0.5f + (1.0f - progress) * 0.5f;
            float b = 1.0f - progress * 0.5f;
            
            // Draw hexagon
            for (int j = 0; j < 6; j++) {
                float angle1 = (float) Math.toRadians(j * 60);
                float angle2 = (float) Math.toRadians((j + 1) * 60);
                
                float x1 = x + (float) Math.cos(angle1) * nodeSize;
                float z1 = z + (float) Math.sin(angle1) * nodeSize;
                float x2 = x + (float) Math.cos(angle2) * nodeSize;
                float z2 = z + (float) Math.sin(angle2) * nodeSize;
                
                bufferBuilder.addVertex(matrix, x, y, z).setColor(r, g, b, 0.8f * pulse1);
                bufferBuilder.addVertex(matrix, x1, y, z1).setColor(r, g, b, 0.6f * pulse1);
                bufferBuilder.addVertex(matrix, x2, y, z2).setColor(r, g, b, 0.6f * pulse1);
                bufferBuilder.addVertex(matrix, x, y, z).setColor(r, g, b, 0.8f * pulse1);
            }
            
            // Inner hexagon with different alpha
            for (int j = 0; j < 6; j++) {
                float angle1 = (float) Math.toRadians(j * 60);
                float angle2 = (float) Math.toRadians((j + 1) * 60);
                
                float x1 = x + (float) Math.cos(angle1) * nodeSize * 0.5f;
                float z1 = z + (float) Math.sin(angle1) * nodeSize * 0.5f;
                float x2 = x + (float) Math.cos(angle2) * nodeSize * 0.5f;
                float z2 = z + (float) Math.sin(angle2) * nodeSize * 0.5f;
                
                bufferBuilder.addVertex(matrix, x, y + 0.01f, z).setColor(r * 0.5f, g * 0.5f, b, 0.9f * pulse1);
                bufferBuilder.addVertex(matrix, x1, y + 0.01f, z1).setColor(r * 0.5f, g * 0.5f, b, 0.7f * pulse1);
                bufferBuilder.addVertex(matrix, x2, y + 0.01f, z2).setColor(r * 0.5f, g * 0.5f, b, 0.7f * pulse1);
                bufferBuilder.addVertex(matrix, x, y + 0.01f, z).setColor(r * 0.5f, g * 0.5f, b, 0.9f * pulse1);
            }
        }
    }
    
    private static void renderHolographicDestination(BufferBuilder bufferBuilder, Matrix4f matrix,
                                                       BlockPos pos, float time, float pulse1, float pulse2, float pulse3) {
        float x = pos.getX() + 0.5f;
        float y = pos.getY() + 0.02f;
        float z = pos.getZ() + 0.5f;
        
        // Multi-layered holographic effect
        
        // 1. Scanning lines effect
        float scanlineOffset = (time * 2) % 1.0f;
        for (int i = 0; i < 10; i++) {
            float scanY = y + i * 0.05f;
            float scanAlpha = 0.3f * (1.0f - Math.abs(i - scanlineOffset * 10));
            if (scanAlpha > 0) {
                bufferBuilder.addVertex(matrix, x - 0.5f, scanY, z - 0.5f).setColor(0.0f, 1.0f, 1.0f, scanAlpha * pulse1);
                bufferBuilder.addVertex(matrix, x + 0.5f, scanY, z - 0.5f).setColor(0.0f, 1.0f, 1.0f, scanAlpha * pulse1);
                bufferBuilder.addVertex(matrix, x + 0.5f, scanY, z + 0.5f).setColor(0.0f, 1.0f, 1.0f, scanAlpha * pulse1);
                bufferBuilder.addVertex(matrix, x - 0.5f, scanY, z + 0.5f).setColor(0.0f, 1.0f, 1.0f, scanAlpha * pulse1);
            }
        }
        
        // 2. Rotating holographic rings
        for (int ring = 0; ring < 5; ring++) {
            float ringY = y + ring * 0.1f;
            float ringPhase = time * 3.0f + ring * (float) Math.PI / 3.0f;
            float ringRadius = 0.3f + 0.1f * (float) Math.sin(ringPhase);
            
            for (int angle = 0; angle < 360; angle += 30) {
                float rad1 = (float) Math.toRadians(angle);
                float rad2 = (float) Math.toRadians(angle + 30);
                
                float x1 = x + (float) Math.cos(rad1 + ringPhase) * ringRadius;
                float z1 = z + (float) Math.sin(rad1 + ringPhase) * ringRadius;
                float x2 = x + (float) Math.cos(rad2 + ringPhase) * ringRadius;
                float z2 = z + (float) Math.sin(rad2 + ringPhase) * ringRadius;
                
                float alpha = 0.6f * (1.0f - ring * 0.15f) * pulse2;
                bufferBuilder.addVertex(matrix, x, ringY, z).setColor(1.0f, 0.8f, 0.0f, alpha);
                bufferBuilder.addVertex(matrix, x1, ringY, z1).setColor(1.0f, 1.0f, 0.0f, alpha * 0.7f);
                bufferBuilder.addVertex(matrix, x2, ringY, z2).setColor(1.0f, 1.0f, 0.0f, alpha * 0.7f);
                bufferBuilder.addVertex(matrix, x, ringY, z).setColor(1.0f, 0.8f, 0.0f, alpha);
            }
        }
        
        // 3. Quantum energy core
        float corePulse = (float) Math.sin(time * 5) * 0.3f + 0.7f;
        float coreSize = 0.2f * corePulse;
        
        // Central core with shifting colors
        float coreR = 1.0f;
        float coreG = 0.5f + 0.5f * (float) Math.sin(time * 3);
        float coreB = 0.5f + 0.5f * (float) Math.cos(time * 4);
        
        // Draw multi-layered core
        for (int layer = 0; layer < 3; layer++) {
            float layerSize = coreSize * (1.0f - layer * 0.3f);
            float layerAlpha = 0.9f * (1.0f - layer * 0.3f) * pulse3;
            
            bufferBuilder.addVertex(matrix, x - layerSize, y + 0.5f, z - layerSize).setColor(coreR, coreG, coreB, layerAlpha);
            bufferBuilder.addVertex(matrix, x + layerSize, y + 0.5f, z - layerSize).setColor(coreR, coreG, coreB, layerAlpha);
            bufferBuilder.addVertex(matrix, x + layerSize, y + 0.5f, z + layerSize).setColor(coreR, coreG, coreB, layerAlpha);
            bufferBuilder.addVertex(matrix, x - layerSize, y + 0.5f, z + layerSize).setColor(coreR, coreG, coreB, layerAlpha);
        }
        
        // 4. Floating data fragments
        for (int i = 0; i < 8; i++) {
            float fragmentPhase = time * 2.0f + i * (float) Math.PI / 4.0f;
            float fragmentX = x + (float) Math.cos(fragmentPhase) * 0.6f;
            float fragmentZ = z + (float) Math.sin(fragmentPhase) * 0.6f;
            float fragmentY = y + 0.3f + 0.2f * (float) Math.sin(fragmentPhase * 2);
            
            bufferBuilder.addVertex(matrix, fragmentX - 0.05f, fragmentY, fragmentZ - 0.05f).setColor(0.0f, 1.0f, 1.0f, 0.7f * pulse1);
            bufferBuilder.addVertex(matrix, fragmentX + 0.05f, fragmentY, fragmentZ - 0.05f).setColor(0.0f, 1.0f, 1.0f, 0.7f * pulse1);
            bufferBuilder.addVertex(matrix, fragmentX + 0.05f, fragmentY + 0.1f, fragmentZ - 0.05f).setColor(0.0f, 1.0f, 1.0f, 0.7f * pulse1);
            bufferBuilder.addVertex(matrix, fragmentX - 0.05f, fragmentY + 0.1f, fragmentZ - 0.05f).setColor(0.0f, 1.0f, 1.0f, 0.7f * pulse1);
        }
    }
    
    private static void renderQuantumStuckIndicator(BufferBuilder bufferBuilder, Matrix4f matrix,
                                                     BlockPos pos, float time, float pulse1, float pulse2) {
        float x = pos.getX() + 0.5f;
        float y = pos.getY() + 0.02f;
        float z = pos.getZ() + 0.5f;
        
        // 1. Probability cloud effect - multiple possible positions
        for (int cloud = 0; cloud < 5; cloud++) {
            float cloudPhase = time * 1.5f + cloud * (float) Math.PI / 2.5f;
            float cloudRadius = 0.3f + 0.2f * (float) Math.sin(cloudPhase * 2);
            float cloudX = x + (float) Math.cos(cloudPhase) * cloudRadius * 0.5f;
            float cloudZ = z + (float) Math.sin(cloudPhase) * cloudRadius * 0.5f;
            float cloudY = y + 0.1f + 0.1f * (float) Math.sin(cloudPhase * 3);
            
            float cloudAlpha = 0.2f * (1.0f - cloud * 0.15f) * pulse1;
            
            // Draw cloud as translucent sphere
            for (int slice = 0; slice < 8; slice++) {
                float sliceAngle = slice * (float) Math.PI / 8;
                float sliceY = cloudY + (float) Math.sin(sliceAngle) * 0.1f;
                float sliceRadius = (float) Math.cos(sliceAngle) * 0.15f;
                
                for (int seg = 0; seg < 8; seg++) {
                    float segAngle1 = seg * (float) Math.PI / 4;
                    float segAngle2 = (seg + 1) * (float) Math.PI / 4;
                    
                    float x1 = cloudX + (float) Math.cos(segAngle1) * sliceRadius;
                    float z1 = cloudZ + (float) Math.sin(segAngle1) * sliceRadius;
                    float x2 = cloudX + (float) Math.cos(segAngle2) * sliceRadius;
                    float z2 = cloudZ + (float) Math.sin(segAngle2) * sliceRadius;
                    
                    bufferBuilder.addVertex(matrix, cloudX, sliceY, cloudZ).setColor(1.0f, 0.2f, 0.2f, cloudAlpha);
                    bufferBuilder.addVertex(matrix, x1, sliceY, z1).setColor(1.0f, 0.3f, 0.3f, cloudAlpha * 0.7f);
                    bufferBuilder.addVertex(matrix, x2, sliceY, z2).setColor(1.0f, 0.3f, 0.3f, cloudAlpha * 0.7f);
                    bufferBuilder.addVertex(matrix, cloudX, sliceY, cloudZ).setColor(1.0f, 0.2f, 0.2f, cloudAlpha);
                }
            }
        }
        
        // 2. Quantum error indicators - floating warning symbols
        for (int i = 0; i < 6; i++) {
            float errorPhase = time * 2 + i * (float) Math.PI / 3;
            float errorRadius = 0.4f + 0.1f * (float) Math.sin(errorPhase * 3);
            float errorX = x + (float) Math.cos(errorPhase) * errorRadius;
            float errorZ = z + (float) Math.sin(errorPhase) * errorRadius;
            float errorY = y + 0.2f + 0.15f * (float) Math.sin(errorPhase * 2 + i);
            
            float errorSize = 0.08f + 0.04f * pulse2;
            float errorAlpha = 0.8f * pulse2;
            
            // Draw triangular warning sign
            bufferBuilder.addVertex(matrix, errorX, errorY, errorZ - errorSize).setColor(1.0f, 0.0f, 0.0f, errorAlpha);
            bufferBuilder.addVertex(matrix, errorX - errorSize, errorY, errorZ + errorSize).setColor(1.0f, 0.0f, 0.0f, errorAlpha);
            bufferBuilder.addVertex(matrix, errorX + errorSize, errorY, errorZ + errorSize).setColor(1.0f, 0.0f, 0.0f, errorAlpha);
            bufferBuilder.addVertex(matrix, errorX, errorY + 0.1f, errorZ).setColor(1.0f, 0.5f, 0.0f, errorAlpha);
        }
        
        // 3. Central quantum anomaly - pulsing red core
        float anomalyPulse = (float) Math.sin(time * 8) * 0.4f + 0.6f;
        float anomalySize = 0.15f * anomalyPulse;
        
        // Multi-layered anomaly core
        for (int layer = 0; layer < 4; layer++) {
            float layerSize = anomalySize * (1.0f - layer * 0.2f);
            float layerAlpha = 0.9f * (1.0f - layer * 0.2f) * pulse2;
            float layerY = y + layer * 0.05f;
            
            // Draw rotating cube for each layer
            bufferBuilder.addVertex(matrix, x - layerSize, layerY, z - layerSize).setColor(1.0f, 0.0f, 0.0f, layerAlpha);
            bufferBuilder.addVertex(matrix, x + layerSize, layerY, z - layerSize).setColor(1.0f, 0.0f, 0.0f, layerAlpha);
            bufferBuilder.addVertex(matrix, x + layerSize, layerY, z + layerSize).setColor(1.0f, 0.0f, 0.0f, layerAlpha);
            bufferBuilder.addVertex(matrix, x - layerSize, layerY, z + layerSize).setColor(1.0f, 0.0f, 0.0f, layerAlpha);
        }
        
        // 4. Critical alert ring
        float alertPhase = time * 4;
        float alertAlpha = 0.7f + 0.3f * (float) Math.sin(alertPhase * 2);
        
        for (int ring = 0; ring < 3; ring++) {
            float ringY = y + ring * 0.08f;
            float ringRadius = 0.25f + ring * 0.05f + 0.05f * (float) Math.sin(alertPhase + ring);
            
            for (int angle = 0; angle < 360; angle += 20) {
                float rad1 = (float) Math.toRadians(angle);
                float rad2 = (float) Math.toRadians(angle + 20);
                
                float x1 = x + (float) Math.cos(rad1) * ringRadius;
                float z1 = z + (float) Math.sin(rad1) * ringRadius;
                float x2 = x + (float) Math.cos(rad2) * ringRadius;
                float z2 = z + (float) Math.sin(rad2) * ringRadius;
                
                bufferBuilder.addVertex(matrix, x, ringY, z).setColor(1.0f, 0.2f, 0.0f, alertAlpha * pulse2);
                bufferBuilder.addVertex(matrix, x1, ringY, z1).setColor(1.0f, 0.0f, 0.0f, alertAlpha * 0.8f * pulse2);
                bufferBuilder.addVertex(matrix, x2, ringY, z2).setColor(1.0f, 0.0f, 0.0f, alertAlpha * 0.8f * pulse2);
                bufferBuilder.addVertex(matrix, x, ringY, z).setColor(1.0f, 0.2f, 0.0f, alertAlpha * pulse2);
            }
        }
    }
    
    private static void renderDirectionArrows(BufferBuilder bufferBuilder, Matrix4f matrix,
                                                   List<BlockPos> nodes, float pulse) {
        // Render arrows every 4 blocks
        for (int i = 0; i < nodes.size() - 1; i += 4) {
            BlockPos p1 = nodes.get(i);
            BlockPos p2 = nodes.get(Math.min(i + 1, nodes.size() - 1));
            
            float x1 = p1.getX() + 0.5f;
            float y1 = p1.getY() + 0.03f; // Slightly above path
            float z1 = p1.getZ() + 0.5f;
            float x2 = p2.getX() + 0.5f;
            float y2 = p2.getY() + 0.03f;
            float z2 = p2.getZ() + 0.5f;
            
            // Calculate direction
            float dx = x2 - x1;
            float dz = z2 - z1;
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            if (len < 0.01f) continue;
            
            dx /= len;
            dz /= len;
            
            // Arrow color with gradient
            float progress = (float) i / (nodes.size() - 1);
            float r = 0.2f + progress * 0.3f;
            float g = 0.8f;
            float b = 0.2f;
            
            // Draw chevron arrow
            float arrowSize = 0.15f;
            float arrowLength = 0.3f;
            
            // Arrow tip
            float tipX = x1 + dx * arrowLength;
            float tipZ = z1 + dz * arrowLength;
            
            // Left wing
            float leftX = tipX - dx * arrowLength - dz * arrowSize;
            float leftZ = tipZ - dz * arrowLength + dx * arrowSize;
            
            // Right wing
            float rightX = tipX - dx * arrowLength + dz * arrowSize;
            float rightZ = tipZ - dz * arrowLength - dx * arrowSize;
            
            // Draw arrow as triangle
            bufferBuilder.addVertex(matrix, x1, y1, z1).setColor(r, g, b, 0.9f * pulse);
            bufferBuilder.addVertex(matrix, leftX, y1, leftZ).setColor(r, g, b, 0.9f * pulse);
            bufferBuilder.addVertex(matrix, tipX, y1, tipZ).setColor(r, g, b, 0.9f * pulse);
            bufferBuilder.addVertex(matrix, rightX, y1, rightZ).setColor(r, g, b, 0.9f * pulse);
        }
    }
    
    private static void renderDestinationBlockEnhanced(BufferBuilder bufferBuilder, Matrix4f matrix,
                                                       BlockPos pos, float pulse) {
        float x = pos.getX() + 0.5f;
        float y = pos.getY() + 0.02f;
        float z = pos.getZ() + 0.5f;
        float size = 0.4f;
        
        // Pulsing glow effect
        float glowAlpha = 0.2f * pulse;
        float mainAlpha = 0.8f + 0.2f * pulse;
        
        // Render glow layers
        for (float glowSize = size + 0.1f; glowSize <= size + 0.3f; glowSize += 0.1f) {
            float alpha = glowAlpha * (1.0f - (glowSize - size) / 0.3f);
            bufferBuilder.addVertex(matrix, x - glowSize, y, z - glowSize).setColor(1.0f, 0.8f, 0.0f, alpha);
            bufferBuilder.addVertex(matrix, x + glowSize, y, z - glowSize).setColor(1.0f, 0.8f, 0.0f, alpha);
            bufferBuilder.addVertex(matrix, x + glowSize, y, z + glowSize).setColor(1.0f, 0.8f, 0.0f, alpha);
            bufferBuilder.addVertex(matrix, x - glowSize, y, z + glowSize).setColor(1.0f, 0.8f, 0.0f, alpha);
        }
        
        // Main platform with gradient from center
        for (float ring = size; ring >= 0; ring -= 0.1f) {
            float ringAlpha = mainAlpha * (1.0f - (size - ring) / size * 0.5f);
            bufferBuilder.addVertex(matrix, x - ring, y, z - ring).setColor(1.0f, 1.0f, 0.0f, ringAlpha);
            bufferBuilder.addVertex(matrix, x + ring, y, z - ring).setColor(1.0f, 1.0f, 0.0f, ringAlpha);
            bufferBuilder.addVertex(matrix, x + ring, y, z + ring).setColor(1.0f, 1.0f, 0.0f, ringAlpha);
            bufferBuilder.addVertex(matrix, x - ring, y, z + ring).setColor(1.0f, 1.0f, 0.0f, ringAlpha);
        }
        
        // Animated beacon effect
        float beaconHeight = 0.2f + 0.1f * pulse;
        for (float h = 0.05f; h < beaconHeight; h += 0.05f) {
            float beaconAlpha = (1.0f - h / beaconHeight) * 0.3f * pulse;
            float beaconSize = size * 0.3f * (1.0f - h / beaconHeight);
            
            bufferBuilder.addVertex(matrix, x - beaconSize, y + h, z - beaconSize).setColor(1.0f, 0.9f, 0.2f, beaconAlpha);
            bufferBuilder.addVertex(matrix, x + beaconSize, y + h, z - beaconSize).setColor(1.0f, 0.9f, 0.2f, beaconAlpha);
            bufferBuilder.addVertex(matrix, x + beaconSize, y + h, z + beaconSize).setColor(1.0f, 0.9f, 0.2f, beaconAlpha);
            bufferBuilder.addVertex(matrix, x - beaconSize, y + h, z + beaconSize).setColor(1.0f, 0.9f, 0.2f, beaconAlpha);
        }
    }
    
    private static void renderStuckIndicatorEnhanced(BufferBuilder bufferBuilder, Matrix4f matrix,
                                                     BlockPos pos, float pulse) {
        float x = pos.getX() + 0.5f;
        float y = pos.getY() + 0.02f;
        float z = pos.getZ() + 0.5f;
        float size = 0.3f;
        
        // Pulsing red glow
        float glowSize = size + 0.1f * pulse;
        float glowAlpha = 0.3f * pulse;
        
        // Draw pulsing red circle around X
        for (float angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
            float x1 = x + (float) Math.cos(angle) * glowSize;
            float z1 = z + (float) Math.sin(angle) * glowSize;
            float x2 = x + (float) Math.cos(angle + Math.PI / 8) * glowSize;
            float z2 = z + (float) Math.sin(angle + Math.PI / 8) * glowSize;
            
            bufferBuilder.addVertex(matrix, x, y, z).setColor(1.0f, 0.0f, 0.0f, glowAlpha);
            bufferBuilder.addVertex(matrix, x1, y, z1).setColor(1.0f, 0.0f, 0.0f, glowAlpha);
            bufferBuilder.addVertex(matrix, x2, y, z2).setColor(1.0f, 0.0f, 0.0f, glowAlpha);
            bufferBuilder.addVertex(matrix, x, y, z).setColor(1.0f, 0.0f, 0.0f, glowAlpha);
        }
        
        // Draw X shape with pulsing intensity
        float xAlpha = 0.9f + 0.1f * pulse;
        bufferBuilder.addVertex(matrix, x - size, y, z - size).setColor(1.0f, 0.0f, 0.0f, xAlpha);
        bufferBuilder.addVertex(matrix, x - size + 0.1f, y, z - size + 0.1f).setColor(1.0f, 0.0f, 0.0f, xAlpha);
        bufferBuilder.addVertex(matrix, x + size - 0.1f, y, z + size - 0.1f).setColor(1.0f, 0.0f, 0.0f, xAlpha);
        bufferBuilder.addVertex(matrix, x + size, y, z + size).setColor(1.0f, 0.0f, 0.0f, xAlpha);
        
        bufferBuilder.addVertex(matrix, x + size, y, z - size).setColor(1.0f, 0.0f, 0.0f, xAlpha);
        bufferBuilder.addVertex(matrix, x + size - 0.1f, y, z - size + 0.1f).setColor(1.0f, 0.0f, 0.0f, xAlpha);
        bufferBuilder.addVertex(matrix, x - size + 0.1f, y, z + size - 0.1f).setColor(1.0f, 0.0f, 0.0f, xAlpha);
        bufferBuilder.addVertex(matrix, x - size, y, z + size).setColor(1.0f, 0.0f, 0.0f, xAlpha);
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