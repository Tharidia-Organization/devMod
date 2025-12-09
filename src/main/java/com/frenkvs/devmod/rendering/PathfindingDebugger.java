package com.frenkvs.devmod.rendering;

import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pathfinding Debugger - Visualizes mob navigation paths in real-time
 *
 * ╔═══════════════════════════════════════════════════════════════════╗
 * ║  🚀 SPECTACULAR PATHFINDING VISUALIZATION 🚀                      ║
 * ╠═══════════════════════════════════════════════════════════════════╣
 * ║  START MARKER (Mob Position):                                     ║
 * ║  - Animated rotating CYAN beacon with vertical beam               ║
 * ║  - Double rotating rings (horizontal planes)                      ║
 * ║  - Pulsing glow effect                                            ║
 * ║  - "▶ START" label                                                ║
 * ║                                                                   ║
 * ║  DESTINATION MARKER:                                              ║
 * ║  - Large GOLD/YELLOW target crosshair                             ║
 * ║  - Diamond frame rotating around destination                      ║
 * ║  - Vertical beacon reaching to the sky                            ║
 * ║  - "◆ DESTINATION" label                                          ║
 * ║                                                                   ║
 * ║  PATH VISUALIZATION:                                              ║
 * ║  - Gradient color from cyan (start) to gold (end)                 ║
 * ║  - Animated "marching ants" effect on path                        ║
 * ║  - Node markers with pulsing effect                               ║
 * ║  - Direction arrows showing movement direction                    ║
 * ╚═══════════════════════════════════════════════════════════════════╝
 *
 * Activation: Toggle in VoxelLab Dashboard
 */
@SuppressWarnings("null")
public class PathfindingDebugger {
    public static final PathfindingDebugger INSTANCE = new PathfindingDebugger();

    private boolean enabled = false;

    // Cache paths to avoid flickering when path is briefly null
    private final Map<UUID, CachedPath> cachedPaths = new HashMap<>();
    private static final long PATH_CACHE_DURATION = 2000; // 2 second cache

    // Get configurable render distance
    private double getMaxRenderDistance() {
        return SettingsManager.INSTANCE.getSettings().visualizers.getRenderDistance();
    }

    // Animation timing
    private long animationTick = 0;

    private PathfindingDebugger() {}

