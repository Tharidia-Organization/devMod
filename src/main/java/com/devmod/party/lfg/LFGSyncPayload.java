package com.devmod.party.lfg;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

/**
 * Server -> Client: Sync all LFG listings.
 */
public record LFGSyncPayload(
    List<LFGListingEntry> listings
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    private static final int MAX_LISTINGS = 100;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_DESC_LENGTH = 128;

    public static final Type<LFGSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "lfg_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, LFGSyncPayload> STREAM_CODEC = StreamCodec.of(
        LFGSyncPayload::encode,
        LFGSyncPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, LFGSyncPayload payload) {
        buf.writeVarInt(payload.listings.size());
        for (LFGListingEntry entry : payload.listings) {
            buf.writeUUID(Objects.requireNonNull(entry.listingId));
            buf.writeUUID(Objects.requireNonNull(entry.leaderId));
            buf.writeUtf(Objects.requireNonNull(entry.leaderName));
            buf.writeVarInt(entry.activityOrdinal);
            buf.writeUtf(Objects.requireNonNull(entry.description));
            buf.writeVarInt(entry.minPlayers);
            buf.writeVarInt(entry.maxPlayers);
            buf.writeVarInt(entry.currentPlayers);
            buf.writeBoolean(entry.autoAccept);
        }
    }

    private static LFGSyncPayload decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_LISTINGS) {
            count = 0;
        }
        List<LFGListingEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID listingId = buf.readUUID();
            UUID leaderId = buf.readUUID();
            String leaderName = buf.readUtf(MAX_NAME_LENGTH);
            int activityOrdinal = buf.readVarInt();
            String description = buf.readUtf(MAX_DESC_LENGTH);
            int minPlayers = buf.readVarInt();
            int maxPlayers = buf.readVarInt();
            int currentPlayers = buf.readVarInt();
            boolean autoAccept = buf.readBoolean();
            entries.add(new LFGListingEntry(listingId, leaderId, leaderName, activityOrdinal,
                description, minPlayers, maxPlayers, currentPlayers, autoAccept));
        }
        return new LFGSyncPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 1; // varint for count
        for (LFGListingEntry entry : listings) {
            size += 16 + 16; // two UUIDs
            size += 3 + entry.leaderName.length();
            size += 1; // activity ordinal
            size += 3 + entry.description.length();
            size += 3; // min, max, current
            size += 1; // autoAccept
        }
        return size;
    }

    /**
     * Create a sync payload from the current LFG listings.
     */
    public static LFGSyncPayload fromListings(List<LFGListing> listings) {
        List<LFGListingEntry> entries = new ArrayList<>(listings.size());
        for (LFGListing listing : listings) {
            entries.add(new LFGListingEntry(
                listing.listingId(), listing.leaderId(), listing.leaderName(),
                listing.activity().ordinal(), listing.description(),
                listing.minPlayers(), listing.maxPlayers(), listing.currentPlayers(),
                listing.autoAccept()
            ));
        }
        return new LFGSyncPayload(entries);
    }

    public static LFGSyncPayload empty() {
        return new LFGSyncPayload(List.of());
    }

    /**
     * Wire-format entry for a single listing.
     */
    public record LFGListingEntry(
        UUID listingId,
        UUID leaderId,
        String leaderName,
        int activityOrdinal,
        String description,
        int minPlayers,
        int maxPlayers,
        int currentPlayers,
        boolean autoAccept
    ) {
        public LFGActivity getActivity() {
            return LFGActivity.fromOrdinal(activityOrdinal);
        }
    }
}
