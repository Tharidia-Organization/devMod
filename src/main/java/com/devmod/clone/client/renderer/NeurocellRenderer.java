package com.devmod.clone.client.renderer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import com.devmod.clone.block.entity.NeurocellBlockEntity;

/**
 * Renderer for the Neurocell block entity.
 * Renders a preview of the entity being cloned inside the chamber
 * with energy scan effects.
 */
public class NeurocellRenderer implements BlockEntityRenderer<NeurocellBlockEntity> {

    private final EntityRenderDispatcher entityRenderer;

    private final Map<String, Entity> entityCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entity> eldest) {
            return this.size() > 50;
        }
    };

    public NeurocellRenderer(BlockEntityRendererProvider.Context context) {
        this.entityRenderer = Minecraft.getInstance().getEntityRenderDispatcher();
    }

    @Override
    public void render(
        @Nonnull NeurocellBlockEntity blockEntity,
        float partialTick,
        @Nonnull PoseStack poseStack,
        @Nonnull MultiBufferSource buffer,
        int light,
        int overlay
    ) {
        String entityTypeString = blockEntity.getEntityType();
        if (entityTypeString == null || entityTypeString.isEmpty()) {
            return;
        }

        boolean isCloning = blockEntity.isCloning();
        boolean hasRagdoll = blockEntity.hasRagdoll();
        if (!isCloning && !hasRagdoll) {
            return;
        }

        float progress = blockEntity.getCloningProgress();
        long gameTime = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0;
        float animTime = (gameTime + partialTick) * 0.05f;

        // Render entity
        try {
            Optional<EntityType<?>> optType = EntityType.byString(entityTypeString);
            if (optType.isEmpty() || blockEntity.getLevel() == null) {
                return;
            }

            EntityType<?> type = optType.get();
            Entity entity = entityCache.get(entityTypeString);
            if (entity == null) {
                entity = type.create(blockEntity.getLevel());
                if (entity != null) {
                    entityCache.put(entityTypeString, entity);
                }
            }

            if (entity != null) {
                poseStack.pushPose();
                poseStack.translate(0.5, 0.5625, 0.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));

                float entityWidth = entity.getBbWidth();
                float entityHeight = entity.getBbHeight();
                float maxDimension = Math.max(entityWidth * 1.3f, entityHeight);
                float scale = maxDimension > 0.85f ? 0.85f / maxDimension : 1.0f;
                scale = Math.max(0.1f, scale);

                float growthScale = isCloning ? 0.05f + progress * 0.95f : 1.0f;
                poseStack.scale(scale * growthScale, scale * growthScale, scale * growthScale);

                entity.setSilent(true);
                if (blockEntity.getLevel() != null) {
                    entity.tickCount = (int) (blockEntity.getLevel().getGameTime() % Integer.MAX_VALUE);
                }

                if (entity instanceof LivingEntity living) {
                    living.walkAnimation.setSpeed(0);
                    living.walkAnimation.update(0, 0);
                    living.yBodyRotO = living.yBodyRot;
                    living.yHeadRotO = living.yHeadRot;
                }

                entityRenderer.render(entity, 0.0, 0.0, 0.0, 0.0f, partialTick, poseStack, buffer, LightTexture.FULL_BRIGHT);
                poseStack.popPose();
            }
        } catch (RuntimeException e) {
            // Ignore
        }

        // Render energy effects when entity is present
        if (isCloning || hasRagdoll) {
            renderEnergyEffects(poseStack, buffer, animTime);
        }
    }

    private void renderEnergyEffects(PoseStack poseStack, MultiBufferSource buffer, float animTime) {
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        // Scanning ring that moves up and down
        float scanY = 0.3f + 1.0f * (0.5f + 0.5f * Mth.sin(animTime * 1.2f));
        renderScanRing(poseStack, vc, scanY, 0.42f, animTime);

        // Second ring moving opposite
        float scanY2 = 1.3f - 1.0f * (0.5f + 0.5f * Mth.sin(animTime * 1.2f));
        renderScanRing(poseStack, vc, scanY2, 0.38f, -animTime);

        // Rotating energy helix
        renderEnergyHelix(poseStack, vc, animTime);

        poseStack.popPose();
    }

    private void renderScanRing(PoseStack poseStack, VertexConsumer vc, float y, float radius, float rotation) {
        poseStack.pushPose();
        poseStack.translate(0, y, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation * 50.0f));

        Matrix4f matrix = poseStack.last().pose();
        int segments = 32;

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (i * 2 * Math.PI / segments);
            float angle2 = (float) ((i + 1) * 2 * Math.PI / segments);

            float x1 = Mth.cos(angle1) * radius;
            float z1 = Mth.sin(angle1) * radius;
            float x2 = Mth.cos(angle2) * radius;
            float z2 = Mth.sin(angle2) * radius;

            // Brightness pulse around ring
            float brightness = 0.5f + 0.5f * Mth.sin(angle1 * 4 + rotation * 2);
            int alpha = (int)(brightness * 220);
            int r = 0, g = (int)(220 + brightness * 35), b = 255;

            // Ring line (thin quad)
            float h = 0.02f;
            vc.addVertex(matrix, x1, -h, z1).setColor(r, g, b, alpha).setNormal(0, 1, 0);
            vc.addVertex(matrix, x2, -h, z2).setColor(r, g, b, alpha).setNormal(0, 1, 0);
            vc.addVertex(matrix, x2, h, z2).setColor(r, g, b, alpha).setNormal(0, 1, 0);
            vc.addVertex(matrix, x1, h, z1).setColor(r, g, b, alpha).setNormal(0, 1, 0);
        }

        poseStack.popPose();
    }

    private void renderEnergyHelix(PoseStack poseStack, VertexConsumer vc, float animTime) {
        Matrix4f matrix = poseStack.last().pose();

        // Two intertwined helixes
        for (int helix = 0; helix < 2; helix++) {
            float phaseOffset = helix * 3.14159f;

            for (int i = 0; i < 40; i++) {
                float t = i / 40.0f;
                float y = 0.1f + t * 1.4f;
                float angle = t * 6.28318f * 2 + animTime * 2.0f + phaseOffset;
                float radius = 0.35f + 0.05f * Mth.sin(t * 6.28f);

                float x = Mth.cos(angle) * radius;
                float z = Mth.sin(angle) * radius;

                // Particle size varies
                float size = 0.015f + 0.01f * Mth.sin(t * 12.56f + animTime * 3);

                // Color shifts along helix
                int r = helix == 0 ? 0 : 50;
                int g = (int)(200 + 55 * t);
                int b = 255;
                int alpha = (int)(180 + 75 * (1 - t));

                // Small glowing point
                vc.addVertex(matrix, x - size, y - size, z).setColor(r, g, b, alpha).setNormal(0, 1, 0);
                vc.addVertex(matrix, x + size, y - size, z).setColor(r, g, b, alpha).setNormal(0, 1, 0);
                vc.addVertex(matrix, x + size, y + size, z).setColor(r, g, b, alpha).setNormal(0, 1, 0);
                vc.addVertex(matrix, x - size, y + size, z).setColor(r, g, b, alpha).setNormal(0, 1, 0);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull NeurocellBlockEntity blockEntity) {
        return true;
    }
}
