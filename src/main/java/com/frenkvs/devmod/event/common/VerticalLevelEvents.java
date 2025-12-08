package com.frenkvs.devmod;

import com.frenkvs.devmod.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
public class VerticalLevelEvents {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Usiamo AFTER_TRANSLUCENT per disegnare dopo che il mondo è stato renderizzato
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        // Controllo rapido per non sprecare risorse se tutto è spento
        boolean showGrid = ModConfig.showVerticalLevels;
        boolean showMeasure = ModConfig.measurePos1 != null;
        boolean showCircle = ModConfig.showCircleGuide;

        if (!showGrid && !showMeasure && !showCircle) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        // --- SETUP COMUNE ---
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        // =============================================================
        // 1. RENDER GRIGLIA (Opaca e bloccata dai muri)
        // =============================================================
        if (showGrid) {
            // IMPOSTAZIONI PER NON VEDERE ATTRAVERSO I BLOCCHI
            RenderSystem.enableDepthTest();  // Attiva il controllo profondità (i muri nascondono le linee)
            RenderSystem.depthMask(true);    // Scrive nella profondità
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.lineWidth(3.0f);    // Linee più spesse (era 1.0 o 2.0)

            // Disegniamo la griglia con alpha più alto (colori più forti)
            renderVerticalGrid(buffer, matrix, mc);

            // Forziamo il disegno SUBITO per la griglia, così possiamo cambiare impostazioni per il metro
            try { BufferUploader.drawWithShader(buffer.buildOrThrow()); } catch (Exception ignored) {}

            // Riapriamo il buffer per le prossime cose
            buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        }

        // =============================================================
        // 2. RENDER METRO / MISURATORE (Visibile ovunque)
        // =============================================================
        if (showMeasure) {
            // IMPOSTAZIONI PER VEDERE ATTRAVERSO TUTTO (X-RAY)
            RenderSystem.disableDepthTest(); // Il metro si vede sempre
            RenderSystem.lineWidth(2.0f);

            renderMeasurement(buffer, matrix, mc);
        }

        // =============================================================
        // 3. RENDER GUIDA CERCHIO
        // =============================================================
        if (showCircle) {
            RenderSystem.enableDepthTest(); // Anche il cerchio lo blocchiamo coi muri (come la griglia)
            RenderSystem.lineWidth(2.0f);
            renderVoxelCircle(buffer, matrix);
        }

        // Disegna tutto quello che è rimasto nel buffer
        try {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } catch (Exception ignored) {}

        poseStack.popPose();

        // =============================================================
        // 4. TESTO FLUTTUANTE (Distanza Metro)
        // =============================================================
        if (ModConfig.measurePos1 != null && ModConfig.measurePos2 != null) {
            renderDistanceText(event.getPoseStack(), cameraPos);
        }

        // Ripristino stato originale Minecraft
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    // --- LOGICA GRIGLIA (COLORI PIÙ FORTI) ---
    private static void renderVerticalGrid(BufferBuilder builder, Matrix4f matrix, Minecraft mc) {
        double baseX = Math.floor(mc.player.getX());
        double baseZ = Math.floor(mc.player.getZ());
        double centerY = ModConfig.gridLockY ? ModConfig.lockedYValue : Math.floor(mc.player.getY());
        int radius = ModConfig.gridRadius;

        // Alpha aumentato a 0.8f o 1.0f per renderlo "Solido"
        drawGridPlane(builder, matrix, baseX, centerY, baseZ, radius, 0.0f, 1.0f, 0.0f, 1.0f); // Verde pieno

        for (int i = 1; i <= ModConfig.gridFloorsUp; i++) {
            double y = centerY + (i * ModConfig.gridSpacingY);
            // Giallo/Rosso molto visibile
            drawGridPlane(builder, matrix, baseX, y, baseZ, radius, 1.0f, Math.max(0, 1.0f - i * 0.2f), 0.0f, 0.8f);
        }
        for (int i = 1; i <= ModConfig.gridFloorsDown; i++) {
            double y = centerY - (i * ModConfig.gridSpacingY);
            // Blu visibile
            drawGridPlane(builder, matrix, baseX, y, baseZ, radius, 0.0f, 0.5f, 1.0f, 0.8f);
        }
    }

