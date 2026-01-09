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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import com.devmod.DevMod;
import com.devmod.clone.block.entity.NeurocellLBlockEntity;

/**
 * Renderer for the Large Neurocell block entity (3x3x3).
 * Renders the glass chamber structure and larger entities.
 */
public class NeurocellLRenderer implements BlockEntityRenderer<NeurocellLBlockEntity> {

    private static final ResourceLocation NEUROCELL_TEXTURE = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "block/neurocell");

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
        // Always render the glass chamber structure
        renderChamber(poseStack, buffer, light, overlay);

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

                // Position in center of 3x3x3 chamber
                // Center is at (0.5, 1.5, 0.5) - middle of the structure
                poseStack.translate(0.5, 1.5, 0.5);

                // Fixed rotation - entities face forward
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));

                // Scale to fit in larger chamber (targetSize 2.5 for 3x3x3)
                float entityWidth = entity.getBbWidth();
                float entityHeight = entity.getBbHeight();
                float visualMargin = 1.3f;
                float visualWidth = entityWidth * visualMargin;
                float maxDimension = Math.max(visualWidth, entityHeight);
                float targetSize = 2.5f; // Much larger than standard Neurocell (0.85)
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
        } catch (RuntimeException e) {
            // Silently ignore rendering errors
        }
    }

    /**
     * Render the glass chamber structure (3x3x3).
     */
    @SuppressWarnings("deprecation")
    private void renderChamber(PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        poseStack.pushPose();

        // Get the texture sprite
        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
            .apply(NEUROCELL_TEXTURE);

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.translucent());
        Matrix4f matrix = poseStack.last().pose();

        // Chamber dimensions (in blocks, relative to center block)
        float minX = -1.0f;    // One block west
        float maxX = 2.0f;     // Two blocks east (center + one)
        float minY = 0.0f;     // Floor at Y=0
        float maxY = 3.0f;     // Three blocks tall
        float minZ = -1.0f;    // One block north
        float maxZ = 2.0f;     // Two blocks south

        // Wall thickness
        float thickness = 0.0625f; // 1 pixel

        // Glass tint (slightly cyan like the neurocell)
        int r = 180, g = 220, b = 255, a = 180;

        // UV coordinates from sprite
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        // === FLOOR (bottom) ===
        renderQuad(vertexConsumer, matrix,
            minX, minY, minZ,
            maxX, minY, minZ,
            maxX, minY, maxZ,
            minX, minY, maxZ,
            0, -1, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);

        // === CEILING (top) ===
        renderQuad(vertexConsumer, matrix,
            minX, maxY, maxZ,
            maxX, maxY, maxZ,
            maxX, maxY, minZ,
            minX, maxY, minZ,
            0, 1, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);

        // === NORTH WALL (Z = minZ) - outside ===
        renderQuad(vertexConsumer, matrix,
            maxX, minY, minZ,
            minX, minY, minZ,
            minX, maxY, minZ,
            maxX, maxY, minZ,
            0, 0, -1, u0, v0, u1, v1, r, g, b, a, light, overlay);

        // === SOUTH WALL (Z = maxZ) - outside ===
        renderQuad(vertexConsumer, matrix,
            minX, minY, maxZ,
            maxX, minY, maxZ,
            maxX, maxY, maxZ,
            minX, maxY, maxZ,
            0, 0, 1, u0, v0, u1, v1, r, g, b, a, light, overlay);

        // === WEST WALL (X = minX) - outside ===
        renderQuad(vertexConsumer, matrix,
            minX, minY, minZ,
            minX, minY, maxZ,
            minX, maxY, maxZ,
            minX, maxY, minZ,
            -1, 0, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);

        // === EAST WALL (X = maxX) - outside ===
        renderQuad(vertexConsumer, matrix,
            maxX, minY, maxZ,
            maxX, minY, minZ,
            maxX, maxY, minZ,
            maxX, maxY, maxZ,
            1, 0, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);

        // === Inside faces (for proper translucency) ===
        float inMinX = minX + thickness;
        float inMaxX = maxX - thickness;
        float inMinY = minY + thickness;
        float inMaxY = maxY - thickness;
        float inMinZ = minZ + thickness;
        float inMaxZ = maxZ - thickness;

        // Floor inside
        renderQuad(vertexConsumer, matrix,
            inMinX, inMinY, inMaxZ,
            inMaxX, inMinY, inMaxZ,
            inMaxX, inMinY, inMinZ,
            inMinX, inMinY, inMinZ,
            0, 1, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);

        // Ceiling inside
        renderQuad(vertexConsumer, matrix,
            inMinX, inMaxY, inMinZ,
            inMaxX, inMaxY, inMinZ,
            inMaxX, inMaxY, inMaxZ,
            inMinX, inMaxY, inMaxZ,
            0, -1, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);

        // North wall inside
        renderQuad(vertexConsumer, matrix,
            inMinX, inMinY, inMinZ + thickness,
            inMaxX, inMinY, inMinZ + thickness,
            inMaxX, inMaxY, inMinZ + thickness,
            inMinX, inMaxY, inMinZ + thickness,
            0, 0, 1, u0, v0, u1, v1, r, g, b, a, light, overlay);

        // South wall inside
        renderQuad(vertexConsumer, matrix,
            inMaxX, inMinY, inMaxZ - thickness,
            inMinX, inMinY, inMaxZ - thickness,
            inMinX, inMaxY, inMaxZ - thickness,
            inMaxX, inMaxY, inMaxZ - thickness,
            0, 0, -1, u0, v0, u1, v1, r, g, b, a, light, overlay);

        // West wall inside
        renderQuad(vertexConsumer, matrix,
            inMinX + thickness, inMinY, inMaxZ,
            inMinX + thickness, inMinY, inMinZ,
            inMinX + thickness, inMaxY, inMinZ,
            inMinX + thickness, inMaxY, inMaxZ,
            1, 0, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);

        // East wall inside
        renderQuad(vertexConsumer, matrix,
            inMaxX - thickness, inMinY, inMinZ,
            inMaxX - thickness, inMinY, inMaxZ,
            inMaxX - thickness, inMaxY, inMaxZ,
            inMaxX - thickness, inMaxY, inMinZ,
            -1, 0, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);

        poseStack.popPose();
    }

    private void renderQuad(
        VertexConsumer consumer, Matrix4f matrix,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float x3, float y3, float z3,
        float x4, float y4, float z4,
        float nx, float ny, float nz,
        float u0, float v0, float u1, float v1,
        int r, int g, int b, int a,
        int light, int overlay
    ) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
        consumer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen(@Nonnull NeurocellLBlockEntity blockEntity) {
        // Render even when the block is at the edge of the screen (large block)
        return true;
    }
}
