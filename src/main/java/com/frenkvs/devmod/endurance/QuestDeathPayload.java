package com.frenkvs.devmod.endurance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Network payload sent from server to client when player dies during quest.
 * Triggers the QuestDeathScreen to appear on the client.
 */
public record QuestDeathPayload(
    int currentWave,
    int totalWaves,
    boolean endlessMode,
    int pointsEarned,
    int deathsThisRun,
    int respawnCost
) implements CustomPacketPayload {

    public static final Type<QuestDeathPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "quest_death")
    );

    public static final StreamCodec<ByteBuf, QuestDeathPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public QuestDeathPayload decode(ByteBuf buf) {
            int currentWave = buf.readInt();
            int totalWaves = buf.readInt();
            boolean endlessMode = buf.readBoolean();
            int pointsEarned = buf.readInt();
            int deathsThisRun = buf.readInt();
            int respawnCost = buf.readInt();
            return new QuestDeathPayload(currentWave, totalWaves, endlessMode, pointsEarned, deathsThisRun, respawnCost);
        }

        @Override
        public void encode(ByteBuf buf, QuestDeathPayload payload) {
            buf.writeInt(payload.currentWave);
            buf.writeInt(payload.totalWaves);
            buf.writeBoolean(payload.endlessMode);
            buf.writeInt(payload.pointsEarned);
            buf.writeInt(payload.deathsThisRun);
            buf.writeInt(payload.respawnCost);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
