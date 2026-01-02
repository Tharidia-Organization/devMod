package com.devmod.endurance;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

public record InstanceLoadingPayload(
    boolean show,
    String status
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<InstanceLoadingPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "instance_loading"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, InstanceLoadingPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBoolean(payload.show);
            buf.writeUtf(Objects.requireNonNull(payload.status), 256);
        },
        buf -> new InstanceLoadingPayload(buf.readBoolean(), Objects.requireNonNull(buf.readUtf(256)))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 1; // show
        size += estimatedUtfSize(status);
        return size;
    }

    // Factory methods for common states - use i18n keys that get translated on client
    public static InstanceLoadingPayload showCreating() {
        return new InstanceLoadingPayload(true, "devmod.loading.creating_dimension");
    }

    public static InstanceLoadingPayload showTeleporting() {
        return new InstanceLoadingPayload(true, "devmod.loading.teleporting");
    }

    public static InstanceLoadingPayload showPreparing() {
        return new InstanceLoadingPayload(true, "devmod.loading.preparing_arena");
    }

    public static InstanceLoadingPayload hide() {
        return new InstanceLoadingPayload(false, "");
    }

    private static int estimatedUtfSize(String value) {
        if (value == null || value.isEmpty()) {
            return varIntSize(0);
        }
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
}
