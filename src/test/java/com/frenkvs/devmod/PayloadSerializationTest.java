package com.frenkvs.devmod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for network payload serialization.
 * Tests encoding/decoding of network packets.
 */
public class PayloadSerializationTest {

    /**
     * Simple ByteBuffer-based stream for testing serialization
     */
    static class MockFriendlyByteBuf {
        private ByteBuffer buffer;
        private int readPosition = 0;

        public MockFriendlyByteBuf() {
            this.buffer = ByteBuffer.allocate(4096);
        }

        public MockFriendlyByteBuf(byte[] data) {
            this.buffer = ByteBuffer.wrap(data);
        }

        // Write methods
        public void writeInt(int value) {
            buffer.putInt(value);
        }

        public void writeFloat(float value) {
            buffer.putFloat(value);
        }

        public void writeDouble(double value) {
            buffer.putDouble(value);
        }

        public void writeBoolean(boolean value) {
            buffer.put((byte) (value ? 1 : 0));
        }

        public void writeByte(byte value) {
            buffer.put(value);
        }

        public void writeUtf(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeVarInt(bytes.length);
            buffer.put(bytes);
        }

        public void writeVarInt(int value) {
            while ((value & ~0x7F) != 0) {
                buffer.put((byte) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
            buffer.put((byte) value);
        }

        public void writeUUID(UUID uuid) {
            buffer.putLong(uuid.getMostSignificantBits());
            buffer.putLong(uuid.getLeastSignificantBits());
        }

        // Read methods
        public int readInt() {
            int value = buffer.getInt(readPosition);
            readPosition += 4;
            return value;
        }

        public float readFloat() {
            float value = buffer.getFloat(readPosition);
            readPosition += 4;
            return value;
        }

        public double readDouble() {
            double value = buffer.getDouble(readPosition);
            readPosition += 8;
            return value;
        }

        public boolean readBoolean() {
            return buffer.get(readPosition++) != 0;
        }

        public byte readByte() {
            return buffer.get(readPosition++);
        }

        public String readUtf() {
            int length = readVarInt();
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) {
                bytes[i] = buffer.get(readPosition++);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }

        public int readVarInt() {
            int value = 0;
            int shift = 0;
            byte b;
            do {
                b = buffer.get(readPosition++);
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            return value;
        }

        public UUID readUUID() {
            long most = buffer.getLong(readPosition);
            readPosition += 8;
            long least = buffer.getLong(readPosition);
            readPosition += 8;
            return new UUID(most, least);
        }

        public byte[] toByteArray() {
            byte[] result = new byte[buffer.position()];
            buffer.rewind();
            buffer.get(result);
            buffer.position(result.length);
            return result;
        }

        public void resetRead() {
            readPosition = 0;
        }

        public int size() {
            return buffer.position();
        }
    }

    /**
     * Mock UpdateMobStatsPayload matching the mod's payload
     */
    static class MockUpdateMobStatsPayload {
        public final int entityId;
        public final float maxHealth;
        public final float attackDamage;
        public final float armor;
        public final float speed;

        public MockUpdateMobStatsPayload(int entityId, float maxHealth, float attackDamage,
                                          float armor, float speed) {
            this.entityId = entityId;
            this.maxHealth = maxHealth;
            this.attackDamage = attackDamage;
            this.armor = armor;
            this.speed = speed;
        }

        public void write(MockFriendlyByteBuf buf) {
            buf.writeInt(entityId);
            buf.writeFloat(maxHealth);
            buf.writeFloat(attackDamage);
            buf.writeFloat(armor);
            buf.writeFloat(speed);
        }

        public static MockUpdateMobStatsPayload read(MockFriendlyByteBuf buf) {
            int entityId = buf.readInt();
            float maxHealth = buf.readFloat();
            float attackDamage = buf.readFloat();
            float armor = buf.readFloat();
            float speed = buf.readFloat();
            return new MockUpdateMobStatsPayload(entityId, maxHealth, attackDamage, armor, speed);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MockUpdateMobStatsPayload other)) return false;
            return entityId == other.entityId &&
                   Float.compare(maxHealth, other.maxHealth) == 0 &&
                   Float.compare(attackDamage, other.attackDamage) == 0 &&
                   Float.compare(armor, other.armor) == 0 &&
                   Float.compare(speed, other.speed) == 0;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(entityId);
            result = 31 * result + Float.hashCode(maxHealth);
            result = 31 * result + Float.hashCode(attackDamage);
            result = 31 * result + Float.hashCode(armor);
            result = 31 * result + Float.hashCode(speed);
            return result;
        }
    }

