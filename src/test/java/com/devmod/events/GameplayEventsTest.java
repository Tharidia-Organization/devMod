package com.devmod.events;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GameplayEvents} - in-game event handlers for player sync and equipment.
 * Focuses on verifying:
 * - Correct @EventBusSubscriber annotation
 * - Player login sync completeness (game mechanics, TesterModality, DuckDB features)
 * - Equipment change sanitization logic
 * - Error isolation in event handlers
 */
@DisplayName("GameplayEvents")
class GameplayEventsTest {

    private static String source;

    @BeforeAll
    static void loadSource() throws IOException {
        source = Files.readString(Paths.get(
            "src/main/java/com/devmod/events/GameplayEvents.java"));
    }

    // --- Annotation correctness ---

    @Test
    @DisplayName("Class uses @EventBusSubscriber on GAME bus")
    void usesGameBusSubscriber() {
        assertTrue(source.contains("@EventBusSubscriber(modid = MODID)"),
            "Should use @EventBusSubscriber with GAME bus (default)");
        assertFalse(source.contains("Bus.MOD"),
            "Should not use MOD bus for gameplay events");
    }

    @Test
    @DisplayName("All event handlers have @SubscribeEvent annotation")
    void allHandlersSubscribed() {
        int subscribeCount = countOccurrences(source, "@SubscribeEvent");
        assertTrue(subscribeCount >= 4,
            "Expected at least 4 @SubscribeEvent (login, equipment, breakSpeed, blockDrop), found: " + subscribeCount);
    }

    // --- Player login sync ---

    @Nested
    @DisplayName("Player login sync")
    class PlayerLoginSync {

        @Test
        @DisplayName("Checks for ServerPlayer before syncing")
        void checksServerPlayer() {
            String loginMethod = extractMethod(source, "public static void onPlayerLoggedIn");
            assertTrue(loginMethod.contains("instanceof ServerPlayer player"),
                "Must check for ServerPlayer to avoid client-side NPE");
        }

        @Test
        @DisplayName("Syncs GameMechanicsConfig to player on login")
        void syncsGameMechanicsConfig() {
            String loginMethod = extractMethod(source, "public static void onPlayerLoggedIn");
            assertTrue(loginMethod.contains("GameMechanicsSyncPayload.fromGlobalConfig()"),
                "Should sync game mechanics config on login");
            assertTrue(loginMethod.contains("PacketDistributor.sendToPlayer(player, payload)"),
                "Should use PacketDistributor to send payload to player");
        }

        @Test
        @DisplayName("Syncs TesterModality state to player on login")
        void syncsTesterModality() {
            String loginMethod = extractMethod(source, "public static void onPlayerLoggedIn");
            assertTrue(loginMethod.contains("new TesterModalitySyncPayload(TesterModality.isEnabled())"),
                "Should sync TesterModality state on login");
        }

        @Test
        @DisplayName("DuckDB-dependent syncs are gated by testerModality AND DuckDB availability")
        void duckDbSyncsAreDoubleGated() {
            String loginMethod = extractMethod(source, "public static void onPlayerLoggedIn");
            assertTrue(loginMethod.contains("testerModulesEnabled && DuckDBBootstrap.isAvailable()"),
                "DuckDB syncs should require both testerModality enabled AND DuckDB available");
        }

        @Test
        @DisplayName("Each sync block has its own try-catch for error isolation")
        void eachSyncBlockIsolated() {
            String loginMethod = extractMethod(source, "public static void onPlayerLoggedIn");
            int tryCount = countOccurrences(loginMethod, "try {");
            assertTrue(tryCount >= 5,
                "Expected at least 5 isolated try-catch blocks for login syncs, found: " + tryCount);
        }

        @Test
        @DisplayName("Mailbox sync includes access, news, tasks, and tickets")
        void mailboxSyncIsComplete() {
            String loginMethod = extractMethod(source, "public static void onPlayerLoggedIn");
            assertTrue(loginMethod.contains("sendMailboxSync(player)"),
                "Should sync mailbox messages");
            assertTrue(loginMethod.contains("sendAccessSync(player)"),
                "Should sync mailbox access permissions");
            assertTrue(loginMethod.contains("sendNewsSync(player)"),
                "Should sync news");
            assertTrue(loginMethod.contains("sendTicketSync(player)"),
                "Should sync tickets");
        }

