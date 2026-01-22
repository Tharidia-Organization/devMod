package com.devmod.endurance;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Client → Server: Request arena template suggestions for a mob.
 */
public record RequestArenaSuggestionsPayload(
    String mobId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    private static final int MAX_STRING_LENGTH = 256;

    public static final Type<RequestArenaSuggestionsPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "request_arena_suggestions"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestArenaSuggestionsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RequestArenaSuggestionsPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            String mobId = Objects.requireNonNull(buf.readUtf(MAX_STRING_LENGTH));
            return new RequestArenaSuggestionsPayload(mobId);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull RequestArenaSuggestionsPayload payload) {
            buf.writeUtf(Objects.requireNonNull(payload.mobId));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return PayloadSizeUtil.estimatedUtfSize(mobId);
    }

    /**
     * Get mob ID as ResourceLocation.
     */
    public ResourceLocation getMobResourceLocation() {
        return Objects.requireNonNull(ResourceLocation.parse(Objects.requireNonNull(mobId)));
    }
}
