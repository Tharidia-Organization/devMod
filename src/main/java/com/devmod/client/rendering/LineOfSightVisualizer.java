package com.devmod.client.rendering;

import com.devmod.ui.unified.persistence.SettingsManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Line of Sight Visualizer - Shows what mobs can see from their perspective
 *
 * Features:
 * - View cone (FOV) visualization showing mob's field of view
 * - LoS rays to player (green = visible, red = blocked)
 * - Blocking obstacles highlighted
 * - Shows potential targets in mob's view cone
 *
 * Activation: Toggle in VoxelLab Dashboard or keybind
 */

public class LineOfSightVisualizer {
    public static final LineOfSightVisualizer INSTANCE = new LineOfSightVisualizer();

    private boolean enabled = false;
    private static final double MOB_VIEW_DISTANCE = 16.0; // How far the view cone extends

    // Get configurable render distance
    private double getMaxRenderDistance() {
        return SettingsManager.INSTANCE.getSettings().visualizers.getRenderDistance();
    }
    private static final float MOB_FOV = 70.0f; // Degrees - typical mob FOV

    private LineOfSightVisualizer() {}

    public void toggle() {
        enabled = !enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Render line of sight visualization for nearby mobs
     */
    public void render(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, @Nonnull Vec3 cameraPos) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        VertexConsumer lineConsumer = buffer.getBuffer(Objects.requireNonNull(RenderType.lines()));

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());
        var pose = Objects.requireNonNull(poseStack.last());

        var player = Objects.requireNonNull(mc.player);
        var level = Objects.requireNonNull(mc.level);
        Vec3 playerPos = Objects.requireNonNull(player.getEyePosition());

        // PERFORMANCE FIX: Use AABB query instead of iterating all entities
        AABB searchBox = Objects.requireNonNull(player.getBoundingBox().inflate(getMaxRenderDistance()));
        VertexConsumer safeConsumer = Objects.requireNonNull(lineConsumer);
        for (Entity entity : level.getEntities(player, searchBox)) {
            if (!(entity instanceof Mob mob)) continue;

            // Render view cone
            renderViewCone(safeConsumer, matrix, pose, mob);

            // Render LoS to player
            renderLoSToPlayer(safeConsumer, matrix, pose, mob, playerPos, mc);

            // Render obstacles in view
            renderBlockingObstacles(safeConsumer, matrix, pose, mob, mc);
        }