        @Test
        @DisplayName("Task sync is gated by TESTER permission")
        void taskSyncGatedByPermission() {
            String loginMethod = extractMethod(source, "public static void onPlayerLoggedIn");
            assertTrue(loginMethod.contains("MailboxPermissions.INSTANCE.hasPermission(player, MailboxPermissions.Permission.TESTER)"),
                "Task sync should require TESTER permission");
            assertTrue(loginMethod.contains("sendTaskSync(player)"),
                "Should sync tasks for tester-permissioned players");
        }

        @Test
        @DisplayName("Notification preferences use async loading with safety checks")
        void notificationPrefsAsyncWithSafety() {
            String loginMethod = extractMethod(source, "public static void onPlayerLoggedIn");
            assertTrue(loginMethod.contains("repo.isInitialized()"),
                "Should check repo is initialized before loading prefs");
            assertTrue(loginMethod.contains("player.isRemoved()"),
                "Should check player is not removed before sending packet");
            assertTrue(loginMethod.contains("player.server.execute("),
                "Should dispatch packet send on server thread");
        }

        @Test
        @DisplayName("Telemetry aggregator initialized for joining player")
        void telemetryAggregatorOnJoin() {
            String loginMethod = extractMethod(source, "public static void onPlayerLoggedIn");
            assertTrue(loginMethod.contains("AggregationConfig.AGGREGATION_ENABLED"),
                "Should check aggregation config before initializing");
            assertTrue(loginMethod.contains("TelemetryAggregatorRegistry.INSTANCE.onPlayerJoin(player.getUUID())"),
                "Should register player UUID for telemetry aggregation");
        }
    }

    // --- Equipment change handling ---

    @Nested
    @DisplayName("Equipment change handling")
    class EquipmentChangeHandling {

        @Test
        @DisplayName("onEquipmentChange early-returns on null or empty stack")
        void earlyReturnsOnEmptyStack() {
            String method = extractMethod(source, "public static void onEquipmentChange");
            assertTrue(method.contains("if (stack == null || stack.isEmpty()) return;"),
                "Should early return on null or empty item stack");
        }

        @Test
        @DisplayName("Weapon sanitization triggers for mainhand and offhand")
        void weaponSanitizationForBothHands() {
            String method = extractMethod(source, "public static void onEquipmentChange");
            assertTrue(method.contains("slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND"),
                "Should sanitize weapons in both mainhand and offhand");
        }

        @Test
        @DisplayName("Weapon detection checks three data sources")
        void weaponDetectionChecksThreeSources() {
            String method = extractMethod(source, "public static void onEquipmentChange");
            assertTrue(method.contains("WEAPON_STATS.get()"),
                "Should check weapon stats component");
            assertTrue(method.contains("WeaponModStats"),
                "Should check custom data for WeaponModStats tag");
            assertTrue(method.contains("WeaponConfigHandler.loadFromAttributeModifiers(stack)"),
                "Should check attribute modifiers for weapon data");
        }

        @Test
        @DisplayName("Weapon stats are clamped and reapplied on equip")
        void weaponStatsClampedOnEquip() {
            String method = extractMethod(source, "public static void onEquipmentChange");
            assertTrue(method.contains("WeaponConfigHandler.clampStats(stats)"),
                "Should clamp weapon stats");
            assertTrue(method.contains("WeaponConfigHandler.INSTANCE.setSpecificStats(stack, stats)"),
                "Should reapply clamped stats");
        }

        @Test
        @DisplayName("Armor sanitization handles armor slots and shields")
        void armorSanitizationHandlesArmorAndShields() {
            String method = extractMethod(source, "public static void onEquipmentChange");
            assertTrue(method.contains("slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR"),
                "Should detect humanoid armor slots");
            assertTrue(method.contains("instanceof net.minecraft.world.item.ShieldItem"),
                "Should detect shields for armor sanitization");
        }

