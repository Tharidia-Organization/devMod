package com.devmod.stats;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LeaderboardEntryTest {

    @Test
    void recordFields_areAccessible() {
        UUID id = UUID.randomUUID();
        LeaderboardEntry entry = new LeaderboardEntry(1, id, "Player1", 1000L, 500L, 200L);

        assertEquals(1, entry.rank());
        assertEquals(id, entry.playerId());
        assertEquals("Player1", entry.playerName());
        assertEquals(1000L, entry.score());
        assertEquals(500L, entry.secondary());
        assertEquals(200L, entry.tertiary());
    }

    @ParameterizedTest
    @CsvSource({
        "7, SSS",
        "6, SS",
        "5, S",
        "4, A",
        "3, B",
        "2, C",
        "1, D",
        "0, ?",
        "-1, ?",
        "8, ?",
        "100, ?"
    })
    void styleRankName_mapsCorrectly(long index, String expected) {
        assertEquals(expected, LeaderboardEntry.styleRankName(index));
    }

    @Test
    void equals_worksForSameValues() {
        UUID id = UUID.randomUUID();
        LeaderboardEntry a = new LeaderboardEntry(1, id, "Test", 100L, 50L, 25L);
        LeaderboardEntry b = new LeaderboardEntry(1, id, "Test", 100L, 50L, 25L);
        assertEquals(a, b);
    }

    @Test
    void equals_failsForDifferentRank() {
        UUID id = UUID.randomUUID();
        LeaderboardEntry a = new LeaderboardEntry(1, id, "Test", 100L, 50L, 25L);
        LeaderboardEntry b = new LeaderboardEntry(2, id, "Test", 100L, 50L, 25L);
        assertNotEquals(a, b);
    }

    @Test
    void hashCode_consistentWithEquals() {
        UUID id = UUID.randomUUID();
        LeaderboardEntry a = new LeaderboardEntry(1, id, "Test", 100L, 50L, 25L);
        LeaderboardEntry b = new LeaderboardEntry(1, id, "Test", 100L, 50L, 25L);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 8, 99, Integer.MAX_VALUE})
    void styleRankName_returnsQuestionMarkForOutOfRange(long index) {
        assertEquals("?", LeaderboardEntry.styleRankName(index));
    }

    @Test
    void toString_containsFields() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        LeaderboardEntry entry = new LeaderboardEntry(1, id, "Steve", 42L, 0L, 0L);
        String s = entry.toString();
        assertTrue(s.contains("Steve"));
        assertTrue(s.contains("42"));
    }
}
