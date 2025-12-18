package com.frenkvs.devmod.rendering;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * VOXEL-LAB Debug Overlay: Aggro Range Spheres
 *
 * Visualizes the aggro/follow range of mobs as translucent spheres.
 * Helps level designers understand mob awareness and engagement zones.
 *
 * Features:
 * - Follow range sphere (outer, blue)
 * - Attack range sphere (inner, red)
 * - Currently targeting indicator (line to target)
 * - Color coding by mob type (hostile/neutral/passive)
 *
 * Toggle: Part of Debug Overlay system (G key)
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class AggroRangeVisualizer {

    public static final AggroRangeVisualizer INSTANCE = new AggroRangeVisualizer();

    private boolean enabled = false;
    private static final int SPHERE_SEGMENTS = 16;

    // Get configurable render distance
    private static double getMaxRenderDistance() {
        return SettingsManager.INSTANCE.getSettings().visualizers.getRenderDistance();
    }

    // Colors (ARGB)
    private static final int COLOR_FOLLOW_HOSTILE = 0x40FF5555;   // Red, translucent
    private static final int COLOR_FOLLOW_NEUTRAL = 0x40FFFF55;   // Yellow, translucent
    private static final int COLOR_ATTACK_RANGE = 0x60FF0000;     // Deep red, slightly more opaque
    private static final int COLOR_PASSIVE = 0x4055FF55;          // Green, translucent

    private AggroRangeVisualizer() {}

    public void toggle() {
        enabled = !enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!INSTANCE.enabled || !DebugRenderer.INSTANCE.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // Get nearby mobs
        AABB searchBox = mc.player.getBoundingBox().inflate(getMaxRenderDistance());
        List<Entity> entities = mc.level.getEntities(mc.player, searchBox,
            e -> e instanceof Mob && e.isAlive());

        for (Entity entity : entities) {
            if (!(entity instanceof Mob mob)) continue;

            double dist = entity.distanceTo(mc.player);
            if (dist > getMaxRenderDistance()) continue;

            renderMobRanges(poseStack, bufferSource, mob, cameraPos);
        }

        bufferSource.endBatch();
    }

    private static void renderMobRanges(PoseStack poseStack, MultiBufferSource buffer,
                                         Mob mob, Vec3 cameraPos) {
        Vec3 mobPos = mob.position();
        double x = mobPos.x - cameraPos.x;
        double y = mobPos.y - cameraPos.y;
        double z = mobPos.z - cameraPos.z;

        poseStack.pushPose();
        poseStack.translate(x, y + mob.getBbHeight() / 2, z);

        // Get mob ranges from attributes
        double followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        double attackRange = getAttackRange(mob);

        // Determine color based on mob type
        int followColor;
        if (mob instanceof Monster) {
            followColor = COLOR_FOLLOW_HOSTILE;
        } else if (mob.getTarget() != null) {
            followColor = COLOR_FOLLOW_NEUTRAL;
        } else {
            followColor = COLOR_PASSIVE;
        }

        // Render follow range sphere
        renderSphere(poseStack, buffer, followRange, followColor);

        // Render attack range sphere (if mob is hostile or has target)
        if (mob instanceof Monster || mob.getTarget() != null) {
            renderSphere(poseStack, buffer, attackRange, COLOR_ATTACK_RANGE);
        }

        poseStack.popPose();

        // Render targeting line if mob has a target
        if (mob.getTarget() != null) {
            renderTargetingLine(poseStack, buffer, mob, mob.getTarget(), cameraPos);
        }
    }

    private static double getAttackRange(Mob mob) {
        // Most mobs have attack range of ~2 blocks for melee
        // Ranged mobs might have larger ranges
        try {
            double range = mob.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            return Math.max(2.0, range);
        } catch (Exception e) {
            return 2.0; // Default melee range
        }
    }

    private static void renderSphere(PoseStack poseStack, MultiBufferSource buffer,
                                      double radius, int color) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.debugLineStrip(1.0));
        Matrix4f matrix = poseStack.last().pose();

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        // Draw horizontal circles at different heights
        for (int h = -SPHERE_SEGMENTS / 2; h <= SPHERE_SEGMENTS / 2; h++) {
            double heightRatio = (double) h / (SPHERE_SEGMENTS / 2);
            double y = heightRatio * radius;
            double circleRadius = Math.sqrt(radius * radius - y * y);

            if (circleRadius > 0.1) {
                renderCircle(consumer, matrix, (float) y, (float) circleRadius, r, g, b, a);
            }
        }

        // Draw vertical circles (meridians)
        for (int m = 0; m < 4; m++) {
            double angle = m * Math.PI / 4;
            renderMeridian(consumer, matrix, (float) radius, (float) angle, r, g, b, a);
        }
    }

    private static void renderCircle(VertexConsumer consumer, Matrix4f matrix,
                                      float y, float radius, float r, float g, float b, float a) {
        int segments = SPHERE_SEGMENTS * 2;
        for (int i = 0; i <= segments; i++) {
            double angle = (2 * Math.PI * i) / segments;
            float x = (float) (Math.cos(angle) * radius);
            float z = (float) (Math.sin(angle) * radius);
            consumer.addVertex(matrix, x, y, z).setColor(r, g, b, a);
        }
    }

    private static void renderMeridian(VertexConsumer consumer, Matrix4f matrix,
                                        float radius, float angle, float r, float g, float b, float a) {
        float cosA = (float) Math.cos(angle);
        float sinA = (float) Math.sin(angle);

        int segments = SPHERE_SEGMENTS * 2;
        for (int i = 0; i <= segments; i++) {
            double vertAngle = (Math.PI * i) / segments - Math.PI / 2;
            float y = (float) (Math.sin(vertAngle) * radius);
            float horizRadius = (float) (Math.cos(vertAngle) * radius);
            float x = cosA * horizRadius;
            float z = sinA * horizRadius;
            consumer.addVertex(matrix, x, y, z).setColor(r, g, b, a);
        }
    }

    private static void renderTargetingLine(PoseStack poseStack, MultiBufferSource buffer,
                                             LivingEntity mob, LivingEntity target, Vec3 cameraPos) {
        Vec3 mobPos = mob.getEyePosition();
        Vec3 targetPos = target.getEyePosition();

        poseStack.pushPose();

        VertexConsumer consumer = buffer.getBuffer(RenderType.debugLineStrip(2.0));
        Matrix4f matrix = poseStack.last().pose();

        float r = 1.0f;
        float g = 0.0f;
        float b = 0.0f;
        float a = 0.8f;

        // Line from mob to target
        consumer.addVertex(matrix,
            (float)(mobPos.x - cameraPos.x),
            (float)(mobPos.y - cameraPos.y),
            (float)(mobPos.z - cameraPos.z))
            .setColor(r, g, b, a);

        consumer.addVertex(matrix,
            (float)(targetPos.x - cameraPos.x),
            (float)(targetPos.y - cameraPos.y),
            (float)(targetPos.z - cameraPos.z))
            .setColor(r, g, b, a);

        poseStack.popPose();
    }
}
