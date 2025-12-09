package com.frenkvs.devmod.event.common;

import com.frenkvs.devmod.config.ModConfig;
import com.frenkvs.devmod.permission.PermissionManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = "devmod", value = Dist.CLIENT)
public class LoSRenderer { // <--- APERTURA CLASSE

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Check if player has OP level 4 or higher
        if (!PermissionManager.isClientOp()) return;
        
        if (!ModConfig.showLoS) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        // Setup Grafico
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0f);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        // 1. LoS del GIOCATORE (Con Info)
        renderEntityLoS(mc, bufferBuilder, matrix, poseStack, mc.player, partialTick, 0.0f, 1.0f, 0.0f, true);

        // 2. LoS dei MOB
        Entity hoveredEntity = null;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) {
            hoveredEntity = ((net.minecraft.world.phys.EntityHitResult) mc.hitResult).getEntity();
        }

        double maxRadiusSqr = ModConfig.allMobsLoSRadius * ModConfig.allMobsLoSRadius;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Mob mob)) continue;

            boolean isHovered = (entity == hoveredEntity);
            boolean isTargetingPlayer = (mob.getTarget() == mc.player);
            boolean isInsideRadius = ModConfig.showAllMobsLoS && mob.distanceToSqr(mc.player) < maxRadiusSqr;

            if (isHovered || isTargetingPlayer || isInsideRadius) {
                renderEntityLoS(mc, bufferBuilder, matrix, poseStack, mob, partialTick, 1.0f, 1.0f, 0.0f, false);
            }
        }

        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    } // <--- CHIUSURA onRenderLevel

    private static void renderEntityLoS(Minecraft mc, BufferBuilder buffer, Matrix4f matrix, PoseStack poseStack, LivingEntity source, float partialTick, float rBase, float gBase, float bBase, boolean showInfo) {
        Vec3 eyePos = source.getEyePosition(partialTick);
        float yaw = source.getViewYRot(partialTick);
        float pitch = source.getViewXRot(partialTick);

        boolean isAllMode = ModConfig.showAllMobsLoS;
        int rays = (isAllMode && !showInfo) ? 20 : 40;
        float fov = 90.0f;
        double range = ModConfig.losDistance;

        int centerIndex = rays / 2;

        for (int i = 0; i <= rays; i++) {
            boolean isCenterRay = (i == centerIndex);

            float offset = (i / (float)rays) * fov - (fov / 2.0f);
            Vec3 direction = calculateViewVector(pitch, yaw + offset);
            Vec3 endPos = eyePos.add(direction.scale(range));

            // Controllo Muri
            BlockHitResult blockResult = mc.level.clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
            Vec3 hitPos = blockResult.getLocation();
            boolean hitBlock = blockResult.getType() != HitResult.Type.MISS;

            // Controllo Entità
            AABB searchBox = source.getBoundingBox().expandTowards(direction.scale(range)).inflate(1.0);
            EntityHitResult entityResult = ProjectileUtil.getEntityHitResult(
                    source, eyePos, hitPos, searchBox,
                    (e) -> !e.isSpectator() && e.isPickable() && e != source, 0.0f
            );

            boolean hitEntity = (entityResult != null);

            float r = rBase, g = gBase, b = bBase;
            String hitInfoName = "";

            if (hitEntity) {
                hitPos = entityResult.getLocation();
                r = 0.2f; g = 0.2f; b = 1.0f;
                hitInfoName = entityResult.getEntity().getName().getString();
            } else if (hitBlock) {
                hitPos = blockResult.getLocation();
                if (rBase == 0.0f) { r = 1.0f; g = 0.0f; b = 0.0f; }
                else { r = 1.0f; g = 0.0f; b = 1.0f; }
                hitInfoName = mc.level.getBlockState(blockResult.getBlockPos()).getBlock().getName().getString();
            }

            float alpha = isCenterRay ? 1.0f : 0.2f;
            if (showInfo && isCenterRay) {
                r = 1.0f; g = 1.0f; b = 1.0f;
            }

            // Disegna linea
            buffer.addVertex(matrix, (float)eyePos.x, (float)eyePos.y - 0.15f, (float)eyePos.z).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, (float)hitPos.x, (float)hitPos.y, (float)hitPos.z).setColor(r, g, b, alpha);

            // INFO E HIGHLIGHT
            if (showInfo && isCenterRay && (hitEntity || hitBlock)) {

                renderImpactMarker(buffer, matrix, hitPos.x, hitPos.y, hitPos.z, 0.1f, r, g, b);

                if (hitEntity) {
                    AABB box = entityResult.getEntity().getBoundingBox();
                    renderWireframeBox(buffer, matrix, box, 0.0f, 1.0f, 1.0f);
                } else if (hitBlock) {
                    BlockPos bp = blockResult.getBlockPos();
                    AABB box = new AABB(bp);
                    renderWireframeBox(buffer, matrix, box, 1.0f, 1.0f, 0.0f);
                }

                double dist = Math.sqrt(eyePos.distanceToSqr(hitPos));
                String text = String.format("%s (%.1fm)", hitInfoName, dist);
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§eTarget: §f" + text), true);
            }
        }
    } // <--- CHIUSURA renderEntityLoS

    private static void renderWireframeBox(BufferBuilder builder, Matrix4f matrix, AABB box, float r, float g, float b) {
        float x1 = (float)box.minX; float y1 = (float)box.minY; float z1 = (float)box.minZ;
        float x2 = (float)box.maxX; float y2 = (float)box.maxY; float z2 = (float)box.maxZ;
        // Base
        builder.addVertex(matrix, x1, y1, z1).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x2, y1, z1).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x2, y1, z1).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x2, y1, z2).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x2, y1, z2).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x1, y1, z2).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x1, y1, z2).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x1, y1, z1).setColor(r, g, b, 1.0f);
        // Top
        builder.addVertex(matrix, x1, y2, z1).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x2, y2, z1).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x2, y2, z1).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x2, y2, z2).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x2, y2, z2).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x1, y2, z2).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x1, y2, z2).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x1, y2, z1).setColor(r, g, b, 1.0f);
        // Columns
        builder.addVertex(matrix, x1, y1, z1).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x1, y2, z1).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x2, y1, z1).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x2, y2, z1).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x2, y1, z2).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x2, y2, z2).setColor(r, g, b, 1.0f);
        builder.addVertex(matrix, x1, y1, z2).setColor(r, g, b, 1.0f); builder.addVertex(matrix, x1, y2, z2).setColor(r, g, b, 1.0f);
    } // <--- CHIUSURA renderWireframeBox

    private static void renderImpactMarker(BufferBuilder builder, Matrix4f matrix, double x, double y, double z, double size, float r, float g, float b) {
        float x1 = (float)(x - size); float x2 = (float)(x + size);
        float y1 = (float)(y - size); float y2 = (float)(y + size);
        float z1 = (float)(z - size); float z2 = (float)(z + size);
        renderWireframeBox(builder, matrix, new AABB(x1, y1, z1, x2, y2, z2), r, g, b);
    } // <--- CHIUSURA renderImpactMarker

    private static Vec3 calculateViewVector(float xRot, float yRot) {
        float f = xRot * ((float)Math.PI / 180F);
        float f1 = -yRot * ((float)Math.PI / 180F);
        float f2 = (float)Math.cos(f1);
        float f3 = (float)Math.sin(f1);
        float f4 = (float)Math.cos(f);
        float f5 = (float)Math.sin(f);
        return new Vec3((double)(f3 * f4), (double)(-f5), (double)(f2 * f4));
    } // <--- CHIUSURA calculateViewVector

} // <--- CHIUSURA CLASSE LoSRenderer (Assolutamente necessaria!)