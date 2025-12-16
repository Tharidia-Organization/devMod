package net.minecraft.core.component;

/**
 * Minimal stub for DataComponentType used in JVM tests.
 */
public interface DataComponentType<T> {
    static <T> Builder<T> builder() {
        return new Builder<>();
    }

    class Builder<T> {
        public Builder<T> persistent(com.mojang.serialization.Codec<? extends T> codec) { return this; }
        public Builder<T> networkSynchronized(net.minecraft.network.codec.StreamCodec<?, ?> codec) { return this; }
        public DataComponentType<T> build() { return new DataComponentType<>() {}; }
    }
}
