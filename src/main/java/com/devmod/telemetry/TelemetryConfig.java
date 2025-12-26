package com.devmod.telemetry;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;

import net.minecraft.core.BlockPos;

import com.devmod.util.ConfigPaths;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;

/**
 * Loads room definitions from config/devmod/telemetry_rooms.json.
 * If the file does not exist, a sample is created and an empty list is returned.
 */
public class TelemetryConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<RoomDefinition>>() {}.getType();

    public List<RoomDefinition> loadRooms() {
        Path file = ConfigPaths.getTelemetryRoomsFile();
        Path configDir = file.getParent();

        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Unable to create telemetry config directory", e);
            return Collections.emptyList();
        }

        if (!Files.exists(file)) {
            writeSample(file);
            LOGGER.info("Created sample telemetry_rooms.json (no rooms loaded yet)");
            return Collections.emptyList();
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            List<RoomDefinition> rooms = GSON.fromJson(reader, LIST_TYPE);
            return rooms != null ? rooms : Collections.emptyList();
        } catch (Exception e) {
            LOGGER.error("Failed to read telemetry room config, falling back to no rooms", e);
            return Collections.emptyList();
        }
    }

    private void writeSample(Path file) {
        var sample = List.of(new RoomDefinition("example_room",
                new BlockPos(-10, 60, -10),
                new BlockPos(10, 80, 10)));
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(sample, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write sample telemetry_rooms.json", e);
        }
    }
}
