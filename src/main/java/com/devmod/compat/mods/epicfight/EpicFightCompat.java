package com.devmod.compat.mods.epicfight;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

/**
 * Compatibility module for Epic Fight mod.
 *
 * Epic Fight is a soulslike combat mod that completely overhauls Minecraft's
 * combat system with animations, combos, skills, stamina, and more.
 *
 * This integration provides:
 * - Combat mode detection (battle mode vs normal)
 * - Entity patch access for animation/combat state
 * - Combat attribute extraction (armor negation, impact, etc.)
 * - Animation state tracking
 * - Stamina system integration
 *
 * Package structure (from decompilation/docs):
 * - yesman.epicfight.world.capabilities.EpicFightCapabilities
 * - yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch
 * - yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch
 * - yesman.epicfight.api.animation.types.AttackAnimation
 * - yesman.epicfight.gameasset.Animations
 *
 * @see <a href="https://github.com/Epic-Fight/epicfight">Epic Fight GitHub</a>
 * @see <a href="https://epicfight-docs.readthedocs.io/">Epic Fight Wiki</a>
 */
public class EpicFightCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(EpicFightCompat.class);
    public static final String MOD_ID = "epicfight";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references - Classes
    @Nullable private static Class<?> epicFightCapabilitiesClass;
    @Nullable private static Class<?> livingEntityPatchClass;
    @Nullable private static Class<?> playerPatchClass;
    @Nullable private static Class<?> animatorClass;

    // Methods from EpicFightCapabilities
    @Nullable private static Method getEntityPatchMethod;
    @Nullable private static Method getPlayerPatchMethod;

    // LivingEntityPatch methods
    @Nullable private static Method isInactionMethod;
    @Nullable private static Method getAnimatorMethod;

    // PlayerPatch methods
    @Nullable private static Method isBattleModeMethod;
    @Nullable private static Method getStaminaMethod;
    @Nullable private static Method getMaxStaminaMethod;

    // Animator methods
    @Nullable private static Method getCurrentAnimationMethod;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Epic Fight";
    }

    @Override
    public int priority() {
        // Very high priority - completely changes combat system
        return 15;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:epicfight] Epic Fight not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:epicfight] Epic Fight detected");
        LOGGER.debug("[Compat:epicfight] Version: {}", Compat.getVersion(MOD_ID));

        loadApi();
    }

    /**
     * Load Epic Fight API classes via reflection.
     */
    private void loadApi() {
        try {
            // Core capability class
            epicFightCapabilitiesClass = Class.forName(
                "yesman.epicfight.world.capabilities.EpicFightCapabilities");
            LOGGER.debug("[Compat:epicfight] Found EpicFightCapabilities");

            // Entity patch classes
            livingEntityPatchClass = Class.forName(
                "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch");
            LOGGER.debug("[Compat:epicfight] Found LivingEntityPatch");

            playerPatchClass = Class.forName(
                "yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch");
            LOGGER.debug("[Compat:epicfight] Found PlayerPatch");

            // Animator class
            animatorClass = Class.forName(
                "yesman.epicfight.api.animation.Animator");
            LOGGER.debug("[Compat:epicfight] Found Animator");

            // Load methods from EpicFightCapabilities
            loadCapabilityMethods();

            // Load methods from entity patches
            loadEntityPatchMethods();

            // Load animator methods
            loadAnimatorMethods();

            apiAvailable = true;
            LOGGER.info("[Compat:epicfight] Epic Fight API loaded successfully");

        } catch (ClassNotFoundException e) {
            LOGGER.debug("[Compat:epicfight] Epic Fight classes not found: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.warn("[Compat:epicfight] Error loading API: {}", e.getMessage());
        }
    }

    private void loadCapabilityMethods() throws NoSuchMethodException {
        final Class<?> capClass = epicFightCapabilitiesClass;
        if (capClass == null) return;

        // getEntityPatch(Entity, Class) -> EntityPatch
        getEntityPatchMethod = capClass.getMethod(
            "getEntityPatch", net.minecraft.world.entity.Entity.class, Class.class);
        LOGGER.debug("[Compat:epicfight] Found getEntityPatch method");

        // getPlayerPatch(Player) -> PlayerPatch
        try {
            getPlayerPatchMethod = capClass.getMethod("getPlayerPatch", Player.class);
            LOGGER.debug("[Compat:epicfight] Found getPlayerPatch method");
        } catch (NoSuchMethodException e) {
            LOGGER.trace("[Compat:epicfight] getPlayerPatch not found, using generic method");
        }
    }

    private void loadEntityPatchMethods() {
        final Class<?> lepClass = livingEntityPatchClass;
        if (lepClass == null) return;

        try {
            // isInaction() -> boolean (entity is in special action)
            isInactionMethod = lepClass.getMethod("isInaction");
            LOGGER.debug("[Compat:epicfight] Found isInaction method");
        } catch (NoSuchMethodException e) {
            LOGGER.trace("[Compat:epicfight] isInaction not found");
        }

        try {
            // getAnimator() -> Animator
            getAnimatorMethod = lepClass.getMethod("getAnimator");
            LOGGER.debug("[Compat:epicfight] Found getAnimator method");
        } catch (NoSuchMethodException e) {
            LOGGER.trace("[Compat:epicfight] getAnimator not found");
        }

        // PlayerPatch specific methods
        final Class<?> ppClass = playerPatchClass;
        if (ppClass != null) {
            try {
                isBattleModeMethod = ppClass.getMethod("isBattleMode");
                LOGGER.debug("[Compat:epicfight] Found isBattleMode method");
            } catch (NoSuchMethodException e) {
                LOGGER.trace("[Compat:epicfight] isBattleMode not found");
            }

            try {
                getStaminaMethod = ppClass.getMethod("getStamina");
                LOGGER.debug("[Compat:epicfight] Found getStamina method");
            } catch (NoSuchMethodException e) {
                LOGGER.trace("[Compat:epicfight] getStamina not found");
            }

            try {
                getMaxStaminaMethod = ppClass.getMethod("getMaxStamina");
                LOGGER.debug("[Compat:epicfight] Found getMaxStamina method");
            } catch (NoSuchMethodException e) {
                LOGGER.trace("[Compat:epicfight] getMaxStamina not found");
            }
        }
    }

    private void loadAnimatorMethods() {
        final Class<?> animClass = animatorClass;
        if (animClass == null) return;

        try {
            getCurrentAnimationMethod = animClass.getMethod("getPlayerCurrentAnimation");
            LOGGER.debug("[Compat:epicfight] Found getCurrentAnimation method");
        } catch (NoSuchMethodException e) {
            // Try alternative name
            try {
                getCurrentAnimationMethod = animClass.getMethod("getCurrentAnimation");
                LOGGER.debug("[Compat:epicfight] Found getCurrentAnimation (alt) method");
            } catch (NoSuchMethodException e2) {
                LOGGER.trace("[Compat:epicfight] getCurrentAnimation not found");
            }
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:epicfight] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Combat mode detection, entity patches, animation tracking, stamina integration";
    }

    // ============================================================
    // Public API
    // ============================================================

    /**
     * Check if Epic Fight is available.
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
     * Check if an entity has an Epic Fight patch.
     *
     * @param entity The entity to check
     * @return true if entity is patched by Epic Fight
     */
    public static boolean hasEntityPatch(@Nonnull LivingEntity entity) {
        if (!apiAvailable) return false;

        try {
            Object patch = getEntityPatch(entity);
            return patch != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the Epic Fight entity patch for an entity.
     *
     * @param entity The entity
     * @return The entity patch object, or null
     */
    @Nullable
    public static Object getEntityPatch(@Nonnull LivingEntity entity) {
        final Method method = getEntityPatchMethod;
        final Class<?> lepClass = livingEntityPatchClass;
        if (!apiAvailable || method == null || lepClass == null) {
            return null;
        }

        try {
            return method.invoke(null, entity, lepClass);
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to get entity patch: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the PlayerPatch for a player.
     *
     * @param player The player
     * @return The player patch object, or null
     */
    @Nullable
    public static Object getPlayerPatch(@Nonnull Player player) {
        if (!apiAvailable) return null;

        try {
            final Method ppMethod = getPlayerPatchMethod;
            if (ppMethod != null) {
                return ppMethod.invoke(null, player);
            }
            final Method epMethod = getEntityPatchMethod;
            final Class<?> ppClass = playerPatchClass;
            if (epMethod != null && ppClass != null) {
                return epMethod.invoke(null, player, ppClass);
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to get player patch: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Check if a player is in Epic Fight battle mode.
     *
     * @param player The player
     * @return true if in battle mode
     */
    public static boolean isInBattleMode(@Nonnull Player player) {
        final Method method = isBattleModeMethod;
        if (!apiAvailable || method == null) return false;

        try {
            Object playerPatch = getPlayerPatch(player);
            if (playerPatch != null) {
                Object result = method.invoke(playerPatch);
                return result instanceof Boolean && (Boolean) result;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to check battle mode: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Check if an entity is currently performing a special action (attack, skill, etc.).
     *
     * @param entity The entity
     * @return true if in action
     */
    public static boolean isInAction(@Nonnull LivingEntity entity) {
        final Method method = isInactionMethod;
        if (!apiAvailable || method == null) return false;

        try {
            Object patch = getEntityPatch(entity);
            if (patch != null) {
                Object result = method.invoke(patch);
                return result instanceof Boolean && (Boolean) result;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to check inaction: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Get the current stamina of a player.
     *
     * @param player The player
     * @return Current stamina, or -1 if unavailable
     */
    public static float getStamina(@Nonnull Player player) {
        final Method method = getStaminaMethod;
        if (!apiAvailable || method == null) return -1f;

        try {
            Object playerPatch = getPlayerPatch(player);
            if (playerPatch != null) {
                Object result = method.invoke(playerPatch);
                if (result instanceof Number) {
                    return ((Number) result).floatValue();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to get stamina: {}", e.getMessage());
        }
        return -1f;
    }

    /**
     * Get the max stamina of a player.
     *
     * @param player The player
     * @return Max stamina, or -1 if unavailable
     */
    public static float getMaxStamina(@Nonnull Player player) {
        final Method method = getMaxStaminaMethod;
        if (!apiAvailable || method == null) return -1f;

        try {
            Object playerPatch = getPlayerPatch(player);
            if (playerPatch != null) {
                Object result = method.invoke(playerPatch);
                if (result instanceof Number) {
                    return ((Number) result).floatValue();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to get max stamina: {}", e.getMessage());
        }
        return -1f;
    }

    /**
     * Get stamina percentage for a player.
     *
     * @param player The player
     * @return Stamina percentage (0-1), or -1 if unavailable
     */
    public static float getStaminaPercent(@Nonnull Player player) {
        float current = getStamina(player);
        float max = getMaxStamina(player);
        if (current >= 0 && max > 0) {
            return current / max;
        }
        return -1f;
    }

    /**
     * Get the current animation name for an entity.
     *
     * @param entity The entity
     * @return Animation name, or null
     */
    @Nullable
    public static String getCurrentAnimationName(@Nonnull LivingEntity entity) {
        final Method animatorMethod = getAnimatorMethod;
        final Method animMethod = getCurrentAnimationMethod;
        if (!apiAvailable || animatorMethod == null) return null;

        try {
            Object patch = getEntityPatch(entity);
            if (patch != null) {
                Object animator = animatorMethod.invoke(patch);
                if (animator != null && animMethod != null) {
                    Object animation = animMethod.invoke(animator);
                    if (animation != null) {
                        // Try to get animation name
                        try {
                            Method getNameMethod = animation.getClass().getMethod("getRegistryName");
                            Object name = getNameMethod.invoke(animation);
                            return name != null ? name.toString() : null;
                        } catch (NoSuchMethodException e) {
                            return animation.getClass().getSimpleName();
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:epicfight] Failed to get animation name: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get combat state information for an entity.
     *
     * @param entity The entity
     * @return Map with combat state info
     */
    public static Map<String, Object> getCombatState(@Nonnull LivingEntity entity) {
        Map<String, Object> state = new LinkedHashMap<>();

        if (!apiAvailable) {
            state.put("available", false);
            return state;
        }

        state.put("available", true);
        state.put("hasEntityPatch", hasEntityPatch(entity));

        if (hasEntityPatch(entity)) {
            state.put("inAction", isInAction(entity));

            String animation = getCurrentAnimationName(entity);
            if (animation != null) {
                state.put("currentAnimation", animation);
            }

            if (entity instanceof Player player) {
                state.put("battleMode", isInBattleMode(player));
                float stamina = getStamina(player);
                float maxStamina = getMaxStamina(player);
                if (stamina >= 0) {
                    state.put("stamina", stamina);
                    state.put("maxStamina", maxStamina);
                    state.put("staminaPercent", getStaminaPercent(player));
                }
            }
        }

        return state;
    }

    /**
     * Check if Epic Fight combat is active for an entity.
     * Returns true if entity has a patch and is either in battle mode or performing an action.
     *
     * @param entity The entity
     * @return true if Epic Fight combat is active
     */
    public static boolean isCombatActive(@Nonnull LivingEntity entity) {
        if (!hasEntityPatch(entity)) return false;

        if (entity instanceof Player player) {
            return isInBattleMode(player) || isInAction(entity);
        }
        return isInAction(entity);
    }

    /**
     * Get status summary for logging/debug.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Epic Fight: not available";
        }
        if (!apiAvailable) {
            return "Epic Fight: detected (API not loaded)";
        }

        StringBuilder sb = new StringBuilder("Epic Fight: available");
        if (isBattleModeMethod != null) sb.append(" [battle-mode]");
        if (getStaminaMethod != null) sb.append(" [stamina]");
        if (getAnimatorMethod != null) sb.append(" [animations]");
        return sb.toString();
    }

    /**
     * Get Epic Fight info for telemetry.
     */
    public static Map<String, Object> getTelemetryInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("available", available);
        info.put("apiAvailable", apiAvailable);

        if (available) {
            info.put("version", Compat.getVersion(MOD_ID));
            info.put("hasBattleModeApi", isBattleModeMethod != null);
            info.put("hasStaminaApi", getStaminaMethod != null);
            info.put("hasAnimatorApi", getAnimatorMethod != null);
        }

        return info;
    }
}
