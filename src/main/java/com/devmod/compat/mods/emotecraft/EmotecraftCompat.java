package com.devmod.compat.mods.emotecraft;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Compatibility module for Emotecraft.
 *
 * Emotecraft provides:
 * - Player emote animations
 * - Custom emote support
 * - Emote wheel UI
 * - Animation playback system
 *
 * This integration allows DevMod to:
 * - Detect when player is emoting
 * - Get current emote name for HUD
 * - Track emote usage for telemetry
 * - Potentially trigger emotes from Arena events
 *
 * @see <a href="https://github.com/KosmX/emotes">Emotecraft GitHub</a>
 */
public class EmotecraftCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmotecraftCompat.class);
    public static final String MOD_ID = "emotecraft";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> emotePlayImplClass;
    private static Class<?> emoteDataClass;
    private static Class<?> clientEmoteAPIClass;
    private static Method isPlayingEmoteMethod;
    private static Method getCurrentEmoteMethod;
    private static Method getEmoteNameMethod;

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
                } catch (ClassNotFoundException ignored) {}
            }

            // Try to find client API
            try {
                clientEmoteAPIClass = Class.forName(
                    "io.github.kosmx.emotes.api.proxy.IEmoteClientProxyAPI");
            } catch (ClassNotFoundException ignored) {
                try {
                    clientEmoteAPIClass = Class.forName(
                        "io.github.kosmx.emotes.main.EmoteHolder");
                } catch (ClassNotFoundException ignored2) {}
            }

            // Try to find emote data class
            try {
                emoteDataClass = Class.forName(
                    "io.github.kosmx.emotes.api.EmoteData");
            } catch (ClassNotFoundException ignored) {
                try {
                    emoteDataClass = Class.forName(
                        "io.github.kosmx.emotes.common.emote.EmoteData");
                } catch (ClassNotFoundException ignored2) {}
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
            } catch (NoSuchMethodException ignored) {}
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
                            } catch (NoSuchMethodException ignored) {}
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
                        } catch (NoSuchMethodException ignored) {}

                        // Try to check if looping
                        try {
                            Method isLoopingMethod = tracker.getClass().getMethod("isLooping");
                            state.put("isLooping", isLoopingMethod.invoke(tracker));
                        } catch (NoSuchMethodException ignored) {}
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
                        } catch (Exception ignored) {}
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