    public void toggle() {
        enabled = !enabled;
        if (!enabled) {
            cachedPaths.clear();
        }
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            cachedPaths.clear();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Track the path for a specific mob - called from tick events
     *
     * IMPROVED: Better handling of path states and edge cases
     */
    public void trackMobPath(@Nonnull Mob mob) {
        if (!enabled) return;

        PathNavigation nav = mob.getNavigation();
        Path path = nav.getPath();
        UUID mobId = mob.getUUID();
        long now = System.currentTimeMillis();

        // Check if we have a valid path with remaining nodes
        boolean hasValidPath = path != null
                && path.getNodeCount() > 0
                && path.getNextNodeIndex() < path.getNodeCount();

        if (hasValidPath) {
            // Build node list starting from current position
            List<Vec3> nodes = new ArrayList<>();

            // Add mob's current position as first point
            nodes.add(mob.position().add(0, 0.1, 0));

            // Add remaining path nodes (from current index onwards)
            int startIndex = path.getNextNodeIndex();
            for (int i = startIndex; i < path.getNodeCount(); i++) {
                Node node = path.getNode(i);
                if (node != null) {
                    nodes.add(Vec3.atBottomCenterOf(node.asBlockPos()).add(0, 0.1, 0));
                }
            }

            // Only cache if we have at least 2 points (start + destination)
            if (nodes.size() >= 2) {
                Vec3 target = path.getTarget() != null ? Vec3.atCenterOf(path.getTarget()) : nodes.get(nodes.size() - 1);
                boolean canReach = path.canReach();

                cachedPaths.put(mobId, new CachedPath(
                        mob.getName().getString(),
                        nodes,
                        target,
                        canReach,
                        now,
                        mob.position()
                ));
            }
        } else if (nav.isInProgress()) {
            // Navigation is in progress but path is temporarily unavailable
            // This can happen during path recalculation
            CachedPath existing = cachedPaths.get(mobId);
            if (existing != null && now - existing.timestamp < PATH_CACHE_DURATION) {
                // Update mob position but keep cached path data
                List<Vec3> updatedNodes = new ArrayList<>(existing.nodes);
                if (!updatedNodes.isEmpty()) {
                    updatedNodes.set(0, mob.position().add(0, 0.1, 0));
                }
                cachedPaths.put(mobId, new CachedPath(
                        existing.mobName,
                        updatedNodes,
                        existing.target,
                        existing.canReach,
                        existing.timestamp, // Keep original timestamp for fading
                        mob.position()
                ));
            }
        }
        // If no valid path and not in progress, let the cache expire naturally
    }

    /**
     * Track mob's current attack target (even without active path)
     * This helps visualize which mobs are targeting something
     *
     * CLIENT-SIDE FIX: On client, mob.getTarget() may be null even if mob is hostile.
     * We also check if the mob is a Monster type and show line to player.
     */
    private void trackMobTarget(@Nonnull Mob mob, @Nonnull net.minecraft.world.entity.player.Player player) {
        UUID mobId = mob.getUUID();
        long now = System.currentTimeMillis();

        // Don't override if we already have a valid path cached
        CachedPath existing = cachedPaths.get(mobId);
        if (existing != null && now - existing.timestamp < 500) {
            return; // Already have fresh path data
        }

        // Check if mob has an attack target
        net.minecraft.world.entity.LivingEntity target = mob.getTarget();

        // CLIENT-SIDE FALLBACK: If no target but mob is hostile monster type,
        // check if it's close enough to potentially be targeting the player
        if (target == null && mob instanceof net.minecraft.world.entity.monster.Monster) {
            double distToPlayer = mob.distanceTo(player);
            // If monster is within aggro range (16 blocks default), assume it's targeting player
            if (distToPlayer <= 16.0) {
                target = player;
            }
        }

        if (target == null) return;

        // Create a simple 2-point "path" from mob to target
        List<Vec3> nodes = new ArrayList<>();
        nodes.add(mob.position().add(0, 0.1, 0));
        nodes.add(target.position().add(0, 0.1, 0));

        // Check if mob can actually reach target (simple distance check)
        boolean canReach = mob.distanceTo(target) <= 32.0;

        String targetName = target == player ? "Player" : target.getName().getString();
        cachedPaths.put(mobId, new CachedPath(
                mob.getName().getString() + " → " + targetName,
                nodes,
                target.position(),
                canReach,
                now,
                mob.position()
        ));
    }

    /**
     * Render all tracked paths
     * Automatically tracks nearby mobs before rendering
     */
    public void render(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource buffer, @Nonnull Vec3 cameraPos) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Update animation tick
        animationTick = System.currentTimeMillis();

        // PERFORMANCE FIX: Use AABB query instead of iterating all entities
        AABB searchBox = mc.player.getBoundingBox().inflate(getMaxRenderDistance());
        for (net.minecraft.world.entity.Entity entity : mc.level.getEntities(mc.player, searchBox)) {
            if (entity instanceof Mob mob) {
                trackMobPath(mob);

                // Also show mob's current target if they have one (even without active path)
                trackMobTarget(mob, mc.player);
            }
        }

        // Clean up old entries
        long now = System.currentTimeMillis();
        cachedPaths.entrySet().removeIf(entry ->
                now - entry.getValue().timestamp > PATH_CACHE_DURATION * 3);

        if (cachedPaths.isEmpty()) return;

        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();
        var pose = poseStack.last();

        for (Map.Entry<UUID, CachedPath> entry : cachedPaths.entrySet()) {
            CachedPath pathData = entry.getValue();

            // Calculate alpha based on age (fade out old paths)
            float age = (now - pathData.timestamp) / (float) (PATH_CACHE_DURATION * 3);
            float alpha = Math.max(0.2f, 1.0f - age * 0.7f);

            renderSpectacularPath(consumer, matrix, pose, pathData, alpha);
        }

        poseStack.popPose();
    }

