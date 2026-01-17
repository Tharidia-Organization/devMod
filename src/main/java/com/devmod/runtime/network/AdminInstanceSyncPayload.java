package com.devmod.runtime.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.network.PayloadValidation;

/**
 * Server-to-client payload for syncing instance data to the admin panel.
 * Sent periodically (auto-refresh) or on manual refresh request.
 */
public record AdminInstanceSyncPayload(
    List<InstanceSnapshot> instances,
    int totalPlayers,
    int activeCount,
    int pendingDestructionCount
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    // Security limits
    private static final int MAX_INSTANCES = 100;
    private static final int MAX_PLAYERS_PER_INSTANCE = 50;
    private static final int MAX_STRING_LENGTH = 128;

    public static final Type<AdminInstanceSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "admin_instance_sync"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminInstanceSyncPayload> STREAM_CODEC = StreamCodec.of(
        AdminInstanceSyncPayload::encode,
        AdminInstanceSyncPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, AdminInstanceSyncPayload payload) {
        // Write instances list
        buf.writeVarInt(payload.instances.size());
        for (InstanceSnapshot instance : payload.instances) {
            buf.writeUUID(Objects.requireNonNull(instance.instanceId));
            buf.writeVarInt(instance.stateOrdinal);
            buf.writeUtf(instance.arenaTemplate != null ? instance.arenaTemplate : "");

            // Write player names list
            buf.writeVarInt(instance.playerNames.size());
            for (String name : instance.playerNames) {
                buf.writeUtf(name != null ? name : "");
            }

            buf.writeVarInt(instance.currentWave);
            buf.writeVarInt(instance.totalWaves);
            buf.writeLong(instance.ageSeconds);
            buf.writeVarInt(instance.playerCount);
            buf.writeVarInt(instance.maxPlayers);
        }

        buf.writeVarInt(payload.totalPlayers);
        buf.writeVarInt(payload.activeCount);
        buf.writeVarInt(payload.pendingDestructionCount);
    }

    private static AdminInstanceSyncPayload decode(RegistryFriendlyByteBuf buf) {
        // Read instances list with security bounds
        int instanceCount = buf.readVarInt();
        if (instanceCount < 0 || instanceCount > MAX_INSTANCES) {
            instanceCount = 0;
        }

        List<InstanceSnapshot> instances = new ArrayList<>(instanceCount);
        for (int i = 0; i < instanceCount; i++) {
            UUID instanceId = Objects.requireNonNull(buf.readUUID());
            int stateOrdinal = buf.readVarInt();
            String arenaTemplate = buf.readUtf(MAX_STRING_LENGTH);
            if (arenaTemplate.isEmpty()) {
                arenaTemplate = null;
            }

            // Read player names list with security bounds
            int playerCount = buf.readVarInt();
            if (playerCount < 0 || playerCount > MAX_PLAYERS_PER_INSTANCE) {
                playerCount = 0;
            }
            List<String> playerNames = new ArrayList<>(playerCount);
            for (int j = 0; j < playerCount; j++) {
                String name = buf.readUtf(MAX_STRING_LENGTH);
                playerNames.add(name.isEmpty() ? null : name);
            }

            int currentWave = buf.readVarInt();
            int totalWaves = buf.readVarInt();
            long ageSeconds = buf.readLong();
            int readPlayerCount = buf.readVarInt();
            int maxPlayers = buf.readVarInt();

            instances.add(new InstanceSnapshot(
                instanceId,
                stateOrdinal,
                arenaTemplate,
                playerNames,
                currentWave,
                totalWaves,
                ageSeconds,
                readPlayerCount,
                maxPlayers
            ));
        }

        int totalPlayers = buf.readVarInt();
        int activeCount = buf.readVarInt();
        int pendingDestructionCount = buf.readVarInt();

        return new AdminInstanceSyncPayload(instances, totalPlayers, activeCount, pendingDestructionCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = varIntSize(instances.size());
        for (InstanceSnapshot instance : instances) {
            size += 16; // UUID
            size += varIntSize(instance.stateOrdinal);
            size += estimatedUtfSize(instance.arenaTemplate);
            size += varIntSize(instance.playerNames.size());
            for (String name : instance.playerNames) {
                size += estimatedUtfSize(name);
            }
            size += varIntSize(instance.currentWave);
            size += varIntSize(instance.totalWaves);
            size += 8; // ageSeconds (long)
            size += varIntSize(instance.playerCount);
            size += varIntSize(instance.maxPlayers);
        }
        size += varIntSize(totalPlayers);
        size += varIntSize(activeCount);
        size += varIntSize(pendingDestructionCount);
        return size;
    }

    private static int estimatedUtfSize(String value) {
        if (value == null || value.isEmpty()) {
            return varIntSize(0);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return varIntSize(bytes.length) + bytes.length;
    }

    private static int varIntSize(int value) {
        int v = value;
        int size = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            size++;
        }
        return size;
    }

    /**
     * Create an empty payload (no instances).
     */
    public static AdminInstanceSyncPayload empty() {
        return new AdminInstanceSyncPayload(List.of(), 0, 0, 0);
    }

    /**
     * Snapshot of a single instance for the admin panel.
     */
    public record InstanceSnapshot(
        UUID instanceId,
        int stateOrdinal,
        String arenaTemplate,
        List<String> playerNames,
        int currentWave,
        int totalWaves,
        long ageSeconds,
        int playerCount,
        int maxPlayers
    ) {
        /**
         * Get instance state display string.
         */
        public String getStateDisplay() {
            return switch (stateOrdinal) {
                case 0 -> "\u00A77CREATING";
                case 1 -> "\u00A7bREADY";
                case 2 -> "\u00A7aACTIVE";
                case 3 -> "\u00A7eCOMPLETING";
                case 4 -> "\u00A7cDESTROYING";
                case 5 -> "\u00A78DESTROYED";
                default -> "\u00A7fUNKNOWN";
            };
        }

        /**
         * Get formatted duration string (MM:SS or HH:MM:SS).
         */
        public String getDurationDisplay() {
            long seconds = ageSeconds;
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;

            if (hours > 0) {
                return String.format("%d:%02d:%02d", hours, minutes, secs);
            }
            return String.format("%d:%02d", minutes, secs);
        }

        /**
         * Get formatted wave progress string.
         */
        public String getWaveDisplay() {
            if (totalWaves <= 0) {
                return "N/A";
            }
            return String.format("%d/%d", currentWave, totalWaves);
        }

        /**
         * Get formatted player count string.
         */
        public String getPlayerCountDisplay() {
            if (maxPlayers <= 0) {
                return String.valueOf(playerCount);
            }
            return String.format("%d/%d", playerCount, maxPlayers);
        }

        /**
         * Check if instance is empty (no players).
         */
        public boolean isEmpty() {
            return playerCount == 0;
        }

        /**
         * Check if instance is in active state.
         */
        public boolean isActive() {
            return stateOrdinal == 2; // ACTIVE
        }

        /**
         * Check if instance is pending destruction.
         */
        public boolean isPendingDestruction() {
            return stateOrdinal == 4; // DESTROYING
        }
    }
}
