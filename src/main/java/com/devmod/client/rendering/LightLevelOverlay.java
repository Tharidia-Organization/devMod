package com.devmod.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Objects;

/**
 * FASE 4 REQ-A2: Light Level Overlay
 *
 * Displays light levels on blocks around the player:
 * - Green (light >= 8): Safe, no mob spawns
 * - Yellow (light 1-7): Partially dark
 * - Red (light 0): Mobs can spawn!
 *
 * Also shows:
 * - Light level number on each block (optional)
 * - Only solid surfaces where mobs could spawn
 *
 * Activation: L key (configurable)
 */
// Minecraft API methods are not annotated but never return null in practice

public class LightLevelOverlay {
    public static final LightLevelOverlay INSTANCE = new LightLevelOverlay();

    private boolean enabled = false;
    private int radius = 16; // Blocchi intorno al player
    private boolean showNumbers = true;
    private boolean onlySpawnableSurfaces = true;

    // === PERFORMANCE OPTIMIZATION: Caching ===
    private static final int CACHE_UPDATE_INTERVAL_TICKS = 5; // Update every 5 ticks (~250ms)
    private int ticksSinceLastUpdate = 0;
    private BlockPos lastPlayerPos = null;
    private java.util.List<LightData> cachedLightData = new java.util.ArrayList<>();

    private record LightData(BlockPos pos, int lightLevel) {}

    private LightLevelOverlay() {}

    public void toggle() {
        enabled = !enabled;
        if (!enabled) {
            cachedLightData.clear(); // Free memory when disabled
        }
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            cachedLightData.clear(); // Free memory when disabled
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(4, Math.min(32, radius));
    }

    public int getRadius() {
        return radius;
    }

    public void setShowNumbers(boolean show) {
        this.showNumbers = show;
    }

    public void setOnlySpawnableSurfaces(boolean only) {
        this.onlySpawnableSurfaces = only;
    }

    /**
     * Render the light level overlay
     * OPTIMIZED: Uses caching to drastically reduce API calls
     */
    public void render(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        var player = Objects.requireNonNull(mc.player);
        ClientLevel level = mc.level;
        BlockPos playerPos = player.blockPosition();

        // === PERFORMANCE: Update cache only periodically or if player has moved ===
        boolean needsCacheUpdate = false;
        ticksSinceLastUpdate++;

        if (ticksSinceLastUpdate >= CACHE_UPDATE_INTERVAL_TICKS) {
            needsCacheUpdate = true;
            ticksSinceLastUpdate = 0;
        }

        // Force update if the player has moved more than 2 blocks
        if (lastPlayerPos == null || playerPos.distManhattan(Objects.requireNonNull(lastPlayerPos)) > 2) {
            needsCacheUpdate = true;
            ticksSinceLastUpdate = 0;
        }

        if (needsCacheUpdate) {
            updateLightCache(level, playerPos);
            lastPlayerPos = playerPos;
        }

        // === Rendering from cache (much faster) ===
        if (cachedLightData.isEmpty()) return;

        VertexConsumer consumer = buffer.getBuffer(Objects.requireNonNull(RenderType.debugQuads()));

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());
        var pose = Objects.requireNonNull(poseStack.last());

        for (LightData data : cachedLightData) {
            int combinedLight = data.lightLevel();

            // Determine color based on light level
            float r, g, b, a;
            if (combinedLight == 0) {
                r = 1.0f; g = 0.0f; b = 0.0f; a = 0.5f;
            } else if (combinedLight <= 7) {
                r = 1.0f; g = 1.0f; b = 0.0f; a = 0.35f;
            } else {
                r = 0.0f; g = 1.0f; b = 0.0f; a = 0.25f;
            }

            float px = data.pos().getX();
            float py = data.pos().getY() + 1.01f;
            float pz = data.pos().getZ();

            Objects.requireNonNull(consumer.addVertex(matrix, px, py, pz).setColor(r, g, b, a)).setNormal(pose, 0f, 1f, 0f);
            Objects.requireNonNull(consumer.addVertex(matrix, px, py, pz + 1).setColor(r, g, b, a)).setNormal(pose, 0f, 1f, 0f);
            Objects.requireNonNull(consumer.addVertex(matrix, px + 1, py, pz + 1).setColor(r, g, b, a)).setNormal(pose, 0f, 1f, 0f);
            Objects.requireNonNull(consumer.addVertex(matrix, px + 1, py, pz).setColor(r, g, b, a)).setNormal(pose, 0f, 1f, 0f);
        }

