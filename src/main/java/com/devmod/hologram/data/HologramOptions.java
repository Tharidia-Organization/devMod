package com.devmod.hologram.data;

import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Configuration options for a hologram.
 * Controls visibility, locking, refresh rates, and zone linking.
 */
public record HologramOptions(
    boolean enabled,
    boolean locked,
    int refreshInterval,
    int visibilityRange,
    @Nullable String linkedZone
) {
    /**
     * Default options for new holograms.
     */
    public static final HologramOptions DEFAULT = new HologramOptions(
        true,   // enabled
        false,  // not locked
        0,      // use global refresh (0 = default)
        0,      // use global visibility (0 = default)
        null    // no linked zone
    );

    /**
     * Default global refresh interval in ticks (matches Config.NEXUS_HOLOGRAM_UPDATE_INTERVAL).
     */
    public static final int DEFAULT_REFRESH_INTERVAL = 600;

    /**
     * Default visibility range in blocks.
     */
    public static final int DEFAULT_VISIBILITY_RANGE = 48;

    public static final Codec<HologramOptions> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.BOOL.fieldOf("enabled").forGetter(HologramOptions::enabled),
            Codec.BOOL.fieldOf("locked").forGetter(HologramOptions::locked),
            Codec.INT.fieldOf("refreshInterval").forGetter(HologramOptions::refreshInterval),
            Codec.INT.fieldOf("visibilityRange").forGetter(HologramOptions::visibilityRange),
            Codec.STRING.optionalFieldOf("linkedZone").forGetter(o -> Optional.ofNullable(o.linkedZone()))
        ).apply(instance, (enabled, locked, refresh, range, zoneOpt) ->
            new HologramOptions(enabled, locked, refresh, range, zoneOpt.orElse(null)))
    );

    public static final StreamCodec<FriendlyByteBuf, HologramOptions> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HologramOptions decode(FriendlyByteBuf buf) {
            boolean enabled = buf.readBoolean();
            boolean locked = buf.readBoolean();
            int refreshInterval = buf.readVarInt();
            int visibilityRange = buf.readVarInt();
            String linkedZone = buf.readBoolean() ? buf.readUtf() : null;
            return new HologramOptions(enabled, locked, refreshInterval, visibilityRange, linkedZone);
        }

        @Override
        public void encode(FriendlyByteBuf buf, HologramOptions opts) {
            buf.writeBoolean(opts.enabled);
            buf.writeBoolean(opts.locked);
            buf.writeVarInt(opts.refreshInterval);
            buf.writeVarInt(opts.visibilityRange);
            buf.writeBoolean(opts.linkedZone != null);
            if (opts.linkedZone != null) {
                buf.writeUtf(opts.linkedZone);
            }
        }
    };

    /**
     * Returns the effective refresh interval, using global default if not set.
     */
    public int getEffectiveRefreshInterval() {
        return refreshInterval > 0 ? refreshInterval : DEFAULT_REFRESH_INTERVAL;
    }

    /**
     * Returns the effective visibility range, using global default if not set.
     */
    public int getEffectiveVisibilityRange() {
        return visibilityRange > 0 ? visibilityRange : DEFAULT_VISIBILITY_RANGE;
    }

    /**
     * Returns whether this hologram is linked to a specific zone.
     */
    public boolean hasLinkedZone() {
        return linkedZone != null && !linkedZone.isEmpty();
    }

    /**
     * Returns a copy with enabled state changed.
     */
    public HologramOptions withEnabled(boolean newEnabled) {
        return new HologramOptions(newEnabled, locked, refreshInterval, visibilityRange, linkedZone);
    }

    /**
     * Returns a copy with locked state changed.
     */
    public HologramOptions withLocked(boolean newLocked) {
        return new HologramOptions(enabled, newLocked, refreshInterval, visibilityRange, linkedZone);
    }
}