    /**
     * Render a spectacular path with animated START and DESTINATION markers
     */
    private void renderSpectacularPath(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                       @Nonnull PoseStack.Pose pose, @Nonnull CachedPath pathData, float alpha) {
        List<Vec3> nodes = pathData.nodes;
        if (nodes.isEmpty()) return;

        // Animation parameters
        float time = (animationTick % 4000) / 4000.0f; // 4 second cycle
        float pulse = (float) (0.7f + 0.3f * Math.sin(time * Math.PI * 2 * 3)); // Pulsing 3x per cycle
        float rotation = time * 360.0f; // Full rotation per cycle

        Vec3 startPos = nodes.get(0);
        Vec3 endPos = nodes.size() > 1 ? nodes.get(nodes.size() - 1) : startPos;
        Vec3 targetPos = pathData.target != null ? pathData.target : endPos;

        // ═══════════════════════════════════════════════════════════════
        // 🚀 SPECTACULAR START MARKER (CYAN)
        // ═══════════════════════════════════════════════════════════════
        renderStartBeacon(consumer, matrix, pose, startPos, alpha, pulse, rotation);

        // ═══════════════════════════════════════════════════════════════
        // ◆ SPECTACULAR DESTINATION MARKER (GOLD)
        // ═══════════════════════════════════════════════════════════════
        renderDestinationBeacon(consumer, matrix, pose, targetPos, pathData.canReach, alpha, pulse, rotation);

        // ═══════════════════════════════════════════════════════════════
        // 〰️ ANIMATED PATH WITH GRADIENT
        // ═══════════════════════════════════════════════════════════════
        if (nodes.size() >= 2) {
            renderAnimatedPath(consumer, matrix, pose, nodes, pathData.canReach, alpha, time);
        }

        // ═══════════════════════════════════════════════════════════════
        // 📍 NODE MARKERS
        // ═══════════════════════════════════════════════════════════════
        for (int i = 1; i < nodes.size() - 1; i++) {
            float progress = (float) i / (nodes.size() - 1);
            renderIntermediateNode(consumer, matrix, pose, nodes.get(i), progress, alpha, pulse);
        }

        // ═══════════════════════════════════════════════════════════════
        // 📝 LABELS
        // ═══════════════════════════════════════════════════════════════
        DebugRenderer.INSTANCE.addLabel(startPos.add(0, 2.5, 0),
                "▶ START: " + pathData.mobName, 0xFF00FFFF, 50);

        String destLabel = pathData.canReach ? "◆ DESTINATION" : "✗ UNREACHABLE";
        int destColor = pathData.canReach ? 0xFFFFD700 : 0xFFFF4444;
        DebugRenderer.INSTANCE.addLabel(targetPos.add(0, 2.5, 0), destLabel, destColor, 50);

        // Distance info
        double distance = startPos.distanceTo(targetPos);
        DebugRenderer.INSTANCE.addLabel(startPos.add(targetPos).scale(0.5).add(0, 1.0, 0),
                String.format("%.1f blocks", distance), 0xFFAAAAAA, 50);
    }

