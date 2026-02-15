package com.devmod.compat.mods.emotecraft;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.player.Player;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class EmotecraftCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmotecraftCompat.class);
    public static final String MOD_ID = "emotecraft";

    private static volatile boolean available = false;
    private static volatile boolean initialized = false;
    private static volatile boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> emotePlayImplClass;
    private static Class<?> emoteDataClass;
    private static Class<?> clientEmoteAPIClass;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Emotecraft";
    }

    @Override
    public int priority() {
        // Lower priority - cosmetic
        return 40;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:emotecraft] Emotecraft not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:emotecraft] Emotecraft detected");
        LOGGER.debug("[Compat:emotecraft] Version: {}", Compat.getVersion(MOD_ID));

        loadApi();
    }

    /**
     * Load Emotecraft API classes via reflection.
     */
    private void loadApi() {
        try {
            // Emotecraft package structure
            String[] packages = {
                "io.github.kosmx.emotes.api.proxy",
                "io.github.kosmx.emotes.main",
                "io.github.kosmx.emotes"
            };

            for (String pkg : packages) {
                try {
                    emotePlayImplClass = Class.forName(pkg + ".EmotePlayImpl");
                    LOGGER.debug("[Compat:emotecraft] Found EmotePlayImpl at {}", pkg);
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:emotecraft] EmotePlayImpl not found in {}", pkg);
                }
            }

            // Try to find client API
            try {
                clientEmoteAPIClass = Class.forName(
                    "io.github.kosmx.emotes.api.proxy.IEmoteClientProxyAPI");
            } catch (ClassNotFoundException e) {
                LOGGER.trace("[Compat:emotecraft] IEmoteClientProxyAPI not found", e);
                try {
                    clientEmoteAPIClass = Class.forName(
                        "io.github.kosmx.emotes.main.EmoteHolder");
                } catch (ClassNotFoundException e2) {
                    LOGGER.trace("[Compat:emotecraft] EmoteHolder not found", e2);
                }
            }

            // Try to find emote data class
            try {
                emoteDataClass = Class.forName(
                    "io.github.kosmx.emotes.api.EmoteData");
            } catch (ClassNotFoundException e) {
                LOGGER.trace("[Compat:emotecraft] EmoteData not found in api package", e);
                try {
                    emoteDataClass = Class.forName(
                        "io.github.kosmx.emotes.common.emote.EmoteData");
                } catch (ClassNotFoundException e2) {
                    LOGGER.trace("[Compat:emotecraft] EmoteData not found in common package", e2);
                }
            }
            if (emoteDataClass != null) {
                LOGGER.debug("[Compat:emotecraft] EmoteData class: {}", emoteDataClass.getName());
            }

            if (emotePlayImplClass != null || clientEmoteAPIClass != null) {
                apiAvailable = true;
                LOGGER.info("[Compat:emotecraft] Emotecraft API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:emotecraft] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:emotecraft] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Emote detection, animation tracking, emote triggers";
    }

    /**
     * Check if Emotecraft is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the API is accessible.
     */
    public static boolean isApiAvailable() {
        return apiAvailable;
    }

    /**
     * Check if a player is currently playing an emote.
     *
     * @param player The player to check
     * @return true if player is emoting
     */
    public static boolean isEmoting(Player player) {
        if (!apiAvailable || player == null) {
            return false;
        }

        try {
            // Try using the emote capability/data attached to player
            // Emotecraft typically uses capabilities or mixins
            Method getEmoteTrackerMethod = findGetEmoteTrackerMethod(player);
            if (getEmoteTrackerMethod != null) {
                Object tracker = getEmoteTrackerMethod.invoke(player);
                if (tracker != null) {
                    Method isPlayingMethod = tracker.getClass().getMethod("isPlayingEmote");
                    return (boolean) isPlayingMethod.invoke(tracker);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:emotecraft] Error checking emote state: {}", e.getMessage());
        }

        return false;
    }

    @Nullable
    private static Method findGetEmoteTrackerMethod(Player player) {
        // Try common method names
        String[] methodNames = {
            "getEmoteTracker",
            "emotecraft$getEmoteTracker",
            "getEmoteCapability"
        };

        for (String name : methodNames) {
            try {
                return player.getClass().getMethod(name);
            } catch (NoSuchMethodException e) {
                LOGGER.trace("[Compat:emotecraft] Emote tracker method not found: {}", name, e);
            }
        }

        return null;
    }

    /**
     * Get the current emote name for a player.
     *
     * @param player The player
     * @return Emote name, or null if not emoting
     */
    @Nullable
    public static String getCurrentEmoteName(Player player) {
        if (!isEmoting(player)) {
            return null;
        }

        try {
            Method getEmoteTrackerMethod = findGetEmoteTrackerMethod(player);
            if (getEmoteTrackerMethod != null) {
                Object tracker = getEmoteTrackerMethod.invoke(player);
                if (tracker != null) {
                    // Try to get current emote
                    Method getCurrentMethod = tracker.getClass().getMethod("getCurrentEmote");
                    Object emote = getCurrentMethod.invoke(tracker);

                    if (emote != null) {
                        // Try to get name
                        try {
                            Method getNameMethod = emote.getClass().getMethod("getName");
                            Object name = getNameMethod.invoke(emote);
                            if (name != null) return name.toString();
                        } catch (NoSuchMethodException e) {
                            // Try title
                            try {
                                Method getTitleMethod = emote.getClass().getMethod("getTitle");
                                Object title = getTitleMethod.invoke(emote);
                                if (title != null) return title.toString();
                            } catch (NoSuchMethodException e2) {
                                LOGGER.trace("[Compat:emotecraft] Emote title method not available", e2);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:emotecraft] Error getting emote name: {}", e.getMessage());
        }

        return "Unknown Emote";
    }

    /**
     * Get emote state info for telemetry.
     *
     * @param player The player
     * @return Emote state map
     */
    public static Map<String, Object> getEmoteState(Player player) {
        Map<String, Object> state = new LinkedHashMap<>();

        if (!apiAvailable || player == null) {
            return state;
        }

        boolean emoting = isEmoting(player);
        state.put("isEmoting", emoting);

        if (emoting) {
            String emoteName = getCurrentEmoteName(player);
            if (emoteName != null) {
                state.put("emoteName", emoteName);
            }

            // Try to get additional info
            try {
                Method getEmoteTrackerMethod = findGetEmoteTrackerMethod(player);
                if (getEmoteTrackerMethod != null) {
                    Object tracker = getEmoteTrackerMethod.invoke(player);
                    if (tracker != null) {
                        // Try to get tick/progress
                        try {
                            Method getTickMethod = tracker.getClass().getMethod("getTick");
                            Object tick = getTickMethod.invoke(tracker);
                            if (tick instanceof Number) {
                                state.put("emoteTick", ((Number) tick).intValue());
                            }
                        } catch (NoSuchMethodException e) {
                            LOGGER.trace("[Compat:emotecraft] getTick not available", e);
                        }

                        // Try to check if looping
                        try {
                            Method isLoopingMethod = tracker.getClass().getMethod("isLooping");
                            state.put("isLooping", isLoopingMethod.invoke(tracker));
                        } catch (NoSuchMethodException e) {
                            LOGGER.trace("[Compat:emotecraft] isLooping not available", e);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[Compat:emotecraft] Error getting emote details: {}", e.getMessage());
            }
        }

        return state;
    }

    /**
     * Get the list of available emote IDs.
     * Note: This may only work on client side.
     *
     * @return Set of emote IDs
     */
    public static Set<String> getAvailableEmotes() {
        Set<String> emotes = new HashSet<>();

        if (!apiAvailable) {
            return emotes;
        }

        try {
            // Try to get emote list from holder/registry
            if (clientEmoteAPIClass != null) {
                Method getEmotesMethod = clientEmoteAPIClass.getMethod("getEmotes");
                Object emotesObj = getEmotesMethod.invoke(null);

                if (emotesObj instanceof Collection<?> collection) {
                    for (Object emote : collection) {
                        try {
                            Method getIdMethod = emote.getClass().getMethod("getId");
                            Object id = getIdMethod.invoke(emote);
                            if (id != null) {
                                emotes.add(id.toString());
                            }
                        } catch (Exception e) {
                            LOGGER.trace("[Compat:emotecraft] Failed to read emote id", e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:emotecraft] Error getting emote list: {}", e.getMessage());
        }

        return emotes;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Emotecraft: not available";
        }
        if (!apiAvailable) {
            return "Emotecraft: detected (API not loaded)";
        }
        return "Emotecraft: API available";
    }
}
