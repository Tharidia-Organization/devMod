package com.devmod.endurance;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.neoforged.fml.loading.FMLPaths;

public final class KitPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(KitPersistence.class);
    private static final String KITS_DIR = "devmod/kits";
    private static final String KITS_FILE = "custom_kits.json";

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    // In-memory cache of custom kits
    private static final Map<String, CustomKit> customKits = new LinkedHashMap<>();
    private static boolean loaded = false;

    private KitPersistence() {}

    /**
     * Get the kits directory path.
     */
    private static Path getKitsDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(KITS_DIR);
    }

    /**
     * Get the kits file path.
     */
    private static Path getKitsFile() {
        return getKitsDirectory().resolve(KITS_FILE);
    }

    /**
     * Ensure the kits directory exists.
     */
    private static void ensureDirectory() {
        try {
            Path dir = getKitsDirectory();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                LOGGER.info("[KitPersistence] Created kits directory: {}", dir);
            }
        } catch (IOException e) {
            LOGGER.error("[KitPersistence] Failed to create kits directory", e);
        }
    }

    /**
     * Load all custom kits from disk.
     */
    public static void loadKits() {
        ensureDirectory();
        customKits.clear();

        Path file = getKitsFile();
        if (!Files.exists(file)) {
            LOGGER.info("[KitPersistence] No custom kits file found, starting fresh");
            loaded = true;
            return;
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<KitData>>(){}.getType();
            List<KitData> kitDataList = GSON.fromJson(json, listType);

            if (kitDataList != null) {
                for (KitData data : kitDataList) {
                    try {
                        CustomKit kit = data.toCustomKit();
                        customKits.put(kit.getId(), kit);
                    } catch (Exception e) {
                        LOGGER.warn("[KitPersistence] Failed to parse kit: {}", data.name, e);
                    }
                }
            }

            LOGGER.info("[KitPersistence] Loaded {} custom kits", customKits.size());
            loaded = true;
        } catch (IOException e) {
            LOGGER.error("[KitPersistence] Failed to load custom kits", e);
            loaded = true;
        }
    }

    /**
     * Save all custom kits to disk.
     */
    public static boolean saveKits() {
        ensureDirectory();

        List<KitData> kitDataList = new ArrayList<>();
        for (CustomKit kit : customKits.values()) {
            kitDataList.add(KitData.fromCustomKit(kit));
        }

        try {
            String json = GSON.toJson(kitDataList);
            Files.writeString(getKitsFile(), json, StandardCharsets.UTF_8);
            LOGGER.info("[KitPersistence] Saved {} custom kits", kitDataList.size());
            return true;
        } catch (IOException e) {
            LOGGER.error("[KitPersistence] Failed to save custom kits", e);
            return false;
        }
    }

    /**
     * Get all custom kits.
     */
    public static List<CustomKit> getAllCustomKits() {
        ensureLoaded();
        return new ArrayList<>(customKits.values());
    }

    /**
     * Get a custom kit by ID.
     */
    public static Optional<CustomKit> getKit(String id) {
        ensureLoaded();
        return Optional.ofNullable(customKits.get(id));
    }

    /**
     * Add or update a custom kit.
     */
    public static boolean saveKit(CustomKit kit) {
        ensureLoaded();
        customKits.put(kit.getId(), kit);
        return saveKits();
    }

    /**
     * Delete a custom kit by ID.
     */
    public static boolean deleteKit(String id) {
        ensureLoaded();
        if (customKits.remove(id) != null) {
            return saveKits();
        }
        return false;
    }

    /**
     * Check if a custom kit exists.
     */
    public static boolean hasKit(String id) {
        ensureLoaded();
        return customKits.containsKey(id);
    }

    /**
     * Get the count of custom kits.
     */
    public static int getKitCount() {
        ensureLoaded();
        return customKits.size();
    }

    /**
     * Ensure kits are loaded before access.
     */
    private static void ensureLoaded() {
        if (!loaded) {
            loadKits();
        }
    }

    /**
     * JSON-serializable kit data structure.
     */
    private static class KitData {
        String id;
        String name;
        String description;
        int color;
        List<ItemData> items;
        long createdAt;
        long lastModified;

        static KitData fromCustomKit(CustomKit kit) {
            KitData data = new KitData();
            data.id = kit.getId();
            data.name = kit.getName();
            data.description = kit.getDescription();
            data.color = kit.getColor();
            data.items = new ArrayList<>();
            for (CustomKit.KitItem item : kit.getKitItems()) {
                ItemData itemData = new ItemData();
                itemData.itemId = item.itemId();
                itemData.count = item.count();
                itemData.nbt = item.nbtTag();
                itemData.potionId = item.potionId();
                // Save enchantments
                if (item.enchantments() != null) {
                    itemData.enchantments = new ArrayList<>();
                    for (CustomKit.EnchantmentData ench : item.enchantments()) {
                        EnchantData ed = new EnchantData();
                        ed.id = ench.enchantmentId();
                        ed.level = ench.level();
                        itemData.enchantments.add(ed);
                    }
                }
                data.items.add(itemData);
            }
            data.createdAt = kit.getCreatedAt();
            data.lastModified = kit.getLastModified();
            return data;
        }

        CustomKit toCustomKit() {
            List<CustomKit.KitItem> kitItems = new ArrayList<>();
            if (items != null) {
                for (ItemData item : items) {
                    // Parse enchantments
                    List<CustomKit.EnchantmentData> enchants = new ArrayList<>();
                    if (item.enchantments != null) {
                        for (EnchantData ed : item.enchantments) {
                            if (ed.id != null) {
                                enchants.add(new CustomKit.EnchantmentData(ed.id, ed.level));
                            }
                        }
                    }
                    kitItems.add(new CustomKit.KitItem(
                        item.itemId != null ? item.itemId : "minecraft:air",
                        item.count,
                        item.nbt,
                        enchants,
                        item.potionId
                    ));
                }
            }
            return new CustomKit(
                id != null ? id : java.util.UUID.randomUUID().toString().substring(0, 8),
                name != null ? name : "Unnamed Kit",
                description,
                color,
                kitItems,
                createdAt > 0 ? createdAt : System.currentTimeMillis(),
                lastModified > 0 ? lastModified : System.currentTimeMillis()
            );
        }
    }

    /**
     * JSON-serializable item data structure.
     */
    private static class ItemData {
        String itemId;
        int count;
        String nbt;
        String potionId;
        List<EnchantData> enchantments;
    }

    /**
     * JSON-serializable enchantment data.
     */
    private static class EnchantData {
        String id;
        int level;
    }
}
