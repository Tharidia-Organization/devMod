package com.devmod.endurance;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public final class KitSyncPersistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(KitSyncPersistence.class);
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private final Map<UUID, PlayerKitData> playerData = new ConcurrentHashMap<>();
    private Path dataDirectory;
    private boolean loaded = false;

    public void initialize(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        load();
    }

    public Optional<PlayerKitSnapshot> getPlayerSnapshot(UUID playerId) {
        ensureLoaded();
        if (playerId == null) {
            return Optional.empty();
        }
        PlayerKitData data = playerData.get(playerId);
        if (data == null) {
            return Optional.empty();
        }
        TemporaryKitSnapshot temporarySnapshot = null;
        if (data.temporaryKit != null && data.temporaryKit.items != null && !data.temporaryKit.items.isEmpty()) {
            List<CustomKit.KitItem> items = toKitItems(data.temporaryKit.items);
            String name = data.temporaryKit.name != null ? data.temporaryKit.name : "Temporary Kit";
            temporarySnapshot = new TemporaryKitSnapshot(name, items);
        }
        List<CustomKit> kits = new ArrayList<>();
        if (data.customKits != null) {
            for (CustomKitData kitData : data.customKits) {
                CustomKit kit = toCustomKit(kitData);
                if (kit != null) {
                    kits.add(kit);
                }
            }
        }
        return Optional.of(new PlayerKitSnapshot(temporarySnapshot, kits));
    }

    public void setTemporaryKit(UUID playerId, String name, List<CustomKit.KitItem> items) {
        if (playerId == null) {
            return;
        }
        ensureLoaded();
        if (items == null || items.isEmpty()) {
            clearTemporaryKit(playerId);
            return;
        }
        PlayerKitData data = playerData.computeIfAbsent(playerId, id -> new PlayerKitData());
        data.temporaryKit = TemporaryKitData.fromItems(name, items);
        save();
    }

    public void setCustomKit(UUID playerId, CustomKit kit) {
        if (playerId == null || kit == null) {
            return;
        }
        ensureLoaded();
        PlayerKitData data = playerData.computeIfAbsent(playerId, id -> new PlayerKitData());
        if (data.customKits == null) {
            data.customKits = new ArrayList<>();
        }
        String kitId = kit.getId();
        data.customKits.removeIf(existing -> kitId != null && kitId.equals(existing.id));
        data.customKits.add(CustomKitData.fromCustomKit(kit));
        save();
    }

    public void clearTemporaryKit(UUID playerId) {
        if (playerId == null) {
            return;
        }
        ensureLoaded();
        PlayerKitData data = playerData.get(playerId);
        if (data == null) {
            return;
        }
        data.temporaryKit = null;
        pruneIfEmpty(playerId, data);
        save();
    }

    private void pruneIfEmpty(UUID playerId, PlayerKitData data) {
        boolean hasTemporary = data.temporaryKit != null
            && data.temporaryKit.items != null
            && !data.temporaryKit.items.isEmpty();
        boolean hasCustom = data.customKits != null && !data.customKits.isEmpty();
        if (!hasTemporary && !hasCustom) {
            playerData.remove(playerId);
        }
    }

    private void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private Path getDataFile() {
        return dataDirectory.resolve("synced_kits.json");
    }

    private void load() {
        if (dataDirectory == null) {
            loaded = true;
            return;
        }

        playerData.clear();
        Path file = getDataFile();
        if (!Files.exists(file)) {
            loaded = true;
            return;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, PlayerKitData>>(){}.getType();
            Map<String, PlayerKitData> loadedData = GSON.fromJson(reader, type);
            if (loadedData != null) {
                loadedData.forEach((key, value) -> {
                    try {
                        UUID playerId = UUID.fromString(key);
                        if (value != null) {
                            playerData.put(playerId, value);
                        }
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("[EnduranceKit] Invalid UUID in synced kit file: {}", key);
                    }
                });
                LOGGER.info("[EnduranceKit] Loaded synced kits for {} players", playerData.size());
            }
        } catch (Exception e) {
            LOGGER.error("[EnduranceKit] Failed to load synced kits", e);
        } finally {
            loaded = true;
        }
    }

    private void save() {
        if (dataDirectory == null) {
            return;
        }
        Path file = getDataFile();
        Path tempFile = dataDirectory.resolve("synced_kits.json.tmp");
        Path backupFile = dataDirectory.resolve("synced_kits.json.bak");

        try {
            Files.createDirectories(dataDirectory);

            try (java.io.BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                Map<String, PlayerKitData> toSave = new HashMap<>();
                playerData.forEach((uuid, data) -> toSave.put(uuid.toString(), data));
                GSON.toJson(toSave, writer);
                writer.flush();
            }

            if (Files.exists(file)) {
                Files.copy(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(tempFile, file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                LOGGER.error("[EnduranceKit] Failed to save synced kits (fallback)", ex);
            }
        } catch (Exception e) {
            LOGGER.error("[EnduranceKit] Failed to save synced kits", e);
        }
    }

    private static List<CustomKit.KitItem> toKitItems(List<ItemData> items) {
        List<CustomKit.KitItem> kitItems = new ArrayList<>();
        if (items == null) {
            return kitItems;
        }
        for (ItemData item : items) {
            if (item == null || item.itemId == null) {
                continue;
            }
            List<CustomKit.EnchantmentData> enchantments = new ArrayList<>();
            if (item.enchantments != null) {
                for (EnchantData enchant : item.enchantments) {
                    if (enchant != null && enchant.id != null) {
                        enchantments.add(new CustomKit.EnchantmentData(enchant.id, enchant.level));
                    }
                }
            }
            kitItems.add(new CustomKit.KitItem(
                item.itemId,
                Math.max(1, item.count),
                item.nbt,
                enchantments,
                item.potionId
            ));
        }
        return kitItems;
    }

    private static CustomKit toCustomKit(@Nullable CustomKitData data) {
        if (data == null || data.id == null || data.name == null) {
            return null;
        }
        List<CustomKit.KitItem> items = toKitItems(data.items);
        return new CustomKit(
            data.id,
            data.name,
            data.description != null ? data.description : "",
            data.color,
            items,
            data.createdAt,
            data.lastModified
        );
    }

    public record PlayerKitSnapshot(@Nullable TemporaryKitSnapshot temporaryKit,
                                    List<CustomKit> customKits) {}

    public record TemporaryKitSnapshot(String name, List<CustomKit.KitItem> items) {}

    private static final class PlayerKitData {
        @Nullable
        private TemporaryKitData temporaryKit;
        private List<CustomKitData> customKits;
    }

    private static final class TemporaryKitData {
        private String name;
        private List<ItemData> items;

        private static TemporaryKitData fromItems(String name, List<CustomKit.KitItem> kitItems) {
            TemporaryKitData data = new TemporaryKitData();
            data.name = name != null ? name : "Temporary Kit";
            data.items = ItemData.fromKitItems(kitItems);
            return data;
        }
    }

    private static final class CustomKitData {
        private String id;
        private String name;
        private String description;
        private int color;
        private List<ItemData> items;
        private long createdAt;
        private long lastModified;

        private static CustomKitData fromCustomKit(CustomKit kit) {
            CustomKitData data = new CustomKitData();
            data.id = kit.getId();
            data.name = kit.getName();
            data.description = kit.getDescription();
            data.color = kit.getColor();
            data.items = ItemData.fromKitItems(kit.getKitItems());
            data.createdAt = kit.getCreatedAt();
            data.lastModified = kit.getLastModified();
            return data;
        }
    }

    private static final class ItemData {
        private String itemId;
        private int count;
        private String nbt;
        private String potionId;
        private List<EnchantData> enchantments;

        private static List<ItemData> fromKitItems(List<CustomKit.KitItem> kitItems) {
            List<ItemData> items = new ArrayList<>();
            if (kitItems == null) {
                return items;
            }
            for (CustomKit.KitItem kitItem : kitItems) {
                if (kitItem == null || kitItem.itemId() == null) {
                    continue;
                }
                ItemData item = new ItemData();
                item.itemId = kitItem.itemId();
                item.count = kitItem.count();
                item.nbt = kitItem.nbtTag();
                item.potionId = kitItem.potionId();
                if (kitItem.enchantments() != null) {
                    item.enchantments = new ArrayList<>();
                    for (CustomKit.EnchantmentData ench : kitItem.enchantments()) {
                        if (ench != null && ench.enchantmentId() != null) {
                            EnchantData ed = new EnchantData();
                            ed.id = ench.enchantmentId();
                            ed.level = ench.level();
                            item.enchantments.add(ed);
                        }
                    }
                }
                items.add(item);
            }
            return items;
        }
    }

    private static final class EnchantData {
        private String id;
        private int level;
    }
}
