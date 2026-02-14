package com.devmod.npc;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.npc.data.DialogMemory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DialogMemory record.
 * Validates creation, option tracking, custom data, and immutability.
 */
class DialogMemoryTest {

    private final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID npcId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    @DisplayName("create returns empty memory")
    void createReturnsEmptyMemory() {
        DialogMemory memory = DialogMemory.create(playerId, npcId);
        assertEquals(playerId, memory.playerId());
        assertEquals(npcId, memory.npcId());
        assertTrue(memory.selectedOptionIds().isEmpty());
        assertTrue(memory.customData().isEmpty());
        assertEquals(0, memory.getSelectionCount());
        assertTrue(memory.lastInteraction() > 0);
    }

    @Test
    @DisplayName("Compact constructor rejects null playerId")
    @SuppressWarnings("NullAway")
    void constructorRejectsNullPlayerId() {
        assertThrows(NullPointerException.class, () ->
            new DialogMemory(null, npcId, java.util.List.of(), java.util.Map.of(), 0));
    }

    @Test
    @DisplayName("Compact constructor rejects null npcId")
    @SuppressWarnings("NullAway")
    void constructorRejectsNullNpcId() {
        assertThrows(NullPointerException.class, () ->
            new DialogMemory(playerId, null, java.util.List.of(), java.util.Map.of(), 0));
    }

    @Test
    @DisplayName("withSelectedOption adds option to history")
    void withSelectedOptionAddsOption() {
        DialogMemory memory = DialogMemory.create(playerId, npcId);
        DialogMemory updated = memory.withSelectedOption("option_1");
        assertTrue(updated.hasSelectedOption("option_1"));
        assertEquals(1, updated.getSelectionCount());
    }

    @Test
    @DisplayName("withSelectedOption does not add duplicate option")
    void withSelectedOptionNoDuplicates() {
        DialogMemory memory = DialogMemory.create(playerId, npcId)
            .withSelectedOption("option_1")
            .withSelectedOption("option_1");
        assertEquals(1, memory.getSelectionCount());
    }

    @Test
    @DisplayName("withSelectedOption adds multiple distinct options")
    void withSelectedOptionMultipleDistinct() {
        DialogMemory memory = DialogMemory.create(playerId, npcId)
            .withSelectedOption("option_1")
            .withSelectedOption("option_2")
            .withSelectedOption("option_3");
        assertEquals(3, memory.getSelectionCount());
        assertTrue(memory.hasSelectedOption("option_1"));
        assertTrue(memory.hasSelectedOption("option_2"));
        assertTrue(memory.hasSelectedOption("option_3"));
    }

    @Test
    @DisplayName("hasSelectedOption returns false for missing option")
    void hasSelectedOptionReturnsFalseWhenMissing() {
        DialogMemory memory = DialogMemory.create(playerId, npcId);
        assertFalse(memory.hasSelectedOption("nonexistent"));
    }

    @Test
    @DisplayName("withCustomData adds key-value pair")
    void withCustomDataAdds() {
        DialogMemory memory = DialogMemory.create(playerId, npcId)
            .withCustomData("key1", "value1");
        assertTrue(memory.hasCustomData("key1"));
        assertEquals("value1", memory.getCustomData("key1", "default"));
    }

    @Test
    @DisplayName("withCustomData overwrites existing key")
    void withCustomDataOverwrites() {
        DialogMemory memory = DialogMemory.create(playerId, npcId)
            .withCustomData("key1", "value1")
            .withCustomData("key1", "value2");
        assertEquals("value2", memory.getCustomData("key1", "default"));
    }

    @Test
    @DisplayName("getCustomData returns default for missing key")
    void getCustomDataReturnsDefault() {
        DialogMemory memory = DialogMemory.create(playerId, npcId);
        assertEquals("fallback", memory.getCustomData("missing", "fallback"));
    }

    @Test
    @DisplayName("withoutCustomData removes key")
    void withoutCustomDataRemoves() {
        DialogMemory memory = DialogMemory.create(playerId, npcId)
            .withCustomData("key1", "value1")
            .withoutCustomData("key1");
        assertFalse(memory.hasCustomData("key1"));
    }

    @Test
    @DisplayName("touch updates lastInteraction timestamp")
    void touchUpdatesTimestamp() {
        DialogMemory memory = DialogMemory.create(playerId, npcId);
        DialogMemory touched = memory.touch();
        assertTrue(touched.lastInteraction() >= memory.lastInteraction());
        // IDs and data should be preserved
        assertEquals(memory.playerId(), touched.playerId());
        assertEquals(memory.npcId(), touched.npcId());
    }

    @Test
    @DisplayName("Original memory is not mutated by withSelectedOption")
    void originalNotMutatedByWithSelectedOption() {
        DialogMemory original = DialogMemory.create(playerId, npcId);
        DialogMemory updated = original.withSelectedOption("opt");
        assertEquals(0, original.getSelectionCount());
        assertEquals(1, updated.getSelectionCount());
    }

    @Test
    @DisplayName("Original memory is not mutated by withCustomData")
    void originalNotMutatedByWithCustomData() {
        DialogMemory original = DialogMemory.create(playerId, npcId);
        DialogMemory updated = original.withCustomData("k", "v");
        assertFalse(original.hasCustomData("k"));
        assertTrue(updated.hasCustomData("k"));
    }
}
