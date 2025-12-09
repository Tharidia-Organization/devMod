package com.frenkvs.devmod.event.common;

import com.frenkvs.devmod.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.*;

@EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
public class VerticalLevelEvents {

    // Cache per il Leak Detector (per non ricalcolare ogni frame e laggare)
    private static final Set<BlockPos> safeAirBlocks = new HashSet<>();
    private static final Set<BlockPos> leakBlocks = new HashSet<>();
    private static int scanTimer = 0;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        boolean showGrid = ModConfig.showVerticalLevels;
        boolean showMeasure = ModConfig.enableMeasureTool && (ModConfig.measurePos1 != null);
        boolean showShape = ModConfig.showShapeGuide;
        boolean showLeaks = ModConfig.showLeakDetector;

        if (!showGrid && !showMeasure && !showShape && !showLeaks) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // AGGIORNAMENTO LOGICA LEAK DETECTOR (Ogni 10 tick = 0.5s)
        if (showLeaks) {
            scanTimer++;
            if (scanTimer > 10) {
                updateLeakScan(mc);
                scanTimer = 0;
            }
        } else {
            // Pulisce la memoria se spento
            if (!safeAirBlocks.isEmpty()) safeAirBlocks.clear();
            if (!leakBlocks.isEmpty()) leakBlocks.clear();
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        Tesselator tesselator = Tesselator.getInstance();

        // 1. FASE LINEE (Grid, Sfera Wire)
        if (showGrid || (showShape && ModConfig.currentShape == ModConfig.ShapeType.SPHERE)) {
            setupRenderState(true);
            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

            if (showGrid) renderVerticalGrid(buffer, matrix, mc);
            if (showShape && ModConfig.currentShape == ModConfig.ShapeType.SPHERE) renderShapeGuide(buffer, matrix, true);

            try { BufferUploader.drawWithShader(buffer.buildOrThrow()); } catch (Exception ignored) {}
        }

        // 2. FASE X-RAY (Metro)
        if (showMeasure) {
            setupRenderState(false);
            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            renderTapeMeasure(buffer, matrix, mc);
            try { BufferUploader.drawWithShader(buffer.buildOrThrow()); } catch (Exception ignored) {}
        }

        // 3. FASE VOXEL/RIEMPIMENTO (Shape Filled, Leak Detector)
        if ((showShape && ModConfig.currentShape != ModConfig.ShapeType.SPHERE) || showLeaks) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            if (showShape && ModConfig.currentShape != ModConfig.ShapeType.SPHERE) {
                renderShapeGuide(buffer, matrix, false);
            }

            // Renderizza i blocchi calcolati
            if (showLeaks) {
                renderCachedLeaks(buffer, matrix);
            }

            try { BufferUploader.drawWithShader(buffer.buildOrThrow()); } catch (Exception ignored) {}
            RenderSystem.depthMask(true);
        }

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);

        if (showMeasure) renderMetricsText(event.getPoseStack(), cameraPos, mc);
    }

    // =============================================================
    // 💧 LEAK DETECTOR (ALGORITMO FLOOD FILL)
    // =============================================================
    private static void updateLeakScan(Minecraft mc) {
        safeAirBlocks.clear();
        leakBlocks.clear();

        BlockPos startPos = mc.player.blockPosition();
        int maxRadius = ModConfig.leakRadius;
        int maxBlocks = 2000; // Limite sicurezza per non crashare

        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(startPos);
        safeAirBlocks.add(startPos);

        while (!queue.isEmpty()) {
            if (safeAirBlocks.size() + leakBlocks.size() > maxBlocks) break;

            BlockPos current = queue.poll();

            // Controlla i 6 vicini
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);

                // Se abbiamo già visitato, salta
                if (safeAirBlocks.contains(neighbor) || leakBlocks.contains(neighbor)) continue;

                // Calcola distanza
                double dist = Math.sqrt(neighbor.distSqr(startPos));

                // Se è un blocco solido, è un muro, ci fermiamo
                if (mc.level.getBlockState(neighbor).isSolidRender(mc.level, neighbor)) continue;

                // Se è ARIA (o non solido):
                if (dist > maxRadius) {
                    // È uscito dal raggio -> è una PERDITA (LEAK)
                    leakBlocks.add(neighbor);
                } else {
                    // È ancora dentro -> è ARIA SICURA
                    safeAirBlocks.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
    }

    private static void renderCachedLeaks(BufferBuilder builder, Matrix4f matrix) {
        // Disegna aria sicura (Ciano chiarissimo)
        for (BlockPos pos : safeAirBlocks) {
            drawFilledBox(builder, matrix, pos, 0.0f, 1.0f, 1.0f, 0.05f); // Quasi invisibile
        }
        // Disegna perdite (Rosso acceso)
        for (BlockPos pos : leakBlocks) {
            drawFilledBox(builder, matrix, pos, 1.0f, 0.0f, 0.0f, 0.5f); // Ben visibile
        }
    }

    // =============================================================
    // ALTRE LOGICHE (Invariate ma incluse per completezza)
    // =============================================================

    private static void renderShapeGuide(BufferBuilder builder, Matrix4f matrix, boolean drawLines) {
        int cx = ModConfig.shapeCenterX; int cy = ModConfig.shapeCenterY; int cz = ModConfig.shapeCenterZ;
        int rA = ModConfig.shapeRadius; int rB = (ModConfig.currentShape == ModConfig.ShapeType.ELLIPSE) ? ModConfig.shapeRadiusB : rA;

        if (!drawLines) drawFilledBlock(builder, matrix, cx, cy, cz, 1.0f, 1.0f, 0.0f, 0.8f);

        if (ModConfig.currentShape == ModConfig.ShapeType.SPHERE && drawLines) {
            for (int x = -rA; x <= rA; x++) for (int y = -rA; y <= rA; y++) for (int z = -rA; z <= rA; z++) {
                if (Math.sqrt(x*x + y*y + z*z) >= rA - 0.5 && Math.sqrt(x*x + y*y + z*z) < rA + 0.5)
                    drawBox(builder, matrix, new BlockPos(cx + x, cy + y, cz + z), 0.0f, 1.0f, 1.0f, 0.5f);
            }
        } else if (!drawLines) {
            for (int x = -rA; x <= rA; x++) for (int z = -rB; z <= rB; z++) {
                double n = (double)(x*x)/(rA*rA) + (double)(z*z)/(rB*rB);
                if (n >= 0.75 && n <= 1.25) drawFilledBlock(builder, matrix, cx + x, cy, cz + z, 0.0f, 1.0f, 1.0f, 0.6f);
            }
        }
    }

    private static void renderVerticalGrid(BufferBuilder builder, Matrix4f matrix, Minecraft mc) {
        double bx = ModConfig.gridLockPos ? ModConfig.lockedX : Math.floor(mc.player.getX());
        double by = ModConfig.gridLockPos ? ModConfig.lockedY : Math.floor(mc.player.getY());
        double bz = ModConfig.gridLockPos ? ModConfig.lockedZ : Math.floor(mc.player.getZ());
        int r = ModConfig.gridRadius;
        drawGridPlane(builder, matrix, bx, by, bz, r, 0.0f, 1.0f, 0.0f, 0.8f);
        for (int i = 1; i <= ModConfig.gridFloorsUp; i++) drawGridPlane(builder, matrix, bx, by + (i * ModConfig.gridSpacingY), bz, r, 1.0f, 0.5f, 0.0f, 0.6f);
        for (int i = 1; i <= ModConfig.gridFloorsDown; i++) drawGridPlane(builder, matrix, bx, by - (i * ModConfig.gridSpacingY), bz, r, 0.0f, 0.5f, 1.0f, 0.6f);
    }

    private static void renderTapeMeasure(BufferBuilder buffer, Matrix4f matrix, Minecraft mc) {
        BlockPos p1 = ModConfig.measurePos1; BlockPos p2 = ModConfig.measurePos2;
        if (p1 == null) return;
        drawBox(buffer, matrix, p1, 0.2f, 1.0f, 0.2f, 1.0f);
        BlockPos t = (p2 != null) ? p2 : getPlayerLookingAtBlock(mc);
        if (t != null) {
            drawBox(buffer, matrix, t, 1.0f, 1.0f, 0.0f, 0.5f);
            if (p2 != null) drawBox(buffer, matrix, p2, 1.0f, 0.2f, 0.2f, 1.0f);
            drawLine(buffer, matrix, p1.getX()+0.5f, p1.getY()+0.5f, p1.getZ()+0.5f, t.getX()+0.5f, t.getY()+0.5f, t.getZ()+0.5f, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private static BlockPos getPlayerLookingAtBlock(Minecraft mc) {
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos();
        return null;
    }

    // --- HELPER DISEGNO ---
    private static void setupRenderState(boolean depthTest) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        if (depthTest) { RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); } else { RenderSystem.disableDepthTest(); }
        RenderSystem.disableCull(); RenderSystem.setShader(GameRenderer::getPositionColorShader); RenderSystem.lineWidth(3.0f);
    }
    private static void drawGridPlane(BufferBuilder b, Matrix4f m, double x, double y, double z, int r, float red, float green, float blue, float alpha) {
        for (int i = -r; i <= r; i++) {
            b.addVertex(m, (float)(x-r), (float)y, (float)(z+i)).setColor(red,green,blue,alpha); b.addVertex(m, (float)(x+r), (float)y, (float)(z+i)).setColor(red,green,blue,alpha);
            b.addVertex(m, (float)(x+i), (float)y, (float)(z-r)).setColor(red,green,blue,alpha); b.addVertex(m, (float)(x+i), (float)y, (float)(z+r)).setColor(red,green,blue,alpha);
        }
    }
    private static void drawBox(BufferBuilder b, Matrix4f m, BlockPos pos, float r, float g, float bl, float a) {
        float x = pos.getX(); float y = pos.getY(); float z = pos.getZ();
        drawRect(b, m, x, y, z, x+1, y, z+1, r, g, bl, a); drawRect(b, m, x, y+1, z, x+1, y+1, z+1, r, g, bl, a);
        drawLine(b, m, x, y, z, x, y+1, z, r, g, bl, a); drawLine(b, m, x+1, y, z, x+1, y+1, z, r, g, bl, a);
        drawLine(b, m, x, y, z+1, x, y+1, z+1, r, g, bl, a); drawLine(b, m, x+1, y, z+1, x+1, y+1, z+1, r, g, bl, a);
    }
    private static void drawFilledBox(BufferBuilder b, Matrix4f m, BlockPos pos, float r, float g, float bl, float a) {
        float x = pos.getX(); float y = pos.getY(); float z = pos.getZ(); float x2=x+1; float y2=y+1; float z2=z+1;
        b.addVertex(m,x,y2,z).setColor(r,g,bl,a); b.addVertex(m,x,y2,z2).setColor(r,g,bl,a); b.addVertex(m,x2,y2,z2).setColor(r,g,bl,a); b.addVertex(m,x2,y2,z).setColor(r,g,bl,a);
        b.addVertex(m,x,y,z).setColor(r,g,bl,a); b.addVertex(m,x2,y,z).setColor(r,g,bl,a); b.addVertex(m,x2,y,z2).setColor(r,g,bl,a); b.addVertex(m,x,y,z2).setColor(r,g,bl,a);
        b.addVertex(m,x,y,z).setColor(r,g,bl,a); b.addVertex(m,x,y2,z).setColor(r,g,bl,a); b.addVertex(m,x2,y2,z).setColor(r,g,bl,a); b.addVertex(m,x2,y,z).setColor(r,g,bl,a);
        b.addVertex(m,x,y,z2).setColor(r,g,bl,a); b.addVertex(m,x2,y,z2).setColor(r,g,bl,a); b.addVertex(m,x2,y2,z2).setColor(r,g,bl,a); b.addVertex(m,x,y2,z2).setColor(r,g,bl,a);
        b.addVertex(m,x,y,z).setColor(r,g,bl,a); b.addVertex(m,x,y,z2).setColor(r,g,bl,a); b.addVertex(m,x,y2,z2).setColor(r,g,bl,a); b.addVertex(m,x,y2,z).setColor(r,g,bl,a);
        b.addVertex(m,x2,y,z).setColor(r,g,bl,a); b.addVertex(m,x2,y2,z).setColor(r,g,bl,a); b.addVertex(m,x2,y2,z2).setColor(r,g,bl,a); b.addVertex(m,x2,y,z2).setColor(r,g,bl,a);
    }
    private static void drawFilledBlock(BufferBuilder b, Matrix4f m, int x, int y, int z, float r, float g, float bl, float a) {
        float yH = y + 0.05f;
        b.addVertex(m, x, yH, z).setColor(r, g, bl, a); b.addVertex(m, x, yH, z+1).setColor(r, g, bl, a);
        b.addVertex(m, x+1, yH, z+1).setColor(r, g, bl, a); b.addVertex(m, x+1, yH, z).setColor(r, g, bl, a);
    }
    private static void drawRect(BufferBuilder b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl, float a) {
        drawLine(b, m, x1, y1, z1, x2, y1, z1, r, g, bl, a); drawLine(b, m, x2, y1, z1, x2, y2, z2, r, g, bl, a);
        drawLine(b, m, x2, y2, z2, x1, y2, z2, r, g, bl, a); drawLine(b, m, x1, y2, z2, x1, y1, z1, r, g, bl, a);
    }
    private static void drawLine(BufferBuilder b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl, float a) {
        b.addVertex(m, x1, y1, z1).setColor(r, g, bl, a); b.addVertex(m, x2, y2, z2).setColor(r, g, bl, a);
    }
    private static void renderMetricsText(PoseStack poseStack, Vec3 camPos, Minecraft mc) {
        BlockPos p1 = ModConfig.measurePos1; BlockPos p2 = ModConfig.measurePos2;
        BlockPos t = (p2 != null) ? p2 : getPlayerLookingAtBlock(mc);
        if (t == null) return;
        double dist = Math.sqrt(p1.distSqr(t));
        int dx = Math.abs(p1.getX() - t.getX()) + 1; int dy = Math.abs(p1.getY() - t.getY()) + 1; int dz = Math.abs(p1.getZ() - t.getZ()) + 1;
        double midX = (p1.getX() + t.getX()) / 2.0 + 0.5; double midY = (p1.getY() + t.getY()) / 2.0 + 0.5; double midZ = (p1.getZ() + t.getZ()) / 2.0 + 0.5;
        poseStack.pushPose(); poseStack.translate(midX - camPos.x, midY - camPos.y + 0.5, midZ - camPos.z);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation()); poseStack.scale(-0.025f, -0.025f, 0.025f);
        Font font = mc.font;
        RenderSystem.disableDepthTest(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        String l1 = String.format("Dist: %.1fm", dist); String l2 = String.format("Box: %dx%dx%d", dx, dy, dz);
        font.drawInBatch(l1, -font.width(l1)/2.0f, -10, -1, false, poseStack.last().pose(), mc.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0x80000000, 15728880);
        font.drawInBatch(l2, -font.width(l2)/2.0f, 0, 0xFFFFFF00, false, poseStack.last().pose(), mc.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0x80000000, 15728880);
        RenderSystem.enableDepthTest(); poseStack.popPose();
    }
}