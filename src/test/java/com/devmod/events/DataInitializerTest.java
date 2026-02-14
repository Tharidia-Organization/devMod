package com.devmod.events;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DataInitializer} - the async DuckDB service initialization utility.
 * Focuses on verifying:
 * - Non-blocking async patterns (no .join() on hot path)
 * - Proper error handling (.exceptionally)
 * - Shutdown timeout safety
 * - Package-private encapsulation
 */
@DisplayName("DataInitializer")
class DataInitializerTest {

    private static String source;

    @BeforeAll
    static void loadSource() throws IOException {
        source = Files.readString(Paths.get(
            "src/main/java/com/devmod/events/DataInitializer.java"));
    }

    // --- BUG-05 regression: no blocking .join() on server startup path ---

    @Test
    @DisplayName("initializeDuckDBServices does not block with .join()")
    void initializeDuckDBServicesDoesNotBlock() {
        // The init methods (initializeMailboxManager, initializeNotificationRepositories)
        // must NOT call .join() - that was the blocking bug (BUG-05)
        String initMailbox = extractMethod(source, "private static void initializeMailboxManager");
        String initNotif = extractMethod(source, "private static void initializeNotificationRepositories");
        String initServices = extractMethod(source, "static void initializeDuckDBServices");

        assertFalse(initMailbox.contains(".join()"),
            "initializeMailboxManager must not call .join() (BUG-05 regression)");
        assertFalse(initNotif.contains(".join()"),
            "initializeNotificationRepositories must not call .join() (BUG-05 regression)");
        assertFalse(initServices.contains(".join()"),
            "initializeDuckDBServices must not call .join() (BUG-05 regression)");
    }

    @Test
    @DisplayName("Initialization uses CompletableFuture composition (thenRun/thenAccept)")
    void initUsesAsyncComposition() {
        assertTrue(source.contains(".thenRun("),
            "Should use .thenRun() for non-blocking chaining");
        assertTrue(source.contains(".exceptionally("),
            "Should use .exceptionally() for async error handling");
    }

    @Test
    @DisplayName("Notification repositories are initialized with allOf for parallel startup")
    void notificationReposInitializedInParallel() {
        assertTrue(source.contains("CompletableFuture.allOf(historyFuture, prefsFuture)"),
            "Both notification repos should be initialized in parallel via allOf");
    }

    // --- Error handling ---

    @Test
    @DisplayName("initializeMailboxManager catches DuckDB native library errors")
    void mailboxManagerCatchesDuckDbNativeErrors() {
        String initMailbox = extractMethod(source, "private static void initializeMailboxManager");
        assertTrue(initMailbox.contains("NoClassDefFoundError"),
            "Should catch NoClassDefFoundError for missing DuckDB native lib");
        assertTrue(initMailbox.contains("UnsatisfiedLinkError"),
            "Should catch UnsatisfiedLinkError for missing DuckDB native lib");
        assertTrue(initMailbox.contains("ExceptionInInitializerError"),
            "Should catch ExceptionInInitializerError for DuckDB init failures");
    }

    @Test
    @DisplayName("initializeNotificationRepositories catches DuckDB native library errors")
    void notificationReposCatchDuckDbNativeErrors() {
        String initNotif = extractMethod(source, "private static void initializeNotificationRepositories");
        assertTrue(initNotif.contains("NoClassDefFoundError"),
            "Should catch NoClassDefFoundError for missing DuckDB native lib");
        assertTrue(initNotif.contains("UnsatisfiedLinkError"),
            "Should catch UnsatisfiedLinkError for missing DuckDB native lib");
    }

    @Test
    @DisplayName("initializeDuckDBServices logs warning when DuckDB not available")
    void logsWarningWhenDuckDbUnavailable() {
        assertTrue(source.contains("DuckDB not available - Mailbox and Notification features disabled"),
            "Should warn when DuckDB is not available");
    }

    @Test
    @DisplayName("Both async chains have .exceptionally() error handlers")
    void asyncChainsHaveExceptionallyHandlers() {
        String initMailbox = extractMethod(source, "private static void initializeMailboxManager");
        String initNotif = extractMethod(source, "private static void initializeNotificationRepositories");
        assertTrue(initMailbox.contains(".exceptionally("),
            "Mailbox init chain must have .exceptionally() handler");
        assertTrue(initNotif.contains(".exceptionally("),
            "Notification init chain must have .exceptionally() handler");
    }

    // --- Shutdown safety ---

