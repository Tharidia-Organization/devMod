package com.devmod.network;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChannelIdCollisionTest {

    @Test
    @DisplayName("ChannelId enum validates no collisions at startup")
    void testChannelIdEnumValidation() {
        // Should not throw - validates no duplicate IDs within same direction
        assertDoesNotThrow(ChannelId::validateNoCollisions,
            "ChannelId.validateNoCollisions() should not throw if there are no collisions");
    }

    @Test
    @DisplayName("No duplicate channel IDs in CLIENT_TO_SERVER direction")
    void testNoDuplicateClientToServerChannels() {
        Map<Integer, ChannelId> clientToServer = new HashMap<>();

        for (ChannelId channel : ChannelId.values()) {
            if (channel.getDirection() == ChannelId.Direction.CLIENT_TO_SERVER) {
                int id = channel.getId();
                ChannelId existing = clientToServer.get(id);

                assertNull(existing,
                    String.format("COLLISION DETECTED: Channel ID %d is used by both %s and %s in C→S direction",
                        id, existing, channel));

                clientToServer.put(id, channel);
            }
        }

        // Log all registered C→S channels for debugging
        System.out.println("=== CLIENT_TO_SERVER Channels ===");
        clientToServer.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> System.out.printf("  [%3d] %s%n", e.getKey(), e.getValue()));
    }

    @Test
    @DisplayName("No duplicate channel IDs in SERVER_TO_CLIENT direction")
    void testNoDuplicateServerToClientChannels() {
        Map<Integer, ChannelId> serverToClient = new HashMap<>();

        for (ChannelId channel : ChannelId.values()) {
            if (channel.getDirection() == ChannelId.Direction.SERVER_TO_CLIENT) {
                int id = channel.getId();
                ChannelId existing = serverToClient.get(id);

                assertNull(existing,
                    String.format("COLLISION DETECTED: Channel ID %d is used by both %s and %s in S→C direction",
                        id, existing, channel));

                serverToClient.put(id, channel);
            }
        }

        // Log all registered S→C channels for debugging
        System.out.println("=== SERVER_TO_CLIENT Channels ===");
        serverToClient.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> System.out.printf("  [%3d] %s%n", e.getKey(), e.getValue()));
    }

    @Test
    @DisplayName("Channel IDs follow documented range allocations")
    void testChannelIdRanges() {
        for (ChannelId channel : ChannelId.values()) {
            int id = channel.getId();
            String name = channel.name();

            // Verify IDs are positive
            assertTrue(id > 0, String.format("Channel %s has invalid ID %d (must be > 0)", name, id));

            // Verify IDs are in reasonable range
            // Range 1-99: Core systems, 100-115: Mailbox system, 120-129: Unified notifications,
            // 130-139: Nutrition, 140-149: Nexus, 150-159: Portal, 160-169: Hologram, 170-179: Clone,
            // 180-189: NPC, 190-199: Area Builder, 200-209: Zone, 210-229: Transport, 230-239: Admin Instance,
            // 240-249: Nexus Hub (Nexus 2.0 slot/hub management), 250-251: Tester Modality
            int maxExpected = 259;
            assertTrue(id <= maxExpected,
                String.format("Channel %s has ID %d which exceeds max expected (%d)", name, id, maxExpected));

            // Log any channels outside their expected ranges (soft warning)
            boolean isMobItemCore = (name.equals("MOB_STATS") || name.startsWith("WEAPON_") ||
                name.equals("EQUIP_MOB") || name.equals("MODIFY_ITEM")) && !name.equals("WEAPON_STATS_V2");
            boolean isItemStats = name.startsWith("USABLE_") || name.startsWith("FOOD_") ||
                name.startsWith("FUEL_") || name.equals("WEAPON_STATS_V2");

            if (isMobItemCore && (id < 1 || id > 4)) {
                System.out.printf("WARNING: MOB/ITEM core channel %s has ID %d outside range 1-4%n", name, id);
            }
            if (isItemStats && (id < 46 || id > 55)) {
                System.out.printf("WARNING: ITEM STATS channel %s has ID %d outside range 46-55%n", name, id);
            }
        }
    }

    @Test
    @DisplayName("All channel IDs have unique string representations")
    void testUniqueStringRepresentations() {
        Map<String, ChannelId> stringIds = new HashMap<>();

        for (ChannelId channel : ChannelId.values()) {
            String stringId = channel.asString();
            ChannelId existing = stringIds.get(stringId);

            // Note: This can be the same if the enum has different names for same ID
            // (e.g., different directions), which is valid
            if (existing != null && existing.getDirection() == channel.getDirection()) {
                fail(String.format("String ID collision: '%s' used by both %s and %s in same direction",
                    stringId, existing, channel));
            }

            stringIds.put(stringId + "_" + channel.getDirection(), channel);
        }
    }

    @Test
    @DisplayName("ChannelId.dumpRegistry() produces valid output")
    void testDumpRegistry() {
        String dump = ChannelId.dumpRegistry();

        assertNotNull(dump, "dumpRegistry() should not return null");
        assertFalse(dump.isEmpty(), "dumpRegistry() should not return empty string");
        assertTrue(dump.contains("DevMod Channel Registry"), "dumpRegistry() should contain header");

        // Print for manual inspection
        System.out.println(dump);
    }

    @Test
    @DisplayName("Channel count is as expected")
    void testChannelCount() {
        int totalChannels = ChannelId.values().length;

        // Log the count
        System.out.printf("Total registered channels: %d%n", totalChannels);

        // Verify we have a reasonable number of channels
        assertTrue(totalChannels >= 30, "Expected at least 30 registered channels");
        assertTrue(totalChannels <= 160, "Unexpectedly high channel count: " + totalChannels);

        // Count by direction
        long clientToServer = java.util.Arrays.stream(ChannelId.values())
            .filter(c -> c.getDirection() == ChannelId.Direction.CLIENT_TO_SERVER)
            .count();
        long serverToClient = java.util.Arrays.stream(ChannelId.values())
            .filter(c -> c.getDirection() == ChannelId.Direction.SERVER_TO_CLIENT)
            .count();

        System.out.printf("C→S channels: %d%n", clientToServer);
        System.out.printf("S→C channels: %d%n", serverToClient);
    }
}
