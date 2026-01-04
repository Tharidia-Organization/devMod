package com.devmod.endurance;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

public record MobPoolConfigSyncPayload(boolean hasConfig, EnduranceMobConfigSyncPayload data)
    implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<MobPoolConfigSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "mob_pool_config_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MobPoolConfigSyncPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public MobPoolConfigSyncPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
                boolean hasConfig = buf.readBoolean();
                EnduranceMobConfigSyncPayload data = EnduranceMobConfigSyncPayload.STREAM_CODEC.decode(buf);
                return new MobPoolConfigSyncPayload(hasConfig, data);
            }

            @Override
            public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull MobPoolConfigSyncPayload payload) {
                buf.writeBoolean(payload.hasConfig());
                EnduranceMobConfigSyncPayload.STREAM_CODEC.encode(buf, payload.data());
            }
        };

    public MobPoolConfigSyncPayload {
        Objects.requireNonNull(data, "data");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 1 + data.estimatedSize();
    }
}
