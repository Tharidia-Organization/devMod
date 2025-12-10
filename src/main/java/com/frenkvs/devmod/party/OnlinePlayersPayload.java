package com.frenkvs.devmod.party;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload sent from server to client containing list of online players.
 * Used for the party invite player selection UI.
 */
public record OnlinePlayersPayload(
    List<PlayerInfo> players
) implements CustomPacketPayload {

    public static final Type<OnlinePlayersPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "online_players"))
    );

    // Security: limit max players to prevent DoS
    private static final int MAX_PLAYERS = 100;
    private static final int MAX_NAME_LENGTH = 16;

    public static final StreamCodec<RegistryFriendlyByteBuf, OnlinePlayersPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        @Nonnull
        public OnlinePlayersPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            count = Math.min(count, MAX_PLAYERS);

            List<PlayerInfo> players = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID playerId = buf.readUUID();
                String playerName = buf.readUtf(MAX_NAME_LENGTH);
                boolean inParty = buf.readBoolean();
                boolean canInvite = buf.readBoolean();
                players.add(new PlayerInfo(playerId, playerName, inParty, canInvite));
            }
            return new OnlinePlayersPayload(players);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull OnlinePlayersPayload payload) {
            List<PlayerInfo> players = payload.players;
            buf.writeVarInt(Math.min(players.size(), MAX_PLAYERS));

            int written = 0;
            for (PlayerInfo player : players) {
                if (written >= MAX_PLAYERS) break;
                buf.writeUUID(player.playerId);
                buf.writeUtf(player.playerName, MAX_NAME_LENGTH);
                buf.writeBoolean(player.inParty);
                buf.writeBoolean(player.canInvite);
                written++;
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Information about an online player for invite selection.
     */
    public record PlayerInfo(
        UUID playerId,
        String playerName,
        boolean inParty,      // Already in a party
        boolean canInvite     // Can be invited (not in party, not self)
    ) {}

    /**
     * Create an empty payload (no players).
     */
    public static OnlinePlayersPayload empty() {
        return new OnlinePlayersPayload(List.of());
    }
}
