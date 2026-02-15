package com.devmod.runtime;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class NexusDialogContextTest {

    private NexusDialogContext createContext(int questsCompleted, boolean isFirstVisit) {
        return new NexusDialogContext(
            UUID.randomUUID(), "TestPlayer",
            questsCompleted, 0, 0, 0,
            false, false, 0, false,
            1, 0, 0, 0, isFirstVisit
        );
    }

    // === getPlayerTitle ===

    @ParameterizedTest
    @CsvSource({
        "0, Newcomer",
        "1, Initiate",
        "4, Initiate",
        "5, Challenger",
        "19, Challenger",
        "20, Veteran",
        "49, Veteran",
        "50, Champion",
        "99, Champion",
        "100, Legend",
        "500, Legend"
    })
    @DisplayName("getPlayerTitle returns correct rank based on quests completed")
    void playerTitleBasedOnQuests(int quests, String expectedTitle) {
        NexusDialogContext ctx = createContext(quests, false);
        assertEquals(expectedTitle, ctx.getPlayerTitle());
    }

    // === isNewcomer ===

    @Test
    @DisplayName("isNewcomer returns true when no quests completed")
    void isNewcomerTrue() {
        assertTrue(createContext(0, false).isNewcomer());
    }

    @Test
    @DisplayName("isNewcomer returns false when quests completed")
    void isNewcomerFalse() {
        assertFalse(createContext(1, false).isNewcomer());
    }

    // === isVeteran ===

    @Test
    @DisplayName("isVeteran returns true when 50+ quests completed")
    void isVeteranTrue() {
        assertTrue(createContext(50, false).isVeteran());
        assertTrue(createContext(100, false).isVeteran());
    }

    @Test
    @DisplayName("isVeteran returns false when fewer than 50 quests")
    void isVeteranFalse() {
        assertFalse(createContext(49, false).isVeteran());
        assertFalse(createContext(0, false).isVeteran());
    }

    // === isAbsoluteFirstVisit ===

    @Test
    @DisplayName("isAbsoluteFirstVisit is true when newcomer AND first visit")
    void isAbsoluteFirstVisitTrue() {
        assertTrue(createContext(0, true).isAbsoluteFirstVisit());
    }

    @Test
    @DisplayName("isAbsoluteFirstVisit is false when not newcomer")
    void isAbsoluteFirstVisitFalseNotNewcomer() {
        assertFalse(createContext(5, true).isAbsoluteFirstVisit());
    }

    @Test
    @DisplayName("isAbsoluteFirstVisit is false when not first visit")
    void isAbsoluteFirstVisitFalseNotFirstVisit() {
        assertFalse(createContext(0, false).isAbsoluteFirstVisit());
    }

    // === isReturningPlayer ===

    @Test
    @DisplayName("isReturningPlayer is true when has history AND first visit this session")
    void isReturningPlayerTrue() {
        assertTrue(createContext(10, true).isReturningPlayer());
    }

    @Test
    @DisplayName("isReturningPlayer is false for newcomers")
    void isReturningPlayerFalseForNewcomers() {
        assertFalse(createContext(0, true).isReturningPlayer());
    }

    @Test
    @DisplayName("isReturningPlayer is false when not first visit this session")
    void isReturningPlayerFalseNotFirstVisit() {
        assertFalse(createContext(10, false).isReturningPlayer());
    }

    // === Record fields ===

    @Test
    @DisplayName("Record fields are accessible")
    void recordFieldsAccessible() {
        UUID playerId = UUID.randomUUID();
        NexusDialogContext ctx = new NexusDialogContext(
            playerId, "TestPlayer",
            10, 50, 15, 200,
            true, true, 4, true,
            5, 1000, 3, 120, false
        );

        assertEquals(playerId, ctx.playerId());
        assertEquals("TestPlayer", ctx.playerName());
        assertEquals(10, ctx.totalQuestsCompleted());
        assertEquals(50, ctx.totalWavesCleared());
        assertEquals(15, ctx.highestWaveReached());
        assertEquals(200, ctx.totalKills());
        assertTrue(ctx.hasActiveQuest());
        assertTrue(ctx.isInParty());
        assertEquals(4, ctx.partySize());
        assertTrue(ctx.isPartyLeader());
        assertEquals(5, ctx.seasonTier());
        assertEquals(1000, ctx.totalXp());
        assertEquals(3, ctx.achievementsUnlocked());
        assertEquals(120, ctx.totalPlaytimeMinutes());
        assertFalse(ctx.isFirstVisit());
    }
}
