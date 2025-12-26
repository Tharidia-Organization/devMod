package com.devmod.arena.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
public record ArenaIdentity(
    UUID arenaId,
    String templateId,
    Instant createdAt,
    String region,
    RoomIdentifier roomIdentifier
) {
    public ArenaIdentity {
        Objects.requireNonNull(arenaId, "arenaId must not be null");
        Objects.requireNonNull(templateId, "templateId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(region, "region must not be null");
        Objects.requireNonNull(roomIdentifier, "roomIdentifier must not be null");
    }

    /**
     * Returns the roomId (alias for arenaId for legacy compatibility).
     */
    public UUID roomId() {
        return arenaId;
    }

    /**
     * DD58: Returns the formatted room identifier string.
     * Format: "srv-{serverId}-wld-{worldId}-inst-{instanceId}"
     */
    public String formattedRoomId() {
        return roomIdentifier.format();
    }

    /**
     * Creates a new ArenaIdentity with current timestamp.
     */
    public static ArenaIdentity create(UUID arenaId, String templateId, String region,
                                       String serverId, String worldId, String instanceId) {
        RoomIdentifier roomId = new RoomIdentifier(serverId, worldId, instanceId);
        return new ArenaIdentity(arenaId, templateId, Instant.now(), region, roomId);
    }

    /**
     * Creates a new ArenaIdentity with a random UUID.
     */
    public static ArenaIdentity createNew(String templateId, String region,
                                          String serverId, String worldId) {
        String instanceId = UUID.randomUUID().toString().substring(0, 8);
        RoomIdentifier roomId = new RoomIdentifier(serverId, worldId, instanceId);
        return new ArenaIdentity(UUID.randomUUID(), templateId, Instant.now(), region, roomId);
    }

    /**
     * DD58: Room Identifier with hierarchical structure.
     * Format: "srv-{serverId}-wld-{worldId}-inst-{instanceId}"
     */
    public record RoomIdentifier(
        String serverId,
        String worldId,
        String instanceId
    ) {
        public RoomIdentifier {
            Objects.requireNonNull(serverId, "serverId must not be null");
            Objects.requireNonNull(worldId, "worldId must not be null");
            Objects.requireNonNull(instanceId, "instanceId must not be null");

            if (serverId.isBlank()) {
                throw new IllegalArgumentException("serverId must not be blank");
            }
            if (worldId.isBlank()) {
                throw new IllegalArgumentException("worldId must not be blank");
            }
            if (instanceId.isBlank()) {
                throw new IllegalArgumentException("instanceId must not be blank");
            }
        }

        /**
         * DD58: Format as "srv-{server}-wld-{world}-inst-{instance}".
         */
        public String format() {
            return String.format("srv-%s-wld-%s-inst-%s", serverId, worldId, instanceId);
        }

        /**
         * DD58: Parse from formatted string.
         */
        public static RoomIdentifier parse(String formatted) {
            if (formatted == null || !formatted.startsWith("srv-")) {
                throw new IllegalArgumentException("Invalid room identifier format: " + formatted);
            }

            String[] parts = formatted.split("-");
            if (parts.length < 6) {
                throw new IllegalArgumentException("Invalid room identifier format: " + formatted);
            }

            // Expected: srv-{server}-wld-{world}-inst-{instance}
            String serverId = parts[1];
            String worldId = parts[3];
            String instanceId = parts[5];

            return new RoomIdentifier(serverId, worldId, instanceId);
        }

        @Override
        public String toString() {
            return format();
        }
    }
}
