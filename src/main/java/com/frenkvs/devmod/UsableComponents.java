package com.frenkvs.devmod;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Data components for usable item persistent data.
 * Provides a typed container for usable stats (cooldowns, use duration, throwable properties).
 */
public final class UsableComponents {
    private UsableComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Objects.requireNonNull(Registries.DATA_COMPONENT_TYPE), DevMod.MODID);

    private static final StreamCodec<RegistryFriendlyByteBuf, CompoundTag> USABLE_TAG_STREAM_CODEC =
        StreamCodec.of(
            (buf, tag) -> ByteBufCodecs.COMPOUND_TAG.encode(buf, tag),
            buf -> ByteBufCodecs.COMPOUND_TAG.decode(buf)
        );

    /**
     * Serialized UsableStats payload.
     * Uses CompoundTag for flexibility.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> USABLE_STATS =
        COMPONENTS.register("usable_stats", () -> DataComponentType.<CompoundTag>builder()
            .persistent(Objects.requireNonNull(CompoundTag.CODEC))
            .networkSynchronized(Objects.requireNonNull(USABLE_TAG_STREAM_CODEC))
            .build());

    // Fallback instance for test environments where Neo registries are not bound.
    private static DataComponentType<CompoundTag> fallbackUsableStats;

    /**
     * Returns the registered usable_stats component when bound, otherwise a local
     * fallback instance so JVM tests can still store/read data without registry binding.
     * Returns null if component is not bound and fallback is not enabled.
     */
    @Nullable
    public static DataComponentType<CompoundTag> usableStatsComponent() {
        try {
            if (USABLE_STATS.isBound()) {
                return USABLE_STATS.get();
            }
        } catch (Exception ignored) {}

        if (Boolean.getBoolean("devmod.allowFallbackComponents")) {
            DevMod.LOGGER.warn("[UsableComponents] Using fallback usable_stats component (test-mode only)");
            return fallbackUsableStats();
        }
        return null;
    }

    /**
     * Check if the usable_stats component is bound to the registry.
     */
    public static boolean isUsableStatsBound() {
        try {
            return USABLE_STATS.isBound();
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    private static synchronized DataComponentType<CompoundTag> fallbackUsableStats() {
        if (fallbackUsableStats == null) {
            fallbackUsableStats = DataComponentType.<CompoundTag>builder()
                .persistent(Objects.requireNonNull(CompoundTag.CODEC))
                .networkSynchronized(Objects.requireNonNull(USABLE_TAG_STREAM_CODEC))
                .build();
        }
        return fallbackUsableStats;
    }
}
