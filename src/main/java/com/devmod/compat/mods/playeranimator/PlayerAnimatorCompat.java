package com.devmod.compat.mods.playeranimator;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.player.Player;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class PlayerAnimatorCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerAnimatorCompat.class);
    public static final String MOD_ID = "playeranimator";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> playerAnimationAccessClass;
    private static Class<?> animationStackClass;
    private static Method isPlayingMethod;
    private static Method getCurrentAnimationMethod;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Player Animation Library";
    }

    @Override
    public int priority() {
        // Medium priority - animation system
        return 35;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:playeranimator] Player Animation Library not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:playeranimator] Player Animation Library detected");
        LOGGER.debug("[Compat:playeranimator] Version: {}", Compat.getVersion(MOD_ID));

        // Try to load API classes
        tryLoadApi();
    }

    /**
     * Attempt to load Player Animation Library API classes via reflection.
     */
    private void tryLoadApi() {
        try {
            // Common package locations
            String[] possiblePackages = {
                "dev.kosmx.playerAnim.api",
                "dev.kosmx.playerAnim.api.layered",
                "dev.kosmx.playerAnim"
            };

            // Try to find IPlayerAnimation or AnimationStack
            for (String pkg : possiblePackages) {
                try {
                    animationStackClass = Class.forName(pkg + ".AnimationStack");
                    LOGGER.debug("[Compat:playeranimator] Found AnimationStack at {}", pkg);
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:playeranimator] AnimationStack not found in {}", pkg);
                }

                try {
                    playerAnimationAccessClass = Class.forName(pkg + ".IPlayerAnimationAccess");
                    LOGGER.debug("[Compat:playeranimator] Found IPlayerAnimationAccess at {}", pkg);
                    break;
                } catch (ClassNotFoundException e) {
                    LOGGER.trace("[Compat:playeranimator] IPlayerAnimationAccess not found in {}", pkg);
                }
            }

            if (animationStackClass != null || playerAnimationAccessClass != null) {
                apiAvailable = true;
                LOGGER.info("[Compat:playeranimator] Player Animation Library API available");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:playeranimator] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:playeranimator] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Animation state tracking, combat animation coordination, telemetry";
    }

    /**
     * Check if Player Animation Library is available.
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
     * Check if a player is currently playing a custom animation.
     *
     * @param player The player to check
     * @return true if a custom animation is playing
     */
    public static boolean isPlayingAnimation(Player player) {
        if (!apiAvailable || player == null) {
            return false;
        }

        try {
            // Try to access player's animation data
            Object animationData = getAnimationData(player);
            if (animationData == null) {
                return false;
            }

            // Check if any animation is active
            if (isPlayingMethod != null) {
                Object result = isPlayingMethod.invoke(animationData);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            }

            // Alternative: check if animation stack is not empty
            if (animationStackClass != null) {
                try {
                    Method getStackMethod = animationData.getClass().getMethod("getAnimationStack");
                    Object stack = getStackMethod.invoke(animationData);
                    if (stack != null) {
                        Method isActiveMethod = stack.getClass().getMethod("isActive");
                        Object active = isActiveMethod.invoke(stack);
                        if (active instanceof Boolean) {
                            return (Boolean) active;
                        }
                    }
                } catch (NoSuchMethodException e) {
                    LOGGER.trace("[Compat:playeranimator] getAnimationStack or isActive not available", e);
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:playeranimator] Error checking animation state: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Get the animation data for a player.
     *
     * @param player The player
     * @return The animation data object, or null
     */
    @Nullable
    private static Object getAnimationData(Player player) {
        if (player == null) {
            return null;
        }

        try {
            // The player usually implements IAnimatedPlayer
            Class<?> iAnimatedPlayer = Class.forName(
                "dev.kosmx.playerAnim.api.layered.IAnimatedPlayer");

            if (iAnimatedPlayer.isInstance(player)) {
                Method method = iAnimatedPlayer.getMethod("getAnimationStack");
                return method.invoke(player);
            }

            // Alternative: try reflection on player object
            for (Method m : player.getClass().getMethods()) {
                if (m.getName().contains("AnimationStack") || m.getName().contains("getAnimation")) {
                    if (m.getParameterCount() == 0) {
                        return m.invoke(player);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:playeranimator] Error getting animation data: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get the name/ID of the currently playing animation.
     *
     * @param player The player
     * @return Animation name, or null if none
     */
    @Nullable
    public static String getCurrentAnimationName(Player player) {
        if (!apiAvailable || player == null) {
            return null;
        }

        try {
            Object animationData = getAnimationData(player);
            if (animationData == null) {
                return null;
            }

            // Try to get current animation
            if (getCurrentAnimationMethod != null) {
                Object animation = getCurrentAnimationMethod.invoke(animationData);
                if (animation != null) {
                    // Try to get animation name
                    try {
                        Method getNameMethod = animation.getClass().getMethod("getName");
                        Object name = getNameMethod.invoke(animation);
                        if (name != null) {
                            return name.toString();
                        }
                    } catch (NoSuchMethodException e) {
                        LOGGER.trace("[Compat:playeranimator] getName not available on animation", e);
                    }

                    // Try toString as fallback
                    return animation.getClass().getSimpleName();
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:playeranimator] Error getting animation name: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get animation state info for telemetry.
     *
     * @param player The player
     * @return Map of animation state data
     */
    public static Map<String, Object> getAnimationState(Player player) {
        Map<String, Object> state = new LinkedHashMap<>();

        if (!available || player == null) {
            state.put("available", false);
            return state;
        }

        state.put("available", true);
        state.put("playing", isPlayingAnimation(player));

        String animName = getCurrentAnimationName(player);
        if (animName != null) {
            state.put("animation", animName);
        }

        return state;
    }

    /**
     * Check if the animation system supports a specific feature.
     *
     * @param featureName The feature to check
     * @return true if supported
     */
    public static boolean supportsFeature(String featureName) {
        if (!apiAvailable) {
            return false;
        }

        // Check for known features
        switch (featureName.toLowerCase(Locale.ROOT)) {
            case "layered":
                return animationStackClass != null;
            case "blending":
                // Check if blending is supported
                try {
                    Class.forName("dev.kosmx.playerAnim.api.layered.ModifierLayer");
                    return true;
                } catch (ClassNotFoundException e) {
                    return false;
                }
            case "keyframe":
                try {
                    Class.forName("dev.kosmx.playerAnim.core.data.KeyframeAnimation");
                    return true;
                } catch (ClassNotFoundException e) {
                    return false;
                }
            default:
                return false;
        }
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Player Animation Lib: not available";
        }
        if (!apiAvailable) {
            return "Player Animation Lib: detected (API not available)";
        }

        StringBuilder sb = new StringBuilder("Player Animation Lib: active");
        if (supportsFeature("layered")) {
            sb.append(" [layered]");
        }
        if (supportsFeature("blending")) {
            sb.append(" [blending]");
        }
        return sb.toString();
    }
}
