package com.devmod.components;
import java.util.Objects;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;

/**
 * Data components for food item persistent data.
 * Provides a typed container for food stats (nutrition, saturation, effects).
 */
public final class FoodComponents {
    private FoodComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Objects.requireNonNull(Registries.DATA_COMPONENT_TYPE), DevMod.MODID);

    private static final StreamCodec<RegistryFriendlyByteBuf, CompoundTag> FOOD_TAG_STREAM_CODEC =
        StreamCodec.of(
            (buf, tag) -> ByteBufCodecs.COMPOUND_TAG.encode(buf, tag),
            buf -> ByteBufCodecs.COMPOUND_TAG.decode(buf)
        );

    /**
     * Serialized FoodStats payload.
     * Uses CompoundTag for flexibility.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> FOOD_STATS =
        COMPONENTS.register("food_stats", () -> DataComponentType.<CompoundTag>builder()
            .persistent(Objects.requireNonNull(CompoundTag.CODEC))
            .networkSynchronized(Objects.requireNonNull(FOOD_TAG_STREAM_CODEC))
            .build());

    // Fallback instance for test environments where Neo registries are not bound.
    private static DataComponentType<CompoundTag> fallbackFoodStats;

    /**
     * Returns the registered food_stats component when bound, otherwise a local
     * fallback instance so JVM tests can still store/read data without registry binding.
     * Returns null if component is not bound and fallback is not enabled.
     */
    public static DataComponentType<CompoundTag> foodStatsComponent() {
        try {
            if (FOOD_STATS.isBound()) {
                return FOOD_STATS.get();
            }
        } catch (Exception ignored) {}

        if (Boolean.getBoolean("devmod.allowFallbackComponents")) {
            DevMod.LOGGER.warn("[FoodComponents] Using fallback food_stats component (test-mode only)");
            return fallbackFoodStats();
        }
        return null;
    }

    /**
     * Check if the food_stats component is bound to the registry.
     */
    public static boolean isFoodStatsBound() {
        try {
            return FOOD_STATS.isBound();
        } catch (Exception e) {
            return false;
        }
    }

    private static synchronized DataComponentType<CompoundTag> fallbackFoodStats() {
        if (fallbackFoodStats == null) {
            fallbackFoodStats = DataComponentType.<CompoundTag>builder()
                .persistent(Objects.requireNonNull(CompoundTag.CODEC))
                .networkSynchronized(Objects.requireNonNull(FOOD_TAG_STREAM_CODEC))
                .build();
        }
        return fallbackFoodStats;
    }
}
