package com.devmod.config.handler;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.devmod.config.handler.export.ConfigExportHelper;
import com.devmod.config.handler.export.IExportableConfigHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import com.devmod.config.stats.IItemStats;

/**
 * Abstract base implementation for config handlers.
 * Provides common functionality for all stat types.
 *
 * @param <S> The stats type this handler manages
 */
public abstract class AbstractConfigHandler<S extends IItemStats>
        implements IExportableConfigHandler<S> {

    protected final Logger logger;
    protected final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Backup settings
    protected static final int MAX_BACKUPS = 5;
    protected static final String BACKUP_SUFFIX = ".backup";
    protected static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // Global stats storage
    protected final Map<Item, S> globalStats = new ConcurrentHashMap<>();

    // Configuration
    protected Path dataDirectory;
    protected final String jsonFileName;
    protected final String nbtKey;

    /**
     * Create a new handler.
     *
     * @param jsonFileName the JSON file name for persistence (e.g., "weapon-configs.json")
     * @param nbtKey the NBT key for CustomData storage (e.g., "WeaponModStats")
     */
    protected AbstractConfigHandler(String jsonFileName, String nbtKey) {
        this.logger = LoggerFactory.getLogger(getClass());
        this.jsonFileName = jsonFileName;
        this.nbtKey = nbtKey;
    }

    /**
     * Initialize the handler with the config directory.
     *
     * @param configDir the base config directory
     */
    public void initialize(Path configDir) {
        this.dataDirectory = configDir.resolve("devmod");
        try {
            Files.createDirectories(dataDirectory);
        } catch (Exception e) {
            logger.error("[{}] Failed to create data directory", getHandlerName(), e);
        }
        load();
    }

    /**
     * Get the handler name for logging.
     */
    protected String getHandlerName() {
        return getStatsClass().getSimpleName().replace("Stats", "Config");
    }

    // ═══════════════════════════════════════════════════════════════
    // ABSTRACT METHODS - Must be implemented by subclasses
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get the data component type for this stats type.
     * May return null if not using typed components.
     */
    @Nullable
    protected abstract DataComponentType<CompoundTag> getComponentType();

    /**
     * Create stats from NBT tag.
     */
    protected abstract S fromTag(CompoundTag tag);

    /**
     * Get the Gson type for Map<String, S> serialization.
     */
    protected abstract Type getMapType();

    // ═══════════════════════════════════════════════════════════════
    // ITEM OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public S getStats(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");

        // 1a. Check typed data component first
        DataComponentType<CompoundTag> componentType = getComponentType();
        if (componentType != null) {
            CompoundTag componentTag = stack.get(componentType);
            if (componentTag != null && !componentTag.isEmpty()) {
                return validateAndClamp(fromTag(componentTag.copy()));
            }
        }

        // 1b. Legacy CustomData path
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.contains(nbtKey)) {
            CompoundTag legacy = customData.copyTag().getCompound(nbtKey);
            S stats = validateAndClamp(fromTag(legacy));
            // Migrate to typed component
            migrateToComponent(stack, stats);
            return stats;
        }

        // 2. Check global stats
        S global = globalStats.get(stack.getItem());
        if (global != null) {
            return global.copy() instanceof IItemStats ? castStats(global.copy()) : global;
        }

        // 3. Return defaults
        return createDefault();
    }

    @SuppressWarnings("unchecked")
    private S castStats(IItemStats stats) {
        return (S) stats;
    }

    /**
     * Migrate stats from legacy NBT to typed component.
     */
    protected void migrateToComponent(ItemStack stack, S stats) {
        DataComponentType<CompoundTag> componentType = getComponentType();
        if (componentType != null) {
            try {
                CompoundTag tag = new CompoundTag();
                stats.save(tag);
                stack.set(componentType, tag.copy());
            } catch (Exception e) {
                logger.debug("[{}] Failed to migrate stats to component: {}", getHandlerName(), e.getMessage());
            }
        }
    }

    @Override
    public void setSpecificStats(ItemStack stack, S stats) {
        S clamped = validateAndClamp(stats);
        CompoundTag tag = new CompoundTag();
        clamped.save(tag);

        // Set typed component
        DataComponentType<CompoundTag> componentType = getComponentType();
        if (componentType != null) {
            stack.set(componentType, tag.copy());
        }

        // Also set CustomData for compatibility
        CustomData current = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag customTag = current.copyTag();
        customTag.put(nbtKey, tag.copy());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
    }

    @Override
    public void clearSpecificStats(ItemStack stack) {
        // Clear typed component
        DataComponentType<CompoundTag> componentType = getComponentType();
        if (componentType != null) {
            stack.remove(componentType);
        }

        // Clear from CustomData
        CustomData current = stack.get(DataComponents.CUSTOM_DATA);
        if (current != null && current.contains(nbtKey)) {
            CompoundTag customTag = current.copyTag();
            customTag.remove(nbtKey);
            if (customTag.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_DATA);
            } else {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
            }
        }
    }

    @Override
    @Nullable
    public S getGlobalStats(Item item) {
        return globalStats.get(item);
    }

    @Override
    public void setGlobalStats(Item item, S stats) {
        globalStats.put(item, validateAndClamp(stats));
        save();
    }

    @Override
    public void clearGlobalStats(Item item) {
        globalStats.remove(item);
        save();
    }

    @Override
    public Map<Item, S> getAllGlobalStats() {
        return Collections.unmodifiableMap(globalStats);
    }

    // ═══════════════════════════════════════════════════════════════
    // DECOMPOSE/RECOMPOSE - Default implementations
    // ═══════════════════════════════════════════════════════════════

    @Override
    public IDecomposedConfig decompose(S stats) {
        DecomposedConfig config = new DecomposedConfig(getComponents());
        populateDecomposed(config, stats);
        return config;
    }

    @Override
    public Optional<S> recompose(IDecomposedConfig decomposed) {
        S stats = createDefault();
        applyFromDecomposed(decomposed, stats);
        return Optional.of(validateAndClamp(stats));
    }

    /**
     * Populate a decomposed config from stats.
     * Subclasses should override to set component values.
     */
    protected abstract void populateDecomposed(DecomposedConfig config, S stats);

    /**
     * Apply decomposed values to stats.
     * Subclasses should override to read component values.
     */
    protected abstract void applyFromDecomposed(IDecomposedConfig config, S stats);

    // ═══════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void load() {
        if (dataDirectory == null) {
            logger.warn("[{}] Data directory not set, skipping load", getHandlerName());
            return;
        }

        Path jsonFile = dataDirectory.resolve(jsonFileName);
        if (!Files.exists(jsonFile)) {
            logger.info("[{}] No config file found at {}", getHandlerName(), jsonFile);
            return;
        }

        try (Reader reader = Files.newBufferedReader(jsonFile)) {
            Map<String, S> loaded = gson.fromJson(reader, getMapType());
            if (loaded == null) {
                logger.warn("[{}] Empty or invalid config file", getHandlerName());
                return;
            }

            globalStats.clear();
            int count = 0;
            for (Map.Entry<String, S> entry : loaded.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    globalStats.put(item, validateAndClamp(entry.getValue()));
                    count++;
                }
            }
            logger.info("[{}] Loaded {} global configs", getHandlerName(), count);
        } catch (IOException e) {
            logger.error("[{}] Failed to load configs", getHandlerName(), e);
        }
    }

    @Override
    public void save() {
        if (dataDirectory == null) {
            logger.warn("[{}] Data directory not set, skipping save", getHandlerName());
            return;
        }

        Path jsonFile = dataDirectory.resolve(jsonFileName);

        // Create backup if file exists
        if (Files.exists(jsonFile)) {
            createBackup(jsonFile);
        }

        // Build map with string keys
        Map<String, S> toSave = new java.util.HashMap<>();
        for (Map.Entry<Item, S> entry : globalStats.entrySet()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(entry.getKey());
            toSave.put(id.toString(), entry.getValue());
        }

        try (Writer writer = Files.newBufferedWriter(jsonFile)) {
            gson.toJson(toSave, writer);
            logger.debug("[{}] Saved {} global configs", getHandlerName(), toSave.size());
        } catch (IOException e) {
            logger.error("[{}] Failed to save configs", getHandlerName(), e);
        }
    }

    /**
     * Create a timestamped backup of the given file.
     */
    protected void createBackup(Path file) {
        try {
            String timestamp = ZonedDateTime.now(ZoneId.systemDefault()).format(BACKUP_DATE_FORMAT);
            Path backup = file.resolveSibling(file.getFileName() + "." + timestamp + BACKUP_SUFFIX);
            Files.copy(file, backup);

            // Clean old backups
            cleanOldBackups(file);
        } catch (IOException e) {
            logger.warn("[{}] Failed to create backup", getHandlerName(), e);
        }
    }

    /**
     * Remove old backup files, keeping only MAX_BACKUPS most recent.
     */
    protected void cleanOldBackups(Path file) {
        try {
            String prefix = file.getFileName().toString();
            java.util.List<Path> backups = Files.list(file.getParent())
                .filter(p -> p.getFileName().toString().startsWith(prefix) &&
                            p.getFileName().toString().endsWith(BACKUP_SUFFIX))
                .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                .toList();

            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
            }
        } catch (IOException e) {
            logger.debug("[{}] Failed to clean old backups", getHandlerName(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DEFAULT COMPONENTS IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Set<IConfigComponent<?>> getComponents() {
        // Subclasses should override to return their components
        return Collections.emptySet();
    }

    // ═══════════════════════════════════════════════════════════════
    // EXPORT (IExportableConfigHandler)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public String getCommandNamespace() {
        // Auto-derive from stats class: WeaponStats -> "weapons"
        String name = getStatsClass().getSimpleName();
        name = name.replace("Stats", "").toLowerCase(Locale.ROOT);
        return name + "s"; // pluralize
    }

    @Override
    public String exportToCommandString(Item item, S stats, boolean prettyPrint) {
        IDecomposedConfig decomposed = decompose(stats);
        return ConfigExportHelper.buildSetStatsCommand(
            getCommandNamespace(), item, decomposed, prettyPrint, true);
    }

    @Override
    public List<String> exportAllToCommandStrings(boolean prettyPrint) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<Item, S> entry : getAllGlobalStats().entrySet()) {
            result.add(exportToCommandString(entry.getKey(), entry.getValue(), prettyPrint));
        }
        return result;
    }
}
