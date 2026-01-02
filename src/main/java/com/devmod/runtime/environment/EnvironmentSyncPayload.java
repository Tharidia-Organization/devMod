package com.devmod.runtime.environment;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Network payload to sync environment settings (frozen time, biome) from server to client.
 * Used to override client-side time display for arena dimensions.
 */
public record EnvironmentSyncPayload(
    @Nonnull String dimensionKey,
    long frozenTime,
    boolean isTimeFrozen,
    @Nonnull String biomeId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /**
     * Compact constructor for validation.
     */
    public EnvironmentSyncPayload {
        Objects.requireNonNull(dimensionKey, "dimensionKey");
        Objects.requireNonNull(biomeId, "biomeId");
    }

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "environment_sync"));
    public static final Type<EnvironmentSyncPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, EnvironmentSyncPayload> STREAM_CODEC =
        StreamCodec.of(
            EnvironmentSyncPayload::encode,
            EnvironmentSyncPayload::decode
        );

    private static void encode(FriendlyByteBuf buf, EnvironmentSyncPayload payload) {
        buf.writeUtf(Objects.requireNonNull(payload.dimensionKey));
        buf.writeLong(payload.frozenTime);
        buf.writeBoolean(payload.isTimeFrozen);
        buf.writeUtf(Objects.requireNonNull(payload.biomeId));
    }

    private static EnvironmentSyncPayload decode(FriendlyByteBuf buf) {
        String dimensionKey = buf.readUtf();
        long frozenTime = buf.readLong();
        boolean isTimeFrozen = buf.readBoolean();
        String biomeId = buf.readUtf();
        return new EnvironmentSyncPayload(dimensionKey, frozenTime, isTimeFrozen, biomeId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = estimatedUtfSize(dimensionKey);
        size += 8; // frozenTime
        size += 1; // isTimeFrozen
        size += estimatedUtfSize(biomeId);
        return size;
    }

    private static int estimatedUtfSize(@Nonnull String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return varIntSize(bytes.length) + bytes.length;
    }

    private static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }

    /**
     * Creates a payload for setting frozen time in a dimension.
     */
    public static EnvironmentSyncPayload frozen(String dimensionKey, long time, String biomeId) {
        return new EnvironmentSyncPayload(dimensionKey, time, true, biomeId);
    }

    /**
     * Creates a payload for clearing frozen time in a dimension.
     */
    public static EnvironmentSyncPayload clear(String dimensionKey) {
        return new EnvironmentSyncPayload(dimensionKey, 0, false, "");
    }
}
