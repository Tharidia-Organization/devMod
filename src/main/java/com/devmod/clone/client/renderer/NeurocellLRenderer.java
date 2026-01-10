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
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.devmod.clone.block.NeurocellLBlock;
import com.devmod.clone.block.entity.NeurocellLBlockEntity;

/**
 * Renderer for the Large Neurocell block entity (2x2x2).
 * Renders the entity inside the chamber with energy effects.
 * The glass chamber is now part of the block model (not rendered here).
 *
 * <p>Uses LOD (Level of Detail) for performance:
 * <ul>
 *   <li>Distance &lt; 16 blocks: Full 3D entity rendering</li>
 *   <li>Distance 16-64 blocks: 2D billboard sprite</li>
 *   <li>Distance &gt; 64 blocks: Not rendered (culled)</li>
 * </ul>
 */
public class NeurocellLRenderer implements BlockEntityRenderer<NeurocellLBlockEntity> {

    /** Distance threshold for switching to billboard rendering */
    private static final double LOD_BILLBOARD_DISTANCE_SQ = 16 * 16; // 16 blocks

    private final EntityRenderDispatcher entityRenderer;

    // LRU cache for entity instances
    private final Map<String, Entity> entityCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entity> eldest) {
            return this.size() > 50;
        }
    };

    public NeurocellLRenderer(BlockEntityRendererProvider.Context context) {
        this.entityRenderer = Minecraft.getInstance().getEntityRenderDispatcher();
    }

    @Override
    public void render(
        @Nonnull NeurocellLBlockEntity blockEntity,
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

        // Render if cloning OR has ragdoll
        boolean isCloning = blockEntity.isCloning();
        boolean hasRagdoll = blockEntity.hasRagdoll();
        if (!isCloning && !hasRagdoll) {
            return;
        }

        float progress = blockEntity.getCloningProgress();
        var level = blockEntity.getLevel();
        long gameTime = level != null ? level.getGameTime() : 0;
        float animTime = (gameTime + partialTick) * 0.05f;

        // Get facing direction to calculate correct entity position
        // The glass chamber rotates with the model, so the center position changes
        Direction facing = Direction.NORTH;
        if (level != null) {
            BlockState state = level.getBlockState(Objects.requireNonNull(blockEntity.getBlockPos()));
            if (state.hasProperty(Objects.requireNonNull(NeurocellLBlock.FACING))) {
                facing = state.getValue(Objects.requireNonNull(NeurocellLBlock.FACING));
            }
        }

        // Calculate entity center position based on facing
        // Model rotates around block center (0.5, 0.5), shifting the glass chamber position:
        // - NORTH (0°):   center at (1.0, 1.0) - original, no rotation
        // - SOUTH (180°): center at (0.0, 0.0) - both axes flip
        // - EAST (90°):   center at (0.0, 1.0) - X flips
        // - WEST (270°):  center at (1.0, 0.0) - Z flips
        float entityCenterX = switch (facing) {
            case SOUTH, EAST -> 0.0f;
            default -> 1.0f;  // NORTH, WEST
        };
        float entityCenterZ = switch (facing) {
            case SOUTH, WEST -> 0.0f;
            default -> 1.0f;  // NORTH, EAST
        };

        // Calculate distance for LOD
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distSq = cameraPos.distanceToSqr(pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);

        // LOD: Use billboard for distant entities
        if (distSq > LOD_BILLBOARD_DISTANCE_SQ) {
            // Add to billboard batch instead of full render
            float growthScale = isCloning ? 0.05f + progress * 0.95f : 1.0f;
            float alpha = isCloning ? 0.7f + 0.3f * progress : 1.0f;
            BillboardBatcher.getInstance().addBillboard(
                pos.getX() + entityCenterX,
                pos.getY() + 1.2,
                pos.getZ() + entityCenterZ,
                entityTypeString,
                1.2f * growthScale, // Larger billboard for 2x2x2
                alpha
            );

            // Still render energy effects (they're VBO-optimized)
            if (isCloning || hasRagdoll) {
                renderEnergyEffectsVBO(poseStack, animTime, entityCenterX, entityCenterZ);
            }
            return;
        }

        // Full entity render for close distance
        try {
            Optional<EntityType<?>> optType = EntityType.byString(entityTypeString);
            if (optType.isEmpty() || blockEntity.getLevel() == null) {
                return;
            }

            EntityType<?> type = optType.get();
            Entity entity = entityCache.get(entityTypeString);
            if (entity == null) {
                // Use client level for rendering only
                net.minecraft.client.multiplayer.ClientLevel clientLevel =
                    Minecraft.getInstance().level;
                if (clientLevel == null) {
                    return;
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
                    entity.setPos(0, -1000, 0);

                    // 3. The entity is NEVER added to the level's entity list
                    // It exists ONLY in our local cache - it will NEVER tick

                    entityCache.put(entityTypeString, entity);
                }
            }

            if (entity != null) {
                poseStack.pushPose();

                // Position in center of glass chamber (adjusted for facing rotation)
                // Floor at Y=7 pixels = 0.4375 blocks, so entity at ~0.5625 like 1x2
                float bobOffset = Mth.sin(animTime * 0.8f) * 0.03f;
                poseStack.translate(entityCenterX, 0.5625 + bobOffset, entityCenterZ);

                // Slow gentle rotation for suspended-in-void effect
                float slowRotation = animTime * 8.0f;
                poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(180.0f + slowRotation)));

                // Scale to fit in glass chamber (22 pixels wide = 1.375 blocks)
                // Quadrupeds (cow, pig, horse) have body length >> getBbWidth()
                // Use higher margin (1.8) to account for rotated long bodies
                float entityWidth = entity.getBbWidth();
                float entityHeight = entity.getBbHeight();
                float visualMargin = 1.8f; // Accounts for quadruped body length when rotated
                float visualWidth = entityWidth * visualMargin;
                float maxDimension = Math.max(visualWidth, entityHeight);
                float targetSize = 1.0f; // Conservative to keep all entities inside glass
                float scale = maxDimension > targetSize ? targetSize / maxDimension : 1.0f;
                scale = Math.max(0.1f, scale);

                // Growth scale - only during active cloning
                float growthScale = isCloning ? 0.05f + progress * 0.95f : 1.0f;
                poseStack.scale(scale * growthScale, scale * growthScale, scale * growthScale);

                // Entity stays in place but keeps its idle animations
                entity.setSilent(true);

                // Use world time for proper animation speed (20 ticks per second)
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
                    // Disable hurt animation and other visual effects
                    living.hurtTime = 0;
                    living.deathTime = 0;
                }

                // Render entity model via EntityRenderDispatcher
                entityRenderer.render(entity, 0.0, 0.0, 0.0, 0.0f, partialTick, poseStack, buffer, LightTexture.FULL_BRIGHT);

                poseStack.popPose();
            }
        } catch (RuntimeException e) {
            // Silently ignore rendering errors
        }

        // Render energy effects when entity is present (using VBO)
        if (isCloning || hasRagdoll) {
            renderEnergyEffectsVBO(poseStack, animTime, entityCenterX, entityCenterZ);
        }
    }

    /**
     * Render energy effects using pre-computed VBO geometry.
     * Uses a larger scale (1.3x) compared to regular Neurocell.
     */
    private void renderEnergyEffectsVBO(PoseStack poseStack, float animTime,
                                         float centerX, float centerZ) {
        poseStack.pushPose();
        // Center at entity position (adjusted for facing)
        poseStack.translate(centerX, 0.5, centerZ);

        // Set shader for position+color+normal rendering
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Render effects using VBO singleton with larger scale for 2x2x2 chamber
        NeurocellEffectsVBO.getInstance().renderEffects(poseStack, animTime, 1.3f);

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull NeurocellLBlockEntity blockEntity) {
        // Render even when the block is at the edge of the screen (multi-block structure)
        return true;
    }

    @Override
    public boolean shouldRender(@Nonnull NeurocellLBlockEntity blockEntity, @Nonnull Vec3 cameraPos) {
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
        double distSq = cameraPos.distanceToSqr(pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        if (distSq > 64 * 64) {
            return false;
        }

        return true;
    }
}
