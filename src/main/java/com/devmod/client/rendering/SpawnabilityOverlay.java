package com.devmod.client.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SpawnabilityOverlay {
    public static final SpawnabilityOverlay INSTANCE = new SpawnabilityOverlay();

    private boolean enabled = false;
    private int radius = 16;
    private static final int MIN_DYNAMIC_RADIUS = 4;
    private int dynamicRadius = radius;
    private boolean showOnlySafeBlocks = false; // If true, show only safe blocks (green)
    private boolean showOnlyDangerBlocks = true; // If true, show only danger blocks (red/orange)

    // Performance: Caching
    private static final int CACHE_UPDATE_INTERVAL_TICKS = 10; // Update every 10 ticks (~500ms)
    private int ticksSinceLastUpdate = 0;
    private BlockPos lastPlayerPos = null;
    private final List<SpawnData> cachedSpawnData = new ArrayList<>();

    // Spawn categories
    public enum SpawnCategory {
        HOSTILE_SPAWN,    // Light 0, valid surface - RED
        CONDITIONAL_SPAWN, // Light 1-7, valid surface (thunderstorm spawn possible) - ORANGE
        SAFE              // Light >= 8 or invalid surface - GREEN
    }

    private record SpawnData(BlockPos pos, SpawnCategory category, int lightLevel) {}

    private SpawnabilityOverlay() {}

    public void toggle() {
        enabled = !enabled;
        if (!enabled) {
            cachedSpawnData.clear();
        }
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            cachedSpawnData.clear();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(4, Math.min(32, radius));
        if (dynamicRadius <= 0) {
            dynamicRadius = this.radius;
        } else if (dynamicRadius > this.radius) {
            dynamicRadius = this.radius;
        }
    }

    public int getRadius() {
        return radius;
    }

    public int getDynamicRadius() {
        return dynamicRadius <= 0 ? radius : dynamicRadius;
    }

    public void setShowOnlySafeBlocks(boolean value) {
        this.showOnlySafeBlocks = value;
    }

    public void setShowOnlyDangerBlocks(boolean value) {
        this.showOnlyDangerBlocks = value;
    }

    /**
     * Render the spawnability overlay.
     * Optimized with caching to reduce API calls.
     */
    public void render(PoseStack poseStack, MultiBufferSource buffer, Vec3 cameraPos) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;

        BlockPos playerPos = player.blockPosition();

        // Performance: Update cache periodically or when player moves
        boolean needsCacheUpdate = false;
        ticksSinceLastUpdate++;

        if (ticksSinceLastUpdate >= CACHE_UPDATE_INTERVAL_TICKS) {
            needsCacheUpdate = true;
            ticksSinceLastUpdate = 0;
        }

        // Force update if player moved more than 2 blocks
        BlockPos previousPos = lastPlayerPos;
        if (previousPos == null) {
            needsCacheUpdate = true;
            ticksSinceLastUpdate = 0;
        } else if (playerPos.distManhattan(previousPos) > 2) {
            needsCacheUpdate = true;
            ticksSinceLastUpdate = 0;
        }

        if (needsCacheUpdate) {
            updateSpawnCache(level, playerPos, resolveDynamicRadius());
            lastPlayerPos = playerPos;
        }

        if (cachedSpawnData.isEmpty()) return;

        VertexConsumer consumer = buffer.getBuffer(Objects.requireNonNull(RenderType.debugQuads()));

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        PoseStack.Pose pose = Objects.requireNonNull(poseStack.last());
        Matrix4f matrix = Objects.requireNonNull(pose.pose());

        for (SpawnData data : cachedSpawnData) {
            // Filter based on settings
            if (showOnlySafeBlocks && data.category() != SpawnCategory.SAFE) continue;
            if (showOnlyDangerBlocks && data.category() == SpawnCategory.SAFE) continue;

            // Determine color based on spawn category
            float r, g, b, a;
            switch (data.category()) {
                case HOSTILE_SPAWN -> {
                    r = 1.0f; g = 0.0f; b = 0.0f; a = 0.6f; // Red - hostile can spawn
                }
                case CONDITIONAL_SPAWN -> {
                    r = 1.0f; g = 0.5f; b = 0.0f; a = 0.4f; // Orange - conditional spawn
                }
                case SAFE -> {
                    r = 0.0f; g = 1.0f; b = 0.0f; a = 0.2f; // Green - safe
                }
                default -> {
                    continue;
                }
            }

            float px = data.pos().getX();
            float py = data.pos().getY() + 1.01f; // Slightly above the block
            float pz = data.pos().getZ();

            // Draw quad on top of block
            consumer.addVertex(matrix, px, py, pz).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
            consumer.addVertex(matrix, px, py, pz + 1).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
            consumer.addVertex(matrix, px + 1, py, pz + 1).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
            consumer.addVertex(matrix, px + 1, py, pz).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
        }

        poseStack.popPose();

        // Render light level numbers on dangerous blocks
        renderSpawnNumbers(poseStack, cameraPos);
    }

    /**
     * Updates the spawn cache (called periodically).
     */
    private void updateSpawnCache(ClientLevel level, BlockPos playerPos, int activeRadius) {
        cachedSpawnData.clear();

        // Check if difficulty allows spawning
        boolean peacefulMode = level.getDifficulty() == Difficulty.PEACEFUL;

        for (int x = -activeRadius; x <= activeRadius; x++) {
            for (int z = -activeRadius; z <= activeRadius; z++) {
                // Circular optimization
                if (x * x + z * z > activeRadius * activeRadius) continue;

                for (int y = -4; y <= 4; y++) {
                    BlockPos surfacePos = Objects.requireNonNull(playerPos.offset(x, y, z));
                    BlockPos spawnPos = Objects.requireNonNull(surfacePos.above());

                    // Check if this is a valid spawn surface
                    if (!isValidSpawnSurface(level, surfacePos, spawnPos)) continue;

                    // Get light level at spawn position
                    int blockLight = level.getBrightness(LightLayer.BLOCK, spawnPos);
                    int skyLight = level.getBrightness(LightLayer.SKY, spawnPos);

                    // For hostile mobs, only block light matters at night
                    // During day, sky light prevents spawning
                    int effectiveLight = Math.max(blockLight, skyLight);

                    SpawnCategory category;
                    if (peacefulMode) {
                        category = SpawnCategory.SAFE;
                    } else if (blockLight == 0 && skyLight == 0) {
                        // Complete darkness - hostile spawn valid
                        category = SpawnCategory.HOSTILE_SPAWN;
                    } else if (effectiveLight <= 7) {
                        // Partial darkness - spawn possible at night/thunderstorm
                        category = SpawnCategory.CONDITIONAL_SPAWN;
                    } else {
                        // Well lit - safe
                        category = SpawnCategory.SAFE;
                    }

                    cachedSpawnData.add(new SpawnData(surfacePos, category, effectiveLight));
                    break; // Only first valid surface found
                }
            }
        }
    }

    /**
     * Checks if a block is a valid spawn surface for hostile mobs.
     */
    private boolean isValidSpawnSurface(ClientLevel level, BlockPos surfacePos, BlockPos spawnPos) {
        BlockPos safeSurfacePos = Objects.requireNonNull(surfacePos);
        BlockPos safeSpawnPos = Objects.requireNonNull(spawnPos);
        BlockPos aboveSpawnPos = Objects.requireNonNull(safeSpawnPos.above());

        BlockState surfaceState = level.getBlockState(safeSurfacePos);
        BlockState spawnState = level.getBlockState(safeSpawnPos);
        BlockState aboveSpawnState = level.getBlockState(aboveSpawnPos);

        // Surface must be solid/full block
        if (!surfaceState.isSolidRender(level, safeSurfacePos)) return false;

        // Spawn position must be passable (air, grass, flowers, etc.)
        if (spawnState.isSolidRender(level, safeSpawnPos)) return false;

        // Block above spawn position must also be passable (for 2-block tall mobs)
        if (aboveSpawnState.isSolidRender(level, aboveSpawnPos)) return false;

        // Check for blocks that prevent spawning
        // (Note: This is a simplified check - full spawn rules are more complex)
        if (surfaceState.getBlock().defaultDestroyTime() < 0) {
            // Bedrock-like blocks - check if it's actually bedrock or barrier
            String blockName = surfaceState.getBlock().getDescriptionId();
            if (blockName.contains("bedrock") || blockName.contains("barrier")) {
                return false;
            }
        }

        return true;
    }

    /**
     * Renders light level numbers on dangerous spawn points.
     */
    private void renderSpawnNumbers(PoseStack poseStack, Vec3 cameraPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        int numberRadiusSqr = 64; // 8 blocks squared

        for (SpawnData data : cachedSpawnData) {
            // Only show numbers on dangerous blocks
            if (data.category() == SpawnCategory.SAFE) continue;

            // Only blocks within 8 blocks of camera
            double dx = data.pos().getX() + 0.5 - cameraPos.x;
            double dz = data.pos().getZ() + 0.5 - cameraPos.z;
            if (dx * dx + dz * dz > numberRadiusSqr) continue;

            // Color based on category
            int textColor;
            String displayText;
            switch (data.category()) {
                case HOSTILE_SPAWN -> {
                    textColor = 0xFFFF0000; // Red
                    displayText = "!" + data.lightLevel();
                }
                case CONDITIONAL_SPAWN -> {
                    textColor = 0xFFFF8800; // Orange
                    displayText = "?" + data.lightLevel();
                }
                default -> {
                    continue;
                }
            }

            Vec3 labelPos = new Vec3(
                data.pos().getX() + 0.5,
                data.pos().getY() + 1.5,
                data.pos().getZ() + 0.5
            );

            renderFloatingText(poseStack, cameraPos, labelPos, displayText, textColor);
        }
    }

    /**
     * Renders floating text in 3D space (billboard style).
     */
    private void renderFloatingText(PoseStack poseStack, Vec3 cameraPos, Vec3 pos, String text, int color) {
        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        if (font == null) return;
        String safeText = Objects.requireNonNull(text);

        poseStack.pushPose();

        // Position relative to camera
        float x = (float) (pos.x - cameraPos.x);
        float y = (float) (pos.y - cameraPos.y);
        float z = (float) (pos.z - cameraPos.z);

        poseStack.translate(x, y, z);

        // Billboard - always face camera
        poseStack.mulPose(Objects.requireNonNull(mc.getEntityRenderDispatcher().cameraOrientation()));

        // Scale
        float scale = 0.025f;
        poseStack.scale(-scale, -scale, scale);

        // Center text
        int width = font.width(safeText);
        float xOffset = -width / 2f;

        // Draw with background
        PoseStack.Pose lastPose = Objects.requireNonNull(poseStack.last());
        Matrix4f textMatrix = Objects.requireNonNull(lastPose.pose());
        MultiBufferSource bufferSource = Objects.requireNonNull(mc.renderBuffers().bufferSource());
        font.drawInBatch(
            safeText, xOffset, 0, color, false,
            textMatrix, bufferSource,
            net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0x60000000, 15728880
        );

        poseStack.popPose();
    }

    /**
     * Gets statistics about current spawn data.
     */
    public SpawnStats getStats() {
        int hostile = 0, conditional = 0, safe = 0;
        for (SpawnData data : cachedSpawnData) {
            switch (data.category()) {
                case HOSTILE_SPAWN -> hostile++;
                case CONDITIONAL_SPAWN -> conditional++;
                case SAFE -> safe++;
            }
        }
        return new SpawnStats(hostile, conditional, safe);
    }

    public record SpawnStats(int hostileSpawnBlocks, int conditionalSpawnBlocks, int safeBlocks) {
        public int totalDangerBlocks() {
            return hostileSpawnBlocks + conditionalSpawnBlocks;
        }
    }

    private int resolveDynamicRadius() {
        int fps = Minecraft.getInstance().getFps();
        float scale = 1.0f;
        if (fps > 0) {
            if (fps < 20) {
                scale = 0.5f;
            } else if (fps < 30) {
                scale = 0.65f;
            } else if (fps < 45) {
                scale = 0.8f;
            }
        }

        int target = Math.max(MIN_DYNAMIC_RADIUS, Math.round(radius * scale));
        if (dynamicRadius == 0) {
            dynamicRadius = target;
            return dynamicRadius;
        }
        if (dynamicRadius < target) {
            dynamicRadius = Math.min(target, dynamicRadius + 1);
        } else if (dynamicRadius > target) {
            dynamicRadius = Math.max(target, dynamicRadius - 1);
        }
        return dynamicRadius;
    }
}
