package com.frenkvs.devmod;

import static com.frenkvs.devmod.DevMod.MODID;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import javax.annotation.Nonnull;
import java.util.Objects;

@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
// Minecraft API methods are not annotated but never return null in practice
@SuppressWarnings("null")
public class WorldRenderEvents {

    @SubscribeEvent
    public static void onRenderLevel(@Nonnull RenderLevelStageEvent event) {
        // If the user has disabled rendering in settings, stop immediately
        if (!ModConfig.showRender) return;

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        if (level == null) return;

        Vec3 cameraPos = Objects.requireNonNull(event.getCamera().getPosition());

        // NEW FEATURE: Render 3D spheres for debug overlay (G key)
        if (com.frenkvs.devmod.rendering.DebugRenderer.INSTANCE.isEnabled()) {
            renderDebugSpheres(event.getPoseStack(), cameraPos, level);
        }

        // FASE 4: Heatmap Visualizer (tasto H)
        var bufferSource = mc.renderBuffers().bufferSource();
        if (com.frenkvs.devmod.rendering.HeatmapVisualizer.INSTANCE.hasActiveHeatmaps()) {
            com.frenkvs.devmod.rendering.HeatmapVisualizer.INSTANCE.render(event.getPoseStack(), bufferSource, cameraPos);
        }

        // FASE 4: Light Level Overlay (tasto L)
        if (com.frenkvs.devmod.rendering.LightLevelOverlay.INSTANCE.isEnabled()) {
            com.frenkvs.devmod.rendering.LightLevelOverlay.INSTANCE.render(event.getPoseStack(), bufferSource, cameraPos);
        }

        // FASE 4: Room Bounds Visualizer (tasto R)
        if (com.frenkvs.devmod.rendering.RoomBoundsVisualizer.INSTANCE.isEnabled()) {
            com.frenkvs.devmod.rendering.RoomBoundsVisualizer.INSTANCE.render(event.getPoseStack(), bufferSource, cameraPos);
        }

        // FASE 4: Pathfinding Debugger (tasto P)
        // MOVED to RenderEvents.java for proper buffer management
        // PathfindingDebugger is now rendered in AFTER_ENTITIES stage with proper flush

        // FASE 4: Line of Sight Visualizer (tasto V)
        if (com.frenkvs.devmod.rendering.LineOfSightVisualizer.INSTANCE.isEnabled()) {
            com.frenkvs.devmod.rendering.LineOfSightVisualizer.INSTANCE.render(event.getPoseStack(), bufferSource, cameraPos);
        }

        // FASE 4: Vertical Levels Visualizer (tasto Y)
        if (com.frenkvs.devmod.rendering.VerticalLevelsVisualizer.INSTANCE.isEnabled()) {
            com.frenkvs.devmod.rendering.VerticalLevelsVisualizer.INSTANCE.render(event.getPoseStack(), bufferSource, cameraPos);
        }

        // FASE 4: Safe Spot Visualizer (tasto C)
        if (com.frenkvs.devmod.rendering.SafeSpotVisualizer.INSTANCE.isEnabled()) {
            com.frenkvs.devmod.rendering.SafeSpotVisualizer.INSTANCE.render(event.getPoseStack(), bufferSource, cameraPos);
        }

        // VOXEL-LAB M27: Spawnability Map (tasto F4)
        if (com.frenkvs.devmod.rendering.SpawnabilityOverlay.INSTANCE.isEnabled()) {
            com.frenkvs.devmod.rendering.SpawnabilityOverlay.INSTANCE.render(event.getPoseStack(), bufferSource, cameraPos);
        }

        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof Mob mob) {
                if (mob.distanceToSqr(Objects.requireNonNull(mc.player)) > 1600) continue;

                // 1. VIEW RANGE (Follow Range)
                double followRange = 0;
                if (mob.getAttribute(Objects.requireNonNull(Attributes.FOLLOW_RANGE)) != null) {
                    followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
                }

                if (followRange > 0 && followRange <= 64) {
                    if (ModConfig.renderAsBlocks) {
                        // BLOCKS mode
                        renderAggroBlocks(Objects.requireNonNull(event.getPoseStack()), mob, followRange, cameraPos, level);
                    } else {
                        // SIMPLE CIRCLE mode
                        renderCircle(Objects.requireNonNull(event.getPoseStack()), mob, followRange, cameraPos, ModConfig.followRangeColor);
                    }
                }

                // ... (red part unchanged)

                // 2. YELLOW CIRCLE (Line) - Attack
                double attackReach = 0;

                // First check if we have a custom value set
                if (mob.getAttribute(Objects.requireNonNull(Attributes.ENTITY_INTERACTION_RANGE)) != null) {
                    attackReach = mob.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
                }

                // If it's still 0 (or almost), use vanilla fallback formula
                if (attackReach <= 0.1) {
                    attackReach = mob.getBbWidth() * 2.0 + 1.0;
                }

                if (attackReach > 0) {
                    // Use fixed yellow or another color if desired
                    renderCircle(Objects.requireNonNull(event.getPoseStack()), mob, attackReach, cameraPos, 0xFFFFFF00);
                }

                // 3. BODY PART HITBOXES DEBUG (HEAD, ARMS, BODY, LEGS)
                // Render colored body part hitboxes (only if enabled AND OBB system is disabled)
                // When OBB is enabled, RenderEvents.renderOBBHitboxes() handles the rendering instead
                if (ModConfig.showBodyPartBoxes && !isOBBSystemEnabled()) {
                    renderBodyPartHitboxes(Objects.requireNonNull(event.getPoseStack()), mob, cameraPos);
                }
            }
        }
    }

    // Draw the block grid (uses configured color)
    // PERFORMANCE: Limita range massima a 16 blocchi per evitare freeze
    private static void renderAggroBlocks(@Nonnull PoseStack poseStack, @Nonnull Mob mob, double range, @Nonnull Vec3 cameraPos, @Nonnull Level level) {
        // PERFORMANCE: Clamp range to prevent massive loops
        range = Math.min(range, 16.0);

        VertexConsumer builder = Objects.requireNonNull(Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(Objects.requireNonNull(RenderType.lines())));
        BlockPos mobPos = Objects.requireNonNull(mob.blockPosition());
        int r = (int) Math.ceil(range);
        double rangeSqr = range * range;

        // Extract ARGB components from configured color
        int color = ModConfig.followRangeColor;
        float alpha = ((color >> 24) & 0xFF) / 255f;
        if (alpha == 0) alpha = 1.0f; // Fix if no alpha
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x*x + z*z > rangeSqr) continue;
                for (int y = -1; y <= 1; y++) {
                    BlockPos targetPos = Objects.requireNonNull(mobPos.offset(x, y, z));
                    if (!level.getBlockState(targetPos).isAir()) {
                        drawBox(builder, matrix, targetPos, red, green, blue, 0.5f);
                    }
                }
            }
        }
        poseStack.popPose();
    }

    // Generic method for drawing circles (used for both view and attack)
    private static void renderCircle(@Nonnull PoseStack poseStack, @Nonnull Mob mob, double radius, @Nonnull Vec3 cameraPos, int color) {
        VertexConsumer builder = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(Objects.requireNonNull(RenderType.lines()));

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

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());
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

    private static void drawBox(@Nonnull VertexConsumer builder, @Nonnull Matrix4f matrix, @Nonnull BlockPos pos, float r, float g, float b, float a) {
        float x = pos.getX(); float y = pos.getY(); float z = pos.getZ();
        // Simplified cube drawing
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

    /**
     * Renders body part hitboxes (HEAD, ARMS, BODY, LEGS).
     *
     * MULTIPART ENTITY SUPPORT:
     * If the entity is multipart (e.g. EnderDragon) use its native hitboxes
     * instead of calculating generic overlapping hitboxes.
     */
    private static void renderBodyPartHitboxes(@Nonnull PoseStack poseStack, @Nonnull LivingEntity entity, @Nonnull Vec3 cameraPos) {
        VertexConsumer builder = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        // MULTIPART ENTITY CHECK: EnderDragon and other entities with native parts
        if (entity instanceof EnderDragon dragon) {
            renderDragonParts(builder, matrix, dragon);
            poseStack.popPose();
            return;
        }

        // GENERIC MULTIPART CHECK: Other entities with getParts()
        if (entity.isMultipartEntity() && entity.getParts() != null && entity.getParts().length > 0) {
            renderMultipartEntity(builder, matrix, entity);
            poseStack.popPose();
            return;
        }

        // STANDARD ENTITY: use generic body part calculation logic
        AABB mainBox = entity.getBoundingBox();
        Vec3 center = mainBox.getCenter();
        double width = mainBox.getXsize();
        double height = mainBox.getYsize();
        double depth = mainBox.getZsize();

        // ADAPTIVE MODE: Detect non-humanoid hitboxes (same logic as HitHelper)
        double aspectRatio = Math.max(width, depth) / height;
        boolean isHorizontalBody = aspectRatio > 2.0;
        boolean isTallBody = height > 3.0 && aspectRatio < 0.5;

        if (isHorizontalBody) {
            // Horizontal body (dragons, serpents) - front/middle/back zones
            double bodyLength = (width > depth) ? width : depth;
            double frontSize = bodyLength * 0.30;

            // HEAD (cyan) - Front 30%
            AABB headBox;
            if (width > depth) {
                headBox = new AABB(mainBox.maxX - frontSize, mainBox.minY, center.z - depth/2,
                                   mainBox.maxX, mainBox.maxY, center.z + depth/2);
            } else {
                headBox = new AABB(center.x - width/2, mainBox.minY, mainBox.maxZ - frontSize,
                                   center.x + width/2, mainBox.maxY, mainBox.maxZ);
            }
            drawAABB(builder, matrix, headBox, 0.0f, 1.0f, 1.0f, 0.6f); // Cyan

            // LEGS (red) - Back 30%
            AABB legsBox;
            if (width > depth) {
                legsBox = new AABB(mainBox.minX, mainBox.minY, center.z - depth/2,
                                   mainBox.minX + frontSize, mainBox.maxY, center.z + depth/2);
            } else {
                legsBox = new AABB(center.x - width/2, mainBox.minY, mainBox.minZ,
                                   center.x + width/2, mainBox.maxY, mainBox.minZ + frontSize);
            }
            drawAABB(builder, matrix, legsBox, 1.0f, 0.0f, 0.0f, 0.6f); // Red

            // BODY (green) - Middle 40% (everything else)
            // Not rendered separately, just implied

        } else if (isTallBody) {
            // Tall body (enderman, bosses) - tighter head detection (15%)
            double headHeight = height * 0.15;
            AABB headBox = new AABB(center.x - width/2, mainBox.maxY - headHeight, center.z - depth/2,
                                    center.x + width/2, mainBox.maxY, center.z + depth/2);
            drawAABB(builder, matrix, headBox, 0.0f, 1.0f, 1.0f, 0.6f); // Cyan

            // Upper body (35%)
            double upperBodyTop = mainBox.maxY - headHeight;
            double upperBodyHeight = height * 0.35;
            AABB upperBodyBox = new AABB(center.x - width/2, upperBodyTop - upperBodyHeight, center.z - depth/2,
                                         center.x + width/2, upperBodyTop, center.z + depth/2);
            drawAABB(builder, matrix, upperBodyBox, 0.0f, 1.0f, 0.0f, 0.6f); // Green

            // Lower body/arms (30%)
            double lowerBodyTop = upperBodyTop - upperBodyHeight;
            double lowerBodyHeight = height * 0.30;
            AABB lowerBodyBox = new AABB(center.x - width/2, lowerBodyTop - lowerBodyHeight, center.z - depth/2,
                                         center.x + width/2, lowerBodyTop, center.z + depth/2);
            drawAABB(builder, matrix, lowerBodyBox, 1.0f, 1.0f, 0.0f, 0.6f); // Yellow (ARMS)

            // Legs (20%)
            AABB legsBox = new AABB(center.x - width/2, mainBox.minY, center.z - depth/2,
                                    center.x + width/2, lowerBodyTop - lowerBodyHeight, center.z + depth/2);
            drawAABB(builder, matrix, legsBox, 1.0f, 0.0f, 0.0f, 0.6f); // Red

        } else {
            // Standard humanoid body (EXACT replica of HitHelper logic)

            // HEAD (TOP 25%) - Cyan
            double headHeight = height * 0.25;
            AABB headBox = new AABB(center.x - width/2, mainBox.maxY - headHeight, center.z - depth/2,
                                    center.x + width/2, mainBox.maxY, center.z + depth/2);
            drawAABB(builder, matrix, headBox, 0.0f, 1.0f, 1.0f, 0.6f); // Cyan

            // TORSO + ARMS (MIDDLE 40%)
            double torsoTop = mainBox.maxY - headHeight;
            double torsoHeight = height * 0.40;
            double torsoBottom = torsoTop - torsoHeight;
            double armWidth = width * 0.30;

            // LEFT ARM - Yellow
            AABB leftArmBox = new AABB(mainBox.minX, torsoBottom, center.z - depth/2,
                                       mainBox.minX + armWidth, torsoTop, center.z + depth/2);
            drawAABB(builder, matrix, leftArmBox, 1.0f, 1.0f, 0.0f, 0.6f); // Yellow

            // RIGHT ARM - Yellow
            AABB rightArmBox = new AABB(mainBox.maxX - armWidth, torsoBottom, center.z - depth/2,
                                        mainBox.maxX, torsoTop, center.z + depth/2);
            drawAABB(builder, matrix, rightArmBox, 1.0f, 1.0f, 0.0f, 0.6f); // Yellow

            // BODY (CENTER) - Green
            double bodyWidth = width - (2 * armWidth);
            AABB bodyBox = new AABB(center.x - bodyWidth/2, torsoBottom, center.z - depth/2,
                                    center.x + bodyWidth/2, torsoTop, center.z + depth/2);
            drawAABB(builder, matrix, bodyBox, 0.0f, 1.0f, 0.0f, 0.6f); // Green

            // LEGS (BOTTOM 35%) - Red
            AABB legsBox = new AABB(center.x - width/2, mainBox.minY, center.z - depth/2,
                                    center.x + width/2, torsoBottom, center.z + depth/2);
            drawAABB(builder, matrix, legsBox, 1.0f, 0.0f, 0.0f, 0.6f); // Red
        }

        poseStack.popPose();
    }

    /**
     * Draws an AABB with colored lines (12 box edges)
     */
    private static void drawAABB(@Nonnull VertexConsumer builder, @Nonnull Matrix4f matrix, @Nonnull AABB box, float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // Bottom face (4 edges)
        builder.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);

        // Top face (4 edges)
        builder.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);

        // Vertical edges (4 edges)
        builder.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        builder.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
    }

    /**
     * Renders the native parts of the EnderDragon with specific colors per part.
     * The EnderDragon has 8 parts: head, neck (x3), body, tail (x3), wing (x2)
     */
    private static void renderDragonParts(@Nonnull VertexConsumer builder, @Nonnull Matrix4f matrix, @Nonnull EnderDragon dragon) {
        EnderDragonPart[] parts = dragon.getSubEntities();
        if (parts == null) return;

        for (EnderDragonPart part : parts) {
            AABB box = part.getBoundingBox();
            String name = part.name != null ? part.name.toLowerCase() : "";

            // Color-code based on part name
            float r, g, b;
            if (name.contains("head")) {
                // HEAD - Cyan (critical hit zone)
                r = 0.0f; g = 1.0f; b = 1.0f;
            } else if (name.contains("neck")) {
                // NECK - Yellow (arms equivalent)
                r = 1.0f; g = 1.0f; b = 0.0f;
            } else if (name.contains("body")) {
                // BODY - Green
                r = 0.0f; g = 1.0f; b = 0.0f;
            } else if (name.contains("tail")) {
                // TAIL - Red (legs equivalent)
                r = 1.0f; g = 0.0f; b = 0.0f;
            } else if (name.contains("wing")) {
                // WING - Magenta
                r = 1.0f; g = 0.0f; b = 1.0f;
            } else {
                // Unknown - White
                r = 1.0f; g = 1.0f; b = 1.0f;
            }

            drawAABB(builder, matrix, box, r, g, b, 0.8f);
        }
    }

    /**
     * Renders the native parts of any generic multipart entity.
     * Uses different colors for each part based on index.
     */
    private static void renderMultipartEntity(@Nonnull VertexConsumer builder, @Nonnull Matrix4f matrix, @Nonnull LivingEntity entity) {
        net.minecraft.world.entity.Entity[] parts = entity.getParts();
        if (parts == null) return;

        // Color palette for different parts
        float[][] colors = {
            {0.0f, 1.0f, 1.0f},  // Cyan
            {1.0f, 1.0f, 0.0f},  // Yellow
            {0.0f, 1.0f, 0.0f},  // Green
            {1.0f, 0.0f, 0.0f},  // Red
            {1.0f, 0.0f, 1.0f},  // Magenta
            {0.0f, 0.5f, 1.0f},  // Blue
            {1.0f, 0.5f, 0.0f},  // Orange
            {0.5f, 1.0f, 0.5f},  // Light green
        };

        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null) continue;

            AABB box = parts[i].getBoundingBox();
            float[] color = colors[i % colors.length];
            drawAABB(builder, matrix, box, color[0], color[1], color[2], 0.8f);
        }
    }

    /**
     * Renders 3D aggro spheres for NEARBY mobs when debug overlay is active.
     * Direct rendering EVERY FRAME for smooth movement - NO BATCHING, NO CLEAR.
     *
     * PERFORMANCE: Limits to mobs within 48 blocks to avoid freezing
     *
     * DISTANCE-BASED ALPHA FADE:
     * - Spheres near the camera become more transparent to reduce the "opaque wall" effect
     * - Quadratic alpha fade: closer = more transparent
     * - This solves the overlap problem when inside multiple spheres
     */
    private static void renderDebugSpheres(PoseStack poseStack, Vec3 cameraPos, net.minecraft.client.multiplayer.ClientLevel level) {
        var bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        final double MAX_RENDER_DIST_SQ = 48.0 * 48.0; // Distance limit

        // Iterate over NEARBY mobs for performance
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof Mob mob)) continue;

            // Skip distant mobs
            if (mob.distanceToSqr(mc.player) > MAX_RENDER_DIST_SQ) continue;

            // Get the FOLLOW_RANGE
            var rangeAttr = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (rangeAttr == null) continue;

            double aggroRange = rangeAttr.getValue();
            if (aggroRange <= 0) continue;

            // Sphere center (mob center) in ABSOLUTE coordinates
            Vec3 mobAbsPos = mob.position().add(0, mob.getBbHeight() / 2.0, 0);

            // Convert to coordinates RELATIVE to camera
            Vec3 mobRelativePos = mobAbsPos.subtract(cameraPos);

            // DISTANCE-BASED ALPHA FADE to reduce overlap
            // Calculate distance from camera to sphere center
            double distanceToCamera = cameraPos.distanceTo(mobAbsPos);

            // Calculate fade factor based on distance relative to sphere radius
            // - If camera is at sphere center: distanceToCamera = 0 → fade = 0 → alpha = 0 (invisible)
            // - If camera is on surface: distanceToCamera = aggroRange → fade = 1 → alpha = max
            // - If camera is far: distanceToCamera > aggroRange → fade = 1 → alpha = max
            double fadeDistance = Math.min(distanceToCamera / aggroRange, 1.0);

            // Quadratic fade for smooth transition (x^3 to make nearby spheres even more transparent)
            double fadeFactor = fadeDistance * fadeDistance * fadeDistance;

            // Base alpha 0.35 (reduced from 0.5) + dynamic fade
            // When INSIDE the sphere (distanceToCamera < aggroRange) → very low alpha
            // When OUTSIDE (distanceToCamera >= aggroRange) → normal alpha
            float baseAlpha = 0.35f;
            float alpha = baseAlpha * (float) fadeFactor;

            // Minimum alpha 0.05 to maintain some visibility even when inside
            alpha = Math.max(alpha, 0.05f);

            // Semi-transparent cyan color
            float red = 0.0f;
            float green = 1.0f;
            float blue = 1.0f;

            // Apply PoseStack transformation for correct positioning
            poseStack.pushPose();
            poseStack.translate(mobRelativePos.x, mobRelativePos.y, mobRelativePos.z);

            // Render sphere with center at (0,0,0) - transformation already applied
            com.frenkvs.devmod.rendering.SphereRenderer.renderSphereFilled(
                poseStack, bufferSource, Vec3.ZERO, aggroRange, red, green, blue, alpha
            );

            poseStack.popPose();
        }
    }

    /**
     * Checks if the OBB hitbox system is enabled in config.
     * Safe method that won't throw if config is not yet loaded.
     */
    private static boolean isOBBSystemEnabled() {
        try {
            return Config.OBB_HITBOX_ENABLED.get();
        } catch (Exception e) {
            return false;
        }
    }
}
