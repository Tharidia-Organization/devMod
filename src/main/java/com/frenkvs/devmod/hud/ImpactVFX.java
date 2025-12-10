package com.frenkvs.devmod.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages 3D impact visual effects:
 * - Energy Vortex Core (energy spiral at center)
 * - Slash Animation (arc following the hit)
 * - Connection Lines (lines from core to HUD panel)
 */
public class ImpactVFX {

    // Active effects list (thread-safe to avoid ConcurrentModificationException)
    private static final List<ImpactEffect> activeEffects = new CopyOnWriteArrayList<>();

    // Effect duration
    private static final long CORE_DURATION_MS = 2500;
    private static final long SLASH_DURATION_MS = 600;  // Longer to see the animation
    private static final long LINE_DURATION_MS = 2000;

    // Colors - Electric Blue theme
    private static final int COLOR_CORE_PRIMARY = 0xFF3D5AFE;     // Electric blue
    private static final int COLOR_CORE_SECONDARY = 0xFF00E5FF;   // Cyan
    private static final int COLOR_CORE_GLOW = 0xFF82B1FF;        // Light blue
    private static final int COLOR_SLASH = 0xFF3D5AFE;            // Electric blue
    private static final int COLOR_LINE = 0xFF00E5FF;             // Cyan

    /**
     * Adds a new impact effect.
     * Spawns both VFX effects (vortex, slash, lines) and the 3D panel.
     */
    public static void addImpact(Vec3 hitPoint, Vec3 slashDirection, ImpactData data) {
        // Remove old effects if there are too many
        while (activeEffects.size() > 5) {
            activeEffects.remove(0);
        }

        activeEffects.add(new ImpactEffect(hitPoint, slashDirection, data));

        // Also spawn the 3D panel
        Impact3DPanelManager.INSTANCE.spawnPanelFromImpact(data);
    }

