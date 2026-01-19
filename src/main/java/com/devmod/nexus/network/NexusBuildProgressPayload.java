package com.devmod.nexus.network;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;

/**
 * Server -> Client: Nexus build progress update.
 * Sent each time a build step completes or when build finishes.
 *
 * <p>This payload enables the client to show a progress bar during
 * Nexus foundation construction, so users know the game isn't frozen.
 */
public record NexusBuildProgressPayload(
    int currentStep,      // Current step (0-based)
    int totalSteps,       // Total number of steps
    @Nullable String stepName,  // Name of current step (e.g., "floor", "corridors")
    boolean complete      // Build completed flag
) implements CustomPacketPayload {

    public static final Type<NexusBuildProgressPayload> TYPE =
        new Type<>(Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "nexus_build_progress")));

    public static final StreamCodec<FriendlyByteBuf, NexusBuildProgressPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.currentStep);
            buf.writeVarInt(payload.totalSteps);
            buf.writeBoolean(payload.stepName != null);
            if (payload.stepName != null) {
                buf.writeUtf(payload.stepName);
            }
            buf.writeBoolean(payload.complete);
        },
        buf -> new NexusBuildProgressPayload(
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readBoolean() ? buf.readUtf() : null,
            buf.readBoolean()
        )
    );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return Objects.requireNonNull(TYPE);
    }

    /**
     * Calculate the progress percentage (0.0 - 1.0).
     */
    public float getProgressPercent() {
        if (totalSteps <= 0) return 0.0f;
        if (complete) return 1.0f;
        return Math.min(1.0f, (float) currentStep / totalSteps);
    }

    /**
     * Create a progress payload for an in-progress build.
     */
    public static NexusBuildProgressPayload progress(int current, int total, @Nullable String stepName) {
        return new NexusBuildProgressPayload(current, total, stepName, false);
    }

    /**
     * Create a completion payload.
     */
    public static NexusBuildProgressPayload completed(int total) {
        return new NexusBuildProgressPayload(total, total, null, true);
    }
}
