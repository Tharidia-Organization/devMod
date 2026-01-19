package com.devmod.nexus.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import com.devmod.portal.PortalColor;
import com.devmod.zone.data.ZoneBounds;

/**
 * Represents a zone slot in the Nexus hub.
 * A slot is a physical location that can be linked to an AreaDefinition.
 *
 * <p>Design:
 * <ul>
 *   <li>Slots define WHERE content can be placed (fixed positions)</li>
 *   <li>Areas define WHAT content is (via Area module)</li>
 *   <li>Linking an area to a slot builds that area's content at the slot's position</li>
 * </ul>
 */
public record ZoneSlot(
    String slotId,                    // Unique ID: "spawn", "quest_north", "economia_west"
    String displayName,               // Display name: "Spawn", "Quest Hub Nord"
    ZoneBounds bounds,                // Physical bounds (uses existing ZoneBounds)
    @Nullable UUID linkedAreaId,      // Link to AreaDefinition (null = empty slot)
    @Nullable UUID linkedZoneId,      // Link to ZoneDefinition (for tracking)
    SlotType type,                    // FIXED, EDITABLE, TEST_AREA, RESTRICTED
    SlotPermissions permissions,      // Who can do what
    BlockPos portalPosition,          // Portal position (relative to bounds.min)
    PortalColor portalColor,          // Portal color (uses existing PortalColor)
    @Nullable String templateId,      // Default template to apply (null = none)
    int priority,                     // Priority for overlap resolution (higher = wins)
    long createdAt,                   // Creation timestamp
    long updatedAt,                   // Last modification timestamp
    // === NEW FIELDS (Step 1.3) ===
    @Nullable String warpNodeId,      // Link to TransportRegistry warp node (null = none)
    List<BlockPos> npcSpawnPoints,    // NPC spawn positions (relative to bounds.min), empty = none
    @Nullable BlockPos hologramPosition // Hologram display position (relative to bounds.min)
) {

    // ========================================================================
    // Codecs
    // ========================================================================

    /**
     * Codec for NBT/JSON serialization.
     * Note: Uses 16 fields (RecordCodecBuilder max is 16).
     */
    public static final Codec<ZoneSlot> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("slotId").forGetter(ZoneSlot::slotId),
            Codec.STRING.fieldOf("displayName").forGetter(ZoneSlot::displayName),
            ZoneBounds.CODEC.fieldOf("bounds").forGetter(ZoneSlot::bounds),
            UUIDUtil.STRING_CODEC.optionalFieldOf("linkedAreaId").forGetter(s -> Optional.ofNullable(s.linkedAreaId())),
            UUIDUtil.STRING_CODEC.optionalFieldOf("linkedZoneId").forGetter(s -> Optional.ofNullable(s.linkedZoneId())),
            Codec.STRING.xmap(SlotType::valueOf, SlotType::name).fieldOf("type").forGetter(ZoneSlot::type),
            SlotPermissions.CODEC.fieldOf("permissions").forGetter(ZoneSlot::permissions),
            BlockPos.CODEC.fieldOf("portalPosition").forGetter(ZoneSlot::portalPosition),
            PortalColor.CODEC.fieldOf("portalColor").forGetter(ZoneSlot::portalColor),
            Codec.STRING.optionalFieldOf("templateId").forGetter(s -> Optional.ofNullable(s.templateId())),
            Codec.INT.fieldOf("priority").forGetter(ZoneSlot::priority),
            Codec.LONG.fieldOf("createdAt").forGetter(ZoneSlot::createdAt),
            Codec.LONG.fieldOf("updatedAt").forGetter(ZoneSlot::updatedAt),
            // New fields (Step 1.3)
            Codec.STRING.optionalFieldOf("warpNodeId").forGetter(s -> Optional.ofNullable(s.warpNodeId())),
            BlockPos.CODEC.listOf().optionalFieldOf("npcSpawnPoints", List.of()).forGetter(ZoneSlot::npcSpawnPoints),
            BlockPos.CODEC.optionalFieldOf("hologramPosition").forGetter(s -> Optional.ofNullable(s.hologramPosition()))
        ).apply(instance, (id, name, bounds, areaOpt, zoneOpt, type, perms, portal, color, templateOpt, priority, created, updated, warpOpt, npcPoints, holoOpt) ->
            new ZoneSlot(id, name, bounds, areaOpt.orElse(null), zoneOpt.orElse(null),
                type, perms, portal, color, templateOpt.orElse(null), priority, created, updated,
                warpOpt.orElse(null), npcPoints, holoOpt.orElse(null))
        )
    );

    /**
     * Stream codec for network serialization.
     * Uses manual encoding since StreamCodec.composite only supports up to 6 fields.
     */
    public static final StreamCodec<FriendlyByteBuf, ZoneSlot> STREAM_CODEC = StreamCodec.of(
        (buf, slot) -> {
            buf.writeUtf(Objects.requireNonNull(slot.slotId));
            buf.writeUtf(Objects.requireNonNull(slot.displayName));
            ZoneBounds.STREAM_CODEC.encode(buf, Objects.requireNonNull(slot.bounds));
            buf.writeBoolean(slot.linkedAreaId != null);
            if (slot.linkedAreaId != null) {
                buf.writeUUID(slot.linkedAreaId);
            }
            buf.writeBoolean(slot.linkedZoneId != null);
            if (slot.linkedZoneId != null) {
                buf.writeUUID(slot.linkedZoneId);
            }
            buf.writeUtf(Objects.requireNonNull(slot.type.name()));
            SlotPermissions.STREAM_CODEC.encode(buf, Objects.requireNonNull(slot.permissions));
            buf.writeBlockPos(Objects.requireNonNull(slot.portalPosition));
            PortalColor.STREAM_CODEC.encode(buf, Objects.requireNonNull(slot.portalColor));
            buf.writeBoolean(slot.templateId != null);
            if (slot.templateId != null) {
                buf.writeUtf(slot.templateId);
            }
            buf.writeVarInt(slot.priority);
            buf.writeLong(slot.createdAt);
            buf.writeLong(slot.updatedAt);
            // New fields (Step 1.3)
            buf.writeBoolean(slot.warpNodeId != null);
            if (slot.warpNodeId != null) {
                buf.writeUtf(slot.warpNodeId);
            }
            buf.writeVarInt(slot.npcSpawnPoints.size());
            for (BlockPos pos : slot.npcSpawnPoints) {
                buf.writeBlockPos(Objects.requireNonNull(pos));
            }
            buf.writeBoolean(slot.hologramPosition != null);
            if (slot.hologramPosition != null) {
                buf.writeBlockPos(slot.hologramPosition);
            }
        },
        buf -> {
            String slotId = buf.readUtf();
            String displayName = buf.readUtf();
            ZoneBounds bounds = ZoneBounds.STREAM_CODEC.decode(buf);
            UUID linkedAreaId = buf.readBoolean() ? buf.readUUID() : null;
            UUID linkedZoneId = buf.readBoolean() ? buf.readUUID() : null;
            SlotType type = SlotType.valueOf(buf.readUtf());
            SlotPermissions permissions = SlotPermissions.STREAM_CODEC.decode(buf);
            BlockPos portalPosition = buf.readBlockPos();
            PortalColor portalColor = PortalColor.STREAM_CODEC.decode(buf);
            String templateId = buf.readBoolean() ? buf.readUtf() : null;
            int priority = buf.readVarInt();
            long createdAt = buf.readLong();
            long updatedAt = buf.readLong();
            // New fields (Step 1.3)
            String warpNodeId = buf.readBoolean() ? buf.readUtf() : null;
            int npcCount = buf.readVarInt();
            List<BlockPos> npcSpawnPoints = new ArrayList<>(npcCount);
            for (int i = 0; i < npcCount; i++) {
                npcSpawnPoints.add(Objects.requireNonNull(buf.readBlockPos()));
            }
            BlockPos hologramPosition = buf.readBoolean() ? buf.readBlockPos() : null;
            return new ZoneSlot(slotId, displayName, bounds, linkedAreaId, linkedZoneId,
                type, permissions, portalPosition, portalColor, templateId, priority, createdAt, updatedAt,
                warpNodeId, npcSpawnPoints, hologramPosition);
        }
    );

    // ========================================================================
    // Validation
    // ========================================================================

    /**
     * Validates this slot's data.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public ZoneSlot {
        Objects.requireNonNull(slotId, "slotId cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(bounds, "bounds cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(permissions, "permissions cannot be null");
        Objects.requireNonNull(portalPosition, "portalPosition cannot be null");
        Objects.requireNonNull(portalColor, "portalColor cannot be null");
        // npcSpawnPoints must never be null - use empty list instead
        npcSpawnPoints = npcSpawnPoints != null ? List.copyOf(npcSpawnPoints) : List.of();

        if (slotId.isBlank()) {
            throw new IllegalArgumentException("slotId cannot be blank");
        }
        if (!bounds.isValid()) {
            throw new IllegalArgumentException("bounds must be valid (min <= max)");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be non-negative");
        }
    }

    // ========================================================================
    // Query Methods
    // ========================================================================

    /**
     * Check if this slot has a linked area.
     */
    public boolean hasLinkedArea() {
        return linkedAreaId != null;
    }

    /**
     * Check if this slot has a linked zone.
     */
    public boolean hasLinkedZone() {
        return linkedZoneId != null;
    }

    /**
     * Check if this slot is empty (no linked area).
     */
    public boolean isEmpty() {
        return linkedAreaId == null;
    }

    /**
     * Check if this slot is editable by players.
     */
    public boolean isPlayerEditable() {
        return type.isPlayerEditable();
    }

    /**
     * Check if this slot is removable.
     */
    public boolean isRemovable() {
        return type.isRemovable();
    }

    /**
     * Get the absolute portal position in world coordinates.
     */
    @Nonnull
    public BlockPos getAbsolutePortalPosition() {
        return new BlockPos(
            bounds.minX() + portalPosition.getX(),
            bounds.minY() + portalPosition.getY(),
            bounds.minZ() + portalPosition.getZ()
        );
    }

    /**
     * Check if a position is within this slot's bounds.
     */
    public boolean contains(@Nonnull BlockPos pos) {
        return bounds.contains(Objects.requireNonNull(pos));
    }

    /**
     * Check if this slot overlaps with another.
     */
    public boolean overlaps(@Nonnull ZoneSlot other) {
        return bounds.overlaps(Objects.requireNonNull(Objects.requireNonNull(other).bounds()));
    }

    // ========================================================================
    // Builder Methods (immutable updates)
    // ========================================================================

    /**
     * Create a copy with linked area.
     */
    @Nonnull
    public ZoneSlot withLinkedArea(@Nullable UUID areaId) {
        return new ZoneSlot(
            slotId, displayName, bounds, areaId, linkedZoneId,
            type, permissions, portalPosition, portalColor, templateId, priority,
            createdAt, System.currentTimeMillis(),
            warpNodeId, npcSpawnPoints, hologramPosition
        );
    }

    /**
     * Create a copy with linked zone.
     */
    @Nonnull
    public ZoneSlot withLinkedZone(@Nullable UUID zoneId) {
        return new ZoneSlot(
            slotId, displayName, bounds, linkedAreaId, zoneId,
            type, permissions, portalPosition, portalColor, templateId, priority,
            createdAt, System.currentTimeMillis(),
            warpNodeId, npcSpawnPoints, hologramPosition
        );
    }

    /**
     * Create a copy with updated permissions.
     */
    @Nonnull
    public ZoneSlot withPermissions(@Nonnull SlotPermissions newPermissions) {
        return new ZoneSlot(
            slotId, displayName, bounds, linkedAreaId, linkedZoneId,
            type, Objects.requireNonNull(newPermissions), portalPosition, portalColor, templateId, priority,
            createdAt, System.currentTimeMillis(),
            warpNodeId, npcSpawnPoints, hologramPosition
        );
    }

    /**
     * Create a copy with updated type (also updates permissions).
     */
    @Nonnull
    public ZoneSlot withType(@Nonnull SlotType newType) {
        return new ZoneSlot(
            slotId, displayName, bounds, linkedAreaId, linkedZoneId,
            Objects.requireNonNull(newType), SlotPermissions.forSlotType(newType),
            portalPosition, portalColor, templateId, priority,
            createdAt, System.currentTimeMillis(),
            warpNodeId, npcSpawnPoints, hologramPosition
        );
    }

    /**
     * Create a copy with updated display name.
     */
    @Nonnull
    public ZoneSlot withDisplayName(@Nonnull String newName) {
        return new ZoneSlot(
            slotId, Objects.requireNonNull(newName), bounds, linkedAreaId, linkedZoneId,
            type, permissions, portalPosition, portalColor, templateId, priority,
            createdAt, System.currentTimeMillis(),
            warpNodeId, npcSpawnPoints, hologramPosition
        );
    }

    /**
     * Create a copy with updated template.
     */
    @Nonnull
    public ZoneSlot withTemplate(@Nullable String newTemplateId) {
        return new ZoneSlot(
            slotId, displayName, bounds, linkedAreaId, linkedZoneId,
            type, permissions, portalPosition, portalColor, newTemplateId, priority,
            createdAt, System.currentTimeMillis(),
            warpNodeId, npcSpawnPoints, hologramPosition
        );
    }

    /**
     * Create a copy with cleared links (unlink).
     */
    @Nonnull
    public ZoneSlot unlinked() {
        return new ZoneSlot(
            slotId, displayName, bounds, null, null,
            type, permissions, portalPosition, portalColor, templateId, priority,
            createdAt, System.currentTimeMillis(),
            warpNodeId, npcSpawnPoints, hologramPosition
        );
    }

    // ========================================================================
    // Factory Methods
    // ========================================================================

    /**
     * Create a new slot with default values.
     *
     * @param slotId unique identifier
     * @param displayName human-readable name
     * @param bounds physical bounds
     * @param type slot type
     * @param portalColor portal color
     * @return new ZoneSlot
     */
    @Nonnull
    public static ZoneSlot create(
            @Nonnull String slotId,
            @Nonnull String displayName,
            @Nonnull ZoneBounds bounds,
            @Nonnull SlotType type,
            @Nonnull PortalColor portalColor
    ) {
        long now = System.currentTimeMillis();
        // Default portal position: center of bounds at floor level
        BlockPos portalPos = new BlockPos(
            bounds.width() / 2,
            1, // One block above floor
            bounds.length() / 2
        );
        return new ZoneSlot(
            Objects.requireNonNull(slotId),
            Objects.requireNonNull(displayName),
            Objects.requireNonNull(bounds),
            null, // no linked area
            null, // no linked zone
            Objects.requireNonNull(type),
            SlotPermissions.forSlotType(type),
            portalPos,
            Objects.requireNonNull(portalColor),
            null, // no template
            0, // default priority
            now,
            now,
            null, // no warp node
            List.of(), // no NPC spawn points
            null // no hologram position
        );
    }

    /**
     * Create a new slot with full parameters.
     *
     * @param slotId unique identifier
     * @param displayName human-readable name
     * @param bounds physical bounds
     * @param type slot type
     * @param portalPosition relative portal position
     * @param portalColor portal color
     * @param templateId optional template
     * @param priority overlap priority
     * @return new ZoneSlot
     */
    @Nonnull
    public static ZoneSlot createFull(
            @Nonnull String slotId,
            @Nonnull String displayName,
            @Nonnull ZoneBounds bounds,
            @Nonnull SlotType type,
            @Nonnull BlockPos portalPosition,
            @Nonnull PortalColor portalColor,
            @Nullable String templateId,
            int priority
    ) {
        long now = System.currentTimeMillis();
        return new ZoneSlot(
            Objects.requireNonNull(slotId),
            Objects.requireNonNull(displayName),
            Objects.requireNonNull(bounds),
            null,
            null,
            Objects.requireNonNull(type),
            SlotPermissions.forSlotType(type),
            Objects.requireNonNull(portalPosition),
            Objects.requireNonNull(portalColor),
            templateId,
            Math.max(0, priority),
            now,
            now,
            null, // no warp node
            List.of(), // no NPC spawn points
            null // no hologram position
        );
    }

    // ========================================================================
    // New Builder Methods (Step 1.3)
    // ========================================================================

    /**
     * Create a copy with updated warp node ID.
     */
    @Nonnull
    public ZoneSlot withWarpNodeId(@Nullable String newWarpNodeId) {
        return new ZoneSlot(
            slotId, displayName, bounds, linkedAreaId, linkedZoneId,
            type, permissions, portalPosition, portalColor, templateId, priority,
            createdAt, System.currentTimeMillis(),
            newWarpNodeId, npcSpawnPoints, hologramPosition
        );
    }

    /**
     * Create a copy with updated NPC spawn points.
     */
    @Nonnull
    public ZoneSlot withNpcSpawnPoints(@Nonnull List<BlockPos> newNpcSpawnPoints) {
        return new ZoneSlot(
            slotId, displayName, bounds, linkedAreaId, linkedZoneId,
            type, permissions, portalPosition, portalColor, templateId, priority,
            createdAt, System.currentTimeMillis(),
            warpNodeId, Objects.requireNonNull(newNpcSpawnPoints), hologramPosition
        );
    }

    /**
     * Create a copy with updated hologram position.
     */
    @Nonnull
    public ZoneSlot withHologramPosition(@Nullable BlockPos newHologramPosition) {
        return new ZoneSlot(
            slotId, displayName, bounds, linkedAreaId, linkedZoneId,
            type, permissions, portalPosition, portalColor, templateId, priority,
            createdAt, System.currentTimeMillis(),
            warpNodeId, npcSpawnPoints, newHologramPosition
        );
    }

    // ========================================================================
    // New Query Methods (Step 1.3)
    // ========================================================================

    /**
     * Check if this slot has a warp node linked.
     */
    public boolean hasWarpNode() {
        return warpNodeId != null;
    }

    /**
     * Check if this slot has NPC spawn points.
     */
    public boolean hasNpcSpawnPoints() {
        return !npcSpawnPoints.isEmpty();
    }

    /**
     * Check if this slot has a hologram position.
     */
    public boolean hasHologramPosition() {
        return hologramPosition != null;
    }

    /**
     * Get the absolute NPC spawn positions in world coordinates.
     */
    @Nonnull
    public List<BlockPos> getAbsoluteNpcSpawnPositions() {
        if (npcSpawnPoints.isEmpty()) {
            return Objects.requireNonNull(List.of());
        }
        List<BlockPos> absolute = new ArrayList<>(npcSpawnPoints.size());
        for (BlockPos relPos : npcSpawnPoints) {
            absolute.add(new BlockPos(
                bounds.minX() + relPos.getX(),
                bounds.minY() + relPos.getY(),
                bounds.minZ() + relPos.getZ()
            ));
        }
        return Objects.requireNonNull(Collections.unmodifiableList(absolute));
    }

    /**
     * Get the absolute hologram position in world coordinates.
     */
    @Nullable
    public BlockPos getAbsoluteHologramPosition() {
        BlockPos holo = hologramPosition;
        if (holo == null) {
            return null;
        }
        return new BlockPos(
            bounds.minX() + holo.getX(),
            bounds.minY() + holo.getY(),
            bounds.minZ() + holo.getZ()
        );
    }
}
