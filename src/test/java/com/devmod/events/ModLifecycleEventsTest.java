package com.devmod.events;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ModLifecycleEvents} - server lifecycle event handlers.
 * Focuses on verifying:
 * - Correct @EventBusSubscriber annotation (GAME bus, not MOD bus)
 * - Event handler structure and error isolation
 * - observeFuture utility correctness
 * - Initialization ordering within onServerStarting
 */
@DisplayName("ModLifecycleEvents")
class ModLifecycleEventsTest {

    private static String source;

    @BeforeAll
    static void loadSource() throws IOException {
        source = Files.readString(Paths.get(
            "src/main/java/com/devmod/events/ModLifecycleEvents.java"));
    }

    // --- Annotation correctness ---

    @Nested
    @DisplayName("Event bus annotations")
    class EventBusAnnotations {

        @Test
        @DisplayName("Class uses @EventBusSubscriber for GAME bus (default, not MOD)")
        void usesGameBusSubscriber() {
            // @EventBusSubscriber(modid = MODID) defaults to GAME bus
            assertTrue(source.contains("@EventBusSubscriber(modid = MODID)"),
                "Should use @EventBusSubscriber with modid on GAME bus (default)");
            // Must NOT specify Bus.MOD (that's for mod lifecycle, not game events)
            assertFalse(source.contains("Bus.MOD"),
                "Should not use MOD bus - lifecycle events like ServerStarting fire on GAME bus");
        }

        @Test
        @DisplayName("All event handlers have @SubscribeEvent")
        void allHandlersSubscribed() {
            // Count @SubscribeEvent annotations vs public static void methods that take event params
            int subscribeCount = countOccurrences(source, "@SubscribeEvent");
            assertTrue(subscribeCount >= 4,
                "Expected at least 4 @SubscribeEvent annotations (attributes, starting, stopping, unload), found: " + subscribeCount);
        }

        @Test
        @DisplayName("Event handlers are public static (required by @EventBusSubscriber)")
        void handlersArePublicStatic() {
            assertTrue(source.contains("public static void modifyEntityAttributes("),
                "modifyEntityAttributes must be public static");
            assertTrue(source.contains("public static void onServerStarting("),
                "onServerStarting must be public static");
            assertTrue(source.contains("public static void onServerStopping("),
                "onServerStopping must be public static");
            assertTrue(source.contains("public static void onLevelUnload("),
                "onLevelUnload must be public static");
        }
    }

    // --- Error isolation ---

    @Nested
    @DisplayName("Error isolation in onServerStarting")
    class ErrorIsolation {

        @Test
        @DisplayName("Each initialization block has its own try-catch")
        void eachInitBlockHasTryCatch() {
            String startingMethod = extractMethod(source, "public static void onServerStarting");
            // Count try blocks - each initialization should be isolated
            int tryCount = countOccurrences(startingMethod, "try {");
            assertTrue(tryCount >= 6,
                "Expected at least 6 isolated try-catch blocks in onServerStarting, found: " + tryCount);
        }

        @Test
        @DisplayName("DamageTypeConfig failure does not prevent HazardTypeRegistry init")
        void damageTypeConfigFailureIsolated() {
            String startingMethod = extractMethod(source, "public static void onServerStarting");
            int damageTypeIdx = startingMethod.indexOf("DamageTypeConfig.INSTANCE.load()");
            int hazardIdx = startingMethod.indexOf("HazardTypeRegistry.INSTANCE.initialize(");
            assertTrue(damageTypeIdx >= 0 && hazardIdx > damageTypeIdx,
                "HazardTypeRegistry init should come after DamageTypeConfig");
            // Verify there's a catch between them
            int catchIdx = startingMethod.indexOf("} catch (Exception e)", damageTypeIdx);
            assertTrue(catchIdx >= 0 && catchIdx < hazardIdx,
                "Should have catch block between DamageTypeConfig and HazardTypeRegistry");
        }

        @Test
        @DisplayName("Each shutdown block has its own try-catch")
        void eachShutdownBlockHasTryCatch() {
            String stoppingMethod = extractMethod(source, "public static void onServerStopping");
            int tryCount = countOccurrences(stoppingMethod, "try {");
            assertTrue(tryCount >= 3,
                "Expected at least 3 isolated try-catch blocks in onServerStopping, found: " + tryCount);
        }
    }

    // --- TesterModality gating ---

    @Nested
    @DisplayName("TesterModality gating")
    class TesterModalityGating {

        @Test
        @DisplayName("onServerStarting checks TesterModality before endurance bootstrap")
        void serverStartingChecksTesterModality() {
            String startingMethod = extractMethod(source, "public static void onServerStarting");
            assertTrue(startingMethod.contains("boolean testerModulesEnabled = TesterModality.isEnabled()"),
                "Should check TesterModality early in onServerStarting");
            assertTrue(startingMethod.contains("if (testerModulesEnabled)"),
                "Should gate tester modules behind testerModulesEnabled flag");
        }

