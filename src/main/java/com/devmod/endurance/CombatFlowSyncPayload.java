package com.devmod.endurance;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;

/**
 * Syncs combat flow state from server to client for HUD display.
 * Contains combo count, style rank, flow state, and momentum.
 */
public record CombatFlowSyncPayload(
    int combo,
    int maxCombo,
    int styleScore,
    int styleRankOrdinal,
    int flowStateOrdinal,
    float virtuosoProgress,
    float staleRisk,
    int momentumPercent,
    int momentumStateOrdinal,
    long overdriveRemainingMs,
    @Nonnull String lastActionName,
    int lastActionPoints
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /**
     * Compact constructor for null validation.
     */
    public CombatFlowSyncPayload {
        Objects.requireNonNull(lastActionName, "lastActionName");
    }

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "combat_flow_sync"));
    public static final Type<CombatFlowSyncPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    // Empty state for when not in quest
    public static final CombatFlowSyncPayload EMPTY = new CombatFlowSyncPayload(
        0, 0, 0, 0, 1, 0f, 0f, 50, 1, 0, "", 0
    );

    public static final StreamCodec<FriendlyByteBuf, CombatFlowSyncPayload> STREAM_CODEC =
        StreamCodec.of(
            CombatFlowSyncPayload::write,
            CombatFlowSyncPayload::read
        );

    private static void write(FriendlyByteBuf buf, CombatFlowSyncPayload payload) {
        buf.writeVarInt(payload.combo);
        buf.writeVarInt(payload.maxCombo);
        buf.writeVarInt(payload.styleScore);
        buf.writeVarInt(payload.styleRankOrdinal);      // VarInt for enum ordinal safety
        buf.writeVarInt(payload.flowStateOrdinal);      // VarInt for enum ordinal safety
        buf.writeFloat(payload.virtuosoProgress);
        buf.writeFloat(payload.staleRisk);
        buf.writeVarInt(payload.momentumPercent);       // VarInt for percent (0-100+)
        buf.writeVarInt(payload.momentumStateOrdinal);  // VarInt for enum ordinal safety
        buf.writeVarLong(payload.overdriveRemainingMs);
        buf.writeUtf(Objects.requireNonNull(payload.lastActionName), 32);
        buf.writeVarInt(payload.lastActionPoints);
    }

    private static CombatFlowSyncPayload read(FriendlyByteBuf buf) {
        return new CombatFlowSyncPayload(
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),  // styleRankOrdinal
            buf.readVarInt(),  // flowStateOrdinal
            buf.readFloat(),
            buf.readFloat(),
            buf.readVarInt(),  // momentumPercent
            buf.readVarInt(),  // momentumStateOrdinal
            buf.readVarLong(),
            buf.readUtf(32),
            buf.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        // 9 VarInts (5 bytes max each) + 2 floats (4 each) + VarLong (9) + string (34) + VarInt (5)
        return 9 * 5 + 4 + 4 + 9 + 34 + 5;
    }

    // === Helper methods for client-side interpretation ===

    public ComboSystem.StyleRank getStyleRank() {
        ComboSystem.StyleRank[] ranks = ComboSystem.StyleRank.values();
        return styleRankOrdinal >= 0 && styleRankOrdinal < ranks.length
            ? ranks[styleRankOrdinal]
            : ComboSystem.StyleRank.D;
    }

    public FlowStateTracker.FlowState getFlowState() {
        FlowStateTracker.FlowState[] states = FlowStateTracker.FlowState.values();
        return flowStateOrdinal >= 0 && flowStateOrdinal < states.length
            ? states[flowStateOrdinal]
            : FlowStateTracker.FlowState.NEUTRAL;
    }

    public MomentumTracker.MomentumState getMomentumState() {
        MomentumTracker.MomentumState[] states = MomentumTracker.MomentumState.values();
        return momentumStateOrdinal >= 0 && momentumStateOrdinal < states.length
            ? states[momentumStateOrdinal]
            : MomentumTracker.MomentumState.BUILDING;
    }

    public boolean isInOverdrive() {
        return overdriveRemainingMs > 0;
    }

    public boolean isEmpty() {
        return combo == 0 && styleScore == 0 && momentumPercent == 50;
    }

    /**
     * Create payload from current server-side session state.
     */
    public static CombatFlowSyncPayload fromSession(
            ComboSystem.ComboSession comboSession,
            MomentumTracker.MomentumSession momentumSession,
            String lastAction,
            int lastPoints) {

        if (comboSession == null) {
            return EMPTY;
        }

        return new CombatFlowSyncPayload(
            comboSession.getCurrentCombo(),
            comboSession.getMaxCombo(),
            comboSession.getStyleScore(),
            comboSession.getCurrentRank().ordinal(),
            comboSession.getFlowState().ordinal(),
            comboSession.getVirtuosoProgress(),
            comboSession.getStaleRisk(),
            momentumSession != null ? momentumSession.getMomentumPercent() : 50,
            momentumSession != null ? momentumSession.getState().ordinal() : 1,
            momentumSession != null ? momentumSession.getOverdriveRemaining() : 0,
            lastAction != null ? lastAction : "",
            lastPoints
        );
    }
}
