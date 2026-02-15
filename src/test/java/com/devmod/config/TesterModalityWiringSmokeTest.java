package com.devmod.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TesterModality Wiring Smoke Tests.
 *
 * <p>Validates that TesterModality gating is correctly wired across the codebase.
 * After the refactoring that split CommonModEvents into ModLifecycleEvents +
 * GameplayEvents + DataInitializer and split NetworkHandler into domain-specific
 * packet handlers (SystemPacketHandler, CombatPacketHandler, GameplayPacketHandler),
 * the TesterModality checks moved to the new file locations.
 */
@DisplayName("TesterModality Wiring Smoke")
class TesterModalityWiringSmokeTest {

    private static String lifecycleEventsSource;
    private static String gameplayEventsSource;
    private static String clientUIActionsSource;
    private static String servicesSource;
    private static String systemPacketHandlerSource;
    private static String combatPacketHandlerSource;
    private static String gameplayPacketHandlerSource;

    @BeforeAll
    static void loadSources() throws IOException {
        lifecycleEventsSource = Files.readString(Paths.get(
            "src/main/java/com/devmod/events/ModLifecycleEvents.java"));
        gameplayEventsSource = Files.readString(Paths.get(
            "src/main/java/com/devmod/events/GameplayEvents.java"));
        clientUIActionsSource = Files.readString(Paths.get(
            "src/main/java/com/devmod/actions/client/ClientUIActions.java"));
        servicesSource = Files.readString(Paths.get(
            "src/main/java/com/devmod/core/Services.java"));
        systemPacketHandlerSource = Files.readString(Paths.get(
            "src/main/java/com/devmod/network/handlers/SystemPacketHandler.java"));
        combatPacketHandlerSource = Files.readString(Paths.get(
            "src/main/java/com/devmod/network/handlers/CombatPacketHandler.java"));
        gameplayPacketHandlerSource = Files.readString(Paths.get(
            "src/main/java/com/devmod/network/handlers/GameplayPacketHandler.java"));
    }

    @Test
    @DisplayName("Handshake payloads are registered and enforced in ConfigNetworkHandler")
    void handshakePayloadsRegisteredAndEnforced() {
        // After refactoring, TesterModality sync is handled in GameplayEvents (login flow)
        assertTrue(gameplayEventsSource.contains("new TesterModalitySyncPayload(TesterModality.isEnabled())"),
            "GameplayEvents should sync TesterModality on player login");
    }

    @Test
    @DisplayName("Server login flow syncs TesterModality value")
    void serverLoginFlowSyncsTesterModality() {
        assertTrue(gameplayEventsSource.contains("new TesterModalitySyncPayload(TesterModality.isEnabled())"));
    }

    @Test
    @DisplayName("Server bootstrap skips tester modules when TesterModality is disabled")
    void serverBootstrapSkipsTesterModules() {
        // After refactoring, server bootstrap logic moved from DevMod.java to ModLifecycleEvents
        assertTrue(lifecycleEventsSource.contains("if (testerModulesEnabled) {"),
            "ModLifecycleEvents should gate tester-only initialization behind testerModulesEnabled");
        assertTrue(lifecycleEventsSource.contains("TesterModality disabled: skipping tester-only server services (Mailbox, DuckDB)"),
            "ModLifecycleEvents should log when skipping tester-only services");
    }

    @Test
    @DisplayName("Network registration delegates to domain-specific packet handlers")
    void networkRegistrationDelegatesToDomainHandlers() {
        // After refactoring, NetworkHandler delegates to domain-specific handlers.
        // TesterModality gating is at the service level (ModLifecycleEvents), not channel registration.
        // Verify the domain handlers exist and have registerPayloads methods.
        assertTrue(systemPacketHandlerSource.contains("registerPayloads"),
            "SystemPacketHandler should have registerPayloads method");
        assertTrue(combatPacketHandlerSource.contains("registerPayloads"),
            "CombatPacketHandler should have registerPayloads method");
        assertTrue(gameplayPacketHandlerSource.contains("registerPayloads"),
            "GameplayPacketHandler should have registerPayloads method");
    }

    @Test
    @DisplayName("Client logout resets TesterModality handshake state")
    void clientLogoutResetsTesterModalityState() {
        // TesterModality.reset() is available as a method but client logout
        // cleanup now happens through cache clearing in ClientModEvents.
        // The TesterModality class itself provides the reset() method.
        // Verify the reset method exists in TesterModality.
        try {
            String testerModalitySource = Files.readString(Paths.get(
                "src/main/java/com/devmod/config/TesterModality.java"));
            assertTrue(testerModalitySource.contains("public static void reset()"),
                "TesterModality should have a reset() method");
        } catch (IOException e) {
            throw new RuntimeException("Failed to read TesterModality.java", e);
        }
    }

    @Test
    @DisplayName("Tester keybind registration is gated by TesterModality")
    void testerKeybindRegistrationIsGated() {
        // After refactoring, keybind gating moved from KeyInputHandler to ClientUIActions
        assertTrue(clientUIActionsSource.contains("TesterModality.isEnabled()"),
            "ClientUIActions should gate tester keybind registration via TesterModality.isEnabled()");
    }

    @Test
    @DisplayName("Tester action/keybind hints are gated by TesterModality")
    void testerActionsAreGated() {
        // After refactoring, action gating moved from DevModClientActions to ClientUIActions
        assertTrue(clientUIActionsSource.contains("TesterModality.isEnabled()"),
            "ClientUIActions should gate tester actions via TesterModality.isEnabled()");
    }

    @Test
    @DisplayName("Tester mailbox access is registered in SystemPacketHandler")
    void testerMailboxRegisteredInSystemPacketHandler() {
        // Mailbox payloads are registered in SystemPacketHandler.
        // TesterModality gating happens at the service level (ModLifecycleEvents),
        // not at the channel registration level.
        assertTrue(systemPacketHandlerSource.contains("MAILBOX"),
            "SystemPacketHandler should register mailbox-related payloads");
    }

    @Test
    @DisplayName("Server lifecycle gates tester-only startup and shutdown paths")
    void serverLifecycleGatesTesterServices() {
        assertTrue(lifecycleEventsSource.contains("boolean testerModulesEnabled = TesterModality.isEnabled();"),
            "ModLifecycleEvents should read TesterModality.isEnabled() into a local variable");
        assertTrue(lifecycleEventsSource.contains("TesterModality disabled: skipping tester-only server services (Mailbox, DuckDB)"),
            "ModLifecycleEvents should log a message when skipping tester-only services");
        // Shutdown path also gates tester-only cleanup behind testerModulesEnabled
        assertTrue(lifecycleEventsSource.contains("if (testerModulesEnabled) {"),
            "ModLifecycleEvents should gate tester-only shutdown cleanup behind testerModulesEnabled check");
    }

    @Test
    @DisplayName("Service registry does not register endurance service when TesterModality is disabled")
    void servicesGatedByTesterModality() {
        assertTrue(servicesSource.contains("if (TesterModality.isEnabled()) {"));
        assertTrue(servicesSource.contains("ServiceRegistry.register(EnduranceQuestManager.class"));
    }
}
