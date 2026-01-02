package com.devmod.endurance;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

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
    int styleRankOrdinal,
    // Flow State data (STALE/FRESH/VIRTUOSO)
    int flowStateOrdinal,
    float virtuosoProgress,
    float staleRisk,
    int uniqueActionCount,
    // Momentum data (pacing enforcement)
    int momentumPercent,
    int momentumStateOrdinal,
    boolean isOverdrive,
    long overdriveRemaining
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

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
        buf.writeVarInt(payload.flowStateOrdinal);
        buf.writeFloat(payload.virtuosoProgress);
        buf.writeFloat(payload.staleRisk);
        buf.writeVarInt(payload.uniqueActionCount);
        buf.writeVarInt(payload.momentumPercent);
        buf.writeVarInt(payload.momentumStateOrdinal);
        buf.writeBoolean(payload.isOverdrive);
        buf.writeLong(payload.overdriveRemaining);
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
        int flowStateOrdinal = buf.readVarInt();
        float virtuosoProgress = buf.readFloat();
        float staleRisk = buf.readFloat();
        int uniqueActionCount = buf.readVarInt();
        int momentumPercent = buf.readVarInt();
        int momentumStateOrdinal = buf.readVarInt();
        boolean isOverdrive = buf.readBoolean();
        long overdriveRemaining = buf.readLong();

        return new QuestSyncPayload(
            hasActiveQuest, questName, templateId, templateVersion, policyId, policyVersion,
            instanceId, arenaId, difficultyLabel, questTypeLabel,
            currentWave, totalWaves, endlessMode,
            pointsEarned, mobsKilled, mobsKilledInWave, totalMobsInWave,
            damageDealt, damageTaken, deaths, sessionDurationMs, stateOrdinal,
            objectiveTypeOrdinal, objectiveTitle, objectiveDescription,
            objectiveProgress, objectiveTarget, objectiveComplete, objectiveFailed,
            modifiers, currentCombo, maxCombo, styleScore, styleRankOrdinal,
            flowStateOrdinal, virtuosoProgress, staleRisk, uniqueActionCount,
            momentumPercent, momentumStateOrdinal, isOverdrive, overdriveRemaining
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = 0;
        size += 1; // hasActiveQuest
        size += estimatedUtfSize(questName);
        size += estimatedUtfSize(templateId);
        size += varIntSize(templateVersion);
        size += estimatedUtfSize(policyId);
        size += varIntSize(policyVersion);
        size += estimatedUtfSize(instanceId);
        size += estimatedUtfSize(arenaId);
        size += estimatedUtfSize(difficultyLabel);
        size += estimatedUtfSize(questTypeLabel);
        size += varIntSize(currentWave);
        size += varIntSize(totalWaves);
        size += 1; // endlessMode
        size += varIntSize(pointsEarned);
        size += varIntSize(mobsKilled);
        size += varIntSize(mobsKilledInWave);
        size += varIntSize(totalMobsInWave);
        size += varIntSize(damageDealt);
        size += varIntSize(damageTaken);
        size += varIntSize(deaths);
        size += 8; // sessionDurationMs
        size += varIntSize(stateOrdinal);
        size += varIntSize(objectiveTypeOrdinal);
        size += estimatedUtfSize(objectiveTitle);
        size += estimatedUtfSize(objectiveDescription);
        size += varIntSize(objectiveProgress);
        size += varIntSize(objectiveTarget);
        size += 1; // objectiveComplete
        size += 1; // objectiveFailed
        size += varIntSize(waveModifiers.size());
        for (String mod : waveModifiers) {
            size += estimatedUtfSize(mod);
        }
        size += varIntSize(currentCombo);
        size += varIntSize(maxCombo);
        size += varIntSize(styleScore);
        size += varIntSize(styleRankOrdinal);
        size += varIntSize(flowStateOrdinal);
        size += 4; // virtuosoProgress
        size += 4; // staleRisk
        size += varIntSize(uniqueActionCount);
        size += varIntSize(momentumPercent);
        size += varIntSize(momentumStateOrdinal);
        size += 1; // isOverdrive
        size += 8; // overdriveRemaining
        return size;
    }

    /**
     * Create an empty payload (no active quest).
     */
    public static QuestSyncPayload empty() {
        return new QuestSyncPayload(
            false, "", "", 0, "", 0, "", "", "", "",
            0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, "", "", 0, 0, false, false,
            List.of(), 0, 0, 0, 0,
            1, 0f, 0f, 0,  // Flow state: NEUTRAL (ordinal 1), no progress
            50, 1, false, 0  // Momentum: 50%, BUILDING (ordinal 1), no overdrive
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

    /**
     * Get the flow state enum value.
     */
    public FlowStateTracker.FlowState getFlowState() {
        FlowStateTracker.FlowState[] values = FlowStateTracker.FlowState.values();
        if (flowStateOrdinal >= 0 && flowStateOrdinal < values.length) {
            return values[flowStateOrdinal];
        }
        return FlowStateTracker.FlowState.NEUTRAL;
    }

    /**
     * Get the momentum state enum value.
     */
    public MomentumTracker.MomentumState getMomentumState() {
        MomentumTracker.MomentumState[] values = MomentumTracker.MomentumState.values();
        if (momentumStateOrdinal >= 0 && momentumStateOrdinal < values.length) {
            return values[momentumStateOrdinal];
        }
        return MomentumTracker.MomentumState.BUILDING;
    }

    public WaveObjectiveState.Type getObjectiveType() {
        WaveObjectiveState.Type[] values = WaveObjectiveState.Type.values();
        if (objectiveTypeOrdinal >= 0 && objectiveTypeOrdinal < values.length) {
            return values[objectiveTypeOrdinal];
        }
        return WaveObjectiveState.Type.KILL_ALL;
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
