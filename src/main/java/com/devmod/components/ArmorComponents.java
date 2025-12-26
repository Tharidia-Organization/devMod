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

        if (Boolean.getBoolean("devmod.allowFallbackComponents")) {
            DevMod.LOGGER.warn("[ArmorComponents] Using fallback armor_stats component (test-mode only)");
            return fallbackArmorStats();
        }
        throw new IllegalStateException("[ArmorComponents] armor_stats component is not bound; DeferredRegister not initialized");
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
