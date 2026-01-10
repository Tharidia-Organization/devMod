package com.devmod.clone.client.renderer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.clone.block.NeurocellLBlock;
import com.devmod.clone.block.entity.NeurocellLBlockEntity;

/**
 * Renderer for the Large Neurocell block entity (2x2x2).
 * Renders the entity inside the chamber with energy effects.
 * The glass chamber is now part of the block model (not rendered here).
 */
public class NeurocellLRenderer implements BlockEntityRenderer<NeurocellLBlockEntity> {

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

        // Render energy effects when entity is present
        if (isCloning || hasRagdoll) {
            renderEnergyEffects(poseStack, buffer, animTime, entityCenterX, entityCenterZ);
        }
    }

    /**
     * Render energy effects around the entity (scanning rings and helix).
     */
    private void renderEnergyEffects(PoseStack poseStack, MultiBufferSource buffer, float animTime,
                                      float centerX, float centerZ) {
        VertexConsumer vc = buffer.getBuffer(Objects.requireNonNull(RenderType.lightning()));

        poseStack.pushPose();
        // Center at entity position (adjusted for facing)
        poseStack.translate(centerX, 0.5, centerZ);

        // Scanning ring that moves up and down (scaled for 1.2 target size)
        float scanY = 0.3f + 1.0f * (0.5f + 0.5f * Mth.sin(animTime * 1.2f));
        renderScanRing(poseStack, vc, scanY, 0.55f, animTime);

        // Second ring moving opposite
        float scanY2 = 1.3f - 1.0f * (0.5f + 0.5f * Mth.sin(animTime * 1.2f));
        renderScanRing(poseStack, vc, scanY2, 0.50f, -animTime);

        // Rotating energy helix
        renderEnergyHelix(poseStack, vc, animTime);

        poseStack.popPose();
    }

    private void renderScanRing(PoseStack poseStack, VertexConsumer vc, float y, float radius, float rotation) {
        poseStack.pushPose();
        poseStack.translate(0, y, 0);
        poseStack.mulPose(Objects.requireNonNull(Axis.YP.rotationDegrees(rotation * 50.0f)));

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());
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
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        // Continuous smooth pulsing effect using sine wave (no pauses)
        float cycleTime = animTime % 5.0f;
        float fadeMultiplier = 0.5f + 0.5f * Mth.sin((cycleTime / 5.0f) * 6.28318f - 1.5708f);

        // Two intertwined helixes
        for (int helix = 0; helix < 2; helix++) {
            float phaseOffset = helix * 3.14159f;

            for (int i = 0; i < 40; i++) {
                float t = i / 40.0f;
                // Helix Y range matches entity position (0.1 to 1.5 above the translate point)
                float y = 0.1f + t * 1.4f;
                float angle = t * 6.28318f * 2 + animTime * 2.0f + phaseOffset;
                // Radius scaled for 1.2 target size (slightly smaller than glass)
                float radius = 0.45f + 0.04f * Mth.sin(t * 6.28f);

                float x = Mth.cos(angle) * radius;
                float z = Mth.sin(angle) * radius;

                // Particle size
                float size = 0.018f + 0.008f * Mth.sin(t * 12.56f + animTime * 3);

                // Color shifts along helix
                int r = helix == 0 ? 0 : 20;
                int g = (int)(160 + 30 * t);
                int b = 200;
                int alpha = (int)((25 + 15 * (1 - t)) * fadeMultiplier);

                // Front face
                vc.addVertex(matrix, x - size, y - size, z).setColor(r, g, b, alpha).setNormal(0, 0, 1);
                vc.addVertex(matrix, x + size, y - size, z).setColor(r, g, b, alpha).setNormal(0, 0, 1);
                vc.addVertex(matrix, x + size, y + size, z).setColor(r, g, b, alpha).setNormal(0, 0, 1);
                vc.addVertex(matrix, x - size, y + size, z).setColor(r, g, b, alpha).setNormal(0, 0, 1);

                // Back face (reverse winding for visibility from both sides)
                vc.addVertex(matrix, x + size, y - size, z).setColor(r, g, b, alpha).setNormal(0, 0, -1);
                vc.addVertex(matrix, x - size, y - size, z).setColor(r, g, b, alpha).setNormal(0, 0, -1);
                vc.addVertex(matrix, x - size, y + size, z).setColor(r, g, b, alpha).setNormal(0, 0, -1);
                vc.addVertex(matrix, x + size, y + size, z).setColor(r, g, b, alpha).setNormal(0, 0, -1);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull NeurocellLBlockEntity blockEntity) {
        // Render even when the block is at the edge of the screen (multi-block structure)
        return true;
    }
}