    /**
     * Renders all active effects.
     * Called from RenderEvents during AFTER_ENTITIES.
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        if (activeEffects.isEmpty()) return;

        // Remove expired effects
        long now = System.currentTimeMillis();
        activeEffects.removeIf(e -> e.isExpired(now));

        for (ImpactEffect effect : activeEffects) {
            renderEffect(poseStack, bufferSource, cameraPos, effect, now);
        }

        // Flush buffer per assicurare rendering
        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    private static void renderEffect(PoseStack poseStack, MultiBufferSource bufferSource,
                                     Vec3 cameraPos, ImpactEffect effect, long now) {
        float coreAlpha = effect.getCoreAlpha(now);

        // 1. Render Energy Vortex Core (spirale di energia)
        if (coreAlpha > 0.01f) {
            poseStack.pushPose();
            Vec3 rel = effect.hitPoint.subtract(cameraPos);
            poseStack.translate(rel.x, rel.y, rel.z);

            renderEnergyVortex(poseStack, bufferSource, coreAlpha, effect.getRotation(now), effect.getPulseScale(now));

            poseStack.popPose();
        }

        // 2. Render Slash Animation (arco che segue il colpo)
        float slashProgress = effect.getSlashProgress(now);
        if (slashProgress >= 0 && slashProgress <= 1.0f) {
            poseStack.pushPose();
            Vec3 rel = effect.hitPoint.subtract(cameraPos);
            poseStack.translate(rel.x, rel.y, rel.z);

            renderSlashTrail(poseStack, bufferSource, effect.slashDirection, slashProgress);

            poseStack.popPose();
        }

        // 3. Render Connection Lines (from core towards screen/HUD)
        float lineAlpha = effect.getLineAlpha(now);
        if (lineAlpha > 0.05f) {
            renderConnectionLines(poseStack, bufferSource, cameraPos, effect.hitPoint, lineAlpha, effect.getRotation(now));
        }
    }

    /**
     * Renders the central energy vortex (rotating spiral).
     */
    private static void renderEnergyVortex(PoseStack poseStack, MultiBufferSource bufferSource,
                                            float alpha, float rotation, float pulseScale) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugLineStrip(2.5));

        // Colors
        float r1 = ((COLOR_CORE_PRIMARY >> 16) & 0xFF) / 255.0f;
        float g1 = ((COLOR_CORE_PRIMARY >> 8) & 0xFF) / 255.0f;
        float b1 = (COLOR_CORE_PRIMARY & 0xFF) / 255.0f;

        float r2 = ((COLOR_CORE_SECONDARY >> 16) & 0xFF) / 255.0f;
        float g2 = ((COLOR_CORE_SECONDARY >> 8) & 0xFF) / 255.0f;
        float b2 = (COLOR_CORE_SECONDARY & 0xFF) / 255.0f;

        float r3 = ((COLOR_CORE_GLOW >> 16) & 0xFF) / 255.0f;
        float g3 = ((COLOR_CORE_GLOW >> 8) & 0xFF) / 255.0f;
        float b3 = (COLOR_CORE_GLOW & 0xFF) / 255.0f;

        // === OUTER SPIRAL (clockwise rotation) ===
        float baseRadius = 0.25f * pulseScale;
        int spiralSegments = 32;
        float spiralRotations = 2.0f; // Spiral turns

        for (int i = 0; i <= spiralSegments; i++) {
            float t = (float) i / spiralSegments;
            float angle = rotation + t * spiralRotations * (float) Math.PI * 2;
            float radius = baseRadius * (1.0f - t * 0.6f); // Narrows towards center

            float x = (float) Math.cos(angle) * radius;
            float y = (float) Math.sin(angle) * radius * 0.5f; // Vertically flattened
            float z = (float) Math.sin(angle) * radius;

            // Color fading from primary to secondary
            float colorMix = t;
            float r = r1 * (1 - colorMix) + r2 * colorMix;
            float g = g1 * (1 - colorMix) + g2 * colorMix;
            float b = b1 * (1 - colorMix) + b2 * colorMix;

            consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, alpha * (1.0f - t * 0.5f))
                .setNormal(0, 1, 0);
        }

        // === INNER SPIRAL (counter-clockwise rotation) ===
        for (int i = 0; i <= spiralSegments; i++) {
            float t = (float) i / spiralSegments;
            float angle = -rotation * 1.5f + t * spiralRotations * (float) Math.PI * 2;
            float radius = baseRadius * 0.6f * (1.0f - t * 0.5f);

            float x = (float) Math.cos(angle) * radius;
            float y = (float) Math.sin(angle) * radius * 0.3f;
            float z = (float) Math.sin(angle) * radius;

            consumer.addVertex(matrix, x, y, z)
                .setColor(r2, g2, b2, alpha * 0.7f * (1.0f - t * 0.3f))
                .setNormal(0, 1, 0);
        }

        // === CONCENTRIC RINGS ("wave" effect) ===
        int ringCount = 3;
        for (int ring = 0; ring < ringCount; ring++) {
            float ringRadius = baseRadius * (0.4f + ring * 0.3f);
            float ringAlpha = alpha * (1.0f - ring * 0.25f);
            int ringSegments = 24;

            // Rotation offset for each ring
            float ringRotation = rotation * (1.0f + ring * 0.3f);

            for (int i = 0; i <= ringSegments; i++) {
                float angle = ringRotation + (float) (2 * Math.PI * i / ringSegments);
                float x = (float) Math.cos(angle) * ringRadius;
                float z = (float) Math.sin(angle) * ringRadius;

                // Vertical wave
                float waveY = (float) Math.sin(angle * 3 + rotation * 2) * 0.03f;

                consumer.addVertex(matrix, x, waveY, z)
                    .setColor(r3, g3, b3, ringAlpha * 0.5f)
                    .setNormal(0, 1, 0);
            }
        }

        // === RAYS FROM CENTER ===
        int rayCount = 6;
        float rayLength = baseRadius * 1.2f;

        for (int i = 0; i < rayCount; i++) {
            float angle = rotation * 0.5f + (float) (2 * Math.PI * i / rayCount);

            // Central point
            consumer.addVertex(matrix, 0, 0, 0)
                .setColor(r2, g2, b2, alpha)
                .setNormal(0, 1, 0);

            // Outer point
            float x = (float) Math.cos(angle) * rayLength;
            float z = (float) Math.sin(angle) * rayLength;
            consumer.addVertex(matrix, x, 0, z)
                .setColor(r2, g2, b2, alpha * 0.2f)
                .setNormal(0, 1, 0);
        }

        // === BRIGHT CENTER POINT ===
        float dotSize = 0.02f * pulseScale;
        consumer.addVertex(matrix, -dotSize, 0, 0).setColor(1f, 1f, 1f, alpha).setNormal(1, 0, 0);
        consumer.addVertex(matrix, dotSize, 0, 0).setColor(1f, 1f, 1f, alpha).setNormal(1, 0, 0);
        consumer.addVertex(matrix, 0, -dotSize, 0).setColor(1f, 1f, 1f, alpha).setNormal(0, 1, 0);
        consumer.addVertex(matrix, 0, dotSize, 0).setColor(1f, 1f, 1f, alpha).setNormal(0, 1, 0);
        consumer.addVertex(matrix, 0, 0, -dotSize).setColor(1f, 1f, 1f, alpha).setNormal(0, 0, 1);
        consumer.addVertex(matrix, 0, 0, dotSize).setColor(1f, 1f, 1f, alpha).setNormal(0, 0, 1);
    }

    /**
     * Renders the slash simulating weapon cutting movement.
     * The animation shows a "blade" moving through the impact point.
     */
    private static void renderSlashTrail(PoseStack poseStack, MultiBufferSource bufferSource,
                                          Vec3 direction, float progress) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugLineStrip(5.0));

        float r = ((COLOR_SLASH >> 16) & 0xFF) / 255.0f;
        float g = ((COLOR_SLASH >> 8) & 0xFF) / 255.0f;
        float b = (COLOR_SLASH & 0xFF) / 255.0f;

        // Base alpha that decreases with progress
        float baseAlpha = Math.max(0, 1.0f - progress * 0.8f);

        // === SLASH LINE (line that "cuts" through the point) ===
        // Perpendicular direction to view (simulates lateral sword movement)
        float dirX = (float) direction.x;
        float dirZ = (float) direction.z;

        // Perpendicular vector for horizontal cut
        float perpX = -dirZ;
        float perpZ = dirX;
        float perpLen = (float) Math.sqrt(perpX * perpX + perpZ * perpZ);
        if (perpLen > 0.001f) {
            perpX /= perpLen;
            perpZ /= perpLen;
        } else {
            perpX = 1;
            perpZ = 0;
        }

        // Cut line dimensions
        float slashLength = 0.8f;  // Total cut length
        float slashHeight = 0.4f;  // Cut arc height

        // === ANIMATION: Blade moves from left to right ===
        // progress 0.0 -> blade on left
        // progress 0.5 -> blade at center (impact point)
        // progress 1.0 -> blade on right

        // Current position of the cut "head"
        float bladePos = (progress * 2.0f - 1.0f) * slashLength; // from -slashLength to +slashLength

        // === CUT TRAIL (remaining trail) ===
        int trailSegments = 24;
        float trailStart = -slashLength;
        float trailEnd = Math.min(bladePos, slashLength);

        if (trailEnd > trailStart) {
            for (int i = 0; i <= trailSegments; i++) {
                float t = (float) i / trailSegments;
                float pos = trailStart + (trailEnd - trailStart) * t;

                // Position along the cut line
                float x = perpX * pos;
                float z = perpZ * pos;

                // Vertical arc (the cut is curved, higher at center)
                float arcT = (pos + slashLength) / (2 * slashLength); // 0 to 1
                float y = (float) Math.sin(arcT * Math.PI) * slashHeight;

                // Alpha: stronger near blade, fades towards tail
                float distFromBlade = Math.abs(pos - bladePos);
                float trailAlpha = baseAlpha * Math.max(0, 1.0f - distFromBlade / slashLength);

                // Color fading from white (blade) to blue (trail)
                float colorFade = Math.min(1, distFromBlade / (slashLength * 0.3f));
                float cr = r + (1 - r) * (1 - colorFade);
                float cg = g + (1 - g) * (1 - colorFade);
                float cb = b + (1 - b) * (1 - colorFade);

                consumer.addVertex(matrix, x, y, z)
                    .setColor(cr, cg, cb, trailAlpha)
                    .setNormal(0, 1, 0);
            }
        }

        // === BLADE (brightest moving point) ===
        if (progress < 0.95f && Math.abs(bladePos) <= slashLength) {
            float bladeX = perpX * bladePos;
            float bladeZ = perpZ * bladePos;
            float arcT = (bladePos + slashLength) / (2 * slashLength);
            float bladeY = (float) Math.sin(arcT * Math.PI) * slashHeight;

            // Bright central point
            float bladeAlpha = baseAlpha * 1.2f;
            float bladeSize = 0.08f;

            // Luminous cross on blade
            consumer.addVertex(matrix, bladeX - bladeSize, bladeY, bladeZ)
                .setColor(1f, 1f, 1f, bladeAlpha).setNormal(1, 0, 0);
            consumer.addVertex(matrix, bladeX + bladeSize, bladeY, bladeZ)
                .setColor(1f, 1f, 1f, bladeAlpha).setNormal(1, 0, 0);
            consumer.addVertex(matrix, bladeX, bladeY - bladeSize, bladeZ)
                .setColor(1f, 1f, 1f, bladeAlpha).setNormal(0, 1, 0);
            consumer.addVertex(matrix, bladeX, bladeY + bladeSize, bladeZ)
                .setColor(1f, 1f, 1f, bladeAlpha).setNormal(0, 1, 0);

            // Vertical blade line ("cut" effect)
            float bladeLengthVert = 0.25f;
            consumer.addVertex(matrix, bladeX, bladeY - bladeLengthVert, bladeZ)
                .setColor(1f, 1f, 1f, bladeAlpha * 0.8f).setNormal(0, 1, 0);
            consumer.addVertex(matrix, bladeX, bladeY + bladeLengthVert, bladeZ)
                .setColor(1f, 1f, 1f, bladeAlpha * 0.8f).setNormal(0, 1, 0);
        }

        // === SPARK PARTICLES ===
        if (progress < 0.7f) {
            int sparkCount = 6;
            for (int i = 0; i < sparkCount; i++) {
                // Random position along the trail (based on index)
                float sparkT = (float) i / sparkCount;
                float sparkPos = trailStart + (trailEnd - trailStart) * sparkT;

                // Deterministic "random" offset
                float offsetX = (float) Math.sin(i * 7.3f + progress * 10) * 0.1f;
                float offsetY = (float) Math.cos(i * 5.1f + progress * 8) * 0.15f;
                float offsetZ = (float) Math.sin(i * 3.7f + progress * 12) * 0.1f;

                float arcT = (sparkPos + slashLength) / (2 * slashLength);
                float baseY = (float) Math.sin(arcT * Math.PI) * slashHeight;

                float x = perpX * sparkPos + offsetX;
                float y = baseY + offsetY;
                float z = perpZ * sparkPos + offsetZ;

                float sparkAlpha = baseAlpha * (1.0f - progress) * 0.9f;

                // Small bright point
                float size = 0.015f;
                consumer.addVertex(matrix, x - size, y, z).setColor(1f, 1f, 0.8f, sparkAlpha).setNormal(1, 0, 0);
                consumer.addVertex(matrix, x + size, y, z).setColor(1f, 1f, 0.8f, sparkAlpha).setNormal(1, 0, 0);
            }
        }

        // === OUTER ARC (cut edge) ===
        // Upper arc line
        int arcSegments = 16;
        float arcOffset = 0.05f; // Offset from center

        for (int i = 0; i <= arcSegments; i++) {
            float t = (float) i / arcSegments;
            float pos = -slashLength + 2 * slashLength * t;

            // Only the already "cut" part
            if (pos > trailEnd) break;

            float x = perpX * pos;
            float z = perpZ * pos;
            float arcT = (pos + slashLength) / (2 * slashLength);
            float y = (float) Math.sin(arcT * Math.PI) * (slashHeight + arcOffset);

            float arcAlpha = baseAlpha * 0.4f;

            consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, arcAlpha)
                .setNormal(0, 1, 0);
        }
    }

    /**
     * Renders connection lines from core towards HUD.
     */
    private static void renderConnectionLines(PoseStack poseStack, MultiBufferSource bufferSource,
                                               Vec3 cameraPos, Vec3 hitPoint, float alpha, float rotation) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        poseStack.pushPose();
        Vec3 rel = hitPoint.subtract(cameraPos);
        poseStack.translate(rel.x, rel.y, rel.z);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugLineStrip(2.0));

        float r = ((COLOR_LINE >> 16) & 0xFF) / 255.0f;
        float g = ((COLOR_LINE >> 8) & 0xFF) / 255.0f;
        float b = (COLOR_LINE & 0xFF) / 255.0f;

        // Direction towards camera
        Vec3 toCamera = cameraPos.subtract(hitPoint).normalize();

        // === MAIN LINES TOWARDS CAMERA ===
        float lineLength = 1.5f;
        int lineCount = 3;

        for (int i = 0; i < lineCount; i++) {
            // Angular offset for each line
            float angleOffset = (float) (2 * Math.PI * i / lineCount) + rotation * 0.2f;

            // Starting point (slightly offset from center)
            float startOffset = 0.1f;
            float startX = (float) Math.cos(angleOffset) * startOffset;
            float startZ = (float) Math.sin(angleOffset) * startOffset;

            // Line extends towards camera with slight curve
            int segments = 8;
            for (int j = 0; j <= segments; j++) {
                float t = (float) j / segments;

                // Interpolation towards camera
                float x = startX * (1 - t) + (float) toCamera.x * lineLength * t;
                float y = (float) toCamera.y * lineLength * t;
                float z = startZ * (1 - t) + (float) toCamera.z * lineLength * t;

                // Alpha decreasing towards end
                float segmentAlpha = alpha * (1.0f - t * 0.7f);

                // "Pulse" effect along the line
                float pulse = (float) Math.sin(t * Math.PI * 2 + rotation * 3) * 0.3f + 0.7f;
                segmentAlpha *= pulse;

                consumer.addVertex(matrix, x, y, z)
                    .setColor(r, g, b, segmentAlpha)
                    .setNormal(0, 1, 0);
            }
        }

        // === SIDE LINES ("scanner" effect) ===
        float sideLength = 0.8f;
        float sideOffset = 0.15f;

        // Right line
        consumer.addVertex(matrix, sideOffset, 0, 0)
            .setColor(r, g, b, alpha * 0.6f)
            .setNormal(1, 0, 0);
        consumer.addVertex(matrix, sideOffset + sideLength, 0.3f, 0)
            .setColor(r, g, b, alpha * 0.1f)
            .setNormal(1, 0, 0);

        // Left line
        consumer.addVertex(matrix, -sideOffset, 0, 0)
            .setColor(r, g, b, alpha * 0.6f)
            .setNormal(1, 0, 0);
        consumer.addVertex(matrix, -sideOffset - sideLength, 0.3f, 0)
            .setColor(r, g, b, alpha * 0.1f)
            .setNormal(1, 0, 0);

        // Top line
        consumer.addVertex(matrix, 0, sideOffset, 0)
            .setColor(r, g, b, alpha * 0.6f)
            .setNormal(0, 1, 0);
        consumer.addVertex(matrix, 0, sideOffset + sideLength, 0)
            .setColor(r, g, b, alpha * 0.1f)
            .setNormal(0, 1, 0);

        poseStack.popPose();
    }

    /**
     * Clears all effects.
     */
    public static void clear() {
        activeEffects.clear();
    }

    /**
     * Internal class for a single impact effect.
     */
    private static class ImpactEffect {
        final Vec3 hitPoint;
        final Vec3 slashDirection;
        final ImpactData data;
        final long startTime;

        ImpactEffect(Vec3 hitPoint, Vec3 slashDirection, ImpactData data) {
            this.hitPoint = hitPoint;
            this.slashDirection = slashDirection != null ? slashDirection : new Vec3(1, 0, 0);
            this.data = data;
            this.startTime = System.currentTimeMillis();
        }

        boolean isExpired(long now) {
            return now - startTime > CORE_DURATION_MS;
        }

        float getCoreAlpha(long now) {
            long elapsed = now - startTime;
            if (elapsed > CORE_DURATION_MS) return 0;

            // Fast fade in during first 100ms
            if (elapsed < 100) {
                return elapsed / 100.0f;
            }

            // Fade out during last 600ms
            long fadeStart = CORE_DURATION_MS - 600;
            if (elapsed > fadeStart) {
                return 1.0f - (elapsed - fadeStart) / 600.0f;
            }
            return 1.0f;
        }

        float getLineAlpha(long now) {
            long elapsed = now - startTime;
            if (elapsed > LINE_DURATION_MS) return 0;

            // Fade in
            if (elapsed < 150) {
                return elapsed / 150.0f * 0.8f;
            }

            // Fade out
            long fadeStart = LINE_DURATION_MS - 500;
            if (elapsed > fadeStart) {
                return 0.8f * (1.0f - (elapsed - fadeStart) / 500.0f);
            }
            return 0.8f;
        }

        float getSlashProgress(long now) {
            long elapsed = now - startTime;
            if (elapsed > SLASH_DURATION_MS) return -1; // Slash finished
            return (float) elapsed / SLASH_DURATION_MS;
        }

        float getRotation(long now) {
            long elapsed = now - startTime;
            // Continuous rotation (radians)
            return (elapsed * 0.003f) % ((float) Math.PI * 2);
        }

        float getPulseScale(long now) {
            long elapsed = now - startTime;
            // More pronounced sinusoidal pulse
            double pulse = Math.sin(elapsed * 0.008) * 0.15 + 1.0;
            return (float) pulse;
        }
    }
}