    private static void drawGridPlane(BufferBuilder builder, Matrix4f matrix, double x, double y, double z, int r, float red, float green, float blue, float alpha) {
        for (int i = -r; i <= r; i++) {
            // Asse X
            builder.addVertex(matrix, (float)(x - r), (float)y, (float)(z + i)).setColor(red, green, blue, alpha);
            builder.addVertex(matrix, (float)(x + r), (float)y, (float)(z + i)).setColor(red, green, blue, alpha);
            // Asse Z
            builder.addVertex(matrix, (float)(x + i), (float)y, (float)(z - r)).setColor(red, green, blue, alpha);
            builder.addVertex(matrix, (float)(x + i), (float)y, (float)(z + r)).setColor(red, green, blue, alpha);
        }
    }

    // --- LOGICA METRO ---
    private static void renderMeasurement(BufferBuilder builder, Matrix4f matrix, Minecraft mc) {
        BlockPos p1 = ModConfig.measurePos1;
        BlockPos p2 = ModConfig.measurePos2;

        if (p1 == null) return; // Sicurezza

        // Disegna Box Punto A (Verde Lime)
        drawBox(builder, matrix, p1, 0.2f, 1.0f, 0.2f, 1.0f);

        if (p2 != null) {
            // Disegna Box Punto B (Rosso)
            drawBox(builder, matrix, p2, 1.0f, 0.2f, 0.2f, 1.0f);

            // Linea di collegamento (Bianca Spessa)
            builder.addVertex(matrix, p1.getX() + 0.5f, p1.getY() + 0.5f, p1.getZ() + 0.5f)
                    .setColor(1.0f, 1.0f, 1.0f, 1.0f);
            builder.addVertex(matrix, p2.getX() + 0.5f, p2.getY() + 0.5f, p2.getZ() + 0.5f)
                    .setColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            // Linea elastica verso il cursore (Anteprima Gialla)
            Vec3 playerEye = mc.player.getEyePosition(1.0f);
            Vec3 lookVec = mc.player.getViewVector(1.0f).scale(10.0); // Raggio di 10 blocchi
            Vec3 target = playerEye.add(lookVec);

            // Se stiamo guardando un blocco, attacchiamo la linea lì
            if (mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                BlockPos lookPos = ((net.minecraft.world.phys.BlockHitResult) mc.hitResult).getBlockPos();
                target = new Vec3(lookPos.getX() + 0.5, lookPos.getY() + 0.5, lookPos.getZ() + 0.5);
                // Box anteprima semitrasparente
                drawBox(builder, matrix, lookPos, 1.0f, 1.0f, 0.0f, 0.4f);
            }

            builder.addVertex(matrix, p1.getX() + 0.5f, p1.getY() + 0.5f, p1.getZ() + 0.5f)
                    .setColor(1.0f, 1.0f, 0.0f, 1.0f);
            builder.addVertex(matrix, (float)target.x, (float)target.y, (float)target.z)
                    .setColor(1.0f, 1.0f, 0.0f, 1.0f);
        }
    }

