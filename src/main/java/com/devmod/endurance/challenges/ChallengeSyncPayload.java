package com.devmod.endurance.challenges;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

public record ChallengeSyncPayload(
        List<ChallengeData> challenges
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<ChallengeSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "challenge_sync"));

    public static final StreamCodec<ByteBuf, ChallengeSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ChallengeData.LIST_CODEC,
                    ChallengeSyncPayload::challenges,
                    ChallengeSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = varIntSize(challenges.size());
        for (ChallengeData data : challenges) {
            size += estimateChallengeSize(data);
        }
        return size;
    }

    /**
     * Data for a single challenge including player progress.
     */
    public record ChallengeData(
            String id,
            String nameKey,
            String descriptionKey,
            int difficulty,      // 0=Easy, 1=Medium, 2=Hard, 3=Extreme
            int targetValue,
            int currentValue,
            int tokenReward,
            int prestigeReward,
            boolean completed,
            boolean rewarded
    ) {
        // Custom codec for 10 fields (StreamCodec.composite only supports up to 6)
        public static final StreamCodec<ByteBuf, ChallengeData> CODEC = StreamCodec.of(
                (buf, data) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, data.id);
                    ByteBufCodecs.STRING_UTF8.encode(buf, data.nameKey);
                    ByteBufCodecs.STRING_UTF8.encode(buf, data.descriptionKey);
                    ByteBufCodecs.VAR_INT.encode(buf, data.difficulty);
                    ByteBufCodecs.VAR_INT.encode(buf, data.targetValue);
                    ByteBufCodecs.VAR_INT.encode(buf, data.currentValue);
                    ByteBufCodecs.VAR_INT.encode(buf, data.tokenReward);
                    ByteBufCodecs.VAR_INT.encode(buf, data.prestigeReward);
                    ByteBufCodecs.BOOL.encode(buf, data.completed);
                    ByteBufCodecs.BOOL.encode(buf, data.rewarded);
                },
                buf -> new ChallengeData(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf)
                )
        );

        public static final StreamCodec<ByteBuf, List<ChallengeData>> LIST_CODEC =
                ChallengeData.CODEC.apply(ByteBufCodecs.list());
    }

    /**
     * Create sync payload for a player.
     */
    public static ChallengeSyncPayload forPlayer(java.util.UUID playerId) {
        List<ChallengeData> data = DailyChallengeManager.INSTANCE.getActiveChallenges().stream()
                .map(challenge -> {
                    DailyChallenge.ChallengeProgress progress =
                            DailyChallengeManager.INSTANCE.getProgress(playerId, challenge.getId());

                    return new ChallengeData(
                            challenge.getId(),
                            challenge.getNameKey(),
                            challenge.getDescriptionKey(),
                            challenge.getDifficulty().ordinal(),
                            challenge.getTargetValue(),
                            progress.getCurrentValue(),
                            challenge.getTokenReward(),
                            challenge.getPrestigeReward(),
                            progress.isCompleted(),
                            progress.isRewarded()
                    );
                })
                .toList();

        return new ChallengeSyncPayload(data);
    }

    private static int estimateChallengeSize(ChallengeData data) {
        if (data == null) {
            return 0;
        }
        int size = 0;
        size += estimatedUtfSize(data.id);
        size += estimatedUtfSize(data.nameKey);
        size += estimatedUtfSize(data.descriptionKey);
        size += varIntSize(data.difficulty);
        size += varIntSize(data.targetValue);
        size += varIntSize(data.currentValue);
        size += varIntSize(data.tokenReward);
        size += varIntSize(data.prestigeReward);
        size += 1; // completed
        size += 1; // rewarded
        return size;
    }

    private static int estimatedUtfSize(String value) {
        if (value == null || value.isEmpty()) {
            return varIntSize(0);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return varIntSize(bytes.length) + bytes.length;
    }

    private static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }
}