    /**
     * Render spectacular START beacon with rotating rings and vertical beam
     */
    private void renderStartBeacon(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                   @Nonnull PoseStack.Pose pose, @Nonnull Vec3 pos,
                                   float alpha, float pulse, float rotation) {
        float x = (float) pos.x;
        float y = (float) pos.y;
        float z = (float) pos.z;

        // CYAN color for start
        float r = 0.0f, g = 1.0f, b = 1.0f;

        // ─── Vertical Beacon (3 blocks high) ───
        float beaconHeight = 3.0f;
        for (int i = 0; i < 4; i++) {
            float offset = i * 0.05f;
            line(consumer, matrix, pose, x + offset, y, z, x + offset, y + beaconHeight, z, r, g, b, alpha * 0.8f);
            line(consumer, matrix, pose, x - offset, y, z, x - offset, y + beaconHeight, z, r, g, b, alpha * 0.8f);
            line(consumer, matrix, pose, x, y, z + offset, x, y + beaconHeight, z + offset, r, g, b, alpha * 0.8f);
            line(consumer, matrix, pose, x, y, z - offset, x, y + beaconHeight, z - offset, r, g, b, alpha * 0.8f);
        }

        // ─── Ground Circle (rotating) ───
        int segments = 16;
        float radius1 = 0.6f * pulse;
        float radius2 = 0.9f * pulse;

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) Math.toRadians((360.0f * i / segments) + rotation);
            float angle2 = (float) Math.toRadians((360.0f * (i + 1) / segments) + rotation);

            // Inner rotating ring
            float x1 = x + (float) Math.cos(angle1) * radius1;
            float z1 = z + (float) Math.sin(angle1) * radius1;
            float x2 = x + (float) Math.cos(angle2) * radius1;
            float z2 = z + (float) Math.sin(angle2) * radius1;
            line(consumer, matrix, pose, x1, y + 0.1f, z1, x2, y + 0.1f, z2, r, g, b, alpha);

