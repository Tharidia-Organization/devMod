package com.devmod.network.protocol;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Unified message envelope for DevMod network protocol.
 *
 * <p>Wraps all payload types in a consistent structure:
 * <ul>
 *   <li>Type - The message type enum</li>
 *   <li>Correlation ID - For request/response matching</li>
 *   <li>Timestamp - When the message was created</li>
 *   <li>Payload - The actual data (type-specific)</li>
 * </ul>
 *
 * <p>Benefits:
 * <ul>
 *   <li>Single registration point for all message types</li>
 *   <li>Consistent error handling</li>
 *   <li>Built-in request correlation</li>
 *   <li>Unified logging and telemetry</li>
 * </ul>
 *
 * @param <T> The payload type
 */
public record MessageEnvelope<T>(
    MessageType type,
    UUID correlationId,
    long timestamp,
    @Nullable T payload,
    int version
) {

    /** Current protocol version */
    public static final int CURRENT_VERSION = 1;

    /**
     * Create a new envelope with auto-generated correlation ID.
     */
    public static <T> MessageEnvelope<T> create(MessageType type, T payload) {
        return new MessageEnvelope<>(
            type,
            UUID.randomUUID(),
            System.currentTimeMillis(),
            payload,
            CURRENT_VERSION
        );
    }

    /**
     * Create a response envelope with the same correlation ID.
     */
    public <R> MessageEnvelope<R> respond(MessageType responseType, R responsePayload) {
        return new MessageEnvelope<>(
            responseType,
            correlationId,
            System.currentTimeMillis(),
            responsePayload,
            CURRENT_VERSION
        );
    }

    /**
     * Create an error response.
     */
    public MessageEnvelope<ErrorPayload> error(String errorCode, String message) {
        return new MessageEnvelope<>(
            type,
            correlationId,
            System.currentTimeMillis(),
            new ErrorPayload(errorCode, message),
            CURRENT_VERSION
        );
    }

    /**
     * Check if this is a valid version.
     */
    public boolean isValidVersion() {
        return version == CURRENT_VERSION;
    }

    /**
     * Get age in milliseconds.
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * Check if message is stale (older than threshold).
     */
    public boolean isStale(long maxAgeMs) {
        return getAgeMs() > maxAgeMs;
    }

    /**
     * Get short correlation ID for logging.
     */
    public String getShortCorrelationId() {
        return correlationId.toString().substring(0, 8);
    }

    /**
     * Error payload for error responses.
     */
    public record ErrorPayload(
        String errorCode,
        String message
    ) {}

    /**
     * Builder for constructing envelopes.
     */
    public static class Builder<T> {
        @Nullable
        private MessageType type;
        private UUID correlationId = UUID.randomUUID();
        private long timestamp = System.currentTimeMillis();
        @Nullable
        private T payload;
        private int version = CURRENT_VERSION;

        public Builder<T> type(MessageType type) {
            this.type = type;
            return this;
        }

        public Builder<T> correlationId(UUID correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder<T> timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        public Builder<T> version(int version) {
            this.version = version;
            return this;
        }

        public MessageEnvelope<T> build() {
            if (type == null) {
                throw new IllegalStateException("MessageType is required");
            }
            return new MessageEnvelope<>(type, correlationId, timestamp, payload, version);
        }
    }

    /**
     * Create a builder.
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    // ============================================================================
    // CODEC SUPPORT
    // ============================================================================

    /**
     * Write envelope header to buffer (without payload).
     */
    public void writeHeader(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeUUID(correlationId);
        buf.writeLong(timestamp);
        buf.writeVarInt(version);
    }

    /**
     * Read envelope header from buffer.
     */
    public static MessageEnvelope<Void> readHeader(FriendlyByteBuf buf) {
        MessageType type = buf.readEnum(MessageType.class);
        UUID correlationId = buf.readUUID();
        long timestamp = buf.readLong();
        int version = buf.readVarInt();
        return new MessageEnvelope<>(type, correlationId, timestamp, null, version);
    }

    /**
     * Create a stream codec for envelopes with a specific payload codec.
     */
    public static <B extends FriendlyByteBuf, T> StreamCodec<B, MessageEnvelope<T>> codec(
            StreamCodec<B, T> payloadCodec) {
        return new StreamCodec<>() {
            @Override
            public MessageEnvelope<T> decode(B buf) {
                MessageType type = buf.readEnum(MessageType.class);
                UUID correlationId = buf.readUUID();
                long timestamp = buf.readLong();
                int version = buf.readVarInt();
                T payload = payloadCodec.decode(buf);
                return new MessageEnvelope<>(type, correlationId, timestamp, payload, version);
            }

            @Override
            public void encode(B buf, MessageEnvelope<T> envelope) {
                buf.writeEnum(envelope.type());
                buf.writeUUID(envelope.correlationId());
                buf.writeLong(envelope.timestamp());
                buf.writeVarInt(envelope.version());
                if (envelope.payload() != null) {
                    payloadCodec.encode(buf, envelope.payload());
                }
            }
        };
    }
}
