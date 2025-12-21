package com.frenkvs.devmod.endurance;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Payload sent from server to client to synchronize quest state for HUD display.
 * This keeps the client informed about the active quest state.
 */
public record QuestSyncPayload(
    boolean hasActiveQuest,
    String questName,
    String templateId,
    int templateVersion,
    String policyId,
    int policyVersion,
    String instanceId,
    String arenaId,
    String difficultyLabel,
    String questTypeLabel,
    int currentWave,
    int totalWaves,
    boolean endlessMode,
    int pointsEarned,
    int mobsKilled,
    int mobsKilledInWave,
    int totalMobsInWave,
    int damageDealt,
    int damageTaken,
    int deaths,
    long sessionDurationMs,
    int stateOrdinal,
    int objectiveTypeOrdinal,
    String objectiveTitle,
    String objectiveDescription,
    int objectiveProgress,
    int objectiveTarget,
    boolean objectiveComplete,
    boolean objectiveFailed,
    List<String> waveModifiers,
    // Combo system data
    int currentCombo,
    int maxCombo,
    int styleScore,
    int styleRankOrdinal
) implements CustomPacketPayload {

    public static final Type<QuestSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "quest_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestSyncPayload> STREAM_CODEC = StreamCodec.of(
        QuestSyncPayload::encode,
        QuestSyncPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, QuestSyncPayload payload) {
        buf.writeBoolean(payload.hasActiveQuest);
        buf.writeUtf(Objects.requireNonNull(payload.questName));
        buf.writeUtf(Objects.requireNonNull(payload.templateId));
        buf.writeVarInt(payload.templateVersion);
        buf.writeUtf(Objects.requireNonNull(payload.policyId));
        buf.writeVarInt(payload.policyVersion);
        buf.writeUtf(Objects.requireNonNull(payload.instanceId));
        buf.writeUtf(Objects.requireNonNull(payload.arenaId));
        buf.writeUtf(Objects.requireNonNull(payload.difficultyLabel));
        buf.writeUtf(Objects.requireNonNull(payload.questTypeLabel));
        buf.writeVarInt(payload.currentWave);
        buf.writeVarInt(payload.totalWaves);
        buf.writeBoolean(payload.endlessMode);
        buf.writeVarInt(payload.pointsEarned);
        buf.writeVarInt(payload.mobsKilled);
        buf.writeVarInt(payload.mobsKilledInWave);
        buf.writeVarInt(payload.totalMobsInWave);
        buf.writeVarInt(payload.damageDealt);
        buf.writeVarInt(payload.damageTaken);
        buf.writeVarInt(payload.deaths);
        buf.writeLong(payload.sessionDurationMs);
        buf.writeVarInt(payload.stateOrdinal);
        buf.writeVarInt(payload.objectiveTypeOrdinal);
        buf.writeUtf(Objects.requireNonNull(payload.objectiveTitle));
        buf.writeUtf(Objects.requireNonNull(payload.objectiveDescription));
        buf.writeVarInt(payload.objectiveProgress);
        buf.writeVarInt(payload.objectiveTarget);
        buf.writeBoolean(payload.objectiveComplete);
        buf.writeBoolean(payload.objectiveFailed);

        // Write modifiers list
        buf.writeVarInt(payload.waveModifiers.size());
        for (String mod : payload.waveModifiers) {
            buf.writeUtf(Objects.requireNonNull(mod));
        }

        buf.writeVarInt(payload.currentCombo);
        buf.writeVarInt(payload.maxCombo);
        buf.writeVarInt(payload.styleScore);
        buf.writeVarInt(payload.styleRankOrdinal);
    }

    // Security limits to prevent DoS attacks
    private static final int MAX_STRING_LENGTH = 256;
    private static final int MAX_MODIFIERS = 50;

    private static QuestSyncPayload decode(RegistryFriendlyByteBuf buf) {
        boolean hasActiveQuest = buf.readBoolean();
        // SECURITY FIX: Limit string length
        String questName = buf.readUtf(MAX_STRING_LENGTH);
        String templateId = buf.readUtf(MAX_STRING_LENGTH);
        int templateVersion = buf.readVarInt();
        String policyId = buf.readUtf(MAX_STRING_LENGTH);
        int policyVersion = buf.readVarInt();
        String instanceId = buf.readUtf(MAX_STRING_LENGTH);
        String arenaId = buf.readUtf(MAX_STRING_LENGTH);
        String difficultyLabel = buf.readUtf(MAX_STRING_LENGTH);
        String questTypeLabel = buf.readUtf(MAX_STRING_LENGTH);
        int currentWave = buf.readVarInt();
        int totalWaves = buf.readVarInt();
        boolean endlessMode = buf.readBoolean();
        int pointsEarned = buf.readVarInt();
        int mobsKilled = buf.readVarInt();
        int mobsKilledInWave = buf.readVarInt();
        int totalMobsInWave = buf.readVarInt();
        int damageDealt = buf.readVarInt();
        int damageTaken = buf.readVarInt();
        int deaths = buf.readVarInt();
        long sessionDurationMs = buf.readLong();
        int stateOrdinal = buf.readVarInt();
        int objectiveTypeOrdinal = buf.readVarInt();
        String objectiveTitle = buf.readUtf(MAX_STRING_LENGTH);
        String objectiveDescription = buf.readUtf(MAX_STRING_LENGTH);
        int objectiveProgress = buf.readVarInt();
        int objectiveTarget = buf.readVarInt();
        boolean objectiveComplete = buf.readBoolean();
        boolean objectiveFailed = buf.readBoolean();

        // Read modifiers list with security bounds
        int modCount = buf.readVarInt();
        // SECURITY FIX: Validate count is non-negative and bounded
        if (modCount < 0 || modCount > MAX_MODIFIERS) {
            modCount = 0; // Reject malformed packet
        }
        List<String> modifiers = new ArrayList<>(Math.min(modCount, MAX_MODIFIERS));
        for (int i = 0; i < modCount; i++) {
            String mod = buf.readUtf(MAX_STRING_LENGTH);
            if (mod != null) {
                modifiers.add(mod);
            }
        }

        int currentCombo = buf.readVarInt();
        int maxCombo = buf.readVarInt();
        int styleScore = buf.readVarInt();
        int styleRankOrdinal = buf.readVarInt();

        return new QuestSyncPayload(
            hasActiveQuest, questName, templateId, templateVersion, policyId, policyVersion,
            instanceId, arenaId, difficultyLabel, questTypeLabel,
            currentWave, totalWaves, endlessMode,
            pointsEarned, mobsKilled, mobsKilledInWave, totalMobsInWave,
            damageDealt, damageTaken, deaths, sessionDurationMs, stateOrdinal,
            objectiveTypeOrdinal, objectiveTitle, objectiveDescription,
            objectiveProgress, objectiveTarget, objectiveComplete, objectiveFailed,
            modifiers, currentCombo, maxCombo, styleScore, styleRankOrdinal
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Create an empty payload (no active quest).
     */
    public static QuestSyncPayload empty() {
        return new QuestSyncPayload(
            false, "", "", 0, "", 0, "", "", "", "",
            0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, "", "", 0, 0, false, false,
            List.of(), 0, 0, 0, 0
        );
    }

    /**
     * Get the quest state enum value.
     */
    public EnduranceQuestState getState() {
        EnduranceQuestState[] values = EnduranceQuestState.values();
        if (stateOrdinal >= 0 && stateOrdinal < values.length) {
            return values[stateOrdinal];
        }
        return EnduranceQuestState.AVAILABLE;
    }

    /**
     * Get the style rank enum value.
     */
    public ComboSystem.StyleRank getStyleRank() {
        ComboSystem.StyleRank[] values = ComboSystem.StyleRank.values();
        if (styleRankOrdinal >= 0 && styleRankOrdinal < values.length) {
            return values[styleRankOrdinal];
        }
        return ComboSystem.StyleRank.D;
    }

    public WaveObjectiveState.Type getObjectiveType() {
        WaveObjectiveState.Type[] values = WaveObjectiveState.Type.values();
        if (objectiveTypeOrdinal >= 0 && objectiveTypeOrdinal < values.length) {
            return values[objectiveTypeOrdinal];
        }
        return WaveObjectiveState.Type.KILL_ALL;
    }
}
