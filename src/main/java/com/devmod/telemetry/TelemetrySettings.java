package com.devmod.telemetry;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;

import com.devmod.util.ConfigPaths;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;

public record TelemetrySettings(long stuckMs, long campingMs, int campingHits, long aggroDropMs, long outOfBoundsMs, double outOfBoundsDeltaY, double bossHpThreshold, boolean bossPhaseDetectionEnabled, boolean skillTrackingEnabled) {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    public static TelemetrySettings defaults() {
        return new TelemetrySettings(3_000, 10_000, 3, 3_000, 2_000, 4.0, 100.0, true, true);
    }

    public static TelemetrySettings load() {
        Path file = ConfigPaths.getTelemetrySettingsFile();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            LOGGER.error("Cannot create config directory for telemetry settings", e);
        }

        if (!Files.exists(file)) {
            writeDefaults(file);
            return defaults();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            TelemetrySettings s = GSON.fromJson(reader, TelemetrySettings.class);
            return s != null ? normalize(s) : defaults();
        } catch (Exception e) {
            LOGGER.error("Failed to read telemetry settings, using defaults", e);
            return defaults();
        }
    }

    private static void writeDefaults(Path file) {
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(defaults(), writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write default telemetry settings", e);
        }
    }

    private static TelemetrySettings normalize(TelemetrySettings s) {
        TelemetrySettings d = defaults();
        long stuck = s.stuckMs > 0 ? s.stuckMs : d.stuckMs;
        long camping = s.campingMs > 0 ? s.campingMs : d.campingMs;
        int campingHits = s.campingHits > 0 ? s.campingHits : d.campingHits;
        long aggro = s.aggroDropMs > 0 ? s.aggroDropMs : d.aggroDropMs;
        long oobMs = s.outOfBoundsMs > 0 ? s.outOfBoundsMs : d.outOfBoundsMs;
        double oobDelta = s.outOfBoundsDeltaY > 0 ? s.outOfBoundsDeltaY : d.outOfBoundsDeltaY;
        double bossHp = s.bossHpThreshold > 0 ? s.bossHpThreshold : d.bossHpThreshold;
        return new TelemetrySettings(stuck, camping, campingHits, aggro, oobMs, oobDelta, bossHp, s.bossPhaseDetectionEnabled, s.skillTrackingEnabled);
    }
}
