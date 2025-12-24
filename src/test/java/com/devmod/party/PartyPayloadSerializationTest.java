package com.devmod.party;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L3: Client/Server Sync Tests for Party System Payloads
 *
 * Tests network serialization and deserialization of party-related payloads.
 * Uses simulated encode/decode to mirror actual PartySyncPayload and PartyActionPayload.
 *
 * Test coverage:
 * - PartySyncPayload encode/decode round-trip
 * - PartyActionPayload encode/decode for all Action types
 * - Mob selection field serialization
 * - Edge cases (null, empty, max values)
 * - Security limits (MAX_MEMBERS, MAX_NAME_LENGTH)
 */
public class PartyPayloadSerializationTest {

    // Security limits matching actual payload
    private static final int MAX_MEMBERS = 20;
    private static final int MAX_NAME_LENGTH = 64;

    // ============================================
    // Mock Buffer (same pattern as PayloadSerializationTest)
    // ============================================

    static class MockFriendlyByteBuf {
        private ByteBuffer buffer;
        private int readPosition = 0;

        public MockFriendlyByteBuf() {
            this.buffer = ByteBuffer.allocate(8192);
        }

        public void writeBoolean(boolean value) {
            buffer.put((byte) (value ? 1 : 0));
        }

        public boolean readBoolean() {
            return buffer.get(readPosition++) != 0;
        }

