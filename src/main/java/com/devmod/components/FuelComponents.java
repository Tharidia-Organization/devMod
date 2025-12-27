package com.devmod.components;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;

public final class FuelComponents {
    private FuelComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Objects.requireNonNull(Registries.DATA_COMPONENT_TYPE), DevMod.MODID);

    private static final StreamCodec<RegistryFriendlyByteBuf, CompoundTag> FUEL_TAG_STREAM_CODEC =
        StreamCodec.of(
            (buf, tag) -> ByteBufCodecs.COMPOUND_TAG.encode(buf, tag),
            buf -> ByteBufCodecs.COMPOUND_TAG.decode(buf)
        );

    /**
     * Serialized FuelStats payload.
     * Uses CompoundTag for flexibility.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> FUEL_STATS =
        COMPONENTS.register("fuel_stats", () -> DataComponentType.<CompoundTag>builder()
            .persistent(Objects.requireNonNull(CompoundTag.CODEC))
            .networkSynchronized(Objects.requireNonNull(FUEL_TAG_STREAM_CODEC))
            .build());

    // Fallback instance for test environments where Neo registries are not bound.
    @Nullable
    private static DataComponentType<CompoundTag> fallbackFuelStats;

    /**
     * Returns the registered fuel_stats component when bound, otherwise a local
     * fallback instance so JVM tests can still store/read data without registry binding.
     * Returns null if component is not bound and fallback is not enabled.
     */
    @Nullable
    public static DataComponentType<CompoundTag> fuelStatsComponent() {
        try {
            if (FUEL_STATS.isBound()) {
                return FUEL_STATS.get();
            }
        } catch (Exception e) {
            DevMod.LOGGER.debug("[FuelComponents] Failed to resolve fuel_stats component", e);
        }

        if (Boolean.getBoolean("devmod.allowFallbackComponents")) {
            DevMod.LOGGER.warn("[FuelComponents] Using fallback fuel_stats component (test-mode only)");
            return fallbackFuelStats();
        }
        return null;
    }

    /**
     * Check if the fuel_stats component is bound to the registry.
     */
    public static boolean isFuelStatsBound() {
        try {
            return FUEL_STATS.isBound();
        } catch (Exception e) {
            DevMod.LOGGER.debug("[FuelComponents] Failed to check fuel_stats binding", e);
            return false;
        }
    }

    private static synchronized DataComponentType<CompoundTag> fallbackFuelStats() {
        if (fallbackFuelStats == null) {
            fallbackFuelStats = DataComponentType.<CompoundTag>builder()
                .persistent(Objects.requireNonNull(CompoundTag.CODEC))
                .networkSynchronized(Objects.requireNonNull(FUEL_TAG_STREAM_CODEC))
                .build();
        }
        return fallbackFuelStats;
    }
}
