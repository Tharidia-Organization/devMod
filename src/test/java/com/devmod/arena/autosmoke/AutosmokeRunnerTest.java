package com.devmod.arena.autosmoke;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.telemetry.ArenaTelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AutosmokeRunnerTest {

    @Test
    void runAllReturnsReportForSmokeTemplates() throws Exception {
        AutosmokeGuard guard = AutosmokeGuard.getInstance();
        String env = guard.getCurrentEnvValue();
        Assumptions.assumeTrue(env == null || !AutosmokeGuard.PRODUCTION_ENV_VALUE.equalsIgnoreCase(env),
            "Skip autosmoke runner test in production env");

        Path originalMarker = guard.getProductionMarkerPath();
        try (ArenaTemplateRegistry registry = new ArenaTemplateRegistry(new ArenaTelemetry())) {
            Path tempDir = Files.createTempDirectory("autosmoke-runner");
            Path marker = tempDir.resolve("marker");
            guard.setProductionMarkerPath(marker);
            guard.setFeatureFlagOverride(true);

            registry.load(ArenaTemplate.smokeFlat64Template());

            AutosmokeRunner runner = new AutosmokeRunner(
                registry,
                guard,
                AutosmokeExceptions.getInstance(),
                new AutosmokeSizeThresholds()
            );

            AutosmokeRunner.AutosmokeReport report = runner.runAll();

            assertNotNull(report);
            assertEquals(1, report.passedCount());
            assertEquals(0, report.failedCount());
            assertEquals(1, report.results().size());
        } finally {
            guard.setFeatureFlagOverride(null);
            guard.setProductionMarkerPath(originalMarker);
        }
    }

    @Test
    void runAllReturnsNullWhenGuardBlocks() throws Exception {
        AutosmokeGuard guard = AutosmokeGuard.getInstance();
        Path originalMarker = guard.getProductionMarkerPath();
        try (ArenaTemplateRegistry registry = new ArenaTemplateRegistry(new ArenaTelemetry())) {
            Path tempDir = Files.createTempDirectory("autosmoke-runner-block");
            Path marker = tempDir.resolve("marker");
            Files.createFile(marker);
            guard.setProductionMarkerPath(marker);
            guard.setFeatureFlagOverride(true);

            registry.load(ArenaTemplate.smokeFlat64Template());

            AutosmokeRunner runner = new AutosmokeRunner(
                registry,
                guard,
                AutosmokeExceptions.getInstance(),
                new AutosmokeSizeThresholds()
            );

            AutosmokeRunner.AutosmokeReport report = runner.runAll();

            assertNull(report);
        } finally {
            guard.setFeatureFlagOverride(null);
            guard.setProductionMarkerPath(originalMarker);
        }
    }
}
