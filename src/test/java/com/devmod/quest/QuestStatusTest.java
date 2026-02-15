package com.devmod.quest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("QuestStatus")
class QuestStatusTest {

    @Test
    @DisplayName("Has exactly 4 values")
    void hasFourValues() {
        assertEquals(4, QuestStatus.values().length);
    }

    @Nested
    @DisplayName("isTerminal")
    class IsTerminalTests {

        @Test
        @DisplayName("NOT_STARTED is not terminal")
        void notStartedIsNotTerminal() {
            assertFalse(QuestStatus.NOT_STARTED.isTerminal());
        }

        @Test
        @DisplayName("ACTIVE is not terminal")
        void activeIsNotTerminal() {
            assertFalse(QuestStatus.ACTIVE.isTerminal());
        }

        @Test
        @DisplayName("COMPLETED is terminal")
        void completedIsTerminal() {
            assertTrue(QuestStatus.COMPLETED.isTerminal());
        }

        @Test
        @DisplayName("FAILED is terminal")
        void failedIsTerminal() {
            assertTrue(QuestStatus.FAILED.isTerminal());
        }
    }

    @Nested
    @DisplayName("isActive")
    class IsActiveTests {

        @Test
        @DisplayName("Only ACTIVE returns true")
        void onlyActiveReturnsTrue() {
            assertTrue(QuestStatus.ACTIVE.isActive());
            assertFalse(QuestStatus.NOT_STARTED.isActive());
            assertFalse(QuestStatus.COMPLETED.isActive());
            assertFalse(QuestStatus.FAILED.isActive());
        }
    }

    @ParameterizedTest
    @EnumSource(QuestStatus.class)
    @DisplayName("All values have valid names")
    void allValuesHaveValidNames(QuestStatus status) {
        assertNotNull(status.name());
        assertFalse(status.name().isEmpty());
    }
}
