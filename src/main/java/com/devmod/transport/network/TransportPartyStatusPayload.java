package com.devmod.transport.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Server → Client payload to sync party teleport status.
 * Shows which party members are ready/arrived.
 *
 * <p>Channel: transport_party_status
 */
public record TransportPartyStatusPayload(
    UUID partyId,
    int phase,              // 0=countdown, 1=teleporting, 2=waiting, 3=complete, 4=cancelled
    int arrivedCount,
    int expectedCount,
    List<UUID> arrivedMembers
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    public static final Type<TransportPartyStatusPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "transport_party_status")));

    public static final StreamCodec<ByteBuf, TransportPartyStatusPayload> STREAM_CODEC =
        StreamCodec.of(TransportPartyStatusPayload::encode, TransportPartyStatusPayload::decode);

    private static void encode(ByteBuf buf, TransportPartyStatusPayload payload) {
        UUIDUtil.STREAM_CODEC.encode(Objects.requireNonNull(buf), Objects.requireNonNull(payload.partyId));
        int safePhase = TransportPayloadLimits.clampRange(payload.phase, PHASE_COUNTDOWN, PHASE_CANCELLED);
        int safeArrived = TransportPayloadLimits.clampCount(payload.arrivedCount, TransportPayloadLimits.MAX_PARTY_MEMBERS);
        int safeExpected = TransportPayloadLimits.clampCount(payload.expectedCount, TransportPayloadLimits.MAX_PARTY_MEMBERS);
        int memberCount = TransportPayloadLimits.clampCount(payload.arrivedMembers.size(), TransportPayloadLimits.MAX_PARTY_MEMBERS);
        buf.writeByte(safePhase);
        buf.writeByte(safeArrived);
        buf.writeByte(safeExpected);
        buf.writeByte(memberCount);
        for (int i = 0; i < memberCount; i++) {
            UUID member = payload.arrivedMembers.get(i);
            UUIDUtil.STREAM_CODEC.encode(buf, Objects.requireNonNull(member));
        }
    }

    private static TransportPartyStatusPayload decode(ByteBuf buf) {
        UUID partyId = UUIDUtil.STREAM_CODEC.decode(Objects.requireNonNull(buf));
        int phase = buf.readUnsignedByte();
        int arrivedCount = buf.readUnsignedByte();
        int expectedCount = buf.readUnsignedByte();
        int rawMemberCount = buf.readUnsignedByte();
        int safeMemberCount = TransportPayloadLimits.clampCount(rawMemberCount, TransportPayloadLimits.MAX_PARTY_MEMBERS);
        List<UUID> arrivedMembers = new ArrayList<>(safeMemberCount);
        for (int i = 0; i < rawMemberCount; i++) {
            UUID member = UUIDUtil.STREAM_CODEC.decode(buf);
            if (i < safeMemberCount) {
                arrivedMembers.add(member);
            }
        }
        int safePhase = TransportPayloadLimits.clampRange(phase, PHASE_COUNTDOWN, PHASE_CANCELLED);
        int safeArrived = TransportPayloadLimits.clampCount(arrivedCount, TransportPayloadLimits.MAX_PARTY_MEMBERS);
        int safeExpected = TransportPayloadLimits.clampCount(expectedCount, TransportPayloadLimits.MAX_PARTY_MEMBERS);
        return new TransportPartyStatusPayload(partyId, safePhase, safeArrived, safeExpected, arrivedMembers);
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    @Override
    public int estimatedSize() {
        long size = 16; // partyId
        size += 4; // phase + arrivedCount + expectedCount + memberCount (bytes)
        int memberCount = TransportPayloadLimits.clampCount(arrivedMembers.size(), TransportPayloadLimits.MAX_PARTY_MEMBERS);
        size += 16L * memberCount;
        return PayloadSizeUtil.clampToInt(size);
    }

    /**
     * Returns true if all expected members have arrived.
     */
    public boolean allArrived() {
        return arrivedCount >= expectedCount;
    }

    /**
     * Returns progress as float (0.0 - 1.0).
     */
    public float getArrivalProgress() {
        if (expectedCount <= 0) return 1.0f;
        return (float) arrivedCount / (float) expectedCount;
    }

    /**
     * Phase constants for UI display.
     */
    public static final int PHASE_COUNTDOWN = 0;
    public static final int PHASE_TELEPORTING = 1;
    public static final int PHASE_WAITING = 2;
    public static final int PHASE_COMPLETE = 3;
    public static final int PHASE_CANCELLED = 4;

    /**
     * Returns true if sequence was cancelled.
     */
    public boolean isCancelled() {
        return phase == PHASE_CANCELLED;
    }

    /**
     * Returns true if sequence is complete.
     */
    public boolean isComplete() {
        return phase == PHASE_COMPLETE;
    }
}
