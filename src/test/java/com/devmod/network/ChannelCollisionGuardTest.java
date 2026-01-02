package com.devmod.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChannelCollisionGuardTest {

    @Test
    void noChannelCollisions_viaChannelIdEnum() {
        // This will throw IllegalStateException if there's a collision
        assertDoesNotThrow(
            ChannelId::validateNoCollisions,
            "ChannelId enum has duplicate channel IDs in the same direction!"
        );
    }

    @Test
    void allChannelIdsArePositive() {
        for (ChannelId channel : ChannelId.values()) {
            assertTrue(channel.getId() > 0,
                "Channel ID must be positive: " + channel.name() + " = " + channel.getId());
        }
    }

    @Test
    void allChannelIdsHaveValidDirection() {
        for (ChannelId channel : ChannelId.values()) {
            assertNotNull(channel.getDirection(),
                "Channel must have direction: " + channel.name());
        }
    }

    @Test
    void allChannelIdsHavePayloadName() {
        for (ChannelId channel : ChannelId.values()) {
            assertNotNull(channel.getPayloadName(),
                "Channel must have payload name: " + channel.name());
            assertFalse(channel.getPayloadName().isEmpty(),
                "Channel payload name must not be empty: " + channel.name());
        }
    }

    @Test
    void channelRangesAreRespected() {
        // Verify channels are within their documented ranges
        for (ChannelId channel : ChannelId.values()) {
            int id = channel.getId();
            String name = channel.name();

            // MOB/ITEM: 1-4 (excludes WEAPON_STATS_V2 and MOB_CONFIG_CONFIRM which are in other ranges)
            if ((name.equals("MOB_STATS") || name.startsWith("WEAPON_") ||
                name.equals("EQUIP_MOB") || name.equals("MODIFY_ITEM"))
                && !name.equals("WEAPON_STATS_V2")) {
                assertTrue(id >= 1 && id <= 4,
                    "MOB/ITEM channel out of range (1-4): " + name + " = " + id);
            }

            // UNIFIED NOTIFICATION CENTER: 120-129
            if (name.startsWith("UNIFIED_") || name.startsWith("NOTIFICATION_") ||
                name.startsWith("SEASON_PASS_") || name.equals("REQUEST_SEASON_PASS")) {
                assertTrue(id >= 120 && id <= 129,
                    "NOTIFICATION CENTER channel out of range (120-129): " + name + " = " + id);
            }

            // ENDURANCE: 5-25 (QUEST_SEQUENCE excluded - it's in PARTY range)
            // REQUEST_SEASON_PASS excluded - it's in NOTIFICATION CENTER range
            if ((name.startsWith("START_QUEST") || name.startsWith("QUEST_") ||
                name.startsWith("SHOP_") || name.startsWith("PERK_") ||
                name.startsWith("PERSONAL_") || name.startsWith("REQUEST_") ||
                name.startsWith("BOSS_") || name.startsWith("BADGE_") ||
                name.startsWith("TOKEN_") || name.startsWith("RECORD_") ||
                name.startsWith("COMBO_") || name.startsWith("INSTANCE_") ||
                name.startsWith("WAVE_") || name.equals("MOB_CONFIG_CONFIRM"))
                && !name.equals("QUEST_SEQUENCE")
                && !name.equals("REQUEST_SEASON_PASS")) {
                assertTrue(id >= 5 && id <= 25,
                    "ENDURANCE channel out of range (5-25): " + name + " = " + id);
            }

            // PARTY: 26-35
            if (name.startsWith("PARTY_") || name.startsWith("NAMED_") ||
                name.startsWith("ARRIVAL_") || name.startsWith("CANCEL_") ||
                name.startsWith("INVITE_") || name.equals("QUEST_SEQUENCE")) {
                assertTrue(id >= 26 && id <= 35,
                    "PARTY channel out of range (26-35): " + name + " = " + id);
            }

            // CONFIG/TELEMETRY: 36-45
            if (name.startsWith("UPDATE_ARMOR") || name.startsWith("RANGED_") ||
                name.startsWith("ARMOR_STATS") || name.startsWith("GLOBAL_") ||
                name.startsWith("RECIPE_") || name.startsWith("TELEMETRY_") ||
                name.startsWith("EDITOR_")) {
                assertTrue(id >= 36 && id <= 45,
                    "CONFIG/TELEMETRY channel out of range (36-45): " + name + " = " + id);
            }

            // ITEM STATS: 46-55
            if (name.startsWith("USABLE_") || name.startsWith("FOOD_") ||
                name.startsWith("FUEL_") || name.equals("WEAPON_STATS_V2")) {
                assertTrue(id >= 46 && id <= 55,
                    "ITEM STATS channel out of range (46-55): " + name + " = " + id);
            }

            // SHIELD: 56-65
            if (name.startsWith("SHIELD_")) {
                assertTrue(id >= 56 && id <= 65,
                    "SHIELD channel out of range (56-65): " + name + " = " + id);
            }

            // ABILITY: 66-75
            if (name.startsWith("STAMINA_") || name.startsWith("ABILITY_")) {
                assertTrue(id >= 66 && id <= 75,
                    "ABILITY channel out of range (66-75): " + name + " = " + id);
            }

            // ARENA: 76-85
            if (name.startsWith("BUILD_")) {
                assertTrue(id >= 76 && id <= 85,
                    "ARENA channel out of range (76-85): " + name + " = " + id);
            }

            // DEBUG: 90-99
            if (name.startsWith("DEBUG_")) {
                assertTrue(id >= 90 && id <= 99,
                    "DEBUG channel out of range (90-99): " + name + " = " + id);
            }
        }
    }

    @Test
    void minimumChannelCount() {
        // Verify minimum channel count to ensure registry is maintained
        int totalChannels = ChannelId.values().length;

        assertTrue(totalChannels >= 45,
            "Expected at least 45 channels in ChannelId enum, found: " + totalChannels +
            ". If you removed channels, update this test!");
    }

    @Test
    void printChannelSummary() {
        System.out.println("=== CHANNEL ID REGISTRY SUMMARY ===");
        System.out.println("Total channels: " + ChannelId.values().length);

        int c2sCount = 0;
        int s2cCount = 0;

        for (ChannelId channel : ChannelId.values()) {
            if (channel.getDirection() == ChannelId.Direction.CLIENT_TO_SERVER) c2sCount++;
            else s2cCount++;
        }

        System.out.println("C→S payloads: " + c2sCount);
        System.out.println("S→C payloads: " + s2cCount);
        System.out.println("===================================");

        // Print full registry
        System.out.println(ChannelId.dumpRegistry());
    }
}
