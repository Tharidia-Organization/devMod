package com.devmod.clone;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.devmod.network.NetworkConstants;

/**
 * Tests for clone module network payloads using mock byte buffers.
 * Validates serialization format, size estimation, and edge cases
 * without requiring Minecraft network infrastructure.
 */
class ClonePayloadSerializationTest {

    /**
     * Lightweight mock matching the FriendlyByteBuf read/write contract.
     */
    static class MockBuf {
        private final ByteBuffer buffer = ByteBuffer.allocate(4096);
        private int readPos = 0;

        // -- Write --

        void writeBlockPos(long packed) {
            buffer.putLong(packed);
        }

        void writeUtf(String value, int maxLen) {
            String safe = value.length() > maxLen ? value.substring(0, maxLen) : value;
            byte[] bytes = safe.getBytes(StandardCharsets.UTF_8);
            writeVarInt(bytes.length);
            buffer.put(bytes);
        }

        void writeFloat(float value) {
            buffer.putFloat(value);
        }

        void writeBoolean(boolean value) {
            buffer.put((byte) (value ? 1 : 0));
        }

        void writeUUID(UUID uuid) {
            buffer.putLong(uuid.getMostSignificantBits());
            buffer.putLong(uuid.getLeastSignificantBits());
        }

        void writeVarInt(int value) {
            while ((value & ~0x7F) != 0) {
                buffer.put((byte) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
            buffer.put((byte) value);
        }

        // -- Read --

        long readBlockPos() {
            long v = buffer.getLong(readPos);
            readPos += 8;
            return v;
        }

        String readUtf(int maxLen) {
            int len = readVarInt();
            if (len > maxLen * 4) {
                throw new RuntimeException("String too long: " + len);
            }
            byte[] bytes = new byte[len];
            for (int i = 0; i < len; i++) bytes[i] = buffer.get(readPos++);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        float readFloat() {
            float v = buffer.getFloat(readPos);
            readPos += 4;
            return v;
        }

        boolean readBoolean() {
            return buffer.get(readPos++) != 0;
        }

        UUID readUUID() {
            long most = buffer.getLong(readPos);
            readPos += 8;
            long least = buffer.getLong(readPos);
            readPos += 8;
            return new UUID(most, least);
        }

        int readVarInt() {
            int value = 0, shift = 0;
            byte b;
            do {
                b = buffer.get(readPos++);
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            return value;
        }

        void resetRead() { readPos = 0; }
        int written() { return buffer.position(); }
    }

    // ========================================================================
    // TelepadConfigPayload mock
    // ========================================================================

    record MockTelepadConfig(long packedPos, String telepadName) {
        static final int MAX_NAME = NetworkConstants.MAX_SMALL_STRING;

        void write(MockBuf buf) {
            buf.writeBlockPos(packedPos);
            String safe = NetworkConstants.truncate(telepadName, MAX_NAME);
            buf.writeUtf(safe, MAX_NAME);
        }

        static MockTelepadConfig read(MockBuf buf) {
            return new MockTelepadConfig(buf.readBlockPos(), buf.readUtf(MAX_NAME));
        }
    }

    // ========================================================================
    // TelepadOpenScreenPayload mock
    // ========================================================================

    record MockTelepadOpenScreen(long packedPos, String telepadName) {
        static final int MAX_NAME = NetworkConstants.MAX_SMALL_STRING;

        void write(MockBuf buf) {
            buf.writeBlockPos(packedPos);
            String safe = NetworkConstants.truncate(telepadName, MAX_NAME);
            buf.writeUtf(safe, MAX_NAME);
        }

        static MockTelepadOpenScreen read(MockBuf buf) {
            return new MockTelepadOpenScreen(buf.readBlockPos(), buf.readUtf(MAX_NAME));
        }
    }

    // ========================================================================
    // MannequinRotationPayload mock
    // ========================================================================

    record MockMannequinRotation(long packedPos, float rotationDelta) {
        void write(MockBuf buf) {
            buf.writeBlockPos(packedPos);
            buf.writeFloat(rotationDelta);
        }

        static MockMannequinRotation read(MockBuf buf) {
            return new MockMannequinRotation(buf.readBlockPos(), buf.readFloat());
        }
    }

    // ========================================================================
    // MannequinSkinPayload mock
    // ========================================================================

    record MockMannequinSkin(long packedPos, UUID skinUUID) {
        void write(MockBuf buf) {
            buf.writeBlockPos(packedPos);
            buf.writeBoolean(skinUUID != null);
            if (skinUUID != null) buf.writeUUID(skinUUID);
        }

        static MockMannequinSkin read(MockBuf buf) {
            long pos = buf.readBlockPos();
            boolean hasSkin = buf.readBoolean();
            UUID uuid = hasSkin ? buf.readUUID() : null;
            return new MockMannequinSkin(pos, uuid);
        }
    }

    // ========================================================================
    // TelepadConfigPayload Tests
    // ========================================================================

    @Nested
    @DisplayName("TelepadConfigPayload serialization")
    class TelepadConfigTests {

        @Test
        @DisplayName("Round-trip with typical name")
        void roundTrip() {
            MockTelepadConfig original = new MockTelepadConfig(123456789L, "base_alpha");
            MockBuf buf = new MockBuf();
            original.write(buf);
            buf.resetRead();
            MockTelepadConfig restored = MockTelepadConfig.read(buf);

            assertEquals(original.packedPos(), restored.packedPos());
            assertEquals(original.telepadName(), restored.telepadName());
        }

        @Test
        @DisplayName("Empty name")
        void emptyName() {
            MockTelepadConfig original = new MockTelepadConfig(0L, "");
            MockBuf buf = new MockBuf();
            original.write(buf);
            buf.resetRead();
            MockTelepadConfig restored = MockTelepadConfig.read(buf);
            assertEquals("", restored.telepadName());
        }

        @Test
        @DisplayName("Name exceeding MAX_SMALL_STRING is truncated")
        void nameTruncation() {
            String longName = "a".repeat(NetworkConstants.MAX_SMALL_STRING + 20);
            MockTelepadConfig original = new MockTelepadConfig(0L, longName);
            MockBuf buf = new MockBuf();
            original.write(buf);
            buf.resetRead();
            MockTelepadConfig restored = MockTelepadConfig.read(buf);
            assertEquals(NetworkConstants.MAX_SMALL_STRING, restored.telepadName().length());
        }

        @ParameterizedTest
        @ValueSource(strings = {"home", "spawn_point", "arena_01", "test"})
        @DisplayName("Common telepad names serialize correctly")
        void commonNames(String name) {
            MockTelepadConfig original = new MockTelepadConfig(42L, name);
            MockBuf buf = new MockBuf();
            original.write(buf);
            buf.resetRead();
            assertEquals(name, MockTelepadConfig.read(buf).telepadName());
        }
    }

    // ========================================================================
    // TelepadOpenScreenPayload Tests
    // ========================================================================

    @Nested
    @DisplayName("TelepadOpenScreenPayload serialization")
    class TelepadOpenScreenTests {

        @Test
        @DisplayName("Round-trip preserves data")
        void roundTrip() {
            MockTelepadOpenScreen original = new MockTelepadOpenScreen(987654321L, "lobby");
            MockBuf buf = new MockBuf();
            original.write(buf);
            buf.resetRead();
            MockTelepadOpenScreen restored = MockTelepadOpenScreen.read(buf);

            assertEquals(original.packedPos(), restored.packedPos());
            assertEquals(original.telepadName(), restored.telepadName());
        }

        @Test
        @DisplayName("Shares same max name length as TelepadConfigPayload")
        void sharedMaxLength() {
            assertEquals(MockTelepadConfig.MAX_NAME, MockTelepadOpenScreen.MAX_NAME);
        }
    }

    // ========================================================================
    // MannequinRotationPayload Tests
    // ========================================================================

    @Nested
    @DisplayName("MannequinRotationPayload serialization")
    class MannequinRotationTests {

        @Test
        @DisplayName("Round-trip with positive delta")
        void positiveDelta() {
            MockMannequinRotation original = new MockMannequinRotation(100L, 15.0f);
            MockBuf buf = new MockBuf();
            original.write(buf);
            buf.resetRead();
            MockMannequinRotation restored = MockMannequinRotation.read(buf);

            assertEquals(100L, restored.packedPos());
            assertEquals(15.0f, restored.rotationDelta(), 0.001f);
        }

        @Test
        @DisplayName("Round-trip with negative delta")
        void negativeDelta() {
            MockMannequinRotation original = new MockMannequinRotation(200L, -30.0f);
            MockBuf buf = new MockBuf();
            original.write(buf);
            buf.resetRead();
            assertEquals(-30.0f, MockMannequinRotation.read(buf).rotationDelta(), 0.001f);
        }

        @Test
        @DisplayName("Zero rotation delta")
        void zeroDelta() {
            MockMannequinRotation original = new MockMannequinRotation(0L, 0.0f);
            MockBuf buf = new MockBuf();
            original.write(buf);
            buf.resetRead();
            assertEquals(0.0f, MockMannequinRotation.read(buf).rotationDelta(), 0.001f);
        }

        @Test
        @DisplayName("Expected wire size: 8 (BlockPos) + 4 (float)")
        void expectedWireSize() {
            MockBuf buf = new MockBuf();
            new MockMannequinRotation(0L, 1.0f).write(buf);
            assertEquals(12, buf.written());
        }
    }

    // ========================================================================
    // MannequinSkinPayload Tests
    // ========================================================================

    @Nested
    @DisplayName("MannequinSkinPayload serialization")
    class MannequinSkinTests {

        @Test
        @DisplayName("Round-trip with skin UUID")
        void withSkin() {
            UUID uuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
            MockMannequinSkin original = new MockMannequinSkin(300L, uuid);
            MockBuf buf = new MockBuf();
            original.write(buf);
            buf.resetRead();
            MockMannequinSkin restored = MockMannequinSkin.read(buf);

            assertEquals(300L, restored.packedPos());
            assertEquals(uuid, restored.skinUUID());
        }

        @Test
        @DisplayName("Round-trip with null UUID (reset to Steve)")
        void withoutSkin() {
            MockMannequinSkin original = new MockMannequinSkin(400L, null);
            MockBuf buf = new MockBuf();
            original.write(buf);
            buf.resetRead();
            MockMannequinSkin restored = MockMannequinSkin.read(buf);

            assertEquals(400L, restored.packedPos());
            assertNull(restored.skinUUID());
        }

        @Test
        @DisplayName("Wire size with UUID: 8 + 1 + 16 = 25")
        void wireSizeWithUuid() {
            MockBuf buf = new MockBuf();
            new MockMannequinSkin(0L, UUID.randomUUID()).write(buf);
            assertEquals(25, buf.written());
        }

        @Test
        @DisplayName("Wire size without UUID: 8 + 1 = 9")
        void wireSizeWithoutUuid() {
            MockBuf buf = new MockBuf();
            new MockMannequinSkin(0L, null).write(buf);
            assertEquals(9, buf.written());
        }
    }
}
