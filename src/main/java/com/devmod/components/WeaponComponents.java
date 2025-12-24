package com.devmod.components;
import com.devmod.DevMod;

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
 * Data components for weapon-related persistent data.
 * Provides a typed container for weapon stats to avoid ad-hoc custom data keys.
 */
public final class WeaponComponents {
    private WeaponComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Objects.requireNonNull(Registries.DATA_COMPONENT_TYPE), DevMod.MODID);

    private static final StreamCodec<RegistryFriendlyByteBuf, CompoundTag> WEAPON_TAG_STREAM_CODEC =
        StreamCodec.of(
            (buf, tag) -> ByteBufCodecs.COMPOUND_TAG.encode(buf, tag),
            buf -> ByteBufCodecs.COMPOUND_TAG.decode(buf)
        );

    /**
     * Serialized WeaponStats payload (mirrors legacy "WeaponModStats" tag).
     * Uses CompoundTag for flexibility while transitioning away from raw CustomData keys.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> WEAPON_STATS =
        COMPONENTS.register("weapon_stats", () -> DataComponentType.<CompoundTag>builder()
            .persistent(Objects.requireNonNull(CompoundTag.CODEC))
            .networkSynchronized(Objects.requireNonNull(WEAPON_TAG_STREAM_CODEC))
            .build());

    // Fallback instance for test environments where Neo registries are not bound.
    private static DataComponentType<CompoundTag> fallbackWeaponStats;

    /**
     * Returns the registered weapon_stats component when bound, otherwise a local
     * fallback instance so JVM tests can still store/read data without registry binding.
     * Returns null if component is not bound and fallback is not enabled.
     */
    @Nullable
    public static DataComponentType<CompoundTag> weaponStatsComponent() {
        try {
            if (WEAPON_STATS.isBound()) {
                return WEAPON_STATS.get();
            }
        } catch (Exception ignored) {}

        if (Boolean.getBoolean("devmod.allowFallbackComponents")) {
            DevMod.LOGGER.warn("[WeaponComponents] Using fallback weapon_stats component (test-mode only)");
            return fallbackWeaponStats();
        }
        return null; // Return null instead of throwing - caller must handle
    }

    /**
     * Check if the weapon_stats component is bound to the registry.
     */
    public static boolean isWeaponStatsBound() {
        try {
            return WEAPON_STATS.isBound();
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    private static synchronized DataComponentType<CompoundTag> fallbackWeaponStats() {
        if (fallbackWeaponStats == null) {
            fallbackWeaponStats = DataComponentType.<CompoundTag>builder()
                .persistent(Objects.requireNonNull(CompoundTag.CODEC))
                .networkSynchronized(Objects.requireNonNull(WEAPON_TAG_STREAM_CODEC))
                .build();
        }
        return fallbackWeaponStats;
    }
}
