package com.frenkvs.devmod.endurance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Network payload sent from server to client with player's personal records.
 * Used to display "Best: Wave X | Y pts" in the EnduranceQuestScreen.
 */
public record PersonalRecordsSyncPayload(
    int totalQuestsAttempted,
    int totalQuestsCompleted,
    int totalPointsEarned,
    Map<String, MobRecord> mobRecords
) implements CustomPacketPayload {

    private static final int MAX_STRING_LENGTH = 128;
    private static final int MAX_RECORDS = 100;

    public static final Type<PersonalRecordsSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "personal_records_sync")
    );

    /**
     * Record data for a specific mob type.
     */
    public record MobRecord(
        int attempts,
        int completions,
        int bestScore,
        int highestWave
    ) {}

    public static final StreamCodec<ByteBuf, PersonalRecordsSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PersonalRecordsSyncPayload decode(ByteBuf buf) {
            int totalAttempted = buf.readInt();
            int totalCompleted = buf.readInt();
            int totalPoints = buf.readInt();

            int recordCount = Math.min(buf.readInt(), MAX_RECORDS);
            Map<String, MobRecord> records = new HashMap<>();

            for (int i = 0; i < recordCount; i++) {
                String mobId = readString(buf);
                int attempts = buf.readInt();
                int completions = buf.readInt();
                int bestScore = buf.readInt();
                int highestWave = buf.readInt();
                records.put(mobId, new MobRecord(attempts, completions, bestScore, highestWave));
            }

            return new PersonalRecordsSyncPayload(totalAttempted, totalCompleted, totalPoints, records);
        }

        @Override
        public void encode(ByteBuf buf, PersonalRecordsSyncPayload payload) {
            buf.writeInt(payload.totalQuestsAttempted);
            buf.writeInt(payload.totalQuestsCompleted);
            buf.writeInt(payload.totalPointsEarned);

            int count = Math.min(payload.mobRecords.size(), MAX_RECORDS);
            buf.writeInt(count);

            int written = 0;
            for (Map.Entry<String, MobRecord> entry : payload.mobRecords.entrySet()) {
                if (written >= count) break;
                writeString(buf, entry.getKey());
                buf.writeInt(entry.getValue().attempts);
                buf.writeInt(entry.getValue().completions);
                buf.writeInt(entry.getValue().bestScore);
                buf.writeInt(entry.getValue().highestWave);
                written++;
            }
        }

        private String readString(ByteBuf buf) {
            int length = buf.readInt();
            if (length < 0 || length > MAX_STRING_LENGTH) length = 0;
            if (length == 0) return "";
            return buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
        }

        private void writeString(ByteBuf buf, String str) {
            if (str == null) str = "";
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            int length = Math.min(bytes.length, MAX_STRING_LENGTH);
            buf.writeInt(length);
            buf.writeBytes(bytes, 0, length);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Get record for a specific mob ID.
     */
    public MobRecord getRecord(String mobId) {
        return mobRecords.getOrDefault(mobId, new MobRecord(0, 0, 0, 0));
    }
}
