package com.devmod.party;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload sent from client to server to respond to a party invite.
 * Contains the invite ID and whether the player accepted or declined.
 */
public record InviteResponsePayload(
    UUID inviteId,
    boolean accepted
) implements CustomPacketPayload {

    public static final Type<InviteResponsePayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "invite_response"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, InviteResponsePayload> STREAM_CODEC = StreamCodec.of(
        InviteResponsePayload::encode,
        InviteResponsePayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, InviteResponsePayload payload) {
        buf.writeUUID(Objects.requireNonNull(payload.inviteId));
        buf.writeBoolean(payload.accepted);
    }

    private static InviteResponsePayload decode(RegistryFriendlyByteBuf buf) {
        UUID inviteId = Objects.requireNonNull(buf.readUUID());
        boolean accepted = buf.readBoolean();
        return new InviteResponsePayload(inviteId, accepted);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Create an accept response.
     */
    public static InviteResponsePayload accept(UUID inviteId) {
        return new InviteResponsePayload(inviteId, true);
    }

    /**
     * Create a decline response.
     */
    public static InviteResponsePayload decline(UUID inviteId) {
        return new InviteResponsePayload(inviteId, false);
    }
}
