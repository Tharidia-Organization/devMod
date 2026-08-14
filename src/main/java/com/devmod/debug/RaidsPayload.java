package com.devmod.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

public record RaidsPayload(List<RaidInfo> raids) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /** Maximum raids per payload to prevent DoS via unbounded allocation */
    private static final int MAX_RAIDS = 32;

    public static final CustomPacketPayload.Type<RaidsPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_raids")));

    public static final StreamCodec<FriendlyByteBuf, RaidsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RaidsPayload decode(@Nonnull FriendlyByteBuf buf) {
            int count = Math.min(buf.readVarInt(), MAX_RAIDS);
            if (count < 0) count = 0;
            List<RaidInfo> raids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                raids.add(new RaidInfo(
                    buf.readVarInt(),  // raidId
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), // center
                    buf.readVarInt(),  // badOmenLevel
                    buf.readVarInt(),  // groupsSpawned
                    buf.readVarInt(),  // numGroups
                    buf.readBoolean(), // isActive
                    buf.readBoolean()  // isVictory
                ));
            }
            return new RaidsPayload(raids);
        }

        @Override
        public void encode(@Nonnull FriendlyByteBuf buf, @Nonnull RaidsPayload payload) {
            buf.writeVarInt(payload.raids.size());
            for (RaidInfo raid : payload.raids) {
                buf.writeVarInt(raid.raidId);
                buf.writeDouble(raid.centerX);
                buf.writeDouble(raid.centerY);
                buf.writeDouble(raid.centerZ);
                buf.writeVarInt(raid.badOmenLevel);
                buf.writeVarInt(raid.groupsSpawned);
                buf.writeVarInt(raid.numGroups);
                buf.writeBoolean(raid.isActive);
                buf.writeBoolean(raid.isVictory);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.varIntSize(raids.size());
        for (RaidInfo raid : raids) {
            size += PayloadSizeUtil.varIntSize(raid.raidId);
            size += 8L * 3; // centerX/Y/Z
            size += PayloadSizeUtil.varIntSize(raid.badOmenLevel);
            size += PayloadSizeUtil.varIntSize(raid.groupsSpawned);
            size += PayloadSizeUtil.varIntSize(raid.numGroups);
            size += 1; // isActive
            size += 1; // isVictory
        }
        return PayloadSizeUtil.clampToInt(size);
    }

    public record RaidInfo(
        int raidId,
        double centerX, double centerY, double centerZ,
        int badOmenLevel,
        int groupsSpawned,
        int numGroups,
        boolean isActive,
        boolean isVictory
    ) {}
}
