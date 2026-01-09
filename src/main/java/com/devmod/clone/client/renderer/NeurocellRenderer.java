package com.devmod.clone.client.renderer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import com.devmod.clone.block.entity.NeurocellBlockEntity;

/**
 * Renderer for the Neurocell block entity.
 * Renders a preview of the entity being cloned inside the chamber.
 * Based on Hologenica's NeurocellRenderer.
 */
public class NeurocellRenderer implements BlockEntityRenderer<NeurocellBlockEntity> {

    private final EntityRenderDispatcher entityRenderer;

    // LRU cache for entity instances
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
        // Check if we have an entity to render
        String entityTypeString = blockEntity.getEntityType();
        if (entityTypeString == null || entityTypeString.isEmpty()) {
            return;
        }

        // Hologenica logic: render if cloning OR has ragdoll
        boolean isCloning = blockEntity.isCloning();
        boolean hasRagdoll = blockEntity.hasRagdoll();
        if (!isCloning && !hasRagdoll) {
            return;
        }

        float progress = blockEntity.getCloningProgress();

        try {
            // Parse entity type
            ResourceLocation entityTypeId = ResourceLocation.tryParse(entityTypeString);
            if (entityTypeId == null) {
                return;
            }

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

                // Position in center of glass chamber (Y=0.5625 like Hologenica)
                // NO bob animation - entities are static in display mode
                poseStack.translate(0.5, 0.5625, 0.5);

                // Fixed rotation - NO spin animation, entities face forward
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));

                // Scale to fit in chamber (targetSize 0.85 like Hologenica)
                float entityWidth = entity.getBbWidth();
                float entityHeight = entity.getBbHeight();
                float visualMargin = 1.3f;
                float visualWidth = entityWidth * visualMargin;
                float maxDimension = Math.max(visualWidth, entityHeight);
                float targetSize = 0.85f;
                float scale = maxDimension > targetSize ? targetSize / maxDimension : 1.0f;
                scale = Math.max(0.1f, scale);

                // Growth scale - only during active cloning
                float growthScale = isCloning ? 0.05f + progress * 0.95f : 1.0f;
                poseStack.scale(scale * growthScale, scale * growthScale, scale * growthScale);

                // Entity stays in place but keeps its idle animations
                entity.setSilent(true);

                // Use world time for proper animation speed (20 ticks per second)
                var level = blockEntity.getLevel();
                if (level != null) {
                    entity.tickCount = (int) (level.getGameTime() % Integer.MAX_VALUE);
                }

                // Freeze walk animation for LivingEntity (cow, pig, etc.)
                // but keep other animations running (blaze rods, etc.)
                if (entity instanceof LivingEntity living) {
                    living.walkAnimation.setSpeed(0);
                    living.walkAnimation.update(0, 0);
                    // Sync old/current rotations to prevent interpolation trembling
                    living.yBodyRotO = living.yBodyRot;
                    living.yHeadRotO = living.yHeadRot;
                    living.xRotO = living.getXRot();
                    living.yRotO = living.getYRot();
                }

                // Render with full brightness (max light)
                entityRenderer.render(entity, 0.0, 0.0, 0.0, 0.0f, partialTick, poseStack, buffer, LightTexture.FULL_BRIGHT);

                poseStack.popPose();
            }
        } catch (Exception e) {
            // Silently ignore rendering errors
        }
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull NeurocellBlockEntity blockEntity) {
        // Render even when the block is at the edge of the screen (tall block)
        return true;
    }
}
