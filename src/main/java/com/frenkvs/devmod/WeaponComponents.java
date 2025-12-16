package com.frenkvs.devmod;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data components for weapon-related persistent data.
 * Provides a typed container for weapon stats to avoid ad-hoc custom data keys.
 */
public final class WeaponComponents {
    private WeaponComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(java.util.Objects.requireNonNull(Registries.DATA_COMPONENT_TYPE), DevMod.MODID);

    /**
     * Serialized WeaponStats payload (mirrors legacy "WeaponModStats" tag).
     * Uses CompoundTag for flexibility while transitioning away from raw CustomData keys.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> WEAPON_STATS =
        COMPONENTS.register("weapon_stats", () -> DataComponentType.<CompoundTag>builder()
            .persistent(java.util.Objects.requireNonNull(CompoundTag.CODEC))
            .networkSynchronized(java.util.Objects.requireNonNull(ByteBufCodecs.COMPOUND_TAG))
            .build());
}
