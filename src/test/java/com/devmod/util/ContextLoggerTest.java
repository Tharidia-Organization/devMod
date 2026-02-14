package com.devmod.util;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContextLogger")
class ContextLoggerTest {

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    // ========================================================================
    // forPlayer
    // ========================================================================

    @Nested
    @DisplayName("forPlayer")
    class ForPlayerTests {

        @Test
        @DisplayName("sets playerUuid and playerName in MDC")
        void setsPlayerContext() {
            UUID uuid = UUID.randomUUID();
            try (var ctx = ContextLogger.forPlayer(uuid, "Steve")) {
                assertEquals(uuid.toString(), MDC.get(ContextLogger.KEY_PLAYER_UUID));
                assertEquals("Steve", MDC.get(ContextLogger.KEY_PLAYER_NAME));
            }
        }

        @Test
        @DisplayName("cleans up MDC on close")
        void cleansUpOnClose() {
            UUID uuid = UUID.randomUUID();
            try (var ctx = ContextLogger.forPlayer(uuid, "Steve")) {
                // context active
            }
            assertNull(MDC.get(ContextLogger.KEY_PLAYER_UUID));
            assertNull(MDC.get(ContextLogger.KEY_PLAYER_NAME));
        }
    }

    // ========================================================================
    // forQuest
    // ========================================================================

    @Nested
    @DisplayName("forQuest")
    class ForQuestTests {

        @Test
        @DisplayName("sets player and quest context")
        void setsQuestContext() {
            UUID uuid = UUID.randomUUID();
            UUID arenaId = UUID.randomUUID();
            try (var ctx = ContextLogger.forQuest(uuid, "Steve", "quest_01", arenaId)) {
                assertEquals(uuid.toString(), MDC.get(ContextLogger.KEY_PLAYER_UUID));
                assertEquals("quest_01", MDC.get(ContextLogger.KEY_QUEST_ID));
                assertEquals(arenaId.toString(), MDC.get(ContextLogger.KEY_ARENA_ID));
            }
        }

        @Test
        @DisplayName("handles null questId and arenaId gracefully")
        void handlesNulls() {
            UUID uuid = UUID.randomUUID();
            try (var ctx = ContextLogger.forQuest(uuid, "Steve", null, null)) {
                assertNull(MDC.get(ContextLogger.KEY_QUEST_ID));
                assertNull(MDC.get(ContextLogger.KEY_ARENA_ID));
            }
        }
    }

    // ========================================================================
    // forParty
    // ========================================================================

    @Test
    @DisplayName("forParty sets player and party context")
    void forPartySetsContext() {
        UUID uuid = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        try (var ctx = ContextLogger.forParty(uuid, "Alex", partyId)) {
            assertEquals(uuid.toString(), MDC.get(ContextLogger.KEY_PLAYER_UUID));
            assertEquals("Alex", MDC.get(ContextLogger.KEY_PLAYER_NAME));
            assertEquals(partyId.toString(), MDC.get(ContextLogger.KEY_PARTY_ID));
        }
    }

    // ========================================================================
    // forRequest
    // ========================================================================

    @Test
    @DisplayName("forRequest sets requestId and action")
    void forRequestSetsContext() {
        try (var ctx = ContextLogger.forRequest("req-123", "spawn_mob")) {
            assertEquals("req-123", MDC.get(ContextLogger.KEY_REQUEST_ID));
            assertEquals("spawn_mob", MDC.get(ContextLogger.KEY_ACTION));
        }
    }

    // ========================================================================
    // forPacket
    // ========================================================================

    @Test
    @DisplayName("forPacket sets player, action, and source")
    void forPacketSetsContext() {
        UUID uuid = UUID.randomUUID();
        try (var ctx = ContextLogger.forPacket(uuid, "Steve", "ConfigSync")) {
            assertEquals(uuid.toString(), MDC.get(ContextLogger.KEY_PLAYER_UUID));
            assertEquals("ConfigSync", MDC.get(ContextLogger.KEY_ACTION));
            assertEquals("network", MDC.get(ContextLogger.KEY_SOURCE));
        }
    }

    // ========================================================================
    // Direct manipulation
    // ========================================================================

    @Nested
    @DisplayName("direct MDC manipulation")
    class DirectManipulation {

        @Test
        @DisplayName("put sets MDC key")
        void putSetsKey() {
            ContextLogger.put("custom_key", "custom_value");
            assertEquals("custom_value", MDC.get("custom_key"));
        }

