package com.devmod.transport.executor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Manages recovery points and area snapshots for transport safety.
 *
 * <p>Features:
 * <ul>
 *   <li>Player recovery points (position + dimension)</li>
 *   <li>Area snapshots using StructureTemplate</li>
 *   <li>Automatic restoration on failure</li>
 *   <li>Timeout-based cleanup</li>
 * </ul>
 *
 * <p>Pattern based on RiftStampManager's snapshot/restore implementation.
 */
public final class RecoveryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryManager.class);

    public static final RecoveryManager INSTANCE = new RecoveryManager();

    /** Default snapshot size (16x24x16). */
    private static final int DEFAULT_FOOTPRINT_SIZE = 16;
    private static final int DEFAULT_SNAPSHOT_HEIGHT = 24;

    /** Placement flags for structure restoration (UPDATE | NO_OBSERVER | NO_PLACE_FUNCTION). */
    private static final int PLACEMENT_FLAGS = 2 | 16 | 64;

    /** Maximum time to keep a recovery point (10 minutes in ticks). */
    private static final long RECOVERY_POINT_TIMEOUT = 20 * 60 * 10;

    /** Player recovery points (safe return positions). */
    private final Map<UUID, RecoveryPoint> playerRecoveryPoints = new ConcurrentHashMap<>();

    /** Area snapshots for restoration. */
    private final Map<UUID, AreaSnapshot> areaSnapshots = new ConcurrentHashMap<>();

    private RecoveryManager() {}

    // =========================================================================
    // Player Recovery Points
    // =========================================================================

    /**
     * Saves a recovery point for a player.
     * This is the position the player can return to if something goes wrong.
     *
     * @param player The player
     * @return The saved recovery point
     */
    @Nonnull
    public RecoveryPoint saveRecoveryPoint(@Nonnull ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        ResourceLocation dimension = level.dimension().location();
        long timestamp = level.getGameTime();

        RecoveryPoint point = new RecoveryPoint(
            player.getUUID(),
            dimension,
            pos,
            player.getYRot(),
            player.getXRot(),
            timestamp
        );

        playerRecoveryPoints.put(player.getUUID(), point);
        LOGGER.debug("[RecoveryManager] Saved recovery point for {} at {} in {}",
            player.getName().getString(), pos, dimension);

        return point;
    }

    /**
     * Gets the recovery point for a player.
     */
    @Nonnull
    public Optional<RecoveryPoint> getRecoveryPoint(@Nonnull UUID playerId) {
        return Optional.ofNullable(playerRecoveryPoints.get(playerId));
    }

    /**
     * Restores a player to their recovery point.
     *
     * @param player The player to restore
     * @return true if restored successfully
     */
    public boolean restorePlayer(@Nonnull ServerPlayer player) {
        RecoveryPoint point = playerRecoveryPoints.remove(player.getUUID());
        if (point == null) {
            LOGGER.warn("[RecoveryManager] No recovery point for {}", player.getName().getString());
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        ServerLevel destLevel = server.getLevel(
            ResourceKey.create(Registries.DIMENSION, point.dimension())
        );
        if (destLevel == null) {
            LOGGER.error("[RecoveryManager] Cannot restore to dimension {}", point.dimension());
            return false;
        }

        // Ensure chunk is loaded
        destLevel.getChunk(point.position());

        // Teleport player back
        player.teleportTo(
            destLevel,
            point.position().getX() + 0.5,
            point.position().getY(),
            point.position().getZ() + 0.5,
            point.yaw(),
            point.pitch()
        );

        LOGGER.info("[RecoveryManager] Restored {} to {} in {}",
            player.getName().getString(), point.position(), point.dimension());

        return true;
    }

    /**
     * Clears the recovery point for a player.
     */
    public void clearRecoveryPoint(@Nonnull UUID playerId) {
        playerRecoveryPoints.remove(playerId);
    }

    /**
     * Checks if a player has a recovery point.
     */
    public boolean hasRecoveryPoint(@Nonnull UUID playerId) {
        return playerRecoveryPoints.containsKey(playerId);
    }

    // =========================================================================
    // Area Snapshots
    // =========================================================================

    /**
     * Takes a snapshot of an area for later restoration.
     *
     * @param snapshotId Unique identifier for this snapshot
     * @param level The server level
     * @param basePos Bottom corner of the area
     * @param size Size of the area to snapshot
     * @return true if snapshot was successful
     */
    public boolean takeSnapshot(
            @Nonnull UUID snapshotId,
            @Nonnull ServerLevel level,
            @Nonnull BlockPos basePos,
            @Nonnull Vec3i size) {

        if (areaSnapshots.containsKey(snapshotId)) {
            LOGGER.warn("[RecoveryManager] Snapshot {} already exists", snapshotId);
            return false;
        }

        // Ensure chunk is loaded
        level.getChunk(basePos);

        try {
            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(level, basePos, size, false, Blocks.AIR);

            AreaSnapshot snapshot = new AreaSnapshot(
                snapshotId,
                level.dimension().location(),
                basePos,
                size,
                template,
                level.getGameTime()
            );

            areaSnapshots.put(snapshotId, snapshot);

            LOGGER.debug("[RecoveryManager] Took snapshot {} at {} ({}x{}x{})",
                snapshotId, basePos, size.getX(), size.getY(), size.getZ());

            return true;
        } catch (Exception e) {
            LOGGER.error("[RecoveryManager] Failed to take snapshot at {}: {}",
                basePos, e.getMessage());
            return false;
        }
    }

    /**
     * Takes a snapshot with default size.
     */
    public boolean takeSnapshot(
            @Nonnull UUID snapshotId,
            @Nonnull ServerLevel level,
            @Nonnull BlockPos basePos) {
        return takeSnapshot(snapshotId, level, basePos,
            new Vec3i(DEFAULT_FOOTPRINT_SIZE, DEFAULT_SNAPSHOT_HEIGHT, DEFAULT_FOOTPRINT_SIZE));
    }

    /**
     * Restores an area from a snapshot.
     *
     * @param snapshotId The snapshot to restore
     * @param server The Minecraft server
     * @return true if restored successfully
     */
    public boolean restoreSnapshot(@Nonnull UUID snapshotId, @Nonnull MinecraftServer server) {
        AreaSnapshot snapshot = areaSnapshots.remove(snapshotId);
        if (snapshot == null) {
            LOGGER.warn("[RecoveryManager] No snapshot found: {}", snapshotId);
            return false;
        }

        ServerLevel level = server.getLevel(
            ResourceKey.create(Registries.DIMENSION, snapshot.dimension())
        );
        if (level == null) {
            LOGGER.error("[RecoveryManager] Cannot restore to dimension {}", snapshot.dimension());
            return false;
        }

        // Ensure chunk is loaded
        level.getChunk(snapshot.basePos());

        try {
            StructurePlaceSettings settings = new StructurePlaceSettings();
            settings.setIgnoreEntities(true);

            snapshot.template().placeInWorld(
                level,
                snapshot.basePos(),
                snapshot.basePos(),
                settings,
                level.getRandom(),
                PLACEMENT_FLAGS
            );

            LOGGER.info("[RecoveryManager] Restored snapshot {} at {}",
                snapshotId, snapshot.basePos());

            return true;
        } catch (Exception e) {
            LOGGER.error("[RecoveryManager] Failed to restore snapshot {}: {}",
                snapshotId, e.getMessage());
            return false;
        }
    }

    /**
     * Discards a snapshot without restoring.
     */
    public void discardSnapshot(@Nonnull UUID snapshotId) {
        if (areaSnapshots.remove(snapshotId) != null) {
            LOGGER.debug("[RecoveryManager] Discarded snapshot {}", snapshotId);
        }
    }

    /**
     * Checks if a snapshot exists.
     */
    public boolean hasSnapshot(@Nonnull UUID snapshotId) {
        return areaSnapshots.containsKey(snapshotId);
    }

    /**
     * Gets snapshot information without removing it.
     */
    @Nonnull
    public Optional<AreaSnapshot> getSnapshot(@Nonnull UUID snapshotId) {
        return Optional.ofNullable(areaSnapshots.get(snapshotId));
    }

    // =========================================================================
    // Maintenance
    // =========================================================================

    /**
     * Cleans up expired recovery points and snapshots.
     * Should be called periodically from server tick.
     */
    public void tick(@Nonnull MinecraftServer server) {
        long currentTick = server.overworld().getGameTime();

        // Clean expired recovery points
        playerRecoveryPoints.entrySet().removeIf(entry -> {
            boolean expired = (currentTick - entry.getValue().timestamp()) > RECOVERY_POINT_TIMEOUT;
            if (expired) {
                LOGGER.debug("[RecoveryManager] Expired recovery point for {}", entry.getKey());
            }
            return expired;
        });

        // Clean expired snapshots
        areaSnapshots.entrySet().removeIf(entry -> {
            boolean expired = (currentTick - entry.getValue().timestamp()) > RECOVERY_POINT_TIMEOUT;
            if (expired) {
                LOGGER.debug("[RecoveryManager] Expired snapshot {}", entry.getKey());
            }
            return expired;
        });
    }

    /**
     * Clears all data (for server shutdown).
     */
    public void clear() {
        playerRecoveryPoints.clear();
        areaSnapshots.clear();
        LOGGER.info("[RecoveryManager] Cleared all recovery data");
    }

    /**
     * Gets the number of active recovery points.
     */
    public int getRecoveryPointCount() {
        return playerRecoveryPoints.size();
    }

    /**
     * Gets the number of active snapshots.
     */
    public int getSnapshotCount() {
        return areaSnapshots.size();
    }

    // =========================================================================
    // Records
    // =========================================================================

    /**
     * A player's recovery point (safe return position).
     */
    public record RecoveryPoint(
        @Nonnull UUID playerId,
        @Nonnull ResourceLocation dimension,
        @Nonnull BlockPos position,
        float yaw,
        float pitch,
        long timestamp
    ) {}

    /**
     * An area snapshot for restoration.
     */
    public record AreaSnapshot(
        @Nonnull UUID snapshotId,
        @Nonnull ResourceLocation dimension,
        @Nonnull BlockPos basePos,
        @Nonnull Vec3i size,
        @Nonnull StructureTemplate template,
        long timestamp
    ) {}
}
