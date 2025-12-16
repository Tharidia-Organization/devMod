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

/**
 * Data components for armor-related persistent data.
 * Mirrors WeaponComponents to provide a typed container for armor stats.
 */
public final class ArmorComponents {
    private ArmorComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Objects.requireNonNull(Registries.DATA_COMPONENT_TYPE), DevMod.MODID);

    private static final StreamCodec<RegistryFriendlyByteBuf, CompoundTag> ARMOR_TAG_STREAM_CODEC =
        StreamCodec.of(
            (buf, tag) -> ByteBufCodecs.COMPOUND_TAG.encode(buf, tag),
            buf -> ByteBufCodecs.COMPOUND_TAG.decode(buf)
        );

    /**
     * Serialized ArmorStats payload (replacement for legacy "ArmorModStats" NBT).
     * Stored as CompoundTag for flexibility while migrating off CustomData.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> ARMOR_STATS =
        COMPONENTS.register("armor_stats", () -> DataComponentType.<CompoundTag>builder()
            .persistent(Objects.requireNonNull(CompoundTag.CODEC))
            .networkSynchronized(Objects.requireNonNull(ARMOR_TAG_STREAM_CODEC))
            .build());
}