        @Test
        @DisplayName("put with null value is a no-op")
        void putNullValueNoop() {
            ContextLogger.put("key", null);
            assertNull(MDC.get("key"));
        }

        @Test
        @DisplayName("get retrieves MDC value")
        void getRetrievesValue() {
            MDC.put("test_key", "test_val");
            assertEquals("test_val", ContextLogger.get("test_key"));
        }

        @Test
        @DisplayName("remove clears specific key")
        void removeKey() {
            MDC.put("test_key", "val");
            ContextLogger.remove("test_key");
            assertNull(MDC.get("test_key"));
        }

        @Test
        @DisplayName("clear removes all MDC context")
        void clearAll() {
            MDC.put("a", "1");
            MDC.put("b", "2");
            ContextLogger.clear();
            assertNull(MDC.get("a"));
            assertNull(MDC.get("b"));
        }

        @Test
        @DisplayName("setPlayer sets playerUuid and playerName")
        void setPlayer() {
            UUID uuid = UUID.randomUUID();
            ContextLogger.setPlayer(uuid, "Steve");
            assertEquals(uuid.toString(), MDC.get(ContextLogger.KEY_PLAYER_UUID));
            assertEquals("Steve", MDC.get(ContextLogger.KEY_PLAYER_NAME));
        }

        @Test
        @DisplayName("setQuest sets questId and arenaId")
        void setQuest() {
            UUID arenaId = UUID.randomUUID();
            ContextLogger.setQuest("quest_42", arenaId);
            assertEquals("quest_42", MDC.get(ContextLogger.KEY_QUEST_ID));
            assertEquals(arenaId.toString(), MDC.get(ContextLogger.KEY_ARENA_ID));
        }

        @Test
        @DisplayName("setQuest with null values does not set keys")
        void setQuestWithNulls() {
            ContextLogger.setQuest(null, null);
            assertNull(MDC.get(ContextLogger.KEY_QUEST_ID));
            assertNull(MDC.get(ContextLogger.KEY_ARENA_ID));
        }
    }

    // ========================================================================
    // LoggingContext fluent API
    // ========================================================================

    @Nested
    @DisplayName("LoggingContext fluent API")
    class LoggingContextFluent {

        @Test
        @DisplayName("withPlayer sets player context")
        void withPlayer() {
            UUID uuid = UUID.randomUUID();
            try (var ctx = new ContextLogger.LoggingContext()) {
                ctx.withPlayer(uuid, "Steve");
                assertEquals(uuid.toString(), MDC.get(ContextLogger.KEY_PLAYER_UUID));
                assertEquals("Steve", MDC.get(ContextLogger.KEY_PLAYER_NAME));
            }
        }

        @Test
        @DisplayName("chained fluent methods set all context")
        void chainedFluent() {
            UUID uuid = UUID.randomUUID();
            UUID arenaId = UUID.randomUUID();
            UUID partyId = UUID.randomUUID();
            try (var ctx = new ContextLogger.LoggingContext()) {
                ctx.withPlayer(uuid, "Alex")
                   .withQuest("q1")
                   .withArena(arenaId)
                   .withParty(partyId)
                   .withRequestId("req-001")
                   .withAction("attack");

                assertEquals(uuid.toString(), MDC.get(ContextLogger.KEY_PLAYER_UUID));
                assertEquals("q1", MDC.get(ContextLogger.KEY_QUEST_ID));
                assertEquals(arenaId.toString(), MDC.get(ContextLogger.KEY_ARENA_ID));
                assertEquals(partyId.toString(), MDC.get(ContextLogger.KEY_PARTY_ID));
                assertEquals("req-001", MDC.get(ContextLogger.KEY_REQUEST_ID));
                assertEquals("attack", MDC.get(ContextLogger.KEY_ACTION));
            }
            // All cleared after close
            assertNull(MDC.get(ContextLogger.KEY_PLAYER_UUID));
            assertNull(MDC.get(ContextLogger.KEY_QUEST_ID));
        }

        @Test
        @DisplayName("put with null value is ignored, key not tracked")
        void putNullIgnored() {
            try (var ctx = new ContextLogger.LoggingContext()) {
                ctx.put("key", null);
                assertNull(MDC.get("key"));
            }
        }

