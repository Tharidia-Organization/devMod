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

    // Fallback instance for test environments where Neo registries are not bound.
    private static DataComponentType<CompoundTag> fallbackArmorStats;

    /**
     * Returns the registered armor_stats component when bound, otherwise a local
     * fallback instance so JVM tests can still store/read data without registry binding.
     */
    public static DataComponentType<CompoundTag> armorStatsComponent() {
        try {
            if (ARMOR_STATS.isBound()) {
                return ARMOR_STATS.get();
            }
        } catch (Exception ignored) {}
        return fallbackArmorStats();
    }

    public static boolean isArmorStatsBound() {
        try {
            return ARMOR_STATS.isBound();
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    private static synchronized DataComponentType<CompoundTag> fallbackArmorStats() {
        if (fallbackArmorStats == null) {
            fallbackArmorStats = DataComponentType.<CompoundTag>builder()
                .persistent(Objects.requireNonNull(CompoundTag.CODEC))
                .networkSynchronized(Objects.requireNonNull(ARMOR_TAG_STREAM_CODEC))
                .build();
        }
        return fallbackArmorStats;
    }
}
