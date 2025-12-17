package com.frenkvs.devmod;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.mojang.serialization.Codec;

import java.util.Objects;

/**
 * Data components for ranged weapons (doc 16).
 * These mirror the CustomData-based fields we already persist, enabling future migration to native components.
 */
public final class RangedComponents {
    private RangedComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Objects.requireNonNull(Registries.DATA_COMPONENT_TYPE), DevMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> DRAW_TIME_TICKS =
        COMPONENTS.register("draw_time_ticks", () -> DataComponentType.<Float>builder().persistent(Objects.requireNonNull(Codec.FLOAT)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> PROJECTILE_SPEED =
        COMPONENTS.register("projectile_speed", () -> DataComponentType.<Float>builder().persistent(Objects.requireNonNull(Codec.FLOAT)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> PROJECTILE_GRAVITY =
        COMPONENTS.register("projectile_gravity", () -> DataComponentType.<Float>builder().persistent(Objects.requireNonNull(Codec.FLOAT)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> PROJECTILE_SPREAD =
        COMPONENTS.register("projectile_spread", () -> DataComponentType.<Float>builder().persistent(Objects.requireNonNull(Codec.FLOAT)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> BASE_ARROW_DAMAGE =
        COMPONENTS.register("base_arrow_damage", () -> DataComponentType.<Float>builder().persistent(Objects.requireNonNull(Codec.FLOAT)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MULTISHOT_COUNT =
        COMPONENTS.register("multishot_count", () -> DataComponentType.<Integer>builder().persistent(Objects.requireNonNull(Codec.INT)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PIERCING_LEVEL =
        COMPONENTS.register("piercing_level", () -> DataComponentType.<Integer>builder().persistent(Objects.requireNonNull(Codec.INT)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> AMMO_TAG_FILTER =
        COMPONENTS.register("ammo_tag_filter", () -> DataComponentType.<ResourceLocation>builder().persistent(Objects.requireNonNull(ResourceLocation.CODEC)).build());

    /**
     * Check if any ranged component is bound (registry initialized).
     * Useful for safe access patterns in runtime code.
     */
    public static boolean isAnyBound() {
        try {
            return DRAW_TIME_TICKS.isBound();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if the ammo tag filter component is bound.
     */
    public static boolean isAmmoFilterBound() {
        try {
            return AMMO_TAG_FILTER.isBound();
        } catch (Exception e) {
            return false;
        }
    }
}
