package com.devmod.arena.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.devmod.arena.identity.ArenaIdentity.RoomIdentifier;

class ArenaIdentityTest {

    @Test
    void constructorValidation() {
        assertThrows(NullPointerException.class, () ->
            new ArenaIdentity(null, "t", Instant.now(), "r", new RoomIdentifier("s", "w", "i")));
        assertThrows(NullPointerException.class, () ->
            new ArenaIdentity(UUID.randomUUID(), null, Instant.now(), "r", new RoomIdentifier("s", "w", "i")));
        assertThrows(NullPointerException.class, () ->
            new ArenaIdentity(UUID.randomUUID(), "t", null, "r", new RoomIdentifier("s", "w", "i")));
        assertThrows(NullPointerException.class, () ->
            new ArenaIdentity(UUID.randomUUID(), "t", Instant.now(), null, new RoomIdentifier("s", "w", "i")));
        assertThrows(NullPointerException.class, () ->
            new ArenaIdentity(UUID.randomUUID(), "t", Instant.now(), "r", null));
    }

    @Test
    void roomIdAliasesArenaId() {
        UUID id = UUID.randomUUID();
        ArenaIdentity identity = new ArenaIdentity(id, "template1", Instant.now(), "eu-west",
            new RoomIdentifier("srv1", "world1", "inst1"));
        assertSame(id, identity.roomId());
    }

    @Test
    void formattedRoomId() {
        ArenaIdentity identity = new ArenaIdentity(UUID.randomUUID(), "t", Instant.now(), "r",
            new RoomIdentifier("srv1", "world1", "inst1"));
        assertEquals("srv-srv1-wld-world1-inst-inst1", identity.formattedRoomId());
    }

    @Test
    void createSetsTimestamp() {
        Instant before = Instant.now();
        ArenaIdentity identity = ArenaIdentity.create(UUID.randomUUID(), "t", "r", "s", "w", "i");
        Instant after = Instant.now();
        assertFalse(identity.createdAt().isBefore(before));
        assertFalse(identity.createdAt().isAfter(after));
    }

    @Test
    void createNewGeneratesId() {
        ArenaIdentity identity = ArenaIdentity.createNew("template", "region", "server", "world");
        assertNotNull(identity.arenaId());
        assertEquals("template", identity.templateId());
        assertEquals("region", identity.region());
    }

    @Test
    void roomIdentifierValidation() {
        assertThrows(NullPointerException.class, () -> new RoomIdentifier(null, "w", "i"));
        assertThrows(NullPointerException.class, () -> new RoomIdentifier("s", null, "i"));
        assertThrows(NullPointerException.class, () -> new RoomIdentifier("s", "w", null));
        assertThrows(IllegalArgumentException.class, () -> new RoomIdentifier("", "w", "i"));
        assertThrows(IllegalArgumentException.class, () -> new RoomIdentifier("s", "  ", "i"));
        assertThrows(IllegalArgumentException.class, () -> new RoomIdentifier("s", "w", ""));
    }

    @Test
    void roomIdentifierFormat() {
        RoomIdentifier room = new RoomIdentifier("alpha", "overworld", "abc123");
        assertEquals("srv-alpha-wld-overworld-inst-abc123", room.format());
        assertEquals(room.format(), room.toString());
    }

    @Test
    void roomIdentifierParse() {
        RoomIdentifier parsed = RoomIdentifier.parse("srv-alpha-wld-overworld-inst-abc123");
        assertEquals("alpha", parsed.serverId());
        assertEquals("overworld", parsed.worldId());
        assertEquals("abc123", parsed.instanceId());
    }

    @Test
    void roomIdentifierParseInvalid() {
        assertThrows(IllegalArgumentException.class, () -> RoomIdentifier.parse(null));
        assertThrows(IllegalArgumentException.class, () -> RoomIdentifier.parse("invalid"));
        assertThrows(IllegalArgumentException.class, () -> RoomIdentifier.parse("srv-only-two"));
    }

    @Test
    void roomIdentifierRoundTrip() {
        RoomIdentifier original = new RoomIdentifier("s1", "w1", "i1");
        RoomIdentifier parsed = RoomIdentifier.parse(original.format());
        assertEquals(original, parsed);
    }
}
