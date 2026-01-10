package com.devmod.clone.client.renderer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import com.devmod.clone.block.entity.NeurocellBlockEntity;

/**
 * Renderer for the Neurocell block entity.
 * Renders a preview of the entity being cloned inside the chamber
 * with energy scan effects.
 *
 * <p>Uses LOD (Level of Detail) for performance:
 * <ul>
 *   <li>Distance &lt; 16 blocks: Full 3D entity rendering</li>
 *   <li>Distance 16-64 blocks: 2D billboard sprite</li>
 *   <li>Distance &gt; 64 blocks: Not rendered (culled)</li>
 * </ul>
 */
public class NeurocellRenderer implements BlockEntityRenderer<NeurocellBlockEntity> {

    /** Distance threshold for switching to billboard rendering */
    private static final double LOD_BILLBOARD_DISTANCE_SQ = 16 * 16; // 16 blocks

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
        var level = blockEntity.getLevel();
        long gameTime = level != null ? level.getGameTime() : 0;
        float animTime = (gameTime + partialTick) * 0.05f;

        // Calculate distance for LOD
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distSq = cameraPos.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        // LOD: Use billboard for distant entities
        if (distSq > LOD_BILLBOARD_DISTANCE_SQ) {
            // Add to billboard batch instead of full render
            float growthScale = isCloning ? 0.05f + progress * 0.95f : 1.0f;
            float alpha = isCloning ? 0.7f + 0.3f * progress : 1.0f;
            BillboardBatcher.getInstance().addBillboard(
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                entityTypeString,
                0.8f * growthScale,
                alpha
            );

            // Still render energy effects (they're VBO-optimized)
            if (isCloning || hasRagdoll) {
                renderEnergyEffectsVBO(poseStack, animTime);
            }
            return;
        }

        // Full entity render for close distance
        try {
            Optional<EntityType<?>> optType = EntityType.byString(entityTypeString);
            if (optType.isEmpty() || level == null) {
                return;
            }

            EntityType<?> type = optType.get();
            Entity entity = entityCache.get(entityTypeString);
            if (entity == null) {
                // === USE CLIENT LEVEL FOR RENDERING ONLY ===
                // This creates an entity that is NEVER added to any tick list
                // and exists purely for visual rendering purposes
                net.minecraft.client.multiplayer.ClientLevel clientLevel =
                    Minecraft.getInstance().level;

                if (clientLevel == null) {
                    return; // No client level available
                }

                entity = type.create(clientLevel);
                if (entity != null) {
                    // === CRITICAL: Ensure entity is 100% isolated from game logic ===

                    // 1. Disable ALL physics and world interaction
                    entity.noPhysics = true;
                    entity.setNoGravity(true);
                    entity.setSilent(true);
                    entity.setInvulnerable(true);

                    // 2. Position far from any possible interaction
                    // This ensures the entity's bounding box is nowhere near the player
                    entity.setPos(0, -1000, 0);

                    // 3. The entity is NEVER added to the level's entity list
                    // It exists ONLY in our local cache - it will NEVER tick
                    // because Level.tick() only iterates over entities in its internal lists

                    entityCache.put(entityTypeString, entity);
                }
            }

            if (entity != null) {
                poseStack.pushPose();

                // Smooth floating bobbing effect - subtle up/down motion
                float bobOffset = Mth.sin(animTime * 0.8f) * 0.03f;
                poseStack.translate(0.5, 0.5625 + bobOffset, 0.5);

                // Slow gentle rotation for suspended-in-void effect
                float slowRotation = animTime * 8.0f;
                poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(180.0f + slowRotation)));

                float entityWidth = entity.getBbWidth();
                float entityHeight = entity.getBbHeight();
                float maxDimension = Math.max(entityWidth * 1.3f, entityHeight);
                float scale = maxDimension > 0.85f ? 0.85f / maxDimension : 1.0f;
                scale = Math.max(0.1f, scale);

                float growthScale = isCloning ? 0.05f + progress * 0.95f : 1.0f;
                poseStack.scale(scale * growthScale, scale * growthScale, scale * growthScale);

                // Update tick for animations without adding to world
                if (level != null) {
                    entity.tickCount = (int) (level.getGameTime() % Integer.MAX_VALUE);
                }

                if (entity instanceof LivingEntity living) {
                    living.walkAnimation.setSpeed(0);
                    living.walkAnimation.update(0, 0);
                    living.yBodyRotO = living.yBodyRot;
                    living.yHeadRotO = living.yHeadRot;
                    // Disable hurt animation and other visual effects
                    living.hurtTime = 0;
                    living.deathTime = 0;
                }

                // Render entity model via EntityRenderDispatcher
                entityRenderer.render(entity, 0.0, 0.0, 0.0, 0.0f, partialTick, poseStack, buffer, LightTexture.FULL_BRIGHT);
                poseStack.popPose();
            }
        } catch (RuntimeException e) {
            // Ignore
        }

        // Render energy effects when entity is present (using VBO)
        if (isCloning || hasRagdoll) {
            renderEnergyEffectsVBO(poseStack, animTime);
        }
    }

    /**
     * Render energy effects using pre-computed VBO geometry.
     * This is significantly more efficient than immediate mode rendering.
     */
    private void renderEnergyEffectsVBO(PoseStack poseStack, float animTime) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        // Set shader for position+color+normal rendering
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Render effects using VBO singleton
        NeurocellEffectsVBO.getInstance().renderEffects(poseStack, animTime);

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull NeurocellBlockEntity blockEntity) {
        return true;
    }

    @Override
    public boolean shouldRender(@Nonnull NeurocellBlockEntity blockEntity, @Nonnull Vec3 cameraPos) {
        // Early exit: Skip rendering if no entity type set
        String entityType = blockEntity.getEntityType();
        if (entityType == null || entityType.isEmpty()) {
            return false;
        }

        // Early exit: Skip if not cloning and no ragdoll
        if (!blockEntity.isCloning() && !blockEntity.hasRagdoll()) {
            return false;
        }

        // Distance-based culling: skip rendering beyond 64 blocks
        BlockPos pos = blockEntity.getBlockPos();
        double distSq = cameraPos.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (distSq > 64 * 64) {
            return false;
        }

        return true;
    }
}