    /**
     * Mock UpdateWeaponPayload matching the mod's payload
     */
    static class MockUpdateWeaponPayload {
        public final String weaponId;
        public final float baseDamageBonus;
        public final float headMult;
        public final float bodyMult;
        public final float armsMult;
        public final float legsMult;
        public final float armorPenetration;

        public MockUpdateWeaponPayload(String weaponId, float baseDamageBonus, float headMult,
                                       float bodyMult, float armsMult, float legsMult,
                                       float armorPenetration) {
            this.weaponId = weaponId;
            this.baseDamageBonus = baseDamageBonus;
            this.headMult = headMult;
            this.bodyMult = bodyMult;
            this.armsMult = armsMult;
            this.legsMult = legsMult;
            this.armorPenetration = armorPenetration;
        }

        public void write(MockFriendlyByteBuf buf) {
            buf.writeUtf(weaponId);
            buf.writeFloat(baseDamageBonus);
            buf.writeFloat(headMult);
            buf.writeFloat(bodyMult);
            buf.writeFloat(armsMult);
            buf.writeFloat(legsMult);
            buf.writeFloat(armorPenetration);
        }

        public static MockUpdateWeaponPayload read(MockFriendlyByteBuf buf) {
            String weaponId = buf.readUtf();
            float baseDamageBonus = buf.readFloat();
            float headMult = buf.readFloat();
            float bodyMult = buf.readFloat();
            float armsMult = buf.readFloat();
            float legsMult = buf.readFloat();
            float armorPenetration = buf.readFloat();
            return new MockUpdateWeaponPayload(weaponId, baseDamageBonus, headMult,
                                               bodyMult, armsMult, legsMult, armorPenetration);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MockUpdateWeaponPayload other)) return false;
            return weaponId.equals(other.weaponId) &&
                   Float.compare(baseDamageBonus, other.baseDamageBonus) == 0 &&
                   Float.compare(headMult, other.headMult) == 0 &&
                   Float.compare(bodyMult, other.bodyMult) == 0 &&
                   Float.compare(armsMult, other.armsMult) == 0 &&
                   Float.compare(legsMult, other.legsMult) == 0 &&
                   Float.compare(armorPenetration, other.armorPenetration) == 0;
        }

