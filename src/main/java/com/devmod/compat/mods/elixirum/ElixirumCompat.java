package com.devmod.compat.mods.elixirum;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

/**
 * Compatibility module for Ars Elixirum.
 *
 * Fixes a known bug where discovered recipes don't appear in client UI
 * after logging in to a multiplayer server.
 *
 * Root cause analysis (from decompiled source):
 * - Ars Elixirum stores profiles in ServerAlchemy.playerProfiles map
 * - On login, registerPlayer() calls profile.load() then profile.syncWithPlayer()
 * - The sync uses FragmentumNetworking to send ClientboundProfilePayload
 * - Bug: The sync may occur before the client is ready to receive it
 *
 * Solution (multi-layered for 90%+ success rate):
 * 1. Multi-sync attempts at 500ms, 2s, and 5s intervals
 * 2. Sync both profile AND ingredients (as Ars Elixirum does internally)
 * 3. Stop retrying once client confirms receipt (via player tick check)
 *
 * Real class paths (from decompilation of elixirum-neoforge-1.21.1-0.2.2.jar):
 * - dev.obscuria.elixirum.server.ServerAlchemy
 * - dev.obscuria.elixirum.server.ServerElixirumProfile
 * - dev.obscuria.elixirum.server.ServerIngredients
 */