    // --- LOGICA CERCHIO VOXEL ---
    private static void renderVoxelCircle(BufferBuilder builder, Matrix4f matrix) {
        int cx = ModConfig.circleCenterX;
        int cy = ModConfig.circleCenterY;
        int cz = ModConfig.circleCenterZ;
        int r = ModConfig.circleRadius;

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double dist = Math.sqrt(x*x + z*z);
                if (dist >= r - 0.5 && dist < r + 0.5) {
                    drawBlockHighlight(builder, matrix, cx + x, cy, cz + z, 0.0f, 1.0f, 1.0f, 0.8f);
                }
            }
        }
        drawBlockHighlight(builder, matrix, cx, cy, cz, 1.0f, 1.0f, 0.0f, 0.8f);
    }

    // --- UTILITIES DISEGNO ---
    private static void drawBox(BufferBuilder builder, Matrix4f matrix, BlockPos pos, float r, float g, float b, float a) {
        // Disegna un cubo completo (wireframe) attorno al blocco
        float x = pos.getX(); float y = pos.getY(); float z = pos.getZ();

        // Base e Top
        drawRect(builder, matrix, x, y, z, x+1, y, z+1, r, g, b, a);       // Base
        drawRect(builder, matrix, x, y+1, z, x+1, y+1, z+1, r, g, b, a);   // Top

        // Colonne verticali
        drawLine(builder, matrix, x, y, z, x, y+1, z, r, g, b, a);
        drawLine(builder, matrix, x+1, y, z, x+1, y+1, z, r, g, b, a);
        drawLine(builder, matrix, x, y, z+1, x, y+1, z+1, r, g, b, a);
        drawLine(builder, matrix, x+1, y, z+1, x+1, y+1, z+1, r, g, b, a);
    }

    private static void drawBlockHighlight(BufferBuilder builder, Matrix4f matrix, int x, int y, int z, float r, float g, float b, float a) {
        // Disegna solo il contorno superiore del blocco (tappeto)
        drawRect(builder, matrix, x, y+0.02f, z, x+1, y+0.02f, z+1, r, g, b, a);
        // Croce centrale
        drawLine(builder, matrix, x, y+0.02f, z, x+1, y+0.02f, z+1, r, g, b, a);
        drawLine(builder, matrix, x+1, y+0.02f, z, x, y+0.02f, z+1, r, g, b, a);
    }

    private static void drawRect(BufferBuilder b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl, float a) {
        b.addVertex(m, x1, y1, z1).setColor(r, g, bl, a); b.addVertex(m, x2, y1, z1).setColor(r, g, bl, a);
        b.addVertex(m, x2, y1, z1).setColor(r, g, bl, a); b.addVertex(m, x2, y2, z2).setColor(r, g, bl, a);
        b.addVertex(m, x2, y2, z2).setColor(r, g, bl, a); b.addVertex(m, x1, y2, z2).setColor(r, g, bl, a);
        b.addVertex(m, x1, y2, z2).setColor(r, g, bl, a); b.addVertex(m, x1, y1, z1).setColor(r, g, bl, a);
    }

    private static void drawLine(BufferBuilder b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float bl, float a) {
        b.addVertex(m, x1, y1, z1).setColor(r, g, bl, a);
        b.addVertex(m, x2, y2, z2).setColor(r, g, bl, a);
    }

    // --- TESTO FLUTTUANTE ---
    private static void renderDistanceText(PoseStack poseStack, Vec3 camPos) {
        BlockPos p1 = ModConfig.measurePos1;
        BlockPos p2 = ModConfig.measurePos2;

        // Calcolo centro linea
        double midX = (p1.getX() + p2.getX()) / 2.0 + 0.5;
        double midY = (p1.getY() + p2.getY()) / 2.0 + 0.5;
        double midZ = (p1.getZ() + p2.getZ()) / 2.0 + 0.5;

        double dist = Math.sqrt(p1.distSqr(p2));

        // Calcolo delta X Y Z
        int dx = Math.abs(p1.getX() - p2.getX()) + 1;
        int dy = Math.abs(p1.getY() - p2.getY()) + 1;
        int dz = Math.abs(p1.getZ() - p2.getZ()) + 1;

        String textLine1 = String.format("Dist: %.1f m", dist);
        String textLine2 = String.format("Box: %d x %d x %d", dx, dy, dz);

        poseStack.pushPose();
        poseStack.translate(midX - camPos.x, midY - camPos.y + 0.8, midZ - camPos.z); // Più in alto
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025f, -0.025f, 0.025f);

        Font font = Minecraft.getInstance().font;
        float w1 = font.width(textLine1) / 2.0f;
        float w2 = font.width(textLine2) / 2.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest(); // Il testo si legge sempre

        // Sfondo nero semitrasparente per leggere meglio
        int bg = 0x80000000;

        font.drawInBatch(textLine1, -w1, -5, 0xFFFFFFFF, false, poseStack.last().pose(), Minecraft.getInstance().renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, bg, 15728880);
        font.drawInBatch(textLine2, -w2, 5, 0xFFFFFF00, false, poseStack.last().pose(), Minecraft.getInstance().renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, bg, 15728880);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        poseStack.popPose();
    }
}