package com.devmod.npc.data;

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
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Immutable record containing complete NPC configuration.
 * Stores identity, appearance, behavior, and persistence data.
 */
public record NpcConfiguration(
    @Nonnull UUID id,
    @Nonnull String displayName,
    @Nullable UUID skinPlayerUUID,
    @Nullable String skinPlayerName,
    @Nonnull NpcBehavior behavior,
    @Nonnull NpcAppearance appearance,
    @Nullable String dialogSetId,
    @Nonnull UUID ownerUUID,
    @Nullable BlockPos spawnPosition,
    @Nullable ResourceKey<Level> dimension,
    long createdAt,
    long updatedAt,
    int revision
) {
    public static final int MAX_DISPLAY_NAME_LENGTH = 64;
    public static final int MAX_SKIN_NAME_LENGTH = 32;
    public static final int MAX_DIALOG_SET_ID_LENGTH = 64;

    public static final Codec<NpcConfiguration> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(NpcConfiguration::id),
            Codec.STRING.fieldOf("displayName").forGetter(NpcConfiguration::displayName),
            UUIDUtil.CODEC.optionalFieldOf("skinPlayerUUID").forGetter(c -> Optional.ofNullable(c.skinPlayerUUID())),
            Codec.STRING.optionalFieldOf("skinPlayerName").forGetter(c -> Optional.ofNullable(c.skinPlayerName())),
            NpcBehavior.CODEC.fieldOf("behavior").forGetter(NpcConfiguration::behavior),
            NpcAppearance.CODEC.fieldOf("appearance").forGetter(NpcConfiguration::appearance),
            Codec.STRING.optionalFieldOf("dialogSetId").forGetter(c -> Optional.ofNullable(c.dialogSetId())),
            UUIDUtil.CODEC.fieldOf("ownerUUID").forGetter(NpcConfiguration::ownerUUID),
            BlockPos.CODEC.optionalFieldOf("spawnPosition").forGetter(c -> Optional.ofNullable(c.spawnPosition())),
            Level.RESOURCE_KEY_CODEC.optionalFieldOf("dimension").forGetter(c -> Optional.ofNullable(c.dimension())),
            Codec.LONG.fieldOf("createdAt").forGetter(NpcConfiguration::createdAt),
            Codec.LONG.fieldOf("updatedAt").forGetter(NpcConfiguration::updatedAt),
            Codec.INT.fieldOf("revision").forGetter(NpcConfiguration::revision)
        ).apply(instance, NpcConfiguration::fromCodec)
    );

    private static NpcConfiguration fromCodec(
            UUID id, String displayName, Optional<UUID> skinUUID, Optional<String> skinName,
            NpcBehavior behavior, NpcAppearance appearance, Optional<String> dialogSetId,
            UUID ownerUUID, Optional<BlockPos> spawnPos, Optional<ResourceKey<Level>> dimension,
            Long createdAt, Long updatedAt, Integer revision) {
        return new NpcConfiguration(
            Objects.requireNonNull(id),
            Objects.requireNonNull(displayName),
            skinUUID.orElse(null),
            skinName.orElse(null),
            Objects.requireNonNull(behavior),
            Objects.requireNonNull(appearance),
            dialogSetId.orElse(null),
            Objects.requireNonNull(ownerUUID),
            spawnPos.orElse(null),
            dimension.orElse(null),
            Objects.requireNonNull(createdAt).longValue(),
            Objects.requireNonNull(updatedAt).longValue(),
            Objects.requireNonNull(revision).intValue()
        );
    }

    public static final StreamCodec<FriendlyByteBuf, NpcConfiguration> STREAM_CODEC = StreamCodec.of(
        (buf, data) -> {
            buf.writeUUID(data.id);
            buf.writeUtf(truncate(data.displayName, MAX_DISPLAY_NAME_LENGTH), MAX_DISPLAY_NAME_LENGTH);
            buf.writeBoolean(data.skinPlayerUUID != null);
            if (data.skinPlayerUUID != null) {
                buf.writeUUID(data.skinPlayerUUID);
            }
            buf.writeBoolean(data.skinPlayerName != null);
            if (data.skinPlayerName != null) {
                buf.writeUtf(truncate(data.skinPlayerName, MAX_SKIN_NAME_LENGTH), MAX_SKIN_NAME_LENGTH);
            }
            NpcBehavior.STREAM_CODEC.encode(buf, data.behavior);
            NpcAppearance.STREAM_CODEC.encode(buf, data.appearance);
            buf.writeBoolean(data.dialogSetId != null);
            if (data.dialogSetId != null) {
                buf.writeUtf(truncate(data.dialogSetId, MAX_DIALOG_SET_ID_LENGTH), MAX_DIALOG_SET_ID_LENGTH);
            }
            buf.writeUUID(data.ownerUUID);
            buf.writeBoolean(data.spawnPosition != null);
            if (data.spawnPosition != null) {
                buf.writeBlockPos(data.spawnPosition);
            }
            buf.writeBoolean(data.dimension != null);
            if (data.dimension != null) {
                buf.writeResourceLocation(Objects.requireNonNull(data.dimension.location()));
            }
            buf.writeLong(data.createdAt);
            buf.writeLong(data.updatedAt);
            buf.writeVarInt(data.revision);
        },
        buf -> {
            UUID id = Objects.requireNonNull(buf.readUUID());
            String displayName = Objects.requireNonNull(buf.readUtf(MAX_DISPLAY_NAME_LENGTH));
            UUID skinUUID = buf.readBoolean() ? buf.readUUID() : null;
            String skinName = buf.readBoolean() ? buf.readUtf(MAX_SKIN_NAME_LENGTH) : null;
            NpcBehavior behavior = Objects.requireNonNull(NpcBehavior.STREAM_CODEC.decode(buf));
            NpcAppearance appearance = Objects.requireNonNull(NpcAppearance.STREAM_CODEC.decode(buf));
            String dialogSetId = buf.readBoolean() ? buf.readUtf(MAX_DIALOG_SET_ID_LENGTH) : null;
            UUID ownerUUID = Objects.requireNonNull(buf.readUUID());
            BlockPos spawnPos = buf.readBoolean() ? buf.readBlockPos() : null;
            ResourceKey<Level> dimension = buf.readBoolean()
                ? ResourceKey.create(Objects.requireNonNull(net.minecraft.core.registries.Registries.DIMENSION),
                    Objects.requireNonNull(buf.readResourceLocation()))
                : null;
            long createdAt = buf.readLong();
            long updatedAt = buf.readLong();
            int revision = buf.readVarInt();
            return new NpcConfiguration(id, displayName, skinUUID, skinName, behavior, appearance,
                dialogSetId, ownerUUID, spawnPos, dimension, createdAt, updatedAt, revision);
        }
    );

    public NpcConfiguration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(behavior, "behavior");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(ownerUUID, "ownerUUID");
        displayName = truncate(displayName, MAX_DISPLAY_NAME_LENGTH);
        if (skinPlayerName != null) {
            skinPlayerName = truncate(skinPlayerName, MAX_SKIN_NAME_LENGTH);
        }
        if (dialogSetId != null) {
            dialogSetId = truncate(dialogSetId, MAX_DIALOG_SET_ID_LENGTH);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * Creates a new NPC configuration with default settings.
     *
     * @param displayName The display name for the NPC
     * @param ownerUUID   The UUID of the player creating this NPC
     * @return A new NpcConfiguration with default behavior and appearance
     */
    @Nonnull
    public static NpcConfiguration createDefault(@Nonnull String displayName, @Nonnull UUID ownerUUID) {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(ownerUUID, "ownerUUID");
        long now = System.currentTimeMillis();
        return new NpcConfiguration(
            Objects.requireNonNull(UUID.randomUUID()),
            displayName,
            null,
            null,
            Objects.requireNonNull(NpcBehavior.DEFAULT),
            Objects.requireNonNull(NpcAppearance.DEFAULT),
            null,
            ownerUUID,
            null,
            null,
            now,
            now,
            0
        );
    }

    /**
     * Creates a copy with updated display name.
     */
    @Nonnull
    public NpcConfiguration withDisplayName(@Nonnull String displayName) {
        return new NpcConfiguration(id, displayName, skinPlayerUUID, skinPlayerName, behavior, appearance,
            dialogSetId, ownerUUID, spawnPosition, dimension, createdAt, System.currentTimeMillis(), revision + 1);
    }

    /**
     * Creates a copy with updated skin player.
     */
    @Nonnull
    public NpcConfiguration withSkin(@Nullable UUID skinUUID, @Nullable String skinName) {
        return new NpcConfiguration(id, displayName, skinUUID, skinName, behavior, appearance,
            dialogSetId, ownerUUID, spawnPosition, dimension, createdAt, System.currentTimeMillis(), revision + 1);
    }

    /**
     * Creates a copy with updated behavior.
     */
    @Nonnull
    public NpcConfiguration withBehavior(@Nonnull NpcBehavior behavior) {
        return new NpcConfiguration(id, displayName, skinPlayerUUID, skinPlayerName, behavior, appearance,
            dialogSetId, ownerUUID, spawnPosition, dimension, createdAt, System.currentTimeMillis(), revision + 1);
    }

    /**
     * Creates a copy with updated appearance.
     */
    @Nonnull
    public NpcConfiguration withAppearance(@Nonnull NpcAppearance appearance) {
        return new NpcConfiguration(id, displayName, skinPlayerUUID, skinPlayerName, behavior, appearance,
            dialogSetId, ownerUUID, spawnPosition, dimension, createdAt, System.currentTimeMillis(), revision + 1);
    }

    /**
     * Creates a copy with updated dialog set ID.
     */
    @Nonnull
    public NpcConfiguration withDialogSetId(@Nullable String dialogSetId) {
        return new NpcConfiguration(id, displayName, skinPlayerUUID, skinPlayerName, behavior, appearance,
            dialogSetId, ownerUUID, spawnPosition, dimension, createdAt, System.currentTimeMillis(), revision + 1);
    }

    /**
     * Creates a copy with updated spawn position and dimension.
     */
    @Nonnull
    public NpcConfiguration withSpawnLocation(@Nullable BlockPos position, @Nullable ResourceKey<Level> dimension) {
        return new NpcConfiguration(id, displayName, skinPlayerUUID, skinPlayerName, behavior, appearance,
            dialogSetId, ownerUUID, position, dimension, createdAt, System.currentTimeMillis(), revision + 1);
    }

    /**
     * Creates a copy with incremented revision and updated timestamp.
     * Use this when modifying the configuration.
     */
    @Nonnull
    public NpcConfiguration touch() {
        return new NpcConfiguration(id, displayName, skinPlayerUUID, skinPlayerName, behavior, appearance,
            dialogSetId, ownerUUID, spawnPosition, dimension, createdAt, System.currentTimeMillis(), revision + 1);
    }

    /**
     * Returns true if this configuration has a skin player UUID.
     */
    public boolean hasSkinUUID() {
        return skinPlayerUUID != null;
    }

    /**
     * Returns true if this configuration has a dialog set assigned.
     */
    public boolean hasDialogSet() {
        return dialogSetId != null && !dialogSetId.isEmpty();
    }

    /**
     * Returns true if this configuration has a spawn position.
     */
    public boolean hasSpawnPosition() {
        return spawnPosition != null;
    }

    /**
     * Returns true if the given player UUID is the owner of this NPC.
     */
    public boolean isOwnedBy(@Nonnull UUID playerUUID) {
        return ownerUUID.equals(playerUUID);
    }
}
