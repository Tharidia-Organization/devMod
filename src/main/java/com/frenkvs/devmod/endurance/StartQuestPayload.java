package com.frenkvs.devmod.endurance;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Payload sent from client to server to start an Endurance Quest.
 */
public record StartQuestPayload(
    String mobId,
    int totalWaves,
    boolean endlessMode,
    int arenaSize
) implements CustomPacketPayload {

    public static final Type<StartQuestPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "start_endurance_quest"))
    );

    // Security limits to prevent DoS attacks
    private static final int MAX_STRING_LENGTH = 256;

    public static final StreamCodec<RegistryFriendlyByteBuf, StartQuestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public StartQuestPayload decode(RegistryFriendlyByteBuf buf) {
            // SECURITY FIX: Limit string length
            String mobId = buf.readUtf(MAX_STRING_LENGTH);
            int totalWaves = buf.readVarInt();
            boolean endlessMode = buf.readBoolean();
            int arenaSize = buf.readVarInt();
            // SECURITY FIX: Clamp arena size to reasonable bounds
            arenaSize = Math.max(16, Math.min(arenaSize, 256));
            return new StartQuestPayload(mobId, totalWaves, endlessMode, arenaSize);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, StartQuestPayload payload) {
            buf.writeUtf(payload.mobId);
            buf.writeVarInt(payload.totalWaves);
            buf.writeBoolean(payload.endlessMode);
            buf.writeVarInt(payload.arenaSize);
        }
    };

    /**
     * Create with default arena size.
     */
    public StartQuestPayload(String mobId, int totalWaves, boolean endlessMode) {
        this(mobId, totalWaves, endlessMode, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Get mob ID as ResourceLocation.
     */
    public ResourceLocation getMobResourceLocation() {
        return ResourceLocation.parse(mobId);
    }
}
