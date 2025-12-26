package com.devmod.telemetry.spatial;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;

import com.devmod.telemetry.RoomDefinition;
import com.devmod.telemetry.TelemetryJson;
import com.devmod.telemetry.room.RoomService;

import com.mojang.logging.LogUtils;

/**
 * Service for light level and spawnability analysis in rooms.
 * Extracted from TelemetryService for better separation of concerns.
 *
 * Provides:
 * - Room light level scanning with export
 * - Spawnability reports (hostile mob spawn potential)
 * - Batch scanning of all rooms in a dimension
 */
public class LightAnalysisService {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final LightAnalysisService INSTANCE = new LightAnalysisService();

    private LightAnalysisService() {}

    /**
     * Scan a room and export light level + mob spawnability data.
     * Analyzes each block in the room volume and records:
     * - Block light level (0-15)
     * - Sky light level (0-15)
     * - Whether hostile mobs can spawn at that position
     * - Block type (air, solid, spawnable surface)
     *
     * @param level Server level to scan
     * @param roomId Room ID to scan (from rooms config)
     * @param lineConsumer Consumer for each NDJSON line (filename, line)
     */
    public void scanRoomLightLevels(ServerLevel level, String roomId, BiConsumer<String, String> lineConsumer) {
        RoomDefinition room = RoomService.INSTANCE.findRoomById(roomId).orElse(null);
        if (room == null) {
            LOGGER.warn("Room not found for light scan: {}", roomId);
            return;
        }

        BlockPos min = room.min();
        BlockPos max = room.max();
        int scannedBlocks = 0;
        int darkSpots = 0;
        int spawnableSpots = 0;

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockPos above = Objects.requireNonNull(pos.above());

                    // Skip if not air above (can't spawn/stand there)
                    if (!level.getBlockState(above).isAir()) continue;

                    // Check if solid surface below for spawning
                    var blockState = level.getBlockState(pos);
                    boolean isSolidSurface = blockState.isSolidRender(level, pos);

                    if (!isSolidSurface) continue;

                    scannedBlocks++;

                    // Get light levels
                    int blockLight = level.getBrightness(LightLayer.BLOCK, above);
                    int skyLight = level.getBrightness(LightLayer.SKY, above);
                    int combinedLight = Math.max(blockLight, skyLight);

                    // Hostile mobs spawn at light level 0 (since 1.18+)
                    boolean canSpawnHostile = combinedLight == 0;

                    if (combinedLight <= 7) darkSpots++;
                    if (canSpawnHostile) spawnableSpots++;

                    // Export each block data
                    String line = "{\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                            + "\"x\":" + x + ","
                            + "\"y\":" + y + ","
                            + "\"z\":" + z + ","
                            + "\"blockLight\":" + blockLight + ","
                            + "\"skyLight\":" + skyLight + ","
                            + "\"combinedLight\":" + combinedLight + ","
                            + "\"canSpawnHostile\":" + canSpawnHostile + ","
                            + "\"blockType\":\"" + TelemetryJson.escape(blockState.getBlock().getName().getString()) + "\"}";
                    lineConsumer.accept("light_levels.ndjson", line);
                }
            }
        }

        // Summary line
        String summaryLine = "{\"room\":\"" + TelemetryJson.escape(roomId) + "\","
                + "\"type\":\"summary\","
                + "\"totalScanned\":" + scannedBlocks + ","
                + "\"darkSpots\":" + darkSpots + ","
                + "\"spawnableSpots\":" + spawnableSpots + ","
                + "\"darkPercent\":" + (scannedBlocks > 0 ? (darkSpots * 100.0 / scannedBlocks) : 0) + ","
                + "\"spawnablePercent\":" + (scannedBlocks > 0 ? (spawnableSpots * 100.0 / scannedBlocks) : 0) + "}";
        lineConsumer.accept("light_levels.ndjson", summaryLine);

        LOGGER.info("Light scan complete for {}: {} blocks, {} dark, {} spawnable",
                roomId, scannedBlocks, darkSpots, spawnableSpots);
    }

    /**
     * Scan all configured rooms in a dimension for light levels.
     *
     * @param level Server level to scan
     * @param lineConsumer Consumer for each NDJSON line (filename, line)
     */
    public void scanAllRoomsLightLevels(ServerLevel level, BiConsumer<String, String> lineConsumer) {
        String dimensionId = level.dimension().location().toString();
        for (RoomDefinition room : RoomService.INSTANCE.getRoomsInDimension(dimensionId)) {
            scanRoomLightLevels(level, room.id(), lineConsumer);
        }
    }

    /**
     * Get spawnable spots count for a specific room (quick check without full export).
     *
     * @param level Server level to analyze
     * @param roomId Room ID to analyze
     * @return SpawnabilityReport with statistics
     */
    public SpawnabilityReport getSpawnabilityReport(ServerLevel level, String roomId) {
        RoomDefinition room = RoomService.INSTANCE.findRoomById(roomId).orElse(null);
        if (room == null) {
            return new SpawnabilityReport(roomId, 0, 0, 0, 0);
        }

        BlockPos min = room.min();
        BlockPos max = room.max();
        int scannedBlocks = 0;
        int darkSpots = 0;
        int spawnableSpots = 0;
        int totalLight = 0;

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockPos above = Objects.requireNonNull(pos.above());

                    if (!level.getBlockState(above).isAir()) continue;

                    var blockState = level.getBlockState(pos);
                    if (!blockState.isSolidRender(level, pos)) continue;

                    scannedBlocks++;

                    int blockLight = level.getBrightness(LightLayer.BLOCK, Objects.requireNonNull(above));
                    int skyLight = level.getBrightness(LightLayer.SKY, Objects.requireNonNull(above));
                    int combinedLight = Math.max(blockLight, skyLight);

                    totalLight += combinedLight;
                    if (combinedLight <= 7) darkSpots++;
                    if (combinedLight == 0) spawnableSpots++;
                }
            }
        }

        double avgLight = scannedBlocks > 0 ? (double) totalLight / scannedBlocks : 0;
        return new SpawnabilityReport(roomId, scannedBlocks, darkSpots, spawnableSpots, avgLight);
    }

    /**
     * Report class for spawnability analysis.
     */
    public record SpawnabilityReport(String roomId, int totalBlocks, int darkSpots, int spawnableSpots, double avgLight) {
        public double darkPercent() {
            return totalBlocks > 0 ? (darkSpots * 100.0 / totalBlocks) : 0;
        }

        public double spawnablePercent() {
            return totalBlocks > 0 ? (spawnableSpots * 100.0 / totalBlocks) : 0;
        }

        @Override
        public String toString() {
            return String.format("Room: %s | Blocks: %d | Dark: %d (%.1f%%) | Spawnable: %d (%.1f%%) | Avg Light: %.1f",
                    roomId, totalBlocks, darkSpots, darkPercent(), spawnableSpots, spawnablePercent(), avgLight);
        }
    }
}
