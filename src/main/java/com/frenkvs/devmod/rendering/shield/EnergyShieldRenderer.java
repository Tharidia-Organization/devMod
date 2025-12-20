package com.frenkvs.devmod.rendering.shield;

import com.frenkvs.devmod.rendering.TrigCache;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Energy Shield Renderer - GPU Shader-Based Visual System
 *
 * <p>Renders energy shield effects using custom GLSL shaders registered
 * via Minecraft's shader system for maximum compatibility.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>GPU-accelerated Simplex noise for energy field animation</li>
 *   <li>Fresnel edge glow computed in vertex shader</li>
 *   <li>Impact wave effect with expanding ripples</li>
 *   <li>Full integration with Minecraft's RenderType system</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)

public class EnergyShieldRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnergyShieldRenderer.class);

    // === Sphere Configuration ===
    private static final int SPHERE_SEGMENTS = 32;
    private static final int SPHERE_RINGS = 24;

    // === Default Values ===
    private static final int DEFAULT_COLOR = 0x3D5AFE; // Electric blue
    private static final float DEFAULT_OPACITY = 0.6f;
    private static final float DEFAULT_RADIUS = 1.2f;

    // === Impact State ===
    private static final List<ShieldImpact> activeImpacts = new ArrayList<>();
    private static final long IMPACT_DURATION_MS = 1500; // Ultra-thin technical ripple
    private static Vec3 currentImpactPoint = Vec3.ZERO;
    private static float currentImpactTime = 999.0f;
    private static float currentImpactIntensity = 0.0f; // Damage-based intensity (0-1)

    /**
     * Records a shield impact for visual feedback.
     */
    public static void recordImpact(Vec3 impactPoint, float damage) {
        activeImpacts.add(new ShieldImpact(impactPoint, damage, System.currentTimeMillis()));
        currentImpactPoint = impactPoint;
        currentImpactTime = 0.0f;

        while (activeImpacts.size() > 5) {
            activeImpacts.remove(0);
        }
        LOGGER.debug("[Shield] Impact recorded at {}", impactPoint);
    }

    /**
     * Triggers the shield shatter effect.
     */
    public static void triggerShatter(Vec3 center) {
        recordImpact(center, 20.0f);
    }

    /**
     * Resets the shatter state.
     */
    public static void resetShatter() {
        activeImpacts.clear();
        currentImpactTime = 999.0f;
    }

    /**
     * Main render method for the energy shield.
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource,
                              LivingEntity entity, int color, float opacity, float radius,
                              float partialTick, Vec3 cameraPos) {
        if (entity == null) return;

        long now = System.currentTimeMillis();

        // Update impact time and intensity from most recent impact
        if (!activeImpacts.isEmpty()) {
            ShieldImpact mostRecent = activeImpacts.get(activeImpacts.size() - 1);
            currentImpactTime = (now - mostRecent.time) / 1000.0f;
            currentImpactPoint = mostRecent.point;
            currentImpactIntensity = mostRecent.getIntensity();
        }

        // Clean old impacts
        activeImpacts.removeIf(impact -> now - impact.time > IMPACT_DURATION_MS);
        if (activeImpacts.isEmpty()) {
            currentImpactTime = 999.0f;
        }

        Vec3 entityPos = entity.position().add(0, entity.getBbHeight() * 0.5, 0);

        // Check if custom shader is available
        if (ShieldShaderRegistry.isUsingCustomShader()) {
            renderWithCustomShader(poseStack, bufferSource, entity, entityPos, cameraPos, color, opacity, radius);
        } else {
            renderFallback(poseStack, bufferSource, entityPos, cameraPos, color, opacity, radius);
        }
    }

    /**
     * Renders using the custom GPU shader.
     */
    private static void renderWithCustomShader(PoseStack poseStack, MultiBufferSource bufferSource,
                                                LivingEntity entity, Vec3 entityPos, Vec3 cameraPos,
                                                int color, float opacity, float radius) {
        RenderType shieldType = ShieldShaderRegistry.getShieldRenderType();
        if (shieldType == null) {
            renderFallback(poseStack, bufferSource, entityPos, cameraPos, color, opacity, radius);
            return;
        }

        // Calculate game time for shader
        float gameTime = 0;
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level != null) {
            gameTime = (level.getGameTime() +
                       mc.getTimer().getGameTimeDeltaPartialTick(true)) / 1200.0f;
        }

        // Extract color components
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        // Calculate impact point in local space
        Vec3 impactLocal = Objects.requireNonNull(Objects.requireNonNull(currentImpactPoint.subtract(entityPos)).scale(1.0 / radius));
        if (impactLocal.lengthSqr() > 0.001) {
            impactLocal = impactLocal.normalize();
        }

        // Set shader uniforms before rendering
        ShaderInstance shader = ShieldShaderRegistry.getShader();
        if (shader != null) {
            shader.safeGetUniform("GameTime").set(gameTime);
            shader.safeGetUniform("ShieldColor").set(r, g, b);
            shader.safeGetUniform("ShieldStrength").set(opacity);
            shader.safeGetUniform("ImpactTime").set(currentImpactTime);
            shader.safeGetUniform("ImpactPoint").set((float) impactLocal.x, (float) impactLocal.y, (float) impactLocal.z);
        }

        // Get buffer and render sphere
        VertexConsumer consumer = bufferSource.getBuffer(shieldType);

        poseStack.pushPose();
        Vec3 rel = Objects.requireNonNull(entityPos.subtract(cameraPos));
        poseStack.translate(rel.x, rel.y, rel.z);

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());
        Vec3 viewDir = Objects.requireNonNull(Objects.requireNonNull(cameraPos.subtract(entityPos)).normalize());

        // Render the sphere with normals for shader
        renderSphereWithNormals(consumer, poseStack, matrix, r, g, b, opacity, radius, viewDir);

        // Render hexagonal grid lines overlay
        renderHexGrid(poseStack, bufferSource, matrix, entityPos, cameraPos, color, opacity, radius);

        poseStack.popPose();
    }

    /**
     * Renders the hexagonal grid lines on top of the energy field.
     * Grid lines are DARKER than the energy field for contrast.
     */
    private static void renderHexGrid(PoseStack poseStack, MultiBufferSource bufferSource,
                                       Matrix4f matrix, Vec3 entityPos, Vec3 cameraPos,
                                       int color, float opacity, float radius) {
        // Get geodesic mesh for hex grid (use low detail for less dense grid)
        HexagonalShieldMesh mesh = HexagonalShieldMesh.lowDetail(radius);

        // Use vanilla lines RenderType for the grid
        VertexConsumer lineConsumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));

        // Calculate DARKER color for grid lines (contrast with bright energy field)
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        // Make lines darker - multiply by 0.3 for subtle dark grid
        r = r * 0.3f;
        g = g * 0.3f;
        b = b * 0.3f;

        // Grid line alpha (visible but not overwhelming)
        float gridAlpha = opacity * 0.6f;

        int gridColor = ((int)(gridAlpha * 255) << 24) |
                        ((int)(r * 255) << 16) |
                        ((int)(g * 255) << 8) |
                        (int)(b * 255);

        // Render lines with fresnel effect (edges more visible)
        mesh.renderLinesWithFresnel(lineConsumer, matrix, 0, 0, 0, gridColor, Objects.requireNonNull(cameraPos.subtract(entityPos)));
    }

    /**
     * Renders a sphere with position, color, and normals for the custom shader.
     */
    private static void renderSphereWithNormals(VertexConsumer consumer, PoseStack poseStack,
                                                 Matrix4f matrix, float r, float g, float b,
                                                 float alpha, float radius, Vec3 viewDir) {
        Matrix4f safeMatrix = Objects.requireNonNull(matrix);
        for (int ring = 0; ring < SPHERE_RINGS; ring++) {
            float theta1 = (float) (ring * Math.PI / SPHERE_RINGS);
            float theta2 = (float) ((ring + 1) * Math.PI / SPHERE_RINGS);

            for (int seg = 0; seg < SPHERE_SEGMENTS; seg++) {
                float phi1 = (float) (seg * 2 * Math.PI / SPHERE_SEGMENTS);
                float phi2 = (float) ((seg + 1) * 2 * Math.PI / SPHERE_SEGMENTS);

                Vector3f v1 = spherePoint(theta1, phi1, radius);
                Vector3f v2 = spherePoint(theta1, phi2, radius);
                Vector3f v3 = spherePoint(theta2, phi2, radius);
                Vector3f v4 = spherePoint(theta2, phi1, radius);

                // Normals (normalized position for unit sphere)
                Vector3f n1 = new Vector3f(v1).normalize();
                Vector3f n2 = new Vector3f(v2).normalize();
                Vector3f n3 = new Vector3f(v3).normalize();
                Vector3f n4 = new Vector3f(v4).normalize();

                int color = packColor(r, g, b, alpha);
                var pose = Objects.requireNonNull(poseStack.last());

                // Triangle 1
                consumer.addVertex(safeMatrix, v1.x, v1.y, v1.z).setColor(color).setNormal(pose, n1.x, n1.y, n1.z);
                consumer.addVertex(safeMatrix, v2.x, v2.y, v2.z).setColor(color).setNormal(pose, n2.x, n2.y, n2.z);
                consumer.addVertex(safeMatrix, v3.x, v3.y, v3.z).setColor(color).setNormal(pose, n3.x, n3.y, n3.z);

                // Triangle 2
                consumer.addVertex(safeMatrix, v1.x, v1.y, v1.z).setColor(color).setNormal(pose, n1.x, n1.y, n1.z);
                consumer.addVertex(safeMatrix, v3.x, v3.y, v3.z).setColor(color).setNormal(pose, n3.x, n3.y, n3.z);
                consumer.addVertex(safeMatrix, v4.x, v4.y, v4.z).setColor(color).setNormal(pose, n4.x, n4.y, n4.z);
            }
        }
    }

    /**
     * Fallback rendering using vanilla shaders when custom shader isn't available.
     */
    private static void renderFallback(PoseStack poseStack, MultiBufferSource bufferSource,
                                        Vec3 entityPos, Vec3 cameraPos,
                                        int color, float opacity, float radius) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        poseStack.pushPose();
        Vec3 rel = Objects.requireNonNull(entityPos.subtract(cameraPos));
        poseStack.translate(rel.x, rel.y, rel.z);

        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());
        Vec3 viewDir = Objects.requireNonNull(Objects.requireNonNull(cameraPos.subtract(entityPos)).normalize());
        float time = (System.currentTimeMillis() % 100000) / 1000.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, Objects.requireNonNull(DefaultVertexFormat.POSITION_COLOR));

        // Render sphere with Fresnel effect
        for (int ring = 0; ring < SPHERE_RINGS; ring++) {
            float theta1 = (float) (ring * Math.PI / SPHERE_RINGS);
            float theta2 = (float) ((ring + 1) * Math.PI / SPHERE_RINGS);

            for (int seg = 0; seg < SPHERE_SEGMENTS; seg++) {
                float phi1 = (float) (seg * 2 * Math.PI / SPHERE_SEGMENTS);
                float phi2 = (float) ((seg + 1) * 2 * Math.PI / SPHERE_SEGMENTS);

                Vector3f v1 = spherePoint(theta1, phi1, radius);
                Vector3f v2 = spherePoint(theta1, phi2, radius);
                Vector3f v3 = spherePoint(theta2, phi2, radius);
                Vector3f v4 = spherePoint(theta2, phi1, radius);

                float f1 = fresnel(v1, viewDir, radius);
                float f2 = fresnel(v2, viewDir, radius);
                float f3 = fresnel(v3, viewDir, radius);
                float f4 = fresnel(v4, viewDir, radius);

                float flow = 0.7f + 0.3f * TrigCache.sin(theta1 * 4 + time * 2);

                addVertex(buffer, matrix, v1, r, g, b, opacity * 0.3f * f1 * flow);
                addVertex(buffer, matrix, v2, r, g, b, opacity * 0.3f * f2 * flow);
                addVertex(buffer, matrix, v3, r, g, b, opacity * 0.3f * f3 * flow);

                addVertex(buffer, matrix, v1, r, g, b, opacity * 0.3f * f1 * flow);
                addVertex(buffer, matrix, v3, r, g, b, opacity * 0.3f * f3 * flow);
                addVertex(buffer, matrix, v4, r, g, b, opacity * 0.3f * f4 * flow);
            }
        }

        BufferUploader.drawWithShader(Objects.requireNonNull(buffer.buildOrThrow()));

        // Render impact ripples using lines
        if (currentImpactTime < 1.0f && !activeImpacts.isEmpty()) {
            renderImpactRipples(poseStack, bufferSource, matrix, entityPos, r, g, b, opacity, radius);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        poseStack.popPose();
    }

    /**
     * Renders impact ripple lines (fallback mode).
     */
    private static void renderImpactRipples(PoseStack poseStack, MultiBufferSource bufferSource,
                                             Matrix4f matrix, Vec3 entityPos,
                                             float r, float g, float b, float opacity, float radius) {
        VertexConsumer consumer = bufferSource.getBuffer(Objects.requireNonNull(RenderType.lines()));
        Matrix4f safeMatrix = Objects.requireNonNull(matrix);

        Vec3 impactDir = Objects.requireNonNull(currentImpactPoint.subtract(entityPos));
        if (impactDir.lengthSqr() < 0.001) return;
        impactDir = impactDir.normalize();

        // Scale wave alpha by impact intensity (stronger hits = brighter ripples)
        float waveAlpha = opacity * (1.0f - currentImpactTime) * (0.5f + 0.5f * currentImpactIntensity);
        float ir = Math.min(1.0f, r + 0.5f);
        float ig = Math.min(1.0f, g + 0.5f);
        float ib = Math.min(1.0f, b + 0.5f);

        Vector3f impactVec = new Vector3f((float) impactDir.x, (float) impactDir.y, (float) impactDir.z);
        Vector3f tangent1 = getTangent(impactVec);
        Vector3f tangent2 = new Vector3f();
        impactVec.cross(tangent1, tangent2).normalize();

        // Draw expanding ripple rings
        for (int ripple = 0; ripple < 3; ripple++) {
            float rippleAge = currentImpactTime - ripple * 0.1f;
            if (rippleAge < 0 || rippleAge > 1.0f) continue;

            float rippleAngle = rippleAge * (float) Math.PI;
            float rippleAlpha = waveAlpha * (1.0f - ripple * 0.3f);
            int color = packColor(ir, ig, ib, rippleAlpha);

            int ringSegments = 32;
            for (int i = 0; i < ringSegments; i++) {
                float angle1 = (float) (i * 2 * Math.PI / ringSegments);
                float angle2 = (float) ((i + 1) * 2 * Math.PI / ringSegments);

                Vector3f p1 = getPointOnSphereFromImpact(impactVec, tangent1, tangent2, rippleAngle, angle1, radius);
                Vector3f p2 = getPointOnSphereFromImpact(impactVec, tangent1, tangent2, rippleAngle, angle2, radius);
                var pose = Objects.requireNonNull(poseStack.last());

                consumer.addVertex(safeMatrix, p1.x, p1.y, p1.z).setColor(color).setNormal(pose, p1.x, p1.y, p1.z);
                consumer.addVertex(safeMatrix, p2.x, p2.y, p2.z).setColor(color).setNormal(pose, p2.x, p2.y, p2.z);
            }
        }
    }

    // === Helper Methods ===

    private static Vector3f spherePoint(float theta, float phi, float radius) {
        float x = radius * TrigCache.sin(theta) * TrigCache.cos(phi);
        float y = radius * TrigCache.cos(theta);
        float z = radius * TrigCache.sin(theta) * TrigCache.sin(phi);
        return new Vector3f(x, y, z);
    }

    private static float fresnel(Vector3f point, Vec3 viewDir, float radius) {
        float nx = point.x / radius;
        float ny = point.y / radius;
        float nz = point.z / radius;
        float dot = (float) Math.abs(nx * viewDir.x + ny * viewDir.y + nz * viewDir.z);
        return 0.3f + 0.7f * (1.0f - dot);
    }

    private static void addVertex(BufferBuilder buffer, Matrix4f matrix, Vector3f v, float r, float g, float b, float a) {
        int color = packColor(r, g, b, a);
        buffer.addVertex(Objects.requireNonNull(matrix), v.x, v.y, v.z).setColor(color);
    }

    private static int packColor(float r, float g, float b, float a) {
        return ((int) (Math.min(1.0f, a) * 255) << 24) |
               ((int) (Math.min(1.0f, r) * 255) << 16) |
               ((int) (Math.min(1.0f, g) * 255) << 8) |
               (int) (Math.min(1.0f, b) * 255);
    }

    private static Vector3f getTangent(Vector3f normal) {
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f tangent = new Vector3f();
        if (Math.abs(normal.dot(up)) < 0.99f) {
            normal.cross(up, tangent);
        } else {
            normal.cross(new Vector3f(1, 0, 0), tangent);
        }
        return tangent.normalize();
    }

    private static Vector3f getPointOnSphereFromImpact(Vector3f impactDir, Vector3f tangent1, Vector3f tangent2,
                                                        float arcAngle, float rotationAngle, float radius) {
        float cosArc = TrigCache.cos(arcAngle);
        float sinArc = TrigCache.sin(arcAngle);
        float cosRot = TrigCache.cos(rotationAngle);
        float sinRot = TrigCache.sin(rotationAngle);

        float x = impactDir.x * cosArc + (tangent1.x * cosRot + tangent2.x * sinRot) * sinArc;
        float y = impactDir.y * cosArc + (tangent1.y * cosRot + tangent2.y * sinRot) * sinArc;
        float z = impactDir.z * cosArc + (tangent1.z * cosRot + tangent2.z * sinRot) * sinArc;

        float len = (float) Math.sqrt(x * x + y * y + z * z);
        return new Vector3f(x / len * radius, y / len * radius, z / len * radius);
    }

    /**
     * Convenience method with default parameters.
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource,
                              LivingEntity entity, float partialTick, Vec3 cameraPos) {
        render(poseStack, bufferSource, entity, DEFAULT_COLOR, DEFAULT_OPACITY, DEFAULT_RADIUS, partialTick, cameraPos);
    }

    /**
     * Clears all active effects.
     */
    public static void clearEffects() {
        activeImpacts.clear();
        currentImpactTime = 999.0f;
    }

    /**
     * Internal class for tracking shield impacts.
     */
    private static class ShieldImpact {
        final Vec3 point;
        final float damage;
        final long time;

        ShieldImpact(Vec3 point, float damage, long time) {
            this.point = point;
            this.damage = damage;
            this.time = time;
        }

        /**
         * Returns a normalized intensity factor based on damage (clamped 0-1).
         * Useful for scaling visual effects based on impact strength.
         */
        float getIntensity() {
            return Math.min(1.0f, damage / 20.0f);
        }

        @Override
        public String toString() {
            return String.format("ShieldImpact[point=%s, damage=%.1f, age=%dms]",
                point, damage, System.currentTimeMillis() - time);
        }
    }
}