    @Test
    @DisplayName("shutdownDuckDBServices uses timeout on MailboxManager shutdown")
    void shutdownUsesTimeout() {
        String shutdownMethod = extractMethod(source, "static void shutdownDuckDBServices");
        assertTrue(shutdownMethod.contains(".orTimeout("),
            "MailboxManager shutdown must use .orTimeout() to prevent hang");
        assertTrue(shutdownMethod.contains("TimeUnit.SECONDS"),
            "Timeout should be specified in seconds");
    }

    @Test
    @DisplayName("Shutdown is a no-op when DuckDB was never available")
    void shutdownNoOpWhenDuckDbUnavailable() {
        String shutdownMethod = extractMethod(source, "static void shutdownDuckDBServices");
        assertTrue(shutdownMethod.contains("DuckDBBootstrap.isAvailable()"),
            "Should check DuckDB availability before shutdown");
        // The early return pattern: if (!isAvailable()) return;
        assertTrue(shutdownMethod.contains("return;"),
            "Should return early when DuckDB not available");
    }

    @Test
    @DisplayName("Shutdown cleans up all DuckDB-dependent services")
    void shutdownCleansUpAllServices() {
        String shutdownMethod = extractMethod(source, "static void shutdownDuckDBServices");
        assertTrue(shutdownMethod.contains("NotificationService.INSTANCE.shutdown()"),
            "Should shutdown NotificationService");
        assertTrue(shutdownMethod.contains("PartyNotificationBridge.unregister()"),
            "Should unregister PartyNotificationBridge");
        assertTrue(shutdownMethod.contains("NotificationHistoryRepository.INSTANCE.shutdown()"),
            "Should shutdown NotificationHistoryRepository");
        assertTrue(shutdownMethod.contains("NotificationPreferencesRepository.INSTANCE.shutdown()"),
            "Should shutdown NotificationPreferencesRepository");
        assertTrue(shutdownMethod.contains("MailboxManager.INSTANCE.shutdown()"),
            "Should shutdown MailboxManager");
    }

    // --- Encapsulation ---

    @Test
    @DisplayName("DataInitializer class is package-private (final, no public)")
    void classIsPackagePrivate() {
        assertTrue(source.contains("final class DataInitializer"),
            "DataInitializer should be final and package-private");
        assertFalse(source.contains("public final class DataInitializer"),
            "DataInitializer must not be public");
        assertFalse(source.contains("public class DataInitializer"),
            "DataInitializer must not be public");
    }

    @Test
    @DisplayName("DataInitializer has private constructor (utility class)")
    void hasPrivateConstructor() {
        assertTrue(source.contains("private DataInitializer()"),
            "Utility class should have private constructor");
    }

    @Test
    @DisplayName("Initialization methods are package-private (not public)")
    void initMethodsArePackagePrivate() {
        // The static methods called from ModLifecycleEvents should be package-private
        assertTrue(source.contains("static void initializeDuckDBServices"),
            "initializeDuckDBServices should exist");
        assertFalse(source.contains("public static void initializeDuckDBServices"),
            "initializeDuckDBServices must not be public");
        assertTrue(source.contains("static void shutdownDuckDBServices"),
            "shutdownDuckDBServices should exist");
        assertFalse(source.contains("public static void shutdownDuckDBServices"),
            "shutdownDuckDBServices must not be public");
    }

    // --- Initialization ordering ---

    @Test
    @DisplayName("NotificationService is initialized after repositories complete")
    void notificationServiceInitAfterRepos() {
        String initNotif = extractMethod(source, "private static void initializeNotificationRepositories");
        // NotificationService.INSTANCE.initialize() must be inside the thenRun block
        // that fires after allOf(historyFuture, prefsFuture)
        int allOfIdx = initNotif.indexOf("allOf(historyFuture, prefsFuture)");
        int thenRunIdx = initNotif.indexOf(".thenRun(", allOfIdx);
        int notifInitIdx = initNotif.indexOf("NotificationService.INSTANCE.initialize()", thenRunIdx);
        assertTrue(allOfIdx >= 0 && thenRunIdx > allOfIdx && notifInitIdx > thenRunIdx,
            "NotificationService must be initialized inside thenRun after allOf completes");
    }

    @Test
    @DisplayName("PartyNotificationBridge is registered after NotificationService init")
    void partyBridgeAfterNotificationService() {
        String initNotif = extractMethod(source, "private static void initializeNotificationRepositories");
        int notifIdx = initNotif.indexOf("NotificationService.INSTANCE.initialize()");
        int bridgeIdx = initNotif.indexOf("PartyNotificationBridge.register()");
        assertTrue(notifIdx >= 0 && bridgeIdx > notifIdx,
            "PartyNotificationBridge.register() must come after NotificationService.initialize()");
    }

    /**
     * Extract a method body from source by finding the method signature
     * and then counting braces to find the end.
     */
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
}
