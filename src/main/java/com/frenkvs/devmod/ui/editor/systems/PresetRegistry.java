package com.frenkvs.devmod.ui.editor.systems;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * Registry and resolver for presets with 3-level hierarchy.
 * Minimal implementation focusing on core resolution logic.
 */
public final class PresetRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(PresetRegistry.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final Map<PresetScope, List<Preset>> presets = new HashMap<>();
    private String detectedModpack = null;
    
    public record Preset(
        String id,
        String name, 
        String description,
        PresetScope scope,
        Map<String, Object> values,
        PresetMetadata metadata
    ) {
        public record PresetMetadata(
            String version,
            String author,
            Instant created,
            List<String> tags
        ) {}
    }
    
    /**
     * Load presets from config directory structure.
     */
    public void loadFromConfig(Path configDir) {
        Path presetsDir = configDir.resolve("presets");
        if (!Files.exists(presetsDir)) {
            LOGGER.info("No presets directory found, creating defaults");
            createDefaultPresets(presetsDir);
            return;
        }
        
        try {
            // Load global presets
            loadPresetsFromDir(presetsDir.resolve("global"), new PresetScope.Global());
            
            // Load category presets  
            Path categoryDir = presetsDir.resolve("category");
            if (Files.exists(categoryDir)) {
                try (Stream<Path> categories = Files.list(categoryDir)) {
                    categories.filter(Files::isDirectory)
                        .forEach(catDir -> {
                            String category = catDir.getFileName().toString();
                            loadPresetsFromDir(catDir, new PresetScope.Category(category));
                        });
                }
            }
            
            // Load modpack presets
            detectedModpack = detectModpack();
            if (detectedModpack != null) {
                Path modpackDir = presetsDir.resolve("modpack").resolve(detectedModpack);
                if (Files.exists(modpackDir)) {
                    try (Stream<Path> categories = Files.list(modpackDir)) {
                        categories.filter(Files::isDirectory)
                            .forEach(catDir -> {
                                String category = catDir.getFileName().toString();
                                loadPresetsFromDir(catDir, new PresetScope.Modpack(detectedModpack, category));
                            });
                    }
                }
            }
            
            LOGGER.info("Loaded {} preset scopes, detected modpack: {}", presets.size(), detectedModpack);
        } catch (IOException e) {
            LOGGER.error("Failed to load presets", e);
        }
    }
    
    /**
     * Resolve applicable presets for an item, respecting hierarchy.
     */
    public List<Preset> resolveForItem(ItemStack item) {
        String category = getItemCategory(item);
        List<Preset> result = new ArrayList<>();
        
        // 1. Modpack presets (highest priority)
        if (detectedModpack != null) {
            result.addAll(getPresets(new PresetScope.Modpack(detectedModpack, category)));
        }
        
        // 2. Category presets
        result.addAll(getPresets(new PresetScope.Category(category)));
        
        // 3. Global presets (lowest priority)
        result.addAll(getPresets(new PresetScope.Global()));
        
        return result;
    }
    
    private void loadPresetsFromDir(Path dir, PresetScope scope) {
        if (!Files.exists(dir)) return;
        
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> json = GSON.fromJson(content, Map.class);
                        
                        Preset preset = parsePreset(json, scope);
                        if (preset != null) {
                            presets.computeIfAbsent(scope, k -> new ArrayList<>()).add(preset);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to load preset from {}: {}", file, e.getMessage());
                    }
                });
        } catch (IOException e) {
            LOGGER.warn("Failed to list presets in {}", dir, e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Preset parsePreset(Map<String, Object> json, PresetScope scope) {
        try {
            String id = (String) json.get("id");
            String name = (String) json.get("name");
            String description = (String) json.getOrDefault("description", "");
            Map<String, Object> values = (Map<String, Object>) json.getOrDefault("values", Map.of());
            
            Map<String, Object> metaJson = (Map<String, Object>) json.getOrDefault("metadata", Map.of());
            Preset.PresetMetadata metadata = new Preset.PresetMetadata(
                (String) metaJson.getOrDefault("version", "1.0.0"),
                (String) metaJson.getOrDefault("author", "Unknown"),
                Instant.now(),
                (List<String>) metaJson.getOrDefault("tags", List.of())
            );
            
            return new Preset(id, name, description, scope, values, metadata);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse preset: {}", e.getMessage());
            return null;
        }
    }
    
    private List<Preset> getPresets(PresetScope scope) {
        return presets.getOrDefault(scope, List.of());
    }
    
    private String getItemCategory(ItemStack item) {
        // Simplified category detection
        String itemName = item.getItem().toString().toLowerCase();
        if (itemName.contains("sword")) return "sword";
        if (itemName.contains("bow")) return "bow";
        if (itemName.contains("helmet")) return "helmet";
        if (itemName.contains("chestplate")) return "chestplate";
        if (itemName.contains("leggings")) return "leggings";
        if (itemName.contains("boots")) return "boots";
        if (itemName.contains("shield")) return "shield";
        return "unknown";
    }
    
    private String detectModpack() {
        // Simplified modpack detection - can be enhanced later
        return null; // No modpack detected for now
    }
    
    private void createDefaultPresets(Path presetsDir) {
        try {
            Files.createDirectories(presetsDir.resolve("global/weapon"));
            Files.createDirectories(presetsDir.resolve("global/armor"));
            
            // Create basic weapon preset
            Map<String, Object> weaponPreset = Map.of(
                "id", "vanilla_default",
                "name", "Vanilla Default",
                "description", "Vanilla-like weapon stats",
                "values", Map.of(
                    "attackDamage", 7.0,
                    "attackSpeed", 1.6,
                    "critChance", 0.05
                ),
                "metadata", Map.of(
                    "version", "1.0.0",
                    "author", "DevMod",
                    "tags", List.of("default", "vanilla")
                )
            );
            
            Files.writeString(
                presetsDir.resolve("global/weapon/vanilla_default.json"),
                GSON.toJson(weaponPreset)
            );
            
            LOGGER.info("Created default presets");
        } catch (IOException e) {
            LOGGER.error("Failed to create default presets", e);
        }
    }
}