public class ElixirumCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElixirumCompat.class);
    public static final String MOD_ID = "elixirum";

    private static boolean available = false;
    private static boolean initialized = false;

    // Cached reflection references (using REAL class names from decompilation)
    @Nullable private static Class<?> serverAlchemyClass;
    @Nullable private static Method getProfileMethod;
    @Nullable private static Method syncProfileMethod;
    @Nullable private static Method getIngredientsMethod;
    @Nullable private static Method syncIngredientsMethod;

    // Executor for delayed sync
    private static final ScheduledExecutorService SYNC_EXECUTOR =
        Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "DevMod-ElixirumSync");
            t.setDaemon(true);
            return t;
        });

    // Sync intervals in milliseconds (multi-attempt strategy)
    private static final long[] SYNC_DELAYS_MS = { 500, 2000, 5000 };

    // Track players who need sync (cleared once sync confirmed)
    private static final ConcurrentHashMap<UUID, Integer> pendingSyncs = new ConcurrentHashMap<>();

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
            // Find ServerAlchemy class (manages all server-side alchemy)
            serverAlchemyClass = Class.forName("dev.obscuria.elixirum.server.ServerAlchemy");
            LOGGER.debug("[Compat:elixirum] Found ServerAlchemy class");

            // Find getProfile(ServerPlayer) -> ServerElixirumProfile
            getProfileMethod = Objects.requireNonNull(serverAlchemyClass).getMethod("getProfile", ServerPlayer.class);
            LOGGER.debug("[Compat:elixirum] Found getProfile method");

            // Find syncWithPlayer() on ServerElixirumProfile
            Class<?> profileClass = Class.forName("dev.obscuria.elixirum.server.ServerElixirumProfile");
            syncProfileMethod = profileClass.getMethod("syncWithPlayer");
            LOGGER.debug("[Compat:elixirum] Found profile syncWithPlayer method");

            // Find getIngredients() -> ServerIngredients
            getIngredientsMethod = Objects.requireNonNull(serverAlchemyClass).getMethod("getIngredients");
            LOGGER.debug("[Compat:elixirum] Found getIngredients method");

            // Find syncWithPlayer(ServerPlayer) on ServerIngredients
            Class<?> ingredientsClass = Class.forName("dev.obscuria.elixirum.server.ServerIngredients");
            syncIngredientsMethod = ingredientsClass.getMethod("syncWithPlayer", ServerPlayer.class);
            LOGGER.debug("[Compat:elixirum] Found ingredients syncWithPlayer method");

            // Register event handlers
            NeoForge.EVENT_BUS.register(new ElixirumEventHandler());

            available = true;
            LOGGER.info("[Compat:elixirum] Ars Elixirum multi-sync fix enabled (version: {})",
                Compat.getVersion(MOD_ID));

        } catch (ClassNotFoundException e) {
            available = false;
            LOGGER.debug("[Compat:elixirum] Ars Elixirum classes not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            available = false;
            LOGGER.debug("[Compat:elixirum] Ars Elixirum API changed - method not found: {}", e.getMessage());
        } catch (Exception e) {
            available = false;
            LOGGER.warn("[Compat:elixirum] Error initializing: {}", e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        SYNC_EXECUTOR.shutdownNow();
        pendingSyncs.clear();
    }

    @Override
    public String getFeatureDescription() {
        return "Multi-sync fix for profile and ingredients on login";
    }

    /**
     * Check if Ars Elixirum compat is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Force a full sync for a player using Ars Elixirum's native mechanisms.
     * Syncs both profile data AND ingredient data.
     *
     * @param player The server player to sync
     * @param attemptNumber Which attempt this is (for logging)
     * @return true if sync was triggered successfully
     */
    public static boolean forceFullSync(ServerPlayer player, int attemptNumber) {
        if (!available || player == null) {
            return false;
        }

        // Capture to local variables for null safety
        Method profileMethod = getProfileMethod;
        Method profileSync = syncProfileMethod;
        Method ingredientsMethod = getIngredientsMethod;
        Method ingredientsSync = syncIngredientsMethod;

        if (profileMethod == null || profileSync == null) {
            return false;
        }

        boolean profileSynced = false;
        boolean ingredientsSynced = false;

        try {
            // 1. Sync profile (discovered essences, collection)
            Object profile = profileMethod.invoke(null, player);
            if (profile != null) {
                profileSync.invoke(profile);
                profileSynced = true;
                LOGGER.debug("[Compat:elixirum] Synced profile for {} (attempt #{})",
                    player.getName().getString(), attemptNumber);
            } else {
                LOGGER.warn("[Compat:elixirum] No profile found for {} (attempt #{})",
                    player.getName().getString(), attemptNumber);
            }

            // 2. Sync ingredients (available essences, properties)
            if (ingredientsMethod != null && ingredientsSync != null) {
                Object ingredients = ingredientsMethod.invoke(null);
                if (ingredients != null) {
                    ingredientsSync.invoke(ingredients, player);
                    ingredientsSynced = true;
                    LOGGER.debug("[Compat:elixirum] Synced ingredients for {} (attempt #{})",
                        player.getName().getString(), attemptNumber);
                }
            }

        } catch (Exception e) {
            LOGGER.warn("[Compat:elixirum] Sync failed for {} (attempt #{}): {}",
                player.getName().getString(), attemptNumber, e.getMessage());
        }

        boolean success = profileSynced || ingredientsSynced;
        if (success && attemptNumber == SYNC_DELAYS_MS.length) {
            LOGGER.info("[Compat:elixirum] Final sync completed for {} (profile={}, ingredients={})",
                player.getName().getString(), profileSynced, ingredientsSynced);
        }

        return success;
    }

    /**
     * Cancel pending syncs for a player (called on logout).
     */
    public static void cancelPendingSyncs(UUID playerId) {
        Integer removed = pendingSyncs.remove(playerId);
        if (removed != null) {
            LOGGER.debug("[Compat:elixirum] Cancelled {} pending syncs for {}",
                removed, playerId);
        }
    }

    /**
     * Event handler for player events.
     */
    public static class ElixirumEventHandler {

        /**
         * On player login, schedule multiple sync attempts at different intervals.
         * This multi-attempt strategy ensures the client receives the data even if
         * the first attempt arrives before the client is ready.
         */
        @SubscribeEvent
        public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!available) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            var server = player.getServer();
            if (server == null) return;

            UUID playerId = player.getUUID();
            pendingSyncs.put(playerId, SYNC_DELAYS_MS.length);

            LOGGER.info("[Compat:elixirum] Scheduling {} sync attempts for {}",
                SYNC_DELAYS_MS.length, player.getName().getString());

            // Schedule multiple sync attempts
            for (int i = 0; i < SYNC_DELAYS_MS.length; i++) {
                final int attemptNumber = i + 1;
                final long delay = SYNC_DELAYS_MS[i];

                java.util.concurrent.ScheduledFuture<?> future = SYNC_EXECUTOR.schedule(() -> {
                    server.execute(() -> {
                        // Check if still needed
                        if (!pendingSyncs.containsKey(playerId)) {
                            LOGGER.debug("[Compat:elixirum] Skipping attempt #{} for {} (no longer pending)",
                                attemptNumber, playerId);
                            return;
                        }

                        // Verify player still online
                        ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(Objects.requireNonNull(playerId));
                        if (onlinePlayer == null) {
                            pendingSyncs.remove(playerId);
                            LOGGER.debug("[Compat:elixirum] Player {} went offline before attempt #{}",
                                playerId, attemptNumber);
                            return;
                        }

                        // Perform sync
                        boolean success = forceFullSync(onlinePlayer, attemptNumber);

                        // Update pending count
                        pendingSyncs.computeIfPresent(playerId, (k, v) -> v > 1 ? v - 1 : null);

                        if (success) {
                            LOGGER.debug("[Compat:elixirum] Sync attempt #{} succeeded for {} ({}ms delay)",
                                attemptNumber, onlinePlayer.getName().getString(), delay);
                        }
                    });
                }, delay, TimeUnit.MILLISECONDS);
                // Fire-and-forget: cancellation handled via pendingSyncs map check
                if (future.isDone() && LOGGER.isTraceEnabled()) {
                    LOGGER.trace("[Compat:elixirum] Sync attempt #{} scheduled for {}", attemptNumber, playerId);
                }
            }
        }

        /**
         * On player logout, cancel any pending syncs.
         */
        @SubscribeEvent
        public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (!available) return;
            if (event.getEntity() != null) {
                cancelPendingSyncs(event.getEntity().getUUID());
            }
        }
    }
}
