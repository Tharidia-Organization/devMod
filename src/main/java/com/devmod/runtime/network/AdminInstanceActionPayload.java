package com.devmod.runtime.network;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Client-to-server payload for admin actions on instances.
 * Requires admin permission level to process.
 */
public record AdminInstanceActionPayload(
    ActionType action,
    UUID instanceId,
    @Nullable UUID playerId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<AdminInstanceActionPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "admin_instance_action"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminInstanceActionPayload> STREAM_CODEC = StreamCodec.of(
        AdminInstanceActionPayload::encode,
        AdminInstanceActionPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, AdminInstanceActionPayload payload) {
        buf.writeVarInt(payload.action.ordinal());
        buf.writeUUID(payload.instanceId != null ? payload.instanceId : new UUID(0, 0));
        buf.writeUUID(payload.playerId != null ? payload.playerId : new UUID(0, 0));
    }

    private static AdminInstanceActionPayload decode(RegistryFriendlyByteBuf buf) {
        int actionOrdinal = buf.readVarInt();
        ActionType action = ActionType.fromOrdinal(actionOrdinal);

        UUID instanceId = buf.readUUID();
        if (instanceId.getMostSignificantBits() == 0 && instanceId.getLeastSignificantBits() == 0) {
            instanceId = null;
        }

        UUID playerId = buf.readUUID();
        if (playerId.getMostSignificantBits() == 0 && playerId.getLeastSignificantBits() == 0) {
            playerId = null;
        }

        return new AdminInstanceActionPayload(action, instanceId, playerId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.varIntSize(action != null ? action.ordinal() : 0);
        size += 16; // instanceId (encoded as UUID, zero if null)
        size += 16; // playerId (encoded as UUID, zero if null)
        return PayloadSizeUtil.clampToInt(size);
    }

    /**
     * Create a refresh request payload.
     */
    public static AdminInstanceActionPayload refresh() {
        return new AdminInstanceActionPayload(ActionType.REFRESH, null, null);
    }

    /**
     * Create an end success payload for an instance.
     */
    public static AdminInstanceActionPayload endSuccess(UUID instanceId) {
        return new AdminInstanceActionPayload(ActionType.END_SUCCESS, instanceId, null);
    }

    /**
     * Create an end failure payload for an instance.
     */
    public static AdminInstanceActionPayload endFailure(UUID instanceId) {
        return new AdminInstanceActionPayload(ActionType.END_FAILURE, instanceId, null);
    }

    /**
     * Create a kick player payload.
     */
    public static AdminInstanceActionPayload kickPlayer(UUID instanceId, UUID playerId) {
        return new AdminInstanceActionPayload(ActionType.KICK_PLAYER, instanceId, playerId);
    }

    /**
     * Create a force destroy payload for an instance.
     */
    public static AdminInstanceActionPayload forceDestroy(UUID instanceId) {
        return new AdminInstanceActionPayload(ActionType.FORCE_DESTROY, instanceId, null);
    }

    /**
     * Admin action types for instance management.
     */
    public enum ActionType {
        /** Request updated instance data sync */
        REFRESH,

        /** End instance as success (rewards given) */
        END_SUCCESS,

        /** End instance as failure (no rewards) */
        END_FAILURE,

        /** Kick a specific player from an instance */
        KICK_PLAYER,

        /** Force immediate destruction of an instance */
        FORCE_DESTROY;

        /**
         * Get action type from ordinal with bounds checking.
         */
        public static ActionType fromOrdinal(int ordinal) {
            ActionType[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return REFRESH;
        }

        /**
         * Check if this action requires an instance ID.
         */
        public boolean requiresInstanceId() {
            return this != REFRESH;
        }

        /**
         * Check if this action requires a player ID.
         */
        public boolean requiresPlayerId() {
            return this == KICK_PLAYER;
        }
    }
}
