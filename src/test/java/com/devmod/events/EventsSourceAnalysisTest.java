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
 * Source-analysis tests for event handlers in the events/ module.
 * Verifies annotation correctness, error isolation, and structural contracts.
 */
@DisplayName("Events Source Analysis")
class EventsSourceAnalysisTest {

    private static String foodSource;
    private static String fuelSource;
    private static String usableSource;
    private static String loginSource;
    private static String arrowSource;
    private static String globalMobSource;

    @BeforeAll
    static void loadSources() throws IOException {
        foodSource = Files.readString(Paths.get("src/main/java/com/devmod/events/FoodEvents.java"));
        fuelSource = Files.readString(Paths.get("src/main/java/com/devmod/events/FuelEvents.java"));
        usableSource = Files.readString(Paths.get("src/main/java/com/devmod/events/UsableEvents.java"));
        loginSource = Files.readString(Paths.get("src/main/java/com/devmod/events/LoginProtectionEvents.java"));
        arrowSource = Files.readString(Paths.get("src/main/java/com/devmod/events/ArrowEvents.java"));
        globalMobSource = Files.readString(Paths.get("src/main/java/com/devmod/events/GlobalMobEvents.java"));
    }

    // ==========================================
    // FoodEvents
    // ==========================================

    @Nested
    @DisplayName("FoodEvents")
    class FoodEventsTests {

        @Test
        @DisplayName("Uses @EventBusSubscriber on GAME bus")
        void usesGameBus() {
            assertTrue(foodSource.contains("@EventBusSubscriber(modid = DevMod.MODID)"));
            assertFalse(foodSource.contains("Bus.MOD"));
        }

        @Test
        @DisplayName("Has @SubscribeEvent on both Start and Finish handlers")
        void hasBothHandlers() {
            int subscribeCount = countOccurrences(foodSource, "@SubscribeEvent");
            assertTrue(subscribeCount >= 2, "Expected at least 2 @SubscribeEvent for Start and Finish");
        }

        @Test
        @DisplayName("onItemUseStart checks if item is food before modifying duration")
        void startChecksIsFood() {
            String method = extractMethod(foodSource, "public static void onItemUseStart");
            assertTrue(method.contains("FoodConfigHandler.isFood(stack)"),
                "Should check if item is food before processing");
        }

        @Test
        @DisplayName("onItemUseFinish only applies to Player instances")
        void finishChecksPlayer() {
            String method = extractMethod(foodSource, "public static void onItemUseFinish");
            assertTrue(method.contains("instanceof Player player"),
                "Should only apply food effects to players");
        }

        @Test
        @DisplayName("Custom effects check probability before applying")
        void effectsCheckProbability() {
            String method = extractMethod(foodSource, "public static void onItemUseFinish");
            assertTrue(method.contains("effectData.probability"),
                "Should check probability for each food effect");
        }

        @Test
        @DisplayName("getEffectHolder handles parse exceptions")
        void getEffectHolderHandlesExceptions() {
            assertTrue(foodSource.contains("catch (Exception e)"),
                "Should catch exceptions when parsing effect IDs");
        }
    }

    // ==========================================
    // FuelEvents
    // ==========================================

    @Nested
    @DisplayName("FuelEvents")
    class FuelEventsTests {

        @Test
        @DisplayName("Uses @EventBusSubscriber on GAME bus")
        void usesGameBus() {
            assertTrue(fuelSource.contains("@EventBusSubscriber(modid = DevMod.MODID)"));
        }

        @Test
        @DisplayName("Early-returns on empty stack")
        void earlyReturnsOnEmpty() {
            String method = extractMethod(fuelSource, "public static void onFuelBurnTime");
            assertTrue(method.contains("stack.isEmpty()"),
                "Should early return on empty stack");
        }

        @Test
        @DisplayName("Respects overrideDefault flag")
        void respectsOverrideFlag() {
            String method = extractMethod(fuelSource, "public static void onFuelBurnTime");
            assertTrue(method.contains("stats.isOverrideDefault()"),
                "Should check overrideDefault flag");
        }

        @Test
        @DisplayName("Uses getEffectiveBurnTime for final value")
        void usesEffectiveBurnTime() {
            String method = extractMethod(fuelSource, "public static void onFuelBurnTime");
            assertTrue(method.contains("stats.getEffectiveBurnTime()"),
                "Should use effective burn time (includes multiplier)");
        }
    }

