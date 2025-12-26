package com.devmod.endurance;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import io.netty.buffer.ByteBuf;
public record QuestCompletionPayload(
    // Quest info
    String questName,
    int finalWave,
    int totalWaves,
    boolean endlessMode,
    long durationMs,
    String templateId,
    int templateVersion,
    String policyId,
    int policyVersion,
    String instanceId,
    String arenaId,
    String difficultyLabel,
    String questTypeLabel,

    // Rewards
    int tokensEarned,
    int baseTokens,
    int prestigeEarned,
    int bloodGemsEarned,

    // Multipliers
    float styleMultiplier,
    float mutatorMultiplier,

    // Bonuses
    boolean noHitBonus,
    boolean speedBonus,
    int styleRankOrdinal,
    int activeMutators,

    // Stats
    int totalKills,
    float totalDamageDealt,
    float totalDamageTaken,
    int deaths,
    int maxCombo,

    // Achievements (names)
    List<String> achievementNames
) implements CustomPacketPayload {

    private static final int MAX_STRING_LENGTH = 256;
    private static final int MAX_ACHIEVEMENTS = 10;

    public static final Type<QuestCompletionPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "quest_completion"))
    );

    public static final StreamCodec<ByteBuf, QuestCompletionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public QuestCompletionPayload decode(@Nonnull ByteBuf buf) {
            String questName = readString(buf);
            int finalWave = buf.readInt();
            int totalWaves = buf.readInt();
            boolean endlessMode = buf.readBoolean();
            long durationMs = buf.readLong();
            String templateId = readString(buf);
            int templateVersion = buf.readInt();
            String policyId = readString(buf);
            int policyVersion = buf.readInt();
            String instanceId = readString(buf);
            String arenaId = readString(buf);
            String difficultyLabel = readString(buf);
            String questTypeLabel = readString(buf);

            int tokensEarned = buf.readInt();
            int baseTokens = buf.readInt();
            int prestigeEarned = buf.readInt();
            int bloodGemsEarned = buf.readInt();

            float styleMultiplier = buf.readFloat();
            float mutatorMultiplier = buf.readFloat();

            boolean noHitBonus = buf.readBoolean();
            boolean speedBonus = buf.readBoolean();
            int styleRankOrdinal = buf.readInt();
            int activeMutators = buf.readInt();

            int totalKills = buf.readInt();
            float totalDamageDealt = buf.readFloat();
            float totalDamageTaken = buf.readFloat();
            int deaths = buf.readInt();
            int maxCombo = buf.readInt();

            int achievementCount = Math.min(buf.readInt(), MAX_ACHIEVEMENTS);
            List<String> achievementNames = new ArrayList<>();
            for (int i = 0; i < achievementCount; i++) {
                achievementNames.add(readString(buf));
            }

            return new QuestCompletionPayload(
                questName, finalWave, totalWaves, endlessMode, durationMs,
                templateId, templateVersion, policyId, policyVersion, instanceId, arenaId,
                difficultyLabel, questTypeLabel,
                tokensEarned, baseTokens, prestigeEarned, bloodGemsEarned,
                styleMultiplier, mutatorMultiplier,
                noHitBonus, speedBonus, styleRankOrdinal, activeMutators,
                totalKills, totalDamageDealt, totalDamageTaken, deaths, maxCombo,
                achievementNames
            );
        }

        @Override
        public void encode(@Nonnull ByteBuf buf, @Nonnull QuestCompletionPayload payload) {
            writeString(buf, payload.questName);
            buf.writeInt(payload.finalWave);
            buf.writeInt(payload.totalWaves);
            buf.writeBoolean(payload.endlessMode);
            buf.writeLong(payload.durationMs);
            writeString(buf, payload.templateId);
            buf.writeInt(payload.templateVersion);
            writeString(buf, payload.policyId);
            buf.writeInt(payload.policyVersion);
            writeString(buf, payload.instanceId);
            writeString(buf, payload.arenaId);
            writeString(buf, payload.difficultyLabel);
            writeString(buf, payload.questTypeLabel);

            buf.writeInt(payload.tokensEarned);
            buf.writeInt(payload.baseTokens);
            buf.writeInt(payload.prestigeEarned);
            buf.writeInt(payload.bloodGemsEarned);

            buf.writeFloat(payload.styleMultiplier);
            buf.writeFloat(payload.mutatorMultiplier);

            buf.writeBoolean(payload.noHitBonus);
            buf.writeBoolean(payload.speedBonus);
            buf.writeInt(payload.styleRankOrdinal);
            buf.writeInt(payload.activeMutators);

            buf.writeInt(payload.totalKills);
            buf.writeFloat(payload.totalDamageDealt);
            buf.writeFloat(payload.totalDamageTaken);
            buf.writeInt(payload.deaths);
            buf.writeInt(payload.maxCombo);

            int count = Math.min(payload.achievementNames.size(), MAX_ACHIEVEMENTS);
            buf.writeInt(count);
            for (int i = 0; i < count; i++) {
                writeString(buf, payload.achievementNames.get(i));
            }
        }

        private String readString(ByteBuf buf) {
            int length = buf.readInt();
            if (length < 0 || length > MAX_STRING_LENGTH) {
                length = 0;
            }
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
     * Get style rank enum from ordinal.
     */
    public ComboSystem.StyleRank getStyleRank() {
        ComboSystem.StyleRank[] ranks = ComboSystem.StyleRank.values();
        if (styleRankOrdinal >= 0 && styleRankOrdinal < ranks.length) {
            return ranks[styleRankOrdinal];
        }
        return ComboSystem.StyleRank.D;
    }

    /**
     * Format duration as MM:SS.
     */
    public String getFormattedDuration() {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
