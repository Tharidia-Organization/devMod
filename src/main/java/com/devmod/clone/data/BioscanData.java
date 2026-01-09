package com.devmod.clone.data;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Immutable record containing scanned entity data for cloning.
 * Stored in BIOSCANNER items and used by NEUROCELL for cloning.
 *
 * <p>For players, stores UUID and name for skin rendering.
 * For other entities, stores full NBT for respawning.
 */
public record BioscanData(
    @Nonnull ResourceLocation entityTypeId,
    @Nonnull String entityName,
    @Nullable UUID playerUUID,
    @Nullable CompoundTag entityNBT,
    long scanTime
) {
    public static final Codec<BioscanData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceLocation.CODEC.fieldOf("entityType").forGetter(BioscanData::entityTypeId),
            Codec.STRING.fieldOf("entityName").forGetter(BioscanData::entityName),
            UUIDUtil.CODEC.optionalFieldOf("playerUUID").forGetter(d -> Optional.ofNullable(d.playerUUID())),
            CompoundTag.CODEC.optionalFieldOf("entityNBT").forGetter(d -> Optional.ofNullable(d.entityNBT())),
            Codec.LONG.fieldOf("scanTime").forGetter(BioscanData::scanTime)
        ).apply(instance, (type, name, uuid, nbt, time) ->
            new BioscanData(type, name, uuid.orElse(null), nbt.orElse(null), time))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BioscanData> STREAM_CODEC = StreamCodec.of(
        (buf, data) -> {
            buf.writeResourceLocation(data.entityTypeId);
            buf.writeUtf(data.entityName);
            buf.writeBoolean(data.playerUUID != null);
            if (data.playerUUID != null) {
                buf.writeUUID(data.playerUUID);
            }
            buf.writeBoolean(data.entityNBT != null);
            if (data.entityNBT != null) {
                ByteBufCodecs.COMPOUND_TAG.encode(buf, data.entityNBT);
            }
            buf.writeLong(data.scanTime);
        },
        buf -> {
            ResourceLocation entityType = buf.readResourceLocation();
            String entityName = buf.readUtf();
            UUID playerUUID = buf.readBoolean() ? buf.readUUID() : null;
            CompoundTag entityNBT = buf.readBoolean() ? ByteBufCodecs.COMPOUND_TAG.decode(buf) : null;
            long scanTime = buf.readLong();
            return new BioscanData(entityType, entityName, playerUUID, entityNBT, scanTime);
        }
    );

    /**
     * Creates BioscanData from a living entity.
     * For players, only stores UUID (for skin) and name.
     * For other entities, stores full NBT.
     */
    @Nonnull
    public static BioscanData fromEntity(@Nonnull LivingEntity entity) {
        Objects.requireNonNull(entity, "entity");

        ResourceLocation typeId = EntityType.getKey(entity.getType());
        String name = entity.getName().getString();
        UUID playerUUID = null;
        CompoundTag nbt = null;

        if (entity instanceof Player player) {
            playerUUID = player.getUUID();
        } else {
            nbt = new CompoundTag();
            entity.save(nbt);
            // Remove position data - will be set by REFORMER
            nbt.remove("Pos");
            nbt.remove("Motion");
            nbt.remove("Rotation");
            nbt.remove("UUID");
        }

        return new BioscanData(
            typeId,
            name,
            playerUUID,
            nbt,
            System.currentTimeMillis()
        );
    }

    /**
     * Returns true if this bioscan data is for a player.
     */
    public boolean isPlayer() {
        return playerUUID != null;
    }

    /**
     * Returns true if this bioscan data has valid entity NBT.
     */
    public boolean hasEntityNBT() {
        return entityNBT != null && !entityNBT.isEmpty();
    }

    /**
     * Returns the entity type, or null if not resolvable.
     */
    @Nullable
    public EntityType<?> getEntityType() {
        return EntityType.byString(entityTypeId.toString()).orElse(null);
    }

    /**
     * Serialize to CompoundTag for storage.
     */
    @Nonnull
    public CompoundTag toTag() {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this)
            .result()
            .orElseGet(CompoundTag::new);
    }

    /**
     * Deserialize from CompoundTag.
     */
    @Nullable
    public static BioscanData fromTag(@Nonnull CompoundTag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
    }
}