    // ==========================================
    // UsableEvents
    // ==========================================

    @Nested
    @DisplayName("UsableEvents")
    class UsableEventsTests {

        @Test
        @DisplayName("Has all four item use event handlers")
        void hasFourHandlers() {
            int count = countOccurrences(usableSource, "@SubscribeEvent");
            assertTrue(count >= 4, "Expected 4 @SubscribeEvent (Start, Finish, Tick, Stop), found: " + count);
        }

        @Test
        @DisplayName("onItemUseStart checks for non-default stats")
        void startChecksDefault() {
            String method = extractMethod(usableSource, "public static void onItemUseStart");
            assertTrue(method.contains("stats.isDefault()"),
                "Should check if stats are default before modifying");
        }

        @Test
        @DisplayName("onItemUseFinish applies cooldown via Player.getCooldowns()")
        void finishAppliesCooldown() {
            String method = extractMethod(usableSource, "public static void onItemUseFinish");
            assertTrue(method.contains("player.getCooldowns().addCooldown"),
                "Should apply cooldown via player cooldown system");
        }

        @Test
        @DisplayName("onItemUseFinish checks cooldownDuration > 0")
        void finishChecksCooldownDuration() {
            String method = extractMethod(usableSource, "public static void onItemUseFinish");
            assertTrue(method.contains("stats.getCooldownDuration() > 0"),
                "Should only apply cooldown when duration is positive");
        }
    }

    // ==========================================
    // LoginProtectionEvents
    // ==========================================

    @Nested
    @DisplayName("LoginProtectionEvents")
    class LoginProtectionEventsTests {

        @Test
        @DisplayName("Class is final with private constructor")
        void classIsFinalWithPrivateConstructor() {
            assertTrue(loginSource.contains("public final class LoginProtectionEvents"));
            assertTrue(loginSource.contains("private LoginProtectionEvents()"));
        }

        @Test
        @DisplayName("Has handlers for login, logout, damage, and death")
        void hasFourHandlers() {
            int count = countOccurrences(loginSource, "@SubscribeEvent");
            assertTrue(count >= 4, "Expected 4 @SubscribeEvent (login, logout, damage, death), found: " + count);
        }

        @Test
        @DisplayName("Login handler checks for ServerPlayer")
        void loginChecksServerPlayer() {
            String method = extractMethod(loginSource, "public static void onPlayerLoggedIn");
            assertTrue(method.contains("instanceof ServerPlayer player"),
                "Should check for ServerPlayer on login");
        }

        @Test
        @DisplayName("Uses ConcurrentHashMap for thread safety")
        void usesConcurrentHashMap() {
            assertTrue(loginSource.contains("ConcurrentHashMap"),
                "Should use ConcurrentHashMap for protection map");
        }

        @Test
        @DisplayName("Damage handler cancels damage during protection period")
        void damageHandlerCancelsDamage() {
            String method = extractMethod(loginSource, "public static void onLivingDamagePre");
            assertTrue(method.contains("event.setNewDamage(0f)"),
                "Should zero out damage during protection");
        }

        @Test
        @DisplayName("Death handler prevents death during protection")
        void deathHandlerPreventsDeath() {
            String method = extractMethod(loginSource, "public static void onLivingDeath");
            assertTrue(method.contains("event.setCanceled(true)"),
                "Should cancel death event during protection");
            assertTrue(method.contains("player.setHealth("),
                "Should restore health on prevented death");
        }

        @Test
        @DisplayName("Recovery protection is longer than default")
        void recoveryProtectionIsLonger() {
            assertTrue(loginSource.contains("DEFAULT_PROTECTION_MS = 10_000"));
            assertTrue(loginSource.contains("RECOVERY_PROTECTION_MS = 20_000"));
        }

        @Test
        @DisplayName("Instance dimension check uses devmod namespace")
        void instanceDimensionCheck() {
            assertTrue(loginSource.contains("\"devmod\".equals(id.getNamespace())"));
            assertTrue(loginSource.contains("id.getPath().startsWith(\"instance_\")"));
        }

        @Test
        @DisplayName("Logout handler removes protection entry")
        void logoutRemovesProtection() {
            String method = extractMethod(loginSource, "public static void onPlayerLoggedOut");
            assertTrue(method.contains("protectionUntilMs.remove(player.getUUID())"),
                "Should remove protection on logout");
        }
    }

