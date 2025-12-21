package com.frenkvs.devmod.telemetry.room;

import com.frenkvs.devmod.telemetry.RoomDefinition;
import com.frenkvs.devmod.telemetry.TelemetryConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

/**
 * Central service for room definition management and resolution.
 * Handles loading, storing, and querying room definitions.
 *
 * Extracted from TelemetryService for better separation of concerns.
 * Thread-safe singleton.
 */
public class RoomService {
    public static final RoomService INSTANCE = new RoomService();

    private volatile List<RoomDefinition> rooms = List.of();

    private RoomService() {}

    /**
     * Load room definitions from configuration.
     * Should be called during server start/reload.
     *
     * @param config TelemetryConfig to load rooms from
     */
    public void loadRooms(TelemetryConfig config) {
        this.rooms = config.loadRooms();
    }

    /**
     * Load room definitions directly from a list.
     * Useful for testing or programmatic configuration.
     *
     * @param roomDefinitions List of room definitions
     */
    public void setRooms(List<RoomDefinition> roomDefinitions) {
        this.rooms = roomDefinitions != null ? List.copyOf(roomDefinitions) : List.of();
    }

    /**
     * Get all configured room definitions.
     * Useful for visualization (REQ-A3) and iteration.
     *
     * @return Immutable list of room definitions
     */
    public List<RoomDefinition> getRoomDefinitions() {
        return rooms;
    }

    /**
     * Get room count.
     *
     * @return Number of configured rooms
     */
    public int getRoomCount() {
        return rooms.size();
    }

    /**
     * Resolve a position to a room ID.
     * If position is inside a configured room, returns the room ID.
     * Otherwise, returns a fallback chunk-based identifier.
     *
     * @param level Server level
     * @param pos Block position
     * @return Room ID or chunk-based fallback
     */
    public String resolveRoom(ServerLevel level, BlockPos pos) {
        RoomDefinition match = findRoom(level, pos);
        if (match != null) {
            return match.id();
        }
        // Fallback: dimension:chunk_X_Z
        ResourceLocation dim = level.dimension().location();
        return dim + ":chunk_" + (pos.getX() >> 4) + "_" + (pos.getZ() >> 4);
    }

    /**
     * Find the room definition containing a position.
     *
     * @param level Server level
     * @param pos Block position
     * @return RoomDefinition if found, null otherwise
     */
    public RoomDefinition findRoom(ServerLevel level, BlockPos pos) {
        for (RoomDefinition def : rooms) {
            if (def.contains(level, pos)) {
                return def;
            }
        }
        return null;
    }

    /**
     * Find the room definition containing a position (Optional version).
     *
     * @param level Server level
     * @param pos Block position
     * @return Optional containing RoomDefinition if found
     */
    public Optional<RoomDefinition> findRoomOptional(ServerLevel level, BlockPos pos) {
        return Optional.ofNullable(findRoom(level, pos));
    }

    /**
     * Find a room definition by ID.
     *
     * @param roomId Room identifier
     * @return Optional containing RoomDefinition if found
     */
    public Optional<RoomDefinition> findRoomById(String roomId) {
        if (roomId == null) return Optional.empty();
        for (RoomDefinition def : rooms) {
            if (def.id().equals(roomId)) {
                return Optional.of(def);
            }
        }
        return Optional.empty();
    }

    /**
     * Check if a position is inside any configured room.
     *
     * @param level Server level
     * @param pos Block position
     * @return true if position is inside a room
     */
    public boolean isInRoom(ServerLevel level, BlockPos pos) {
        return findRoom(level, pos) != null;
    }

    /**
     * Get all rooms in a specific dimension.
     *
     * @param dimensionId Dimension resource location as string
     * @return List of rooms in the dimension
     */
    public List<RoomDefinition> getRoomsInDimension(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return List.of();
        }
        return rooms.stream()
            .filter(r -> dimensionId.equals(r.dimension()))
            .toList();
    }

    /**
     * Clear all room definitions.
     * Useful for testing or full reload.
     */
    public void clear() {
        this.rooms = List.of();
    }
}
