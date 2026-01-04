package com.devmod.endurance;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.endurance.config.ConfigScope;
import com.devmod.network.PayloadValidation;

public record RequestMobPoolConfigPayload(ConfigScope scope)
    implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<RequestMobPoolConfigPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "request_mob_pool_config"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMobPoolConfigPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public RequestMobPoolConfigPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
                int scopeOrdinal = buf.readVarInt();
                ConfigScope scope = ConfigScope.values()[Math.min(scopeOrdinal, ConfigScope.values().length - 1)];
                return new RequestMobPoolConfigPayload(scope);
            }

            @Override
            public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull RequestMobPoolConfigPayload payload) {
                buf.writeVarInt(payload.scope.ordinal());
            }
        };

    public RequestMobPoolConfigPayload {
        Objects.requireNonNull(scope, "scope");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 1;
    }
}