        poseStack.popPose();

        // Render numbers if enabled (uses cache)
        if (showNumbers) {
            renderLightNumbersCached(poseStack, cameraPos);
        }
    }

    /**
     * Updates the light data cache (called every N ticks)
     */
    private void updateLightCache(ClientLevel level, BlockPos playerPos) {
        cachedLightData.clear();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                // Skip if too far (circular optimization)
                if (x*x + z*z > radius*radius) continue;

                for (int y = -4; y <= 4; y++) {
                    BlockPos checkPos = Objects.requireNonNull(playerPos.offset(x, y, z));
                    BlockPos above = Objects.requireNonNull(checkPos.above());

                    if (onlySpawnableSurfaces) {
                        if (!level.getBlockState(checkPos).isSolidRender(level, checkPos)) continue;
                        if (!level.getBlockState(above).isAir()) continue;
                    }

                    int blockLight = level.getBrightness(LightLayer.BLOCK, above);
                    int skyLight = level.getBrightness(LightLayer.SKY, above);
                    int combinedLight = Math.max(blockLight, skyLight);

                    cachedLightData.add(new LightData(checkPos, combinedLight));
                    break; // Only first block found
                }
            }
        }
    }

    /**
     * Renders light level numbers from cache (OPTIMIZED)
     */
    private void renderLightNumbersCached(PoseStack poseStack, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        int numberRadiusSqr = 64; // 8 blocchi al quadrato

        for (LightData data : cachedLightData) {
            // Only blocks within 8 blocks of camera
            double dx = data.pos().getX() + 0.5 - cameraPos.x;
            double dz = data.pos().getZ() + 0.5 - cameraPos.z;
            if (dx*dx + dz*dz > numberRadiusSqr) continue;

            int combinedLight = data.lightLevel();

            // Text color based on light level
            int textColor;
            if (combinedLight == 0) {
                textColor = 0xFFFF0000; // Rosso
            } else if (combinedLight <= 7) {
                textColor = 0xFFFFFF00; // Giallo
            } else {
                textColor = 0xFF00FF00; // Verde
            }

            // Label position (block center, slightly above)
            Vec3 labelPos = new Vec3(data.pos().getX() + 0.5, data.pos().getY() + 1.3, data.pos().getZ() + 0.5);

            renderFloatingText(poseStack, cameraPos, labelPos, String.valueOf(combinedLight), textColor);
        }
    }

    private void renderFloatingText(PoseStack poseStack, Vec3 cameraPos, Vec3 pos, String text, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        poseStack.pushPose();

        // Position relative to camera
        float x = (float) (pos.x - cameraPos.x);
        float y = (float) (pos.y - cameraPos.y);
        float z = (float) (pos.z - cameraPos.z);

        poseStack.translate(x, y, z);

        // Billboard (always face camera)
        poseStack.mulPose(Objects.requireNonNull(mc.getEntityRenderDispatcher().cameraOrientation()));

        // Scale
        float scale = 0.02f;
        poseStack.scale(-scale, -scale, scale);

        // Center text
        var font = Objects.requireNonNull(mc.font);
        String safeText = Objects.requireNonNull(text);
        int width = font.width(safeText);
        float xOffset = -width / 2f;

        // Draw
        font.drawInBatch(safeText, xOffset, 0, color, false,
                Objects.requireNonNull(poseStack.last().pose()), Objects.requireNonNull(mc.renderBuffers().bufferSource()),
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0x40000000, 15728880);

        poseStack.popPose();
    }
}
