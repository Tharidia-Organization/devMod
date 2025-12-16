package net.neoforged.neoforge.registries;

import java.util.function.Supplier;

/**
 * Minimal stub for DeferredRegister to allow main classes to load in tests.
 */
public class DeferredRegister<T> {
    public static <T> DeferredRegister<T> create(Object registry, String modid) {
        return new DeferredRegister<>();
    }

    public static <T> DeferredRegister<T> create(net.minecraft.resources.ResourceKey<?> registry, String modid) {
        return new DeferredRegister<>();
    }

    public <I> DeferredHolder<T, I> register(String name, Supplier<I> supplier) {
        return new DeferredHolder<>(java.util.Objects.requireNonNull(supplier.get()));
    }
}
