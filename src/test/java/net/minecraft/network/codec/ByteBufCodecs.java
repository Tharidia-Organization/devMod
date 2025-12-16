package net.minecraft.network.codec;

/**
 * Minimal stub for ByteBufCodecs.
 */
public final class ByteBufCodecs {
    private ByteBufCodecs() {}

    public static final StreamCodec<?, ?> COMPOUND_TAG = new StreamCodec<>() {};
    public static final StreamCodec<?, ?> BOOL = new StreamCodec<>() {};
}
