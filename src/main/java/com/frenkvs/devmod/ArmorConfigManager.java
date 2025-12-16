package com.frenkvs.devmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.frenkvs.devmod.network.PacketSecurityService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.core.Holder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manages armor configuration storage and retrieval.
 * Follows the same pattern as WeaponConfigManager for consistency.
 *
 * Storage hierarchy:
 * 1. NBT Data (per-item instance) - highest priority
 * 2. Global Config (per-item type) - from JSON file
 * 3. Default Values - ArmorStats with all zeros
 */
public class ArmorConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArmorConfigManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Backup settings (same as WeaponConfigManager)
    private static final int MAX_BACKUPS = 5;
    private static final String BACKUP_SUFFIX = ".backup";
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // TOML file for per-world overrides
    private static final String TOML_FILE = "devmod-items.toml";

    // NBT key for armor mod stats
    private static final String NBT_KEY = "ArmorModStats";

    // Global stats map (per item type)
    private static final Map<Item, ArmorStats> globalArmorStats = new HashMap<>();

    private static Path dataDirectory = null;

    /**
     * Initialize with config directory path.
     * Should be called during mod initialization.
     */
    public static void initialize(Path configDir) {
        dataDirectory = configDir.resolve("devmod");
        try {
            Files.createDirectories(dataDirectory);
        } catch (Exception e) {
            LOGGER.error("[ArmorConfig] Failed to create data directory", e);
        }
        load();
        loadToml();
    }

    // ========== Stats Retrieval ==========

    /**
     * Gets the final statistics for a specific armor piece.
     * Priority: NBT -> Global -> Default
     */
    public static ArmorStats getStats(ItemStack stack) {
        stack = Objects.requireNonNull(stack);

        // 0. Prefer data component if present
        try {
            net.minecraft.nbt.CompoundTag componentTag = stack.get(Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get()));
            if (componentTag != null && !componentTag.isEmpty()) {
                return ArmorStats.load(componentTag.copy());
            }
        } catch (Exception ignored) {}

        // 1. Check if the armor has SPECIFIC modifications (NBT)
        CustomData customData = stack.get(Objects.requireNonNull(DataComponents.CUSTOM_DATA));
        if (customData != null && customData.contains(NBT_KEY)) {
            net.minecraft.nbt.CompoundTag statsTag = customData.copyTag().getCompound(NBT_KEY);
            ArmorStats loaded = clampStats(ArmorStats.load(statsTag));
            // Migrate legacy NBT into component for consistency
            try {
                net.minecraft.nbt.CompoundTag clampedTag = new net.minecraft.nbt.CompoundTag();
                loaded.save(clampedTag);
                stack.set(Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get()), clampedTag.copy());
                applyAttributeModifiers(stack, loaded);
            } catch (Exception ignored) {}
            return loaded;
        }

        // 2. If no specific modifications, use GLOBAL ones
        if (globalArmorStats.containsKey(stack.getItem())) {
            return globalArmorStats.get(stack.getItem());
        }

        // 3. Otherwise return default statistics (all zeros)
        return new ArmorStats();
    }

    /**
     * Get global stats for a specific item type.
     */
    public static ArmorStats getGlobalStats(Item item) {
        return globalArmorStats.getOrDefault(item, new ArmorStats());
    }

    /**
     * Check if item has global config.
     */
    public static boolean hasGlobalConfig(Item item) {
        return globalArmorStats.containsKey(item);
    }

    /**
     * Check if item has specific (NBT) config.
     */
    public static boolean hasSpecificConfig(ItemStack stack) {
        CustomData customData = stack.get(Objects.requireNonNull(DataComponents.CUSTOM_DATA));
        boolean hasCustom = customData != null && customData.contains(NBT_KEY);
        boolean hasComponent = false;
        try {
            hasComponent = stack.get(Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get())) != null;
        } catch (Exception ignored) {}
        return hasCustom || hasComponent;
    }

    // ========== Stats Modification ==========

    /**
     * Set global stats for an item type.
     * Automatically saves to disk.
     */
    public static void setGlobalStats(Item item, ArmorStats stats) {
        stats = clampStats(stats);
        globalArmorStats.put(item, stats);
        save(); // AUTO-SAVE on modification
    }

    /**
     * Set specific stats on an item instance (NBT).
     */
    public static void setSpecificStats(ItemStack stack, ArmorStats stats) {
        ArmorStats clamped = clampStats(stats);
        var customDataType = Objects.requireNonNull(DataComponents.CUSTOM_DATA);
        CustomData.update(customDataType, Objects.requireNonNull(stack), tag -> {
            net.minecraft.nbt.CompoundTag statsTag = new net.minecraft.nbt.CompoundTag();
            clamped.save(statsTag);
            tag.put(NBT_KEY, statsTag);
        });
        try {
            net.minecraft.nbt.CompoundTag statsTag = new net.minecraft.nbt.CompoundTag();
            clamped.save(statsTag);
            stack.set(Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get()), statsTag.copy());
        } catch (Exception ignored) {}
        applyAttributeModifiers(stack, clamped);
    }

    /**
     * Remove specific stats from an item (clears NBT).
     */
    public static void clearSpecificStats(ItemStack stack) {
        var customDataType = Objects.requireNonNull(DataComponents.CUSTOM_DATA);
        CustomData.update(customDataType, Objects.requireNonNull(stack), tag -> {
            tag.remove(NBT_KEY);
        });
        try {
            stack.remove(Objects.requireNonNull(ArmorComponents.ARMOR_STATS.get()));
        } catch (Exception ignored) {}
    }

    /**
     * Remove global stats for an item type.
     */
    public static void clearGlobalStats(Item item) {
        globalArmorStats.remove(item);
        save();
    }

    /**
     * Clear all global stats (used for testing).
     */
    public static void clearAllGlobalStats() {
        globalArmorStats.clear();
    }

    /**
     * Clamp stats with server-side security limits to prevent extreme values.
     */
    public static ArmorStats clampStats(ArmorStats stats) {
        try {
            PacketSecurityService security = PacketSecurityService.INSTANCE;
            stats.physicalReduction = (float) security.validateArmorReduction(stats.physicalReduction);
            stats.fireReduction = (float) security.validateArmorReduction(stats.fireReduction);
            stats.magicReduction = (float) security.validateArmorReduction(stats.magicReduction);
            stats.explosionReduction = (float) security.validateArmorReduction(stats.explosionReduction);
            stats.projectileReduction = (float) security.validateArmorReduction(stats.projectileReduction);
            stats.armorBonus = (float) security.validateArmorBonus(stats.armorBonus);
            stats.toughnessBonus = (float) security.validateToughnessBonus(stats.toughnessBonus);
            stats.knockbackResistance = (float) security.validateKnockbackResistance(stats.knockbackResistance);
            stats.thornsPercent = (float) security.validateThornsPercent(stats.thornsPercent);
            stats.shieldBlockStrength = (float) security.validateShieldBlock(stats.shieldBlockStrength);
            stats.shieldRecoverySpeed = (float) security.validateShieldRecovery(stats.shieldRecoverySpeed);
        } catch (Exception e) {
            LOGGER.debug("[ArmorConfig] Failed to clamp stats: {}", e.getMessage());
        }
        return stats;
    }

    private static void applyAttributeModifiers(ItemStack stack, ArmorStats stats) {
        try {
            ItemAttributeModifiers existing = stack.getOrDefault(
                Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS),
                Objects.requireNonNull(ItemAttributeModifiers.EMPTY)
            );
            java.util.List<ItemAttributeModifiers.Entry> entries = new java.util.ArrayList<>(existing.modifiers());
            EquipmentSlotGroup group = resolveGroup(stack);

            addModifier(entries, Attributes.ARMOR, stats.armorBonus, AttributeModifier.Operation.ADD_VALUE, "armor_bonus", group);
            addModifier(entries, Attributes.ARMOR_TOUGHNESS, stats.toughnessBonus, AttributeModifier.Operation.ADD_VALUE, "armor_toughness", group);
            addModifier(entries, Attributes.KNOCKBACK_RESISTANCE, stats.knockbackResistance, AttributeModifier.Operation.ADD_VALUE, "armor_kb_resist", group);

            stack.set(Objects.requireNonNull(DataComponents.ATTRIBUTE_MODIFIERS), new ItemAttributeModifiers(entries, existing.showInTooltip()));
        } catch (Exception e) {
            LOGGER.debug("[ArmorConfig] Failed to apply attribute modifiers: {}", e.getMessage());
        }
    }

    private static EquipmentSlotGroup resolveGroup(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return Objects.requireNonNull(EquipmentSlotGroup.bySlot(Objects.requireNonNull(armorItem.getEquipmentSlot())));
        }
        if (stack.getItem() instanceof ShieldItem) {
            return EquipmentSlotGroup.OFFHAND;
        }
        return EquipmentSlotGroup.ANY;
    }

    private static void addModifier(java.util.List<ItemAttributeModifiers.Entry> entries, Holder<Attribute> attribute,
                                    double value, AttributeModifier.Operation op, String keySuffix, EquipmentSlotGroup group) {
        if (attribute == null) return;
        ResourceLocation modId = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "armor_editor_" + keySuffix));
        removeDevModModifier(entries, attribute, modId);
        if (value == 0) {
            return;
        }
            AttributeModifier modifier = new AttributeModifier(modId, value, Objects.requireNonNull(op));
            entries.add(new ItemAttributeModifiers.Entry(Objects.requireNonNull(attribute), modifier, Objects.requireNonNull(group)));
    }

    private static void removeDevModModifier(java.util.List<ItemAttributeModifiers.Entry> entries, Holder<Attribute> attribute, ResourceLocation modId) {
        entries.removeIf(e -> {
            if (e.attribute() == null || attribute == null) return false;
            Attribute current = e.attribute().value();
            Attribute target = attribute.value();
            boolean sameAttr = current == target;
            boolean devNamespace = e.modifier().id() != null && DevMod.MODID.equals(e.modifier().id().getNamespace());
            boolean sameId = e.modifier().id() != null && e.modifier().id().equals(modId);
            return sameAttr && (devNamespace || sameId);
        });
    }

    // Holder conversion not needed (Neo 1.21 attributes are already Holder-based via Attributes.*)

    // ========== Armor Detection ==========

    /**
     * Check if an item is armor.
     */
    public static boolean isArmor(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var item = stack.getItem();
        return item instanceof ArmorItem || item instanceof net.minecraft.world.item.ShieldItem;
    }

    /**
     * Check if an item is armor by Item reference.
     */
    public static boolean isArmor(Item item) {
        return item instanceof ArmorItem || item instanceof ShieldItem;
    }

    /**
     * Get the equipment slot for an armor item.
     * Returns null if not armor.
     */
    public static EquipmentSlot getArmorSlot(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return armorItem.getEquipmentSlot();
        }
        return null;
    }

    // ========== Persistence ==========

    /**
     * Load armor configurations from file.
     */
    public static void load() {
        if (dataDirectory == null) return;

        Path configFile = dataDirectory.resolve("armor_configs.json");
        Path backupFile = dataDirectory.resolve("armor_configs.json.bak");

        Path fileToLoad = Files.exists(configFile) ? configFile :
                          (Files.exists(backupFile) ? backupFile : null);

        if (fileToLoad != null) {
            try (Reader reader = Files.newBufferedReader(fileToLoad, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, ArmorStats>>(){}.getType();
                Map<String, ArmorStats> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    globalArmorStats.clear();
                    loaded.forEach((key, stats) -> {
                        ResourceLocation resLoc = ResourceLocation.tryParse(Objects.requireNonNull(key));
                        if (resLoc != null && BuiltInRegistries.ITEM.containsKey(resLoc)) {
                            Item item = Objects.requireNonNull(BuiltInRegistries.ITEM.get(resLoc));
                            globalArmorStats.put(item, clampStats(stats));
                        } else {
                            LOGGER.warn("[ArmorConfig] Unknown item in config: {}", key);
                        }
                    });
                    LOGGER.info("[ArmorConfig] Loaded {} armor configurations from {}",
                            globalArmorStats.size(), fileToLoad.getFileName());
                }
            } catch (Exception e) {
                LOGGER.error("[ArmorConfig] Failed to load armor configs from {}", fileToLoad, e);
            }
        }
    }

    /**
     * Save armor configurations to file.
     */
    public static void save() {
        if (dataDirectory == null) return;

        // Create timestamped backup before saving
        createTimestampedBackup();

        Path configFile = dataDirectory.resolve("armor_configs.json");
        Path tempFile = dataDirectory.resolve("armor_configs.json.tmp");
        Path backupFile = dataDirectory.resolve("armor_configs.json.bak");

        try {
            Files.createDirectories(dataDirectory);

            // Write to temp file first (atomic write pattern)
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                Map<String, ArmorStats> toSave = new HashMap<>();
                globalArmorStats.forEach((item, stats) -> {
                    ResourceLocation key = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item));
                    toSave.put(key.toString(), stats);
                });
                GSON.toJson(toSave, writer);
                writer.flush();
            }

            // Create backup of existing file
            if (Files.exists(configFile)) {
                Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // Atomic move temp to final
            Files.move(tempFile, configFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            LOGGER.info("[ArmorConfig] Saved {} armor configurations", globalArmorStats.size());
            // Also persist TOML view for interoperability (per doc 06-persistence)
            saveToml();

        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                LOGGER.error("[ArmorConfig] Failed to save armor configs (fallback)", ex);
            }
        } catch (Exception e) {
            LOGGER.error("[ArmorConfig] Failed to save armor configs", e);
        }
    }

    // ========== BACKUP & RECOVERY SYSTEM ==========

    /**
     * Creates a timestamped backup of the current config file.
     */
    private static void createTimestampedBackup() {
        if (dataDirectory == null) return;

        Path configFile = dataDirectory.resolve("armor_configs.json");
        if (!Files.exists(configFile)) {
            return;
        }

        try {
            Path backupDir = dataDirectory.resolve("backups");
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT);
            String backupName = "armor_configs_" + timestamp + BACKUP_SUFFIX;
            Path backupFile = backupDir.resolve(backupName);

            Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.debug("[ArmorConfig] Created backup: {}", backupFile);

            cleanupOldBackups(backupDir);
        } catch (IOException e) {
            LOGGER.warn("[ArmorConfig] Failed to create backup", e);
        }
    }

    /**
     * Removes old backups keeping only MAX_BACKUPS most recent.
     */
    private static void cleanupOldBackups(Path backupDir) {
        try {
            var backups = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith("armor_configs_")
                          && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                .sorted((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .toList();

            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
                LOGGER.debug("[ArmorConfig] Deleted old backup: {}", backups.get(i));
            }
        } catch (IOException e) {
            LOGGER.warn("[ArmorConfig] Failed to cleanup old backups", e);
        }
    }

    /**
     * Restores config from the most recent backup.
     */
    public static boolean restoreFromBackup() {
        if (dataDirectory == null) return false;

        Path configFile = dataDirectory.resolve("armor_configs.json");
        Path backupDir = dataDirectory.resolve("backups");

        if (!Files.exists(backupDir)) {
            LOGGER.warn("[ArmorConfig] No backup directory found");
            return false;
        }

        try {
            var latestBackup = Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith("armor_configs_")
                          && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                .max((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                    } catch (IOException e) {
                        return 0;
                    }
                });

            if (latestBackup.isEmpty()) {
                LOGGER.warn("[ArmorConfig] No backup files found");
                return false;
            }

            Path backup = latestBackup.get();
            Files.copy(backup, configFile, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[ArmorConfig] Restored from backup: {}", backup);

            load();
            return true;
        } catch (IOException e) {
            LOGGER.error("[ArmorConfig] Failed to restore from backup", e);
            return false;
        }
    }

    /**
     * Lists available backup files.
     */
    public static Map<String, String> listBackups() {
        Map<String, String> backups = new HashMap<>();
        if (dataDirectory == null) return backups;

        Path backupDir = dataDirectory.resolve("backups");
        if (!Files.exists(backupDir)) return backups;

        try {
            Files.list(backupDir)
                .filter(p -> p.getFileName().toString().startsWith("armor_configs_")
                          && p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                .forEach(p -> {
                    try {
                        backups.put(p.getFileName().toString(), Files.getLastModifiedTime(p).toString());
                    } catch (IOException e) {
                        // Skip
                    }
                });
        } catch (IOException e) {
            LOGGER.warn("[ArmorConfig] Failed to list backups", e);
        }

        return backups;
    }

    /**
     * Get count of configured armor types.
     */
    public static int getConfiguredCount() {
        return globalArmorStats.size();
    }

    /**
     * Get an immutable view of all global armor overrides.
     */
    public static Map<Item, ArmorStats> getAllGlobalStats() {
        return Map.copyOf(globalArmorStats);
    }

    // ========== TOML STORAGE (per doc 06-persistence) ==========

    private static void loadToml() {
        if (dataDirectory == null) return;
        Path tomlPath = dataDirectory.resolve(TOML_FILE);
        if (!Files.exists(tomlPath)) {
            return;
        }

        try {
            Map<Item, ArmorStats> loaded = new HashMap<>();
            String currentType = null;
            Item currentItem = null;

            for (String rawLine : Files.readAllLines(tomlPath, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("[")) {
                    // Section header: [armor."minecraft:diamond_chestplate"]
                    currentItem = null;
                    currentType = null;
                    int typeEnd = line.indexOf(".");
                    int quoteStart = line.indexOf('"');
                    int quoteEnd = line.lastIndexOf('"');
                    if (typeEnd > 1 && quoteStart > typeEnd && quoteEnd > quoteStart) {
                        currentType = line.substring(1, typeEnd).trim(); // armor / weapon
                        String itemId = line.substring(quoteStart + 1, quoteEnd);
                        ResourceLocation id = ResourceLocation.tryParse(Objects.requireNonNull(itemId, "itemId"));
                        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                            currentItem = BuiltInRegistries.ITEM.get(id);
                            if (!"armor".equals(currentType)) {
                                currentItem = null; // skip non-armor in this manager
                            } else {
                                loaded.putIfAbsent(currentItem, new ArmorStats());
                            }
                        } else {
                            LOGGER.warn("[ArmorConfig] Unknown item in TOML: {}", itemId);
                        }
                    }
                    continue;
                }

                if (currentItem == null) continue;
                ArmorStats stats = loaded.getOrDefault(currentItem, new ArmorStats());

                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                // Strip quotes if present
                if (val.startsWith("\"") && val.endsWith("\"")) {
                    val = val.substring(1, val.length() - 1);
                }
                try {
                    switch (key) {
                        case "physicalReduction" -> stats.physicalReduction = parseClampedFloat(val);
                        case "fireReduction" -> stats.fireReduction = parseClampedFloat(val);
                        case "magicReduction" -> stats.magicReduction = parseClampedFloat(val);
                        case "explosionReduction" -> stats.explosionReduction = parseClampedFloat(val);
                        case "projectileReduction" -> stats.projectileReduction = parseClampedFloat(val);
                        case "armorBonus" -> stats.armorBonus = parseFloat(val);
                        case "toughnessBonus" -> stats.toughnessBonus = parseFloat(val);
                        case "knockbackResistance" -> stats.knockbackResistance = parseFloat(val);
                        case "thornsReflect" -> stats.thornsReflect = Boolean.parseBoolean(val);
                        case "thornsPercent" -> stats.thornsPercent = parseFloat(val);
                        case "shieldReflectProjectiles" -> stats.shieldReflectProjectiles = Boolean.parseBoolean(val);
                        case "shieldBlockStrength" -> stats.shieldBlockStrength = parseClampedFloat(val);
                        case "shieldRecoverySpeed" -> stats.shieldRecoverySpeed = parseClampedFloat2(val);
                        default -> {}
                    }
                    loaded.put(currentItem, stats);
                } catch (NumberFormatException ex) {
                    LOGGER.warn("[ArmorConfig] Invalid value '{}' for {} in {}", val, key, tomlPath.getFileName());
                }
            }

            if (!loaded.isEmpty()) {
                loaded.replaceAll((item, stats) -> clampStats(stats));
                globalArmorStats.clear();
                globalArmorStats.putAll(loaded);
                LOGGER.info("[ArmorConfig] Loaded {} armor entries from {}", loaded.size(), tomlPath.getFileName());
            }
        } catch (Exception e) {
            LOGGER.error("[ArmorConfig] Failed to load {}", tomlPath, e);
        }
    }

    private static void saveToml() {
        if (dataDirectory == null) return;
        Path tomlPath = dataDirectory.resolve(TOML_FILE);
        StringBuilder sb = new StringBuilder();
        sb.append("# DevMod per-world armor overrides\n");
        sb.append("# Generated automatically from ArmorConfigManager\n\n");

        globalArmorStats.forEach((item, stats) -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(item));
            String idString = Objects.requireNonNull(id, "item id cannot be null").toString();
            sb.append("[armor.\"").append(idString).append("\"]\n");
            sb.append("physicalReduction = ").append(stats.physicalReduction).append("\n");
            sb.append("fireReduction = ").append(stats.fireReduction).append("\n");
            sb.append("magicReduction = ").append(stats.magicReduction).append("\n");
            sb.append("explosionReduction = ").append(stats.explosionReduction).append("\n");
            sb.append("projectileReduction = ").append(stats.projectileReduction).append("\n");
            sb.append("armorBonus = ").append(stats.armorBonus).append("\n");
            sb.append("toughnessBonus = ").append(stats.toughnessBonus).append("\n");
            sb.append("knockbackResistance = ").append(stats.knockbackResistance).append("\n");
            sb.append("thornsReflect = ").append(stats.thornsReflect).append("\n");
            sb.append("thornsPercent = ").append(stats.thornsPercent).append("\n");
            sb.append("shieldReflectProjectiles = ").append(stats.shieldReflectProjectiles).append("\n");
            sb.append("shieldBlockStrength = ").append(stats.shieldBlockStrength).append("\n");
            sb.append("shieldRecoverySpeed = ").append(stats.shieldRecoverySpeed).append("\n\n");
        });

        try {
            Files.createDirectories(dataDirectory);
            Files.writeString(tomlPath, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[ArmorConfig] Failed to save {}", tomlPath, e);
        }
    }

    private static float parseFloat(String val) {
        return Float.parseFloat(val);
    }

    private static float parseClampedFloat(String val) {
        return Mth.clamp(Float.parseFloat(val), 0.0f, 1.0f);
    }

    private static float parseClampedFloat2(String val) {
        return Mth.clamp(Float.parseFloat(val), 0.0f, 2.0f);
    }
}