        @Test
        @DisplayName("Armor stats are clamped and reapplied")
        void armorStatsClampedOnEquip() {
            String method = extractMethod(source, "public static void onEquipmentChange");
            assertTrue(method.contains("ArmorConfigHandler.clampStats(stats)"),
                "Should clamp armor stats");
            assertTrue(method.contains("ArmorConfigHandler.INSTANCE.setSpecificStats(stack, stats)"),
                "Should reapply clamped armor stats");
        }

        @Test
        @DisplayName("Non-DevMod modifier warnings are logged for weapons")
        void nonDevmodModifierWarningsWeapons() {
            String method = extractMethod(source, "public static void onEquipmentChange");
            assertTrue(method.contains("!DevMod.MODID.equals(id.getNamespace())"),
                "Should check modifier namespace against DevMod");
            assertTrue(method.contains("Non-DevMod modifier"),
                "Should warn about non-DevMod modifiers");
        }

        @Test
        @DisplayName("Economy telemetry recorded on equipment change for ServerPlayer")
        void economyTelemetryRecorded() {
            String method = extractMethod(source, "public static void onEquipmentChange");
            assertTrue(method.contains("EconomyMetricsService.INSTANCE.recordItemEquipped(player, stack, slot.getName())"),
                "Should record economy metrics for server players");
            assertTrue(method.contains("TelemetryService.INSTANCE.appendEconomyLine"),
                "Should append telemetry line for equipment events");
        }

        @Test
        @DisplayName("Economy telemetry only fires server-side")
        void economyTelemetryServerSideOnly() {
            String method = extractMethod(source, "public static void onEquipmentChange");
            assertTrue(method.contains("instanceof ServerPlayer player && !player.level().isClientSide()"),
                "Economy recording should only fire on server side");
        }
    }

    // --- Break speed and block drop ---

    @Nested
    @DisplayName("Break speed and block drop handlers")
    class BreakSpeedAndBlockDrop {

        @Test
        @DisplayName("onBreakSpeed early-returns on null or empty stack")
        void breakSpeedEarlyReturn() {
            String method = extractMethod(source, "public static void onBreakSpeed");
            assertTrue(method.contains("if (stack == null || stack.isEmpty()) return;"),
                "Should early return on null or empty mainhand item");
        }

        @Test
        @DisplayName("onBreakSpeed sanitizes weapon stats like onEquipmentChange")
        void breakSpeedSanitizes() {
            String method = extractMethod(source, "public static void onBreakSpeed");
            assertTrue(method.contains("WeaponConfigHandler.clampStats(stats)"),
                "Should clamp weapon stats on break speed");
        }

        @Test
        @DisplayName("onBlockDrop respects clearToolRules flag")
        void blockDropRespectsToolRules() {
            String method = extractMethod(source, "public static void onBlockDrop");
            assertTrue(method.contains("stats.isClearToolRules()"),
                "Should check clearToolRules flag");
            assertTrue(method.contains("stack.remove("),
                "Should remove tool component when clearToolRules is true");
            assertTrue(method.contains("DataComponents.TOOL"),
                "Should remove the TOOL data component");
        }
    }

    // --- Component access safety ---

    @Test
    @DisplayName("Component access failures are caught and logged via logComponentAccessFailure")
    void componentAccessFailuresCaught() {
        int logCount = countOccurrences(source, "logComponentAccessFailure(");
        assertTrue(logCount >= 6,
            "Expected at least 6 calls to logComponentAccessFailure for safe component access, found: " + logCount);
    }

    @Test
    @DisplayName("logComponentAccessFailure helper is private static")
    void logHelperIsPrivateStatic() {
        assertTrue(source.contains("private static void logComponentAccessFailure("),
            "logComponentAccessFailure should be private static");
    }

    // --- Helpers ---

    private static String extractMethod(String src, String signature) {
        int start = src.indexOf(signature);
        assertTrue(start >= 0, "Could not find method: " + signature);
        int braceStart = src.indexOf('{', start);
        int depth = 0;
        for (int i = braceStart; i < src.length(); i++) {
            if (src.charAt(i) == '{') depth++;
            if (src.charAt(i) == '}') depth--;
            if (depth == 0) return src.substring(start, i + 1);
        }
        fail("Could not find end of method: " + signature);
        return "";
    }

    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) >= 0) {
            count++;
            idx += pattern.length();
        }
        return count;
    }
}
