package com.devmod.zone.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Server -> Client payload when a player enters a zone.
 * Used to play the zone cue sound and show the hint message.
 */
public record ZoneEnterPayload(
    @Nonnull UUID zoneId,
    @Nonnull String displayName,
    @Nonnull String hintMessage,
    @Nullable ResourceLocation cueSoundId,
    int color,
    boolean isSpecial
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "zone_enter")
    );
    public static final Type<ZoneEnterPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<RegistryFriendlyByteBuf, ZoneEnterPayload> STREAM_CODEC =
        StreamCodec.of(ZoneEnterPayload::encode, ZoneEnterPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ZoneEnterPayload payload) {
        buf.writeUUID(payload.zoneId);
        buf.writeUtf(payload.displayName, 64);
        buf.writeUtf(payload.hintMessage, 256);

        // Sound ID (optional)
        buf.writeBoolean(payload.cueSoundId != null);
        if (payload.cueSoundId != null) {
            buf.writeResourceLocation(payload.cueSoundId);
        }

        buf.writeInt(payload.color);
        buf.writeBoolean(payload.isSpecial);
    }

    private static ZoneEnterPayload decode(RegistryFriendlyByteBuf buf) {
        UUID zoneId = buf.readUUID();
        String displayName = buf.readUtf(64);
        String hintMessage = buf.readUtf(256);

        ResourceLocation cueSoundId = buf.readBoolean()
            ? buf.readResourceLocation()
            : null;

        int color = buf.readInt();
        boolean isSpecial = buf.readBoolean();

        return new ZoneEnterPayload(Objects.requireNonNull(zoneId), Objects.requireNonNull(displayName), Objects.requireNonNull(hintMessage), cueSoundId, color, isSpecial);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 16 + 66 + 258 + 64 + 4 + 1; // UUID + displayName + hint + soundId + color + isSpecial
    }

    /**
     * Creates a zone enter payload from a zone's display data.
     */
    public static ZoneEnterPayload fromZone(
        @Nonnull UUID zoneId,
        @Nonnull String displayName,
        @Nonnull String hintMessage,
        @Nullable SoundEvent cueSound,
        int color,
        boolean isSpecial
    ) {
        ResourceLocation soundId = cueSound != null
            ? BuiltInRegistries.SOUND_EVENT.getKey(cueSound)
            : null;
        return new ZoneEnterPayload(zoneId, displayName, hintMessage, soundId, color, isSpecial);
    }

    /**
     * Gets the SoundEvent for the cue, or null if not found.
     */
    @Nullable
    public SoundEvent getCueSound() {
        if (cueSoundId == null) {
            return null;
        }
        return BuiltInRegistries.SOUND_EVENT.get(cueSoundId);
    }
}
