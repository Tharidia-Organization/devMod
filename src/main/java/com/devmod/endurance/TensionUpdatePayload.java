package com.devmod.endurance;

import java.util.Objects;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
public record TensionUpdatePayload(float tensionPercent, int tensionLevel, boolean bossImminent) implements CustomPacketPayload {

    public static final ResourceLocation ID = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "tension_update"));
    public static final Type<TensionUpdatePayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, TensionUpdatePayload> STREAM_CODEC =
        StreamCodec.composite(
            Objects.requireNonNull(StreamCodec.of(
                (buf, value) -> buf.writeFloat(value.floatValue()),
                FriendlyByteBuf::readFloat
            )),
            payload -> payload.tensionPercent,
            Objects.requireNonNull(StreamCodec.of(
                (buf, value) -> buf.writeInt(value.intValue()),
                FriendlyByteBuf::readInt
            )),
            payload -> payload.tensionLevel,
            Objects.requireNonNull(StreamCodec.of(
                (buf, value) -> buf.writeBoolean(value.booleanValue()),
                FriendlyByteBuf::readBoolean
            )),
            payload -> payload.bossImminent,
            (percent, level, boss) -> new TensionUpdatePayload(percent.floatValue(), level.intValue(), boss.booleanValue())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Get display color based on tension level.
     * 0 = calm (green), 4 = critical (red/pulsing)
     */
    public int getDisplayColor() {
        return switch (tensionLevel) {
            case 0 -> 0x44FF44; // Green - calm
            case 1 -> 0xAAFF44; // Yellow-green - building
            case 2 -> 0xFFFF44; // Yellow - moderate
            case 3 -> 0xFFAA44; // Orange - high
            case 4 -> 0xFF4444; // Red - critical (boss imminent)
            default -> 0xFFFFFF;
        };
    }

    /**
     * Get tension label for UI display.
     */
    public String getTensionLabel() {
        return switch (tensionLevel) {
            case 0 -> "Calm";
            case 1 -> "Rising";
            case 2 -> "Tense";
            case 3 -> "Critical";
            case 4 -> "BOSS INCOMING";
            default -> "";
        };
    }
}
