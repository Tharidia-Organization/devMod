package com.devmod.compat.mods.easynpc;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Compatibility module for Easy NPC.
 *
 * Easy NPC provides:
 * - Custom NPC creation with configurable appearance
 * - Dialog systems with multiple options
 * - Actions and behaviors (trading, commands, etc.)
 * - NPC presets and templates
 * - Pathing and navigation
 *
 * This integration allows DevMod to:
 * - Spawn NPCs for Arena scenarios (trainers, vendors, quest givers)
 * - Configure NPC dialogs for arena tutorials
 * - Create wave-based NPC enemies
 * - Use NPC presets from templates
 *
 * @see <a href="https://github.com/MarkusBordihn/Easy-NPC">Easy NPC GitHub</a>
 */
public class EasyNpcCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(EasyNpcCompat.class);
    public static final String MOD_ID = "easy_npc";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> easyNpcEntityClass;
    private static Class<?> easyNpcSpawnerClass;
    private static Class<?> npcPresetClass;
    private static Method spawnNpcMethod;
    private static Method setNpcPresetMethod;

    // Track spawned NPCs for cleanup
    private static final Map<String, UUID> spawnedNpcs = new HashMap<>();
    private static final String DEVMOD_TAG = "devmod_arena";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Easy NPC";
    }

    @Override
    public int priority() {
        // Medium priority - QoL/Arena support
        return 40;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:easy_npc] Easy NPC not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:easy_npc] Easy NPC detected");
        LOGGER.debug("[Compat:easy_npc] Version: {}", Compat.getVersion(MOD_ID));

        // Try to load API classes
        tryLoadApi();
    }

    /**
     * Attempt to load Easy NPC API classes via reflection.
     */
    private void tryLoadApi() {
        try {
            // Common package locations for Easy NPC
            String[] possiblePackages = {
                "de.markusbordihn.easynpc",
                "de.markusbordihn.easynpc.entity"
            };

            for (String pkg : possiblePackages) {
                try {
                    easyNpcEntityClass = Class.forName(pkg + ".EasyNPCEntity");
                    LOGGER.debug("[Compat:easy_npc] Found EasyNPCEntity at {}", pkg);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            // Try to find spawner/helper classes
            try {
                easyNpcSpawnerClass = Class.forName("de.markusbordihn.easynpc.spawner.NPCSpawner");
            } catch (ClassNotFoundException e) {
                LOGGER.debug("[Compat:easy_npc] NPCSpawner not found");
            }

            // Try to find preset class
            try {
                npcPresetClass = Class.forName("de.markusbordihn.easynpc.preset.NPCPreset");
            } catch (ClassNotFoundException e) {
                LOGGER.debug("[Compat:easy_npc] NPCPreset not found");
            }

            if (easyNpcEntityClass != null) {
                apiAvailable = true;
                LOGGER.info("[Compat:easy_npc] Easy NPC API available");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:easy_npc] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:easy_npc] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Arena NPC spawning, trainer NPCs, quest NPCs";
    }

    @Override
    public void shutdown() {
        // Clean up spawned NPCs
        removeAllSpawnedNpcs(null);
    }

    /**
     * Check if Easy NPC is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the Easy NPC API is accessible.
     */
    public static boolean isApiAvailable() {
        return apiAvailable;
    }

    /**
     * Check if an entity is an Easy NPC.
     *
     * @param entity The entity to check
     * @return true if it's an Easy NPC
     */
    public static boolean isEasyNpc(Entity entity) {
        if (!available || entity == null || easyNpcEntityClass == null) {
            return false;
        }
        return easyNpcEntityClass.isInstance(entity);
    }

    /**
     * Spawn a basic NPC at a position.
     * This is a simplified spawning method that uses Minecraft's entity system
     * when Easy NPC's direct API isn't available.
     *
     * @param level The server level
     * @param pos The position to spawn at
     * @param npcId Unique identifier for tracking
     * @param displayName The NPC's display name
     * @return The spawned entity's UUID, or null if failed
     */
    @Nullable
    public static UUID spawnNpc(ServerLevel level, BlockPos pos, String npcId, String displayName) {
        if (!available || level == null || pos == null) {
            return null;
        }

        try {
            // Try to find Easy NPC entity type
            ResourceLocation npcTypeRl = ResourceLocation.parse("easy_npc:humanoid");
            Optional<EntityType<?>> entityTypeOpt = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.ENTITY_TYPE)
                .getOptional(npcTypeRl);

            if (entityTypeOpt.isEmpty()) {
                LOGGER.debug("[Compat:easy_npc] Could not find NPC entity type");
                return null;
            }

            Entity npc = entityTypeOpt.get().create(level);
            if (npc == null) {
                return null;
            }

            // Set position
            npc.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            // Set custom name if provided
            if (displayName != null && !displayName.isEmpty()) {
                npc.setCustomName(net.minecraft.network.chat.Component.literal(displayName));
                npc.setCustomNameVisible(true);
            }

            // Add tag for tracking
            npc.addTag(DEVMOD_TAG);
            npc.addTag(DEVMOD_TAG + "_" + npcId);

            // Spawn the entity
            if (level.addFreshEntity(npc)) {
                UUID uuid = npc.getUUID();
                spawnedNpcs.put(npcId, uuid);
                LOGGER.debug("[Compat:easy_npc] Spawned NPC '{}' at {} with UUID {}",
                    displayName, pos, uuid);
                return uuid;
            }

        } catch (Exception e) {
            LOGGER.warn("[Compat:easy_npc] Failed to spawn NPC: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Spawn a trainer NPC for arena practice.
     */
    @Nullable
    public static UUID spawnTrainerNpc(ServerLevel level, BlockPos pos, String npcId) {
        return spawnNpc(level, pos, npcId, "Arena Trainer");
    }

    /**
     * Spawn a quest giver NPC.
     */
    @Nullable
    public static UUID spawnQuestNpc(ServerLevel level, BlockPos pos, String npcId, String questName) {
        return spawnNpc(level, pos, npcId, questName + " Quest");
    }

    /**
     * Spawn a vendor NPC.
     */
    @Nullable
    public static UUID spawnVendorNpc(ServerLevel level, BlockPos pos, String npcId, String vendorType) {
        return spawnNpc(level, pos, npcId, vendorType + " Vendor");
    }

    /**
     * Remove a specific spawned NPC.
     *
     * @param level The server level
     * @param npcId The NPC identifier
     * @return true if removed
     */
    public static boolean removeNpc(ServerLevel level, String npcId) {
        UUID uuid = spawnedNpcs.remove(npcId);
        if (uuid == null || level == null) {
            return false;
        }

        try {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                entity.discard();
                LOGGER.debug("[Compat:easy_npc] Removed NPC: {}", npcId);
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:easy_npc] Error removing NPC: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Remove all DevMod-spawned NPCs from a level.
     *
     * @param level The server level (null to just clear tracking)
     */
    public static void removeAllSpawnedNpcs(@Nullable ServerLevel level) {
        if (level != null) {
            for (UUID uuid : spawnedNpcs.values()) {
                try {
                    Entity entity = level.getEntity(uuid);
                    if (entity != null) {
                        entity.discard();
                    }
                } catch (Exception e) {
                    LOGGER.debug("[Compat:easy_npc] Error removing NPC: {}", e.getMessage());
                }
            }
        }

        int count = spawnedNpcs.size();
        spawnedNpcs.clear();
        LOGGER.debug("[Compat:easy_npc] Removed {} tracked NPCs", count);
    }

    /**
     * Get all NPCs tagged as DevMod arena NPCs in a level.
     *
     * @param level The server level
     * @return List of entities
     */
    public static List<Entity> getArenaTaggedNpcs(ServerLevel level) {
        if (level == null) {
            return Collections.emptyList();
        }

        List<Entity> result = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.getTags().contains(DEVMOD_TAG)) {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * Get the count of active spawned NPCs.
     */
    public static int getSpawnedNpcCount() {
        return spawnedNpcs.size();
    }

    /**
     * Check if a specific NPC exists.
     *
     * @param npcId The NPC identifier
     * @return true if tracked
     */
    public static boolean hasNpc(String npcId) {
        return spawnedNpcs.containsKey(npcId);
    }

    /**
     * Get the UUID of a spawned NPC.
     *
     * @param npcId The NPC identifier
     * @return UUID or null
     */
    @Nullable
    public static UUID getNpcUuid(String npcId) {
        return spawnedNpcs.get(npcId);
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Easy NPC: not available";
        }
        int count = getSpawnedNpcCount();
        return String.format("Easy NPC: %d spawned NPC%s",
            count, count == 1 ? "" : "s");
    }
}
