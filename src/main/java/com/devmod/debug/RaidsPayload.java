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

public record RaidsPayload(List<RaidInfo> raids) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RaidsPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "debug_raids")));

    public static final StreamCodec<FriendlyByteBuf, RaidsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RaidsPayload decode(@Nonnull FriendlyByteBuf buf) {
            int count = buf.readVarInt();
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