        poseStack.popPose();
    }

    /**
     * Render the mob's field of view as a cone
     */
    private void renderViewCone(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                @Nonnull PoseStack.Pose pose, @Nonnull Mob mob) {
        Vec3 eyePos = mob.getEyePosition();
        Vec3 lookVec = mob.getViewVector(1.0f);

        // Calculate cone parameters
        double coneLength = MOB_VIEW_DISTANCE;
        double coneRadius = Math.tan(Math.toRadians(MOB_FOV / 2)) * coneLength;

        // Get perpendicular vectors for cone base
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = Objects.requireNonNull(lookVec.cross(up).normalize());
        if (right.lengthSqr() < 0.001) {
            right = new Vec3(1, 0, 0);
        }
        Vec3 actualUp = Objects.requireNonNull(right.cross(lookVec).normalize());

        // Cone tip is at eye position
        float tipX = (float) eyePos.x;
        float tipY = (float) eyePos.y;
        float tipZ = (float) eyePos.z;

        // Cone base center
        Vec3 baseCenter = Objects.requireNonNull(eyePos.add(Objects.requireNonNull(lookVec.scale(coneLength))));

        // Draw cone outline (8 segments)
        int segments = 8;
        float[][] basePoints = new float[segments][3];

        for (int i = 0; i < segments; i++) {
            double angle = (2 * Math.PI * i) / segments;
            Vec3 offset = Objects.requireNonNull(Objects.requireNonNull(right.scale(Math.cos(angle) * coneRadius))
                    .add(Objects.requireNonNull(actualUp.scale(Math.sin(angle) * coneRadius))));
            Vec3 point = Objects.requireNonNull(baseCenter.add(offset));
            basePoints[i][0] = (float) point.x;
            basePoints[i][1] = (float) point.y;
            basePoints[i][2] = (float) point.z;
        }

        // Draw lines from tip to base points (cone edges)
        float r = 0.3f, g = 0.7f, b = 1.0f, a = 0.4f; // Light blue for view cone
        for (int i = 0; i < segments; i++) {
            line(consumer, matrix, pose, tipX, tipY, tipZ,
                    basePoints[i][0], basePoints[i][1], basePoints[i][2], r, g, b, a);
        }

        // Draw base circle
        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;
            line(consumer, matrix, pose,
                    basePoints[i][0], basePoints[i][1], basePoints[i][2],
                    basePoints[next][0], basePoints[next][1], basePoints[next][2],
                    r, g, b, a);
        }

        // Draw center look direction line (stronger color)
        Vec3 lookEnd = Objects.requireNonNull(eyePos.add(Objects.requireNonNull(lookVec.scale(coneLength))));
        line(consumer, matrix, pose, tipX, tipY, tipZ,
                (float) lookEnd.x, (float) lookEnd.y, (float) lookEnd.z,
                0.0f, 1.0f, 1.0f, 0.8f); // Cyan center line
    }

    /**
     * Render line of sight from mob to player
     */
    private void renderLoSToPlayer(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                   @Nonnull PoseStack.Pose pose, @Nonnull Mob mob,
                                   @Nonnull Vec3 playerEye, @Nonnull Minecraft mc) {
        Vec3 mobEye = Objects.requireNonNull(mob.getEyePosition());

        // Check if player is within mob's FOV
        Vec3 lookVec = mob.getViewVector(1.0f);
        Vec3 toPlayerRaw = Objects.requireNonNull(playerEye.subtract(mobEye));
        Vec3 toPlayer = Objects.requireNonNull(toPlayerRaw.normalize());
        double dotProduct = lookVec.dot(toPlayer);
        double angleToPlayer = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dotProduct))));

        boolean inFOV = angleToPlayer <= MOB_FOV / 2;

        // Ray cast from mob to player
        ClipContext context = new ClipContext(
                mobEye,
                playerEye,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mob
        );

        HitResult hit = Objects.requireNonNull(mc.level).clip(context);
        boolean hasLoS = hit.getType() == HitResult.Type.MISS;

        if (hasLoS && inFOV) {
            // Clear LoS and in FOV - bright green
            line(consumer, matrix, pose,
                    (float) mobEye.x, (float) mobEye.y, (float) mobEye.z,
                    (float) playerEye.x, (float) playerEye.y, (float) playerEye.z,
                    0.0f, 1.0f, 0.0f, 0.8f);

            // Add label
            Vec3 midPoint = Objects.requireNonNull(Objects.requireNonNull(mobEye.add(playerEye)).scale(0.5));
            DebugRenderer.INSTANCE.addLabel(midPoint, "VISIBLE", 0xFF00FF00, 50);

        } else if (hasLoS && !inFOV) {
            // Has LoS but outside FOV - yellow (could see if turned)
            line(consumer, matrix, pose,
                    (float) mobEye.x, (float) mobEye.y, (float) mobEye.z,
                    (float) playerEye.x, (float) playerEye.y, (float) playerEye.z,
                    1.0f, 1.0f, 0.0f, 0.5f);

            DebugRenderer.INSTANCE.addLabel(Objects.requireNonNull(mobEye.add(Objects.requireNonNull(toPlayer.scale(3)))), "OUT OF FOV", 0xFFFFFF00, 50);

        } else if (hit.getType() == HitResult.Type.BLOCK) {
            // Blocked
            BlockHitResult blockHit = (BlockHitResult) hit;
            Vec3 hitPos = hit.getLocation();

            // Yellow line to block point
            line(consumer, matrix, pose,
                    (float) mobEye.x, (float) mobEye.y, (float) mobEye.z,
                    (float) hitPos.x, (float) hitPos.y, (float) hitPos.z,
                    1.0f, 0.6f, 0.0f, 0.7f);

            // Red dashed line from block to player
            line(consumer, matrix, pose,
                    (float) hitPos.x, (float) hitPos.y, (float) hitPos.z,
                    (float) playerEye.x, (float) playerEye.y, (float) playerEye.z,
                    1.0f, 0.0f, 0.0f, 0.3f);

            // Highlight blocking block
            renderBlockHighlight(consumer, matrix, pose, blockHit);

            DebugRenderer.INSTANCE.addLabel(hitPos, "BLOCKED", 0xFFFF4444, 50);
        }
    }

    /**
     * Render obstacles that block the mob's view
     */
    private void renderBlockingObstacles(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                         @Nonnull PoseStack.Pose pose, @Nonnull Mob mob,
                                         @Nonnull Minecraft mc) {
        Vec3 eyePos = Objects.requireNonNull(mob.getEyePosition());
        Vec3 lookVec = mob.getViewVector(1.0f);

        // Cast rays in a grid pattern within the view cone
        int rayCount = 5; // 5x5 grid of rays
        double halfFOV = Math.toRadians(MOB_FOV / 2);

        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = Objects.requireNonNull(lookVec.cross(up).normalize());
        if (right.lengthSqr() < 0.001) {
            right = new Vec3(1, 0, 0);
        }
        Vec3 actualUp = Objects.requireNonNull(right.cross(lookVec).normalize());

        List<BlockPos> highlightedBlocks = new ArrayList<>();

        for (int i = -rayCount / 2; i <= rayCount / 2; i++) {
            for (int j = -rayCount / 2; j <= rayCount / 2; j++) {
                // Calculate ray direction
                double yawOffset = (halfFOV * i) / (rayCount / 2.0);
                double pitchOffset = (halfFOV * j) / (rayCount / 2.0);

                Vec3 rayDir = Objects.requireNonNull(Objects.requireNonNull(Objects.requireNonNull(lookVec
                        .add(Objects.requireNonNull(right.scale(Math.sin(yawOffset)))))
                        .add(Objects.requireNonNull(actualUp.scale(Math.sin(pitchOffset)))))
                        .normalize());

                Vec3 rayEnd = Objects.requireNonNull(eyePos.add(Objects.requireNonNull(rayDir.scale(MOB_VIEW_DISTANCE))));

                ClipContext context = new ClipContext(
                        eyePos, rayEnd,
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        mob
                );

                HitResult hit = Objects.requireNonNull(mc.level).clip(context);

                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) hit;
                    BlockPos pos = blockHit.getBlockPos();

                    // Only highlight each block once
                    if (!highlightedBlocks.contains(pos)) {
                        highlightedBlocks.add(pos);

                        // Small dot at hit point
                        Vec3 hitLoc = hit.getLocation();
                        float size = 0.05f;
                        line(consumer, matrix, pose,
                                (float) (hitLoc.x - size), (float) hitLoc.y, (float) hitLoc.z,
                                (float) (hitLoc.x + size), (float) hitLoc.y, (float) hitLoc.z,
                                1.0f, 0.5f, 0.0f, 0.6f);
                        line(consumer, matrix, pose,
                                (float) hitLoc.x, (float) (hitLoc.y - size), (float) hitLoc.z,
                                (float) hitLoc.x, (float) (hitLoc.y + size), (float) hitLoc.z,
                                1.0f, 0.5f, 0.0f, 0.6f);
                    }
                }
            }
        }
    }

    private void renderBlockHighlight(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                      @Nonnull PoseStack.Pose pose, @Nonnull BlockHitResult hit) {
        float x = hit.getBlockPos().getX();
        float y = hit.getBlockPos().getY();
        float z = hit.getBlockPos().getZ();

        float r = 1.0f, g = 0.3f, b = 0.0f, a = 0.7f;

        // Wireframe cube
        // Bottom
        line(consumer, matrix, pose, x, y, z, x + 1, y, z, r, g, b, a);
        line(consumer, matrix, pose, x + 1, y, z, x + 1, y, z + 1, r, g, b, a);
        line(consumer, matrix, pose, x + 1, y, z + 1, x, y, z + 1, r, g, b, a);
        line(consumer, matrix, pose, x, y, z + 1, x, y, z, r, g, b, a);

        // Top
        line(consumer, matrix, pose, x, y + 1, z, x + 1, y + 1, z, r, g, b, a);
        line(consumer, matrix, pose, x + 1, y + 1, z, x + 1, y + 1, z + 1, r, g, b, a);
        line(consumer, matrix, pose, x + 1, y + 1, z + 1, x, y + 1, z + 1, r, g, b, a);
        line(consumer, matrix, pose, x, y + 1, z + 1, x, y + 1, z, r, g, b, a);

        // Verticals
        line(consumer, matrix, pose, x, y, z, x, y + 1, z, r, g, b, a);
        line(consumer, matrix, pose, x + 1, y, z, x + 1, y + 1, z, r, g, b, a);
        line(consumer, matrix, pose, x + 1, y, z + 1, x + 1, y + 1, z + 1, r, g, b, a);
        line(consumer, matrix, pose, x, y, z + 1, x, y + 1, z + 1, r, g, b, a);
    }

    private void line(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix, @Nonnull PoseStack.Pose pose,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float r, float g, float b, float a) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
    }
}
