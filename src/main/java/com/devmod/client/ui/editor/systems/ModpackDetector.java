package com.devmod.client.ui.editor.systems;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;

import com.devmod.DevMod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Detects the current modpack using multiple strategies.
 *
 * Detection order (first match wins):
 * 1. Explicit config file: config/devmod/modpack.txt
 * 2. CurseForge/Modrinth manifest: manifest.json in game root
 * 3. Known mod combinations (signature detection)
 */
public final class ModpackDetector {

    // Known modpack signatures: modpack ID -> required mod IDs
    private static final Map<String, Set<String>> KNOWN_MODPACKS = Map.of(
        "rlcraft", Set.of("lycanitesmobs", "iceandfire", "spartanweaponry"),
        "better_minecraft", Set.of("create", "farmersdelight", "supplementaries"),
        "all_the_mods_9", Set.of("mekanism", "thermal", "ae2"),
        "vault_hunters", Set.of("the_vault", "iskallia"),
        "create_above_beyond", Set.of("create", "kubejs", "ftbquests"),
        "enigmatica", Set.of("apotheosis", "thermal", "mekanism")
    );

    // Cached result (lazy init)
    private static String cachedModpack = null;
    private static boolean detectionDone = false;

    private ModpackDetector() {}

    /**
     * Detect the current modpack.
     *
     * @return modpack ID or null if not detected
     */
    @Nullable
    public static String detect() {
        if (detectionDone) {
            return cachedModpack;
        }

        cachedModpack = doDetection();
        detectionDone = true;

        if (cachedModpack != null) {
            DevMod.LOGGER.info("[PresetRegistry] Detected modpack: {}", cachedModpack);
        } else {
            DevMod.LOGGER.debug("[PresetRegistry] No modpack detected");
        }

        return cachedModpack;
    }

    /**
     * Get cached modpack result without re-detection.
     */
    @Nullable
    public static String getCachedModpack() {
        return cachedModpack;
    }

    /**
     * Force re-detection (useful for config reload).
     */
    public static void invalidateCache() {
        detectionDone = false;
        cachedModpack = null;
    }

    private static String doDetection() {
        // Strategy 1: Explicit config file
        String explicit = detectFromExplicitConfig();
        if (explicit != null) {
            DevMod.LOGGER.debug("[ModpackDetector] Detected via explicit config: {}", explicit);
            return explicit;
        }

        // Strategy 2: Manifest file (CurseForge/Modrinth)
        String manifest = detectFromManifest();
        if (manifest != null) {
            DevMod.LOGGER.debug("[ModpackDetector] Detected via manifest: {}", manifest);
            return manifest;
        }

        // Strategy 3: Known mod combinations
        String signature = detectFromModSignature();
        if (signature != null) {
            DevMod.LOGGER.debug("[ModpackDetector] Detected via mod signature: {}", signature);
            return signature;
        }

        return null;
    }

    /**
     * Strategy 1: Check explicit config file.
     */
    @Nullable
    private static String detectFromExplicitConfig() {
        try {
            Path configFile = FMLPaths.CONFIGDIR.get().resolve("devmod/modpack.txt");
            if (Files.exists(configFile)) {
                String content = Files.readString(configFile, StandardCharsets.UTF_8).trim();
                if (!content.isEmpty() && !content.startsWith("#")) {
                    return normalizeModpackName(content);
                }
            }
        } catch (Exception e) {
            DevMod.LOGGER.warn("[ModpackDetector] Failed to read modpack.txt: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Strategy 2: Check manifest.json (CurseForge/Modrinth format).
     */
    @Nullable
    private static String detectFromManifest() {
        try {
            // Check game root
            Path manifest = FMLPaths.GAMEDIR.get().resolve("manifest.json");
            if (!Files.exists(manifest)) {
                // Try parent directory (for development environments)
                manifest = FMLPaths.GAMEDIR.get().getParent().resolve("manifest.json");
            }

            if (Files.exists(manifest)) {
                try (BufferedReader reader = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                    // CurseForge format
                    if (json.has("name")) {
                        return normalizeModpackName(json.get("name").getAsString());
                    }

                    // Modrinth format
                    if (json.has("pack") && json.get("pack").isJsonObject()) {
                        JsonObject pack = json.getAsJsonObject("pack");
                        if (pack.has("name")) {
                            return normalizeModpackName(pack.get("name").getAsString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            DevMod.LOGGER.warn("[ModpackDetector] Failed to read manifest.json: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Strategy 3: Detect by checking for known mod combinations.
     */
    @Nullable
    private static String detectFromModSignature() {
        try {
            Set<String> loadedMods = ModList.get().getMods().stream()
                .map(IModInfo::getModId)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

            // Check each known modpack signature
            for (var entry : KNOWN_MODPACKS.entrySet()) {
                if (loadedMods.containsAll(entry.getValue())) {
                    return entry.getKey();
                }
            }
        } catch (Exception e) {
            DevMod.LOGGER.warn("[ModpackDetector] Failed to check mod signature: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Normalize modpack name to a filesystem-safe identifier.
     */
    private static String normalizeModpackName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        return name.toLowerCase()
            .replaceAll("[^a-z0-9_]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
    }
}
