package com.devmod.mailbox.network.payload;

import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;
/**
 * Payload to sync mailbox access flags to the client.
 */
public record MailboxAccessPayload(
    boolean isAdmin,
    boolean isTester
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<MailboxAccessPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "mailbox_access"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MailboxAccessPayload> STREAM_CODEC = StreamCodec.of(
        MailboxAccessPayload::encode,
        MailboxAccessPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, MailboxAccessPayload payload) {
        buf.writeBoolean(payload.isAdmin);
        buf.writeBoolean(payload.isTester);
    }

    private static MailboxAccessPayload decode(RegistryFriendlyByteBuf buf) {
        boolean isAdmin = buf.readBoolean();
        boolean isTester = buf.readBoolean();
        return new MailboxAccessPayload(isAdmin, isTester);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        return 2; // two booleans
    }
}
