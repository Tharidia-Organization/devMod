package com.devmod.compat.mods.journeymap;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compatibility module for JourneyMap.
 *
 * JourneyMap provides:
 * - Minimap and fullscreen map
 * - Waypoints with custom icons and colors
 * - Map overlays (polygons, markers, lines)
 * - Dimension-aware mapping
 *
 * This integration allows DevMod to:
 * - Create waypoints for Arena locations
 * - Show arena boundaries on the map
 * - Mark spawn points and objectives
 * - Track player positions during arena runs
 *
 * @see <a href="https://journeymap.info/JourneyMap_API">JourneyMap API</a>
 */
public class JourneyMapCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(JourneyMapCompat.class);
    public static final String MOD_ID = "journeymap";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> clientApiClass;
    private static Class<?> waypointClass;
    private static Class<?> markerOverlayClass;
    private static Class<?> polygonOverlayClass;
    private static Object clientApiInstance;

    // Methods
    private static Method getClientApiMethod;
    private static Method addWaypointMethod;
    private static Method removeWaypointMethod;
    private static Method showWaypointMethod;

    // Waypoint builders
    private static Constructor<?> waypointConstructor;

    // Track created waypoints for cleanup
    private static final Map<String, Object> createdWaypoints = new HashMap<>();
    private static final String DEVMOD_PREFIX = "devmod_";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "JourneyMap";
    }

    @Override
    public int priority() {
        // Medium priority - UI mod
        return 30;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:journeymap] JourneyMap not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:journeymap] JourneyMap detected");
        LOGGER.debug("[Compat:journeymap] Version: {}", Compat.getVersion(MOD_ID));
    }

    @Override
    public void initClient() {
        if (!available) return;

        // JourneyMap API is client-side only
        tryLoadClientApi();

        if (apiAvailable) {
            LOGGER.info("[Compat:journeymap] JourneyMap API available for waypoint management");
        } else {
            LOGGER.debug("[Compat:journeymap] JourneyMap API not available, basic detection only");
        }
    }

    /**
     * Attempt to load JourneyMap's client API via reflection.
     */
    private void tryLoadClientApi() {
        try {
            // JourneyMap API package locations
            String apiPackage = "journeymap.client.api.v1";

            clientApiClass = Class.forName(apiPackage + ".ClientAPI");
            waypointClass = Class.forName(apiPackage + ".model.Waypoint");

            // Try to get the API instance
            Class<?> clientApiFactoryClass = Class.forName(apiPackage + ".ClientAPIFactory");
            getClientApiMethod = clientApiFactoryClass.getMethod("getClientAPI", String.class);

            // Get the API instance for DevMod
            try {
                clientApiInstance = getClientApiMethod.invoke(null, "devmod");
            } catch (Exception e) {
                LOGGER.debug("[Compat:journeymap] Could not get API instance: {}", e.getMessage());
            }

            if (clientApiInstance != null) {
                // Get waypoint methods
                addWaypointMethod = clientApiClass.getMethod("add", waypointClass);
                removeWaypointMethod = clientApiClass.getMethod("remove", waypointClass);
                showWaypointMethod = clientApiClass.getMethod("show", waypointClass);

                // Get waypoint constructor
                waypointConstructor = waypointClass.getConstructor(
                    String.class,  // mod id
                    String.class,  // name
                    ResourceKey.class, // dimension
                    BlockPos.class // position
                );

                apiAvailable = true;
            }

        } catch (ClassNotFoundException e) {
            LOGGER.debug("[Compat:journeymap] JourneyMap API classes not found: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.debug("[Compat:journeymap] Error loading JourneyMap API: {}", e.getMessage());
        }
    }

    @Override
    public String getFeatureDescription() {
        return "Arena waypoint creation, map markers for objectives";
    }

    @Override
    public void shutdown() {
        // Clean up all DevMod-created waypoints
        removeAllDevModWaypoints();
    }

    /**
     * Check if JourneyMap is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the JourneyMap API is accessible.
     */
    public static boolean isApiAvailable() {
        return apiAvailable && clientApiInstance != null;
    }

    /**
     * Create a waypoint for an arena location.
     *
     * @param id Unique identifier for this waypoint
     * @param name Display name
     * @param dimension The dimension key
     * @param pos The position
     * @param color RGB color (0xRRGGBB)
     * @return true if waypoint was created
     */
    public static boolean createArenaWaypoint(
            String id,
            String name,
            ResourceKey<Level> dimension,
            BlockPos pos,
            int color
    ) {
        if (!apiAvailable || waypointConstructor == null) {
            return false;
        }

        String waypointId = DEVMOD_PREFIX + id;

        try {
            // Create the waypoint
            Object waypoint = waypointConstructor.newInstance(
                "devmod",
                name,
                dimension,
                pos
            );

            // Set color if possible
            try {
                Method setColorMethod = waypointClass.getMethod("setColor", int.class);
                setColorMethod.invoke(waypoint, color);
            } catch (NoSuchMethodException ignored) {
                // Color method may not exist in all versions
            }

            // Enable the waypoint
            try {
                Method setEnabledMethod = waypointClass.getMethod("setEnabled", boolean.class);
                setEnabledMethod.invoke(waypoint, true);
            } catch (NoSuchMethodException ignored) {}

            // Add to JourneyMap
            addWaypointMethod.invoke(clientApiInstance, waypoint);

            // Track it for cleanup
            createdWaypoints.put(waypointId, waypoint);

            LOGGER.debug("[Compat:journeymap] Created waypoint: {} at {}", name, pos);
            return true;

        } catch (Exception e) {
            LOGGER.debug("[Compat:journeymap] Failed to create waypoint: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create a spawn point waypoint (green color).
     */
    public static boolean createSpawnWaypoint(
            String id,
            ResourceKey<Level> dimension,
            BlockPos pos
    ) {
        return createArenaWaypoint(id, "Arena Spawn", dimension, pos, 0x00FF00);
    }

    /**
     * Create an objective waypoint (gold color).
     */
    public static boolean createObjectiveWaypoint(
            String id,
            String name,
            ResourceKey<Level> dimension,
            BlockPos pos
    ) {
        return createArenaWaypoint(id, name, dimension, pos, 0xFFD700);
    }

    /**
     * Create an exit waypoint (blue color).
     */
    public static boolean createExitWaypoint(
            String id,
            ResourceKey<Level> dimension,
            BlockPos pos
    ) {
        return createArenaWaypoint(id, "Arena Exit", dimension, pos, 0x4169E1);
    }

    /**
     * Remove a specific DevMod waypoint.
     *
     * @param id The waypoint ID (without prefix)
     * @return true if removed
     */
    public static boolean removeWaypoint(String id) {
        if (!apiAvailable) {
            return false;
        }

        String waypointId = DEVMOD_PREFIX + id;
        Object waypoint = createdWaypoints.remove(waypointId);

        if (waypoint == null) {
            return false;
        }

        try {
            removeWaypointMethod.invoke(clientApiInstance, waypoint);
            LOGGER.debug("[Compat:journeymap] Removed waypoint: {}", id);
            return true;
        } catch (Exception e) {
            LOGGER.debug("[Compat:journeymap] Failed to remove waypoint: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Remove all DevMod-created waypoints.
     */
    public static void removeAllDevModWaypoints() {
        if (!apiAvailable) {
            createdWaypoints.clear();
            return;
        }

        for (Map.Entry<String, Object> entry : new HashMap<>(createdWaypoints).entrySet()) {
            try {
                removeWaypointMethod.invoke(clientApiInstance, entry.getValue());
            } catch (Exception e) {
                LOGGER.debug("[Compat:journeymap] Failed to remove waypoint {}: {}",
                    entry.getKey(), e.getMessage());
            }
        }

        createdWaypoints.clear();
        LOGGER.debug("[Compat:journeymap] Removed all DevMod waypoints");
    }

    /**
     * Get the count of active DevMod waypoints.
     */
    public static int getActiveWaypointCount() {
        return createdWaypoints.size();
    }

    /**
     * Check if a waypoint exists.
     *
     * @param id The waypoint ID (without prefix)
     * @return true if exists
     */
    public static boolean hasWaypoint(String id) {
        return createdWaypoints.containsKey(DEVMOD_PREFIX + id);
    }

    /**
     * Create waypoints for an arena (spawn + exit).
     *
     * @param arenaId Unique arena identifier
     * @param arenaName Display name
     * @param dimension The dimension
     * @param spawnPos Spawn position
     * @param exitPos Exit position (can be null)
     */
    public static void createArenaWaypoints(
            String arenaId,
            String arenaName,
            ResourceKey<Level> dimension,
            BlockPos spawnPos,
            @Nullable BlockPos exitPos
    ) {
        createSpawnWaypoint(arenaId + "_spawn", dimension, spawnPos);

        if (exitPos != null) {
            createExitWaypoint(arenaId + "_exit", dimension, exitPos);
        }

        LOGGER.info("[Compat:journeymap] Created waypoints for arena: {}", arenaName);
    }

    /**
     * Remove all waypoints for a specific arena.
     *
     * @param arenaId The arena identifier
     */
    public static void removeArenaWaypoints(String arenaId) {
        List<String> toRemove = new ArrayList<>();

        for (String key : createdWaypoints.keySet()) {
            if (key.startsWith(DEVMOD_PREFIX + arenaId)) {
                toRemove.add(key.substring(DEVMOD_PREFIX.length()));
            }
        }

        for (String id : toRemove) {
            removeWaypoint(id);
        }

        LOGGER.debug("[Compat:journeymap] Removed {} waypoints for arena: {}",
            toRemove.size(), arenaId);
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "JourneyMap: not available";
        }
        if (!apiAvailable) {
            return "JourneyMap: detected (API not available)";
        }
        int count = getActiveWaypointCount();
        return String.format("JourneyMap: %d active waypoint%s",
            count, count == 1 ? "" : "s");
    }
}