        @Override
        public int hashCode() {
            int result = weaponId.hashCode();
            result = 31 * result + Float.hashCode(baseDamageBonus);
            result = 31 * result + Float.hashCode(headMult);
            result = 31 * result + Float.hashCode(bodyMult);
            result = 31 * result + Float.hashCode(armsMult);
            result = 31 * result + Float.hashCode(legsMult);
            result = 31 * result + Float.hashCode(armorPenetration);
            return result;
        }
    }

    /**
     * Mock EquipMobPayload matching the mod's payload
     */
    static class MockEquipMobPayload {
        public final int entityId;
        public final int slot;
        public final String itemId;

        public MockEquipMobPayload(int entityId, int slot, String itemId) {
            this.entityId = entityId;
            this.slot = slot;
            this.itemId = itemId;
        }

        public void write(MockFriendlyByteBuf buf) {
            buf.writeInt(entityId);
            buf.writeInt(slot);
            buf.writeUtf(itemId);
        }

        public static MockEquipMobPayload read(MockFriendlyByteBuf buf) {
            int entityId = buf.readInt();
            int slot = buf.readInt();
            String itemId = buf.readUtf();
            return new MockEquipMobPayload(entityId, slot, itemId);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof MockEquipMobPayload other)) return false;
            return entityId == other.entityId &&
                   slot == other.slot &&
                   itemId.equals(other.itemId);
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(entityId);
            result = 31 * result + Integer.hashCode(slot);
            result = 31 * result + itemId.hashCode();
            return result;
        }
    }

    // ============================================
    // Basic Serialization Tests
    // ============================================

    @Nested
    @DisplayName("Basic Serialization Tests")
    class BasicSerializationTests {

        @Test
        @DisplayName("Int serialization round-trip")
        void testIntSerialization() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            buf.writeInt(12345);
            buf.writeInt(-67890);
            buf.writeInt(Integer.MAX_VALUE);
            buf.writeInt(Integer.MIN_VALUE);

            buf.resetRead();
            assertEquals(12345, buf.readInt());
            assertEquals(-67890, buf.readInt());
            assertEquals(Integer.MAX_VALUE, buf.readInt());
            assertEquals(Integer.MIN_VALUE, buf.readInt());
        }

        @Test
        @DisplayName("Float serialization round-trip")
        void testFloatSerialization() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            buf.writeFloat(3.14159f);
            buf.writeFloat(-2.71828f);
            buf.writeFloat(Float.MAX_VALUE);
            buf.writeFloat(Float.MIN_VALUE);

            buf.resetRead();
            assertEquals(3.14159f, buf.readFloat(), 0.00001f);
            assertEquals(-2.71828f, buf.readFloat(), 0.00001f);
            assertEquals(Float.MAX_VALUE, buf.readFloat(), 0.00001f);
            assertEquals(Float.MIN_VALUE, buf.readFloat(), 0.00001f);
        }

        @Test
        @DisplayName("Boolean serialization round-trip")
        void testBooleanSerialization() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            buf.writeBoolean(true);
            buf.writeBoolean(false);
            buf.writeBoolean(true);

            buf.resetRead();
            assertTrue(buf.readBoolean());
            assertFalse(buf.readBoolean());
            assertTrue(buf.readBoolean());
        }

        @Test
        @DisplayName("String serialization round-trip")
        void testStringSerialization() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            buf.writeUtf("Hello, World!");
            buf.writeUtf("minecraft:diamond_sword");
            buf.writeUtf("");

            buf.resetRead();
            assertEquals("Hello, World!", buf.readUtf());
            assertEquals("minecraft:diamond_sword", buf.readUtf());
            assertEquals("", buf.readUtf());
        }

        @Test
        @DisplayName("UUID serialization round-trip")
        void testUUIDSerialization() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            UUID uuid1 = UUID.randomUUID();
            UUID uuid2 = UUID.fromString("12345678-1234-1234-1234-123456789abc");

            buf.writeUUID(uuid1);
            buf.writeUUID(uuid2);

            buf.resetRead();
            assertEquals(uuid1, buf.readUUID());
            assertEquals(uuid2, buf.readUUID());
        }

        @Test
        @DisplayName("VarInt serialization round-trip")
        void testVarIntSerialization() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            buf.writeVarInt(0);
            buf.writeVarInt(127);
            buf.writeVarInt(128);
            buf.writeVarInt(16383);
            buf.writeVarInt(16384);
            buf.writeVarInt(2097151);

            buf.resetRead();
            assertEquals(0, buf.readVarInt());
            assertEquals(127, buf.readVarInt());
            assertEquals(128, buf.readVarInt());
            assertEquals(16383, buf.readVarInt());
            assertEquals(16384, buf.readVarInt());
            assertEquals(2097151, buf.readVarInt());
        }
    }

    // ============================================
    // UpdateMobStatsPayload Tests
    // ============================================

    @Nested
    @DisplayName("UpdateMobStatsPayload Tests")
    class UpdateMobStatsPayloadTests {

        @Test
        @DisplayName("Serialize and deserialize MobStats")
        void testMobStatsRoundTrip() {
            MockUpdateMobStatsPayload original = new MockUpdateMobStatsPayload(
                12345, 100.0f, 15.0f, 20.0f, 0.3f
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.write(buf);

            buf.resetRead();
            MockUpdateMobStatsPayload deserialized = MockUpdateMobStatsPayload.read(buf);

            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("MobStats with extreme values")
        void testMobStatsExtremeValues() {
            MockUpdateMobStatsPayload original = new MockUpdateMobStatsPayload(
                Integer.MAX_VALUE, Float.MAX_VALUE, 0.0f, -10.0f, 0.001f
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.write(buf);

            buf.resetRead();
            MockUpdateMobStatsPayload deserialized = MockUpdateMobStatsPayload.read(buf);

            assertEquals(original.entityId, deserialized.entityId);
            assertEquals(original.maxHealth, deserialized.maxHealth, 0.001f);
        }

        @Test
        @DisplayName("Multiple MobStats payloads in sequence")
        void testMultipleMobStatsPayloads() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();

            MockUpdateMobStatsPayload p1 = new MockUpdateMobStatsPayload(1, 20f, 5f, 2f, 0.3f);
            MockUpdateMobStatsPayload p2 = new MockUpdateMobStatsPayload(2, 40f, 10f, 8f, 0.25f);
            MockUpdateMobStatsPayload p3 = new MockUpdateMobStatsPayload(3, 100f, 25f, 20f, 0.15f);

            p1.write(buf);
            p2.write(buf);
            p3.write(buf);

            buf.resetRead();
            assertEquals(p1, MockUpdateMobStatsPayload.read(buf));
            assertEquals(p2, MockUpdateMobStatsPayload.read(buf));
            assertEquals(p3, MockUpdateMobStatsPayload.read(buf));
        }
    }

    // ============================================
    // UpdateWeaponPayload Tests
    // ============================================

    @Nested
    @DisplayName("UpdateWeaponPayload Tests")
    class UpdateWeaponPayloadTests {

        @Test
        @DisplayName("Serialize and deserialize WeaponPayload")
        void testWeaponPayloadRoundTrip() {
            MockUpdateWeaponPayload original = new MockUpdateWeaponPayload(
                "minecraft:diamond_sword", 5.0f, 2.0f, 1.0f, 0.75f, 0.5f, 0.2f
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.write(buf);

            buf.resetRead();
            MockUpdateWeaponPayload deserialized = MockUpdateWeaponPayload.read(buf);

            assertEquals(original, deserialized);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "minecraft:diamond_sword",
            "minecraft:netherite_axe",
            "modded:epic_weapon_of_doom",
            "namespace:item_with_long_name_that_is_really_long",
            "a:b"
        })
        @DisplayName("Various weapon IDs should serialize correctly")
        void testVariousWeaponIds(String weaponId) {
            MockUpdateWeaponPayload original = new MockUpdateWeaponPayload(
                weaponId, 0f, 2f, 1f, 0.75f, 0.5f, 0f
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.write(buf);

            buf.resetRead();
            MockUpdateWeaponPayload deserialized = MockUpdateWeaponPayload.read(buf);

            assertEquals(weaponId, deserialized.weaponId);
        }

        @Test
        @DisplayName("WeaponPayload with all multipliers custom")
        void testCustomMultipliers() {
            MockUpdateWeaponPayload original = new MockUpdateWeaponPayload(
                "test:weapon", 10.0f, 3.0f, 1.5f, 1.0f, 0.8f, 0.5f
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.write(buf);

            buf.resetRead();
            MockUpdateWeaponPayload deserialized = MockUpdateWeaponPayload.read(buf);

            assertEquals(10.0f, deserialized.baseDamageBonus, 0.001f);
            assertEquals(3.0f, deserialized.headMult, 0.001f);
            assertEquals(1.5f, deserialized.bodyMult, 0.001f);
            assertEquals(1.0f, deserialized.armsMult, 0.001f);
            assertEquals(0.8f, deserialized.legsMult, 0.001f);
            assertEquals(0.5f, deserialized.armorPenetration, 0.001f);
        }
    }

    // ============================================
    // EquipMobPayload Tests
    // ============================================

    @Nested
    @DisplayName("EquipMobPayload Tests")
    class EquipMobPayloadTests {

        @Test
        @DisplayName("Serialize and deserialize EquipMobPayload")
        void testEquipMobRoundTrip() {
            MockEquipMobPayload original = new MockEquipMobPayload(
                12345, 0, "minecraft:diamond_sword"
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.write(buf);

            buf.resetRead();
            MockEquipMobPayload deserialized = MockEquipMobPayload.read(buf);

            assertEquals(original, deserialized);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 3, 4, 5})
        @DisplayName("Various equipment slots should work")
        void testVariousSlots(int slot) {
            MockEquipMobPayload original = new MockEquipMobPayload(
                100, slot, "minecraft:iron_helmet"
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.write(buf);

            buf.resetRead();
            MockEquipMobPayload deserialized = MockEquipMobPayload.read(buf);

            assertEquals(slot, deserialized.slot);
        }

        @Test
        @DisplayName("Empty item ID for unequip")
        void testEmptyItemId() {
            MockEquipMobPayload original = new MockEquipMobPayload(100, 0, "");

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.write(buf);

            buf.resetRead();
            MockEquipMobPayload deserialized = MockEquipMobPayload.read(buf);

            assertEquals("", deserialized.itemId);
        }
    }

    // ============================================
    // Mixed Payload Tests
    // ============================================

    @Nested
    @DisplayName("Mixed Payload Tests")
    class MixedPayloadTests {

        @Test
        @DisplayName("Different payloads in same buffer")
        void testMixedPayloadsInBuffer() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();

            MockUpdateMobStatsPayload mobStats = new MockUpdateMobStatsPayload(1, 100f, 10f, 5f, 0.3f);
            MockUpdateWeaponPayload weaponPayload = new MockUpdateWeaponPayload(
                "minecraft:sword", 5f, 2f, 1f, 0.75f, 0.5f, 0.1f
            );
            MockEquipMobPayload equipPayload = new MockEquipMobPayload(1, 0, "minecraft:sword");

            // Write all with type markers
            buf.writeByte((byte) 1); // Type: MobStats
            mobStats.write(buf);
            buf.writeByte((byte) 2); // Type: Weapon
            weaponPayload.write(buf);
            buf.writeByte((byte) 3); // Type: Equip
            equipPayload.write(buf);

            // Read back
            buf.resetRead();

            assertEquals(1, buf.readByte());
            assertEquals(mobStats, MockUpdateMobStatsPayload.read(buf));

            assertEquals(2, buf.readByte());
            assertEquals(weaponPayload, MockUpdateWeaponPayload.read(buf));

            assertEquals(3, buf.readByte());
            assertEquals(equipPayload, MockEquipMobPayload.read(buf));
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Unicode strings")
        void testUnicodeStrings() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            buf.writeUtf("日本語テスト");
            buf.writeUtf("émojis: 🎮⚔️🛡️");
            buf.writeUtf("Ñoño");

            buf.resetRead();
            assertEquals("日本語テスト", buf.readUtf());
            assertEquals("émojis: 🎮⚔️🛡️", buf.readUtf());
            assertEquals("Ñoño", buf.readUtf());
        }

        @Test
        @DisplayName("Zero values")
        void testZeroValues() {
            MockUpdateMobStatsPayload original = new MockUpdateMobStatsPayload(0, 0f, 0f, 0f, 0f);

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.write(buf);

            buf.resetRead();
            MockUpdateMobStatsPayload deserialized = MockUpdateMobStatsPayload.read(buf);

            assertEquals(0, deserialized.entityId);
            assertEquals(0f, deserialized.maxHealth);
            assertEquals(0f, deserialized.attackDamage);
        }

        @Test
        @DisplayName("Negative entity IDs")
        void testNegativeEntityIds() {
            MockUpdateMobStatsPayload original = new MockUpdateMobStatsPayload(-1, 100f, 10f, 5f, 0.3f);

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.write(buf);

            buf.resetRead();
            MockUpdateMobStatsPayload deserialized = MockUpdateMobStatsPayload.read(buf);

            assertEquals(-1, deserialized.entityId);
        }

        @Test
        @DisplayName("Very long weapon ID")
        void testVeryLongWeaponId() {
            String longId = "very_long_mod_namespace:extremely_long_weapon_name_that_goes_on_and_on_" +
                           "and_contains_many_underscores_and_descriptors_v2_final_really_final";

            MockUpdateWeaponPayload original = new MockUpdateWeaponPayload(
                longId, 0f, 2f, 1f, 0.75f, 0.5f, 0f
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.write(buf);

            buf.resetRead();
            MockUpdateWeaponPayload deserialized = MockUpdateWeaponPayload.read(buf);

            assertEquals(longId, deserialized.weaponId);
        }

        @Test
        @DisplayName("Special float values")
        void testSpecialFloatValues() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            buf.writeFloat(Float.POSITIVE_INFINITY);
            buf.writeFloat(Float.NEGATIVE_INFINITY);
            buf.writeFloat(Float.NaN);

            buf.resetRead();
            assertEquals(Float.POSITIVE_INFINITY, buf.readFloat());
            assertEquals(Float.NEGATIVE_INFINITY, buf.readFloat());
            assertTrue(Float.isNaN(buf.readFloat()));
        }

        @Test
        @DisplayName("Buffer size calculation")
        void testBufferSize() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();

            // Int = 4 bytes
            buf.writeInt(100);
            assertEquals(4, buf.size());

            // Float = 4 bytes
            buf.writeFloat(1.0f);
            assertEquals(8, buf.size());

            // Boolean = 1 byte
            buf.writeBoolean(true);
            assertEquals(9, buf.size());
        }

        @Test
        @DisplayName("Multiple reads and writes interleaved")
        void testInterleavedReadsWrites() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();

            buf.writeInt(10);
            buf.writeFloat(2.5f);
            buf.writeUtf("test");
            buf.writeBoolean(true);

            buf.resetRead();

            // Read in same order
            assertEquals(10, buf.readInt());
            assertEquals(2.5f, buf.readFloat(), 0.001f);
            assertEquals("test", buf.readUtf());
            assertTrue(buf.readBoolean());
        }
    }

    // ============================================
    // Performance Tests
    // ============================================

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Many payloads serialization")
        void testManyPayloads() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();

            // Write 100 payloads
            for (int i = 0; i < 100; i++) {
                new MockUpdateMobStatsPayload(i, i * 10f, i * 2f, i, i * 0.1f).write(buf);
            }

            buf.resetRead();

            // Read and verify
            for (int i = 0; i < 100; i++) {
                MockUpdateMobStatsPayload p = MockUpdateMobStatsPayload.read(buf);
                assertEquals(i, p.entityId);
                assertEquals(i * 10f, p.maxHealth, 0.001f);
            }
        }
    }
}
