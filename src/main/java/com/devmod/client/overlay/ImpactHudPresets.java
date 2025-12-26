package com.devmod.client.overlay;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import net.neoforged.neoforge.common.ModConfigSpec;

import com.devmod.DevMod;
import com.devmod.config.Config;
import com.devmod.util.ConfigPaths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Export/import utility for Impact HUD configuration presets.
 */
public final class ImpactHudPresets {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ImpactHudPresets() {}

    public static Path getDefaultPresetFile() {
        return ConfigPaths.getImpactHudPresetsFile();
    }

    public static boolean exportToDefault() {
        return exportToFile(getDefaultPresetFile());
    }

    public static boolean importFromDefault() {
        return importFromFile(getDefaultPresetFile());
    }

    public static boolean exportToFile(Path path) {
        if (path == null) {
            return false;
        }

        try {
            Files.createDirectories(path.getParent());

            JsonObject root = new JsonObject();
            root.addProperty("formatVersion", 1);
            root.addProperty("impactHudEnabled", Config.IMPACT_HUD_ENABLED.get());
            root.addProperty("impactHudPosition", Config.IMPACT_HUD_POSITION.get().name());
            root.addProperty("impactHudOffsetX", Config.IMPACT_HUD_OFFSET_X.get());
            root.addProperty("impactHudOffsetY", Config.IMPACT_HUD_OFFSET_Y.get());
            root.addProperty("impactHudHistoryEnabled", Config.IMPACT_HUD_HISTORY_ENABLED.get());
            root.addProperty("impactHudDpsEnabled", Config.IMPACT_HUD_DPS_ENABLED.get());
            root.addProperty("impactHudHistoryCount", Config.IMPACT_HUD_HISTORY_COUNT.get());
            root.addProperty("impactVfxDuration", Config.IMPACT_VFX_DURATION_MS.get());
            root.addProperty("impactVfxEnabled", Config.IMPACT_VFX_ENABLED.get());
            root.addProperty("impactVfxVortexEnabled", Config.IMPACT_VFX_VORTEX_ENABLED.get());
            root.addProperty("impactVfxSlashEnabled", Config.IMPACT_VFX_SLASH_ENABLED.get());
            root.addProperty("impactVfxLinesEnabled", Config.IMPACT_VFX_LINES_ENABLED.get());
            root.addProperty("impactVfxIntensity", Config.IMPACT_VFX_INTENSITY.get());

            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            DevMod.LOGGER.warn("[ImpactHudPresets] Failed to export presets to {}", path, e);
            return false;
        }
    }

    public static boolean importFromFile(Path path) {
        if (path == null || !Files.exists(path)) {
            return false;
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();

            applyBoolean(root, "impactHudEnabled", Config.IMPACT_HUD_ENABLED);
            applyPosition(root, "impactHudPosition", Config.IMPACT_HUD_POSITION);
            applyInt(root, "impactHudOffsetX", Config.IMPACT_HUD_OFFSET_X, 0, 200);
            applyInt(root, "impactHudOffsetY", Config.IMPACT_HUD_OFFSET_Y, 0, 200);
            applyBoolean(root, "impactHudHistoryEnabled", Config.IMPACT_HUD_HISTORY_ENABLED);
            applyBoolean(root, "impactHudDpsEnabled", Config.IMPACT_HUD_DPS_ENABLED);
            applyInt(root, "impactHudHistoryCount", Config.IMPACT_HUD_HISTORY_COUNT, 1, 10);
            applyInt(root, "impactVfxDuration", Config.IMPACT_VFX_DURATION_MS, 100, 5000);
            applyBoolean(root, "impactVfxEnabled", Config.IMPACT_VFX_ENABLED);
            applyBoolean(root, "impactVfxVortexEnabled", Config.IMPACT_VFX_VORTEX_ENABLED);
            applyBoolean(root, "impactVfxSlashEnabled", Config.IMPACT_VFX_SLASH_ENABLED);
            applyBoolean(root, "impactVfxLinesEnabled", Config.IMPACT_VFX_LINES_ENABLED);
            applyDouble(root, "impactVfxIntensity", Config.IMPACT_VFX_INTENSITY, 0.1, 2.0);

            return true;
        } catch (Exception e) {
            DevMod.LOGGER.warn("[ImpactHudPresets] Failed to import presets from {}", path, e);
            return false;
        }
    }

    private static void applyBoolean(JsonObject root, String key, ModConfigSpec.BooleanValue target) {
        if (root.has(key) && root.get(key).isJsonPrimitive()) {
            target.set(root.get(key).getAsBoolean());
        }
    }

    private static void applyInt(JsonObject root, String key, ModConfigSpec.IntValue target, int min, int max) {
        if (root.has(key) && root.get(key).isJsonPrimitive()) {
            int value = root.get(key).getAsInt();
            target.set(Math.max(min, Math.min(max, value)));
        }
    }

    private static void applyDouble(JsonObject root, String key, ModConfigSpec.DoubleValue target, double min, double max) {
        if (root.has(key) && root.get(key).isJsonPrimitive()) {
            double value = root.get(key).getAsDouble();
            target.set(Math.max(min, Math.min(max, value)));
        }
    }

    private static void applyPosition(JsonObject root, String key, ModConfigSpec.EnumValue<Config.HudPosition> target) {
        if (root.has(key) && root.get(key).isJsonPrimitive()) {
            String value = root.get(key).getAsString();
            try {
                target.set(Config.HudPosition.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                DevMod.LOGGER.warn("[ImpactHudPresets] Invalid HUD position '{}'", value);
            }
        }
    }
}
