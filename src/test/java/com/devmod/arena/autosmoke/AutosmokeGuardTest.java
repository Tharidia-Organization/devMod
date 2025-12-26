package com.devmod.arena.autosmoke;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutosmokeGuardTest {

    @Test
    void blocksWhenFeatureFlagDisabled() throws Exception {
        AutosmokeGuard guard = AutosmokeGuard.getInstance();
        Path originalMarker = guard.getProductionMarkerPath();
        try {
            Path tempDir = Files.createTempDirectory("autosmoke-guard-flag");
            Path marker = tempDir.resolve("marker");
            guard.setProductionMarkerPath(marker);
            guard.setFeatureFlagOverride(false);

            AutosmokeGuard.GuardResult result = guard.checkAll();

            assertFalse(result.flagCheckPassed());
            assertFalse(result.allowed());
        } finally {
            guard.setFeatureFlagOverride(null);
            guard.setProductionMarkerPath(originalMarker);
        }
    }

    @Test
    void blocksWhenMarkerExists() throws Exception {
        AutosmokeGuard guard = AutosmokeGuard.getInstance();
        Path originalMarker = guard.getProductionMarkerPath();
        try {
            Path tempDir = Files.createTempDirectory("autosmoke-guard-marker");
            Path marker = tempDir.resolve("marker");
            Files.createFile(marker);
            guard.setProductionMarkerPath(marker);
            guard.setFeatureFlagOverride(true);

            AutosmokeGuard.GuardResult result = guard.checkAll();

            assertFalse(result.markerCheckPassed());
            assertFalse(result.allowed());
        } finally {
            guard.setFeatureFlagOverride(null);
            guard.setProductionMarkerPath(originalMarker);
        }
    }

    @Test
    void allowsWhenAllChecksPass() throws Exception {
        AutosmokeGuard guard = AutosmokeGuard.getInstance();
        String env = guard.getCurrentEnvValue();
        Assumptions.assumeTrue(env == null || !AutosmokeGuard.PRODUCTION_ENV_VALUE.equalsIgnoreCase(env),
            "Skip guard allow test in production env");

        Path originalMarker = guard.getProductionMarkerPath();
        try {
            Path tempDir = Files.createTempDirectory("autosmoke-guard-allow");
            Path marker = tempDir.resolve("marker");
            guard.setProductionMarkerPath(marker);
            guard.setFeatureFlagOverride(true);

            AutosmokeGuard.GuardResult result = guard.checkAll();

            assertTrue(result.envCheckPassed());
            assertTrue(result.flagCheckPassed());
            assertTrue(result.markerCheckPassed());
            assertTrue(result.allowed());
        } finally {
            guard.setFeatureFlagOverride(null);
            guard.setProductionMarkerPath(originalMarker);
        }
    }
}
