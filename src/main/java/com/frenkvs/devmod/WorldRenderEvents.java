package com.frenkvs.devmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
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

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Mob mob) {
                if (mob.distanceToSqr(mc.player) > 1600) continue;

                // 1. RAGGIO DI VISTA (Follow Range)
                double followRange = 0;
                if (mob.getAttribute(Attributes.FOLLOW_RANGE) != null) {
                    followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
                }

                if (followRange > 0 && followRange <= 64) {
                    if (ModConfig.renderAsBlocks) {
                        // Modalità BLOCCHI
                        renderAggroBlocks(event.getPoseStack(), mob, followRange, cameraPos, mc.level);
                    } else {
                        // Modalità CERCHIO SEMPLICE
                        renderCircle(event.getPoseStack(), mob, followRange, cameraPos, ModConfig.followRangeColor);
                    }
                }

                // ... (parte rossa uguale)

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
                    // Usiamo il giallo fisso o un altro colore se vuoi
                    renderCircle(event.getPoseStack(), mob, attackReach, cameraPos, 0xFFFFFF00);
                }
            }
        }
    }

    // Disegna la griglia di blocchi (usa il colore configurato)
    private static void renderAggroBlocks(PoseStack poseStack, Mob mob, double range, Vec3 cameraPos, Level level) {
        VertexConsumer builder = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lines());
        BlockPos mobPos = mob.blockPosition();
        int r = (int) Math.ceil(range);
        double rangeSqr = range * range;

        // Estraiamo i componenti ARGB dal colore configurato
        int color = ModConfig.followRangeColor;
        float alpha = ((color >> 24) & 0xFF) / 255f;
        if (alpha == 0) alpha = 1.0f; // Fix se non c'è alpha
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x*x + z*z > rangeSqr) continue;
                for (int y = -1; y <= 1; y++) {
                    BlockPos targetPos = mobPos.offset(x, y, z);
                    if (!level.getBlockState(targetPos).isAir()) {
                        drawBox(builder, matrix, targetPos, red, green, blue, 0.5f);
                    }
                }
            }
        }
        poseStack.popPose();
    }

    // Metodo generico per disegnare cerchi (usato sia per vista che attacco)
    private static void renderCircle(PoseStack poseStack, Mob mob, double radius, Vec3 cameraPos, int color) {
        VertexConsumer builder = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lines());

        float alpha = ((color >> 24) & 0xFF) / 255f;
        if (alpha == 0) alpha = 1.0f;
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;

        poseStack.pushPose();
        double x = mob.getX() - cameraPos.x;
        double y = mob.getY() - cameraPos.y + 0.1;
        double z = mob.getZ() - cameraPos.z;
        poseStack.translate(x, y, z);

        Matrix4f matrix = poseStack.last().pose();
        int segments = 48;

        for (int i = 0; i < segments; i++) {
            double angle1 = (i * 2 * Math.PI) / segments;
            double angle2 = ((i + 1) * 2 * Math.PI) / segments;

            float x1 = (float) (Math.cos(angle1) * radius);
            float z1 = (float) (Math.sin(angle1) * radius);
            float x2 = (float) (Math.cos(angle2) * radius);
            float z2 = (float) (Math.sin(angle2) * radius);

            builder.addVertex(matrix, x1, 0, z1).setColor(red, green, blue, alpha).setNormal(0, 1, 0);
            builder.addVertex(matrix, x2, 0, z2).setColor(red, green, blue, alpha).setNormal(0, 1, 0);
        }
        poseStack.popPose();
    }

    private static void drawBox(VertexConsumer builder, Matrix4f matrix, BlockPos pos, float r, float g, float b, float a) {
        float x = pos.getX(); float y = pos.getY(); float z = pos.getZ();
        // Disegno semplificato del cubo
        builder.addVertex(matrix, x, y+1, z).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x+1, y+1, z).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x+1, y+1, z).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x+1, y+1, z+1).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x+1, y+1, z+1).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x, y+1, z+1).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x, y+1, z+1).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x, y+1, z).setColor(r, g, b, a).setNormal(0, 1, 0);
        // Base
        builder.addVertex(matrix, x, y, z).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x+1, y, z).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x+1, y, z).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x+1, y, z+1).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x+1, y, z+1).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x, y, z+1).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x, y, z+1).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, x, y, z).setColor(r, g, b, a).setNormal(0, 1, 0);
    }
}