package com.devmod.template.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * A placed component within a room template.
 * Defines where and how a component is positioned.
 */
public record ComponentPlacement(
    @Nonnull String componentId,
    @Nonnull RelativePosition position,
    @Nonnull Direction facing,
    @Nonnull Map<String, String> params
) {
    public static final Codec<ComponentPlacement> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("componentId").forGetter(ComponentPlacement::componentId),
            RelativePosition.CODEC.fieldOf("position").forGetter(ComponentPlacement::position),
            Direction.CODEC.fieldOf("facing").forGetter(ComponentPlacement::facing),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("params").forGetter(ComponentPlacement::params)
        ).apply(instance, ComponentPlacement::new)
    );

    public static final StreamCodec<FriendlyByteBuf, ComponentPlacement> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ComponentPlacement decode(FriendlyByteBuf buf) {
            String componentId = buf.readUtf();
            RelativePosition position = RelativePosition.STREAM_CODEC.decode(buf);
            Direction facing = Direction.from3DDataValue(buf.readVarInt());
            int paramCount = buf.readVarInt();
            Map<String, String> params = new HashMap<>(paramCount);
            for (int i = 0; i < paramCount; i++) {
                params.put(buf.readUtf(), buf.readUtf());
            }
            return new ComponentPlacement(componentId, position, facing, params);
        }

        @Override
        public void encode(FriendlyByteBuf buf, ComponentPlacement placement) {
            buf.writeUtf(placement.componentId);
            RelativePosition.STREAM_CODEC.encode(buf, placement.position);
            buf.writeVarInt(placement.facing.get3DDataValue());
            buf.writeVarInt(placement.params.size());
            for (Map.Entry<String, String> entry : placement.params.entrySet()) {
                buf.writeUtf(entry.getKey());
                buf.writeUtf(entry.getValue());
            }
        }
    };

    public ComponentPlacement {
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(facing, "facing");
        Objects.requireNonNull(params, "params");
    }

    /**
     * Create a placement with default facing (NORTH) and no params.
     */
    public static ComponentPlacement of(String componentId, RelativePosition position) {
        return new ComponentPlacement(componentId, position, Direction.NORTH, Map.of());
    }

    /**
     * Create a placement with specific facing and no params.
     */
    public static ComponentPlacement of(String componentId, RelativePosition position, Direction facing) {
        return new ComponentPlacement(componentId, position, facing, Map.of());
    }

    /**
     * Get a parameter value with default.
     */
    public String getParam(String key, String defaultValue) {
        return params.getOrDefault(key, defaultValue);
    }

    /**
     * Get an integer parameter with default.
     */
    public int getIntParam(String key, int defaultValue) {
        String value = params.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get a boolean parameter with default.
     */
    public boolean getBoolParam(String key, boolean defaultValue) {
        String value = params.get(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }
}
