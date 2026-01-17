package com.devmod.npc.data;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable record containing NPC behavior configuration.
 * Controls movement, interaction, and combat properties.
 */
public record NpcBehavior(
    boolean floating,
    float floatAmplitude,
    float floatSpeed,
    boolean lookAtPlayer,
    boolean invulnerable
) {
    /** Default behavior: floating, looks at player, invulnerable */
    public static final NpcBehavior DEFAULT = new NpcBehavior(true, 0.3f, 0.05f, true, true);

    public static final Codec<NpcBehavior> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.BOOL.fieldOf("floating").forGetter(NpcBehavior::floating),
            Codec.FLOAT.fieldOf("floatAmplitude").forGetter(NpcBehavior::floatAmplitude),
            Codec.FLOAT.fieldOf("floatSpeed").forGetter(NpcBehavior::floatSpeed),
            Codec.BOOL.fieldOf("lookAtPlayer").forGetter(NpcBehavior::lookAtPlayer),
            Codec.BOOL.fieldOf("invulnerable").forGetter(NpcBehavior::invulnerable)
        ).apply(instance, NpcBehavior::new)
    );

    public static final StreamCodec<FriendlyByteBuf, NpcBehavior> STREAM_CODEC = StreamCodec.of(
        (buf, data) -> {
            buf.writeBoolean(data.floating);
            buf.writeFloat(data.floatAmplitude);
            buf.writeFloat(data.floatSpeed);
            buf.writeBoolean(data.lookAtPlayer);
            buf.writeBoolean(data.invulnerable);
        },
        buf -> new NpcBehavior(
            buf.readBoolean(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readBoolean(),
            buf.readBoolean()
        )
    );

    /**
     * Creates a new NpcBehavior with floating enabled/disabled.
     */
    @Nonnull
    public NpcBehavior withFloating(boolean floating) {
        return new NpcBehavior(floating, this.floatAmplitude, this.floatSpeed, this.lookAtPlayer, this.invulnerable);
    }

    /**
     * Creates a new NpcBehavior with custom float amplitude.
     */
    @Nonnull
    public NpcBehavior withFloatAmplitude(float amplitude) {
        return new NpcBehavior(this.floating, amplitude, this.floatSpeed, this.lookAtPlayer, this.invulnerable);
    }

    /**
     * Creates a new NpcBehavior with custom float speed.
     */
    @Nonnull
    public NpcBehavior withFloatSpeed(float speed) {
        return new NpcBehavior(this.floating, this.floatAmplitude, speed, this.lookAtPlayer, this.invulnerable);
    }

    /**
     * Creates a new NpcBehavior with lookAtPlayer enabled/disabled.
     */
    @Nonnull
    public NpcBehavior withLookAtPlayer(boolean lookAtPlayer) {
        return new NpcBehavior(this.floating, this.floatAmplitude, this.floatSpeed, lookAtPlayer, this.invulnerable);
    }

    /**
     * Creates a new NpcBehavior with invulnerable enabled/disabled.
     */
    @Nonnull
    public NpcBehavior withInvulnerable(boolean invulnerable) {
        return new NpcBehavior(this.floating, this.floatAmplitude, this.floatSpeed, this.lookAtPlayer, invulnerable);
    }
}
