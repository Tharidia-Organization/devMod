package com.devmod.client.ui.radial;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Radial Menu Ordering and Overrides")
class RadialMenuOrderingTest {

    private static String actionLayoutSource;
    private static String loaderSource;
    private static String configSource;
    private static String jsonSource;

    private static final Path ACTION_LAYOUT_SOURCE_PATH = Paths.get(
        "src/main/java/com/devmod/client/ui/radial/RadialMenuActionLayout.java");
    private static final Path LOADER_SOURCE_PATH = Paths.get(
        "src/main/java/com/devmod/client/ui/radial/config/RadialMenuDefinitionLoader.java");
    private static final Path CONFIG_SOURCE_PATH = Paths.get(
        "src/main/java/com/devmod/client/ui/radial/config/RadialMenuDefinitionConfig.java");
    private static final Path JSON_CONFIG_PATH = Paths.get(
        "config/devmod/radial_menu_definitions.json");

    @BeforeAll
    static void loadSourceCode() throws IOException {
        actionLayoutSource = Files.readString(ACTION_LAYOUT_SOURCE_PATH);
        loaderSource = Files.readString(LOADER_SOURCE_PATH);
        configSource = Files.readString(CONFIG_SOURCE_PATH);
        jsonSource = Files.readString(JSON_CONFIG_PATH);
    }

    @Test
    @DisplayName("Telemetry dashboard override uses Dashboard subcategory")
    void telemetryOverrideUsesDashboard() {
        assertTrue(actionLayoutSource.contains("UI_TELEMETRY_DASHBOARD_OPEN"),
            "Action layout should reference telemetry dashboard action ID");
        assertTrue(actionLayoutSource.contains("Root/Telemetry/Dashboard"),
            "Telemetry dashboard override should map to Root/Telemetry/Dashboard");
    }

    @Test
    @DisplayName("Config supports preserveOrdering flag")
    void configSupportsPreserveOrdering() {
        assertTrue(configSource.contains("preserveOrdering"),
            "RootConfig record should include preserveOrdering flag");
        assertTrue(loaderSource.contains("preserveOrdering()"),
            "Loader should read preserveOrdering flag");
        assertTrue(loaderSource.contains("!config.preserveOrdering()"),
            "Loader should skip default ordering when preserveOrdering is true");
    }

    @Test
    @DisplayName("JSON config preserves ordering by default")
    void jsonConfigPreservesOrdering() {
        assertTrue(jsonSource.contains("\"preserveOrdering\": true"),
            "Config JSON should set preserveOrdering to true");
    }
}
