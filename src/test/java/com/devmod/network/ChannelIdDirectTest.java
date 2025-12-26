package com.devmod.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelIdDirectTest {

    @Test
    @DisplayName("Channel ID registry has no collisions")
    void channelIdsHaveNoCollisions() {
        assertDoesNotThrow(ChannelId::validateNoCollisions);
    }

    @Test
    @DisplayName("Channel metadata matches declared values")
    void channelMetadataMatchesValues() {
        assertEquals(5, ChannelId.START_QUEST.getId());
        assertEquals(ChannelId.Direction.CLIENT_TO_SERVER, ChannelId.START_QUEST.getDirection());
    }
}