        @Test
        @DisplayName("onServerStopping checks TesterModality before tester shutdown")
        void serverStoppingChecksTesterModality() {
            String stoppingMethod = extractMethod(source, "public static void onServerStopping");
            assertTrue(stoppingMethod.contains("boolean testerModulesEnabled = TesterModality.isEnabled()"),
                "Should check TesterModality in onServerStopping");
        }

        @Test
        @DisplayName("Core systems (DamageTypeConfig, BiomeRegistry) are always initialized")
        void coreSystemsAlwaysInit() {
            String startingMethod = extractMethod(source, "public static void onServerStarting");
            int damageTypeIdx = startingMethod.indexOf("DamageTypeConfig.INSTANCE.load()");
            int biomeIdx = startingMethod.indexOf("BiomeRegistry.initialize(");
            int firstTesterGate = startingMethod.indexOf("if (testerModulesEnabled)");
            // Core systems should be initialized before the first tester gate
            assertTrue(damageTypeIdx >= 0 && damageTypeIdx < firstTesterGate,
                "DamageTypeConfig should be loaded before any tester modality check");
            assertTrue(biomeIdx >= 0 && biomeIdx < firstTesterGate,
                "BiomeRegistry should be initialized before any tester modality check");
        }
    }

    // --- observeFuture utility ---

    @Nested
    @DisplayName("observeFuture utility")
    class ObserveFutureTests {

        @Test
        @DisplayName("observeFuture is package-private static")
        void isPackagePrivateStatic() {
            assertTrue(source.contains("static void observeFuture(CompletableFuture<?>"),
                "observeFuture should be package-private static");
            assertFalse(source.contains("public static void observeFuture"),
                "observeFuture must not be public");
            assertFalse(source.contains("private static void observeFuture"),
                "observeFuture must not be private (used by DataInitializer)");
        }

        @Test
        @DisplayName("observeFuture does not call .join() or .get()")
        void doesNotBlock() {
            String method = extractMethod(source, "static void observeFuture");
            assertFalse(method.contains(".join()"),
                "observeFuture must not block with .join()");
            assertFalse(method.contains(".get()"),
                "observeFuture must not block with .get()");
        }

        @Test
        @DisplayName("observeFuture attaches .exceptionally() handler for error logging")
        void attachesExceptionallyHandler() {
            String method = extractMethod(source, "static void observeFuture");
            assertTrue(method.contains(".exceptionally("),
                "observeFuture should attach .exceptionally() for error logging");
        }

        @Test
        @DisplayName("observeFuture accepts context parameter for diagnostic logging")
        void acceptsContextParameter() {
            assertTrue(source.contains("observeFuture(CompletableFuture<?> future, String context)"),
                "observeFuture should accept a context string for diagnostic messages");
        }
    }

    // --- onLevelUnload ---

    @Test
    @DisplayName("onLevelUnload checks for ServerLevel before notifying DynamicDimensionManager")
    void levelUnloadChecksServerLevel() {
        String method = extractMethod(source, "public static void onLevelUnload");
        assertTrue(method.contains("instanceof net.minecraft.server.level.ServerLevel"),
            "Should check for ServerLevel to avoid client-side calls");
        assertTrue(method.contains("DynamicDimensionManager.INSTANCE.onDimensionUnload"),
            "Should notify DynamicDimensionManager on server level unload");
    }

    // --- DataInitializer delegation ---

    @Test
    @DisplayName("onServerStarting delegates DuckDB init to DataInitializer")
    void delegatesDuckDbInitToDataInitializer() {
        String startingMethod = extractMethod(source, "public static void onServerStarting");
        assertTrue(startingMethod.contains("DataInitializer.initializeDuckDBServices(event)"),
            "Should delegate DuckDB initialization to DataInitializer");
    }

    @Test
    @DisplayName("onServerStopping delegates DuckDB shutdown to DataInitializer")
    void delegatesDuckDbShutdownToDataInitializer() {
        String stoppingMethod = extractMethod(source, "public static void onServerStopping");
        assertTrue(stoppingMethod.contains("DataInitializer.shutdownDuckDBServices()"),
            "Should delegate DuckDB shutdown to DataInitializer");
    }

    // --- Shutdown ordering ---

    @Test
    @DisplayName("GlobalMobConfigStorage cache is cleared during shutdown")
    void globalMobConfigCacheClearedOnShutdown() {
        String stoppingMethod = extractMethod(source, "public static void onServerStopping");
        assertTrue(stoppingMethod.contains("GlobalMobConfigStorage.clearCache()"),
            "Should clear GlobalMobConfigStorage cache during shutdown to prevent stale data");
    }

    @Test
    @DisplayName("MixinLogFilter summary is logged during shutdown")
    void mixinLogFilterSummaryOnShutdown() {
        String stoppingMethod = extractMethod(source, "public static void onServerStopping");
        assertTrue(stoppingMethod.contains("MixinLogFilter.logSummary()"),
            "Should log mixin filter summary during shutdown");
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
