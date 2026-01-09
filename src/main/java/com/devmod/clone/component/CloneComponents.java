package com.devmod.clone.component;

import java.util.Objects;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;
import com.devmod.clone.data.BioscanData;

/**
 * Data component registry for the clone module.
 * Provides BIOSCAN_DATA component for storing scanned entity data.
 */
public final class CloneComponents {
    private CloneComponents() {}

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Objects.requireNonNull(Registries.DATA_COMPONENT_TYPE), DevMod.MODID);

    /**
     * Bioscan data component for storing scanned entity information.
     * Used by BIOSCANNER item and read by NEUROCELL for cloning.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BioscanData>> BIOSCAN_DATA =
        COMPONENTS.register("bioscan_data", () -> DataComponentType.<BioscanData>builder()
            .persistent(Objects.requireNonNull(BioscanData.CODEC))
            .networkSynchronized(Objects.requireNonNull(BioscanData.STREAM_CODEC))
            .build());

    /**
     * Initialize the component registry (call from CloneModule).
     */
    public static void init() {
        // Static initialization
    }
}
