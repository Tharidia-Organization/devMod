package com.devmod.endurance;

import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record InstanceLoadingPayload(
    boolean show,
    String status
) implements CustomPacketPayload {

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
}