        @Test
        @DisplayName("max 10 keys per context")
        void maxKeysPerContext() {
            try (var ctx = new ContextLogger.LoggingContext()) {
                for (int i = 0; i < 12; i++) {
                    ctx.put("key" + i, "val" + i);
                }
                // First 10 should be set
                assertEquals("val0", MDC.get("key0"));
                assertEquals("val9", MDC.get("key9"));
                // 11th and 12th should be silently dropped
                assertNull(MDC.get("key10"));
                assertNull(MDC.get("key11"));
            }
        }
    }

    // ========================================================================
    // Utility methods
    // ========================================================================

    @Nested
    @DisplayName("utility methods")
    class UtilityMethods {

        @Test
        @DisplayName("generateRequestId returns 8-char string")
        void generateRequestId() {
            String id = ContextLogger.generateRequestId();
            assertNotNull(id);
            assertEquals(8, id.length());
        }

        @Test
        @DisplayName("generateRequestId produces unique IDs")
        void generateRequestIdUnique() {
            String id1 = ContextLogger.generateRequestId();
            String id2 = ContextLogger.generateRequestId();
            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("getContextString returns empty prefix when no context")
        void contextStringEmpty() {
            assertEquals("", ContextLogger.getContextString());
        }

        @Test
        @DisplayName("getContextString includes player UUID prefix")
        void contextStringWithPlayer() {
            UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
            ContextLogger.setPlayer(uuid, "Steve");
            String ctx = ContextLogger.getContextString();
            assertTrue(ctx.contains("player=12345678..."));
        }

        @Test
        @DisplayName("getContextString includes quest and arena")
        void contextStringFull() {
            UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
            UUID arenaId = UUID.fromString("aabbccdd-1234-1234-1234-123456789abc");
            ContextLogger.setPlayer(uuid, "Steve");
            ContextLogger.setQuest("q1", arenaId);
            String ctx = ContextLogger.getContextString();
            assertTrue(ctx.contains("player=12345678..."));
            assertTrue(ctx.contains("quest=q1"));
            assertTrue(ctx.contains("arena=aabbccdd..."));
            assertTrue(ctx.startsWith("["));
            assertTrue(ctx.endsWith("] "));
        }

        @Test
        @DisplayName("isInQuest returns false without context")
        void isInQuestFalse() {
            assertFalse(ContextLogger.isInQuest());
        }

        @Test
        @DisplayName("isInQuest returns true when questId is set")
        void isInQuestTrue() {
            MDC.put(ContextLogger.KEY_QUEST_ID, "quest_01");
            assertTrue(ContextLogger.isInQuest());
        }

        @Test
        @DisplayName("isInArena returns false without context")
        void isInArenaFalse() {
            assertFalse(ContextLogger.isInArena());
        }

        @Test
        @DisplayName("isInArena returns true when arenaId is set")
        void isInArenaTrue() {
            MDC.put(ContextLogger.KEY_ARENA_ID, UUID.randomUUID().toString());
            assertTrue(ContextLogger.isInArena());
        }

        @Test
        @DisplayName("hasPlayerContext returns false without context")
        void hasPlayerContextFalse() {
            assertFalse(ContextLogger.hasPlayerContext());
        }

        @Test
        @DisplayName("hasPlayerContext returns true when playerUuid is set")
        void hasPlayerContextTrue() {
            MDC.put(ContextLogger.KEY_PLAYER_UUID, UUID.randomUUID().toString());
            assertTrue(ContextLogger.hasPlayerContext());
        }
    }

    // ========================================================================
    // Nesting: inner context does not pollute outer
    // ========================================================================

    @Test
    @DisplayName("nested contexts preserve outer context on inner close")
    void nestedContextPreservation() {
        UUID outerPlayer = UUID.randomUUID();
        try (var outer = ContextLogger.forPlayer(outerPlayer, "Outer")) {
            assertEquals("Outer", MDC.get(ContextLogger.KEY_PLAYER_NAME));

            try (var inner = ContextLogger.forRequest("req-42", "inner_action")) {
                assertEquals("req-42", MDC.get(ContextLogger.KEY_REQUEST_ID));
                // Note: inner overwrites nothing on player keys, so player still visible
                assertEquals("Outer", MDC.get(ContextLogger.KEY_PLAYER_NAME));
            }

            // After inner closes, requestId is cleaned up
            assertNull(MDC.get(ContextLogger.KEY_REQUEST_ID));
            // Outer player still set
            assertEquals("Outer", MDC.get(ContextLogger.KEY_PLAYER_NAME));
        }
    }
}
