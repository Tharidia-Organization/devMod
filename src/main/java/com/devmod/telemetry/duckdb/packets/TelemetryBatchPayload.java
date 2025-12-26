package com.devmod.telemetry.duckdb.packets;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
public record TelemetryBatchPayload(
    long batchTimestamp,
    List<CompressedEvent> events
) implements CustomPacketPayload {

    public static final int MAX_EVENTS_PER_BATCH = 50;
    public static final int MAX_EVENT_DATA_SIZE = 2048;

    public static final CustomPacketPayload.Type<TelemetryBatchPayload> TYPE =
        new CustomPacketPayload.Type<>(Objects.requireNonNull(
            ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "telemetry_batch")));

    public static final StreamCodec<RegistryFriendlyByteBuf, TelemetryBatchPayload> STREAM_CODEC =
        StreamCodec.of(TelemetryBatchPayload::write, TelemetryBatchPayload::read);

    private static TelemetryBatchPayload read(RegistryFriendlyByteBuf buf) {
        long batchTimestamp = buf.readLong();
        int eventCount = buf.readVarInt();

        // Security: Limit events per batch
        int safeCount = Math.min(eventCount, MAX_EVENTS_PER_BATCH);
        List<CompressedEvent> events = new ArrayList<>(safeCount);

        for (int i = 0; i < safeCount; i++) {
            byte typeId = buf.readByte();
            int deltaMs = buf.readVarInt();
            int dataLength = buf.readVarInt();

            // Security: Limit data size per event
            int safeDataLength = Math.min(dataLength, MAX_EVENT_DATA_SIZE);
            byte[] data = new byte[safeDataLength];
            buf.readBytes(data);

            // Skip excess bytes if data was truncated
            if (dataLength > safeDataLength) {
                buf.skipBytes(dataLength - safeDataLength);
            }

            events.add(new CompressedEvent(typeId, deltaMs, data));
        }

        return new TelemetryBatchPayload(batchTimestamp, events);
    }

    private static void write(RegistryFriendlyByteBuf buf, TelemetryBatchPayload payload) {
        buf.writeLong(payload.batchTimestamp);
        int eventCount = Math.min(payload.events.size(), MAX_EVENTS_PER_BATCH);
        buf.writeVarInt(eventCount);

        for (int i = 0; i < eventCount; i++) {
            CompressedEvent event = payload.events.get(i);
            buf.writeByte(event.typeId);
            buf.writeVarInt(event.deltaMs);

            int dataLength = Math.min(event.data.length, MAX_EVENT_DATA_SIZE);
            buf.writeVarInt(dataLength);
            buf.writeBytes(event.data, 0, dataLength);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Event type IDs for efficient network encoding.
     * Using byte values saves bandwidth vs string event types.
     */
    public enum EventType {
        // Combat (0-9)
        COMBAT_HIT(0),
        COMBAT_DEATH(1),
        COMBAT_HEAL(2),
        COMBAT_SPAWN(3),

        // Player (10-19)
        PLAYER_SNAPSHOT(10),
        PLAYER_ABILITY(11),
        PLAYER_ATTRIBUTE_CHANGE(12),

        // Spatial (20-29)
        HEATMAP_UPDATE(20),
        ROOM_TRANSITION(21),

        // Endurance (30-49)
        ENDURANCE_SESSION(30),
        ENDURANCE_WAVE(31),
        ENDURANCE_KILL(32),
        ENDURANCE_COMBO(33),
        ENDURANCE_PERK(34),
        ENDURANCE_REWARD(35),

        // Performance (50-59)
        PERFORMANCE_SAMPLE(50),

        // UI (60-69)
        UI_SCREEN_OPEN(60),
        UI_SCREEN_CLOSE(61),
        UI_ACTION(62);

        public final byte id;

        EventType(int id) {
            this.id = (byte) id;
        }

        @Nullable
        public static EventType fromId(byte id) {
            for (EventType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Compressed telemetry event for efficient network transfer.
     *
     * @param typeId Event type identifier (see EventType enum)
     * @param deltaMs Milliseconds offset from batch timestamp
     * @param data Compressed event data (typically minimal JSON or binary)
     */
    public record CompressedEvent(byte typeId, int deltaMs, byte[] data) {

        /**
         * Get the full timestamp of this event.
         */
        public long getFullTimestamp(long batchTimestamp) {
            return batchTimestamp + deltaMs;
        }

        /**
         * Decode data as UTF-8 string (for JSON events).
         */
        public String getDataAsString() {
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        }

        /**
         * Create from string data.
         */
        public static CompressedEvent fromString(EventType type, int deltaMs, String jsonData) {
            return new CompressedEvent(
                type.id,
                deltaMs,
                jsonData.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        }
    }

    /**
     * Builder for creating telemetry batches.
     */
    public static class Builder {
        private final long batchTimestamp;
        private final List<CompressedEvent> events = new ArrayList<>();

        public Builder() {
            this.batchTimestamp = System.currentTimeMillis();
        }

        public Builder addEvent(EventType type, String jsonData) {
            if (events.size() < MAX_EVENTS_PER_BATCH) {
                int deltaMs = (int) (System.currentTimeMillis() - batchTimestamp);
                events.add(CompressedEvent.fromString(type, deltaMs, jsonData));
            }
            return this;
        }

        public Builder addEvent(CompressedEvent event) {
            if (events.size() < MAX_EVENTS_PER_BATCH) {
                events.add(event);
            }
            return this;
        }

        public boolean isFull() {
            return events.size() >= MAX_EVENTS_PER_BATCH;
        }

        public boolean isEmpty() {
            return events.isEmpty();
        }

        public int size() {
            return events.size();
        }

        public TelemetryBatchPayload build() {
            return new TelemetryBatchPayload(batchTimestamp, new ArrayList<>(events));
        }

        public void clear() {
            events.clear();
        }
    }
}
