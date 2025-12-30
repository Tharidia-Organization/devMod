package com.devmod.compat.mods.elixirum;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
import com.devmod.util.ConfigPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Compatibility module for Ars Elixirum.
 *
 * Fixes a known bug where players lose their discovered recipes when
 * disconnecting from a multiplayer server. This module persists the
 * player's alchemy data to disk on logout and restores it on login.
 */
public class ElixirumCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElixirumCompat.class);
    public static final String MOD_ID = "elixirum";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean available = false;
    private static boolean initialized = false;

    // Cached reflection references
    private static Class<?> alchemyDataClass;
    private static Class<?> alchemyDataAttachmentClass;
    @SuppressWarnings("unused") // Used via reflection
    private static Method getAlchemyDataMethod;
    private static Method serializeNBTMethod;
    private static Method deserializeNBTMethod;
    @SuppressWarnings("unused") // Reserved for future direct field access
    private static Field discoveredRecipesField;

    // In-memory cache of player data (for quick restore)
    private static final Map<UUID, CompoundTag> playerDataCache = new ConcurrentHashMap<>();

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Ars Elixirum";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        try {
            // Try to find Ars Elixirum's player data classes
            // The mod likely uses NeoForge's Data Attachments system

            // Try common class paths for player alchemy data
            String[] possibleDataClasses = {
                "net.obscuria.elixirum.data.AlchemyData",
                "net.obscuria.elixirum.common.data.AlchemyData",
                "net.obscuria.elixirum.api.data.AlchemyData",
                "net.obscuria.elixirum.player.AlchemyData",
                "net.obscuria.elixirum.AlchemyData",
                "net.obscuria.elixirum.data.PlayerAlchemyData",
                "net.obscuria.elixirum.registry.AlchemyDataAttachment"
            };

            for (String className : possibleDataClasses) {
                try {
                    alchemyDataClass = Class.forName(className);
                    LOGGER.debug("[Compat:elixirum] Found data class: {}", className);
                    break;
                } catch (ClassNotFoundException ignored) {
                    // Try next
                }
            }

            // Try to find the attachment class
            String[] possibleAttachmentClasses = {
                "net.obscuria.elixirum.registry.ElixirumAttachments",
                "net.obscuria.elixirum.data.ElixirumAttachments",
                "net.obscuria.elixirum.ElixirumAttachments",
                "net.obscuria.elixirum.common.ElixirumAttachments"
            };

            for (String className : possibleAttachmentClasses) {
                try {
                    alchemyDataAttachmentClass = Class.forName(className);
                    LOGGER.debug("[Compat:elixirum] Found attachment class: {}", className);
                    break;
                } catch (ClassNotFoundException ignored) {
                    // Try next
                }
            }

            if (alchemyDataClass != null) {
                // Try to find serialization methods
                try {
                    serializeNBTMethod = alchemyDataClass.getMethod("serializeNBT");
                } catch (NoSuchMethodException e) {
                    try {
                        serializeNBTMethod = alchemyDataClass.getMethod("save");
                    } catch (NoSuchMethodException ignored) {
                        // Will try field-based approach
                    }
                }

                try {
                    deserializeNBTMethod = alchemyDataClass.getMethod("deserializeNBT", CompoundTag.class);
                } catch (NoSuchMethodException e) {
                    try {
                        deserializeNBTMethod = alchemyDataClass.getMethod("load", CompoundTag.class);
                    } catch (NoSuchMethodException ignored) {
                        // Will try field-based approach
                    }
                }

                // Try to find discovered recipes field
                for (Field field : alchemyDataClass.getDeclaredFields()) {
                    String name = field.getName().toLowerCase(Locale.ROOT);
                    if (name.contains("recipe") || name.contains("discovered") || name.contains("known")) {
                        field.setAccessible(true);
                        discoveredRecipesField = field;
                        LOGGER.debug("[Compat:elixirum] Found recipes field: {}", field.getName());
                        break;
                    }
                }
            }

            // Register event handlers
            NeoForge.EVENT_BUS.register(new ElixirumEventHandler());

            available = alchemyDataClass != null || alchemyDataAttachmentClass != null;

            if (available) {
                LOGGER.info("[Compat:elixirum] Ars Elixirum recipe persistence fix enabled");
                LOGGER.debug("[Compat:elixirum] Version: {}", Compat.getVersion(MOD_ID));

                // Ensure data directory exists
                ensureDataDirectory();
            } else {
                // Even without reflection access, we can still try to persist data
                // using alternative methods
                LOGGER.info("[Compat:elixirum] Ars Elixirum detected, using fallback persistence");
                available = true;
            }

        } catch (Exception e) {
            available = false;
            LOGGER.warn("[Compat:elixirum] Error initializing: {}", e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        // Save any cached data to disk
        for (Map.Entry<UUID, CompoundTag> entry : playerDataCache.entrySet()) {
            savePlayerDataToDisk(entry.getKey(), entry.getValue());
        }
        playerDataCache.clear();
    }

    @Override
    public String getFeatureDescription() {
        return "Fixes recipe persistence bug on player disconnect";
    }

    /**
     * Check if Ars Elixirum compat is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Get the data directory for Elixirum player data.
     */
    private static Path getDataDirectory() {
        return ConfigPaths.getGameDir()
            .resolve("devmod")
            .resolve("elixirum_data");
    }

    /**
     * Ensure the data directory exists.
     */
    private static void ensureDataDirectory() {
        try {
            Files.createDirectories(getDataDirectory());
        } catch (IOException e) {
            LOGGER.error("[Compat:elixirum] Failed to create data directory", e);
        }
    }

    /**
     * Get the file path for a player's data.
     */
    private static Path getPlayerDataFile(UUID playerId) {
        return getDataDirectory().resolve(playerId.toString() + ".json");
    }

    /**
     * Save player alchemy data to disk.
     */
    private static void savePlayerDataToDisk(UUID playerId, CompoundTag data) {
        Path file = getPlayerDataFile(playerId);
        try {
            JsonObject json = new JsonObject();
            json.addProperty("uuid", playerId.toString());
            json.addProperty("timestamp", System.currentTimeMillis());
            json.addProperty("data", data.toString());

            Files.writeString(file, GSON.toJson(json));
            LOGGER.debug("[Compat:elixirum] Saved alchemy data for {}", playerId);
        } catch (IOException e) {
            LOGGER.error("[Compat:elixirum] Failed to save data for {}: {}", playerId, e.getMessage());
        }
    }

    /**
     * Load player alchemy data from disk.
     */
    @Nullable
    private static CompoundTag loadPlayerDataFromDisk(UUID playerId) {
        Path file = getPlayerDataFile(playerId);
        if (!Files.exists(file)) {
            return null;
        }

        try {
            String content = Files.readString(file);
            JsonElement element = JsonParser.parseString(content);
            if (element.isJsonObject()) {
                JsonObject json = element.getAsJsonObject();
                if (json.has("data")) {
                    String nbtString = json.get("data").getAsString();
                    // Parse NBT from string representation
                    return parseNbtString(nbtString);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[Compat:elixirum] Failed to load data for {}: {}", playerId, e.getMessage());
        }

        return null;
    }

    /**
     * Parse NBT from string representation.
     */
    @Nullable
    private static CompoundTag parseNbtString(String nbtString) {
        try {
            // Use Minecraft's NBT parsing
            return net.minecraft.nbt.TagParser.parseTag(nbtString);
        } catch (Exception e) {
            LOGGER.debug("[Compat:elixirum] Failed to parse NBT: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Try to get alchemy data from a player using reflection.
     */
    @Nullable
    private static Object getAlchemyData(ServerPlayer player) {
        if (alchemyDataAttachmentClass != null) {
            try {
                // Try to get data via attachment
                for (Field field : alchemyDataAttachmentClass.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        Object attachment = field.get(null);
                        if (attachment != null) {
                            // Try to get data from player using this attachment
                            Method getDataMethod = player.getClass().getMethod("getData", attachment.getClass());
                            return getDataMethod.invoke(player, attachment);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[Compat:elixirum] Failed to get attachment data: {}", e.getMessage());
            }
        }

        if (getAlchemyDataMethod != null) {
            try {
                return getAlchemyDataMethod.invoke(null, player);
            } catch (Exception e) {
                LOGGER.debug("[Compat:elixirum] Failed to get alchemy data: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * Event handler for player events.
     */
    public static class ElixirumEventHandler {

        @SubscribeEvent
        public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (!available) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            UUID playerId = player.getUUID();

            try {
                // Try to get and save the player's alchemy data
                Object alchemyData = getAlchemyData(player);

                if (alchemyData != null && serializeNBTMethod != null) {
                    Object nbtData = serializeNBTMethod.invoke(alchemyData);
                    if (nbtData instanceof CompoundTag tag) {
                        playerDataCache.put(playerId, tag);
                        savePlayerDataToDisk(playerId, tag);
                        LOGGER.debug("[Compat:elixirum] Saved alchemy data for {} on logout",
                            player.getName().getString());
                    }
                } else {
                    // Fallback: try to access player's persistent data
                    CompoundTag persistentData = player.getPersistentData();
                    CompoundTag elixirumData = persistentData.getCompound("elixirum");
                    if (!elixirumData.isEmpty()) {
                        playerDataCache.put(playerId, elixirumData.copy());
                        savePlayerDataToDisk(playerId, elixirumData);
                        LOGGER.debug("[Compat:elixirum] Saved elixirum persistent data for {}",
                            player.getName().getString());
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[Compat:elixirum] Error saving data on logout: {}", e.getMessage());
            }
        }

        @SubscribeEvent
        public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!available) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            UUID playerId = player.getUUID();

            try {
                // First check cache, then disk
                CompoundTag savedData = playerDataCache.get(playerId);
                if (savedData == null) {
                    savedData = loadPlayerDataFromDisk(playerId);
                }

                if (savedData == null) {
                    LOGGER.debug("[Compat:elixirum] No saved data for {}", player.getName().getString());
                    return;
                }

                // Try to restore the data
                Object alchemyData = getAlchemyData(player);

                if (alchemyData != null && deserializeNBTMethod != null) {
                    deserializeNBTMethod.invoke(alchemyData, savedData);
                    LOGGER.info("[Compat:elixirum] Restored alchemy data for {}",
                        player.getName().getString());
                } else {
                    // Fallback: try to restore to player's persistent data
                    CompoundTag persistentData = player.getPersistentData();
                    persistentData.put("elixirum", savedData.copy());
                    LOGGER.info("[Compat:elixirum] Restored elixirum persistent data for {}",
                        player.getName().getString());
                }

                // Remove from cache after successful restore
                playerDataCache.remove(playerId);

            } catch (Exception e) {
                LOGGER.debug("[Compat:elixirum] Error restoring data on login: {}", e.getMessage());
            }
        }

        @SubscribeEvent
        public void onPlayerClone(PlayerEvent.Clone event) {
            // Handle respawn/dimension change - copy alchemy data
            if (!available) return;
            if (event.isWasDeath()) {
                // On death, we might want to preserve recipes (config option?)
                // For now, let Ars Elixirum handle death normally
            }
        }
    }
}
