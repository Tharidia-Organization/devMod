package com.devmod.npc.data;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable record containing NPC appearance configuration.
 * Controls visual effects like particles and glow.
 */
public record NpcAppearance(
    boolean particlesEnabled,
    @Nonnull ResourceLocation particleTypeId,
    int particleInterval,
    boolean glowEffect
) {
    /** Default appearance: particles enabled (end_rod), 10 tick interval, no glow */
    public static final NpcAppearance DEFAULT = new NpcAppearance(
        true,
        ResourceLocation.withDefaultNamespace("end_rod"),
        10,
        false
    );

    public static final Codec<NpcAppearance> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.BOOL.fieldOf("particlesEnabled").forGetter(NpcAppearance::particlesEnabled),
            ResourceLocation.CODEC.fieldOf("particleTypeId").forGetter(NpcAppearance::particleTypeId),
            Codec.INT.fieldOf("particleInterval").forGetter(NpcAppearance::particleInterval),
            Codec.BOOL.fieldOf("glowEffect").forGetter(NpcAppearance::glowEffect)
        ).apply(instance, NpcAppearance::new)
    );

    public static final StreamCodec<FriendlyByteBuf, NpcAppearance> STREAM_CODEC = StreamCodec.of(
        (buf, data) -> {
            buf.writeBoolean(data.particlesEnabled);
            buf.writeResourceLocation(data.particleTypeId);
            buf.writeVarInt(data.particleInterval);
            buf.writeBoolean(data.glowEffect);
        },
        buf -> new NpcAppearance(
            buf.readBoolean(),
            buf.readResourceLocation(),
            buf.readVarInt(),
            buf.readBoolean()
        )
    );

    /**
     * Creates a new NpcAppearance with particles enabled/disabled.
     */
    @Nonnull
    public NpcAppearance withParticlesEnabled(boolean enabled) {
        return new NpcAppearance(enabled, this.particleTypeId, this.particleInterval, this.glowEffect);
    }

    /**
     * Creates a new NpcAppearance with custom particle type.
     */
    @Nonnull
    public NpcAppearance withParticleType(@Nonnull ResourceLocation particleType) {
        return new NpcAppearance(this.particlesEnabled, particleType, this.particleInterval, this.glowEffect);
    }

    /**
     * Creates a new NpcAppearance with custom particle interval.
     */
    @Nonnull
    public NpcAppearance withParticleInterval(int interval) {
        return new NpcAppearance(this.particlesEnabled, this.particleTypeId, interval, this.glowEffect);
    }

    /**
     * Creates a new NpcAppearance with glow effect enabled/disabled.
     */
    @Nonnull
    public NpcAppearance withGlowEffect(boolean glow) {
        return new NpcAppearance(this.particlesEnabled, this.particleTypeId, this.particleInterval, glow);
    }
}
