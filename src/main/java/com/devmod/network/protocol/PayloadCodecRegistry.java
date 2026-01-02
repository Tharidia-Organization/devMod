package com.devmod.network.protocol;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Registry for payload codecs by message type.
 *
 * <p>Provides centralized codec management:
 * <ul>
 *   <li>Register codecs for each MessageType</li>
 *   <li>Lookup codecs at runtime</li>
 *   <li>Type-safe encoding/decoding</li>
 *   <li>Validation and error handling</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Register a codec
 * PayloadCodecRegistry.register(
 *     MessageType.QUEST_SYNC,
 *     QuestSyncData.class,
 *     QuestSyncData.CODEC
 * );
 *
 * // Encode
 * PayloadCodecRegistry.encode(buf, MessageType.QUEST_SYNC, questData);
 *
 * // Decode
 * QuestSyncData data = PayloadCodecRegistry.decode(buf, MessageType.QUEST_SYNC, QuestSyncData.class);
 * }</pre>
 */
public final class PayloadCodecRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadCodecRegistry.class);

    private static final Map<MessageType, CodecEntry<?>> codecs = new ConcurrentHashMap<>();

    private PayloadCodecRegistry() {}

    /**
     * Register a codec for a message type.
     *
     * @param type The message type
     * @param payloadClass The payload class
     * @param codec The stream codec
     */
    public static <T> void register(
            MessageType type,
            Class<T> payloadClass,
            StreamCodec<FriendlyByteBuf, T> codec) {
        CodecEntry<T> entry = new CodecEntry<>(payloadClass, codec);
        CodecEntry<?> existing = codecs.put(type, entry);

        if (existing != null) {
            LOGGER.warn("[PayloadCodecRegistry] Overwriting codec for {} (was {}, now {})",
                type, existing.payloadClass.getSimpleName(), payloadClass.getSimpleName());
        } else {
            LOGGER.debug("[PayloadCodecRegistry] Registered codec for {} -> {}",
                type, payloadClass.getSimpleName());
        }
    }

    /**
     * Register a codec using encoder/decoder functions.
     */
    public static <T> void register(
            MessageType type,
            Class<T> payloadClass,
            Function<FriendlyByteBuf, T> decoder,
            BiConsumer<FriendlyByteBuf, T> encoder) {
        StreamCodec<FriendlyByteBuf, T> codec = new StreamCodec<>() {
            @Override
            public T decode(FriendlyByteBuf buf) {
                return decoder.apply(buf);
            }

            @Override
            public void encode(FriendlyByteBuf buf, T value) {
                encoder.accept(buf, value);
            }
        };
        register(type, payloadClass, codec);
    }

    /**
     * Get the codec for a message type.
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<StreamCodec<FriendlyByteBuf, T>> getCodec(MessageType type) {
        CodecEntry<?> entry = codecs.get(type);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of((StreamCodec<FriendlyByteBuf, T>) entry.codec);
    }

    /**
     * Get the payload class for a message type.
     */
    public static Optional<Class<?>> getPayloadClass(MessageType type) {
        CodecEntry<?> entry = codecs.get(type);
        return entry != null ? Optional.of(entry.payloadClass) : Optional.empty();
    }

    /**
     * Check if a codec is registered for a message type.
     */
    public static boolean hasCodec(MessageType type) {
        return codecs.containsKey(type);
    }

    /**
     * Encode a payload to a buffer.
     *
     * @throws IllegalStateException if no codec is registered
     */
    @SuppressWarnings("unchecked")
    public static <T> void encode(FriendlyByteBuf buf, MessageType type, T payload) {
        CodecEntry<?> entry = codecs.get(type);
        if (entry == null) {
            throw new IllegalStateException("No codec registered for " + type);
        }

        try {
            ((StreamCodec<FriendlyByteBuf, T>) entry.codec).encode(buf, payload);
        } catch (Exception e) {
            LOGGER.error("[PayloadCodecRegistry] Failed to encode {} payload", type, e);
            throw new RuntimeException("Failed to encode " + type + " payload", e);
        }
    }

    /**
     * Decode a payload from a buffer.
     *
     * @throws IllegalStateException if no codec is registered
     */
    public static <T> T decode(FriendlyByteBuf buf, MessageType type, Class<T> expectedType) {
        CodecEntry<?> entry = codecs.get(type);
        if (entry == null) {
            throw new IllegalStateException("No codec registered for " + type);
        }

        try {
            Object decoded = entry.codec.decode(buf);
            if (!expectedType.isInstance(decoded)) {
                throw new IllegalStateException("Decoded payload type mismatch for " + type
                    + ": expected " + expectedType.getName()
                    + " but got " + (decoded != null ? decoded.getClass().getName() : "null"));
            }
            return expectedType.cast(decoded);
        } catch (Exception e) {
            LOGGER.error("[PayloadCodecRegistry] Failed to decode {} payload", type, e);
            throw new RuntimeException("Failed to decode " + type + " payload", e);
        }
    }

    /**
     * Try to decode a payload from a buffer.
     *
     * @return The decoded payload, or null if decoding failed
     */
    @Nullable
    public static <T> T tryDecode(FriendlyByteBuf buf, MessageType type, Class<T> expectedType) {
        CodecEntry<?> entry = codecs.get(type);
        if (entry == null) {
            LOGGER.warn("[PayloadCodecRegistry] No codec registered for {}", type);
            return null;
        }

        try {
            Object decoded = entry.codec.decode(buf);
            if (!expectedType.isInstance(decoded)) {
                LOGGER.warn("[PayloadCodecRegistry] Decoded type mismatch for {} (expected {}, got {})",
                    type, expectedType.getName(),
                    decoded != null ? decoded.getClass().getName() : "null");
                return null;
            }
            return expectedType.cast(decoded);
        } catch (Exception e) {
            LOGGER.error("[PayloadCodecRegistry] Failed to decode {} payload", type, e);
            return null;
        }
    }

    /**
     * Get the number of registered codecs.
     */
    public static int size() {
        return codecs.size();
    }

    /**
     * Clear all registered codecs (for testing).
     */
    public static void clear() {
        codecs.clear();
        LOGGER.info("[PayloadCodecRegistry] Cleared all codecs");
    }

    /**
     * Get diagnostics info.
     */
    public static String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("PayloadCodecRegistry (").append(codecs.size()).append(" codecs):\n");

        for (MessageType.Domain domain : MessageType.Domain.values()) {
            long count = codecs.keySet().stream()
                .filter(t -> t.getDomain() == domain)
                .count();
            sb.append("  ").append(domain).append(": ").append(count).append("\n");
        }

        return sb.toString();
    }

    /**
     * Internal codec entry.
     */
    private record CodecEntry<T>(
        Class<T> payloadClass,
        StreamCodec<FriendlyByteBuf, T> codec
    ) {}
}