        public void writeVarInt(int value) {
            while ((value & ~0x7F) != 0) {
                buffer.put((byte) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
            buffer.put((byte) value);
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

        public void writeUUID(UUID uuid) {
            buffer.putLong(uuid.getMostSignificantBits());
            buffer.putLong(uuid.getLeastSignificantBits());
        }

        public UUID readUUID() {
            long most = buffer.getLong(readPosition);
            readPosition += 8;
            long least = buffer.getLong(readPosition);
            readPosition += 8;
            return new UUID(most, least);
        }

        public void writeUtf(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeVarInt(bytes.length);
            buffer.put(bytes);
        }

        public String readUtf() {
            int length = readVarInt();
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) {
                bytes[i] = buffer.get(readPosition++);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }

        public String readUtf(int maxLength) {
            int length = Math.min(readVarInt(), maxLength);
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) {
                bytes[i] = buffer.get(readPosition++);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }

        public void resetRead() {
            readPosition = 0;
        }

        public int size() {
            return buffer.position();
        }
    }

    // ============================================
    // Simulated PartyMemberInfo
    // ============================================

    record SimPartyMemberInfo(
        UUID playerId,
        String playerName,
        boolean isReady,
        boolean isLeader,
        boolean isOnline
    ) {
        public void encode(MockFriendlyByteBuf buf) {
            buf.writeUUID(playerId);
            buf.writeUtf(playerName);
            buf.writeBoolean(isReady);
            buf.writeBoolean(isLeader);
            buf.writeBoolean(isOnline);
        }

        public static SimPartyMemberInfo decode(MockFriendlyByteBuf buf) {
            UUID playerId = buf.readUUID();
            String playerName = buf.readUtf(MAX_NAME_LENGTH);
            boolean isReady = buf.readBoolean();
            boolean isLeader = buf.readBoolean();
            boolean isOnline = buf.readBoolean();
            return new SimPartyMemberInfo(playerId, playerName, isReady, isLeader, isOnline);
        }
    }

    // ============================================
    // Simulated PartySyncPayload (mirrors actual encode/decode)
    // ============================================

    record SimPartySyncPayload(
        boolean hasParty,
        UUID partyId,
        UUID leaderId,
        List<SimPartyMemberInfo> members,
        int questTypeOrdinal,
        int stateOrdinal,
        UUID instanceId,
        String selectedMobId
    ) {
        public static void encode(MockFriendlyByteBuf buf, SimPartySyncPayload payload) {
            buf.writeBoolean(payload.hasParty);
            buf.writeUUID(payload.partyId);
            buf.writeUUID(payload.leaderId);

            buf.writeVarInt(payload.members.size());
            for (SimPartyMemberInfo member : payload.members) {
                member.encode(buf);
            }

            buf.writeVarInt(payload.questTypeOrdinal);
            buf.writeVarInt(payload.stateOrdinal);
            buf.writeUUID(payload.instanceId != null ? payload.instanceId : new UUID(0, 0));
            buf.writeUtf(payload.selectedMobId != null ? payload.selectedMobId : "");
        }

        public static SimPartySyncPayload decode(MockFriendlyByteBuf buf) {
            boolean hasParty = buf.readBoolean();
            UUID partyId = buf.readUUID();
            UUID leaderId = buf.readUUID();

            int memberCount = buf.readVarInt();
            if (memberCount < 0 || memberCount > MAX_MEMBERS) {
                memberCount = 0;
            }
            List<SimPartyMemberInfo> members = new ArrayList<>(memberCount);
            for (int i = 0; i < memberCount; i++) {
                members.add(SimPartyMemberInfo.decode(buf));
            }

            int questTypeOrdinal = buf.readVarInt();
            int stateOrdinal = buf.readVarInt();
            UUID instanceId = buf.readUUID();
            String selectedMobId = buf.readUtf(MAX_NAME_LENGTH);

            // Convert zero UUID back to null
            if (instanceId.getMostSignificantBits() == 0 && instanceId.getLeastSignificantBits() == 0) {
                instanceId = null;
            }

            // Convert empty string to null
            if (selectedMobId.isEmpty()) {
                selectedMobId = null;
            }

            return new SimPartySyncPayload(hasParty, partyId, leaderId, members,
                questTypeOrdinal, stateOrdinal, instanceId, selectedMobId);
        }
    }

    // ============================================
    // Simulated PartyActionPayload (mirrors actual encode/decode)
    // ============================================

    enum SimAction {
        TOGGLE_READY, LEAVE_PARTY, KICK_MEMBER, SET_QUEST_TYPE,
        SET_MOB_TYPE, DISBAND_PARTY, START_QUEST, CREATE_PARTY
    }

    record SimPartyActionPayload(
        SimAction action,
        UUID targetPlayerId,
        int questTypeOrdinal,
        String mobId
    ) {
        public static void encode(MockFriendlyByteBuf buf, SimPartyActionPayload payload) {
            buf.writeVarInt(payload.action.ordinal());

            buf.writeBoolean(payload.targetPlayerId != null);
            if (payload.targetPlayerId != null) {
                buf.writeUUID(payload.targetPlayerId);
            }

            buf.writeVarInt(payload.questTypeOrdinal);

            buf.writeBoolean(payload.mobId != null);
            if (payload.mobId != null) {
                buf.writeUtf(payload.mobId);
            }
        }

        public static SimPartyActionPayload decode(MockFriendlyByteBuf buf) {
            int actionOrdinal = buf.readVarInt();
            SimAction action = SimAction.values()[Math.min(actionOrdinal, SimAction.values().length - 1)];

            UUID targetPlayerId = null;
            if (buf.readBoolean()) {
                targetPlayerId = buf.readUUID();
            }

            int questTypeOrdinal = buf.readVarInt();

            String mobId = null;
            if (buf.readBoolean()) {
                mobId = buf.readUtf(128);
            }

            return new SimPartyActionPayload(action, targetPlayerId, questTypeOrdinal, mobId);
        }

        // Factory methods
        public static SimPartyActionPayload toggleReady() {
            return new SimPartyActionPayload(SimAction.TOGGLE_READY, null, 0, null);
        }

        public static SimPartyActionPayload leaveParty() {
            return new SimPartyActionPayload(SimAction.LEAVE_PARTY, null, 0, null);
        }

        public static SimPartyActionPayload kickMember(UUID playerId) {
            return new SimPartyActionPayload(SimAction.KICK_MEMBER, playerId, 0, null);
        }

        public static SimPartyActionPayload setQuestType(int questTypeOrdinal) {
            return new SimPartyActionPayload(SimAction.SET_QUEST_TYPE, null, questTypeOrdinal, null);
        }

        public static SimPartyActionPayload setMobType(String mobId) {
            return new SimPartyActionPayload(SimAction.SET_MOB_TYPE, null, 0, mobId);
        }

        public static SimPartyActionPayload disbandParty() {
            return new SimPartyActionPayload(SimAction.DISBAND_PARTY, null, 0, null);
        }

        public static SimPartyActionPayload startQuest() {
            return new SimPartyActionPayload(SimAction.START_QUEST, null, 0, null);
        }

        public static SimPartyActionPayload createParty() {
            return new SimPartyActionPayload(SimAction.CREATE_PARTY, null, 0, null);
        }

        public static SimPartyActionPayload createParty(int questTypeOrdinal) {
            return new SimPartyActionPayload(SimAction.CREATE_PARTY, null, questTypeOrdinal, null);
        }
    }

    // ============================================
    // PartySyncPayload Tests
    // ============================================

    @Nested
    @DisplayName("PartySyncPayload Serialization Tests")
    class PartySyncPayloadTests {

        @Test
        @DisplayName("Empty party payload round-trip")
        void testEmptyPartyRoundTrip() {
            SimPartySyncPayload original = new SimPartySyncPayload(
                false,
                new UUID(0, 0),
                new UUID(0, 0),
                List.of(),
                0,
                0,
                null,
                null
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(buf, original);

            buf.resetRead();
            SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

            assertFalse(decoded.hasParty());
            assertTrue(decoded.members().isEmpty());
            assertNull(decoded.instanceId());
            assertNull(decoded.selectedMobId());
        }

        @Test
        @DisplayName("Full party payload round-trip")
        void testFullPartyRoundTrip() {
            UUID partyId = UUID.randomUUID();
            UUID leaderId = UUID.randomUUID();
            UUID member2Id = UUID.randomUUID();
            UUID instanceId = UUID.randomUUID();

            List<SimPartyMemberInfo> members = List.of(
                new SimPartyMemberInfo(leaderId, "LeaderPlayer", true, true, true),
                new SimPartyMemberInfo(member2Id, "Member2", false, false, true)
            );

            SimPartySyncPayload original = new SimPartySyncPayload(
                true,
                partyId,
                leaderId,
                members,
                1, // RAID_BOSS
                0, // FORMING
                instanceId,
                "minecraft:zombie"
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(buf, original);

            buf.resetRead();
            SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

            assertTrue(decoded.hasParty());
            assertEquals(partyId, decoded.partyId());
            assertEquals(leaderId, decoded.leaderId());
            assertEquals(2, decoded.members().size());
            assertEquals(1, decoded.questTypeOrdinal());
            assertEquals(0, decoded.stateOrdinal());
            assertEquals(instanceId, decoded.instanceId());
            assertEquals("minecraft:zombie", decoded.selectedMobId());
        }

        @Test
        @DisplayName("Party member info serialization")
        void testMemberInfoSerialization() {
            UUID playerId = UUID.randomUUID();
            SimPartyMemberInfo original = new SimPartyMemberInfo(
                playerId, "TestPlayer", true, false, true
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.encode(buf);

            buf.resetRead();
            SimPartyMemberInfo decoded = SimPartyMemberInfo.decode(buf);

            assertEquals(playerId, decoded.playerId());
            assertEquals("TestPlayer", decoded.playerName());
            assertTrue(decoded.isReady());
            assertFalse(decoded.isLeader());
            assertTrue(decoded.isOnline());
        }

        @Test
        @DisplayName("Null instanceId converts to zero UUID and back to null")
        void testNullInstanceIdConversion() {
            SimPartySyncPayload original = new SimPartySyncPayload(
                true, UUID.randomUUID(), UUID.randomUUID(),
                List.of(), 0, 0, null, null
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(buf, original);

            buf.resetRead();
            SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

            assertNull(decoded.instanceId(), "null instanceId should round-trip as null");
        }

        @Test
        @DisplayName("Null selectedMobId converts to empty string and back to null")
        void testNullMobIdConversion() {
            SimPartySyncPayload original = new SimPartySyncPayload(
                true, UUID.randomUUID(), UUID.randomUUID(),
                List.of(), 0, 0, null, null
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(buf, original);

            buf.resetRead();
            SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

            assertNull(decoded.selectedMobId(), "null selectedMobId should round-trip as null");
        }

        @ParameterizedTest
        @CsvSource({
            "minecraft:zombie, minecraft:zombie",
            "minecraft:skeleton, minecraft:skeleton",
            "minecraft:wither_skeleton, minecraft:wither_skeleton",
            "minecraft:blaze, minecraft:blaze",
            "modded:custom_boss, modded:custom_boss"
        })
        @DisplayName("Various mob IDs serialize correctly")
        void testVariousMobIds(String inputMobId, String expectedMobId) {
            SimPartySyncPayload original = new SimPartySyncPayload(
                true, UUID.randomUUID(), UUID.randomUUID(),
                List.of(), 0, 0, null, inputMobId
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(buf, original);

            buf.resetRead();
            SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

            assertEquals(expectedMobId, decoded.selectedMobId());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 3, 4, 5})
        @DisplayName("Various quest type ordinals")
        void testQuestTypeOrdinals(int ordinal) {
            SimPartySyncPayload original = new SimPartySyncPayload(
                true, UUID.randomUUID(), UUID.randomUUID(),
                List.of(), ordinal, 0, null, null
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(buf, original);

            buf.resetRead();
            SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

            assertEquals(ordinal, decoded.questTypeOrdinal());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2, 3})
        @DisplayName("Various party state ordinals")
        void testStateOrdinals(int ordinal) {
            SimPartySyncPayload original = new SimPartySyncPayload(
                true, UUID.randomUUID(), UUID.randomUUID(),
                List.of(), 0, ordinal, null, null
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(buf, original);

            buf.resetRead();
            SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

            assertEquals(ordinal, decoded.stateOrdinal());
        }

        @Test
        @DisplayName("Maximum members count")
        void testMaxMembers() {
            List<SimPartyMemberInfo> members = new ArrayList<>();
            for (int i = 0; i < MAX_MEMBERS; i++) {
                members.add(new SimPartyMemberInfo(
                    UUID.randomUUID(), "Player" + i, i % 2 == 0, i == 0, true
                ));
            }

            SimPartySyncPayload original = new SimPartySyncPayload(
                true, UUID.randomUUID(), UUID.randomUUID(),
                members, 0, 0, null, null
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(buf, original);

            buf.resetRead();
            SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

            assertEquals(MAX_MEMBERS, decoded.members().size());
        }

        @Test
        @DisplayName("Player name at max length")
        void testMaxNameLength() {
            String maxLengthName = "A".repeat(MAX_NAME_LENGTH);

            SimPartyMemberInfo original = new SimPartyMemberInfo(
                UUID.randomUUID(), maxLengthName, true, true, true
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.encode(buf);

            buf.resetRead();
            SimPartyMemberInfo decoded = SimPartyMemberInfo.decode(buf);

            assertEquals(maxLengthName, decoded.playerName());
        }

        @Test
        @DisplayName("All member states combinations")
        void testMemberStateCombinations() {
            List<SimPartyMemberInfo> members = List.of(
                new SimPartyMemberInfo(UUID.randomUUID(), "Ready_Leader_Online", true, true, true),
                new SimPartyMemberInfo(UUID.randomUUID(), "NotReady_NotLeader_Online", false, false, true),
                new SimPartyMemberInfo(UUID.randomUUID(), "Ready_NotLeader_Offline", true, false, false),
                new SimPartyMemberInfo(UUID.randomUUID(), "NotReady_NotLeader_Offline", false, false, false)
            );

            SimPartySyncPayload original = new SimPartySyncPayload(
                true, UUID.randomUUID(), UUID.randomUUID(),
                members, 0, 0, null, null
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(buf, original);

            buf.resetRead();
            SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

            assertEquals(4, decoded.members().size());

            SimPartyMemberInfo m0 = decoded.members().get(0);
            assertTrue(m0.isReady() && m0.isLeader() && m0.isOnline());

            SimPartyMemberInfo m1 = decoded.members().get(1);
            assertFalse(m1.isReady());
            assertFalse(m1.isLeader());
            assertTrue(m1.isOnline());

            SimPartyMemberInfo m2 = decoded.members().get(2);
            assertTrue(m2.isReady());
            assertFalse(m2.isLeader());
            assertFalse(m2.isOnline());

            SimPartyMemberInfo m3 = decoded.members().get(3);
            assertFalse(m3.isReady());
            assertFalse(m3.isLeader());
            assertFalse(m3.isOnline());
        }
    }

    // ============================================
    // PartyActionPayload Tests
    // ============================================

    @Nested
    @DisplayName("PartyActionPayload Serialization Tests")
    class PartyActionPayloadTests {

        @ParameterizedTest
        @EnumSource(SimAction.class)
        @DisplayName("All action types serialize correctly")
        void testAllActionTypes(SimAction action) {
            UUID targetId = action == SimAction.KICK_MEMBER ? UUID.randomUUID() : null;
            int questType = (action == SimAction.SET_QUEST_TYPE || action == SimAction.CREATE_PARTY) ? 1 : 0;
            String mobId = action == SimAction.SET_MOB_TYPE ? "minecraft:zombie" : null;

            SimPartyActionPayload original = new SimPartyActionPayload(action, targetId, questType, mobId);

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartyActionPayload.encode(buf, original);

            buf.resetRead();
            SimPartyActionPayload decoded = SimPartyActionPayload.decode(buf);

            assertEquals(action, decoded.action());
            assertEquals(targetId, decoded.targetPlayerId());
            assertEquals(questType, decoded.questTypeOrdinal());
            assertEquals(mobId, decoded.mobId());
        }

        @Test
        @DisplayName("TOGGLE_READY action round-trip")
        void testToggleReadyRoundTrip() {
            SimPartyActionPayload original = SimPartyActionPayload.toggleReady();

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartyActionPayload.encode(buf, original);

            buf.resetRead();
            SimPartyActionPayload decoded = SimPartyActionPayload.decode(buf);

            assertEquals(SimAction.TOGGLE_READY, decoded.action());
            assertNull(decoded.targetPlayerId());
            assertEquals(0, decoded.questTypeOrdinal());
            assertNull(decoded.mobId());
        }

        @Test
        @DisplayName("LEAVE_PARTY action round-trip")
        void testLeavePartyRoundTrip() {
            SimPartyActionPayload original = SimPartyActionPayload.leaveParty();

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartyActionPayload.encode(buf, original);

            buf.resetRead();
            SimPartyActionPayload decoded = SimPartyActionPayload.decode(buf);

            assertEquals(SimAction.LEAVE_PARTY, decoded.action());
        }

        @Test
        @DisplayName("KICK_MEMBER action with target UUID")
        void testKickMemberWithTarget() {
            UUID targetId = UUID.randomUUID();
            SimPartyActionPayload original = SimPartyActionPayload.kickMember(targetId);

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartyActionPayload.encode(buf, original);

            buf.resetRead();
            SimPartyActionPayload decoded = SimPartyActionPayload.decode(buf);

            assertEquals(SimAction.KICK_MEMBER, decoded.action());
            assertEquals(targetId, decoded.targetPlayerId());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2})
        @DisplayName("SET_QUEST_TYPE action with various ordinals")
        void testSetQuestTypeOrdinals(int ordinal) {
            SimPartyActionPayload original = SimPartyActionPayload.setQuestType(ordinal);

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartyActionPayload.encode(buf, original);

            buf.resetRead();
            SimPartyActionPayload decoded = SimPartyActionPayload.decode(buf);

            assertEquals(SimAction.SET_QUEST_TYPE, decoded.action());
            assertEquals(ordinal, decoded.questTypeOrdinal());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "minecraft:zombie",
            "minecraft:skeleton",
            "minecraft:wither_skeleton",
            "minecraft:ender_dragon",
            "modded:epic_boss"
        })
        @DisplayName("SET_MOB_TYPE action with various mob IDs")
        void testSetMobTypeVariousMobs(String mobId) {
            SimPartyActionPayload original = SimPartyActionPayload.setMobType(mobId);

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartyActionPayload.encode(buf, original);

            buf.resetRead();
            SimPartyActionPayload decoded = SimPartyActionPayload.decode(buf);

            assertEquals(SimAction.SET_MOB_TYPE, decoded.action());
            assertEquals(mobId, decoded.mobId());
        }

        @Test
        @DisplayName("DISBAND_PARTY action round-trip")
        void testDisbandPartyRoundTrip() {
            SimPartyActionPayload original = SimPartyActionPayload.disbandParty();

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartyActionPayload.encode(buf, original);

            buf.resetRead();
            SimPartyActionPayload decoded = SimPartyActionPayload.decode(buf);

            assertEquals(SimAction.DISBAND_PARTY, decoded.action());
        }

        @Test
        @DisplayName("START_QUEST action round-trip")
        void testStartQuestRoundTrip() {
            SimPartyActionPayload original = SimPartyActionPayload.startQuest();

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartyActionPayload.encode(buf, original);

            buf.resetRead();
            SimPartyActionPayload decoded = SimPartyActionPayload.decode(buf);

            assertEquals(SimAction.START_QUEST, decoded.action());
        }

        @Test
        @DisplayName("CREATE_PARTY action without quest type")
        void testCreatePartyNoQuestType() {
            SimPartyActionPayload original = SimPartyActionPayload.createParty();

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartyActionPayload.encode(buf, original);

            buf.resetRead();
            SimPartyActionPayload decoded = SimPartyActionPayload.decode(buf);

            assertEquals(SimAction.CREATE_PARTY, decoded.action());
            assertEquals(0, decoded.questTypeOrdinal());
        }

        @Test
        @DisplayName("CREATE_PARTY action with quest type")
        void testCreatePartyWithQuestType() {
            SimPartyActionPayload original = SimPartyActionPayload.createParty(2);

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartyActionPayload.encode(buf, original);

            buf.resetRead();
            SimPartyActionPayload decoded = SimPartyActionPayload.decode(buf);

            assertEquals(SimAction.CREATE_PARTY, decoded.action());
            assertEquals(2, decoded.questTypeOrdinal());
        }
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Empty mob ID string vs null")
        void testEmptyVsNullMobId() {
            // Empty string should decode as null
            SimPartySyncPayload withEmpty = new SimPartySyncPayload(
                true, UUID.randomUUID(), UUID.randomUUID(),
                List.of(), 0, 0, null, ""
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(buf, withEmpty);

            buf.resetRead();
            SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

            // Empty string becomes null after decode (actual behavior)
            assertNull(decoded.selectedMobId());
        }

        @Test
        @DisplayName("Zero UUID as instanceId")
        void testZeroUuidInstanceId() {
            UUID zeroUuid = new UUID(0, 0);
            SimPartySyncPayload original = new SimPartySyncPayload(
                true, UUID.randomUUID(), UUID.randomUUID(),
                List.of(), 0, 0, zeroUuid, null
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(buf, original);

            buf.resetRead();
            SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

            // Zero UUID becomes null after decode (actual behavior)
            assertNull(decoded.instanceId());
        }

        @Test
        @DisplayName("Unicode player names")
        void testUnicodePlayerNames() {
            SimPartyMemberInfo original = new SimPartyMemberInfo(
                UUID.randomUUID(), "プレイヤー名", true, true, true
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.encode(buf);

            buf.resetRead();
            SimPartyMemberInfo decoded = SimPartyMemberInfo.decode(buf);

            assertEquals("プレイヤー名", decoded.playerName());
        }

        @Test
        @DisplayName("Special characters in player names")
        void testSpecialCharacterNames() {
            SimPartyMemberInfo original = new SimPartyMemberInfo(
                UUID.randomUUID(), "Player<>\"&'", true, false, true
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            original.encode(buf);

            buf.resetRead();
            SimPartyMemberInfo decoded = SimPartyMemberInfo.decode(buf);

            assertEquals("Player<>\"&'", decoded.playerName());
        }

        @Test
        @DisplayName("Action ordinal out of range clamps to valid")
        void testActionOrdinalOutOfRange() {
            // This simulates receiving a corrupted/future action ordinal
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            buf.writeVarInt(999); // Invalid action ordinal
            buf.writeBoolean(false); // No target
            buf.writeVarInt(0); // Quest type
            buf.writeBoolean(false); // No mob ID

            buf.resetRead();
            SimPartyActionPayload decoded = SimPartyActionPayload.decode(buf);

            // Should clamp to last valid action
            assertEquals(SimAction.CREATE_PARTY, decoded.action());
        }

        @Test
        @DisplayName("Members list exceeding MAX_MEMBERS resets to empty")
        void testExcessiveMembersCount() {
            // Manually craft a buffer with invalid member count
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            buf.writeBoolean(true); // hasParty
            buf.writeUUID(UUID.randomUUID()); // partyId
            buf.writeUUID(UUID.randomUUID()); // leaderId
            buf.writeVarInt(100); // Invalid: exceeds MAX_MEMBERS
            // No actual member data follows

            buf.writeVarInt(0); // questType
            buf.writeVarInt(0); // state
            buf.writeUUID(new UUID(0, 0)); // instanceId
            buf.writeUtf(""); // mobId

            buf.resetRead();
            SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

            // Should have 0 members due to security check
            assertEquals(0, decoded.members().size());
        }

        @Test
        @DisplayName("Negative member count resets to zero")
        void testNegativeMembersCount() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            buf.writeBoolean(true);
            buf.writeUUID(UUID.randomUUID());
            buf.writeUUID(UUID.randomUUID());
            buf.writeVarInt(-1); // Negative count
            buf.writeVarInt(0);
            buf.writeVarInt(0);
            buf.writeUUID(new UUID(0, 0));
            buf.writeUtf("");

            buf.resetRead();
            SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

            assertEquals(0, decoded.members().size());
        }

        @Test
        @DisplayName("Empty string mob ID in action payload")
        void testEmptyMobIdInAction() {
            SimPartyActionPayload original = new SimPartyActionPayload(
                SimAction.SET_MOB_TYPE, null, 0, ""
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartyActionPayload.encode(buf, original);

            buf.resetRead();
            SimPartyActionPayload decoded = SimPartyActionPayload.decode(buf);

            assertEquals("", decoded.mobId());
        }
    }

    // ============================================
    // Mob Selection Sync Flow Tests
    // ============================================

    @Nested
    @DisplayName("Mob Selection Sync Flow Tests")
    class MobSelectionSyncFlowTests {

        @Test
        @DisplayName("Client sends SET_MOB_TYPE, server responds with sync")
        void testMobSelectionClientServerFlow() {
            // 1. Client sends action
            SimPartyActionPayload clientAction = SimPartyActionPayload.setMobType("minecraft:wither_skeleton");

            MockFriendlyByteBuf actionBuf = new MockFriendlyByteBuf();
            SimPartyActionPayload.encode(actionBuf, clientAction);

            actionBuf.resetRead();
            SimPartyActionPayload decodedAction = SimPartyActionPayload.decode(actionBuf);

            assertEquals(SimAction.SET_MOB_TYPE, decodedAction.action());
            assertEquals("minecraft:wither_skeleton", decodedAction.mobId());

            // 2. Server processes and sends sync back
            UUID partyId = UUID.randomUUID();
            UUID leaderId = UUID.randomUUID();

            SimPartySyncPayload serverSync = new SimPartySyncPayload(
                true,
                partyId,
                leaderId,
                List.of(new SimPartyMemberInfo(leaderId, "Leader", true, true, true)),
                0,
                0,
                null,
                decodedAction.mobId() // Server echoes the mob selection
            );

            MockFriendlyByteBuf syncBuf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(syncBuf, serverSync);

            syncBuf.resetRead();
            SimPartySyncPayload decodedSync = SimPartySyncPayload.decode(syncBuf);

            assertEquals("minecraft:wither_skeleton", decodedSync.selectedMobId());
        }

        @Test
        @DisplayName("Multiple mob changes maintain consistency")
        void testMultipleMobChanges() {
            String[] mobSequence = {
                "minecraft:zombie",
                "minecraft:skeleton",
                "minecraft:wither_skeleton",
                "minecraft:blaze",
                null // Clear selection
            };

            UUID partyId = UUID.randomUUID();
            UUID leaderId = UUID.randomUUID();

            for (String mobId : mobSequence) {
                SimPartySyncPayload sync = new SimPartySyncPayload(
                    true, partyId, leaderId, List.of(), 0, 0, null, mobId
                );

                MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
                SimPartySyncPayload.encode(buf, sync);

                buf.resetRead();
                SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

                assertEquals(mobId, decoded.selectedMobId());
            }
        }

        @Test
        @DisplayName("Mob selection persists across quest type changes")
        void testMobSelectionWithQuestTypeChange() {
            // Initial state with mob and quest type
            UUID partyId = UUID.randomUUID();

            for (int questType = 0; questType < 3; questType++) {
                SimPartySyncPayload sync = new SimPartySyncPayload(
                    true, partyId, UUID.randomUUID(), List.of(),
                    questType, 0, null, "minecraft:zombie"
                );

                MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
                SimPartySyncPayload.encode(buf, sync);

                buf.resetRead();
                SimPartySyncPayload decoded = SimPartySyncPayload.decode(buf);

                assertEquals(questType, decoded.questTypeOrdinal());
                assertEquals("minecraft:zombie", decoded.selectedMobId(),
                    "Mob selection should persist across quest type changes");
            }
        }
    }

    // ============================================
    // Buffer Size Tests
    // ============================================

    @Nested
    @DisplayName("Buffer Size Tests")
    class BufferSizeTests {

        @Test
        @DisplayName("Empty payload has minimal size")
        void testEmptyPayloadSize() {
            SimPartySyncPayload empty = new SimPartySyncPayload(
                false, new UUID(0, 0), new UUID(0, 0),
                List.of(), 0, 0, null, null
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(buf, empty);

            // bool(1) + UUID(16) + UUID(16) + VarInt(1) + VarInt(1) + VarInt(1) + UUID(16) + VarInt(1)
            assertTrue(buf.size() > 0);
            assertTrue(buf.size() < 100, "Empty payload should be under 100 bytes");
        }

        @Test
        @DisplayName("Full party payload size is reasonable")
        void testFullPayloadSize() {
            List<SimPartyMemberInfo> members = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                members.add(new SimPartyMemberInfo(
                    UUID.randomUUID(), "Player" + i, true, i == 0, true
                ));
            }

            SimPartySyncPayload full = new SimPartySyncPayload(
                true, UUID.randomUUID(), UUID.randomUUID(),
                members, 0, 0, UUID.randomUUID(), "minecraft:zombie"
            );

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartySyncPayload.encode(buf, full);

            // 10 members * ~50 bytes each = ~500 bytes, plus header
            assertTrue(buf.size() < 1000, "10-member party should be under 1KB");
        }

        @Test
        @DisplayName("Action payload is compact")
        void testActionPayloadSize() {
            SimPartyActionPayload action = SimPartyActionPayload.setMobType("minecraft:zombie");

            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();
            SimPartyActionPayload.encode(buf, action);

            // VarInt(1) + bool(1) + VarInt(1) + bool(1) + string(~20)
            assertTrue(buf.size() < 50, "Action payload should be under 50 bytes");
        }
    }

    // ============================================
    // Sequential Operations Tests
    // ============================================

    @Nested
    @DisplayName("Sequential Operations Tests")
    class SequentialOperationsTests {

        @Test
        @DisplayName("Multiple syncs in sequence")
        void testMultipleSyncsInSequence() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();

            SimPartySyncPayload sync1 = new SimPartySyncPayload(
                true, UUID.randomUUID(), UUID.randomUUID(),
                List.of(), 0, 0, null, "minecraft:zombie"
            );
            SimPartySyncPayload sync2 = new SimPartySyncPayload(
                true, UUID.randomUUID(), UUID.randomUUID(),
                List.of(), 1, 0, null, "minecraft:skeleton"
            );

            SimPartySyncPayload.encode(buf, sync1);
            SimPartySyncPayload.encode(buf, sync2);

            buf.resetRead();
            SimPartySyncPayload decoded1 = SimPartySyncPayload.decode(buf);
            SimPartySyncPayload decoded2 = SimPartySyncPayload.decode(buf);

            assertEquals("minecraft:zombie", decoded1.selectedMobId());
            assertEquals("minecraft:skeleton", decoded2.selectedMobId());
        }

        @Test
        @DisplayName("Mixed action and sync payloads")
        void testMixedPayloads() {
            MockFriendlyByteBuf buf = new MockFriendlyByteBuf();

            // Client action
            SimPartyActionPayload action = SimPartyActionPayload.setMobType("minecraft:blaze");
            buf.writeBoolean(true); // Marker: action
            SimPartyActionPayload.encode(buf, action);

            // Server sync
            SimPartySyncPayload sync = new SimPartySyncPayload(
                true, UUID.randomUUID(), UUID.randomUUID(),
                List.of(), 0, 0, null, "minecraft:blaze"
            );
            buf.writeBoolean(false); // Marker: sync
            SimPartySyncPayload.encode(buf, sync);

            buf.resetRead();

            assertTrue(buf.readBoolean()); // Action marker
            SimPartyActionPayload decodedAction = SimPartyActionPayload.decode(buf);
            assertEquals("minecraft:blaze", decodedAction.mobId());

            assertFalse(buf.readBoolean()); // Sync marker
            SimPartySyncPayload decodedSync = SimPartySyncPayload.decode(buf);
            assertEquals("minecraft:blaze", decodedSync.selectedMobId());
        }
    }
}