            // Outer rotating ring (opposite direction)
            float angle1b = (float) Math.toRadians((360.0f * i / segments) - rotation);
            float angle2b = (float) Math.toRadians((360.0f * (i + 1) / segments) - rotation);
            x1 = x + (float) Math.cos(angle1b) * radius2;
            z1 = z + (float) Math.sin(angle1b) * radius2;
            x2 = x + (float) Math.cos(angle2b) * radius2;
            z2 = z + (float) Math.sin(angle2b) * radius2;
            line(consumer, matrix, pose, x1, y + 0.1f, z1, x2, y + 0.1f, z2, r, g, b, alpha * 0.7f);
        }

        // ─── Elevated Rotating Ring (at beacon top) ───
        float topY = y + beaconHeight;
        float topRadius = 0.4f;
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) Math.toRadians((360.0f * i / segments) + rotation * 2);
            float angle2 = (float) Math.toRadians((360.0f * (i + 1) / segments) + rotation * 2);
            float x1 = x + (float) Math.cos(angle1) * topRadius;
            float z1 = z + (float) Math.sin(angle1) * topRadius;
            float x2 = x + (float) Math.cos(angle2) * topRadius;
            float z2 = z + (float) Math.sin(angle2) * topRadius;
            line(consumer, matrix, pose, x1, topY, z1, x2, topY, z2, r, g, b, alpha);
        }

        // ─── Cross pattern on ground ───
        float crossSize = 1.0f;
        line(consumer, matrix, pose, x - crossSize, y + 0.05f, z, x + crossSize, y + 0.05f, z, r, g, b, alpha);
        line(consumer, matrix, pose, x, y + 0.05f, z - crossSize, x, y + 0.05f, z + crossSize, r, g, b, alpha);

        // ─── Diagonal lines ───
        float diagSize = 0.7f;
        line(consumer, matrix, pose, x - diagSize, y + 0.05f, z - diagSize, x + diagSize, y + 0.05f, z + diagSize, r, g, b, alpha * 0.6f);
        line(consumer, matrix, pose, x - diagSize, y + 0.05f, z + diagSize, x + diagSize, y + 0.05f, z - diagSize, r, g, b, alpha * 0.6f);

        // ─── Arrow pointing up ───
        float arrowY = y + 1.5f;
        float arrowSize = 0.3f;
        line(consumer, matrix, pose, x, arrowY, z, x, arrowY + 0.5f, z, r, g, b, alpha);
        line(consumer, matrix, pose, x, arrowY + 0.5f, z, x - arrowSize, arrowY + 0.3f, z, r, g, b, alpha);
        line(consumer, matrix, pose, x, arrowY + 0.5f, z, x + arrowSize, arrowY + 0.3f, z, r, g, b, alpha);
    }

    /**
     * Render spectacular DESTINATION beacon with diamond frame and target crosshair
     */
    private void renderDestinationBeacon(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                         @Nonnull PoseStack.Pose pose, @Nonnull Vec3 pos,
                                         boolean canReach, float alpha, float pulse, float rotation) {
        float x = (float) pos.x;
        float y = (float) pos.y;
        float z = (float) pos.z;

        // GOLD for reachable, RED for unreachable
        float r, g, b;
        if (canReach) {
            r = 1.0f; g = 0.84f; b = 0.0f; // Gold
        } else {
            r = 1.0f; g = 0.2f; b = 0.2f; // Red
        }

        // ─── Vertical Beacon (5 blocks high - taller than start) ───
        float beaconHeight = 5.0f;
        for (int i = 0; i < 4; i++) {
            float offset = i * 0.05f;
            line(consumer, matrix, pose, x + offset, y, z, x + offset, y + beaconHeight, z, r, g, b, alpha * 0.8f);
            line(consumer, matrix, pose, x - offset, y, z, x - offset, y + beaconHeight, z, r, g, b, alpha * 0.8f);
            line(consumer, matrix, pose, x, y, z + offset, x, y + beaconHeight, z + offset, r, g, b, alpha * 0.8f);
            line(consumer, matrix, pose, x, y, z - offset, x, y + beaconHeight, z - offset, r, g, b, alpha * 0.8f);
        }

        // ─── Large Target Crosshair on Ground ───
        float targetSize = 1.5f;
        // Main cross
        line(consumer, matrix, pose, x - targetSize, y + 0.1f, z, x + targetSize, y + 0.1f, z, r, g, b, alpha);
        line(consumer, matrix, pose, x, y + 0.1f, z - targetSize, x, y + 0.1f, z + targetSize, r, g, b, alpha);

        // Inner circle marks
        float innerMark = 0.5f;
        line(consumer, matrix, pose, x - innerMark, y + 0.1f, z - 0.1f, x - innerMark, y + 0.1f, z + 0.1f, r, g, b, alpha);
        line(consumer, matrix, pose, x + innerMark, y + 0.1f, z - 0.1f, x + innerMark, y + 0.1f, z + 0.1f, r, g, b, alpha);
        line(consumer, matrix, pose, x - 0.1f, y + 0.1f, z - innerMark, x + 0.1f, y + 0.1f, z - innerMark, r, g, b, alpha);
        line(consumer, matrix, pose, x - 0.1f, y + 0.1f, z + innerMark, x + 0.1f, y + 0.1f, z + innerMark, r, g, b, alpha);

        // ─── Rotating Diamond Frame ───
        float diamondRadius = 1.2f * pulse;
        float diamondHeight = y + 1.0f;

        float[] diamondX = new float[4];
        float[] diamondZ = new float[4];
        for (int i = 0; i < 4; i++) {
            float angle = (float) Math.toRadians((90.0f * i) + rotation);
            diamondX[i] = x + (float) Math.cos(angle) * diamondRadius;
            diamondZ[i] = z + (float) Math.sin(angle) * diamondRadius;
        }

        // Connect diamond points
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            line(consumer, matrix, pose, diamondX[i], diamondHeight, diamondZ[i],
                    diamondX[next], diamondHeight, diamondZ[next], r, g, b, alpha);
            // Connect to center
            line(consumer, matrix, pose, diamondX[i], diamondHeight, diamondZ[i],
                    x, diamondHeight + 0.3f, z, r, g, b, alpha * 0.5f);
        }

        // ─── Outer Circles (multiple levels) ───
        int circleSegments = 16;
        float[] circleRadii = {0.8f, 1.2f};
        float[] circleHeights = {y + 0.1f, y + 0.5f};

        for (int c = 0; c < circleRadii.length; c++) {
            float radius = circleRadii[c] * pulse;
            float circleY = circleHeights[c];
            float rotDir = (c % 2 == 0) ? rotation : -rotation;

            for (int i = 0; i < circleSegments; i++) {
                float angle1 = (float) Math.toRadians((360.0f * i / circleSegments) + rotDir);
                float angle2 = (float) Math.toRadians((360.0f * (i + 1) / circleSegments) + rotDir);
                float x1 = x + (float) Math.cos(angle1) * radius;
                float z1 = z + (float) Math.sin(angle1) * radius;
                float x2 = x + (float) Math.cos(angle2) * radius;
                float z2 = z + (float) Math.sin(angle2) * radius;
                line(consumer, matrix, pose, x1, circleY, z1, x2, circleY, z2, r, g, b, alpha * (1.0f - c * 0.2f));
            }
        }

        // ─── Pulsing inner diamond marker ───
        float innerSize = 0.3f * pulse;
        line(consumer, matrix, pose, x, y + 0.2f, z - innerSize, x + innerSize, y + 0.2f, z, r, g, b, alpha);
        line(consumer, matrix, pose, x + innerSize, y + 0.2f, z, x, y + 0.2f, z + innerSize, r, g, b, alpha);
        line(consumer, matrix, pose, x, y + 0.2f, z + innerSize, x - innerSize, y + 0.2f, z, r, g, b, alpha);
        line(consumer, matrix, pose, x - innerSize, y + 0.2f, z, x, y + 0.2f, z - innerSize, r, g, b, alpha);

        // ─── Vertical spokes at top of beacon ───
        float topY = y + beaconHeight;
        float spokeLength = 0.5f;
        line(consumer, matrix, pose, x, topY, z, x, topY + spokeLength, z, r, g, b, alpha);
        line(consumer, matrix, pose, x - spokeLength * 0.5f, topY, z, x + spokeLength * 0.5f, topY, z, r, g, b, alpha);
        line(consumer, matrix, pose, x, topY, z - spokeLength * 0.5f, x, topY, z + spokeLength * 0.5f, r, g, b, alpha);
    }

    /**
     * Render animated path with gradient color and marching ants effect
     */
    private void renderAnimatedPath(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                    @Nonnull PoseStack.Pose pose, @Nonnull List<Vec3> nodes,
                                    boolean canReach, float alpha, float time) {
        // Calculate total path length for animation
        double totalLength = 0;
        for (int i = 0; i < nodes.size() - 1; i++) {
            totalLength += nodes.get(i).distanceTo(nodes.get(i + 1));
        }

        double currentLength = 0;
        float marchOffset = time * 10; // Marching ants speed

        for (int i = 0; i < nodes.size() - 1; i++) {
            Vec3 p1 = nodes.get(i);
            Vec3 p2 = nodes.get(i + 1);
            double segmentLength = p1.distanceTo(p2);

            // Gradient from CYAN (start) to GOLD (end)
            float progress = (float) (currentLength / totalLength);
            float r, g, b;
            if (canReach) {
                // Cyan to Gold gradient
                r = progress; // 0 -> 1
                g = 1.0f - progress * 0.16f; // 1 -> 0.84
                b = 1.0f - progress; // 1 -> 0
            } else {
                // Cyan to Red gradient
                r = progress; // 0 -> 1
                g = 1.0f - progress * 0.8f; // 1 -> 0.2
                b = 1.0f - progress * 0.8f; // 1 -> 0.2
            }

            // Main path line (thick appearance with multiple lines)
            for (float offset = -0.03f; offset <= 0.03f; offset += 0.03f) {
                line(consumer, matrix, pose,
                        (float) p1.x + offset, (float) p1.y, (float) p1.z,
                        (float) p2.x + offset, (float) p2.y, (float) p2.z,
                        r, g, b, alpha * 0.9f);
                line(consumer, matrix, pose,
                        (float) p1.x, (float) p1.y + offset, (float) p1.z,
                        (float) p2.x, (float) p2.y + offset, (float) p2.z,
                        r, g, b, alpha * 0.9f);
            }

            // Marching ants effect (animated dashes)
            Vec3 dir = p2.subtract(p1).normalize();
            int numDashes = Math.max(2, (int) (segmentLength / 0.4));
            for (int d = 0; d < numDashes; d++) {
                float dashProgress = ((d + marchOffset) % numDashes) / (float) numDashes;
                Vec3 dashStart = p1.add(dir.scale(segmentLength * dashProgress));
                Vec3 dashEnd = p1.add(dir.scale(Math.min(segmentLength, segmentLength * dashProgress + 0.15)));

                // Bright dash
                line(consumer, matrix, pose,
                        (float) dashStart.x, (float) dashStart.y + 0.15f, (float) dashStart.z,
                        (float) dashEnd.x, (float) dashEnd.y + 0.15f, (float) dashEnd.z,
                        1.0f, 1.0f, 1.0f, alpha * 0.7f);
            }

            // Direction arrows every 2 blocks
            if (segmentLength > 1.5) {
                Vec3 midPoint = p1.add(p2).scale(0.5);
                renderDirectionArrow(consumer, matrix, pose, midPoint, dir, r, g, b, alpha);
            }

            currentLength += segmentLength;
        }
    }

    /**
     * Render intermediate node marker with pulsing effect
     */
    private void renderIntermediateNode(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                        @Nonnull PoseStack.Pose pose, @Nonnull Vec3 pos,
                                        float progress, float alpha, float pulse) {
        float x = (float) pos.x;
        float y = (float) pos.y;
        float z = (float) pos.z;

        // Color based on progress (cyan to gold)
        float r = progress;
        float g = 1.0f - progress * 0.16f;
        float b = 1.0f - progress;

        float size = 0.15f * pulse;

        // Diamond marker
        line(consumer, matrix, pose, x, y, z - size, x + size, y, z, r, g, b, alpha);
        line(consumer, matrix, pose, x + size, y, z, x, y, z + size, r, g, b, alpha);
        line(consumer, matrix, pose, x, y, z + size, x - size, y, z, r, g, b, alpha);
        line(consumer, matrix, pose, x - size, y, z, x, y, z - size, r, g, b, alpha);

        // Vertical spike
        line(consumer, matrix, pose, x, y, z, x, y + size * 2, z, r, g, b, alpha * 0.8f);
    }

    /**
     * Render direction arrow showing movement direction
     */
    private void renderDirectionArrow(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix,
                                      @Nonnull PoseStack.Pose pose, @Nonnull Vec3 pos,
                                      @Nonnull Vec3 direction, float r, float g, float b, float alpha) {
        float arrowLength = 0.3f;
        float arrowWidth = 0.15f;

        Vec3 tip = pos.add(direction.scale(arrowLength)).add(0, 0.3, 0);
        Vec3 right = direction.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 back1 = tip.subtract(direction.scale(arrowLength * 0.7)).add(right.scale(arrowWidth));
        Vec3 back2 = tip.subtract(direction.scale(arrowLength * 0.7)).subtract(right.scale(arrowWidth));

        // Arrow head
        line(consumer, matrix, pose, (float) tip.x, (float) tip.y, (float) tip.z,
                (float) back1.x, (float) back1.y, (float) back1.z, r, g, b, alpha);
        line(consumer, matrix, pose, (float) tip.x, (float) tip.y, (float) tip.z,
                (float) back2.x, (float) back2.y, (float) back2.z, r, g, b, alpha);
        line(consumer, matrix, pose, (float) back1.x, (float) back1.y, (float) back1.z,
                (float) back2.x, (float) back2.y, (float) back2.z, r, g, b, alpha * 0.5f);
    }

    private void line(@Nonnull VertexConsumer consumer, @Nonnull Matrix4f matrix, @Nonnull PoseStack.Pose pose,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float r, float g, float b, float a) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
    }

    /**
     * Get number of active tracked paths
     */
    public int getActivePathCount() {
        return cachedPaths.size();
    }

    /**
     * Cached path data with mob info
     */
    private record CachedPath(
            String mobName,
            List<Vec3> nodes,
            Vec3 target,
            boolean canReach,
            long timestamp,
            Vec3 mobPosition
    ) {}
}
