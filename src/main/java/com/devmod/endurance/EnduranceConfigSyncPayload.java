package com.devmod.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.endurance.config.ConfigScope;
import com.devmod.network.PayloadValidation;

/**
 * Payload for syncing Endurance config changes from client to server.
 *
 * Supports 3 permission levels:
 * - GLOBAL: OP 2+ only, permanently modifies server config
 * - PROPOSAL: Testers can propose changes for admin approval
 * - SESSION: Quest host can override settings for current session only
 */
public record EnduranceConfigSyncPayload(
    List<ConfigEntry> entries,
    ConfigScope scope
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /**
     * A single config entry with key, type, and value.
     */
    public record ConfigEntry(String key, String valueType, String value) {
        private static final int MAX_STRING_LENGTH = 256;

        public static ConfigEntry decode(RegistryFriendlyByteBuf buf) {
            String key = buf.readUtf(MAX_STRING_LENGTH);
            String valueType = buf.readUtf(32); // "int", "double", "boolean"
            String value = buf.readUtf(MAX_STRING_LENGTH);
            return new ConfigEntry(key, valueType, value);
        }

        public void encode(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(Objects.requireNonNull(key));
            buf.writeUtf(Objects.requireNonNull(valueType));
            buf.writeUtf(Objects.requireNonNull(value));
        }

        public int estimatedSize() {
            return varIntSize(key.length()) + key.length()
                 + varIntSize(valueType.length()) + valueType.length()
                 + varIntSize(value.length()) + value.length();
        }

        private static int varIntSize(int value) {
            int size = 1;
            while ((value & ~0x7F) != 0) {
                value >>>= 7;
                size++;
            }
            return size;
        }
    }

    public static final Type<EnduranceConfigSyncPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "endurance_config_sync"))
    );

    // Security limits
    private static final int MAX_ENTRIES = 50;

    public static final StreamCodec<RegistryFriendlyByteBuf, EnduranceConfigSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EnduranceConfigSyncPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            // Read scope
            int scopeOrdinal = buf.readVarInt();
            ConfigScope scope = ConfigScope.values()[Math.min(scopeOrdinal, ConfigScope.values().length - 1)];

            // Read entries
            int entryCount = buf.readVarInt();
            entryCount = Math.min(entryCount, MAX_ENTRIES); // Security limit

            List<ConfigEntry> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                entries.add(ConfigEntry.decode(buf));
            }

            return new EnduranceConfigSyncPayload(entries, scope);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull EnduranceConfigSyncPayload payload) {
            // Write scope
            buf.writeVarInt(payload.scope.ordinal());

            // Write entries (limit to MAX_ENTRIES)
            int count = Math.min(payload.entries.size(), MAX_ENTRIES);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                payload.entries.get(i).encode(buf);
            }
        }
    };

    /**
     * Create a payload for a single config change.
     */
    public EnduranceConfigSyncPayload(String key, String valueType, String value, ConfigScope scope) {
        this(List.of(new ConfigEntry(key, valueType, value)), scope);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = varIntSize(scope.ordinal()); // scope ordinal as VarInt
        size += varIntSize(entries.size());
        for (ConfigEntry entry : entries) {
            size += entry.estimatedSize();
        }
        return size;
    }

    private static int varIntSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }
}
