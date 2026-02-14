package com.devmod.abilities.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.devmod.abilities.DodgeAbilitySystem.DodgeDirection;

/**
 * Tests for AbilityEvent sealed record hierarchy.
 * Verifies field access, equality, and toString for all 5 event types.
 */
class AbilityEventTest {

    private static final UUID PLAYER = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final long TICK = 42000L;

    // ------------------------------------------------------------------
    // DodgePre
    // ------------------------------------------------------------------

    @Test
    void dodgePreFieldAccess() {
        var e = new AbilityEvent.DodgePre(PLAYER, TICK, DodgeDirection.LEFT, 2);

        assertEquals(PLAYER, e.playerId());
        assertEquals(TICK, e.timestamp());
        assertEquals(DodgeDirection.LEFT, e.direction());
        assertEquals(2, e.chargesAvailable());
    }

    @Test
    void dodgePreNullDirection() {
        var e = new AbilityEvent.DodgePre(PLAYER, TICK, null, 1);
        assertNull(e.direction());
    }

    @Test
    void dodgePreEquality() {
        var a = new AbilityEvent.DodgePre(PLAYER, TICK, DodgeDirection.RIGHT, 2);
        var b = new AbilityEvent.DodgePre(PLAYER, TICK, DodgeDirection.RIGHT, 2);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void dodgePreInequalityOnDirection() {
        var a = new AbilityEvent.DodgePre(PLAYER, TICK, DodgeDirection.LEFT, 2);
        var b = new AbilityEvent.DodgePre(PLAYER, TICK, DodgeDirection.RIGHT, 2);
        assertNotEquals(a, b);
    }

    @Test
    void dodgePreToStringContainsFields() {
        var e = new AbilityEvent.DodgePre(PLAYER, TICK, DodgeDirection.BACK, 1);
        String s = e.toString();
        assertTrue(s.contains("DodgePre"));
        assertTrue(s.contains("BACK"));
    }

    @Test
    void dodgePreImplementsAbilityEvent() {
        AbilityEvent e = new AbilityEvent.DodgePre(PLAYER, TICK, DodgeDirection.FORWARD, 2);
        assertEquals(PLAYER, e.playerId());
        assertEquals(TICK, e.timestamp());
    }

    // ------------------------------------------------------------------
    // DodgePost
    // ------------------------------------------------------------------

    @Test
    void dodgePostFieldAccess() {
        var e = new AbilityEvent.DodgePost(PLAYER, TICK, DodgeDirection.RIGHT, 1, 5);

        assertEquals(PLAYER, e.playerId());
        assertEquals(TICK, e.timestamp());
        assertEquals(DodgeDirection.RIGHT, e.direction());
        assertEquals(1, e.chargesRemaining());
        assertEquals(5, e.totalDodgeCount());
    }

    @Test
    void dodgePostEquality() {
        var a = new AbilityEvent.DodgePost(PLAYER, TICK, DodgeDirection.BACK, 1, 5);
        var b = new AbilityEvent.DodgePost(PLAYER, TICK, DodgeDirection.BACK, 1, 5);
        assertEquals(a, b);
    }

    @Test
    void dodgePostToString() {
        var e = new AbilityEvent.DodgePost(PLAYER, TICK, null, 0, 10);
        String s = e.toString();
        assertTrue(s.contains("DodgePost"));
    }

    // ------------------------------------------------------------------
    // DashPre
    // ------------------------------------------------------------------

    @Test
    void dashPreFieldAccess() {
        var e = new AbilityEvent.DashPre(PLAYER, TICK, 2);

        assertEquals(PLAYER, e.playerId());
        assertEquals(TICK, e.timestamp());
        assertEquals(2, e.chargesAvailable());
    }

    @Test
    void dashPreEquality() {
        var a = new AbilityEvent.DashPre(PLAYER, TICK, 1);
        var b = new AbilityEvent.DashPre(PLAYER, TICK, 1);
        assertEquals(a, b);
    }

    @Test
    void dashPreToString() {
        var e = new AbilityEvent.DashPre(PLAYER, TICK, 2);
        assertTrue(e.toString().contains("DashPre"));
    }

    // ------------------------------------------------------------------
    // DashPost
    // ------------------------------------------------------------------

    @Test
    void dashPostFieldAccess() {
        var e = new AbilityEvent.DashPost(PLAYER, TICK, 1, 3);

        assertEquals(PLAYER, e.playerId());
        assertEquals(TICK, e.timestamp());
        assertEquals(1, e.chargesRemaining());
        assertEquals(3, e.totalDashCount());
    }

    @Test
    void dashPostEquality() {
        var a = new AbilityEvent.DashPost(PLAYER, TICK, 0, 7);
        var b = new AbilityEvent.DashPost(PLAYER, TICK, 0, 7);
        assertEquals(a, b);
    }

    @Test
    void dashPostToString() {
        var e = new AbilityEvent.DashPost(PLAYER, TICK, 1, 3);
        assertTrue(e.toString().contains("DashPost"));
    }

    // ------------------------------------------------------------------
    // PerfectDodge
    // ------------------------------------------------------------------

    @Test
    void perfectDodgeFieldAccess() {
        var e = new AbilityEvent.PerfectDodge(PLAYER, TICK, 8.5f, "minecraft:player_attack", 2);

        assertEquals(PLAYER, e.playerId());
        assertEquals(TICK, e.timestamp());
        assertEquals(8.5f, e.damageNegated(), 0.001f);
        assertEquals("minecraft:player_attack", e.damageSourceId());
        assertEquals(2, e.totalPerfectDodges());
    }

    @Test
    void perfectDodgeEquality() {
        var a = new AbilityEvent.PerfectDodge(PLAYER, TICK, 3.0f, "mob", 1);
        var b = new AbilityEvent.PerfectDodge(PLAYER, TICK, 3.0f, "mob", 1);
        assertEquals(a, b);
    }

    @Test
    void perfectDodgeInequalityOnDamage() {
        var a = new AbilityEvent.PerfectDodge(PLAYER, TICK, 3.0f, "mob", 1);
        var b = new AbilityEvent.PerfectDodge(PLAYER, TICK, 5.0f, "mob", 1);
        assertNotEquals(a, b);
    }

    @Test
    void perfectDodgeToString() {
        var e = new AbilityEvent.PerfectDodge(PLAYER, TICK, 1.0f, "test", 0);
        String s = e.toString();
        assertTrue(s.contains("PerfectDodge"));
        assertTrue(s.contains("test"));
    }

    // ------------------------------------------------------------------
    // Sealed hierarchy
    // ------------------------------------------------------------------

    @Test
    void allRecordsAreAbilityEvents() {
        AbilityEvent[] events = {
            new AbilityEvent.DodgePre(PLAYER, TICK, null, 2),
            new AbilityEvent.DodgePost(PLAYER, TICK, null, 1, 1),
            new AbilityEvent.DashPre(PLAYER, TICK, 2),
            new AbilityEvent.DashPost(PLAYER, TICK, 1, 1),
            new AbilityEvent.PerfectDodge(PLAYER, TICK, 1f, "x", 1)
        };

        for (AbilityEvent e : events) {
            assertEquals(PLAYER, e.playerId());
            assertEquals(TICK, e.timestamp());
        }
    }
}
