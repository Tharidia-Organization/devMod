package com.devmod.area.data;

import java.util.Objects;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Defines the type of generation for an area.
 */
public enum AreaGenerationType implements StringRepresentable {
    /**
     * Custom area built using palette materials.
     */
    CUSTOM("custom"),

    /**
     * Real biome generation with terrain, features, etc.
     */
    BIOME("biome");

    public static final Codec<AreaGenerationType> CODEC = StringRepresentable.fromEnum(AreaGenerationType::values);

    public static final StreamCodec<ByteBuf, AreaGenerationType> STREAM_CODEC = StreamCodec.of(
        (buf, type) -> ByteBufCodecs.VAR_INT.encode(buf, type.ordinal()),
        buf -> values()[Math.min(ByteBufCodecs.VAR_INT.decode(buf), values().length - 1)]
    );

    private final String name;

    AreaGenerationType(String name) {
        this.name = name;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return Objects.requireNonNull(name);
    }
}
