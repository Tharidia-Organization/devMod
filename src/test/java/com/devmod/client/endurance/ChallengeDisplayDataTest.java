package com.devmod.client.endurance;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import net.minecraft.network.chat.Component;

class ChallengeDisplayDataTest {

    private ClientChallengeCache.ChallengeDisplayData create(
            int target, int current, int tokens, int prestige, boolean completed) {
        return new ClientChallengeCache.ChallengeDisplayData(
            "test_challenge",
            Component.literal("Test Challenge"),
            Component.literal("Do something"),
            "Medium",
            0xFFCC00,
            target,
            current,
            tokens,
            prestige,
            completed,
            false
        );
    }

    // ========== getProgress ==========

    @Test
    void getProgress_returnsZeroAtStart() {
        var data = create(100, 0, 10, 5, false);
        assertEquals(0f, data.getProgress(), 0.001f);
    }

    @Test
    void getProgress_returnsHalfway() {
        var data = create(100, 50, 10, 5, false);
        assertEquals(0.5f, data.getProgress(), 0.001f);
    }

    @Test
    void getProgress_returnsOneAtComplete() {
        var data = create(100, 100, 10, 5, true);
        assertEquals(1.0f, data.getProgress(), 0.001f);
    }

    @Test
    void getProgress_clampsToOne() {
        var data = create(100, 150, 10, 5, false);
        assertEquals(1.0f, data.getProgress(), 0.001f);
    }

    @Test
    void getProgress_returnsOneForZeroTarget() {
        var data = create(0, 0, 10, 5, false);
        assertEquals(1.0f, data.getProgress(), 0.001f);
    }

    @Test
    void getProgress_returnsOneForNegativeTarget() {
        var data = create(-1, 0, 10, 5, false);
        assertEquals(1.0f, data.getProgress(), 0.001f);
    }

    // ========== getProgressText ==========

    @Test
    void getProgressText_showsCurrentAndTarget() {
        var data = create(100, 42, 10, 5, false);
        assertEquals("42 / 100", data.getProgressText());
    }

    @Test
    void getProgressText_showsCompleteWhenDone() {
        var data = create(100, 100, 10, 5, true);
        assertEquals("Complete!", data.getProgressText());
    }

    // ========== getRewardText ==========

    @Test
    void getRewardText_tokensOnly() {
        var data = create(100, 0, 50, 0, false);
        assertEquals("50 Tokens", data.getRewardText());
    }

    @Test
    void getRewardText_prestigeOnly() {
        var data = create(100, 0, 0, 25, false);
        assertEquals("25 Prestige", data.getRewardText());
    }

    @Test
    void getRewardText_both() {
        var data = create(100, 0, 50, 25, false);
        assertEquals("50 Tokens + 25 Prestige", data.getRewardText());
    }

    @Test
    void getRewardText_emptyForNoReward() {
        var data = create(100, 0, 0, 0, false);
        assertEquals("", data.getRewardText());
    }

    // ========== Record fields ==========

    @Test
    void fieldsAccessible() {
        var data = create(100, 42, 10, 5, false);
        assertEquals("test_challenge", data.id());
        assertEquals("Medium", data.difficultyName());
        assertEquals(0xFFCC00, data.difficultyColor());
        assertEquals(100, data.targetValue());
        assertEquals(42, data.currentValue());
        assertEquals(10, data.tokenReward());
        assertEquals(5, data.prestigeReward());
        assertFalse(data.completed());
        assertFalse(data.rewarded());
    }
}