    // ==========================================
    // ArrowEvents
    // ==========================================

    @Nested
    @DisplayName("ArrowEvents")
    class ArrowEventsTests {

        @Test
        @DisplayName("Uses ScheduledExecutorService instead of Thread.sleep")
        void usesScheduledExecutor() {
            assertTrue(arrowSource.contains("ScheduledExecutorService"),
                "Should use ScheduledExecutor for evasion check, not Thread.sleep");
            // "Thread.sleep" may appear in comments but should not be used as actual code
            assertFalse(arrowSource.contains("Thread.sleep("),
                "Should NOT call Thread.sleep()");
        }

        @Test
        @DisplayName("Daemon thread for evasion scheduler")
        void daemonThreadForScheduler() {
            assertTrue(arrowSource.contains("t.setDaemon(true)"),
                "Evasion scheduler thread should be daemon to not block shutdown");
        }

        @Test
        @DisplayName("onProjectileImpact checks for AbstractArrow")
        void checksAbstractArrow() {
            String method = extractMethod(arrowSource, "public static void onProjectileImpact");
            assertTrue(method.contains("instanceof AbstractArrow arrow"),
                "Should check for AbstractArrow");
        }

        @Test
        @DisplayName("Only processes server-side")
        void onlyServerSide() {
            String method = extractMethod(arrowSource, "public static void onProjectileImpact");
            assertTrue(method.contains("arrow.level().isClientSide"),
                "Should skip client-side processing");
        }

        @Test
        @DisplayName("Supports OBB and fallback body part detection")
        void supportsOBBAndFallback() {
            assertTrue(arrowSource.contains("OBBHitHelper.useOBBSystem()"),
                "Should check OBB system availability");
            assertTrue(arrowSource.contains("HitHelper.rayTraceBodyPartWithHitPoint"),
                "Should have AABB fallback");
        }

        @Test
        @DisplayName("Maps body parts to translation keys")
        void mapsBodyPartKeys() {
            assertTrue(arrowSource.contains("devmod.arrow.head_headshot"));
            assertTrue(arrowSource.contains("devmod.arrow.arms"));
            assertTrue(arrowSource.contains("devmod.arrow.legs"));
            assertTrue(arrowSource.contains("devmod.arrow.torso"));
        }
    }

    // ==========================================
    // GlobalMobEvents
    // ==========================================

    @Nested
    @DisplayName("GlobalMobEvents")
    class GlobalMobEventsTests {

        @Test
        @DisplayName("Defines comprehensive BLOCKED_SPAWN_TYPES")
        void definedBlockedSpawnTypes() {
            assertTrue(globalMobSource.contains("BLOCKED_SPAWN_TYPES = EnumSet.of("));
            assertTrue(globalMobSource.contains("MobSpawnType.NATURAL"));
            assertTrue(globalMobSource.contains("MobSpawnType.CHUNK_GENERATION"));
            assertTrue(globalMobSource.contains("MobSpawnType.SPAWNER"));
        }

        @Test
        @DisplayName("onEntityJoin defers processing for performance")
        void defersEntityProcessing() {
            String method = extractMethod(globalMobSource, "public static void onEntityJoin");
            assertTrue(method.contains("DeferredEntityProcessor.INSTANCE.queueSpawn"),
                "Should queue for deferred processing instead of immediate");
        }

        @Test
        @DisplayName("onEntityJoin filters server-side only")
        void entityJoinServerSide() {
            String method = extractMethod(globalMobSource, "public static void onEntityJoin");
            assertTrue(method.contains("event.getLevel().isClientSide"),
                "Should filter client-side events");
        }

        @Test
        @DisplayName("Mob spawn placement fails for instance dimensions and active arenas")
        void spawnPlacementFailsInInstances() {
            String method = extractMethod(globalMobSource, "public static void onMobSpawnPlacement");
            assertTrue(method.contains("isInstanceDimension(serverLevel)"));
            assertTrue(method.contains("isInsideActiveArena(serverLevel, event.getPos())"));
            assertTrue(method.contains("Result.FAIL"));
        }

        @Test
        @DisplayName("Instance dimension check uses devmod:instance_ prefix")
        void instanceDimensionPrefix() {
            assertTrue(globalMobSource.contains("\"devmod:instance_\""),
                "Should identify instance dimensions by prefix");
        }
    }

    // === Helpers ===

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
