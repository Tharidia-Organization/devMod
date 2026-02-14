package com.devmod.party.lfg;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

/**
 * Client -> Server: LFG actions (create, remove, apply, refresh).
 */
public record LFGActionPayload(
    Action action,
    int activityOrdinal,
    String description,
    int minPlayers,
    int maxPlayers,
    boolean autoAccept,
    @Nullable UUID targetListingId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    private static final int MAX_DESC_LENGTH = 128;

    public static final Type<LFGActionPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "lfg_action"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, LFGActionPayload> STREAM_CODEC = StreamCodec.of(
        LFGActionPayload::encode,
        LFGActionPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, LFGActionPayload payload) {
        buf.writeVarInt(payload.action.ordinal());
        buf.writeVarInt(payload.activityOrdinal);
        buf.writeUtf(Objects.requireNonNull(payload.description != null ? payload.description : ""));
        buf.writeVarInt(payload.minPlayers);
        buf.writeVarInt(payload.maxPlayers);
        buf.writeBoolean(payload.autoAccept);
        buf.writeBoolean(payload.targetListingId != null);
        if (payload.targetListingId != null) {
            buf.writeUUID(payload.targetListingId);
        }
    }

    private static LFGActionPayload decode(RegistryFriendlyByteBuf buf) {
        int actionOrdinal = buf.readVarInt();
        Action action = Action.fromOrdinal(actionOrdinal);
        int activityOrdinal = buf.readVarInt();
        String description = buf.readUtf(MAX_DESC_LENGTH);
        int minPlayers = buf.readVarInt();
        int maxPlayers = buf.readVarInt();
        boolean autoAccept = buf.readBoolean();
        UUID targetListingId = null;
        if (buf.readBoolean()) {
            targetListingId = buf.readUUID();
        }
        return new LFGActionPayload(action, activityOrdinal, description, minPlayers, maxPlayers, autoAccept, targetListingId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 1; // action ordinal
        size += 1; // activity ordinal
        String desc = description != null ? description : "";
        size += 3 + desc.length();
        size += 2; // min, max
        size += 1; // autoAccept
        size += 1; // has targetListingId
        if (targetListingId != null) {
            size += 16;
        }
        return size;
    }

    public LFGActivity getActivity() {
        return LFGActivity.fromOrdinal(activityOrdinal);
    }

    public enum Action {
        CREATE,
        REMOVE,
        APPLY,
        REFRESH;

        public static Action fromOrdinal(int ordinal) {
            Action[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return REFRESH;
        }
    }

    // Factory methods

    public static LFGActionPayload create(LFGActivity activity, String description,
                                           int minPlayers, int maxPlayers, boolean autoAccept) {
        return new LFGActionPayload(Action.CREATE, activity.ordinal(), description,
            minPlayers, maxPlayers, autoAccept, null);
    }

    public static LFGActionPayload remove() {
        return new LFGActionPayload(Action.REMOVE, 0, "", 1, 4, false, null);
    }

    public static LFGActionPayload apply(UUID listingId) {
        return new LFGActionPayload(Action.APPLY, 0, "", 1, 4, false, listingId);
    }

    public static LFGActionPayload refresh() {
        return new LFGActionPayload(Action.REFRESH, 0, "", 1, 4, false, null);
    }
}
