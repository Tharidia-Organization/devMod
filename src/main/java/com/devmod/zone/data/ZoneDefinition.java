package com.devmod.zone.data;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import com.devmod.portal.PortalColor;

/**
 * Immutable definition of a zone within the Nexus.
 * Zones define logical regions with spawn points, visual feedback, and permissions.
 *
 * Note: Zone-Area linking is tracked from AreaDefinition (Area owns Zone relationship).
 */
public record ZoneDefinition(
    @Nonnull UUID id,
    @Nonnull String zoneId,           // Human-readable ID like "combat", "arena"
    @Nonnull String displayName,       // Display name like "Combat Test"
    @Nonnull ZoneBounds bounds,
    @Nullable BlockPos spawnOffset,    // Spawn position relative to hub origin
    int color,                         // ARGB color for UI/particles
    @Nonnull SoundEvent cueSound,      // Sound played when entering zone
    @Nonnull String hintMessage,       // Action bar message when entering
    boolean allowBuilding,             // Whether players can build here
    boolean isSpecial,                 // Special zone (AFK, QUIET, PERFORMANCE)
    boolean hasTeleport,               // Can be target of /devmod nexus go
    @Nullable BlockPos portalOffset,   // Portal position relative to hub origin
    @Nullable PortalColor portalColor, // Portal color if has portal
    int priority,                      // Resolution priority (higher = checked first)
    long createdAt,
    long updatedAt
) {
    // ========================================================================
    // Validation
    // ========================================================================

    public ZoneDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(cueSound, "cueSound");
        Objects.requireNonNull(hintMessage, "hintMessage");

        // Validate zoneId format
        if (!ZoneNaming.isValidZoneId(zoneId)) {
            throw new IllegalArgumentException("Invalid zone ID: " + zoneId);
        }
    }

    // ========================================================================
    // Codecs
    // ========================================================================

    public static final Codec<ZoneDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("id").forGetter(ZoneDefinition::id),
        Codec.STRING.fieldOf("zoneId").forGetter(ZoneDefinition::zoneId),
        Codec.STRING.fieldOf("displayName").forGetter(ZoneDefinition::displayName),
        ZoneBounds.CODEC.fieldOf("bounds").forGetter(ZoneDefinition::bounds),
        BlockPos.CODEC.optionalFieldOf("spawnOffset").forGetter(d -> Optional.ofNullable(d.spawnOffset())),
        Codec.INT.fieldOf("color").forGetter(ZoneDefinition::color),
        BuiltInRegistries.SOUND_EVENT.byNameCodec().fieldOf("cueSound").forGetter(ZoneDefinition::cueSound),
        Codec.STRING.fieldOf("hintMessage").forGetter(ZoneDefinition::hintMessage),
        Codec.BOOL.fieldOf("allowBuilding").forGetter(ZoneDefinition::allowBuilding),
        Codec.BOOL.fieldOf("isSpecial").forGetter(ZoneDefinition::isSpecial),
        Codec.BOOL.fieldOf("hasTeleport").forGetter(ZoneDefinition::hasTeleport),
        BlockPos.CODEC.optionalFieldOf("portalOffset").forGetter(d -> Optional.ofNullable(d.portalOffset())),
        PortalColor.CODEC.optionalFieldOf("portalColor").forGetter(d -> Optional.ofNullable(d.portalColor())),
        Codec.INT.fieldOf("priority").forGetter(ZoneDefinition::priority),
        Codec.LONG.fieldOf("createdAt").forGetter(ZoneDefinition::createdAt),
        Codec.LONG.fieldOf("updatedAt").forGetter(ZoneDefinition::updatedAt)
    ).apply(instance, ZoneDefinition::fromCodec));

    private static ZoneDefinition fromCodec(
            UUID id, String zoneId, String displayName, ZoneBounds bounds,
            Optional<BlockPos> spawnOffset, Integer color, SoundEvent cueSound,
            String hintMessage, Boolean allowBuilding, Boolean isSpecial,
            Boolean hasTeleport, Optional<BlockPos> portalOffset,
            Optional<PortalColor> portalColor, Integer priority, Long createdAt,
            Long updatedAt) {
        return new ZoneDefinition(
            id, zoneId, displayName, bounds,
            spawnOffset.orElse(null),
            Objects.requireNonNull(color).intValue(),
            cueSound, hintMessage,
            Objects.requireNonNull(allowBuilding).booleanValue(),
            Objects.requireNonNull(isSpecial).booleanValue(),
            Objects.requireNonNull(hasTeleport).booleanValue(),
            portalOffset.orElse(null), portalColor.orElse(null),
            Objects.requireNonNull(priority).intValue(),
            Objects.requireNonNull(createdAt).longValue(),
            Objects.requireNonNull(updatedAt).longValue()
        );
    }

    public static final StreamCodec<FriendlyByteBuf, ZoneDefinition> STREAM_CODEC = StreamCodec.of(
        ZoneDefinition::encode,
        ZoneDefinition::decode
    );

    private static void encode(FriendlyByteBuf buf, ZoneDefinition def) {
        buf.writeUUID(def.id);
        buf.writeUtf(def.zoneId);
        buf.writeUtf(def.displayName);
        ZoneBounds.STREAM_CODEC.encode(buf, def.bounds);

        buf.writeBoolean(def.spawnOffset != null);
        if (def.spawnOffset != null) {
            buf.writeBlockPos(def.spawnOffset);
        }

        buf.writeInt(def.color);
        buf.writeResourceLocation(Objects.requireNonNull(BuiltInRegistries.SOUND_EVENT.getKey(def.cueSound)));
        buf.writeUtf(def.hintMessage);
        buf.writeBoolean(def.allowBuilding);
        buf.writeBoolean(def.isSpecial);
        buf.writeBoolean(def.hasTeleport);

        buf.writeBoolean(def.portalOffset != null);
        if (def.portalOffset != null) {
            buf.writeBlockPos(def.portalOffset);
        }

        buf.writeBoolean(def.portalColor != null);
        if (def.portalColor != null) {
            buf.writeEnum(def.portalColor);
        }

        buf.writeInt(def.priority);
        buf.writeLong(def.createdAt);
        buf.writeLong(def.updatedAt);
    }

    private static ZoneDefinition decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String zoneId = buf.readUtf();
        String displayName = buf.readUtf();
        ZoneBounds bounds = ZoneBounds.STREAM_CODEC.decode(buf);

        BlockPos spawnOffset = buf.readBoolean() ? buf.readBlockPos() : null;
        int color = buf.readInt();
        SoundEvent cueSound = BuiltInRegistries.SOUND_EVENT.get(buf.readResourceLocation());
        String hintMessage = buf.readUtf();
        boolean allowBuilding = buf.readBoolean();
        boolean isSpecial = buf.readBoolean();
        boolean hasTeleport = buf.readBoolean();

        BlockPos portalOffset = buf.readBoolean() ? buf.readBlockPos() : null;
        PortalColor portalColor = buf.readBoolean() ? buf.readEnum(PortalColor.class) : null;

        int priority = buf.readInt();
        long createdAt = buf.readLong();
        long updatedAt = buf.readLong();

        return new ZoneDefinition(
            id, zoneId, displayName, bounds, spawnOffset, color,
            cueSound != null ? cueSound : SoundEvents.NOTE_BLOCK_CHIME.value(),
            hintMessage, allowBuilding, isSpecial, hasTeleport,
            portalOffset, portalColor, priority, createdAt, updatedAt
        );
    }

    // ========================================================================
    // Convenience Methods
    // ========================================================================

    /**
     * Gets the display name as a colored Component.
     */
    @Nonnull
    public Component getDisplayComponent() {
        return Objects.requireNonNull(Component.literal(displayName)
            .withStyle(Objects.requireNonNull(Style.EMPTY.withColor(color))));
    }

    /**
     * Checks if a position is inside this zone.
     */
    public boolean containsPosition(@Nonnull BlockPos pos) {
        return bounds.contains(pos);
    }

    /**
     * Gets the absolute spawn position given a hub origin.
     */
    @Nullable
    public BlockPos getAbsoluteSpawn(@Nonnull BlockPos hubOrigin) {
        return spawnOffset != null ? hubOrigin.offset(spawnOffset) : null;
    }

    /**
     * Gets the absolute portal position given a hub origin.
     */
    @Nullable
    public BlockPos getAbsolutePortal(@Nonnull BlockPos hubOrigin) {
        return portalOffset != null ? hubOrigin.offset(portalOffset) : null;
    }

    // ========================================================================
    // Builder
    // ========================================================================

    /**
     * Creates a builder for a new zone.
     */
    @Nonnull
    public static Builder builder(@Nonnull String zoneId) {
        return new Builder(zoneId);
    }

    /**
     * Creates a builder from an existing zone (for updates).
     */
    @Nonnull
    public Builder toBuilder() {
        return new Builder(this);
    }

    public static class Builder {
        private UUID id;
        private final String zoneId;
        private String displayName;
        @Nullable private ZoneBounds bounds;
        @Nullable private BlockPos spawnOffset;
        private int color = 0x00D4AA; // Default cyan
        private SoundEvent cueSound = SoundEvents.NOTE_BLOCK_CHIME.value();
        private String hintMessage = "";
        private boolean allowBuilding = false;
        private boolean isSpecial = false;
        private boolean hasTeleport = true;
        @Nullable private BlockPos portalOffset;
        @Nullable private PortalColor portalColor;
        private int priority = 0;
        private long createdAt;
        private long updatedAt;

        private Builder(@Nonnull String zoneId) {
            this.id = UUID.randomUUID();
            this.zoneId = Objects.requireNonNull(zoneId);
            this.displayName = zoneId;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = this.createdAt;
        }

        private Builder(@Nonnull ZoneDefinition existing) {
            this.id = existing.id;
            this.zoneId = existing.zoneId;
            this.displayName = existing.displayName;
            this.bounds = existing.bounds;
            this.spawnOffset = existing.spawnOffset;
            this.color = existing.color;
            this.cueSound = existing.cueSound;
            this.hintMessage = existing.hintMessage;
            this.allowBuilding = existing.allowBuilding;
            this.isSpecial = existing.isSpecial;
            this.hasTeleport = existing.hasTeleport;
            this.portalOffset = existing.portalOffset;
            this.portalColor = existing.portalColor;
            this.priority = existing.priority;
            this.createdAt = existing.createdAt;
            this.updatedAt = System.currentTimeMillis();
        }

        public Builder id(@Nonnull UUID id) { this.id = id; return this; }
        public Builder displayName(@Nonnull String displayName) { this.displayName = displayName; return this; }
        public Builder bounds(@Nonnull ZoneBounds bounds) { this.bounds = bounds; return this; }
        public Builder spawnOffset(@Nullable BlockPos spawnOffset) { this.spawnOffset = spawnOffset; return this; }
        public Builder color(int color) { this.color = color; return this; }
        public Builder cueSound(@Nonnull SoundEvent cueSound) { this.cueSound = cueSound; return this; }
        public Builder hintMessage(@Nonnull String hintMessage) { this.hintMessage = hintMessage; return this; }
        public Builder allowBuilding(boolean allowBuilding) { this.allowBuilding = allowBuilding; return this; }
        public Builder isSpecial(boolean isSpecial) { this.isSpecial = isSpecial; return this; }
        public Builder hasTeleport(boolean hasTeleport) { this.hasTeleport = hasTeleport; return this; }
        public Builder portalOffset(@Nullable BlockPos portalOffset) { this.portalOffset = portalOffset; return this; }
        public Builder portalColor(@Nullable PortalColor portalColor) { this.portalColor = portalColor; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }

        @Nonnull
        public ZoneDefinition build() {
            Objects.requireNonNull(bounds, "bounds must be set");
            return new ZoneDefinition(
                id, zoneId, displayName, bounds, spawnOffset, color,
                cueSound, hintMessage, allowBuilding, isSpecial, hasTeleport,
                portalOffset, portalColor, priority, createdAt, updatedAt
            );
        }
    }
}
